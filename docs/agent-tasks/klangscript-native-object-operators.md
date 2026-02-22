# Feature Implementation Plan: Operator Functions for Registered Native Objects

## Goal

Allow registering Kotlin-style operator functions on native objects in KlangScript. This enables
native objects to participate in KlangScript's existing operator syntax (`+`, `-`, `*`, `/`, `%`,
unary `-`/`+`/`!`, and direct call `obj(args)`).

Operators are stored as regular extension methods under canonical reserved names. The interpreter
checks for these names during operator and call dispatch. No new storage infrastructure is needed.

---

## Operator Name Convention

Follow Kotlin's operator function naming:

| KlangScript expression | Operator name | Notes                                    |
|------------------------|---------------|------------------------------------------|
| `obj(a, b)`            | `invoke`      | Makes native objects callable            |
| `obj + arg`            | `plus`        |                                          |
| `obj - arg`            | `minus`       |                                          |
| `obj * arg`            | `times`       |                                          |
| `obj / arg`            | `div`         |                                          |
| `obj % arg`            | `rem`         |                                          |
| `-obj`                 | `unaryMinus`  |                                          |
| `+obj`                 | `unaryPlus`   |                                          |
| `!obj`                 | `not`         |                                          |
| `obj == arg`           | `equals`      | Must return `Boolean`                    |
| `obj != arg`           | `equals`      | Same as `equals`, result is negated      |
| `obj < arg`            | `compareTo`   | Must return `Number`; negative = less    |
| `obj <= arg`           | `compareTo`   | Must return `Number`; non-positive = lte |
| `obj > arg`            | `compareTo`   | Must return `Number`; positive = greater |
| `obj >= arg`           | `compareTo`   | Must return `Number`; non-negative = gte |

Operators are looked up through the same `getExtensionMethod` path as regular methods, so
supertype-based inheritance works automatically.

---

## Files to Change

1. `klangscript/src/commonMain/kotlin/runtime/NativeInterop.kt` — add `NativeOperatorNames` object
2. `klangscript/src/commonMain/kotlin/builder/KlangScriptExtensionBuilder.kt` — add `register*Operator` helpers to
   `NativeObjectExtensionsBuilder<T>`
3. `klangscript/src/commonMain/kotlin/runtime/Interpreter.kt` — dispatch operators to native methods
4. `klangscript/src/commonTest/kotlin/NativeObjectOperatorsTest.kt` — new test file

---

## Step 1 — Add `NativeOperatorNames` to `NativeInterop.kt`

Add a simple constants object at the top of the file, alongside `NativeTypeInfo` and
`NativeExtensionMethod`:

```kotlin
/**
 * Canonical names for operator extension methods on native objects.
 *
 * Registering an extension method under one of these names makes the native
 * object participate in the corresponding KlangScript operator syntax.
 */
object NativeOperatorNames {
    // Callable object:  obj(a, b, ...)
    const val INVOKE = "invoke"

    // Binary arithmetic:  obj + arg,  obj - arg, ...
    const val PLUS = "plus"
    const val MINUS = "minus"
    const val TIMES = "times"
    const val DIV = "div"
    const val REM = "rem"

    // Unary:  -obj,  +obj,  !obj
    const val UNARY_MINUS = "unaryMinus"
    const val UNARY_PLUS = "unaryPlus"
    const val NOT = "not"

    // Equality / comparison:  obj == arg,  obj < arg, ...
    const val EQUALS = "equals"
    const val COMPARE_TO = "compareTo"

    /** Map from BinaryOperator to operator name (null for AND / OR which are handled elsewhere). */
    fun forBinaryOp(op: BinaryOperator): String? = when (op) {
        BinaryOperator.ADD -> PLUS
        BinaryOperator.SUBTRACT -> MINUS
        BinaryOperator.MULTIPLY -> TIMES
        BinaryOperator.DIVIDE -> DIV
        BinaryOperator.MODULO -> REM
        BinaryOperator.EQUAL -> EQUALS
        BinaryOperator.NOT_EQUAL -> EQUALS   // result is negated by interpreter
        BinaryOperator.LESS_THAN -> COMPARE_TO
        BinaryOperator.LESS_THAN_OR_EQUAL -> COMPARE_TO
        BinaryOperator.GREATER_THAN -> COMPARE_TO
        BinaryOperator.GREATER_THAN_OR_EQUAL -> COMPARE_TO
        else -> null  // AND, OR — short-circuited before this is reached
    }
}
```

