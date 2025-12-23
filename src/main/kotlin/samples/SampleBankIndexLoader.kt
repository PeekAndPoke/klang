package io.peekandpoke.samples

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import java.net.URI
import java.nio.charset.StandardCharsets


class SampleBankIndexLoader(
    private val downloader: SampleDownloader,
    private val cache: io.peekandpoke.utils.UrlCache,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun load(
        sampleMapUrl: URI,
        aliasUrl: URI? = null,
        defaultBank: String = "RolandTR909",
    ): SampleBankIndex = coroutineScope {
        val sampleMapBytes = async {
            cache.getOrPut(sampleMapUrl, "json") { downloader.download(sampleMapUrl) }
        }
        val aliasBytes = async {
            aliasUrl?.let { url ->
                cache.getOrPut(url, "json") { downloader.download(url) }
            }
        }

        val sampleMapJson = sampleMapBytes.await().toString(StandardCharsets.UTF_8)
        val aliasJson = aliasBytes.await()?.toString(StandardCharsets.UTF_8)

        val banks = parseTidalDrumMachines(sampleMapJson)
        val bankAliases = aliasJson?.let { parseTidalDrumMachinesAliases(it) } ?: emptyMap()

        SampleBankIndex(
            banks = banks,
            bankAliases = bankAliases,
            defaultBank = defaultBank,
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
    private fun parseTidalDrumMachinesAliases(jsonText: String): Map<String, String> {
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

    // ... existing code (parseTidalDrumMachines, splitBankSound, etc.) ...

    private fun parseTidalDrumMachines(jsonText: String): Map<String, Map<String, List<String>>> {
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
                }
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

    private fun kotlinx.serialization.json.JsonElement.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull
}
