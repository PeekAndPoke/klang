package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.exp as kexp
import kotlin.math.tanh as ktanh

/**
 * Runtime semantic tests for the arithmetic ops added in the IgnitorDsl arithmetic batch
 * (2026-04-27). Verifies math correctness, edge cases (NaN/Inf safety), and the
 * `SAFE_MIN`/`SAFE_MAX` safety contract from `audio/ref/numerical-safety.md`.
 */
class IgnitorArithmeticTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 256

    fun ctx(): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * 4,
        gateEndFrame = blockFrames * 4,
        releaseFrames = 0,
        voiceEndFrame = blockFrames * 4,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    /** Render a signal to a AudioBuffer. freq is irrelevant for pure-arithmetic chains. */
    fun render(sig: Ignitor, freqHz: Double = 0.0): AudioBuffer {
        val buf = AudioBuffer(blockFrames)
        sig.generate(buf, freqHz, ctx())
        return buf
    }

    /** Constant-signal Ignitor for deterministic input. */
    fun const(value: Double): Ignitor = ParamIgnitor("c", value)

    /** Verify every sample in [buf] is finite (no NaN, no ±Inf). */
    fun AudioBuffer.allFinite(): Boolean = this.all { !it.isNaN() && !it.isInfinite() }

    // ═════════════════════════════════════════════════════════════════════════════
    // Unary arithmetic
    // ═════════════════════════════════════════════════════════════════════════════

    "neg flips sign" {
        val out = render(const(0.7).neg())
        out[0].toDouble() shouldBe (-0.7 plusOrMinus 1e-6)
    }

    "neg of negative is positive" {
        val out = render(const(-0.4).neg())
        out[0].toDouble() shouldBe (0.4 plusOrMinus 1e-6)
    }

    "abs of negative becomes positive" {
        val out = render(const(-0.6).abs())
        out[0].toDouble() shouldBe (0.6 plusOrMinus 1e-6)
    }

    "abs of positive stays positive" {
        val out = render(const(0.3).abs())
        out[0].toDouble() shouldBe (0.3 plusOrMinus 1e-6)
    }

    "sq is x squared" {
        val out = render(const(0.5).sq())
        out[0].toDouble() shouldBe (0.25 plusOrMinus 1e-6)
    }

    "sq of negative is positive (no signed-magnitude)" {
        val out = render(const(-0.5).sq())
        out[0].toDouble() shouldBe (0.25 plusOrMinus 1e-6)
    }

    "neg, abs, sq combined chain" {
        // (-0.5).neg().abs().sq() = ((+0.5).abs()).sq() = 0.25
        val out = render(const(-0.5).neg().abs().sq())
        out[0].toDouble() shouldBe (0.25 plusOrMinus 1e-6)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Binary arithmetic
    // ═════════════════════════════════════════════════════════════════════════════

    "minus subtracts" {
        val out = render(const(0.7).minus(const(0.3)))
        out[0].toDouble() shouldBe (0.4 plusOrMinus 1e-6)
    }

    "minus of itself is zero" {
        val out = render(const(0.5).minus(const(0.5)))
        out[0].toDouble() shouldBe (0.0 plusOrMinus 1e-6)
    }

    "min picks smaller" {
        val out = render(const(0.7).min(const(0.3)))
        out[0].toDouble() shouldBe (0.3 plusOrMinus 1e-6)
    }

    "min handles negative" {
        val out = render(const(-0.5).min(const(0.5)))
        out[0].toDouble() shouldBe (-0.5 plusOrMinus 1e-6)
    }

    "max picks larger" {
        val out = render(const(0.7).max(const(0.3)))
        out[0].toDouble() shouldBe (0.7 plusOrMinus 1e-6)
    }

    "max handles negative" {
        val out = render(const(-0.5).max(const(-0.9)))
        out[0].toDouble() shouldBe (-0.5 plusOrMinus 1e-6)
    }

    "clamp bounds within range" {
        val out = render(const(2.0).clamp(const(-1.0), const(1.0)))
        out[0].toDouble() shouldBe (1.0 plusOrMinus 1e-6)
    }

    "clamp passes through if in range" {
        val out = render(const(0.3).clamp(const(-1.0), const(1.0)))
        out[0].toDouble() shouldBe (0.3 plusOrMinus 1e-6)
    }

    "clamp lower bound" {
        val out = render(const(-2.0).clamp(const(-1.0), const(1.0)))
        out[0].toDouble() shouldBe (-1.0 plusOrMinus 1e-6)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pow (signed-magnitude semantics)
    // ═════════════════════════════════════════════════════════════════════════════

    "pow positive base" {
        val out = render(const(2.0).pow(const(3.0)))
        out[0].toDouble() shouldBe (8.0 plusOrMinus 1e-4)
    }

    "pow negative base — signed magnitude" {
        // (-2)^3 with signed-magnitude = -(2^3) = -8 (matches real cube)
        val out = render(const(-2.0).pow(const(3.0)))
        out[0].toDouble() shouldBe (-8.0 plusOrMinus 1e-4)
    }

    "pow negative base with even exp differs from sq()" {
        // signed-magnitude pow(2) gives -|x|² for negative bases, NOT |x|²
        // This is the documented quirk — see numerical-safety doc.
        val out = render(const(-2.0).pow(const(2.0)))
        out[0].toDouble() shouldBe (-4.0 plusOrMinus 1e-4)
        // sq() correctly gives positive
        val sqOut = render(const(-2.0).sq())
        sqOut[0].toDouble() shouldBe (4.0 plusOrMinus 1e-4)
    }

    "pow with zero base is finite (no NaN/Inf)" {
        val out = render(const(0.0).pow(const(-1.0)))
        out.allFinite() shouldBe true
    }

    "pow with extreme base does not produce Inf (SAFE_MAX clamp)" {
        // 1e10 ^ 10 = 1e100 — way past Float MAX. Output clamp must catch it.
        val out = render(const(1e10).pow(const(10.0)))
        out.allFinite() shouldBe true
        out[0].shouldBeLessThanOrEqual(SAFE_MAX)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Transcendentals (signed-magnitude where applicable)
    // ═════════════════════════════════════════════════════════════════════════════

    "exp of 0 is 1" {
        val out = render(const(0.0).exp())
        out[0].toDouble() shouldBe (1.0 plusOrMinus 1e-6)
    }

    "exp of 1 is e" {
        val out = render(const(1.0).exp())
        out[0].toDouble() shouldBe (kexp(1.0) plusOrMinus 1e-5)
    }

    "exp of large value clamped to SAFE_MAX (no Inf)" {
        // exp(40) ≈ 2.35e17 — past SAFE_MAX of 1e15
        val out = render(const(40.0).exp())
        out.allFinite() shouldBe true
        out[0].shouldBeLessThanOrEqual(SAFE_MAX)
    }

    "log of positive matches ln" {
        val out = render(const(2.0).log())
        out[0].toDouble() shouldBe (ln(2.0) plusOrMinus 1e-5)
    }

    "log of negative is signed-magnitude" {
        // signed-magnitude log: -ln(|x|) for x < 0
        val out = render(const(-2.0).log())
        out[0].toDouble() shouldBe (-ln(2.0) plusOrMinus 1e-5)
    }

    "log of 0 is 0 (no -Inf)" {
        val out = render(const(0.0).log())
        out[0] shouldBe 0.0
        out.allFinite() shouldBe true
    }

    "sqrt of positive matches √" {
        val out = render(const(4.0).sqrt())
        out[0].toDouble() shouldBe (2.0 plusOrMinus 1e-5)
    }

    "sqrt of negative is signed-magnitude" {
        // sqrt(-4) = -sqrt(4) = -2 (signed-magnitude, no NaN)
        val out = render(const(-4.0).sqrt())
        out[0].toDouble() shouldBe (-2.0 plusOrMinus 1e-5)
        out.allFinite() shouldBe true
    }

    "sign of positive is 1" {
        render(const(0.7).sign())[0] shouldBe 1.0
    }

    "sign of negative is -1" {
        render(const(-0.3).sign())[0] shouldBe -1.0
    }

    "sign of zero is 0" {
        render(const(0.0).sign())[0] shouldBe 0.0
    }

    "tanh saturates positive" {
        val out = render(const(5.0).tanh())
        out[0].toDouble() shouldBe (ktanh(5.0) plusOrMinus 1e-5)
        out[0].shouldBeLessThan(1.0001)
    }

    "tanh saturates negative" {
        val out = render(const(-5.0).tanh())
        out[0].toDouble() shouldBe (ktanh(-5.0) plusOrMinus 1e-5)
        out[0].shouldBeGreaterThan(-1.0001)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Crossfade / range mapping
    // ═════════════════════════════════════════════════════════════════════════════

    "lerp at t=0 is left" {
        val out = render(const(0.2).lerp(const(0.8), const(0.0)))
        out[0].toDouble() shouldBe (0.2 plusOrMinus 1e-6)
    }

    "lerp at t=1 is right" {
        val out = render(const(0.2).lerp(const(0.8), const(1.0)))
        out[0].toDouble() shouldBe (0.8 plusOrMinus 1e-6)
    }

    "lerp at t=0.5 is midpoint" {
        val out = render(const(0.2).lerp(const(0.8), const(0.5)))
        out[0].toDouble() shouldBe (0.5 plusOrMinus 1e-6)
    }

    "range maps -1..1 to lo..hi at midpoint" {
        // input = 0 (midpoint of [-1,1]) → output = midpoint of [lo, hi]
        val out = render(const(0.0).range(const(2.0), const(10.0)))
        out[0].toDouble() shouldBe (6.0 plusOrMinus 1e-5)
    }

    "range maps -1 to lo" {
        val out = render(const(-1.0).range(const(2.0), const(10.0)))
        out[0].toDouble() shouldBe (2.0 plusOrMinus 1e-5)
    }

    "range maps +1 to hi" {
        val out = render(const(1.0).range(const(2.0), const(10.0)))
        out[0].toDouble() shouldBe (10.0 plusOrMinus 1e-5)
    }

    "bipolar maps 0..1 to -1..1" {
        // 0 → -1, 1 → +1, 0.5 → 0
        render(const(0.0).bipolar())[0].toDouble() shouldBe (-1.0 plusOrMinus 1e-6)
        render(const(1.0).bipolar())[0].toDouble() shouldBe (1.0 plusOrMinus 1e-6)
        render(const(0.5).bipolar())[0].toDouble() shouldBe (0.0 plusOrMinus 1e-6)
    }

    "unipolar maps -1..1 to 0..1" {
        // -1 → 0, +1 → 1, 0 → 0.5
        render(const(-1.0).unipolar())[0].toDouble() shouldBe (0.0 plusOrMinus 1e-6)
        render(const(1.0).unipolar())[0].toDouble() shouldBe (1.0 plusOrMinus 1e-6)
        render(const(0.0).unipolar())[0].toDouble() shouldBe (0.5 plusOrMinus 1e-6)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Quantization
    // ═════════════════════════════════════════════════════════════════════════════

    "floor rounds down" {
        render(const(2.7).floor())[0].toDouble() shouldBe 2.0
        render(const(-1.3).floor())[0].toDouble() shouldBe -2.0
    }

    "ceil rounds up" {
        render(const(2.3).ceil())[0].toDouble() shouldBe 3.0
        render(const(-1.7).ceil())[0].toDouble() shouldBe -1.0
    }

    "round to nearest integer" {
        render(const(2.4).round())[0].toDouble() shouldBe 2.0
        render(const(2.6).round())[0].toDouble() shouldBe 3.0
        render(const(-1.4).round())[0].toDouble() shouldBe -1.0
    }

    "frac is x minus floor(x)" {
        render(const(2.7).frac())[0].toDouble() shouldBe (0.7 plusOrMinus 1e-6)
        render(const(-1.3).frac())[0].toDouble() shouldBe (0.7 plusOrMinus 1e-6)  // -1.3 - (-2) = 0.7
        render(const(0.0).frac())[0].toDouble() shouldBe 0.0
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Division-class ops with safety
    // ═════════════════════════════════════════════════════════════════════════════

    "mod follows Kotlin rem semantics — sign follows dividend" {
        // 5.5 % 2.0 = 1.5
        render(const(5.5).mod(const(2.0)))[0].toDouble() shouldBe (1.5 plusOrMinus 1e-5)
        // -5.5 % 2.0 = -1.5 (rem, not floored mod)
        render(const(-5.5).mod(const(2.0)))[0].toDouble() shouldBe (-1.5 plusOrMinus 1e-5)
    }

    "mod by zero is finite (epsilon-substituted divisor)" {
        val out = render(const(1.0).mod(const(0.0)))
        out.allFinite() shouldBe true
    }

    "recip is 1/x" {
        render(const(2.0).recip())[0].toDouble() shouldBe (0.5 plusOrMinus 1e-5)
        render(const(-4.0).recip())[0].toDouble() shouldBe (-0.25 plusOrMinus 1e-5)
    }

    "recip of zero is finite (clamped to SAFE_MAX)" {
        val out = render(const(0.0).recip())
        out.allFinite() shouldBe true
        // Should be very close to +SAFE_MAX (0 → +SAFE_MIN → 1/SAFE_MIN ≈ SAFE_MAX).
        // Not exactly equal in Double — `1.0 / 1e-15` rounds to ~9.999999999999999e14,
        // which is just below SAFE_MAX (1e15).
        out[0] shouldBe (SAFE_MAX plusOrMinus 1.0)
    }

    "recip of subnormal-small input is finite" {
        // Subnormal inputs would otherwise overflow Float in the reciprocal.
        val out = render(const(1e-25).recip())
        out.allFinite() shouldBe true
        out[0].shouldBeLessThanOrEqual(SAFE_MAX)
    }

    "div by zero is finite (epsilon-substituted divisor)" {
        val out = render((const(1.0) * const(1.0)).div(const(0.0)))
        out.allFinite() shouldBe true
    }

    "div by tiny near-zero is finite (clamped to SAFE_MAX via output clamp)" {
        val out = render(const(1.0).div(const(1e-25)) * const(1.0))
        out.allFinite() shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Times output clamp
    // ═════════════════════════════════════════════════════════════════════════════

    "times multiplies normally" {
        val out = render(const(0.5) * const(0.4))
        out[0].toDouble() shouldBe (0.2 plusOrMinus 1e-6)
    }

    "times of huge values clamped to SAFE_MAX (no Inf)" {
        val out = render(const(1e20) * const(1e20))
        out.allFinite() shouldBe true
        abs(out[0]).shouldBeLessThanOrEqual(SAFE_MAX)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Select
    // ═════════════════════════════════════════════════════════════════════════════

    "select picks whenTrue when cond > 0" {
        val out = render(const(0.5).select(const(7.0), const(3.0)))
        out[0].toDouble() shouldBe (7.0 plusOrMinus 1e-6)
    }

    "select picks whenFalse when cond <= 0" {
        val out = render(const(-0.5).select(const(7.0), const(3.0)))
        out[0].toDouble() shouldBe (3.0 plusOrMinus 1e-6)
    }

    "select cond=0 picks whenFalse (strict > 0 semantics)" {
        val out = render(const(0.0).select(const(7.0), const(3.0)))
        out[0].toDouble() shouldBe (3.0 plusOrMinus 1e-6)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Safety — composed chains never produce NaN/Inf
    // ═════════════════════════════════════════════════════════════════════════════

    "deeply nested arithmetic chain stays finite" {
        // recip(0).log().sqrt().pow(2) — every step has a safety hazard
        val sig = const(0.0).recip().log().sqrt().pow(const(2.0))
        val out = render(sig)
        out.allFinite() shouldBe true
    }

    "1/sin near zero crossing stays finite (audio-rate scenario)" {
        // A sine going through zero would normally produce ±Inf via recip.
        val out = AudioBuffer(blockFrames)
        Ignitors.sine().recip().generate(out, 440.0, ctx())
        out.allFinite() shouldBe true
    }
})
