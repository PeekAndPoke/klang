/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

// The block-framing checker (KLANG-00420, probe 4; the property is KLANG-00156's): does a note sound the same wherever
// the block borders fall? A measurement, never a gate: these functions assert nothing about an engine stage; a spec
// that guards a fixed finding calls [measureFraming] with a threshold of its own.

/** Every offset inside the 128-frame block. */
val FRAMING_ALL_OFFSETS: List<Int> = (0 until PROBE_BLOCK_FRAMES).toList()

/** The short list: the reference, the first frames, both sides of the middle and the last frame. */
val FRAMING_SHORT_OFFSETS: List<Int> = listOf(0, 1, 2, 31, 63, 64, 65, 76, 127)

/** The class of a difference [maxAbsDiff] against a reference of peak [referencePeak] ([FramingClass]). */
fun framingClassOf(maxAbsDiff: Double, referencePeak: Double): FramingClass = when {
    maxAbsDiff == 0.0 -> FramingClass.IDENTICAL
    referencePeak > 0.0 && maxAbsDiff <= FRAMING_ROUNDING_REL * referencePeak -> FramingClass.ROUNDING
    referencePeak > 0.0 && maxAbsDiff <= FRAMING_SMALL_REL * referencePeak -> FramingClass.SMALL
    else -> FramingClass.AUDIBLE
}

/** The region of a first difference at note frame [first] for an event at [eventFrame] ([FramingRegion]). */
fun framingRegionOf(first: Int, eventFrame: Int, blockFrames: Int = PROBE_BLOCK_FRAMES): FramingRegion = when {
    first == NEVER -> FramingRegion.NONE
    first < blockFrames -> FramingRegion.ONSET
    first >= eventFrame - blockFrames && first < eventFrame + blockFrames -> FramingRegion.TRANSITION
    first < eventFrame - blockFrames -> FramingRegion.HELD
    else -> FramingRegion.RELEASE
}

/** The head window `[start, end)` of [region] for a first difference at [first] ([FramingExtent]). */
private fun headWindow(region: FramingRegion, first: Int, eventFrame: Int, blockFrames: Int): IntRange = when (region) {
    FramingRegion.NONE -> IntRange.EMPTY
    FramingRegion.ONSET -> 0 until blockFrames
    FramingRegion.TRANSITION -> (eventFrame - blockFrames) until (eventFrame + blockFrames)
    FramingRegion.HELD, FramingRegion.RELEASE -> first until (first + blockFrames)
}

/** The largest |x| of [output] (both channels) before absolute frame [onset]. */
private fun peakBefore(output: FramedOutput, onset: Int): Double {
    var peak = 0.0

    for (n in 0 until minOf(onset, output.left.size)) {
        peak = max(peak, abs(output.left[n]))
        output.right?.let { peak = max(peak, abs(it[n])) }
    }

    return peak
}

/** |a - b| where a NaN or an infinity on one side only reads +Inf, and two equal values (infinities included) read 0. */
private fun sampleDiff(a: Double, b: Double): Double {
    if (a == b) {
        return 0.0
    }

    val d = abs(a - b)

    return if (d != d) Double.POSITIVE_INFINITY else d // NaN-guard
}

/**
 * Compares [test] (its note starting at absolute frame [testOnset]) with [reference] (onset [referenceOnset]) over
 * [spanFrames] note frames, aligned by the onset: note frame n is `test[testOnset + n]` against
 * `reference[referenceOnset + n]`, on every channel both have. [eventFrame] is the sweep's event (note-relative), the
 * anchor of the [FramingRegion.TRANSITION] window; [offset] only labels the row.
 */
