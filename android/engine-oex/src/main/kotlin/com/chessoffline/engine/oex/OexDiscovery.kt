package com.chessoffline.engine.oex

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

/**
 * Découverte et résolution des moteurs Open Exchange (OEX) sur Android 8 à 15+.
 *
 * Stratégie conforme W^X (Android 10+ / targetSdk >= 29) :
 * 1. Découverte via `intent.chess.provider.ENGINE` (activities, services, receivers, providers)
 *    + fallback direct par `getPackageInfo` sur les packages Stockfish connus (`com.stockfish141`, etc.).
 * 2. Lecture de `chess.provider.engine.authority` + `enginelist.xml` (ContentProvider avec
 *    `openInputStream` / `openAssetFileDescriptor`, ou ressources/assets du package).
 * 3. Filtrage ABI souple gérant les multi-ABI (`arm64-v8a|armeabi-v7a`) et alias (`aarch64`, `arm64`, `armv7`...).
 * 4. Résolution PRIORITAIRE dans `nativeLibraryDir` (fichiers `.so` ou sans extension) puis copie ContentResolver.
 */
object OexDiscovery {

    const val ACTION_ENGINE_PROVIDER = "intent.chess.provider.ENGINE"
    const val META_AUTHORITY = "chess.provider.engine.authority"
    const val STOCKFISH_19_PACKAGE = "com.stockfish141"

    private val KNOWN_OEX_PACKAGES = listOf(
        STOCKFISH_19_PACKAGE,
        "org.stockfishchess.android"
    )

    private val NON_ENGINE_SHARED_LIBS = setOf(
        "libc++_shared.so",
        "libstdc++.so"
    )

    internal data class RawOexEntry(
        val name: String,
        val fileName: String,
        val targetAbi: String?
    )

    private data class CandidatePackage(
        val packageName: String,
        val discoverySource: String,
        val explicitAuthority: String?
    )

    suspend fun discoverEngines(context: Context): List<OexEngineInfo> =
        discoverEnginesWithDiagnostics(context).engines

    suspend fun discoverEnginesWithDiagnostics(context: Context): OexDiscoveryResult =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val deviceAbis = Build.SUPPORTED_ABIS.toList()
            val intent = Intent(ACTION_ENGINE_PROVIDER)

            val resolveInfos = queryAllProviders(pm, intent)
            val intentPackagesFound = ArrayList<String>()
            val candidates = ArrayList<CandidatePackage>()
            val seenPackages = HashSet<String>()

            for (info in resolveInfos) {
                val pkgName = info.activityInfo?.packageName
                    ?: info.serviceInfo?.packageName
                    ?: info.providerInfo?.packageName
                    ?: continue

                if (!intentPackagesFound.contains(pkgName)) {
                    intentPackagesFound.add(pkgName)
                }
                if (!seenPackages.add(pkgName)) continue

                val metaData = info.activityInfo?.metaData
                    ?: info.serviceInfo?.metaData
                    ?: info.providerInfo?.metaData

                val explicitAuthority = metaData?.getString(META_AUTHORITY)
                    ?: info.providerInfo?.authority

                candidates.add(
                    CandidatePackage(
                        packageName = pkgName,
                        discoverySource = "INTENT",
                        explicitAuthority = explicitAuthority
                    )
                )
            }

            // Fallback explicite et vérification de visibilité de com.stockfish141 et packages connus
            var stockfish141Visible = false
            var stockfish141Detail = "non trouvé"
            for (knownPkg in KNOWN_OEX_PACKAGES) {
                val pkgCheck = runCatching { getPackageInfoCompat(pm, knownPkg) }
                if (knownPkg == STOCKFISH_19_PACKAGE) {
                    if (pkgCheck.isSuccess) {
                        val pInfo = pkgCheck.getOrThrow()
                        stockfish141Visible = true
                        stockfish141Detail = "v${pInfo.versionName ?: "?"}"
                    } else {
                        val ex = pkgCheck.exceptionOrNull()
                        stockfish141Detail = "${ex?.javaClass?.simpleName}: ${ex?.message ?: "absent"}"
                    }
                }
                if (pkgCheck.isSuccess && seenPackages.add(knownPkg)) {
                    candidates.add(
                        CandidatePackage(
                            packageName = knownPkg,
                            discoverySource = "FALLBACK_PACKAGE",
                            explicitAuthority = null
                        )
                    )
                }
            }

