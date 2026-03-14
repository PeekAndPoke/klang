# REQUEST:

Hi! Can you please check if in the KlangScriptParser we already support arithmetic like

t % 16 > 4

If so then we have a bug in arrow function and maybe even function bodies in general, because this currently does not
parse:

obj.fn(t => t % 16 > 4)

Please verify this. If you find the cause and a way to fix it, then please write a detailed implementation plan, that i
will hand over to a coding agent. The plan must be detailed and include instructions for writing tests.

# ANSWER:

Hello! You are absolutely right. I've verified the `KlangScriptParser` and `Ast`, and we currently **do not support**
the modulo operator (`%`).

The `BinaryOperator` enum is missing a `MODULO` type, and the parser's `multiplicationExpr` only accepts `*` and `/`.
This is indeed why `t % 16 > 4` fails to parse, which in turn causes the arrow function `obj.fn(t => t % 16 > 4)` to
fail.

Here is the detailed implementation plan to fix this "bug" (missing feature) and add full support for arithmetic modulo.

### Implementation Plan

#### 1. Update AST Definition

We need to add the modulo operator to the `BinaryOperator` enum in `Ast.kt`.

* **File:** `klangscript/src/commonMain/kotlin/ast/Ast.kt`
* **Action:** Add `MODULO` to `BinaryOperator`.

```kotlin
enum class BinaryOperator {
    // ... existing operators ...
    DIVIDE,

    /** Modulo: a % b */
    MODULO,

    // ... existing operators ...
}
```

#### 2. Update Parser

We need to recognize the `%` token and include it in the grammar. The modulo operator typically shares the same
precedence as multiplication and division.

* **File:** `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt`
* **Action:**
    1. Add a `modulo` token for `%`.
    2. Update `multiplicationExpr` to accept `modulo`.

```kotlin
// ... inside KlangScriptParser object ...

/** Arithmetic operators */
// ... existing operators ...
private val divide by literalToken("/")
private val modulo by literalToken("%") // Add this line
// ... existing operators ...

// ...

/**
 * Multiplication, division, and modulo (higher precedence)
 * Left-associative: 6 / 2 / 3 = (6 / 2) / 3
 */
private val multiplicationExpr: Parser<Expression> by
leftAssociative(callExpr, times or divide or modulo) { left, op, right -> // Add `or modulo`
    val operator = when (op.text) {
        "*" -> BinaryOperator.MULTIPLY
        "/" -> BinaryOperator.DIVIDE
        "%" -> BinaryOperator.MODULO // Handle the token
        else -> error("Unexpected operator: $op")
    }
    BinaryOperation(left, operator, right, op.toLocation())
}
```

#### 3. Update Interpreter

We need to implement the runtime behavior for the new `BinaryOperator.MODULO`.

* **File:** `klangscript/src/commonMain/kotlin/runtime/Interpreter.kt`
* **Action:** Handle `BinaryOperator.MODULO` in the `evaluateBinaryOp` method.

```kotlin
// ... inside evaluateBinaryOp method ...
val result = when (binOp.operator) {
    // ... existing cases ...
    BinaryOperator.MULTIPLY -> leftValue.value * rightValue.value
    BinaryOperator.DIVIDE -> { /* ... existing division logic ... */
    }
    BinaryOperator.MODULO -> {
        // Optional: You might want to check for modulo by zero, 
        // though Kotlin/JS double behavior (NaN) is usually fine.
        leftValue.value % rightValue.value
    }
    else -> error("Unexpected arithmetic operator: ${binOp.operator}")
}
return NumberValue(result)
```

#### 4. Add Tests

We must verify the fix with unit tests covering simple arithmetic, precedence, and the arrow function case.

* **File:** `klangscript/src/commonTest/kotlin/ArithmeticTest.kt`
* **Action:** Add tests for basic modulo and precedence.

```kotlin
"should evaluate simple modulo" {
    val engine = klangScript()
    val result = engine.execute("10 % 3")
    (result as NumberValue).value shouldBe 1.0
}

"should respect operator precedence - modulo before addition" {
    val engine = klangScript()
    // 1 + (10 % 3) = 1 + 1 = 2
    val result = engine.execute("1 + 10 % 3")
    (result as NumberValue).value shouldBe 2.0
}

"should respect left associativity with multiplication" {
    val engine = klangScript()
    // (10 % 3) * 4 = 1 * 4 = 4
    val result = engine.execute("10 % 3 * 4")
    (result as NumberValue).value shouldBe 4.0
}
```

* **File:** `klangscript/src/commonTest/kotlin/ArrowFunctionTest.kt`
* **Action:** Add the specific case that was failing.

```kotlin
"should handle modulo in arrow function body" {
    val script = klangScript()
    // (t => t % 16)(18) should be 2
    val result = script.execute("(t => t % 16)(18)")
    result.shouldBeInstanceOf<NumberValue>()
    result.value shouldBe 2.0
}

"should handle modulo comparison in arrow function" {
    val script = klangScript()
    // (t => t % 16 > 4)(20) -> 20 % 16 is 4. 4 > 4 is false.
    // (t => t % 16 > 4)(21) -> 21 % 16 is 5. 5 > 4 is true.

    val resultFalse = script.execute("(t => t % 16 > 4)(20)")
    (resultFalse as BooleanValue).value shouldBe false

    val resultTrue = script.execute("(t => t % 16 > 4)(21)")
    (resultTrue as BooleanValue).value shouldBe true
}
```
