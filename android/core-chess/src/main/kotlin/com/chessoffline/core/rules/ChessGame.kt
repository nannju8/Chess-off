package com.chessoffline.core.rules

import com.chessoffline.core.model.ChessMove
import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.DrawReason
import com.chessoffline.core.model.GameStatus
import com.chessoffline.core.model.MoveRecord
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.model.Square
import com.chessoffline.core.notation.FenCodec
import com.chessoffline.core.notation.PgnCodec

/**
 * Moteur complet de partie d'échecs FIDE gérant :
 * - L'historique complet et la navigation libre avant/arrière
 * - L'annulation de coup (undo)
 * - La détection automatique de mat, pat, triple répétition, 50 coups et matériel insuffisant
 * - L'import/export FEN et PGN
 */
class ChessGame(initialFen: String = FenCodec.STARTING_FEN) {

    var initialFen: String = initialFen
        private set

    private val timeline = ArrayList<ChessPosition>()
    private val moveHistory = ArrayList<MoveRecord>()
    private val repetitionHistory = ArrayList<String>()
    private var forcedTerminalStatus: GameStatus? = null

    init {
        resetFromFen(initialFen)
    }

    fun resetFromFen(fen: String = FenCodec.STARTING_FEN): Result<Unit> {
        val parsed = FenCodec.parse(fen)
        if (parsed.isFailure) return Result.failure(parsed.exceptionOrNull()!!)

        val pos = parsed.getOrThrow()
        initialFen = FenCodec.serialize(pos)
        timeline.clear()
        moveHistory.clear()
        repetitionHistory.clear()
        forcedTerminalStatus = null

        timeline.add(pos)
        repetitionHistory.add(DrawDetector.positionRepetitionKey(pos))
        return Result.success(Unit)
    }

    val currentPosition: ChessPosition
        get() = timeline.last()

    val currentFen: String
        get() = FenCodec.serialize(currentPosition)

    val history: List<MoveRecord>
        get() = moveHistory.toList()

    val plyCount: Int
        get() = moveHistory.size

    fun positionAtPly(ply: Int): ChessPosition {
        val clamped = ply.coerceIn(0, timeline.lastIndex)
        return timeline[clamped]
    }

    fun fenAtPly(ply: Int): String = FenCodec.serialize(positionAtPly(ply))

    fun legalMoves(): List<ChessMove> {
        if (status().isTerminal) return emptyList()
        return MoveGenerator.generateLegalMoves(currentPosition)
    }

    fun legalMovesFrom(square: Square): List<ChessMove> {
        if (status().isTerminal) return emptyList()
        return MoveGenerator.generateLegalMovesFrom(currentPosition, square)
    }

    fun findLegalMove(from: Square, to: Square, promotion: PieceType? = null): ChessMove? {
        val candidates = legalMovesFrom(from).filter { it.to == to }
        if (candidates.isEmpty()) return null
        return if (promotion != null) {
            candidates.find { it.promotion == promotion }
        } else if (candidates.any { it.promotion != null }) {
            // Si une promotion est requise sans choix précisé, retourner null pour déclencher la modale
            null
        } else {
            candidates.first()
        }
    }

    fun needsPromotionChoice(from: Square, to: Square): Boolean =
        legalMovesFrom(from).any { it.to == to && it.promotion != null }

    fun playMove(from: Square, to: Square, promotion: PieceType? = null): MoveRecord? {
        val move = findLegalMove(from, to, promotion) ?: return null
        return playValidatedMove(move)
    }

    fun playUciMove(uci: String): MoveRecord? {
        if (uci.length < 4) return null
        val from = Square.fromAlgebraic(uci.substring(0, 2)) ?: return null
        val to = Square.fromAlgebraic(uci.substring(2, 4)) ?: return null
        val promo = if (uci.length >= 5) PieceType.fromFenChar(uci[4]) else null
        return playMove(from, to, promo)
    }

