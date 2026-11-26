package com.chessoffline.core.model

/**
 * Couleur d'une pièce ou d'un camp aux échecs.
 */
enum class PieceColor {
    WHITE,
    BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE

    val fenChar: Char
        get() = if (this == WHITE) 'w' else 'b'
}

/**
 * Type de pièce selon les règles FIDE.
 */
enum class PieceType(
    val fenLowerChar: Char,
    val sanLetter: String,
    val materialValue: Int
) {
    PAWN('p', "", 1),
    KNIGHT('n', "N", 3),
    BISHOP('b', "B", 3),
    ROOK('r', "R", 5),
    QUEEN('q', "Q", 9),
    KING('k', "K", 0);

    companion object {
        fun fromFenChar(c: Char): PieceType? = entries.find { it.fenLowerChar == c.lowercaseChar() }

        fun fromSanLetter(c: Char): PieceType? = when (c) {
            'N' -> KNIGHT
            'B' -> BISHOP
            'R' -> ROOK
            'Q' -> QUEEN
            'K' -> KING
            else -> null
        }
    }
}

/**
 * Pièce sur l'échiquier (type + couleur).
 */
data class Piece(
    val type: PieceType,
    val color: PieceColor
) {
    val fenChar: Char
        get() = if (color == PieceColor.WHITE) type.fenLowerChar.uppercaseChar() else type.fenLowerChar

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromFenChar(c) ?: return null
            val color = if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
            return Piece(type, color)
        }
    }
}

/**
 * Coordonnée d'une case sur l'échiquier (colonne 0..7 = a..h, rangée 0..7 = 1..8).
 */
@JvmInline
value class Square(val index: Int) {
    init {
        require(index in 0..63) { "Indice de case invalide : $index" }
    }

    constructor(file: Int, rank: Int) : this(rank * 8 + file) {
        require(file in 0..7 && rank in 0..7) { "Coordonnées hors plateau : ($file, $rank)" }
    }

    val file: Int get() = index % 8
    val rank: Int get() = index / 8
    val isLightSquare: Boolean get() = (file + rank) % 2 != 0

    fun toAlgebraic(): String = "${'a' + file}${'1' + rank}"

    companion object {
        fun fromAlgebraic(coord: String): Square? {
            if (coord.length != 2) return null
            val f = coord[0] - 'a'
            val r = coord[1] - '1'
            if (f !in 0..7 || r !in 0..7) return null
            return Square(f, r)
        }

        fun ofOrNull(file: Int, rank: Int): Square? =
            if (file in 0..7 && rank in 0..7) Square(file, rank) else null
    }
}

/**
 * Droits de roque FIDE pour les deux camps.
 */
data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
) {
    fun toFenString(): String {
        val sb = StringBuilder()
        if (whiteKingSide) sb.append('K')
        if (whiteQueenSide) sb.append('Q')
        if (blackKingSide) sb.append('k')
        if (blackQueenSide) sb.append('q')
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    companion object {
        val NONE = CastlingRights(false, false, false, false)

        fun fromFenString(s: String): CastlingRights? {
            if (s == "-") return NONE
            if (s.isEmpty()) return null
            var wk = false
            var wq = false
            var bk = false
            var bq = false
            for (ch in s) {
                when (ch) {
                    'K' -> wk = true
                    'Q' -> wq = true
                    'k' -> bk = true
                    'q' -> bq = true
                    else -> return null
                }
            }
            return CastlingRights(wk, wq, bk, bq)
        }
    }
}

/**
 * Représente un coup d'échecs complet avec métadonnées FIDE.
 */
data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isCapture: Boolean = false,
    val isEnPassant: Boolean = false,
    val isKingSideCastle: Boolean = false,
    val isQueenSideCastle: Boolean = false
) {
    fun toUci(): String = buildString {
        append(from.toAlgebraic())
        append(to.toAlgebraic())
        if (promotion != null) append(promotion.fenLowerChar)
    }
}

/**
 * État immuable complet d'une position d'échecs FIDE.
 */
data class ChessPosition(
    val board: List<Piece?>,
    val sideToMove: PieceColor,
    val castlingRights: CastlingRights,
    val enPassantTarget: Square?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int
) {
    init {
        require(board.size == 64) { "Le plateau doit contenir exactement 64 cases." }
    }

    fun pieceAt(square: Square): Piece? = board[square.index]

    fun pieceAt(file: Int, rank: Int): Piece? =
        if (file in 0..7 && rank in 0..7) board[rank * 8 + file] else null
}

/**
 * Résultat ou état courant de la partie selon les règles FIDE.
 */
sealed class GameStatus {
    data class Ongoing(val isCheck: Boolean) : GameStatus()
    data class Checkmate(val winner: PieceColor) : GameStatus()
    data object Stalemate : GameStatus()
    data class Draw(val reason: DrawReason) : GameStatus()
    data class Resigned(val winner: PieceColor) : GameStatus()
    data class Timeout(val winner: PieceColor) : GameStatus()

    val isTerminal: Boolean
        get() = this !is Ongoing

    fun pgnResult(): String = when (this) {
        is Ongoing -> "*"
        is Checkmate -> if (winner == PieceColor.WHITE) "1-0" else "0-1"
        is Resigned -> if (winner == PieceColor.WHITE) "1-0" else "0-1"
        is Timeout -> if (winner == PieceColor.WHITE) "1-0" else "0-1"
        Stalemate, is Draw -> "1/2-1/2"
    }
}

enum class DrawReason(val labelFr: String) {
    THREEFOLD_REPETITION("Triple répétition"),
    FIFTY_MOVE_RULE("Règle des 50 coups"),
    INSUFFICIENT_MATERIAL("Matériel insuffisant"),
    AGREEMENT("Accord mutuel")
}

/**
 * Entrée d'historique pour un demi-coup joué.
 */
data class MoveRecord(
    val ply: Int,
    val move: ChessMove,
    val san: String,
    val fenAfter: String,
    val capturedPiece: Piece?,
    val isCheckAfter: Boolean,
    val isCheckmateAfter: Boolean
)
