package com.chessoffline.engine

import android.content.Context
import com.chessoffline.engine.oex.OexDiscovery
import com.chessoffline.engine.oex.OexEngineInfo
import com.chessoffline.engine.oex.OexError
import com.chessoffline.engine.uci.DifficultyLevel
import com.chessoffline.engine.uci.MultiPvHumanizer
import com.chessoffline.engine.uci.UciInfoLine
import com.chessoffline.engine.uci.UciParser
import com.chessoffline.engine.uci.UciProcessClient
import com.chessoffline.engine.uci.UciScore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * État courant de la détection et connexion au moteur OEX (ex. Stockfish 19).
 */
sealed class EngineConnectionState {
    data object Idle : EngineConnectionState()
    data object Discovering : EngineConnectionState()
    data class Ready(
        val engineInfo: OexEngineInfo,
        val uciName: String,
        val availableEngines: List<OexEngineInfo>
    ) : EngineConnectionState()

    data class Error(val error: OexError) : EngineConnectionState()
}

/**
 * Résultat d'analyse en direct (barre d'évaluation + meilleur coup suggéré).
 */
data class AnalysisSnapshot(
    val score: UciScore? = null,
    val bestMoveUci: String? = null,
    val depth: Int = 0,
    val pvLine: List<String> = emptyList()
)

/**
 * Coordinateur haut niveau gérant la découverte OEX, le cycle de vie UCI de Stockfish 19,
 * la génération de coups IA selon les 10 niveaux, les indices et la barre d'évaluation.
 */
