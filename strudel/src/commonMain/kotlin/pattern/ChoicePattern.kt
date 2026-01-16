package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

internal class ChoicePattern(
    val selector: StrudelPattern,
    val choices: List<StrudelPattern>,
    val weights: List<StrudelPattern>? = null,
    val mode: StructurePattern.Mode = StructurePattern.Mode.Out,
) : StrudelPattern {

    companion object {
        // This matches what the parser calls: pattern.choice(right)
        // For the parser '|' operator, we likely want a random choice.
        // We can model this as a ChoicePattern with a random selector.
        // But the parser constructs it recursively.
        // If we want to keep the old behavior of pattern.choice(other) meaning "pick one of these randomly per cycle/query",
        // we can use a specific selector.
        // The previous implementation used `ctx.getSeededRandom` which is effectively `rand.segment(1)`.

        fun StrudelPattern.choice(other: StrudelPattern): StrudelPattern {
            // Flatten both sides to ensure equal probability for all items in a sequence like a|b|c
            // BUT: ChoicePattern structure changed. It now has a selector.
            // If `this` is already a ChoicePattern with a specific "random per cycle" selector, we can merge choices.
            // But if it's a general ChoicePattern (e.g. controlled by sine), merging is tricky.

            // For the parser's '|' operator, we assume we are building a list of choices to be picked randomly.
            // We can check if `this` is a "simple random choice" pattern.

            // For simplicity in this refactor, let's just collect the choices and creating a NEW pattern
            // that picks randomly.
            // We assume the user wants `rand.segment(1)` behavior for `|`.

            val leftChoices = if (this is ChoicePattern && this.isSimpleRandom) {
                this.choices
            } else {
                listOf(this)
            }

            val rightChoices = if (other is ChoicePattern && other.isSimpleRandom) {
                other.choices
            } else {
                listOf(other)
            }

            return create(
                selector = rand.segment(1),
                choices = leftChoices + rightChoices,
                mode = StructurePattern.Mode.In // Usually random choices like this preserve inner structure? Or Out?
            )
        }

        fun create(
            selector: StrudelPattern,
            choices: List<StrudelPattern>,
            weights: List<StrudelPattern>? = null,
            mode: StructurePattern.Mode = StructurePattern.Mode.Out,
        ): ChoicePattern {
            return ChoicePattern(selector, choices, weights, mode)
        }

        // Helper to handle raw lists
        fun createFromRaw(
            selector: StrudelPattern,
            choices: List<Any?>,
            weights: List<Any?>? = null,
            mode: StructurePattern.Mode = StructurePattern.Mode.Out,
        ): StrudelPattern {
            val choicePatterns = choices.toListOfPatterns(defaultModifier)
            if (choicePatterns.isEmpty()) return silence

            val weightPatterns = weights?.toListOfPatterns(defaultModifier)

            return ChoicePattern(selector, choicePatterns, weightPatterns, mode)
        }
    }

    // Helper to identify if this pattern was created via the simple `|` operator logic (random per cycle)
    // allowing us to flatten nested choices.
    private val isSimpleRandom: Boolean
        get() = weights == null // && selector is rand.segment(1) (hard to check equality, but null weights is a good proxy for basic |)

    override val weight: Double get() = selector.weight

    override val steps: Rational? get() = selector.steps

    override fun estimateCycleDuration(): Rational = selector.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        // Query the selector to find out which choice to pick at what time
        val selectorEvents = selector.queryArcContextual(from, to, ctx)

        return selectorEvents.flatMap { ev ->
            val t = ev.data.value?.asDouble ?: 0.0

            val selectedIndex = if (weights != null) {
                // Weighted choice
                // We map weights to double values at the start of the event
                val currentWeights = weights.map { wp ->
                    // Sample weight at event start.
                    // Using a tiny epsilon to ensure we are "inside" the event if it starts exactly at `from`.
                    val eps = 0.0001.toRational()
                    val wEvs = wp.queryArcContextual(ev.begin, ev.begin + eps, ctx)
                    wEvs.firstOrNull()?.data?.value?.asDouble ?: 0.0
                }

                val totalWeight = currentWeights.sum()
                val r = t // selector value 0..1
                var sum = 0.0
                var found = -1
                for ((i, w) in currentWeights.withIndex()) {
                    sum += w
                    if (r * totalWeight < sum) {
                        found = i
                        break
                    }
                }
                if (found == -1) currentWeights.lastIndex else found
            } else {
                // Uniform choice: Map 0..1 to 0..n-1
                if (t >= 1.0) choices.lastIndex
                else (t * choices.size).toInt().coerceIn(0, choices.lastIndex)
            }

            val chosenPat = choices.getOrNull(selectedIndex) ?: return@flatMap emptyList()

            // Mode.Out (default for chooseWith): Constrain to selector's timeframe.
            // Mode.In (default for chooseInWith): Preserve inner structure?
            // In Strudel JS, innerJoin vs outerJoin determines where the 'whole' comes from.
            // For playback purposes (queryArc), both effectively restrict the events to the window
            // where the selector was active.
            // We pass the selector event's timeframe to query the chosen pattern.

            chosenPat.queryArcContextual(ev.begin, ev.end, ctx)
        }
    }
}
