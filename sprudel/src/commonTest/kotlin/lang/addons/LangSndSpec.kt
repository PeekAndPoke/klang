package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note

class LangSndSpec : StringSpec({

    // -- simple (no params) -----------------------------------------------------------------------------------------------

    "sndSine() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSine()" to note("c3").sndSine(),
            "string.sndSine()" to "c3".sndSine(),
            "script pattern.sndSine()" to SprudelPattern.compile("""note("c3").sndSine()"""),
            "script string.sndSine()" to SprudelPattern.compile(""""c3".sndSine()"""),
            "apply(sndSine())" to note("c3").apply(sndSine()),
            "script apply(sndSine())" to SprudelPattern.compile("""note("c3").apply(sndSine())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sound shouldBe "sine"
        }
    }

    "sndSaw() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSaw()" to note("c3").sndSaw(),
            "string.sndSaw()" to "c3".sndSaw(),
            "script pattern.sndSaw()" to SprudelPattern.compile("""note("c3").sndSaw()"""),
            "script string.sndSaw()" to SprudelPattern.compile(""""c3".sndSaw()"""),
            "apply(sndSaw())" to note("c3").apply(sndSaw()),
            "script apply(sndSaw())" to SprudelPattern.compile("""note("c3").apply(sndSaw())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sound shouldBe "sawtooth"
        }
    }

    "sndSquare() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSquare()" to note("c3").sndSquare(),
            "string.sndSquare()" to "c3".sndSquare(),
            "script pattern.sndSquare()" to SprudelPattern.compile("""note("c3").sndSquare()"""),
            "script string.sndSquare()" to SprudelPattern.compile(""""c3".sndSquare()"""),
            "apply(sndSquare())" to note("c3").apply(sndSquare()),
            "script apply(sndSquare())" to SprudelPattern.compile("""note("c3").apply(sndSquare())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sound shouldBe "square"
        }
    }

    "sndTriangle() dsl interface" {
        dslInterfaceTests(
            "pattern.sndTriangle()" to note("c3").sndTriangle(),
            "string.sndTriangle()" to "c3".sndTriangle(),
            "script pattern.sndTriangle()" to SprudelPattern.compile("""note("c3").sndTriangle()"""),
            "script string.sndTriangle()" to SprudelPattern.compile(""""c3".sndTriangle()"""),
            "apply(sndTriangle())" to note("c3").apply(sndTriangle()),
            "script apply(sndTriangle())" to SprudelPattern.compile("""note("c3").apply(sndTriangle())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sound shouldBe "triangle"
        }
    }

    "sndRamp() dsl interface" {
        dslInterfaceTests(
            "pattern.sndRamp()" to note("c3").sndRamp(),
            "string.sndRamp()" to "c3".sndRamp(),
            "script pattern.sndRamp()" to SprudelPattern.compile("""note("c3").sndRamp()"""),
            "script string.sndRamp()" to SprudelPattern.compile(""""c3".sndRamp()"""),
            "apply(sndRamp())" to note("c3").apply(sndRamp()),
            "script apply(sndRamp())" to SprudelPattern.compile("""note("c3").apply(sndRamp())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sound shouldBe "ramp"
        }
    }

    "sndNoise() dsl interface" {
        dslInterfaceTests(
            "pattern.sndNoise()" to note("c3").sndNoise(),
            "string.sndNoise()" to "c3".sndNoise(),
            "script pattern.sndNoise()" to SprudelPattern.compile("""note("c3").sndNoise()"""),
            "script string.sndNoise()" to SprudelPattern.compile(""""c3".sndNoise()"""),
            "apply(sndNoise())" to note("c3").apply(sndNoise()),
            "script apply(sndNoise())" to SprudelPattern.compile("""note("c3").apply(sndNoise())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sound shouldBe "whitenoise"
        }
    }

    "sndBrown() dsl interface" {
        dslInterfaceTests(
            "pattern.sndBrown()" to note("c3").sndBrown(),
            "string.sndBrown()" to "c3".sndBrown(),
            "script pattern.sndBrown()" to SprudelPattern.compile("""note("c3").sndBrown()"""),
            "script string.sndBrown()" to SprudelPattern.compile(""""c3".sndBrown()"""),
            "apply(sndBrown())" to note("c3").apply(sndBrown()),
            "script apply(sndBrown())" to SprudelPattern.compile("""note("c3").apply(sndBrown())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sound shouldBe "brownnoise"
        }
    }

    "sndPink() dsl interface" {
        dslInterfaceTests(
            "pattern.sndPink()" to note("c3").sndPink(),
            "string.sndPink()" to "c3".sndPink(),
            "script pattern.sndPink()" to SprudelPattern.compile("""note("c3").sndPink()"""),
            "script string.sndPink()" to SprudelPattern.compile(""""c3".sndPink()"""),
            "apply(sndPink())" to note("c3").apply(sndPink()),
            "script apply(sndPink())" to SprudelPattern.compile("""note("c3").apply(sndPink())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sound shouldBe "pinknoise"
        }
    }

    // -- one param (duty / density) ---------------------------------------------------------------------------------------

    "sndPulze() dsl interface" {
        dslInterfaceTests(
            "pattern.sndPulze()" to note("c3").sndPulze("0.25"),
            "string.sndPulze()" to "c3".sndPulze("0.25"),
            "script pattern.sndPulze()" to SprudelPattern.compile("""note("c3").sndPulze("0.25")"""),
            "script string.sndPulze()" to SprudelPattern.compile(""""c3".sndPulze("0.25")"""),
            "apply(sndPulze())" to note("c3").apply(sndPulze("0.25")),
            "script apply(sndPulze())" to SprudelPattern.compile("""note("c3").apply(sndPulze("0.25"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "pulze"
                events[0].data.oscParams?.get("duty") shouldBe 0.25
            }
        }
    }

    "sndPulze() with no params sets sound" {
        val p = note("c3").sndPulze()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.sound shouldBe "pulze"
    }

    "sndDust() dsl interface" {
        dslInterfaceTests(
            "pattern.sndDust()" to note("c3").sndDust("0.3"),
            "string.sndDust()" to "c3".sndDust("0.3"),
            "script pattern.sndDust()" to SprudelPattern.compile("""note("c3").sndDust("0.3")"""),
            "script string.sndDust()" to SprudelPattern.compile(""""c3".sndDust("0.3")"""),
            "apply(sndDust())" to note("c3").apply(sndDust("0.3")),
            "script apply(sndDust())" to SprudelPattern.compile("""note("c3").apply(sndDust("0.3"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "dust"
                events[0].data.oscParams?.get("density") shouldBe 0.3
            }
        }
    }

    "sndDust() with no params sets sound" {
        val p = note("c3").sndDust()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.sound shouldBe "dust"
    }

    "sndCrackle() dsl interface" {
        dslInterfaceTests(
            "pattern.sndCrackle()" to note("c3").sndCrackle("0.5"),
            "string.sndCrackle()" to "c3".sndCrackle("0.5"),
            "script pattern.sndCrackle()" to SprudelPattern.compile("""note("c3").sndCrackle("0.5")"""),
            "script string.sndCrackle()" to SprudelPattern.compile(""""c3".sndCrackle("0.5")"""),
            "apply(sndCrackle())" to note("c3").apply(sndCrackle("0.5")),
            "script apply(sndCrackle())" to SprudelPattern.compile("""note("c3").apply(sndCrackle("0.5"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "crackle"
                events[0].data.oscParams?.get("density") shouldBe 0.5
            }
        }
    }

    "sndCrackle() with no params sets sound" {
        val p = note("c3").sndCrackle()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.sound shouldBe "crackle"
    }

    // -- two params (voices:freqSpread) -----------------------------------------------------------------------------------

    "sndSuperSaw() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSuperSaw()" to note("c3").sndSuperSaw("7:0.3"),
            "string.sndSuperSaw()" to "c3".sndSuperSaw("7:0.3"),
            "script pattern.sndSuperSaw()" to SprudelPattern.compile("""note("c3").sndSuperSaw("7:0.3")"""),
            "script string.sndSuperSaw()" to SprudelPattern.compile(""""c3".sndSuperSaw("7:0.3")"""),
            "apply(sndSuperSaw())" to note("c3").apply(sndSuperSaw("7:0.3")),
            "script apply(sndSuperSaw())" to SprudelPattern.compile("""note("c3").apply(sndSuperSaw("7:0.3"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "supersaw"
                events[0].data.oscParams?.get("voices") shouldBe 7.0
                events[0].data.oscParams?.get("freqSpread") shouldBe 0.3
            }
        }
    }

    "sndSuperSaw() with no params sets sound" {
        val p = note("c3").sndSuperSaw()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.sound shouldBe "supersaw"
    }

    "sndSuperSine() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSuperSine()" to note("c3").sndSuperSine("5:0.2"),
            "string.sndSuperSine()" to "c3".sndSuperSine("5:0.2"),
            "script pattern.sndSuperSine()" to SprudelPattern.compile("""note("c3").sndSuperSine("5:0.2")"""),
            "script string.sndSuperSine()" to SprudelPattern.compile(""""c3".sndSuperSine("5:0.2")"""),
            "apply(sndSuperSine())" to note("c3").apply(sndSuperSine("5:0.2")),
            "script apply(sndSuperSine())" to SprudelPattern.compile("""note("c3").apply(sndSuperSine("5:0.2"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "supersine"
                events[0].data.oscParams?.get("voices") shouldBe 5.0
                events[0].data.oscParams?.get("freqSpread") shouldBe 0.2
            }
        }
    }

    "sndSuperSine() with no params sets sound" {
        val p = note("c3").sndSuperSine()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.sound shouldBe "supersine"
    }

    "sndSuperSquare() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSuperSquare()" to note("c3").sndSuperSquare("7:0.3"),
            "string.sndSuperSquare()" to "c3".sndSuperSquare("7:0.3"),
            "script pattern.sndSuperSquare()" to SprudelPattern.compile("""note("c3").sndSuperSquare("7:0.3")"""),
            "script string.sndSuperSquare()" to SprudelPattern.compile(""""c3".sndSuperSquare("7:0.3")"""),
            "apply(sndSuperSquare())" to note("c3").apply(sndSuperSquare("7:0.3")),
            "script apply(sndSuperSquare())" to SprudelPattern.compile("""note("c3").apply(sndSuperSquare("7:0.3"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "supersquare"
                events[0].data.oscParams?.get("voices") shouldBe 7.0
                events[0].data.oscParams?.get("freqSpread") shouldBe 0.3
            }
        }
    }

    "sndSuperSquare() with no params sets sound" {
        val p = note("c3").sndSuperSquare()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.sound shouldBe "supersquare"
    }

    "sndSuperTri() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSuperTri()" to note("c3").sndSuperTri("5:0.2"),
            "string.sndSuperTri()" to "c3".sndSuperTri("5:0.2"),
            "script pattern.sndSuperTri()" to SprudelPattern.compile("""note("c3").sndSuperTri("5:0.2")"""),
            "script string.sndSuperTri()" to SprudelPattern.compile(""""c3".sndSuperTri("5:0.2")"""),
            "apply(sndSuperTri())" to note("c3").apply(sndSuperTri("5:0.2")),
            "script apply(sndSuperTri())" to SprudelPattern.compile("""note("c3").apply(sndSuperTri("5:0.2"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "supertri"
                events[0].data.oscParams?.get("voices") shouldBe 5.0
                events[0].data.oscParams?.get("freqSpread") shouldBe 0.2
            }
        }
    }

    "sndSuperTri() with no params sets sound" {
        val p = note("c3").sndSuperTri()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.sound shouldBe "supertri"
    }

    "sndSuperRamp() dsl interface" {
        dslInterfaceTests(
            "pattern.sndSuperRamp()" to note("c3").sndSuperRamp("7:0.3"),
            "string.sndSuperRamp()" to "c3".sndSuperRamp("7:0.3"),
            "script pattern.sndSuperRamp()" to SprudelPattern.compile("""note("c3").sndSuperRamp("7:0.3")"""),
            "script string.sndSuperRamp()" to SprudelPattern.compile(""""c3".sndSuperRamp("7:0.3")"""),
            "apply(sndSuperRamp())" to note("c3").apply(sndSuperRamp("7:0.3")),
            "script apply(sndSuperRamp())" to SprudelPattern.compile("""note("c3").apply(sndSuperRamp("7:0.3"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.sound shouldBe "superramp"
                events[0].data.oscParams?.get("voices") shouldBe 7.0
                events[0].data.oscParams?.get("freqSpread") shouldBe 0.3
            }
        }
    }

    "sndSuperRamp() with no params sets sound" {
        val p = note("c3").sndSuperRamp()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.sound shouldBe "superramp"
    }
})
