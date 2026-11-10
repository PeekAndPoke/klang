/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_bridge.ScheduledVoice

/**
 * The replace path's dedup (`VoiceScheduler.replaceVoices`): the [incoming] voices without those a still-playing
 * timeline voice already covers. [active] is the scheduler's active list in its order, one entry per voice: the
 * voice's source event, or null for a voice that has none (a realtime voice, which never matches).
 *
 * The rule: each incoming voice, in its order, is matched to the FIRST active voice, in the list's order, that is not
 * matched yet and is its duplicate ([ScheduledVoice.isDuplicate]: the same start, equal data); a matched incoming
 * voice is dropped, every other one is kept, in its order. 1 to 1, so a chord resent keeps its multiplicity.
 *
 * Sub-quadratic (audit L2-09, L9-10: every incoming voice was compared with every active one, and on the JVM each
 * pair boxed an index: 9.2 ms and 13.6 MB at 1000 by 1000, on the audio thread). A duplicate has the same start, so
 * only the active voices of that start are asked: their positions are sorted by start once per command (a stable
 * merge sort, so a group keeps the list's order), and each incoming voice finds its group by a binary search. Which
 * active voice of a group absorbs an incoming one never changes which incoming voices are kept (the two are equal, so
 * each equal class keeps its incoming voices past the count of its active ones); the order is kept anyway. The
 * frontend's real shape, every incoming start after every active one, returns before the sort. Starts compare with
 * `<` and `==`, as `isDuplicate` does (`-0.0` equals `0.0`; a NaN start matches nothing). Allocates per command (the
 * arrays and the result), never per pair.
 */
internal fun dropResentDuplicates(incoming: List<ScheduledVoice>, active: List<ScheduledVoice?>): List<ScheduledVoice> {
    if (incoming.isEmpty()) {
        return incoming
    }

    var count = 0
    var latestActive = Double.NEGATIVE_INFINITY

    for (i in 0 until active.size) {
        val source = active[i] ?: continue

        // NaN-guard: an active start that is NaN matches nothing and would break the sort's order; the scheduler
        // admits no such voice, this keeps the function whole
        if (source.startTime != source.startTime) {
            continue
        }

        count++

        if (source.startTime > latestActive) {
            latestActive = source.startTime
        }
    }

    if (count == 0) {
        return incoming
    }

    // The real shape: nothing incoming starts at or before the latest active start, so nothing can be a duplicate.
    // NaN-guard: a NaN start fails `>` and takes the general path below, where it matches nothing.
    var allLater = true

    for (i in 0 until incoming.size) {
        if (!(incoming[i].startTime > latestActive)) {
            allLater = false

            break
        }
    }

    if (allLater) {
        return incoming
    }

    // The active sources' positions, sorted by start, the list's order within one start.
    val positions = IntArray(count)
    var k = 0

    for (i in 0 until active.size) {
        val start = active[i]?.startTime ?: continue

        if (start == start) { // NaN-guard: as counted above
            positions[k++] = i
        }
    }

    val starts = DoubleArray(active.size)

    for (i in 0 until active.size) {
        starts[i] = active[i]?.startTime ?: 0.0
    }

    sortPositionsByStart(positions = positions, starts = starts)

    val claimed = BooleanArray(active.size)
    val kept = ArrayList<ScheduledVoice>(incoming.size)

    for (i in 0 until incoming.size) {
        val voice = incoming[i]
        val start = voice.startTime
        var matched = false
        // The first position whose start is not below this one's
        var lo = 0
        var hi = count

        while (lo < hi) {
            val mid = (lo + hi) ushr 1

            if (starts[positions[mid]] < start) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }

        var p = lo

        while (p < count && starts[positions[p]] == start) { // NaN-guard: a NaN start equals nothing
            val position = positions[p]

            if (!claimed[position] && active[position]!!.isDuplicate(voice)) {
                claimed[position] = true
                matched = true

                break
            }

            p++
        }

        if (!matched) {
            kept.add(voice)
        }
    }

    return kept
}

/**
 * Sorts [positions] by `starts[position]`, ascending under `<`, keeping the given order among equal starts: a
 * bottom-up merge sort over one scratch array, no boxing.
 */
private fun sortPositionsByStart(positions: IntArray, starts: DoubleArray) {
    val n = positions.size
    var from = positions
    var to = IntArray(n)
    var width = 1

    while (width < n) {
        var left = 0

        while (left < n) {
            val mid = minOf(left + width, n)
            val right = minOf(left + 2 * width, n)
            var a = left
            var b = mid
            var out = left

            while (a < mid && b < right) {
                // Stable: the left run wins a tie
                if (starts[from[b]] < starts[from[a]]) {
                    to[out++] = from[b++]
                } else {
                    to[out++] = from[a++]
                }
            }

            while (a < mid) {
                to[out++] = from[a++]
            }

            while (b < right) {
                to[out++] = from[b++]
            }

            left = right
        }

        val swap = from
        from = to
        to = swap
        width *= 2
    }

    if (from !== positions) {
        from.copyInto(positions)
    }
}
