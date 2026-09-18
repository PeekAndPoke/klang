/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import io.peekandpoke.klang.sprudel.SprudelPattern
import kotlin.math.abs

/**
 * The acceptance of Katalyst steps 5a-2 and 5a-3 (2026-09-18), rendered: declaring
 * `Katalyst(k => k.classic())` on an orbit whose voices carry a compound-door call must sound like
 * the same song without the declaration. The `body(...)` door is the one the steps were built
 * around and the one the file is named after; the `compressor(...)` row joined it with step 5a-3,
 * because the two are the same question about two stages.
 *
 * Before the index slot it did not, and that is the confound the step-4 EQ measurement had to work
 * around (`docs/tasks/katalyst-dsl.md` §9): a declared chain owned its material, a slot could not
 * carry a name, and declaring a chain therefore DROPPED the body, worth +1.5 dB at 254 and 508 Hz
 * on the frozen song. `body.material` as a catalogue INDEX is what closes it, and the only way to
 * see that closed is to render both and compare samples.
 *
 * Both spellings of the call are rendered, because they fail differently. `body(material, wet)`
 * writes two slots; `body(material)` writes ONE, and the amount is then whatever an unset
 * `body.wet` slot resolves to, which round 1 of the review found resolving to a fully dry 0.0.
 *
 * Why an end-to-end render and not a stage-level comparison: the two paths differ in WHO configures
 * the stage (the owner voice's fields against the orbit's param state) and in WHEN (the cylinder
 * installs a declared chain on an idle orbit before the first voice sounds). Only a render
 * exercises both. The stage-level half lives in `KatalystClassicMatchesUntouchedVoiceSpec`, which
 * cannot see the cylinder.
 *
 * **What the BIT-EXACT rows depend on.** An offline render starts silent, so `VoiceScheduler`
 * requests the chain before the first `activateVoice` and the cylinder installs it while it is
 * IDLE, which is the immediate path. A declaration arriving on an orbit that is already sounding
 * takes the crossfade instead, and that is deliberately not bit-exact (the arriving chain's banks
 * and rings warm from empty inside the 60 ms window). These rows are about the values the two
 * paths resolve, not about the swap.
 */
