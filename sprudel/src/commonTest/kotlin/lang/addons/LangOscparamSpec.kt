package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.s
import io.peekandpoke.klang.sprudel.lang.seq

class LangOscparamSpec : StringSpec({

    "oscparam dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.oscparam(key, ctrl)" to
                    seq(pat).oscparam("mykey", ctrl),
            "script pattern.oscparam(key, ctrl)" to
                    SprudelPattern.compile("""seq("$pat").oscparam("mykey", "$ctrl")"""),
            "string.oscparam(key, ctrl)" to
                    pat.oscparam("mykey", ctrl),
            "script string.oscparam(key, ctrl)" to
                    SprudelPattern.compile(""""$pat".oscparam("mykey", "$ctrl")"""),
            "oscparam(key, ctrl)" to
                    seq(pat).apply(oscparam("mykey", ctrl)),
            "script oscparam(key, ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(oscparam("mykey", "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("mykey") shouldBe 0.1
            events[1].data.oscParams?.get("mykey") shouldBe 0.5
        }
    }

    "oscp dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.oscp(key, ctrl)" to
                    seq(pat).oscp("mykey", ctrl),
            "script pattern.oscp(key, ctrl)" to
                    SprudelPattern.compile("""seq("$pat").oscp("mykey", "$ctrl")"""),
            "string.oscp(key, ctrl)" to
                    pat.oscp("mykey", ctrl),
            "script string.oscp(key, ctrl)" to
                    SprudelPattern.compile(""""$pat".oscp("mykey", "$ctrl")"""),
            "oscp(key, ctrl)" to
                    seq(pat).apply(oscp("mykey", ctrl)),
            "script oscp(key, ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(oscp("mykey", "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("mykey") shouldBe 0.1
            events[1].data.oscParams?.get("mykey") shouldBe 0.5
        }
    }

    "oscparam() sets oscParam correctly" {
        val p = note("c3").s("supersaw").oscparam("custom", "0.7")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.oscParams?.get("custom") shouldBe 0.7
        }
    }

    "oscparam() works with control pattern" {
        val p = note("c3 e3").oscparam("drift", "0.1 0.3")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("drift") shouldBe 0.1
            events[1].data.oscParams?.get("drift") shouldBe 0.3
        }
    }

    "oscparam() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").s("supersaw").oscparam("analog", "0.2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        assertSoftly {
            events.size shouldBe 1
            events[0].data.oscParams?.get("analog") shouldBe 0.2
        }
    }

    "oscp() alias works identically" {
        val p = note("c3").s("supersaw").oscp("custom", "0.5")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.oscParams?.get("custom") shouldBe 0.5
        }
    }

    "oscparam() with multiple keys on same pattern" {
        val p = note("c3").oscparam("key1", "0.3").oscparam("key2", "0.7")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.oscParams?.get("key1") shouldBe 0.3
            events[0].data.oscParams?.get("key2") shouldBe 0.7
        }
    }

    "oscparam() default is null when not set" {
        val p = s("supersaw")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.oscParams?.get("custom") shouldBe null
        }
    }
})