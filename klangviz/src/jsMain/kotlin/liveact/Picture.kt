/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * The live act's picture, seen and set from outside it: hand one to [LiveAct] (`picture`). It tells the oversampling
 * ([samples]) whenever it changes (at its start, by the key o, by its own step down), takes what is asked with
 * [choose], and tells the frame rate ([fps]) the live act counts for its line at the top, each half second while the
 * page is seen. The oversampling steps are [STEPS], the samples drawn for each pixel, as the key o cycles through
 * them. It also tells how far the band's figures have loaded ([figures]). For an app's controls, its loading screen
 * and its picture test (notstrom's, KLANG-00465); one handle for one live act at a time.
 */
class Picture {

    private val samplesSource = StreamSource(1)
    private val fpsSource = StreamSource(0.0)
    private val figuresSource = StreamSource(Figures(arrived = 0, of = 0, failed = false))

    /** How far the band's figures have loaded: [arrived] of [of] files; [failed] where one could not be loaded. */
    data class Figures(val arrived: Int, val of: Int, val failed: Boolean) {
        /** All of them are there (and none failed). */
        val done: Boolean get() = !failed && of > 0 && arrived >= of
    }

    /** Who takes a choice: the live act this handle was given to, while it is mounted. */
    private var taker: ((Int) -> Unit)? = null

    /** The samples drawn for each pixel now, as the live act draws them; 1 until one tells. */
    val samples: Stream<Int> = samplesSource.readonly

    /** The frames a second of the last half second counted; 0 until one tells. */
    val fps: Stream<Double> = fpsSource.readonly

    /** How far the band's figures have loaded; none of none until the live act begins to load them. */
    val figures: Stream<Figures> = figuresSource.readonly

    /**
     * Asks the live act for [samples] for each pixel (one of [STEPS]; another is not taken). A choice by hand, as the
     * key o is: the live act steps down by itself no more in that show. Nothing happens while no live act has it.
     */
    fun choose(samples: Int) {
        if (samples in STEPS) {
            taker?.invoke(samples)
        }
    }

    /** The live act takes the choices from now on. */
    internal fun attach(take: (Int) -> Unit) {
        taker = take
    }

    /** The live act is gone. */
    internal fun detach() {
        taker = null
    }

    /** The live act tells what it draws now. */
    internal fun tellSamples(samples: Int) {
        samplesSource(samples)
    }

    /** The live act tells the frame rate it counted. */
    internal fun tellFps(fps: Double) {
        fpsSource(fps)
    }

    /** The live act tells how far the figures have loaded. */
    internal fun tellFigures(figures: Figures) {
        figuresSource(figures)
    }

    companion object {
        /** The oversampling steps: the samples drawn for each pixel. */
        val STEPS = listOf(1, 2, 4)
    }
}
