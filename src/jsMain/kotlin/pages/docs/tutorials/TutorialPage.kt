/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.PlayableCodeExample
import io.peekandpoke.klang.comp.QuickHelpPanel
import io.peekandpoke.klang.comp.lexikonSlugOf
import io.peekandpoke.klang.comp.toAdsrSignals
import io.peekandpoke.klang.layouts.WorkbenchSlots
import io.peekandpoke.klang.pages.docs.lexikon.lexikonAutoLinks
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.klang.ui.comp.KlangAdsrSignal
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.css.CssBuilder
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.section
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent

@Suppress("FunctionName")
fun Tag.TutorialPage() = comp {
    TutorialPage(it)
}

/**
 * A tutorial on the workbench (KLANG-00370, the canvas's board "Learn: a tutorial"): its title, labels and what it is
 * about, then each section's prose at reading width with its examples to play; at its end "Mark as done" and the next
 * lesson. The column holds the track's lessons ([TutorialColumn]); the bottom panel the Quick
 * help and the Lexikon for the examples' code, as on the song page. A Lexikon term in the prose links itself, and a
 * plain click on it opens the Lexikon tab.
 */
class TutorialPage(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        const val PARAM_TRACK = "track"

        /** Counts the pages drawn, for the help panel's key. */
        private var pagesDrawn = 0

        /** The width of the page's content: the prose and the examples alike. */
        private const val CONTENT_WIDTH_PX = 980

    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val currentRoute by subscribingTo(router.current)

    private val done by subscribingTo(TutorialStorage.done)

    /** For a picture with no example after it: nothing ever comes. */
    private val noSignals: Stream<KlangAdsrSignal?> = StreamSource(null)

    /** Per ADSR picture (by tutorial, section, block), the notes its example plays; kept while the page is. */
    private val adsrSignals = mutableMapOf<String, StreamSource<KlangAdsrSignal?>>()

    private val helpPanel = QuickHelpPanel(pageKey = "tutorial-${++pagesDrawn}")

    /** The column this page put into the workbench; null while it put none. */
    private var slotColumn: (DIV.() -> Unit)? = null

    private fun currentTutorial(): Tutorial? = allTutorials.find { it.slug == currentRoute.matchedRoute["slug"] }

    init {
        lifecycle {
            onMount { fillWorkbench() }
            onUnmount { emptyWorkbench() }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun fillWorkbench() {
        // The copy of the page fading out after a change of page is inert: it must neither fill nor empty the slots,
        // or its cleanup would take the next page's column away (as the song page guards it)
        if (dom?.closest("[inert]") != null) {
            return
        }

        val column: DIV.() -> Unit = { TutorialColumn() }

        slotColumn = column
        WorkbenchSlots.column(column)
        WorkbenchSlots.bottom(helpPanel.bottom())
    }

    /** Empties the workbench's slots, unless another page has filled them since. */
    private fun emptyWorkbench() {
        if (slotColumn != null && WorkbenchSlots.column() === slotColumn) {
            WorkbenchSlots.clear()
        }

        slotColumn = null
    }

    /** A plain click on a Lexikon link in the prose opens it in the Lexikon tab; with a key held, the link's own way. */
    private fun openLexikonInPlace(event: Event) {
        val mouse = event as? MouseEvent ?: return

        if (mouse.button.toInt() != 0 || mouse.ctrlKey || mouse.metaKey || mouse.shiftKey || mouse.altKey) {
            return
        }

        val link = (event.target as? Element)?.closest("a") ?: return
        val slug = lexikonSlugOf(link.getAttribute("href") ?: return) ?: return

        event.preventDefault()
        helpPanel.showLexikon(slug)
    }

    override fun VDom.render() {
        val tutorial = currentTutorial()

        // One column, centred: equal margins left and right, the prose as wide as the examples (the maintainer)
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "22px")
                put("box-sizing", "border-box")
                put("max-width", "${CONTENT_WIDTH_PX + 96}px")
                put("margin", "0 auto")
                put("padding", "30px 48px 48px")
                put("color", "var(--kl-ink-text)")
            }
            onClick { event -> openLexikonInPlace(event) }

            if (tutorial == null) {
                backLink(router = router, label = "Learn", to = Nav.tutorials())
                div { +"There is no such tutorial." }

                return@div
            }

            key = "tutorial-${tutorial.slug}"

            renderHeader(tutorial)

            for ((index, section) in tutorial.sections.withIndex()) {
                renderSection(index = index, section = section, tutorial = tutorial)
            }

            renderEnd(tutorial)
        }
    }

    private fun FlowContent.renderHeader(tutorial: Tutorial) {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "8px")
            }

            h1 {
                css {
                    put("margin", "0")
                    put("font-size", "30px")
                    put("color", "var(--kl-ink-strong)")
                }

                +tutorial.title
            }

            div {
                css {
                    put("display", "flex")
                    put("flex-wrap", "wrap")
                    put("gap", "6px")
                }

                val also = allTracks.filter { it != theKlangPathTrack && tutorial in it.lessons }
                val labels = listOfNotNull(
                    tutorial.strand()?.label,
                    tutorial.difficulty.label,
                    tutorial.depth.label,
                ) + tutorial.tags.map { it.label } +
                        listOfNotNull(also.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Also in ") { it.title })

                for (label in labels) {
                    renderChip(label)
                }
            }

            div {
                css {
                    put("font-size", "16px")
                    put("line-height", "1.5")
                }

                +tutorial.description
            }
        }
    }

    private fun FlowContent.renderChip(text: String) {
        span {
            css {
                put("font-size", "10px")
                put("letter-spacing", "0.08em")
                put("text-transform", "uppercase")
                put("padding", "2px 7px")
                put("border-radius", "var(--kl-radius)")
                put("color", "var(--kl-ink-label)")
                put("border", "1px solid color-mix(in srgb, var(--kl-ink-label) 40%, transparent)")
            }

            +text
        }
    }

    private fun FlowContent.renderSection(index: Int, section: TutorialSection, tutorial: Tutorial) {
        section {
            key = "section-$index"
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "10px")
            }

            section.heading?.let { heading ->
                h2 {
                    css {
                        put("margin", "0")
                        put("font-size", "20px")
                        put("color", "var(--kl-ink-strong)")
                    }

                    +heading
                }
            }

            // A picture follows the players right after it (KLANG-00371): a stream per picture, fed by each of them
            val follows = section.blocks.indices
                .filter { section.blocks[it] is Block.Visual.Adsr }
                .associateWith { blockIndex -> adsrSignals.getOrPut("${tutorial.slug}/$index/$blockIndex") { StreamSource(null) } }
            val feeds = section.playersAfterPictures()

            for ((blockIndex, block) in section.blocks.withIndex()) {
                when (block) {
                    is Block.Text -> div {
                        css { renderProse() }

                        // Text blocks use \n\n as paragraph breaks: each its own paragraph
                        for (paragraph in block.text.split("\n\n").filter { it.isNotBlank() }) {
                            p { +paragraph }
                        }
                    }

                    is Block.Markdown -> div {
                        css { renderProse() }

                        // The Lexikon's terms link themselves (KLANG-00369); a click opens the Lexikon tab
                        MarkdownDisplay(markdown = lexikonAutoLinks(block.markdown))
                    }

                    is Block.Code -> div {
                        when (block.lang) {
                            "KlangScript" -> {
                                val pictureIndex = feeds[blockIndex]
                                val picture = pictureIndex?.let { section.blocks[it] as Block.Visual.Adsr }
                                val stream = pictureIndex?.let { follows[it] }

                                PlayableCodeExample(
                                    code = block.code,
                                    rpm = tutorial.rpm,
                                    help = helpPanel,
                                    onSignal = if (picture != null && stream != null) {
                                        { signal -> signal.toAdsrSignals(door = picture.envelope.door).forEach { stream(it) } }
                                    } else {
                                        null
                                    },
                                )
                            }
                            else -> pre { +block.code }
                        }
                    }

                    is Block.Visual.Adsr -> AdsrVisual(
                        visual = block,
                        example = (section.blocks.getOrNull(blockIndex + 1) as? Block.Code)?.code,  // the first player after it
                        signals = follows[blockIndex] ?: noSignals,
                    )
                }
            }
        }
    }

    /** Prose, as wide as the examples. */
    private fun CssBuilder.renderProse() {
        put("font-size", "15px")
        put("line-height", "1.6")
    }

    /**
     * At the page's end, one clear action on the right, a quiet key on the left (the maintainer, 2026-10-27: a checkbox
     * there was "easy to miss"): not done yet, "Done. On to ..." marks the lesson done and goes on to the track's next
     * one (the gold "try me" lamp only when the lesson is the next one), "Mark as done, stay here" beside it; done, its
     * lamp lit, "Not done after all", and the next lesson, or on a track's last lesson "Back to Learn".
     */
    private fun FlowContent.renderEnd(tutorial: Tutorial) {
        val track = tutorialTrack(currentRoute.matchedRoute.queryParams[PARAM_TRACK], tutorial)
        val next = track?.lessons?.let { lessons -> lessons.getOrNull(lessons.indexOf(tutorial) + 1) }
        val nextLabel = next?.let { "${track.lessons.indexOf(it) + 1}. ${it.title}" }
        val isDone = tutorial.slug in done

        fun goNext() {
            if (next != null && track != null) {
                router.navToUri(Nav.tutorialInTrack(slug = next.slug, trackSlug = track.slug))
            }
        }

        div {
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("align-items", "center")
                put("gap", "14px")
                put("padding", "22px 0 8px")
                put("border-top", "1px solid var(--kl-ground-panel)")
            }

            if (!isDone) {
                // As in the done state: the quiet key on the left, the one to press on the right (the maintainer)
                if (next != null) {
                    quietKey(label = "Mark as done, stay here", marker = "stay") {
                        TutorialStorage.setCompleted(tutorial.slug, true)
                    }
                }

                div { css { put("flex", "1") } }

                KlangButton(
                    onClick = {
                        TutorialStorage.setCompleted(tutorial.slug, true)
                        goNext()
                    },
                    // The size of the done state's Next key, its lamp inline (the maintainer: the tile was "too big");
                    // gold only on the lesson that is next, one gold lamp at a time
                    lamp = if (tutorial == TutorialProgress(done = done).next) KlangLamp.State.Try else null,
                ) {
                    attributes["data-done"] = "next"

                    +(nextLabel?.let { "Done. On to $it ›" } ?: "Mark this lesson as done")
                }
            } else {
                // The quiet key's text box: its size and its line height (a button's "normal", 1.15 in Lato), and no line
                // of its own around the lamp's label; centred in the row, the two then share one baseline (measured)
                span {
                    css {
                        put("display", "flex")
                        put("font-size", "13px")
                        put("line-height", "1.15")
                    }

                    KlangLamp(state = KlangLamp.State.On, label = "Done")
                }

                quietKey(label = "Not done after all", marker = "undo") {
                    TutorialStorage.setCompleted(tutorial.slug, false)
                }

                div { css { put("flex", "1") } }

                if (nextLabel != null) {
                    KlangButton(onClick = { goNext() }) {
                        attributes["data-next"] = next.slug
                        +"Next: $nextLabel ›"
                    }
                } else {
                    // The track's last lesson: back to Learn (the maintainer, 2026-10-27)
                    KlangButton(onClick = { router.navToUri(Nav.tutorials()) }) {
                        attributes["data-next"] = "learn"
                        +"Back to Learn ›"
                    }
                }
            }
        }
    }

    /** A quiet key: plain text in the label's ink, brighter under the mouse (`kl-quiet-key` in klang.css). */
    private fun FlowContent.quietKey(label: String, marker: String, onClick: () -> Unit) {
        button(type = ButtonType.button, classes = "kl-quiet-key") {
            attributes["data-done"] = marker
            onClick { onClick() }

            +label
        }
    }
}
