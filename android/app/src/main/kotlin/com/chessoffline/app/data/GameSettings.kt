package com.chessoffline.app.data

import com.chessoffline.app.coach.CoachTone
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.TimeControl

enum class GameMode(val id: String, val labelFr: String) {
    VS_AI("vs_ai", "Contre Stockfish"),
    TWO_PLAYERS("two_players", "2 Joueurs"),
    ANALYSIS("analysis", "Analyse")
}

enum class ColorPreference(val id: String, val labelFr: String) {
    WHITE("white", "Blancs"),
    BLACK("black", "Noirs"),
    RANDOM("random", "Aléatoire");

    fun resolve(): PieceColor = when (this) {
        WHITE -> PieceColor.WHITE
        BLACK -> PieceColor.BLACK
        RANDOM -> if (kotlin.random.Random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
    }
}

enum class BoardThemeId(val id: String, val labelFr: String) {
    WOOD("wood", "Bois Classique"),
    TOURNAMENT("tournament", "Vert Tournoi"),
    DARK_SLATE("dark_slate", "Ardoise Sombre")
}

enum class PieceStyleId(val id: String, val labelFr: String) {
    STAUNTON("staunton", "Staunton"),
    NEO("neo", "Néo Moderne")
}

/**
 * Réglages persistés localement.
 */
data class GameSettings(
    val boardTheme: BoardThemeId = BoardThemeId.TOURNAMENT,
    val pieceStyle: PieceStyleId = PieceStyleId.STAUNTON,
    val isMuted: Boolean = false,
    val autoFlipTwoPlayers: Boolean = true,
    val showCoordinates: Boolean = true,
    val showEvalBar: Boolean = true,
    val coachEnabled: Boolean = true,
    val coachTone: CoachTone = CoachTone.PEDAGOGUE,
    val useOpeningBookForBot: Boolean = true,
    val preferredEngineId: String? = null,
    val lastDifficultyLevel: Int = 3,
    val lastTimeControl: TimeControl = TimeControl.BLITZ_5_0,
    val lastColorPreference: ColorPreference = ColorPreference.WHITE
)

/**
 * Instantané complet d'une partie sauvegardée automatiquement en local.
 */
data class SavedGameSnapshot(
    val initialFen: String,
    val uciMoves: List<String>,
    val gameMode: GameMode,
    val humanColor: PieceColor,
    val difficultyLevel: Int,
    val timeControl: TimeControl,
    val whiteTimeMs: Long,
    val blackTimeMs: Long,
    val isBoardFlipped: Boolean
)
