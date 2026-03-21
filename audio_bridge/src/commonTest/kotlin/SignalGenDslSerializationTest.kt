package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class SignalGenDslSerializationTest : StringSpec({

    val json = Json {
        prettyPrint = false
    }

    fun roundTrip(dsl: SignalGenDsl): SignalGenDsl {
        val encoded = json.encodeToString(SignalGenDsl.serializer(), dsl)
        return json.decodeFromString(SignalGenDsl.serializer(), encoded)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Primitives
    // ═════════════════════════════════════════════════════════════════════════════

    "Sine with custom gain round-trips" {
        val dsl = SignalGenDsl.Sine(0.8)
        roundTrip(dsl) shouldBe dsl
    }

    "Sawtooth round-trips" {
        val dsl = SignalGenDsl.Sawtooth()
        roundTrip(dsl) shouldBe dsl
    }

    "Square round-trips" {
        val dsl = SignalGenDsl.Square(0.3)
        roundTrip(dsl) shouldBe dsl
    }

    "Triangle round-trips" {
        val dsl = SignalGenDsl.Triangle()
        roundTrip(dsl) shouldBe dsl
    }

    "WhiteNoise round-trips" {
        val dsl = SignalGenDsl.WhiteNoise(0.5)
        roundTrip(dsl) shouldBe dsl
    }

    "Silence round-trips" {
        val dsl = SignalGenDsl.Silence
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Composition
    // ═════════════════════════════════════════════════════════════════════════════

    "Plus composition round-trips" {
        val dsl = SignalGenDsl.Sine() + SignalGenDsl.Sawtooth()
        roundTrip(dsl) shouldBe dsl
    }

    "Nested composition (Sine + Sawtooth.detune).div round-trips" {
        val dsl = (SignalGenDsl.Sine() + SignalGenDsl.Sawtooth().detune(0.1)).div(2.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Times composition round-trips" {
        val dsl = SignalGenDsl.Sine() * SignalGenDsl.Triangle()
        roundTrip(dsl) shouldBe dsl
    }

    "Mul round-trips" {
        val dsl = SignalGenDsl.Sine().mul(0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters & Effects
    // ═════════════════════════════════════════════════════════════════════════════

    "Lowpass round-trips" {
        val dsl = SignalGenDsl.Square().lowpass(2000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "Highpass with custom q round-trips" {
        val dsl = SignalGenDsl.Sawtooth().highpass(500.0, 1.5)
        roundTrip(dsl) shouldBe dsl
    }

    "OnePoleLowpass round-trips" {
        val dsl = SignalGenDsl.Sawtooth().onePoleLowpass(3000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "ADSR round-trips" {
        val dsl = SignalGenDsl.Sine().adsr(0.01, 0.3, 0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM
    // ═════════════════════════════════════════════════════════════════════════════

    "FM synthesis round-trips" {
        val dsl = SignalGenDsl.Sine().fm(
            modulator = SignalGenDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
        roundTrip(dsl) shouldBe dsl
    }

    "FM with ADSR round-trips" {
        val dsl = SignalGenDsl.Sine()
            .fm(SignalGenDsl.Sine(), ratio = 1.4, depth = 300.0)
            .adsr(0.01, 0.3, 0.5, 0.5)
        roundTrip(dsl) shouldBe dsl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Complex compositions (matching the registry presets)
    // ═════════════════════════════════════════════════════════════════════════════

    "sgpad composition round-trips" {
        val dsl = (SignalGenDsl.Sawtooth() + SignalGenDsl.Sawtooth().detune(0.1))
            .div(2.0)
            .onePoleLowpass(3000.0)
        roundTrip(dsl) shouldBe dsl
    }

    "sgbell composition round-trips" {
        val dsl = SignalGenDsl.Sine().fm(
            modulator = SignalGenDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envAttackSec = 0.001,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
        roundTrip(dsl) shouldBe dsl
    }

    "sgbuzz composition round-trips" {
        val dsl = SignalGenDsl.Square().lowpass(2000.0)
        roundTrip(dsl) shouldBe dsl
    }
})
