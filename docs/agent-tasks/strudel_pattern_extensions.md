# Refactoring Strudel Patterns: Bind and ApplyControl

## Goal

Refactor the `StrudelPattern` implementations to reduce code duplication and centralize complex time-logic (
intersection, clipping, epsilon handling).

We have identified two core "meta-patterns" that logic across many classes falls into:

1. **Structure-Defining Logic (`bind` / Inner Join)**: An outer pattern defines the structure (timing), and an inner
   pattern fills it.
2. **Parameter/Sampling Logic (`applyControl` / Outer Join)**: A source pattern keeps its structure, but its values are
   modified by sampling a control pattern.

## Task 1: Implement `StrudelPattern.bind`

Create a generalized `bind` extension function in `StrudelPattern.kt`. This function abstracts the logic of "selecting a
pattern based on an event, then querying that pattern within the event's timeframe and clipping the result".

### Specification

- **Receiver**: `StrudelPattern` (The "outer" or "selector" pattern).
- **Parameters**:
    - `ctx`: `QueryContext`
    - `transform`: `(StrudelPatternEvent) -> StrudelPattern?` (Produces the inner pattern).
- **Logic**:
    1. Query `this` (outer) pattern for the given arc.
    2. For each outer event:
        - Call `transform(event)` to get the inner pattern.
        - Calculate the **intersection** of the outer event and the original query arc.
        - Query the **inner pattern** for this intersection.
        - **Clip** the resulting inner events to the outer event's boundaries.
        - Add them to the result list.

### Implementation Draft (kotlin)

```kotlin
// In StrudelPattern.kt

/**
 * Standard Monadic Bind / Inner Join.
 * 
 * Queries this pattern (outer), and for each event, generates an inner pattern via [transform].
 * The inner pattern is queried within the timeframe of the outer event, and results are clipped
 * to the outer event's boundaries.
 */
fun StrudelPattern.bind(
    from: Rational,
    to: Rational,
    ctx: StrudelPattern.QueryContext,
    transform: (StrudelPatternEvent) -> StrudelPattern?
): List<StrudelPatternEvent> {
    val outerEvents = this.queryArcContextual(from, to, ctx)
    // Use helper from PatternQueryUtils if available, else standard mutableListOf
    val result = createEventList()
    val epsilon = 1e-5.toRational() // Centralize this constant if possible

    for (outer in outerEvents) {
        val innerPattern = transform(outer) ?: continue

        // Intersect query arc with outer event
        val intersectStart = maxOf(from, outer.begin)
        val intersectEnd = minOf(to, outer.end)

        if (intersectEnd <= intersectStart) continue

        // Query inner
        val innerEvents = innerPattern.queryArcContextual(intersectStart, intersectEnd, ctx)

        for (inner in innerEvents) {
            // Clip inner event to outer event
            val clippedBegin = maxOf(inner.begin, outer.begin)
            val clippedEnd = minOf(inner.end, outer.end)

            if (clippedEnd > clippedBegin) {
                // Optimization: Only copy if boundaries actually changed
                if (clippedBegin != inner.begin || clippedEnd != inner.end) {
                    result.add(inner.copy(begin = clippedBegin, end = clippedEnd, dur = clippedEnd - clippedBegin))
                } else {
                    result.add(inner)
                }
            }
        }
    }
    return result
}
```

## Task 2: Implement `StrudelPattern.applyControl`

Create a generalized `applyControl` extension function. This abstracts the logic of "keeping the source events, but
modifying them based on the state of another pattern at that time".

### Specification

- **Receiver**: `StrudelPattern` (The source).
- **Parameters**:
    - `control`: `StrudelPattern` (The pattern to sample).
    - `ctx`: `QueryContext`
    - `combiner`: `(source: StrudelPatternEvent, control: StrudelPatternEvent?) -> StrudelPatternEvent?`
- **Logic**:
    1. Query `this` (source).
    2. For each source event:
        - Query the `control` pattern at `event.begin` (using a tiny epsilon duration).
        - Take the first matching control event (if any).
        - Call `combiner`.
        - If `combiner` returns non-null, add to result.

### Implementation Draft (kotlin)

```kotlin
// In StrudelPattern.kt

/**
 * Applies a control pattern to this pattern.
 * 
 * Preserves the structure of [this]. For each event, samples [control] at the event's start time.
 */
fun StrudelPattern.applyControl(
    control: StrudelPattern,
    from: Rational,
    to: Rational,
    ctx: StrudelPattern.QueryContext,
    combiner: (source: StrudelPatternEvent, control: StrudelPatternEvent?) -> StrudelPatternEvent?
): List<StrudelPatternEvent> {
    val sourceEvents = this.queryArcContextual(from, to, ctx)
    if (sourceEvents.isEmpty()) return emptyList()

    val result = createEventList()
    val epsilon = 1e-5.toRational()

    for (event in sourceEvents) {
        // Point-query the control pattern
        val controlEvents = control.queryArcContextual(event.begin, event.begin + epsilon, ctx)
        val controlEvent = controlEvents.firstOrNull()

        val combined = combiner(event, controlEvent)
        if (combined != null) {
            result.add(combined)
        }
    }
    return result
}
```

## Task 3: Refactor Existing Patterns

Refactor the following classes to use the new helpers. You may be able to delete some logic entirely.

### 1. `PickInnerPattern`

**Current**: Manually implements the loop-intersect-clip logic.
**Target**:

```kotlin
override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
    return selector.bind(from, to, ctx) { selectorEvent ->
        val key = extractKey(selectorEvent.data, modulo, lookup.size)
        lookup[key] // Returns StrudelPattern?
    }
}
```

### 2. `StructurePattern`

**Current**: Has distinct `queryIn` and `queryOut` methods.
**Target**:

- **Mode.Out** (struct): Use `bind`.
    ```kotlin
    // 'other' is the struct/mask pattern, 'source' is the content
    other.bind(from, to, ctx) { maskEvent -> 
        if (maskEvent.data.isTruthy()) source else null 
    }
    ```
- **Mode.In** (mask): Use `applyControl`.
    ```kotlin
    // 'source' is content, 'other' is the mask
    source.applyControl(other, from, to, ctx) { src, mask ->
        // keep if mask exists and is truthy (depending on filterByTruthiness)
        if (mask != null && mask.data.isTruthy()) src else null
    }
    ```

### 3. `ControlPattern`

**Current**: Manually implements the sampling logic.
**Target**:

```kotlin
override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
    return source.applyControl(control, from, to, ctx) { src, ctrl ->
        if (ctrl != null) {
            val mappedControl = mapper(ctrl.data)
            val newData = combiner(src.data, mappedControl)
            src.copy(data = newData).prependLocation(ctrl.sourceLocations?.innermost)
        } else {
            src
        }
    }
}
```

### 4. `TimeShiftPattern`

**Current**: Manually calculates offsets.
**Target**:
Can be refactored using `bind`?

```kotlin
// Theoretically:
offsetProvider.toPattern().bind(...) { offsetEvent ->
    // This is tricky because TimeShift shifts the *source* based on the offset.
    // If offsetProvider is a pattern, we are placing the shifted source *inside* the offset event's span.
    // Actually, TimeShift might be unique enough to stay as is, or use a specialized version of bind.
    // CHECK CAREFULLY before refactoring this one.
    // If it fits `bind`, the inner pattern would be `source.shiftedBy(offset)`.
}
```

**Decision**: Check if `TimeShiftPattern` fits strictly into `bind`. If `bind` enforces clipping to the outer event (
which `TimeShift` with control patterns does), then it fits.
