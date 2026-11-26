package com.chessoffline.app.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessoffline.app.audio.LocalSoundPlayer
import com.chessoffline.app.coach.CoachAnalysisInput
import com.chessoffline.app.coach.CoachAnalyzer
import com.chessoffline.app.coach.CoachCommentaryPicker
import com.chessoffline.app.coach.CoachMessage
import com.chessoffline.app.coach.CoachTone
import com.chessoffline.app.data.BoardThemeId
import com.chessoffline.app.data.ColorPreference
import com.chessoffline.app.data.GameMode
import com.chessoffline.app.data.GameSettings
import com.chessoffline.app.data.LocalPersistence
import com.chessoffline.app.data.PieceStyleId
import com.chessoffline.app.data.SavedGameSnapshot
import com.chessoffline.app.openings.OpeningBookIndex
import com.chessoffline.app.openings.OpeningBookRepository
import com.chessoffline.app.openings.OpeningEntry
import com.chessoffline.core.model.ChessMove
import com.chessoffline.core.model.ChessPosition
import com.chessoffline.core.model.GameStatus
import com.chessoffline.core.model.MoveRecord
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.PieceType
import com.chessoffline.core.model.Square
import com.chessoffline.core.model.TimeControl
import com.chessoffline.core.notation.FenCodec
import com.chessoffline.core.rules.ChessGame
import com.chessoffline.core.rules.MoveGenerator
import com.chessoffline.engine.AnalysisSnapshot
import com.chessoffline.engine.EngineConnectionState
import com.chessoffline.engine.StockfishCoordinator
import com.chessoffline.engine.oex.OexEngineInfo
import com.chessoffline.engine.oex.OexError
import com.chessoffline.engine.uci.DifficultyLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

data class PendingPromotion(
    val from: Square,
    val to: Square,
    val color: PieceColor
)

data class ChessUiState(
    val position: ChessPosition,
    val gameStatus: GameStatus,
    val gameMode: GameMode = GameMode.VS_AI,
    val humanColor: PieceColor = PieceColor.WHITE,
    val difficulty: DifficultyLevel = DifficultyLevel.LEVEL_3,
    val timeControl: TimeControl = TimeControl.BLITZ_5_0,
    val whiteTimeMs: Long = TimeControl.BLITZ_5_0.initialMillis,
    val blackTimeMs: Long = TimeControl.BLITZ_5_0.initialMillis,
    val isBoardFlipped: Boolean = false,
    val selectedSquare: Square? = null,
    val legalTargetsForSelected: Set<Square> = emptySet(),
    val lastMove: ChessMove? = null,
    val kingInCheckSquare: Square? = null,
    val history: List<MoveRecord> = emptyList(),
    val viewingPly: Int = 0,
    val capturedByWhite: List<Piece> = emptyList(),
    val capturedByBlack: List<Piece> = emptyList(),
    val pendingPromotion: PendingPromotion? = null,
    val hintMove: Pair<Square, Square>? = null,
    val isAiThinking: Boolean = false,
    val isComputingHint: Boolean = false,
    val settings: GameSettings = GameSettings(),
    val engineState: EngineConnectionState = EngineConnectionState.Idle,
    val analysis: AnalysisSnapshot = AnalysisSnapshot(),
    val recognizedOpening: OpeningEntry? = null,
    val coachMessage: CoachMessage? = null,
    val showNoEngineDialog: Boolean = false,
    val statusBannerMessage: String? = null
) {
    val isViewingLatest: Boolean get() = viewingPly == history.size
    val currentFen: String get() = FenCodec.serialize(position)
}

class ChessViewModel(application: Application) : AndroidViewModel(application) {

    private val persistence = LocalPersistence(application)
    private val soundPlayer = LocalSoundPlayer(application)
    private val coordinator = StockfishCoordinator(application)
    private val openingBook = OpeningBookRepository(application)
    private val coachPicker = CoachCommentaryPicker(application)
    private val coachAnalyzer = CoachAnalyzer()

    private val game = ChessGame()
    private val _uiState: MutableStateFlow<ChessUiState>

    val uiState: StateFlow<ChessUiState>

    private var clockJob: Job? = null
    private var aiMoveJob: Job? = null
    private var coachJob: Job? = null

    @Volatile
    private var activeEvalFenKey: String? = null

