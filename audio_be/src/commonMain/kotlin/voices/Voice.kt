/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.send.SendRenderer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_SECONDS

// Frame counters use Int instead of Long: Long is boxed in Kotlin/JS (emulated via a wrapper
// object), causing heap allocation on every operation. Int maps directly to a JS number.
// At 48kHz with 128-sample blocks, Int overflows after ~12.4 hours — sufficient for any session.

/**
 * A voice in the audio engine.
 *
 * Runs a composable [BlockRenderer] pipeline: **Pitch → Ignite → (teardown fade) → Send**. The Ignitor tree the
 * ignite stage renders IS the instrument, envelope and filters included; the voice strip that used to run after
 * it retired in phase 3 step 9.
 *
 * **Lifecycle.** The voice is a state machine ([state], [State]): `Pending` until the block that holds its
 * onset, `Sounding` through the gate, `Releasing` once a block starts at or after the gate end, `Zombie` once the
 * release has stayed silent for the cull window, and `Done` from the first block that starts at or after
 * [endFrame]. [render] advances the state at the start of every block (the time-driven transitions) and then
 * dispatches on it; the cull measurement moves a `Releasing` voice to `Zombie` at the end of a block. Events from
 * outside arrive as methods, and the voice decides by its state whether they apply: the note-off
 * ([releaseGate]) and the hard kill ([kill], `Done` from any state). `Zombie` and `Done` are terminal: nothing
 * leads out of them but `Zombie` to `Done` (at [endFrame], or killed). The state describes a whole block, so a
 * block may still hold frames of the next phase (the gate end, the death frame) that the stages see frame by
 * frame. The plan: `docs/tasks/voice-lifecycle-state-machine.md`.
 */
