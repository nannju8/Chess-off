package com.chessoffline.app.data

import android.content.Context
import android.content.SharedPreferences
import com.chessoffline.app.coach.CoachTone
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.TimeControl
import com.chessoffline.core.notation.FenCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde automatique 100 % locale dans SharedPreferences (JSON natif Android).
 */
class LocalPersistence(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chess_offline_prefs", Context.MODE_PRIVATE)

    fun saveSettings(settings: GameSettings) {
        val json = JSONObject().apply {
            put("boardTheme", settings.boardTheme.id)
            put("pieceStyle", settings.pieceStyle.id)
            put("isMuted", settings.isMuted)
            put("autoFlipTwoPlayers", settings.autoFlipTwoPlayers)
            put("showCoordinates", settings.showCoordinates)
            put("showEvalBar", settings.showEvalBar)
            put("coachEnabled", settings.coachEnabled)
            put("coachTone", settings.coachTone.id)
            put("useOpeningBookForBot", settings.useOpeningBookForBot)
            put("preferredEngineId", settings.preferredEngineId ?: JSONObject.NULL)
            put("lastDifficultyLevel", settings.lastDifficultyLevel)
            put("lastTimeControl", settings.lastTimeControl.id)
            put("lastColorPreference", settings.lastColorPreference.id)
        }
        prefs.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }

    fun loadSettings(): GameSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return GameSettings()
        return runCatching {
            val json = JSONObject(raw)
            GameSettings(
                boardTheme = BoardThemeId.entries.find { it.id == json.optString("boardTheme") }
                    ?: BoardThemeId.TOURNAMENT,
                pieceStyle = PieceStyleId.entries.find { it.id == json.optString("pieceStyle") }
                    ?: PieceStyleId.STAUNTON,
                isMuted = json.optBoolean("isMuted", false),
                autoFlipTwoPlayers = json.optBoolean("autoFlipTwoPlayers", true),
                showCoordinates = json.optBoolean("showCoordinates", true),
                showEvalBar = json.optBoolean("showEvalBar", true),
                coachEnabled = json.optBoolean("coachEnabled", true),
                coachTone = CoachTone.fromId(json.optString("coachTone", CoachTone.PEDAGOGUE.id)),
                useOpeningBookForBot = json.optBoolean("useOpeningBookForBot", true),
                preferredEngineId = json.optString("preferredEngineId", "").takeIf {
                    it.isNotBlank() && it != "null"
                },
                lastDifficultyLevel = json.optInt("lastDifficultyLevel", 3).coerceIn(1, 10),
                lastTimeControl = TimeControl.fromId(json.optString("lastTimeControl")),
                lastColorPreference = ColorPreference.entries.find {
                    it.id == json.optString("lastColorPreference")
                } ?: ColorPreference.WHITE
            )
        }.getOrDefault(GameSettings())
    }

    fun saveActiveGame(snapshot: SavedGameSnapshot) {
        val movesArray = JSONArray()
        snapshot.uciMoves.forEach { movesArray.put(it) }

        val json = JSONObject().apply {
            put("initialFen", snapshot.initialFen)
            put("uciMoves", movesArray)
            put("gameMode", snapshot.gameMode.id)
            put("humanColor", snapshot.humanColor.name)
            put("difficultyLevel", snapshot.difficultyLevel)
            put("timeControl", snapshot.timeControl.id)
            put("whiteTimeMs", snapshot.whiteTimeMs)
            put("blackTimeMs", snapshot.blackTimeMs)
            put("isBoardFlipped", snapshot.isBoardFlipped)
        }
        prefs.edit().putString(KEY_ACTIVE_GAME, json.toString()).apply()
    }

    fun loadActiveGame(): SavedGameSnapshot? {
        val raw = prefs.getString(KEY_ACTIVE_GAME, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("uciMoves") ?: JSONArray()
            val moves = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                moves.add(arr.getString(i))
            }
            SavedGameSnapshot(
                initialFen = json.optString("initialFen", FenCodec.STARTING_FEN),
                uciMoves = moves,
                gameMode = GameMode.entries.find { it.id == json.optString("gameMode") }
                    ?: GameMode.VS_AI,
                humanColor = runCatching {
                    PieceColor.valueOf(json.optString("humanColor", "WHITE"))
                }.getOrDefault(PieceColor.WHITE),
                difficultyLevel = json.optInt("difficultyLevel", 3).coerceIn(1, 10),
                timeControl = TimeControl.fromId(json.optString("timeControl")),
                whiteTimeMs = json.optLong("whiteTimeMs", 300_000L),
                blackTimeMs = json.optLong("blackTimeMs", 300_000L),
                isBoardFlipped = json.optBoolean("isBoardFlipped", false)
            )
        }.getOrNull()
    }

    private companion object {
        const val KEY_SETTINGS = "settings_v1"
        const val KEY_ACTIVE_GAME = "active_game_v1"
    }
}
