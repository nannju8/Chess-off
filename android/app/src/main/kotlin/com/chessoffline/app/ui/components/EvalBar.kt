package com.chessoffline.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessoffline.engine.uci.UciScore
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Barre d'évaluation horizontale compacte affichant l'avantage en pions ou l'annonce de mat.
 */
@Composable
fun EvalBar(
    score: UciScore?,
    depth: Int,
    modifier: Modifier = Modifier
) {
    val whiteFractionTarget = when (score) {
        null -> 0.5f
        is UciScore.Mate -> if (score.moves > 0) 0.98f else 0.02f
        is UciScore.Centipawns -> {
            val normalized = tanh(score.cp / 450.0).toFloat()
            (0.5f + 0.45f * normalized).coerceIn(0.05f, 0.95f)
        }
    }

    val animatedWhiteFraction by animateFloatAsState(
        targetValue = whiteFractionTarget,
        animationSpec = tween(durationMillis = 280),
        label = "evalBarFill"
    )

    val label = when (score) {
        null -> "0.0"
        is UciScore.Mate -> if (score.moves > 0) "M${score.moves}" else "-M${abs(score.moves)}"
        is UciScore.Centipawns -> {
            val pawns = score.cp / 100.0
            if (pawns > 0.05) "+%.1f".format(pawns) else "%.1f".format(pawns)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1F242D))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedWhiteFraction)
                    .background(Color(0xFFF1F5F9))
            )
        }

        Text(
            text = if (depth > 0) "$label (p.$depth)" else label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
