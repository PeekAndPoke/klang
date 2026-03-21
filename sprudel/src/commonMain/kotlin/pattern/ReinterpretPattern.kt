package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData

internal class ReinterpretPattern internal constructor(
    val source: SprudelPattern,
    val interpret: (evt: SprudelPatternEvent, ctx: QueryContext) -> SprudelPatternEvent,
) : SprudelPattern {

    companion object {
        fun SprudelPattern.reinterpret(interpret: (SprudelPatternEvent) -> SprudelPatternEvent): SprudelPattern =
            ReinterpretPattern(this) { evt, _ -> interpret(evt) }

        fun SprudelPattern.reinterpret(interpret: (SprudelPatternEvent, QueryContext) -> SprudelPatternEvent): SprudelPattern =
            ReinterpretPattern(this, interpret)

        fun SprudelPattern.reinterpretVoice(interpret: (voice: SprudelVoiceData) -> SprudelVoiceData): SprudelPattern =
            ReinterpretPattern(this, { evt, _ -> evt.copy(data = interpret(evt.data)) })
    }

    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)

        return sourceEvents.map { interpret(it, ctx) }
    }
}
