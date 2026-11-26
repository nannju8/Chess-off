package com.chessoffline.core.notation

import com.chessoffline.core.model.CastlingRights
import com.chessoffline.core.model.ChessMove
import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.model.Square
import com.chessoffline.core.rules.MoveGenerator

/**
 * Encodage et décodage complet du format FEN (Forsyth-Edwards Notation).
 */
object FenCodec {

    const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun parse(fen: String): Result<ChessPosition> = runCatching {
        val parts = fen.trim().split(Regex("\\s+"))
        require(parts.size in 4..6) { "FEN invalide : 4 à 6 champs attendus." }

        val boardTokens = parts[0].split('/')
        require(boardTokens.size == 8) { "FEN invalide : 8 rangées attendues." }

        val board = MutableList<Piece?>(64) { null }
        var whiteKings = 0
        var blackKings = 0

        for ((fenRankIdx, rankStr) in boardTokens.withIndex()) {
            val rank = 7 - fenRankIdx
            var file = 0
            for (ch in rankStr) {
                if (ch in '1'..'8') {
                    file += ch - '0'
                } else {
                    require(file in 0..7) { "FEN invalide : débordement sur la rangée ${rank + 1}." }
                    val piece = Piece.fromFenChar(ch)
                        ?: throw IllegalArgumentException("Symbole de pièce FEN inconnu : '$ch'")
                    if (piece.type == PieceType.KING) {
                        if (piece.color == PieceColor.WHITE) whiteKings++ else blackKings++
                    }
                    board[Square(file, rank).index] = piece
                    file++
                }
            }
            require(file == 8) { "FEN invalide : la rangée ${rank + 1} ne contient pas 8 colonnes." }
        }

        require(whiteKings == 1 && blackKings == 1) {
            "FEN invalide : chaque camp doit posséder exactement un roi."
        }

        val sideToMove = when (parts[1]) {
            "w" -> PieceColor.WHITE
            "b" -> PieceColor.BLACK
            else -> throw IllegalArgumentException("Trait FEN invalide : '${parts[1]}'")
        }

        val castling = CastlingRights.fromFenString(parts[2])
            ?: throw IllegalArgumentException("Droits de roque FEN invalides : '${parts[2]}'")

        val enPassant = if (parts[3] == "-") {
            null
        } else {
            Square.fromAlgebraic(parts[3])
                ?: throw IllegalArgumentException("Case en passant FEN invalide : '${parts[3]}'")
        }

        val halfmove = parts.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val fullmove = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        ChessPosition(
            board = board,
            sideToMove = sideToMove,
            castlingRights = castling,
            enPassantTarget = enPassant,
            halfmoveClock = halfmove,
            fullmoveNumber = fullmove
        )
    }

    fun serialize(position: ChessPosition): String {
        val sb = StringBuilder(90)
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = position.pieceAt(file, rank)
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(piece.fenChar)
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }

        sb.append(' ').append(position.sideToMove.fenChar)
        sb.append(' ').append(position.castlingRights.toFenString())
        sb.append(' ').append(position.enPassantTarget?.toAlgebraic() ?: "-")
        sb.append(' ').append(position.halfmoveClock)
        sb.append(' ').append(position.fullmoveNumber)
        return sb.toString()
    }

    /**
     * Formate un coup en notation algébrique standard (SAN) avant son exécution sur [position].
     */
    fun toSan(
        position: ChessPosition,
        move: ChessMove,
        legalMoves: List<ChessMove> = MoveGenerator.generateLegalMoves(position)
    ): String {
        val movingPiece = position.pieceAt(move.from)
            ?: error("Pièce introuvable sur ${move.from.toAlgebraic()}")

        val base = when {
            move.isKingSideCastle -> "O-O"
            move.isQueenSideCastle -> "O-O-O"
            movingPiece.type == PieceType.PAWN -> buildString {
                if (move.isCapture) {
                    append('a' + move.from.file)
                    append('x')
                }
                append(move.to.toAlgebraic())
                if (move.promotion != null) {
                    append('=')
                    append(move.promotion.sanLetter)
                }
            }
            else -> buildString {
                append(movingPiece.type.sanLetter)
                val ambiguous = legalMoves.filter {
                    it.from != move.from &&
                        it.to == move.to &&
                        position.pieceAt(it.from)?.type == movingPiece.type
                }
                if (ambiguous.isNotEmpty()) {
                    val sameFile = ambiguous.any { it.from.file == move.from.file }
                    val sameRank = ambiguous.any { it.from.rank == move.from.rank }
                    if (!sameFile) {
                        append('a' + move.from.file)
                    } else if (!sameRank) {
                        append('1' + move.from.rank)
                    } else {
                        append(move.from.toAlgebraic())
                    }
                }
                if (move.isCapture) append('x')
                append(move.to.toAlgebraic())
            }
        }

        val next = MoveGenerator.applyMoveUnchecked(position, move)
        val inCheck = MoveGenerator.isKingInCheck(next, next.sideToMove)
        val suffix = if (inCheck) {
            val hasReplies = MoveGenerator.generateLegalMoves(next).isNotEmpty()
            if (hasReplies) "+" else "#"
        } else {
            ""
        }
        return base + suffix
    }
}
