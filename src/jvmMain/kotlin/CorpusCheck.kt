/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.util.HexFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * The corpus check: renders every built-in song, the two frozen songs and the frozen piece, and compares each with
 * its line in `docs/benchmarks/corpus-reference.md`. Any difference is a sound change.
 *
 * `./gradlew runSongBenchmark --args=corpus` checks: it prints identical, CHANGED, MISSING or NEW per song and returns
 * 1 on any difference. `./gradlew runSongBenchmark --args=corpus-accept` rewrites the reference from this tree: a
 * commit that changes the sound on purpose carries its new reference, so that file's history lists every deliberate
 * sound change. `console/full-check.sh` runs the check.
 *
 * Per song: 256 cycles at 48 kHz through [KlangOfflineRenderer] (phase pool seed 1, the inline DSL registration, the
 * samples, a 2 s tail plus the limiter's latency), the wall-clock signals (`timeOfDay` and friends) pinned to
 * `pure(0.5)`. The hash is a SHA-256 of the raw doubles of the engine's output after the master (what is heard),
 * the first 16 hex digits, and the peak beside it. The samples come from the cache (`./cache`) or the network. A sample
 * that fails to load would change its song's hash, so any file that could not load turns the run red with that as its
 * cause, before any song is compared (and an accept is refused).
 */
object CorpusCheck {

    private const val REFERENCE = "docs/benchmarks/corpus-reference.md"
    private const val CYCLES = 256
    private const val SAMPLE_RATE = 48_000

    /** Songs rendered at once; each worker owns its own [Samples], whose caches are not thread-safe. */
    private const val WORKERS = 4

    private val wallClock = Regex("""\b(timeOfDay|sineOfDay|timeOfNight|sineOfNight)\b""")

    private class Song(val title: String, val code: String, val rpm: Double)

    private class Rendered(val title: String, val hash: String, val peak: String)

    private fun corpus(): List<Song> = buildList {
        BuiltInSongs.songs.forEach { add(Song(title = it.title, code = it.code, rpm = it.rpm)) }
        add(Song(title = "frozen derSchmetterling_2026_09_25", code = FrozenSongs.derSchmetterling_2026_09_25, rpm = 32.5))
        add(Song(title = "frozen strangerThings_2026_07_03", code = FrozenSongs.strangerThings_2026_07_03, rpm = 34.0))
        add(
            Song(
                title = "frozen piece derSchmetterling_2026_09_16",
                code = FrozenPieces.derSchmetterling_2026_09_16,
                rpm = FrozenPieces.derSchmetterlingRpm_2026_09_16,
            )
        )
    }

    /** Checks the corpus against the reference ([accept] false) or rewrites the reference. Returns the exit code. */
    fun run(accept: Boolean): Int {
        val songs = corpus()
        val start = System.nanoTime().toDouble()

        println("=== Corpus ${if (accept) "accept" else "check"}: ${songs.size} songs, $CYCLES cycles, $SAMPLE_RATE Hz ===")
        println()

        val failedDownloads: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val rendered = renderAll(songs = songs, failedDownloads = failedDownloads)
        val seconds = (System.nanoTime().toDouble() - start) / 1.0e9
        val file = File(REFERENCE)

        if (failedDownloads.isNotEmpty()) {
            println(
                "CORPUS RED: ${failedDownloads.size} sample files could not load (no ./cache and no network?), " +
                        "so no song was compared${if (accept) " and nothing was accepted" else ""}"
            )
            failedDownloads.sorted().take(5).forEach { println("  could not load $it") }

            return 1
        }

        if (accept) {
            writeReference(file = file, rendered = rendered)
            rendered.forEach { println("accepted  ${it.title} | ${it.hash} | peak ${it.peak}") }
            println()
            println("CORPUS ACCEPTED: ${rendered.size} songs written to $REFERENCE (${fmt(seconds, 0)} s)")

            return 0
        }

        val reference = readReference(file)

        if (reference == null) {
            println("CORPUS RED: no reference at $REFERENCE (write one with --args=corpus-accept)")

            return 1
        }

        var identical = 0
        val worse = mutableListOf<String>()

        for (song in rendered) {
            val ref = reference[song.title]

            when {
                ref == null -> {
                    worse.add("NEW       ${song.title} | ${song.hash} | peak ${song.peak}")
                }

                ref.hash == song.hash && ref.peak == song.peak -> {
                    identical++
                    println("identical ${song.title}")
                }

                else -> {
                    worse.add("CHANGED   ${song.title} | ${ref.hash} -> ${song.hash} | peak ${ref.peak} -> ${song.peak}")
                }
            }
        }

        val titles = rendered.map { it.title }.toSet()

        reference.keys.filter { it !in titles }.forEach { worse.add("MISSING   $it (in the reference, not rendered)") }
        worse.forEach { println(it) }
        println()

        if (worse.isEmpty()) {
            println("CORPUS GREEN: $identical of ${rendered.size} songs identical to the reference (${fmt(seconds, 0)} s)")

            return 0
        }

        println("CORPUS RED: $identical of ${rendered.size} songs identical to the reference, ${worse.size} differences (${fmt(seconds, 0)} s)")

        return 1
    }

    private fun renderAll(songs: List<Song>, failedDownloads: MutableSet<String>): List<Rendered> = runBlocking(Dispatchers.Default) {
        val results = arrayOfNulls<Rendered>(songs.size)
        val next = AtomicInteger(0)

        (0 until WORKERS).map {
            async {
                val samples = Samples.create(loader = NotingLoader(failedDownloads), catalogue = SampleCatalogue.default)
                var index = next.getAndIncrement()

                while (index < songs.size) {
                    results[index] = render(song = songs[index], samples = samples)
                    index = next.getAndIncrement()
                }
            }
        }.awaitAll()

        results.map { it ?: error("a song was not rendered") }
    }

    private suspend fun render(song: Song, samples: Samples): Rendered {
        val engine = klangScript {
            registerLibrary(sprudelLib)
            registerBuiltInSongsAsModules()
        }

        engine.execute("""import * from "stdlib"""")
        engine.execute("""import * from "sprudel"""")

        val pinned = wallClock.replace(song.code) { "(pure(0.5))" }
        val pattern = engine.execute(pinned + "\n").toObjectOrNull<KlangPattern>() ?: error("${song.title} did not compile")
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = ByteBuffer.allocate(0)
        var peak = 0.0

        KlangOfflineRenderer(sampleRate = SAMPLE_RATE).render(
            pattern = pattern,
            cycles = CYCLES,
            cyclesPerSecond = song.rpm / 60.0,
            samples = samples,
        ) { out, frames ->
            if (bytes.capacity() < frames * 16) {
                bytes = ByteBuffer.allocate(frames * 16)
            }

            bytes.clear()

            for (i in 0 until frames) {
                bytes.putDouble(out.left[i])
                bytes.putDouble(out.right[i])
                peak = maxOf(peak, abs(out.left[i]), abs(out.right[i]))
            }

            digest.update(bytes.array(), 0, bytes.position())
        }

        val hash = HexFormat.of().formatHex(digest.digest()).take(16)

        return Rendered(title = song.title, hash = hash, peak = fmt(peak, 4))
    }

    /**
     * The default loader, noting every file it could not fetch. It sits below the disk cache, which asks it only on a
     * miss, so a warm cache notes nothing. The failure itself goes on as before.
     */
    private class NotingLoader(private val failed: MutableSet<String>) : AssetLoader {
        override suspend fun download(uri: String): ByteArray? {
            val bytes = try {
                AssetLoader.default.download(uri)
            } catch (e: Exception) {
                failed.add(uri)

                throw e
            }

            if (bytes == null) {
                failed.add(uri)
            }

            return bytes
        }
    }

    private class Reference(val hash: String, val peak: String)

    /** The reference's table rows by title, or null when there is no reference file. */
    private fun readReference(file: File): Map<String, Reference>? {
        if (!file.isFile) {
            return null
        }

        return file.readLines()
            .filter { it.startsWith("| ") && !it.startsWith("| song |") }
            .map { line -> line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() } }
            .filter { it.size == 3 }
            .associate { (title, hash, peak) -> title to Reference(hash = hash, peak = peak) }
    }

    private fun writeReference(file: File, rendered: List<Rendered>) {
        val text = StringBuilder()

        text.appendLine("# The corpus reference")
        text.appendLine()
        text.appendLine("What the corpus sounds like, one line per song: the first 16 hex digits of a SHA-256 of the engine's output")
        text.appendLine("after the master, and the peak. Written by `./gradlew runSongBenchmark --args=corpus-accept`, checked by")
        text.appendLine("`./gradlew runSongBenchmark --args=corpus` (and `console/full-check.sh`), never edited by hand. A commit that")
        text.appendLine("changes the sound on purpose carries its new reference, so this file's history lists every deliberate sound")
        text.appendLine("change. The method is in `src/jvmMain/kotlin/CorpusCheck.kt`.")
        text.appendLine()
        text.appendLine("Accepted on ${LocalDate.now()}, tree `${describe()}`.")
        text.appendLine()
        text.appendLine("| song | hash | peak |")
        text.appendLine("|---|---|---|")
        rendered.forEach { text.appendLine("| ${it.title} | ${it.hash} | ${it.peak} |") }

        file.parentFile.mkdirs()
        file.writeText(text.toString())
    }

    private fun fmt(v: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", v)

    private fun describe(): String = runCatching {
        val process = ProcessBuilder("git", "describe", "--always", "--dirty").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText().trim()

        if (process.waitFor() == 0) text else "unknown"
    }.getOrDefault("unknown")
}
