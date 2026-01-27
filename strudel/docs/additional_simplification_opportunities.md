# Additional Simplification Opportunities

## Current Status

We've already simplified significantly:

- ✅ 5 pattern classes eliminated (137 lines)
- ✅ 3 generic pattern classes created
- ✅ 8 extension functions added
- ✅ 41 test cases written

## Analysis of Remaining Patterns

### Category 1: Candidates for Further Simplification

#### 1. **SuperimposePattern** (35 lines) - MAYBE

**Current Implementation:**

```kotlin
internal class SuperimposePattern(
    val source: StrudelPattern,
    val transform: (StrudelPattern) -> StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = source.weight
    override val steps: Rational? get() = source.steps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(...) {
        val originalEvents = source.queryArcContextual(from, to, ctx)
        val superimposedEvents = try {
            transform(source).queryArcContextual(from, to, ctx)
        } catch (e: Exception) {
            println("Failed to superimpose pattern: ${e.stackTraceToString()}")
            emptyList()
        }
        return originalEvents + superimposedEvents
    }
}
```

**Potential Replacement:**

```kotlin
// Could replace with .map() extension
val StrudelPattern.superimpose by dslPatternExtension { p, args, _ ->
    val transform: (StrudelPattern) -> StrudelPattern = ...

    p.map { originalEvents ->
        val superimposedEvents = try {
            transform(p).queryArcContextual(from, to, ctx)  // Problem: no access to from/to/ctx here!
        } catch (e: Exception) {
            emptyList()
        }
        originalEvents + superimposedEvents
    }
}
```

**Problem**: MapPattern's transform function doesn't have access to `from`, `to`, `ctx` - it only gets the event list.
We'd need a new pattern type or enhancement.

**Verdict**: ❌ **Keep SuperimposePattern** - it needs access to query parameters that `.map()` doesn't provide.

---

#### 2. **OffPattern** (40 lines) - NO

**Current**: Specialized cycle-aware filtering for delayed events

**Verdict**: ❌ **Keep OffPattern** - Too specialized, has cycle-aware logic that doesn't fit generic patterns

---

#### 3. **ContextRangeMapPattern** (35 lines) - NO

**Current**: Specific logic for remapping ContinuousPattern range values

**Verdict**: ❌ **Keep ContextRangeMapPattern** - Specific enough to warrant its own class

---

### Category 2: Patterns That Could Use Existing Helpers

Let me check if any patterns are manually implementing bind/applyControl logic...

#### **FirstOfPattern** (92 lines) - Complex cycle logic

- Has custom cycle-based transformation logic
- ❌ Too specialized for helpers

#### **PickSqueezePattern** (82 lines) - Time compression

- Compresses inner pattern into outer event
- ❌ Too specialized (not a simple bind)

#### **PickRestartPattern** (82 lines) - Time shifting

- Maps time relative to event start
- ❌ Too specialized

#### **PickResetPattern** (80 lines) - Cycle-aware shifting

- Maps time based on cycle position
- ❌ Too specialized

---

### Category 3: Already Optimized

- ✅ **ControlPattern** - Uses `applyControl()`
- ✅ **StructurePattern** - Uses `bind()` for Mode.Out
- ✅ **MapPattern** - Already generic
- ✅ **BindPattern** - Already generic
- ✅ **PropertyOverridePattern** - Already generic

---

## Potential New Helpers

### 1. **QueryContextAwareMapPattern** (NEW)

If we wanted to eliminate SuperimposePattern, we'd need:

