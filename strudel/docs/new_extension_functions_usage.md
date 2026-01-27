# New Extension Functions - Usage Guide

## Purpose of New Extensions

The new extension functions provide a **consistent, discoverable API** for pattern operations. They're designed for:

1. **User-facing API** - IDE autocomplete shows all available operations
2. **Code clarity** - Clear, descriptive names
3. **Consistency** - Similar operations have similar names
4. **Fluent chaining** - Easy to compose operations

---

## Extension Functions Overview

### Event Transformation

#### `StrudelPattern.map()` - Transform event lists

```kotlin
// Filter events
pattern.map { events -> events.filter { it.data.note != "~" } }

// Sort events
pattern.map { events -> events.sortedBy { it.begin } }

// Duplicate events
pattern.map { events -> events + events }
```

**When to use**: When you need to modify the entire list of events (filter, sort, add, remove)

---

#### `StrudelPattern.mapEvents()` - Transform individual events

```kotlin
// Transpose all notes up by 2 semitones
pattern.mapEvents { event ->
    event.copy(data = event.data.copy(note = transpose(event.data.note, 2)))
}

// Add gain to all events
pattern.mapEvents { event ->
    event.copy(data = event.data.withGain(0.8))
}
```

**When to use**: When you need to modify each event individually

**Note**: The existing `.reinterpret()` and `.reinterpretVoice()` functions serve similar purposes and can still be
used. The new functions provide alternative, more discoverable names.

---

#### `StrudelPattern.mapEventsWithContext()` - Context-aware transformation

```kotlin
// Randomize velocity based on context seed
pattern.mapEventsWithContext { event, ctx ->
    val random = ctx.getRandom()
    event.copy(data = event.data.withVelocity(random.nextDouble()))
}
```

**When to use**: When transformations need access to QueryContext (random seeds, context keys)

---

### Property Overrides

#### `StrudelPattern.withWeight()` - Override weight

```kotlin
// In mini-notation, "bd@2" creates a weighted pattern
// You can also do it programmatically:
note("bd").withWeight(2.0)

// Useful in sequences for proportional time distribution
seq(
    note("bd").withWeight(1.0),
    note("sn").withWeight(2.0),
    note("hh").withWeight(1.0)
)
```

**When to use**: When adjusting relative duration in sequences

**Replaces**: Direct `WeightedPattern` instantiation

---

#### `StrudelPattern.withSteps()` - Override steps

```kotlin
// Override steps for polymeter alignment
pattern.withSteps(16.toRational())
```

**When to use**: When overriding steps for polymeter

**Replaces**: Direct `StepsOverridePattern` instantiation

---

### Pattern Combination

#### `StrudelPattern.stack()` - Stack patterns

```kotlin
// Fluent stacking
val bass = note("c2")
val chords = seq("c3 e3 g3")
val melody = seq("c4 d4 e4 f4")

val combined = bass.stack(chords, melody)

// Same as: StackPattern(listOf(bass, chords, melody))
```

**When to use**: Fluent API for combining patterns that play simultaneously

---

## Usage Philosophy

### These extensions are primarily for **USER CODE**

The extensions are designed for users writing Strudel patterns, not necessarily for internal library code. They provide:

1. **Discoverability** - Shows up in IDE autocomplete
2. **Clarity** - Obvious what each operation does
3. **Consistency** - Similar naming conventions across operations
4. **Chaining** - Easy to compose: `pattern.withWeight(2.0).withSteps(8).map { ... }`

### Internal code can continue using direct instantiation

Internal library code can continue using:

- Direct `BindPattern` instantiation
- Direct `MapPattern` instantiation
- Direct `PropertyOverridePattern` instantiation
- Existing `.reinterpret()` / `.reinterpretVoice()` extensions

This is **perfectly fine** - these are implementation details.

---

## Examples of User Code

### Example 1: Building a pattern with multiple transformations

```kotlin
val pattern = note("c3 e3 g3")
    .withWeight(2.0)                                    // Adjust weight
    .mapEvents { it.copy(data = it.data.withGain(0.8)) } // Set gain
    .map { events -> events.filter { it.data.note != "~" } } // Remove rests
```

### Example 2: Creating a layered pattern

```kotlin
val bass = note("c2").withWeight(2.0)
val chords = seq("c3 e3 g3")
val melody = seq("c4 d4 e4 f4").mapEvents {
    it.copy(data = it.data.withGain(0.5))
}

val song = bass.stack(chords, melody)
```

### Example 3: Context-aware randomization

```kotlin
val pattern = seq("c3 e3 g3 c4").mapEventsWithContext { event, ctx ->
    val random = ctx.getSeededRandom(event.begin)
    val randomGain = 0.6 + (random.nextDouble() * 0.4)
    event.copy(data = event.data.withGain(randomGain))
}
```

### Example 4: Custom filtering

```kotlin
val pattern = seq("bd sn hh sn bd sn hh sn")
    .map { events ->
        events.filterIndexed { index, _ -> index % 2 == 0 }
    }
// Keeps only even-indexed events
```

---

## Relationship to Existing Extensions

### `.reinterpret()` vs `.mapEvents()`

Both serve similar purposes:

```kotlin
// Using existing .reinterpret()
pattern.reinterpret { event -> event.copy(...) }

// Using new .mapEvents()
pattern.mapEvents { event -> event.copy(...) }
```

**Recommendation**: Both are valid. Use whichever feels more natural for your use case.

### `.reinterpretVoice()` vs `.mapEvents()`

```kotlin
// Using existing .reinterpretVoice()
pattern.reinterpretVoice { voice -> voice.copy(note = "x") }

// Using new .mapEvents()
pattern.mapEvents { event -> event.copy(data = event.data.copy(note = "x")) }
```

**Recommendation**: `.reinterpretVoice()` is more concise for voice data changes. Keep using it.

---

## Summary

**The new extensions are primarily API improvements for users**, making pattern operations more:

- **Discoverable** (IDE autocomplete)
- **Consistent** (similar naming patterns)
- **Chainable** (fluent API)

Internal library code can continue using whatever is most appropriate for that context. The goal is a better user
experience, not a forced migration of existing code.
