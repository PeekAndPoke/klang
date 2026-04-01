package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class ExciterDslSerializationTest : StringSpec({

    val json = Json {
        prettyPrint = false
    }

    fun roundTrip(dsl: ExciterDsl): ExciterDsl {
        val encoded = json.encodeToString(ExciterDsl.serializer(), dsl)
        return json.decodeFromString(ExciterDsl.serializer(), encoded)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Primitives
    // ═════════════════════════════════════════════════════════════════════════════

    "Sine with custom freq round-trips" {
        val dsl = ExciterDsl.Sine(freq = ExciterDsl.Param("freq", 440.0))
        roundTrip(dsl) shouldBe dsl
    }

    "Sawtooth round-trips" {
        val dsl = ExciterDsl.Sawtooth()
        roundTrip(dsl) shouldBe dsl
    }

    "Square with custom freq round-trips" {
        val dsl = ExciterDsl.Square(freq = ExciterDsl.Param("freq", 220.0))
        roundTrip(dsl) shouldBe dsl
    }

    "Triangle round-trips" {
        val dsl = ExciterDsl.Triangle()
        roundTrip(dsl) shouldBe dsl
    }

    "WhiteNoise round-trips" {
        val dsl = ExciterDsl.WhiteNoise
        roundTrip(dsl) shouldBe dsl
    }

    "Silence round-trips" {
        val dsl = ExciterDsl.Silence
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Composition
    // ═════════════════════════════════════════════════════════════════════════════

    "Plus composition round-trips" {
        val dsl = ExciterDsl.Sine() + ExciterDsl.Sawtooth()
        roundTrip(dsl) shouldBe dsl
    }

    "Nested composition (Sine + Sawtooth.detune).div round-trips" {
        val dsl = (ExciterDsl.Sine() + ExciterDsl.Sawtooth().detune(0.1)).div(ExciterDsl.Param("divisor", 2.0))
        roundTrip(dsl) shouldBe dsl
    }

    "Times composition round-trips" {
        val dsl = ExciterDsl.Sine() * ExciterDsl.Triangle()
        roundTrip(dsl) shouldBe dsl
    }

    "Mul round-trips" {
        val dsl = ExciterDsl.Sine().mul(ExciterDsl.Param("factor", 0.5))
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters & Effects
    // ═════════════════════════════════════════════════════════════════════════════

    "Lowpass round-trips" {
        val dsl = ExciterDsl.Square().lowpass(2000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Highpass with custom q round-trips" {
        val dsl = ExciterDsl.Sawtooth().highpass(500.0, 1.5)
        roundTrip(dsl) shouldBe dsl
    }

    "OnePoleLowpass round-trips" {
        val dsl = ExciterDsl.Sawtooth().onePoleLowpass(3000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "ADSR round-trips" {
        val dsl = ExciterDsl.Sine().adsr(0.01, 0.3, 0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM
    // ═════════════════════════════════════════════════════════════════════════════

    "FM synthesis round-trips" {
        val dsl = ExciterDsl.Sine().fm(
            modulator = ExciterDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
        roundTrip(dsl) shouldBe dsl
    }

    "FM with ADSR round-trips" {
        val dsl = ExciterDsl.Sine()
            .fm(ExciterDsl.Sine(), ratio = 1.4, depth = 300.0)
            .adsr(0.01, 0.3, 0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Complex compositions (matching the registry presets)
    // ═════════════════════════════════════════════════════════════════════════════

    "sgpad composition round-trips" {
        val dsl = (ExciterDsl.Sawtooth() + ExciterDsl.Sawtooth().detune(0.1))
            .div(ExciterDsl.Param("divisor", 2.0))
            .onePoleLowpass(3000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "sgbell composition round-trips" {
        val dsl = ExciterDsl.Sine().fm(
            modulator = ExciterDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envAttackSec = 0.001,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
        roundTrip(dsl) shouldBe dsl
    }

    "sgbuzz composition round-trips" {
        val dsl = ExciterDsl.Square().lowpass(2000.0)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Parameter Slots
    // ═════════════════════════════════════════════════════════════════════════════

    "Constant round-trips" {
        val dsl = ExciterDsl.Constant(42.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Param round-trips" {
        val dsl = ExciterDsl.Param("cutoff", 1000.0, "Filter cutoff")
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Noise Sources
    // ═════════════════════════════════════════════════════════════════════════════

    "PerlinNoise round-trips" {
        val dsl = ExciterDsl.PerlinNoise()
        roundTrip(dsl) shouldBe dsl
    }

    "BerlinNoise round-trips" {
        val dsl = ExciterDsl.BerlinNoise()
        roundTrip(dsl) shouldBe dsl
    }

    "BrownNoise round-trips" {
        val dsl = ExciterDsl.BrownNoise
        roundTrip(dsl) shouldBe dsl
    }

    "PinkNoise round-trips" {
        val dsl = ExciterDsl.PinkNoise
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Additional Oscillator Primitives
    // ═════════════════════════════════════════════════════════════════════════════

    "Ramp round-trips" {
        val dsl = ExciterDsl.Ramp()
        roundTrip(dsl) shouldBe dsl
    }

    "Impulse round-trips" {
        val dsl = ExciterDsl.Impulse()
        roundTrip(dsl) shouldBe dsl
    }

    "Pulze round-trips" {
        val dsl = ExciterDsl.Pulze()
        roundTrip(dsl) shouldBe dsl
    }

    "Dust round-trips" {
        val dsl = ExciterDsl.Dust()
        roundTrip(dsl) shouldBe dsl
    }

    "Crackle round-trips" {
        val dsl = ExciterDsl.Crackle()
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Super Oscillators
    // ═════════════════════════════════════════════════════════════════════════════

    "SuperSine round-trips" {
        val dsl = ExciterDsl.SuperSine()
        roundTrip(dsl) shouldBe dsl
    }

    "SuperSquare round-trips" {
        val dsl = ExciterDsl.SuperSquare()
        roundTrip(dsl) shouldBe dsl
    }

    "SuperTri round-trips" {
        val dsl = ExciterDsl.SuperTri()
        roundTrip(dsl) shouldBe dsl
    }

    "SuperRamp round-trips" {
        val dsl = ExciterDsl.SuperRamp()
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Physical Models
    // ═════════════════════════════════════════════════════════════════════════════

    "Pluck round-trips" {
        val dsl = ExciterDsl.Pluck()
        roundTrip(dsl) shouldBe dsl
    }

    "SuperPluck round-trips" {
        val dsl = ExciterDsl.SuperPluck()
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects
    // ═════════════════════════════════════════════════════════════════════════════

    "Distort round-trips" {
        val dsl = ExciterDsl.Sine().distort(0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Crush round-trips" {
        val dsl = ExciterDsl.Sine().crush(8.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Phaser round-trips" {
        val dsl = ExciterDsl.Sine().phaser(0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Tremolo round-trips" {
        val dsl = ExciterDsl.Sine().tremolo(5.0, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Vibrato round-trips" {
        val dsl = ExciterDsl.Sine().vibrato(5.0, 0.02)
        roundTrip(dsl) shouldBe dsl
    }

    "Detune round-trips" {
        val dsl = ExciterDsl.Sine().detune(7.0)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Complex Nested Trees
    // ═════════════════════════════════════════════════════════════════════════════

    "Complex nested tree round-trips" {
        val dsl = ExciterDsl.SuperSaw(freq = ExciterDsl.Constant(5.0))
            .lowpass(2000.0)
            .adsr(0.01, 0.3, 0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Drive, Clip, Bandpass, Notch, Coarse, Accelerate
    // ═════════════════════════════════════════════════════════════════════════════

    "Drive round-trips" {
        val dsl = ExciterDsl.Sine().drive(0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Clip round-trips" {
        val dsl = ExciterDsl.Sine().clip("hard")
        roundTrip(dsl) shouldBe dsl
    }

    "Bandpass round-trips" {
        val dsl = ExciterDsl.Sine().bandpass(1000.0, 2.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Notch round-trips" {
        val dsl = ExciterDsl.Sine().notch(1000.0, 2.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Drive + Clip chain round-trips" {
        val dsl = ExciterDsl.Sine().drive(0.5).clip("fold")
        roundTrip(dsl) shouldBe dsl
    }

    "Coarse round-trips" {
        val dsl = ExciterDsl.Sine().coarse(4.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Accelerate round-trips" {
        val dsl = ExciterDsl.Sine().accelerate(1.0)
        roundTrip(dsl) shouldBe dsl
    }
})
