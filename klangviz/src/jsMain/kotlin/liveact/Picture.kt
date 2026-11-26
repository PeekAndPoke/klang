/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.LookFlip
import io.peekandpoke.klang.viz.liveact.stage.LookVariants
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * The live act's picture, seen and set from outside it: hand one to [LiveAct] (`picture`). It tells the oversampling
 * ([samples]) whenever it changes (at its start, by the key o, by its own step down), takes what is asked with
 * [choose], and tells the frame rate ([fps]) the live act counts for its line at the top, each half second while the
 * page is seen. The oversampling steps are [STEPS], the samples drawn for each pixel, as the key o cycles through
 * them. The fog on the floor likewise: it tells the [fog]'s quality whenever it changes (at its start, by the key g)
 * and takes what is asked with [chooseFog]; the lamps' [shadows], taken with [chooseShadows]; and the mirror floor's
 * [reflections], taken with [chooseReflections]. The look ([look],
 * its variants and which side of the key x is shown) whenever it changes (at its start, by a variant's key, by x),
 * taken with [chooseLook] and [flipLook] as the keys take it, with the line at the top that says it. A choice through
 * the handle is taken at once. It also tells how far the
 * band's figures have loaded ([figures]). For an app's controls, its loading screen and its picture test (notstrom's,
 * KLANG-00465); one handle for one live act at a time.
 */
class Picture {

    private val samplesSource = StreamSource(Oversampling.PLAIN)
    private val fogSource = StreamSource(FogQuality.High)
    private val shadowsSource = StreamSource(ShadowQuality.High)
    private val reflectionsSource = StreamSource(ReflectionQuality.Mid)
    private val fpsSource = StreamSource(0.0)
    private val figuresSource = StreamSource(Figures(arrived = 0, of = 0, failed = false))
    private val lookSource = StreamSource(LookFlip.of(Switches()))

    /** How far the band's figures have loaded: [arrived] of [of] files; [failed] where one could not be loaded. */
    data class Figures(val arrived: Int, val of: Int, val failed: Boolean) {
        /** All of them are there (and none failed). */
        val done: Boolean get() = !failed && of > 0 && arrived >= of
    }

    /** Who takes a choice of the oversampling: the live act this handle was given to, while it is mounted. */
    private var taker: ((Double) -> Unit)? = null

    /** Who takes a choice of the fog: the same live act. */
    private var fogTaker: ((FogQuality) -> Unit)? = null

    /** Who takes a choice of the shadows: the same live act. */
    private var shadowsTaker: ((ShadowQuality) -> Unit)? = null

    /** Who takes a choice of the reflections: the same live act. */
    private var reflectionsTaker: ((ReflectionQuality) -> Unit)? = null

    /** Who takes a choice of the look's variants: the same live act. */
    private var lookTaker: ((LookVariants) -> Unit)? = null

    /** Who flips the whole look: the same live act. */
    private var lookFlipper: (() -> Unit)? = null

    /** Who turns the front lamp on by hand: the same live act. */
    private var frontTrier: (() -> Unit)? = null

    /** The samples drawn for each pixel now, as the live act draws them; 1 until one tells. */
    val samples: Stream<Double> = samplesSource.readonly

    /** The fog's quality now, as the live act draws it; high until one tells. */
    val fog: Stream<FogQuality> = fogSource.readonly

    /** The shadows' quality now, as the live act draws them; high until one tells. */
    val shadows: Stream<ShadowQuality> = shadowsSource.readonly

    /** How the mirror floor's reflections are drawn now, as the live act draws them; mid until one tells. */
    val reflections: Stream<ReflectionQuality> = reflectionsSource.readonly

    /** The frames a second of the last half second counted; 0 until one tells. */
    val fps: Stream<Double> = fpsSource.readonly

    /** How far the band's figures have loaded; none of none until the live act begins to load them. */
    val figures: Stream<Figures> = figuresSource.readonly

    /** The look now, as the live act shows it ([LookFlip.shown]) and keeps for the key x; today's until one tells. */
    val look: Stream<LookFlip> = lookSource.readonly

    /**
     * Asks the live act for [samples] for each pixel (one of [STEPS]; another is not taken). A choice by hand, as the
     * key o is: the live act steps down by itself no more in that show. Nothing happens while no live act has it.
     */
    fun choose(samples: Double) {
        if (samples in STEPS) {
            taker?.invoke(samples)
        }
    }

    /**
     * Asks the live act for the fog drawn at [quality]: the old fog goes, with all it holds on the graphics card, and
     * the new one is made (none for off). Nothing happens while no live act has it.
     */
    fun chooseFog(quality: FogQuality) {
        fogTaker?.invoke(quality)
    }

    /**
     * Asks the live act for the lamps' shadows at [quality]: maps of another size are made anew, and where the number
     * of lamps that cast changes, every lit material is built anew once (a short hitch). Nothing happens while no live
     * act has it.
     */
    fun chooseShadows(quality: ShadowQuality) {
        shadowsTaker?.invoke(quality)
    }

    /**
     * Asks the live act for the reflections at [quality]: off, the mirror floor is drawn as the plain floor and what
     * its pictures hold on the graphics card is freed; low, mid or high, it is drawn (again) where the look has it, as
     * finely as the step says. Nothing happens while no live act has it.
     */
    fun chooseReflections(quality: ReflectionQuality) {
        reflectionsTaker?.invoke(quality)
    }

    /**
     * Asks the live act to show [variants]: each variant that differs is turned as its own key turns it (the key m for
     * the stage, ...), on the side of the key x shown now, and the line at the top says it for a moment. Nothing
     * happens while no live act has it.
     */
    fun chooseLook(variants: LookVariants) {
        lookTaker?.invoke(variants)
    }

    /** Asks the live act to flip the whole look, as the key x does. Nothing happens while no live act has it. */
    fun flipLook() {
        lookFlipper?.invoke()
    }

    /**
     * Asks the live act for the front lamp by hand, as the key f does: full on in the next colour to try, after the
     * last back to the show's own. Nothing happens while no live act has it.
     */
    fun tryFront() {
        frontTrier?.invoke()
    }

    /**
     * The live act takes the choices from now on: [take] the oversampling's, [takeFog] the fog's, [takeShadows] the
     * shadows', [takeReflections] the reflections', [takeLook] the look's variants, [flipLook] flips the whole look, and
     * [tryFront] turns the front lamp on by hand.
     */
    internal fun attach(
        take: (Double) -> Unit,
        takeFog: (FogQuality) -> Unit,
        takeShadows: (ShadowQuality) -> Unit,
        takeReflections: (ReflectionQuality) -> Unit,
        takeLook: (LookVariants) -> Unit,
        flipLook: () -> Unit,
        tryFront: () -> Unit,
    ) {
        taker = take
        fogTaker = takeFog
        shadowsTaker = takeShadows
        reflectionsTaker = takeReflections
        lookTaker = takeLook
        lookFlipper = flipLook
        frontTrier = tryFront
    }

    /** The live act is gone. */
    internal fun detach() {
        taker = null
        fogTaker = null
        shadowsTaker = null
        reflectionsTaker = null
        lookTaker = null
        lookFlipper = null
        frontTrier = null
    }

    /** The live act tells the look it shows now. */
    internal fun tellLook(look: LookFlip) {
        lookSource(look)
    }

    /** The live act tells what it draws now. */
    internal fun tellSamples(samples: Double) {
        samplesSource(samples)
    }

    /** The live act tells the fog it draws now. */
    internal fun tellFog(quality: FogQuality) {
        fogSource(quality)
    }

    /** The live act tells the shadows it draws now. */
    internal fun tellShadows(quality: ShadowQuality) {
        shadowsSource(quality)
    }

    /** The live act tells whether it draws the reflections now. */
    internal fun tellReflections(quality: ReflectionQuality) {
        reflectionsSource(quality)
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
        /** The oversampling steps: the samples drawn for each pixel ([Oversampling.STEPS]). */
        val STEPS: List<Double> = Oversampling.STEPS
    }
}
