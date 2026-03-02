# KlangScript — Memory

## Current Status

- **Tests**: 970+ passing on JVM ✅ (as of 2026-03-02)
- **Production**: Kotlin/JS builds working ✅
- **Parser**: Hand-rolled recursive descent (replaced better-parse due to Kotlin/JS issue #66)

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
