package io.peekandpoke.samples

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Stores raw WAV bytes on disk.
 *
 * Project-local default suggested by you: ./cache/samples
 */
class DiskSampleStorage(
    private val dir: Path,
) : SampleStorage {

    init {
        Files.createDirectories(dir)
    }

    override suspend fun get(key: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            val p = pathFor(key)
            if (!Files.exists(p)) return@withContext null
            Files.readAllBytes(p)
        }
    }

    override suspend fun put(key: String, bytes: ByteArray) {
        return withContext(Dispatchers.IO) {
            val p = pathFor(key)
            val tmp = dir.resolve("$key.tmp")
            Files.createDirectories(dir)
            Files.write(tmp, bytes)
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun pathFor(key: String): Path =
        dir.resolve("$key.wav")
}
