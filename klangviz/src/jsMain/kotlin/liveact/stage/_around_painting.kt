/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.camera.REVERSE
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.theatre.Sky
import kotlinx.browser.document
import org.w3c.dom.CanvasFillRule
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.EVENODD
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// How the surroundings' shapes are drawn (Around): once, as the stage is built, onto two canvases, by code and the
// same every time (each shape's numbers come from a seeded stream, Dice). A caricature: bold dark shapes, no detail.
//
// Each surrounding goes into its own colour channel of both pictures (Surrounding.ordinal: red, green, blue), so one
// texture holds all three, and a turn from one to the next is a uniform. In a channel, 1 is a near shape, a little
// under a half one further back (darker, and a little see-through), 0 nothing. What is drawn in metres: the canvas's
// transform turns them into its pixels. The finest limbs are kept some pixels wide and fade as they get thinner, so
// they do not shimmer as the camera moves.

/** A stream of numbers from 0 to 1, the same every time for the same [salt]: what makes a shape the same every time. */
internal class Dice(private val salt: Double) {
    private var n = 0

    /** The next number, 0 to 1. */
    fun roll(): Double {
        n++

        return hash01(salt + n * 1.618034)
    }

    /** The next number, between [from] and [to]. */
    fun between(from: Double, to: Double): Double = from + (to - from) * roll()
}

/** A row of the shapes of a picture: the near ones, or the ones further back. Each has a picture of its own. */
internal enum class Row { Near, Deep }

/** Where the shapes beside the stage are drawn from and to, in the painter's metres: its picture's inner and outer edge. */
private const val DRAWN_FROM = 4.0
private const val DRAWN_TO = DRAWN_FROM + Around.SIDE_TO - Around.SIDE_FROM

/**
 * A picture of the shapes left and right of the stage (see [Around]), [width] by [height] pixels: the audience's left
 * in its left half; of the shapes only those of [row], drawn whole and at full strength (the other row's are rolled
 * as well, so both pictures are of the same place).
 */
internal fun paintSides(row: Row, width: Int, height: Int): HTMLCanvasElement = packed(width = width, height = height) { g, surrounding ->
    val wide = DRAWN_TO - DRAWN_FROM
    val perX = width / 2 / wide
    val perY = height / Around.SIDE_HIGH
    val half = width / 2.0

    for (way in listOf(-1, 1)) {
        g.save()
        g.setTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        g.beginPath()
        g.rect(if (way < 0) 0.0 else half, 0.0, half, height.toDouble())
        g.clip()
        // drawn in metres outward (d, from DRAWN_FROM at the picture's inner edge) and up from the floor (y): on the audience's right as it
        // is, on its left mirrored, both toward the stage at the picture's middle
        if (way < 0) {
            g.setTransform(-perX, 0.0, 0.0, -perY, DRAWN_TO * perX, height.toDouble())
        } else {
            g.setTransform(perX, 0.0, 0.0, -perY, half - DRAWN_FROM * perX, height.toDouble())
        }

        val painter = Painter(g = g, dice = Dice(salt = 11.0 * (surrounding.ordinal + 1) + 3.0 * way), pixels = min(perX, perY), only = row)

        when (surrounding) {
            Surrounding.Forest -> painter.forestBeside()
            Surrounding.Ruins -> painter.ruinsBeside()
            Surrounding.Stones -> painter.stonesBeside()
        }

        g.restore()
    }
}

/**
 * A picture of the horizon round the audience (see [Around]), [width] by [height] pixels: along the ring from one end
 * to the other; of the shapes only those of [row], as [paintSides] draws them.
 */
internal fun paintFar(row: Row, width: Int, height: Int): HTMLCanvasElement =
    far(row = row, relief = false, width = width, height = height)

/**
 * The relief of the horizon's near shapes, a picture laid out as [paintFar]'s: how far a shape's surface stands out,
 * place by place, from 0 (deep in a joint) to 1, [LEVEL] where it is plain and where nothing is. Painted, not worked
 * out (the maintainer, 2026-11-22: "painted maps would be better"): a wall's courses of blocks and the stones round
 * its arches, a column's flutes, a pine's tiers each over the next, a limb round, a standing stone in facets with
 * cracks.
 */
internal fun paintFarRelief(width: Int, height: Int): HTMLCanvasElement =
    far(row = Row.Near, relief = true, width = width, height = height)

private fun far(row: Row, relief: Boolean, width: Int, height: Int): HTMLCanvasElement = packed(
    width = width,
    height = height,
    ground = if (relief) grey(LEVEL) else "#000000",
) { g, surrounding ->
    val reach = Around.FAR_WRAP * Around.FAR_RADIUS
    val perX = width / (2 * reach)
    val perY = height / Around.FAR_HIGH

    // drawn in metres along the ring (s, 0 straight ahead of the band, to the audience's right for more) and up (y)
    g.setTransform(perX, 0.0, 0.0, -perY, reach * perX, height + Around.FAR_BASE * perY)

    val painter = Painter(
        g = g,
        dice = Dice(salt = 101.0 * (surrounding.ordinal + 1)),
        pixels = min(perX, perY),
        only = row,
        relief = relief,
    )
    val horizon = Horizon(reach = reach)

    when (surrounding) {
        Surrounding.Forest -> painter.forestFar(horizon)
        Surrounding.Ruins -> painter.ruinsFar(horizon)
        Surrounding.Stones -> painter.stonesFar(horizon)
    }
}

