@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.script.builder

import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.getUniqueClassName
import io.peekandpoke.klang.script.runtime.*
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * Defines a klang script extension
 */
class KlangScriptExtension(
    /** Registered libraries */
    val libraries: Map<String, KlangScriptLibrary>,
    /** Registered native functions */
    val functions: List<Pair<String, (List<RuntimeValue>, SourceLocation?) -> RuntimeValue>>,
    /** Registered native types */
    val types: Map<KClass<*>, NativeTypeInfo>,
    /** Registered native objects */
    val objects: Map<String, Any>,
    /** Registered native object extension methods */
    val extensionMethods: Map<KClass<*>, MutableMap<String, NativeExtensionMethod>>,
)

/**
 * Native extension builder.
 *
 * This is used to register functions, types, and extension methods for kotlin interop.
 */
interface KlangScriptExtensionBuilder {
    companion object {
        /** Create a new [KlangScriptExtensionBuilder] */
        operator fun invoke(): KlangScriptExtensionBuilder = RegistryBuilderImpl()
    }

    /** Build the extension */
    fun buildNativeRegistry(): KlangScriptExtension

    /** Register a library with the engine */
    fun registerLibrary(library: KlangScriptLibrary)

    /** Registers a native function as a top-level function with source location */
    fun registerFunctionRaw(
        name: String,
        fn: (List<RuntimeValue>, SourceLocation?) -> RuntimeValue,
    )

    /** Register a native Kotlin type */
    fun <T : Any> registerType(cls: KClass<T>, block: NativeObjectExtensionsBuilder<T>.() -> Unit = {})

    /** Register a native Kotlin object */
    fun <T : Any> registerObject(name: String, obj: T, block: NativeObjectExtensionsBuilder<T>.() -> Unit = {})

    /** Register a native Kotlin extension method */
    fun <T : Any> registerExtensionMethod(
        receiver: KClass<T>,
        name: String,
        fn: (T, List<RuntimeValue>, SourceLocation?) -> RuntimeValue,
    )
}

/**
 * Native extension builder implementation.
 */
class RegistryBuilderImpl : KlangScriptExtensionBuilder {
    /** Registered libraries */
    private val libraries = mutableMapOf<String, KlangScriptLibrary>()

    /** Registered native functions */
    private val nativeFunctions =
        mutableListOf<Pair<String, (List<RuntimeValue>, SourceLocation?) -> RuntimeValue>>()

    /** Registered native types */
    private val nativeTypes = mutableMapOf<KClass<*>, NativeTypeInfo>()

    /** Registered native objects */
    private val nativeObjects = mutableMapOf<String, Any>()

    /** Registered native object extension methods */
    private val nativeExtensionMethods = mutableMapOf<KClass<*>, MutableMap<String, NativeExtensionMethod>>()

    /** Build the extension */
    override fun buildNativeRegistry(): KlangScriptExtension = KlangScriptExtension(
        libraries = libraries,
        types = nativeTypes,
        objects = nativeObjects,
        extensionMethods = nativeExtensionMethods,
        functions = nativeFunctions
    )

    /** Register a library with the engine */
    override fun registerLibrary(library: KlangScriptLibrary) {
        libraries[library.name] = library
    }

    /** Register a native function */
    override fun registerFunctionRaw(
        name: String,
        fn: (List<RuntimeValue>, SourceLocation?) -> RuntimeValue,
    ) {
        nativeFunctions.add(name to fn)
    }

    /** Register a native Kotlin type */
    override fun <T : Any> registerType(cls: KClass<T>, block: NativeObjectExtensionsBuilder<T>.() -> Unit) {
        // println("Registering native type: ${cls.simpleName} -> ${cls.getUniqueClassName()}")

        if (!nativeTypes.containsKey(cls)) {
            val qualifiedName = cls.getUniqueClassName()
            nativeTypes[cls] = NativeTypeInfo(cls, qualifiedName)
        }

        block(NativeObjectExtensionsBuilder(this, cls))
    }

    /** Register a native Kotlin object */
    override fun <T : Any> registerObject(
        name: String,
        obj: T,
        block: NativeObjectExtensionsBuilder<T>.() -> Unit,
    ) {
        nativeObjects[name] = obj

        @Suppress("UNCHECKED_CAST")
        registerType(cls = obj::class as KClass<T>, block = block)
    }

    /** Register a native Kotlin extension method */
    override fun <T : Any> registerExtensionMethod(
        receiver: KClass<T>,
        name: String,
        fn: (T, List<RuntimeValue>, SourceLocation?) -> RuntimeValue,
    ) {
        val extensionMethod = NativeExtensionMethod(
            methodName = name,
            receiverClass = receiver,
            invoker = { receiver, args, location ->
                @Suppress("UNCHECKED_CAST")
                val result = fn(receiver as T, args, location)
                wrapAsRuntimeValue(result)
            })

        registerType(receiver)

        nativeExtensionMethods.getOrPut(receiver) { mutableMapOf() }[name] = extensionMethod
    }
}

/**
 * Builder for registering extension methods on a native object or type
 */
