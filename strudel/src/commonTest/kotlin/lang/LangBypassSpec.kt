// strudel/src/commonTest/kotlin/lang/LangBypassSpec.kt
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangBypassSpec : StringSpec({

    "bypass() with 'true' silences the pattern" {
        val p = note("a").bypass("true")
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "bypass() with 1 silences the pattern" {
        val p = note("a").bypass(1)
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "bypass() with 'false' keeps the pattern" {
        val p = note("a").bypass("false")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "bypass() with 0 keeps the pattern" {
        val p = note("a").bypass(0)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "bypass() with control pattern" {
        // bypass "0 1" -> 1st half: bypass(0)=keep, 2nd half: bypass(1)=silence
        val p = note("a b").bypass("0 1")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "bypass() works as string extension" {
        val p = "a".bypass(1).note()
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "bypass() works as top-level function" {
        val p = bypass(1, note("a"))
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "bypass() with boolean arguments" {
        val p1 = note("a").bypass(true)
        p1.queryArc(0.0, 1.0).size shouldBe 0

        val p2 = note("a").bypass(false)
        p2.queryArc(0.0, 1.0).size shouldBe 1
    }
})
