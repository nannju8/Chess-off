package com.chessoffline.app.coach

import android.content.Context
import com.chessoffline.core.model.PieceType
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
 * Charge `assets/coach_comments.json` hors ligne et sélectionne des commentaires adaptés
 * sans jamais répéter deux fois le même `id` au cours d'une même partie.
 */
class CoachCommentaryPicker(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val loadMutex = Mutex()
    @Volatile
    private var templatesByCategory: Map<CoachCategory, List<CoachCommentTemplate>> = emptyMap()

    private val usedCommentIds = HashSet<String>()
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    suspend fun ensureLoaded() {
        if (_isLoaded.value) return
        loadMutex.withLock {
            if (_isLoaded.value) return
            val loaded = withContext(ioDispatcher) {
                runCatching {
                    val raw = context.assets.open(ASSET_FILE_NAME)
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                    parseCommentsJson(raw).groupBy { it.category }
                }.getOrElse { emptyMap() }
            }
            templatesByCategory = loaded
            _isLoaded.value = true
        }
    }

    @Synchronized
    fun resetGameHistory() {
        usedCommentIds.clear()
    }

    @Synchronized
    fun pickAndFormat(
        detection: CoachDetection,
        preferredTone: CoachTone,
        random: Random = Random.Default
    ): CoachMessage? {
        val candidates = templatesByCategory[detection.category] ?: return null
        if (candidates.isEmpty()) return null

        val unusedInCategory = candidates.filter { it.id !in usedCommentIds }
        if (unusedInCategory.isEmpty()) {
            // Aucun commentaire inédit restant pour cette catégorie dans cette partie
            return null
        }

        val toneKey = preferredTone.jsonKey
        val toneFiltered = if (toneKey == null) {
            unusedInCategory
        } else {
            unusedInCategory.filter { it.tone.equals(toneKey, ignoreCase = true) }
                .ifEmpty { unusedInCategory }
        }

        val chosen = toneFiltered[random.nextInt(toneFiltered.size)]
        usedCommentIds.add(chosen.id)

        val formatted = formatTemplate(chosen.text, detection)
        val actionableBestMove = if (detection.category.isErrorCategory) {
            detection.bestMoveUci
        } else {
            null
        }

        return CoachMessage(
            commentId = chosen.id,
            category = detection.category,
            tone = chosen.tone,
            text = formatted,
            bestMoveUci = actionableBestMove,
            bestMoveSan = if (actionableBestMove != null) detection.bestMoveSan else null,
            isErrorHighlight = detection.category.isErrorCategory && actionableBestMove != null
        )
    }

    companion object {
        private const val ASSET_FILE_NAME = "coach_comments.json"

        fun parseCommentsJson(jsonText: String): List<CoachCommentTemplate> {
            if (jsonText.isBlank()) return emptyList()
            val array = JSONArray(jsonText)
            val result = ArrayList<CoachCommentTemplate>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val categoryRaw = obj.optString("category", "").trim()
                val tone = obj.optString("tone", "pedagogue").trim().lowercase()
                val text = obj.optString("text", "").trim()
                val category = CoachCategory.fromNameOrNull(categoryRaw) ?: continue
                if (id.isNotEmpty() && text.isNotEmpty()) {
                    result.add(
                        CoachCommentTemplate(
                            id = id,
                            category = category,
                            tone = tone,
                            text = text
                        )
                    )
                }
            }
            return result
        }

        /**
         * Nom de pièce en français, strictement en minuscules et sans article.
         */
        fun pieceNameFrLower(type: PieceType): String = when (type) {
            PieceType.PAWN -> "pion"
            PieceType.KNIGHT -> "cavalier"
            PieceType.BISHOP -> "fou"
            PieceType.ROOK -> "tour"
            PieceType.QUEEN -> "dame"
            PieceType.KING -> "roi"
        }

        /**
         * Remplace `{playedMove}`, `{bestMove}`, `{square}`, `{opening}` et `{piece}`.
         * Met automatiquement une majuscule si le placeholder est placé en tout début de phrase.
         */
        fun formatTemplate(template: String, detection: CoachDetection): String {
            val replacements = mapOf(
                "{playedMove}" to detection.playedMoveSan,
                "{bestMove}" to (detection.bestMoveSan ?: detection.playedMoveSan),
                "{square}" to (detection.squareAlgebraic ?: ""),
                "{opening}" to (detection.openingNameFr ?: "cette ouverture"),
                "{piece}" to (detection.pieceNameFrLower ?: "pièce")
            )

            var out = template
            for ((token, rawValue) in replacements) {
                var searchFrom = 0
                while (true) {
                    val idx = out.indexOf(token, startIndex = searchFrom)
                    if (idx < 0) break
                    val value = if (isAtSentenceStart(out, idx)) {
                        rawValue.replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                        }
                    } else {
                        rawValue
                    }
                    out = out.substring(0, idx) + value + out.substring(idx + token.length)
                    searchFrom = idx + value.length
                }
            }
            return out.replace(Regex(" {2,}"), " ").trim()
        }

        private fun isAtSentenceStart(text: String, index: Int): Boolean {
            var i = index - 1
            while (i >= 0 && text[i].isWhitespace()) {
                i--
            }
            if (i < 0) return true
            return text[i] == '.' || text[i] == '!' || text[i] == '?'
        }
    }
}
