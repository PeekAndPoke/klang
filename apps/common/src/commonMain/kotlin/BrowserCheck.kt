/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

/**
 * Whether the browser is one the apps are made for: Google Chrome on a desktop computer or a laptop (the maintainer,
 * 2026-11-18: "We only focus on Chrome for now"). Where it is not, the studio's start page and notstrom's start screen
 * show the [warning] before they go on, answered with [CONTINUE] or [NOT_NOW].
 */
data class BrowserCheck(
    /** A phone or a tablet. */
    val isMobile: Boolean,
    /** Google Chrome (not another browser built on it that says so in its name, as Edge does). */
    val isChrome: Boolean,
) {
    /** A warning: its [headline] and the [text] under it. */
    data class Warning(val headline: String, val text: String)

    /** What to show before going on; null where nothing is needed. A phone or a tablet is said first. */
    val warning: Warning?
        get() = when {
            isMobile -> MOBILE
            !isChrome -> NOT_CHROME
            else -> null
        }

    companion object {
        /** The warning on a phone or a tablet. */
        val MOBILE = Warning(
            headline = "A phone or a tablet",
            text = "This version only works reliably on desktop computers or laptops using Google Chrome.",
        )

        /** The warning in a browser other than Chrome. */
        val NOT_CHROME = Warning(
            headline = "Not Chrome",
            text = "Currently, only Google Chrome is well tested. Other browsers may have compatibility issues.",
        )

        /** The answer that goes on. */
        const val CONTINUE = "Continue anyway"

        /** The answer that does not. */
        const val NOT_NOW = "Not now"

        /**
         * The check for a browser as a browser detection names it: its [platformType] ("desktop", "mobile",
         * "tablet", ...) and its [browserName] ("Chrome", "Microsoft Edge", "Firefox", ...).
         */
        fun of(platformType: String, browserName: String): BrowserCheck {
            val name = browserName.lowercase()

            return BrowserCheck(
                isMobile = platformType == "mobile" || platformType == "tablet",
                isChrome = name.contains("chrome") && !name.contains("edg"),
            )
        }
    }
}
