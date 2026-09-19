/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.min

/**
 * The orbit compressor, an insert: it processes the orbit mix in place.
 *
 * **How it switches** (decided with the maintainer 2026-09-19, `docs/tasks/katalyst-dsl.md` step 5c;
 * built in Katalyst 5c-7). Every edge fades over [KNOB_GLIDE_SECONDS], linearly, between the
 * compressed mix and the DRY mix, the law of [KatalystFilterSwap] with the dry signal as the fade
 * partner: `out = dry + w * (compressed - dry)`, so the gain reduction in force is scaled towards 0
 * dB and lands there exactly.
 * - **OFF** glides the gain reduction to 0 dB (`w` from 1 to 0) while the instance keeps running
 *   on the live signal, and only once `w` is exactly 0, so the output IS the dry mix, does the
 *   effect enter [Off] and forget that life. Not by letting the compressor's own release
 *   run out. Measured (5c-7; a saw, a three-note chord and a bass, each band-limited to 3 kHz, at
 *   3, 6 and 12 dB of reduction, 44.1 and 48 kHz; peak 0.7 ms RMS above 8 kHz and 20 ms RMS below
 *   60 Hz, both against the signal): the hard drop sat at -10 to -39 dB above 8 kHz (a click) and
 *   -5 to -34 dB below 60 Hz (a thump on the bass); the glide lands at -78 to -86 dB above 8 kHz,
 *   the steady floor, and below 60 Hz within 7 dB of the compressor's own steady floor (the level
 *   change itself, spread over the glide).
 * - **ON** starts a new life (the envelope rises from rest by its own attack, exactly as a freshly
 *   built compressor's would) and fades it in from dry. Measured without the fade-in: not a click (-67 dB or quieter above 8 kHz), but at
 *   an attack of 5 ms or less the level drops at the attack's speed, 8 to 26 dB above the steady
 *   floor below 60 Hz; the fade-in leaves at most 8 dB, like the OFF glide. At a 20 ms attack the
 *   fade has nothing to add: the reduction arrives after it, as it does on any note from silence.
 * - **A return** (an owner that wants compression back while it fades out, or drops it while it
 *   fades in) turns the fade around where it stands: the SAME instance, its envelope untouched,
 *   `w` ramping from its current value to the other end over one full fade.
 * - **The first initialisation is instant.** Until a block has been processed since
 *   construction or [reset], a switch acts at once (the `KnobGlide` snap rule): nothing has
 *   sounded, so there is nothing to be continuous with. A compressor set on an orbit's first block
 *   is exactly what it was before this step.
 * - **[reset] and [retire] are a HARD cut** to [Off], for the cylinder's deactivation and the
 *   shelf: the orbit is silent by then, and a fade that survived would resume in the orbit's next
 *   life on new material.
 *
 * **What glides while it runs** (`docs/plans/knob-glide.md`, measured first in 5c-7): the gain
 * computer's THRESHOLD, RATIO and KNEE, per sample ([Compressor.processGliding]), each on its own
 * [KnobGlide], the ratio as its INVERSE (the curve is linear in `1 / ratio - 1`; a glide linear in
 * the ratio bunched a 100 to 1 change into the glide's last blocks, -41 dB). A jump of any of them steps the gain at once, because the gain computer has no
 * memory: a threshold jump measured -9 to -36 dB above 8 kHz, a ratio jump -27 to -43, a knee jump
 * (with the envelope inside the knee) -34 to -52; a per-block glide left -31 to -69 dB in a model
 * of the gain computer (the zipper); the per-sample ramp reaches the steady floor in every row but
 * one kind: a wide RISING threshold swing (-40 to 0 dB) stays at -62 to -65 dB, part the size of a
 * 25 dB release in 50 ms, part the threshold moving the reduction linearly in dB.
 * ATTACK and RELEASE do not glide: they are the follower's own coefficients and its state stays
 * continuous across a jump (measured at the steady floor above 8 kHz, both directions). The old
 * path, literally: [configure] writes the knobs into the instance, and while a glide moves the
 * block runs at the glide's per-sample values instead.
 *
 * The lifecycle is a state machine (`docs/plans/effect-state-machines.md`), the shape of
 * [KatalystDelayEffect]; the table on [State] is authoritative for its edges.
 *
 * **The four questions of the plan, answered for the compressor:**
 * 1. *What outlives its states:* the one [instance] (built with the effect, it survives every
 *    state), the three knob glides, the settings cache [applied], the dry scratch buffers, and the
 *    snap flag [fresh]. No `enter` touches them except [Off.enter].
 * 2. *The record of a finished life:* the envelope, the knob glides and [applied], all forgotten
 *    in [Off.enter] (the instance is reset, the cache emptied so the next ON writes every knob).
 *    The next life starts from an envelope at rest, its knobs snap, and nothing can glide from a
 *    dead life's values.
 * 3. *The Off precondition:* the output is the dry mix, gain reduction 0 dB. [Off] is entered only
 *    from a fade whose weight has landed on exactly 0, by [reset] (the host guarantees silence),
 *    or while [fresh] (nothing has sounded).
 * 4. *References and who drops them:* none is dropped. The instance is built once and lives on the
 *    effect; the settings cache is forgotten by [Off.enter]. Fading's own data are numbers (the
 *    fade's two ends and its position), so [reset] enters Off without dispatching, as the delay's
 *    does.
 */
