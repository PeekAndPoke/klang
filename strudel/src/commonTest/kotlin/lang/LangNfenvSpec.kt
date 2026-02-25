package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangNfenvSpec : StringSpec({

    // ---- nfenv ----

    "nfenv dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfenv(ctrl)" to seq(pat).nfenv(ctrl),
            "script pattern.nfenv(ctrl)" to StrudelPattern.compile("""seq("$pat").nfenv("$ctrl")"""),
            "string.nfenv(ctrl)" to pat.nfenv(ctrl),
            "script string.nfenv(ctrl)" to StrudelPattern.compile(""""$pat".nfenv("$ctrl")"""),
            "nfenv(ctrl)" to seq(pat).apply(nfenv(ctrl)),
            "script nfenv(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(nfenv("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfenv shouldBe 0.5
            events[1].data.nfenv shouldBe 1.0
        }
    }

    "reinterpret voice data as nfenv | seq(\"0.5 1.0\").nfenv()" {
        val p = seq("0.5 1.0").nfenv()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfenv shouldBe 0.5
            events[1].data.nfenv shouldBe 1.0
        }
    }

    "reinterpret voice data as nfenv | \"0.5 1.0\".nfenv()" {
        val p = "0.5 1.0".nfenv()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfenv shouldBe 0.5
            events[1].data.nfenv shouldBe 1.0
        }
    }

    "reinterpret voice data as nfenv | seq(\"0.5 1.0\").apply(nfenv())" {
        val p = seq("0.5 1.0").apply(nfenv())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfenv shouldBe 0.5
            events[1].data.nfenv shouldBe 1.0
        }
    }

    "nfenv() sets VoiceData.nfenv" {
        val p = note("a b").apply(nfenv("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfenv() sets VoiceData.nfenv on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfenv("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfenv } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfenv() works as string extension" {
        val p = "c3".nfenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfenv shouldBe 0.5
    }

    "nfenv() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").nfenv("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.5, 1.0)
    }

    // ---- nfe (alias) ----

    "nfe dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfe(ctrl)" to seq(pat).nfe(ctrl),
            "script pattern.nfe(ctrl)" to StrudelPattern.compile("""seq("$pat").nfe("$ctrl")"""),
            "string.nfe(ctrl)" to pat.nfe(ctrl),
            "script string.nfe(ctrl)" to StrudelPattern.compile(""""$pat".nfe("$ctrl")"""),
            "nfe(ctrl)" to seq(pat).apply(nfe(ctrl)),
            "script nfe(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(nfe("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfenv shouldBe 0.5
            events[1].data.nfenv shouldBe 1.0
        }
    }

    "reinterpret voice data as nfenv | seq(\"0.5 1.0\").nfe()" {
        val p = seq("0.5 1.0").nfe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfenv shouldBe 0.5
            events[1].data.nfenv shouldBe 1.0
        }
    }

    "nfe() alias works as pattern extension" {
        val p = note("c d").nfe("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.4, 0.6)
    }

    "nfe() alias works as string extension" {
        val p = "e3".nfe("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfenv shouldBe 0.8
    }

    "nfe() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").nfe("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.2, 0.9)
    }
})