package io.peekandpoke.klang.script.runtime

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
 * @property invoker Function that invokes the extension method with receiver and arguments
 */
data class NativeExtensionMethod(
    val methodName: String,
    val receiverClass: KClass<*>,
    val invoker: (receiver: Any, args: List<RuntimeValue>) -> RuntimeValue,
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
