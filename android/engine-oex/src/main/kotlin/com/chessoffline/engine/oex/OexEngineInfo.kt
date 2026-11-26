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
