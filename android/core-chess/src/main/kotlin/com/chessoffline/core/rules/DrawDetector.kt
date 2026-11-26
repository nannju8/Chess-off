package com.chessoffline.core.rules

import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.model.Square

/**
 * Détection des cas de nulle selon les règles officielles FIDE :
 * - Triple répétition de position
 * - Règle des 50 coups
 * - Matériel insuffisant (aucun mat possible par une suite de coups légaux)
 */
object DrawDetector {

    /**
     * Génère une clé canonique de position pour la règle de triple répétition :
     * plateau + trait + droits de roque + case en passant (seulement si une prise en passant légale existe).
     */
    fun positionRepetitionKey(position: ChessPosition): String {
        val sb = StringBuilder(80)
        var empty = 0
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                val p = position.pieceAt(file, rank)
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(p.fenChar)
                }
            }
            if (empty > 0) {
                sb.append(empty)
                empty = 0
            }
            if (rank > 0) sb.append('/')
        }

        sb.append(' ').append(position.sideToMove.fenChar)
        sb.append(' ').append(position.castlingRights.toFenString())

        val epSq = position.enPassantTarget
        val epValidForRepetition = if (epSq != null) {
            hasLegalEnPassantCapture(position, epSq)
        } else {
            false
        }
        sb.append(' ').append(if (epValidForRepetition && epSq != null) epSq.toAlgebraic() else "-")
        return sb.toString()
    }

    private fun hasLegalEnPassantCapture(position: ChessPosition, epSq: Square): Boolean {
        return MoveGenerator.generateLegalMoves(position).any { it.isEnPassant && it.to == epSq }
    }

    fun isThreefoldRepetition(repetitionCounts: Map<String, Int>, currentKey: String): Boolean =
        (repetitionCounts[currentKey] ?: 0) >= 3

    fun isFiftyMoveRule(position: ChessPosition): Boolean =
        position.halfmoveClock >= 100

    /**
     * Vérifie les cas classiques de matériel insuffisant :
     * - Roi vs Roi
     * - Roi + Cavalier vs Roi
     * - Roi + Fou vs Roi
     * - Roi + Fou(s) vs Roi + Fou(s) sur des cases de même couleur uniquement
     */
    fun isInsufficientMaterial(position: ChessPosition): Boolean {
        var knights = 0
        var bishopsLight = 0
        var bishopsDark = 0

        for (idx in 0..63) {
            val piece = position.board[idx] ?: continue
            when (piece.type) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.KNIGHT -> knights++
                PieceType.BISHOP -> {
                    val sq = Square(idx)
                    if (sq.isLightSquare) bishopsLight++ else bishopsDark++
                }
                PieceType.KING -> Unit
            }
        }

        val totalBishops = bishopsLight + bishopsDark
        val totalMinors = knights + totalBishops

        // K vs K, K+N vs K, K+B vs K
        if (totalMinors <= 1) return true

        // K+B vs K+B (ou plusieurs fous uniquement sur la même couleur de case, sans cavaliers)
        if (knights == 0 && (bishopsLight == 0 || bishopsDark == 0)) return true

        return false
    }
}
