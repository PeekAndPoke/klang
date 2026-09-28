/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.plus

/**
 * The census on hand-counted graphs: each row states the passes and the buffer traffic a reader
 * can count from the runtime lowering, so a weight that drifts from the engine is caught here.
 */
class GraphCensusSpec : StringSpec({

    fun c(v: Double) = IgnitorDsl.Constant(v)
    val saw = IgnitorDsl.Sawtooth()

    "a source is one pass that writes the block" {
        val census = GraphCensus.of(saw)

        census.passes shouldBe 1
        census.traffic shouldBe 1
    }

    "a filter is one pass in place per stage, and holds a section's state per stage" {
        GraphCensus.of(saw.lowpass(1000.0)).let {
            it.passes shouldBe 2
            it.traffic shouldBe 3
            it.bytes shouldBe 64 + 48
        }
        GraphCensus.of(IgnitorDsl.Lowpass(saw, freq = c(1000.0), passes = c(3.0))).let {
            it.passes shouldBe 4
            it.traffic shouldBe 7
            it.bytes shouldBe 64 + 3 * 48
        }
        // the engine clamps a cascade at FILTER_MAX_PASSES (16), so does the count
        GraphCensus.of(IgnitorDsl.Lowpass(saw, freq = c(1000.0), passes = c(64.0))).passes shouldBe 1 + 16
        // a slotted count (phase 3 step 5) is read through the voice's params, as the runtime reads it
        GraphCensus.of(IgnitorDsl.Lowpass(saw, freq = c(1000.0), passes = IgnitorDsl.Param("p", 1.0)), params = mapOf("p" to 3.0))
            .passes shouldBe 4
    }

    "scalar-only arithmetic is one value per block: no pass, no traffic" {
        val gain = c(2.0).plus(c(1.0)).mul(IgnitorDsl.Param("level", 0.5))
        val census = GraphCensus.of(saw.mul(gain))

        census.passes shouldBe 2
        census.traffic shouldBe 3
        GraphCensus.isScalar(gain) shouldBe true
        GraphCensus.isScalar(saw.mul(gain)) shouldBe false
    }

    "the optimizer's Affine is one pass in place where the chain was three" {
        val chain = saw.mul(c(2.0)).plus(c(1.0)).mul(c(0.5))

        GraphCensus.of(chain).passes shouldBe 4
        GraphCensus.of(chain.optimize()).passes shouldBe 3 // the saw, one Affine, one more Affine (no merge across the add)
        GraphCensus.of(saw.mul(c(2.0)).plus(c(1.0)).optimize()).let {
            it.passes shouldBe 2
            it.traffic shouldBe 3
        }
    }

    "a pass over two signals reads both and writes one" {
        val census = GraphCensus.of(saw.plus(IgnitorDsl.Sine()))

        census.passes shouldBe 3
        census.traffic shouldBe 1 + 1 + 3
    }

    "an Eq is a pass per section; a tap reads the input copy and the chain, and the copy is a pass over the input" {
        val serial = IgnitorDsl.Eq(
            saw,
            sections = listOf(
                IgnitorDsl.EqSection.Lowpass(c(1000.0)),
                IgnitorDsl.EqSection.Highpass(c(100.0)),
                IgnitorDsl.EqSection.Bell(c(500.0), c(1.0), c(3.0)),
            ),
        )
        val tapped = IgnitorDsl.Eq(
            saw,
            sections = listOf(IgnitorDsl.EqSection.Lowpass(c(1000.0)), IgnitorDsl.EqSection.RawTap(c(800.0), c(1.0), c(0.5))),
        )

        GraphCensus.of(serial).let {
            it.passes shouldBe 4
            it.traffic shouldBe 1 + 6
            it.bytes shouldBe 64 + 3 * 48
        }
        GraphCensus.of(tapped).let {
            // the saw, the serial section, the tap, and the input copy, which is a pass over the block
            it.passes shouldBe 4
            // the saw, the serial section in place (2), the tap reading the copy and the chain (3), the copy (2)
            it.traffic shouldBe 1 + 2 + 3 + 2
            it.bytes shouldBe 64 + 2 * 48 + 128 * 8
        }
    }

    "an oversampled shaper counts its round trip: upsample, shaper loop, decimation stages, copy back" {
        // distort(0.5, soft, 4) lowers to Drive then Shape at 4x: the drive is one pass in place;
        // the shape reads one and writes four, shapes eight, decimates 4 -> 2 (two outputs, nine
        // taps read each, two written) and 2 -> 1 (one output, nine taps, one written), copies back
        // two, then the DC blocker and the soft cap
        GraphCensus.oversampleTraffic(4) shouldBe (1 + 4) + (9 * 2 + 2) + (9 * 1 + 1) + 2
        GraphCensus.oversampleTraffic(1) shouldBe 0

        val census = GraphCensus.of(saw.distort(0.5, "soft", 4))

        census.passes shouldBe 3
        census.traffic shouldBe 1 + 2 + (2 * 4 + 2 + 2 + GraphCensus.oversampleTraffic(4))
    }

    "the fused Distort node is ONE stage: the drive rides in the shaper loop, a copy out replaces the cap" {
        // classic()'s distort stage, the strip's law since phase 3 step 4 (D2): no separate drive pass.
        // At 4x: the saw (one pass, one write, its 64 bytes); the stage reads one and writes four,
        // drives and shapes eight in place (2 * 4), runs the DC blocker in place (2) and copies its
        // work buffer out (2), plus the oversampler's round trip; its state is the oversampler's plus
        // the DC blocker's 24 bytes.
        val census = GraphCensus.of(IgnitorDsl.Distort(saw, IgnitorDsl.Constant(0.5), oversample = IgnitorDsl.Constant(4.0)))

        census.passes shouldBe 1 + 1
        census.traffic shouldBe 1 + (2 * 4 + 2 + 2 + GraphCensus.oversampleTraffic(4))
        census.bytes shouldBe 64 + (GraphCensus.oversampleBytes(4) + 24)
    }

    "a shared node renders once into a memo, and every consumer copies the memo out" {
        val filtered = saw.lowpass(1000.0)
        val census = GraphCensus.of(filtered.plus(filtered.highpass(2000.0)))

        // saw 1, lowpass 1, highpass 1, plus 1; the shared filtered subtree is counted once
        census.passes shouldBe 4
        // saw 1, lowpass 2, highpass 2, plus over two signals 3, two memo copies of 2 each
        census.traffic shouldBe 1 + 2 + 2 + 3 + 2 + 2
        census.bytes shouldBe 64 + 48 + 48 + 128 * 8
    }

    "sharing is by identity, as the runtime's build cache keys it: two equal saws are two saws" {
        val census = GraphCensus.of(IgnitorDsl.Sawtooth().plus(IgnitorDsl.Sawtooth()))

        census.passes shouldBe 3
        census.traffic shouldBe 1 + 1 + 3
        census.bytes shouldBe 64 + 64
    }

    "a pitch-mod node renders its mod block, the ratio loop reads it, the source reads the ratio" {
        val census = GraphCensus.of(IgnitorDsl.Vibrato(saw.lowpass(1000.0)))

        census.passes shouldBe 3
        census.traffic shouldBe 3 + 4
        // a detune is a constant factor folded into the source's increment
        GraphCensus.of(IgnitorDsl.Detune(saw, semitones = c(12.0))).passes shouldBe 1
    }

    "one variant is counted, the one the sound index picks, and the others make nothing shared" {
        val variants = IgnitorDsl.Variants(listOf(saw, saw.lowpass(1000.0).highpass(100.0)))

        GraphCensus.of(variants).let {
            it.passes shouldBe 1
            it.bytes shouldBe 64
        }
        GraphCensus.of(variants, soundIndex = 1).passes shouldBe 3
        GraphCensus.of(variants, soundIndex = 2).passes shouldBe 1
    }

    "a lerp always renders its second signal and a select both branches, whatever their kind" {
        GraphCensus.of(IgnitorDsl.Lerp(saw, c(0.5), c(0.5))).traffic shouldBe 1 + 3
        GraphCensus.of(IgnitorDsl.Select(saw, c(1.0), c(0.0))).traffic shouldBe 1 + 4
    }

    "a unison stack is a pass per voice; the count comes from the literal, the voice's params, or the slot's default" {
        GraphCensus.of(IgnitorDsl.SuperSaw(voices = c(4.0))).let {
            it.passes shouldBe 4
            it.traffic shouldBe 2 * 4 - 1
            it.bytes shouldBe 64 + 4 * 40
        }
        GraphCensus.of(IgnitorDsl.SuperSaw(), params = mapOf("voices" to 13.0)).passes shouldBe 13
        GraphCensus.of(IgnitorDsl.SuperSaw()).passes shouldBe 8
    }
})
