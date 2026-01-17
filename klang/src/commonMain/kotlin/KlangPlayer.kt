package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.AudioBackend
import io.peekandpoke.klang.audio_bridge.infra.*
import io.peekandpoke.klang.audio_fe.KlangEventFetcher
import io.peekandpoke.klang.audio_fe.KlangEventSource
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.*
import kotlin.math.ceil

class KlangPlayer(
    /** The player config */
    val options: Options,
    /** Player Backend factory */
    private val backendFactory: suspend (config: AudioBackend.Config) -> AudioBackend,
    /** The coroutines scope on which the player runs */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** The dispatcher used for the event fetcher */
    private val fetcherDispatcher: CoroutineDispatcher,
    /** The dispatcher used for the audio backend */
    private val backendDispatcher: CoroutineDispatcher,
) {
    companion object {
        private val nextPlaybackId = KlangAtomicInt(0)
        private fun generatePlaybackId(): String = "playback-${nextPlaybackId.getAndIncrement()}"
    }
    data class Options(
        /** The samples */
        val samples: Samples,
        /** The sample rate to use for audio playback */
        val sampleRate: Int = 48_000,
        /** The audio rendering block size */
        val blockSize: Int = 512,
        /** Amount of time to look ahead in the [KlangEventSource] */
        val lookaheadSec: Double = 1.0,
        // TODO: use BPM instead and let strudel do the conversion to CPS
        val cyclesPerSecond: Double = 0.5,
        /** Initial cycles prefetch, so that the audio starts flawlessly */
        val prefetchCycles: Int = ceil(maxOf(2.0, cyclesPerSecond * 2)).toInt(),
    )

    // Shared communication link and backend
    private val commLink = KlangCommLink(capacity = 8192)
    private var backendJob: Job? = null

    // Thread-safe state management
    private val lock = KlangLock()
    private val _activePlaybacks = mutableListOf<KlangPlayback>()

    /**
     * Read-only list of currently active playbacks
     */
    val activePlaybacks: List<KlangPlayback>
        get() = lock.withLock { _activePlaybacks.toList() }

    init {
        // Start the audio backend when the player is created
        backendJob = scope.launch {
            val backend = backendFactory(
                AudioBackend.Config(
                    commLink = commLink.backend,
                    sampleRate = options.sampleRate,
                    blockSize = options.blockSize,
                ),
            )

            launch(backendDispatcher.limitedParallelism(1)) {
                backend.run(this)
            }
        }
    }

    /**
     * Create a new playback for the given source.
     */
    fun play(source: KlangEventSource): KlangPlayback {
        val playback = KlangPlayback(
            playbackId = generatePlaybackId(),
            source = source,
            options = options,
            commLink = commLink,
            scope = scope,
            fetcherDispatcher = fetcherDispatcher,
            onStopped = { stopped ->
                lock.withLock { _activePlaybacks.remove(stopped) }
            }
        )
        lock.withLock {
            _activePlaybacks.add(playback)
        }
        return playback
    }

    /**
     * Stop all playbacks and shut down the backend.
     */
    fun shutdown() {
        lock.withLock {
            // Stop all active playbacks (use toList() to avoid concurrent modification)
            _activePlaybacks.toList().forEach { it.stop() }
            _activePlaybacks.clear()

            // Shutdown the backend
            backendJob?.cancel()
            backendJob = null
        }
    }
}

class KlangPlayback internal constructor(
    /** Unique identifier for this playback */
    private val playbackId: String,
    /** Sound event source */
    private val source: KlangEventSource,
    /** The player config */
    private val options: KlangPlayer.Options,
    /** Shared communication link to the backend */
    private val commLink: KlangCommLink,
    /** The coroutines scope on which the player runs */
    private val scope: CoroutineScope,
    /** The dispatcher used for the event fetcher */
    private val fetcherDispatcher: CoroutineDispatcher,
    /** Callback invoked when this playback is stopped */
    private val onStopped: (KlangPlayback) -> Unit = {},
) {
    // Thread-safe state management
    private val running = KlangAtomicBool(false)
    private val jobLock = KlangLock()
    private var fetcherJob: Job? = null

    fun start() {
        // Atomically transition from stopped to running
        if (!running.compareAndSet(expect = false, update = true)) {
            // Already running
            return
        }

        // Start the fetcher job
        jobLock.withLock {
            fetcherJob = scope.launch(fetcherDispatcher.limitedParallelism(1)) {
                val fetcher = KlangEventFetcher(
                    config = KlangEventFetcher.Config(
                        playbackId = playbackId,
                        samples = options.samples,
                        source = source,
                        commLink = commLink.frontend,
                        sampleRate = options.sampleRate,
                        cps = options.cyclesPerSecond,
                        lookaheadSec = options.lookaheadSec,
                        prefetchCycles = options.prefetchCycles.toDouble()
                    ),
                )

                fetcher.run(this)
            }
        }
    }

    fun stop() {
        // Atomically transition from running to stopped
        if (!running.compareAndSet(expect = true, update = false)) {
            // Already stopped
            return
        }

        // Cancel the fetcher job
        jobLock.withLock {
            fetcherJob?.cancel()
            fetcherJob = null
        }

        // Notify the player that this playback has stopped (outside lock to avoid deadlock)
        onStopped(this)
    }
}
