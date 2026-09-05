/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * The two [VoiceScheduler] behaviours that had **zero coverage anywhere in the repo** until this
 * spec: solo/mute ducking (`process`) and cut/choke groups (`activateVoice`).
 *
 * Audit finding F7 (`docs/audio-audit/FINDINGS.md#f7`). The finding also claimed the scheduler was
 * "not reached by any spec" — that half is withdrawn, `SampleVoiceOnsetSpec` and
 * `SeededPlaybackReproducibilitySpec` both drive it through `engine.scheduler`. What was true is
 * that no spec took its logic as the *subject*, and a repo-wide grep for `solo`, `choke` and
 * `cutGroup` in `commonTest` returned nothing. The [Rig] below is the same shape as those two.
 *
 * **Why the two halves are observed differently.** Cut is a question about *membership* of the
 * active list, so `getActiveVoiceCount()` answers it exactly and no DSP measurement can blur it.
 * Solo is a question about *gain*, so it has to be heard: the soloed voice is panned hard left and
 * the background hard right (equal-power pan, `SendRenderer:32-39`), which puts each one in its own
 * channel and lets the right channel be read as "the background, alone".
 */
class VoiceSchedulerSoloCutSpec : StringSpec({

    val sampleRate = 48_000
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES
    val blockDurationSec = blockFrames.toDouble() / sampleRate

    // VoiceScheduler.soloMuteRamp is ValueRamp(1.0, duration = 1.5, Ease.InOut.cubic), stepped once
    // per block — so a completed transition takes ceil(1.5 / blockDuration) blocks.
    val rampBlocks = (1.5 / blockDurationSec).toInt() + 2

    class Rig {
        val clock = BackendClock(sampleRate)
        val context = AudioBackendContext.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            clock = clock,
            // Two Rigs are compared against each other in most rows below, so nothing in the render
            // may vary per run. Live (null) deals a fresh phase-pool stream per playback.
            phasePoolSeed = 1,
        )
        val engine = PlaybackEngine.create(context)
        private val mix = StereoBuffer(blockFrames)

        val activeCount: Int get() = engine.scheduler.getActiveVoiceCount()

        fun nowSec(): Double = clock.cursorFrame / sampleRate

        /** Long gates throughout: every voice here must outlive the 1.5 s solo ramp. */
        fun schedule(startSec: Double, data: VoiceData, durSec: Double = 30.0) {
            engine.scheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = "song",
                    startTime = startSec,
                    gateEndTime = startSec + durSec,
                    data = data,
                    playbackStartTime = 0.0,
                )
            )
        }

        /** Renders [blocks] blocks and returns the (left, right) peak of the LAST one. */
        fun renderPeaks(blocks: Int): Pair<Double, Double> {
            var l = 0.0
            var r = 0.0

            for (block in 0 until blocks) {
                mix.clear()
                engine.renderInto(mix, clock.cursorFrame)

                l = 0.0
                r = 0.0

                for (i in 0 until blockFrames) {
                    l = maxOf(l, abs(mix.left[i]))
                    r = maxOf(r, abs(mix.right[i]))
                }

                clock.cursorFrame += blockFrames
            }

            return l to r
        }
    }

    // freqHz explicitly, NOT a note name and NOT the default: VoiceFactory resolves the synth
    // branch as `freqHz ?: 0.0`, and a 0 Hz sine is flat DC — every solo row would measure silence
    // against silence and pass.
    fun tone(sourceId: String, pan: Double, solo: Double? = null, cut: Int? = null): VoiceData =
        VoiceData.empty.copy(
            sound = "sine",
            freqHz = 440.0,
            pan = pan,
            solo = solo,
            sourceId = sourceId,
            cut = cut,
        )

    /** Peak of the hard-right background voice after [blocks] blocks, with solo set to [solo]. */
    fun backgroundAfter(blocks: Int, solo: Double?, leadSourceId: String = "lead"): Double {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = leadSourceId, pan = 0.0, solo = solo))
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        return rig.renderPeaks(blocks).second
    }

    // ── Solo: the background ducks, on a ramp, to a floor that is not silence ────────────────────

    "solo: with no solo anywhere the background renders at full gain" {
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)

        // The reference every other solo row is measured against — if this is silent the pan
        // convention is wrong and the whole file is vacuous.
        unsoloed shouldBeGreaterThan 1e-6
    }

    "solo: a soloing voice ducks every OTHER source" {
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)
        val ducked = backgroundAfter(blocks = rampBlocks, solo = 1.0)

        ducked shouldBeLessThan unsoloed * 0.2
    }

    "solo: the soloed source itself is NOT ducked" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "lead", pan = 0.0, solo = 1.0))
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val (lead, bed) = rig.renderPeaks(rampBlocks)

        // Left is the soloed voice at gain 1.0; right is the bed at the 0.05 floor.
        lead shouldBeGreaterThan bed * 5.0
    }

    "solo: the duck is a RAMP, not a jump — one block in, the background is still near full" {
        val unsoloed = backgroundAfter(blocks = 2, solo = null)
        val ducked = backgroundAfter(blocks = 2, solo = 1.0)

        // Ease.InOut.cubic at progress ~0.0018 is ~2e-8 of the way down. Anything that replaces the
        // ramp with its target lands at 0.05 here and this row goes red.
        ducked shouldBeGreaterThan unsoloed * 0.9
    }

    "solo: the floor is an attenuation (0.05), not a mute" {
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)
        val ducked = backgroundAfter(blocks = rampBlocks, solo = 1.0)

        // targetGain = 1.0 - (maxSolo * 0.95). Bracketing BOTH sides pins the 0.95: a coefficient of
        // 1.0 silences the bed and fails the lower bound; a smaller one fails the upper.
        ducked shouldBeGreaterThan unsoloed * 0.02
        ducked shouldBeLessThan unsoloed * 0.10
    }

    "solo: the strongest solo wins — the duck follows the MAX, not the first or the sum" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "half", pan = 0.0, solo = 0.5))
        rig.schedule(0.0, tone(sourceId = "full", pan = 0.0, solo = 1.0))
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val ducked = rig.renderPeaks(rampBlocks).second
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)

        // max => 0.05. A `min` or a first-wins read gives 0.525 and fails; a sum saturates lower.
        ducked shouldBeLessThan unsoloed * 0.10
        ducked shouldBeGreaterThan unsoloed * 0.02
    }

    "solo: a soloing voice with no sourceId does not engage solo at all" {
        val rig = Rig()
        rig.schedule(
            0.0,
            VoiceData.empty.copy(sound = "sine", freqHz = 440.0, pan = 0.0, solo = 1.0, sourceId = null),
        )
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val ducked = rig.renderPeaks(rampBlocks).second
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)

        // Guards the `&& voice.sourceId != null` conjunct in process(): drop it and the bed ducks.
        ducked shouldBeGreaterThan unsoloed * 0.9
    }

    // ── Cut / choke groups: membership of the active list ────────────────────────────────────────

    "cut: a new voice hard-kills the voice already sounding in its cut group" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "hat", pan = 0.5, cut = 1))
        rig.renderPeaks(2)
        rig.activeCount shouldBe 1

        rig.schedule(rig.nowSec(), tone(sourceId = "hat", pan = 0.5, cut = 1))
        rig.renderPeaks(2)

        // The arriving voice replaced the sounding one rather than joining it.
        rig.activeCount shouldBe 1
    }

    "cut: the arriving voice does not cut ITSELF" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "hat", pan = 0.5, cut = 1))
        rig.renderPeaks(2)

        // The cut sweep runs BEFORE the new voice is added. Move the add above it and this is 0.
        rig.activeCount shouldBe 1
    }

    "cut: voices in DIFFERENT cut groups coexist" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "hat", pan = 0.5, cut = 1))
        rig.renderPeaks(2)

        rig.schedule(rig.nowSec(), tone(sourceId = "oh", pan = 0.5, cut = 2))
        rig.renderPeaks(2)

        rig.activeCount shouldBe 2
    }

    "cut: a voice with no cut group kills nothing" {
        val rig = Rig()
        // BOTH ungrouped, deliberately. With one grouped voice the row would pass even with the
        // `if (cut != null)` gate deleted, because a null sweep would find no null-cut victim to
        // remove — it takes two ungrouped voices to make the gate's absence observable.
        rig.schedule(0.0, tone(sourceId = "pad", pan = 0.5, cut = null))
        rig.renderPeaks(2)
        rig.activeCount shouldBe 1

        rig.schedule(rig.nowSec(), tone(sourceId = "str", pan = 0.5, cut = null))
        rig.renderPeaks(2)

        rig.activeCount shouldBe 2
    }
})
