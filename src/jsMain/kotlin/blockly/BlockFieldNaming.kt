package io.peekandpoke.klang.blockly

/**
 * Centralised convention for Blockly field names used throughout the `blockly` package.
 *
 * Every block parameter occupies one field whose name encodes both its **index** (0-based) and
 * its **value kind** (string / number / boolean).  Knowing the field name alone is therefore
 * sufficient to:
 *   - produce the correct Blockly field widget in [BlockDefinitionBuilder]
 *   - quote / unquote the value correctly in [KlangScriptGenerator]
 *   - write the correct JSON key in [AstToBlockly]
 *
 * Convention:
 *   `ARG_<index>_STR`  – free-text (field_input);  value is quoted in generated code
 *   `ARG_<index>_NUM`  – number  (field_number);    value is unquoted
 *   `ARG_<index>_BOOL` – boolean (field_checkbox);  value is `true` or `false`
 */
object BlockFieldNaming {

    // ----------------------------------------------------------------
    // Kind tokens
    // ----------------------------------------------------------------

    const val KIND_STR = "STR"
    const val KIND_NUM = "NUM"
    const val KIND_BOOL = "BOOL"

    // ----------------------------------------------------------------
    // Field name construction
    // ----------------------------------------------------------------

    fun strField(index: Int) = "ARG_${index}_$KIND_STR"
    fun numField(index: Int) = "ARG_${index}_$KIND_NUM"
    fun boolField(index: Int) = "ARG_${index}_$KIND_BOOL"

    /** Name of the value-input socket for a PatternLike arg at [index]. */
    fun patInput(index: Int) = "PAT_$index"

    /**
     * Return the canonical field name for a parameter at [index] given its Kotlin [typeName].
     */
    fun fieldName(index: Int, typeName: String): String = when {
        isNumericType(typeName) -> numField(index)
        isBoolType(typeName) -> boolField(index)
        else -> strField(index)
    }

    // ----------------------------------------------------------------
    // Kind predicates
    // ----------------------------------------------------------------

    fun isNumericType(typeName: String): Boolean =
        typeName in setOf("Double", "Float", "Int", "Long", "Number")

    fun isBoolType(typeName: String): Boolean =
        typeName == "Boolean"

    fun isPatternLikeType(typeName: String): Boolean =
        typeName in setOf("PatternLike", "StrudelPattern", "Pattern")

    /**
     * Parse the kind suffix from a field name (e.g. "ARG_0_STR" → "STR").
     * Returns null when the name does not match the convention.
     */
    fun kindOf(fieldName: String): String? {
        val parts = fieldName.split("_")
        // Expected format: ARG _ <index> _ <KIND>  →  3 parts
        return if (parts.size == 3 && parts[0] == "ARG") parts[2] else null
    }

    /**
     * Parse the zero-based parameter index from a field name (e.g. "ARG_0_STR" → 0).
     * Returns null when the name does not match the convention.
     */
    fun indexOf(fieldName: String): Int? {
        val parts = fieldName.split("_")
        return if (parts.size == 3 && parts[0] == "ARG") parts[1].toIntOrNull() else null
    }

    // ----------------------------------------------------------------
    // Code-generation helpers
    // ----------------------------------------------------------------

    /**
     * Format a raw field value for use in generated KlangScript code.
     *
     * - `KIND_STR`  → `"value"` (double-quoted, internal quotes escaped)
     * - `KIND_NUM`  → `value` (as-is; Blockly's number field returns a parseable string)
     * - `KIND_BOOL` → `true` / `false` (lowercased)
     */
    fun formatValue(fieldName: String, rawValue: String): String {
        return when (kindOf(fieldName)) {
            KIND_NUM -> rawValue
            KIND_BOOL -> rawValue.lowercase()
            else -> "\"${rawValue.escapeForCode()}\""
        }
    }

    // ----------------------------------------------------------------
    // Internal helpers
    // ----------------------------------------------------------------

    private fun String.escapeForCode(): String = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
