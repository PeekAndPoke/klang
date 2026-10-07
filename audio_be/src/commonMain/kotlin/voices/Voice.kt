/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.utils.fadeToZero
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.send.SendRenderer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.CUT_FADE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_SECONDS
import kotlin.math.ceil

/**
 * A voice in the audio engine.
 *
 * Runs a composable [BlockRenderer] pipeline: **Pitch → Ignite → (teardown fade) → Send**. The Ignitor tree the
 * ignite stage renders IS the instrument, envelope and filters included; the voice strip that used to run after
 * it retired in phase 3 step 9.
 *
 * **Lifecycle.** The voice is a state machine ([state], [State]). [render] advances the state at the start of
 * every block (the time-driven transitions, [advance]) and then dispatches on it; the cull may end it at the
 * block's end. Events from outside arrive as methods ([releaseGate], [cutOff], [kill]), and the voice decides by its
 * state whether they apply. The state describes a whole block, so a block may still hold frames of the next phase
 * (the gate end, the death frame) that the stages see frame by frame.
 *
 * The transitions, states times events. This table is the one list of them; the methods point here.
 *
 * | state \ event | block start ([advance]) | block end (the cull) | [releaseGate] | [cutOff], finite onset | [kill] |
 * |---|---|---|---|---|---|
 * | **Pending** | **Done** at [endFrame]; else **Sounding** once the block ends after the onset, and **Releasing** in the same call if the block starts at or after the gate end | (renders nothing) | moves the gate and the end, stays Pending | **Done** | **Done** |
 * | **Sounding** | **Done** at [endFrame]; else **Releasing** once the block starts at or after the gate end | measures until heard, never counts | moves the gate and the end; Releasing from the first block that starts at or after the new gate | **Fading** | **Done** |
 * | **Releasing** | **Done** at [endFrame] | **Done** ([culled]) at the end of the block that completes the cull window of silence, once heard | ignored | **Fading** | **Done** |
 * | **Fading** | **Done** at [endFrame] or at the fade end, whichever comes first | no measurement | ignored | ignored | **Done** |
 * | **Done** | stays Done | (renders nothing; [render] returns false) | ignored | ignored | stays Done |
 *
 * "At [endFrame]" (or the fade end) means from the first block that starts at or after it. A note-off moves the
 * gate only to an earlier frame than the one it has. A [cutOff] with a non-finite onset is **Done** from every
 * state. `Fading` and `Done` are terminal: nothing leads out of them but `Fading` to `Done`.
 *
 * The orbit's bus settings are owned by the newest `Sounding` voice; a voice gives the orbit up when its gate
 * closes or it is cut ([claimsOrbit]). The states are a sealed type: `Releasing` carries the cull's silence count
 * and `Fading` the cut's fade window, each one instance created with the voice, so no transition allocates
 * ([State]). The plan: `docs/tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`.
 */
