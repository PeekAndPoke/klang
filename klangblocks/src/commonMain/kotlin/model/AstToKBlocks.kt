package io.peekandpoke.klang.blocks.model

import io.peekandpoke.klang.script.ast.*
import kotlin.random.Random

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

        is ExpressionStatement -> convertExprStmt(stmt.expression)

        // ReturnStatement / ExportStatement do not appear at top-level in user code
        else -> null
    }

    private fun convertExprStmt(expr: Expression): KBChainStmt? {
        val chain = extractChain(expr) ?: return null
        val steps = chain.mapIndexed { i, link ->
            KBCallBlock(
                id = uuid(),
                funcName = link.funcName,
                args = link.args.map { convertExpr(it) },
                isHead = i == 0,
                pocketLayout = layoutForLink(link),
            )
        }
        return KBChainStmt(id = uuid(), steps = steps)
    }

    // ---- Chain extraction -------------------------------------------

    private data class ChainLink(
        val funcName: String,
        val args: List<Expression>,
        val callLocation: SourceLocation?,
    )

    /**
     * Recursively unwraps a left-recursive chain of calls:
     *   sound("bd").gain(0.5)  →  [ChainLink("sound", ["bd"], loc), ChainLink("gain", [0.5], loc)]
     *
     * Returns null for any expression that is not a plain call chain
     * (e.g. arbitrary member access on non-call, object/array literals, etc.).
     */
    private fun extractChain(expr: Expression): List<ChainLink>? = when {
        expr is CallExpression && expr.callee is Identifier ->
            listOf(ChainLink((expr.callee as Identifier).name, expr.arguments, expr.location))

        expr is CallExpression && expr.callee is MemberAccess -> {
            val access = expr.callee as MemberAccess
            val prefix = extractChain(access.obj) ?: return null
            prefix + ChainLink(access.property, expr.arguments, expr.location)
        }

        else -> null
    }

    private fun layoutForLink(link: ChainLink): KBPocketLayout {
        if (link.args.isEmpty() || link.callLocation == null) return KBPocketLayout.HORIZONTAL
        val callLine = link.callLocation.startLine
        return if (link.args.all { it.location?.startLine == callLine }) {
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
        )

        is ArrowFunction -> KBArrowFunctionArg(
            params = expr.parameters,
            bodySource = expr.body.toSourceString(),
        )

        // Nested call chain → KBNestedChainArg
        is CallExpression, is MemberAccess -> {
            val chain = extractChain(expr)
            if (chain != null) {
                val steps = chain.mapIndexed { i, link ->
                    KBCallBlock(
                        id = uuid(),
                        funcName = link.funcName,
                        args = link.args.map { convertExpr(it) },
                        isHead = i == 0,
                        pocketLayout = layoutForLink(link),
                    )
                }
                KBNestedChainArg(KBChainStmt(id = uuid(), steps = steps))
            } else {
                KBStringArg(expr.toSourceString())
            }
        }

        // Object/array literals: fall back to raw source string
        is ObjectLiteral -> KBStringArg(expr.toSourceString())
        is ArrayLiteral -> KBStringArg(expr.toSourceString())
    }
}

// ---- BinaryOperator / UnaryOperator symbols -------------------------

private fun BinaryOperator.toSymbol(): String = when (this) {
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

private fun UnaryOperator.toSymbol(): String = when (this) {
    UnaryOperator.NEGATE -> "-"
    UnaryOperator.PLUS -> "+"
    UnaryOperator.NOT -> "!"
}

// ---- AST → source string (for arrow function bodies, fallbacks) -----

private fun ArrowFunctionBody.toSourceString(): String = when (this) {
    is ArrowFunctionBody.ExpressionBody -> expression.toSourceString()
    is ArrowFunctionBody.BlockBody ->
        "{ ${statements.joinToString("; ") { it.toSourceString() }} }"
}

private fun Statement.toSourceString(): String = when (this) {
    is ExpressionStatement -> expression.toSourceString()
    is ReturnStatement -> "return${value?.let { " ${it.toSourceString()}" } ?: ""}"
    is LetDeclaration -> "let $name${initializer?.let { " = ${it.toSourceString()}" } ?: ""}"
    is ConstDeclaration -> "const $name = ${initializer.toSourceString()}"
    else -> ""
}

internal fun uuid(): String =
    (0 until 16).joinToString("") { Random.nextInt(16).toString(16) }

private fun Expression.toSourceString(): String = when (this) {
    is StringLiteral -> "\"$value\""
    is NumberLiteral -> {
        val l = value.toLong()
        if (value == l.toDouble()) l.toString() else value.toString()
    }

    is BooleanLiteral -> value.toString()
    NullLiteral -> "null"
    is Identifier -> name
    is BinaryOperation ->
        "${left.toSourceString()} ${operator.toSymbol()} ${right.toSourceString()}"

    is UnaryOperation -> "${operator.toSymbol()}${operand.toSourceString()}"
    is MemberAccess -> "${obj.toSourceString()}.${property}"
    is CallExpression ->
        "${callee.toSourceString()}(${arguments.joinToString(", ") { it.toSourceString() }})"

    is ArrowFunction -> {
        val params = if (parameters.size == 1) parameters[0]
        else "(${parameters.joinToString(", ")})"
        "$params => ${body.toSourceString()}"
    }

    is ObjectLiteral ->
        "{ ${properties.joinToString(", ") { (k, v) -> "$k: ${v.toSourceString()}" }} }"

    is ArrayLiteral ->
        "[${elements.joinToString(", ") { it.toSourceString() }}]"
}
