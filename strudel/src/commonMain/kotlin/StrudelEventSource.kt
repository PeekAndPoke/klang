package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_fe.KlangEventSource

class StrudelEventSource(private val pattern: StrudelPattern) : KlangEventSource<StrudelPatternEvent> {

    override fun query(from: Double, to: Double): List<StrudelPatternEvent> {
        return pattern.queryArc(from, to)
            .filter { it.begin >= from && it.begin < to }
            .sortedBy { it.begin }
    }
}
