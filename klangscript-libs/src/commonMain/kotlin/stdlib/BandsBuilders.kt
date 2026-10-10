/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import kotlin.math.sqrt

// `bands`: a signal split into frequency bands, each band processed on its own, the bands summed again
// (`docs/plans/future/signal-graph-engine.md` §6.9, `docs/tasks/in-progress/parallel-serial-bands.md` step 4). Built on
// `parallel`, on both hosts, from the EQ's existing sections: no DSP of its own.
//
// The crossover is Linkwitz-Riley (Siegfried Linkwitz and Russ Riley, 1976): each side of a cut is two Butterworth
// sections in series, -3 dB each and -6 dB per side at the cut, so the low and the high side sum to flat LEVEL, an
// all-pass. Band k is the high side of every cut below it and the low side of its own upper cut (a split tree). With
// three bands or more a band below a later cut also passes that cut's ALL-PASS, so every band carries the same phase
// turn and the bands still sum to an all-pass. That all-pass is one existing EQ section: a raw tap at the cut, Q
// 1/sqrt(2), gain -2 (`x - 2 * bandpass(x)`, exact for the EQ's linear sections; found in the coding review of step 4).
// An untouched `bands` is therefore flat in level with the phase turned around each cut: the waveform and its peaks
// change, the sound's balance does not.
//
// Read from the bottom up: `b.band(low).cut(200).band(mid).cut(2000).band(high)`. `band()` adds a processor to the
// band being written; two `band()` calls on one band SUM their processors (`parallel`); a band with no `band()` passes
// untouched. A cut below the one before it is coerced up to it (maintainer, 2026-10-09). Two EQUAL cuts leave a narrow
// band between them, not an empty one: the low and the high side of the same cut, about an octave wide, -12 dB at its
// peak, so a processor there still acts on that bump.
//
// A cut is a plain number on both hosts, unlike the other frequency knobs (which take a slot or a signal): the order
// coercion needs the number when the split is written, and moving cuts are out of scope (§6.9). Recorded asymmetry.

/** The Butterworth Q of each section of a Linkwitz-Riley pair: 1/sqrt(2). */
private val BUTTERWORTH_Q: Double = 1.0 / sqrt(2.0)

/**
 * A cut as written, coerced so the bands stay in order: a cut below [previous] is [previous]. A NaN or a negative
 * infinity is [previous] (0 for the first cut), and a positive infinity is "everything below", the largest number,
 * which the engine clamps to just under Nyquist. Out-of-range cuts are the engine's to clamp, as for every filter: its
 * floor is 5 Hz, so a lowest band cut at 0 still holds the content below 5 Hz, DC included.
 */
private fun coerceCut(freq: Double, previous: Double?): Double {
    // NaN-guard on a value the author can write: a NaN cut adds no band edge of its own.
    val written = when {
        freq.isFinite() -> freq
        freq == Double.POSITIVE_INFINITY -> Double.MAX_VALUE
        else -> previous ?: 0.0
    }

    return if (previous != null && written < previous) previous else written
}

/** One Linkwitz-Riley side at [cut]: two Butterworth lowpass sections, or two highpass ones. */
private fun lrSide(cut: Double, low: Boolean): List<IgnitorDsl.EqSection> = List(2) {
    if (low) {
        IgnitorDsl.EqSection.Lowpass(freq = IgnitorDsl.Constant(cut), q = IgnitorDsl.Constant(BUTTERWORTH_Q))
    } else {
        IgnitorDsl.EqSection.Highpass(freq = IgnitorDsl.Constant(cut), q = IgnitorDsl.Constant(BUTTERWORTH_Q))
    }
}

/**
 * The all-pass of a Linkwitz-Riley cut, the sum of its two sides, as one section: `x - 2 * bandpass(x)` at the cut with
 * Q 1/sqrt(2). A tap reads its EQ's input, so it must sit in an EQ of its own.
 */
