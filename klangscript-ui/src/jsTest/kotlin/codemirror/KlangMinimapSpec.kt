/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.TransactionSpec
import io.peekandpoke.klang.codemirror.ext.codeFolding
import io.peekandpoke.klang.codemirror.ext.foldEffect
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.coroutines.delay
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * The minimap's highlights (KLANG-00410): the bands fade in, hold and fade out calmly, and they sit on the rows the
 * package draws its lines on, which [KlangMinimap.Rows] repeats from `@replit/codemirror-minimap` 0.5.2.
 */
class KlangMinimapSpec : StringSpec({

    /**
     * A minimap on a document of [lines] empty lines, but [line], which holds four spaces and then text; [folding] adds
     * CodeMirror's folding.
     */
    suspend fun withMinimap(
        lines: Int,
        line: Int,
        folding: Boolean = false,
        block: suspend (EditorView, KlangMinimap) -> Unit,
    ) {
        val parent = document.createElement("div") as HTMLElement
        parent.style.height = "200px"
        parent.style.width = "600px"
        document.body!!.appendChild(parent)

        val minimap = KlangMinimap()
        val view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = EditorState.create(
                    jsObject<EditorStateConfig> {
                        this.doc = (1..lines).joinToString("\n") { if (it == line) "    xxxxxxxxxxxx" else "" }
                        this.extensions = if (folding) {
                            arrayOf(codeFolding(), minimap.extension())
                        } else {
                            arrayOf(minimap.extension())
                        }
                    }
                )
                this.parent = parent
            }
        )

        try {
            block(view, minimap)
        } finally {
            view.destroy()
            parent.remove()
        }
    }

    /** The first row and the first column of [canvas] with ink in them from the column [fromX] on, null for none. */
    fun firstInk(canvas: HTMLCanvasElement, fromX: Int = 0): Pair<Int, Int>? {
        val context = canvas.getContext("2d").unsafeCast<CanvasRenderingContext2D>()
        val data = context.getImageData(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble()).data.asDynamic()

        for (y in 0 until canvas.height) {
            for (x in fromX until canvas.width) {
                if ((data[(y * canvas.width + x) * 4 + 3] as Int) > 0) {
                    return y to x
                }
            }
        }

        return null
    }

    fun EditorView.packageCanvas() = dom.querySelector(".cm-minimap-inner canvas") as HTMLCanvasElement

    fun EditorView.ourCanvas() = dom.querySelector(".klang-minimap-highlights") as HTMLCanvasElement

    /** Where the package drew [line] (its first ink), where [minimap] puts the line's top, and the character width. */
    suspend fun inkAndTop(view: EditorView, minimap: KlangMinimap, line: Int): Triple<Pair<Int, Int>, Double, Double> {
        val scroll = view.scrollDOM
        val canvas = view.packageCanvas()
        val context = canvas.getContext("2d").unsafeCast<CanvasRenderingContext2D>()
        val metrics = minimap.measure(view = view, context = context)

        // The package draws on a scroll, on the next frame. Only where the line's text is, after its four spaces: a
        // fold's placeholder sits at the start of the fold's row
        val ink = eventually {
            scroll.dispatchEvent(Event("scroll"))
            delay(20)

            firstInk(view.packageCanvas(), fromX = (3.5 * metrics.charWidth).toInt()).also { it shouldNotBe null }!!
        }

        val rows = minimap.rows(view = view, metrics = metrics, canvasHeight = canvas.height.toDouble())
        val top = rows.rowTop(
            row = minimap.foldedLines(view).rowOf(line)!!,
            scrollTop = scroll.scrollTop,
            scrollHeight = scroll.scrollHeight.toDouble(),
            clientHeight = scroll.clientHeight.toDouble(),
        )

        return Triple(ink, top, metrics.charWidth)
    }

    // The minimap scrolled to the top, to the middle, to the end; the line in sight each time
    listOf(
        Triple(300, 10, 0.0),
        Triple(300, 150, 0.5),
        Triple(300, 295, 1.0),
    ).forEach { (lines, line, scrolled) ->

        "the bands' rows are the package's: line $line of $lines, scrolled to ${(scrolled * 100).toInt()} percent" {
            withMinimap(lines = lines, line = line) { view, minimap ->
                val scroll = view.scrollDOM

                scroll.scrollTop = scrolled * (scroll.scrollHeight - scroll.clientHeight)

                val (ink, top, charWidth) = inkAndTop(view = view, minimap = minimap, line = line)

                withClue("the row the package drew the line on") { ink.first.toDouble() shouldBe (top plusOrMinus 1.0) }
                withClue("the column its text starts at, after four spaces") {
                    ink.second.toDouble() shouldBe (4 * charWidth plusOrMinus 1.0)
                }

                // Our band on the same line, the same stretch, on our own canvas
                minimap.light(line = line, fromColumn = 5, toColumn = 17, durationMs = 2000.0)

                val band = eventually {
                    delay(20)

                    firstInk(view.ourCanvas()).also { it shouldNotBe null }!!
                }

                withClue("our band's row") { band.first.toDouble() shouldBe (top plusOrMinus 1.0) }
                withClue("our band's column") { band.second.toDouble() shouldBe (4 * charWidth plusOrMinus 1.0) }

                minimap.clear()
                delay(30)

                withClue("cleared: nothing drawn") { firstInk(view.ourCanvas()) shouldBe null }
            }
        }
    }

    "folded code, scrolled to the end: the package's rows skip the lines in a fold, and so do the bands" {
        withMinimap(lines = 300, line = 290, folding = true) { view, minimap ->
            val doc = view.state.doc

            val range: dynamic = js("({})")
            range.from = doc.line(5).to
            range.to = doc.line(30).to

            view.dispatch(view.state.update(jsObject<TransactionSpec> { effects = foldEffect.of(range) }))

            withClue("lines 6 to 30 folded into line 5: line 290 is the 265th row of 275") {
                minimap.foldedLines(view).rowOf(290) shouldBe 264
                minimap.foldedLines(view).rowCount shouldBe 275
            }

            // Scrolled to the end, where the rows counted decide how far the minimap scrolls
            val scroll = view.scrollDOM
            scroll.scrollTop = (scroll.scrollHeight - scroll.clientHeight).toDouble()

            val (ink, top, _) = inkAndTop(view = view, minimap = minimap, line = 290)

            withClue("the row the package drew the line on") { ink.first.toDouble() shouldBe (top plusOrMinus 1.0) }
        }
    }

    "the rows of folded lines: a line starting in a fold joins the row above it" {
        // Five lines of four characters and a line break; a fold from inside line 2 to inside line 4
        val lines = KlangMinimap.FoldedLines.of(lineStarts = listOf(0, 5, 10, 15, 20), folds = listOf(7 to 17))

        (1..5).map { lines.rowOf(it) } shouldBe listOf(0, 1, null, null, 2)
        lines.rowCount shouldBe 3

        withClue("a fold from the first line: the first line keeps its row, there is none above it to join") {
            KlangMinimap.FoldedLines.of(lineStarts = listOf(0, 5, 10), folds = listOf(0 to 7))
                .let { folded -> (1..3).map { folded.rowOf(it) } } shouldBe listOf(0, null, 1)
        }

        withClue("two folds back to back") {
            KlangMinimap.FoldedLines.of(lineStarts = listOf(0, 5, 10, 15, 20, 25), folds = listOf(3 to 8, 8 to 13))
                .let { folded -> (1..6).map { folded.rowOf(it) } } shouldBe listOf(0, null, null, 1, 2, 3)
        }

        withClue("a fold in a fold: the outer one hides all") {
            KlangMinimap.FoldedLines.of(lineStarts = (0..35 step 5).toList(), folds = listOf(3 to 30, 8 to 13))
                .let { folded -> (1..8).map { folded.rowOf(it) } }
                .shouldBe(listOf(0, null, null, null, null, null, 1, 2))
        }

        withClue("folds given out of order, overlapping: taken by their start, as the package does") {
            KlangMinimap.FoldedLines
                .of(lineStarts = listOf(0, 120, 160), folds = listOf(200 to 300, 10 to 100, 50 to 150))
                .let { folded -> (1..3).map { folded.rowOf(it) } } shouldBe listOf(0, null, 1)
        }

        withClue("no folds: a line's row is its number less one") {
            KlangMinimap.FoldedLines.of(lineStarts = listOf(0, 5, 10), folds = emptyList()).rowOf(3) shouldBe 2
        }
    }

    "the strength is the canvas's opacity; the bands are drawn opaque on it" {
        withMinimap(lines = 20, line = 3) { view, minimap ->
            minimap.strength = 0.5

            view.ourCanvas().style.opacity.toDouble() shouldBe (0.5 * 0.85 plusOrMinus 1e-9)
        }
    }

    "a band fades in, holds while it sounds, fades out, and is gone" {
        val bands = MinimapBands()

        bands.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 400.0, now = 1000.0)
        val band = bands.all().single()

        withClue("lit: dark at first") { band.alphaAt(1000.0) shouldBe 0.0 }
        withClue("half way in") { band.alphaAt(1050.0) shouldBe (0.5 plusOrMinus 1e-9) }
        withClue("in, and held") { band.alphaAt(1100.0) shouldBe 1.0 }
        withClue("held to its end") { band.alphaAt(1400.0) shouldBe 1.0 }
        withClue("half way out") { band.alphaAt(1525.0) shouldBe (0.5 plusOrMinus 1e-9) }
        withClue("out") { band.alphaAt(1650.0) shouldBe 0.0 }

        bands.prune(1600.0)
        withClue("still fading, still there") { bands.all().size shouldBe 1 }

        bands.prune(1651.0)
        withClue("gone") { bands.isEmpty() shouldBe true }
    }

    "lit again while it holds, it stays as it is and holds to the later end; never from dark again" {
        val bands = MinimapBands()

        bands.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 400.0, now = 1000.0)
        bands.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 400.0, now = 1300.0)
        val band = bands.all().single()

        withClue("its fade in kept") { band.onAt shouldBe 1000.0 }
        withClue("held to the second end") { band.offAt shouldBe 1700.0 }
        withClue("still full where the first would have faded") { band.alphaAt(1500.0) shouldBe 1.0 }

        bands.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 100.0, now = 1050.0)
        withClue("a shorter note inside it does not cut it short") { bands.all().single().offAt shouldBe 1700.0 }
    }

    "lit again while it fades out, it fades back in from where it is" {
        val bands = MinimapBands()

        bands.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 400.0, now = 1000.0)
        // Half way out
        bands.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 400.0, now = 1525.0)
        val band = bands.all().single()

        withClue("where it was, not dark") { band.alphaAt(1525.0) shouldBe (0.5 plusOrMinus 1e-9) }
        withClue("back in") { band.alphaAt(1575.0) shouldBe 1.0 }
        withClue("held to its new end") { band.offAt shouldBe 1925.0 }
    }

    "two stretches of one line are two bands" {
        val bands = MinimapBands()

        bands.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 400.0, now = 1000.0)
        bands.light(line = 3, fromColumn = 8, toColumn = 12, durationMs = 400.0, now = 1000.0)

        bands.all().map { it.fromColumn } shouldBe listOf(1, 8)
    }
})
