package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.effects.*
import io.peekandpoke.klang.audio_be.voices.Voice.*

/**
 * Abstract base class for all voice implementations.
 * Contains the complete audio processing pipeline shared by SynthVoice and SampleVoice.
 *
 * Subclasses only need to implement generateSignal() which fills the buffer with
 * the raw audio (oscillator output or sample playback).
 */
abstract class AbstractVoice(
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Lifecycle & Routing
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val startFrame: Long,
    override val endFrame: Long,
    override val gateEndFrame: Long,
    override val orbitId: Int,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Synthesis & Pitch
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val fm: Fm?,
    override val accelerate: Accelerate,
    override val vibrato: Vibrato,
    override val pitchEnvelope: PitchEnvelope?,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Dynamics
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val gain: Double,
    override val pan: Double,
    override val postGain: Double,
    override val envelope: Envelope,
    override val compressor: Compressor?,
    override val ducking: Ducking?,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Filters & Modulation
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val filter: AudioFilter,
    override val filterModulators: List<FilterModulator>,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Time-Based Effects
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val delay: Delay,
    override val reverb: Reverb,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Raw Effect Data
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val phaser: Phaser,
    override val tremolo: Tremolo,
    override val distort: Distort,
    override val crush: Crush,
    override val coarse: Coarse,
) : Voice {

    // Initialize effect filters
    private val fxCrush = if (crush.amount > 0.0) BitCrushFilter(crush.amount) else null
    private val fxCoarse = if (coarse.amount > 1.0) SampleRateReducerFilter(coarse.amount) else null
    private val fxDistortion = if (distort.amount > 0.0) DistortionFilter(distort.amount) else null

    // Lazily initialized filters that need sampleRate
    private var lazyTremolo: TremoloFilter? = null
    private var lazyPhaser: PhaserFilter? = null

    // Pre-filters: Destructive effects applied before main filter
    override val preFilters: List<AudioFilter> = listOfNotNull(
        fxCrush,
        fxCoarse
    )

    // Post-filters: Color/modulation effects applied after envelope
    override val postFilters: List<AudioFilter> = listOfNotNull(
        fxDistortion
    )

    /**
     * Generates the raw audio signal into ctx.voiceBuffer.
     * This is the only method subclasses need to implement.
     *
     * @param ctx Render context with buffers and configuration
     * @param offset Start position in buffer
     * @param length Number of frames to generate
     * @param pitchMod Pitch modulation buffer (null if no modulation active)
     */
    protected abstract fun generateSignal(
        ctx: RenderContext,
        offset: Int,
        length: Int,
        pitchMod: DoubleArray?,
    )

    override fun render(ctx: RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames
        // Lifecycle check
        if (ctx.blockStart >= endFrame) return false
        if (blockEnd <= startFrame) return true

        val vStart = maxOf(ctx.blockStart, startFrame)
        val vEnd = minOf(blockEnd, endFrame)
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // Apply filter modulation (control rate - once per block)
        for (mod in filterModulators) {
            val envValue = calculateFilterEnvelope(mod.envelope, ctx)
            val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue)
            mod.filter.setCutoff(newCutoff)
        }

        // 1. Calculate base pitch modulation (Vibrato, Pitch Env, Slide)
        var modBuffer = fillPitchModulation(ctx, offset, length)

        // 2. Apply FM (Frequency Modulation)
        val fmInstance = fm
        if (fmInstance != null && fmInstance.depth != 0.0) {
            val buf = modBuffer ?: ctx.freqModBuffer
            if (modBuffer == null) {
                for (i in 0 until length) buf[offset + i] = 1.0
                modBuffer = buf
            }

            // Calculate Modulator Parameters
            val modFreq = getBaseFrequency() * fmInstance.ratio
            val modInc = (io.peekandpoke.klang.audio_be.TWO_PI * modFreq) / ctx.sampleRate
            var modPhase = fmInstance.modPhase

            val envLevel = calculateFilterEnvelope(fmInstance.envelope, ctx)
            val effectiveDepth = fmInstance.depth * envLevel

            for (i in 0 until length) {
                val modSignal = kotlin.math.sin(modPhase) * effectiveDepth
                modPhase += modInc
                val fmMult = 1.0 + (modSignal / getBaseFrequency())
                buf[offset + i] *= fmMult
            }
            fmInstance.modPhase = modPhase
        }

        // 3. Generate signal (delegate to subclass)
        generateSignal(ctx, offset, length, modBuffer)

        // 4. Pre-Filters (Destructive: Crush, Coarse)
        for (fx in preFilters) {
            fx.process(ctx.voiceBuffer, offset, length)
        }

        // 5. Main Filter (Subtractive)
        filter.process(ctx.voiceBuffer, offset, length)

        // 6. VCA / Envelope (Dynamics)
        applyEnvelope(ctx, offset, length)

        // 7. Post-Filters (Color & Modulation)
        for (fx in postFilters) {
            fx.process(ctx.voiceBuffer, offset, length)
        }

        // Tremolo (with lazy initialization)
        if (tremolo.depth > 0.0) {
            if (lazyTremolo == null) {
                lazyTremolo = TremoloFilter(tremolo.rate, tremolo.depth, ctx.sampleRate)
            }
            lazyTremolo?.process(ctx.voiceBuffer, offset, length)
        }

        // Phaser (with lazy initialization)
        if (phaser.depth > 0.0) {
            if (lazyPhaser == null) {
                lazyPhaser = PhaserFilter(
                    rate = phaser.rate,
                    depth = phaser.depth,
                    center = if (phaser.center > 0) phaser.center else 1000.0,
                    sweep = if (phaser.sweep > 0) phaser.sweep else 1000.0,
                    sampleRate = ctx.sampleRate
                )
            }
            lazyPhaser?.process(ctx.voiceBuffer, offset, length)
        }

        // 8. Mix to orbit (panning, sends, and summing)
        mixToOrbit(ctx, offset, length)

        return true
    }

    /**
     * Get the base frequency for FM calculation.
     * SynthVoice returns oscillator frequency, SampleVoice returns sample pitch.
     */
    protected abstract fun getBaseFrequency(): Double

    /**
     * Apply ADSR envelope to the voice buffer.
     * This is the VCA (Voltage Controlled Amplifier) stage.
     */
    protected fun applyEnvelope(ctx: RenderContext, offset: Int, length: Int) {
        val env = envelope
        val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
        val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
        val relRateDen = if (env.releaseFrames > 0) env.releaseFrames else 1.0

        var absPos = (ctx.blockStart + offset) - startFrame
        val gateEndPos = gateEndFrame - startFrame
        var currentEnv = env.level

        for (i in 0 until length) {
            val idx = offset + i

            if (absPos >= gateEndPos) {
                // Release phase
                if (!env.releaseStarted) {
                    env.releaseStartLevel = currentEnv
                    env.releaseStarted = true
                }
                val relPos = absPos - gateEndPos
                val relRate = env.releaseStartLevel / relRateDen
                currentEnv = env.releaseStartLevel - (relPos * relRate)
            } else {
                // Attack/Decay/Sustain phases
                env.releaseStarted = false
                currentEnv = when {
                    absPos < env.attackFrames -> absPos * attRate
                    absPos < env.attackFrames + env.decayFrames -> {
                        val decPos = absPos - env.attackFrames
                        1.0 - (decPos * decRate)
                    }
                    else -> env.sustainLevel
                }
            }

            if (currentEnv < 0.0) currentEnv = 0.0

            ctx.voiceBuffer[idx] *= currentEnv
            absPos++
        }

        // Update envelope state
        env.level = currentEnv
    }
}
