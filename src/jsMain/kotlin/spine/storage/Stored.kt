/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine.storage

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.klang.workshop.storage.persist
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.serialization.KSerializer

private val log = Logger(area = LogArea.Storage, source = "Stored")

/**
 * One stored section of the Spine (KLANG-00422): a data class kept as one document under one [key], read when the
 * Spine is built, shown as a [stream], changed with [update].
 *
 * The keeping is the workshop's ([DocumentStorage] behind a persisted stream): a write merges only the fields that
 * changed into what the store holds, so two tabs and an older build beside a newer one seldom undo each other (what
 * it does not cover is in its KDoc); a document that cannot be read is put aside before anything is written over
 * it; what was kept before the section existed is read once ([legacy]). Which [store] it is decides who shares the
 * section (`SectionStores`): the user, this machine, or this tab. A section that [follows] shows another tab's
 * change at once.
 *
 * What is here is the Spine's side: the [default] when nothing is kept, every value held in its range ([normalise]),
 * and the log. A section that begins from another's value says so with [start].
 */
class Stored<T>(
    /** The key the section is kept under. */
    val key: String,
    version: Int,
    serializer: KSerializer<T>,
    private val default: T,
    store: KeyValueStore,
    private val normalise: (T) -> T = { it },
    legacy: (KeyValueStore) -> DocumentStorage.Legacy<T>? = { null },
    follows: Boolean = true,
    /**
     * What the section begins with where nothing is kept under its key, in place of the [default], and kept at once:
     * a tab's section that begins as the machine's last one, and is the tab's own from then. A [reset] still shows
     * the [default].
     */
    start: (() -> T)? = null,
) {
    private val storage = DocumentStorage(
        key = key,
        version = version,
        serializer = serializer,
        store = store,
        clean = normalise,
        legacy = legacy,
        follows = follows,
        onProblem = { message, cause -> log.warn(message, cause) },
    )

    /** What is shown; [kept] is the same stream with every value it is given also written. */
    private val shown = StreamSource(default)

    private val kept = shown.persist(storage)

    init {
        // Kept also where it is the default: the section holds its own from here, whatever the other one becomes
        if (start != null && !storage.holds) {
            kept(normalise(start()))
        }
    }

    /** The section as it is now. */
    val stream: Stream<T> = shown

    /** The section's value now. */
    operator fun invoke(): T = shown()

    /**
     * One part of the section as a stream of its own, which only says something when that part changes. Asked for
     * its value it gives the part as it is now, listened to or not (Ultra's `distinct` reads its source since
     * 0.118.0, ULTRA-00302).
     */
    fun <R> part(pick: (T) -> R): Stream<R> = stream.map(pick).distinct()

    /** Changes the section: kept, then shown. A change that changes nothing writes nothing. */
    fun update(change: (T) -> T) {
        val next = normalise(change(shown()))

        if (next != shown()) {
            kept(next)
        }
    }

    /** Forgets the section: nothing is kept under its key, and it shows its default. */
    fun reset() {
        storage.forget()
        shown(default)
    }
}
