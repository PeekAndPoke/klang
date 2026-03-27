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

    "Sine with custom gain round-trips" {
        val dsl = ExciterDsl.Sine(gain = ExciterDsl.Param("gain", 0.8))
        roundTrip(dsl) shouldBe dsl
    }

    "Sawtooth round-trips" {
        val dsl = ExciterDsl.Sawtooth()
        roundTrip(dsl) shouldBe dsl
    }

    "Square round-trips" {
        val dsl = ExciterDsl.Square(gain = ExciterDsl.Param("gain", 0.3))
        roundTrip(dsl) shouldBe dsl
    }

    "Triangle round-trips" {
        val dsl = ExciterDsl.Triangle()
        roundTrip(dsl) shouldBe dsl
    }

    "WhiteNoise round-trips" {
        val dsl = ExciterDsl.WhiteNoise(gain = ExciterDsl.Param("gain", 0.5))
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
        val dsl = (ExciterDsl.Sine() + ExciterDsl.Sawtooth().detune(0.1)).div(2.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Times composition round-trips" {
        val dsl = ExciterDsl.Sine() * ExciterDsl.Triangle()
        roundTrip(dsl) shouldBe dsl
    }

    "Mul round-trips" {
        val dsl = ExciterDsl.Sine().mul(0.5)
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
            .div(2.0)
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
})
