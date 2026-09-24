/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/**
 * What the ENGINE substitutes for a compound door's unwritten knobs, rendered: a call that names a
 * stage and leaves a knob out must sound exactly like the same call with that knob spelled out at
 * the shared constant. The `body(...)` door is the one this file is named after; `vowel(...)` and
 * `compressor(...)` are the same question about two more stages.
 *
 * **Retired in step 5b-1 (2026-09-19), and why.** Until this round these rows compared the same
 * song WITH and WITHOUT `.katalyst(Katalyst(k => k.classic()))`, which was the acceptance of steps
 * 5a-2 and 5a-3: a declared chain used to resolve slots where an undeclared orbit read voice
 * fields, so the pair was a real old-path-against-new-path comparison. 5b-1 made the born-with
 * chain slot-driven and made a content-classic declaration resolve to the born-with chain itself
 * (`Cylinder.chainFor`), so both renders now run the IDENTICAL chain object and the rows were true
 * by construction. They were migration fixtures whose migration had finished, and the coverage they
 * carried moved here, to a pair whose two sides are two different paths through the DOOR: the FILL
 * (what a call leaves out) against the explicit spelling.
 *
 * The confound those rows were built for is recorded in `docs/tasks/katalyst-dsl.md` §9: before
 * `body.material` became a catalogue INDEX a declared chain owned its material, so declaring
 * DROPPED the body, worth +1.5 dB at 254 and 508 Hz on the frozen song.
 *
 * **Which of the two substitutions each row pins, measured rather than assumed** (round 1 of
 * 5b-1's review asked for the second one here and it does not live here): since step 5a-3 the DOOR
 * fills `body.wet` and `body.floor`, so a `body("wood")` never reaches the engine with an unset
 * amount at all. These render rows therefore pin the DOOR's fill: breaking it (the door's default
 * changed to 0.0) turns the material-only row red, verified. The ENGINE's own non-finite rule
 * (`KatalystSlots.bodyDef` substituting BODY_WET for an unset mix, the second line of defence
 * behind the fill and what a raw `katp("body.material", n)` still needs) is guarded one level down,
 * by `KatalystClassicMatchesUntouchedVoiceSpec`'s "a MATERIAL-ONLY body reaches the classic chain
 * at the engine's own wet" row, which writes the slot by hand; breaking `bodyDef` turns THAT row
 * red and leaves these green, also verified.
 *
 * The constants come from `audio_bridge/constants/`, interpolated into the song text, so the
 * explicit side cannot drift from the fill it is the oracle for.
 *
 * Renamed in step 5b-1 from `KatalystDeclaredBodyParitySpec`, which is what it was when its rows
 * compared a declared chain against an undeclared one.
 *
 * Why an end-to-end render and not a stage-level comparison: only a render exercises the cylinder,
 * the lease and the order in which a chain is installed. The stage-level half lives in
 * `KatalystClassicMatchesUntouchedVoiceSpec`, which cannot see the cylinder.
 */
