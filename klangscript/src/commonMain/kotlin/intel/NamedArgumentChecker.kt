package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.ast.Argument
import io.peekandpoke.klang.script.ast.ArrayLiteral
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.AssignmentExpression
import io.peekandpoke.klang.script.ast.BinaryOperation
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
import io.peekandpoke.klang.script.ast.ObjectLiteral
import io.peekandpoke.klang.script.ast.Program
import io.peekandpoke.klang.script.ast.ReturnStatement
import io.peekandpoke.klang.script.ast.Statement
import io.peekandpoke.klang.script.ast.TemplateLiteral
import io.peekandpoke.klang.script.ast.TemplatePart
import io.peekandpoke.klang.script.ast.TernaryExpression
import io.peekandpoke.klang.script.ast.UnaryOperation
import io.peekandpoke.klang.script.ast.WhileStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangType

/**
 * Static analyzer that flags named-argument mistakes against known callables
 * **before** the user runs the code.
 *
 * For each [CallExpression] whose callee resolves to a [KlangCallable] in the
 * [KlangDocsRegistry], emits [AnalyzerDiagnostic.ERROR] entries for:
 *
 *  - **Mixing** positional and named in the same call (the all-or-nothing rule).
 *  - **Unknown named** parameter — name doesn't match any declared param.
 *  - **Duplicate named** parameter — same name passed twice.
 *  - **Missing required** — only checked for all-named calls when no unknown
 *    names were flagged (avoids noisy cascades).
 *
 * The checker is intentionally **silent on unresolved callees**. If the docs
 * registry doesn't know the function (could be a script-defined arrow fn or
 * an unimported library), we have no specs to check against — surfacing
 * "unknown function" is the existing reference-error path's job.
 */
