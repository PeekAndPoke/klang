/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.IgnitorDsl.Constant
import io.peekandpoke.klang.audio_bridge.IgnitorDsl.Freq
import io.peekandpoke.klang.audio_bridge.abs
import io.peekandpoke.klang.audio_bridge.accelerate
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.bipolar
import io.peekandpoke.klang.audio_bridge.ceil
import io.peekandpoke.klang.audio_bridge.clamp
import io.peekandpoke.klang.audio_bridge.coarse
import io.peekandpoke.klang.audio_bridge.crush
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.drive
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.exp
import io.peekandpoke.klang.audio_bridge.floor
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.frac
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lerp
import io.peekandpoke.klang.audio_bridge.log
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.max
import io.peekandpoke.klang.audio_bridge.min
import io.peekandpoke.klang.audio_bridge.minus
import io.peekandpoke.klang.audio_bridge.mod
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.neg
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.audio_bridge.onepole
import io.peekandpoke.klang.audio_bridge.optimizer
import io.peekandpoke.klang.audio_bridge.phaser
import io.peekandpoke.klang.audio_bridge.pitchMod
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.pow
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.recip
import io.peekandpoke.klang.audio_bridge.round
import io.peekandpoke.klang.audio_bridge.select
import io.peekandpoke.klang.audio_bridge.shape
import io.peekandpoke.klang.audio_bridge.shimmer
import io.peekandpoke.klang.audio_bridge.sign
import io.peekandpoke.klang.audio_bridge.sq
import io.peekandpoke.klang.audio_bridge.sqrt
import io.peekandpoke.klang.audio_bridge.tanh
import io.peekandpoke.klang.audio_bridge.tap
import io.peekandpoke.klang.audio_bridge.tremolo
import io.peekandpoke.klang.audio_bridge.unipolar
import io.peekandpoke.klang.audio_bridge.vibrato

/**
 * The ignitor VOCABULARY the warmup plays: synthetic graphs that together touch every `IgnitorDsl`
 * node kind, so the first note of a song's own instrument does not JIT that kind in a live frame.
 *
 * Why: with the warehouse stocked, the first run of Der Schmetterling still spiked and hiccuped
 * when its voices came in, and the second run did not (maintainer, Fairphone, 2026-09-04). The
 * count-in was fine; the voices are custom ignitors composed of node kinds the warmup had never
 * executed (`eq`, `seg`, `range`, `pitchEnvelope`, `crackle`, `unison` …). Each such kind's first
 * blocks run cold, on the audio thread, and on a phone that is the hiccup. The warmup's job is
 * therefore not only to build things but to have RUN every path a song can take.
 *
 * Explicit and synthetic, not a builtin song (maintainer). Every graph is finite by construction
 * (`log` of `abs + 1`, `recip` of `+ 2`, `sqrt` of `abs`, `pow` of a clamped base), because a
 * NaN in the warmup mix would be a NaN in the master post chain. Output is silenced by the host
 * regardless. The guard that this list stays complete is `WarmupVocabularySpec`: an exhaustive
 * `when` over every `IgnitorDsl` kind, so adding a kind without deciding whether it is warmed
 * does not compile.
 */
object WarmupVocabulary {

    /** Reserved name prefix — no real song can use these. */
    const val NAME_PREFIX = "--warm-"

    private fun sine(mul: Double = 1.0): IgnitorDsl = IgnitorDsl.Sine(freq = Freq.mul(Constant(mul)))

    /** Every plain oscillator, mixed at a tenth each. */
    val waves: IgnitorDsl = listOf<IgnitorDsl>(
        IgnitorDsl.Sine(),
        IgnitorDsl.Sawtooth(),
        IgnitorDsl.Square(),
        IgnitorDsl.Triangle(),
        IgnitorDsl.Zawtooth(),
        IgnitorDsl.Zamp(),
        IgnitorDsl.Impulse(),
        IgnitorDsl.Pulze(),
        IgnitorDsl.RawPulze(),
        IgnitorDsl.Ramp(),
        IgnitorDsl.Silence,
    ).reduce { acc, osc -> IgnitorDsl.Plus(acc, osc) }.mul(Constant(0.1))

    /** The unison families and the plucks — the phase pools and the Karplus paths. */
    val supers: IgnitorDsl = listOf<IgnitorDsl>(
        IgnitorDsl.SuperSaw(voices = Constant(7.0), spread = Constant(0.3)),
        IgnitorDsl.SuperSine(voices = Constant(5.0)),
        IgnitorDsl.SuperSquare(voices = Constant(5.0)),
        IgnitorDsl.SuperTri(voices = Constant(5.0)),
        IgnitorDsl.SuperRamp(voices = Constant(5.0)),
        IgnitorDsl.Sine(harmonics = Constant(7.0), octaves = Constant(2.0), suboctaves = Constant(1.0)),
        IgnitorDsl.Pluck(),
        IgnitorDsl.SuperPluck(voices = Constant(3.0)),
    ).reduce { acc, osc -> IgnitorDsl.Plus(acc, osc) }.mul(Constant(0.15))

