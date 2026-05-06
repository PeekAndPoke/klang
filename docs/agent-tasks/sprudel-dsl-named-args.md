# Sprudel DSL — Named-Arg Migration

> **Status**: deferred. Pick up *after* `klangscript-named-arguments.md` is fully landed
> (analyzer diagnostics + KDoc sweep + general polish). The KlangScript-side machinery is
> in place — what's left is wiring sprudel into it.

## Goal

Bring **named-argument support** to the entire sprudel DSL by routing every primitive through
the same `@KlangScript.*` + spec-aware bridge path that the stdlib now uses. Today, sprudel
functions hit the legacy vararg-with-CallInfo registration helpers, which carry no parameter
names — so `note("c").gain(amount = 0.5)` errors with the transitional "named args not yet
supported" reject.

A clean side-effect of the migration is that `sprudel-ksp` and `SprudelRegistry` become
redundant — both can be retired. But that's a follow-on; the user-visible win is named args
across the music DSL.

---

## Background — what sprudel does today

| Layer                                          | File(s)                                                                                                              | Role                                                                                                                                                                                                                   |
|------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **User-facing typed functions**                | `sprudel/.../lang/lang_*.kt`, `lang/addons/*.kt`                                                                     | Annotated `@SprudelDsl`. ~4 overloads per primitive (Pattern receiver, String receiver, top-level mapper, MapperFn receiver — exact set varies). Each is a thin shim that calls the matching `_xxx` property delegate. |
| **`_xxx` property delegates**                  | `dslPatternExtension { … }`, `dslStringExtension { … }`, `dslPatternMapper { … }`, `dslPatternMapperExtension { … }` | (a) Define the dispatch logic — typically calls a private `applyXxx(p, args)` helper. (b) Side-effect: registers the handler into `SprudelRegistry` at class-init.                                                     |
| **`SprudelRegistry`**                          | `sprudel/.../lang/SprudelRegistry.kt`                                                                                | Global map populated by the property delegates.                                                                                                                                                                        |
| **`KlangScriptStrudelLib.registerSprudelDsl`** | `sprudel/lang/KlangScriptStrudelLib.kt`                                                                              | Iterates `SprudelRegistry` and bulk-registers everything as `registerVarargFunctionWithCallInfo` / `registerVarargMethodWithCallInfo` — **no parameter names**.                                                        |
| **Sprudel KSP doc processor**                  | `sprudel-ksp/SprudelDocsProcessor.kt`                                                                                | Parses `@SprudelDsl` functions' KDoc, emits `generatedSprudelKlangSymbols: Map<String, KlangSymbol>` for the Web UI.                                                                                                   |

Net result: docs know the parameter names, runtime registration doesn't. Named-arg calls
fail because the bridge has no `paramSpecs`.

---

## Design choice — additive `@KlangScript.*` alongside `@SprudelDsl`

The natural fix: annotate each user-facing function ALSO with the matching `@KlangScript.*`
annotation. Klangscript-ksp's existing emission picks them up and generates spec-aware
bridges (with `ParamSpec` lists) per registration. Sprudel's existing machinery keeps running
during the migration; once coverage is complete, the legacy path is retired.

### Why not auto-generate the four shapes from one source-of-truth?

Considered and rejected:

- DSL functions are not uniform. `gain` standalone returns a `PatternMapperFn`; `sound`
  standalone returns a `Pattern` (creator). Some primitives don't have a `String` receiver,
  some have no `MapperFn` form. KSP can't infer which shapes apply where without elaborate
  per-primitive metadata that defeats the simplification.
- LOC reduction is not the motivation. The boilerplate of declaring 4 overloads per primitive
  is acceptable; what's *not* acceptable is opting users out of named args.

### Why not blanket `@KlangScript.*` on every `@SprudelDsl` function?

Sprudel sometimes has multiple Kotlin overloads per `(name, receiver)` for ergonomic
Kotlin-side calls (e.g. `gain(Double)` and `gain(PatternLike)`). KlangScript only dispatches
by `(name, receiver)` — it has no overload-by-signature concept. So we **selectively** annotate
the canonical overload (typically the most permissive `PatternLike?` form).

To prevent accidental double-annotation, klangscript-ksp gains a build-time check: emit an
error if two `@KlangScript.Method`-annotated functions resolve to the same `(name, receiverClass)`.

---

## Per-function annotation pattern

For each primitive, add file-level `@file:KlangScript.Library("sprudel")` and a per-function
`@KlangScript.Method` (or `@KlangScript.Function` for top-level) on the chosen overload:

