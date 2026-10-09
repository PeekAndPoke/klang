/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.common.strings.buildLineOffsets
import io.peekandpoke.klang.common.strings.lineColToOffset
import io.peekandpoke.klang.script.ast.Argument
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
import io.peekandpoke.klang.script.runtime.ArgAlignment
import io.peekandpoke.klang.script.ast.TernaryExpression
import io.peekandpoke.klang.script.ast.UnaryOperation
import io.peekandpoke.klang.script.ast.WhileStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
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
    /**
     * Identifier-reference → local binding it resolved to.
     *
     * Populated for every [Identifier] occurrence that the lexical scope walk
     * matched (i.e. references to a `let` / `const` / `export` / arrow-param
     * binding in scope at that position). Used by [symbolAt] to render the
     * popup as a local instead of looking the name up in the registry.
     */
    private val bindingMap: Map<Identifier, TypeScope.LocalBinding>,
    /** Diagnostics from static analysis (errors, warnings, hints). */
    val diagnostics: List<AnalyzerDiagnostic>,
) {
    /** Cached line offsets for line/col → offset conversion. */
    private val lineOffsets: IntArray by lazy { buildLineOffsets(source) }

    /** Look up the pre-computed type for an expression node. */
    fun typeOf(expr: Expression): KlangType? = typeMap[expr]

    /**
     * Resolve the docs/popup symbol for the cursor position [pos].
     *
     * Single entry point that callers (hover, doc popup) consume to keep the
     * decision logic — member-access receiver filtering, local-binding
     * shadowing, strict policy when receiver doesn't match — out of the UI.
     *
     * Resolution order:
     * 1. If the node at [pos] is the `property` side of a `MemberAccess` and
     *    the receiver's type is known → registry-filtered symbol restricted to
     *    variants matching that receiver. Strict: if no variant matches, returns
     *    `null` rather than leaking unrelated DSL variants.
     * 2. If the node is an [Identifier] resolved to a local binding → a
     *    synthesised [KlangSymbol] with `origin = KlangSymbol.Origin.Local`.
     *    Locals shadow same-named registry entries.
     * 3. Otherwise → the registry symbol narrowed to what the name is at that position: a member of an unknown
     *    receiver shows the methods ([KlangDocsRegistry.memberView]), a bare name its top-level variants and,
     *    for a second name of a callable object, the object's call form ([KlangDocsRegistry.topLevelView]).
     */
    fun symbolAt(pos: Int): KlangSymbol? {
        val node = astIndex.nodeAt(pos) ?: return null
        val name = identifierNameOf(node) ?: return null

        // Case 1: cursor sits on the property side of a MemberAccess.
        val memberAccess = memberAccessFor(node, name)
        if (memberAccess != null) {
            val receiverType = typeMap[memberAccess.obj]
            if (receiverType != null) {
                return registry.getSymbolWithReceiver(name, receiverType)
            }

            // Receiver type unknown. A local value (the `x` of `x => x.gain(...)`) is no namespace: the methods of
            // that name. Anything else (a namespace import, `sp.note(...)`, calls the top-level form): the whole symbol.
            val receiverIsLocal = (memberAccess.obj as? Identifier)?.let { bindingMap[it] } != null

            return registry.get(name)?.let { if (receiverIsLocal) registry.memberView(it) else it }
        }

        // Case 2: cursor sits on an Identifier reference that was resolved to a
        // local binding by the type-map builder.
        if (node is Identifier) {
            bindingMap[node]?.let { return synthesizeLocalSymbol(it) }
        }

        // Case 3: a bare name, as it stands at the top level.
        return registry.get(name)?.let { registry.topLevelView(it) }
    }

    /** The local binding [identifier] resolved to (`let`, `const`, `export`, a lambda parameter), or null for a global. */
    fun localBindingOf(identifier: Identifier): TypeScope.LocalBinding? = bindingMap[identifier]

    /**
     * Resolve the receiver type for code completion triggered at offset [pos]
     * (typically the position immediately after a `.`).
     *
     * Wraps [getExpressionTypeEndingAt] so the completion source doesn't need
     * to know about [typeMap] or about local-binding shadowing.
     */
    fun receiverTypeBeforeDot(pos: Int): KlangType? = getExpressionTypeEndingAt(pos)

    private fun identifierNameOf(node: io.peekandpoke.klang.script.ast.AstNode): String? = when (node) {
        is Identifier -> node.name
        is MemberAccess -> node.property
        is CallExpression -> (node.callee as? MemberAccess)?.property
        else -> null
    }

    /**
     * Returns the MemberAccess whose `property` slot is the symbol under the
     * cursor: when the cursor is sitting on that property, directly on the
     * MemberAccess or wrapped in a CallExpression `obj.foo(...)`.
     */
    private fun memberAccessFor(node: io.peekandpoke.klang.script.ast.AstNode, name: String): MemberAccess? {
        if (node is MemberAccess && node.property == name) return node
        if (node is CallExpression) {
            val cm = node.callee as? MemberAccess
            if (cm != null && cm.property == name) return cm
        }
        // No parent branch: the index holds only a MemberAccess's `obj` under it, the receiver, never the member
        return null
    }

    private fun synthesizeLocalSymbol(binding: TypeScope.LocalBinding): KlangSymbol {
        return KlangSymbol(
            name = binding.name,
            category = "local",
            origin = KlangSymbol.Origin.Local(kind = binding.kind),
            variants = listOf(
                KlangProperty(
                    name = binding.name,
                    owner = null,
                    type = binding.type ?: KlangType("?"),
                )
            ),
        )
    }

    /**
     * Get the inferred type of the deepest expression at a 1-based line/column position.
     *
     * Returns null if the position doesn't map to an expression or the type is unknown.
     */
    fun getTypeAt(line: Int, col: Int): KlangType? {
        val offset = lineColToOffset(lineOffsets = lineOffsets, line = line, column = col) ?: return null
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
     * Used for dot-completion: when the user types a dot after `Ignitor.sine()`, the cursor
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
            val builder = TypeMapBuilder(inferrer)
            builder.visitStatements(program.statements)
            val typeMap = builder.map
            val bindingMap = builder.bindingMap
            val diagnostics = if (computeDiagnostics) {
                NamedArgumentChecker(registry, typeMap, localIdentifiers = bindingMap.keys).check(program)
            } else {
                emptyList()
            }
            return AnalyzedAst(
                ast = program,
                source = source,
                astIndex = astIndex,
                registry = registry,
                typeMap = typeMap,
                bindingMap = bindingMap,
                diagnostics = diagnostics,
            )
        }
    }
}

