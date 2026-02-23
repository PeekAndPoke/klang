package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangReleaseSpec : StringSpec({

    "release dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.release(ctrl)" to
                    seq(pat).release(ctrl),
            "script pattern.release(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").release("$ctrl")"""),
            "string.release(ctrl)" to
                    pat.release(ctrl),
            "script string.release(ctrl)" to
                    StrudelPattern.compile(""""$pat".release("$ctrl")"""),
            "release(ctrl)" to
                    seq(pat).apply(release(ctrl)),
            "script release(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(release("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.release shouldBe 0.1
            events[1].data.release shouldBe 0.5
        }
    }

    "reinterpret voice data as release | seq(\"0 1\").release()" {
        val p = seq("0.1 0.5").release()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.release shouldBe 0.1
            events[1].data.release shouldBe 0.5
        }
    }

    "reinterpret voice data as release | \"0 1\".release()" {
        val p = "0.1 0.5".release()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.release shouldBe 0.1
            events[1].data.release shouldBe 0.5
        }
    }

    "reinterpret voice data as release | seq(\"0 1\").apply(release())" {
        val p = seq("0.1 0.5").apply(release())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.release shouldBe 0.1
            events[1].data.release shouldBe 0.5
        }
    }

    "release() sets VoiceData.release" {
        val p = "0 1".apply(release("0.1 0.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.release } shouldBe listOf(0.1, 0.5)
    }

    "release() works as pattern extension" {
        val p = note("c").release("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.release shouldBe 0.1
    }

    "release() works as string extension" {
        val p = "c".release("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.release shouldBe 0.1
    }

    "release() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").release("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.release shouldBe 0.1
    }

    "release() with continuous pattern sets release correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").release(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.release shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.release shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.release shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.release shouldBe (0.0 plusOrMinus EPSILON)
    }
})