/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import kotlin.math.abs
import kotlin.math.sin

/**
 * The reverb shelf's scaled shelves ([ReverbUnits.atFactor], KLANG-00214 (oversampling regions) step 3a): a reverb
 * inside a region rents a unit at the region's rate from a shelf of its own, which the base shelf creates once, keeps,
 * and housekeeps with its own, so the warmup's "every idle unit is zeroed" covers it.
 */
class ReverbUnitsScaledSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /**
     * Runs [unit] on a loud signal for [blocks] blocks, so every sample of every line holds energy: 110 blocks pass the
     * longest line at 8x (about 13 100 samples at 44.1 kHz).
     */
    fun dirty(unit: Reverb, blocks: Int = 110) {
        val input = StereoBuffer(blockFrames)
        val output = StereoBuffer(blockFrames)

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                input.left[i] = 0.8 * sin(0.37 * (b * blockFrames + i))
                input.right[i] = 0.8 * sin(0.53 * (b * blockFrames + i))
            }

            unit.process(input = input, output = output, length = blockFrames)
        }
    }

    /**
     * The loudest sample [unit] puts out over [blocks] blocks of silence: about 1e-19 when every line and store is zero
     * (the reverb's deliberate anti-denormal offset), about 0.1 on a unit left dirty.
     */
    fun ringOf(unit: Reverb, blocks: Int = 110): Double {
        val input = StereoBuffer(blockFrames)
        val output = StereoBuffer(blockFrames)
        var peak = 0.0

        for (b in 0 until blocks) {
            output.clear()
            unit.process(input = input, output = output, length = blockFrames)

            for (i in 0 until blockFrames) {
                peak = maxOf(peak, abs(output.left[i]), abs(output.right[i]))
            }
        }

        return peak
    }

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

    "a dirty unit on a scaled shelf is zeroed a base unit's worth per call: an 8x unit takes eight calls, then is silent" {
        val units = ReverbUnits(sampleRate)
        val at8 = units.atFactor(8)
        val unit = at8.rent().shouldNotBeNull()

        dirty(unit)
        at8.giveBack(unit)

        at8.isClean shouldBe false
        units.isClean shouldBe false

        // The base shelf has nothing dirty of its own: its calls reach the scaled one, one base unit's stores each
        repeat(7) {
            units.housekeep() shouldBe true
            at8.isClean shouldBe false
        }

        units.housekeep() shouldBe true
        at8.isClean shouldBe true
        units.isClean shouldBe true
        units.housekeep() shouldBe false

        // Taken clean, no zeroing on the spot, and nothing left in it
        (at8.rent() === unit) shouldBe true
        at8.syncCleans shouldBe 0
        ringOf(unit) shouldBeLessThan 1e-12
    }

    "a scaled unit rented half zeroed is zeroed whole on the spot, and its next slicing starts from the top" {
        val units = ReverbUnits(sampleRate)
        val at4 = units.atFactor(4)
        val unit = at4.rent().shouldNotBeNull()

        dirty(unit)
        at4.giveBack(unit)
        units.housekeep() shouldBe true
        at4.isClean shouldBe false

        (at4.rent() === unit) shouldBe true
        at4.syncCleans shouldBe 1
        ringOf(unit) shouldBeLessThan 1e-12

        // A second life, then housekeeping again: four whole slices, not the three the first slicing had left
        dirty(unit)
        at4.giveBack(unit)
        repeat(3) { units.housekeep() }
        at4.isClean shouldBe false
        units.housekeep()
        at4.isClean shouldBe true
        ringOf(unit) shouldBeLessThan 1e-12
    }

    "a scaled shelf allocates through the injected allocator, with its factor, so a refusal reaches it" {
        val asked = mutableListOf<Pair<Int, Int>>()
        var failing = true
        val units = ReverbUnits(sampleRate, allocate = { rate, factor ->
            asked.add(rate to factor)
            if (failing) null else Reverb(sampleRate = rate, factor = factor)
        })
        val at4 = units.atFactor(4)

        at4.rent().shouldBeNull()
        at4.failures shouldBe 1
        asked shouldBe listOf(4 * sampleRate to 4)

        failing = false
        at4.rent().shouldNotBeNull().factor shouldBe 4
        at4.allocations shouldBe 1
    }

    "the base shelf housekeeps its own units first, one unit per call, then the scaled ones a base unit's worth per call" {
        val units = ReverbUnits(sampleRate)
        val at2 = units.atFactor(2)

        units.giveBack(units.rent().shouldNotBeNull())
        at2.giveBack(at2.rent().shouldNotBeNull())

        units.housekeep() shouldBe true
        units.dirtyCount shouldBe 0
        at2.isClean shouldBe false

        // The 2x unit, in two slices
        units.housekeep() shouldBe true
        at2.isClean shouldBe false
        units.housekeep() shouldBe true
        units.isClean shouldBe true
    }
})
