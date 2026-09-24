/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.phaser
import io.peekandpoke.klang.audio_bridge.shimmer
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * C4.2 guard (docs/plans/filter-unification.md): the shared wet knob has ONE name per door —
 * prefixed `xxxWet`/`xxxFloor` on sprudel (fields on one unordered voice); on the ignitor `wet`
 * is the FIRST door parameter and `floor` a builder knob (phase 3 step 3d(i)). These rows pin
 * that every renamed surface still writes the
 * SAME wire field (wire names deliberately keep their old spelling; the reverb's follow its door word since
 * 2026-09-16, `reverb` and `reverbSize`), that the new
 * `phaserFloor` reaches the wire, and that the ignitor knobs land on the node.
 */
class LangWetKnobSpec : StringSpec({

    fun firstData(p: SprudelPattern?): SprudelVoiceData =
        (p ?: error("no pattern")).queryArc(0.0, 1.0).first().data

    "sprudel Kotlin door: wet knobs write the (unchanged) wire fields" {
        firstData(note("c").reverb(0.4)).katalystParams?.get("reverb.wet") shouldBe 0.4
        firstData(note("c").delay(0.3)).katalystParams?.get("delay.wet") shouldBe 0.3
        firstData(note("c").phaser(wet = 0.8)).phaserDepth shouldBe 0.8
        firstData(note("c").phaser(floor = 0.3)).phaserFloor shouldBe 0.3
        firstData(note("c").body(wet = 0.5)).bodyMix shouldBe 0.5
        firstData(note("c").vowel(wet = 0.6)).vowelMix shouldBe 0.6
    }

    "sprudel script door: wet knobs dispatch and write the same fields" {
        firstData(SprudelPattern.compile("""note("c").reverb(0.4)""")).katalystParams?.get("reverb.wet") shouldBe 0.4
        firstData(SprudelPattern.compile("""note("c").delay(0.3)""")).katalystParams?.get("delay.wet") shouldBe 0.3
        firstData(SprudelPattern.compile("""note("c").phaser(wet = 0.8)""")).phaserDepth shouldBe 0.8
        firstData(SprudelPattern.compile("""note("c").phaser(floor = 0.3)""")).phaserFloor shouldBe 0.3
        firstData(SprudelPattern.compile("""note("c").body(wet = 0.5)""")).bodyMix shouldBe 0.5
        firstData(SprudelPattern.compile("""note("c").vowel(wet = 0.6)""")).vowelMix shouldBe 0.6
    }

    "compound heads: wet is the FIRST positional slot of every door that has one (step 3d(iii))" {
        val reverb = firstData(note("c").reverb(0.4, 5.0))
        reverb.katalystParams?.get("reverb.wet") shouldBe 0.4
        reverb.katalystParams?.get("reverb.size") shouldBe 5.0

        val delay = firstData(note("c").delay(0.5, 0.25, 0.6))
        delay.katalystParams?.get("delay.wet") shouldBe 0.5
        delay.katalystParams?.get("delay.time") shouldBe 0.25
        delay.katalystParams?.get("delay.feedback") shouldBe 0.6

        val phaser = firstData(note("c").phaser(0.8, 2.0))
        phaser.phaserRate shouldBe 2.0
        phaser.phaserDepth shouldBe 0.8

        val body = firstData(note("c").body(0.7, "wood"))
        body.bodyMix shouldBe 0.7
        body.body shouldBe "wood"

        val vowel = firstData(note("c").vowel(0.6, "a"))
        vowel.vowelMix shouldBe 0.6
        vowel.vowel shouldBe "a"
    }

    "phaserFloor takes the engine's additive default 1.0 unless set" {
        // It used to stay null and the engine substituted PHASER_FLOOR; since Katalyst step 5a-3
        // the door writes the same constant when any phaser knob names the stage, so the additive
        // law still rules and one place decides it. A value the author gave is untouched.
        firstData(note("c").phaser(wet = 0.5)).phaserFloor shouldBe PHASER_FLOOR
        firstData(note("c").phaser(wet = 0.5, floor = 0.25)).phaserFloor shouldBe 0.25
    }

    "phaserFloor crosses the WIRE boundary (toVoiceData): set passes through, unnamed takes PHASER_FLOOR" {
        // The sprudel accessor rows above stop BEFORE the wire; a dropped mapping line in
        // toVoiceData() would make phaser(floor = ...) a silent no-op in real playback while
        // every accessor row stays green.
        firstData(note("c").phaser(floor = 0.25)).toVoiceData().phaserFloor shouldBe 0.25
        firstData(note("c").phaser(wet = 0.5)).toVoiceData().phaserFloor shouldBe PHASER_FLOOR
    }

    "ignitor Kotlin door: wet is the first door parameter, floor a field of the node" {
        val p = IgnitorDsl.Sine().phaser(0.25, 1.0).copy(floor = IgnitorDsl.Constant(0.1))
        p.wet shouldBe IgnitorDsl.Constant(0.25)
        p.rate shouldBe IgnitorDsl.Constant(1.0)
        p.floor shouldBe IgnitorDsl.Constant(0.1)

        val sh = IgnitorDsl.Sine().shimmer(0.3).copy(floor = IgnitorDsl.Constant(0.2))
        sh.wet shouldBe IgnitorDsl.Constant(0.3)
        sh.floor shouldBe IgnitorDsl.Constant(0.2)
    }

    "ignitor node defaults: wet 0.5, floor 0.0 on both effects" {
        val p = IgnitorDsl.Phaser(inner = IgnitorDsl.Sine())
        p.wet shouldBe IgnitorDsl.Constant(0.5)
        p.floor shouldBe IgnitorDsl.Constant(0.0)

        val sh = IgnitorDsl.Sine().shimmer()
        sh.wet shouldBe IgnitorDsl.Constant(0.5)
        sh.floor shouldBe IgnitorDsl.Constant(0.0)
    }

    "ignitor script door: wet on the door, floor() a knob on the configure builder" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        fun eval(code: String): Any? = engine.execute(code).toObjectOrNull<Any>()

        val p = eval("""Osc.saw().phaser(0.25, 1.0, x => x.floor(0.1))""") as IgnitorDsl.Phaser
        p.wet shouldBe IgnitorDsl.Constant(0.25)
        p.floor shouldBe IgnitorDsl.Constant(0.1)

        val sh = eval("""Osc.saw().shimmer(0.3)""") as IgnitorDsl.Shimmer
        sh.wet shouldBe IgnitorDsl.Constant(0.3)
    }
})
