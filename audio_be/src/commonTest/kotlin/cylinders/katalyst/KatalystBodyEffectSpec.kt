/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.BANK_CROSSFADE_SECONDS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Contract for the orbit-level body resonator: inactive until configured; since only the OWNER voice
 * configures it (via VoiceLease), `null` (owner has no body) turns it off; and `reset()` deactivates it.
 */
class KatalystBodyEffectSpec : StringSpec({

    val sampleRate = 44100.0
    val n = 128

    fun contextWithConstantMix(value: Double): Pair<KatalystContext, StereoBuffer> {
        val mix = StereoBuffer(n)
        mix.left.fill(value)
        mix.right.fill(value)
        val ctx = KatalystContext(
            blockFrames = n,
            mixBuffer = mix,
        )
        return ctx to mix
    }

    val woodish = FilterDef.Body(
        bands = listOf(FilterDef.Body.Mode(freq = 300.0, db = 6.0, q = 8.0)),
        mix = 1.0,
    )

    val glassy = FilterDef.Body(
        bands = listOf(FilterDef.Body.Mode(freq = 520.0, db = 9.0, q = 12.0)),
        mix = 0.8,
    )

    "inactive body is a no-op on the mix" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        KatalystBodyEffect(sampleRate).process(ctx)
        mix.left[n - 1] shouldBe 1.0
        mix.right[n - 1] shouldBe 1.0
    }

    "a configured body colours the mix" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystBodyEffect(sampleRate)
        fx.configure(woodish)
        fx.process(ctx)
        // A bandpass body on a DC step blends toward BODY_FLOOR·dry — the sample must have changed.
        mix.left[n - 1] shouldNotBe 1.0
    }

    "configure(null) turns the body off (only the owner configures now, so null = owner has no body)" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystBodyEffect(sampleRate)
        fx.configure(woodish)
        fx.configure(null) // the owning voice has no body → resonator off
        fx.process(ctx)
        mix.left[n - 1] shouldBe 1.0 // mix untouched — body is off
    }

    "reset() deactivates the body" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystBodyEffect(sampleRate)
        fx.configure(woodish)
        fx.reset()
        fx.process(ctx)
        mix.left[n - 1] shouldBe 1.0
    }

    "body floor is honored — a lower floor passes less dry (guards bodyFloor() plumbing)" {
        fun outAt(floor: Double): Double {
            val (ctx, mix) = contextWithConstantMix(1.0)
            KatalystBodyEffect(sampleRate).apply { configure(woodish.copy(floor = floor)) }.process(ctx)
            return mix.left[n - 1]
        }
        // At mix=1 the dry is held at `floor`; the wet is floor-independent, so it cancels. If the
        // floor were ignored (the KatalystFormantEffect bug), these would be equal.
        outAt(0.2) shouldBeLessThan outAt(0.8)
    }

    "a live material change does not step the output (declick crossfade)" {
        val bodyA = FilterDef.Body(bands = listOf(FilterDef.Body.Mode(120.0, 9.0, 12.0)), mix = 1.0)
        val bodyB = FilterDef.Body(bands = listOf(FilterDef.Body.Mode(320.0, 9.0, 12.0)), mix = 1.0)
        val fx = KatalystBodyEffect(sampleRate)
        val freq = 110.0 // near bodyA's mode → a strong ring to swap out of

        var phase = 0
        fun runSineBlock(): DoubleArray {
            val (ctx, mix) = contextWithConstantMix(0.0)
            for (i in 0 until n) {
                val s = sin(2.0 * PI * freq * (phase + i) / sampleRate)
                mix.left[i] = s
                mix.right[i] = s
            }
            fx.process(ctx)
            phase += n
            return DoubleArray(n) { mix.left[it] }
        }

        fx.configure(bodyA)
        var block = DoubleArray(n)
        repeat(12) { block = runSineBlock() } // let the ring settle on bodyA

        val lastA = block[n - 1]
        val naturalStep = (1 until n).maxOf { abs(block[it] - block[it - 1]) }

        fx.configure(bodyB) // live material swap while the orbit is ringing
        val boundaryStep = abs(runSineBlock()[0] - lastA)

        // Continuous: the swap-boundary jump is within a few natural per-sample steps. A bare
        // filter-instance swap would jump by the whole ring amplitude (≫ naturalStep) → a click.
        boundaryStep shouldBeLessThan (naturalStep * 4.0)
    }

    // ── Non-finite knobs ─────────────────────────────────────────────────────────────────────────
    //
    // A pattern can write one: `body(material = "wood", wet = "NaN")` parses to a NaN, and
    // `SprudelVoiceData.toVoiceData` guards a null mix, not a non-finite one. The declared path
    // substitutes in `KatalystSlots.bodyDef`, so these rows are the born-with half of one rule.

    val nonFinite = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

    /** The probe signal, synthesised: sample [i] of a sine at [freq], counted from the run's start. */
    fun sineAt(freq: Double, i: Int): Double = sin(2.0 * PI * freq * i / sampleRate)

    /** [blocks] blocks of that sine, the DRY reference, made without touching the code under test. */
    fun drySine(freq: Double, blocks: Int): DoubleArray = DoubleArray(blocks * n) { sineAt(freq, it) }

    /**
     * Renders [blocks] blocks of a sine at [freq] through ONE stage. With [everyBlock] the stage is
     * re-offered the SAME def before every block, which is what the cylinder does for the orbit's
     * owner voice on every block it is alive.
     */
    fun renderSine(def: FilterDef.Body, freq: Double, everyBlock: Boolean, blocks: Int): DoubleArray {
        val fx = KatalystBodyEffect(sampleRate)
        val out = DoubleArray(blocks * n)

        fx.configure(def)

        for (b in 0 until blocks) {
            if (everyBlock) {
                fx.configure(def)
            }

            val (ctx, mix) = contextWithConstantMix(0.0)

            for (i in 0 until n) {
                val s = sineAt(freq, b * n + i)
                mix.left[i] = s
                mix.right[i] = s
            }

            fx.process(ctx)

            for (i in 0 until n) {
                out[b * n + i] = mix.left[i]
            }
        }

        return out
    }

    "a non-finite wet is UNSET and installs at BODY_WET, on all three spellings" {
        // Not just NaN: an infinity reaches the wet/dry law too, where a non-finite amount reads as
        // a fully dry 0.0, so the orbit would lose its body without saying so.
        nonFinite.forEach { unset ->
            val fx = KatalystBodyEffect(sampleRate)

            fx.configure(woodish.copy(mix = unset))

            withClue("wet = $unset") {
                fx.isEngaged shouldBe true
                fx.installedBands shouldBe woodish.bands
                fx.installedMix shouldBe BODY_WET
            }
        }
    }

    "a non-finite floor takes BODY_FLOOR; a null floor stays null and a finite one is untouched" {
        nonFinite.forEach { unset ->
            val fx = KatalystBodyEffect(sampleRate)

            fx.configure(woodish.copy(floor = unset))

            withClue("floor = $unset") { fx.installedFloor shouldBe BODY_FLOOR }
        }

        // null is not an accident, it IS the engine default: `createBody` reads it as BODY_FLOOR,
        // and `KatalystClassicMatchesUntouchedVoiceSpec` pins that the voice path spells it null
        // while a declared chain writes the same number out.
        KatalystBodyEffect(sampleRate).apply { configure(woodish) }.installedFloor shouldBe null

        // And a finite floor is the author's own, untouched: no clamp was added here.
        KatalystBodyEffect(sampleRate)
            .apply { configure(woodish.copy(floor = 0.05)) }
            .installedFloor shouldBe 0.05
    }

    "the SAME def with a non-finite knob installs ONCE, not one bank per block" {
        // The defect this row is born from. The owner re-offers its def every block, and
        // `body.mix != curMix` is TRUE forever for a NaN against a NaN, so every block allocated
        // two filter banks on the audio thread and restarted the crossfade (12 ms then), which then never
        // completed. Substituting BEFORE the comparison is what makes it settle.
        //
        // The FLOOR is in this row and not only in the seam row above: being nullable does not
        // save it. A `Double?` pair of NaNs answers "not equal" on the JVM and on Kotlin/JS alike
        // (measured 2026-09-18), so `floor != curFloor` was self-unequal in exactly the same way
        // and the unguarded floor spun the bank every block too.
        //
        // Two limits of the measurement, so nobody reads more out of it than is here. The row is
        // BLIND AT BLOCK 0: a re-install there swaps one zero-state bank for another and the
        // output is identical; blocks 1 to 7 are what carry it. And under a missing WET guard the
        // row dies on the teeth clue first (a non-finite amount bypasses, so there is no bank to
        // hear at all), which leaves the FLOOR sub-case as the one that binds the install count.
        val freq = 300.0 // woodish's own mode, so the bank rings hard and a fresh one is nothing like it

        listOf(
            "wet" to woodish.copy(mix = Double.NaN),
            "floor" to woodish.copy(floor = Double.NaN),
        ).forEach { (knob, def) ->
            val once = renderSine(def, freq, everyBlock = false, blocks = 8)
            val perBlock = renderSine(def, freq, everyBlock = true, blocks = 8)

            val dry = drySine(freq, blocks = 8)

            val coloured = once.indices.maxOf { abs(once[it] - dry[it]) }
            val worst = once.indices.maxOf { abs(perBlock[it] - once[it]) }

            // Teeth: the bank really runs. Without the wet substitution both renders would be the
            // same bypassed sine and the comparison below would pass saying nothing.
            withClue("non-finite $knob: max |body - dry| was $coloured") { coloured shouldBeGreaterThan 0.01 }

            // A re-install on any of blocks 1 to 7 starts a crossfade to a zero-state bank, which
            // differs from the ringing one from its first sample on (the fade is 2205 frames, so
            // it is still running at the end; it does not need to finish to show).
            withClue("non-finite $knob: max |re-offered every block - offered once| was $worst") {
                worst shouldBe 0.0
            }
        }
    }

    "after reset() the next configure installs again, the identical def included" {
        // What forces the install is `curBands = null`, the FIRST term of the short-circuiting
        // `||`, and not the mix sentinel the earlier wording credited: `curMix = NaN` against a
        // substituted BODY_WET would do it too, and so would the floor, which is exactly why each
        // of the three is read back below instead of being inferred from `isEngaged`.
        val def = woodish.copy(mix = Double.NaN)
        val fx = KatalystBodyEffect(sampleRate)

        fx.configure(def)
        fx.isEngaged shouldBe true

        fx.reset()
        fx.isEngaged shouldBe false

        // "Nothing installed" written out through the seams, because each of the three writes
        // would force the next install ON ITS OWN and the `||` short-circuits on the first of
        // them: a row watching only `isEngaged` could not tell which of them still works.
        fx.installedBands shouldBe null
        // Raw NaN reads, the one case code-style 23 names as the exception: `shouldNotBe` would
        // pass for the wrong reason here, a NaN not being equal to itself whatever the field holds.
        fx.installedMix.isNaN() shouldBe true
        fx.installedFloor?.isNaN() shouldBe true

        fx.configure(def)

        fx.isEngaged shouldBe true
        fx.installedMix shouldBe BODY_WET

        // The IDENTICAL def, bands and all: `BodyMaterials` hands out ONE shared list instance per
        // entry, and the stage leans on that identity to short-circuit an unchanged block. So the
        // def a reused orbit re-offers after a reset is literally the object that was installed
        // before, and the stage still has to install it rather than believe it is already there.
        val same = FilterDef.Body(bands = woodish.bands, mix = 0.3)

        fx.configure(same)
        fx.installedMix shouldBe 0.3

        fx.reset()
        fx.isEngaged shouldBe false

        fx.configure(same)

        fx.isEngaged shouldBe true
        fx.installedBands shouldBe woodish.bands
        fx.installedMix shouldBe 0.3
    }

    "a finite wet is the author's own, below 0 and above 1 included" {
        // The [0, 1] coercion of the wet/dry law is `ParallelMixFilter`'s and pre-dates this stage.
        // Nothing here clamps: the Motor stays raw, and what the stage installs is what it was told.
        listOf(-0.5, 0.0, 0.3, 1.0, 2.5).forEach { wet ->
            val fx = KatalystBodyEffect(sampleRate)

            fx.configure(woodish.copy(mix = wet))

            withClue("wet = $wet") { fx.installedMix shouldBe wet }
        }
    }
    // ── Every edge fades (Katalyst steps 5c-6 and 5c-11) ─────────────────────────────────────────────────────
    //
    // The oracle is `FilterSwapLaw`, the decided switching law, applied to reference banks built
    // from the bare DSP (`LowPassHighPassFilters.createBody`) and run on the same input.

    val fadeLen = (sampleRate * BANK_CROSSFADE_SECONDS).toInt()

    /** Blocks a fade needs to LAND: the stage installs a parked config at the end of the last one. */
    val fadeBlocks = (fadeLen + n - 1) / n
    val landBlocks = fadeBlocks + 1

    fun script(fx: KatalystBodyEffect, blocks: Int): SwapHostScript =
        SwapHostScript(n, fadeLen, drySine(300.0, blocks)) { fx.process(it) }

    fun ref(def: FilterDef.Body) = LowPassHighPassFilters.createBody(def.bands, def.mix, sampleRate, def.floor)

    "off fades the bank to dry and releases it; the SAME body after that fade-out installs afresh" {
        // Question 2 of the plan: the config cache survives the fade-out, and an unchanged def with
        // the intent off must not be taken for "already installed". The first bank has faded out,
        // so the second install is a NEW bank fading in from dry.
        val fx = KatalystBodyEffect(sampleRate)
        val s = script(fx, blocks = 12 + 2 * landBlocks)
        val first = s.reference(ref(woodish))

        fx.configure(woodish)
        s.law.set(first)
        repeat(12) { s.step("on") }

        fx.configure(null)
        s.law.clear()

        withClue("intent off at once, the sound still fading") {
            fx.isEngaged shouldBe false
            fx.isSounding shouldBe true
        }

        repeat(landBlocks) {
            fx.configure(null)
            s.step("fading out")
        }

        withClue("landed on dry: released") { fx.isSounding shouldBe false }

        val second = s.reference(ref(woodish))
        fx.configure(woodish)
        s.law.set(second)

        withClue("the identical def installs again") {
            fx.isEngaged shouldBe true
            fx.isSounding shouldBe true
        }

        repeat(landBlocks - 2) {
            fx.configure(woodish)
            s.step("fading in")
        }
    }

    "an owner that returns mid-fade-out takes the fading bank back where it stands" {
        // The re-entry requirement: continuous, never a restart. A NEW bank here would start from
        // zero state and differ from the reference bank that ran on all along.
        val fx = KatalystBodyEffect(sampleRate)
        val s = script(fx, blocks = 10 + 5 + landBlocks)
        val bank = s.reference(ref(woodish))

        fx.configure(woodish)
        s.law.set(bank)
        repeat(10) { s.step("on") }

        fx.configure(null)
        s.law.clear()
        repeat(5) {
            fx.configure(null)
            s.step("fading out")
        }

        fx.configure(woodish)
        s.law.resume(bank) shouldBe true
        fx.isEngaged shouldBe true

        repeat(landBlocks) {
            fx.configure(woodish)
            s.step("turned around")
        }
    }

    "a change mid-fade is PARKED: the fade in flight is untouched, and it installs on the landing" {
        // Two banks and ONE parking slot (Katalyst 5c-11). The law knows nothing of the parking,
        // so it says "the a-to-b fade runs on"; the stage is compared against that sample for
        // sample, and a stage that installed c early would depart from it at once.
        val fx = KatalystBodyEffect(sampleRate)
        val s = script(fx, blocks = 8 + fadeBlocks + landBlocks)
        val a = s.reference(ref(woodish))

        fx.configure(woodish)
        s.law.set(a)
        repeat(8) { fx.configure(woodish); s.step("a") }

        val b = s.reference(ref(glassy))

        fx.configure(glassy)
        s.law.set(b)
        s.step("a to b")

        val c = woodish.copy(mix = 0.5)

        fx.configure(c)

        withClue("the change waits, and what is installed is still b") {
            fx.isParked shouldBe true
            fx.isEngaged shouldBe true
            fx.installedBands shouldBe glassy.bands
        }

        repeat(fadeBlocks - 1) { fx.configure(c); s.step("a to b, undisturbed") }

        withClue("the fade landed, so the parked change is in") {
            fx.isParked shouldBe false
            fx.installedBands shouldBe c.bands
            fx.installedMix shouldBe 0.5
        }

        // It crossfades from the SETTLED bank (b), which is what the law is told here.
        val cRef = s.reference(ref(c))

        s.law.set(cRef)
        repeat(fadeBlocks) { fx.configure(c); s.step("b to c") }
    }

    "a further change REPLACES what is parked: only the last one ever sounds" {
        val fx = KatalystBodyEffect(sampleRate)
        val s = script(fx, blocks = 4 + fadeBlocks + landBlocks)
        val a = s.reference(ref(woodish))

        fx.configure(woodish)
        s.law.set(a)
        repeat(4) { fx.configure(woodish); s.step("a") }

        val b = s.reference(ref(glassy))

        fx.configure(glassy)
        s.law.set(b)
        s.step("a to b")

        val overtaken = woodish.copy(mix = 0.25)
        val last = woodish.copy(mix = 0.75)

        fx.configure(overtaken)
        fx.configure(last)

        repeat(fadeBlocks - 1) { fx.configure(last); s.step("a to b, undisturbed") }

        withClue("the overtaken change never installed; the last one did") {
            fx.installedMix shouldBe 0.75
        }

        val lastRef = s.reference(ref(last))

        s.law.set(lastRef)
        repeat(fadeBlocks) { fx.configure(last); s.step("b to the last change") }
    }

    "an OFF that arrives mid-fade is parked too: the intent flips at once, the fade to dry waits" {
        val fx = KatalystBodyEffect(sampleRate)
        val s = script(fx, blocks = 4 + fadeBlocks + landBlocks)
        val a = s.reference(ref(woodish))

        fx.configure(woodish)
        s.law.set(a)
        repeat(4) { fx.configure(woodish); s.step("a") }

        val b = s.reference(ref(glassy))

        fx.configure(glassy)
        s.law.set(b)
        s.step("a to b")

        fx.configure(null)

        withClue("intent off at once, the sound still fading in") {
            fx.isEngaged shouldBe false
            fx.isSounding shouldBe true
            fx.isParked shouldBe true
        }

        repeat(fadeBlocks - 1) { fx.configure(null); s.step("a to b, undisturbed") }

        s.law.clear()
        repeat(fadeBlocks) { fx.configure(null); s.step("b to dry") }

        withClue("landed on dry: released") { fx.isSounding shouldBe false }
    }

    "a return to the config that is fading IN drops the parked change, and nothing is installed" {
        // The owner's latest word is what already sounds, so the parked one is overtaken by it.
        val fx = KatalystBodyEffect(sampleRate)
        val s = script(fx, blocks = 4 + 2 * fadeBlocks + 4)
        val a = s.reference(ref(woodish))

        fx.configure(woodish)
        s.law.set(a)
        repeat(4) { fx.configure(woodish); s.step("a") }

        val b = s.reference(ref(glassy))

        fx.configure(glassy)
        s.law.set(b)
        s.step("a to b")

        fx.configure(woodish.copy(mix = 0.25))
        fx.isParked shouldBe true

        fx.configure(glassy)

        withClue("back to what is installed: nothing waits any more") {
            fx.isParked shouldBe false
            fx.installedBands shouldBe glassy.bands
        }

        repeat(fadeBlocks + 2) { fx.configure(glassy); s.step("a to b, and then b alone") }
    }

    "reset() mid-fade-out is a hard cut: dry at once, and the next life starts on a fresh bank at once" {
        // The cylinder's deactivation and retire. A fade that survived would resume in the orbit's
        // next life, on new material. Two runs of the same life, because the first block after the
        // cut would spend the snap the second half needs (in the engine no block runs between an
        // orbit's deactivation and its next life).
        fun lifeThenCut(): Pair<KatalystBodyEffect, SwapHostScript> {
            val fx = KatalystBodyEffect(sampleRate)
            val s = script(fx, blocks = 20)
            val bank = s.reference(ref(woodish))

            fx.configure(woodish)
            s.law.set(bank)
            repeat(10) { s.step("on") }

            fx.configure(null)
            s.law.clear()
            repeat(3) { s.step("fading out") }

            fx.retire()

            withClue("nothing sounding, nothing intended") {
                fx.isSounding shouldBe false
                fx.isEngaged shouldBe false
            }

            return fx to s
        }

        val (_, cutRun) = lifeThenCut()
        val dry = cutRun.inputBlock(cutRun.block)
        val cut = cutRun.raw()

        withClue("the block after the cut is the dry input, bit for bit") {
            (0 until n).all { cut[it].toRawBits() == dry[it].toRawBits() } shouldBe true
        }

        val (fx, nextRun) = lifeThenCut()
        val fresh = ref(glassy)
        val next = nextRun.inputBlock(nextRun.block)
        fresh.process(next, 0, n)
        fx.configure(glassy)
        val got = nextRun.raw()

        withClue("the first block of the next life is the fresh bank alone") {
            (0 until n).all { got[it].toRawBits() == next[it].toRawBits() } shouldBe true
        }
    }

    // ── A bank never changes (the morph of Katalyst 5c-10, REJECTED 2026-09-20) ──────────────────

    "a material change builds a NEW bank and crossfades: the bank in service is never retuned" {
        // The morph that travelled the bands of the bank in service is gone: the maintainer heard
        // it as a filter sweep. The teeth are the reference bank, which is FRESH at the change:
        // a stage that retuned the bank it had would carry that bank's ringing state into the
        // change and differ from this oracle from the first sample.
        val fx = KatalystBodyEffect(sampleRate)
        val s = script(fx, blocks = 6 + fadeBlocks + 4)
        val a = s.reference(ref(woodish))

        fx.configure(woodish)
        s.law.set(a)
        repeat(6) { fx.configure(woodish); s.step("wood") }

        val sameWet = FilterDef.Body(bands = glassy.bands, mix = woodish.mix)
        val b = s.reference(ref(sameWet))

        fx.configure(sameWet)
        s.law.set(b)
        repeat(fadeBlocks + 3) { fx.configure(sameWet); s.step("wood to glass") }
    }
})
