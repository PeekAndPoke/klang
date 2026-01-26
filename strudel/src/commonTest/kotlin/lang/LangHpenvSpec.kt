package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHpenvSpec : StringSpec({

    "hpenv() sets StrudelVoiceData.hpenv" {
        val p = hpenv("0.5 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hpenv shouldBe 0.5
        events[1].data.hpenv shouldBe 0.7
    }

    "hpenv() works as pattern extension" {
        val p = note("c").hpenv("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpenv() works as string extension" {
        val p = "c".hpenv("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpenv() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpenv("0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpenv() with continuous pattern sets hpenv correctly" {
        val p = note("a b c d").hpenv(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hpenv shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hpenv shouldBe (0.0 plusOrMinus EPSILON)
    }

    "hpenv() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hpenv = 0.7
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.depth shouldBe 0.7
    }

    // Alias tests

    "hpe() is an alias for hpenv()" {
        val p = hpe("0.65")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.65
    }

    "hpe() works as pattern extension" {
        val p = note("c").hpe("0.65")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.65
    }

    "hpe() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpe("0.65")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.65
    }
})
