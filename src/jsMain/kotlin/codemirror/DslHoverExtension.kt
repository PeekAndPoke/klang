package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.utils.Vector2D
import de.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.klang.codemirror.ext.Coords
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.KlangDocsHoverPopupCtrl

fun dslHoverTooltipExtension(
    docProvider: (String) -> KlangSymbol?,
    hoverPopup: KlangDocsHoverPopupCtrl,
): Extension {
    var lastHoveredWord: String? = null

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
                val anchor = Vector2D(x = wordRect?.left ?: event.clientX, y = wordRect?.top ?: event.clientY)
                hoverPopup.scheduleShow(doc, anchor)
            } else {
                hoverPopup.scheduleClose()
            }
        } else if (currentWord != null) {
            hoverPopup.cancelClose()
        }
        false
    }

    val onMouseLeave: (event: dynamic, view: EditorView) -> Boolean = { _, _ ->
        lastHoveredWord = null
        hoverPopup.scheduleClose()
        false
    }

    val handlers = jsObject<dynamic> {
        this.mousemove = onMouseMove
        this.mouseleave = onMouseLeave
    }

    return EditorView.domEventHandlers(handlers).unsafeCast<Extension>()
}
