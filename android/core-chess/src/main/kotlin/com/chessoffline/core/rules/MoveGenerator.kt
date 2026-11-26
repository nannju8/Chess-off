package com.chessoffline.core.rules

import com.chessoffline.core.model.CastlingRights
import com.chessoffline.core.model.ChessMove
import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.model.Square

/**
 * Générateur strict de coups légaux selon les règles FIDE :
 * roques, prise en passant, clouages, échecs et promotions.
 */
object MoveGenerator {

    private val KNIGHT_OFFSETS = arrayOf(
        1 to 2, 2 to 1, 2 to -1, 1 to -2,
        -1 to -2, -2 to -1, -2 to 1, -1 to 2
    )

    private val KING_OFFSETS = arrayOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0,           1 to 0,
        -1 to 1,  0 to 1,  1 to 1
    )

    private val BISHOP_RAYS = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val ROOK_RAYS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /**
     * Retourne la liste exhaustive des coups strictement légaux pour la position donnée.
     */
    fun generateLegalMoves(position: ChessPosition): List<ChessMove> {
        val pseudoLegal = generatePseudoLegalMoves(position)
        val color = position.sideToMove
        return pseudoLegal.filter { move ->
            val next = applyMoveUnchecked(position, move)
            !isKingInCheck(next, color)
        }
    }

    /**
     * Génère les coups légaux partant d'une case précise.
     */
    fun generateLegalMovesFrom(position: ChessPosition, from: Square): List<ChessMove> {
        val piece = position.pieceAt(from) ?: return emptyList()
        if (piece.color != position.sideToMove) return emptyList()
        return generateLegalMoves(position).filter { it.from == from }
    }

    /**
     * Indique si le roi de [color] est actuellement en échec.
     */
    fun isKingInCheck(position: ChessPosition, color: PieceColor): Boolean {
        val kingSquare = findKingSquare(position, color) ?: return false
        return isSquareAttacked(position, kingSquare, color.opposite())
    }

    /**
     * Retourne la case du roi de [color], ou null si introuvable.
     */
    fun findKingSquare(position: ChessPosition, color: PieceColor): Square? {
        for (idx in 0..63) {
            val p = position.board[idx]
            if (p != null && p.color == color && p.type == PieceType.KING) {
                return Square(idx)
            }
        }
        return null
    }

    /**
     * Vérifie si [target] est attaquée par au moins une pièce de couleur [attackerColor].
     */
    fun isSquareAttacked(position: ChessPosition, target: Square, attackerColor: PieceColor): Boolean {
        val tf = target.file
        val tr = target.rank

        // 1. Attaques de pions (direction inverse depuis la cible)
        val pawnAttackRank = if (attackerColor == PieceColor.WHITE) tr - 1 else tr + 1
        if (pawnAttackRank in 0..7) {
            for (df in intArrayOf(-1, 1)) {
                val pf = tf + df
                if (pf in 0..7) {
                    val p = position.pieceAt(pf, pawnAttackRank)
                    if (p != null && p.color == attackerColor && p.type == PieceType.PAWN) {
                        return true
                    }
                }
            }
        }

        // 2. Attaques de cavaliers
        for ((df, dr) in KNIGHT_OFFSETS) {
            val nf = tf + df
            val nr = tr + dr
            if (nf in 0..7 && nr in 0..7) {
                val p = position.pieceAt(nf, nr)
                if (p != null && p.color == attackerColor && p.type == PieceType.KNIGHT) {
                    return true
                }
            }
        }

        // 3. Attaques du roi adverse
        for ((df, dr) in KING_OFFSETS) {
            val kf = tf + df
            val kr = tr + dr
            if (kf in 0..7 && kr in 0..7) {
                val p = position.pieceAt(kf, kr)
                if (p != null && p.color == attackerColor && p.type == PieceType.KING) {
                    return true
                }
            }
        }

        // 4. Rayons diagonaux (Fou / Dame)
        for ((df, dr) in BISHOP_RAYS) {
            var f = tf + df
            var r = tr + dr
            while (f in 0..7 && r in 0..7) {
                val p = position.pieceAt(f, r)
                if (p != null) {
                    if (p.color == attackerColor &&
                        (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)
                    ) {
                        return true
                    }
                    break
                }
                f += df
                r += dr
            }
        }

        // 5. Rayons orthogonaux (Tour / Dame)
        for ((df, dr) in ROOK_RAYS) {
            var f = tf + df
            var r = tr + dr
            while (f in 0..7 && r in 0..7) {
                val p = position.pieceAt(f, r)
                if (p != null) {
                    if (p.color == attackerColor &&
                        (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)
                    ) {
                        return true
                    }
                    break
                }
                f += df
                r += dr
            }
        }

        return false
    }

    /**
     * Applique un coup déjà validé (ou en cours de validation) pour produire la position suivante.
     */
    fun applyMoveUnchecked(position: ChessPosition, move: ChessMove): ChessPosition {
        val newBoard = position.board.toMutableList()
        val movingPiece = newBoard[move.from.index]
            ?: error("Aucune pièce sur la case de départ ${move.from.toAlgebraic()}")
        val targetPiece = newBoard[move.to.index]

        // Déplacer la pièce (avec promotion éventuelle)
        newBoard[move.from.index] = null
        newBoard[move.to.index] = if (move.promotion != null) {
            Piece(move.promotion, movingPiece.color)
        } else {
            movingPiece
        }

        // Prise en passant : retirer le pion adverse capturé sur la même rangée que la case de départ
        if (move.isEnPassant) {
            val capturedPawnSq = Square(move.to.file, move.from.rank)
            newBoard[capturedPawnSq.index] = null
        }

        // Roques : déplacer la tour correspondante
        if (move.isKingSideCastle) {
            val rank = move.from.rank
            val rookFrom = Square(7, rank)
            val rookTo = Square(5, rank)
            newBoard[rookTo.index] = newBoard[rookFrom.index]
            newBoard[rookFrom.index] = null
        } else if (move.isQueenSideCastle) {
            val rank = move.from.rank
            val rookFrom = Square(0, rank)
            val rookTo = Square(3, rank)
            newBoard[rookTo.index] = newBoard[rookFrom.index]
            newBoard[rookFrom.index] = null
        }

        // Mise à jour des droits de roque (mouvement roi/tour OU capture sur case de départ tour)
        val rights = updateCastlingRights(position.castlingRights, move.from, move.to, movingPiece)

        // Mise à jour de la case de prise en passant (uniquement si double pas de pion)
        val nextEnPassant = if (movingPiece.type == PieceType.PAWN &&
            kotlin.math.abs(move.to.rank - move.from.rank) == 2
        ) {
            val midRank = (move.from.rank + move.to.rank) / 2
            Square(move.from.file, midRank)
        } else {
            null
        }

        // Compteur des 50 coups (remis à 0 sur mouvement de pion ou capture)
        val isResetClock = movingPiece.type == PieceType.PAWN || targetPiece != null || move.isEnPassant
        val nextHalfmove = if (isResetClock) 0 else position.halfmoveClock + 1
        val nextFullmove = if (position.sideToMove == PieceColor.BLACK) {
            position.fullmoveNumber + 1
        } else {
            position.fullmoveNumber
        }

        return ChessPosition(
            board = newBoard,
            sideToMove = position.sideToMove.opposite(),
            castlingRights = rights,
            enPassantTarget = nextEnPassant,
            halfmoveClock = nextHalfmove,
            fullmoveNumber = nextFullmove
        )
    }

    private fun updateCastlingRights(
        current: CastlingRights,
        from: Square,
        to: Square,
        movingPiece: Piece
    ): CastlingRights {
        var wk = current.whiteKingSide
        var wq = current.whiteQueenSide
        var bk = current.blackKingSide
        var bq = current.blackQueenSide

        if (movingPiece.type == PieceType.KING) {
            if (movingPiece.color == PieceColor.WHITE) {
                wk = false
                wq = false
            } else {
                bk = false
                bq = false
            }
        }

        // Si une tour bouge ou est capturée sur sa case d'origine
        val touchedSquares = intArrayOf(from.index, to.index)
        for (sq in touchedSquares) {
            when (sq) {
                0 -> wq = false  // a1
                7 -> wk = false  // h1
                56 -> bq = false // a8
                63 -> bk = false // h8
            }
        }

        return CastlingRights(wk, wq, bk, bq)
    }

    private fun generatePseudoLegalMoves(position: ChessPosition): List<ChessMove> {
        val moves = ArrayList<ChessMove>(48)
        val color = position.sideToMove

        for (idx in 0..63) {
            val piece = position.board[idx] ?: continue
            if (piece.color != color) continue
            val from = Square(idx)

            when (piece.type) {
                PieceType.PAWN -> addPawnMoves(position, from, color, moves)
                PieceType.KNIGHT -> addJumpMoves(position, from, color, KNIGHT_OFFSETS, moves)
                PieceType.BISHOP -> addSlidingMoves(position, from, color, BISHOP_RAYS, moves)
                PieceType.ROOK -> addSlidingMoves(position, from, color, ROOK_RAYS, moves)
                PieceType.QUEEN -> {
                    addSlidingMoves(position, from, color, BISHOP_RAYS, moves)
                    addSlidingMoves(position, from, color, ROOK_RAYS, moves)
                }
                PieceType.KING -> {
                    addJumpMoves(position, from, color, KING_OFFSETS, moves)
                    addCastlingMoves(position, from, color, moves)
                }
            }
        }
        return moves
    }

    private fun addPawnMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        out: MutableList<ChessMove>
    ) {
        val dir = if (color == PieceColor.WHITE) 1 else -1
        val startRank = if (color == PieceColor.WHITE) 1 else 6
        val promoRank = if (color == PieceColor.WHITE) 7 else 0

        val oneRank = from.rank + dir
        if (oneRank in 0..7) {
            val oneAhead = Square(from.file, oneRank)
            if (position.pieceAt(oneAhead) == null) {
                if (oneRank == promoRank) {
                    addPromotionMoves(from, oneAhead, isCapture = false, out = out)
                } else {
                    out.add(ChessMove(from, oneAhead))
                    // Double avance depuis la rangée initiale
                    if (from.rank == startRank) {
                        val twoAhead = Square(from.file, from.rank + 2 * dir)
                        if (position.pieceAt(twoAhead) == null) {
                            out.add(ChessMove(from, twoAhead))
                        }
                    }
                }
            }

            // Captures diagonales et prise en passant
            for (df in intArrayOf(-1, 1)) {
                val targetFile = from.file + df
                if (targetFile !in 0..7) continue
                val targetSq = Square(targetFile, oneRank)
                val targetPiece = position.pieceAt(targetSq)

                if (targetPiece != null && targetPiece.color != color) {
                    if (oneRank == promoRank) {
                        addPromotionMoves(from, targetSq, isCapture = true, out = out)
                    } else {
                        out.add(ChessMove(from, targetSq, isCapture = true))
                    }
                } else if (position.enPassantTarget == targetSq) {
                    // Vérification supplémentaire qu'un pion adverse est bien sur la case capturée
                    val adjacentPawn = position.pieceAt(targetFile, from.rank)
                    if (adjacentPawn != null &&
                        adjacentPawn.color != color &&
                        adjacentPawn.type == PieceType.PAWN
                    ) {
                        out.add(ChessMove(from, targetSq, isCapture = true, isEnPassant = true))
                    }
                }
            }
        }
    }

    private fun addPromotionMoves(
        from: Square,
        to: Square,
        isCapture: Boolean,
        out: MutableList<ChessMove>
    ) {
        for (promo in arrayOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
            out.add(ChessMove(from, to, promotion = promo, isCapture = isCapture))
        }
    }

    private fun addJumpMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        offsets: Array<Pair<Int, Int>>,
        out: MutableList<ChessMove>
    ) {
        for ((df, dr) in offsets) {
            val tf = from.file + df
            val tr = from.rank + dr
            if (tf !in 0..7 || tr !in 0..7) continue
            val to = Square(tf, tr)
            val target = position.pieceAt(to)
            if (target == null) {
                out.add(ChessMove(from, to, isCapture = false))
            } else if (target.color != color) {
                out.add(ChessMove(from, to, isCapture = true))
            }
        }
    }

    private fun addSlidingMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        rays: Array<Pair<Int, Int>>,
        out: MutableList<ChessMove>
    ) {
        for ((df, dr) in rays) {
            var f = from.file + df
            var r = from.rank + dr
            while (f in 0..7 && r in 0..7) {
                val to = Square(f, r)
                val target = position.pieceAt(to)
                if (target == null) {
                    out.add(ChessMove(from, to, isCapture = false))
                } else {
                    if (target.color != color) {
                        out.add(ChessMove(from, to, isCapture = true))
                    }
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun addCastlingMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        out: MutableList<ChessMove>
    ) {
        val expectedRank = if (color == PieceColor.WHITE) 0 else 7
        if (from.file != 4 || from.rank != expectedRank) return

        val opponent = color.opposite()
        // Le roi ne doit pas être en échec au départ
        if (isSquareAttacked(position, from, opponent)) return

        val canKingSide = if (color == PieceColor.WHITE) {
            position.castlingRights.whiteKingSide
        } else {
            position.castlingRights.blackKingSide
        }

        if (canKingSide) {
            val fSq = Square(5, expectedRank)
            val gSq = Square(6, expectedRank)
            val hSq = Square(7, expectedRank)
            val rook = position.pieceAt(hSq)
            if (rook != null && rook.color == color && rook.type == PieceType.ROOK &&
                position.pieceAt(fSq) == null &&
                position.pieceAt(gSq) == null &&
                !isSquareAttacked(position, fSq, opponent) &&
                !isSquareAttacked(position, gSq, opponent)
            ) {
                out.add(ChessMove(from, gSq, isKingSideCastle = true))
            }
        }

        val canQueenSide = if (color == PieceColor.WHITE) {
            position.castlingRights.whiteQueenSide
        } else {
            position.castlingRights.blackQueenSide
        }

        if (canQueenSide) {
            val dSq = Square(3, expectedRank)
            val cSq = Square(2, expectedRank)
            val bSq = Square(1, expectedRank)
            val aSq = Square(0, expectedRank)
            val rook = position.pieceAt(aSq)
            if (rook != null && rook.color == color && rook.type == PieceType.ROOK &&
                position.pieceAt(dSq) == null &&
                position.pieceAt(cSq) == null &&
                position.pieceAt(bSq) == null &&
                !isSquareAttacked(position, dSq, opponent) &&
                !isSquareAttacked(position, cSq, opponent)
            ) {
                out.add(ChessMove(from, cSq, isQueenSideCastle = true))
            }
        }
    }
}
