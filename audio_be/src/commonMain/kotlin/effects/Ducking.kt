/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.isFiniteInline
import io.peekandpoke.klang.audio_be.utils.timeConstantCoeff
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Sidechain ducking/compression processor.
 *
 * Reduces the volume of a signal based on the level of a sidechain (trigger) signal.
 * Commonly used for kick-bass ducking or pumping effects.
 *
 * Uses linked stereo detection: the sidechain level is derived from max(abs(L), abs(R))
 * and a single shared envelope applies the same gain reduction to both channels.
 * This preserves the stereo image during ducking.
 *
 * **Topology** (intentionally NOT shared with [Compressor]): linear-domain peak detector
 * with **instantaneous attack** (gain drops the moment the sidechain spikes) and
 * **exponential release** (one-pole IIR with coefficient `1 - exp(-1/(t·sr))`). The
 * `Compressor` class in this directory uses a dB-domain feedback envelope follower with
 * a soft-knee gain curve — different math, different artefacts. Don't try to share helpers.
 *
 * @param sampleRate Audio sample rate in Hz
 * @param attackSeconds Recovery/release time — how fast the volume returns to normal after
 *   the sidechain trigger stops (in seconds). Note: the duck-down is instantaneous;
 *   this parameter only controls the return smoothing. Named "attack" for strudel compatibility.
 * @param depth Amount of ducking (0.0 = no effect, 1.0 = full silence)
 */
