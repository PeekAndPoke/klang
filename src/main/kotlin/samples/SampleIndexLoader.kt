package io.peekandpoke.samples

import io.peekandpoke.utils.AssetLoader
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import java.nio.charset.StandardCharsets

class SampleIndexLoader(
    private val loader: AssetLoader,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun load(
        library: SampleCatalogue,
    ): Samples.Index = coroutineScope {

        val banks = mutableMapOf<String, Samples.Bank>()

        library.banks.forEach { bank ->
            run {
                val sampleMapBytes = loader.download(bank.soundsUri)?.toString(StandardCharsets.UTF_8)

                // Load the banks data
                val banksData = sampleMapBytes
                    ?.let { parseBankFile(it) } ?: emptyMap()

                // merge with existing data
                banksData.forEach { (bankName, sounds) ->
                    val existingBank = banks[bankName] ?: Samples.Bank(bankName)
                    banks[bankName] = existingBank.copy(sounds = sounds)
                }
            }

//            // TODO: aliases
//            val aliases = bank.aliasUris.map {
//
//            }
        }

        // return
        Samples.Index(
            defaultBank = library.defaultBank,
            banks = banks.values.toList(),
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
     * TODO: what other kinds of shapes are there?
     */
    private fun parseBankFile(jsonText: String): Map<String, Map<String, List<String>>> {
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

        val banks = linkedMapOf<String, LinkedHashMap<String, MutableList<String>>>()

        for ((key, value) in obj) {
            if (key == "_base") continue

            val (bank, sound) = splitBankSound(key) ?: continue
            val list = (value as? JsonArray)?.mapNotNull { it.asStringOrNull() } ?: continue

            val target = banks.getOrPut(bank) { linkedMapOf() }
                .getOrPut(sound) { mutableListOf() }

            for (p in list) {
                val fullUrl = if (p.startsWith("http://") || p.startsWith("https://")) {
                    p
                } else {
                    base + p.trimStart('/')
                }.replace(" ", "%20") // simple URL encode
                target += fullUrl
            }
        }

        val frozen = banks.mapValues { (_, sounds) ->
            sounds.mapValues { (_, urls) -> urls.toList() }
        }

        return frozen
    }

    private fun splitBankSound(key: String): Pair<String, String>? {
        val idx = key.lastIndexOf('_')
        if (idx <= 0 || idx >= key.lastIndex) return null
        val bank = key.substring(0, idx)
        val sound = key.substring(idx + 1)
        if (bank.isBlank() || sound.isBlank()) return null
        return bank to sound
    }

    private fun JsonElement.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull
}
