package com.chessoffline.app.openings

import com.chessoffline.core.notation.FenCodec
import com.chessoffline.core.notation.PgnCodec
import com.chessoffline.core.rules.MoveGenerator
import kotlin.random.Random

/**
 * Données brutes issues du fichier JSON embarqué `assets/openings.json`.
 */
data class RawOpeningEntry(
    val eco: String,
    val nameFr: String,
    val pgn: String,
    val idea: String
)

/**
 * Entrée d'ouverture indexée avec sa séquence de coups UCI et sa clé FEN simplifiée.
 */
data class OpeningEntry(
    val eco: String,
    val nameFr: String,
    val pgn: String,
    val idea: String,
    val uciMoves: List<String>,
    val finalFenKey: String
)

/**
 * Index en mémoire du livre d'ouvertures permettant :
 * - Une recherche en O(1) par clé FEN simplifiée (4 premiers champs du FEN -> gère les transpositions).
 * - La suggestion de coups théoriques variés pour le bot sur les premiers coups.
 */
class OpeningBookIndex private constructor(
    private val byFenKey: Map<String, OpeningEntry>,
    private val continuationsByFenKey: Map<String, List<String>>
) {
    val size: Int get() = byFenKey.size

    fun findByFen(fen: String): OpeningEntry? = byFenKey[simplifyFenKey(fen)]

    /**
     * Parcourt l'historique FEN de la partie (du plus récent au plus ancien) pour conserver
     * le nom de la dernière ouverture reconnue même après être sorti de la théorie.
     */
    fun findLatestInTimeline(fens: List<String>): OpeningEntry? {
        for (i in fens.lastIndex downTo 0) {
            val match = byFenKey[simplifyFenKey(fens[i])]
            if (match != null) return match
        }
        return null
    }

    /**
     * Retourne un coup UCI théorique aléatoire depuis [fen] si l'on se trouve encore
     * dans la fenêtre d'ouverture ([plyCount] < [maxBookPlies]).
     */
    fun pickBookMove(
        fen: String,
        plyCount: Int,
        maxBookPlies: Int = 16,
        random: Random = Random.Default
    ): String? {
        if (plyCount >= maxBookPlies) return null
        val candidates = continuationsByFenKey[simplifyFenKey(fen)] ?: return null
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    companion object {
        val EMPTY = OpeningBookIndex(emptyMap(), emptyMap())

        /**
         * Extrait les 4 premiers champs d'un FEN (plateau, trait, droits de roque, case en passant)
         * pour obtenir une clé canonique indépendante des compteurs de demi-coups et numéros de coups.
         */
        fun simplifyFenKey(fen: String): String {
            val parts = fen.trim().split(Regex("\\s+"))
            return if (parts.size >= 4) {
                "${parts[0]} ${parts[1]} ${parts[2]} ${parts[3]}"
            } else {
                fen.trim()
            }
        }

        /**
         * Construit l'index complet en rejouant chaque PGN via [PgnCodec] et [MoveGenerator].
         * Les entrées invalides sont ignorées sans bloquer le chargement.
         */
        fun buildFromRaw(entries: List<RawOpeningEntry>): OpeningBookIndex {
            if (entries.isEmpty()) return EMPTY

            val byFen = HashMap<String, OpeningEntry>(entries.size * 2)
            val continuations = HashMap<String, LinkedHashSet<String>>(entries.size * 3)

            for (raw in entries) {
                val parsed = PgnCodec.parse(raw.pgn).getOrNull() ?: continue
                if (parsed.moves.isEmpty()) continue

                var pos = FenCodec.parse(parsed.initialFen).getOrNull() ?: continue
                val uciMoves = ArrayList<String>(parsed.moves.size)

                for (move in parsed.moves) {
                    val fenKeyBefore = simplifyFenKey(FenCodec.serialize(pos))
                    val uci = move.toUci()
                    uciMoves.add(uci)
                    continuations.getOrPut(fenKeyBefore) { LinkedHashSet() }.add(uci)
                    pos = MoveGenerator.applyMoveUnchecked(pos, move)
                }

                val finalKey = simplifyFenKey(FenCodec.serialize(pos))
                val indexed = OpeningEntry(
                    eco = raw.eco,
                    nameFr = raw.nameFr,
                    pgn = raw.pgn,
                    idea = raw.idea,
                    uciMoves = uciMoves,
                    finalFenKey = finalKey
                )

                // En cas de transposition exacte sur la même position finale, privilégier l'entrée la plus descriptive
                val existing = byFen[finalKey]
                if (existing == null || indexed.uciMoves.size >= existing.uciMoves.size) {
                    byFen[finalKey] = indexed
                }
            }

            val frozenContinuations = continuations.mapValues { (_, set) -> set.toList() }
            return OpeningBookIndex(
                byFenKey = byFen,
                continuationsByFenKey = frozenContinuations
            )
        }
    }
}
