/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

/**
 * The solo state of one playback: per source, its NEWEST solo event, "soloed at amount a from s until t".
 *
 * The scheduler records an entry from every event that carries a solo amount and a source id, a control event
 * (the keep-alive `solo(...)` puts over a rest) and a sounding note alike, and reads two things per block:
 *
 * - **Soloed**: a source is soloed while its newest event runs (`until + graceSec > now`) and carries a positive
 *   amount ([isSoloed]); its voices play at 1.0. The grace bridges a seam between two back-to-back events, so a solo
 *   never shows a hole one block wide that would restart the ramp.
 * - **The others** play at `1 - max(amount of the soloed sources)`, or at 1.0 when none is soloed ([targetGain]).
 *
 * The latest value wins at once (maintainer Q28, 2026-10-13, engine follow-up 43): an event with a LATER start
 * replaces the source's amount and end, so `solo(1)` edited to `solo(0)` ends the solo at the first zero event and
 * `solo("<1 0.5>")` follows its pattern. An event with an earlier start changes nothing. Nothing is held past the
 * newest event.
 *
 * **Control events decide the end.** Once a source's entry comes from a control event (what `solo(...)` puts over
 * its whole window, on a 1/8-cycle grid), a sounding note of it never sets the end: it updates the amount only. A
 * note's gate may be far longer than a grid piece (`note("a3").slow(4).solo()`), and taking it would keep the solo
 * alive for the whole note after `.solo()` was removed. With the SAME start (a note and the control piece over it,
 * popped in no fixed order) the control event's end wins; the next piece covers a note whose gate is shorter. Only
 * a source without control events (a frontend that sends notes alone) takes its ends from the notes, the later one
 * at the same start.
 *
 * Fixed-capacity arrays and a linear scan: nothing allocates after construction (audit item B4.2,
 * `docs/audio-audit/2026-10-07-engine-tidy-audit.md`). A source beyond [capacity] takes the slot of the entry that
 * ends first. The tracker belongs to the playback's scheduler and dies with it.
 */
