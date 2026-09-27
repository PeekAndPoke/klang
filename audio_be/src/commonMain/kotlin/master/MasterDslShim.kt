/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RATIO
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET

/**
 * **Scaffolding: dies in phase 3 step 12 C5**, when `.master()` takes a `KatalystDsl` and the Master
 * DSL retires. Until then the wire still carries a [MasterDsl], and this is the ONE place it becomes
 * the [KatalystDsl] the output runs ([MasterBus] builds a `KatalystChain` from it).
 *
 * Each stage goes to its Katalyst twin, every knob a `Constant`: `gain` to `Gain`, `reverb` to
 * `Reverb`, `delay` to `Delay`, and `limiter` to a `Compressor` stage with its five numbers and its
 * `lookahead` (step 12 decisions (a) and (d): one DSP, one wire word).
 *
 * **It keeps the retired `MasterChain.build`'s build-time rules, so the output sounds exactly as it
 * did** (C3 is identity):
 *  - a non-finite knob takes the fallback that build used (the shared constant, or unity for the
 *    gain; a non-finite lowpass is absent). A Katalyst slot reads non-finite as UNSET instead, which
 *    for a send stage is OFF, so without this a NaN master reverb would vanish where it used to ring
 *    at the default size;
 *  - a stage that cannot be heard is left out: a unity gain, and a reverb or delay whose wet is at
 *    most [MIN_WET] or whose (normalized) size or time is under [MIN_TIME_FX]. That keeps a chain of
 *    such stages EMPTY, so the engine keeps its fast path (plan risk R2).
 *
 * Both rules go with this file in C5; the plan's section 7.4 names what then changes (items 3 and 4:
 * an inaudible wet in (0, 0.0001] runs, a non-finite size or time is off), none of it reachable from
 * a finite song. The lookahead is passed through raw: `KatalystCompressorEffect` coerces it with the
 * same `Compressor.coerceLookaheadSeconds` the old build called.
 */
internal object MasterDslShim {

    /** At or below this send level the old master dropped the stage as inaudible. */
    const val MIN_WET = 0.0001

    /**
     * Under this normalized size or this delay time (seconds) the old master dropped the stage; the
     * same number as `KatalystReverbEffect.MIN_ACTIVE_SIZE` and `KatalystDelayEffect.MIN_ACTIVE_DELAY_SECONDS`.
     */
    const val MIN_TIME_FX = 0.01

    /** The [KatalystDsl] the output runs for [dsl], stage for stage (see the object KDoc). */
    fun toKatalyst(dsl: MasterDsl): KatalystDsl {
        val stages = ArrayList<KatalystStageDsl>(dsl.stages.size)

        for (stage in dsl.stages) {
            val twin = when (stage) {
                is MasterStageDsl.Gain -> gain(stage)
                is MasterStageDsl.Limiter -> limiter(stage)
                is MasterStageDsl.Reverb -> reverb(stage)
                is MasterStageDsl.Delay -> delay(stage)
            }

            if (twin != null) {
                stages.add(twin)
            }
        }

        return KatalystDsl(stages)
    }

    private fun gain(stage: MasterStageDsl.Gain): KatalystStageDsl? {
        val gain = finite(stage.gain, 1.0)

        if (gain == 1.0) {
            return null
        }

        return KatalystStageDsl.Gain(gain = c(gain))
    }

    private fun limiter(stage: MasterStageDsl.Limiter): KatalystStageDsl = KatalystStageDsl.Compressor(
        threshold = c(finite(stage.threshold, LIMITER_THRESHOLD_DB)),
        ratio = c(finite(stage.ratio, LIMITER_RATIO)),
        knee = c(finite(stage.knee, LIMITER_KNEE_DB)),
        attack = c(finite(stage.attackSeconds, AUTHORED_LIMITER_ATTACK_SECONDS)),
        release = c(finite(stage.releaseSeconds, LIMITER_RELEASE_SECONDS)),
        lookahead = stage.lookaheadSeconds,
    )

    private fun reverb(stage: MasterStageDsl.Reverb): KatalystStageDsl? {
        val wet = finite(stage.wet, REVERB_WET)
        // The fallback is the AUTHORED default: the stage carries the 0..10 scale, and the writer
        // normalizes it (the drop test below normalizes the same way, `Reverb.normalizeSize`).
        val size = finite(stage.size, REVERB_SIZE)

        if (wet <= MIN_WET || Reverb.normalizeSize(size) < MIN_TIME_FX) {
            return null
        }

        return KatalystStageDsl.Reverb(
            wet = c(wet),
            size = c(size),
            lowpass = stage.lowpass?.takeIf { it.isFinite() }?.let { c(it) },
        )
    }

    private fun delay(stage: MasterStageDsl.Delay): KatalystStageDsl? {
        val wet = finite(stage.wet, DELAY_WET)
        val time = finite(stage.time, DELAY_TIME_SECONDS)

        if (wet <= MIN_WET || time < MIN_TIME_FX) {
            return null
        }

        return KatalystStageDsl.Delay(
            wet = c(wet),
            time = c(time),
            feedback = c(finite(stage.feedback, DELAY_FEEDBACK)),
            cap = c(finite(stage.cap, DELAY_CAP)),
        )
    }

    private fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

    /** NOT a magnitude clamp (the engine is raw): only a non-finite value takes [fallback]. */
    private fun finite(value: Double, fallback: Double): Double = if (value.isFinite()) value else fallback
}
