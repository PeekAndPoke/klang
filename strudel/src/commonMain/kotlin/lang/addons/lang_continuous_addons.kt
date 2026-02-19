package io.peekandpoke.klang.strudel.lang.addons

import de.peekandpoke.ultra.common.datetime.Kronos
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

/** Returns the cycles per second at which the playback is currently running */
@StrudelDsl
val cps by dslObject { ContinuousPattern { _, _, ctx -> ctx.getCps() } }

// -- bpm() ------------------------------------------------------------------------------------------------------------

/** Returns the beats per minute at which the playback is currently running (assuming 4/4 time, 4 beats per cycle) */
@StrudelDsl
val bpm by dslObject { ContinuousPattern { _, _, ctx -> ctx.getCps() * 240.0 } }

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

/** Returns the current time of day as a linear value: 0.0 (midnight) -> 0.5 (noon) -> 1.0 (midnight) */
@StrudelDsl
val timeOfDay by dslObject {
    ContinuousPattern { _, _, ctx ->
        getTimeOfDayFraction(ctx.getKronos())
    }
}

/** Returns the current time of day as a sine wave: 0.0 (midnight) -> 1.0 (noon) -> 0.0 (midnight) */
@StrudelDsl
val sinOfDay by dslObject {
    ContinuousPattern { _, _, ctx ->
        val t = getTimeOfDayFraction(ctx.getKronos())
        sin(t * PI)
    }
}

/** Returns the current time of day as a bipolar sine wave: -1.0 (midnight) -> 1.0 (noon) -> -1.0 (midnight) */
@StrudelDsl
val sinOfDay2 by dslObject {
    ContinuousPattern { _, _, ctx ->
        val t = getTimeOfDayFraction(ctx.getKronos())
        sin(t * PI) * 2.0 - 1.0
    }
}

/** Returns the current time of night (inverse of timeOfDay): 1.0 (midnight) -> 0.0 (noon) -> 1.0 (midnight) */
@StrudelDsl
val timeOfNight by dslObject {
    ContinuousPattern { _, _, ctx ->
        1.0 - getTimeOfDayFraction(ctx.getKronos())
    }
}

/** Returns the current time of night as a sine wave: 1.0 (midnight) -> 0.0 (noon) -> 1.0 (midnight) */
@StrudelDsl
val sinOfNight by dslObject {
    ContinuousPattern { _, _, ctx ->
        val t = getTimeOfDayFraction(ctx.getKronos())
        1.0 - sin(t * PI)
    }
}

/** Returns the current time of night as a bipolar sine wave: 1.0 (midnight) -> -1.0 (noon) -> 1.0 (midnight) */
@StrudelDsl
val sinOfNight2 by dslObject {
    ContinuousPattern { _, _, ctx ->
        val t = getTimeOfDayFraction(ctx.getKronos())
        1.0 - sin(t * PI) * 2.0
    }
}


