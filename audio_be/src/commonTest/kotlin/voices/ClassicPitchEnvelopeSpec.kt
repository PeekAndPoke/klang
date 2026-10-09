/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * **`classic()`'s pitch envelope stage, filled by sprudel's `penv` slots (pitch pipeline step 1), against an ORACLE.**
 *
 * The voice strip's pitch envelope retired in step 1; sprudel's `penv(semitones, attack, decay, sustain, release)`
 * and `penvCurves(...)` travel as the `penv.*` and `penvCurves.*` slots and fill the Ignitor `pitchEnvelope` node that
 * `classic()` places. Every row renders a real voice through `VoiceFactory` and reads the frequency ratio it applies,
 * frame by frame ([renderPitchRatios], the ramp-sample probe), and compares it with the law written out here:
 *
 *  - the level: attack `p / A` (fractional `A` frames), decay `s + (1 - s)(1 - (p - A) / D)`, then the sustain `s`
 *    until the gate; the release counts `floor(R)` frames from the level `L` AT the gate, `L (1 - k / (floor(R) - 1))`,
 *    and is 0 after (linear curves; the curves are pinned in `EnvelopeLawSpec` and `ModEnvelopeDefaultCurveSpec`);
 *  - the ratio: `2^(semitones * level / 12)`.
 *
 * An oracle, not a parity spec (`audio/ref/verification.md`, "One law, two hosts, two specs"): a slot wired to the
 * wrong knob, or a law changed in the shared core, is red here. The strip-against-node parity spec
 * (`StripPitchEnvelopeParitySpec`) retired with the strip host.
 */
