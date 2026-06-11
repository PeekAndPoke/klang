package io.peekandpoke.klang.sprudel.compat

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel.formatAsTable
import io.peekandpoke.klang.sprudel.graal.GraalSprudelCompiler
import org.junit.jupiter.api.fail
import java.lang.reflect.Modifier
import kotlin.math.abs

@Suppress("OPT_IN_USAGE")
class JsCompatTests : StringSpec() {

    data class ComparisonReport(
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val success: Boolean = errors.isEmpty(),
        val report: String,
    )

    enum class ComparisonResult {
        EXACT,
        CLOSE,
        DIFFERENT,
        IGNORED,
        RECOVERED,
    }

    private val graalCompiler = GraalSprudelCompiler()

    init {
        // Testing that simple pattern code produces the same results
        JsCompatTestData.patterns.forEachIndexed { index, example ->
            "Pattern ${index + 1}: ${example.name}".config(enabled = !example.skip) {
                runComparison(example)
            }
        }

        // Testing that songs code produces the same results
        JsCompatTestSongs.songs.forEachIndexed { index, example ->
            "Song ${index + 1}: ${example.name}".config(enabled = !example.skip) {
                runComparison(example)
            }
        }
    }

    private suspend fun runComparison(example: Example) {
        val name = example.name
        val code = example.code

        val nativePattern = withClue("Compiling '$name' natively") {
            SprudelPattern.compile(code)
                ?: fail("Failed to compile '$name' natively")
        }

        val graalPattern = withClue("Compiling '$name' with GraalVM") {
            val result = try {
                graalCompiler.compile(code).await()
            } catch (e: Throwable) {
                fail("Failed to compile '$name' with GraalVM", e)
            }

            result.shouldNotBeNull()
        }

        fun List<SprudelPatternEvent>.sort() = sortedWith(
            compareBy(
                { it.part.begin.toDouble() },
                { it.data.note },
            )
        )

        val numCycles = 32

        val graalArc = (0..<numCycles)
            .flatMap { graalPattern.queryArc(it.toDouble(), (it + 1).toDouble()) }.sort()
            .filter { it.isOnset }

        val nativeArc = (0..<numCycles)
            .flatMap { nativePattern.queryArc(it.toDouble(), (it + 1).toDouble()) }.sort()
            .filter { it.isOnset }

        printEventComparison(graalArc, nativeArc)

        assertSoftly {
            withClue(
                """
Number of events must match 
| Name: $name
| Pattern: $code
| JS (Graal): ${graalArc.size} VS Native: ${nativeArc.size}
                """.trimIndent()
            ) {
                nativeArc.size shouldBe graalArc.size
            }

            val zippedArc = graalArc.zip(nativeArc)

            var errors = 0

            zippedArc.asSequence().filter { errors < 10 }
                .forEachIndexed { index, (graal, native) ->

                    val comparison = buildComparisonReport(graal, native, example)

                    if (comparison.errors.isNotEmpty()) errors++

                    withClue(
                        """
============================================================================================
Name:    $name
Pattern: $code

Event ${index + 1} / ${zippedArc.size} must be equal:
--------------------------------------------------------------------------------------------
${comparison.errors.joinToString("\n")}
--------------------------------------------------------------------------------------------
${comparison.report}

                    """.trimIndent()
                    ) {
                        comparison.success shouldBe true
                    }
                }
        }
    }

    private fun buildComparisonReport(
        graal: SprudelPatternEvent,
        native: SprudelPatternEvent,
        example: Example,
    ): ComparisonReport {
        val ignore = example.ignoreFields

        fun isCompatible(path: String, graalElem: String?, nativeElem: String?): ComparisonResult {
            val worst = if (path in ignore) {
                ComparisonResult.IGNORED
            } else if (example.tryRecover(path, graal, native)) {
                ComparisonResult.RECOVERED
            } else {
                ComparisonResult.DIFFERENT
            }

            // Exact match?
            if (graalElem == nativeElem) return ComparisonResult.EXACT
            // One is null and the other one is not?
            if (graalElem == null || nativeElem == null) return worst

            val graalNum = graalElem.toDoubleOrNull()
            val nativeNum = nativeElem.toDoubleOrNull()

            if (graalNum != null && nativeNum != null) {
                val numDiff = abs(graalNum - nativeNum)

                if (numDiff < 1e-3) return ComparisonResult.CLOSE
            }

            if (path == "data.note" && graalElem.equals(nativeElem, ignoreCase = true)) {
                return ComparisonResult.CLOSE
            }

            return worst
        }

        val graalFlat = mutableMapOf<String, String?>().apply { flatten(graal, "", this) }
        val nativeFlat = mutableMapOf<String, String?>().apply { flatten(native, "", this) }

        val allKeys = (graalFlat.keys + nativeFlat.keys)
            .distinct()
            .sortedWith(compareBy({ it.count { c -> c == '.' } }, { it }))

        val errors = mutableListOf<String>()

        val rows = mutableListOf<List<Any?>>()
        rows.add(listOf("", "field", "JS (Graal)", "Native"))

        allKeys.forEach { key ->
            val gVal = graalFlat[key]
            val nVal = nativeFlat[key]
            val result = isCompatible(path = key, graalElem = gVal, nativeElem = nVal)

            if (result == ComparisonResult.DIFFERENT) {
                errors.add(
                    "'$key': JS (Graal) '${gVal?.toString() ?: "null"}' VS Native '${nVal?.toString() ?: "null"}'"
                )
            }

            val resultStr = when (result) {
                ComparisonResult.EXACT -> "    ✓    "
                ComparisonResult.CLOSE -> "   ~==   "
                ComparisonResult.DIFFERENT -> "  ERROR  "
                ComparisonResult.IGNORED -> " IGNORED "
                ComparisonResult.RECOVERED -> " RECOVER "
            }

            rows.add(
                listOf(
                    resultStr,
                    key,
                    gVal?.toString() ?: "null",
                    nVal?.toString() ?: "null"
                )
            )
        }

        return ComparisonReport(
            errors = errors.toList(),
            report = rows.formatAsTable()
        )
    }

