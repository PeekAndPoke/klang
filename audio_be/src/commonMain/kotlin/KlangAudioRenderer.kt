/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangMessageReceiver

/**
 * Standalone single-engine render: one playback, scheduled into its scheduler directly, for the benchmarks and the specs
 * that measure the engine. Songs and the render specs render through `KlangOfflineRenderer` on the real playback path
 * (the wire into [OfflineBackend]) since KLANG-00399 step 4. Its output is the engine's floating-point stereo block
 * (see [HouseStage.process]); a caller that needs 16-bit PCM converts at its own edge with [writePcm16].
 *
 * **One block driver** (KLANG-00290 (engine follow ups) item 27, D4, maintainer 2026-11-06: "the offline renderer must
 * be a complete transparent alternative for the audio backend"): the block is the live [PlaybackEngineDispatcher]'s
 * own `renderBlock`, the one the JVM and JS backends and [OfflineBackend] run (the engines, the house stage, the
 * disposal sweep, the warehouse's housekeeping, the diagnostics). This class only adds a way to reach the one engine's
 * scheduler and registries without the wire. It used to run a copy of that block (the engine and a house stage of its
 * own), which the live path could leave behind.
 *
 * It owns its comm link, and nobody reads its feedback, so it empties it after every block: the dispatcher's
 * diagnostics (one every 20 ms of audio) would otherwise fill the ring and stay alive for the whole render. The timing
 * benchmarks built on it pay the dispatcher's per-block bookkeeping since D4, as the live backends do; their numbers
 * moved with it.
 */
class KlangAudioRenderer private constructor(
    private val context: AudioBackendContext,
    clock: BackendClock,
    /** The receiving end of the feedback this renderer's dispatcher sends; emptied after every block. */
    private val feedback: KlangMessageReceiver<KlangCommLink.Feedback>,
) {
    private val dispatcher = PlaybackEngineDispatcher(context = context, clock = clock)

    private val engine = dispatcher.openEngine(ENGINE_PLAYBACK_ID)

    /** The single engine's scheduler — callers schedule voices here. */
    val voices: VoiceScheduler get() = engine.scheduler

    /** Parent ignitor registry — callers register custom oscillators here. */
    val ignitorRegistry: IgnitorRegistry get() = context.ignitorRegistry

    /** Parent Katalyst registry; callers register custom chains here, for a cylinder or the output. */
    val katalystRegistry: KatalystRegistry get() = context.katalystRegistry

    /**
     * Frames of latency the output adds ([PlaybackEngineDispatcher.latencyFrames]): the house stage's (the house
     * limiter's lookahead delay) plus the playback's master chain in service.
     *
     * An offline render must run this many frames PAST its musical end, or the final samples are
     * still in a delay ring when the loop stops. Read it per block: a master event later in the song swaps
     * the chain in the middle of the render.
     */
    val latencyFrames: Int get() = dispatcher.latencyFrames

    fun setBackendStartTime(startTimeSec: Double) {
        dispatcher.setBackendStartTime(startTimeSec)
    }

    /** Clears the house stage (limiter envelope + DC blocker IIR state). */
    fun resetPostChain() {
        dispatcher.resetPostChain()
    }

    /** Renders one block into [out] (`blockFrames` frames per channel), clipped to `[-1, 1]`: the dispatcher's block. */
    // NB `cursorFrame` is Double, not Int: it is an ABSOLUTE frame on the backend timeline, which
    // grows for the life of the backend and overflows Int after ~12.4 h. Exact below 2^53
    // (~5,950 years at 48 kHz). See RenderClock.cursorFrame. Per-sample offsets stay Int.
    fun renderBlock(cursorFrame: Double, out: StereoBuffer) {
        dispatcher.renderBlock(cursorFrame = cursorFrame, out = out)

        // Nobody reads it (see the class KDoc): dropped here so the ring holds nothing between blocks
        while (feedback.receive() != null) {
            continue
        }
    }

    /** The render clock, for the spec that pins the between-renders convention offline (block-framing B1). */
    internal val clockForTest: RenderClock get() = dispatcher.clockForTest

    companion object {
        /**
         * The id the one engine is filed under in the dispatcher. The voices a caller schedules name their own
         * playback, and the scheduler's context takes that id.
         */
        private const val ENGINE_PLAYBACK_ID: String = "offline"

        fun create(
            sampleRate: Int,
            blockFrames: Int,
            performanceTimeMs: () -> Double = { 0.0 },
            phasePoolSeed: Int? = null,
        ): KlangAudioRenderer {
            val clock = BackendClock(sampleRate)
            val commLink = KlangCommLink()
            val context = AudioBackendContext.create(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                commLink = commLink.backend,
                clock = clock,
                performanceTimeMs = performanceTimeMs,
                phasePoolSeed = phasePoolSeed,
            )
            return KlangAudioRenderer(context = context, clock = clock, feedback = commLink.frontend.feedback)
        }
    }
}
