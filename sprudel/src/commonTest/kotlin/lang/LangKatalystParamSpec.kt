/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.putKatalystParam
import io.peekandpoke.klang.sprudel.putOscParam

/**
 * The orbit chain's param state on the pattern side (Katalyst step 5a): the `.katp` door, the bus
 * doors writing the same slots as aliases, and the merge and wire behaviour of the map that carries
 * them.
 *
 * What the ENGINE then does with the map is `CylinderKatalystParamsSpec`'s subject; here the only
 * question is what a pattern writes.
 */
class LangKatalystParamSpec : StringSpec({

    fun slots(p: SprudelPattern, cycle: Int = 0): List<Map<String, Double>?> =
        p.queryArc(cycle.toDouble(), cycle + 1.0).map { it.data.katalystParams }

    fun slot(p: SprudelPattern, key: String, cycle: Int = 0): Double? =
        slots(p, cycle).firstOrNull()?.get(key)

    // ── The raw door ─────────────────────────────────────────────────────────────────────────────

    "katp dsl interface" {
        val pat = "0 1"
        val ctrl = "3 6"

        dslInterfaceTests(
            "pattern.katp(key, ctrl)" to
                    seq(pat).katp("reverb.size", ctrl),
            "script pattern.katp(key, ctrl)" to
                    SprudelPattern.compile("""seq("$pat").katp("reverb.size", "$ctrl")"""),
            "string.katp(key, ctrl)" to
                    pat.katp("reverb.size", ctrl),
            "script string.katp(key, ctrl)" to
                    SprudelPattern.compile(""""$pat".katp("reverb.size", "$ctrl")"""),
            "katp(key, ctrl)" to
                    seq(pat).apply(katp("reverb.size", ctrl)),
            "script katp(key, ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(katp("reverb.size", "$ctrl"))"""),
            "chained katp(key, ctrl)" to
                    seq(pat).apply(gain(0.5).katp("reverb.size", ctrl)),
            "script chained katp(key, ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(gain(0.5).katp("reverb.size", "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.katalystParams?.get("reverb.size") shouldBe 3.0
            events[1].data.katalystParams?.get("reverb.size") shouldBe 6.0
        }
    }

    "katp writes one slot, and a REST in its control pattern leaves the entry untouched" {
        val p = note("c3").katp("reverb.size", "<3 ~ 6>")

        withClue("cycle 0 writes 3") { slot(p, "reverb.size", cycle = 0) shouldBe 3.0 }
        // The 2026-09-16 rule: a rest writes nothing, so nothing lands in the map at all. A
        // `.katp` on a rest cycle must not be read as "0" and must not clear an earlier write.
        withClue("cycle 1 rests") { slots(p, cycle = 1).first().shouldBeNull() }
        withClue("cycle 2 writes 6") { slot(p, "reverb.size", cycle = 2) shouldBe 6.0 }
    }

    "katp: an earlier value survives the rest cycle of a later call" {
        val p = note("c3").katp("reverb.size", 4).katp("reverb.size", "<9 ~>")

        slot(p, "reverb.size", cycle = 0) shouldBe 9.0
        slot(p, "reverb.size", cycle = 1) shouldBe 4.0
    }

    "katp: the two namespaces never cross" {
        val p = note("c3").katp("room", 5).oscp("room", 7)

        slot(p, "room") shouldBe 5.0
        p.queryArc(0.0, 1.0).first().data.oscParams?.get("room") shouldBe 7.0
    }

    // ── The bus doors as aliases ─────────────────────────────────────────────────────────────────

    "reverb(...) writes its slots, and the call fills the companions it left unset" {
        val full = note("c3").reverb(wet = 0.5, size = 6, lowpass = 1500)

        slot(full, "reverb.wet") shouldBe 0.5
        slot(full, "reverb.size") shouldBe 6.0
        slot(full, "reverb.lowpass") shouldBe 1500.0

        // A tail-only call fills the wet it did not name, exactly as it fills the voice field.
        val tail = note("c3").reverb(size = 4)

        slot(tail, "reverb.size") shouldBe 4.0
        slot(tail, "reverb.wet") shouldBe REVERB_WET
        withClue("lowpass has no default, so it stays unset") {
            slots(tail).first()?.containsKey("reverb.lowpass") shouldBe false
        }

        // ...and the other way round: a wet-only call fills the size, which is what makes a
        // declared chain's room audible at all (the engine gates the reverb on its SIZE).
        val wetOnly = note("c3").reverb(0.3)

        slot(wetOnly, "reverb.wet") shouldBe 0.3
        slot(wetOnly, "reverb.size") shouldBe REVERB_SIZE
    }

    "delay(...) writes its four slots with the same fill" {
        val tail = note("c3").delay(time = 0.5)

        slot(tail, "delay.time") shouldBe 0.5
        slot(tail, "delay.wet") shouldBe DELAY_WET
        slot(tail, "delay.feedback") shouldBe DELAY_FEEDBACK
        slot(tail, "delay.cap") shouldBe DELAY_CAP

        slot(note("c3").delay(0.4), "delay.time") shouldBe DELAY_TIME_SECONDS
    }

    "compressor(...) writes ONLY the slots it was given: no fill, the recorded asymmetry" {
        val p = note("c3").compressor(ratio = 8)

        slot(p, "compressor.ratio") shouldBe 8.0
        slots(p).first()?.keys shouldBe setOf("compressor.ratio")

        val two = note("c3").compressor(threshold = -12, release = 0.2)

        two.queryArc(0.0, 1.0).first().data.katalystParams?.keys shouldBe
                setOf("compressor.threshold", "compressor.release")
    }

    "duck(...) writes three slots, with attack at its constant, and never invents an orbit" {
        val p = note("c3").duck(orbit = 2, depth = 0.5)

        slot(p, "duck.orbit") shouldBe 2.0
        slot(p, "duck.depth") shouldBe 0.5
        slot(p, "duck.attack") shouldBe DUCK_ATTACK_SECONDS

        // A tail-only call names no source, so the orbit slot stays absent and the stage stays off.
        val tail = note("c3").duck(depth = 0.5)

        tail.queryArc(0.0, 1.0).first().data.katalystParams?.keys shouldBe
                setOf("duck.depth", "duck.attack")

        // An orbit-only call is the mirror: DUCK_DEPTH is 0, so a filled depth is still "no duck".
        slot(note("c3").duck(1), "duck.depth") shouldBe DUCK_DEPTH
    }

    "phaser(...) writes the five slots it was given, and nothing it was not" {
        val p = note("c3").phaser(rate = 0.5, wet = 0.4, center = 1800, sweep = 900, floor = 0.2)

        slot(p, "phaser.rate") shouldBe 0.5
        slot(p, "phaser.wet") shouldBe 0.4
        slot(p, "phaser.center") shouldBe 1800.0
        slot(p, "phaser.sweep") shouldBe 900.0
        slot(p, "phaser.floor") shouldBe 0.2

        note("c3").phaser(wet = 0.4).queryArc(0.0, 1.0).first().data.katalystParams?.keys shouldBe
                setOf("phaser.wet")
    }

    "body(...) and vowel(...) write the NUMBERS only: a material is not a slot" {
        val b = note("c3").body(material = "wood", wet = 0.3)

        b.queryArc(0.0, 1.0).first().data.katalystParams?.keys shouldBe setOf("body.wet")
        slot(b, "body.wet") shouldBe 0.3
        withClue("the material still reaches the voice field") {
            b.queryArc(0.0, 1.0).first().data.body shouldBe "wood"
        }

        val v = note("c3").vowel(vowel = "a", wet = 0.6, floor = 0.1)

        v.queryArc(0.0, 1.0).first().data.katalystParams?.keys shouldBe setOf("vowel.wet", "vowel.floor")
        slot(v, "vowel.wet") shouldBe 0.6
        slot(v, "vowel.floor") shouldBe 0.1
    }

    "a non-numeric control leaves the voice field AND its slot untouched, together" {
        // The two sources must never disagree on one event. The numeric helper skips a value that
        // is not a number (the 2026-09-16 rule), so neither half moves; the `?: SLOT_UNSET` arm in
        // the setters is what keeps that true on the one path that CAN hand a setter null (the
        // bare-call reinterpret branch of `_liftOrReinterpretNumericalField`).
        val p = note("c3 e3").phaser(wet = 0.4).phaser(wet = "0.7 x").body(wet = 0.3).body(wet = "0.6 x")
        val events = p.queryArc(0.0, 1.0)

        events[0].data.phaserDepth shouldBe 0.7
        events[0].data.katalystParams?.get("phaser.wet") shouldBe 0.7
        events[0].data.bodyMix shouldBe 0.6
        events[0].data.katalystParams?.get("body.wet") shouldBe 0.6

        withClue("the second event's control is not a number: neither the field nor the slot moves") {
            events[1].data.phaserDepth shouldBe 0.4
            events[1].data.katalystParams?.get("phaser.wet") shouldBe 0.4
            events[1].data.bodyMix shouldBe 0.3
            events[1].data.katalystParams?.get("body.wet") shouldBe 0.3
        }
    }

    // ── The map itself ───────────────────────────────────────────────────────────────────────────

    "a slot write goes into the event's OWN map: at most the first one allocates" {
        val vd = createSprudelVoiceData()

        vd.putKatalystParam("reverb.wet", 0.5)
        val first = vd.katalystParams.shouldNotBeNull()

        vd.putKatalystParam("reverb.size", 6.0)
        vd.putKatalystParam("room", 2.0)

        vd.katalystParams shouldBeSameInstanceAs first
        first shouldBe mutableMapOf("reverb.wet" to 0.5, "reverb.size" to 6.0, "room" to 2.0)
    }

    "a bus door call on an already-cloned voice allocates no map" {
        // The probe seeds the map and remembers the instance; every door below then writes into
        // THAT map. A door that builds a fresh map per call (the pre-2026-09-18 shape) hands the
        // event a different object, which is the "twenty allocations per note" class on the query
        // path: after step 5b every bus door writes here.
        //
        // Numeric slots only, deliberately: a door that sets a NAME (`body("wood")`) runs the string
        // setter, which CLONES the whole voice data and therefore its map too. That is the
        // pre-existing shape of `_liftOrReinterpretStringField`, not a map this change allocates.
        var seeded: Map<String, Double>? = null

        val events = note("c3")
            .reinterpretVoice { data ->
                data.also {
                    it.putKatalystParam("seed", 1.0)
                    seeded = it.katalystParams
                }
            }
            .reverb(wet = 0.5, size = 6, lowpass = 1500)
            .delay(0.2, 0.25)
            .compressor(ratio = 8, knee = 3)
            .duck(orbit = 2, depth = 0.5)
            .phaser(wet = 0.3)
            .body(wet = 0.4)
            .queryArc(0.0, 1.0)

        val data = events.first().data

        data.katalystParams shouldBeSameInstanceAs seeded.shouldNotBeNull()
        // ...and it really did fill up, so the row is not passing on an empty map.
        data.katalystParams.shouldNotBeNull().size shouldBe 15
    }

    "merge is last-writer-wins per key, like oscParams" {
        val base = createSprudelVoiceData { katalystParams = mutableMapOf("reverb.size" to 3.0, "room" to 1.0) }
        val other = createSprudelVoiceData { katalystParams = mutableMapOf("room" to 9.0, "delay.time" to 0.5) }

        base.merge(other).katalystParams shouldBe
                mapOf("reverb.size" to 3.0, "room" to 9.0, "delay.time" to 0.5)

        // The in-place twin must not drift from the copying one.
        base.mergeFrom(other)
        base.katalystParams shouldBe mapOf("reverb.size" to 3.0, "room" to 9.0, "delay.time" to 0.5)
    }

    "toVoiceData hands the wire a COPY of both maps, not the event's own" {
        val data = note("c3").katp("room", 2).oscp("analog", 4).queryArc(0.0, 1.0).first().data
        val wire = data.toVoiceData()

        wire.katalystParams shouldNotBeSameInstanceAs data.katalystParams
        wire.oscParams shouldNotBeSameInstanceAs data.oscParams

        // Why it has to be a copy: the backend keeps `Voice.katalystParams` for the whole life of
        // the voice and its chain gates the re-resolve on the map's IDENTITY, so a write sprudel
        // made afterwards would move an orbit's settings with nothing to notice it.
        data.putKatalystParam("room", 9.0)
        data.putOscParam("analog", 9.0)

        wire.katalystParams shouldBe mapOf("room" to 2.0)
        wire.oscParams shouldBe mapOf("analog" to 4.0)
    }

    "toVoiceData carries the map to the wire" {
        val event = note("c3").reverb(wet = 0.5, size = 6).katp("room", 2).queryArc(0.0, 1.0).first()

        event.data.toVoiceData().katalystParams shouldBe
                mapOf("reverb.wet" to 0.5, "reverb.size" to 6.0, "room" to 2.0)

        // A pattern that writes no slot sends no map at all.
        note("c3").queryArc(0.0, 1.0).first().data.toVoiceData().katalystParams.shouldBeNull()
    }
})
