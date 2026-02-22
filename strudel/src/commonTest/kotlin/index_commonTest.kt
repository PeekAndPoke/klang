package io.peekandpoke.klang.strudel

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.math.max

const val EPSILON = 1e-5

fun dslInterfaceTests(
    vararg cases: Pair<String, StrudelPattern?>,
    assertions: (cycle: Double, events: List<StrudelPatternEvent>) -> Unit,
) {
    assertSoftly {
        cases.forEach { (name, pattern) ->
            pattern.shouldNotBeNull()

            (0..16).forEach { cycle ->

                withClue("$name | cycle: $cycle - ${cycle + 1}") {
                    val cycleDbl = cycle.toDouble()
                    val events = pattern.queryArc(cycleDbl, cycleDbl + 1)

                    assertSoftly {
                        assertions(cycleDbl, events)
                    }
                }
            }
        }
    }
}


fun List<List<Any?>>.formatAsTable(lineBreak: String = "\n"): String {

    val maxLengths = mutableMapOf<Int, Int>()

    forEach { row ->
        row.forEachIndexed { idx, it ->
            maxLengths[idx] = max(maxLengths[idx] ?: 0, it?.toString()?.length ?: 0)
        }
    }

    fun printRow(cells: List<Any?>, pad: Char = ' ', join: String = " | "): String {

        val asLines = cells.map { it.toString().lines() }
        val maxLines = asLines.maxOf { it.size }

        val result = (0 until maxLines).map { line ->
            asLines.mapIndexed { idx, lines ->
                (lines.getOrNull(line) ?: "").padEnd(maxLengths[idx] ?: 0, pad)
            }.joinToString(join)
        }

        return result.joinToString(lineBreak)
    }

    val header = firstOrNull()
    val data = drop(1)

    val all = listOfNotNull(
        header?.let { printRow(it) },
        header?.let { printRow(it.map { "" }, '-', "-+-") },
    ).plus(data.map { printRow(it) })

    return all.joinToString(lineBreak)
}
