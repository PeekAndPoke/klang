/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS

/**
 * `classic()`'s STRUCTURE and its SLOT VOCABULARY (phase 3 step 5), pinned where they are written.
 *
 * The order is the strip's (`docs/tasks/builtin-instruments.md` section 4): crush, coarse, distort,
 * highpass, bandpass, notch, lowpass, tremolo, adsr. The slot table below is the contract step 8 builds
 * on (the sprudel doors become `oscp` aliases and write exactly these keys), so a renamed key or a moved
 * default is a red row here before it is a silent door anywhere else. What the tail RENDERS is pinned in
 * `audio_be` (`ClassicStripParitySpec`, `ClassicTailRenderSpec`).
 */
class ClassicTailSpec : StringSpec({

    val saw = IgnitorDsl.Sawtooth()
    val tail = saw.classic()

    /** The chain from the outermost stage to the source, one node per stage. */
    fun spine(node: IgnitorDsl): List<IgnitorDsl> = buildList {
        var n: IgnitorDsl = node

        while (true) {
            add(n)

            n = when (n) {
                is IgnitorDsl.Adsr -> n.inner
                is IgnitorDsl.Tremolo -> n.inner
                is IgnitorDsl.Lowpass -> n.inner
                is IgnitorDsl.Notch -> n.inner
                is IgnitorDsl.Bandpass -> n.inner
                is IgnitorDsl.Highpass -> n.inner
                is IgnitorDsl.Distort -> n.inner
                is IgnitorDsl.Coarse -> n.inner
                is IgnitorDsl.Crush -> n.inner
                else -> return@buildList
            }
        }
    }

    "the order is the strip's: crush, coarse, distort, hpf, bpf, notch, lpf, tremolo, adsr (read inside out)" {
        spine(tail).map { it::class.simpleName } shouldBe listOf(
            "Adsr", "Tremolo", "Lowpass", "Notch", "Bandpass", "Highpass", "Distort", "Coarse", "Crush", "Sawtooth",
        )
        spine(tail).last() shouldBe saw
    }

    "the distort stage is the fused Distort node, the one that switches drive and shape off as a unit" {
        spine(tail).filterIsInstance<IgnitorDsl.Distort>().size shouldBe 1
        spine(tail).any { it is IgnitorDsl.Shape || it is IgnitorDsl.Drive } shouldBe false
    }

    "every filter humanizes from the voice's analog slot, as the strip does" {
        val filters = spine(tail).filter {
            it is IgnitorDsl.Lowpass || it is IgnitorDsl.Highpass || it is IgnitorDsl.Bandpass || it is IgnitorDsl.Notch
        }

        filters.size shouldBe 4

        for (f in filters) {
            withClue(f::class.simpleName) {
                val (analog, humanize) = when (f) {
                    is IgnitorDsl.Lowpass -> f.analog to f.humanize
                    is IgnitorDsl.Highpass -> f.analog to f.humanize
                    is IgnitorDsl.Bandpass -> f.analog to f.humanize
                    is IgnitorDsl.Notch -> f.analog to f.humanize
                    else -> error("not a filter")
                }

                analog shouldBe IgnitorDsl.Slots.analog
                humanize shouldBe true
            }
        }
    }

    "the envelope de-clicks at the strip's constant, not through a slot" {
        val adsr = tail.shouldBeInstanceOf<IgnitorDsl.Adsr>()

        adsr.declickSeconds shouldBe IgnitorDsl.Constant(ENV_DECLICK_SECONDS)
    }

    "the slot vocabulary: every name, in placement order, with the strip's untouched value as its default" {
        val unset = Double.NaN
        val exp = AdsrCurves.indexOf(AdsrCurve.Default)
        // The filter envelopes' curve slots default to the modulation envelopes' curve (D3), written as the
        // literal here: Exponential, the same index as the amplitude default today, a separate decision.
        val modExp = AdsrCurves.indexOf(AdsrCurve.Exponential)
        val expected: List<Pair<String, Double>> = listOf(
            "crush.amount" to 0.0,
            "coarse.amount" to 0.0,
            "distort.amount" to 0.0,
            "distort.shape" to DistortionShapes.SOFT_INDEX.toDouble(),
            "distort.oversample" to 0.0,
        ) + listOf("hpf", "bpf", "notch", "lpf").flatMap { door ->
            val hasPasses = door == "hpf" || door == "lpf"

            buildList {
                add("$door.freq" to unset)
                add("$door.q" to 0.707)
                add("analog" to 0.0)

                if (hasPasses) {
                    add("$door.passes" to 1.0)
                }

                add("$door.env" to unset)
                add("$door.attack" to 0.01)
                add("$door.decay" to 0.1)
                add("$door.sustain" to 1.0)
                add("$door.release" to 0.1)
                add("${door}Curves.attack" to modExp)
                add("${door}Curves.decay" to modExp)
                add("${door}Curves.release" to modExp)
            }
        } + listOf(
            "tremolo.sync" to 0.0,
            "tremolo.depth" to 0.0,
            "tremolo.shape" to LfoShapes.SINE_INDEX.toDouble(),
            "tremolo.skew" to 0.0,
            "tremolo.phase" to 0.0,
            "adsr.attack" to 0.01,
            "adsr.decay" to 0.1,
            "adsr.sustain" to 1.0,
            "adsr.release" to 0.05,
            "adsrCurves.attack" to exp,
            "adsrCurves.decay" to exp,
            "adsrCurves.release" to exp,
            "adsr.on" to 1.0,
        )

        // Collection runs inner first, so the source's own slot (the saw's `analog`) comes first.
        val actual = tail.getParamSlots().map { it.name to it.default }.drop(1)

        actual.size shouldBe expected.size

        for ((i, pair) in expected.withIndex()) {
            withClue("slot $i, ${pair.first}") {
                actual[i].first shouldBe pair.first
                // raw bits, so the two unset defaults compare as NaN against NaN
                actual[i].second.toRawBits() shouldBe pair.second.toRawBits()
            }
        }
    }

    "a slot's user-visible description claims a sprudel reader only where sprudel has one" {
        val curveDoors = listOf("adsrCurves", "hpfCurves", "bpfCurves", "notchCurves", "lpfCurves")
        val noReader = setOf("distort.shape", "tremolo.shape", "adsr.on") +
            curveDoors.flatMap { d -> listOf("$d.attack", "$d.decay", "$d.release") }
        val slots = tail.getParamSlots().filter { it.name != "analog" }

        for (p in slots) {
            withClue(p.name) {
                if (p.name in noReader) {
                    p.description.contains("Mirrors") shouldBe false
                    p.description.contains("what sprudel") shouldBe true
                } else {
                    p.description shouldBe "Mirrors sprudel's reader `${p.name}`"
                }
            }
        }
    }

    "the defaults are the voice envelope's, not the adsr node's (sustain 1.0 against 0.7, release 0.05 against 0.3)" {
        val adsr = AdsrDef.Std.defaultSynth

        (IgnitorDsl.Slots.adsr.attack as IgnitorDsl.Param).default shouldBe adsr.attack
        (IgnitorDsl.Slots.adsr.decay as IgnitorDsl.Param).default shouldBe adsr.decay
        (IgnitorDsl.Slots.adsr.sustain as IgnitorDsl.Param).default shouldBe adsr.sustain
        (IgnitorDsl.Slots.adsr.release as IgnitorDsl.Param).default shouldBe adsr.release
    }

    "classic() is a function of its input only: two calls on one source build equal trees" {
        saw.classic() shouldBe saw.classic()
        IgnitorDsl.Square().classic() shouldBe IgnitorDsl.Square().classic()
    }

    "a non-finite pass count is one pass, the house rule for a non-finite wire number" {
        coercePasses(Double.POSITIVE_INFINITY) shouldBe 1
        coercePasses(Double.NEGATIVE_INFINITY) shouldBe 1
        coercePasses(Double.NaN) shouldBe 1
        coercePasses(2.5) shouldBe 3
        coercePasses(1e9) shouldBe FILTER_MAX_PASSES
    }
})
