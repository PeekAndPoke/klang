package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangEngineSpec : StringSpec({

    "engine dsl interface" {
        val pat = "c3"
        val engineVal = "pedal"

        dslInterfaceTests(
            "pattern.engine(v)" to note(pat).engine(engineVal),
            "script pattern.engine(v)" to SprudelPattern.compile("""note("$pat").engine("$engineVal")"""),
            "string.engine(v)" to pat.engine(engineVal),
            "script string.engine(v)" to SprudelPattern.compile(""""$pat".engine("$engineVal")"""),
            "engine(v)" to note(pat).apply(engine(engineVal)),
            "script engine(v)" to SprudelPattern.compile("""note("$pat").apply(engine("$engineVal"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.engine shouldBe "pedal"
        }
    }

    "engine() sets the engine property" {
        val p = note("c3").engine("pedal")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.engine shouldBe "pedal"
    }

    "engine() lowercases the value" {
        val p = note("c3").engine("PEDAL")
        val events = p.queryArc(0.0, 1.0)

        events[0].data.engine shouldBe "pedal"
    }

    "engine() works with string pattern sequences" {
        val p = note("c3 e3").engine("modern pedal")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.engine shouldBe "modern"
        events[1].data.engine shouldBe "pedal"
    }

    "engine() as string extension" {
        val p = "c3 e3".engine("pedal")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.engine shouldBe "pedal"
        events[1].data.engine shouldBe "pedal"
    }

    "engine() as pattern mapper function" {
        val p = note("c3").apply(engine("pedal"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.engine shouldBe "pedal"
    }

    "engine() as chained pattern mapper" {
        val p = note("c3").apply(gain(0.5).engine("pedal"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.engine shouldBe "pedal"
        events[0].data.gain shouldBe 0.5
    }

    "engine() flows through to VoiceData" {
        val p = note("c3").engine("pedal")
        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        voiceData.engine shouldBe "pedal"
    }

    "engine() merge precedence: later value wins" {
        val base = note("c3").engine("modern")
        val override = base.engine("pedal")
        val events = override.queryArc(0.0, 1.0)

        events[0].data.engine shouldBe "pedal"
    }

    "engine not set: defaults to null" {
        val p = note("c3")
        val events = p.queryArc(0.0, 1.0)

        events[0].data.engine shouldBe null
    }
})
