package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.klang.codemirror.ext.*
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

/**
 * CodeMirror extension that adds three doc-navigation gestures:
 *
 * - Right-click on a known function → small context menu with "Go to docs" link
 * - Ctrl/Cmd + click              → navigate to docs page in the same tab
 * - Ctrl/Cmd + Shift + click      → navigate to docs page in a new tab
 * - Ctrl/Cmd held while hovering  → underline the function name
 */
fun dslGoToDocsExtension(
    docProvider: (String) -> KlangSymbol?,
    onNavigate: (doc: KlangSymbol, event: dynamic) -> Unit,
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

    fun showContextMenu(event: dynamic, func: KlangSymbol) {
        // Remove any stale menu from a previous right-click
        document.getElementById("cm-dsl-ctx-menu")?.asDynamic()?.remove()

        val menu = (document.createElement("div") as HTMLElement).also { el ->
            el.id = "cm-dsl-ctx-menu"
            el.style.cssText = """
                position:fixed;left:${event.clientX}px;top:${event.clientY}px;
                background:#fff;border:1px solid #ccc;border-radius:4px;
                box-shadow:0 2px 8px rgba(0,0,0,.2);padding:4px 0;
                z-index:9999;font-size:13px;font-family:inherit;min-width:160px;
            """.trimIndent()
        }

        fun menuItem(label: String, onItemClick: (dynamic) -> Unit): HTMLElement =
            (document.createElement("div") as HTMLElement).also { el ->
                el.style.cssText = "padding:7px 14px;cursor:pointer;color:#333;white-space:nowrap;"
                el.textContent = label
                el.onmouseover = { el.style.backgroundColor = "#f0f4ff"; null }
                el.onmouseout = { el.style.backgroundColor = ""; null }
                el.onclick = { e: dynamic -> onItemClick(e); menu.asDynamic().remove(); null }
            }

        // Same tab: pass the actual click event (shiftKey will be false)
        menu.appendChild(menuItem("Go to docs: ${func.name}") { e ->
            onNavigate(func, e)
        })
        // New tab: synthesise an event-like object with shiftKey = true
        menu.appendChild(menuItem("Open docs in new tab: ${func.name}") { e ->
            onNavigate(func, jsObject<dynamic> { this.shiftKey = true; this.ctrlKey = e.ctrlKey; this.metaKey = e.metaKey })
        })

        document.body?.appendChild(menu)

        // Dismiss on the next click outside the menu.
        // Deferred via setTimeout so the current right-click event doesn't immediately close it.
        window.setTimeout({
            var listener: ((dynamic) -> Unit)? = null
            listener = { e: dynamic ->
                if (menu.asDynamic().contains(e.target) != true) {
                    menu.asDynamic().remove()
                }
                document.asDynamic().removeEventListener("click", listener)
            }
            document.asDynamic().addEventListener("click", listener)
        }, 0)
    }

    // ── DOM event handlers ─────────────────────────────────────────────────

    val handlers = jsObject<dynamic> {

        // Right-click → context menu
        this.contextmenu = contextmenu@{ event: dynamic, view: EditorView ->
            val result = wordDocAt(view, event) ?: return@contextmenu false
            event.preventDefault()
            showContextMenu(event, result.second)
            true
        }

        // Ctrl/Cmd + click → same tab  |  Ctrl/Cmd + Shift + click → new tab
        this.click = click@{ event: dynamic, view: EditorView ->
            if (!isModifier(event)) return@click false
            val result = wordDocAt(view, event) ?: return@click false
            event.preventDefault()
            onNavigate(result.second, event)
            true
        }

        // Ctrl/Cmd held while moving → underline the token under the cursor
        this.mousemove = { event: dynamic, view: EditorView ->
            if (isModifier(event)) {
                val result = wordDocAt(view, event)
                if (result != null) {
                    view.dispatch(view.state.update(jsObject {
                        this.effects = setUnderlineEffect.of(result.first)
                    }))
                } else {
                    dispatchClear(view)
                }
            } else {
                dispatchClear(view)
            }
            false
        }

        // Mouse leaves editor → clear underline
        this.mouseleave = { _: dynamic, view: EditorView ->
            dispatchClear(view)
            false
        }

        // Ctrl/Cmd released → clear underline
        this.keyup = { event: dynamic, view: EditorView ->
            if (event.key == "Control" || event.key == "Meta") dispatchClear(view)
            false
        }
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
