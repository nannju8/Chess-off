package com.chessoffline.app.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessoffline.app.coach.CoachTone
import com.chessoffline.app.data.BoardThemeId
import com.chessoffline.app.data.GameSettings
import com.chessoffline.app.data.PieceStyleId
import com.chessoffline.app.ui.theme.BoardPalettes
import com.chessoffline.engine.EngineConnectionState
import com.chessoffline.engine.oex.OexEngineInfo

/**
 * Paramètres de personnalisation, Coach hors ligne et diagnostic du moteur OEX détecté (ex. Stockfish 19).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    settings: GameSettings,
    engineState: EngineConnectionState,
    onSelectBoardTheme: (BoardThemeId) -> Unit,
    onSelectPieceStyle: (PieceStyleId) -> Unit,
    onToggleMute: () -> Unit,
    onSetAutoFlip: (Boolean) -> Unit,
    onSetShowCoords: (Boolean) -> Unit,
    onSetShowEvalBar: (Boolean) -> Unit,
    onSetCoachEnabled: (Boolean) -> Unit,
    onSelectCoachTone: (CoachTone) -> Unit,
    onSetUseOpeningBookForBot: (Boolean) -> Unit,
    onSelectOexEngine: (OexEngineInfo) -> Unit,
    onRediscoverOex: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres & Moteur OEX", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section moteur OEX détecté
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Moteur UCI / OEX détecté",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    when (engineState) {
                        is EngineConnectionState.Ready -> {
                            Text(
                                text = "Actif : ${engineState.uciName}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Source : ${engineState.engineInfo.packageName} (${engineState.engineInfo.source.name})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (engineState.availableEngines.size > 1) {
                                Text(
                                    text = "Autres moteurs détectés :",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                engineState.availableEngines.forEach { eng ->
                                    FilterChip(
                                        selected = eng.id == engineState.engineInfo.id,
                                        onClick = { onSelectOexEngine(eng) },
                                        label = { Text(eng.displayName, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                        is EngineConnectionState.Discovering -> {
                            Text("Recherche des moteurs OEX...", fontSize = 13.sp)
                        }
                        is EngineConnectionState.Error -> {
                            Text(
                                text = engineState.error.titleFr,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        EngineConnectionState.Idle -> {
                            Text("En attente d'initialisation", fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onRediscoverOex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualiser la détection OEX")
                    }
                }

                // Section Coach & Ouvertures
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Coach & Livre d'ouvertures",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    SettingToggleRow(
                        title = "Commentaires du Coach en direct",
                        checked = settings.coachEnabled,
                        onCheckedChange = onSetCoachEnabled
                    )
                    if (settings.coachEnabled) {
                        Text(
                            text = "Personnalité du Coach",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CoachTone.entries.forEach { tone ->
                                FilterChip(
                                    selected = settings.coachTone == tone,
                                    onClick = { onSelectCoachTone(tone) },
                                    label = { Text(tone.labelFr, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                    SettingToggleRow(
                        title = "Ouvertures variées pour le bot",
                        checked = settings.useOpeningBookForBot,
                        onCheckedChange = onSetUseOpeningBookForBot
                    )
                }

                // Thèmes d'échiquier
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Thème de l'échiquier", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BoardThemeId.entries.forEach { themeId ->
                            val palette = BoardPalettes.forTheme(themeId)
                            val selected = settings.boardTheme == themeId
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectBoardTheme(themeId) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row {
                                    Box(Modifier.size(14.dp).background(palette.lightSquare))
                                    Box(Modifier.size(14.dp).background(palette.darkSquare))
                                }
                                Text(
                                    text = themeId.labelFr,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Style de pièces
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Jeu de pièces", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PieceStyleId.entries.forEach { style ->
                            FilterChip(
                                selected = settings.pieceStyle == style,
                                onClick = { onSelectPieceStyle(style) },
                                label = { Text(style.labelFr) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Options booléennes
                SettingToggleRow(
                    title = "Sons locaux activés",
                    checked = !settings.isMuted,
                    onCheckedChange = { onToggleMute() }
                )
                SettingToggleRow(
                    title = "Retourner l'échiquier (2 joueurs)",
                    checked = settings.autoFlipTwoPlayers,
                    onCheckedChange = onSetAutoFlip
                )
                SettingToggleRow(
                    title = "Afficher la barre d'évaluation",
                    checked = settings.showEvalBar,
                    onCheckedChange = onSetShowEvalBar
                )
                SettingToggleRow(
                    title = "Afficher les coordonnées (a-h, 1-8)",
                    checked = settings.showCoordinates,
                    onCheckedChange = onSetShowCoords
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Terminer")
            }
        }
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
