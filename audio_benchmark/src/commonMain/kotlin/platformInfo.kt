/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

/** Returns a human-readable platform identifier for benchmark headers (e.g., "JVM 21 / Linux", "Chrome 120 / macOS"). */
expect fun platformInfo(): String

/**
 * Optional case filter, `KLANG_BENCH_FILTER` in the environment: when set, only the ignitor cases whose
 * name contains it run, and the other benchmark families are skipped. Null when unset or blank.
 */
expect fun benchmarkCaseFilter(): String?
