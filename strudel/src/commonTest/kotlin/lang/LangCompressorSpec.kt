package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangCompressorSpec : StringSpec({

    "top-level compressor() sets VoiceData.compressor correctly" {
        // Given a simple sequence of compressor values within one cycle
        val p = compressor("0.5:2 0.8:4")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the compressor values in order
        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.5:2", "0.8:4")
    }

    "control pattern compressor() sets VoiceData.compressor on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the compressor per step
        val p = base.compressor("0.5:2:0.1 0.8:4:0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the compressor values in order
        events.map { it.data.compressor } shouldBe listOf("0.5:2:0.1", "0.8:4:0.2", "0.5:2:0.1", "0.8:4:0.2")
    }

    "compressor() works as string extension" {
        val p = "c3".compressor("0.6:3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.compressor shouldBe "0.6:3"
    }

    "compressor() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""compressor("0.5:2 0.8:4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.5:2", "0.8:4")
    }

    "compressor() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").compressor("0.5:2 0.8:4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.5:2", "0.8:4")
    }

    "compressor() with full parameters (threshold:ratio:knee:attack:release)" {
        val p = note("c").compressor("0.5:2:0.1:0.01:0.1")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "0.5:2:0.1:0.01:0.1"
    }

    "comp() alias works as top-level function" {
        val p = comp("0.7:3 0.9:5")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.7:3", "0.9:5")
    }

    "comp() alias works as pattern extension" {
        val p = note("c d").comp("0.4:2 0.6:3")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.4:2", "0.6:3")
    }

    "comp() alias works as string extension" {
        val p = "e3".comp("0.8:4")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.compressor shouldBe "0.8:4"
    }

    "comp() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").comp("0.2:1.5 0.9:6")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.2:1.5", "0.9:6")
    }

    "compressor() can accept numeric values" {
        // Compressor accepts any value and converts to string
        val p = note("c").compressor(2.5)

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "2.5"
    }
})
