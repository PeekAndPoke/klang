@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
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

// -- Helpers ----------------------------------------------------------------------------------------------------------

private fun StrudelPattern.mapRangeContext(
    transformMin: (Double) -> Double,
    transformMax: (Double) -> Double,
): StrudelPattern {
    return object : StrudelPattern {
        override val weight: Double get() = this@mapRangeContext.weight

        override val steps: Rational = Rational.ONE

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
            val min = ctx.getOrNull(ContinuousPattern.minKey)
            val max = ctx.getOrNull(ContinuousPattern.maxKey)

            val newCtx = if (min != null && max != null) {
                ctx.update {
                    set(ContinuousPattern.minKey, transformMin(min))
                    set(ContinuousPattern.maxKey, transformMax(max))
                }
            } else {
                ctx
            }
            return this@mapRangeContext.queryArcContextual(from, to, newCtx)
        }
    }
}

/**
 * Maps a pattern in the range 0..1 to -1..1.
 */
/**
 * Maps a pattern in the range 0..1 to -1..1.
 */
@StrudelDsl
val StrudelPattern.toBipolar by dslPatternExtension { p, _ ->
    val contextAware = p.mapRangeContext(
        transformMin = { (it + 1.0) / 2.0 },
        transformMax = { (it + 1.0) / 2.0 }
    )
    applyUnaryOp(contextAware) { v ->
        val d = v.asDouble
        if (d != null) (d * 2.0 - 1.0).asVoiceValue() else v
    }
}

@StrudelDsl
val String.toBipolar by dslStringExtension { p, _ -> p.toBipolar() }

/**
 * Maps a pattern in the range -1..1 to 0..1.
 * Useful for converting LFOs like sine2/tri2 to probabilities or selectors.
 */
@StrudelDsl
val StrudelPattern.fromBipolar by dslPatternExtension { p, _ ->
    val contextAware = p.mapRangeContext(
        transformMin = { it * 2.0 - 1.0 },
        transformMax = { it * 2.0 - 1.0 }
    )
    applyUnaryOp(contextAware) { v ->
        val d = v.asDouble
        if (d != null) ((d + 1.0) / 2.0).asVoiceValue() else v
    }
}

@StrudelDsl
val String.fromBipolar by dslStringExtension { p, _ -> p.fromBipolar() }

// -- Continuous patterns settings -------------------------------------------------------------------------------------

private fun applyRange(pattern: StrudelPattern, args: List<Any?>): ContextModifierPattern {
    val min = args.getOrNull(0)?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.asDoubleOrNull() ?: 1.0
    val granularity = args.getOrNull(2)?.asDoubleOrNull()?.toRational() ?: Rational.ONE

    return pattern.withContext {
        set(ContinuousPattern.minKey, min)
        set(ContinuousPattern.maxKey, max)
        set(ContinuousPattern.granularityKey, granularity)
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
val sine2 by dslObject { sine.toBipolar() }

/** Cosine oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val cosine by dslObject { signal { t -> (sin(t * 2.0 * PI + PI / 2.0) + 1.0) / 2.0 } }

/** Cosine oscillator: -1 to 1, period of 1 cycle */
@StrudelDsl
val cosine2 by dslObject { cosine.toBipolar() }

/** Sawtooth oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val saw by dslObject { signal { t -> t % 1.0 } }

/** Sawtooth oscillator: -1 to 1, period of 1 cycle */
@StrudelDsl
val saw2 by dslObject { saw.toBipolar() }

/** Inverse Sawtooth oscillator: 1 to 0, period of 1 cycle */
@StrudelDsl
val isaw by dslObject { signal { t -> 1.0 - (t % 1.0) } }

/** Inverse Sawtooth oscillator: 1 to -1, period of 1 cycle */
@StrudelDsl
val isaw2 by dslObject { isaw.toBipolar() }

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
val tri2 by dslObject { tri.toBipolar() }

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
val itri2 by dslObject { itri.toBipolar() }

/** Square oscillator: 0 or 1, period of 1 cycle */
@StrudelDsl
val square by dslObject { signal { t -> if (t % 1.0 < 0.5) 0.0 else 1.0 } }

/** Square oscillator: -1 or 1, period of 1 cycle */
@StrudelDsl
val square2 by dslObject { square.toBipolar() }

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

@StrudelDsl
val perlin2 by dslObject { perlin.toBipolar() }

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

@StrudelDsl
val berlin2 by dslObject { berlin.toBipolar() }
