package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.AudioBackend
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.KlangEventFetcher
import io.peekandpoke.klang.audio_fe.KlangEventSource
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.*
import kotlin.math.ceil

class KlangPlayer<T>(
    /** Sound event source */
    private val source: KlangEventSource<T>,
    /** Transformation from sound even to ScheduledVoice */
    private val transform: (T) -> ScheduledVoice,
    /** The player config */
    private val options: Options,
    /** Player Backend factory */
    private val backendFactory: suspend (config: AudioBackend.Config) -> AudioBackend,
    /** The coroutines scope on which the player runs */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** The dispatcher used for the event fetcher */
    private val fetcherDispatcher: CoroutineDispatcher,
    /** The dispatcher used for the audio backend */
    private val backendDispatcher: CoroutineDispatcher,
) {
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

    // TODO: make atomic
    private var running = false

    private var playerJob: Job? = null

    fun start() {
        if (running) return

        running = true

        playerJob = scope.launch {
            val commLink = KlangCommLink(capacity = 8192)

            val fetcher = KlangEventFetcher(
                config = KlangEventFetcher.Config(
                    samples = options.samples,
                    source = source,
                    commLink = commLink.frontend,
                    transform = transform,
                    sampleRate = options.sampleRate,
                    cps = options.cyclesPerSecond,
                    lookaheadSec = options.lookaheadSec,
                    prefetchCycles = options.prefetchCycles.toDouble()
                ),
            )

            val backend = backendFactory(
                AudioBackend.Config(
                    commLink = commLink.backend,
                    sampleRate = options.sampleRate,
                    blockSize = options.blockSize,
                ),
            )

            // Launch Fetcher - it decides its own dispatching if needed,
            // but usually inheriting the parent (Default) is fine for logic.
            launch(fetcherDispatcher.limitedParallelism(1)) {
                fetcher.run(this)
            }

            // Launch Audio Loop - it receives the state and channel
            launch(backendDispatcher.limitedParallelism(1)) {
                backend.run(this)
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false

        playerJob?.cancel()
        playerJob = null
    }
}
