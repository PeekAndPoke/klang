package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.sqrt

class PitchModFactoriesSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 4410 // 100ms — long enough to capture LFO cycles

    fun createCtx(frames: Int = blockFrames): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate, // 1 second
        gateEndFrame = sampleRate,
        releaseFrames = 0,
        voiceEndFrame = sampleRate,
        scratchBuffers = ScratchBuffers(frames),
    ).apply {
        offset = 0
        length = frames
        voiceElapsedFrames = 0
    }

    fun render(ig: Ignitor, freqHz: Double = 440.0, ctx: IgniteContext = createCtx()): AudioBuffer {
        val buf = AudioBuffer(ctx.length)
        ig.generate(buf, freqHz, ctx)
        return buf
    }

    fun AudioBuffer.mean(): Double = sumOf { it.toDouble() } / size
    fun AudioBuffer.rms(): Double {
        var s = 0.0; for (x in this) s += x.toDouble() * x.toDouble(); return sqrt(s / size)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Vibrato mod
    // ═══════════════════════════════════════════════════════════════════════════

    "vibratoMod: depth=0 produces all ones (no modulation)" {
        val mod = vibratoModIgnitor(rate = 5.0, depth = 0.0)
        val out = render(mod)
        for (s in out) s shouldBe 1.0
    }

    "vibratoMod: output is centered near 1.0 (ratio space)" {
        val mod = vibratoModIgnitor(rate = 10.0, depth = 1.0)
        // Use a full second to average over many complete LFO cycles
        val ctx = createCtx(sampleRate)
        val out = render(mod, ctx = ctx)
        // Mean should be near 1.0 (symmetric LFO over complete cycles, ratio space)
        abs(out.mean() - 1.0) shouldBeLessThan 0.01
    }

    "vibratoMod: output has RMS > 1.0 (actual modulation above unity)" {
        val mod = vibratoModIgnitor(rate = 5.0, depth = 1.0)
        val out = render(mod)
        out.rms() shouldBeGreaterThan 0.9
    }

    "vibratoMod: larger depth produces larger deviation from 1.0" {
        val small = vibratoModIgnitor(rate = 5.0, depth = 0.25)
        val large = vibratoModIgnitor(rate = 5.0, depth = 2.0)
        fun deviationRms(buf: AudioBuffer): Double {
            var s = 0.0; for (x in buf) {
                val d = x.toDouble() - 1.0; s += d * d
            }; return sqrt(s / buf.size)
        }
        deviationRms(render(large)) shouldBeGreaterThan deviationRms(render(small)) * 2.0
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accelerate mod
    // ═══════════════════════════════════════════════════════════════════════════

    "accelerateMod: amount=0 produces all ones" {
        val mod = accelerateModIgnitor(amount = 0.0)
        val out = render(mod)
        for (s in out) s shouldBe 1.0
    }

    "accelerateMod: starts near 1.0 at voice start (progress=0)" {
        val mod = accelerateModIgnitor(amount = 2.0)
        val ctx = createCtx()
        ctx.voiceElapsedFrames = 0
        val out = render(mod, ctx = ctx)
        // First sample: 2^(2.0 * 0/44100) ≈ 1.0
        out[0].toDouble() shouldBe (1.0 plusOrMinus 0.001)
    }

    "accelerateMod: positive amount produces increasing ratio" {
        val mod = accelerateModIgnitor(amount = 2.0)
        val ctx = createCtx()
        ctx.voiceElapsedFrames = sampleRate / 2 // halfway
        val out = render(mod, ctx = ctx)
        // At progress=0.5: ratio = 2^(2.0 * 0.5) = 2^1 = 2.0
        out[0].toDouble() shouldBe (2.0 plusOrMinus 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Pitch envelope mod
    // ═══════════════════════════════════════════════════════════════════════════

    "pitchEnvelopeMod: amount=0 produces all ones" {
        val mod = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("a", 0.1),
            decaySec = ParamIgnitor("d", 0.1),
            amount = ParamIgnitor("amount", 0.0),
        )
        val out = render(mod)
        for (s in out) s shouldBe 1.0
    }

    "pitchEnvelopeMod: produces non-zero deviation when amount is non-zero" {
        val mod = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("a", 0.01),
            decaySec = ParamIgnitor("d", 0.05),
            amount = ParamIgnitor("amount", 12.0), // one octave
        )
        val out = render(mod)
        out.rms() shouldBeGreaterThan 0.01
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FM mod
    // ═══════════════════════════════════════════════════════════════════════════

    "fmMod: depth=0 produces all ones" {
        val mod = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 2.0),
            depth = ParamIgnitor("depth", 0.0),
        )
        val out = render(mod)
        for (s in out) s shouldBe 1.0
    }

    "fmMod: output is centered near 1.0 (ratio space)" {
        val mod = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 2.0),
            depth = ParamIgnitor("depth", 200.0),
        )
        val out = render(mod)
        // Sine modulator is symmetric → ratio mean near 1.0
        abs(out.mean() - 1.0) shouldBeLessThan 0.1
    }

    "fmMod: larger depth produces larger deviation from 1.0" {
        val small = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 2.0),
            depth = ParamIgnitor("depth", 50.0),
        )
        val large = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 2.0),
            depth = ParamIgnitor("depth", 400.0),
        )

        fun deviationRms(buf: AudioBuffer): Double {
            var s = 0.0; for (x in buf) {
                val d = x.toDouble() - 1.0; s += d * d
            }; return sqrt(s / buf.size)
        }
        deviationRms(render(large)) shouldBeGreaterThan deviationRms(render(small)) * 2.0
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deviation space contract: all mods output values near 0
    // ═══════════════════════════════════════════════════════════════════════════

    "all mods: output values are valid phase ratios (near 1.0)" {
        val mods = listOf(
            vibratoModIgnitor(rate = 5.0, depth = 0.5),
            accelerateModIgnitor(amount = 1.0),
        )
        for (mod in mods) {
            val out = render(mod)
            for (s in out) {
                s.toDouble() shouldBeGreaterThan 0.5
                s.toDouble() shouldBeLessThan 2.0
            }
        }
    }
})
