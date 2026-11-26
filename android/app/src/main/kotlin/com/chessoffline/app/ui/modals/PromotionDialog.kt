package com.chessoffline.app.ui.modals

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessoffline.app.data.PieceStyleId
import com.chessoffline.app.ui.theme.drawableRes
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.PieceType

/**
 * Fenêtre modale pour choisir la pièce de promotion (Dame, Tour, Fou ou Cavalier).
 */
@Composable
fun PromotionDialog(
    color: PieceColor,
    pieceStyle: PieceStyleId,
    onSelect: (PieceType) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        PieceType.QUEEN to "Dame",
        PieceType.ROOK to "Tour",
        PieceType.BISHOP to "Fou",
        PieceType.KNIGHT to "Cavalier"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promotion du pion", fontWeight = FontWeight.Bold) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                options.forEach { (type, label) ->
                    val piece = Piece(type, color)
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelect(type) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(id = piece.drawableRes(pieceStyle)),
                            contentDescription = label,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
