# KlangScript union types: tell the editor what an `XLike` parameter accepts

> **Written 2026-09-18, not started.** Priority: **NICE** (editor convenience; no engine or sound
> impact). Outside the signal-flow work stream on purpose. Follow-up that depends on it:
> [`katalyst-master-configure-doors.md`](katalyst-master-configure-doors.md).
> Touches KSP, so the mandatory mutation tier of `/review-loop` applies, and the stone rule on
> complexity applies to §3: the declaration form is a maintainer decision (§D1).

## 1. The problem

Several doors accept "one of several things" and declare the parameter as an alias of `Any`:

| alias | where | accepts at runtime |
|---|---|---|
| `PatternLike` | `sprudel/src/commonMain/kotlin/lang/lang.kt:24` | a string (mini-notation), a number, a pattern, a mapper lambda, a field accessor |
| `IgnitorDslLike` | `klangscript-libs/src/commonMain/kotlin/stdlib/KlangScriptOscExtensions.kt:18` | a number or an ignitor node |

The runtime sorts the value out by kind, and that works. The EDITOR knows nothing: KSP emits
`KlangType(simpleName = "PatternLike", isTypeAlias = true)` and stops. Consequences a user feels:

- **No completion inside a lambda argument.** The analyzer types a lambda's parameters from the
  declared parameter type (`klangscript/src/commonMain/kotlin/intel/AnalyzedAst.kt:348`,
  `expected[i].functionParams`). An alias of `Any` has no function type, so in
  `note("c").superimpose(x => x.` the `x` is untyped and nothing completes.
- **The docs popup shows only the alias name**, not what may be passed.
- **No diagnostics are possible** for a wrong kind of argument, because `Any` accepts everything.

## 2. What already exists

- `KlangType` (`klangscript/src/commonMain/kotlin/types/KlangType.kt`) already has
  `unionMembers: List<KlangType>?` and `isUnion`. Nothing populates it, and `render()` ignores it.
- `KlangType.functionParams` / `functionReturn` describe a function type, and KSP already emits them
  from a `KSType` (`functionComponentsExpr` in `klangscript-ksp/.../KlangScriptProcessor.kt`, near
  the type emission at ~1676 to 1700).
- KSP recognises a parameter declared through a `KSTypeAlias` and emits `isTypeAlias = true`.
- The interop needs nothing: under an `Any` slot a script lambda already arrives as a Kotlin
  `FunctionN` (`RuntimeValue.convertToKotlin` in `runtime/NativeInterop.kt`; sprudel's
  `patternMapper` relies on it), a number as `Double`, a string as `String`, a native object as
  itself.
- KlangScript has NO overloads by design: KSP refuses two annotated Kotlin functions under one
  script (name, receiver) (the collision check at `KlangScriptProcessor.kt` ~627). A union type is
  therefore the only way one script door can be typed for several argument kinds.

## 3. Design

### 3.1 A union is declared once, on the alias

The alias stays `typealias PatternLike = Any` in Kotlin, so no door signature and no runtime code
changes. What is new is a declaration KSP can read that says what the alias stands for. The
members must be able to carry FUNCTION types with their parameter types (the whole point is
typing a lambda), and the declaration must be readable from a DEPENDENCY module, because
`IgnitorDslLike` is declared in `klangscript-libs` and used by doors in `sprudel`.

**§D1, the declaration form. Parked for the maintainer.** Two candidates:

- **(a) Members as the parameters of a marker function.** Plain Kotlin, fully typed, and KSP
  already knows how to turn each parameter's `KSType` into a `KlangType`, function types and
  aliases included:

  ```kotlin
  typealias PatternLike = Any

  @KlangScript.Union(alias = "PatternLike")
  @Suppress("unused", "UNUSED_PARAMETER")
  private fun patternLikeMembers(
      text: String, number: Number, pattern: SprudelPattern,
      mapper: PatternMapperFn, accessor: PatternMapperProvider,
  ) = Unit
  ```

  For: no parser, no strings, a typo is a compile error, reuses the existing type emission.
  Against: it is a trick (a function that is never called), and cross-module lookup has to find
  the marker function in the dependency (`resolver.getFunctionDeclarationsByName` on a
  conventional name, or an index file KSP writes per module).

- **(b) Members as strings on the alias.**

  ```kotlin
  @KlangScript.Union("String", "Number", "SprudelPattern", "(SprudelPattern) -> SprudelPattern")
  typealias PatternLike = Any
  ```

  For: reads exactly like what the user will see; an annotation on the alias travels in the
  metadata, so a dependent module's KSP finds it without an index. Against: a small type-string
  parser in KSP, and names are checked only by KSP (it must fail the build on a name it cannot
  resolve to a registered type, or the union rots silently).

