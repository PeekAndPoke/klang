/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import io.peekandpoke.klang.sprudel.pattern.ControlPattern
import io.peekandpoke.ultra.datetime.Kronos
import kotlin.math.PI
import kotlin.math.sin

// -- time -------------------------------------------------------------------------------------------------------------

private val timeBase: SprudelPattern by lazy { applySignal { t -> t } }

/**
 * Continuous ramp — current cycle time, increases linearly by `1.0` per cycle.
 *
 * At cycle `n`, the value equals `n + fraction_of_cycle`. Useful as a time-dependent modulation
 * source or for creating patterns that evolve over many cycles. Use [range] or [rangex] to map
 * it to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * time.range(100.0, 255.0).freq().segment(128)  // linearly rising frequency per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * time.rangex(100.0, 2000.0).freq().segment(128)  // exponentially rising frequency over time
 * ```
 * @category continuous
 * @tags time, continuous, linear, ramp
 */
@KlangScript.Constant
val time: SprudelPattern = timeBase

// -- cps() ------------------------------------------------------------------------------------------------------------

/**
 * Returns the cycles per second at which playback is currently running as a continuous pattern.
 *
 * ```KlangScript(Playable)
 * sound("sd").delay(wet = 0.25, time = pure(1/8).div(cps), feedback = 0.5)  // Delay time based in CPS
 * ```
 *
 * @category continuous
 * @tags cps, tempo, playback speed, continuous
 */
@KlangScript.Constant
val cps: SprudelPattern = ContinuousPattern { _, _, ctx -> ctx.getCps() }

// -- rpm() ------------------------------------------------------------------------------------------------------------

/**
 * Returns the current revolutions per minute as a continuous pattern (RPM = CPS × 60).
 *
 * ```KlangScript(Playable)
 * sound("sd").delay(wet = 0.25, time = pure(1).div(rpm), feedback = 0.5)  // Delay time based in RPM
 * ```
 *
 * @category continuous
 * @tags rpm, tempo, revolutions per minute, continuous
 */
@KlangScript.Constant
val rpm: SprudelPattern = ContinuousPattern { _, _, ctx -> ctx.getCps() * 60.0 }

// -- bpm() ------------------------------------------------------------------------------------------------------------

/**
 * Returns the current beats per minute as a continuous pattern (assuming 4/4 time, 4 beats per cycle).
 *
 * ```KlangScript(Playable)
 * sound("sd").delay(wet = 0.15, time = pure(60).div(bpm), feedback = 0.33)  // Delay time based in BPM
 * ```
 *
 * @category continuous
 * @tags bpm, tempo, beats per minute, continuous
 */
@KlangScript.Constant
val bpm: SprudelPattern = ContinuousPattern { _, _, ctx -> ctx.getCps() * 60.0 * DEFAULT_BEATS_PER_CYCLE }

// -- beats() ----------------------------------------------------------------------------------------------------------

/** Four beats to a cycle: the 4/4 that [bpm] assumes and [beats] takes when no base is given. */
private const val DEFAULT_BEATS_PER_CYCLE = 4.0

/** The length of one beat in seconds at the tempo of this query, with [base] beats to a cycle. */
private fun QueryContext.secondsPerBeat(base: Double): Double = 1.0 / (getCps() * base)

private fun applyBeats(args: List<SprudelDslArg<Any?>>, base: Number): SprudelPattern {
    val nArg = args.firstOrNull() ?: return silence
    val staticN = (nArg.value as? Number)?.toDouble()
    // Coerced, not asserted: a base of 0 or less, or a non-finite one, counts in fours
    val beatsPerCycle = base.toDouble().takeIf { it > 0.0 && it.isFinite() } ?: DEFAULT_BEATS_PER_CYCLE

    // A number is a signal, like cps
    if (staticN != null) {
        return ContinuousPattern { _, _, ctx -> staticN * ctx.secondsPerBeat(beatsPerCycle) }
    }

    // A pattern keeps its own rhythm; each of its values is read against the tempo of the query
    return ControlPattern(
        source = nArg.toPattern(),
        control = ContinuousPattern { _, _, ctx -> ctx.secondsPerBeat(beatsPerCycle) },
        mapper = { it },
        combiner = { nData, beatData ->
            val n = nData.value?.asDouble
            val secondsPerBeat = beatData.value?.asDouble

            if (n != null && secondsPerBeat != null) {
                nData.copy(value = (n * secondsPerBeat).asVoiceValue())
            } else {
                nData
            }
        },
    )
}

/**
 * The length of `n` beats in seconds, at the tempo that is playing right now.
 *
 * The sound doors take a time in seconds (`delay.time`, the envelope stages). `beats` turns a musical length
 * into those seconds and follows every tempo change while playing, like [cps]. The pattern doors that shift
 * time (`late`, `early`) take cycles, not seconds, so `beats` is not for them. `base` is how many
 * beats make a cycle, 4 unless you say otherwise, the same 4/4 that [bpm] assumes: at cps 0.5 (120 bpm)
 * `beats(1)` is 0.5 s and `beats(0.5)` is 0.25 s, an eighth note. In a waltz, `beats(1, 3)` is a third of
 * a cycle.
 *
 * It is a duration, not a rate: a door that takes Hz (the tremolo's `sync`) needs `pure(1).div(beats(n))` for one
 * wobble every `n` beats.
 *
 * A number gives a signal. A pattern keeps its own rhythm, and each of its values becomes seconds.
 * Write fractions as decimals in a string: in mini-notation `"1/8"` means slow down by 8, not one eighth.
 *
 * ```KlangScript(Playable)
 * s("sd ~ ~ ~").delay(0.4, beats(0.75), 0.5)             // a dotted-eighth echo at any tempo
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd ~ ~ ~").delay(0.4, beats("<0.5 0.75>"), 0.5)     // eighth, then dotted eighth, every other cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd sd").delay(0.4, beats(1, 3), 0.5)             // three beats to the cycle: echo one beat later
 * ```
 *
 * @param n How many beats; a number or a pattern.
 * @param base How many beats make one cycle, default 4. A value of 0 or less counts in fours.
 * @return The length of `n` beats in seconds, following the tempo.
 * @category continuous
 * @tags beats, tempo, bpm, cps, seconds, delay time, continuous
 */