class StockfishCoordinator(
    private val appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val searchMutex = Mutex()

    private val _connectionState = MutableStateFlow<EngineConnectionState>(EngineConnectionState.Idle)
    val connectionState: StateFlow<EngineConnectionState> = _connectionState.asStateFlow()

    private val _analysis = MutableStateFlow(AnalysisSnapshot())
    val analysis: StateFlow<AnalysisSnapshot> = _analysis.asStateFlow()

    private var client: UciProcessClient? = null
    private var analysisJob: Job? = null

    suspend fun initializeDefaultEngine(preferredEngineId: String? = null): Result<String> {
        _connectionState.value = EngineConnectionState.Discovering
        val engines = OexDiscovery.discoverEngines(appContext)
        if (engines.isEmpty()) {
            val err = OexError.NoEngineInstalled
            _connectionState.value = EngineConnectionState.Error(err)
            return Result.failure(err)
        }

        val chosen = engines.find { it.id == preferredEngineId } ?: engines.first()
        return connectToEngine(chosen, engines)
    }

    suspend fun connectToEngine(
        engineInfo: OexEngineInfo,
        allEngines: List<OexEngineInfo>? = null
    ): Result<String> {
        stopAnalysis()
        client?.stop()

        val newClient = UciProcessClient(engineInfo)
        val res = newClient.startAndHandshake()
        return if (res.isSuccess) {
            client = newClient
            val uciName = res.getOrThrow()
            val list = allEngines ?: OexDiscovery.discoverEngines(appContext)
            _connectionState.value = EngineConnectionState.Ready(
                engineInfo = engineInfo,
                uciName = uciName,
                availableEngines = list
            )
            Result.success(uciName)
        } else {
            val err = (res.exceptionOrNull() as? OexError)
                ?: OexError.EngineCrash(engineInfo.displayName, res.exceptionOrNull()?.message ?: "Erreur inconnue")
            _connectionState.value = EngineConnectionState.Error(err)
            Result.failure(err)
        }
    }

    suspend fun computeAiMove(
        fen: String,
        sideToMoveIsWhite: Boolean,
        difficulty: DifficultyLevel
    ): Result<String> = searchMutex.withLock {
        stopAnalysisInternal()

        val activeClient = client ?: return Result.failure(OexError.NoEngineInstalled)
        if (!activeClient.isRunning) {
            val reconnect = activeClient.startAndHandshake()
            if (reconnect.isFailure) return Result.failure(reconnect.exceptionOrNull()!!)
        }

        runCatching {
            val opts = activeClient.supportedOptions

            if ("MultiPV" in opts) {
                activeClient.sendCommand("setoption name MultiPV value ${difficulty.multiPv}")
            }

            if (difficulty.limitStrengthElo != null && "UCI_LimitStrength" in opts && "UCI_Elo" in opts) {
                activeClient.sendCommand("setoption name UCI_LimitStrength value true")
                activeClient.sendCommand("setoption name UCI_Elo value ${difficulty.limitStrengthElo}")
            } else {
                if ("UCI_LimitStrength" in opts) {
                    activeClient.sendCommand("setoption name UCI_LimitStrength value false")
                }
                if ("Skill Level" in opts) {
                    activeClient.sendCommand("setoption name Skill Level value ${difficulty.skillLevel}")
                }
            }

            activeClient.sendCommand("position fen $fen")

            val multiPvCandidates = LinkedHashMap<Int, UciInfoLine>()
            val bestMoveDeferred = CompletableDeferred<String>()

            val listenerJob = scope.launch {
                activeClient.stdoutLines.collect { line ->
                    UciParser.parseInfo(line, sideToMoveIsWhite)?.let { info ->
                        if (info.pvMoves.isNotEmpty()) {
                            multiPvCandidates[info.multiPv] = info
                        }
                    }
                    UciParser.parseBestMove(line)?.let { best ->
                        if (!bestMoveDeferred.isCompleted) {
                            bestMoveDeferred.complete(best.bestMove)
                        }
                    }
                }
            }

            try {
                val goCmd = buildString {
                    append("go")
                    if (difficulty.depthCap != null) append(" depth ${difficulty.depthCap}")
                    if (difficulty.moveTimeMs != null) append(" movetime ${difficulty.moveTimeMs}")
                }
                activeClient.sendCommand(goCmd)

                val timeoutMs = (difficulty.moveTimeMs ?: 1500L) + 4000L
                val rawBest = withTimeout(timeoutMs) { bestMoveDeferred.await() }

                if (difficulty.multiPv > 1) {
                    MultiPvHumanizer.pickMoveFromMultiPv(
                        candidates = multiPvCandidates,
                        difficulty = difficulty,
                        sideToMoveIsWhite = sideToMoveIsWhite,
                        fallbackBestMove = rawBest
                    )
                } else {
                    rawBest
                }
            } finally {
                listenerJob.cancel()
            }
        }
    }

    /**
     * Calcule un indice ("Meilleur coup") et met à jour la barre d'évaluation à force maximale.
     */
    suspend fun computeBestHint(
        fen: String,
        sideToMoveIsWhite: Boolean,
        moveTimeMs: Long = 700L
    ): Result<AnalysisSnapshot> = searchMutex.withLock {
        stopAnalysisInternal()

        val activeClient = client ?: return Result.failure(OexError.NoEngineInstalled)
        if (!activeClient.isRunning) {
            val reconnect = activeClient.startAndHandshake()
            if (reconnect.isFailure) return Result.failure(reconnect.exceptionOrNull()!!)
        }

        runCatching {
            val opts = activeClient.supportedOptions
            if ("MultiPV" in opts) activeClient.sendCommand("setoption name MultiPV value 1")
            if ("UCI_LimitStrength" in opts) activeClient.sendCommand("setoption name UCI_LimitStrength value false")
            if ("Skill Level" in opts) activeClient.sendCommand("setoption name Skill Level value 20")

            activeClient.sendCommand("position fen $fen")

            var latestSnapshot = AnalysisSnapshot()
            val bestMoveDeferred = CompletableDeferred<String>()

            val listenerJob = scope.launch {
                activeClient.stdoutLines.collect { line ->
                    UciParser.parseInfo(line, sideToMoveIsWhite)?.let { info ->
                        if (info.multiPv == 1 && info.pvMoves.isNotEmpty()) {
                            latestSnapshot = AnalysisSnapshot(
                                score = info.score ?: latestSnapshot.score,
                                bestMoveUci = info.pvMoves.first(),
                                depth = info.depth,
                                pvLine = info.pvMoves
                            )
                            _analysis.value = latestSnapshot
                        }
                    }
                    UciParser.parseBestMove(line)?.let { best ->
                        if (!bestMoveDeferred.isCompleted) {
                            bestMoveDeferred.complete(best.bestMove)
                        }
                    }
                }
            }

            try {
                activeClient.sendCommand("go movetime $moveTimeMs")
                val finalBest = withTimeout(moveTimeMs + 3000L) { bestMoveDeferred.await() }
                latestSnapshot = latestSnapshot.copy(bestMoveUci = finalBest)
                _analysis.value = latestSnapshot
                latestSnapshot
            } finally {
                listenerJob.cancel()
            }
        }
    }

    /**
     * Évalue rapidement une position (sans modifier l'affichage si [updateUiAnalysis] est faux)
     * afin d'alimenter l'analyse du Coach avant/après un coup.
     */
    suspend fun evaluateQuick(
        fen: String,
        sideToMoveIsWhite: Boolean,
        moveTimeMs: Long = 220L,
        updateUiAnalysis: Boolean = false
    ): Result<AnalysisSnapshot> = searchMutex.withLock {
        stopAnalysisInternal()

        val activeClient = client ?: return Result.failure(OexError.NoEngineInstalled)
        if (!activeClient.isRunning) {
            val reconnect = activeClient.startAndHandshake()
            if (reconnect.isFailure) return Result.failure(reconnect.exceptionOrNull()!!)
        }

        runCatching {
            val opts = activeClient.supportedOptions
            if ("MultiPV" in opts) activeClient.sendCommand("setoption name MultiPV value 1")
            if ("UCI_LimitStrength" in opts) activeClient.sendCommand("setoption name UCI_LimitStrength value false")
            if ("Skill Level" in opts) activeClient.sendCommand("setoption name Skill Level value 20")

            activeClient.sendCommand("position fen $fen")

            var latestSnapshot = AnalysisSnapshot()
            val bestMoveDeferred = CompletableDeferred<String>()

            val listenerJob = scope.launch {
                activeClient.stdoutLines.collect { line ->
                    UciParser.parseInfo(line, sideToMoveIsWhite)?.let { info ->
                        if (info.multiPv == 1 && info.pvMoves.isNotEmpty()) {
                            latestSnapshot = AnalysisSnapshot(
                                score = info.score ?: latestSnapshot.score,
                                bestMoveUci = info.pvMoves.first(),
                                depth = info.depth,
                                pvLine = info.pvMoves
                            )
                        }
                    }
                    UciParser.parseBestMove(line)?.let { best ->
                        if (!bestMoveDeferred.isCompleted) {
                            bestMoveDeferred.complete(best.bestMove)
                        }
                    }
                }
            }

            try {
                activeClient.sendCommand("go movetime $moveTimeMs")
                val finalBest = withTimeout(moveTimeMs + 2500L) { bestMoveDeferred.await() }
                latestSnapshot = latestSnapshot.copy(bestMoveUci = finalBest)
                if (updateUiAnalysis) {
                    _analysis.value = latestSnapshot
                }
                latestSnapshot
            } finally {
                listenerJob.cancel()
            }
        }
    }

    /**
     * Lance une évaluation rapide en arrière-plan pour la barre d'évaluation en Mode Analyse / Entraînement.
     */
    fun startBackgroundEval(fen: String, sideToMoveIsWhite: Boolean, depthLimit: Int = 14) {
        analysisJob?.cancel()
        analysisJob = scope.launch {
            searchMutex.withLock {
                val activeClient = client ?: return@withLock
                if (!activeClient.isRunning) {
                    if (activeClient.startAndHandshake().isFailure) return@withLock
                }

                runCatching {
                    val opts = activeClient.supportedOptions
                    if ("MultiPV" in opts) activeClient.sendCommand("setoption name MultiPV value 1")
                    if ("UCI_LimitStrength" in opts) activeClient.sendCommand("setoption name UCI_LimitStrength value false")
                    if ("Skill Level" in opts) activeClient.sendCommand("setoption name Skill Level value 20")

                    activeClient.sendCommand("position fen $fen")
                    val done = CompletableDeferred<Unit>()

                    val collectJob = launch {
                        activeClient.stdoutLines.collect { line ->
                            UciParser.parseInfo(line, sideToMoveIsWhite)?.let { info ->
                                if (info.multiPv == 1) {
                                    _analysis.value = AnalysisSnapshot(
                                        score = info.score ?: _analysis.value.score,
                                        bestMoveUci = info.pvMoves.firstOrNull() ?: _analysis.value.bestMoveUci,
                                        depth = info.depth,
                                        pvLine = info.pvMoves
                                    )
                                }
                            }
                            if (UciParser.parseBestMove(line) != null && !done.isCompleted) {
                                done.complete(Unit)
                            }
                        }
                    }

                    try {
                        activeClient.sendCommand("go depth $depthLimit")
                        withTimeout(3000L) { done.await() }
                    } catch (_: Exception) {
                        runCatching { activeClient.sendCommand("stop") }
                    } finally {
                        collectJob.cancel()
                    }
                }
            }
        }
    }

    fun stopAnalysis() {
        scope.launch {
            stopAnalysisInternal()
        }
    }

    private suspend fun stopAnalysisInternal() {
        analysisJob?.cancel()
        analysisJob = null
        val active = client
        if (active != null && active.isRunning) {
            runCatching { active.sendCommand("stop") }
        }
    }

    fun clearAnalysis() {
        _analysis.value = AnalysisSnapshot()
    }

    fun release() {
        analysisJob?.cancel()
        client?.dispose()
    }
}
