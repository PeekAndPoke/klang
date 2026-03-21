# Sprudel — DSL Conventions (`lang_*.kt`)

## Before Adding Any DSL Function — Ask First

**Always ask the user:** "Is this an original Strudel function or a Klang addon?"

- **Original Strudel** → goes in the appropriate `lang_*.kt` file (e.g. `lang_structural.kt`)
- **Addon** → goes in `lang/addons/lang_*_addons.kt` and requires `addon` in `@tags`

See `ref/dsl-addons.md` for addon rules and conventions.

## Pattern for Every DSL Function

**1.** Make `val` delegates private with `_` prefix (they still register with KlangScript):

```kotlin
private val _foo by dslFunction { args, _ -> applyFoo(args) }
private val SprudelPattern._foo by dslPatternExtension { p, args, _ -> applyFoo(p, args) }
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
  @SprudelDsl fun foo(vararg patterns: PatternLike): SprudelPattern = _foo(patterns.toList())
  @SprudelDsl fun SprudelPattern.foo(vararg patterns: PatternLike): SprudelPattern = this._foo(patterns.toList())
  @SprudelDsl fun String.foo(vararg patterns: PatternLike): SprudelPattern = this._foo(patterns.toList())

```

## KDoc Rules

- Examples: fenced ` ```KlangScript ``` ` blocks — **NOT** `@sample` tags
- Required tags: `@param`, `@return`, `@category` (one word), `@tags` (comma-separated)
- `@param-sub` required for composite params (colon-separated values like `"amount:shape"`)
- `@alias` required when aliases exist — every alias must list all the others
- Max line length: 120 chars
- Single-line `/** ... */` only when entire comment fits within 120 chars

## Aliases

Every alias must cross-reference all others:
```kotlin
// hush → @alias bypass, mute
// bypass → @alias hush, mute
// mute → @alias hush, bypass
```

## KSP

- `sprudel-ksp` extracts KDoc from all `@SprudelDsl`-annotated `fun` and `val` items
- After changing KDoc: `./gradlew :sprudel:jvmTest` — KSP regenerates docs automatically
- `SprudelDocsSpec` tests verify docs are correctly registered
- Examples use fenced ` ```KlangScript ``` ` blocks (KSP reads these, not `@sample`)
