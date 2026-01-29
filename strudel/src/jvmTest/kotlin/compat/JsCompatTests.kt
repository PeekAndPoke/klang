package io.peekandpoke.klang.strudel.compat

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.test.logging.warn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.formatAsTable
import io.peekandpoke.klang.strudel.graal.GraalStrudelCompiler
import kotlinx.serialization.json.*
import org.junit.jupiter.api.fail
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

    private val graalCompiler = GraalStrudelCompiler()
    private val json = Json { prettyPrint = true }

    init {
        // Testing that simple pattern code produces the same results
        JsCompatTestData.patterns.forEachIndexed { index, example ->
            "Simple Pattern ${index + 1}: ${example.name}" {
                if (!example.skip) {
                    runComparison(example)
                } else {
                    warn { "Skipping test '${example.name}' because it's marked as 'shouldRun = false'" }
                }
            }
        }

        // Testing that songs code produces the same results
        JsCompatTestSongs.songs.forEachIndexed { index, example ->
            "Song ${index + 1}: ${example.name}" {
                if (!example.skip) {
                    runComparison(example)
                } else {
                    warn { "Skipping test '${example.name}' because it's marked as 'shouldRun = false'" }
                }
            }
        }
    }

    private suspend fun runComparison(example: Example) {
        val name = example.name
        val code = example.code

        val nativePattern = withClue("Compiling '$name' natively") {
            StrudelPattern.compile(code)
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

        fun List<StrudelPatternEvent>.sort() = sortedWith(
            compareBy(
                { it.begin.toDouble() },
                { it.data.note },
            )
        )

        val numCycles = 32

        val graalArc = (0..<numCycles)
            .flatMap { graalPattern.queryArc(it.toDouble(), (it + 1).toDouble()) }.sort()

        val nativeArc = (0..<numCycles)
            .flatMap { nativePattern.queryArc(it.toDouble(), (it + 1).toDouble()) }.sort()

        assertSoftly {
            withClue("Number of events must match | JS (Graal): ${graalArc.size} VS Native: ${nativeArc.size}") {
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
        graal: StrudelPatternEvent,
        native: StrudelPatternEvent,
        example: Example,
    ): ComparisonReport {
        val ignore = example.ignoreFields

        val graalJson = json.encodeToJsonElement(graal)
        val nativeJson = json.encodeToJsonElement(native)

        fun flattenJson(
            element: JsonElement,
            path: String = "",
            result: MutableMap<String, JsonPrimitive> = mutableMapOf(),
        ) {
            when (element) {
                is JsonPrimitive -> result[path] = element
                is JsonObject -> {
                    element.forEach { (key, value) ->
                        val newPath = if (path.isEmpty()) key else "$path.$key"
                        flattenJson(value, newPath, result)
                    }
                }

                is JsonArray -> {
                    element.forEachIndexed { index, value ->
                        val newPath = if (path.isEmpty()) "$index" else "$path.$index"
                        flattenJson(value, newPath, result)
                    }
                }
            }
        }

        fun isCompatible(path: String, graalElem: JsonPrimitive?, nativeElem: JsonPrimitive?): ComparisonResult {
            val worst = if (path in ignore) {
                ComparisonResult.IGNORED
            } else if (example.recover(graal, native)) {
                ComparisonResult.RECOVERED
            } else {
                ComparisonResult.DIFFERENT
            }

            // Exact match?
            if (graalElem == nativeElem) return ComparisonResult.EXACT
            // One is null and the other one is not?
            if (graalElem == null || nativeElem == null) return worst

            val graalNum = graalElem.doubleOrNull
            val nativeNum = nativeElem.doubleOrNull

            if (graalNum != null && nativeNum != null) {
                val numDiff = abs(graalNum - nativeNum)

                if (numDiff < 1e-3) return ComparisonResult.CLOSE
            }

            val graalStr = graalElem.contentOrNull
            val nativeStr = nativeElem.contentOrNull

            if (path == "data.note" && graalStr != null && nativeStr != null) {
                if (graalStr.equals(nativeStr, ignoreCase = true)) {
                    return ComparisonResult.CLOSE
                }
            }

            return worst
        }

        val graalFlat = mutableMapOf<String, JsonPrimitive>()
            .apply { flattenJson(graalJson, "", this) }

        val nativeFlat = mutableMapOf<String, JsonPrimitive>()
            .apply { flattenJson(nativeJson, "", this) }

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
