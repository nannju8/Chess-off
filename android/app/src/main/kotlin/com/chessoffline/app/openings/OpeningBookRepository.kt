package com.chessoffline.app.openings

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.random.Random

/**
 * Dépôt 100 % hors ligne chargeant `assets/openings.json` en tâche de fond (`Dispatchers.IO`)
 * et fournissant la reconnaissance d'ouverture et les coups du livre pour le bot.
 */
class OpeningBookRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val loadMutex = Mutex()
    @Volatile
    private var index: OpeningBookIndex = OpeningBookIndex.EMPTY

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    suspend fun ensureLoaded() {
        if (_isLoaded.value) return
        loadMutex.withLock {
            if (_isLoaded.value) return
            val built = withContext(ioDispatcher) {
                runCatching {
                    val rawJson = context.assets.open(ASSET_FILE_NAME)
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                    val rawEntries = parseOpeningsJson(rawJson)
                    OpeningBookIndex.buildFromRaw(rawEntries)
                }.getOrElse { OpeningBookIndex.EMPTY }
            }
            index = built
            _isLoaded.value = true
        }
    }

    fun findOpening(fen: String): OpeningEntry? = index.findByFen(fen)

    fun findLatestOpeningInTimeline(fens: List<String>): OpeningEntry? =
        index.findLatestInTimeline(fens)

    fun pickBookMove(
        fen: String,
        plyCount: Int,
        maxBookPlies: Int = 16,
        random: Random = Random.Default
    ): String? = index.pickBookMove(
        fen = fen,
        plyCount = plyCount,
        maxBookPlies = maxBookPlies,
        random = random
    )

    companion object {
        private const val ASSET_FILE_NAME = "openings.json"

        fun parseOpeningsJson(jsonText: String): List<RawOpeningEntry> {
            if (jsonText.isBlank()) return emptyList()
            val array = JSONArray(jsonText)
            val result = ArrayList<RawOpeningEntry>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val eco = obj.optString("eco", "").trim()
                val nameFr = obj.optString("nameFr", "").trim()
                val pgn = obj.optString("pgn", "").trim()
                val idea = obj.optString("idea", "").trim()
                if (nameFr.isNotEmpty() && pgn.isNotEmpty()) {
                    result.add(
                        RawOpeningEntry(
                            eco = eco,
                            nameFr = nameFr,
                            pgn = pgn,
                            idea = idea
                        )
                    )
                }
            }
            return result
        }
    }
}
