package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.pattern.ContinuousPattern
import kotlin.math.PI
import kotlin.math.sin

object Sine : ContinuousPattern(
    getValue = { t -> sin(t * 2.0 * PI) }
)
