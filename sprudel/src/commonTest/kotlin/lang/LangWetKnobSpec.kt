/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.dryFloor
import io.peekandpoke.klang.audio_bridge.phaser
import io.peekandpoke.klang.audio_bridge.shimmer
import io.peekandpoke.klang.audio_bridge.wet
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * C4.2 guard (docs/plans/filter-unification.md): the shared wet knob has ONE name per door —
 * prefixed `xxxWet`/`xxxFloor` on sprudel (fields on one unordered voice), typed `.wet()` /
 * `.dryFloor()` on the ignitor. These rows pin that every renamed surface still writes the
 * SAME wire field (wire names deliberately keep their old spelling), that the new
 * `phaserFloor` reaches the wire, and that the ignitor knobs land on the node.
 */
class LangWetKnobSpec : StringSpec({

    fun firstData(p: SprudelPattern?): SprudelVoiceData =
        (p ?: error("no pattern")).queryArc(0.0, 1.0).first().data

    "sprudel Kotlin door: wet knobs write the (unchanged) wire fields" {
        firstData(note("c").room(0.4)).room shouldBe 0.4
        firstData(note("c").delay(0.3)).delay shouldBe 0.3
        firstData(note("c").phaser(wet = 0.8)).phaserDepth shouldBe 0.8
        firstData(note("c").phaser(floor = 0.3)).phaserFloor shouldBe 0.3
        firstData(note("c").bodyWet(0.5)).bodyMix shouldBe 0.5
        firstData(note("c").vowelWet(0.6)).vowelMix shouldBe 0.6
    }

    "sprudel script door: wet knobs dispatch and write the same fields" {
        firstData(SprudelPattern.compile("""note("c").room(0.4)""")).room shouldBe 0.4
        firstData(SprudelPattern.compile("""note("c").delay(0.3)""")).delay shouldBe 0.3
        firstData(SprudelPattern.compile("""note("c").phaser(wet = 0.8)""")).phaserDepth shouldBe 0.8
        firstData(SprudelPattern.compile("""note("c").phaser(floor = 0.3)""")).phaserFloor shouldBe 0.3
        firstData(SprudelPattern.compile("""note("c").bodyWet(0.5)""")).bodyMix shouldBe 0.5
        firstData(SprudelPattern.compile("""note("c").vowelWet(0.6)""")).vowelMix shouldBe 0.6
    }

    "compound heads: the wet slot stays the head's first/second slot" {
        val room = firstData(note("c").room(0.4, 5.0))
        room.room shouldBe 0.4
        room.roomSize shouldBe 5.0

        val delay = firstData(note("c").delay(0.5, 0.25, 0.6))
        delay.delay shouldBe 0.5
        delay.delayTime shouldBe 0.25
        delay.delayFeedback shouldBe 0.6

        val phaser = firstData(note("c").phaser(2.0, 0.8))
        phaser.phaserRate shouldBe 2.0
        phaser.phaserDepth shouldBe 0.8
    }

    "phaserFloor stays ABSENT unless set — the engine's additive default 1.0 must rule" {
        firstData(note("c").phaser(wet = 0.5)).phaserFloor shouldBe null
    }

    "phaserFloor crosses the WIRE boundary (toVoiceData) — set passes through, unset stays null" {
        // The sprudel accessor rows above stop BEFORE the wire; a dropped mapping line in
        // toVoiceData() would make phaser(floor = ...) a silent no-op in real playback while
        // every accessor row stays green.
        firstData(note("c").phaser(floor = 0.25)).toVoiceData().phaserFloor shouldBe 0.25
        firstData(note("c").phaser(wet = 0.5)).toVoiceData().phaserFloor shouldBe null
    }

    "ignitor Kotlin door: .wet()/.dryFloor() typed onto the node" {
        val p = IgnitorDsl.Sine().phaser(1.0).wet(0.25).dryFloor(0.1)
        p.wet shouldBe IgnitorDsl.Constant(0.25)
        p.dryFloor shouldBe IgnitorDsl.Constant(0.1)

        val sh = IgnitorDsl.Sine().shimmer().wet(0.3).dryFloor(0.2)
        sh.wet shouldBe IgnitorDsl.Constant(0.3)
        sh.dryFloor shouldBe IgnitorDsl.Constant(0.2)
    }

    "ignitor node defaults: wet 0.5, dryFloor 0.0 on both effects" {
        val p = IgnitorDsl.Sine().phaser(1.0)
        p.wet shouldBe IgnitorDsl.Constant(0.5)
        p.dryFloor shouldBe IgnitorDsl.Constant(0.0)

        val sh = IgnitorDsl.Sine().shimmer()
        sh.wet shouldBe IgnitorDsl.Constant(0.5)
        sh.dryFloor shouldBe IgnitorDsl.Constant(0.0)
    }

    "ignitor script door: wet()/dryFloor() are knobs on the configure builder" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        fun eval(code: String): Any? = engine.execute(code).toObjectOrNull<Any>()

        val p = eval("""Osc.saw().phaser(1.0, x => x.wet(0.25).dryFloor(0.1))""") as IgnitorDsl.Phaser
        p.wet shouldBe IgnitorDsl.Constant(0.25)
        p.dryFloor shouldBe IgnitorDsl.Constant(0.1)

        val sh = eval("""Osc.saw().shimmer(x => x.wet(0.3))""") as IgnitorDsl.Shimmer
        sh.wet shouldBe IgnitorDsl.Constant(0.3)
    }

    "ignitor script door: the old blend parameter is GONE from the builders" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        shouldThrowAny {
            engine.execute("""Osc.saw().phaser(1.0, blend = 0.5)""")
        }
        shouldThrowAny {
            engine.execute("""Osc.saw().shimmer(blend = 0.5)""")
        }
    }
})
