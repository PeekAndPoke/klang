package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.KlangPlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class StrudelEventsFetcher(
    private val pattern: StrudelPattern,
    private val options: StrudelPlayer.Options,
    private val state: KlangPlayerState,
    private val eventChannel: SendChannel<StrudelScheduledVoice>,
) {
    private val secPerCycle get() = 1.0 / options.cps
    private val framesPerCycle get() = secPerCycle * options.sampleRate.toDouble()

    suspend fun runFetcher(scope: CoroutineScope) {
        var queryCursorCycles = options.prefetchCycles.toDouble()
        val fetchChunk = 1.0

        while (scope.isActive) {
            val nowFrame = state.cursorFrame()
            val nowSec = nowFrame.toDouble() / options.sampleRate.toDouble()
            val nowCycles = nowSec / secPerCycle

            val targetCycles = nowCycles + (options.lookaheadSec / secPerCycle)

            while (queryCursorCycles < targetCycles) {
                val from = queryCursorCycles
                val to = from + fetchChunk

                try {
                    val events = fetchEventsSorted(from, to)

                    for (e in events) {
                        eventChannel.send(e.toScheduled())
                    }

                    queryCursorCycles = to
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.printStackTrace()
                    break
                }
            }
            delay(options.fetchPeriodMs)
        }
    }

    private fun fetchEventsSorted(from: Double, to: Double): List<StrudelPatternEvent> {
        return pattern.queryArc(from, to)
            .filter { it.begin >= from && it.begin < to }
            .sortedBy { it.begin }
    }

    private fun StrudelPatternEvent.toScheduled(): StrudelScheduledVoice {
        val startFrame = (begin * framesPerCycle).toLong()
        val durFrames = (dur * framesPerCycle).toLong().coerceAtLeast(1L)
        val releaseSec = release ?: 0.05
        val releaseFrames = (releaseSec * options.sampleRate).toLong()

        return StrudelScheduledVoice(
            startFrame = startFrame,
            endFrame = startFrame + durFrames + releaseFrames,
            gateEndFrame = startFrame + durFrames,
            evt = this,
        )
    }
}
