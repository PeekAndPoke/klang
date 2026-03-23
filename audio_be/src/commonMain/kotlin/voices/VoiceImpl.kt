package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.signalgen.SignalContext
import io.peekandpoke.klang.audio_be.signalgen.SignalGen
import io.peekandpoke.klang.audio_be.voices.Voice.*

/**
 * Concrete voice implementation.
 *
 * Rendering pipeline: **Pitch → Excite → Filter → Route**
 *
 * - **Pitch** stages (vibrato, accelerate, pitch envelope, FM) are still inline (Phase 3 of refactor)
 * - **Excite** stage delegates to [SignalGen]
 * - **Filter** stages are a composable [BlockRenderer] pipeline
 * - **Route** mixes to orbit (panning, sends)
 */
class VoiceImpl(
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Lifecycle & Routing
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val startFrame: Long,
    override val endFrame: Long,
    override val gateEndFrame: Long,
    override val orbitId: Int,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Synthesis & Pitch (TODO: extract to PitchRenderers in Phase 3)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val fm: Fm?,
    override val accelerate: Accelerate,
    override val vibrato: Vibrato,
    override val pitchEnvelope: PitchEnvelope?,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Dynamics & Routing
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val gain: Double,
    override val pan: Double,
    override val postGain: Double,
    override val compressor: Compressor?,
    override val ducking: Ducking?,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Time-Based Effects & Orbit Configuration
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val delay: Delay,
    override val reverb: Reverb,
    override val phaser: Phaser,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Cut group
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val cut: Int? = null,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Excite (Signal Generation)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    private val signal: SignalGen,
    private val signalCtx: SignalContext,
    private val freqHz: Double,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Filter pipeline (composable BlockRenderer chain)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    private val filterPipeline: List<BlockRenderer>,
) : Voice {

    // BlockContext created once, mutated per block
    private var blockCtx: BlockContext? = null

    // Dynamic gain multiplier (set by VoiceScheduler for smooth transitions, solo/mute, etc.)
    private var _gainMultiplier: Double = 1.0

    override val gainMultiplier: Double get() = _gainMultiplier

    override fun setGainMultiplier(multiplier: Double) {
        _gainMultiplier = multiplier
    }

    override fun render(ctx: RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames
        // Lifecycle check
        if (ctx.blockStart >= endFrame) return false
        if (blockEnd <= startFrame) return true

        val vStart = maxOf(ctx.blockStart, startFrame)
        val vEnd = minOf(blockEnd, endFrame)
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // ── Pitch (TODO: extract to BlockRenderer pipeline in Phase 3) ──────────

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

            val modFreq = freqHz * fmInstance.ratio
            val modInc = (io.peekandpoke.klang.audio_be.TWO_PI * modFreq) / ctx.sampleRate
            var modPhase = fmInstance.modPhase

            val envLevel = calculateFilterEnvelope(fmInstance.envelope, ctx)
            val effectiveDepth = fmInstance.depth * envLevel

            for (i in 0 until length) {
                val modSignal = kotlin.math.sin(modPhase) * effectiveDepth
                modPhase += modInc
                val fmMult = 1.0 + (modSignal / freqHz)
                buf[offset + i] *= fmMult
            }
            fmInstance.modPhase = modPhase % io.peekandpoke.klang.audio_be.TWO_PI
        }

        // ── Excite ──────────────────────────────────────────────────────────────

        signalCtx.offset = offset
        signalCtx.length = length
        signalCtx.voiceElapsedFrames = (ctx.blockStart - startFrame).toInt()
        signalCtx.phaseMod = modBuffer

        signal.generate(ctx.voiceBuffer, freqHz, signalCtx)

        // ── Filter pipeline ─────────────────────────────────────────────────────

        if (filterPipeline.isNotEmpty()) {
            val bCtx = blockCtx ?: BlockContext(
                audioBuffer = ctx.voiceBuffer,
                freqModBuffer = ctx.freqModBuffer,
                scratchBuffers = ctx.scratchBuffers,
                sampleRate = ctx.sampleRate,
                startFrame = startFrame,
                endFrame = endFrame,
                gateEndFrame = gateEndFrame,
                freqHz = freqHz,
                signal = signal,
                signalCtx = signalCtx,
                orbits = ctx.orbits,
            ).also { blockCtx = it }

            // Update per-block mutable state
            bCtx.audioBuffer = ctx.voiceBuffer
            bCtx.offset = offset
            bCtx.length = length
            bCtx.blockStart = ctx.blockStart

            for (renderer in filterPipeline) {
                renderer.render(bCtx)
            }
        }

        // ── Route ───────────────────────────────────────────────────────────────

        mixToOrbit(ctx, offset, length)

        return true
    }
}
