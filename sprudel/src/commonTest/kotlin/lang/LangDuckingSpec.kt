/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangDuckingSpec : StringSpec({

    "ducking parameters merge correctly" {
        val p = note("c3")
            .duck(0)
            .duck(attack = 0.1)
            .duck(depth = 0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalystParams?.get("duck.orbit") shouldBe 0.0
        events[0].data.katalystParams?.get("duck.attack") shouldBe 0.1
        events[0].data.katalystParams?.get("duck.depth") shouldBe 0.8
    }

    "ducking slots transfer to VoiceData" {
        val p = note("c3")
            .duck(0)
            .duck(attack = 0.15)
            .duck(depth = 0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val voiceData = events[0].data.toVoiceData()

        voiceData.katalystParams?.get("duck.orbit") shouldBe 0.0
        voiceData.katalystParams?.get("duck.attack") shouldBe 0.15
        voiceData.katalystParams?.get("duck.depth") shouldBe 0.6
    }
})
