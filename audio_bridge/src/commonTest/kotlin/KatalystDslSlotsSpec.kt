/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR

/**
 * [KatalystDsl.Slots], the Kotlin door of `Katalyst.slot` (the Ignitor/Katalyst naming, plan section 4.3), and the
 * classic chain built FROM it.
 *
 * Two promises: the chain is unchanged by value (the literal below is the chain as it was written out by hand before
 * the slots existed, so the render stays bit-identical), and every knob of the chain IS the handle's param, the same
 * instance, so a handle a song names and the knob the chain reads cannot drift.
 */
class KatalystDslSlotsSpec : StringSpec({

    fun p(name: String, default: Double) = IgnitorDsl.Param(name = name, default = default)

    "classic is the chain it always was, by value" {
        KatalystDsl.classic shouldBe KatalystDsl(
            listOf(
                KatalystStageDsl.Body(
                    material = p("body.material", SLOT_UNSET), wet = p("body.wet", SLOT_UNSET), floor = p("body.floor", BODY_FLOOR),
                ),
                KatalystStageDsl.Vowel(
                    vowel = p("vowel.vowel", SLOT_UNSET), wet = p("vowel.wet", SLOT_UNSET), floor = p("vowel.floor", VOWEL_FLOOR),
                ),
                KatalystStageDsl.Delay(
                    wet = p("delay.wet", 0.0), time = p("delay.time", 0.0), feedback = p("delay.feedback", 0.0),
                    cap = p("delay.cap", DELAY_CAP),
                ),
                KatalystStageDsl.Reverb(
                    wet = p("reverb.wet", 0.0), size = p("reverb.size", 0.0), lowpass = p("reverb.lowpass", SLOT_UNSET),
                ),
                KatalystStageDsl.Phaser(
                    rate = p("phaser.rate", PHASER_RATE_HZ), wet = p("phaser.wet", 0.0), center = p("phaser.center", PHASER_CENTER_HZ),
                    sweep = p("phaser.sweep", PHASER_SWEEP_HZ), floor = p("phaser.floor", PHASER_FLOOR),
                ),
                KatalystStageDsl.Compressor(
                    threshold = p("compressor.threshold", SLOT_UNSET), ratio = p("compressor.ratio", SLOT_UNSET),
                    knee = p("compressor.knee", SLOT_UNSET), attack = p("compressor.attack", SLOT_UNSET),
                    release = p("compressor.release", SLOT_UNSET),
                ),
                KatalystStageDsl.Gain(gain = p("gain.gain", 1.0)),
                KatalystStageDsl.Duck(
                    orbit = p("duck.orbit", SLOT_UNSET), depth = p("duck.depth", 0.0), attack = p("duck.attack", DUCK_ATTACK_SECONDS),
                ),
            )
        )
    }

    "every knob of classic IS its handle's param, the same instance, all 27" {
        val s = KatalystDsl.Slots
        val stages = KatalystDsl.classic.stages
        val body = stages[0] as KatalystStageDsl.Body
        val vowel = stages[1] as KatalystStageDsl.Vowel
        val delay = stages[2] as KatalystStageDsl.Delay
        val reverb = stages[3] as KatalystStageDsl.Reverb
        val phaser = stages[4] as KatalystStageDsl.Phaser
        val comp = stages[5] as KatalystStageDsl.Compressor
        val gain = stages[6] as KatalystStageDsl.Gain
        val duck = stages[7] as KatalystStageDsl.Duck

        val pairs: List<Pair<IgnitorDsl?, KatalystParam>> = listOf(
            body.material to s.body.material, body.wet to s.body.wet, body.floor to s.body.floor,
            vowel.vowel to s.vowel.vowel, vowel.wet to s.vowel.wet, vowel.floor to s.vowel.floor,
            delay.wet to s.delay.wet, delay.time to s.delay.time, delay.feedback to s.delay.feedback, delay.cap to s.delay.cap,
            reverb.wet to s.reverb.wet, reverb.size to s.reverb.size, reverb.lowpass to s.reverb.lowpass,
            phaser.rate to s.phaser.rate, phaser.wet to s.phaser.wet, phaser.center to s.phaser.center,
            phaser.sweep to s.phaser.sweep, phaser.floor to s.phaser.floor,
            comp.threshold to s.compressor.threshold, comp.ratio to s.compressor.ratio, comp.knee to s.compressor.knee,
            comp.attack to s.compressor.attack, comp.release to s.compressor.release,
            gain.gain to s.gain.gain,
            duck.orbit to s.duck.orbit, duck.depth to s.duck.depth, duck.attack to s.duck.attack,
        )

        stages shouldHaveSize 8
        pairs shouldHaveSize 27
        pairs.map { it.second.name }.toSet() shouldHaveSize 27

        pairs.forEach { (knob, handle) ->
            withClue(handle.name) { knob shouldBeSameInstanceAs handle.param }
        }
    }
})
