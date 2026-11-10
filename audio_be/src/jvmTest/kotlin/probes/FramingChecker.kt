/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import kotlin.math.log10

// The block-framing checker's data (KLANG-00420, probe 4; the property is KLANG-00156's): the same note rendered with
// its onset, its gate close or its cut at another frame inside the 128-frame block, aligned by the onset and compared.

/** A note frame that does not apply: no cut. */
const val FRAMING_NONE: Int = -1

/**
 * Which border moves inside the block (KLANG-00156's invariants I1 to I4).
 *
 * - [ONSET]: the note (fixed gate) starts at offset k; the reference starts at 0. The gate close moves with it, so
 *   both failure modes show: (a) a wrong clock in the first block, (b) a transition decided per block.
 * - [GATE]: the onset stays on a block border and the gate closes at offset k ([FramingConfig.gateBaseFrames] + k);
 *   the reference is the SAME note started at `(128 - k) mod 128`, so its gate closes exactly on a border, where a
 *   transition decided per block is right. A difference starting at the gate is failure mode (b) in the test render.
 * - [CUT]: as [GATE] for a cut (`Voice.cutOff`) at [FramingConfig.cutBaseFrames] + k after the onset.
 */
enum class FramingSweep {
    ONSET,
    GATE,
    CUT,
}

/**
 * One render of a framing sweep: the note starts at absolute frame [onsetFrame] (the render starts at frame 0, on a
 * block border), its gate is [gateFrames] long, it is cut [cutFrame] frames after its onset ([FRAMING_NONE]: never),
 * and at least [renderFrames] frames are rendered.
 */
class FramingNote(
    val onsetFrame: Int,
    val gateFrames: Int,
    val cutFrame: Int,
    val renderFrames: Int,
)

/**
 * What a renderer put out for one [FramingNote], in ABSOLUTE frames from frame 0: [left], and [right] for a stereo
 * output (null for mono). [rngFingerprint] is the next draw of the voice's own random stream once the voice is done
 * (null when the renderer cannot read it or the voice outlived the render): two renders that drew the same number of
 * times from one seed show the same value.
 */
class FramedOutput(
    val left: DoubleArray,
    val right: DoubleArray?,
    val rngFingerprint: Int?,
)

/**
 * Renders one [FramingNote] in [PROBE_BLOCK_FRAMES] blocks from frame 0, from a FRESH instance every call (the same
 * seed every call, so two calls differ only by where the borders fall). Test code, never the engine's hot path.
 */
fun interface FramingRenderer {
    fun render(note: FramingNote): FramedOutput
}

/**
 * The frames of a sweep (KLANG-00420, probe 4). Every length is chosen so a border lands mid-block where it should:
 * [gateFrames] is 70 blocks and 41 frames, [gateBaseFrames] and [cutBaseFrames] are whole blocks (offset 0 is the
 * border itself). The compared span runs from the onset to [tailFrames] after the latest event of the sweep.
 */
data class FramingConfig(
    val blockFrames: Int = PROBE_BLOCK_FRAMES,
    /** The gate of the [FramingSweep.ONSET] sweep: 9001 frames, 0.1875 s at 48 kHz, closing at frame 41 of its block. */
    val gateFrames: Int = 9001,
    /** The [FramingSweep.GATE] sweep's gate at offset 0: 71 whole blocks. */
    val gateBaseFrames: Int = 71 * PROBE_BLOCK_FRAMES,
    /** The [FramingSweep.CUT] sweep's cut at offset 0: 32 whole blocks after the onset. */
    val cutBaseFrames: Int = 32 * PROBE_BLOCK_FRAMES,
    /** The gate of the [FramingSweep.CUT] sweep's note, long enough that the cut lands inside it. */
    val cutGateFrames: Int = 9001,
    /** Frames compared after the sweep's latest event (0.25 s at 48 kHz by default). */
    val tailFrames: Int = 12000,
) {
    /** The frames compared from the onset in [sweep]. */
    fun spanFrames(sweep: FramingSweep): Int = when (sweep) {
        FramingSweep.ONSET -> gateFrames + tailFrames
        FramingSweep.GATE -> gateBaseFrames + blockFrames + tailFrames
        FramingSweep.CUT -> cutBaseFrames + blockFrames + tailFrames
    }

    /** The frames every render of [sweep] covers: the span after the latest onset, in whole blocks. */
    fun renderFrames(sweep: FramingSweep): Int = spanFrames(sweep) + 2 * blockFrames
}

/**
 * The verdict of one comparison, by the largest |difference| relative to the reference's peak (both channels):
 * [IDENTICAL] bit for bit; [ROUNDING] at most [FRAMING_ROUNDING_REL] (-200 dB, a different summation order);
 * [SMALL] at most [FRAMING_SMALL_REL] (-100 dB); [AUDIBLE] above.
 */
enum class FramingClass {
    IDENTICAL,
    ROUNDING,
    SMALL,
    AUDIBLE,
}

/** The [FramingClass.ROUNDING] bound: 1e-10 of the reference's peak, -200 dB. */
const val FRAMING_ROUNDING_REL: Double = 1e-10

/** The [FramingClass.SMALL] bound: 1e-5 of the reference's peak, -100 dB. */
const val FRAMING_SMALL_REL: Double = 1e-5

/**
 * Where the first differing frame lies, note-relative: [ONSET] in the first block window `[0, B)`; [TRANSITION] within
 * one block of the sweep's event (the gate close or the cut), `[E - B, E + B)`; [HELD] between the two; [RELEASE]
 * after the transition window. [NONE]: no difference.
 */