    /**
     * Flattens an event into `path -> stringified-leaf` (e.g. `data.gain -> "0.7"`, `part.end -> "1.0"`),
     * mirroring the dotted-path shape the old kotlinx `encodeToJsonElement` flatten produced — so the
     * `ignoreFields` / `recovers(path)` contract in the test data keeps working. Reflection over the data
     * classes (no serialization): scalars/enums/`CycleTime` are leaves, `List`/`Map` recurse by index/key,
     * everything else recurses its (non-static, non-synthetic) declared fields. `sourceLocations` is skipped
     * (it was `@Transient`), and `SprudelVoiceValue` / `SprudelPattern` are leafed to avoid walking patterns.
     */
    private fun flatten(value: Any?, path: String, out: MutableMap<String, String?>) {
        fun child(seg: String) = if (path.isEmpty()) seg else "$path.$seg"
        when (value) {
            null -> out[path] = null
            is CycleTime -> out[path] = value.toDouble().toString()
            is SprudelVoiceValue -> out[path] = value.toString()
            is SprudelPattern -> out[path] = "<pattern>"
            is Number, is Boolean, is CharSequence, is Char -> out[path] = value.toString()
            is Enum<*> -> out[path] = value.name
            is List<*> -> value.forEachIndexed { i, v -> flatten(v, child("$i"), out) }
            is Map<*, *> -> value.forEach { (k, v) -> flatten(v, child("$k"), out) }
            else -> value.javaClass.declaredFields
                .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) && it.name != "sourceLocations" && '$' !in it.name }
                .forEach { f ->
                    f.isAccessible = true
                    flatten(f.get(value), child(f.name), out)
                }
        }
    }

    private fun printEventComparison(graalArc: List<SprudelPatternEvent>, nativeArc: List<SprudelPatternEvent>) {

        val zippedArc = graalArc.zipAll(nativeArc).take(32)

        fun Double.toFixed(n: Int) = "%.${n}f".format(this)

        fun CycleTimeSpan.str() = "${begin.toDouble().toFixed(5)}-${end.toDouble().toFixed(5)}"

        fun SprudelPatternEvent.str(): String {
            val parts = listOf(
                data.value?.asString,
                data.note,
                data.sound,
            )

            return parts.joinToString(" | ")
        }

        val rows = listOf(
            listOf(
                "#",
                "JS part",
                "JS whole",
                "JS Data",
                "JS hash",
                "Kotlin part",
                "Kotlin whole",
                "Kotlin Data",
                "Kotlin hash"
            )
        )
            .plus(
                zippedArc.mapIndexed { index, (graal, native) ->
                    listOf(
                        index,
                        graal?.part?.str(),
                        graal?.whole?.str(),
                        graal?.str(),
                        graal?.hashCode().toString(),
                        native?.part?.str(),
                        native?.whole?.str(),
                        native?.str(),
                        native?.hashCode().toString(),
                    )
                }
            )

        println(rows.formatAsTable())
    }

    private fun <T, R> Iterable<T>.zipAll(other: Iterable<R>): List<Pair<T?, R?>> {
        val firstItr = this.iterator()
        val secondItr = other.iterator()
        val result = mutableListOf<Pair<T?, R?>>()

        while (firstItr.hasNext() || secondItr.hasNext()) {
            val firstNext = if (firstItr.hasNext()) firstItr.next() else null
            val secondNext = if (secondItr.hasNext()) secondItr.next() else null
            result.add(firstNext to secondNext)
        }
        return result
    }
}
