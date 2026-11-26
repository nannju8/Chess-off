package com.chessoffline.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessoffline.app.data.BoardThemeId
import com.chessoffline.app.data.PieceStyleId
import com.chessoffline.app.ui.theme.BoardPalettes
import com.chessoffline.app.ui.theme.drawableRes
import com.chessoffline.core.model.ChessMove
import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.Square
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private data class DragState(
    val fromSquare: Square,
    val piece: Piece,
    val pointerPx: Offset
)

/**
 * Échiquier interactif tactile gérant simultanément le glisser-déposer (Drag & Drop)
 * et le clic-puis-clic, les coups légaux, le roi en échec et la flèche d'indice.
 */
@Composable
fun ChessBoardView(
    position: ChessPosition,
    isFlipped: Boolean,
    selectedSquare: Square?,
    legalTargets: Set<Square>,
    lastMove: ChessMove?,
    kingInCheckSquare: Square?,
    hintMove: Pair<Square, Square>?,
    boardTheme: BoardThemeId,
    pieceStyle: PieceStyleId,
    showCoordinates: Boolean,
    interactionEnabled: Boolean,
    onSquareTapped: (Square) -> Unit,
    onPieceDragged: (Square, Square) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = BoardPalettes.forTheme(boardTheme)
    var dragState by remember { mutableStateOf<DragState?>(null) }
    var hoverTargetSquare by remember { mutableStateOf<Square?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, palette.boardBorder, RoundedCornerShape(8.dp))
    ) {
        val boardSizePx = with(LocalDensity.current) { maxWidth.toPx() }
        val squareSizePx = boardSizePx / 8f
        val squareSizeDp = maxWidth / 8f

        fun offsetToSquare(offset: Offset): Square? {
            if (squareSizePx <= 0f) return null
            val col = (offset.x / squareSizePx).toInt()
            val row = (offset.y / squareSizePx).toInt()
            if (col !in 0..7 || row !in 0..7) return null
            val file = if (isFlipped) 7 - col else col
            val rank = if (isFlipped) row else 7 - row
            return Square(file, rank)
        }

        fun squareToCenter(sq: Square): Offset {
            val col = if (isFlipped) 7 - sq.file else sq.file
            val row = if (isFlipped) sq.rank else 7 - sq.rank
            return Offset((col + 0.5f) * squareSizePx, (row + 0.5f) * squareSizePx)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(position, isFlipped, interactionEnabled) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!interactionEnabled) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.all { !it.pressed }) break
                            }
                            return@awaitEachGesture
                        }

                        val startSq = offsetToSquare(down.position)
                        val startPiece = startSq?.let { position.pieceAt(it) }
                        var isDragging = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                if (isDragging && dragState != null) {
                                    val dropSq = offsetToSquare(change.position)
                                    val fromSq = dragState!!.fromSquare
                                    dragState = null
                                    hoverTargetSquare = null
                                    if (dropSq != null && dropSq != fromSq) {
                                        onPieceDragged(fromSq, dropSq)
                                    } else {
                                        onSquareTapped(fromSq)
                                    }
                                } else if (startSq != null) {
                                    onSquareTapped(startSq)
                                }
                                break
                            }

                            val dist = hypot(
                                change.position.x - down.position.x,
                                change.position.y - down.position.y
                            )
                            if (!isDragging && dist > touchSlop && startSq != null &&
                                startPiece != null && startPiece.color == position.sideToMove
                            ) {
                                isDragging = true
                                // Sélectionner automatiquement la case au début du glissement
                                onSquareTapped(startSq)
                            }

                            if (isDragging && startSq != null && startPiece != null) {
                                change.consume()
                                dragState = DragState(startSq, startPiece, change.position)
                                hoverTargetSquare = offsetToSquare(change.position)
                            }
                        }
                    }
                }
        ) {
            // 1. Fond de l'échiquier, surbrillances, roi en échec et coups légaux
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sqPx = size.width / 8f

                for (row in 0..7) {
                    for (col in 0..7) {
                        val file = if (isFlipped) 7 - col else col
                        val rank = if (isFlipped) row else 7 - row
                        val sq = Square(file, rank)
                        val topLeft = Offset(col * sqPx, row * sqPx)
                        val sqSize = Size(sqPx, sqPx)

                        val baseColor = if (sq.isLightSquare) palette.lightSquare else palette.darkSquare
                        drawRect(color = baseColor, topLeft = topLeft, size = sqSize)

                        val isLastMove = lastMove != null && (lastMove.from == sq || lastMove.to == sq)
                        if (isLastMove) {
                            val hl = if (sq.isLightSquare) palette.lastMoveLight else palette.lastMoveDark
                            drawRect(color = hl, topLeft = topLeft, size = sqSize)
                        }

                        if (selectedSquare == sq) {
                            drawRect(color = palette.selectedSquare, topLeft = topLeft, size = sqSize)
                        }

                        if (hoverTargetSquare == sq && sq in legalTargets) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.28f),
                                topLeft = topLeft,
                                size = sqSize
                            )
                        }

                        if (kingInCheckSquare == sq) {
                            val center = Offset(topLeft.x + sqPx / 2f, topLeft.y + sqPx / 2f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xEEEF4444), Color(0x88DC2626), Color.Transparent),
                                    center = center,
                                    radius = sqPx * 0.65f
                                ),
                                radius = sqPx * 0.65f,
                                center = center
                            )
                        }

                        if (sq in legalTargets) {
                            val center = Offset(topLeft.x + sqPx / 2f, topLeft.y + sqPx / 2f)
                            if (position.pieceAt(sq) != null) {
                                drawCircle(
                                    color = palette.legalDotColor,
                                    radius = sqPx * 0.44f,
                                    center = center,
                                    style = Stroke(width = sqPx * 0.085f)
                                )
                            } else {
                                drawCircle(
                                    color = palette.legalDotColor,
                                    radius = sqPx * 0.16f,
                                    center = center
                                )
                            }
                        }
                    }
                }
            }

            // 2. Coordonnées a-h et 1-8
            if (showCoordinates) {
                for (row in 0..7) {
                    for (col in 0..7) {
                        val file = if (isFlipped) 7 - col else col
                        val rank = if (isFlipped) row else 7 - row
                        val sq = Square(file, rank)
                        val coordColor = if (sq.isLightSquare) palette.coordOnLight else palette.coordOnDark

                        if (col == 0) {
                            Text(
                                text = "${'1' + rank}",
                                color = coordColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .offset(x = squareSizeDp * col + 2.dp, y = squareSizeDp * row + 1.dp)
                            )
                        }
                        if (row == 7) {
                            Text(
                                text = "${'a' + file}",
                                color = coordColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .offset(
                                        x = squareSizeDp * (col + 1) - 10.dp,
                                        y = squareSizeDp * (row + 1) - 14.dp
                                    )
                            )
                        }
                    }
                }
            }

            // 3. Pièces fixes sur les cases (masquer la pièce en cours de glissement)
            for (row in 0..7) {
                for (col in 0..7) {
                    val file = if (isFlipped) 7 - col else col
                    val rank = if (isFlipped) row else 7 - row
                    val sq = Square(file, rank)
                    val piece = position.pieceAt(sq) ?: continue
                    if (dragState?.fromSquare == sq) continue

                    Image(
                        painter = painterResource(id = piece.drawableRes(pieceStyle)),
                        contentDescription = "${piece.type.name} ${piece.color.name} ${sq.toAlgebraic()}",
                        modifier = Modifier
                            .offset(x = squareSizeDp * col, y = squareSizeDp * row)
                            .size(squareSizeDp)
                            .padding(2.dp)
                    )
                }
            }

            // 4. Flèche d'indice ("Meilleur coup")
            if (hintMove != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val start = squareToCenter(hintMove.first)
                    val end = squareToCenter(hintMove.second)
                    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                    val headLen = size.width / 8f * 0.36f
                    val shaftEnd = Offset(
                        x = end.x - (cos(angle) * headLen * 0.72f).toFloat(),
                        y = end.y - (sin(angle) * headLen * 0.72f).toFloat()
                    )

                    drawLine(
                        color = palette.hintArrowColor,
                        start = start,
                        end = shaftEnd,
                        strokeWidth = size.width / 8f * 0.16f,
                        cap = StrokeCap.Round
                    )

                    val p1 = Offset(
                        x = end.x - (headLen * cos(angle - Math.PI / 6)).toFloat(),
                        y = end.y - (headLen * sin(angle - Math.PI / 6)).toFloat()
                    )
                    val p2 = Offset(
                        x = end.x - (headLen * cos(angle + Math.PI / 6)).toFloat(),
                        y = end.y - (headLen * sin(angle + Math.PI / 6)).toFloat()
                    )
                    val arrowHead = Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        close()
                    }
                    drawPath(arrowHead, color = palette.hintArrowColor)
                }
            }

            // 5. Pièce en cours de glissement (agrandie et centrée sous le doigt)
            dragState?.let { activeDrag ->
                val dragSize = squareSizeDp * 1.25f
                val dragHalfPx = squareSizePx * 1.25f / 2f
                Image(
                    painter = painterResource(id = activeDrag.piece.drawableRes(pieceStyle)),
                    contentDescription = null,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (activeDrag.pointerPx.x - dragHalfPx).roundToInt(),
                                y = (activeDrag.pointerPx.y - dragHalfPx * 1.2f).roundToInt()
                            )
                        }
                        .size(dragSize)
                )
            }
        }
    }
}
