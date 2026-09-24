/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Feedback
import io.peekandpoke.klang.audio_bridge.wire.decode_KatalystDsl
import io.peekandpoke.klang.audio_bridge.wire.decode_MasterDsl
import io.peekandpoke.klang.audio_bridge.wire.decode_PipelineDsl
import io.peekandpoke.klang.audio_bridge.wire.decode_SampleRequest
import io.peekandpoke.klang.audio_bridge.wire.decode_ScheduledVoice
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Feedback
import io.peekandpoke.klang.audio_bridge.wire.encode_KatalystDsl
import io.peekandpoke.klang.audio_bridge.wire.encode_MasterDsl
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
                    // on = false, not the default true: with the default, this case passes even
                    // if the codec drops the field entirely.
                    StageDsl.Vca(expK = 2.5, declickSeconds = 0.002, on = false),
                )
            ),
        ).forEach { decode_PipelineDsl(encode_PipelineDsl(it)) shouldBe it }
    }

    "a non-finite Param default survives the trip: SLOT_UNSET is how the wire says 'never set'" {
        // `KatalystDsl.classic` puts SLOT_UNSET (NaN) on ELEVEN slots (all five compressor knobs,
        // `duck.orbit`, `reverb.lowpass`, and the name and amount of the body and the vowel), so
        // this is not a corner case
        // but the everyday chain. The codec rides a structured-clone JS object rather than JSON, so
        // NaN travels; this pins that, and pins that the comparison is not vacuous (an Infinity and
        // a finite neighbour are in the same list).
        listOf(
            KatalystDsl.of(KatalystStageDsl.Compressor(threshold = IgnitorDsl.Param("compressor.threshold", SLOT_UNSET))),
            KatalystDsl.of(KatalystStageDsl.Duck(orbit = IgnitorDsl.Param("duck.orbit", Double.POSITIVE_INFINITY))),
            KatalystDsl.of(KatalystStageDsl.Reverb(lowpass = IgnitorDsl.Constant(SLOT_UNSET))),
            KatalystDsl.of(KatalystStageDsl.Gain(IgnitorDsl.Param("g", 1.4))),
        ).forEach { decode_KatalystDsl(encode_KatalystDsl(it)) shouldBe it }

        // ...and the decoded default really is non-finite, not a zero the equality glossed over.
        val decoded = decode_KatalystDsl(encode_KatalystDsl(KatalystDsl.classic))
        val threshold = decoded.stages.filterIsInstance<KatalystStageDsl.Compressor>().single().threshold

        (threshold as IgnitorDsl.Param).default.isFinite() shouldBe false
    }

    "a DECODED classic chain maps to the same synthetic name: the only fresh NaN in the system" {
        // Every other NaN in the chain came from the `SLOT_UNSET` literal, so it is the same double
        // and hashes the same. A decoded one did not: it crossed the structured-clone boundary,
        // which is not required to preserve a NaN's payload bits, and Kotlin/JS hashes a Double by
        // its bit pattern. If a decoded NaN hashed differently from the literal, the identity map
        // would mint the decoded chain a SECOND name, and the backend would register, build and
        // crossfade one chain as two. This is the only producer of that case in the system, and it
        // can only be tested here, where the real codec is.
        val decoded = decode_KatalystDsl(encode_KatalystDsl(KatalystDsl.classic))

        decoded shouldBe KatalystDsl.classic
        decoded.uniqueId() shouldBe KatalystDsl.classic.uniqueId()

        // ...and the decoded slot really is non-finite, so the row above is about a NaN and not
        // about a zero the codec substituted on the way through.
        val threshold = decoded.stages.filterIsInstance<KatalystStageDsl.Compressor>().single().threshold

        (threshold as IgnitorDsl.Param).default.isFinite() shouldBe false

        // Encoding the decoded chain again must land on the same name too: a second trip is what a
        // relayed command does (frontend to worklet to a nested playback).
        decode_KatalystDsl(encode_KatalystDsl(decoded)).uniqueId() shouldBe KatalystDsl.classic.uniqueId()
    }

    "KatalystDsl round-trips (every KatalystStageDsl variant, with IgnitorDsl knobs)" {
        // Every variant, and on each the shapes a codec can silently lose: a Param knob next to a
        // Constant one, the nullable `lowpass` in BOTH states, an empty section list next to a
        // populated one, and the two catalogue-index knobs (`body.material`, `vowel.vowel`) both
        // naming something and unset.
        listOf(
            KatalystDsl(emptyList()),
            KatalystDsl.classic,
            KatalystDsl.of(
                KatalystStageDsl.Body(
                    material = IgnitorDsl.Constant(BodyMaterials.indexOf("wood")),
                    wet = IgnitorDsl.Constant(0.7),
                    floor = IgnitorDsl.Constant(0.3),
                ),
                KatalystStageDsl.Body(),
                KatalystStageDsl.Vowel(
                    vowel = IgnitorDsl.Constant(VowelBands.indexOf("soprano:a")),
                    wet = IgnitorDsl.Param("vowel.wet", 0.6),
                    floor = IgnitorDsl.Constant(0.1),
                ),
                KatalystStageDsl.Vowel(),
                KatalystStageDsl.Delay(
                    wet = IgnitorDsl.Constant(0.2), time = IgnitorDsl.Constant(0.375),
                    feedback = IgnitorDsl.Constant(0.45), cap = IgnitorDsl.Constant(3.0),
                ),
                // every reverb field set, including the nullable lowpass
                KatalystStageDsl.Reverb(
                    wet = IgnitorDsl.Constant(0.4), size = IgnitorDsl.Constant(8.0),
                    lowpass = IgnitorDsl.Constant(9000.0),
                ),
                // ...and the nullable branch: lowpass absent
                KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.4), size = IgnitorDsl.Constant(8.0)),
                KatalystStageDsl.Phaser(
                    rate = IgnitorDsl.Constant(0.3), wet = IgnitorDsl.Constant(0.5),
                    center = IgnitorDsl.Constant(800.0), sweep = IgnitorDsl.Constant(1200.0),
                    floor = IgnitorDsl.Constant(0.2),
                ),
                KatalystStageDsl.Compressor(
                    threshold = IgnitorDsl.Constant(-21.0), ratio = IgnitorDsl.Constant(3.0),
                    knee = IgnitorDsl.Constant(6.0), attack = IgnitorDsl.Constant(0.005),
                    release = IgnitorDsl.Constant(0.12),
                ),
                KatalystStageDsl.Duck(
                    orbit = IgnitorDsl.Constant(2.0), depth = IgnitorDsl.Constant(0.8),
                    attack = IgnitorDsl.Constant(0.05),
                ),
                KatalystStageDsl.Eq(
                    sections = listOf(
                        IgnitorDsl.EqSection.Bell(freq = IgnitorDsl.Constant(300.0), q = IgnitorDsl.Constant(0.8), db = IgnitorDsl.Constant(2.0)),
                        IgnitorDsl.EqSection.Lowpass(freq = IgnitorDsl.Constant(4000.0)),
                    )
                ),
                KatalystStageDsl.Eq(),
                KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(1.4)),
            ),
        ).forEach { decode_KatalystDsl(encode_KatalystDsl(it)) shouldBe it }
    }

    "MasterDsl round-trips (sealed MasterStageDsl: gain / limiter / reverb / delay)" {
        listOf(
            MasterDsl.default,
            MasterDsl.of(MasterStageDsl.Gain(gain = 2.5)),
            MasterDsl.of(
                MasterStageDsl.Gain(gain = 1.8),
                // every reverb field set, including the nullable lowpass
                MasterStageDsl.Reverb(wet = 0.4, size = 8.0, lowpass = 9000.0),
                // ...and the nullable branch: lowpass absent
                MasterStageDsl.Reverb(wet = 0.4, size = 8.0),
                MasterStageDsl.Delay(wet = 0.2, time = 0.375, feedback = 0.45, cap = 3.0),
                MasterStageDsl.Limiter(
                    threshold = -0.5, ratio = 12.0, knee = 1.0,
                    attackSeconds = 0.002, releaseSeconds = 0.25,
                    // Non-default on purpose: a field left at its default round-trips even if the
                    // codec drops it entirely, which is why every field here is set explicitly.
                    lookaheadSeconds = 0.003,
                ),
            ),
        ).forEach { decode_MasterDsl(encode_MasterDsl(it)) shouldBe it }
    }

    "SampleRequest round-trips (scalars + nulls)" {
        listOf(
            SampleRequest(bank = "MPC60", sound = "bd", index = 2, note = "c3"),
            SampleRequest(bank = null, sound = null, index = null, note = null),
        ).forEach { decode_SampleRequest(encode_SampleRequest(it)) shouldBe it }
    }

    "ScheduledVoice round-trips a populated VoiceData (adsr + all filter kinds + enum + map)" {
        val data = VoiceData.empty.copy(
            note = "c3", freqHz = 130.81, gain = 0.7, soundIndex = 2, cull = 0.2,
            oscParams = mapOf("voices" to 7.0, "spread" to 0.3),
            // The second string-keyed map, the orbit's slot state: same shape, different host, and
            // a key with a dot in it (the `<stage>.<knob>` spelling every classic slot carries).
            katalystParams = mapOf("reverb.size" to 6.0, "compressor.ratio" to 8.0, "room" to 0.5),
            adsr = AdsrDef.Std(
                attack = 0.005, decay = 0.2, sustain = 0.6, release = 0.05,
                attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Square, releaseCurve = AdsrCurve.Cube,
                // Non-default on purpose (default is null): `Boolean?` through a `dynamic` codec is
                // exactly the shape where `false` and `undefined` can be confused.
                on = false,
            ),
            filters = FilterDefs(
                listOf(
                    FilterDef.HighPass(freq = 500.0, q = 2.0, envelope = null, passes = 3),
                    FilterDef.LowPass(
                        freq = 1000.0, q = 1.5,
                        envelope = FilterEnvDef(attack = 0.01, decay = 0.1, sustain = 0.5, release = 0.2, depth = 0.9),
                        passes = 2,
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
                warehouse = KlangCommLink.Feedback.Diagnostics.WarehouseStats.empty,
            ),
            // The warehouse snapshot rides the same message.
            KlangCommLink.Feedback.Diagnostics(
                playbackId = "pb", sampleRate = 44100, renderHeadroom = 0.5, activeVoiceCount = 0,
                cylinders = emptyList(), backendNowMs = 1.0,
                warehouse = KlangCommLink.Feedback.Diagnostics.WarehouseStats(
                    ringIdleBytes = 6_160_384.0, ringIdleCount = 16, ringDirtyCount = 2, ringAllocations = 16, ringHits = 8,
                    ringFailures = 0, ringDropped = 0, ringSyncCleans = 1,
                    reverbIdleCount = 16, reverbDirtyCount = 0, reverbAllocations = 16, reverbHits = 4, reverbFailures = 0, reverbDropped = 0,
                    cylinderIdleCount = 8, cylinderAllocations = 16, cylinderHits = 8, cylinderDropped = 0,
                    scratchCapacity = 64, scratchHighWater = 30, scratchLateAllocations = 0, scratchUnbalancedReleases = 0,
                    sampleBytes = 12_345_678.0, sampleCount = 7, sampleAllocationFailures = 0,
                    droppedVoices = 3, deniedRents = 1,
                ),
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
            // Both gateDurSec variants: fixed length AND the held case (null must survive the trip)
            KlangCommLink.Cmd.StartRealtimeVoice("pb", RealtimeVoice(liveId = 7, data = voice.data, gateDurSec = 0.4)),
            KlangCommLink.Cmd.StartRealtimeVoice("pb", RealtimeVoice(liveId = 8, data = voice.data, gateDurSec = null)),
            KlangCommLink.Cmd.StopRealtimeVoice("pb", liveId = 8),
            KlangCommLink.Cmd.RegisterIgnitor("pb", "mysynth", dsl),
            KlangCommLink.Cmd.RegisterMaster("pb", "master-0", MasterDsl.of(MasterStageDsl.Gain(2.0))),
            KlangCommLink.Cmd.RegisterKatalyst(
                "pb", "katalyst-0",
                KatalystDsl.of(KatalystStageDsl.Gain(IgnitorDsl.Constant(1.4)), KatalystStageDsl.Reverb()),
            ),
            KlangCommLink.Cmd.RegisterKatalyst("pb", "katalyst-1", KatalystDsl.classic),
            KlangCommLink.Cmd.Sample.NotFound(SampleRequest("b", "s", 1, "c3")),
        )
        cases.forEach { decode_KlangCommLink_Cmd(encode_KlangCommLink_Cmd(it)) shouldBe it }
    }

    "ScheduledVoice round-trips the katalyst reference" {
        // The orbit chain rides the same field family as the master: if the codec dropped it,
        // every declared chain would silently stay unregistered on the far side.
        val control = ScheduledVoice(
            "pb", VoiceData.empty.copy(katalyst = "katalyst-3", control = true), 0.0, 1.0, 0.0,
        )
        val sounding = ScheduledVoice(
            "pb", VoiceData.empty.copy(note = "c3", sound = "sine", katalyst = "katalyst-3"), 0.0, 1.0, 0.0,
        )

        listOf(control, sounding).forEach {
            decode_ScheduledVoice(encode_ScheduledVoice(it)) shouldBe it
        }
    }

    "ScheduledVoice round-trips the master reference + control flag" {
        // If `control` were dropped by the codec, every master(...) carrier would decode as an
        // audible default-oscillator voice — once per cycle, forever. Guard both directions.
        val control = ScheduledVoice(
            "pb", VoiceData.empty.copy(master = "master-7", control = true), 0.0, 1.0, 0.0,
        )
        val sounding = ScheduledVoice(
            "pb", VoiceData.empty.copy(note = "c3", sound = "sine", master = "master-7"), 0.0, 1.0, 0.0,
        )

        listOf(control, sounding).forEach {
            decode_ScheduledVoice(encode_ScheduledVoice(it)) shouldBe it
        }
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
