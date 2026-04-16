package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.runtime.CallArgs.Companion.resolve
import kotlin.reflect.KClass

/**
 * Metadata about a registered native type
 *
 * @property kClass The Kotlin class of the native type
 * @property qualifiedName The fully qualified class name
 */
data class NativeTypeInfo(
    val kClass: KClass<*>,
    val qualifiedName: String,
)

/**
 * Wrapper for an extension method registered on a native type
 *
 * @property methodName The name of the method
 * @property receiverClass The Kotlin class this method is registered on
 * @property invoker Function that invokes the extension method with receiver, arguments, and source location
 */
data class NativeExtensionMethod(
    val methodName: String,
    val receiverClass: KClass<*>,
    val invoker: (receiver: Any, args: List<RuntimeValue>, location: SourceLocation?, engine: KlangScriptEngine) -> RuntimeValue,
    /**
     * Optional declared parameters. Drives named-arg resolution through the
     * interpreter when the user calls this extension method by name.
     * Receiver is NOT included in this list — it's supplied separately by the
     * binding logic.
     */
    val paramSpecs: List<ParamSpec>? = null,
)

/**
 * Check if the number of arguments matches the expected count.
 *
 * @param fn Function name for error reporting
 * @param args Actual arguments received
 * @param expected Minimum expected argument count
 * @param location Optional source location for error reporting
 * @throws KlangScriptArgumentError if too few arguments were provided
 */
fun checkArgsSize(fn: String, args: List<RuntimeValue>, expected: Int, location: SourceLocation? = null) {
    if (args.size < expected) {
        throw KlangScriptArgumentError(
            functionName = fn,
            message = "Call to function $fn expected $expected arguments but got ${args.size}",
            expected = expected,
            actual = args.size,
            location = location,
        )
    }
}

/**
 * Convert a RuntimeValue to a Kotlin type.
 *
 * @param cls Target Kotlin class to convert to
 * @param loc Optional source location for error reporting
 * @return The converted value as the target type
 * @throws KlangScriptTypeError if conversion is not possible
 */
