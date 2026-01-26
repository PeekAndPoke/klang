package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHpreleaseSpec : StringSpec({

    "hprelease() sets StrudelVoiceData.hprelease" {
        val p = hprelease("0.3 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hprelease shouldBe 0.3
        events[1].data.hprelease shouldBe 0.5
    }

    "hprelease() works as pattern extension" {
        val p = note("c").hprelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.4
    }

    "hprelease() works as string extension" {
        val p = "c".hprelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.4
    }

    "hprelease() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hprelease("0.4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.4
    }

    "hprelease() with continuous pattern sets hprelease correctly" {
        val p = note("a b c d").hprelease(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hprelease shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hprelease shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hprelease shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hprelease shouldBe (0.0 plusOrMinus EPSILON)
    }

    "hprelease() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hprelease = 0.5
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.release shouldBe 0.5
    }

    // Alias tests

    "hpr() is an alias for hprelease()" {
        val p = hpr("0.45")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.45
    }

    "hpr() works as pattern extension" {
        val p = note("c").hpr("0.45")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.45
    }

    "hpr() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpr("0.45")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.45
    }
})
