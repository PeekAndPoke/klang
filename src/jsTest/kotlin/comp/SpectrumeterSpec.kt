/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** The Motor's spectrum (KLANG-00344): where its columns go, which one a pointer is over, a column's label. */
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

    "a column's frequencies read in Hz below 1 kHz, in kHz from 1 kHz on, mixed across it" {
        rangeLabel(20.4..249.6) shouldBe "20 to 250 Hz"
        rangeLabel(1000.0..2549.0) shouldBe "1 to 2.5 kHz"
        rangeLabel(800.0..1249.0) shouldBe "800 Hz to 1.2 kHz"
        // 1 kHz itself already reads in kHz
        rangeLabel(900.0..1000.0) shouldBe "900 Hz to 1 kHz"
    }
})
