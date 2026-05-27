package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class AdsrDefTest : StringSpec({

    val json = Json { encodeDefaults = false }

    "AdsrDef.empty is an AdsrDef.Std with all fields null" {
        val empty = AdsrDef.empty
        (empty is AdsrDef.Std) shouldBe true
        empty as AdsrDef.Std
        empty.attack shouldBe null
        empty.decay shouldBe null
        empty.sustain shouldBe null
        empty.release shouldBe null
        empty.attackCurve shouldBe null
        empty.decayCurve shouldBe null
        empty.releaseCurve shouldBe null
    }

    "AdsrDef.defaultSynth uses Square curves on every stage" {
        val def = AdsrDef.defaultSynth as AdsrDef.Std
        def.attack shouldBe 0.01
        def.decay shouldBe 0.1
        def.sustain shouldBe 1.0
        def.release shouldBe 0.05
        def.attackCurve shouldBe AdsrCurve.Square
        def.decayCurve shouldBe AdsrCurve.Square
        def.releaseCurve shouldBe AdsrCurve.Square
    }

    "Std.resolve() fills in defaults when fields are null" {
        val resolved = AdsrDef.Std().resolve()
        resolved.attack shouldBe 0.01
        resolved.decay shouldBe 0.1
        resolved.sustain shouldBe 1.0
        resolved.release shouldBe 0.05
        resolved.attackCurve shouldBe AdsrCurve.Square
        resolved.decayCurve shouldBe AdsrCurve.Square
        resolved.releaseCurve shouldBe AdsrCurve.Square
    }

    "Std.resolve() preserves explicit field values over defaults" {
        val resolved = AdsrDef.Std(
            attack = 0.5,
            decay = 1.0,
            sustain = 0.3,
            release = 2.0,
            attackCurve = AdsrCurve.Linear,
            decayCurve = AdsrCurve.Cube,
            releaseCurve = AdsrCurve.Linear,
        ).resolve()
        resolved.attack shouldBe 0.5
        resolved.decay shouldBe 1.0
        resolved.sustain shouldBe 0.3
        resolved.release shouldBe 2.0
        resolved.attackCurve shouldBe AdsrCurve.Linear
        resolved.decayCurve shouldBe AdsrCurve.Cube
        resolved.releaseCurve shouldBe AdsrCurve.Linear
    }

    "mergeWith — values in this take precedence over other" {
        val a = AdsrDef.Std(attack = 0.1, attackCurve = AdsrCurve.Cube)
        val b = AdsrDef.Std(attack = 0.5, decay = 0.2, attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Linear)
        val merged = a.mergeWith(b) as AdsrDef.Std
        merged.attack shouldBe 0.1                 // from a
        merged.decay shouldBe 0.2                  // from b
        merged.attackCurve shouldBe AdsrCurve.Cube // from a
        merged.decayCurve shouldBe AdsrCurve.Linear // from b
    }

    "mergeWith null returns this" {
        val a = AdsrDef.Std(attack = 0.1)
        val merged = a.mergeWith(null) as AdsrDef.Std
        merged shouldBe a
    }

    "Std round-trips through JSON serialization" {
        val original = AdsrDef.Std(
            attack = 0.01, decay = 0.1, sustain = 0.5, release = 0.3,
            attackCurve = AdsrCurve.Linear,
            decayCurve = AdsrCurve.Square,
            releaseCurve = AdsrCurve.Cube,
        )
        val encoded: String = json.encodeToString(AdsrDef.serializer(), original)
        val decoded = json.decodeFromString(AdsrDef.serializer(), encoded) as AdsrDef.Std
        decoded shouldBe original
    }

    "Std round-trips through JSON with default null fields omitted" {
        val original = AdsrDef.Std(attack = 0.01)
        val encoded = json.encodeToString(AdsrDef.serializer(), original)
        val decoded = json.decodeFromString(AdsrDef.serializer(), encoded) as AdsrDef.Std
        decoded shouldBe original
        decoded.attackCurve shouldBe null
    }

    "AdsrCurve enum has Linear, Square, Cube" {
        AdsrCurve.entries.size shouldBe 3
        AdsrCurve.entries shouldBe listOf(AdsrCurve.Linear, AdsrCurve.Square, AdsrCurve.Cube)
    }
})
