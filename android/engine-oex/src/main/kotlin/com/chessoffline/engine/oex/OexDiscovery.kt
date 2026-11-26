package com.chessoffline.engine.oex

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
 * 1. Découverte des fournisseurs via l'intent `intent.chess.provider.ENGINE` (avec <queries>).
 * 2. Lecture de `chess.provider.engine.authority` + `enginelist.xml` (via ContentProvider ou ressources APK).
 * 3. Résolution PRIORITAIRE du binaire directement dans le `nativeLibraryDir` de l'APK fournisseur
 *    (ex. `libstockfish19.so` / `libstockfish.so`), seul emplacement où `execve()` reste autorisé par SELinux.
 * 4. Copie de secours vers `noBackupFilesDir` uniquement si aucun binaire exécutable n'est présent dans `nativeLibraryDir`.
 */
object OexDiscovery {

    const val ACTION_ENGINE_PROVIDER = "intent.chess.provider.ENGINE"
    const val META_AUTHORITY = "chess.provider.engine.authority"

    private data class RawOexEntry(
        val name: String,
        val fileName: String,
        val targetAbi: String?
    )

    suspend fun discoverEngines(context: Context): List<OexEngineInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(ACTION_ENGINE_PROVIDER)

        val resolveInfos = queryAllProviders(pm, intent)
        val seenPackages = HashSet<String>()
        val discovered = ArrayList<OexEngineInfo>()

        for (info in resolveInfos) {
            val pkgName = info.activityInfo?.packageName
                ?: info.serviceInfo?.packageName
                ?: info.providerInfo?.packageName
                ?: continue

            if (!seenPackages.add(pkgName)) continue

            val metaData = info.activityInfo?.metaData
                ?: info.serviceInfo?.metaData
                ?: info.providerInfo?.metaData

            val authority = metaData?.getString(META_AUTHORITY)
                ?: findAuthorityFallback(pm, pkgName)

            val appInfo = runCatching { getApplicationInfoCompat(pm, pkgName) }.getOrNull() ?: continue
            val appLabel = pm.getApplicationLabel(appInfo).toString()

            val entries = readEngineList(context, pm, pkgName, authority, appLabel)
            for (entry in entries) {
                val resolved = resolveExecutableForEntry(
                    context = context,
                    appInfo = appInfo,
                    pkgName = pkgName,
                    authority = authority,
                    entry = entry
                )
                if (resolved != null) {
                    discovered.add(resolved)
                }
            }
        }

