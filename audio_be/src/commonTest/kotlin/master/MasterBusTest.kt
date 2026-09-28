/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The master-in-pattern path end to end on the backend: a `master(…)` reference rides the voice
 * stream, the scheduler consumes it, and the engine applies the chain to its bus.
 *
 * See `docs/tasks/master-dsl.md`.
 */
class MasterBusTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
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
            KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "m1", dsl = KatalystDsl(emptyList()))
        )
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(masterEvent("m1"))))

        val peak = renderPeak(d, blocks = 4)

        // Nothing was scheduled but the control event → the engine must be silent, and no voice
        // may have been created from it (a null `sound` would otherwise render the default osc).
        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0
        peak shouldBe 0.0
    }

    "master gain scales the engine's bus" {
        val loud = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(2.0)))

        val plain = newDispatcher()
        plain.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(sineVoice())))
        val plainPeak = renderPeak(plain, blocks = 40)

        val boosted = newDispatcher()
        boosted.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "loud", dsl = loud))
        boosted.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("loud"), sineVoice()),
            )
        )
        val boostedPeak = renderPeak(boosted, blocks = 40)

        plainPeak shouldBeGreaterThan 0.0
        // The nominal ratio, not a loosened one. This assertion used to read `* 1.5` with the
        // note "the crossfade means the first ~60 ms ramps in" — a workaround for master round
        // M1, which is now fixed: the first master is adopted at full weight.
        boostedPeak shouldBeGreaterThan plainPeak * 1.95
    }

    "a Katalyst.param slot at the output is its default: nothing fills it there (decision b1)" {
        // Phase 3 step 12 decision (b): the output has no param channel. A slot in a master chain
        // is the number its author wrote as the default, even when the carrier event carries a
        // value under that very name (which is what an orbit's owner voice would hand an orbit
        // chain). Before step 12 C5 no Param could reach the output; now `Katalyst.param(...)` can.
        fun render(dsl: KatalystDsl?): ShortArray {
            val d = newDispatcher()
            val voices = mutableListOf(quietSineVoice())

            if (dsl != null) {
                d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "m", dsl = dsl))
                val carrier = masterEvent("m")
                voices += carrier.copy(data = carrier.data.copy(katalystParams = mapOf("level" to 0.25)))
            }

            d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = voices))

            val all = ShortArray(blockFrames * 2 * 40)
            val out = ShortArray(blockFrames * 2)
            for (b in 0 until 40) {
                d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
                out.copyInto(all, destinationOffset = b * blockFrames * 2)
            }

            return all
        }

        fun peak(samples: ShortArray): Int = samples.maxOf { abs(it.toInt()) }

        val slotted = render(KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Param("level", 2.5))))
        val constant = render(KatalystDsl.of(KatalystStageDsl.Gain(gain = c(2.5))))
        val plain = render(null)

        // The slot renders exactly as the default written as a constant, sample for sample...
        slotted.contentEquals(constant) shouldBe true
        // ...and that is really the 2.5 and not two unity chains agreeing (0.25 would be quieter).
        peak(slotted) shouldBeGreaterThan (peak(plain) * 2.4).toInt()
    }

    "the FIRST master is adopted at full gain, not faded up from unity" {
        // Master round M1. The crossfade exists to stop a click when swapping between two
        // AUDIBLE chains; applied to the first master it instead ramped the song's opening
        // 60 ms up from UNMASTERED, so the first downbeat of every mastered song was quieter
        // than the same note later — up to 8.3 dB down for DerSchmetterling's gain(2.6), and
        // audible on four other shipped songs.
        //
        // The probe window is 8 blocks: past the master limiter's 5 ms lookahead (240 frames,
        // ~2 blocks — block 0 is literally silent, which is why a one-block probe cannot be
        // used here) and still deep inside the 60 ms fade the old code would have been
        // running. Under that old behaviour this window peaked around 1.4x, not 2x.
        val loud = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(2.0)))

        val plain = newDispatcher()
        plain.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(sineVoice())))
        val plainEarly = renderPeak(plain, blocks = 8)

        val boosted = newDispatcher()
        boosted.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "loud", dsl = loud))
        boosted.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("loud"), sineVoice()),
            )
        )
        val boostedEarly = renderPeak(boosted, blocks = 8)

        plainEarly shouldBeGreaterThan 0.0
        boostedEarly shouldBeGreaterThan plainEarly * 1.9
    }

    "a master applies only to its own playback" {
        val quiet = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.0)))

        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "a", name = "mute", dsl = quiet))
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
        muted.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "a", name = "mute", dsl = quiet))
        muted.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "a",
                voices = listOf(masterEvent("mute", pid = "a"), sineVoice(pid = "a")),
            )
        )
        val out = ShortArray(blockFrames * 2)
        // Skip past the crossfade window (60 ms ≈ 21 blocks at 128/44100), then measure.
        for (b in 0 until 40) {
            muted.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }
        var tailPeak = 0.0
        for (b in 40 until 60) {
            muted.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
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
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "boost",
                dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(4.0))),
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
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song", name = "a", dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.25))),
            )
        )
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song", name = "b", dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(4.0))),
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
        // RegisterKatalyst). It must NOT pin the playback to unity forever.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(quietSineVoice(), masterEvent("late", startTime = 0.2)),
            )
        )
        val beforeRegistration = blockPeaks(d, blocks = 200).last()

        // Registration arrives, and the carrier re-emits (as the top-level master() does every cycle).
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song", name = "late", dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(4.0))),
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
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
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song", name = "boost", dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(4.0))),
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
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song", name = "boost", dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(4.0))),
            )
        )
        // The sine first — this fixes the playback epoch at t=0.
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(quietSineVoice())))

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 344) {
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
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
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "hall",
                dsl = KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.6), size = c(9.0), lowpass = c(17640.0))),
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }

        // The voice is long gone, but the engine must not be considered idle while the master
        // still rings — disposing it here would chop the tail mid-decay.
        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0
        d.engine("song")?.isIdle() shouldBe false
    }

    "the master tail eventually clears so the engine can be disposed" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "smallroom",
                dsl = KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.5), size = c(1.0), lowpass = c(2205.0))),
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }

        // The counterpart to the tail-holds-the-engine test: once the small, heavily damped room
        // has decayed, isIdle must go true again — otherwise a stopped playback leaks an engine
        // that keeps rendering forever.
        d.engine("song")?.isIdle() shouldBe true
    }

    "a master delay keeps ringing between echoes — the gap is not mistaken for silence" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "echo",
                dsl = KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.6), time = c(0.5), feedback = c(0.6))),
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }

        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0
        d.engine("song")?.isIdle() shouldBe false
    }

    "swapping back to a master does not replay its old tail" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "hall",
                dsl = KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.9), size = c(9.5), lowpass = c(19845.0))),
            )
        )
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song", name = "dry", dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(1.0001))),
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }

        var afterReturn = 0.0
        for (b in 1030 until 1400) {   // across and past the swap back to "hall"
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
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

    "a chain whose stages are all inaudible never holds the engine open" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "noop",
                dsl = KatalystDsl.of(
                    KatalystStageDsl.Gain(gain = c(1.0)),                              // unity
                    KatalystStageDsl.Reverb(wet = c(0.0), size = c(9.0)),             // no send
                    KatalystStageDsl.Delay(wet = c(0.5), time = c(0.0)),           // no time
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }

        // The stages stay in the chain (a Katalyst keeps what it declares; the old master dropped
        // them at build, step 12 C5 retired that), each Off or transparent: nothing rings, so
        // nothing keeps the engine alive.
        d.engine("song")?.isIdle() shouldBe true
    }

    "the chain cache stays bounded through a live-coding burst, and the last request is what plays" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song", name = "keep", dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(4.0))),
            )
        )

        // A live-coding burst: every edit mints a new content-derived name, so without a bound the
        // engine would retain each one's Freeverb buffers and delay ring forever. A chain is built
        // on its first REQUEST (one registry serves both positions, step 12 C5), so each edit is
        // registered and then requested, one per 0.1 s (longer than the 60 ms fade, so every
        // request builds while its predecessor is in play). Last, the playback returns to "keep".
        val events = mutableListOf(quietSineVoice(), masterEvent("keep"))
        for (i in 0 until 30) {
            d.handle(
                KlangCommLink.Cmd.RegisterKatalyst(
                    playbackId = "song",
                    name = "edit-$i",
                    dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(1.0 + i * 0.01))),
                )
            )
            events += masterEvent("edit-$i", startTime = 0.3 + i * 0.1)
        }
        events += masterEvent("keep", startTime = 3.4)
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = events))

        val out = ShortArray(blockFrames * 2)
        for (b in 0 until 1300) { // ~3.8 s: every edit and the return to "keep" have landed
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }

        val engine = d.engine("song").shouldNotBeNull()
        engine.masterBusForTest.cachedChainCount shouldBeLessThan 9

        // ...and the master in play after the burst is the one asked for last: level is boosted.
        var peak = 0.0
        for (b in 1300 until 1400) {
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
            for (i in out.indices step 2) {
                val v = abs(out[i].toDouble() / Short.MAX_VALUE)
                if (v > peak) {
                    peak = v
                }
            }
        }
        peak shouldBeGreaterThan 0.25
    }

    "the cache never evicts the master in play, even when it is the OLDEST entry" {
        // The chain in play is the first one built ("hall", with a room), so it is the first
        // eviction candidate by insertion order: only "not the current chain" keeps it. It must be
        // current while the cache overflows, without being the outgoing or the queued one. So: a
        // fade to "x" and back queues "hall", and during the fade back (current = hall, previous =
        // x) a burst of edits is built, each queued over the last; a final request for "hall"
        // drops the queue.
        val units = ReverbUnits(sampleRate)
        val registry = KatalystRegistry().apply {
            register("hall", KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.6), size = c(9.0))))
            register("x", KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.9))))
            for (i in 0 until 8) {
                register("e$i", KatalystDsl.of(KatalystStageDsl.Gain(gain = c(1.1 + i * 0.01))))
            }
        }
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, reverbs = units)
        val buf = StereoBuffer(blockFrames)
        var n = 0

        fun block(loud: Boolean): Double {
            var energy = 0.0
            for (i in 0 until blockFrames) {
                val x = if (loud) 0.4 * sin(2.0 * PI * 330.0 * n / sampleRate) else 0.0
                buf.left[i] = x
                buf.right[i] = x
                n++
            }
            bus.process(buf, blockFrames)
            bus.markRendered()
            for (i in 0 until blockFrames) {
                energy += abs(buf.left[i])
            }
            return energy
        }

        bus.requestSwap("hall")
        repeat(40) { block(loud = true) }        // charge the room

        bus.requestSwap("x")                      // fade hall -> x ...
        bus.requestSwap("hall")                   // ... and back, queued behind it
        repeat(30) { block(loud = true) }        // past the 60 ms fade: now fading x -> hall

        for (i in 0 until 8) {
            bus.requestSwap("e$i")                // built, then queued over the previous one
        }
        bus.requestSwap("hall")                   // the playing chain again: the queue is dropped

        // The overflow evicted edits, never the room in play: its unit was not handed back...
        bus.cachedChainCount shouldBeLessThan 9
        units.idleCount shouldBe 0

        // ...and the room is still ringing on silence (a retired, re-rented unit would be empty).
        var tail = 0.0
        repeat(20) { tail += block(loud = false) }
        tail shouldBeGreaterThan 1.0
    }

    "a self-sustaining master delay cannot hold a drained engine open forever" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "runaway",
                // feedback 1.0 recirculates without loss — the ring never empties, so an unbounded
                // tail hold would keep a stopped playback rendering and leak an engine per stop.
                dsl = KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.6), time = c(0.25), feedback = c(1.0))),
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }
        // Still echoing at full level — correctly held open.
        d.engine("song")?.isIdle() shouldBe false

        for (b in 500 until 8000) {
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }
        // Past the hold bound the engine is released rather than rendering forever.
        d.engine("song")?.isIdle() shouldBe true
    }

    "a long song still keeps its master tail — the hold is measured from silence, not uptime" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "hall",
                dsl = KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.6), size = c(8.5), lowpass = c(17640.0))),
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
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }

        // The reverb is decaying right now. A bound counted from engine start (rather than from the
        // moment the engine fell quiet) would have expired ~20 s ago and chopped this tail.
        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0
        d.engine("song")?.isIdle() shouldBe false
    }

    "switching to an inaudible master releases the engine instead of pinning it" {
        // Two ways to say "master off": `master(Katalyst())`, the empty chain, which puts the bus
        // back on the engine's fast path (the bus stops being processed at all), and a chain whose
        // one stage is dry, which keeps the bus running with nothing in it that rings.
        listOf(
            "the empty chain" to KatalystDsl(emptyList()),
            "a dry room" to KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.0))),
        ).forEach { (label, off) ->
            withClue(label) {
                val d = newDispatcher()
                d.handle(
                    KlangCommLink.Cmd.RegisterKatalyst(
                        playbackId = "song",
                        name = "hall",
                        dsl = KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.6), size = c(9.0), lowpass = c(17640.0))),
                    )
                )
                d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "off", dsl = off))
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
                    d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
                }

                // Once the master is inaudible there is nothing left that can ring. A cached "still
                // ringing" answer would freeze here once the bus stops being processed, and leak
                // the engine.
                d.engine("song")?.isIdle() shouldBe true
            }
        }
    }
})
