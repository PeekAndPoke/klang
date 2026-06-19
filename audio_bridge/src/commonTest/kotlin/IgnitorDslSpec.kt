package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Platform-agnostic `IgnitorDsl` logic: builder factory shapes + tree walks (`collectParams`, `maxReleaseSec`).
 *
 * Wire round-trips live in the JS-only `IgnitorDslWireCodecSpec` (the worklet codec uses `dynamic`); these are
 * pure data/logic and stay in commonTest.
 */
class IgnitorDslSpec : StringSpec({

    "mul builder produces Times" {
        IgnitorDsl.Sine().mul(IgnitorDsl.Param("factor", 0.5)).shouldBeInstanceOf<IgnitorDsl.Times>()
    }

    "distort builder produces Clip(Drive(...))" {
        val dsl = IgnitorDsl.Sine().distort(0.5)
        val clip = dsl.shouldBeInstanceOf<IgnitorDsl.Clip>()
        clip.inner.shouldBeInstanceOf<IgnitorDsl.Drive>()
    }

    "Variants.collectParams unions over children" {
        val dsl = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine(freq = IgnitorDsl.Param("a", 440.0)),
                IgnitorDsl.Sawtooth(freq = IgnitorDsl.Param("b", 220.0)),
            )
        )
        val params = mutableListOf<IgnitorDsl.Param>()
        dsl.collectParams(params)
        params.map { it.name } shouldBe listOf("a", "b")
    }

    "Variants.maxReleaseSec takes the max across children" {
        val dsl = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine().adsr(0.01, 0.1, 0.5, 0.2),
                IgnitorDsl.Sawtooth().adsr(0.01, 0.1, 0.5, 1.5),
                IgnitorDsl.Square().adsr(0.01, 0.1, 0.5, 0.8),
            )
        )
        dsl.maxReleaseSec() shouldBe 1.5
    }

    "Variants.maxReleaseSec returns 0 for empty children" {
        IgnitorDsl.Variants(emptyList()).maxReleaseSec() shouldBe 0.0
    }
})
