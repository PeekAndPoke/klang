package io.peekandpoke.klang.strudel.math

import de.peekandpoke.ultra.common.maths.Ease
import kotlin.math.abs

/**
 * Smoothly transitions a value towards a target using an easing function over a fixed duration.
 *
 * @param initialValue The starting value.
 * @param duration The time in seconds it takes to complete a transition.
 * @param ease The easing function to use.
 */
class ValueRamp(
    initialValue: Double,
    private val duration: Double = 0.1,
    private val ease: Ease.Fn = Ease.InOut.quad,
) {
    var current: Double = initialValue
        private set

    private var startValue: Double = initialValue
    private var targetValue: Double = initialValue
    private var progress: Double = 1.0 // 0.0 .. 1.0

    fun reset(value: Double) {
        current = value
        startValue = value
        targetValue = value
        progress = 1.0
    }

    /**
     * Advances the current value towards the [target] by [dt] seconds.
     * Returns the new current value.
     */
    fun step(target: Double, dt: Double): Double {
        // If target changed, start a new transition from current position
        if (abs(target - targetValue) > 1e-6) {
            startValue = current
            targetValue = target
            progress = 0.0
        }

        if (progress >= 1.0) {
            current = targetValue
            return current
        }

        if (duration <= 0.0) {
            progress = 1.0
            current = targetValue
            return current
        }

        progress += dt / duration

        if (progress >= 1.0) {
            progress = 1.0
            current = targetValue
        } else {
            val t = ease(progress)
            current = startValue + (targetValue - startValue) * t
        }

        return current
    }
}
