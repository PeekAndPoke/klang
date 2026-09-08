/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.SimpleAsyncQueue
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.key
import kotlinx.coroutines.delay
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.unsafe
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
fun Tag.MarkdownDisplay(
    markdown: String,
    key: String = "md-" + markdown.hashCode().toString(),
    linksInNewTab: Boolean = false,
) = comp(
    MarkdownDisplay.Props(markdown = markdown, key = key, linksInNewTab = linksInNewTab)
) {
    MarkdownDisplay(it)
}

class MarkdownDisplay(ctx: Ctx<Props>) : Component<MarkdownDisplay.Props>(ctx) {

    companion object {
        val cache = mutableMapOf<String, String>()

        private val anchorOpening = Regex("<a\\s", RegexOption.IGNORE_CASE)

        /** `rel` as well as `target`: a bare `target="_blank"` hands the opener to the new page. */
        private fun String.withLinksInNewTab(): String =
            replace(anchorOpening, """<a target="_blank" rel="noopener noreferrer" """)
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val markdown: String,
        val key: String,
        /**
         * Open the rendered links in a new tab.
         *
         * Set it wherever a navigation would destroy work in progress: the app uses path routing, so a
         * plain link is a full page load, and a docs popup floating over the editor would take the
         * unsaved song with it.
         */
        val linksInNewTab: Boolean = false,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val marked by subscribingTo(addons.marked) {
        q.add {
            delay(1)
            updateMd(props.markdown)
        }
    }

    private var md: String? by value(null)
    private val q: SimpleAsyncQueue = SimpleAsyncQueue()

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    init {
        lifecycle {
            onNextProps { new, _ ->
                q.add {
                    delay(1.milliseconds)
                    updateMd(new.markdown)
                }
            }
        }
    }

    private fun updateMd(markdown: String) {
        val m = marked ?: return

        md = try {
            cache.getOrPut(cacheKey(markdown)) {
                m.markdown2html(markdown).let { html ->
                    if (props.linksInNewTab) html.withLinksInNewTab() else html
                }
            }
        } catch (e: Exception) {
            console.warn("Error rendering markdown:", e)
            markdown
        }
    }

    /** The same markdown renders differently per [Props.linksInNewTab], so the flag belongs in the key. */
    private fun cacheKey(markdown: String) = if (props.linksInNewTab) "newtab:$markdown" else markdown

    override fun VDom.render() {
        div {
            key = props.key
            md?.let {
                unsafe { +it }
            }
        }
    }
}
