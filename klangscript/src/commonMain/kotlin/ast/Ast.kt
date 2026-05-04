package io.peekandpoke.klang.script.ast

import io.peekandpoke.klang.common.SourceLocation

/**
 * Abstract Syntax Tree (AST) node definitions for KlangScript
 *
 * The AST represents the hierarchical structure of parsed code.
 * It serves as an intermediate representation between the source code
 * and the runtime execution.
 */

/**
 * Base class for all AST nodes
 *
 * All nodes in the syntax tree inherit from this sealed class,
 * allowing exhaustive when expressions and type-safe pattern matching.
 *
 * @param location Optional source location for debugging and error messages
 */
sealed class AstNode(
    open val location: SourceLocation? = null,
)

// ============================================================
// Programs and Statements
// ============================================================

/**
 * Root node representing a complete program
 *
 * A program consists of a list of top-level statements.
 * These can be expressions, declarations, or other statement types.
 *
 * Example: "print('hello')\nprint('world')" results in a Program
 * with two ExpressionStatement nodes.
 */
data class Program(
    val statements: List<Statement>,
    override val location: SourceLocation? = null,
) : AstNode(location)

/**
 * Base class for all statements
 *
 * Statements represent actions or declarations in the program.
 * They don't produce values directly but can have side effects.
 */
sealed class Statement(location: SourceLocation? = null) : AstNode(location)

/**
 * An expression used as a statement
 *
 * In KlangScript, expressions can appear as statements at the top level.
 * This allows code like: print("hello") or 1 + 1
 *
 * Example: "print('hello')" is a CallExpression wrapped in an ExpressionStatement
 */
