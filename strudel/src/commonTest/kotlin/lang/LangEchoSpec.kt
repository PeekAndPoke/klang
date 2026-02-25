package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangEchoSpec : StringSpec({

    "echo dsl interface" {
        val pat = "bd sd"
        dslInterfaceTests(
            "pattern.echo(3, 0.125, 0.5)" to s(pat).echo(3, 0.125, 0.5),
            "script pattern.echo(3, 0.125, 0.5)" to StrudelPattern.compile("""s("$pat").echo(3, 0.125, 0.5)"""),
            "string.echo(3, 0.125, 0.5)" to pat.echo(3, 0.125, 0.5),
            "script string.echo(3, 0.125, 0.5)" to StrudelPattern.compile(""""$pat".echo(3, 0.125, 0.5)"""),
            "echo(3, 0.125, 0.5)" to s(pat).apply(echo(3, 0.125, 0.5)),
            "script echo(3, 0.125, 0.5)" to StrudelPattern.compile("""s("$pat").apply(echo(3, 0.125, 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "stut dsl interface" {
        val pat = "bd sd"
        dslInterfaceTests(
            "pattern.stut(3, 0.125, 0.5)" to s(pat).stut(3, 0.125, 0.5),
            "script pattern.stut(3, 0.125, 0.5)" to StrudelPattern.compile("""s("$pat").stut(3, 0.125, 0.5)"""),
            "string.stut(3, 0.125, 0.5)" to pat.stut(3, 0.125, 0.5),
            "script string.stut(3, 0.125, 0.5)" to StrudelPattern.compile(""""$pat".stut(3, 0.125, 0.5)"""),
            "stut(3, 0.125, 0.5)" to s(pat).apply(stut(3, 0.125, 0.5)),
            "script stut(3, 0.125, 0.5)" to StrudelPattern.compile("""s("$pat").apply(stut(3, 0.125, 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "echo() produces the original plus decayed delayed copies" {
        // s("bd sd").echo(3, 0.25, 0.5): 3 layers, each shifted 0.25 cycles
        val p = s("bd sd").echo(3, 0.25, 0.5)
        val events = p.queryArc(0.0, 1.0)

        // original (2) + echo1 (2) + echo2 wraps from/into adjacent cycles = 7 in [0,1)
        events.size shouldBe 7
    }

    "echo(1, ...) returns just the original pattern unchanged" {
        val p = n("0 1 2 3").echo(1, 0.25, 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
    }

    "stut() produces the same result as echo()" {
        val p1 = s("bd sd").echo(3, 0.125, 0.7)
        val p2 = s("bd sd").stut(3, 0.125, 0.7)

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe events2.size
    }
})