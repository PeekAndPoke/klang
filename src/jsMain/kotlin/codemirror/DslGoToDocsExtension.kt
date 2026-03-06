package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.popups.PopupsManager
import de.peekandpoke.kraft.utils.Vector2D
import de.peekandpoke.kraft.utils.jsObject
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onMouseEnter
import de.peekandpoke.ultra.html.onMouseLeave
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.codemirror.ext.*
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.KlangDocsHoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import kotlinx.css.minWidth
import kotlinx.css.px

/**
 * Combined CodeMirror extension for DSL editor interactions:
 *
 * - Hover over a known function  → show doc tooltip
 * - Right-click on a function/argument → context menu popup
 * - Ctrl/Cmd + click             → navigate to docs (same tab)
 * - Ctrl/Cmd + Shift + click     → navigate to docs (new tab)
 * - Ctrl/Cmd held while hovering → underline the function name
 */
fun dslEditorExtension(
    docProvider: (String) -> KlangSymbol?,
    hoverPopup: KlangDocsHoverPopupCtrl,
    popups: PopupsManager,
    onNavigate: (doc: KlangSymbol, event: dynamic) -> Unit,
    onOpenTool: ((toolName: String, ctx: KlangUiToolContext, event: dynamic) -> Unit)? = null,
): Extension {

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

    /** Resolve the word under the mouse and look it up in the doc registry. */
    fun wordDocAt(view: EditorView, event: dynamic): Pair<SelectionRange, KlangSymbol>? {
        val coords = jsObject<dynamic> {
            this.x = event.clientX
            this.y = event.clientY
        }.unsafeCast<Coords>()
        val pos = view.posAtCoords(coords) ?: return null
        val word = view.state.wordAt(pos) ?: return null
        val name = view.state.doc.sliceString(word.from, word.to)
        val doc = docProvider(name) ?: return null
        return word to doc
    }

    fun dispatchClear(view: EditorView) {
        view.dispatch(view.state.update(jsObject {
            this.effects = clearUnderlineEffect.of(Unit.unsafeCast<Unit>())
        }))
    }

    // ── Context menu ───────────────────────────────────────────────────────

    fun showContextMenu(event: dynamic, func: KlangSymbol?, argInfo: CallArgInfo?, view: EditorView) {

        if (func == null && argInfo == null) return

        val anchor = Vector2D(
            x = event.clientX.unsafeCast<Double>(),
            y = event.clientY.unsafeCast<Double>(),
        )

        popups.showContextMenu(anchor = anchor, positioning = PopupsManager.Positioning.BottomLeft) { handle ->

            fun item(label: String, action: () -> Unit) {
                noui.link.item {
                    onClick {
                        it.stopPropagation()
                        handle.close()
                        action()
                    }
                    +label
                }
            }

            ui.compact.vertical.menu {
                css {
                    minWidth = 200.px
                }

                onMouseEnter { hoverPopup.cancelClose() }
                onMouseLeave { hoverPopup.scheduleClose() }

                // ── Docs navigation items ──────────────────────────────────
                if (func != null) {
                    item("Go to docs: ${func.name}") {
                        onNavigate(func, event)
                    }
                    item("Open docs in new tab: ${func.name}") {
                        onNavigate(func, jsObject {
                            this.shiftKey = true
                            this.ctrlKey = event.ctrlKey
                            this.metaKey = event.metaKey
                        })
                    }
                }

                // ── Tool items ─────────────────────────────────────────────
                if (argInfo != null && onOpenTool != null) {
                    if (func != null) noui.divider {}
                    argInfo.tools.forEach { (toolName, _) ->
                        item("Open $toolName\u2026") {
                            val ctx = KlangUiToolContext(
                                symbol = argInfo.symbol,
                                paramName = argInfo.paramName,
                                currentValue = argInfo.argText,
                                onCommit = { result ->
                                    view.dispatch(
                                        view.state.update(jsObject {
                                            this.changes = jsObject<dynamic> {
                                                this.from = argInfo.argFrom
                                                this.to = argInfo.argTo
                                                this.insert = result
                                            }
                                        })
                                    )
                                },
                                onCancel = {},
                            )
                            onOpenTool(toolName, ctx, event)
                        }
                    }
                }
            }
        }
    }

    // ── Hover state ────────────────────────────────────────────────────────

    var lastHoveredWord: String? = null

    // ── DOM event handlers ─────────────────────────────────────────────────

    // Right-click → context menu
    val onContextMenu: (event: dynamic, view: EditorView) -> Boolean = contextmenu@{ event, view ->
        console.log("onContextMenu", event)

        try {
            val wordDoc = wordDocAt(view, event)
            val source = view.state.doc.toString()
            val coords = jsObject<dynamic> { this.x = event.clientX; this.y = event.clientY }.unsafeCast<Coords>()
            val pos = view.posAtCoords(coords)
            val argInfo = if (pos != null) findCallArgAt(source, pos, docProvider) else null

            console.log("wordDoc", wordDoc, "coords", coords, "pos", pos, "argInfo", argInfo)

            if (wordDoc == null && argInfo == null) return@contextmenu false
            event.preventDefault()
            event.stopPropagation()
            showContextMenu(event, wordDoc?.second, argInfo, view)
            true
        } catch (e: Throwable) {
            console.error("dslEditorExtension contextmenu error:", e)
            false
        }
    }

    // Ctrl/Cmd + click → same tab  |  Ctrl/Cmd + Shift + click → new tab
    val onClick: (event: dynamic, view: EditorView) -> Boolean = click@{ event, view ->
        if (!isModifier(event)) return@click false
        val result = wordDocAt(view, event) ?: return@click false
        event.preventDefault()
        onNavigate(result.second, event)
        true
    }

    // Hover tooltip + Ctrl/Cmd underline
    val onMouseMove: (event: dynamic, view: EditorView) -> Boolean = { event, view ->
        val wordDoc = wordDocAt(view, event)
        val word = wordDoc?.first
        val doc = wordDoc?.second
        val name = word?.let { view.state.doc.sliceString(it.from, it.to) }

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
        val currentWord = if (doc != null) name else null
        if (currentWord != lastHoveredWord) {
            @Suppress("AssignedValueIsNeverRead")
            lastHoveredWord = currentWord
            if (doc != null && word != null) {
                val wordRect = view.coordsAtPos(word.from)
                val anchor = Vector2D(
                    x = wordRect?.left ?: event.clientX.unsafeCast<Double>(),
                    y = wordRect?.top ?: event.clientY.unsafeCast<Double>(),
                )
                hoverPopup.scheduleShow(doc, anchor)
            } else {
                hoverPopup.scheduleClose()
            }
        } else if (currentWord != null) {
            hoverPopup.cancelClose()
        }

        false
    }

    // Mouse leaves editor → clear underline + close hover tooltip
    val onMouseLeave: (event: dynamic, view: EditorView) -> Boolean = { _, view ->
        lastHoveredWord = null
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
        // Ctrl/Cmd + click → same tab  |  Ctrl/Cmd + Shift + click → new tab
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
        js(
            """({
            ".cm-dsl-link": {
                textDecoration: "underline",
                cursor: "pointer",
                color: "#0060df"
            }
        })"""
        )
    )

    return arrayOf(
        underlineField,
        EditorView.domEventHandlers(handlers),
        theme,
    ).unsafeCast<Extension>()
}
