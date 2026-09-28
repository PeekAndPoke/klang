/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * The name-to-index conversion both Katalyst doors and the backend resolver share (step 5a-2,
 * 2026-09-18): a `body.material` and a `vowel.vowel` slot carry the INDEX of a name in a closed,
 * ordered catalogue, so no string slot joins the wire and a pattern's `body(material = "wood")` reaches a
 * declared chain.
 *
 * What is pinned here is the CONVERSION, both ways and at its edges, because it is the one place
 * that can silently point a song at the wrong box. That the boxes themselves are right is
 * `LangBodySpec` / `LangVowelComprehensiveSpec` (the voice path) and `KatalystSlotResolverSpec`
 * (the chain path); this file never restates a mode or a formant.
 */
class CatalogueIndexSpec : StringSpec({

    // ── The body catalogue ───────────────────────────────────────────────────────────────────────

    "every body material round-trips through its index, and the index IS its position in names" {
        // The round trip is the contract a door and the resolver meet on: the door writes
        // `indexOf(name)`, the resolver reads `modesAt(index)`.
        //
        // Read the second assertion carefully, because it is WEAKER than it looks: `modesFor` is
        // itself `modesAt(indexOf(name))` since step 5a-2, so an off-by-one INSIDE `modesAt` shifts
        // both sides and this row would not see it. What it does catch is the two halves drifting
        // apart (a door that stopped lowercasing, a name missing from one side). The bias itself is
        // caught by the `names.indexOf` cross-check here and by the anchored table facts below,
        // neither of which routes through the functions under test.
        BodyMaterials.names.forEachIndexed { index, name ->
            withClue("$name at $index") {
                BodyMaterials.indexOf(name) shouldBe index.toDouble()
                // The independent oracle: the list's own position, not the map the lookup uses.
                BodyMaterials.indexOf(name) shouldBe BodyMaterials.names.indexOf(name).toDouble()
                BodyMaterials.modesAt(index.toDouble()) shouldBeSameInstanceAs BodyMaterials.modesFor(name)
            }
        }
    }

    "the table is anchored: index 1 is wood, and wood's first mode is 100 Hz" {
        // The fact that does NOT route through `indexOf` or `modesFor`, so a uniform off-by-one
        // inside `modesAt` (which the round-trip rows above cannot see) fails here. The frequency
        // is read off `BodyMaterials`' own wood arm, which the mode tables have carried since the
        // 2026-07 body work, and `names[1]` is wood by declaration order.
        BodyMaterials.names[1] shouldBe "wood"

        val wood = BodyMaterials.modesAt(1.0).shouldNotBeNull()

        wood.size shouldBe 8
        wood.first() shouldBe FilterDef.Body.Mode(freq = 100.0, db = 3.0, q = 12.0)

        // ...and the neighbour is a DIFFERENT material, so the row cannot pass on a table where
        // every index answers the same list.
        BodyMaterials.names[2] shouldBe "cedar"
        BodyMaterials.modesAt(2.0).shouldNotBeNull().first().freq shouldBe 95.0
    }

    "index 0 is none, and none is the only name in the table that has no modes" {
        BodyMaterials.indexOf("none") shouldBe 0.0
        BodyMaterials.modesAt(0.0).shouldBeNull()

        // ...and every other name DOES resolve, so the row above is not passing on a table of nulls.
        BodyMaterials.names.drop(1).forEach { name ->
            withClue(name) { BodyMaterials.modesFor(name).shouldNotBeNull() }
        }
    }

    "an unknown body material is index 0, which is off: user input never throws" {
        BodyMaterials.indexOf("unobtainium") shouldBe 0.0
        BodyMaterials.modesFor("unobtainium").shouldBeNull()
        BodyMaterials.indexOf("") shouldBe 0.0
    }

    "a body material name is case-insensitive on the index door too" {
        BodyMaterials.indexOf("WOOD") shouldBe BodyMaterials.indexOf("wood")
        BodyMaterials.modesFor("Wood") shouldBeSameInstanceAs BodyMaterials.modesFor("wood")
    }

    "modesAt is off outside the table and rounds to the nearest index inside it" {
        // The edges, one row each, because each is a different failure: an unset slot, a negative
        // number a pattern can write, the first index past the end, and the rounding rule.
        withClue("unset") { BodyMaterials.modesAt(SLOT_UNSET).shouldBeNull() }
        withClue("negative") { BodyMaterials.modesAt(-1.0).shouldBeNull() }
        withClue("none") { BodyMaterials.modesAt(0.0).shouldBeNull() }
        withClue("past the end") { BodyMaterials.modesAt(BodyMaterials.names.size.toDouble()).shouldBeNull() }

        // Rounding, both ways across the 0/1 boundary: 0.4 is `none` and 0.6 is the first material.
        withClue("0.4 rounds to none") { BodyMaterials.modesAt(0.4).shouldBeNull() }
        withClue("0.6 rounds to the first material") {
            BodyMaterials.modesAt(0.6) shouldBeSameInstanceAs BodyMaterials.modesFor(BodyMaterials.names[1])
        }

        // And the documented TIE rule, which `kotlin.math.round` decides the same way on both
        // platforms: a tie goes to the EVEN index, so 0.5 is `none` and 2.5 is index 2, never 3.
        // 2.5 rather than 1.5 because the two candidates must be DIFFERENT materials: half-even
        // gives index 2, half-up would give 3, and the two banks are content-distinct, so the row
        // cannot pass through list identity alone.
        withClue("0.5 ties to the even index, which is none") { BodyMaterials.modesAt(0.5).shouldBeNull() }
        withClue("2.5 ties to index 2, not index 3") {
            BodyMaterials.modesAt(2.5) shouldBeSameInstanceAs BodyMaterials.modesFor(BodyMaterials.names[2])
            BodyMaterials.modesAt(2.5) shouldNotBe BodyMaterials.modesFor(BodyMaterials.names[3])
        }
    }

    // ── The vowel catalogue ──────────────────────────────────────────────────────────────────────

    "every vowel round-trips through its index, and the index IS its position in names" {
        // Same shape and the same caveat as the body row: `bandsFor` goes through `bandsAt`, so the
        // second assertion pins the two halves together and the `names.indexOf` cross-check plus
        // the anchored bank below pin the absolute position.
        VowelBands.names.forEachIndexed { index, name ->
            withClue("$name at $index") {
                VowelBands.indexOf(name) shouldBe index.toDouble()
                VowelBands.indexOf(name) shouldBe VowelBands.names.indexOf(name).toDouble()
                VowelBands.bandsAt(index.toDouble()) shouldBeSameInstanceAs VowelBands.bandsFor(name)
            }
        }
    }

    "the vowel table is anchored: index 1 is bass:a, whose first formant is 600 Hz" {
        // The independent fact, the twin of the body's. `bass` is the first register and `a` the
        // first vowel in the generated cross product, and 600 Hz is what the table's bass `a` arm
        // has carried since the sung-vowel tables landed.
        VowelBands.names[1] shouldBe "bass:a"

        val bassA = VowelBands.bandsAt(1.0).shouldNotBeNull()

        bassA.size shouldBe 5
        bassA.first() shouldBe FilterDef.Formant.Band(freq = 600.0, db = 0.0, q = 60.0)

        // The soprano `a` is a different bank at a different index, so nothing collapsed.
        VowelBands.bandsFor("soprano:a").shouldNotBeNull().first().freq shouldBe 800.0
    }

    "index 0 is none, and every other catalogue entry has a bank" {
        VowelBands.indexOf("none") shouldBe 0.0
        VowelBands.bandsAt(0.0).shouldBeNull()

        VowelBands.names.drop(1).forEach { name ->
            withClue(name) { VowelBands.bandsFor(name).shouldNotBeNull() }
        }
    }

    "every register and vowel bandsFor accepts is IN the catalogue, with its own index" {
        // What this row guards, precisely: the CROSS PRODUCT is complete and collision-free. Every
        // register crossed with every vowel resolves, lands on its own index, and the catalogue
        // holds exactly that many names plus `none`. So a name dropped from the generated
        // catalogue, or two names collapsing onto one index, is red here.
        //
        // What it does NOT guard, because the literals below are a copy of the two lists the
        // catalogue is generated from and not of the table's `when` arms: a vowel that an arm
        // answers but no list names would be missing from both sides and stay green. The row
        // "every catalogue entry has a bank" guards the other direction, catalogue to table.
        val registers = listOf("bass", "tenor", "alto", "countertenor", "soprano")
        val vowels = listOf(
            "a", "ei", "au", "e", "i", "o", "u",
            "ae", "ä", "oe", "ö", "ue", "ü",
            "eu", "äu",
        )

        // Every pair resolves, and to its own index rather than collapsing onto one.
        val indices = mutableSetOf<Double>()

        registers.forEach { register ->
            vowels.forEach { vowel ->
                val name = "$register:$vowel"

                withClue(name) {
                    VowelBands.bandsFor(name).shouldNotBeNull()
                    val index = VowelBands.indexOf(name)

                    index shouldBe VowelBands.names.indexOf(name).toDouble()
                    indices.add(index)
                }
            }
        }

        indices.size shouldBe registers.size * vowels.size
        VowelBands.names.size shouldBe registers.size * vowels.size + 1
    }

    "a bare vowel name is the soprano register on the index door, as it always was" {
        VowelBands.indexOf("a") shouldBe VowelBands.indexOf("soprano:a")
        VowelBands.bandsFor("a") shouldBeSameInstanceAs VowelBands.bandsFor("soprano:a")

        // ...and a different register really is a different index, so the row above is not an
        // accident of everything answering the same number.
        VowelBands.indexOf("bass:a") shouldNotBe VowelBands.indexOf("soprano:a")
    }

    "an unknown vowel or register is index 0, which is off" {
        VowelBands.indexOf("zzz") shouldBe 0.0
        VowelBands.indexOf("robot:a") shouldBe 0.0
        VowelBands.bandsFor("bass:zzz").shouldBeNull()
        VowelBands.bandsFor("none") shouldBe null
        VowelBands.bandsFor("bass:none") shouldBe null
    }

    "a vowel name is case-insensitive, and a third colon part is ignored" {
        VowelBands.indexOf("BASS:A") shouldBe VowelBands.indexOf("bass:a")
        VowelBands.indexOf("bass:a:loud") shouldBe VowelBands.indexOf("bass:a")
    }

    "bandsAt is off outside the catalogue and rounds to the nearest index inside it" {
        withClue("unset") { VowelBands.bandsAt(SLOT_UNSET).shouldBeNull() }
        withClue("negative") { VowelBands.bandsAt(-1.0).shouldBeNull() }
        withClue("none") { VowelBands.bandsAt(0.0).shouldBeNull() }
        withClue("past the end") { VowelBands.bandsAt(VowelBands.names.size.toDouble()).shouldBeNull() }

        withClue("0.4 rounds to none") { VowelBands.bandsAt(0.4).shouldBeNull() }
        withClue("0.6 rounds to the first vowel") {
            VowelBands.bandsAt(0.6) shouldBeSameInstanceAs VowelBands.bandsFor(VowelBands.names[1])
        }

        // The tie rule, the body table's twin, on a tie whose two candidates really differ:
        // 4.5 goes to 4 half-even and to 5 half-up, and those two banks are content-distinct.
        // (3.5 would not discriminate: both rules land on 4.)
        withClue("0.5 ties to the even index, which is none") { VowelBands.bandsAt(0.5).shouldBeNull() }
        withClue("4.5 ties to index 4, not index 5") {
            VowelBands.bandsAt(4.5) shouldBeSameInstanceAs VowelBands.bandsFor(VowelBands.names[4])
            VowelBands.bandsAt(4.5) shouldNotBe VowelBands.bandsFor(VowelBands.names[5])
        }
    }

    // ── The waveshaper and LFO catalogues (phase 3 step 3b, 2026-09-25) ────────────────────────────
    //
    // The conversion only. That position i IS the backend enum's entry i, and that every name and
    // alias still reaches the shape it reached before 3b, is `ShapeCatalogueSpec` in audio_be, which
    // can see the enums.

    "every waveshaper and LFO name is its own position, and every alias its canonical name's" {
        for ((names, aliases, indexOf) in listOf(
            Triple(DistortionShapes.names, DistortionShapes.aliases, { n: String -> DistortionShapes.indexOf(n) }),
            Triple(LfoShapes.names, LfoShapes.aliases, { n: String -> LfoShapes.indexOf(n) }),
        )) {
            names.forEachIndexed { i, name ->
                withClue(name) {
                    indexOf(name) shouldBe i.toDouble()
                    indexOf(name.uppercase()) shouldBe i.toDouble()
                }
            }

            for ((alias, canonical) in aliases) {
                withClue(alias) { indexOf(alias) shouldBe names.indexOf(canonical).toDouble() }
            }
        }
    }

    "an unknown waveshaper is soft (0) and an unknown or absent LFO shape the sine (0)" {
        DistortionShapes.names[DistortionShapes.SOFT_INDEX] shouldBe "soft"
        LfoShapes.names[LfoShapes.SINE_INDEX] shouldBe "sine"

        DistortionShapes.indexOf("nonexistent") shouldBe 0.0
        LfoShapes.indexOf("rampup") shouldBe 0.0
        LfoShapes.indexOf(null) shouldBe 0.0
    }

    "indexAt: the nearest position, ties to even, and the fallback outside the catalogue" {
        for (index in listOf(SLOT_UNSET, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, -0.51)) {
            withClue(index) {
                DistortionShapes.indexAt(index) shouldBe DistortionShapes.SOFT_INDEX
                LfoShapes.indexAt(index) shouldBe LfoShapes.SINE_INDEX
            }
        }

        DistortionShapes.indexAt(DistortionShapes.names.size.toDouble()) shouldBe 0
        DistortionShapes.indexAt(DistortionShapes.names.size - 0.6) shouldBe DistortionShapes.names.size - 1
        LfoShapes.indexAt(4.5) shouldBe 4 // the last position, by the tie rule: half-up would be 5, past the end
        DistortionShapes.indexAt(-0.49) shouldBe 0
        DistortionShapes.indexAt(2.5) shouldBe 2 // half-up would be 3
        LfoShapes.indexAt(2.5) shouldBe 2
        DistortionShapes.indexAt(1.6) shouldBe 2 // nearest, not truncated
    }

    "the shared rule answers its FALLBACK for a non-finite index, whatever the fallback is" {
        // Both catalogues fall back to position 0, and a NaN that slipped past the guard would ALSO land
        // on 0 (`NaN.toInt()` is 0 on both platforms), so through them the guard cannot be seen. The rule
        // itself must not depend on that coincidence: a catalogue whose fallback is not 0 is one append
        // away.
        for (index in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue(index) { catalogueIndexAt(index, size = 5, fallback = 3) shouldBe 3 }
        }
    }
})
