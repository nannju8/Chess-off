package com.chessoffline.app.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessoffline.engine.oex.OexDiagnosticReport
import com.chessoffline.engine.oex.OexError

/**
 * Boîte de dialogue claire affichée si aucun moteur OEX compatible (ex. Stockfish 19)
 * n'est détecté ou si son exécution est bloquée, avec un mini diagnostic technique détaillé.
 */
@Composable
fun NoOexEngineDialog(
    error: OexError,
    diagnostics: OexDiagnosticReport? = null,
    onRetryDetection: () -> Unit,
    onSwitchToTwoPlayers: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = error.messageFr,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Astuce : vérifiez que l'application Stockfish 19 (fournisseur Open Exchange) est bien installée sur ce téléphone, puis relancez la détection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (diagnostics != null) {
                    Text(
                        text = "ABI : ${diagnostics.deviceAbis.firstOrNull() ?: "?"} · " +
                            "OEX : ${diagnostics.intentPackagesFound.size} · " +
                            "com.stockfish141 : ${if (diagnostics.stockfish141Visible) "visible" else "invisible"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(
                        onClick = { showDetails = !showDetails },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (showDetails) "Masquer les détails techniques" else "Détails techniques",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    AnimatedVisibility(visible = showDetails) {
                        Text(
                            text = diagnostics.formatReport(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    }
                }
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
