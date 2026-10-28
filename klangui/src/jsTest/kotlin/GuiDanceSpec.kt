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
    GuiDance.Arc(id = "first-song", title = "First song", steps = listOf("song.play", "motor.menu", "drawer.songs")),
    GuiDance.Arc(id = "docs", title = "The docs", steps = listOf("motor.menu", "drawer.docs")),
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

    "a finished arc is told, once, after it is kept; an arc's other steps tell nothing" {
        val store = GuiDance.MemoryStore()
        val told = mutableListOf<Pair<String, Set<String>>>()
        lateinit var dance: GuiDance

        dance = GuiDance(arcs = arcs, store = store, onArcDone = { told.add(it.id to store.loadDone()) })

        dance.used("song.play")
        dance.used("motor.menu")
        told shouldContainExactly emptyList()

        dance.used("drawer.songs")
        dance.used("drawer.songs")
        told shouldContainExactly listOf("first-song" to setOf("first-song"))
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

    "the walkthroughs done, in their order, and how many are still to come" {
        val dance = GuiDance(arcs = arcs, store = GuiDance.MemoryStore())

        dance.doneArcs() shouldBe emptyList()
        dance.remaining() shouldBe 2

        dance.used("song.play")
        dance.used("motor.menu")

        // An arc half done is still to come
        dance.remaining() shouldBe 2

        dance.used("drawer.songs")

        dance.doneArcs().map { it.title } shouldContainExactly listOf("First song")
        dance.remaining() shouldBe 1
    }

    "muted, nothing is lit and nothing used counts; the mute is kept; when it runs out the step lights again" {
        var clock = 1_000.0
        val store = GuiDance.MemoryStore()
        val dance = GuiDance(arcs = arcs, store = store, now = { clock })

        dance.mute(forMs = 500.0)

        dance.lit() shouldBe null
        dance.mutedUntil() shouldBe 1_500.0
        store.loadMutedUntil() shouldBe 1_500.0

        dance.used("song.play")
        clock = 1_499.0
        dance.refresh()
        dance.lit() shouldBe null

        clock = 1_500.0
        dance.refresh()

        dance.mutedUntil() shouldBe null
        // Where the arc stood: the use while muted did not count
        dance.lit() shouldBe "song.play"
    }

    "a done arc reopened comes back in its place: an earlier one is danced again from its first step, and kept so" {
        val store = GuiDance.MemoryStore(setOf("first-song"))
        val dance = GuiDance(arcs = arcs, store = store)

        dance.used("motor.menu")
        dance.lit() shouldBe "drawer.docs"

        dance.reopen("first-song")

        dance.lit() shouldBe "song.play"
        dance.doneArcs() shouldBe emptyList()
        store.loadDone() shouldBe emptySet()
    }

    "reopening an arc after the current one, or one not done, leaves the lit step where it is" {
        val store = GuiDance.MemoryStore(setOf("docs"))
        val dance = GuiDance(arcs = arcs, store = store)

        dance.used("song.play")
        dance.reopen("docs")

        dance.lit() shouldBe "motor.menu"
        store.loadDone() shouldBe emptySet()

        dance.reopen("first-song")

        dance.lit() shouldBe "motor.menu"
    }

    "a mute kept from an earlier visit holds; unmuting lights the step at once" {
        val store = GuiDance.MemoryStore(mutedUntil = 5_000.0)
        val dance = GuiDance(arcs = arcs, store = store, now = { 1_000.0 })

        dance.lit() shouldBe null

        dance.unmute()

        dance.lit() shouldBe "song.play"
        store.loadMutedUntil() shouldBe 0.0
    }
})
