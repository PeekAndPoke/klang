package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import io.peekandpoke.klang.audio_bridge.infra.KlangRingBuffer
import io.peekandpoke.klang.audio_fe.KlangEventFetcher
import io.peekandpoke.klang.audio_fe.KlangEventSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil

class KlangPlayer<T, S>(
    private val source: KlangEventSource<T>,
    private val options: Options,
    private val transform: (T) -> S,
    // The loop function itself. It suspends until playback stops.
    private val audioLoop: suspend (KlangPlayerState, KlangRingBuffer<S>) -> Unit,
    // External scope controls the lifecycle
    private val scope: CoroutineScope,
) : AutoCloseable {

    data class Options(
        val sampleRate: Int = 48_000,
        val cps: Double = 0.5,
        val lookaheadSec: Double = 1.0,
        val fetchPeriodMs: Long = 250L,
        val prefetchCycles: Int = ceil(maxOf(2.0, cps * 2)).toInt(),
    )

    private val state = KlangPlayerState()
    private var playerJob: Job? = null

    fun start() {
        if (!state.running(expect = false, update = true)) return

        playerJob = scope.launch {
            val channel = KlangRingBuffer<S>(capacity = 8192)

            val fetcher = KlangEventFetcher(
                source = source,
                state = state,
                eventChannel = channel,
                config = KlangEventFetcher.Config(
                    sampleRate = options.sampleRate,
                    cps = options.cps,
                    lookaheadSec = options.lookaheadSec,
                    fetchPeriodMs = options.fetchPeriodMs,
                    prefetchCycles = options.prefetchCycles.toDouble()
                ),
                transform = transform
            )

            // Launch Fetcher - it decides its own dispatching if needed,
            // but usually inheriting the parent (Default) is fine for logic.
            launch(Dispatchers.Default.limitedParallelism(1)) {
                fetcher.runFetcher(this)
            }

            // Launch Audio Loop - it receives the state and channel
            launch {
                audioLoop(state, channel)
            }
        }
    }

    fun stop() {
        if (!state.running(expect = true, update = false)) return
        playerJob?.cancel()
        playerJob = null
        state.cursorFrame(0L)
    }

    override fun close() = stop()
}
