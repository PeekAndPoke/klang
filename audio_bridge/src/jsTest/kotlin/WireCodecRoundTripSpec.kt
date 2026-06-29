/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Feedback
import io.peekandpoke.klang.audio_bridge.wire.decode_PipelineDsl
import io.peekandpoke.klang.audio_bridge.wire.decode_SampleRequest
import io.peekandpoke.klang.audio_bridge.wire.decode_ScheduledVoice
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Feedback
import io.peekandpoke.klang.audio_bridge.wire.encode_PipelineDsl
import io.peekandpoke.klang.audio_bridge.wire.encode_SampleRequest
import io.peekandpoke.klang.audio_bridge.wire.encode_ScheduledVoice

/**
 * Round-trip guard for the KSP-generated worklet wire codec (JS-only — the codec uses `dynamic`).
 *
 * The codec is symmetric (both worklet ends are generated from the same types), so `decode(encode(x)) == x`
 * over a representative corpus is the correctness guarantee. Grows per phase; here: scalars, enums, sealed
 * dispatch, nested types, `List`, and `Map` (the `ScheduledVoice`/`VoiceData` + `Feedback` subgraphs).
 */
class WireCodecRoundTripSpec : StringSpec({

    "PipelineDsl round-trips (sealed StageDsl: data-object markers + Filter/Vca config)" {
        listOf(
            PipelineDsl.modern,
            PipelineDsl.pedal,
            PipelineDsl(
                listOf(
                    StageDsl.FilterMod,
                    StageDsl.Filter(cutoffOffsetPerAnalog = 0.01, drivePerAnalog = 0.7, driftRelToOsc = 4.0),
                    StageDsl.Vca(expK = 2.5, declickSeconds = 0.002),
                )
            ),
        ).forEach { decode_PipelineDsl(encode_PipelineDsl(it)) shouldBe it }
    }

    "SampleRequest round-trips (scalars + nulls)" {
        listOf(
            SampleRequest(bank = "MPC60", sound = "bd", index = 2, note = "c3"),
            SampleRequest(bank = null, sound = null, index = null, note = null),
        ).forEach { decode_SampleRequest(encode_SampleRequest(it)) shouldBe it }
    }

    "ScheduledVoice round-trips a populated VoiceData (adsr + all filter kinds + enum + map)" {
        val data = VoiceData.empty.copy(
            note = "c3", freqHz = 130.81, gain = 0.7, velocity = 0.9, soundIndex = 2,
            oscParams = mapOf("voices" to 7.0, "freqSpread" to 0.3),
            adsr = AdsrDef.Std(
                attack = 0.005, decay = 0.2, sustain = 0.6, release = 0.05,
                attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Square, releaseCurve = AdsrCurve.Cube,
            ),
            filters = FilterDefs(
                listOf(
                    FilterDef.HighPass(cutoffHz = 500.0, q = 2.0, envelope = null),
                    FilterDef.LowPass(
                        cutoffHz = 1000.0, q = 1.5,
                        envelope = FilterEnvDef(attack = 0.01, decay = 0.1, sustain = 0.5, release = 0.2, depth = 0.9),
                    ),
                    FilterDef.Formant(bands = listOf(FilterDef.Formant.Band(freq = 800.0, db = 0.0, q = 5.0)), mix = 0.5),
                )
            ),
        )
        val sv = ScheduledVoice("pb-1", data, startTime = 1.25, gateEndTime = 2.5, playbackStartTime = 0.5)

        decode_ScheduledVoice(encode_ScheduledVoice(sv)) shouldBe sv
    }

    "ScheduledVoice round-trips a minimal (mostly-null) VoiceData" {
        val sv = ScheduledVoice(
            "pb-2",
            VoiceData.empty.copy(note = "a4", freqHz = 440.0, adsr = AdsrDef.Std.empty),
            0.0, 1.0, 0.0,
        )
        decode_ScheduledVoice(encode_ScheduledVoice(sv)) shouldBe sv
    }

    "Feedback round-trips (sealed dispatch + nested list)" {
        val cases = listOf<KlangCommLink.Feedback>(
            KlangCommLink.Feedback.BackendReady(),
            KlangCommLink.Feedback.RequestSample("pb", SampleRequest("b", "s", 1, "c3")),
            KlangCommLink.Feedback.Diagnostics(
                playbackId = "pb", sampleRate = 48000, renderHeadroom = 0.8, activeVoiceCount = 3,
                cylinders = listOf(
                    KlangCommLink.Feedback.Diagnostics.CylinderState(id = 0, active = true),
                    KlangCommLink.Feedback.Diagnostics.CylinderState(id = 1, active = false),
                ),
                backendNowMs = 1234.5,
            ),
        )
        cases.forEach { decode_KlangCommLink_Feedback(encode_KlangCommLink_Feedback(it)) shouldBe it }
    }

    "Cmd round-trips (flattened sealed, ScheduledVoice list, recursive IgnitorDsl tree)" {
        val voice = ScheduledVoice("pb", VoiceData.empty.copy(note = "c3", adsr = AdsrDef.Std.empty), 0.0, 1.0, 0.0)
        val dsl = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine(freq = IgnitorDsl.Freq),
                IgnitorDsl.Adsr(inner = IgnitorDsl.SuperSaw(), attackSec = IgnitorDsl.Constant(0.02), attackCurve = AdsrCurve.Exponential),
                IgnitorDsl.Shimmer(inner = IgnitorDsl.Square(), pitches = listOf(0.0, 7.0, 12.0)),
            )
        )
        val cases = listOf<KlangCommLink.Cmd>(
            KlangCommLink.Cmd.Cleanup("pb"),
            KlangCommLink.Cmd.ScheduleVoice("pb", voice),
            KlangCommLink.Cmd.ScheduleVoices("pb", listOf(voice, voice)),
            KlangCommLink.Cmd.ReplaceVoices("pb", listOf(voice), afterTimeSec = 2.0),
            KlangCommLink.Cmd.RegisterIgnitor("pb", "mysynth", dsl),
            KlangCommLink.Cmd.Sample.NotFound(SampleRequest("b", "s", 1, "c3")),
        )
        cases.forEach { decode_KlangCommLink_Cmd(encode_KlangCommLink_Cmd(it)) shouldBe it }
    }

    "Cmd.Sample.Chunk round-trips its DoubleArray (compared by content)" {
        val chunk = KlangCommLink.Cmd.Sample.Chunk(
            req = SampleRequest("b", "s", null, "c3"), note = "c3", pitchHz = 261.6, sampleRate = 48000,
            meta = SampleMetadata.default, totalSize = 3, isLastChunk = true, chunkOffset = 0,
            data = doubleArrayOf(0.1, -0.2, 0.3),
        )
        val dec = decode_KlangCommLink_Cmd(encode_KlangCommLink_Cmd(chunk)) as KlangCommLink.Cmd.Sample.Chunk
        dec.req shouldBe chunk.req
        dec.note shouldBe "c3"; dec.pitchHz shouldBe 261.6; dec.sampleRate shouldBe 48000
        dec.totalSize shouldBe 3; dec.isLastChunk shouldBe true; dec.chunkOffset shouldBe 0
        dec.meta shouldBe chunk.meta
        dec.data.toList() shouldBe chunk.data.toList()
    }
})
