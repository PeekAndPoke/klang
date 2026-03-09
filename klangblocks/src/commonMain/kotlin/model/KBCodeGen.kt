package io.peekandpoke.klang.blocks.model

// ── Public result type + lookup ───────────────────────────────────────────────

/**
 * The output of [KBProgram.toCodeGen].
 *
 * @param code              The generated source code string.
 * @param blockRanges       Maps each [KBCallBlock.id] to the [IntRange] of characters
 *                          it occupies in [code].  Nested blocks are included.
 * @param slotContentRanges Maps (blockId, slotIndex) to the [IntRange] of the string
 *                          *content* (excluding surrounding quotes/backticks) in [code].
 */
class CodeGenResult(
    val code: String,
    val blockRanges: Map<String, IntRange>,
    val slotContentRanges: Map<Pair<String, Int>, IntRange> = emptyMap(),
) {
    /** Full hit info returned by [findAt]. */
    data class HitResult(
        val blockId: String,
        /** Non-null when the hit falls inside a tracked string slot's content. */
        val slotIndex: Int?,
        /** 0-based char offset within the slot string content; non-null iff [slotIndex] is non-null. */
        val offsetInSlot: Int?,
    )

    // Ranges sorted by start offset — used by findByOffset for early-exit scan.
    // Stored as (start, lastInclusive, id) where lastInclusive = IntRange.last.
    private val sortedRanges: List<Triple<Int, Int, String>> by lazy {
        blockRanges.entries
            .map { (id, range) -> Triple(range.first, range.last, id) }
            .sortedBy { it.first }
    }

    // Caches (line, col) → HitResult; same location is queried on every audio tick.
    // Key packs both values into a single Long to avoid boxing.
    private val hitCache = mutableMapOf<Long, HitResult?>()

    /**
     * Returns the [KBCallBlock.id] whose generated-code range contains the given
     * 1-based [line]/[col] position, or null if no block covers that position.
     */
    fun findBlockAt(line: Int, col: Int): String? = findAt(line, col)?.blockId

    /**
     * Returns a [HitResult] for the given 1-based [line]/[col] position, including
     * which slot and offset within that slot when the location falls inside a string
     * argument's content.  Results are cached — repeated calls are O(1).
     */
    fun findAt(line: Int, col: Int): HitResult? {
        val key = line.toLong().shl(32) or (col.toLong() and 0xFFFFFFFFL)
        return hitCache.getOrPut(key) {
            val offset = code.lineColToCharOffset(line, col)
            val blockId = findByOffset(offset) ?: return@getOrPut null
            // Check whether the offset falls inside a string slot's content range.
            for ((pair, range) in slotContentRanges) {
                val (bid, slotIdx) = pair
                if (bid == blockId && offset in range) {
                    return@getOrPut HitResult(blockId, slotIdx, offset - range.first)
                }
            }
            HitResult(blockId, null, null)
        }
    }

    /**
     * Returns the **innermost** (smallest) block range containing [offset].
     *
     * Iterates ranges sorted by start offset with an early-exit once `start > offset`.
     * Among all matching ranges it picks the one with the smallest span — this correctly
     * resolves nested blocks (e.g. a slot-nested chain inside an outer block) where
     * multiple ranges overlap and we want the most specific one.
     *
     * Fix 1: uses `offset <= lastInclusive` (not `offset < lastInclusive`) so the last
     * character of every block is correctly matched (fixes the previous off-by-one).
     */
    private fun findByOffset(offset: Int): String? {
        var bestId: String? = null
        var bestSize = Int.MAX_VALUE
        for ((start, lastInclusive, id) in sortedRanges) {
            if (start > offset) break          // sorted — no subsequent range can start before offset
            if (offset <= lastInclusive) {     // offset is within [start, lastInclusive] inclusive
                val size = lastInclusive - start
                if (size < bestSize) {
                    bestId = id
                    bestSize = size
                }
            }
        }
        return bestId
    }
}

