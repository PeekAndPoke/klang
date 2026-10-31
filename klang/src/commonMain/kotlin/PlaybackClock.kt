/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * The backend's timeline as the frontend knows it (KLANG-00399, step 3a): the position the backend reported last,
 * counted in the frames it rendered ([KlangCommLink.Feedback.Diagnostics.backendNowMs]), plus the time passed since on
 * the frontend's own clock, up to a cap of a few report gaps.
 *
 * A backend that stutters renders fewer frames and reports less; one that stops (an audio thread starved, a laptop
 * asleep) reports nothing. This clock slows or stops with it instead of running away: it stops at most [capSec] past
 * the last report (up to [MAX_CAP_SEC]). It never goes backwards: when a report is behind what it already said, it
 * holds until the backend has caught up.
 *
 * Before the first report it reads the frontend's clock: both start out on the wall clock, the backend at its first
 * block (`KlangAudioWorklet`, `JvmAudioBackend`). Such a reading is a guess and holds nothing back: the first report
 * replaces it. One per player, like the backend's one timeline; every playback reads it, from several threads on the
 * JVM, so a lock guards it. It replaces `BackendClockSync`, which compared the backend's wall clock with the
 * frontend's and so never saw the backend fall behind.
 */
internal class PlaybackClock(
    /** The frontend's clock in milliseconds: the time since a report, and the guess before the first one. */
    private val localMs: () -> Double = KlangTime.create()::internalMsNow,
) {
    companion object {
        /** The clock runs on past a report by [CAP_GAPS] of the longest recent report gap, at least this. */
        const val MIN_CAP_SEC = 0.1

        /** ... and at most this: a main-thread stall that delayed the reports must not leave a long cap behind. */
        const val MAX_CAP_SEC = 0.5

        /** How many of the longest recent report gaps the clock runs on past the last report. */
        const val CAP_GAPS = 3.0

        /** How many report gaps the longest is taken from. */
        const val GAPS_KEPT = 16
    }

    private val lock = KlangLock()

    private var reportSec = Double.NaN
    private var reportAtMs = 0.0
    private var lastReadSec = Double.NEGATIVE_INFINITY

    private val gaps = DoubleArray(GAPS_KEPT)
    private var gapsSeen = 0
    private var cap = MIN_CAP_SEC

    private var latencySec = 0.0

    /** The time from the backend's timeline to the speakers, from the last report: what the UI adds to be on the sound. */
    val outputLatencySec: Double get() = lock.withLock { latencySec }

    /**
     * How far past the last report the clock runs on: [CAP_GAPS] of the longest of the last [GAPS_KEPT] report gaps,
     * so reports that come in bursts (Android hands `process()` over in bursts of 40 to 100 ms) do not stop it between
     * two bursts.
     */
    val capSec: Double get() = lock.withLock { cap }

    /** A report from the backend: its diagnostics carry its timeline. */
    fun onDiagnostics(diagnostics: KlangCommLink.Feedback.Diagnostics) {
        onReport(timelineSec = diagnostics.backendNowMs / 1000.0, outputLatencyMs = diagnostics.outputLatencyMs)
    }

    /** The backend was at [timelineSec] when it reported; it is taken as now. */
    fun onReport(timelineSec: Double, outputLatencyMs: Double) {
        val atMs = localMs()

        lock.withLock {
            if (!reportSec.isNaN()) {
                gaps[gapsSeen % GAPS_KEPT] = (atMs - reportAtMs) / 1000.0
                gapsSeen++
                cap = (CAP_GAPS * longestGap()).coerceIn(MIN_CAP_SEC, MAX_CAP_SEC)
            }

            reportSec = timelineSec
            reportAtMs = atMs
            latencySec = outputLatencyMs / 1000.0
        }
    }

    /** Now on the backend's timeline, in seconds. */
    fun nowSec(): Double {
        val atMs = localMs()

        return lock.withLock {
            // No report yet: a guess, which the first report replaces
            if (reportSec.isNaN()) {
                return@withLock atMs / 1000.0
            }

            val now = max(reportSec + min((atMs - reportAtMs) / 1000.0, cap), lastReadSec)
            lastReadSec = now

            now
        }
    }

    /**
     * Where the backend said it was in its last report, no run-on (KLANG-00399, step 3b): what has surely been rendered.
     * What ends with the sound (a cycle completed, a one-shot's stop) waits for this, not for [nowSec], which may run up
     * to [capSec] ahead of a backend that stalled. Before the first report, the same guess as [nowSec].
     */
    fun reportedSec(): Double {
        val atMs = localMs()

        return lock.withLock {
            when {
                reportSec.isNaN() -> atMs / 1000.0
                else -> reportSec
            }
        }
    }

    private fun longestGap(): Double {
        var longest = 0.0

        for (i in 0 until min(gapsSeen, GAPS_KEPT)) {
            longest = max(longest, gaps[i])
        }

        return longest
    }
}
