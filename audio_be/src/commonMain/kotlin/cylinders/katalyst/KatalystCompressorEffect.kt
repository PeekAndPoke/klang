/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.utils.copyRangeInto
import io.peekandpoke.klang.audio_be.utils.crossfadeLinear
import io.peekandpoke.klang.audio_be.utils.linearFadeWeight
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import kotlin.math.min

/**
 * The orbit compressor, an insert: it processes the orbit mix in place.
 *
 * **How it switches** (decided with the maintainer 2026-09-19, `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` step 5c;
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
 * **With a lookahead** (phase 3 step 12 C2: `Katalyst(k => k.limiter(lookahead = 0.005))`) the
 * instance delays the orbit by [latencyFrames] and anticipates its transients (see
 * [Compressor.lookaheadSeconds]). The latency is the STAGE's, not the compression's: it holds in
 * every state, so switching the compression on or off never moves the orbit in time. Off passes the
 * DELAYED dry mix, a fade blends against the delayed dry, and the instance's ring keeps running
 * through Off; switching on from Off re-seeds the detector from the ring under the new knobs
 * ([Compressor.reseedFromRing]). Nothing compensates: the orbit, and the duck it triggers elsewhere,
 * run late by it, by the author's choice. A chain swap, on an orbit and at the output alike, places
 * both chains' weights so they meet at the output, delayed by the later of the two latencies
 * (`ChainSwap`, `Crossfade`): two chains of equal latency swap without a seam, and between two
 * latencies the jump in time happens under an ordinary linear crossfade of the two copies, weights
 * summing to one (a comb mid-fade: two time-offset copies lose level mid-fade, input-dependent).
 * The knob glides do not apply on
 * this path: [Compressor.processGliding] runs [Compressor.process] on a lookahead instance, whose
 * hold, release and box smoother already keep a knob step off the output.
 * Without a lookahead (the default, and every chain before step 12) none of this paragraph applies
 * and the effect is what it always was.
 *
 * The lifecycle is a state machine (`docs/plans/effect-state-machines.md`), the shape of
 * [KatalystDelayEffect]; the table on [State] is authoritative for its edges.
 *
 * **The four questions of the plan, answered for the compressor:**
 * 1. *What outlives its states:* the one [instance] (built with the effect, it survives every
 *    state), the three knob glides, the settings cache [applied], the dry scratch buffers, the
 *    snap flag [fresh], and with a lookahead the ring's tail count [quietFrames]. No `enter` touches
 *    them except [Off.enter]; outside the states [reset] resets [instance] and [quietFrames] too,
 *    and the ON edge out of Off re-seeds a lookahead instance's detector ([Off.switchOn]).
 * 2. *The record of a finished life:* the envelope, the knob glides and [applied], all forgotten
 *    in [Off.enter] (the instance is reset, the cache emptied so the next ON writes every knob).
 *    The next life starts from an envelope at rest, its knobs snap, and nothing can glide from a
 *    dead life's values. With a lookahead the instance is NOT reset there: its ring holds the
 *    orbit's last [latencyFrames] of signal, not a record, and keeps running through Off. The
 *    detector's record is dropped at the ON edge instead: [Compressor.reseedFromRing] puts it in the
 *    state of a fresh instance that has heard exactly the ring, under the new knobs, so the new
 *    life's ceiling holds from its first sample (it still fades in from the delayed dry over the
 *    switch fade, like every ON out of Off). [reset] still resets the whole instance.
 * 3. *The Off precondition:* the output is the dry mix, gain reduction 0 dB (with a lookahead, the
 *    DELAYED dry mix). [Off] is entered only from a fade whose weight has landed on exactly 0, by
 *    [reset] (the host guarantees silence), or while [fresh] (nothing has sounded).
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
    /**
     * The stage's lookahead in seconds, as the chain declares it (`KatalystStageDsl.Compressor
     * .lookahead`). Coerced here through [Compressor.coerceLookaheadSeconds], the one bound on what
     * building this effect allocates; 0 (the default) is no lookahead and no latency.
     */
    lookaheadSeconds: Double = 0.0,
) : KatalystEffect {

    private val fadeLen: Int = (sampleRate * KNOB_GLIDE_SECONDS).toInt().coerceAtLeast(1)

    private val invFadeLen: Double = 1.0 / fadeLen

    /**
     * The DSP, ONE instance built with the effect (5c-7 review round 1: the ON edge used to build a
     * `Compressor` on the audio thread). It outlives every state: [Off.enter] resets it (the
     * envelope back to rest) and the ON arm of [configure] writes all five knobs, which together
     * give the same doubles as a freshly built instance on the classic path (the constructor and
     * the setters store the same coerced values and compute the same coefficients). Proven by every
     * spec row that compares a new life against a FRESH bare `Compressor` bit for bit. One difference remains for a direct caller only: a
     * non-finite knob keeps the previous value where a constructor takes its default; the writer
     * never hands one (`KatalystCompressorWriter` substitutes the constants).
     */
    private val instance = Compressor(
        sampleRate = sampleRate,
        lookaheadSeconds = Compressor.coerceLookaheadSeconds(lookaheadSeconds),
    )

    /** Frames this stage delays the orbit by, in every state; 0 without a lookahead. */
    val latencyFrames: Int = instance.latencyFrames

    /** True when the instance has a lookahead ring: every lookahead-only branch below tests this. */
    private val latent: Boolean = latencyFrames > 0

    /**
     * Frames since the last input block that held a sample above [SILENCE_FLOOR], counted at
     * block ends and held at [latencyFrames]. Below [latencyFrames] the ring may still hold audio
     * the orbit has not heard yet, which is the tail [hasTail] reports. Only a latent instance
     * counts; [reset] sets it to "nothing in the ring".
     */
    private var quietFrames: Int = latencyFrames

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
    private var applied: CompressorSettings? = null

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
     *
     * With a lookahead the table is the same, read with "dry" as "the delayed dry" and "the reset
     * instance" as "the running instance" (only [reset] resets it).
     */
    private sealed class State {
        /** The owner wants compression; [compressor] is already there and configured. */
        abstract fun switchOn()

        /** The owner wants none. */
        abstract fun switchOff()

        /** One block of the orbit mix, in place. */
        abstract fun process(ctx: KatalystContext)
    }

    /**
     * No compression: the mix passes through untouched, or with a lookahead as the delayed dry mix
     * (the instance keeps running, see the class KDoc).
     */
    private inner class Off : State() {
        /**
         * Forgets the record of the finished life: the instance's envelope (reset to rest; with a
         * lookahead the instance keeps running instead, see the class KDoc), the knob glides, the
         * settings cache (so the next ON writes every knob).
         *
         * PRECONDITION: the output is already the dry mix, the delayed dry mix with a lookahead (a
         * fade out has landed on exactly 0, or the host guarantees silence at [reset], or nothing has
         * sounded yet). Entering Off from a sounding compressor is a level step, the click this class
         * exists to prevent.
         */
        fun enter() {
            // A latent instance keeps running through Off (its ring is the orbit's signal, see
            // the class KDoc); [reset] is what resets it.
            if (!latent) {
                instance.reset()
            }

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
                // A lookahead instance ran through Off at the last life's (or the constructor's)
                // knobs; [configure] has just written the new ones. One pass over the ring.
                if (latent) {
                    instance.reseedFromRing()
                }

                fading.enter(from = 0.0, to = 1.0)
            }
        }

        override fun switchOff() {}

        /** Without a lookahead, nothing. With one, the delayed dry mix: the latency holds while off. */
        override fun process(ctx: KatalystContext) {
            if (!latent) {
                return
            }

            val n = ctx.blockFrames

            ensureDry(n)

            val mixL = ctx.mixBuffer.left
            val mixR = ctx.mixBuffer.right

            instance.processLookahead(left = mixL, right = mixR, blockSize = n, delayedLeft = dryL, delayedRight = dryR)
            dryL.copyRangeInto(destination = mixL, destinationOffset = 0, startIndex = 0, endIndex = n)
            dryR.copyRangeInto(destination = mixR, destinationOffset = 0, startIndex = 0, endIndex = n)
        }
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
            compress(c = instance, left = ctx.mixBuffer.left, right = ctx.mixBuffer.right, n = ctx.blockFrames)
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
        private fun weightNow(): Double =
            linearFadeWeight(weightFrom = from, weightTo = to, remaining = fadeLen - pos, invLength = invFadeLen)

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
            ensureDry(n)

            val mixL = ctx.mixBuffer.left
            val mixR = ctx.mixBuffer.right
            val dL = dryL
            val dR = dryR
            val f = from
            val t = to
            val p0 = pos
            val len = fadeLen
            val inv = invFadeLen

            // The instance runs the whole block, so its envelope stays continuous. The dry partner
            // is what the orbit would sound like uncompressed at this moment: the input itself,
            // or with a lookahead the input D frames ago, straight out of the instance's ring.
            if (latent) {
                c.processLookahead(left = mixL, right = mixR, blockSize = n, delayedLeft = dL, delayedRight = dR)
            } else {
                mixL.copyRangeInto(destination = dL, destinationOffset = 0, startIndex = 0, endIndex = n)
                mixR.copyRangeInto(destination = dR, destinationOffset = 0, startIndex = 0, endIndex = n)
                compress(c = c, left = mixL, right = mixR, n = n)
            }

            val end = min(n, len - p0)

            // out = dry + w * (compressed - dry), the one law of the FilterSwap's bank fade too
            // (`utils/linear_crossfade.kt`).
            crossfadeLinear(
                target = mixL,
                base = dL,
                other = mixL,
                count = end,
                weightFrom = f,
                weightTo = t,
                remaining = len - p0,
                invLength = inv,
            )
            crossfadeLinear(
                target = mixR,
                base = dR,
                other = mixR,
                count = end,
                weightFrom = f,
                weightTo = t,
                remaining = len - p0,
                invLength = inv,
            )

            if (p0 + n < len) {
                pos = p0 + n

                return
            }

            // Landed: the samples from `end` on carry `to` exactly.
            if (t == 0.0) {
                // Gain reduction 0 dB: the rest of the block IS the dry mix, and the life ends.
                dL.copyRangeInto(destination = mixL, destinationOffset = end, startIndex = end, endIndex = n)
                dR.copyRangeInto(destination = mixR, destinationOffset = end, startIndex = end, endIndex = n)
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
     * every block the orbit has an owner (`KatalystChain.applyParams`), so an unchanged owner must cost
     * nothing: the knobs are written only when the settings object changes (see [applied]).
     */
    fun configure(settings: CompressorSettings?) {
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
            c.process(left = left, right = right, blockSize = n)

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

    /** Grows the dry scratch buffers to [n] frames once, for a direct caller's longer block. */
    private fun ensureDry(n: Int) {
        if (dryL.size < n) {
            dryL = AudioBuffer(n)
            dryR = AudioBuffer(n)
        }
    }

    override fun process(ctx: KatalystContext) {
        fresh = false

        if (latent) {
            countQuiet(ctx)
        }

        state.process(ctx)
    }

    /** Updates [quietFrames] from the block about to enter the ring. */
    private fun countQuiet(ctx: KatalystContext) {
        val n = ctx.blockFrames
        val left = ctx.mixBuffer.left
        val right = ctx.mixBuffer.right
        val floor = SILENCE_FLOOR

        for (i in 0 until n) {
            val l = left[i]
            val r = right[i]

            if (l > floor || l < -floor || r > floor || r < -floor) {
                quietFrames = 0

                return
            }
        }

        quietFrames = min(quietFrames + n, latencyFrames)
    }

    /**
     * Without a lookahead, false: the envelope follower is state, but a compressor only ATTENUATES
     * what it is given and emits nothing from silence, so it can neither hold nor start a tail,
     * fading or not. See [KatalystResonatorEffect.hasTail] for the insert-vs-send rule a future stage has
     * to apply.
     *
     * With a lookahead, true while the ring may still hold audio above [SILENCE_FLOOR] (fewer
     * than [latencyFrames] quiet frames since the last loud input block): the orbit has not heard it
     * yet, and a chain swap retires a leaving chain at the end of its ramp unless it reports a tail
     * (`ChainSwap`), which would cut it.
     */
    override fun hasTail(): Boolean = latent && quietFrames < latencyFrames

    /**
     * A HARD cut to [Off], the instance reset to rest, and the next switch snaps again. Only for a silent
     * orbit (the cylinder's deactivation) or the shelf ([retire]).
     */
    override fun reset() {
        off.enter()

        // [Off.enter] leaves a latent instance running; the hard cut resets it, ring included.
        if (latent) {
            instance.reset()
            quietFrames = latencyFrames
        }

        fresh = true
    }

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }
}
