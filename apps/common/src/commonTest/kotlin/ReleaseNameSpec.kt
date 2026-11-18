/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ReleaseNameSpec : StringSpec({

    "a clean release is named by its version and its revision, as the deploy names its folder; its date does not count" {
        releaseName(version = "0.8.15", gitRev = "6c62fb63", gitDesc = "v0.8.13-70-g6c62fb63e", date = "2026-11-18 17:29:11") shouldBe
            "v0.8.15-6c62fb63"
    }

    "a build from a tree with uncommitted changes says so, with the build's time in digits" {
        releaseName(version = "0.8.15", gitRev = "00a5ff2c", gitDesc = "v0.8.13-71-g00a5ff2c5-dirty", date = "2026-11-18 17:29:11") shouldBe
            "v0.8.15-00a5ff2c-dirty-20261118-172911"
    }

    "two dirty builds at one commit are two releases" {
        val first = releaseName(version = "1.0", gitRev = "abc", gitDesc = "g-dirty", date = "2026-11-18 17:29:11")
        val second = releaseName(version = "1.0", gitRev = "abc", gitDesc = "g-dirty", date = "2026-11-18 17:30:02")

        first shouldNotBe second
    }

    "a dirty build without a date is still named dirty" {
        releaseName(version = "1.0", gitRev = "abc", gitDesc = "g-dirty", date = "") shouldBe "v1.0-abc-dirty"
    }

    "only the end of the description counts" {
        releaseName(version = "1.0", gitRev = "abc", gitDesc = "dirty-branch-1-gabc", date = "2026-11-18 17:29:11") shouldBe "v1.0-abc"
    }
})
