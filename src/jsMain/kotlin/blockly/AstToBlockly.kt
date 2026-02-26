package io.peekandpoke.klang.blockly

import io.peekandpoke.klang.blockly.ext.WorkspaceSvg
import io.peekandpoke.klang.blockly.ext.serialization
import io.peekandpoke.klang.script.ast.*
import io.peekandpoke.klang.script.docs.DslDocsRegistry
import io.peekandpoke.klang.script.docs.ParamModel
import io.peekandpoke.klang.script.parser.KlangScriptParser

/**
 * Converts KlangScript source code into a Blockly workspace state using Blockly's
 * serialisation API (`Blockly.serialization.workspaces.load`).
 *
 * Strategy
 * --------
 * 1. Parse the source with [KlangScriptParser].
 * 2. Walk only [ExpressionStatement] nodes (skip imports, lets, etc.).
 * 3. For each statement, extract a *linear chain* — a list of (funcName, args) steps
 *    derived by walking left-recursively through [CallExpression] / [MemberAccess] nodes.
 * 4. Translate each chain into the Blockly serialisation JSON format.
 * 5. Load the JSON into the workspace via `serialization.workspaces.load(state, workspace)`.
 *
 * Limitations (v1)
 * ----------------
 * - Only linear chains are supported (`a().b().c()`).
 * - Nested calls in argument position (e.g. `seq("a", stack("b","c"))`) are rendered as
 *   raw text in the field value rather than as nested blocks.
 * - Statements that cannot be expressed as a chain (binary ops, arrow functions, …) are
 *   silently skipped.
 */
object AstToBlockly {

    // ----------------------------------------------------------------
    // Internal data classes
    // ----------------------------------------------------------------

    /** One step in a method chain: the function name and its resolved argument values. */
    private data class ChainStep(
        val funcName: String,
        val args: List<Expression>,
    )

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Parse [code] and populate [workspace] with the corresponding blocks.
     *
     * Clears any existing blocks first.  On parse failure the workspace is left empty and a
     * warning is printed to the console.
     */
    fun populate(workspace: WorkspaceSvg, code: String) {
        workspace.clear()

        if (code.isBlank()) return

        val program = try {
            KlangScriptParser.parse(code)
        } catch (e: Throwable) {
            console.warn("AstToBlockly: failed to parse code:", e.message)
            return
        }

        val state = buildWorkspaceState(program)

        try {
            serialization.workspaces.load(state, workspace)
        } catch (e: Throwable) {
            console.warn("AstToBlockly: failed to load workspace state:", e.message)
        }
    }

    // ----------------------------------------------------------------
    // Workspace state JSON construction
    // ----------------------------------------------------------------

    private fun buildWorkspaceState(program: Program): dynamic {
        val topBlocks = mutableListOf<dynamic>()
        var xOffset = 30

        for (stmt in program.statements) {
            if (stmt !is ExpressionStatement) continue

            val chain = extractChain(stmt.expression)
            if (chain.isEmpty()) continue

            val blockJson = chainToJson(chain, x = xOffset, y = 30)
                ?: continue

            topBlocks.add(blockJson)
            xOffset += 240
        }

        val json = buildString {
            append("""{"blocks":{"languageVersion":0,"blocks":[""")
            append(topBlocks.joinToString(",") { js("JSON").stringify(it) as String })
            append("]}}")
        }

        return js("JSON").parse(json)
    }

    // ----------------------------------------------------------------
    // Chain extraction from AST
    // ----------------------------------------------------------------

    /**
     * Walk an [Expression] and extract a linear chain of [ChainStep]s.
     *
     * Supports patterns like:
     *   `sound("bd hh sd")`                         → [sound("bd hh sd")]
     *   `sound("bd hh sd").gain(0.5).speed(2)`      → [sound, gain, speed]
     *
     * Returns an empty list for anything that cannot be represented as a linear chain.
     */
    private fun extractChain(expr: Expression): List<ChainStep> {
        val steps = mutableListOf<ChainStep>()
        var current: Expression = expr

        while (true) {
            when (current) {
                is CallExpression -> {
                    when (val callee = current.callee) {
                        // Top-level call: sound("bd hh sd")
                        is Identifier -> {
                            steps.add(0, ChainStep(callee.name, current.arguments))
                            return steps  // nothing left to unwrap
                        }
                        // Chained call: <expr>.method(args)
                        is MemberAccess -> {
                            steps.add(0, ChainStep(callee.property, current.arguments))
                            current = callee.obj
                        }
                        // Other forms not supported
                        else -> return emptyList()
                    }
                }
                // Bare identifier (object / constant), e.g. `silence`
                is Identifier -> {
                    steps.add(0, ChainStep(current.name, emptyList()))
                    return steps
                }

                else -> return emptyList()
            }
        }
    }

    // ----------------------------------------------------------------
    // JSON generation
    // ----------------------------------------------------------------

