/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * A voice started "now" by a realtime source (e.g. a MIDI keyboard) — the second note source
 * next to the pre-scheduled timeline (see docs/tasks/midi-keyboard-playground.md).
 *
 * Deliberately carries NO start time: immediacy is not a special time value, it is the absence
 * of the concept. The backend stamps the current time at receipt; a realtime voice never enters
 * the scheduled heap or the epoch-anchoring machinery that timeline voices need.
 */
@WireFormat
data class RealtimeVoice(
    /**
     * Frontend-assigned id, unique per playback while the voice is alive. The key a later
     * stop/gate-off command uses to release exactly this voice (chords and retriggers make the
     * note number unusable as an identity).
     */
    val liveId: Int,
    /** The synthesis payload — same contract as [ScheduledVoice.data]. */
    val data: VoiceData,
    /**
     * Gate length in seconds from the actual start. Null = held: the gate stays open until a
     * stop command releases it (the natural state of a pressed key).
     */
    val gateDurSec: Double? = null,
)
