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

| Feature               | Syntax                                                                          | Semantics                                                           | KlangScript Test                | KlangBlocks                 | KlangBlocks Test                      |
|-----------------------|---------------------------------------------------------------------------------|---------------------------------------------------------------------|---------------------------------|-----------------------------|---------------------------------------|
| `ExpressionStatement` | `expr`                                                                          | evaluates expression for side effects                               | `KlangScriptIntegrationTest.kt` | ✅ → `KBChainStmt`           | `KBCodeGenTest.kt`                    |
| `LetDeclaration`      | `let x = expr` / `let x`                                                        | mutable, block-scoped; uninitialized → `null`                       | `VariableTest.kt`               | ✅ → `KBLetStmt`             | `LetConstDeclarationRoundTripTest.kt` |
| `ConstDeclaration`    | `const x = expr`                                                                | immutable, block-scoped; must have initializer                      | `VariableTest.kt`               | ✅ → `KBConstStmt`           | `LetConstDeclarationRoundTripTest.kt` |
| `ImportStatement`     | `import * from "lib"` / `import * as ns from "lib"` / `import { a } from "lib"` | loads & evaluates library; binds exports into scope                 | `ImportSystemTest.kt`           | ✅ → `KBImportStmt`          | `ImportStatementRoundTripTest.kt`     |
| `ExportStatement`     | `export { a, b as c }`                                                          | marks symbols as visible to importers; library files only           | `ExportImportTest.kt`           | — skipped (top-level only)  | —                                     |
| `ExportDeclaration`   | `export name = expr`                                                            | combined immutable binding + auto-export under same name; top-level | `ExportDeclarationTest.kt`      | ✅ → `KBExportStmt`          | `ExportDeclarationRoundTripTest.kt`   |
| `ReturnStatement`     | `return` / `return expr`                                                        | exits current function; only valid in arrow function block body     | `ArrowFunctionBlockBodyTest.kt` | — skipped (not top-level)   | —                                     |
| `WhileStatement`      | `while (cond) { ... }`                                                          | loop; supports `break`/`continue`; `ReturnException` propagates     | `ControlFlowTest.kt`            | ⚠️ → `KBStringArg` fallback | —                                     |
| `DoWhileStatement`    | `do { ... } while (cond)`                                                       | executes body at least once; supports `break`/`continue`            | `ControlFlowTest.kt`            | ⚠️ → `KBStringArg` fallback | —                                     |
| `ForStatement`        | `for (init; cond; update) { ... }`                                              | C-style loop; init scoped to loop; supports `break`/`continue`      | `ControlFlowTest.kt`            | ⚠️ → `KBStringArg` fallback | —                                     |
| `BreakStatement`      | `break`                                                                         | exits enclosing loop; throws `BreakException`                       | `ControlFlowTest.kt`            | — (not top-level)           | —                                     |
| `ContinueStatement`   | `continue`                                                                      | skips to next loop iteration; throws `ContinueException`            | `ControlFlowTest.kt`            | — (not top-level)           | —                                     |

---

## Expressions