/** Converts a 1-based [line]/[col] pair to a 0-based character offset in this string. */
private fun String.lineColToCharOffset(line: Int, col: Int): Int {
    var currentLine = 1
    var i = 0
    while (i < length && currentLine < line) {
        if (this[i] == '\n') currentLine++
        i++
    }
    return i + (col - 1)
}

// ── Builder ───────────────────────────────────────────────────────────────────

class CodeBuilder {
    private val sb = StringBuilder()
    private val _blockRanges = mutableMapOf<String, IntRange>()
    private val _slotContentRanges = mutableMapOf<Pair<String, Int>, IntRange>()

    val length: Int get() = sb.length

    fun append(s: String): CodeBuilder = apply { sb.append(s) }
    fun append(c: Char): CodeBuilder = apply { sb.append(c) }

    /** Wraps [content] so the char range it produces is recorded under [blockId]. */
    fun trackBlock(blockId: String, content: CodeBuilder.() -> Unit): CodeBuilder = apply {
        val start = sb.length
        content()
        _blockRanges[blockId] = start until sb.length
    }

    /** Records the string content range (excluding quotes) for [blockId] / [slotIndex]. */
    fun trackSlotContent(blockId: String, slotIndex: Int, range: IntRange) {
        _slotContentRanges[blockId to slotIndex] = range
    }

    fun build() = CodeGenResult(sb.toString(), _blockRanges.toMap(), _slotContentRanges.toMap())
}

// ── Public API ────────────────────────────────────────────────────────────────

/** Convenience wrapper — keeps existing call-sites working. */
fun KBProgram.toCode(): String = toCodeGen().code

fun KBProgram.toCodeGen(): CodeGenResult {
    val builder = CodeBuilder()
    // Mirror the original mapNotNull { toCode() }.joinToString("\n") logic:
    // empty KBChainStmts are skipped, KBBlankLine contributes an empty line.
    val stmts = statements.filter { stmt ->
        stmt !is KBChainStmt || stmt.steps.filterIsInstance<KBCallBlock>().isNotEmpty()
    }
    stmts.forEachIndexed { index, stmt ->
        if (index > 0) builder.append("\n")
        stmt.appendTo(builder)
    }
    return builder.build()
}

// ── Leaf toCode() — no block IDs, unchanged ───────────────────────────────────

fun KBImportStmt.toCode(): String {
    val what = when {
        names != null -> "{${names.joinToString(", ")}}"
        alias != null -> "* as $alias"
        else -> "*"
    }
    return "import $what from \"$libraryName\""
}

