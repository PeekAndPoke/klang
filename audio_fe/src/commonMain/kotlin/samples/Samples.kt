package io.peekandpoke.klang.audio_fe.samples

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
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
    private val loader: AssetLoader,
    private val decoder: AudioDecoder,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    companion object {
        // used for platform specific extension functions
    }

    interface SoundProvider {
        val key: String

        suspend fun provide(request: SampleRequest): ResolvedSample?
    }

    /** A [sample] that was resolved from the [request]. */
    data class ResolvedSample(
        val request: SampleRequest,
        val sample: Sample,
    )

    /** A [sample] that was resolved from the [request] including the [pcm] audio data. */
    data class LoadedSample(
        val request: SampleRequest,
        val sample: Sample,
        val pcm: MonoSamplePcm?,
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

    sealed interface Sample {
        class FromUrl(
            override val note: String?,
            override val pitchHz: Double,
            val url: String,
        ) : Sample {
            override suspend fun getPcm(loader: AssetLoader, decoder: AudioDecoder): MonoSamplePcm? {
                println("Loading sample $url")

                return loader.download(url)?.let {
                    try {
                        decoder.decodeMonoFloatPcm(it)
                    } catch (e: Exception) {
                        println("Failed to decode sample $url: ${e.stackTraceToString()}")
                        null
                    }
                }
            }
        }

        class FromBytes(
            override val note: String?,
            override val pitchHz: Double,
            val sampleRate: Int,
            val bytes: ByteArray,
            val meta: SampleMetadata,
        ) : Sample {
            private var _pcm: MonoSamplePcm? = null

            override suspend fun getPcm(loader: AssetLoader, decoder: AudioDecoder): MonoSamplePcm? {
                return _pcm ?: getPcmInternal(decoder)?.also { _pcm = it }
            }

            private suspend fun getPcmInternal(decoder: AudioDecoder): MonoSamplePcm? {
                return decoder.decodeMonoFloatPcm(bytes)?.withMetadata(meta)
            }
        }

        /** Optional informal note name ... not used for anything */
        val note: String?

        /** The pitch of this sound in Hz. Use for playing this sound at different tones */
        val pitchHz: Double

        /** The pcm audio data */
        suspend fun getPcm(loader: AssetLoader, decoder: AudioDecoder): MonoSamplePcm?
    }

    private val resolveCache = mutableMapOf<SampleRequest, ResolvedSample?>()
    private val loadCache = mutableMapOf<SampleRequest, LoadedSample?>()

    /**
     * Resolve a sample by the given [request].
     */
    suspend fun resolve(request: SampleRequest): ResolvedSample? {
        return resolveCache.getOrPut(request) { index.resolve(request) }
    }

    /**
     * Resolved a sample by the given [request] and loads it pcm audio data.
     */
    suspend fun get(request: SampleRequest): LoadedSample? {

        return loadCache.getOrPut(request) {
            val resolved = resolveCache.getOrPut(request) {
                index.resolve(request)
            }

            resolved?.let {
                val pcm = resolved.sample.getPcm(loader, decoder)

                LoadedSample(
                    request = resolved.request,
                    sample = resolved.sample,
                    pcm = pcm,
                )
            }
        }
    }

    /**
     * Gets a sound and calls the [callback] with the result
     */
    fun getWithCallback(request: SampleRequest, callback: (LoadedSample?) -> Unit) {
        scope.launch {
            val resolved = get(request)
            callback(resolved)
        }
    }
}
