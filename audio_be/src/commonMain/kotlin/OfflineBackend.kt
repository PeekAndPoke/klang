/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation

/**
 * A backend that renders only when told (KLANG-00399, step 2): the live backends' [PlaybackEngineDispatcher], with no
 * audio device and no timer. Whoever holds it decides when a block renders, so a spec can let the frontend's time
 * run on while nothing renders (an audio thread starved, a laptop asleep), render while the frontend stands still (a
 * main thread stalled), or render faster or slower than the frontend's clock (drift). [VoiceWatch] tells what became
 * of each voice.
 *
 * Like a live backend it drains the control link before each block and sends its feedback through the link; unlike
 * one it has no warmup: [KlangCommLink.Feedback.BackendReady] goes out at once. Its timeline starts at
 * [startTimeSec], and its wall clock is the timeline (the render headroom reads it). Nothing paces it, so the holder
 * reads the feedback as it renders: the link's ring is bounded, and a full one drops what comes next without a sign.
 */
class OfflineBackend(
    config: AudioBackend.Config,
    /** The timeline's second at frame 0; a live backend takes it from its clock at the start. */
    val startTimeSec: Double = 0.0,
    voiceWatch: VoiceWatch? = null,
) : AudioBackend {

    private val commLink = config.commLink
    private val sampleRate = config.sampleRate
    private val blockFrames = config.blockSize

    /** The next frame to render: the frames rendered so far. */
    var cursorFrame: Double = 0.0
        private set

    /** Now on the timeline: the start of the next block to render. */
    val nowSec: Double get() = startTimeSec + cursorFrame / sampleRate

    private val dispatcher = PlaybackEngineDispatcher.create(
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        commLink = commLink,
        performanceTimeMs = { nowSec * 1000.0 },
        voiceWatch = voiceWatch,
    )

    /** The last block rendered. */
    val out = StereoBuffer(blockFrames)

    init {
        dispatcher.setBackendStartTime(startTimeSec)
        commLink.feedback.send(KlangCommLink.Feedback.BackendReady())
    }

    /** Nothing runs on its own: the holder renders. */
    override suspend fun run(scope: CoroutineScope) {
        awaitCancellation()
    }

    /** Handles the commands sent so far, then renders [blocks] blocks, each into [out] and handed to [onBlock]. */
    fun render(blocks: Int = 1, onBlock: (StereoBuffer) -> Unit = {}) {
        repeat(blocks) {
            drain()
            dispatcher.renderBlock(cursorFrame = cursorFrame, out = out)
            cursorFrame += blockFrames
            onBlock(out)
        }
    }

    /** Renders until the timeline reaches [sec]: the blocks that start before it. */
    fun renderUntil(sec: Double, onBlock: (StereoBuffer) -> Unit = {}) {
        while (nowSec < sec) {
            render(blocks = 1, onBlock = onBlock)
        }
    }

    /** Handles the commands sent so far, rendering nothing. */
    fun drain() {
        while (true) {
            val cmd = commLink.control.receive() ?: break
            dispatcher.handle(cmd)
        }
    }
}
