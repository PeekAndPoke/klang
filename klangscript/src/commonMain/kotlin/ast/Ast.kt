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
 * Strings can be enclosed in single or double quotes.
 * The value stored here has quotes stripped and escape sequences processed.
 *
 * Examples: "hello", 'world'
 */
data class StringLiteral(
    val value: String,
) : Expression()

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
