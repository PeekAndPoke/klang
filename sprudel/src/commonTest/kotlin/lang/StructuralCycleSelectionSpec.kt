package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.sampleAt
import io.peekandpoke.klang.sprudel.soundName

/**
 * Hunts the structural cycle-selection bug.
 *
 * A slowcat `<a0 … a(N-1)>` compiles to `seq(N).slow(N)` (MnPatternToSprudelPattern.alternationToPattern):
 * at output cycle `c` the active item MUST be `c mod N`. For `N` that does NOT divide [CycleTime.T],
 * `slow()`'s `scaleBy(1/N)` rounds, and the floored step selection may pick the wrong item at some cycle.
 *
 * We scan lengths `N = 1..MAX_N` over many cycles, checking the active item two ways:
 *  1. queryArc — the raw structural query (which item the cycle resolves to),
 *  2. sampleAt — exactly what the shared control sampler (`_applyControl` → `sampleAt(whole.begin)`) does,
 *     so we can tell whether a failure is in the slowcat itself or specific to the control path.
 *
 * Records the FIRST failing cycle per (length, method) and asserts there are none.
 */
class StructuralCycleSelectionSpec : StringSpec({

    // The N∤T misselection appears at cycle 1-4, but we scan well past 1000 cycles as a standing guard
    // (per the "watch many cycles" requirement) against any future high-cycle boundary regression.
    val cycles = 2_000
    val maxN = 48
    val ctx = SprudelPattern.QueryContext()

    fun dividesT(n: Int): Boolean = CycleTime.T % n.toDouble() == 0.0

    data class Fail(val n: Int, val method: String, val cycle: Int, val expected: String, val got: String?)

    "slowcat <s0..s(N-1)> selects item (c mod N) for every cycle and length" {
        val fails = mutableListOf<Fail>()

        for (n in 1..maxN) {
            val atoms = (0 until n).joinToString(" ") { "s$it" }
            val pat = sound("<$atoms>")

            var qFail: Fail? = null
            var sFail: Fail? = null

            for (c in 0 until cycles) {
                val expected = "s${c % n}"

                if (qFail == null) {
                    val got = pat.queryArc(c.toDouble(), c + 1.0)
                        .filter { it.isOnset }.firstOrNull()?.data?.soundName
                    if (got != expected) qFail = Fail(n, "queryArc", c, expected, got)
                }
                if (sFail == null) {
                    val got = pat.sampleAt(c.toDouble(), ctx)?.data?.soundName
                    if (got != expected) sFail = Fail(n, "sampleAt", c, expected, got)
                }
                if (qFail != null && sFail != null) break
            }

            qFail?.let { fails.add(it) }
            sFail?.let { fails.add(it) }
        }

        val failingNs = fails.map { it.n }.toSet()
        val report = buildString {
            appendLine("CycleTime.T = ${CycleTime.T}  (scanned N=1..$maxN over $cycles cycles)")
            appendLine("Failing (first failing cycle per method):")
            fails.sortedWith(compareBy({ it.n }, { it.method })).forEach { f ->
                appendLine("  N=${f.n} (divides T=${dividesT(f.n)})  ${f.method} @cycle ${f.cycle}: expected ${f.expected}, got ${f.got}")
            }
            appendLine("Failing N: ${failingNs.sorted()}")
            appendLine("Passing  N: ${(1..maxN).filter { it !in failingNs }}")
        }
        println(report)

        withClue(report) { fails.shouldBeEmpty() }
    }
})
