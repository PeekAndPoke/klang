/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.spine.storage.RecentsStorage
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlin.js.Date

/**
 * What the user opened last, a part of the Spine (KLANG-00403, D1): songs and tutorial lessons only (the maintainer,
 * 2026-11-01: "I would only add the Songs and Tutorial to the 'recently open'"; docs get bookmarks, KLANG-00404). A page
 * reports what it opened ([opened]); the Motor's menu shows [entries] under "Pick up where you left off". Kept in the
 * browser ([RecentsStorage]), so the list is there after a reload.
 */
class Recents {

    companion object {
        /**
         * How many of each kind are kept: the songs the menu shows, and a few lessons (it shows the track of the latest one
         * the app still has).
         */
        val keep: Map<Kind, Int> = mapOf(Kind.Song to 6, Kind.Tutorial to 3)

        /**
         * The list after [entry] was opened: the entry first, any earlier one of the same kind and id gone, each kind cut to
         * its [keep] count (a kind not in it is not kept at all). The order is the order of opening, not of the clock, so a
         * clock set back cannot put the entry just opened below older ones.
         */
        fun record(entries: List<Entry>, entry: Entry, keep: Map<Kind, Int>): List<Entry> {
            val all = listOf(entry) + entries.filterNot { it.kind == entry.kind && it.id == entry.id }
            val kept = mutableMapOf<Kind, Int>()

            return all.filter { item ->
                val count = kept[item.kind] ?: 0

                kept[item.kind] = count + 1

                count < (keep[item.kind] ?: 0)
            }
        }
    }

    enum class Kind {
        Song,
        Tutorial,
    }

    /**
     * One thing opened: its [kind] and [id] (a song's id, a lesson's slug), its [title] and [at] when, in milliseconds
     * since the epoch. A song keeps its [rpm]; a lesson the [track] it was opened in (its slug), if any.
     */
    data class Entry(
        val kind: Kind,
        val id: String,
        val title: String,
        val at: Double,
        val rpm: Double? = null,
        val track: String? = null,
    )

    private val entriesSource = StreamSource(RecentsStorage.load())

    /** What was opened, the newest first. */
    val entries: Stream<List<Entry>> = entriesSource

    /** A page opened the [entry]: it goes first, an earlier entry for the same thing goes, and the list is kept. */
    fun opened(entry: Entry) {
        val next = record(entries = entriesSource(), entry = entry, keep = keep)

        if (next != entriesSource()) {
            entriesSource(next)
            RecentsStorage.save(next)
        }
    }

    /** A song opened now. */
    fun openedSong(id: String, title: String, rpm: Double) {
        opened(Entry(kind = Kind.Song, id = id, title = title, at = Date.now(), rpm = rpm))
    }

    /** A lesson opened now, in the track [track] (its slug), if it was opened in one. */
    fun openedTutorial(slug: String, title: String, track: String?) {
        opened(Entry(kind = Kind.Tutorial, id = slug, title = title, at = Date.now(), track = track))
    }
}
