/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_bridge.constants.VCA_OFF_TEARDOWN_FADE_SECONDS
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The teardown fade: a unity gate with a short linear fade to EXACT zero over the voice's last frames.
 *
 * The one copy of this law, with two hosts (it lives next to [Voice], not in the strip, because it
 * outlives the strip: from step 9 it is the voice's own teardown guard):
 *  - the strip's `adsrOff` path (`EnvelopeRenderer` with `on = false`), as always;
 *  - a voice whose Ignitor tree IS the whole voice (a built-in on `classic()` since phase 3 step 6)
 *    and whose build reports that the tree does not end in its own amplitude envelope
 *    (`BuiltIgnitor.endsInEnvelope`), which is the tree's `adsrOff`. Appended after the ignite stage,
 *    it runs exactly where the strip's VCA ran, so both hosts render the same bits.
 *
 * Stateless (it reads the block context only), so one instance serves every voice.
 *
 * **Why the fade exists.** In a VCA-last pipeline (`modern`) the curve drove the fully
 * amplified signal to zero before `Voice.render` dropped the voice. Switching the curve off
 * removes that, and the instrument's own envelope cannot replace it: it sits BEFORE the
 * instrument's amp stages, so a tail it has taken to ~1e-4 comes back out of a tube/drive stage
 * 20 dB louder, and teardown steps that straight to zero. See [VCA_OFF_TEARDOWN_FADE_SECONDS]
 * for the measurements and `VcaOffTeardownSpec` for the guard.
 *
 * **Known limit: this guarantees silence at THIS stage's output, not the voice's.** A custom
 * pipeline may place the VCA ahead of Crush / Coarse / Distort / Filter / Tremolo, and those carry
 * state (IIR memory, sample-and-hold) that keeps emitting from a zero input. On such a pipeline
 * the fade removes the gate's own step but not the downstream tail. Closing that properly means
 * a guard at the END of the strip, which would also change the `on = true` path, so it is left
 * as a deliberate follow-up rather than smuggled in here.
 *
 * Note also that the `on = true` path is NOT click-free either: the de-click one-pole lags and
 * leaves ~3.3e-3 on the last frame at a 50 ms release. See the scope note in `AdsrCurveMath`.
 */
object TeardownFadeRenderer : BlockRenderer {

    override fun render(ctx: BlockContext) {
        // The LAST frame the voice renders is floor(endFrame)-1. Voice.render clamps vEnd to
        // endFrame and truncates the length, and endFrame is a Double (gateEnd + release*sampleRate,
        // fractional at most sample rates), so the frame AT endFrame never exists: targeting it
        // would leave frac(endFrame)/fadeDen behind, the same residual class this fade removes.
        //
        // PRECONDITION: startFrame and blockStart are integral Doubles (VoiceFactory floors the
        // former, the worklet advances cursorFrame by whole blocks). That is what makes
        // `offset + length - 1 == floor(endFrame) - blockStart - 1` hold even when a voice starts
        // and ends inside one block, and hence what makes the endpoint exact. `VoiceFactoryVcaOffSpec`
        // renders a REAL Voice to pin the coupling; if sub-sample onsets ever arrive, revisit here.
        val lastFrame = floor(ctx.endFrame) - 1.0
        val fadeFrames = VCA_OFF_TEARDOWN_FADE_SECONDS * ctx.sampleRateD
        // The guard always gets its full window ON THE TIMELINE PATH, where endFrame is known
        // before the window is rendered. A realtime note-off rewrites endFrame between blocks
        // (Voice.releaseGate), so an authored release SHORTER than this window enters the ramp
        // mid-way: a step of up to ~50% at release 2 ms, 100% at release 0. Known, unfixed:
        // scoping a fix needs the release-vs-window comparison at releaseGate time, and whether
        // to extend the voice by the window is a maintainer call (see the round-3 parked item in
        // docs/tasks-archive/2026-08/20260829-realtime-note-off-gate-release.md).
        //
        // An earlier version preferred to start at gate end
        // so it could not touch the note body, but that collapsed the window for a SHORT non-zero
        // release: at 0.1 ms the ramp got 4 frames and the last sample came out at 0.21 of full
        // scale, a step, i.e. the click this exists to remove, and worse than release = 0 got.
        // Taking the window from the gate tail when the release is too short is the lesser evil.
        val ideal = lastFrame - (fadeFrames - 1.0)
        // ...but never more than the second half of the voice: this is a fade guard, not an envelope.
        val midpoint = (ctx.startFrame + lastFrame) * 0.5
        val fadeStart = maxOf(ideal, midpoint).coerceAtMost(lastFrame)
        // A reciprocal is safe here, unlike the release ramp: the endpoint that must be exact is
        // `remaining == 0.0`, which comes from `lastIdx - idx == 0`, and 0.0 * x is 0.0 exactly.
        // The `> 1.0` clamp absorbs a 1-ulp overshoot at the entry frame.
        val fadeScale = 1.0 / (lastFrame - fadeStart).coerceAtLeast(1.0)

        // Hoisted per block so the per-sample path stays an Int compare.
        val lastIdx = lastFrame - ctx.blockStart
        val fadeStartIdx = ceil(fadeStart - ctx.blockStart).toInt()

        // No de-click smoother here, and this is REQUIRED, not merely free: inside the window the
        // target ramps 1.0 -> 0, so a one-pole would lag and leave a non-zero final sample, losing
        // the exact-zero endpoint that is the whole point.
        //
        // Skip the note body entirely: `from` collapses the per-sample branch and, for every block
        // before the ramp, the loop does not run at all. maxOf also absorbs the very negative
        // fadeStartIdx that ceil().toInt() produces on later blocks.
        val end = ctx.windowEnd
        val from = maxOf(ctx.offset, fadeStartIdx)

        for (idx in from until end) {
            val remaining = (lastIdx - idx) * fadeScale
            val gain = if (remaining < 0.0) 0.0 else if (remaining > 1.0) 1.0 else remaining
            ctx.audioBuffer[idx] = ctx.audioBuffer[idx] * gain
        }
    }
}
