/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.MapMode
import io.peekandpoke.klang.codemirror.ext.ViewUpdate
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.CallText
import io.peekandpoke.klang.script.intel.alignArgsToParams
import io.peekandpoke.klang.script.intel.serializeCallArgs
import io.peekandpoke.klang.script.intel.toolCallAt
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolCall
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.window

/**
 * The user event of a tool's own edits. CodeMirror's history joins adjacent edits into one undo step only for events
 * named `input.type...` or `delete...` (its `joinableUserEvent`), each within half a second of the last: so one drag
 * is one undo. `delete`, not `input.type`, because the completion opens on typing (`input.type`) and must not on a
 * tool's edit; nothing here reads `delete.tool` as a deletion (the completion reacts to `delete.backward` only).
 */
const val TOOL_EDIT_EVENT = "delete.tool"

/** How long the cursor rests before the call around it is looked up: typing does not look up on every key. */
private const val FOLLOW_DELAY_MS = 80

/** How often the follower asks again for an analysis of the code as it stands, before it waits for the next edit. */
private const val FOLLOW_TRIES = 20

/**
 * The call around the cursor whose tools the Sprudel tool shows (KLANG-00374), as the editor found it.
 *
 * [callKey] names the call and the parameter; [key] adds the state of the code it read: it changes with an edit by
 * other hands too, and then the tool starts over. The tool's own edits keep both. [context] makes a tool's context:
 * it reads the argument as it is now, and its commits write where the argument (or, for a whole-call tool, the
 * argument list) is now, however the code moved since.
 */
class ToolCallSite internal constructor(
    val symbol: KlangSymbol,
    val callable: KlangCallable,
    val paramName: String,
    val tools: List<Pair<String, KlangUiTool>>,
    /** The argument's source text when the site was found; null when the call had none. */
    val currentValue: String?,
    /** Where the argument starts (or would be written), for the tool's source locations. */
    val argFrom: Int,
    val callKey: String,
    val key: String,
    private val makeContext: () -> KlangUiToolContext,
) {
    fun context(): KlangUiToolContext = makeContext()
}

/** The opening parenthesis of the call a site shows, kept on it through every edit; null once it is deleted. */
private class Anchor(var pos: Int?) {
    /** The call's parentheses in [source] now. */
    fun parentheses(source: String): IntRange? = pos?.let { CallText.parentheses(source, it) }
}

/**
 * Follows the cursor for the Sprudel tool (KLANG-00374; the maintainer, 2026-10-27: "Whenever the cursor is inside the
 * parenteses if a function that supports tools we should so the tool"): after a short rest it looks up the call around
 * the cursor ([toolCallAt]) and hands [onToolCall] its [ToolCallSite] when it changed, or null outside any such call.
 * It looks after a move of the cursor or an edit by other hands, never after a tool's own edit: what the tool wrote
 * (`adsr(decay = 0.3)`, a parameter without a tool) must not take the tool away from under the hand.
 *
 * It looks up only on an analysis of the code as it stands ([analysisProvider] is rebuilt a moment after typing); until
 * then it asks again, [FOLLOW_TRIES] times. A site keeps one position, its call's `(`, moved with every edit; the
 * argument and the argument list are found again from the text ([CallText]) at each read and each commit, by the
 * parameter's name or the argument's place. A tool's own commits carry [TOOL_EDIT_EVENT].
 */
