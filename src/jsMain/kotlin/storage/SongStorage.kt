/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.persistInLocalStorage
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * What the browser keeps of one song: its code, title and rpm, and its bookmarks (KLANG-00323).
 *
 * Each is a stream that starts from the value stored under its key ([StorageKeys], by the app's [version] and the
 * [songId]) and writes every value it is given back to the localStorage; a song that was never stored starts from the
 * defaults. The page decides when to write: the code on Play and on a reset, the title and rpm on every change, the
 * bookmarks on every change.
 */
class SongStorage(
    version: String,
    songId: String,
    defaultCode: String,
    defaultTitle: String,
    defaultRpm: Double,
) {
    /** The code, as last played. */
    val code: StreamSource<String> = StreamSource(defaultCode)
        .persistInLocalStorage(StorageKeys.songCode(version = version, songId = songId), String.serializer())

    val title: StreamSource<String> = StreamSource(defaultTitle)
        .persistInLocalStorage(StorageKeys.songTitle(version = version, songId = songId), String.serializer())

    val rpm: StreamSource<Double> = StreamSource(defaultRpm)
        .persistInLocalStorage(StorageKeys.songRpm(version = version, songId = songId), Double.serializer())

    /** The bookmarks, digit to line number (counted from 1); none for a song that was never stored. */
    val bookmarks: StreamSource<Map<Int, Int>> = StreamSource(emptyMap<Int, Int>())
        .persistInLocalStorage(
            StorageKeys.songBookmarks(version = version, songId = songId),
            MapSerializer(Int.serializer(), Int.serializer()),
        )
}
