package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.silence
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Empty pattern, f.e. for [silence]
 */
object EmptyPattern : StrudelPattern.FixedWeight {
    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        return emptyList()
    }
}
