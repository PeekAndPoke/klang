package io.peekandpoke.klang.script.annotations

import kotlin.reflect.KClass

/**
 * Container for KlangScript registration annotations.
 *
 * These annotations are processed by KSP to generate registration code
 * and documentation for native Kotlin functions exposed to KlangScript.
 */
object KlangScript {

    /**
     * Assigns annotated elements to a named KlangScript library.
     *
     * When placed on a class/object, all `@Method` functions inside inherit the library.
     * When placed on a file (`@file:KlangScript.Library(...)`), all `@Function` declarations
     * in that file inherit the library.
     *
     * Use [KlangScriptLibraries] constants to avoid repeating string literals.
     *
     * @param name The library name (e.g., [KlangScriptLibraries.STDLIB])
     */
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Library(val name: String)

    /**
     * Exposes an object as a named singleton in KlangScript.
     *
     * The annotated class (typically a Kotlin `object`) becomes accessible in KlangScript
     * by the given name. Methods inside annotated with `@Method` become callable on it.
     *
     * Must be combined with `@Library` on the same class.
     *
     * @param name The name visible in KlangScript (e.g., "Math", "console"). Defaults to the Kotlin class name.
     */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Object(val name: String = "")

    /**
     * Registers methods as extensions on a KlangScript RuntimeValue subtype.
     *
     * The annotated class (typically a Kotlin `object`) contains `@Method`-annotated
     * extension functions on the specified type.
     *
     * Must be combined with `@Library` on the same class.
     *
     * @param type The RuntimeValue subclass to extend (e.g., `StringValue::class`, `ArrayValue::class`)
     */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    annotation class TypeExtensions(val type: KClass<*>)

    /**
     * Registers a KlangScript function.
     *
     * When [receiver] is `Unit` (the default), the function is registered as a
     * **top-level** callable. When [receiver] is set to a concrete type (e.g.
     * `SprudelPattern::class`), the function is registered as an **extension
     * method** on that type — equivalent to placing the function inside a
     * `@TypeExtensions(receiver)` class but without the wrapper boilerplate.
     *
     * The library is inherited from `@Library` on the enclosing file or class.
     *
     * For extension methods: the **first** parameter of the Kotlin function is
     * treated as the receiver (same convention as `@TypeExtensions`). Remaining
     * parameters are the script-visible params.
     *
     * Signature conventions:
     * - Fixed params → fixed-arity registration
     * - `vararg` param → vararg registration
     * - `CallInfo` as last param → CallInfo-aware registration (auto-detected)
     *
     * @param name The function name in KlangScript. Defaults to the Kotlin function name.
     * @param receiver The receiver class for extension-method registration.
     *   `Unit::class` (default) = top-level function. Any other class = extension method.
     */
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Function(
        val name: String = "",
        val receiver: KClass<*> = Unit::class,
    )

    /**
     * Registers a method on an `@Object` or as a type extension in `@TypeExtensions`.
     *
     * Signature conventions:
     * - Fixed params → fixed-arity registration
     * - `vararg` param → vararg registration
     * - `CallInfo` as last param → CallInfo-aware registration (auto-detected)
     *
     * @param name The method name in KlangScript. Defaults to the Kotlin function name.
     */
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Method(val name: String = "")
}
