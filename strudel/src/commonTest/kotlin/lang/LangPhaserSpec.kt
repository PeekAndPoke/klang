package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPhaserSpec : StringSpec({

    // -- phaser() ---------------------------------------------------------------------------------------------------------

    "phaser() sets VoiceData.phaser correctly" {
        val p = note("c3").phaser("2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaser shouldBe 2.0
    }

    "phaser() alias 'ph' works" {
        val p = note("c3").ph("3.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaser shouldBe 3.0
    }

    "phaser() works as top-level function" {
        val p = phaser("1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaser shouldBe 1.5
    }

    "phaser() works with control pattern" {
        val p = note("c3 e3").phaser("1.0 2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.phaser shouldBe 1.0
        events[1].data.phaser shouldBe 2.0
    }

    // -- phaserdepth() ----------------------------------------------------------------------------------------------------

    "phaserdepth() sets VoiceData.phaserDepth correctly" {
        val p = note("c3").phaserdepth("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserDepth shouldBe 0.8
    }

    "phaserdepth() alias 'phd' works" {
        val p = note("c3").phd("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserDepth shouldBe 0.5
    }

    "phaserdepth() alias 'phasdp' works" {
        val p = note("c3").phasdp("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserDepth shouldBe 0.7
    }

    "phaserdepth() works with control pattern" {
        val p = note("c3 e3").phaserdepth("0.3 0.9")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.phaserDepth shouldBe 0.3
        events[1].data.phaserDepth shouldBe 0.9
    }

    // -- phasercenter() ---------------------------------------------------------------------------------------------------

    "phasercenter() sets VoiceData.phaserCenter correctly" {
        val p = note("c3").phasercenter("500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserCenter shouldBe 500.0
    }

    "phasercenter() alias 'phc' works" {
        val p = note("c3").phc("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserCenter shouldBe 1000.0
    }

    "phasercenter() works with control pattern" {
        val p = note("c3 e3").phasercenter("300 700")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.phaserCenter shouldBe 300.0
        events[1].data.phaserCenter shouldBe 700.0
    }

    // -- phasersweep() ----------------------------------------------------------------------------------------------------

    "phasersweep() sets VoiceData.phaserSweep correctly" {
        val p = note("c3").phasersweep("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserSweep shouldBe 1000.0
    }

    "phasersweep() alias 'phs' works" {
        val p = note("c3").phs("2000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserSweep shouldBe 2000.0
    }

    "phasersweep() works with control pattern" {
        val p = note("c3 e3").phasersweep("500 1500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.phaserSweep shouldBe 500.0
        events[1].data.phaserSweep shouldBe 1500.0
    }

    // -- combined tests ---------------------------------------------------------------------------------------------------

    "phaser functions can be chained together" {
        val p = note("c3").phaser("2.0").phaserdepth("0.8").phasercenter("500").phasersweep("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaser shouldBe 2.0
        events[0].data.phaserDepth shouldBe 0.8
        events[0].data.phaserCenter shouldBe 500.0
        events[0].data.phaserSweep shouldBe 1000.0
    }

    "phaser functions work in compiled code" {
        val p = StrudelPattern.compile("""note("c3").phaser(2).phaserdepth(0.8)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.phaser shouldBe 2.0
        events[0].data.phaserDepth shouldBe 0.8
    }
})
