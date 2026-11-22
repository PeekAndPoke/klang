/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.ultra.html.css
import kotlinx.browser.document
import kotlinx.html.FlowContent
import kotlinx.html.div
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement

// The band's mark on the screens before the show: the name NOTSTROM as a banner in the upper part of the page, and
// `powered by klangmotor` close under it, right-aligned to the M, the two read as one mark. Where the letters stand
// depends on the font the machine has (the page names system fonts): what the placing needs of it is measured once in
// the page, so the credit's right edge meets the M's on every machine.

/** The page's fonts (as index_template.html sets them for the body). */
const val PAGE_FONTS = "system-ui, -apple-system, \"Segoe UI\", sans-serif"

/** The name's size: about three quarters of the window's width from its first letter to its last, within bounds. */
private const val NAME_SIZE = "clamp(40px, 10.6vw, 260px)"

/** The name's letter-spacing, of its size. */
private const val NAME_SPACING = 0.18

/** The credit's size, of the name's; never under [CREDIT_LEAST], so a small window can still read it. */
private const val CREDIT_SIZE = 0.085

/** The credit's smallest size. */
private const val CREDIT_LEAST = "13px"

/** The credit's letter-spacing, of its own size. */
private const val CREDIT_SPACING = 0.1

/** Where the name's capitals begin, from the top: a share of the window's height. */
private const val NAME_TOP_VH = 14.0

/** The gap from the name's baseline to the top of the credit's tall letters, of the name's cap height. */
private const val CREDIT_GAP = 0.14

/**
 * What the placing needs of the page's fonts, each of the font's size: the line's ascent and descent, the height of a
 * capital (and of the credit's tall letters), how far the M's ink stays short of its advance on the right, and how far
 * the r's ink runs past its own. Measured on a canvas, once; where the browser does not tell, values of a common sans.
 */
private class MarkFont(
    val ascent: Double,
    val descent: Double,
    val cap: Double,
    val mBearing: Double,
    val creditAscent: Double,
    val creditDescent: Double,
    val creditTall: Double,
    val rOverhang: Double,
) {
    /** Where the capitals begin in a line of height 1 (the font's line centred in it), of the size. */
    val capTop: Double get() = (1 - ascent - descent) / 2 + ascent - cap

    /** Where the baseline lies in a line of height 1, of the size. */
    val baseline: Double get() = (1 - ascent - descent) / 2 + ascent

    /** Where the credit's tall letters begin in its line of height 1, of its size. */
    val creditTallTop: Double get() = (1 - creditAscent - creditDescent) / 2 + creditAscent - creditTall

    companion object {
        val measured: MarkFont by lazy { measure() }

        private fun measure(): MarkFont {
            val ctx = (document.createElement("canvas") as HTMLCanvasElement).getContext("2d") as? CanvasRenderingContext2D
                ?: return fallback

            fun of(weight: Int, text: String, read: (dynamic) -> Double?): Double? {
                ctx.font = "$weight 1000px $PAGE_FONTS"

                // NaN-guard: what the browser does not tell is no number
                return read(ctx.measureText(text).asDynamic())?.takeIf { it == it }?.div(1000)
            }

            fun advance(m: dynamic): Double? = (m.width as? Number)?.toDouble()
            fun right(m: dynamic): Double? = (m.actualBoundingBoxRight as? Number)?.toDouble()

            return MarkFont(
                ascent = of(700, "N") { (it.fontBoundingBoxAscent as? Number)?.toDouble() } ?: fallback.ascent,
                descent = of(700, "N") { (it.fontBoundingBoxDescent as? Number)?.toDouble() } ?: fallback.descent,
                cap = of(700, "N") { (it.actualBoundingBoxAscent as? Number)?.toDouble() } ?: fallback.cap,
                mBearing = of(700, "M") { m -> advance(m)?.let { a -> right(m)?.let { a - it } } } ?: fallback.mBearing,
                creditAscent = of(400, "k") { (it.fontBoundingBoxAscent as? Number)?.toDouble() } ?: fallback.creditAscent,
                creditDescent = of(400, "k") { (it.fontBoundingBoxDescent as? Number)?.toDouble() } ?: fallback.creditDescent,
                creditTall = of(400, "k") { (it.actualBoundingBoxAscent as? Number)?.toDouble() } ?: fallback.creditTall,
                rOverhang = of(400, "r") { m -> advance(m)?.let { a -> right(m)?.let { it - a } } } ?: fallback.rOverhang,
            )
        }

        /** A common sans, where the browser tells nothing. */
        private val fallback = MarkFont(
            ascent = 0.93,
            descent = 0.24,
            cap = 0.73,
            mBearing = 0.08,
            creditAscent = 0.93,
            creditDescent = 0.24,
            creditTall = 0.76,
            rOverhang = 0.0,
        )
    }
}

/** Where the mark ends (the credit's lowest ink), from the page's top, as CSS. */
fun markBottom(): String {
    val font = MarkFont.measured
    val credit = "max($CREDIT_LEAST, $CREDIT_SIZE * $NAME_SIZE)"

    return "calc(${NAME_TOP_VH}vh + ${font.cap * (1 + CREDIT_GAP)} * $NAME_SIZE + " +
        "${font.creditTall + font.creditDescent * 0.8} * $credit)"
}

/**
 * The mark: the name with its capitals' top at [NAME_TOP_VH] of the height, centred; the credit under it, its tall
 * letters [CREDIT_GAP] of the cap height below the name's baseline, its right edge on the M's ink. Each line's last
 * letter-spacing is taken back by a negative margin, and the M's side bearing and the r's overhang (as the font has
 * them) are given to the credit, so the two right edges meet.
 */
fun FlowContent.mark() {
    val font = MarkFont.measured
    // the credit's margins, in the name's size and the credit's own (em): its top from the name's line end to where its
    // tall letters are to begin; its right with its letter-spacing taken back, the M's bearing and the r's overhang
    val creditTop = "calc(${font.baseline + CREDIT_GAP * font.cap - 1} * $NAME_SIZE - ${font.creditTallTop}em)"
    val creditRight = "calc(${font.mBearing} * $NAME_SIZE + ${font.rOverhang - CREDIT_SPACING}em)"

    div {
        css {
            put("position", "absolute")
            put("top", "calc(${NAME_TOP_VH}vh - ${font.capTop} * $NAME_SIZE)")
            put("left", "50%")
            put("transform", "translateX(-50%)")
            put("display", "flex")
            put("flex-direction", "column")
            put("align-items", "flex-end")
            put("font-family", PAGE_FONTS)
            put("font-size", NAME_SIZE)
            put("line-height", "1")
            put("white-space", "nowrap")
        }

        div {
            css {
                put("font-weight", "700")
                put("letter-spacing", "${NAME_SPACING}em")
                put("margin-right", "-${NAME_SPACING}em")
                put("color", "#e8ecf4")
            }
            +"MØRTSTØN"
        }

        div {
            css {
                put("font-size", "max($CREDIT_LEAST, ${CREDIT_SIZE}em)")
                put("font-weight", "400")
                put("letter-spacing", "${CREDIT_SPACING}em")
                put("margin-top", creditTop)
                put("margin-right", creditRight)
                put("color", "#8a93a8")
            }
            +"powered by klangmotor"
        }
    }
}
