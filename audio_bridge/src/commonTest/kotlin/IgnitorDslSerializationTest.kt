package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

class IgnitorDslSerializationTest : StringSpec({

    val json = Json {
        prettyPrint = false
    }

    fun roundTrip(dsl: IgnitorDsl): IgnitorDsl {
        val encoded = json.encodeToString(IgnitorDsl.serializer(), dsl)
        return json.decodeFromString(IgnitorDsl.serializer(), encoded)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Freq
    // ═════════════════════════════════════════════════════════════════════════════

    "Freq round-trips" {
        val dsl = IgnitorDsl.Freq
        roundTrip(dsl) shouldBe dsl
    }

    "Sine with default Freq round-trips" {
        val dsl = IgnitorDsl.Sine()
        val result = roundTrip(dsl)
        result shouldBe dsl
        (result as IgnitorDsl.Sine).freq shouldBe IgnitorDsl.Freq
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Primitives
    // ═════════════════════════════════════════════════════════════════════════════

    "Sine with custom freq round-trips" {
        val dsl = IgnitorDsl.Sine(freq = IgnitorDsl.Param("freq", 440.0))
        roundTrip(dsl) shouldBe dsl
    }

    "Sine with Freq.div(2) round-trips" {
        val dsl = IgnitorDsl.Sine(freq = IgnitorDsl.Div(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0)))
        val result = roundTrip(dsl)
        result shouldBe dsl
        (result as IgnitorDsl.Sine).freq shouldBe IgnitorDsl.Div(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0))
    }

    "Sine with Freq.div(2) round-trips with WorkletContract codec settings" {
        val workletCodec = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val dsl = IgnitorDsl.Sine(freq = IgnitorDsl.Div(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0)))
        val encoded = workletCodec.encodeToString(IgnitorDsl.serializer(), dsl)
        val result = workletCodec.decodeFromString(IgnitorDsl.serializer(), encoded)
        result shouldBe dsl
        (result as IgnitorDsl.Sine).freq shouldBe IgnitorDsl.Div(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0))
    }

    "Sawtooth round-trips" {
        val dsl = IgnitorDsl.Sawtooth()
        roundTrip(dsl) shouldBe dsl
    }

    "Square with custom freq round-trips" {
        val dsl = IgnitorDsl.Square(freq = IgnitorDsl.Param("freq", 220.0))
        roundTrip(dsl) shouldBe dsl
    }

    "Triangle round-trips" {
        val dsl = IgnitorDsl.Triangle()
        roundTrip(dsl) shouldBe dsl
    }

    "WhiteNoise round-trips" {
        val dsl = IgnitorDsl.WhiteNoise()
        roundTrip(dsl) shouldBe dsl
    }

    "Silence round-trips" {
        val dsl = IgnitorDsl.Silence
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Composition
    // ═════════════════════════════════════════════════════════════════════════════

    "Plus composition round-trips" {
        val dsl = IgnitorDsl.Sine() + IgnitorDsl.Sawtooth()
        roundTrip(dsl) shouldBe dsl
    }

    "Nested composition (Sine + Sawtooth.detune).div round-trips" {
        val dsl = (IgnitorDsl.Sine() + IgnitorDsl.Sawtooth().detune(0.1)).div(IgnitorDsl.Param("divisor", 2.0))
        roundTrip(dsl) shouldBe dsl
    }

    "Times composition round-trips" {
        val dsl = IgnitorDsl.Sine() * IgnitorDsl.Triangle()
        roundTrip(dsl) shouldBe dsl
    }

    "mul builder produces Times — round-trips" {
        val dsl = IgnitorDsl.Sine().mul(IgnitorDsl.Param("factor", 0.5))
        dsl.shouldBeInstanceOf<IgnitorDsl.Times>()
        roundTrip(dsl) shouldBe dsl
    }

    "Minus round-trips" {
        val dsl = IgnitorDsl.Sine().minus(IgnitorDsl.Sawtooth())
        roundTrip(dsl) shouldBe dsl
    }

    "Neg round-trips" {
        val dsl = IgnitorDsl.Sine().neg()
        roundTrip(dsl) shouldBe dsl
    }

    "Abs round-trips" {
        val dsl = IgnitorDsl.Sine().abs()
        roundTrip(dsl) shouldBe dsl
    }

    "Pow round-trips" {
        val dsl = IgnitorDsl.Sine().pow(IgnitorDsl.Constant(2.0))
        roundTrip(dsl) shouldBe dsl
    }

    "Min round-trips" {
        val dsl = IgnitorDsl.Sine().min(IgnitorDsl.Constant(0.5))
        roundTrip(dsl) shouldBe dsl
    }

    "Max round-trips" {
        val dsl = IgnitorDsl.Sine().max(IgnitorDsl.Constant(-0.5))
        roundTrip(dsl) shouldBe dsl
    }

    "Clamp round-trips" {
        val dsl = IgnitorDsl.Sine().clamp(IgnitorDsl.Constant(-0.5), IgnitorDsl.Constant(0.5))
        roundTrip(dsl) shouldBe dsl
    }

    "Exp round-trips" {
        val dsl = IgnitorDsl.Sine().exp()
        roundTrip(dsl) shouldBe dsl
    }

    "Log round-trips" {
        val dsl = IgnitorDsl.Sine().log()
        roundTrip(dsl) shouldBe dsl
    }

    "Sqrt round-trips" {
        val dsl = IgnitorDsl.Sine().sqrt()
        roundTrip(dsl) shouldBe dsl
    }

    "Sign round-trips" {
        val dsl = IgnitorDsl.Sine().sign()
        roundTrip(dsl) shouldBe dsl
    }

    "Tanh round-trips" {
        val dsl = IgnitorDsl.Sine().tanh()
        roundTrip(dsl) shouldBe dsl
    }

    "Lerp round-trips" {
        val dsl = IgnitorDsl.Sine().lerp(IgnitorDsl.Sawtooth(), IgnitorDsl.Constant(0.3))
        roundTrip(dsl) shouldBe dsl
    }

    "Range round-trips" {
        val dsl = IgnitorDsl.Sine().range(IgnitorDsl.Constant(0.5), IgnitorDsl.Constant(5.0))
        roundTrip(dsl) shouldBe dsl
    }

    "Bipolar round-trips" {
        val dsl = IgnitorDsl.Sine().bipolar()
        roundTrip(dsl) shouldBe dsl
    }

    "Unipolar round-trips" {
        val dsl = IgnitorDsl.Sine().unipolar()
        roundTrip(dsl) shouldBe dsl
    }

    "Floor round-trips" {
        val dsl = IgnitorDsl.Sine().floor()
        roundTrip(dsl) shouldBe dsl
    }

    "Ceil round-trips" {
        val dsl = IgnitorDsl.Sine().ceil()
        roundTrip(dsl) shouldBe dsl
    }

    "Round round-trips" {
        val dsl = IgnitorDsl.Sine().round()
        roundTrip(dsl) shouldBe dsl
    }

    "Frac round-trips" {
        val dsl = IgnitorDsl.Sine().frac()
        roundTrip(dsl) shouldBe dsl
    }

    "Mod round-trips" {
        val dsl = IgnitorDsl.Sine().mod(IgnitorDsl.Constant(0.5))
        roundTrip(dsl) shouldBe dsl
    }

    "Recip round-trips" {
        val dsl = IgnitorDsl.Sine().recip()
        roundTrip(dsl) shouldBe dsl
    }

    "Sq round-trips" {
        val dsl = IgnitorDsl.Sine().sq()
        roundTrip(dsl) shouldBe dsl
    }

    "Select round-trips" {
        val dsl = IgnitorDsl.Sine().select(IgnitorDsl.Constant(1.0), IgnitorDsl.Constant(-1.0))
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters & Effects
    // ═════════════════════════════════════════════════════════════════════════════

    "Lowpass round-trips" {
        val dsl = IgnitorDsl.Square().lowpass(2000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Highpass with custom q round-trips" {
        val dsl = IgnitorDsl.Sawtooth().highpass(500.0, 1.5)
        roundTrip(dsl) shouldBe dsl
    }

    "OnePoleLowpass round-trips" {
        val dsl = IgnitorDsl.Sawtooth().onePoleLowpass(3000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "ADSR round-trips" {
        val dsl = IgnitorDsl.Sine().adsr(0.01, 0.3, 0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM
    // ═════════════════════════════════════════════════════════════════════════════

    "FM synthesis round-trips" {
        val dsl = IgnitorDsl.Sine().fm(
            modulator = IgnitorDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
        roundTrip(dsl) shouldBe dsl
    }

    "FM with ADSR round-trips" {
        val dsl = IgnitorDsl.Sine()
            .fm(IgnitorDsl.Sine(), ratio = 1.4, depth = 300.0)
            .adsr(0.01, 0.3, 0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Complex compositions (matching the registry presets)
    // ═════════════════════════════════════════════════════════════════════════════

    "sgpad composition round-trips" {
        val dsl = (IgnitorDsl.Sawtooth() + IgnitorDsl.Sawtooth().detune(0.1))
            .div(IgnitorDsl.Param("divisor", 2.0))
            .onePoleLowpass(3000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "sgbell composition round-trips" {
        val dsl = IgnitorDsl.Sine().fm(
            modulator = IgnitorDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envAttackSec = 0.001,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
        roundTrip(dsl) shouldBe dsl
    }

    "sgbuzz composition round-trips" {
        val dsl = IgnitorDsl.Square().lowpass(2000.0)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Parameter Slots
    // ═════════════════════════════════════════════════════════════════════════════

    "Constant round-trips" {
        val dsl = IgnitorDsl.Constant(42.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Param round-trips" {
        val dsl = IgnitorDsl.Param("cutoff", 1000.0, "Filter cutoff")
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Noise Sources
    // ═════════════════════════════════════════════════════════════════════════════

    "PerlinNoise round-trips" {
        val dsl = IgnitorDsl.PerlinNoise()
        roundTrip(dsl) shouldBe dsl
    }

    "BerlinNoise round-trips" {
        val dsl = IgnitorDsl.BerlinNoise()
        roundTrip(dsl) shouldBe dsl
    }

    "BrownNoise round-trips" {
        val dsl = IgnitorDsl.BrownNoise()
        roundTrip(dsl) shouldBe dsl
    }

    "PinkNoise round-trips" {
        val dsl = IgnitorDsl.PinkNoise()
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Additional Oscillator Primitives
    // ═════════════════════════════════════════════════════════════════════════════

    "Ramp round-trips" {
        val dsl = IgnitorDsl.Ramp()
        roundTrip(dsl) shouldBe dsl
    }

    "Impulse round-trips" {
        val dsl = IgnitorDsl.Impulse()
        roundTrip(dsl) shouldBe dsl
    }

    "Pulze round-trips" {
        val dsl = IgnitorDsl.Pulze()
        roundTrip(dsl) shouldBe dsl
    }

    "Dust round-trips" {
        val dsl = IgnitorDsl.Dust()
        roundTrip(dsl) shouldBe dsl
    }

    "Crackle round-trips" {
        val dsl = IgnitorDsl.Crackle()
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Super Oscillators
    // ═════════════════════════════════════════════════════════════════════════════

    "SuperSine round-trips" {
        val dsl = IgnitorDsl.SuperSine()
        roundTrip(dsl) shouldBe dsl
    }

    "SuperSquare round-trips" {
        val dsl = IgnitorDsl.SuperSquare()
        roundTrip(dsl) shouldBe dsl
    }

    "SuperTri round-trips" {
        val dsl = IgnitorDsl.SuperTri()
        roundTrip(dsl) shouldBe dsl
    }

    "SuperRamp round-trips" {
        val dsl = IgnitorDsl.SuperRamp()
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Physical Models
    // ═════════════════════════════════════════════════════════════════════════════

    "Pluck round-trips" {
        val dsl = IgnitorDsl.Pluck()
        roundTrip(dsl) shouldBe dsl
    }

    "SuperPluck round-trips" {
        val dsl = IgnitorDsl.SuperPluck()
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects
    // ═════════════════════════════════════════════════════════════════════════════

    "Distort round-trips" {
        val dsl = IgnitorDsl.Sine().distort(0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Crush round-trips" {
        val dsl = IgnitorDsl.Sine().crush(8.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Phaser round-trips" {
        val dsl = IgnitorDsl.Sine().phaser(0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Tremolo round-trips" {
        val dsl = IgnitorDsl.Sine().tremolo(5.0, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Vibrato round-trips" {
        val dsl = IgnitorDsl.Sine().vibrato(5.0, 0.02)
        roundTrip(dsl) shouldBe dsl
    }

    "Detune round-trips" {
        val dsl = IgnitorDsl.Sine().detune(7.0)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Complex Nested Trees
    // ═════════════════════════════════════════════════════════════════════════════

    "Complex nested tree round-trips" {
        val dsl = IgnitorDsl.SuperSaw(freq = IgnitorDsl.Constant(5.0))
            .lowpass(2000.0)
            .adsr(0.01, 0.3, 0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Drive, Clip, Bandpass, Notch, Coarse, Accelerate
    // ═════════════════════════════════════════════════════════════════════════════

    "Drive round-trips" {
        val dsl = IgnitorDsl.Sine().drive(0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Clip round-trips" {
        val dsl = IgnitorDsl.Sine().clip("hard")
        roundTrip(dsl) shouldBe dsl
    }

    "Bandpass round-trips" {
        val dsl = IgnitorDsl.Sine().bandpass(1000.0, 2.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Notch round-trips" {
        val dsl = IgnitorDsl.Sine().notch(1000.0, 2.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Drive + Clip chain round-trips" {
        val dsl = IgnitorDsl.Sine().drive(0.5).clip("fold")
        roundTrip(dsl) shouldBe dsl
    }

    "Coarse round-trips" {
        val dsl = IgnitorDsl.Sine().coarse(4.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Accelerate round-trips" {
        val dsl = IgnitorDsl.Sine().accelerate(1.0)
        roundTrip(dsl) shouldBe dsl
    }
})
