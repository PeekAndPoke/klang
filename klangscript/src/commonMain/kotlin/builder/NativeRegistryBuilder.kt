@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.script.builder

import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.getUniqueClassName
import io.peekandpoke.klang.script.runtime.*
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

class NativeRegistry(
    val libraries: Map<String, KlangScriptLibrary>,
    val nativeTypes: Map<KClass<*>, NativeTypeInfo>,
    val nativeExtensionMethods: Map<KClass<*>, MutableMap<String, NativeExtensionMethod>>,
    val nativeFunctions: List<Pair<String, (List<RuntimeValue>) -> RuntimeValue>>,
)

interface NativeRegistryBuilder {
    companion object {
        /** Create a new [NativeRegistryBuilder] */
        operator fun invoke(): NativeRegistryBuilder = RegistryBuilderImpl()
    }

    /**
     * Build the registry
     */
    fun buildNativeRegistry(): NativeRegistry

    /**
     * Register a library with the engine
     *
     * @param library The library to register
     * @return This builder for method chaining
     */
    fun registerLibrary(library: KlangScriptLibrary)

    /**
     * Register a native function
     *
     * @param name The function name
     * @param fn The function implementation
     * @return This builder for method chaining
     */
    fun registerNativeFunction(name: String, fn: (List<RuntimeValue>) -> RuntimeValue)

    /**
     * Register a native Kotlin type
     */
    fun <T : Any> registerNativeType(cls: KClass<T>)

    /**
     * Register a native Kotlin extension method
     */
    fun <T : Any> registerNativeExtensionMethod(
        receiver: KClass<T>, name: String, fn: (T, List<RuntimeValue>) -> RuntimeValue,
    )
}

class RegistryBuilderImpl : NativeRegistryBuilder {
    private val libraries = mutableMapOf<String, KlangScriptLibrary>()

    private val nativeTypes = mutableMapOf<KClass<*>, NativeTypeInfo>()

    private val nativeExtensionMethods = mutableMapOf<KClass<*>, MutableMap<String, NativeExtensionMethod>>()

    private val nativeFunctions = mutableListOf<Pair<String, (List<RuntimeValue>) -> RuntimeValue>>()

    /**
     * Build the registry
     */
    override fun buildNativeRegistry(): NativeRegistry = NativeRegistry(
        libraries = libraries,
        nativeTypes = nativeTypes,
        nativeExtensionMethods = nativeExtensionMethods,
        nativeFunctions = nativeFunctions
    )

    /**
     * Register a library with the engine
     *
     * @param library The library to register
     * @return This builder for method chaining
     */
    override fun registerLibrary(library: KlangScriptLibrary) {
        libraries[library.name] = library
    }

    /**
     * Register a native function
     *
     * @param name The function name
     * @param fn The function implementation
     * @return This builder for method chaining
     */
    override fun registerNativeFunction(name: String, fn: (List<RuntimeValue>) -> RuntimeValue) {
        nativeFunctions.add(name to fn)
    }

    /**
     * Register a native Kotlin type
     */
    override fun <T : Any> registerNativeType(cls: KClass<T>) {
        if (!nativeTypes.containsKey(cls)) {
            val qualifiedName = cls.getUniqueClassName()
            nativeTypes[cls] = NativeTypeInfo(cls, qualifiedName)
        }
    }

    override fun <T : Any> registerNativeExtensionMethod(
        receiver: KClass<T>, name: String, fn: (T, List<RuntimeValue>) -> RuntimeValue,
    ) {
        val extensionMethod = NativeExtensionMethod(
            methodName = name,
            receiverClass = receiver,
            invoker = { receiver, args ->
                @Suppress("UNCHECKED_CAST")
                val result = fn(receiver as T, args)
                wrapAsRuntimeValue(result)
            })

        nativeExtensionMethods.getOrPut(receiver) { mutableMapOf() }[name] = extensionMethod
    }
}

/**
 * Register a library from source code (backward compatibility)
 *
 * @param name The library name
 * @param sourceCode The KlangScript source code
 * @return This builder for method chaining
 */
fun NativeRegistryBuilder.registerLibrary(name: String, sourceCode: String) {
    registerLibrary(
        KlangScriptLibrary.builder(name).source(sourceCode).build()
    )
}

/**
 * Register a native Kotlin type
 */
inline fun <reified T : Any> NativeRegistryBuilder.registerNativeType() {
    registerNativeType(T::class)
}

/**
 * Register a native function with variable number of parameters
 */
