package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * Verifies that CallInfo (source locations) flows through DSL functions when
 * called from KlangScript. Each test compiles a script expression and asserts
 * that the resulting events carry non-null source locations — proving the
 * generated bridge constructs and passes CallInfo correctly.
 *
 * Kotlin-side calls pass `callInfo = null` by design, so only script-compiled
 * patterns are tested here.
 */
class CallInfoTest : StringSpec({

    "gain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").gain("0.5")""")
    }

    "pan passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").pan("0.25")""")
    }

    "velocity passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").velocity("0.5")""")
    }

    "postgain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").postgain("0.5")""")
    }

    "compressor passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").compressor("-20:4:3:0.03:0.1")""")
    }

    "unison passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").unison("3")""")
    }

    "detune passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").detune("0.3")""")
    }

    "spread passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").spread("0.8")""")
    }

    "density passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").density("5")""")
    }

    "attack passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").attack("0.01")""")
    }

    "decay passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").decay("0.2")""")
    }

    "sustain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").sustain("0.7")""")
    }

    "release passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").release("0.5")""")
    }

    "adsr passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").adsr("0.01:0.2:0.7:0.5")""")
    }

    "orbit passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").orbit("1")""")
    }

    "duckorbit passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").duckorbit("1")""")
    }

    "duckattack passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").duckattack("0.2")""")
    }

    "duckdepth passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").duckdepth("0.8")""")
    }

    "vowel passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").vowel("a")""")
    }

    "firstOf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3 d3").firstOf(2, x => x.note("e3"))""")
    }

    "every passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3 d3").every(2, x => x.note("e3"))""")
    }

    "lastOf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3 d3").lastOf(2, x => x.note("e3"))""")
    }

    "when passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3 d3").when("1 0", x => x.note("e3"))""")
    }

    "lateInCycle passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").lateInCycle("0.1")""")
    }

    "earlyInCycle passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").earlyInCycle("0.1")""")
    }

    "stretchBy passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").stretchBy("2")""")
    }

    "oscparam passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").oscparam("analog", "0.5")""")
    }

    "oscp passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").oscp("analog", "0.5")""")
    }

    "analog passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").analog("0.5")""")
    }

    "warmth passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").warmth("0.5")""")
    }

    "reverb passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").reverb("0.5:2")""")
    }

    "lpadsr passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").lpadsr("0.01:0.3:0.5:0.5")""")
    }

    "hpadsr passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").hpadsr("0.01:0.3:0.5:0.5")""")
    }

    "bpadsr passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").bpadsr("0.01:0.3:0.5:0.5")""")
    }

    "tremolo passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").tremolo("0.5:4")""")
    }

    "nfadsr passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfadsr("0.01:0.3:0.5:0.5")""")
    }

    "euclid passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1 2 3").euclid(3, 8)""")
    }

    "euclidRot passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1 2 3").euclidRot(3, 8, 2)""")
    }

    "euclidrot passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1 2 3").euclidrot(3, 8, 2)""")
    }

    "bjork passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1 2 3").bjork(3, 8, 0)""")
    }

    "euclidLegato passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1 2 3").euclidLegato(3, 8)""")
    }

    "euclidLegatoRot passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1 2 3").euclidLegatoRot(3, 8, 2)""")
    }

    "euclidish passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1 2 3").euclidish(3, 8, 0.5)""")
    }

    "eish passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1 2 3").eish(3, 8, 0.5)""")
    }
})

/**
 * Shared helper: compile a KlangScript expression, query events, and assert
 * that at least one event carries source-location info from CallInfo.
 */
fun assertCallInfoPresent(scriptExpr: String) {
    val pattern = SprudelPattern.compile(scriptExpr)
    pattern.shouldNotBeNull()

    val events = pattern.queryArc(0.0, 1.0)
    events.shouldNotBeEmpty()

    val hasLocations = events.any { it.sourceLocations != null }
    hasLocations shouldBe true
}
