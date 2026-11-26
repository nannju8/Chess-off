package com.chessoffline.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.chessoffline.app.R
import com.chessoffline.core.model.MoveRecord

/**
 * Lecteur audio 100 % local basé sur SoundPool pour une latence quasi nulle.
 */
class LocalSoundPlayer(context: Context) {

    private val soundPool: SoundPool
    private val moveId: Int
    private val captureId: Int
    private val castleId: Int
    private val checkId: Int
    private val gameEndId: Int

    @Volatile
    var isMuted: Boolean = false

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        moveId = soundPool.load(context, R.raw.snd_move, 1)
        captureId = soundPool.load(context, R.raw.snd_capture, 1)
        castleId = soundPool.load(context, R.raw.snd_castle, 1)
        checkId = soundPool.load(context, R.raw.snd_check, 1)
        gameEndId = soundPool.load(context, R.raw.snd_game_end, 1)
    }

    fun playForMove(record: MoveRecord, isGameOver: Boolean) {
        if (isMuted) return
        val id = when {
            isGameOver -> gameEndId
            record.isCheckAfter -> checkId
            record.move.isKingSideCastle || record.move.isQueenSideCastle -> castleId
            record.move.isCapture -> captureId
            else -> moveId
        }
        soundPool.play(id, 0.9f, 0.9f, 1, 0, 1.0f)
    }

    fun playGameEnd() {
        if (isMuted) return
        soundPool.play(gameEndId, 0.9f, 0.9f, 1, 0, 1.0f)
    }

    fun release() {
        soundPool.release()
    }
}
