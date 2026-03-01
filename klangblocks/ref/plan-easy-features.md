# KlangBlocks — Plan: Easy Feature Representation

This document plans how each newly implemented KlangScript "easy" feature maps into the
KlangBlocks model (`KBArgValue`, `KBStmt`), code generation, and round-trip tests.

**Do not implement anything from this file without reading it in full first.**

---

## Current Gap: `AstToKBlocks.kt` does not compile

After adding the new AST nodes (`AssignmentExpression`, `TernaryExpression`, `IndexAccess`) and
new enum values (`POWER`, `STRICT_EQUAL`, `STRICT_NOT_EQUAL`, `IN`, `PREFIX_INCREMENT`, etc.),
the exhaustive `when` expressions in `AstToKBlocks.kt` will fail to compile. All items below
fix this.

---

## 1. New Binary Operators (`**`, `===`, `!==`, `in`)

**KlangScript AST:** `BinaryOperation` (already exists)
**KlangBlocks type:** `KBBinaryArg` (already exists)

No new model type needed. Only `BinaryOperator.toSymbol()` in `AstToKBlocks.kt` must be
extended:

```kotlin
BinaryOperator.POWER             -> "**"
BinaryOperator.STRICT_EQUAL      -> "==="
BinaryOperator.STRICT_NOT_EQUAL  -> "!=="
BinaryOperator.IN                -> "in"
```

Code gen `"${left.toCode()} $op ${right.toCode()}"` already handles these correctly.

**Round-trip test:** `let x = a ** b`, `let y = a === b`, `let z = a !== b`,
`let w = "key" in obj`

---

## 2. Prefix `++` / `--` (`++x`, `--x`)

**KlangScript AST:** `UnaryOperation` with `PREFIX_INCREMENT` / `PREFIX_DECREMENT`
**KlangBlocks type:** `KBUnaryArg` (already exists)

Extend `UnaryOperator.toSymbol()`:

```kotlin
UnaryOperator.PREFIX_INCREMENT -> "++"
UnaryOperator.PREFIX_DECREMENT -> "--"
```

Current code gen `"$op${operand.toCode()}"` already emits the correct prefix form (`++x`).

**Round-trip test:** `let x = ++y`, `let z = --w` (as initialisers)

---

## 3. Postfix `++` / `--` (`x++`, `x--`)

**KlangScript AST:** `UnaryOperation` with `POSTFIX_INCREMENT` / `POSTFIX_DECREMENT`
**Problem:** Current `KBUnaryArg.toCode()` always emits `op + operand` (prefix order).

### Model change — extend `KBUnaryArg`

Add a `position` field with a new enum:

```kotlin
enum class KBUnaryPosition { PREFIX, POSTFIX }

data class KBUnaryArg(
    val op: String,
    val operand: KBArgValue,
    val position: KBUnaryPosition = KBUnaryPosition.PREFIX,  // default keeps existing behaviour
) : KBArgValue()
```

### Code gen change

In `KBArgValue.toCode()`:

```kotlin
is KBUnaryArg -> when (position) {
    KBUnaryPosition.PREFIX  -> "$op${operand.toCode()}"
    KBUnaryPosition.POSTFIX -> "${operand.toCode()}$op"
}
```

### AstToKBlocks change

Extend `UnaryOperator.toSymbol()`:

```kotlin
UnaryOperator.POSTFIX_INCREMENT -> "++"
UnaryOperator.POSTFIX_DECREMENT -> "--"
```

In `convertExpr()` for `UnaryOperation`, set `position` based on the operator:

```kotlin
is UnaryOperation -> KBUnaryArg(
op = expr.operator.toSymbol(),
operand = convertExpr(expr.operand),
position = when (expr.operator) {
    UnaryOperator.POSTFIX_INCREMENT, UnaryOperator.POSTFIX_DECREMENT ->
        KBUnaryPosition.POSTFIX
    else -> KBUnaryPosition.PREFIX
},
)
```

**Round-trip test:** `x++`, `y--` (as top-level expression statements)

---

## 4. Ternary Expression (`cond ? thenExpr : elseExpr`)

