@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel._mapRangeContext
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

/**
 * Maps a pattern in the range 0..1 to -1..1.
 */
/**
 * Maps a pattern in the range 0..1 to -1..1.
 */
@StrudelDsl
val StrudelPattern.toBipolar by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    val contextAware = p._mapRangeContext(
        transformMin = { (it + 1.0) / 2.0 },
        transformMax = { (it + 1.0) / 2.0 }
    )
    applyUnaryOp(contextAware) { v ->
        val d = v.asDouble
        if (d != null) (d * 2.0 - 1.0).asVoiceValue() else v
    }
}

@StrudelDsl
val String.toBipolar by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p.toBipolar() }

/**
 * Maps a pattern in the range -1..1 to 0..1.
 * Useful for converting LFOs like sine2/tri2 to probabilities or selectors.
 */
@StrudelDsl
val StrudelPattern.fromBipolar by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    val contextAware = p._mapRangeContext(
        transformMin = { it * 2.0 - 1.0 },
        transformMax = { it * 2.0 - 1.0 }
    )
    applyUnaryOp(contextAware) { v ->
        val d = v.asDouble
        if (d != null) ((d + 1.0) / 2.0).asVoiceValue() else v
    }
}

@StrudelDsl
val String.fromBipolar by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p.fromBipolar() }

// -- Continuous patterns settings -------------------------------------------------------------------------------------

private fun applyRange(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): ContextModifierPattern {
    val min = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0
    val granularity = args.getOrNull(2)?.value?.asDoubleOrNull()?.toRational() ?: Rational.ONE

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
val StrudelPattern.range by dslPatternExtension { p, args, /* callInfo */ _ -> applyRange(p, args) }

@StrudelDsl
val String.range by dslStringExtension { p, args, /* callInfo */ _ -> applyRange(p, args) }

// -- rangex -----------------------------------------------------------------------------------------------------------

private fun applyRangex(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val min = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0
    val granularity = args.getOrNull(2)?.value?.asDoubleOrNull()?.toRational() ?: Rational.ONE

    // Apply logarithmic transformation to min/max for exponential scaling
    val logMin = kotlin.math.ln(kotlin.math.max(min, 0.0001)) // Avoid log(0)
    val logMax = kotlin.math.ln(kotlin.math.max(max, 0.0001))

    val ranged = pattern.withContext {
        set(ContinuousPattern.minKey, logMin)
        set(ContinuousPattern.maxKey, logMax)
        set(ContinuousPattern.granularityKey, granularity)
    }

    // Apply exponential function to the result
    return applyUnaryOp(ranged) { v ->
        v.asRational?.exp()?.asVoiceValue() ?: v
    }
}

/**
 * Scales unipolar values (0-1) to a min-max range using an exponential curve.
 * Useful for frequency ranges and other parameters where exponential scaling feels more natural.
 */
@StrudelDsl
val StrudelPattern.rangex by dslPatternExtension { p, args, /* callInfo */ _ -> applyRangex(p, args) }

@StrudelDsl
val String.rangex by dslStringExtension { p, args, /* callInfo */ _ -> applyRangex(p, args) }

// -- range2 -----------------------------------------------------------------------------------------------------------

private fun applyRange2(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Convert bipolar (-1 to 1) to unipolar (0 to 1), then apply range
    return applyRange(pattern.fromBipolar(), args)
}

/**
 * Scales bipolar values (-1 to 1) to a min-max range.
 * Useful for LFOs and bipolar continuous patterns.
 */
@StrudelDsl
val StrudelPattern.range2 by dslPatternExtension { p, args, /* callInfo */ _ -> applyRange2(p, args) }

@StrudelDsl
val String.range2 by dslStringExtension { p, args, /* callInfo */ _ -> applyRange2(p, args) }

// -- Continuous patterns ----------------------------------------------------------------------------------------------

/** Empty pattern that does not produce any events */
@StrudelDsl
val silence by dslObject { EmptyPattern }

/** Empty pattern that does not produce any events */
@StrudelDsl
val rest by dslObject { silence }

/** Empty pattern that does not produce any events */
@StrudelDsl
val nothing by dslObject { silence }

/**
 * Continuous pattern that produces a constant from a callback
 *
 * The first parameter must be a function (Double) -> Double
 */
@StrudelDsl
val signal by dslFunction { args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val value = args.getOrNull(0)?.value as? Function1<Double, Any?> ?: { 0.0 }

    ContinuousPattern { t -> value(t)?.asDoubleOrNull() ?: 0.0 }
}

/** Continuous pattern that produces a constant value */
@StrudelDsl
val steady by dslFunction { args, /* callInfo */ _ ->
    val value = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
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
