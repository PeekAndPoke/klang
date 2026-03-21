package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.addons.reverb

class LangReverbSpec : StringSpec({

    // -- roomfade() -------------------------------------------------------------------------------------------------------

    "roomfade() sets VoiceData.roomFade correctly" {
        val p = note("c3").roomfade("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomFade shouldBe 0.5
    }

    "roomfade() alias 'rfade' works" {
        val p = note("c3").rfade("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomFade shouldBe 0.8
    }

    "roomfade() works as top-level function" {
        val p = note("a").apply(roomfade("0.3"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomFade shouldBe 0.3
    }

    "roomfade() works with control pattern" {
        val p = note("c3 e3").roomfade("0.2 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.roomFade shouldBe 0.2
        events[1].data.roomFade shouldBe 0.6
    }

    "roomfade() works as string extension" {
        val p = "c3".roomfade("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomFade shouldBe 0.4
    }

    // -- roomlp() ---------------------------------------------------------------------------------------------------------

    "roomlp() sets VoiceData.roomLp correctly" {
        val p = note("c3").roomlp("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomLp shouldBe 1000.0
    }

    "roomlp() alias 'rlp' works" {
        val p = note("c3").rlp("2000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomLp shouldBe 2000.0
    }

    "roomlp() works as top-level function" {
        val p = note("a").apply(roomlp("500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomLp shouldBe 500.0
    }

    "roomlp() works with control pattern" {
        val p = note("c3 e3").roomlp("800 1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.roomLp shouldBe 800.0
        events[1].data.roomLp shouldBe 1200.0
    }

    "roomlp() works as string extension" {
        val p = "c3".roomlp("1500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomLp shouldBe 1500.0
    }

    // -- roomdim() --------------------------------------------------------------------------------------------------------

    "roomdim() sets VoiceData.roomDim correctly" {
        val p = note("c3").roomdim("5000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomDim shouldBe 5000.0
    }

    "roomdim() alias 'rdim' works" {
        val p = note("c3").rdim("6000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomDim shouldBe 6000.0
    }

    "roomdim() works as top-level function" {
        val p = note("a").apply(roomdim("3000"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomDim shouldBe 3000.0
    }

    "roomdim() works with control pattern" {
        val p = note("c3 e3").roomdim("4000 7000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.roomDim shouldBe 4000.0
        events[1].data.roomDim shouldBe 7000.0
    }

    "roomdim() works as string extension" {
        val p = "c3".roomdim("8000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomDim shouldBe 8000.0
    }

    // -- iresponse() ------------------------------------------------------------------------------------------------------

    "iresponse() sets VoiceData.iResponse correctly" {
        val p = note("c3").iresponse("hall")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.iResponse shouldBe "hall"
    }

    "iresponse() alias 'ir' works" {
        val p = note("c3").ir("plate")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.iResponse shouldBe "plate"
    }

    "iresponse() works as top-level function" {
        val p = note("a").apply(iresponse("chamber"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.iResponse shouldBe "chamber"
    }

    "iresponse() works with control pattern (string sequence)" {
        val p = note("c3 e3").iresponse("hall plate")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.iResponse shouldBe "hall"
        events[1].data.iResponse shouldBe "plate"
    }

    "iresponse() works as string extension" {
        val p = "c3".iresponse("spring")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.iResponse shouldBe "spring"
    }

    // -- combined tests ---------------------------------------------------------------------------------------------------

    "reverb functions can be chained together" {
        val p = note("c3")
            .room("0.8")
            .roomsize("0.9")
            .roomfade("0.5")
            .roomlp("1000")
            .roomdim("5000")
            .iresponse("hall")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.8
        events[0].data.roomSize shouldBe 0.9
        events[0].data.roomFade shouldBe 0.5
        events[0].data.roomLp shouldBe 1000.0
        events[0].data.roomDim shouldBe 5000.0
        events[0].data.iResponse shouldBe "hall"
    }

    "reverb functions work with aliases chained" {
        val p = note("c3")
            .room("0.7")
            .sz("0.85")
            .rfade("0.4")
            .rlp("2000")
            .rdim("6000")
            .ir("plate")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.7
        events[0].data.roomSize shouldBe 0.85
        events[0].data.roomFade shouldBe 0.4
        events[0].data.roomLp shouldBe 2000.0
        events[0].data.roomDim shouldBe 6000.0
        events[0].data.iResponse shouldBe "plate"
    }

    "reverb functions work in compiled code" {
        val p = SprudelPattern.compile("""note("c3").room(0.8).roomfade(0.5).roomlp(1000).iresponse("hall")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.room shouldBe 0.8
        events[0].data.roomFade shouldBe 0.5
        events[0].data.roomLp shouldBe 1000.0
        events[0].data.iResponse shouldBe "hall"
    }

    // -- reverb() addon (combined) ----------------------------------------------------------------------------------------

    "reverb() addon dsl interface" {
        val pat = "0 1"
        val ctrl = "0.8:2:0.5:8000:6000"

        dslInterfaceTests(
            "pattern.reverb(ctrl)" to
                    seq(pat).reverb(ctrl),
            "script pattern.reverb(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").reverb("$ctrl")"""),
            "string.reverb(ctrl)" to
                    pat.reverb(ctrl),
            "script string.reverb(ctrl)" to
                    SprudelPattern.compile(""""$pat".reverb("$ctrl")"""),
            "reverb(ctrl)" to
                    seq(pat).apply(reverb(ctrl)),
            "script reverb(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(reverb("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.room shouldBe 0.8
                events[0].data.roomSize shouldBe 2.0
                events[0].data.roomFade shouldBe 0.5
                events[0].data.roomLp shouldBe 8000.0
                events[0].data.roomDim shouldBe 6000.0
            }
        }
    }

    "reverb() addon sets all five VoiceData fields" {
        val p = note("c").reverb("0.5:4:0.3:10000:5000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            room shouldBe 0.5
            roomSize shouldBe 4.0
            roomFade shouldBe 0.3
            roomLp shouldBe 10000.0
            roomDim shouldBe 5000.0
        }
    }

    "reverb() addon with partial params sets only specified fields" {
        val p = note("c").reverb("0.8:2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            room shouldBe 0.8
            roomSize shouldBe 2.0
            roomFade shouldBe null
            roomLp shouldBe null
            roomDim shouldBe null
        }
    }

    "reverb() addon with single param sets only room" {
        val p = note("c").reverb("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            room shouldBe 0.6
            roomSize shouldBe null
            roomFade shouldBe null
        }
    }

    "reverb() addon works as string extension" {
        val p = "c".reverb("0.5:3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            room shouldBe 0.5
            roomSize shouldBe 3.0
        }
    }

    "reverb() addon works in compiled code" {
        val p = SprudelPattern.compile("""note("c").reverb("0.8:2:0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            room shouldBe 0.8
            roomSize shouldBe 2.0
            roomFade shouldBe 0.5
        }
    }

    "reverb() addon works with mini-notation patterns" {
        val p = note("c3 e3").reverb("<0.3:1 0.8:4>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.room shouldBe 0.3
            cycle0[0].data.roomSize shouldBe 1.0

            cycle1.size shouldBe 2
            cycle1[0].data.room shouldBe 0.8
            cycle1[0].data.roomSize shouldBe 4.0
        }
    }

    "reverb() addon works chained with other effects" {
        val p = note("c").apply(gain(0.8).reverb("0.5:2"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            room shouldBe 0.5
            roomSize shouldBe 2.0
        }
    }
})
