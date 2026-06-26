/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.EngineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * One per-`playbackId` DSP instance. Owns its **full** render state — its own [VoiceScheduler]
 * (own scheduling timeline, solo state, scratch, `RenderContext`, `VoiceFactory`) and its own
 * [Cylinders] (orbits + FX). No shared mutable render state with other engines, so engines render
 * independently and can later be parallelized; the only join point is the dispatcher's mix sum.
 *
 * See `docs/tasks/per-playback-engine.md` (D2·b).
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

    fun preallocateCylinders() {
        cylinders.preallocateAll()
    }

    companion object {
        /**
         * Builds an engine: its own [Cylinders] + a [VoiceScheduler] wired to the **shared**
         * [sampleStore] and registries. The ignitor registry should be the engine's own fork
         * (per-playback custom oscillators); [engineRegistry] and [sampleStore] are shared.
         */
        fun create(
            sampleRate: Int,
            blockFrames: Int,
            commLink: KlangCommLink.BackendEndpoint,
            performanceTimeMs: () -> Double,
            ignitorRegistry: IgnitorRegistry,
            engineRegistry: EngineRegistry,
            sampleStore: SampleStore,
        ): PlaybackEngine {
            val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate)

            val scheduler = VoiceScheduler(
                VoiceScheduler.Options(
                    commLink = commLink,
                    sampleRate = sampleRate,
                    blockFrames = blockFrames,
                    ignitorRegistry = ignitorRegistry,
                    engineRegistry = engineRegistry,
                    cylinders = cylinders,
                    performanceTimeMs = performanceTimeMs,
                    sampleStore = sampleStore,
                )
            )

            return PlaybackEngine(scheduler = scheduler, cylinders = cylinders)
        }
    }
}