fun <T : Any> RuntimeValue.convertToKotlin(cls: KClass<T>, loc: SourceLocation? = null): T {
    // println("Converting ${this::class.simpleName} to ${cls.simpleName}")

    val result = when (this) {
        // Special conversion logic for numeric values
        is NumberValue -> when (cls) {
            Double::class -> value
            Float::class -> value.toFloat()
            Int::class -> value.toInt()
            Short::class -> value.toInt().toShort()
            Byte::class -> value.toInt().toByte()
            else -> value
        }

        is BooleanValue -> when (cls) {
            Boolean::class -> value
            else -> value
        }

        is FunctionValue -> this.convertFunctionToKotlin()

        is NativeFunctionValue -> {
            @Suppress("RedundantLambdaArrow") { ->
                val result = function(emptyList(), null)
                result.value
            }
        }

        is ArrayValue -> when (cls) {
            DoubleArray::class -> elements.map { it.convertToKotlin(Double::class) }.toDoubleArray()
            FloatArray::class -> elements.map { it.convertToKotlin(Float::class) }.toFloatArray()
            IntArray::class -> elements.map { it.convertToKotlin(Int::class) }.toIntArray()
            ShortArray::class -> elements.map { it.convertToKotlin(Short::class) }.toShortArray()
            LongArray::class -> elements.map { it.convertToKotlin(Double::class).toLong() }.toLongArray()
            ByteArray::class -> elements.map { it.convertToKotlin(Byte::class) }.toByteArray()
            BooleanArray::class -> elements.map { it.convertToKotlin(Boolean::class) }.toBooleanArray()
            List::class, Collection::class, Iterable::class, Any::class -> elements.map {
                if (it is NullValue) null else it.convertToKotlin(Any::class)
            }

            Array::class -> elements.map {
                if (it is NullValue) null else it.convertToKotlin(Any::class)
            }.toTypedArray()

            else -> value
        }

        else -> {
            val isValid = cls.isInstance(value)

            when (isValid) {
                true -> value
                else -> throw KlangScriptTypeError(
                    message = "Cannot convert ${this::class.simpleName} to ${cls.simpleName}",
                    operation = "parameter conversion",
                    location = loc,
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    return result as T
}

/**
 * Convert a script [FunctionValue] to a Kotlin lambda.
 *
 * Supports functions with 0 to 10 parameters.
 *
 * @return A Kotlin lambda that invokes this script function
 * @throws KlangScriptTypeError if the function has more than 10 parameters
 */
fun <T : Any> FunctionValue.convertFunctionToKotlin(): T {

    val func = this

    @Suppress("UNCHECKED_CAST")
    val fn = when (func.parameters.size) {
        0 -> run {
            // NOTICE: The -> is important. It defines a Function0
            { -> callFunction(listOf(Unit)) } as T
        }

        1 -> { a1: Any? ->
            callFunction(listOf(a1))
        }

        2 -> { a1: Any?, a2: Any? ->
            callFunction(listOf(a1, a2))
        }

        3 -> { a1: Any?, a2: Any?, a3: Any? ->
            callFunction(listOf(a1, a2, a3))
        }

        4 -> { a1: Any?, a2: Any?, a3: Any?, a4: Any? ->
            callFunction(listOf(a1, a2, a3, a4))
        }

        5 -> { a1: Any?, a2: Any?, a3: Any?, a4: Any?, a5: Any? ->
            callFunction(listOf(a1, a2, a3, a4, a5))
        }

        6 -> { a1: Any?, a2: Any?, a3: Any?, a4: Any?, a5: Any?, a6: Any? ->
            callFunction(listOf(a1, a2, a3, a4, a5, a6))
        }

        7 -> { a1: Any?, a2: Any?, a3: Any?, a4: Any?, a5: Any?, a6: Any?, a7: Any? ->
            callFunction(listOf(a1, a2, a3, a4, a5, a6, a7))
        }

        8 -> { a1: Any?, a2: Any?, a3: Any?, a4: Any?, a5: Any?, a6: Any?, a7: Any?, a8: Any? ->
            callFunction(listOf(a1, a2, a3, a4, a5, a6, a7, a8))
        }

        9 -> { a1: Any?, a2: Any?, a3: Any?, a4: Any?, a5: Any?, a6: Any?, a7: Any?, a8: Any?, a9: Any? ->
            callFunction(listOf(a1, a2, a3, a4, a5, a6, a7, a8, a9))
        }

        10 -> { a1: Any?, a2: Any?, a3: Any?, a4: Any?, a5: Any?, a6: Any?, a7: Any?, a8: Any?, a9: Any?, a10: Any? ->
            callFunction(listOf(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10))
        }

        else -> throw KlangScriptTypeError(
            message = "Cannot convert script function to Kotlin. Only functions with up to 10 parameters are supported.",
            operation = "parameter conversion",
            // No location available for function conversion errors
        )
    }

    @Suppress("UNCHECKED_CAST")
    return fn as T
}

/**
 * Invoke this script function with the given raw Kotlin arguments.
 *
 * Wraps arguments as [RuntimeValue]s, creates a call environment,
 * evaluates the function body, and unwraps the result.
 *
 * @param args Raw Kotlin arguments (will be wrapped via [wrapAsRuntimeValue])
 * @return The unwrapped result of the function execution
 */
private fun FunctionValue.callFunction(args: List<Any?>): Any? {
    // 1. Wrap arguments
    val wrappedArgs = args.map { wrapAsRuntimeValue(it) }
    // 2. Create a new environment for this call, extending the closure
    val callEnv = Environment(parent = closureEnv)
    // 3. Bind the arguments to parameters
    parameters.zip(wrappedArgs).forEach { (paramName, argValue) ->
        callEnv.define(paramName, argValue)
    }
    // 4. Create a new Interpreter instance with a default execution context
    // This is used when script functions are called from Kotlin code
    val executionContext = ExecutionContext(sourceName = "native-callback")
    val interpreter = Interpreter(env = callEnv, engine = engine, executionContext = executionContext)
    // 5. Evaluate the body based on type
    val result = when (val functionBody = body) {
        is ArrowFunctionBody.ExpressionBody -> {
            // Expression body: implicitly return the expression value
            interpreter.evaluate(functionBody.expression)
        }

        is ArrowFunctionBody.BlockBody -> {
            // Block body: execute statements, catch return exception
            try {
                for (stmt in functionBody.statements) {
                    interpreter.executeStatement(stmt)
                }
                // If no return statement was encountered, return NullValue
                NullValue
            } catch (e: ReturnException) {
                // Return statement was encountered, return its value
                e.value
            }
        }
    }
    // 6. Unwrap the result
    return result.value
}

/**
 * Convert argument at [index] to Kotlin type. When [nullable] is true, [NullValue] returns null.
 *
 * @param fn Function name for error reporting
 * @param args List of runtime arguments
 * @param index Zero-based argument index
 * @param cls Target Kotlin class to convert to
 * @param loc Optional source location for error reporting
 * @return The converted argument value
 * @throws KlangScriptArgumentError if the argument index is out of bounds
 * @throws KlangScriptTypeError if conversion fails
 */
fun <T : Any> convertArgToKotlin(
    fn: String,
    args: List<RuntimeValue>,
    index: Int,
    cls: KClass<T>,
    nullable: Boolean = false,
    loc: SourceLocation? = null,
): T? {
    val arg = args.getOrNull(index) ?: throw KlangScriptArgumentError(
        functionName = fn,
        message = "Expected argument at index $index",
        expected = null,
        actual = null,
        location = loc,
    )

    if (nullable && arg is NullValue) {
        return null
    }

    return arg.convertToKotlin(cls = cls, loc = loc)
}

/**
 * Wrap a value as a RuntimeValue
 *
 * Automatically wraps Kotlin values in appropriate RuntimeValue types.
 * Native objects are wrapped in NativeObjectValue with KClass and qualified name.
 *
 * @param value The value to wrap (can be null, RuntimeValue, or native object)
 * @return The wrapped RuntimeValue
 */
fun wrapAsRuntimeValue(value: Any?): RuntimeValue {
    return when (value) {
        null -> NullValue
        is RuntimeValue -> value
        is String -> StringValue(value)
        is Double -> NumberValue(value)
        is Int -> NumberValue(value.toDouble())
        is Boolean -> BooleanValue(value)
        // TODO: list -> ArrayValue
        // TODO: Map -> ObjectValue
        else -> {
            // Wrap as native object
            val kClass = value::class
            val qualifiedName = kClass.simpleName ?: "Unknown"  // Use simpleName for multiplatform compatibility
            @Suppress("UNCHECKED_CAST")
            NativeObjectValue(
                kClass = kClass as KClass<Any>,
                qualifiedName = qualifiedName,
                value = value
            )
        }
    }
}

/**
 * Guard a native function call. Re-throws [KlangScriptRuntimeError]s as-is.
 * Wraps any other exception in a [KlangScriptInternalError] with context.
 *
 * @param functionName Name of the native function being called
 * @param args The RuntimeValue arguments passed to the function
 * @param location Source location of the call site
 * @param block The native function body to execute
 */
inline fun guardNativeCall(
    functionName: String,
    args: List<RuntimeValue>,
    location: SourceLocation?,
    block: () -> RuntimeValue,
): RuntimeValue {
    return try {
        block()
    } catch (e: KlangScriptRuntimeError) {
        throw e // already a proper error, re-throw as-is
    } catch (e: Throwable) {
        val argsDesc = args.mapIndexed { i, v -> "p${i + 1}=${v.toDisplayString()}" }.joinToString(", ")
        throw KlangScriptInternalError(
            message = "Internal error in native function '$functionName($argsDesc)': ${e.message ?: e::class.simpleName}",
            cause = e,
            location = location,
        )
    }
}

// ========================================================================
// Named-argument support (Phase 3)
// ========================================================================

/**
 * A single argument evaluated at a call site.
 *
 * The interpreter evaluates each AST [io.peekandpoke.klang.script.ast.Argument]
 * in source order, producing one [EvaluatedArgument] per arg. These are then
 * classified into a [CallArgs] for the all-or-nothing rule.
 */
sealed class EvaluatedArgument {
    abstract val value: RuntimeValue

    /** Positional: bound to the parameter at its index. */
    data class Positional(override val value: RuntimeValue) : EvaluatedArgument()

    /** Named: bound to the parameter matching [name]. */
    data class Named(
        val name: String,
        override val value: RuntimeValue,
        val nameLocation: SourceLocation?,
    ) : EvaluatedArgument()
}

/**
 * Resolved arguments at a call site. A call is either all positional or all
 * named — never mixed. [resolve] enforces this plus "no duplicate named names".
 */
sealed class CallArgs {
    /** Number of supplied arguments, regardless of style. */
    abstract val size: Int

    /** Every argument was positional. */
    data class Positional(val values: List<RuntimeValue>) : CallArgs() {
        override val size: Int get() = values.size
    }

    /** Every argument was named. Preserves insertion order for diagnostics. */
    data class Named(val values: Map<String, RuntimeValue>) : CallArgs() {
        override val size: Int get() = values.size
    }

    /** Zero-arg call — trivially both styles. */
    data object Empty : CallArgs() {
        override val size: Int get() = 0
    }

    companion object {
        /**
         * Classify a source-ordered list of evaluated arguments into a [CallArgs].
         *
         * Throws [KlangScriptArgumentError] if:
         *  - positional and named are mixed at the same call site, OR
         *  - the same name is used twice.
         *
         * Unknown-parameter errors are raised by the callee, which owns the
         * parameter name list — not by this resolver.
         */
        fun resolve(
            functionName: String,
            evaluated: List<EvaluatedArgument>,
            callLocation: SourceLocation?,
            callStackTrace: List<CallStackFrame> = emptyList(),
        ): CallArgs {
            if (evaluated.isEmpty()) return Empty

            val firstKind = evaluated[0]::class
            val firstMismatch = evaluated.firstOrNull { it::class != firstKind }
            if (firstMismatch != null) {
                val loc = when (firstMismatch) {
                    is EvaluatedArgument.Named -> firstMismatch.nameLocation ?: callLocation
                    else -> callLocation
                }
                throw KlangScriptArgumentError(
                    functionName = functionName,
                    message = "Call must use either all positional or all named arguments — no mixing",
                    location = loc,
                    callStackTrace = callStackTrace,
                )
            }

            val first = evaluated[0]
            if (first is EvaluatedArgument.Positional) {
                return Positional(evaluated.map { (it as EvaluatedArgument.Positional).value })
            }

            val map = linkedMapOf<String, RuntimeValue>()
            for (arg in evaluated) {
                arg as EvaluatedArgument.Named
                if (arg.name in map) {
                    throw KlangScriptArgumentError(
                        functionName = functionName,
                        message = "Duplicate named argument: '${arg.name}'",
                        location = arg.nameLocation ?: callLocation,
                        callStackTrace = callStackTrace,
                    )
                }
                map[arg.name] = arg.value
            }
            return Named(map)
        }
    }
}

/**
 * Declares one parameter of a native callable, used by the Phase 4 builder
 * hierarchy. Added in Phase 3 so the runtime types travel together; no
 * production callers use this until Phase 4.
 *
 * @property name Parameter name used for named-arg binding.
 * @property kotlinType Kotlin class the converted value must match.
 * @property default Null ⇒ required; non-null thunk runs only when the arg is
 *                   missing.
 * @property isVararg True if this slot captures trailing positional args.
 */
data class ParamSpec(
    val name: String,
    val kotlinType: KClass<*>,
    val default: (() -> RuntimeValue)? = null,
    val isVararg: Boolean = false,
) {
    val isOptional: Boolean get() = default != null
}

/**
 * Bind a [CallArgs] to a spec list, producing a flat List<RuntimeValue?>
 * aligned with [specs].
 *
 * Slot semantics:
 *  - Fixed required slot missing → [KlangScriptArgumentError].
 *  - Fixed optional slot missing → null at that index (caller invokes the default thunk).
 *  - Vararg slot:
 *      * Always last in [specs] (enforced at builder time).
 *      * Positional call: tail past the fixed slots is wrapped into a fresh [ArrayValue].
 *      * Named call: the named entry must already be an [ArrayValue]; it is passed through.
 *      * Missing in named call → empty [ArrayValue].
 *      * Vararg slots are never "required" (always 0+) and never use a default thunk.
 *
 * Used by Phase 4+ builder bodies and by the interpreter's
 * `positionalArgsForNative` to translate named calls into a positional list.
 */
fun resolveByParamSpec(
    functionName: String,
    specs: List<ParamSpec>,
    args: CallArgs,
    callLocation: SourceLocation?,
    callStackTrace: List<CallStackFrame> = emptyList(),
): List<RuntimeValue?> {
    val varargIdx = specs.indexOfFirst { it.isVararg }
    val hasVararg = varargIdx != -1
    val fixedCount = if (hasVararg) varargIdx else specs.size

    val result = arrayOfNulls<RuntimeValue>(specs.size)

    when (args) {
        CallArgs.Empty -> {
            // Nothing bound; required-check below catches unfilled required fixed params.
        }

        is CallArgs.Positional -> {
            if (hasVararg) {
                // First fixedCount values map to fixed slots; the remainder forms the vararg payload.
                for (i in 0 until minOf(fixedCount, args.values.size)) {
                    result[i] = args.values[i]
                }
                val tail = if (args.values.size > fixedCount) {
                    args.values.subList(fixedCount, args.values.size).toMutableList()
                } else {
                    mutableListOf()
                }
                result[varargIdx] = ArrayValue(tail)
            } else {
                if (args.values.size > specs.size) {
                    throw KlangScriptArgumentError(
                        functionName = functionName,
                        message = "too many arguments (${args.values.size}, expected ≤ ${specs.size})",
                        expected = specs.size,
                        actual = args.values.size,
                        location = callLocation,
                        callStackTrace = callStackTrace,
                    )
                }
                args.values.forEachIndexed { i, v -> result[i] = v }
            }
        }

        is CallArgs.Named -> {
            for ((name, v) in args.values) {
                val idx = specs.indexOfFirst { it.name == name }
                if (idx == -1) {
                    throw KlangScriptArgumentError(
                        functionName = functionName,
                        message = "unknown parameter '$name' (expected: ${specs.joinToString(", ") { it.name }})",
                        location = callLocation,
                        callStackTrace = callStackTrace,
                    )
                }
                if (specs[idx].isVararg && v !is ArrayValue) {
                    throw KlangScriptArgumentError(
                        functionName = functionName,
                        message = "vararg parameter '$name' requires an array; got ${v::class.simpleName}",
                        location = callLocation,
                        callStackTrace = callStackTrace,
                    )
                }
                result[idx] = v
            }
            if (hasVararg && result[varargIdx] == null) {
                result[varargIdx] = ArrayValue(mutableListOf())
            }
        }
    }

    specs.forEachIndexed { i, spec ->
        if (result[i] == null && spec.default == null && !spec.isVararg) {
            throw KlangScriptArgumentError(
                functionName = functionName,
                message = "missing required parameter '${spec.name}'",
                location = callLocation,
                callStackTrace = callStackTrace,
            )
        }
    }

    return result.toList()
}
