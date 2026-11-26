/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.apps.common.FrameStats
import io.peekandpoke.klang.viz.liveact.PicturePreset
import kotlin.js.Date
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A result kept on a plain display of 1280 by 800 at 1.25 pixels, by this test's version. */
private val KEPT = StoredTest(
    version = StoredTest.TEST_VERSION,
    at = Date("2026-11-19T12:00:00Z").getTime(),
    ratio = 1.25,
    width = 1280,
    height = 800,
    voices = 212,
    fps = 59.5,
    slowestMs = 21.5,
    quality = "high",
)

/** As the browser's storage reads it (ultra's persistInLocalStorage): what it does not know, it leaves out. */
private val JSON = Json { ignoreUnknownKeys = true }

class StoredTestSpec : StringSpec({

    "a kept result counts on the same display, measured by this test's version" {
        KEPT.fits(ratio = 1.25, width = 1280, height = 800) shouldBe true
    }

    "another pixel ratio, or another screen size, and it counts no more" {
        KEPT.fits(ratio = 2.0, width = 1280, height = 800) shouldBe false
        KEPT.fits(ratio = 1.25, width = 1440, height = 800) shouldBe false
        KEPT.fits(ratio = 1.25, width = 1280, height = 900) shouldBe false
    }

    "a result of another test version counts no more: 5, before the test chose the picture's quality as a whole" {
        KEPT.copy(version = 5).fits(ratio = 1.25, width = 1280, height = 800) shouldBe false
        KEPT.copy(version = StoredTest.TEST_VERSION + 1).fits(ratio = 1.25, width = 1280, height = 800) shouldBe false
        KEPT.copy(version = StoredTest.TEST_VERSION - 1).fits(ratio = 1.25, width = 1280, height = 800) shouldBe false
    }

    "a kept quality that names no preset makes the result count no more" {
        KEPT.copy(quality = "ultra").fits(ratio = 1.25, width = 1280, height = 800) shouldBe false
        KEPT.preset shouldBe PicturePreset.High
    }

    "what the result screen shows comes back from it" {
        KEPT.frames shouldBe FrameStats(fps = 59.5, slowestMs = 21.5)
    }

    // The stored text, pinned: a field renamed in the code must not change it, or every visitor's kept result is lost
    "the kept text, field by field" {
        JSON.encodeToString(StoredTest.serializer(), KEPT) shouldBe
            """{"version":6,"at":1795089600000,"ratio":1.25,"width":1280,"height":800,"voices":212,""" +
            """"fps":59.5,"slowestMs":21.5,"quality":"high"}"""
    }

    "a text kept by version 5 (the oversampling and the fog, no quality) is not read: the test runs again" {
        val old = """{"version":5,"at":1795089600000,"ratio":1.25,"width":1280,"height":800,"voices":212,""" +
            """"fps":59.5,"slowestMs":21.5,"oversample":2,"fog":"low"}"""

        shouldThrow<SerializationException> { JSON.decodeFromString(StoredTest.serializer(), old) }
    }

    "a kept text is read back, also with a field it does not know" {
        val text = """{"version":6,"at":1795089600000,"ratio":1.25,"width":1280,"height":800,"voices":212,""" +
            """"fps":59.5,"slowestMs":21.5,"quality":"high","later":true}"""

        JSON.decodeFromString(StoredTest.serializer(), text) shouldBe KEPT
    }

    "when it was measured, in plain words, in this machine's own time" {
        // noon in the local time, so the day is the same wherever the test runs (months count from 0)
        measuredLine(localNoon(year = 2026, month = 10, day = 19)) shouldBe "Measured on this machine on 19 Nov 2026 at 12:00."
        measuredLine(localNoon(year = 2027, month = 0, day = 2)) shouldBe "Measured on this machine on 2 Jan 2027 at 12:00."
        // minutes with two digits
        measuredLine(localNoon(year = 2027, month = 0, day = 2) + 5 * 60_000.0) shouldBe "Measured on this machine on 2 Jan 2027 at 12:05."
    }

    "a kept result must hold what the test may choose: low, medium or high, never max" {
        KEPT.copy(quality = "low").fits(ratio = 1.25, width = 1280, height = 800) shouldBe true
        KEPT.copy(quality = "medium").fits(ratio = 1.25, width = 1280, height = 800) shouldBe true
        KEPT.copy(quality = "max").fits(ratio = 1.25, width = 1280, height = 800) shouldBe false
    }
})

/** Noon of [day] in [month] (from 0) of [year], in this machine's local time, in milliseconds since 1970. */
private fun localNoon(year: Int, month: Int, day: Int): Double = Date(year, month, day, 12).getTime()
