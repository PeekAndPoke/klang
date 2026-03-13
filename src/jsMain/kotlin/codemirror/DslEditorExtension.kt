package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.popups.PopupsManager
import de.peekandpoke.kraft.utils.Vector2D
import de.peekandpoke.kraft.utils.jsObject
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onMouseEnter
import de.peekandpoke.ultra.html.onMouseLeave
import de.peekandpoke.ultra.semanticui.SemanticIcon
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.codemirror.ext.*
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.KlangDocsHoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.html.FlowContent
import org.w3c.dom.Element

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
    onOpenTool: ((toolName: String, ctx: KlangUiToolContext, argFrom: Int, event: dynamic) -> Unit)? = null,
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
        val pos = view.posAtCoords(coords)
        return if (pos != null) findCallArgAt(source, pos, docProvider) else null
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
        )
    }

    // ── Context menu ───────────────────────────────────────────────────────

    fun showContextMenu(event: dynamic, func: KlangSymbol?, argInfo: CallArgInfo?, view: EditorView) {

        if (func == null && argInfo == null) return

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

            ui.compact.vertical.menu {
                css {
                    minWidth = 200.px
                }

                onMouseEnter { hoverPopup.cancelClose() }
                onMouseLeave { hoverPopup.scheduleClose() }

                // ── Docs navigation items ──────────────────────────────────
                if (func != null) {
                    item({ icon.book(); +"Go to docs: ${func.name}" }) {
                        onNavigate(func, event)
                    }
                }

                // ── Tool items ─────────────────────────────────────────────
                if (argInfo != null && onOpenTool != null) {
                    if (func != null) noui.divider {}
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

    var lastHoveredWord: String? = null

    // ── Tool-badge overlay ──────────────────────────────────────────────────

    /** Lazily created floating container for tool-icon badge buttons. */
    var badgeContainer: Element? = null

    /** Key of the arg whose badges are currently rendered (prevents needless DOM rebuilds). */
    var badgeCacheKey: String? = null

    /** The [CallArgInfo] whose badges are currently rendered. */
    var badgeArgInfo: CallArgInfo? = null

    var badgesHideTimer: dynamic = null

    fun cancelBadgesClose() {
        if (badgesHideTimer != null) window.clearTimeout(badgesHideTimer.unsafeCast<Int>())
        badgesHideTimer = null
    }

    fun hideBadges() {
        val container = badgeContainer
        if (container != null) container.asDynamic().style.display = "none"
        badgeCacheKey = null
        badgeArgInfo = null
    }

    fun scheduleBadgesClose() {
        cancelBadgesClose()
        badgesHideTimer = window.setTimeout({ hideBadges() }, 300)
    }

    fun getOrCreateBadgeContainer(): Element {
        return badgeContainer ?: run {
            val div = document.createElement("div")
            div.asDynamic().style.cssText =
                "position:fixed;z-index:9999;display:none;flex-direction:row;gap:3px;pointer-events:auto;"
            div.addEventListener("mouseenter", { cancelBadgesClose() })
            div.addEventListener("mouseleave", { scheduleBadgesClose() })
            document.body?.appendChild(div)
            badgeContainer = div
            div
        }
    }

    fun showBadges(argInfo: CallArgInfo, view: EditorView, mouseX: Double) {
        if (onOpenTool == null || argInfo.tools.isEmpty()) {
            hideBadges(); return
        }

        val rect = view.coordsAtPos(argInfo.argFrom) ?: run {
            console.warn("[badges] coordsAtPos returned null for argFrom=${argInfo.argFrom}")
            return
        }

        val container = getOrCreateBadgeContainer()
        // Center horizontally on mouse X, above the text line
        container.asDynamic().style.left = "${mouseX}px"
        container.asDynamic().style.top = "${rect.top - 21}px"
        container.asDynamic().style.transform = "translateX(-50%)"
        container.asDynamic().style.display = "flex"

        val key = "${argInfo.argFrom}:${argInfo.tools.joinToString(",") { it.first }}"
        if (key == badgeCacheKey) return
        badgeCacheKey = key
        badgeArgInfo = argInfo
        container.innerHTML = ""

        argInfo.tools.forEach { (toolName, tool) ->
            val iconCss = SemanticIcon.cssClassOf(tool.iconFn)
            val btn = document.createElement("button")
            btn.asDynamic().title = tool.title ?: toolName
            btn.asDynamic().style.cssText =
                "cursor:pointer;background:${KlangTheme.Hex.cardBackground};" +
                        "border:1px solid ${KlangTheme.Hex.textTertiary};border-radius:3px;" +
                        "padding:1px 5px;font-size:12px;line-height:1.5;color:${KlangTheme.Hex.textPrimary};"

            btn.innerHTML = """<i class="$iconCss icon" style="margin:0;font-size:12px;"></i>"""

            btn.addEventListener("click", { event ->
                event.asDynamic().preventDefault()
                event.asDynamic().stopPropagation()
                hideBadges()
                onOpenTool(toolName, makeToolContext(argInfo, view), argInfo.argFrom, event.asDynamic())
            })
            container.appendChild(btn)
        }
    }

    // ── DOM event handlers ─────────────────────────────────────────────────

    // Right-click → context menu
    val onContextMenu: (event: dynamic, view: EditorView) -> Boolean = contextmenu@{ event, view ->
        // console.log("onContextMenu", event)

        try {
            val wordDoc = wordDocAt(view, event)
            val argInfo = argInfoAt(event, view)

            // console.log("wordDoc", wordDoc, "argInfo", argInfo)

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

        // ── Tool-icon badge overlay ────────────────────────────────────────
        if (onOpenTool != null) {
            try {
                val mouseX = event.clientX.unsafeCast<Double>()
                val argInfo = argInfoAt(event, view)
                if (argInfo != null && argInfo.tools.isNotEmpty()) {
                    cancelBadgesClose()
                    showBadges(argInfo, view, mouseX)
                } else if (badgeArgInfo != null) {
                    // Mouse left the current param but badges are visible —
                    // keep them alive so the user can reach them.
                } else {
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
        lastHoveredWord = null
        dispatchClear(view)
        hoverPopup.scheduleClose()
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
        jsObject {
            this[".cm-dsl-link"] = jsObject {
                textDecoration = "underline"
                cursor = "pointer"
                color = KlangTheme.Hex.gold
            }
        }
    )

    return arrayOf(
        underlineField,
        EditorView.domEventHandlers(handlers),
        theme,
    ).unsafeCast<Extension>()
}
