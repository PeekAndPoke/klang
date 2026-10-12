/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.ignitor.PERLIN_STEP
import io.peekandpoke.klang.audio_be.ignitor.plusLaw
import io.peekandpoke.klang.audio_be.master.MasterBus
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.klangMapOf
import io.peekandpoke.klang.common.math.BerlinNoise
import io.peekandpoke.klang.common.math.PerlinNoise
import kotlin.math.floor
import kotlin.math.round
import kotlin.random.Random

/**
 * Knob expressions step 5b (`docs/tasks/in-progress/katalyst-knob-expressions.md`): the per-block path of a modulated
 * Katalyst knob through a REAL chain. The hook (a modulated knob ticks at the top of `KatalystChain.process`, owner or
 * not), its reach (an orbit with no owner, the master, a `parallel` branch, the leaving chain of a swap through its
 * fade and its drain), the clock rules (an unset knob pauses, a resolve never advances) and the span (D2: a modulated
 * knob glides over ONE block, a slot-only knob on the same stage keeps its 50 ms).
 *
 * The oracle of every value is a hand law at the EIGHTH clock (`_katalyst_modulator_hand_laws.kt`: 46.875 Hz at 48 kHz,
 * an eighth of a cycle per block), or for the noises the noise generator seeded by hand at the knob's position and
 * walked by hand; never a second run of the evaluator. A gain stage on a bus of ones writes its factor into every
 * sample, from the block's END, so a block's last sample IS the knob's value for that block.
 */
class KatalystModulatedChainSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    fun c(v: Double) = KatalystKnobDsl.Constant(v)

    fun eighth(phase: Double = 0.0) = KatalystKnobDsl.Sine(freq = c(EIGHTH_HZ), phase = c(phase))

    /** The sine at the eighth clock, block [k], plus [phase]: the hand law. */
    fun sineAt(k: Int, phase: Double = 0.0): Double = handSine((k % 8) * 0.125 + phase)

    fun build(dsl: KatalystDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    )

    /**
     * [blocks] blocks of a bus of [input] through [chain], configured the way an owned orbit is: `applyParams` with
     * [params] of that block, then `process`. Returns each block's left channel.
     */
    fun render(
        chain: KatalystChain,
        blocks: Int,
        input: Double = 1.0,
        params: (Int) -> KlangMap<Double>? = { null },
    ): List<DoubleArray> {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        return List(blocks) { k ->
            ctx.mixBuffer.left.fill(input)
            ctx.mixBuffer.right.fill(input)
            chain.applyParams(params(k))
            chain.process(ctx)
            ctx.mixBuffer.left.copyOf()
        }
    }

    fun gainChain(knob: KatalystKnobDsl) = build(KatalystDsl.of(KatalystStageDsl.Gain(gain = knob)))

    /** Where a ramp from [from] to [end] across one block stands at sample [i]: written from the END. */
    fun ramp(from: Double, end: Double, i: Int): Double = end - (end - from) / blockFrames * (blockFrames - 1 - i)

    // ── Per shape, through a real chain ──────────────────────────────────────────────────────────────────────────

    "each periodic shape on a gain stage: a block's last sample is the hand law at that block, at blocks 0, 1 and k" {
        // A phase offset of 0.3 keeps every reading off the edges, so the hand law is exact.
        val phase = c(0.3)
        val shapes = listOf<Triple<String, KatalystKnobDsl, (Double) -> Double>>(
            Triple("sine", KatalystKnobDsl.Sine(freq = c(EIGHTH_HZ), phase = phase), { p -> handSine(p) }),
            Triple("saw", KatalystKnobDsl.Saw(freq = c(EIGHTH_HZ), phase = phase), { p -> handSaw(p) }),
            Triple("ramp", KatalystKnobDsl.Ramp(freq = c(EIGHTH_HZ), phase = phase), { p -> -handSaw(p) }),
            Triple("tri", KatalystKnobDsl.Tri(freq = c(EIGHTH_HZ), phase = phase), { p -> handTri(p) }),
            Triple(
                "square, duty 0.4",
                KatalystKnobDsl.Square(freq = c(EIGHTH_HZ), duty = c(0.4), phase = phase),
                { p -> handSquare(p, duty = 0.4) },
            ),
        )

        for ((name, knob, hand) in shapes) {
            val out = render(gainChain(knob), blocks = 12)

            fun at(k: Int): Double {
                val p = (k % 8) * 0.125 + 0.3

                return hand(if (p >= 1.0) p - 1.0 else p)
            }

            for (k in intArrayOf(0, 1, 2, 5, 11)) {
                withClue("$name, block $k, last sample") { out[k][blockFrames - 1] shouldBe (at(k) plusOrMinus 1e-9) }
            }

            withClue("$name: block 0 snaps, its first sample is already the value") {
                out[0][0] shouldBe (at(0) plusOrMinus 1e-9)
            }
            withClue("$name: block 1 ramps from block 0's value, its middle on the line between the two") {
                out[1][63] shouldBe (ramp(from = at(0), end = at(1), i = 63) plusOrMinus 1e-9)
            }
        }
    }

    "perlin and berlin on a gain stage: the noise seeded at its position, walked by hand, block by block" {
        val rate = 0.5

        /** The hand walk: the seeded start, plus `rate * PERLIN_STEP * 128` a block, wrapped by the walk's span. */
        fun walk(start: Double, k: Int, span: Double): Double {
            var x = start

            repeat(k) {
                x += rate * PERLIN_STEP * blockFrames

                if (x >= span) {
                    x -= span * floor(x / span)
                }
            }

            return x
        }

        val perlinOut = render(gainChain(KatalystKnobDsl.PerlinNoise(rate = c(rate))), blocks = 9)
        val perlinRng = Random(KATALYST_KNOB_NOISE_SEED)
        val perlin = PerlinNoise(perlinRng)
        val perlinStart = perlinRng.nextDouble() * 256.0

        val berlinOut = render(gainChain(KatalystKnobDsl.BerlinNoise(rate = c(rate))), blocks = 9)
        val berlinRng = Random(KATALYST_KNOB_NOISE_SEED)
        val berlin = BerlinNoise(berlinRng)
        val berlinStart = berlinRng.nextDouble() * 256.0

        for (k in intArrayOf(0, 1, 2, 8)) {
            val p = perlin.fbm(x = walk(start = perlinStart, k = k, span = 256.0), octaves = 1, persistence = 0.5)
            val berlinAt = walk(start = berlinStart, k = k, span = 8388608.0)
            val b = berlin.fbm(t = berlinAt, octaves = 1, persistence = 0.5) * 2.0 - 1.0

            withClue("perlin, block $k") { perlinOut[k][blockFrames - 1] shouldBe (p plusOrMinus 1e-12) }
            withClue("berlin, block $k") { berlinOut[k][blockFrames - 1] shouldBe (b plusOrMinus 1e-12) }
        }

        withClue("the walk moves") { perlinOut[8][blockFrames - 1] shouldNotBe perlinOut[0][blockFrames - 1] }
    }

    "an arithmetic stage over a modulator: the law with the operand ramped across each block to the block's value" {
        val chain = build(KatalystDsl.of(KatalystStageDsl.Plus(other = KatalystKnobDsl.Tri(freq = c(EIGHTH_HZ)))))
        val out = render(chain, blocks = 6, input = 0.25)

        for (k in 0 until 6) {
            withClue("block $k, last sample") {
                out[k][blockFrames - 1] shouldBe (plusLaw(a = 0.25, b = handTri(k * 0.125)) plusOrMinus 1e-12)
            }
        }

        withClue("block 3, sample 40: 0.25 plus the operand ramping from block 2's value to block 3's") {
            val operand = ramp(from = handTri(0.25), end = handTri(0.375), i = 40)

            out[3][40] shouldBe (0.25 + operand plusOrMinus 1e-12)
        }
    }

    // ── The hook's reach ─────────────────────────────────────────────────────────────────────────────────────────

    "an orbit with no owner keeps modulating: the hook is in process, not in applyParams" {
        val rig = CylinderSwapRig(sampleRate = sampleRate)

        rig.registry.register("mod", KatalystDsl.of(KatalystStageDsl.Gain(gain = eighth(phase = 0.3))))
        rig.cylinder.requestChain("mod")
        // Block 0 has an owner, which configures the chain; every block after it has none. A voice that only checks
        // in (past its gate, say) keeps the orbit active without owning it.
        rig.block(level = 0.5)

        val gain = rig.cylinder.pipeline.single().shouldBeInstanceOf<KatalystGainEffect>()
        val seen = mutableListOf(gain.gain)

        repeat(11) {
            rig.cylinder.checkIn(rig.blockStart)
            rig.block(level = 0.5, owned = false)
            seen += gain.gain
        }

        for (k in seen.indices) {
            withClue("block $k, unowned from block 1 on") { seen[k] shouldBe (sineAt(k, phase = 0.3) plusOrMinus 1e-9) }
        }
    }

    "the master modulates: a chain at the output moves its knob every block" {
        val registry = KatalystRegistry()

        registry.register(
            "m",
            KatalystDsl.of(
                KatalystStageDsl.Gain(
                    gain = KatalystKnobDsl.Range(inner = eighth(phase = 0.3), from = c(0.5), to = c(1.0)),
                ),
            ),
        )

        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry)
        val buffer = StereoBuffer(blockFrames)

        bus.requestSwap("m")

        for (k in 0 until 10) {
            buffer.left.fill(0.5)
            buffer.right.fill(0.5)
            bus.process(buffer, blockFrames)
            bus.markRendered()

            val factor = 0.5 + (sineAt(k, phase = 0.3) + 1.0) * 0.5 * 0.5

            withClue("block $k") { buffer.left[blockFrames - 1] shouldBe (0.5 * factor plusOrMinus 1e-9) }
        }
    }

    "a parallel branch modulates, and its clock ticks once a block, not twice" {
        // One branch is the bus through that branch. A parent that ticked its branches' writers as well as the
        // branch chain's own process would read clock 2k at block k.
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Parallel(
                    branches = listOf(KatalystDsl.of(KatalystStageDsl.Gain(gain = eighth(phase = 0.3)))),
                ),
            ),
        )
        val out = render(chain, blocks = 9)

        for (k in 0 until 9) {
            withClue("block $k") { out[k][blockFrames - 1] shouldBe (sineAt(k, phase = 0.3) plusOrMinus 1e-9) }
        }
    }

    "the leaving chain of a swap modulates through its fade and its drain (Q4), unowned" {
        val rig = CylinderSwapRig(sampleRate = sampleRate)

        // A room, so the leaving chain drains, and a modulated fader after it, which the drain's ring-out runs through.
        rig.registry.register(
            "mod",
            KatalystDsl.of(
                KatalystStageDsl.Reverb(wet = c(0.5), size = c(6.0)),
                KatalystStageDsl.Gain(gain = eighth(phase = 0.3)),
            ),
        )
        rig.registry.register("dry", CylinderSwapRig.dryChain(0.9))
        rig.cylinder.requestChain("mod")

        // Five owned blocks of the chain in service: its clock stands at 5.
        rig.render(blocks = 5, level = 0.5)

        rig.cylinder.requestChain("dry")

        val leaving = rig.swap.leaving.shouldNotBeNull()
        val gain = leaving.pipeline[1].shouldBeInstanceOf<KatalystGainEffect>()
        var processed = 5
        var fadeBlocks = 0
        var drainBlocks = 0

        // No owner from here: only the hook moves the leaving chain's knob.
        while (rig.swap.leaving === leaving && fadeBlocks + drainBlocks < 400) {
            val fading = rig.swap.isFading

            rig.cylinder.checkIn(rig.blockStart)
            rig.block(level = 0.5, owned = false)

            if (rig.swap.leaving !== leaving) {
                break
            }

            withClue("block $processed of the leaving chain, ${if (fading) "fading" else "draining"}") {
                gain.gain shouldBe (sineAt(processed, phase = 0.3) plusOrMinus 1e-9)
            }

            processed++

            if (fading) {
                fadeBlocks++
            } else {
                drainBlocks++
            }
        }

        withClue("both phases ran: $fadeBlocks fade blocks, $drainBlocks drain blocks") {
            (fadeBlocks > 10) shouldBe true
            (drainBlocks > 10) shouldBe true
        }
    }

    // ── The clock rules ──────────────────────────────────────────────────────────────────────────────────────────

    "an unset knob is not ticked: its clock pauses while a slot leaf is unset and resumes where it stopped (Q5, Q3)" {
        // The fader is the sine times a depth slot. While the depth is unset (NaN) the whole knob is unset, the
        // writer hands unity, and the clock must not move.
        val chain = gainChain(
            KatalystKnobDsl.Times(left = eighth(phase = 0.3), right = KatalystKnobDsl.Param("depth", 1.0)),
        )
        val set = klangMapOf("depth" to 1.0)
        val unset = klangMapOf("depth" to Double.NaN)
        // Blocks 0 to 2 set, 3 to 6 unset, 7 on set again (a new map instance each time it changes).
        val out = render(chain, blocks = 11) { k -> if (k in 3..6) unset else set }

        for (k in 0..2) {
            withClue("block $k, set") { out[k][blockFrames - 1] shouldBe (sineAt(k, phase = 0.3) plusOrMinus 1e-9) }
        }

        withClue("block 6, unset: the fader glided to unity") { out[6][blockFrames - 1] shouldBe 1.0 }

        for (k in 7..10) {
            // Blocks 0, 1 and 2 moved the clock to 3; the four unset blocks moved nothing.
            withClue("block $k reads clock ${k - 4}") {
                out[k][blockFrames - 1] shouldBe (sineAt(k - 4, phase = 0.3) plusOrMinus 1e-9)
            }
        }
    }

    "a block that resolves a changed map advances the clock once: resolve, then tick, never a double advance" {
        // A new map instance every block: every block resolves (a run at the clock as it stands) and then ticks.
        val chain = gainChain(
            KatalystKnobDsl.Times(left = eighth(phase = 0.3), right = KatalystKnobDsl.Param("depth", 1.0)),
        )
        val out = render(chain, blocks = 10) { k -> klangMapOf("depth" to 1.0, "noise" to k.toDouble()) }

        chain.resolveCount shouldBe 10

        for (k in 0 until 10) {
            withClue("block $k") { out[k][blockFrames - 1] shouldBe (sineAt(k, phase = 0.3) plusOrMinus 1e-9) }
        }
    }

    // ── The span (D2) ────────────────────────────────────────────────────────────────────────────────────────────

    "the span is per knob: a modulated center lands in one block, a katp-moved sweep on the same phaser glides 50 ms" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Phaser(
                    wet = c(0.5),
                    center = KatalystKnobDsl.Range(inner = eighth(phase = 0.3), from = c(400.0), to = c(1600.0)),
                    sweep = KatalystKnobDsl.Param("sw", 400.0),
                ),
            ),
        )
        val phaser = chain.phaser.shouldNotBeNull()
        val glideBlocks = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt()
        val before = klangMapOf("sw" to 400.0)
        val after = klangMapOf("sw" to 800.0)
        // The sweep moves at block 4.
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        for (k in 0 until 4 + glideBlocks + 2) {
            ctx.mixBuffer.left.fill(0.3)
            ctx.mixBuffer.right.fill(0.3)
            chain.applyParams(if (k < 4) before else after)
            chain.process(ctx)

            withClue("block $k: the modulated center is at its block's value") {
                // `fastSin` is within about 1e-11 of the sine; 600 Hz of swing scales that to a few nHz.
                phaser.center shouldBe (400.0 + (sineAt(k, phase = 0.3) + 1.0) * 0.5 * 1200.0 plusOrMinus 1e-7)
            }

            val g = k - 3

            when {
                g <= 0 -> withClue("block $k: the sweep stands") { phaser.sweep shouldBe 400.0 }

                g < glideBlocks -> withClue("block $k: the sweep is $g of $glideBlocks blocks into its 50 ms glide") {
                    phaser.sweep shouldBe (400.0 + 400.0 * g / glideBlocks plusOrMinus 1e-9)
                }

                else -> withClue("block $k: the sweep landed") { phaser.sweep shouldBe 800.0 }
            }
        }
    }
})
