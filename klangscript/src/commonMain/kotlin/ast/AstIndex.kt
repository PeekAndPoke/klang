package io.peekandpoke.klang.script.ast

/**
 * Result of finding a CallExpression at a cursor position.
 *
 * @param call The deepest CallExpression containing the cursor
 * @param functionName The resolved function/method name (rightmost member for chains)
 * @param argIndex Which argument the cursor is in (0-based), or -1 if on the callee
 * @param argument The argument AST node, if argIndex >= 0
 * @param argFrom Start offset of the argument in the source (inclusive)
 * @param argTo End offset of the argument in the source (exclusive)
 */
data class CallExpressionAtResult(
    val call: CallExpression,
    val functionName: String,
    val argIndex: Int,
    val argument: Expression?,
    val argFrom: Int,
    val argTo: Int,
)

/**
 * Pre-built index of AST node positions for fast cursor-to-node lookups.
 *
 * Built once per successful parse. Maintains:
 * - A sorted list of positioned entries for O(log n) cursor lookups
 * - A node-to-parent map for walking up the tree
 * - A node-to-offset map for retrieving argument offsets
 */
class AstIndex private constructor(
    /** Positioned nodes sorted by startOffset asc, level desc. Used for nodeAt lookups. */
    private val positionedEntries: List<PositionedEntry>,
    /** Every node's parent (including nodes without valid positions). */
    private val parentMap: Map<AstNode, AstNode>,
    /** Offset ranges for nodes that have valid positions. */
    private val offsetMap: Map<AstNode, IntRange>,
) {
    data class PositionedEntry(
        val node: AstNode,
        val level: Int,
        val startOffset: Int,
        val endOffset: Int, // exclusive
    )

    companion object {
        fun build(program: Program, source: String): AstIndex {
            val lineOffsets = buildLineOffsets(source)
            val positioned = mutableListOf<PositionedEntry>()
            val parents = mutableMapOf<AstNode, AstNode>()
            val offsets = mutableMapOf<AstNode, IntRange>()
            val builder = IndexBuilder(lineOffsets, positioned, parents, offsets)
            builder.visitStatements(program.statements, parent = program, level = 0)
            positioned.sortWith(compareBy<PositionedEntry> { it.startOffset }.thenByDescending { it.level })
            return AstIndex(positioned, parents, offsets)
        }
    }

    /**
     * Finds the call argument info at the given cursor position.
     *
     * Looks up the deepest AST node at [pos], then walks up through parents
     * to find the enclosing [CallExpression] and determine the argument index.
     */
    fun callArgAt(pos: Int): CallExpressionAtResult? {
        val deepestNode = nodeAt(pos) ?: return null

        // Walk up from the deepest node to find an enclosing CallExpression
        var node: AstNode = deepestNode

        while (true) {
            val parent = parentMap[node] ?: return null

            if (parent is CallExpression) {
                // Check if 'node' is (or is an ancestor of) one of the call's arguments
                val argIndex = findArgIndex(node, parent)
                if (argIndex >= 0) {
                    val arg = parent.arguments[argIndex]
                    val argRange = offsetMap[arg]
                    return CallExpressionAtResult(
                        call = parent,
                        functionName = extractFunctionName(parent.callee),
                        argIndex = argIndex,
                        argument = arg,
                        argFrom = argRange?.first ?: pos,
                        argTo = argRange?.let { it.last + 1 } ?: pos,
                    )
                }
            }

            node = parent
        }
    }

    /**
     * Returns the deepest (highest level) AST node whose range contains [pos].
     */
    private fun nodeAt(pos: Int): AstNode? {
        var best: PositionedEntry? = null

        // Binary search for approximate start
        var lo = 0
        var hi = positionedEntries.size - 1

        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (positionedEntries[mid].startOffset <= pos) lo = mid + 1 else hi = mid - 1
        }

        // Scan backward and forward from insertion point to find all containing entries
        for (i in (hi downTo maxOf(0, hi - 200))) {
            val entry = positionedEntries[i]
            if (entry.startOffset > pos) continue
            if (entry.endOffset <= pos) continue
            if (best == null || entry.level > best.level) best = entry
        }
        for (i in (hi + 1) until minOf(positionedEntries.size, hi + 200)) {
            val entry = positionedEntries[i]
            if (entry.startOffset > pos) break
            if (entry.endOffset <= pos) continue
            if (best == null || entry.level > best.level) best = entry
        }

        return best?.node
    }

    /**
     * Given a descendant node and a CallExpression, determines which argument index
     * the descendant falls under. Returns -1 if the descendant is the callee (not an arg).
     */
    private fun findArgIndex(descendant: AstNode, call: CallExpression): Int {
        // Walk up from descendant until we find a direct child of the call
        var node: AstNode = descendant
        while (true) {
            val idx = call.arguments.indexOfFirst { it === node }
            if (idx >= 0) return idx

            // If we've reached the call itself, the descendant is the callee
            val parent = parentMap[node] ?: return -1
            if (parent === call) return -1 // node is a direct child but not in arguments (it's the callee)
            node = parent
        }
    }
}

/**
 * Builds the index entries by walking the AST once.
 */
