/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
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

    "pregain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").pregain("0.5")""")
    }

    "pan passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").pan("0.25")""")
    }

    "velocity passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").velocity("0.5")""")
    }

    "compressor passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").compressor(-20, 4, 3, 0.03, 0.1)""")
    }

    "unison passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").unison("3")""")
    }

    "detune passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").unison(spread = "0.3")""")
    }

    "spread passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").unison(pan = "0.8")""")
    }

    "density passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").density("5")""")
    }

    "attack passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").adsr(attack = "0.01")""")
    }

    "decay passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").adsr(decay = "0.2")""")
    }

    "sustain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").adsr(sustain = "0.7")""")
    }

    "release passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").adsr(release = "0.5")""")
    }

    "adsr passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").adsr(0.01, 0.2, 0.7, 0.5)""")
    }

    "orbit passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").orbit("1")""")
    }

    "duckorbit passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").duck("1")""")
    }

    "duckattack passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").duck(attack = "0.2")""")
    }

    "duckdepth passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").duck(depth = "0.8")""")
    }

    "vowel passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").vowel(vowel = "a")""")
    }

    "body's material passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").body(material = "wood")""")
    }

    "phaser's rate passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").phaser(rate = "2")""")
    }

    "phaser's wet, the head, passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").phaser("0.5")""")
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

    "onepole passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").onepole("12000")""")
    }

    "reverb passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").reverb(0.5, 2)""")
    }

    "lpf(attack, decay, sustain, release) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)""")
    }

    "hpf(attack, decay, sustain, release) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)""")
    }

    "bpf(attack, decay, sustain, release) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").bpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)""")
    }

    "tremolo passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").tremolo(0.5, 4)""")
    }

    "notch(attack, decay, sustain, release) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)""")
    }

    "euclid keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""seq("0 1 2 3").euclid(3, 8)""")
    }

    "euclidRot keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""seq("0 1 2 3").euclidRot(3, 8, 2)""")
    }

    "euclidrot keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""seq("0 1 2 3").euclidrot(3, 8, 2)""")
    }

    "bjork keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""seq("0 1 2 3").bjork(3, 8, 0)""")
    }

    "euclidLegato keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""seq("0 1 2 3").euclidLegato(3, 8)""")
    }

    "euclidLegatoRot keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""seq("0 1 2 3").euclidLegatoRot(3, 8, 2)""")
    }

    "euclidish keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""seq("0 1 2 3").euclidish(3, 8, 0.5)""")
    }

    "eish keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""seq("0 1 2 3").eish(3, 8, 0.5)""")
    }

    "notch(freq) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notch("1000")""")
    }

    "notch(q = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notch(q = "5")""")
    }

    "notch(attack = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notch(attack = "0.1")""")
    }

    "notch(decay = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notch(decay = "0.3")""")
    }

    "notch(sustain = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notch(sustain = "0.5")""")
    }

    "notch(release = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notch(release = "0.5")""")
    }

    "notch(env = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").notch(env = "3000")""")
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

    "unit keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""s("bd").unit("c")""")
    }

    "loop keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""s("bd").loop(1)""")
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

    "loopAt keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""s("bd").loopAt("1")""")
    }

    "loopAtCps keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""s("bd").loopAtCps("1", "0.5")""")
    }

    "loopatcps keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""s("bd").loopatcps("1", "0.5")""")
    }

    "cut passes CallInfo from KlangScript" {
        assertCallInfoPresent("""s("bd").cut("1")""")
    }

    "slice keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""s("bd").slice(8, 0)""")
    }

    "splice keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""s("bd").splice(8, 0)""")
    }

    "sndPluck passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c3").sndPluck(0.99, 0.5)""")
    }

    "sndSuperPluck keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSuperPluck()""")
    }

    "sndSine keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSine()""")
    }

    "sndSuperSine keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSuperSine()""")
    }

    "sndSaw keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSaw()""")
    }

    "sndSuperSaw keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSuperSaw()""")
    }

    "sndSquare keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSquare()""")
    }

    "sndSuperSquare keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSuperSquare()""")
    }

    "sndTriangle keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndTriangle()""")
    }

    "sndSuperTri keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSuperTri()""")
    }

    "sndRamp keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndRamp()""")
    }

    "sndSuperRamp keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndSuperRamp()""")
    }

    "sndPulze keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndPulze()""")
    }

    "sndPink keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndPink()""")
    }

    "sndBrown keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndBrown()""")
    }

    "sndNoise keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndNoise()""")
    }

    "sndCrackle keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndCrackle()""")
    }

    "sndDust keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c3").sndDust()""")
    }

    "slow passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").slow(2)""")
    }

    "fast passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").fast(2)""")
    }

    "rev keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d e f").rev()""")
    }

    "revv keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d e f").revv()""")
    }

    "palindrome keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d e f").palindrome()""")
    }

    "early keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").early("0.25")""")
    }

    "late keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").late("0.25")""")
    }

    "compress keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").compress("0.25", "0.75")""")
    }

    "focus keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").focus("0.25", "0.75")""")
    }

    "ply passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").ply("2")""")
    }

    // plyWith / plywith intentionally not tested — applyPlyWith uses `_bindSqueeze` with
    // AtomicInfinitePattern, which does not propagate source locations from the outer call.

    "hurry passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c d").hurry("2")""")
    }

    "fastGap keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").fastGap("2")""")
    }

    "densityGap keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").densityGap("2")""")
    }

    // inside / outside intentionally not tested — applyInside/applyOutside reconstruct
    // patterns through slow/transform/fast which does not reliably propagate source locations.

    "swing keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").swing("2")""")
    }

    "swingBy keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").swingBy("0.3", "2")""")
    }

    "brak keeps the receiver's locations" {
        assertReceiverLocationsSurvive("""note("c d").brak()""")
    }

    "lpf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lpf("500")""")
    }

    "hpf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hpf("500")""")
    }

    "bpf passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bpf("500")""")
    }

    "lpf(q = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lpf(q = "5")""")
    }

    "hpf(q = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hpf(q = "5")""")
    }

    "bpf(q = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bpf(q = "5")""")
    }

    "lpf(env = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").lpf(env = "2000")""")
    }

    "hpf(env = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").hpf(env = "2000")""")
    }

    "bpf(env = ...) passes CallInfo from KlangScript" {
        assertCallInfoPresent("""note("c4").bpf(env = "2000")""")
    }

    "lpf(attack, decay, sustain, release) passes CallInfo from KlangScript on a note receiver" {
        assertCallInfoPresent("""note("c4").lpf(attack = 0.1, decay = 0.3, sustain = 0.5, release = 0.2)""")
    }

    "hpf(attack, decay, sustain, release) passes CallInfo from KlangScript on a note receiver" {
        assertCallInfoPresent("""note("c4").hpf(attack = 0.1, decay = 0.3, sustain = 0.5, release = 0.2)""")
    }

    "bpf(attack, decay, sustain, release) passes CallInfo from KlangScript on a note receiver" {
        assertCallInfoPresent("""note("c4").bpf(attack = 0.1, decay = 0.3, sustain = 0.5, release = 0.2)""")
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
 * The WEAK check, named for what it is: at least one event still carries a source location, which
 * the receiver alone supplies. It proves the call does not DROP the receiver's locations, and
 * nothing about its own arguments.
 *
 * It is the check for the rows whose arguments never put a location on an event, found when
 * [assertCallInfoPresent] was made strict (round 1 of step 3d(iii), 2026-09-24). Three groups:
 * - the structural and time doors `euclid`, `euclidRot`/`euclidrot`, `bjork`, `euclidLegato`,
 *   `euclidLegatoRot`, `euclidish`/`eish`, `early`/`late`, `compress`/`focus`,
 *   `fastGap`/`densityGap`, `swing`/`swingBy`, `slice`/`splice`, `loopAt` and
 *   `loopAtCps`/`loopatcps` take their argument as a structure parameter (a factor, a step count,
 *   an inner join) and never prepend its location;
 * - `unit` and `loop` lift through `_liftData`, whose merge copies the control's DATA but not its
 *   `sourceLocations`: that merge is where the gap would be fixed. (`loopBegin`/`loopb`,
 *   `loopEnd`/`loope` are not in this group; they carry their locations and stay strict.)
 * - the no-argument doors (the `snd*` sounds, `rev`, `revv`, `palindrome`, `brak`) have no
 *   argument to locate.
 * Whether the first two groups SHOULD carry argument locations is a separate question; this helper
 * only keeps the old guarantee honest.
 */
fun assertReceiverLocationsSurvive(scriptExpr: String) {
    val pattern = SprudelPattern.compile(scriptExpr)
    pattern.shouldNotBeNull()

    val events = pattern.queryArc(0.0, 1.0)
    events.shouldNotBeEmpty()

    events.any { it.sourceLocations != null } shouldBe true
}

/**
 * Shared helper: compile a KlangScript expression, query events, and assert that CallInfo reached
 * them FROM THE ARGUMENTS of the expression's last call.
 *
 * The expression's own receiver (`seq("0 1")`, `note("c3")`) always carries a location, so "some
 * event has some location" proved nothing about the call under test (round 1 of step 3d(iii),
 * 2026-09-24: dropping the vowel name's CallInfo left the old helper green). The check is therefore
 * that at least one event carries a location that STARTS inside the column span of the last call's
 * argument list. A call with an empty argument list has no argument to locate and is refused:
 * such a row belongs to [assertReceiverLocationsSurvive].
 */
fun assertCallInfoPresent(scriptExpr: String) {
    val pattern = SprudelPattern.compile(scriptExpr)
    pattern.shouldNotBeNull()

    val events = pattern.queryArc(0.0, 1.0)
    events.shouldNotBeEmpty()

    val span = lastCallSpan(scriptExpr)
    val locations = events.flatMap { it.sourceLocations?.locations ?: emptyList() }

    withClue("$scriptExpr: a location inside columns ${span.first} until ${span.last + 1}; found ${locations.distinct()}") {
        locations.any { it.startLine == 1 && it.startColumn in span } shouldBe true
    }
}

/**
 * The 1-based column range of the last top-level call's argument list in a one-line expression.
 * String literals are skipped, so a parenthesis inside mini-notation does not count.
 */
private fun lastCallSpan(expr: String): IntRange {
    var depth = 0
    var quote: Char? = null
    var lastOpen = -1
    var lastClose = -1

    for ((i, c) in expr.withIndex()) {
        if (quote != null) {
            if (c == quote) {
                quote = null
            }
            continue
        }

        when (c) {
            '"', '\'', '`' -> quote = c
            '(' -> {
                if (depth == 0) {
                    lastOpen = i
                }
                depth++
            }

            ')' -> {
                depth--
                if (depth == 0) {
                    lastClose = i
                }
            }

            else -> {}
        }
    }

    check(lastOpen >= 0 && lastClose > lastOpen) { "no call in '$expr'" }

    val argsStart = lastOpen + 1
    val argsEnd = lastClose - 1

    check(expr.substring(argsStart, lastClose).isNotBlank()) {
        "strict check needs an argument; use assertReceiverLocationsSurvive for '$expr'"
    }

    // 0-based index i is column i + 1.
    return (argsStart + 1)..(argsEnd + 1)
}
