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
 * its line in `docs/benchmarks/corpus-reference.md`.
 *
 * Kind: for the future, its engine half: it proves that a behaviour-neutral engine change is neutral, while songs move
 * freely (`CLAUDE.md`, "Two kinds of check"). Each row holds two hashes: the song's CODE (its text and rpm) and its
 * SOUND.
 *  - A frozen text whose sound differs is RED (CHANGED, NEW or MISSING).
 *  - A live song whose code is unchanged but whose sound moved is RED: the engine or its inputs (samples, catalogue)
 *    moved it (CHANGED).
 *  - A live song whose code moved is information only (`INFO`, edited, new or gone), whatever its sound did.
 *
 * `./gradlew runSongBenchmark --args=corpus` checks and returns 1 on any RED row. `--args=corpus-accept` rewrites the
 * reference from this tree and REFUSES (exit 1, nothing written) while any row the check calls RED is there: it prints
 * those rows. `--args=corpus-accept-engine` accepts a deliberate engine sound change on any row. Since a red row can
 * only be written by `corpus-accept-engine`, the reference's history lists every deliberate engine sound change that
 * moved a frozen text or an unedited live song. An engine move on a live song edited in the same change rides along
 * as INFO, which is why a neutrality proof reads "the corpus GREEN with no live song edited (or accept first)".
 * `console/full-check.sh` runs the check.
 *
 * Per song: 256 cycles at 48 kHz through [KlangOfflineRenderer] (phase pool seed 1, the inline DSL registration, the
 * samples, a 2 s tail plus the limiter's latency), the wall-clock signals (`timeOfDay` and friends) pinned to
 * `pure(0.5)`. The sound hash is a SHA-256 of the raw doubles of the engine's output after the master (what is heard),
 * the first 16 hex digits, and the peak beside it. The code hash is the first 16 hex digits of a SHA-256 of what the
 * render reads from the song: its rpm and its text, and the code of every built-in module it imports
 * ([builtInSongModules]), so an edit to the song it imports counts as its own edit. The samples come from the cache (`./cache`) or the network. A
 * sample that fails to load would change its song's hash, so any file that could not load turns the run red with that
 * as its cause, before any song is compared (and an accept is refused).
 */
object CorpusCheck {

    private const val REFERENCE = "docs/benchmarks/corpus-reference.md"
    private const val CYCLES = 256
    private const val SAMPLE_RATE = 48_000

    /** Songs rendered at once; each worker owns its own [Samples], whose caches are not thread-safe. */
    private const val WORKERS = 4

    private val wallClock = Regex("""\b(timeOfDay|sineOfDay|timeOfNight|sineOfNight)\b""")

    /** The title of every frozen text starts with this; it tells a MISSING reference row's kind, which has no song. */
    private const val FROZEN = "frozen "

    /** What a run does: check, accept while nothing is RED, or accept a deliberate engine sound change too. */
    enum class Mode { CHECK, ACCEPT, ACCEPT_ENGINE }

    private class Song(val title: String, val code: String, val rpm: Double, val frozen: Boolean)

    private class Rendered(val title: String, val frozen: Boolean, val code: String, val hash: String, val peak: String)

    private fun corpus(): List<Song> = buildList {
        BuiltInSongs.songs.forEach { add(Song(title = it.title, code = it.code, rpm = it.rpm, frozen = false)) }
        add(
            Song(
                title = "${FROZEN}derSchmetterling_2026_09_25",
                code = FrozenSongs.derSchmetterling_2026_09_25,
                rpm = FrozenSongs.derSchmetterlingRpm_2026_09_25,
                frozen = true,
            )
        )
        add(Song(title = "${FROZEN}strangerThings_2026_07_03", code = FrozenSongs.strangerThings_2026_07_03, rpm = FrozenSongs.strangerThingsRpm_2026_07_03, frozen = true))
        add(
            Song(
                title = "${FROZEN}piece derSchmetterling_2026_09_16",
                code = FrozenPieces.derSchmetterling_2026_09_16,
                rpm = FrozenPieces.derSchmetterlingRpm_2026_09_16,
                frozen = true,
            )
        )
    }

    /** An import's module name, in single or double quotes; the regex also allows backticks, harmless over-matching. */
    private val importFrom = Regex("""\bfrom\s*(["'`])([^"'`]+)\1""")

    /** The code hash: what the render reads from the song, its [rpm] and its [code], plus the modules it imports. */
    private fun codeHash(code: String, rpm: Double): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val seen = mutableSetOf<String>()

        fun feed(text: String) {
            digest.update(text.toByteArray())

            importFrom.findAll(text).map { it.groupValues[2] }.forEach { name ->
                val module = builtInSongModules[name]

                if (module != null && seen.add(name)) {
                    digest.update("module $name\n".toByteArray())
                    feed(module.code)
                }
            }
        }

        digest.update("rpm $rpm\n".toByteArray())
        feed(code)

        return HexFormat.of().formatHex(digest.digest()).take(16)
    }

    /** The check's verdict on every row: the one definition of RED, shared by the check and the accept guard. */
    private class Comparison {
        var frozenIdentical = 0
        var liveIdentical = 0
        var edited = 0
        var added = 0
        var gone = 0
        var frozenWorse = 0
        var engineMoved = 0
        val identical = mutableListOf<String>()

        /** The RED rows. */
        val worse = mutableListOf<String>()

        /** The live rows that moved with their text: information. */
        val moved = mutableListOf<String>()
    }

    private fun compare(rendered: List<Rendered>, reference: Map<String, Reference>): Comparison {
        val c = Comparison()

        for (song in rendered) {
            val ref = reference[song.title]

            when {
                ref == null && song.frozen -> {
                    c.frozenWorse++
                    c.worse.add("NEW       ${song.title} | ${song.hash} | peak ${song.peak}")
                }

                ref == null -> {
                    c.added++
                    c.moved.add("INFO      ${song.title} new | code ${song.code} | ${song.hash} | peak ${song.peak}")
                }

                !song.frozen && ref.code != song.code -> {
                    c.edited++
                    c.moved.add(
                        "INFO      ${song.title} edited | code ${ref.code} -> ${song.code} | " +
                                "${ref.hash} -> ${song.hash} | peak ${ref.peak} -> ${song.peak}"
                    )
                }

                ref.hash == song.hash && ref.peak == song.peak -> {
                    if (song.frozen) {
                        c.frozenIdentical++
                    } else {
                        c.liveIdentical++
                    }

                    c.identical.add("identical ${song.title}")
                }

                song.frozen -> {
                    val edited = if (ref.code != song.code) " (its frozen text was edited)" else ""

                    c.frozenWorse++
                    c.worse.add("CHANGED   ${song.title}$edited | ${ref.hash} -> ${song.hash} | peak ${ref.peak} -> ${song.peak}")
                }

                else -> {
                    c.engineMoved++
                    c.worse.add(
                        "CHANGED   ${song.title} (song text unchanged, the engine or its inputs moved it) | ${ref.hash} -> ${song.hash} | " +
                                "peak ${ref.peak} -> ${song.peak}"
                    )
                }
            }
        }

        val titles = rendered.map { it.title }.toSet()

        reference.keys.filter { it !in titles }.forEach { title ->
            if (title.startsWith(FROZEN)) {
                c.frozenWorse++
                c.worse.add("MISSING   $title (in the reference, not rendered)")
            } else {
                c.gone++
                c.moved.add("INFO      $title gone (in the reference, not rendered)")
            }
        }

        return c
    }

    /** Checks the corpus against the reference, or rewrites the reference ([mode]). Returns the exit code. */
    fun run(mode: Mode): Int {
        val songs = corpus()
        val start = System.nanoTime().toDouble()
        val accept = mode != Mode.CHECK

        println("=== Corpus ${if (accept) "accept" else "check"}: ${songs.size} songs, $CYCLES cycles, $SAMPLE_RATE Hz ===")
        println()

        val failedDownloads: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val rendered = renderAll(songs = songs, failedDownloads = failedDownloads)
        val seconds = (System.nanoTime().toDouble() - start) / 1.0e9
        val time = "(${fmt(seconds, 0)} s)"
        val file = File(REFERENCE)

        if (failedDownloads.isNotEmpty()) {
            println(
                "CORPUS RED: ${failedDownloads.size} sample files could not load (no ./cache and no network?), " +
                        "so no song was compared${if (accept) " and nothing was accepted" else ""}"
            )
            failedDownloads.sorted().take(5).forEach { println("  could not load $it") }

            return 1
        }

        val reference = readReference(file)

        if (accept) {
            // no reference yet: nothing to compare with, so nothing to refuse
            val red = if (reference == null) emptyList() else compare(rendered = rendered, reference = reference).worse

            red.forEach { println(it) }

            if (mode == Mode.ACCEPT && red.isNotEmpty()) {
                println()
                println(
                    "CORPUS RED: accept refused, ${red.size} rows the check calls red, nothing was written; " +
                            "a deliberate engine sound change accepts them with --args=corpus-accept-engine $time"
                )

                return 1
            }

            writeReference(file = file, rendered = rendered)
            rendered.forEach { println("accepted  ${it.title} | ${it.code} | ${it.hash} | peak ${it.peak}") }
            println()
            println("CORPUS ACCEPTED: ${rendered.size} songs written to $REFERENCE, ${red.size} red rows accepted $time")

            return 0
        }

        if (reference == null) {
            println("CORPUS RED: no reference at $REFERENCE (write one with --args=corpus-accept)")

            return 1
        }

        val c = compare(rendered = rendered, reference = reference)

        c.identical.forEach { println(it) }
        c.moved.forEach { println(it) }
        c.worse.forEach { println(it) }
        println()

        val frozenCount = rendered.count { it.frozen }
        val info = "${c.edited} edited, ${c.added} new, ${c.gone} gone (information)"

        if (c.worse.isEmpty()) {
            println("CORPUS GREEN: ${c.frozenIdentical} of $frozenCount frozen identical; live songs: ${c.liveIdentical} identical, $info $time")

            return 0
        }

        val engine = if (c.engineMoved > 0) "${c.engineMoved} moved by the engine, " else ""

        println(
            "CORPUS RED: ${c.frozenIdentical} of $frozenCount frozen identical, ${c.frozenWorse} frozen differences; " +
                    "live songs: ${c.liveIdentical} identical, $engine$info $time"
        )

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

        return Rendered(title = song.title, frozen = song.frozen, code = codeHash(code = song.code, rpm = song.rpm), hash = hash, peak = fmt(peak, 4))
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

    private class Reference(val code: String, val hash: String, val peak: String)

    /** The reference's table rows by title, or null when there is no reference file. */
    private fun readReference(file: File): Map<String, Reference>? {
        if (!file.isFile) {
            return null
        }

        return file.readLines()
            .filter { it.startsWith("| ") && !it.startsWith("| song |") }
            .map { line -> line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() } }
            .associate { cells ->
                require(cells.size == 4) { "$REFERENCE: a row needs 4 cells (song, code, hash, peak): $cells" }

                cells[0] to Reference(code = cells[1], hash = cells[2], peak = cells[3])
            }
    }

    private fun writeReference(file: File, rendered: List<Rendered>) {
        val text = StringBuilder()

        text.appendLine("# The corpus reference")
        text.appendLine()
        text.appendLine("What the corpus sounds like, one line per song: the first 16 hex digits of a SHA-256 of the song's code")
        text.appendLine("(its text, its rpm and the built-in modules it imports), of the engine's output after the master (hash), and")
        text.appendLine("the peak. Written by `./gradlew runSongBenchmark --args=corpus-accept`, checked by")
        text.appendLine("`./gradlew runSongBenchmark --args=corpus` (and `console/full-check.sh`), never edited by hand. The method is")
        text.appendLine("in `src/jvmMain/kotlin/CorpusCheck.kt`.")
        text.appendLine()
        text.appendLine("A `frozen` row whose sound differs is red. A live song whose code is unchanged but whose sound moved is red: the")
        text.appendLine("engine or its inputs moved it. A live song whose code moved is information only, because songs move freely.")
        text.appendLine("`corpus-accept` refuses while any row is red; `corpus-accept-engine` accepts a deliberate engine sound change.")
        text.appendLine("Since a red row can only be written by `corpus-accept-engine`, this file's history lists every deliberate")
        text.appendLine("engine sound change that moved a frozen text or an unedited live song. An engine move on a live song edited in")
        text.appendLine("the same change rides along as INFO, which is why a neutrality proof reads \"the corpus GREEN with no live song")
        text.appendLine("edited (or accept first)\".")
        text.appendLine()
        text.appendLine("Accepted on ${LocalDate.now()}, tree `${describe()}`.")
        text.appendLine()
        text.appendLine("| song | code | hash | peak |")
        text.appendLine("|---|---|---|---|")
        rendered.forEach { text.appendLine("| ${it.title} | ${it.code} | ${it.hash} | ${it.peak} |") }

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
