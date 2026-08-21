/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.audio_bridge.onePoleLowpass
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.optimizer
import io.peekandpoke.klang.audio_bridge.tap
import kotlin.random.Random

/**
 * THE promise of the optimizer, at the only level that can prove it: a tree and its optimized
 * twin must render the SAME BITS through the real runtime.
 *
 * `IgnitorDslOptimizerSpec` (audio_bridge) pins the tree shapes; this pins that the rewrite is
 * inaudible. A rule that fuses the wrong thing shows up here even when the shape looks right.
 *
 * Both trees are rendered with their OWN same-seeded `Random`, which D3c made possible
 * (`toExciter(random = ...)` + `IgniteContext(random = ...)`). That matters: dossier item 10's
 * "cross-tree parity must be RNG-inert" applied to the shared global stream and is RELAXED for
 * same-seeded twins. Without it this corpus could contain no drift, no noise and no unison, and
 * would therefore have zero coverage of the exact bug class this workstream already shipped once
 * (a rewrite that changes the NUMBER or ORDER of draws). The filter `analog` gate does not help
 * here: the optimizer happily fuses filters sitting on top of noise and drift-bearing SOURCES.
 */
class IgnitorDslOptimizerRenderSpec : StringSpec({

    val blockFrames = 128
    val blocks = 4
    val sr = 44100

    fun ctx(random: Random): IgniteContext = IgniteContext(
        sampleRate = sr,
        voiceDurationFrames = blockFrames * 16,
        gateEndFrame = blockFrames * 16,
        releaseFrames = 0,
        voiceEndFrame = blockFrames * 16,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = random,
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    /** Production mid-block onset: the voice clock starts NEGATIVE by the start offset. */
    fun onsetCtx(random: Random, offset: Int, length: Int): IgniteContext =
        ctx(random).apply {
            this.offset = offset
            this.length = length
            voiceElapsedFrames = -offset
        }

    val seed = 0x5EED

    /**
     * Renders the authored tree and its optimized twin with same-seeded RNG, asserting raw-bit
     * equality inside the window and that nothing was written OUTSIDE it.
     *
     * [expectFused] is the liveness half: without it every row here would pass if `optimize()`
     * were the identity function, and `register()` degrades to exactly that on a throw.
     */
    fun assertOptimizeIsInaudible(
        authored: IgnitorDsl,
        freqs: List<Double> = listOf(220.0, 440.0),
        offset: Int = 0,
        length: Int = blockFrames,
        expectFused: Boolean = true,
    ) {
        val optimized = authored.optimize()

        withClue("optimizer must actually have rewritten this tree") {
            (optimized !== authored) shouldBe expectFused
        }

        for (f in freqs) {
            // ONE stream per tree, shared between build-time and generate-time consumers —
            // the production wiring (a voice deals a single Random and hands the same
            // instance to toExciter and IgniteContext). Two separate streams would hide any
            // future rule that shifts a draw across the build/generate boundary.
            val rngA = Random(seed)
            val rngB = Random(seed)
            val a = authored.toExciter(null, random = rngA)
            val b = optimized.toExciter(null, random = rngB)
            val sentinel = -12345.0
            val bufA = AudioBuffer(blockFrames)
            val bufB = AudioBuffer(blockFrames)
            val ca = onsetCtx(rngA, offset, length)
            val cb = onsetCtx(rngB, offset, length)

            repeat(blocks) { block ->
                // Production shape: only the FIRST block is partial (the voice starts
                // mid-block); every later block is a full window. What the transition buys:
                // EqCore runs over a partial AND a full window on one core, captureInput is
                // reused at a second length, and the out-of-window sentinel below stops being
                // vacuous (at offset 0 it checks nothing). It does NOT exercise
                // MemoizingIgnitor's offset/length cache key — every node in these rows has a
                // single consumer, so that path short-circuits before the key is read — and it
                // does not RE-grow the input copy, since the quantum round-up already
                // allocated 128 for the 64-frame window (EqCoreSpec pins re-growth directly).
                val curOffset = if (block == 0) offset else 0
                val curLength = if (block == 0) length else blockFrames
                ca.offset = curOffset; ca.length = curLength
                cb.offset = curOffset; cb.length = curLength

                for (i in 0 until blockFrames) {
                    bufA[i] = sentinel
                    bufB[i] = sentinel
                }
                a.generate(bufA, f, ca)
                b.generate(bufB, f, cb)

                for (i in curOffset until curOffset + curLength) {
                    // NaN compares as NaN: payload bits are outside the contract (EqCoreSpec).
                    if (!(bufA[i].isNaN() && bufB[i].isNaN())) {
                        withClue("freq=$f sample=$i") {
                            bufA[i].toRawBits() shouldBe bufB[i].toRawBits()
                        }
                    }
                }
                // The fused path renders upstream into the CALLER's buffer where the chained
                // path used scratch, so an out-of-window write only becomes visible after
                // fusion — and comparing inside the window alone cannot see it.
                for (i in 0 until blockFrames) {
                    if (i < curOffset || i >= curOffset + curLength) {
                        withClue("freq=$f wrote outside the window at $i") {
                            bufB[i] shouldBe sentinel
                        }
                    }
                }

                ca.voiceElapsedFrames += blockFrames
                cb.voiceElapsedFrames += blockFrames
            }
        }
    }

    "the guitar tail: four chained filters fuse inaudibly" {
        // The shape D4 exists for. Every one of these is a node today and one Eq afterwards.
        assertOptimizeIsInaudible(
            IgnitorDsl.Sawtooth()
                .notch(210.0, 2.5)
                .highpass(440.0, 0.707)
                .lowpass(5300.0, 0.707)
                .lowpass(5300.0, 0.707)
        )
    }

    "Der Schmetterling's full guitar tail fuses to one node, inaudibly" {
        // Der Schmetterling's guitar as it is being migrated: two authored taps plus four
        // chained filters, including the note-tracking highpass. This is the row that says the
        // phone gets the four chained nodes for free and the guitar still sounds like the
        // guitar. (The song file is the maintainer's to commit; the repo copy may still carry
        // the older hand-built parallel form.)
        assertOptimizeIsInaudible(
            IgnitorDsl.Sawtooth()
                .eq()
                .tap(850.0, 0.707, 1.7)
                .tap(2500.0, 0.7, 5.0)
                .notch(210.0, 2.5)
                .let {
                    IgnitorDsl.Highpass(
                        inner = it,
                        cutoffHz = IgnitorDsl.Times(IgnitorDsl.Freq, IgnitorDsl.Constant(1.0)),
                        q = IgnitorDsl.Constant(0.707),
                    )
                }
                .lowpass(5250.0, 0.707)
                .lowpass(5250.0, 0.707),
            freqs = listOf(110.0, 220.0, 440.0),
            // Mid-block onset on the HEADLINE shape. What this actually adds is the
            // out-of-window sentinel, which is vacuous at offset 0. It does NOT pin EqCore's
            // relative-indexed tap capture: both trees run the same core with the same
            // offset, so an absolute-vs-relative regression is common-mode and the parity
            // oracle stays green. That guard lives in EqCoreSpec, against an independent
            // chain oracle.
            offset = 37,
            length = 64,
        )
    }

    "fusion keeps the RNG draw ORDER (two consumers on one seeded stream)" {
        // TWO consumers is the whole point. A source with drift plus an expression-backed
        // cutoff whose LFO ALSO carries drift means the saw's draws must land before the
        // LFO's on the shared stream; swap the order and the saw gets the LFO's numbers.
        // With a single consumer (or with Constant-only params) a reordering rewrite is
        // invisible, which is exactly how the D3 params-before-upstream bug shipped.
        fun lfoCutoff() = IgnitorDsl.Plus(
            IgnitorDsl.Constant(2000.0),
            IgnitorDsl.Times(
                IgnitorDsl.Sine(freq = IgnitorDsl.Constant(2.0), analog = IgnitorDsl.Constant(0.3)),
                IgnitorDsl.Constant(500.0),
            ),
        )

        assertOptimizeIsInaudible(
            IgnitorDsl.Lowpass(
                inner = IgnitorDsl.Sawtooth(analog = IgnitorDsl.Constant(0.7)),
                cutoffHz = lfoCutoff(),
                q = IgnitorDsl.Constant(0.9),
            ).notch(210.0, 2.5)
        )
    }

    "the seeded-twin harness is load-bearing (different seeds DO diverge)" {
        // Guards the guard: if the RNG were not actually reaching these trees, the two rows
        // above would pass for the trivial reason that nothing random happens, and a real
        // draw-order regression would sail through. Different seeds must produce different
        // audio, or those rows prove nothing.
        val dsl = IgnitorDsl.Sawtooth(analog = IgnitorDsl.Constant(0.7))
            .notch(210.0, 2.5)
            .lowpass(5300.0, 0.707)

        fun render(seed: Int): AudioBuffer {
            val rng = Random(seed)
            val e = dsl.toExciter(null, random = rng)
            val buf = AudioBuffer(blockFrames)
            val c = ctx(rng)
            repeat(blocks) {
                e.generate(buf, 220.0, c)
                c.voiceElapsedFrames += blockFrames
            }
            return buf
        }

        val a = render(1)
        val b = render(2)
        var differs = false
        for (i in 0 until blockFrames) {
            if (a[i].toRawBits() != b[i].toRawBits()) {
                differs = true
                break
            }
        }
        differs shouldBe true
    }

    "fusion keeps the per-sample draw COUNT (white noise through a fused chain)" {
        // Order is pinned twice over; COUNT is not. White noise draws once per rendered
        // sample from the shared stream, so a fused path that rendered a different number of
        // upstream samples would desynchronise it even with the order intact.
        assertOptimizeIsInaudible(
            IgnitorDsl.WhiteNoise().highpass(300.0, 0.8).lowpass(6000.0, 0.707)
        )
    }

    "fusion keeps the CONSTRUCTION-time draw order (two Perlin consumers)" {
        // The row above covers draws made while RENDERING. A few generators instead draw when
        // they are CONSTRUCTED — Perlin/Berlin seed their start position and Crackle its state
        // in property initialisers — so for those the BUILD order is what must be preserved.
        // Two of them, one as the source and one inside a cutoff expression, make that order
        // observable; with a single one it would not be. (White noise does NOT work here: it
        // captures the RNG but draws per sample, so its construction order is unobservable.)
        assertOptimizeIsInaudible(
            IgnitorDsl.Lowpass(
                inner = IgnitorDsl.PerlinNoise(),
                cutoffHz = IgnitorDsl.Plus(
                    IgnitorDsl.Constant(4000.0),
                    IgnitorDsl.Times(IgnitorDsl.PerlinNoise(), IgnitorDsl.Constant(200.0)),
                ),
                q = IgnitorDsl.Constant(0.8),
            ).highpass(300.0, 0.8)
        )
    }

    "a single filter converting to a one-section Eq is inaudible" {
        assertOptimizeIsInaudible(IgnitorDsl.Sine().lowpass(2000.0, 0.707))
    }

    "every fusible filter type survives the rewrite" {
        assertOptimizeIsInaudible(
            IgnitorDsl.Sawtooth()
                .lowpass(4000.0, 0.8)
                .highpass(200.0, 0.9)
                .bandpass(1200.0, 1.1)
                .notch(600.0, 1.3)
        )
    }

    "a chain split by a nonlinear node is inaudible on BOTH sides" {
        assertOptimizeIsInaudible(
            IgnitorDsl.Sine().bandpass(1000.0, 0.8).distort(0.5).lowpass(4000.0, 0.707)
        )
    }

    "the optimizer may nest an Eq UNDER an authored tap, and the tap still reads its own input" {
        // `.lowpass(...).eq().tap(...)` wraps a non-Eq, so the rewrite yields Eq(Eq(saw,[LP]),
        // [tap]) — a nesting the authored tree never had. The tap must keep reading the INNER
        // Eq's output. This is the live counterpart of followups section 2's merge trap, and
        // the tripwire for the day that merge is implemented.
        assertOptimizeIsInaudible(
            IgnitorDsl.Sawtooth().lowpass(1000.0, 0.707).eq().tap(850.0, 0.707, 1.7)
        )
    }

    "an unfusable FILTER is a wall too (warmth, and an analog filter)" {
        // The nearest miss: `asFusibleSection` must decline these while its neighbours fuse.
        // `.warmth()` especially — it is a one-liner people reach for constantly.
        assertOptimizeIsInaudible(
            IgnitorDsl.Sawtooth().lowpass(2000.0, 0.707).onePoleLowpass(800.0).lowpass(4000.0, 0.707)
        )
        assertOptimizeIsInaudible(
            IgnitorDsl.Lowpass(
                inner = IgnitorDsl.Sawtooth().lowpass(2000.0, 0.707),
                cutoffHz = IgnitorDsl.Constant(3000.0),
                q = IgnitorDsl.Constant(0.707),
                analog = IgnitorDsl.Constant(2.0),
            ).lowpass(4000.0, 0.707)
        )
    }

    "a chain split by a gain multiply is inaudible" {
        assertOptimizeIsInaudible(
            IgnitorDsl.Sine()
                .lowpass(2000.0, 0.707)
                .mul(IgnitorDsl.Constant(0.5))
                .lowpass(3000.0, 0.707)
        )
    }

    "a filter appended onto an AUTHORED Eq is inaudible" {
        // Mixed provenance: sections the user wrote plus a section the optimizer folded in.
        assertOptimizeIsInaudible(
            IgnitorDsl.Sawtooth().eq().band(1200.0, 0.9, 6.0).tap(850.0, 0.707, 1.7)
                .lowpass(5300.0, 0.707)
                .notch(210.0, 2.5)
        )
    }

    "a shared intermediate renders identically and is not forked" {
        val shared = IgnitorDsl.Sawtooth().notch(210.0, 2.5)
        assertOptimizeIsInaudible(
            IgnitorDsl.Plus(shared.lowpass(3000.0, 0.707), shared.lowpass(6000.0, 0.707))
        )
    }

    "a tracking highpass (Freq-backed cutoff) fuses inaudibly across voice frequencies" {
        // The song's shape: cutoff follows the note, so the section must resolve per block from
        // the voice frequency exactly as the chained node did.
        assertOptimizeIsInaudible(
            IgnitorDsl.Highpass(
                inner = IgnitorDsl.Sawtooth(),
                cutoffHz = IgnitorDsl.Times(IgnitorDsl.Freq, IgnitorDsl.Constant(1.5)),
                q = IgnitorDsl.Constant(0.707),
            ).lowpass(5300.0, 0.707),
            freqs = listOf(110.0, 220.0, 440.0, 880.0),
        )
    }

    "the production sub-block onset shape is inaudible (offset != 0, partial length)" {
        // Voices start mid-block; the partial window must be the FIRST call on fresh state.
        assertOptimizeIsInaudible(
            IgnitorDsl.Sawtooth().notch(210.0, 2.5).lowpass(5300.0, 0.707),
            freqs = listOf(220.0),
            offset = 37,
            length = 64,
        )
    }

    "optimizer(0) renders identically to the unoptimized tree" {
        // The A/B hatch: with the marker present the tree is untouched, so this is trivially
        // true — which is the point. It pins that the marker really does reach the optimizer.
        // Compare the MARKED tree against the bare one: the property that matters for the A/B
        // is that the hint itself is render-transparent (buildIgnitor dissolves it), not the
        // tautology that an untouched tree equals itself.
        // Wrapped in a vibrato on purpose: the hint dissolves in buildIgnitor's prologue and
        // must pass `accumulatedMod` down to the source. Dropping it there would silently
        // un-modulate any sound carrying the kill switch — the one place a difference must
        // never appear, since it would look like the fusion A/B rather than the hatch.
        val bare = IgnitorDsl.Vibrato(
            inner = IgnitorDsl.Sawtooth().notch(210.0, 2.5).lowpass(5300.0, 0.707),
            rate = IgnitorDsl.Constant(5.0),
            depth = IgnitorDsl.Constant(0.3),
        )
        val marked = IgnitorDsl.Vibrato(
            inner = IgnitorDsl.Sawtooth().notch(210.0, 2.5).lowpass(5300.0, 0.707).optimizer(on = 0),
            rate = IgnitorDsl.Constant(5.0),
            depth = IgnitorDsl.Constant(0.3),
        )

        // The marked tree still fuses NOTHING (the hint disables the whole definition).
        (marked.optimize() === marked) shouldBe true

        val rngA = Random(seed)
        val rngB = Random(seed)
        val a = bare.toExciter(null, random = rngA)
        val b = marked.toExciter(null, random = rngB)
        val bufA = AudioBuffer(blockFrames)
        val bufB = AudioBuffer(blockFrames)
        val ca = ctx(rngA)
        val cb = ctx(rngB)
        repeat(blocks) {
            a.generate(bufA, 220.0, ca)
            b.generate(bufB, 220.0, cb)
            for (i in 0 until blockFrames) {
                bufA[i].toRawBits() shouldBe bufB[i].toRawBits()
            }
            ca.voiceElapsedFrames += blockFrames
            cb.voiceElapsedFrames += blockFrames
        }
    }

    "an unfusable analog filter renders identically (it is left alone)" {
        assertOptimizeIsInaudible(
            IgnitorDsl.Lowpass(
                inner = IgnitorDsl.Sawtooth(),
                cutoffHz = IgnitorDsl.Constant(3000.0),
                q = IgnitorDsl.Constant(0.707),
                analog = IgnitorDsl.Constant(2.0),
            ),
            expectFused = false,
        )
    }
})