class Ducking(
    private val sampleRate: Int,
    attackSeconds: Double = 0.1,
    depth: Double = 0.5,
) {
    /** Internal release coefficient (named externally as "attack" for Strudel compat). */
    private var releaseCoeff: Double = calculateCoefficient(attackSeconds)

    /** Current gain (1.0 = no duck, 0.0 = full duck). */
    private var currentGain: Double = 1.0

    /**
     * The stored [depth], what its getter reads. A property with default accessors, so a reader loads the field: on
     * Kotlin/JS [depth]'s custom getter is a call, and a double a call returns is a heap number wherever V8 does not
     * inline it (`audio/ref/performance.md`). The loops here and `KatalystDuckEffect.configure` read this.
     */
    internal var depthValue: Double = depth.coerceIn(0.0, 1.0)
        private set

    /** The stored [attackSeconds], what its getter reads; a plain property for the same reason as [depthValue]. */
    internal var attackValue: Double = guardOr(value = attackSeconds, fallback = 0.1)
        private set

    /**
     * Ducking depth (0.0 = no ducking, 1.0 = full silence). Setter silently ignores non-finite values. The public door
     * and the specs' seam: a per-block caller writes [knobArgs] and calls [applyKnobArgs] (engine follow-up 45).
     */
    var depth: Double
        get() = depthValue
        set(value) {
            if (!value.isFinite()) return
            depthValue = value.coerceIn(0.0, 1.0)
        }

    /**
     * Attack time in seconds (return to normal). Setter silently ignores non-finite values. The public door and the
     * specs' seam: a per-block caller writes [knobArgs] and calls [applyKnobArgs] (engine follow-up 45).
     */
    var attackSeconds: Double
        get() = attackValue
        set(value) {
            if (!value.isFinite()) return
            attackValue = value
            releaseCoeff = calculateCoefficient(value)
        }

    /**
     * The two knobs of one [applyKnobArgs], written by the caller before the call, at [KNOB_ATTACK] and [KNOB_DEPTH]:
     * the raw values, as a caller would hand them to the two setters. A non-finite one keeps the stored value, as
     * the setters do.
     *
     * **A holder, on purpose** (engine follow-up 45, 2026-10-19): the orbit duck writes its knobs on every block a
     * modulated depth or attack moves. Through the two setters each knob crossed a call V8 does not always inline, so
     * each was a heap number (`audio/ref/performance.md`, the rule on a double handed to a function that is not
     * inlined): under a mixed profile `KatalystDuckEffect.configure` booked 32 B per block, two heap numbers, in
     * every process measured. The compressor's [Compressor.knobArgs] is the same remedy (engine follow-up 40). An
     * ARRAY, not double fields: an element access in optimized code never allocates (engine follow-up 7). No test
     * can pin the box; this KDoc is the guard.
     */
    internal val knobArgs = DoubleArray(KNOB_ARGS)

    /**
     * Applies [knobArgs] as the two setters would, the attack first, and stores the same numbers bit for bit: each
     * knob guarded as its setter guards it (a non-finite one keeps the stored value), the depth coerced to `[0, 1]`
     * as [depth]'s setter does, the release coefficient computed from a written attack. Exact in either order,
     * because neither setter reads what the other writes. Guarded by "applyKnobArgs leaves the instance where the two
     * setters leave it, bit for bit" (`DuckingSpec`).
     */
    internal fun applyKnobArgs() {
        val attack = knobArgs[KNOB_ATTACK]
        val depthArg = knobArgs[KNOB_DEPTH]

        // `isFiniteInline`, not the stdlib `isFinite()` (a call on Kotlin/JS): the same answer for every input.
        if (attack.isFiniteInline()) {
            attackValue = attack
            updateReleaseCoeff()
        }

        // The setter's `coerceIn(0.0, 1.0)`, written out (the stdlib's body), so no double crosses a call.
        if (depthArg.isFiniteInline()) {
            depthValue = if (depthArg < 0.0) 0.0 else if (depthArg > 1.0) 1.0 else depthArg
        }
    }

    /** [releaseCoeff] from the stored attack. Takes no double and returns none, so nothing crosses a call. */
    private fun updateReleaseCoeff() {
        releaseCoeff = calculateCoefficient(attackValue)
    }

    /**
     * Per-sample envelope step. Computes target gain from the sidechain level and [depthNow]
     * (the stored depth for the settled path, a ramped value while the depth glides), then applies
     * instant-attack / exponential-release smoothing on `currentGain`.
     * Inlined into [processStereo] and [processStereoGliding] so each loop body stays specialized.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun stepGain(sidechainLevel: Double, depthNow: Double): Double {
        // No silence gate — the formula naturally yields gain=1.0 when sidechainLevel is 0,
        // so the previous `if (level > 0.01)` short-circuit was redundant and introduced a
        // tiny snap discontinuity at the threshold crossing.
        val targetGain = 1.0 - depthNow * min(1.0, sidechainLevel * SIDECHAIN_SENSITIVITY)
        currentGain = if (targetGain < currentGain) {
            targetGain
        } else {
            currentGain + releaseCoeff * (targetGain - currentGain)
        }
        return currentGain
    }

    /**
     * Processes stereo audio with sidechain ducking (linked stereo detection).
     *
     * Detects sidechain level from max(abs(L), abs(R)) and applies the same gain
     * to both channels, preserving the stereo image.
     */
    fun processStereo(
        inputL: AudioBuffer,
        inputR: AudioBuffer,
        sidechainL: AudioBuffer,
        sidechainR: AudioBuffer,
        blockSize: Int,
    ) {
        for (i in 0 until blockSize) {
            val gain = stepGain(sidechainLevel = max(abs(sidechainL[i]), abs(sidechainR[i])), depthNow = depthValue)
            inputL[i] = inputL[i] * gain
            inputR[i] = inputR[i] * gain
        }
    }

    /**
     * The same block, with [depth] and the stage's WEIGHT moving across it (Katalyst 5c-9,
     * `audio/ref/knob-glide.md`). The envelope runs on the whole block either way, so it stays
     * continuous across every edge; what changes is only what the block multiplies by.
     *
     * - **depth** ramps linearly from [depthFrom] to [depthTo]. The target gain is LINEAR in depth
     *   (`1 - depth * x`), so that is the axis the law is linear in, and the detector is
     *   memoryless downwards (the attack is instantaneous), which makes a depth step a level step:
     *   measured -16.8 dB above 8 kHz against the signal at worst on a rise, at the floor on a
     *   fall (the release smooths that direction on its own).
     * - **weight** ramps linearly from [weightFrom] to [weightTo], 1 being the full reduction and
     *   0 exactly 0 dB of it: `gain = 1 + w * (g - 1)`. That is what switching OFF rides down, so
     *   the stage reaches identity BEFORE it lets go of the envelope, and what switching ON rides
     *   up. No dry copy is needed, because ducking is one multiply: blending a scaled mix with the
     *   dry one is the same number as scaling the reduction.
     *
     * Both ramps are written from the block's END, so the last sample carries [depthTo] and
     * [weightTo] bit for bit.
     */
    fun processStereoGliding(
        inputL: AudioBuffer,
        inputR: AudioBuffer,
        sidechainL: AudioBuffer,
        sidechainR: AudioBuffer,
        blockSize: Int,
        depthFrom: Double,
        depthTo: Double,
        weightFrom: Double,
        weightTo: Double,
    ) {
        // A block of zero frames never enters the loop; the reciprocal keeps the division out of
        // the divide-by-zero corner anyway.
        val inv = if (blockSize > 0) 1.0 / blockSize else 0.0
        val depthStep = (depthTo - depthFrom) * inv
        val weightStep = (weightTo - weightFrom) * inv
        val last = blockSize - 1

        for (i in 0 until blockSize) {
            val back = (last - i).toDouble()
            val gain = stepGain(sidechainLevel = max(abs(sidechainL[i]), abs(sidechainR[i])), depthNow = depthTo - depthStep * back)
            val weighted = 1.0 + (weightTo - weightStep * back) * (gain - 1.0)

            inputL[i] = inputL[i] * weighted
            inputR[i] = inputR[i] * weighted
        }
    }

    /**
     * Calculate time constant coefficient for the release envelope. Higher values =
     * faster response. Non-finite or sub-millisecond inputs fall back to a 1ms minimum
     * (matches `Compressor.updateCoefficients`).
     *
     * Kotlin `inline`, with `isFiniteInline` (the stdlib `isFinite()`'s exact twin), so on the per-block path of a
     * moving attack ([applyKnobArgs]) no double crosses a call (engine follow-up 45): as a plain function it took the
     * attack and returned the coefficient, and with V8's inlining budget squeezed to 250 the duck's `configure` (with
     * [applyKnobArgs] inlined) still booked one heap number per block for it; inline, none (`E-kat-dyn` 130.7 to 114.6
     * B per block there).
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun calculateCoefficient(timeSeconds: Double): Double {
        val safeTime = if (timeSeconds.isFiniteInline()) timeSeconds else 0.001
        val clampedTime = max(0.001, safeTime)
        return timeConstantCoeff(timeSeconds = clampedTime, sampleRate = sampleRate.toDouble())
    }

    /** Reset internal state */
    fun reset() {
        currentGain = 1.0
    }

    companion object {
        /**
         * Sidechain sensitivity / saturation knee. With `* 2.0`, the ducker reaches full
         * saturation when the sidechain peak is 0.5 — half the dynamic range, which is
         * musical for percussion sidechains (kick drums peak around -3 to -6 dBFS).
         * Lower the constant for a less aggressive duck; raise it for a harsher pump.
         */
        private const val SIDECHAIN_SENSITIVITY: Double = 2.0

        /** The slots of [knobArgs]: the attack and the depth. */
        internal const val KNOB_ATTACK: Int = 0
        internal const val KNOB_DEPTH: Int = 1

        /** How many slots [knobArgs] has. */
        private const val KNOB_ARGS: Int = 2

        /** Returns [value] when finite; otherwise [fallback]. Mirrors `Compressor.guardOr`. */
        private fun guardOr(value: Double, fallback: Double): Double =
            if (value.isFinite()) value else fallback
    }
}
