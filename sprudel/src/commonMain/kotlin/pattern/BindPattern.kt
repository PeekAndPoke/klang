package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Generic pattern that applies bind (inner join) operation.
 *
 * @param outer The outer pattern that defines the structure
 * @param transform Function that generates an inner pattern from each outer event
 * @param preserveMetadata If true, weight and numSteps are delegated to [outer].
 *                         If false, they are reset (1.0 and null). Defaults to true.
 */
internal class BindPattern(
    private val outer: SprudelPattern,
    private val preserveMetadata: Boolean = true,
    private val transform: (SprudelPatternEvent) -> SprudelPattern?,
) : SprudelPattern {

    override val weight: Double
        get() = if (preserveMetadata) outer.weight else 1.0

    override val numSteps: Rational?
        get() = if (preserveMetadata) outer.numSteps else null

    override fun estimateCycleDuration(): Rational =
        if (preserveMetadata) outer.estimateCycleDuration() else Rational.ONE

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        // ... (The inner join logic we normalized previously) ...
        // 1. Query outer pattern
        val outerEvents = outer.queryArcContextual(from, to, ctx)
        val result = mutableListOf<SprudelPatternEvent>()

        // val f = from
        // val t = to
        // println("From: ${f.toDouble()} | To: ${t.toDouble()} | Events: ${outerEvents.map { it.data.value?.asString}}")

        for (outerEvent in outerEvents) {
            val innerPattern = transform(outerEvent) ?: continue

            val intersectStart = maxOf(from, outerEvent.part.begin)
            val intersectEnd = minOf(to, outerEvent.part.end)

            if (intersectEnd <= intersectStart) continue

            val innerEvents = innerPattern.queryArcContextual(intersectStart, intersectEnd, ctx)

            for (innerEvent in innerEvents) {
                val clippedPart = innerEvent.part.clipTo(outerEvent.part)

                if (clippedPart != null) {
                    result.add(
                        // CRITICAL: preserve whole - don't modify it!
                        innerEvent.copy(
                            part = clippedPart
                        )
                    )
                }
            }
        }

        return result
    }
}
