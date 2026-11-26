package com.chessoffline.engine.oex

/**
 * Erreurs typées et explicites lors de la découverte ou du lancement d'un moteur OEX.
 */
sealed class OexError(val titleFr: String, val messageFr: String) : Exception(messageFr) {

    data object NoEngineInstalled : OexError(
        titleFr = "Aucun moteur d'échecs OEX détecté",
        messageFr = "L'application Stockfish 19 (ou tout autre fournisseur Open Exchange compatible intent.chess.provider.ENGINE) n'a pas été trouvée sur cet appareil. Vérifiez que Stockfish 19 est installé, ou jouez en mode 2 Joueurs / Analyse manuelle."
    )

    data class AbiIncompatible(val packageName: String, val supportedAbis: List<String>) : OexError(
        titleFr = "Architecture CPU incompatible",
        messageFr = "Le moteur détecté ($packageName) ne fournit aucun binaire compatible avec ce processeur (${supportedAbis.joinToString()})."
    )

    data class ExecutionDenied(val path: String, val causeDetail: String) : OexError(
        titleFr = "Exécution bloquée par Android (W^X)",
        messageFr = "Impossible d'exécuter le binaire ($path) : $causeDetail. Assurez-vous que votre application Stockfish 19 expose bien ses binaires dans nativeLibraryDir."
    )

    data class EngineCrash(val engineName: String, val detail: String) : OexError(
        titleFr = "Le moteur $engineName s'est arrêté",
        messageFr = "La communication UCI avec $engineName a été interrompue : $detail"
    )
}
