package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPitchEnvelopeSpec : StringSpec({

    // pattack tests

    "pattack() sets StrudelVoiceData.pAttack" {
        val p = pattack("0.1 0.2")
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
        val p = StrudelPattern.compile("""note("c").pattack("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
    }

    "patt() is an alias for pattack()" {
        val p = note("c").patt("0.1")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.pAttack shouldBe 0.1
    }

    // pdecay tests

    "pdecay() sets StrudelVoiceData.pDecay" {
        val p = pdecay("0.3 0.4")
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

    // prelease tests

    "prelease() sets StrudelVoiceData.pRelease" {
        val p = prelease("0.5 0.6")
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

    // penv tests

    "penv() sets StrudelVoiceData.pEnv" {
        val p = penv("12 24")
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

    // pcurve tests

    "pcurve() sets StrudelVoiceData.pCurve" {
        val p = pcurve("0.5 1.5")
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

    // panchor tests

    "panchor() sets StrudelVoiceData.pAnchor" {
        val p = panchor("0.0 1.0")
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

    // Combined test

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
