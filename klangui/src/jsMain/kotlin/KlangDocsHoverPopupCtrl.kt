package io.peekandpoke.klang.ui

import de.peekandpoke.kraft.popups.PopupsManager
import de.peekandpoke.kraft.utils.Vector2D
import de.peekandpoke.ultra.html.onMouseEnter
import de.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.div
import org.w3c.dom.events.MouseEvent

class KlangDocsHoverPopupCtrl(
    private val popups: PopupsManager,
    val showDelayMs: Int = 300,
    val hideDelayMs: Int = 500,
    private val content: FlowContent.(KlangSymbol) -> Unit,
) {
    private var showTimer: Int? = null
    private var closeTimer: Int? = null

    fun scheduleShow(
        doc: KlangSymbol,
        event: MouseEvent,
        positioning: PopupsManager.Positioning = PopupsManager.Positioning.TopLeft,
    ) {
        cancelClose()
        showTimer?.let { window.clearTimeout(it) }
        showTimer = window.setTimeout({
            showTimer = null
            popups.showContextMenu(event, positioning) { _ ->
                div {
                    onMouseEnter { cancelClose() }
                    onMouseLeave { scheduleClose() }
                    content(doc)
                }
            }
        }, showDelayMs)
    }

    fun scheduleShow(
        doc: KlangSymbol,
        anchor: Vector2D,
        positioning: PopupsManager.Positioning = PopupsManager.Positioning.TopLeft,
    ) {
        cancelClose()
        showTimer?.let { window.clearTimeout(it) }
        showTimer = window.setTimeout({
            showTimer = null
            popups.showContextMenu(anchor = anchor, positioning = positioning) { _ ->
                div {
                    onMouseEnter { cancelClose() }
                    onMouseLeave { scheduleClose() }
                    content(doc)
                }
            }
        }, showDelayMs)
    }

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
}
