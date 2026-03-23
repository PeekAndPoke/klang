package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ExciterDsl {

    // ═════════════════════════════════════════════════════════════════════════════
    // Oscillator Primitives
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("sine")
    data class Sine(val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("sawtooth")
    data class Sawtooth(val gain: Double = 0.6) : ExciterDsl()

    @Serializable
    @SerialName("square")
    data class Square(val gain: Double = 0.5) : ExciterDsl()

    @Serializable
    @SerialName("triangle")
    data class Triangle(val gain: Double = 0.7) : ExciterDsl()

    @Serializable
    @SerialName("white-noise")
    data class WhiteNoise(val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("zawtooth")
    data class Zawtooth(val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("impulse")
    data class Impulse(val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("pulze")
    data class Pulze(val duty: Double = 0.5, val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("brown-noise")
    data class BrownNoise(val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("pink-noise")
    data class PinkNoise(val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("dust")
    data class Dust(val density: Double = 0.2, val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("crackle")
    data class Crackle(val density: Double = 0.2, val gain: Double = 1.0) : ExciterDsl()

    @Serializable
    @SerialName("ramp")
    data class Ramp(val gain: Double = 0.6) : ExciterDsl()

    @Serializable
    @SerialName("supersaw")
    data class SuperSaw(
        val voices: Int = 5,
        val freqSpread: Double = 0.2,
        val gain: Double = 0.6,
    ) : ExciterDsl()

    @Serializable
    @SerialName("supersine")
    data class SuperSine(
        val voices: Int = 5,
        val freqSpread: Double = 0.2,
        val gain: Double = 1.0,
    ) : ExciterDsl()

    @Serializable
    @SerialName("supersquare")
    data class SuperSquare(
        val voices: Int = 5,
        val freqSpread: Double = 0.2,
        val gain: Double = 0.5,
    ) : ExciterDsl()

    @Serializable
    @SerialName("supertri")
    data class SuperTri(
        val voices: Int = 5,
        val freqSpread: Double = 0.2,
        val gain: Double = 0.7,
    ) : ExciterDsl()

    @Serializable
    @SerialName("superramp")
    data class SuperRamp(
        val voices: Int = 5,
        val freqSpread: Double = 0.2,
        val gain: Double = 0.6,
    ) : ExciterDsl()

    @Serializable
    @SerialName("silence")
    data object Silence : ExciterDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Physical Models
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("pluck")
    data class Pluck(
        val decay: Double = 0.996,
        val brightness: Double = 0.5,
        val pickPosition: Double = 0.5,
        val stiffness: Double = 0.0,
        val gain: Double = 0.7,
    ) : ExciterDsl()

    @Serializable
    @SerialName("superpluck")
    data class SuperPluck(
        val voices: Int = 5,
        val freqSpread: Double = 0.2,
        val decay: Double = 0.996,
        val brightness: Double = 0.5,
        val pickPosition: Double = 0.5,
        val stiffness: Double = 0.0,
        val gain: Double = 0.7,
    ) : ExciterDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic Composition
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("plus")
    data class Plus(val left: ExciterDsl, val right: ExciterDsl) : ExciterDsl()

    @Serializable
    @SerialName("times")
    data class Times(val left: ExciterDsl, val right: ExciterDsl) : ExciterDsl()

    @Serializable
    @SerialName("mul")
    data class Mul(val inner: ExciterDsl, val factor: Double) : ExciterDsl()

    @Serializable
    @SerialName("div")
    data class Div(val inner: ExciterDsl, val divisor: Double) : ExciterDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Frequency Modifiers
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("detune")
    data class Detune(val inner: ExciterDsl, val semitones: Double) : ExciterDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("lowpass")
    data class Lowpass(val inner: ExciterDsl, val cutoffHz: Double, val q: Double = 0.707) : ExciterDsl()

    @Serializable
    @SerialName("highpass")
    data class Highpass(val inner: ExciterDsl, val cutoffHz: Double, val q: Double = 0.707) : ExciterDsl()

    @Serializable
    @SerialName("one-pole-lowpass")
    data class OnePoleLowpass(val inner: ExciterDsl, val cutoffHz: Double) : ExciterDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // Envelope
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("adsr")
    data class Adsr(
        val inner: ExciterDsl,
        val attackSec: Double,
        val decaySec: Double,
        val sustainLevel: Double,
        val releaseSec: Double,
    ) : ExciterDsl()

    // ═════════════════════════════════════════════════════════════════════════════
    // FM Synthesis
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("fm")
    data class Fm(
        val carrier: ExciterDsl,
        val modulator: ExciterDsl,
        val ratio: Double,
        val depth: Double,
        val envAttackSec: Double = 0.0,
        val envDecaySec: Double = 0.0,
        val envSustainLevel: Double = 1.0,
        val envReleaseSec: Double = 0.0,
    ) : ExciterDsl()
}

// ═════════════════════════════════════════════════════════════════════════════════
// Builder Extensions
// ═════════════════════════════════════════════════════════════════════════════════

// Arithmetic
operator fun ExciterDsl.plus(other: ExciterDsl) = ExciterDsl.Plus(this, other)
operator fun ExciterDsl.times(other: ExciterDsl) = ExciterDsl.Times(this, other)
fun ExciterDsl.mul(factor: Double) = ExciterDsl.Mul(this, factor)
fun ExciterDsl.div(divisor: Double) = ExciterDsl.Div(this, divisor)

// Frequency
fun ExciterDsl.detune(semitones: Double) = ExciterDsl.Detune(this, semitones)
fun ExciterDsl.octaveUp() = ExciterDsl.Detune(this, 12.0)
fun ExciterDsl.octaveDown() = ExciterDsl.Detune(this, -12.0)

// Filters
fun ExciterDsl.lowpass(cutoffHz: Double, q: Double = 0.707) = ExciterDsl.Lowpass(this, cutoffHz, q)
fun ExciterDsl.highpass(cutoffHz: Double, q: Double = 0.707) = ExciterDsl.Highpass(this, cutoffHz, q)
fun ExciterDsl.onePoleLowpass(cutoffHz: Double) = ExciterDsl.OnePoleLowpass(this, cutoffHz)

// Envelope
fun ExciterDsl.adsr(attackSec: Double, decaySec: Double, sustainLevel: Double, releaseSec: Double) =
    ExciterDsl.Adsr(this, attackSec, decaySec, sustainLevel, releaseSec)

// FM
fun ExciterDsl.fm(
    modulator: ExciterDsl,
    ratio: Double,
    depth: Double,
    envAttackSec: Double = 0.0,
    envDecaySec: Double = 0.0,
    envSustainLevel: Double = 1.0,
    envReleaseSec: Double = 0.0,
) = ExciterDsl.Fm(
    carrier = this,
    modulator = modulator,
    ratio = ratio,
    depth = depth,
    envAttackSec = envAttackSec,
    envDecaySec = envDecaySec,
    envSustainLevel = envSustainLevel,
    envReleaseSec = envReleaseSec,
)
