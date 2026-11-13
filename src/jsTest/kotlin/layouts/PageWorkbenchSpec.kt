/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.key
import kotlinx.coroutines.delay
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

/** The parts the next page gives; null for none. */
private var nextParts: (PageWorkbench.Parts.() -> Unit)? = null

/** A page that gives its parts through [PageWorkbench], as the song page, a tutorial and the docs page do. */
private class GivingPage(ctx: NoProps) : PureComponent(ctx) {
    private val given = nextParts ?: {}

    @Suppress("unused")
    private val workbench = PageWorkbench(this) { given() }

    override fun VDom.render() {
        span { +"PAGE" }
    }
}

@Suppress("FunctionName")
private fun Tag.GivingPage() = comp { GivingPage(it) }

/** Shows the page or not. */
private class PageHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: PageHost? = null
    }

    var shown: Boolean by value(true)

    init {
        current = this
    }

    override fun VDom.render() {
        div {
            key = "host"

            if (shown) {
                GivingPage()
            }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.PageHost() = comp { PageHost(it) }

/** A page's one entry point to the workbench (KLANG-00409): what it fills, what it empties, what it leaves alone. */
class PageWorkbenchSpec : StringSpec({

    val column: DIV.() -> Unit = { +"COLUMN" }
    val settings = KlangWorkbench.Tool(id = "settings", title = "Settings", icon = { sliders_horizontal }) {}
    val help = KlangWorkbench.Tab(id = "help", title = "Help") {}

    val allParts: PageWorkbench.Parts.() -> Unit = {
        column(column)
        tool(id = settings.id, title = settings.title, icon = settings.icon) {}
        bottomTabs(listOf(help))
    }

    fun reset() {
        WorkbenchSlots.clear()
        nextParts = null
    }

    "mounted, a page fills the slots with its parts; unmounted, it empties them" {
        reset()
        nextParts = allParts

        try {
            TestBed.preact(view = { PageHost() }) {
                withClue("mounted: its column, its tool window, its tabs") {
                    eventually {
                        WorkbenchSlots.column() shouldBe column
                        WorkbenchSlots.tools().map { it.id } shouldBe listOf("settings")
                        WorkbenchSlots.bottomTabs().map { it.id } shouldBe listOf("help")
                    }
                }

                PageHost.current!!.shown = false

                withClue("unmounted: the slots empty") {
                    eventually {
                        WorkbenchSlots.column() shouldBe null
                        WorkbenchSlots.tools() shouldBe emptyList()
                        WorkbenchSlots.bottomTabs() shouldBe emptyList()
                    }
                }
            }
        } finally {
            reset()
        }
    }

    "unmounted, it leaves each part a newer page put in since; a slot it left alone stays as it is" {
        reset()
        nextParts = allParts

        try {
            TestBed.preact(view = { PageHost() }) {
                eventually { WorkbenchSlots.bottomTabs().map { it.id } shouldBe listOf("help") }

                // The page opened next has filled the slots before this one goes
                val newerColumn: DIV.() -> Unit = { +"NEWER" }
                val newerTools = listOf(KlangWorkbench.Tool(id = "newer", title = "Newer", icon = { book }) {})
                val newerTabs = listOf(KlangWorkbench.Tab(id = "newer", title = "Newer") {})

                WorkbenchSlots.column(newerColumn)
                WorkbenchSlots.tools(newerTools)
                WorkbenchSlots.bottomTabs(newerTabs)

                PageHost.current!!.shown = false
                eventually { PageHost.current!!.dom?.textContent shouldBe "" }
                delay(50)

                withClue("the newer page's column stays") { WorkbenchSlots.column() shouldBe newerColumn }
                withClue("the newer page's tool windows stay") { WorkbenchSlots.tools() shouldBe newerTools }
                withClue("the newer page's tabs stay") { WorkbenchSlots.bottomTabs() shouldBe newerTabs }
            }
        } finally {
            reset()
        }
    }

    "a page that gives only tabs leaves the column and the tool windows to the others" {
        reset()
        nextParts = { bottomTabs(listOf(help)) }

        try {
            val otherColumn: DIV.() -> Unit = { +"OTHER" }

            WorkbenchSlots.column(otherColumn)

            TestBed.preact(view = { PageHost() }) {
                eventually { WorkbenchSlots.bottomTabs().map { it.id } shouldBe listOf("help") }

                PageHost.current!!.shown = false
                eventually { WorkbenchSlots.bottomTabs() shouldBe emptyList() }

                withClue("a slot it never filled stays as it is") { WorkbenchSlots.column() shouldBe otherColumn }
            }
        } finally {
            reset()
        }
    }
})
