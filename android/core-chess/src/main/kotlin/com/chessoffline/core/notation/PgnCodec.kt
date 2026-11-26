package com.chessoffline.core.notation

import com.chessoffline.core.model.ChessMove
import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.GameStatus
import com.chessoffline.core.model.MoveRecord
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.rules.MoveGenerator

/**
 * Données extraites lors de l'import d'un fichier ou texte PGN.
 */
data class ParsedPgn(
    val headers: Map<String, String>,
    val initialFen: String,
    val moves: List<ChessMove>
)

/**
 * Parseur et exporteur PGN (Portable Game Notation) compatible avec les standards FIDE.
 */
object PgnCodec {

    fun export(
        initialFen: String,
        history: List<MoveRecord>,
        status: GameStatus,
        whiteName: String = "Blancs",
        blackName: String = "Noirs",
        event: String = "Partie Locale",
        date: String = "????.??.??"
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""[Event "$event"]""")
        sb.appendLine("""[Site "Android Offline"]""")
        sb.appendLine("""[Date "$date"]""")
        sb.appendLine("""[Round "-"]""")
        sb.appendLine("""[White "$whiteName"]""")
        sb.appendLine("""[Black "$blackName"]""")
        sb.appendLine("""[Result "${status.pgnResult()}"]""")

        if (initialFen != FenCodec.STARTING_FEN) {
            sb.appendLine("""[SetUp "1"]""")
            sb.appendLine("""[FEN "$initialFen"]""")
        }
        sb.appendLine()

        val initialPos = FenCodec.parse(initialFen).getOrNull()
        val startsWithBlack = initialPos?.sideToMove?.fenChar == 'b'

        if (history.isEmpty()) {
            sb.append(status.pgnResult())
            return sb.toString()
        }

        val moveTokens = ArrayList<String>()
        history.forEachIndexed { idx, record ->
            val moveNumber = if (startsWithBlack) {
                ((idx + 1) / 2) + (initialPos?.fullmoveNumber ?: 1)
            } else {
                (idx / 2) + (initialPos?.fullmoveNumber ?: 1)
            }

            val isWhiteTurnAtRecord = if (startsWithBlack) idx % 2 == 1 else idx % 2 == 0
            if (isWhiteTurnAtRecord) {
                moveTokens.add("$moveNumber. ${record.san}")
            } else if (idx == 0) {
                moveTokens.add("$moveNumber... ${record.san}")
            } else {
                moveTokens.add(record.san)
            }
        }
        moveTokens.add(status.pgnResult())

        // Découpage propre à ~80 caractères par ligne
        var lineLength = 0
        for (token in moveTokens) {
            if (lineLength > 0 && lineLength + token.length + 1 > 80) {
                sb.appendLine()
                lineLength = 0
            } else if (lineLength > 0) {
                sb.append(' ')
                lineLength++
            }
            sb.append(token)
            lineLength += token.length
        }

        return sb.toString().trim()
    }

    fun parse(pgnText: String): Result<ParsedPgn> = runCatching {
        val headers = LinkedHashMap<String, String>()
        val bodyBuilder = StringBuilder()

        for (rawLine in pgnText.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("[") && line.endsWith("]")) {
                val match = Regex("""^\[(\w+)\s+"(.*)"\]$""").find(line)
                if (match != null) {
                    headers[match.groupValues[1]] = match.groupValues[2]
                }
            } else if (!line.startsWith("%")) {
                val commentFree = line.substringBefore(';')
                bodyBuilder.append(commentFree).append(' ')
            }
        }

        val initialFen = headers["FEN"] ?: FenCodec.STARTING_FEN
        var position = FenCodec.parse(initialFen).getOrThrow()

        val cleanedBody = stripCommentsAndVariations(bodyBuilder.toString())
        val rawTokens = cleanedBody.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        val moves = ArrayList<ChessMove>()
        for (token in rawTokens) {
            if (token in setOf("1-0", "0-1", "1/2-1/2", "*")) break
            if (token.matches(Regex("^\\d+\\.+$"))) continue
            if (token.startsWith("$")) continue

            // Retirer un éventuel préfixe de numéro collé (ex: "1.e4" ou "12...Nf6")
            val sanToken = token.replace(Regex("^\\d+\\.+"), "")
            if (sanToken.isEmpty()) continue

            val move = matchSanMove(position, sanToken)
                ?: throw IllegalArgumentException("Coup PGN illégal ou introuvable : '$sanToken'")
            moves.add(move)
            position = MoveGenerator.applyMoveUnchecked(position, move)
        }

        ParsedPgn(headers = headers, initialFen = initialFen, moves = moves)
    }

    private fun stripCommentsAndVariations(input: String): String {
        val sb = StringBuilder(input.length)
        var inBraceComment = false
        var parenDepth = 0

        for (ch in input) {
            when {
                inBraceComment -> if (ch == '}') inBraceComment = false
                ch == '{' -> inBraceComment = true
                ch == '(' -> parenDepth++
                ch == ')' -> if (parenDepth > 0) parenDepth--
                parenDepth == 0 -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun matchSanMove(position: ChessPosition, rawSan: String): ChessMove? {
        val normalized = rawSan
            .replace("0-0-0", "O-O-O")
            .replace("0-0", "O-O")
            .trimEnd('+', '#', '!', '?')

        val legalMoves = MoveGenerator.generateLegalMoves(position)

        // Comparaison directe sur les chaînes SAN calculées (sans suffixes +/#)
        for (move in legalMoves) {
            val candidateSan = FenCodec.toSan(position, move, legalMoves).trimEnd('+', '#')
            if (candidateSan == normalized) return move
        }

        // Fallback souple si un coup de promotion omet '=' (ex: "e8Q")
        val promoMatch = Regex("^([a-h](?:x[a-h])?[18])([QRBN])$").find(normalized)
        if (promoMatch != null) {
            val rebuilt = "${promoMatch.groupValues[1]}=${promoMatch.groupValues[2]}"
            for (move in legalMoves) {
                val candidateSan = FenCodec.toSan(position, move, legalMoves).trimEnd('+', '#')
                if (candidateSan == rebuilt) return move
            }
        }

        // Fallback UCI éventuel ("e2e4")
        if (normalized.length in 4..5) {
            val promo = if (normalized.length == 5) PieceType.fromFenChar(normalized[4]) else null
            val uciMatch = legalMoves.find {
                it.from.toAlgebraic() == normalized.substring(0, 2) &&
                    it.to.toAlgebraic() == normalized.substring(2, 4) &&
                    it.promotion == promo
            }
            if (uciMatch != null) return uciMatch
        }

        return null
    }
}
