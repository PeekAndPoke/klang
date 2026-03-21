package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.lang.apply
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.seq

class LangNfreleaseSpec : StringSpec({

    // ---- nfrelease ----

    "nfrelease dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfrelease(ctrl)" to seq(pat).nfrelease(ctrl),
            "script pattern.nfrelease(ctrl)" to StrudelPattern.compile("""seq("$pat").nfrelease("$ctrl")"""),
            "string.nfrelease(ctrl)" to pat.nfrelease(ctrl),
            "script string.nfrelease(ctrl)" to StrudelPattern.compile(""""$pat".nfrelease("$ctrl")"""),
            "nfrelease(ctrl)" to seq(pat).apply(nfrelease(ctrl)),
            "script nfrelease(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(nfrelease("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfrelease shouldBe 0.5
            events[1].data.nfrelease shouldBe 1.0
        }
    }

    "reinterpret voice data as nfrelease | seq(\"0.5 1.0\").nfrelease()" {
        val p = seq("0.5 1.0").nfrelease()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfrelease shouldBe 0.5
            events[1].data.nfrelease shouldBe 1.0
        }
    }

    "reinterpret voice data as nfrelease | \"0.5 1.0\".nfrelease()" {
        val p = "0.5 1.0".nfrelease()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfrelease shouldBe 0.5
            events[1].data.nfrelease shouldBe 1.0
        }
    }

    "reinterpret voice data as nfrelease | seq(\"0.5 1.0\").apply(nfrelease())" {
        val p = seq("0.5 1.0").apply(nfrelease())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfrelease shouldBe 0.5
            events[1].data.nfrelease shouldBe 1.0
        }
    }

    "nfrelease() sets VoiceData.nfrelease" {
        val p = note("a b").apply(nfrelease("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfrelease() sets VoiceData.nfrelease on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfrelease("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfrelease } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfrelease() works as string extension" {
        val p = "c3".nfrelease("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfrelease shouldBe 0.5
    }

    "nfrelease() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").nfrelease("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.5, 1.0)
    }

    // ---- nfr (alias) ----

    "nfr dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfr(ctrl)" to seq(pat).nfr(ctrl),
            "script pattern.nfr(ctrl)" to StrudelPattern.compile("""seq("$pat").nfr("$ctrl")"""),
            "string.nfr(ctrl)" to pat.nfr(ctrl),
            "script string.nfr(ctrl)" to StrudelPattern.compile(""""$pat".nfr("$ctrl")"""),
            "nfr(ctrl)" to seq(pat).apply(nfr(ctrl)),
            "script nfr(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(nfr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfrelease shouldBe 0.5
            events[1].data.nfrelease shouldBe 1.0
        }
    }

    "reinterpret voice data as nfrelease | seq(\"0.5 1.0\").nfr()" {
        val p = seq("0.5 1.0").nfr()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfrelease shouldBe 0.5
            events[1].data.nfrelease shouldBe 1.0
        }
    }

    "nfr() alias works as pattern extension" {
        val p = note("c d").nfr("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.4, 0.6)
    }

    "nfr() alias works as string extension" {
        val p = "e3".nfr("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfrelease shouldBe 0.8
    }

    "nfr() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").nfr("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.2, 0.9)
    }
})
