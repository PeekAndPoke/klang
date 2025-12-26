package io.peekandpoke.klang.audio_fe.cache

import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.NoSuchFileException
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
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: NoSuchFileException) {
            }

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

    /**
     * Calc sha256 hash from string
     */
    private fun String.sha256Hex(): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(this.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) sb.append("%02x".format(b))
        return sb.toString()
    }
}
