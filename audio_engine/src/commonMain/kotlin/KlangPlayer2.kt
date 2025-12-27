package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.KlangPlayerBackend2
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import io.peekandpoke.klang.audio_engine.KlangPlayer.Options
import io.peekandpoke.klang.audio_fe.KlangEventFetcher
import io.peekandpoke.klang.audio_fe.KlangEventSource
import kotlinx.coroutines.*


class KlangPlayer2<T>(
    private val source: KlangEventSource<T>,
    private val transform: (T) -> ScheduledVoice,
    private val options: Options,
    // The loop function itself. It suspends until playback stops.
    // TODO: combine into one parameter object
    private val backendFactory: suspend (config: KlangPlayerBackend2.Config) -> KlangPlayerBackend2,
    // External scope controls the lifecycle
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val fetcherDispatcher: CoroutineDispatcher,
    private val backendDispatcher: CoroutineDispatcher,
) {
    private val state = KlangPlayerState()

    private var playerJob: Job? = null

    fun start() {
        if (!state.running(expect = false, update = true)) return

        playerJob = scope.launch {
            val commLink = KlangCommLink(capacity = 8192)

            // TODO: combine all params in Config
            val fetcher = KlangEventFetcher(
                samples = options.samples,
                source = source,
                state = state,
                commLink = commLink.frontend,
                config = KlangEventFetcher.Config(
                    sampleRate = options.sampleRate,
                    cps = options.cyclesPerSecond,
                    lookaheadSec = options.lookaheadSec,
                    fetchPeriodMs = options.fetchPeriodMs,
                    prefetchCycles = options.prefetchCycles.toDouble()
                ),
                transform = transform
            )

            val backend = backendFactory(
                KlangPlayerBackend2.Config(
                    state = state,
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
        if (!state.running(expect = true, update = false)) return
        playerJob?.cancel()
        playerJob = null
    }
}
