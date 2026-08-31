/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.filter.FilterModRenderer
import io.peekandpoke.klang.audio_be.voices.strip.pitch.FmRenderer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * **The realtime path: [KlangCommLink.Cmd.StartRealtimeVoice] promotes straight to active.**
 *
 * Realtime voices (MIDI keyboard & friends) carry no start time on the wire — the backend stamps
 * "now" at receipt and the voice must sound in the very next rendered block, without touching the
 * scheduled heap or the epoch machinery of the timeline path (see docs/tasks/midi-keyboard-playground.md).
 */
class RealtimeVoiceSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun newDispatcher() = PlaybackEngineDispatcher.create(
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        commLink = KlangCommLink(capacity = 1024).backend,
        performanceTimeMs = { 0.0 },
    ).also { it.setBackendStartTime(0.0) }

    // Sustain pinned to 1.0 so "still sounding" is a statement about the GATE, not about a
    // default envelope's decay tail.
    val sustained = VoiceData.empty.copy(
        sound = "sine",
        freqHz = 440.0,
        adsr = AdsrDef.Std(attack = 0.001, decay = 0.01, sustain = 1.0, release = 0.01),
    )

    fun hasAudio(out: ShortArray): Boolean = out.any { abs(it.toInt()) > 200 }

    /**
     * Renders [warmupBlocks] to move the cursor, sends the voice, renders on and reports which
     * of the following blocks sounded (true per block).
     */
    fun renderAround(
        voice: RealtimeVoice,
        warmupBlocks: Int = 4,
        blocksAfter: Int,
    ): List<Boolean> {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)
        var frame = 0.0

        repeat(warmupBlocks) {
            d.renderBlock(cursorFrame = frame, out = out)
            frame += blockFrames
        }

        d.handle(KlangCommLink.Cmd.StartRealtimeVoice(playbackId = "rt", voice = voice))

        return (0 until blocksAfter).map {
            d.renderBlock(cursorFrame = frame, out = out)
            frame += blockFrames
            hasAudio(out)
        }
    }

    // NB on "immediate": the house master limiter delays ALL output by its 5 ms lookahead
    // (220 frames = ~1.7 blocks at 44.1k/128 — see audio/MEMORY.md). The voice starts in the
    // very FIRST rendered block after the command (no heap, no epoch anchoring); through the
    // master its output emerges 220 frames later. That pipe delay is a CONSTANT, so onset rows
    // pin exact block indices where it matters.

    "a realtime voice starts in the first rendered block (heap bypass, pipe-delay-shifted)" {
        val heard = renderAround(
            voice = RealtimeVoice(liveId = 1, data = sustained, gateDurSec = 0.5),
            blocksAfter = 4,
        )
        // Block 0 carries only the master's delayed pre-voice silence; block 2 (frames 256..383,
        // past the 220-frame pipe) is fully audible. A detour through the scheduled heap / epoch
        // machinery cannot hit this index.
        heard[0].shouldBeFalse()
        heard[2].shouldBeTrue()
    }

    "a fixed gate ends the voice: audible while gated, silent after gate + release" {
        // 50 ms gate + 10 ms release ≈ 21 blocks at 44.1k/128; render 80 to see the silence.
        val heard = renderAround(
            voice = RealtimeVoice(liveId = 2, data = sustained, gateDurSec = 0.05),
            blocksAfter = 80,
        )
        heard.take(4).any { it }.shouldBeTrue()
        heard.last().shouldBeFalse()
    }

    "a held voice (gateDurSec = null) still sounds after seconds of rendering" {
        // 2 s ≈ 690 blocks — far past every envelope stage; only the open gate keeps it alive.
        val heard = renderAround(
            voice = RealtimeVoice(liveId = 3, data = sustained, gateDurSec = null),
            blocksAfter = 690,
        )
        heard.take(4).any { it }.shouldBeTrue()
        heard.last().shouldBeTrue()
    }

    "a control-only realtime event never sounds" {
        val heard = renderAround(
            voice = RealtimeVoice(liveId = 4, data = sustained.copy(control = true), gateDurSec = 0.5),
            blocksAfter = 10,
        )
        heard.any { it }.shouldBeFalse()
    }

    //  NOTE-OFF (StopRealtimeVoice) ////////////////////////////////////////////////////////////////////////////

    fun renderBlocks(
        d: PlaybackEngineDispatcher,
        fromFrame: Double,
        blocks: Int,
        onBlock: (Int) -> Unit = {},
    ): List<ShortArray> {
        var frame = fromFrame
        return (0 until blocks).map { i ->
            onBlock(i) // commands land BETWEEN blocks, like the real pump
            val out = ShortArray(blockFrames * 2)
            d.renderBlock(cursorFrame = frame, out = out)
            frame += blockFrames
            out
        }
    }

    fun maxAbs(out: ShortArray): Int = out.maxOf { abs(it.toInt()) }

    fun start(d: PlaybackEngineDispatcher, liveId: Int, data: VoiceData = sustained) =
        d.handle(KlangCommLink.Cmd.StartRealtimeVoice("rt", RealtimeVoice(liveId = liveId, data = data, gateDurSec = null)))

    fun stop(d: PlaybackEngineDispatcher, liveId: Int) =
        d.handle(KlangCommLink.Cmd.StopRealtimeVoice("rt", liveId = liveId))

    "note-off: a held voice enters its release tail and is silent after it" {
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1)
        renderBlocks(d, 4.0 * blockFrames, 10).drop(3).all { hasAudio(it) }.shouldBeTrue()

        stop(d, liveId = 1)
        // release 0.01 s ≈ 4 blocks at 44.1k/128 — a ramp first, then silence
        val after = renderBlocks(d, 14.0 * blockFrames, 40)
        // NB after.first() outputs purely PRE-stop audio (the 220-frame pipe) — it cannot
        // distinguish a release from a cut. The ramp-vs-cut guard is the dedicated
        // "DECAYING release tail" row below.
        hasAudio(after.first()).shouldBeTrue()
        after.takeLast(30).any { hasAudio(it) }.shouldBeFalse()
    }

    "note-off releases the targeted liveId and leaves other voices sounding" {
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1)
        start(d, liveId = 2, data = sustained.copy(freqHz = 660.0))
        renderBlocks(d, 4.0 * blockFrames, 10)

        stop(d, liveId = 1)
        val afterOne = renderBlocks(d, 14.0 * blockFrames, 40)
        hasAudio(afterOne.last()).shouldBeTrue() // voice 2 is still held

        stop(d, liveId = 2)
        val afterBoth = renderBlocks(d, 54.0 * blockFrames, 40)
        afterBoth.takeLast(30).any { hasAudio(it) }.shouldBeFalse()
    }

    "note-off with an unknown liveId is a no-op" {
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 7)
        renderBlocks(d, 4.0 * blockFrames, 5)

        stop(d, liveId = 999)
        renderBlocks(d, 9.0 * blockFrames, 20).all { hasAudio(it) }.shouldBeTrue()
    }

    "a double-stop is bit-identical to a single stop" {
        fun run(stopAtBlocks: Set<Int>): List<ShortArray> {
            val d = newDispatcher()
            renderBlocks(d, 0.0, 4)
            start(d, liveId = 1)
            return renderBlocks(d, 4.0 * blockFrames, 30) { i ->
                if (i in stopAtBlocks) {
                    stop(d, liveId = 1)
                }
            }
        }

        val once = run(setOf(10))
        val twice = run(setOf(10, 12))
        once.zip(twice).forEach { (a, b) -> a.contentEquals(b).shouldBeTrue() }
    }

    "I4: the release enters at the same note-relative sample regardless of block alignment" {
        // Voice-level on purpose (audit R3): at the dispatcher level BOTH the start and the stop
        // are cursor-stamped, so shifting the grid shifts everything together and the row cannot
        // discriminate. Here the voice starts OFF-grid (frame 37) and the gate moves to an
        // absolute frame (549) non-aligned in both grids — any absolute-frame leakage into the
        // envelope math (e.g. gateEndPos derived from blockStart) breaks bit-identity.
        fun run(gridOffset: Double): DoubleArray {
            val voice = VoiceTestHelpers.createVoice(
                startFrame = 37.0,
                endFrame = 100_000.0,
                gateEndFrame = 90_000.0,
                blockFrames = 100,
                envelope = Voice.Envelope(
                    attackFrames = 50.0,
                    decayFrames = 100.0,
                    sustainLevel = 0.8,
                    releaseFrames = 300.0,
                ),
            )
            val ctx = VoiceTestHelpers.createContext(blockFrames = 100)
            val out = DoubleArray(2100)
            var frame = gridOffset
            var released = false

            while (frame < 2000.0) {
                // Release before rendering the block that contains frame 549 — grid-independent.
                if (!released && frame + 100.0 > 549.0) {
                    voice.releaseGate(549.0)
                    released = true
                }
                ctx.blockStart = frame
                voice.render(ctx)

                var f = maxOf(frame, 37.0).toInt()
                val end = minOf(frame + 100.0, voice.endFrame).toInt()
                while (f < end) {
                    out[f] = ctx.voiceBuffer[f - frame.toInt()]
                    f++
                }
                frame += 100.0
            }
            return out
        }

        // Grid B starts BEFORE the voice (offset -50), so BOTH grids render every note frame
        // from 37 on — the VCA de-click smoother is causal state, and a grid that skipped the
        // first samples would legitimately diverge.
        val a = run(0.0)
        val b = run(-50.0)
        // Bit-identical across attack, decay, sustain, the moved gate at 549, the whole release,
        // and the silence after it.
        a.copyOfRange(37, 2000).contentEquals(b.copyOfRange(37, 2000)).shouldBeTrue()
    }

    "releaseGate moves endFrame to atFrame + the original release span" {
        val voice = VoiceTestHelpers.createVoice(startFrame = 0.0, endFrame = 1300.0, gateEndFrame = 1000.0)
        voice.releaseGate(200.0)
        voice.endFrame shouldBe 500.0

        // ...and the voice is genuinely dead past the moved end.
        val ctx = VoiceTestHelpers.createContext(blockStart = 512.0, blockFrames = 128)
        voice.render(ctx).shouldBeFalse()
    }

    "note-off renders a DECAYING release tail, not a hard cut" {
        // The feature's headline behavior (audit R2): a hard-cut mutation (endFrame = atFrame,
        // span dropped) must fail here — the tail must be audible past the pipe delay, below the
        // held level, and fall monotonically block-over-block.
        val longRelease = sustained.copy(
            adsr = AdsrDef.Std(attack = 0.001, decay = 0.01, sustain = 1.0, release = 0.2),
        )
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1, data = longRelease)
        val held = renderBlocks(d, 4.0 * blockFrames, 10)
        val heldPeak = held.takeLast(3).maxOf { maxAbs(it) }

        stop(d, liveId = 1)
        // release 0.2 s ≈ 69 blocks at 44.1k/128. The mid-tail threshold is the AUDIBLE bar
        // (200, like hasAudio) — a hard cut leaves at most 1-2 LSB of DC-blocker residue there,
        // which a bare `> 0` would mistake for a tail.
        val peaks = renderBlocks(d, 14.0 * blockFrames, 69).map { maxAbs(it) }
        (peaks[10] > 200).shouldBeTrue()
        (peaks[10] < heldPeak).shouldBeTrue()
        (peaks[20] < peaks[10]).shouldBeTrue()
        (peaks[30] < peaks[20]).shouldBeTrue()
        (peaks[40] < peaks[30]).shouldBeTrue()
    }

    "a note-on and note-off in the same command drain still produce an audible tap" {
        // Zero-length note (drum pad, arpeggiator gate, sub-10ms human tap): both commands stamp
        // the same frame. Without the one-block floor in releaseRealtimeVoice the gate lands ON
        // startFrame and the first rendered sample enters release from level 0 — pure silence.
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1)
        stop(d, liveId = 1) // same drain — no block rendered in between

        renderBlocks(d, 4.0 * blockFrames, 12).any { hasAudio(it) }.shouldBeTrue()
    }

    "the onset enters the attack at position 0 (one-block-ahead stamp, start side)" {
        // Guards the R1 start stamp. Linear 40 ms attack (1764 frames): with the correct stamp,
        // output block 1 carries only the first ~36 attack frames (220-frame pipe) — near
        // silence. A stamp one block late (dropping `+ blockFrames`) skips 128 frames of attack
        // AND seeds the de-click smoother mid-ramp: the same block comes out ~15x hotter
        // (measured 116 vs 1736 against steady 23178). The 3%-of-steady bar sits between with
        // wide margins both ways.
        val slowAttack = sustained.copy(
            adsr = AdsrDef.Std(
                attack = 0.04, decay = 0.01, sustain = 1.0, release = 0.01,
                attackCurve = AdsrCurve.Linear,
            ),
        )
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1, data = slowAttack)
        val blocks = renderBlocks(d, 4.0 * blockFrames, 30)
        val steady = blocks.subList(20, 25).maxOf { maxAbs(it) }

        (maxAbs(blocks[1]) < steady * 3 / 100).shouldBeTrue()
        (maxAbs(blocks[5]) > 200).shouldBeTrue() // ...and the ramp is really rising, not silence
    }

    "the release enters its curve at position 0 (one-block-ahead stamp, stop side)" {
        // Guards the R1 stop stamp. Linear 10 ms release (441 frames): post-stop output block 2
        // (2*128 - 220 = 36) carries release positions 36..163 — level 0.92..0.63 of held under
        // the correct stamp. A stamp one block late enters the curve 128 frames in: the same
        // block carries positions 164..291 — level 0.63..0.34. Threshold 0.8 sits between.
        val linRelease = sustained.copy(
            adsr = AdsrDef.Std(
                attack = 0.001, decay = 0.01, sustain = 1.0, release = 0.01,
                releaseCurve = AdsrCurve.Linear,
            ),
        )
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1, data = linRelease)
        val held = renderBlocks(d, 4.0 * blockFrames, 10)
        val heldPeak = held.takeLast(3).maxOf { maxAbs(it) }

        stop(d, liveId = 1)
        val post = renderBlocks(d, 14.0 * blockFrames, 8)
        (maxAbs(post[2]) > heldPeak * 8 / 10).shouldBeTrue()
    }

    "vca-off: the teardown fade follows a realtime note-off (no full-amplitude step)" {
        // Guards the blockCtx.endFrame mirror in releaseGate: without it, renderGate's fade
        // window stays at the held horizon while the voice still dies at the moved end — a
        // full-amplitude cut mid-waveform. Bare sine (NO ignitor envelope), vca off: only the
        // teardown fade shapes the death.
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.RegisterIgnitor("rt", "baresine", IgnitorDsl.Sine()))
        val bare = VoiceData.empty.copy(
            sound = "baresine",
            freqHz = 440.0,
            adsr = AdsrDef.Std(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.01, on = false),
        )
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1, data = bare)
        renderBlocks(d, 4.0 * blockFrames, 10)

        stop(d, liveId = 1)
        val tail = renderBlocks(d, 14.0 * blockFrames, 12)
        // Max adjacent-sample step across the death region (left channel): a 440 Hz sine moves
        // ~6% of amplitude per sample and the fade adds ~0.6%; a missing fade is a step of the
        // FULL amplitude in one sample.
        val samples = tail.flatMap { blk -> (0 until blockFrames).map { blk[it * 2].toInt() } }
        var maxStep = 0
        for (i in 1 until samples.size) {
            maxStep = maxOf(maxStep, abs(samples[i] - samples[i - 1]))
        }
        val amp = tail.take(2).maxOf { maxAbs(it) }
        (maxStep < amp / 4).shouldBeTrue()
    }

    "playbacks are isolated end-to-end: a note-off cannot cross engines" {
        // At dispatcher level each playback gets its OWN engine, so this pins the end-to-end
        // isolation property; the scheduler-level playbackId match is pinned separately below.
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        d.handle(KlangCommLink.Cmd.StartRealtimeVoice("rtA", RealtimeVoice(liveId = 1, data = sustained, gateDurSec = null)))
        d.handle(
            KlangCommLink.Cmd.StartRealtimeVoice(
                "rtB", RealtimeVoice(liveId = 1, data = sustained.copy(freqHz = 660.0), gateDurSec = null),
            )
        )
        renderBlocks(d, 4.0 * blockFrames, 10)

        d.handle(KlangCommLink.Cmd.StopRealtimeVoice("rtA", liveId = 1))
        renderBlocks(d, 14.0 * blockFrames, 40).takeLast(30).all { hasAudio(it) }.shouldBeTrue()
    }

    "stopRealtimeVoice releases only the addressed playback within ONE scheduler" {
        // The dispatcher isolates playbacks in separate engines today, so this pins the
        // scheduler-level defense-in-depth directly: one scheduler CAN host two playbacks (the
        // API takes a playbackId per call) and liveIds restart at 1 per FE playback.
        val clock = BackendClock(sampleRate)
        val context = AudioBackendContext.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            clock = clock,
            phasePoolSeed = 1,
        )
        val engine = PlaybackEngine.create(context)
        val mix = StereoBuffer(blockFrames)

        fun renderDirect(n: Int) {
            repeat(n) {
                mix.clear()
                engine.renderInto(mix, clock.cursorFrame)
                clock.cursorFrame += blockFrames
            }
        }

        renderDirect(4)
        engine.scheduler.startRealtimeVoice("rtA", RealtimeVoice(liveId = 1, data = sustained, gateDurSec = null))
        engine.scheduler.startRealtimeVoice(
            "rtB", RealtimeVoice(liveId = 1, data = sustained.copy(freqHz = 660.0), gateDurSec = null),
        )
        renderDirect(4)
        engine.scheduler.getActiveVoiceCount() shouldBe 2

        engine.scheduler.stopRealtimeVoice("rtA", liveId = 1)
        // release 0.01 s ~= 4 blocks; render far past it — only rtA's voice may die
        renderDirect(20)
        engine.scheduler.getActiveVoiceCount() shouldBe 1
    }

    "Cmd.Cleanup releases held realtime voices instead of ringing them to the horizon" {
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1)
        renderBlocks(d, 4.0 * blockFrames, 10)

        d.handle(KlangCommLink.Cmd.Cleanup("rt"))
        renderBlocks(d, 14.0 * blockFrames, 40).takeLast(30).any { hasAudio(it) }.shouldBeFalse()
    }

    "Cmd.Cleanup lets a FIXED-gate realtime voice play out (only held voices are released)" {
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        d.handle(
            KlangCommLink.Cmd.StartRealtimeVoice("rt", RealtimeVoice(liveId = 1, data = sustained, gateDurSec = 0.5))
        )
        renderBlocks(d, 4.0 * blockFrames, 4)

        d.handle(KlangCommLink.Cmd.Cleanup("rt"))
        // 0.5 s gate ≈ 172 blocks: still sounding LONG after a released tail (~4 blocks) would
        // have ended.
        renderBlocks(d, 8.0 * blockFrames, 40).takeLast(10).all { hasAudio(it) }.shouldBeTrue()
    }

    "Cmd.Cleanup lets an active TIMELINE voice ring out its full gate" {
        // Guards the WIDTH of cleanup's release predicate: widening it to all voices would chop
        // every song's final chord into its release tail the moment playback stops.
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                "rt",
                listOf(
                    ScheduledVoice(
                        playbackId = "rt",
                        data = sustained,
                        startTime = 0.0,
                        gateEndTime = 2.0,
                        playbackStartTime = 0.0,
                    )
                ),
            )
        )
        renderBlocks(d, 0.0, 8) // promoted + sounding

        d.handle(KlangCommLink.Cmd.Cleanup("rt"))
        renderBlocks(d, 8.0 * blockFrames, 40).takeLast(10).all { hasAudio(it) }.shouldBeTrue()
    }

    "a stop for an unknown playback never creates (or revives) an engine" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.StopRealtimeVoice("never-existed", liveId = 1))
        d.activePlaybackIds.isEmpty().shouldBeTrue()
    }

    "note-off releases EVERY voice carrying the liveId, not just the first" {
        // Audit R7: layered instruments may start several voices per key under one liveId.
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1)
        start(d, liveId = 1, data = sustained.copy(freqHz = 660.0))
        renderBlocks(d, 4.0 * blockFrames, 10)

        stop(d, liveId = 1)
        renderBlocks(d, 14.0 * blockFrames, 40).takeLast(30).any { hasAudio(it) }.shouldBeFalse()
    }

    "note-off on a release-0 voice stops it, not ignores it" {
        // Guards the STRICT one-shot guard in Voice.releaseGate: endFrame == gateEndFrame is the
        // release-0 case and must still stop (a `<=` guard would hold the voice to the horizon).
        val relZero = sustained.copy(
            adsr = AdsrDef.Std(attack = 0.001, decay = 0.01, sustain = 1.0, release = 0.0),
        )
        val d = newDispatcher()
        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1, data = relZero)
        renderBlocks(d, 4.0 * blockFrames, 10).drop(3).all { hasAudio(it) }.shouldBeTrue()

        stop(d, liveId = 1)
        // The first ~2 post-stop blocks still flush the master's delayed pre-stop audio.
        renderBlocks(d, 14.0 * blockFrames, 10).drop(3).any { hasAudio(it) }.shouldBeFalse()
    }

    //  MOVED-GATE STRIP UNITS //////////////////////////////////////////////////////////////////////////////////

    // Minimal BlockContext for the two rows below: the strip's gate must be read from the ctx
    // PER RENDER CALL — a re-baked constructor copy is the regression class BlockContext's
    // KDoc warns about, and these rows are what kill it.
    fun stripCtx(gateEndFrame: Double): BlockContext = BlockContext(
        audioBuffer = AudioBuffer(blockFrames),
        freqModBuffer = DoubleArray(blockFrames),
        scratchBuffers = ScratchBuffers(blockFrames),
        sampleRate = sampleRate,
        startFrame = 0.0,
        endFrame = 1_000_000.0,
        gateEndFrame = gateEndFrame,
        freqHz = 440.0,
        signal = Ignitors.silence(),
        signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = 1_000_000,
            gateEndFrame = 1_000_000,
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
        ),
        cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
    ).apply {
        updateOffsetAndLength(0, blockFrames)
        blockStart = 0.0
    }

    "FilterModRenderer follows a gate moved between blocks (ctx read, not a baked copy)" {
        val filter = VoiceTestHelpers.TunableSpyFilter()
        val mod = Voice.FilterModulator(
            filter = filter,
            envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0),
            depth = 12.0,
            baseCutoff = 800.0,
            drift = null,
        )
        val renderer = FilterModRenderer(modulators = listOf(mod), startFrame = 0.0)
        val ctx = stripCtx(gateEndFrame = 100_000.0)

        renderer.render(ctx)
        filter.currentCutoff shouldBe 1600.0 // sustain: 800 * 2^(12/12 * 1.0)

        // The realtime note-off moves the gate BETWEEN blocks — the renderer must see it.
        ctx.gateEndFrame = 0.0
        ctx.blockStart = blockFrames.toDouble()
        renderer.render(ctx)
        filter.currentCutoff shouldBe 800.0 // released (release 0): envelope 0 -> base cutoff
    }

    "FmRenderer follows a gate moved between blocks (ctx read, not a baked copy)" {
        val fm = Voice.Fm(
            ratio = 1.0,
            depth = 50.0,
            envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0),
        )
        val renderer = FmRenderer(fm = fm, freqHz = 440.0, sampleRate = sampleRate, startFrame = 0.0)
        val ctx = stripCtx(gateEndFrame = 100_000.0)

        renderer.render(ctx)
        ctx.freqModBuffer.any { it != 1.0 }.shouldBeTrue() // sustain: fm depth modulates pitch

        ctx.gateEndFrame = 0.0
        ctx.blockStart = blockFrames.toDouble()
        ctx.freqModBufferWritten = false
        renderer.render(ctx)
        ctx.freqModBuffer.all { it == 1.0 }.shouldBeTrue() // released: depth collapses to 0
    }

    "vca-off: note-off ramps the ignitor envelope down, not sustain-then-cliff (amendment A1)" {
        // MUTATION CHECK (manual): revert the signalCtx update in Voice.releaseGate — the mid-tail
        // assertion goes red (the ignitor keeps sustaining until the teardown fade at the end).
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterIgnitor(
                "rt", "heldorgan",
                IgnitorDsl.Adsr(
                    inner = IgnitorDsl.Sine(),
                    attackSec = IgnitorDsl.Constant(0.001),
                    decaySec = IgnitorDsl.Constant(0.01),
                    sustainLevel = IgnitorDsl.Constant(1.0),
                    releaseSec = IgnitorDsl.Constant(0.06),
                ),
            )
        )
        val organ = VoiceData.empty.copy(
            sound = "heldorgan",
            freqHz = 440.0,
            adsr = AdsrDef.Std(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.06, on = false),
        )

        renderBlocks(d, 0.0, 4)
        start(d, liveId = 1, data = organ)
        val held = renderBlocks(d, 4.0 * blockFrames, 10)
        val heldPeak = held.takeLast(3).maxOf { maxAbs(it) }

        stop(d, liveId = 1)
        // release 0.06 s ≈ 21 blocks: the middle of the tail must already be well below the held
        // level (a ramp), and the end silent.
        val tail = renderBlocks(d, 14.0 * blockFrames, 30)
        val midTail = tail.subList(8, 14).maxOf { maxAbs(it) }
        (midTail < heldPeak / 2).shouldBeTrue()
        tail.takeLast(6).any { hasAudio(it) }.shouldBeFalse()
    }
})
