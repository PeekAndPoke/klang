package io.peekandpoke.klang.blockly

import io.peekandpoke.klang.blockly.BlockFieldNaming.formatValue
import io.peekandpoke.klang.blockly.ext.Block
import io.peekandpoke.klang.blockly.ext.WorkspaceSvg

/**
 * Walks a [WorkspaceSvg] and produces a KlangScript source string.
 *
 * Algorithm
 * ---------
 * 1. Collect all *top* blocks (blocks with no previous connection) — these are the chain heads.
 * 2. For each chain head, walk `nextBlock` links to collect the full chain.
 * 3. Render the first block as `funcName(args…)` and each subsequent block as `.funcName(args…)`.
 * 4. Prepend the standard `import * from "stdlib"` / `"strudel"` lines so that the resulting
 *    code can be passed directly to `StrudelPattern.compileRaw()`.
 *
 * Field-value quoting is delegated to [BlockFieldNaming.formatValue] which uses the field-name
 * suffix (`_STR`, `_NUM`, `_BOOL`) to decide whether to wrap the value in quotes.
 */
object KlangScriptGenerator {

    private val PREAMBLE = """
        import * from "stdlib"
        import * from "strudel"
    """.trimIndent()

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Generate KlangScript code from the current contents of [workspace].
     *
     * Returns an empty string when the workspace contains no renderable blocks,
     * so callers can check `result.isBlank()` before using it.
     */
    fun generate(workspace: WorkspaceSvg): String {
        val topBlocks = workspace.getTopBlocks(ordered = true)
        val patterns = topBlocks.mapNotNull { generateChain(it) }
        if (patterns.isEmpty()) return ""

        return buildString {
            append(PREAMBLE)
            append("\n\n")
            append(patterns.joinToString("\n\n"))
        }
    }

    // ----------------------------------------------------------------
    // Chain rendering
    // ----------------------------------------------------------------

    /**
     * Render a single chain starting at [headBlock].
     * Returns null when the chain produces no code (e.g. all blocks have unknown types).
     */
    private fun generateChain(headBlock: Block): String? {
        val parts = mutableListOf<String>()
        var current: Block? = headBlock
        var isHead = true

        while (current != null) {
            val part = renderBlock(current, isHead)
            if (part != null) {
                parts += part
                isHead = false
            }
            current = current.nextBlock
        }

        return if (parts.isEmpty()) null else parts.joinToString("")
    }

    /**
     * Render a single block as a KlangScript expression fragment.
     *
     * @param block  The block to render.
     * @param isHead True when this is the first block in the chain (no leading dot).
     * @return The code fragment, or null if the block type is not a `klang_` block.
     */
    private fun renderBlock(block: Block, isHead: Boolean): String? {
        val type = block.type
        if (!type.startsWith("klang_")) return null

        val funcName = type.removePrefix("klang_")
        val args = collectArgs(block)

        return if (isHead) {
            "$funcName(${args.joinToString(", ")})"
        } else {
            ".$funcName(${args.joinToString(", ")})"
        }
    }

    // ----------------------------------------------------------------
    // Field collection
    // ----------------------------------------------------------------

    /**
     * Collect formatted argument strings from a block's `ARG_<i>_*` fields in index order.
     *
     * Iteration terminates as soon as neither `_STR`, `_NUM`, nor `_BOOL` exists for index `i`.
     */
    private fun collectArgs(block: Block): List<String> {
        val args = mutableListOf<String>()
        var i = 0

        while (true) {
            val strField = BlockFieldNaming.strField(i)
            val numField = BlockFieldNaming.numField(i)
            val boolField = BlockFieldNaming.boolField(i)

            val (fieldName, rawValue) = when {
                block.getFieldValue(strField) != null -> strField to block.getFieldValue(strField)!!
                block.getFieldValue(numField) != null -> numField to block.getFieldValue(numField)!!
                block.getFieldValue(boolField) != null -> boolField to block.getFieldValue(boolField)!!
                else -> break
            }

            // Skip trailing empty string slots (from optional vararg fields)
            if (rawValue.isBlank() && BlockFieldNaming.kindOf(fieldName) == BlockFieldNaming.KIND_STR) {
                i++
                continue
            }

            args += formatValue(fieldName, rawValue)
            i++
        }

        return args
    }
}
