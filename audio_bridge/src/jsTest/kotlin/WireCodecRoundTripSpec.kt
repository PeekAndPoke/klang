/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.wire.WIRE_SCHEMA_HASH
import io.peekandpoke.klang.audio_bridge.wire.decode_KatalystDsl
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Feedback
import io.peekandpoke.klang.audio_bridge.wire.decode_SampleRequest
import io.peekandpoke.klang.audio_bridge.wire.decode_ScheduledVoice
import io.peekandpoke.klang.audio_bridge.wire.encode_KatalystDsl
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Feedback
import io.peekandpoke.klang.audio_bridge.wire.encode_SampleRequest
import io.peekandpoke.klang.audio_bridge.wire.encode_ScheduledVoice
import io.peekandpoke.klang.common.infra.klangMapOf
import io.peekandpoke.klang.common.infra.toMap

/**
 * Round-trip guard for the KSP-generated worklet wire codec (JS-only — the codec uses `dynamic`).
 *
 * The codec is symmetric (both worklet ends are generated from the same types), so `decode(encode(x)) == x`
 * over a representative corpus is the correctness guarantee. Grows per phase; here: scalars, enums, sealed
 * dispatch, nested types, `List`, and `Map` (the `ScheduledVoice`/`VoiceData` + `Feedback` subgraphs).
 */
