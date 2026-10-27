/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.pages.docs.lexikon.LexikonEntry
import io.peekandpoke.klang.pages.docs.lexikon.allLexikonEntries
import io.peekandpoke.klang.pages.docs.lexikon.lexikonAutoLinks
import io.peekandpoke.klang.pages.docs.lexikon.lexikonEntryBySlug
import io.peekandpoke.klang.pages.docs.lexikon.matchesSearch
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.streams.Stream
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent

/** A Lexikon entry's page in a link (`/manuals/lexikon/orbit-bus`, as the DSL docs write it), its slug; else null. */
fun lexikonSlugOf(href: String): String? = lexikonHref.find(href)?.groupValues?.get(1)

/** A line of the entry's text, at most: longer lines are hard to follow back to their start. */
private const val READING_WIDTH = "76ch"

private val lexikonHref = Regex("""/manuals/lexikon/([a-z0-9-]+)/?(?:[?#].*)?$""")

@Suppress("FunctionName")
fun Tag.LexikonPanel(
    slug: Stream<String?>,
    onPick: (slug: String?) -> Unit,
) = comp(
    LexikonPanel.Props(slug = slug, onPick = onPick)
) {
    LexikonPanel(it)
}

/**
 * The Lexikon in the song page's bottom panel (KLANG-00365; the maintainer, 2026-10-27: a Lexikon link clicked in the
 * help should open "another tab in bottom tool [...] with the lexikon entry", so the user stays in the editor). The
 * wide, low panel splits: the terms on the left, searchable, the one shown marked; the term on the right. No way back
 * is needed, the list is always there (the maintainer, 2026-10-27, after a back link in the headline's row read as "a
 * stranger"). Each side scrolls on its own.
 *
 * [Props.slug] is the term shown, null for none yet; [Props.onPick] gets a term picked here.
 */
class LexikonPanel(ctx: Ctx<Props>) : Component<LexikonPanel.Props>(ctx) {

    data class Props(
        val slug: Stream<String?>,
        val onPick: (slug: String?) -> Unit,
    )

    /** A term shown from elsewhere (a link in an entry, the help): its item in view in the list, the focus kept. */
    private val slug by subscribingTo(props.slug) { showInList(it) }

    /** What the list's search field holds. */
    private var query: String by value("")

