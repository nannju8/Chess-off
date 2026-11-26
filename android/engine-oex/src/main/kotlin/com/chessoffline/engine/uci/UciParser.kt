package com.chessoffline.engine.uci

/**
 * Score d'évaluation UCI normalisé du point de vue des Blancs.
 */
sealed class UciScore {
    /** Avantage en centipions du point de vue des Blancs (+100 = +1.00 pion). */
    data class Centipawns(val cp: Int) : UciScore()

    /** Mat forcé en N coups (positif = les Blancs matent, négatif = les Noirs matent). */
    data class Mate(val moves: Int) : UciScore()
}

/**
 * Ligne d'analyse émise par le moteur UCI (`info ... pv ...`).
 */
data class UciInfoLine(
    val depth: Int,
    val multiPv: Int,
    val score: UciScore?,
    val pvMoves: List<String>
)

/**
 * Résultat final `bestmove` renvoyé par le moteur UCI.
 */
data class UciBestMove(
    val bestMove: String,
    val ponderMove: String?
)

/**
 * Parseur sans état des messages stdout du protocole UCI.
 */
object UciParser {

    fun parseIdName(line: String): String? {
        if (!line.startsWith("id name ")) return null
        return line.removePrefix("id name ").trim().takeIf { it.isNotEmpty() }
    }

    fun parseOptionName(line: String): String? {
        if (!line.startsWith("option name ")) return null
        val after = line.removePrefix("option name ")
        return after.substringBefore(" type ").trim().takeIf { it.isNotEmpty() }
    }

    fun parseBestMove(line: String): UciBestMove? {
        if (!line.startsWith("bestmove ")) return null
        val tokens = line.trim().split(Regex("\\s+"))
        val move = tokens.getOrNull(1) ?: return null
        if (move == "(none)" || move == "0000") return null
        val ponderIdx = tokens.indexOf("ponder")
        val ponder = if (ponderIdx >= 0) tokens.getOrNull(ponderIdx + 1) else null
        return UciBestMove(bestMove = move, ponderMove = ponder)
    }

    /**
     * Parse une ligne `info` UCI. [sideToMoveIsWhite] sert à normaliser le score du point de vue des Blancs
     * (en UCI, `score cp` / `score mate` est toujours relatif au joueur au trait).
     */
    fun parseInfo(line: String, sideToMoveIsWhite: Boolean): UciInfoLine? {
        if (!line.startsWith("info ")) return null
        val tokens = line.trim().split(Regex("\\s+"))

        var depth = 0
        var multiPv = 1
        var score: UciScore? = null
        var pvMoves: List<String> = emptyList()

        var i = 1
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> {
                    depth = tokens.getOrNull(i + 1)?.toIntOrNull() ?: depth
                    i += 2
                }
                "multipv" -> {
                    multiPv = tokens.getOrNull(i + 1)?.toIntOrNull() ?: multiPv
                    i += 2
                }
                "score" -> {
                    val kind = tokens.getOrNull(i + 1)
                    val value = tokens.getOrNull(i + 2)?.toIntOrNull()
                    if (kind == "cp" && value != null) {
                        val normalized = if (sideToMoveIsWhite) value else -value
                        score = UciScore.Centipawns(normalized)
                        i += 3
                    } else if (kind == "mate" && value != null) {
                        val normalized = if (sideToMoveIsWhite) value else -value
                        score = UciScore.Mate(normalized)
                        i += 3
                    } else {
                        i += 2
                    }
                }
                "pv" -> {
                    pvMoves = tokens.subList(i + 1, tokens.size)
                    break
                }
                else -> i++
            }
        }

        if (pvMoves.isEmpty() && score == null) return null
        return UciInfoLine(depth = depth, multiPv = multiPv, score = score, pvMoves = pvMoves)
    }
}
