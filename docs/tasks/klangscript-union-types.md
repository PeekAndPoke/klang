# KlangScript union types: tell the editor what an `XLike` parameter accepts

> **Written 2026-09-18, updated 2026-09-28, not started.** Priority: **NICE** (editor convenience; no engine or sound
> impact). Outside the signal-flow work stream on purpose. Follow-ups that depend on it:
> [`katalyst-master-configure-doors.md`](katalyst-master-configure-doors.md), and (2026-09-29) the edit-time diagnostic
> for a typo in a shape function ([`silent-shape-discard-on-error.md`](silent-shape-discard-on-error.md), via
> [`future/editor-diagnostics.md`](future/editor-diagnostics.md)).
>
> **Direction added 2026-09-29 (maintainer):** "TypeScript-like string union types" are needed before those
> diagnostics can be shown. That reaches past §3.3 as written, which checks "is it a String", not "is the string
> valid": string LITERAL unions (`"soft" | "hard"`) would let the editor check the value too. The maintainer's
> "EnumString" direction for these is §3.6.
> The 2026-09-28 update records the maintainer's direction (§3.5): typed Kotlin overloads that
> KlangScript does not see, one generic script door that dispatches by kind and throws on an
> unknown one, and IntelliSense that checks arguments before runtime (§3.3, §D2).
> Touches KSP, so the mandatory mutation tier of `/review-loop` applies, and the stone rule on
> complexity applies to §3: the declaration form is a maintainer decision (§D1).

## 1. The problem

Several doors accept "one of several things" and declare the parameter as an alias of `Any`:

| alias | where | accepts at runtime |
|---|---|---|
| `PatternLike` | `sprudel/src/commonMain/kotlin/lang/lang.kt:24` | through the pattern conversion (`toListOfPatterns`, `sprudel/.../lang/lang_helpers.kt:336`): a pattern, a `SprudelPatternEvent`, a string (mini-notation), a number, a boolean, a list whose items are converted the same way, `null`; on the doors that take one, a mapper lambda or a field accessor |
| `IgnitorDslLike` | `klangscript-libs/src/commonMain/kotlin/stdlib/KlangScriptOscExtensions.kt:18` | a number or an ignitor node |

The runtime sorts the value out by kind, and that works. The EDITOR knows nothing: KSP emits
`KlangType(simpleName = "PatternLike", isTypeAlias = true)` and stops. Consequences a user feels:

- **No completion inside a lambda argument.** The analyzer types a lambda's parameters from the
  declared parameter type (`klangscript/src/commonMain/kotlin/intel/AnalyzedAst.kt:348`,
  `expected[i].functionParams`). An alias of `Any` has no function type, so in
  `note("c").superimpose(x => x.` the `x` is untyped and nothing completes.
- **The docs popup shows only the alias name**, not what may be passed.
- **No diagnostics are possible** for a wrong kind of argument, because `Any` accepts everything.
- **A wrong kind vanishes at runtime.** The pattern conversion drops a value it does not know
  (`else -> null`, `lang_helpers.kt:382`): no error, the argument is simply not there. The user
  hears something missing and gets no message.
- **The KDoc on the alias is out of date** (it lists four kinds; the conversion takes seven), which
  is what happens to a union that lives only in prose.

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
- The static checks that exist are about names and arity only: `NamedArgumentChecker`
  (`klangscript/src/commonMain/kotlin/intel/`) flags mixed positional and named arguments, unknown
  names, duplicates and missing required parameters. Nothing checks an argument's TYPE. The
  per-expression type map it reads (`ExpressionTypeInferrer`, built in the same `AnalyzedAst` pass)
  is what a type check builds on.
- KSP does not look at Kotlin visibility: it registers whatever carries the annotation. The
  generated registration compiles in the same module, so an `internal` script door should be
  callable from it (unverified, §3.5).

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

