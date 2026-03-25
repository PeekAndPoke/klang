package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.gain
import io.peekandpoke.klang.sprudel.lang.note

class LangSndPluckSpec : StringSpec({

    // -- dsl interface tests -----------------------------------------------------------------------------------------

    "sndPluck() dsl interface" {
        dslInterfaceTests(
            "pattern.sndPluck()" to note("c3").sndPluck("0.99:0.8"),
            "string.sndPluck()" to "c3".sndPluck("0.99:0.8"),
            "script pattern.sndPluck()" to SprudelPattern.compile("""note("c3").sndPluck("0.99:0.8")"""),
            "script string.sndPluck()" to SprudelPattern.compile(""""c3".sndPluck("0.99:0.8")"""),
            "apply(sndPluck())" to note("c3").apply(sndPluck("0.99:0.8")),
            "script apply(sndPluck())" to SprudelPattern.compile("""note("c3").apply(sndPluck("0.99:0.8"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "pluck"
                events[0].data.oscParams?.get("decay") shouldBe 0.99
                events[0].data.oscParams?.get("brightness") shouldBe 0.8
            }
        }
    }

    // -- no params ---------------------------------------------------------------------------------------------------

    "sndPluck() with no params sets sound to pluck" {
        val p = note("c3").sndPluck()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "pluck"
        events[0].data.oscParams?.get("decay") shouldBe null
    }

    // -- single param ------------------------------------------------------------------------------------------------

    "sndPluck(\"0.99\") sets decay only" {
        val p = note("c3").sndPluck("0.99")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "pluck"
        events[0].data.oscParams?.get("decay") shouldBe 0.99
        events[0].data.oscParams?.get("brightness") shouldBe null
    }

    // -- two params --------------------------------------------------------------------------------------------------

    "sndPluck(\"0.99:0.8\") sets decay and brightness" {
        val p = note("c3").sndPluck("0.99:0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "pluck"
        events[0].data.oscParams?.get("decay") shouldBe 0.99
        events[0].data.oscParams?.get("brightness") shouldBe 0.8
    }

    // -- all four params ---------------------------------------------------------------------------------------------

    "sndPluck(\"0.996:0.5:0.2:0.3\") sets all four params" {
        val p = note("c3").sndPluck("0.996:0.5:0.2:0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            sound shouldBe "pluck"
            oscParams?.get("decay") shouldBe 0.996
            oscParams?.get("brightness") shouldBe 0.5
            oscParams?.get("pickPosition") shouldBe 0.2
            oscParams?.get("stiffness") shouldBe 0.3
        }
    }

    // -- control patterns --------------------------------------------------------------------------------------------

    "sndPluck() works with control pattern" {
        val p = note("c3 e3").sndPluck("0.99:0.8 0.95:0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.sound shouldBe "pluck"
        events[0].data.oscParams?.get("decay") shouldBe 0.99
        events[0].data.oscParams?.get("brightness") shouldBe 0.8
        events[1].data.oscParams?.get("decay") shouldBe 0.95
        events[1].data.oscParams?.get("brightness") shouldBe 0.3
    }

    // -- compiled scripts --------------------------------------------------------------------------------------------

    "sndPluck() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").sndPluck("0.99:0.8:0.2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.sound shouldBe "pluck"
        events[0].data.oscParams?.get("decay") shouldBe 0.99
        events[0].data.oscParams?.get("brightness") shouldBe 0.8
        events[0].data.oscParams?.get("pickPosition") shouldBe 0.2
    }

    // -- string extension --------------------------------------------------------------------------------------------

    "sndPluck() works as string extension" {
        val p = "c3".sndPluck("0.996")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "pluck"
        events[0].data.oscParams?.get("decay") shouldBe 0.996
    }

    // -- chaining ----------------------------------------------------------------------------------------------------

    "sndPluck() can be chained with other effects" {
        val p = note("c3").sndPluck("0.99:0.8").gain("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "pluck"
        events[0].data.oscParams?.get("decay") shouldBe 0.99
        events[0].data.gain shouldBe 0.5
    }
})