class KatalystDoorFillRenderSpec : StringSpec({

    val voices = """note("c3 e3 g3 b3").s("supersaw")"""

    // The duck rows need a SOURCE orbit that actually sounds and a ducked orbit that declares a
    // chain whose depth is a slot with an authored default. `duckSong` is everything up to the
    // pattern's own duck call, `duckChain` the declaration that follows it.
    // The source is a SYNTH, not a sample: this renderer has no sample bank, so an `s("bd*4")`
    // source orbit would be silent and the duck would have nothing to trigger on.
    val duckSong =
        """stack(note("c1*4").s("triangle").orbit(0), note("c3 e3 g3 b3").s("supersaw").orbit(1)"""
    val duckChain =
        """.katalyst(Katalyst(k => k.duck(orbit = 0, depth = Katalyst.param("duck.depth", 0.8), attack = 0.3))))"""
    // The control's chain: a declaration that takes the orbit over but has no duck stage at all.
    // Both strings close the `stack(` that `duckSong` opens.
    val duckChainOff = """.katalyst(Katalyst(k => k.gain(1.0))))"""

    // The renderer, the sample-for-sample comparison and the peak read are shared with the other
    // small Katalyst render rows: `_katalyst_render_helpers.kt`, which also owns the numbers (48
    // kHz, four cycles at the frozen song's 34.5 rpm) so they are stated once.
    suspend fun render(code: String): List<ShortArray> = renderSong(code)

    "a MATERIAL-ONLY body(material) plays at the shared BODY_WET and BODY_FLOOR, not dry" {
        // The subject is the SUBSTITUTION, and the oracle is the same door called the long way.
        // `body("wood")` writes the index and leaves the amount and the floor to the door's fill and
        // to `KatalystSlots.bodyDef`'s non-finite rule; the right side spells both out at the
        // constants. Equal samples say the substitution lands on those numbers. With a SET 0.0
        // default the left side rendered a fully dry orbit and this pair failed by thousands of
        // counts, which is the bug round 1 of step 5a-2 found.
        val short = render("$voices.body(material = \"wood\").orbit(1)")
        val long = render("$voices.body(material = \"wood\", wet = $BODY_WET, floor = $BODY_FLOOR).orbit(1)")

        long.size shouldBe short.size

        // The render has to be LOUD, or an all-silent pair would pass this row without saying
        // anything. A supersaw chord through a body is nowhere near quiet.
        val peak = peakOf(short)

        withClue("the song actually made sound") { (peak > 3000) shouldBe true }
        withClue("max |short - long| in 16-bit counts, peak $peak") {
            maxDiff(short, long) shouldBe 0
        }
    }

    "a VOWEL-ONLY vowel(vowel) plays at the shared VOWEL_WET and VOWEL_FLOOR, the body row's twin" {
        // Written out rather than folded in: body and vowel are two writers over two catalogues and
        // two pairs of constants, and a vowel wired to the body's numbers would pass a body row.
        val short = render("$voices.vowel(vowel = \"a\").orbit(1)")
        val long = render("$voices.vowel(vowel = \"a\", wet = $VOWEL_WET, floor = $VOWEL_FLOOR).orbit(1)")

        long.size shouldBe short.size

        val peak = peakOf(short)

        withClue("the song actually made sound") { (peak > 3000) shouldBe true }
        withClue("max |short - long| in 16-bit counts, peak $peak") {
            maxDiff(short, long) shouldBe 0
        }
    }

    "the vowel row has teeth: the same song WITHOUT the vowel renders differently" {
        val withVowel = render("$voices.vowel(vowel = \"a\").orbit(1)")
        val withoutVowel = render("$voices.orbit(1)")

        withVowel.size shouldBe withoutVowel.size

        val diff = maxDiff(withVowel, withoutVowel)

        withClue("a formant bank at the shared wet has to move the samples, max diff was $diff") {
            (diff > 100) shouldBe true
        }
    }

    "a partial compressor(ratio = 8) compresses at the four filled constants" {
        // The compressor has no name knob, so ANY of its five knobs is the gate, and since step
        // 5a-3 the door fills the other four with exactly the constants
        // `Voice.Compressor.fromParams` substituted for a null field. The oracle is the same call
        // with all five spelled out.
        val short = render("$voices.gain(1.4).compressor(ratio = 8).orbit(1)")
        val long = render(
            "$voices.gain(1.4).compressor(" +
                    "threshold = $COMPRESSOR_THRESHOLD_DB, ratio = 8, knee = $COMPRESSOR_KNEE_DB, " +
                    "attack = $COMPRESSOR_ATTACK_SECONDS, release = $COMPRESSOR_RELEASE_SECONDS" +
                    ").orbit(1)"
        )

        long.size shouldBe short.size

        val peak = peakOf(short)

        withClue("the song actually made sound") { (peak > 3000) shouldBe true }
        withClue("max |short - long| in 16-bit counts, peak $peak") {
            maxDiff(short, long) shouldBe 0
        }
    }

    "the compressor row has teeth: ratio 8 at the filled threshold really moves the samples" {
        // The negative control for the row above. Without it, a compressor that never engaged on
        // either side would let "declared == undeclared" pass on two identical uncompressed
        // renders, and the row would be about nothing.
        val compressed = render("$voices.gain(1.4).compressor(ratio = 8).orbit(1)")
        val plain = render("$voices.gain(1.4).orbit(1)")

        compressed.size shouldBe plain.size

        val diff = maxDiff(compressed, plain)

        withClue("a ratio of 8 over the shared threshold has to compress, max diff was $diff") {
            (diff > 100) shouldBe true
        }
    }

    "a tail-only duck(attack) does not silence a chain-authored duck depth" {
        // Round 3 of step 5a-3, the MAJOR. The duck's name knob is its ORBIT, so only naming the
        // orbit may fill. Until the fix every duck knob filled, so a pattern saying `duck(attack =
        // 0.3)` wrote `duck.depth = 0.0` into the orbit's slot state and the chain's own
        // `Katalyst.param("duck.depth", 0.8)` resolved to silence: the ducking simply stopped.
        //
        // The chain authors its attack as a CONSTANT 0.3, which no slot can move, and the pattern's
        // tail call asks for the same 0.3. So after the fix the tail call changes nothing at all
        // and the two renders are identical, sample for sample. Before it, the depth went to zero.
        val withTail = render("$duckSong.duck(attack = 0.3)$duckChain")
        val withoutTail = render("$duckSong$duckChain")

        withTail.size shouldBe withoutTail.size

        val peak = peakOf(withoutTail)

        withClue("the song actually made sound") { (peak > 3000) shouldBe true }
        withClue("max |with tail - without tail| in 16-bit counts, peak $peak") {
            maxDiff(withTail, withoutTail) shouldBe 0
        }
    }

    "the duck row has teeth: the chain-authored duck really is ducking" {
        // The control, and it is about a different failure than the row above. Under the bug only
        // the WITH-tail render lost its ducking, so that row was red, not vacuously green; this one
        // guards the other way, that a future change which stops the chain ducking on BOTH sides
        // cannot make the pair equal and silent.
        val ducked = render("$duckSong.duck(attack = 0.3)$duckChain")
        val notDucked = render("$duckSong.duck(attack = 0.3)$duckChainOff")

        ducked.size shouldBe notDucked.size

        val diff = maxDiff(ducked, notDucked)

        withClue("a depth of 0.8 under a four-on-the-floor kick has to move the samples, max was $diff") {
            (diff > 100) shouldBe true
        }
    }

    "the guard has teeth: the same song WITHOUT the body renders differently" {
        // The negative control. If the body were being dropped on BOTH sides, the rows above would
        // pass on a silence that is equal to itself. This is what proves 0.3 of wood is audible
        // here at all, so those rows are about the body surviving and not about it being absent.
        val withBody = render("$voices.body(material = \"wood\", wet = 0.3).orbit(1)")
        val withoutBody = render("$voices.orbit(1)")

        withBody.size shouldBe withoutBody.size

        val diff = maxDiff(withBody, withoutBody)

        withClue("a body at wet 0.3 has to move the samples, max diff was $diff") {
            (diff > 100) shouldBe true
        }
    }

    "a declaration really does reach the orbit: a chain with NO body stage drops the body" {
        // A different subject from every row above. Those are about the DOOR's fill, what a call
        // that leaves a knob out sounds like; this one is about a DECLARATION arriving at a
        // cylinder at all. It is worth keeping here because the duck rows above depend on it: if a
        // `.katalyst(...)` silently never installed, they would compare two identical renders and
        // pass. So: declare a chain with a `gain` stage and nothing else. That orbit has no body
        // stage, the same `body(...)` call has nowhere to land, and the render MUST differ.
        val undeclared = render("$voices.body(material = \"wood\", wet = 0.3).orbit(1)")
        val noBodyStage = render(
            "$voices.body(material = \"wood\", wet = 0.3).orbit(1).katalyst(Katalyst(k => k.gain(1.0)))"
        )

        noBodyStage.size shouldBe undeclared.size

        val diff = maxDiff(undeclared, noBodyStage)

        withClue("a chain with no body stage cannot sound like one with a body, max diff was $diff") {
            (diff > 100) shouldBe true
        }
    }
})
