package com.chessoffline.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessoffline.core.model.MoveRecord

/**
 * Barre d'historique des coups SAN cliquable avec navigation avant/arrière.
 */
@Composable
fun MoveHistoryBar(
    history: List<MoveRecord>,
    viewingPly: Int,
    onJumpToPly: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(history.size, viewingPly) {
        if (history.isNotEmpty() && viewingPly > 0) {
            listState.animateScrollToItem((viewingPly - 1).coerceIn(0, history.lastIndex))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onJumpToPly(0) },
            enabled = viewingPly > 0,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Rounded.FirstPage, contentDescription = "Début de partie")
        }
        IconButton(
            onClick = { onJumpToPly(viewingPly - 1) },
            enabled = viewingPly > 0,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Coup précédent")
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun coup joué",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(history) { idx, record ->
                    val ply = idx + 1
                    val isSelected = viewingPly == ply
                    val isWhiteMove = idx % 2 == 0
                    val moveNum = (idx / 2) + 1
                    val prefix = if (isWhiteMove) "$moveNum. " else ""

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .clickable { onJumpToPly(ply) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "$prefix${record.san}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = { onJumpToPly(viewingPly + 1) },
            enabled = viewingPly < history.size,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Coup suivant")
        }
        IconButton(
            onClick = { onJumpToPly(history.size) },
            enabled = viewingPly < history.size,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Rounded.LastPage, contentDescription = "Position actuelle")
        }
    }
}