class NamedArgumentChecker(
    private val docs: KlangDocsRegistry,
    private val typeMap: Map<Expression, KlangType?>,
) {
    /** Walk the program and collect all named-arg diagnostics. */
    fun check(program: Program): List<AnalyzerDiagnostic> {
        val out = mutableListOf<AnalyzerDiagnostic>()
        program.statements.forEach { visitStatement(it, out) }
        return out
    }

    // ── AST traversal ────────────────────────────────────────────────────────

    private fun visitStatement(stmt: Statement, out: MutableList<AnalyzerDiagnostic>) {
        when (stmt) {
            is ExpressionStatement -> visitExpression(stmt.expression, out)
            is LetDeclaration -> stmt.initializer?.let { visitExpression(it, out) }
            is ConstDeclaration -> visitExpression(stmt.initializer, out)
            is ExportDeclaration -> visitExpression(stmt.initializer, out)
            is ReturnStatement -> stmt.value?.let { visitExpression(it, out) }
            is WhileStatement -> {
                visitExpression(stmt.condition, out)
                stmt.body.forEach { visitStatement(it, out) }
            }

            is DoWhileStatement -> {
                stmt.body.forEach { visitStatement(it, out) }
                visitExpression(stmt.condition, out)
            }

            is ForStatement -> {
                stmt.init?.let { visitStatement(it, out) }
                stmt.condition?.let { visitExpression(it, out) }
                stmt.update?.let { visitExpression(it, out) }
                stmt.body.forEach { visitStatement(it, out) }
            }

            is ImportStatement, is ExportStatement, is BreakStatement, is ContinueStatement -> {}
        }
    }

    private fun visitExpression(expr: Expression, out: MutableList<AnalyzerDiagnostic>) {
        when (expr) {
            is CallExpression -> {
                checkCall(expr, out)
                visitExpression(expr.callee, out)
                expr.arguments.forEach { visitExpression(it.value, out) }
            }

            is BinaryOperation -> {
                visitExpression(expr.left, out)
                visitExpression(expr.right, out)
            }

            is UnaryOperation -> visitExpression(expr.operand, out)
            is MemberAccess -> visitExpression(expr.obj, out)
            is IndexAccess -> {
                visitExpression(expr.obj, out)
                visitExpression(expr.index, out)
            }

            is TernaryExpression -> {
                visitExpression(expr.condition, out)
                visitExpression(expr.thenExpr, out)
                visitExpression(expr.elseExpr, out)
            }

            is AssignmentExpression -> {
                visitExpression(expr.target, out)
                visitExpression(expr.value, out)
            }

            is ArrayLiteral -> expr.elements.forEach { visitExpression(it, out) }
            is ObjectLiteral -> expr.properties.forEach { (_, v) -> visitExpression(v, out) }

            is ArrowFunction -> when (val body = expr.body) {
                is ArrowFunctionBody.ExpressionBody -> visitExpression(body.expression, out)
                is ArrowFunctionBody.BlockBody -> body.statements.forEach { visitStatement(it, out) }
            }

            is IfExpression -> {
                visitExpression(expr.condition, out)
                expr.thenBranch.forEach { visitStatement(it, out) }
                when (val e = expr.elseBranch) {
                    is ElseBranch.Block -> e.statements.forEach { visitStatement(it, out) }
                    is ElseBranch.If -> visitExpression(e.ifExpr, out)
                    null -> {}
                }
            }

            is TemplateLiteral -> for (part in expr.parts) {
                if (part is TemplatePart.Interp) visitExpression(part.expression, out)
            }

            else -> {}
        }
    }

    // ── Per-call check ───────────────────────────────────────────────────────

    private fun checkCall(call: CallExpression, out: MutableList<AnalyzerDiagnostic>) {
        val callable = resolveCallable(call) ?: return

        val positional = call.arguments.filterIsInstance<Argument.Positional>()
        val named = call.arguments.filterIsInstance<Argument.Named>()

        // Rule 1: no mixing. Point at the first arg whose style differs from
        // the call's initial style — that's the most likely typo.
        if (positional.isNotEmpty() && named.isNotEmpty()) {
            val firstKind = call.arguments.first()::class
            val firstOfOtherStyle = call.arguments.firstOrNull { it::class != firstKind }
            out += diagnostic(
                firstOfOtherStyle?.location ?: call.location,
                "Call to '${callable.name}' uses both positional and named arguments — pick one style",
            )
            return
        }

        // All-positional or zero args — no named-arg diagnostics needed.
        if (named.isEmpty()) return

        // Rule 2: unknown + duplicate named.
        val specNames = callable.params.map { it.name }.toSet()
        val seen = mutableSetOf<String>()
        var hasUnknown = false
        for (arg in named) {
            val loc = argLocation(arg, call)
            if (arg.name !in specNames) {
                val expectedHint = if (specNames.isEmpty()) {
                    "'${callable.name}' takes no parameters"
                } else {
                    "expected: ${specNames.joinToString(", ")}"
                }
                out += diagnostic(loc, "Unknown parameter '${arg.name}' on '${callable.name}' ($expectedHint)")
                hasUnknown = true
            }
            if (!seen.add(arg.name)) {
                out += diagnostic(loc, "Duplicate named argument: '${arg.name}'")
            }
        }

        // Rule 3: missing required — skip when unknown names were already
        // flagged, because the "expected: a, b, c" hint from Rule 2 already
        // tells the user which params exist. Firing both is just noise.
        if (hasUnknown) return

        val supplied = named.map { it.name }.toSet()
        val missing = callable.params
            .filter { !it.isOptional && !it.isVararg && it.name !in supplied }
            .map { it.name }
        if (missing.isNotEmpty()) {
            out += diagnostic(
                call.location,
                "Call to '${callable.name}' is missing required parameter(s): ${missing.joinToString(", ")}",
            )
        }
    }

    // ── Callable resolution — mirrors ExpressionTypeInferrer.inferCallExpression ─
    // Uses the pre-computed typeMap (from the same AnalyzedAst.build pass) to
    // resolve receiver types, avoiding a redundant second inference pass.

    private fun resolveCallable(call: CallExpression): KlangCallable? = when (val callee = call.callee) {
        is Identifier -> docs.getCallable(callee.name, receiverType = null)
        is MemberAccess -> {
            val objType = typeMap[callee.obj]
            if (objType != null) docs.getCallable(callee.property, objType) else null
        }

        else -> null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun argLocation(arg: Argument.Named, call: CallExpression): SourceLocation? =
        arg.nameLocation ?: arg.value.location ?: call.location

    private fun diagnostic(loc: SourceLocation?, message: String): AnalyzerDiagnostic = AnalyzerDiagnostic(
        message = message,
        severity = DiagnosticSeverity.ERROR,
        startLine = loc?.startLine ?: 1,
        startColumn = loc?.startColumn ?: 1,
        endLine = loc?.endLine ?: 1,
        endColumn = loc?.endColumn ?: 1,
    )
}
