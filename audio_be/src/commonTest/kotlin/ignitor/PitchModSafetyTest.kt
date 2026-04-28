package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/**
 * Safety tests for pitch-mod factories — ensures extreme user inputs don't produce
 * `NaN`/`Inf` that would poison oscillator phase accumulators. See
 * `audio/ref/numerical-safety.md` for the contract.
 *
 * The `2.0.pow(...)` arithmetic and `effectiveDepth / freqHz` division in
 * [vibratoModIgnitor], [accelerateModIgnitor], [pitchEnvelopeModIgnitor], and
 * [fmModIgnitor] are now wrapped in `safeOut` / `safeDiv`.
 */
class PitchModSafetyTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 256

    fun ctx(elapsedFrames: Int = 0, durationFrames: Int = sampleRate): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = durationFrames,
        gateEndFrame = durationFrames,
        releaseFrames = 0,
        voiceEndFrame = durationFrames,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = elapsedFrames
    }

    fun render(sig: Ignitor, freqHz: Double = 440.0, c: IgniteContext = ctx()): FloatArray {
        val buf = FloatArray(blockFrames)
        sig.generate(buf, freqHz, c)
        return buf
    }

    fun FloatArray.allFinite(): Boolean = this.all { !it.isNaN() && !it.isInfinite() }

    fun FloatArray.allInBounds(): Boolean = this.all { abs(it) <= SAFE_MAX }

    // ═════════════════════════════════════════════════════════════════════════════
    // Vibrato
    // ═════════════════════════════════════════════════════════════════════════════

    "vibrato with normal depth produces ratios near 1.0" {
        val sig = vibratoModIgnitor(rate = 5.0, depth = 1.0)
        val out = render(sig)
        out.allFinite() shouldBe true
        // 1 semitone = ~5.95% pitch change; ratio in [2^(-1/12), 2^(1/12)] ≈ [0.944, 1.059]
        out.all { it in 0.93f..1.07f } shouldBe true
    }

    "vibrato with extreme depthSemitones stays finite" {
        // depthSemitones = 10000 → 2^(±833) easily overflows Float.
        val sig = vibratoModIgnitor(rate = 5.0, depth = 10000.0)
        val out = render(sig)
        out.allFinite() shouldBe true
        out.allInBounds() shouldBe true
    }

    "vibrato with zero depth outputs exactly 1.0" {
        val sig = vibratoModIgnitor(rate = 5.0, depth = 0.0)
        val out = render(sig)
        out.all { it == 1.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Accelerate
    // ═════════════════════════════════════════════════════════════════════════════

    "accelerate with small amount produces graduated ratios" {
        val sig = accelerateModIgnitor(amount = 1.0)
        val out = render(sig)
        out.allFinite() shouldBe true
        out[0].toDouble() shouldBe (1.0 plusOrMinus 0.01)  // start ≈ 1
    }

    "accelerate with extreme amount stays finite at end of voice" {
        // amount = 10000 → ratio reaches 2^10000 → Inf in Double, must clamp on Float cast.
        val sig = accelerateModIgnitor(amount = 10000.0)
        // Render block from near the end of the voice, when ratio has fully accumulated.
        val out = render(sig, c = ctx(elapsedFrames = sampleRate - blockFrames))
        out.allFinite() shouldBe true
        out.allInBounds() shouldBe true
    }

    "accelerate with zero amount outputs exactly 1.0" {
        val sig = accelerateModIgnitor(amount = 0.0)
        val out = render(sig)
        out.all { it == 1.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pitch envelope
    // ═════════════════════════════════════════════════════════════════════════════

    "pitch envelope with extreme amount stays finite" {
        // amount = 1000 semitones — way past Float pow overflow at envLevel=1.
        val sig = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("att", 0.01),
            decaySec = ParamIgnitor("dec", 0.1),
            amount = ParamIgnitor("amt", 1000.0),
        )
        val out = render(sig)
        out.allFinite() shouldBe true
        out.allInBounds() shouldBe true
    }

    "pitch envelope with zero amount outputs exactly 1.0" {
        val sig = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("att", 0.01),
            decaySec = ParamIgnitor("dec", 0.1),
            amount = ParamIgnitor("amt", 0.0),
        )
        val out = render(sig)
        out.all { it == 1.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM
    // ═════════════════════════════════════════════════════════════════════════════

    "fm at normal frequency produces finite ratios" {
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 100.0),
        )
        val out = render(sig, freqHz = 440.0)
        out.allFinite() shouldBe true
    }

    "fm at sub-Hz freqHz stays finite (safeDiv on freqHz)" {
        // Heavy detune toward 0 → tiny freqHz → effectiveDepth/freqHz would explode.
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 1000.0),
        )
        val out = render(sig, freqHz = 1e-20)
        out.allFinite() shouldBe true
        out.allInBounds() shouldBe true
    }

    "fm at zero freqHz uses bypass path" {
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 100.0),
        )
        val out = render(sig, freqHz = 0.0)
        // freqHz <= 0 short-circuits to all-1.0 output.
        out.all { it == 1.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Composition: extreme pitch-mod feeding an oscillator does NOT silence it
    // ═════════════════════════════════════════════════════════════════════════════

    "extreme vibrato through ModApplyingIgnitor keeps oscillator alive" {
        // Without the safety clamp, an extreme depth would set phase=Inf on first sample
        // and the oscillator would output 0/NaN forever. With the clamp, output stays bounded.
        val mod = vibratoModIgnitor(rate = 5.0, depth = 10000.0)
        val osc = ModApplyingIgnitor(Ignitors.sine(), mod)
        val out = render(osc, freqHz = 440.0)
        out.allFinite() shouldBe true
        // Output isn't silent — at least one sample is non-zero (oscillator is still running).
        out.any { it != 0.0f } shouldBe true
    }
})