class NativeObjectExtensionsBuilder<T : Any>(
    @PublishedApi
    internal val builder: KlangScriptExtensionBuilder,
    @PublishedApi
    internal val cls: KClass<T>,
) {
    /** Register a native extension function with variable number of parameters */
    inline fun <reified P : Any, reified R : Any> registerVarargMethod(
        name: String, noinline fn: T.(List<P>) -> R,
    ) {
        builder.registerExtensionMethod(cls, name) { receiver, args, _ ->
            val params = List(args.size) { index ->
                convertArgToKotlin(name, args, index, P::class)
            }
            val result = receiver.fn(params)
            wrapAsRuntimeValue(result)
        }
    }

    /**
     * Register a native extension method with CallInfo for location tracking
     *
     * Extracts source locations from RuntimeValue parameters and receiver, and provides them via CallInfo.
     */
    inline fun <reified P : Any, reified R> registerVarargMethodWithCallInfo(
        name: String, noinline fn: T.(List<P>, CallInfo) -> R,
    ) {
        builder.registerExtensionMethod(cls, name) { receiver, args, callLocation ->
            // Extract receiver location if it's a StringValue or NumberValue
            val receiverLocation = when (receiver) {
                is StringValue -> receiver.location
                is NumberValue -> receiver.location
                else -> null
            }

            // Extract parameter locations from RuntimeValue instances
            val paramLocations = args.map { arg ->
                when (arg) {
                    is StringValue -> arg.location
                    is NumberValue -> arg.location
                    else -> null
                }
            }

            val callInfo = CallInfo(
                callLocation = callLocation,
                receiverLocation = receiverLocation,
                paramLocations = paramLocations
            )

            // Convert arguments to target type
            val params = List(args.size) { index ->
                convertArgToKotlin(name, args, index, P::class)
            }

            val result = receiver.fn(params, callInfo)
            wrapAsRuntimeValue(result)
        }
    }

    /** Register a native extension method with no parameters */
    @JvmName("registerNativeExtensionMethod0")
    inline fun <reified R : Any> registerMethod(
        name: String, noinline fn: T.(Any?) -> R,
    ) {
        builder.registerExtensionMethod(cls, name) { receiver, _, _ ->
            val result = receiver.fn(null)
            wrapAsRuntimeValue(result)
        }
    }

    /** Register a native extension method with one parameter */
    inline fun <reified P1 : Any, reified R : Any> registerMethod(
        name: String, noinline fn: T.(P1) -> R,
    ) {
        builder.registerExtensionMethod(cls, name) { receiver, args, _ ->
            checkArgsSize(name, args, 1)
            val p1 = convertArgToKotlin(name, args, 0, P1::class)
            val result = receiver.fn(p1)
            wrapAsRuntimeValue(result)
        }
    }

    /**
     * Register a native extension method with two parameters
     */
    inline fun <reified P1 : Any, reified P2 : Any, reified R : Any> registerMethod(
        name: String, noinline fn: T.(P1, P2) -> R,
    ) {
        builder.registerExtensionMethod(cls, name) { receiver, args, _ ->
            checkArgsSize(name, args, 2)
            val p1 = convertArgToKotlin(name, args, 0, P1::class)
            val p2 = convertArgToKotlin(name, args, 1, P2::class)
            val result = receiver.fn(p1, p2)
            wrapAsRuntimeValue(result)
        }
    }

    /** Register a native extension method with three parameters */
    inline fun <reified P1 : Any, reified P2 : Any, reified P3 : Any, reified R : Any> registerMethod(
        name: String, noinline fn: T.(P1, P2, P3) -> R,
    ) {
        builder.registerExtensionMethod(cls, name) { receiver, args, _ ->
            checkArgsSize(name, args, 3)
            val p1 = convertArgToKotlin(name, args, 0, P1::class)
            val p2 = convertArgToKotlin(name, args, 1, P2::class)
            val p3 = convertArgToKotlin(name, args, 2, P3::class)
            val result = receiver.fn(p1, p2, p3)
            wrapAsRuntimeValue(result)
        }
    }

    /** Register a native extension method with four parameters */
    inline fun <reified P1 : Any, reified P2 : Any, reified P3 : Any, reified P4 : Any, reified R : Any>
            registerMethod(name: String, noinline fn: T.(P1, P2, P3, P4) -> R) {
        builder.registerExtensionMethod(cls, name) { receiver, args, _ ->
            checkArgsSize(name, args, 4)
            val p1 = convertArgToKotlin(name, args, 0, P1::class)
            val p2 = convertArgToKotlin(name, args, 1, P2::class)
            val p3 = convertArgToKotlin(name, args, 2, P3::class)
            val p4 = convertArgToKotlin(name, args, 3, P4::class)
            val result = receiver.fn(p1, p2, p3, p4)
            wrapAsRuntimeValue(result)
        }
    }

    /** Register a native extension method with five parameters */
    inline fun <
            reified P1 : Any,
            reified P2 : Any,
            reified P3 : Any,
            reified P4 : Any,
            reified P5 : Any,
            reified R : Any,
            > registerMethod(
        name: String,
        noinline fn: T.(P1, P2, P3, P4, P5) -> R,
    ) {
        builder.registerExtensionMethod(cls, name) { receiver, args, _ ->
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
}

/** Register a library from source code (backward compatibility) */
fun KlangScriptExtensionBuilder.registerLibrary(name: String, sourceCode: String) {
    registerLibrary(
        KlangScriptLibrary.builder(name).source(sourceCode).build()
    )
}

/** Register a native Kotlin type */
inline fun <reified T : Any> KlangScriptExtensionBuilder.registerType(
    noinline block: NativeObjectExtensionsBuilder<T>.() -> Unit = {},
) {
    registerType(cls = T::class, block = block)
}

/** Registers a native function as a top-level vararg function */
inline fun <reified P : Any, reified R : Any> KlangScriptExtensionBuilder.registerVarargFunction(
    name: String, noinline fn: (List<P>) -> R,
) {
    registerFunctionRaw(name) { args, _ ->
        val params = List(args.size) { index ->
            // println("Converting arg $index of type ${args[index].value!!::class} to type ${P::class}")
            convertArgToKotlin(name, args, index, P::class)
        }
        val result = fn(params)
        wrapAsRuntimeValue(result)
    }
}

/** Registers a native function as a top-level function with no parameters */
@JvmName("registerNativeFunction0")
inline fun <reified R : Any> KlangScriptExtensionBuilder.registerFunction(
    name: String, noinline fn: (Any?) -> R,
) {
    registerFunctionRaw(name) { _, _ ->
        val result = fn(null)
        wrapAsRuntimeValue(result)
    }
}

/** Registers a native function as a top-level function with one parameter */
inline fun <reified P1 : Any, reified R : Any> KlangScriptExtensionBuilder.registerFunction(
    name: String, noinline fn: (P1) -> R,
) {
    registerFunctionRaw(name) { args, _ ->
        checkArgsSize(name, args, 1)
        val param = convertArgToKotlin(name, args, 0, P1::class)
        val result = fn(param)
        wrapAsRuntimeValue(result)
    }
}

/** Registers a native function as a top-level function with two parameters */
inline fun <reified P1 : Any, reified P2 : Any, reified R : Any> KlangScriptExtensionBuilder.registerFunction(
    name: String, noinline fn: (P1, P2) -> R,
) {
    registerFunctionRaw(name) { args, _ ->
        checkArgsSize(name, args, 2)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val result = fn(p1, p2)
        wrapAsRuntimeValue(result)
    }
}

/** Registers a native function as a top-level function with three parameters */
inline fun <reified P1 : Any, reified P2 : Any, reified P3 : Any, reified R : Any>
        KlangScriptExtensionBuilder.registerFunction(name: String, noinline fn: (P1, P2, P3) -> R) {
    registerFunctionRaw(name) { args, _ ->
        checkArgsSize(name, args, 3)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val p3 = convertArgToKotlin(name, args, 2, P3::class)
        val result = fn(p1, p2, p3)
        wrapAsRuntimeValue(result)
    }
}

/** Registers a native function as a top-level function with four parameters */
inline fun <reified P1 : Any, reified P2 : Any, reified P3 : Any, reified P4 : Any, reified R : Any>
        KlangScriptExtensionBuilder.registerFunction(name: String, noinline fn: (P1, P2, P3, P4) -> R) {
    registerFunctionRaw(name) { args, _ ->
        checkArgsSize(name, args, 4)
        val p1 = convertArgToKotlin(name, args, 0, P1::class)
        val p2 = convertArgToKotlin(name, args, 1, P2::class)
        val p3 = convertArgToKotlin(name, args, 2, P3::class)
        val p4 = convertArgToKotlin(name, args, 3, P4::class)
        val result = fn(p1, p2, p3, p4)
        wrapAsRuntimeValue(result)
    }
}

/** Registers a native function as a top-level function with five parameters */
inline fun <reified P1 : Any, reified P2 : Any, reified P3 : Any, reified P4 : Any, reified P5 : Any, reified R : Any>
        KlangScriptExtensionBuilder.registerFunction(name: String, noinline fn: (P1, P2, P3, P4, P5) -> R) {
    registerFunctionRaw(name) { args, _ ->
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

// ===== CallInfo-aware registration methods =====

/**
 * Registers a native function with CallInfo for location tracking
 *
 * Extracts source locations from RuntimeValue parameters and provides them via CallInfo.
 */
inline fun <reified P : Any, reified R> KlangScriptExtensionBuilder.registerVarargFunctionWithCallInfo(
    name: String, noinline fn: (List<P>, CallInfo) -> R,
) {
    registerFunctionRaw(name) { args, callLocation ->
        // Extract parameter locations from RuntimeValue instances
        val paramLocations = args.map { arg ->
            when (arg) {
                is StringValue -> arg.location
                is NumberValue -> arg.location
                else -> null
            }
        }

        val callInfo = CallInfo(
            callLocation = callLocation,
            paramLocations = paramLocations
        )

        // Convert arguments to target type
        val params = List(args.size) { index ->
            convertArgToKotlin(name, args, index, P::class)
        }

        val result = fn(params, callInfo)

        wrapAsRuntimeValue(result)
    }
}
