/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.workshop.storage.StoredDocument

/**
 * The stored sections' data classes (KLANG-00422) as documents, read and written on the JVM too: a server keeps the
 * same classes the browser does. Each goes to JSON and back unchanged, and an empty document reads as a first visit.
 */
class StoredStateSpec : StringSpec({

    val json = StoredDocument.json

    "the workbench's layout" {
        val layout = BenchLayout(
            pinned = setOf("help", "tools"),
            open = "tools",
            bottomOpen = false,
            bottomPinned = false,
            bottomHeight = 412.5,
            bottomTab = "console",
        )
        val text = json.encodeToString(BenchLayout.serializer(), layout)

        text shouldBe
            """{"pinned":["help","tools"],"open":"tools","bottomOpen":false,"bottomPinned":false,"bottomHeight":412.5,"bottomTab":"console"}"""
        json.decodeFromString(BenchLayout.serializer(), text) shouldBe layout

        withClue("a first visit: the bottom panel open and docked") {
            json.decodeFromString(BenchLayout.serializer(), "{}") shouldBe
                BenchLayout(pinned = emptySet(), open = null, bottomOpen = true, bottomPinned = true, bottomHeight = null, bottomTab = null)
        }
        withClue("nothing open is written, so a window closed is kept as closed") {
            json.encodeToString(BenchLayout.serializer(), BenchLayout()) shouldBe
                """{"pinned":[],"open":null,"bottomOpen":true,"bottomPinned":true,"bottomHeight":null,"bottomTab":null}"""
        }
    }

    "a layout's height that is no number is the bench's own; any other stays" {
        BenchLayout(bottomHeight = Double.NaN).inRange() shouldBe BenchLayout()
        BenchLayout(bottomHeight = Double.POSITIVE_INFINITY).inRange() shouldBe BenchLayout()
        BenchLayout(bottomHeight = 120.0, pinned = setOf("help")).inRange() shouldBe BenchLayout(bottomHeight = 120.0, pinned = setOf("help"))
    }

    "the look worn with the slots, a look of the user's own, the tools' switch" {
        val choice = LookChoice(worn = "own-1", slots = listOf("own-1", null, "classic"))
        val choiceText = json.encodeToString(LookChoice.serializer(), choice)

        choiceText shouldBe """{"worn":"own-1","slots":["own-1",null,"classic"]}"""
        json.decodeFromString(LookChoice.serializer(), choiceText) shouldBe choice
        withClue("a first visit: no look chosen, the slots never arranged") {
            json.decodeFromString(LookChoice.serializer(), "{}") shouldBe LookChoice(worn = null, slots = null)
        }

        val look = OwnLook(name = "Rust", author = "Someone", values = mapOf("--kl-accent" to "#b04fd6"))
        val lookText = json.encodeToString(OwnLook.serializer(), look)

        lookText shouldBe """{"name":"Rust","author":"Someone","values":{"--kl-accent":"#b04fd6"}}"""
        json.decodeFromString(OwnLook.serializer(), lookText) shouldBe look
        json.decodeFromString(OwnLook.serializer(), "{}") shouldBe OwnLook(name = "", author = "", values = emptyMap())

        json.encodeToString(ToolSettings.serializer(), ToolSettings(autoUpdate = false)) shouldBe """{"autoUpdate":false}"""
        withClue("a first visit: a tool writes each change at once") {
            json.decodeFromString(ToolSettings.serializer(), "{}") shouldBe ToolSettings(autoUpdate = true)
        }
    }

    "the MIDI play code and this machine's MIDI setup" {
        json.encodeToString(MidiPlay.serializer(), MidiPlay(code = "x => x")) shouldBe """{"code":"x => x"}"""
        withClue("a first visit: no play code of the user's") {
            json.decodeFromString(MidiPlay.serializer(), "{}") shouldBe MidiPlay(code = null)
        }

        val desk = MidiDesk(loadout = listOf("lead", "\"saw\""), keyLayout = "French")
        val text = json.encodeToString(MidiDesk.serializer(), desk)

        text shouldBe """{"loadout":["lead","\"saw\""],"keyLayout":"French"}"""
        json.decodeFromString(MidiDesk.serializer(), text) shouldBe desk
        withClue("a first visit: an empty loadout, the layout Automatic") {
            json.decodeFromString(MidiDesk.serializer(), "{}") shouldBe MidiDesk(loadout = emptyList(), keyLayout = null)
        }
    }

    "what is kept of a song" {
        val song = SongKept(code = "sound(\"bd\")", title = "Mine", rpm = 37.5, bookmarks = mapOf(1 to 12, 9 to 3))
        val text = json.encodeToString(SongKept.serializer(), song)

        text shouldBe """{"code":"sound(\"bd\")","title":"Mine","rpm":37.5,"bookmarks":{"1":12,"9":3}}"""
        json.decodeFromString(SongKept.serializer(), text) shouldBe song
        withClue("nothing changed: the song's own code, title and tempo, no bookmarks") {
            json.decodeFromString(SongKept.serializer(), "{}") shouldBe
                SongKept(code = null, title = null, rpm = null, bookmarks = emptyMap())
        }
    }

    "what was opened last" {
        val opened = LastOpened(
            entries = listOf(
                LastOpened.Entry(kind = LastOpened.Kind.Tutorial, id = "l", title = "Lesson", at = 3.5, track = "t"),
                LastOpened.Entry(kind = LastOpened.Kind.Song, id = "s", title = "Song", at = 2.5, rpm = 37.2),
            )
        )
        val text = json.encodeToString(LastOpened.serializer(), opened)

        text shouldBe """{"entries":[""" +
            """{"kind":"Tutorial","id":"l","title":"Lesson","at":3.5,"rpm":null,"track":"t"},""" +
            """{"kind":"Song","id":"s","title":"Song","at":2.5,"rpm":37.2,"track":null}]}"""
        json.decodeFromString(LastOpened.serializer(), text) shouldBe opened
        json.decodeFromString(LastOpened.serializer(), "{}") shouldBe LastOpened(entries = emptyList())
    }

    "this machine's output: the volume held in 0.0 to 1.0, one that is no number at full" {
        json.encodeToString(AudioOutput.serializer(), AudioOutput(volume = 0.25)) shouldBe """{"volume":0.25}"""
        json.decodeFromString(AudioOutput.serializer(), """{"volume":0.25}""") shouldBe AudioOutput(volume = 0.25)
        json.decodeFromString(AudioOutput.serializer(), "{}") shouldBe AudioOutput(volume = 1.0)

        AudioOutput(volume = 0.25).inRange() shouldBe AudioOutput(volume = 0.25)
        AudioOutput(volume = 3.0).inRange() shouldBe AudioOutput(volume = 1.0)
        AudioOutput(volume = -0.5).inRange() shouldBe AudioOutput(volume = 0.0)
        AudioOutput(volume = Double.NaN).inRange() shouldBe AudioOutput(volume = 1.0)
    }

    "the random seeds: none until one is made" {
        json.encodeToString(RandomSeeds.serializer(), RandomSeeds(metal = -42)) shouldBe """{"metal":-42}"""
        json.decodeFromString(RandomSeeds.serializer(), """{"metal":-42}""") shouldBe RandomSeeds(metal = -42)
        json.decodeFromString(RandomSeeds.serializer(), "{}") shouldBe RandomSeeds(metal = null)
    }
})
