package io.peekandpoke.klang.script.ast

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
 */
sealed class AstNode

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
) : AstNode()

/**
 * Base class for all statements
 *
 * Statements represent actions or declarations in the program.
 * They don't produce values directly but can have side effects.
 */
sealed class Statement : AstNode()

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
) : Statement()

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
) : Statement()

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
) : Statement()

/**
 * An export statement for marking symbols to be exported from a library
 *
 * Export statements declare which symbols should be available when the library
 * is imported. Only exported symbols are visible to importing code.
 *
 * **Syntax:**
 * - `export { functionName, objectName, ... }`
 *
 * **Design philosophy:**
 * Explicit exports prevent accidental scope pollution and name conflicts.
 * Library internal helpers remain private unless explicitly exported.
 *
 * Examples:
 * ```javascript
 * // Library code:
 * let internalHelper = (x) => x * 2  // Not exported, stays private
 * let add = (a, b) => a + b
 * let multiply = (a, b) => a * b
 * export { add, multiply }  // Only these are accessible
 *
 * // User code:
 * import { add } from "math"
 * add(1, 2)  // Works
 * internalHelper(5)  // Error: undefined
 * ```
 *
 * @param names List of symbol names to export
 */
data class ExportStatement(
    val names: List<String>,
) : Statement()

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
) : Statement()

// ============================================================
// Expressions
// ============================================================

/**
 * Base class for all expressions
 *
 * Expressions are nodes that evaluate to values.
 * They can be combined and nested to form complex computations.
 */
sealed class Expression : AstNode()

/**
 * A numeric literal (integers and decimals)
 *
 * All numbers are stored as doubles internally, matching JavaScript semantics.
 *
 * Examples: 42, 3.14, 0.5
 */
data class NumberLiteral(
    val value: Double,
) : Expression()

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
) : Expression()

/**
 * A boolean literal
 *
 * Represents true or false values.
 *
 * Examples: true, false
 */
data class BooleanLiteral(
    val value: Boolean,
) : Expression()

/**
 * A null literal
 *
 * Represents the absence of a value.
 * KlangScript has no 'undefined' - only 'null'.
 *
 * Example: null
 */
data object NullLiteral : Expression()

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
) : Expression()

/**
 * A function call expression
 *
 * Represents calling a function with zero or more arguments.
 * The callee can be any expression (typically an Identifier).
 *
 * Examples:
 * - print("hello") -> callee: Identifier("print"), arguments: [StringLiteral("hello")]
 * - add(1, 2, 3) -> callee: Identifier("add"), arguments: [NumberLiteral(1), NumberLiteral(2), NumberLiteral(3)]
 * - upper(getName()) -> nested function call
 */
data class CallExpression(
    val callee: Expression,
    val arguments: List<Expression>,
) : Expression()

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
) : Expression()

/**
 * Unary operator types for prefix operations
 */
enum class UnaryOperator {
    /** Negation: -x (arithmetic negation) */
    NEGATE,

    /** Plus: +x (arithmetic identity, converts to number) */
    PLUS,

    /** Logical NOT: !x (boolean negation) */
    NOT,
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
) : Expression()

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
) : Expression()

/**
 * An arrow function expression (lambda/anonymous function)
 *
 * Represents JavaScript-style arrow functions for callbacks and functional programming.
 * Arrow functions are first-class values that can be passed as arguments, returned from
 * functions, and stored in variables.
 *
 * **Current implementation: Expression bodies only**
 * - Single expression after `=>`: `x => x + 1`
 * - Block bodies deferred to Phase 6: `x => { return x + 1; }`
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
 * // Single parameter, simple expression
 * x => x + 1
 *
 * // Multiple parameters
 * (a, b) => a + b
 *
 * // No parameters
 * () => 42
 *
 * // Nested arrow functions
 * x => y => x + y
 *
 * // As callback argument
 * note("a b c").superImpose(x => x.detune(0.5))
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
 * - `x => x + 1` becomes ArrowFunction(["x"], BinaryOperation(...))
 * - `(a, b) => a * b` becomes ArrowFunction(["a", "b"], BinaryOperation(...))
 *
 * @param parameters List of parameter names (identifiers)
 * @param body Expression to evaluate and return
 */
data class ArrowFunction(
    val parameters: List<String>,
    val body: Expression,
) : Expression()

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
) : Expression()
