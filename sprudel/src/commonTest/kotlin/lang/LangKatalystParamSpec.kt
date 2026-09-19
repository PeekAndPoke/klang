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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
import io.peekandpoke.klang.sprudel.ParamBag
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.paramBagOf
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
        p.queryArc(cycle.toDouble(), cycle + 1.0).map { it.data.katalystParams?.toMap() }

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

    "compressor(...) fills the other four, whichever of the five the call named" {
        // The compressor has no NAME KNOB, so ANY of the five names it (Katalyst step 5a-3). The
        // constants are the ones `Voice.Compressor.fromParams` already substituted for an unset
        // field, which is why moving the fill to the door changed no sound.
        val p = note("c3").compressor(ratio = 8)

        slot(p, "compressor.ratio") shouldBe 8.0
        slot(p, "compressor.threshold") shouldBe COMPRESSOR_THRESHOLD_DB
        slot(p, "compressor.knee") shouldBe COMPRESSOR_KNEE_DB
        slot(p, "compressor.attack") shouldBe COMPRESSOR_ATTACK_SECONDS
        slot(p, "compressor.release") shouldBe COMPRESSOR_RELEASE_SECONDS

        val two = note("c3").compressor(threshold = -12, release = 0.2)

        slot(two, "compressor.threshold") shouldBe -12.0
        slot(two, "compressor.release") shouldBe 0.2
        slot(two, "compressor.ratio") shouldBe COMPRESSOR_RATIO
    }

    "compressor(...) never overwrites a value an earlier call set" {
        val p = note("c3").compressor(threshold = -30).compressor(ratio = 8)

        withClue("the explicit threshold survives the second call's fill") {
            slot(p, "compressor.threshold") shouldBe -30.0
        }

        slot(p, "compressor.ratio") shouldBe 8.0

        // ...and the other order ends on the author's value, because a named knob is a `set`.
        slot(note("c3").compressor(ratio = 8).compressor(threshold = -30), "compressor.threshold") shouldBe -30.0
    }

    "duck(...) fills its companions only when the call NAMES an orbit" {
        // The orbit is this stage's name knob, so only naming it fills (`/dsl-design` §4). Until
        // round 3 of step 5a-3 every duck knob filled, and a tail-only call stamped DUCK_DEPTH (0)
        // over a chain-authored depth, which silenced the ducking on a declared chain.
        val p = note("c3").duck(orbit = 2, depth = 0.5)

        slot(p, "duck.orbit") shouldBe 2.0
        slot(p, "duck.depth") shouldBe 0.5
        slot(p, "duck.attack") shouldBe DUCK_ATTACK_SECONDS

        // An orbit-only call fills both companions: DUCK_DEPTH is 0, so it is still "no duck" until
        // a depth is asked for, but the two slots are written and right.
        val orbitOnly = note("c3").duck(1)

        orbitOnly.queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe
                setOf("duck.orbit", "duck.depth", "duck.attack")
        slot(orbitOnly, "duck.depth") shouldBe DUCK_DEPTH

        // A tail-only call names nothing: exactly its own slot, no orbit and no companion.
        note("c3").duck(depth = 0.5).queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe
                setOf("duck.depth")

        note("c3").duck(attack = 0.3).queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe
                setOf("duck.attack")
    }

    "a bare duck() that names nothing writes nothing, slot included" {
        // The bare form reinterprets the pattern's own values as the orbit, so on a pattern whose
        // values are not numbers it names nothing. It must then leave the slot alone: re-writing
        // the value of the last naming call would stamp 2 over the 5 the katp asked for (checklist
        // 12). Until step 5b-3 that stale value sat in the voice FIELD, and a row here pinned that
        // the field kept it; the field is gone.
        val p = note("c3").duck(2).katp("duck.orbit", 5).duck()

        slot(p, "duck.orbit") shouldBe 5.0

        // ...and on its own it writes no slot at all, so the event carries no bag: naming nothing
        // must not even allocate one, let alone fill the companions.
        note("c3").duck().queryArc(0.0, 1.0).first().data.katalystParams.shouldBeNull()
    }

    "duck(...): a non-numeric control leaves the slot where it was" {
        // Until step 5b-3 there were two sources, the field and the slot, and they had to agree; the
        // slot is the only one left. The mechanism is the lift,
        // not a clear arm in the setter: `_liftNumericField` RETURNS EARLY on a control value that
        // is not a number and `_mapNumericField` skips a null mapping (the 2026-09-16 rule), so
        // `duck(depth = "x")` never reaches the setter at all and the earlier 0.5 stands. That holds for every numeric TAIL setter of every compound BUS door, which is why
        // none of them carries a clear arm; a HEAD setter can be handed a null by the bare-call
        // reinterpret, and the two NAME setters are the only ones that CLEAR on it.
        val p = note("c3").duck(orbit = 1, depth = 0.5).duck(depth = "x")

        slot(p, "duck.depth") shouldBe 0.5
        slot(p, "duck.orbit") shouldBe 1.0
    }

    "phaser(...) writes the five slots, filling the ones the call did not name" {
        val p = note("c3").phaser(rate = 0.5, wet = 0.4, center = 1800, sweep = 900, floor = 0.2)

        slot(p, "phaser.rate") shouldBe 0.5
        slot(p, "phaser.wet") shouldBe 0.4
        slot(p, "phaser.center") shouldBe 1800.0
        slot(p, "phaser.sweep") shouldBe 900.0
        slot(p, "phaser.floor") shouldBe 0.2

        // The phaser has no name knob either, so any of its five names the stage (Katalyst step
        // 5a-3). PHASER_WET is 0.0, so the fill leaves it inaudible: the engine gates the sweep on
        // the depth, which is why reaching for a rate alone still makes no sound.
        val rateOnly = note("c3").phaser(rate = 2)

        slot(rateOnly, "phaser.rate") shouldBe 2.0
        slot(rateOnly, "phaser.wet") shouldBe PHASER_WET
        slot(rateOnly, "phaser.center") shouldBe PHASER_CENTER_HZ
        slot(rateOnly, "phaser.sweep") shouldBe PHASER_SWEEP_HZ
        slot(rateOnly, "phaser.floor") shouldBe PHASER_FLOOR

        withClue("the voice fields take the same five, which is what VoiceFactory substituted") {
            val data = rateOnly.queryArc(0.0, 1.0).first().data

            data.phaserDepth shouldBe PHASER_WET
            data.phaserCenter shouldBe PHASER_CENTER_HZ
            data.phaserSweep shouldBe PHASER_SWEEP_HZ
            data.phaserFloor shouldBe PHASER_FLOOR
        }

        note("c3").phaser(wet = 0.4).queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe
                setOf("phaser.rate", "phaser.wet", "phaser.center", "phaser.sweep", "phaser.floor")
    }

    "body(...) writes the material as an INDEX slot, next to its numbers" {
        // Katalyst step 5a-2: a material travels as the index of its name in `BodyMaterials.names`,
        // so `body("wood", wet = 0.3)` reaches a DECLARED chain the same way `reverb(...)` does.
        // The expected index is read off the catalogue, never typed here.
        val b = note("c3").body(material = "wood", wet = 0.3)

        b.queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe
                setOf("body.material", "body.wet", "body.floor")
        slot(b, "body.material") shouldBe BodyMaterials.indexOf("wood")
        slot(b, "body.wet") shouldBe 0.3
        withClue("the material still reaches the voice field, which the wire carries until step 5b-3") {
            b.queryArc(0.0, 1.0).first().data.body shouldBe "wood"
        }

        // ...and it is really the WOOD index, not just some number: a different material is a
        // different slot value, and `none` is 0.
        slot(b, "body.material") shouldNotBe slot(note("c3").body("glass"), "body.material")
        slot(note("c3").body("none"), "body.material") shouldBe 0.0

        // An unknown material is index 0 as well, which is the stage off, the same answer the
        // voice path gives a name it cannot resolve.
        slot(note("c3").body("unobtainium"), "body.material") shouldBe 0.0
    }

    "body(...) does not INVENT a material: a tail-only call writes no index and no companion" {
        // The NAME KNOB of this stage is the MATERIAL, and a door never invents one. A call that only
        // asked for a wet writes the wet: an index here would switch a body on for every such
        // pattern, and a filled floor would claim the stage was named.
        val tail = note("c3").body(wet = 0.3)

        tail.queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe setOf("body.wet")

        note("c3").body(floor = 0.2).queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe
                setOf("body.floor")
    }

    "body(material) alone fills wet and floor from the shared constants" {
        // Katalyst step 5a-3, the compound-door fill rule. Byte-identical to what the engine did
        // with an unset field, on both the voice path and the declared path, which is what
        // `KatalystDoorFillRenderSpec` renders.
        val p = note("c3").body("wood")
        val data = p.queryArc(0.0, 1.0).first().data

        slot(p, "body.material") shouldBe BodyMaterials.indexOf("wood")
        slot(p, "body.wet") shouldBe BODY_WET
        slot(p, "body.floor") shouldBe BODY_FLOOR

        withClue("the voice fields are filled with the same two numbers, so they cannot drift") {
            data.bodyMix shouldBe BODY_WET
            data.bodyFloor shouldBe BODY_FLOOR
        }
    }

    "body(...): an explicit wet survives a later material, and a later wet wins" {
        val wetFirst = note("c3").body(wet = 0.3).body("wood")

        withClue("naming the material must not overwrite the 0.3 the author already asked for") {
            slot(wetFirst, "body.wet") shouldBe 0.3
            wetFirst.queryArc(0.0, 1.0).first().data.bodyMix shouldBe 0.3
        }

        withClue("the floor was never named either way, so the material's call fills it") {
            slot(wetFirst, "body.floor") shouldBe BODY_FLOOR
        }

        val wetLast = note("c3").body("wood").body(wet = 0.3)

        slot(wetLast, "body.wet") shouldBe 0.3
        wetLast.queryArc(0.0, 1.0).first().data.bodyMix shouldBe 0.3
    }

    "a fill never overwrites a slot a raw katp already wrote" {
        // The never-overwrite half of the rule where only the SLOT can carry the memory: `katp`
        // writes no voice field, so the field is still null when the naming call fills. Absence in
        // the bag is the only thing that can tell "nobody said" from "the author said 0.7", which
        // is why `ParamBag.setOrDefault` tests for the name and not for a sentinel value.
        val body = note("c3").katp("body.wet", 0.7).body("wood")

        slot(body, "body.wet") shouldBe 0.7
        withClue("the companion nobody named still takes its constant") {
            slot(body, "body.floor") shouldBe BODY_FLOOR
        }

        val comp = note("c3").katp("compressor.threshold", -30).compressor(ratio = 8)

        slot(comp, "compressor.threshold") shouldBe -30.0
        slot(comp, "compressor.ratio") shouldBe 8.0
        slot(comp, "compressor.knee") shouldBe COMPRESSOR_KNEE_DB

        val room = note("c3").katp("reverb.wet", 0.7).reverb(size = 4)

        slot(room, "reverb.wet") shouldBe 0.7
        slot(room, "reverb.size") shouldBe 4.0

        val ducked = note("c3").katp("duck.depth", 0.7).duck(1)

        slot(ducked, "duck.depth") shouldBe 0.7
        slot(ducked, "duck.orbit") shouldBe 1.0
    }

    "a bare body() or vowel() with nothing to name CLEARS the name, field and slot together" {
        // The two NAME setters are the only ones that CLEAR on a null (the string lift's bare-call
        // reinterpret), and the only two that carry a clear arm. `note("c3")` has no value to
        // reinterpret, so the name goes away and `SLOT_UNSET` goes into the slot: the wire's
        // "never set", which turns the stage off on a declared chain exactly as a null field turns
        // it off on the voice path.
        val b = note("c3").body("wood").body()
        val bodyData = b.queryArc(0.0, 1.0).first().data

        bodyData.body.shouldBeNull()
        withClue("the slot is cleared with the field, never left at the stale index") {
            slot(b, "body.material").shouldNotBeNull().isFinite() shouldBe false
        }

        val v = note("c3").vowel("a").vowel()
        val vowelData = v.queryArc(0.0, 1.0).first().data

        vowelData.vowel.shouldBeNull()
        slot(v, "vowel.vowel").shouldNotBeNull().isFinite() shouldBe false
    }

    "a fill never stamps a constant over a katp written BETWEEN two calls of the same door" {
        // Round 1 of step 5a-3 found this: while the fill read the voice FIELD as its value, the
        // field held the constant the first call's own fill had written, so the second call handed
        // the bag a "value" and overwrote the author's katp. The fill passes null now (checklist
        // 12), and only the bag's absence test decides.
        val comp = note("c3").compressor(ratio = 8).katp("compressor.threshold", -40).compressor(knee = 3)

        slot(comp, "compressor.threshold") shouldBe -40.0
        slot(comp, "compressor.ratio") shouldBe 8.0
        slot(comp, "compressor.knee") shouldBe 3.0

        val room = note("c3").reverb(wet = 0.3).katp("reverb.wet", 0.7).reverb(size = 4)

        slot(room, "reverb.wet") shouldBe 0.7
        slot(room, "reverb.size") shouldBe 4.0

        val boxed = note("c3").body("wood").katp("body.wet", 0.8).body("glass")

        slot(boxed, "body.wet") shouldBe 0.8
        slot(boxed, "body.material") shouldBe BodyMaterials.indexOf("glass")
    }

    "vowel(name) alone fills wet and floor, the body's twin" {
        val p = note("c3").vowel("a")
        val data = p.queryArc(0.0, 1.0).first().data

        slot(p, "vowel.vowel") shouldBe VowelBands.indexOf("a")
        slot(p, "vowel.wet") shouldBe VOWEL_WET
        slot(p, "vowel.floor") shouldBe VOWEL_FLOOR

        data.vowelMix shouldBe VOWEL_WET
        data.vowelFloor shouldBe VOWEL_FLOOR

        // And the never-overwrite half, on the wet.
        slot(note("c3").vowel(wet = 0.6).vowel("a"), "vowel.wet") shouldBe 0.6
    }

    "vowel(...) writes the vowel as an INDEX slot, register and all" {
        val v = note("c3").vowel(vowel = "a", wet = 0.6, floor = 0.1)

        v.queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe
                setOf("vowel.vowel", "vowel.wet", "vowel.floor")
        slot(v, "vowel.vowel") shouldBe VowelBands.indexOf("a")
        slot(v, "vowel.wet") shouldBe 0.6
        slot(v, "vowel.floor") shouldBe 0.1

        // A register-qualified name is its own index, and a bare name is the soprano one, exactly
        // as the voice path reads it.
        slot(note("c3").vowel("bass:a"), "vowel.vowel") shouldBe VowelBands.indexOf("bass:a")
        slot(note("c3").vowel("a"), "vowel.vowel") shouldBe VowelBands.indexOf("soprano:a")
        VowelBands.indexOf("bass:a") shouldNotBe VowelBands.indexOf("soprano:a")

        slot(note("c3").vowel("none"), "vowel.vowel") shouldBe 0.0
        slot(note("c3").vowel("zzz"), "vowel.vowel") shouldBe 0.0

        // Tail-only, the twin of the body's row: no name, no index, no companion.
        note("c3").vowel(wet = 0.5).queryArc(0.0, 1.0).first().data.katalystParams?.toMap()?.keys shouldBe
                setOf("vowel.wet")
    }

    "a patterned material writes the index of the name at each event" {
        // `body("wood glass")` is mini-notation, so the two halves of the cycle are two materials,
        // and the slot has to follow the name event for event.
        val events = note("c3 e3").body("wood glass", 0.7).queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.katalystParams?.get("body.material") shouldBe BodyMaterials.indexOf("wood")
        events[1].data.katalystParams?.get("body.material") shouldBe BodyMaterials.indexOf("glass")
    }

    "a non-numeric control leaves the voice field AND its slot untouched, together" {
        // The two sources must never disagree on one event, and the numeric lift is what keeps that
        // true: `_liftNumericField` returns early on a value that is not a number (the 2026-09-16
        // rule), so the setter never runs and neither half moves. No numeric TAIL setter of a
        // compound BUS door can be handed a null at all, so none of them carries a clear arm.
        // A HEAD setter can be, through the bare-call reinterpret, and the two NAME setters
        // (`body`'s material, `vowel`'s vowel) are the only ones that CLEAR on it.
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

    // ── The accessors read the slots (Katalyst step 5b-3) ───────────────────────────────────────

    "a bus accessor reads the slot, so a value a katp wrote is what it reads, in both doors" {
        // Until step 5b-3 the `delay.*` / `reverb.*` / `compressor.*` / `duck.*` accessors read the
        // voice FIELDS, which a raw `katp` never wrote, so these reads came back empty. The fields
        // are gone and the slot is the knob's only storage.
        val cases = listOf(
            "delay.wet" to Pair(note("c3").katp("delay.wet", 0.5).gain(delay.wet), """note("c3").katp("delay.wet", 0.5).gain(delay.wet)"""),
            "reverb.size" to Pair(note("c3").katp("reverb.size", 0.7).gain(reverb.size), """note("c3").katp("reverb.size", 0.7).gain(reverb.size)"""),
            "compressor.ratio" to Pair(note("c3").katp("compressor.ratio", 0.3).gain(compressor.ratio), """note("c3").katp("compressor.ratio", 0.3).gain(compressor.ratio)"""),
            "duck.depth" to Pair(note("c3").katp("duck.depth", 0.6).gain(duck.depth), """note("c3").katp("duck.depth", 0.6).gain(duck.depth)"""),
        )

        for ((name, doors) in cases) {
            val (kotlin, script) = doors
            val expected = slot(kotlin, name).shouldNotBeNull()

            withClue("$name, Kotlin door") { kotlin.queryArc(0.0, 1.0).first().data.gain shouldBe expected }
            withClue("$name, script door") {
                SprudelPattern.compile(script).shouldNotBeNull().queryArc(0.0, 1.0).first().data.gain shouldBe expected
            }
        }
    }

    "the duck accessors and mappers see the companions its fill wrote" {
        // `duck(1)` fills `duck.depth` and `duck.attack` into the SLOTS; until step 5b-3 it wrote no
        // voice field for them, so `duck.attack` read nothing and a mapper on an unnamed attack
        // wrote nothing. Now both see what the orbit runs.
        note("c3").duck(1).gain(duck.attack).queryArc(0.0, 1.0).first().data.gain shouldBe DUCK_ATTACK_SECONDS

        val mapped = note("c3").duck(1, 0.8).duck(attack = mul(4))

        slot(mapped, "duck.attack") shouldBe DUCK_ATTACK_SECONDS * 4
        slot(mapped, "duck.depth") shouldBe 0.8
    }

    "a bare reverb() whose values are not numbers leaves the wet slot readable, in both doors" {
        // The bare call reinterprets the pattern's own values as the wet; on a non-number it names
        // nothing. Until step 5b-3 that path CLEARED the voice field (so `reverb.wet` read nothing
        // afterwards) while leaving the slot; now the slot is the only storage and it stays.
        val kotlin = note("c").reverb(0.5).reverb().gain(reverb.wet)
        val script = SprudelPattern.compile("""note("c").reverb(0.5).reverb().gain(reverb.wet)""").shouldNotBeNull()

        for ((door, p) in listOf("Kotlin" to kotlin, "script" to script)) {
            withClue(door) {
                slot(p, "reverb.wet") shouldBe 0.5
                p.queryArc(0.0, 1.0).first().data.gain shouldBe 0.5
            }
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
        first.toMap() shouldBe mapOf("reverb.wet" to 0.5, "reverb.size" to 6.0, "room" to 2.0)
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
        var seeded: ParamBag? = null

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
        withClue("the row is about the bag's identity, so it has to prove the doors wrote into it") {
            data.katalystParams.shouldNotBeNull()["reverb.size"] shouldBe 6.0
        }
    }

    "merge is last-writer-wins per key, like oscParams" {
        val base = createSprudelVoiceData { katalystParams = paramBagOf("reverb.size" to 3.0, "room" to 1.0) }
        val other = createSprudelVoiceData { katalystParams = paramBagOf("room" to 9.0, "delay.time" to 0.5) }

        base.merge(other).katalystParams?.toMap() shouldBe
                mapOf("reverb.size" to 3.0, "room" to 9.0, "delay.time" to 0.5)

        // The in-place twin must not drift from the copying one.
        base.mergeFrom(other)
        base.katalystParams?.toMap() shouldBe mapOf("reverb.size" to 3.0, "room" to 9.0, "delay.time" to 0.5)
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
