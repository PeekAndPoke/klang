package io.peekandpoke.klang.ui

import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.utils.Vector2D
import io.peekandpoke.ultra.html.onMouseEnter
import io.peekandpoke.ultra.html.onMouseLeave
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.div
import org.w3c.dom.events.UIEvent

/**
 * Manages delayed show/hide for hover-triggered popups.
 *
 * Shared base for tool-info popups, docs hovers, and similar use cases.
 * Create one per component instance.
 */
open class HoverPopupCtrl(
    private val popups: PopupsManager,
    val showDelayMs: Int = 300,
    val hideDelayMs: Int = 500,
) {
    private var showTimer: Int? = null
    private var closeTimer: Int? = null

    fun scheduleShow(
        event: UIEvent,
        positioning: PopupsManager.Positioning = PopupsManager.Positioning.BottomCenter,
        content: FlowContent.(PopupsManager.Handle) -> Unit,
    ) {
        cancelClose()
        showTimer?.let { window.clearTimeout(it) }
        showTimer = window.setTimeout({
            showTimer = null
            popups.showContextMenu(event, positioning) { handle ->
                div {
                    onMouseEnter { cancelClose() }
                    onMouseLeave { scheduleClose() }
                    content(handle)
                }
            }
        }, showDelayMs)
    }

    fun scheduleShow(
        anchor: Vector2D,
        positioning: PopupsManager.Positioning = PopupsManager.Positioning.BottomCenter,
        content: FlowContent.(PopupsManager.Handle) -> Unit,
    ) {
        cancelClose()
        showTimer?.let { window.clearTimeout(it) }
        showTimer = window.setTimeout({
            showTimer = null
            popups.showContextMenu(anchor = anchor, positioning = positioning) { handle ->
                div {
                    onMouseEnter { cancelClose() }
                    onMouseLeave { scheduleClose() }
                    content(handle)
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
