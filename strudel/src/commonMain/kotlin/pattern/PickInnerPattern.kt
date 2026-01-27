package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.bind
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that selects from a lookup table based on a selector pattern and flattens with innerJoin.
 *
 * @param selector Pattern providing selection indices/keys
 * @param lookup Map of patterns to select from (keys can be Int for lists or String for maps)
 * @param modulo If true, wrap out-of-bounds indices; if false, clamp them
 * @param extractKey Function to extract the lookup key from a selector event value
 */
internal class PickInnerPattern(
    private val selector: StrudelPattern,
    private val lookup: Map<Any, StrudelPattern>,
    private val modulo: Boolean,
    private val extractKey: (StrudelVoiceData, Boolean, Int) -> Any?,
) : StrudelPattern {

    override val weight: Double get() = selector.weight
    override val steps: Rational? get() = selector.steps
    override fun estimateCycleDuration(): Rational = selector.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        return selector.bind(from, to, ctx) { selectorEvent ->
            val key: Any? = extractKey(selectorEvent.data, modulo, lookup.size)
            if (key != null) lookup[key] else null
        }
    }
}
