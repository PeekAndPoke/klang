package io.peekandpoke.klang.samples

import io.peekandpoke.klang.tones.Tones
import io.peekandpoke.klang.utils.AssetLoader
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

    /** A fully resolved variant (exact URL choice). */
    data class SampleId(
        val request: SampleRequest,
        val sample: Sample,
    )

    data class Index(
        val banks: List<Bank>,
        val aliases: Map<String, String> = emptyMap(),
    ) {
        val banksByName: Map<String, Bank> = banks.associateBy { it.name }

        fun resolve(request: SampleRequest): SampleId? {
            // There is a special "no name" bank for all sounds
            val bankName = request.bank ?: ""
            // No sound name ... no music
            val soundName = request.sound ?: return null
            // No bank ... no music
            val bank = getBank(bankName) ?: return null
            // No sound ... no music
            val sound = bank.getSound(soundName)?.takeIf { it.samples.isNotEmpty() } ?: return null

            val sample = when (val note = request.note) {
                null -> {
                    // No frequency ... pick by given sound index or the first one
                    sound.getSampleByIndex(request.index ?: 0)
                }

                else -> {
                    // Note given ... pick the best sample
                    sound.getSampleByNote(note)
                }
            }

            // no sample ... no music
            sample ?: return null

            return SampleId(request = request, sample = sample)
        }

        fun getBank(name: String?): Bank? {
            // Look directly first
            banksByName[name]?.let { return it }
            // Try to resolve by alias
            return aliases[name]?.let { resolved -> banksByName[resolved] }
        }
    }

    data class Bank(
        val name: String,
        val sounds: List<Sound> = emptyList(),
    ) {
        val samplesByName: Map<String, Sound> = sounds.associateBy { it.key }

        fun getSound(name: String): Sound? {
            return samplesByName[name]
        }

        fun plusSounds(sounds: List<Sound>) = copy(
            sounds = this.sounds.plus(sounds).distinctBy { it.key }
        )
    }

    data class Sound(
        /** The to look up the sound ... case sensitive */
        val key: String,
        /** The samples associated with this sound */
        val samples: List<Sample>,
    ) {
        val samplesSortedByPitch: List<Sample> = samples.sortedBy { it.pitchHz }

        fun getSampleByIndex(idx: Int): Sample? {
            return samples.getOrNull(idx % samples.size)
        }

        fun getSampleByPitch(pitch: Double): Sample? {
            return samplesSortedByPitch.firstOrNull { pitch <= it.pitchHz }
                ?: samplesSortedByPitch.lastOrNull()
        }

        fun getSampleByNote(note: String): Sample? {
            val pitchHz = Tones.noteToFreq(note)

            return getSampleByPitch(pitchHz)
        }
    }

    data class Sample(
        /** Optional informal note name ... not used for anything */
        val note: String?,
        /** The pitch of this sound in Hz. Use for playing this sound at different tones */
        val pitchHz: Double,
        /** The url for loading the sound files */
        val url: String,
    )

    private val sampleCache = mutableMapOf<SampleId, MonoSamplePcm?>()

    /**
     * Looks up the index for a given sample
     */
    fun hasSample(request: SampleRequest): Boolean = index.resolve(request) != null

    /**
     * Prefetch sample sound data when sound is in index.
     */
    fun prefetch(request: SampleRequest) {
        getIfLoaded(request)
    }

    /**
     * Gets a sound if it is already loaded
     */
    fun getIfLoaded(request: SampleRequest): Pair<SampleId, MonoSamplePcm>? {
        val sampleId = index.resolve(request) ?: return null

        sampleCache[sampleId]?.let { return sampleId to it }

        scope.launch {
            sampleCache[sampleId] = loadAndDecode(sampleId)
        }

        return null
    }

    private suspend fun loadAndDecode(id: SampleId): MonoSamplePcm? {
        return loader.download(id.sample.url)?.let {
            decoder.decodeMonoFloatPcm(it)
        }
    }
}
