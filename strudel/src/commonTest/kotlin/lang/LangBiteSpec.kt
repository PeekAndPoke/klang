package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangBiteSpec : StringSpec({

    "bite(4, '0 1 2 3') reconstructs the original pattern" {
        // Slicing into 4 and playing 0, 1, 2, 3 in order should be identity
        val p = n("0 1 2 3").bite(4, "0 1 2 3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events.map { it.data.soundIndex } shouldBe listOf(0, 1, 2, 3)
    }

    "bite(4, '3 2 1 0') reverses the pattern" {
        val p = n("0 1 2 3").bite(4, "3 2 1 0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events.map { it.data.soundIndex } shouldBe listOf(3, 2, 1, 0)
    }

    "bite works with pattern indices" {
        // indices: <0 1>
        val p = n("10 20").bite(2, seq("0 1"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.soundIndex shouldBe 10
        events[1].data.soundIndex shouldBe 20
    }

    "bite handles wrapping indices" {
        // Index 2 on a 2-slice pattern should wrap to 0
        val p = n("10 20").bite(2, "2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundIndex shouldBe 10
    }

    "bite handles negative indices" {
        // Index -1 on a 2-slice pattern should wrap to 1 (last element)
        val p = n("10 20").bite(2, "-1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundIndex shouldBe 20
    }

    "bite works as string extension" {
        val p = "0 1 2 3".bite(4, "3 2 1 0")
        val events = p.queryArc(0.0, 1.0)
        events.map { it.data.value?.asInt } shouldBe listOf(3, 2, 1, 0)
    }
})
