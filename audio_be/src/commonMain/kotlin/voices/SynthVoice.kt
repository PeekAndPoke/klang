package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.effects.*
import io.peekandpoke.klang.audio_be.osci.OscFn
import io.peekandpoke.klang.audio_be.voices.Voice.Phaser
import io.peekandpoke.klang.audio_be.voices.Voice.Tremolo

/**
 * Synthesizer voice - generates audio from oscillators.
 * Implements the full audio pipeline from oscillator generation to final mixing.
 */
class SynthVoice(
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
    override val fm: Voice.Fm?,
    override val accelerate: Voice.Accelerate,
    override val vibrato: Voice.Vibrato,
    override val pitchEnvelope: Voice.PitchEnvelope?,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Dynamics
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val gain: Double,
    override val pan: Double,
    override val postGain: Double,
    override val envelope: Voice.Envelope,
    override val compressor: Voice.Compressor?,
    override val ducking: Voice.Ducking?,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Filters & Modulation
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val filter: AudioFilter,
    override val filterModulators: List<Voice.FilterModulator>,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Time-Based Effects
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val delay: Voice.Delay,
    override val reverb: Voice.Reverb,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Raw Effect Data
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val phaser: Phaser,
    override val tremolo: Tremolo,
    override val distort: Voice.Distort,
    override val crush: Voice.Crush,
    override val coarse: Voice.Coarse,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // SynthVoice-Specific Parameters
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    /** Oscillator function for waveform generation */
    val osc: OscFn,
    /** Base frequency in Hz */
    val freqHz: Double,
    /** Phase increment per sample */
    val phaseInc: Double,
    /** Current oscillator phase */
    var phase: Double = 0.0,
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
    // Note: Tremolo and Phaser are handled separately due to lazy initialization
    override val postFilters: List<AudioFilter> = listOfNotNull(
        fxDistortion
    )

    override fun render(ctx: Voice.RenderContext): Boolean {
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
        if (fm != null && fm.depth != 0.0) {
            val buf = modBuffer ?: ctx.freqModBuffer
            if (modBuffer == null) {
                for (i in 0 until length) buf[offset + i] = 1.0
                modBuffer = buf
            }

            val modFreq = freqHz * fm.ratio
            val modInc = (io.peekandpoke.klang.audio_be.TWO_PI * modFreq) / ctx.sampleRate
            var modPhase = fm.modPhase

            val envLevel = calculateFilterEnvelope(fm.envelope, ctx)
            val effectiveDepth = fm.depth * envLevel

            for (i in 0 until length) {
                val modSignal = kotlin.math.sin(modPhase) * effectiveDepth
                modPhase += modInc
                val fmMult = 1.0 + (modSignal / freqHz)
                buf[offset + i] *= fmMult
            }
            fm.modPhase = modPhase
        }

        // 3. Generate oscillator output
        phase = osc.process(
            buffer = ctx.voiceBuffer,
            offset = offset,
            length = length,
            phase = phase,
            phaseInc = phaseInc,
            phaseMod = modBuffer
        )

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

        // 8. Mix to orbit (now simplified - just panning, sends, and summing)
        mixToOrbit(ctx, offset, length)

        return true
    }

    /**
     * Apply ADSR envelope to the voice buffer.
     * This is the VCA (Voltage Controlled Amplifier) stage.
     */
    private fun applyEnvelope(ctx: Voice.RenderContext, offset: Int, length: Int) {
        val env = envelope
        val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
        val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
        val relRate = if (env.releaseFrames > 0) env.sustainLevel / env.releaseFrames else 1.0

        var absPos = (ctx.blockStart + offset) - startFrame
        val gateEndPos = gateEndFrame - startFrame
        var currentEnv = env.level

        for (i in 0 until length) {
            val idx = offset + i

            // Calculate envelope value
            currentEnv = if (absPos >= gateEndPos) {
                // Release phase
                val relPos = absPos - gateEndPos
                env.sustainLevel - (relPos * relRate)
            } else {
                // Attack/Decay/Sustain phases
                when {
                    absPos < env.attackFrames -> absPos * attRate
                    absPos < env.attackFrames + env.decayFrames -> {
                        val decPos = absPos - env.attackFrames
                        1.0 - (decPos * decRate)
                    }

                    else -> env.sustainLevel
                }
            }

            if (currentEnv < 0.0) currentEnv = 0.0

            // Apply envelope
            ctx.voiceBuffer[idx] *= currentEnv

            absPos++
        }

        // Update envelope state
        env.level = currentEnv
    }
}
