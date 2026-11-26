package com.chessoffline.app

import com.chessoffline.app.coach.CoachAnalysisInput
import com.chessoffline.app.coach.CoachAnalyzer
import com.chessoffline.app.coach.CoachCategory
import com.chessoffline.app.coach.CoachCommentaryPicker
import com.chessoffline.app.coach.CoachDetection
import com.chessoffline.app.openings.OpeningBookIndex
import com.chessoffline.app.openings.RawOpeningEntry
import com.chessoffline.core.model.GameStatus
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.notation.FenCodec
import com.chessoffline.core.rules.ChessGame
import com.chessoffline.engine.AnalysisSnapshot
import com.chessoffline.engine.uci.UciScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OpeningAndCoachTest {

    @Test
    fun `opening index matches transpositions in O(1)`() {
        val index = OpeningBookIndex.buildFromRaw(
            listOf(
                RawOpeningEntry(
                    eco = "E10",
                    nameFr = "Défense Est-Indienne / Jeu de la Dame",
                    pgn = "1. d4 Nf6 2. c4",
                    idea = "Contrôle du centre avec c4 et d4."
                )
            )
        )

        // Ordre transposé : 1. c4 Nf6 2. d4 aboutit à la même clé FEN simplifiée
        val transposedGame = ChessGame()
        transposedGame.loadPgn("1. c4 Nf6 2. d4").getOrThrow()

        val found = index.findByFen(transposedGame.currentFen)
        assertNotNull(found)
        assertEquals("E10", found?.eco)
    }

    @Test
    fun `opening book proposes continuations within ply cap`() {
        val index = OpeningBookIndex.buildFromRaw(
            listOf(
                RawOpeningEntry(
                    eco = "C50",
                    nameFr = "Partie Italienne",
                    pgn = "1. e4 e5 2. Nf3 Nc6 3. Bc4",
                    idea = "Pression rapide sur f7."
                )
            )
        )

        val game = ChessGame()
        game.loadPgn("1. e4 e5").getOrThrow()

        val bookMove = index.pickBookMove(game.currentFen, plyCount = 2, maxBookPlies = 16)
        assertEquals("g1f3", bookMove)

        val outOfWindow = index.pickBookMove(game.currentFen, plyCount = 16, maxBookPlies = 16)
        assertNull(outOfWindow)
    }

    @Test
    fun `piece placeholder stays lowercase mid-sentence and capitalizes at start`() {
        assertEquals("pion", CoachCommentaryPicker.pieceNameFrLower(PieceType.PAWN))
        assertEquals("cavalier", CoachCommentaryPicker.pieceNameFrLower(PieceType.KNIGHT))
        assertEquals("fou", CoachCommentaryPicker.pieceNameFrLower(PieceType.BISHOP))
        assertEquals("tour", CoachCommentaryPicker.pieceNameFrLower(PieceType.ROOK))
        assertEquals("dame", CoachCommentaryPicker.pieceNameFrLower(PieceType.QUEEN))
        assertEquals("roi", CoachCommentaryPicker.pieceNameFrLower(PieceType.KING))

        val detection = CoachDetection(
            category = CoachCategory.PLAYER_BLUNDER_HANGING_PIECE,
            playedMoveSan = "Cg5",
            bestMoveSan = "Cf3",
            squareAlgebraic = "g5",
            pieceNameFrLower = "cavalier",
            openingNameFr = "Partie Italienne"
        )

        val midSentence = CoachCommentaryPicker.formatTemplate(
            "Attention, ton {piece} en {square} est sans défense après {playedMove} !",
            detection
        )
        assertEquals("Attention, ton cavalier en g5 est sans défense après Cg5 !", midSentence)

        val startSentence = CoachCommentaryPicker.formatTemplate(
            "{piece} en {square} est en danger. {piece} aurait dû aller ailleurs.",
            detection
        )
        assertEquals("Cavalier en g5 est en danger. Cavalier aurait dû aller ailleurs.", startSentence)
    }

    @Test
    fun `analyzer prioritizes allowed mate over generic blunder`() {
        val analyzer = CoachAnalyzer(Random(42))
        val game = ChessGame()
        val prePos = game.currentPosition
        val record = game.playUciMove("f2f3")!!
        val postPos = game.currentPosition

        val detection = analyzer.analyzeMove(
            CoachAnalysisInput(
                prePosition = prePos,
                postPosition = postPos,
                moveRecord = record,
                isPlayerMove = true,
                preEval = AnalysisSnapshot(score = UciScore.Centipawns(0), bestMoveUci = "e2e4"),
                postEval = AnalysisSnapshot(score = UciScore.Mate(-2), bestMoveUci = "e7e5"),
                gameStatus = GameStatus.Ongoing(isCheck = false),
                hasClock = true,
                remainingPlayerTimeMs = 180_000L,
                openingEntry = null,
                plyCount = 1
            )
        )

        assertNotNull(detection)
        assertEquals(CoachCategory.PLAYER_ALLOWED_MATE, detection?.category)
        assertTrue(detection?.category?.isErrorCategory == true)
    }

    @Test
    fun `analyzer detects hanging knight blunder without needing eval`() {
        val analyzer = CoachAnalyzer(Random(42))
        // Blancs jouent Cd5 directement sur une case attaquée par le pion c6 et non défendue
        val fen = "rnbqkbnr/pp1ppppp/2p5/8/8/2N5/PPPPPPPP/R1BQKBNR w KQkq - 0 1"
        val game = ChessGame(fen)
        val prePos = game.currentPosition
        val record = game.playUciMove("c3d5")!!
        val postPos = game.currentPosition

        val detection = analyzer.analyzeMove(
            CoachAnalysisInput(
                prePosition = prePos,
                postPosition = postPos,
                moveRecord = record,
                isPlayerMove = true,
                preEval = null,
                postEval = null,
                gameStatus = GameStatus.Ongoing(isCheck = false),
                hasClock = false,
                remainingPlayerTimeMs = null,
                openingEntry = null,
                plyCount = 10
            )
        )

        assertNotNull(detection)
        assertEquals(CoachCategory.PLAYER_BLUNDER_HANGING_PIECE, detection?.category)
        assertEquals("cavalier", detection?.pieceNameFrLower)
        assertEquals("d5", detection?.squareAlgebraic)
    }

    @Test
    fun `analyzer detects castling good`() {
        val analyzer = CoachAnalyzer(Random(42))
        val fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
        val game = ChessGame(fen)
        val prePos = game.currentPosition
        val record = game.playUciMove("e1g1")!!
        val postPos = game.currentPosition

        val detection = analyzer.analyzeMove(
            CoachAnalysisInput(
                prePosition = prePos,
                postPosition = postPos,
                moveRecord = record,
                isPlayerMove = true,
                preEval = AnalysisSnapshot(score = UciScore.Centipawns(30), bestMoveUci = "d2d3"),
                postEval = AnalysisSnapshot(score = UciScore.Centipawns(25), bestMoveUci = "d7d6"),
                gameStatus = GameStatus.Ongoing(isCheck = false),
                hasClock = false,
                remainingPlayerTimeMs = null,
                openingEntry = null,
                plyCount = 7
            )
        )

        assertEquals(CoachCategory.CASTLING_GOOD, detection?.category)
    }
}
