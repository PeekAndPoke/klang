package io.peekandpoke.klang.blocks.model

import io.peekandpoke.klang.blocks.model.AstToKBlocks.extractChain
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.math.formatAsIntOrDouble
import io.peekandpoke.klang.script.ast.ArrayLiteral
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.AssignmentExpression
import io.peekandpoke.klang.script.ast.BinaryOperation
import io.peekandpoke.klang.script.ast.BinaryOperator
import io.peekandpoke.klang.script.ast.BooleanLiteral
import io.peekandpoke.klang.script.ast.BreakStatement
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.ConstDeclaration
import io.peekandpoke.klang.script.ast.ContinueStatement
import io.peekandpoke.klang.script.ast.DoWhileStatement
import io.peekandpoke.klang.script.ast.ElseBranch
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
import io.peekandpoke.klang.script.ast.UnaryOperator
import io.peekandpoke.klang.script.ast.WhileStatement

object AstToKBlocks {

    fun convert(program: Program): KBProgram {
        val result = mutableListOf<KBStmt>()
        program.statements.forEachIndexed { index, stmt ->
            if (index > 0) {
                val prevEnd = program.statements[index - 1].location?.endLine ?: 0
                val currStart = stmt.location?.startLine ?: 0
                if (currStart > prevEnd + 1) {
                    result.add(KBBlankLine(id = uuid()))
                }
            }
            convertStmt(stmt)?.let { result.add(it) }
        }
        return KBProgram(statements = result)
    }

    // ---- Statements -------------------------------------------------

    private fun convertStmt(stmt: Statement): KBStmt? = when (stmt) {
        is ImportStatement -> KBImportStmt(
            id = uuid(),
            libraryName = stmt.libraryName,
            alias = stmt.namespaceAlias,
            names = stmt.imports?.map { it.second }, // (exportName, localAlias) pairs
        )

        is LetDeclaration -> KBLetStmt(
            id = uuid(),
            name = stmt.name,
            value = stmt.initializer?.let { convertExpr(it) },
        )

        is ConstDeclaration -> KBConstStmt(
            id = uuid(),
            name = stmt.name,
            value = convertExpr(stmt.initializer),
        )

        is ExpressionStatement -> when (val inner = stmt.expression) {
            is AssignmentExpression -> KBAssignStmt(
                id = uuid(),
                target = inner.target.toSourceString(),
                value = convertExpr(inner.value),
            )

            else -> convertExprStmt(stmt.expression)
        }

        // ReturnStatement / ExportStatement do not appear at top-level in user code
        else -> null
    }

    private fun convertExprStmt(expr: Expression): KBStmt {
        val chain = extractChain(expr)
        if (chain != null) return KBChainStmt(id = uuid(), steps = chainResultToSteps(chain))
        // Non-chain expression statement (e.g. postfix x++, x--): preserve as KBExprStmt
        return KBExprStmt(id = uuid(), expr = convertExpr(expr))
    }

    // ---- Chain extraction -------------------------------------------

    private data class ChainLink(
        val funcName: String,
        val args: List<Expression>,
        val callLocation: SourceLocation?,
    )

    /** Result of [extractChain]: an optional head (string or identifier) plus the call sequence. */
    private data class ChainResult(
        val stringHead: String? = null,
        val identHead: String? = null,
        val links: List<ChainLink>,
    )

    /**
     * Recursively unwraps a left-recursive chain of calls, including an optional string
     * literal receiver at the head:
     *   sound("bd").gain(0.5)    →  ChainResult(null, [sound, gain])
     *   "C4".transpose(1).slow(2) →  ChainResult("C4", [transpose, slow])
     *
     * Returns null for any expression that is not a plain (possibly string-headed) call chain.
     */
    private fun extractChain(expr: Expression): ChainResult? = when {
        expr is CallExpression && expr.callee is Identifier ->
            ChainResult(links = listOf(ChainLink((expr.callee as Identifier).name, expr.arguments.map { it.value }, expr.location)))

        expr is CallExpression && expr.callee is MemberAccess -> {
            val access = expr.callee as MemberAccess
            val thisLink = ChainLink(access.property, expr.arguments.map { it.value }, expr.location)
            when {
                // String literal is the direct receiver: "C4".func(...)
                access.obj is StringLiteral ->
                    ChainResult(stringHead = (access.obj as StringLiteral).value, links = listOf(thisLink))
                // Bare identifier is the direct receiver: sine.func(...)
                access.obj is Identifier ->
                    ChainResult(identHead = (access.obj as Identifier).name, links = listOf(thisLink))
                // Deeper chain prefix
                else -> {
                    val prefix = extractChain(access.obj) ?: return null
                    ChainResult(stringHead = prefix.stringHead, identHead = prefix.identHead, links = prefix.links + thisLink)
                }
            }
        }

        else -> null
    }

