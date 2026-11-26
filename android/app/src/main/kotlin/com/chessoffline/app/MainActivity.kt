package com.chessoffline.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chessoffline.app.data.GameMode
import com.chessoffline.app.ui.components.ChessBoardView
import com.chessoffline.app.ui.components.CoachBubble
import com.chessoffline.app.ui.components.ControlDock
import com.chessoffline.app.ui.components.EvalBar
import com.chessoffline.app.ui.components.MoveHistoryBar
import com.chessoffline.app.ui.components.OpeningBadge
import com.chessoffline.app.ui.components.OpeningIdeaDialog
import com.chessoffline.app.ui.components.PlayerClockBanner
import com.chessoffline.app.ui.modals.FenPgnDialog
import com.chessoffline.app.ui.modals.NewGameSheet
import com.chessoffline.app.ui.modals.NoOexEngineDialog
import com.chessoffline.app.ui.modals.PromotionDialog
import com.chessoffline.app.ui.modals.SettingsDialog
import com.chessoffline.app.ui.state.ChessViewModel
import com.chessoffline.app.ui.theme.ChessOfflineTheme
import com.chessoffline.core.model.GameStatus
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.TimeControl
import com.chessoffline.engine.EngineConnectionState
import com.chessoffline.engine.oex.OexError

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessOfflineTheme {
                ChessMainScreen()
            }
        }
    }
}

