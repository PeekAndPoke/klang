package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.AudioBackend
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

    // Thread-safe state management
    private val lock = KlangLock()
    private val _activePlaybacks = mutableListOf<KlangPlayback>()

    // Expose scope and dispatchers for playback implementations
    val playbackScope: CoroutineScope get() = scope
    val playbackFetcherDispatcher: CoroutineDispatcher get() = fetcherDispatcher
    val playbackCallbackDispatcher: CoroutineDispatcher get() = callbackDispatcher

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
