package io.peekandpoke.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Disk-backed [UrlCache].
 *
 * Stores entries under ./cache/index (or wherever you point it).
 */
class DiskUrlCache(
    private val dir: Path,
) : UrlCache {

    init {
        Files.createDirectories(dir)
    }

    override suspend fun getOrPut(uri: URI, suffix: String, loader: suspend () -> ByteArray): ByteArray {
        val safeSuffix = suffix.trim().trimStart('.').ifEmpty { "bin" }
        val key = uri.toString().sha256Hex()
        val path = dir.resolve("$key.$safeSuffix")

        return withContext(Dispatchers.IO) {
            if (Files.exists(path)) {
                return@withContext Files.readAllBytes(path)
            }

            val bytes = loader()

            val tmp = dir.resolve("$key.tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)

            bytes
        }
    }
}