```kotlin
// At the top of e.g. lang_dynamics.kt
@file:KlangScript.Library("sprudel")
@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.sprudel.lang
…

// Existing public Kotlin function — UNCHANGED, still callable from Kotlin without CallInfo.
@SprudelDsl
fun SprudelPattern.gain(amount: PatternLike? = null): SprudelPattern =
    this._gain(listOfNotNull(amount).asSprudelDslArgs())

// NEW: internal overload that carries CallInfo. KlangScript-only — invisible to Kotlin
// callers because they can't pass CallInfo. The @KlangScript.Method routes klangscript-ksp
// to generate a spec-aware bridge for THIS overload. The body delegates straight to the
// private helper `applyGain` (or to the `_gain` delegate when cross-shape composition
// wiring is needed; see Pattern→String→MapperFn cases below).
@SprudelDsl
@KlangScript.Method
internal fun SprudelPattern.gain(amount: PatternLike? = null, callInfo: CallInfo): SprudelPattern =
    applyGain(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))
```

For shapes that need cross-shape composition (e.g. `String.sndPluck` defers to
`Pattern.sndPluck` after parsing), the internal overload calls the `_xxx` delegate so the
existing wiring stays:

```kotlin
@SprudelDsl
@KlangScript.Method
internal fun String.sndPluck(params: PatternLike? = null, callInfo: CallInfo): SprudelPattern =
    this._sndPluck(listOfNotNull(params).asSprudelDslArgs(callInfo), callInfo)
```

CallInfo flows: klangscript-ksp's existing `hasCallInfoParam` detection routes the bridge
through the CallInfo-aware path automatically when `callInfo: CallInfo` is the last param.

---

## KDoc parser parity — `sprudel-ksp` becomes redundant

Verified: `klangscript-ksp/KDocParser.kt` and `sprudel-ksp/KDocParser.kt` have identical
`ParsedKDoc` shapes (description, params, returnDoc, samples, category, tags, aliases,
paramTools for `@param-tool`, paramSubs for `@param-sub`). One was clearly forked from the
other. Klangscript-ksp's existing emission already produces `KlangParam` entries with all
the same fields the Web UI consumes today.

Output names don't collide during the migration:

| Processor                | File                              | Symbol                         |
|--------------------------|-----------------------------------|--------------------------------|
| `sprudel-ksp` (existing) | `GeneratedSprudelDocs.kt`         | `generatedSprudelKlangSymbols` |
| `klangscript-ksp` (new)  | `GeneratedSprudelRegistration.kt` | `generatedSprudelDocs`         |

So both run in parallel. Once every primitive has `@KlangScript.*` coverage, the
`KlangScriptLibraryDocsPage` consumer flips from `generatedSprudelKlangSymbols` to
`generatedSprudelDocs`, and `sprudel-ksp` is deleted.

---

## Migration plan

### Phase 0 — prerequisites in klangscript-ksp

- **Duplicate-name+receiver collision check.** Today the second
  `nativeExtensionMethods[receiver][name] = …` silently overwrites the first. Add an error at
  emission time so accidentally annotating two Kotlin overloads of the same `(name, receiver)`
  fails the build with a clear diagnostic.
- **Vararg + CallInfo helpers should thread `paramSpecs`.** Phase 5 of the KlangScript named-args
  refactor deferred this — `registerVarargMethodWithCallInfo` etc. don't accept `paramSpecs`
  yet. Sprudel's primitives are mostly single-param, but a few are genuinely vararg
  (e.g. `seq("a", "b", "c")`). Either:
    - Migrate those varargs to single-param signatures with an array (`seq(patterns: List<PatternLike>)`), OR
    - Add `paramSpecs` to the vararg-with-CallInfo helpers AND extend `resolveByParamSpec` to
      accept named-array values for vararg slots (the rule is already documented in
      `klangscript-named-arguments.md` Part 4.4).

### Phase 1 — file-level library annotations

Add `@file:KlangScript.Library("sprudel")` at the top of every `lang_*.kt` and addon file.
Mechanical, no behavior change. Verify klangscript-ksp's library-name resolution still works.

### Phase 2 — selective per-function annotations

For each `@SprudelDsl` user-facing function:

1. Pick the canonical overload (most permissive — usually `params: PatternLike? = null`).
2. Convert it to `internal` and add `callInfo: CallInfo` as the last param.
3. Add `@KlangScript.Method` (or `@KlangScript.Function` for top-level).
4. The original public overload (Kotlin-side, no CallInfo) stays as-is — it's a thin shim
   that delegates to the new internal overload by passing some default CallInfo (or directly
   to `applyXxx` / `_xxx`, same as today).

Order of attack: start with one addon file end-to-end (suggested: `lang_dynamics.kt`, small
and well-bounded), validate named-arg calls work via existing tests, then fan out across
the rest by file.

### Phase 3 — validation

For each migrated primitive, manually verify:

- Positional calls still work (legacy regression).
- Named calls work (`note("c").gain(amount = 0.5)`).
- CallInfo-dependent diagnostics still surface source locations correctly.
- The Web UI docs page renders the param's name + optional/default markers (already handled
  by Phase 7 work in klangscript-named-arguments.md).

Two registration paths run in parallel during this phase. The second one wins for KlangScript
dispatch (last-write-wins in `nativeExtensionMethods`), but the docs registry merge needs care
— flip the consumer in `KlangScriptLibraryDocsPage` once coverage is universal.

### Phase 4 — drop the legacy registry path

