/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler

/**
 * One per-`playbackId` DSP instance. Owns its **full** render state — its own [VoiceScheduler]
 * (own scheduling timeline, solo state, scratch, `RenderContext`, `VoiceFactory`) and its own
 * [Cylinders] (orbits + FX). The only thing it does NOT own is the shared backend state
 * ([AudioBackendContext]); in particular the audio timeline (clock) is read from there, never per
 * engine — see `docs/tasks/per-playback-engine.md` (D2·b/D2·d).
 */
class PlaybackEngine(
    val scheduler: VoiceScheduler,
    val cylinders: Cylinders,
) {
    /** Render this engine's voices through its own cylinders, accumulating into [target]. */
    fun renderInto(target: StereoBuffer, cursorFrame: Int) {
        cylinders.clearAll()
        scheduler.process(cursorFrame)
        cylinders.processAndMix(target)
    }

    /** True once this engine has no active voices and all its cylinders have gone silent. */
    fun isIdle(): Boolean =
        scheduler.getActiveVoiceCount() == 0 && !cylinders.anyActive()

    companion object {
        /** Builds an engine: its own [Cylinders] + a [VoiceScheduler] wired to the shared [context]. */
        fun create(context: AudioBackendContext): PlaybackEngine {
            val cylinders = Cylinders(blockFrames = context.blockFrames, sampleRate = context.sampleRate)
            val scheduler = VoiceScheduler(VoiceScheduler.Options(context = context, cylinders = cylinders))
            return PlaybackEngine(scheduler = scheduler, cylinders = cylinders)
        }
    }
}
