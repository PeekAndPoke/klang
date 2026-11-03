/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * The reverb shelf's scaled shelves ([ReverbUnits.atFactor], KLANG-00214 (oversampling regions) step 3a): a reverb
 * inside a region rents a unit at the region's rate from a shelf of its own, which the base shelf creates once, keeps,
 * and housekeeps with its own, so the warmup's "every idle unit is zeroed" covers it.
 */
class ReverbUnitsScaledSpec : StringSpec({

    val sampleRate = 44100

    "atFactor(1) is the shelf itself; a factor's shelf is created once, at that rate, its units mapped by the factor" {
        val units = ReverbUnits(sampleRate)

        (units.atFactor(1) === units) shouldBe true

        val at4 = units.atFactor(4)

        (units.atFactor(4) === at4) shouldBe true
        (units.atFactor(2) === at4) shouldBe false
        at4.sampleRate shouldBe 4 * sampleRate

        val unit = at4.rent().shouldNotBeNull()

        unit.sampleRate shouldBe 4 * sampleRate
        unit.factor shouldBe 4
        units.atFactor(16).rent().shouldNotBeNull().factor shouldBe 16
    }

    "on a scaled shelf the factors multiply: 2x of the 2x shelf is the one 4x shelf, its units mapped by 4" {
        val units = ReverbUnits(sampleRate)
        val at2 = units.atFactor(2)

        (at2.atFactor(2) === units.atFactor(4)) shouldBe true
        (at2.atFactor(4).atFactor(2) === units.atFactor(16)) shouldBe true
        at2.atFactor(1) shouldBe at2

        val unit = at2.atFactor(2).rent().shouldNotBeNull()

        unit.factor shouldBe 4
        unit.sampleRate shouldBe 4 * sampleRate
    }

    "a scaled shelf keeps fewer idle units, by its factor: a 4x unit is four base units" {
        val units = ReverbUnits(sampleRate, maxIdle = 8)
        val at4 = units.atFactor(4)
        val rented = List(3) { at4.rent().shouldNotBeNull() }

        rented.forEach { at4.giveBack(it) }

        // 8 / 4: two kept, the third dropped
        at4.idleCount shouldBe 2
        at4.dropped shouldBe 1
    }

    "a dirty unit on a scaled shelf keeps the base shelf unclean until the base shelf's housekeeping zeroes it" {
        val units = ReverbUnits(sampleRate)
        val at8 = units.atFactor(8)

        at8.giveBack(at8.rent().shouldNotBeNull())

        at8.isClean shouldBe false
        units.isClean shouldBe false

        // The base shelf has nothing dirty of its own: its call reaches the scaled one
        units.housekeep() shouldBe true
        at8.isClean shouldBe true
        units.isClean shouldBe true
        units.housekeep() shouldBe false
    }

    "the base shelf housekeeps its own units first, one unit per call" {
        val units = ReverbUnits(sampleRate)
        val at2 = units.atFactor(2)

        units.giveBack(units.rent().shouldNotBeNull())
        at2.giveBack(at2.rent().shouldNotBeNull())

        units.housekeep() shouldBe true
        units.dirtyCount shouldBe 0
        at2.isClean shouldBe false

        units.housekeep() shouldBe true
        units.isClean shouldBe true
    }
})
