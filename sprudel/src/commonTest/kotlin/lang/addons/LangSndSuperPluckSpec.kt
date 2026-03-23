package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note

class LangSndSuperPluckSpec : StringSpec({

    "sndSuperPluck() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSuperPluck()" to note("c3").sndSuperPluck("7:0.3:0.99:0.8"),
            "string.sndSuperPluck()" to "c3".sndSuperPluck("7:0.3:0.99:0.8"),
            "script pattern.sndSuperPluck()" to SprudelPattern.compile("""note("c3").sndSuperPluck("7:0.3:0.99:0.8")"""),
            "script string.sndSuperPluck()" to SprudelPattern.compile(""""c3".sndSuperPluck("7:0.3:0.99:0.8")"""),
            "apply(sndSuperPluck())" to note("c3").apply(sndSuperPluck("7:0.3:0.99:0.8")),
            "script apply(sndSuperPluck())" to SprudelPattern.compile("""note("c3").apply(sndSuperPluck("7:0.3:0.99:0.8"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "superpluck"
                events[0].data.oscParams?.get("voices") shouldBe 7.0
                events[0].data.oscParams?.get("freqSpread") shouldBe 0.3
                events[0].data.oscParams?.get("decay") shouldBe 0.99
                events[0].data.oscParams?.get("brightness") shouldBe 0.8
            }
        }
    }

    "sndSuperPluck() with no params sets sound" {
        val p = note("c3").sndSuperPluck()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "superpluck"
    }

    "sndSuperPluck() with all six params" {
        val p = note("c3").sndSuperPluck("7:0.3:0.996:0.5:0.2:0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            sound shouldBe "superpluck"
            oscParams?.get("voices") shouldBe 7.0
            oscParams?.get("freqSpread") shouldBe 0.3
            oscParams?.get("decay") shouldBe 0.996
            oscParams?.get("brightness") shouldBe 0.5
            oscParams?.get("pickPosition") shouldBe 0.2
            oscParams?.get("stiffness") shouldBe 0.4
        }
    }

    "sndSuperPluck() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").sndSuperPluck("5:0.2:0.99")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.sound shouldBe "superpluck"
        events[0].data.oscParams?.get("voices") shouldBe 5.0
        events[0].data.oscParams?.get("freqSpread") shouldBe 0.2
        events[0].data.oscParams?.get("decay") shouldBe 0.99
    }
})