    override fun VDom.render() {
        val entry = slug?.let { lexikonEntryBySlug(it) }

        div {
            css {
                put("display", "grid")
                put("grid-template-columns", "220px minmax(0, 1fr)")
                put("gap", "24px")
                put("height", "100%")
                put("color", "var(--kl-ink-text)")
            }
            onClick { event -> followInPlace(event) }

            renderTerms(shown = entry)

            div {
                key = "lexikon-${entry?.slug ?: "none"}"
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("gap", "12px")
                    put("min-height", "0")
                    put("overflow-y", "auto")
                }

                if (entry == null) {
                    div {
                        css { put("color", "var(--kl-ink-label)") }

                        +"Pick a term on the left, or follow a Lexikon link in the help."
                    }
                } else {
                    renderEntry(entry)
                }
            }
        }
    }

    /**
     * Brings the shown term's item into view in the list (only the list scrolls: scrollIntoView would also move the
     * workbench's boxes), and when the focus was lost with the link followed, puts it on that item.
     */
    private fun showInList(slug: String?) {
        slug ?: return

        window.requestAnimationFrame {
            val list = dom?.querySelector("[data-terms]") as? HTMLElement ?: return@requestAnimationFrame
            val item = list.querySelector("[data-term='$slug']") as? HTMLElement ?: return@requestAnimationFrame
            val top = item.offsetTop - list.offsetTop

            if (top < list.scrollTop || top + item.offsetHeight > list.scrollTop + list.clientHeight) {
                list.scrollTop = (top - 8).toDouble()
            }

            val now = document.activeElement

            if (now == null || now == document.body) {
                // Without scrolling: the list was scrolled above, nothing else should move
                item.asDynamic().focus(js("({ preventScroll: true })"))
            }
        }
    }

    /** A plain click on a link to another entry shows it here; with a key held, the link keeps its own way. */
    private fun followInPlace(event: Event) {
        val mouse = event as? MouseEvent ?: return

        if (mouse.button.toInt() != 0 || mouse.ctrlKey || mouse.metaKey || mouse.shiftKey || mouse.altKey) {
            return
        }

        val link = (event.target as? Element)?.closest("a") ?: return
        val slug = lexikonSlugOf(link.getAttribute("href") ?: return) ?: return

        event.preventDefault()
        props.onPick(slug)
    }

    /** The terms, by name, narrowed by the search; the one [shown] marked. Its own scroller, under the search. */
    private fun FlowContent.renderTerms(shown: LexikonEntry?) {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "8px")
                put("min-height", "0")
            }

            KlangSearchField(label = "Search the Lexikon", value = query, onInput = { query = it }, placeholder = "Search terms")

            val found = allLexikonEntries.filter { it.matchesSearch(query) }.sortedBy { it.term.lowercase() }

            // Outside the list: a list holds only its items
            if (found.isEmpty()) {
                div {
                    css {
                        put("padding", "4px 8px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +"No term matches."
                }
            }

            div {
                attributes["role"] = "list"
                attributes["data-terms"] = ""
                attributes["aria-label"] = "Terms"
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("min-height", "0")
                    put("overflow-y", "auto")
                }

                for (term in found) {
                    val current = term == shown

                    // An item holding a plain button, so it is read as one that can be pressed
                    div {
                        key = term.slug
                        attributes["role"] = "listitem"
                        css { put("display", "flex") }

                        button(type = ButtonType.button, classes = "kl-quiet-key") {
                            attributes["data-term"] = term.slug

                            if (current) {
                                attributes["aria-current"] = "true"
                            }

                            css {
                                put("flex", "1")
                                put("text-align", "left")
                                put("border-radius", "var(--kl-radius)")

                                if (current) {
                                    put("background", "var(--kl-ground-panel)")
                                    put("color", "var(--kl-ink-strong)")
                                    put("font-weight", "700")
                                }
                            }
                            onClick { props.onPick(term.slug) }

                            +term.term
                        }
                    }
                }
            }
        }
    }

    /** One term: its name and where it belongs, its summary and what else it is called, its detail, then its story. */
    private fun FlowContent.renderEntry(entry: LexikonEntry) {
        div {
            css {
                put("display", "flex")
                put("align-items", "baseline")
                put("flex-wrap", "wrap")
                put("gap", "10px")
            }

            span {
                css {
                    put("font-size", "18px")
                    put("color", "var(--kl-ink-strong)")
                }

                +entry.term
            }

            for (label in listOf(entry.category.label) + entry.tags.map { it.label }) {
                renderChip(label)
            }
        }

        div {
            css {
                put("max-width", READING_WIDTH)
                put("font-size", "15px")
                put("color", "var(--kl-ink-strong)")
            }

            +entry.summary
        }

        // Short, so on one line with the summary's facts, before the long text
        entry.conventional?.let { conventional ->
            div {
                css {
                    put("display", "flex")
                    put("align-items", "baseline")
                    put("flex-wrap", "wrap")
                    put("gap", "4px 10px")
                }

                renderLabel("Elsewhere known as")
                span { +conventional }
            }
        }

        // One column, read from top to bottom at a comfortable length: two fixed columns left one side empty while the
        // other ran on under the fold, and balanced columns taller than the panel meant scrolling back up to read on
        // (the maintainer, 2026-10-27: "not a nice layout")
        div {
            css {
                put("max-width", READING_WIDTH)
                put("line-height", "1.55")
            }

            // Its terms linked to their entries (KLANG-00369), never to itself; a link followed stays in the tab
            MarkdownDisplay(lexikonAutoLinks(markdown = entry.detail, self = entry.slug))
        }

        // The story beside the facts: quieter, set off by a line
        entry.trivia?.let { trivia ->
            div {
                css {
                    put("max-width", READING_WIDTH)
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("gap", "4px")
                    put("padding-left", "12px")
                    put("border-left", "2px solid color-mix(in srgb, var(--kl-ink-label) 35%, transparent)")
                    put("font-size", "13px")
                    put("line-height", "1.5")
                }

                renderLabel("Did you know")
                div { +trivia.text }

                val meta = listOfNotNull(trivia.year, trivia.person, trivia.device)

                if (meta.isNotEmpty()) {
                    div {
                        css {
                            put("font-size", "12px")
                            put("color", "var(--kl-ink-label)")
                        }

                        +meta.joinToString(", ")
                    }
                }

                trivia.url?.let { url ->
                    a(href = url, target = "_blank") {
                        css {
                            put("font-size", "12px")
                            put("color", "var(--kl-accent)")
                        }

                        +"Read more"
                    }
                }
            }
        }
    }

    private fun FlowContent.renderLabel(text: String) {
        div {
            css {
                put("font-family", "monospace")
                put("font-size", "11px")
                put("letter-spacing", "0.18em")
                put("text-transform", "uppercase")
                put("color", "var(--kl-ink-label)")
            }

            +text
        }
    }

    private fun FlowContent.renderChip(text: String) {
        span {
            css {
                put("font-size", "11px")
                put("letter-spacing", "0.08em")
                put("text-transform", "uppercase")
                put("color", "var(--kl-ink-label)")
                put("border", "1px solid color-mix(in srgb, var(--kl-ink-label) 40%, transparent)")
                put("border-radius", "3px")
                put("padding", "1px 6px")
            }

            +text
        }
    }
}
