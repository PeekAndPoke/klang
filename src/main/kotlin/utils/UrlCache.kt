package io.peekandpoke.utils

import java.net.URI

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
    suspend fun getOrPut(uri: URI, suffix: String, loader: suspend () -> ByteArray): ByteArray
}

suspend fun UrlCache.getOrPutJson(uri: URI, loader: suspend () -> ByteArray): ByteArray =
    getOrPut(uri, "json", loader)
