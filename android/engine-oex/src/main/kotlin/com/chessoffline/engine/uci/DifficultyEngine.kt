package com.chessoffline.engine.uci

import kotlin.math.exp
import kotlin.random.Random

/**
 * Profils de difficulté progressifs (10 niveaux : ~400 Elo jusqu'au niveau maximum de Stockfish 19).
 *
 * Les niveaux 1 à 4 utilisent impérativement une profondeur très faible (`depth = 1..2`)
 * combinée à `MultiPV` pour sélectionner des coups réalistes de niveau débutant à intermédiaire,
 * car même au Skill Level minimum, Stockfish 19 reste beaucoup trop fort pour un débutant.
 */
enum class DifficultyLevel(
    val level: Int,
    val labelFr: String,
    val approxEloLabel: String,
    val depthCap: Int?,
    val moveTimeMs: Long?,
    val multiPv: Int,
    val skillLevel: Int,
    val limitStrengthElo: Int?,
    val humanNoiseTemperature: Double
) {
    LEVEL_1(1, "Novice", "~400 Elo", depthCap = 1, moveTimeMs = null, multiPv = 5, skillLevel = 0, limitStrengthElo = null, humanNoiseTemperature = 220.0),
    LEVEL_2(2, "Débutant", "~600 Elo", depthCap = 1, moveTimeMs = null, multiPv = 4, skillLevel = 0, limitStrengthElo = null, humanNoiseTemperature = 140.0),
    LEVEL_3(3, "Apprenti", "~800 Elo", depthCap = 2, moveTimeMs = null, multiPv = 3, skillLevel = 1, limitStrengthElo = null, humanNoiseTemperature = 90.0),
    LEVEL_4(4, "Initié", "~1000 Elo", depthCap = 2, moveTimeMs = null, multiPv = 3, skillLevel = 2, limitStrengthElo = null, humanNoiseTemperature = 55.0),
    LEVEL_5(5, "Club", "~1250 Elo", depthCap = 5, moveTimeMs = 180L, multiPv = 1, skillLevel = 4, limitStrengthElo = 1320, humanNoiseTemperature = 0.0),
    LEVEL_6(6, "Intermédiaire", "~1500 Elo", depthCap = 7, moveTimeMs = 280L, multiPv = 1, skillLevel = 7, limitStrengthElo = 1500, humanNoiseTemperature = 0.0),
    LEVEL_7(7, "Confirmé", "~1800 Elo", depthCap = 10, moveTimeMs = 450L, multiPv = 1, skillLevel = 11, limitStrengthElo = 1800, humanNoiseTemperature = 0.0),
    LEVEL_8(8, "Avancé", "~2100 Elo", depthCap = null, moveTimeMs = 800L, multiPv = 1, skillLevel = 15, limitStrengthElo = 2100, humanNoiseTemperature = 0.0),
    LEVEL_9(9, "Maître", "~2450 Elo", depthCap = null, moveTimeMs = 1400L, multiPv = 1, skillLevel = 18, limitStrengthElo = 2450, humanNoiseTemperature = 0.0),
    LEVEL_10(10, "Stockfish Max", "Max (3500+)", depthCap = null, moveTimeMs = 2200L, multiPv = 1, skillLevel = 20, limitStrengthElo = null, humanNoiseTemperature = 0.0);

    companion object {
        fun fromLevel(level: Int): DifficultyLevel = entries.find { it.level == level } ?: LEVEL_3
    }
}

/**
 * Sélectionne un coup à partir des lignes `MultiPV` pour les niveaux débutants (~400 à 1000 Elo).
 */
object MultiPvHumanizer {

    fun pickMoveFromMultiPv(
        candidates: Map<Int, UciInfoLine>,
        difficulty: DifficultyLevel,
        sideToMoveIsWhite: Boolean,
        fallbackBestMove: String,
        random: Random = Random.Default
    ): String {
        if (difficulty.multiPv <= 1 || candidates.isEmpty()) return fallbackBestMove

        val ordered = candidates.entries
            .sortedBy { it.key }
            .mapNotNull { (_, info) ->
                val move = info.pvMoves.firstOrNull() ?: return@mapNotNull null
                val evalCp = scoreToCentipawns(info.score, sideToMoveIsWhite)
                move to evalCp
            }

        if (ordered.isEmpty()) return fallbackBestMove
        if (ordered.size == 1) return ordered.first().first

        val bestEval = ordered.maxOf { it.second }
        val temp = difficulty.humanNoiseTemperature.coerceAtLeast(15.0)

        // Calcul de poids softmax sur l'écart en centipions par rapport au meilleur coup
        val weights = ordered.map { (_, cp) ->
            val delta = (bestEval - cp).coerceIn(0, 600).toDouble()
            exp(-delta / temp)
        }

        val totalWeight = weights.sum()
        if (totalWeight <= 0.0) return fallbackBestMove

        var roll = random.nextDouble(totalWeight)
        for (i in ordered.indices) {
            roll -= weights[i]
            if (roll <= 0.0) {
                return ordered[i].first
            }
        }
        return ordered.first().first
    }

    private fun scoreToCentipawns(score: UciScore?, sideToMoveIsWhite: Boolean): Int {
        if (score == null) return 0
        return when (score) {
            is UciScore.Centipawns -> if (sideToMoveIsWhite) score.cp else -score.cp
            is UciScore.Mate -> {
                val signed = if (sideToMoveIsWhite) score.moves else -score.moves
                if (signed > 0) 10_000 - signed * 10 else -10_000 - signed * 10
            }
        }
    }
}