class KatalystDeclaredBodyParitySpec : StringSpec({

    val sampleRate = 48_000
    val cycles = 4
    val cps = 0.575 // 34.5 rpm, the frozen song's tempo

    val voices = """note("c3 e3 g3 b3").s("supersaw")"""
    val declaration = """.katalyst(Katalyst(k => k.classic()))"""

    // The duck rows need a SOURCE orbit that actually sounds and a ducked orbit that declares a
    // chain whose depth is a slot with an authored default. `duckSong` is everything up to the
    // pattern's own duck call, `duckChain` the declaration that follows it.
    // The source is a SYNTH, not a sample: this renderer has no sample bank, so an `s("bd*4")`
    // source orbit would be silent and the duck would have nothing to trigger on.
    val duckSong =
        """stack(note("c1*4").s("triangle").orbit(0), note("c3 e3 g3 b3").s("supersaw").orbit(1)"""
    val duckChain =
        """.katalyst(Katalyst(k => k.duck(d => d.orbit(0).depth(Katalyst.param("duck.depth", 0.8)).attack(0.3)))))"""
    // The control's chain: a declaration that takes the orbit over but has no duck stage at all.
    // Both strings close the `stack(` that `duckSong` opens.
    val duckChainOff = """.katalyst(Katalyst(k => k.gain(1.0))))"""

    suspend fun render(code: String): List<ShortArray> {
        val pattern = SprudelPattern.compile(code) ?: error("the song did not compile: $code")
        val blocks = mutableListOf<ShortArray>()

        KlangOfflineRenderer(sampleRate = sampleRate).render(
            pattern = pattern,
            cycles = cycles,
            cyclesPerSecond = cps,
            tailSec = 0.5,
            onBlock = { samples, count -> blocks.add(samples.copyOf(count)) },
        )

        return blocks
    }

    /**
     * The largest sample-to-sample difference between two renders, in 16-bit counts, so a failure
     * says HOW far apart they are: a dropped body is thousands of counts, a rounding difference one
     * or two. Every row asserts equal block COUNTS before it calls this; the per-block length is
     * clamped anyway, so a render that diverges in length is reported as a difference by the row
     * that compares the counts, never as an index crash from here.
     */
    fun maxDiff(a: List<ShortArray>, b: List<ShortArray>): Int {
        var worst = 0

        for (block in 0 until minOf(a.size, b.size)) {
            val left = a[block]
            val right = b[block]

            for (i in 0 until minOf(left.size, right.size)) {
                val diff = abs(left[i].toInt() - right[i].toInt())

                if (diff > worst) {
                    worst = diff
                }
            }
        }

        return worst
    }

    /** The loudest sample of a render, so a row can prove it is asserting about actual sound. */
    fun peakOf(blocks: List<ShortArray>): Int {
        var peak = 0

        for (block in blocks) {
            for (i in block.indices) {
                val level = abs(block[i].toInt())

                if (level > peak) {
                    peak = level
                }
            }
        }

        return peak
    }

    "a declared classic chain keeps a body(material, wet): the render is identical, sample for sample" {
        val none = render("$voices.body(material = \"wood\", wet = 0.3).orbit(1)")
        val declared = render("$voices.body(material = \"wood\", wet = 0.3).orbit(1)$declaration")

        declared.size shouldBe none.size

        // The render has to be LOUD, or an all-silent pair would pass this row without saying
        // anything. A supersaw chord through a body is nowhere near quiet.
        val peak = peakOf(none)

        withClue("the song actually made sound") { (peak > 3000) shouldBe true }
        withClue("max |declared - undeclared| in 16-bit counts, peak $peak") {
            maxDiff(none, declared) shouldBe 0
        }
    }

    "a declared classic chain keeps a MATERIAL-ONLY body(material) too, at the engine's own wet" {
        // The spelling the door writes one slot for. The amount comes from an unset `body.wet`
        // slot, which the engine substitutes BODY_WET for, exactly as the voice path does for a
        // null `bodyMix`. With a SET 0.0 default this row rendered a fully dry orbit and failed by
        // thousands of counts while the row above still passed, which is how the bug hid.
        val none = render("$voices.body(material = \"wood\").orbit(1)")
        val declared = render("$voices.body(material = \"wood\").orbit(1)$declaration")

        declared.size shouldBe none.size

        val peak = peakOf(none)

        withClue("the song actually made sound") { (peak > 3000) shouldBe true }
        withClue("max |declared - undeclared| in 16-bit counts, peak $peak") {
            maxDiff(none, declared) shouldBe 0
        }
    }

    "a declared classic chain keeps a partial compressor(ratio = 8), at the same threshold" {
        // The compressor has no on-knob, so ANY of its five is the gate, and since step 5a-3 the
        // door fills the other four with exactly the constants `Voice.Compressor.fromParams` had
        // been substituting for a null field. Declared or not, the orbit therefore compresses with
        // the same five numbers, and the samples have to agree. Before the fill this row would have
        // compared a compressor at the engine's threshold against a declared chain resolving its
        // own unset slots, which is the same shape of bug the body row caught.
        val none = render("$voices.gain(1.4).compressor(ratio = 8).orbit(1)")
        val declared = render("$voices.gain(1.4).compressor(ratio = 8).orbit(1)$declaration")

        declared.size shouldBe none.size

        val peak = peakOf(none)

        withClue("the song actually made sound") { (peak > 3000) shouldBe true }
        withClue("max |declared - undeclared| in 16-bit counts, peak $peak") {
            maxDiff(none, declared) shouldBe 0
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

    "the positive control: a declaration that names NO body really does take over the orbit" {
        // The other half of the control, and the one the rows above cannot supply. They prove
        // "declared == undeclared", which a declaration that never installed at all would also
        // satisfy. So: declare a chain with a `gain` stage and nothing else. That orbit has no body
        // stage, so the same `body(...)` call has nowhere to land and the render MUST differ.
        // Together with the rows above this says the declaration is live and the body survives it.
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
