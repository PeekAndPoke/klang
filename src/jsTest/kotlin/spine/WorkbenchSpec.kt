/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.state.BenchLayout
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/**
 * The workbench's layout as a stored section (KLANG-00422): the tab's own, a new tab beginning with the machine's
 * last, and the layout kept before read once.
 */
class WorkbenchSpec : StringSpec({

    /** A tab of one browser: the browser's storage shared, the tab's storage its own. */
    fun tab(site: MemoryKeyValueStore, own: MemoryKeyValueStore = MemoryKeyValueStore()) =
        Workbench(stores = SectionStores(user = site, machine = site, tab = own))

    "a first visit: the bottom panel open and docked, nothing pinned" {
        tab(site = MemoryKeyValueStore()).layout() shouldBe
            BenchLayout(pinned = emptySet(), open = null, bottomOpen = true, bottomPinned = true, bottomHeight = null, bottomTab = null)
    }

    "a tab's layout lives through a reload, and is no other tab's" {
        val site = MemoryKeyValueStore()
        val ownA = MemoryKeyValueStore()
        val a = tab(site = site, own = ownA)
        val b = tab(site = site)

        a.arranged(BenchLayout(pinned = setOf("help"), open = "help", bottomOpen = false, bottomHeight = 420.0, bottomTab = "midi"))

        withClue("the other tab") { b.layout() shouldBe BenchLayout() }
        withClue("after a reload") {
            tab(site = site, own = ownA).layout() shouldBe
                BenchLayout(pinned = setOf("help"), open = "help", bottomOpen = false, bottomHeight = 420.0, bottomTab = "midi")
        }

        a.arranged(a.layout().copy(open = null, bottomTab = null))
        withClue("a window closed and the first tab shown again: kept so") {
            tab(site = site, own = ownA).layout() shouldBe BenchLayout(pinned = setOf("help"), bottomOpen = false, bottomHeight = 420.0)
        }
    }

    "a new tab begins with the layout arranged last in any tab, and is its own from then" {
        val site = MemoryKeyValueStore()
        val a = tab(site = site)

        a.arranged(BenchLayout(pinned = setOf("help")))

        val ownB = MemoryKeyValueStore()
        val b = tab(site = site, own = ownB)

        withClue("the new tab") { b.layout() shouldBe BenchLayout(pinned = setOf("help")) }

        a.arranged(BenchLayout(pinned = setOf("help", "tools"), bottomOpen = false))

        withClue("the new tab reloaded, never arranged by hand: still as it began") {
            tab(site = site, own = ownB).layout() shouldBe BenchLayout(pinned = setOf("help"))
        }
        withClue("a third tab") { tab(site = site).layout() shouldBe BenchLayout(pinned = setOf("help", "tools"), bottomOpen = false) }
    }

    "a new tab with nothing arranged anywhere keeps the first visit's layout as its own" {
        val site = MemoryKeyValueStore()
        val own = MemoryKeyValueStore()

        tab(site = site, own = own)
        tab(site = site).arranged(BenchLayout(pinned = setOf("help")))

        tab(site = site, own = own).layout() shouldBe BenchLayout()
    }

    "the layout kept before the section existed is the machine's last, read once: a tab begins with it" {
        val old = """{"pinned":["help","tools"],"open":"tools","bottomOpen":false,"bottomPinned":true,"bottomHeight":300.5,"bottomTab":"console"}"""
        val site = MemoryKeyValueStore(initial = mapOf(StorageKeys.WORKBENCH_LAYOUT to old))

        tab(site = site).layout() shouldBe BenchLayout(
            pinned = setOf("help", "tools"),
            open = "tools",
            bottomOpen = false,
            bottomPinned = true,
            bottomHeight = 300.5,
            bottomTab = "console",
        )

        withClue("the old key") { site.read(StorageKeys.WORKBENCH_LAYOUT) shouldBe null }
        withClue("what was kept counts as chosen, also where it is the default") {
            site.read(StorageKeys.WORKBENCH) shouldBe
                """{"v":1,"data":{"pinned":["help","tools"],"open":"tools","bottomOpen":false,"bottomPinned":true,"bottomHeight":300.5,"bottomTab":"console"}}"""
        }
    }

    "an old layout that cannot be read: the first visit's layout, and the old text stays" {
        val site = MemoryKeyValueStore(initial = mapOf(StorageKeys.WORKBENCH_LAYOUT to "[1, 2]"))

        tab(site = site).layout() shouldBe BenchLayout()
        site.read(StorageKeys.WORKBENCH_LAYOUT) shouldBe "[1, 2]"
    }

    "a height that is no number is the bench's own" {
        val bench = tab(site = MemoryKeyValueStore())

        bench.arranged(BenchLayout(bottomHeight = Double.NaN))

        bench.layout().bottomHeight shouldBe null
    }
})
