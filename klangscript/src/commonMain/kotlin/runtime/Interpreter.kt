package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.ast.*

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
 * val interpreter = Interpreter()
 * val program = KlangScriptParser.parse("print('hello')")
 * interpreter.execute(program)
 * ```
 */
class Interpreter(
    /** The environment for variable and function storage */
    private val environment: Environment = Environment(),
    /** Optional library loader for import statements */
    private val libraryLoader: LibraryLoader? = null,
    /** Call stack for tracking function calls and generating stack traces */
    private val callStack: CallStack = CallStack(),
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

            // Import statement: load and evaluate library, import symbols
            is ImportStatement -> executeImport(statement)

            // Export statement: mark symbols for export
            is ExportStatement -> executeExport(statement)
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
        // Check if library loader is available
        if (libraryLoader == null) {
            throw ImportError(
                null,
                "Cannot import libraries: no library loader configured",
                location = importStmt.location,
                stackTrace = getStackTrace()
            )
        }

        // Load library source code
        val librarySource = try {
            libraryLoader.loadLibrary(importStmt.libraryName)
        } catch (e: Exception) {
            throw ImportError(
                importStmt.libraryName,
                "Failed to load library: ${e.message}",
                location = importStmt.location,
                stackTrace = getStackTrace()
            )
        }

        // Parse library source code
        val libraryProgram = io.peekandpoke.klang.script.parser.KlangScriptParser.parse(librarySource)

        // Create isolated environment for library evaluation
        // The library environment has the current environment as parent, so it can access
        // native functions and global variables, but its own definitions are isolated
        val libraryEnv = Environment(parent = environment)

        // Execute library in isolated environment
        val libraryInterpreter = Interpreter(libraryEnv, libraryLoader)
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
            environment.define(namespaceAlias, namespaceObject, mutable = true)
        } else if (imports == null) {
            // Wildcard import - import all exports with their original names
            for ((name, value) in exports) {
                environment.define(name, value, mutable = true)
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
                environment.define(localAlias, value, mutable = true)
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
        environment.markExports(exportStmt.exports)
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
    private fun evaluate(expression: Expression): RuntimeValue {
        return when (expression) {
            // Literals evaluate to themselves
            is NumberLiteral -> NumberValue(expression.value)
            is StringLiteral -> StringValue(expression.value)
            is BooleanLiteral -> BooleanValue(expression.value)
            is NullLiteral -> NullValue

            // Identifiers look up variables in the environment
            is Identifier -> environment.get(expression.name, expression.location, getStackTrace())

            // Function calls are delegated to evaluateCall
            is CallExpression -> evaluateCall(expression)

            // Binary operations are delegated to evaluateBinaryOp
            is BinaryOperation -> evaluateBinaryOp(expression)

            // Unary operations are delegated to evaluateUnaryOp
            is UnaryOperation -> evaluateUnaryOp(expression)

            // Member access is delegated to evaluateMemberAccess
            is MemberAccess -> evaluateMemberAccess(expression)

            // Arrow functions create function values with closure
            is ArrowFunction -> FunctionValue(expression.parameters, expression.body, environment)

            // Object literals create object values
            is ObjectLiteral -> evaluateObjectLiteral(expression)
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
                try {
                    // Call native Kotlin function
                    callee.function(args)
                } finally {
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

                    // Create temporary interpreter with function environment and shared call stack
                    val funcInterpreter = Interpreter(funcEnv, libraryLoader, callStack)

                    // Evaluate function body in the new environment
                    funcInterpreter.evaluate(callee.body)
                } finally {
                    callStack.pop()
                }
            }

            is BoundNativeMethod -> {
                // Call the bound native method
                callStack.push("${callee.receiver.qualifiedName}.${callee.methodName}", call.location)
                try {
                    callee.invoker(args)
                } finally {
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
     * Binary operations perform arithmetic on two number operands.
     * Both operands must be numbers; otherwise a TypeError is thrown.
     *
     * Process:
     * 1. Evaluate the left operand
     * 2. Evaluate the right operand
     * 3. Verify both are NumberValues
     * 4. Apply the operator
     * 5. Return the result as NumberValue
     *
     * Supported operators:
     * - ADD (+): Addition
     * - SUBTRACT (-): Subtraction
     * - MULTIPLY (*): Multiplication
     * - DIVIDE (/): Division (throws TypeError on division by zero)
     *
     * @param binOp The binary operation AST node
     * @return The runtime value after applying the operation
     * @throws TypeError if operands are not numbers or division by zero
     *
     * Examples:
     * - 5 + 3 → NumberValue(8.0)
     * - 10 - 4 → NumberValue(6.0)
     * - 3 * 7 → NumberValue(21.0)
     * - 20 / 4 → NumberValue(5.0)
     * - 1 / 0 → TypeError: "Division by zero"
     * - "a" + 1 → TypeError: "Binary ADD operation requires numbers"
     */
    private fun evaluateBinaryOp(binOp: BinaryOperation): RuntimeValue {
        // Evaluate both operands
        val leftValue = evaluate(binOp.left)
        val rightValue = evaluate(binOp.right)

        // Ensure both operands are numbers
        if (leftValue !is NumberValue || rightValue !is NumberValue) {
            throw TypeError(
                "Binary ${binOp.operator} operation requires numbers, got ${leftValue.toDisplayString()} and ${rightValue.toDisplayString()}",
                operation = binOp.operator.toString(),
                location = binOp.location,
                stackTrace = getStackTrace()
            )
        }

        // Perform the operation
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
        }

        return NumberValue(result)
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
            val engine = libraryLoader as? io.peekandpoke.klang.script.KlangScript
            if (engine != null) {
                val extensionMethod = engine.getExtensionMethod(objValue.kClass, memberAccess.property)
                if (extensionMethod != null) {
                    // Return bound method
                    return BoundNativeMethod(
                        methodName = memberAccess.property,
                        receiver = objValue,
                        invoker = { args -> extensionMethod.invoker(objValue.value, args) }
                    )
                }
            }

            // Method not found - throw error with helpful message
            val availableMethods = engine?.getExtensionMethodNames(objValue.kClass) ?: emptyList()
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
     * Get the environment for external access
     *
     * This allows the KlangScript facade to register functions
     * in the interpreter's environment.
     *
     * @return The interpreter's environment
     */
    fun getEnvironment(): Environment = environment
}
