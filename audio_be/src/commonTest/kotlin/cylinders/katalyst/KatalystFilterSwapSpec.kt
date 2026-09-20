/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_bridge.constants.BANK_CROSSFADE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS

/**
 * [KatalystFilterSwap] switches the resonant banks behind `body(...)`, `vowel(...)` and the orbit
 * `eq` without a click: every edge (on, off, a change, a return) is a linear crossfade over
 * [BANK_CROSSFADE_SECONDS] from what sounds NOW (Katalyst step 5c-6, decided with the maintainer
 * 2026-09-19; TWO BANKS and 20 ms since step 5c-11, 2026-09-20).
 *
 * The "filters" here are mostly plain gains on a DC input, so the output of any sequence of events
 * is exactly predictable, and the oracle is [FilterSwapLaw], the decided law written per sample
 * from scratch, never a number read off a run of the class.
 *
 * Why the ramp is pinned sample by sample and not only at its start: a ramp that never advanced
 * would be perfectly continuous at the swap sample (the output would simply stay the old bank) and
 * snap to the new one where the fade position finally crosses its end. Whether an outgoing pair is
 * actually RELEASED is invisible from the output (a retained pair at weight 0 sounds identical), so
 * the reference rows ask the swap through its `holds` seam instead.
 */
class KatalystFilterSwapSpec : StringSpec({

    val sampleRate = 44100.0
    val n = 128

    // KatalystFilterSwap computes this itself as (sampleRate * BANK_CROSSFADE_SECONDS).toInt(),
    // recomputed here from the same definition so the spec does not simply agree with the class.
    val fadeLen = (sampleRate * BANK_CROSSFADE_SECONDS).toInt() // 882, 6.9 blocks

    // Enough blocks for any fade in this file to land.
    val fadeBlocks = fadeLen / n + 2

    /** A "filter" that multiplies by a constant, so the crossfade output is exactly predictable. */
    class Gain(val g: Double) : AudioFilter {
        /** The last block this filter processed, so a spec can count the banks that ran. */
        var lastBlock = -1
        var block = 0

        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            lastBlock = block

            for (i in offset until offset + length) {
                buffer[i] *= g
            }
        }
    }

    fun dcBlock(): StereoBuffer = StereoBuffer(n).apply {
        for (i in 0 until n) {
            left[i] = 1.0
            right[i] = 1.0
        }
    }

    /**
     * Drives [swap] and [FilterSwapLaw] side by side. Each [Gain] is a bank id (its index in
     * [gains]); on the DC input its output is its gain, and dry is 1.0.
     */
    class Twin(sampleRate: Double, fadeLen: Int, val n: Int) {
        val swap = KatalystFilterSwap(sampleRate)
        val law = FilterSwapLaw(fadeLen)
        val gains = mutableListOf<Pair<Gain, Gain>>()
        var blocks = 0

        fun pair(g: Double): Int {
            gains += Gain(g) to Gain(g)

            return gains.size - 1
        }

        fun set(id: Int) {
            swap.set(gains[id].first, gains[id].second)
            law.set(id)
        }

        fun clear() {
            swap.clear()
            law.clear()
        }

        fun resume(id: Int): Boolean {
            val a = swap.resume(gains[id].first)
            val b = law.resume(id)

            withClue("resume($id): the swap and the law agree") { a shouldBe b }

            return a
        }

        fun reset() {
            swap.reset()
            law.reset()
        }

        /** One block; asserts every sample of both channels against the law. */
        fun block(label: String) {
            for ((l, r) in gains) {
                l.block = blocks
                r.block = blocks
            }

            val mix = StereoBuffer(n)
            mix.left.fill(1.0)
            mix.right.fill(1.0)

            swap.process(mix, n)

            val expected = law.block(n) { id, _ -> if (id == FilterSwapLaw.DRY) 1.0 else gains[id].first.g }

            for (k in 0 until n) {
                withClue("$label, block $blocks, sample $k") {
                    mix.left[k] shouldBe expected[k].plusOrMinus(1e-12)
                    mix.right[k] shouldBe expected[k].plusOrMinus(1e-12)
                }
            }

            blocks++
        }

        /** Banks that processed the last block. */
        fun ranLastBlock(): Int = gains.count { it.first.lastBlock == blocks - 1 }
    }

    fun twin() = Twin(sampleRate, fadeLen, n)

    // ── A change ──────────────────────────────────────────────────────────────────────────────────

    /** Installs x1.0, swaps to x0.0, then renders [blocks] blocks of DC and concatenates the left channel. */
    fun rampAfterSwap(blocks: Int): DoubleArray {
        val swap = KatalystFilterSwap(sampleRate)
        swap.set(Gain(1.0), Gain(1.0))
        swap.process(dcBlock(), n)
        swap.set(Gain(0.0), Gain(0.0))

        val out = DoubleArray(blocks * n)

        for (b in 0 until blocks) {
            val mix = dcBlock()
            swap.process(mix, n)

            for (i in 0 until n) {
                out[b * n + i] = mix.left[i]
            }
        }

        return out
    }

    "a change ramps exactly linearly from the pair in service to the new one, across block boundaries" {
        val out = rampAfterSwap(blocks = fadeBlocks)

        // out[k] = new + (1 - t) * (old - new) with old = 1, new = 0, t = min(k / fadeLen, 1). Every
        // block is sampled: the fade position accumulates ACROSS process() calls.
        for (k in 0 until fadeBlocks * n) {
            val t = minOf(k.toDouble() / fadeLen, 1.0)

            withClue("sample $k") { out[k] shouldBe (1.0 - t).plusOrMinus(1e-12) }
        }
    }

    "the fade lands on sample 882 at 44.1 kHz and 960 at 48 kHz: 20 ms, and NOT the knob glide" {
        // Katalyst 5c-11: the bank swap got its own constant, 20 ms, while KNOB_GLIDE_SECONDS
        // stays 50 ms for every level and dynamics glide. The LANDING SAMPLE is written out as a
        // literal on purpose: a row that recomputed its expectation from the constant it guards
        // would stay green for any value, and 20 ms is a decision, not an implementation detail.
        // The inequality below then says the swap is not simply reading the other constant.
        withClue("the two constants are apart") {
            (BANK_CROSSFADE_SECONDS == KNOB_GLIDE_SECONDS) shouldBe false
        }

        /** Installs x1.0, swaps to x0.0 at [rate], and returns the landing sample of the ramp. */
        fun landingAt(rate: Double): Int {
            val swap = KatalystFilterSwap(rate)

            swap.set(Gain(1.0), Gain(1.0))
            swap.process(dcBlock(), n)
            swap.set(Gain(0.0), Gain(0.0))

            var landed = -1
            var k = 0

            while (landed < 0 && k < 200 * n) {
                val mix = dcBlock()

                swap.process(mix, n)

                for (i in 0 until n) {
                    if (landed < 0 && mix.left[i] == 0.0) {
                        landed = k + i
                    }
                }

                k += n
            }

            return landed
        }

        withClue("44.1 kHz") { landingAt(44100.0) shouldBe 882 }
        withClue("48 kHz") { landingAt(48000.0) shouldBe 960 }

        // 881, not `fadeLen - 1`: this row may not take its expectation from the constant.
        val out = rampAfterSwap(blocks = fadeBlocks)

        withClue("the sample before the landing still carries the old pair") {
            (out[881] > 0.0) shouldBe true
        }
    }

    "the fade starts at the OLD pair (the swap sample is continuous) and ends on the new one alone" {
        val out = rampAfterSwap(blocks = fadeBlocks)

        out[0] shouldBe 1.0.plusOrMinus(1e-12)

        for (k in fadeLen until fadeBlocks * n) {
            withClue("sample $k") { out[k] shouldBe 0.0 }
        }
    }

    "with no pair installed the mix passes through untouched" {
        val swap = KatalystFilterSwap(sampleRate)
        val mix = dcBlock()

        swap.process(mix, n)

        swap.active shouldBe false
        swap.sounding shouldBe false
        (0 until n).all { mix.left[it] == 1.0 && mix.right[it] == 1.0 } shouldBe true
    }

    // ── Off and on ────────────────────────────────────────────────────────────────────────────────

    "clear() fades the pair to dry, lands on exactly dry, and only then lets go of it" {
        // Question 3, the Off precondition: Off is entered only once the output IS the dry input.
        val t = twin()
        val a = t.pair(0.25)

        t.set(a)
        t.block("installed")
        t.clear()

        withClue("the intent flips at once, the sound does not") {
            t.swap.active shouldBe false
            t.swap.sounding shouldBe true
        }

        repeat(fadeBlocks) { t.block("fading out") }

        withClue("landed: Off, and the pair is released") {
            t.swap.sounding shouldBe false
            t.swap.holds(t.gains[a].first) shouldBe false
            t.swap.holds(t.gains[a].second) shouldBe false
        }

        // And dry from here on, bit for bit.
        val mix = dcBlock()
        t.swap.process(mix, n)
        (0 until n).all { mix.left[it].toRawBits() == 1.0.toRawBits() } shouldBe true
    }

    "switching ON after the first block fades in from dry" {
        val t = twin()
        val a = t.pair(0.0)

        t.block("nothing installed, so the snap is spent")
        t.set(a)

        withClue("the intent flips at once") { t.swap.active shouldBe true }

        repeat(fadeBlocks) { t.block("fading in") }
    }

    "the first initialisation is instant: set and clear act at once until a block has run, and again after reset" {
        fun firstBlockIs(swap: KatalystFilterSwap, value: Double, label: String) {
            val mix = dcBlock()
            swap.process(mix, n)

            withClue(label) { (0 until n).all { mix.left[it] == value && mix.right[it] == value } shouldBe true }
        }

        val fresh = KatalystFilterSwap(sampleRate)
        fresh.set(Gain(0.5), Gain(0.5))
        firstBlockIs(fresh, 0.5, "a fresh set is alone from its first sample")

        val twice = KatalystFilterSwap(sampleRate)
        twice.set(Gain(0.5), Gain(0.5))
        twice.set(Gain(0.25), Gain(0.25))
        firstBlockIs(twice, 0.25, "two sets before the first block: the second replaces the first")

        val cleared = KatalystFilterSwap(sampleRate)
        cleared.set(Gain(0.5), Gain(0.5))
        cleared.clear()
        cleared.sounding shouldBe false
        firstBlockIs(cleared, 1.0, "a clear before the first block is dry at once")

        val relive = KatalystFilterSwap(sampleRate)
        relive.set(Gain(0.5), Gain(0.5))
        relive.process(dcBlock(), n)
        relive.reset()
        relive.set(Gain(0.25), Gain(0.25))
        firstBlockIs(relive, 0.25, "after reset the next set snaps again")
    }

    // ── What sounds now ───────────────────────────────────────────────────────────────────────────

    "a change mid-fade is REFUSED: the fade in flight runs on, and only two banks ever sound" {
        // The 10-bank pool of 5c-6 let A, B and C smear under D. Since 5c-11 there are two banks
        // and no more: a change that arrives while a fade runs changes NOTHING here, and the host
        // parks its config instead (`KatalystBodyEffectSpec`, the parking rows). The law refuses
        // it too, so the twin's per-sample comparison is what pins "nothing changed".
        val t = twin()
        val a = t.pair(1.0)
        val b = t.pair(2.0)
        val c = t.pair(4.0)
        val d = t.pair(8.0)

        t.set(a)
        t.block("A alone")
        t.set(b)
        repeat(3) { t.block("A to B") }
        t.set(c)
        t.set(d)

        withClue("A and B sound, C and D were refused and are held by nothing") {
            t.swap.holds(t.gains[a].first) shouldBe true
            t.swap.holds(t.gains[b].first) shouldBe true
            t.swap.holds(t.gains[c].first) shouldBe false
            t.swap.holds(t.gains[d].first) shouldBe false
        }

        repeat(fadeBlocks) { t.block("A to B, undisturbed") }

        withClue("B alone at the end, and the refused pairs never ran") {
            t.swap.holds(t.gains[b].first) shouldBe true
            t.swap.holds(t.gains[a].first) shouldBe false
            t.gains[c].first.lastBlock shouldBe -1
            t.gains[d].first.lastBlock shouldBe -1
        }
    }

    "at most two banks run on a block, whatever the rate of changes" {
        // A change on EVERY block, forty of them: the pool that could grow is gone, so the count
        // of banks that actually processed the block is the invariant.
        val t = twin()
        val ids = (0 until 40).map { t.pair(1.0 + it) }

        t.set(ids[0])
        t.block("first")

        for (i in 1 until ids.size) {
            t.set(ids[i])
            t.block("change $i")

            withClue("block ${t.blocks - 1}: banks that ran") {
                t.ranLastBlock() shouldBeLessThanOrEqual 2
            }
        }
    }

    "a return mid-fade-out turns around where it stands, and a second clear is idempotent" {
        // The owner comes back to the SAME pair while it fades out: no new bank, no step. The law
        // puts the dry entry at its current weight and lets it ramp back out.
        val t = twin()
        val a = t.pair(0.25)

        t.set(a)
        t.block("installed")
        t.clear()
        repeat(4) { t.block("fading out") }
        t.clear()
        t.block("a second clear changes nothing")
        t.resume(a) shouldBe true

        withClue("the intent is back at once") { t.swap.active shouldBe true }

        repeat(fadeBlocks) { t.block("turned around") }

        // Out again, all the way: after landing the pair is not there to be taken back.
        t.clear()
        repeat(fadeBlocks) { t.block("fading out for good") }
        t.resume(a) shouldBe false
        t.swap.sounding shouldBe false
    }

    "a clear mid-change is refused too, and an on during a fade-out still needs the fade to land" {
        // `clear` is a change like any other: while an entry fades it cannot start, because dry
        // would be a third thing sounding. The host parks the off (`KatalystBodyEffectSpec`).
        val t = twin()
        val a = t.pair(1.0)
        val b = t.pair(0.5)
        val c = t.pair(0.25)

        t.set(a)
        t.block("A")
        t.set(b)
        repeat(2) { t.block("A to B") }
        t.clear()

        withClue("refused: B is still the target, so the intent this class knows is still on") {
            t.swap.active shouldBe true
        }

        repeat(fadeBlocks) { t.block("A to B, undisturbed") }

        t.clear()
        repeat(3) { t.block("B to dry") }
        t.set(c)

        withClue("refused: C waits for the fade-out to land") {
            t.swap.holds(t.gains[c].first) shouldBe false
        }

        repeat(fadeBlocks) { t.block("B to dry, undisturbed") }

        withClue("landed on dry") { t.swap.sounding shouldBe false }

        t.set(c)
        repeat(fadeBlocks) { t.block("dry to C") }
    }

    "reset() is a hard cut from every state: dry at once, nothing held, and the next set snaps" {
        // The cylinder's deactivation and retire: a fade that survived would resume on the orbit's
        // next life, on new material.
        // Two runs per case, because the first block after the cut spends the snap (in the engine
        // no block runs between an orbit's deactivation and its next life).
        fun afterReset(prepare: (Twin) -> Unit, label: String) {
            val t = twin()
            prepare(t)
            t.reset()

            withClue("$label: nothing held, nothing sounding") {
                t.gains.none { t.swap.holds(it.first) || t.swap.holds(it.second) } shouldBe true
                t.swap.sounding shouldBe false
                t.swap.active shouldBe false
            }

            val dry = dcBlock()
            t.swap.process(dry, n)

            withClue("$label: dry at once") { (0 until n).all { dry.left[it] == 1.0 && dry.right[it] == 1.0 } shouldBe true }

            val u = twin()
            prepare(u)
            u.reset()
            u.swap.set(Gain(0.125), Gain(0.125))
            val next = dcBlock()
            u.swap.process(next, n)

            withClue("$label: the next life starts at once, no stale fade") {
                (0 until n).all { next.left[it] == 0.125 && next.right[it] == 0.125 } shouldBe true
            }
        }

        afterReset({ t -> t.set(t.pair(0.5)); t.block("engaged") }, "from Engaged")
        afterReset({ t -> t.set(t.pair(0.5)); t.block("engaged"); t.clear(); repeat(3) { t.block("out") } }, "mid fade-out")
        afterReset({ t -> t.set(t.pair(0.5)); t.block("a"); t.set(t.pair(0.25)); t.block("b"); t.set(t.pair(2.0)) }, "mid change")
        afterReset(
            { t ->
                t.set(t.pair(0.0))
                repeat(12) { t.block("x"); t.set(t.pair(1.0 + it)) }
            },
            "with changes refused mid-fade",
        )
    }

    "a landed fade and a refused change leave no reference behind" {
        // Question 4, the REFERENCES: the outgoing entry dies with its own landing, the pair in
        // service with Off, and a refused change was never referenced at all.
        val t = twin()
        val a = t.pair(1.0)
        val b = t.pair(0.5)

        t.set(a)
        t.block("a")
        t.set(b)
        t.block("a to b")

        withClue("mid-fade the outgoing pair is held") { t.swap.holds(t.gains[a].first) shouldBe true }

        val refused = (0 until 12).map { t.pair(2.0 + it) }

        for (id in refused) {
            t.set(id)
        }

        withClue("a refused change is held by nothing, whichever of them it was") {
            refused.none { t.swap.holds(t.gains[it].first) || t.swap.holds(t.gains[it].second) } shouldBe true
        }

        repeat(fadeBlocks) { t.block("to b") }

        withClue("after its landing the outgoing pair is dropped, the pair in service kept") {
            t.swap.holds(t.gains[a].first) shouldBe false
            t.swap.holds(t.gains[a].second) shouldBe false
            t.swap.holds(t.gains[b].first) shouldBe true
            t.swap.holds(t.gains[b].second) shouldBe true
        }

        t.reset()

        withClue("reset drops the pair in service as well") {
            t.swap.holds(t.gains[b].first) shouldBe false
            t.swap.holds(t.gains[b].second) shouldBe false
        }
    }

    "a pair's own state runs on across every transition: in service, outgoing, and alone again" {
        // Question 1, what outlives the states: the pairs. The swap never builds, resets or re-runs
        // one; it only moves a reference. A clock shows it: each pair must see every sample of its
        // life exactly once, whatever state the swap is in.
        class Clock(start: Int) : AudioFilter {
            private var count = start

            override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
                for (i in offset until offset + length) {
                    buffer[i] = count.toDouble()
                    count++
                }
            }
        }

        val swap = KatalystFilterSwap(sampleRate)

        // Distinct right-channel starts, so a right pair run twice, skipped or wired to the left shows.
        val aRight = 5000
        val bRight = 7000

        swap.set(Clock(0), Clock(aRight))
        swap.process(dcBlock(), n)
        swap.set(Clock(0), Clock(bRight))

        // A was in service for one block, so it enters the fade at 128; B starts at its own start.
        for (block in 0 until fadeBlocks) {
            val mix = dcBlock()
            swap.process(mix, n)

            for (i in 0 until n) {
                val kk = block * n + i
                val w = maxOf(0, fadeLen - kk).toDouble() / fadeLen
                val left = kk + w * ((n + kk) - kk)
                val right = (bRight + kk) + w * ((aRight + n + kk) - (bRight + kk))

                withClue("A to B, left sample $kk") { mix.left[i] shouldBe left.plusOrMinus(1e-9) }
                withClue("A to B, right sample $kk") { mix.right[i] shouldBe right.plusOrMinus(1e-9) }
            }
        }
    }
})