---

## Step 2 — Add `register*Operator` helpers to `NativeObjectExtensionsBuilder<T>`

Append the following extension functions to the bottom of `KlangScriptExtensionBuilder.kt`. They
are thin wrappers that delegate to existing `registerMethod` / `registerVarargMethod` with the
canonical operator name.

```kotlin
// ===== Operator registration helpers for NativeObjectExtensionsBuilder =====

/**
 * Register an `invoke` operator, making the object callable as a function.
 *
 * Raw variant: receives the raw [RuntimeValue] argument list and source location.
 * Use this when you need full control over argument handling.
 *
 * Usage:
 * ```kotlin
 * registerType<MyObj> {
 *     registerInvokeOperator { args, _ -> wrapAsRuntimeValue(handle(args)) }
 * }
 * ```

* KlangScript: `myObj(1, 2, 3)`
  */
  fun NativeObjectExtensionsBuilder<T>.registerInvokeOperator(
  fn: T.(List<RuntimeValue>, SourceLocation?) -> RuntimeValue,
  ) {
  builder.registerExtensionMethod(cls, NativeOperatorNames.INVOKE) { receiver, args, location ->
  receiver.fn(args, location)
  }
  }

/**

* Register a typed `invoke` operator using vararg parameter conversion.
*
* All arguments must be convertible to [P].
*
* Usage:
* ```kotlin
* registerType<MyObj> {
*     registerInvokeOperator<Double, MyObj> { args -> applyArgs(args) }
* }
* ```

*/
inline fun <reified T : Any, reified P : Any, reified R : Any>
NativeObjectExtensionsBuilder<T>.registerInvokeOperator(noinline fn: T.(List<P>) -> R) {
registerVarargMethod(NativeOperatorNames.INVOKE, fn)
}

/**

* Register a `plus` operator for `obj + arg`.
*
* Usage:
* ```kotlin
* registerType<MyObj> {
*     registerPlusOperator<Double, MyObj> { other -> combine(other) }
* }
* ```

*/
inline fun <reified T : Any, reified P : Any, reified R : Any>
NativeObjectExtensionsBuilder<T>.registerPlusOperator(noinline fn: T.(P) -> R) {
registerMethod(NativeOperatorNames.PLUS, fn)
}

/** Register a `minus` operator for `obj - arg`. */
inline fun <reified T : Any, reified P : Any, reified R : Any>
NativeObjectExtensionsBuilder<T>.registerMinusOperator(noinline fn: T.(P) -> R) {
registerMethod(NativeOperatorNames.MINUS, fn)
}

/** Register a `times` operator for `obj * arg`. */
inline fun <reified T : Any, reified P : Any, reified R : Any>
NativeObjectExtensionsBuilder<T>.registerTimesOperator(noinline fn: T.(P) -> R) {
registerMethod(NativeOperatorNames.TIMES, fn)
}

/** Register a `div` operator for `obj / arg`. */
inline fun <reified T : Any, reified P : Any, reified R : Any>
NativeObjectExtensionsBuilder<T>.registerDivOperator(noinline fn: T.(P) -> R) {
registerMethod(NativeOperatorNames.DIV, fn)
}

/** Register a `rem` operator for `obj % arg`. */
inline fun <reified T : Any, reified P : Any, reified R : Any>
NativeObjectExtensionsBuilder<T>.registerRemOperator(noinline fn: T.(P) -> R) {
registerMethod(NativeOperatorNames.REM, fn)
}

/**

* Register an `equals` operator for `obj == arg` and `obj != arg`.
* The function must return `Boolean`.
  */
  inline fun <reified T : Any, reified P : Any>
  NativeObjectExtensionsBuilder<T>.registerEqualsOperator(noinline fn: T.(P) -> Boolean) {
  registerMethod(NativeOperatorNames.EQUALS, fn)
  }

/**

* Register a `compareTo` operator for `<`, `<=`, `>`, `>=`.
* The function must return `Int`:
*
    - negative → receiver is less than arg
*
    - zero → receiver equals arg
*
    - positive → receiver is greater than arg
      */
      inline fun <reified T : Any, reified P : Any>
      NativeObjectExtensionsBuilder<T>.registerCompareToOperator(noinline fn: T.(P) -> Int) {
      registerMethod(NativeOperatorNames.COMPARE_TO, fn)
      }

/**

* Register a `unaryMinus` operator for `-obj`.
  */
  inline fun <reified T : Any, reified R : Any>
  NativeObjectExtensionsBuilder<T>.registerUnaryMinusOperator(noinline fn: T.() -> R) {
  builder.registerExtensionMethod(cls, NativeOperatorNames.UNARY_MINUS) { receiver, _, _ ->
  wrapAsRuntimeValue(receiver.fn())
  }
  }

/**

* Register a `unaryPlus` operator for `+obj`.
  */
  inline fun <reified T : Any, reified R : Any>
  NativeObjectExtensionsBuilder<T>.registerUnaryPlusOperator(noinline fn: T.() -> R) {
  builder.registerExtensionMethod(cls, NativeOperatorNames.UNARY_PLUS) { receiver, _, _ ->
  wrapAsRuntimeValue(receiver.fn())
  }
  }

/**

* Register a `not` operator for `!obj`.
* The function must return `Boolean`.
  */
  inline fun <reified T : Any>
  NativeObjectExtensionsBuilder<T>.registerNotOperator(noinline fn: T.() -> Boolean) {
  builder.registerExtensionMethod(cls, NativeOperatorNames.NOT) { receiver, _, _ ->
  BooleanValue(receiver.fn())
  }
  }

```

