/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe.cache

/**
 * Generic cache for "URL -> bytes".
 *
 * We'll use it for caching:
 * - sample map JSON
 * - alias JSON
 *
 * (Separately, WAV caching is handled by SampleStorage / DiskSampleStorage.)
 */
interface UrlCache {
    suspend fun has(uri: String): Boolean

    suspend fun get(uri: String): ByteArray?

    suspend fun getOrPut(uri: String, loader: suspend () -> ByteArray?): ByteArray?
}
