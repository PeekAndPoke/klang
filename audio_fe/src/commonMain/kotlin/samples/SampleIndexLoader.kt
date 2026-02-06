package io.peekandpoke.klang.audio_fe.samples

import io.ktor.util.*
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_fe.samples.Samples.ResolvedSample
import io.peekandpoke.klang.audio_fe.samples.Samples.Sample
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.audio_fe.utils.isUrlWithProtocol
import io.peekandpoke.klang.tones.Tones
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*

class SampleIndexLoader(
    loader: AssetLoader,
    json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    private val genericBundleLoader = GenericBundleLoader(loader, json)
    private val soundFontLoader = SoundFontLoader(loader, json)

    suspend fun load(
        library: SampleCatalogue,
    ): Samples.Index = coroutineScope {

        val banks = mutableMapOf<String, Samples.Bank>()
        val aliases = mutableMapOf<String, String>()

        library.sources.forEach { source ->
            try {
                println("Loading sample source: ${source.name}")

                val result = when (source) {
                    is SampleCatalogue.Bundle -> genericBundleLoader.load(source)
                    is SampleCatalogue.Soundfont -> soundFontLoader.load(source)
                }

                val numBanks = result.banks.size
                val numSounds = result.banks.values.sumOf { it.sounds.size }
                val numAliases = result.aliases.size

                println(
                    "Loaded sample source: ${source.name} ... " +
                            "Found $numBanks banks, $numSounds sounds, $numAliases aliases"
                )

                // Merge all banks
                result.banks.values.forEach { bank ->
                    val existing = banks[bank.key] ?: Samples.Bank(bank.key)
                    banks[bank.key] = existing.plusSounds(bank.sounds)
                }

                // Collect aliases
                aliases.putAll(result.aliases)

            } catch (e: Exception) {
                println("Could not load sample source: ${source.name}")
                e.printStackTrace()
            }
        }

        // return
        Samples.Index(
            banks = banks.values.toList(),
            aliases = aliases.toMap(),
        )
    }

    private data class LoadResult(
        val banks: Map<String, Samples.Bank>,
        val aliases: Map<String, String>,
    )

    private class SoundFontLoader(
        private val loader: AssetLoader,
        private val json: Json,
    ) {
        private inner class Provider(
            override val key: String,
            private val index: SoundfontIndex,
            private val variants: List<SoundfontIndex.Variant>,
        ) : Samples.SoundProvider {
            override suspend fun provide(request: SampleRequest): ResolvedSample? {
                if (variants.isEmpty()) return null

                val variantIndex = ((request.index ?: 0) % variants.size)
                val variant = variants[variantIndex]
                val variantData = loadVariantData(variant) ?: return null
                val zones = variantData.zones
                    .takeIf { it.isNotEmpty() }
                    ?.sortedBy { it.originalPitch }
                    ?: return null

                val requestedPitch = Tones.noteToFreq(request.note ?: "")

                val selected = zones
                    .firstOrNull { Tones.midiToFreq(it.originalPitch / 100.0) >= requestedPitch }
                    ?: zones.last()

                val bytes = selected.file.decodeBase64Bytes()

                val sample = Sample.FromBytes(
                    note = request.note,
                    pitchHz = Tones.midiToFreq(selected.originalPitch / 100.0),
                    sampleRate = selected.sampleRate,
                    bytes = bytes,
                    meta = selected.getSampleMetadata()
                )

                return ResolvedSample(
                    request = request,
                    sample = sample,
                )
            }

            private suspend fun loadVariantData(variant: SoundfontIndex.Variant): SoundfontIndex.SoundData? {

                val detailsUrl = index.baseUrl.trimEnd('/') + '/' + variant.file.trimStart('/')
                val loaded = loader.download(detailsUrl)?.decodeToString()
                    ?: return null

                return try {
                    json.decodeFromString<SoundfontIndex.SoundData>(loaded)
                } catch (e: Exception) {
                    println("Could not load soundfont: $detailsUrl \n${e.stackTraceToString()}")
                    null
                }
            }
        }

        suspend fun load(bundle: SampleCatalogue.Soundfont): LoadResult {

            val content = loader.download(bundle.indexUrl)?.decodeToString()
                ?: error("Soundfont index not found: ${bundle.indexUrl}")

            val decoded = json.decodeFromString<Map<String, List<SoundfontIndex.Variant>>>(content)

            val index = SoundfontIndex(name = bundle.name, baseUrl = bundle.baseUrl, entries = decoded)

            val sounds = index.entries.map { (soundKey, variants) ->
                Provider(key = soundKey, index = index, variants = variants)
            }

            val bankKey = ""
            val bank = Samples.Bank(key = bankKey, sounds = sounds)

            return LoadResult(banks = mapOf(bankKey to bank), aliases = emptyMap())
        }
    }

    private class GenericBundleLoader(
        private val loader: AssetLoader,
        private val json: Json,
    ) {
        private class Provider(
            override val key: String,
            val sound: Sound,
        ) : Samples.SoundProvider {
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

            override suspend fun provide(request: SampleRequest): ResolvedSample? {
                if (sound.samples.isEmpty()) return null

                // No sample ... no music
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

                return ResolvedSample(request = request, sample = sample)
            }
        }

        suspend fun load(bundle: SampleCatalogue.Bundle): LoadResult {

            val collectedBanks = mutableMapOf<String, Samples.Bank>()
            val collectedAliases = mutableMapOf<String, String>()

            run {
                val loaded = loader.download(bundle.soundsUri)?.decodeToString()
                // Load the banks data
                loaded?.let { parseSoundsFile(bundle.defaultPitchHz, it) }?.let { banks ->
                    // Merge all
                    banks.forEach { bank ->
                        val exising = collectedBanks[bank.key] ?: Samples.Bank(bank.key)
                        collectedBanks[bank.key] = exising.plusSounds(bank.sounds)
                    }
                }
            }

            bundle.aliasUris.forEach { alias ->
                val loaded = loader.download(alias)?.decodeToString()

                loaded?.let { parseAliasFile(it) }?.let { parsed ->
                    collectedAliases += parsed
                }
            }

            return LoadResult(banks = collectedBanks, aliases = collectedAliases)
        }

        /**
         * Alias JSON format:
         * {
         *   "AkaiMPC60": "MPC60",
         *   "BossDR55": "DR55",
         *   ...
         * }
         *
         * We invert it to:
         * "MPC60" -> "AkaiMPC60"
         */
        private fun parseAliasFile(jsonText: String): Map<String, String> {
            val root = json.parseToJsonElement(jsonText)
            val obj = root as? JsonObject
                ?: throw IllegalArgumentException("Alias JSON must be an object")

            val out = linkedMapOf<String, String>()

            for ((canonical, aliasEl) in obj) {
                val alias = (aliasEl as? JsonPrimitive)?.contentOrNull?.trim()
                if (alias.isNullOrBlank()) continue

                // alias -> canonical
                out[alias] = canonical
            }

            return out
        }

        /**
         * Parse bank file.
         *
         * For percussive banks the shape is:
         * https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json
         *
         * {
         *   "_base": "https://raw.githubusercontent.com/ritchse/tidal-drum-machines/main/machines/",
         *   "AJKPercusyn_bd": ["AJKPercusyn/ajkpercusyn-bd/Bassdrum.wav"],
         *   "AJKPercusyn_cb": [
         *     "AJKPercusyn/ajkpercusyn-cb/Cowbell.wav",
         *     "AJKPercusyn/ajkpercusyn-cb/Snarepop.wav"
         *   ],
         *   "AJKPercusyn_ht": ["AJKPercusyn/ajkpercusyn-ht/Tom.wav"],
         *   "AJKPercusyn_sd": ["AJKPercusyn/ajkpercusyn-sd/Noise.wav"],
         *   "AkaiLinn_bd": ["AkaiLinn/akailinn-bd/Bassdrum.wav"],
         *   "AkaiLinn_cb": ["AkaiLinn/akailinn-cb/Cowbell.wav"],
         *   "AkaiLinn_cp": ["AkaiLinn/akailinn-cp/Clap.wav"],
         *   "AkaiLinn_cr": ["AkaiLinn/akailinn-cr/Crash.wav"],
         *   "AkaiLinn_hh": ["AkaiLinn/akailinn-hh/Closed Hat.wav"],
         *   "AkaiLinn_ht": ["AkaiLinn/akailinn-ht/Tom H.wav"],
         *   "AkaiLinn_lt": ["AkaiLinn/akailinn-lt/Tom L.wav"],
         *   "AkaiLinn_mt": ["AkaiLinn/akailinn-mt/Tom M.wav"],
         *   "AkaiLinn_oh": ["AkaiLinn/akailinn-oh/Open Hat.wav"],
         *   "AkaiLinn_rd": ["AkaiLinn/akailinn-rd/Ride.wav"],
         *   "AkaiLinn_sd": ["AkaiLinn/akailinn-sd/SD.wav"],
         *   "AkaiLinn_sh": ["AkaiLinn/akailinn-sh/Shuffle.wav"],
         *   "AkaiLinn_tb": ["AkaiLinn/akailinn-tb/Tambourin.wav"],
         *   "AkaiMPC60_bd": [
         *     "AkaiMPC60/akaimpc60-bd/0 Bassdrum.wav",
         *     "AkaiMPC60/akaimpc60-bd/Bassdrum Gated.wav"
         *   ],
         *   ...
         * }
         *
         * Then there a banks that have multiple sample for different pitches like this one:
         * https://raw.githubusercontent.com/felixroos/dough-samples/main/piano.json
         *
         * {
         *   "_base": "https://raw.githubusercontent.com/felixroos/dough-samples/main/piano/",
         *   "piano": {
         *     "A0": "A0v8.mp3",
         *     "C1": "C1v8.mp3",
         *     "Ds1": "Ds1v8.mp3",
         *     "Fs1": "Fs1v8.mp3",
         *     "A1": "A1v8.mp3",
         *     "C2": "C2v8.mp3",
         *     "Ds2": "Ds2v8.mp3",
         *     "Fs2": "Fs2v8.mp3",
         *     "A2": "A2v8.mp3",
         *     "C3": "C3v8.mp3",
         *     "Ds3": "Ds3v8.mp3",
         *     "Fs3": "Fs3v8.mp3",
         *     "A3": "A3v8.mp3",
         *     "C4": "C4v8.mp3",
         *     "Ds4": "Ds4v8.mp3",
         *     "Fs4": "Fs4v8.mp3",
         *     "A4": "A4v8.mp3",
         *     "C5": "C5v8.mp3",
         *     "Fs5": "Fs5v8.mp3",
         *     "A5": "A5v8.mp3",
         *     "C6": "C6v8.mp3",
         *     "Ds6": "Ds6v8.mp3",
         *     "Fs6": "Fs6v8.mp3",
         *     "A6": "A6v8.mp3",
         *     "C7": "C7v8.mp3",
         *     "Ds7": "Ds7v8.mp3",
         *     "Fs7": "Fs7v8.mp3",
         *     "A7": "A7v8.mp3",
         *     "C8": "C8v8.mp3"
         *   }
         * }
         *
         * TODO: what other kinds of shapes are there?
         */
        private fun parseSoundsFile(defaultPitchHz: Double, jsonText: String): List<Samples.Bank> {
            val root = json.parseToJsonElement(jsonText)
            val obj = root as? JsonObject
                ?: throw IllegalArgumentException("Sample map JSON must be an object")

            // Get base url for sound files
            val base = obj["_base"]?.jsonPrimitive?.contentOrNull
                ?.trim()?.trimEnd('/')?.let { "$it/" }
                ?: ""

            val banks = mutableMapOf<String, Samples.Bank>()

            for ((key, value) in obj) {
                if (key == "_base") continue

                val (bankKey, soundKey) = splitBankAndSound(key)

                val sound: Provider.Sound? = when (value) {
                    // Is this a bank based pattern?
                    is JsonArray -> {
                        val samples = value
                            .mapNotNull { it.asStringOrNull()?.sanitizeUrl(base) }
                            .map { url ->
                                Sample.FromUrl(note = null, pitchHz = defaultPitchHz, url = url)
                            }

                        Provider.Sound(key = soundKey, samples = samples)
                    }

                    // Is this a sound with different pitches?
                    is JsonObject -> {
                        val samples = value.mapNotNull {
                            val note = it.key
                            val pitch = Tones.noteToFreq(note)
                            val url = it.value.asStringOrNull()?.sanitizeUrl(base) ?: return@mapNotNull null

                            Sample.FromUrl(note = note, pitchHz = pitch, url = url)
                        }

                        Provider.Sound(key = soundKey, samples = samples)
                    }

                    else -> null
                }

                // Add the sound to the bank
                sound?.let {
                    banks[bankKey] = (banks[bankKey] ?: Samples.Bank(bankKey))
                        .plusSound(Provider(key = soundKey, sound))
                }
            }

            return banks.values.toList()
        }

        private fun String.sanitizeUrl(base: String) = if (isUrlWithProtocol()) {
            this
        } else {
            base.trimEnd('/') + '/' + trimStart('/')
        }.replace(" ", "%20") // simple URL encode

        private fun splitBankAndSound(key: String): Pair<String, String> {

            val parts = key.split('_')
            val sound = parts.last()
            val bank = parts.dropLast(1).joinToString("_")

            return bank to sound
        }

        private fun JsonElement.asStringOrNull(): String? =
            (this as? JsonPrimitive)?.contentOrNull
    }
}
