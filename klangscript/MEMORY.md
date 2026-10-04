# KlangScript: Memory

What is true in the klangscript module (and the stdlib in `:klangscript-libs`, which keeps no memory of
its own) now: the state, the decisions and rules in force, the traps and the open threads, each said once
with a pointer to its home. Short by design. To keep it that way: when a change moves something here,
update the section it touched in place, add ONE line to History (date, a few words, the link to the task
record), and put the narrative in the task record, which gets archived. The full dated record up to
2026-09-29 is `ref/memory-history.md`; read it only when you need the history of a decision.

## Current state

- **Parser**: hand-rolled lexer and recursive descent (`ref/parser-impl.md`). better-parse broke Kotlin/JS
  production builds; do not re-introduce a parser combinator library.
- **Language**: literals, operators including `===`, arrow functions, `let` / `const`, objects, arrays,
  `if` / `else` as an expression, `while` / `do-while` / `for`, `break` / `continue`, template literals,
  named arguments (`name = value`), imports and exports including `export name = expr`. The status per
  feature is `ref/feature-catalog.md` and `language-features/NN-*.md`.
- **Module split**: this module is the language and runtime (`klangScriptEngine()` builds a bare engine);
  the stdlib (`Ignitor` / `Ign`, `Katalyst` / `Kat`, `Math`, `Object`, `console`, the value-type extensions) and `klangScript()`
  live in `:klangscript-libs` (`klangscript-libs/CLAUDE.md`), which depends on this module, never the
  reverse. Tests that need the real stdlib live there too.
- **Configure lambdas**: `runtime/ArgAlignment` holds the trailing-lambda rule for the interpreter and the
  analyzer; `ParamSpec.isFunctionType` and `KlangType.functionParams` / `functionReturn` (KSP emits the
  `FunctionN` components) let the analyzer bind a lambda's parameters with the callee's declared types, so
  `.superimpose(x => x.` completes. Language docs: `language-features/04-functions.md` 4.10.
- **Callable objects**: `@KlangScript.Invoke` and the `invoke` dispatch, `ref/interpreter-impl.md`.
- **Stdlib doors** (`klangscript-libs`): the oscillator doors are `Ignitor.name(freq?, configure?)` (short `Ign.name`) with the knobs
  on immutable oscillator builder wrappers (`IgnitorBuilders.kt`); `OscSineBuilder` carries `harmonics`,
  `octaves`, `suboctaves`, `fundamental` (a gain), `analog` and `analogSpread`, and the six super builders
  carry `analogSpread` (0 to 1, 1 is a drift lane per voice). Door shapes follow `/dsl-design` §2.
- **Number methods** on `KlangScriptNumberExtensions`: `pow abs sqrt round floor ceil min max clamp rem mod
  log2 log10 ln exp sign semitones cents toSemitones db toDb`, and `toRatio()` on strings (via `Interval.get`
  from `tones`). The Kotlin door for the musical conversions is `common/math/PitchAndGain.kt` plus
  `val Interval.ratio`. `min` / `max` are clamps (rules register). No `coerce*` aliases (maintainer).

## Design Decisions

KlangScript keeps a JavaScript-like syntax but its semantics and its stdlib follow Kotlin.

- **The stdlib follows Kotlin conventions, not JavaScript**: Kotlin naming and argument style, named
  arguments as `name = value`. Array methods already do (`size`, `first`, `add`, `removeAt`, `reversed`,
  `joinToString`, `contains`); the string methods still carry JS names (`charAt`, `substring`, `toUpperCase`).
- **No implicit type coercion**: `string + number` and `string + null` throw a type error
  (`ArithmeticTest`); use a template literal. `string + string` concatenates.
- **No `switch`**: a Kotlin-style `when` expression is planned instead (no fall-through, exhaustive).
- **No `this`, no `var`, no `undefined`**: object methods are stored arrow functions; only `let` and
  `const`; only `null`.
- **Control flow is expression-based**, and the out-of-scope list (async, `class`, `try`, `eval`, JS
  globals) is `ref/language-design.md`.
- **A JS-syntax dialect for V1** (maintainer, 2026-09-05): no Kotlin grammar subset. The broken sub-type
  chains were fixed with configure lambdas on the existing arrow syntax and dedicated immutable builder
  types, no back-compat. Receiver lambdas are PARKED (they need several `this` and mutable builders); a
  Kotlin round trip is a later editor feature (paste-detect, "copy as Kotlin" from the AST), not grammar.
  Record: `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`.
