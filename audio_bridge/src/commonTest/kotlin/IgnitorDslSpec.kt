/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Platform-agnostic `IgnitorDsl` logic: builder factory shapes + tree walks (`collectParams`, `maxReleaseSec`).
 *
 * Wire round-trips live in the JS-only `IgnitorDslWireCodecSpec` (the worklet codec uses `dynamic`); these are
 * pure data/logic and stay in commonTest.
 */
class IgnitorDslSpec : StringSpec({

    "mul builder produces Times" {
        IgnitorDsl.Sine().mul(IgnitorDsl.Param("factor", 0.5)).shouldBeInstanceOf<IgnitorDsl.Times>()
    }

    "distort builder produces Clip(Drive(...))" {
        val dsl = IgnitorDsl.Sine().distort(0.5)
        val clip = dsl.shouldBeInstanceOf<IgnitorDsl.Clip>()
        clip.inner.shouldBeInstanceOf<IgnitorDsl.Drive>()
    }

    "eq extension wraps and is idempotent" {
        val wrapped = IgnitorDsl.Sine().eq()
        wrapped.sections shouldBe emptyList()
        wrapped.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        // Idempotent: an existing Eq is returned as the SAME instance, not re-wrapped.
        (wrapped.eq() === wrapped) shouldBe true
    }

    "the Bell wire default and the band() surface default are the SAME q" {
        // Parameter-parity: one bell, one omitted-field sound. These drifted apart once
        // already (wire ctor 1.0 vs surface 0.707), which is the roomSize-10x bug class:
        // Kotlin code building EqSection.Bell(...) directly and a song writing .band(...)
        // would have produced two different bandwidths from identical-looking source.
        // The Kotlin and script surfaces are pinned to each other by StdLibOscTest.
        val fromWireDefault = IgnitorDsl.EqSection.Bell(freqHz = IgnitorDsl.Constant(1200.0))
        val fromSurface = IgnitorDsl.Sine().eq().band(1200.0).sections.single()

        (fromWireDefault.q as IgnitorDsl.Constant).value shouldBe
                ((fromSurface as IgnitorDsl.EqSection.Bell).q as IgnitorDsl.Constant).value
    }

    "the RawTap wire defaults and the tap() surface defaults are the SAME" {
        // Same bug class as the Bell row above, pinned before it can happen.
        val fromWireDefault = IgnitorDsl.EqSection.RawTap(freqHz = IgnitorDsl.Constant(850.0))
        val fromSurface = IgnitorDsl.Sine().eq().tap(850.0).sections.single()
                as IgnitorDsl.EqSection.RawTap

        (fromWireDefault.q as IgnitorDsl.Constant).value shouldBe
                (fromSurface.q as IgnitorDsl.Constant).value
        (fromWireDefault.gain as IgnitorDsl.Constant).value shouldBe
                (fromSurface.gain as IgnitorDsl.Constant).value
    }

    "the DSL-typed and scalar overloads carry the SAME defaults" {
        // Each method ships two overloads, each with its own default literals. The rows below
        // exercise only the scalar ones, so without this the DSL-typed copy could drift alone
        // and every other parity row would stay green (the roomSize-10x class again).
        val bandDsl = IgnitorDsl.Sine().eq().band(IgnitorDsl.Constant(1200.0)).sections.single()
                as IgnitorDsl.EqSection.Bell
        val bandScalar = IgnitorDsl.Sine().eq().band(1200.0).sections.single()
                as IgnitorDsl.EqSection.Bell
        (bandDsl.q as IgnitorDsl.Constant).value shouldBe (bandScalar.q as IgnitorDsl.Constant).value
        (bandDsl.db as IgnitorDsl.Constant).value shouldBe (bandScalar.db as IgnitorDsl.Constant).value

        val tapDsl = IgnitorDsl.Sine().eq().tap(IgnitorDsl.Constant(850.0)).sections.single()
                as IgnitorDsl.EqSection.RawTap
        val tapScalar = IgnitorDsl.Sine().eq().tap(850.0).sections.single()
                as IgnitorDsl.EqSection.RawTap
        (tapDsl.q as IgnitorDsl.Constant).value shouldBe (tapScalar.q as IgnitorDsl.Constant).value
        (tapDsl.gain as IgnitorDsl.Constant).value shouldBe (tapScalar.gain as IgnitorDsl.Constant).value
    }

    "band extension adds a bell with stdlib-matching defaults" {
        // band() is receiver-typed on IgnitorDsl.Eq — .eq() is the entry point; a plain
        // oscillator has no .band() (compile error, the supersaw config-method pattern).
        val dsl = IgnitorDsl.Sine().eq().band(1200.0)
        val bell = dsl.sections.single().shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
        (bell.freqHz as IgnitorDsl.Constant).value shouldBe 1200.0
        (bell.q as IgnitorDsl.Constant).value shouldBe 0.707
        (bell.db as IgnitorDsl.Constant).value shouldBe 0.0
    }

    "band extension appends in list order" {
        val dsl = IgnitorDsl.Sine().eq().band(300.0, q = 1.0, db = 6.0).band(2500.0)
        dsl.sections.size shouldBe 2
        ((dsl.sections[0] as IgnitorDsl.EqSection.Bell).freqHz as IgnitorDsl.Constant).value shouldBe 300.0
        ((dsl.sections[0] as IgnitorDsl.EqSection.Bell).db as IgnitorDsl.Constant).value shouldBe 6.0
        ((dsl.sections[1] as IgnitorDsl.EqSection.Bell).freqHz as IgnitorDsl.Constant).value shouldBe 2500.0
    }

    "tap extension adds a RawTap with stdlib-matching defaults" {
        val dsl = IgnitorDsl.Sawtooth().eq().tap(850.0)
        val tap = dsl.sections.single().shouldBeInstanceOf<IgnitorDsl.EqSection.RawTap>()
        (tap.freqHz as IgnitorDsl.Constant).value shouldBe 850.0
        (tap.q as IgnitorDsl.Constant).value shouldBe 1.0
        (tap.gain as IgnitorDsl.Constant).value shouldBe 1.0
    }

    "tap and band append into ONE ordered section list" {
        val dsl = IgnitorDsl.Sawtooth().eq().tap(850.0, 0.707, 1.7).band(4000.0, 0.7, -3.0)
        dsl.sections.size shouldBe 2
        dsl.sections[0].shouldBeInstanceOf<IgnitorDsl.EqSection.RawTap>()
        dsl.sections[1].shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
    }

    "band extension accepts IgnitorDsl params (note tracking)" {
        val dsl = IgnitorDsl.Sawtooth().eq().band(freq = IgnitorDsl.Freq)
        (dsl.sections.single() as IgnitorDsl.EqSection.Bell).freqHz.shouldBeInstanceOf<IgnitorDsl.Freq>()
    }

    "Variants.collectParams unions over children" {
        // analog is given an explicit Constant so the oscillators' default Slots.analog Param leaf
        // doesn't enter the union — this test is about the freq Params.
        val dsl = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine(freq = IgnitorDsl.Param("a", 440.0), analog = IgnitorDsl.Constant(0.0)),
                IgnitorDsl.Sawtooth(freq = IgnitorDsl.Param("b", 220.0), analog = IgnitorDsl.Constant(0.0)),
            )
        )
        val params = mutableListOf<IgnitorDsl.Param>()
        dsl.collectParams(params)
        params.map { it.name } shouldBe listOf("a", "b")
    }

    "Variants.maxReleaseSec takes the max across children" {
        val dsl = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine().adsr(0.01, 0.1, 0.5, 0.2),
                IgnitorDsl.Sawtooth().adsr(0.01, 0.1, 0.5, 1.5),
                IgnitorDsl.Square().adsr(0.01, 0.1, 0.5, 0.8),
            )
        )
        dsl.maxReleaseSec() shouldBe 1.5
    }

    "Variants.maxReleaseSec returns 0 for empty children" {
        IgnitorDsl.Variants(emptyList()).maxReleaseSec() shouldBe 0.0
    }

    "getParamSlots returns exactly the Params collectParams gathers" {
        val dsl = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine(freq = IgnitorDsl.Param("a", 440.0), analog = IgnitorDsl.Constant(0.0)),
                IgnitorDsl.Sawtooth(freq = IgnitorDsl.Param("b", 220.0), analog = IgnitorDsl.Constant(0.0)),
            )
        )
        val viaCollect = mutableListOf<IgnitorDsl.Param>().also { dsl.collectParams(it) }
        dsl.getParamSlots() shouldBe viaCollect
        dsl.getParamSlots().map { it.name } shouldBe listOf("a", "b")
    }

    "getParamSlots is empty when the tree has no Param leaves" {
        // analog defaults to the Slots.analog Param, so pin it to a Constant to get a Param-free tree.
        IgnitorDsl.Sine(freq = IgnitorDsl.Constant(440.0), analog = IgnitorDsl.Constant(0.0))
            .getParamSlots() shouldBe emptyList()
    }
})
