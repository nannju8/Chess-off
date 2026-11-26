package com.chessoffline.engine.oex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OexAbiAndParserTest {

    @Test
    fun `normalizes common stockfish abi aliases`() {
        assertEquals("arm64-v8a", OexDiscovery.normalizeAbiToken("aarch64"))
        assertEquals("arm64-v8a", OexDiscovery.normalizeAbiToken("arm64"))
        assertEquals("arm64-v8a", OexDiscovery.normalizeAbiToken("arm-v8a"))
        assertEquals("armeabi-v7a", OexDiscovery.normalizeAbiToken("arm"))
        assertEquals("armeabi-v7a", OexDiscovery.normalizeAbiToken("armv7"))
        assertEquals("x86_64", OexDiscovery.normalizeAbiToken("x64"))
    }

    @Test
    fun `matches pipe separated multi-abi targets and keeps ambiguous when binary exists`() {
        val entries = listOf(
            OexDiscovery.RawOexEntry(
                name = "Stockfish 19",
                fileName = "libstockfish.so",
                targetAbi = "arm64-v8a|armeabi-v7a"
            ),
            OexDiscovery.RawOexEntry(
                name = "Stockfish Alias",
                fileName = "libstockfish_aarch64.so",
                targetAbi = "aarch64"
            )
        )

        val selected = OexDiscovery.filterBestAbiEntries(
            entries = entries,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            hasNativeBinary = true
        )
        assertEquals(2, selected.size)

        val ambiguousEntries = listOf(
            OexDiscovery.RawOexEntry(
                name = "Stockfish 19",
                fileName = "stockfish",
                targetAbi = "android-universal"
            )
        )
        val fallbackSelected = OexDiscovery.filterBestAbiEntries(
            entries = ambiguousEntries,
            supportedAbis = listOf("arm64-v8a"),
            hasNativeBinary = true
        )
        assertTrue(fallbackSelected.isNotEmpty())
        assertEquals("stockfish", fallbackSelected.first().fileName)
    }
}
