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
import io.peekandpoke.klang.audio_bridge.constants.BANK_CROSSFADE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Contract for the orbit-level vowel/formant resonator — the twin of [KatalystBodyEffectSpec].
 * Exists because the formant effect diverged from the body effect (it was missing the `floor`
 * plumbing that `vowelFloor()` relies on); the floor test below guards that parity.
 */
class KatalystFormantEffectSpec : StringSpec({

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

    val vowelish = FilterDef.Formant(
        bands = listOf(FilterDef.Formant.Band(freq = 700.0, db = 0.0, q = 10.0)),
        mix = 1.0,
    )

    val ohish = FilterDef.Formant(
        bands = listOf(FilterDef.Formant.Band(freq = 450.0, db = 0.0, q = 8.0)),
        mix = 0.8,
    )

    "inactive vowel is a no-op on the mix" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        KatalystFormantEffect(sampleRate).process(ctx)
        mix.left[n - 1] shouldBe 1.0
        mix.right[n - 1] shouldBe 1.0
    }

    "a configured vowel colours the mix" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        KatalystFormantEffect(sampleRate).apply { configure(vowelish) }.process(ctx)
        mix.left[n - 1] shouldNotBe 1.0
    }

    "configure(null) turns the vowel off" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystFormantEffect(sampleRate)
        fx.configure(vowelish)
        fx.configure(null)
        fx.process(ctx)
        mix.left[n - 1] shouldBe 1.0
    }

    "vowel floor is honored — a lower floor passes less dry (guards vowelFloor() plumbing)" {
        fun outAt(floor: Double): Double {
            val (ctx, mix) = contextWithConstantMix(1.0)
            KatalystFormantEffect(sampleRate).apply { configure(vowelish.copy(floor = floor)) }.process(ctx)
            return mix.left[n - 1]
        }
        // At mix=1 the dry is held at `floor`; the wet is floor-independent, so it cancels. This is the
        // exact test that was missing when the formant effect dropped the floor arg.
        outAt(0.2) shouldBeLessThan outAt(0.8)
    }

    // ── Non-finite knobs: the twin of KatalystBodyEffectSpec's rows, same rule, vowel constants ───
    //
    // `vowel("a", wet = "NaN")` parses to a NaN and `SprudelVoiceData.toVoiceData` guards a null
    // mix, not a non-finite one, so the born-with chain has to substitute here exactly as
    // `KatalystSlots.vowelDef` does for a declared chain's slots.

    val nonFinite = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

    /** The probe signal, synthesised: sample [i] of a sine at [freq], counted from the run's start. */
    fun sineAt(freq: Double, i: Int): Double = sin(2.0 * PI * freq * i / sampleRate)

    /** [blocks] blocks of that sine, the DRY reference, made without touching the code under test. */
    fun drySine(freq: Double, blocks: Int): DoubleArray = DoubleArray(blocks * n) { sineAt(freq, it) }

    /**
     * Renders [blocks] blocks of a sine at [freq] through ONE stage. With [everyBlock] the stage is
     * re-offered the SAME def before every block, which is what the cylinder does for the owner
     * voice on every block it is alive.
     */
    fun renderSine(def: FilterDef.Formant, freq: Double, everyBlock: Boolean, blocks: Int): DoubleArray {
        val fx = KatalystFormantEffect(sampleRate)
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

    "a non-finite wet is UNSET and installs at VOWEL_WET, on all three spellings" {
        nonFinite.forEach { unset ->
            val fx = KatalystFormantEffect(sampleRate)

            fx.configure(vowelish.copy(mix = unset))

            withClue("wet = $unset") {
                fx.isEngaged shouldBe true
                fx.installedBands shouldBe vowelish.bands
                fx.installedMix shouldBe VOWEL_WET
            }
        }
    }

    "a non-finite floor takes VOWEL_FLOOR; a null floor stays null and a finite one is untouched" {
        nonFinite.forEach { unset ->
            val fx = KatalystFormantEffect(sampleRate)

            fx.configure(vowelish.copy(floor = unset))

            withClue("floor = $unset") { fx.installedFloor shouldBe VOWEL_FLOOR }
        }

        KatalystFormantEffect(sampleRate).apply { configure(vowelish) }.installedFloor shouldBe null

        KatalystFormantEffect(sampleRate)
            .apply { configure(vowelish.copy(floor = 0.05)) }
            .installedFloor shouldBe 0.05
    }

    "the SAME def with a non-finite knob installs ONCE, not one bank per block" {
        // The body's defect, on the twin: `vowel.mix != curMix` is true forever for a NaN against a
        // NaN, so the owner's per-block re-offer allocated two formant banks per block on the audio
        // thread and restarted a crossfade (12 ms then) that never completed. The floor is here for the
        // reason the body's row gives (measured on both targets), with the same two limits: the row is blind at block 0, and
        // under a missing wet guard it dies on the teeth clue before it can count installs.
        val freq = 700.0 // vowelish's own band, so the bank rings and a fresh one is nothing like it

        listOf(
            "wet" to vowelish.copy(mix = Double.NaN),
            "floor" to vowelish.copy(floor = Double.NaN),
        ).forEach { (knob, def) ->
            val once = renderSine(def, freq, everyBlock = false, blocks = 8)
            val perBlock = renderSine(def, freq, everyBlock = true, blocks = 8)

            val dry = drySine(freq, blocks = 8)

            val coloured = once.indices.maxOf { abs(once[it] - dry[it]) }
            val worst = once.indices.maxOf { abs(perBlock[it] - once[it]) }

            withClue("non-finite $knob: max |vowel - dry| was $coloured") { coloured shouldBeGreaterThan 0.01 }
            withClue("non-finite $knob: max |re-offered every block - offered once| was $worst") {
                worst shouldBe 0.0
            }
        }
    }

    "after reset() the next configure installs again, the identical def included" {
        // `curBands = null` is the term that fires first; the mix and floor sentinels would each
        // do it alone as well, which is why all three are read back through the seams below. The
        // body's row says it at length.
        val def = vowelish.copy(mix = Double.NaN)
        val fx = KatalystFormantEffect(sampleRate)

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
        fx.installedMix shouldBe VOWEL_WET

        // The IDENTICAL def: `VowelBands` hands out one shared band list per entry, and the stage
        // leans on that identity to short-circuit an unchanged block, so a reused orbit re-offers
        // the very object that was installed before and must still get a bank.
        val same = FilterDef.Formant(bands = vowelish.bands, mix = 0.3)

        fx.configure(same)
        fx.installedMix shouldBe 0.3

        fx.reset()
        fx.isEngaged shouldBe false

        fx.configure(same)

        fx.isEngaged shouldBe true
        fx.installedBands shouldBe vowelish.bands
        fx.installedMix shouldBe 0.3
    }

    "a finite wet is the author's own, below 0 and above 1 included" {
        // Nothing here clamps: the [0, 1] coercion is `ParallelMixFilter`'s and the Motor stays raw.
        listOf(-0.5, 0.0, 0.3, 1.0, 2.5).forEach { wet ->
            val fx = KatalystFormantEffect(sampleRate)

            fx.configure(vowelish.copy(mix = wet))

            withClue("wet = $wet") { fx.installedMix shouldBe wet }
        }
    }
    // ── Every edge fades (Katalyst steps 5c-6 and 5c-11) ─────────────────────────────────────────────────────
    //
    // The oracle is `FilterSwapLaw`, the decided switching law, applied to reference banks built
    // from the bare DSP (`LowPassHighPassFilters.createFormant`) and run on the same input.

    val fadeLen = (sampleRate * BANK_CROSSFADE_SECONDS).toInt()

    /** Blocks a fade needs to LAND: the stage installs a parked config at the end of the last one. */
    val fadeBlocks = (fadeLen + n - 1) / n
    val landBlocks = fadeBlocks + 1

    fun script(fx: KatalystFormantEffect, blocks: Int): SwapHostScript =
        SwapHostScript(n, fadeLen, drySine(700.0, blocks)) { fx.process(it) }

    fun ref(def: FilterDef.Formant) = LowPassHighPassFilters.createFormant(def.bands, def.mix, sampleRate, def.floor)

    "off fades the bank to dry and releases it; the SAME vowel after that fade-out installs afresh" {
        // Question 2 of the plan: the config cache survives the fade-out, and an unchanged def with
        // the intent off must not be taken for "already installed". The first bank has faded out,
        // so the second install is a NEW bank fading in from dry.
        val fx = KatalystFormantEffect(sampleRate)
        val s = script(fx, blocks = 12 + 2 * landBlocks)
        val first = s.reference(ref(vowelish))

        fx.configure(vowelish)
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

        val second = s.reference(ref(vowelish))
        fx.configure(vowelish)
        s.law.set(second)

        withClue("the identical def installs again") {
            fx.isEngaged shouldBe true
            fx.isSounding shouldBe true
        }

        repeat(landBlocks - 2) {
            fx.configure(vowelish)
            s.step("fading in")
        }
    }

    "an owner that returns mid-fade-out takes the fading bank back where it stands" {
        // The re-entry requirement: continuous, never a restart. A NEW bank here would start from
        // zero state and differ from the reference bank that ran on all along.
        val fx = KatalystFormantEffect(sampleRate)
        val s = script(fx, blocks = 10 + 5 + landBlocks)
        val bank = s.reference(ref(vowelish))

        fx.configure(vowelish)
        s.law.set(bank)
        repeat(10) { s.step("on") }

        fx.configure(null)
        s.law.clear()
        repeat(5) {
            fx.configure(null)
            s.step("fading out")
        }

        fx.configure(vowelish)
        s.law.resume(bank) shouldBe true
        fx.isEngaged shouldBe true

        repeat(landBlocks) {
            fx.configure(vowelish)
            s.step("turned around")
        }
    }

    "a change mid-fade is PARKED: the fade in flight is untouched, and it installs on the landing" {
        // The twin of `KatalystBodyEffectSpec`'s parking row; the full note is there.
        val fx = KatalystFormantEffect(sampleRate)
        val s = script(fx, blocks = 8 + fadeBlocks + landBlocks)
        val a = s.reference(ref(vowelish))

        fx.configure(vowelish)
        s.law.set(a)
        repeat(8) { fx.configure(vowelish); s.step("a") }

        val b = s.reference(ref(ohish))

        fx.configure(ohish)
        s.law.set(b)
        s.step("a to b")

        val c = vowelish.copy(mix = 0.5)

        fx.configure(c)

        withClue("the change waits, and what is installed is still b") {
            fx.isParked shouldBe true
            fx.isEngaged shouldBe true
            fx.installedBands shouldBe ohish.bands
        }

        repeat(fadeBlocks - 1) { fx.configure(c); s.step("a to b, undisturbed") }

        withClue("the fade landed, so the parked change is in") {
            fx.isParked shouldBe false
            fx.installedBands shouldBe c.bands
            fx.installedMix shouldBe 0.5
        }

        val cRef = s.reference(ref(c))

        s.law.set(cRef)
        repeat(fadeBlocks) { fx.configure(c); s.step("b to c") }
    }

    "a further change REPLACES what is parked: only the last one ever sounds" {
        val fx = KatalystFormantEffect(sampleRate)
        val s = script(fx, blocks = 4 + fadeBlocks + landBlocks)
        val a = s.reference(ref(vowelish))

        fx.configure(vowelish)
        s.law.set(a)
        repeat(4) { fx.configure(vowelish); s.step("a") }

        val b = s.reference(ref(ohish))

        fx.configure(ohish)
        s.law.set(b)
        s.step("a to b")

        fx.configure(vowelish.copy(mix = 0.25))
        fx.configure(vowelish.copy(mix = 0.75))

        repeat(fadeBlocks - 1) { fx.configure(vowelish.copy(mix = 0.75)); s.step("a to b, undisturbed") }

        withClue("the overtaken change never installed; the last one did") {
            fx.installedMix shouldBe 0.75
        }

        val lastRef = s.reference(ref(vowelish.copy(mix = 0.75)))

        s.law.set(lastRef)
        repeat(fadeBlocks) { fx.configure(vowelish.copy(mix = 0.75)); s.step("b to the last change") }
    }

    "an OFF that arrives mid-fade is parked too: the intent flips at once, the fade to dry waits" {
        val fx = KatalystFormantEffect(sampleRate)
        val s = script(fx, blocks = 4 + fadeBlocks + landBlocks)
        val a = s.reference(ref(vowelish))

        fx.configure(vowelish)
        s.law.set(a)
        repeat(4) { fx.configure(vowelish); s.step("a") }

        val b = s.reference(ref(ohish))

        fx.configure(ohish)
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

    "reset() mid-fade-out is a hard cut: dry at once, and the next life starts on a fresh bank at once" {
        // The cylinder's deactivation and retire. A fade that survived would resume in the orbit's
        // next life, on new material. Two runs of the same life, because the first block after the
        // cut would spend the snap the second half needs (in the engine no block runs between an
        // orbit's deactivation and its next life).
        fun lifeThenCut(): Pair<KatalystFormantEffect, SwapHostScript> {
            val fx = KatalystFormantEffect(sampleRate)
            val s = script(fx, blocks = 20)
            val bank = s.reference(ref(vowelish))

            fx.configure(vowelish)
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
        val fresh = ref(ohish)
        val next = nextRun.inputBlock(nextRun.block)
        fresh.process(next, 0, n)
        fx.configure(ohish)
        val got = nextRun.raw()

        withClue("the first block of the next life is the fresh bank alone") {
            (0 until n).all { got[it].toRawBits() == next[it].toRawBits() } shouldBe true
        }
    }

    "a return to the config that is fading IN drops the parked change, and nothing is installed" {
        // The twin of the body's row. Without it the two lines that clear the slot in the
        // `unchanged` branch of `configure` are unguarded on this host.
        val fx = KatalystFormantEffect(sampleRate)
        val s = script(fx, blocks = 4 + 2 * fadeBlocks + 4)
        val a = s.reference(ref(vowelish))

        fx.configure(vowelish)
        s.law.set(a)
        repeat(4) { fx.configure(vowelish); s.step("a") }

        val b = s.reference(ref(ohish))

        fx.configure(ohish)
        s.law.set(b)
        s.step("a to b")

        fx.configure(vowelish.copy(mix = 0.25))
        fx.isParked shouldBe true

        fx.configure(ohish)

        withClue("back to what is installed: nothing waits any more") {
            fx.isParked shouldBe false
            fx.installedBands shouldBe ohish.bands
        }

        repeat(fadeBlocks + 2) { fx.configure(ohish); s.step("a to b, and then b alone") }
    }

    "a DIFFERENT vowel arriving during the fade-OUT parks, and installs from dry on the landing" {
        // The host half of "a change mid-fade": the fade in flight is a fade to DRY, so the parked
        // vowel installs from Off and fades in from dry, not from a bank. The swap half is
        // `KatalystFilterSwapSpec`; this is the one that drives the host's own door.
        val fx = KatalystFormantEffect(sampleRate)
        val s = script(fx, blocks = 4 + fadeBlocks + fadeBlocks + 4)
        val a = s.reference(ref(vowelish))

        fx.configure(vowelish)
        s.law.set(a)
        repeat(4) { fx.configure(vowelish); s.step("a") }

        fx.configure(null)
        s.law.clear()
        s.step("a to dry")

        fx.configure(ohish)

        withClue("the new vowel waits behind the fade-out, and the intent is back on at once") {
            fx.isParked shouldBe true
            fx.isEngaged shouldBe true
            fx.isSounding shouldBe true
        }

        repeat(fadeBlocks - 1) { fx.configure(ohish); s.step("a to dry, undisturbed") }

        withClue("the fade-out landed and the parked vowel went in") {
            fx.isParked shouldBe false
            fx.installedBands shouldBe ohish.bands
        }

        val b = s.reference(ref(ohish))

        s.law.set(b)
        repeat(fadeBlocks) { fx.configure(ohish); s.step("dry to b") }
    }

    // ── A bank never changes (the morph of Katalyst 5c-10, REJECTED 2026-09-20) ──────────────────

    "a vowel change builds a NEW bank and crossfades: the bank in service is never retuned" {
        // The formants do NOT travel any more. The teeth are the reference bank, which is FRESH
        // at the change: a stage that retuned the bank it had would carry its ringing state into
        // the change and differ from this oracle from the first sample.
        val fx = KatalystFormantEffect(sampleRate)
        val s = script(fx, blocks = 6 + fadeBlocks + 4)
        val a = s.reference(ref(vowelish))

        fx.configure(vowelish)
        s.law.set(a)
        repeat(6) { fx.configure(vowelish); s.step("a vowel") }

        val sameWet = FilterDef.Formant(bands = ohish.bands, mix = vowelish.mix)
        val b = s.reference(ref(sameWet))

        fx.configure(sameWet)
        s.law.set(b)
        repeat(fadeBlocks + 3) { fx.configure(sameWet); s.step("one vowel to another") }
    }
})