    private fun playValidatedMove(move: ChessMove): MoveRecord {
        val prev = currentPosition
        val allLegal = MoveGenerator.generateLegalMoves(prev)
        val san = FenCodec.toSan(prev, move, allLegal)

        val capturedPiece = when {
            move.isEnPassant -> prev.pieceAt(move.to.file, move.from.rank)
            else -> prev.pieceAt(move.to)
        }

        val next = MoveGenerator.applyMoveUnchecked(prev, move)
        val nextFen = FenCodec.serialize(next)
        val isCheckAfter = MoveGenerator.isKingInCheck(next, next.sideToMove)
        val isCheckmateAfter = isCheckAfter && MoveGenerator.generateLegalMoves(next).isEmpty()

        val record = MoveRecord(
            ply = moveHistory.size + 1,
            move = move,
            san = san,
            fenAfter = nextFen,
            capturedPiece = capturedPiece,
            isCheckAfter = isCheckAfter,
            isCheckmateAfter = isCheckmateAfter
        )

        timeline.add(next)
        moveHistory.add(record)
        repetitionHistory.add(DrawDetector.positionRepetitionKey(next))
        return record
    }

    fun undoLastMove(steps: Int = 1): Boolean {
        if (steps <= 0 || moveHistory.isEmpty()) return false
        val actualSteps = steps.coerceAtMost(moveHistory.size)
        repeat(actualSteps) {
            timeline.removeAt(timeline.lastIndex)
            moveHistory.removeAt(moveHistory.lastIndex)
            repetitionHistory.removeAt(repetitionHistory.lastIndex)
        }
        forcedTerminalStatus = null
        return true
    }

    fun resign(losingColor: PieceColor) {
        if (!status().isTerminal) {
            forcedTerminalStatus = GameStatus.Resigned(winner = losingColor.opposite())
        }
    }

    fun flagTimeout(losingColor: PieceColor) {
        if (!status().isTerminal) {
            forcedTerminalStatus = GameStatus.Timeout(winner = losingColor.opposite())
        }
    }

    fun status(): GameStatus {
        forcedTerminalStatus?.let { return it }

        val pos = currentPosition
        val legal = MoveGenerator.generateLegalMoves(pos)
        val inCheck = MoveGenerator.isKingInCheck(pos, pos.sideToMove)

        if (legal.isEmpty()) {
            return if (inCheck) {
                GameStatus.Checkmate(winner = pos.sideToMove.opposite())
            } else {
                GameStatus.Stalemate
            }
        }

        if (DrawDetector.isInsufficientMaterial(pos)) {
            return GameStatus.Draw(DrawReason.INSUFFICIENT_MATERIAL)
        }
        if (DrawDetector.isFiftyMoveRule(pos)) {
            return GameStatus.Draw(DrawReason.FIFTY_MOVE_RULE)
        }

        val counts = repetitionHistory.groupingBy { it }.eachCount()
        val currentKey = repetitionHistory.last()
        if (DrawDetector.isThreefoldRepetition(counts, currentKey)) {
            return GameStatus.Draw(DrawReason.THREEFOLD_REPETITION)
        }

        return GameStatus.Ongoing(isCheck = inCheck)
    }

    fun exportPgn(whiteName: String = "Blancs", blackName: String = "Noirs"): String =
        PgnCodec.export(
            initialFen = initialFen,
            history = moveHistory,
            status = status(),
            whiteName = whiteName,
            blackName = blackName
        )

    fun loadPgn(pgnText: String): Result<Unit> {
        val parsed = PgnCodec.parse(pgnText)
        if (parsed.isFailure) return Result.failure(parsed.exceptionOrNull()!!)

        val data = parsed.getOrThrow()
        val resetRes = resetFromFen(data.initialFen)
        if (resetRes.isFailure) return resetRes

        for (move in data.moves) {
            if (playMove(move.from, move.to, move.promotion) == null) {
                return Result.failure(IllegalStateException("Échec de rejeu PGN sur ${move.toUci()}"))
            }
        }
        return Result.success(Unit)
    }

    fun uciMovesPlayed(): List<String> = moveHistory.map { it.move.toUci() }

    /**
     * Retourne les pièces capturées par chaque camp jusqu'au demi-coup [upToPly].
     */
    fun capturedPiecesUpTo(upToPly: Int = moveHistory.size): Pair<List<Piece>, List<Piece>> {
        val capturedByWhite = ArrayList<Piece>()
        val capturedByBlack = ArrayList<Piece>()
        val limit = upToPly.coerceIn(0, moveHistory.size)
        for (i in 0 until limit) {
            val cap = moveHistory[i].capturedPiece ?: continue
            if (cap.color == PieceColor.BLACK) capturedByWhite.add(cap) else capturedByBlack.add(cap)
        }
        capturedByWhite.sortByDescending { it.type.materialValue }
        capturedByBlack.sortByDescending { it.type.materialValue }
        return capturedByWhite to capturedByBlack
    }
}
