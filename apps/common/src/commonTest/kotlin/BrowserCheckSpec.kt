/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class BrowserCheckSpec : StringSpec({

    "Chrome on a desktop computer goes on without a warning" {
        BrowserCheck.of(platformType = "desktop", browserName = "Chrome").warning.shouldBeNull()
    }

    "Edge, Firefox and Safari on a desktop computer are warned of as not Chrome" {
        BrowserCheck.of(platformType = "desktop", browserName = "Microsoft Edge").warning shouldBe BrowserCheck.NOT_CHROME
        BrowserCheck.of(platformType = "desktop", browserName = "Firefox").warning shouldBe BrowserCheck.NOT_CHROME
        BrowserCheck.of(platformType = "desktop", browserName = "Safari").warning shouldBe BrowserCheck.NOT_CHROME
    }

    "a name that says Chrome and Edg is Edge, not Chrome" {
        BrowserCheck.of(platformType = "desktop", browserName = "Chrome Edg").warning shouldBe BrowserCheck.NOT_CHROME
    }

    "a phone or a tablet is warned of as such, also with Chrome" {
        BrowserCheck.of(platformType = "mobile", browserName = "Chrome").warning shouldBe BrowserCheck.MOBILE
        BrowserCheck.of(platformType = "tablet", browserName = "Chrome").warning shouldBe BrowserCheck.MOBILE
        BrowserCheck.of(platformType = "mobile", browserName = "Safari").warning shouldBe BrowserCheck.MOBILE
    }
})
