/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.soundName

class LangSndPluckSpec : StringSpec({

    // -- no params ---------------------------------------------------------------------------------------------------

    "sndPluck() with no params sets sound to pluck" {
        val p = note("c3").sndPluck()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "pluck"
        events[0].data.ignitorParams?.get("decay") shouldBe null
    }

    // -- single param ------------------------------------------------------------------------------------------------

    "sndPluck(\"0.99\") sets decay only" {
        val p = note("c3").sndPluck(0.99)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "pluck"
        events[0].data.ignitorParams?.get("decay") shouldBe 0.99
        events[0].data.ignitorParams?.get("brightness") shouldBe null
    }
})