internal class SoloTracker(
    val graceSec: Double,
    val capacity: Int = DEFAULT_CAPACITY,
) {
    companion object {
        /** Far more sources than a song solos at once. */
        const val DEFAULT_CAPACITY: Int = 32
    }

    private val ids = arrayOfNulls<String>(capacity)
    private val amounts = DoubleArray(capacity)
    private val startSecs = DoubleArray(capacity)
    private val untilSecs = DoubleArray(capacity)

    /** Whether the entry's end comes from a control event: then a note no longer sets it. */
    private val fromControl = BooleanArray(capacity)

    /** Whether the source has just lost its solo and the background has not come back yet ([isReleased]). */
    private val released = BooleanArray(capacity)

    /**
     * The number of entries: those whose newest event still runs, soloed or not (amount 0), and the released ones
     * ([isReleased]) until [clearReleased].
     */
    var size: Int = 0
        private set

    private var maxAmount: Double = 0.0

    /**
     * Records that [sourceId]'s solo event from [startSec] until [untilSec] (seconds on the backend clock) carries
     * [amount]; [control] says whether it is a control event (`VoiceData.control`) or a sounding note.
     *
     * An amount that is not positive and finite (0.0, a NaN, an infinity) means "not soloed": it ends a running solo
     * of the source and starts none. A start or an end that is not finite is ignored (such an entry could never be
     * ordered or expire). An amount above 1.0 is kept as it is (the Motor stays raw; the frontend coerces).
     */
    fun record(sourceId: String, amount: Double, startSec: Double, untilSec: Double, control: Boolean) {
        if (!startSec.isFinite() || !untilSec.isFinite()) {
            return
        }

        // NaN-guard: a NaN amount fails the compare and reads as "not soloed"
        val value = if (amount > 0.0 && amount.isFinite()) amount else 0.0
        val existing = indexOf(sourceId)

        if (existing >= 0) {
            val later = startSec > startSecs[existing]

            if (!later && startSec != startSecs[existing]) {
                return
            }

            // a soloed source told "0" is released; one soloed again is not
            released[existing] = if (value > 0.0) false else released[existing] || amounts[existing] > 0.0
            amounts[existing] = value
            startSecs[existing] = startSec

            when {
                // a control event decides the end: a later one replaces it, one at the same start overrides a note's
                // (two at the same start, one call site solo-ing twice, keep the later)
                control -> {
                    untilSecs[existing] = if (later || !fromControl[existing]) untilSec else maxOf(untilSecs[existing], untilSec)
                    fromControl[existing] = true
                }
                // a note never moves a control event's end
                fromControl[existing] -> {}
                // notes alone: a later one replaces the end, one at the same start keeps the later
                later -> {
                    untilSecs[existing] = untilSec
                }
                else -> {
                    untilSecs[existing] = maxOf(untilSecs[existing], untilSec)
                }
            }

            return
        }

        // A source that is not soloed needs no entry to say so
        if (value == 0.0) {
            return
        }

        val slot = if (size < capacity) size++ else soonestToExpire()

        ids[slot] = sourceId
        amounts[slot] = value
        startSecs[slot] = startSec
        untilSecs[slot] = untilSec
        fromControl[slot] = control
        released[slot] = false
    }

    /**
     * Ends the entries whose newest event has ended (plus the grace) at [nowSec] and refreshes [targetGain]. A soloed
     * source whose event ends this way is not dropped but released (amount 0, [isReleased]) until [clearReleased];
     * any other ended entry is dropped.
     */
    fun advance(nowSec: Double) {
        var write = 0
        var max = 0.0

        for (read in 0 until size) {
            val until = untilSecs[read]

            if (until + graceSec <= nowSec) {
                if (amounts[read] > 0.0) {
                    // the solo ran out: released, and the next event of the source starts afresh
                    amounts[read] = 0.0
                    released[read] = true
                    fromControl[read] = false
                } else if (!released[read]) {
                    continue
                }
            }

            if (write != read) {
                ids[write] = ids[read]
                amounts[write] = amounts[read]
                startSecs[write] = startSecs[read]
                untilSecs[write] = until
                fromControl[write] = fromControl[read]
                released[write] = released[read]
            }

            if (amounts[write] > max) {
                max = amounts[write]
            }

            write++
        }

        for (i in write until size) {
            ids[i] = null
        }

        size = write
        maxAmount = max
    }

    /** The level the voices of every source that is not soloed play at, as of the last [advance]: `1 - max amount`. */
    fun targetGain(): Double = 1.0 - maxAmount

    /** True while [sourceId] is soloed, as of the last [advance]: its voices play at 1.0. */
    fun isSoloed(sourceId: String?): Boolean {
        if (sourceId == null) {
            return false
        }

        val index = indexOf(sourceId)

        return index >= 0 && amounts[index] > 0.0
    }

    /**
     * True while [sourceId] has just lost its solo (its newest event said 0, or its solo ran out) and the scheduler
     * has not called [clearReleased] yet: its voices, new ones included, play at the level the background is coming
     * back to, not at the ramp's (no dip).
     */
    fun isReleased(sourceId: String?): Boolean {
        if (sourceId == null) {
            return false
        }

        val index = indexOf(sourceId)

        return index >= 0 && released[index]
    }

    /**
     * Ends every release: the background's ramp has reached its target, or the target fell (another solo). A
     * released entry whose event has ended is dropped at the next [advance].
     */
    fun clearReleased() {
        for (i in 0 until size) {
            released[i] = false
        }
    }

    private fun indexOf(sourceId: String): Int {
        for (i in 0 until size) {
            if (ids[i] == sourceId) {
                return i
            }
        }

        return -1
    }

    /** The slot a new source takes when full: an entry that solos nothing (amount 0) first, else the one ending first. */
    private fun soonestToExpire(): Int {
        var slot = 0

        for (i in 1 until size) {
            val zeroFirst = (amounts[i] == 0.0) != (amounts[slot] == 0.0)

            if (zeroFirst) {
                if (amounts[i] == 0.0) {
                    slot = i
                }
            } else if (untilSecs[i] < untilSecs[slot]) {
                slot = i
            }
        }

        return slot
    }
}