```kotlin
/**
 * Like MapPattern but provides access to query parameters.
 */
internal class QueryContextAwareMapPattern(
    private val source: StrudelPattern,
    private val transform: (
        source: StrudelPattern,
        sourceEvents: List<StrudelPatternEvent>,
        from: Rational,
        to: Rational,
        ctx: QueryContext
    ) -> List<StrudelPatternEvent>,
) : StrudelPattern {
    override val weight: Double get() = source.weight
    override val steps: Rational? get() = source.steps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        return transform(source, sourceEvents, from, to, ctx)
    }
}

// Extension
fun StrudelPattern.mapWithQuery(
    transform: (StrudelPattern, List<StrudelPatternEvent>, Rational, Rational, QueryContext) -> List<StrudelPatternEvent>
): StrudelPattern = QueryContextAwareMapPattern(this, transform)
```

**Then SuperimposePattern becomes:**

```kotlin
val StrudelPattern.superimpose by dslPatternExtension { p, args, _ ->
    val transform: (StrudelPattern) -> StrudelPattern = ...

    p.mapWithQuery { source, originalEvents, from, to, ctx ->
        val superimposedEvents = try {
            transform(source).queryArcContextual(from, to, ctx)
        } catch (e: Exception) {
            emptyList()
        }
        originalEvents + superimposedEvents
    }
}
```

**Worth it?**

- Saves: 35 lines (SuperimposePattern)
- Adds: ~40 lines (QueryContextAwareMapPattern)
- **Net: -5 lines, probably not worth the complexity**

---

### 2. **Additional Property Overrides**

We could extend PropertyOverridePattern to support:

```kotlin
fun StrudelPattern.withCycleDuration(duration: Rational): StrudelPattern =
    PropertyOverridePattern(this, cycleDurationOverride = duration)
```

**Worth it?**

- If there's demand for it, yes
- Currently no usage sites identified

---

## Summary & Recommendation

### ✅ We've Already Done the Best Simplifications

The patterns we eliminated were:

1. **Pure property wrappers** (WeightedPattern, StepsOverridePattern)
2. **Pure delegation wrappers** (PickInnerPattern, PickOuterPattern)
3. **Simple transformations** (FilterPattern)

### ❌ Remaining Patterns Are Appropriately Specialized

The remaining small patterns (<50 lines) are:

- **SuperimposePattern** - Needs query context access, has error handling
- **OffPattern** - Cycle-aware filtering
- **ContextRangeMapPattern** - Specific domain logic
- **StackPattern** - Used directly, has specific property calculations

These are **appropriately sized** for what they do. Making them "more generic" would actually make the code **less clear
**.

---

## Design Principle: When to Stop Simplifying

**Stop simplifying when:**

1. **The abstraction would be more complex than the concrete class**
    - QueryContextAwareMapPattern vs SuperimposePattern: similar complexity

2. **The pattern has domain-specific logic**
    - OffPattern's cycle-aware filtering
    - ContextRangeMapPattern's range mapping

3. **The pattern is small but clear**
    - 35-40 lines is often the "sweet spot"
    - Clear, self-contained, easy to understand

4. **Usage is limited and specific**
    - SuperimposePattern is only used for `.superimpose()`
    - Making it generic doesn't help other use cases

---

## Recommendation: ✅ DONE - Don't Simplify Further

The current state achieves:

- ✅ Eliminated unnecessary duplication (5 classes)
- ✅ Created reusable abstractions where they make sense
- ✅ Kept specialized patterns that warrant their complexity
- ✅ Consistent, discoverable API for users

**Further simplification would:**

- ❌ Add complexity without clear benefit
- ❌ Make code less readable
- ❌ Create overly generic abstractions

**The codebase is now in a good state!** 🎉

---

## If You Must Simplify More...

If you really want to eliminate more patterns, here's the order:

1. **SuperimposePattern** → QueryContextAwareMapPattern (~5 line savings)
2. **Add withCycleDuration()** extension (if usage emerges)
3. **OffPattern** - Could potentially use QueryContextAwareMapPattern too

But honestly, **I don't recommend it**. The current state is well-balanced between:

- Abstraction (generic patterns)
- Clarity (specialized patterns)
- Discoverability (extensions)
- Maintainability (centralized logic)
