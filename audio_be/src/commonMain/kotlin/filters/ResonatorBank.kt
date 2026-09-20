/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.exp
import kotlin.math.ln

/**
 * Resonator bank: a parallel bank of [SvfBPF][LowPassHighPassFilters.SvfBPF] bandpasses, each
 * band scaled by its own LINEAR gain, the outputs summed. The one core behind `body(...)` and
 * `vowel(...)` (Katalyst step 5c-3, 2026-09-19); it knows nothing about either. What a band's
 * gain means is decided where a table row becomes a band:
 * [LowPassHighPassFilters.bodyGain] and [LowPassHighPassFilters.vowelGain].
 *
 * This is a **pure wet** filter: its output is the resonance, nothing else. The dry/wet blend
 * and the floor live in the [ParallelMixFilter] that wraps it (`createBody` / `createFormant`).
 *
 * **NaN safety:** a band's `freq` and `q` are guarded HERE, with the SVF's own
 * [clampSvfCutoff] / [clampSvfQ], before their logarithm is taken, so every log is finite and
 * the coefficients are the same doubles the raw values would have produced (the SVF's own clamps
 * are then no-ops). A band's gain is used as given, and each mapping guards a non-finite dB.
 *
 * **DC:** each SVF bandpass has DC gain 0, so the sum stays 0 at DC.
 *
 * **Output normalization:** none, N coherent peaks are summed without `1/N` scaling. Relies on
 * the master softCap/limiter. Scratch buffers are per instance and grow once if the block does.
 *
 * ## The morph (Katalyst step 5c-10, 2026-09-20)
 *
 * [morphTo] travels this bank's bands to another material's instead of crossfading two banks, so
 * a vowel change sweeps like a mouth. The rules were decided with the maintainer
 * (`docs/tasks/katalyst-dsl.md`, "Morph rules of the bank"):
 *
 * - bands pair **by position**, band n to band n, never by nearest frequency;
 * - **frequency and Q glide in LOG space**, the **gain LINEARLY**, over [KNOB_GLIDE_SECONDS];
 * - a band that exists on **one side only keeps its frequency and fades its gain in place**;
 * - a band that arrives on a slot that held another band starts **cold** (its integrators are
 *   zeroed), which its own fade-in from gain 0 masks (measured 0.2 dB, Katalyst 5c-10);
 * - the bank has a **fixed capacity**, preallocated at construction, so a morph allocates
 *   nothing: today's tables need at most 8 (body 8, vowel 5).
 *
 * The gain moves per SAMPLE and the coefficients once per BLOCK, each measured before it was
 * built: a gain moved per block is a step every 128 frames and measured 53 dB worse; the
 * coefficient ramp is given the BLOCK's length, not [FILTER_SMOOTH_SAMPLES], which measured at
 * the floor of an ideal per-sample glide where 32 samples left a 375 Hz staircase 16 dB above it
 * (see [LowPassHighPassFilters.BaseSvf.retune]). A band whose frequency AND q are unchanged is
 * not retuned at all, which is exact (it already stands there) and saves the `tan` its
 * coefficients would cost: the whole band-count case, where five modes only fade out in place.
 *
 * **The first morph is instant.** Until a block has been processed the bank has not sounded, so
 * [morphTo] installs at once (the `KnobGlide` snap rule): a host configured twice before its
 * first block must open on the second material, not glide from the first.
 *
 * **The four questions of `docs/plans/effect-state-machines.md`, answered for this bank.** It has
 * no state classes, for the reason the gain, the phaser and the duck have none: its situations
 * are exactly a glide's (fresh, travelling, settled), which are [fresh] and [morphPos], and
 * classes would copy them.
 * 1. *What outlives a morph:* the SVF instances with their integrator state (a morph RETUNES,
 *    it never rebuilds, which is the whole point), the gains, the capacity and the scratch
 *    buffers. Row: the trajectory row, whose oracle keeps one filter per band running across
 *    the change.
 * 2. *The record a finished life leaves:* none. The landing writes `from = to` and drops every
 *    band that reached gain 0 ([landed]); a dropped band that comes back starts COLD, so no
 *    previous life rings into the next one. Rows: "a band the target does not have ... in place"
 *    and "a band the bank does not have arrives ... COLD".
 * 3. *The Off precondition:* this bank has no Off. It is installed and released by
 *    [KatalystFilterSwap][io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystFilterSwap],
 *    which owns that edge and fades it; the bank is never silenced in place. Rows: the hosts'
 *    "a change on a bank that is FADING OUT installs a fresh one".
 * 4. *References and who drops them:* none. Every datum here is a `Double`, an `Int` or one of
 *    the bank's own preallocated objects, so nothing outside can be kept alive. Vacuous, and
 *    said so rather than pinned by a row that cannot fail.
 */
