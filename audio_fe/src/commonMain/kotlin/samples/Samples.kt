package io.peekandpoke.klang.audio_fe.samples

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
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
class Samples(
    private val index: Index,
    private val decoder: AudioDecoder,
    private val loader: AssetLoader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    companion object {
        // used for platform specific extension functions
    }

    interface SoundProvider {
        val key: String

        suspend fun provide(request: SampleRequest): ResolvedSample?
    }

    /** A fully resolved variant (exact URL choice). */
    data class ResolvedSample(
        val request: SampleRequest,
        val sample: Sample,
    )

    data class Index(
        val banks: List<Bank>,
        val aliases: Map<String, String> = emptyMap(),
    ) {
        val banksByKey: Map<String, Bank> = banks.associateBy { it.key }

        suspend fun resolve(request: SampleRequest): ResolvedSample? {
            // Resolve the sample provider (taking aliases into account)
            // Note there is a special bank with an empty name ""
            val resolvedBank = resolveBank(request.bank ?: "") ?: return null
            // Get the sound provider
            val sound = resolvedBank.getSound(request.sound ?: "") ?: return null
            // Try to provide the sound
            return sound.provide(request)
        }

        private fun resolveBank(key: String, visitedAliases: List<String> = emptyList()): Bank? {
            if (key in visitedAliases) {
                println("[Samples] Bank '$key' has an alias loop: ${visitedAliases.joinToString(" -> ")}.")
                return null
            }

            // Do we have a bank with this key?
            return when (val bank = banksByKey[key]) {
                // No? Check aliases
                null -> aliases[key]?.let { resolveBank(it, visitedAliases + key) }
                // Else try to load
                else -> bank
            }
        }
    }

    data class Bank(
        val key: String,
        val sounds: List<SoundProvider> = emptyList(),
    ) {
        val soundsByKey: Map<String, SoundProvider> = sounds.associateBy { it.key }

        fun getSound(key: String): SoundProvider? {
            return soundsByKey[key]
        }

        fun plusSound(sound: SoundProvider) = copy(
            sounds = sounds.plus(sound).distinctBy { it.key }
        )

        fun plusSounds(sounds: List<SoundProvider>) = copy(
            sounds = this.sounds.plus(sounds).distinctBy { it.key }
        )
    }

    data class Sample(
        /** Optional informal note name ... not used for anything */
        val note: String?,
        /** The pitch of this sound in Hz. Use for playing this sound at different tones */
        val pitchHz: Double,
        /** The url for loading the sound files */
        val url: String,
    )

    private val sampleCache = mutableMapOf<ResolvedSample, MonoSamplePcm?>()

    /**
     * Looks up the index for a given sample
     */
    suspend fun hasSample(request: SampleRequest): Boolean = index.resolve(request) != null

    /**
     * Gets a sound if it is already loaded
     */
    suspend fun getIfLoaded(request: SampleRequest): Pair<ResolvedSample, MonoSamplePcm>? {
        val sampleId = index.resolve(request) ?: return null

        sampleCache[sampleId]?.let { return sampleId to it }

        scope.launch {
            sampleCache[sampleId] = loadAndDecode(sampleId)
        }

        return null
    }

    /**
     * Gets a sound and calls the [callback] with the result
     */
    suspend fun getWithCallback(request: SampleRequest, callback: (Pair<Sample, MonoSamplePcm?>?) -> Unit) {
        val resolved = index.resolve(request) ?: return callback(null)

        scope.launch {
            val pcm = sampleCache.getOrPut(resolved) { loadAndDecode(resolved) }
            val result = resolved.sample to pcm

            callback(result)
        }
    }

    /**
     * Loads and decodes a sample.
     *
     * If the sample cannot be loaded or decoded, null is returned.
     */
    private suspend fun loadAndDecode(id: ResolvedSample): MonoSamplePcm? {
        return loader.download(id.sample.url)?.let {
            try {
                decoder.decodeMonoFloatPcm(it)
            } catch (e: Exception) {
                println("Failed to decode sample ${id.sample.url}: ${e.stackTraceToString()}")
                null
            }
        }
    }
}
