/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.pan

/**
 * `pan` at the wire ([SprudelVoiceData.toVoiceData]): a non-finite value reads as UNSET (`/dsl-design` section 4), as
 * the gain does there ([WireGainFoldSpec]), so the wire stays sparse and the engine's own `?: 0.5` answers. A pattern
 * reaches a NaN through a string atom (`pan("NaN")`), `Math.sqrt(-1)` or arithmetic; the engine audit measured that
 * it put NaN on the wire and silenced the whole output while the voice sounded (audit L1-01). The engine guards its
 * own read too (`VoiceFactory`). Every finite value passes raw.
 */
class WirePanSpec : StringSpec({

    "an unset pan stays unset" {
        createSprudelVoiceData { }.toVoiceData().pan shouldBe null
    }

    "a non-finite pan reads as unset" {
        createSprudelVoiceData { pan = Double.NaN }.toVoiceData().pan shouldBe null
        createSprudelVoiceData { pan = Double.POSITIVE_INFINITY }.toVoiceData().pan shouldBe null
        createSprudelVoiceData { pan = Double.NEGATIVE_INFINITY }.toVoiceData().pan shouldBe null
    }

    "a finite pan crosses raw, outside 0 to 1 included" {
        createSprudelVoiceData { pan = 0.3 }.toVoiceData().pan shouldBe 0.3
        createSprudelVoiceData { pan = -1.0 }.toVoiceData().pan shouldBe -1.0
        createSprudelVoiceData { pan = 1.7 }.toVoiceData().pan shouldBe 1.7
    }

    "a pattern's NaN atom arrives unset" {
        val events = note("c3 e3").pan("NaN 0.25").queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.toVoiceData().pan } shouldBe listOf(null, 0.25)
    }
})
