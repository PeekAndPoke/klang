/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.utils.easeInOutCubic
import kotlin.math.abs

/**
 * The level of a playback's background voices while another source is soloed: it moves to each new target over
 * [durationSec] on the in-out cubic ([easeInOutCubic]), stepped once per block by the scheduler.
 *
 * - A target that differs from the last one by more than 1e-6 starts a new transition from the current value;
 *   a smaller change is no change.
 * - The transition ends exactly ON its target (progress at or past 1), so a fully muted background plays at
 *   exactly 0.0.
 * - A duration of 0 or less jumps to the target at once.
 *
 * The law of the `common` module's former `ValueRamp`, its one caller, with the curve inlined (audit item B4.17):
 * same operations in the same order, bit for bit. Nothing allocates after construction.
 */
internal class SoloRamp(
    initialValue: Double,
    private val durationSec: Double,
) {
    /** The value of the last [step]; [initialValue] before the first. */
    var current: Double = initialValue
        private set

    private var startValue: Double = initialValue
    private var targetValue: Double = initialValue

    /** How far the transition has come, 0 to 1; 1 when it has ended. */
    private var progress: Double = 1.0

    /** Advances the value toward [target] by [dt] seconds and returns it. */
    fun step(target: Double, dt: Double): Double {
        if (abs(target - targetValue) > 1e-6) {
            startValue = current
            targetValue = target
            progress = 0.0
        }

        if (progress >= 1.0) {
            current = targetValue

            return current
        }

        if (durationSec <= 0.0) {
            progress = 1.0
            current = targetValue

            return current
        }

        progress += dt / durationSec

        if (progress >= 1.0) {
            progress = 1.0
            current = targetValue
        } else {
            current = startValue + (targetValue - startValue) * easeInOutCubic(progress)
        }

        return current
    }
}
