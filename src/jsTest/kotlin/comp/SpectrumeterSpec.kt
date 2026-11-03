/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.css.Color

/**
 * The Motor's spectrum (KLANG-00344): where its columns go, which one a pointer is over, a column's label, each cell's
 * colour.
 */
class SpectrumeterSpec : StringSpec({

    "the columns are whole pixels wide, and the pixels left over go half to either side" {
        // 28 columns of 3 pixels in 100 leave 16: 8 on the left
        spectrumColumns(width = 100, count = 28) shouldBe SpectrumColumns(step = 3, left = 8)
        // 17 left over: the odd pixel goes to the right
        spectrumColumns(width = 101, count = 28) shouldBe SpectrumColumns(step = 3, left = 8)
        // Nothing left over
        spectrumColumns(width = 280, count = 28) shouldBe SpectrumColumns(step = 10, left = 0)
    }

    "a pointer over a column names it, one over the padding on either side names none" {
        val columns = spectrumColumns(width = 100, count = 28)

        columns.columnAt(x = 7.9, count = 28) shouldBe null
        columns.columnAt(x = 8.0, count = 28) shouldBe 0
        columns.columnAt(x = 10.9, count = 28) shouldBe 0
        columns.columnAt(x = 11.0, count = 28) shouldBe 1
        columns.columnAt(x = 91.9, count = 28) shouldBe 27
        columns.columnAt(x = 92.0, count = 28) shouldBe null

        columns.xOf(27) shouldBe 89
    }

    "without room for a column, or without columns, a pointer names none" {
        spectrumColumns(width = 10, count = 28).columnAt(x = 5.0, count = 28) shouldBe null
        spectrumColumns(width = 100, count = 0).columnAt(x = 50.0, count = 0) shouldBe null
        spectrumColumns(width = -5, count = 28) shouldBe SpectrumColumns(step = 0, left = 0)
    }

    "without a blend each band keeps its colour, the last one on to the top" {
        val blue = Color("#000064")
        val red = Color("#c80000")

        spectrumCellColors(colors = listOf(blue, red), cells = 5, cellsPerColor = 2, blend = 0.0)
            .map { it.value } shouldContainExactly listOf("#000064", "#000064", "#c80000", "#c80000", "#c80000")
    }

    "a blend fades a band's cells towards the next band's colour, and the top band stays as it is" {
        val colors = listOf(Color("#000064"), Color("#c80000"), Color("#ffffff"))

        // Four cells a band. The lowest: its floor cell a shade darker, its colour above it, then a third and two
        // thirds of the way to the red. The red's cells a quarter further towards the white each. Mixed in OKLab, so
        // the blue's way to the red runs through a warm purple, not a plain RGB mix
        spectrumCellColors(colors = colors, cells = 10, cellsPerColor = 4, blend = 1.0)
            .map { it.value } shouldContainExactly listOf(
            "#00004f",
            "#000064",
            "#402657",
            "#822b41",
            "#c80000",
            "#dd5f50",
            "#ee978a",
            "#f9cbc4",
            "#ffffff",
            "#ffffff",
        )
    }

    "short hex and hex with an alpha come out opaque, the draw sets the alpha; a colour not in hex is not mixed" {
        // kotlin-css's withAlpha cannot read #rgba: a look may give it, and the draw threw on it
        spectrumCellColors(colors = listOf(Color("#00f8"), Color("#f00")), cells = 3, cellsPerColor = 3, blend = 1.0)
            .map { it.value } shouldContainExactly listOf("#0000ce", "#0000ff", "#8c53a2")

        spectrumCellColors(colors = listOf(Color("#abc8")), cells = 1, cellsPerColor = 1, blend = 0.0)
            .map { it.value } shouldContainExactly listOf("#aabbcc")

        opaqueHex(Color("#11223344")).value shouldBe "#112233"

        spectrumCellColors(colors = listOf(Color("blue"), Color("#f00")), cells = 3, cellsPerColor = 3, blend = 1.0)
            .map { it.value } shouldContainExactly listOf("blue", "blue", "blue")
    }

    "half a blend fades and shades half as far, and a blend that is not a number counts as none" {
        val colors = listOf(Color("#000064"), Color("#c80000"))

        spectrumCellColors(colors = colors, cells = 3, cellsPerColor = 3, blend = 0.5)
            .map { it.value } shouldContainExactly listOf("#000059", "#000064", "#30225a")

        spectrumCellColors(colors = colors, cells = 3, cellsPerColor = 3, blend = Double.NaN)
            .map { it.value } shouldContainExactly listOf("#000064", "#000064", "#000064")
    }

    "a column's frequencies read in Hz below 1 kHz, in kHz from 1 kHz on, mixed across it" {
        rangeLabel(20.4..249.6) shouldBe "20 to 250 Hz"
        rangeLabel(1000.0..2549.0) shouldBe "1 to 2.5 kHz"
        rangeLabel(800.0..1249.0) shouldBe "800 Hz to 1.2 kHz"
        // 1 kHz itself already reads in kHz
        rangeLabel(900.0..1000.0) shouldBe "900 Hz to 1 kHz"
    }
})
