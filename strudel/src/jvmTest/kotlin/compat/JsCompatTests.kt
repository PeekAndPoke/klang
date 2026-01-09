package io.peekandpoke.klang.strudel.compat

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.test.logging.warn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.graal.GraalStrudelCompiler
import io.peekandpoke.klang.strudel.printAsTable
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
    }

    private val graalCompiler = GraalStrudelCompiler()
    private val json = Json { prettyPrint = true }

    init {
        // Testing that simple pattern code produces the same results
        JsCompatTestData.simplePatterns.forEachIndexed { index, (shouldRun, name, code) ->
            "Simple Pattern ${index + 1}: $name" {
                if (shouldRun) {
                    runComparison(name, code)
                } else {
                    warn { "Skipping test '$name' because it's marked as 'shouldRun = false'" }
                }
            }
        }

        // Testing that songs code produces the same results
        JsCompatTestData.songs.forEachIndexed { index, (shouldRun, name, code) ->
            "Song ${index + 1}: $name" {
                if (shouldRun) {
                    runComparison(name, code)
                } else {
                    warn { "Skipping test '$name' because it's marked as 'shouldRun = false'" }
                }
            }
        }
    }

    private suspend fun runComparison(name: String, code: String) {
        val graalPattern = withClue("Compiling '$name' with GraalVM") {
            val result = try {
                graalCompiler.compile(code).await()
            } catch (e: Throwable) {
                fail("Failed to compile '$name' with GraalVM", e)
            }

            result.shouldNotBeNull()
        }

        val nativePattern = withClue("Compiling song '$name' natively") {
            StrudelPattern.compile(code)
                ?: fail("Failed to compile song '$name' natively")
        }

        fun List<StrudelPatternEvent>.sort() = sortedWith(compareBy({ it.begin }, { it.end }, { it.hashCode() }))

        val graalArc = graalPattern.queryArc(0.0, 64.0).sort()
        val nativeArc = nativePattern.queryArc(0.0, 64.0).sort()

        assertSoftly {
            withClue("Number of events must match | Graal: ${graalArc.size} VS Native: ${nativeArc.size}") {
                graalArc.size shouldBe nativeArc.size
            }

            val zippedArc = graalArc.zip(nativeArc)

            var errors = 0

            zippedArc.asSequence().filter { errors < 10 }
                .forEachIndexed { index, (graal, native) ->

                    val comparison = buildComparisonReport(graal, native)

                    if (comparison.errors.isNotEmpty()) errors++

                    withClue(
                        """
============================================================================================
Event $index must be equal:
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

    private fun buildComparisonReport(graal: StrudelPatternEvent, native: StrudelPatternEvent): ComparisonReport {

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

        fun isCompatible(graal: JsonPrimitive?, native: JsonPrimitive?): ComparisonResult {
            // Exact match?
            if (graal == native) return ComparisonResult.EXACT
            // One is null and the other one is not?
            if (graal == null || native == null) return ComparisonResult.DIFFERENT

            val graalNum = graal.doubleOrNull
            val nativeNum = native.doubleOrNull

            if (graalNum != null && nativeNum != null) {
                val numDiff = abs(graalNum - nativeNum)

                if (numDiff < EPSILON) return ComparisonResult.CLOSE
            }

            return ComparisonResult.DIFFERENT
        }

        val graalFlat = mutableMapOf<String, JsonPrimitive>().apply { flattenJson(graalJson, "", this) }
        val nativeFlat = mutableMapOf<String, JsonPrimitive>().apply { flattenJson(nativeJson, "", this) }

        val allKeys = (graalFlat.keys + nativeFlat.keys)
            .distinct()
            .sortedWith(compareBy({ it.count { c -> c == '.' } }, { it }))

        val errors = mutableListOf<String>()

        val rows = mutableListOf<List<Any?>>()
        rows.add(listOf("", "field", "Graal", "Native"))

        allKeys.forEach { key ->
            val gVal = graalFlat[key]
            val nVal = nativeFlat[key]
            val result = isCompatible(gVal, nVal)

            if (result == ComparisonResult.DIFFERENT) {
                errors.add(
                    "'$key': Graal '${gVal?.toString() ?: "null"}' VS Native '${nVal?.toString() ?: "null"}'"
                )
            }

            val resultStr = when (result) {
                ComparisonResult.EXACT -> "  ✓  "
                ComparisonResult.CLOSE -> " ~== "
                ComparisonResult.DIFFERENT -> "ERROR"
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
            report = rows.printAsTable()
        )
    }
}

/*
       | field                               | Graal                | Native
-------+-------------------------------------+----------------------+---------------------
       | begin                               | 1.0                  | 1.0
       | end                                 | 2.0                  | 2.0
       | ...
       | data.note                           | "c3"                 | "c3"
 ERROR | data.freqHz                         | 100.0                | 200.0
       | ...
       | filters.0._type                     | low-pass             | low-pass
 ERROR | filters.0.cutoffHz                  | null                 | 1000.0
 ...

 */
