# KlangScript — Memory

## Current Status

- **Tests**: 735 passing on JVM ✅, JS ✅ (as of 2026-02-17)
- **Production**: Kotlin/JS builds working ✅
- **Parser**: Hand-rolled recursive descent (replaced better-parse due to Kotlin/JS issue #66)

## Recent Work (2026-02)

- Parser rewritten from better-parse to hand-rolled (1005-line lexer + recursive descent)
- Multiline backtick string source location tracking fixed (`Token.endLine` now tracked separately)
- `MultilineStringLocationTest.kt` added to validate multiline location tracking

## Completed Phases

- **Phase 1**: Foundation & parsing (AST, lexer, all literal types, operators, arrow functions, variables, objects,
  arrays)
- **Phase 2**: Tree-walking interpreter (all value types, scoping, native interop, error handling, stack traces,
  array/string/object built-in methods)
- **Phase 3**: API & integration (import/export, native Kotlin interop, library system, immutable builder pattern)

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

## Pending (see TODOS.MD)

- Array indexing (`arr[0]`)
- Control flow (`if/else`, `while`, `for`)
- Strict equality (`===`, `!==`)
- Template string interpolation
- Higher-order array methods (`map`, `filter`, etc.)
- Phase 4: Documentation & examples
