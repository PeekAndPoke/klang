package io.peekandpoke.klang.blockly

import io.peekandpoke.klang.blockly.BlockDefinitionBuilder.MAX_VARARG_SLOTS
import io.peekandpoke.klang.blockly.BlockDefinitionBuilder.PAT_POCKET_CATEGORIES
import io.peekandpoke.klang.blockly.BlockFieldNaming.boolField
import io.peekandpoke.klang.blockly.BlockFieldNaming.fieldName
import io.peekandpoke.klang.blockly.BlockFieldNaming.isBoolType
import io.peekandpoke.klang.blockly.BlockFieldNaming.isNumericType
import io.peekandpoke.klang.blockly.BlockFieldNaming.numField
import io.peekandpoke.klang.blockly.BlockFieldNaming.strField
import io.peekandpoke.klang.blockly.ext.defineBlocksWithJsonArray
import io.peekandpoke.klang.script.docs.DslDocsRegistry
import io.peekandpoke.klang.script.docs.DslType
import io.peekandpoke.klang.script.docs.FunctionDoc
import io.peekandpoke.klang.script.docs.ParamModel

/**
 * Builds Blockly block definitions and a toolbox configuration from [DslDocsRegistry].
 *
 * Design rules:
 * - One Blockly block per [FunctionDoc], type = `"klang_<funcName>"`.
 * - ALL blocks get both `previousStatement` and `nextStatement` with no type check, so any
 *   block can be placed anywhere — snapped into method chains or into the pattern-input
 *   sockets of combinators like `stack` and `seq`.
 * - vararg [PatternLike][BlockFieldNaming.isPatternLikeType] params become `input_statement`
 *   sockets; other vararg params become capped text fields ([MAX_VARARG_SLOTS] slots each).
 * - Properties / objects (no params) are omitted (no callable form).
 */
object BlockDefinitionBuilder {

    // ---------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------

    /** Maximum number of individual input slots generated for vararg parameters. */
    const val MAX_VARARG_SLOTS = 4

    /** Default colour for unknown categories. */
    private const val DEFAULT_COLOUR = 200

    /**
     * Only functions in these categories get `input_statement` sockets for their vararg
     * PatternLike parameters.  All other vararg PatternLike params fall back to text fields
     * so that string literals (e.g. `note("c d e")`) stay editable.
     */
    val PAT_POCKET_CATEGORIES: Set<String> = setOf("structural")

    private val CATEGORY_COLOURS = mapOf(
        "synthesis" to 230,
        "sample" to 160,
        "effects" to 120,
        "tempo" to 60,
        "structural" to 300,
        "random" to 20,
        "tonal" to 260,
        "continuous" to 180,
        "filters" to 90,
    )

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Build all block definitions from the global registry and register them with Blockly
     * via [defineBlocksWithJsonArray].
     *
     * Safe to call multiple times — Blockly replaces any previously registered definition
     * for the same type.
     */
    fun registerBlocks(registry: DslDocsRegistry = DslDocsRegistry.global) {
        val defs = registry.functions.values.mapNotNull { buildBlockDef(it) }
        if (defs.isNotEmpty()) {
            defineBlocksWithJsonArray(defs.toTypedArray())
        }
    }

    /**
     * Build and return a Blockly toolbox JSON object (parsed from a JSON string).
     * Categories are derived from [DslDocsRegistry.categories]; within each category
     * blocks are listed alphabetically.
     */
    fun buildToolbox(registry: DslDocsRegistry = DslDocsRegistry.global): dynamic {
        val json = buildString {
            append("""{"kind":"categoryToolbox","contents":[""")

            val categoryJsons = registry.categories.mapNotNull { category ->
                val blocks = registry.getFunctionsByCategory(category)
                    .filter { hasVisibleBlock(it) }
                    .joinToString(",") { """{"kind":"block","type":"klang_${it.name}"}""" }

                if (blocks.isEmpty()) return@mapNotNull null

                val colour = categoryColour(category)
                val label = category.replaceFirstChar { it.uppercaseChar() }
                """{"kind":"category","name":"$label","colour":"$colour","contents":[$blocks]}"""
            }

            append(categoryJsons.joinToString(","))
            append("]}")
        }
        return js("JSON").parse(json)
    }

    /**
     * Return the Blockly block-type string for the given DSL function name.
     */
    fun blockType(funcName: String) = "klang_$funcName"

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /** A function doc has a visible block when it has at least one callable variant. */
    private fun hasVisibleBlock(doc: FunctionDoc): Boolean =
        doc.variants.any { it.signatureModel.params != null }

    private fun categoryColour(category: String): Int =
        CATEGORY_COLOURS[category] ?: DEFAULT_COLOUR

