package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangLoopBeginEndSpec : StringSpec({

    // ---- loopBegin ----

    "loopBegin dsl interface" {
        val pat = "a b"
        val ctrl = "0.25 0.5"
        dslInterfaceTests(
            "pattern.loopBegin(ctrl)" to seq(pat).loopBegin(ctrl),
            "script pattern.loopBegin(ctrl)" to SprudelPattern.compile("""seq("$pat").loopBegin("$ctrl")"""),
            "string.loopBegin(ctrl)" to pat.loopBegin(ctrl),
            "script string.loopBegin(ctrl)" to SprudelPattern.compile(""""$pat".loopBegin("$ctrl")"""),
            "loopBegin(ctrl)" to seq(pat).apply(loopBegin(ctrl)),
            "script loopBegin(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(loopBegin("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.loopBegin shouldBe 0.25
            events[1].data.loopBegin shouldBe 0.5
        }
    }

    "reinterpret voice data as loopBegin | seq(\"0.25 0.5\").loopBegin()" {
        val p = seq("0.25 0.5").loopBegin()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.loopBegin shouldBe 0.25
            events[1].data.loopBegin shouldBe 0.5
        }
    }

    "reinterpret voice data as loopBegin | seq(\"0.25 0.5\").apply(loopBegin())" {
        val p = seq("0.25 0.5").apply(loopBegin())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.loopBegin shouldBe 0.25
            events[1].data.loopBegin shouldBe 0.5
        }
    }

    "loopBegin() sets VoiceData.loopBegin correctly" {
        val p = sound("hh hh").apply(loopBegin("0.25 0.5"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.loopBegin } shouldBe listOf(0.25, 0.5)
    }

    "loopb dsl interface" {
        val pat = "a b"
        val ctrl = "0.25 0.5"
        dslInterfaceTests(
            "pattern.loopb(ctrl)" to seq(pat).loopb(ctrl),
            "script pattern.loopb(ctrl)" to SprudelPattern.compile("""seq("$pat").loopb("$ctrl")"""),
            "string.loopb(ctrl)" to pat.loopb(ctrl),
            "script string.loopb(ctrl)" to SprudelPattern.compile(""""$pat".loopb("$ctrl")"""),
            "loopb(ctrl)" to seq(pat).apply(loopb(ctrl)),
            "script loopb(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(loopb("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.loopBegin shouldBe 0.25
            events[1].data.loopBegin shouldBe 0.5
        }
    }

    // ---- loopEnd ----

    "loopEnd dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 0.75"
        dslInterfaceTests(
            "pattern.loopEnd(ctrl)" to seq(pat).loopEnd(ctrl),
            "script pattern.loopEnd(ctrl)" to SprudelPattern.compile("""seq("$pat").loopEnd("$ctrl")"""),
            "string.loopEnd(ctrl)" to pat.loopEnd(ctrl),
            "script string.loopEnd(ctrl)" to SprudelPattern.compile(""""$pat".loopEnd("$ctrl")"""),
            "loopEnd(ctrl)" to seq(pat).apply(loopEnd(ctrl)),
            "script loopEnd(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(loopEnd("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.loopEnd shouldBe 0.5
            events[1].data.loopEnd shouldBe 0.75
        }
    }

    "reinterpret voice data as loopEnd | seq(\"0.5 0.75\").loopEnd()" {
        val p = seq("0.5 0.75").loopEnd()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.loopEnd shouldBe 0.5
            events[1].data.loopEnd shouldBe 0.75
        }
    }

    "reinterpret voice data as loopEnd | seq(\"0.5 0.75\").apply(loopEnd())" {
        val p = seq("0.5 0.75").apply(loopEnd())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.loopEnd shouldBe 0.5
            events[1].data.loopEnd shouldBe 0.75
        }
    }

    "loopEnd() sets VoiceData.loopEnd correctly" {
        val p = sound("hh hh").apply(loopEnd("0.5 0.75"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.loopEnd } shouldBe listOf(0.5, 0.75)
    }

    "loope dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 0.75"
        dslInterfaceTests(
            "pattern.loope(ctrl)" to seq(pat).loope(ctrl),
            "script pattern.loope(ctrl)" to SprudelPattern.compile("""seq("$pat").loope("$ctrl")"""),
            "string.loope(ctrl)" to pat.loope(ctrl),
            "script string.loope(ctrl)" to SprudelPattern.compile(""""$pat".loope("$ctrl")"""),
            "loope(ctrl)" to seq(pat).apply(loope(ctrl)),
            "script loope(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(loope("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.loopEnd shouldBe 0.5
            events[1].data.loopEnd shouldBe 0.75
        }
    }
})
