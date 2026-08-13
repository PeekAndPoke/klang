/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


/**
 * Use to schedule the playback of a voice
 */
@WireFormat
data class ScheduledVoice(
    /** The ID of the playback (song) this voice belongs to */
    val playbackId: String,
    /** The event that triggered this voice */
    val data: VoiceData,
    /** Time in seconds relative to playback start */
    val startTime: Double,
    /** Time in seconds relative to playback start when the note key is lifted */
    val gateEndTime: Double,
    /** Frontend's playback start time in seconds (for epoch anchoring) */
    val playbackStartTime: Double,
) {
    /**
     * True if [other] is the SAME scheduled voice event — same start time, same synthesis payload
     * ([data]). This is the identity used by the live-update replace dedup
     * (`VoiceScheduler.dedupAgainstActive`): a re-derived-identical event is a duplicate, while
     * anything differing in `data` (a chord tone, a `superimpose` layer, a changed note) is NOT.
     * `playbackId` is not compared — the scheduler is per-playback (one engine per playback), so all
     * of its voices already share one playbackId. Deliberately ignores `gateEndTime` /
     * `playbackStartTime` too — `data` is the voice's identity.
     */
    fun isDuplicate(other: ScheduledVoice): Boolean =
        startTime == other.startTime && data == other.data
}
