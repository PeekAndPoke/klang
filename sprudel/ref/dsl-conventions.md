# Sprudel — DSL Conventions (`lang_*.kt`)

## Before Adding Any DSL Function — Ask First

**Always ask the user:** "Is this an original Strudel function or a Klang addon?"

- **Original Strudel** → goes in the appropriate `lang_*.kt` file (e.g. `lang_structural.kt`)
- **Addon** → goes in `lang/addons/lang_*_addons.kt` and requires `addon` in `@tags`

See `ref/dsl-addons.md` for addon rules and conventions.

## Pattern for Every DSL Function

> ⚠️ Historical note: an older delegate API (`@SprudelDsl`, `dslFunction`, `dslPatternExtension`,
> init sentinel vars) no longer exists. Current reality below — `lang_body.kt` and
> `lang/addons/lang_structural_addons.kt` are good reference implementations.

**1.** File header registers the library; every public form is a plain `fun` annotated
`@KlangScript.Function` (registration into KlangScript is fully automatic via `klangscript-ksp` —
there is no manual registry):

```kotlin
@file:KlangScript.Library("sprudel")
```

**2.** A private `apply*` helper holds the logic; the four public forms (a)–(d) call it:

```kotlin
private fun applyFoo(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern { ... }

@KlangScript.Function  // (a) pattern extension
fun SprudelPattern.foo(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern = ...

@KlangScript.Function  // (b) String extension — receiver parses as mini-notation
fun String.foo(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).foo(amount, callInfo)

@KlangScript.Function  // (c) top-level factory
fun foo(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.foo(amount, callInfo) }

@KlangScript.Function  // (d) chained mapper
fun PatternMapperFn.foo(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.foo(amount, callInfo) }
```

**3.** Writing into voice data — pick the right idiom:

- `voiceSetter { ... }` (`lang_helpers.kt`) mutates in place — the fast path, safe because every
  `SprudelVoiceData` reaching a modifier is a single-owner leaf clone. Prefer this for new code.
- Lift helpers on `SprudelPattern`: `_liftNumericField` / `_liftStringField` /
  `_liftOrReinterpret*` (outer join), `_liftData` (inner join), `_applyControlFromParams`
  (outer join with a custom combiner). **`_innerJoin` support is mandatory for any function
  accepting control patterns** — static values work without it, control patterns silently break.
- **Literal (non-patternable) arguments must NOT go through the lift helpers** — those parse
  strings as mini-notation. Use `reinterpretVoice { }` instead (precedents: `pipeline(dsl)` in
  `lang_pipeline.kt`, `tag(name)` in `lang_structural_addons.kt`).

## Field accessors and mapper arguments (2026-09-06, pilot: `freq`)

- A setter that receives a single `PatternMapperFn` or `PatternMapperProvider` argument applies
  it to its OWN field through `_mapNumericField(mapper, read, update)` (read the field into
  `value`, run the mapper, write `value` back, drain it). Add the branch in the `apply*` helper:

  ```kotlin
  private fun applyFreq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
      args.singleMapperOrNull()?.let { mapper ->
          return source._mapNumericField(mapper, read = { it.freqHz }, update = freqUpdate)
      }
      return source._liftOrReinterpretNumericalField(args, freqUpdate)
  }
  ```

- The accessor is `@KlangScript.Library("sprudel") @KlangScript.Object("<name>") object <Name> :
  FieldAccessor({ it.<field> })` with a `@KlangScript.Method(name = "invoke")` member that
  delegates to the Kotlin factory (same parameters), plus an unannotated `val <name> = <Name>` for
  the Kotlin door. The top-level factory `fun <name>(...)` stays for Kotlin and loses its
  `@KlangScript.Function` (it would collide with the object); its KDoc moves onto `invoke`, the
  object's KDoc describes the accessor with two playable examples and keeps the field's category.
  Never make the accessor a `PatternMapperFn`: see `MEMORY.md` 2026-09-06 for the ambiguity.
- Every new accessor gets two rows in `LangFieldAccessorsSpec`: a mapper on its own field and the
  bare accessor read into another field, both doors.
- An alias (`rsize` for `roomsize`) is `@KlangScript.Constant val rsize: RoomSize = RoomSize` with
  a one-line KDoc; its factory `fun rsize(...)` stays for Kotlin, unannotated. The editor types it
  as the canonical object, so `rsize(` shows the `roomsize(...)` signature. One alias row per alias.
- Design record and rejected alternatives: `docs/tasks/sprudel-field-accessors.md`.

## KDoc Rules

- Examples: fenced ` ```KlangScript ``` ` blocks (or ` ```KlangScript(Playable) ``` `) — **NOT** `@sample` tags
- Required tags: `@param`, `@return`, `@category` (one word), `@tags` (comma-separated)
- `@param-tool <ParamName> <ToolName>` wires a param to a `KlangUiTool` (see `ref/uitools.md`)
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

- `klangscript-ksp` scans `@KlangScript.Function` items and generates
  `GeneratedSprudelRegistration.kt` — callable bindings AND docs (`generatedSprudelDocs`);
  `KlangScriptStrudelLib.kt` registers the generated bundle once, nothing per-function
- After changing KDoc: `./gradlew :sprudel:jvmTest` — KSP regenerates docs automatically
- `SprudelDocsSpec` tests verify docs are correctly registered
- **Vararg params kill named arguments** (KSP emits empty ParamSpecs for varargs) — prefer fixed
  arity when named-arg support and intellisense matter
- Default values: only pure literals (numbers, plain strings, booleans, `null`) survive into the
  generated default thunks; anything else makes the param required in named-arg calls