Recommendation: (a) if the cross-module lookup turns out to be a few lines, otherwise (b). Spike
the lookup first (half a day), decide on evidence.

### 3.2 KSP

- Collect the unions of the module and of its dependencies: alias fqcn to member list.
- Wherever a `KlangType` is emitted for a declaration that is a `KSTypeAlias` with a known union
  (parameters, and return types if any ever use one), emit `unionMembers = ...`. Emit ONE shared
  `val` per alias in the generated file and reference it, so a union with five members is not
  inlined at several hundred `PatternLike` parameters (watch the generated file size; the JS
  bundle task `reduce-js-bundle-size.md` cares).
- Nullability stays on the use site (`PatternLike?`); `KlangParam.kt:47` already avoids a double
  `??` for aliases of `Any?`. A member itself is never nullable.
- An alias of an alias resolves to the innermost union. A union member that is itself a union is
  flattened.
- Guard: an alias named `*Like` whose underlying type is `Any` and that has NO union declared is a
  KSP warning (not an error in step 1, so the change can land alias by alias).

### 3.3 The analyzer and IntelliSense

- **Lambda typing (the payoff).** Where `AnalyzedAst` reads `expected[i].functionParams`, fall
  back to the union: pick the function-typed member whose arity matches the lambda's parameter
  count; with exactly one function member, take it regardless of arity (the interop adapts arity
  already). Two function members of the same arity is a declaration error KSP reports.
- **Rendering.** `KlangType.render()` for a union with an alias name stays the alias name (short,
  what signatures show today). Add `renderExpanded()`: `String | Number | SprudelPattern |
  (SprudelPattern) -> SprudelPattern`. The hover and the docs popup show
  `PatternLike = <expanded>` once, under the signature.
- **Assignability.** Add `KlangType.accepts(other)`: true if `other` is assignable to any member.
  Phase 1 uses it for nothing user-visible. Phase 2 (its own step, after phase 1 has lived in the
  editor for a while) turns a mismatch into an editor WARNING, never an error: the runtime is the
  judge, mini-notation strings and numbers coerce in ways a static check does not model.
- **Completion after a union-typed expression** (a union as a RETURN type) is out of scope: no
  door returns one.

### 3.4 What does not change

Kotlin signatures, the interop, the runtime dispatch by value kind, script semantics. The Kotlin
door keeps `Any`; Kotlin callers gain nothing from this task (Kotlin has no union types). Where a
Kotlin caller deserves types, the door offers typed Kotlin overloads beside the script-registered
one, as the follow-up task does for `katalyst` and `master`.

## 4. Steps

1. **Spike §D1** (cross-module lookup of the marker function), decide the declaration form.
2. **KSP emission + model**: `unionMembers` populated, shared `val` per alias, `renderExpanded()`,
   the `*Like`-without-union warning. Declare the unions of `PatternLike` and `IgnitorDslLike`.
3. **Analyzer**: lambda typing through a union; hover and docs popup show the expansion.
4. (Later, own decision) assignability warnings.

## 5. Tests (KSP tier: every new test mutation-checked)

- KSP: a fixture alias with a union emits the members, function member included with its
  parameter types; an alias without a union emits none and raises the warning; two same-arity
  function members fail the build; a dependency-declared union is found (the cross-module case is
  the one most likely to rot, test it with two fixture modules or the real `IgnitorDslLike`).
- Model: `render()` unchanged for an alias; `renderExpanded()` exact string; nullable use site.
- Analyzer: a lambda passed to a union-typed parameter gets its parameter typed (assert the
  inferred type of `x` and that a member completes on `x.`); a lambda passed to a plain `Any`
  parameter stays untyped (the negative control); arity selection with two function members of
  different arity.
- Mutations: drop the union fallback in `AnalyzedAst` (the lambda row goes red); emit the members
  in the wrong order or drop the function member (KSP row red); make `render()` expand (the
  signature-stays-short row red).

## 6. Acceptance

In the editor: `note("c").superimpose(x => x.` completes `SprudelPattern` methods; hovering a
`PatternLike` parameter shows the expansion; `Osc.sine(freq = ` shows `Number | IgnitorDsl`.
No door signature changed, no generated registration behaves differently at runtime (the KSP
golden of the registration, if one exists, differs only in `unionMembers`).

## Links

- `klangscript-intellisense.md`, the analyzer's roadmap; this task slots in beside its tiers.
- `editor-local-symbol-completion.md`, the neighbouring completion work.
- `/dsl-design` §3 (two doors, script-door defaults), `klangscript/MEMORY.md` (design decisions).
