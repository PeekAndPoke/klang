/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.peekandpoke.klang.common.math.PerlinNoise2D
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Brushed metal as a flat finish (KLANG-00366; the maintainer, 2026-10-27: "the brushed iron look on the backgrounds
 * similar to the start page. But not as a 3d thing", "maybe just select elements"): the start page's hand-brushed
 * strokes painted once into a tall tile that repeats without a seam (light strokes white, deep ones black, the rest
 * clear: on the looks' dark grounds `overlay` showed next to nothing), handed to the styles as `--kl-brushed-image`.
 * A part with the finish lays it over its ground at the look's `--kl-brushed` ([KlangRole.Brushed]).
 *
 * A trial: the strokes are a copy of `MotorBackground.brushed` (the app's start page); kept, the start page takes
 * them from here.
 */
object KlangBrushedMetal {

    /** The CSS variable that holds the tile, on the document's root. */
    const val IMAGE_VAR = "--kl-brushed-image"

    /** The tile's size: wide, as the strokes are long; tall, so a narrow part (the strip) shows no repeat. */
    const val WIDTH = 1024
    const val HEIGHT = 1024

    /** Rows painted per turn of the event loop, so the page stays live while the tile is made. */
    private const val ROWS_PER_TURN = 64

    /** How opaque a stroke is per unit of its level: a light stroke white, a deep one black, both mostly clear. */
    private const val CONTRAST = 700.0

    /**
     * The user's seed ([install]): the strokes are the user's own sheet, the same on every visit (the maintainer,
     * 2026-10-28: "Same for the metal brush here and on the ui-elements"; "every user should get a ui-seed").
     */
    private var seed = 0

    private var pressureNoise = PerlinNoise2D(random = Random(seed))

    /** Painted, or on its way: a second [install] does nothing. */
    private var installed = false

    /**
     * Paints the tile from the user's [seed] a few rows at a time, then hands it to the styles; nothing where a canvas
     * cannot be had.
     */
    fun install(seed: Int) {
        if (installed) {
            return
        }

        installed = true
        this.seed = seed
        pressureNoise = PerlinNoise2D(random = Random(seed))

        val canvas = document.createElement("canvas") as HTMLCanvasElement

        canvas.width = WIDTH
        canvas.height = HEIGHT

        val ctx = canvas.getContext("2d") as? CanvasRenderingContext2D ?: return
        val image = ctx.createImageData(WIDTH.toDouble(), HEIGHT.toDouble())

        fun turn(from: Int) {
            val to = (from + ROWS_PER_TURN).coerceAtMost(HEIGHT)

            paintRows(data = image.data.asDynamic(), from = from, to = to)

            if (to < HEIGHT) {
                window.setTimeout({ turn(from = to) }, 0)
            } else {
                ctx.putImageData(image, 0.0, 0.0)
                (document.documentElement as? HTMLElement)?.style?.setProperty(IMAGE_VAR, "url(${canvas.toDataURL("image/png")})")
            }
        }

        window.setTimeout({ turn(from = 0) }, 0)
    }

    /** Paints the rows [from] to [to] (exclusive) into [data], written as numbers: Kotlin's bytes would be signed. */
    private fun paintRows(data: dynamic, from: Int, to: Int) {
        for (py in from until to) {
            for (px in 0 until WIDTH) {
                val level = level(px = px, py = py)
                val ink = if (level > 0.0) 255 else 0
                val i = (py * WIDTH + px) * 4

                data[i] = ink
                data[i + 1] = ink
                data[i + 2] = ink
                data[i + 3] = (abs(level) * CONTRAST).roundToInt().coerceIn(0, 255)
            }
        }
    }

    /**
     * The tile's level at a pixel, about -0.16 to 0.16 (two layers of at most 0.055, the blend's lift up to 1.41): the strokes, blended with themselves a tile further left, so
     * the tile's right edge meets its left one, and lifted back where the blend flattened them (its middle); the rows
     * are strokes of their own, so top and bottom meet anyway.
     */
    internal fun level(px: Int, py: Int): Double {
        val t = px.toDouble() / WIDTH
        val blended = (1.0 - t) * strokes(px = px, py = py) + t * strokes(px = px - WIDTH, py = py)

        return blended / sqrt((1.0 - t) * (1.0 - t) + t * t)
    }

    /**
     * Strokes of a hand, not machine rows (the start page's, 2026-10-26: "make it look hand brushed"): each row cut
     * into strokes of their own length and depth, fading in and out at their ends; a second layer over the first,
     * its strokes cut elsewhere; some patches brushed harder than others.
     *
     * Level, no angle: a slant on rows a pixel high stepped every row down at the same x, so every stroke broke there,
     * a seam from top to bottom every 143 and 250 pixels (the maintainer, 2026-10-28: "The brush still creates
     * artifacts").
     */
    private fun strokes(px: Int, py: Int): Double {
        var sum = 0.0

        for (layer in 0..1) {
            val row = py
            val length = 140.0 + 260.0 * hashCell(col = row, row = 31 + layer)
            val along = (px + hashCell(col = row, row = 47 + layer) * length) / length
            val stroke = floor(along).toInt()
            val fade = sin(PI * (along - stroke))
            val depth = hashCell(col = row * 7 + stroke, row = 59 + layer) - 0.5

            sum += depth * 0.11 * fade
        }

        return sum * pressure(px = px, py = py)
    }

    /** How hard a patch is brushed, repeating with the tile's height so top and bottom meet. */
    private fun pressure(px: Int, py: Int): Double {
        val t = py.toDouble() / HEIGHT
        val here = pressureNoise.noise(x = px * 0.006 + 3.1, y = py * 0.02 + 8.7)
        val above = pressureNoise.noise(x = px * 0.006 + 3.1, y = (py - HEIGHT) * 0.02 + 8.7)

        return 0.55 + 0.45 * ((1.0 - t) * here + t * above)
    }

    private fun hashCell(col: Int, row: Int): Double {
        var h = (col * 73856093) xor (row * 19349663) xor seed
        h = ((h ushr 16) xor h) * 0x45d9f3b
        h = ((h ushr 16) xor h) * 0x45d9f3b
        h = (h ushr 16) xor h

        return (h and 0xffff).toDouble() / 0xffff.toDouble()
    }
}