        // Trier en plaçant Stockfish 19 / Stockfish en tête
        discovered.sortedWith(
            compareByDescending<OexEngineInfo> {
                it.displayName.contains("stockfish", ignoreCase = true)
            }.thenByDescending {
                it.displayName.contains("19")
            }.thenByDescending {
                it.source == BinaryResolutionSource.NATIVE_LIB_DIR
            }.thenBy { it.displayName }
        )
    }

    private fun queryAllProviders(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        val combined = ArrayList<ResolveInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            combined.addAll(pm.queryIntentActivities(intent, flags))
            combined.addAll(pm.queryIntentServices(intent, flags))
            combined.addAll(pm.queryIntentContentProviders(intent, flags))
        } else {
            @Suppress("DEPRECATION")
            combined.addAll(pm.queryIntentActivities(intent, PackageManager.GET_META_DATA))
            @Suppress("DEPRECATION")
            combined.addAll(pm.queryIntentServices(intent, PackageManager.GET_META_DATA))
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

    private fun findAuthorityFallback(pm: PackageManager, pkgName: String): String? {
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PROVIDERS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, PackageManager.GET_PROVIDERS)
            }
        }.getOrNull() ?: return null

        return pkgInfo.providers?.firstOrNull()?.authority
    }

    /**
     * Lit la liste des moteurs exposés par le fournisseur OEX via :
     * 1) Le ContentProvider (`content://<authority>/enginelist.xml` ou `/`)
     * 2) Les ressources XML/assets du package fournisseur (`enginelist.xml`)
     * 3) À défaut, scan direct du `nativeLibraryDir`
     */
    private fun readEngineList(
        context: Context,
        pm: PackageManager,
        pkgName: String,
        authority: String?,
        fallbackAppLabel: String
    ): List<RawOexEntry> {
        val parsedEntries = ArrayList<RawOexEntry>()

        // 1. Tenter lecture via ContentProvider OEX
        if (!authority.isNullOrBlank()) {
            val candidateUris = listOf(
                Uri.parse("content://$authority/enginelist.xml"),
                Uri.parse("content://$authority")
            )
            for (uri in candidateUris) {
                val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                if (stream != null) {
                    stream.use {
                        parsedEntries.addAll(parseEngineListXml(it))
                    }
                    if (parsedEntries.isNotEmpty()) break
                }
            }
        }

        // 2. Tenter lecture depuis res/xml/enginelist.xml ou assets/enginelist.xml du package fournisseur
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
                } else {
                    res.assets.open("enginelist.xml").use { stream ->
                        parsedEntries.addAll(parseEngineListXml(stream))
                    }
                }
            }
        }

        // 3. Fallback si aucun XML trouvé mais des binaires sont présents dans nativeLibraryDir
        if (parsedEntries.isEmpty()) {
            val appInfo = runCatching { getApplicationInfoCompat(pm, pkgName) }.getOrNull()
            val libDir = appInfo?.nativeLibraryDir?.let { File(it) }
            val soFiles = libDir?.listFiles { file ->
                file.isFile && (file.name.endsWith(".so") || file.canExecute())
            }?.sortedBy { it.name }.orEmpty()

            if (soFiles.isNotEmpty()) {
                for (so in soFiles) {
                    val cleanName = so.name
                        .removePrefix("lib")
                        .removeSuffix(".so")
                        .replaceFirstChar { it.uppercase() }
                    parsedEntries.add(
                        RawOexEntry(
                            name = if (soFiles.size == 1) fallbackAppLabel else cleanName,
                            fileName = so.name,
                            targetAbi = Build.SUPPORTED_ABIS.firstOrNull()
                        )
                    )
                }
            }
        }

        return filterBestAbiEntries(parsedEntries)
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
            if (eventType == XmlPullParser.START_TAG && parser.name == "engine") {
                val name = parser.getAttributeValue(null, "name") ?: "Moteur OEX"
                val fileName = parser.getAttributeValue(null, "filename")
                    ?: parser.getAttributeValue(null, "file")
                    ?: ""
                val target = parser.getAttributeValue(null, "target")
                    ?: parser.getAttributeValue(null, "arch")
                if (fileName.isNotBlank()) {
                    result.add(RawOexEntry(name = name, fileName = fileName, targetAbi = target))
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun filterBestAbiEntries(entries: List<RawOexEntry>): List<RawOexEntry> {
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val groupedByName = entries.groupBy { it.name }
        val selected = ArrayList<RawOexEntry>()

        for ((_, candidates) in groupedByName) {
            val best = candidates.minByOrNull { entry ->
                val abi = entry.targetAbi
                if (abi.isNullOrBlank()) {
                    supportedAbis.size
                } else {
                    val idx = supportedAbis.indexOfFirst { it.equals(abi, ignoreCase = true) }
                    if (idx >= 0) idx else Int.MAX_VALUE
                }
            }
            if (best != null) {
                val abi = best.targetAbi
                val isCompatible = abi.isNullOrBlank() ||
                    supportedAbis.any { it.equals(abi, ignoreCase = true) }
                if (isCompatible) {
                    selected.add(best)
                }
            }
        }
        return selected
    }

    /**
     * Résout le chemin d'exécution du binaire en privilégiant impérativement `nativeLibraryDir`.
     */
    private fun resolveExecutableForEntry(
        context: Context,
        appInfo: ApplicationInfo,
        pkgName: String,
        authority: String?,
        entry: RawOexEntry
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
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    destFile.setExecutable(true, false)
                    destFile.exists() && destFile.length() > 0L
                } ?: false
            }.getOrDefault(false)

            if (copied) {
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

        val candidates = listOf(
            File(nativeDir, baseName),
            File(nativeDir, "lib$baseName.so"),
            File(nativeDir, "lib$baseWithoutSo.so"),
            File(nativeDir, baseWithoutSo)
        )

        candidates.firstOrNull { it.exists() && it.isFile }?.let { return it }

        val files = nativeDir.listFiles { f -> f.isFile && f.name.endsWith(".so") }.orEmpty()
        files.firstOrNull {
            val clean = it.name.removePrefix("lib").removeSuffix(".so").lowercase()
            clean.contains(baseWithoutSo.lowercase()) ||
                (normalizedEngine.isNotEmpty() && clean.contains(normalizedEngine.take(8)))
        }?.let { return it }

        // Si un seul binaire .so est présent dans l'APK fournisseur, c'est le moteur principal
        if (files.size == 1) return files.first()
        return null
    }
}
