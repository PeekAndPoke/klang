/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

actual fun platformInfo(): String {
    return try {
        val ua = js("navigator.userAgent") as? String ?: "unknown"
        "JS / $ua"
    } catch (_: Throwable) {
        "JS / Node.js"
    }
}
