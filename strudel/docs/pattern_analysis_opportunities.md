# Pattern Class Analysis - Further Consolidation Opportunities

## Pattern Categories

### 1. Property Override Wrappers

**Pattern**: Just override one property, delegate everything else

```kotlin
// StepsOverridePattern (12 lines)
internal class StepsOverridePattern(
    private val source: StrudelPattern,
    override val steps: Rational,
) : StrudelPattern by source

// WeightedPattern (23 lines)
internal class WeightedPattern(
    private val inner: StrudelPattern,
    override val weight: Double,
) : StrudelPattern {
    override val steps: Rational? get() = inner.steps
    override fun estimateCycleDuration(): Rational = inner.estimateCycleDuration()
    override fun queryArcContextual(...) = inner.queryArcContextual(...)
}
```

**Opportunity**: Create generic `PropertyOverridePattern`

---

### 2. Context Modifiers

**Pattern**: Modify query context before delegating to source

```kotlin
// ContextModifierPattern (33 lines) - ALREADY HAS EXTENSION!
internal class ContextModifierPattern(
    val source: StrudelPattern,
    val modifier: QueryContext.Updater.() -> Unit,
) : StrudelPattern {
    companion object {
        fun StrudelPattern.withContext(modifier: ...) = ContextModifierPattern(...)
    }
    override fun queryArcContextual(...) {
        val updated = ctx.update(modifier)
        return source.queryArcContextual(from, to, updated)
    }
}

// ContextRangeMapPattern (35 lines) - SPECIFIC USE CASE
// Transforms min/max range values in context
```

**Status**: ✅ Already has `withContext()` extension. ContextRangeMapPattern is specific enough to keep.

---

### 3. Event Transformers

**Pattern**: Query source, transform each individual event

```kotlin
// ReinterpretPattern (36 lines) - ALREADY HAS EXTENSIONS!
class ReinterpretPattern(
    val source: StrudelPattern,
    val interpret: (StrudelPatternEvent, QueryContext) -> StrudelPatternEvent,
) : StrudelPattern {
    companion object {
        fun StrudelPattern.reinterpret(interpret: ...) = ReinterpretPattern(...)
        fun StrudelPattern.reinterpretVoice(interpret: ...) = ReinterpretPattern(...)
    }
    override fun queryArcContextual(...) {
        return source.queryArcContextual(...).map { interpret(it, ctx) }
    }
}
```

**Opportunity**: Could add `StrudelPattern.mapEvents()` extension that creates ReinterpretPattern

---

### 4. Pattern Combiners

**Pattern**: Query multiple patterns and combine results

```kotlin
// StackPattern (32 lines)
internal class StackPattern(val patterns: List<StrudelPattern>) {
    override fun queryArcContextual(...) {
        return patterns
            .flatMap { it.queryArcContextual(from, to, ctx) }
            .sortedBy { it.begin }
    }
}

// SuperimposePattern (35 lines)
internal class SuperimposePattern(
    val source: StrudelPattern,
    val transform: (StrudelPattern) -> StrudelPattern,
) {
    override fun queryArcContextual(...) {
        val originalEvents = source.queryArcContextual(from, to, ctx)
        val superimposedEvents = transform(source).queryArcContextual(from, to, ctx)
        return originalEvents + superimposedEvents
    }
}

// OffPattern (40 lines)
internal class OffPattern(
    val source: StrudelPattern,
    val time: Double,
    val transform: (StrudelPattern) -> StrudelPattern,
) {
    private val transformed = transform(source.late(time))

    override fun queryArcContextual(...) {
        return source.queryArcContextual(from, to, ctx) +
               transformed.queryArcContextual(from, to, ctx).filter { it.begin >= from }
    }
}
```

**Opportunity**: Create generic `CombinePattern` or pattern combiner helpers

---

## Proposed New Helpers

### 1. PropertyOverridePattern (HIGHEST IMPACT)

```kotlin
/**
 * Generic pattern that overrides specific properties while delegating to source.
 */
internal class PropertyOverridePattern(
    private val source: StrudelPattern,
    private val weightOverride: Double? = null,
    private val stepsOverride: Rational? = null,
    private val cycleDurationOverride: Rational? = null,
) : StrudelPattern {
    override val weight: Double get() = weightOverride ?: source.weight
    override val steps: Rational? get() = stepsOverride ?: source.steps
    override fun estimateCycleDuration(): Rational = cycleDurationOverride ?: source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext) =
        source.queryArcContextual(from, to, ctx)
}

// Extension functions
fun StrudelPattern.withWeight(weight: Double): StrudelPattern =
    PropertyOverridePattern(this, weightOverride = weight)

fun StrudelPattern.withSteps(steps: Rational): StrudelPattern =
    PropertyOverridePattern(this, stepsOverride = steps)
```

**Impact**: Eliminates WeightedPattern (23 lines) and StepsOverridePattern (12 lines) = 35 lines saved

---

### 2. StrudelPattern.mapEvents() Extension

