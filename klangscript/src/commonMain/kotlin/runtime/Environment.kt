package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.builder.NativeRegistry
import kotlin.reflect.KClass

/**
 * Environment for storing variables and managing lexical scope
 *
 * The environment implements lexical scoping through a parent-chain pattern.
 * Each environment has:
 * - A map of variable names to their values
 * - A map tracking which variables are mutable (let) vs immutable (const)
 * - An optional parent environment
 *
 * Variable lookup traverses the scope chain from inner to outer scopes.
 *
 * Example scope chain:
 * ```
 * Global Environment (parent=null)
 *   └─> Function Environment (parent=Global)
 *       └─> Block Environment (parent=Function)
 * ```
 *
 * When looking up a variable:
 * 1. Check the current environment
 * 2. If not found, check the parent
 * 3. Continue up the chain until found or we reach the top
 *
 * Const protection:
 * Variables declared with `const` cannot be reassigned after initialization.
 * Attempting to reassign a const variable throws a RuntimeException.
 */
class Environment(
    /** Parent environment for scope chaining (null for global scope) */
    private val parent: Environment? = null,
) {
    /** Registry of all known libraries */
    private val libraries = mutableMapOf<String, KlangScriptLibrary>()

    /** Registry with all known native types */
    private val nativeTypes = mutableMapOf<KClass<*>, NativeTypeInfo>()

    /** Registry with all known native extension methods */
    private val nativeExtensionMethods = mutableMapOf<KClass<*>, MutableMap<String, NativeExtensionMethod>>()

    /** Map of variable names to their runtime values in this scope */
    private val values = mutableMapOf<String, RuntimeValue>()

    /** Map tracking which variables are mutable (true for let, false for const) */
    private val mutable = mutableMapOf<String, Boolean>()

    /** Map of local names to exported names (for libraries with export aliasing) */
    private val exportAliases = mutableMapOf<String, String>()

    fun register(native: NativeRegistry) {
        // Register all libraries
        libraries.putAll(native.libraries)

        // Register native functions
        nativeTypes.putAll(native.nativeTypes)

        // Register all native extension methods
        nativeExtensionMethods.putAll(native.nativeExtensionMethods)

        // Define all native functions as values
        native.nativeFunctions.forEach { (name, function) ->
            define(name, NativeFunctionValue(name, function))
        }
    }

    /**
     * Define a new variable in this environment
     *
     * If a variable with the same name already exists in this environment,
     * it will be overwritten. This does not affect parent scopes.
     *
     * @param name The variable name
     * @param value The runtime value to store
     * @param mutable Whether the variable can be reassigned (true for let, false for const)
     */
    fun define(name: String, value: RuntimeValue, mutable: Boolean = true) {
        values[name] = value
        this.mutable[name] = mutable
    }

    /**
     * Get a variable value, traversing the scope chain if needed
     *
     * Looks up the variable in this environment first. If not found,
     * recursively searches parent environments up the scope chain.
     *
     * @param name The variable name to look up
     * @return The runtime value associated with this variable
     * @throws RuntimeException if the variable is not found in any scope
     *
     * Example:
     * ```
     * val global = Environment()
     * global.define("x", NumberValue(10.0))
     *
     * val local = Environment(parent = global)
     * local.get("x")  // Returns NumberValue(10.0) from parent
     * ```
     */
    fun get(
        name: String,
        location: SourceLocation? = null,
        stackTrace: List<CallStackFrame> = emptyList(),
    ): RuntimeValue {
        return values[name] ?: parent?.get(name, location, stackTrace)
        ?: throw ReferenceError(name, location = location, stackTrace = stackTrace)
    }

    /**
     * Check if a variable exists in this environment or parent chain
     *
     * @param name The variable name to check
     * @return true if the variable exists anywhere in the scope chain
     */
    fun has(name: String): Boolean {
        return values.containsKey(name) || (parent?.has(name) == true)
    }

    /**
     * Mark symbols as exported with optional aliasing
     *
     * This is used by export statements to declare which symbols
     * should be accessible when importing this library, and under what names.
     *
     * @param exports List of (localName, exportedName) pairs
     *                localName is the variable name in the library
     *                exportedName is the name visible to importers
     */
    fun markExports(exports: List<Pair<String, String>>) {
        for ((localName, exportedName) in exports) {
            exportAliases[localName] = exportedName
        }
    }

    /**
     * Get all exported symbols from this environment
     *
     * This is used for importing library symbols. It returns only the
     * symbols that have been explicitly marked for export, using their
     * exported names (which may differ from local names due to aliasing).
     *
     * If no exports are marked, all symbols are considered exported
     * (backward compatibility for libraries without export statements).
     *
     * @return Map of exported names to their runtime values
     */
    fun getExportedSymbols(): Map<String, RuntimeValue> {
        return if (exportAliases.isEmpty()) {
            // No exports marked - export all symbols with their local names (backward compatibility)
            values.toMap()
        } else {
            // Export only explicitly marked symbols, using their exported names
            exportAliases.mapNotNull { (localName, exportedName) ->
                values[localName]?.let { value ->
                    exportedName to value
                }
            }.toMap()
        }
    }

    /**
     * Get all symbols defined in this environment (not including parent)
     *
     * This is used for importing library symbols. It returns a map of
     * all variables defined in this environment scope only.
     *
     * @return Map of variable names to their runtime values
     * @deprecated Use getExportedSymbols() for library imports
     */
    fun getAllSymbols(): Map<String, RuntimeValue> {
        return values.toMap()
    }

    /**
     * Load library source code by name (LibraryLoader interface implementation)
     *
     * This is called by the interpreter when executing import statements.
     * It applies any native registrations and returns the library source code.
     *
     * @param name The library name
     * @return The library source code
     * @throws RuntimeException if the library is not found
     */
    fun loadLibrary(name: String): String {
        val library = libraries[name]
            ?: throw ImportError(name, "Library not found")

        register(library.native)

        // Return source code (or empty string if library has no source)
        return library.sourceCode
    }

    /**
     * Get an extension method for a native type
     *
     * Used by the interpreter to lookup methods during member access evaluation.
     *
     * @param kClass The Kotlin class to lookup methods for
     * @param methodName The method name
     * @return The extension method, or null if not found
     */
    fun getExtensionMethod(kClass: KClass<*>, methodName: String): NativeExtensionMethod? {
        return nativeExtensionMethods[kClass]?.get(methodName)
    }

    /**
     * Get all registered extension method names for a native type
     *
     * Used for error messages to suggest available methods.
     *
     * @param kClass The Kotlin class to get methods for
     * @return List of method names
     */
    fun getExtensionMethodNames(kClass: KClass<*>): List<String> {
        return nativeExtensionMethods[kClass]?.keys?.toList() ?: emptyList()
    }
}
