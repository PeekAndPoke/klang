/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.apps.common.createScriptEngine
import io.peekandpoke.klang.sprudel.SprudelPattern

/** Four tagged notes a cycle; at 60 rpm a cycle is a second, so the seconds read are the cycles read. */
private const val TAGGED = """note("c3 e3 g3 c4").tag("lead")"""

private suspend fun reading(code: String = TAGGED, heardSec: Double = 0.0): SongReading? =
    SongReading.begin(engine = createScriptEngine(), code = code, rpm = 60.0, heardSec = heardSec)

private fun SongReading.leadTimes(): List<Double> = song.times(listOf("lead"))

class SongReadingSpec : StringSpec({

    "before a playback has begun, the first stretch is read from the code and the tempo alone" {
        val reading = reading().shouldNotBeNull()
        val times = reading.leadTimes()

        // AHEAD_SEC (14 s) is more than AHEAD (8 cycles) at a cycle a second: cycles 0 to 13, four notes each
        times.size shouldBe 14 * 4
        times.first() shouldBe 0.0
        times.last() shouldBeLessThan 14.0
        times.last() shouldBeGreaterThanOrEqual 13.0
    }

    "read on, it stays ahead of what is heard" {
        val reading = reading().shouldNotBeNull()

        reading.readOn(heardSec = 10.0)

        reading.leadTimes().size shouldBe 24 * 4
        reading.leadTimes().last() shouldBeGreaterThanOrEqual 23.0
    }

    "fallen behind what is heard, the past is not read, and what lies well behind is forgotten" {
        val reading = reading().shouldNotBeNull()

        reading.readOn(heardSec = 30.0)

        val times = reading.leadTimes()

        // read on from a bar before what is heard (29) to 14 s ahead, nothing of 14 to 28; and what was read before
        // (up to 14) lies before 30 - KEEP_SEC (16 s): forgotten
        times.first() shouldBe 29.0
        times.size shouldBe 15 * 4
        times.last() shouldBeGreaterThanOrEqual 43.0
    }

    "a song that begins later is read from a bar before what is heard" {
        val reading = reading(heardSec = 5.5).shouldNotBeNull()

        reading.leadTimes().first() shouldBe 4.0
    }

    "events without a tag are not the band's" {
        val reading = reading(code = """note("c3 e3")""").shouldNotBeNull()

        reading.leadTimes().shouldBeEmpty()
        reading.song.version shouldBe 0
    }

    "control events are not the band's, even tagged: only the notes are read" {
        // a tagged katalyst declaration beside the notes: one control event a cycle, which never sounds
        val code = """stack($TAGGED, katalyst(Katalyst(k => k.classic())).tag("lead"))"""

        // the pattern does carry the tagged control events: what the reading leaves out is there to leave out
        val pattern = SprudelPattern.compile(createScriptEngine(), code).shouldNotBeNull()
        val controls = pattern.queryEvents(fromCycles = 0.0, toCycles = 1.0, cps = 1.0)
            .map { it.toVoiceData() }
            .filter { it.control == true && it.tags.orEmpty().isNotEmpty() }

        controls.size shouldBe 1

        val reading = reading(code = code).shouldNotBeNull()

        reading.leadTimes().size shouldBe 14 * 4
    }

    "code that gives no pattern gives no reading" {
        reading(code = "1 + 1").shouldBeNull()
    }
})
