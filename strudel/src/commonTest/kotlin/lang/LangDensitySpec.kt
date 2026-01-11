package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDensitySpec : StringSpec({

    "density() sets VoiceData.density" {
        val p = density("0.2 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.density } shouldBe listOf(0.2, 0.8)
    }

    "d() alias sets VoiceData.density" {
        val p = d("0.2 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.density } shouldBe listOf(0.2, 0.8)
    }

    "density() works as pattern extension" {
        val p = note("c").density("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.density shouldBe 0.5
    }

    "density() works as string extension" {
        val p = "c".density("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.density shouldBe 0.5
    }

    "density() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").density("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.density shouldBe 0.5
    }
})
