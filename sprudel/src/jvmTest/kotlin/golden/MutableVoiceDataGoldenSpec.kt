/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.golden

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import org.junit.jupiter.api.fail
import java.io.File
import java.lang.reflect.Modifier

/**
 * Differential golden test guarding the mutable-`SprudelVoiceData` refactor (see
 * `docs/agent-tasks/mutable-voicedata-optimization.md`, Phase 0).
 *
 * It compiles a deterministic corpus ([GoldenCorpus]), queries each pattern cycle-by-cycle exactly the
 * way playback does, and serializes every event's wire-format [VoiceData] (`toVoiceData()`) together
 * with its tick-exact timing. The concatenation is compared byte-for-byte against a committed golden
 * file.
 *
 * The mutable refactor must reproduce this output **identically**. Aliasing bugs (a shared `data`
 * reference mutated by one event corrupting another) are otherwise silent and very hard to catch.
 *
 * To (re)generate the golden after an *intentional* change, either delete the golden file and run the
 * test (it rewrites then fails once, asking for a verify run), or set the env var:
 * ```
 * UPDATE_GOLDEN=true ./gradlew :sprudel:jvmTest --tests "*MutableVoiceDataGoldenSpec"
 * ```
 */
class MutableVoiceDataGoldenSpec : StringSpec() {

    // jvmTest runs with the module dir (sprudel/) as the working directory.
    private val goldenFile = File("src/jvmTest/resources/golden/voicedata_golden.txt")
    private val updateGolden = System.getProperty("updateGolden") == "true" ||
            System.getenv("UPDATE_GOLDEN") == "true"

    init {
        "VoiceData output is byte-identical to the committed golden" {
            val actual = GoldenCorpus.entries.joinToString("\n") { capture(it) }

            if (updateGolden || !goldenFile.exists()) {
                goldenFile.parentFile.mkdirs()
                goldenFile.writeText(actual)
                if (updateGolden) {
                    println("[golden] Updated ${goldenFile.path} (${actual.length} chars)")
                } else {
                    fail("Golden file did not exist — created ${goldenFile.path}. Re-run to verify.")
                }
            } else {
                val expected = goldenFile.readText()
                if (expected != actual) {
                    fail(buildMismatchMessage(expected, actual))
                }
            }
        }
    }

    /** Compile and capture per-cycle event output. */
    private fun capture(entry: GoldenCorpus.Entry): String {
        val pattern = SprudelPattern.compile(entry.code)
            ?: fail("Failed to compile corpus entry '${entry.name}'")

        val sb = StringBuilder()
        sb.append("### ").append(entry.name).append('\n')

        for (c in 0 until entry.cycles) {
            val events = pattern.queryArc(c.toDouble(), c + 1.0).sortedWith(EVENT_ORDER)
            for (e in events) {
                sb.append(formatEvent(e)).append('\n')
            }
        }
        return sb.toString()
    }

    private fun formatEvent(e: SprudelPatternEvent): String {
        val vd: VoiceData = e.data.toVoiceData()
        // Tick counts are exact integers (index * T) — stable across runs and platforms.
        return "${e.whole.begin.ticks}|${e.whole.end.ticks}|" +
                "${e.part.begin.ticks}|${e.part.end.ticks}|${e.isOnset}|${vd.golden()}"
    }

    /**
     * Compact, deterministic structural dump of a [VoiceData] — replaces the old kotlinx `encodeToString`
     * (the wire types are no longer `@Serializable`; the worklet uses the KSP codec). Only non-null leaves
     * are emitted (matching the old `explicitNulls=false`), keys are sorted, so a corruption that sets a
     * null field adds a key and one that nulls a set field drops a key — both show as a byte diff. Pure
     * reflection over the data classes, no serialization.
     */
    private fun VoiceData.golden(): String {
        val out = sortedMapOf<String, String>()
        flattenNonNull(this, "", out)
        return out.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    private fun flattenNonNull(value: Any?, path: String, out: MutableMap<String, String>) {
        fun child(seg: String) = if (path.isEmpty()) seg else "$path.$seg"
        when (value) {
            null -> {} // omit
            is Number, is Boolean, is CharSequence, is Char -> out[path] = value.toString()
            is Enum<*> -> out[path] = value.name
            is List<*> -> value.forEachIndexed { i, v -> flattenNonNull(v, child("$i"), out) }
            is Map<*, *> -> value.forEach { (k, v) -> flattenNonNull(v, child("$k"), out) }
            else -> value.javaClass.declaredFields
                .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) && '$' !in it.name }
                .forEach { f ->
                    f.isAccessible = true
                    flattenNonNull(f.get(value), child(f.name), out)
                }
        }
    }

    private fun buildMismatchMessage(expected: String, actual: String): String {
        val exp = expected.lines()
        val act = actual.lines()
        val firstDiff = (0 until maxOf(exp.size, act.size)).firstOrNull {
            exp.getOrNull(it) != act.getOrNull(it)
        } ?: -1
        return buildString {
            appendLine("Golden VoiceData output changed — the mutable refactor must reproduce it exactly.")
            appendLine("If this change is intentional, regenerate with -DupdateGolden=true.")
            appendLine("Lines: expected=${exp.size}, actual=${act.size}; first diff at line ${firstDiff + 1}:")
            appendLine("  expected: ${exp.getOrNull(firstDiff)?.take(300)}")
            appendLine("  actual:   ${act.getOrNull(firstDiff)?.take(300)}")
        }
    }

    companion object {
        private val EVENT_ORDER: Comparator<SprudelPatternEvent> = compareBy(
            { it.whole.begin.ticks },
            { it.part.begin.ticks },
            { it.whole.end.ticks },
            { it.part.end.ticks },
            { it.data.toVoiceData().toString() },
        )
    }
}
