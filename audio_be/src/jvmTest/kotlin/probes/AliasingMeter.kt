/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

/**
 * How [measureAliasing] reads one steady signal (KLANG-00420, probe 2): it skips [settleFrames], takes the next
 * [fftSize] frames (a power of two) under the 7-term Blackman-Harris window ([blackmanHarris7]), and counts every bin
 * within [bandBins] of a harmonic of the fundamental (or of DC) as that component's. The fundamental is searched within
 * [searchFraction] of the nominal pitch (at least 3 bins) and refined by a parabola through the log power.
 */
data class AliasingConfig(
    val sampleRate: Int = PROBE_SAMPLE_RATE,
    val fftSize: Int = 65536,
    val settleFrames: Int = PROBE_SAMPLE_RATE / 2,
    val bandBins: Int = 8,
    val searchFraction: Double = 0.02,
) {
    /** The width of one FFT bin in Hz. */
    val binHz: Double get() = sampleRate.toDouble() / fftSize

    /** The frames a signal must have: the settle time and one analysis window. */
    val framesNeeded: Int get() = settleFrames + fftSize
}

/**
 * What [measureAliasing] read off one signal. Levels in dBFS are relative to a full-scale SINE (a sine of peak 1.0 reads
 * 0 dBFS); [dcDbfs] is the DC value itself (a constant 1.0 reads 0 dB). Levels in plain dB are relative to the
 * fundamental's power. A sum with no power reads -Infinity.
 */
data class AliasingReading(
    /** The pitch the meter was told to expect. */
    val nominalHz: Double,
    /** The base of the harmonic grid: the strongest bin near [nominalHz], refined between the bins, or [nominalHz] itself when the pitch was not searched. */
    val fundamentalHz: Double,
    /** [fundamentalHz] re [nominalHz], in cents. */
    val offsetCents: Double,
    /** The fundamental's level (its band's power), dBFS. */
    val fundamentalDbfs: Double,
    /** How many harmonics (the fundamental included) lie below Nyquist: the bands counted as legitimate. */
    val harmonicCount: Int,
    /** The power of harmonics 2 and up below Nyquist, re the fundamental. */
    val harmonicDb: Double,
    /** Every harmonic band's power (the fundamental's included), dBFS. */
    val harmonicsDbfs: Double,
    /** The power of every bin outside the harmonic bands and the DC band, re the fundamental: the aliasing. */
    val nonHarmonicDb: Double,
    /**
     * The same power re every harmonic band's (the fundamental's included): the one number that stays meaningful where
     * the fundamental is weak or absent (a full-wave rectifier makes even harmonics only).
     */
    val nonHarmonicReHarmonicsDb: Double,
    /** [nonHarmonicDb] as an absolute level, dBFS. */
    val nonHarmonicDbfs: Double,
    /**
     * The non-harmonic power that lands below [ALIASING_AUDIBLE_BAND_HZ] (5 kHz), re the fundamental: the aliases where
     * they are heard. The total ([nonHarmonicDb]) is dominated by the band just under Nyquist and can rank two methods
     * the wrong way round (audit finding L3-02, KLANG-00430).
     */
    val nonHarmonicBelow5kDb: Double,
    /** The strongest bin outside the bands, refined between the bins (NaN when there is none). */
    val strongestHz: Double,
    /** The power within [AliasingConfig.bandBins] of [strongestHz] (outside the bands), re the fundamental. */
    val strongestDb: Double,
    /** The DC band's level: 20 log10 of the DC value. */
    val dcDbfs: Double,
)

/**
 * What a row's number may be read as. [CONTROL]: the meter's floor or a harness check. [MEASURED]: non-harmonic energy is
 * aliasing or imaging, a defect to rank. [BY_DESIGN]: the source or stage is inharmonic on purpose (noise, crush,
 * coarse); the number is printed and is not a defect. [NOT_MEASURABLE]: this meter cannot separate aliasing from what
 * the source legitimately does (detuned unison, a string's stretched partials); the row says why and prints no number.
 */
enum class AliasingKind {
    CONTROL,
    MEASURED,
    BY_DESIGN,
    NOT_MEASURABLE,
}

/**
 * One row of the aliasing table before it is measured: [stage] at [knobs], of [family], at [pitchHz], read as [kind]
 * with [note] (the reason for a by-design or not-measurable row). [render] produces the mono output the meter reads,
 * at least [AliasingConfig.framesNeeded] frames.
 */
class AliasingCase(
    val family: String,
    val stage: String,
    val knobs: String,
    val pitchHz: Double,
    val kind: AliasingKind,
    val note: String,
    /** True for a source (its pitch is searched, [measureAliasing]'s `findPitch`), false for a stage fed an exact sine. */
    val findPitch: Boolean,
    val render: () -> DoubleArray,
)

/** One measured row: [case] and its [reading]. */
class AliasingRow(
    val case: AliasingCase,
    val reading: AliasingReading,
)
