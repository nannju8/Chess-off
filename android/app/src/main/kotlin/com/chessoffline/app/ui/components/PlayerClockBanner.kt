package com.chessoffline.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessoffline.app.data.PieceStyleId
import com.chessoffline.app.ui.theme.drawableRes
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.PieceColor

/**
 * Bandeau d'un joueur affichant son nom, ses pièces capturées, son avance matérielle et sa pendule.
 */
@Composable
fun PlayerClockBanner(
    playerName: String,
    subtitle: String?,
    color: PieceColor,
    isActiveTurn: Boolean,
    hasClock: Boolean,
    remainingMs: Long,
    capturedPieces: List<Piece>,
    materialAdvantage: Int,
    pieceStyle: PieceStyleId,
    isThinking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isActiveTurn) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isActiveTurn) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (color == PieceColor.WHITE) Color(0xFFF8FAFC) else Color(0xFF181A1F))
                    .border(1.dp, Color(0xFF64748B), RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isThinking) "$playerName (réflexion...)" else playerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!subtitle.isNullOrBlank() || capturedPieces.isNotEmpty() || materialAdvantage > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!subtitle.isNullOrBlank() && capturedPieces.isEmpty()) {
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        capturedPieces.take(10).forEach { cap ->
                            Image(
                                painter = painterResource(id = cap.drawableRes(pieceStyle)),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (materialAdvantage > 0) {
                            Text(
                                text = "+$materialAdvantage",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        if (hasClock) {
            val isLowTime = remainingMs in 1L..20_000L
            val clockBg = when {
                isLowTime && isActiveTurn -> Color(0xFF991B1B)
                isActiveTurn -> MaterialTheme.colorScheme.primary
                else -> Color(0xFF14161A)
            }
            val clockFg = when {
                isLowTime && isActiveTurn -> Color.White
                isActiveTurn -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(clockBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatClockMillis(remainingMs),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = clockFg
                )
            }
        }
    }
}

private fun formatClockMillis(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (clamped < 20_000L && clamped > 0L) {
        val tenths = (clamped % 1000L) / 100L
        "%02d:%02d.%d".format(minutes, seconds, tenths)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
