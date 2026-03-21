// sprudel/src/commonTest/kotlin/lang/LangLastOfSpec.kt
package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangLastOfSpec : StringSpec({

    "lastOf dsl interface" {
        dslInterfaceTests(
            "pattern.lastOf(n, transform)" to
                    note("a").lastOf(2) { it.note("b") },
            "script pattern.lastOf(n, transform)" to
                    SprudelPattern.compile("""note("a").lastOf(2, x => x.note("b"))"""),
            "string.lastOf(n, transform)" to
                    "a".lastOf(2) { it.note("b") },
            "script string.lastOf(n, transform)" to
                    SprudelPattern.compile(""""a".lastOf(2, x => x.note("b"))"""),
            "lastOf(n, transform)" to
                    note("a").apply(lastOf(2) { it.note("b") }),
            "script lastOf(n, transform)" to
                    SprudelPattern.compile("""note("a").apply(lastOf(2, x => x.note("b")))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "lastOf(n) applies transform on the last cycle" {
        // lastOf(2)
        // cycle 0: "a" (original)
        // cycle 1: "b" (transformed)
        // cycle 2: "a" (original loop)
        val p = note("a").lastOf(2) { it.note("b") }

        val c0 = p.queryArc(0.0, 1.0)
        c0.size shouldBe 1
        c0[0].data.note shouldBeEqualIgnoringCase "a"

        val c1 = p.queryArc(1.0, 2.0)
        c1.size shouldBe 1
        c1[0].data.note shouldBeEqualIgnoringCase "b"

        val c2 = p.queryArc(2.0, 3.0)
        c2.size shouldBe 1
        c2[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "lastOf(3) applies transform on 3rd cycle" {
        // n=3
        // 0 -> original
        // 1 -> original
        // 2 -> transform
        // 3 -> original
        val p = note("a").lastOf(3) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(2.0, 3.0)[0].data.note shouldBeEqualIgnoringCase "b"
        p.queryArc(3.0, 4.0)[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "lastOf() works as string extension" {
        val p = "a".lastOf(2) { it.note("b") }.note()

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "A"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "B"
    }

    "lastOf() works as top-level PatternMapper" {
        val p = note("a").apply(lastOf(2) { it.note("b") })

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "b"
    }

    "lastOf(1) always applies transform" {
        val p = note("a").lastOf(1) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "b"
    }

    "apply(lastOf().lastOf()) chains two lastOf mappers" {
        // lastOf(2): cycle 0 -> "a" (original), cycle 1 -> "b" (transform)
        // lastOf(2) chained: on cycle 1, where inner gives "b", outer lastOf fires -> "c"
        val p = note("a").apply(
            lastOf(2) { it.note("b") }
                .lastOf(2) { it.note("c") }
        )

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "c"
        p.queryArc(2.0, 3.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(3.0, 4.0)[0].data.note shouldBeEqualIgnoringCase "c"
    }

    "script apply(lastOf()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(lastOf(2, x => x.note("b")))""")!!

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "b"
    }
})
