package io.peekandpoke.samples

import io.peekandpoke.utils.sha256Hex
import kotlinx.coroutines.*
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * PCM decoded sample ready for mixing.
 *
 * MVP:
 * - mono float PCM
 * - sampleRate matches renderer sampleRate (resampling can be added later)
 */
class DecodedSample(
    val sampleRate: Int,
    val pcm: FloatArray,
)

/** What the pattern asks for (bank + sound + optional variant index). */
data class SampleRequest(
    val bank: String?,
    val sound: String,
    val index: Int? = null,
)

/** A fully resolved variant (exact URL choice). */
data class SampleId(
    val bank: String,
    val sound: String,
    val index: Int,
    val url: String,
)

data class SampleBankIndex(
    /** e.g. "RolandTR909" -> ("bd" -> [url0, url1, ...]) */
    val banks: Map<String, Map<String, List<String>>>,
    /** optional alias mapping, e.g. "909" -> "RolandTR909" */
    val bankAliases: Map<String, String> = emptyMap(),
    /** used when request.bank is null */
    val defaultBank: String? = null,
)

interface SampleStorage {
    /** Return WAV bytes if present, else null. */
    suspend fun get(key: String): ByteArray?

    /** Store WAV bytes. */
    suspend fun put(key: String, bytes: ByteArray)
}

/**
 * Registry for resolving and decoding samples.
 *
 * Threading:
 * - safe to call from scheduler thread to prefetch
 * - DO NOT call from the audio render loop unless you know it's already loaded (see getIfLoaded)
 */
class SampleRegistry(
    private val index: SampleBankIndex,
    private val decoder: WavDecoder,
    private val downloader: SampleDownloader,
    private val storage: SampleStorage? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val inFlightOrLoaded = ConcurrentHashMap<SampleId, Deferred<DecodedSample>>()

    fun canResolve(req: SampleRequest): Boolean =
        resolveUrl(req) != null

    /**
     * Prefetch into memory cache (and disk cache if configured).
     * Safe to call repeatedly; concurrent calls dedupe.
     */
    fun prefetch(req: SampleRequest): Deferred<DecodedSample> {
        val resolved = resolveUrl(req)
            ?: return CompletableDeferred(
                DecodedSample(sampleRate = 0, pcm = FloatArray(0))
            ).also {
                (it as CompletableDeferred).completeExceptionally(
                    IllegalArgumentException("Unknown sample: bank=${req.bank} sound=${req.sound}")
                )
            }

        return inFlightOrLoaded.computeIfAbsent(resolved) {
            scope.async {
                loadAndDecode(resolved)
            }.also { d ->
                d.invokeOnCompletion { t ->
                    if (t != null) {
                        inFlightOrLoaded.remove(resolved)
                    }
                }
            }
        }
    }

    /**
     * Non-blocking check for audio thread:
     * returns decoded sample only if already available *now*.
     */
    fun getIfLoaded(req: SampleRequest): DecodedSample? {
        val resolved = resolveUrl(req) ?: return null
        val d = inFlightOrLoaded[resolved] ?: return null
        return if (d.isCompleted && !d.isCancelled) d.getCompleted() else null
    }

    private fun resolveUrl(req: SampleRequest): SampleId? {
        val bank = canonicalBank(req.bank ?: index.defaultBank ?: return null) ?: return null
        val sounds = index.banks[bank] ?: return null
        val variants = sounds[req.sound] ?: return null
        if (variants.isEmpty()) return null

        val idx = (req.index ?: 0).floorMod(variants.size)
        val url = variants[idx]

        return SampleId(bank = bank, sound = req.sound, index = idx, url = url)
    }

    private fun canonicalBank(bank: String): String? =
        index.bankAliases[bank] ?: bank

    private suspend fun loadAndDecode(id: SampleId): DecodedSample {
        val cacheKey = cacheKeyFor(id.url)

        val wavBytes = storage?.get(cacheKey) ?: run {
            val bytes = downloader.download(URI.create(id.url))
            storage?.put(cacheKey, bytes)
            bytes
        }

        return decoder.decodeMonoFloatPcm(wavBytes)
    }

    private fun cacheKeyFor(url: String): String =
        "wav_" + url.sha256Hex()

    private fun Int.floorMod(mod: Int): Int {
        val r = this % mod
        return if (r < 0) r + mod else r
    }
}