| Feature                | Syntax                                                                         | Semantics                                                                 | KlangScript Test                                                                                | KlangBlocks                                        | KlangBlocks Test                                              |
|------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|----------------------------------------------------|---------------------------------------------------------------|
| `NumberLiteral`        | `42`, `3.14`                                                                   | stored as `Double`; integer rendered without decimal                      | `LiteralsTest.kt`                                                                               | ✅ → `KBNumberArg`                                  | `KBCodeGenTest.kt`                                            |
| `StringLiteral`        | `"…"` / `'…'` / `` `…` ``                                                      | escape sequences processed; backtick = multiline                          | `LiteralsTest.kt`                                                                               | ✅ → `KBStringArg`                                  | `KBCodeGenTest.kt`                                            |
| `BooleanLiteral`       | `true` / `false`                                                               |                                                                           | `LiteralsTest.kt`                                                                               | ✅ → `KBBoolArg`                                    | `LiteralsRoundTripTest.kt`                                    |
| `NullLiteral`          | `null`                                                                         | no `undefined` in KlangScript                                             | `LiteralsTest.kt`                                                                               | ✅ → `KBIdentifierArg("null")`                      | `LiteralsRoundTripTest.kt`                                    |
| `Identifier`           | `x`, `myVar`                                                                   | looks up name in lexical environment                                      | `VariableTest.kt`                                                                               | ✅ → `KBIdentifierArg` / `KBIdentifierItem`         | `IdentifierRoundTripTest.kt`                                  |
| `CallExpression`       | `func(a, b)`                                                                   | evaluates callee, then each arg left-to-right                             | `KlangScriptIntegrationTest.kt`                                                                 | ✅ → `KBCallBlock` / `KBNestedChainArg`             | `KBCodeGenTest.kt`                                            |
| `MemberAccess`         | `obj.prop`                                                                     | property lookup; foundation of method chains                              | `MemberAccessTest.kt`                                                                           | ✅ → chain link / `KBStringLiteralItem`             | `KBCodeGenTest.kt`                                            |
| `BinaryOperation`      | `a + b`, `a == b`, `a && b`, `a ** b`, `a === b`, `a !== b`, `a in b` (17 ops) | standard arithmetic/comparison/logical/power; `&&`/`\|\|` short-circuit   | `ArithmeticTest.kt`, `ComparisonOperatorsTest.kt`, `BooleanLogicTest.kt`, `EasyFeaturesTest.kt` | ✅ → `KBBinaryArg`                                  | `BinaryUnaryRoundTripTest.kt`, `EasyFeaturesRoundTripTest.kt` |
| `UnaryOperation`       | `-x`, `!x`, `+x`, `++x`, `--x`, `x++`, `x--`                                   | negate/not/identity; prefix/postfix increment/decrement                   | `UnaryOperatorsTest.kt`, `EasyFeaturesTest.kt`                                                  | ✅ → `KBUnaryArg` (with `KBUnaryPosition`)          | `BinaryUnaryRoundTripTest.kt`, `EasyFeaturesRoundTripTest.kt` |
| `TernaryExpression`    | `cond ? a : b`                                                                 | evaluates `cond`; returns `a` if truthy, `b` if falsy                     | `EasyFeaturesTest.kt`                                                                           | ✅ → `KBTernaryArg`                                 | `EasyFeaturesRoundTripTest.kt`                                |
| `IndexAccess`          | `arr[i]`, `obj["key"]`                                                         | numeric or string index into array/object                                 | `EasyFeaturesTest.kt`                                                                           | ✅ → `KBIndexAccessArg`                             | `EasyFeaturesRoundTripTest.kt`                                |
| `AssignmentExpression` | `x = expr`, `obj.prop = expr`, `arr[i] = expr`                                 | assigns value to variable, property, or index; desugars compound `+=` etc | `EasyFeaturesTest.kt`                                                                           | ✅ → `KBAssignStmt` (stmt) / `KBStringArg` (expr)   | `EasyFeaturesRoundTripTest.kt`                                |
| `ArrowFunction`        | `x => expr` / `(a, b) => expr` / `() => { … }`                                 | closures; expression body = implicit return; block body requires `return` | `ArrowFunctionTest.kt`, `ArrowFunctionBlockBodyTest.kt`                                         | ✅ → `KBArrowFunctionArg` (body kept as raw source) | `ArrowFunctionRoundTripTest.kt`                               |
| `IfExpression`         | `if (cond) { … } else { … }`                                                   | expression-based; value = last expr in executed branch (or null)          | `ControlFlowTest.kt`                                                                            | ⚠️ → `KBStringArg` fallback                        | —                                                             |
| `TemplateLiteral`      | `` `Hello ${name}!` ``                                                         | backtick string with `${expr}` interpolation; parts concat to string      | `TemplateLiteralTest.kt`                                                                        | ⚠️ → `KBStringArg` fallback                        | —                                                             |
| `ObjectLiteral`        | `{ k: v, … }`, `{ name }` (shorthand)                                          | creates object; shorthand desugars to `name: name` at parse time          | `ObjectLiteralTest.kt`                                                                          | ⚠️ → `KBStringArg` fallback                        | `FallbackEncodingTest.kt`                                     |
| `ArrayLiteral`         | `[1, 2, 3]`                                                                    | creates array; any expression as element                                  | `ArrayLiteralTest.kt`                                                                           | ⚠️ → `KBStringArg` fallback                        | `FallbackEncodingTest.kt`                                     |

---

## KlangBlocks-Only Constructs

These have no corresponding AST node — they are block-model-level features.

| Construct                 | When present                                       | KlangBlocks Test                         |
|---------------------------|----------------------------------------------------|------------------------------------------|
| `KBStringLiteralItem`     | string at chain head: `"C4".transpose(1)`          | `KBBlocksOnlyConstructsRoundTripTest.kt` |
| `KBIdentifierItem`        | identifier at chain head: `sine.range(0.25, 0.75)` | `KBBlocksOnlyConstructsRoundTripTest.kt` |
| `KBBlankLine`             | blank line between statements                      | `KBBlocksOnlyConstructsRoundTripTest.kt` |
| `KBExprStmt`              | non-chain expression statement: `x++`, `x--`       | `EasyFeaturesRoundTripTest.kt`           |
| `KBAssignStmt`            | top-level assignment: `x = 5`, `arr[i] = v`        | `EasyFeaturesRoundTripTest.kt`           |
| `KBNewlineHint`           | line break within a chain                          | ❌                                        |
| `KBPocketLayout.VERTICAL` | any arg on a different line than its call          | `KBCodeGenTest.kt`                       |
