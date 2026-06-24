/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

/** Returns a human-readable platform identifier for benchmark headers (e.g., "JVM 21 / Linux", "Chrome 120 / macOS"). */
expect fun platformInfo(): String
