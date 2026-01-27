# Strudel Pattern Refactoring - Complete Summary

## Overview

Successfully refactored the Strudel pattern system to eliminate code duplication and create reusable abstractions for
common pattern operations.

## New Extension Functions

### 1. `StrudelPattern.bind()`

**Purpose**: Reusable operation for "inner join" semantics

- Queries outer pattern, generates inner patterns from events, clips to boundaries
- **Returns**: `List<StrudelPatternEvent>` directly
- **Use case**: When you need the intersection/clipping logic without wrapping in a pattern

```kotlin
fun StrudelPattern.bind(
    from: Rational,
    to: Rational,
    ctx: StrudelPattern.QueryContext,
    transform: (StrudelPatternEvent) -> StrudelPattern?,
): List<StrudelPatternEvent>
```

### 2. `StrudelPattern.applyControl()`

**Purpose**: Reusable operation for "outer join" semantics

- Preserves source structure, samples control pattern, applies combiner
- **Returns**: `List<StrudelPatternEvent>` directly
- **Use case**: Applying control values/effects while maintaining timing

```kotlin
fun StrudelPattern.applyControl(
    control: StrudelPattern,
    from: Rational,
    to: Rational,
    ctx: StrudelPattern.QueryContext,
    combiner: (source: StrudelPatternEvent, control: StrudelPatternEvent?) -> StrudelPatternEvent?,
): List<StrudelPatternEvent>
```

### 3. `StrudelPattern.map()`

**Purpose**: Creates a pattern that transforms event lists

- **Returns**: `StrudelPattern` (a MapPattern instance)
- **Use case**: Filtering, mapping, or transforming events while preserving pattern structure

```kotlin
fun StrudelPattern.map(
    transform: (List<StrudelPatternEvent>) -> List<StrudelPatternEvent>,
): StrudelPattern
```

## New Generic Pattern Classes

### 1. BindPattern

**Purpose**: Generic wrapper for bind operations

- Wraps any pattern transformation that uses `bind()`
- Delegates `weight`, `steps`, `estimateCycleDuration()` to outer pattern
- **Implementation**: Calls `outer.bind(from, to, ctx, transform)`

### 2. MapPattern

**Purpose**: Generic wrapper for event list transformations

- Wraps simple event transformations (filtering, mapping, etc.)
- Delegates all pattern properties to source pattern
- **Implementation**: Applies `transform(source.queryArcContextual(from, to, ctx))`

## Patterns Removed

### ✅ PickInnerPattern (38 lines) → BindPattern

**Before**:

```kotlin
internal class PickInnerPattern(...) : StrudelPattern {
    override val weight: Double get() = selector.weight
    override val steps: Rational? get() = selector.steps
    override fun estimateCycleDuration(): Rational = selector.estimateCycleDuration()

    override fun queryArcContextual(...): List<StrudelPatternEvent> {
        return selector.bind(from, to, ctx) { selectorEvent ->
            val key = extractKey(selectorEvent.data, modulo, lookup.size)
            if (key != null) lookup[key] else null
        }
    }
}
```

**After**:

```kotlin
BindPattern(
    outer = pat,
    transform = { selectorEvent ->
        val key = keyExtractor(selectorEvent.data, modulo, reifiedLookup.size)
        if (key != null) reifiedLookup[key] else null
    }
)
```

### ✅ PickOuterPattern (36 lines) → BindPattern

Identical to PickInnerPattern, also replaced with BindPattern.

### ✅ FilterPattern (28 lines) → MapPattern via map()

**Before**:

```kotlin
internal class FilterPattern(
    private val source: StrudelPattern,
    private val predicate: (StrudelPatternEvent) -> Boolean,
) : StrudelPattern {
    override val weight: Double get() = source.weight
    override val steps: Rational? get() = source.steps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(...): List<StrudelPatternEvent> {
        return source.queryArcContextual(from, to, ctx).filter(predicate)
    }
}
```

**After**:

```kotlin
source.map { events -> events.filter(predicate) }
```

## Design Patterns

### Pattern for bind() and applyControl()

These are **operations** that return results directly:

1. Extension function contains the full logic
2. Pattern classes call the extension function
3. Use when you need to reuse complex logic across multiple patterns

### Pattern for map()

This is a **factory** that creates pattern objects:

1. Extension function creates and returns a MapPattern
2. MapPattern contains the transformation logic
3. Use when you want to wrap a transformation in a pattern object

## Statistics

- **Lines Removed**: 102 (3 pattern class files)
- **Lines Added**: ~100 (2 generic patterns + 3 extension functions)
- **Net Change**: Minimal line change, but much better abstraction
- **Pattern Classes Removed**: 3
- **Reusable Components Added**: 5 (3 extensions + 2 generic patterns)

## Benefits

1. **Code Reusability**: Common operations (bind, applyControl, map) can be used anywhere
2. **Consistency**: All patterns using these operations behave consistently
3. **Maintainability**: Logic is centralized, easier to fix bugs or add features
4. **Simplicity**: Creating new patterns is easier - just use BindPattern or MapPattern
5. **Flexibility**: Extensions can be used directly when pattern wrapping isn't needed

## Files Modified

1. `StrudelPattern.kt` - Added 3 extension functions
2. `pattern/BindPattern.kt` - New generic pattern (34 lines)
3. `pattern/MapPattern.kt` - New generic pattern (34 lines)
4. `lang/lang_pattern_picking.kt` - Updated to use BindPattern
5. `lang/lang_structural.kt` - Updated to use map() extension
6. `pattern/ControlPattern.kt` - Uses applyControl()
7. `pattern/StructurePattern.kt` - Uses bind()

## Files Removed

1. `pattern/PickInnerPattern.kt` (38 lines)
2. `pattern/PickOuterPattern.kt` (36 lines)
3. `pattern/FilterPattern.kt` (28 lines)

## No Breaking Changes

- All public APIs remain unchanged
- Pattern behavior is identical
- Only internal implementation was refactored
- All existing tests continue to work
