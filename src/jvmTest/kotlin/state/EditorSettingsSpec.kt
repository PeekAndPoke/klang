/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.workshop.storage.StoredDocument

/**
 * The editor's settings as a document (KLANG-00422), read and written on the JVM too: a server keeps the same data
 * class the browser does.
 */
class EditorSettingsSpec : StringSpec({

    val json = StoredDocument.json

    "the settings go to JSON and back unchanged, every field written" {
        val settings = EditorSettings(highlightsOn = false, highlightsPerEvent = 7, highlightStrength = 40, hoverHelp = false)
        val text = json.encodeToString(EditorSettings.serializer(), settings)

        text shouldBe """{"highlightsOn":false,"highlightsPerEvent":7,"highlightStrength":40,"hoverHelp":false}"""
        json.decodeFromString(EditorSettings.serializer(), text) shouldBe settings
    }

    "a document with fields missing takes their defaults; numbers out of range are held by inRange" {
        json.decodeFromString(EditorSettings.serializer(), """{"highlightStrength":40}""") shouldBe
            EditorSettings(highlightsOn = true, highlightsPerEvent = 25, highlightStrength = 40, hoverHelp = true)

        EditorSettings(highlightsPerEvent = 99, highlightStrength = -3).inRange() shouldBe
            EditorSettings(highlightsPerEvent = 50, highlightStrength = 0)
    }

    "the whole document, with its version, is the same text here as in the browser" {
        val document = StoredDocument.serializer(EditorSettings.serializer())
        val text = """{"v":1,"data":{"highlightsOn":false,"highlightsPerEvent":25,"highlightStrength":40,"hoverHelp":true}}"""

        val read = json.decodeFromString(document, text)

        read.v shouldBe 1
        read.data shouldBe EditorSettings(highlightsOn = false, highlightStrength = 40)
        json.encodeToString(document, StoredDocument(v = read.v, data = read.data)) shouldBe text
    }
})
