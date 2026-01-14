package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

class ReinterpretPattern private constructor(
    val source: StrudelPattern,
    val interpret: (evt: StrudelPatternEvent, ctx: QueryContext) -> StrudelPatternEvent,
) : StrudelPattern {

    companion object {
        fun StrudelPattern.reinterpret(interpret: (StrudelPatternEvent) -> StrudelPatternEvent): StrudelPattern =
            ReinterpretPattern(this) { evt, _ -> interpret(evt) }

        fun StrudelPattern.reinterpret(interpret: (StrudelPatternEvent, QueryContext) -> StrudelPatternEvent): StrudelPattern =
            ReinterpretPattern(this, interpret)

        fun StrudelPattern.reinterpretVoice(interpret: (voice: VoiceData) -> VoiceData): StrudelPattern =
            ReinterpretPattern(this, { evt, _ -> evt.copy(data = interpret(evt.data)) })
    }

    override val weight: Double get() = source.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)

        return sourceEvents.map { interpret(it, ctx) }
    }
}
