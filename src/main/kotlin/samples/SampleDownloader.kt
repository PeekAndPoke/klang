package io.peekandpoke.samples

import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SampleDownloader(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    suspend fun download(uri: URI): ByteArray {
        val req = HttpRequest.newBuilder(uri)
            .GET()
            .header("User-Agent", "knudel")
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
