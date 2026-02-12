Here is a detailed implementation plan for adding **Bitwise Operators** (`&`, `|`, `^`, `<<`, `>>`) to the KlangScript
language. This complements the boolean logic operators (`&&`, `||`) discussed previously.

You can save this plan as `docs/agent-tasks/klangscript-bitwise-operators.md` and hand it over to a coding agent.

***

# Feature Implementation Plan: Bitwise Operators

This task involves adding support for standard bitwise operations to the KlangScript language. These operators allow
manipulation of numbers at the bit level, which is essential for certain algorithmic patterns and flag manipulation.

## 1. Goal

Support the following binary operators in the AST, Parser, and Interpreter:

* **Bitwise AND**: `&`
* **Bitwise OR**: `|`
* **Bitwise XOR**: `^`
* **Bitwise Left Shift**: `<<`
* **Bitwise Right Shift**: `>>` (signed)

## 2. Technical Implementation Details

### A. Abstract Syntax Tree (`Ast.kt`)

Update the `BinaryOperator` enum to include the new operator types.

* **File**: `klangscript/src/commonMain/kotlin/ast/Ast.kt`
* **Action**: Add the following enum entries to `BinaryOperator`:

```kotlin
BITWISE_AND,    // &
BITWISE_OR,     // |
BITWISE_XOR,    // ^
SHIFT_LEFT,     // <<
SHIFT_RIGHT,    // >>
```

### B. Parser Grammar (`KlangScriptParser.kt`)

We need to introduce new tokens and grammar rules. Crucially, we must respect standard operator precedence.

**Standard Precedence Order (High to Low):**

1. Multiplicative (`*`, `/`, `%`)
2. Additive (`+`, `-`)
3. **Bitwise Shifts** (`<<`, `>>`)  <-- **Insert Here**
4. Relational / Comparison (`<`, `<=`, `>`, `>=`)
5. Equality (`==`, `!=`)
6. **Bitwise AND** (`&`)            <-- **Insert Here**
7. **Bitwise XOR** (`^`)            <-- **Insert Here**
8. **Bitwise OR** (`|`)             <-- **Insert Here**
9. Logical AND (`&&`)
10. Logical OR (`||`)

* **File**: `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt`
* **Step 1: Define Tokens**
    * Add tokens for `<<` and `>>` (Must be defined *before* `<` and `>` to ensure greedy matching).
    * Add tokens for `&`, `^`, `|`.
* **Step 2: Define Grammar Layers**
    * Create `shiftExpr` parser rule:
        * Depends on `additionExpr`.
        * Handles `<<`, `>>`.
    * Refactor `comparisonExpr` to depend on `shiftExpr` instead of `additionExpr`.
    * Create `bitwiseAndExpr` parser rule:
        * Depends on `comparisonExpr`.
        * Handles `&`.
    * Create `bitwiseXorExpr` parser rule:
        * Depends on `bitwiseAndExpr`.
        * Handles `^`.
    * Create `bitwiseOrExpr` parser rule:
        * Depends on `bitwiseXorExpr`.
        * Handles `|`.
    * Update `logicalAndExpr` (or the next level up) to depend on `bitwiseOrExpr`.

### C. Interpreter Logic (`Interpreter.kt`)

Implement the evaluation logic for the new operators. Since KlangScript numbers are `Double`, they must be cast to
`Int` (or `Long`) to perform bitwise operations, then converted back to `Double`.

* **File**: `klangscript/src/commonMain/kotlin/runtime/Interpreter.kt`
* **Action**: Update `evaluateBinaryOp` method.
* **Logic**:
    * Ensure both operands are `NumberValue`. Throw `TypeError` if not.
    * Convert operands to `Int`: `val leftInt = leftValue.value.toInt()`
    * Perform operation:
        * `BITWISE_AND`: `leftInt and rightInt`
        * `BITWISE_OR`: `leftInt or rightInt`
        * `BITWISE_XOR`: `leftInt xor rightInt`
        * `SHIFT_LEFT`: `leftInt shl rightInt`
        * `SHIFT_RIGHT`: `leftInt shr rightInt`
    * Return result as `NumberValue(result.toDouble())`.

## 3. Verification & Testing

Create a new test file `BitwiseOperatorTest.kt` or add to existing tests.

**Test Cases:**

1. **AND**: `5 & 3` should be `1` (101 & 011 = 001)
2. **OR**: `5 | 3` should be `7` (101 | 011 = 111)
3. **XOR**: `5 ^ 3` should be `6` (101 ^ 011 = 110)
4. **Shift Left**: `1 << 2` should be `4`
5. **Shift Right**: `8 >> 2` should be `2`
6. **Precedence**:
    * `1 + 2 << 2` should be `12` (calculated as `(1+2) << 2`), not `5` (`1 + (2<<2)`).
    * `5 & 3 == 1` should be `true` (calculated as `5 & (3 == 1)` is `5 & 0 = 0`, wait, `==` has higher precedence than
      `&`).
        * Correction: `==` binds tighter than `&`.
        * `5 & 1 == 1` -> `5 & (1 == 1)` -> `5 & 1` -> `1`.
        * `5 & 3 + 2` -> `5 & 5` -> `5`.

## 4. Dependencies

* This task assumes the Boolean Logic (`&&`, `||`) implementation is either completed or will be integrated after this
  chain is established. If `&&` / `||` are not yet in, the `bitwiseOrExpr` will simply be the top-level expression or
  flow into `arrowExpr`.