            val discovered = ArrayList<OexEngineInfo>()
            val packageDiagnostics = ArrayList<OexPackageDiagnostic>()

            for (candidate in candidates) {
                val pkgName = candidate.packageName
                val notes = ArrayList<String>()

                val appInfoResult = runCatching { getApplicationInfoCompat(pm, pkgName) }
                val appInfo = appInfoResult.getOrNull()
                if (appInfo == null) {
                    notes.add("getApplicationInfo échoué : ${appInfoResult.exceptionOrNull()?.message}")
                    packageDiagnostics.add(
                        OexPackageDiagnostic(
                            packageName = pkgName,
                            discoverySource = candidate.discoverySource,
                            authority = candidate.explicitAuthority,
                            xmlEntries = emptyList(),
                            nativeLibDir = null,
                            nativeLibContents = emptyList(),
                            resolutionNotes = notes
                        )
                    )
                    continue
                }

                val authority = candidate.explicitAuthority
                    ?: appInfo.metaData?.getString(META_AUTHORITY)
                    ?: findAuthorityFallback(pm, pkgName)

                val appLabel = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(pkgName)

                val nativeDirPath = appInfo.nativeLibraryDir
                val nativeContents = listNativeDirSummary(nativeDirPath, notes)

                val xmlReadResult = readXmlEntries(context, pm, pkgName, authority, notes)
                val xmlEntriesText = xmlReadResult.map { "${it.name} | file=${it.fileName} | target=${it.targetAbi ?: "-"}" }

                val filteredEntries = filterBestAbiEntries(
                    entries = xmlReadResult,
                    supportedAbis = deviceAbis,
                    hasNativeBinary = nativeContents.isNotEmpty()
                )

                val effectiveEntries = if (filteredEntries.isNotEmpty()) {
                    filteredEntries
                } else {
                    if (xmlReadResult.isNotEmpty()) {
                        notes.add("Aucune entrée XML retenue après filtrage ABI -> bascule sur scan nativeLibraryDir")
                    }
                    scanNativeDirEntries(nativeDirPath, appLabel)
                }

                if (effectiveEntries.isEmpty()) {
                    notes.add("Aucun moteur trouvé dans XML ni dans nativeLibraryDir")
                }

                for (entry in effectiveEntries) {
                    val resolved = resolveExecutableForEntry(
                        context = context,
                        appInfo = appInfo,
                        pkgName = pkgName,
                        authority = authority,
                        entry = entry,
                        notes = notes
                    )
                    if (resolved != null) {
                        discovered.add(resolved)
                    } else {
                        notes.add("Échec résolution binaire pour ${entry.fileName}")
                    }
                }

                packageDiagnostics.add(
                    OexPackageDiagnostic(
                        packageName = pkgName,
                        discoverySource = candidate.discoverySource,
                        authority = authority,
                        xmlEntries = xmlEntriesText,
                        nativeLibDir = nativeDirPath,
                        nativeLibContents = nativeContents,
                        resolutionNotes = notes
                    )
                )
            }

            // Trier en plaçant Stockfish 19 / Stockfish en tête
            val sorted = discovered.sortedWith(
                compareByDescending<OexEngineInfo> {
                    it.displayName.contains("stockfish", ignoreCase = true) ||
                        it.packageName == STOCKFISH_19_PACKAGE
                }.thenByDescending {
                    it.displayName.contains("19") || it.packageName == STOCKFISH_19_PACKAGE
                }.thenByDescending {
                    it.source == BinaryResolutionSource.NATIVE_LIB_DIR
                }.thenBy { it.displayName }
            )