```kotlin
/**
 * Creates a pattern that maps individual events.
 * Convenience extension for creating ReinterpretPattern.
 */
fun StrudelPattern.mapEvents(
    transform: (StrudelPatternEvent) -> StrudelPatternEvent
): StrudelPattern =
    ReinterpretPattern(this) { evt, _ -> transform(evt) }

fun StrudelPattern.mapEventsWithContext(
    transform: (StrudelPatternEvent, QueryContext) -> StrudelPatternEvent
): StrudelPattern =
    ReinterpretPattern(this, transform)
```

**Impact**: Makes ReinterpretPattern usage more discoverable and consistent with `map()`

---

### 3. CombinePattern (MODERATE IMPACT)

```kotlin
/**
 * Generic pattern that combines results from multiple pattern sources.
 */
internal class CombinePattern(
    private val sources: List<() -> StrudelPattern>,
    private val combiner: (List<List<StrudelPatternEvent>>) -> List<StrudelPatternEvent>,
    private val weight: Double = 1.0,
    private val steps: Rational? = null,
) : StrudelPattern {
    override val weight: Double get() = this.weight
    override val steps: Rational? get() = this.steps

    override fun estimateCycleDuration(): Rational {
        val patterns = sources.map { it() }
        return patterns.maxOfOrNull { it.estimateCycleDuration() } ?: Rational.ONE
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val allEvents = sources.map { it().queryArcContextual(from, to, ctx) }
        return combiner(allEvents)
    }
}

// Extension
fun StrudelPattern.stack(vararg others: StrudelPattern): StrudelPattern {
    val allPatterns = listOf(this) + others
    return CombinePattern(
        sources = allPatterns.map { p -> { p } },
        combiner = { eventLists -> eventLists.flatten().sortedBy { it.begin } }
    )
}

fun StrudelPattern.superimpose(transform: (StrudelPattern) -> StrudelPattern): StrudelPattern =
    CombinePattern(
        sources = listOf({ this }, { transform(this) }),
        combiner = { (original, superimposed) -> original + superimposed }
    )
```

**Impact**: Could potentially replace SuperimposePattern (35 lines), but OffPattern is complex enough to keep

---

### 4. StrudelPattern.layer() Extension (SIMPLER ALTERNATIVE)

Instead of CombinePattern, just add extensions:

```kotlin
/**
 * Stacks this pattern with others, playing all simultaneously.
 */
fun StrudelPattern.stack(vararg others: StrudelPattern): StrudelPattern =
    StackPattern(listOf(this) + others.toList())

/**
 * Alias for creating StackPattern from list.
 */
fun stack(vararg patterns: StrudelPattern): StrudelPattern =
    StackPattern(patterns.toList())
```

**Impact**: Makes StackPattern more discoverable, no code reduction

---

## Summary of Recommendations

### ✅ RECOMMENDED - High Value

1. **PropertyOverridePattern + Extensions** (withWeight, withSteps)
    - Eliminates: WeightedPattern, StepsOverridePattern
    - Saves: ~35 lines
    - Benefit: More flexible, reusable property overriding

2. **StrudelPattern.mapEvents()** Extension
    - Doesn't eliminate ReinterpretPattern (still needed internally)
    - Saves: 0 lines
    - Benefit: More discoverable, consistent API with `map()`

3. **StrudelPattern.stack()** Extension
    - Doesn't eliminate StackPattern
    - Saves: 0 lines
    - Benefit: More convenient API for users

### ⚠️ MAYBE - Moderate Value

4. **Generic CombinePattern**
    - Could eliminate SuperimposePattern
    - Saves: ~35 lines
    - Risk: Might be over-engineered, less clear than specific classes

### ❌ NOT RECOMMENDED - Keep As-Is

- ContextModifierPattern - Already has `withContext()` extension ✅
- ReinterpretPattern - Already has extensions, complex enough to keep ✅
- OffPattern - Too specialized (cycle-aware filtering)
- StackPattern - Simple and clear, used directly by DSL
- Most other patterns - Have complex, specialized logic

---

## Total Potential Savings

**Conservative estimate**:

- PropertyOverridePattern: ~35 lines saved
- Extensions for mapEvents/stack: ~20 lines added
- **Net: ~15 lines saved, but significantly better API consistency**

**Aggressive estimate** (with CombinePattern):

- PropertyOverridePattern: ~35 lines saved
- SuperimposePattern replaced: ~35 lines saved
- CombinePattern + extensions: ~40 lines added
- **Net: ~30 lines saved**

---

## Design Philosophy

The key question: **When to create a specific class vs. generic pattern?**

**Create specific class when**:

- Logic is complex (>30 lines)
- Has specialized behavior (TimeShiftPattern, ReversePattern)
- Performance matters (caching, optimization)
- Clear domain concept (EuclideanPattern, ArrangementPattern)

**Use generic pattern when**:

- Simple delegation with 1-2 line differences
- Common operation across many patterns
- Can be expressed as composition of existing helpers

**Current status**: Already in good shape! Main opportunities are property overrides and API consistency.
