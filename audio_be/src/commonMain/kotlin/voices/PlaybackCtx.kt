/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry

/**
 * Per-playback context. Created when a playback is first seen, destroyed on cleanup.
 *
 * Groups per-playback state that was previously scattered across VoiceScheduler maps.
 * The forked registry allows per-playback oscillator registration (for future KlangScript integration).
 */
class PlaybackCtx(
    val playbackId: String,
    /** Per-playback registry, forked from global. User can register custom oscillators. */
    val ignitorRegistry: IgnitorRegistry,
    /** Backend-local epoch (seconds since backend start) when this playback first appeared */
    var epoch: Double = 0.0,
)
