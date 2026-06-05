package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Mini-notation alternation `<a b c>`: item `i` plays for one full cycle, cycling every `N` cycles.
 *
 * Exact replacement for the former `seq(items).slow(N)` encoding. That encoding scaled time by `1/N`,
 * which **rounds** to the tick grid whenever `N` does not divide [CycleTime.T] — and the rounded cycle
 * boundary then dropped or misselected the active item. Empirically (StructuralCycleSelectionSpec) this
 * broke for *every* non-divisor length (9, 11, 13, 17, 18, 19, …), and since controls (`scale`/`gain`/
 * `lpf`) sample a `<…>` via the shared control sampler, the wrong item leaked everywhere.
 *
 * Here the active item is chosen by **exact integer cycle index** (`c mod N`) and placed with
 * **integer-tick shifts only** — no scaling, no rounding — so it is correct for all `N`.
 *
 * Semantics preserved from `seq(items).slow(N)`: at output cycle `c` the active item is
 * `items[c mod N]`, queried at the item's OWN cycle `floor(c / N)` (each item advances once per full
 * lap), then shifted back into `[c, c+1)`. For atoms and fixed sub-cycle groups (the common case) the
 * inner cycle is immaterial; the lap index only matters for inner patterns that evolve across cycles.
 */
internal class AlternationPattern(
    private val items: List<SprudelPattern>,
) : SprudelPattern.FixedWeight {

    private val n = items.size

    override val numSteps: Double get() = n.toDouble()

    override fun estimateCycleDuration(): Double = n.toDouble()

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        if (n == 0) return emptyList()

        val result = createEventList()

        val startCycle = from.cycleIndex()
        val endCycle = to.ceilToCycle().cycleIndex()

        for (c in startCycle until endCycle) {
            val item = items[c.mod(n)]
            val lap = c.floorDiv(n) // which cycle of the item plays (items advance once per lap)

            val cycleStart = CycleTime.ofCycleIndex(c)
            val cycleEnd = cycleStart + CycleTime.ONE

            val queryStart = from.coerceAtLeast(cycleStart)
            val queryEnd = to.coerceAtMost(cycleEnd)
            if (queryEnd <= queryStart) continue

            // Output cycle c shows the item's cycle `lap`; shift between them is an exact whole-cycle
            // count: (c - lap) * T. Map the query window into item-local time, then shift events back.
            val shift = CycleTime.ofCycleIndex(c - lap)
            val itemEvents = item.queryArcContextual(queryStart - shift, queryEnd - shift, ctx)

            itemEvents.forEach { ev ->
                result.add(
                    ev.copy(
                        part = ev.part.shift(shift),
                        whole = ev.whole.shift(shift),
                    )
                )
            }
        }

        return result
    }
}
