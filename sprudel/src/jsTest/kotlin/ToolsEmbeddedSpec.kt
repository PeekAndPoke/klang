/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.sprudel.lang.parser.MnNode
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.html.Tag
import kotlinx.html.div

/**
 * The tools live in the Sprudel tab only (KLANG-00374 step 3, the maintainer, 2026-10-27: "Panel only"): every tool
 * a sprudel KDoc names renders embedded, and a mini-notation editor in a panel writes every change at once, with no
 * Cancel/Reset/Update bar, whatever the modal's auto-update switch says.
 */
class ToolsEmbeddedSpec : StringSpec({

    registerSprudelUiTools()

    "every tool a sprudel KDoc names is registered and renders embedded" {
        val names = generatedSprudelDocs.values
            .flatMap { it.variants.filterIsInstance<KlangCallable>() }
            .flatMap { callable -> callable.uitools + callable.params.flatMap { it.uitools } }
            .distinct()

        names.size shouldBeGreaterThan 40

        val notEmbedded = names.filter { name ->
            withClue(name) { KlangUiToolRegistry.get(name) shouldNotBe null }

            KlangUiToolRegistry.get(name) !is KlangUiToolEmbeddable
        }

        notEmbedded.shouldBeEmpty()
    }

    "the mini-notation, note, scale degree and scale tools in a panel: no Cancel, Reset or Update; in a modal, the bar" {
        val manager = KeysManager(platform = KeyPlatform.Pc).apply { define(KlangKeyBindings) }
        val tools = listOf("SprudelGainSequenceEditor", "SprudelNoteEditor", "SprudelScaleDegreeEditor", "SprudelScaleEditor")

        /** The texts of the buttons shown. */
        fun buttons(): List<String> = document.querySelectorAll(".ui.button").let { all ->
            (0 until all.length).mapNotNull { all.item(it)?.textContent?.trim() }
        }

        try {
            tools.forEach { name ->
                val tool = KlangUiToolRegistry.get(name) as KlangUiToolEmbeddable
                val toolCtx = KlangUiToolContext(
                    symbol = KlangSymbol(name = "n", variants = emptyList(), category = "test"),
                    paramName = "pattern",
                    currentValue = "\"c4 e4\"",
                    onCommit = {},
                    onCancel = {},
                )

                withClue("$name embedded") {
                    TestBed.preact(appSetup = { keys(manager) }, view = { div { with(tool) { renderEmbedded(toolCtx) } } }) {
                        eventually { (document.body?.textContent?.contains("c4") == true) shouldBe true }
                        buttons().filter { it in setOf("Cancel", "Reset", "Update") }.shouldBeEmpty()
                    }
                }

                withClue("$name in a modal") {
                    TestBed.preact(appSetup = { keys(manager) }, view = { div { with(tool) { render(toolCtx) } } }) {
                        eventually { buttons() shouldContain "Cancel" }
                    }
                }
            }
        } finally {
            manager.dispose()
        }
    }

    "a mini-notation editor in a panel: each change written at once (text, staff, undo, redo), no bar; in a modal without auto-update, neither" {
        val autoUpdateWas = KlangToolAutoUpdate()
        // The bar's buttons have keys (Esc is Cancel): the app's key map
        val manager = KeysManager(platform = KeyPlatform.Pc).apply { define(KlangKeyBindings) }

        if (autoUpdateWas) {
            KlangToolAutoUpdate.toggle()
        }

        try {
            listOf(true, false).forEach { embedded ->
                val commits = mutableListOf<String>()
                val toolCtx = KlangUiToolContext(
                    symbol = KlangSymbol(name = "n", variants = emptyList(), category = "test"),
                    paramName = "pattern",
                    currentValue = "\"a b\"",
                    onCommit = { commits.add(it) },
                    onCancel = {},
                )

                TestBed.preact(appSetup = { keys(manager) }, view = { TestPanelMnEditor(toolCtx = toolCtx, embedded = embedded) }) {
                    eventually { TestPanelMnEditor.last shouldNotBe null }

                    val editor = TestPanelMnEditor.last!!

                    // The text field, a staff edit (a node replaced, a node removed), the tool's undo
                    editor.change(newText = "c d")
                    editor.replaceFirst(value = "e")
                    editor.removeFirst()
                    editor.undo()
                    editor.redo()

                    withClue("embedded = $embedded") {
                        commits shouldContainExactly
                            (if (embedded) listOf("\"c d\"", "\"e d\"", "\"d\"", "\"e d\"", "\"d\"") else emptyList())
                        eventually { (document.querySelector(".ui.button") != null) shouldBe !embedded }
                    }
                }

                TestPanelMnEditor.last = null
            }
        } finally {
            manager.dispose()

            if (KlangToolAutoUpdate() != autoUpdateWas) {
                KlangToolAutoUpdate.toggle()
            }
        }
    }
})

/** The editors' shared base with its text and its bottom bar, and a change as the text field makes one. */
private class TestPanelMnEditor(ctx: Ctx<Props>) : MnPatternEditorBase<TestPanelMnEditor.Props>(ctx) {

    companion object {
        var last: TestPanelMnEditor? = null
    }

    data class Props(override val toolCtx: KlangUiToolContext, override val embedded: Boolean) : BaseProps

    init {
        last = this
    }

    fun change(newText: String) {
        text = newText
        liveUpdate()
    }

    fun replaceFirst(value: String) {
        replaceNode(targetId = collectAtoms(pattern!!).first().id, replacement = MnNode.Atom(value = value))
    }

    fun removeFirst() {
        removeNode(targetId = collectAtoms(pattern!!).first().id)
    }

    override fun VDom.render() {
        div {
            +text
            renderBottomBar()
        }
    }
}

@Suppress("FunctionName")
private fun Tag.TestPanelMnEditor(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(TestPanelMnEditor.Props(toolCtx = toolCtx, embedded = embedded)) { TestPanelMnEditor(it) }
