package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.popups.PopupsManager
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onMouseEnter
import de.peekandpoke.ultra.html.onMouseLeave
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.span

// ── Tool-level header with (i) icon ──────────────────────────────────────────

/**
 * Renders a flex row with [title] on the left and an (i) icon on the right.
 * Hovering the (i) shows the symbol description popup.
 * If the symbol has no description, falls back to a plain `ui.small.header`.
 */
internal fun FlowContent.toolHeaderWithInfo(
    title: String,
    ctx: KlangUiToolContext,
    popupCtrl: HoverPopupCtrl?,
) {
    val description = ctx.symbol.variants
        .firstOrNull { it.description.isNotBlank() }
        ?.description
        ?.split("\n\n")?.first()?.trim()

    if (description == null || popupCtrl == null) {
        ui.small.header { +title }
        return
    }

    div {
        css {
            display = Display.flex
            alignItems = Align.center
            justifyContent = JustifyContent.spaceBetween
            marginBottom = 8.px
        }
        ui.small.header {
            css { margin = Margin(0.px) }
            +title
        }
        infoIconWithPopup(description, popupCtrl)
    }
}

// ── Per-field info icon (single-param tools) ─────────────────────────────────

/**
 * Looks up the description for [paramName] from the symbol and renders an (i) icon
 * that shows the description on hover. Renders nothing if no description exists.
 */
internal fun FlowContent.paramInfoIcon(
    paramName: String,
    ctx: KlangUiToolContext,
    popupCtrl: HoverPopupCtrl?,
) {
    if (popupCtrl == null) return

    val description = ctx.symbol.variants.filterIsInstance<KlangCallable>()
        .flatMap { it.params }
        .firstOrNull { it.name == paramName }
        ?.description
        ?.takeIf { it.isNotBlank() }
        ?: return

    infoIconWithPopup(description, popupCtrl)
}

// ── Per-sub-field info icon (multi-field tools) ──────────────────────────────

/**
 * Looks up the description for [subFieldName] within [paramName]'s subFields.
 * Renders an (i) icon that shows the description on hover.
 * Renders nothing if no description exists.
 */
internal fun FlowContent.subFieldInfoIcon(
    paramName: String,
    subFieldName: String,
    ctx: KlangUiToolContext,
    popupCtrl: HoverPopupCtrl?,
) {
    if (popupCtrl == null) return

    val description = ctx.symbol.variants.filterIsInstance<KlangCallable>()
        .flatMap { it.params }
        .firstOrNull { it.name == paramName }
        ?.subFields?.get(subFieldName)
        ?.takeIf { it.isNotBlank() }
        ?: return

    infoIconWithPopup(description, popupCtrl)
}

// ── Shared icon renderer ─────────────────────────────────────────────────────

/**
 * Renders a small (i) icon that shows a text description popup on hover.
 */
private fun FlowContent.infoIconWithPopup(
    description: String,
    popupCtrl: HoverPopupCtrl,
) {
    val laf = KlangTheme()

    span {
        css {
            color = Color(laf.textTertiary)
            marginLeft = 4.px
            opacity = 0.6
            hover {
                opacity = 1.0
                color = Color(laf.textSecondary)
            }
        }
        onMouseEnter { event ->
            popupCtrl.scheduleShow(event = event, positioning = PopupsManager.Positioning.TopRight) { _ ->
                ui.compact.segment {
                    css {
                        maxWidth = 30.vw
                        width = LinearDimension.maxContent
                    }

                    MarkdownDisplay(description)
                }
            }
        }
        onMouseLeave {
            popupCtrl.scheduleClose()
        }
        icon.info_circle()
    }
}
