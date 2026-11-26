package com.chessoffline.app.coach

import com.chessoffline.app.openings.OpeningEntry
import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.GameStatus
import com.chessoffline.core.model.MoveRecord
import com.chessoffline.engine.AnalysisSnapshot

/**
 * Personnalité du Coach sélectionnable par le joueur.
 */
enum class CoachTone(val id: String, val jsonKey: String?, val labelFr: String) {
    PEDAGOGUE("pedagogue", "pedagogue", "Pédagogue"),
    STRICT("strict", "strict", "Strict"),
    TAQUIN("taquin", "taquin", "Taquin"),
    MIXED("mixed", null, "Mixte");

    companion object {
        fun fromId(id: String): CoachTone = entries.find { it.id == id } ?: PEDAGOGUE
    }
}

/**
 * Les 20 catégories exactes présentes dans `assets/coach_comments.json`.
 */
enum class CoachCategory {
    PLAYER_BRILLIANT,
    PLAYER_BEST_MOVE,
    PLAYER_GOOD_MOVE,
    PLAYER_INACCURACY,
    PLAYER_MISTAKE,
    PLAYER_BLUNDER_GENERIC,
    PLAYER_BLUNDER_HANGING_PIECE,
    PLAYER_MISSED_MATE,
    PLAYER_ALLOWED_MATE,
    OPENING_GOOD_PRINCIPLES,
    OPENING_WARNING,
    CASTLING_GOOD,
    TACTIC_CHECK_OR_FORK,
    ENDGAME_KING_ACTIVE,
    ENDGAME_PASSED_PAWN,
    TIME_PRESSURE,
    BOT_PLAYED_TRICKY,
    BOT_IN_TROUBLE,
    BOT_WINNING,
    GAME_END_SUMMARY;

    val isErrorCategory: Boolean
        get() = when (this) {
            PLAYER_INACCURACY,
            PLAYER_MISTAKE,
            PLAYER_BLUNDER_GENERIC,
            PLAYER_BLUNDER_HANGING_PIECE,
            PLAYER_MISSED_MATE,
            PLAYER_ALLOWED_MATE -> true
            else -> false
        }

    companion object {
        fun fromNameOrNull(raw: String): CoachCategory? =
            entries.find { it.name == raw.trim() }
    }
}

/**
 * Modèle d'un commentaire brut chargé depuis `assets/coach_comments.json`.
 */
data class CoachCommentTemplate(
    val id: String,
    val category: CoachCategory,
    val tone: String,
    val text: String
)

/**
 * Contexte extrait par [CoachAnalyzer] pour formater un commentaire.
 */
data class CoachDetection(
    val category: CoachCategory,
    val playedMoveSan: String,
    val bestMoveUci: String? = null,
    val bestMoveSan: String? = null,
    val squareAlgebraic: String? = null,
    val pieceNameFrLower: String? = null,
    val openingNameFr: String? = null
)

/**
 * Message final affiché dans la bulle du Coach.
 */
data class CoachMessage(
    val commentId: String,
    val category: CoachCategory,
    val tone: String,
    val text: String,
    val bestMoveUci: String? = null,
    val bestMoveSan: String? = null,
    val isErrorHighlight: Boolean = false
)

/**
 * Entrée complète d'analyse d'un coup ou d'une fin de partie.
 */
data class CoachAnalysisInput(
    val prePosition: ChessPosition,
    val postPosition: ChessPosition,
    val moveRecord: MoveRecord,
    val isPlayerMove: Boolean,
    val preEval: AnalysisSnapshot?,
    val postEval: AnalysisSnapshot?,
    val gameStatus: GameStatus,
    val hasClock: Boolean,
    val remainingPlayerTimeMs: Long?,
    val openingEntry: OpeningEntry?,
    val plyCount: Int
)