    /**
     * Convert a [chain] into the Blockly serialisation JSON structure for a single top-level
     * block (with nested "next" blocks).
     *
     * Returns null when the chain is empty or when no known block type exists for the head step.
     */
    private fun chainToJson(chain: List<ChainStep>, x: Int, y: Int): dynamic? {
        if (chain.isEmpty()) return null

        // Build from the tail backwards so we can embed "next" into each parent
        var nextJson: dynamic = null
        for (i in chain.indices.reversed()) {
            val step = chain[i]
            val isHead = (i == 0)
            val blockObj = stepToJson(step, isHead = isHead, x = if (isHead) x else null, y = if (isHead) y else null)
                ?: continue  // skip unknown block types

            if (nextJson != null) {
                blockObj.next = js("{}")
                blockObj.next.block = nextJson
            }

            nextJson = blockObj
        }

        return nextJson
    }

    /**
     * Build a single block object for Blockly serialisation.
     *
     * The function uses [DslDocsRegistry] to determine the expected parameter types (and thus
     * the correct field names).  If the function is not in the registry, field names default
     * to `ARG_<i>_STR` for all positions.
     */
    private fun stepToJson(step: ChainStep, isHead: Boolean, x: Int?, y: Int?): dynamic? {
        val blockType = BlockDefinitionBuilder.blockType(step.funcName)

        // Look up parameter types from registry (optional — graceful fallback)
        val paramModels: List<ParamModel>? =
            DslDocsRegistry.global.get(step.funcName)
                ?.variants
                ?.firstOrNull { it.signatureModel.params != null }
                ?.signatureModel
                ?.params

        // Build the block object directly as dynamic
        val block: dynamic = js("{}")
        block.type = blockType

        if (x != null) block.x = x
        if (y != null) block.y = y

        val fields: dynamic = js("{}")
        var hasFields = false

        step.args.forEachIndexed { idx, argExpr ->
            val typeName = paramModels
                ?.getOrNull(idx)
                ?.let { if (it.isVararg) it.type.simpleName else it.type.simpleName }
                ?: "PatternLike"  // fallback: treat as string

            val fieldName = BlockFieldNaming.fieldName(idx, typeName)
            val rawValue = expressionToFieldValue(argExpr, typeName)

            if (rawValue != null) {
                fields[fieldName] = rawValue
                hasFields = true
            }
        }

        if (hasFields) {
            block.fields = fields
        }

        return block
    }

    /**
     * Render an [Expression] to a raw field value suitable for the given [typeName].
     *
     * - Literal values are extracted directly.
     * - Complex sub-expressions (nested calls, etc.) are rendered as their source text via a
     *   best-effort serialiser — this lets users see *something* in the text field even when
     *   the value is not a plain literal.
     * - Returns null if the expression cannot produce any meaningful value.
     */
    private fun expressionToFieldValue(expr: Expression, typeName: String): Any? {
        return when (expr) {
            is StringLiteral -> expr.value
            is NumberLiteral -> {
                val d = expr.value
                // Prefer integer representation when there is no fractional part
                if (d == kotlin.math.floor(d) && !d.isInfinite()) d.toInt().toString() else d.toString()
            }

            is BooleanLiteral -> expr.value.toString()
            is NullLiteral -> null
            // For anything else (identifiers, sub-calls) produce a best-effort text rendering
            else -> exprToText(expr)?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Best-effort serialisation of an [Expression] back to KlangScript text.
     * Used for complex argument values that cannot be stored as simple literals.
     */
    private fun exprToText(expr: Expression): String? {
        return when (expr) {
            is StringLiteral -> "\"${expr.value}\""
            is NumberLiteral -> {
                val d = expr.value
                if (d == kotlin.math.floor(d) && !d.isInfinite()) d.toInt().toString() else d.toString()
            }

            is BooleanLiteral -> expr.value.toString()
            is NullLiteral -> "null"
            is Identifier -> expr.name
            is CallExpression -> {
                val callee = exprToText(expr.callee) ?: return null
                val args = expr.arguments.joinToString(", ") { exprToText(it) ?: "?" }
                "$callee($args)"
            }

            is MemberAccess -> {
                val obj = exprToText(expr.obj) ?: return null
                "$obj.${expr.property}"
            }

            is UnaryOperation -> {
                val operand = exprToText(expr.operand) ?: return null
                when (expr.operator) {
                    UnaryOperator.NEGATE -> "-$operand"
                    UnaryOperator.PLUS -> "+$operand"
                    UnaryOperator.NOT -> "!$operand"
                }
            }

            is BinaryOperation -> {
                val l = exprToText(expr.left) ?: return null
                val r = exprToText(expr.right) ?: return null
                val op = when (expr.operator) {
                    BinaryOperator.ADD -> "+"
                    BinaryOperator.SUBTRACT -> "-"
                    BinaryOperator.MULTIPLY -> "*"
                    BinaryOperator.DIVIDE -> "/"
                    BinaryOperator.MODULO -> "%"
                    BinaryOperator.EQUAL -> "=="
                    BinaryOperator.NOT_EQUAL -> "!="
                    BinaryOperator.LESS_THAN -> "<"
                    BinaryOperator.LESS_THAN_OR_EQUAL -> "<="
                    BinaryOperator.GREATER_THAN -> ">"
                    BinaryOperator.GREATER_THAN_OR_EQUAL -> ">="
                    BinaryOperator.AND -> "&&"
                    BinaryOperator.OR -> "||"
                }
                "($l $op $r)"
            }

            else -> null
        }
    }
}