Once every `@SprudelDsl` function also has `@KlangScript.*`:

- Delete `KlangScriptStrudelLib.registerSprudelDsl`'s body (or reduce it to non-DSL setup
  like `registerObject(name, instance)` for any global symbols).
- Drop `sprudel-ksp/SprudelDocsProcessor` and the entire `sprudel-ksp` module.
- Switch `KlangScriptLibraryDocsPage` from `generatedSprudelKlangSymbols` to
  `generatedSprudelDocs`.

### Phase 5 — optional: drop `_xxx` delegates and `SprudelRegistry`

The property delegates serve two roles: (a) auto-registration into `SprudelRegistry`, and
(b) cross-shape composition wiring (e.g. `String._sndPluck` calls `Pattern._sndPluck`).

After Phase 4, role (a) is dead. Role (b) is real logic that can be expressed as plain
private functions. Convert each `internal val Foo._xxx by dsl…` into a plain
`private fun applyXxxOnFoo(receiver, args, callInfo): R` and update the user-facing
function bodies to call those helpers directly.

Once nothing references `dslPatternExtension` etc., delete those factories along with
`SprudelRegistry`.

This phase is cosmetic and can be deferred indefinitely. The DSL works fine with the
delegates left in place as harmless internal helpers.

---

## Open questions / risks

1. **Vararg primitives** — sprudel has some genuine varargs (e.g. `seq("a", "b", "c")`,
   `chord("c", "e", "g")`). These need either a typed-list signature change, OR Phase 0's
   vararg-spec-aware helper work. Check inventory before committing to either.

2. **Kotlin-side breakages from `internal` shift.** Today the user-facing functions are
   public. Making them internal hides them from outside the `sprudel` module. If anything
   in `klang/` or another consumer module calls `SprudelPattern.gain(...)` directly from
   Kotlin, those break. Likely none exist — sprudel is consumed via KlangScript script
   sources, not Kotlin call sites — but verify before flipping visibility.

3. **CallInfo as "required last param" vs `CallInfo? = null`** — making it nullable lets
   Kotlin-side test fixtures call the internal overload without synthesizing CallInfo. May
   simplify migration. Tradeoff: nullable adds an `!!` or `?:` in the body. Decide once.

4. **`@SprudelDsl` retention.** After Phase 4, the only thing `@SprudelDsl` does is mark
   things for `sprudel-ksp` (which is gone). Either keep it as a documentation marker for
   Kotlin-side authoring, or remove it entirely. Lean: delete — clutter without function.

5. **Sprudel-specific KDoc tags.** Verified parity with klangscript-ksp's parser today, but
   if sprudel-ksp evolves new tags before this migration lands, klangscript-ksp's parser
   needs to track them.

---

## Effort estimate (when picked up)

| Phase                                                       | Effort      | Risk                                        |
|-------------------------------------------------------------|-------------|---------------------------------------------|
| 0 — Klangscript-ksp prereqs (collision check, vararg specs) | 1 day       | Low — small, isolated changes               |
| 1 — File-level annotations                                  | 0.5 day     | Low — purely additive                       |
| 2 — Per-function selective annotation                       | 2-3 days    | Medium — many files, mechanical but careful |
| 3 — Validation across the DSL surface                       | 1 day       | Medium — needs eyes on actual script tests  |
| 4 — Retire legacy registry + sprudel-ksp                    | 0.5 day     | Low — deletion                              |
| 5 — Drop `_xxx` delegates (optional)                        | 2 days      | Medium — refactors live wiring              |
| **Total (mandatory)**                                       | **~5 days** |                                             |
| **Total (with cleanup)**                                    | **~7 days** |                                             |

---

## Success criteria

- [ ] `note("c").gain(amount = 0.5)` works.
- [ ] Every primitive in the sprudel docs page shows parameter names + optional markers.
- [ ] `KlangScriptStrudelLib.registerSprudelDsl` no longer iterates `SprudelRegistry`.
- [ ] `sprudel-ksp` module removed from the build.
- [ ] No regressions in existing positional-call test fixtures (sprudel jvmTest stays
  green except the pre-existing `ChoicePatternSpec` flake).
- [ ] CallInfo-driven diagnostics still surface accurate source locations in pattern
  errors.

---

## Cross-references

- `docs/agent-tasks/klangscript-named-arguments.md` — the prerequisite work in klangscript
  itself. Phase 5b of that doc covers the KSP rewrite that this plan piggy-backs on.
- `klangscript-ksp/src/main/kotlin/KlangScriptProcessor.kt` — the existing processor that
  picks up `@KlangScript.*` annotations and emits spec-aware bridges.
- `klangscript-ksp/src/main/kotlin/KDocParser.kt` — the parser that already understands
  every KDoc tag sprudel uses.
- `sprudel/src/commonMain/kotlin/lang/KlangScriptStrudelLib.kt` — the registry-iteration
  bridge that this plan retires.
- `sprudel-ksp/src/main/kotlin/SprudelDocsProcessor.kt` — the doc generator that this plan
  retires.