> **Note on `inline` + `reified T`**: The existing helpers (e.g., `registerMethod`) already carry
> `T` through the `NativeObjectExtensionsBuilder<T>` class parameter. All the new helpers need the
> same `reified T` in their own inline signature because they call `registerMethod` which itself is
> `inline`. This matches the pattern already used in the file.

---

## Step 3 — Interpreter changes

All three dispatch functions need modification. The pattern is the same in each: **check for a
registered operator method on `NativeObjectValue` before falling through to the built-in
number/boolean handling**.

### 3a — `evaluateCall`: support `invoke` operator

In the `when (callee)` block inside `evaluateCall`, add a new branch **before** the `else` catch-all:

```kotlin
is NativeObjectValue<*> -> {
    val invokeMethod = engine.getExtensionMethod(callee, NativeOperatorNames.INVOKE)
        ?: throw TypeError(
            "Native type '${callee.qualifiedName}' is not callable. " +
                "Register an 'invoke' operator to make it callable.",
            operation = "function call",
            location = call.location,
            stackTrace = getStackTrace()
        )

    callStack.push("${callee.qualifiedName}.${NativeOperatorNames.INVOKE}", call.location)
    val previousLocation = executionContext.currentLocation
    executionContext.currentLocation = call.location
    try {
        invokeMethod.invoker(callee.value, args, call.location)
    } finally {
        executionContext.currentLocation = previousLocation
        callStack.pop()
    }
}
```

### 3b — `evaluateBinaryOp`: dispatch arithmetic / comparison / equality to native operators

After `val rightValue = evaluate(binOp.right)` (i.e., after both operands are evaluated but before
the existing `when (binOp.operator)` block), insert:

```kotlin
// --- Native object operator dispatch ---
// Must run before the built-in number/boolean checks so that a registered
// operator on a NativeObjectValue takes precedence.
if (leftValue is NativeObjectValue<*>) {
    val operatorName = NativeOperatorNames.forBinaryOp(binOp.operator)
    if (operatorName != null) {
        val method = engine.getExtensionMethod(leftValue, operatorName)
        if (method != null) {
            val rawResult = method.invoker(leftValue.value, listOf(rightValue), binOp.location)

            return when (binOp.operator) {
                // equals returns a BooleanValue directly
                BinaryOperator.EQUAL -> {
                    rawResult as? BooleanValue ?: throw TypeError(
                        "'${NativeOperatorNames.EQUALS}' operator must return a Boolean",
                        operation = "==",
                        location = binOp.location,
                        stackTrace = getStackTrace()
                    )
                }
                // not_equal: same operator, result negated
                BinaryOperator.NOT_EQUAL -> {
                    val b = rawResult as? BooleanValue ?: throw TypeError(
                        "'${NativeOperatorNames.EQUALS}' operator must return a Boolean",
                        operation = "!=",
                        location = binOp.location,
                        stackTrace = getStackTrace()
                    )
                    BooleanValue(!b.value)
                }
                // compareTo returns a Number whose sign determines the result
                BinaryOperator.LESS_THAN -> {
                    val cmp = compareToInt(rawResult, binOp)
                    BooleanValue(cmp < 0)
                }
                BinaryOperator.LESS_THAN_OR_EQUAL -> {
                    val cmp = compareToInt(rawResult, binOp)
                    BooleanValue(cmp <= 0)
                }
                BinaryOperator.GREATER_THAN -> {
                    val cmp = compareToInt(rawResult, binOp)
                    BooleanValue(cmp > 0)
                }
                BinaryOperator.GREATER_THAN_OR_EQUAL -> {
                    val cmp = compareToInt(rawResult, binOp)
                    BooleanValue(cmp >= 0)
                }
                // Arithmetic operators return the result of the method directly
                else -> rawResult
            }
        }
    }
}
// --- End native object operator dispatch ---
```

