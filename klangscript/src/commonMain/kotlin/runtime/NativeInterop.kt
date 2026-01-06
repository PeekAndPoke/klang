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
data class ExtensionMethod(
    val methodName: String,
    val receiverClass: KClass<*>,
    val invoker: (receiver: Any, args: List<RuntimeValue>) -> RuntimeValue,
)

/**
 * Convert a RuntimeValue to a Kotlin type
 *
 * Performs type-safe conversion from script runtime values to native Kotlin types.
 * Supports: String, Double, Int, Boolean, and native objects.
 *
 * @param T The target Kotlin type
 * @param value The RuntimeValue to convert
 * @return The converted Kotlin value
 * @throws TypeError if conversion fails
 */
inline fun <reified T : Any> convertParameter(value: RuntimeValue): T {
    return when {
        // String conversion
        T::class == String::class -> {
            when (value) {
                is StringValue -> value.value as T
                else -> throw TypeError(
                    "Cannot convert ${value::class.simpleName} to String",
                    operation = "parameter conversion"
                )
            }
        }

        // Double conversion
        T::class == Double::class -> {
            when (value) {
                is NumberValue -> value.value as T
                else -> throw TypeError(
                    "Cannot convert ${value::class.simpleName} to Double",
                    operation = "parameter conversion"
                )
            }
        }

        // Int conversion
        T::class == Int::class -> {
            when (value) {
                is NumberValue -> value.value.toInt() as T
                else -> throw TypeError(
                    "Cannot convert ${value::class.simpleName} to Int",
                    operation = "parameter conversion"
                )
            }
        }

        // Boolean conversion
        T::class == Boolean::class -> {
            when (value) {
                is BooleanValue -> value.value as T
                else -> throw TypeError(
                    "Cannot convert ${value::class.simpleName} to Boolean",
                    operation = "parameter conversion"
                )
            }
        }

        // Native object conversion
        value is NativeObjectValue<*> -> {
            if (value.kClass == T::class) {
                value.value as T
            } else {
                throw TypeError(
                    "Cannot convert native type ${value.qualifiedName} to ${T::class.simpleName}",
                    operation = "parameter conversion"
                )
            }
        }

        // Unsupported conversion
        else -> throw TypeError(
            "Cannot convert ${value::class.simpleName} to ${T::class.simpleName}",
            operation = "parameter conversion"
        )
    }
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
