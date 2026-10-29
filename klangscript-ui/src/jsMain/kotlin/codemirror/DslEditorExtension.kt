/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Command
import io.peekandpoke.klang.codemirror.ext.Coords
import io.peekandpoke.klang.codemirror.ext.Decoration
import io.peekandpoke.klang.codemirror.ext.DecorationSet
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.SelectionRange
import io.peekandpoke.klang.codemirror.ext.StateEffect
import io.peekandpoke.klang.codemirror.ext.StateField
import io.peekandpoke.klang.codemirror.ext.StateFieldConfig
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.LocalDefinition
import io.peekandpoke.klang.script.intel.LocalUsage
import io.peekandpoke.klang.script.intel.ToolCallAt
import io.peekandpoke.klang.script.intel.toolCallAt
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.utils.Vector2D
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onMouseEnter
import io.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.browser.window
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.html.FlowContent
import org.w3c.dom.events.KeyboardEvent

/**
 * The word under the mouse and what it names: its docs [symbol] and, for a local, its [definition] (null for a library
 * name).
 */
private class WordDoc(val word: SelectionRange, val symbol: KlangSymbol, val definition: LocalDefinition?) {
    /** The symbol whose docs page the word opens: a library name's; null for a local, which has its declaration. */
    val docsSymbol: KlangSymbol? get() = symbol.takeIf { it.origin !is KlangSymbol.Origin.Local }
}

/** What the hover popup shows. It is rebuilt when the word under the mouse or what it names changes. */
private data class HoverKey(val from: Int, val to: Int, val symbol: KlangSymbol, val definition: LocalDefinition?)

/**
 * A host's quick help for a name: its [KlangSymbol], a local's declaration code (null for a library name), and a local's
 * uses in the editor's text (empty for a library name), for a list to jump from (KLANG-00349).
 */
typealias QuickHelpHandler = (symbol: KlangSymbol, declarationCode: String?, usages: List<LocalUsage>) -> Unit

/** How long the mouse rests on a name before its help goes to the host ([dslEditorExtension]'s onHoverDocumentation). */
private const val HOVER_TO_HOST_MS = 350

/**
 * Combined CodeMirror extension for DSL editor interactions:
 *
 * - Hover over a known function  → show doc tooltip (for a local, with its declaration's code)
 * - Right-click on a function/argument → context menu popup (a local: go to definition; a library name: go to docs;
 *   inside a call with tools: open the Sprudel tool there, [onShowTool])
 * - Ctrl/Cmd + click             → a local: jump to its declaration; a library name: navigate to docs (same tab)
 * - Ctrl/Cmd + Shift + click     → a local: jump to its declaration; a library name: navigate to docs (new tab)
 * - Ctrl/Cmd held while hovering → underline the name
 * - Ctrl+B / Alt+Q (the key map's go to definition and quick documentation) → the same for the name at the cursor
 *
 * [hoverContent] renders the popup for a symbol; `declarationCode` is a local's declaration, null for a library name.
 * [hoverDocs] is asked on every move whether the hover shows the popup (a setting in the app, on by default:
 * KLANG-00349). [onQuickDocumentation], when given, gets the name at the cursor on quick documentation instead of
 * the popup (the song page shows it in its Help panel); a cursor on no known name does not call it.
 * [onHoverDocumentation], when given, gets the name the mouse rests on instead of the popup, once it rested a moment
 * (the song page shows its help in the Help panel, never as a popup: the maintainer, 2026-10-27).
 */
