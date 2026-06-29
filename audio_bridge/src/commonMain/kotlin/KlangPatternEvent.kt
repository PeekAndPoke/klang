/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.SourceLocationChain

/**
 * Common interface for pattern events that can be scheduled by the playback controller.
 *
 * Each pattern implementation (Strudel, sequencer, MIDI, etc.) provides its own event type
 * implementing this interface. The scheduling engine only needs these properties to convert
 * events into [ScheduledVoice] instances for the audio backend.
 */
interface KlangPatternEvent {
    /** Event start time in cycles (from pattern start) */
    val startCycles: Double

    /** Event duration in cycles */
    val durationCycles: Double

    /** Source locations for code highlighting */
    val sourceLocations: SourceLocationChain?

    /**
     * The sound this event references, if any. Default `null` for pattern types that
     * don't carry [SoundValue]. Pattern languages that may carry an inline ignitor
     * (e.g. sprudel) override to expose the event's [SoundValue].
     *
     * Used by the playback's wire-emission step to pre-register inline ignitors with
     * the backend before voice events that reference them are scheduled.
     */
    val sound: SoundValue? get() = null

    /**
     * The voice pipeline this event references, if any. Default `null` for pattern types that don't
     * carry [PipelineValue]. Pattern languages that may carry an inline pipeline (e.g. sprudel) override
     * to expose the event's [PipelineValue].
     *
     * Used by the playback's wire-emission step to pre-register inline pipelines with the backend before
     * voice events that reference them are scheduled. Mirror of [sound].
     */
    val pipeline: PipelineValue? get() = null

    /** Convert to engine-level voice data. */
    fun toVoiceData(): VoiceData
}
