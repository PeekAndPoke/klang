/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBuffer
import io.peekandpoke.klang.audio_bridge.analyzer.createAnalyzerBuffer
import org.khronos.webgl.set
import kotlin.math.log10

/** The spectrum's binning: the columns read the same whatever the analyser's FFT size. */
class SpectrumBinningSpec : StringSpec({

    /** A flat spectrum of [bins] bins, every bin at [db]. */
    fun flat(bins: Int, db: Double): AnalyzerBuffer = createAnalyzerBuffer(bins).also { buffer ->
        for (i in 0 until bins) {
            buffer[i] = db.toFloat()
        }
    }

    "the same noise reads the same at 1024 and at 4096 bins, though each of 4096 bins holds a quarter of the energy" {
        val coarse = SpectrumBinning(numBuckets = 28, fftBinCount = 1024)
        val fine = SpectrumBinning(numBuckets = 28, fftBinCount = 4096)

        val coarseOut = DoubleArray(28)
        val fineOut = DoubleArray(28)

        coarse.process(fftBuffer = flat(bins = 1024, db = -40.0), out = coarseOut)
        // A quarter of the energy per bin: 6 dB less
        fine.process(fftBuffer = flat(bins = 4096, db = -40.0 - 10.0 * log10(4.0)), out = fineOut)

        for (i in 0 until 28) {
            fineOut[i] shouldBe (coarseOut[i] plusOrMinus 1e-6)
        }
    }

    "silence stays at the floor at 4096 bins, the resolution correction lifts no column" {
        val fine = SpectrumBinning(numBuckets = 28, fftBinCount = 4096)
        val out = DoubleArray(28) { 1.0 }

        // The Spectrumeter's buffer before audio starts
        fine.process(fftBuffer = flat(bins = 4096, db = -100.0), out = out)

        out.toList() shouldBe List(28) { 0.0 }
    }
})
