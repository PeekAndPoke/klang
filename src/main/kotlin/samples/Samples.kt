package io.peekandpoke.samples

import io.peekandpoke.samples.decoders.MonoSamplePCM
import io.peekandpoke.samples.decoders.WavDecoder
import io.peekandpoke.utils.AssetLoader
import io.peekandpoke.utils.withDiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * Registry for resolving and decoding samples.
 *
 * Threading:
 * - safe to call from scheduler thread to prefetch
 * - DO NOT call from the audio render loop unless you know it's already loaded (see getIfLoaded)
 */
class Samples(
    private val index: Index,
    private val decoder: WavDecoder,
    private val loader: AssetLoader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    companion object {
        suspend fun createDefault(
            cacheDir: Path = Path.of("./cache"),
            assetLoader: AssetLoader = AssetLoader.default,
        ): Samples {

            val indexLoader = SampleIndexLoader(
                loader = assetLoader.withDiskCache(cacheDir.resolve("./index")),
            )

            val index: Index = indexLoader.load(SampleCatalogue.default)

            return Samples(
                index = index,
                decoder = WavDecoder(),
                loader = assetLoader.withDiskCache(cacheDir.resolve("./samples")),
            )
        }
    }

    /** A fully resolved variant (exact URL choice). */
    data class SampleId(
        val bank: String,
        val sound: String,
        val index: Int,
        val url: String,
    )

    data class Index(
        val defaultBank: String,
        val banks: List<Bank>,
        val aliases: Map<String, String> = emptyMap(),
    ) {
        val banksByName: Map<String, Bank> = banks.associateBy { it.name }

        // TODO: handle default strudel sound (no bank name)
        //   https://raw.githubusercontent.com/tidalcycles/uzu-drumkit/main/strudel.json
        //   should we handle this here or in the loader?

        fun resolve(reg: SampleRequest): SampleId? {
            val bankName = reg.bank ?: defaultBank
            val soundName = reg.sound ?: return null
            val bank = getBank(bankName) ?: return null
            val sounds = bank.getSounds(soundName)?.takeIf { it.isNotEmpty() } ?: return null
            val soundIdx = (reg.index ?: 0) % sounds.size
            val url = sounds[soundIdx]

            return SampleId(bank = bankName, sound = soundName, index = soundIdx, url = url)
        }

        fun getBank(name: String): Bank? {
            // Look directly first
            banksByName[name]?.let { return it }
            // Try to resolve by alias
            return aliases[name]?.let { resolved -> banksByName[resolved] }
        }
    }

    data class Bank(
        val name: String,
        val sounds: Map<String, List<String>> = emptyMap(),
    ) {
        val samplesByName: Map<String, List<String>> = sounds.entries.associate { it.key to it.value }

        fun getSounds(name: String): List<String>? {
            return samplesByName[name]
        }
    }

    private val sampleCache = mutableMapOf<SampleId, MonoSamplePCM?>()

    /**
     * Looks up the index for a given sample
     */
    fun hasSample(req: SampleRequest): Boolean = index.resolve(req) != null

    /**
     * Prefetch sample sound data when sound is in index.
     */
    fun prefetch(req: SampleRequest) {
        getIfLoaded(req)
    }

    /**
     * Gets a sound if it is already loaded
     */
    fun getIfLoaded(req: SampleRequest): MonoSamplePCM? {
        val sampleId = index.resolve(req) ?: return null

        sampleCache[sampleId]?.let { return it }

        scope.launch {
            sampleCache[sampleId] = loadAndDecode(sampleId)
        }

        return null
    }

    private suspend fun loadAndDecode(id: SampleId): MonoSamplePCM? {
        return loader.download(id.url)?.let {
            decoder.decodeMonoFloatPcm(it)
        }
    }
}
