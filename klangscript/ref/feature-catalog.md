# KlangScript — Feature Catalog

Ground truth: `ast/Ast.kt` — one row per sealed subclass of `Statement` / `Expression`.
**Update this file whenever a feature is added, documented, or newly tested.**

## Sync Rules

1. Every row must link a KlangScript test file
2. Every KlangBlocks-supported row must link a round-trip / source-map test file
3. ⚠️ = opaque fallback: round-trips correctly but inner structure is not block-editable
4. ❌ = not implemented / not tested yet; — = intentionally not applicable

---

## Statements

| Feature               | Syntax                                                                          | Semantics                                                       | KlangScript Test                | KlangBlocks                | KlangBlocks Test   |
|-----------------------|---------------------------------------------------------------------------------|-----------------------------------------------------------------|---------------------------------|----------------------------|--------------------|
| `ExpressionStatement` | `expr`                                                                          | evaluates expression for side effects                           | `KlangScriptIntegrationTest.kt` | ✅ → `KBChainStmt`          | `KBCodeGenTest.kt` |
| `LetDeclaration`      | `let x = expr` / `let x`                                                        | mutable, block-scoped; uninitialized → `null`                   | `VariableTest.kt`               | ✅ → `KBLetStmt`            | ❌                  |
| `ConstDeclaration`    | `const x = expr`                                                                | immutable, block-scoped; must have initializer                  | `VariableTest.kt`               | ✅ → `KBConstStmt`          | ❌                  |
| `ImportStatement`     | `import * from "lib"` / `import * as ns from "lib"` / `import { a } from "lib"` | loads & evaluates library; binds exports into scope             | `ImportSystemTest.kt`           | ✅ → `KBImportStmt`         | ❌                  |
| `ExportStatement`     | `export { a, b as c }`                                                          | marks symbols as visible to importers; library files only       | `ExportImportTest.kt`           | — skipped (top-level only) | —                  |
| `ReturnStatement`     | `return` / `return expr`                                                        | exits current function; only valid in arrow function block body | `ArrowFunctionBlockBodyTest.kt` | — skipped (not top-level)  | —                  |

---

## Expressions

| Feature           | Syntax                                         | Semantics                                                                 | KlangScript Test                                                         | KlangBlocks                                        | KlangBlocks Test   |
|-------------------|------------------------------------------------|---------------------------------------------------------------------------|--------------------------------------------------------------------------|----------------------------------------------------|--------------------|
| `NumberLiteral`   | `42`, `3.14`                                   | stored as `Double`; integer rendered without decimal                      | `LiteralsTest.kt`                                                        | ✅ → `KBNumberArg`                                  | `KBCodeGenTest.kt` |
| `StringLiteral`   | `"…"` / `'…'` / `` `…` ``                      | escape sequences processed; backtick = multiline                          | `LiteralsTest.kt`                                                        | ✅ → `KBStringArg`                                  | `KBCodeGenTest.kt` |
| `BooleanLiteral`  | `true` / `false`                               |                                                                           | `LiteralsTest.kt`                                                        | ✅ → `KBBoolArg`                                    | ❌                  |
| `NullLiteral`     | `null`                                         | no `undefined` in KlangScript                                             | `LiteralsTest.kt`                                                        | ✅ → `KBIdentifierArg("null")`                      | ❌                  |
| `Identifier`      | `x`, `myVar`                                   | looks up name in lexical environment                                      | `VariableTest.kt`                                                        | ✅ → `KBIdentifierArg` / `KBIdentifierItem`         | ❌                  |
| `CallExpression`  | `func(a, b)`                                   | evaluates callee, then each arg left-to-right                             | `KlangScriptIntegrationTest.kt`                                          | ✅ → `KBCallBlock` / `KBNestedChainArg`             | `KBCodeGenTest.kt` |
| `MemberAccess`    | `obj.prop`                                     | property lookup; foundation of method chains                              | `MemberAccessTest.kt`                                                    | ✅ → chain link / `KBStringLiteralItem`             | `KBCodeGenTest.kt` |
| `BinaryOperation` | `a + b`, `a == b`, `a && b` (13 ops)           | standard arithmetic/comparison/logical; `&&`/`\|\|` are short-circuit     | `ArithmeticTest.kt`, `ComparisonOperatorsTest.kt`, `BooleanLogicTest.kt` | ✅ → `KBBinaryArg`                                  | ❌                  |
| `UnaryOperation`  | `-x`, `!x`, `+x`                               | `-` negates number; `!` logical NOT; `+` identity                         | `UnaryOperatorsTest.kt`                                                  | ✅ → `KBUnaryArg`                                   | ❌                  |
| `ArrowFunction`   | `x => expr` / `(a, b) => expr` / `() => { … }` | closures; expression body = implicit return; block body requires `return` | `ArrowFunctionTest.kt`, `ArrowFunctionBlockBodyTest.kt`                  | ✅ → `KBArrowFunctionArg` (body kept as raw source) | ❌                  |
| `ObjectLiteral`   | `{ k: v, … }`                                  | creates object; only identifier/string keys; no computed keys             | `ObjectLiteralTest.kt`                                                   | ⚠️ → `KBStringArg` fallback                        | ❌                  |
| `ArrayLiteral`    | `[1, 2, 3]`                                    | creates array; any expression as element                                  | `ArrayLiteralTest.kt`                                                    | ⚠️ → `KBStringArg` fallback                        | ❌                  |

---

## KlangBlocks-Only Constructs

These have no corresponding AST node — they are block-model-level features.

| Construct                 | When present                                       | KlangBlocks Test |
|---------------------------|----------------------------------------------------|------------------|
| `KBStringLiteralItem`     | string at chain head: `"C4".transpose(1)`          | ❌                |
| `KBIdentifierItem`        | identifier at chain head: `sine.range(0.25, 0.75)` | ❌                |
| `KBBlankLine`             | blank line between statements                      | ❌                |
| `KBNewlineHint`           | line break within a chain                          | ❌                |
| `KBPocketLayout.VERTICAL` | any arg on a different line than its call          | ❌                |