    private fun chainResultToSteps(chain: ChainResult): List<KBChainItem> {
        val steps = mutableListOf<KBChainItem>()
        chain.stringHead?.let { steps.add(KBStringLiteralItem(it)) }
        chain.identHead?.let { steps.add(KBIdentifierItem(it)) }
        val hasHead = chain.stringHead != null || chain.identHead != null
        chain.links.forEachIndexed { i, link ->
            // Insert a KBNewlineHint when consecutive calls are on different source lines.
            if (i > 0) {
                val prevLine = chain.links[i - 1].callLocation?.startLine
                val currLine = link.callLocation?.startLine
                if (prevLine != null && currLine != null && currLine != prevLine) {
                    steps.add(KBNewlineHint)
                }
            }
            steps.add(
                KBCallBlock(
                    id = uuid(),
                    funcName = link.funcName,
                    args = link.args.map { convertExpr(it) },
                    isHead = !hasHead && i == 0,
                    pocketLayout = layoutForLink(link),
                )
            )
        }
        return steps
    }

    private fun layoutForLink(link: ChainLink): KBPocketLayout {
        if (link.args.isEmpty() || link.callLocation == null) return KBPocketLayout.HORIZONTAL
        val callLine = link.callLocation.startLine
        // Args with no location (e.g. NullLiteral singleton) are treated as same-line.
        return if (link.args.all { arg -> arg.location.let { it == null || it.startLine == callLine } }) {
            KBPocketLayout.HORIZONTAL
        } else {
            KBPocketLayout.VERTICAL
        }
    }

    // ---- Expression → KBArgValue ------------------------------------

    private fun convertExpr(expr: Expression): KBArgValue = when (expr) {
        is StringLiteral -> KBStringArg(expr.value)
        is NumberLiteral -> KBNumberArg(expr.value)
        is BooleanLiteral -> KBBoolArg(expr.value)
        NullLiteral -> KBIdentifierArg("null")
        is Identifier -> KBIdentifierArg(expr.name)

        is BinaryOperation -> KBBinaryArg(
            left = convertExpr(expr.left),
            op = expr.operator.toSymbol(),
            right = convertExpr(expr.right),
        )

        is UnaryOperation -> KBUnaryArg(
            op = expr.operator.toSymbol(),
            operand = convertExpr(expr.operand),
            position = when (expr.operator) {
                UnaryOperator.POSTFIX_INCREMENT, UnaryOperator.POSTFIX_DECREMENT ->
                    KBUnaryPosition.POSTFIX

                else -> KBUnaryPosition.PREFIX
            },
        )

        is TernaryExpression -> KBTernaryArg(
            condition = convertExpr(expr.condition),
            thenExpr = convertExpr(expr.thenExpr),
            elseExpr = convertExpr(expr.elseExpr),
        )

        is IndexAccess -> KBIndexAccessArg(
            obj = convertExpr(expr.obj),
            index = convertExpr(expr.index),
        )

        // Assignment as an expression (rare) — fall back to raw source
        is AssignmentExpression -> KBStringArg(expr.toSourceString())

        is ArrowFunction -> KBArrowFunctionArg(
            params = expr.parameters,
            bodySource = expr.body.toSourceString(),
        )

        // Nested call chain (possibly string-headed) → KBNestedChainArg
        is CallExpression, is MemberAccess -> {
            val chain = extractChain(expr)
            if (chain != null) {
                KBNestedChainArg(KBChainStmt(id = uuid(), steps = chainResultToSteps(chain)))
            } else {
                KBStringArg(expr.toSourceString())
            }
        }

        // Object/array literals: fall back to raw source string
        is ObjectLiteral -> KBStringArg(expr.toSourceString())
        is ArrayLiteral -> KBStringArg(expr.toSourceString())

        // New expression types: fall back to raw source string
        is IfExpression -> KBStringArg(expr.toSourceString())
        is TemplateLiteral -> KBStringArg(expr.toSourceString())
    }
}

// ---- BinaryOperator / UnaryOperator symbols -------------------------

private fun BinaryOperator.toSymbol(): String = when (this) {
    BinaryOperator.ADD -> "+"
    BinaryOperator.SUBTRACT -> "-"
    BinaryOperator.MULTIPLY -> "*"
    BinaryOperator.DIVIDE -> "/"
    BinaryOperator.MODULO -> "%"
    BinaryOperator.POWER -> "**"
    BinaryOperator.EQUAL -> "=="
    BinaryOperator.NOT_EQUAL -> "!="
    BinaryOperator.STRICT_EQUAL -> "==="
    BinaryOperator.STRICT_NOT_EQUAL -> "!=="
    BinaryOperator.LESS_THAN -> "<"
    BinaryOperator.LESS_THAN_OR_EQUAL -> "<="
    BinaryOperator.GREATER_THAN -> ">"
    BinaryOperator.GREATER_THAN_OR_EQUAL -> ">="
    BinaryOperator.AND -> "&&"
    BinaryOperator.OR -> "||"
    BinaryOperator.IN -> "in"
    BinaryOperator.BITWISE_AND -> "&"
    BinaryOperator.BITWISE_OR -> "|"
    BinaryOperator.BITWISE_XOR -> "^"
    BinaryOperator.SHIFT_LEFT -> "<<"
    BinaryOperator.SHIFT_RIGHT -> ">>"
    BinaryOperator.UNSIGNED_SHIFT_RIGHT -> ">>>"
    BinaryOperator.NULLISH_COALESCE -> "??"
}

