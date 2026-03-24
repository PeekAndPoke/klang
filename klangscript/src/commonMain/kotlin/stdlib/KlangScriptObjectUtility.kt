package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.*

/**
 * Object utility for KlangScript -- provides static methods on the Object type.
 *
 * Exposed as `Object` in KlangScript, matching the JavaScript Object API.
 *
 * Methods use the raw-args pattern (List<RuntimeValue>) because they operate
 * on RuntimeValue objects directly rather than unwrapped Kotlin types.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Object")
internal object KlangScriptObjectUtility {
    override fun toString(): String = "[Object utility]"

    /**
     * Returns an array of the object's own property names.
     *
     * ```KlangScript(Executable)
     * let obj = { name: "klang", version: 1 }
     * Object.keys(obj).joinToString(", ")
     * ```
     *
     * @param obj The object to get keys from
     * @return An array of property name strings
     * @category object
     * @tags properties
     */
    @KlangScript.Method
    fun keys(args: List<RuntimeValue>, callLocation: SourceLocation?): RuntimeValue {
        val obj = args[0] as? ObjectValue
            ?: throw KlangScriptTypeError("Object.keys() expects an object argument", location = callLocation)
        val keys = obj.properties.keys.map { StringValue(it) }
        return ArrayValue(keys.toMutableList())
    }

    /**
     * Returns an array of the object's own property values.
     *
     * ```KlangScript(Executable)
     * let obj = { a: 1, b: 2, c: 3 }
     * Object.values(obj).joinToString(", ")
     * ```
     *
     * @param obj The object to get values from
     * @return An array of property values
     * @category object
     * @tags properties
     */
    @KlangScript.Method
    fun values(args: List<RuntimeValue>, callLocation: SourceLocation?): RuntimeValue {
        val obj = args[0] as? ObjectValue
            ?: throw KlangScriptTypeError("Object.values() expects an object argument", location = callLocation)
        return ArrayValue(obj.properties.values.toMutableList())
    }

    /**
     * Returns an array of the object's own [key, value] pairs.
     *
     * ```KlangScript(Executable)
     * let obj = { x: 10, y: 20 }
     * Object.entries(obj).size()
     * ```
     *
     * @param obj The object to get entries from
     * @return An array of [key, value] arrays
     * @category object
     * @tags properties
     */
    @KlangScript.Method
    fun entries(args: List<RuntimeValue>, callLocation: SourceLocation?): RuntimeValue {
        val obj = args[0] as? ObjectValue
            ?: throw KlangScriptTypeError("Object.entries() expects an object argument", location = callLocation)
        val entries = obj.properties.map { (key, value) ->
            ArrayValue(mutableListOf(StringValue(key), value))
        }
        return ArrayValue(entries.toMutableList())
    }
}