- **One call form per callable object**: KlangScript has no overloads (`ref/interpreter-impl.md`).
- **Number literals take methods with Kotlin precedence**: `2.pow(7/12)` is a member call, `-x.abs()` is
  `-(x.abs())`, and `-6.db()` is refused as ambiguous (`ref/parser-impl.md`).

## Lessons

- **Lexer**: multi-char tokens before their single-char prefixes; a number scanner must look past the dot
  (`ref/parser-impl.md`). Template literal brace matching tracks `inString` / `escaped`.
- **Parser**: an arrow body `{` is an object literal only when `identifier :` follows; method chaining is
  a postfix loop, not recursion (`ref/parser-impl.md`).
- **`ReturnException` is control flow**, caught only at the function call boundary.
- **Blocks that must not leak `let` / `const`** (loop bodies, if branches) use `executeBlockInChildScope()`,
  never a bare `executeBlock()`.
- **`ast/Ast.kt` changes cascade** through parser, interpreter and tests (`ref/adding-features.md`).
- **Library maps merge per name; `checkArgsSize` is a minimum** (`ref/interpreter-impl.md`).
- **A stdlib string method must not share a name with a sprudel function**: sprudel registers every
  pattern function on strings too and the later import wins (`ratio` became `toRatio`). Guard in sprudel:
  `LangStdlibStringMethodCollisionSpec`.
- **`tones` spells a descending interval `"-5P"` or `"P-5"`**, never `"-P5"` (`IntervalRatio.kt`).
- **KSP-generated code in another module** needs the members it touches to be public
  (`NativeObjectExtensionsBuilder.builder` / `cls`), and Kotlin cannot smart-cast a property declared in
  another module.
- **A script-door default must be a safe literal**, or KSP emits no thunk and the trailing lambda cannot
  float (rules register guardrail, `/dsl-design` §3).
- **Keep the feature docs in sync**: after every implementation update `ref/feature-catalog.md` and the
  matching `language-features/NN-*.md` status.

## Open threads

- `klangscript/TODOS.MD`: higher-order array methods (`map`, `filter`, ...), the `when` expression,
  `for...in` / `for...of`, spread, destructuring.
- `docs/tasks/klangscript-native-object-operators.md` (arithmetic operators on native objects),
  `docs/tasks/klangscript-union-types.md`, `docs/tasks/klangscript-intellisense.md`,
  `docs/tasks/klangscript-named-args-docs-polish.md`.

## History

One line per step; the narrative is in the linked record or in `ref/memory-history.md`.

- 2026-02 to 2026-03: phases 1 to 4c, from parsing to control flow, scoping fixes and native interop
  tests (`ref/memory-history.md#completed-phases`, `#recent-work-2026-03`).
- 2026-05: `export name = expr` (`ref/memory-history.md#recent-work-2026-05`).
- 2026-09-05: stays a JS-syntax dialect for V1 (`ref/memory-history.md#design-decisions-kotlin-style-not-js-style`).
- 2026-09-06: configure lambdas S1 to S6, the `invoke` operator, the `klangscript-libs` split
  (`docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`,
  `docs/tasks-archive/2026-09/20260906-klangscript-libs-split.md`). The Pipeline and Master builders of S5
  and S6 are retired since (rules register).
- 2026-09-07: `@KlangScript.Invoke`; sine partial banks (`docs/plans/sine-partial-banks.md`).
- 2026-09-08: methods on number literals, the number methods, library registration merges per name
  (`docs/tasks-archive/2026-09/20260908-klangscript-number-methods.md`).
- 2026-09-10: `analogSpread` on the super family (`docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md`).
- 2026-09-29: this file restructured; the old status, phases and dated entries are in `ref/memory-history.md`.
- 2026-10-02: a non-callable value on a function-typed native parameter is a type error ("expected a function, got
  a number"), found while building `through` (`docs/tasks-archive/2026-10/20261002-through-signal-chains.md`).
- 2026-10-04: the Ignitor/Katalyst naming: the stdlib object is `Ignitor` with the alias `Ign` (a
  `@KlangScript.Constant`, the house pattern for a second name), `Katalyst` with `Kat`, `Ignitor.slot` and
  `Katalyst.slot` hand back the Kotlin door's objects (`docs/plans/ignitor-katalyst-naming.md`).
- 2026-10-04: error locations: a script error thrown without a location inside a native call gets the call's
  location (`guardNativeCall`, via `KlangScriptRuntimeError.withLocation`), one with a location keeps it; so a
  wrong Katalyst knob value marks `k.reverb(...)` (guards: `NativeCallErrorLocationSpec`, `KlangScriptKatalystDoorParitySpec`).
