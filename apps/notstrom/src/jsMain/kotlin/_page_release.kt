/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.apps.common.releaseName
import kotlinx.browser.window

/**
 * This release's name ([releaseName]), from the build's `version.json` fields the page carries
 * (`window.__APP_VERSION__`, baked in by webpack.config.d/01-page.js); "dev" where the page carries none (a build
 * without git, a test).
 */
fun pageRelease(): String {
    val fields = window.asDynamic().__APP_VERSION__
    val version = fields?.version as? String
    val gitRev = fields?.gitRev as? String
    val gitDesc = fields?.gitDesc as? String ?: ""
    val date = fields?.date as? String ?: ""

    if (version.isNullOrBlank() || gitRev.isNullOrBlank() || gitRev == "n/a") {
        return "dev"
    }

    return releaseName(version = version, gitRev = gitRev, gitDesc = gitDesc, date = date)
}
