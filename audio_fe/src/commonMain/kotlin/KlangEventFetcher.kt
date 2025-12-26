package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.infra.KlangEventDispatcher
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class KlangEventFetcher<T, S>(
    private val source: KlangEventSource<T>,
    private val state: KlangPlayerState,
    private val eventChannel: KlangEventDispatcher<S>,
    private val config: Config,
    private val transform: (T) -> S,
) {
    data class Config(
        val sampleRate: Int,
        val cps: Double,
        val lookaheadSec: Double,
        val fetchPeriodMs: Long,
        val prefetchCycles: Double,
    )

    private val secPerCycle get() = 1.0 / config.cps

    suspend fun runFetcher(scope: CoroutineScope) {
        var queryCursorCycles = 0.0
        val fetchChunk = 1.0

        while (scope.isActive) {
            val nowFrame = state.cursorFrame()
            val nowSec = nowFrame.toDouble() / config.sampleRate.toDouble()
            val nowCycles = nowSec / secPerCycle

            val targetCycles = nowCycles + (config.lookaheadSec / secPerCycle)

            while (queryCursorCycles < targetCycles) {
                val from = queryCursorCycles
                val to = from + fetchChunk

                try {
                    val events = source.query(from, to)

                    for (e in events) {
                        // Transform source event T to scheduled event S
                        eventChannel.dispatch(transform(e))
                    }

                    queryCursorCycles = to
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.printStackTrace()
                    break
                }
            }

            delay(config.fetchPeriodMs)
        }
    }
}
