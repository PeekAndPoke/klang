/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.pages.docs.lexikon.LexikonDomain
import io.peekandpoke.klang.pages.docs.lexikon.LexikonEntry
import io.peekandpoke.klang.pages.docs.lexikon.allLexikonEntries
import io.peekandpoke.klang.pages.docs.lexikon.lexikonAutoLinks
import io.peekandpoke.klang.pages.docs.lexikon.lexikonEntryBySlug
import io.peekandpoke.klang.pages.docs.lexikon.matchesSearch
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
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
 * help should open "another tab in bottom tool [...] with the lexikon entry", so the user stays in the editor): one
 * term laid out for the wide, low panel, or, before a term is picked, the terms to pick from, searchable.
 *
 * [Props.slug] is the term shown; [Props.onPick] gets a term picked here, or null for the list.
 */
class LexikonPanel(ctx: Ctx<Props>) : Component<LexikonPanel.Props>(ctx) {

    data class Props(
        val slug: Stream<String?>,
        val onPick: (slug: String?) -> Unit,
    )

    /** A term picked or put back: once drawn, a focus lost on the way goes to what comes next. */
    private val slug by subscribingTo(props.slug) { keepFocus() }

    /** What the list's search field holds. */
    private var query: String by value("")

    override fun VDom.render() {
        val entry = slug?.let { lexikonEntryBySlug(it) }

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "12px")
                put("color", "var(--kl-ink-text)")
            }
            onClick { event -> followInPlace(event) }

            if (entry == null) {
                key = "lexikon-terms"
                renderTerms()
            } else {
                key = "lexikon-${entry.slug}"
                renderEntry(entry)
            }
        }
    }

    /**
     * After a switch (a term picked, "All terms", a Lexikon link in the help), the key pressed is gone and the focus
     * with it: it goes to "All terms" on a term, to the search on the list, so the keyboard goes on from there.
     */
    private fun keepFocus() {
        window.requestAnimationFrame {
            val now = document.activeElement

            if (now == null || now == document.body) {
                // The back key itself, or the field inside the search's box
                (dom?.querySelector("button[data-focus-next], [data-focus-next] input") as? HTMLElement)?.focus()
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

    /** The terms to pick from, by domain, narrowed by the search. */
    private fun FlowContent.renderTerms() {
        div {
            css { put("max-width", "360px") }
            attributes["data-focus-next"] = ""

            KlangSearchField(label = "Search the Lexikon", value = query, onInput = { query = it }, placeholder = "Search terms")
        }

        val found = allLexikonEntries.filter { it.matchesSearch(query) }

        if (found.isEmpty()) {
            div {
                css { put("color", "var(--kl-ink-label)") }

                +"No term matches \"${query.trim()}\"."
            }
        }

        for (domain in LexikonDomain.entries) {
            val terms = found.filter { it.domain == domain }

            if (terms.isEmpty()) {
                continue
            }

            div {
                key = "domain-${domain.name}"
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("gap", "6px")
                }

                renderLabel(domain.label)

                div {
                    css {
                        put("display", "flex")
                        put("flex-wrap", "wrap")
                        put("gap", "6px")
                    }

                    for (term in terms) {
                        KlangButton(onClick = { props.onPick(term.slug) }) { +term.term }
                    }
                }
            }
        }
    }

    /** One term: its name and where it belongs, its summary, then its detail beside what else it is called and its story. */
    private fun FlowContent.renderEntry(entry: LexikonEntry) {
        // Back to the terms, quiet and on the left, where the eye starts (the maintainer, 2026-10-27: the key at the
        // far right was "too far away from the action")
        button(type = ButtonType.button) {
            css {
                put("align-self", "flex-start")
                put("display", "inline-flex")
                put("align-items", "center")
                put("gap", "2px")
                put("padding", "0")
                put("border", "none")
                put("background", "transparent")
                put("font-size", "12px")
                put("color", "var(--kl-ink-label)")
                put("cursor", "pointer")
            }
            attributes["data-focus-next"] = ""
            onClick { props.onPick(null) }

            icon.angle_left.render { attributes["aria-hidden"] = "true" }
            +"All terms"
        }

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
                put("font-size", "15px")
                put("color", "var(--kl-ink-strong)")
            }

            +entry.summary
        }

        div {
            css {
                put("display", "grid")
                put("grid-template-columns", "minmax(0, 3fr) minmax(0, 2fr)")
                put("gap", "24px")
            }

            div {
                css { put("line-height", "1.55") }

                // Its terms linked to their entries (KLANG-00369), never to itself; a link followed stays in the tab
                MarkdownDisplay(lexikonAutoLinks(markdown = entry.detail, self = entry.slug))
            }

            div {
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("gap", "10px")
                }

                entry.conventional?.let { conventional ->
                    div {
                        renderLabel("Elsewhere known as")
                        div { +conventional }
                    }
                }

                entry.trivia?.let { trivia ->
                    div {
                        renderLabel("Did you know")
                        div {
                            css { put("line-height", "1.5") }

                            +trivia.text
                        }

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