class ClassicPitchEnvelopeSpec : StringSpec({

    val sampleRate = 48000
    val lin = AdsrCurve.Linear

    fun sec(frames: Double): Double = frames / sampleRate

    /** The law, written out: the level at note-relative frame [p], linear curves, the gate at [gate]. */
    fun oracleLevel(p: Int, a: Double, d: Double, s: Double, r: Double, gate: Int): Double {
        fun ads(q: Int): Double = when {
            q < a -> q / a
            q < a + d -> s + (1.0 - s) * (1.0 - (q - a) / d)
            else -> s
        }

        if (p < gate) {
            return ads(p)
        }

        val n = floor(r).toInt()
        val k = p - gate

        return if (k >= n - 1) 0.0 else ads(gate) * (1.0 - k.toDouble() / (n - 1))
    }

    fun penv(semitones: Double?, a: Double?, d: Double?, s: Double?, r: Double?, curve: AdsrCurve? = lin) = DoorPenv(
        semitones = semitones,
        attack = a?.let { sec(it) },
        decay = d?.let { sec(it) },
        sustain = s,
        release = r?.let { sec(it) },
        attackCurve = curve,
        decayCurve = curve,
        releaseCurve = curve,
    )

    /** The voice lives a second past its gate, so every release here renders inside the voice's life. */
    val longLife = DoorAdsr(release = 1.0)

    class Row(val title: String, val semitones: Double, val a: Double, val d: Double, val s: Double, val r: Double, val gate: Int)

    val rows = listOf(
        Row(title = "the gate after the sweep, a release inside the render", semitones = 7.0, a = 480.0, d = 960.0, s = 0.5, r = 1440.0, gate = 2400),
        Row(title = "the gate inside the decay: the release starts from the level there", semitones = 12.0, a = 480.0, d = 960.0, s = 0.25, r = 700.0, gate = 1000),
        Row(title = "the gate inside the attack, a negative depth and a raw sustain above 1", semitones = -9.0, a = 1200.5, d = 300.0, s = 1.5, r = 333.7, gate = 700),
    )

    for (row in rows) {
        for (onset in listOf(0, 37)) {
            "the stage follows the law, frame by frame: ${row.title} (onset $onset)" {
                val frames = row.gate + floor(row.r).toInt() + 300
                val ratios = renderPitchRatios(
                    doors = DoorFields(penv = penv(semitones = row.semitones, a = row.a, d = row.d, s = row.s, r = row.r), adsr = longLife),
                    frames = frames,
                    gateFrames = row.gate,
                    onsetFrames = onset,
                )

                val wrong = (0 until frames).firstOrNull { p ->
                    val expected = 2.0.pow(row.semitones * oracleLevel(p = p, a = row.a, d = row.d, s = row.s, r = row.r, gate = row.gate) / 12.0)

                    abs(ratios[p] - expected) > 1e-9
                }

                withClue("first frame off the law") { wrong shouldBe null }
                withClue("engaged: the pitch moved") { ratios.any { abs(it - 1.0) > 1e-3 } shouldBe true }
            }
        }
    }

    for (onset in listOf(0, 37)) {
        "a realtime note-off through the slot path: a held voice released inside the decay follows the law with the gate there (onset $onset)" {
            // The sprudel door's path end to end (`VoiceFactory`, `classic()`'s stage, `IgniteRenderer`'s per-block gate):
            // the voice is scheduled with a far gate and released at frame 1000 the way a realtime note-off does
            // (`Voice.releaseGate`), and must render what the law gives with the gate AT 1000.
            val a = 480.0
            val d = 960.0
            val s = 0.25
            val r = 700.0
            val releaseAt = 1000
            val frames = releaseAt + floor(r).toInt() + 300
            val ratios = renderPitchRatios(
                doors = DoorFields(penv = penv(semitones = 12.0, a = a, d = d, s = s, r = r), adsr = longLife),
                frames = frames,
                gateFrames = 1_000_000,
                onsetFrames = onset,
                releaseAtFrame = releaseAt,
            )

            val wrong = (0 until frames).firstOrNull { p ->
                val expected = 2.0.pow(12.0 * oracleLevel(p = p, a = a, d = d, s = s, r = r, gate = releaseAt) / 12.0)

                abs(ratios[p] - expected) > 1e-9
            }

            withClue("first frame off the law") { wrong shouldBe null }
            withClue("engaged: back on the note after the release") { (abs(ratios[frames - 1] - 1.0) < 1e-9) shouldBe true }
        }
    }

    "a gate inside the sweep returns to the note AT the gate with the default release 0" {
        // A 0.3 s decay, the gate at 0.2 s, no release written: the ratio is 1 (level 0) from the gate frame on, and
        // above 1 on the frame before it. The tolerance is the probe's rounding (a difference of two playheads).
        val gate = 9600
        val ratios = renderPitchRatios(
            doors = DoorFields(penv = DoorPenv(semitones = 24.0, attack = 0.001, decay = 0.3)),
            frames = gate + 2000,
            gateFrames = gate,
            onsetFrames = 37,
        )

        assertSoftly {
            withClue("the frame before the gate is still in the sweep") { (ratios[gate - 1] > 1.01) shouldBe true }
            withClue("on the note from the gate frame") { ratios.drop(gate).all { abs(it - 1.0) < 1e-9 } shouldBe true }
        }
    }

    "an unwritten penv.semitones switches nothing on: a stage-only write is the bare voice" {
        val frames = 4000
        val bare = renderPitchRatios(doors = DoorFields(), frames = frames, gateFrames = 2000)
        val stagesOnly = renderPitchRatios(doors = DoorFields(penv = DoorPenv(attack = 0.01, decay = 0.02, sustain = 0.5, release = 0.1)), frames = frames, gateFrames = 2000)

        withClue("the bare voice is on the note") { bare.all { it == 1.0 } shouldBe true }
        withClue("the stage-only write is the bare voice") { stagesOnly.toList() shouldBe bare.toList() }
    }

    "a non-finite penv.semitones is no pitch envelope, a non-finite sustain is the unwritten sustain" {
        // Written raw into the bag, past sprudel's boundary (which drops a non-finite value): the slot's leaf reads a
        // non-finite override as unset, so the switch reads its default 0 (off) and the sustain its default.
        val frames = 6000
        val semitonesKey = (IgnitorDsl.Slots.penv.semitones as IgnitorDsl.Param).name
        val sustainKey = (IgnitorDsl.Slots.penv.sustain as IgnitorDsl.Param).name
        val stages = DoorPenv(attack = 0.01, decay = 0.05)

        val bare = renderPitchRatios(doors = DoorFields(), frames = frames, gateFrames = 4000)
        val nanSemitones = renderPitchRatios(
            doors = DoorFields(penv = stages),
            data = VoiceData.empty.copy(ignitorParams = mapOf(semitonesKey to Double.NaN)),
            frames = frames,
            gateFrames = 4000,
        )
        val unwrittenSustain = renderPitchRatios(doors = DoorFields(penv = stages.copy(semitones = 12.0)), frames = frames, gateFrames = 4000)
        val infiniteSustain = renderPitchRatios(
            doors = DoorFields(penv = stages.copy(semitones = 12.0)),
            data = VoiceData.empty.copy(ignitorParams = mapOf(sustainKey to Double.POSITIVE_INFINITY)),
            frames = frames,
            gateFrames = 4000,
        )

        withClue("a NaN switch renders the bare voice") { nanSemitones.toList() shouldBe bare.toList() }
        withClue("an infinite sustain renders the unwritten sustain") { infiniteSustain.toList() shouldBe unwrittenSustain.toList() }
        withClue("anti-vacuous: the envelope with an unwritten sustain is engaged") { unwrittenSustain.toList() shouldNotBe bare.toList() }
    }

    "the sustain level the oracle reads is the one written: the sustain slot reaches the stage" {
        // A direct read of one sustained frame, so a slot wired to the wrong knob is named, not only "off the law".
        val ratios = renderPitchRatios(
            doors = DoorFields(penv = penv(semitones = 12.0, a = 100.0, d = 100.0, s = 0.75, r = 100.0), adsr = longLife),
            frames = 1000,
            gateFrames = 900,
        )

        ratios[500] shouldBe (2.0.pow(0.75) plusOrMinus 1e-9)
    }
})
