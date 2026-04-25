@file:Suppress("ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import io.peekandpoke.ultra.datetime.Kronos
import kotlin.math.PI
import kotlin.math.sin
// -- cps() ------------------------------------------------------------------------------------------------------------

/**
 * Returns the cycles per second at which playback is currently running as a continuous pattern.
 *
 * ```KlangScript(Playable)
 * sound("sd").delay(0.25).delaytime(pure(1/8).div(cps)).delayfeedback(0.5)  // Dalay time based in CPS
 * ```
 *
 * @category continuous
 * @tags cps, tempo, playback speed, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
val cps: SprudelPattern = ContinuousPattern { _, _, ctx -> ctx.getCps() }

// -- rpm() ------------------------------------------------------------------------------------------------------------

/**
 * Returns the current revolutions per minute as a continuous pattern (RPM = CPS × 60).
 *
 * ```KlangScript(Playable)
 * sound("sd").delay(0.25).delaytime(pure(1).div(rpm)).delayfeedback(0.5)  // Delay time based in RPM
 * ```
 *
 * @category continuous
 * @tags rpm, tempo, revolutions per minute, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
val rpm: SprudelPattern = ContinuousPattern { _, _, ctx -> ctx.getCps() * 60.0 }

// -- bpm() ------------------------------------------------------------------------------------------------------------

/**
 * Returns the current beats per minute as a continuous pattern (assuming 4/4 time, 4 beats per cycle).
 *
 * ```KlangScript(Playable)
 * sound("sd").delay(0.15).delaytime(pure(60).div(bpm)).delayfeedback(0.33)  // Dalay time based in BPM
 * ```
 *
 * @category continuous
 * @tags bpm, tempo, beats per minute, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
val bpm: SprudelPattern = ContinuousPattern { _, _, ctx -> ctx.getCps() * 240.0 }

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
 * gain(timeOfDay)                   // gain rises through the day
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(timeOfDay.range(200, 4000))  // filter opens as the day progresses
 * ```
 *
 * @category continuous
 * @tags timeOfDay, time, clock, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
val timeOfDay: SprudelPattern = ContinuousPattern { _, _, ctx ->
    getTimeOfDayFraction(ctx.getKronos())
}

/**
 * Returns the current time of day as a sine wave: `0.0` (midnight) → `1.0` (noon) → `0.0` (midnight).
 *
 * ```KlangScript(Playable)
 * gain(sinOfDay)                    // gain peaks at noon
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").vibrato(sinOfDay.range(0, 8))  // vibrato rises and falls with the sun
 * ```
 *
 * @category continuous
 * @tags sinOfDay, time, sine, clock, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
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
 * gain(sinOfDay2.range(0, 1))       // bipolar to unipolar conversion
 * ```
 *
 * @category continuous
 * @tags sinOfDay2, time, sine, bipolar, clock, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
val sinOfDay2: SprudelPattern = ContinuousPattern { _, _, ctx ->
    val t = getTimeOfDayFraction(ctx.getKronos())
    sin(t * PI) * 2.0 - 1.0
}

/**
 * Returns the current time of night (inverse of [timeOfDay]): `1.0` (midnight) → `0.0` (noon) → `1.0` (midnight).
 *
 * ```KlangScript(Playable)
 * gain(timeOfNight)                 // gain is highest at midnight
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(timeOfNight.range(200, 4000))  // filter opens at night
 * ```
 *
 * @category continuous
 * @tags timeOfNight, time, clock, night, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
val timeOfNight: SprudelPattern = ContinuousPattern { _, _, ctx ->
    1.0 - getTimeOfDayFraction(ctx.getKronos())
}

/**
 * Returns the current time of night as a sine wave: `1.0` (midnight) → `0.0` (noon) → `1.0` (midnight).
 *
 * ```KlangScript(Playable)
 * gain(sinOfNight)                  // gain peaks at midnight
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").vibrato(sinOfNight.range(0, 8))  // vibrato is strongest at night
 * ```
 *
 * @category continuous
 * @tags sinOfNight, time, sine, night, clock, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
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
 * gain(sinOfNight2.range(0, 1))     // bipolar night signal to unipolar gain
 * ```
 *
 * @category continuous
 * @tags sinOfNight2, time, sine, bipolar, night, clock, continuous, addon
 */
@SprudelDsl
@KlangScript.Property
val sinOfNight2: SprudelPattern = ContinuousPattern { _, _, ctx ->
    val t = getTimeOfDayFraction(ctx.getKronos())
    1.0 - sin(t * PI) * 2.0
}
