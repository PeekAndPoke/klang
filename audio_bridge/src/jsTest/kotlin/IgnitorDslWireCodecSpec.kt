/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.wire.decode_IgnitorDsl
import io.peekandpoke.klang.audio_bridge.wire.encode_IgnitorDsl

/**
 * Round-trip guard for the `IgnitorDsl` subgraph through the KSP-generated worklet wire codec (JS-only).
 *
 * `IgnitorDsl` is the deepest/widest sealed tree on the wire (osc primitives, noise, arithmetic combinators,
 * filters, envelopes, FM, effects, pitch mods, recursive composites). `decode_IgnitorDsl(encode_IgnitorDsl(x))
 * == x` over a representative-per-family corpus pins that every leaf's `@WireName` tag + fields survive the
 * boundary. Data classes give structural `==`. Replaces the old kotlinx-JSON round-trip test (the worklet
 * never used kotlinx; this exercises the actual runtime codec).
 */
class IgnitorDslWireCodecSpec : StringSpec({

    fun roundTrip(dsl: IgnitorDsl): IgnitorDsl = decode_IgnitorDsl(encode_IgnitorDsl(dsl))

    fun check(dsl: IgnitorDsl) {
        roundTrip(dsl) shouldBe dsl
    }

    // --- parameter slots / special leaves -------------------------------------------------------------------
    "Freq" { check(IgnitorDsl.Freq) }
    "Silence" { check(IgnitorDsl.Silence) }
    "Constant" { check(IgnitorDsl.Constant(42.0)) }
    "Param (with description)" { check(IgnitorDsl.Param("cutoff", 1000.0, "Filter cutoff")) }

    // --- oscillator primitives ------------------------------------------------------------------------------
    "Sine default Freq" { check(IgnitorDsl.Sine()) }
    "Sine custom freq param" { check(IgnitorDsl.Sine(freq = IgnitorDsl.Param("freq", 440.0))) }
    "Sine with Freq.div(2)" { check(IgnitorDsl.Sine(freq = IgnitorDsl.Div(IgnitorDsl.Freq, IgnitorDsl.Constant(2.0)))) }
    "Sawtooth" { check(IgnitorDsl.Sawtooth()) }
    "Square" { check(IgnitorDsl.Square(freq = IgnitorDsl.Param("freq", 220.0))) }
    "Triangle" { check(IgnitorDsl.Triangle()) }
    "Ramp" { check(IgnitorDsl.Ramp()) }
    "Zawtooth" { check(IgnitorDsl.Zawtooth()) }
    "Zamp" { check(IgnitorDsl.Zamp()) }
    "Impulse" { check(IgnitorDsl.Impulse()) }
    "Pulze" { check(IgnitorDsl.Pulze()) }
    "RawPulze" { check(IgnitorDsl.RawPulze()) }

    // --- super oscillators ----------------------------------------------------------------------------------
    "SuperSaw" { check(IgnitorDsl.SuperSaw(freq = IgnitorDsl.Constant(5.0))) }
    "SuperSine" { check(IgnitorDsl.SuperSine()) }
    "SuperSquare" { check(IgnitorDsl.SuperSquare()) }
    "SuperTri" { check(IgnitorDsl.SuperTri()) }
    "SuperRamp" { check(IgnitorDsl.SuperRamp()) }

    // --- noise sources --------------------------------------------------------------------------------------
    "WhiteNoise" { check(IgnitorDsl.WhiteNoise()) }
    "BrownNoise" { check(IgnitorDsl.BrownNoise()) }
    "PinkNoise" { check(IgnitorDsl.PinkNoise()) }
    "PerlinNoise" { check(IgnitorDsl.PerlinNoise()) }
    "BerlinNoise" { check(IgnitorDsl.BerlinNoise()) }
    "Dust" { check(IgnitorDsl.Dust()) }
    "Crackle" { check(IgnitorDsl.Crackle()) }

    // --- physical models ------------------------------------------------------------------------------------
    "Pluck" { check(IgnitorDsl.Pluck()) }
    "SuperPluck" { check(IgnitorDsl.SuperPluck()) }

    // --- arithmetic / math ----------------------------------------------------------------------------------
    "Plus" { check(IgnitorDsl.Sine() + IgnitorDsl.Sawtooth()) }
    "Times" { check(IgnitorDsl.Sine() * IgnitorDsl.Triangle()) }
    "Div" { check(IgnitorDsl.Sine().div(IgnitorDsl.Param("divisor", 2.0))) }
    "Minus" { check(IgnitorDsl.Sine().minus(IgnitorDsl.Sawtooth())) }
    "Neg" { check(IgnitorDsl.Sine().neg()) }
    "Abs" { check(IgnitorDsl.Sine().abs()) }
    "Pow" { check(IgnitorDsl.Sine().pow(IgnitorDsl.Constant(2.0))) }
    "Min" { check(IgnitorDsl.Sine().min(IgnitorDsl.Constant(0.5))) }
    "Max" { check(IgnitorDsl.Sine().max(IgnitorDsl.Constant(-0.5))) }
    "Clamp" { check(IgnitorDsl.Sine().clamp(IgnitorDsl.Constant(-0.5), IgnitorDsl.Constant(0.5))) }
    "Exp" { check(IgnitorDsl.Sine().exp()) }
    "Log" { check(IgnitorDsl.Sine().log()) }
    "Sqrt" { check(IgnitorDsl.Sine().sqrt()) }
    "Sign" { check(IgnitorDsl.Sine().sign()) }
    "Tanh" { check(IgnitorDsl.Sine().tanh()) }
    "Lerp" { check(IgnitorDsl.Sine().lerp(IgnitorDsl.Sawtooth(), IgnitorDsl.Constant(0.3))) }
    "Range" { check(IgnitorDsl.Sine().range(IgnitorDsl.Constant(0.5), IgnitorDsl.Constant(5.0))) }
    "Bipolar" { check(IgnitorDsl.Sine().bipolar()) }
    "Unipolar" { check(IgnitorDsl.Sine().unipolar()) }
    "Floor" { check(IgnitorDsl.Sine().floor()) }
    "Ceil" { check(IgnitorDsl.Sine().ceil()) }
    "Round" { check(IgnitorDsl.Sine().round()) }
    "Frac" { check(IgnitorDsl.Sine().frac()) }
    "Mod" { check(IgnitorDsl.Sine().mod(IgnitorDsl.Constant(0.5))) }
    "Recip" { check(IgnitorDsl.Sine().recip()) }
    "Sq" { check(IgnitorDsl.Sine().sq()) }
    "Select" { check(IgnitorDsl.Sine().select(IgnitorDsl.Constant(1.0), IgnitorDsl.Constant(-1.0))) }

    // --- frequency / filters --------------------------------------------------------------------------------
    "Detune" { check(IgnitorDsl.Sine().detune(7.0)) }
    "Lowpass" { check(IgnitorDsl.Square().lowpass(2000.0)) }
    "Highpass (custom q)" { check(IgnitorDsl.Sawtooth().highpass(500.0, 1.5)) }
    "OnePoleLowpass" { check(IgnitorDsl.Sawtooth().onePoleLowpass(3000.0)) }
    "Bandpass" { check(IgnitorDsl.Sine().bandpass(1000.0, 2.0)) }
    "Notch" { check(IgnitorDsl.Sine().notch(1000.0, 2.0)) }

    // --- envelope / FM --------------------------------------------------------------------------------------
    "Adsr" { check(IgnitorDsl.Sine().adsr(0.01, 0.3, 0.5, 0.5)) }
    "Adsr with curves" {
        check(
            IgnitorDsl.Adsr(
                inner = IgnitorDsl.SuperSaw(),
                attackSec = IgnitorDsl.Constant(0.02),
                attackCurve = AdsrCurve.Exponential,
                releaseCurve = AdsrCurve.Square,
            )
        )
    }
    "Fm" { check(IgnitorDsl.Sine().fm(IgnitorDsl.Sine(), ratio = 1.4, depth = 300.0, envDecaySec = 0.5)) }
    "Fm with Adsr" { check(IgnitorDsl.Sine().fm(IgnitorDsl.Sine(), ratio = 1.4, depth = 300.0).adsr(0.01, 0.3, 0.5, 0.5)) }

    // --- effects --------------------------------------------------------------------------------------------
    "Drive" { check(IgnitorDsl.Sine().drive(0.5)) }
    "Clip" { check(IgnitorDsl.Sine().clip("hard")) }
    "Distort (Drive+Clip chain)" { check(IgnitorDsl.Sine().distort(0.5)) }
    "Crush" { check(IgnitorDsl.Sine().crush(8.0)) }
    "Coarse" { check(IgnitorDsl.Sine().coarse(4.0)) }
    "Phaser" { check(IgnitorDsl.Sine().phaser(0.5, 0.5)) }
    "Tremolo" { check(IgnitorDsl.Sine().tremolo(5.0, 0.5)) }
    "Shimmer (pitches list)" { check(IgnitorDsl.Square().shimmer(pitches = listOf(0.0, 7.0, 12.0))) }

    // --- pitch modulation -----------------------------------------------------------------------------------
    "Vibrato" { check(IgnitorDsl.Sine().vibrato(5.0, 0.02)) }
    "Accelerate" { check(IgnitorDsl.Sine().accelerate(1.0)) }
    "PitchEnvelope" { check(IgnitorDsl.PitchEnvelope(inner = IgnitorDsl.Sine(), amount = IgnitorDsl.Constant(12.0))) }
    "PitchMod" { check(IgnitorDsl.Sine().pitchMod(IgnitorDsl.Sine())) }

    // --- dispatch / deep composites -------------------------------------------------------------------------
    "Variants (primitive children)" {
        check(IgnitorDsl.Variants(listOf(IgnitorDsl.Sine(), IgnitorDsl.Sawtooth(), IgnitorDsl.Square())))
    }
    "Variants (nested inside Variants)" {
        check(
            IgnitorDsl.Variants(
                listOf(
                    IgnitorDsl.Variants(listOf(IgnitorDsl.Sine(), IgnitorDsl.Square())),
                    IgnitorDsl.Sawtooth(),
                )
            )
        )
    }
    "deep composite (SuperSaw → lowpass → adsr)" {
        check(IgnitorDsl.SuperSaw(freq = IgnitorDsl.Constant(5.0)).lowpass(2000.0).adsr(0.01, 0.3, 0.5, 0.5))
    }
    "sgpad-style composite" {
        check(
            (IgnitorDsl.Sawtooth() + IgnitorDsl.Sawtooth().detune(0.1))
                .div(IgnitorDsl.Param("divisor", 2.0))
                .onePoleLowpass(3000.0)
        )
    }
})