private fun lrAllPass(cut: Double): IgnitorDsl.EqSection =
    IgnitorDsl.EqSection.RawTap(freq = IgnitorDsl.Constant(cut), q = IgnitorDsl.Constant(BUTTERWORTH_Q), gain = IgnitorDsl.Constant(-2.0))

/** The cuts above band [k]'s own upper edge: their all-passes align the band's phase with the bands above. */
private fun allPassCuts(k: Int, cuts: List<Double>): List<Double> = if (k + 1 < cuts.size) cuts.subList(k + 1, cuts.size) else emptyList()

// ── Ignitor ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Builder for `bands` on the Ignitor, handed to its `configure` lambda. Knobs: `band` (a processor for the band being
 * written) and `cut` (the edge to the next band up). Immutable: every knob returns a new builder. Made by the door
 * only, so its two lists always agree (one more band than cuts, the cuts ascending).
 */
@ConsistentCopyVisibility
data class IgnitorBandsBuilder internal constructor(
    /** The processors per band, lowest band first. */
    val processors: List<List<(IgnitorDsl) -> IgnitorDsl>> = listOf(emptyList()),
    /** The cuts between the bands, in Hz, ascending (coerced). */
    val cuts: List<Double> = emptyList(),
)

/**
 * Adds [processor] to the band being written. Two `band()` calls on one band SUM their processors, as `parallel` does;
 * a band with no `band()` passes untouched.
 *
 * @param processor a function from the band's signal to a signal.
 */
@KlangScript.Function
fun IgnitorBandsBuilder.band(processor: (IgnitorDsl) -> IgnitorDsl): IgnitorBandsBuilder =
    copy(processors = processors.dropLast(1) + listOf(processors.last() + processor))

/**
 * Closes the band being written at [freq] and starts the next one up. A cut below the one before it is moved up to it.
 * A plain number: the crossover does not move.
 *
 * @param freq the crossover frequency in Hz.
 */
@KlangScript.Function
fun IgnitorBandsBuilder.cut(freq: Double): IgnitorBandsBuilder =
    copy(processors = processors + listOf(emptyList()), cuts = cuts + coerceCut(freq, cuts.lastOrNull()))

/**
 * The split of [signal] as a tree: the low side of the lowest cut is band 1, its high side (`rest`) is split again at
 * the next cut, and so on, each high side ONE node shared by identity between the band below and the split above it, so
 * the build renders it once: the crossover's sections grow with the number of cuts (4 per cut), not its square (audio
 * review of step 4). The all-passes still grow with the square, one per band per cut above it ((N-1)(N-2)/2 for N
 * bands, 3 at 4 bands, 21 at 8), one section each. The processed bands are summed.
 */
internal fun IgnitorBandsBuilder.split(signal: IgnitorDsl): IgnitorDsl {
    val bandCount = processors.size
    var rest = signal

    val bands = List(bandCount) { k ->
        var band: IgnitorDsl = if (k < bandCount - 1) IgnitorDsl.Eq(inner = rest, sections = lrSide(cut = cuts[k], low = true)) else rest

        if (k < bandCount - 1) {
            rest = IgnitorDsl.Eq(inner = rest, sections = lrSide(cut = cuts[k], low = false))
        }

        for (cut in allPassCuts(k = k, cuts = cuts)) {
            band = IgnitorDsl.Eq(inner = band, sections = listOf(lrAllPass(cut)))
        }

        if (processors[k].isEmpty()) {
            band
        } else {
            // The processors get the band behind an EQ with no section of its own: `eq()` CONTINUES an EQ it is called
            // on, and a tap reads its EQ's input, so a tap written in a band would otherwise land in the crossover's
            // EQ and read the unsplit signal (coding review of step 4). Continuing this empty one reads the band. The one
            // band of a split without a cut is the signal itself, the author's own, and is handed on as it is.
            val input = if (band === signal) band else IgnitorDsl.Eq(inner = band)

            val processed = processors[k].mapIndexed { index, processor ->
                runStage<IgnitorDsl, IgnitorDsl>(
                    door = "band ${k + 1} of Ignitor bands",
                    noun = "processor",
                    index = index,
                    stage = processor,
                    input = input,
                    returns = "signal",
                    example = "x => x.distort(0.5)",
                    isResult = { it is IgnitorDsl },
                )
            }

            if (processed.size == 1) processed[0] else IgnitorDsl.Parallel(branches = processed)
        }
    }

    return if (bands.size == 1) bands[0] else IgnitorDsl.Parallel(branches = bands)
}

