/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.MasterValue
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import java.io.File

/**
 * Structural fingerprint of every DSL tree the builtin songs produce: sounds (`SoundValue.Osc`),
 * master chains (`MasterValue.Dsl`) and inline pipelines (`PipelineValue.Dsl`).
 *
 * Each song is compiled and queried over its first [CYCLES] cycles; every DSL tree on an event
 * is rendered with the data class `toString()`, prefixed with its kind (noise `uid`s normalised
 * away: they come from a global counter) and hashed. The sorted set of hashes per song is the
 * fingerprint. A DSL migration that rewrites how a song SPELLS its sounds (the
 * configure-lambda builders, `docs/tasks/dsl-configure-lambdas.md`) must leave every
 * fingerprint untouched: same trees, bit-identical audio.
 *
 * The spec always writes the CURRENT fingerprints to `build/reports/sound-trees/fingerprints.txt`.
 * To regenerate the baseline deliberately (never by hand), run the spec once and copy that
 * report over `src/jvmTest/resources/builtin-songs-sound-trees-baseline.txt`.
 */
class BuiltInSongsSoundTreeBaselineSpec : StringSpec({

    fun engine() = klangScript {
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    fun fingerprint(song: Song): String {
        val pattern = SprudelPattern.compile(engine(), song.code)
        withClue(song.id) { pattern.shouldNotBeNull() }
        val trees = pattern!!.queryArc(0.0, CYCLES.toDouble())
            .flatMap { event ->
                listOfNotNull(
                    (event.data.sound as? SoundValue.Osc)?.osc?.let { "sound:$it" },
                    (event.data.master as? MasterValue.Dsl)?.master?.let { "master:$it" },
                    (event.data.pipeline as? PipelineValue.Dsl)?.pipeline?.let { "pipeline:$it" },
                )
            }
            .map { it.replace(UID_REGEX, "uid=#") }
            .toSortedSet()
        val hashes = trees.map { it.hashCode().toUInt().toString(16) }
        return "${song.id}\t${trees.size}\t${hashes.joinToString(",")}"
    }

    val lines = BuiltInSongs.songs.map { fingerprint(it) }
    File(REPORT).apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n") + "\n")

    val baseline = object {}.javaClass.classLoader.getResourceAsStream(BASELINE)
        ?.bufferedReader()?.readLines()?.filter { it.isNotBlank() }
        ?: emptyList()
    val expected = baseline.associateBy { it.substringBefore('\t') }

    "the baseline covers every builtin song (else copy $REPORT to src/jvmTest/resources/$BASELINE)" {
        expected.keys shouldBe BuiltInSongs.songs.map { it.id }.toSet()
    }

    for (line in lines) {
        val id = line.substringBefore('\t')
        "$id: sound trees are unchanged" {
            line shouldBe expected[id]
        }
    }
}) {
    companion object {
        const val CYCLES = 256
        const val BASELINE = "builtin-songs-sound-trees-baseline.txt"
        const val REPORT = "build/reports/sound-trees/fingerprints.txt"
        val UID_REGEX = Regex("uid=\\d+")
    }
}