inline fun <reified P : Any, reified R : Any> NativeRegistryBuilder.registerNativeFunctionVararg(
    name: String, noinline fn: (List<P>) -> R,
) {
    registerNativeFunction(name) { args ->
        val params = List(args.size) { index ->
            println("Converting arg $index of type ${args[index].value!!::class} to type ${P::class}")
            convertArgToKotlin(name, args, index, P::class)
        }
        val result = fn(params)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native function with no parameters
 */
inline fun <reified R : Any> NativeRegistryBuilder.registerNativeFunction(
    name: String, noinline fn: () -> R,
) {
    registerNativeFunction(name) { _ ->
        val result = fn()
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native function with one parameter
 */
inline fun <reified P1 : Any, reified R : Any> NativeRegistryBuilder.registerNativeFunction(
    name: String, noinline fn: (P1) -> R,
) {
    registerNativeFunction(name) { args ->
        checkArgsSize(name, args, 1)
        val param = convertArgToKotlin(name, args, 0, P1::class)
        val result = fn(param)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native function with two parameters
 */
inline fun <reified P1 : Any, reified P2 : Any, reified R : Any> NativeRegistryBuilder.registerNativeFunction(
    name: String, noinline fn: (P1, P2) -> R,
) {
    registerNativeFunction(name) { args ->
        checkArgsSize(name, args, 2)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val result = fn(p1, p2)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native function with three parameters
 */
inline fun <reified P1 : Any, reified P2 : Any, reified P3 : Any, reified R : Any>
        NativeRegistryBuilder.registerNativeFunction(name: String, noinline fn: (P1, P2, P3) -> R) {
    registerNativeFunction(name) { args ->
        checkArgsSize(name, args, 3)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val p3 = convertArgToKotlin(name, args, 2, P3::class)
        val result = fn(p1, p2, p3)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native function with four parameters
 */
inline fun <reified P1 : Any, reified P2 : Any, reified P3 : Any, reified P4 : Any, reified R : Any>
        NativeRegistryBuilder.registerNativeFunction(name: String, noinline fn: (P1, P2, P3, P4) -> R) {
    registerNativeFunction(name) { args ->
        checkArgsSize(name, args, 4)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val p3 = convertArgToKotlin(name, args, 2, P3::class)
        val p4 = convertArgToKotlin(name, args, 3, P4::class)
        val result = fn(p1, p2, p3, p4)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native function with five parameters
 */
inline fun <reified P1 : Any, reified P2 : Any, reified P3 : Any, reified P4 : Any, reified P5 : Any, reified R : Any>
        NativeRegistryBuilder.registerNativeFunction(name: String, noinline fn: (P1, P2, P3, P4, P5) -> R) {
    registerNativeFunction(name) { args ->
        checkArgsSize(name, args, 5)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val p3 = convertArgToKotlin(name, args, 2, P3::class)
        val p4 = convertArgToKotlin(name, args, 3, P4::class)
        val p5 = convertArgToKotlin(name, args, 4, P5::class)
        val result = fn(p1, p2, p3, p4, p5)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native extension function with variable number of parameters
 */
inline fun <reified TRes : Any, reified P : Any, reified R : Any> NativeRegistryBuilder.registerNativeExtensionMethodVararg(
    name: String, noinline fn: TRes.(List<P>) -> R,
) {
    registerNativeExtensionMethod(TRes::class, name) { receiver, args ->
        val params = List(args.size) { index ->
            convertArgToKotlin(name, args, index, P::class)
        }
        val result = receiver.fn(params)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native extension method with no parameters
 */
@JvmName("registerNativeExtensionMethod0")
inline fun <reified TRes : Any, reified R : Any> NativeRegistryBuilder.registerNativeExtensionMethod(
    name: String, noinline fn: TRes.(Any?) -> R,
) {
    registerNativeExtensionMethod(TRes::class, name) { receiver, _ ->
        val result = receiver.fn(null)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native extension method with one parameter
 */
inline fun <reified TRes : Any, reified P1 : Any, reified R : Any> NativeRegistryBuilder.registerNativeExtensionMethod(
    name: String, noinline fn: TRes.(P1) -> R,
) {
    registerNativeExtensionMethod(TRes::class, name) { receiver, args ->
        checkArgsSize(name, args, 1)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val result = receiver.fn(p1)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native extension method with two parameters
 */
inline fun <reified TRes : Any, reified P1 : Any, reified P2 : Any, reified R : Any>
        NativeRegistryBuilder.registerNativeExtensionMethod(
    name: String, noinline fn: TRes.(P1, P2) -> R,
) {
    registerNativeExtensionMethod(TRes::class, name) { receiver, args ->
        checkArgsSize(name, args, 2)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val result = receiver.fn(p1, p2)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native extension method with three parameters
 */
inline fun <reified TRes : Any, reified P1 : Any, reified P2 : Any, reified P3 : Any, reified R : Any>
        NativeRegistryBuilder.registerNativeExtensionMethod(
    name: String, noinline fn: TRes.(P1, P2, P3) -> R,
) {
    registerNativeExtensionMethod(TRes::class, name) { receiver, args ->
        checkArgsSize(name, args, 3)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val p3 = convertArgToKotlin(name, args, 2, P3::class)
        val result = receiver.fn(p1, p2, p3)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native extension method with four parameters
 */
inline fun <reified TRes : Any, reified P1 : Any, reified P2 : Any, reified P3 : Any, reified P4 : Any, reified R : Any>
        NativeRegistryBuilder.registerNativeExtensionMethod(
    name: String, noinline fn: TRes.(P1, P2, P3, P4) -> R,
) {
    registerNativeExtensionMethod(TRes::class, name) { receiver, args ->
        checkArgsSize(name, args, 4)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val p3 = convertArgToKotlin(name, args, 2, P3::class)
        val p4 = convertArgToKotlin(name, args, 3, P4::class)
        val result = receiver.fn(p1, p2, p3, p4)
        wrapAsRuntimeValue(result)
    }
}

/**
 * Register a native extension method with four parameters
 */
inline fun <reified TRes : Any, reified P1 : Any, reified P2 : Any, reified P3 : Any, reified P4 : Any, reified P5 : Any, reified R : Any>
        NativeRegistryBuilder.registerNativeExtensionMethod(
    name: String, noinline fn: TRes.(P1, P2, P3, P4, P5) -> R,
) {
    registerNativeExtensionMethod(TRes::class, name) { receiver, args ->
        checkArgsSize(name, args, 5)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val p3 = convertArgToKotlin(name, args, 2, P3::class)
        val p4 = convertArgToKotlin(name, args, 3, P4::class)
        val p5 = convertArgToKotlin(name, args, 4, P5::class)
        val result = receiver.fn(p1, p2, p3, p4, p5)
        wrapAsRuntimeValue(result)
    }
}
