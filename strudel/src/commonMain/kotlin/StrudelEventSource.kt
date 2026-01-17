package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_fe.KlangEventSource
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.math.Rational

class StrudelEventSource(
    private val pattern: StrudelPattern,
    private val cyclesPerSecond: Double,
    private val sampleRate: Int,
) : KlangEventSource {

    override fun query(from: Double, to: Double): List<ScheduledVoice> {
        // Convert Double time to Rational for exact pattern arithmetic
        val fromRational = Rational(from)
        val toRational = Rational(to)

//        println("Querying " +
//                "${fromRational.toDouble()} (${fromRational.numerator}/${fromRational.denominator}) to " +
//                "${toRational.toDouble()} (${toRational.numerator}/${toRational.denominator})")

        val events = pattern.queryArcContextual(from = fromRational, to = toRational, QueryContext.empty)
            .filter { it.begin >= fromRational && it.begin < toRational }
            .sortedBy { it.begin }

        // Transform to ScheduledVoice
        val secPerCycle = 1.0 / cyclesPerSecond
        val framesPerCycle = secPerCycle * sampleRate.toDouble()

        return events.map { event ->
            val startFrame = (event.begin * framesPerCycle).toLong()
            val durFrames = (event.dur * framesPerCycle).toLong().coerceAtLeast(1L)

            ScheduledVoice(
                data = event.data,
                startFrame = startFrame,
                gateEndFrame = startFrame + durFrames,
            )
        }
    }
}
