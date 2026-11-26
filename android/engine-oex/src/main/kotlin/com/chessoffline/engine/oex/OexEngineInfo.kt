package com.chessoffline.engine.oex

/**
 * Source de résolution du binaire exécutable OEX.
 */
enum class BinaryResolutionSource {
    /** Exécution directe depuis nativeLibraryDir (conforme W^X Android 10 à 15+). */
    NATIVE_LIB_DIR,
    /** Copie de secours dans noBackupFilesDir via ContentResolver (legacy / APK sans extractNativeLibs). */
    COPIED_FILES_DIR
}

/**
 * Description d'un moteur d'échecs OEX détecté sur le téléphone (ex. Stockfish 19).
 */
data class OexEngineInfo(
    val id: String,
    val displayName: String,
    val packageName: String,
    val authority: String?,
    val fileName: String,
    val executablePath: String,
    val nativeLibraryDir: String?,
    val targetAbi: String,
    val source: BinaryResolutionSource
)

/**
 * Trace technique pour un package inspecté lors de la découverte OEX.
 */
data class OexPackageDiagnostic(
    val packageName: String,
    val discoverySource: String,
    val authority: String?,
    val xmlEntries: List<String>,
    val nativeLibDir: String?,
    val nativeLibContents: List<String>,
    val resolutionNotes: List<String>
)

/**
 * Rapport complet de diagnostic OEX affiché dans les détails techniques en cas d'échec.
 */
data class OexDiagnosticReport(
    val deviceAbis: List<String>,
    val intentPackagesFound: List<String>,
    val stockfish141Visible: Boolean,
    val stockfish141Detail: String,
    val inspectedPackages: List<OexPackageDiagnostic>,
    val lastExecutionError: String? = null
) {
    fun formatReport(): String = buildString {
        appendLine("ABI téléphone : ${deviceAbis.joinToString(", ").ifBlank { "inconnue" }}")
        appendLine(
            "Packages via intent OEX (${intentPackagesFound.size}) : " +
                if (intentPackagesFound.isEmpty()) "aucun" else intentPackagesFound.joinToString(", ")
        )
        appendLine(
            "Visibilité com.stockfish141 : " +
                if (stockfish141Visible) "OUI ($stockfish141Detail)" else "NON ($stockfish141Detail)"
        )
        if (inspectedPackages.isEmpty()) {
            appendLine("Aucun package OEX inspectable.")
        } else {
            inspectedPackages.forEach { pkg ->
                appendLine("— Package : ${pkg.packageName} [${pkg.discoverySource}]")
                appendLine("  Authority : ${pkg.authority ?: "aucune"}")
                appendLine(
                    "  Entrées XML (${pkg.xmlEntries.size}) : " +
                        if (pkg.xmlEntries.isEmpty()) "aucune" else pkg.xmlEntries.joinToString(" ; ")
                )
                appendLine("  nativeLibraryDir : ${pkg.nativeLibDir ?: "null"}")
                appendLine(
                    "  Contenu nativeDir (${pkg.nativeLibContents.size}) : " +
                        if (pkg.nativeLibContents.isEmpty()) "vide ou inaccessible" else pkg.nativeLibContents.joinToString(", ")
                )
                if (pkg.resolutionNotes.isNotEmpty()) {
                    appendLine("  Notes : ${pkg.resolutionNotes.joinToString(" | ")}")
                }
            }
        }
        if (!lastExecutionError.isNullOrBlank()) {
            appendLine("Dernière erreur exécution : $lastExecutionError")
        }
    }.trim()
}

data class OexDiscoveryResult(
    val engines: List<OexEngineInfo>,
    val diagnostics: OexDiagnosticReport
)
