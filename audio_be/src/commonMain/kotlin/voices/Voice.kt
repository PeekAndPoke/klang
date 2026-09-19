/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.ignitor.AnalogDrift
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.send.SendRenderer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
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
 * Runs a composable [BlockRenderer] pipeline: **Pitch → Ignite → Filter → Send**
 */
class Voice(
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Identity — globally-unique, monotonic. Used by per-orbit effect ownership (VoiceLease) to tell voices
    // apart by value (not by object reference, which a future voice pool could recycle). Defaulted so every
    // constructed voice gets a fresh id. Voice creation is single-threaded (the render thread).
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    val id: Int = nextId(),

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Lifecycle & Routing
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Absolute backend frame — Double, see RenderClock.cursorFrame. Relative offsets stay Int.
    val startFrame: Double,
    // Mutable via [releaseGate] only (realtime note-off).
    endFrame: Double,
    gateEndFrame: Double,
    val cylinderId: Int,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Dynamics & Routing
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Since Katalyst step 5b-1 the orbit's bus reads its knobs from [katalystParams] alone, so most
    // of the block below has NO reader left. Who reads what, exactly:
    //
    //   gain, pan     SendRenderer, every block of every voice.
    //   delay, reverb nobody (since step 5b-2 the orbit's delay and reverb are fed from the orbit
    //                 mix by the owner's `wet` slot; the per-voice send amounts are gone).
    //   phaser        FilterPipelineBuilder, all five knobs, for the PER-VOICE phaser of a custom
    //                 pipeline that declares `StageDsl.Phaser`. No built-in preset does
    //                 (`PipelineDsl`, maintainer 2026-08-24: the phaser is a bus effect).
    //   compressor    nobody.
    //   ducking       nobody.
    //   body, vowel   nobody.
    //
    // They are still built by `VoiceFactory` and still carried on the wire; they go in step 5b-3.
    val gain: Double,
    val pan: Double,
    val compressor: Compressor?,
    val ducking: Ducking?,
    val delay: Delay,
    val reverb: Reverb,
    val phaser: Phaser,

    // Orbit-level resonators, moved off the per-voice filter chain in the 2026-07-03 orbit-Katalyst
    // work (docs/tasks-archive/2026-07/20260703-body-vowel-orbit-katalyst.md). The orbit reads its
    // resonators from the `body.*` / `vowel.*` slots now, so these two have no reader and leave with
    // the wire fields in step 5b-3.
    val body: FilterDef.Body? = null,
    val vowel: FilterDef.Formant? = null,

    /**
     * The orbit chain slots this voice writes (`VoiceData.katalystParams`), carried by REFERENCE:
     * the wire map is immutable by contract and the copy would be per voice, for a map only the
     * orbit's owner ever reads.
     *
     * While this voice holds the orbit's lease, the orbit's chain resolves every `Param` knob
     * against it (`KatalystChain.applyParams`), so this is the orbit's param state and it dies with
     * the voice. Null when the pattern wrote no slot, which is the same answer as an empty map: the
     * chain's authored defaults. Which chain reads which slot of it is one rule with one home, the
     * `katp` door's KDoc in `sprudel/lang/lang_katalyst.kt`: EVERY chain reads it, for every stage
     * it declares, the chain a cylinder is born with included (Katalyst step 5b-1). The bus FIELDS
     * on this class are not a knob source any more, and since step 5b-2 [delay] and [reverb] have
     * no reader either (the orbit's delay and reverb are fed from the orbit mix); they leave with
     * the wire fields in 5b-3.
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
    // Strip pipeline: Pitch → Ignite → Filter (Send is appended in init)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    pipeline: List<BlockRenderer>,

    // Pre-built BlockContext (created by VoiceFactory, mutated per block)
    private val blockCtx: BlockContext,

    // The baked main filter chain (LP/HP/BP/Notch/Formant), in the exact order received.
    // Exposed for tests that assert filter-bake ordering; not used during rendering
    // (the pipeline drives audio). Null when the voice has no main filter.
    internal val mainFilter: AudioFilter? = null,
) {
    /**
     * Voice death frame (gate end + release tail). Rewritten by a realtime note-off
     * ([releaseGate]) — earlier in every real case; an authored NEGATIVE release (raw-Motor,
     * passes through unclamped) can nudge it later by under a block, with no audible effect.
     */
    var endFrame: Double = endFrame
        private set

    /** Frame where release begins. Moves earlier on a realtime note-off ([releaseGate]). */
    private var gateEndFrame: Double = gateEndFrame

    // Full pipeline: Pitch → Ignite → Filter → Send
    private val pipeline: List<BlockRenderer> = pipeline + SendRenderer(voice = this)

    /**
     * True once this voice's release has stayed under [VOICE_CULL_FLOOR] for the whole cull window.
     * From then on [render] runs no strip: the voice is a ZOMBIE that only renews its orbit lease
     * and keeps its slot in the scheduler's active list until its scheduled [endFrame], where it
     * expires like any other voice. Staying in the list is the point: the orbit lease passes to
     * whichever voice renders FIRST after an owner dies, and that order is the active list, so an
     * early removal would reorder it and hand orbits to different successors (measured 2026-09-15
     * on Der Schmetterling: a culled hat changed which of guitar 3 and the bass owned orbit 3, at
     * -32 dBFS). The zombie's per-block cost is the lease renewal, and as the owner the bus config
     * re-application that comes with it, exactly what a sounding tail paid; the strip it skips is
     * the win.
     */
    var culled: Boolean = false
        private set

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
     * Releases the gate NOW (realtime note-off): every gate consumer sees the moved gate through
     * [BlockContext] / [io.peekandpoke.klang.audio_be.ignitor.IgniteContext] and the envelopes
     * enter their release from the current level (the release-from-history latch in the amp VCA
     * and the ignitor door). The release SPAN is untouched — only WHEN it begins moves.
     *
     * Deliberately untouched: `IgniteContext.voiceDurationFrames` (the `accelerate` glide base) —
     * see its KDoc; on held realtime voices `accelerate` is inert by decision.
     *
     * PRECONDITION: `atFrame >= startFrame` — the caller owns it (the scheduler floors at
     * `startFrame + blockFrames`, see `VoiceScheduler.releaseRealtimeVoice`). An earlier frame
     * would write a NEGATIVE ignitor-door gate and the ignitor envelope would release from
     * level 0 on its first sample: a silent voice, no exception, no error.
     */
    fun releaseGate(atFrame: Double) {
        // Natural gate is earlier — no-op (also makes a double-stop idempotent).
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
        gateEndFrame = atFrame
        endFrame = atFrame + releaseSpan

        blockCtx.gateEndFrame = gateEndFrame
        blockCtx.endFrame = endFrame

        // The ignitor door reads the gate voice-relative (Int) — move it too, or vca(on = false)
        // instruments would sustain through the tail and hit the teardown fade (amendment A1).
        blockCtx.signalCtx.gateEndFrame = (atFrame - startFrame).toInt()
    }

    /**
     * Renders the voice into the context's buffers.
     *
     * Runs the composable BlockRenderer pipeline: Pitch → Ignite → Filter → Send.
     *
     * @return true if the voice is still active, false if it has finished
     */
    fun render(ctx: RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames
        // Lifecycle check
        if (ctx.blockStart >= endFrame) return false
        if (blockEnd <= startFrame) return true

        // A culled voice renews its orbit lease and nothing else (see [culled]).
        if (culled) {
            ctx.cylinders.getOrInit(cylinderId, this, ctx.blockStart)

            return true
        }

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
        // voice pays nothing for the rest of its gate.
        val measure = cullWindowFrames >= 0 && (!heard || ctx.blockStart >= gateEndFrame)
        blockCtx.measurePeak = measure
        blockCtx.voiceOutputPeak = 0.0 // never a stale read from the previous block

        // ── Pitch → Ignite → Filter → Send ────────────────────────────────────────

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

            if (heard && ctx.blockStart >= gateEndFrame) {
                if (silent) {
                    silentFrames += length

                    if (silentFrames >= cullWindowFrames) {
                        culled = true
                    }
                } else {
                    silentFrames = 0
                }
            }
        }

        return true
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Nested types
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

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

    /** [semitones] = pitch shift at envelope peak, in SEMITONES (`2^(semitones·env/12)`). */
    class PitchEnvelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val releaseFrames: Double,
        val semitones: Double,
        val curve: Double,
        val anchor: Double,
    )

    class Envelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val sustainLevel: Double,
        val releaseFrames: Double,
        val attackCurve: AdsrCurve = AdsrCurve.Default,
        val decayCurve: AdsrCurve = AdsrCurve.Default,
        val releaseCurve: AdsrCurve = AdsrCurve.Default,
        var level: Double = 0.0,
        var releaseStartLevel: Double = 0.0,
        var releaseStarted: Boolean = false,
        // One-pole de-click smoother state on the final VCA gain (see envDeclickCoeff).
        // Rounds the slope-discontinuity ("corner") at segment joins that radiates a
        // click — most audible on low notes. `smoothPrimed` seeds it to the first
        // rendered gain so always-on voices and the note onset are not faded in.
        var smoothedLevel: Double = 0.0,
        var smoothPrimed: Boolean = false,
    ) {
        companion object {
            fun of(adsr: AdsrDef.Resolved, sampleRate: Int) = Envelope(
                attackFrames = adsr.attack * sampleRate,
                decayFrames = adsr.decay * sampleRate,
                sustainLevel = adsr.sustain,
                releaseFrames = adsr.release * sampleRate,
                attackCurve = adsr.attackCurve,
                decayCurve = adsr.decayCurve,
                releaseCurve = adsr.releaseCurve,
            )
        }
    }

    class Compressor(
        val thresholdDb: Double,
        val ratio: Double,
        val kneeDb: Double,
        val attackSeconds: Double,
        val releaseSeconds: Double,
    ) {
        companion object {
            /**
             * Builds per-voice compressor settings from the per-param wire fields (C0.2).
             * Null when no field is set; missing fields fall back to the classic defaults
             * (threshold -20 dB, ratio 4:1, knee 6 dB, attack 3 ms, release 100 ms).
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

    class FilterModulator(
        val filter: AudioFilter.Tunable,
        val envelope: Envelope,
        val depth: Double,
        val baseCutoff: Double,
        /**
         * Per-voice slow cutoff drift (OU process). When non-null, `FilterModRenderer`
         * advances the drift once per block and multiplies its output into the
         * envelope-derived cutoff. Set when the patch has `analog > 0`. See
         * [io.peekandpoke.klang.audio_bridge.constants.FILTER_DRIFT_RELATIVE_TO_OSC].
         */
        val drift: AnalogDrift? = null,
    )

    class Distort(val amount: Double, val shape: String = "soft", val oversample: Int = 0)
    class Crush(val amount: Double, val oversample: Int = 0)
    class Coarse(val amount: Double, val oversample: Int = 0)
    /** [floor] = minimum dry coefficient of the C4 wet/dry law; 1.0 (default) = purely additive. */
    class Phaser(val rate: Double, val depth: Double, val center: Double, val sweep: Double, val floor: Double = 1.0)
    /**
     * Per-voice tremolo, carried RAW: [rate] in Hz, [phase] as an authored cycle offset
     * (`0..1`), [skew] in `-1..+1` with 0 symmetric, [shape] a house waveform name (null =
     * sine). The unit conversions and the unknown-name fallback live in one place,
     * `TremoloRenderer`.
     */
    class Tremolo(
        val rate: Double, val depth: Double, val skew: Double, val phase: Double,
        val shape: String?,
    )

    class Delay(val amount: Double, val time: Double, val feedback: Double, val cap: Double = 1.0)
    /** [size] is normalized 0..1 (`Reverb.normalizeSize` ran in VoiceFactory); [lowpass] is the tail damping cutoff in Hz. */
    class Reverb(val amount: Double, val size: Double, val lowpass: Double? = null)

    companion object {
        // Monotonic voice-id source for [id]. Voice creation is single-threaded (render thread), so a plain
        // counter is enough; a wrap after 2^31 ids is harmless (identity only has to hold between two voices
        // that are co-active on the same orbit).
        private var idCounter: Int = 0
        private fun nextId(): Int = idCounter++
    }
}
