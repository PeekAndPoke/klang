/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/** Two arcs, the second lighting the Motor button again, as the app's later arcs may. */
private val arcs = listOf(
    GuiDance.Arc(id = "first-song", steps = listOf("song.play", "motor.menu", "drawer.songs")),
    GuiDance.Arc(id = "docs", steps = listOf("motor.menu", "drawer.docs")),
)

/** The Gui-dance (KLANG-00350): one lit step, an arc moving on by use, an arc kept only when finished. */
class GuiDanceSpec : StringSpec({

    "on a first visit the first arc's first step is lit" {
        val dance = GuiDance(arcs = arcs, store = GuiDance.MemoryStore())

        dance.lit() shouldBe "song.play"
    }

    "using the lit step lights the next; using another step changes nothing" {
        val dance = GuiDance(arcs = arcs, store = GuiDance.MemoryStore())

        dance.used("drawer.songs")
        dance.lit() shouldBe "song.play"

        dance.used("song.play")
        dance.lit() shouldBe "motor.menu"

        dance.used("song.play")
        dance.lit() shouldBe "motor.menu"
    }

    "the lit step is published to whoever listens" {
        val dance = GuiDance(arcs = arcs, store = GuiDance.MemoryStore())
        val seen = mutableListOf<String?>()

        dance.lit.subscribeToStream { seen.add(it) }
        dance.used("song.play")
        dance.used("motor.menu")

        seen shouldContainExactly listOf("song.play", "motor.menu", "drawer.songs")
    }

    "a finished arc is kept, and the next arc lights its first step, the same Motor button again" {
        val store = GuiDance.MemoryStore()
        val dance = GuiDance(arcs = arcs, store = store)

        dance.used("song.play")
        dance.used("motor.menu")
        dance.used("drawer.songs")

        store.loadDone() shouldBe setOf("first-song")
        dance.currentArc()?.id shouldBe "docs"
        dance.lit() shouldBe "motor.menu"
    }

    "after the last arc nothing is lit, and nothing used changes that" {
        val dance = GuiDance(arcs = arcs, store = GuiDance.MemoryStore(setOf("first-song")))

        dance.used("motor.menu")
        dance.used("drawer.docs")

        dance.lit() shouldBe null
        dance.currentArc() shouldBe null

        dance.used("song.play")
        dance.lit() shouldBe null
    }

    "an arc left in the middle is not kept: the next visit starts it over" {
        val store = GuiDance.MemoryStore()

        GuiDance(arcs = arcs, store = store).apply {
            used("song.play")
            used("motor.menu")
        }

        store.loadDone() shouldBe emptySet()
        GuiDance(arcs = arcs, store = store).lit() shouldBe "song.play"
    }

    "a kept arc does not come back on the next visit" {
        val store = GuiDance.MemoryStore(setOf("first-song"))

        GuiDance(arcs = arcs, store = store).lit() shouldBe "motor.menu"
    }

    "the arcs run in their order: a later arc kept does not skip an earlier one" {
        val store = GuiDance.MemoryStore(setOf("docs"))

        GuiDance(arcs = arcs, store = store).lit() shouldBe "song.play"
    }
})