    /** Every noise and chaos generator. */
    val noises: IgnitorDsl = listOf<IgnitorDsl>(
        IgnitorDsl.WhiteNoise(),
        IgnitorDsl.BrownNoise(),
        IgnitorDsl.PinkNoise(),
        IgnitorDsl.PerlinNoise(),
        IgnitorDsl.BerlinNoise(),
        IgnitorDsl.Dust(density = Constant(200.0)),
        IgnitorDsl.Crackle(),
    ).reduce { acc, osc -> IgnitorDsl.Plus(acc, osc) }.mul(Constant(0.15))

    /** Every arithmetic and shaping node, on a sine, kept finite at every step. */
    val math: IgnitorDsl = run {
        val s = sine()
        val t = sine(1.5)
        val lfo = IgnitorDsl.Sine(freq = Constant(2.0)).unipolar()
        val a = s.mul(Constant(0.5)).div(Constant(2.0)).minus(t.mul(Constant(0.1))).neg().abs()
        // `max(t).min(-t)` bounds to [-t, t]: cap first, then floor. Builds Max(Min(x, t), -t),
        // the same node pair as before the min/max doors became clamps.
        val b = a.clamp(Constant(-1.0), Constant(1.0)).pow(Constant(2.0)).max(t).min(t.neg())
        val c = b.exp().log().sqrt().sign().mul(b.tanh()).lerp(t, lfo).range(Constant(-0.5), Constant(0.5))
        val d = c.bipolar().unipolar().mul(Constant(4.0)).floor().mul(Constant(0.1))
            .mul(t.ceil().round().mul(Constant(0.1)).mul(t.frac()))
            .mod(Constant(0.5)).mul(t.sq().plus(Constant(2.0)).recip().mul(Constant(0.5))) // recip of `+2`: never near zero
        val chosen = lfo.select(whenTrue = d, whenFalse = c)
        val variants = IgnitorDsl.Variants(listOf(chosen, s))
        val withParam = variants.mul(IgnitorDsl.Param(name = "warm", default = 0.5))
        withParam.detune(7.0).optimizer(on = 1).mul(Constant(0.5))
    }

    /**
     * Every filter node and every Eq section kind, in one chain — TWICE: the optimizer fuses a
     * plain `lowpass/highpass/bandpass/notch` into `Eq` sections only when `analog` is a literal
     * zero, so the first pass runs the fused Eq path and the second (`analog = 1.0`, the
     * state-dependent damping branch) runs the standalone SVF nodes — the branch Der Schmetterling's
     * guitar takes with its `analog` param (review round 3).
     */
    val filters: IgnitorDsl = IgnitorDsl.Sawtooth()
        .lowpass(freq = 3000.0, q = 1.2, passes = 2)
        .highpass(freq = 80.0, q = 0.9)
        .onepole(freq = 6000.0)
        .bandpass(freq = 1200.0, q = 2.0)
        .notch(freq = 900.0, q = 4.0)
        .lowpass(freq = 4000.0, q = 1.0, analog = 1.0)
        .highpass(freq = 60.0, q = 0.8, analog = 1.0)
        .bandpass(freq = 1500.0, q = 1.5, analog = 1.0)
        .notch(freq = 700.0, q = 3.0, analog = 1.0)
        .eq()
        .band(freq = 400.0, q = 1.0, db = 3.0) // EqSection.Bell
        .tap(freq = 2500.0, q = 3.0, gain = 0.5) // EqSection.RawTap
        .let { eq ->
            eq.copy(
                sections = eq.sections +
                    IgnitorDsl.EqSection.Lowpass(freq = Constant(5000.0)) +
                    IgnitorDsl.EqSection.Highpass(freq = Constant(60.0)) +
                    IgnitorDsl.EqSection.Bandpass(freq = Constant(1000.0)) +
                    IgnitorDsl.EqSection.Notch(freq = Constant(700.0)),
            )
        }
        .mul(Constant(0.5))

    /** Every envelope, modulation and effect node. */
    val effects: IgnitorDsl = IgnitorDsl.Sine()
        .fm(modulator = IgnitorDsl.Sine(), ratio = 2.0, depth = 0.3)
        .adsr(0.005, 0.1, 0.6, 0.2)
        .drive(0.4)
        .shape("soft")
        .distort(0.5, "hard", oversample = 4) // = Drive + Shape (oversampled)
        .let { IgnitorDsl.Distort(inner = it, amount = Constant(0.3), shape = "soft", oversample = 2) } // the fused node, as the wire carries it
        .crush(6.0)
        .coarse(3.0)
        .phaser(rate = 0.7, center = 800.0)
        .tremolo(rate = 4.0, depth = 0.4)
        .shimmer()
        .vibrato(rate = 5.0, semitones = 0.2)
        .accelerate(1.0)
        .pitchMod(IgnitorDsl.Sine(freq = Constant(3.0)).mul(Constant(0.1)))
        .let { IgnitorDsl.PitchEnvelope(inner = it, semitones = Constant(12.0), decaySec = Constant(0.1)) }
        .mul(Constant(0.4))

    /** name → graph. The warmup registers each on its own playback and rotates its orbits through them. */
    val sounds: List<Pair<String, IgnitorDsl>> = listOf(
        "${NAME_PREFIX}waves--" to waves,
        "${NAME_PREFIX}supers--" to supers,
        "${NAME_PREFIX}noises--" to noises,
        "${NAME_PREFIX}math--" to math,
        "${NAME_PREFIX}filters--" to filters,
        "${NAME_PREFIX}effects--" to effects,
    )
}
