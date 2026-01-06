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
 * @return The converted Kotlin value
 * @throws TypeError if conversion fails
 */
inline fun <reified T : Any> RuntimeValue.convertToKotlin(): T {

    val isValid = T::class.isInstance(value)

    when (isValid) {
        true -> return value as T
        else -> throw TypeError(
            "Cannot convert ${this::class.simpleName} to ${T::class.simpleName}",
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
