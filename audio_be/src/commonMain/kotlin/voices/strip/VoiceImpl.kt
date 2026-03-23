package io.peekandpoke.klang.audio_be.voices.strip

import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.Voice.*
import io.peekandpoke.klang.audio_be.voices.mixToOrbit

/**
 * Concrete voice implementation.
 *
 * Runs a composable [BlockRenderer] pipeline: **Pitch → Excite → Filter**
 * then routes the result to the orbit mixer.
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
    // Dynamics & Routing (used by mixToOrbit and Orbit configuration)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val gain: Double,
    override val pan: Double,
    override val postGain: Double,
    override val compressor: Compressor?,
    override val ducking: Ducking?,
    override val delay: Delay,
    override val reverb: Reverb,
    override val phaser: Phaser,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Cut group
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    override val cut: Int? = null,

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Strip pipeline: Pitch → Excite → Filter
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    private val pipeline: List<BlockRenderer>,

    // Pre-built BlockContext (created by VoiceScheduler, mutated per block)
    private val blockCtx: BlockContext,
) : Voice {

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

        // Update per-block state
        blockCtx.audioBuffer = ctx.voiceBuffer
        blockCtx.offset = offset
        blockCtx.length = length
        blockCtx.blockStart = ctx.blockStart
        blockCtx.freqModBufferWritten = false

        // ── Pitch → Excite → Filter ─────────────────────────────────────────────

        for (renderer in pipeline) {
            renderer.render(blockCtx)
        }

        // ── Route ───────────────────────────────────────────────────────────────

        mixToOrbit(ctx, offset, length)

        return true
    }
}