// ── Katalyst ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Builder for `bands` on the Katalyst, handed to its `configure` lambda. Knobs: `band` (a processor for the band being
 * written, a function of stages from an empty builder, as a `parallel` branch) and `cut`. Immutable; made by the door
 * only, as [IgnitorBandsBuilder].
 */
@ConsistentCopyVisibility
data class KatalystBandsBuilder internal constructor(
    /** The processors per band, lowest band first. */
    val processors: List<List<(KatalystBuilder) -> KatalystBuilder>> = listOf(emptyList()),
    /** The cuts between the bands, in Hz, ascending (coerced). */
    val cuts: List<Double> = emptyList(),
)

/**
 * Adds [processor] to the band being written: a function of stages that receives an empty builder, the band at this
 * point, as a `parallel` branch does. Two `band()` calls on one band SUM their processors; a band with no `band()`
 * passes untouched.
 *
 * @param processor a function from a builder to a builder.
 */
@KlangScript.Function
fun KatalystBandsBuilder.band(processor: (KatalystBuilder) -> KatalystBuilder): KatalystBandsBuilder =
    copy(processors = processors.dropLast(1) + listOf(processors.last() + processor))

/**
 * Closes the band being written at [freq] and starts the next one up. A cut below the one before it is moved up to it.
 * A plain number: the crossover does not move.
 *
 * @param freq the crossover frequency in Hz.
 */
@KlangScript.Function
fun KatalystBandsBuilder.cut(freq: Double): KatalystBandsBuilder =
    copy(processors = processors + listOf(emptyList()), cuts = cuts + coerceCut(freq, cuts.lastOrNull()))

/**
 * The split as stages: one `Parallel` of the bands, or the one band's stages in place. A bus band is a chain of its own
 * (stages cannot share a prefix by identity, as the voice's split tree does), so band k carries the high side of every
 * cut below it itself; the bus cost is small next to a voice's.
 */
internal fun KatalystBandsBuilder.split(): List<KatalystStageDsl> {
    val bandCount = processors.size

    val bands = List(bandCount) { k ->
        val stages = mutableListOf<KatalystStageDsl>()
        val sections = buildList {
            for (j in 0 until k) {
                addAll(lrSide(cut = cuts[j], low = false))
            }

            if (k < bandCount - 1) {
                addAll(lrSide(cut = cuts[k], low = true))
            }
        }

        if (sections.isNotEmpty()) {
            stages += KatalystStageDsl.Eq(sections = sections)
        }

        for (cut in allPassCuts(k = k, cuts = cuts)) {
            stages += KatalystStageDsl.Eq(sections = listOf(lrAllPass(cut)))
        }

        val processed = processors[k].mapIndexed { index, processor ->
            runStage<KatalystBuilder, KatalystBuilder>(
                door = "band ${k + 1} of Katalyst bands",
                noun = "processor",
                index = index,
                stage = processor,
                input = KatalystBuilder(KatalystDsl(emptyList())),
                returns = "builder",
                example = "b => b.distort(0.5)",
                isResult = { it is KatalystBuilder },
            ).node
        }

        when (processed.size) {
            0 -> Unit
            1 -> stages += processed[0].stages
            else -> stages += KatalystStageDsl.Parallel(branches = processed)
        }

        KatalystDsl(stages)
    }

    return if (bands.size == 1) bands[0].stages else listOf(KatalystStageDsl.Parallel(branches = bands))
}
