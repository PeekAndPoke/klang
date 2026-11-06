/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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
     * The output chain (the playback's master) this event references, if any: a [KatalystValue],
     * the same chain type an orbit runs. Default `null` for pattern types that don't carry one.
     * Pattern languages that may carry an inline chain (e.g. sprudel) override to expose it.
     *
     * Used by the playback's wire-emission step to pre-register inline chains with the backend
     * before events that reference them are scheduled, into the one Katalyst registry. Mirror of
     * [sound].
     */
    val master: KatalystValue? get() = null

    /**
     * The orbit chain this event references, if any. Default `null` for pattern types that don't
     * carry [KatalystValue]. Pattern languages that may carry an inline chain (e.g. sprudel)
     * override to expose the event's [KatalystValue].
     *
     * Used by the playback's wire-emission step to pre-register inline chains with the backend
     * before events that reference them are scheduled. Mirror of [master].
     */
    val katalyst: KatalystValue? get() = null

    /**
     * How long the gate stays open for an event that lasts [durationSec] seconds: the event's whole duration, unless
     * the pattern language shortens or stretches it (sprudel's `legato`). The frontend folds it into the gate time it
     * sends ([ScheduledVoice.gateEndTime], a realtime voice's `gateDurSec`), so the backend learns only seconds
     * (KLANG-00290 (engine follow ups) item 24, decision D6, maintainer 2026-11-06: `legato` does not travel on the
     * wire). The default is the duration itself.
     */
    fun gateSeconds(durationSec: Double): Double = durationSec

    /** Convert to engine-level voice data. */
    fun toVoiceData(): VoiceData
}
