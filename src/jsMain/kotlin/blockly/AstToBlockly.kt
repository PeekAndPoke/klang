package io.peekandpoke.klang.blockly

import io.peekandpoke.klang.blockly.AstToBlockly.buildBlockJson
import io.peekandpoke.klang.blockly.AstToBlockly.chainToJson
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
     * Build a single statement-block object for Blockly serialisation.
     * Delegates to [buildBlockJson] with the standard `klang_` type prefix.
     */
    private fun stepToJson(step: ChainStep, isHead: Boolean, x: Int?, y: Int?): dynamic? =
        buildBlockJson(step.funcName, step.args, x = x, y = y)

    /**
     * Build a Blockly serialisation object for a single block.
     *
     * For each argument:
     * - If the corresponding param is a vararg [PatternLike], the argument is recursively
     *   converted to a block and placed in the `inputs` map under `PAT_<idx>` (statement pocket).
     * - Otherwise the argument is serialised to a field value in the `fields` map.
     */
    private fun buildBlockJson(
        funcName: String,
        args: List<Expression>,
        x: Int? = null,
        y: Int? = null,
    ): dynamic? {
        val blockType = BlockDefinitionBuilder.blockType(funcName)

        val paramModels: List<ParamModel>? =
            DslDocsRegistry.global.get(funcName)
                ?.variants
                ?.firstOrNull { it.signatureModel.params != null }
                ?.signatureModel
                ?.params

        val block: dynamic = js("{}")
        block.type = blockType
        if (x != null) block.x = x
        if (y != null) block.y = y

        val fields: dynamic = js("{}")
        var hasFields = false
        val inputs: dynamic = js("{}")
        var hasInputs = false

        val funcCategory = DslDocsRegistry.global.get(funcName)?.category ?: ""

        args.forEachIndexed { idx, argExpr ->
            // Resolve param model — for vararg, last param covers all overflow indices
            val param = paramModels?.getOrNull(idx)
                ?: paramModels?.lastOrNull()?.takeIf { it.isVararg }
            val typeName = param?.type?.simpleName ?: "PatternLike"
            // Only structural combinators (stack, seq, gap, …) use PAT pockets;
            // other vararg PatternLike functions (note, etc.) use regular text fields.
            val isVarargPatternLike = param?.isVararg == true &&
                    BlockFieldNaming.isPatternLikeType(typeName) &&
                    funcCategory in BlockDefinitionBuilder.PAT_POCKET_CATEGORIES

            if (isVarargPatternLike) {
                val inputName = BlockFieldNaming.patInput(idx)
                val nestedBlock = expressionToValueBlock(argExpr)
                if (nestedBlock != null) {
                    inputs[inputName] = js("{}")
                    inputs[inputName].block = nestedBlock
                    hasInputs = true
                }
            } else {
                val fieldName = BlockFieldNaming.fieldName(idx, typeName)
                val rawValue = expressionToFieldValue(argExpr, typeName)
                if (rawValue != null) {
                    fields[fieldName] = rawValue
                    hasFields = true
                }
            }
        }

        if (hasFields) block.fields = fields
        if (hasInputs) block.inputs = inputs

        return block
    }

    /**
     * Convert an [Expression] argument into a block object (or chain) suitable for placing
     * inside a `PAT_<i>` statement-input socket.
     *
     * Supports both simple calls (`sound("bd")`) and chained calls
     * (`sound("bd hh sd").slow(2)`) by extracting a full chain and rendering it
     * as a linked sequence of blocks via [chainToJson].
     *
     * Returns null for expression forms that cannot be represented as a chain.
     */
    private fun expressionToValueBlock(expr: Expression): dynamic? {
        val chain = extractChain(expr)
        if (chain.isEmpty()) return null
        return chainToJson(chain, x = 0, y = 0)
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
