package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.workshop.keys.KeyHintLayout.Box
import io.peekandpoke.klang.workshop.keys.KeyHintLayout.GAP_PX
import io.peekandpoke.klang.workshop.keys.KeyHintLayout.Item
import io.peekandpoke.klang.workshop.keys.KeyHintLayout.MARGIN_PX

/**
 * Where the stage puts its hints: at their anchor, flipped at the window's edge, pushed apart, kept in the window.
 */
class KeyHintLayoutSpec : StringSpec({

    val window = 800.0 to 600.0

    fun place(vararg items: Item) =
        KeyHintLayout.place(items = items.toList(), viewportWidth = window.first, viewportHeight = window.second)

    fun hint(
        id: Int,
        anchor: Box,
        width: Double = 40.0,
        height: Double = 16.0,
        placement: KeyHintPlacement = KeyHintPlacement.Below,
    ) = Item(id = id, anchor = anchor, width = width, height = height, placement = placement)

    fun overlap(a: Box, b: Box): Boolean = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    val button = Box(left = 380.0, top = 100.0, width = 40.0, height = 30.0)

    "each placement at its anchor" {
        withClue("below, centred") {
            place(hint(1, button))[1] shouldBe Box(left = 380.0, top = 130.0 + GAP_PX, width = 40.0, height = 16.0)
        }

        withClue("above, centred") {
            place(hint(1, button, placement = KeyHintPlacement.Above))[1] shouldBe
                Box(left = 380.0, top = 100.0 - GAP_PX - 16.0, width = 40.0, height = 16.0)
        }

        withClue("over, centred both ways") {
            place(hint(1, button, width = 20.0, placement = KeyHintPlacement.Over))[1] shouldBe
                Box(left = 390.0, top = 107.0, width = 20.0, height = 16.0)
        }

        withClue("start and end, centred in height") {
            place(hint(1, button, placement = KeyHintPlacement.Start))[1]!!.right shouldBe 380.0 - GAP_PX
            place(hint(1, button, placement = KeyHintPlacement.End))[1]!!.left shouldBe 420.0 + GAP_PX
        }
    }

    "at the window's edge a hint flips to the other side" {
        val bottom = Box(left = 380.0, top = 570.0, width = 40.0, height = 28.0)
        place(hint(1, bottom))[1]!!.bottom shouldBe 570.0 - GAP_PX

        val top = Box(left = 380.0, top = 2.0, width = 40.0, height = 20.0)
        place(hint(1, top, placement = KeyHintPlacement.Above))[1]!!.top shouldBe 22.0 + GAP_PX

        val left = Box(left = 5.0, top = 100.0, width = 40.0, height = 20.0)
        place(hint(1, left, placement = KeyHintPlacement.Start))[1]!!.left shouldBe 45.0 + GAP_PX
    }

    "two buttons side by side with wide hints: pushed apart, in order, centred on their anchors as a pair" {
        // Play, then a narrow Stop right next to it: both hints are wider than the buttons
        val play = Box(left = 300.0, top = 100.0, width = 60.0, height = 30.0)
        val stop = Box(left = 364.0, top = 100.0, width = 30.0, height = 30.0)

        val placed = place(hint(1, play, width = 70.0), hint(2, stop, width = 70.0))
        val a = placed[1]!!
        val b = placed[2]!!

        overlap(a, b) shouldBe false
        withClue("in the buttons' order") { a.left shouldBeLessThanOrEqual b.left }
        withClue("the gap between them") { (b.left - a.right) shouldBe GAP_PX }
        withClue("the pair stays centred on its anchors") {
            ((a.centerX + b.centerX) / 2) shouldBe ((play.centerX + stop.centerX) / 2)
        }
    }

    "hints in different rows are not pushed" {
        val upper = Box(left = 380.0, top = 100.0, width = 40.0, height = 30.0)
        val lower = Box(left = 380.0, top = 300.0, width = 40.0, height = 30.0)

        val placed = place(hint(1, upper), hint(2, lower))

        placed[1]!!.left shouldBe 380.0
        placed[2]!!.left shouldBe 380.0
    }

    "a row wider than the window wraps into a further line, away from the anchors" {
        val anchors = (0 until 6).map { Box(left = 100.0 + it * 100.0, top = 100.0, width = 90.0, height = 30.0) }
        val placed = place(*anchors.mapIndexed { i, a -> hint(i, a, width = 200.0) }.toTypedArray())

        withClue("no two overlap") {
            placed.values.toList().let { boxes ->
                boxes.indices.forEach { i -> (i + 1 until boxes.size).forEach { j -> overlap(boxes[i], boxes[j]) shouldBe false } }
            }
        }

        withClue("a second line, below the first one: away from the anchors") {
            val tops = placed.values.map { it.top }.distinct().sorted()
            tops.size shouldBe 2
            tops[1] shouldBeGreaterThanOrEqual (tops[0] + 16.0)
            placed.values.minOf { it.top } shouldBeGreaterThanOrEqual 130.0
        }
    }

    "a row of hints above wraps upwards, away from the anchors" {
        val anchors = (0 until 6).map { Box(left = 100.0 + it * 100.0, top = 300.0, width = 90.0, height = 30.0) }
        val placed = place(*anchors.mapIndexed { i, a -> hint(i, a, width = 200.0, placement = KeyHintPlacement.Above) }.toTypedArray())

        val bottoms = placed.values.map { it.bottom }.distinct().sorted()
        bottoms.size shouldBe 2
        withClue("the second line above the first, and all of them above the anchors") {
            bottoms[0] shouldBeLessThanOrEqual (bottoms[1] - 16.0)
            placed.values.maxOf { it.bottom } shouldBeLessThanOrEqual 300.0
        }
    }

    "in a corner a hint still flips when the other side has the room, the clamp keeps it in the window sideways" {
        val bottomLeft = Box(left = 0.0, top = 570.0, width = 10.0, height = 28.0)
        val below = place(hint(1, bottomLeft))[1]!!

        withClue("above its button, not on it") {
            below.bottom shouldBeLessThanOrEqual 570.0
            below.left shouldBe MARGIN_PX
        }

        val topLeft = Box(left = 0.0, top = 2.0, width = 10.0, height = 20.0)
        place(hint(1, topLeft, placement = KeyHintPlacement.Above))[1]!!.top shouldBeGreaterThanOrEqual 22.0
    }

    "a hint nobody pushed stays at its anchor, also next to a pushed pair" {
        val play = Box(left = 300.0, top = 100.0, width = 60.0, height = 30.0)
        val stop = Box(left = 364.0, top = 100.0, width = 30.0, height = 30.0)
        val far = Box(left = 600.0, top = 100.0, width = 30.0, height = 30.0)

        val placed = place(hint(1, play, width = 70.0), hint(2, stop, width = 70.0), hint(3, far, width = 30.0))

        placed[3]!!.centerX shouldBe far.centerX
    }

    "a column taller than the window wraps into a further column, nothing overlaps" {
        val rows = (0 until 30).map { Box(left = 100.0, top = 10.0 + it * 19.0, width = 200.0, height = 19.0) }
        val placed = place(*rows.mapIndexed { i, a -> hint(i, a, height = 28.0, placement = KeyHintPlacement.End) }.toTypedArray())
        val boxes = placed.values.toList()

        boxes.indices.forEach { i -> (i + 1 until boxes.size).forEach { j -> overlap(boxes[i], boxes[j]) shouldBe false } }
        withClue("every hint inside the window") {
            boxes.forEach { it.bottom shouldBeLessThanOrEqual (600.0 - MARGIN_PX) }
        }
        withClue("the further column to the right, away from the anchors") {
            boxes.map { it.left }.distinct().size shouldBe 2
        }
    }

    "hints at the side, stacked: pushed apart up and down" {
        val one = Box(left = 100.0, top = 100.0, width = 40.0, height = 10.0)
        val two = Box(left = 100.0, top = 110.0, width = 40.0, height = 10.0)

        val placed = place(hint(1, one, placement = KeyHintPlacement.End), hint(2, two, placement = KeyHintPlacement.End))

        overlap(placed[1]!!, placed[2]!!) shouldBe false
        placed[1]!!.top shouldBeLessThanOrEqual placed[2]!!.top
    }

    "kept inside the window" {
        val corner = Box(left = 0.0, top = 100.0, width = 10.0, height = 30.0)
        val placed = place(hint(1, corner, width = 120.0))[1]!!

        placed.left shouldBeGreaterThanOrEqual MARGIN_PX
    }

    "an anchor with no size or outside the window gets no hint" {
        val hidden = Box(left = 100.0, top = 100.0, width = 0.0, height = 0.0)
        val scrolledAway = Box(left = 100.0, top = -200.0, width = 40.0, height = 30.0)

        val placed = place(hint(1, hidden), hint(2, scrolledAway))

        placed shouldNotContainKey 1
        placed shouldNotContainKey 2
    }

    "a row that would wrap out of the window at its bottom wraps upwards, and no line lands on another" {
        val anchors = (0 until 6).map { Box(left = 100.0 + it * 100.0, top = 540.0, width = 90.0, height = 30.0) }
        val placed = place(*anchors.mapIndexed { i, a -> hint(i, a, width = 200.0) }.toTypedArray())
        val boxes = placed.values.toList()

        boxes.indices.forEach { i -> (i + 1 until boxes.size).forEach { j -> overlap(boxes[i], boxes[j]) shouldBe false } }
        boxes.forEach { it.bottom shouldBeLessThanOrEqual (600.0 - MARGIN_PX) }
    }

    "a wrapped row whose hints start at different heights: no line lands on another" {
        val anchors = (0 until 6).map {
            Box(left = 100.0 + it * 100.0, top = if (it % 2 == 0) 300.0 else 306.0, width = 90.0, height = 30.0)
        }
        val placed = place(*anchors.mapIndexed { i, a -> hint(i, a, width = 200.0) }.toTypedArray())
        val boxes = placed.values.toList()

        boxes.indices.forEach { i -> (i + 1 until boxes.size).forEach { j -> overlap(boxes[i], boxes[j]) shouldBe false } }
    }
})