**KlangScript AST:** `TernaryExpression` (new node)
**KlangBlocks type:** New `KBTernaryArg`

### Model change — add `KBTernaryArg` to `KBArgValue.kt`

```kotlin
/**
 * A ternary conditional expression used as an argument, e.g. `x > 0 ? "pos" : "neg"`.
 * All three sub-expressions are themselves [KBArgValue]s and are individually editable.
 */
data class KBTernaryArg(
    val condition: KBArgValue,
    val thenExpr: KBArgValue,
    val elseExpr: KBArgValue,
) : KBArgValue()
```

### Code gen change

In `KBArgValue.toCode()`:

```kotlin
is KBTernaryArg -> "${condition.toCode()} ? ${thenExpr.toCode()} : ${elseExpr.toCode()}"
```

### AstToKBlocks change

In `convertExpr()`:

```kotlin
is TernaryExpression -> KBTernaryArg(
condition = convertExpr(expr.condition),
thenExpr = convertExpr(expr.thenExpr),
elseExpr = convertExpr(expr.elseExpr),
)
```

In `Expression.toSourceString()`:

```kotlin
is TernaryExpression ->
"${condition.toSourceString()} ? ${thenExpr.toSourceString()} : ${elseExpr.toSourceString()}"
```

**Round-trip test:** `let x = cond ? 1 : 0`, `let y = a > b ? a : b`

---

## 5. Index Access (`arr[i]`, `obj["key"]`)

**KlangScript AST:** `IndexAccess` (new node)
**KlangBlocks type:** New `KBIndexAccessArg`

### Model change — add `KBIndexAccessArg` to `KBArgValue.kt`

```kotlin
/**
 * An index/bracket access expression used as an argument, e.g. `arr[0]` or `obj["key"]`.
 * Both the object and the index are themselves [KBArgValue]s.
 */
data class KBIndexAccessArg(
    val obj: KBArgValue,
    val index: KBArgValue,
) : KBArgValue()
```

### Code gen change

In `KBArgValue.toCode()`:

```kotlin
is KBIndexAccessArg -> "${obj.toCode()}[${index.toCode()}]"
```

### AstToKBlocks change

In `convertExpr()`:

```kotlin
is IndexAccess -> KBIndexAccessArg(
obj = convertExpr(expr.obj),
index = convertExpr(expr.index),
)
```

In `Expression.toSourceString()`:

```kotlin
is IndexAccess -> "${obj.toSourceString()}[${index.toSourceString()}]"
```

**Chain context:** If `IndexAccess` is the receiver of a method chain (`arr[0].note()`),
`extractChain()` currently returns `null` (not a `CallExpression`/`MemberAccess` head).
This falls back to `KBStringArg(toSourceString())` for the whole chain — acceptable for now.

**Round-trip test:** `let x = arr[0]`, `let y = obj["key"]`

---

## 6. Assignment Expression (`x = expr`)

**KlangScript AST:** `AssignmentExpression` (new node)

This is the most complex case. `AssignmentExpression` can appear:

- **As a top-level statement** (`ExpressionStatement(AssignmentExpression(...))`): the common
  case in live coding — e.g. `x = x + 1`
- **As an expression** (inside another expression): rare; fall back to `KBStringArg`

### Model change — add `KBAssignStmt` to `KBBlock.kt`

```kotlin
/**
 * A variable (re-)assignment statement, e.g. `x = 5` or `x = x + 1`.
 *
 * [target] is the raw source string of the left-hand side (e.g. `"x"`, `"arr[0]"`).
 * [value] is the structured right-hand side.
 */
data class KBAssignStmt(
    override val id: String,
    val target: String,     // raw LHS source: "x", "arr[0]", "obj.prop"
    val value: KBArgValue,
) : KBStmt()
```

**Why `target: String`?** Assignment targets can be identifiers, index expressions
(`arr[i]`), or member accesses (`obj.prop`). Storing the target as a raw string string
keeps the model simple and handles all cases without a recursive KBArgValue subtype for
targets. The target is emitted verbatim in code gen — no quoting.

### Code gen change

In `KBStmt.appendTo()`:

```kotlin
is KBAssignStmt -> builder.append("$target = ").append(value.toCode())
```

