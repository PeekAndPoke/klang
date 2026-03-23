package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.exciter.ExciterRegistry

/**
 * Per-playback context. Created when a playback is first seen, destroyed on cleanup.
 *
 * Groups per-playback state that was previously scattered across VoiceScheduler maps.
 * The forked registry allows per-playback oscillator registration (for future KlangScript integration).
 */
class PlaybackCtx(
    val playbackId: String,
    /** Per-playback registry, forked from global. User can register custom oscillators. */
    val exciterRegistry: ExciterRegistry,
    /** Backend-local epoch (seconds since backend start) when this playback first appeared */
    var epoch: Double = 0.0,
)
