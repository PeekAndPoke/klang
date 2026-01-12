@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.math.PerlinNoise
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern.Companion.withContext
import io.peekandpoke.klang.strudel.pattern.ContinuousPattern
import io.peekandpoke.klang.strudel.pattern.EmptyPattern
import kotlin.math.PI
import kotlin.math.sin

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangContinuousInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Continuous patterns
// ///

/** Empty pattern that does not produce any events */
@StrudelDsl
val silence by dslObject { EmptyPattern }

/** Empty pattern that does not produce any events */
@StrudelDsl
val rest by dslObject { silence }

/** Sine oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val sine by dslObject {
    ContinuousPattern { t -> (sin(t * 2.0 * PI) + 1.0) / 2.0 }
}

/** Sawtooth oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val saw by dslObject {
    ContinuousPattern { t -> t % 1.0 }
}

/** Inverse Sawtooth oscillator: 1 to 0, period of 1 cycle */
@StrudelDsl
val isaw by dslObject {
    ContinuousPattern { t -> 1.0 - (t % 1.0) }
}

/** Triangle oscillator: 0 to 1 to 0, period of 1 cycle */
@StrudelDsl
val tri by dslObject {
    ContinuousPattern { t ->
        val phase = t % 1.0
        if (phase < 0.5) phase * 2.0 else 2.0 - (phase * 2.0)
    }
}

/** Square oscillator: 0 or 1, period of 1 cycle */
@StrudelDsl
val square by dslObject {
    ContinuousPattern { t -> if (t % 1.0 < 0.5) 0.0 else 1.0 }
}

/** Square oscillator: 0 or 1, period of 1 cycle */
@StrudelDsl
val perlin by dslObject {
    ContinuousPattern { t -> (PerlinNoise.noise(t) + 1.0) / 2.0 }
}

/**
 * Sets the range of a continuous pattern to a new minimum and maximum value.
 */
@StrudelDsl
val StrudelPattern.range by dslPatternExtension { p, args ->

    // TODO: We must be able to provide these parameters from other patterns as well
    val min = args.getOrNull(0)?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.asDoubleOrNull() ?: 1.0

    p.withContext {
        setIfAbsent(ContinuousPattern.minKey, min)
        setIfAbsent(ContinuousPattern.maxKey, max)
    }
}
