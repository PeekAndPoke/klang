/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class PitchAndGainSpec : StringSpec({

    // All expected values are computed from the definitions by hand, never from the function
    // under test: 2^(n/12) for semitones, 2^(c/1200) for cents, 10^(dB/20) for gains.

    "semitones() converts a number of semitones to a frequency ratio" {
        listOf(
            0.0 to 1.0,          // unison
            12.0 to 2.0,         // an octave up
            -12.0 to 0.5,        // an octave down
            24.0 to 4.0,         // two octaves up
            7.0 to 1.4983,       // 2^(7/12), a perfect fifth
            4.0 to 1.2599,       // 2^(4/12), a major third
            3.0 to 1.1892,       // 2^(3/12), a minor third
            -7.0 to 0.6674,      // 2^(-7/12), a fifth down
            0.5 to 1.0293,       // 2^(1/24), half a semitone
        ).forEach { (semis, expected) ->
            withClue("($semis).semitones()") {
                semis.semitones() shouldBe (expected plusOrMinus 1e-4)
            }
        }
    }

    "cents() converts a number of cents to a frequency ratio" {
        listOf(
            0.0 to 1.0,          // unison
            1200.0 to 2.0,       // an octave up
            -1200.0 to 0.5,      // an octave down
            100.0 to 1.0595,     // 2^(1/12), one semitone
            700.0 to 1.4983,     // 2^(7/12), a perfect fifth
            50.0 to 1.0293,      // 2^(1/24), a quarter tone
            -25.0 to 0.98566,    // 2^(-1/48), a typical detune
        ).forEach { (cents, expected) ->
            withClue("($cents).cents()") {
                cents.cents() shouldBe (expected plusOrMinus 1e-4)
            }
        }
    }

    "one hundred cents is one semitone" {
        listOf(-1200.0, -50.0, 0.0, 100.0, 700.0, 1200.0).forEach { cents ->
            withClue("($cents).cents() == (${cents / 100.0}).semitones()") {
                cents.cents() shouldBe ((cents / 100.0).semitones() plusOrMinus 1e-12)
            }
        }
    }

    "toSemitones() converts a frequency ratio to a number of semitones" {
        listOf(
            1.0 to 0.0,          // unison
            2.0 to 12.0,         // an octave up
            0.5 to -12.0,        // an octave down
            4.0 to 24.0,         // two octaves up
            1.5 to 7.0196,       // 12 * log2(3/2), the just fifth, two cents above the tempered one
            1.25 to 3.8631,      // 12 * log2(5/4), the just major third
            3.0 to 19.0196,      // 12 * log2(3), an octave and a just fifth
        ).forEach { (ratio, expected) ->
            withClue("($ratio).toSemitones()") {
                ratio.toSemitones() shouldBe (expected plusOrMinus 1e-4)
            }
        }
    }

    "toSemitones() is the inverse of semitones()" {
        listOf(-24.0, -12.5, -7.0, -0.25, 0.0, 3.0, 7.0, 12.0, 19.7).forEach { semis ->
            withClue("($semis).semitones().toSemitones()") {
                semis.semitones().toSemitones() shouldBe (semis plusOrMinus 1e-9)
            }
        }
    }

    "db() converts decibels to a linear gain" {
        listOf(
            0.0 to 1.0,          // unity
            6.0 to 1.9953,       // 10^(6/20), roughly double the amplitude
            -6.0 to 0.5012,      // 10^(-6/20), roughly half the amplitude
            3.0 to 1.4125,       // 10^(3/20)
            20.0 to 10.0,        // ten times the amplitude
            -20.0 to 0.1,        // a tenth of it
            -60.0 to 0.001,      // a thousandth of it
        ).forEach { (db, expected) ->
            withClue("($db).db()") {
                db.db() shouldBe (expected plusOrMinus 1e-4)
            }
        }
    }

    "toDb() converts a linear gain to decibels" {
        listOf(
            1.0 to 0.0,          // unity
            2.0 to 6.0206,       // 20 * log10(2)
            0.5 to -6.0206,      // 20 * log10(1/2)
            0.25 to -12.0412,    // 20 * log10(1/4)
            10.0 to 20.0,
            0.1 to -20.0,
            0.001 to -60.0,
        ).forEach { (gain, expected) ->
            withClue("($gain).toDb()") {
                gain.toDb() shouldBe (expected plusOrMinus 1e-4)
            }
        }
    }

    "toDb() is the inverse of db()" {
        listOf(-60.0, -12.0, -6.0, -0.5, 0.0, 3.0, 12.5).forEach { db ->
            withClue("($db).db().toDb()") {
                db.db().toDb() shouldBe (db plusOrMinus 1e-9)
            }
        }
    }

    "the octave and unity points are exact" {
        0.0.semitones() shouldBe 1.0
        12.0.semitones() shouldBe 2.0
        (-12.0).semitones() shouldBe 0.5
        1200.0.cents() shouldBe 2.0
        2.0.toSemitones() shouldBe 12.0
        1.0.toSemitones() shouldBe 0.0
        0.0.db() shouldBe 1.0
        1.0.toDb() shouldBe 0.0
    }

    "transposing a frequency by semitones multiplies it by the ratio" {
        // The same formula the engine spells as Double.applySemitoneDetuneToFrequency.
        (440.0 * 12.0.semitones()) shouldBe (880.0 plusOrMinus 1e-9)
        (440.0 * (-12.0).semitones()) shouldBe (220.0 plusOrMinus 1e-9)
        // Middle C, nine semitones below concert A.
        (440.0 * (-9.0).semitones()) shouldBe (261.6256 plusOrMinus 1e-4)
    }

    "toSemitones() of zero or a negative ratio follows kotlin.math" {
        0.0.toSemitones() shouldBe Double.NEGATIVE_INFINITY
        (-1.0).toSemitones().isNaN() shouldBe true
        (-0.5).toSemitones().isNaN() shouldBe true
    }

    "toDb() of zero or a negative gain follows kotlin.math" {
        0.0.toDb() shouldBe Double.NEGATIVE_INFINITY
        (-1.0).toDb().isNaN() shouldBe true
        (-0.5).toDb().isNaN() shouldBe true
    }
})
