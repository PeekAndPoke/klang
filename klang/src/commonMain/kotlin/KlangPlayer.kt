package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.AudioBackend
import io.peekandpoke.klang.audio_be.AudioVisualizer
import io.peekandpoke.klang.audio_bridge.infra.KlangAtomicInt
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangLock
import io.peekandpoke.klang.audio_bridge.infra.withLock
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.*

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
    /** The dispatcher used for frontend callbacks */
    private val callbackDispatcher: CoroutineDispatcher,
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
    )

    // Shared communication link and backend
    val commLink = KlangCommLink(capacity = 8192)
    private var backendJob: Job? = null

    // Hold reference to active backend for visualizer access
    private var _activeBackend: AudioBackend? = null

    // Thread-safe state management
    private val lock = KlangLock()
    private val _activePlaybacks = mutableListOf<KlangPlayback>()

    // Signal bus for system-wide signals (diagnostics, etc.)
    val signals = KlangPlaybackSignals()
    private var feedbackDispatcherJob: Job? = null

    // Expose scope and dispatchers for playback implementations
    val playbackScope: CoroutineScope get() = scope
    val playbackFetcherDispatcher: CoroutineDispatcher get() = fetcherDispatcher
    val playbackCallbackDispatcher: CoroutineDispatcher get() = callbackDispatcher

    // Centralized sample preloader (shared across all playbacks)
    val samplePreloader = SamplePreloader(
        samples = options.samples,
        sendControl = ::sendControl,
        scope = scope,
        dispatcher = fetcherDispatcher,
    )

    // Context bundle for playback implementations (reduces constructor parameter lists)
    val playbackContext = KlangPlaybackContext(
        playerOptions = options,
        samplePreloader = samplePreloader,
        sendControl = ::sendControl,
        scope = scope,
        fetcherDispatcher = fetcherDispatcher,
        callbackDispatcher = callbackDispatcher,
    )

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

            // Store backend reference for visualizer access
            _activeBackend = backend

            launch(backendDispatcher.limitedParallelism(1)) {
                backend.run(this)
            }
        }

        // Start feedback dispatcher - the sole consumer of feedback messages
        // Use scope's default dispatcher (Dispatchers.Default) to avoid blocking fetcherDispatcher
        feedbackDispatcherJob = scope.launch {
            while (isActive) {
                val feedback = commLink.frontend.feedback.receive()

                if (feedback == null) {
                    delay(16) // Avoid busy-wait when no messages available
                    continue
                }

                when {
                    // System messages go to player signals
                    feedback.playbackId == KlangCommLink.SYSTEM_PLAYBACK_ID -> {
                        when (feedback) {
                            is KlangCommLink.Feedback.Diagnostics -> {
                                withContext(callbackDispatcher) {
                                    signals.emit(KlangPlaybackSignal.Diagnostics(feedback))
                                }
                            }

                            else -> {
                                // Ignore other system messages for now
                            }
                        }
                    }
                    // Playback-specific messages go to the matching playback
                    else -> {
                        val playback = lock.withLock {
                            _activePlaybacks.find { it.playbackId == feedback.playbackId }
                        }

                        playback?.handleFeedback(feedback)
                    }
                }
            }
        }

        println("[KlangPlayer] Init complete, backendJob=$backendJob, feedbackDispatcherJob=$feedbackDispatcherJob")
    }

    /**
     * Get the visualizer
     */
    fun getVisualizer(): AudioVisualizer? = _activeBackend?.visualizer

    /**
     * Register a playback.
     * Called by playback implementations (like StrudelPlayback) during initialization.
     */
    fun registerPlayback(playback: KlangPlayback) {
        lock.withLock {
            _activePlaybacks.add(playback)
        }
    }

    /**
     * Unregister a playback.
     * Called when a playback is stopped.
     */
    fun unregisterPlayback(playback: KlangPlayback) {
        lock.withLock {
            _activePlaybacks.remove(playback)
        }
    }

    /**
     * Generate a unique playback ID.
     */
    fun generatePlaybackId(): String = Companion.generatePlaybackId()

    /**
     * Send a control message to the backend.
     * This is the only way playbacks should communicate with the backend.
     */
    fun sendControl(cmd: KlangCommLink.Cmd) {
        commLink.frontend.control.send(cmd)
    }

    /**
     * Stop all playbacks and shut down the backend.
     */
    fun shutdown() {
        lock.withLock {
            // Stop all active playbacks (use toList() to avoid concurrent modification)
            _activePlaybacks.toList().forEach { it.stop() }
            _activePlaybacks.clear()

            // Shutdown the backend and clear reference
            backendJob?.cancel()
            backendJob = null
            _activeBackend = null

            // Shutdown the feedback dispatcher
            feedbackDispatcherJob?.cancel()
            feedbackDispatcherJob = null

            // Clear preloader cache
            samplePreloader.clear()

            // Clear signal listeners
            signals.clear()
        }
    }
}
