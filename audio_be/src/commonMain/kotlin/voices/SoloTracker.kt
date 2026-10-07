/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

/**
 * The solo state of one playback: "source X is soloed at amount a until t".
 *
 * The scheduler records an entry from every event that carries a solo amount and a source id, a control event
 * (the keep-alive `solo(...)` puts over a rest) and a sounding note alike, and reads two things per block:
 *
 * - **Live**: an entry is live while `until + graceSec > now`. The others play at `1 - max(amount of live
 *   entries)`, or at 1.0 when no entry is live ([targetGain]). The grace bridges a seam between two back-to-back
 *   events, so a solo never shows a hole one block wide that would restart the ramp.
 * - **Protected**: a source keeps its entry, and its voices play at 1.0, while `until + holdSec > now`
 *   ([isProtected]). The hold must be at least the background's ramp time, so a soloed source's tail never dips
 *   while the others come back.
 *
 * A re-recorded source takes the new amount (the last writer wins, so `solo("<1 0.5>")` follows its pattern) and
 * keeps the LATER of the two ends: a soloed note and the control event over its cycle start together and pop in no
 * fixed order, and the note's shorter gate must not cut the solo short. An end never moves back.
 *
 * Fixed-capacity arrays and a linear scan: nothing allocates after construction (audit item B4.2,
 * `docs/audio-audit/2026-10-07-engine-tidy-audit.md`). A source beyond [capacity] takes the slot of the entry that
 * expires first. The tracker belongs to the playback's scheduler and dies with it.
 */
internal class SoloTracker(
    val holdSec: Double,
    val graceSec: Double,
    val capacity: Int = DEFAULT_CAPACITY,
) {
    companion object {
        /** Far more sources than a song solos at once. */
        const val DEFAULT_CAPACITY: Int = 32
    }

    private val ids = arrayOfNulls<String>(capacity)
    private val amounts = DoubleArray(capacity)
    private val untilSecs = DoubleArray(capacity)

    /** The number of entries, live or only protecting. */
    var size: Int = 0
        private set

    private var anyLive: Boolean = false
    private var maxLiveAmount: Double = 0.0

    /**
     * Records that [sourceId] is soloed at [amount] until [untilSec] (seconds on the backend clock).
     *
     * An amount that is not positive and finite engages nothing (a NaN, 0.0, an infinity), and an end that is not
     * finite is ignored: such an entry could never expire. An amount above 1.0 is kept as it is (the Motor stays raw;
     * the frontend coerces).
     */
    fun record(sourceId: String, amount: Double, untilSec: Double) {
        // NaN-guard: a NaN amount fails the compare and engages nothing
        if (!(amount > 0.0) || !amount.isFinite() || !untilSec.isFinite()) {
            return
        }

        val existing = indexOf(sourceId)

        if (existing >= 0) {
            amounts[existing] = amount

            if (untilSec > untilSecs[existing]) {
                untilSecs[existing] = untilSec
            }

            return
        }

        val slot = if (size < capacity) size++ else soonestToExpire()

        ids[slot] = sourceId
        amounts[slot] = amount
        untilSecs[slot] = untilSec
    }

    /** Drops the entries whose hold has ended at [nowSec] and refreshes [targetGain]. Once per block. */
    fun advance(nowSec: Double) {
        var write = 0
        var live = false
        var max = 0.0

        for (read in 0 until size) {
            val until = untilSecs[read]

            if (until + holdSec <= nowSec) {
                continue
            }

            if (write != read) {
                ids[write] = ids[read]
                amounts[write] = amounts[read]
                untilSecs[write] = until
            }

            if (until + graceSec > nowSec) {
                live = true

                if (amounts[write] > max) {
                    max = amounts[write]
                }
            }

            write++
        }

        for (i in write until size) {
            ids[i] = null
        }

        size = write
        anyLive = live
        maxLiveAmount = max
    }

    /** The level the voices of every unprotected source play at, as of the last [advance]: `1 - max amount`. */
    fun targetGain(): Double = if (anyLive) 1.0 - maxLiveAmount else 1.0

    /** True while [sourceId] holds an entry, as of the last [advance]: its voices play at 1.0. */
    fun isProtected(sourceId: String?): Boolean = sourceId != null && indexOf(sourceId) >= 0

    private fun indexOf(sourceId: String): Int {
        for (i in 0 until size) {
            if (ids[i] == sourceId) {
                return i
            }
        }

        return -1
    }

    private fun soonestToExpire(): Int {
        var slot = 0

        for (i in 1 until size) {
            if (untilSecs[i] < untilSecs[slot]) {
                slot = i
            }
        }

        return slot
    }
}
