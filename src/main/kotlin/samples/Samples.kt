package io.peekandpoke.samples

import io.peekandpoke.samples.decoders.MonoSamplePCM
import io.peekandpoke.samples.decoders.WavDecoder
import io.peekandpoke.utils.AssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    private val loader: AssetLoader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

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
        val defaultBank: String = "RolandTR909",
    )

    fun canResolve(req: SampleRequest): Boolean =
        resolveUrl(req) != null

    private val sampleCache = mutableMapOf<SampleId, MonoSamplePCM?>()

    /**
     * Prefetch into memory cache (and disk cache if configured).
     * Safe to call repeatedly; concurrent calls dedupe.
     */
    fun prefetch(req: SampleRequest) {
        getIfLoaded(req)
    }

    /**
     * Non-blocking check for audio thread:
     * returns decoded sample only if already available *now*.
     */
    fun getIfLoaded(req: SampleRequest): MonoSamplePCM? {
        val sampleId = resolveUrl(req) ?: return null

        sampleCache[sampleId]?.let { return it }

        scope.launch {
            sampleCache[sampleId] = loadAndDecode(sampleId)
        }

        return null
    }

    private fun resolveUrl(req: SampleRequest): SampleId? {
        val sound = req.sound ?: return null

        val bank = canonicalBank(req.bank ?: index.defaultBank)
        val sounds = index.banks[bank] ?: return null
        val variants = sounds[req.sound] ?: return null
        if (variants.isEmpty()) return null

        val idx = (req.index ?: 0).floorMod(variants.size)
        // Slight URL encode ... javas URLEncoder is broken
        val url = variants[idx]

//        println(url)

        return SampleId(bank = bank, sound = sound, index = idx, url = url)
    }

    private fun canonicalBank(bank: String): String = index.bankAliases[bank] ?: bank

    private suspend fun loadAndDecode(id: SampleId): MonoSamplePCM? {
        return loader.download(id.url)?.let {
            decoder.decodeMonoFloatPcm(it)
        }
    }

    private fun Int.floorMod(mod: Int): Int {
        val r = this % mod
        return if (r < 0) r + mod else r
    }
}
