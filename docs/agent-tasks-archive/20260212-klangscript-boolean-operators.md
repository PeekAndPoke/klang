It appears that `KlangScriptParser` **does not yet support** boolean logic operators (`&&` and `||`). The
`BinaryOperator` enum in `Ast.kt` currently lacks `AND` and `OR`, and the parser grammar handles comparison (`<`, `>`,
`==`) but not logical conjunctions.

I have updated the necessary files to support boolean logic, including support within arrow functions as you requested (
e.g., `x => x >= 5 && x < 10`).

Here are the edits:

1. **`Ast.kt`**: Added `AND` and `OR` to the `BinaryOperator` enum.
2. **`KlangScriptParser.kt`**: Added `&&` and `||` tokens and grammar rules, placing them correctly in the precedence
   chain (lower than comparisons).
3. **`Interpreter.kt`**: Implemented `AND` and `OR` logic with short-circuit evaluation.

### 1. Update `Ast.kt` to add the operators

```kotlin
// ... existing code ...
enum class BinaryOperator {
    /** Addition: a + b */
    ADD,

    /** Subtraction: a - b */
    SUBTRACT,

    /** Multiplication: a * b */
    MULTIPLY,

    /** Division: a / b */
    DIVIDE,

    /** Modulo: a % b */
    MODULO,

    /** Equality: a == b */
    EQUAL,

    /** Inequality: a != b */
    NOT_EQUAL,

    /** Less than: a < b */
    LESS_THAN,

    /** Less than or equal: a <= b */
    LESS_THAN_OR_EQUAL,

    /** Greater than: a > b */
    GREATER_THAN,

    /** Greater than or equal: a >= b */
    GREATER_THAN_OR_EQUAL,

    /** Logical AND: a && b */
    AND,

    /** Logical OR: a || b */
    OR,
}

/**
 * A binary operation expression
// ... existing code ...
```

### 2. Update `KlangScriptParser.kt` to parse `&&` and `||`

```kotlin
// ... existing code ...
/** Arrow function operator - must be before = to match => before = */
private val arrow by literalToken("=>")

/** Logical operators */
private val and by literalToken("&&")
private val or by literalToken("||")

/** Comparison operators - must be defined before single-char operators to match correctly */
private val doubleEquals by literalToken("==")
private val notEquals by literalToken("!=")
// ... existing code ...
private val comparisonExpr: Parser<Expression> by
leftAssociative(
    additionExpr,
    doubleEquals or notEquals or lessThanOrEqual or greaterThanOrEqual or lessThan or greaterThan
) { left, op, right ->
    val operator = when (op.text) {
        "==" -> BinaryOperator.EQUAL
        "!=" -> BinaryOperator.NOT_EQUAL
        "<" -> BinaryOperator.LESS_THAN
        "<=" -> BinaryOperator.LESS_THAN_OR_EQUAL
        ">" -> BinaryOperator.GREATER_THAN
        ">=" -> BinaryOperator.GREATER_THAN_OR_EQUAL
        else -> error("Unexpected operator: $op")
    }
    BinaryOperation(left, operator, right, op.toLocation())
}

/**
 * Logical AND expression (lower precedence than comparison)
 * Syntax: expr && expr
 */
private val logicalAndExpr: Parser<Expression> by
leftAssociative(comparisonExpr, and) { left, op, right ->
    BinaryOperation(left, BinaryOperator.AND, right, op.toLocation())
}

/**
 * Logical OR expression (lower precedence than AND)
 * Syntax: expr || expr
 */
private val logicalOrExpr: Parser<Expression> by
leftAssociative(logicalAndExpr, or) { left, op, right ->
    BinaryOperation(left, BinaryOperator.OR, right, op.toLocation())
}

/**
 * Arrow function body - either expression or block
 * Syntax:
// ... existing code ...
private val arrowFunctionBody: Parser<ArrowFunctionBody> by
// Try block body first (with braces)
(leftBrace and zeroOrMore(parser(this::statement)) and -rightBrace).map { (_, statements) ->
ArrowFunctionBody.BlockBody(statements)
} or
// Otherwise, parse expression body (right-associative to allow nested arrows)
parser(this::arrowExpr).map { expr ->
ArrowFunctionBody.ExpressionBody(expr)
}

/**
 * Arrow function expressions (lowest precedence)
 * Syntax:
 * - Single parameter: `x => expr`
// ... existing code ...
params.map { it.text }
}
) and arrow and arrowFunctionBody
).map { (params, arrowToken, body) ->
ArrowFunction(params, body, arrowToken.toLocation())
} or logicalOrExpr  // Fall back to logical OR expression (which chains up to others)

/**
 * Let declaration statement
 * Syntax: let x = expr OR let x
// ... existing code ...
```

