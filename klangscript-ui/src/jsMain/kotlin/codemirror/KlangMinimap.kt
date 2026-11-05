/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.MinimapConfig
import io.peekandpoke.klang.codemirror.ext.foldedRanges
import io.peekandpoke.klang.codemirror.ext.showMinimap
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent
import kotlin.js.Date
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The editor's minimap (`@replit/codemirror-minimap`) with the code highlights on it (KLANG-00410), self-contained: the
 * minimap, its blocks, its overlay always shown, and a canvas of our own over it for the highlights. It knows nothing
 * of playback: [light] says a stretch of a line sounds now, for so long, [clear] puts every band out. The editor's
 * highlight buffer lights it with each highlight it shows (the maintainer, 2026-11-05: "Maybe same principle, our own
 * overlay?").
 *
 * The bands are calm (the maintainer: "I would not show them as 'hectic'"): no pulse, a short fade in, steady while the
 * note sounds, a short fade out; a stretch lit again before it went out stays lit ([MinimapBands]). The draw loop runs
 * only while a band shows.
 *
 * A press on the minimap starts its drag, never a text selection across the page (the maintainer, 2026-11-02: "when
 * the code is scrolled by dragging the minimap preview, then text selection on the page must be disabled"): the
 * package's own listeners, on its overlay inside the element, still see the press and drive the scroll.
 */
class KlangMinimap {

    companion object {
        /** The package's canvas pixels per CSS pixel (its `Scale.PixelMultiplier`, 0.5.2). */
        const val PIXEL_MULTIPLIER = 2.0

        /** How much smaller than the editor the package draws (its `Scale.SizeRatio`, 0.5.2). */
        const val SIZE_RATIO = 4.0

        /** The highlight's gold, as the editor's highlight border. */
        private const val BAND_COLOR = "#FFDC64"

        /** A band's alpha at full strength: over the blocks, not hiding them. */
        private const val BAND_ALPHA = 0.85
    }

    /** The line height and the character width the package draws with, in its canvas pixels. */
    data class Metrics(val lineHeight: Double, val charWidth: Double)

    /**
     * The package's rows, in its canvas pixels: the line height it draws with, the editor's padding scaled down, the
     * canvas height and the rows there are ([FoldedLines.rowCount]). Its `canvasStartAndEndIndex` (0.5.2) repeated in
     * [rowTop]; `KlangMinimapSpec` checks it against the package's own drawing, so an update that moves the lines turns
     * red.
     */
    data class Rows(
        val lineHeight: Double,
        val paddingTop: Double,
        val paddingBottom: Double,
        val canvasHeight: Double,
        val rowCount: Int,
    ) {
        /**
         * The top of [row] (0-based, [FoldedLines.rowOf]) on the canvas, with the editor scrolled to [scrollTop] of
         * [scrollHeight] in a box [clientHeight] tall: the package scrolls the minimap by the same share of what does
         * not fit.
         */
        fun rowTop(row: Int, scrollTop: Double, scrollHeight: Double, clientHeight: Double): Double {
            val scrollPercent = (scrollTop / (scrollHeight - clientHeight)).let {
                // NaN-guard: a document that fits has nothing to scroll (0 / 0)
                if (it.isNaN()) 0.0 else it
            }
            val totalHeight = paddingTop + paddingBottom + rowCount * lineHeight
            val canvasTop = max(0.0, scrollPercent * (totalHeight - canvasHeight))
            val offsetY = max(0.0, paddingTop - canvasTop)
            val startIndex = (max(0.0, canvasTop - paddingTop) / lineHeight).roundToInt()

            return offsetY + (row - startIndex) * lineHeight
        }
    }

    /**
     * The package's rows over the document's lines (its `computeLinesState`, 0.5.2): a line that starts inside a folded
     * range joins the row above it, so it has no row of its own, and the rows are counted without it. Without folds a
     * line's row is its number less one.
     */
    class FoldedLines(private val rowOfLine: IntArray?, val rowCount: Int) {

        companion object {
            /**
             * The rows of the lines starting at [lineStarts] (offsets, in order) with [folds] (from, to) in any order:
             * taken by their start, as the package's cursor takes them (CodeMirror keeps overlapping folds in layers).
             */
            fun of(lineStarts: List<Int>, folds: List<Pair<Int, Int>>): FoldedLines {
                if (folds.isEmpty()) {
                    return FoldedLines(rowOfLine = null, rowCount = lineStarts.size)
                }

                val sorted = folds.sortedBy { it.first }

                val rows = IntArray(lineStarts.size)
                var row = -1
                var fold = 0

                lineStarts.forEachIndexed { index, start ->
                    // As the package's cursor: past the folds that end before this line
                    while (fold < sorted.size && sorted[fold].second < start) {
                        fold++
                    }

                    val hidden = fold < sorted.size && start >= sorted[fold].first && start < sorted[fold].second

                    // A first line in a fold still makes a row: there is none above it to join
                    if (hidden && row >= 0) {
                        rows[index] = -1
                    } else {
                        row++
                        rows[index] = row
                    }
                }

                return FoldedLines(rowOfLine = rows, rowCount = row + 1)
            }
        }

        /** The row (0-based) of [line] (1-based); null for a line hidden in a fold, or one that is not there. */
        fun rowOf(line: Int): Int? {
            if (line < 1) {
                return null
            }

            if (rowOfLine == null) {
                return if (line <= rowCount) line - 1 else null
            }

            return rowOfLine.getOrNull(line - 1)?.takeIf { it >= 0 }
        }
    }

    /** The bands lit now. */
    val bands = MinimapBands()

    /**
     * How strong the bands show, 0 to 1: the editor's highlight strength (KLANG-00411). It is the canvas's opacity, the
     * bands drawn opaque on it, so two stretches overlapping on a line show as one, no brighter (the calm look).
     */
    var strength: Double = 1.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            canvas?.style?.opacity = opacity()
        }

    private var view: EditorView? = null
    private var holder: HTMLElement? = null
    private var canvas: HTMLCanvasElement? = null
    private var frame: Int? = null

    /** The metrics measured when the bands came on, kept while they show: a measure reads the editor's style. */
    private var measured: Metrics? = null

    /** The rows of the lines for the state [linesOf], kept until the state changes. */
    private var lines: FoldedLines? = null
    private var linesOf: EditorState? = null

    /** The minimap, for the editor's extensions. */
    fun extension(): Extension {
        val config = jsObject<MinimapConfig> {
            create = { editorView -> mount(editorView) }
            displayText = "blocks"
            showOverlay = "always"
        }

        return showMinimap.of(config)
    }

    /**
     * Lights the stretch of [line] (1-based) from [fromColumn] to [toColumn] (1-based, exclusive; null for the rest of
     * the line) for [durationMs]. Nothing without a minimap on screen.
     */
    fun light(line: Int, fromColumn: Int, toColumn: Int?, durationMs: Double) {
        if (canvas == null) {
            return
        }

        bands.light(
            line = line,
            fromColumn = fromColumn,
            toColumn = toColumn,
            durationMs = durationMs,
            now = Date.now(),
        )
        ensureDrawing()
    }

    /** Puts every band out at once: the song stopped, or a visual covers the window. */
    fun clear() {
        bands.clear()
        frame?.let { window.cancelAnimationFrame(it) }
        frame = null
        measured = null
        lines = null
        linesOf = null
        canvas?.let { c ->
            c.getContext("2d").unsafeCast<CanvasRenderingContext2D?>()
                ?.clearRect(0.0, 0.0, c.width.toDouble(), c.height.toDouble())
        }
    }

    /** The minimap's element, our canvas in it; the package adds its own inner part with its canvas after it. */
    private fun mount(editorView: EditorView): dynamic {
        // A plain object: inside a jsObject lambda `dom` would bind to an outer `dom`
        val result: dynamic = js("{}")
        val element = document.createElement("div") as HTMLElement

        element.addEventListener("mousedown", { event ->
            if ((event as MouseEvent).button.toInt() == 0) {
                event.preventDefault()
            }
        })

        val overlay = document.createElement("canvas") as HTMLCanvasElement

        overlay.className = "klang-minimap-highlights"
        overlay.style.apply {
            position = "absolute"
            top = "0"
            right = "0"
            zIndex = "1"
            opacity = opacity()
            setProperty("pointer-events", "none")
        }
        element.appendChild(overlay)

        clear()
        view = editorView
        holder = element
        canvas = overlay

        result.dom = element

        return result
    }

    private fun opacity(): String = (strength * BAND_ALPHA).toString()

    private fun ensureDrawing() {
        if (frame == null) {
            frame = window.requestAnimationFrame { draw() }
        }
    }

    private fun draw() {
        frame = null

        val view = this.view ?: return
        val holder = this.holder ?: return
        val canvas = this.canvas ?: return

        // The editor went: stop, and forget the bands
        if (!canvas.isConnected) {
            bands.clear()
            return
        }

        val now = Date.now()

        bands.prune(now)

        // Sized as the package sizes its canvas: the minimap's width, the editor's height, at its pixel multiplier
        val widthPx = holder.clientWidth.toDouble()
        val heightPx = view.dom.getBoundingClientRect().height
        val width = (widthPx * PIXEL_MULTIPLIER).toInt()
        val height = (heightPx * PIXEL_MULTIPLIER).toInt()

        if (canvas.width != width || canvas.height != height) {
            canvas.width = width
            canvas.height = height
            canvas.style.width = "${widthPx}px"
            canvas.style.height = "${heightPx}px"
        }

        val context = canvas.getContext("2d").unsafeCast<CanvasRenderingContext2D?>() ?: return

        context.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())

        if (bands.isEmpty()) {
            measured = null
            return
        }

        val metrics = measured ?: measure(view = view, context = context).also { measured = it }
        val lineHeight = metrics.lineHeight
        val charWidth = metrics.charWidth
        val folded = foldedLines(view)
        val rows = rows(view = view, metrics = metrics, canvasHeight = height.toDouble())
        val scroll = view.scrollDOM

        context.fillStyle = BAND_COLOR

        bands.forEachShown(now) { band, alpha ->
            // A line that starts in a fold has no row of its own. The line a fold ends on shows its rest on the fold's
            // row, but at other columns than its own: it gets no band, a small gap accepted
            val row = folded.rowOf(band.line) ?: return@forEachShown
            val y = rows.rowTop(
                row = row,
                scrollTop = scroll.scrollTop,
                scrollHeight = scroll.scrollHeight.toDouble(),
                clientHeight = scroll.clientHeight.toDouble(),
            )

            if (y + lineHeight > 0 && y < height) {
                val x = (band.fromColumn - 1) * charWidth
                val w = band.toColumn?.let { (it - band.fromColumn) * charWidth } ?: (width - x)

                // Opaque but for the fade: the canvas's opacity is the strength
                context.globalAlpha = alpha
                context.fillRect(x, y, max(charWidth, min(w, width - x)), lineHeight - 2 / SIZE_RATIO)
            }
        }

        context.globalAlpha = 1.0

        ensureDrawing()
    }

    /** The package's rows for [view] now, with the [metrics] measured, on a canvas [canvasHeight] tall. */
    internal fun rows(view: EditorView, metrics: Metrics, canvasHeight: Double): Rows {
        val padding = view.asDynamic().documentPadding

        return Rows(
            lineHeight = metrics.lineHeight,
            paddingTop = (padding?.top as? Double ?: 0.0) / SIZE_RATIO,
            paddingBottom = (padding?.bottom as? Double ?: 0.0) / SIZE_RATIO,
            canvasHeight = canvasHeight,
            rowCount = foldedLines(view).rowCount,
        )
    }

    /** The rows of [view]'s lines with its folds, worked out again only when its state changed. */
    internal fun foldedLines(view: EditorView): FoldedLines {
        val state = view.state
        val kept = lines

        if (kept != null && linesOf === state) {
            return kept
        }

        val folds = mutableListOf<Pair<Int, Int>>()

        foldedRanges(state).between(0, state.doc.length) { from, to, _ -> folds += from to to }

        val starts = if (folds.isEmpty()) {
            List(state.doc.lines) { 0 }
        } else {
            (1..state.doc.lines).map { state.doc.line(it).from }
        }

        return FoldedLines.of(lineStarts = starts, folds = folds).also {
            lines = it
            linesOf = state
        }
    }

    /**
     * The line height and the character width the package draws with, measured as it measures them: a line's style
     * scaled down, and the width of "_" in that font at the line's height.
     */
    internal fun measure(view: EditorView, context: CanvasRenderingContext2D): Metrics {
        val token = document.createElement("span")
        val line = document.createElement("div") as HTMLElement

        line.className = "cm-line"
        line.style.display = "none"
        line.appendChild(token)
        view.contentDOM.appendChild(line)

        val style = window.getComputedStyle(token)
        // As the package's parseFloat: "20.8px" is 20.8
        val lineHeight = (style.lineHeight.removeSuffix("px").toDoubleOrNull() ?: 16.0) / SIZE_RATIO

        context.font = "${style.fontStyle} ${style.fontWeight} ${lineHeight}px ${style.fontFamily}"
        val charWidth = context.measureText("_").width

        view.contentDOM.removeChild(line)

        return Metrics(lineHeight = lineHeight, charWidth = charWidth)
    }
}