// ── Type map builder ───────────────────────────────────────────────────────

/**
 * Walks the AST building two parallel maps:
 *  - [map]        : `Expression → KlangType?` for every expression encountered.
 *  - [bindingMap] : `Identifier → LocalBinding` for every identifier reference
 *                   that resolved to a lexical (let/const/export/arrow-param)
 *                   binding in scope at that point.
 *
 * The builder maintains a [TypeScope] stack mirroring the interpreter's
 * `runtime/Environment.kt` lookup rules (block-scope for let/const/export,
 * shadowing of outer scopes and of registry globals).
 */
private class TypeMapBuilder(private val inferrer: ExpressionTypeInferrer) {
    val map = mutableMapOf<Expression, KlangType?>()
    val bindingMap = mutableMapOf<Identifier, TypeScope.LocalBinding>()

    private var scope: TypeScope = TypeScope()

    private inline fun <T> withChildScope(deferred: Boolean = false, block: () -> T): T {
        val saved = scope
        scope = if (deferred) scope.deferredBody() else scope.child()
        try {
            return block()
        } finally {
            scope = saved
        }
    }

    fun visitExpr(expr: Expression) {
        map[expr] = inferrer.inferType(expr, scope)
        if (expr is Identifier) {
            scope.resolveReference(expr)?.let { bindingMap[expr] = it }
        }
        // Recurse into children
        when (expr) {
            is CallExpression -> {
                visitExpr(expr.callee)
                // Resolving the callee re-infers the receiver chain; only pay for it when a
                // lambda argument can actually use the declared parameter types.
                val expected = if (expr.arguments.any { it.value is ArrowFunction }) expectedArgumentTypes(expr) else null
                expr.arguments.forEachIndexed { i, arg ->
                    val value = arg.value
                    val lambdaParams = expected?.getOrNull(i)?.functionParams
                    if (value is ArrowFunction && lambdaParams != null) {
                        // A lambda passed where the callee declares a function type: its
                        // parameters take the declared types, so `x.` completes inside.
                        map[value] = inferrer.inferType(value, scope)
                        visitArrowBody(value, lambdaParams)
                    } else {
                        visitExpr(value)
                    }
                }
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

            // An arrow with no expected type: its parameters are untyped locals that still shadow same-named
            // registry symbols. Here it runs where it is written (an argument, an IIFE, an array element); an arrow
            // bound by a declaration is visited by [visitDeferrable].
            is ArrowFunction -> visitArrowBody(expr, paramTypes = null)

            is IfExpression -> {
                visitExpr(expr.condition)
                withChildScope { visitStatements(expr.thenBranch) }
                when (val e = expr.elseBranch) {
                    is ElseBranch.Block -> withChildScope { visitStatements(e.statements) }
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

    /**
     * Visit an arrow function's body in a child scope, binding its parameters. When
     * [paramTypes] is given (the arrow is an argument to a callable whose parameter is
     * function-typed) the parameters are bound WITH those types; extra parameters beyond
     * the declared arity bind untyped (the runtime binds them to null).
     */
    private fun visitArrowBody(
        expr: ArrowFunction,
        paramTypes: List<KlangType>?,
        deferred: Boolean = false,
    ) = withChildScope(deferred = deferred) {
        expr.parameters.forEachIndexed { i, p ->
            scope.bind(
                TypeScope.LocalBinding(name = p, type = paramTypes?.getOrNull(i), kind = KlangSymbol.LocalKind.PARAM)
            )
        }
        when (val body = expr.body) {
            is ArrowFunctionBody.ExpressionBody -> visitExpr(body.expression)
            is ArrowFunctionBody.BlockBody -> visitStatements(body.statements)
        }
    }

    /**
     * Visit the initialiser of a `let` / `const` / `export`. An arrow there runs later than where it is written, so its
     * body is deferred ([TypeScope.deferredBody]) and sees a local the declaring scope declares further down. Every
     * other arrow runs where it is written (a call argument, an IIFE) and sees what the runtime sees there. A returned
     * arrow is not deferred: the only later declarations it could see follow its `return`, and those never run.
     */
    private fun visitDeferrable(expr: Expression) {
        if (expr is ArrowFunction) {
            map[expr] = inferrer.inferType(expr, scope)
            visitArrowBody(expr, paramTypes = null, deferred = true)
        } else {
            visitExpr(expr)
        }
    }

    /** Bind a declaration, and give the identifiers that read it early from a deferred body its binding and type. */
    private fun bindDeclaration(binding: TypeScope.LocalBinding) {
        scope.bind(binding).forEach { reader ->
            bindingMap[reader] = binding
            map[reader] = binding.type
        }
    }

    /**
     * The declared parameter type each argument of [call] binds to, aligned with
     * `call.arguments`, or null when the callee is unknown or the call mixes named and
     * positional arguments (an error the checker reports elsewhere).
     *
     * Positional alignment goes through [ArgAlignment] so the editor binds a trailing
     * lambda to the same parameter the interpreter does. Arguments past the declared
     * parameters fall into a trailing vararg parameter when there is one.
     */
    private fun expectedArgumentTypes(call: CallExpression): List<KlangType?>? {
        val callable = inferrer.resolveCallable(call, scope) ?: return null
        val params = callable.params
        val args = call.arguments
        if (args.isEmpty()) {
            return emptyList()
        }
        if (args.all { it is Argument.Named }) {
            return args.map { arg -> params.firstOrNull { it.name == (arg as Argument.Named).name }?.type }
        }
        if (!args.all { it is Argument.Positional }) {
            return null
        }

        val varargType = params.lastOrNull()?.takeIf { it.isVararg }?.type
        // The interpreter's vararg branch maps positionally and never floats (see ArgAlignment).
        val targets = if (varargType != null) {
            List(args.size) { it }
        } else {
            ArgAlignment.positionalTargets(
                argCount = args.size,
                paramCount = params.size,
                isFunctionArg = { args[it].value is ArrowFunction },
                isFunctionParam = { params[it].type.isFunction },
            )
        }
        return targets.map { t -> params.getOrNull(t)?.type ?: varargType }
    }

    /**
     * Visit the statements of one scope. Their declarations are announced first ([TypeScope.declareAhead]), so a
     * deferred body written above a declaration ([visitDeferrable]) sees the local it will see when it runs.
     */
    fun visitStatements(statements: List<Statement>) {
        statements.forEach { declareAhead(it) }
        statements.forEach { visitStmt(it) }
    }

    private fun declareAhead(stmt: Statement) {
        when (stmt) {
            is LetDeclaration -> scope.declareAhead(stmt.name, KlangSymbol.LocalKind.LET)
            is ConstDeclaration -> scope.declareAhead(stmt.name, KlangSymbol.LocalKind.CONST)
            is ExportDeclaration -> scope.declareAhead(stmt.name, KlangSymbol.LocalKind.EXPORT)
            else -> Unit
        }
    }

    fun visitStmt(stmt: Statement) {
        when (stmt) {
            is ExpressionStatement -> visitExpr(stmt.expression)
            is LetDeclaration -> {
                stmt.initializer?.let { visitDeferrable(it) }
                val type = stmt.initializer?.let { map[it] }
                bindDeclaration(TypeScope.LocalBinding(name = stmt.name, type = type, kind = KlangSymbol.LocalKind.LET))
            }

            is ConstDeclaration -> {
                visitDeferrable(stmt.initializer)
                bindDeclaration(
                    TypeScope.LocalBinding(
                        name = stmt.name, type = map[stmt.initializer], kind = KlangSymbol.LocalKind.CONST
                    )
                )
            }

            is ExportDeclaration -> {
                visitDeferrable(stmt.initializer)
                bindDeclaration(
                    TypeScope.LocalBinding(
                        name = stmt.name, type = map[stmt.initializer], kind = KlangSymbol.LocalKind.EXPORT
                    )
                )
            }

            is ReturnStatement -> stmt.value?.let { visitExpr(it) }
            is WhileStatement -> {
                visitExpr(stmt.condition)
                withChildScope { visitStatements(stmt.body) }
            }

            is DoWhileStatement -> {
                withChildScope { visitStatements(stmt.body) }
                visitExpr(stmt.condition)
            }

            is ForStatement -> withChildScope {
                // `for (let i = 0; ...)` — the init's binding must be visible to
                // condition/update/body, so they all share one scope started here.
                stmt.init?.let { visitStmt(it) }
                stmt.body.forEach { declareAhead(it) }
                stmt.condition?.let { visitExpr(it) }
                stmt.update?.let { visitExpr(it) }
                stmt.body.forEach { visitStmt(it) }
            }

            is ImportStatement, is ExportStatement, is BreakStatement, is ContinueStatement -> {}
        }
    }
}
