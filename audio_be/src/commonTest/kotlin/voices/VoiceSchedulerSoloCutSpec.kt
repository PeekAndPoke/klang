/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
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
import io.peekandpoke.klang.common.infra.klangMapOf
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

    // VoiceScheduler.soloMuteRamp is SoloRamp(1.0, durationSec = SOLO_RAMP_SEC = 1.5), on the in-out cubic, stepped
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
        val engine = PlaybackEngine.create(context = context, playbackId = "song")
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

        // The in-out cubic at progress ~0.0018 is ~2e-8 of the way down. Anything that replaces the
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
        // The ramp ends ON its target (SoloRamp), so the bed's multiplier is exactly 0.0. The old
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
        // After them the background ramps back over 1.5 s, and the lead must not dip on the way: with no hold
        // since Q28, the no-dip rule keeps it at 1.0. Reference: the same lead with no solo anywhere.
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

    // ── Solo reacts at once: a source is soloed while its NEWEST event says so, no hold, no per-voice protection
    // (Q28 as answered 2026-10-13, engine follow-up 43; it replaced Q14's "a soloed voice is protected for its whole
    // life" and the 2 s hold after a source's last solo event) ──────────────────────────────────────────────

    /** A soloed pad with a 5 s release, hard left: its tail outlives its event by far. */
    fun pad(solo: Double? = 1.0): VoiceData = tone(sourceId = "pad", pan = 0.0, solo = solo).copy(ignitorParams = klangMapOf("adsr.release" to 5.0))

    /** A rig whose kick (hard right) solos at 1.0 from 0 s: the rest is muted once the 1.5 s ramp is down. */
    fun kickRig(): Rig = Rig().apply { schedule(0.0, tone(sourceId = "kick", pan = 1.0, solo = 1.0)) }

    /** The left peak of [peaks] (one pair per block) at [sec]. */
    fun List<Pair<Double, Double>>.leftAt(sec: Double): Double = this[blocksFor(sec)].first

    "solo: a soloed pad's release beside another live solo is ducked at once when its event ends (Q28)" {
        // `note("c3").sound(myPad).release(5).solo()` beside `s("bd*4").solo()`, the pad's one event from 2.0 to
        // 2.5 s, after the kick's ramp has muted the rest. Reference: the same pad with no kick. Before Q28's answer
        // the pad's voice was protected to its end; with only the 2 s hold it would ring at full level to 4.5 s.
        val rig = kickRig()
        rig.schedule(2.0, pad(), durSec = 0.5)
        val reference = Rig()
        reference.schedule(2.0, pad(), durSec = 0.5)

        val soloed = rig.peaksPerBlock(blocksFor(7.0))
        val alone = reference.peaksPerBlock(blocksFor(7.0))

        withClue("the tail still sounds at 4 s (the row is not vacuous)") { alone.leftAt(4.0) shouldBeGreaterThan 1e-3 }
        withClue("at full level while soloed (2.25 s)") { soloed.leftAt(2.25) shouldBe (alone.leftAt(2.25) plusOrMinus 1e-12) }

        for (block in blocksFor(2.55) until soloed.size) {
            // not exactly 0: the kick, hard right, leaks cos(pi / 2) = 6e-17 of itself into the left channel
            withClue("block $block (${block * blockDurationSec} s): the tail ducked to 1 - 1.0") {
                soloed[block].first shouldBeLessThan 1e-9
            }
        }
    }

    "solo: a plain note of the source after its solo ended is ducked" {
        val rig = Rig()
        rig.schedule(0.0, pad(), durSec = 0.5)
        rig.schedule(0.0, tone(sourceId = "kick", pan = 1.0, solo = 1.0))
        rig.peaksPerBlock(blocksFor(7.0))

        withClue("the pad's voice has ended") { rig.activeCount shouldBe 1 }

        // the same source again, a note without solo: nothing solos it, so the kick's live solo silences it
        rig.schedule(rig.nowSec(), pad(solo = null), durSec = 1.0)
        val (padPeak, kickPeak) = rig.renderPeaks(blocksFor(0.5))

        withClue("the kick still sounds") { kickPeak shouldBeGreaterThan 1e-3 }
        withClue("the plain pad note is ducked to 1 - 1.0") { padPeak shouldBeLessThan 1e-9 }
    }

    "solo: solo(1) on a release(5) pad, then the same text with solo(0): the whole source is ducked at once from the first solo(0) event, the soloed tail included" {
        // The old Q28 example: the pad soloed in the cycle from 2 to 4 s, then the same source with amount 0 in the
        // cycles from 4 to 10 s, all with a 5 s release, beside the soloed kick. Left is the whole pad source.
        fun Rig.song() {
            schedule(2.0, pad(), durSec = 2.0)

            for (cycle in 2..4) {
                schedule(cycle * 2.0, pad(solo = 0.0), durSec = 2.0)
            }
        }

        val peaks = kickRig().apply { song() }.peaksPerBlock(blocksFor(9.0))
        val alone = Rig().apply { song() }.peaksPerBlock(blocksFor(9.0))

        withClue("the soloed cycle at full level (3.0 s)") { peaks.leftAt(3.0) shouldBe (alone.leftAt(3.0) plusOrMinus 1e-12) }

        for (sec in listOf(4.05, 4.5, 6.0, 8.5)) {
            withClue("the pad sounds alone at $sec s (not vacuous)") { alone.leftAt(sec) shouldBeGreaterThan 1e-3 }
            withClue("the source ducked at $sec s, its soloed tail and its solo(0) notes alike") { peaks.leftAt(sec) shouldBeLessThan 1e-9 }
        }
    }

    "solo: solo(1) edited to solo(0) at the same call site: the source drops back at the first zero event, its notes still sounding" {
        // The live edit: the soloed note (0 to 2 s), then overlapping notes of the same source with amount 0 from
        // 1.9 s, which is what the same `solo(...)` call site sends after the edit. The kick solos all the way.
        val rig = kickRig()
        rig.schedule(0.0, tone(sourceId = "pad", pan = 0.0, solo = 1.0), durSec = 2.0)

        for (k in 1..4) {
            rig.schedule(k * 1.9, tone(sourceId = "pad", pan = 0.0, solo = 0.0), durSec = 2.0)
        }

        val peaks = rig.peaksPerBlock(blocksFor(8.0))

        withClue("soloed at 1.85 s") { peaks.leftAt(1.85) shouldBeGreaterThan 1e-3 }

        for (sec in listOf(1.95, 2.5, 3.5, 6.0, 7.5)) {
            withClue("ducked at $sec s, a note of the source sounding") { peaks.leftAt(sec) shouldBeLessThan 1e-9 }
        }
    }

    "solo: solo(\"<1 0 0 0>\") solos the soloed cycles, not one block of the zeros between them" {
        // One note per 2 s cycle, each overlapping the next by 0.1 s; the amount is 1 in cycles 0 and 4, 0 in the rest.
        val rig = kickRig()

        for (cycle in 0 until 8) {
            val amount = if (cycle % 4 == 0) 1.0 else 0.0

            rig.schedule(cycle * 2.0, tone(sourceId = "pad", pan = 0.0, solo = amount), durSec = 2.1)
        }

        val peaks = rig.peaksPerBlock(blocksFor(16.0))

        for (sec in listOf(1.0, 1.95, 9.0, 9.95)) {
            withClue("soloed at $sec s") { peaks.leftAt(sec) shouldBeGreaterThan 1e-3 }
        }

        for (sec in listOf(2.05, 3.5, 5.0, 7.5, 10.05, 13.0, 15.5)) {
            withClue("ducked at $sec s (a zero cycle)") { peaks.leftAt(sec) shouldBeLessThan 1e-9 }
        }
    }

    "solo: amount 0, a NaN or an infinity as a source's newest event ends its running solo" {
        // A soloed pad note from 0 to 4 s beside the kick; at 2.0 s the same source sends a control event with X.
        for ((name, amount) in listOf("amount 0" to 0.0, "amount NaN" to Double.NaN, "amount +Inf" to Double.POSITIVE_INFINITY)) {
            val rig = kickRig()
            rig.schedule(0.0, tone(sourceId = "pad", pan = 0.0, solo = 1.0), durSec = 4.0)
            rig.schedule(2.0, soloControl(sourceId = "pad", solo = amount), durSec = 2.0)

            val peaks = rig.peaksPerBlock(blocksFor(3.6))

            withClue("$name: soloed at 1.9 s") { peaks.leftAt(1.9) shouldBeGreaterThan 1e-3 }

            for (sec in listOf(2.05, 2.5, 3.5)) {
                withClue("$name: ducked at $sec s") { peaks.leftAt(sec) shouldBeLessThan 1e-9 }
            }
        }
    }

    "solo: an event with an EARLIER start than the source's newest changes nothing" {
        // The heap pops in start order, so an older event can only arrive late, after a newer one was recorded.
        // The newer says 0 (the solo ended at 1.0 s); a late event from 0.5 s with amount 1 must not solo it again.
        val rig = kickRig()
        rig.schedule(0.0, tone(sourceId = "pad", pan = 0.0, solo = 1.0), durSec = 1.0)
        rig.schedule(1.0, soloControl(sourceId = "pad", solo = 0.0), durSec = 3.0)
        rig.peaksPerBlock(blocksFor(2.0))

        rig.schedule(0.5, soloControl(sourceId = "pad", solo = 1.0), durSec = 3.0)
        rig.schedule(rig.nowSec(), tone(sourceId = "pad", pan = 0.0))

        val peaks = rig.peaksPerBlock(blocksFor(1.0))

        withClue("the pad stays ducked") { peaks.last().first shouldBeLessThan 1e-9 }
    }

    "solo: a soloed voice with no source id solos nothing and is ducked" {
        val noSource = kickRig()
        noSource.schedule(0.0, pad().copy(sourceId = null), durSec = 1.0)

        val peaks = noSource.peaksPerBlock(blocksFor(3.0))

        for (sec in listOf(1.6, 2.5)) {
            withClue("ducked at $sec s") { peaks.leftAt(sec) shouldBeLessThan 1e-9 }
        }
    }

    "solo: a held realtime key of the source with no solo of its own is ducked once the source's solo ends" {
        // A soloed timeline note of "lead" from 0 to 1 s, and a realtime key of "lead" held all the time without a solo.
        val rig = kickRig()
        rig.schedule(0.0, tone(sourceId = "lead", pan = 0.0, solo = 0.95), durSec = 1.0)
        rig.engine.scheduler.startRealtimeVoice(
            playbackId = "song",
            voice = RealtimeVoice(liveId = 1, data = tone(sourceId = "lead", pan = 0.0), gateDurSec = null),
        )

        val peaks = rig.peaksPerBlock(blocksFor(6.0))

        withClue("soloed at 0.9 s") { peaks.leftAt(0.9) shouldBeGreaterThan 1e-3 }

        for (sec in listOf(1.6, 3.5, 5.5)) {
            withClue("the held key ducked at $sec s") { peaks.leftAt(sec) shouldBeLessThan 1e-9 }
        }
    }

    "solo: solo() then solo(0) with no other solo: the part that was soloed stays at full level while the rest comes back (no dip)" {
        // Control events solo the lead (hard left, 30 s, no solo of its own) at 0.95 for 2 s, then say 0. Without the
        // no-dip rule the lead would drop to the background's 0.05 at 2 s and fade back in over 1.5 s.
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "lead", pan = 0.0))
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 2.0)
        rig.schedule(2.0, soloControl(sourceId = "lead", solo = 0.0), durSec = 28.0)
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

        withClue("the bed ducked at 1.9 s") {
            (soloed[blocksFor(1.9)].second / unsoloed[blocksFor(1.9)].second) shouldBe (0.05 plusOrMinus 1e-12)
        }
        withClue("the bed on its way back at 2.5 s") {
            (soloed[blocksFor(2.5)].second / unsoloed[blocksFor(2.5)].second) shouldBeLessThan 0.9
        }
        withClue("the bed back at full level at 4 s") {
            (soloed[blocksFor(4.0)].second / unsoloed[blocksFor(4.0)].second) shouldBe (1.0 plusOrMinus 1e-12)
        }
    }

    "solo: a voice that starts while the background comes back starts on the ramp, not at full level" {
        // The no-dip rule holds a voice that WAS above the ramp; a new voice has no level yet and joins the ramp.
        val late = tone(sourceId = "late", pan = 1.0)
        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 2.0)
        rig.schedule(2.5, late)
        val reference = Rig()
        reference.schedule(2.5, late)

        val blocks = blocksFor(2.6)
        val ratio = rig.peaksPerBlock(blocks).last().second / reference.peaksPerBlock(blocks).last().second

        withClue("on the way back at 2.6 s") {
            ratio shouldBeGreaterThan 0.05
            ratio shouldBeLessThan 0.9
        }
    }

    "solo: a removed solo ends with its last control event, not with a long soloed note's gate (round 1 MAJOR)" {
        // `note("a3").slow(4).solo("<[1 ~ ~ ~ ~ ~ ~ ~] ~ ~ ~>")` and the same after `.solo()` is removed: the control
        // pieces (1/8 cycle, 0.25 s here) stop, and a soloed note started on the last grid line (sharing its start, in
        // either pop order) or inside the last piece rings 8 s on. The solo must end within the piece plus the grace:
        // the bed (hard right) starts back up at once. Before the fix the note's gate held it down to its end.
        for (case in listOf("note first", "control first", "note inside the piece")) {
            val rig = Rig()
            val note = tone(sourceId = "lead", pan = 0.0, solo = 0.95)

            for (piece in 0 until 7) {
                rig.schedule(piece * 0.25, soloControl(sourceId = "lead", solo = 0.95), durSec = 0.25)
            }

            when (case) {
                "note first" -> {
                    rig.schedule(1.75, note, durSec = 8.0)
                    rig.schedule(1.75, soloControl(sourceId = "lead", solo = 0.95), durSec = 0.25)
                }
                "control first" -> {
                    rig.schedule(1.75, soloControl(sourceId = "lead", solo = 0.95), durSec = 0.25)
                    rig.schedule(1.75, note, durSec = 8.0)
                }
                else -> {
                    rig.schedule(1.75, soloControl(sourceId = "lead", solo = 0.95), durSec = 0.25)
                    rig.schedule(1.8, note, durSec = 8.0)
                }
            }

            rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
            val reference = Rig().apply { schedule(0.0, tone(sourceId = "bed", pan = 1.0)) }

            val blocks = blocksFor(3.7)
            val soloed = rig.peaksPerBlock(blocks)
            val unsoloed = reference.peaksPerBlock(blocks)

            fun bedRatio(sec: Double): Double = soloed[blocksFor(sec)].second / unsoloed[blocksFor(sec)].second

            withClue("$case: ducked at 1.95 s") { bedRatio(1.95) shouldBe (0.05 plusOrMinus 1e-12) }
            withClue("$case: on its way back at 2.3 s (the solo ended near 2.0 s)") { bedRatio(2.3) shouldBeGreaterThan 0.06 }
            withClue("$case: back at full level at 3.6 s") { bedRatio(3.6) shouldBe (1.0 plusOrMinus 1e-12) }
        }
    }

    "solo: a voice that loses its solo while another solo is live fades over at least the cut fade (4 ms), at once" {
        // The kick solos at 1.0 from 0 s (the rest muted from 1.5 s); "y" (hard left, 301.3 Hz) is soloed from 2.0 to
        // 2.5 s by a control event. When its solo ends it falls to 0: over CUT_FADE_SECONDS (two blocks), not inside
        // one block (2.7 ms, a tick). Read as the ratio to the same tone alone, on samples where the tone is loud.
        val lead = tone(sourceId = "y", pan = 0.0).copy(freqHz = 301.3)
        val rig = kickRig()
        rig.schedule(0.0, lead)
        rig.schedule(2.0, soloControl(sourceId = "y", solo = 0.95), durSec = 0.5)
        val reference = Rig()
        reference.schedule(0.0, lead)

        val blocks = blocksFor(2.6)
        val soloed = rig.renderLeft(blocks)
        val alone = reference.renderLeft(blocks)
        val loud = 0.5 * (0 until blocksFor(2.4) * blockFrames).maxOf { abs(alone[it]) }

        var firstFalling = -1
        var firstSilent = -1

        for (i in (2.4 * sampleRate).toInt() until (2.59 * sampleRate).toInt()) {
            if (abs(alone[i]) < loud) {
                continue
            }

            val ratio = abs(soloed[i]) / abs(alone[i])

            if (firstFalling < 0 && ratio < 0.98) {
                firstFalling = i
            }

            if (firstSilent < 0 && ratio < 0.02) {
                firstSilent = i
            }
        }

        withClue("it falls (at $firstFalling) and goes silent (at $firstSilent)") {
            firstFalling shouldBeGreaterThan (2.5 * sampleRate).toInt()
            firstSilent shouldBeGreaterThan firstFalling
        }
        withClue("the fall takes at least most of 4 ms (192 frames), not one 128-frame block") {
            (firstSilent - firstFalling) shouldBeGreaterThan 170
        }
        withClue("and is done within 20 ms of the event's end: it reacts at once") {
            firstSilent shouldBeLessThan (2.52 * sampleRate).toInt()
        }
    }

    "solo: a NEW note of a source that just lost its solo plays at full level, not on the ramp (round 2 MAJOR)" {
        // The lead ("lead", hard left) soloed at 0.95 by control events from 0 to 2 s, then either `solo(0)` (control
        // events with amount 0) or nothing (the `.solo()` removed). Its next note starts at 2.5 s, while the bed
        // (hard right) is still on its way back: it plays at full level at once. Before the fix it started on the ramp
        // (0.29 at 2.5 s) and faded in over 1.5 s.
        for (case in listOf("solo(0)", "solo removed")) {
            val rig = Rig()
            rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 2.0)

            if (case == "solo(0)") {
                rig.schedule(2.0, soloControl(sourceId = "lead", solo = 0.0), durSec = 28.0)
            }

            rig.schedule(2.5, tone(sourceId = "lead", pan = 0.0))
            rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
            val reference = Rig()
            reference.schedule(2.5, tone(sourceId = "lead", pan = 0.0))
            reference.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

            val blocks = blocksFor(4.0)
            val soloed = rig.peaksPerBlock(blocks)
            val unsoloed = reference.peaksPerBlock(blocks)

            withClue("$case: the bed still on its way back at 2.55 s") {
                (soloed[blocksFor(2.55)].second / unsoloed[blocksFor(2.55)].second) shouldBeLessThan 0.9
            }

            for (block in blocksFor(2.5) until blocks) {
                withClue("$case, block $block (${block * blockDurationSec} s): the new lead note at full level") {
                    soloed[block].first shouldBe (unsoloed[block].first plusOrMinus 1e-12)
                }
            }
        }
    }

    "solo: a release ends when the background is back, or when another solo starts: later notes start on the ramp again" {
        // "lead" soloed at 0.95 from 0 to 2 s, then released. Case "back": the bed is back by 3.6 s; another solo ("x",
        // control only) from 4.0 to 6.0 s, and a new lead note at 6.3 s, on the ramp back. Case "fell": "x" solos from
        // 2.5 to 3.0 s, before the ramp is back, and a new lead note at 3.3 s. In both the lead is background again: its
        // note starts on the ramp, below full level, like any other new voice.
        for ((case, xFrom, noteAt) in listOf(Triple("back", 4.0, 6.3), Triple("fell", 2.5, 3.3))) {
            val xDur = if (case == "back") 2.0 else 0.5
            val rig = Rig()
            rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 2.0)
            rig.schedule(xFrom, soloControl(sourceId = "x", solo = 0.95), durSec = xDur)
            rig.schedule(noteAt, tone(sourceId = "lead", pan = 0.0))
            val reference = Rig()
            reference.schedule(noteAt, tone(sourceId = "lead", pan = 0.0))

            val blocks = blocksFor(noteAt + 0.1)
            val ratio = rig.peaksPerBlock(blocks).last().first / reference.peaksPerBlock(blocks).last().first

            withClue("$case: the new lead note on the ramp") { ratio shouldBeLessThan 0.9 }
        }
    }

    "solo: a release ends once the background has reached its target, also when the target later rises again" {
        // "lead" at 0.95 from 0 to 2 s beside "b" at 0.5 from 0 to 5 s (control only): at 2 s the lead is released and
        // the rest ramps up to 0.5, which it reaches by about 3.5 s. When "b" ends at 5 s the rest ramps up again; a
        // new lead note at 5.3 s is background by then and starts on that ramp, not at full level.
        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 2.0)
        rig.schedule(0.0, soloControl(sourceId = "b", solo = 0.5), durSec = 5.0)
        rig.schedule(5.3, tone(sourceId = "lead", pan = 0.0))
        val reference = Rig()
        reference.schedule(5.3, tone(sourceId = "lead", pan = 0.0))

        val blocks = blocksFor(5.4)
        val ratio = rig.peaksPerBlock(blocks).last().first / reference.peaksPerBlock(blocks).last().first

        withClue("the new lead note on the ramp") { ratio shouldBeLessThan 0.9 }
    }

    "solo: solo(\"<1 0>\"): the notes of a 0 cycle play at full level from their first block, with no other solo" {
        // One lead note per 2 s cycle with the amount 1, 0, 1, 0, the bed hard right. In the 0 cycles the lead is not
        // soloed, and its new notes play at full level while the bed comes back; in the 1 cycles the bed ducks again.
        val rig = Rig()
        val reference = Rig()

        for (cycle in 0 until 4) {
            val amount = if (cycle % 2 == 0) 1.0 else 0.0

            rig.schedule(cycle * 2.0, tone(sourceId = "lead", pan = 0.0, solo = amount), durSec = 2.0)
            reference.schedule(cycle * 2.0, tone(sourceId = "lead", pan = 0.0), durSec = 2.0)
        }

        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
        reference.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val blocks = blocksFor(7.9)
        val soloed = rig.peaksPerBlock(blocks)
        val unsoloed = reference.peaksPerBlock(blocks)

        for (sec in listOf(1.0, 2.01, 2.5, 3.9, 5.0, 6.01, 6.5, 7.8)) {
            withClue("the lead at full level at $sec s") {
                soloed[blocksFor(sec)].first shouldBe (unsoloed[blocksFor(sec)].first plusOrMinus 1e-12)
            }
        }

        withClue("the bed coming back in the 0 cycle (2.5 s)") {
            (soloed[blocksFor(2.5)].second / unsoloed[blocksFor(2.5)].second) shouldBeLessThan 0.9
        }
        withClue("the bed ducking again in the next 1 cycle (5.8 s)") {
            (soloed[blocksFor(5.8)].second / unsoloed[blocksFor(5.8)].second) shouldBeLessThan 0.1
        }
    }

    "solo: a long soloed note as the source's FIRST event, at the start of the first control piece, does not set the end" {
        // The one order where the note creates the entry: the control piece at the same start must take the end over
        // from the note's 8 s gate. Both scheduling orders; the solo ends with the last piece (2.0 s).
        for (noteFirst in listOf(true, false)) {
            val rig = Rig()
            val note = tone(sourceId = "lead", pan = 0.0, solo = 0.95)

            if (noteFirst) {
                rig.schedule(0.0, note, durSec = 8.0)
                rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 0.25)
            } else {
                rig.schedule(0.0, soloControl(sourceId = "lead", solo = 0.95), durSec = 0.25)
                rig.schedule(0.0, note, durSec = 8.0)
            }

            for (piece in 1 until 8) {
                rig.schedule(piece * 0.25, soloControl(sourceId = "lead", solo = 0.95), durSec = 0.25)
            }

            rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
            val reference = Rig().apply { schedule(0.0, tone(sourceId = "bed", pan = 1.0)) }

            val soloed = rig.peaksPerBlock(blocksFor(3.7))
            val unsoloed = reference.peaksPerBlock(blocksFor(3.7))

            withClue("note first = $noteFirst: ducked at 1.95 s") {
                (soloed[blocksFor(1.95)].second / unsoloed[blocksFor(1.95)].second) shouldBe (0.05 plusOrMinus 1e-12)
            }
            withClue("note first = $noteFirst: back at full level at 3.6 s") {
                (soloed[blocksFor(3.6)].second / unsoloed[blocksFor(3.6)].second) shouldBe (1.0 plusOrMinus 1e-12)
            }
        }
    }

    "solo: decided (round 3): when the target rises again during a release, the old voice holds, the new note starts at full" {
        // "a" (a tone hard left) soloed at 0.95 from 0 to 2 s, "b" at 0.5 from 0 to 2.5 s (control only). At 2 s "a"
        // is released and the rest ramps toward 0.5; at 2.5 s "b" ends, before the ramp lands, and the target rises to
        // 1.0. a's old voice holds at 0.5 until the ramp passes it; a's new note (hard right) at 3.0 s starts at 1.0.
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "a", pan = 0.0))
        rig.schedule(0.0, soloControl(sourceId = "a", solo = 0.95), durSec = 2.0)
        rig.schedule(0.0, soloControl(sourceId = "b", solo = 0.5), durSec = 2.5)
        rig.schedule(3.0, tone(sourceId = "a", pan = 1.0))
        val reference = Rig()
        reference.schedule(0.0, tone(sourceId = "a", pan = 0.0))
        reference.schedule(3.0, tone(sourceId = "a", pan = 1.0))

        val blocks = blocksFor(3.1)
        val soloed = rig.peaksPerBlock(blocks)
        val alone = reference.peaksPerBlock(blocks)

        withClue("the old voice held at 0.5 (3.09 s)") { (soloed.last().first / alone.last().first) shouldBe (0.5 plusOrMinus 1e-12) }
        withClue("the new note at full level (3.09 s)") { (soloed.last().second / alone.last().second) shouldBe (1.0 plusOrMinus 1e-12) }
    }

    "solo: two solos at 1.0 and 0.5: when the stronger ends, its voices go straight to 0.5 while the rest ramps up to it" {
        // "a" (hard left, a 30 s tone) soloed at 1.0 for 2 s by control events, "b" soloed at 0.5 throughout by control
        // events only; the bed hard right. After 2 s the target is 1 - 0.5: the bed ramps up from 0, "a" goes to 0.5
        // over one block (it never dips to the bed's level on the way).
        val rig = Rig()
        rig.schedule(0.0, tone(sourceId = "a", pan = 0.0))
        rig.schedule(0.0, soloControl(sourceId = "a", solo = 1.0), durSec = 2.0)
        rig.schedule(0.0, soloControl(sourceId = "b", solo = 0.5))
        rig.schedule(0.0, tone(sourceId = "bed", pan = 1.0))
        val reference = Rig()
        reference.schedule(0.0, tone(sourceId = "a", pan = 0.0))
        reference.schedule(0.0, tone(sourceId = "bed", pan = 1.0))

        val blocks = blocksFor(4.5)
        val soloed = rig.peaksPerBlock(blocks)
        val unsoloed = reference.peaksPerBlock(blocks)

        fun leftRatio(sec: Double): Double = soloed[blocksFor(sec)].first / unsoloed[blocksFor(sec)].first
        fun rightRatio(sec: Double): Double = soloed[blocksFor(sec)].second / unsoloed[blocksFor(sec)].second

        withClue("a at full level while soloed (1.9 s)") { leftRatio(1.9) shouldBe (1.0 plusOrMinus 1e-12) }
        withClue("the bed muted while a solos (1.9 s)") { rightRatio(1.9) shouldBe 0.0 }

        for (sec in listOf(2.05, 2.5, 3.0, 4.4)) {
            withClue("a at 0.5 from just after its solo ends ($sec s)") { leftRatio(sec) shouldBe (0.5 plusOrMinus 1e-12) }
        }

        withClue("the bed still low at 2.05 s") { rightRatio(2.05) shouldBeLessThan 0.05 }
        withClue("the bed at 0.5 at 4.4 s") { rightRatio(4.4) shouldBe (0.5 plusOrMinus 1e-12) }
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

    fun Rig.release(liveId: Int) = engine.scheduler.stopRealtimeVoice(liveId = liveId)

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

    "solo: a held realtime solo ducks the bed wherever it stands in the active list, first included" {
        // The key goes down before the bed starts, so it is the first voice in the list.
        val rig = Rig()
        val reference = Rig()

        rig.press(liveId = 1)
        rig.schedule(0.01, tone(sourceId = "bed", pan = 1.0))
        reference.schedule(0.01, tone(sourceId = "bed", pan = 1.0))

        bedRatio(rig = rig, reference = reference, blocks = rampBlocks) shouldBe (0.05 plusOrMinus 1e-12)
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

    "solo: a released realtime solo's long tail beside another live solo is ducked at once after the note-off (Q28)" {
        // The realtime key's solo lasts while its gate is open; after the note-off its 5 s tail rings on, and the
        // kick's live solo (hard right) silences it once the grace has passed. Before Q28's answer the voice kept its
        // protection to its end. The key goes down after the kick's ramp has muted the rest.
        fun Rig.pressPad() {
            engine.scheduler.startRealtimeVoice(
                playbackId = "song",
                voice = RealtimeVoice(liveId = 1, data = pad(solo = 0.95), gateDurSec = null),
            )
        }

        val rig = kickRig()
        val reference = Rig()
        rig.renderPeaks(rampBlocks)
        reference.renderPeaks(rampBlocks)
        rig.pressPad()
        reference.pressPad()

        val held = rig.peaksPerBlock(blocksFor(0.5))
        val heldAlone = reference.peaksPerBlock(blocksFor(0.5))

        rig.release(liveId = 1)
        reference.release(liveId = 1)

        val tail = rig.peaksPerBlock(blocksFor(4.0))
        val tailAlone = reference.peaksPerBlock(blocksFor(4.0))

        withClue("the tail still sounds 3.5 s after the note-off (the row is not vacuous)") {
            tailAlone[blocksFor(3.5)].first shouldBeGreaterThan 1e-3
        }

        for (block in held.indices) {
            withClue("held block $block: the pad at full level") { held[block].first shouldBe (heldAlone[block].first plusOrMinus 1e-12) }
        }

        for (block in blocksFor(0.03) until tail.size) {
            withClue("tail block $block: ducked to 1 - 1.0") { tail[block].first shouldBeLessThan 1e-9 }
        }
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

    "solo: a voice entering or leaving a solo moves without a step (no click)" {
        // `solo(1.0)` on "x" (control events only) mutes everything else. The lead "y" is soloed from 2 s to 3 s:
        // at 2.0 s it enters protection (0 to 1.0), just after 3.0 s (its event's end plus the grace) it leaves it
        // (1.0 to 0).
        // Each change is ramped across one 128-frame block, so the largest sample step stays at the sine's own
        // slope plus at most one 128th of its amplitude. A step inside one sample would be the full amplitude.
        // 301.3 Hz, not 440: a 440 Hz sine started at 0 crosses zero exactly at 2.0 s, where a step would hide.
        val lead = tone(sourceId = "y", pan = 0.0).copy(freqHz = 301.3)
        val rig = Rig()
        rig.schedule(0.0, soloControl(sourceId = "x", solo = 1.0))
        rig.schedule(0.0, lead)
        rig.schedule(2.0, soloControl(sourceId = "y", solo = 0.95), durSec = 1.0)
        val reference = Rig()
        reference.schedule(0.0, lead)

        val blocks = blocksFor(3.2)
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
        withClue("heard between, at full level") { maxStep(samples = soloed, fromSec = 2.5, toSec = 2.6) shouldBe (maxStep(samples = unsoloed, fromSec = 2.5, toSec = 2.6) plusOrMinus 1e-12) }
        withClue("heard to just before 3.0 s") { maxStep(samples = soloed, fromSec = 2.95, toSec = 2.99) shouldBeGreaterThan slope * 0.5 }
        withClue("the solo's end just after 3.0 s") { maxStep(samples = soloed, fromSec = 2.99, toSec = 3.05) shouldBeLessThan slope * 1.2 }
        withClue("muted after") { maxStep(samples = soloed, fromSec = 3.05, toSec = 3.15) shouldBe 0.0 }
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
        val bed = tone(sourceId = "bed", pan = 1.0).copy(ignitorParams = klangMapOf(releaseSlot to 8.0))

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

        withClue("dropped like a late voice") { rig.engine.scheduler.droppedVoiceCount() shouldBe 1 }
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
