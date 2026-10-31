/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.SourceLocationChain

/**
 * Signals emitted during playback lifecycle.
 */
sealed class KlangPlaybackSignal {
    /**
     * Initial state of a playback signal stream before any lifecycle events have occurred.
     */
    data object Idle : KlangPlaybackSignal()

    /**
     * Emitted when sample preloading begins.
     * Gives the frontend a chance to show a "loading samples..." indicator.
     */
    data class PreloadingSamples(
        /** Number of unique samples that need to be loaded */
        val count: Int,
        /** The sample identifiers being loaded */
        val samples: List<String>,
    ) : KlangPlaybackSignal()

    /**
     * Emitted when sample preloading completes (all samples sent to backend).
     */
    data class SamplesPreloaded(
        /** Number of samples that were loaded */
        val count: Int,
        /** How long preloading took in milliseconds */
        val durationMs: Int,
    ) : KlangPlaybackSignal()

    /**
     * Emitted when playback has actually started (after preloading, first voices scheduled).
     */
    data object PlaybackStarted : KlangPlaybackSignal()

    /**
     * Emitted when playback has stopped.
     */
    data object PlaybackStopped : KlangPlaybackSignal()

    /**
     * Emitted when a live update replaces the running pattern, BEFORE the new
     * pattern's [VoicesScheduled] signals arrive. UI surfaces use it to cancel
     * highlights already scheduled ahead for the old pattern — the engine drops
     * those voices at the update cutoff, so their highlights must die with them.
     */
    data object PatternUpdated : KlangPlaybackSignal()

    /**
     * Emitted when a full cycle has been completed.
     * Stall-safe: if multiple cycles are missed due to CPU stalls, all cycles are emitted in order.
     *
     * @param cycleIndex The cycle that just completed (0-based). cycleIndex=0 means cycle [0,1) completed.
     * @param atTimeSec When the cycle's end is heard, in seconds on the UI's clock (`Date.now()` in the browser), the
     *   output latency included (KLANG-00399).
     */
    data class CycleCompleted(
        val cycleIndex: Int,
        val atTimeSec: Double,
    ) : KlangPlaybackSignal()

    /**
     * Emitted when voices are scheduled for a cycle chunk.
     * Contains all voices scheduled in this batch for efficient processing.
     *
     * Note: its times are when a voice is heard, in seconds on the UI's clock (`Date.now()` in the browser, which the
     * highlights compare with), the output latency included (KLANG-00399). Not `KlangTime`: after a sleep it can lag.
     */
    data class VoicesScheduled(
        /** All voice events scheduled in this batch */
        val voices: List<VoiceEvent>,
    ) : KlangPlaybackSignal() {
        data class VoiceEvent(
            /** Absolute start time in seconds (wall-clock) */
            val startTime: Double,
            /** Absolute end time in seconds (wall-clock) */
            val endTime: Double,
            /** The voice data being played */
            val data: VoiceData,
            /** Source locations for code highlighting */
            val sourceLocations: SourceLocationChain?,
        )
    }

    /**
     * Emitted with system-wide diagnostics information.
     * Contains performance metrics and cylinder states.
     */
    data class Diagnostics(
        val diagnostics: KlangCommLink.Feedback.Diagnostics,
    ) : KlangPlaybackSignal()
}
