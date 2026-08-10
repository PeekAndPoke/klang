/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
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
    val endFrame: Double,
    private val gateEndFrame: Double,
    val cylinderId: Int,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Dynamics & Routing (used by SendRenderer and Cylinder configuration)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    val gain: Double,
    val pan: Double,
    val postGain: Double,
    val compressor: Compressor?,
    val ducking: Ducking?,
    val delay: Delay,
    val reverb: Reverb,
    val phaser: Phaser,

    // Orbit-level resonators — carried here (not baked into the per-voice filter chain) so the
    // Cylinder can configure its body/vowel Katalyst from the voice. See docs/tasks/body-vowel-to-orbit-katalyst.md.
    val body: FilterDef.Body? = null,
    val vowel: FilterDef.Formant? = null,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Cut group
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    val cut: Int? = null,

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
    // Full pipeline: Pitch → Ignite → Filter → Send
    private val pipeline: List<BlockRenderer> = pipeline + SendRenderer(voice = this)

    // Dynamic gain multiplier (set by VoiceScheduler for smooth transitions, solo/mute, etc.)
    private var _gainMultiplier: Double = 1.0

    val gainMultiplier: Double get() = _gainMultiplier

    fun setGainMultiplier(multiplier: Double) {
        _gainMultiplier = multiplier
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

        val vStart = maxOf(ctx.blockStart, startFrame)
        val vEnd = minOf(blockEnd, endFrame)
        // Relative to this block / this voice — Int, and everything downstream of here is Int.
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // Update per-block state
        blockCtx.audioBuffer = ctx.voiceBuffer
        blockCtx.offset = offset
        blockCtx.length = length
        blockCtx.blockStart = ctx.blockStart
        blockCtx.renderContext = ctx
        blockCtx.freqModBufferWritten = false

        // ── Pitch → Ignite → Filter → Send ────────────────────────────────────────

        for (renderer in pipeline) {
            renderer.render(blockCtx)
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

    class Accelerate(val amount: Double)

    /** @param rate LFO frequency in Hz. @param depth modulation depth in semitones. */
    class Vibrato(
        val rate: Double,
        val depth: Double,
        var phase: Double = 0.0,
    )

    class PitchEnvelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val releaseFrames: Double,
        val amount: Double,
        val curve: Double,
        val anchor: Double,
    )

    class Envelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val sustainLevel: Double,
        val releaseFrames: Double,
        val attackCurve: AdsrCurve = AdsrCurve.Exponential,
        val decayCurve: AdsrCurve = AdsrCurve.Exponential,
        val releaseCurve: AdsrCurve = AdsrCurve.Exponential,
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
            fun fromStringConfig(config: String?): Compressor? {
                val settings = config?.let {
                    io.peekandpoke.klang.audio_be.effects.Compressor.parseSettings(it)
                } ?: return null
                return Compressor(
                    thresholdDb = settings.thresholdDb,
                    ratio = settings.ratio,
                    kneeDb = settings.kneeDb,
                    attackSeconds = settings.attackSeconds,
                    releaseSeconds = settings.releaseSeconds,
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
         * [io.peekandpoke.klang.audio_be.filters.FILTER_DRIFT_RELATIVE_TO_OSC].
         */
        val drift: AnalogDrift? = null,
    )

    class Distort(val amount: Double, val shape: String = "soft", val oversample: Int = 0)
    class Crush(val amount: Double, val oversample: Int = 0)
    class Coarse(val amount: Double, val oversample: Int = 0, var lastCoarseValue: Double = 0.0, var coarseCounter: Double = 0.0)
    class Phaser(val rate: Double, val depth: Double, val center: Double, val sweep: Double)
    class Tremolo(
        val rate: Double, val depth: Double, val skew: Double, val phase: Double,
        val shape: String?, var currentPhase: Double = 0.0,
    )

    class Delay(val amount: Double, val time: Double, val feedback: Double, val cap: Double = 1.0)
    class Reverb(
        val room: Double, val roomSize: Double, val roomFade: Double? = null,
        val roomLp: Double? = null, val roomDim: Double? = null, val iResponse: String? = null,
    )

    companion object {
        // Monotonic voice-id source for [id]. Voice creation is single-threaded (render thread), so a plain
        // counter is enough; a wrap after 2^31 ids is harmless (identity only has to hold between two voices
        // that are co-active on the same orbit).
        private var idCounter: Int = 0
        private fun nextId(): Int = idCounter++
    }
}