class WireCodecRoundTripSpec : StringSpec({

    "a non-finite Param default survives the trip: SLOT_UNSET is how the wire says 'never set'" {
        // `KatalystDsl.classic` puts SLOT_UNSET (NaN) on ELEVEN slots (all five compressor knobs,
        // `duck.orbit`, `reverb.lowpass`, and the name and amount of the body and the vowel), so
        // this is not a corner case
        // but the everyday chain. The codec rides a structured-clone JS object rather than JSON, so
        // NaN travels; this pins that, and pins that the comparison is not vacuous (an Infinity and
        // a finite neighbour are in the same list).
        listOf(
            KatalystDsl.of(KatalystStageDsl.Compressor(threshold = KatalystKnobDsl.Param("compressor.threshold", SLOT_UNSET))),
            KatalystDsl.of(KatalystStageDsl.Duck(orbit = KatalystKnobDsl.Param("duck.orbit", Double.POSITIVE_INFINITY))),
            KatalystDsl.of(KatalystStageDsl.Reverb(lowpass = KatalystKnobDsl.Constant(SLOT_UNSET))),
            KatalystDsl.of(KatalystStageDsl.Gain(KatalystKnobDsl.Param("g", 1.4))),
        ).forEach { decode_KatalystDsl(encode_KatalystDsl(it)) shouldBe it }

        // ...and the decoded default really is non-finite, not a zero the equality glossed over.
        val decoded = decode_KatalystDsl(encode_KatalystDsl(KatalystDsl.classic))
        val threshold = decoded.stages.filterIsInstance<KatalystStageDsl.Compressor>().single().threshold

        (threshold as KatalystKnobDsl.Param).default.isFinite() shouldBe false
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

        (threshold as KatalystKnobDsl.Param).default.isFinite() shouldBe false

        // Encoding the decoded chain again must land on the same name too: a second trip is what a
        // relayed command does (frontend to worklet to a nested playback).
        decode_KatalystDsl(encode_KatalystDsl(decoded)).uniqueId() shouldBe KatalystDsl.classic.uniqueId()
    }

    "every knob of every stage carries both KatalystKnobDsl leaves through the trip" {
        // The knob type of step 3 of KLANG-00302 (katalyst knob expressions): per stage, one chain whose
        // every
        // knob is a Constant and one whose every knob is a Param (each its own name, a description on one), so a
        // knob the codec drops, swaps with a neighbour or decodes as the other leaf goes red on its stage. The eq
        // carries the Q2 residue: a section knob is an Ignitor leaf, the Param one included.
        var n = 0
        fun k(): KatalystKnobDsl.Constant = KatalystKnobDsl.Constant(0.125 * ++n)
        fun p(): KatalystKnobDsl.Param = KatalystKnobDsl.Param(name = "knob${++n}", default = 0.5 * n, description = "d$n")

        listOf(
            KatalystStageDsl.Body(material = k(), wet = k(), floor = k()),
            KatalystStageDsl.Body(material = p(), wet = p(), floor = p()),
            KatalystStageDsl.Vowel(vowel = k(), wet = k(), floor = k()),
            KatalystStageDsl.Vowel(vowel = p(), wet = p(), floor = p()),
            KatalystStageDsl.Delay(wet = k(), time = k(), feedback = k(), cap = k()),
            KatalystStageDsl.Delay(wet = p(), time = p(), feedback = p(), cap = p()),
            KatalystStageDsl.Reverb(wet = k(), size = k(), lowpass = k()),
            KatalystStageDsl.Reverb(wet = p(), size = p(), lowpass = p()),
            KatalystStageDsl.Phaser(rate = k(), wet = k(), center = k(), sweep = k(), floor = k()),
            KatalystStageDsl.Phaser(rate = p(), wet = p(), center = p(), sweep = p(), floor = p()),
            KatalystStageDsl.Compressor(threshold = k(), ratio = k(), knee = k(), attack = k(), release = k(), lookahead = 0.002),
            KatalystStageDsl.Compressor(threshold = p(), ratio = p(), knee = p(), attack = p(), release = p()),
            KatalystStageDsl.Distort(amount = k(), shape = 2, oversample = 8),
            KatalystStageDsl.Distort(amount = p()),
            KatalystStageDsl.Duck(orbit = k(), depth = k(), attack = k()),
            KatalystStageDsl.Duck(orbit = p(), depth = p(), attack = p()),
            KatalystStageDsl.Gain(gain = k()),
            KatalystStageDsl.Gain(gain = p()),
            KatalystStageDsl.Plus(other = k()),
            KatalystStageDsl.Plus(other = p()),
            KatalystStageDsl.Minus(other = k()),
            KatalystStageDsl.Minus(other = p()),
            KatalystStageDsl.Times(other = k()),
            KatalystStageDsl.Times(other = p()),
            KatalystStageDsl.Div(other = k()),
            KatalystStageDsl.Div(other = p()),
            KatalystStageDsl.Pow(exp = k()),
            KatalystStageDsl.Pow(exp = p()),
            KatalystStageDsl.Min(other = k()),
            KatalystStageDsl.Min(other = p()),
            KatalystStageDsl.Max(other = k()),
            KatalystStageDsl.Max(other = p()),
            KatalystStageDsl.Eq(
                sections = listOf(
                    IgnitorDsl.EqSection.Bell(
                        freq = IgnitorDsl.Param("eqf", 900.0, "the bell"), q = IgnitorDsl.Constant(0.8), db = IgnitorDsl.Param("eqdb", 3.0),
                    ),
                    IgnitorDsl.EqSection.RawTap(freq = IgnitorDsl.Constant(2500.0), q = IgnitorDsl.Constant(0.7), gain = IgnitorDsl.Param("tap", 0.5)),
                ),
            ),
        ).forEach { stage ->
            withClue(stage.toString()) { decode_KatalystDsl(encode_KatalystDsl(KatalystDsl.of(stage))) shouldBe KatalystDsl.of(stage) }
        }
    }

    "KatalystDsl round-trips (every KatalystStageDsl variant, with KatalystKnobDsl knobs)" {
        // Every variant, and on each the shapes a codec can silently lose: a Param knob next to a
        // Constant one, the nullable `lowpass` in BOTH states, an empty section list next to a
        // populated one, and the two catalogue-index knobs (`body.material`, `vowel.vowel`) both
        // naming something and unset.
        listOf(
            KatalystDsl(emptyList()),
            KatalystDsl.classic,
            KatalystDsl.of(
                KatalystStageDsl.Body(
                    material = KatalystKnobDsl.Constant(BodyMaterials.indexOf("wood")),
                    wet = KatalystKnobDsl.Constant(0.7),
                    floor = KatalystKnobDsl.Constant(0.3),
                ),
                KatalystStageDsl.Body(),
                KatalystStageDsl.Vowel(
                    vowel = KatalystKnobDsl.Constant(VowelBands.indexOf("soprano:a")),
                    wet = KatalystKnobDsl.Param("vowel.wet", 0.6),
                    floor = KatalystKnobDsl.Constant(0.1),
                ),
                KatalystStageDsl.Vowel(),
                KatalystStageDsl.Delay(
                    wet = KatalystKnobDsl.Constant(0.2), time = KatalystKnobDsl.Constant(0.375),
                    feedback = KatalystKnobDsl.Constant(0.45), cap = KatalystKnobDsl.Constant(3.0),
                ),
                // every reverb field set, including the nullable lowpass
                KatalystStageDsl.Reverb(
                    wet = KatalystKnobDsl.Constant(0.4), size = KatalystKnobDsl.Constant(8.0),
                    lowpass = KatalystKnobDsl.Constant(9000.0),
                ),
                // ...and the nullable branch: lowpass absent
                KatalystStageDsl.Reverb(wet = KatalystKnobDsl.Constant(0.4), size = KatalystKnobDsl.Constant(8.0)),
                KatalystStageDsl.Phaser(
                    rate = KatalystKnobDsl.Constant(0.3), wet = KatalystKnobDsl.Constant(0.5),
                    center = KatalystKnobDsl.Constant(800.0), sweep = KatalystKnobDsl.Constant(1200.0),
                    floor = KatalystKnobDsl.Constant(0.2),
                ),
                // ...with a lookahead, the one plain-Double knob of the Katalyst (step 12 C2)
                KatalystStageDsl.Compressor(
                    threshold = KatalystKnobDsl.Constant(-21.0), ratio = KatalystKnobDsl.Constant(3.0),
                    knee = KatalystKnobDsl.Constant(6.0), attack = KatalystKnobDsl.Constant(0.005),
                    release = KatalystKnobDsl.Constant(0.12), lookahead = 0.004,
                ),
                KatalystStageDsl.Duck(
                    orbit = KatalystKnobDsl.Constant(2.0), depth = KatalystKnobDsl.Constant(0.8),
                    attack = KatalystKnobDsl.Constant(0.05),
                ),
                // the bus distort: a slot amount and the two plain Int knobs fixed with the chain, and bare
                KatalystStageDsl.Distort(
                    amount = KatalystKnobDsl.Param("drive", 0.15), shape = DistortionShapes.indexOf("tube").toInt(), oversample = 4,
                ),
                KatalystStageDsl.Distort(),
                KatalystStageDsl.Eq(
                    sections = listOf(
                        IgnitorDsl.EqSection.Bell(freq = IgnitorDsl.Constant(300.0), q = IgnitorDsl.Constant(0.8), db = IgnitorDsl.Constant(2.0)),
                        IgnitorDsl.EqSection.Lowpass(freq = IgnitorDsl.Constant(4000.0)),
                    )
                ),
                KatalystStageDsl.Eq(),
                KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(1.4)),
                // the arithmetic stages: a constant or a slot operand each, and the knob-free rectifier
                KatalystStageDsl.Plus(other = KatalystKnobDsl.Constant(0.1)),
                KatalystStageDsl.Minus(other = KatalystKnobDsl.Param("off", 0.2)),
                KatalystStageDsl.Times(other = KatalystKnobDsl.Constant(-1.0)),
                KatalystStageDsl.Div(other = KatalystKnobDsl.Param("d", 2.0)),
                KatalystStageDsl.Pow(exp = KatalystKnobDsl.Constant(1.5)),
                KatalystStageDsl.Min(other = KatalystKnobDsl.Constant(0.9)),
                KatalystStageDsl.Max(other = KatalystKnobDsl.Param("floor", -0.9)),
                KatalystStageDsl.Abs,
                // A chain inside a stage: an empty branch, a branch of two, and a parallel nested in a branch.
                KatalystStageDsl.Parallel(
                    branches = listOf(
                        KatalystDsl(emptyList()),
                        KatalystDsl.of(KatalystStageDsl.Distort(), KatalystStageDsl.Gain(gain = KatalystKnobDsl.Param("g", 0.25))),
                        KatalystDsl.of(KatalystStageDsl.Parallel(branches = listOf(KatalystDsl.of(KatalystStageDsl.Duck())))),
                    ),
                ),
            ),
        ).forEach { decode_KatalystDsl(encode_KatalystDsl(it)) shouldBe it }
    }

    "every KatalystKnobDsl expression node carries its operands through the trip, in their order" {
        // Step 4 of KLANG-00302 (katalyst knob expressions): a row per node, each operand a different
        // leaf (and the leaves' values all different), so an operand the codec drops, swaps or decodes as another node
        // goes red on its node. Then one deep tree, every node nested in another.
        val a = KatalystKnobDsl.Param(name = "a", default = 0.25, description = "the a")
        val b = KatalystKnobDsl.Constant(-3.5)
        val c = KatalystKnobDsl.Param(name = "c", default = 7.0)

        listOf(
            KatalystKnobDsl.Plus(left = a, right = b),
            KatalystKnobDsl.Minus(left = b, right = a),
            KatalystKnobDsl.Times(left = a, right = c),
            KatalystKnobDsl.Div(left = c, right = b),
            KatalystKnobDsl.Pow(base = a, exp = b),
            KatalystKnobDsl.Min(left = b, right = c),
            KatalystKnobDsl.Max(left = c, right = a),
            KatalystKnobDsl.Neg(inner = a),
            KatalystKnobDsl.Abs(inner = b),
            KatalystKnobDsl.Range(inner = a, from = b, to = c),
            KatalystKnobDsl.Range(
                inner = KatalystKnobDsl.Neg(
                    inner = KatalystKnobDsl.Times(left = a, right = KatalystKnobDsl.Abs(inner = b)),
                ),
                from = KatalystKnobDsl.Max(
                    left = KatalystKnobDsl.Pow(base = c, exp = b),
                    right = KatalystKnobDsl.Div(left = a, right = c),
                ),
                to = KatalystKnobDsl.Min(
                    left = KatalystKnobDsl.Plus(left = b, right = c),
                    right = KatalystKnobDsl.Minus(left = c, right = a),
                ),
            ),
        ).forEach { knob ->
            val chain = KatalystDsl.of(
                KatalystStageDsl.Gain(gain = knob),
                KatalystStageDsl.Reverb(size = knob, lowpass = knob),
            )

            withClue(knob.toString()) { decode_KatalystDsl(encode_KatalystDsl(chain)) shouldBe chain }
        }
    }

    "every KatalystKnobDsl modulator carries its operands through the trip, in their order" {
        // Step 5a of KLANG-00302 (katalyst knob expressions): a row per modulator with every operand a
        // different leaf, a row per modulator on its defaults, then modulators nested in each other and in the
        // arithmetic (a modulator's operand is any knob, a modulator included).
        val a = KatalystKnobDsl.Param(name = "a", default = 0.25, description = "the a")
        val b = KatalystKnobDsl.Constant(-3.5)
        val c = KatalystKnobDsl.Param(name = "c", default = 7.0)

        listOf(
            KatalystKnobDsl.Sine(freq = a, phase = b),
            KatalystKnobDsl.Saw(freq = b, phase = c),
            KatalystKnobDsl.Ramp(freq = c, phase = a),
            KatalystKnobDsl.Tri(freq = a, phase = c),
            KatalystKnobDsl.Square(freq = c, duty = a, phase = b),
            KatalystKnobDsl.PerlinNoise(rate = a, octaves = b, persistence = c),
            KatalystKnobDsl.BerlinNoise(rate = c, octaves = a, persistence = b),
            KatalystKnobDsl.Sine(freq = b),
            KatalystKnobDsl.Saw(freq = b),
            KatalystKnobDsl.Ramp(freq = b),
            KatalystKnobDsl.Tri(freq = b),
            KatalystKnobDsl.Square(freq = b),
            KatalystKnobDsl.PerlinNoise(),
            KatalystKnobDsl.BerlinNoise(),
            KatalystKnobDsl.Range(
                inner = KatalystKnobDsl.Square(
                    freq = KatalystKnobDsl.Sine(freq = a, phase = KatalystKnobDsl.Tri(freq = c)),
                    duty = KatalystKnobDsl.Abs(inner = KatalystKnobDsl.BerlinNoise(rate = b)),
                ),
                from = KatalystKnobDsl.PerlinNoise(octaves = KatalystKnobDsl.Ramp(freq = a)),
                to = KatalystKnobDsl.Times(left = KatalystKnobDsl.Saw(freq = c), right = b),
            ),
        ).forEach { knob ->
            val chain = KatalystDsl.of(
                KatalystStageDsl.Gain(gain = knob),
                KatalystStageDsl.Reverb(size = knob, lowpass = knob),
            )

            withClue(knob.toString()) { decode_KatalystDsl(encode_KatalystDsl(chain)) shouldBe chain }
        }
    }

    "SampleRequest round-trips (scalars + nulls)" {
        listOf(
            SampleRequest(bank = "MPC60", sound = "bd", index = 2, note = "c3"),
            SampleRequest(bank = null, sound = null, index = null, note = null),
        ).forEach { decode_SampleRequest(encode_SampleRequest(it)) shouldBe it }
    }

    "ScheduledVoice round-trips a populated VoiceData (both slot maps, a false Boolean?)" {
        val data = VoiceData.empty.copy(
            note = "c3", freqHz = 130.81, gain = 0.7, soundIndex = 2, cull = 0.2,
            ignitorParams = klangMapOf("voices" to 7.0, "spread" to 0.3, "lpf.freq" to 1000.0, "adsr.on" to 0.0),
            // The second string-keyed map, the orbit's slot state: same shape, different host, and
            // a key with a dot in it (the `<stage>.<knob>` spelling every classic slot carries).
            katalystParams = klangMapOf("reverb.size" to 6.0, "compressor.ratio" to 8.0, "room" to 0.5),
            // Non-default on purpose (default is null): `Boolean?` through a `dynamic` codec is exactly the
            // shape where `false` and `undefined` can be confused.
            control = false,
        )
        val sv = ScheduledVoice("pb-1", data, startTime = 1.25, gateEndTime = 2.5, playbackStartTime = 0.5)

        decode_ScheduledVoice(encode_ScheduledVoice(sv)) shouldBe sv
    }

    "the decoded slot maps keep the order they were sent in and a NaN slot, and an absent key reads null" {
        // The engine reads these maps by key (engine follow-up 2), and the decoder must hand every slot over as it
        // was sent. Equality with the sent maps is the row above.
        val data = VoiceData.empty.copy(
            ignitorParams = klangMapOf("lpf.freq" to 1000.0, "analog" to 0.2, "adsr.attack" to 0.01),
            katalystParams = klangMapOf("reverb.wet" to 0.3, "delay.wet" to SLOT_UNSET, "body.material" to 2.0),
        )
        val decoded = decode_ScheduledVoice(encode_ScheduledVoice(ScheduledVoice("pb-1", data, 0.0, 1.0, 0.0))).data
        val ignitor = decoded.ignitorParams
        val katalyst = decoded.katalystParams

        ignitor!!.toMap().keys.toList() shouldBe listOf("lpf.freq", "analog", "adsr.attack")
        katalyst!!.toMap().keys.toList() shouldBe listOf("reverb.wet", "delay.wet", "body.material")
        katalyst["delay.wet"]!!.isNaN() shouldBe true
        katalyst["reverb.wet"] shouldBe 0.3
        katalyst["gain.gain"] shouldBe null
    }

    "KlangMap slot maps (sprudel's) encode every entry, in order, and round-trip" {
        // Pins that every entry of a KlangMap reaches the wire, in order (the encoder walks it with `forEachEntry`,
        // its one walk since step 5).
        val data = VoiceData.empty.copy(
            ignitorParams = klangMapOf("lpf.freq" to 1000.0, "analog" to 0.2),
            katalystParams = klangMapOf("reverb.wet" to 0.3, "delay.wet" to SLOT_UNSET, "body.material" to 2.0),
        )
        val decoded = decode_ScheduledVoice(encode_ScheduledVoice(ScheduledVoice("pb-1", data, 0.0, 1.0, 0.0))).data

        decoded shouldBe data
        decoded.ignitorParams!!.toMap().keys.toList() shouldBe listOf("lpf.freq", "analog")
        decoded.katalystParams!!.toMap().keys.toList() shouldBe listOf("reverb.wet", "delay.wet", "body.material")
        decoded.katalystParams!!["delay.wet"]!!.isNaN() shouldBe true
    }

    "the wire object is byte for byte what it was when the slot maps were stdlib maps (a golden from before step 5)" {
        // Engine follow-up 2, step 5: `KlangMap` became the fields' type. The golden is JSON.stringify of the same
        // voice encoded at `ccde9f9e`, when the fields were `Map<String, Double>` (built with `linkedMapOf`): same
        // keys, same order, same numbers. JSON prints a NaN slot as null; the rows above pin the NaN through the codec.
        val data = VoiceData.empty.copy(
            note = "c3", freqHz = 130.81, gain = 0.7, soundIndex = 2, cull = 0.2,
            ignitorParams = klangMapOf("voices" to 7.0, "spread" to 0.3, "lpf.freq" to 1000.0, "adsr.on" to 0.0),
            katalystParams = klangMapOf("reverb.size" to 6.0, "delay.wet" to SLOT_UNSET, "body.material" to 2.0, "room" to 0.5),
            control = false,
        )
        val sv = ScheduledVoice("pb-1", data, startTime = 1.25, gateEndTime = 2.5, playbackStartTime = 0.5)
        val json = js("JSON").stringify(encode_ScheduledVoice(sv)).unsafeCast<String>()

        json shouldBe GOLDEN_WIRE_JSON
    }

    "the wire schema hash is pinned: a change of the wire's type signatures moves it, and a move is made here on purpose" {
        // The worklet refuses a message whose hash differs from its own (`WorkletContract`), so a moved hash only
        // refuses a stale worklet. Pinned since engine follow-up 2, step 5, which changed the slot maps' Kotlin type
        // and kept their signature. A deliberate wire change updates this number in the same change: step 3 of
        // KLANG-00302 (katalyst knob expressions) moved it from 1330155241 (the Katalyst knobs left
        // `IgnitorDsl` for `KatalystKnobDsl`), step 4 from 1168938423 (the knob expressions joined it), step 5a from
        // 615137850 (the modulators joined it).
        WIRE_SCHEMA_HASH shouldBe 211191718
    }

    "ScheduledVoice round-trips a minimal (mostly-null) VoiceData" {
        val sv = ScheduledVoice(
            playbackId = "pb-2",
            data = VoiceData.empty.copy(note = "a4", freqHz = 440.0),
            startTime = 0.0, gateEndTime = 1.0, playbackStartTime = 0.0,
        )
        decode_ScheduledVoice(encode_ScheduledVoice(sv)) shouldBe sv
    }

    "Feedback round-trips (sealed dispatch + nested list)" {
        val cases = listOf<KlangCommLink.Feedback>(
            KlangCommLink.Feedback.BackendReady(),
            KlangCommLink.Feedback.RequestSample("pb", SampleRequest(bank = "b", sound = "s", index = 1, note = "c3")),
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
        val voice = ScheduledVoice(playbackId = "pb", data = VoiceData.empty.copy(note = "c3"), startTime = 0.0, gateEndTime = 1.0, playbackStartTime = 0.0)
        val dsl = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine(freq = IgnitorDsl.Freq),
                IgnitorDsl.Adsr(inner = IgnitorDsl.SuperSaw(), attack = IgnitorDsl.Constant(0.02), attackCurve = AdsrCurves.knob(AdsrCurve.Linear)),
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
            KlangCommLink.Cmd.RegisterIgnitor(playbackId = "pb", name = "mysynth", dsl = dsl),
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "pb", name = "katalyst-0",
                dsl = KatalystDsl.of(KatalystStageDsl.Gain(KatalystKnobDsl.Constant(1.4)), KatalystStageDsl.Reverb()),
            ),
            KlangCommLink.Cmd.RegisterKatalyst(playbackId = "pb", name = "katalyst-1", dsl = KatalystDsl.classic),
            KlangCommLink.Cmd.Sample.NotFound(SampleRequest(bank = "b", sound = "s", index = 1, note = "c3")),
        )
        cases.forEach { decode_KlangCommLink_Cmd(encode_KlangCommLink_Cmd(it)) shouldBe it }
    }

    "ScheduledVoice round-trips the katalyst reference" {
        // The orbit chain rides the same field family as the master: if the codec dropped it,
        // every declared chain would silently stay unregistered on the far side.
        val control = ScheduledVoice(
            playbackId = "pb", data = VoiceData.empty.copy(katalyst = "katalyst-3", control = true), startTime = 0.0, gateEndTime = 1.0, playbackStartTime = 0.0,
        )
        val sounding = ScheduledVoice(
            playbackId = "pb", data = VoiceData.empty.copy(note = "c3", sound = "sine", katalyst = "katalyst-3"), startTime = 0.0, gateEndTime = 1.0, playbackStartTime = 0.0,
        )

        listOf(control, sounding).forEach {
            decode_ScheduledVoice(encode_ScheduledVoice(it)) shouldBe it
        }
    }

    "ScheduledVoice round-trips the master reference + control flag" {
        // If `control` were dropped by the codec, every master(...) carrier would decode as an
        // audible default-oscillator voice — once per cycle, forever. Guard both directions.
        val control = ScheduledVoice(
            playbackId = "pb", data = VoiceData.empty.copy(master = "katalyst-7", control = true), startTime = 0.0, gateEndTime = 1.0, playbackStartTime = 0.0,
        )
        val sounding = ScheduledVoice(
            playbackId = "pb", data = VoiceData.empty.copy(note = "c3", sound = "sine", master = "katalyst-7"), startTime = 0.0, gateEndTime = 1.0, playbackStartTime = 0.0,
        )

        listOf(control, sounding).forEach {
            decode_ScheduledVoice(encode_ScheduledVoice(it)) shouldBe it
        }
    }

    "Cmd.Sample.Chunk round-trips its DoubleArray (compared by content)" {
        val chunk = KlangCommLink.Cmd.Sample.Chunk(
            req = SampleRequest(bank = "b", sound = "s", index = null, note = "c3"), note = "c3", pitchHz = 261.6, sampleRate = 48000,
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

    "a sample's meta envelope round-trips inside Cmd.Sample.Chunk (AdsrDef's one wire host since VoiceData.adsr left)" {
        val meta = SampleMetadata(
            anchor = 0.01,
            loop = SampleMetadata.LoopRange(startSec = 0.1, endSec = 0.4),
            adsr = AdsrDef.Std(
                attack = 0.005, decay = 0.2, sustain = 0.6, release = 0.05,
                attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Square, releaseCurve = AdsrCurve.Cube,
            ),
        )
        val chunk = KlangCommLink.Cmd.Sample.Chunk(
            req = SampleRequest(bank = "b", sound = "s", index = null, note = "c3"), note = "c3", pitchHz = 261.6, sampleRate = 48000,
            meta = meta, totalSize = 1, isLastChunk = true, chunkOffset = 0,
            data = doubleArrayOf(0.5),
        )
        val dec = decode_KlangCommLink_Cmd(encode_KlangCommLink_Cmd(chunk)) as KlangCommLink.Cmd.Sample.Chunk

        dec.meta shouldBe meta
    }
})

/** JSON.stringify of the golden voice above, encoded at `ccde9f9e` (engine follow-up 2, step 5). */
private const val GOLDEN_WIRE_JSON: String =
    "{\"playbackId\":\"pb-1\",\"data\":{\"note\":\"c3\",\"freqHz\":130.81,\"gain\":0.7,\"legato\":null," +
        "\"bank\":null,\"sound\":null,\"soundIndex\":2,\"ignitorParams\":{\"voices\":7,\"spread\":0.3," +
        "\"lpf.freq\":1000,\"adsr.on\":0},\"katalystParams\":{\"reverb.size\":6,\"delay.wet\":null," +
        "\"body.material\":2,\"room\":0.5},\"cylinder\":null,\"pan\":null,\"cut\":null,\"solo\":null," +
        "\"sourceId\":null,\"master\":null,\"katalyst\":null,\"control\":false,\"tags\":null,\"cull\":0.2}," +
        "\"startTime\":1.25,\"gateEndTime\":2.5,\"playbackStartTime\":0.5}"
