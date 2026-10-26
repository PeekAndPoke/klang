package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.klang.workshop.keys.KeyCapGlyphs.keyCapGlyph
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.kbd
import kotlinx.html.span
import kotlinx.html.style

/**
 * The plain default look of a key hint; an app sets its own [KeyHintRenderer] once for the whole app
 * (`KraftApp.Builder.keys`) or per [OnKey]. A renderer draws only the box: the [KeyHintStage] places it.
 */
object KeyHints {

    /** The CSS class of [badge]'s element, for an app's stylesheet. */
    const val BADGE_CLASS = "key-hint"

    /** The CSS class of [overview]'s element, for an app's stylesheet. */
    const val OVERVIEW_CLASS = "key-overview"

    /** The CSS class of [waiting]'s element, for an app's stylesheet. */
    const val WAITING_CLASS = "key-waiting"

    /** One way to press a key: its parts side by side, each in a plain key cap; muted while the action cannot act. */
    fun FlowContent.caps(parts: List<String>, enabled: Boolean = true) {
        val text = if (enabled) "#fff" else "#999"

        span {
            style = "display: inline-flex; align-items: center; gap: 3px; white-space: nowrap"

            parts.forEach { part ->
                kbd {
                    style = listOf(
                        "display: inline-flex",
                        "align-items: center",
                        "justify-content: center",
                        "box-sizing: border-box",
                        "height: 20px",
                        "min-width: 20px",
                        "padding: 0 4px",
                        "border: 1px solid #777",
                        "border-bottom-width: 2px",
                        "border-radius: 3px",
                        "background: #333",
                        "color: $text",
                        "font-family: inherit",
                        "font-size: 12px",
                        "line-height: 1.4",
                        "text-align: center",
                    ).joinToString("; ")

                    keyCapGlyph(part = part, sizePx = 12)
                }
            }
        }
    }

    /** One way to press a key, step by step: each step's plain caps, a small gap between the steps. */
    fun FlowContent.capsGroups(groups: List<List<String>>, enabled: Boolean = true) {
        span {
            style = "display: inline-flex; align-items: center; gap: 6px; white-space: nowrap"

            groups.forEach { group -> caps(parts = group, enabled = enabled) }
        }
    }

    /** Each key in a small key cap on an opaque dark frame. */
    val badge: KeyHintRenderer = { state ->
        span(classes = BADGE_CLASS) {
            style = "display: inline-flex; padding: 3px 4px; border-radius: 4px; background: #222"

            capsGroups(groups = state.keyGroups(compact = true), enabled = state.enabled)
        }
    }

    /** The chords pressed so far, then an ellipsis for the key to come, on the same dark frame as [badge]. */
    val waiting: KeyWaitingRenderer = { state ->
        span(classes = WAITING_CLASS) {
            style = listOf(
                "display: inline-flex",
                "align-items: center",
                "gap: 6px",
                "padding: 4px 8px",
                "border-radius: 4px",
                "background: #222",
                "color: #fff",
            ).joinToString("; ")

            capsGroups(groups = state.keyGroups())
            +"…"
        }
    }

    /** A plain dark panel: each row's title, and its keys in key caps. */
    val overview: KeyOverviewRenderer = { state ->
        div(classes = OVERVIEW_CLASS) {
            style = listOf(
                "display: grid",
                "grid-template-columns: auto auto",
                "column-gap: 16px",
                "row-gap: 4px",
                "align-items: center",
                "padding: 8px 10px",
                "border-radius: 4px",
                "background: #222",
                "color: #ddd",
                "font-size: 12px",
            ).joinToString("; ")

            state.title?.let { title ->
                div {
                    style = "grid-column: 1 / span 2; font-weight: bold"
                    +title
                }
            }

            state.entries.forEach { entry ->
                span { +entry.title }

                span {
                    style = "display: inline-flex; gap: 6px; justify-self: end"

                    entry.keys.forEachIndexed { index, groups ->
                        if (index > 0) {
                            +"/"
                        }

                        capsGroups(groups = groups)
                    }
                }
            }
        }
    }
}