    private val evalCache: MutableMap<String, AnalysisSnapshot> = Collections.synchronizedMap(
        object : LinkedHashMap<String, AnalysisSnapshot>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnalysisSnapshot>?): Boolean {
                return size > 120
            }
        }
    )

    init {
        val loadedSettings = persistence.loadSettings()
        soundPlayer.isMuted = loadedSettings.isMuted

        val savedGame = persistence.loadActiveGame()
        if (savedGame != null) {
            restoreSavedGame(savedGame)
        }

        val initialPos = game.currentPosition
        val initialStatus = game.status()
        val initialMode = savedGame?.gameMode ?: GameMode.VS_AI
        val initialHumanColor = savedGame?.humanColor ?: PieceColor.WHITE
        val initialDifficulty = DifficultyLevel.fromLevel(
            savedGame?.difficultyLevel ?: loadedSettings.lastDifficultyLevel
        )
        val initialTc = savedGame?.timeControl ?: loadedSettings.lastTimeControl
        val initialFlipped = savedGame?.isBoardFlipped ?: (initialHumanColor == PieceColor.BLACK)

        _uiState = MutableStateFlow(
            ChessUiState(
                position = initialPos,
                gameStatus = initialStatus,
                gameMode = initialMode,
                humanColor = initialHumanColor,
                difficulty = initialDifficulty,
                timeControl = initialTc,
                whiteTimeMs = savedGame?.whiteTimeMs ?: initialTc.initialMillis,
                blackTimeMs = savedGame?.blackTimeMs ?: initialTc.initialMillis,
                isBoardFlipped = initialFlipped,
                lastMove = game.history.lastOrNull()?.move,
                kingInCheckSquare = kingCheckSquare(initialPos),
                history = game.history,
                viewingPly = game.plyCount,
                capturedByWhite = game.capturedPiecesUpTo().first,
                capturedByBlack = game.capturedPiecesUpTo().second,
                settings = loadedSettings
            )
        )
        uiState = _uiState.asStateFlow()

        observeCoordinator()
        preloadOfflinePacks()
        discoverAndInitEngine(loadedSettings.preferredEngineId)
        startClockLoopIfNeeded()
    }

    private fun preloadOfflinePacks() {
        viewModelScope.launch {
            openingBook.ensureLoaded()
            refreshRecognizedOpening(_uiState.value.viewingPly)
        }
        viewModelScope.launch {
            coachPicker.ensureLoaded()
        }
    }

    private fun restoreSavedGame(snapshot: SavedGameSnapshot) {
        if (game.resetFromFen(snapshot.initialFen).isSuccess) {
            for (uci in snapshot.uciMoves) {
                if (game.playUciMove(uci) == null) break
            }
        }
    }

    private fun observeCoordinator() {
        viewModelScope.launch {
            coordinator.connectionState.collect { connState ->
                _uiState.update { state ->
                    state.copy(
                        engineState = connState,
                        showNoEngineDialog = connState is EngineConnectionState.Error &&
                            state.gameMode == GameMode.VS_AI
                    )
                }
            }
        }
        viewModelScope.launch {
            coordinator.analysis.collect { snap ->
                val trackedKey = activeEvalFenKey
                if (trackedKey != null && snap.score != null) {
                    evalCache[trackedKey] = snap
                }
                _uiState.update { it.copy(analysis = snap) }
            }
        }
    }

    fun discoverAndInitEngine(preferredId: String? = _uiState.value.settings.preferredEngineId) {
        viewModelScope.launch {
            val res = coordinator.initializeDefaultEngine(preferredId)
            if (res.isSuccess) {
                maybeTriggerAiOrAnalysis()
            }
        }
    }

    fun selectOexEngine(engineInfo: OexEngineInfo) {
        updateSettings { it.copy(preferredEngineId = engineInfo.id) }
        viewModelScope.launch {
            val res = coordinator.connectToEngine(engineInfo)
            if (res.isSuccess) {
                maybeTriggerAiOrAnalysis()
            }
        }
    }

    fun dismissNoEngineDialog() {
        _uiState.update { it.copy(showNoEngineDialog = false) }
    }

    fun showEngineErrorDialog() {
        _uiState.update { it.copy(showNoEngineDialog = true) }
    }

    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        if (!state.isViewingLatest) {
            jumpToPly(state.history.size)
            return
        }
        if (state.gameStatus.isTerminal || state.isAiThinking) return
        if (state.gameMode == GameMode.VS_AI && state.position.sideToMove != state.humanColor) return

        val currentSelection = state.selectedSquare
        if (currentSelection != null && square in state.legalTargetsForSelected) {
            tryCommitMove(currentSelection, square)
            return
        }

        val tappedPiece = state.position.pieceAt(square)
        if (tappedPiece != null && tappedPiece.color == state.position.sideToMove) {
            val targets = game.legalMovesFrom(square).map { it.to }.toSet()
            _uiState.update {
                it.copy(
                    selectedSquare = if (currentSelection == square) null else square,
                    legalTargetsForSelected = if (currentSelection == square) emptySet() else targets
                )
            }
        } else {
            _uiState.update { it.copy(selectedSquare = null, legalTargetsForSelected = emptySet()) }
        }
    }

    fun onPieceDragged(from: Square, to: Square) {
        val state = _uiState.value
        if (!state.isViewingLatest || state.gameStatus.isTerminal || state.isAiThinking) return
        if (state.gameMode == GameMode.VS_AI && state.position.sideToMove != state.humanColor) return
        if (from == to) return

        val piece = state.position.pieceAt(from) ?: return
        if (piece.color != state.position.sideToMove) return

        val legalTargets = game.legalMovesFrom(from).map { it.to }.toSet()
        if (to in legalTargets) {
            tryCommitMove(from, to)
        } else {
            _uiState.update { it.copy(selectedSquare = null, legalTargetsForSelected = emptySet()) }
        }
    }

    private fun tryCommitMove(from: Square, to: Square) {
        if (game.needsPromotionChoice(from, to)) {
            _uiState.update {
                it.copy(
                    pendingPromotion = PendingPromotion(
                        from = from,
                        to = to,
                        color = game.currentPosition.sideToMove
                    ),
                    selectedSquare = null,
                    legalTargetsForSelected = emptySet()
                )
            }
            return
        }
        executeMove(from, to, null)
    }

    fun confirmPromotion(pieceType: PieceType) {
        val pending = _uiState.value.pendingPromotion ?: return
        _uiState.update { it.copy(pendingPromotion = null) }
        executeMove(pending.from, pending.to, pieceType)
    }

    fun cancelPromotion() {
        _uiState.update { it.copy(pendingPromotion = null) }
    }

    private fun executeMove(from: Square, to: Square, promotion: PieceType?) {
        val prePosition = game.currentPosition
        val preFen = game.currentFen
        val moverColor = prePosition.sideToMove

        val record = game.playMove(from, to, promotion) ?: return
        val postPosition = game.currentPosition
        val postFen = game.currentFen

        val stateBefore = _uiState.value
        val tc = stateBefore.timeControl
        val updatedWhiteMs = if (tc.hasClock && moverColor == PieceColor.WHITE && game.plyCount > 1) {
            stateBefore.whiteTimeMs + tc.incrementMillis
        } else {
            stateBefore.whiteTimeMs
        }
        val updatedBlackMs = if (tc.hasClock && moverColor == PieceColor.BLACK && game.plyCount > 1) {
            stateBefore.blackTimeMs + tc.incrementMillis
        } else {
            stateBefore.blackTimeMs
        }

        val nextStatus = game.status()
        soundPlayer.playForMove(record, nextStatus.isTerminal)

        val autoFlip = stateBefore.gameMode == GameMode.TWO_PLAYERS &&
            stateBefore.settings.autoFlipTwoPlayers &&
            !nextStatus.isTerminal
        val nextFlipped = if (autoFlip) {
            postPosition.sideToMove == PieceColor.BLACK
        } else {
            stateBefore.isBoardFlipped
        }

        syncStateFromGame(
            whiteMs = updatedWhiteMs,
            blackMs = updatedBlackMs,
            flipped = nextFlipped,
            clearHint = true
        )
        saveGameNow()

        val isPlayerMove = stateBefore.gameMode != GameMode.VS_AI || moverColor == stateBefore.humanColor
        val remainingTimeMs = if (moverColor == PieceColor.WHITE) updatedWhiteMs else updatedBlackMs

        triggerCoachAnalysis(
            prePosition = prePosition,
            preFen = preFen,
            postPosition = postPosition,
            postFen = postFen,
            record = record,
            isPlayerMove = isPlayerMove,
            gameStatus = nextStatus,
            hasClock = tc.hasClock && stateBefore.gameMode != GameMode.ANALYSIS,
            remainingPlayerTimeMs = remainingTimeMs,
            plyCount = game.plyCount
        )

        if (!nextStatus.isTerminal) {
            maybeTriggerAiOrAnalysis()
        }
    }

    private fun maybeTriggerAiOrAnalysis() {
        val state = _uiState.value
        if (state.gameStatus.isTerminal || !state.isViewingLatest) return

        when (state.gameMode) {
            GameMode.VS_AI -> {
                if (state.position.sideToMove != state.humanColor) {
                    launchAiTurn()
                } else if ((state.settings.showEvalBar || state.settings.coachEnabled) &&
                    state.engineState is EngineConnectionState.Ready
                ) {
                    launchTrackedBackgroundEval(
                        fen = state.currentFen,
                        sideToMoveIsWhite = state.position.sideToMove == PieceColor.WHITE
                    )
                }
            }
            GameMode.ANALYSIS -> {
                if (state.engineState is EngineConnectionState.Ready) {
                    launchTrackedBackgroundEval(
                        fen = state.currentFen,
                        sideToMoveIsWhite = state.position.sideToMove == PieceColor.WHITE
                    )
                }
            }
            GameMode.TWO_PLAYERS -> {
                if ((state.settings.showEvalBar || state.settings.coachEnabled) &&
                    state.engineState is EngineConnectionState.Ready
                ) {
                    launchTrackedBackgroundEval(
                        fen = state.currentFen,
                        sideToMoveIsWhite = state.position.sideToMove == PieceColor.WHITE,
                        depthLimit = 12
                    )
                }
            }
        }
    }

    private fun launchTrackedBackgroundEval(
        fen: String,
        sideToMoveIsWhite: Boolean,
        depthLimit: Int = 14
    ) {
        activeEvalFenKey = OpeningBookIndex.simplifyFenKey(fen)
        coordinator.startBackgroundEval(
            fen = fen,
            sideToMoveIsWhite = sideToMoveIsWhite,
            depthLimit = depthLimit
        )
    }

    private fun launchAiTurn() {
        aiMoveJob?.cancel()
        val state = _uiState.value
        val fenAtRequest = game.currentFen
        val prePosition = game.currentPosition
        val aiIsWhite = prePosition.sideToMove == PieceColor.WHITE
        val difficulty = state.difficulty
        val currentPly = game.plyCount

        // Vérifier d'abord si le livre d'ouvertures propose un coup varié (6 à 8 premiers coups par camp)
        val bookMoveUci = if (state.settings.useOpeningBookForBot) {
            openingBook.pickBookMove(
                fen = fenAtRequest,
                plyCount = currentPly,
                maxBookPlies = 16
            )?.takeIf { candidate ->
                game.legalMoves().any { it.toUci() == candidate }
            }
        } else {
            null
        }

        if (bookMoveUci == null && state.engineState !is EngineConnectionState.Ready) {
            _uiState.update { it.copy(showNoEngineDialog = true) }
            return
        }

        _uiState.update { it.copy(isAiThinking = true) }

        aiMoveJob = viewModelScope.launch {
            val result: Result<String> = if (bookMoveUci != null) {
                delay(320L)
                Result.success(bookMoveUci)
            } else {
                coordinator.computeAiMove(
                    fen = fenAtRequest,
                    sideToMoveIsWhite = aiIsWhite,
                    difficulty = difficulty
                )
            }

            _uiState.update { it.copy(isAiThinking = false) }

            if (result.isSuccess) {
                val uci = result.getOrThrow()
                if (game.currentFen == fenAtRequest && !game.status().isTerminal) {
                    val moverColor = game.currentPosition.sideToMove
                    val record = game.playUciMove(uci)
                    if (record != null) {
                        val postPosition = game.currentPosition
                        val postFen = game.currentFen
                        val current = _uiState.value
                        val tc = current.timeControl
                        val nextWhiteMs = if (tc.hasClock && moverColor == PieceColor.WHITE && game.plyCount > 1) {
                            current.whiteTimeMs + tc.incrementMillis
                        } else {
                            current.whiteTimeMs
                        }
                        val nextBlackMs = if (tc.hasClock && moverColor == PieceColor.BLACK && game.plyCount > 1) {
                            current.blackTimeMs + tc.incrementMillis
                        } else {
                            current.blackTimeMs
                        }
                        val nextStatus = game.status()
                        soundPlayer.playForMove(record, nextStatus.isTerminal)
                        syncStateFromGame(
                            whiteMs = nextWhiteMs,
                            blackMs = nextBlackMs,
                            flipped = current.isBoardFlipped,
                            clearHint = true
                        )
                        saveGameNow()

                        if (bookMoveUci == null || nextStatus.isTerminal) {
                            triggerCoachAnalysis(
                                prePosition = prePosition,
                                preFen = fenAtRequest,
                                postPosition = postPosition,
                                postFen = postFen,
                                record = record,
                                isPlayerMove = false,
                                gameStatus = nextStatus,
                                hasClock = tc.hasClock,
                                remainingPlayerTimeMs = if (moverColor == PieceColor.WHITE) nextWhiteMs else nextBlackMs,
                                plyCount = game.plyCount
                            )
                        }

                        if (!nextStatus.isTerminal &&
                            (current.settings.showEvalBar || current.settings.coachEnabled) &&
                            current.engineState is EngineConnectionState.Ready
                        ) {
                            launchTrackedBackgroundEval(
                                fen = postFen,
                                sideToMoveIsWhite = postPosition.sideToMove == PieceColor.WHITE
                            )
                        }
                    }
                }
            } else {
                val err = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        statusBannerMessage = (err as? OexError)?.messageFr
                            ?: err?.message
                            ?: "Erreur moteur UCI",
                        showNoEngineDialog = err is OexError.NoEngineInstalled || err is OexError.ExecutionDenied
                    )
                }
            }
        }
    }

    private fun triggerCoachAnalysis(
        prePosition: ChessPosition,
        preFen: String,
        postPosition: ChessPosition,
        postFen: String,
        record: MoveRecord,
        isPlayerMove: Boolean,
        gameStatus: GameStatus,
        hasClock: Boolean,
        remainingPlayerTimeMs: Long?,
        plyCount: Int
    ) {
        val settings = _uiState.value.settings
        if (!settings.coachEnabled) return

        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            coachPicker.ensureLoaded()
            val preKey = OpeningBookIndex.simplifyFenKey(preFen)
            val postKey = OpeningBookIndex.simplifyFenKey(postFen)
            val engineReady = _uiState.value.engineState is EngineConnectionState.Ready

            var preEval = evalCache[preKey]
            var postEval = evalCache[postKey]

            if (!gameStatus.isTerminal && engineReady && isPlayerMove) {
                if (preEval == null) {
                    preEval = coordinator.evaluateQuick(
                        fen = preFen,
                        sideToMoveIsWhite = prePosition.sideToMove == PieceColor.WHITE,
                        moveTimeMs = 160L,
                        updateUiAnalysis = false
                    ).getOrNull()?.also { evalCache[preKey] = it }
                }
                if (postEval == null) {
                    postEval = coordinator.evaluateQuick(
                        fen = postFen,
                        sideToMoveIsWhite = postPosition.sideToMove == PieceColor.WHITE,
                        moveTimeMs = 180L,
                        updateUiAnalysis = _uiState.value.settings.showEvalBar
                    ).getOrNull()?.also { evalCache[postKey] = it }
                }
            }

            val opening = _uiState.value.recognizedOpening
            val detection = coachAnalyzer.analyzeMove(
                CoachAnalysisInput(
                    prePosition = prePosition,
                    postPosition = postPosition,
                    moveRecord = record,
                    isPlayerMove = isPlayerMove,
                    preEval = preEval,
                    postEval = postEval,
                    gameStatus = gameStatus,
                    hasClock = hasClock,
                    remainingPlayerTimeMs = remainingPlayerTimeMs,
                    openingEntry = opening,
                    plyCount = plyCount
                )
            ) ?: return@launch

            val message = coachPicker.pickAndFormat(
                detection = detection,
                preferredTone = _uiState.value.settings.coachTone
            ) ?: return@launch

            _uiState.update { it.copy(coachMessage = message) }
        }
    }

    fun showCoachBestMove() {
        val uci = _uiState.value.coachMessage?.bestMoveUci ?: return
        if (uci.length < 4) return
        val from = Square.fromAlgebraic(uci.substring(0, 2)) ?: return
        val to = Square.fromAlgebraic(uci.substring(2, 4)) ?: return
        _uiState.update { it.copy(hintMove = from to to) }
    }

    fun dismissCoachMessage() {
        _uiState.update { it.copy(coachMessage = null) }
    }

    fun requestHint() {
        val state = _uiState.value
        if (state.gameStatus.isTerminal || state.isComputingHint) return
        if (state.engineState !is EngineConnectionState.Ready) {
            _uiState.update { it.copy(showNoEngineDialog = true) }
            return
        }

        val targetPly = state.viewingPly
        val fen = game.fenAtPly(targetPly)
        val pos = game.positionAtPly(targetPly)

        _uiState.update { it.copy(isComputingHint = true) }
        viewModelScope.launch {
            val res = coordinator.computeBestHint(
                fen = fen,
                sideToMoveIsWhite = pos.sideToMove == PieceColor.WHITE
            )
            val snap = res.getOrNull()
            if (snap != null && snap.score != null) {
                evalCache[OpeningBookIndex.simplifyFenKey(fen)] = snap
            }
            val bestUci = snap?.bestMoveUci
            val arrow = if (bestUci != null && bestUci.length >= 4) {
                val from = Square.fromAlgebraic(bestUci.substring(0, 2))
                val to = Square.fromAlgebraic(bestUci.substring(2, 4))
                if (from != null && to != null) from to to else null
            } else null

            _uiState.update {
                it.copy(
                    isComputingHint = false,
                    hintMove = arrow
                )
            }
        }
    }

    fun undoMove() {
        val state = _uiState.value
        if (state.history.isEmpty()) return

        aiMoveJob?.cancel()
        coachJob?.cancel()
        coordinator.stopAnalysis()

        val steps = if (state.gameMode == GameMode.VS_AI &&
            state.position.sideToMove == state.humanColor &&
            state.history.size >= 2
        ) {
            2
        } else {
            1
        }

        if (game.undoLastMove(steps)) {
            _uiState.update { it.copy(isAiThinking = false, coachMessage = null) }
            syncStateFromGame(
                whiteMs = state.whiteTimeMs,
                blackMs = state.blackTimeMs,
                flipped = state.isBoardFlipped,
                clearHint = true
            )
            saveGameNow()
            maybeTriggerAiOrAnalysis()
        }
    }

    fun flipBoard() {
        _uiState.update { it.copy(isBoardFlipped = !it.isBoardFlipped) }
        saveGameNow()
    }

    fun resignGame() {
        val state = _uiState.value
        if (state.gameStatus.isTerminal) return
        aiMoveJob?.cancel()
        coachJob?.cancel()
        coordinator.stopAnalysis()

        val losing = if (state.gameMode == GameMode.VS_AI) {
            state.humanColor
        } else {
            game.currentPosition.sideToMove
        }
        game.resign(losing)
        soundPlayer.playGameEnd()
        syncStateFromGame(
            whiteMs = state.whiteTimeMs,
            blackMs = state.blackTimeMs,
            flipped = state.isBoardFlipped,
            clearHint = true
        )
        emitTerminalCoachSummary()
        saveGameNow()
    }

    private fun emitTerminalCoachSummary() {
        val state = _uiState.value
        if (!state.settings.coachEnabled) return
        val lastRec = game.history.lastOrNull() ?: return
        val prePos = game.positionAtPly((game.plyCount - 1).coerceAtLeast(0))
        triggerCoachAnalysis(
            prePosition = prePos,
            preFen = FenCodec.serialize(prePos),
            postPosition = game.currentPosition,
            postFen = game.currentFen,
            record = lastRec,
            isPlayerMove = true,
            gameStatus = game.status(),
            hasClock = state.timeControl.hasClock,
            remainingPlayerTimeMs = null,
            plyCount = game.plyCount
        )
    }

    fun jumpToPly(ply: Int) {
        val clamped = ply.coerceIn(0, game.plyCount)
        val pos = game.positionAtPly(clamped)
        val (capW, capB) = game.capturedPiecesUpTo(clamped)
        val lastMoveAtPly = if (clamped > 0) game.history[clamped - 1].move else null
        val openingAtPly = computeOpeningAtPly(clamped)

        _uiState.update {
            it.copy(
                position = pos,
                viewingPly = clamped,
                selectedSquare = null,
                legalTargetsForSelected = emptySet(),
                lastMove = lastMoveAtPly,
                kingInCheckSquare = kingCheckSquare(pos),
                capturedByWhite = capW,
                capturedByBlack = capB,
                hintMove = null,
                recognizedOpening = openingAtPly
            )
        }

        if (_uiState.value.gameMode == GameMode.ANALYSIS &&
            _uiState.value.engineState is EngineConnectionState.Ready
        ) {
            launchTrackedBackgroundEval(
                fen = FenCodec.serialize(pos),
                sideToMoveIsWhite = pos.sideToMove == PieceColor.WHITE
            )
        }
    }

    fun startNewGame(
        mode: GameMode,
        colorPreference: ColorPreference,
        difficulty: DifficultyLevel,
        timeControl: TimeControl,
        coachEnabled: Boolean = _uiState.value.settings.coachEnabled,
        coachTone: CoachTone = _uiState.value.settings.coachTone
    ) {
        aiMoveJob?.cancel()
        coachJob?.cancel()
        coordinator.stopAnalysis()
        coordinator.clearAnalysis()
        evalCache.clear()
        activeEvalFenKey = null
        coachPicker.resetGameHistory()
        coachAnalyzer.reset()

        val resolvedHumanColor = colorPreference.resolve()
        game.resetFromFen(FenCodec.STARTING_FEN)

        val nextSettings = _uiState.value.settings.copy(
            lastDifficultyLevel = difficulty.level,
            lastTimeControl = timeControl,
            lastColorPreference = colorPreference,
            coachEnabled = coachEnabled,
            coachTone = coachTone
        )
        persistence.saveSettings(nextSettings)

        val initialFlipped = when (mode) {
            GameMode.VS_AI -> resolvedHumanColor == PieceColor.BLACK
            GameMode.TWO_PLAYERS, GameMode.ANALYSIS -> false
        }

        _uiState.update {
            ChessUiState(
                position = game.currentPosition,
                gameStatus = game.status(),
                gameMode = mode,
                humanColor = resolvedHumanColor,
                difficulty = difficulty,
                timeControl = timeControl,
                whiteTimeMs = timeControl.initialMillis,
                blackTimeMs = timeControl.initialMillis,
                isBoardFlipped = initialFlipped,
                kingInCheckSquare = null,
                history = emptyList(),
                viewingPly = 0,
                settings = nextSettings,
                engineState = it.engineState,
                recognizedOpening = null,
                coachMessage = null,
                showNoEngineDialog = mode == GameMode.VS_AI &&
                    it.engineState is EngineConnectionState.Error
            )
        }
        saveGameNow()
        maybeTriggerAiOrAnalysis()
    }

    fun importFen(fen: String): Result<Unit> {
        aiMoveJob?.cancel()
        coachJob?.cancel()
        coordinator.stopAnalysis()
        coordinator.clearAnalysis()
        evalCache.clear()
        activeEvalFenKey = null
        coachPicker.resetGameHistory()
        coachAnalyzer.reset()

        val res = game.resetFromFen(fen)
        if (res.isSuccess) {
            val state = _uiState.value
            _uiState.update { it.copy(coachMessage = null) }
            syncStateFromGame(
                whiteMs = state.timeControl.initialMillis,
                blackMs = state.timeControl.initialMillis,
                flipped = state.isBoardFlipped,
                clearHint = true
            )
            saveGameNow()
            maybeTriggerAiOrAnalysis()
        }
        return res
    }

    fun importPgn(pgn: String): Result<Unit> {
        aiMoveJob?.cancel()
        coachJob?.cancel()
        coordinator.stopAnalysis()
        coordinator.clearAnalysis()
        evalCache.clear()
        activeEvalFenKey = null
        coachPicker.resetGameHistory()
        coachAnalyzer.reset()

        val res = game.loadPgn(pgn)
        if (res.isSuccess) {
            val state = _uiState.value
            _uiState.update { it.copy(coachMessage = null) }
            syncStateFromGame(
                whiteMs = state.timeControl.initialMillis,
                blackMs = state.timeControl.initialMillis,
                flipped = state.isBoardFlipped,
                clearHint = true
            )
            saveGameNow()
            maybeTriggerAiOrAnalysis()
        }
        return res
    }

    fun exportCurrentPgn(): String {
        val state = _uiState.value
        val engineLabel = (state.engineState as? EngineConnectionState.Ready)?.uciName ?: "Stockfish 19"
        val whiteName = when (state.gameMode) {
            GameMode.VS_AI -> if (state.humanColor == PieceColor.WHITE) "Vous" else engineLabel
            GameMode.TWO_PLAYERS -> "Joueur 1 (Blancs)"
            GameMode.ANALYSIS -> "Analyse"
        }
        val blackName = when (state.gameMode) {
            GameMode.VS_AI -> if (state.humanColor == PieceColor.BLACK) "Vous" else engineLabel
            GameMode.TWO_PLAYERS -> "Joueur 2 (Noirs)"
            GameMode.ANALYSIS -> "Analyse"
        }
        return game.exportPgn(whiteName = whiteName, blackName = blackName)
    }

    fun toggleMute() {
        val muted = !_uiState.value.settings.isMuted
        soundPlayer.isMuted = muted
        updateSettings { it.copy(isMuted = muted) }
    }

    fun setBoardTheme(themeId: BoardThemeId) = updateSettings { it.copy(boardTheme = themeId) }
    fun setPieceStyle(styleId: PieceStyleId) = updateSettings { it.copy(pieceStyle = styleId) }
    fun setAutoFlipTwoPlayers(enabled: Boolean) = updateSettings { it.copy(autoFlipTwoPlayers = enabled) }
    fun setShowCoordinates(enabled: Boolean) = updateSettings { it.copy(showCoordinates = enabled) }
    fun setShowEvalBar(enabled: Boolean) = updateSettings { it.copy(showEvalBar = enabled) }

    fun setCoachEnabled(enabled: Boolean) {
        updateSettings { it.copy(coachEnabled = enabled) }
        if (!enabled) {
            coachJob?.cancel()
            _uiState.update { it.copy(coachMessage = null) }
        }
    }

    fun setCoachTone(tone: CoachTone) = updateSettings { it.copy(coachTone = tone) }
    fun setUseOpeningBookForBot(enabled: Boolean) = updateSettings { it.copy(useOpeningBookForBot = enabled) }
    fun clearStatusBanner() = _uiState.update { it.copy(statusBannerMessage = null) }

    private fun updateSettings(transform: (GameSettings) -> GameSettings) {
        val updated = transform(_uiState.value.settings)
        persistence.saveSettings(updated)
        _uiState.update { it.copy(settings = updated) }
    }

    private fun syncStateFromGame(
        whiteMs: Long,
        blackMs: Long,
        flipped: Boolean,
        clearHint: Boolean
    ) {
        val pos = game.currentPosition
        val (capW, capB) = game.capturedPiecesUpTo()
        val opening = computeOpeningAtPly(game.plyCount)
        _uiState.update {
            it.copy(
                position = pos,
                gameStatus = game.status(),
                whiteTimeMs = whiteMs,
                blackTimeMs = blackMs,
                isBoardFlipped = flipped,
                selectedSquare = null,
                legalTargetsForSelected = emptySet(),
                lastMove = game.history.lastOrNull()?.move,
                kingInCheckSquare = kingCheckSquare(pos),
                history = game.history,
                viewingPly = game.plyCount,
                capturedByWhite = capW,
                capturedByBlack = capB,
                hintMove = if (clearHint) null else it.hintMove,
                recognizedOpening = opening
            )
        }
    }

    private fun refreshRecognizedOpening(ply: Int) {
        val opening = computeOpeningAtPly(ply)
        _uiState.update { it.copy(recognizedOpening = opening) }
    }

    private fun computeOpeningAtPly(ply: Int): OpeningEntry? {
        if (!openingBook.isLoaded.value) return null
        val maxPly = ply.coerceIn(0, game.plyCount)
        val fens = ArrayList<String>(maxPly + 1)
        for (p in 0..maxPly) {
            fens.add(game.fenAtPly(p))
        }
        return openingBook.findLatestOpeningInTimeline(fens)
    }

    private fun kingCheckSquare(position: ChessPosition): Square? {
        return if (MoveGenerator.isKingInCheck(position, position.sideToMove)) {
            MoveGenerator.findKingSquare(position, position.sideToMove)
        } else {
            null
        }
    }

    private fun startClockLoopIfNeeded() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive) {
                delay(100L)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTick).coerceIn(0L, 500L)
                lastTick = now

                val state = _uiState.value
                if (!state.timeControl.hasClock) continue
                if (state.gameStatus.isTerminal) continue
                if (state.gameMode == GameMode.ANALYSIS) continue
                // La pendule démarre après le premier coup des Blancs
                if (state.history.isEmpty()) continue

                val activeColor = game.currentPosition.sideToMove
                if (activeColor == PieceColor.WHITE) {
                    val remaining = (state.whiteTimeMs - elapsed).coerceAtLeast(0L)
                    if (remaining == 0L) {
                        game.flagTimeout(PieceColor.WHITE)
                        soundPlayer.playGameEnd()
                        syncStateFromGame(0L, state.blackTimeMs, state.isBoardFlipped, clearHint = true)
                        emitTerminalCoachSummary()
                        saveGameNow()
                    } else {
                        _uiState.update { it.copy(whiteTimeMs = remaining) }
                    }
                } else {
                    val remaining = (state.blackTimeMs - elapsed).coerceAtLeast(0L)
                    if (remaining == 0L) {
                        game.flagTimeout(PieceColor.BLACK)
                        soundPlayer.playGameEnd()
                        syncStateFromGame(state.whiteTimeMs, 0L, state.isBoardFlipped, clearHint = true)
                        emitTerminalCoachSummary()
                        saveGameNow()
                    } else {
                        _uiState.update { it.copy(blackTimeMs = remaining) }
                    }
                }
            }
        }
    }

    private fun saveGameNow() {
        val state = _uiState.value
        persistence.saveActiveGame(
            SavedGameSnapshot(
                initialFen = game.initialFen,
                uciMoves = game.uciMovesPlayed(),
                gameMode = state.gameMode,
                humanColor = state.humanColor,
                difficultyLevel = state.difficulty.level,
                timeControl = state.timeControl,
                whiteTimeMs = state.whiteTimeMs,
                blackTimeMs = state.blackTimeMs,
                isBoardFlipped = state.isBoardFlipped
            )
        )
    }

    override fun onCleared() {
        clockJob?.cancel()
        aiMoveJob?.cancel()
        coachJob?.cancel()
        saveGameNow()
        coordinator.release()
        soundPlayer.release()
        super.onCleared()
    }
}
