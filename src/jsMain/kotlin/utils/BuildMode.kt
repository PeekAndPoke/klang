/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.utils

/**
 * The build the page runs (KLANG-00407, D4): webpack writes its mode into `process.env.NODE_ENV` when it bundles, so a
 * development build reads "development". Where nothing wrote it (no webpack), the page counts as a release.
 */
object BuildMode {

    /** Whether this is a development build. */
    val isDevelopment: Boolean by lazy {
        try {
            js("process.env.NODE_ENV") == "development"
        } catch (_: Throwable) {
            // No `process` at all: nothing wrote the mode
            false
        }
    }
}