fun KBArgValue.toCode(): String = when (this) {
    is KBEmptyArg -> ""
    is KBStringArg -> if ('\n' in value) "`$value`" else "\"$value\""
    is KBNumberArg -> {
        val long = value.toLong()
        if (value == long.toDouble()) long.toString() else value.toString()
    }
    is KBBoolArg -> value.toString()
    is KBIdentifierArg -> name
    is KBNestedChainArg -> chain.toCode() ?: ""  // fallback; appendTo handles tracking
    is KBBinaryArg -> "${left.toCode()} $op ${right.toCode()}"
    is KBUnaryArg -> when (position) {
        KBUnaryPosition.PREFIX -> "$op${operand.toCode()}"
        KBUnaryPosition.POSTFIX -> "${operand.toCode()}$op"
    }

    is KBTernaryArg -> "${condition.toCode()} ? ${thenExpr.toCode()} : ${elseExpr.toCode()}"
    is KBIndexAccessArg -> "${obj.toCode()}[${index.toCode()}]"
    is KBArrowFunctionArg -> {
        val paramsStr = if (params.size == 1) params[0] else "(${params.joinToString(", ")})"
        "$paramsStr => $bodySource"
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

private fun KBChainStmt.toCode(): String? {
    val blocks = steps.filterIsInstance<KBCallBlock>()
    if (blocks.isEmpty()) return null
    val builder = CodeBuilder()
    appendTo(builder)
    return builder.build().code
}

// ── Private appendTo — position-tracking tree walk ───────────────────────────

private fun KBStmt.appendTo(builder: CodeBuilder) {
    when (this) {
        is KBImportStmt -> builder.append(toCode())
        is KBChainStmt -> appendTo(builder)
        is KBLetStmt -> {
            builder.append("let $name")
            if (value != null) {
                builder.append(" = ")
                // Nested chains: inner blocks track themselves; no stmtId wrapper needed.
                // Scalar values (string, number, bool, …): wrap so stmtId appears in the
                // source map and string content atoms get a slot-content range.
                if (value is KBNestedChainArg) {
                    value.appendTo(builder, id, 0)
                } else {
                    builder.trackBlock(id) { value.appendTo(this, id, 0) }
                }
            }
        }

        is KBConstStmt -> {
            builder.append("const $name = ")
            if (value is KBNestedChainArg) {
                value.appendTo(builder, id, 0)
            } else {
                builder.trackBlock(id) { value.appendTo(this, id, 0) }
            }
        }
        is KBAssignStmt -> builder.append("$target = ").append(value.toCode())
        is KBExprStmt -> builder.append(expr.toCode())
        is KBBlankLine -> Unit
    }
}

private fun KBChainStmt.appendTo(builder: CodeBuilder) {
    // Emit string/identifier head without a trailing dot — the separator loop handles it.
    when (val head = steps.firstOrNull()) {
        is KBStringLiteralItem -> {
            val multiline = '\n' in head.value || '\r' in head.value
            val escaped = if (multiline) head.value else head.value
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
            builder.trackBlock(id) {
                append(if (multiline) "`" else "\"")
                val contentStart = length
                append(escaped)
                trackSlotContent(id, 0, contentStart until length)
                append(if (multiline) "`" else "\"")
            }
        }

        is KBIdentifierItem -> builder.append(head.name)
        else -> {}
    }

    // Walk the full step list; KBNewlineHint controls when the chain separator is \n  .
    // VERTICAL pocketLayout only affects arg rendering inside the block's () — it does NOT
    // force newlines between chain items.
    var newlinePending = false
    for (item in steps) {
        when (item) {
            is KBStringLiteralItem, is KBIdentifierItem -> {} // already emitted above
            is KBNewlineHint -> newlinePending = true
            is KBCallBlock -> {
                when {
                    item.isHead -> {}  // first call in chain, no receiver → no leading separator
                    newlinePending -> builder.append("\n  .")
                    else -> builder.append(".")
                }
                item.appendTo(builder)
                newlinePending = false
            }
        }
    }
}

private fun KBCallBlock.appendTo(builder: CodeBuilder) {
    builder.trackBlock(id) {
        // Pair each non-empty arg with its original slot index so string content
        // ranges are keyed by the same index used in the UI slot loop.
        val nonEmptyArgs = args.mapIndexedNotNull { slotIdx, arg ->
            if (arg is KBEmptyArg) null else slotIdx to arg
        }
        if (pocketLayout == KBPocketLayout.VERTICAL && nonEmptyArgs.isNotEmpty()) {
            append(funcName).append("(\n  ")
            nonEmptyArgs.forEachIndexed { i, (slotIdx, arg) ->
                if (i > 0) append(",\n  ")
                arg.appendTo(this, id, slotIdx)
            }
            append("\n)")
        } else {
            append(funcName).append("(")
            nonEmptyArgs.forEachIndexed { i, (slotIdx, arg) ->
                if (i > 0) append(", ")
                arg.appendTo(this, id, slotIdx)
            }
            append(")")
        }
    }
}

private fun KBArgValue.appendTo(builder: CodeBuilder, blockId: String, slotIndex: Int) {
    when (this) {
        is KBStringArg -> {
            // Write the surrounding quotes and record the content range (excluding quotes).
            val multiline = '\n' in value
            builder.append(if (multiline) "`" else "\"")
            val contentStart = builder.length
            builder.append(value)
            builder.trackSlotContent(blockId, slotIndex, contentStart until builder.length)
            builder.append(if (multiline) "`" else "\"")
        }

        // Recurse so nested block positions are tracked.
        is KBNestedChainArg -> {
            val blocks = chain.steps.filterIsInstance<KBCallBlock>()
            if (blocks.isNotEmpty()) chain.appendTo(builder)
        }

        else -> builder.append(toCode())
    }
}
