/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * Sample voices must start **sample-accurately**, exactly like oscillator voices — and must still
 * clamp to the current block when they arrive late.
 *
 * `VoiceFactory` used to hand the sample branch `nowFrame` (the current block's first frame) as the
 * voice's `startFrame`, so every sample onset was rounded DOWN to a block boundary — firing early by
 * 0..blockFrames-1 frames. That is not a constant offset but per-hit jitter (where a hit falls inside
 * its block varies per hit), which is what audibly wrecks the groove — ±2.7 ms live at 128 frames,
 * ±10.7 ms in an offline render that used a 512-frame block.
 *
 * The mechanism to do it right was always there: `Voice.render` clips the voice into the block via
 * `offset = max(blockStart, startFrame) - blockStart`. The sample branch just wasn't using it.
 *
 * The fix is `maxOf(startFrame, nowFrame)`, so this spec has to pin **both** halves of that `maxOf`:
 * the on-time case (sample-accurate onset) and the late case (the `nowFrame` floor). A plain
 * `startFrame` passes the on-time cases, so without the late case the floor is unguarded.
 *
 * Drives a bare [PlaybackEngine] rather than the dispatcher:
 *  - `PlaybackEngine.renderInto` skips `MasterStage`, whose limiter lookahead would delay the onset
 *    under test by 5 ms;
 *  - owning the [BackendClock] lets the spec advance `cursorFrame` in step with the render, so "now"
 *    on the promotion path (`ensureEpoch` reads `clock.nowSec()`) matches the block being rendered.
 *    Passing the cursor only as a `renderInto` argument while the clock sat at frame 0 would make
 *    this guard silently change meaning the day anything else on that path consults the clock.
 */
class SampleVoiceOnsetSpec : StringSpec({

    val sampleRate = 48_000
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES
    val soundName = "onsettest"
    val req = SampleRequest(bank = null, sound = soundName, index = null, note = null)

    class Rig {
        val clock = BackendClock(sampleRate)
        val context = AudioBackendContext.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            clock = clock,
        )
        val engine = PlaybackEngine.create(context)
        private val mix = StereoBuffer(blockFrames)

        init {
            // A DC sample (not silence, not a ramp) so the sample's very first frame already carries
            // full signal — anything non-zero in the output is the voice, at the frame it started.
            context.sampleStore.addSample(
                KlangCommLink.Cmd.Sample.Complete(
                    req = req,
                    note = null,
                    pitchHz = 440.0,
                    sample = TestSamples.constant(size = sampleRate, value = 1.0, sampleRate = sampleRate),
                )
            )
        }

        fun schedule(startFrame: Double, data: VoiceData = VoiceData.empty.copy(sound = soundName)) {
            val startSec = startFrame.toDouble() / sampleRate
            engine.scheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = "song",
                    startTime = startSec,
                    gateEndTime = startSec + 0.2,
                    data = data,
                    playbackStartTime = 0.0,
                )
            )
        }

        /**
         * Pins the playback epoch to frame 0 while the cursor is still there.
         *
         * `ensureEpoch` snaps the epoch to "now" for the FIRST voice of a playback, so a voice
         * cannot be late if it is also the first one — its start time is defined to be now. The
         * late cases therefore have to establish the epoch up front. A control-only event does
         * that without sounding: `promoteScheduled` consumes it and `continue`s before any voice
         * is built.
         */
        fun establishEpochAtZero() {
            schedule(startFrame = 0.0, data = VoiceData.empty.copy(control = true))
        }

        /** Renders [blocks] blocks from the current cursor, returning per-frame peak magnitude. */
        fun render(blocks: Int): DoubleArray {
            val out = DoubleArray(blocks * blockFrames)

            for (block in 0 until blocks) {
                mix.clear()
                engine.renderInto(mix, clock.cursorFrame)

                for (i in 0 until blockFrames) {
                    out[block * blockFrames + i] = maxOf(abs(mix.left[i]), abs(mix.right[i]))
                }

                clock.cursorFrame += blockFrames
            }

            return out
        }
    }

    /** Index of the first frame carrying signal, or -1 if the render is silent. */
    fun firstAudibleFrame(frames: DoubleArray): Int = frames.indexOfFirst { it > 1e-12 }

    // ── On-time: the onset lands exactly where it was scheduled ──────────────────────────────────

    // Deliberately non-block-aligned: just into a block, mid-block, and the last frame of a block.
    // All of them used to collapse onto the same block boundary (frame 128).
    //
    // Not `blockFrames + 1`: with the onset quantised to the boundary, the one frame in front of it
    // is the ADSR's attack at exactly 0.0, so that case cannot tell the two behaviours apart.
    listOf(
        blockFrames + 5,
        blockFrames + (blockFrames / 2),
        blockFrames + (blockFrames - 1),
        (3 * blockFrames) + 37,
    ).forEach { startFrame ->

        "sample voice scheduled at frame $startFrame starts there, not at its block boundary" {
            val rig = Rig()
            rig.schedule(startFrame.toDouble())
            val frames = rig.render(blocks = 8)

            // Guards against a vacuous pass: the render must actually contain the voice.
            frames.max() shouldBeGreaterThan 1e-6

            // THE assertion. With the old `nowFrame` behaviour the onset landed on the block
            // boundary below startFrame, so these frames were loud.
            frames.copyOfRange(0, startFrame).count { it > 1e-12 } shouldBe 0

            // And it starts right there — not pushed into the next block either. A few frames of
            // slack for the ADSR attack ramping up from exactly 0.0 at the onset frame.
            val onset = firstAudibleFrame(frames)
            onset shouldBeGreaterThanOrEqual startFrame
            onset shouldBeLessThan startFrame + 8
        }
    }

    "two sample voices one frame apart stay one frame apart" {
        // The jitter case stated directly: under block-quantised onsets these two collapse onto
        // the SAME frame whenever they share a block.
        val a = Rig().also { it.schedule((blockFrames + 10).toDouble()) }.render(blocks = 8)
        val b = Rig().also { it.schedule((blockFrames + 11).toDouble()) }.render(blocks = 8)

        (firstAudibleFrame(b) - firstAudibleFrame(a)) shouldBe 1
    }

    // ── Late: `nowFrame` is still the floor ──────────────────────────────────────────────────────

    "a sample voice that arrives late starts its envelope at the onset, not partway in" {
        // Guards the `maxOf`'s second half — and it has to assert the ENVELOPE, not the onset
        // frame. `Voice.render` clamps the render window itself (`vStart = max(blockStart,
        // startFrame)`), so with or without the floor the first audible frame is the same one.
        // What differs is the envelope phase: built with a past `startFrame`, the voice's ADSR is
        // already ~100 frames in while `SampleIgnitor`'s playhead still starts at the top of the
        // PCM. Envelope and sample desynced — and the render STEPS from silence straight to a live
        // envelope value instead of ramping. That step is the click the floor exists to prevent.
        //
        // The scheduling happens AFTER the cursor has advanced, which is the real-world shape of
        // this: FE/BE clock skew, command latency, or a stalled worklet delivering a voice whose
        // start has already gone by.
        val rig = Rig()
        rig.establishEpochAtZero()

        // Advance two blocks with nothing scheduled — this is the render that puts the voice's
        // start time in the past.
        val before = rig.render(blocks = 2)
        before.count { it > 1e-12 } shouldBe 0

        // Scheduled 100 frames behind the cursor (well inside `oldestAllowedSec` = 5 blocks, so it
        // is clamped rather than dropped).
        val lateStart = rig.clock.cursorFrame - 100
        rig.schedule(lateStart)

        val frames = rig.render(blocks = 6)
        frames.max() shouldBeGreaterThan 1e-6

        // It starts at the top of THIS block — at the cursor, not skipped forward.
        firstAudibleFrame(frames) shouldBeLessThan 8

        // And it starts at the BEGINNING of its envelope: the first frame is the attack at zero.
        // Without the floor the voice is built with a startFrame 100 frames in the past, so the
        // very first rendered frame already carries the envelope's value that far into the attack
        // (≈0.032 with the current ADSR defaults) and the declick one-pole is seeded to it.
        frames[0] shouldBeLessThan 1e-9

        // The opening must also still be climbing, not already at level. Threshold 0.5% because
        // the mutation lands at ≈4.6% — a 5% threshold sits only 9% away from passing on the
        // broken code, and would silently stop discriminating if the ADSR defaults were retuned.
        val peak = frames.max()
        val openingPeak = (0 until 8).maxOf { frames[it] }
        openingPeak shouldBeLessThan (peak * 0.005)
    }

    "a sample voice later than the drop window is dropped" {
        // Guards `VoiceScheduler.oldestAllowedSec`, NOT the VoiceFactory floor — the drop happens
        // in `promoteScheduled` before `makeVoice` is ever called, so this test is green whichever
        // way `sampleStartFrame` is written. It is here because the floor and the drop window are
        // the two halves of late-voice handling and it would be easy to "simplify" one into the
        // other: without the drop, a stalled worklet would clamp a backlog of stale voices to the
        // current block and fire them all at once.
        val rig = Rig()
        rig.establishEpochAtZero()
        rig.render(blocks = 12).count { it > 1e-12 } shouldBe 0

        // `oldestAllowedSec` is 5 blocks behind now; 8 blocks is comfortably past it.
        rig.schedule(rig.clock.cursorFrame - (8 * blockFrames))

        val frames = rig.render(blocks = 6)
        frames.count { it > 1e-12 } shouldBe 0
    }
})
