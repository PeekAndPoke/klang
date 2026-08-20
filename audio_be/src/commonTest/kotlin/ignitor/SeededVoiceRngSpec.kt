/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.random.Random

/**
 * Seeded-voice-rng (D3c): every voice derives ONE `Random` from the playback's `coreRandom`
 * and shares it across its whole sub-graph — build-time consumers capture it via
 * `IgnitorBuildCache.random` (noise, super-oscillator jitter, pluck excitation), generate-time
 * constructions read it from `IgniteContext.random` (analog drift). This makes draw ORDER
 * between voices/nodes irrelevant and identical runs bit-REPRODUCIBLE — the property this
 * spec pins.
 *
 * The reproducibility row is the single pin for the WHOLE threading: its DSL contains BOTH a
 * ctx-channel consumer (drifting saw) and a cache-channel consumer (white noise), so a
 * fallback to the global Random on EITHER channel diverges the two same-seeded renders
 * (the global is a shared stream — whoever draws first steals the other's numbers).
 */
class SeededVoiceRngSpec : StringSpec({

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

    // Every RNG consumer family live in one voice graph: WaveIgnitor drift (inline ctx
    // site), SineIgnitor drift (the initAnalogDrift ctx site — a DIFFERENT construction
    // path), and noise (the cache channel). A severed channel ANYWHERE diverges the two
    // same-seeded renders.
    fun wildDsl(): IgnitorDsl = IgnitorDsl.Plus(
        IgnitorDsl.Plus(
            IgnitorDsl.Plus(
                IgnitorDsl.Sawtooth(analog = IgnitorDsl.Constant(0.7)),
                IgnitorDsl.Sine(analog = IgnitorDsl.Constant(0.5)),
            ),
            IgnitorDsl.Pluck(),
        ),
        IgnitorDsl.Times(IgnitorDsl.WhiteNoise(), IgnitorDsl.Constant(0.2)),
    )

    fun render(seed: Int): List<AudioBuffer> {
        // ONE instance per voice, shared by BOTH channels — exactly the production wiring
        // (VoiceFactory passes the same voiceRandom to createExciter and IgniteContext).
        val voiceRandom = Random(seed)
        val exciter = wildDsl().toExciter(random = voiceRandom)
        val c = ctx(voiceRandom)
        val out = ArrayList<AudioBuffer>(blocks)
        repeat(blocks) {
            val buf = AudioBuffer(blockFrames)
            exciter.generate(buf, 220.0, c)
            out.add(buf)
            c.voiceElapsedFrames += blockFrames
        }
        return out
    }

    "same-seeded voices render bit-identically WITH active drift and noise" {
        // Impossible before D3c: both trees drew from the one global stream in sequence, so
        // the second render could never see the first's numbers. With derived streams the
        // render is a pure function of (DSL, seed, freqHz) — the reproducibility contract.
        val a = render(seed = 7)
        val b = render(seed = 7)
        for (blk in 0 until blocks) {
            for (i in 0 until blockFrames) {
                a[blk][i].toRawBits() shouldBe b[blk][i].toRawBits()
            }
        }
    }

    "different seeds diverge (the seed is live)" {
        val a = render(seed = 7)
        val b = render(seed = 8)
        var differs = false
        outer@ for (blk in 0 until blocks) {
            for (i in 0 until blockFrames) {
                if (a[blk][i].toRawBits() != b[blk][i].toRawBits()) {
                    differs = true
                    break@outer
                }
            }
        }
        differs shouldBe true
    }

    "every noise family + karplus is seed-reproducible" {
        // One row per RNG-consuming source family: two same-seed renders must be bit-equal
        // (build-time draws — perlin/berlin/crackle seed at construction — and generate-time
        // draws both covered). A single family reverted to the global reddens its entry.
        val sources = listOf<Pair<String, () -> IgnitorDsl>>(
            "white" to { IgnitorDsl.WhiteNoise() },
            "brown" to { IgnitorDsl.BrownNoise() },
            "pink" to { IgnitorDsl.PinkNoise() },
            "perlin" to { IgnitorDsl.PerlinNoise() },
            "berlin" to { IgnitorDsl.BerlinNoise() },
            "dust" to { IgnitorDsl.Dust() },
            "crackle" to { IgnitorDsl.Crackle() },
            "pluck" to { IgnitorDsl.Pluck() },
            "superpluck" to { IgnitorDsl.SuperPluck() },
        )
        for ((name, mk) in sources) {
            fun renderOne(seed: Int): AudioBuffer {
                val r = Random(seed)
                val exciter = mk().toExciter(random = r)
                val buf = AudioBuffer(blockFrames)
                exciter.generate(buf, 220.0, ctx(r))
                return buf
            }
            val a = renderOne(5)
            val b = renderOne(5)
            for (i in 0 until blockFrames) {
                withClue("source=$name i=$i") {
                    a[i].toRawBits() shouldBe b[i].toRawBits()
                }
            }
        }
    }

    "supersaw unison jitter is seed-reproducible" {
        // The super-oscillator family draws phases/gain jitter at first generate from the
        // build-captured rng — same seed, same unison spread, bit-for-bit.
        fun renderSuper(seed: Int): AudioBuffer {
            val r = Random(seed)
            val exciter = IgnitorDsl.SuperSaw(
                voices = IgnitorDsl.Constant(7.0),
                analog = IgnitorDsl.Constant(1.0), // engages the per-unison-voice drift site
            ).toExciter(random = r)
            val buf = AudioBuffer(blockFrames)
            exciter.generate(buf, 220.0, ctx(r))
            return buf
        }
        val a = renderSuper(3)
        val b = renderSuper(3)
        for (i in 0 until blockFrames) {
            a[i].toRawBits() shouldBe b[i].toRawBits()
        }
    }
})
