# KlangScript — Memory

## Current Status

- **Tests**: 970+ passing on JVM ✅ (as of 2026-03-02)
- **Production**: Kotlin/JS builds working ✅
- **Parser**: Hand-rolled recursive descent (replaced better-parse due to Kotlin/JS issue #66)

## Recent Work (2026-09)

- **Pipeline builders, S6 (2026-09-06)**: `Pipeline(p => p.filterMod().vca(v => v.expK(2)).distort())`,
  presets `Pipeline.modern(p => p.tuneVca(...))`; stage knobs append, `tuneVca`/`tuneFilter` configure
  existing stages (error when none). `Pipeline.of`, `Stage` and the stage knob objects deleted.

- **Effect and master builders, S3 + S5 (2026-09-06, `klangscript-libs`)**: `.eq(e => e.band().tap())`,
  `.phaser(rate, center, sweep, x => x.wet())`, `.shimmer(..., x => x.wet())` on `EqBuilder`/
  `PhaserBuilder`/`ShimmerBuilder` (`EffectBuilders.kt`; `EqBuilder` delegates to the audio_bridge
  Kotlin `Eq.band/tap`, which stay as the engine-level API). `Master(m => m.reverb(r => ...).gain(2.5)
  .limiter(l => ...))` via the `invoke` operator, aliases `Master.build`/`Master.default`;
  `Master.of` and `MasterFx` deleted. The sound-tree baseline spec now fingerprints master and inline
  pipeline chains too. Lesson: `shimmer.pitches` needed a literal default (`null`) for the lambda to
  float; any door parameter with a non-literal default blocks the trailing lambda (KSP guard).

- **`invoke` operator (S4, 2026-09-06)**: a `NativeObjectValue` callee dispatches to the `invoke`
  extension method of its type through the spec-aware member-call path (`Interpreter.evaluateCall`);
  `ExpressionTypeInferrer.resolveCallable` falls back to `getCallable("invoke", type)`;
  `KlangCallable.signature` renders it as `Master(...)`; member completion hides `invoke`.
  `NativeOperatorNames` holds the name. Arithmetic operators of the same plan: not built.

- **Configure-lambda doors, S2 (2026-09-06, `klangscript-libs`)**: the 16 oscillator doors are
  `Osc.name(freq?, configure?)`, knobs live on immutable `Osc*Builder` value wrappers
  (`IgnitorBuilders.kt`), the 17 sub-type extension objects are gone. Lesson: the old `pluck`/
  `superpluck` doors baked SEALED `Constant` defaults while the nodes carry open `Slots.*` params;
  a builder door that falls back to node defaults changes the tree. `BuiltInSongsSoundTreeBaselineSpec`
  (root `jvmTest`) is the guard that caught it: every builtin song's sound trees, fingerprinted.

- **Module split (2026-09-06)**: `stdlib/` (35 files + `PlatformConsole`) moved to the new
  `:klangscript-libs` module together with `stdlibLib` and `klangScript()`; the core gained
  `klangScriptEngine()` (bare engine) and dropped its `audio_bridge` dependency and its own KSP
  processor run (the KSP *plugin* stays applied for kotest). Tests that need the real stdlib
  (`stdlib/`, docs, `GeneratedRegistrationTest`, analyzer tests using `generatedStdlibDocs`)
  moved with it. `sprudel` and `klangscript-ui` depend on the libs module. Plan and facts:
  `docs/tasks/klangscript-libs-split.md`. Lesson: KSP-generated code in another module needs the
  members it touches to be public (`NativeObjectExtensionsBuilder.builder/cls`), and Kotlin cannot
  smart-cast a property declared in another module (two moved tests needed explicit casts).

- **Configure-lambda foundation (S1 of `docs/tasks/dsl-configure-lambdas.md`)**:
  `runtime/ArgAlignment` (trailing-lambda rule, shared by interpreter and analyzer),
  `ParamSpec.isFunctionType`, `KlangType.functionParams/functionReturn` (KSP now emits the
  `FunctionN` components, aliases like `PatternMapperFn` included), and the analyzer binds a
  lambda argument's parameters with the callee's declared types (`.superimpose(x => x.` now
  completes). Language docs: `language-features/04-functions.md` 4.10.

## Recent Work (2026-05)

- Added `ExportDeclaration` — new top-level form `export name = expr` (immutable binding +
  auto-export under same name). Parser, AST, interpreter, feature catalog, and language docs
  all updated. Existing
  `export { a, b as c }` form remains. Foundation for Projekt Klangbuch's named-part
  imports across modules.

## Recent Work (2026-03)

- Implemented "medium" language features: `if/else` expression, `while`/`do-while`/`for` loops,
  `break`/`continue`, template literals with `${...}` interpolation
- Fixed per-iteration loop scoping (`executeBlockInChildScope()`) and if-branch scoping
- Fixed template literal brace matching to handle strings inside `${}` expressions
- Fixed `NumberValue.toDisplayString()` cross-platform: whole numbers format as `"42"` (not `"42.0"`)
- Implemented `string + string` concatenation (no implicit coercion — not `string + number`)
- Added `NativeInteropConversionTest` covering `wrapAsRuntimeValue`, `convertToKotlin` for all
  array types, `convertArgToKotlin`, `checkArgsSize`, and actual arrow-function argument passing
- Updated all `language-features/` files with correct ✅/🟡/❌ status

## Design Decisions (Kotlin-style, not JS-style)

- **No `switch`**: will implement `when`-expression (no fall-through, exhaustive, expression form)
- **No implicit type coercion**: `string + number` throws TypeError; use template literals instead
- **Immutable stdlib**: array/list/string/object methods will follow Kotlin conventions (no in-place
  mutation where possible)
- **No `this` keyword**: object methods use stored arrow functions (`obj.fn = (a, b) => ...`)
- **No `var`**: only `let` and `const`
- **No `undefined`**: only `null`
- **Stays a JS-syntax dialect for V1 (decided 2026-09-05)**: the maintainer considered a Kotlin
  subset (trailing lambdas `f { }`, receiver lambdas) to fix the broken sub-type chains
  (`Osc.supersaw().voices(9).lowpass(800).analog(3)` cannot reach `.analog`). Decided instead:
  **configure lambdas on the existing arrow syntax with dedicated immutable builder types**,
  `Osc.sine(freq, configure: (OscSineBuilder) -> OscSineBuilder)`; knobs leave `IgnitorDsl`;
  NO back-compat; `Master(...)`/`Pipeline(...)` via the `invoke` operator
  (`docs/tasks/klangscript-native-object-operators.md` must be revised first). Full plan and
  every closed decision: `docs/tasks/dsl-configure-lambdas.md`. Receiver lambdas PARKED (need
  multiple `this` + mutable builders). Kotlin round trip is an editor feature for later
  (paste-detect + "copy as Kotlin" from the AST), not a grammar change. Analyzer gap that this
  work closes: arrow params bind with `type = null`, so `.superimpose(x => x.` has no completion.

## Completed Phases

- **Phase 1**: Foundation & parsing (AST, lexer, all literal types, operators, arrow functions, variables, objects,
  arrays)
- **Phase 2**: Tree-walking interpreter (all value types, scoping, native interop, error handling, stack traces,
  array/string/object built-in methods)
- **Phase 3**: API & integration (import/export, native Kotlin interop, library system, immutable builder pattern)
- **Phase 4a**: Medium control-flow features (if/else expr, loops, break/continue, template literals, ternary, ===)
- **Phase 4b**: Scoping correctness audit + fixes (per-iteration scope, if-branch scope, closure tests)
- **Phase 4c**: NativeInterop tests + string concatenation fix

## Lessons Learned

**Multi-char tokens must be defined before single-char tokens** in the lexer — e.g. `==` before `=`, `!=` before `!`,
`<=` before `<`. Initial comparison operator implementation failed because of wrong ordering.

**Arrow function with object literal body** (`x => { key: val }`) is ambiguous with block body — must disambiguate at
parse time by peeking for `identifier:` pattern.

**Method chaining requires a loop**, not recursion — `obj.method().prop.method2()` must allow any alternating order of
call and member access. Recursive descent naturally handles this with a postfix loop.

**`ReturnException` is not an error** — it's a control flow mechanism. Throw on `return` statement, catch at function
call site. Don't let it bubble past function boundaries.

**better-parse breaks in Kotlin/JS production builds** — the hand-rolled parser has zero dependencies and works on both
platforms. Do not re-introduce parser combinator libraries.

**`ast/Ast.kt` changes have wide impact** — every AST node change requires updates to parser + interpreter + potentially
all existing tests using `FunctionValue` or affected node constructors.

**`executeBlockInChildScope()`** — always use this for any block that should not leak `let`/`const` to the outer scope
(loop bodies, if branches). Never call bare `executeBlock()` for these.

**Template literal brace matching** — naive depth counter fails for `${obj.toString("{}")}`. Track `inString`/`escaped`
state when scanning for the closing `}`.

**Feature-catalog files must be kept in sync** — after every implementation, update the relevant
`language-features/NN-*.md` file to reflect the new ✅/🟡/❌ status.

## Pending (see TODOS.MD)

- Higher-order array methods (`map`, `filter`, `forEach`, `find`, `some`, `every`, `reduce`)
- `when`-expression (replacement for `switch`)
- `for...in` / `for...of` loops
- Kotlin-style string/array/object stdlib (separate module)
- Spread operator, destructuring
