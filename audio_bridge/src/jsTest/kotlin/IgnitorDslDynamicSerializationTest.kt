package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic

@Suppress("OPT_IN_USAGE")
class IgnitorDslDynamicSerializationTest : StringSpec({

    val workletCodec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun roundTrip(dsl: IgnitorDsl): IgnitorDsl {
        val encoded = workletCodec.encodeToDynamic(IgnitorDsl.serializer(), dsl)
        return workletCodec.decodeFromDynamic(IgnitorDsl.serializer(), encoded)
    }

    "Sine default freq survives dynamic round-trip" {
        val dsl = IgnitorDsl.Sine()
        val result = roundTrip(dsl)
        result shouldBe dsl
        (result as IgnitorDsl.Sine).freq shouldBe IgnitorDsl.Freq
    }

    "Sine with Freq.div(2) survives dynamic round-trip" {
        val dsl = IgnitorDsl.Sine(freq = IgnitorDsl.Div(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0)))
        val result = roundTrip(dsl)
        result shouldBe dsl
        (result as IgnitorDsl.Sine).freq shouldBe IgnitorDsl.Div(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0))
    }

    "Sine with Freq.mul(2) survives dynamic round-trip" {
        val dsl = IgnitorDsl.Sine(freq = IgnitorDsl.Mul(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0)))
        val result = roundTrip(dsl)
        result shouldBe dsl
    }

    "Sine with Constant freq survives dynamic round-trip" {
        val dsl = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(220.0))
        val result = roundTrip(dsl)
        result shouldBe dsl
    }
})
