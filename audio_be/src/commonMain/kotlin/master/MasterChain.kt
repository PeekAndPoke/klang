/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.master.MasterChain.Companion.buildReverb
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl

/**
 * One stage of a built [MasterChain] — a thin shell over the **shared** DSP in
 * `audio_be/effects/`, processing the master bus in place.
 *
 * Deliberately the same shape as `KatalystEffect` (the orbit-bus counterpart): the effect
 * implementations are shared, only the host differs (orbit bus vs master bus).
 */
internal fun interface MasterFx {
    fun process(bus: StereoBuffer, frames: Int)
}

/**
 * A built master chain: the ordered [MasterFx] shells for one [MasterDsl], plus the state they own.
 *
 * Instances are **stateful** (limiter envelope, reverb/delay buffers), so a chain belongs to exactly
 * one bus and is never shared. Building allocates (delay rings, Freeverb buffers), so chains are
 * built **once per registered master** and cached — never on the swap path, which runs on the audio
 * thread. See [MasterBus.register].
 *
 * Because chains are cached and re-adopted, they must be [reset] before coming back into service:
 * a chain holds its buffers frozen while inactive and would otherwise dump a previous section's
 * reverb tail into the bus on return. Same reason `Cylinder.tryDeactivate` resets its bus effects.
 */
