/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
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

    "an unknown master name is parked: a later registration lands it, no re-emission needed" {
        val d = newDispatcher()
        // The event references a master the backend has never heard of (a dropped or late
        // RegisterKatalyst). It must NOT pin the playback to unity forever. Since step 12 C4 the
        // name is parked (the orbit's rule) and the engine polls it every block, so the ONE event
        // is enough: a one-shot `note(...).master(...)` never re-emits.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(quietSineVoice(), masterEvent("late", startTime = 0.2)),
            )
        )
        val beforeRegistration = blockPeaks(d, blocks = 200).last()

        // Registration arrives; the carrier does NOT re-emit.
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song", name = "late", dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(4.0))),
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

    "switching to an inaudible master holds the engine for the old room's ring-out, then releases it" {
        // Two ways to say "master off": `master(Katalyst())`, the empty chain, which puts the bus
        // back on the engine's fast path once nothing is leaving it, and a chain whose one stage is
        // dry, which keeps the bus running with nothing in it that rings. Since step 12 C4 the room
        // swapped away drains instead of being cut, so the engine is held for that ring-out (the
        // empty chain must keep the bus processing until it ends) and released when it is over,
        // long before the 20 s hold bound would release it anyway.
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
                for (b in 0 until 1000) {
                    d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
                }

                // ~2.9 s: the room left service at ~1.06 s and is still ringing out, audibly.
                val engine = d.engine("song").shouldNotBeNull()
                engine.masterBusForTest.chainSwap.isDraining shouldBe true
                engine.isIdle() shouldBe false

                // Released once the ring-out is over, well inside the hold bound (20 s from the
                // moment the engine went quiet, about 0.5 s in): a cached "still ringing" answer
                // that froze when the bus stopped being processed would hold it until then.
                var b = 1000
                while (!engine.isIdle() && b < 5000) {
                    d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
                    b++
                }

                engine.isIdle() shouldBe true
                b shouldBeLessThan 5000
                engine.masterBusForTest.chainSwap.settled shouldBe true
            }
        }
    }

    // ── The swap law (phase 3 step 12 C4, decision (f)) ─────────────────────────────────────
    //
    // The master runs the orbit's `ChainSwap`: the leaving chain's INPUT ramps down while the
    // arriving chain's output ramps up, then the leaving chain drains at full weight on silence.
    // These rows drive a bus directly, in the engine's order per block: the request (the
    // scheduler's), `pollPendingSwap`, `process`, `markRendered`.

    /** A master bus fed a chosen input one block at a time, the engine's calls around it. */
    class Rig(
        val registry: KatalystRegistry = KatalystRegistry(),
        val units: ReverbUnits = ReverbUnits(sampleRate),
    ) {
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, reverbs = units)
        val buf = StereoBuffer(blockFrames)
        var n = 0

        /** The probe the swap rows feed: a 330 Hz sine at [amp], continuous across blocks. */
        fun x(amp: Double, i: Int): Double = amp * sin(2.0 * PI * 330.0 * (n + i) / sampleRate)

        /** One block of the probe at [amp] (0.0 is silence); returns the left channel. */
        fun block(amp: Double): DoubleArray {
            for (i in 0 until blockFrames) {
                val v = x(amp, i)
                buf.left[i] = v
                buf.right[i] = v
            }
            n += blockFrames
            bus.pollPendingSwap()
            bus.process(buf, blockFrames)
            bus.markRendered()

            return buf.left.copyOf()
        }

        /** One block of constant [level] on both channels; returns the left channel. */
        fun dc(level: Double): DoubleArray {
            for (i in 0 until blockFrames) {
                buf.left[i] = level
                buf.right[i] = level
            }
            n += blockFrames
            bus.pollPendingSwap()
            bus.process(buf, blockFrames)
            bus.markRendered()

            return buf.left.copyOf()
        }
    }

    fun room(wet: Double, size: Double) = KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(wet), size = c(size)))

    fun gain(g: Double) = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(g)))

    fun maxAbs(xs: DoubleArray): Double = xs.maxOf { abs(it) }

    "a master swapped mid-tail rings out: the leaving room drains at full weight instead of being cut" {
        // Two buses with the same room, charged the same way. One swaps to a dry chain the moment
        // the input stops; the other keeps the room. On silence the swap feeds the leaving room
        // nothing and the arriving chain produces nothing, so under the drain law the swapped bus
        // IS the room's own ring-out, sample for sample, through the fade and after it. Before C4
        // the room's output was ramped down over the fade and cut at its end.
        fun rig() = Rig().apply {
            registry.register("hall", room(wet = 0.6, size = 4.0))
            registry.register("dry", gain(0.5))
            bus.requestSwap("hall")
            repeat(40) { block(0.4) }
        }

        val swapped = rig()
        val control = rig()

        swapped.bus.requestSwap("dry")

        var worst = 0.0
        var drainBlocks = 0
        var tailAfterFade = 0.0

        for (b in 0 until 6000) {
            val got = swapped.block(0.0)
            val want = control.block(0.0)

            for (i in 0 until blockFrames) {
                worst = maxOf(worst, abs(got[i] - want[i]))
            }

            if (swapped.bus.chainSwap.isDraining) {
                drainBlocks++
            }

            // Past the old cut: the 60 ms fade is 21 blocks at 44.1 kHz.
            if (b in 30 until 60) {
                tailAfterFade += got.sumOf { abs(it) }
            }

            if (swapped.bus.chainSwap.settled) {
                break
            }
        }

        worst shouldBe 0.0
        // The room really rang after the fade's end (a cut would leave exactly nothing there)...
        tailAfterFade shouldBeGreaterThan 1.0
        drainBlocks shouldBeGreaterThan 100
        // ...and it ended: the room left service and its unit went back to the shelf.
        swapped.bus.chainSwap.settled shouldBe true
        swapped.units.idleCount shouldBe 1
        control.units.idleCount shouldBe 0
    }

    "the fade: the leaving master's INPUT ramps down and the arriving master's OUTPUT ramps up, per sample" {
        // The oracle is the law written here, over the two chains built by hand and run standalone:
        // the leaving room is fed x(1 - r), the arriving gain is fed x, and the bus is
        // gainOut * r + roomOut, with r = m / T over the T = 60 ms frames after the swap block's
        // start, 1 from there on. A room is the leaving chain because it is where the two laws part:
        // fed the ramped input its ring-out after the fade is the echo of a quieter signal, fed the
        // full input and faded at its output (the law before C4) it is not.
        val roomDsl = room(wet = 0.5, size = 3.0)
        val liftDsl = gain(2.0)

        val rig = Rig().apply {
            registry.register("room", roomDsl)
            registry.register("lift", liftDsl)
        }

        fun standalone(dsl: KatalystDsl) = KatalystChainBuilder.build(
            dsl = dsl,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        ).also { it.applyParams(null) }

        val roomAlone = standalone(roomDsl)
        val liftAlone = standalone(liftDsl)
        val roomCtx = KatalystContext(blockFrames, StereoBuffer(blockFrames))
        val liftCtx = KatalystContext(blockFrames, StereoBuffer(blockFrames))
        val fadeFrames = (0.06 * sampleRate).toInt()

        rig.bus.requestSwap("room")

        val swapBlock = 30
        var m = 0
        var worst = 0.0
        var roomTailAfterFade = 0.0

        for (b in 0 until 200) {
            if (b == swapBlock) {
                rig.bus.requestSwap("lift")
            }

            val amp = if (b < 80) 0.4 else 0.0

            for (i in 0 until blockFrames) {
                val x = rig.x(amp, i)
                val t = if (b < swapBlock) 0.0 else if (m + i >= fadeFrames) 1.0 else (m + i) / fadeFrames.toDouble()

                roomCtx.mixBuffer.left[i] = x * (1.0 - t)
                roomCtx.mixBuffer.right[i] = x * (1.0 - t)
                liftCtx.mixBuffer.left[i] = x
                liftCtx.mixBuffer.right[i] = x
            }

            val got = rig.block(amp)
            roomAlone.process(roomCtx)
            liftAlone.process(liftCtx)

            for (i in 0 until blockFrames) {
                val t = if (b < swapBlock) 0.0 else if (m + i >= fadeFrames) 1.0 else (m + i) / fadeFrames.toDouble()
                val want = if (b < swapBlock) roomCtx.mixBuffer.left[i] else liftCtx.mixBuffer.left[i] * t + roomCtx.mixBuffer.left[i]

                worst = maxOf(worst, abs(got[i] - want))
            }

            if (b >= swapBlock) {
                m += blockFrames
            }

            if (b >= 80) {
                roomTailAfterFade += roomCtx.mixBuffer.left.sumOf { abs(it) }
            }
        }

        worst shouldBe 0.0
        // The room's echo of the ramped input is really in the sum (not an all-dry comparison).
        roomTailAfterFade shouldBeGreaterThan 1.0
    }

    "the parked request at the master: latest wins, the chain in service drops it, an unknown name lands when registered" {
        val rig = Rig().apply {
            registry.register("a", gain(0.5))
            registry.register("b", gain(2.0))
            registry.register("c", gain(8.0))
            registry.register("d", gain(4.0))
            registry.register("e", gain(6.0))
            registry.register("low", gain(1.0))
        }
        val level = 0.1

        rig.bus.requestSwap("a")
        repeat(4) { rig.dc(level) }

        // Latest wins: b starts, c and d arrive while it fades, and only d may land. c at 8x would
        // push the output past d's 0.4 on the way.
        rig.bus.requestSwap("b")
        rig.bus.requestSwap("c")
        rig.bus.requestSwap("d")
        var highest = 0.0
        repeat(120) { highest = maxOf(highest, maxAbs(rig.dc(level))) }

        highest shouldBeLessThan 0.4 + 1e-12
        rig.dc(level)[0] shouldBe (0.4 plusOrMinus 1e-12)

        // A request for the chain in service drops the parked one: e starts, low is parked behind
        // it, then e is asked for again. low at 1x would pull the output under d's 0.4.
        rig.bus.requestSwap("e")
        rig.bus.requestSwap("low")
        rig.bus.requestSwap("e")
        var lowest = 1.0
        repeat(120) { lowest = minOf(lowest, rig.dc(level).min()) }

        lowest shouldBeGreaterThan 0.4 - 1e-12
        rig.dc(level)[0] shouldBe (0.6 plusOrMinus 1e-12)

        // An unknown name is parked, not dropped: its registration alone lands it, on the next
        // block's poll, with no second request.
        rig.bus.requestSwap("late")
        repeat(10) { rig.dc(level) }
        rig.dc(level)[0] shouldBe (0.6 plusOrMinus 1e-12)

        rig.registry.register("late", gain(3.0))
        repeat(40) { rig.dc(level) }
        rig.dc(level)[0] shouldBe (0.3 plusOrMinus 1e-12)
    }

    "swapping back to a master that is still ringing out waits for the ring-out, then lands it empty" {
        // Decision (g), kept: a request while the leaving chain drains waits for the whole
        // ring-out. Here the request is for the very room that is draining, so it cannot be
        // re-used mid-tail: it comes back retired and reset, holding nothing of its last life.
        val rig = Rig().apply {
            registry.register("hall", room(wet = 0.8, size = 3.0))
            registry.register("dry", gain(0.9))
        }
        val swap = rig.bus.chainSwap

        rig.bus.requestSwap("hall")
        repeat(40) { rig.block(0.4) }
        rig.bus.requestSwap("dry")
        repeat(40) { rig.block(0.0) }

        swap.isDraining shouldBe true
        rig.bus.requestSwap("hall")

        // The drain is not cut and nothing starts until it is over.
        var drained = 0
        while (swap.isDraining && drained < 6000) {
            rig.block(0.0).also { drained++ }
            swap.isFading shouldBe false
        }

        drained shouldBeGreaterThan 100
        swap.isDraining shouldBe false

        // The parked hall lands on the next block's poll, as a fade from dry.
        rig.block(0.0)
        swap.isFading shouldBe true

        // It came back empty: on silence nothing comes out, through its fade and after. Not
        // exactly 0: the reverb's anti-denormal offset (a deliberate engine exception) leaves a
        // residue near 1e-18; a replayed room is many orders above the floor.
        var replay = 0.0
        repeat(200) { replay = maxOf(replay, maxAbs(rig.block(0.0))) }
        replay shouldBeLessThan 1e-12

        // And it came back working: a new note rings in the room after it stops.
        repeat(20) { rig.block(0.4) }
        var ring = 0.0
        repeat(20) { ring += rig.block(0.0).sumOf { abs(it) } }
        ring shouldBeGreaterThan 1.0
    }

    "the cache never evicts the master in service, even when it is the OLDEST entry" {
        // "hall" is built first, so it is the first eviction candidate by insertion order: only
        // "not the chain in service" keeps it. The cache is filled with 7 edits that each land and
        // leave, then hall comes back into service (from the cache), is charged, and an eighth edit
        // overflows the cache while hall is still the chain in service.
        val rig = Rig().apply {
            registry.register("hall", room(wet = 0.6, size = 3.0))
            for (i in 0 until 8) {
                registry.register("e$i", gain(1.1 + i * 0.01))
            }
        }

        fun settle() {
            var guard = 0
            while (!rig.bus.chainSwap.settled && guard < 6000) {
                rig.block(0.0)
                guard++
            }
            rig.block(0.0)
        }

        rig.bus.requestSwap("hall")
        rig.block(0.0)

        for (i in 0 until 7) {
            rig.bus.requestSwap("e$i")
            settle()
        }

        rig.bus.requestSwap("hall")
        settle()
        repeat(40) { rig.block(0.4) }
        rig.bus.cachedChainCount shouldBe 8

        rig.bus.requestSwap("e7")

        // The overflow evicted an edit, never the room in service: its unit was not handed back...
        rig.bus.cachedChainCount shouldBe 8
        rig.units.idleCount shouldBe 0

        // ...and the room, leaving service now, rings out (a retired, re-rented unit would be empty).
        var tail = 0.0
        repeat(20) { tail += rig.block(0.0).sumOf { abs(it) } }
        tail shouldBeGreaterThan 1.0
    }
})
