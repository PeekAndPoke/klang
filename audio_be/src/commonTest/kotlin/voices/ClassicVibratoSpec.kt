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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * **`classic()`'s vibrato stage, filled by sprudel's `vib` slots (pitch pipeline step 2), against an ORACLE.**
 *
 * The voice strip's vibrato retired in step 2; sprudel's `vibrato(rate, semitones)` (alias `vib`) travels as the
 * `vibrato.rate` and `vibrato.semitones` slots and fills the Ignitor `vibrato` node that `classic()` places outside
 * the pitch envelope. Every row renders a real voice through `VoiceFactory` and reads the frequency ratio it applies,
 * frame by frame ([renderPitchRatios], the ramp-sample probe), against the law written out here with the library's
 * `sin` and `pow`: the LFO starts at phase 0 on the voice's first rendered frame and steps `2 pi rate / sampleRate`
 * per frame, the ratio is `2^(sin(phase) * semitones / 12)`.
 *
 * An oracle, not a parity spec: the strip-against-node spec (`VibratoConsistencyTest`) retired with the strip host.
 * The guard row from step 0's audio review is here too: an unwritten `vibrato.semitones` builds no vibrato.
 */
class ClassicVibratoSpec : StringSpec({

    val sampleRate = 48000

    fun oracle(p: Int, rate: Double, semitones: Double): Double = 2.0.pow(sin(2.0 * PI * rate * p / sampleRate) * semitones / 12.0)

    class Row(val title: String, val rate: Double?, val semitones: Double)

    val rows = listOf(
        Row(title = "5 Hz, half a semitone", rate = 5.0, semitones = 0.5),
        Row(title = "7.3 Hz, two semitones", rate = 7.3, semitones = 2.0),
        Row(title = "the rate unwritten: the slot's default, 5 Hz", rate = null, semitones = 0.3),
    )

    for (row in rows) {
        for (onset in listOf(0, 37)) {
            "the stage follows the law, frame by frame: ${row.title} (onset $onset)" {
                val frames = 20_000
                val ratios = renderPitchRatios(
                    doors = DoorFields(vibratoRate = row.rate, vibratoSemitones = row.semitones),
                    frames = frames,
                    gateFrames = frames,
                    onsetFrames = onset,
                )
                val rate = row.rate ?: 5.0

                val wrong = (0 until frames).firstOrNull { p -> abs(ratios[p] - oracle(p = p, rate = rate, semitones = row.semitones)) > 1e-9 }

                withClue("first frame off the law") { wrong shouldBe null }
                withClue("engaged: the pitch moved") { ratios.any { abs(it - 1.0) > 1e-3 } shouldBe true }
            }
        }
    }

    "a rate alone switches nothing on, and neither does a depth of 0 or below: the bare voice" {
        val frames = 4000
        val bare = renderPitchRatios(doors = DoorFields(), frames = frames, gateFrames = frames)

        withClue("the bare voice is on the note") { bare.all { it == 1.0 } shouldBe true }

        for ((what, doors) in listOf(
            "a rate alone (`vib(4)`)" to DoorFields(vibratoRate = 4.0),
            "a depth of 0" to DoorFields(vibratoRate = 4.0, vibratoSemitones = 0.0),
            "a negative depth" to DoorFields(vibratoRate = 4.0, vibratoSemitones = -0.5),
        )) {
            withClue(what) { renderPitchRatios(doors = doors, frames = frames, gateFrames = frames).toList() shouldBe bare.toList() }
        }
    }

    "a non-finite vibrato.semitones in the bag reads as unset: the slot's default 0, no vibrato" {
        // Written raw into the bag, past sprudel's boundary (which drops a non-finite value). The slot's leaf reads it
        // as unset, so the depth is the slot default 0.0, not the node's own non-finite rule (`VIBRATO_SEMITONES`).
        val frames = 4000
        val key = (IgnitorDsl.Slots.vibrato.semitones as IgnitorDsl.Param).name
        val bare = renderPitchRatios(doors = DoorFields(), frames = frames, gateFrames = frames)

        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("$value") {
                renderPitchRatios(
                    doors = DoorFields(vibratoRate = 5.0),
                    data = VoiceData.empty.copy(ignitorParams = mapOf(key to value)),
                    frames = frames,
                    gateFrames = frames,
                ).toList() shouldBe bare.toList()
            }
        }
    }

    // ── The guard (pitch pipeline step 0, audio review round 1) ──────────────────────────────────────────────────

    /** The class the root of [dsl]'s build hands the source, built with [bag]: a pitched source under a built pitch stage is a `ModApplyingIgnitor`. */
    fun shapeOf(dsl: IgnitorDsl, bag: Map<String, Double>): Any {
        val built: Ignitor = dsl.buildExciter(ignitorParams = bag, random = Random(7), freqHz = 220.0, sampleRate = sampleRate).ignitor

        return (built as MemoizingIgnitor).inner::class
    }

    "an unwritten vibrato.semitones slot builds no vibrato: the tree is the bare source's" {
        // The vibrato's gate keeps a NON-FINITE depth built (it renders the node's default), so the slot must default
        // to the finite literal 0.0, never to `SLOT_UNSET`, or every voice of every song gets a vibrato. The
        // envelope is switched off so the root IS the source (an off envelope is not built).
        val classic = IgnitorDsl.Sine().classic()
        val off = mapOf("adsr.on" to 0.0)
        val bareShape = shapeOf(IgnitorDsl.Sine(), emptyMap())

        withClue("nothing written") { shapeOf(classic, off) shouldBe bareShape }
        withClue("only the rate written") { shapeOf(classic, off + ("vibrato.rate" to 6.0)) shouldBe bareShape }
        withClue("positive control: a written depth builds the stage") {
            shapeOf(classic, off + ("vibrato.semitones" to 0.3)) shouldNotBe bareShape
        }
    }
})
