/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import kotlin.math.pow

/**
 * [ResonatorTables], the engine's one table per catalogue index (engine tidy-up step 12 (a)).
 *
 * The stage decides whether to install by comparing table REFERENCES, which reproduces the structural compare of the
 * rows it replaced only if two indices share a table exactly when their rows are equal. The gains are the doubles the
 * tables were heard with, written here from scratch with literal constants (the vowel tame 0.05, the SVF's q clamp
 * `[0.1, 200]`), so a regrouped product shows.
 */
class ResonatorTablesSpec : StringSpec({

    fun bits(x: Double): Long = x.toRawBits()

    "two body indices share a table exactly when their rows are equal; none has no table" {
        val n = BodyMaterials.names.size

        ResonatorTables.at(kind = ResonatorKind.BODY, slotValue = 0.0).shouldBeNull()

        for (i in 1 until n) {
            for (j in 1 until n) {
                val same = BodyMaterials.modesAt(i.toDouble()) == BodyMaterials.modesAt(j.toDouble())
                val ti = ResonatorTables.at(kind = ResonatorKind.BODY, slotValue = i.toDouble())
                val tj = ResonatorTables.at(kind = ResonatorKind.BODY, slotValue = j.toDouble())

                withClue("${BodyMaterials.names[i]} / ${BodyMaterials.names[j]}") {
                    (ti === tj) shouldBe same
                }
            }
        }
    }

    "two vowel indices share a table exactly when their rows are equal, and the catalogue HAS such pairs" {
        val n = VowelBands.names.size
        var sharedPairs = 0

        ResonatorTables.at(kind = ResonatorKind.VOWEL, slotValue = 0.0).shouldBeNull()

        for (i in 1 until n) {
            for (j in 1 until n) {
                val same = VowelBands.bandsAt(i.toDouble()) == VowelBands.bandsAt(j.toDouble())
                val ti = ResonatorTables.at(kind = ResonatorKind.VOWEL, slotValue = i.toDouble())
                val tj = ResonatorTables.at(kind = ResonatorKind.VOWEL, slotValue = j.toDouble())

                withClue("${VowelBands.names[i]} / ${VowelBands.names[j]}") {
                    (ti === tj) shouldBe same
                }

                if (i != j && same) {
                    sharedPairs++
                }
            }
        }

        // The aliases the stage relies on: three names of one bank, the two spellings of one register, one umlaut.
        sharedPairs shouldBeGreaterThan 0
        ResonatorTables.at(ResonatorKind.VOWEL, VowelBands.indexOf("bass:ei")) shouldBeSameInstanceAs
            ResonatorTables.at(ResonatorKind.VOWEL, VowelBands.indexOf("bass:a"))
        ResonatorTables.at(ResonatorKind.VOWEL, VowelBands.indexOf("countertenor:o")) shouldBeSameInstanceAs
            ResonatorTables.at(ResonatorKind.VOWEL, VowelBands.indexOf("alto:o"))
        ResonatorTables.at(ResonatorKind.VOWEL, VowelBands.indexOf("soprano:ä")) shouldBeSameInstanceAs
            ResonatorTables.at(ResonatorKind.VOWEL, VowelBands.indexOf("soprano:ae"))
    }

    "every table carries its rows' freq and q raw and the gain rules' doubles, bit for bit" {
        for (i in 1 until BodyMaterials.names.size) {
            val rows = BodyMaterials.modesAt(i.toDouble()).shouldNotBeNull()
            val t = ResonatorTables.at(kind = ResonatorKind.BODY, slotValue = i.toDouble()).shouldNotBeNull()

            t.count shouldBe rows.size

            for (b in rows.indices) {
                withClue("${BodyMaterials.names[i]} mode $b") {
                    bits(t.freq[b]) shouldBe bits(rows[b].freq)
                    bits(t.q[b]) shouldBe bits(rows[b].q)
                    // The catalogue's dBs are finite, so the NaN rule of the body gain does not apply here.
                    bits(t.gain[b]) shouldBe bits(10.0.pow(rows[b].db / 20.0))
                }
            }
        }

        for (i in 1 until VowelBands.names.size) {
            val rows = VowelBands.bandsAt(i.toDouble()).shouldNotBeNull()
            val t = ResonatorTables.at(kind = ResonatorKind.VOWEL, slotValue = i.toDouble()).shouldNotBeNull()

            t.count shouldBe rows.size

            for (b in rows.indices) {
                withClue("${VowelBands.names[i]} band $b") {
                    bits(t.freq[b]) shouldBe bits(rows[b].freq)
                    bits(t.q[b]) shouldBe bits(rows[b].q)
                    bits(t.gain[b]) shouldBe bits(10.0.pow(rows[b].db / 20.0) * rows[b].q.coerceIn(0.1, 200.0) * 0.05)
                }
            }
        }
    }

    "the capacity of a kind is its largest row count: 8 body modes, 5 vowel bands" {
        val bodyMax = (1 until BodyMaterials.names.size).maxOf { BodyMaterials.modesAt(it.toDouble())?.size ?: 0 }
        val vowelMax = (1 until VowelBands.names.size).maxOf { VowelBands.bandsAt(it.toDouble())?.size ?: 0 }

        ResonatorTables.capacity(ResonatorKind.BODY) shouldBe bodyMax
        ResonatorTables.capacity(ResonatorKind.VOWEL) shouldBe vowelMax
        bodyMax shouldBe 8
        vowelMax shouldBe 5
    }

    "a slot value reads the catalogue's own index rule: unset, none and out of range are no table" {
        for (kind in ResonatorKind.entries) {
            withClue(kind) {
                ResonatorTables.at(kind = kind, slotValue = SLOT_UNSET).shouldBeNull()
                ResonatorTables.at(kind = kind, slotValue = -1.0).shouldBeNull()
                ResonatorTables.at(kind = kind, slotValue = 0.4).shouldBeNull()
                ResonatorTables.at(kind = kind, slotValue = 1e9).shouldBeNull()
                ResonatorTables.at(kind = kind, slotValue = 0.6) shouldBeSameInstanceAs ResonatorTables.at(kind = kind, slotValue = 1.0)
            }
        }

        ResonatorTables.at(ResonatorKind.BODY, BodyMaterials.indexOf("glass")).shouldNotBeNull().count shouldBe 8
        ResonatorTables.at(ResonatorKind.VOWEL, VowelBands.indexOf("tenor:o")).shouldNotBeNull().count shouldBe 5
    }
})
