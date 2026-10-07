/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
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
 * active list (since lifecycle step 4 after the choked voice's 4 ms fade), so `getActiveVoiceCount()` answers it exactly and no DSP measurement can blur it.
 * Solo is a question about *gain*, so it has to be heard: the soloed voice is panned hard left and
 * the background hard right (equal-power pan, `SendRenderer:32-39`), which puts each one in its own
 * channel and lets the right channel be read as "the background, alone".
 */
class VoiceSchedulerSoloCutSpec : StringSpec({

    val sampleRate = 48_000
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES
    val blockDurationSec = blockFrames.toDouble() / sampleRate

    // VoiceScheduler.soloMuteRamp is ValueRamp(1.0, duration = SOLO_RAMP_SEC = 1.5, Ease.InOut.cubic), stepped
    // once per block, so a completed transition takes ceil(1.5 / blockDuration) blocks.
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

        /** Renders [blocks] blocks and returns the left channel of all of them, in order. */
        fun renderLeft(blocks: Int): DoubleArray {
            val out = DoubleArray(blocks * blockFrames)

            for (block in 0 until blocks) {
                mix.clear()
                engine.renderInto(mix, clock.cursorFrame)
                mix.left.copyInto(destination = out, destinationOffset = block * blockFrames, startIndex = 0, endIndex = blockFrames)
                clock.cursorFrame += blockFrames
            }

            return out
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

    /** What `solo(...)` puts over a rest: engine state only, never a voice. */
    fun soloControl(sourceId: String?, solo: Double): VoiceData =
        VoiceData.empty.copy(control = true, solo = solo, sourceId = sourceId)

    /** Peak of the hard-right background voice after [blocks] blocks, with solo set to [solo]. */
    fun backgroundAfter(blocks: Int, solo: Double?, leadSourceId: String = "lead"): Double {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = leadSourceId, pan = 0.0, solo = solo))
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        return rig.renderPeaks(blocks).second
    }

    /** Renders [blocks] blocks and returns the (left, right) peak of EVERY block. */
    fun Rig.peaksPerBlock(blocks: Int): List<Pair<Double, Double>> = List(blocks) { renderPeaks(1) }

    /** The index of the block that starts at [sec] (or the last one before it), counted in frames. */
    fun blocksFor(sec: Double): Int = (sec * sampleRate / blockFrames).toInt()

    // ── Solo: the background ducks, on a ramp, to `1 - amount` ─────────────────────────────────────

    "solo: with no solo anywhere the background renders at full gain" {
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)

        // The reference every other solo row is measured against — if this is silent the pan
        // convention is wrong and the whole file is vacuous.
        unsoloed shouldBeGreaterThan 1e-6
    }

    "solo: a soloing voice ducks every OTHER source" {
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)
        val ducked = backgroundAfter(blocks = rampBlocks, solo = 0.95)

        ducked shouldBeLessThan unsoloed * 0.2
    }

    "solo: the soloed source itself is NOT ducked" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "lead", pan = 0.0, solo = 0.95))
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val (lead, bed) = rig.renderPeaks(rampBlocks)

        // Left is the soloed voice at gain 1.0; right is the bed at 0.05.
        lead shouldBeGreaterThan bed * 5.0
    }

    "solo: the duck is a RAMP, not a jump — one block in, the background is still near full" {
        val unsoloed = backgroundAfter(blocks = 2, solo = null)
        val ducked = backgroundAfter(blocks = 2, solo = 1.0)

        // Ease.InOut.cubic at progress ~0.0018 is ~2e-8 of the way down. Anything that replaces the
        // ramp with its target lands at 0.0 here and this row goes red.
        ducked shouldBeGreaterThan unsoloed * 0.9
    }

    "solo: after the ramp the background plays at 1 - amount (decided 2026-10-07): 0.95, 0.7, 1.0" {
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)

        // The bed is the same voice in every rig and its multiplier is constant within a block, so the
        // peak ratio IS the multiplier. The expected values are the decision's, written out, not derived.
        withClue("solo(0.95): the others at 5 %") {
            (backgroundAfter(blocks = rampBlocks, solo = 0.95) / unsoloed) shouldBe (0.05 plusOrMinus 1e-12)
        }
        withClue("solo(0.7): the others at 0.3") {
            (backgroundAfter(blocks = rampBlocks, solo = 0.7) / unsoloed) shouldBe (0.3 plusOrMinus 1e-12)
        }
    }

    "solo: solo(1.0) is exact silence for the others after the ramp, not an attenuation" {
        // The ramp ends ON its target (ValueRamp), so the bed's multiplier is exactly 0.0. The old
        // `1 - amount * 0.95` left it at 0.05 (-26 dB).
        backgroundAfter(blocks = rampBlocks, solo = 1.0) shouldBe 0.0
    }

    "solo: the strongest solo wins: the duck follows the MAX, not the first, the last or the sum" {
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)

        // Both orders: a first-wins and a last-wins read each fail one of them.
        for (strongFirst in listOf(true, false)) {
            val rig = Rig()
            val half = tone(sourceId = "half", pan = 0.0, solo = 0.5)
            val strong = tone(sourceId = "strong", pan = 0.0, solo = 0.95)

            if (strongFirst) {
                rig.schedule(0.0, strong)
                rig.schedule(0.0, half)
            } else {
                rig.schedule(0.0, half)
                rig.schedule(0.0, strong)
            }

            rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

            // max => 0.05. A `min`, a first-wins or a last-wins read gives 0.5 in one order; a sum gives -0.45.
            withClue("strong first = $strongFirst") {
                (rig.renderPeaks(rampBlocks).second / unsoloed) shouldBe (0.05 plusOrMinus 1e-12)
            }
        }
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

        // Guards the sourceId requirement of the record: drop it and the bed ducks.
        ducked shouldBe unsoloed
    }

    // ── Solo state from control events (bugfix-solo-rests-and-amount, 2026-10-07) ──────────────────

    "solo: a control-only solo event ducks the background and builds no voice" {
        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 1.0))
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val (_, bed) = rig.renderPeaks(rampBlocks)

        withClue("the bed is silent: the control event alone carries the solo") { bed shouldBe 0.0 }
        withClue("only the bed is a voice") { rig.activeCount shouldBe 1 }
    }

    "solo: the duck holds through a rest covered only by control events, the ramp never rises" {
        // The soloed note sounds for 0.5 s; from there to 4 s only control events (in 0.5 s chunks, the
        // way the frontend queries) say the solo is alive. Before 2026-10-07 the amount came from active
        // voices only, so the background ramped back up in every rest.
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "lead", pan = 0.0, solo = 0.95), durSec = 0.5)

        for (chunk in 1 until 8) {
            rig.schedule(chunk * 0.5, soloControl(sourceId = "lead", solo = 0.95), durSec = 0.5)
        }

        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val unsoloed = Rig().apply { schedule(0.0, tone(sourceId = "bed", pan = 1.0)) }.peaksPerBlock(blocksFor(3.9))
        val ducked = rig.peaksPerBlock(blocksFor(3.9))

        for (block in rampBlocks until ducked.size) {
            withClue("block $block (${block * blockDurationSec} s)") {
                (ducked[block].second / unsoloed[block].second) shouldBe (0.05 plusOrMinus 1e-12)
            }
        }
    }

    "solo: a soloed note and the control event over its cycle start together; the note's shorter gate never ends the solo" {
        // The heap pops equal start times in no fixed order. Both orders, the note's 0.5 s gate against the
        // control event's 2 s: the solo lasts the 2 s either way.
        for (noteFirst in listOf(true, false)) {
            val rig = Rig()
            val note = tone(sourceId = "lead", pan = 0.0, solo = 0.95)
            val control = soloControl(sourceId = "lead", solo = 0.95)

            if (noteFirst) {
                rig.schedule(0.0, note, durSec = 0.5)
                rig.schedule(0.0, control, durSec = 2.0)
            } else {
                rig.schedule(0.0, control, durSec = 2.0)
                rig.schedule(0.0, note, durSec = 0.5)
            }

            rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
            val reference = Rig().apply { schedule(0.0, tone(sourceId = "bed", pan = 1.0)) }

            val soloed = rig.peaksPerBlock(blocksFor(1.95))
            val unsoloed = reference.peaksPerBlock(blocksFor(1.95))

            withClue("note first = $noteFirst: still ducked at 1.95 s") {
                (soloed.last().second / unsoloed.last().second) shouldBe (0.05 plusOrMinus 1e-12)
            }
        }
    }

    "solo: a control event two blocks after the previous one ended does not restart the ramp (the grace)" {
        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 2.0)
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
        val reference = Rig()
        reference.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        // Through the block that starts at 2.0 s, where the first event ends, and one block more: the next
        // event arrives two blocks after the end, inside the grace (4 blocks).
        val untilEnd = blocksFor(2.0) + 1
        val first = rig.peaksPerBlock(untilEnd)
        val firstReference = reference.peaksPerBlock(untilEnd)

        rig.renderPeaks(1)
        reference.renderPeaks(1)
        rig.schedule(rig.nowSec(), soloControl(sourceId = "lead", solo = 0.95), durSec = 2.0)

        val second = rig.peaksPerBlock(blocksFor(1.0))
        val secondReference = reference.peaksPerBlock(blocksFor(1.0))

        withClue("ducked before the seam") { (first.last().second / firstReference.last().second) shouldBe (0.05 plusOrMinus 1e-12) }

        for (block in second.indices) {
            withClue("block $block after the seam") {
                (second[block].second / secondReference[block].second) shouldBe (0.05 plusOrMinus 1e-12)
            }
        }
    }

    "solo: after the last control event the background comes back, and the soloed voice stays at 1.0 all the way" {
        // The lead sounds for 30 s and carries no solo itself; control events solo its source for 2 s.
        // After them the background ramps back over 1.5 s, and the lead must not dip on the way: the
        // hold (2 s) is at least the ramp. Reference: the same lead with no solo anywhere.
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "lead", pan = 0.0))
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 2.0)
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
        val reference = Rig()
        reference.schedule(0.0, tone(sourceId = "lead", pan = 0.0))
        reference.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val blocks = blocksFor(5.0)
        val soloed = rig.peaksPerBlock(blocks)
        val unsoloed = reference.peaksPerBlock(blocks)

        for (block in 0 until blocks) {
            withClue("block $block (${block * blockDurationSec} s): the lead at full level") {
                soloed[block].first shouldBe (unsoloed[block].first plusOrMinus 1e-12)
            }
        }

        withClue("ducked at 1.9 s") {
            (soloed[blocksFor(1.9)].second / unsoloed[blocksFor(1.9)].second) shouldBe (0.05 plusOrMinus 1e-12)
        }
        withClue("back to full level at 4 s") {
            (soloed[blocksFor(4.0)].second / unsoloed[blocksFor(4.0)].second) shouldBe (1.0 plusOrMinus 1e-12)
        }
    }

    "solo: a LATE solo control event still records its state, like a late master" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
        rig.renderPeaks(10)

        // Starts at 0.0, ten blocks already rendered past: a voice would be dropped as late.
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95))
        val ducked = rig.renderPeaks(rampBlocks).second

        val reference = Rig()
        reference.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
        val unsoloed = reference.renderPeaks(10 + rampBlocks).second

        (ducked / unsoloed) shouldBe (0.05 plusOrMinus 1e-12)
    }

    "solo: amount 0, a NaN or infinite amount, and a null sourceId engage nothing" {
        val unsoloed = backgroundAfter(blocks = rampBlocks, solo = null)

        for ((name, control) in listOf(
            "amount 0" to soloControl(sourceId = "lead", solo = 0.0),
            "amount NaN" to soloControl(sourceId = "lead", solo = Double.NaN),
            "amount +Inf" to soloControl(sourceId = "lead", solo = Double.POSITIVE_INFINITY),
            "null sourceId" to soloControl(sourceId = null, solo = 1.0),
        )) {
            val rig = Rig()
            rig.schedule(0.0, control)
            rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

            withClue(name) { rig.renderPeaks(rampBlocks).second shouldBe unsoloed }
        }
    }

    // ── Solo on the realtime path: refreshed every block while a voice of the source holds its gate ───────────

    /** A held realtime key of the soloed "lead" source, hard left. */
    fun Rig.press(liveId: Int, solo: Double = 0.95, cut: Int? = null, sound: String = "sine") {
        engine.scheduler.startRealtimeVoice(
            playbackId = "song",
            voice = RealtimeVoice(
                liveId = liveId,
                data = tone(sourceId = "lead", pan = 0.0, solo = solo, cut = cut).copy(sound = sound),
                gateDurSec = null,
            ),
        )
    }

    fun Rig.release(liveId: Int) = engine.scheduler.stopRealtimeVoice(playbackId = "song", liveId = liveId)

    /** The bed (hard right) in [rig] against the same bed alone in [reference], after [blocks] more blocks. */
    fun bedRatio(rig: Rig, reference: Rig, blocks: Int): Double =
        rig.renderPeaks(blocks).second / reference.renderPeaks(blocks).second

    fun bedRigs(): Pair<Rig, Rig> {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
        val reference = Rig()
        reference.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        return rig to reference
    }

    // The ramp back takes rampBlocks; the last gate's solo ends one block plus the grace (4 blocks) after it closes.
    // So "ended" is read rampBlocks + 10 blocks after the last gate closed: the background is back at exactly 1.0.

    "solo: a HELD realtime voice solos while held, and its note-off ends the solo" {
        val (rig, reference) = bedRigs()
        rig.press(liveId = 1)

        withClue("ducked while held") { bedRatio(rig = rig, reference = reference, blocks = rampBlocks) shouldBe (0.05 plusOrMinus 1e-12) }

        rig.release(liveId = 1)

        withClue("back to full level after the note-off") {
            bedRatio(rig = rig, reference = reference, blocks = rampBlocks + 10) shouldBe (1.0 plusOrMinus 1e-12)
        }
    }

    "solo: legato on a realtime solo: key 1's tail ending does not end the solo while key 2 is held" {
        val (rig, reference) = bedRigs()
        rig.press(liveId = 1)
        bedRatio(rig = rig, reference = reference, blocks = rampBlocks)

        // Key 2 goes down while key 1 rings out; key 1's tail ends and leaves the list with key 2 held.
        rig.release(liveId = 1)
        rig.press(liveId = 2)

        withClue("still ducked while key 2 is held") {
            bedRatio(rig = rig, reference = reference, blocks = rampBlocks + 10) shouldBe (0.05 plusOrMinus 1e-12)
        }

        rig.release(liveId = 2)

        withClue("ended after the last note-off") { bedRatio(rig = rig, reference = reference, blocks = rampBlocks + 10) shouldBe (1.0 plusOrMinus 1e-12) }
    }

    "solo: a mono (cut) realtime solo: key 2 cutting key 1 keeps the solo while key 2 is held" {
        val (rig, reference) = bedRigs()
        rig.press(liveId = 1, cut = 1)
        bedRatio(rig = rig, reference = reference, blocks = rampBlocks)

        // No note-off for key 1: key 2 in the same cut group fades it out (4 ms) and it leaves the list.
        rig.press(liveId = 2, cut = 1)

        withClue("still ducked while key 2 is held") {
            bedRatio(rig = rig, reference = reference, blocks = rampBlocks + 10) shouldBe (0.05 plusOrMinus 1e-12)
        }

        rig.release(liveId = 2)

        withClue("ended after the last note-off") { bedRatio(rig = rig, reference = reference, blocks = rampBlocks + 10) shouldBe (1.0 plusOrMinus 1e-12) }
    }

    "solo: two held realtime keys of one source: releasing one keeps the solo, releasing both ends it" {
        val (rig, reference) = bedRigs()
        rig.press(liveId = 1)
        rig.press(liveId = 2)
        bedRatio(rig = rig, reference = reference, blocks = rampBlocks)

        rig.release(liveId = 1)

        withClue("still ducked while key 2 is held") {
            bedRatio(rig = rig, reference = reference, blocks = rampBlocks + 10) shouldBe (0.05 plusOrMinus 1e-12)
        }

        rig.release(liveId = 2)

        withClue("ended after the last note-off") { bedRatio(rig = rig, reference = reference, blocks = rampBlocks + 10) shouldBe (1.0 plusOrMinus 1e-12) }
    }

    "solo: a held realtime note that made no voice (its sample is not loaded) solos nothing" {
        val (rig, reference) = bedRigs()
        rig.press(liveId = 1, solo = 1.0, sound = "no-such-sample")

        withClue("no voice was made") { rig.activeCount shouldBe 1 }
        withClue("the bed is not ducked") { bedRatio(rig = rig, reference = reference, blocks = rampBlocks) shouldBe 1.0 }
    }

    "solo: a HELD realtime voice that leaves without a note-off (cut by another source) ends its solo" {
        // The lead is cut by a silent timeline voice of its cut group, so no note-off ever comes. Once it is cut its
        // gate counts as closed and the refresh stops.
        val (rig, reference) = bedRigs()
        rig.press(liveId = 1, cut = 1)

        withClue("ducked while held") { bedRatio(rig = rig, reference = reference, blocks = rampBlocks) shouldBe (0.05 plusOrMinus 1e-12) }

        rig.schedule(rig.nowSec(), tone(sourceId = "hat", pan = 0.0, cut = 1).copy(gain = 0.0))

        withClue("back to full level after the cut") { bedRatio(rig = rig, reference = reference, blocks = rampBlocks + 10) shouldBe (1.0 plusOrMinus 1e-12) }
    }

    "solo: a voice entering or leaving protection ramps over one block, never a step (no click)" {
        // `solo(1.0)` on "x" (control events only) mutes everything else. The lead "y" is soloed from 2 s to 3 s:
        // at 2.0 s it enters protection (0 to 1.0), at 5.0 s (3 s plus the 2 s hold) it leaves it (1.0 to 0).
        // Each change is ramped across one 128-frame block, so the largest sample step stays at the sine's own
        // slope plus at most one 128th of its amplitude. A step inside one sample would be the full amplitude.
        // 301.3 Hz, not 440: a 440 Hz sine started at 0 crosses zero exactly at 2.0 s and 5.0 s, where a step
        // would hide.
        val lead = tone(sourceId = "y", pan = 0.0).copy(freqHz = 301.3)
        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "x", solo = 1.0))
        rig.schedule(0.0, lead)
        rig.schedule(2.0, soloControl(sourceId = "y", solo = 0.95), durSec = 1.0)
        val reference = Rig()
        reference.schedule(0.0, lead)

        val blocks = blocksFor(5.1)
        val soloed = rig.renderLeft(blocks)
        val unsoloed = reference.renderLeft(blocks)

        fun maxStep(samples: DoubleArray, fromSec: Double, toSec: Double): Double {
            var max = 0.0

            for (i in (fromSec * sampleRate).toInt() until (toSec * sampleRate).toInt()) {
                max = maxOf(max, abs(samples[i + 1] - samples[i]))
            }

            return max
        }

        val slope = maxStep(samples = unsoloed, fromSec = 1.0, toSec = 1.1)

        withClue("the reference sine has a slope") { slope shouldBeGreaterThan 1e-3 }
        withClue("muted before 2 s") { maxStep(samples = soloed, fromSec = 1.8, toSec = 1.95) shouldBe 0.0 }
        withClue("solo start at 2.0 s") { maxStep(samples = soloed, fromSec = 1.95, toSec = 2.05) shouldBeLessThan slope * 1.2 }
        withClue("heard between, at full level") { maxStep(samples = soloed, fromSec = 3.0, toSec = 3.1) shouldBe (maxStep(samples = unsoloed, fromSec = 3.0, toSec = 3.1) plusOrMinus 1e-12) }
        withClue("protection end at 5.0 s") { maxStep(samples = soloed, fromSec = 4.95, toSec = 5.05) shouldBeLessThan slope * 1.2 }
        withClue("muted after") { maxStep(samples = soloed, fromSec = 5.05, toSec = 5.09) shouldBe 0.0 }
    }

    "solo: a voice that starts while the others are muted is silent from its first sample (no ramp in from 1.0)" {
        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "x", solo = 1.0))
        rig.renderPeaks(rampBlocks)

        rig.schedule(rig.nowSec(), tone(sourceId = "late", pan = 1.0))

        rig.peaksPerBlock(3).forEach { (_, right) -> right shouldBe 0.0 }
        rig.activeCount shouldBe 1
    }

    "solo: a voice muted to 0 stays alive and is heard again mid-note when the solo ends" {
        // solo(1.0) for 2 s from control events; the bed's gate is open for 30 s. Muted, it keeps
        // rendering (its phase and envelope run on), so it comes back mid-note at its full level.
        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 1.0), durSec = 2.0)
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
        val reference = Rig()
        reference.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val blocks = blocksFor(4.5)
        val soloed = rig.peaksPerBlock(blocks)
        val unsoloed = reference.peaksPerBlock(blocks)

        withClue("silent at 1.9 s") { soloed[blocksFor(1.9)].second shouldBe 0.0 }
        withClue("still a voice while silent") { rig.activeCount shouldBe 1 }
        withClue("back at its full level at 4.4 s") {
            soloed[blocksFor(4.4)].second shouldBe (unsoloed[blocksFor(4.4)].second plusOrMinus 1e-12)
        }
    }

    "solo: a voice muted to 0 in its release is not culled for being muted" {
        // The bed's gate ends at 0.3 s and its release runs 8 s: audible all the way, but muted to 0 by
        // solo(1.0) from 1.5 s to 2 s. The cull reads the voice's level BEFORE the multiplier; read
        // after it, the 50 ms cull window would end the bed in the mute and it would never come back.
        val releaseSlot = (IgnitorDsl.Slots.adsr.release as IgnitorDsl.Param).name
        val bed = tone(sourceId = "bed", pan = 1.0).copy(ignitorParams = mapOf(releaseSlot to 8.0))

        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 1.0), durSec = 2.0)
        rig.schedule(0.0, bed, durSec = 0.3)
        val reference = Rig()
        reference.schedule(0.0, bed, durSec = 0.3)

        val blocks = blocksFor(4.5)
        val soloed = rig.peaksPerBlock(blocks)
        val unsoloed = reference.peaksPerBlock(blocks)

        withClue("silent at 1.9 s") { soloed[blocksFor(1.9)].second shouldBe 0.0 }
        withClue("the release is still audible unsoloed at 4.4 s") { unsoloed[blocksFor(4.4)].second shouldBeGreaterThan 1e-3 }
        withClue("and heard again at 4.4 s, at its unsoloed level") {
            soloed[blocksFor(4.4)].second shouldBe (unsoloed[blocksFor(4.4)].second plusOrMinus 1e-12)
        }
        withClue("still a voice") { rig.activeCount shouldBe 1 }
    }

    // ── Cut / choke groups: membership of the active list ────────────────────────────────────────
    //
    // Since lifecycle step 4 a cut FADES the choked voice (`Voice.cutOff`): it stays in the list, `Fading`, for
    // the 4 ms fade (`CUT_FADE_SECONDS`, 192 frames here, so the first block that starts at or after the fade end
    // is the third after the cutting onset) and leaves through the render loop. Every row below renders past
    // that, so "coexist" means coexisting after the fade.

    "cut: a new voice cuts the voice already sounding in its cut group: it fades, then leaves" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "hat", pan = 0.5, cut = 1))
        rig.renderPeaks(2)
        rig.activeCount shouldBe 1

        rig.schedule(rig.nowSec(), tone(sourceId = "hat", pan = 0.5, cut = 1))
        rig.renderPeaks(2)

        // Two blocks after the cutting onset the old voice is still fading (the fade ends 192 frames in).
        rig.activeCount shouldBe 2

        rig.renderPeaks(1)

        // The arriving voice replaced the sounding one rather than joining it.
        rig.activeCount shouldBe 1
    }

    "cut: the fade starts at the cutting voice's onset, mid-block too, and reaches zero 191 frames later" {
        // The cutting voice is silent (gain 0), so the mix is the cut voice alone. The reference rig is the same,
        // with the second voice in another group: the two mixes agree up to the cutting onset and part there.
        fun rig(secondCut: Int): Pair<Rig, DoubleArray> {
            val rig = Rig()
            rig.schedule(0.0, tone(sourceId = "hat", pan = 0.0, cut = 1))
            rig.renderPeaks(4)

            // 50 frames into the next block (the quarter frame keeps the floor away from a rounding edge).
            val onsetSec = (rig.clock.cursorFrame + 50.25) / sampleRate
            rig.schedule(onsetSec, tone(sourceId = "hat2", pan = 0.0, cut = secondCut).copy(gain = 0.0))

            return rig to rig.renderLeft(4)
        }

        val (_, cut) = rig(secondCut = 1)
        val (_, reference) = rig(secondCut = 2)
        val onset = 50

        for (i in 0 until onset) {
            withClue("frame $i: before the onset the cut voice is untouched") { cut[i] shouldBe reference[i] }
        }

        withClue("one frame after the onset the ramp is under way") { (abs(cut[onset + 1]) < abs(reference[onset + 1])) shouldBe true }

        for (i in onset + 191 until cut.size) {
            withClue("frame $i: silent from the ramp's zero") { (cut[i] == 0.0) shouldBe true }
        }

        withClue("the reference keeps sounding") { (reference.drop(onset + 192).maxOf { abs(it) } > 0.1) shouldBe true }
    }

    "cut: a cutting voice with a non-finite start is dropped (counted) and cuts nothing" {
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "hat", pan = 0.0, cut = 1))
        rig.renderPeaks(4)

        rig.schedule(Double.NaN, tone(sourceId = "hat2", pan = 0.0, cut = 1))
        val out = rig.renderLeft(4)

        withClue("dropped like a late voice") { rig.engine.scheduler.droppedVoiceCount("song") shouldBe 1 }
        withClue("the sounding voice was not cut") { rig.activeCount shouldBe 1 }
        withClue("and keeps sounding, finite") { (out.all { it.isFinite() } && out.takeLast(64).maxOf { abs(it) } > 0.1) shouldBe true }
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
        rig.renderPeaks(4)

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
        rig.renderPeaks(4)

        rig.activeCount shouldBe 2
    }
})