### 3. Update `Interpreter.kt` to evaluate the logic

I've also added a helper `toBoolean` function to handle truthiness consistently for both `!` (NOT) and the new `&&`/`||`
operators.

```kotlin
// ... existing code ...
UnaryOperator.PLUS -> {
    if (operandValue !is NumberValue) {
        throw TypeError(
            "Unary plus operator requires a number, got ${operandValue.toDisplayString()}",
            operation = "unary +",
            location = unaryOp.location,
            stackTrace = getStackTrace()
        )
    }
    NumberValue(operandValue.value)
}

UnaryOperator.NOT -> {
    // Convert value to boolean using JavaScript-like truthiness rules
    val boolValue = toBoolean(operandValue)
    BooleanValue(!boolValue)
}
}
}

private fun toBoolean(value: RuntimeValue): Boolean {
return when (value) {
is BooleanValue -> value.value
is NullValue -> false
is NumberValue -> value.value != 0.0 && !value.value.isNaN()
is StringValue -> value.value.isNotEmpty()
else -> true // Objects and functions are truthy
}
}

/**
 * Evaluate a binary operation expression
 *
 * Binary operations perform arithmetic and comparison operations.
 *
 * Process:
 * 1. Evaluate the left operand
 * 2. Evaluate the right operand
 * 3. Apply the operator based on type
 * 4. Return the result
 *
 * Supported operators:
 * - Arithmetic (+, -, *, /): Require number operands, return NumberValue
 * - Comparison (<, <=, >, >=): Require number operands, return BooleanValue
 * - Equality (==, !=): Work on all types, return BooleanValue
 *
 * @param binOp The binary operation AST node
 * @return NumberValue for arithmetic, BooleanValue for comparisons
 * @throws TypeError if operands are invalid for the operation
 *
 * Examples:
 * - 5 + 3 → NumberValue(8.0)
 * - 10 - 4 → NumberValue(6.0)
 * - 3 * 7 → NumberValue(21.0)
 * - 20 / 4 → NumberValue(5.0)
 * - 10 % 3 → NumberValue(1.0)
 * - 5 > 3 → BooleanValue(true)
 * - 10 == 10 → BooleanValue(true)
 * - "a" == "a" → BooleanValue(true)
 * - 1 / 0 → TypeError: "Division by zero"
 * - 1 % 0 → TypeError: "Modulo by zero"
 */
private fun evaluateBinaryOp(binOp: BinaryOperation): RuntimeValue {
// Handle logical operators with short-circuit evaluation
if (binOp.operator == BinaryOperator.AND) {
val leftValue = evaluate(binOp.left)
return if (!toBoolean(leftValue)) {
BooleanValue(false)
} else {
BooleanValue(toBoolean(evaluate(binOp.right)))
}
}
if (binOp.operator == BinaryOperator.OR) {
val leftValue = evaluate(binOp.left)
return if (toBoolean(leftValue)) {
BooleanValue(true)
} else {
BooleanValue(toBoolean(evaluate(binOp.right)))
}
}

// Evaluate both operands
val leftValue = evaluate(binOp.left)
val rightValue = evaluate(binOp.right)

// Handle comparison operators
when (binOp.operator) {
BinaryOperator.EQUAL -> {
return BooleanValue(valuesEqual(leftValue, rightValue))
}
// ... existing code ...
```
