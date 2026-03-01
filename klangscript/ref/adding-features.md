# KlangScript — Adding New Language Features

## Checklist for Every New Feature

1. **AST** — add sealed subclass to `ast/Ast.kt`
2. **Parser** — add token(s) and parse rule in `parser/KlangScriptParser.kt`
3. **Interpreter** — add `when` branch in `runtime/Interpreter.kt`
4. **Tests** — add test file in `src/commonTest/kotlin/`
5. **Run tests** — `./gradlew :klangscript:jvmTest`

## Example: Adding Array Indexing (`arr[0]`)

### 1. AST (`ast/Ast.kt`)

```kotlin
data class IndexAccess(
    val array: Expression,
    val index: Expression,
    val location: SourceLocation? = null
) : Expression
```

### 2. Parser — add `LBRACKET`/`RBRACKET` tokens, extend postfix loop

```kotlin
// In postfixExpr loop:
check(LBRACKET) -> {
    consume(LBRACKET)
    val index = parseExpression()
    consume(RBRACKET)
    IndexAccess(expr, index, location)
}
```

### 3. Interpreter — add to `evaluateExpression()`

```kotlin
is IndexAccess -> {
    val arr = evaluate(node.array) as? ArrayValue
        ?: throw TypeError("not an array", node.location)
    val idx = (evaluate(node.index) as? NumberValue)?.value?.toInt()
        ?: throw TypeError("index must be a number", node.location)
    arr.elements.getOrNull(idx) ?: NullValue
}
```

### 4. Tests (`src/commonTest/kotlin/IndexAccessTest.kt`)

```kotlin
class IndexAccessTest : StringSpec({
    "should access array by index" {
        val result = engine.execute("[10, 20, 30][1]")
        result shouldBe NumberValue(20.0)
    }
})
```

## Example: Adding `if/else`

### 1. AST

```kotlin
data class IfStatement(
    val condition: Expression,
    val thenBranch: List<Statement>,
    val elseBranch: List<Statement>?,
    val location: SourceLocation? = null
) : Statement
```

### 2. Parser — add `if`/`else` keywords, parse condition + braces

### 3. Interpreter

```kotlin
is IfStatement -> {
    val cond = evaluate(node.condition)
    if (cond.isTruthy()) node.thenBranch.forEach { executeStatement(it) }
    else node.elseBranch?.forEach { executeStatement(it) }
}
```

## Rules & Gotchas

- **Token ordering matters** — always define multi-char tokens before their single-char prefixes
- **`ast/Ast.kt` changes cascade** — check every `when (node)` in the interpreter and any exhaustive `when` expressions
- **Block body vs expression disambiguation** — if parsing `{` after `=>`, peek for `id:` to detect object literal
- **`ReturnException`** must only be caught at function call boundaries, not swallowed elsewhere
- **Test on both platforms** — run `jsTest` after `jvmTest` for any new feature touching the parser
