@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.math.BerlinNoise
import io.peekandpoke.klang.strudel.math.PerlinNoise
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern
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

// -- Continuous patterns settings -------------------------------------------------------------------------------------

private fun applyRange(pattern: StrudelPattern, args: List<Any?>): ContextModifierPattern {
    val min = args.getOrNull(0)?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.asDoubleOrNull() ?: 1.0
    val granularity = args.getOrNull(2)?.asDoubleOrNull()?.toRational() ?: Rational.ONE

    return pattern.withContext {
        setIfAbsent(ContinuousPattern.minKey, min)
        setIfAbsent(ContinuousPattern.maxKey, max)
        setIfAbsent(ContinuousPattern.granularityKey, granularity)
    }
}

/**
 * Sets the range of a continuous pattern to a new minimum and maximum value.
 */
@StrudelDsl
val StrudelPattern.range by dslPatternExtension { p, args -> applyRange(p, args) }

@StrudelDsl
val String.range by dslStringExtension { p, args -> applyRange(p, args) }

// -- Continuous patterns ----------------------------------------------------------------------------------------------

/** Empty pattern that does not produce any events */
@StrudelDsl
val silence by dslObject { EmptyPattern }

/** Empty pattern that does not produce any events */
@StrudelDsl
val rest by dslObject { silence }

/**
 * Continuous pattern that produces a constant from a callback
 *
 * The first parameter must be a function (Double) -> Double
 */
@StrudelDsl
val signal by dslFunction { args ->
    @Suppress("UNCHECKED_CAST")
    val value = args.getOrNull(0) as? Function1<Double, Any?> ?: { 0.0 }
    ContinuousPattern { t -> value(t)?.asDoubleOrNull() ?: 0.0 }
}

/** Continuous pattern that produces a constant value */
@StrudelDsl
val steady by dslFunction { args ->
    val value = args.getOrNull(0)?.asDoubleOrNull() ?: 0.0
    signal { _ -> value }
}

/** Continuous pattern that represents the current time (in cycles) */
@StrudelDsl
val time by dslObject { signal { t -> t } }

/** Sine oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val sine by dslObject { signal { t -> (sin(t * 2.0 * PI) + 1.0) / 2.0 } }

/** Sine oscillator: -1 to 1, period of 1 cycle */
@StrudelDsl
val sine2 by dslObject { sine.range(-1.0, 1.0) }

/** Cosine oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val cosine by dslObject { signal { t -> (sin(t * 2.0 * PI + PI / 2.0) + 1.0) / 2.0 } }

/** Cosine oscillator: -1 to 1, period of 1 cycle */
@StrudelDsl
val cosine2 by dslObject { cosine.range(-1.0, 1.0) }

/** Sawtooth oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val saw by dslObject { signal { t -> t % 1.0 } }

/** Sawtooth oscillator: -1 to 1, period of 1 cycle */
@StrudelDsl
val saw2 by dslObject { saw.range(-1.0, 1.0) }

/** Inverse Sawtooth oscillator: 1 to 0, period of 1 cycle */
@StrudelDsl
val isaw by dslObject { signal { t -> 1.0 - (t % 1.0) } }

/** Inverse Sawtooth oscillator: 1 to -1, period of 1 cycle */
@StrudelDsl
val isaw2 by dslObject { isaw.range(-1.0, 1.0) }

/** Triangle oscillator: 0 to 1 to 0, period of 1 cycle */
@StrudelDsl
val tri by dslObject {
    signal { t ->
        val phase = t % 1.0
        if (phase < 0.5) phase * 2.0 else 2.0 - (phase * 2.0)
    }
}

/** Triangle oscillator: -1 to 1 to -1, period of 1 cycle */
@StrudelDsl
val tri2 by dslObject { tri.range(-1.0, 1.0) }

/** Inverse Triangle oscillator: 1 to 0 to 1, period of 1 cycle */
@StrudelDsl
val itri by dslObject {
    signal { t ->
        val phase = t % 1.0
        if (phase < 0.5) 1.0 - phase * 2.0 else phase * 2.0 - 1.0
    }
}

/** Inverse Triangle oscillator: 1 to -1 to 1, period of 1 cycle */
@StrudelDsl
val itri2 by dslObject { itri.range(-1.0, 1.0) }

/** Square oscillator: 0 or 1, period of 1 cycle */
@StrudelDsl
val square by dslObject { signal { t -> if (t % 1.0 < 0.5) 0.0 else 1.0 } }

/** Square oscillator: -1 or 1, period of 1 cycle */
@StrudelDsl
val square2 by dslObject { square.range(-1.0, 1.0) }

/** Generates a continuous pattern of Perlin noise producing values between 0 and 1 */
@StrudelDsl
val perlin by dslObject {
    val engines = mutableMapOf<Int, PerlinNoise>()

    ContinuousPattern { from, _, ctx ->
        val random = ctx.getSeededRandom("perlin")
        val engine = engines.getOrPut(random.nextInt()) {
            PerlinNoise(random)
        }

        (engine.noise(from) + 1.0) / 2.0
    }
}

/**
 * Generates a continuous pattern of Berlin noise producing values between 0 and 1
 *
 * Conceived by Jame Coyne and Jade Rowland as a joke but turned out to be surprisingly cool and useful,
 * like perlin noise but with sawtooth waves,
 */
@StrudelDsl
val berlin by dslObject {
    val engines = mutableMapOf<Int, BerlinNoise>()

    ContinuousPattern { from, _, ctx ->
        val random = ctx.getSeededRandom("Berlin")
        val engine = engines.getOrPut(random.nextInt()) {
            BerlinNoise(random)
        }

        engine.noise(from)
    }
}
