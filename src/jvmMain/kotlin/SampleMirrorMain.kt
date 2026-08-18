/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.utils.isUrlWithProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * Mirrors all sample sets from their upstream hosts into a local static-file tree
 * (by default the sibling GitHub-Pages checkout), so they can be served from our own host
 * instead of rate-limited raw.githubusercontent.com.
 *
 * For every bundle in [SampleCatalogue.mirrorSets] it downloads the upstream manifest,
 * downloads every referenced sample file, and writes a rewritten `index.json` next to them:
 * entries stay byte-identical, but "_base" is removed so the manifest resolves relative to
 * its own url (see SampleIndexLoader's fallback base). The GM soundfont is not re-downloaded —
 * it already lives in the target tree — but its completeness is verified.
 *
 * Usage:
 *   ./gradlew runSampleMirror                                  mirror into ../peekandpoke.github.io/klang
 *   ./gradlew runSampleMirror --args="/some/other/target"      mirror into a custom target dir
 *   ./gradlew runSampleMirror --args="--verify"                offline check: manifest entries vs files on disk
 *
 * Re-running is cheap: files that already exist are skipped, so a run interrupted by upstream
 * rate limiting (HTTP 429) is simply re-run until the report shows zero failures.
 */
class SampleMirror(
    private val targetDir: Path,
) {
    companion object {
        /**
         * Manifest keys excluded from the mirror, per set dir.
         *
         * uzu-drumkit "brk" is an Amen-break derivative — the only file in that otherwise
         * public-domain (Unlicense) kit with contested provenance. Verified unused in this codebase.
         */
        val excludedKeys: Map<String, Set<String>> = mapOf(
            "uzu-drumkit" to setOf("brk"),
        )

        /**
         * Drum-machine short-name aliases (canonical -> alias), inlined as our own table.
         * Same factual mapping the strudel ecosystem uses; the upstream file carries no license,
         * so we ship our own copy of the data instead of mirroring the file.
         */
        val drumMachineAliases: Map<String, String> = mapOf(
            "AJKPercusyn" to "Percysyn",
            "AkaiLinn" to "Linn",
            "AkaiMPC60" to "MPC60",
            "AkaiXR10" to "XR10",
            "AlesisHR16" to "HR16",
            "AlesisSR16" to "SR16",
            "BossDR110" to "DR110",
            "BossDR220" to "DR220",
            "BossDR55" to "DR55",
            "BossDR550" to "DR550",
            "CasioRZ1" to "RZ1",
            "CasioSK1" to "SK1",
            "CasioVL1" to "VL1",
            "DoepferMS404" to "MS404",
            "EmuDrumulator" to "Drumulator",
            "EmuSP12" to "SP12",
            "KorgDDM110" to "DDM110",
            "KorgKPR77" to "KPR77",
            "KorgKR55" to "KR55",
            "KorgKRZ" to "KRZ",
            "KorgM1" to "M1",
            "KorgMinipops" to "Minipops",
            "KorgPoly800" to "Poly800",
            "KorgT3" to "T3",
            "Linn9000" to "9000",
            "LinnLM1" to "LM1",
            "LinnLM2" to "LM2",
            "MoogConcertMateMG1" to "ConcertMateMG1",
            "OberheimDMX" to "DMX",
            "RhodesPolaris" to "Polaris",
            "RhythmAce" to "Ace",
            "RolandCompurhythm1000" to "Compurhythm1000",
            "RolandCompurhythm78" to "Compurhythm78",
            "RolandCompurhythm8000" to "Compurhythm8000",
            "RolandD110" to "D110",
            "RolandD70" to "D70",
            "RolandDDR30" to "DDR30",
            "RolandJD990" to "JD990",
            "RolandMC202" to "MC202",
            "RolandMC303" to "MC303",
            "RolandMT32" to "MT32",
            "RolandR8" to "R8",
            "RolandS50" to "S50",
            "RolandSH09" to "SH09",
            "RolandSystem100" to "System100",
            "RolandTR505" to "TR505",
            "RolandTR606" to "TR606",
            "RolandTR626" to "TR626",
            "RolandTR707" to "TR707",
            "RolandTR727" to "TR727",
            "RolandTR808" to "TR808",
            "RolandTR909" to "TR909",
            "SakataDPM48" to "DPM48",
            "SequentialCircuitsDrumtracks" to "CircuitsDrumtracks",
            "SequentialCircuitsTom" to "CircuitsTom",
            "SergeModular" to "Serge",
            "SimmonsSDS400" to "SDS400",
            "SimmonsSDS5" to "SDS5",
            "SoundmastersR88" to "R88",
            "UnivoxMicroRhythmer12" to "MicroRhythmer12",
            "ViscoSpaceDrum" to "SpaceDrum",
            "XdrumLM8953" to "LM8953",
            "YamahaRM50" to "RM50",
            "YamahaRX21" to "RX21",
            "YamahaRX5" to "RX5",
            "YamahaRY30" to "RY30",
            "YamahaTG33" to "TG33",
        )

        const val maxAttempts = 5
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonPretty = Json { prettyPrint = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private class SetReport(val dir: String) {
        var entries = 0
        var excluded = 0
        var downloaded = 0
        var skipped = 0
        var megaBytes = 0.0
        val failed = mutableListOf<String>()
    }

    private val reports = mutableListOf<SetReport>()

    /** Downloads everything and writes rewritten manifests. Returns true when nothing failed. */
    fun run(): Boolean {
        Files.createDirectories(targetDir)

        SampleCatalogue.mirrorSets.forEach { set -> mirrorBundle(set) }

        writeAliasFile()

        val gmOk = verifyGm()

        printReports()

        val failures = reports.sumOf { it.failed.size }
        return failures == 0 && gmOk
    }

    /** Offline check: every entry of every local manifest must exist on disk. Returns true when complete. */
    fun verify(): Boolean {
        var missing = 0

        SampleCatalogue.mirrorSets.forEach { set ->
            val setDir = targetDir.resolve(set.dir)
            val manifestFile = setDir.resolve("index.json")

            if (!Files.exists(manifestFile)) {
                println("MISSING manifest: $manifestFile")
                missing++
                return@forEach
            }

            val obj = json.parseToJsonElement(Files.readString(manifestFile)) as? JsonObject
            if (obj == null) {
                println("BROKEN manifest: $manifestFile")
                missing++
                return@forEach
            }

            var setMissing = 0
            var setTotal = 0

            forEachEntry(obj) { entry ->
                setTotal++
                if (!Files.exists(setDir.resolve(toDiskRelPath(entry)))) {
                    println("  MISSING ${set.dir}: $entry")
                    setMissing++
                }
            }

            println("${set.dir}: $setTotal entries, $setMissing missing")
            missing += setMissing
        }

        val aliasFile = targetDir.resolve("tidal-drum-machines/alias.json")
        if (!Files.exists(aliasFile)) {
            println("MISSING alias file: $aliasFile")
            missing++
        }

        val gmOk = verifyGm()

        return missing == 0 && gmOk
    }

    private fun mirrorBundle(set: SampleCatalogue.MirrorSet) {
        val report = SetReport(set.dir).also { reports += it }
        val setDir = targetDir.resolve(set.dir)

        println("== ${set.dir} <- ${set.source.soundsUri}")

        val manifestBytes = try {
            fetch(set.source.soundsUri)
        } catch (e: Exception) {
            report.failed += "${set.source.soundsUri} (${e.message})"
            println("  FAILED to fetch manifest: ${e.message}")
            return
        }

        val obj = json.parseToJsonElement(manifestBytes.decodeToString()) as? JsonObject
        if (obj == null) {
            report.failed += "${set.source.soundsUri} (manifest is not a JSON object)"
            return
        }

        val base = obj["_base"]?.jsonPrimitive?.contentOrNull?.trim()?.trimEnd('/')
            ?: set.source.soundsUri.substringBeforeLast('/')

        val excluded = excludedKeys[set.dir] ?: emptySet()

        // Rebuild the manifest: drop "_base" and excluded keys, keep entries byte-identical
        // (absolute-url entries get rewritten to their new relative location).
        val rewritten = linkedMapOf<String, JsonElement>()

        for ((key, value) in obj) {
            if (key == "_base") continue

            if (key in excluded) {
                report.excluded++
                println("  excluding '$key' (see SampleMirror.excludedKeys)")
                continue
            }

            val element = when (value) {
                is JsonArray -> JsonArray(
                    value.map { el ->
                        val entry = (el as? JsonPrimitive)?.contentOrNull
                            ?: return@map el
                        JsonPrimitive(mirrorEntry(entry, base, setDir, report))
                    }
                )

                is JsonObject -> JsonObject(
                    value.mapValues { (_, el) ->
                        val entry = (el as? JsonPrimitive)?.contentOrNull
                            ?: return@mapValues el
                        JsonPrimitive(mirrorEntry(entry, base, setDir, report))
                    }
                )

                else -> value
            }

            rewritten[key] = element
        }

        writeAtomic(setDir.resolve("index.json"), jsonPretty.encodeToString(JsonObject.serializer(), JsonObject(rewritten)).encodeToByteArray())
    }

    /**
     * Downloads one manifest entry into [setDir] and returns the (possibly rewritten) entry string
     * for the mirrored manifest.
     */
    private fun mirrorEntry(entry: String, base: String, setDir: Path, report: SetReport): String {
        report.entries++

        // Absolute urls inside a manifest are unexpected today ... mirror them under external/ and rewrite the entry
        val (url, relEntry) = when {
            entry.isUrlWithProtocol() -> {
                val rel = "external/" + URI(entry).path.trimStart('/')
                println("  NOTE absolute entry rewritten: $entry -> $rel")
                entry to rel
            }

            else -> {
                val u = (base.trimEnd('/') + '/' + entry.trimStart('/')).replace(" ", "%20")
                u to entry
            }
        }

        try {
            val dest = setDir.resolve(toDiskRelPath(relEntry))

            if (Files.exists(dest) && Files.size(dest) > 0) {
                report.skipped++
            } else {
                val bytes = fetch(url)
                writeAtomic(dest, bytes)
                report.downloaded++
                report.megaBytes += bytes.size / (1024.0 * 1024.0)
                // Be polite to the upstream host
                Thread.sleep(50)
            }
        } catch (e: Exception) {
            report.failed += "$url (${e.message})"
            println("  FAILED $url: ${e.message}")
        }

        return relEntry
    }

    private fun writeAliasFile() {
        val dest = targetDir.resolve("tidal-drum-machines/alias.json")
        val obj = JsonObject(drumMachineAliases.mapValues { (_, v) -> JsonPrimitive(v) })
        writeAtomic(dest, jsonPretty.encodeToString(JsonObject.serializer(), obj).encodeToByteArray())
        println("== tidal-drum-machines/alias.json written (${drumMachineAliases.size} aliases)")
    }

    /** The GM soundfont already lives in the target tree; check that every referenced variant file exists. */
    private fun verifyGm(): Boolean {
        val gmDir = targetDir.resolve("felixroos/gm")
        val indexFile = gmDir.resolve("index.json")

        if (!Files.exists(indexFile)) {
            println("== GM soundfont: MISSING $indexFile")
            return false
        }

        val obj = json.parseToJsonElement(Files.readString(indexFile)) as? JsonObject
        if (obj == null) {
            println("== GM soundfont: BROKEN $indexFile")
            return false
        }

        var total = 0
        var missing = 0

        obj.values.forEach { variants ->
            (variants as? JsonArray)?.forEach inner@{ variant ->
                val file = (variant as? JsonObject)?.get("file")?.jsonPrimitive?.contentOrNull ?: return@inner
                total++
                if (!Files.exists(gmDir.resolve(file))) {
                    println("  MISSING gm: $file")
                    missing++
                }
            }
        }

        println("== GM soundfont: $total variant files referenced, $missing missing")
        return missing == 0
    }

    private fun forEachEntry(manifest: JsonObject, block: (String) -> Unit) {
        for ((key, value) in manifest) {
            if (key == "_base") continue

            when (value) {
                is JsonArray -> value.forEach { el ->
                    (el as? JsonPrimitive)?.contentOrNull?.let(block)
                }

                is JsonObject -> value.values.forEach { el ->
                    (el as? JsonPrimitive)?.contentOrNull?.let(block)
                }

                else -> Unit
            }
        }
    }

    /** Percent-decodes a manifest entry into its on-disk relative path (real spaces on disk). */
    private fun toDiskRelPath(entry: String): String {
        // Protect literal '+' ... URLDecoder would turn it into a space
        val decoded = URLDecoder.decode(entry.trimStart('/').replace("+", "%2B"), Charsets.UTF_8)

        val segments = decoded.split('/')
        check(segments.none { it == ".." || it.isBlank() }) { "Suspicious sample path: $entry" }

        return decoded
    }

    private fun writeAtomic(dest: Path, bytes: ByteArray) {
        Files.createDirectories(dest.parent)
        val tmp = dest.resolveSibling(dest.fileName.toString() + ".tmp")
        Files.write(tmp, bytes)
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** GET with retry: exponential backoff on 429/5xx (honoring Retry-After) and on IO errors. */
    private fun fetch(url: String): ByteArray {
        var attempt = 0
        var waitSeconds = 2.0

        while (true) {
            attempt++

            try {
                val request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "klang-sample-mirror (+https://klang.finzo.de)")
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                val status = response.statusCode()

                when {
                    status in 200..299 -> return response.body()

                    status == 429 || status >= 500 -> {
                        if (attempt >= maxAttempts) error("HTTP $status after $attempt attempts")

                        val retryAfter = response.headers().firstValue("Retry-After").orElse(null)?.toDoubleOrNull()
                        val sleep = maxOf(waitSeconds, retryAfter ?: 0.0)
                        println("  HTTP $status, retrying in ${sleep}s: $url")
                        Thread.sleep((sleep * 1000).toLong())
                        waitSeconds *= 2
                    }

                    else -> error("HTTP $status")
                }
            } catch (e: IOException) {
                if (attempt >= maxAttempts) throw e
                println("  ${e.message}, retrying in ${waitSeconds}s: $url")
                Thread.sleep((waitSeconds * 1000).toLong())
                waitSeconds *= 2
            }
        }
    }

    private fun printReports() {
        println()
        println("==== Mirror report ====")

        reports.forEach { r ->
            val mb = (r.megaBytes * 10).toInt() / 10.0
            println(
                "${r.dir}: ${r.entries} entries" +
                        (if (r.excluded > 0) " (${r.excluded} excluded)" else "") +
                        " | downloaded ${r.downloaded} ($mb MB), skipped ${r.skipped}, failed ${r.failed.size}"
            )
        }

        val failures = reports.flatMap { it.failed }
        if (failures.isNotEmpty()) {
            println()
            println("FAILED (${failures.size}) — re-run runSampleMirror to retry:")
            failures.forEach { println("  $it") }
        }
    }
}

fun main(args: Array<String>) {
    val verifyOnly = "--verify" in args
    val target = args.firstOrNull { !it.startsWith("--") } ?: "../peekandpoke.github.io/klang"

    val mirror = SampleMirror(targetDir = Path.of(target))

    val ok = when {
        verifyOnly -> mirror.verify()
        else -> mirror.run()
    }

    when {
        ok -> println("OK — mirror is complete")
        else -> {
            println("INCOMPLETE — see report above")
            exitProcess(1)
        }
    }
}