enum class FramingRegion {
    NONE,
    ONSET,
    HELD,
    TRANSITION,
    RELEASE,
}

/**
 * How far the difference reaches beyond the HEAD window of its region (`[0, B)` for [FramingRegion.ONSET],
 * `[E - B, E + B)` for [FramingRegion.TRANSITION], one block from the first difference otherwise):
 * [CONFINED] not one differing frame after it (a click-class defect, failure mode a or b); [SPILLS] it goes on but
 * its RMS after the head (up to the last differing frame) is more than [FRAMING_SPILL_DB] under the head's (a
 * defect's state decaying out of a filter); [PERSISTS] anything else (a phase or clock offset, a per-block law).
 */
enum class FramingExtent {
    NONE,
    CONFINED,
    SPILLS,
    PERSISTS,
}

/** How many blocks before the transition window [FramingReading.lateMaxAbs] looks at. */
const val FRAMING_LATE_BLOCKS: Int = 7

/** The [FramingExtent.SPILLS] bound: the difference after the head 20 dB (RMS) under the difference inside it. */
const val FRAMING_SPILL_DB: Double = -20.0

/**
 * One offset of a sweep, compared after alignment by the onset over [spanFrames] note frames. Frames are
 * note-relative ([NEVER] when there is none); levels are absolute unless named relative.
 */
data class FramingReading(
    /** The offset k inside the block that this row moved the border to. */
    val offset: Int,
    /** The reference render's onset frame (absolute). */
    val referenceOnset: Int,
    /** The moved render's onset frame (absolute). */
    val testOnset: Int,
    /** The sweep's event, note-relative: the gate close ([FramingSweep.ONSET], [FramingSweep.GATE]) or the cut. */
    val eventFrame: Int,
    /** Note frames compared. */
    val spanFrames: Int,
    /** The reference's largest |sample| over the span, both channels. */
    val referencePeak: Double,
    /** The reference's RMS over the span, both channels. */
    val referenceRms: Double,
    /** The largest |test - reference| (a NaN on one side only reads +Inf). */
    val maxAbsDiff: Double,
    /** Where [maxAbsDiff] sits, note-relative ([NEVER] when identical). */
    val maxDiffFrame: Int,
    /** RMS of the difference over the span relative to [referenceRms], dB (-Inf when identical). */
    val rmsRelDb: Double,
    /** The first and the last differing note frame ([NEVER] when identical). */
    val firstDiffFrame: Int,
    val lastDiffFrame: Int,
    /** How many note frames differ (either channel). */
    val diffFrames: Int,
    val region: FramingRegion,
    val extent: FramingExtent,
    /** The largest |difference| inside the head window, and after it. */
    val headMaxAbs: Double,
    val restMaxAbs: Double,
    /** RMS of the difference after the head (to the last differing frame) re the RMS inside the head, dB; -Inf when none. */
    val persistDb: Double,
    /**
     * The largest |difference| late in the held note: over the [FRAMING_LATE_BLOCKS] blocks before the transition
     * window, `[E - (1 + FRAMING_LATE_BLOCKS) B, E - B)`. Zero for a defect that is over by then (an onset or an
     * attack knee); the size of the offset for a phase or clock offset or a per-block law.
     */
    val lateMaxAbs: Double,
    /** The largest |sample| either render put out BEFORE its own onset (must be 0: onset placement, I1). */
    val preOnsetPeak: Double,
    /** Whether both renders' voices drew equally often from their random stream; null when unknown. */
    val rngSame: Boolean?,
    val cls: FramingClass,
) {
    /** [maxAbsDiff] relative to [referencePeak], dB (-Inf when identical, +Inf against a silent reference). */
    val maxRelDb: Double
        get() = when {
            maxAbsDiff == 0.0 -> Double.NEGATIVE_INFINITY
            referencePeak > 0.0 -> 20.0 * log10(maxAbsDiff / referencePeak)
            else -> Double.POSITIVE_INFINITY
        }

    /** Where [maxAbsDiff] sits relative to the event, frames ([NEVER] when identical). */
    val maxDiffFromEvent: Int get() = if (maxDiffFrame == NEVER) NEVER else maxDiffFrame - eventFrame
}

/** One sweep's readings, in offset order, and its worst one (by [FramingReading.maxAbsDiff] over the peak, then RMS). */
class FramingResult(
    val sweep: FramingSweep,
    val readings: List<FramingReading>,
) {
    val worst: FramingReading
        get() = readings.maxWith(
            compareBy<FramingReading> { it.cls.ordinal }
                .thenBy { if (it.referencePeak > 0.0) it.maxAbsDiff / it.referencePeak else it.maxAbsDiff }
                .thenBy { it.rmsRelDb },
        )

    /** The case's class: its worst offset's. */
    val cls: FramingClass get() = worst.cls

    /** How many offsets read each class. */
    fun count(cls: FramingClass): Int = readings.count { it.cls == cls }
}

/**
 * One row of the framing table before it is measured: [name] with [knobs] in [family], swept by [sweep] under
 * [config]. [known] quotes where the KLANG-00156 ledger or `audio/MEMORY.md` already names the law this case can show
 * (empty: nothing there).
 */
class FramingCase(
    val family: String,
    val name: String,
    val knobs: String,
    val sweep: FramingSweep,
    val config: FramingConfig,
    val known: String,
    val renderer: FramingRenderer,
)

/** One measured row: [case] and its [result]. */
class FramingRow(
    val case: FramingCase,
    val result: FramingResult,
)
