/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.strings.buildLineOffsets
import io.peekandpoke.klang.common.strings.lineColToOffset
import io.peekandpoke.klang.script.ast.Argument
import io.peekandpoke.klang.script.ast.ArrayLiteral
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.AssignmentExpression
import io.peekandpoke.klang.script.ast.AstIndex
import io.peekandpoke.klang.script.ast.AstNode
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
import io.peekandpoke.klang.script.types.KlangParam
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
class AnalyzedAst internal constructor(
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
    /** The program's scope, the root of the scope tree [localsAt] reads. */
    private val programScope: LocalScopeRange,
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
     * The locals visible at the cursor [pos] in [text]: the `let` / `const` / `export` names and the arrow parameters
     * of the innermost scope that encloses the cursor and of every scope around it, each declared above the cursor.
     * An inner binding shadows an outer one of the same name, so every name comes once; the innermost scope comes
     * first. Code completion's entry point for the song's own names.
     *
     * A name a closure only sees because it is declared further down ("Locals declared further down",
     * `ref/intel-analyzer.md`) is not offered: completion offers what is declared above the cursor.
     *
     * A scope's range runs from the token that opens it to the last token the analysis met inside it (the AST keeps
     * no closing brace), so a cursor on the blank stretch between a block's last statement and its `}` reads the
     * enclosing scope: fewer locals, never a wrong one.
     *
     * [text] is the editor's text, which can be ahead of the analysed [source]: while the text does not parse, the
     * editor keeps the last good analysis. The cursor is then mapped back across the edit (the text both share
     * before and after it): a cursor before or after the edit reads the same place in [source], a cursor inside it
     * reads the edited stretch, the innermost scope that holds all of it and the declarations above it. When the
     * typed text repeats the text around it, where the edit starts is ambiguous; the stretch then starts at the
     * earliest place it can. So the stale analysis gives fewer locals at worst, never one declared below the cursor
     * or in a sibling scope.
     */
    fun localsAt(pos: Int, text: String = source): List<TypeScope.LocalBinding> {
        val edit = editTo(text) ?: return localsIn(from = pos, to = pos)

        return when {
            pos <= edit.start -> localsIn(from = pos, to = pos)
            pos >= edit.newEnd -> (pos - edit.newEnd + edit.oldEnd).let { localsIn(from = it, to = it) }
            else -> localsIn(from = edit.start, to = edit.oldEnd)
        }
    }

    /**
     * Where the local that the word at the cursor [pos] in [text] reads is declared: go-to-definition's entry point,
     * and the hover's for a local. For a word that reads a `let` / `const` / `export` or an arrow parameter, the offset
     * of the declared name, the range of the declaration and its code ([LocalDefinition]). Null for a library name, a
     * member, a literal, a keyword and the declared name itself. The binding is the one the analysis resolved, so an
     * inner declaration shadows an outer one, and a closure's read of a name declared further down finds it there
     * ("Locals declared further down", `ref/intel-analyzer.md`).
     *
     * [text] is the editor's text, which can be ahead of the analysed [source] while it does not parse ([localsAt]).
     * The word and the declaration are mapped across the edit: before it they keep their offsets, after it they move
     * with it, and a word or a declaration the edit touches answers null (no jump rather than a wrong one). The
     * answer's offsets and code are [text]'s.
     */
    fun definitionAt(pos: Int, text: String = source): LocalDefinition? {
        var wordStart = pos.coerceIn(0, text.length)
        var wordEnd = wordStart

        while (wordStart > 0 && text[wordStart - 1].code.isWordChar()) {
            wordStart--
        }

        while (wordEnd < text.length && text[wordEnd].code.isWordChar()) {
            wordEnd++
        }

        if (wordStart == wordEnd) {
            return null
        }

        val edit = editTo(text)

        // The word as the analysis read it: in place before the edit, moved back after it
        val wordShift = when {
            edit == null || wordEnd <= edit.start -> 0
            wordStart >= edit.newEnd -> edit.oldEnd - edit.newEnd
            else -> return null
        }

        val analysedStart = wordStart + wordShift
        val identifier = astIndex.nodeAt(analysedStart) as? Identifier ?: return null
        val isSameWord = identifier.name == text.substring(wordStart, wordEnd) &&
                astIndex.offsetOf(identifier) == analysedStart until (wordEnd + wordShift)

        if (!isSameWord) {
            return null
        }

        val binding = bindingMap[identifier] ?: return null
        val site = binding.site ?: return null

        // The declaration in the editor's text: in place before the edit, moved with it after it
        val siteShift = when {
            edit == null || site.end <= edit.start -> 0
            site.start >= edit.oldEnd -> edit.newEnd - edit.oldEnd
            else -> return null
        }

        val start = site.start + siteShift
        val end = site.end + siteShift

        return LocalDefinition(
            namePos = site.namePos + siteShift,
            start = start,
            end = end,
            code = LocalDefinition.codeOf(text = text, start = start, end = end),
            symbol = synthesizeLocalSymbol(binding),
        )
    }

    /**
     * The parameters a named argument can still fill at the cursor [pos] in [text], in declaration order: the
     * parameters of the called callable that no argument of the call names. Code completion's entry point for
     * `name = ` in an argument list. [pos] is the start of the word being typed (or the cursor, when there is none).
     *
     * Empty unless the cursor stands where a new argument starts in a call to a callable the analysis resolves, by
     * the resolution the named-argument check uses ([callableCalledBy]): right after the `(` or after a `,`, with
     * nothing but blanks, comments and the word at [pos] in that slot ([ArgumentSlotAt]). That word can also be a value
     * of one word (`(|800)`): a name then goes in front of it (`freq = 800`). Empty as well when another
     * argument of the call is positional: the check refuses a call that mixes the two styles (all-or-nothing), so
     * there a name is never right. The innermost open `(` is the call: in a nested call's argument list the nested
     * callable's parameters apply. A vararg parameter is offered like any other: by name it takes an array.
     *
     * [text] is the editor's text, which can be ahead of the analysed [source] while it does not parse ([editTo]). The
     * argument list is read from [text]; the callee only from the analysis, and only when the edit cannot have
     * changed it: the edit lies after the call's `(` (an edit in front of it can change the receiver, turn a grouping
     * paren into a call, or declare a local that shadows the callee), and the call is not inside an arrow that a
     * `let` / `const` / `export` binds (such a body can read a declaration from further down, the edit's included).
     * The text up to the `(` is then the analysed text, offsets included. Otherwise nothing is offered until the text
     * parses again: fewer names, never a wrong callable's.
     */
    fun namedArgumentsAt(pos: Int, text: String = source): List<KlangParam> {
        val slot = ArgumentSlotAt.scan(text = text, pos = pos) ?: return emptyList()

        if (slot.hasPositional) {
            return emptyList()
        }

        val paren = slot.openParen
        val edit = editTo(text)

        if (edit != null && paren >= edit.start) {
            return emptyList()
        }

        // The `(` token is zero-width, so the index covers a call from its callee to its last argument, and the node at
        // a grouping paren can be the call around it: only the call whose own `(` stands there
        val call = astIndex.nodeAt(paren) as? CallExpression ?: return emptyList()

        if (parenOf(call) != paren) {
            return emptyList()
        }

        val ancestors = generateSequence(astIndex.parentOf(call)) { astIndex.parentOf(it) }.toList()

        // Inside a template literal's `${...}` the offsets count from the interpolation, not the source
        if (ancestors.any { it is TemplateLiteral }) {
            return emptyList()
        }

        if (edit != null && isInDeclaredArrow(ancestors)) {
            return emptyList()
        }

        val callable = registry.callableCalledBy(call = call, typeOf = { typeMap[it] }, isLocal = { it in bindingMap })
            ?: return emptyList()

        return callable.params.filter { it.name !in slot.namedArgs }
    }

    /** The source offset of [call]'s own `(` (its location), or null. */
    private fun parenOf(call: CallExpression): Int? {
        val location = call.location ?: return null

        return lineColToOffset(lineOffsets = lineOffsets, line = location.startLine, column = location.startColumn)
    }

    /**
     * Whether [ancestors] (a node's parents, innermost first) hold an arrow that a `let` / `const` / `export` binds:
     * the node then runs later than it is written and can read a declaration from further down ("Locals declared
     * further down", `ref/intel-analyzer.md`).
     */
    private fun isInDeclaredArrow(ancestors: List<AstNode>): Boolean = ancestors.zipWithNext().any { (node, parent) ->
        node is ArrowFunction && (parent is LetDeclaration || parent is ConstDeclaration || parent is ExportDeclaration)
    }

    /**
     * The edit that turned the analysed [source] into the editor's [text], or null when they are the same: it replaced
     * `source[start, oldEnd)` with `text[start, newEnd)`. Found by the text both share before and after it. The shared
     * prefix and suffix can overlap (`let ` typed in front of `let b`): any start from `shorter - rawSuffix` up to the
     * prefix fits, so the edit takes the earliest start, and the latest end. Several separate edits since the last
     * good parse count as one stretch.
     */
    private fun editTo(text: String): SourceEdit? {
        if (text == source) {
            return null
        }

        val prefix = source.commonPrefixWith(text).length
        val rawSuffix = source.commonSuffixWith(text).length
        val shorter = minOf(source.length, text.length)
        val suffix = minOf(rawSuffix, shorter - prefix)

        return SourceEdit(
            start = minOf(prefix, shorter - rawSuffix),
            oldEnd = source.length - suffix,
            newEnd = text.length - suffix,
        )
    }

    /** One edit from the analysed source to the editor's text: `source[start, oldEnd)` became `text[start, newEnd)`. */
    private class SourceEdit(val start: Int, val oldEnd: Int, val newEnd: Int)

    /**
     * The locals of the innermost scope that holds all of `source[from, to]` and of the scopes around it, each
     * declared before [from].
     */
    private fun localsIn(from: Int, to: Int): List<TypeScope.LocalBinding> {
        var innermost = programScope
        var next = innermost.childContaining(from = from, to = to)

        while (next != null) {
            innermost = next
            next = innermost.childContaining(from = from, to = to)
        }

        val seen = mutableSetOf<String>()
        val result = mutableListOf<TypeScope.LocalBinding>()
        var scope: LocalScopeRange? = innermost

        while (scope != null) {
            // Inside one scope the latest declaration of a name above the cursor wins
            for (binding in scope.bindings.asReversed()) {
                val declPos = binding.declPos ?: continue

                if (declPos < from && seen.add(binding.name)) {
                    result.add(binding)
                }
            }

            scope = scope.parent
        }

        return result
    }

    /**
     * Resolve the receiver type for code completion triggered at offset [pos]
     * (typically the position immediately after a `.`).
     *
     * Wraps [getExpressionTypeEndingAt] so the completion source doesn't need
     * to know about [typeMap] or about local-binding shadowing.
     */
    fun receiverTypeBeforeDot(pos: Int): KlangType? = getExpressionTypeEndingAt(pos)

    private fun identifierNameOf(node: AstNode): String? = when (node) {
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
    private fun memberAccessFor(node: AstNode, name: String): MemberAccess? {
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
            val builder = TypeMapBuilder(
                inferrer = inferrer,
                lineOffsets = buildLineOffsets(source),
                codes = IntArray(source.length) { source[it].code },
            )
            builder.visitStatements(program.statements)
            val typeMap = builder.map
            val bindingMap = builder.bindingMap
            val diagnostics = if (computeDiagnostics) {
                NamedArgumentChecker(
                    registry,
                    typeMap,
                    localIdentifiers = bindingMap.keys + builder.interpolationLocals,
                ).check(program)
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
                programScope = builder.programScope,
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
 * shadowing of outer scopes and of registry globals), and beside it the tree of
 * [LocalScopeRange]s, each scope's place in the source and its bindings, for
 * [AnalyzedAst.localsAt]. Every binding carries its [DeclarationSite], for
 * [AnalyzedAst.definitionAt].
 */
private class TypeMapBuilder(
    private val inferrer: ExpressionTypeInferrer,
    private val lineOffsets: IntArray,
    /** The source as char codes, for reading a declaration's name and end back from the text ([DeclarationSite]). */
    private val codes: IntArray,
) {
    val map = mutableMapOf<Expression, KlangType?>()
    val bindingMap = mutableMapOf<Identifier, TypeScope.LocalBinding>()

    /**
     * The identifiers inside a template literal's `${...}` that read a local. They stay out of [bindingMap]: their
     * locations count from the interpolation, and an [Identifier] is a data class, so one equal to an identifier
     * outside (same name, line and column) would replace that one's entry. The named-argument check still treats them
     * as locals.
     */
    val interpolationLocals = mutableSetOf<Identifier>()

    /** The program's scope range, the root of the tree [AnalyzedAst.localsAt] reads. */
    val programScope = LocalScopeRange(start = 0, parent = null)

    private var scope: TypeScope = TypeScope()
    private var range: LocalScopeRange = programScope

    /**
     * Above 0 inside a template literal's `${...}`: the parser reads an interpolation on its own, so the locations
     * there count from the interpolation's start, not the source's. Those nodes widen no range, and a scope opened
     * there gets an empty range (fewer locals, never a wrong one).
     */
    private var interpolationDepth = 0

    /**
     * Run [block] in a child scope that opens at the source offset [start]. When it closes, the enclosing range
     * reaches at least as far as the child did.
     */
    private inline fun <T> withChildScope(start: Int, deferred: Boolean = false, block: () -> T): T {
        val saved = scope
        val savedRange = range
        scope = if (deferred) scope.deferredBody() else scope.child()
        range = LocalScopeRange(start = start, parent = savedRange).also { savedRange.children.add(it) }
        try {
            return block()
        } finally {
            savedRange.extendTo(range.end)
            scope = saved
            range = savedRange
        }
    }

    /** Source offset where [location] starts, or where the walk has got to when the node carries no location. */
    private fun startOf(location: SourceLocation?): Int {
        if (interpolationDepth > 0) {
            return range.end
        }

        location ?: return range.end

        return lineColToOffset(lineOffsets = lineOffsets, line = location.startLine, column = location.startColumn)
            ?: range.end
    }

    /** Widen the current scope range to the end of the token at [location]. */
    private fun extendRange(location: SourceLocation?) {
        if (interpolationDepth > 0) {
            return
        }

        location ?: return

        lineColToOffset(lineOffsets = lineOffsets, line = location.endLine, column = location.endColumn)?.let {
            range.extendTo(it)
        }
    }

    /** Bind [binding] in the current scope and record it in the current scope range. */
    private fun bindLocal(binding: TypeScope.LocalBinding): List<Identifier> {
        range.bindings.add(binding)

        return scope.bind(binding)
    }

    fun visitExpr(expr: Expression) {
        extendRange(expr.location)
        map[expr] = inferrer.inferType(expr, scope)
        if (expr is Identifier) {
            if (interpolationDepth > 0) {
                // No reader is remembered either: a declaration further down would write it into the map when reached
                if (scope.resolve(expr.name) != null) {
                    interpolationLocals.add(expr)
                }
            } else {
                scope.resolveReference(expr)?.let { bindingMap[expr] = it }
            }
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
                // The `else` block opens where the `if` block's last token ends
                val thenEnd = withChildScope(start = startOf(expr.location)) {
                    visitStatements(expr.thenBranch)
                    range.end
                }
                when (val e = expr.elseBranch) {
                    is ElseBranch.Block -> withChildScope(start = thenEnd) { visitStatements(e.statements) }
                    is ElseBranch.If -> visitExpr(e.ifExpr)
                    null -> {}
                }
            }

            is TemplateLiteral -> {
                interpolationDepth++
                try {
                    for (part in expr.parts) {
                        if (part is TemplatePart.Interp) {
                            visitExpr(part.expression)
                        }
                    }
                } finally {
                    interpolationDepth--
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
    ) = withChildScope(start = startOf(expr.location), deferred = deferred) {
        // A parameter is declared at the arrow: visible in the whole body
        val declPos = range.start
        val sites = paramSitesOf(expr = expr, arrowStart = declPos)
        expr.parameters.forEachIndexed { i, p ->
            bindLocal(
                TypeScope.LocalBinding(
                    name = p,
                    type = paramTypes?.getOrNull(i),
                    kind = KlangSymbol.LocalKind.PARAM,
                    declPos = declPos,
                    site = sites?.getOrNull(i),
                )
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

    /**
     * Where a `let` / `const` / `export` becomes visible, called right after its [initializer] was visited. An arrow
     * initializer sees its own name (a recursive local, `const f = () => f()`): the keyword at [stmtStart]. Any other
     * initializer runs before the interpreter defines the name, so the name is visible after the initializer's last
     * token (`let osc = run(os` offers no `osc`, `let g = g.gain(2)` reads the outer `g`).
     */
    private fun declPosOf(initializer: Expression?, stmtStart: Int): Int {
        return if (initializer == null || initializer is ArrowFunction) stmtStart else range.end
    }

    /**
     * Where the `let` / `const` / `export` [stmt], whose keyword starts at [stmtStart], declares [name]. Called right after
     * its initializer was visited: the range's end is then the end of the statement's last token the walk met. Null
     * inside a template interpolation (the offsets there count from the interpolation) and without a location.
     */
    private fun statementSiteOf(stmt: Statement, name: String, stmtStart: Int): DeclarationSite? {
        if (interpolationDepth > 0 || stmt.location == null) {
            return null
        }

        return DeclarationSite.ofStatement(codes = codes, keywordStart = stmtStart, name = name, lastEnd = range.end)
    }

    /** Where the parameters of [expr], whose `=>` starts at [arrowStart], are declared; null as for [statementSiteOf]. */
    private fun paramSitesOf(expr: ArrowFunction, arrowStart: Int): List<DeclarationSite>? {
        if (interpolationDepth > 0 || expr.location == null) {
            return null
        }

        return DeclarationSite.ofArrowParams(codes = codes, arrowStart = arrowStart, params = expr.parameters)
    }

    /** Bind a declaration, and give the identifiers that read it early from a deferred body its binding and type. */
    private fun bindDeclaration(binding: TypeScope.LocalBinding) {
        bindLocal(binding).forEach { reader ->
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
        extendRange(stmt.location)
        // Where the statement's keyword starts: a declaration's position, a loop scope's start
        val stmtStart = startOf(stmt.location)

        when (stmt) {
            is ExpressionStatement -> visitExpr(stmt.expression)
            is LetDeclaration -> {
                stmt.initializer?.let { visitDeferrable(it) }
                val type = stmt.initializer?.let { map[it] }
                bindDeclaration(
                    TypeScope.LocalBinding(
                        name = stmt.name,
                        type = type,
                        kind = KlangSymbol.LocalKind.LET,
                        declPos = declPosOf(initializer = stmt.initializer, stmtStart = stmtStart),
                        site = statementSiteOf(stmt = stmt, name = stmt.name, stmtStart = stmtStart),
                    )
                )
            }

            is ConstDeclaration -> {
                visitDeferrable(stmt.initializer)
                bindDeclaration(
                    TypeScope.LocalBinding(
                        name = stmt.name,
                        type = map[stmt.initializer],
                        kind = KlangSymbol.LocalKind.CONST,
                        declPos = declPosOf(initializer = stmt.initializer, stmtStart = stmtStart),
                        site = statementSiteOf(stmt = stmt, name = stmt.name, stmtStart = stmtStart),
                    )
                )
            }

            is ExportDeclaration -> {
                visitDeferrable(stmt.initializer)
                bindDeclaration(
                    TypeScope.LocalBinding(
                        name = stmt.name,
                        type = map[stmt.initializer],
                        kind = KlangSymbol.LocalKind.EXPORT,
                        declPos = declPosOf(initializer = stmt.initializer, stmtStart = stmtStart),
                        site = statementSiteOf(stmt = stmt, name = stmt.name, stmtStart = stmtStart),
                    )
                )
            }

            is ReturnStatement -> stmt.value?.let { visitExpr(it) }
            is WhileStatement -> {
                visitExpr(stmt.condition)
                withChildScope(start = stmtStart) { visitStatements(stmt.body) }
            }

            is DoWhileStatement -> {
                withChildScope(start = stmtStart) { visitStatements(stmt.body) }
                visitExpr(stmt.condition)
            }

            is ForStatement -> withChildScope(start = stmtStart) {
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
