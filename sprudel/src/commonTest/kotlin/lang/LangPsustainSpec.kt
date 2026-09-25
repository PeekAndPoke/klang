/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangPsustainSpec : StringSpec({

    "penv(sustain = ...) sets VoiceData.pSustain correctly" {
        val p = note("a b").penv(sustain = "0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pSustain } shouldBe listOf(0.5, 1.0)
    }

    "control pattern penv(sustain = ...) sets VoiceData.pSustain on existing pattern" {
        val base = note("c3 e3")
        val p = base.penv(sustain = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.pSustain } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "penv(sustain = ...) works as string extension" {
        val p = "c3".penv(sustain = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.pSustain shouldBe 0.5
    }

    "penv(sustain = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").penv(sustain = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pSustain } shouldBe listOf(0.5, 1.0)
    }

    "toVoiceData carries every pitch envelope slot and curve to the wire" {
        val vd = note("c").penv(12, 0.01, 0.2, 0.5, 0.3).penvCurves("linear", "scurve", "cube")
            .queryArc(0.0, 1.0)[0].data.toVoiceData()

        vd.pEnv shouldBe 12.0
        vd.pAttack shouldBe 0.01
        vd.pDecay shouldBe 0.2
        vd.pSustain shouldBe 0.5
        vd.pRelease shouldBe 0.3
        vd.pAttackCurve shouldBe AdsrCurve.Linear
        vd.pDecayCurve shouldBe AdsrCurve.SCurve
        vd.pReleaseCurve shouldBe AdsrCurve.Cube
    }
})
