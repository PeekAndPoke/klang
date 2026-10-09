/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.MemoizingIgnitor
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.classic
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * **`classic()`'s accelerate stage, filled by sprudel's `accelerate` slot (pitch pipeline step 3), against an ORACLE.**
 *
 * The voice strip's accelerate retired in step 3; sprudel's `accelerate(semitones)` travels as the flat `accelerate`
 * slot and fills the Ignitor `accelerate` node that `classic()` places between the pitch envelope and the vibrato.
 * Every row renders a real voice through `VoiceFactory` and reads the frequency ratio it applies, frame by frame
 * ([renderPitchRatios], the ramp-sample probe), against the law written out here with the library's `pow`: the
 * ratio is `2^(semitones / 12 * p / gate)` from the onset to the gate close, and `2^(semitones / 12)` from the gate
 * frame on, through the release (decision D2, with its hold). The strip glided over the scheduled END instead (the
 * release tail included), so these rows are also the proof that the sprudel door changed its base: on the strip a
 * release tail as long as these would leave the ratio at the gate well short of the target.
 *
 * The node's own law, bit for bit, is `AccelerateSemitoneLawSpec`. The guard row of the other pitch stages is here
 * too: an unwritten `accelerate` builds no stage.
 */
class ClassicAccelerateSpec : StringSpec({

    val sampleRate = 48000

    fun oracle(p: Int, semitones: Double, gate: Int): Double =
        if (p < gate) 2.0.pow(semitones / 12.0 * p / gate) else 2.0.pow(semitones / 12.0)

    /** The voice lives a second past its gate: the release tail the strip's base included, longer than the gate. */
    val longLife = DoorAdsr(release = 1.0)

    class Row(val title: String, val semitones: Double, val gate: Int)

    val rows = listOf(
        Row(title = "a fifth up over a quarter second", semitones = 7.0, gate = 12_000),
        Row(title = "an octave down over a gate inside a block", semitones = -12.0, gate = 9_001),
        Row(title = "Kokon's strike, a twentieth of a semitone", semitones = 0.05, gate = 12_000),
    )

    for (row in rows) {
        for (onset in listOf(0, 37)) {
            "the stage glides to the gate and holds through a long release tail: ${row.title} (onset $onset)" {
                val frames = row.gate + 40_000
                val ratios = renderPitchRatios(
                    doors = DoorFields(accelerate = row.semitones, adsr = longLife),
                    frames = frames,
                    gateFrames = row.gate,
                    onsetFrames = onset,
                )

                val wrong = (0 until frames).firstOrNull { p -> abs(ratios[p] - oracle(p = p, semitones = row.semitones, gate = row.gate)) > 1e-9 }

                withClue("first frame off the law") { wrong shouldBe null }
                withClue("engaged: the pitch moved") { (abs(ratios[frames - 1] - 1.0) > 1e-4) shouldBe true }
            }
        }
    }

    for (onset in listOf(0, 37)) {
        "a held realtime voice keeps its far scheduled gate as the base: a note-off never brings the hold (onset $onset)" {
            // The decided semantics of 2026-08-29: `voiceDurationFrames` is the SCHEDULED gate, which a note-off does not
            // move. A held voice is scheduled with a far gate, so its glide is all but inert, and after the note-off at
            // frame 1000 it keeps that base: it never jumps to the target.
            val farGate = 1_000_000
            val frames = 3000
            val ratios = renderPitchRatios(
                doors = DoorFields(accelerate = 24.0, adsr = longLife),
                frames = frames,
                gateFrames = farGate,
                onsetFrames = onset,
                releaseAtFrame = 1000,
            )

            val wrong = (0 until frames).firstOrNull { p -> abs(ratios[p] - oracle(p = p, semitones = 24.0, gate = farGate)) > 1e-9 }

            withClue("first frame off the far-gate law") { wrong shouldBe null }
            withClue("engaged: the glide runs, slowly") { (ratios[frames - 1] > 1.004) shouldBe true }
        }
    }

    "0 semitones is the bare voice, and so is a non-finite accelerate in the bag (it reads as the slot's 0)" {
        // Written raw into the bag, past sprudel's boundary (which drops a non-finite value).
        val frames = 4000
        val key = (IgnitorDsl.Slots.accelerate as IgnitorDsl.Param).name
        val bare = renderPitchRatios(doors = DoorFields(), frames = frames, gateFrames = 2000)

        withClue("the bare voice is on the note") { bare.all { it == 1.0 } shouldBe true }
        withClue("0 semitones") {
            renderPitchRatios(doors = DoorFields(accelerate = 0.0), frames = frames, gateFrames = 2000).toList() shouldBe bare.toList()
        }

        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("$value") {
                renderPitchRatios(
                    doors = DoorFields(),
                    data = VoiceData.empty.copy(ignitorParams = mapOf(key to value)),
                    frames = frames,
                    gateFrames = 2000,
                ).toList() shouldBe bare.toList()
            }
        }
    }

    // ── The guard (the pitch stages' shape: an unwritten switch builds nothing) ──────────────────────────────────

    /** The class the root of [dsl]'s build hands the source, built with [bag]: a pitched source under a built pitch stage is a `ModApplyingIgnitor`. */
    fun shapeOf(dsl: IgnitorDsl, bag: Map<String, Double>): Any {
        val built: Ignitor = dsl.buildExciter(ignitorParams = bag, random = Random(7), freqHz = 220.0, sampleRate = sampleRate).ignitor

        return (built as MemoizingIgnitor).inner::class
    }

    "an unwritten accelerate slot builds no stage: the tree is the bare source's" {
        // The envelope is switched off so the root IS the source (an off envelope is not built).
        val classic = IgnitorDsl.Sine().classic()
        val off = mapOf("adsr.on" to 0.0)
        val bareShape = shapeOf(IgnitorDsl.Sine(), emptyMap())

        withClue("nothing written") { shapeOf(classic, off) shouldBe bareShape }
        withClue("positive control: a written accelerate builds the stage") {
            shapeOf(classic, off + ("accelerate" to 2.0)) shouldNotBe bareShape
        }
    }
})
