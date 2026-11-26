package com.chessoffline.app.coach

import com.chessoffline.core.model.ChessMove
import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.model.Square
import com.chessoffline.core.notation.FenCodec
import com.chessoffline.core.rules.MoveGenerator
import com.chessoffline.engine.uci.UciScore
import kotlin.math.abs
import kotlin.random.Random

/**
 * Analyseur tactique et positionnel du Coach :
 * - Compare l'évaluation Stockfish avant/après le coup + inspecte la position dans `:core-chess`
 * - Respecte strictement la hiérarchie de priorité des 20 catégories
 * - Limite le bruit sur les coups banals (anti-spam)
 */
class CoachAnalyzer(
    private val random: Random = Random.Default
) {
    private var movesSinceLastMundaneComment = 0
    private var movesSinceLastBotStatusComment = 0

    fun reset() {
        movesSinceLastMundaneComment = 0
        movesSinceLastBotStatusComment = 0
    }

    /**
     * Analyse un coup et retourne éventuellement une [CoachDetection] à commenter,
     * ou `null` si le coup est banal et filtré par l'anti-spam.
     */
    fun analyzeMove(input: CoachAnalysisInput): CoachDetection? {
        val moverColor = input.prePosition.sideToMove
        val opponentColor = moverColor.opposite()
        val movedPiece = input.prePosition.pieceAt(input.moveRecord.move.from)
        val movedPieceType = movedPiece?.type ?: PieceType.PAWN
        val movedPieceNameFr = CoachCommentaryPicker.pieceNameFrLower(movedPieceType)
        val playedUci = input.moveRecord.move.toUci()
        val playedSan = input.moveRecord.san.trimEnd('+', '#')
        val destSquare = input.moveRecord.move.to.toAlgebraic()
        val openingName = input.openingEntry?.nameFr

        val bestMoveUci = input.preEval?.bestMoveUci?.takeIf { it != playedUci }
        val bestMoveSan = bestMoveUci?.let { uciToSanOrNull(input.prePosition, it) }

        fun makeDetection(
            category: CoachCategory,
            squareOverride: String? = destSquare,
            pieceOverride: String? = movedPieceNameFr
        ) = CoachDetection(
            category = category,
            playedMoveSan = playedSan,
            bestMoveUci = bestMoveUci,
            bestMoveSan = bestMoveSan,
            squareAlgebraic = squareOverride,
            pieceNameFrLower = pieceOverride,
            openingNameFr = openingName
        )

        // 1. Fin de partie (priorité absolue)
        if (input.gameStatus.isTerminal) {
            return makeDetection(CoachCategory.GAME_END_SUMMARY)
        }

        val prePlayerCp = scoreToPlayerCp(input.preEval?.score, moverColor)
        val postPlayerCp = scoreToPlayerCp(input.postEval?.score, moverColor)
        val evalDropCp = if (prePlayerCp != null && postPlayerCp != null) {
            (prePlayerCp - postPlayerCp).coerceAtLeast(0)
        } else {
            null
        }

        // Si c'est un coup du joueur (ou en mode 2 joueurs / analyse)
        if (input.isPlayerMove) {
            val preScore = input.preEval?.score
            val postScore = input.postEval?.score

            // 2. Mat autorisé / Mat manqué
            if (isMateAgainst(postScore, moverColor) && !isMateAgainst(preScore, moverColor)) {
                movesSinceLastMundaneComment = 0
                return makeDetection(CoachCategory.PLAYER_ALLOWED_MATE)
            }
            if (isMateFor(preScore, moverColor) && !isMateFor(postScore, moverColor)) {
                movesSinceLastMundaneComment = 0
                return makeDetection(CoachCategory.PLAYER_MISSED_MATE)
            }

            // 3. Pièce laissée sans défense (gaffe tactique détectable même sans grande latence)
            val hangingInfo = findNewlyHangingFriendlyPiece(
                prePos = input.prePosition,
                postPos = input.postPosition,
                friendlyColor = moverColor,
                movedTo = input.moveRecord.move.to
            )
            if (hangingInfo != null && (evalDropCp == null || evalDropCp >= 160)) {
                movesSinceLastMundaneComment = 0
                return makeDetection(
                    category = CoachCategory.PLAYER_BLUNDER_HANGING_PIECE,
                    squareOverride = hangingInfo.first.toAlgebraic(),
                    pieceOverride = CoachCommentaryPicker.pieceNameFrLower(hangingInfo.second.type)
                )
            }

            // 4. Gaffe / Erreur / Imprécision selon la perte en centipions
            if (evalDropCp != null) {
                when {
                    evalDropCp >= 250 -> {
                        movesSinceLastMundaneComment = 0
                        return makeDetection(CoachCategory.PLAYER_BLUNDER_GENERIC)
                    }
                    evalDropCp >= 120 -> {
                        movesSinceLastMundaneComment = 0
                        return makeDetection(CoachCategory.PLAYER_MISTAKE)
                    }
                    evalDropCp >= 55 -> {
                        movesSinceLastMundaneComment = 0
                        return makeDetection(CoachCategory.PLAYER_INACCURACY)
                    }
                }
            }

            // 5. Coup brillant / Meilleur coup
            val engineBestUci = input.preEval?.bestMoveUci
            val matchedBest = engineBestUci != null && engineBestUci == playedUci
            if (matchedBest && isBrilliantSacrificeOrTactic(input, moverColor, postPlayerCp)) {
                movesSinceLastMundaneComment = 0
                return makeDetection(CoachCategory.PLAYER_BRILLIANT)
            }

            if (matchedBest && (evalDropCp == null || evalDropCp <= 12) &&
                (movesSinceLastMundaneComment >= 2 || input.moveRecord.isCheckAfter || input.moveRecord.move.isCapture)
            ) {
                movesSinceLastMundaneComment = 0
                return makeDetection(CoachCategory.PLAYER_BEST_MOVE)
            }

            // 6. Roque bénéfique
            if (input.moveRecord.move.isKingSideCastle || input.moveRecord.move.isQueenSideCastle) {
                movesSinceLastMundaneComment = 0
                return makeDetection(CoachCategory.CASTLING_GOOD)
            }

            // 7. Tactique : échec ou fourchette (double attaque sur pièces adverses)
            val forkTargetSquare = detectCheckOrForkSquare(
                postPos = input.postPosition,
                move = input.moveRecord.move,
                moverColor = moverColor,
                isCheckAfter = input.moveRecord.isCheckAfter
            )
            if (forkTargetSquare != null && (evalDropCp == null || evalDropCp < 45)) {
                movesSinceLastMundaneComment = 0
                return makeDetection(
                    category = CoachCategory.TACTIC_CHECK_OR_FORK,
                    squareOverride = forkTargetSquare
                )
            }

            // 8. Principes d'ouverture (12 à 14 premiers demi-coups)
            if (input.plyCount <= 14) {
                val openingEval = evaluateOpeningPrinciples(
                    prePos = input.prePosition,
                    postPos = input.postPosition,
                    move = input.moveRecord.move,
                    movedType = movedPieceType,
                    moverColor = moverColor,
                    plyCount = input.plyCount
                )
                if (openingEval != null && (evalDropCp == null || evalDropCp < 55)) {
                    movesSinceLastMundaneComment = 0
                    return makeDetection(openingEval)
                }
            }

            // 9. Finale (Roi actif / Pion passé)
            if (isEndgame(input.postPosition)) {
                if (movedPieceType == PieceType.KING &&
                    isMovingTowardCenter(input.moveRecord.move.from, input.moveRecord.move.to)
                ) {
                    movesSinceLastMundaneComment = 0
                    return makeDetection(CoachCategory.ENDGAME_KING_ACTIVE)
                }
                if (movedPieceType == PieceType.PAWN &&
                    isPassedPawn(input.postPosition, input.moveRecord.move.to, moverColor)
                ) {
                    movesSinceLastMundaneComment = 0
                    return makeDetection(CoachCategory.ENDGAME_PASSED_PAWN)
                }
            }

            // 10. Pression de temps (< 30 secondes)
            if (input.hasClock &&
                input.remainingPlayerTimeMs != null &&
                input.remainingPlayerTimeMs in 1L..29_999L
            ) {
                movesSinceLastMundaneComment = 0
                return makeDetection(CoachCategory.TIME_PRESSURE)
            }

            // 11. Bon coup banal (commenté occasionnellement seulement pour ne pas spammer)
            movesSinceLastMundaneComment++
            if ((evalDropCp == null || evalDropCp <= 30) &&
                movesSinceLastMundaneComment >= 3 &&
                random.nextFloat() < 0.40f
            ) {
                movesSinceLastMundaneComment = 0
                return makeDetection(CoachCategory.PLAYER_GOOD_MOVE)
            }

            return null
        }

        // Réactions lorsque c'est le bot qui vient de jouer
        val botColor = moverColor
        val botScoreCp = scoreToPlayerCp(input.postEval?.score, botColor)

        val isBotTricky = input.moveRecord.isCheckAfter ||
            detectForkTargetsCount(input.postPosition, input.moveRecord.move.to, botColor) >= 2
        if (isBotTricky && (botScoreCp == null || botScoreCp >= -80)) {
            movesSinceLastBotStatusComment = 0
            return makeDetection(CoachCategory.BOT_PLAYED_TRICKY)
        }

        movesSinceLastBotStatusComment++
        if (botScoreCp != null && movesSinceLastBotStatusComment >= 3) {
            if (botScoreCp >= 240 && random.nextFloat() < 0.45f) {
                movesSinceLastBotStatusComment = 0
                return makeDetection(CoachCategory.BOT_WINNING)
            }
            if (botScoreCp <= -240 && random.nextFloat() < 0.45f) {
                movesSinceLastBotStatusComment = 0
                return makeDetection(CoachCategory.BOT_IN_TROUBLE)
            }
        }

        return null
    }

    private fun uciToSanOrNull(position: ChessPosition, uci: String): String? {
        if (uci.length < 4) return null
        val from = Square.fromAlgebraic(uci.substring(0, 2)) ?: return null
        val to = Square.fromAlgebraic(uci.substring(2, 4)) ?: return null
        val promo = if (uci.length >= 5) PieceType.fromFenChar(uci[4]) else null
        val legal = MoveGenerator.generateLegalMoves(position)
        val match = legal.find { it.from == from && it.to == to && it.promotion == promo } ?: return null
        return FenCodec.toSan(position, match, legal)
    }

    private fun scoreToPlayerCp(score: UciScore?, playerColor: PieceColor): Int? {
        if (score == null) return null
        val whitePerspective = when (score) {
            is UciScore.Centipawns -> score.cp
            is UciScore.Mate -> if (score.moves > 0) 10_000 - score.moves * 10 else -10_000 - score.moves * 10
        }
        return if (playerColor == PieceColor.WHITE) whitePerspective else -whitePerspective
    }

    private fun isMateFor(score: UciScore?, color: PieceColor): Boolean {
        if (score !is UciScore.Mate) return false
        return if (color == PieceColor.WHITE) score.moves > 0 else score.moves < 0
    }

    private fun isMateAgainst(score: UciScore?, color: PieceColor): Boolean {
        if (score !is UciScore.Mate) return false
        return if (color == PieceColor.WHITE) score.moves < 0 else score.moves > 0
    }

    /**
     * Détecte si une pièce amie de valeur >= 3 (cavalier, fou, tour, dame) ou la pièce qui vient de jouer
     * se retrouve attaquée par l'adversaire et sans défenseur ami sur [postPos].
     */
    private fun findNewlyHangingFriendlyPiece(
        prePos: ChessPosition,
        postPos: ChessPosition,
        friendlyColor: PieceColor,
        movedTo: Square
    ): Pair<Square, Piece>? {
        val enemy = friendlyColor.opposite()

        // Vérifier d'abord la pièce déplacée si elle vaut >= 3 et n'était pas un échange gagnant
        val movedPiece = postPos.pieceAt(movedTo)
        if (movedPiece != null &&
            movedPiece.color == friendlyColor &&
            movedPiece.type != PieceType.KING &&
            movedPiece.type.materialValue >= 3 &&
            MoveGenerator.isSquareAttacked(postPos, movedTo, enemy) &&
            !isSquareDefendedByFriendly(postPos, movedTo, friendlyColor)
        ) {
            val capturedBefore = prePos.pieceAt(movedTo)
            if (capturedBefore == null || capturedBefore.type.materialValue < movedPiece.type.materialValue) {
                return movedTo to movedPiece
            }
        }

        // Vérifier les autres pièces amies de valeur >= 3 qui viennent d'être exposées
        var worst: Pair<Square, Piece>? = null
        for (idx in 0..63) {
            val sq = Square(idx)
            if (sq == movedTo) continue
            val p = postPos.pieceAt(sq) ?: continue
            if (p.color != friendlyColor || p.type == PieceType.KING || p.type.materialValue < 3) continue

            val attackedAfter = MoveGenerator.isSquareAttacked(postPos, sq, enemy)
            val defendedAfter = isSquareDefendedByFriendly(postPos, sq, friendlyColor)
            if (attackedAfter && !defendedAfter) {
                val wasAlreadyHanging = MoveGenerator.isSquareAttacked(prePos, sq, enemy) &&
                    !isSquareDefendedByFriendly(prePos, sq, friendlyColor)
                if (!wasAlreadyHanging) {
                    if (worst == null || p.type.materialValue > worst.second.type.materialValue) {
                        worst = sq to p
                    }
                }
            }
        }
        return worst
    }

    /**
     * Vérifie si [target] est protégée par une autre pièce de couleur [friendlyColor].
     * On simule temporairement le remplacement de [target] par une pièce adverse pour réutiliser
     * `MoveGenerator.isSquareAttacked` (captures de pions incluses !).
     */
    private fun isSquareDefendedByFriendly(
        position: ChessPosition,
        target: Square,
        friendlyColor: PieceColor
    ): Boolean {
        val mutableBoard = position.board.toMutableList()
        mutableBoard[target.index] = Piece(PieceType.PAWN, friendlyColor.opposite())
        val probePos = position.copy(board = mutableBoard)
        return MoveGenerator.isSquareAttacked(probePos, target, friendlyColor)
    }

    private fun isBrilliantSacrificeOrTactic(
        input: CoachAnalysisInput,
        moverColor: PieceColor,
        postPlayerCp: Int?
    ): Boolean {
        if (postPlayerCp == null || postPlayerCp < 80) return false
        val move = input.moveRecord.move
        val movedPiece = input.postPosition.pieceAt(move.to) ?: return false
        if (movedPiece.type == PieceType.PAWN || movedPiece.type == PieceType.KING) return false

        val enemy = moverColor.opposite()
        val destAttacked = MoveGenerator.isSquareAttacked(input.postPosition, move.to, enemy)
        val capturedVal = input.moveRecord.capturedPiece?.type?.materialValue ?: 0
        // La pièce se place volontairement en prise (sacrifice) tout en gardant un net avantage Stockfish
        return destAttacked && movedPiece.type.materialValue > capturedVal
    }

    private fun detectCheckOrForkSquare(
        postPos: ChessPosition,
        move: ChessMove,
        moverColor: PieceColor,
        isCheckAfter: Boolean
    ): String? {
        val forkTargets = detectForkTargetsCount(postPos, move.to, moverColor)
        return when {
            forkTargets >= 2 -> move.to.toAlgebraic()
            isCheckAfter && (move.isCapture || forkTargets >= 1) -> move.to.toAlgebraic()
            isCheckAfter -> move.to.toAlgebraic()
            else -> null
        }
    }

    private fun detectForkTargetsCount(
        postPos: ChessPosition,
        attackerSq: Square,
        attackerColor: PieceColor
    ): Int {
        val attacker = postPos.pieceAt(attackerSq) ?: return 0
        if (attacker.color != attackerColor) return 0

        val enemy = attackerColor.opposite()
        val attackedSquares = attackedSquaresFrom(postPos, attackerSq, attacker)
        var valuableTargets = 0
        for (sq in attackedSquares) {
            val victim = postPos.pieceAt(sq) ?: continue
            if (victim.color != enemy) continue
            val isHigherValue = victim.type == PieceType.KING ||
                victim.type.materialValue > attacker.type.materialValue
            val isUndefendedMajorOrMinor = victim.type.materialValue >= 3 &&
                !isSquareDefendedByFriendly(postPos, sq, enemy)
            if (isHigherValue || isUndefendedMajorOrMinor) {
                valuableTargets++
            }
        }
        return valuableTargets
    }

    private fun attackedSquaresFrom(
        position: ChessPosition,
        from: Square,
        piece: Piece
    ): List<Square> {
        val result = ArrayList<Square>()
        val f0 = from.file
        val r0 = from.rank
        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.color == PieceColor.WHITE) 1 else -1
                for (df in intArrayOf(-1, 1)) {
                    Square.ofOrNull(f0 + df, r0 + dir)?.let { result.add(it) }
                }
            }
            PieceType.KNIGHT -> {
                val offsets = arrayOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
                for ((df, dr) in offsets) {
                    Square.ofOrNull(f0 + df, r0 + dr)?.let { result.add(it) }
                }
            }
            PieceType.KING -> {
                for (df in -1..1) {
                    for (dr in -1..1) {
                        if (df != 0 || dr != 0) {
                            Square.ofOrNull(f0 + df, r0 + dr)?.let { result.add(it) }
                        }
                    }
                }
            }
            PieceType.BISHOP -> addRayAttacks(position, f0, r0, BISHOP_DIRS, result)
            PieceType.ROOK -> addRayAttacks(position, f0, r0, ROOK_DIRS, result)
            PieceType.QUEEN -> {
                addRayAttacks(position, f0, r0, BISHOP_DIRS, result)
                addRayAttacks(position, f0, r0, ROOK_DIRS, result)
            }
        }
        return result
    }

    private fun addRayAttacks(
        position: ChessPosition,
        f0: Int,
        r0: Int,
        dirs: Array<Pair<Int, Int>>,
        out: MutableList<Square>
    ) {
        for ((df, dr) in dirs) {
            var f = f0 + df
            var r = r0 + dr
            while (f in 0..7 && r in 0..7) {
                val sq = Square(f, r)
                out.add(sq)
                if (position.pieceAt(sq) != null) break
                f += df
                r += dr
            }
        }
    }

    private fun evaluateOpeningPrinciples(
        prePos: ChessPosition,
        postPos: ChessPosition,
        move: ChessMove,
        movedType: PieceType,
        moverColor: PieceColor,
        plyCount: Int
    ): CoachCategory? {
        val homeRank = if (moverColor == PieceColor.WHITE) 0 else 7

        // Avertissement : sortir la Dame trop tôt sans capture
        if (movedType == PieceType.QUEEN && plyCount <= 10 && !move.isCapture &&
            move.from.rank == homeRank
        ) {
            return CoachCategory.OPENING_WARNING
        }

        // Avertissement : pousser un pion de bord (a/h) très tôt sans développement mineur
        if (movedType == PieceType.PAWN && plyCount <= 8 && (move.from.file == 0 || move.from.file == 7) &&
            !move.isCapture && countDevelopedMinors(prePos, moverColor) < 2
        ) {
            return CoachCategory.OPENING_WARNING
        }

        // Bon principe : développement d'un cavalier/fou depuis sa rangée de départ vers le centre
        if ((movedType == PieceType.KNIGHT || movedType == PieceType.BISHOP) &&
            move.from.rank == homeRank &&
            move.to.rank != homeRank &&
            move.to.file in 1..6
        ) {
            // Limiter pour ne pas commenter chaque développement mineur
            if (plyCount in 3..10 && random.nextFloat() < 0.45f) {
                return CoachCategory.OPENING_GOOD_PRINCIPLES
            }
        }

        // Bon principe : occupation du centre (d4, d5, e4, e5) avec un pion au début
        if (movedType == PieceType.PAWN && plyCount <= 4 &&
            move.to.file in 3..4 && move.to.rank in 3..4 &&
            random.nextFloat() < 0.50f
        ) {
            return CoachCategory.OPENING_GOOD_PRINCIPLES
        }

        return null
    }

    private fun countDevelopedMinors(position: ChessPosition, color: PieceColor): Int {
        val homeRank = if (color == PieceColor.WHITE) 0 else 7
        var developed = 0
        for (idx in 0..63) {
            val sq = Square(idx)
            val p = position.pieceAt(sq) ?: continue
            if (p.color == color &&
                (p.type == PieceType.KNIGHT || p.type == PieceType.BISHOP) &&
                sq.rank != homeRank
            ) {
                developed++
            }
        }
        return developed
    }

    private fun isEndgame(position: ChessPosition): Boolean {
        var whiteNonKing = 0
        var blackNonKing = 0
        for (p in position.board) {
            if (p == null || p.type == PieceType.KING) continue
            if (p.color == PieceColor.WHITE) {
                whiteNonKing += p.type.materialValue
            } else {
                blackNonKing += p.type.materialValue
            }
        }
        return whiteNonKing <= 13 && blackNonKing <= 13
    }

    private fun isMovingTowardCenter(from: Square, to: Square): Boolean {
        val distFrom = abs(from.file - 3.5) + abs(from.rank - 3.5)
        val distTo = abs(to.file - 3.5) + abs(to.rank - 3.5)
        return distTo < distFrom
    }

    private fun isPassedPawn(position: ChessPosition, pawnSq: Square, color: PieceColor): Boolean {
        val enemy = color.opposite()
        val direction = if (color == PieceColor.WHITE) 1 else -1
        var r = pawnSq.rank + direction
        while (r in 0..7) {
            for (df in -1..1) {
                val f = pawnSq.file + df
                if (f in 0..7) {
                    val p = position.pieceAt(f, r)
                    if (p != null && p.color == enemy && p.type == PieceType.PAWN) {
                        return false
                    }
                }
            }
            r += direction
        }
        return true
    }

    private companion object {
        val BISHOP_DIRS = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        val ROOK_DIRS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}