class ToolCallFollower(
    private val docProvider: (String) -> KlangSymbol?,
    private val analysisProvider: () -> AnalyzedAst?,
    private val onToolCall: (ToolCallSite?) -> Unit,
) {
    /** The open parenthesis of the site shown; moved with every edit. */
    private var anchor: Anchor? = null

    /** Counts the edits by other hands: a site's key holds it, so the tool starts over after one. */
    private var foreignEdits = 0

    private var lastKey: String? = null

    /** The parameter of the site shown. */
    private var lastParam: String? = null

    /** The last edit was the tool's own, and the cursor has not moved since: the site stays while the cursor is in it. */
    private var ownTextLast = false

    private var timer: Int? = null

    val extension: Extension = EditorView.updateListener.of { update -> onUpdate(update) }.unsafeCast<Extension>()

    private fun onUpdate(update: ViewUpdate) {
        val foreign = update.docChanged && update.transactions.none { it.isUserEvent(TOOL_EDIT_EVENT) }

        if (update.docChanged) {
            // An insertion right before the parenthesis moves it on; the parenthesis deleted, the site has no place
            anchor?.let { kept -> kept.pos = kept.pos?.let { update.changes.mapPos(it, 1, MapMode.TrackAfter) } }
        }

        if (foreign) {
            foreignEdits++
        }

        when {
            foreign || update.selectionSet -> ownTextLast = false
            update.docChanged -> ownTextLast = true
        }

        // A tool's own edit maps the selection, it does not set it: no look then
        if (foreign || update.selectionSet) {
            schedule(update.view, tries = FOLLOW_TRIES)
        }
    }

    private fun schedule(view: EditorView, tries: Int) {
        timer?.let { window.clearTimeout(it) }
        timer = window.setTimeout({ look(view, tries) }, FOLLOW_DELAY_MS)
    }

    private fun look(view: EditorView, tries: Int) {
        timer = null

        // The editor is gone: nothing more to report
        if (!view.dom.isConnected) {
            return
        }

        val source = view.state.doc.toString()
        val analysis = analysisProvider()

        // Not yet analysed as it stands: ask again in a moment
        if (analysis == null || analysis.source != source) {
            if (tries > 0) {
                schedule(view, tries - 1)
            }

            return
        }

        val head = view.state.selection.main.head
        val found = analysis.toolCallAt(pos = head, symbolLookup = docProvider)
        val tools = found?.let { KlangUiToolRegistry.resolve(it.tools) }.orEmpty()
        val callKey = found?.takeIf { tools.isNotEmpty() }?.let { "${it.functionName}@${it.parentheses.first}:${it.binding.param.name}" }
        val shown = anchor?.parentheses(source)
        val sameSite = found != null && callKey != null && found.parentheses.first == shown?.first && found.binding.param.name == lastParam

        // A look asked for before the tool wrote: what the tool wrote (`adsr(decay = 0.3)`) never takes the tool away
        // from under the hand, nor swaps it for another parameter's; the same site after an edit by other hands starts over
        if (ownTextLast && !sameSite && shown != null && head > shown.first && head <= shown.last) {
            return
        }

        if (found == null || callKey == null) {
            if (lastKey != null) {
                lastKey = null
                lastParam = null
                anchor = null
                onToolCall(null)
            }

            return
        }

        val key = "$callKey:$foreignEdits"

        // The same site: the tool keeps its context, and the anchor it holds
        if (key == lastKey) {
            return
        }

        val siteAnchor = Anchor(found.parentheses.first)
        val paramName = found.binding.param.name
        // The argument's place: the parameter's own (a whole-call rewrite may turn named arguments positional), but for a
        // vararg, which holds several, the one the analysis found (only a trailing function argument floats elsewhere)
        val argIndex = if (found.binding.param.isVararg) {
            found.argIndex.coerceAtLeast(0)
        } else {
            found.binding.callable.params.indexOf(found.binding.param)
        }
        val editRange = found.editRange

        lastKey = key
        lastParam = found.binding.param.name
        anchor = siteAnchor

        onToolCall(
            ToolCallSite(
                symbol = found.symbol,
                callable = found.binding.callable,
                paramName = found.binding.param.name,
                tools = tools,
                currentValue = editRange?.let { source.substring(it.first, it.last + 1) },
                argFrom = editRange?.first ?: (found.parentheses.first + 1),
                callKey = callKey,
                key = key,
                makeContext = {
                    makeContext(
                        view = view,
                        anchor = siteAnchor,
                        symbol = found.symbol,
                        callable = found.binding.callable,
                        paramName = paramName,
                        argIndex = argIndex,
                        wholeCall = found.binding.wholeCall,
                    )
                },
            )
        )
    }

    private fun makeContext(
        view: EditorView,
        anchor: Anchor,
        symbol: KlangSymbol,
        callable: KlangCallable,
        paramName: String,
        argIndex: Int,
        wholeCall: Boolean,
    ): KlangUiToolContext {
        val source = view.state.doc.toString()

        return KlangUiToolContext(
            symbol = symbol,
            paramName = paramName,
            // Read now: a tool picked after another one wrote starts from what is there
            currentValue = literalNow(source, anchor, paramName, argIndex)?.let { source.substring(it.first, it.last + 1) },
            onCommit = { result ->
                val now = view.state.doc.toString()
                val range = literalNow(now, anchor, paramName, argIndex)
                val parens = anchor.parentheses(now)

                when {
                    range != null -> write(view = view, from = range.first, to = range.last + 1, text = result)

                    // No argument yet: the value goes first in the parentheses, over space only, before a comment
                    parens != null && CallText.arguments(now, parens).isEmpty() -> {
                        val blank = now.substring(parens.first + 1, parens.last).isBlank()
                        val from = if (blank) parens.first + 1 else CallText.skipBlank(now, parens.first + 1)

                        write(view = view, from = from, to = if (blank) parens.last else from, text = result)
                    }
                }
            },
            onCancel = {},
            // An ambiguous untyped call gets the scalar tool, never the whole-call rewrite
            call = wholeCall.takeIf { it }?.let { wholeCallNow(view = view, anchor = anchor, callable = callable) },
        )
    }

    /** Where the literal is in [source] now: the argument by the parameter's name, else at its place; null when gone. */
    private fun literalNow(source: String, anchor: Anchor, paramName: String, argIndex: Int): IntRange? {
        val parens = anchor.parentheses(source) ?: return null

        return CallText.literalOf(source = source, parentheses = parens, name = paramName, index = argIndex)
    }

    /** The whole argument list for a tool that edits the call, as it is now; its commit rewrites the list where it is. */
    private fun wholeCallNow(view: EditorView, anchor: Anchor, callable: KlangCallable): KlangUiToolCall? {
        val paramNames = callable.params.map { it.name }.takeIf { it.isNotEmpty() } ?: return null
        val source = view.state.doc.toString()
        val parens = anchor.parentheses(source) ?: return null
        val texts = CallText.arguments(source, parens).map { source.substring(it.from, it.to) }

        return KlangUiToolCall(
            paramNames = paramNames,
            args = alignArgsToParams(paramNames, texts),
            onCommitCall = { values ->
                val now = view.state.doc.toString()

                anchor.parentheses(now)?.let { nowParens ->
                    write(view = view, from = nowParens.first + 1, to = nowParens.last, text = serializeCallArgs(paramNames, values))
                }
            },
        )
    }

    private fun write(view: EditorView, from: Int, to: Int, text: String) {
        view.dispatch(view.state.update(jsObject {
            this.changes = jsObject<dynamic> {
                this.from = from
                this.to = to
                this.insert = text
            }
            this.userEvent = TOOL_EDIT_EVENT
        }))
    }
}