@Composable
fun ChessMainScreen(viewModel: ChessViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewGameSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showFenPgnDialog by remember { mutableStateOf(false) }
    var showResignConfirm by remember { mutableStateOf(false) }
    var showOpeningIdeaDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.statusBannerMessage) {
        val msg = state.statusBannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val engineName = when (val eng = state.engineState) {
        is EngineConnectionState.Ready -> eng.uciName
        is EngineConnectionState.Discovering -> "Détection OEX..."
        is EngineConnectionState.Error -> "Aucun moteur OEX"
        EngineConnectionState.Idle -> "Stockfish OEX"
    }

    val topColor = if (state.isBoardFlipped) PieceColor.WHITE else PieceColor.BLACK
    val bottomColor = topColor.opposite()

    val whiteScoreSum = state.capturedByWhite.sumOf { it.type.materialValue }
    val blackScoreSum = state.capturedByBlack.sumOf { it.type.materialValue }

    fun nameForColor(color: PieceColor): String = when (state.gameMode) {
        GameMode.VS_AI -> if (color == state.humanColor) "Vous" else engineName
        GameMode.TWO_PLAYERS -> if (color == PieceColor.WHITE) "Blancs" else "Noirs"
        GameMode.ANALYSIS -> if (color == PieceColor.WHITE) "Blancs (Analyse)" else "Noirs (Analyse)"
    }

    fun subtitleForColor(color: PieceColor): String? = when (state.gameMode) {
        GameMode.VS_AI -> if (color != state.humanColor) {
            "Niv. ${state.difficulty.level} · ${state.difficulty.approxEloLabel}"
        } else null
        else -> null
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // En-tête minimaliste mobile-first
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Échecs Pro · Hors Ligne",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.gameMode.labelFr} · $engineName",
                        fontSize = 11.sp,
                        color = if (state.engineState is EngineConnectionState.Error && state.gameMode == GameMode.VS_AI) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            if (state.engineState is EngineConnectionState.Error) {
                                viewModel.showEngineErrorDialog()
                            } else {
                                showSettingsDialog = true
                            }
                        }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::toggleMute, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = if (state.settings.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = "Son"
                        )
                    }
                    IconButton(onClick = { showFenPgnDialog = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.Code, contentDescription = "FEN et PGN")
                    }
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Paramètres")
                    }
                }
            }

            // Badge discret d'ouverture ECO (cliquable pour lire l'idée stratégique)
            state.recognizedOpening?.let { opening ->
                OpeningBadge(
                    opening = opening,
                    onClick = { showOpeningIdeaDialog = true }
                )
            }

            // Bannière résultat si partie terminée
            if (state.gameStatus.isTerminal) {
                GameOverBanner(
                    status = state.gameStatus,
                    onNewGame = { showNewGameSheet = true }
                )
            }

            // Barre d'évaluation temps réel (Stockfish 19)
            if (state.settings.showEvalBar && state.engineState is EngineConnectionState.Ready) {
                EvalBar(
                    score = state.analysis.score,
                    depth = state.analysis.depth
                )
            }

            // Bandeau joueur haut
            PlayerClockBanner(
                playerName = nameForColor(topColor),
                subtitle = subtitleForColor(topColor),
                color = topColor,
                isActiveTurn = !state.gameStatus.isTerminal && state.position.sideToMove == topColor,
                hasClock = state.timeControl.hasClock && state.gameMode != GameMode.ANALYSIS,
                remainingMs = if (topColor == PieceColor.WHITE) state.whiteTimeMs else state.blackTimeMs,
                capturedPieces = if (topColor == PieceColor.WHITE) state.capturedByWhite else state.capturedByBlack,
                materialAdvantage = if (topColor == PieceColor.WHITE) {
                    (whiteScoreSum - blackScoreSum).coerceAtLeast(0)
                } else {
                    (blackScoreSum - whiteScoreSum).coerceAtLeast(0)
                },
                pieceStyle = state.settings.pieceStyle,
                isThinking = state.isAiThinking && state.position.sideToMove == topColor
            )

            // Échiquier principal centré (absorbe l'espace vertical restant sans écraser les barres)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ChessBoardView(
                    position = state.position,
                    isFlipped = state.isBoardFlipped,
                    selectedSquare = state.selectedSquare,
                    legalTargets = state.legalTargetsForSelected,
                    lastMove = state.lastMove,
                    kingInCheckSquare = state.kingInCheckSquare,
                    hintMove = state.hintMove,
                    boardTheme = state.settings.boardTheme,
                    pieceStyle = state.settings.pieceStyle,
                    showCoordinates = state.settings.showCoordinates,
                    interactionEnabled = !state.gameStatus.isTerminal && !state.isAiThinking,
                    onSquareTapped = viewModel::onSquareTapped,
                    onPieceDragged = viewModel::onPieceDragged,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bandeau joueur bas
            PlayerClockBanner(
                playerName = nameForColor(bottomColor),
                subtitle = subtitleForColor(bottomColor),
                color = bottomColor,
                isActiveTurn = !state.gameStatus.isTerminal && state.position.sideToMove == bottomColor,
                hasClock = state.timeControl.hasClock && state.gameMode != GameMode.ANALYSIS,
                remainingMs = if (bottomColor == PieceColor.WHITE) state.whiteTimeMs else state.blackTimeMs,
                capturedPieces = if (bottomColor == PieceColor.WHITE) state.capturedByWhite else state.capturedByBlack,
                materialAdvantage = if (bottomColor == PieceColor.WHITE) {
                    (whiteScoreSum - blackScoreSum).coerceAtLeast(0)
                } else {
                    (blackScoreSum - whiteScoreSum).coerceAtLeast(0)
                },
                pieceStyle = state.settings.pieceStyle,
                isThinking = state.isAiThinking && state.position.sideToMove == bottomColor
            )

            // Historique SAN cliquable
            MoveHistoryBar(
                history = state.history,
                viewingPly = state.viewingPly,
                onJumpToPly = viewModel::jumpToPly
            )

            // Bulle compacte du Coach hors ligne
            AnimatedVisibility(visible = state.settings.coachEnabled && state.coachMessage != null) {
                state.coachMessage?.let { msg ->
                    CoachBubble(
                        message = msg,
                        selectedTone = state.settings.coachTone,
                        onShowBestMove = viewModel::showCoachBestMove,
                        onDismiss = viewModel::dismissCoachMessage
                    )
                }
            }

            // Barre d'actions tactile
            ControlDock(
                canUndo = state.history.isNotEmpty(),
                canHint = !state.gameStatus.isTerminal && state.engineState is EngineConnectionState.Ready,
                isComputingHint = state.isComputingHint,
                canResign = !state.gameStatus.isTerminal && state.gameMode != GameMode.ANALYSIS,
                onUndo = viewModel::undoMove,
                onHint = viewModel::requestHint,
                onFlip = viewModel::flipBoard,
                onNewGame = { showNewGameSheet = true },
                onResign = { showResignConfirm = true }
            )
        }
    }

    // Modales
    state.pendingPromotion?.let { pending ->
        PromotionDialog(
            color = pending.color,
            pieceStyle = state.settings.pieceStyle,
            onSelect = viewModel::confirmPromotion,
            onDismiss = viewModel::cancelPromotion
        )
    }

    if (showOpeningIdeaDialog) {
        state.recognizedOpening?.let { opening ->
            OpeningIdeaDialog(
                opening = opening,
                onDismiss = { showOpeningIdeaDialog = false }
            )
        }
    }

    if (showNewGameSheet) {
        NewGameSheet(
            initialMode = state.gameMode,
            initialColor = state.settings.lastColorPreference,
            initialDifficulty = state.difficulty,
            initialTimeControl = state.timeControl,
            initialCoachEnabled = state.settings.coachEnabled,
            initialCoachTone = state.settings.coachTone,
            onStartGame = { mode, colorPref, diff, tc, coachEnabled, coachTone ->
                showNewGameSheet = false
                viewModel.startNewGame(mode, colorPref, diff, tc, coachEnabled, coachTone)
            },
            onDismiss = { showNewGameSheet = false }
        )
    }

    if (showFenPgnDialog) {
        FenPgnDialog(
            currentFen = state.currentFen,
            currentPgn = viewModel.exportCurrentPgn(),
            onImportFen = viewModel::importFen,
            onImportPgn = viewModel::importPgn,
            onDismiss = { showFenPgnDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = state.settings,
            engineState = state.engineState,
            onSelectBoardTheme = viewModel::setBoardTheme,
            onSelectPieceStyle = viewModel::setPieceStyle,
            onToggleMute = viewModel::toggleMute,
            onSetAutoFlip = viewModel::setAutoFlipTwoPlayers,
            onSetShowCoords = viewModel::setShowCoordinates,
            onSetShowEvalBar = viewModel::setShowEvalBar,
            onSetCoachEnabled = viewModel::setCoachEnabled,
            onSelectCoachTone = viewModel::setCoachTone,
            onSetUseOpeningBookForBot = viewModel::setUseOpeningBookForBot,
            onSelectOexEngine = viewModel::selectOexEngine,
            onRediscoverOex = { viewModel.discoverAndInitEngine() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showResignConfirm) {
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text("Abandonner la partie ?") },
            text = { Text("La partie sera immédiatement enregistrée comme perdue par abandon.") },
            confirmButton = {
                Button(onClick = {
                    showResignConfirm = false
                    viewModel.resignGame()
                }) {
                    Text("Abandonner")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) {
                    Text("Continuer")
                }
            }
        )
    }

    if (state.showNoEngineDialog) {
        val errorState = state.engineState as? EngineConnectionState.Error
        val err = errorState?.error ?: OexError.NoEngineInstalled
        NoOexEngineDialog(
            error = err,
            diagnostics = errorState?.diagnostics,
            onRetryDetection = { viewModel.discoverAndInitEngine() },
            onSwitchToTwoPlayers = {
                viewModel.dismissNoEngineDialog()
                viewModel.startNewGame(
                    mode = GameMode.TWO_PLAYERS,
                    colorPreference = state.settings.lastColorPreference,
                    difficulty = state.difficulty,
                    timeControl = TimeControl.BLITZ_5_0
                )
            },
            onDismiss = viewModel::dismissNoEngineDialog
        )
    }
}

@Composable
private fun GameOverBanner(
    status: GameStatus,
    onNewGame: () -> Unit
) {
    val label = when (status) {
        is GameStatus.Checkmate -> {
            val winner = if (status.winner == PieceColor.WHITE) "les Blancs" else "les Noirs"
            "Échec et mat · Victoire pour $winner"
        }
        GameStatus.Stalemate -> "Pat · Partie nulle"
        is GameStatus.Draw -> "Partie nulle · ${status.reason.labelFr}"
        is GameStatus.Resigned -> {
            val winner = if (status.winner == PieceColor.WHITE) "les Blancs" else "les Noirs"
            "Abandon · Victoire pour $winner"
        }
        is GameStatus.Timeout -> {
            val winner = if (status.winner == PieceColor.WHITE) "les Blancs" else "les Noirs"
            "Temps écoulé · Victoire pour $winner"
        }
        is GameStatus.Ongoing -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onNewGame) {
            Text("Rejouer", fontWeight = FontWeight.Bold)
        }
    }
}
