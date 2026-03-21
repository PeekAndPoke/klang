package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.mul
import io.peekandpoke.klang.sprudel.lang.seq

class LangNotSpec : StringSpec({

    "not dsl interface" {
        val pat = "1 0"

        dslInterfaceTests(
            "pattern.not" to
                    seq(pat).not(),
            "string.not" to
                    pat.not(),
            "not()" to
                    seq(pat).apply(not),
            "script pattern.not" to
                    SprudelPattern.compile("""seq("$pat").not()"""),
            "script string.not" to
                    SprudelPattern.compile(""""$pat".not()"""),
            "script not()" to
                    SprudelPattern.compile("""seq("$pat").apply(not)"""),
        ) { _, events ->
            events.shouldHaveSize(2)
            events[0].data.isTruthy() shouldBe false   // NOT 1 = false
            events[1].data.isTruthy() shouldBe true    // NOT 0 = true
        }
    }

    "not() inverts truthy values" {
        val p = seq(1.0, 0.0, 1.0, 0.0).not()
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 8

        events[0].data.isTruthy() shouldBe false
        events[1].data.isTruthy() shouldBe true
        events[2].data.isTruthy() shouldBe false
        events[3].data.isTruthy() shouldBe true

        events[4].data.isTruthy() shouldBe false
        events[5].data.isTruthy() shouldBe true
        events[6].data.isTruthy() shouldBe false
        events[7].data.isTruthy() shouldBe true
    }

    "apply(mul().not())" {
        val p = seq("1 0").apply(mul("1").not())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.isTruthy() shouldBe false  // not(1*1)=not(1)=false
            events[1].data.isTruthy() shouldBe true   // not(0*1)=not(0)=true
        }
    }

    "script apply(mul().not())" {
        val p = SprudelPattern.compile("""seq("1 0").apply(mul("1").not())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.isTruthy() shouldBe false  // not(1*1)=not(1)=false
            events[1].data.isTruthy() shouldBe true   // not(0*1)=not(0)=true
        }
    }

    "not() as string extension" {
        val p = "1 0".not()
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4

        events[0].data.isTruthy() shouldBe false
        events[1].data.isTruthy() shouldBe true
        events[2].data.isTruthy() shouldBe false
        events[3].data.isTruthy() shouldBe true
    }
})