fun compareFraming(
    reference: FramedOutput,
    referenceOnset: Int,
    test: FramedOutput,
    testOnset: Int,
    spanFrames: Int,
    eventFrame: Int,
    offset: Int,
    blockFrames: Int = PROBE_BLOCK_FRAMES,
): FramingReading {
    require(referenceOnset + spanFrames <= reference.left.size) { "the reference ends before the span" }
    require(testOnset + spanFrames <= test.left.size) { "the test render ends before the span" }

    val refChannels = listOfNotNull(reference.left, reference.right?.takeIf { test.right != null })
    val testChannels = listOfNotNull(test.left, test.right?.takeIf { reference.right != null })
    // Per note frame, the larger |difference| of the channels: positions and extents are read off it.
    val diff = DoubleArray(spanFrames)
    var sumDiff2 = 0.0
    var sumRef2 = 0.0
    var refPeak = 0.0

    for (c in refChannels.indices) {
        val r = refChannels[c]
        val t = testChannels[c]

        for (n in 0 until spanFrames) {
            val rv = r[referenceOnset + n]
            val d = sampleDiff(t[testOnset + n], rv)

            diff[n] = max(diff[n], d)
            sumDiff2 += d * d
            sumRef2 += rv * rv
            refPeak = max(refPeak, abs(rv))
        }
    }

    val samples = (spanFrames * refChannels.size).toDouble()
    var maxAbs = 0.0
    var maxFrame = NEVER
    var first = NEVER
    var last = NEVER
    var count = 0

    for (n in 0 until spanFrames) {
        val d = diff[n]

        if (d > 0.0) {
            if (first == NEVER) {
                first = n
            }

            last = n
            count++
        }

        if (d > maxAbs) {
            maxAbs = d
            maxFrame = n
        }
    }

    val region = framingRegionOf(first = first, eventFrame = eventFrame, blockFrames = blockFrames)
    val head = headWindow(region = region, first = first, eventFrame = eventFrame, blockFrames = blockFrames)
    var headMax = 0.0
    var headSum2 = 0.0
    var headCount = 0
    var restMax = 0.0
    var restSum2 = 0.0
    var restCount = 0

    if (first != NEVER) {
        for (n in maxOf(0, head.first)..minOf(spanFrames - 1, head.last)) {
            headMax = max(headMax, diff[n])
            headSum2 += diff[n] * diff[n]
            headCount++
        }

        for (n in (head.last + 1)..last) {
            restMax = max(restMax, diff[n])
            restSum2 += diff[n] * diff[n]
            restCount++
        }
    }

    val persistDb = if (restMax > 0.0 && headSum2 > 0.0) {
        20.0 * log10(sqrt(restSum2 / restCount) / sqrt(headSum2 / headCount))
    } else if (restMax > 0.0) {
        Double.POSITIVE_INFINITY
    } else {
        Double.NEGATIVE_INFINITY
    }
    val extent = when {
        first == NEVER -> FramingExtent.NONE
        restMax == 0.0 -> FramingExtent.CONFINED
        persistDb < FRAMING_SPILL_DB -> FramingExtent.SPILLS
        else -> FramingExtent.PERSISTS
    }
    var lateMax = 0.0

    for (n in maxOf(0, eventFrame - (1 + FRAMING_LATE_BLOCKS) * blockFrames) until minOf(spanFrames, maxOf(0, eventFrame - blockFrames))) {
        lateMax = max(lateMax, diff[n])
    }

    val refRms = sqrt(sumRef2 / samples)
    val rmsRelDb = when {
        sumDiff2 == 0.0 -> Double.NEGATIVE_INFINITY
        sumRef2 > 0.0 -> 10.0 * log10(sumDiff2 / sumRef2)
        else -> Double.POSITIVE_INFINITY
    }
    val rngSame = if (reference.rngFingerprint != null && test.rngFingerprint != null) reference.rngFingerprint == test.rngFingerprint else null

    return FramingReading(
        offset = offset,
        referenceOnset = referenceOnset,
        testOnset = testOnset,
        eventFrame = eventFrame,
        spanFrames = spanFrames,
        referencePeak = refPeak,
        referenceRms = refRms,
        maxAbsDiff = maxAbs,
        maxDiffFrame = maxFrame,
        rmsRelDb = rmsRelDb,
        firstDiffFrame = first,
        lastDiffFrame = last,
        diffFrames = count,
        region = region,
        extent = extent,
        headMaxAbs = headMax,
        restMaxAbs = restMax,
        persistDb = persistDb,
        lateMaxAbs = lateMax,
        preOnsetPeak = max(peakBefore(output = reference, onset = referenceOnset), peakBefore(output = test, onset = testOnset)),
        rngSame = rngSame,
        cls = framingClassOf(maxAbsDiff = maxAbs, referencePeak = refPeak),
    )
}