### AstToKBlocks change — statement level

In `convertStmt()`, intercept `AssignmentExpression` before calling `convertExprStmt()`:

```kotlin
is ExpressionStatement -> when (val inner = stmt.expression) {
    is AssignmentExpression -> KBAssignStmt(
    id = uuid(),
    target = inner.target.toSourceString(),   // works for Identifier, IndexAccess, MemberAccess
    value = convertExpr(inner.value),
    )
    else -> convertExprStmt(stmt.expression)
}
```

### AstToKBlocks change — expression level (fallback)

In `convertExpr()`, `AssignmentExpression` appearing as an argument falls back to raw source:

```kotlin
is AssignmentExpression -> KBStringArg(expr.toSourceString())
```

### `Expression.toSourceString()` change

```kotlin
is AssignmentExpression ->
"${target.toSourceString()} = ${value.toSourceString()}"
```

**Round-trip test:** `x = 5`, `x = x + 1` (note: `x += 1` desugars to `x = x + 1` at parse
time, so the round-trip produces `x = x + 1` — not `x += 1`. AST-level equality still holds.)

---

## 7. Object Property Shorthand (`{ name }`)

**KlangScript AST:** Desugared at parse time to `ObjectLiteral` with `name: name` property.
**KlangBlocks:** No change needed — `ObjectLiteral` already falls back to
`KBStringArg(toSourceString())`.

---

## 8. Compound Assignment (`x += expr`, `x -= expr`, ...)

**KlangScript AST:** Desugared at parse time to `AssignmentExpression(target, BinaryOp(...))`.
**KlangBlocks:** Handled automatically via `KBAssignStmt` (see item 6 above).

The generated code will emit `x = x + expr` (not `x += expr`). AST-level round-trip holds.

---

## Files to Modify

| File                    | Change                                                             |
|-------------------------|--------------------------------------------------------------------|
| `model/KBArgValue.kt`   | Add `KBTernaryArg`, `KBIndexAccessArg`; extend `KBUnaryArg`        |
| `model/KBBlock.kt`      | Add `KBAssignStmt`                                                 |
| `model/AstToKBlocks.kt` | Add operators to `toSymbol()`; add `convertExpr` cases; add        |
|                         | `AssignmentExpression` stmt detection; add `toSourceString` cases  |
| `model/KBCodeGen.kt`    | Add `KBTernaryArg`, `KBIndexAccessArg`, `KBAssignStmt` to code gen |
|                         | Fix `KBUnaryArg` for POSTFIX position                              |

---

## Round-Trip Tests to Write

New file: `jvmTest/kotlin/model/EasyFeaturesRoundTripTest.kt`

| Feature                  | Test source                          |
|--------------------------|--------------------------------------|
| Power (`**`)             | `let x = a ** b`                     |
| Strict equal (`===`)     | `let x = a === b`                    |
| Strict not-equal (`!==`) | `let x = a !== b`                    |
| `in` operator            | `let x = "k" in obj`                 |
| Prefix `++`              | `let x = ++y`                        |
| Prefix `--`              | `let x = --y`                        |
| Postfix `++`             | `x++`                                |
| Postfix `--`             | `x--`                                |
| Ternary                  | `let x = cond ? 1 : 0`               |
| Index access             | `let x = arr[0]`                     |
| Simple assignment        | `x = 5`                              |
| Compound assignment      | `x = x + 1` ← desugared form of `+=` |

---

## Feature Catalog Updates

After implementation, update `klangscript/ref/feature-catalog.md`:

| Feature                | KlangBlocks                                              |
|------------------------|----------------------------------------------------------|
| `BinaryOperation`      | ✅ `KBBinaryArg` (now includes `**`, `===`, `!==`, `in`)  |
| `UnaryOperation`       | ✅ `KBUnaryArg` (now includes `++`/`--` prefix+postfix)   |
| `TernaryExpression`    | ✅ `KBTernaryArg`                                         |
| `IndexAccess`          | ✅ `KBIndexAccessArg` (chain-head fallback to raw string) |
| `AssignmentExpression` | ✅ `KBAssignStmt` (stmt) / `KBStringArg` fallback (expr)  |
