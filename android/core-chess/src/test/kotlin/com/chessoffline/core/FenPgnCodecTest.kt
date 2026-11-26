package com.chessoffline.core

import com.chessoffline.core.notation.FenCodec
import com.chessoffline.core.rules.ChessGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FenPgnCodecTest {

    @Test
    fun `aller retour FEN strict`() {
        val fen = "r1bqkbnr/pp1ppppp/2n5/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"
        val parsed = FenCodec.parse(fen)
        assertTrue(parsed.isSuccess)
        assertEquals(fen, FenCodec.serialize(parsed.getOrThrow()))
    }

    @Test
    fun `import et export PGN avec commentaires et variantes`() {
        val samplePgn = """
            [Event "Test Hors Ligne"]
            [White "Joueur 1"]
            [Black "Stockfish 19"]
            [Result "*"]

            1. e4 {Ouverture favorite} e5 (1... c5 2. Nf3) 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O *
        """.trimIndent()

        val game = ChessGame()
        val res = game.loadPgn(samplePgn)
        assertTrue(res.isSuccess)
        assertEquals(9, game.plyCount)
        assertEquals("O-O", game.history.last().san)

        val exported = game.exportPgn("Joueur 1", "Stockfish 19")
        assertTrue(exported.contains("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O *"))
    }
}
