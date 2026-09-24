/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.effects.TailCeiling
import io.peekandpoke.klang.audio_be.master.MasterChain.Companion.buildReverb
import io.peekandpoke.klang.audio_be.warehouse.ResourceWarehouse
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import kotlin.math.ceil

/**
 * One stage of a built [MasterChain] — a thin shell over the **shared** DSP in
 * `audio_be/effects/`, processing the master bus in place.
 *
 * The orbit-bus counterpart is `KatalystEffect`. Both chains have the same lifecycle (a clean
 * slate for re-entry into service, a tail question, a return of rented units); what differs is
 * WHERE it lives. Here it is chain-level: [MasterChain] owns typed arrays of the units it built
 * ([MasterChain.reverbs], [MasterChain.delays], [MasterChain.limiters], the ceilings) and walks
 * them in [MasterChain.reset], [MasterChain.hasActiveTail] and [MasterChain.releaseUnits], so a
 * stage shell needs nothing but [process]. On the orbit bus it is per stage, because the stages
 * own heterogeneous lifecycles (a delay grows its ring, a resonator swaps its bank) that no
 * chain-level array can express. What the two DO share is the rule this shell exists for: the
 * effect implementations are the same DSP classes, only the host differs.
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
    val limiters: Array<Compressor>,
    /** One content ceiling per time-based stage, fed inside that stage's [MasterFx]. */
    private val tails: Array<TailCeiling>,
    /**
     * Delay and reverb stages the warehouse REFUSED a ring or unit for (resource-warehouse steps
     * 2e/2d). Each is built without its effect — the chain stays usable, the echo or room is
     * simply absent — and counted here so the reporting step can surface it like a per-orbit
     * `deniedRents`.
     */
    val deniedRents: Int,
) {
    /**
     * Set by [releaseUnits]: the chain's rings and networks belong to the shelf now, and any
     * further [process] / [reset] would write into units another orbit may already have rented —
     * cross-talk the double-return guard cannot see (review round 3). Both become no-ops.
     */
    private var released = false

    /** True when this chain does anything at all — an empty chain is a pure pass-through. */
    val isActive: Boolean = stages.isNotEmpty()

    /** True when this chain owns time-based state that can keep ringing after its input goes quiet. */
    val hasTail: Boolean = reverbs.isNotEmpty() || delays.isNotEmpty()

    fun process(bus: StereoBuffer, frames: Int) {
        if (released) {
            return
        }
        for (i in stages.indices) {
            stages[i].process(bus, frames)
        }
    }

    /**
     * True while any reverb/delay in this chain still holds audible energy — a compare, not a scan.
     *
     * Each time-based stage answers from a ceiling on its content (see [TailCeiling]) fed by ITS
     * OWN input, the send as it reaches that stage: a delay ahead of a reverb keeps feeding the
     * reverb its echoes after the bus went silent, so the reverb's ceiling decays only once the
     * echoes end — one chain-wide measure at the bus was wrong by exactly that. Not
     * decided from the chain's output either: a delay's output is silent between echoes, so an
     * output-based test would declare a 250 ms delay finished ~85 ms after the last note and cut
     * every remaining echo. Same closed form as the orbit path (`KatalystDelayEffect` /
     * `KatalystReverbEffect`); the whole-ring `DelayLine.hasTail` scan it replaced had lost its
     * bound when the ring ceiling went (2e).
     */
    fun hasActiveTail(): Boolean {
        if (released) {
            return false
        }
        for (i in tails.indices) {
            if (tails[i].hasTail) {
                return true
            }
        }
        return false
    }

    /**
     * Hands every delay ring back to [rings] and every reverb network back to [reverbs] — the
     * return path of resource-warehouse steps 2e/2d. Call exactly once, when the chain leaves the
     * cache ([MasterBus.evictIfNeeded]); the chain must not process afterwards (its units now
     * belong to whoever rents them next).
     */
    fun releaseUnits(rings: SizedBuffers, reverbs: ReverbUnits) {
        if (released) {
            return
        }
        released = true
        for (i in delays.indices) {
            rings.giveBack(delays[i].ring)
        }
        for (i in this.reverbs.indices) {
            reverbs.giveBack(this.reverbs[i])
        }
    }

    /** Clears every stateful unit, so a re-adopted chain cannot replay an earlier section. */
    fun reset() {
        if (released) {
            return
        }
        for (i in tails.indices) {
            tails[i].reset()
        }
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
        /**
         * Ceiling on limiter lookahead. `MasterBus.register` builds chains on the audio thread, so
         * an unbounded value would allocate there — 50 ms is ~77 KB stereo, 10 s would be ~7.7 MB.
         * A resource bound, not a taste clamp.
         */
        private const val MAX_LOOKAHEAD_SECONDS = 0.05

        /** At or below this send level the effect is inaudible and is dropped from the chain. */
        private const val MIN_WET = 0.0001

        /** Matches the Katalyst off-thresholds (`KatalystReverbEffect.MIN_ACTIVE_SIZE` and
         *  `KatalystDelayEffect.MIN_ACTIVE_DELAY_SECONDS`) — one contract, kept in prose sync
         *  because importing a katalyst constant here would invert the layering. */
        private const val MIN_TIME_FX = 0.01

        /**
         * Builds the shells for [dsl] in declaration order.
         *
         * **Allocates** (Freeverb buffers; delay rings only when the shelf has none idle) — call
         * this at registration time, never while applying a swap. Delay rings come from [rings],
         * the backend's shelf, class-sized exactly as the per-orbit delay sizes its own (step 2e):
         * `delay(20)` is the same 20 s echo on both buses, and a ring an evicted chain returns is
         * what the next master delay of that class gets without allocating.
         */
        fun build(
            dsl: MasterDsl,
            sampleRate: Int,
            blockFrames: Int,
            rings: SizedBuffers = SizedBuffers.forRings(sampleRate),
            reverbs: ReverbUnits = ReverbUnits(sampleRate),
        ): MasterChain {
            val stages = mutableListOf<MasterFx>()
            val reverbUnits = mutableListOf<Reverb>()
            val delays = mutableListOf<DelayLine>()
            val limiters = mutableListOf<Compressor>()
            val tails = mutableListOf<TailCeiling>()
            var deniedRents = 0

            for (stage in dsl.stages) {
                when (stage) {
                    is MasterStageDsl.Gain -> buildGain(stage)?.let { stages.add(it) }

                    is MasterStageDsl.Limiter -> {
                        val limiter = buildLimiter(stage, sampleRate)
                        limiters.add(limiter)
                        stages.add(MasterFx { bus, frames -> limiter.process(bus.left, bus.right, frames) })
                    }

                    is MasterStageDsl.Reverb -> when (val built = buildReverb(stage, blockFrames, reverbs)) {
                        null -> {} // inaudible: no stage
                        BuiltReverb.Denied -> deniedRents++
                        is BuiltReverb.Ready -> {
                            reverbUnits.add(built.reverb)
                            tails.add(built.tail)
                            stages.add(built.fx)
                        }
                    }

                    is MasterStageDsl.Delay -> when (val built = buildDelay(stage, sampleRate, blockFrames, rings)) {
                        null -> {} // inaudible, or below the off-threshold: no stage
                        BuiltDelay.Denied -> deniedRents++
                        is BuiltDelay.Ready -> {
                            delays.add(built.delayLine)
                            tails.add(built.tail)
                            stages.add(built.fx)
                        }
                    }
                }
            }

            return MasterChain(
                stages = stages.toTypedArray(),
                reverbs = reverbUnits.toTypedArray(),
                delays = delays.toTypedArray(),
                limiters = limiters.toTypedArray(),
                tails = tails.toTypedArray(),
                deniedRents = deniedRents,
            )
        }

        private sealed class BuiltReverb {
            class Ready(val reverb: Reverb, val fx: MasterFx, val tail: TailCeiling) : BuiltReverb()

            /** The shelf had no unit and could not allocate one: the stage is skipped, and counted. */
            object Denied : BuiltReverb()
        }

        private sealed class BuiltDelay {
            class Ready(val delayLine: DelayLine, val fx: MasterFx, val tail: TailCeiling) : BuiltDelay()

            /** The shelf had no ring and could not allocate one: the stage is skipped, and counted. */
            object Denied : BuiltDelay()
        }

        /**
         * Finite-guards a user-supplied parameter, falling back to [fallback]. For the send effects
         * the fallback is the shared default (`constants/SendEffectDefaults.kt`): a non-finite value
         * reads as unset, the same meaning `VoiceFactory` gives it on an orbit.
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
            thresholdDb = finite(stage.threshold, -1.0),
            ratio = finite(stage.ratio, 20.0),
            kneeDb = finite(stage.knee, 2.0),
            attackSeconds = finite(stage.attackSeconds, 0.001),
            releaseSeconds = finite(stage.releaseSeconds, 0.1),
            // Bounded, because this is the one stage parameter that SIZES AN ARRAY: a negative
            // value throws NegativeArraySizeException on the audio thread and takes the worklet
            // with it, and Infinity asks for Int.MAX_VALUE doubles. Not a tone clamp — the same
            // protection every sibling gets from finite(), plus a memory ceiling like the delay's.
            lookaheadSeconds = finite(stage.lookaheadSeconds, 0.0)
                .coerceIn(0.0, MAX_LOOKAHEAD_SECONDS),
        )

        /**
         * Reverb as an *insert*: the shared [Reverb] is a send/return effect (it reads a send buffer
         * and adds its wet output to a target), so the shell owns the send buffer, fills it with the
         * bus scaled by `wet`, and lets the unchanged DSP mix the tail back in.
         *
         * Skipped entirely when inaudible — the same off-test as
         * `KatalystReverbEffect.MIN_ACTIVE_SIZE` (there an off-config starts a drain of the
         * live tail; here the stage is decided at build time, so no tail exists to drain), so an
         * "off" master reverb costs nothing (Freeverb is the heaviest single DSP unit in the
         * engine).
         */
        private fun buildReverb(stage: MasterStageDsl.Reverb, blockFrames: Int, units: ReverbUnits): BuiltReverb? {
            val wet = finite(stage.wet, REVERB_WET)
            // The authored value is on the ~0..10 scale; ONE shared conversion for both buses. The
            // fallback must be the *authored* default, not the normalized one — a 0.5 here would
            // normalize to 0.05 and silently build a tiny room instead of the default one.
            val size = Reverb.normalizeSize(finite(stage.size, REVERB_SIZE))

            if (wet <= MIN_WET || size < MIN_TIME_FX) {
                return null
            }

            // lowpass is assigned through its setter, which drops non-finite values; do NOT move it
            // into the constructor.
            val reverb = (units.rent() ?: return BuiltReverb.Denied).also {
                it.size = size
                it.lowpass = stage.lowpass?.takeIf { lp -> lp.isFinite() }
            }
            val send = StereoBuffer(blockFrames)
            val tail = TailCeiling()

            return BuiltReverb.Ready(
                reverb = reverb,
                tail = tail,
                fx = MasterFx { bus, frames ->
                    fillSend(send, bus, frames, wet)
                    // The ceiling reads what the unit is FED: the send, after `wet`, like the
                    // orbit stages read their feed.
                    tail.observe(
                        inputPeak = TailCeiling.peakOf(send, frames),
                        frames = frames,
                        windowSamples = reverb.tailWindowSamples,
                        feedback = reverb.tailFeedback,
                        lapsPerWindow = reverb.tailLapsPerWindow,
                    )
                    reverb.process(send, bus, frames)
                },
            )
        }

        /**
         * Delay as an *insert* — same send-copy trick as [buildReverb], same off-threshold as
         * `KatalystDelayEffect.MIN_ACTIVE_DELAY_SECONDS` (decided at BUILD time here, at configure
         * time there). Without the skip, a "zero" time would be coerced up to the DSP's ~5-sample
         * minimum and ring as a metallic comb.
         *
         * The ring is rented from the shelf at the class that holds the declared time plus the
         * interpolation margin — the SAME sizing rule as `KatalystDelayEffect.framesFor`, so a time
         * means one ring on either bus. There is no ceiling on either (a 20 s master delay is a
         * real 20 s echo, as it is on an orbit). A time past the Int range asks for
         * `Int.MAX_VALUE`, which nothing serves: the stage is [BuiltDelay.Denied], like a refused
         * allocation.
         */
        private fun buildDelay(
            stage: MasterStageDsl.Delay,
            sampleRate: Int,
            blockFrames: Int,
            rings: SizedBuffers,
        ): BuiltDelay? {
            val wet = finite(stage.wet, DELAY_WET)
            val time = finite(stage.time, DELAY_TIME_SECONDS)

            if (wet <= MIN_WET || time < MIN_TIME_FX) {
                return null
            }

            val frames = ceil(time * sampleRate)
            val needed = if (frames < Int.MAX_VALUE - ResourceWarehouse.RING_MARGIN_FRAMES) {
                frames.toInt() + ResourceWarehouse.RING_MARGIN_FRAMES
            } else {
                Int.MAX_VALUE
            }
            val ring = rings.rent(needed) ?: return BuiltDelay.Denied

            val delayLine = DelayLine(
                ring = ring,
                sampleRate = sampleRate,
                time = time,
                // A property initializer bypasses the class's own non-finite setter guard.
                feedback = finite(stage.feedback, DELAY_FEEDBACK),
            ).also {
                it.cap = finite(stage.cap, DELAY_CAP)
            }
            val send = StereoBuffer(blockFrames)
            val tail = TailCeiling()

            return BuiltDelay.Ready(
                delayLine = delayLine,
                tail = tail,
                fx = MasterFx { bus, frames ->
                    fillSend(send, bus, frames, wet)
                    // The ceiling reads what the unit is FED: the send, after `wet`, like the
                    // orbit stages read their feed.
                    tail.observe(
                        inputPeak = TailCeiling.peakOf(send, frames),
                        frames = frames,
                        windowSamples = delayLine.tailWindowSamples,
                        feedback = delayLine.feedback,
                        lapsPerWindow = delayLine.tailLapsPerWindow,
                    )
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
