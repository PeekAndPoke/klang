# Sprudel — DSL Addons

Addon functions extend the Sprudel DSL with features NOT present in the original strudel.cc
JavaScript implementation. They live in `lang/addons/` and follow all standard DSL conventions
with one additional requirement: `addon` must always appear in `@tags`.

## Location

```
sprudel/src/commonMain/kotlin/lang/addons/
    lang_arithmetic_addons.kt   — flipSign, oneMinusValue, not, abs
    lang_structural_addons.kt   — morse, merge, timeLoop, repeat, solo
    lang_continuous_addons.kt   — cps, bpm, timeOfDay/Night, sinOfDay/Night variants
    lang_tempo_addons.kt
    lang_filters_addons.kt
    lang_osc_addons.kt
```

## Package

`io.peekandpoke.klang.sprudel.lang.addons`

## File Structure Convention

Every addon file must have:

1. A top-of-file comment: `ADDONS: functions that are NOT available in the original strudel impl`
2. The `@file:KlangScript.Library("sprudel")` header — registration is fully automatic via
   `klangscript-ksp`; there are no delegates and no init sentinels (the old delegate API is gone)

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
private fun applyMyAddon(pattern: SprudelPattern): SprudelPattern { ... }

/**
 * One-line summary.
 *
 * ```KlangScript
 * note("c d e f").myAddon()   // example
 * ```
 *
 * @return Description.
 * @category structural
 * @tags myAddon, something, addon
 */
@KlangScript.Function
fun SprudelPattern.myAddon(callInfo: CallInfo? = null): SprudelPattern = applyMyAddon(this)

@KlangScript.Function
fun String.myAddon(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).myAddon(callInfo)
```

See `tag()` in `lang_structural_addons.kt` for a full four-form example including the mapper
forms (c)/(d) and a literal (non-mini-notation) string parameter.

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
| `tag(name)` | structural | Add a semantic tag (Set, unordered) to every event, e.g. for visualizations |
| `cps` | continuous | Current cycles per second |
| `bpm` | continuous | Current beats per minute (cps × 240) |
| `timeOfDay` | continuous | Time of day 0.0 (midnight) → 1.0 |
| `sinOfDay` / `sinOfDay2` | continuous | Sine of time of day (unipolar / bipolar) |
| `timeOfNight` | continuous | Inverse of timeOfDay |
| `sinOfNight` / `sinOfNight2` | continuous | Sine of time of night (unipolar / bipolar) |
