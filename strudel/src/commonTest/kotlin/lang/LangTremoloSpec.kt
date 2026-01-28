package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangTremoloSpec : StringSpec({

    // -- tremolosync() ----------------------------------------------------------------------------------------------------

    "tremolosync() sets VoiceData.tremoloSync correctly" {
        val p = note("c3").tremolosync("4.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 4.0
    }

    "tremolosync() alias 'tremsync' works" {
        val p = note("c3").tremsync("8.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 8.0
    }

    "tremolosync() works as top-level function" {
        val p = tremolosync("2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 2.0
    }

    "tremolosync() works with control pattern" {
        val p = note("c3 e3").tremolosync("2.0 4.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloSync shouldBe 2.0
        events[1].data.tremoloSync shouldBe 4.0
    }

    // -- tremolodepth() ---------------------------------------------------------------------------------------------------

    "tremolodepth() sets VoiceData.tremoloDepth correctly" {
        val p = note("c3").tremolodepth("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.5
    }

    "tremolodepth() alias 'tremdepth' works" {
        val p = note("c3").tremdepth("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.8
    }

    "tremolodepth() works with control pattern" {
        val p = note("c3 e3").tremolodepth("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloDepth shouldBe 0.3
        events[1].data.tremoloDepth shouldBe 0.7
    }

    // -- tremoloskew() ----------------------------------------------------------------------------------------------------

    "tremoloskew() sets VoiceData.tremoloSkew correctly" {
        val p = note("c3").tremoloskew("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSkew shouldBe 0.6
    }

    "tremoloskew() alias 'tremskew' works" {
        val p = note("c3").tremskew("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSkew shouldBe 0.4
    }

    "tremoloskew() works with control pattern" {
        val p = note("c3 e3").tremoloskew("0.2 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloSkew shouldBe 0.2
        events[1].data.tremoloSkew shouldBe 0.8
    }

    // -- tremolophase() ---------------------------------------------------------------------------------------------------

    "tremolophase() sets VoiceData.tremoloPhase correctly" {
        val p = note("c3").tremolophase("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloPhase shouldBe 0.25
    }

    "tremolophase() alias 'tremphase' works" {
        val p = note("c3").tremphase("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloPhase shouldBe 0.5
    }

    "tremolophase() works with control pattern" {
        val p = note("c3 e3").tremolophase("0.0 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloPhase shouldBe 0.0
        events[1].data.tremoloPhase shouldBe 0.5
    }

    // -- tremoloshape() ---------------------------------------------------------------------------------------------------

    "tremoloshape() sets VoiceData.tremoloShape correctly" {
        val p = note("c3").tremoloshape("sine")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloShape shouldBe "sine"
    }

    "tremoloshape() alias 'tremshape' works" {
        val p = note("c3").tremshape("square")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloShape shouldBe "square"
    }

    "tremoloshape() works as top-level function" {
        val p = tremoloshape("tri")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloShape shouldBe "tri"
    }

    "tremoloshape() works with control pattern (string sequence)" {
        val p = note("c3 e3").tremoloshape("sine square")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloShape shouldBe "sine"
        events[1].data.tremoloShape shouldBe "square"
    }

    "tremoloshape() converts to lowercase" {
        val p = note("c3").tremoloshape("SINE")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloShape shouldBe "sine"
    }

    // -- combined tests ---------------------------------------------------------------------------------------------------

    "tremolo functions can be chained together" {
        val p = note("c3")
            .tremolosync("4.0")
            .tremolodepth("0.5")
            .tremoloskew("0.6")
            .tremolophase("0.25")
            .tremoloshape("sine")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 4.0
        events[0].data.tremoloDepth shouldBe 0.5
        events[0].data.tremoloSkew shouldBe 0.6
        events[0].data.tremoloPhase shouldBe 0.25
        events[0].data.tremoloShape shouldBe "sine"
    }

    "tremolo functions work in compiled code" {
        val p = StrudelPattern.compile("""note("c3").tremolosync(4).tremolodepth(0.5).tremoloshape("sine")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 4.0
        events[0].data.tremoloDepth shouldBe 0.5
        events[0].data.tremoloShape shouldBe "sine"
    }
})
