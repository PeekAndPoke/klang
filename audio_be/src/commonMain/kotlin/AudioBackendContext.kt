/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The shared backend state — everything that is **one per backend** and injected into every
 * [PlaybackEngine] / `VoiceScheduler`, instead of threading half a dozen separate parameters.
 *
 * Holds the shared services (sample cache, registries, IPC link, config) and the read-only
 * [clock]. Per-engine state (its own `Cylinders`, scheduling timeline, scratch buffers) is NOT here
 * — it is built inside each engine. See `docs/tasks/per-playback-engine.md`.
 *
 * Note: `performanceTimeMs` is transitional — once diagnostics emission moves up to the dispatcher
 * (D5), the per-scheduler wall-clock read leaves this context.
 */
class AudioBackendContext(
    val sampleRate: Int,
    val blockFrames: Int,
    val commLink: KlangCommLink.BackendEndpoint,
    val sampleStore: SampleStore,
    /** Parent ignitor registry — each engine's scheduler forks it per playback. */
    val ignitorRegistry: IgnitorRegistry,
    val pipelineRegistry: PipelineRegistry,
    /** The single audio timeline (read-only here; written by the main loop via [BackendClock]). */
    val clock: RenderClock,
    /** Wall clock in ms — for render-headroom measurement + FE drift reporting. */
    val performanceTimeMs: () -> Double,
) {
    val sampleRateDouble: Double = sampleRate.toDouble()

    companion object {
        /**
         * Builds a context with the standard shared services (sample cache + seeded registries).
         * The caller creates the mutable [BackendClock], passes it in (as a read-only [RenderClock]),
         * and keeps its own reference to advance it.
         */
        fun create(
            sampleRate: Int,
            blockFrames: Int,
            commLink: KlangCommLink.BackendEndpoint,
            clock: RenderClock,
            performanceTimeMs: () -> Double = { 0.0 },
        ): AudioBackendContext = AudioBackendContext(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink,
            sampleStore = SampleStore(commLink),
            ignitorRegistry = IgnitorRegistry().apply { registerDefaults() },
            pipelineRegistry = PipelineRegistry(),
            clock = clock,
            performanceTimeMs = performanceTimeMs,
        )
    }
}
