/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.html.div
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

/** A function with one tooled parameter, as a library declares it. */
private fun tooled(name: String, tool: String) = KlangSymbol(
    name = name,
    category = "test",
    origin = KlangSymbol.Origin.Library("test"),
    variants = listOf(
        KlangCallable(
            name = name,
            params = listOf(KlangParam(name = "amount", type = KlangType(simpleName = "Number"), uitools = listOf(tool))),
        ),
    ),
)

/**
 * The right-click's item for the Sprudel tool (KLANG-00374 step 3): inside a call with a registered tool it puts the
 * cursor at the click and asks the host to show the tab; no item for a call without one, nor on a stale analysis.
 */
class DslEditorToolMenuSpec : StringSpec({

    KlangUiToolRegistry.register("TestMenuTool", KlangUiTool { })

    val registry = KlangDocsRegistry().apply {
        register(tooled(name = "level", tool = "TestMenuTool"))
        register(tooled(name = "ghost", tool = "NoSuchTool"))
    }

    class Host(val analysisOf: (String) -> AnalyzedAst?) {
        val shown = mutableListOf<Int>()
        val popups = PopupsManager(PopupsManager.Settings(popupRenderer = PopupsManager.DefaultPopupFactory))
        var view: EditorView? = null

        val wiring = dslEditorExtension(
            docProvider = { name -> registry.get(name) },
            hoverPopup = HoverPopupCtrl(popups = popups, showDelayMs = 10, hideDelayMs = 10),
            hoverContent = { symbol, _ -> div { +symbol.name } },
            popups = popups,
            onNavigate = { _, _ -> },
            analysisProvider = { view?.let { analysisOf(it.state.doc.toString()) } },
            onShowTool = { pos -> shown.add(pos) },
        )
    }

    suspend fun withEditor(host: Host, doc: String, block: suspend (EditorView) -> Unit) {
        val parent = document.createElement("div")
        document.body!!.appendChild(parent)

        val view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = EditorState.create(
                    jsObject<EditorStateConfig> {
                        this.doc = doc
                        this.extensions = arrayOf<Any>(host.wiring.extension).unsafeCast<Array<Extension>>()
                    }
                )
                this.parent = parent
            }
        )

        host.view = view

        try {
            block(view)
        } finally {
            view.destroy()
            parent.remove()
        }
    }

    /** A right-click on the text at [pos]. */
    fun EditorView.rightClick(pos: Int) {
        val at = coordsAtPos(pos)!!

        contentDOM.dispatchEvent(
            MouseEvent(
                "contextmenu",
                MouseEventInit(
                    clientX = (at.left + 1).toInt(),
                    clientY = ((at.top + at.bottom) / 2).toInt(),
                    bubbles = true,
                    cancelable = true,
                ),
            )
        )
    }

    "inside a call with a tool: the item, and clicking it puts the cursor there and shows the tab" {
        val host = Host(analysisOf = { AnalyzedAst.build(it, registry) })

        withEditor(host = host, doc = "level(0.5)") { view ->
            view.rightClick(pos = 7)
            delay(50)

            val menu = host.popups().single()

            TestBed.preact(view = { div { menu.view(this, menu) } }) { root ->
                val item = root.single().querySelectorAll(".item").let { items ->
                    (0 until items.length).map { items.item(it) as HTMLElement }
                }.single()

                item.textContent shouldBe "Open the Sprudel tool: level"
                item.click()
            }

            view.state.selection.main.head shouldBe 7
            host.shown shouldContainExactly listOf(7)
            host.popups().size shouldBe 0
        }
    }

    "no item: a call whose tool is not registered, a stale analysis" {
        withClue("not registered") {
            val host = Host(analysisOf = { AnalyzedAst.build(it, registry) })

            withEditor(host = host, doc = "ghost(0.5)") { view ->
                view.rightClick(pos = 7)
                delay(50)

                host.popups().size shouldBe 0
            }
        }

        withClue("an analysis of the code before an edit") {
            // Its offsets still fall inside the call: only its being out of date may keep the item away
            val host = Host(analysisOf = { AnalyzedAst.build("$it ", registry) })

            withEditor(host = host, doc = "level(0.5)") { view ->
                view.rightClick(pos = 7)
                delay(50)

                host.popups().size shouldBe 0
            }
        }
    }
})
