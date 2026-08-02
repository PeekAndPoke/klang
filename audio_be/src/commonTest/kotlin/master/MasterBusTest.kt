/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * The master-in-pattern path end to end on the backend: a `master(…)` reference rides the voice
 * stream, the scheduler consumes it, and the engine applies the chain to its bus.
 *
 * See `docs/tasks/master-dsl.md`.
 */
class MasterBusTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    /** Blocks to skip before measuring the envelope — covers the voice's own attack/decay. */
    val ATTACK_BLOCKS = 60

    fun newDispatcher(): PlaybackEngineDispatcher =
        PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

    /** A sounding voice: a long sine so every rendered block has signal in it. */
    fun sineVoice(pid: String = "song") = ScheduledVoice(
        playbackId = pid,
        startTime = 0.0,
        gateEndTime = 10.0,
        data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5),
        playbackStartTime = 0.0,
    )

    /** A quiet sine — stays well under the safety limiter so master gain changes are visible. */
    fun quietSineVoice(pid: String = "song") = ScheduledVoice(
        playbackId = pid,
        startTime = 0.0,
        gateEndTime = 30.0,
        data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.1),
        playbackStartTime = 0.0,
    )

    /** The control-only carrier the top-level `master(…)` emits: master reference, no sound. */
    fun masterEvent(name: String, pid: String = "song", startTime: Double = 0.0) = ScheduledVoice(
        playbackId = pid,
        startTime = startTime,
        gateEndTime = startTime + 1.0,
        data = VoiceData.empty.copy(master = name, control = true),
        playbackStartTime = 0.0,
    )

    /** Peak absolute sample over [blocks] rendered blocks, as a 0..1 float scale. */
    fun renderPeak(d: PlaybackEngineDispatcher, blocks: Int): Double {
        val out = ShortArray(blockFrames * 2)
        var peak = 0.0

        for (b in 0 until blocks) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
            for (s in out) {
                val v = abs(s.toDouble() / Short.MAX_VALUE)
                if (v > peak) {
                    peak = v
                }
            }
        }

        return peak
    }

    "a control-only master event is consumed — it never becomes a voice" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(playbackId = "song", name = "m1", dsl = MasterDsl.default)
        )
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(masterEvent("m1"))))

        val peak = renderPeak(d, blocks = 4)

        // Nothing was scheduled but the control event → the engine must be silent, and no voice
        // may have been created from it (a null `sound` would otherwise render the default osc).
        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0
        peak shouldBe 0.0
    }

    "master gain scales the engine's bus" {
        val loud = MasterDsl.of(MasterStageDsl.Gain(gain = 2.0))

        val plain = newDispatcher()
        plain.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(sineVoice())))
        val plainPeak = renderPeak(plain, blocks = 40)

        val boosted = newDispatcher()
        boosted.handle(KlangCommLink.Cmd.RegisterMaster(playbackId = "song", name = "loud", dsl = loud))
        boosted.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("loud"), sineVoice()),
            )
        )
        val boostedPeak = renderPeak(boosted, blocks = 40)

        plainPeak shouldBeGreaterThan 0.0
        // Louder, and audibly so — the crossfade means the first ~60 ms ramps in, hence a
        // tolerance well below the nominal 2.0 rather than an exact ratio.
        boostedPeak shouldBeGreaterThan plainPeak * 1.5
    }

    "a master applies only to its own playback" {
        val quiet = MasterDsl.of(MasterStageDsl.Gain(gain = 0.0))

        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.RegisterMaster(playbackId = "a", name = "mute", dsl = quiet))
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "a",
                voices = listOf(masterEvent("mute", pid = "a"), sineVoice(pid = "a")),
            )
        )
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "b", voices = listOf(sineVoice(pid = "b"))))

        // Playback "b" is untouched by "a"'s master, so the summed mix still has signal.
        renderPeak(d, blocks = 40) shouldBeGreaterThan 0.0

        // And with only the muted playback, the mix is (after the crossfade) silent.
        val muted = newDispatcher()
        muted.handle(KlangCommLink.Cmd.RegisterMaster(playbackId = "a", name = "mute", dsl = quiet))
        muted.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "a",
                voices = listOf(masterEvent("mute", pid = "a"), sineVoice(pid = "a")),
            )
        )
        val out = ShortArray(blockFrames * 2)
        // Skip past the crossfade window (60 ms ≈ 21 blocks at 128/44100), then measure.
        for (b in 0 until 40) {
            muted.renderBlock(cursorFrame = b * blockFrames, out = out)
        }
        var tailPeak = 0.0
        for (b in 40 until 60) {
            muted.renderBlock(cursorFrame = b * blockFrames, out = out)
            for (s in out) {
                val v = abs(s.toDouble() / Short.MAX_VALUE)
                if (v > tailPeak) {
                    tailPeak = v
                }
            }
        }
        tailPeak shouldBe 0.0
    }

    // ── Crossfade behaviour ─────────────────────────────────────────────────────────────────
    //
    // The click guard measures the block-level ENVELOPE, not raw sample deltas: a per-sample
    // threshold is phase-dependent and, on a limited+clipped output, an un-faded switch can stay
    // under it by luck. Envelope ratios are phase-independent and directly express "ramped, not
    // stepped". Levels are kept well under the safety limiter so it never masks the transition.

    /** Per-block peak of the left channel, over [blocks] blocks starting at block 0. */
    fun blockPeaks(d: PlaybackEngineDispatcher, blocks: Int): List<Double> {
        val out = ShortArray(blockFrames * 2)

        return (0 until blocks).map { b ->
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
            var peak = 0.0
            for (i in out.indices step 2) {
                val v = abs(out[i].toDouble() / Short.MAX_VALUE)
                if (v > peak) {
                    peak = v
                }
            }
            peak
        }
    }

    /**
     * Largest ratio between the peaks of two consecutive (audible) blocks, from block [from] on.
     *
     * [from] skips the note's own ADSR attack/decay, which is a legitimate fast level change and
     * would otherwise dominate the measurement.
     */
    fun maxEnvelopeRatio(peaks: List<Double>, from: Int = 0): Double {
        var worst = 1.0
        for (i in (from + 1) until peaks.size) {
            val a = peaks[i - 1]
            val b = peaks[i]
            if (a > 0.01 && b > 0.01) {
                val ratio = if (b > a) b / a else a / b
                if (ratio > worst) {
                    worst = ratio
                }
            }
        }
        return worst
    }

    /**
     * How many blocks the envelope spends *between* two plateau levels.
     *
     * This is the ramp-vs-step measure: a crossfade of `fadeFrames` spans many blocks in the band,
     * a hard switch spans at most one. Preferred over a block-to-block ratio whenever the two
     * levels differ a lot — a LINEAR fade between very different gains makes a large *relative*
     * step at the quiet end while still being a perfectly smooth ramp.
     */
    fun transitionBlocks(peaks: List<Double>, low: Double, high: Double, from: Int): Int =
        (from until peaks.size).count { peaks[it] > low * 1.25 && peaks[it] < high * 0.8 }

    "swapping masters ramps the level instead of stepping it" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "boost",
                dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 4.0)),
            )
        )
        // Quiet source × 4 stays under the -1 dB safety ceiling, so the limiter cannot hide a step.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(quietSineVoice(), masterEvent("boost", startTime = 0.5)),
            )
        )

        val peaks = blockPeaks(d, blocks = 400)
        val before = peaks[100]
        val after = peaks[399]

        // The swap really happened...
        (after / before) shouldBeGreaterThan 3.0
        // ...but no single block jumped there. A hard switch would show a ~4x block-to-block ratio;
        // a 60 ms fade spreads it over ~20 blocks (≈7% per block).
        maxEnvelopeRatio(peaks, from = ATTACK_BLOCKS) shouldBeLessThan 1.5
        // And the level genuinely travelled through the middle rather than teleporting.
        transitionBlocks(peaks, low = before, high = after, from = ATTACK_BLOCKS) shouldBeGreaterThan 8
    }

    "a swap arriving mid-fade is queued — it never cuts the running fade" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "a", dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 0.25)),
            )
        )
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "b", dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 4.0)),
            )
        )
        // Two swaps 20 ms apart — well inside the 60 ms crossfade.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(
                    quietSineVoice(),
                    masterEvent("a", startTime = 0.5),
                    masterEvent("b", startTime = 0.52),
                ),
            )
        )

        val peaks = blockPeaks(d, blocks = 500)

        // Both fades ran to completion: the level ends up at b (4x), not stuck at a (0.25x)...
        (peaks[499] / peaks[100]) shouldBeGreaterThan 3.0
        // ...and neither transition stepped: the envelope spends many blocks climbing from the
        // quiet "a" plateau up to "b". (A block-to-block ratio is not the right measure here — a
        // LINEAR fade across a 16x gain change necessarily makes a big relative step at the quiet
        // end while still being a smooth ramp; transition WIDTH is what separates ramp from step.)
        transitionBlocks(peaks, low = peaks[190], high = peaks[499], from = ATTACK_BLOCKS) shouldBeGreaterThan 8
    }

    // ── Robustness ──────────────────────────────────────────────────────────────────────────

    "an unknown master name is not latched — a later registration still applies" {
        val d = newDispatcher()
        // The event references a master the backend has never heard of (a dropped or late
        // RegisterMaster). It must NOT pin the playback to unity forever.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(quietSineVoice(), masterEvent("late", startTime = 0.2)),
            )
        )
        val beforeRegistration = blockPeaks(d, blocks = 200).last()

        // Registration arrives, and the carrier re-emits (as the top-level master() does every cycle).
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "late", dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 4.0)),
            )
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("late", startTime = 1.0)),
            )
        )

        val out = ShortArray(blockFrames * 2)
        var after = 0.0
        for (b in 200 until 600) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
            for (i in out.indices step 2) {
                val v = abs(out[i].toDouble() / Short.MAX_VALUE)
                if (v > after) {
                    after = v
                }
            }
        }

        (after / beforeRegistration) shouldBeGreaterThan 3.0
    }

    "a master rides a sounding note — the note still plays and the master applies" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "boost", dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 4.0)),
            )
        )
        // No control flag: this is `note("c3").master(...)` — it must sound AND swap.
        val soundingWithMaster = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 10.0,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.1, master = "boost"),
            playbackStartTime = 0.0,
        )
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(soundingWithMaster)))

        val peaks = blockPeaks(d, blocks = 200)

        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 1
        peaks.last() shouldBeGreaterThan 0.2   // 0.1 source × 4 master; unmastered would be ~0.1
    }

    "a late master event still applies — state is not dropped like a stale note" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "boost", dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 4.0)),
            )
        )
        // The sine first — this fixes the playback epoch at t=0.
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(quietSineVoice())))

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 344) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }
        var beforePeak = 0.0
        for (i in out.indices step 2) {
            val v = abs(out[i].toDouble() / Short.MAX_VALUE)
            if (v > beforePeak) {
                beforePeak = v
            }
        }

        // NOW deliver a master event stamped t=0 — about a second in the past, far beyond the
        // scheduler's 5-block staleness window (a worklet stall / late batch). A late *note* is
        // rightly dropped; late *state* must still take effect.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("boost", startTime = 0.0)),
            )
        )

        var afterPeak = 0.0
        for (b in 344 until 700) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
            for (i in out.indices step 2) {
                val v = abs(out[i].toDouble() / Short.MAX_VALUE)
                if (v > afterPeak) {
                    afterPeak = v
                }
            }
        }

        (afterPeak / beforePeak) shouldBeGreaterThan 3.0
    }

    "a master reverb tail keeps the engine alive after the notes stop" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "hall",
                dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.6, roomSize = 0.9, damp = 0.2)),
            )
        )
        // A short note, then nothing — the reverb tail is all that is left.
        val shortNote = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 0.2,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5),
            playbackStartTime = 0.0,
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("hall"), shortNote),
            )
        )

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 200) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }

        // The voice is long gone, but the engine must not be considered idle while the master
        // still rings — disposing it here would chop the tail mid-decay.
        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0
        d.engine("song")?.isIdle() shouldBe false
    }

    "the master tail eventually clears so the engine can be disposed" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "smallroom",
                dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.5, roomSize = 0.1, damp = 0.9)),
            )
        )
        val shortNote = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 0.05,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.3),
            playbackStartTime = 0.0,
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("smallroom"), shortNote),
            )
        )

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 3000) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }

        // The counterpart to the tail-holds-the-engine test: once the small, heavily damped room
        // has decayed, isIdle must go true again — otherwise a stopped playback leaks an engine
        // that keeps rendering forever.
        d.engine("song")?.isIdle() shouldBe true
    }

    "a master delay keeps ringing between echoes — the gap is not mistaken for silence" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "echo",
                dsl = MasterDsl.of(MasterStageDsl.Delay(wet = 0.6, timeSeconds = 0.5, feedback = 0.6)),
            )
        )
        val blip = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 0.05,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5),
            playbackStartTime = 0.0,
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("echo"), blip),
            )
        )

        val out = ShortArray(blockFrames * 2)
        // ~0.35 s in: the note is long over and the first echo has not arrived yet, so the master
        // OUTPUT is silent — but the delay ring is full. Watching the output would call this
        // finished and cut every remaining echo.
        for (b in 0 until 120) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }

        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0
        d.engine("song")?.isIdle() shouldBe false
    }

    "swapping back to a master does not replay its old tail" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "hall",
                dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.9, roomSize = 0.95, damp = 0.1)),
            )
        )
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "dry", dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 1.0001)),
            )
        )
        val note = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 0.2,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.6),
            playbackStartTime = 0.0,
        )
        // Loud note into the hall, then away to dry, then back to hall — with no further notes.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(
                    masterEvent("hall", startTime = 0.0),
                    note,
                    masterEvent("dry", startTime = 0.4),
                    masterEvent("hall", startTime = 3.0),
                ),
            )
        )

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 1030) {   // up to ~2.99 s — the bus is silent well before this
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }

        var afterReturn = 0.0
        for (b in 1030 until 1400) {   // across and past the swap back to "hall"
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
            for (i in out.indices step 2) {
                val v = abs(out[i].toDouble() / Short.MAX_VALUE)
                if (v > afterReturn) {
                    afterReturn = v
                }
            }
        }

        // Nothing is playing, so returning to "hall" must be silent. A cached chain that kept its
        // frozen comb buffers would dump seconds-old reverb here.
        afterReturn shouldBeLessThan 0.01
    }

    "a chain whose stages are all inaudible costs nothing and never holds the engine open" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "noop",
                dsl = MasterDsl.of(
                    MasterStageDsl.Gain(gain = 1.0),                              // unity
                    MasterStageDsl.Reverb(wet = 0.0, roomSize = 0.9),             // no send
                    MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.0),           // no time
                ),
            )
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("noop")),
            )
        )

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 40) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }

        // Every stage was dropped at build time: nothing to ring, nothing to keep alive.
        d.engine("song")?.isIdle() shouldBe true
    }

    "the chain cache stays bounded and never evicts the master in play" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "keep", dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 4.0)),
            )
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(quietSineVoice(), masterEvent("keep")),
            )
        )
        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 100) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }

        // A live-coding burst: every edit mints a new content-derived name, so without a bound the
        // engine would retain each one's Freeverb buffers and delay ring forever.
        for (i in 0 until 30) {
            d.handle(
                KlangCommLink.Cmd.RegisterMaster(
                    playbackId = "song",
                    name = "edit-$i",
                    dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 1.0 + i)),
                )
            )
        }

        val engine = d.engine("song").shouldNotBeNull()
        engine.masterBusForTest.cachedChainCount shouldBeLessThan 9

        // ...and the master that was playing survived the burst: level is still boosted.
        var peak = 0.0
        for (b in 100 until 200) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
            for (i in out.indices step 2) {
                val v = abs(out[i].toDouble() / Short.MAX_VALUE)
                if (v > peak) {
                    peak = v
                }
            }
        }
        peak shouldBeGreaterThan 0.25
    }

    "a self-sustaining master delay cannot hold a drained engine open forever" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "runaway",
                // feedback 1.0 recirculates without loss — the ring never empties, so an unbounded
                // tail hold would keep a stopped playback rendering and leak an engine per stop.
                dsl = MasterDsl.of(MasterStageDsl.Delay(wet = 0.6, timeSeconds = 0.25, feedback = 1.0)),
            )
        )
        val blip = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 0.05,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5),
            playbackStartTime = 0.0,
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("runaway"), blip),
            )
        )

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 500) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }
        // Still echoing at full level — correctly held open.
        d.engine("song")?.isIdle() shouldBe false

        for (b in 500 until 8000) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }
        // Past the hold bound the engine is released rather than rendering forever.
        d.engine("song")?.isIdle() shouldBe true
    }

    "a long song still keeps its master tail — the hold is measured from silence, not uptime" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "hall",
                dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.6, roomSize = 0.85, damp = 0.2)),
            )
        )
        // A note near the END of a long piece: the engine has been rendering for ~25 s before it.
        val lateNote = ScheduledVoice(
            playbackId = "song",
            startTime = 25.0,
            gateEndTime = 25.2,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5),
            playbackStartTime = 0.0,
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("hall"), lateNote),
            )
        )

        val out = ShortArray(blockFrames * 2)
        // Render past the note (25.2 s ≈ block 8680) and a little beyond.
        for (b in 0 until 8800) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }

        // The reverb is decaying right now. A bound counted from engine start (rather than from the
        // moment the engine fell quiet) would have expired ~20 s ago and chopped this tail.
        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0
        d.engine("song")?.isIdle() shouldBe false
    }

    "switching to an inaudible master releases the engine instead of pinning it" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song",
                name = "hall",
                dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.6, roomSize = 0.9, damp = 0.2)),
            )
        )
        // "master off": every stage is inaudible, so the built chain is empty.
        d.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "off", dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.0)),
            )
        )
        val note = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 0.2,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5),
            playbackStartTime = 0.0,
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("hall", startTime = 0.0), note, masterEvent("off", startTime = 1.0)),
            )
        )

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 2000) {
            d.renderBlock(cursorFrame = b * blockFrames, out = out)
        }

        // Once the master is inaudible there is nothing left that can ring. A cached "still ringing"
        // answer would freeze here (the bus stops being processed at all) and leak the engine.
        d.engine("song")?.isIdle() shouldBe true
    }
})
