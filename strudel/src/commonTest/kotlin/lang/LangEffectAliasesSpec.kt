package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangEffectAliasesSpec : StringSpec({

    // -- roomsize aliases -------------------------------------------------------------------------------------------------

    "roomsize() alias 'sz' sets VoiceData.roomSize correctly" {
        val p = note("c3").sz("0.9")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 0.9
    }

    "roomsize() alias 'sz' works as top-level function" {
        val p = sz("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 0.8
    }

    "roomsize() alias 'sz' works as string extension" {
        val p = "c3".sz("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 0.7
    }

    "roomsize() alias 'size' sets VoiceData.roomSize correctly" {
        val p = note("c3").size("0.95")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 0.95
    }

    "roomsize() alias 'size' works as top-level function" {
        val p = size("0.85")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 0.85
    }

    "roomsize() alias 'size' works as string extension" {
        val p = "c3".size("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 0.75
    }

    "roomsize() aliases work with control patterns" {
        val p = note("c3 e3").sz("0.5 0.9")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.roomSize shouldBe 0.5
        events[1].data.roomSize shouldBe 0.9
    }

    "roomsize() aliases can be chained with room()" {
        val p = note("c3").room("0.5").sz("0.9")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
        events[0].data.roomSize shouldBe 0.9
    }

    "roomsize() aliases work in compiled code" {
        val p = StrudelPattern.compile("""note("c3").room(0.5).sz(0.9)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
        events[0].data.roomSize shouldBe 0.9
    }

    // -- orbit alias ------------------------------------------------------------------------------------------------------

    "orbit() alias 'o' sets VoiceData.orbit correctly" {
        val p = note("c3").o("1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.orbit shouldBe 1
    }

    "orbit() alias 'o' works as top-level function" {
        val p = o("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.orbit shouldBe 2
    }

    "orbit() alias 'o' works as string extension" {
        val p = "c3".o("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.orbit shouldBe 3
    }

    "orbit() alias 'o' works with control patterns" {
        val p = note("c3 e3").o("0 1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.orbit shouldBe 0
        events[1].data.orbit shouldBe 1
    }

    "orbit() alias 'o' works in compiled code" {
        val p = StrudelPattern.compile("""note("c3").o(1)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.orbit shouldBe 1
    }
})