class ResonatorBank(
    bands: List<Band>,
    private val sampleRate: Double,
    /**
     * How many bands this bank can ever hold, preallocated. Raised to `bands.size` when a caller
     * asks for less, so no argument can make [process] read past its arrays. A [morphTo] to more
     * bands than this is refused; the host then installs a new bank and crossfades, which is what
     * it does when there is nothing to morph from anyway.
     */
    capacity: Int = MORPH_CAPACITY,
) : AudioFilter {

    companion object {
        /**
         * The capacity every bank gets at least: the largest band count the shipped tables use
         * (8, the body materials; every vowel has 5). A user surface that lists formants one by
         * one states its own maximum, which is why this is a floor and not a law.
         *
         * `ResonatorBankMorphSpec` pins it against both catalogues, so a table that grows past it
         * turns a row red instead of silently losing a morph.
         */
        const val MORPH_CAPACITY: Int = 8
    }

    /** One band: centre frequency in Hz, the SVF's q (a pure width control), a linear gain. */
    data class Band(val freq: Double, val q: Double, val gain: Double)

    private val slots: Int = maxOf(bands.size, capacity)

    private val filters =
        Array(slots) { LowPassHighPassFilters.SvfBPF(freqOf(bands, it), qOf(bands, it), sampleRate) }

    /** The gain in force at the last sample processed; the morph's start value for a retarget. */
    private val gains = DoubleArray(slots) { if (it < bands.size) bands[it].gain else 0.0 }

    private val gainFrom = DoubleArray(slots) { gains[it] }
    private val gainTo = DoubleArray(slots) { gains[it] }

    // Frequency and Q travel on the LOG axis, so these are the logarithms of the clamped values.
    private val logFreqFrom = DoubleArray(slots) { ln(freqOf(bands, it)) }
    private val logFreqTo = DoubleArray(slots) { logFreqFrom[it] }
    private val logQFrom = DoubleArray(slots) { ln(qOf(bands, it)) }
    private val logQTo = DoubleArray(slots) { logQFrom[it] }

    // The same targets UN-logged. `exp(ln(x))` is x only to within an ulp, and the landing block
    // retunes from these instead of from the logarithms, so a landed morph holds the target
    // EXACTLY, as every other glide in the engine does - exactly, that is, to within the
    // RESOLUTION OF THE LOG AXIS: `ln` is not injective on doubles (seven doubles share
    // `ln(1900.0)`), so a target within a few ulps of where the band already stands reads as no
    // move at all, is skipped, and leaves the band up to about 3e-16 relative off its target. No
    // shipped table can ask for a move that small.
    private val freqTo = DoubleArray(slots) { freqOf(bands, it) }
    private val qTo = DoubleArray(slots) { qOf(bands, it) }

    /** Bands the sum loop runs: the union of the two sides while a morph travels between them. */
    private var active: Int = bands.size

    /** Bands left when the morph lands; the ones above it have reached gain 0 and drop out. */
    private var landed: Int = bands.size

    private val morphLen: Int = (sampleRate * KNOB_GLIDE_SECONDS).toInt().coerceAtLeast(1)

    private val invMorphLen: Double = 1.0 / morphLen

    /** Samples of the current morph already travelled; [morphLen] means settled. */
    private var morphPos: Int = morphLen

    /** True until a block has been processed: the first [morphTo] installs at once. */
    private var fresh: Boolean = true

    private var inputCopy: AudioBuffer = AudioBuffer(0)
    private var bandBuffer: AudioBuffer = AudioBuffer(0)

    /** Test seam: whether a morph is still travelling. */
    internal val morphing: Boolean get() = morphPos < morphLen

    /** Test seam: the bands the sum loop runs, the ones fading out to 0 included. */
    internal val activeBands: Int get() = active

    /** Test seam: how many bands this bank can hold. */
    internal val capacityBands: Int get() = slots

    /** Test seam: the band the bank sounds at this instant, the morph's position folded in. */
    internal fun bandNow(index: Int): Band = Band(
        freq = freqNow(index),
        q = qNow(index),
        gain = gains[index],
    )

    /**
     * Travels every band to the target over [KNOB_GLIDE_SECONDS], by the rules in the class KDoc.
     * Returns false, and changes nothing, when the target does not fit this bank's capacity: the
     * caller then installs a new bank and crossfades.
     *
     * The target arrives as three parallel arrays and a [count] rather than a list of [Band]s, so
     * a host can keep ONE scratch of each and a material change on the audio thread allocates
     * nothing at all. Only the first [count] entries of each array are read.
     *
     * A morph still travelling is retargeted from where its bands STAND, so a change on every
     * block never steps (and, like every glide, converges like a one-pole rather than reaching
     * each material: `docs/plans/knob-glide.md`).
     */
    fun morphTo(freq: DoubleArray, q: DoubleArray, gain: DoubleArray, count: Int): Boolean {
        if (count > slots) {
            return false
        }

        val snap = fresh
        val union = maxOf(active, count)

        for (b in 0 until union) {
            val hasNow = b < active
            val hasNext = b < count
            // Where this band stands right now, in Hz and in q: its live values, or the TARGET's
            // for a slot no band occupies (it arrives in place and only its gain travels). The
            // logarithms are taken from these, so a band that is not moving sits on exactly one
            // double, however often a LATER morph retargets it.
            val fromGain = if (hasNow) gains[b] else 0.0
            val fromFreq = if (hasNow) freqNow(b) else clampSvfCutoff(freq[b], sampleRate)
            val fromQ = if (hasNow) qNow(b) else clampSvfQ(q[b])

            // A band that exists on one side only keeps its frequency and moves only its gain.
            gainTo[b] = if (hasNext) gain[b] else 0.0
            freqTo[b] = if (hasNext) clampSvfCutoff(freq[b], sampleRate) else fromFreq
            qTo[b] = if (hasNext) clampSvfQ(q[b]) else fromQ
            logFreqTo[b] = ln(freqTo[b])
            logQTo[b] = ln(qTo[b])

            if (!hasNow) {
                // A cold start on a slot a previous life may have left ringing at another
                // frequency: the fade-in from 0 masks it, and it is the one thing that must not
                // carry over (measured in Katalyst 5c-10).
                filters[b].resetState()
            }

            if (snap) {
                gains[b] = gainTo[b]
                gainFrom[b] = gainTo[b]
                logFreqFrom[b] = logFreqTo[b]
                logQFrom[b] = logQTo[b]
                filters[b].retune(freqTo[b], qTo[b], 0)
            } else {
                gains[b] = fromGain
                gainFrom[b] = fromGain
                logFreqFrom[b] = ln(fromFreq)
                logQFrom[b] = ln(fromQ)

                if (!hasNow) {
                    // Nothing to travel from: the frequency and Q are already the target's.
                    filters[b].retune(fromFreq, fromQ, 0)
                }
            }
        }

        active = if (snap) count else union
        landed = count
        morphPos = if (snap) morphLen else 0

        return true
    }

    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        fresh = false

        if (inputCopy.size < length) {
            inputCopy = AudioBuffer(length)
            bandBuffer = AudioBuffer(length)
        }

        // 1. Copy the input to scratch (we overwrite `buffer` with the band sum below).
        buffer.copyInto(inputCopy, 0, offset, offset + length)

        // 2. Clear the output region: the first band sums into zero. No bands: silence.
        buffer.fill(0.0, offset, offset + length)

        val n = active
        val pos = morphPos
        val len = morphLen
        val inv = invMorphLen
        // The samples of THIS block the morph still travels; 0 once it has landed. The
        // coefficients ramp over exactly these, so they land on the morph's last sample.
        val ramp = if (pos < len) minOf(length, len - pos) else 0

        if (ramp > 0) {
            val lands = pos + ramp >= len
            val tEnd = (pos + ramp) * inv

            for (b in 0 until n) {
                val fromLogF = logFreqFrom[b]
                val toLogF = logFreqTo[b]
                val fromLogQ = logQFrom[b]
                val toLogQ = logQTo[b]

                if (fromLogF == toLogF && fromLogQ == toLogQ) {
                    // A band that only fades: it already stands exactly where it is going, so a
                    // retune would cost a `tan` to write the numbers it already holds.
                    continue
                }

                // The landing block retunes from the un-logged target, so the bank settles on the
                // material EXACTLY; every earlier block travels on the log axis.
                if (lands) {
                    filters[b].retune(freqTo[b], qTo[b], ramp)
                } else {
                    filters[b].retune(
                        exp(fromLogF + (toLogF - fromLogF) * tEnd),
                        exp(fromLogQ + (toLogQ - fromLogQ) * tEnd),
                        ramp,
                    )
                }
            }
        }

        // 3. Run each band on its own copy of the input; sum into the output with its gain.
        for (b in 0 until n) {
            inputCopy.copyInto(bandBuffer, 0, 0, length)
            filters[b].process(bandBuffer, 0, length)

            if (ramp > 0) {
                val to = gainTo[b]
                val span = to - gainFrom[b]
                // Counted DOWN to the landing, so the morph's last sample is exactly the target.
                var left = len - pos

                for (i in 0 until ramp) {
                    left--
                    buffer[offset + i] = buffer[offset + i] + bandBuffer[i] * (to - span * (left * inv))
                }

                for (i in ramp until length) {
                    buffer[offset + i] = buffer[offset + i] + bandBuffer[i] * to
                }

                gains[b] = to - span * (left * inv)
            } else {
                val gain = gains[b]

                for (i in 0 until length) {
                    buffer[offset + i] = buffer[offset + i] + bandBuffer[i] * gain
                }
            }
        }

        if (ramp > 0) {
            morphPos = pos + ramp

            if (morphPos >= len) {
                // Landed: the values ARE the target, and the bands that reached gain 0 drop out.
                for (b in 0 until n) {
                    gainFrom[b] = gainTo[b]
                    logFreqFrom[b] = logFreqTo[b]
                    logQFrom[b] = logQTo[b]
                }

                active = landed
            }
        }
    }

    /**
     * The frequency band [index] is tuned to at this instant: the target once the morph has
     * landed (exactly, never through a logarithm and back), the point on the log axis while it
     * travels. That is what the last [process] handed the SVF, EXCEPT for a band the block
     * skipped as unmoving, where `process` handed it nothing and the filter still stands on the
     * value it was last given, which is this one to within the log axis's own resolution. Either
     * way a retarget starts where the band stands.
     */
    private fun freqNow(index: Int): Double {
        if (!morphing) {
            return freqTo[index]
        }

        val t = morphPos * invMorphLen

        return exp(logFreqFrom[index] + (logFreqTo[index] - logFreqFrom[index]) * t)
    }

    /** The twin of [freqNow] for q. */
    private fun qNow(index: Int): Double {
        if (!morphing) {
            return qTo[index]
        }

        val t = morphPos * invMorphLen

        return exp(logQFrom[index] + (logQTo[index] - logQFrom[index]) * t)
    }

    /** The SVF's own cutoff guard, applied here so the logarithm below is always finite. */
    private fun freqOf(bands: List<Band>, index: Int): Double {
        if (index >= bands.size) {
            return 1000.0
        }

        return clampSvfCutoff(bands[index].freq, sampleRate)
    }

    /** The SVF's own q guard, for the same reason as [freqOf]. */
    private fun qOf(bands: List<Band>, index: Int): Double {
        if (index >= bands.size) {
            return SVF_Q_FALLBACK
        }

        return clampSvfQ(bands[index].q)
    }
}
