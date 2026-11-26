package com.chessoffline.engine.uci

import com.chessoffline.engine.oex.OexEngineInfo
import com.chessoffline.engine.oex.OexError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Client bas niveau gérant le processus natif UCI (ex. Stockfish 19)
 * via flux `stdin`/`stdout` dans des coroutines dédiées sur `Dispatchers.IO`.
 */
class UciProcessClient(
    val engineInfo: OexEngineInfo
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private var process: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private var readerJob: Job? = null

    private val _stdoutLines = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val stdoutLines: SharedFlow<String> = _stdoutLines.asSharedFlow()

    @Volatile
    var detectedUciName: String = engineInfo.displayName
        private set

    val supportedOptions: MutableSet<String> = LinkedHashSet()

    @Volatile
    var isRunning: Boolean = false
        private set

    suspend fun startAndHandshake(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            stop()

            val pb = ProcessBuilder(engineInfo.executablePath).apply {
                redirectErrorStream(true)
                val workDir = File(engineInfo.executablePath).parentFile
                if (workDir != null && workDir.exists()) {
                    directory(workDir)
                }
                engineInfo.nativeLibraryDir?.let { libDir ->
                    val currentLd = environment()["LD_LIBRARY_PATH"].orEmpty()
                    environment()["LD_LIBRARY_PATH"] = if (currentLd.isBlank()) {
                        libDir
                    } else {
                        "$libDir:$currentLd"
                    }
                }
            }

            val proc = try {
                pb.start()
            } catch (e: Exception) {
                throw OexError.ExecutionDenied(
                    path = engineInfo.executablePath,
                    causeDetail = e.message ?: "Erreur ProcessBuilder"
                )
            }

            process = proc
            stdinWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
            isRunning = true

            val uciOkDeferred = CompletableDeferred<Unit>()
            val readyOkDeferred = CompletableDeferred<Unit>()

            readerJob = scope.launch {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        UciParser.parseIdName(line)?.let { detectedUciName = it }
                        UciParser.parseOptionName(line)?.let { supportedOptions.add(it) }

                        if (line.trim() == "uciok" && !uciOkDeferred.isCompleted) {
                            uciOkDeferred.complete(Unit)
                        }
                        if (line.trim() == "readyok" && !readyOkDeferred.isCompleted) {
                            readyOkDeferred.complete(Unit)
                        }
                        _stdoutLines.tryEmit(line)
                    }
                } catch (_: Exception) {
                    // Fermeture de flux attendue à l'arrêt du moteur
                } finally {
                    isRunning = false
                    if (!uciOkDeferred.isCompleted) {
                        uciOkDeferred.completeExceptionally(
                            OexError.EngineCrash(engineInfo.displayName, "Flux terminé avant uciok")
                        )
                    }
                    if (!readyOkDeferred.isCompleted) {
                        readyOkDeferred.completeExceptionally(
                            OexError.EngineCrash(engineInfo.displayName, "Flux terminé avant readyok")
                        )
                    }
                }
            }

            sendCommand("uci")
            withTimeout(5_000L) { uciOkDeferred.await() }

            // Configuration optimale mobile : 2 threads et 32 Mo de table de transposition
            if ("Threads" in supportedOptions) sendCommand("setoption name Threads value 2")
            if ("Hash" in supportedOptions) sendCommand("setoption name Hash value 32")

            sendCommand("isready")
            withTimeout(5_000L) { readyOkDeferred.await() }

            detectedUciName
        }
    }

    suspend fun sendCommand(command: String) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val writer = stdinWriter ?: throw OexError.EngineCrash(
                engineInfo.displayName,
                "Le moteur n'est pas démarré"
            )
            writer.write(command)
            writer.newLine()
            writer.flush()
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        isRunning = false
        runCatching {
            stdinWriter?.let { w ->
                w.write("quit")
                w.newLine()
                w.flush()
            }
        }
        runCatching { stdinWriter?.close() }
        stdinWriter = null
        readerJob?.cancel()
        readerJob = null
        runCatching { process?.destroy() }
        process = null
    }

    fun dispose() {
        runCatching { process?.destroyForcibly() }
        scope.cancel()
    }
}