class Voice(
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Identity: globally-unique, monotonic. Used by per-orbit effect ownership (`Cylinder.offer`) to order
    // voices with the same onset (the later-created wins). Defaulted so every
    // constructed voice gets a fresh id. Voice creation is single-threaded (the render thread).
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    val id: Int = nextId(),

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Routing (the time limits arrive with the block context, see [limits])
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    val cylinderId: Int,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Dynamics & Routing
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // The orbit's bus reads its knobs from [katalystParams] alone (Katalyst step 5b-1), and the
    // voice carries no bus settings of its own since step 5b-3. Who reads what, exactly:
    //
    //   gain, pan     SendRenderer, every block of every voice.
    val gain: Double,
    val pan: Double,

    /**
     * The orbit chain slots this voice writes (`VoiceData.katalystParams`), carried by REFERENCE:
     * the wire map is immutable by contract and the copy would be per voice, for a map only the
     * orbit's owner ever reads.
     *
     * While this voice owns the orbit (the newest `Sounding` voice, see [claimsOrbit]), the orbit's
     * chain resolves every `Param` knob against it (`KatalystChain.applyParams`), so this is the orbit's
     * param state; the reference is dropped when the voice gives the orbit up, and the orbit keeps the
     * settings applied last until the next `Sounding` voice claims. Null when the pattern wrote no slot, which is the same answer as an empty map: the
     * chain's authored defaults. Which chain reads which slot of it is one rule with one home, the
     * `katalystParam` door's KDoc in `sprudel/lang/lang_katalyst.kt`: EVERY chain reads it, for every stage
     * it declares, the chain a cylinder is born with included (Katalyst step 5b-1).
     */
    val katalystParams: Map<String, Double>? = null,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Cut group
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    val cut: Int? = null,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Silence culling: the `cull(seconds)` window; null = VOICE_CULL_SECONDS, negative = never (noCull()).
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    cull: Double? = null,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Pipeline: Pitch → Ignite → (teardown fade) (Send is appended in init)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    pipeline: List<BlockRenderer>,

    // Pre-built BlockContext (created by VoiceFactory, mutated per block). It carries the voice's [limits].
    private val blockCtx: BlockContext,
) {
    /**
     * The voice's time limits (onset, gate end, end; absolute frames), the ONE home of them: the
     * instance the factory put into the block context, which every stage reads per call. This voice is
     * its only writer ([releaseGate]).
     */
    private val limits: VoiceLimits = blockCtx.limits

    /** The onset (absolute frame, Double, see RenderClock.cursorFrame). */
    val startFrame: Double get() = limits.startFrame

    /**
     * Voice death frame (gate end + release tail). Rewritten by a realtime note-off
     * ([releaseGate]): earlier in every real case; an authored NEGATIVE release (raw-Motor,
     * passes through unclamped) can nudge it later by under a block, with no audible effect.
     */
    val endFrame: Double get() = limits.endFrame

    /** Frame where release begins. Moves earlier on a realtime note-off ([releaseGate]). */
    private val gateEndFrame: Double get() = limits.gateEndFrame

    // The stages before the send: Pitch → Ignite → (teardown fade). A cut's fade runs between them and the send.
    private val stages: List<BlockRenderer> = pipeline

    // The last stage: the send into the orbit.
    private val send: BlockRenderer = SendRenderer(voice = this)

    /**
     * The lifecycle state, `Pending` at construction. [render] moves it at the start and the end of a block, the
     * events between blocks; every transition: the table in the class KDoc.
     */
    var state: State = State.Pending
        private set

    // The states with data of their own, one instance each, created with the voice. `enter` returns its state, so
    // a transition is written as one line with its entry, `state = x.enter(...)` (a convention: `state = fading`
    // alone would still compile). Neither a transition nor a block allocates.
    private val releasing = State.Releasing()
    private val fading = State.Fading()

    /**
     * True once this voice ended by culling: its release stayed under [VOICE_CULL_FLOOR] for the whole cull
     * window, so it turned `Done` at the end of that release block and the scheduler removed it, long before
     * its scheduled [endFrame] (the rest of the tail would only have produced silence). A latch, set with
     * that `Done`; the scheduler's culled count reads it. Until lifecycle step 5 a culled voice stayed listed
     * as a zombie to keep the active list's order (it decided who took an orbit next); since ownership goes by
     * onset ([claimsOrbit]) and the list keeps its order on every removal, nothing needs it any more.
     */
    var culled: Boolean = false
        private set

    /**
     * Whether this voice offers itself as the owner of its orbit's bus settings in the block starting at
     * [blockStart]: `Sounding`, and the gate still open at the voice's first frame in the block. The orbit's bus
     * settings are owned by the newest `Sounding` voice; a voice gives the orbit up when its gate closes or it is
     * cut (lifecycle step 5, maintainer 2026-10-07; the order is `Cylinder.offer`'s). A zero-length gate never
     * offers.
     */
    fun claimsOrbit(blockStart: Double): Boolean =
        state is State.Sounding && gateEndFrame > maxOf(blockStart, startFrame)

    /**
     * Whether the gate is still open at [frame]: the voice has not been released, cut or ended (`Pending` or
     * `Sounding`) and its gate ends after [frame]. The scheduler's realtime solo reads it once per block.
     */
    fun gateOpenAt(frame: Double): Boolean =
        (state is State.Pending || state is State.Sounding) && gateEndFrame > frame

    /**
     * True once any block of this voice has been audible (peak at or above [VOICE_CULL_FLOOR]).
     * A voice that has not sounded yet is never culled, whatever its gate says: a sample with
     * leading silence pitched two octaves down, or an ignitor envelope whose attack outlives a
     * short gate, is silent at gate end and sounds only later. The gate marks "the note was told
     * to stop", not "the sound has started"; this latch marks the latter. A voice whose [gain] is
     * exactly zero (`gain(0)`, the hand mute) can never be heard and starts latched, so its silent
     * tail is culled like any other. Every voice the factory builds has a FINITE gain (it
     * substitutes a non-finite wire value), so this compare settles; a NaN would leave the latch
     * off forever.
     */
    private var heard: Boolean = gain == 0.0

    /**
     * The cull window in frames. Negative = never cull (`noCull()`); `0` = end at the first silent
     * block of the release; otherwise the consecutive silent frames the release must show first.
     * Counted in FRAMES, not blocks, so the window has the same length at any block size and the
     * cut lands within one block of the same frame.
     */
    private val cullWindowFrames: Int = when {
        cull == null || cull != cull -> (VOICE_CULL_SECONDS * blockCtx.sampleRateD).toInt() // NaN-guard: default
        cull < 0.0 -> -1
        else -> (cull * blockCtx.sampleRateD).toInt()
    }

    // Dynamic gain multiplier (set by VoiceScheduler once per block, for solo/mute)
    private var _gainMultiplier: Double = 1.0
    private var _gainMultiplierFrom: Double = 1.0
    private var gainMultiplierSet: Boolean = false

    /** The multiplier this block ends on. */
    val gainMultiplier: Double get() = _gainMultiplier

    /**
     * The multiplier this block starts from: the one the previous block ended on. `SendRenderer` ramps linearly
     * from here to [gainMultiplier] across the block, so a change is never a step inside a sample (a click); when
     * the two are equal (no solo anywhere: 1.0 to 1.0) it applies the plain constant, bit for bit as before.
     */
    val gainMultiplierFrom: Double get() = _gainMultiplierFrom

    /**
     * Sets the multiplier for the coming block; called once per block. The first call sets both ends, so a voice
     * starts at its multiplier instead of ramping in from 1.0.
     */
    fun setGainMultiplier(multiplier: Double) {
        _gainMultiplierFrom = if (gainMultiplierSet) _gainMultiplier else multiplier
        _gainMultiplier = multiplier
        gainMultiplierSet = true
    }

    /**
     * The note-off event: releases the gate NOW (realtime note-off). It moves the gate and the end in [limits], the one
     * home every gate consumer reads (the stages through [BlockContext.limits], the ignitors through
     * the gate the ignite stage derives from it per block), and the envelopes
     * enter their release from the level the envelope law gives AT the new gate frame
     * (`EnvelopeCore`, stateless), which is the level the voice would have rendered there. The release
     * SPAN is untouched: only WHEN it begins moves.
     *
     * Deliberately untouched: `IgniteContext.voiceDurationFrames` (the `accelerate` glide base) —
     * see its KDoc; on held realtime voices `accelerate` is inert by decision.
     *
     * PRECONDITION: `atFrame >= startFrame` — the caller owns it (the scheduler floors at
     * `startFrame + blockFrames`, see `VoiceScheduler.releaseRealtimeVoice`). An earlier frame
     * would write a gate at or before the onset, and the envelope law releases such a gate from
     * level 0 (`EnvelopeCore.prepare`): every envelope is 0 on every frame (an amplitude envelope
     * silences the voice), no exception, no error.
     *
     * Which states take it: the transition table in the class KDoc. A `Releasing` voice ignores it because it
     * has been released already; through the scheduler that is what happened before the state existed: such a
     * voice rendered a block that started at or after its gate, and the scheduler releases at its cursor, which
     * lies after that block's start, so the natural-gate check below returned (a `Done` voice has left the
     * active list).
     */
    fun releaseGate(atFrame: Double) {
        if (state !is State.Pending && state !is State.Sounding) {
            return
        }

        // Natural gate is earlier: no-op (also makes a double-stop idempotent).
        if (atFrame >= gateEndFrame) {
            return
        }

        // Raw-Motor: an authored NEGATIVE release passes through resolve() unclamped, making
        // endFrame < gateEndFrame. A note-off must still STOP such a voice (ignoring it would
        // strand it at the held horizon — audit R5), so the span clamps to 0 and it hard-stops
        // exactly like release-0: endFrame lands on atFrame and the voice is dropped between
        // blocks WITHOUT rendering another sample — the same outcome a timeline release-0 note
        // gets at its gate (audit R9: no de-click runs on this path; none is needed, nothing
        // renders).
        val releaseSpan = (endFrame - gateEndFrame).coerceAtLeast(0.0)

        // The one write: every stage reads [limits] per call, and the ignite stage derives the ignitors'
        // voice-relative gate from it per block, so no copy can be left behind (amendment A1).
        limits.gateEndFrame = atFrame
        limits.endFrame = atFrame + releaseSpan
    }

    /**
     * The hard-kill event: the voice is `Done` NOW (the transition table in the class KDoc) and renders nothing
     * more (no fade: the caller is a teardown path, `VoiceScheduler.cleanupHard` at the end of the warmup
     * handshake). The scheduler removes `Done` voices; it never ends a voice any other way (the cut
     * is an event too, [cutOff]).
     */
    fun kill() {
        state = State.Done
    }

    /**
     * The cut event: a voice of the same cut group begins at [fadeStartFrame] (its onset, absolute frame; it may
     * fall inside a block). What it does in each state: the transition table in the class KDoc (a `Pending` voice
     * is silent so far and ends at once; the scheduler removes it, keeping the list's order).
     *
     * A `Fading` voice plays on until [fadeStartFrame], then ramps linearly to exact zero over [CUT_FADE_SECONDS]
     * (after the instrument tree, before the send, so the orbit sends fade too). The fade window lives in the
     * `Fading` state ([State.Fading]); the end frame does not move, so a voice whose own end comes first ends there
     * as usual (its teardown, where it has one, stays at its own end and multiplies with the ramp in the overlap;
     * both are continuous).
     *
     * A non-finite [fadeStartFrame] cannot place a fade: the voice is `Done` at once, whatever its state (the
     * scheduler already drops a voice with a non-finite start before it can cut; this guards a direct call).
     */
    fun cutOff(fadeStartFrame: Double) {
        if (!fadeStartFrame.isFinite()) { // NaN-guard: a non-finite onset ends the voice, never a NaN fade
            state = State.Done

            return
        }

        state = when (state) {
            is State.Pending -> {
                State.Done
            }

            is State.Sounding, is State.Releasing -> {
                fading.enter(
                    fadeStartFrame = fadeStartFrame,
                    fadeEndFrame = fadeStartFrame + CUT_FADE_SECONDS * blockCtx.sampleRateD,
                )
            }

            is State.Fading, is State.Done -> {
                state
            }
        }
    }

    /**
     * Renders the voice into the context's buffers.
     *
     * Advances the [state] for this block, then dispatches on it: `Pending` renders nothing,
     * `Sounding` and `Releasing` run the BlockRenderer pipeline (Pitch → Ignite → (teardown fade) →
     * Send), `Fading` runs it with the cut's ramp before the send, `Done` returns false. A releasing
     * voice that the cull ends in its block returns false at once.
     *
     * @return true if the voice is still active, false if it has finished (`Done`)
     */
    fun render(ctx: RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames

        advance(blockStart = ctx.blockStart, blockEnd = blockEnd)

        return when (state) {
            is State.Pending -> true

            is State.Sounding -> {
                renderStages(ctx, blockEnd, isReleasing = false, isFading = false)

                true
            }

            is State.Releasing -> {
                renderStages(ctx, blockEnd, isReleasing = true, isFading = false)

                // The cull may have ended it at this block's end.
                state !is State.Done
            }

            is State.Fading -> {
                renderStages(ctx, blockEnd, isReleasing = false, isFading = true)

                true
            }

            is State.Done -> false
        }
    }

    /**
     * The time-driven transitions at the start of the block `[blockStart, blockEnd)`: the "block start" column
     * of the transition table in the class KDoc. The `Done` check comes first, for every state; a voice whose
     * onset and gate end fall into one pending block passes through `Sounding` to `Releasing` in one call.
     * Forward only: no branch leaves `Done`.
     */
    private fun advance(blockStart: Double, blockEnd: Double) {
        if (blockStart >= endFrame || (state is State.Fading && blockStart >= fading.fadeEndFrame)) {
            state = State.Done

            return
        }

        if (state is State.Pending) {
            if (blockEnd <= startFrame) {
                return
            }

            state = State.Sounding
        }

        if (state is State.Sounding && blockStart >= gateEndFrame) {
            state = releasing.enter()
        }
    }

    /**
     * One block of the pipeline, for a `Sounding`, `Releasing` or `Fading` voice, and the cull measurement
     * that may end a releasing voice (`Done`, [culled]) at the block's end (the "block end" column of the
     * transition table in the class KDoc). A `Fading` voice gets the cut's ramp between the stages and the send.
     */
    private fun renderStages(ctx: RenderContext, blockEnd: Double, isReleasing: Boolean, isFading: Boolean) {
        val vStart = maxOf(ctx.blockStart, startFrame)
        val vEnd = minOf(blockEnd, endFrame)
        // Relative to this block / this voice — Int, and everything downstream of here is Int.
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // Update per-block state
        blockCtx.audioBuffer = ctx.voiceBuffer
        blockCtx.updateOffsetAndLength(offset = offset, length = length)
        blockCtx.blockStart = ctx.blockStart
        blockCtx.renderContext = ctx
        blockCtx.freqModBufferWritten = false

        // Silence culling reads the output peak only on a cullable voice, and only while it is
        // needed: until the voice has been heard (the [heard] latch), then in the release. A heard
        // `Sounding` voice pays nothing for the rest of its gate, a `Fading` one nothing at all.
        val measure = !isFading && cullWindowFrames >= 0 && (!heard || isReleasing)
        blockCtx.measurePeak = measure
        blockCtx.voiceOutputPeak = 0.0 // never a stale read from the previous block

        // ── Pitch → Ignite → (teardown fade) → Send ───────────────────────────────

        for (renderer in stages) {
            renderer.render(blockCtx)
        }

        if (isFading) {
            applyCutFade()
        }

        send.render(blockCtx)

        // ── Silence culling ───────────────────────────────────────────────────────
        // Only in the release: the gate is the held part of the note, and a note may be silent
        // there on purpose (a slow attack, a gated tremolo, sparse crackle). The release has been
        // told to stop; once its output has stayed under the floor for the window, the rest of
        // the scheduled tail is work that produces nothing: the voice ends here (see [culled]). Reverb and delay tails live on the cylinder buses and keep ringing; only
        // future ~zero sends are removed. A release that goes silent and comes back (a gated
        // tremolo: excluded by the factory; a sparse source inside an ignitor: `noCull()`) is the
        // author's call. A voice that has not sounded yet is not silent, it is late (see [heard]).
        if (measure) {
            val silent = blockCtx.voiceOutputPeak < VOICE_CULL_FLOOR

            if (!silent) {
                heard = true
            }

            if (heard && isReleasing) {
                if (releasing.countSilence(silent = silent, frames = length, windowFrames = cullWindowFrames)) {
                    culled = true
                    state = State.Done
                }
            }
        }
    }

    /**
     * The cut's ramp on this block's window: gain 1 up to the fade start, then linear to exact zero on the LAST
     * frame the voice renders, `ceil(fadeEnd) - 1` ([State.Fading.fadeEndFrame]; the voice is `Done` from the first
     * block that starts at or after the fade end, so that frame always renders), zero after it. The law of
     * `TeardownFadeRenderer` (both run `fadeToZero`), whose zero is `floor(endFrame) - 1`: the ramp spans the fade length minus one frame
     * (191 steps at 48 kHz). The clamps absorb a 1-ulp overshoot at the entry frame.
     */
    private fun applyCutFade() {
        val fadeStart = fading.fadeStartFrame
        val zeroFrame = ceil(fading.fadeEndFrame) - 1.0

        fadeToZero(
            buffer = blockCtx.audioBuffer,
            startIndex = blockCtx.offset,
            endIndex = blockCtx.windowEnd,
            // The buffer index at which the gain reaches zero (frame = blockStart + index).
            zeroIndex = zeroFrame - blockCtx.blockStart,
            scale = 1.0 / (zeroFrame - fadeStart).coerceAtLeast(1.0),
        )
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Nested types
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * A voice's lifecycle state, a sealed type (`docs/plans/effect-state-machines.md` §1, the maintainer's rule
     * of 2026-10-07): a state without data of its own is a `data object`; a state with data only it may see is a
     * class whose one instance is created with the voice; its `enter(...)` sets the state's data and returns the
     * state, and a transition is the one line `state = x.enter(...)`, so neither a transition nor a block
     * allocates. [render] dispatches with an exhaustive `when`. What stays on the voice because more than one
     * state reads it: the [heard] latch (`Sounding` and `Releasing`), the cull window, [culled] (the scheduler) and
     * the time limits ([VoiceLimits], the stages). Every transition: the table in the class KDoc.
     */
    sealed class State {
        /** No block has reached the onset yet: [render] renders nothing and keeps the voice. */
        data object Pending : State()

        /** The gate holds: the pipeline runs; the cull measures only until the voice has been heard. */
        data object Sounding : State()

        /**
         * The block starts at or after the gate end: the pipeline runs and the cull measures. Carries the cull's
         * count of consecutive silent release frames, which no other state reads.
         */
        class Releasing internal constructor() : State() {
            /** Consecutive release frames whose output stayed under the floor. Reset by any audible block. */
            private var silentFrames: Int = 0

            /** Entered once, from `Sounding`: the count starts at zero. Returns this state, for `state = ...`. */
            internal fun enter(): Releasing {
                silentFrames = 0

                return this
            }

            /**
             * Counts one release block of [frames] frames: a [silent] block adds them, an audible one resets the
             * count. True once the count reaches [windowFrames], the cull window (the voice then ends, [culled]).
             */
            internal fun countSilence(silent: Boolean, frames: Int, windowFrames: Int): Boolean {
                if (!silent) {
                    silentFrames = 0

                    return false
                }

                silentFrames += frames

                return silentFrames >= windowFrames
            }
        }

        /**
         * Cut by its group (terminal, [cutOff]): the pipeline runs with the cut's ramp before the send, and no cull
         * measurement. Carries the fade window, which the voice reads only while in this state (`internal`, because
         * an outer class cannot read a nested class's private members; the specs read it too).
         */
        class Fading internal constructor() : State() {
            /** Where the cut's fade begins (the cutting voice's onset, absolute frame; it may fall inside a block). */
            internal var fadeStartFrame: Double = Double.POSITIVE_INFINITY
                private set

            /**
             * Where the fade ends ([fadeStartFrame] plus the cut fade). The voice is `Done` from the first block that
             * starts at or after it; the ramp's exact zero lies on the last frame before it, `ceil(fadeEndFrame) - 1`,
             * which always renders. It does NOT move the voice's end frame (`TeardownFadeRenderer` reads that): the
             * voice ends here or at its own end, whichever block comes first.
             */
            internal var fadeEndFrame: Double = Double.POSITIVE_INFINITY
                private set

            /**
             * Entered once, by the cut ([cutOff]); `Fading` is terminal, so a second cut never re-enters it. Returns
             * this state, for `state = ...`.
             */
            internal fun enter(fadeStartFrame: Double, fadeEndFrame: Double): Fading {
                this.fadeStartFrame = fadeStartFrame
                this.fadeEndFrame = fadeEndFrame

                return this
            }
        }

        /**
         * Terminal: [render] returns false and the scheduler removes the voice. The ways in: the transition table in
         * the class KDoc.
         */
        data object Done : State()
    }

    /**
     * Rendering context shared across all voices during a processing block.
     */
    class RenderContext(
        val cylinders: Cylinders,
        val sampleRate: Int,
        val blockFrames: Int,
        val voiceBuffer: AudioBuffer,
        val freqModBuffer: DoubleArray,
        val scratchBuffers: ScratchBuffers,
    ) {
        // Absolute backend frame — Double, see RenderClock.cursorFrame.
        var blockStart: Double = 0.0
    }

    class Fm(
        val ratio: Double,
        val depth: Double,
        val envelope: Envelope,
        var modPhase: Double = 0.0,
    )

    /** [semitones] = total pitch glide over the voice, in SEMITONES (12 = one octave). */
    class Accelerate(val semitones: Double)

    /** @param rate LFO frequency in Hz. @param semitones modulation depth in SEMITONES. */
    class Vibrato(
        val rate: Double,
        val semitones: Double,
        var phase: Double = 0.0,
    )

    /**
     * The voice's pitch envelope (sprudel's `penv`): [semitones] = pitch shift at the envelope's peak, in
     * SEMITONES (`2^(semitones * level / 12)`), and [envelope] its stages and curves, the level law of
     * `EnvelopeCore`, the Ignitor pitch envelope's (phase 3 step 5b (c1)). The [Fm] shape.
     */
    class PitchEnvelope(
        val semitones: Double,
        val envelope: Envelope,
    )

    /** A modulation envelope of the voice's pitch pipeline (FM index, pitch envelope), in frames. */
    class Envelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val sustainLevel: Double,
        val releaseFrames: Double,
        val attackCurve: AdsrCurve = AdsrCurve.Default,
        val decayCurve: AdsrCurve = AdsrCurve.Default,
        val releaseCurve: AdsrCurve = AdsrCurve.Default,
    )

    companion object {
        // Monotonic voice-id source for [id]. Voice creation is single-threaded (render thread), so a plain
        // counter is enough. Wrap-safe (audit leftovers §3): after 2^31 ids it turns negative and an id repeats
        // only after 2^32. The one reader, `Cylinder.offer`, compares ids only between voices of the same onset
        // on one orbit, to order them; across the one wrap (2^31 voices, about a year of dense playing) one tie
        // would go the other way, inaudibly.
        private var idCounter: Int = 0
        private fun nextId(): Int = idCounter++
    }
}
