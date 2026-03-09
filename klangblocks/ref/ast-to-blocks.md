# KlangBlocks — AST → Blocks Conversion

## Entry Point

```kotlin
AstToKBlocks.convert(program: Program): KBProgram
```

Converts a KlangScript `Program` AST into a `KBProgram`.

## Statement Mapping

| AST node                              | KBStmt produced                                                          |
|---------------------------------------|--------------------------------------------------------------------------|
| `ImportStatement`                     | `KBImportStmt`                                                           |
| `LetDeclaration`                      | `KBLetStmt`                                                              |
| `ConstDeclaration`                    | `KBConstStmt`                                                            |
| `ExpressionStatement(AssignmentExpr)` | `KBAssignStmt` (target stored as raw source string; value is structured) |
| `ExpressionStatement(call chain)`     | `KBChainStmt` (via `extractChain`)                                       |
| `ExpressionStatement(other expr)`     | `KBExprStmt` (fallback — preserves non-chain stmts like `x++`)           |
| `ReturnStatement` / `ExportStatement` | `null` (skipped)                                                         |

**Blank line detection**: if the gap between consecutive statements is > 1 line, a `KBBlankLine` is inserted before the
second statement.

## Chain Extraction (`extractChain`)

Recursively unwraps left-recursive call chains:

```
sound("bd").gain(0.5)          → ChainResult(links=[sound, gain])
"C4".transpose(1).slow(2)      → ChainResult(stringHead="C4", links=[transpose, slow])
sine.range(0.25, 0.75)         → ChainResult(identHead="sine", links=[range])
```

Rules:

- `CallExpression` with `Identifier` callee → simple head call
- `CallExpression` with `MemberAccess` callee → chain link; recurse into `MemberAccess.obj`
- `StringLiteral` as direct receiver → `stringHead`
- `Identifier` as direct receiver → `identHead`
- Anything else → `null` (falls back to `KBStringArg(expr.toSourceString())`)

## Expression → KBArgValue Mapping

| AST expression                            | KBArgValue                                                                                                                   |
|-------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `StringLiteral`                           | `KBStringArg`                                                                                                                |
| `NumberLiteral`                           | `KBNumberArg`                                                                                                                |
| `BooleanLiteral`                          | `KBBoolArg`                                                                                                                  |
| `NullLiteral`                             | `KBIdentifierArg("null")`                                                                                                    |
| `Identifier`                              | `KBIdentifierArg`                                                                                                            |
| `BinaryOperation`                         | `KBBinaryArg` (op string: `+`, `-`, `*`, `/`, `%`, `**`, `==`, `!=`, `===`, `!==`, `<`, `<=`, `>`, `>=`, `&&`, `\|\|`, `in`) |
| `UnaryOperation`                          | `KBUnaryArg` (position `PREFIX` or `POSTFIX`; op: `-`, `+`, `!`, `++`, `--`)                                                 |
| `TernaryExpression`                       | `KBTernaryArg`                                                                                                               |
| `IndexAccess`                             | `KBIndexAccessArg`                                                                                                           |
| `AssignmentExpression`                    | `KBStringArg(toSourceString())` (fallback when used as expression, not statement)                                            |
| `ArrowFunction`                           | `KBArrowFunctionArg` (body as raw source)                                                                                    |
| `CallExpression` / `MemberAccess` (chain) | `KBNestedChainArg`                                                                                                           |
| `ObjectLiteral` / `ArrayLiteral`          | `KBStringArg(toSourceString())` (fallback)                                                                                   |

## Layout Detection

`pocketLayout` for each `KBCallBlock` is set to:

- `HORIZONTAL` — all arg start-lines equal the call's start-line
- `VERTICAL` — any arg is on a different line than the call

## IDs

Every `KBCallBlock`, `KBChainStmt`, `KBImportStmt`, `KBLetStmt`, `KBConstStmt`, `KBBlankLine`
gets a fresh random hex ID via `uuid()` on every `convert()` call.
IDs are **not** preserved across conversions — they are editor-session identifiers only.