class KatalystCompressorEffect(
    private val sampleRate: Int,
    /**
     * The frames of one render block, pinned to [AudioBackendContext.RENDER_QUANTUM_FRAMES] in the
     * engine; `KatalystChainBuilder` passes its own. It sizes the dry scratch buffers and counts the
     * knob glides' blocks.
     */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) : KatalystEffect {

    private val fadeLen: Int = (sampleRate * KNOB_GLIDE_SECONDS).toInt().coerceAtLeast(1)

    private val invFadeLen: Double = 1.0 / fadeLen

    /**
     * The DSP, ONE instance built with the effect (5c-7 review round 1: the ON edge used to build a
     * `Compressor` on the audio thread). It outlives every state: [Off.enter] resets it (the
     * envelope back to rest) and the ON arm of [configure] writes all five knobs, which together
     * give the same doubles as a freshly built instance on the classic path (the constructor and
     * the setters store the same coerced values and compute the same coefficients; nothing on an
     * orbit writes `makeupGainDb`). Proven by every spec row that compares a new life against a
     * FRESH bare `Compressor` bit for bit. One difference remains for a direct caller only: a
     * non-finite knob keeps the previous value where a constructor takes its default; the writer
     * never hands one (`Voice.Compressor.fromParams` substitutes the constants).
     */
    private val instance = Compressor(sampleRate = sampleRate)

    /**
     * The DSP while the stage is on (Engaged or Fading), null in [Off]. Read-only outside: every
     * write goes through [configure] and the states.
     */
    val compressor: Compressor? get() = if (state === off) null else instance

    /**
     * The settings last written into [compressor], by reference: the writer resolves a new object
     * only when the owner's param state changes, so an unchanged owner costs one compare per block
     * instead of five setters and about fifteen `exp()` (the open item on `writeCompressor`,
     * closed here). Forgotten in [Off.enter], so the ON arm out of Off always writes the knobs.
     */
    private var applied: Voice.Compressor? = null

    private val thresholdGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)
    /** The ratio glides as its INVERSE, `1 / ratio`: see [Compressor.processGliding]. */
    private val inverseRatioGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)
    private val kneeGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /**
     * True from construction and from [reset] until a block has been processed: while it holds, a
     * switch acts at once (the first initialisation is instant). It outlives every state.
     */
    private var fresh: Boolean = true

    /**
     * The dry mix of a [Fading] block. Sized for one block at construction; a block longer than
     * `blockFrames` grows them once (a direct caller only: the engine's blocks are all
     * `blockFrames` long).
     */
    private var dryL: AudioBuffer = AudioBuffer(blockFrames)
    private var dryR: AudioBuffer = AudioBuffer(blockFrames)

    /**
     * The lifecycle, one class per state. One instance of each is created with the effect and
     * [state] points at the current one, so a transition is a pointer swap and nothing allocates on
     * the audio thread (nor does the ON edge: the [Compressor] is built once, see [instance]).
     * `enter` is the only way into a state, and it is what initialises that state's own data.
     *
     * "Fresh" is [fresh]: no block processed since construction or [reset].
     *
     * | state \ event | `configure`, ON | `configure`, OFF | `process`, the fade lands | `reset` / `retire` |
     * |---|---|---|---|---|
     * | **Off** | the knobs written into the reset instance; fresh: **Engaged** at once, else **Fading** in from dry | Off | (never) | Off |
     * | **Engaged** | Engaged (the knobs are written; a changed one glides) | fresh: **Off** at once; else **Fading** out | (never) | **Off** |
     * | **Fading** | fading in: Fading; fading out: Fading, turned around (the same instance) | fading out: Fading (idempotent); fading in: Fading, turned around | **Engaged** (in) or **Off** (out, the output exactly dry) | **Off** |
     *
     * The ON arm writes the knobs the same way from every state, on the effect ([configure]); what
     * differs per state is the switch, and that dispatches ([State.switchOn], [State.switchOff]).
     */
    private sealed class State {
        /** The owner wants compression; [compressor] is already there and configured. */
        abstract fun switchOn()

        /** The owner wants none. */
        abstract fun switchOff()

        /** One block of the orbit mix, in place. */
        abstract fun process(ctx: KatalystContext)
    }

    /** Nothing runs: the mix passes through untouched. */
    private inner class Off : State() {
        /**
         * Forgets the record of the finished life: the instance's envelope (reset to rest), the
         * knob glides, the settings cache (so the next ON writes every knob).
         *
         * PRECONDITION: the output is already the dry mix (a fade out has landed on exactly 0, or
         * the host guarantees silence at [reset], or nothing has sounded yet). Entering Off from a
         * sounding compressor is a level step, the click this class exists to prevent.
         */
        fun enter() {
            instance.reset()
            applied = null
            thresholdGlide.reset()
            inverseRatioGlide.reset()
            kneeGlide.reset()
            state = this
        }

        override fun switchOn() {
            if (fresh) {
                engaged.enter()
            } else {
                fading.enter(from = 0.0, to = 1.0)
            }
        }

        override fun switchOff() {}

        override fun process(ctx: KatalystContext) {}
    }

    /** The instance at full weight, in place. It owns no data: the instance outlives it. */
    private inner class Engaged : State() {
        fun enter() {
            state = this
        }

        override fun switchOn() {}

        override fun switchOff() {
            if (fresh) {
                off.enter()
            } else {
                fading.enter(from = 1.0, to = 0.0)
            }
        }

        override fun process(ctx: KatalystContext) {
            compress(instance, ctx.mixBuffer.left, ctx.mixBuffer.right, ctx.blockFrames)
        }
    }

    /**
     * The weight `w` of the compressed mix against the dry one moves linearly from [from] to [to]
     * (0 or 1) over one fade, counted in samples; the weight of the fade's k-th sample is
     * `to + (from - to) * (fadeLen - k) / fadeLen`, so the first sample carries [from] and the
     * sample at `fadeLen` is [to] exactly. Never entered while [fresh].
     *
     * The two ends and the position die with this state, so they live here; [enter] is their
     * only initialiser.
     */
    private inner class Fading : State() {
        private var from: Double = 0.0
        private var to: Double = 0.0
        private var pos: Int = 0

        fun enter(from: Double, to: Double) {
            this.from = from
            this.to = to
            pos = 0
            state = this
        }

        /** The weight the next sample would carry: where a turned-around fade starts. */
        private fun weightNow(): Double = to + (from - to) * ((fadeLen - pos) * invFadeLen)

        override fun switchOn() {
            if (to == 0.0) {
                enter(from = weightNow(), to = 1.0)
            }
        }

        override fun switchOff() {
            if (to == 1.0) {
                enter(from = weightNow(), to = 0.0)
            }
        }

        override fun process(ctx: KatalystContext) {
            val c = instance
            val n = ctx.blockFrames

            // Grow the FIELDS first, then take the locals (see [dryL]).
            if (dryL.size < n) {
                dryL = AudioBuffer(n)
                dryR = AudioBuffer(n)
            }

            val mixL = ctx.mixBuffer.left
            val mixR = ctx.mixBuffer.right
            val dL = dryL
            val dR = dryR
            val f = from
            val t = to
            val p0 = pos
            val len = fadeLen
            val inv = invFadeLen

            mixL.copyInto(dL, 0, 0, n)
            mixR.copyInto(dR, 0, 0, n)

            // The instance runs the whole block, so its envelope stays continuous.
            compress(c, mixL, mixR, n)

            val end = min(n, len - p0)

            for (i in 0 until end) {
                val w = t + (f - t) * ((len - p0 - i) * inv)

                mixL[i] = dL[i] + w * (mixL[i] - dL[i])
                mixR[i] = dR[i] + w * (mixR[i] - dR[i])
            }

            if (p0 + n < len) {
                pos = p0 + n

                return
            }

            // Landed: the samples from `end` on carry `to` exactly.
            if (t == 0.0) {
                // Gain reduction 0 dB: the rest of the block IS the dry mix, and the life ends.
                dL.copyInto(mixL, end, end, n)
                dR.copyInto(mixR, end, end, n)
                off.enter()
            } else {
                // Full weight: the rest of the block is the compressed mix as it stands.
                engaged.enter()
            }
        }
    }

    private val off = Off()
    private val engaged = Engaged()
    private val fading = Fading()

    /**
     * The current state: one of the three instances above, never a fresh one. This initializer is
     * the one entry into Off that does not run [Off.enter]; a just-built instance is what a reset
     * makes of it, and there is no glide or cache yet to forget.
     */
    private var state: State = off

    /**
     * Test seam: the current state OBJECT, for `KatalystCompressorStateIdentitySpec`, which walks the
     * transition table and asserts that only ever those three instances appear. Production never
     * reads it.
     */
    internal val currentState: Any get() = state

    /**
     * Applies the orbit owner's compressor settings, null for none. Called by the chain's writer on
     * every block the lease is held (`KatalystChain.applyParams`), so an unchanged owner must cost
     * nothing: the knobs are written only when the settings object changes (see [applied]).
     */
    fun configure(settings: Voice.Compressor?) {
        if (settings == null) {
            state.switchOff()

            return
        }

        // Out of Off [applied] is null, so a new life always writes all five.
        if (settings !== applied) {
            val c = instance

            c.thresholdDb = settings.thresholdDb
            c.ratio = settings.ratio
            c.kneeDb = settings.kneeDb
            c.attackSeconds = settings.attackSeconds
            c.releaseSeconds = settings.releaseSeconds
            applied = settings
            retarget(c)
        }

        state.switchOn()
    }

    /**
     * Points the three glides at what the instance STORED, not at the raw settings: the setters
     * coerce (a ratio of at least 1, a knee of at least 0) and drop non-finite values, and the
     * glide must land on exactly the number the settled path then reads. A glide forgotten in
     * [Off.enter] snaps here, so a new instance starts on its own knobs.
     */
    private fun retarget(c: Compressor) {
        thresholdGlide.retarget(c.thresholdDb)
        inverseRatioGlide.retarget(1.0 / c.ratio)
        kneeGlide.retarget(c.kneeDb)
    }

    /**
     * One block of [c] in place: the settled path (the instance's own [Compressor.process], which
     * reads the knobs it stores) unless a knob glide moves, then [Compressor.processGliding] from
     * the values the last block ended on to this block's.
     */
    private fun compress(c: Compressor, left: AudioBuffer, right: AudioBuffer, n: Int) {
        val thresholdFrom = thresholdGlide.value
        val thresholdTo = thresholdGlide.advance()
        val inverseRatioFrom = inverseRatioGlide.value
        val inverseRatioTo = inverseRatioGlide.advance()
        val kneeFrom = kneeGlide.value
        val kneeTo = kneeGlide.advance()

        if (thresholdFrom == thresholdTo && inverseRatioFrom == inverseRatioTo && kneeFrom == kneeTo) {
            c.process(left, right, n)

            return
        }

        c.processGliding(
            left = left,
            right = right,
            blockSize = n,
            thresholdFrom = thresholdFrom,
            thresholdTo = thresholdTo,
            inverseRatioFrom = inverseRatioFrom,
            inverseRatioTo = inverseRatioTo,
            kneeFrom = kneeFrom,
            kneeTo = kneeTo,
        )
    }

    override fun process(ctx: KatalystContext) {
        fresh = false
        state.process(ctx)
    }

    /**
     * False: the envelope follower is state, but a compressor only ATTENUATES what it is given and
     * emits nothing from silence, so it can neither hold nor start a tail, fading or not. See
     * [KatalystBodyEffect.hasTail] for the insert-vs-send rule a future stage has to apply.
     */
    override fun hasTail(): Boolean = false

    /**
     * A HARD cut to [Off], the instance reset to rest, and the next switch snaps again. Only for a silent
     * orbit (the cylinder's deactivation) or the shelf ([retire]).
     */
    override fun reset() {
        off.enter()
        fresh = true
    }

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }
}
