/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * What became of each voice that reached its start (KLANG-00399): it started, it came too late, or it made no sound.
 * Told on the audio thread as it happens, one call per voice, never per sample. A voice replaced, cleared or dropped
 * as a duplicate before its start is not told. The [OfflineBackend]'s specs listen; the live backends pass none, so the
 * hot path pays one null check per voice.
 *
 * [atSec] is the voice's start on the backend's timeline.
 */
interface VoiceWatch {
    /** The voice starts at [atSec]. */
    fun started(playbackId: String, data: VoiceData, atSec: Double) {}

    /** The voice is dropped: its start had been rendered past ("no late voices", `VoiceScheduler`). */
    fun late(playbackId: String, data: VoiceData, atSec: Double) {}

    /** The voice is dropped: no voice could be made, its sample not there (or empty) or its sound unknown. */
    fun soundless(playbackId: String, data: VoiceData, atSec: Double) {}
}
