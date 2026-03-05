package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.popups.PopupsManager
import de.peekandpoke.kraft.utils.Vector2D
import de.peekandpoke.kraft.utils.jsObject
import de.peekandpoke.ultra.html.onMouseEnter
import de.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.klang.codemirror.ext.Coords
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.comp.KlangSymbolDocsComp
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.browser.window
import kotlinx.html.div

fun dslHoverTooltipExtension(
    docProvider: (String) -> KlangSymbol?,
    onNavigate: (doc: KlangSymbol, event: dynamic) -> Unit,
    popups: PopupsManager,
    showDelayMs: Int = 300,
    hideDelayMs: Int = 500,
): Extension {
    var lastHoveredWord: String? = null
    var showTimer: Int? = null
    var closeTimer: Int? = null

    fun scheduleClose() {
        showTimer?.let { window.clearTimeout(it) }
        showTimer = null
        closeTimer?.let { window.clearTimeout(it) }
        closeTimer = window.setTimeout({
            popups.closeAll()
            closeTimer = null
        }, hideDelayMs)
    }

    fun cancelClose() {
        closeTimer?.let { window.clearTimeout(it) }
        closeTimer = null
    }

    val onMouseMove: (event: dynamic, view: EditorView) -> Boolean = { event, view ->
        val coords = jsObject<dynamic> {
            this.x = event.clientX
            this.y = event.clientY
        }.unsafeCast<Coords>()
        val pos = view.posAtCoords(coords)
        val word = pos?.let { view.state.wordAt(it) }
        val wordRect = word?.let { view.coordsAtPos(it.from) }
        val name = word?.let { view.state.doc.sliceString(word.from, word.to) }
        val doc = name?.let { docProvider(it) }
        val currentWord = if (doc != null) name else null

        if (currentWord != lastHoveredWord) {
            @Suppress("AssignedValueIsNeverRead")
            lastHoveredWord = currentWord

            if (doc != null) {
                cancelClose()
                showTimer?.let { window.clearTimeout(it) }

                val anchor = Vector2D(
                    x = wordRect?.left ?: event.clientX,
                    y = wordRect?.top ?: event.clientY,
                )

                showTimer = window.setTimeout({
                    showTimer = null
                    popups.showContextMenu(
                        anchor = anchor,
                        positioning = PopupsManager.Positioning.TopLeft,
                    ) { _ ->
                        div {
                            onMouseEnter { cancelClose() }
                            onMouseLeave { scheduleClose() }
                            KlangSymbolDocsComp(symbol = doc, onNavigate = onNavigate)
                        }
                    }
                }, showDelayMs)
            } else {
                scheduleClose()
            }
        } else if (currentWord != null) {
            cancelClose()
        }
        false
    }

    val onMouseLeave: (event: dynamic, view: EditorView) -> Boolean = { _, _ ->
        lastHoveredWord = null
        scheduleClose()
        false
    }

    val handlers = jsObject<dynamic> {
        this.mousemove = onMouseMove
        this.mouseleave = onMouseLeave
    }

    return EditorView.domEventHandlers(handlers).unsafeCast<Extension>()
}
