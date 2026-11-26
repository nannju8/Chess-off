package com.chessoffline.app.ui.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessoffline.engine.oex.OexError

/**
 * Boîte de dialogue claire affichée si aucun moteur OEX compatible (ex. Stockfish 19)
 * n'est détecté ou si son exécution est bloquée.
 */
@Composable
fun NoOexEngineDialog(
    error: OexError,
    onRetryDetection: () -> Unit,
    onSwitchToTwoPlayers: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = error.titleFr,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = error.messageFr,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Astuce : vérifiez que l'application Stockfish 19 (fournisseur Open Exchange) est bien installée sur ce téléphone, puis relancez la détection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onRetryDetection) {
                Text("Réessayer")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onSwitchToTwoPlayers) {
                Text("Mode 2 Joueurs")
            }
        }
    )
}
