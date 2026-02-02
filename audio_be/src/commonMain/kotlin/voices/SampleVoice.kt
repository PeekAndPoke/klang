package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.effects.*
import io.peekandpoke.klang.audio_be.voices.Voice.Phaser
import io.peekandpoke.klang.audio_be.voices.Voice.Tremolo
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import kotlin.math.max
import kotlin.math.min

/**
 * Sample voice - plays back pre-recorded audio samples.
 * Implements the full audio pipeline with sample playback, looping, and effects.
 */
class SampleVoice(
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
    // SampleVoice-Specific Parameters
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    /** Sample playback configuration (looping, cut groups, etc.) */
    val samplePlayback: SamplePlayback,
    /** Sample PCM data to play */
    val sample: MonoSamplePcm,
    /** Playback rate multiplier (includes pitch shifting) */
    val rate: Double,
    /** Current playhead position in samples */
    var playhead: Double,
) : Voice {

    /**
     * Sample playback configuration.
     * Controls looping behavior, cut groups, and playback boundaries.
     */
    class SamplePlayback(
        /** Cut group ID (voices in same group cut each other) */
        val cut: Int?,
        /** Whether explicit looping is enabled */
        val explicitLooping: Boolean,
        /** Loop start point in samples */
        val explicitLoopStart: Double,
        /** Loop end point in samples */
        val explicitLoopEnd: Double,
        /** Stop frame (for .end() slicing) */
        val stopFrame: Double,
    ) {
        companion object {
            val default = SamplePlayback(
                cut = null,
                explicitLooping = false,
                explicitLoopStart = -1.0,
                explicitLoopEnd = -1.0,
                stopFrame = Double.MAX_VALUE
            )
        }
    }

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

    // Pre-calculate loop points
    private val loopStart = if (samplePlayback.explicitLooping) {
        samplePlayback.explicitLoopStart
    } else {
        sample.meta.loop?.start?.toDouble() ?: -1.0
    }

    private val loopEnd = if (samplePlayback.explicitLooping) {
        samplePlayback.explicitLoopEnd
    } else {
        sample.meta.loop?.end?.toDouble() ?: -1.0
    }

    private val isLooping = loopStart >= 0.0 && loopEnd > loopStart

    override fun render(ctx: Voice.RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames
        if (ctx.blockStart >= endFrame) return false
        if (blockEnd <= startFrame) return true

        val vStart = max(ctx.blockStart, startFrame)
        val vEnd = min(blockEnd, endFrame)
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // Apply filter modulation (control rate - once per block)
        for (mod in filterModulators) {
            val envValue = calculateFilterEnvelope(mod.envelope, ctx)
            val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue)
            mod.filter.setCutoff(newCutoff)
        }

        // 1. Calculate pitch modulation
        val modBuffer = fillPitchModulation(ctx, offset, length)
        val pcm = sample.pcm
        val pcmMax = pcm.size - 1
        val out = ctx.voiceBuffer

        // 2. Generate sample output (with pitch modulation)
        var ph = playhead
        for (i in 0 until length) {
            val idxOut = offset + i

            // Loop Logic
            if (isLooping && ph >= loopEnd) {
                ph = loopStart + (ph - loopEnd)
            }

            // Check strict end
            if (ph >= samplePlayback.stopFrame) {
                out[idxOut] = 0.0
            } else {
                // Read Sample
                val base = ph.toInt()

                if (base >= pcmMax) {
                    out[idxOut] = 0.0
                } else if (base >= 0) {
                    val frac = ph - base.toDouble()
                    val a = pcm[base]
                    val b = pcm[base + 1]
                    val sampleValue = a + (b - a) * frac

                    out[idxOut] = sampleValue
                }
            }

            // Advance Playhead
            ph += if (modBuffer != null) rate * modBuffer[idxOut] else rate
        }

        playhead = ph

        // 3. Pre-Filters (Destructive: Crush, Coarse)
        for (fx in preFilters) {
            fx.process(ctx.voiceBuffer, offset, length)
        }

        // 4. Main Filter (Subtractive)
        filter.process(ctx.voiceBuffer, offset, length)

        // 5. VCA / Envelope (Dynamics)
        applyEnvelope(ctx, offset, length)

        // 6. Post-Filters (Color & Modulation)
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

        // 7. Mix to orbit (now simplified)
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
