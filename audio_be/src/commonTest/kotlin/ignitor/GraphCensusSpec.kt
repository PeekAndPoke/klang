/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.tremolo
import io.peekandpoke.klang.audio_bridge.withChildNodes
import io.peekandpoke.klang.common.infra.klangMapOf

/**
 * The census on hand-counted graphs: each row states the passes and the buffer traffic a reader
 * can count from the runtime lowering, so a weight that drifts from the engine is caught here.
 */
class GraphCensusSpec : StringSpec({

    fun c(v: Double) = IgnitorDsl.Constant(v)
    val saw = IgnitorDsl.Saw()

    // the tree with every pitch stage replaced by the child it bends, as the runtime builds a gated one
    fun withoutPitchStages(node: IgnitorDsl): IgnitorDsl = when (node) {
        is IgnitorDsl.Vibrato -> withoutPitchStages(node.inner)
        is IgnitorDsl.Accelerate -> withoutPitchStages(node.inner)
        is IgnitorDsl.PitchEnvelope -> withoutPitchStages(node.inner)
        is IgnitorDsl.Fm -> withoutPitchStages(node.carrier)
        else -> node.withChildNodes(node.childNodes().map { withoutPitchStages(it) })
    }

    "a sine's explicit partials count up to the cap: the knobs of a 257th and later are not built, so not counted" {
        // every gain a signal (a 3 Hz sine), so each built partial's knob is one more source pass
        fun sine(n: Int) = IgnitorDsl.Sine(
            fundamental = c(0.0),
            partials = List(n) { IgnitorDsl.Sine.Partial(ratio = c(0.5 + 0.01 * it), gain = IgnitorDsl.Sine(freq = c(3.0 + it))) },
        )

        GraphCensus.of(sine(300)) shouldBe GraphCensus.of(sine(256))
        GraphCensus.of(sine(255)).passes shouldBe GraphCensus.of(sine(256)).passes - 1
    }

    "a source is one pass that writes the block" {
        val census = GraphCensus.of(saw)

        census.passes shouldBe 1
        census.traffic shouldBe 1
    }

    "a filter is one pass in place per stage, and holds a section's state per stage" {
        GraphCensus.of(saw.lowpass(1000.0.ign)).let {
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
        GraphCensus.of(IgnitorDsl.Lowpass(saw, freq = c(1000.0), passes = IgnitorDsl.Param("p", 1.0)), params = klangMapOf("p" to 3.0))
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
                IgnitorDsl.EqSection.Bell(freq = c(500.0), q = c(1.0), db = c(3.0)),
            ),
        )
        val tapped = IgnitorDsl.Eq(
            saw,
            sections = listOf(IgnitorDsl.EqSection.Lowpass(c(1000.0)), IgnitorDsl.EqSection.RawTap(freq = c(800.0), q = c(1.0), gain = c(0.5))),
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
        // the IIR half-band round trip (2026-10-10): the first stage up reads one and writes two, the second moves its
        // two inputs (read and write), reads them and writes four; down, each output reads two and writes one (2 at the
        // second stage, 1 at the first); the copy back two. Then the DC blocker and the soft cap.
        GraphCensus.oversampleTraffic(4) shouldBe (1 + 2) + (2 + 2 + 2 + 4) + (2 * 3) + (1 * 3) + 2
        GraphCensus.oversampleTraffic(1) shouldBe 0
        // The state at 4x: two directions, two doubles (x1, y1) per all-pass section, 8 sections at the base-rate stage
        // and 6 above it, 8 bytes each.
        GraphCensus.oversampleBytes(4) shouldBe 2 * 2 * (8 + 6) * 8
        GraphCensus.oversampleBytes(1) shouldBe 0

        val census = GraphCensus.of(saw.distort(amount = 0.5.ign, shape = "soft", oversample = 4.ign))

        census.passes shouldBe 3
        census.traffic shouldBe 1 + 2 + (2 * 4 + 2 + 2 + GraphCensus.oversampleTraffic(4))
    }

    "the fused Distort node is ONE stage: the drive rides in the shaper loop, a copy out replaces the cap" {
        // classic()'s distort stage, the strip's law since phase 3 step 4 (D2): no separate drive pass.
        // At 4x: the saw (one pass, one write, its 64 bytes); the stage reads one and writes four,
        // drives and shapes eight in place (2 * 4), runs the DC blocker in place (2) and copies its
        // work buffer out (2), plus the oversampler's round trip; its state is the oversampler's plus
        // the DC blocker's 24 bytes.
        val census = GraphCensus.of(IgnitorDsl.Distort(inner = saw, amount = IgnitorDsl.Constant(0.5), oversample = IgnitorDsl.Constant(4.0)))

        census.passes shouldBe 1 + 1
        census.traffic shouldBe 1 + (2 * 4 + 2 + 2 + GraphCensus.oversampleTraffic(4))
        census.bytes shouldBe 64 + (GraphCensus.oversampleBytes(4) + 24)
    }

    "a shared node renders once into a memo, and every consumer copies the memo out" {
        val filtered = saw.lowpass(1000.0.ign)
        val census = GraphCensus.of(filtered.plus(filtered.highpass(2000.0.ign)))

        // saw 1, lowpass 1, highpass 1, plus 1; the shared filtered subtree is counted once
        census.passes shouldBe 4
        // saw 1, lowpass 2, highpass 2, plus over two signals 3, two memo copies of 2 each
        census.traffic shouldBe 1 + 2 + 2 + 3 + 2 + 2
        census.bytes shouldBe 64 + 48 + 48 + 128 * 8
    }

    "sharing is by identity, as the runtime's build cache keys it: two equal saws are two saws" {
        val census = GraphCensus.of(IgnitorDsl.Saw().plus(IgnitorDsl.Saw()))

        census.passes shouldBe 3
        census.traffic shouldBe 1 + 1 + 3
        census.bytes shouldBe 64 + 64
    }

    "a pitch-mod node renders its mod into a memo; the source copies it out, runs the ratio loop and reads the ratio" {
        val census = GraphCensus.of(IgnitorDsl.Accelerate(saw.lowpass(1000.0.ign), semitones = c(12.0)))

        // the saw (1, 1) + the lowpass (1, 2) + the accelerate writing its ratio block (1, 1) into the memo
        // (`combineMods` caches per block even for one reader: a block of state) + the saw, the one reader: the copy
        // out of the memo (0, 2), the ratio loop's read and write (0, 2), its read of the ratio (0, 1)
        census.passes shouldBe 3
        census.traffic shouldBe 1 + 2 + 1 + 2 + 2 + 1
        census.bytes shouldBe 64 + 48 + 64 + 128 * 8
        // a detune is a constant factor folded into the source's increment
        GraphCensus.of(IgnitorDsl.Detune(saw, semitones = c(12.0))).passes shouldBe 1
    }

    "a vibrato is its composition: LFO, multiply, converter, memo, the source's copy, loop and read; a range, a signal depth or phase add theirs" {
        val lfo = IgnitorDsl.Sine(freq = c(0.5))
        val plain = GraphCensus.of(IgnitorDsl.Vibrato(saw))

        // the saw (1, 1) + the LFO (1, 1) + the multiply in place over the scalar depth (1, 2) + the converter in
        // place (1, 2) into the memo (a block of state) + the saw reading it: the copy out (0, 2), the ratio loop, a
        // read and a write (0, 2), the source reading the ratio (0, 1); the floor folds to one value per block
        plain.passes shouldBe 4
        plain.traffic shouldBe 1 + 1 + 2 + 2 + 2 + 2 + 1
        plain.bytes shouldBe 2 * 64 + 128 * 8

        // a range is built per note, as the runtime builds it: none at the default swing, a written-out default, a
        // non-finite literal or an unset slot; one in place (1, 2) for any other literal, a slot's value included
        val ranged = GraphCensus.of(IgnitorDsl.Vibrato(saw, rangeFrom = c(0.0), rangeTo = c(1.0)))

        ranged shouldBe plain + GraphCensus(passes = 1, traffic = 2, bytes = 0)
        GraphCensus.of(IgnitorDsl.Vibrato(saw, rangeFrom = c(-1.0), rangeTo = c(1.0))) shouldBe plain
        GraphCensus.of(IgnitorDsl.Vibrato(saw, rangeTo = c(Double.NaN))) shouldBe plain
        GraphCensus.of(IgnitorDsl.Vibrato(saw, rangeTo = IgnitorDsl.Param("vt", 1.0))) shouldBe plain
        GraphCensus.of(IgnitorDsl.Vibrato(saw, rangeTo = IgnitorDsl.Param("vt", 1.0)), params = klangMapOf("vt" to 2.0)) shouldBe ranged

        // a signal bound: + the bound's sine (1, 1), the range reading both bounds (1, 2 + 2)
        GraphCensus.of(IgnitorDsl.Vibrato(saw, rangeTo = lfo)).let {
            it.passes shouldBe 6
            it.traffic shouldBe 1 + 1 + 1 + 4 + 2 + 2 + 2 + 2 + 1
            it.bytes shouldBe 3 * 64 + 128 * 8
        }

        // a signal depth: + the depth's sine (1, 1), its floor in place (1, 2), and the multiply over two signals (1, 3)
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = lfo)).let {
            it.passes shouldBe 6
            it.traffic shouldBe 1 + 1 + 1 + 2 + 3 + 2 + 2 + 2 + 1
            it.bytes shouldBe 3 * 64 + 128 * 8
        }

        // block-constant arithmetic as the depth is control-rate, so counted like a leaf
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = IgnitorDsl.Plus(left = IgnitorDsl.Param("d", 0.2), right = c(0.3)))) shouldBe plain

        // a moving phase: + the phase's sine (1, 1) and the LFO's loop reading it (0, 1); a scalar phase costs nothing
        GraphCensus.of(IgnitorDsl.Vibrato(saw, phase = lfo)).let {
            it.passes shouldBe 5
            it.traffic shouldBe 1 + 1 + 1 + 1 + 2 + 2 + 2 + 2 + 1
            it.bytes shouldBe 3 * 64 + 128 * 8
        }
        GraphCensus.of(IgnitorDsl.Vibrato(saw, phase = c(0.25))) shouldBe plain
    }

    "a pitch stage at a LITERAL off value is not built: it counts nothing, its knobs included; any other switch is built" {
        val lfo = IgnitorDsl.Sine(freq = c(0.5))
        val bare = GraphCensus.of(saw)
        val plainVibrato = GraphCensus.of(IgnitorDsl.Vibrato(saw))

        // the vibrato: off at a finite depth <= 0, a slot read through the voice's params (a non-finite override reads
        // as the slot's default); a signal rate is a knob, not built either
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = c(0.0))) shouldBe bare
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = c(-1.0))) shouldBe bare
        GraphCensus.of(IgnitorDsl.Vibrato(saw, rate = lfo, semitones = c(0.0))) shouldBe bare
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = IgnitorDsl.Param("vd", 0.0))) shouldBe bare
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = IgnitorDsl.Param("vd", 0.0)), params = klangMapOf("vd" to Double.NaN)) shouldBe bare
        // a slot written on is built, the plain vibrato's count (the depth is a scalar either way)
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = IgnitorDsl.Param("vd", 0.0)), params = klangMapOf("vd" to 0.5)) shouldBe plainVibrato
        // a non-finite LITERAL depth is NOT off: the runtime renders the node's default depth. -Infinity is the case
        // that tells "off at a finite depth <= 0" from "off at a depth <= 0" (NaN <= 0 is false either way)
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = c(Double.NaN))) shouldBe plainVibrato
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = c(Double.NEGATIVE_INFINITY))) shouldBe plainVibrato
        // only a literal gates: block-constant arithmetic at 0 is built, as the runtime builds it. Its count is not
        // pinned: the runtime's multiply `swing * max(semitones, 0)` takes its dead branch at a block-constant 0
        // (`binaryLadder`), so the LFO does not render, which the census does not model (it over-counts this case)
        GraphCensus.of(IgnitorDsl.Vibrato(saw, semitones = IgnitorDsl.Plus(left = c(0.0), right = c(0.0)))) shouldNotBe bare

        // accelerate and the pitch envelope: off at 0 and at a non-finite literal; a negative amount is on.
        // Built: the saw (1, 1) + the ratio block (1, 1) + the memo (a block) + the saw's copy, loop and read (0, 5)
        val accelerated = GraphCensus(passes = 2, traffic = 1 + 1 + 2 + 2 + 1, bytes = 2 * 64 + 128 * 8)

        GraphCensus.of(IgnitorDsl.Accelerate(saw, semitones = c(0.0))) shouldBe bare
        GraphCensus.of(IgnitorDsl.Accelerate(saw, semitones = c(Double.NaN))) shouldBe bare
        GraphCensus.of(IgnitorDsl.Accelerate(saw, semitones = c(-12.0))) shouldBe accelerated
        GraphCensus.of(IgnitorDsl.PitchEnvelope(saw)) shouldBe bare
        GraphCensus.of(IgnitorDsl.PitchEnvelope(saw, semitones = c(Double.POSITIVE_INFINITY))) shouldBe bare
        GraphCensus.of(IgnitorDsl.PitchEnvelope(saw, semitones = c(12.0))) shouldBe accelerated

        // pitchModSemitones: off at a literal 0; a signal mod is built: the saw (1, 1) + the mod's sine (1, 1) + the
        // conversion in place (1, 2) + the memo + the saw's copy, loop and read (0, 5)
        val converted = GraphCensus(passes = 3, traffic = 1 + 1 + 2 + 2 + 2 + 1, bytes = 3 * 64 + 128 * 8)

        GraphCensus.of(IgnitorDsl.PitchModSemitones(saw, mod = c(0.0))) shouldBe bare
        GraphCensus.of(IgnitorDsl.PitchModSemitones(saw, mod = lfo)) shouldBe converted
        // pitchMod has no gate, and the same conversion in place (`DeviationToRatioIgnitor`)
        GraphCensus.of(IgnitorDsl.PitchMod(saw, mod = lfo)) shouldBe converted

        // fm: off at a depth of 0 (its default), its modulator not built; built, the saw (1, 1) + the modulator sine
        // (1, 1) + the fm loop reading it and writing the ratios (1, 2) + the memo + the saw's copy, loop and read (0, 5)
        GraphCensus.of(IgnitorDsl.Fm(carrier = saw, modulator = IgnitorDsl.Sine())) shouldBe bare
        GraphCensus.of(IgnitorDsl.Fm(carrier = saw, modulator = IgnitorDsl.Sine(), depth = c(100.0))) shouldBe
            GraphCensus(passes = 3, traffic = 1 + 1 + 2 + 2 + 2 + 1, bytes = 3 * 64 + 128 * 8)
    }

    "classic() with nothing written builds no pitch stage; a written one counts its mod, its memo and its source's read" {
        val vibratoDepth = (IgnitorDsl.Slots.vibrato.semitones as IgnitorDsl.Param).name
        val accelerate = (IgnitorDsl.Slots.accelerate as IgnitorDsl.Param).name
        val fmDepth = (IgnitorDsl.Slots.fm.depth as IgnitorDsl.Param).name
        val tail = saw.classic()
        val unwritten = GraphCensus.of(tail)

        // the four pitch stages at their unwritten slots (vibrato, accelerate and pitch envelope at 0, fm at depth 0)
        // count exactly what the tree without them counts
        unwritten shouldBe GraphCensus.of(withoutPitchStages(tail))

        // the vibrato written on: its LFO (1, 1), the multiply (1, 2), the converter (1, 2), the memo (a block), and the
        // saw under the three gated stages its one reader: the copy out, the ratio loop, the read (0, 5)
        GraphCensus.of(tail, params = klangMapOf(vibratoDepth to 0.5)) shouldBe
            unwritten + GraphCensus(passes = 3, traffic = 1 + 2 + 2 + 2 + 2 + 1, bytes = 64 + 128 * 8)

        // the vibrato and the accelerate: ONE ratio loop and ONE read (the saw's), and one combine: the accelerate's
        // memo holds `vibrato * accelerate`, the vibrato's memo copied out (0, 2) and multiplied with the ratio block
        // over two signals (1, 3). The vibrato (3, 5) and its memo; the accelerate's block (1, 1) and its memo; the
        // combine (1, 2 + 3); the saw (0, 2 + 2 + 1)
        GraphCensus.of(tail, params = klangMapOf(vibratoDepth to 0.5, accelerate to 12.0)) shouldBe
            unwritten + GraphCensus(passes = 3 + 1 + 1, traffic = 5 + 1 + 5 + 5, bytes = 2 * 64 + 2 * 128 * 8)

        // the vibrato and the fm: the fm's memo combines the vibrato's (1, 2 + 3), and the fm's modulator, a pitched
        // sine, reads the vibrato's memo too (`CarrierFreqMod`: 0, 2 + 2 + 1); the fm loop (1, 2) and its memo, the
        // modulator's own render (1, 1), the saw reading the fm's memo (0, 5)
        GraphCensus.of(tail, params = klangMapOf(vibratoDepth to 0.5, fmDepth to 100.0)) shouldBe
            unwritten + GraphCensus(passes = 3 + 1 + 1 + 1, traffic = 5 + 5 + 5 + 2 + 1 + 5, bytes = 3 * 64 + 2 * 128 * 8)
    }

    "the ratio loop and the read are per pitched source: two sources two, a fixed freq and a noise none, a stack a read per voice" {
        // two pitched sources under one accelerate: the saw (1, 1), the sine (1, 1), the sum (1, 3), the ratio block
        // (1, 1) and its memo, then EACH source its copy, loop and read (0, 5)
        GraphCensus.of(IgnitorDsl.Accelerate(saw.plus(IgnitorDsl.Sine()), semitones = c(12.0))).let {
            it.passes shouldBe 4
            it.traffic shouldBe 1 + 1 + 3 + 1 + 5 + 5
            it.bytes shouldBe 3 * 64 + 128 * 8
        }

        // a source whose freq reads no note is shielded (`ModBlockingIgnitor`) and a noise ignores the mod: only the
        // saw reads. The saw, the 5 Hz sine, the noise (1, 1 each), two sums (1, 3 each), the ratio block (1, 1), the saw (0, 5)
        val fixedAndNoise = saw.plus(IgnitorDsl.Sine(freq = c(5.0))).plus(IgnitorDsl.WhiteNoise())

        GraphCensus.of(IgnitorDsl.Accelerate(fixedAndNoise, semitones = c(12.0))).let {
            it.passes shouldBe 6
            it.traffic shouldBe 1 + 1 + 1 + 3 + 3 + 1 + 5
            it.bytes shouldBe 4 * 64 + 128 * 8
        }

        // a unison stack reads the ratio in each voice loop: the stack of 4 (4, 2 * 4 - 1), the ratio block (1, 1),
        // the copy and the loop (0, 4), four reads (0, 4)
        GraphCensus.of(IgnitorDsl.Accelerate(IgnitorDsl.SuperSaw(voices = c(4.0)), semitones = c(12.0))).let {
            it.passes shouldBe 5
            it.traffic shouldBe 7 + 1 + 4 + 4
            it.bytes shouldBe 64 + 4 * 40 + 64 + 128 * 8
        }

        // a sample's playhead reads the ratio, a silence is wrapped too but reads none: the source (1, 1), the ratio
        // block (1, 1) and its memo, the copy and the loop (0, 4), the read (0, 1) for the sample only
        GraphCensus.of(IgnitorDsl.Accelerate(IgnitorDsl.Sample, semitones = c(12.0))) shouldBe
            GraphCensus(passes = 2, traffic = 1 + 1 + 4 + 1, bytes = 2 * 64 + 128 * 8)
        GraphCensus.of(IgnitorDsl.Accelerate(IgnitorDsl.Silence, semitones = c(12.0))) shouldBe
            GraphCensus(passes = 2, traffic = 1 + 1 + 4, bytes = 64 + 128 * 8)

        // one source reached twice under one pitch node is one reader, as the build cache shares it: the saw (1, 1)
        // shared, its own memo copied out by both consumers (0, 2 + 2, a block), the highpass (1, 2), the sum (1, 3),
        // the ratio block (1, 1) and its memo, the saw's copy, loop and read once (0, 5)
        val shared = IgnitorDsl.Saw()

        GraphCensus.of(IgnitorDsl.Accelerate(shared.plus(shared.highpass(100.0.ign)), semitones = c(12.0))).let {
            it.passes shouldBe 4
            it.traffic shouldBe 1 + 4 + 2 + 3 + 1 + 5
            it.bytes shouldBe 64 + 128 * 8 + 48 + 64 + 128 * 8
        }
    }

    "a pitch node under another multiplies the outer memo into its own; a gated one between them is transparent" {
        // the saw (1, 1); the vibrato's LFO, multiply and converter (3, 5) and its memo; the accelerate reads that
        // memo into its own: the copy out and `vibrato * accelerate` over two signals (1, 2 + 3); the accelerate's
        // ratio block (1, 1) and its memo; the saw reads the accelerate's memo once (0, 5)
        GraphCensus.of(IgnitorDsl.Vibrato(IgnitorDsl.Accelerate(saw, semitones = c(12.0)))).let {
            it.passes shouldBe 1 + 3 + 1 + 1
            it.traffic shouldBe 1 + 5 + 5 + 1 + 5
            it.bytes shouldBe 3 * 64 + 2 * 128 * 8
        }

        GraphCensus.of(IgnitorDsl.Vibrato(IgnitorDsl.Accelerate(saw, semitones = c(0.0)))) shouldBe GraphCensus.of(IgnitorDsl.Vibrato(saw))

        // an fm under the vibrato: the fm's memo combines the vibrato's (1, 2 + 3), and its modulator reads the
        // vibrato's memo (0, 5); the saw (1, 1), the modulator (1, 1), the vibrato (3, 5), the fm loop (1, 2), the saw
        // reading the fm's memo (0, 5)
        GraphCensus.of(IgnitorDsl.Vibrato(IgnitorDsl.Fm(carrier = saw, modulator = IgnitorDsl.Sine(), depth = c(100.0)))).let {
            it.passes shouldBe 1 + 1 + 3 + 1 + 1
            it.traffic shouldBe 1 + 1 + 5 + 5 + 5 + 2 + 5
            it.bytes shouldBe 4 * 64 + 2 * 128 * 8
        }
    }

    "a song-shaped voice: the readers sit under a filter, a detune, an envelope and a sum, a pluck among them" {
        // IrishLament's blockfloete cut down, with a pluck: `PitchEnvelope(Vibrato(OnePoleLowpass(sum)))`, the mod reaching
        // the sum's every layer through the one-pole (`inner.withMod()`), the sums (both operands), the multiply (both),
        // the envelope (its inner) and the detunes (`inner.passThrough()`)
        val octave = IgnitorDsl.Times(left = IgnitorDsl.Detune(IgnitorDsl.Sine(), semitones = c(12.0)), right = c(0.1))
        val partial = IgnitorDsl.Adsr(
            IgnitorDsl.Detune(IgnitorDsl.Sine(), semitones = c(19.0)),
            attack = c(0.001),
            decay = c(0.1),
            sustain = c(0.01),
            release = c(0.01),
        )
        val layers = IgnitorDsl.Sine().plus(octave).plus(partial).plus(IgnitorDsl.Pluck())
        val flute = IgnitorDsl.PitchEnvelope(
            IgnitorDsl.Vibrato(IgnitorDsl.OnePoleLowpass(layers, freq = c(3500.0)), rate = c(3.5), semitones = c(0.1)),
            semitones = c(0.15),
        )

        // the layers: three sines (1, 1 each), the multiply in place over its scalar side (1, 2), the envelope in place
        // (1, 2), the pluck's string (1, 3), three sums (1, 3 each); the one-pole in place (1, 2); the detunes nothing.
        // The pitch envelope: its ratio block (1, 1) and its memo. The vibrato: its LFO, multiply and converter (3, 5),
        // the pitch envelope's memo read into its own (1, 2 + 3), its memo. Its four readers, the three sines and the
        // pluck: the copy out, the ratio loop and the read each (0, 2 + 2 + 1)
        GraphCensus.of(flute).let {
            it.passes shouldBe 3 + 1 + 1 + 1 + 3 + 1 + 1 + 3 + 1
            it.traffic shouldBe 3 + 2 + 2 + 3 + 9 + 2 + 1 + 5 + 5 + 4 * 5
            it.bytes shouldBe 3 * 64 + 64 + 2_500 * 8 + 16 + 64 + 64 + 2 * 128 * 8
        }
    }

    "one variant is counted, the one the sound index picks, and the others make nothing shared" {
        val variants = IgnitorDsl.Variants(listOf(saw, saw.lowpass(1000.0.ign).highpass(100.0.ign)))

        GraphCensus.of(variants).let {
            it.passes shouldBe 1
            it.bytes shouldBe 64
        }
        GraphCensus.of(variants, soundIndex = 1).passes shouldBe 3
        GraphCensus.of(variants, soundIndex = 2).passes shouldBe 1
    }

    "a lerp always renders its second signal and a select both branches, whatever their kind" {
        GraphCensus.of(IgnitorDsl.Lerp(left = saw, right = c(0.5), t = c(0.5))).traffic shouldBe 1 + 3
        GraphCensus.of(IgnitorDsl.Select(cond = saw, whenTrue = c(1.0), whenFalse = c(0.0))).traffic shouldBe 1 + 4
    }

    "a unison stack is a pass per voice; the count comes from the literal, the voice's params, or the slot's default" {
        GraphCensus.of(IgnitorDsl.SuperSaw(voices = c(4.0))).let {
            it.passes shouldBe 4
            it.traffic shouldBe 2 * 4 - 1
            it.bytes shouldBe 64 + 4 * 40
        }
        GraphCensus.of(IgnitorDsl.SuperSaw(), params = klangMapOf("voices" to 13.0)).passes shouldBe 13
        GraphCensus.of(IgnitorDsl.SuperSaw()).passes shouldBe 8
    }

    "a tremolo is its composition: the LFO source, the range, the multiply; a signal depth adds its floor and 1 - depth" {
        for (shape in LfoShapes.names) {
            withClue("$shape, a constant depth") {
                // the saw (1 pass, 1 write) + the LFO (1, 1) + range in place (1, 2) + the multiply (1, 3)
                GraphCensus.of(saw.tremolo(rate = 4.0.ign, depth = 0.5.ign) { it.shape(shape) }).let {
                    it.passes shouldBe 4
                    it.traffic shouldBe 7
                    it.bytes shouldBe 64 + 64
                }
            }

            withClue("$shape, a signal depth") {
                // + the depth's own sine (1, 1), max(depth, 0) in place (1, 2), 1 - depth in place (1, 2), and the
                // range reads both bounds (1, 2 + 2)
                val depth = IgnitorDsl.Sine(freq = c(2.0))
                val node = IgnitorDsl.Tremolo(saw, rate = c(4.0), depth = depth, shape = c(LfoShapes.indexOf(shape)))

                GraphCensus.of(node).let {
                    it.passes shouldBe 7
                    it.traffic shouldBe 14
                    it.bytes shouldBe 3 * 64
                }
            }

            withClue("$shape, block-constant arithmetic as the depth: control-rate, so counted like a leaf") {
                // the floor and 1 - depth fold to one value per block, and the range keeps its constant-bound path
                val depth = IgnitorDsl.Plus(left = IgnitorDsl.Param("d", 0.2), right = c(0.3))
                val node = IgnitorDsl.Tremolo(saw, rate = c(4.0), depth = depth, shape = c(LfoShapes.indexOf(shape)))

                GraphCensus.of(node).let {
                    it.passes shouldBe 4
                    it.traffic shouldBe 7
                    it.bytes shouldBe 2 * 64
                }
            }
        }
    }

    "an oscillator's phase: a scalar one costs nothing, a signal one adds its read to each voice loop" {
        val lfo = IgnitorDsl.Sine(freq = c(0.5))

        // the saw (1, 1) and nothing else: the constant moves the accumulator once per block
        GraphCensus.of(IgnitorDsl.Saw(phase = c(0.25))) shouldBe GraphCensus.of(saw)
        GraphCensus.of(IgnitorDsl.Saw(phase = IgnitorDsl.Param("ph", 0.1))) shouldBe GraphCensus.of(saw)

        // + the phase LFO (1, 1) + the saw's loop reading it (0, 1)
        GraphCensus.of(IgnitorDsl.Saw(phase = lfo)).let {
            it.passes shouldBe 2
            it.traffic shouldBe 3
            it.bytes shouldBe 2 * 64
        }

        // a stack of 5 (5 passes, 2 * 5 - 1) + the LFO (1, 1) + every voice loop reading it (0, 5)
        GraphCensus.of(IgnitorDsl.SuperSaw(voices = c(5.0), phase = lfo)).let {
            it.passes shouldBe 6
            it.traffic shouldBe 9 + 1 + 5
            it.bytes shouldBe 64 + 5 * 40 + 64
        }
    }

    "a tremolo off its classic range counts only what runs" {
        val lfo = IgnitorDsl.Sine(freq = c(0.5))

        // scalar depth and bounds fold to one value per block: the classic tremolo's count
        GraphCensus.of(IgnitorDsl.Tremolo(saw, rate = c(4.0), depth = c(0.5), rangeFrom = c(0.0), rangeTo = c(1.0))) shouldBe
            GraphCensus.of(saw.tremolo(rate = 4.0.ign, depth = 0.5.ign))

        // a signal upper bound, a scalar depth: the saw (1, 1), the LFO (1, 1), the bound's own sine (1, 1),
        // `floored * to` in place over the scalar side (1, 2), `+ 1` in place (1, 2), the range reading both bounds
        // (1, 4), the multiply (1, 3); no floor pass, no lower-bound pass
        GraphCensus.of(IgnitorDsl.Tremolo(saw, rate = c(4.0), depth = c(0.5), rangeFrom = c(0.0), rangeTo = lfo)).let {
            it.passes shouldBe 7
            it.traffic shouldBe 14
            it.bytes shouldBe 3 * 64
        }

        // a signal depth, scalar bounds: + the depth's sine (1, 1), its floor in place (1, 2) memoized for the two
        // bounds (a copy out each, 0, 4, a block of state), and each bound a multiply and an add (2 x (1, 2) each)
        GraphCensus.of(IgnitorDsl.Tremolo(saw, rate = c(4.0), depth = lfo, rangeFrom = c(0.0), rangeTo = c(1.0))).let {
            it.passes shouldBe 10
            it.traffic shouldBe 1 + 1 + 1 + 2 + 4 + 4 + 4 + 4 + 3
            it.bytes shouldBe 3 * 64 + 128 * 8
        }
    }
})
