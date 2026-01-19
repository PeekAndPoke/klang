package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.ast.*
import io.peekandpoke.klang.script.parser.KlangScriptParser

/**
 * Interface for loading library source code
 *
 * This allows the interpreter to load libraries without directly depending
 * on the KlangScript engine class.
 */
interface LibraryLoader {
    /**
     * Load library source code by name
     *
     * @param name The library name
     * @return The KlangScript source code for the library
     * @throws RuntimeException if the library is not found
     */
    fun loadLibrary(name: String): String
}

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
 * val context = ExecutionContext(sourceName = "main.klang")
 * val interpreter = Interpreter(executionContext = context, engine = engine)
 * val program = KlangScriptParser.parse("print('hello')")
 * interpreter.execute(program)
 * ```
 */
class Interpreter(
    /** The environment for variable and function storage */
    private val env: Environment = Environment(),
    /** Optional library loader for import statements */
    private val engine: KlangScriptEngine,
    /** Call stack for tracking function calls and generating stack traces */
    private val callStack: CallStack = CallStack(),
    /** Execution context with source metadata and current location */
    private val executionContext: ExecutionContext,
) {
    /**
     * Get the current stack trace
     *
     * Returns a snapshot of the current call stack for error reporting.
     */
    private fun getStackTrace(): List<CallStackFrame> = callStack.getFrames()

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
     * @throws ReturnException if a return statement is encountered
     */
    internal fun executeStatement(statement: Statement): RuntimeValue {
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
                env.define(statement.name, value, mutable = true)
                NullValue  // Declarations don't produce values
            }

            // Const declaration: create immutable variable
            is ConstDeclaration -> {
                val value = evaluate(statement.initializer)
                env.define(statement.name, value, mutable = false)
                NullValue  // Declarations don't produce values
            }

            // Import statement: load and evaluate library, import symbols
            is ImportStatement -> executeImport(statement)

            // Export statement: mark symbols for export
            is ExportStatement -> executeExport(statement)

            // Return statement: throw ReturnException to exit function
            is ReturnStatement -> {
                val returnValue = if (statement.value != null) {
                    evaluate(statement.value)
                } else {
                    NullValue
                }
                throw ReturnException(returnValue)
            }
        }
    }

    /**
     * Execute an import statement
     *
     * Process:
     * 1. Load the library source code from the library loader
     * 2. Parse the library source code
     * 3. Create an isolated environment for library evaluation
     * 4. Execute the library in the isolated environment
     * 5. Import all top-level symbols from the library environment into current scope
     *
     * @param importStmt The import statement AST node
     * @return NullValue (imports don't produce values)
     * @throws RuntimeException if library loader is not available or library not found
     *
     * Example:
     * ```javascript
     * // strudel.klang library:
     * let note = (pattern) => { /* ... */ }
     * let sound = (pattern) => { /* ... */ }
     *
     * // User script:
     * import * from "strudel"
     * note("a b c")  // note() is now available
     * ```
     */
    private fun executeImport(importStmt: ImportStatement): RuntimeValue {
        // Load library source code
        val librarySource = try {
            engine.loadLibrary(importStmt.libraryName)
        } catch (e: Exception) {
            throw ImportError(
                importStmt.libraryName,
                "Failed to load library: ${e.message}",
                location = importStmt.location,
                stackTrace = getStackTrace()
            )
        }

        // Parse library source code
        val libraryProgram = KlangScriptParser.parse(librarySource)

        // Create an isolated environment / scope for library evaluation,
        // starting with the native environment of the engine.
        val libraryEnv = Environment(parent = engine.nativeEnvironment)

        // Execute the library in an isolated environment with shared execution context
        val libraryInterpreter = Interpreter(
            env = libraryEnv,
            engine = engine,
            callStack = CallStack(),
            executionContext = executionContext
        )
        libraryInterpreter.execute(libraryProgram)

        // Import symbols from library environment into current environment
        importSymbolsFromEnvironment(libraryEnv, importStmt.imports, importStmt.namespaceAlias, importStmt.libraryName)

        return NullValue  // Imports don't produce values
    }

    /**
     * Import symbols from a library environment into the current environment
     *
     * This copies exported symbols from the library into the current scope.
     * Supports wildcard, namespace, and selective imports with optional aliasing.
     *
     * @param libraryEnv The library environment containing symbols to import
     * @param imports List of (exportName, localAlias) pairs for selective imports (null for wildcard)
     * @param namespaceAlias If set, creates a namespace object instead of importing into current scope
     * @param libraryName The name of the library being imported (for error messages)
     */
    private fun importSymbolsFromEnvironment(
        libraryEnv: Environment,
        imports: List<Pair<String, String>>?,
        namespaceAlias: String?,
        libraryName: String,
    ) {
        // Get exported symbols from library
        val exports = libraryEnv.getExportedSymbols()

        if (namespaceAlias != null) {
            // Namespace import - create an object containing all exports
            if (imports != null) {
                throw ImportError(
                    null,
                    "Cannot use namespace import with selective imports",
                    location = null,
                    stackTrace = getStackTrace()
                )
            }
            val namespaceObject = ObjectValue(exports.toMutableMap())
            env.define(namespaceAlias, namespaceObject, mutable = true)
        } else if (imports == null) {
            // Wildcard import - import all exports with their original names
            for ((name, value) in exports) {
                env.define(name, value, mutable = true)
            }
        } else {
            // Selective import - import only specified names, applying aliases
            val exportNames = imports.map { it.first }
            val missingExports = exportNames.filter { it !in exports }
            if (missingExports.isNotEmpty()) {
                throw ImportError(
                    libraryName,
                    "Cannot import non-exported symbols: ${missingExports.joinToString()}",
                    location = null,
                    stackTrace = getStackTrace()
                )
            }

            // Import each symbol with its alias
            for ((exportName, localAlias) in imports) {
                val value = exports[exportName]!!  // Safe because we validated above
                env.define(localAlias, value, mutable = true)
            }
        }
    }

    /**
     * Execute an export statement
     *
     * Marks the specified symbols as exported from the current environment,
     * optionally with different names (aliasing).
     * Only exported symbols will be accessible when this code is loaded as a library.
     *
     * @param exportStmt The export statement AST node
     * @return NullValue (exports don't produce values)
     *
     * Example:
     * ```javascript
     * let add = (a, b) => a + b
     * let subtract = (a, b) => a - b
     * let internal = (x) => x * 2  // Not exported
     * export { add, subtract }  // Export with original names
     *
     * // Or with aliases:
     * export { add as sum }  // Export 'add' as 'sum'
     * ```
     */
    private fun executeExport(exportStmt: ExportStatement): RuntimeValue {
        // Mark the symbols as exported in the environment with their aliases
        env.markExports(exportStmt.exports)
        return NullValue  // Exports don't produce values
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
    fun evaluate(expression: Expression): RuntimeValue {
        return when (expression) {
            // Literals evaluate to themselves
            is NumberLiteral -> NumberValue(expression.value)
            is StringLiteral -> StringValue(expression.value)
            is BooleanLiteral -> BooleanValue(expression.value)
            is NullLiteral -> NullValue

            // Identifiers look up variables in the environment
            is Identifier -> env.get(expression.name, expression.location, getStackTrace())

            // Function calls are delegated to evaluateCall
            is CallExpression -> evaluateCall(expression)

            // Binary operations are delegated to evaluateBinaryOp
            is BinaryOperation -> evaluateBinaryOp(expression)

            // Unary operations are delegated to evaluateUnaryOp
            is UnaryOperation -> evaluateUnaryOp(expression)

            // Member access is delegated to evaluateMemberAccess
            is MemberAccess -> evaluateMemberAccess(expression)

            // Arrow functions create function values with closure
            is ArrowFunction -> FunctionValue(
                parameters = expression.parameters,
                body = expression.body,
                closureEnv = env,
                engine = engine,
            )

            // Object literals create object values
            is ObjectLiteral -> evaluateObjectLiteral(expression)

            // Array literals create array values
            is ArrayLiteral -> evaluateArrayLiteral(expression)
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
                // Push native function onto call stack
                callStack.push("<native>", call.location)

                // Update execution context with current call location
                val previousLocation = executionContext.currentLocation
                executionContext.currentLocation = call.location

                try {
                    // Call native Kotlin function with location
                    callee.function(args, call.location)
                } finally {
                    executionContext.currentLocation = previousLocation
                    callStack.pop()
                }
            }

            is FunctionValue -> {
                // Verify argument count matches parameter count
                if (args.size != callee.parameters.size) {
                    throw ArgumentError(
                        functionName = "<anonymous function>",
                        message = "Function expects ${callee.parameters.size} arguments, got ${args.size}",
                        expected = callee.parameters.size,
                        actual = args.size,
                        location = call.location,
                        stackTrace = getStackTrace()
                    )
                }

                // Push function onto call stack
                callStack.push("<anonymous>", call.location)
                try {
                    // Create new environment extending the function's closure
                    val funcEnv = Environment(callee.closureEnv)

                    // Bind parameters to arguments
                    callee.parameters.zip(args).forEach { (param, arg) ->
                        funcEnv.define(param, arg)
                    }

                    // Create temporary interpreter with function environment, shared call stack, and execution context
                    val funcInterpreter = Interpreter(funcEnv, engine, callStack, executionContext)

                    // Evaluate function body based on type
                    when (val body = callee.body) {
                        is ArrowFunctionBody.ExpressionBody -> {
                            // Expression body: implicitly return the expression value
                            funcInterpreter.evaluate(body.expression)
                        }

                        is ArrowFunctionBody.BlockBody -> {
                            // Block body: execute statements, catch return exception
                            try {
                                for (stmt in body.statements) {
                                    funcInterpreter.executeStatement(stmt)
                                }
                                // If no return statement was encountered, return NullValue
                                NullValue
                            } catch (e: ReturnException) {
                                // Return statement was encountered, return its value
                                e.value
                            }
                        }
                    }
                } finally {
                    callStack.pop()
                }
            }

            is BoundNativeMethod -> {
                // Call the bound native method
                callStack.push("${callee.receiver.qualifiedName}.${callee.methodName}", call.location)

                // Update execution context with current call location
                val previousLocation = executionContext.currentLocation
                executionContext.currentLocation = call.location

                try {
                    callee.invoker(args, call.location)
                } finally {
                    executionContext.currentLocation = previousLocation
                    callStack.pop()
                }
            }

            else -> {
                throw TypeError(
                    "Cannot call non-function value: ${callee.toDisplayString()}",
                    operation = "function call",
                    location = call.location,
                    stackTrace = getStackTrace()
                )
            }
        }
    }

    /**
     * Evaluate a unary operation expression
     *
     * Handles prefix operators: -, +, !
     *
     * Process:
     * 1. Evaluate the operand expression
     * 2. Apply the operator based on its type
     * 3. Return the result
     *
     * Operator semantics:
     * - NEGATE: Flips the sign of a number (requires NumberValue)
     * - PLUS: Identity operation, returns the number unchanged (requires NumberValue)
     * - NOT: Logical negation (converts to boolean, then negates)
     *
     * @param unaryOp The unary operation AST node
     * @return The runtime value after applying the operator
     * @throws RuntimeException if type mismatch occurs
     *
     * Examples:
     * - -5 → NumberValue(-5.0)
     * - +42 → NumberValue(42.0)
     * - !true → BooleanValue(false)
     * - !null → BooleanValue(true)
     */
    private fun evaluateUnaryOp(unaryOp: UnaryOperation): RuntimeValue {
        val operandValue = evaluate(unaryOp.operand)

        return when (unaryOp.operator) {
            UnaryOperator.NEGATE -> {
                if (operandValue !is NumberValue) {
                    throw TypeError(
                        "Negation operator requires a number, got ${operandValue.toDisplayString()}",
                        operation = "unary -",
                        location = unaryOp.location,
                        stackTrace = getStackTrace()
                    )
                }
                NumberValue(-operandValue.value)
            }

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
                val boolValue = when (operandValue) {
                    is BooleanValue -> operandValue.value
                    is NullValue -> false
                    is NumberValue -> operandValue.value != 0.0 && !operandValue.value.isNaN()
                    is StringValue -> operandValue.value.isNotEmpty()
                    else -> true // Objects and functions are truthy
                }
                BooleanValue(!boolValue)
            }
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
     * - 5 > 3 → BooleanValue(true)
     * - 10 == 10 → BooleanValue(true)
     * - "a" == "a" → BooleanValue(true)
     * - 1 / 0 → TypeError: "Division by zero"
     */
    private fun evaluateBinaryOp(binOp: BinaryOperation): RuntimeValue {
        // Evaluate both operands
        val leftValue = evaluate(binOp.left)
        val rightValue = evaluate(binOp.right)

        // Handle comparison operators
        when (binOp.operator) {
            BinaryOperator.EQUAL -> {
                return BooleanValue(valuesEqual(leftValue, rightValue))
            }

            BinaryOperator.NOT_EQUAL -> {
                return BooleanValue(!valuesEqual(leftValue, rightValue))
            }

            BinaryOperator.LESS_THAN,
            BinaryOperator.LESS_THAN_OR_EQUAL,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.GREATER_THAN_OR_EQUAL,
                -> {
                // Numeric comparison operators require both operands to be numbers
                if (leftValue !is NumberValue || rightValue !is NumberValue) {
                    throw TypeError(
                        "Binary ${binOp.operator} operation requires numbers, " +
                                "got ${leftValue.toDisplayString()} and ${rightValue.toDisplayString()}",
                        operation = binOp.operator.toString(),
                        location = binOp.location,
                        stackTrace = getStackTrace()
                    )
                }
                val result = when (binOp.operator) {
                    BinaryOperator.LESS_THAN -> leftValue.value < rightValue.value
                    BinaryOperator.LESS_THAN_OR_EQUAL -> leftValue.value <= rightValue.value
                    BinaryOperator.GREATER_THAN -> leftValue.value > rightValue.value
                    BinaryOperator.GREATER_THAN_OR_EQUAL -> leftValue.value >= rightValue.value
                    else -> error("Unexpected comparison operator: ${binOp.operator}")
                }
                return BooleanValue(result)
            }

            else -> {
                // Arithmetic operators - ensure both operands are numbers
                if (leftValue !is NumberValue || rightValue !is NumberValue) {
                    throw TypeError(
                        "Binary ${binOp.operator} operation requires numbers, " +
                                "got ${leftValue.toDisplayString()} and ${rightValue.toDisplayString()}",
                        operation = binOp.operator.toString(),
                        location = binOp.location,
                        stackTrace = getStackTrace()
                    )
                }

                // Perform the arithmetic operation
                val result = when (binOp.operator) {
                    BinaryOperator.ADD -> leftValue.value + rightValue.value
                    BinaryOperator.SUBTRACT -> leftValue.value - rightValue.value
                    BinaryOperator.MULTIPLY -> leftValue.value * rightValue.value
                    BinaryOperator.DIVIDE -> {
                        // Check for division by zero
                        if (rightValue.value == 0.0) {
                            throw TypeError(
                                "Division by zero",
                                operation = "division",
                                location = binOp.location,
                                stackTrace = getStackTrace()
                            )
                        }
                        leftValue.value / rightValue.value
                    }

                    else -> error("Unexpected arithmetic operator: ${binOp.operator}")
                }

                return NumberValue(result)
            }
        }
    }

    /**
     * Compare two runtime values for equality
     *
     * Implements value equality semantics:
     * - Numbers: compare by value
     * - Strings: compare by content
     * - Booleans: compare by value
     * - Null: null == null is true
     * - Different types: always false
     * - Objects/Arrays: compare by reference (same instance)
     *
     * @param left First value
     * @param right Second value
     * @return true if values are equal, false otherwise
     */
    private fun valuesEqual(left: RuntimeValue, right: RuntimeValue): Boolean {
        return when {
            // Both null
            left is NullValue && right is NullValue -> true
            // Both numbers
            left is NumberValue && right is NumberValue -> left.value == right.value
            // Both strings
            left is StringValue && right is StringValue -> left.value == right.value
            // Both booleans
            left is BooleanValue && right is BooleanValue -> left.value == right.value
            // Same object reference
            left is ObjectValue && right is ObjectValue -> left === right
            // Same array reference
            left is ArrayValue && right is ArrayValue -> left === right
            // Different types or values
            else -> false
        }
    }

    /**
     * Evaluate a member access expression
     *
     * Member access (dot notation) allows accessing properties on both
     * script objects and native Kotlin objects. This enables method chaining
     * and object-oriented patterns.
     *
     * Process:
     * 1. Evaluate the object expression
     * 2. If native object: Look up extension method, return BoundNativeMethod
     * 3. If script object: Return the property value (or NullValue if missing)
     * 4. Otherwise: Throw TypeError
     *
     * @param memberAccess The member access AST node
     * @return The runtime value of the property or bound method
     * @throws TypeError if the object doesn't support member access or property not found
     *
     * Examples:
     * ```
     * // Script object:
     * let obj = { name: "test", value: 42 }
     * obj.name  // Returns StringValue("test")
     *
     * // Native object:
     * let pattern = note("a b c")  // Returns NativeObjectValue<StrudelPattern>
     * pattern.sound  // Returns BoundNativeMethod for sound() extension
     *
     * // Method chaining:
     * note("c d e").sound("saw").gain(0.5)
     * // Each dot returns next method in chain
     * ```
     */
    private fun evaluateMemberAccess(memberAccess: MemberAccess): RuntimeValue {
        // Evaluate the object expression
        val objValue = evaluate(memberAccess.obj)

        // Handle native objects - lookup extension methods
        if (objValue is NativeObjectValue<*>) {
            val extensionMethod = engine.getExtensionMethod(objValue, memberAccess.property)
            if (extensionMethod != null) {
                // Return bound method
                return BoundNativeMethod(
                    methodName = memberAccess.property,
                    receiver = objValue,
                    invoker = { args, location -> extensionMethod.invoker(objValue.value, args, location) }
                )
            }

            // Method not found - throw error with helpful message
            val availableMethods = engine.getExtensionMethodNames(objValue)
            val suggestion = if (availableMethods.isNotEmpty()) {
                " Available methods: ${availableMethods.joinToString(", ")}"
            } else {
                ""
            }

            throw TypeError(
                "Native type '${objValue.qualifiedName}' has no method '${memberAccess.property}'.$suggestion",
                operation = "member access",
                location = memberAccess.location,
                stackTrace = getStackTrace()
            )
        }
        // Handle built-in runtime types (ArrayValue, StringValue, etc.) - lookup extension methods
        else if (objValue is ArrayValue || objValue is StringValue || objValue is NumberValue) {
            val extensionMethod = engine.getExtensionMethod(objValue, memberAccess.property)
            if (extensionMethod != null) {
                // Return bound method
                return BoundNativeMethod(
                    methodName = memberAccess.property,
                    receiver = NativeObjectValue.fromValue(objValue),
                    invoker = { args, location -> extensionMethod.invoker(objValue, args, location) }
                )
            }

            // If there are registered extension methods for this type but the requested one doesn't exist,
            // throw a helpful error. Otherwise, fall through to the generic error below.
            val availableMethods = engine.getExtensionMethodNames(objValue)
            if (availableMethods.isNotEmpty()) {
                val typeName = objValue::class.simpleName ?: "unknown"
                val suggestion = " Available methods: ${availableMethods.joinToString(", ")}"

                throw TypeError(
                    "Type '$typeName' has no method '${memberAccess.property}'.$suggestion",
                    operation = "member access",
                    location = memberAccess.location,
                    stackTrace = getStackTrace()
                )
            }
            // Otherwise, fall through to generic error handling
        }

        // Handle script objects
        if (objValue !is ObjectValue) {
            throw TypeError(
                "Cannot access property '${memberAccess.property}' on non-object value: ${objValue.toDisplayString()}",
                operation = "member access",
                location = memberAccess.location,
                stackTrace = getStackTrace()
            )
        }

        // Return the property value
        return objValue.getProperty(memberAccess.property)
    }

    /**
     * Evaluate an object literal expression
     *
     * Object literals create new ObjectValue instances with properties
     * evaluated in the current environment.
     *
     * Process:
     * 1. Create empty ObjectValue
     * 2. For each property: evaluate the value expression
     * 3. Store the property in the object
     * 4. Return the object
     *
     * @param objectLiteral The object literal AST node
     * @return An ObjectValue with all properties evaluated
     *
     * Examples:
     * ```
     * // Script: { x: 10, y: 20 }
     * // Creates ObjectValue with x=10, y=20
     *
     * // Script: { name: getName(), age: 30 }
     * // Evaluates getName() first, then creates object
     *
     * // Script: { nested: { inner: 42 } }
     * // Recursively evaluates nested object literal
     * ```
     */
    private fun evaluateObjectLiteral(objectLiteral: ObjectLiteral): RuntimeValue {
        val properties = mutableMapOf<String, RuntimeValue>()

        // Evaluate each property value
        for ((key, valueExpr) in objectLiteral.properties) {
            val value = evaluate(valueExpr)
            properties[key] = value
        }

        return ObjectValue(properties)
    }

    /**
     * Evaluate an array literal expression
     *
     * Array literals create new ArrayValue instances at runtime.
     * Each element expression is evaluated and the results are collected into a mutable list.
     *
     * **Process:**
     * 1. Iterate through all element expressions in the array literal
     * 2. Evaluate each element expression in the current environment
     * 3. Collect all evaluated values into a mutable list
     * 4. Return an ArrayValue containing the elements
     *
     * **Supports:**
     * - Empty arrays: `[]` produces `ArrayValue(mutableListOf())`
     * - Any expression types: literals, variables, function calls, nested arrays, objects
     * - Nested evaluation: `[[1, 2], [3, 4]]` recursively evaluates inner arrays
     *
     * @param arrayLiteral The array literal AST node to evaluate
     * @return ArrayValue containing the evaluated element values
     *
     * Examples:
     * ```javascript
     * // Simple array
     * [1, 2, 3] -> ArrayValue([NumberValue(1), NumberValue(2), NumberValue(3)])
     *
     * // Mixed types
     * [1, "hello", true] -> ArrayValue([NumberValue(1), StringValue("hello"), BooleanValue(true)])
     *
     * // Expressions
     * let x = 5
     * [x, x + 1, x * 2] -> ArrayValue([NumberValue(5), NumberValue(6), NumberValue(10)])
     *
     * // Nested arrays
     * [[1, 2], [3, 4]] -> ArrayValue([
     *     ArrayValue([NumberValue(1), NumberValue(2)]),
     *     ArrayValue([NumberValue(3), NumberValue(4)])
     * ])
     *
     * // Arrays with function calls
     * [getValue(), calculate()] // Calls both functions, stores results
     * ```
     */
    private fun evaluateArrayLiteral(arrayLiteral: ArrayLiteral): RuntimeValue {
        val elements = mutableListOf<RuntimeValue>()

        // Evaluate each element expression
        for (elementExpr in arrayLiteral.elements) {
            val value = evaluate(elementExpr)
            elements.add(value)
        }

        return ArrayValue(elements)
    }

    /**
     * Get the environment for external access
     *
     * This allows the KlangScript facade to register functions
     * in the interpreter's environment.
     *
     * @return The interpreter's environment
     */
    fun getEnvironment(): Environment = env
}
