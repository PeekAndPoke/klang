package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.ast.ArrayLiteral
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.AssignmentExpression
import io.peekandpoke.klang.script.ast.AstIndex
import io.peekandpoke.klang.script.ast.BinaryOperation
import io.peekandpoke.klang.script.ast.BooleanLiteral
import io.peekandpoke.klang.script.ast.BreakStatement
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.ConstDeclaration
import io.peekandpoke.klang.script.ast.ContinueStatement
import io.peekandpoke.klang.script.ast.DoWhileStatement
import io.peekandpoke.klang.script.ast.ElseBranch
import io.peekandpoke.klang.script.ast.ExportDeclaration
import io.peekandpoke.klang.script.ast.ExportStatement
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.ast.ForStatement
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.ast.IfExpression
import io.peekandpoke.klang.script.ast.ImportStatement
import io.peekandpoke.klang.script.ast.IndexAccess
import io.peekandpoke.klang.script.ast.LetDeclaration
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.ast.NullLiteral
import io.peekandpoke.klang.script.ast.NumberLiteral
import io.peekandpoke.klang.script.ast.ObjectLiteral
import io.peekandpoke.klang.script.ast.Program
import io.peekandpoke.klang.script.ast.ReturnStatement
import io.peekandpoke.klang.script.ast.Statement
import io.peekandpoke.klang.script.ast.StringLiteral
import io.peekandpoke.klang.script.ast.TemplateLiteral
import io.peekandpoke.klang.script.ast.TemplatePart
import io.peekandpoke.klang.script.ast.TernaryExpression
import io.peekandpoke.klang.script.ast.UnaryOperation
import io.peekandpoke.klang.script.ast.WhileStatement
import io.peekandpoke.klang.script.ast.buildLineOffsets
import io.peekandpoke.klang.script.ast.lineColToOffset
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangType

/**
 * Result of parsing and analyzing a KlangScript program.
 *
 * Bundles the parsed AST, an index for cursor-to-node lookups, a pre-computed type map
 * for all expressions, and any diagnostics produced by static analysis.
 *
 * Built once per code change; editor features query it via O(1) lookups instead of
 * re-running type inference on every hover or completion request.
 */
class AnalyzedAst(
    /** The parsed AST. */
    val ast: Program,
    /** The original source code. */
    val source: String,
    /** Index for O(log n) cursor-to-node lookups. */
    val astIndex: AstIndex,
    /** The docs registry used for type inference and symbol lookups. */
    val registry: KlangDocsRegistry,
    /** Pre-computed types for every expression in the AST. */
    private val typeMap: Map<Expression, KlangType?>,
    /** Diagnostics from static analysis (errors, warnings, hints). */
    val diagnostics: List<AnalyzerDiagnostic>,
) {
    /** Cached line offsets for line/col → offset conversion. */
    private val lineOffsets: IntArray by lazy { buildLineOffsets(source) }

    /** Look up the pre-computed type for an expression node. */
    fun typeOf(expr: Expression): KlangType? = typeMap[expr]

    /**
     * Get the inferred type of the deepest expression at a 1-based line/column position.
     *
     * Returns null if the position doesn't map to an expression or the type is unknown.
     */
    fun getTypeAt(line: Int, col: Int): KlangType? {
        val offset = lineColToOffset(lineOffsets, line, col) ?: return null
        return getTypeAtOffset(offset)
    }

    /**
     * Get the inferred type of the deepest expression at a 0-based character offset.
     *
     * Returns null if the position doesn't map to an expression or the type is unknown.
     */
    fun getTypeAtOffset(offset: Int): KlangType? {
        val node = astIndex.nodeAt(offset)
        return if (node is Expression) {
            typeMap[node]
        } else {
            null
        }
    }

    /**
     * Find the inferred type of the expression ending at or just before [offset].
     *
     * Used for dot-completion: when the user types a dot after `Osc.sine()`, the cursor
     * is right after `)` which may fall outside the CallExpression's exclusive end range.
     *
     * Strategy:
     * 1. Try nodeAt(offset), then walk back up to 3 positions
     * 2. For each position, find the deepest node
     * 3. If that node is an Expression with a known type, return it
     * 4. Otherwise, walk up through parents looking for a CallExpression or other
     *    Expression with a known type — this handles the case where we land on an
     *    inner node (e.g., an Identifier inside a MemberAccess inside a CallExpression)
     */
    fun getExpressionTypeEndingAt(offset: Int): KlangType? {
        for (lookback in 0..3) {
            val pos = offset - lookback
            if (pos < 0) {
                break
            }
            val node = astIndex.nodeAt(pos) ?: continue

            // Try the node itself
            if (node is Expression) {
                val type = typeMap[node]
                if (type != null) {
                    return type
                }
            }

            // Walk up through parents looking for an Expression with a known type
            // that ends at or near our target offset
            var current = node
            while (true) {
                val parent = astIndex.parentOf(current) ?: break
                if (parent is Expression) {
                    val type = typeMap[parent]
                    if (type != null) {
                        // Verify this parent actually ends near our offset
                        val range = astIndex.offsetOf(parent)
                        if (range != null && offset in range.first..(range.last + 2)) {
                            return type
                        }
                    }
                }
                current = parent
            }
        }
        return null
    }

    companion object {
        /**
         * Parse source code and build an [AnalyzedAst].
         */
        fun build(
            source: String,
            registry: KlangDocsRegistry,
            computeDiagnostics: Boolean = true,
        ): AnalyzedAst {
            val program = KlangScriptParser.parse(source)
            return build(program, source, registry, computeDiagnostics)
        }

        /**
         * Build an [AnalyzedAst] from an already-parsed program.
         *
         * @param computeDiagnostics Set to false when diagnostics aren't needed
         *   (e.g. engine runtime, batch tools) to skip the analyzer walk.
         */
        fun build(
            program: Program,
            source: String,
            registry: KlangDocsRegistry,
            computeDiagnostics: Boolean = true,
        ): AnalyzedAst {
            val astIndex = AstIndex.build(program, source)
            val inferrer = ExpressionTypeInferrer(registry)
            val typeMap = buildTypeMap(program, inferrer)
            val diagnostics = if (computeDiagnostics) {
                NamedArgumentChecker(registry, typeMap).check(program)
            } else {
                emptyList()
            }
            return AnalyzedAst(
                ast = program,
                source = source,
                astIndex = astIndex,
                registry = registry,
                typeMap = typeMap,
                diagnostics = diagnostics,
            )
        }
    }
}

