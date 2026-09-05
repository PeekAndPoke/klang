# KlangScript — Feature Catalog

Ground truth: `ast/Ast.kt` — one row per sealed subclass of `Statement` / `Expression`.
**Update this file whenever a feature is added, documented, or newly tested.**

## Sync Rules

1. Every row must link a KlangScript test file
2. ❌ = not implemented / not tested yet; — = intentionally not applicable

---

## Statements

| Feature               | Syntax                                                                          | Semantics                                                           | KlangScript Test                |
|-----------------------|---------------------------------------------------------------------------------|---------------------------------------------------------------------|---------------------------------|
| `ExpressionStatement` | `expr`                                                                          | evaluates expression for side effects                               | `KlangScriptIntegrationTest.kt` |
| `LetDeclaration`      | `let x = expr` / `let x`                                                        | mutable, block-scoped; uninitialized → `null`                       | `VariableTest.kt`               |
| `ConstDeclaration`    | `const x = expr`                                                                | immutable, block-scoped; must have initializer                      | `VariableTest.kt`               |
| `ImportStatement`     | `import * from "lib"` / `import * as ns from "lib"` / `import { a } from "lib"` | loads & evaluates library; binds exports into scope                 | `ImportSystemTest.kt`           |
| `ExportStatement`     | `export { a, b as c }`                                                          | marks symbols as visible to importers; library files only           | `ExportImportTest.kt`           |
| `ExportDeclaration`   | `export name = expr`                                                            | combined immutable binding + auto-export under same name; top-level | `ExportDeclarationTest.kt`      |
| `ReturnStatement`     | `return` / `return expr`                                                        | exits current function; only valid in arrow function block body     | `ArrowFunctionBlockBodyTest.kt` |
| `WhileStatement`      | `while (cond) { ... }`                                                          | loop; supports `break`/`continue`; `ReturnException` propagates     | `ControlFlowTest.kt`            |
| `DoWhileStatement`    | `do { ... } while (cond)`                                                       | executes body at least once; supports `break`/`continue`            | `ControlFlowTest.kt`            |
| `ForStatement`        | `for (init; cond; update) { ... }`                                              | C-style loop; init scoped to loop; supports `break`/`continue`      | `ControlFlowTest.kt`            |
| `BreakStatement`      | `break`                                                                         | exits enclosing loop; throws `BreakException`                       | `ControlFlowTest.kt`            |
| `ContinueStatement`   | `continue`                                                                      | skips to next loop iteration; throws `ContinueException`            | `ControlFlowTest.kt`            |

---

## Expressions

| Feature                | Syntax                                                                         | Semantics                                                                 | KlangScript Test                                                                                |
|------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `NumberLiteral`        | `42`, `3.14`                                                                   | stored as `Double`; integer rendered without decimal                      | `LiteralsTest.kt`                                                                               |
| `StringLiteral`        | `"…"` / `'…'` / `` `…` ``                                                      | escape sequences processed; backtick = multiline                          | `LiteralsTest.kt`                                                                               |
| `BooleanLiteral`       | `true` / `false`                                                               |                                                                           | `LiteralsTest.kt`                                                                               |
| `NullLiteral`          | `null`                                                                         | no `undefined` in KlangScript                                             | `LiteralsTest.kt`                                                                               |
| `Identifier`           | `x`, `myVar`                                                                   | looks up name in lexical environment                                      | `VariableTest.kt`                                                                               |
| `CallExpression`       | `func(a, b)`                                                                   | evaluates callee, then each arg left-to-right                             | `KlangScriptIntegrationTest.kt`                                                                 |
| `MemberAccess`         | `obj.prop`                                                                     | property lookup; foundation of method chains                              | `MemberAccessTest.kt`                                                                           |
| `BinaryOperation`      | `a + b`, `a == b`, `a && b`, `a ** b`, `a === b`, `a !== b`, `a in b` (17 ops) | standard arithmetic/comparison/logical/power; `&&`/`\|\|` short-circuit   | `ArithmeticTest.kt`, `ComparisonOperatorsTest.kt`, `BooleanLogicTest.kt`, `EasyFeaturesTest.kt` |
| `UnaryOperation`       | `-x`, `!x`, `+x`, `++x`, `--x`, `x++`, `x--`                                   | negate/not/identity; prefix/postfix increment/decrement                   | `UnaryOperatorsTest.kt`, `EasyFeaturesTest.kt`                                                  |
| `TernaryExpression`    | `cond ? a : b`                                                                 | evaluates `cond`; returns `a` if truthy, `b` if falsy                     | `EasyFeaturesTest.kt`                                                                           |
| `IndexAccess`          | `arr[i]`, `obj["key"]`                                                         | numeric or string index into array/object                                 | `EasyFeaturesTest.kt`                                                                           |
| `AssignmentExpression` | `x = expr`, `obj.prop = expr`, `arr[i] = expr`                                 | assigns value to variable, property, or index; desugars compound `+=` etc | `EasyFeaturesTest.kt`                                                                           |
| `ArrowFunction`        | `x => expr` / `(a, b) => expr` / `() => { … }`                                 | closures; expression body = implicit return; block body requires `return` | `ArrowFunctionTest.kt`, `ArrowFunctionBlockBodyTest.kt`                                         |
| `CallExpression` on a native object | `Master(...)`, `Master(m => …)`                                                | dispatches to the object type's `invoke` extension method through the spec-aware call path; type error naming `invoke` when absent | `NativeObjectInvokeTest.kt`, `InvokeAnalysisTest.kt`                          |
| `ArrowFunction` as native arg | `native(x => …)`, `native(1, x => …)`, `native(configure = x => …)`     | converted to a Kotlin lambda; a sole trailing lambda floats to the single trailing function-typed param (`ArgAlignment`); analyzer types its params from the callee | `ArgAlignmentTest.kt`, `ConfigureLambdaBindingTest.kt`, `AnalyzedAstTest.kt`                 |
| `IfExpression`         | `if (cond) { … } else { … }`                                                   | expression-based; value = last expr in executed branch (or null)          | `ControlFlowTest.kt`                                                                            |
| `TemplateLiteral`      | `` `Hello ${name}!` ``                                                         | backtick string with `${expr}` interpolation; parts concat to string      | `TemplateLiteralTest.kt`                                                                        |
| `ObjectLiteral`        | `{ k: v, … }`, `{ name }` (shorthand)                                          | creates object; shorthand desugars to `name: name` at parse time          | `ObjectLiteralTest.kt`                                                                          |
| `ArrayLiteral`         | `[1, 2, 3]`                                                                    | creates array; any expression as element                                  | `ArrayLiteralTest.kt`                                                                           |

---