fun dslEditorExtension(
    docProvider: (String) -> KlangSymbol?,
    analysisProvider: () -> AnalyzedAst? = { null },
    hoverPopup: HoverPopupCtrl,
    hoverContent: FlowContent.(symbol: KlangSymbol, declarationCode: String?) -> Unit,
    popups: PopupsManager,
    onNavigate: (doc: KlangSymbol, event: dynamic) -> Unit,
    onShowTool: ((pos: Int) -> Unit)? = null,
    hoverDocs: () -> Boolean = { true },
    onQuickDocumentation: QuickHelpHandler? = null,
    onHoverDocumentation: QuickHelpHandler? = null,
): DslEditorWiring {

    // ── Underline decoration (CTRL/Cmd-hover) ─────────────────────────────

    val setUnderlineEffect = StateEffect.define<SelectionRange>()
    val clearUnderlineEffect = StateEffect.define<Unit>()

    val underlineField = StateField.define(
        jsObject<StateFieldConfig<DecorationSet>> {
            this.create = { Decoration.none }
            this.update = { decorations, tr ->
                var updated = decorations.map(tr.changes)
                tr.effects.forEach { effect ->
                    when {
                        effect.`is`(setUnderlineEffect) -> {
                            val range = effect.value.unsafeCast<SelectionRange>()
                            updated = Decoration.set(
                                arrayOf(
                                    jsObject {
                                        this.from = range.from
                                        this.to = range.to
                                        this.value = Decoration.mark(jsObject {
                                            this.`class` = "cm-dsl-link"
                                        })
                                    }
                                ))
                        }

                        effect.`is`(clearUnderlineEffect) -> {
                            updated = Decoration.none
                        }
                    }
                }
                updated
            }
            this.provide = { field -> EditorView.decorations.from(field) }
        }
    ).unsafeCast<Extension>()

    // ── Helpers ────────────────────────────────────────────────────────────

    /** True when Ctrl (Windows/Linux) or Cmd/Meta (Mac) is held. */
    fun isModifier(event: dynamic): Boolean =
        event.ctrlKey == true || event.metaKey == true

    /** The word at [pos] and what it names, as [wordDocAt] reads the one under the mouse. */
    fun wordDocAtPos(view: EditorView, pos: Int): WordDoc? {
        val word = view.state.wordAt(pos) ?: return null
        val name = view.state.doc.sliceString(word.from, word.to)

        // Skip words inside string literals (both "..." and '...')
        val line = view.state.doc.lineAt(word.from)
        val prefix = view.state.doc.sliceString(line.from, word.from)
        if (isInsideStringLiteral(prefix)) {
            return null
        }

        // Preferred path: the analyzer makes the decision (member-access receiver
        // filtering, local-binding shadowing, strict policy when receiver doesn't
        // match). The editor doesn't replicate any of that logic.
        val analysis = analysisProvider()

        if (analysis != null) {
            // A local answers with its declaration, read from the text as it is now (the analysis can be older); on
            // its declared name too (the maintainer, 2026-10-27: the help "also on the declaration sites")
            val text = view.state.doc.toString()
            val definition = analysis.definitionAt(pos = pos, text = text) ?: analysis.declarationAt(pos = pos, text = text)

            if (definition != null) {
                return WordDoc(word = word, symbol = definition.symbol, definition = definition)
            }

            return analysis.symbolAt(pos)?.let { WordDoc(word = word, symbol = it, definition = null) }
        }

        // No analyzer yet (initial render, parse error) — fall back to the
        // bare-name registry lookup. The strict / local-binding behaviours kick
        // in as soon as an analysis is available again.
        val doc = docProvider(name) ?: return null

        return WordDoc(word = word, symbol = doc, definition = null)
    }

    /** Resolve the word under the mouse and look it up via the analyzer (receiver-aware + local-binding-aware). */
    fun wordDocAt(view: EditorView, event: dynamic): WordDoc? {
        val coords = jsObject<dynamic> {
            this.x = event.clientX
            this.y = event.clientY
        }.unsafeCast<Coords>()
        val pos = view.posAtCoords(coords) ?: return null

        return wordDocAtPos(view = view, pos = pos)
    }

    /** Whether the editor shows a Ctrl/Cmd underline now: only then is there one to clear. */
    fun hasUnderline(view: EditorView): Boolean {
        val set = view.state.asDynamic().field(underlineField, false)

        return set != null && set != undefined && (set.size as Int) > 0
    }

    /**
     * Takes the Ctrl/Cmd underline away. Every mouse move over the editor asks for it; a transaction only when there is
     * one (the maintainer's trace, 2026-10-29: a transaction a move, about 19 ms a second while the mouse moved).
     */
    fun dispatchClear(view: EditorView) {
        if (!hasUnderline(view)) {
            return
        }

        view.dispatch(view.state.update(jsObject {
            this.effects = clearUnderlineEffect.of(Unit.unsafeCast<Unit>())
        }))
    }

    // ── The Sprudel tool ─────────────────────────────────────────────────────

    /**
     * The call with tools under [event], and the position in it, for the context menu (KLANG-00374: the tools live in
     * the bottom panel's Sprudel tab, a right-click opens it there); null when there is none.
     */
    fun toolCallAtEvent(event: dynamic, view: EditorView): Pair<Int, ToolCallAt>? {
        val coords = jsObject<dynamic> {
            this.x = event.clientX
            this.y = event.clientY
        }.unsafeCast<Coords>()
        val pos = view.posAtCoords(coords) ?: return null
        // Only an analysis of the code as it stands: offsets from before an edit would name another call
        val analysis = analysisProvider()?.takeIf { it.source == view.state.doc.toString() } ?: return null
        val found = analysis.toolCallAt(pos = pos, symbolLookup = docProvider) ?: return null

        return (pos to found).takeIf { KlangUiToolRegistry.resolve(found.tools).isNotEmpty() }
    }

    // ── Context menu ───────────────────────────────────────────────────────

    /** A local's uses in the editor's text, for the host's quick help; none for a library name. */
    fun usagesOf(view: EditorView, word: Int, isLocal: Boolean): List<LocalUsage> {
        if (!isLocal) {
            return emptyList()
        }

        return analysisProvider()?.usagesAt(pos = word, text = view.state.doc.toString()) ?: emptyList()
    }

    /** Move the cursor to a local's declared name, scroll it into view and keep the focus in the editor. */
    fun goToDefinition(view: EditorView, definition: LocalDefinition) {
        view.dispatch(jsObject<dynamic> {
            this.selection = jsObject<dynamic> { this.anchor = definition.namePos }
            this.scrollIntoView = true
        })
        view.focus()
    }

    fun showContextMenu(event: dynamic, wordDoc: WordDoc?, toolAt: Pair<Int, ToolCallAt>?, view: EditorView) {
        val definition = wordDoc?.definition
        val docs = wordDoc?.docsSymbol
        val hasWordItem = definition != null || docs != null

        if (!hasWordItem && toolAt == null) {
            return
        }

        val anchor = Vector2D(
            x = event.clientX.unsafeCast<Double>(),
            y = event.clientY.unsafeCast<Double>(),
        )

        popups.showContextMenu(anchor = anchor, positioning = PopupsManager.Positioning.BottomLeft) { handle ->

            fun item(label: FlowContent.() -> Unit, action: () -> Unit) {
                noui.link.item {
                    onClick {
                        it.stopPropagation()
                        handle.close()
                        action()
                    }
                    label()
                }
            }

            ui.compact.vertical.menu.with(KlangTheme().styles.contextMenu()) {
                css {
                    minWidth = 200.px
                }

                onMouseEnter { hoverPopup.cancelClose() }
                onMouseLeave { hoverPopup.scheduleClose() }

                // ── Navigation: a local to its declaration, a library name to its docs ──
                if (definition != null) {
                    item({ icon.code(); +"Go to definition: ${definition.symbol.name}" }) {
                        goToDefinition(view, definition)
                    }
                } else if (docs != null) {
                    item({ icon.book(); +"Go to docs: ${docs.name}" }) {
                        onNavigate(docs, event)
                    }
                }

                // ── The Sprudel tool: the cursor goes there, and the tab opens on its tool ──
                if (toolAt != null && onShowTool != null) {
                    if (hasWordItem) {
                        noui.divider {}
                    }

                    val (pos, found) = toolAt

                    item({ icon.wrench(); +"Open the Sprudel tool: ${found.functionName}" }) {
                        view.dispatch(jsObject<dynamic> { this.selection = jsObject<dynamic> { this.anchor = pos } })
                        view.focus()
                        onShowTool(pos)
                    }
                }
            }
        }
    }

    // ── Hover state ────────────────────────────────────────────────────────

    var lastHoverKey: HoverKey? = null

    /** True while the docs popup is the one quick documentation opened, which no mouse move will close. */
    var popupByKey = false

    /** Closes the docs popup when quick documentation opened it: on Esc, an edit, a cursor move. */
    fun closeKeyPopup() {
        if (popupByKey) {
            popupByKey = false
            lastHoverKey = null
            hoverPopup.scheduleClose()
        }
    }

    // ── The hover's quick help ───────────────────────────────────────────────

    /** The hover's quick help on its way to the host ([onHoverDocumentation]), waiting for the mouse to rest. */
    var hoverToHostTimer: Int? = null

    fun cancelHoverToHost() {
        hoverToHostTimer?.let { window.clearTimeout(it) }
        hoverToHostTimer = null
    }

    // ── DOM event handlers ─────────────────────────────────────────────────

    // Right-click → context menu
    val onContextMenu: (event: dynamic, view: EditorView) -> Boolean = contextmenu@{ event, view ->
        // console.log("onContextMenu", event)

        try {
            // A local the analysis cannot place right now offers neither its declaration nor docs
            val wordDoc = wordDocAt(view, event)?.takeIf { it.definition != null || it.docsSymbol != null }
            val toolAt = if (onShowTool != null) toolCallAtEvent(event, view) else null

            if (wordDoc == null && toolAt == null) return@contextmenu false
            event.preventDefault()
            event.stopPropagation()
            showContextMenu(event, wordDoc, toolAt, view)
            true
        } catch (e: Throwable) {
            console.error("dslEditorExtension contextmenu error:", e)
            false
        }
    }

    // Ctrl/Cmd + click → a local: its declaration | a library name: its docs, same tab (with Shift: new tab)
    val onClick: (event: dynamic, view: EditorView) -> Boolean = click@{ event, view ->
        if (!isModifier(event)) {
            return@click false
        }

        val result = wordDocAt(view, event) ?: return@click false
        val definition = result.definition
        event.preventDefault()

        if (definition != null) {
            // The declaration is in this document, so Shift makes no difference
            goToDefinition(view, definition)
        } else {
            // A local the analysis cannot place right now (the edit since the last parse touches it) has no docs page
            result.docsSymbol?.let { onNavigate(it, event) }
        }

        true
    }

    /**
     * Shows the docs popup of [symbol] in a corner of the editor's visible part, away from [y] (the mouse's or the
     * cursor's height), so it does not cover what the user looks at.
     */
    fun showDocs(view: EditorView, y: Double, symbol: KlangSymbol, declarationCode: String?) {
        val viewportH = window.innerHeight.toDouble()
        val viewportW = window.innerWidth.toDouble()

        // Use the scroll container's visible rect, clamped to viewport
        val scrollRect = view.scrollDOM.getBoundingClientRect()
        val visTop = maxOf(scrollRect.top, 0.0)
        val visBottom = minOf(scrollRect.bottom, viewportH)
        val visRight = minOf(scrollRect.right, viewportW)
        val visMidY = (visTop + visBottom) / 2.0

        val pad = 8.0
        // The popup's glow (box-shadow 0 0 8px) is not part of its measured
        // box, so the bottom edge needs extra clearance or the glow (plus
        // rounding) hangs past the viewport edge.
        val bottomPad = 20.0
        // NOTE kraft semantics: Top* = the popup extends UP from the anchor,
        // Bottom* = it extends DOWN. Getting these backwards pushes the
        // popup's bottom outside the viewport.
        val (anchor, positioning) = if (y < visMidY) {
            // The mouse (or the cursor) in the top half → popup sits in the bottom-right corner, growing up
            Vector2D(x = visRight - pad, y = visBottom - bottomPad) to
                    PopupsManager.Positioning.TopRight
        } else {
            // In the bottom half → popup sits in the top-right corner, growing down
            Vector2D(x = visRight - pad, y = visTop + pad) to
                    PopupsManager.Positioning.BottomRight
        }
        hoverPopup.scheduleShow(anchor = anchor, positioning = positioning) { _ -> hoverContent(symbol, declarationCode) }
    }

    // Hover tooltip + Ctrl/Cmd underline
    val onMouseMove: (event: dynamic, view: EditorView) -> Boolean = { event, view ->
        val wordDoc = wordDocAt(view, event)
        val word = wordDoc?.word
        val doc = wordDoc?.symbol
        val declarationCode = wordDoc?.definition?.code

        // ── Ctrl/Cmd underline ─────────────────────────────────────────────
        if (isModifier(event)) {
            if (word != null) {
                view.dispatch(view.state.update(jsObject {
                    this.effects = setUnderlineEffect.of(word)
                }))
            } else {
                dispatchClear(view)
            }
        } else {
            dispatchClear(view)
        }

        // ── Hover tooltip ──────────────────────────────────────────────────
        // Keyed by the word's place and meaning: the same text elsewhere (a local `gain`, then the member `gain`) is
        // another popup
        val hoverKey = if (doc != null && word != null) {
            HoverKey(from = word.from, to = word.to, symbol = doc, definition = wordDoc.definition)
        } else {
            null
        }

        if (hoverKey != lastHoverKey) {
            lastHoverKey = hoverKey
            // The mouse has the popup now
            popupByKey = false

            cancelHoverToHost()

            val toHost = onHoverDocumentation

            if (doc != null && word != null && hoverDocs()) {
                if (toHost != null) {
                    // The host shows it, once the mouse rested on the name a moment
                    hoverToHostTimer = window.setTimeout({
                        hoverToHostTimer = null

                        // An editor gone meanwhile (its page left by a key, no mouseleave on the way) hands nothing over
                        if (!view.dom.isConnected) {
                            return@setTimeout
                        }

                        toHost(doc, declarationCode, usagesOf(view = view, word = word.from, isLocal = declarationCode != null))
                    }, HOVER_TO_HOST_MS)
                } else {
                    showDocs(view = view, y = event.clientY.unsafeCast<Double>(), symbol = doc, declarationCode = declarationCode)
                }
            } else {
                hoverPopup.scheduleClose()
            }
        } else if (hoverKey != null) {
            hoverPopup.cancelClose()
        }

        false
    }

    // Mouse leaves editor → clear underline + close hover tooltip
    val onMouseLeave: (event: dynamic, view: EditorView) -> Boolean = { _, view ->
        lastHoverKey = null
        // A name the mouse only passed on its way out (into the host's quick help, say) is not handed over
        cancelHoverToHost()
        dispatchClear(view)
        hoverPopup.scheduleClose()
        false
    }

    // Ctrl/Cmd released → clear underline
    val onKeyUp: (event: dynamic, view: EditorView) -> Boolean = { event, view ->
        if (event.key == "Control" || event.key == "Meta") dispatchClear(view)
        false
    }

    val handlers = jsObject<dynamic> {
        // Right-click → context menu
        this.contextmenu = onContextMenu
        // Ctrl/Cmd + click → a local: its declaration | a library name: its docs, same tab (with Shift: new tab)
        this.click = onClick
        // Ctrl/Cmd held while moving → underline the token under the cursor
        this.mousemove = onMouseMove
        // Mouse leaves editor → clear underline
        this.mouseleave = onMouseLeave
        // Ctrl/Cmd released → clear underline
        this.keyup = onKeyUp
    }

    // ── Theme ──────────────────────────────────────────────────────────────

    val theme = EditorView.baseTheme(
        jsObject {
            this[".cm-dsl-link"] = jsObject {
                textDecoration = "underline"
                cursor = "pointer"
                color = KlangTheme.Hex.gold
            }
        }
    )

    // ── The key map's commands (KLANG-00325 step 3b) ─────────────────────

    /** The word at the cursor and what it names; a cursor right after a word still reads that word. */
    fun wordDocAtCursor(view: EditorView): WordDoc? {
        val head = view.state.selection.main.head
        val pos = view.state.wordAt(head)?.from ?: head

        return wordDocAtPos(view = view, pos = pos)
    }

    // Both keep their key from the browser, also on a word with nothing behind it (Firefox opens its bookmarks on
    // Ctrl+B)
    val goToDefinitionAtCursor: Command = { view ->
        wordDocAtCursor(view)?.let { result ->
            val definition = result.definition

            if (definition != null) {
                goToDefinition(view, definition)
            } else {
                // No mouse event: the docs open in the same tab
                result.docsSymbol?.let { onNavigate(it, jsObject { this.shiftKey = false }) }
            }
        }

        true
    }

    val quickDocumentation: Command = { view ->
        val result = wordDocAtCursor(view)
        val cursorTop = view.coordsAtPos(view.state.selection.main.head)?.top
        val toWindow = onQuickDocumentation

        if (result != null && toWindow != null) {
            // The host shows the help in a window of its own, which closes itself; no popup
            toWindow(
                result.symbol,
                result.definition?.code,
                usagesOf(view = view, word = result.word.from, isLocal = result.definition != null),
            )
        } else if (result != null && cursorTop != null) {
            // The popup stands for this word now, so the next mouse move elsewhere closes it, as after a hover; Esc,
            // an edit or a cursor move close it too ([closeKeyPopup])
            popupByKey = true
            lastHoverKey = HoverKey(from = result.word.from, to = result.word.to, symbol = result.symbol, definition = result.definition)
            showDocs(view = view, y = cursorTop, symbol = result.symbol, declarationCode = result.definition?.code)
        }

        true
    }

    return DslEditorWiring(
        extension = arrayOf(
            underlineField,
            EditorView.domEventHandlers(handlers),
            EditorView.domEventObservers(
                jsObject<dynamic> {
                    this.keydown = { event: KeyboardEvent, _: EditorView ->
                        if (event.key == "Escape") {
                            closeKeyPopup()
                        }
                    }
                }
            ),
            EditorView.updateListener.of { update ->
                if (update.docChanged || update.selectionSet) {
                    closeKeyPopup()
                }

                // A rest that began before an edit would hand over a name and uses of two different texts
                if (update.docChanged) {
                    cancelHoverToHost()
                }
            },
            theme,
        ).unsafeCast<Extension>(),
        commands = mapOf(
            KlangEditorKeyBindings.GoToDefinition.id to listOf(goToDefinitionAtCursor),
            KlangEditorKeyBindings.QuickDocumentation.id to listOf(quickDocumentation),
        ),
    )
}

/** The DSL editor's extension, and the commands of its keys in the key map (go to definition, quick documentation). */
class DslEditorWiring(val extension: Extension, val commands: Map<String, List<Command>>)