@KlangScript.Function
fun beats(n: PatternLike, base: Number = 4, callInfo: CallInfo? = null): SprudelPattern =
    applyBeats(listOf(n).asSprudelDslArgs(callInfo), base)

// -- Time of Day Functions --------------------------------------------------------------------------------------------

/**
 * Helper function to get the current time of day as a fraction (0.0 to 1.0)
 * 0.0 = midnight, 0.5 = noon, 1.0 = next midnight
 */
private fun getTimeOfDayFraction(kronos: Kronos): Double {
    val localTime = kronos.localDateTimeNow()
    val hour = localTime.hour.toDouble()
    val minute = localTime.minute.toDouble()
    val second = localTime.second.toDouble()
    return (hour + minute / 60.0 + second / 3600.0) / 24.0
}

/**
 * Returns the current time of day as a linear value: `0.0` (midnight) → `0.5` (noon) → `1.0` (midnight).
 *
 * ```KlangScript(Playable)
 * s("hh*8").gain(timeOfDay)         // gain rises through the day
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(timeOfDay.range(200, 4000))  // filter opens as the day progresses
 * ```
 *
 * @category continuous
 * @tags timeOfDay, time, clock, continuous
 */
@KlangScript.Constant
val timeOfDay: SprudelPattern = ContinuousPattern { _, _, ctx ->
    getTimeOfDayFraction(ctx.getKronos())
}

/**
 * Returns the current time of day as a sine wave: `0.0` (midnight) → `1.0` (noon) → `0.0` (midnight).
 *
 * ```KlangScript(Playable)
 * s("hh*8").gain(sinOfDay)          // gain peaks at noon
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").vibrato(sinOfDay.range(0, 8))  // vibrato rises and falls with the sun
 * ```
 *
 * @category continuous
 * @tags sinOfDay, time, sine, clock, continuous
 */
@KlangScript.Constant
val sinOfDay: SprudelPattern = ContinuousPattern { _, _, ctx ->
    val t = getTimeOfDayFraction(ctx.getKronos())
    sin(t * PI)
}

/**
 * Returns the current time of day as a bipolar sine wave: `-1.0` (midnight) → `1.0` (noon) → `-1.0` (midnight).
 *
 * ```KlangScript(Playable)
 * note("c4").transpose(sinOfDay2.range(-12, 12))  // transpose oscillates through the day
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").gain(sinOfDay2.range(0, 1))  // bipolar to unipolar conversion
 * ```
 *
 * @category continuous
 * @tags sinOfDay2, time, sine, bipolar, clock, continuous
 */
@KlangScript.Constant
val sinOfDay2: SprudelPattern = ContinuousPattern { _, _, ctx ->
    val t = getTimeOfDayFraction(ctx.getKronos())
    sin(t * PI) * 2.0 - 1.0
}

/**
 * Returns the current time of night (inverse of [timeOfDay]): `1.0` (midnight) → `0.0` (noon) → `1.0` (midnight).
 *
 * ```KlangScript(Playable)
 * s("hh*8").gain(timeOfNight)       // gain is highest at midnight
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(timeOfNight.range(200, 4000))  // filter opens at night
 * ```
 *
 * @category continuous
 * @tags timeOfNight, time, clock, night, continuous
 */
@KlangScript.Constant
val timeOfNight: SprudelPattern = ContinuousPattern { _, _, ctx ->
    1.0 - getTimeOfDayFraction(ctx.getKronos())
}

/**
 * Returns the current time of night as a sine wave: `1.0` (midnight) → `0.0` (noon) → `1.0` (midnight).
 *
 * ```KlangScript(Playable)
 * s("hh*8").gain(sinOfNight)        // gain peaks at midnight
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").vibrato(sinOfNight.range(0, 8))  // vibrato is strongest at night
 * ```
 *
 * @category continuous
 * @tags sinOfNight, time, sine, night, clock, continuous
 */
@KlangScript.Constant
val sinOfNight: SprudelPattern = ContinuousPattern { _, _, ctx ->
    val t = getTimeOfDayFraction(ctx.getKronos())
    1.0 - sin(t * PI)
}

/**
 * Returns the current time of night as a bipolar sine wave:
 * `1.0` (midnight) → `-1.0` (noon) → `1.0` (midnight).
 *
 * ```KlangScript(Playable)
 * note("c4").transpose(sinOfNight2.range(-12, 12))  // transpose inverts through the day
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").gain(sinOfNight2.range(0, 1))  // bipolar night signal to unipolar gain
 * ```
 *
 * @category continuous
 * @tags sinOfNight2, time, sine, bipolar, night, clock, continuous
 */
@KlangScript.Constant
val sinOfNight2: SprudelPattern = ContinuousPattern { _, _, ctx ->
    val t = getTimeOfDayFraction(ctx.getKronos())
    1.0 - sin(t * PI) * 2.0
}
