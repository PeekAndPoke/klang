package io.peekandpoke.klang.workshop.keys

import kotlin.math.max
import kotlin.math.min

/** Where a key hint shows, next to the element it belongs to (its anchor). */
enum class KeyHintPlacement {
    /** Under the anchor, centred. */
    Below,

    /** Over the anchor's top edge, centred. */
    Above,

    /** Right on top of the anchor, covering it, centred. */
    Over,

    /** Left of the anchor, centred in height. */
    Start,

    /** Right of the anchor, centred in height. */
    End,
}

/**
 * Where the [KeyHintStage] puts its hints: a pure function of the anchors, the hints' sizes and the window, so it is
 * tested without a browser's layout.
 *
 * 1. Each hint goes to its [KeyHintPlacement] at its anchor, [GAP_PX] away. At the window's edge it flips to the other
 *    side (Below to Above, Start to End and back) when the other side has the room on that axis.
 * 2. Hints in one row (Below, Above, Over) or one column (Start, End) that would overlap are pushed apart along it, in
 *    their anchors' order; each chain of hints that touch is centred on its own anchors, a hint nobody pushed stays at
 *    its anchor. A row wider than the window wraps into further lines, a column taller than the window into further
 *    columns, away from the anchors, or towards them where the window has no room that way.
 * 3. Every hint is kept inside the window, [MARGIN_PX] from its edge: along its row or column by the packing, across
 *    it by a clamp.
 *
 * A hint whose anchor has no size or lies outside the window is not placed at all.
 */
object KeyHintLayout {

    /** The space between a hint and its anchor, and between two hints, in CSS pixels. */
    const val GAP_PX = 4.0

    /** The space between a hint and the window's edge, in CSS pixels. */
    const val MARGIN_PX = 4.0

    /** A rectangle in the window's coordinates (CSS pixels, as `getBoundingClientRect` gives it). */
    data class Box(val left: Double, val top: Double, val width: Double, val height: Double) {
        val right: Double get() = left + width
        val bottom: Double get() = top + height
        val centerX: Double get() = left + width / 2
        val centerY: Double get() = top + height / 2
    }

    /** One hint to place: its anchor, its own size, where it wants to be. */
    data class Item(
        val id: Int,
        val anchor: Box,
        val width: Double,
        val height: Double,
        val placement: KeyHintPlacement,
    )

