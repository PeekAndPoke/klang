/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

private val defaultNowMs: () -> Double = KlangTime.create()::internalMsNow

/**
 * The single frontend↔backend clock offset — the FE mirror of the backend's one `BackendClock`.
 *
 * GLOBAL state: there is one backend clock, so there is one offset. Owned by [KlangPlayer], corrected
 * from the system-wide [KlangCommLink.Feedback.Diagnostics] stream, and READ (read-only) by every
 * `KlangPlaybackController` for UI-signal latency compensation.
 *
 * Previously this lived per-controller and was effectively dead: `Diagnostics` carry
 * `SYSTEM_PLAYBACK_ID`, so [KlangPlayer] consumed them and they never reached a controller's handler —
 * the offset stayed at its initial value and the UI drifted ahead of the music over time.
 *
 * `KlangTime` is epoch-consistent across instances (JS `Date.now()`-anchored, JVM singleton), so this
 * private clock agrees with the controllers' `klangTime` used for `startTimeMs`.
 */
internal class BackendClockSync(
    private val nowMs: () -> Double = defaultNowMs,
) {
    private var offsetMs: Double = 100.0
    private val largeDriftThresholdMs = 500.0

    /** Current FE→BE offset in seconds, applied to UI-signal timing. */
    val offsetSec: Double get() = offsetMs / 1000.0

    /** Fold a backend [KlangCommLink.Feedback.Diagnostics] into the offset estimate. */
    fun onDiagnostics(diagnostics: KlangCommLink.Feedback.Diagnostics) {
        val rawOffset = (diagnostics.backendNowMs - nowMs()) + diagnostics.outputLatencyMs
        val drift = abs(rawOffset - offsetMs)
        offsetMs = if (drift > largeDriftThresholdMs) {
            // Large clock discontinuity (hibernate, AudioContext suspension): snap immediately.
            rawOffset
        } else {
            // EMA α=0.05: ~1s convergence at 20 Hz; smooths message-transit jitter.
            offsetMs * 0.95 + rawOffset * 0.05
        }
    }
}