/**
 * A picture of [width] by [height] pixels with each surrounding drawn by [paint] in its own channel: each is drawn
 * white (near) and grey (far) on a clear canvas, put on [ground], turned to its channel's colour and added.
 */
private fun packed(
    width: Int,
    height: Int,
    ground: String = "#000000",
    paint: (CanvasRenderingContext2D, Surrounding) -> Unit,
): HTMLCanvasElement {
    val w = width.toDouble()
    val h = height.toDouble()
    val out = canvas(width = width, height = height)
    val o = out.getContext("2d") as CanvasRenderingContext2D
    val each = canvas(width = width, height = height)
    val g = each.getContext("2d") as CanvasRenderingContext2D

    o.fillStyle = "#000000"
    o.fillRect(0.0, 0.0, w, h)

    for (surrounding in Surrounding.entries) {
        g.setTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        g.globalCompositeOperation = "source-over"
        g.globalAlpha = 1.0
        g.clearRect(0.0, 0.0, w, h)
        paint(g, surrounding)

        g.setTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        g.globalAlpha = 1.0
        g.globalCompositeOperation = "destination-over"
        g.fillStyle = ground
        g.fillRect(0.0, 0.0, w, h)
        g.globalCompositeOperation = "multiply"
        g.fillStyle = CHANNELS[surrounding.ordinal]
        g.fillRect(0.0, 0.0, w, h)

        o.globalCompositeOperation = "lighter"
        o.drawImage(each, 0.0, 0.0)
    }

    return out
}

/** A canvas of its own, never put into the page. */
private fun canvas(width: Int, height: Int): HTMLCanvasElement {
    val canvas = document.createElement("canvas") as HTMLCanvasElement

    canvas.width = width
    canvas.height = height

    return canvas
}

/** A relief's level where a surface is plain, 0 to 1: room to stand out from it and to sink into it. */
private const val LEVEL = 0.5

/** The grey of [level] (0 to 1), as a canvas's colour. */
private fun grey(level: Double): String {
    val n = (level.coerceIn(0.0, 1.0) * 255).toInt()

    return "rgb($n,$n,$n)"
}

/** The channel of each surrounding, by its place. */
private val CHANNELS = listOf("#ff0000", "#00ff00", "#0000ff")

/**
 * How high the horizon's shapes reach at a place along the ring ([top], in metres up), as the reverse total sees them
 * (the look back from behind the drummer over the audience, [REVERSE]): low in its middle, just over the audience's last
 * lights (they end some 12.6 m up there, as seen from that camera), and rising toward its left and right edges so they
 * frame it; and below the moon ([moonAt]), which they never cover.
 */
private class Horizon(val reach: Double) {
    /** Where the reverse total looks, as a round of the ring (radians, 0 straight ahead of the band). */
    val middle: Double = roundSeen(dx = REVERSE.at.first - REVERSE.pos.first, dz = REVERSE.at.third - REVERSE.pos.third)

    /** Where the moon stands over the ring, seen from the reverse total, in metres along it. */
    val moonAt: Double = roundSeen(dx = Sky.MOON.x, dz = Sky.MOON.z) * Around.FAR_RADIUS

    /** How high the shapes reach at [s] metres along the ring. */
    fun top(s: Double): Double {
        val x = ((abs(s / Around.FAR_RADIUS - middle) - 0.2) / 0.36).coerceIn(0.0, 1.0)
        val high = LOW + (HIGH - LOW) * x * x * (3 - 2 * x)
        val m = abs(s - moonAt) / MOON_GAP

        return if (m < 1.0) min(high, UNDER_MOON + 1.4 * m * m) else high
    }

    /** Where a level look from the reverse total's camera along [dx], [dz] meets the ring: its round. */
    private fun roundSeen(dx: Double, dz: Double): Double {
        val px = REVERSE.pos.first
        val pz = REVERSE.pos.third
        val length = sqrt(dx * dx + dz * dz)
        val ux = dx / length
        val uz = dz / length
        val b = px * ux + pz * uz
        val c = px * px + pz * pz - Around.FAR_RADIUS * Around.FAR_RADIUS
        val t = -b + sqrt(b * b - c)

        return atan2(px + ux * t, pz + uz * t)
    }

    companion object {
        /** How high the shapes reach in the middle and at the sides, in metres up. */
        const val LOW = 15.2
        const val HIGH = 31.0

        /** Below the moon (its disc comes down to some 15.3 m), and how far to its sides, in metres. */
        const val UNDER_MOON = 14.0
        const val MOON_GAP = 6.0
    }
}