/** The onset of the reference that puts a border-relative event at offset [offset] exactly on a block border. */
fun framingBorderOnset(offset: Int, blockFrames: Int = PROBE_BLOCK_FRAMES): Int = (blockFrames - offset % blockFrames) % blockFrames

/**
 * Sweeps [renderer] over [offsets] in the way [sweep] names ([FramingSweep]) and compares every render with its
 * reference ([compareFraming]); one reading per offset, in the order given.
 *
 * [FramingSweep.ONSET] renders the reference once (onset 0) and every offset afresh, offset 0 included, so a renderer
 * that is not deterministic shows at offset 0. [FramingSweep.GATE] and [FramingSweep.CUT] render a reference per
 * offset, the same note started at [framingBorderOnset] (offset 0: both renders start at 0).
 */
fun measureFraming(
    renderer: FramingRenderer,
    sweep: FramingSweep,
    offsets: List<Int> = FRAMING_ALL_OFFSETS,
    config: FramingConfig = FramingConfig(),
): FramingResult {
    val span = config.spanFrames(sweep)
    val frames = config.renderFrames(sweep)
    val b = config.blockFrames

    val readings = when (sweep) {
        FramingSweep.ONSET -> {
            val reference = renderer.render(FramingNote(onsetFrame = 0, gateFrames = config.gateFrames, cutFrame = FRAMING_NONE, renderFrames = frames))

            offsets.map { k ->
                val test = renderer.render(FramingNote(onsetFrame = k, gateFrames = config.gateFrames, cutFrame = FRAMING_NONE, renderFrames = frames))

                compareFraming(
                    reference = reference, referenceOnset = 0, test = test, testOnset = k,
                    spanFrames = span, eventFrame = config.gateFrames, offset = k, blockFrames = b,
                )
            }
        }

        FramingSweep.GATE -> offsets.map { k ->
            val gate = config.gateBaseFrames + k
            val refOnset = framingBorderOnset(offset = k, blockFrames = b)
            val test = renderer.render(FramingNote(onsetFrame = 0, gateFrames = gate, cutFrame = FRAMING_NONE, renderFrames = frames))
            val reference = renderer.render(FramingNote(onsetFrame = refOnset, gateFrames = gate, cutFrame = FRAMING_NONE, renderFrames = frames))

            compareFraming(
                reference = reference, referenceOnset = refOnset, test = test, testOnset = 0,
                spanFrames = span, eventFrame = gate, offset = k, blockFrames = b,
            )
        }

        FramingSweep.CUT -> offsets.map { k ->
            val cut = config.cutBaseFrames + k
            val refOnset = framingBorderOnset(offset = k, blockFrames = b)
            val test = renderer.render(FramingNote(onsetFrame = 0, gateFrames = config.cutGateFrames, cutFrame = cut, renderFrames = frames))
            val reference = renderer.render(FramingNote(onsetFrame = refOnset, gateFrames = config.cutGateFrames, cutFrame = cut, renderFrames = frames))

            compareFraming(
                reference = reference, referenceOnset = refOnset, test = test, testOnset = 0,
                spanFrames = span, eventFrame = cut, offset = k, blockFrames = b,
            )
        }
    }

    return FramingResult(sweep = sweep, readings = readings)
}