data class ExpressionStatement(
    val expression: Expression,
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * A variable declaration statement using `let`
 *
 * Declares a mutable variable in the current scope. The variable can be
 * reassigned after declaration.
 *
 * **Syntax:**
 * - With initializer: `let x = 10`
 * - Without initializer: `let x` (value is `null`)
 *
 * **Scope rules:**
 * - Block-scoped (lexical scoping)
 * - Can be shadowed in inner scopes
 * - Cannot be redeclared in the same scope
 *
 * Examples:
 * ```
 * let count = 0
 * let name = "Alice"
 * let uninitialized  // null by default
 * ```
 *
 * @param name The variable identifier
 * @param initializer Optional expression to initialize the variable (null if omitted)
 */
data class LetDeclaration(
    val name: String,
    val initializer: Expression?,
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * A constant declaration statement using `const`
 *
 * Declares an immutable variable in the current scope. The variable cannot
 * be reassigned after declaration.
 *
 * **Syntax:**
 * - `const PI = 3.14159`
 * - Must have an initializer (no uninitialized const)
 *
 * **Scope rules:**
 * - Block-scoped (lexical scoping)
 * - Cannot be reassigned (runtime error if attempted)
 * - Can be shadowed in inner scopes (new binding)
 * - Cannot be redeclared in the same scope
 *
 * Examples:
 * ```
 * const MAX_SIZE = 100
 * const message = "Hello"
 * const result = calculateValue()
 * ```
 *
 * **Note:** Currently, KlangScript requires const to have an initializer.
 * `const x` without initialization is a syntax error.
 *
 * @param name The constant identifier
 * @param initializer Expression to initialize the constant (required)
 */
data class ConstDeclaration(
    val name: String,
    val initializer: Expression,
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * An export statement for marking symbols to be exported from a library
 *
 * Export statements declare which symbols should be available when the library
 * is imported. Only exported symbols are visible to importing code.
 *
 * **Syntax:**
 * - `export { functionName, objectName, ... }` - Export with original names
 * - `export { add as sum, multiply }` - Export with aliases
 *
 * **Design philosophy:**
 * Explicit exports prevent accidental scope pollution and name conflicts.
 * Library internal helpers remain private unless explicitly exported.
 * Aliases allow libraries to expose different API names than internal names.
 *
 * Examples:
 * ```javascript
 * // Library code without aliases:
 * let internalHelper = (x) => x * 2  // Not exported, stays private
 * let add = (a, b) => a + b
 * let multiply = (a, b) => a * b
 * export { add, multiply }  // Only these are accessible
 *
 * // Library code with aliases:
 * let add = (a, b) => a + b
 * export { add as sum }  // Exported as 'sum', not 'add'
 *
 * // User code:
 * import { sum } from "math"  // Import by exported name
 * sum(1, 2)  // Works
 * add(1, 2)  // Error: undefined (not exported with this name)
 * ```
 *
 * @param exports List of (localName, exportedName) pairs
 *                The localName is the variable in the library scope
 *                The exportedName is the name visible to importers
 */
data class ExportStatement(
    val exports: List<Pair<String, String>>,  // Pair: (localName, exportedName)
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * An export declaration: combined immutable binding + export marker
 *
 * Declares a constant at the top level and marks it as visible to importers
 * under its own name. Equivalent to `const name = expr; export { name }` but
 * expressed in a single statement so the authorial intent (this is a
 * deliberately public part of the module) is obvious at the declaration site.
 *
 * **Syntax:** `export <name> = <expression>`
 *
 * **Semantics:**
 * - Binds `name` in the current environment as immutable (cannot be reassigned).
 * - Marks `name` as exported under its own name.
 * - Intended for top-level use in a library file. Nested usage inside a function
 *   body or block currently produces a local immutable binding plus an
 *   ineffective export marker (consistent with the existing `export { ... }`
 *   form, which is also silently ineffective inside nested scopes).
 *
 * Example:
 * ```javascript
 * export bass = n("0 -2 4 5").scale("e2:minor")
 * export song = stack(bass).gain(0.8)
 * ```
 *
 * **Equivalent to:**
 * ```javascript
 * const bass = n("0 -2 4 5").scale("e2:minor")
 * const song = stack(bass).gain(0.8)
 * export { bass, song }
 * ```
 *
 * @param name The exported binding name
 * @param initializer Expression producing the value (required)
 */
data class ExportDeclaration(
    val name: String,
    val initializer: Expression,
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * An import statement for loading library code
 *
 * Imports symbols from a library into the current scope.
 * Libraries are KlangScript files that export functions, objects, and values.
 *
 * **Syntax:**
 * - `import * from "libraryName"` - Import all exports into current scope
 * - `import * as name from "libraryName"` - Import as namespace object
 * - `import { name1, name2 } from "libraryName"` - Import specific exports
 * - `import { name1 as alias1, name2 } from "libraryName"` - Import with aliasing
 *
 * **Semantics:**
 * 1. Load the library source code by name
 * 2. Parse and evaluate the library in an isolated environment
 * 3. Import specified symbols (or all exports for wildcard) into current scope
 * 4. Apply aliases if specified (bind to local name instead of export name)
 *
 * **Design philosophy:**
 * Libraries are not hard-coded in Kotlin. Instead, they are KlangScript files
 * registered with the engine. This keeps the language core minimal and flexible.
 *
 * Examples:
 * ```javascript
 * // Import all exported functions into current scope
 * import * from "strudel.klang"
 * note("a b c d").gain(0.5)  // note() is now available
 *
 * // Import as namespace (no scope pollution)
 * import * as math from "math"
 * math.add(1, 2)  // Clear that 'add' comes from math
 *
 * // Import only specific functions
 * import { add, multiply } from "math"
 * add(1, 2)  // Works
 * subtract(5, 3)  // Error if not imported
 *
 * // Import with aliases to avoid name conflicts
 * import { add as sum, multiply as mul } from "math"
 * sum(1, 2)  // Uses 'sum' instead of 'add'
 *
 * // Mixed: some aliased, some not
 * import { add, multiply as mul } from "math"
 * add(1, 2)
 * mul(3, 4)
 * ```
 *
 * **Implementation notes:**
 * - Both wildcard (`*`) and selective imports are supported
 * - Library evaluation is isolated (library internals don't leak)
 * - Only explicitly exported symbols can be imported
 * - Aliases allow importing conflicting names from different libraries
 *
 * @param libraryName The name of the library to import (without .klang extension)
 * @param imports List of (exportName, localAlias) pairs for selective imports (null for wildcard)
 *                The localAlias is the name to bind in current scope; exportName is from library
 */
data class ImportStatement(
    val libraryName: String,
    val imports: List<Pair<String, String>>? = null,  // null = import *, non-null = import { ... }
    // Pair: (exportName, localAlias)
    val namespaceAlias: String? = null,  // If set, import * as namespaceAlias
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * A return statement for exiting from functions
 *
 * Returns a value from the current function and transfers control back to the caller.
 * Return statements are used in arrow function block bodies and other functions.
 *
 * **Syntax:**
 * - `return` - Return null/undefined
 * - `return expression` - Return the value of the expression
 *
 * **Semantics:**
 * - Evaluates the expression (if provided)
 * - Immediately exits the current function
 * - Returns control to the function caller with the value
 *
 * Examples:
 * ```javascript
 * // In arrow function block body
 * x => {
 *   let doubled = x * 2
 *   return doubled + 1
 * }
 *
 * // Early return
 * x => {
 *   if (x < 0) return 0
 *   return x * 2
 * }
 *
 * // Return null
 * () => {
 *   doSomething()
 *   return
 * }
 * ```
 *
 * @param value Optional expression to return (null means return NullValue)
 */
data class ReturnStatement(
    val value: Expression?,
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * A while loop statement
 *
 * Repeatedly executes the body as long as the condition evaluates to truthy.
 *
 * Example:
 * ```javascript
 * let i = 0
 * while (i < 5) {
 *   i = i + 1
 * }
 * ```
 */
data class WhileStatement(
    val condition: Expression,
    val body: List<Statement>,
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * A do-while loop statement
 *
 * Executes the body at least once, then continues as long as the condition is truthy.
 *
 * Example:
 * ```javascript
 * let i = 0
 * do {
 *   i = i + 1
 * } while (i < 5)
 * ```
 */
data class DoWhileStatement(
    val body: List<Statement>,
    val condition: Expression,
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * A classic for loop statement
 *
 * Supports optional init, condition, and update parts.
 *
 * Example:
 * ```javascript
 * for (let i = 0; i < 5; i = i + 1) {
 *   print(i)
 * }
 * ```
 *
 * @param init Optional initializer statement (let i = 0 or expression)
 * @param condition Optional loop condition (null = infinite loop)
 * @param update Optional update expression (runs after each iteration)
 * @param body Loop body statements
 */
data class ForStatement(
    val init: Statement?,
    val condition: Expression?,
    val update: Expression?,
    val body: List<Statement>,
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * A break statement for exiting loops
 *
 * When encountered inside a while, do-while, or for loop, immediately exits the loop.
 * If used outside a loop, a runtime error occurs.
 *
 * Example:
 * ```javascript
 * while (true) {
 *   break
 * }
 * ```
 */
data class BreakStatement(
    override val location: SourceLocation? = null,
) : Statement(location)

/**
 * A continue statement for skipping to the next loop iteration
 *
 * When encountered inside a while, do-while, or for loop, skips the rest of the
 * loop body and proceeds to the next iteration.
 * If used outside a loop, a runtime error occurs.
 *
 * Example:
 * ```javascript
 * for (let i = 0; i < 10; i = i + 1) {
 *   if (i == 5) continue
 *   print(i)
 * }
 * ```
 */
data class ContinueStatement(
    override val location: SourceLocation? = null,
) : Statement(location)

// ============================================================
// Expressions
// ============================================================

/**
 * Base class for all expressions
 *
 * Expressions are nodes that evaluate to values.
 * They can be combined and nested to form complex computations.
 */
sealed class Expression(location: SourceLocation? = null) : AstNode(location)

/**
 * A numeric literal (integers and decimals)
 *
 * All numbers are stored as doubles internally, matching JavaScript semantics.
 *
 * Examples: 42, 3.14, 0.5
 */
data class NumberLiteral(
    val value: Double,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * A string literal
 *
 * Strings can be enclosed in single or double quotes, or backticks for multi-line.
 * The value stored here has quotes stripped and escape sequences processed.
 *
 * Examples: "hello", 'world', `multi-line`
 */
data class StringLiteral(
    val value: String,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * A boolean literal
 *
 * Represents true or false values.
 *
 * Examples: true, false
 */
data class BooleanLiteral(
    val value: Boolean,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * A null literal
 *
 * Represents the absence of a value.
 * KlangScript has no 'undefined' - only 'null'.
 *
 * Example: null
 */
data object NullLiteral : Expression(null)

/**
 * An identifier (variable or function name)
 *
 * Represents a reference to a variable or function by name.
 * The interpreter will look this up in the environment.
 *
 * Examples: x, print, myFunction
 */
data class Identifier(
    val name: String,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * A single argument at a call site.
 *
 * A [CallExpression] carries a list of [Argument]s. Each argument is either
 * positional (bound by index) or named (bound by parameter name).
 *
 * A call must use either all positional or all named arguments — mixing is
 * rejected at classification time by the interpreter and by the analyzer.
 */
sealed class Argument {
    /** The expression producing the argument's value. */
    abstract val value: Expression

    /** Source location of the argument — the name token for named, the value for positional. */
    abstract val location: SourceLocation?

    /** Positional: `foo(expr)` — bound by index. */
    data class Positional(
        override val value: Expression,
        override val location: SourceLocation? = value.location,
    ) : Argument()

    /** Named: `foo(name = expr)` — bound to the parameter matching [name]. */
    data class Named(
        val name: String,
        override val value: Expression,
        val nameLocation: SourceLocation? = null,
        override val location: SourceLocation? = nameLocation ?: value.location,
    ) : Argument()
}

/**
 * A function call expression
 *
 * Represents calling a function with zero or more arguments.
 * The callee can be any expression (typically an Identifier).
 *
 * Examples:
 * - print("hello") -> callee: Identifier("print"), arguments: [Positional(StringLiteral("hello"))]
 * - add(1, 2, 3) -> callee: Identifier("add"), arguments: [Positional, Positional, Positional]
 * - filter(cutoff = 800) -> callee: Identifier("filter"), arguments: [Named("cutoff", ...)]
 */
data class CallExpression(
    val callee: Expression,
    val arguments: List<Argument>,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * Binary operator types for arithmetic and other operations
 */
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

    /** Exponentiation: a ** b */
    POWER,

    /** Equality: a == b */
    EQUAL,

    /** Inequality: a != b */
    NOT_EQUAL,

    /** Strict equality: a === b (same type AND same value) */
    STRICT_EQUAL,

    /** Strict inequality: a !== b */
    STRICT_NOT_EQUAL,

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

    /** In operator: "key" in obj */
    IN,

    /** Bitwise AND: a & b */
    BITWISE_AND,

    /** Bitwise OR: a | b */
    BITWISE_OR,

    /** Bitwise XOR: a ^ b */
    BITWISE_XOR,

    /** Shift left: a << b */
    SHIFT_LEFT,

    /** Shift right: a >> b */
    SHIFT_RIGHT,

    /** Unsigned shift right: a >>> b */
    UNSIGNED_SHIFT_RIGHT,

    /** Nullish coalescing: a ?? b */
    NULLISH_COALESCE,
}

/**
 * A binary operation expression
 *
 * Represents an operation between two expressions.
 * The operator determines what operation is performed.
 *
 * Examples:
 * - 1 + 2 -> left: NumberLiteral(1), operator: ADD, right: NumberLiteral(2)
 * - x * 3 -> left: Identifier("x"), operator: MULTIPLY, right: NumberLiteral(3)
 * - (1 + 2) * 3 -> nested: left: BinaryOp(1 + 2), operator: MULTIPLY, right: NumberLiteral(3)
 *
 * Operator precedence:
 * - Multiplication and division have higher precedence than addition and subtraction
 * - Operations of equal precedence are left-associative: 1 - 2 - 3 = (1 - 2) - 3
 */
data class BinaryOperation(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * Unary operator types for prefix and postfix operations
 */
enum class UnaryOperator {
    /** Negation: -x (arithmetic negation) */
    NEGATE,

    /** Plus: +x (arithmetic identity, converts to number) */
    PLUS,

    /** Logical NOT: !x (boolean negation) */
    NOT,

    /** Prefix increment: ++x (add 1, return new value) */
    PREFIX_INCREMENT,

    /** Prefix decrement: --x (subtract 1, return new value) */
    PREFIX_DECREMENT,

    /** Postfix increment: x++ (add 1, return original value) */
    POSTFIX_INCREMENT,

    /** Postfix decrement: x-- (subtract 1, return original value) */
    POSTFIX_DECREMENT,

    /** Bitwise NOT: ~x */
    BITWISE_NOT,
}

/**
 * A unary operation expression
 *
 * Represents a prefix operation on a single expression.
 * The operator determines what operation is performed.
 *
 * Examples:
 * - -5 -> operator: NEGATE, operand: NumberLiteral(5)
 * - !true -> operator: NOT, operand: BooleanLiteral(true)
 * - -x -> operator: NEGATE, operand: Identifier("x")
 * - -(1 + 2) -> operator: NEGATE, operand: BinaryOperation(1 + 2)
 *
 * Operator semantics:
 * - NEGATE: Arithmetic negation (flips sign of number)
 * - PLUS: Arithmetic identity (no-op for numbers, can be used for clarity)
 * - NOT: Logical negation (converts truthy/falsy to opposite boolean)
 */
data class UnaryOperation(
    val operator: UnaryOperator,
    val operand: Expression,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * A member access expression (dot notation)
 *
 * Represents accessing a property or method on an object using dot notation.
 * This is the foundation for method chaining patterns.
 *
 * The object can be any expression, and property is always an identifier.
 * Member access has higher precedence than function calls, allowing patterns like:
 * - obj.method() - first access method, then call it
 * - obj.prop.method() - chain multiple accesses
 *
 * Examples:
 * - obj.name -> object: Identifier("obj"), property: "name"
 * - person.getName -> object: Identifier("person"), property: "getName"
 * - obj.prop.method -> nested: MemberAccess(MemberAccess(obj, "prop"), "method")
 * - note("c").gain(0.5) -> MemberAccess applied to CallExpression result
 *
 * Method chaining:
 * ```
 * note("c d e f")    // CallExpression returns an object
 *   .gain(0.5)       // MemberAccess to "gain", then CallExpression
 *   .pan("0 1")      // MemberAccess to "pan", then CallExpression
 * ```
 *
 * This desugars to nested operations:
 * CallExpression(
 *   MemberAccess(
 *     CallExpression(
 *       MemberAccess(CallExpression(note, ["c d e f"]), "gain"),
 *       [0.5]
 *     ),
 *     "pan"
 *   ),
 *   ["0 1"]
 * )
 */
data class MemberAccess(
    val obj: Expression,
    val property: String,
    val optional: Boolean = false,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * Body of an arrow function - either an expression or a block of statements
 */
sealed class ArrowFunctionBody {
    /**
     * Expression body: `x => x + 1`
     * The expression value is implicitly returned
     */
    data class ExpressionBody(val expression: Expression) : ArrowFunctionBody()

    /**
     * Block body: `x => { let y = x + 1; return y; }`
     * Requires explicit return statement to return a value
     */
    data class BlockBody(val statements: List<Statement>) : ArrowFunctionBody()
}

/**
 * An arrow function expression (lambda/anonymous function)
 *
 * Represents JavaScript-style arrow functions for callbacks and functional programming.
 * Arrow functions are first-class values that can be passed as arguments, returned from
 * functions, and stored in variables.
 *
 * **Supported forms:**
 * - Expression body: `x => x + 1` (implicit return)
 * - Block body: `x => { return x + 1; }` (explicit return)
 *
 * **Parameter syntax:**
 * - Single parameter (no parens): `x => expr`
 * - Multiple parameters (with parens): `(a, b) => expr`
 * - No parameters: `() => expr`
 *
 * **Closure semantics:**
 * Arrow functions capture their lexical environment (closure), allowing access
 * to variables from outer scopes.
 *
 * Examples:
 * ```
 * // Expression body (implicit return)
 * x => x + 1
 * (a, b) => a + b
 *
 * // Block body (explicit return)
 * x => {
 *   let doubled = x * 2
 *   return doubled + 1
 * }
 *
 * // Block body with early return
 * x => {
 *   if (x < 0) return 0
 *   return x * 2
 * }
 *
 * // No parameters
 * () => 42
 *
 * // Nested arrow functions
 * x => y => x + y
 *
 * // As callback argument
 * note("a b c").filter(x => x.data.note == "a")
 *
 * // Returning object literal (wrapped in parens to avoid ambiguity)
 * x => ({ value: x, doubled: x * 2 })
 *
 * // Closure capturing outer variable
 * let offset = 5
 * let addOffset = x => x + offset
 * ```
 *
 * AST structure:
 * - `x => x + 1` becomes ArrowFunction(["x"], ExpressionBody(BinaryOperation(...)))
 * - `x => { return x + 1; }` becomes ArrowFunction(["x"], BlockBody([ReturnStatement(...)]))
 *
 * @param parameters List of parameter names (identifiers)
 * @param body Function body (expression or block)
 */
data class ArrowFunction(
    val parameters: List<String>,
    val body: ArrowFunctionBody,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * An object literal expression
 *
 * Represents JavaScript-style object literals with key-value pairs.
 * Object literals create new ObjectValue instances at runtime with the
 * specified properties.
 *
 * **Syntax:**
 * - Empty object: `{}`
 * - With properties: `{ a: 1, b: 2 }`
 * - String keys: `{ "name": "Alice", "age": 30 }`
 * - Nested objects: `{ outer: { inner: 42 } }`
 * - Trailing commas allowed: `{ a: 1, b: 2, }`
 *
 * **Key types:**
 * Currently only identifier and string literal keys are supported.
 * Computed property names are not supported yet.
 *
 * **Property values:**
 * Any expression can be used as a property value:
 * - Literals: `{ x: 42, y: "hello" }`
 * - Variables: `{ count: myVar }`
 * - Function calls: `{ result: calculate() }`
 * - Arrow functions: `{ callback: x => x * 2 }`
 * - Nested objects: `{ config: { debug: true } }`
 *
 * Examples:
 * ```
 * // Simple object
 * { x: 10, y: 20 }
 *
 * // With string keys
 * { "first-name": "John", "last-name": "Doe" }
 *
 * // Nested structure
 * {
 *   position: { x: 0, y: 0 },
 *   velocity: { x: 1, y: -1 }
 * }
 *
 * // With functions
 * {
 *   name: "Counter",
 *   increment: () => count + 1
 * }
 *
 * // In arrow function (requires parens to disambiguate from block)
 * x => ({ value: x, doubled: x * 2 })
 * ```
 *
 * AST structure:
 * - `{ a: 1, b: 2 }` becomes ObjectLiteral([("a", NumberLiteral(1)), ("b", NumberLiteral(2))])
 * - Empty object `{}` becomes ObjectLiteral([])
 *
 * @param properties List of key-value pairs (property name, property value expression)
 */
data class ObjectLiteral(
    val properties: List<Pair<String, Expression>>,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * An array literal expression
 *
 * Represents JavaScript-style array literals with zero or more elements.
 * Array literals create new ArrayValue instances at runtime with the
 * specified elements.
 *
 * **Syntax:**
 * - Empty array: `[]`
 * - With elements: `[1, 2, 3]`
 * - Mixed types: `[1, "hello", true, null]`
 * - Expressions: `[x + 1, func(), obj.prop]`
 * - Nested arrays: `[[1, 2], [3, 4]]`
 * - Trailing commas allowed: `[1, 2, 3,]`
 *
 * **Element types:**
 * Any expression can be used as an array element:
 * - Literals: `[42, "hello", true, null]`
 * - Variables: `[x, y, z]`
 * - Function calls: `[getValue(), calculate()]`
 * - Arrow functions: `[x => x * 2, y => y + 1]`
 * - Objects: `[{ a: 1 }, { b: 2 }]`
 * - Nested arrays: `[[1, 2], [3, 4]]`
 *
 * Examples:
 * ```javascript
 * // Empty array
 * []
 *
 * // Simple array
 * [1, 2, 3]
 *
 * // Mixed types
 * [42, "hello", true, null, { x: 10 }]
 *
 * // Expressions as elements
 * [x + 1, y * 2, calculate()]
 *
 * // Nested arrays
 * [[1, 2], [3, 4], [5, 6]]
 *
 * // Arrays in variables
 * let numbers = [1, 2, 3, 4, 5]
 *
 * // Arrays as function arguments
 * print([1, 2, 3])
 *
 * // Arrays in objects
 * { items: [1, 2, 3], names: ["a", "b"] }
 * ```
 *
 * AST structure:
 * - `[1, 2, 3]` becomes ArrayLiteral([NumberLiteral(1), NumberLiteral(2), NumberLiteral(3)])
 * - Empty array `[]` becomes ArrayLiteral([])
 * - Nested `[[1, 2]]` becomes ArrayLiteral([ArrayLiteral([NumberLiteral(1), NumberLiteral(2)])])
 *
 * @param elements List of expressions representing array elements
 */
data class ArrayLiteral(
    val elements: List<Expression>,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * An assignment expression: target = value
 *
 * Assigns a value to an assignable target.
 * Target can be:
 * - Identifier: x = expr
 * - MemberAccess: obj.prop = expr
 * - IndexAccess: arr[ i ] = expr
 *
 * Assignment is right-associative and has the lowest precedence (below ternary).
 *
 * @param target The left-hand side expression (must be assignable)
 * @param value The right-hand side expression to assign
 */
data class AssignmentExpression(
    val target: Expression,
    val value: Expression,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * A ternary conditional expression: condition ? thenExpr : elseExpr
 *
 * Evaluates condition; if truthy evaluates and returns thenExpr, otherwise elseExpr.
 * Right-associative.
 *
 * @param condition The condition expression
 * @param thenExpr Expression evaluated when condition is truthy
 * @param elseExpr Expression evaluated when condition is falsy
 */
data class TernaryExpression(
    val condition: Expression,
    val thenExpr: Expression,
    val elseExpr: Expression,
    override val location: SourceLocation? = null,
) : Expression(location)

/**
 * An index access expression: obj[index]
 *
 * Accesses an element of an array by numeric index,
 * or a property of an object by string key.
 *
 * Examples:
 * - arr[0]       → first element of array
 * - arr[ i ]       → element at index i
 * - obj["key"]   → property "key" on object
 *
 * @param obj The object/array expression
 * @param index The index/key expression
 */
data class IndexAccess(
    val obj: Expression,
    val index: Expression,
    override val location: SourceLocation? = null,
) : Expression(location)

// ============================================================
// Control Flow Expressions
// ============================================================

/**
 * Else branch for if expressions
 *
 * Either a block of statements or another if expression (for else-if chains).
 */
sealed class ElseBranch {
    /** An else block: `else { statements }` */
    data class Block(val statements: List<Statement>) : ElseBranch()

    /** An else-if chain: `else if (cond) { ... }` */
    data class If(val ifExpr: IfExpression) : ElseBranch()
}

/**
 * An if expression (can also be used as a statement)
 *
 * `if/else` is an expression in KlangScript, meaning it can produce a value.
 * When used as a statement, the value is discarded.
 * When used as an expression (e.g. `let x = if (cond) { 1 } else { 2 }`), the
 * value of the executed branch is the value of the if expression.
 *
 * The value of a branch is the value of the last ExpressionStatement in the block,
 * or NullValue if the block is empty or ends with a non-expression statement.
 *
 * Examples:
 * ```javascript
 * // As statement
 * if (x > 0) {
 *   print("positive")
 * } else {
 *   print("non-positive")
 * }
 *
 * // As expression
 * let label = if (x > 0) { "positive" } else { "non-positive" }
 *
 * // Else-if chain
 * if (x < 0) { "negative" } else if (x == 0) { "zero" } else { "positive" }
 * ```
 *
 * @param condition The boolean condition to evaluate
 * @param thenBranch Statements to execute if condition is truthy
 * @param elseBranch Optional else branch (block or else-if)
 */
data class IfExpression(
    val condition: Expression,
    val thenBranch: List<Statement>,
    val elseBranch: ElseBranch?,
    override val location: SourceLocation? = null,
) : Expression(location)

// ============================================================
// Template Literals
// ============================================================

/**
 * A part of a template literal string
 *
 * Template literals consist of alternating text and interpolated expressions.
 */
sealed class TemplatePart {
    /** A literal text segment */
    data class Text(val value: String) : TemplatePart()

    /** An interpolated expression: `${expr}` */
    data class Interp(val expression: Expression) : TemplatePart()
}

/**
 * A template literal expression
 *
 * Template literals (backtick strings) support expression interpolation using `${expr}`.
 * They evaluate each part and concatenate the results into a string.
 *
 * Example:
 * ```javascript
 * let name = "World"
 * let greeting = `Hello, ${name}!`  // "Hello, World!"
 * let result = `The answer is ${40 + 2}`  // "The answer is 42"
 * ```
 *
 * @param parts List of alternating text and interpolated expression parts
 */
data class TemplateLiteral(
    val parts: List<TemplatePart>,
    override val location: SourceLocation? = null,
) : Expression(location)
