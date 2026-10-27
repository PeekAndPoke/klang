/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.history
import io.peekandpoke.klang.codemirror.ext.undo
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.coroutines.delay

/** Waits until [check] holds (the follower looks after a short rest), at most [timeoutMs]. */
private suspend fun eventually(timeoutMs: Int = 3000, check: () -> Unit) {
    var waited = 0

    while (true) {
        try {
            check()
            return
        } catch (e: AssertionError) {
            if (waited >= timeoutMs) {
                throw e
            }
        }

        delay(25)
        waited += 25
    }
}

/** A function `level(amount, speed)` whose first parameter has a tool, as a library declares one. */
private val level = KlangSymbol(
    name = "level",
    category = "test",
    origin = KlangSymbol.Origin.Library("test"),
    variants = listOf(
        KlangCallable(
            name = "level",
            params = listOf(
                KlangParam(name = "amount", type = KlangType(simpleName = "Number"), uitools = listOf("TestLevelTool")),
                KlangParam(name = "speed", type = KlangType(simpleName = "Number")),
            ),
        ),
    ),
)

/** The Sprudel tool's eyes in the editor (KLANG-00374): the call around the cursor, its place kept through edits, one undo per drag. */
class ToolCallFollowerSpec : StringSpec({

    KlangUiToolRegistry.register("TestLevelTool", KlangUiTool { })

    val registry = KlangDocsRegistry().apply { register(level) }

    /** An editor with the follower, its analysis made from the text as it stands, and what the follower reported. */
    suspend fun withEditor(doc: String, block: suspend (view: EditorView, reported: MutableList<ToolCallSite?>) -> Unit) {
        val reported = mutableListOf<ToolCallSite?>()
        var view: EditorView? = null

        val follower = ToolCallFollower(
            docProvider = { name -> registry.get(name) },
            analysisProvider = { view?.let { AnalyzedAst.build(it.state.doc.toString(), registry) } },
            onToolCall = { reported.add(it) },
        )

        val parent = document.createElement("div")
        document.body!!.appendChild(parent)

        view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = EditorState.create(
                    jsObject<EditorStateConfig> {
                        this.doc = doc
                        this.extensions = arrayOf<Any>(history(jsObject {}), follower.extension).unsafeCast<Array<Extension>>()
                    }
                )
                this.parent = parent
            }
        )

        try {
            block(view, reported)
        } finally {
            view.destroy()
            parent.remove()
        }
    }

    fun EditorView.cursorTo(pos: Int) {
        dispatch(state.update(jsObject { this.selection = jsObject<dynamic> { this.anchor = pos } }))
    }

    fun EditorView.text(): String = state.doc.toString()

    "the cursor inside a call with a tool: its site, the argument's text; outside it: nothing" {
        withEditor("""level(0.5).fast(2)""") { view, reported ->
            view.cursorTo(7)

            eventually { reported.lastOrNull()?.paramName shouldBe "amount" }
            reported.last()!!.currentValue shouldBe "0.5"

            view.cursorTo(16)

            eventually { reported.last() shouldBe null }
        }
    }

    "a tool's commits write where the argument is now, each over the last; the site stays the same" {
        withEditor("""level(0.5)""") { view, reported ->
            view.cursorTo(7)

            eventually { reported shouldHaveSize 1 }

            val context = reported.single()!!.context()

            context.onCommit("0.75")
            context.onCommit("1")

            view.text() shouldBe "level(1)"

            // Its own edits are no reason to start the tool over
            delay(400)
            reported shouldHaveSize 1
        }
    }

    "an edit by other hands: the tool still writes in its place, and the site starts over" {
        withEditor("""level(0.5)""") { view, reported ->
            view.cursorTo(7)

            eventually { reported shouldHaveSize 1 }

            val context = reported.single()!!.context()

            view.dispatch(view.state.update(jsObject { this.changes = jsObject<dynamic> { this.from = 0; this.insert = "  " } }))

            context.onCommit("0.9")
            view.text() shouldBe "  level(0.9)"

            eventually {
                reported.size shouldBe 2
                reported.last() shouldNotBe null
            }
        }
    }

    "a whole-call write after an edit by other hands, then another tool of the same site: each reads and writes what is there" {
        withEditor("""level(0.5)""") { view, reported ->
            view.cursorTo(7)

            eventually { reported shouldHaveSize 1 }

            val site = reported.single()!!

            val call = site.context().call!!

            // Moved by other hands before the tool writes: it writes where the call is now
            view.dispatch(view.state.update(jsObject { this.changes = jsObject<dynamic> { this.from = 0; this.insert = "  " } }))

            call.onCommitCall(listOf("1400"))
            view.text() shouldBe "  level(1400)"

            val next = site.context()

            next.currentValue shouldBe "1400"
            next.onCommit("\"<800 1200>\"")
            view.text() shouldBe "  level(\"<800 1200>\")"
        }
    }

    "a context held while the cursor moves in the call, then an edit by other hands: it still writes in its place" {
        withEditor("""level(0.5, 2)""") { view, reported ->
            view.cursorTo(7)

            eventually { reported shouldHaveSize 1 }

            val context = reported.single()!!.context()

            // The same site looked up again: nothing new for the tab, and the context's place still followed
            view.cursorTo(11)
            delay(300)
            reported shouldHaveSize 1

            view.dispatch(view.state.update(jsObject { this.changes = jsObject<dynamic> { this.from = 0; this.insert = "  " } }))

            context.onCommit("0.9")
            view.text() shouldBe "  level(0.9, 2)"
        }
    }

    "a named argument, another one written in front of it later: the tool still writes its own" {
        withEditor("""level(amount = 0.5)""") { view, reported ->
            view.cursorTo(16)

            eventually { reported shouldHaveSize 1 }

            val context = reported.single()!!.context()

            view.dispatch(view.state.update(jsObject { this.changes = jsObject<dynamic> { this.from = 6; this.insert = "speed = 2, " } }))

            context.onCommit("0.9")
            view.text() shouldBe "level(speed = 2, amount = 0.9)"
        }
    }

    "an empty call: the tool writes its first argument; a comment in it stays" {
        withEditor("level( /* soon */ )") { view, reported ->
            view.cursorTo(7)

            eventually { reported shouldHaveSize 1 }

            reported.single()!!.currentValue shouldBe null
            reported.single()!!.context().onCommit("0.3")
            view.text() shouldBe "level( /* soon */ 0.3)"
        }
    }

    "a tool writes a parameter without a tool into an empty call: the site stays, the cursor did not move" {
        withEditor("level()") { view, reported ->
            view.cursorTo(6)

            eventually { reported shouldHaveSize 1 }

            reported.single()!!.context().call!!.onCommitCall(listOf(null, "2"))
            view.text() shouldBe "level(speed = 2)"

            delay(1000)
            reported shouldHaveSize 1
        }
    }

    "a look asked for by other hands, the tool writing before it ran: the site stays all the same" {
        withEditor("level()") { view, reported ->
            view.cursorTo(6)

            eventually { reported shouldHaveSize 1 }

            val call = reported.single()!!.context().call!!

            // Within the follower's rest: the look the edit asked for runs on what the tool wrote
            view.dispatch(view.state.update(jsObject { this.changes = jsObject<dynamic> { this.from = 0; this.insert = "  " } }))
            call.onCommitCall(listOf(null, "2"))
            view.text() shouldBe "  level(speed = 2)"

            delay(1000)
            reported.last() shouldNotBe null
        }
    }

    "named arguments out of order, made positional by a whole-call write: the next tool reads its own parameter" {
        withEditor("""level(speed = 2, amount = 0.5)""") { view, reported ->
            view.cursorTo(27)

            eventually { reported shouldHaveSize 1 }

            val site = reported.single()!!

            site.context().call!!.onCommitCall(listOf("0.7", "2"))
            view.text() shouldBe "level(0.7, 2)"

            site.context().currentValue shouldBe "0.7"
        }
    }

    "a vararg parameter: the tool reads and writes the argument the cursor is on, not the first" {
        val notes = KlangSymbol(
            name = "notes",
            category = "test",
            origin = KlangSymbol.Origin.Library("test"),
            variants = listOf(
                KlangCallable(
                    name = "notes",
                    params = listOf(
                        KlangParam(name = "n", type = KlangType(simpleName = "String"), isVararg = true, uitools = listOf("TestLevelTool")),
                    ),
                ),
            ),
        )

        registry.register(notes)

        withEditor("notes(\"a\", \"b\")") { view, reported ->
            view.cursorTo(12)

            eventually { reported shouldHaveSize 1 }

            val context = reported.single()!!.context()

            context.currentValue shouldBe "\"b\""
            context.onCommit("\"c\"")
            view.text() shouldBe "notes(\"a\", \"c\")"
        }
    }

    "an empty call with only space in it: the tool's value takes the space" {
        withEditor("level(   )") { view, reported ->
            view.cursorTo(7)

            eventually { reported shouldHaveSize 1 }

            reported.single()!!.context().onCommit("0.3")
            view.text() shouldBe "level(0.3)"
        }
    }

    "the call's parenthesis deleted by other hands: a held context writes nowhere" {
        withEditor("""level(0.5).fast(2)""") { view, reported ->
            view.cursorTo(7)

            eventually { reported shouldHaveSize 1 }

            val context = reported.single()!!.context()

            // `(0.5).fast` goes: the next `(` is fast's, and it must not take the value
            view.dispatch(view.state.update(jsObject { this.changes = jsObject<dynamic> { this.from = 5; this.to = 15 } }))
            view.text() shouldBe "level(2)"

            context.onCommit("0.9")
            view.text() shouldBe "level(2)"
        }
    }

    "one drag is one undo: the tool's quick commits come back as one" {
        withEditor("""level(0.5)""") { view, reported ->
            view.cursorTo(7)

            eventually { reported shouldHaveSize 1 }

            val context = reported.single()!!.context()

            context.onCommit("0.6")
            context.onCommit("0.7")
            context.onCommit("0.8")

            undo(view)

            view.text() shouldBe "level(0.5)"
        }
    }
})
