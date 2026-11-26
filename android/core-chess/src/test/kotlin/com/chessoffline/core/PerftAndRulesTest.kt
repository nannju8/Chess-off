package com.chessoffline.core

import com.chessoffline.core.model.DrawReason
import com.chessoffline.core.model.GameStatus
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.model.Square
import com.chessoffline.core.notation.FenCodec
import com.chessoffline.core.rules.ChessGame
import com.chessoffline.core.rules.MoveGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerftAndRulesTest {

    private fun perft(fen: String, depth: Int): Long {
        val pos = FenCodec.parse(fen).getOrThrow()
        return perftRec(pos, depth)
    }

    private fun perftRec(pos: com.chessoffline.core.model.ChessPosition, depth: Int): Long {
        if (depth == 0) return 1L
        val moves = MoveGenerator.generateLegalMoves(pos)
        if (depth == 1) return moves.size.toLong()
        var nodes = 0L
        for (m in moves) {
            nodes += perftRec(MoveGenerator.applyMoveUnchecked(pos, m), depth - 1)
        }
        return nodes
    }

    @Test
    fun `perft position initiale profondeurs 1 a 3`() {
        assertEquals(20L, perft(FenCodec.STARTING_FEN, 1))
        assertEquals(400L, perft(FenCodec.STARTING_FEN, 2))
        assertEquals(8902L, perft(FenCodec.STARTING_FEN, 3))
    }

    @Test
    fun `perft kiwipete roques et promotions`() {
        val kiwipete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(48L, perft(kiwipete, 1))
        assertEquals(2039L, perft(kiwipete, 2))
    }

    @Test
    fun `prise en passant interdite si clouage horizontal sur le roi`() {
        // Roi blanc en a5, pion blanc en d5, pion noir en e5 (vient d'avancer de e7->e5), tour noire en h5
        val fen = "8/8/8/K2pP2r/8/8/8/4k3 w - d6 0 1"
        val game = ChessGame(fen)
        // e5xd6 en passant découvrirait le roi blanc sur la 5e rangée -> illégal !
        assertNull(game.playMove(Square.fromAlgebraic("e5")!!, Square.fromAlgebraic("d6")!!))
    }

    @Test
    fun `roque interdit sous echec ou case traversee attaquee`() {
        // Fou noir en a6 attaque f1 : petit roque blanc interdit, grand roque autorisé
        val fen = "4k3/8/b7/8/8/8/8/R3K2R w KQ - 0 1"
        val game = ChessGame(fen)
        assertNull(game.playMove(Square.fromAlgebraic("e1")!!, Square.fromAlgebraic("g1")!!))
        assertNotNull(game.playMove(Square.fromAlgebraic("e1")!!, Square.fromAlgebraic("c1")!!))
    }

    @Test
    fun `mat du berger et statut echec et mat`() {
        val game = ChessGame()
        assertNotNull(game.playUciMove("e2e4"))
        assertNotNull(game.playUciMove("e7e5"))
        assertNotNull(game.playUciMove("d1h5"))
        assertNotNull(game.playUciMove("b8c6"))
        assertNotNull(game.playUciMove("f1c4"))
        assertNotNull(game.playUciMove("g8f6"))
        val mate = game.playUciMove("h5f7")
        assertNotNull(mate)
        assertTrue(mate!!.isCheckmateAfter)
        assertEquals(GameStatus.Checkmate(PieceColor.WHITE), game.status())
    }

    @Test
    fun `pat detecte correctement`() {
        val stalemateFen = "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"
        val game = ChessGame(stalemateFen)
        assertEquals(GameStatus.Stalemate, game.status())
    }

    @Test
    fun `triple repetition detectee`() {
        val game = ChessGame()
        val cycle = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        // 1ère répétition initiale + 2 aller-retours = 3 occurrences de la position de départ
        repeat(2) {
            for (uci in cycle) assertNotNull(game.playUciMove(uci))
        }
        assertEquals(GameStatus.Draw(DrawReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun `regle des 50 coups et materiel insuffisant`() {
        val fiftyFen = "4k3/8/8/8/8/8/8/4K2R w - - 100 80"
        assertEquals(GameStatus.Draw(DrawReason.FIFTY_MOVE_RULE), ChessGame(fiftyFen).status())

        val insufficientSameColorBishops = "4k1b1/8/8/8/8/8/8/2B1K3 w - - 0 1"
        assertEquals(
            GameStatus.Draw(DrawReason.INSUFFICIENT_MATERIAL),
            ChessGame(insufficientSameColorBishops).status()
        )
    }

    @Test
    fun `promotion exige choix et transforme le pion`() {
        val fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        val game = ChessGame(fen)
        val a7 = Square.fromAlgebraic("a7")!!
        val a8 = Square.fromAlgebraic("a8")!!
        assertTrue(game.needsPromotionChoice(a7, a8))
        assertNull(game.playMove(a7, a8, null))
        val rec = game.playMove(a7, a8, PieceType.KNIGHT)
        assertNotNull(rec)
        assertEquals("a8=N", rec!!.san)
        assertFalse(game.status().isTerminal)
    }
}
