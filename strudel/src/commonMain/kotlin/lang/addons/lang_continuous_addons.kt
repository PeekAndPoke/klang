@file:Suppress("ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang.addons

import de.peekandpoke.ultra.common.datetime.Kronos
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.StrudelDsl
import io.peekandpoke.klang.strudel.lang.dslObject
import io.peekandpoke.klang.strudel.pattern.ContinuousPattern
import kotlin.math.PI
import kotlin.math.sin

/**
 * ADDONS: Continuous functions that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangContinuousAddonsInit = false

// -- cps() ------------------------------------------------------------------------------------------------------------

internal val _cps by dslObject { ContinuousPattern { _, _, ctx -> ctx.getCps() } }

/**
 * Returns the cycles per second at which playback is currently running as a continuous pattern.
 *
 * ```KlangScript
 * gain(cps)             // modulate gain by playback speed
 * ```
 *
 * ```KlangScript
 * note("c4").lpf(cps.range(200, 2000))  // LPF tracks tempo
 * ```
 *
 * @category continuous
 * @tags cps, tempo, playback speed, continuous, addon
 */
@StrudelDsl
val cps: StrudelPattern get() = _cps

// -- bpm() ------------------------------------------------------------------------------------------------------------

internal val _bpm by dslObject { ContinuousPattern { _, _, ctx -> ctx.getCps() * 240.0 } }

/**
 * Returns the current beats per minute as a continuous pattern (assuming 4/4 time, 4 beats per cycle).
 *
 * ```KlangScript
 * gain(bpm.range(0, 1))   // scale gain by BPM
 * ```
 *
 * ```KlangScript
 * note("c4").vibrato(bpm.range(2, 8))  // vibrato speed follows BPM
 * ```
 *
 * @category continuous
 * @tags bpm, tempo, beats per minute, continuous, addon
 */
@StrudelDsl
val bpm: StrudelPattern get() = _bpm

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

internal val _timeOfDay by dslObject {
    ContinuousPattern { _, _, ctx ->
        getTimeOfDayFraction(ctx.getKronos())
    }
}

/**
 * Returns the current time of day as a linear value: `0.0` (midnight) → `0.5` (noon) → `1.0` (midnight).
 *
 * ```KlangScript
 * gain(timeOfDay)                   // gain rises through the day
 * ```
 *
 * ```KlangScript
 * note("c4").lpf(timeOfDay.range(200, 4000))  // filter opens as the day progresses
 * ```
 *
 * @category continuous
 * @tags timeOfDay, time, clock, continuous, addon
 */
@StrudelDsl
val timeOfDay: StrudelPattern get() = _timeOfDay

internal val _sinOfDay by dslObject {
    ContinuousPattern { _, _, ctx ->
        val t = getTimeOfDayFraction(ctx.getKronos())
        sin(t * PI)
    }
}

/**
 * Returns the current time of day as a sine wave: `0.0` (midnight) → `1.0` (noon) → `0.0` (midnight).
 *
 * ```KlangScript
 * gain(sinOfDay)                    // gain peaks at noon
 * ```
 *
 * ```KlangScript
 * note("c4").vibrato(sinOfDay.range(0, 8))  // vibrato rises and falls with the sun
 * ```
 *
 * @category continuous
 * @tags sinOfDay, time, sine, clock, continuous, addon
 */
@StrudelDsl
val sinOfDay: StrudelPattern get() = _sinOfDay

internal val _sinOfDay2 by dslObject {
    ContinuousPattern { _, _, ctx ->
        val t = getTimeOfDayFraction(ctx.getKronos())
        sin(t * PI) * 2.0 - 1.0
    }
}

/**
 * Returns the current time of day as a bipolar sine wave: `-1.0` (midnight) → `1.0` (noon) → `-1.0` (midnight).
 *
 * ```KlangScript
 * note("c4").transpose(sinOfDay2.range(-12, 12))  // transpose oscillates through the day
 * ```
 *
 * ```KlangScript
 * gain(sinOfDay2.range(0, 1))       // bipolar to unipolar conversion
 * ```
 *
 * @category continuous
 * @tags sinOfDay2, time, sine, bipolar, clock, continuous, addon
 */
@StrudelDsl
val sinOfDay2: StrudelPattern get() = _sinOfDay2

internal val _timeOfNight by dslObject {
    ContinuousPattern { _, _, ctx ->
        1.0 - getTimeOfDayFraction(ctx.getKronos())
    }
}

/**
 * Returns the current time of night (inverse of [timeOfDay]): `1.0` (midnight) → `0.0` (noon) → `1.0` (midnight).
 *
 * ```KlangScript
 * gain(timeOfNight)                 // gain is highest at midnight
 * ```
 *
 * ```KlangScript
 * note("c4").lpf(timeOfNight.range(200, 4000))  // filter opens at night
 * ```
 *
 * @category continuous
 * @tags timeOfNight, time, clock, night, continuous, addon
 */
@StrudelDsl
val timeOfNight: StrudelPattern get() = _timeOfNight

internal val _sinOfNight by dslObject {
    ContinuousPattern { _, _, ctx ->
        val t = getTimeOfDayFraction(ctx.getKronos())
        1.0 - sin(t * PI)
    }
}

/**
 * Returns the current time of night as a sine wave: `1.0` (midnight) → `0.0` (noon) → `1.0` (midnight).
 *
 * ```KlangScript
 * gain(sinOfNight)                  // gain peaks at midnight
 * ```
 *
 * ```KlangScript
 * note("c4").vibrato(sinOfNight.range(0, 8))  // vibrato is strongest at night
 * ```
 *
 * @category continuous
 * @tags sinOfNight, time, sine, night, clock, continuous, addon
 */
@StrudelDsl
val sinOfNight: StrudelPattern get() = _sinOfNight

internal val _sinOfNight2 by dslObject {
    ContinuousPattern { _, _, ctx ->
        val t = getTimeOfDayFraction(ctx.getKronos())
        1.0 - sin(t * PI) * 2.0
    }
}

/**
 * Returns the current time of night as a bipolar sine wave:
 * `1.0` (midnight) → `-1.0` (noon) → `1.0` (midnight).
 *
 * ```KlangScript
 * note("c4").transpose(sinOfNight2.range(-12, 12))  // transpose inverts through the day
 * ```
 *
 * ```KlangScript
 * gain(sinOfNight2.range(0, 1))     // bipolar night signal to unipolar gain
 * ```
 *
 * @category continuous
 * @tags sinOfNight2, time, sine, bipolar, night, clock, continuous, addon
 */
@StrudelDsl
val sinOfNight2: StrudelPattern get() = _sinOfNight2