Add the private helper at the bottom of `Interpreter`:

```kotlin
/** Extract the integer sign from a `compareTo` result value. */
private fun compareToInt(rawResult: RuntimeValue, binOp: BinaryOperation): Int {
    val num = rawResult as? NumberValue ?: throw TypeError(
        "'${NativeOperatorNames.COMPARE_TO}' operator must return a Number",
        operation = binOp.operator.toString(),
        location = binOp.location,
        stackTrace = getStackTrace()
    )
    return num.value.toInt()
}
```

### 3c — `evaluateUnaryOp`: dispatch `-`, `+`, `!` to native operators

At the very top of `evaluateUnaryOp`, **before** the existing `when (unaryOp.operator)` block,
insert:

```kotlin
// --- Native object operator dispatch ---
if (operandValue is NativeObjectValue<*>) {
    val operatorName = when (unaryOp.operator) {
        UnaryOperator.NEGATE -> NativeOperatorNames.UNARY_MINUS
        UnaryOperator.PLUS -> NativeOperatorNames.UNARY_PLUS
        UnaryOperator.NOT -> NativeOperatorNames.NOT
    }
    val method = engine.getExtensionMethod(operandValue, operatorName)
    if (method != null) {
        callStack.push("${operandValue.qualifiedName}.$operatorName", unaryOp.location)
        val previousLocation = executionContext.currentLocation
        executionContext.currentLocation = unaryOp.location
        try {
            return method.invoker(operandValue.value, emptyList(), unaryOp.location)
        } finally {
            executionContext.currentLocation = previousLocation
            callStack.pop()
        }
    }
    // No operator registered → fall through to the existing type error below
}
// --- End native object operator dispatch ---
```

---

## Step 4 — Tests (`NativeObjectOperatorsTest.kt`)

Create `klangscript/src/commonTest/kotlin/NativeObjectOperatorsTest.kt`.

The test helper setup:

```kotlin
/** A simple counter value used as the receiver in all operator tests. */
data class Counter(val n: Int)

fun engineWithCounter(): KlangScriptEngine = KlangScriptEngine.builder()
    .registerFunction<Int, Counter>("counter") { n -> Counter(n) }
    .registerType<Counter> {
        // regular method for asserting the value
        registerMethod("value") { _: Any? -> n }

        // operators under test
        registerInvokeOperator<Double, Counter> { args -> Counter(n + args.sumOf { it.toInt() }) }
        registerPlusOperator<Counter, Counter> { other -> Counter(n + other.n) }
        registerMinusOperator<Counter, Counter> { other -> Counter(n - other.n) }
        registerTimesOperator<Double, Counter> { factor -> Counter((n * factor).toInt()) }
        registerDivOperator<Double, Counter> { divisor -> Counter((n / divisor).toInt()) }
        registerRemOperator<Double, Counter> { mod -> Counter(n % mod.toInt()) }
        registerUnaryMinusOperator { Counter(-n) }
        registerUnaryPlusOperator { Counter(+n) }
        registerEqualsOperator<Counter> { other -> n == other.n }
        registerCompareToOperator<Counter> { other -> n.compareTo(other.n) }
    }
    .build()
```

Test cases (Kotest `StringSpec` style, run with `./gradlew :klangscript:jvmTest`):