private class IndexBuilder(
    private val lineOffsets: IntArray,
    private val positioned: MutableList<AstIndex.PositionedEntry>,
    private val parents: MutableMap<AstNode, AstNode>,
    private val offsets: MutableMap<AstNode, IntRange>,
) {
    private fun SourceLocation.toStartOffset(): Int? = lineColToOffset(lineOffsets, startLine, startColumn)
    private fun SourceLocation.toEndOffset(): Int? = lineColToOffset(lineOffsets, endLine, endColumn)

    private fun index(node: AstNode, parent: AstNode, level: Int) {
        parents[node] = parent

        val loc = node.location ?: return
        val start = loc.toStartOffset() ?: return
        val end = loc.toEndOffset() ?: return
        if (start >= end) return // skip zero-width (e.g., CallExpression's paren-only location)

        offsets[node] = start until end
        positioned.add(AstIndex.PositionedEntry(node, level, start, end))
    }

    fun visitStatements(stmts: List<Statement>, parent: AstNode, level: Int) {
        for (stmt in stmts) visitStatement(stmt, parent, level)
    }

    private fun visitStatement(stmt: Statement, parent: AstNode, level: Int) {
        index(stmt, parent, level)
        when (stmt) {
            is ExpressionStatement -> visitExpression(stmt.expression, stmt, level + 1)
            is LetDeclaration -> stmt.initializer?.let { visitExpression(it, stmt, level + 1) }
            is ConstDeclaration -> visitExpression(stmt.initializer, stmt, level + 1)
            is ReturnStatement -> stmt.value?.let { visitExpression(it, stmt, level + 1) }
            is WhileStatement -> {
                visitExpression(stmt.condition, stmt, level + 1)
                visitStatements(stmt.body, stmt, level + 1)
            }

            is DoWhileStatement -> {
                visitStatements(stmt.body, stmt, level + 1)
                visitExpression(stmt.condition, stmt, level + 1)
            }

            is ForStatement -> {
                stmt.init?.let { visitStatement(it, stmt, level + 1) }
                stmt.condition?.let { visitExpression(it, stmt, level + 1) }
                stmt.update?.let { visitExpression(it, stmt, level + 1) }
                visitStatements(stmt.body, stmt, level + 1)
            }

            is IfExpression -> visitExpression(stmt, parent, level)
            is ImportStatement, is ExportStatement, is BreakStatement, is ContinueStatement -> {}
        }
    }

    private fun visitExpression(expr: Expression, parent: AstNode, level: Int) {
        index(expr, parent, level)
        when (expr) {
            is CallExpression -> {
                visitExpression(expr.callee, expr, level + 1)
                for (arg in expr.arguments) {
                    visitExpression(arg, expr, level + 1)
                }
            }

            is BinaryOperation -> {
                visitExpression(expr.left, expr, level + 1)
                visitExpression(expr.right, expr, level + 1)
            }

            is UnaryOperation -> visitExpression(expr.operand, expr, level + 1)
            is MemberAccess -> visitExpression(expr.obj, expr, level + 1)

            is IndexAccess -> {
                visitExpression(expr.obj, expr, level + 1)
                visitExpression(expr.index, expr, level + 1)
            }

            is TernaryExpression -> {
                visitExpression(expr.condition, expr, level + 1)
                visitExpression(expr.thenExpr, expr, level + 1)
                visitExpression(expr.elseExpr, expr, level + 1)
            }

            is AssignmentExpression -> {
                visitExpression(expr.target, expr, level + 1)
                visitExpression(expr.value, expr, level + 1)
            }

            is ArrayLiteral -> expr.elements.forEach { visitExpression(it, expr, level + 1) }
            is ObjectLiteral -> expr.properties.forEach { (_, v) -> visitExpression(v, expr, level + 1) }

            is ArrowFunction -> when (val body = expr.body) {
                is ArrowFunctionBody.ExpressionBody -> visitExpression(body.expression, expr, level + 1)
                is ArrowFunctionBody.BlockBody -> visitStatements(body.statements, expr, level + 1)
            }

            is IfExpression -> {
                visitExpression(expr.condition, expr, level + 1)
                visitStatements(expr.thenBranch, expr, level + 1)
                when (val elseBranch = expr.elseBranch) {
                    is ElseBranch.Block -> visitStatements(elseBranch.statements, expr, level + 1)
                    is ElseBranch.If -> visitExpression(elseBranch.ifExpr, expr, level + 1)
                    null -> {}
                }
            }

            is TemplateLiteral -> {
                for (part in expr.parts) {
                    if (part is TemplatePart.Interp) visitExpression(part.expression, expr, level + 1)
                }
            }

            is NumberLiteral, is StringLiteral, is BooleanLiteral, is NullLiteral, is Identifier -> {}
        }
    }
}

private fun extractFunctionName(callee: Expression): String = when (callee) {
    is Identifier -> callee.name
    is MemberAccess -> callee.property
    else -> ""
}

internal fun buildLineOffsets(source: String): IntArray {
    val offsets = mutableListOf(0)
    for (i in source.indices) {
        if (source[i] == '\n') {
            offsets.add(i + 1)
        }
    }
    return offsets.toIntArray()
}

internal fun lineColToOffset(lineOffsets: IntArray, line: Int, column: Int): Int? {
    val lineIdx = line - 1
    if (lineIdx < 0 || lineIdx >= lineOffsets.size) return null
    return lineOffsets[lineIdx] + (column - 1)
}
