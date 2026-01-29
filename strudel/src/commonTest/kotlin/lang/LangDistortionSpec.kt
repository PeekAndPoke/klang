package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDistortionSpec : StringSpec({

    // distortion tests

    "distortion() sets StrudelVoiceData.distortion" {
        val p = distortion("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortion shouldBe 0.7
    }

    "distortion() works with multiple values" {
        val p = distortion("0.5 0.7 0.9")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.distortion } shouldBe listOf(0.5, 0.7, 0.9)
    }

    "distortion() works as pattern extension" {
        val p = note("c").distortion("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortion shouldBe 0.7
    }

    "distortion() works as string extension" {
        val p = "c".distortion("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortion shouldBe 0.7
    }

    "distortion() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").distortion("0.7")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.distortion shouldBe 0.7
    }

    // shape tests

    "shape() sets StrudelVoiceData.shape" {
        val p = shape("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.shape shouldBe 2.0
    }

    "shape() works with multiple values" {
        val p = shape("1 2 3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.shape } shouldBe listOf(1.0, 2.0, 3.0)
    }

    "shape() works as pattern extension" {
        val p = note("c").shape("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.shape shouldBe 2.0
    }

    "shape() works as string extension" {
        val p = "c".shape("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.shape shouldBe 2.0
    }

    "shape() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").shape("2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.shape shouldBe 2.0
    }

    // Combined test

    "distortion and shape work together" {
        val p = note("c").distortion("0.8").shape("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortion shouldBe 0.8
        events[0].data.shape shouldBe 2.0
    }

    "distortion is separate from distort" {
        val p = note("c").distort("0.5").distortion("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
        events[0].data.distortion shouldBe 0.8
    }
})