    /**
     * Build a single Blockly block definition as a [dynamic] (parsed from JSON),
     * or return null when the function should not appear as a block (e.g. pure properties).
     *
     * Every block gets both `previousStatement` and `nextStatement` with no type check so
     * that any block can snap into any socket or chain position.
     */
    private fun buildBlockDef(doc: FunctionDoc): dynamic? {
        // Use the first callable variant as the primary signature
        val variant = doc.variants.firstOrNull { it.signatureModel.params != null } ?: return null
        val params = variant.signatureModel.params!!  // non-null guaranteed by filter above

        val isExtension = variant.type == DslType.EXTENSION_METHOD
        val colour = categoryColour(doc.category)

        // Expand parameters into field / input descriptors
        val fields = expandParams(params, doc.category)

        // Block label: ".name" for extension methods so users see the dot-chain form
        val label = if (isExtension) ".${doc.name}" else doc.name

        // message0:  "label %1 %2 … %N"
        val message = buildString {
            append(label.escapeJsonString())
            fields.forEachIndexed { i, _ -> append(" %${i + 1}") }
        }

        val json = buildString {
            append("""{"type":"klang_${doc.name}"""")
            append(""","message0":"$message"""")

            if (fields.isNotEmpty()) {
                append(""","args0":[""")
                append(fields.joinToString(",") { it.fieldJson })
                append("]")
            }

            // All blocks get both connection types with no type restriction,
            // so they can be placed anywhere (chain heads, chain links, pattern pockets).
            append(""","previousStatement":null""")
            append(""","nextStatement":null""")

            append(""","colour":$colour""")
            append(""","tooltip":"${doc.name.escapeJsonString()}"""")
            append("}")
        }

        return try {
            js("JSON").parse(json)
        } catch (e: Throwable) {
            console.warn("BlockDefinitionBuilder: failed to parse block def for ${doc.name}", e)
            null
        }
    }

    // ---------------------------------------------------------------
    // Field / input descriptors
    // ---------------------------------------------------------------

    /**
     * Minimal description of one Blockly field or input inside `args0`.
     */
    private data class FieldDescriptor(
        val fieldName: String,
        val fieldJson: String,
    )

    /**
     * Expand a list of [ParamModel]s into [FieldDescriptor]s.
     * Non-vararg parameters produce one descriptor each.
     * A vararg PatternLike parameter in a [PAT_POCKET_CATEGORIES] function produces
     * [MAX_VARARG_SLOTS] `input_statement` sockets; in all other functions it produces
     * [MAX_VARARG_SLOTS] text-field descriptors (so `note("c d e")` stays editable).
     * A vararg non-PatternLike parameter always produces [MAX_VARARG_SLOTS] text-field descriptors.
     */
    private fun expandParams(params: List<ParamModel>, category: String = ""): List<FieldDescriptor> {
        val result = mutableListOf<FieldDescriptor>()

        for (param in params) {
            if (param.isVararg) {
                val startIdx = result.size
                val usePockets = BlockFieldNaming.isPatternLikeType(param.type.simpleName)
                        && category in PAT_POCKET_CATEGORIES
                if (usePockets) {
                    for (slot in 0 until MAX_VARARG_SLOTS) {
                        result += makePatInput(startIdx + slot)
                    }
                } else {
                    for (slot in 0 until MAX_VARARG_SLOTS) {
                        result += makeField(startIdx + slot, param.type.simpleName, optional = slot > 0)
                    }
                }
            } else {
                result += makeField(result.size, param.type.simpleName, optional = false)
            }
        }

        return result
    }

    /**
     * Create a `PAT_<index>` [input_statement] descriptor.
     * Any block (with its `previousStatement` connection) can snap into this pocket.
     */
    private fun makePatInput(index: Int): FieldDescriptor {
        val name = BlockFieldNaming.patInput(index)
        return FieldDescriptor(
            fieldName = name,
            fieldJson = """{"type":"input_statement","name":"$name"}""",
        )
    }

    private fun makeField(index: Int, typeName: String, optional: Boolean): FieldDescriptor {
        val name = fieldName(index, typeName)
        val json = when {
            isNumericType(typeName) -> {
                """{"type":"field_number","name":"${numField(index)}","value":0}"""
            }

            isBoolType(typeName) -> {
                """{"type":"field_checkbox","name":"${boolField(index)}","checked":false}"""
            }

            else -> {
                // PatternLike, String, or anything else → free-text input
                val placeholder = if (optional) "" else ""
                """{"type":"field_input","name":"${strField(index)}","text":"$placeholder"}"""
            }
        }
        return FieldDescriptor(fieldName = name, fieldJson = json)
    }

    // ---------------------------------------------------------------
    // JSON escaping
    // ---------------------------------------------------------------

    private fun String.escapeJsonString(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
