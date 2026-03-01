# Strudel — Technical Reference

Kotlin/Multiplatform port of the JavaScript Strudel pattern language for live coding music.
Patterns generate musical events scheduled over cyclic time (1 cycle ≈ 1 measure).

## Event Structure

```kotlin
data class StrudelPatternEvent(
    val part: TimeSpan,   // visible portion (may be clipped)
    val whole: TimeSpan,  // complete original event — always non-nullable
    val data: StrudelVoiceData
) {
    val isOnset: Boolean = whole.begin == part.begin
}
```

`queryArc()` returns all events including non-onsets. Playback filters by `isOnset`.

## Control Patterns — `_innerJoin` (CRITICAL)

Any DSL function accepting pattern arguments **must** use `_innerJoin`. Without it, static values
work but control patterns (e.g. `pressBy("<0 0.5>")`) silently break.

```kotlin
// ✅ Correct
fun applyPressBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val rArg = args.getOrNull(0) ?: return pattern
    return pattern._innerJoin(rArg) { src, rVal ->
        val r = rVal?.asDouble ?: return@_innerJoin src
        src.fmap { AtomicPattern.value(it) }
            .let { applyCompress(it, listOf(StrudelDslArg.of(r), StrudelDslArg.of(1.0))) }
            .squeezeJoin()
    }
}
```

## Pattern-of-Patterns: `fmap` + `squeezeJoin`

`fmap` maps values into patterns (creates pattern-of-patterns).
`squeezeJoin` flattens by squeezing inner patterns into outer event timespans.

**Data merge rule** (critical): `outerEvent.data.copy(value = innerEvent.data.value)`
— preserves outer musical properties (sound, note, etc.) while taking the inner value.

## Pattern Operation Categories

| Type           | Examples                              | Part rule       | Whole rule               |
|----------------|---------------------------------------|-----------------|--------------------------|
| Event Creation | AtomicPattern, EuclideanPattern       | set to bounds   | same as part             |
| Clipping       | BindPattern, StructurePattern         | clip to bounds  | **preserve**             |
| Scaling        | TempoModifierPattern, HurryPattern    | scale by factor | scale by **same** factor |
| Shifting       | RepeatCyclesPattern, TimeShiftPattern | shift by offset | shift by **same** offset |
| Data Transform | ControlPattern, Gain, Note            | don't touch     | don't touch              |
| Pass-Through   | MapPattern, ChoicePattern             | don't modify    | don't modify             |

## Key Files

| File                                           | Role                                      |
|------------------------------------------------|-------------------------------------------|
| `StrudelPatternEvent.kt`                       | Event definition (part/whole/isOnset)     |
| `StrudelPattern.kt`                            | Pattern interface + helpers               |
| `StrudelVoiceData.kt` / `StrudelVoiceValue.kt` | Voice data; values inc. `Pattern` variant |
| `BindPattern.kt`                               | Inner join (clipping)                     |
| `TempoModifierPattern.kt`                      | fast/slow (scaling)                       |
| `RepeatCyclesPattern.kt`                       | Cycle repetition (shifting)               |
| `AtomicPattern.kt`                             | Basic event creation                      |
| `StrudelPlayback.kt`                           | Schedules events, filters by `isOnset`    |
| `lang_*.kt`                                    | User-facing DSL API                       |

## Testing

```bash
./gradlew :strudel:jvmTest                          # preferred (fast)
./gradlew :strudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :strudel:jsTest                           # browser-specific only
```

Test across **≥12 cycles** — timing bugs compound. Always verify both `part` and `whole` explicitly.
Compare against JS: `compat/JsCompatTests.kt`.

## DSL Documentation Conventions (`lang_*.kt`)

When adding/refactoring a `@StrudelDsl` function:

**1.** Make `val` delegates private with `_` prefix (they still register with KlangScript):
```kotlin
private val _foo by dslFunction { args, _ -> applyFoo(args) }
private val StrudelPattern._foo by dslPatternExtension { p, args, _ -> applyFoo(p, args) }
private val String._foo by dslStringExtension { p, args, callInfo -> p._foo(args, callInfo) }
```

**2.** Add public `fun` overloads with full KDoc. Always call through the private delegate:
```kotlin
/**
 * One-line summary.
 *
 * ```KlangScript
 * foo("c d e f").note()   // example 1
 * ```

*
* ```KlangScript
* note("c e g").foo()     // example 2
* ```
*
* @param patterns Description.
* @return Description.
* @category structural
* @tags foo, rhythm
  */
  @StrudelDsl fun foo(vararg patterns: PatternLike): StrudelPattern = _foo(patterns.toList())
  @StrudelDsl fun StrudelPattern.foo(vararg patterns: PatternLike): StrudelPattern = this._foo(patterns.toList())
  @StrudelDsl fun String.foo(vararg patterns: PatternLike): StrudelPattern = this._foo(patterns.toList())
```

**KDoc rules:**
- Examples: fenced ` ```KlangScript ``` ` blocks (NOT `@sample` tags)
- Required tags: `@param`, `@return`, `@category` (one word), `@tags` (comma-separated)
- `@alias` required when aliases exist — every alias must list all the others
- Max line length: 120 chars

**KSP:** After changing KDoc, run `./gradlew :strudel:jvmTest` — KSP regenerates docs automatically.
`StrudelDocsSpec` tests verify docs are correctly registered.

## Common Operations

```kotlin
// Create event
StrudelPatternEvent(part = ts, whole = ts, data = data)

// Clip (whole preserved)
val clipped = event.part.clipTo(bounds)
if (clipped != null) event.copy(part = clipped)

// Scale (both part and whole)
event.copy(part = event.part.scale(factor), whole = event.whole.scale(factor))

// Shift (both part and whole)
event.copy(part = event.part.shift(offset), whole = event.whole.shift(offset))
```

## When Stuck

1. Control patterns involved? → need `_innerJoin`
2. Look at similar working patterns: `BindPattern`, `TempoModifierPattern`, `RepeatCyclesPattern`
3. Check JS Strudel source for semantics
4. Write a unit test to isolate the issue
5. Trace through `fmap` / `squeezeJoin` data flow
