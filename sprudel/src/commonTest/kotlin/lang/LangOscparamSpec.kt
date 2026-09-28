/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangOscparamSpec : StringSpec({

    "oscparam() with multiple keys on same pattern" {
        val p = note("c3").oscparam("key1", "0.3").oscparam("key2", "0.7")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.oscParams?.get("key1") shouldBe 0.3
            events[0].data.oscParams?.get("key2") shouldBe 0.7
        }
    }

    "oscparam() default is null when not set" {
        val p = s("supersaw")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.oscParams?.get("custom") shouldBe null
        }
    }
})