internal class MasterChain private constructor(
    /** Array, not List — iterated once per block in the render callback; no iterator allocation. */
    private val stages: Array<MasterFx>,
    /**
     * The time-based units, kept for [reset] / [hasActiveTail]; empty for a chain without tails.
     * Module-internal rather than private so specs can assert what actually reached the DSP.
     */
    val reverbs: Array<Reverb>,
    val delays: Array<DelayLine>,
    private val limiters: Array<Compressor>,
) {
    /** True when this chain does anything at all — an empty chain is a pure pass-through. */
    val isActive: Boolean = stages.isNotEmpty()

    /** True when this chain owns time-based state that can keep ringing after its input goes quiet. */
    val hasTail: Boolean = reverbs.isNotEmpty() || delays.isNotEmpty()

    fun process(bus: StereoBuffer, frames: Int) {
        for (i in stages.indices) {
            stages[i].process(bus, frames)
        }
    }

    /**
     * True while any reverb/delay in this chain still holds audible energy.
     *
     * Asks the DSP about its **internal** state rather than watching the chain's output — a delay's
     * output is silent between echoes, so an output-based test would declare a 250 ms delay finished
     * ~85 ms after the last note and cut every remaining echo. This is exactly how the orbit path
     * decides (`Cylinder.tryDeactivate` → `DelayLine.hasTail()` / `Reverb.hasTail()`).
     */
    fun hasActiveTail(): Boolean {
        for (i in reverbs.indices) {
            if (reverbs[i].hasTail()) {
                return true
            }
        }
        for (i in delays.indices) {
            if (delays[i].hasTail()) {
                return true
            }
        }
        return false
    }

    /** Clears every stateful unit, so a re-adopted chain cannot replay an earlier section. */
    fun reset() {
        for (i in reverbs.indices) {
            reverbs[i].reset()
        }
        for (i in delays.indices) {
            delays[i].reset()
        }
        for (i in limiters.indices) {
            limiters[i].reset()
        }
    }

    companion object {
        /** Head-room added to a delay ring on top of the declared time. */
        private const val DELAY_RING_MARGIN_SECONDS = 0.05

        /**
         * Upper bound on a master delay ring.
         *
         * Matches the per-orbit delay (`Cylinder` builds its `DelayLine` with 10 s) so `delay` means
         * the same thing on both buses — a lower cap here would silently re-time a long echo. Rings
         * are sized to the *declared* time, so raising the cap costs nothing for short delays.
         */
        private const val MAX_DELAY_SECONDS = 10.0

        /** At or below this send level the effect is inaudible and is dropped from the chain. */
        private const val MIN_WET = 0.0001

        /** Matches the Katalyst short-circuits (`roomSize < 0.01` / `delayTimeSeconds < 0.01`). */
        private const val MIN_TIME_FX = 0.01

        /**
         * Builds the shells for [dsl] in declaration order.
         *
         * **Allocates** (Freeverb buffers, delay rings) — call this at registration time, never while
         * applying a swap.
         */
        fun build(dsl: MasterDsl, sampleRate: Int, blockFrames: Int): MasterChain {
            val stages = mutableListOf<MasterFx>()
            val reverbs = mutableListOf<Reverb>()
            val delays = mutableListOf<DelayLine>()
            val limiters = mutableListOf<Compressor>()

            for (stage in dsl.stages) {
                when (stage) {
                    is MasterStageDsl.Gain -> buildGain(stage)?.let { stages.add(it) }

                    is MasterStageDsl.Limiter -> {
                        val limiter = buildLimiter(stage, sampleRate)
                        limiters.add(limiter)
                        stages.add(MasterFx { bus, frames -> limiter.process(bus.left, bus.right, frames) })
                    }

                    is MasterStageDsl.Reverb -> buildReverb(stage, sampleRate, blockFrames)?.let { built ->
                        reverbs.add(built.reverb)
                        stages.add(built.fx)
                    }

                    is MasterStageDsl.Delay -> buildDelay(stage, sampleRate, blockFrames)?.let { built ->
                        delays.add(built.delayLine)
                        stages.add(built.fx)
                    }
                }
            }

            return MasterChain(
                stages = stages.toTypedArray(),
                reverbs = reverbs.toTypedArray(),
                delays = delays.toTypedArray(),
                limiters = limiters.toTypedArray(),
            )
        }

        private class BuiltReverb(val reverb: Reverb, val fx: MasterFx)
        private class BuiltDelay(val delayLine: DelayLine, val fx: MasterFx)

        /**
         * Finite-guards a user-supplied parameter, falling back to [fallback].
         *
         * NOT a magnitude clamp — the engine is raw, so any finite value passes through untouched.
         * Non-finite input, however, is written into feedback state that has no reset path on the
         * master bus (the playback would stay NaN for good) and can even reach a `coerceIn` with an
         * inverted range, which **throws on the audio thread** and kills the worklet. The shared DSP
         * setters apply the same guard; these are the parameters `MasterChain` owns directly.
         */
        private fun finite(value: Double, fallback: Double): Double =
            if (value.isFinite()) value else fallback

        private fun buildGain(stage: MasterStageDsl.Gain): MasterFx? {
            val gain = finite(stage.gain, 1.0)

            if (gain == 1.0) {
                return null
            }

            return MasterFx { bus, frames ->
                val left = bus.left
                val right = bus.right

                for (i in 0 until frames) {
                    left[i] *= gain
                    right[i] *= gain
                }
            }
        }

        private fun buildLimiter(stage: MasterStageDsl.Limiter, sampleRate: Int): Compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = finite(stage.thresholdDb, -1.0),
            ratio = finite(stage.ratio, 20.0),
            kneeDb = finite(stage.kneeDb, 2.0),
            attackSeconds = finite(stage.attackSeconds, 0.001),
            releaseSeconds = finite(stage.releaseSeconds, 0.1),
        )

        /**
         * Reverb as an *insert*: the shared [Reverb] is a send/return effect (it reads a send buffer
         * and adds its wet output to a target), so the shell owns the send buffer, fills it with the
         * bus scaled by `wet`, and lets the unchanged DSP mix the tail back in.
         *
         * Skipped entirely when inaudible — mirrors `KatalystReverbEffect`'s `roomSize < 0.01`
         * short-circuit, so an "off" master reverb costs nothing (Freeverb is the heaviest single
         * DSP unit in the engine).
         */
        private fun buildReverb(stage: MasterStageDsl.Reverb, sampleRate: Int, blockFrames: Int): BuiltReverb? {
            val wet = finite(stage.wet, 0.0)
            // The authored value is on the sprudel ~0..10 scale; ONE shared conversion for both
            // buses. The fallback must be the *authored* default, not the normalized one — a 0.5
            // here would normalize to 0.05, fall under MIN_TIME_FX and silently delete the stage.
            val roomSize = Reverb.normalizeRoomSize(
                finite(stage.roomSize, MasterStageDsl.Reverb.DEFAULT_ROOM_SIZE)
            )

            // `roomFade` overrides `roomSize` in the DSP, so an explicit fade means "render", at any
            // value — 0.0 is the shortest tail (~0.7 s), not "off". Gating on roomSize alone dropped
            // stages that would have been audible. Same question `KatalystReverbEffect` asks.
            val hasFade = stage.roomFade?.isFinite() == true
            if (wet <= MIN_WET || (!hasFade && roomSize < MIN_TIME_FX)) {
                return null
            }

            // roomFade/roomLp are assigned through their setters, which drop non-finite values;
            // do NOT move them into the constructor. roomFade overrides roomSize for the comb
            // feedback, so it carries the same 0..1 bound (past unity the combs run away to NaN —
            // see `Reverb.normalizeRoomSize`); damp is bounded because past 2.5 the comb one-pole
            // coefficient exceeds 1 and the filter diverges.
            val reverb = Reverb(sampleRate = sampleRate).also {
                it.roomSize = roomSize
                it.damp = finite(stage.damp, 0.5).coerceIn(0.0, 1.0)
                it.roomFade = stage.roomFade?.coerceIn(0.0, 1.0)
                it.roomLp = stage.roomLp
            }
            val send = StereoBuffer(blockFrames)

            return BuiltReverb(
                reverb = reverb,
                fx = MasterFx { bus, frames ->
                    fillSend(send, bus, frames, wet)
                    reverb.process(send, bus, frames)
                },
            )
        }

        /**
         * Delay as an *insert* — same send-copy trick as [buildReverb], same short-circuit as
         * `KatalystDelayEffect` (`delayTimeSeconds < 0.01`). Without the skip, a "zero" time would
         * be coerced up to the DSP's ~5-sample minimum and ring as a metallic comb.
         *
         * The ring is sized to the declared time (fixed per chain), not to a blanket maximum — a
         * 0.25 s master delay costs ~190 KB instead of ~3 MB.
         */
        private fun buildDelay(stage: MasterStageDsl.Delay, sampleRate: Int, blockFrames: Int): BuiltDelay? {
            val wet = finite(stage.wet, 0.0)
            val timeSeconds = finite(stage.timeSeconds, 0.25)

            if (wet <= MIN_WET || timeSeconds < MIN_TIME_FX) {
                return null
            }

            val time = timeSeconds.coerceAtMost(MAX_DELAY_SECONDS)
            val delayLine = DelayLine(
                maxDelaySeconds = time + DELAY_RING_MARGIN_SECONDS,
                sampleRate = sampleRate,
                delayTimeSeconds = time,
                // A property initializer bypasses the class's own non-finite setter guard.
                feedback = finite(stage.feedback, 0.0),
            ).also {
                it.feedbackCap = finite(stage.cap, 1.0)
            }
            val send = StereoBuffer(blockFrames)

            return BuiltDelay(
                delayLine = delayLine,
                fx = MasterFx { bus, frames ->
                    fillSend(send, bus, frames, wet)
                    delayLine.process(send, bus, frames)
                },
            )
        }

        /** Copies [frames] of [bus] into [send], scaled by [wet]. */
        private fun fillSend(send: StereoBuffer, bus: StereoBuffer, frames: Int, wet: Double) {
            val sendL = send.left
            val sendR = send.right
            val busL = bus.left
            val busR = bus.right

            for (i in 0 until frames) {
                sendL[i] = busL[i] * wet
                sendR[i] = busR[i] * wet
            }
        }
    }
}
