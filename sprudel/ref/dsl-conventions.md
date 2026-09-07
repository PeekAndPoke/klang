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

- The accessor is ONE declaration, named exactly like the script name (maintainer decision
  2026-09-07, the Kotlin naming convention is suppressed at file level with `"ClassName"`):
  `@KlangScript.Library("sprudel") @KlangScript.Object("gain") object gain : FieldAccessor({ it.gain })`
  with a `@KlangScript.Method(name = "invoke") operator fun invoke(...) = { p -> p.gain(...) }`
  member. The object IS the Kotlin door: `gain(0.5)` resolves through the invoke convention and
  `pan(gain)` reads the value. No top-level `fun gain(...)` factory and no `val gain` twin (both
  removed 2026-09-07). The old factory's KDoc lives on `invoke`; the object's KDoc describes the
  accessor with two playable examples and keeps the field's category.
  Never make the accessor a `PatternMapperFn`: see `MEMORY.md` 2026-09-06 for the ambiguity.
- Every new accessor gets two rows in `LangFieldAccessorsSpec`: a mapper on its own field and the
  bare accessor read into another field, both doors.
- A compound door whose slots have no doors of their own (`adsr`) is an object with the slot
  accessors as children: `@KlangScript.Object("adsr") object adsr { @KlangScript.Property val
  attack: FieldAccessor = FieldAccessor { it.attack } ... @Method("invoke") ... }`; the slot
  helpers stay private and take the mapper branch, so `adsr(attack = mul(2))` works and
  `adsr.attack` reads. Do not add single doors for such slots.
- A setter whose lift call carries an inline update lambda (`_liftOrReinterpretNumericalField(args)
  { v -> copy(x = v) }`) gets a named `private val <name>Update: SprudelVoiceData.(Double?) ->
  SprudelVoiceData` so the mapper branch and the lift share one update (tonal, 2026-09-07).
- An alias (`rsize` for `roomsize`) is `@KlangScript.Constant val rsize: roomsize = roomsize` with
  a KDoc that carries `@category` and `@tags` (the property entry merges into the symbol first, so
  without them the docs page shows the alias as "uncategorized"; guarded by
  `FreqAccessorIntelSpec`). No alias factory either: `rsize(4)` in Kotlin is the constant's invoke. The editor types it
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
