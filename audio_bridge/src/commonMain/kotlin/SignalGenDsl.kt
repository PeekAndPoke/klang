package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SignalGenDsl {

    // ═════════════════════════════════════════════════════════════════════════════
    // Oscillator Primitives
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("sine")
    data class Sine(val gain: Double = 1.0) : SignalGenDsl()

    @Serializable
    @SerialName("sawtooth")
    data class Sawtooth(val gain: Double = 0.6) : SignalGenDsl()

    @Serializable
    @SerialName("square")
    data class Square(val gain: Double = 0.5) : SignalGenDsl()

    @Serializable
    @SerialName("triangle")
    data class Triangle(val gain: Double = 0.7) : SignalGenDsl()

    @Serializable
    @SerialName("white-noise")
    data class WhiteNoise(val gain: Double = 1.0) : SignalGenDsl()

    @Serializable
    @SerialName("silence")
    data object Silence : SignalGenDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic Composition
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("plus")
    data class Plus(val left: SignalGenDsl, val right: SignalGenDsl) : SignalGenDsl()

    @Serializable
    @SerialName("times")
    data class Times(val left: SignalGenDsl, val right: SignalGenDsl) : SignalGenDsl()

    @Serializable
    @SerialName("mul")
    data class Mul(val inner: SignalGenDsl, val factor: Double) : SignalGenDsl()

    @Serializable
    @SerialName("div")
    data class Div(val inner: SignalGenDsl, val divisor: Double) : SignalGenDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Frequency Modifiers
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("detune")
    data class Detune(val inner: SignalGenDsl, val semitones: Double) : SignalGenDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("lowpass")
    data class Lowpass(val inner: SignalGenDsl, val cutoffHz: Double, val q: Double = 0.707) : SignalGenDsl()

    @Serializable
    @SerialName("highpass")
    data class Highpass(val inner: SignalGenDsl, val cutoffHz: Double, val q: Double = 0.707) : SignalGenDsl()

    @Serializable
    @SerialName("one-pole-lowpass")
    data class OnePoleLowpass(val inner: SignalGenDsl, val cutoffHz: Double) : SignalGenDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Envelope
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("adsr")
    data class Adsr(
        val inner: SignalGenDsl,
        val attackSec: Double,
        val decaySec: Double,
        val sustainLevel: Double,
        val releaseSec: Double,
    ) : SignalGenDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // FM Synthesis
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("fm")
    data class Fm(
        val carrier: SignalGenDsl,
        val modulator: SignalGenDsl,
        val ratio: Double,
        val depth: Double,
        val envAttackSec: Double = 0.0,
        val envDecaySec: Double = 0.0,
        val envSustainLevel: Double = 1.0,
        val envReleaseSec: Double = 0.0,
    ) : SignalGenDsl()
}

// ═════════════════════════════════════════════════════════════════════════════════
// Builder Extensions
// ═════════════════════════════════════════════════════════════════════════════════

// Arithmetic
operator fun SignalGenDsl.plus(other: SignalGenDsl) = SignalGenDsl.Plus(this, other)
operator fun SignalGenDsl.times(other: SignalGenDsl) = SignalGenDsl.Times(this, other)
fun SignalGenDsl.mul(factor: Double) = SignalGenDsl.Mul(this, factor)
fun SignalGenDsl.div(divisor: Double) = SignalGenDsl.Div(this, divisor)

// Frequency
fun SignalGenDsl.detune(semitones: Double) = SignalGenDsl.Detune(this, semitones)
fun SignalGenDsl.octaveUp() = SignalGenDsl.Detune(this, 12.0)
fun SignalGenDsl.octaveDown() = SignalGenDsl.Detune(this, -12.0)

// Filters
fun SignalGenDsl.lowpass(cutoffHz: Double, q: Double = 0.707) = SignalGenDsl.Lowpass(this, cutoffHz, q)
fun SignalGenDsl.highpass(cutoffHz: Double, q: Double = 0.707) = SignalGenDsl.Highpass(this, cutoffHz, q)
fun SignalGenDsl.onePoleLowpass(cutoffHz: Double) = SignalGenDsl.OnePoleLowpass(this, cutoffHz)

// Envelope
fun SignalGenDsl.adsr(attackSec: Double, decaySec: Double, sustainLevel: Double, releaseSec: Double) =
    SignalGenDsl.Adsr(this, attackSec, decaySec, sustainLevel, releaseSec)

// FM
fun SignalGenDsl.fm(
    modulator: SignalGenDsl,
    ratio: Double,
    depth: Double,
    envAttackSec: Double = 0.0,
    envDecaySec: Double = 0.0,
    envSustainLevel: Double = 1.0,
    envReleaseSec: Double = 0.0,
) = SignalGenDsl.Fm(
    carrier = this,
    modulator = modulator,
    ratio = ratio,
    depth = depth,
    envAttackSec = envAttackSec,
    envDecaySec = envDecaySec,
    envSustainLevel = envSustainLevel,
    envReleaseSec = envReleaseSec,
)
