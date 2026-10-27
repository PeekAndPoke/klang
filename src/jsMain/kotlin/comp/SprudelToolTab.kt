/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.defaultTool
import io.peekandpoke.klang.ui.codemirror.ToolCallSite
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Stream
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.SprudelToolTab(
    site: Stream<ToolCallSite?>,
    contextOf: (ToolCallSite) -> KlangUiToolContext?,
) = comp(
    SprudelToolTab.Props(site = site, contextOf = contextOf)
) {
    SprudelToolTab(it)
}

/**
 * The bottom panel's Sprudel tab (KLANG-00374): the tool of the call the cursor is in, live. It follows the editor's
 * cursor ([ToolCallSite]); a change in the tool writes into the code at once (one undo per drag), and a playing song
 * follows. Where a parameter has a value tool and a sequence tool, the one for the argument as written is shown (a
 * string: the sequence), the other a key away.
 *
 * The tool starts over when the site's key changes (another call or parameter, or the code edited by other hands); its
 * own changes keep it, so a drag is never cut short.
 */
class SprudelToolTab(ctx: Ctx<Props>) : Component<SprudelToolTab.Props>(ctx) {

    data class Props(
        val site: Stream<ToolCallSite?>,
        /** The context for a site's tool, from the editor that found it; null when there is no editor (any more). */
        val contextOf: (ToolCallSite) -> KlangUiToolContext?,
    )

    private val site by subscribingTo(props.site)

    /** The tool picked by its key, for the site it was picked on. */
    private var picked: Pair<String, String>? by value(null)

    /** The context made for the site shown, so a redraw keeps the one the tool works with. */
    private var context: Pair<String, KlangUiToolContext>? = null

    override fun VDom.render() {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "12px")
                put("padding-bottom", "16px")
                put("color", "var(--kl-ink-text)")
            }

            val shown = site

            if (shown == null) {
                renderHint()
            } else {
                renderSite(shown)
            }
        }
    }

    private fun FlowContent.renderHint() {
        div {
            css {
                put("color", "var(--kl-ink-label)")
                put("font-size", "13px")
            }

            +"Put the cursor inside the parentheses of a call with a tool, as lpf( ), adsr( ) or gain( ), and its tool shows here."
        }
    }

    private fun FlowContent.renderSite(site: ToolCallSite) {
        val default = site.tools.defaultTool(site.currentValue.orEmpty()) ?: return
        val toolName = picked?.takeIf { it.first == site.key }?.second?.takeIf { name -> site.tools.any { it.first == name } }
            ?: default.first
        val tool = site.tools.first { it.first == toolName }.second

        div {
            css {
                put("display", "flex")
                put("align-items", "center")
                put("flex-wrap", "wrap")
                put("gap", "10px")
            }

            span {
                css {
                    put("font-family", "monospace")
                    put("font-size", "15px")
                    put("font-weight", "700")
                    put("color", "var(--kl-ink-strong)")
                }

                +"${site.symbol.name}(${site.paramName})"
            }

            // A value tool and its sequence tool: the other a key away
            if (site.tools.size > 1) {
                div {
                    attributes["role"] = "group"
                    attributes["aria-label"] = "Tools"
                    css {
                        put("display", "flex")
                        put("gap", "4px")
                    }

                    for ((name, each) in site.tools) {
                        KlangButton(onClick = { picked = site.key to name }, pressed = name == toolName) {
                            +each.label(name)
                        }
                    }
                }
            }
        }

        val toolContext = contextFor(site = site, toolName = toolName) ?: return

        div {
            key = "tool-${site.key}-$toolName"
            css { put("max-width", "760px") }

            tool.apply {
                if (this is KlangUiToolEmbeddable) {
                    renderEmbedded(toolContext)
                } else {
                    // A tool made for a modal only (none of sprudel's since KLANG-00374 step 3): its own buttons
                    render(toolContext)
                }
            }
        }
    }

    /** One context per site and tool: a tool picked after another one wrote reads the code as it is then. */
    private fun contextFor(site: ToolCallSite, toolName: String): KlangUiToolContext? {
        val key = "${site.key}:$toolName"

        context?.takeIf { it.first == key }?.let { return it.second }

        return props.contextOf(site)?.also { context = key to it }
    }

    /** A tool's name for its key: its title, else what its registered name says. */
    private fun KlangUiTool.label(name: String): String =
        title ?: if (editsSequence) "Sequence" else name.removePrefix("Sprudel").removeSuffix("Editor")
}
