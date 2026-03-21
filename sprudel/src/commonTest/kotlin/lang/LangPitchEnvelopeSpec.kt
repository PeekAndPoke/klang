package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangPitchEnvelopeSpec : StringSpec({

    // ---- pattack / patt ----

    "pattack dsl interface" {
        val pat = "c4 e4"
        val amount = 0.1

        dslInterfaceTests(
            "pattern.pattack(v)" to note(pat).pattack(amount),
            "script pattern.pattack(v)" to SprudelPattern.compile("""note("$pat").pattack($amount)"""),
            "string.pattack(v)" to pat.pattack(amount),
            "script string.pattack(v)" to SprudelPattern.compile(""""$pat".pattack($amount)"""),
            "pattack(v)" to note(pat).apply(pattack(amount)),
            "script pattack(v)" to SprudelPattern.compile("""note("$pat").apply(pattack($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pAttack shouldBe amount
        }
    }

    "reinterpret voice data as pAttack | seq(\"0.1 0.2\").pattack()" {
        val p = seq("0.1 0.2").pattack()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAttack } shouldBe listOf(0.1, 0.2)
    }

    "pattack() sets StrudelVoiceData.pAttack" {
        val p = note("a b").pattack("0.1 0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAttack } shouldBe listOf(0.1, 0.2)
    }

    "pattack() works as pattern extension" {
        val p = note("c").pattack("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
    }

    "pattack() works as string extension" {
        val p = "c".pattack("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
    }

    "pattack() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").pattack("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
    }

    "patt() is an alias for pattack()" {
        val p = note("c").patt("0.1")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.pAttack shouldBe 0.1
    }

    // ---- pdecay / pdec ----

    "pdecay dsl interface" {
        val pat = "c4 e4"
        val amount = 0.3

        dslInterfaceTests(
            "pattern.pdecay(v)" to note(pat).pdecay(amount),
            "script pattern.pdecay(v)" to SprudelPattern.compile("""note("$pat").pdecay($amount)"""),
            "string.pdecay(v)" to pat.pdecay(amount),
            "script string.pdecay(v)" to SprudelPattern.compile(""""$pat".pdecay($amount)"""),
            "pdecay(v)" to note(pat).apply(pdecay(amount)),
            "script pdecay(v)" to SprudelPattern.compile("""note("$pat").apply(pdecay($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pDecay shouldBe amount
        }
    }

    "reinterpret voice data as pDecay | seq(\"0.3 0.4\").pdecay()" {
        val p = seq("0.3 0.4").pdecay()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pDecay } shouldBe listOf(0.3, 0.4)
    }

    "pdecay() sets StrudelVoiceData.pDecay" {
        val p = note("a b").pdecay("0.3 0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pDecay } shouldBe listOf(0.3, 0.4)
    }

    "pdecay() works as pattern extension" {
        val p = note("c").pdecay("0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pDecay shouldBe 0.3
    }

    "pdec() is an alias for pdecay()" {
        val p = note("c").pdec("0.3")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.pDecay shouldBe 0.3
    }

    // ---- prelease / prel ----

    "prelease dsl interface" {
        val pat = "c4 e4"
        val amount = 0.5

        dslInterfaceTests(
            "pattern.prelease(v)" to note(pat).prelease(amount),
            "script pattern.prelease(v)" to SprudelPattern.compile("""note("$pat").prelease($amount)"""),
            "string.prelease(v)" to pat.prelease(amount),
            "script string.prelease(v)" to SprudelPattern.compile(""""$pat".prelease($amount)"""),
            "prelease(v)" to note(pat).apply(prelease(amount)),
            "script prelease(v)" to SprudelPattern.compile("""note("$pat").apply(prelease($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pRelease shouldBe amount
        }
    }

    "reinterpret voice data as pRelease | seq(\"0.5 0.6\").prelease()" {
        val p = seq("0.5 0.6").prelease()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pRelease } shouldBe listOf(0.5, 0.6)
    }

    "prelease() sets StrudelVoiceData.pRelease" {
        val p = note("a b").prelease("0.5 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pRelease } shouldBe listOf(0.5, 0.6)
    }

    "prelease() works as pattern extension" {
        val p = note("c").prelease("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pRelease shouldBe 0.5
    }

    "prel() is an alias for prelease()" {
        val p = note("c").prel("0.5")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.pRelease shouldBe 0.5
    }

    // ---- penv / pamt ----

    "penv dsl interface" {
        val pat = "c4 e4"
        val amount = 12.0

        dslInterfaceTests(
            "pattern.penv(v)" to note(pat).penv(amount),
            "script pattern.penv(v)" to SprudelPattern.compile("""note("$pat").penv($amount)"""),
            "string.penv(v)" to pat.penv(amount),
            "script string.penv(v)" to SprudelPattern.compile(""""$pat".penv($amount)"""),
            "penv(v)" to note(pat).apply(penv(amount)),
            "script penv(v)" to SprudelPattern.compile("""note("$pat").apply(penv($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pEnv shouldBe amount
        }
    }

    "reinterpret voice data as pEnv | seq(\"12 24\").penv()" {
        val p = seq("12 24").penv()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(12.0, 24.0)
    }

    "penv() sets StrudelVoiceData.pEnv" {
        val p = note("a b").penv("12 24")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(12.0, 24.0)
    }

    "penv() works as pattern extension" {
        val p = note("c").penv("12")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pEnv shouldBe 12.0
    }

    "pamt() is an alias for penv()" {
        val p = note("c").pamt("12")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.pEnv shouldBe 12.0
    }

    // ---- pcurve / pcrv ----

    "pcurve dsl interface" {
        val pat = "c4 e4"
        val amount = 0.5

        dslInterfaceTests(
            "pattern.pcurve(v)" to note(pat).pcurve(amount),
            "script pattern.pcurve(v)" to SprudelPattern.compile("""note("$pat").pcurve($amount)"""),
            "string.pcurve(v)" to pat.pcurve(amount),
            "script string.pcurve(v)" to SprudelPattern.compile(""""$pat".pcurve($amount)"""),
            "pcurve(v)" to note(pat).apply(pcurve(amount)),
            "script pcurve(v)" to SprudelPattern.compile("""note("$pat").apply(pcurve($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pCurve shouldBe amount
        }
    }

    "reinterpret voice data as pCurve | seq(\"0.5 1.5\").pcurve()" {
        val p = seq("0.5 1.5").pcurve()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pCurve } shouldBe listOf(0.5, 1.5)
    }

    "pcurve() sets StrudelVoiceData.pCurve" {
        val p = note("a b").pcurve("0.5 1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pCurve } shouldBe listOf(0.5, 1.5)
    }

    "pcurve() works as pattern extension" {
        val p = note("c").pcurve("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pCurve shouldBe 0.5
    }

    "pcrv() is an alias for pcurve()" {
        val p = note("c").pcrv("0.5")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.pCurve shouldBe 0.5
    }

    // ---- panchor / panc ----

    "panchor dsl interface" {
        val pat = "c4 e4"
        val amount = 0.0

        dslInterfaceTests(
            "pattern.panchor(v)" to note(pat).panchor(amount),
            "script pattern.panchor(v)" to SprudelPattern.compile("""note("$pat").panchor($amount)"""),
            "string.panchor(v)" to pat.panchor(amount),
            "script string.panchor(v)" to SprudelPattern.compile(""""$pat".panchor($amount)"""),
            "panchor(v)" to note(pat).apply(panchor(amount)),
            "script panchor(v)" to SprudelPattern.compile("""note("$pat").apply(panchor($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pAnchor shouldBe amount
        }
    }

    "reinterpret voice data as pAnchor | seq(\"0.0 1.0\").panchor()" {
        val p = seq("0.0 1.0").panchor()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.0, 1.0)
    }

    "panchor() sets StrudelVoiceData.pAnchor" {
        val p = note("a b").panchor("0.0 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.0, 1.0)
    }

    "panchor() works as pattern extension" {
        val p = note("c").panchor("0.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAnchor shouldBe 0.0
    }

    "panc() is an alias for panchor()" {
        val p = note("c").panc("0.0")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.pAnchor shouldBe 0.0
    }

    // ---- Combined test ----

    "pitch envelope functions work together" {
        val p = note("c").pattack("0.1").pdecay("0.3").prelease("0.5").penv("12")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
        events[0].data.pDecay shouldBe 0.3
        events[0].data.pRelease shouldBe 0.5
        events[0].data.pEnv shouldBe 12.0
    }
})
