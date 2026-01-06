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
     *
     * @param statement The statement to execute
     * @return The runtime value produced by the statement
     */
    private fun executeStatement(statement: Statement): RuntimeValue {
        return when (statement) {
            // Expression statement: evaluate the expression
            is ExpressionStatement -> evaluate(statement.expression)

            // Let declaration: create mutable variable
            is LetDeclaration -> {
                val value = if (statement.initializer != null) {
                    evaluate(statement.initializer)
                } else {
                    NullValue
                }
                environment.define(statement.name, value, mutable = true)
                NullValue  // Declarations don't produce values
            }

            // Const declaration: create immutable variable
            is ConstDeclaration -> {
                val value = evaluate(statement.initializer)
                environment.define(statement.name, value, mutable = false)
                NullValue  // Declarations don't produce values
            }
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

            // Binary operations are delegated to evaluateBinaryOp
            is BinaryOperation -> evaluateBinaryOp(expression)

            // Member access is delegated to evaluateMemberAccess
            is MemberAccess -> evaluateMemberAccess(expression)

            // Arrow functions create function values with closure
            is ArrowFunction -> FunctionValue(expression.parameters, expression.body, environment)
        }
    }

    /**
     * Evaluate a function call expression
     *
     * Handles both native Kotlin functions and script-defined arrow functions.
     *
     * Process:
     * 1. Evaluate the callee (what we're calling) expression
     * 2. Verify it's actually a function
     * 3. Evaluate all argument expressions
     * 4. Call the function with the evaluated arguments
     * 5. Return the function's result
     *
     * For script functions (arrow functions):
     * - Create a new environment extending the function's closure
     * - Bind parameters to argument values in the new environment
     * - Evaluate the function body in the new environment
     * - Restore the previous environment
     *
     * @param call The call expression AST node
     * @return The runtime value returned by the function
     * @throws RuntimeException if callee is not a function or argument count mismatch
     *
     * Example (native function):
     * ```
     * // Script: print("hello")
     * // 1. Evaluate "print" → NativeFunctionValue
     * // 2. Evaluate "hello" → StringValue
     * // 3. Call native function → NullValue
     * ```
     *
     * Example (script function):
     * ```
     * // Script: (x => x + 1)(5)
     * // 1. Evaluate arrow function → FunctionValue
     * // 2. Evaluate 5 → NumberValue(5.0)
     * // 3. Create new environment with x=5
     * // 4. Evaluate body: x + 1 → NumberValue(6.0)
     * ```
     */
    private fun evaluateCall(call: CallExpression): RuntimeValue {
        // Evaluate what we're calling (usually an identifier)
        val callee = evaluate(call.callee)

        // Evaluate all arguments left-to-right
        val args = call.arguments.map { evaluate(it) }

        // Handle different function types
        return when (callee) {
            is NativeFunctionValue -> {
                // Call native Kotlin function
                callee.function(args)
            }

            is FunctionValue -> {
                // Call script-defined arrow function

                // Verify argument count matches parameter count
                if (args.size != callee.parameters.size) {
                    throw RuntimeException(
                        "Function expects ${callee.parameters.size} arguments, got ${args.size}"
                    )
                }

                // Create new environment extending the function's closure
                val funcEnv = Environment(callee.closureEnv)

                // Bind parameters to arguments
                callee.parameters.zip(args).forEach { (param, arg) ->
                    funcEnv.define(param, arg)
                }

                // Create temporary interpreter with function environment to evaluate body
                val funcInterpreter = Interpreter(funcEnv)

                // Evaluate function body in the new environment
                funcInterpreter.evaluate(callee.body)
            }

            else -> {
                throw RuntimeException("Cannot call non-function value: ${callee.toDisplayString()}")
            }
        }
    }

    /**
     * Evaluate a binary operation expression
     *
     * Process:
     * 1. Evaluate the left operand
     * 2. Evaluate the right operand
     * 3. Perform the operation based on the operator
     * 4. Return the result
     *
     * Currently only supports arithmetic on numbers.
     * Type checking ensures both operands are numbers.
     *
     * @param binOp The binary operation AST node
     * @return The runtime value result of the operation
     * @throws RuntimeException if operands are not numbers
     *
     * Example:
     * ```
     * // Script: 10 + 2 * 3
     * // AST: BinaryOp(10, ADD, BinaryOp(2, MULTIPLY, 3))
     * // 1. Evaluate left: 10 → NumberValue(10.0)
     * // 2. Evaluate right: 2 * 3
     * //    2a. Evaluate 2 → NumberValue(2.0)
     * //    2b. Evaluate 3 → NumberValue(3.0)
     * //    2c. Multiply: 2.0 * 3.0 = 6.0
     * // 3. Add: 10.0 + 6.0 = 16.0
     * ```
     */
    private fun evaluateBinaryOp(binOp: BinaryOperation): RuntimeValue {
        // Evaluate both operands
        val leftValue = evaluate(binOp.left)
        val rightValue = evaluate(binOp.right)

        // Ensure both operands are numbers
        if (leftValue !is NumberValue || rightValue !is NumberValue) {
            throw RuntimeException("Binary operations only supported on numbers")
        }

        // Perform the operation
        val result = when (binOp.operator) {
            BinaryOperator.ADD -> leftValue.value + rightValue.value
            BinaryOperator.SUBTRACT -> leftValue.value - rightValue.value
            BinaryOperator.MULTIPLY -> leftValue.value * rightValue.value
            BinaryOperator.DIVIDE -> {
                // Check for division by zero
                if (rightValue.value == 0.0) {
                    throw RuntimeException("Division by zero")
                }
                leftValue.value / rightValue.value
            }
        }

        return NumberValue(result)
    }

    /**
     * Evaluate a member access expression
     *
     * Member access (dot notation) allows accessing properties on objects.
     * This is essential for method chaining and object-oriented patterns.
     *
     * Process:
     * 1. Evaluate the object expression
     * 2. Verify it's an ObjectValue
     * 3. Return the property value (or NullValue if missing)
     *
     * @param memberAccess The member access AST node
     * @return The runtime value of the property
     * @throws RuntimeException if the object is not an ObjectValue
     *
     * Example:
     * ```
     * // Script: obj.name
     * // 1. Evaluate obj → ObjectValue with properties
     * // 2. Access "name" property
     * // 3. Return the property's value (or NullValue)
     * ```
     *
     * Method chaining example:
     * ```
     * // Script: note("c").gain(0.5)
     * // Parsed as: CallExpression(MemberAccess(CallExpression(note, ["c"]), "gain"), [0.5])
     * // 1. Evaluate note("c") → ObjectValue with "gain" property
     * // 2. Access "gain" property → NativeFunctionValue
     * // 3. Call function evaluates in evaluateCall
     * ```
     */
    private fun evaluateMemberAccess(memberAccess: MemberAccess): RuntimeValue {
        // Evaluate the object expression
        val objValue = evaluate(memberAccess.obj)

        // Ensure it's an object
        if (objValue !is ObjectValue) {
            throw RuntimeException("Cannot access property '${memberAccess.property}' on non-object value: ${objValue.toDisplayString()}")
        }

        // Return the property value
        return objValue.getProperty(memberAccess.property)
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
