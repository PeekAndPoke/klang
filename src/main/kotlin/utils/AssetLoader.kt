package io.peekandpoke.utils

import io.peekandpoke.cache.DiskUrlCache
import io.peekandpoke.cache.InMemoryUrlCache
import io.peekandpoke.cache.UrlCache
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

interface AssetLoader {
    companion object {
        val default by lazy { JavaAssetLoader() }
    }

    suspend fun download(uri: String): ByteArray?
}

fun AssetLoader.withDiskCache(dir: Path): AssetLoader {
    return CachingAssetLoader(
        cache = InMemoryUrlCache(
            DiskUrlCache(dir)
        ),
        delegate = this,
    )
}

class CachingAssetLoader(
    private val cache: UrlCache,
    private val delegate: AssetLoader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AssetLoader {
    private val inFlightOrLoaded = ConcurrentHashMap<String, Deferred<ByteArray?>>()

    override suspend fun download(uri: String): ByteArray? {
        // Exists?
        return cache.getOrPut(uri) {
            val deferred = inFlightOrLoaded.computeIfAbsent(uri) {
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

class JavaAssetLoader(
    val agentName: String = "Knödel",
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) : AssetLoader {
    override suspend fun download(uri: String): ByteArray? {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .GET()
            .header("User-Agent", agentName)
            .build()

        val res = client
            .sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
            .await()

        if (res.statusCode() !in 200..299) {
            throw IllegalStateException("Failed to download sample: $uri (HTTP ${res.statusCode()})")
        }

        return res.body()
    }
}
