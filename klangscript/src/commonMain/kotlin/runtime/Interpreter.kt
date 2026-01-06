package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.ast.*

/**
 * Tree-walking interpreter for KlangScript
 *
 * This interpreter executes KlangScript programs by walking the AST and
 * evaluating nodes recursively. It's called "tree-walking" because it
 * traverses the syntax tree directly without compiling to bytecode.
 *
 * Execution flow:
 * 1. Parse source code → AST (done by parser)
 * 2. Walk the AST and evaluate each node
 * 3. Return the final result
 *
 * The interpreter uses a visitor pattern, with separate methods for
 * statements and expressions. All expressions evaluate to RuntimeValues.
 *
 * Example:
 * ```kotlin
 * val interpreter = Interpreter()
 * val program = KlangScriptParser.parse("print('hello')")
 * interpreter.execute(program)
 * ```
 */
class Interpreter(
    /** The environment for variable and function storage */
    private val environment: Environment = Environment(),
) {
    /**
     * Execute a complete program
     *
     * Executes each statement in the program sequentially.
     * The value of the last statement is returned as the program's result.
     *
     * @param program The parsed AST program to execute
     * @return The runtime value of the last statement, or NullValue if empty
     *
     * Example:
     * ```
     * // Script: print("a"); print("b"); 42
     * // Returns: NumberValue(42.0)
     * ```
     */
    fun execute(program: Program): RuntimeValue {
        var lastValue: RuntimeValue = NullValue

        for (statement in program.statements) {
            lastValue = executeStatement(statement)
        }

        return lastValue
    }

    /**
     * Execute a single statement
     *
     * Statements represent actions in the program but may also produce values.
     * Currently only expression statements are supported.
     *
     * @param statement The statement to execute
     * @return The runtime value produced by the statement
     */
    private fun executeStatement(statement: Statement): RuntimeValue {
        return when (statement) {
            // Expression statement: evaluate the expression
            is ExpressionStatement -> evaluate(statement.expression)
            // Future: let declarations, return statements, etc.
        }
    }

    /**
     * Evaluate an expression to a runtime value
     *
     * This is the core of the interpreter. It recursively evaluates
     * expressions by pattern matching on the expression type.
     *
     * @param expression The expression AST node to evaluate
     * @return The runtime value that the expression evaluates to
     */
    private fun evaluate(expression: Expression): RuntimeValue {
        return when (expression) {
            // Literals evaluate to themselves
            is NumberLiteral -> NumberValue(expression.value)
            is StringLiteral -> StringValue(expression.value)

            // Identifiers look up variables in the environment
            is Identifier -> environment.get(expression.name)

            // Function calls are delegated to evaluateCall
            is CallExpression -> evaluateCall(expression)
            // Future: binary operations, member access, arrow functions, etc.
        }
    }

    /**
     * Evaluate a function call expression
     *
     * Process:
     * 1. Evaluate the callee (what we're calling) expression
     * 2. Verify it's actually a function
     * 3. Evaluate all argument expressions
     * 4. Call the function with the evaluated arguments
     * 5. Return the function's result
     *
     * @param call The call expression AST node
     * @return The runtime value returned by the function
     * @throws RuntimeException if callee is not a function
     *
     * Example:
     * ```
     * // Script: print(upper("hello"))
     * // 1. Evaluate callee: "print" → NativeFunctionValue
     * // 2. Evaluate arg: upper("hello")
     * //    2a. Evaluate "upper" → NativeFunctionValue
     * //    2b. Evaluate "hello" → StringValue
     * //    2c. Call upper with ["hello"] → StringValue("HELLO")
     * // 3. Call print with ["HELLO"] → NullValue
     * ```
     */
    private fun evaluateCall(call: CallExpression): RuntimeValue {
        // Evaluate what we're calling (usually an identifier)
        val callee = evaluate(call.callee)

        // Ensure it's a function
        if (callee !is NativeFunctionValue) {
            throw RuntimeException("Cannot call non-function value")
        }

        // Evaluate all arguments left-to-right
        val args = call.arguments.map { evaluate(it) }

        // Call the function and return its result
        return callee.function(args)
    }

    /**
     * Get the environment for external access
     *
     * This allows the KlangScript facade to register functions
     * in the interpreter's environment.
     *
     * @return The interpreter's environment
     */
    fun getEnvironment(): Environment = environment
}
