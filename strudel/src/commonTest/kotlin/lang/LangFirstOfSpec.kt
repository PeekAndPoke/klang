// strudel/src/commonTest/kotlin/lang/LangFirstOfSpec.kt
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangFirstOfSpec : StringSpec({

    "firstOf dsl interface" {
        dslInterfaceTests(
            "pattern.firstOf(n, transform)" to
                    note("a").firstOf(2) { it.note("b") },
            "script pattern.firstOf(n, transform)" to
                    StrudelPattern.compile("""note("a").firstOf(2, x => x.note("b"))"""),
            "string.firstOf(n, transform)" to
                    "a".firstOf(2) { it.note("b") },
            "script string.firstOf(n, transform)" to
                    StrudelPattern.compile(""""a".firstOf(2, x => x.note("b"))"""),
            "firstOf(n, transform)" to
                    note("a").apply(firstOf(2) { it.note("b") }),
            "script firstOf(n, transform)" to
                    StrudelPattern.compile("""note("a").apply(firstOf(2, x => x.note("b")))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "every dsl interface" {
        dslInterfaceTests(
            "pattern.every(n, transform)" to
                    note("a").every(2) { it.note("b") },
            "script pattern.every(n, transform)" to
                    StrudelPattern.compile("""note("a").every(2, x => x.note("b"))"""),
            "string.every(n, transform)" to
                    "a".every(2) { it.note("b") },
            "script string.every(n, transform)" to
                    StrudelPattern.compile(""""a".every(2, x => x.note("b"))"""),
            "every(n, transform)" to
                    note("a").apply(every(2) { it.note("b") }),
            "script every(n, transform)" to
                    StrudelPattern.compile("""note("a").apply(every(2, x => x.note("b")))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "firstOf(n) applies transform on the first cycle" {
        // cycle 0: "b" (transformed)
        // cycle 1: "a" (original)
        // cycle 2: "b" (transformed loop)
        val p = note("a").firstOf(2) { it.note("b") }

        val c0 = p.queryArc(0.0, 1.0)
        c0.size shouldBe 1
        c0[0].data.note shouldBeEqualIgnoringCase "b"

        val c1 = p.queryArc(1.0, 2.0)
        c1.size shouldBe 1
        c1[0].data.note shouldBeEqualIgnoringCase "a"

        val c2 = p.queryArc(2.0, 3.0)
        c2.size shouldBe 1
        c2[0].data.note shouldBeEqualIgnoringCase "b"
    }

    "firstOf(3) applies transform on 1st cycle, original on 2nd and 3rd" {
        // n=3
        // 0 -> transform
        // 1 -> original
        // 2 -> original
        // 3 -> transform
        val p = note("a").firstOf(3) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(2.0, 3.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(3.0, 4.0)[0].data.note shouldBeEqualIgnoringCase "b"
    }

    "firstOf() works as string extension" {
        val p = "a".firstOf(2) { it.note("b") }.note()

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "B"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "A" // "a" parses to note "A" by default
    }

    "firstOf() works as top-level PatternMapper" {
        val p = note("a").apply(firstOf(2) { it.note("b") })

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "firstOf(1) always applies transform" {
        val p = note("a").firstOf(1) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "b"
    }

    "every() is an alias for firstOf()" {
        val p = note("a").every(2) { it.note("b") }

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "every() works as string extension" {
        val p = "a".every(2) { it.note("b") }.note()

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "B"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "A"
    }

    "apply(firstOf().firstOf()) chains two firstOf mappers" {
        // firstOf(2): cycle 0 -> "b", cycle 1 -> "a"
        // firstOf(2) chained: cycle 0 -> "c" (second transform fires on cycle 0), cycle 1 -> "a"
        val p = note("a").apply(
            firstOf(2) { it.note("b") }
                .firstOf(2) { it.note("c") }
        )

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "c"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "a"
        p.queryArc(2.0, 3.0)[0].data.note shouldBeEqualIgnoringCase "c"
        p.queryArc(3.0, 4.0)[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "script apply(firstOf()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(firstOf(2, x => x.note("b")))""")!!

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "apply(every().every()) chains two every mappers" {
        // every is an alias for firstOf: cycle 0 -> "c" (both fire), cycle 1 -> "a"
        val p = note("a").apply(
            every(2) { it.note("b") }
                .every(2) { it.note("c") }
        )

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "c"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "script apply(every()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(every(2, x => x.note("b")))""")!!

        p.queryArc(0.0, 1.0)[0].data.note shouldBeEqualIgnoringCase "b"
        p.queryArc(1.0, 2.0)[0].data.note shouldBeEqualIgnoringCase "a"
    }
})
