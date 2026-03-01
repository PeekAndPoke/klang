# Strudel — DSL Addons

Addon functions extend the Strudel DSL with features NOT present in the original strudel.cc
JavaScript implementation. They live in `lang/addons/` and follow all standard DSL conventions
with one additional requirement: `addon` must always appear in `@tags`.

## Location

```
strudel/src/commonMain/kotlin/lang/addons/
    lang_arithmetic_addons.kt   — flipSign, oneMinusValue, not, abs
    lang_structural_addons.kt   — morse, merge, timeLoop, repeat, solo
    lang_continuous_addons.kt   — cps, bpm, timeOfDay/Night, sinOfDay/Night variants
    lang_tempo_addons.kt
    lang_filters_addons.kt
    lang_osc_addons.kt
```

## Package

`io.peekandpoke.klang.strudel.lang.addons`

## File Structure Convention

Every addon file must have:

1. A top-of-file comment: `ADDONS: functions that are NOT available in the original strudel impl`
2. An init sentinel var (forces class initialization so delegates register in `StrudelRegistry`):

```kotlin
var strudelLangXxxAddonsInit = false
```

## KDoc Requirement: `addon` tag

Every addon function **must** include `addon` in its `@tags`:

```kotlin
/**
 * ...
 * @category structural
 * @tags myFunc, something, addon    ← addon is mandatory
 */
```

## Example Addon (minimal complete example)

```kotlin
private fun applyMyAddon(pattern: StrudelPattern): StrudelPattern { ... }

internal val StrudelPattern._myAddon by dslPatternExtension { p, _, _ -> applyMyAddon(p) }
internal val String._myAddon by dslStringExtension { p, _, _ -> applyMyAddon(p) }

/**
 * One-line summary.
 *
 * ```KlangScript
 * note("c d e f").myAddon()   // example
 * ```

*
* @category structural
* @tags myAddon, something, addon
  */
  @StrudelDsl fun StrudelPattern.myAddon(): StrudelPattern = this._myAddon(emptyList())
  @StrudelDsl fun String.myAddon(): StrudelPattern = this._myAddon(emptyList())

```

## Existing Addons

| Function | File | Description |
|----------|------|-------------|
| `flipSign()` | arithmetic | Multiply value by -1 |
| `oneMinusValue()` | arithmetic | Compute 1.0 - value |
| `not()` | arithmetic | Logical NOT on boolean values |
| `abs()` | arithmetic | Absolute value |
| `morse(text)` | structural | Rhythm from Morse code encoding |
| `merge(ctrl)` | structural | Overlay voice properties from a control pattern |
| `timeLoop(duration)` | structural | Tile pattern within a fixed cycle window |
| `repeat(times)` | structural | Repeat pattern N times sequentially |
| `solo()` / `solo(enabled)` | structural | Solo this pattern, muting others |
| `cps` | continuous | Current cycles per second |
| `bpm` | continuous | Current beats per minute (cps × 240) |
| `timeOfDay` | continuous | Time of day 0.0 (midnight) → 1.0 |
| `sinOfDay` / `sinOfDay2` | continuous | Sine of time of day (unipolar / bipolar) |
| `timeOfNight` | continuous | Inverse of timeOfDay |
| `sinOfNight` / `sinOfNight2` | continuous | Sine of time of night (unipolar / bipolar) |
