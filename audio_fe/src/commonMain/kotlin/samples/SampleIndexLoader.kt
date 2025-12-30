package io.peekandpoke.klang.audio_fe.samples

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

    suspend fun load(
        library: SampleCatalogue,
    ): Samples.Index = coroutineScope {

        val collectedBanks = mutableMapOf<String, Samples.BankProvider>()
        val collectedAliases = mutableMapOf<String, String>()

        library.sources.forEach { source ->
            val result = when (source) {
                is SampleCatalogue.Bundle -> genericBundleLoader.load(source)
            }

            collectedBanks.putAll(result.banks)
            collectedAliases.putAll(result.aliases)
        }

        // return
        Samples.Index(
            banks = collectedBanks.values.toList(),
            aliases = collectedAliases.toMap(),
        )
    }

    private data class LoadResult(
        val banks: Map<String, Samples.BankProvider>,
        val aliases: Map<String, String>,
    )

    private class GenericBundleLoader(private val loader: AssetLoader, private val json: Json = Json) {
        suspend fun load(bundle: SampleCatalogue.Bundle): LoadResult {

            val collectedBanks = mutableMapOf<String, Samples.Bank>()
            val collectedAliases = mutableMapOf<String, String>()

            run {
                val loaded = loader.download(bundle.soundsUri)?.decodeToString()
                // Load the banks data
                loaded?.let { parseSoundsFile(bundle.defaultPitchHz, it) }?.let { parsed ->
                    // merge with existing data
                    parsed.forEach { bank ->
                        val existingBank = collectedBanks[bank.key] ?: Samples.Bank(bank.key)
                        collectedBanks[bank.key] = existingBank.plusSounds(sounds = bank.sounds)
                    }
                }
            }

            bundle.aliasUris.forEach { alias ->
                val loaded = loader.download(alias)?.decodeToString()

                loaded?.let { parseAliasFile(it) }?.let { parsed ->
                    collectedAliases += parsed
                }
            }

            return LoadResult(
                banks = collectedBanks.mapValues { (key, bank) ->
                    Samples.BankProvider.Instant(key, bank)
                },
                aliases = collectedAliases,
            )
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

            val base = obj["_base"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.trimEnd('/')
                ?.let { "$it/" }
                ?: ""

            val banks = mutableMapOf<String, Samples.Bank>()

            for ((key, value) in obj) {
                if (key == "_base") continue

                val (key, soundName) = splitBankAndSound(key)

                val sound: Samples.Sound? = when (value) {
                    // Is this a bank based pattern?
                    is JsonArray -> {
                        val samples = value.mapNotNull { it.asStringOrNull()?.sanitizeUrl(base) }
                            .map { url -> Samples.Sample(note = null, pitchHz = defaultPitchHz, url = url) }

                        Samples.Sound(key = soundName, samples = samples)
                    }

                    // Is this a sound with different pitches?
                    is JsonObject -> {
                        val samples = value.mapNotNull {
                            val note = it.key
                            val pitch = Tones.noteToFreq(note)
                            val url = it.value.asStringOrNull()?.sanitizeUrl(base) ?: return@mapNotNull null

                            Samples.Sample(note = note, pitchHz = pitch, url = url)
                        }

                        Samples.Sound(key = soundName, samples = samples)
                    }

                    else -> null
                }

                // Add the sound to the bank
                sound?.let {
                    banks[key] = (banks[key] ?: Samples.Bank(key)).plusSounds(listOf(sound))
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
