// strudel/src/commonTest/kotlin/lang/LangFirstOfSpec.kt
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFirstOfSpec : StringSpec({

    "firstOf(n) applies transform on the first cycle" {
        // cycle 0: "b" (transformed)
        // cycle 1: "a" (original)
        // cycle 2: "b" (transformed loop)
        val p = note("a").firstOf(2) { it.note("b") }

        val c0 = p.queryArc(0.0, 1.0)
        c0.size shouldBe 1
        c0[0].data.note shouldBe "b"

        val c1 = p.queryArc(1.0, 2.0)
        c1.size shouldBe 1
        c1[0].data.note shouldBe "a"

        val c2 = p.queryArc(2.0, 3.0)
        c2.size shouldBe 1
        c2[0].data.note shouldBe "b"
    }

    "firstOf(3) applies transform on 1st cycle, original on 2nd and 3rd" {
        // n=3
        // 0 -> transform
        // 1 -> original
        // 2 -> original
        // 3 -> transform
        val p = note("a").firstOf(3) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "a"
        p.queryArc(2.0, 3.0)[0].data.note shouldBe "a"
        p.queryArc(3.0, 4.0)[0].data.note shouldBe "b"
    }

    "firstOf() works as string extension" {
        val p = "a".firstOf(2) { it.note("b") }.note()

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "B"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "A" // "a" parses to note "A" by default
    }

    "firstOf() works as top-level function" {
        // firstOf(n, transform, pattern)
        val p = firstOf(2, { it: StrudelPattern -> it.note("b") })

        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "firstOf(1) always applies transform" {
        val p = note("a").firstOf(1) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "b"
    }

    "every() is an alias for firstOf()" {
        val p = note("a").every(2) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "a"
    }

    "every() works as string extension" {
        val p = "a".every(2) { it.note("b") }.note()

        p.queryArc(0.0, 1.0)[0].data.note shouldBe "B"
        p.queryArc(1.0, 2.0)[0].data.note shouldBe "A"
    }
})
