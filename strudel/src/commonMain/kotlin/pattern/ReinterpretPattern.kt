package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

class ReinterpretPattern(
    val source: StrudelPattern,
    val interpret: (StrudelPatternEvent) -> StrudelPatternEvent,
) : StrudelPattern {

    companion object {
        fun StrudelPattern.reinterpret(interpret: (StrudelPatternEvent) -> StrudelPatternEvent): StrudelPattern =
            ReinterpretPattern(this, interpret)

        fun StrudelPattern.reinterpretVoice(interpret: (VoiceData) -> VoiceData): StrudelPattern =
            ReinterpretPattern(this, { it.copy(data = interpret(it.data)) })
    }

    override val weight: Double get() = source.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)

        return sourceEvents.map(interpret)
    }
}
