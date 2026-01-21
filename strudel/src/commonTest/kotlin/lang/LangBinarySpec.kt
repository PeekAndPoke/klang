package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.strudel.StrudelVoiceValue

class LangBinarySpec : StringSpec({

    "binary(5) generates 1 0 1" {
        // 5 is 101 in binary
        val p = binary(5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 1
    }

    "binaryN(5, 4) generates 0 1 0 1" {
        // 5 is 101, padded to 4 bits -> 0101
        val p = binaryN(5, 4)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 4
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 0
        events[3].data.value?.asInt shouldBe 1
    }

    "binary(0) generates 0" {
        val p = binary(0)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asInt shouldBe 0
    }

    "binaryL(5) generates list [1, 0, 1]" {
        val p = binaryL(5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val value = events[0].data.value
        value.shouldBeInstanceOf<StrudelVoiceValue.Seq>()
        value.value.map { it.asInt } shouldBe listOf(1, 0, 1)
    }

    "binaryNL(5, 4) generates list [0, 1, 0, 1]" {
        val p = binaryNL(5, 4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val value = events[0].data.value
        value.shouldBeInstanceOf<StrudelVoiceValue.Seq>()
        value.value.map { it.asInt } shouldBe listOf(0, 1, 0, 1)
    }
})