/**
 * The minimap's bands (KLANG-00410), apart from the drawing: each stretch fades in over [FADE_IN_MS], stays while it
 * sounds, fades out over [FADE_OUT_MS]. A stretch lit again before it went out stays lit and is held longer; one
 * fading out fades back in from where it is, never from dark: no flicker on a busy line.
 */
class MinimapBands {

    companion object {
        const val FADE_IN_MS = 100.0
        const val FADE_OUT_MS = 250.0
    }

    /** A lit stretch: on at [onAt], held until [offAt], then fading out. */
    class Band(val line: Int, val fromColumn: Int, val toColumn: Int?, val onAt: Double, val offAt: Double) {

        /** The band's alpha at [now], 0 to 1. */
        fun alphaAt(now: Double): Double {
            if (now < onAt) {
                return 0.0
            }

            val fadeIn = min(1.0, (now - onAt) / FADE_IN_MS)

            if (now <= offAt) {
                return fadeIn
            }

            return max(0.0, min(fadeIn, 1.0 - (now - offAt) / FADE_OUT_MS))
        }

        /** Gone for good at [now]: faded out. */
        fun outAt(now: Double): Boolean = now > offAt && alphaAt(now) <= 0.0
    }

    private val lit = mutableMapOf<String, Band>()

    fun light(line: Int, fromColumn: Int, toColumn: Int?, durationMs: Double, now: Double) {
        val key = "$line:$fromColumn:$toColumn"
        val before = lit[key]
        val offAt = now + durationMs

        lit[key] = when {
            before == null || before.outAt(now) -> Band(
                line = line,
                fromColumn = fromColumn,
                toColumn = toColumn,
                onAt = now,
                offAt = offAt,
            )

            // Still held: it stays as it is, held until the later end
            now <= before.offAt -> Band(
                line = line,
                fromColumn = fromColumn,
                toColumn = toColumn,
                onAt = before.onAt,
                offAt = max(before.offAt, offAt),
            )

            // Fading out: back in from where it is
            else -> Band(
                line = line,
                fromColumn = fromColumn,
                toColumn = toColumn,
                onAt = now - before.alphaAt(now) * FADE_IN_MS,
                offAt = offAt,
            )
        }
    }

    /** Forgets the bands that went out by [now]. */
    fun prune(now: Double) {
        lit.entries.removeAll { it.value.outAt(now) }
    }

    fun forEachShown(now: Double, block: (Band, Double) -> Unit) {
        lit.values.forEach { band ->
            val alpha = band.alphaAt(now)

            if (alpha > 0.0) {
                block(band, alpha)
            }
        }
    }

    fun isEmpty(): Boolean = lit.isEmpty()

    fun clear() {
        lit.clear()
    }

    /** The bands lit, for the specs. */
    fun all(): List<Band> = lit.values.toList()
}