private fun UnaryOperator.toSymbol(): String = when (this) {
    UnaryOperator.NEGATE -> "-"
    UnaryOperator.PLUS -> "+"
    UnaryOperator.NOT -> "!"
    UnaryOperator.PREFIX_INCREMENT -> "++"
    UnaryOperator.PREFIX_DECREMENT -> "--"
    UnaryOperator.POSTFIX_INCREMENT -> "++"
    UnaryOperator.POSTFIX_DECREMENT -> "--"
    UnaryOperator.BITWISE_NOT -> "~"
}

// ---- AST → source string (for arrow function bodies, fallbacks) -----

private fun ArrowFunctionBody.toSourceString(): String = when (this) {
    is ArrowFunctionBody.ExpressionBody -> expression.toSourceString()
    is ArrowFunctionBody.BlockBody ->
        "{ ${statements.joinToString("; ") { it.toSourceString() }} }"
}

private fun Statement.toSourceString(): String = when (this) {
    is ExpressionStatement -> expression.toSourceString()
    is LetDeclaration -> "let $name${initializer?.let { " = ${it.toSourceString()}" } ?: ""}"
    is ConstDeclaration -> "const $name = ${initializer.toSourceString()}"
    is ReturnStatement -> "return${value?.let { " ${it.toSourceString()}" } ?: ""}"
    is BreakStatement -> "break"
    is ContinueStatement -> "continue"
    is WhileStatement -> "while (${condition.toSourceString()}) { ${body.joinToString("; ") { it.toSourceString() }} }"
    is DoWhileStatement -> "do { ${body.joinToString("; ") { it.toSourceString() }} } while (${condition.toSourceString()})"
    is ForStatement -> {
        val initStr = init?.toSourceString() ?: ""
        val condStr = condition?.toSourceString() ?: ""
        val updateStr = update?.toSourceString() ?: ""
        "for ($initStr; $condStr; $updateStr) { ${body.joinToString("; ") { it.toSourceString() }} }"
    }

    is ImportStatement -> "import * from \"$libraryName\""
    is ExportStatement -> "export { ${exports.joinToString(", ") { (local, exp) -> if (local == exp) local else "$local as $exp" }} }"
}

private val idCounter = io.peekandpoke.klang.common.infra.KlangAtomicInt(0)

internal fun uuid(): String = "id-${idCounter.incrementAndGet()}"

private fun Expression.toSourceString(): String = when (this) {
    is StringLiteral -> "\"$value\""
    is NumberLiteral -> value.formatAsIntOrDouble()

    is BooleanLiteral -> value.toString()
    NullLiteral -> "null"
    is Identifier -> name
    is BinaryOperation ->
        "${left.toSourceString()} ${operator.toSymbol()} ${right.toSourceString()}"

    is UnaryOperation -> when (operator) {
        UnaryOperator.POSTFIX_INCREMENT, UnaryOperator.POSTFIX_DECREMENT ->
            "${operand.toSourceString()}${operator.toSymbol()}"

        else -> "${operator.toSymbol()}${operand.toSourceString()}"
    }

    is TernaryExpression ->
        "${condition.toSourceString()} ? ${thenExpr.toSourceString()} : ${elseExpr.toSourceString()}"

    is IndexAccess -> "${obj.toSourceString()}[${index.toSourceString()}]"
    is AssignmentExpression -> "${target.toSourceString()} = ${value.toSourceString()}"
    is MemberAccess -> "${obj.toSourceString()}.$property"
    is CallExpression ->
        "${callee.toSourceString()}(${arguments.joinToString(", ") { it.value.toSourceString() }})"

    is ArrowFunction -> {
        val params = if (parameters.size == 1) {
            parameters[0]
        } else {
            "(${parameters.joinToString(", ")})"
        }
        "$params => ${body.toSourceString()}"
    }

    is ObjectLiteral ->
        "{ ${properties.joinToString(", ") { (k, v) -> "$k: ${v.toSourceString()}" }} }"

    is ArrayLiteral ->
        "[${elements.joinToString(", ") { it.toSourceString() }}]"

    is IfExpression -> {
        val thenStr = thenBranch.joinToString("; ") { it.toSourceString() }
        val elseStr = when (val eb = elseBranch) {
            is ElseBranch.Block -> " else { ${eb.statements.joinToString("; ") { it.toSourceString() }} }"
            is ElseBranch.If -> " else ${eb.ifExpr.toSourceString()}"
            null -> ""
        }
        "if (${condition.toSourceString()}) { $thenStr }$elseStr"
    }

    is TemplateLiteral -> "`${
        parts.joinToString("") { part ->
            when (part) {
                is TemplatePart.Text -> part.value
                is TemplatePart.Interp -> "\${${part.expression.toSourceString()}}"
            }
        }
    }`"
}
