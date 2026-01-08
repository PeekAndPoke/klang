package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_fe.KlangEventSource
import io.peekandpoke.klang.strudel.math.Rational

class StrudelEventSource(private val pattern: StrudelPattern) : KlangEventSource<StrudelPatternEvent> {

    override fun query(from: Double, to: Double): List<StrudelPatternEvent> {
        // Convert Double time to Rational for exact pattern arithmetic
        val fromRational = Rational(from)
        val toRational = Rational(to)

        return pattern.queryArc(fromRational, toRational)
            .filter { it.begin >= fromRational && it.begin < toRational }
            .sortedBy { it.begin }
    }
}
