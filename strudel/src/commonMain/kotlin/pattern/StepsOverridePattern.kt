package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Wraps a pattern but overrides its steps value.
 */
internal class StepsOverridePattern(
    private val source: StrudelPattern,
    override val steps: Rational,
) : StrudelPattern by source
