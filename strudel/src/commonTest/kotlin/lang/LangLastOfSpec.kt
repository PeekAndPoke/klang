// strudel/src/commonTest/kotlin/lang/LangLastOfSpec.kt
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLastOfSpec : StringSpec({

    "lastOf(n) applies transform on the last cycle" {
        // lastOf(2)
        // cycle 0: "a" (original)
        // cycle 1: "b" (transformed)
        // cycle 2: "a" (original loop)
        val p = note("a").lastOf(2) { it.note("b") }

        val c0 = p.queryArc(0.0, 1.0)
        c0.size shouldBe 1
        c0[0].data.note shouldBe "a"

        val c1 = p.queryArc(1.0, 2.0)
        c1.size shouldBe 1
        c1[0].data.note shouldBe "b"

        val c2 = p.queryArc(2.0, 3.0)
        c2.size shouldBe 1
        c2[0].data.note shouldBe "a"
    }

    "lastOf(3) applies transform on 3rd cycle" {
        // n=3
        // 0 -> original
        // 1 -> original
        // 2 -> transform
        // 3 -> original
        val p = note("a").lastOf(3) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "a"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "a"
        p.queryArc(2.0, 3.0)[0].data.note shouldBe "b"
        p.queryArc(3.0, 4.0)[0].data.note shouldBe "a"
    }

    "lastOf() works as string extension" {
        val p = "a".lastOf(2) { it.note("b") }.note()

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "A"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "B"
    }

    "lastOf() works as top-level function" {
        // lastOf(n, transform, pattern)
        val p = lastOf(2, { it: StrudelPattern -> it.note("b") }, note("a"))

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "a"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "b"
    }

    "lastOf(1) always applies transform" {
        val p = note("a").lastOf(1) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "b"
    }
})