// ── Type map builder ───────────────────────────────────────────────────────

private class TypeMapBuilder(private val inferrer: ExpressionTypeInferrer) {
    val map = mutableMapOf<Expression, KlangType?>()

    fun visitExpr(expr: Expression) {
        map[expr] = inferrer.inferType(expr)
        // Recurse into children
        when (expr) {
            is CallExpression -> {
                visitExpr(expr.callee)
                expr.arguments.forEach { visitExpr(it.value) }
            }

            is BinaryOperation -> {
                visitExpr(expr.left)
                visitExpr(expr.right)
            }

            is UnaryOperation -> {
                visitExpr(expr.operand)
            }

            is MemberAccess -> {
                visitExpr(expr.obj)
            }

            is IndexAccess -> {
                visitExpr(expr.obj)
                visitExpr(expr.index)
            }

            is TernaryExpression -> {
                visitExpr(expr.condition)
                visitExpr(expr.thenExpr)
                visitExpr(expr.elseExpr)
            }

            is AssignmentExpression -> {
                visitExpr(expr.target)
                visitExpr(expr.value)
            }

            is ArrayLiteral -> {
                expr.elements.forEach { visitExpr(it) }
            }

            is ObjectLiteral -> {
                expr.properties.forEach { (_, v) -> visitExpr(v) }
            }

            is ArrowFunction -> when (val body = expr.body) {
                is ArrowFunctionBody.ExpressionBody -> visitExpr(body.expression)
                is ArrowFunctionBody.BlockBody -> body.statements.forEach { visitStmt(it) }
            }

            is IfExpression -> {
                visitExpr(expr.condition)
                expr.thenBranch.forEach { visitStmt(it) }
                when (val e = expr.elseBranch) {
                    is ElseBranch.Block -> e.statements.forEach { visitStmt(it) }
                    is ElseBranch.If -> visitExpr(e.ifExpr)
                    null -> {}
                }
            }

            is TemplateLiteral -> {
                for (part in expr.parts) {
                    if (part is TemplatePart.Interp) {
                        visitExpr(part.expression)
                    }
                }
            }
            // Leaf expressions — no children to recurse into
            is NumberLiteral, is StringLiteral, is BooleanLiteral, is Identifier, NullLiteral -> {}
        }
    }

    fun visitStmt(stmt: Statement) {
        when (stmt) {
            is ExpressionStatement -> visitExpr(stmt.expression)
            is LetDeclaration -> stmt.initializer?.let { visitExpr(it) }
            is ConstDeclaration -> visitExpr(stmt.initializer)
            is ExportDeclaration -> visitExpr(stmt.initializer)
            is ReturnStatement -> stmt.value?.let { visitExpr(it) }
            is WhileStatement -> {
                visitExpr(stmt.condition)
                stmt.body.forEach { visitStmt(it) }
            }

            is DoWhileStatement -> {
                stmt.body.forEach { visitStmt(it) }
                visitExpr(stmt.condition)
            }

            is ForStatement -> {
                stmt.init?.let { visitStmt(it) }
                stmt.condition?.let { visitExpr(it) }
                stmt.update?.let { visitExpr(it) }
                stmt.body.forEach { visitStmt(it) }
            }

            is ImportStatement, is ExportStatement, is BreakStatement, is ContinueStatement -> {}
        }
    }
}

private fun buildTypeMap(program: Program, inferrer: ExpressionTypeInferrer): Map<Expression, KlangType?> {
    val builder = TypeMapBuilder(inferrer)
    program.statements.forEach { builder.visitStmt(it) }
    return builder.map
}