```kotlin
class NativeObjectOperatorsTest : StringSpec({

    "invoke operator - calling native object as function" {
        val result = engineWithCounter().execute("counter(10)(1, 2, 3).value()")
        result.value shouldBe 16.0  // 10 + 1 + 2 + 3
    }

    "plus operator" {
        val result = engineWithCounter().execute("(counter(3) + counter(4)).value()")
        result.value shouldBe 7.0
    }

    "minus operator" {
        val result = engineWithCounter().execute("(counter(10) - counter(3)).value()")
        result.value shouldBe 7.0
    }

    "times operator" {
        val result = engineWithCounter().execute("(counter(4) * 3).value()")
        result.value shouldBe 12.0
    }

    "div operator" {
        val result = engineWithCounter().execute("(counter(10) / 2).value()")
        result.value shouldBe 5.0
    }

    "rem operator" {
        val result = engineWithCounter().execute("(counter(10) % 3).value()")
        result.value shouldBe 1.0
    }

    "unaryMinus operator" {
        val result = engineWithCounter().execute("(-counter(5)).value()")
        result.value shouldBe -5.0
    }

    "unaryPlus operator" {
        val result = engineWithCounter().execute("(+counter(5)).value()")
        result.value shouldBe 5.0
    }

    "equals operator - true" {
        val result = engineWithCounter().execute("counter(5) == counter(5)")
        result shouldBe BooleanValue(true)
    }

    "equals operator - false" {
        val result = engineWithCounter().execute("counter(5) == counter(6)")
        result shouldBe BooleanValue(false)
    }

    "not_equal operator" {
        val result = engineWithCounter().execute("counter(5) != counter(6)")
        result shouldBe BooleanValue(true)
    }

    "compareTo - less than" {
        val result = engineWithCounter().execute("counter(3) < counter(5)")
        result shouldBe BooleanValue(true)
    }

    "compareTo - less than or equal (equal)" {
        val result = engineWithCounter().execute("counter(5) <= counter(5)")
        result shouldBe BooleanValue(true)
    }

    "compareTo - greater than" {
        val result = engineWithCounter().execute("counter(9) > counter(5)")
        result shouldBe BooleanValue(true)
    }

    "compareTo - greater than or equal (greater)" {
        val result = engineWithCounter().execute("counter(9) >= counter(5)")
        result shouldBe BooleanValue(true)
    }

    "invoke operator not registered - throws TypeError" {
        val engine = KlangScriptEngine.builder()
            .registerFunction<Int, Counter>("counter") { n -> Counter(n) }
            .registerType<Counter> {}
            .build()
        shouldThrow<TypeError> { engine.execute("counter(1)()") }
    }

    "operator falls through to TypeError when not registered" {
        val engine = KlangScriptEngine.builder()
            .registerFunction<Int, Counter>("counter") { n -> Counter(n) }
            .registerType<Counter> {}
            .build()
        shouldThrow<TypeError> { engine.execute("counter(1) + counter(2)") }
    }

    "operator result can be chained with method call" {
        val result = engineWithCounter().execute("(counter(2) + counter(3)).value()")
        result.value shouldBe 5.0
    }

    "operator result can be used in further operations" {
        val result = engineWithCounter().execute("(counter(2) + counter(3) + counter(1)).value()")
        result.value shouldBe 6.0
    }
})
```

---

## Behavior Notes

- **Left-operand only**: Operator dispatch checks the **left** operand only. `5 + myObj` does NOT
  dispatch to `myObj.plus`; it falls through to the existing `TypeError` for non-numbers. This
  matches Kotlin's dispatch semantics.

- **Fallthrough on missing operator**: If a `NativeObjectValue` has no registered operator for a
  given symbol, the interpreter falls through to the existing type error. This keeps backwards
  compatibility — objects that don't opt in to operators behave exactly as before.

- **`equals` and `compareTo` return-type contracts**: The interpreter throws `TypeError` at runtime
  if an `equals` operator does not return `BooleanValue`, or a `compareTo` operator does not return
  `NumberValue`. This is a runtime check, not a compile-time one.

- **Supertype inheritance**: Because operators are stored as regular extension methods,
  `getExtensionMethod` walks the supertype chain automatically. An operator registered on an
  interface or superclass is available to all subclasses.

- **`NativeOperatorNames` visibility**: Expose the object as `public` so that consuming code
  (libraries that register operators manually via `registerExtensionMethod`) can reference the
  canonical names without hard-coding strings.

---

## Out of Scope for This Task

- KlangScript syntax for operator definitions (e.g., `operator fun + ...`) — deferred.
- `get` / `set` index operators (`obj[i]`, `obj[i] = v`) — requires parser changes (no `[]`
  syntax yet). Deferred.
- `rangeTo` / `contains` / `in` operator — deferred.
- Right-hand-side dispatch (`5 + nativeObj`) — deferred; left-side only for now.
