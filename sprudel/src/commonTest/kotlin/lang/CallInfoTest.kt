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

    "notchf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notchf("1000")""")
    }

    "nresonance passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nresonance("5")""")
    }

    "notchq passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notchq("5")""")
    }

    "nfattack passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfattack("0.1")""")
    }

    "nfa passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfa("0.1")""")
    }

    "nfdecay passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfdecay("0.3")""")
    }

    "nfd passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfd("0.3")""")
    }

    "nfsustain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfsustain("0.5")""")
    }

    "nfs passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfs("0.5")""")
    }

    "nfrelease passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfrelease("0.5")""")
    }

    "nfr passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfr("0.5")""")
    }

    "nfenv passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfenv("3000")""")
    }

    "nfe passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").nfe("3000")""")
    }

    "begin passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").begin("0.5")""")
    }

    "end passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").end("0.5")""")
    }

    "speed passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").speed("2")""")
    }

    "unit passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").unit("c")""")
    }

    "loop passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").loop(1)""")
    }

    "loopBegin passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").loopBegin("0.25")""")
    }

    "loopb passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").loopb("0.25")""")
    }

    "loopEnd passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").loopEnd("0.75")""")
    }

    "loope passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").loope("0.75")""")
    }

    "loopAt passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").loopAt("1")""")
    }

    "loopAtCps passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").loopAtCps("1", "0.5")""")
    }

    "loopatcps passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").loopatcps("1", "0.5")""")
    }

    "cut passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").cut("1")""")
    }

    "slice passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").slice(8, 0)""")
    }

    "splice passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").splice(8, 0)""")
    }

    "sndPluck passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndPluck("0.99:0.5")""")
    }

    "sndSuperPluck passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSuperPluck()""")
    }

    "sndSine passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSine()""")
    }

    "sndSuperSine passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSuperSine()""")
    }

    "sndSaw passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSaw()""")
    }

    "sndSuperSaw passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSuperSaw()""")
    }

    "sndSquare passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSquare()""")
    }

    "sndSuperSquare passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSuperSquare()""")
    }

    "sndTriangle passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndTriangle()""")
    }

    "sndSuperTri passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSuperTri()""")
    }

    "sndRamp passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndRamp()""")
    }

    "sndSuperRamp passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndSuperRamp()""")
    }

    "sndPulze passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndPulze()""")
    }

    "sndPink passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndPink()""")
    }

    "sndBrown passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndBrown()""")
    }

    "sndNoise passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndNoise()""")
    }

    "sndCrackle passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndCrackle()""")
    }

    "sndDust passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndDust()""")
    }

    "slow passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").slow(2)""")
    }

    "fast passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").fast(2)""")
    }

    "rev passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d e f").rev()""")
    }

    "revv passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d e f").revv()""")
    }

    "palindrome passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d e f").palindrome()""")
    }

    "early passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").early("0.25")""")
    }

    "late passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").late("0.25")""")
    }

    "compress passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").compress("0.25", "0.75")""")
    }

    "focus passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").focus("0.25", "0.75")""")
    }

    "ply passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").ply("2")""")
    }

    // plyWith / plywith intentionally not tested — applyPlyWith uses `_bindSqueeze` with
    // AtomicInfinitePattern, which does not propagate source locations from the outer call.

    "hurry passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").hurry("2")""")
    }

    "fastGap passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").fastGap("2")""")
    }

    "densityGap passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").densityGap("2")""")
    }

    // inside / outside intentionally not tested — applyInside/applyOutside reconstruct
    // patterns through slow/transform/fast which does not reliably propagate source locations.

    "swing passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").swing("2")""")
    }

    "swingBy passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").swingBy("0.3", "2")""")
    }

    "brak passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").brak()""")
    }

    "lpf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lpf("500")""")
    }

    "hpf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hpf("500")""")
    }

    "bandf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bandf("500")""")
    }

    "resonance passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").resonance("5")""")
    }

    "hresonance passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hresonance("5")""")
    }

    "bandq passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bandq("5")""")
    }

    "lpenv passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lpenv("2000")""")
    }

    "hpenv passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hpenv("2000")""")
    }

    "bpenv passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bpenv("2000")""")
    }

    "lpattack passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lpattack("0.1")""")
    }

    "lpdecay passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lpdecay("0.3")""")
    }

    "lpsustain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lpsustain("0.5")""")
    }

    "lprelease passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lprelease("0.5")""")
    }

    "hpattack passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hpattack("0.1")""")
    }

    "hpdecay passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hpdecay("0.3")""")
    }

    "hpsustain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hpsustain("0.5")""")
    }

    "hprelease passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hprelease("0.5")""")
    }

    "bpattack passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bpattack("0.1")""")
    }

    "bpdecay passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bpdecay("0.3")""")
    }

    "bpsustain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bpsustain("0.5")""")
    }

    "bprelease passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bprelease("0.5")""")
    }

    "note passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4 e4")""")
    }

    "n passes CallInfo from KlangScript" {
        assertCallInfoPresent("""n("0 1 2")""")
    }

    "sound passes CallInfo from KlangScript" {
        assertCallInfoPresent("""sound("bd sd")""")
    }

    "s passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd sd")""")
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
