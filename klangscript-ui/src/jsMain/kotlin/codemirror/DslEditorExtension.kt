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
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolCall
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.badgeTool
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.utils.Vector2D
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onMouseEnter
import io.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.ultra.semanticui.SemanticIcon
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.html.FlowContent
import org.w3c.dom.Element

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
 * Combined CodeMirror extension for DSL editor interactions:
 *
 * - Hover over a known function  → show doc tooltip (for a local, with its declaration's code)
 * - Right-click on a function/argument → context menu popup (a local: go to definition; a library name: go to docs)
 * - Ctrl/Cmd + click             → a local: jump to its declaration; a library name: navigate to docs (same tab)
 * - Ctrl/Cmd + Shift + click     → a local: jump to its declaration; a library name: navigate to docs (new tab)
 * - Ctrl/Cmd held while hovering → underline the name
 * - Ctrl+B / Ctrl+Q (the key map's go to definition and quick documentation) → the same for the name at the cursor
 *
 * [hoverContent] renders the popup for a symbol; `declarationCode` is a local's declaration, null for a library name.
 */
fun dslEditorExtension(
    docProvider: (String) -> KlangSymbol?,
    analysisProvider: () -> AnalyzedAst? = { null },
    hoverPopup: HoverPopupCtrl,
    hoverContent: FlowContent.(symbol: KlangSymbol, declarationCode: String?) -> Unit,
    popups: PopupsManager,
    onNavigate: (doc: KlangSymbol, event: dynamic) -> Unit,
    onOpenTool: ((toolName: String, ctx: KlangUiToolContext, argFrom: Int, event: dynamic) -> Unit)? = null,
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
            // A local answers with its declaration, read from the text as it is now (the analysis can be older)
            val definition = analysis.definitionAt(pos = pos, text = view.state.doc.toString())

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

    fun dispatchClear(view: EditorView) {
        view.dispatch(view.state.update(jsObject {
            this.effects = clearUnderlineEffect.of(Unit.unsafeCast<Unit>())
        }))
    }

    // ── Tool context factory ───────────────────────────────────────────────

    /**
     * Creates a [KlangUiToolContext] for the given argInfo that commits edits back into the view.
     *
     * argTo is tracked as a mutable local because the tool can commit multiple times
     * (e.g. the user tweaks values and hits Update repeatedly without closing the modal).
     * Each commit may insert a result of a different length than the previous one, shifting
     * subsequent document positions. argFrom never moves (it is always the start of the
     * argument), but argTo is updated to argFrom + result.length after every commit so the
     * next commit replaces exactly the text that was just written.
     */
    /** Returns the [CallArgInfo] for the document position under [event], or null if none. */
    fun argInfoAt(event: dynamic, view: EditorView): CallArgInfo? {
        val source = view.state.doc.toString()
        val coords = jsObject<dynamic> { this.x = event.clientX; this.y = event.clientY }.unsafeCast<Coords>()
        val pos = view.posAtCoords(coords) ?: return null
        return findCallArgAtAst(analysisProvider(), source, pos, docProvider)
    }

    /**
     * Builds the whole-call view for MultiParam tools: current per-param texts of the host
     * call plus a commit that rewrites the ENTIRE argument list (contiguous prefix ->
     * positional, gaps -> all-named; KlangScript forbids mixing the two forms).
     */
    fun makeToolCall(argInfo: CallArgInfo, view: EditorView): KlangUiToolCall? {
        val source = view.state.doc.toString()
        val span = scanCallArgsSpan(source, argInfo.argFrom) ?: return null
        // Sanity guard: the backward paren scan is string-blind (parity with findCallArgAt), so
        // an unbalanced paren inside a preceding string arg could yield a wrong span. If the
        // clicked argument's text is not part of the scanned span, refuse the whole-call view
        // rather than risk rewriting the wrong source range.
        if (span.argTexts.none { argInfo.argText == it || argInfo.argText in it }) return null
        val paramNames = argInfo.callable.params.map { it.name }
        if (paramNames.isEmpty()) return null
        val aligned = alignArgsToParams(paramNames, span.argTexts)
        var argsTo = span.argsTo
        return KlangUiToolCall(
            paramNames = paramNames,
            args = aligned,
            onCommitCall = { texts ->
                val result = serializeCallArgs(paramNames, texts)
                view.dispatch(view.state.update(jsObject {
                    this.changes = jsObject<dynamic> {
                        this.from = span.argsFrom
                        this.to = argsTo
                        this.insert = result
                    }
                }))
                argsTo = span.argsFrom + result.length
            },
        )
    }

    fun makeToolContext(argInfo: CallArgInfo, view: EditorView): KlangUiToolContext {
        var argTo = argInfo.argTo
        return KlangUiToolContext(
            symbol = argInfo.symbol,
            paramName = argInfo.paramName,
            currentValue = argInfo.argText,
            onCommit = { result ->
                view.dispatch(view.state.update(jsObject {
                    this.changes = jsObject<dynamic> {
                        this.from = argInfo.argFrom
                        this.to = argTo
                        this.insert = result
                    }
                }))
                argTo = argInfo.argFrom + result.length
            },
            onCancel = {},
            // An ambiguous untyped call gets the scalar tool, never the whole-call rewrite.
            call = if (argInfo.wholeCall) makeToolCall(argInfo, view) else null,
        )
    }


    // ── Context menu ───────────────────────────────────────────────────────

    /** Move the cursor to a local's declared name, scroll it into view and keep the focus in the editor. */
    fun goToDefinition(view: EditorView, definition: LocalDefinition) {
        view.dispatch(jsObject<dynamic> {
            this.selection = jsObject<dynamic> { this.anchor = definition.namePos }
            this.scrollIntoView = true
        })
        view.focus()
    }

    fun showContextMenu(event: dynamic, wordDoc: WordDoc?, argInfo: CallArgInfo?, view: EditorView) {
        val definition = wordDoc?.definition
        val docs = wordDoc?.docsSymbol
        val hasWordItem = definition != null || docs != null

        if (!hasWordItem && argInfo == null) {
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

                // ── Tool items ─────────────────────────────────────────────
                if (argInfo != null && onOpenTool != null) {
                    if (hasWordItem) {
                        noui.divider {}
                    }

                    argInfo.tools.forEach { (toolName, tool) ->
                        item({
                            tool.run { icon.iconFn().render() }
                            +(tool.title ?: toolName)
                        }) {
                            onOpenTool(toolName, makeToolContext(argInfo, view), argInfo.argFrom, event)
                        }
                    }
                }
            }
        }
    }

    // ── Hover state ────────────────────────────────────────────────────────

    var lastHoverKey: HoverKey? = null

    // ── Tool-badge overlay ──────────────────────────────────────────────────

    /** Lazily created floating container for tool-icon badge buttons. */
    var badgeContainer: Element? = null

    /** Key of the arg whose badges are currently rendered (prevents needless DOM rebuilds). */
    var badgeCacheKey: String? = null

    /** The [CallArgInfo] whose badges are currently rendered. */
    var badgeArgInfo: CallArgInfo? = null

    var badgesHideTimer: dynamic = null
    var badgesShowTimer: dynamic = null

    fun cancelBadgesShow() {
        if (badgesShowTimer != null) window.clearTimeout(badgesShowTimer.unsafeCast<Int>())
        badgesShowTimer = null
    }

    fun cancelBadgesClose() {
        if (badgesHideTimer != null) window.clearTimeout(badgesHideTimer.unsafeCast<Int>())
        badgesHideTimer = null
    }

    fun hideBadges() {
        cancelBadgesShow()
        val container = badgeContainer
        if (container != null) container.asDynamic().style.display = "none"
        badgeCacheKey = null
        badgeArgInfo = null
    }

    fun scheduleBadgesClose() {
        cancelBadgesClose()
        badgesHideTimer = window.setTimeout({ hideBadges() }, 300)
    }

    // TODO: Future improvement — create a Kraft component lifecycle "attachment" mechanism:
    //  a self-cleanup object that reacts to onMount/onUnMount, keeping state (like badge container,
    //  timers, caches) in one place instead of scattered across the host component. The pattern is
    //  always the same: onMount creates resources, onUnMount tears them down. The attachment would
    //  own all state variables and auto-cleanup when the host component unmounts.

    /** Lazily created badge container. Attached to the editor's own DOM so it is automatically
     *  removed when the editor is destroyed — prevents orphaned elements leaking in document.body. */
    fun getOrCreateBadgeContainer(view: EditorView): Element {
        return badgeContainer ?: run {
            val div = document.createElement("div")
            div.asDynamic().style.cssText =
                "position:fixed;z-index:9999;display:none;flex-direction:row;gap:3px;pointer-events:auto;"
            div.addEventListener("mouseenter", { cancelBadgesClose() })
            div.addEventListener("mouseleave", { scheduleBadgesClose() })
            // Append to editor DOM instead of document.body: position:fixed still positions
            // relative to the viewport, but the element's lifetime is tied to the editor.
            view.dom.appendChild(div)
            badgeContainer = div
            div
        }
    }

    /** Identifies the badge an argument shows, so the DOM is rebuilt only when it changes. */
    fun badgeKey(argInfo: CallArgInfo): String =
        "${argInfo.argFrom}:${argInfo.tools.badgeTool(argInfo.argText)?.first}"

    fun showBadges(argInfo: CallArgInfo, view: EditorView, mouseX: Double) {
        val badge = argInfo.tools.badgeTool(argInfo.argText)

        if (onOpenTool == null || badge == null) {
            hideBadges(); return
        }

        val rect = view.coordsAtPos(argInfo.argFrom) ?: run {
            console.warn("[badges] coordsAtPos returned null for argFrom=${argInfo.argFrom}")
            return
        }

        val container = getOrCreateBadgeContainer(view)
        // Appears centred on the mouse X at show time, then stays put —
        // deliberately does NOT track the mouse afterwards.
        container.asDynamic().style.left = "${mouseX}px"
        container.asDynamic().style.top = "${rect.top - 26}px"
        container.asDynamic().style.transform = "translateX(-50%)"
        container.asDynamic().style.display = "flex"

        val key = badgeKey(argInfo)
        if (key == badgeCacheKey) return
        badgeCacheKey = key
        badgeArgInfo = argInfo
        container.innerHTML = ""

        // One badge per argument; the context menu lists every tool of the param.
        val (toolName, tool) = badge

        val iconCss = SemanticIcon.cssClassOf(tool.iconFn)
        val btn = document.createElement("button")
        btn.asDynamic().title = tool.title ?: toolName
        btn.asDynamic().style.cssText =
            "cursor:pointer;background:${KlangTheme.Hex.cardBackground};" +
                    "border:1px solid ${KlangTheme.Hex.textSecondary};border-radius:3px;" +
                    "padding:2px 8px;font-size:12px;line-height:1.5;color:${KlangTheme.Hex.gold};"

        btn.innerHTML = """<i class="$iconCss icon" style="margin:0;font-size:12px;"></i>"""

        btn.addEventListener("click", { event ->
            event.asDynamic().preventDefault()
            event.asDynamic().stopPropagation()
            hideBadges()
            onOpenTool(toolName, makeToolContext(argInfo, view), argInfo.argFrom, event.asDynamic())
        })
        container.appendChild(btn)
    }

    // ── DOM event handlers ─────────────────────────────────────────────────

    // Right-click → context menu
    val onContextMenu: (event: dynamic, view: EditorView) -> Boolean = contextmenu@{ event, view ->
        // console.log("onContextMenu", event)

        try {
            // A local the analysis cannot place right now offers neither its declaration nor docs
            val wordDoc = wordDocAt(view, event)?.takeIf { it.definition != null || it.docsSymbol != null }
            val argInfo = argInfoAt(event, view)

            // console.log("wordDoc", wordDoc, "argInfo", argInfo)

            if (wordDoc == null && argInfo == null) return@contextmenu false
            event.preventDefault()
            event.stopPropagation()
            showContextMenu(event, wordDoc, argInfo, view)
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

            if (doc != null && word != null) {
                showDocs(view = view, y = event.clientY.unsafeCast<Double>(), symbol = doc, declarationCode = declarationCode)
            } else {
                hoverPopup.scheduleClose()
            }
        } else if (hoverKey != null) {
            hoverPopup.cancelClose()
        }

        // ── Tool-icon badge overlay ────────────────────────────────────────
        if (onOpenTool != null) {
            try {
                val mouseX = event.clientX.unsafeCast<Double>()
                val argInfo = argInfoAt(event, view)
                if (argInfo != null && argInfo.tools.isNotEmpty()) {
                    cancelBadgesClose()
                    val newKey = badgeKey(argInfo)
                    if (newKey != badgeCacheKey) {
                        // New arg — schedule the badge. The timer re-arms on every
                        // move, so the mouseX that fires is where the mouse came to
                        // rest; once shown the badge never repositions.
                        cancelBadgesShow()
                        badgesShowTimer = window.setTimeout({
                            badgesShowTimer = null
                            showBadges(argInfo, view, mouseX)
                        }, 100)
                    }
                } else if (badgeArgInfo != null) {
                    // Mouse left the current param but badges are visible —
                    // schedule close so they don't linger, but with enough
                    // delay that the user can still reach them.
                    cancelBadgesShow()
                    scheduleBadgesClose()
                } else {
                    cancelBadgesShow()
                    scheduleBadgesClose()
                }
            } catch (e: Throwable) {
                console.error("dslEditorExtension badge overlay error:", e)
            }
        }

        false
    }

    // Mouse leaves editor → clear underline + close hover tooltip + schedule badge close
    val onMouseLeave: (event: dynamic, view: EditorView) -> Boolean = { _, view ->
        lastHoverKey = null
        dispatchClear(view)
        hoverPopup.scheduleClose()
        cancelBadgesShow()
        scheduleBadgesClose()
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

    // Both keep their key from the browser, also on a word with nothing behind it: Firefox opens its bookmarks on
    // Ctrl+B, and quits on Ctrl+Q under Linux
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

        if (result != null && cursorTop != null) {
            // The popup stands for this word now, so the next mouse move elsewhere closes it, as after a hover
            lastHoverKey = HoverKey(from = result.word.from, to = result.word.to, symbol = result.symbol, definition = result.definition)
            showDocs(view = view, y = cursorTop, symbol = result.symbol, declarationCode = result.definition?.code)
        }

        true
    }

    return DslEditorWiring(
        extension = arrayOf(
            underlineField,
            EditorView.domEventHandlers(handlers),
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
