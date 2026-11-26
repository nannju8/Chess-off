package com.chessoffline.app.ui.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessoffline.app.coach.CoachTone
import com.chessoffline.app.data.ColorPreference
import com.chessoffline.app.data.GameMode
import com.chessoffline.core.model.TimeControl
import com.chessoffline.engine.uci.DifficultyLevel
import kotlin.math.roundToInt

/**
 * Feuille de configuration pour lancer une nouvelle partie (IA / 2 Joueurs / Analyse, camp, 10 niveaux, cadence, Coach).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewGameSheet(
    initialMode: GameMode,
    initialColor: ColorPreference,
    initialDifficulty: DifficultyLevel,
    initialTimeControl: TimeControl,
    initialCoachEnabled: Boolean,
    initialCoachTone: CoachTone,
    onStartGame: (GameMode, ColorPreference, DifficultyLevel, TimeControl, Boolean, CoachTone) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMode by remember { mutableStateOf(initialMode) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedLevelFloat by remember { mutableFloatStateOf(initialDifficulty.level.toFloat()) }
    var selectedTimeControl by remember { mutableStateOf(initialTimeControl) }
    var coachEnabled by remember { mutableStateOf(initialCoachEnabled) }
    var selectedCoachTone by remember { mutableStateOf(initialCoachTone) }

    val difficulty = DifficultyLevel.fromLevel(selectedLevelFloat.roundToInt().coerceIn(1, 10))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Nouvelle partie",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Mode de jeu
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mode de jeu", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.labelFr) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Camp & Difficulté si Contre Stockfish 19
            if (selectedMode == GameMode.VS_AI) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Votre camp", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ColorPreference.entries.forEach { pref ->
                            FilterChip(
                                selected = selectedColor == pref,
                                onClick = { selectedColor = pref },
                                label = { Text(pref.labelFr) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Niveau ${difficulty.level}/10 · ${difficulty.labelFr}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = difficulty.approxEloLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = selectedLevelFloat,
                        onValueChange = { selectedLevelFloat = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                    Text(
                        text = if (difficulty.level <= 4) {
                            "Profondeur limitée (depth ${difficulty.depthCap}) + MultiPV ${difficulty.multiPv} pour un jeu humain accessible."
                        } else {
                            "Paramètres UCI calibrés (Skill Level ${difficulty.skillLevel})."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Cadence de pendule
            if (selectedMode != GameMode.ANALYSIS) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pendule (cadence)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimeControl.entries.forEach { tc ->
                            FilterChip(
                                selected = selectedTimeControl == tc,
                                onClick = { selectedTimeControl = tc },
                                label = { Text("${tc.categoryFr} ${tc.labelFr}") }
                            )
                        }
                    }
                }
            }

            // Coach hors ligne
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Coach en direct", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = coachEnabled, onCheckedChange = { coachEnabled = it })
                }
                if (coachEnabled) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CoachTone.entries.forEach { tone ->
                            FilterChip(
                                selected = selectedCoachTone == tone,
                                onClick = { selectedCoachTone = tone },
                                label = { Text(tone.labelFr) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val effectiveTc = if (selectedMode == GameMode.ANALYSIS) {
                        TimeControl.UNLIMITED
                    } else {
                        selectedTimeControl
                    }
                    onStartGame(
                        selectedMode,
                        selectedColor,
                        difficulty,
                        effectiveTc,
                        coachEnabled,
                        selectedCoachTone
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 24.dp)
            ) {
                Text("Lancer la partie", fontWeight = FontWeight.Bold)
            }
        }
    }
}