/**
 * Draws shapes onto [g] in metres, by the numbers of [dice]; [pixels] is how many of the picture's pixels a metre is at
 * least, so the finest limbs stay some pixels wide. With [only], just that row's shapes are drawn, the far ones then
 * whole and at full strength; the other row's leave no mark. With [relief], not the shapes are drawn but how far their
 * surfaces stand out (see [paintFarRelief]): the same shapes at the same places, plain at [LEVEL], their detail on them.
 */
private class Painter(
    val g: CanvasRenderingContext2D,
    val dice: Dice,
    val pixels: Double,
    val only: Row? = null,
    val relief: Boolean = false,
) {
    /** Whether the shapes drawn just now leave no mark. */
    private var unseen = false

    /** The numbers of a relief's detail: its own, so the shapes are the same with and without it. */
    private val detail = Dice(salt = 977.0)

    /** Whether a relief's detail is drawn now. */
    private val carving: Boolean get() = relief && !unseen

    /** Draws [draw] within the shape through [xy] alone, and only onto what is there: a relief's detail on a shape. */
    private fun within(xy: List<Double>, draw: () -> Unit) {
        if (!carving) {
            return
        }

        g.save()
        g.beginPath()
        g.moveTo(xy[0], xy[1])

        for (i in 2 until xy.size step 2) {
            g.lineTo(xy[i], xy[i + 1])
        }

        g.closePath()
        g.clip()
        g.globalCompositeOperation = "source-atop"
        g.globalAlpha = 1.0
        draw()
        g.restore()
    }

    /** A fill that runs from the grey [from] at ([x0], [y0]) to the grey [to] at ([x1], [y1]): a surface that leans. */
    private fun lean(x0: Double, y0: Double, from: Double, x1: Double, y1: Double, to: Double) {
        val fill = g.createLinearGradient(x0, y0, x1, y1)

        fill.addColorStop(0.0, grey(from))
        fill.addColorStop(1.0, grey(to))
        g.fillStyle = fill
    }

    /** Shapes from now on leave no mark: nothing drawn behind what is there, whatever strength a shape sets. */
    private fun none() {
        unseen = true
        g.globalCompositeOperation = "destination-over"
        g.globalAlpha = 1.0
        g.fillStyle = "rgba(0,0,0,0)"
    }

    /** Shapes from now on near: white, over what is there. */
    fun near() {
        if (only == Row.Deep) {
            return none()
        }

        unseen = false
        g.globalCompositeOperation = "source-over"
        g.globalAlpha = 1.0
        g.fillStyle = if (relief) grey(LEVEL) else "#ffffff"
    }

    /** Shapes from now on further back: grey, and only behind what is there already. */
    fun far() {
        if (only == Row.Near) {
            return none()
        }

        unseen = false
        g.globalAlpha = 1.0

        if (only == Row.Deep) {
            // a picture of its own: whole, at full strength
            g.globalCompositeOperation = "source-over"
            g.fillStyle = "#ffffff"
        } else {
            g.globalCompositeOperation = "destination-over"
            g.fillStyle = FAR
        }
    }

    /** Shapes from now on cut out of what is there (an arch's opening). */
    fun cut() {
        if (unseen) {
            return
        }

        g.globalCompositeOperation = "destination-out"
        g.globalAlpha = 1.0
        g.fillStyle = "#ffffff"
    }

    /** A filled shape through the points [xy] (x, y, x, y, ...). */
    fun polygon(xy: List<Double>) {
        g.beginPath()
        g.moveTo(xy[0], xy[1])

        for (i in 2 until xy.size step 2) {
            g.lineTo(xy[i], xy[i + 1])
        }

        g.closePath()
        g.fill()
    }

    /** A filled disc. */
    fun disc(x: Double, y: Double, radius: Double) {
        g.beginPath()
        g.arc(x, y, radius, 0.0, 2 * PI)
        g.fill()
    }

    /** An arched opening cut from [left] to [right], from [bottom] up to where its arch springs at [spring]. */
    fun opening(left: Double, right: Double, bottom: Double, spring: Double) {
        cut()
        g.fillRect(left, bottom, right - left, spring - bottom)
        disc(x = (left + right) / 2, y = spring, radius = (right - left) / 2)
        near()

        if (!carving) {
            return
        }

        // the relief: the arch's stones stand out round it, a joint between each two
        val x = (left + right) / 2
        val radius = (right - left) / 2
        val deep = min(0.8, radius * 0.4)

        g.save()
        g.globalCompositeOperation = "source-atop"
        g.lineWidth = deep
        g.strokeStyle = grey(0.72)
        g.beginPath()
        g.arc(x, spring, radius + deep / 2, 0.0, PI)
        g.stroke()
        g.fillStyle = grey(0.72)
        g.fillRect(left - deep, bottom, deep, spring - bottom)
        g.fillRect(right, bottom, deep, spring - bottom)
        g.lineWidth = JOINT
        g.strokeStyle = grey(0.18)

        val stones = max(5, (PI * radius / 0.8).toInt())

        for (i in 0..stones) {
            val a = PI * i / stones

            g.beginPath()
            g.moveTo(x + cos(a) * radius, spring + sin(a) * radius)
            g.lineTo(x + cos(a) * (radius + deep), spring + sin(a) * (radius + deep))
            g.stroke()
        }

        g.restore()
    }

    /**
     * A piece of wall from [left] to [right], from [bottom] up to about [top], broken off: its top in steps of a few
     * blocks, each [rough] metres higher or lower.
     */
    fun broken(left: Double, right: Double, bottom: Double, top: Double, rough: Double) {
        val xy = mutableListOf(left, bottom)
        val steps = 3 + (dice.roll() * 3).toInt()

        for (i in 0 until steps) {
            val from = left + (right - left) * i / steps
            val to = left + (right - left) * (i + 1) / steps
            val y = top + dice.between(-rough, rough)

            xy += listOf(from, y, to - (to - from) * dice.between(0.0, 0.3), y)
        }

        xy += listOf(right, bottom)
        polygon(xy)

        // the relief: courses of blocks, each a little further out or in than the next, a joint between them; the
        // same courses along the whole ring, so one piece of wall goes on in the next
        within(xy) {
            var course = floor((bottom - Around.FAR_BASE) / COURSE).toInt()

            while (Around.FAR_BASE + course * COURSE < top + rough) {
                val y = Around.FAR_BASE + course * COURSE
                val shift = if (course % 2 == 0) 0.0 else BLOCK / 2
                var block = floor((left - shift) / BLOCK).toInt()

                while (shift + block * BLOCK < right) {
                    val x = shift + block * BLOCK

                    g.fillStyle = grey(0.4 + 0.24 * hash01(course * 17.3 + block * 3.1))
                    g.fillRect(x, y, BLOCK, COURSE)
                    g.fillStyle = grey(0.16)
                    g.fillRect(x, y, BLOCK, JOINT)
                    g.fillRect(x, y, JOINT * 1.3, COURSE)
                    block++
                }

                course++
            }
        }
    }

    /**
     * A limb from ([x], [y]) at [angle] from straight up, [length] long, [width] thick at its foot: it bends once on
     * its way, thins, and forks into two or three, which fork again, until they are as thin as the picture lets them
     * be. The thinner a limb, the fainter it is drawn.
     */
    fun limb(x: Double, y: Double, angle: Double, length: Double, width: Double, depth: Int) {
        val bent = angle + dice.between(-0.2, 0.2)
        val mx = x + sin(angle) * length * 0.5
        val my = y + cos(angle) * length * 0.5
        val ex = mx + sin(bent) * length * 0.5
        val ey = my + cos(bent) * length * 0.5
        val end = width * 0.72
        val mid = (width + end) / 2
        val shown = g.globalAlpha

        g.globalAlpha = shown * faint(mid)
        taper(x0 = x, y0 = y, w0 = width, x1 = mx, y1 = my, w1 = mid)
        taper(x0 = mx, y0 = my, w0 = mid, x1 = ex, y1 = ey, w1 = end)
        g.globalAlpha = shown

        if (end * pixels < FINEST || depth >= 8) {
            return
        }

        val forks = if (dice.roll() < 0.3) 3 else 2
        val way = if (dice.roll() < 0.5) 1.0 else -1.0

        for (i in 0 until forks) {
            val spread = dice.between(0.22, 0.5)
            val turn = when (i) {
                0 -> way * spread * 0.5
                1 -> -way * spread
                else -> way * spread * 1.5
            }
            // they reach for the sky again: the way they point is pulled back toward straight up
            val toward = (bent * 0.6 + turn).coerceIn(-1.3, 1.3)
            val share = if (i == 0) 0.85 else dice.between(0.55, 0.7)

            limb(x = ex, y = ey, angle = toward, length = length * dice.between(0.68, 0.84), width = end * share, depth = depth + 1)
        }
    }

    /** How strongly a limb [width] metres thick is drawn: full from some 3 pixels, fainter below. */
    private fun faint(width: Double): Double = ((width * pixels - 0.6) / 2.4).coerceIn(0.2, 1.0)

    /** A piece of a limb from ([x0], [y0]) [w0] thick to ([x1], [y1]) [w1] thick, round at its end. */
    private fun taper(x0: Double, y0: Double, w0: Double, x1: Double, y1: Double, w1: Double) {
        val dx = x1 - x0
        val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy)

        if (length <= 0.0) {
            return
        }

        val nx = -dy / length
        val ny = dx / length

        polygon(
            listOf(
                x0 + nx * w0 / 2, y0 + ny * w0 / 2,
                x1 + nx * w1 / 2, y1 + ny * w1 / 2,
                x1 - nx * w1 / 2, y1 - ny * w1 / 2,
                x0 - nx * w0 / 2, y0 - ny * w0 / 2,
            )
        )
        disc(x = x1, y = y1, radius = w1 / 2)

        if (!carving) {
            return
        }

        // the relief: round, highest along its middle
        val plain = g.fillStyle

        for ((share, level) in listOf(0.62 to 0.64, 0.28 to 0.78)) {
            g.fillStyle = grey(level)
            polygon(
                listOf(
                    x0 + nx * w0 / 2 * share, y0 + ny * w0 / 2 * share,
                    x1 + nx * w1 / 2 * share, y1 + ny * w1 / 2 * share,
                    x1 - nx * w1 / 2 * share, y1 - ny * w1 / 2 * share,
                    x0 - nx * w0 / 2 * share, y0 - ny * w0 / 2 * share,
                )
            )
        }

        g.fillStyle = plain
    }

    /** A bare old tree at [x], its foot at [base]: a trunk [width] thick at the foot that forks, about [height] tall. */
    fun bareTree(x: Double, base: Double, width: Double, height: Double) {
        // the foot spreads into its roots
        polygon(
            listOf(
                x - width * 1.3, base - 0.3,
                x - width * 0.8, base + width * 0.3,
                x - width * 0.5, base + width * 1.5,
                x + width * 0.5, base + width * 1.5,
                x + width * 0.8, base + width * 0.3,
                x + width * 1.3, base - 0.3,
            )
        )
        limb(x = x, y = base - 0.3, angle = dice.between(-0.07, 0.07), length = height * dice.between(0.27, 0.35), width = width, depth = 0)
    }

    /** A dark pine at [x], its foot at [base], [height] tall and [width] wide at its lowest branches: tiers of them. */
    fun pine(x: Double, base: Double, height: Double, width: Double) {
        val tiers = 7 + (dice.roll() * 4).toInt()

        g.fillRect(x - width * 0.05, base - 0.3, width * 0.1, height * 0.3)

        val right = mutableListOf<Double>()
        val left = mutableListOf<Double>()

        for (i in 1..tiers) {
            val f = i.toDouble() / tiers
            val y = base + height * (1.0 - 0.86 * f)
            val outR = width / 2 * f.pow(0.8) * dice.between(0.8, 1.15)
            val outL = width / 2 * f.pow(0.8) * dice.between(0.8, 1.15)
            val notch = height * 0.025

            right += listOf(x + outR, y - notch, x + outR * 0.4, y - notch * 0.2)
            left += listOf(x - outL * 0.4, y - notch * 0.2, x - outL, y - notch)
        }

        // down the right side from the tip, across the foot of the crown, up the left side
        val xy = mutableListOf(x, base + height)

        xy += right
        xy += listOf(x + width * 0.08, base + height * 0.1, x - width * 0.08, base + height * 0.1)

        for (i in left.size - 4 downTo 0 step 4) {
            xy += listOf(left[i + 2], left[i + 3], left[i], left[i + 1])
        }

        polygon(xy)

        // the relief: each tier of branches hangs out over the one below it, and the whole is round
        within(xy) {
            var above = base + height

            for (i in 1..tiers) {
                val y = base + height * (1.0 - 0.86 * i / tiers) - height * 0.025

                lean(x0 = 0.0, y0 = above, from = 0.26, x1 = 0.0, y1 = y, to = 0.78)
                g.fillRect(x - width, y, width * 2, above - y)
                above = y
            }

            val round = g.createLinearGradient(x - width / 2, 0.0, x + width / 2, 0.0)

            round.addColorStop(0.0, "rgba(0,0,0,0.4)")
            round.addColorStop(0.5, "rgba(0,0,0,0)")
            round.addColorStop(1.0, "rgba(0,0,0,0.4)")
            g.fillStyle = round
            g.fillRect(x - width, base, width * 2, height)
        }
    }

    /**
     * A rough standing stone at [x], its foot at [base], [width] wide at its foot and [height] tall, leaning by [lean]
     * (metres across for a metre up): narrower toward its top, its edges and its top uneven.
     */
    fun monolith(x: Double, base: Double, width: Double, height: Double, lean: Double) {
        val half = width / 2

        // rough-hewn: nearly straight sides, a little narrower up high, a broken shoulder at the top
        fun at(across: Double, up: Double): List<Double> = listOf(x + across + lean * up * height, base + up * height)

        val xy = at(-half, 0.0) +
            at(-half * dice.between(0.9, 1.0), dice.between(0.35, 0.55)) +
            at(-half * dice.between(0.78, 0.9), dice.between(0.8, 0.88)) +
            at(-half * dice.between(0.3, 0.5), dice.between(0.98, 1.03)) +
            at(half * dice.between(0.1, 0.4), dice.between(0.9, 1.0)) +
            at(half * dice.between(0.75, 0.88), dice.between(0.72, 0.84)) +
            at(half * dice.between(0.9, 1.0), dice.between(0.3, 0.5)) +
            at(half, 0.0)

        polygon(xy)

        // the relief: hewn in a few large faces, each leaning its own way, and a crack or two down it
        within(xy) {
            lean(x0 = x - half, y0 = base, from = detail.between(0.3, 0.5), x1 = x + half, y1 = base + height * 0.3, to = detail.between(0.55, 0.75))
            g.fillRect(x - width, base, width * 2, height * 1.1)

            repeat(4) {
                val up = detail.between(0.05, 0.85)
                val across = detail.between(-0.5, 0.5) * width
                val wide = detail.between(0.5, 0.9) * width
                val high = detail.between(0.15, 0.3) * height
                val (ax, ay) = at(across, up)

                lean(
                    x0 = ax, y0 = ay, from = detail.between(0.25, 0.75),
                    x1 = ax + wide * detail.between(-1.0, 1.0), y1 = ay + high, to = detail.between(0.25, 0.75),
                )
                polygon(listOf(ax, ay, ax + wide, ay + high * detail.between(0.1, 0.5), ax + wide * 0.6, ay + high, ax - wide * 0.3, ay + high * 0.7))
            }

            g.strokeStyle = grey(0.12)
            g.lineWidth = JOINT

            repeat(2) {
                var (cx, cy) = at(detail.between(-0.35, 0.35) * width, detail.between(0.5, 1.0))

                g.beginPath()
                g.moveTo(cx, cy)

                repeat(5) {
                    cx += detail.between(-0.18, 0.18) * width
                    cy -= detail.between(0.04, 0.12) * height
                    g.lineTo(cx, cy)
                }

                g.stroke()
            }
        }
    }

    /**
     * A column at [x], its foot at [base], [width] thick and [height] tall: with its capital where it stands whole
     * ([whole]), else broken off, its top jagged and slanting.
     */
    fun column(x: Double, base: Double, width: Double, height: Double, whole: Boolean) {
        // its base block
        g.fillRect(x - width * 0.7, base - 0.3, width * 1.4, width * 0.55 + 0.3)

        // the relief of its shaft: round, and fluted
        fun fluted(xy: List<Double>) = within(xy) {
            val round = g.createLinearGradient(x - width / 2, 0.0, x + width / 2, 0.0)

            round.addColorStop(0.0, grey(0.25))
            round.addColorStop(0.5, grey(0.8))
            round.addColorStop(1.0, grey(0.25))
            g.fillStyle = round
            g.fillRect(x - width, base, width * 2, height + width)
            g.fillStyle = "rgba(0,0,0,0.45)"

            for (i in 1..5) {
                g.fillRect(x - width / 2 + width * i / 6 - JOINT / 2, base, JOINT, height + width)
            }
        }

        if (whole) {
            val shaft = listOf(x - width / 2, base, x - width * 0.42, base + height, x + width * 0.42, base + height, x + width / 2, base)

            polygon(shaft)
            fluted(shaft)
            g.fillRect(x - width * 0.62, base + height, width * 1.24, width * 0.35)
            g.fillRect(x - width * 0.8, base + height + width * 0.35, width * 1.6, width * 0.4)

            return
        }

        val slant = dice.between(-0.25, 0.25) * width

        val shaft = listOf(
            x - width / 2, base,
            x - width * 0.44, base + height - slant,
            x - width * 0.15, base + height + dice.between(-0.4, 0.2) * width,
            x + width * 0.1, base + height - dice.between(0.1, 0.5) * width,
            x + width * 0.44, base + height + slant,
            x + width / 2, base,
        )

        polygon(shaft)
        fluted(shaft)
    }

    /** The forest beside the stage: bare giants and a few young trees before dark pines further back. */
    fun forestBeside() {
        near()

        var d = DRAWN_FROM + dice.between(1.0, 1.8)

        while (d < DRAWN_TO + 1.0) {
            val young = dice.roll() < 0.3

            if (young) {
                bareTree(x = d, base = 0.0, width = dice.between(0.22, 0.32), height = dice.between(9.0, 12.0))
            } else {
                bareTree(x = d, base = 0.0, width = dice.between(0.55, 0.95), height = dice.between(13.0, 17.0))
            }

            d += if (young) dice.between(1.4, 2.2) else dice.between(2.6, 4.2)
        }

        far()
        d = DRAWN_FROM + dice.between(2.0, 3.0)

        while (d < DRAWN_TO + 2.0) {
            pine(x = d, base = 0.0, height = dice.between(9.0, 15.0), width = dice.between(3.0, 4.6))
            d += dice.between(1.8, 3.0)
        }
    }

    /**
     * The ruins beside the stage: a broken column near it and a drum fallen from it, then the arcade of a colossal
     * theatre, its arches whole in the lower tier but here and there a bay fallen in, the upper tier broken off lower
     * toward the stage; and further back its outer wall, higher still.
     */
    fun ruinsBeside() {
        near()
        column(x = 5.3 + dice.between(-0.2, 0.2), base = 0.0, width = 1.15, height = dice.between(4.0, 5.2), whole = false)
        polygon(listOf(6.1, -0.2, 6.15, 1.0, 7.5, 1.05, 7.55, -0.2))

        val from = 8.0 + dice.between(-0.3, 0.3)
        val module = 5.6
        val pier = 2.0
        var left = from

        while (left < DRAWN_TO + 1.0) {
            val share = (left - from) / (DRAWN_TO - from)
            // the lower tier's arches stand (their crowns at 6.8 m), unless the bay has fallen in
            val fallen = left > from && dice.roll() < 0.22
            val top = if (fallen) dice.between(3.8, 5.8) else (8.6 + 6.0 * share + dice.between(-1.0, 1.2)).coerceIn(7.9, 14.6)

            broken(left = left, right = left + module, bottom = -0.3, top = top, rough = 0.7)

            if (top > 8.9) {
                g.fillRect(left - 0.1, 8.0, module + 0.2, 0.45)
            }

            opening(left = left + pier, right = left + module, bottom = -0.3, spring = 5.0)

            if (top > 11.2) {
                opening(left = left + pier + 0.3, right = left + module - 0.3, bottom = 9.0, spring = 10.4)
            }

            left += module
        }

        // blocks fallen from it
        for (i in 0 until 4) {
            val x = dice.between(DRAWN_FROM + 0.8, DRAWN_TO)
            val w = dice.between(0.9, 1.8)
            val h = dice.between(0.5, 1.0)

            polygon(listOf(x - w / 2, -0.2, x - w / 2 + 0.1, h, x + w / 2, h - dice.between(0.0, 0.3), x + w / 2 + 0.1, -0.2))
        }

        // the outer wall behind, its arches whole: an outline with the openings in it
        far()
        g.beginPath()

        val wallFrom = 12.5
        val wallTo = DRAWN_TO + 2.0

        g.moveTo(wallFrom, -0.3)

        var x = wallFrom

        while (x < wallTo) {
            val y = 16.4 + dice.between(-0.6, 0.8)

            g.lineTo(x, y)
            x += dice.between(1.2, 2.6)
            g.lineTo(x, y)
        }

        g.lineTo(wallTo, -0.3)
        g.closePath()

        var bay = wallFrom + 2.2

        while (bay + 4.2 < wallTo) {
            g.moveTo(bay, -0.3)
            g.lineTo(bay, 11.0)
            g.arc(bay + 2.1, 11.0, 2.1, PI, 0.0, true)
            g.lineTo(bay + 4.2, -0.3)
            g.closePath()
            bay += 6.4
        }

        g.fill(CanvasFillRule.EVENODD)
    }

    /**
     * The standing stones beside the stage: a ring of rough monoliths, small near the stage and towering further out,
     * with one pair under its lintel, boulders at their feet, and smaller stones further back between them.
     */
    fun stonesBeside() {
        near()

        for ((at, wide, high) in STONES) {
            monolith(
                x = at + dice.between(-0.4, 0.4),
                base = -0.3,
                width = wide * dice.between(0.9, 1.1),
                height = high * dice.between(0.92, 1.06),
                lean = dice.between(-0.03, 0.03),
            )
        }

        // the lintel on the pair
        val lift = dice.between(-0.2, 0.2)

        polygon(listOf(13.6, 9.1 + lift, 13.8, 10.5, 19.6, 10.6 - lift, 19.9, 9.2))

        for (i in 0 until 5) {
            monolith(
                x = dice.between(DRAWN_FROM + 1.0, DRAWN_TO),
                base = -0.3,
                width = dice.between(0.9, 1.5),
                height = dice.between(0.8, 1.4),
                lean = dice.between(-0.2, 0.2),
            )
        }

        far()

        var d = DRAWN_FROM + dice.between(2.0, 3.0)

        while (d < DRAWN_TO + 2.0) {
            monolith(x = d, base = -0.3, width = dice.between(1.2, 2.0), height = dice.between(2.8, 5.8), lean = dice.between(-0.04, 0.04))
            d += dice.between(2.2, 3.4)
        }
    }

    /** The forest round the audience: dark pines and bare crowns along the ring, a taller row of pines behind. */
    fun forestFar(horizon: Horizon) {
        near()

        var s = -horizon.reach

        while (s < horizon.reach) {
            val top = horizon.top(s) * dice.between(0.93, 1.05)

            if (dice.roll() < 0.3) {
                bareTree(x = s, base = Around.FAR_BASE, width = dice.between(0.8, 1.3), height = top - Around.FAR_BASE + 1.0)
            } else {
                pine(x = s, base = Around.FAR_BASE, height = top - Around.FAR_BASE, width = dice.between(3.2, 5.0))
            }

            s += dice.between(1.6, 3.2)
        }

        far()
        s = -horizon.reach

        while (s < horizon.reach) {
            val high = horizon.top(s) * dice.between(1.04, 1.12) - Around.FAR_BASE

            pine(x = s, base = Around.FAR_BASE, height = high, width = dice.between(3.5, 5.5))
            s += dice.between(2.0, 3.5)
        }
    }

    /**
     * The ruins round the audience: the outer ring of the colossal theatre, three tiers of arches, broken: high at the
     * sides, only stumps of the lowest tier in the middle with a few columns standing, and one great arch whole round
     * the moon.
     */
    fun ruinsFar(horizon: Horizon) {
        near()

        val module = 7.2
        val pier = 2.6
        var left = -horizon.reach

        while (left < horizon.reach) {
            val middle = left + module / 2

            if (abs(middle - horizon.moonAt) < 9.5) {
                left += module

                continue
            }

            val top = max(Around.FAR_BASE + 4.0, horizon.top(middle) + dice.between(-2.2, 1.4))

            broken(left = left, right = left + module, bottom = Around.FAR_BASE, top = top, rough = 0.9)

            if (top > 17.0) {
                g.fillRect(left - 0.15, 16.0, module + 0.3, 0.6)
            }

            opening(left = left + pier, right = left + module, bottom = Around.FAR_BASE, spring = 12.6)

            if (top > 19.0) {
                opening(left = left + pier + 0.3, right = left + module - 0.3, bottom = 17.0, spring = 18.8)
            }

            if (top > 26.0) {
                cut()
                g.fillRect(left + pier + 1.0, 23.8, module - pier - 2.0, 1.8)
                near()
            }

            left += module
        }

        // the great arch round the moon, as the reverse total sees it
        val moon = horizon.moonAt

        broken(left = moon - 7.8, right = moon + 7.8, bottom = Around.FAR_BASE, top = 24.2, rough = 1.0)
        opening(left = moon - 4.6, right = moon + 4.6, bottom = Around.FAR_BASE, spring = 17.2)

        // columns standing in the middle, where the ring is broken down
        for (at in listOf(-0.16, 0.05, 0.19)) {
            val x = (horizon.middle + at) * Around.FAR_RADIUS + dice.between(-2.0, 2.0)

            if (abs(x - moon) > 9.0) {
                column(x = x, base = Around.FAR_BASE, width = 1.7, height = dice.between(8.0, 10.0), whole = dice.roll() < 0.4)
            }
        }
    }

    /**
     * The standing stones round the audience: monoliths along the ring, towering at the sides and low in the middle,
     * one pair under its lintel round the moon, smaller stones behind in the gaps.
     */
    fun stonesFar(horizon: Horizon) {
        near()

        val moon = horizon.moonAt
        var s = -horizon.reach

        while (s < horizon.reach) {
            if (abs(s - moon) < 8.5) {
                s = moon + 8.5

                continue
            }

            val top = horizon.top(s) * dice.between(0.9, 1.05)

            monolith(
                x = s,
                base = Around.FAR_BASE,
                width = dice.between(3.0, 5.5),
                height = top - Around.FAR_BASE,
                lean = dice.between(-0.03, 0.03),
            )
            s += dice.between(6.5, 11.0)
        }

        // the pair round the moon and their lintel
        monolith(x = moon - 5.0, base = Around.FAR_BASE, width = 3.2, height = 13.6, lean = 0.01)
        monolith(x = moon + 5.0, base = Around.FAR_BASE, width = 3.2, height = 13.4, lean = -0.01)
        polygon(listOf(moon - 7.4, 21.0, moon - 7.2, 23.4, moon + 7.0, 23.2, moon + 7.3, 21.1))

        far()
        s = -horizon.reach + dice.between(2.0, 5.0)

        while (s < horizon.reach) {
            val high = horizon.top(s) * 0.82 - Around.FAR_BASE

            monolith(x = s, base = Around.FAR_BASE, width = dice.between(2.0, 3.5), height = high, lean = 0.0)
            s += dice.between(4.0, 7.0)
        }
    }

    companion object {
        /** A shape further back: a little under half. */
        const val FAR = "rgb(107,107,107)"

        /** A limb thinner than this many pixels forks no further. */
        const val FINEST = 1.5

        /** A wall's relief: how high a course of its blocks is, how long a block, and how wide a joint, in metres. */
        const val COURSE = 0.95
        const val BLOCK = 2.1
        const val JOINT = 0.13

        /** The standing stones beside the stage: where, how wide and how tall, in metres out from its middle. */
        val STONES = listOf(
            Triple(5.7, 1.9, 4.6),
            Triple(8.5, 2.5, 6.4),
            Triple(11.7, 2.9, 8.2),
            Triple(15.0, 2.3, 9.4),
            Triple(18.4, 2.3, 9.4),
            Triple(22.8, 3.3, 11.4),
            Triple(27.8, 3.9, 13.2),
        )
    }
}
