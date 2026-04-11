package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.send.SendRenderer
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope

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
    // Lifecycle & Routing
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    val startFrame: Int,
    val endFrame: Int,
    private val gateEndFrame: Int,
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
        val offset = vStart - ctx.blockStart
        val length = vEnd - vStart

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
        val voiceBuffer: FloatArray,
        val freqModBuffer: DoubleArray,
        val scratchBuffers: ScratchBuffers,
    ) {
        var blockStart: Int = 0
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
        var level: Double = 0.0,
        var releaseStartLevel: Double = 0.0,
        var releaseStarted: Boolean = false,
    ) {
        companion object {
            fun of(adsr: AdsrEnvelope.Resolved, sampleRate: Int) = Envelope(
                attackFrames = adsr.attack * sampleRate,
                decayFrames = adsr.decay * sampleRate,
                sustainLevel = adsr.sustain,
                releaseFrames = adsr.release * sampleRate,
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
    )

    class Distort(val amount: Double, val shape: String = "soft", val oversample: Int = 0)
    class Crush(val amount: Double, val oversample: Int = 0)
    class Coarse(val amount: Double, val oversample: Int = 0, var lastCoarseValue: Double = 0.0, var coarseCounter: Double = 0.0)
    class Phaser(val rate: Double, val depth: Double, val center: Double, val sweep: Double)
    class Tremolo(
        val rate: Double, val depth: Double, val skew: Double, val phase: Double,
        val shape: String?, var currentPhase: Double = 0.0,
    )

    class Delay(val amount: Double, val time: Double, val feedback: Double)
    class Reverb(
        val room: Double, val roomSize: Double, val roomFade: Double? = null,
        val roomLp: Double? = null, val roomDim: Double? = null, val iResponse: String? = null,
    )
}
