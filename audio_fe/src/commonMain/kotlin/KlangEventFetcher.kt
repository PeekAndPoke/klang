package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class KlangEventFetcher<T>(
    private val source: KlangEventSource<T>,
    private val state: KlangPlayerState,
    private val commLink: KlangCommLink.FrontendEndpoint,
    private val config: Config,
    private val transform: (T) -> ScheduledVoice,
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

        // TODO: we need two jobs
        //   1. (DONE) one that fetches audio events and send scheduled voice to the backend -> see existing code
        //   2. listening for request from the backend -> for example to receive sample pcm data

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
                        val voice = transform(e)
                        commLink.control.dispatch(KlangCommLink.Cmd.ScheduleVoice(voice))
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
