package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangEffectsRoutingSpec : StringSpec({

    // distort
    "top-level distort() sets VoiceData.distort correctly" {
        val p = distort("0.0 2.5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.0, 2.5)
    }

    "control pattern distort() sets VoiceData.distort on existing pattern" {
        val base = note("c3 e3")
        val p = base.distort("1.0 3.0")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.distort } shouldBe listOf(1.0, 3.0, 1.0, 3.0)
    }

    // crush
    "top-level crush() sets VoiceData.crush correctly" {
        val p = crush("8 4")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.crush } shouldBe listOf(8.0, 4.0)
    }

    "control pattern crush() sets VoiceData.crush on existing pattern" {
        val base = note("c3 e3")
        val p = base.crush("12 6")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.crush } shouldBe listOf(12.0, 6.0, 12.0, 6.0)
    }

    // coarse
    "top-level coarse() sets VoiceData.coarse correctly" {
        val p = coarse("1 2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.coarse } shouldBe listOf(1.0, 2.0)
    }

    "control pattern coarse() sets VoiceData.coarse on existing pattern" {
        val base = note("c3 e3")
        val p = base.coarse("3 4")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.coarse } shouldBe listOf(3.0, 4.0, 3.0, 4.0)
    }

    // room
    "top-level room() sets VoiceData.room correctly" {
        val p = room("0.1 0.9")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.1, 0.9)
    }

    "control pattern room() sets VoiceData.room on existing pattern" {
        val base = note("c3 e3")
        val p = base.room("0.3 0.6")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.room } shouldBe listOf(0.3, 0.6, 0.3, 0.6)
    }

    // roomsize / rsize alias
    "top-level roomsize() sets VoiceData.roomSize correctly (and rsize alias)" {
        val p1 = roomsize("0.2 0.8")
        val e1 = p1.queryArc(0.0, 1.0)
        e1.size shouldBe 2
        e1.map { it.data.roomSize } shouldBe listOf(0.2, 0.8)

        val p2 = rsize("0.4 0.6")
        val e2 = p2.queryArc(0.0, 1.0)
        e2.size shouldBe 2
        e2.map { it.data.roomSize } shouldBe listOf(0.4, 0.6)
    }

    "control pattern roomsize()/rsize sets VoiceData.roomSize on existing pattern" {
        val base = note("c3 e3")
        val p = base.rsize("0.1 0.3")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.roomSize } shouldBe listOf(0.1, 0.3, 0.1, 0.3)
    }

    // delay
    "top-level delay() sets VoiceData.delay correctly" {
        val p = delay("0.0 1.0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.0, 1.0)
    }

    "control pattern delay() sets VoiceData.delay on existing pattern" {
        val base = note("c3 e3")
        val p = base.delay("0.25 0.5")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.delay } shouldBe listOf(0.25, 0.5, 0.25, 0.5)
    }

    // delaytime
    "top-level delaytime() sets VoiceData.delayTime correctly" {
        val p = delaytime("0.125 0.25")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.delayTime } shouldBe listOf(0.125, 0.25)
    }

    "control pattern delaytime() sets VoiceData.delayTime on existing pattern" {
        val base = note("c3 e3")
        val p = base.delaytime("0.0625 0.5")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.delayTime } shouldBe listOf(0.0625, 0.5, 0.0625, 0.5)
    }

    // delayfeedback
    "top-level delayfeedback() sets VoiceData.delayFeedback correctly" {
        val p = delayfeedback("0.25 0.75")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.delayFeedback } shouldBe listOf(0.25, 0.75)
    }

    "control pattern delayfeedback() sets VoiceData.delayFeedback on existing pattern" {
        val base = note("c3 e3")
        val p = base.delayfeedback("0.1 0.9")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.delayFeedback } shouldBe listOf(0.1, 0.9, 0.1, 0.9)
    }

    // orbit
    "top-level orbit() sets VoiceData.orbit correctly" {
        val p = orbit("0 2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.orbit } shouldBe listOf(0, 2)
    }

    "control pattern orbit() sets VoiceData.orbit on existing pattern" {
        val base = note("c3 e3")
        val p = base.orbit("1 3")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.orbit } shouldBe listOf(1, 3, 1, 3)
    }

    "distort() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""distort("0 1")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.0, 1.0)
    }

    "distort() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").distort("0 1")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.0, 1.0)
    }

    "crush() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""crush("8 4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.crush } shouldBe listOf(8.0, 4.0)
    }

    "crush() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").crush("8 4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.crush } shouldBe listOf(8.0, 4.0)
    }

    "coarse() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""coarse("1 2")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.coarse } shouldBe listOf(1.0, 2.0)
    }

    "coarse() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").coarse("1 2")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.coarse } shouldBe listOf(1.0, 2.0)
    }

    "room() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""room("0.1 0.9")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.1, 0.9)
    }

    "room() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").room("0.1 0.9")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.1, 0.9)
    }

    "roomsize() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""roomsize("0.2 0.8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.roomSize } shouldBe listOf(0.2, 0.8)
    }

    "roomsize() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").roomsize("0.2 0.8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.roomSize } shouldBe listOf(0.2, 0.8)
    }

    "delay() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""delay("0.0 1.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.0, 1.0)
    }

    "delay() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").delay("0.0 1.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.0, 1.0)
    }

    "delaytime() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""delaytime("0.125 0.25")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delayTime } shouldBe listOf(0.125, 0.25)
    }

    "delaytime() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").delaytime("0.125 0.25")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delayTime } shouldBe listOf(0.125, 0.25)
    }

    "delayfeedback() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""delayfeedback("0.25 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delayFeedback } shouldBe listOf(0.25, 0.75)
    }

    "delayfeedback() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").delayfeedback("0.25 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delayFeedback } shouldBe listOf(0.25, 0.75)
    }

    "orbit() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""orbit("0 2")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.orbit } shouldBe listOf(0, 2)
    }

    "orbit() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").orbit("0 2")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.orbit } shouldBe listOf(0, 2)
    }
})