Either form must express the RECURSIVE member of `PatternLike`: a list whose items are themselves
`PatternLike`. In (a) that is a parameter `list: List<PatternLike>`, and KSP must stop at the alias
instead of expanding it again; in (b) a string such as `"List<PatternLike>"`. The type check (§3.3)
then checks the items of a list literal against the same union.

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
- **Argument type check (the maintainer's goal, 2026-09-28: "intellisense checks params before
  runtime").** Add `KlangType.accepts(other)`: true if `other` is assignable to any member (Kotlin
  `Int` and `Double` both count as the script's `Number`). A new `ArgumentTypeChecker` beside
  `NamedArgumentChecker` walks the calls, binds each argument to its parameter (reuse
  `ArgumentBinding`), and flags an argument whose inferred type is KNOWN and accepted by no member.
  It is SILENT when the inferred type is unknown (a script-defined value, an untyped lambda
  result), the same way `NamedArgumentChecker` is silent on an unresolved callee; a noisy checker
  gets ignored. Items of a list literal are checked against the recursive member. The check covers
  every typed parameter, not only unions: `Number` against `String` is the same question.
- **§D2, severity. Parked for the maintainer.** The 2026-09-18 draft said WARNING, never error,
  because the runtime silently coerces. With §3.5 the runtime THROWS on a kind outside the union,
  so a known non-member is a certain runtime error, and ERROR is the honest severity.
  Recommendation: ERROR for a known type no member accepts, nothing for unknown. Mini-notation
  strings are not a concern here: the check asks "is it a String", not "is the string valid".
- **Completion after a union-typed expression** (a union as a RETURN type) is out of scope: no
  door returns one.

### 3.4 What does not change

The interop and script semantics, apart from the throw on an unknown kind (§3.5). Kotlin has no
union types (Rich Errors, announced at KotlinConf 2025, allow only one ordinary type plus declared
error types, so they cannot express `PatternLike`); the Kotlin side gets its types from overloads
instead, §3.5.

### 3.5 Maintainer direction, 2026-09-28: Kotlin overloads, one generic script door

- **Kotlin**: typed overloads per member (`note(n: String)`, `note(n: Number)`,
  `note(n: SprudelPattern)`, ...), NOT annotated, so KlangScript never sees them.
- **KlangScript**: one generic door per concept, carrying the union (§3.1), dispatching by kind.
  It THROWS on a kind outside the union, with a message that names the door, the parameter, the
  kind it got and the kinds it accepts. That replaces the silent drop at `lang_helpers.kt:382`.
- **Catch 1, hide the generic door from Kotlin.** If a public `note(n: Any)` stands beside
  `note(n: String)`, Kotlin overload resolution quietly falls back to the `Any` door for every
  other argument, and the overloads check nothing. The generic door is `internal` (or otherwise
  unreachable from Kotlin callers). Verify first that the KSP-generated registration calls an
  `internal` door on JVM and JS (§2: KSP ignores visibility today).
- **Catch 2, mixed varargs stay generic on Kotlin too.** `seq("a", pat, 1)` cannot be spelled as
  overloads. The 122 `vararg args: PatternLike` doors keep `PatternLike` on the Kotlin side and
  rely on the same runtime throw. Overloads pay off on single-value parameters (`n`, `amount`,
  `factor`, the envelope stages).
- **Catch 3, the throw is a behaviour change.** A song that passes an unknown kind today plays
  with that argument missing; after the change it stops with an error. Render every builtin song
  and the tutorials once before and after to find any that relied on the drop.
- **Parity.** Both doors keep the same name, meaning and scale (`/dsl-design` §3, §4); the
  door-parity spec feeds one value of each union member through the Kotlin overload and through
  the script door and asserts the same pattern, so a member added on one side only goes red.
- **Nullable parameters** (`attack: PatternLike?`): `null` stays a member of the conversion, so an
  omitted optional parameter still means "not set", never a throw.

### 3.6 Maintainer direction, 2026-09-29: "EnumString" parameters

"String unions can be expressed as Enums on the kotlin side, so we need a way to expose an 'EnumString' param to
klangscript, while the intellisense needs to be aware of all possible values. This would also be handy for any other
string-enum based param, like the distort shapes, the adsr-curves and similar."

So a string union is not only a union of TYPES (§3.1) but also a union of VALUES: a parameter whose value is one of a
closed set of names. The Kotlin side owns the set; KlangScript sees a string; IntelliSense knows every allowed name.

**Where the sets live today** (read 2026-09-29), two forms the mechanism has to serve:

| set | Kotlin form | where |
|---|---|---|
| ADSR curves (`adsrCurves(...)`, the filter and pitch curve doors) | a public `enum class AdsrCurve` | `audio_bridge/src/commonMain/kotlin/AdsrDef.kt`, names read by `AdsrCurves.curveOf` (`AdsrCurves.kt`) |
| distort shapes (`distort(amount, shape)`, `shape(...)`) | a name catalogue: `DistortionShapes.names` plus `aliases`, the POSITION is the wire index; the backend's `internal enum class DistortionShape` mirrors it, pinned by a spec | `audio_bridge/src/commonMain/kotlin/DistortionShapes.kt` |
| body materials (`body(wet, material)`) | a name catalogue, `BodyMaterials` | `audio_bridge/src/commonMain/kotlin/BodyMaterials.kt` |
| vowels, LFO/tremolo shapes, and similar | the tremolo shapes are the `LfoShapes` catalogue in `audio_bridge` (since 2026-09-29 the tremolo composes the oscillators; the backend enum `LfoShape` is gone) | to be listed when the task starts |

**Ideas, not decided:**

- **One declaration form for both.** An annotation or a marker type that points KSP at the set, e.g. a parameter typed
  `EnumString<AdsrCurve>` or annotated `@KlangScript.OneOf(DistortionShapes::class)` where the catalogue implements a
  small interface (`names`, optional `aliases`). KSP emits the names into the docs registry as a string literal union
  (`"soft" | "hard" | ...`), so the analyzer needs no Kotlin reflection at edit time. The declaration form is §D1's
  question, widened to value unions.
- **IntelliSense:** completion INSIDE the string literal offers the canonical names (not the aliases); hover shows the
  set; a literal that is not in the set is a diagnostic (the diagnostics topic, `future/editor-diagnostics.md`).
- **Runtime stays as it is:** coerce, never `require` (stone rule "The Motor stays raw"): an unknown name keeps today's
  fallback (distort: `soft`), names stay case-insensitive, aliases keep working.
- **Mixed parameters.** `distort`'s `shape` is `IgnitorDslLike`: a name, a number (the index) or a slot. So the value
  union is one member of a type union (`OneOf<DistortionShapes> | Number | IgnitorDsl`), which is exactly §3.1's shape.
- **Sprudel pattern strings.** On sprudel doors the string is often mini-notation (`distort(0.5, "<soft hard>")`), not a
  single literal. The check has to parse the mini-notation and test each atom against the set, and completion has to
  work inside the pattern string. That is the harder half, and worth its own step.
- **NOT SETTLED (maintainer, 2026-09-29): validating inside mini-notation.** The mini-notation parser would need a
  way to be told which values are acceptable for the door it feeds; the same would let `note("a1 x1")` report `x1`
  as not a valid note. That means one layer of abstraction somewhere (a validate callback or similar), and how such
  a layer feeds IntelliSense (completion and diagnostics inside a pattern string) is an open question. Not to be
  designed now; any design of this section must not assume it is solved.
- **House rule check:** "Wire types over enums" allows an enum for a closed, parameter-less set, which these are; the
  wire keeps carrying what it carries today (the curve enum, the shape index).

## 4. Steps

1. **Spike §D1** (cross-module lookup of the marker function), decide the declaration form.
2. **KSP emission + model**: `unionMembers` populated, shared `val` per alias, `renderExpanded()`,
   the `*Like`-without-union warning. Declare the unions of `PatternLike` and `IgnitorDslLike`.
3. **Analyzer**: lambda typing through a union; hover and docs popup show the expansion.
4. **Argument type check** (§3.3): `accepts()`, `ArgumentTypeChecker`, severity per §D2.
5. **Runtime throw** (§3.5): the conversion throws on an unknown kind with the named message;
   builtin songs and tutorials rendered before and after.
6. **Kotlin overloads** (§3.5): verify the `internal` generic door first, then add the typed
   overloads door family by family (single-value parameters only), each with its parity spec.

Steps 4 to 6 are independent of each other once step 2 has landed; step 5 can even go first,
since it needs no union metadata, only the member list the conversion already switches on.

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
- Type check: a boolean passed to a `Number` parameter is flagged; a number passed to a
  `PatternLike` parameter is not; an argument of unknown type is not (the silence row); a list
  literal with one bad item is flagged on that item.
- Runtime: an unknown kind throws with the door, parameter and accepted kinds in the message;
  `null` into a nullable parameter does not throw; a nested list still converts.
- Kotlin: a call `note(someAny)` with a static type of `Any` does NOT compile from outside the
  module (the generic door is hidden); each overload and the script door give the same pattern.
- Mutations: drop the union fallback in `AnalyzedAst` (the lambda row goes red); emit the members
  in the wrong order or drop the function member (KSP row red); make `render()` expand (the
  signature-stays-short row red); make the checker flag unknown types (silence row red); put the
  silent `else -> null` back (throw row red).

## 6. Acceptance

In the editor: `note("c").superimpose(x => x.` completes `SprudelPattern` methods; hovering a
`PatternLike` parameter shows the expansion; `Osc.sine(freq = ` shows `Number | IgnitorDsl`.
`note(Osc.sine())` (an ignitor node, not a `PatternLike` member) is marked in the editor before the
code runs, and throws with a named message if run anyway. A Kotlin caller passing an ignitor node
to `note` gets a compile error.

## Links

- `klangscript-intellisense.md`, the analyzer's roadmap; this task slots in beside its tiers.
- `editor-local-symbol-completion.md`, the neighbouring completion work.
- `/dsl-design` §3 (two doors, script-door defaults), `klangscript/MEMORY.md` (design decisions).
