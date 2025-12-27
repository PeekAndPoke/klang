package io.peekandpoke.klang.audio_fe.utils

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.peekandpoke.klang.audio_fe.cache.UrlCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AssetLoader {
    companion object {
        val default by lazy { KtorAssetLoader() }
    }

    suspend fun download(uri: String): ByteArray?
}

class KtorAssetLoader(
    private val client: HttpClient = HttpClient {
        followRedirects = true
    },
) : AssetLoader {
    override suspend fun download(uri: String): ByteArray? {
        val response = client.get(uri) {
//            header(HttpHeaders.UserAgent, agentName)
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to download sample: $uri (HTTP ${response.status.value})")
        }

        return response.readRawBytes()
    }
}

class CachingAssetLoader(
    private val cache: UrlCache,
    private val delegate: AssetLoader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AssetLoader {
    private val lock = Mutex()

    private val inFlightOrLoaded = mutableMapOf<String, Deferred<ByteArray?>>()

    override suspend fun download(uri: String): ByteArray? = lock.withLock {
        // Exists?
        return cache.getOrPut(uri) {
            val deferred = inFlightOrLoaded.getOrPut(uri) {
                scope.async {
                    delegate.download(uri)
                }.also { d ->
                    d.invokeOnCompletion {
                        inFlightOrLoaded.remove(uri).let { /* noop */ }
                    }
                }
            }

            deferred.await()
        }
    }
}