class Voice(
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Identity — globally-unique, monotonic. Used by per-orbit effect ownership (VoiceLease) to tell voices
    // apart by value (not by object reference, which a future voice pool could recycle). Defaulted so every
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
     * While this voice holds the orbit's lease, the orbit's chain resolves every `Param` knob
     * against it (`KatalystChain.applyParams`), so this is the orbit's param state and it dies with
     * the voice. Null when the pattern wrote no slot, which is the same answer as an empty map: the
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

    // Full pipeline: Pitch → Ignite → (teardown fade) → Send
    private val pipeline: List<BlockRenderer> = pipeline + SendRenderer(voice = this)

    /**
     * The lifecycle state (see the class KDoc and [State]). [render] moves it at the start and the end of
     * a block; the events move it between blocks ([kill] sends it to `Done` at once). `Pending` until the
     * first block that reaches the onset.
     */
    var state: State = State.Pending
        private set

    /**
     * True while this voice is a [State.Zombie]: its release has stayed under [VOICE_CULL_FLOOR] for
     * the whole cull window. From then on [render] runs no stage: the zombie only renews its orbit
     * lease and keeps its slot in the scheduler's active list until its scheduled [endFrame], where
     * it turns `Done` and expires like any other voice. Staying in the list is the point: the orbit
     * lease passes to whichever voice renders FIRST after an owner dies, and that order is the active
     * list, so an early removal would reorder it and hand orbits to different successors (measured
     * 2026-09-15 on Der Schmetterling: a culled hat changed which of guitar 3 and the bass owned
     * orbit 3, at -32 dBFS). The zombie's per-block cost is the lease renewal, and as the owner the
     * bus config re-application that comes with it, exactly what a sounding tail paid; the stages it
     * skips are the win.
     *
     * A read of [state], not a latch: once the zombie is `Done` this is false again. The scheduler's
     * culled count reads it around one [render] call, and no call turns a voice into a zombie and
     * `Done` at once (`Done` is decided at the block's start, `Zombie` at its end).
     */
    val culled: Boolean get() = state == State.Zombie

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

    /** Consecutive release frames whose output stayed under the floor. Reset by any audible block. */
    private var silentFrames: Int = 0

    // Dynamic gain multiplier (set by VoiceScheduler for smooth transitions, solo/mute, etc.)
    private var _gainMultiplier: Double = 1.0

    val gainMultiplier: Double get() = _gainMultiplier

    fun setGainMultiplier(multiplier: Double) {
        _gainMultiplier = multiplier
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
     * The voice decides by its state whether the event applies. It applies to a `Pending` and a
     * `Sounding` voice; a `Sounding` voice whose gate moves into the past turns `Releasing` at the
     * start of the next block that starts at or after the new gate. A `Releasing` voice has been
     * released already, and `Zombie` and `Done` are terminal: they ignore it. Through the scheduler
     * that is what happened before the state existed: such a voice rendered a block that started at
     * or after its gate, and the scheduler releases at its cursor, which lies after that block's
     * start, so the natural-gate check below returned (a `Done` voice has left the active list).
     */
    fun releaseGate(atFrame: Double) {
        if (state != State.Pending && state != State.Sounding) {
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
     * The hard-kill event: the voice is `Done` NOW, from any state, and renders nothing more (no
     * fade: the caller is a teardown path, `VoiceScheduler.cleanupHard` at the end of the warmup
     * handshake). The scheduler removes `Done` voices; it never ends a
     * voice any other way (step 4 turns the cut, the one exception left, into an event too).
     */
    fun kill() {
        state = State.Done
    }

    /**
     * Renders the voice into the context's buffers.
     *
     * Advances the [state] for this block, then dispatches on it: `Pending` renders nothing,
     * `Sounding` and `Releasing` run the BlockRenderer pipeline (Pitch → Ignite → (teardown fade) →
     * Send), `Zombie` only renews the orbit lease, `Done` returns false.
     *
     * @return true if the voice is still active, false if it has finished (`Done`)
     */
    fun render(ctx: RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames

        advance(ctx.blockStart, blockEnd)

        return when (state) {
            State.Pending -> true

            State.Sounding -> {
                renderStages(ctx, blockEnd, releasing = false)

                true
            }

            State.Releasing -> {
                renderStages(ctx, blockEnd, releasing = true)

                true
            }

            // A zombie renews its orbit lease and nothing else (see [culled]).
            State.Zombie -> {
                ctx.cylinders.getOrInit(cylinderId, this, ctx.blockStart)

                true
            }

            State.Done -> false
        }
    }

    /**
     * The time-driven transitions, at the start of the block `[blockStart, blockEnd)`: `Done` from the
     * first block that starts at or after [endFrame] (from every state, a zombie included), `Sounding`
     * from the first block that ends after [startFrame], `Releasing` from the first block that starts at
     * or after [gateEndFrame]. A voice whose onset and gate end fall into one pending block passes
     * through `Sounding` to `Releasing` in one call. Forward only: no branch leaves `Done`, and a `Zombie`
     * only reaches `Done`.
     */
    private fun advance(blockStart: Double, blockEnd: Double) {
        if (blockStart >= endFrame) {
            state = State.Done

            return
        }

        if (state == State.Pending) {
            if (blockEnd <= startFrame) {
                return
            }

            state = State.Sounding
        }

        if (state == State.Sounding && blockStart >= gateEndFrame) {
            state = State.Releasing
        }
    }

    /**
     * One block of the pipeline, for a `Sounding` or a `Releasing` voice, and the cull measurement
     * that may turn a releasing voice into a `Zombie` at the block's end.
     */
    private fun renderStages(ctx: RenderContext, blockEnd: Double, releasing: Boolean) {
        val vStart = maxOf(ctx.blockStart, startFrame)
        val vEnd = minOf(blockEnd, endFrame)
        // Relative to this block / this voice — Int, and everything downstream of here is Int.
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // Update per-block state
        blockCtx.audioBuffer = ctx.voiceBuffer
        blockCtx.updateOffsetAndLength(offset, length)
        blockCtx.blockStart = ctx.blockStart
        blockCtx.renderContext = ctx
        blockCtx.freqModBufferWritten = false

        // Silence culling reads the output peak only on a cullable voice, and only while it is
        // needed: until the voice has been heard (the [heard] latch), then in the release. A heard
        // `Sounding` voice pays nothing for the rest of its gate.
        val measure = cullWindowFrames >= 0 && (!heard || releasing)
        blockCtx.measurePeak = measure
        blockCtx.voiceOutputPeak = 0.0 // never a stale read from the previous block

        // ── Pitch → Ignite → (teardown fade) → Send ───────────────────────────────

        for (renderer in pipeline) {
            renderer.render(blockCtx)
        }

        // ── Silence culling ───────────────────────────────────────────────────────
        // Only in the release: the gate is the held part of the note, and a note may be silent
        // there on purpose (a slow attack, a gated tremolo, sparse crackle). The release has been
        // told to stop; once its output has stayed under the floor for the window, the rest of
        // the scheduled tail is work that produces nothing: the voice turns into a zombie (see
        // [culled]). Reverb and delay tails live on the cylinder buses and keep ringing; only
        // future ~zero sends are removed. A release that goes silent and comes back (a gated
        // tremolo: excluded by the factory; a sparse source inside an ignitor: `noCull()`) is the
        // author's call. A voice that has not sounded yet is not silent, it is late (see [heard]).
        if (measure) {
            val silent = blockCtx.voiceOutputPeak < VOICE_CULL_FLOOR

            if (!silent) {
                heard = true
            }

            if (heard && releasing) {
                if (silent) {
                    silentFrames += length

                    if (silentFrames >= cullWindowFrames) {
                        state = State.Zombie
                    }
                } else {
                    silentFrames = 0
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Nested types
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * A voice's lifecycle state (a closed, param-less set: an enum, no allocation per block). Who moves it
     * and when: the class KDoc and `docs/tasks/voice-lifecycle-state-machine.md`.
     */
    enum class State {
        /** No block has reached the onset yet: [render] renders nothing and keeps the voice. */
        Pending,

        /** The gate holds: the pipeline runs; the cull measures only until the voice has been heard. */
        Sounding,

        /** The block starts at or after the gate end: the pipeline runs and the cull measures. */
        Releasing,

        /** Culled (terminal): renders nothing, renews its orbit lease until [endFrame] (see [culled]). */
        Zombie,

        /** At or past [endFrame], or killed (terminal): [render] returns false and the scheduler removes it. */
        Done,
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

    class Compressor(
        val thresholdDb: Double,
        val ratio: Double,
        val kneeDb: Double,
        val attackSeconds: Double,
        val releaseSeconds: Double,
    ) {
        companion object {
            /**
             * Builds the orbit compressor's settings from its five knobs, as
             * `KatalystSlots.compressorSettings` resolves them from the owner's slots (a
             * non-finite slot arrives here as null). Null when no knob is set; a missing knob
             * falls back to its `COMPRESSOR_*` constant.
             */
            fun fromParams(
                threshold: Double?,
                ratio: Double?,
                knee: Double?,
                attack: Double?,
                release: Double?,
            ): Compressor? {
                if (threshold == null && ratio == null && knee == null && attack == null && release == null) {
                    return null
                }
                return Compressor(
                    thresholdDb = threshold ?: COMPRESSOR_THRESHOLD_DB,
                    ratio = ratio ?: COMPRESSOR_RATIO,
                    kneeDb = knee ?: COMPRESSOR_KNEE_DB,
                    attackSeconds = attack ?: COMPRESSOR_ATTACK_SECONDS,
                    releaseSeconds = release ?: COMPRESSOR_RELEASE_SECONDS,
                )
            }
        }
    }

    class Ducking(
        val cylinderId: Int,
        val attackSeconds: Double,
        val depth: Double,
    )

    companion object {
        // Monotonic voice-id source for [id]. Voice creation is single-threaded (render thread), so a plain
        // counter is enough. Wrap-safe (audit leftovers §3): after 2^31 ids it turns negative and an id repeats
        // only after 2^32. The one reader, `VoiceLease`, compares ids for EQUALITY between voices co-active on
        // one orbit and has no sentinel id (`VoiceLeaseSpec` pins an owner with id -1), so neither a negative id
        // nor a repeat 2^32 voices later can be mistaken for a live owner.
        private var idCounter: Int = 0
        private fun nextId(): Int = idCounter++
    }
}