    /** Where each placed hint goes, by [Item.id]. */
    fun place(items: List<Item>, viewportWidth: Double, viewportHeight: Double): Map<Int, Box> {
        val visible = items.filter { item ->
            val a = item.anchor

            a.width > 0.0 && a.height > 0.0 &&
                a.right > 0.0 && a.bottom > 0.0 && a.left < viewportWidth && a.top < viewportHeight
        }

        val wanted = visible.map { item -> desired(item = item, viewportWidth = viewportWidth, viewportHeight = viewportHeight) }

        val (columns, rows) = wanted.partition { it.placement == KeyHintPlacement.Start || it.placement == KeyHintPlacement.End }

        val result = mutableMapOf<Int, Box>()

        groups(placed = rows, crossStart = { it.box.top }, crossEnd = { it.box.bottom }).forEach { row ->
            pack(group = row, alongRow = true, limit = viewportWidth, crossLimit = viewportHeight).forEach { (id, box) ->
                // Along the row the packing kept it inside; across it, the clamp does
                result[id] = box.copy(top = clampStart(start = box.top, size = box.height, limit = viewportHeight))
            }
        }

        groups(placed = columns, crossStart = { it.box.left }, crossEnd = { it.box.right }).forEach { column ->
            pack(group = column, alongRow = false, limit = viewportHeight, crossLimit = viewportWidth).forEach { (id, box) ->
                result[id] = box.copy(left = clampStart(start = box.left, size = box.width, limit = viewportWidth))
            }
        }

        return result
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** A hint at its wanted spot, with the placement it ended up with after a flip. */
    private data class Placed(val id: Int, val box: Box, val placement: KeyHintPlacement, val anchor: Box)

    private fun desired(item: Item, viewportWidth: Double, viewportHeight: Double): Placed {
        val a = item.anchor
        val w = item.width
        val h = item.height

        fun at(placement: KeyHintPlacement): Box = when (placement) {
            KeyHintPlacement.Below -> Box(left = a.centerX - w / 2, top = a.bottom + GAP_PX, width = w, height = h)
            KeyHintPlacement.Above -> Box(left = a.centerX - w / 2, top = a.top - GAP_PX - h, width = w, height = h)
            KeyHintPlacement.Over -> Box(left = a.centerX - w / 2, top = a.centerY - h / 2, width = w, height = h)
            KeyHintPlacement.Start -> Box(left = a.left - GAP_PX - w, top = a.centerY - h / 2, width = w, height = h)
            KeyHintPlacement.End -> Box(left = a.right + GAP_PX, top = a.centerY - h / 2, width = w, height = h)
        }

        val first = at(item.placement)
        val bottomLimit = viewportHeight - MARGIN_PX
        val rightLimit = viewportWidth - MARGIN_PX

        // At the window's edge: the other side, when it has the room on the axis of the flip (the clamp handles the other)
        val flipped = when (item.placement) {
            KeyHintPlacement.Below -> KeyHintPlacement.Above
                .takeIf { first.bottom > bottomLimit && at(it).top >= MARGIN_PX }

            KeyHintPlacement.Above -> KeyHintPlacement.Below
                .takeIf { first.top < MARGIN_PX && at(it).bottom <= bottomLimit }

            KeyHintPlacement.Start -> KeyHintPlacement.End
                .takeIf { first.left < MARGIN_PX && at(it).right <= rightLimit }

            KeyHintPlacement.End -> KeyHintPlacement.Start
                .takeIf { first.right > rightLimit && at(it).left >= MARGIN_PX }

            KeyHintPlacement.Over -> null
        }

        val placement = flipped ?: item.placement

        return Placed(id = item.id, box = at(placement), placement = placement, anchor = a)
    }

    /** The hints whose spans across the flow overlap: one row (or column) each, in flow order. */
    private fun groups(
        placed: List<Placed>,
        crossStart: (Placed) -> Double,
        crossEnd: (Placed) -> Double,
    ): List<List<Placed>> {
        val sorted = placed.sortedBy(crossStart)
        val result = mutableListOf<MutableList<Placed>>()
        var groupEnd = Double.NEGATIVE_INFINITY

        sorted.forEach { p ->
            if (result.isEmpty() || crossStart(p) >= groupEnd) {
                result.add(mutableListOf(p))
                groupEnd = crossEnd(p)
            } else {
                result.last().add(p)
                groupEnd = max(groupEnd, crossEnd(p))
            }
        }

        return result
    }

    /**
     * A row ([alongRow]) or a column: pushed apart along it, each chain centred on its anchors, wrapped into further
     * lines away from the anchors when longer than the window ([limit] along it), or towards them when the lines would
     * leave the window that way ([crossLimit] across it): the clamp then never pushes one line onto another.
     */
    private fun pack(group: List<Placed>, alongRow: Boolean, limit: Double, crossLimit: Double): List<Pair<Int, Box>> {
        fun start(box: Box) = if (alongRow) box.left else box.top
        fun size(box: Box) = if (alongRow) box.width else box.height
        fun anchorCenter(p: Placed) = if (alongRow) p.anchor.centerX else p.anchor.centerY

        val available = limit - 2 * MARGIN_PX
        val lines = mutableListOf<MutableList<Placed>>()
        var lineLength = 0.0

        group.sortedBy { anchorCenter(it) }.forEach { p ->
            val needed = lineLength + GAP_PX + size(p.box)

            if (lines.isEmpty() || needed > available) {
                lines.add(mutableListOf(p))
                lineLength = size(p.box)
            } else {
                lines.last().add(p)
                lineLength = needed
            }
        }

        // A line is the group's whole band across the flow: its hints need not start at one height
        val bandStart = group.minOf { if (alongRow) it.box.top else it.box.left }
        val bandEnd = group.maxOf { if (alongRow) it.box.bottom else it.box.right }
        val lineStep = bandEnd - bandStart + GAP_PX

        // Further lines go away from the anchors: up for hints above, left for hints at the start, else down or right;
        // the other way when the last line would leave the window
        val reach = (lines.size - 1) * lineStep
        val awayWanted = when (lines.first().first().placement) {
            KeyHintPlacement.Above, KeyHintPlacement.Start -> -1.0
            else -> 1.0
        }
        val away = when {
            awayWanted > 0 && bandEnd + reach > crossLimit - MARGIN_PX -> -1.0
            awayWanted < 0 && bandStart - reach < MARGIN_PX -> 1.0
            else -> awayWanted
        }

        return lines.flatMapIndexed { index, line ->
            val offset = away * index * lineStep
            val starts = spread(desired = line.map { start(it.box) }, sizes = line.map { size(it.box) }, limit = limit)

            line.mapIndexed { i, p ->
                p.id to when {
                    alongRow -> p.box.copy(left = starts[i], top = p.box.top + offset)
                    else -> p.box.copy(top = starts[i], left = p.box.left + offset)
                }
            }
        }
    }

    /**
     * Starts along one axis for boxes in order, wanting [desired]: none overlaps the one before it, each chain of
     * touching boxes is centred on its wanted spots (a box nobody pushed stays where it wants to be), and every chain
     * stays inside 0..[limit]: a chain held back at the window's edge joins the one it then touches.
     */
    private fun spread(desired: List<Double>, sizes: List<Double>, limit: Double): List<Double> {
        // Chains of boxes, each as the indices it holds; centred, merged with the one before while they overlap
        val chains = mutableListOf<MutableList<Int>>()
        val chainStart = mutableListOf<Double>()

        fun lengthOf(chain: List<Int>): Double = chain.sumOf { sizes[it] } + GAP_PX * (chain.size - 1)

        fun centredStart(chain: List<Int>): Double {
            // The start that puts each box, at its place in the chain, closest to its wanted spot on the average
            var offset = 0.0

            return chain.map { i -> (desired[i] - offset).also { offset += sizes[i] + GAP_PX } }.average()
        }

        desired.indices.forEach { i ->
            chains.add(mutableListOf(i))
            chainStart.add(clampStart(start = desired[i], size = sizes[i], limit = limit))

            while (chains.size > 1) {
                val last = chains.size - 1
                val before = chains[last - 1]

                if (chainStart[last - 1] + lengthOf(before) + GAP_PX <= chainStart[last]) {
                    break
                }

                before.addAll(chains.removeAt(last))
                chainStart.removeAt(last)
                chainStart[last - 1] = clampStart(start = centredStart(before), size = lengthOf(before), limit = limit)
            }
        }

        val starts = MutableList(desired.size) { 0.0 }

        chains.forEachIndexed { c, chain ->
            var at = chainStart[c]

            chain.forEach { i ->
                starts[i] = at
                at += sizes[i] + GAP_PX
            }
        }

        return starts
    }

    /** [start] moved so a box of [size] stays inside 0..[limit], [MARGIN_PX] from both ends where it fits. */
    private fun clampStart(start: Double, size: Double, limit: Double): Double =
        min(max(start, MARGIN_PX), max(MARGIN_PX, limit - MARGIN_PX - size))
}
