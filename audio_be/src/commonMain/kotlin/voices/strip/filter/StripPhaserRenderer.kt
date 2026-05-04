package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.effects.PhaserCore
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Per-voice phaser — thin [BlockRenderer] wrapper around a single mono [PhaserCore].
 *
 * **Output**: additive — `output = dry + wet · depth`. Matches the cylinder-bus
 * Phaser and the strudel-side `phaserDepth` semantic where `depth` adds an
 * effect on top of the source. The Ignitor-DSL phaser uses crossfade with a
 * differently-named `blend` parameter; they share `PhaserCore` for the
 * per-sample math but differ in output mixing.
 *
 * `depth = 0` bypasses entirely.
 */
class StripPhaserRenderer(
    rate: Double,
    private val depth: Double,
    center: Double,
    sweep: Double,
    sampleRate: Int,
) : BlockRenderer {
    private val core = PhaserCore(PhaserCore.DEFAULT_STAGES, sampleRate).apply {
        this.rate = rate
        this.center = center
        this.sweep = sweep
        // feedback uses PhaserCore's default (0.5) — voice-side phaser doesn't
        // expose feedback as a per-note parameter today.
    }

    override fun render(ctx: BlockContext) {
        if (depth <= 0.0) return

        val buf = ctx.audioBuffer
        val d = depth

        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i
            val dry = buf[idx]
            val wet = core.step(dry)
            buf[idx] = dry + wet * d
        }
    }
}
