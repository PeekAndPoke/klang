/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangReverbSpec : StringSpec({

    // -- room(fade = ...) -------------------------------------------------------------------------------------------------------

    "room(fade = ...) sets VoiceData.roomFade correctly" {
        val p = note("c3").room(fade = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomFade shouldBe 0.5
    }

    "room(fade = ...) works as top-level function" {
        val p = note("a").apply(room(fade = "0.3"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomFade shouldBe 0.3
    }

    "room(fade = ...) works with control pattern" {
        val p = note("c3 e3").room(fade = "0.2 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.roomFade shouldBe 0.2
        events[1].data.roomFade shouldBe 0.6
    }

    "room(fade = ...) works as string extension" {
        val p = "c3".room(fade = "0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomFade shouldBe 0.4
    }

    // -- room(lowpass = ...) ---------------------------------------------------------------------------------------------------------

    "room(lowpass = ...) sets VoiceData.roomLp correctly" {
        val p = note("c3").room(lowpass = "1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomLp shouldBe 1000.0
    }

    "room(lowpass = ...) works as top-level function" {
        val p = note("a").apply(room(lowpass = "500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomLp shouldBe 500.0
    }

    "room(lowpass = ...) works with control pattern" {
        val p = note("c3 e3").room(lowpass = "800 1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.roomLp shouldBe 800.0
        events[1].data.roomLp shouldBe 1200.0
    }

    "room(lowpass = ...) works as string extension" {
        val p = "c3".room(lowpass = "1500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomLp shouldBe 1500.0
    }

    // -- room(dim = ...) --------------------------------------------------------------------------------------------------------

    "room(dim = ...) sets VoiceData.roomDim correctly" {
        val p = note("c3").room(dim = "5000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomDim shouldBe 5000.0
    }

    "room(dim = ...) works as top-level function" {
        val p = note("a").apply(room(dim = "3000"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomDim shouldBe 3000.0
    }

    "room(dim = ...) works with control pattern" {
        val p = note("c3 e3").room(dim = "4000 7000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.roomDim shouldBe 4000.0
        events[1].data.roomDim shouldBe 7000.0
    }

    "room(dim = ...) works as string extension" {
        val p = "c3".room(dim = "8000")
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

    // -- per-param tests --------------------------------------------------------------------------------

    "reverb functions can be chained together" {
        val p = note("c3")
            .room("0.8")
            .room(size = "0.9")
            .room(fade = "0.5")
            .room(lowpass = "1000")
            .room(dim = "5000")
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

    "reverb functions work in compiled code" {
        val p = SprudelPattern.compile("""note("c3").room(wet = 0.8, fade = 0.5, lowpass = 1000).iresponse("hall")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.room shouldBe 0.8
        events[0].data.roomFade shouldBe 0.5
        events[0].data.roomLp shouldBe 1000.0
        events[0].data.iResponse shouldBe "hall"
    }

    "room() addon sets all five VoiceData fields" {
        val p = note("c").room(0.5, 4, 0.3, 10000, 5000)
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

    "room() addon with partial params sets only specified fields" {
        val p = note("c").room(0.8, 2)
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

    "room() addon with single param sets only room" {
        val p = note("c").room(0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            room shouldBe 0.6
            roomSize shouldBe null
            roomFade shouldBe null
        }
    }

    "room() addon works as string extension" {
        val p = "c".room(0.5, 3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            room shouldBe 0.5
            roomSize shouldBe 3.0
        }
    }

    "room() addon works in compiled code" {
        val p = SprudelPattern.compile("""note("c").room(0.8, 2, 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            room shouldBe 0.8
            roomSize shouldBe 2.0
            roomFade shouldBe 0.5
        }
    }

    "room() addon works with mini-notation patterns" {
        val p = note("c3 e3").room("<0.3 0.8>", "<1 4>")
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

    "room() addon works chained with other effects" {
        val p = note("c").apply(gain(0.8).room(0.5, 2))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            room shouldBe 0.5
            roomSize shouldBe 2.0
        }
    }

    "room(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the room field
        val p = SprudelPattern.compile("""seq("3 4").room(size = 8)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.room shouldBe null
        events[0].data.roomSize shouldBe 8.0
    }
})
