package io.peekandpoke.klang.strudel.compat

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.test.logging.warn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.graal.GraalStrudelCompiler
import io.peekandpoke.klang.strudel.printAsTable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.fail

@Suppress("OPT_IN_USAGE")
class JsCompatTests : StringSpec() {

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
        val graalPattern = withClue("Compiling song '$name' with GraalVM") {
            val result = try {
                val graalCompiler = GraalStrudelCompiler()
                graalCompiler.compile(code).await()
            } catch (e: Throwable) {
                fail("Failed to compile song '$name' with GraalVM", e)
            }

            result.shouldNotBeNull()
        }

        val nativePattern = withClue("Compiling song '$name' natively") {
            StrudelPattern.compile(code)
                ?: fail("Failed to compile song '$name' natively")
        }

        val graalArc = graalPattern.queryArc(0.0, 16.0)
        val nativeArc = nativePattern.queryArc(0.0, 16.0)

        assertSoftly {
            withClue("Number of events must match") {
                graalArc.size shouldBe nativeArc.size
            }

            val zippedArc = graalArc.zip(nativeArc)

            zippedArc.forEachIndexed { index, (graal, native) ->

                val comparison = buildComparison(graal, native)

                withClue("Event $index must be equal:\n\n${comparison}\n") {
                    graal shouldBe native
                }
            }
        }
    }

    private fun buildComparison(graal: StrudelPatternEvent, native: StrudelPatternEvent): String {

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

        val graalFlat = mutableMapOf<String, JsonPrimitive>().apply { flattenJson(graalJson, "", this) }
        val nativeFlat = mutableMapOf<String, JsonPrimitive>().apply { flattenJson(nativeJson, "", this) }

        val allKeys = (graalFlat.keys + nativeFlat.keys).distinct().sorted()

        val rows = mutableListOf<List<Any?>>()
        rows.add(listOf("", "field", "Graal", "Native"))

        allKeys.forEach { key ->
            val gVal = graalFlat[key]
            val nVal = nativeFlat[key]
            val isError = gVal != nVal

            rows.add(
                listOf(
                    if (isError) "ERROR" else "",
                    key,
                    gVal?.toString() ?: "null",
                    nVal?.toString() ?: "null"
                )
            )
        }

        return rows.printAsTable()
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
