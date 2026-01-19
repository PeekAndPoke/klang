package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.ast.ArrowFunctionBody
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
    val invoker: (receiver: Any, args: List<RuntimeValue>, location: io.peekandpoke.klang.script.ast.SourceLocation?) -> RuntimeValue,
)

/** Check if the number of arguments matches the expected count */
fun checkArgsSize(fn: String, args: List<RuntimeValue>, expected: Int) {
    if (args.size < expected) {
        throw ArgumentError(
            functionName = fn,
            message = "Call to function $fn expected $expected arguments but got ${args.size}",
            expected = expected,
            actual = args.size,
        )
    }
}

/**
 * Convert a RuntimeValue to a Kotlin type
 */
fun <T : Any> RuntimeValue.convertToKotlin(cls: KClass<T>): T {
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

        is FunctionValue -> this.convertFunctionToKotlin()

        else -> {
            val isValid = cls.isInstance(value)

            when (isValid) {
                true -> value
                else -> throw TypeError(
                    "Cannot convert ${this::class.simpleName} to ${cls.simpleName}",
                    operation = "parameter conversion"
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    return result as T
}

fun <T : Any> FunctionValue.convertFunctionToKotlin(): T {

    val func = this

    val fn = when (func.parameters.size) {
        0 -> run {
            // NOTICE: The -> is important. It defines a Function0
            @Suppress("UNCHECKED_CAST")
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

        else -> throw TypeError(
            "Cannot convert script function to Kotlin. " +
                    "Only functions with up to 5 parameters are supported.",
            operation = "parameter conversion"
        )
    }

    @Suppress("UNCHECKED_CAST")
    return fn as T
}

/**
 * Calls the function with the provided arguments
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

// ... existing code ...
fun <T : Any> convertArgToKotlin(fn: String, args: List<RuntimeValue>, index: Int, cls: KClass<T>): T {
    val arg = args.getOrNull(index) ?: throw ArgumentError(
        fn,
        "Expected argument at index $index",
        expected = null,
        actual = null
    )

    return arg.convertToKotlin(cls)
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
