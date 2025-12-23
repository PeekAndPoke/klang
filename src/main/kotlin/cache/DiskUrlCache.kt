package io.peekandpoke.cache

import io.peekandpoke.utils.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override suspend fun has(uri: String): Boolean {
        return withContext(Dispatchers.IO) {
            val path = getFilePath(uri)

            Files.exists(path)
        }
    }

    override suspend fun get(uri: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            val path = getFilePath(uri)

            if (!Files.exists(path)) return@withContext null

            Files.readAllBytes(path)
        }
    }

    override suspend fun getOrPut(uri: String, loader: suspend () -> ByteArray?): ByteArray? {
        return withContext(Dispatchers.IO) {
            val path = getFilePath(uri)

            if (Files.exists(path)) {
                return@withContext Files.readAllBytes(path)
            }

            // load
            val bytes = loader()

            // First write a tmp file, to avoid accidental access while file is still being downloaded
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            Files.write(tmp, bytes)
            // Move to final location
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)

            // return
            bytes
        }
    }

    private fun getFilePath(uri: String): Path {
        val safeSuffix = uri.substringAfterLast(".", "bin")
        val key = uri.sha256Hex()
        val path = dir.resolve("$key.$safeSuffix")

        return path
    }

}