            val report = OexDiagnosticReport(
                deviceAbis = deviceAbis,
                intentPackagesFound = intentPackagesFound,
                stockfish141Visible = stockfish141Visible,
                stockfish141Detail = stockfish141Detail,
                inspectedPackages = packageDiagnostics
            )

            OexDiscoveryResult(engines = sorted, diagnostics = report)
        }

    private fun queryAllProviders(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        val combined = ArrayList<ResolveInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            runCatching { combined.addAll(pm.queryIntentActivities(intent, flags)) }
            runCatching { combined.addAll(pm.queryIntentServices(intent, flags)) }
            runCatching { combined.addAll(pm.queryBroadcastReceivers(intent, flags)) }
            runCatching { combined.addAll(pm.queryIntentContentProviders(intent, flags)) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { combined.addAll(pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)) }
            @Suppress("DEPRECATION")
            runCatching { combined.addAll(pm.queryIntentServices(intent, PackageManager.GET_META_DATA)) }
            @Suppress("DEPRECATION")
            runCatching { combined.addAll(pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)) }
            @Suppress("DEPRECATION")
            runCatching { combined.addAll(pm.queryIntentContentProviders(intent, PackageManager.GET_META_DATA)) }
        }
        return combined
    }

    private fun getApplicationInfoCompat(pm: PackageManager, pkgName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
        }
    }

    private fun getPackageInfoCompat(pm: PackageManager, pkgName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                pkgName,
                PackageManager.PackageInfoFlags.of((PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA).toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkgName, PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)
        }
    }

    private fun findAuthorityFallback(pm: PackageManager, pkgName: String): String? {
        val pkgInfo = runCatching { getPackageInfoCompat(pm, pkgName) }.getOrNull() ?: return null
        return pkgInfo.providers?.firstOrNull()?.authority
    }

    private fun listNativeDirSummary(nativeDirPath: String?, notes: MutableList<String>): List<String> {
        if (nativeDirPath.isNullOrBlank()) return emptyList()
        val dir = File(nativeDirPath)
        if (!dir.exists()) {
            notes.add("nativeLibraryDir inexistant")
            return emptyList()
        }
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            notes.add("listFiles nativeDir refusé : ${e.message}")
            null
        } ?: return emptyList()

        return files.sortedBy { it.name }.map { f ->
            val execFlag = if (f.canExecute()) "x" else "-"
            "${f.name} (${f.length()}o,$execFlag)"
        }
    }

    private fun readXmlEntries(
        context: Context,
        pm: PackageManager,
        pkgName: String,
        authority: String?,
        notes: MutableList<String>
    ): List<RawOexEntry> {
        val parsedEntries = ArrayList<RawOexEntry>()

        // 1. ContentProvider OEX (openInputStream puis openAssetFileDescriptor)
        if (!authority.isNullOrBlank()) {
            val candidateUris = listOf(
                Uri.parse("content://$authority/enginelist.xml"),
                Uri.parse("content://$authority")
            )
            for (uri in candidateUris) {
                val streamResult = openContentUriStream(context, uri)
                val stream = streamResult.getOrNull()
                if (stream != null) {
                    runCatching {
                        stream.use { parsedEntries.addAll(parseEngineListXml(it)) }
                    }.onFailure {
                        notes.add("Erreur XML $uri : ${it.message}")
                    }
                    if (parsedEntries.isNotEmpty()) break
                } else {
                    streamResult.exceptionOrNull()?.let { ex ->
                        notes.add("Provider $uri : ${ex.javaClass.simpleName}: ${ex.message}")
                    }
                }
            }
        }

        // 2. Ressources res/xml/enginelist.xml ou assets/enginelist.xml du package fournisseur
        if (parsedEntries.isEmpty()) {
            runCatching {
                val res = pm.getResourcesForApplication(pkgName)
                val xmlId = res.getIdentifier("enginelist", "xml", pkgName)
                if (xmlId != 0) {
                    val parser = res.getXml(xmlId)
                    try {
                        parsedEntries.addAll(parseEngineListFromXmlParser(parser))
                    } finally {
                        parser.close()
                    }
                }
                if (parsedEntries.isEmpty()) {
                    res.assets.open("enginelist.xml").use { stream ->
                        parsedEntries.addAll(parseEngineListXml(stream))
                    }
                }
            }.onFailure { ex ->
                if (ex !is java.io.FileNotFoundException) {
                    notes.add("Lecture res/assets : ${ex.javaClass.simpleName}: ${ex.message}")
                }
            }
        }

        return parsedEntries
    }

    private fun openContentUriStream(context: Context, uri: Uri): Result<InputStream?> = runCatching {
        context.contentResolver.openInputStream(uri)
            ?: context.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
    }

    private fun scanNativeDirEntries(nativeDirPath: String?, fallbackAppLabel: String): List<RawOexEntry> {
        if (nativeDirPath.isNullOrBlank()) return emptyList()
        val libDir = File(nativeDirPath)
        if (!libDir.exists() || !libDir.isDirectory) return emptyList()

        val allFiles = libDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".so") || file.canExecute() || !file.name.contains('.'))
        }?.sortedBy { it.name }.orEmpty()

        val engineFiles = allFiles.filterNot { it.name.lowercase() in NON_ENGINE_SHARED_LIBS }
            .ifEmpty { allFiles }

        return engineFiles.map { file ->
            val cleanName = file.name
                .removePrefix("lib")
                .removeSuffix(".so")
                .replaceFirstChar { it.uppercase() }
            RawOexEntry(
                name = if (engineFiles.size == 1) fallbackAppLabel else cleanName,
                fileName = file.name,
                targetAbi = Build.SUPPORTED_ABIS.firstOrNull()
            )
        }
    }

    private fun parseEngineListXml(stream: InputStream): List<RawOexEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)
        return parseEngineListFromXmlParser(parser)
    }

    private fun parseEngineListFromXmlParser(parser: XmlPullParser): List<RawOexEntry> {
        val result = ArrayList<RawOexEntry>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("engine", ignoreCase = true)) {
                val attrs = HashMap<String, String>(parser.attributeCount)
                for (i in 0 until parser.attributeCount) {
                    val attrName = parser.getAttributeName(i)?.lowercase()?.substringAfter(':') ?: continue
                    val attrValue = parser.getAttributeValue(i)?.trim() ?: continue
                    attrs[attrName] = attrValue
                }

                val name = attrs["name"]
                    ?: attrs["label"]
                    ?: attrs["title"]
                    ?: "Moteur OEX"

                val fileName = attrs["filename"]
                    ?: attrs["file"]
                    ?: attrs["path"]
                    ?: attrs["binary"]
                    ?: ""

                val target = attrs["target"]
                    ?: attrs["arch"]
                    ?: attrs["abi"]

                if (fileName.isNotBlank()) {
                    result.add(RawOexEntry(name = name, fileName = fileName, targetAbi = target))
                }
            }
            eventType = parser.next()
        }
        return result
    }

    /**
     * Filtre les entrées par compatibilité ABI en gérant les valeurs multiples (`arm64-v8a|armeabi-v7a`)
     * et les alias (`aarch64`, `arm64`, `arm-v8a`, `arm`, `armv7`, etc.).
     * Ne rejette pas une entrée ambiguë lorsqu'un binaire existe dans `nativeLibraryDir`.
     */
    internal fun filterBestAbiEntries(
        entries: List<RawOexEntry>,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
        hasNativeBinary: Boolean = false
    ): List<RawOexEntry> {
        if (entries.isEmpty()) return emptyList()
        val normalizedSupported = supportedAbis.map { normalizeAbiToken(it) }
        val groupedByName = entries.groupBy { it.name }
        val selected = ArrayList<RawOexEntry>()

        for ((_, candidates) in groupedByName) {
            var bestCandidate: RawOexEntry? = null
            var bestRank = Int.MAX_VALUE

            for (entry in candidates) {
                val rank = abiMatchRank(entry.targetAbi, normalizedSupported, hasNativeBinary)
                if (rank < bestRank) {
                    bestRank = rank
                    bestCandidate = entry
                }
            }

            if (bestCandidate != null && bestRank < Int.MAX_VALUE) {
                selected.add(bestCandidate)
            }
        }

        // Si le filtrage strict n'a rien retenu mais qu'un binaire exécutable existe dans nativeLibraryDir,
        // conserver les entrées dont le target est ambigu plutôt que de tout rejeter.
        if (selected.isEmpty() && hasNativeBinary) {
            return entries.distinctBy { it.fileName }
        }
        return selected
    }

    private fun abiMatchRank(
        rawTarget: String?,
        normalizedSupported: List<String>,
        hasNativeBinary: Boolean
    ): Int {
        if (rawTarget.isNullOrBlank()) return normalizedSupported.size

        val tokens = rawTarget.split(Regex("[|,;\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return normalizedSupported.size

        var bestIdx = Int.MAX_VALUE
        var hadAnyRecognizedAbi = false

        for (token in tokens) {
            val norm = normalizeAbiToken(token)
            if (isKnownAndroidAbi(norm)) hadAnyRecognizedAbi = true
            val idx = normalizedSupported.indexOfFirst { it == norm }
            if (idx in 0 until bestIdx) {
                bestIdx = idx
            }
        }

        if (bestIdx < Int.MAX_VALUE) return bestIdx

        // Si le champ target est ambigu/non reconnu (ex. identifiant générique) et qu'un binaire
        // existe dans nativeLibraryDir, on le garde en priorité inférieure sans le rejeter.
        if (!hadAnyRecognizedAbi && hasNativeBinary) {
            return normalizedSupported.size + 1
        }
        return Int.MAX_VALUE
    }

    internal fun normalizeAbiToken(raw: String): String {
        return when (raw.trim().lowercase()) {
            "arm64-v8a", "arm64", "aarch64", "arm-v8a", "armv8", "armv8a", "v8a" -> "arm64-v8a"
            "armeabi-v7a", "armeabi", "arm", "armv7", "armv7a", "arm-v7a", "armeabi7a", "v7a" -> "armeabi-v7a"
            "x86_64", "x64", "amd64", "x86-64" -> "x86_64"
            "x86", "i386", "i686" -> "x86"
            else -> raw.trim().lowercase()
        }
    }

    private fun isKnownAndroidAbi(normalized: String): Boolean =
        normalized == "arm64-v8a" ||
            normalized == "armeabi-v7a" ||
            normalized == "x86_64" ||
            normalized == "x86"

    /**
     * Résout le chemin d'exécution du binaire en privilégiant impérativement `nativeLibraryDir`.
     */
    private fun resolveExecutableForEntry(
        context: Context,
        appInfo: ApplicationInfo,
        pkgName: String,
        authority: String?,
        entry: RawOexEntry,
        notes: MutableList<String>
    ): OexEngineInfo? {
        val primaryAbi = entry.targetAbi ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val nativeDirPath = appInfo.nativeLibraryDir
        val nativeDir = nativeDirPath?.let { File(it) }

        // PRIORITÉ 1 : Exécution directe depuis nativeLibraryDir du package fournisseur (conforme W^X Android 10–15+)
        if (nativeDir != null && nativeDir.exists() && nativeDir.isDirectory) {
            val directMatch = findMatchingBinaryInNativeDir(nativeDir, entry.fileName, entry.name)
            if (directMatch != null && directMatch.exists()) {
                return OexEngineInfo(
                    id = "$pkgName:${entry.fileName}",
                    displayName = entry.name,
                    packageName = pkgName,
                    authority = authority,
                    fileName = entry.fileName,
                    executablePath = directMatch.absolutePath,
                    nativeLibraryDir = nativeDirPath,
                    targetAbi = primaryAbi,
                    source = BinaryResolutionSource.NATIVE_LIB_DIR
                )
            }
        }

        // PRIORITÉ 2 (Fallback uniquement) : Copie via ContentResolver dans noBackupFilesDir
        if (!authority.isNullOrBlank()) {
            val destDir = File(context.noBackupFilesDir, "oex_engines/$pkgName")
            if (!destDir.exists()) destDir.mkdirs()
            val cleanBaseName = File(entry.fileName).name
            val destFile = File(destDir, cleanBaseName)

            val uri = Uri.parse("content://$authority/${entry.fileName}")
            val copyResult = runCatching {
                val inputStream = openContentUriStream(context, uri).getOrThrow()
                    ?: error("Flux null pour $uri")
                inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.setExecutable(true, false)
                destFile.exists() && destFile.length() > 0L
            }

            if (copyResult.getOrDefault(false)) {
                return OexEngineInfo(
                    id = "$pkgName:${entry.fileName}",
                    displayName = entry.name,
                    packageName = pkgName,
                    authority = authority,
                    fileName = entry.fileName,
                    executablePath = destFile.absolutePath,
                    nativeLibraryDir = nativeDirPath,
                    targetAbi = primaryAbi,
                    source = BinaryResolutionSource.COPIED_FILES_DIR
                )
            } else {
                copyResult.exceptionOrNull()?.let { ex ->
                    notes.add("Copie fallback $uri échouée : ${ex.javaClass.simpleName}: ${ex.message}")
                }
            }
        }

        return null
    }

    private fun findMatchingBinaryInNativeDir(
        nativeDir: File,
        rawFileName: String,
        engineName: String
    ): File? {
        val baseName = File(rawFileName).name
        val baseWithoutSo = baseName.removePrefix("lib").removeSuffix(".so")
        val normalizedEngine = engineName.lowercase().replace(Regex("[^a-z0-9]"), "")

        val directCandidates = listOf(
            File(nativeDir, baseName),
            File(nativeDir, "lib$baseName.so"),
            File(nativeDir, "lib$baseWithoutSo.so"),
            File(nativeDir, baseWithoutSo)
        )

        directCandidates.firstOrNull { it.exists() && it.isFile }?.let { return it }

        // Scanner à la fois les fichiers `.so`, les binaires sans extension et les fichiers exécutables
        val allBinaries = nativeDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".so") || !f.name.contains('.') || f.canExecute())
        }?.sortedBy { it.name }.orEmpty()

        val engineCandidates = allBinaries.filterNot { it.name.lowercase() in NON_ENGINE_SHARED_LIBS }
            .ifEmpty { allBinaries }

        val cleanNeedle = baseWithoutSo.lowercase()
        engineCandidates.firstOrNull { file ->
            val clean = file.name.removePrefix("lib").removeSuffix(".so").lowercase()
            (cleanNeedle.isNotEmpty() && (clean.contains(cleanNeedle) || cleanNeedle.contains(clean))) ||
                (normalizedEngine.isNotEmpty() && clean.contains(normalizedEngine.take(8)))
        }?.let { return it }

        // Privilégier un binaire nommé "stockfish" si présent
        engineCandidates.firstOrNull { it.name.contains("stockfish", ignoreCase = true) }?.let { return it }

        // Si un seul binaire moteur est présent dans nativeLibraryDir, c'est le moteur principal
        if (engineCandidates.size == 1) return engineCandidates.first()

        // Dernier recours : retourner le premier exécutable non-système plutôt que de rejeter le binaire principal
        return engineCandidates.firstOrNull { it.canExecute() } ?: engineCandidates.firstOrNull()
    }
}
