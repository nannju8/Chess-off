package com.chessoffline.app.ui.modals

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Fenêtre d'import et export 100 % hors ligne des formats FEN et PGN.
 */
@Composable
fun FenPgnDialog(
    currentFen: String,
    currentPgn: String,
    onImportFen: (String) -> Result<Unit>,
    onImportPgn: (String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var fenText by remember(currentFen) { mutableStateOf(currentFen) }
    var pgnText by remember(currentPgn) { mutableStateOf(currentPgn) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        isError = false
        feedback = "$label copié dans le presse-papiers."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FEN & PGN (Hors ligne)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; feedback = null },
                        text = { Text("Position FEN") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; feedback = null },
                        text = { Text("Partie PGN") }
                    )
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = fenText,
                        onValueChange = { fenText = it; feedback = null },
                        label = { Text("Chaîne FEN") },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { copyToClipboard("FEN", fenText) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copier")
                        }
                        Button(
                            onClick = {
                                val res = onImportFen(fenText)
                                if (res.isSuccess) {
                                    onDismiss()
                                } else {
                                    isError = true
                                    feedback = res.exceptionOrNull()?.message ?: "FEN invalide"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Charger")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = pgnText,
                        onValueChange = { pgnText = it; feedback = null },
                        label = { Text("Contenu PGN") },
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp, max = 220.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { copyToClipboard("PGN", pgnText) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copier")
                        }
                        Button(
                            onClick = {
                                val res = onImportPgn(pgnText)
                                if (res.isSuccess) {
                                    onDismiss()
                                } else {
                                    isError = true
                                    feedback = res.exceptionOrNull()?.message ?: "PGN invalide"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Importer")
                        }
                    }
                }

                feedback?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}
