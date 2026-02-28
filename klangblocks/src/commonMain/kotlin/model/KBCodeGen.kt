package io.peekandpoke.klang.blocks.model

// ── Public result type + lookup ───────────────────────────────────────────────

/**
 * The output of [KBProgram.toCodeGen].
 *
 * @param code        The generated source code string.
 * @param blockRanges Maps each [KBCallBlock.id] to the [IntRange] of characters
 *                    it occupies in [code].  Nested blocks are included.
 */
class CodeGenResult(
    val code: String,
    val blockRanges: Map<String, IntRange>,
) {
    // Ranges sorted by start offset — built once on first lookup, enables binary search.
    private val sortedRanges: List<Triple<Int, Int, String>> by lazy {
        blockRanges.entries
            .map { (id, range) -> Triple(range.first, range.last, id) }
            .sortedBy { it.first }
    }

    // Caches (line, col) → blockId results; same location is queried on every audio tick.
    // Key packs both values into a single Long to avoid boxing.
    private val locationCache = mutableMapOf<Long, String?>()

    /**
     * Returns the [KBCallBlock.id] whose generated-code range contains the given
     * 1-based [line]/[col] position, or null if no block covers that position.
     * Results are cached — repeated calls for the same location are O(1).
     */
    fun findBlockAt(line: Int, col: Int): String? {
        val key = line.toLong().shl(32) or (col.toLong() and 0xFFFFFFFFL)
        return locationCache.getOrPut(key) {
            findByOffset(code.lineColToCharOffset(line, col))
        }
    }

    /** Binary search over sorted ranges — O(log n) per unique (line, col). */
    private fun findByOffset(offset: Int): String? {
        var lo = 0
        var hi = sortedRanges.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val (start, end, id) = sortedRanges[mid]
            when {
                offset < start -> hi = mid - 1
                offset >= end -> lo = mid + 1   // IntRange is start until end (exclusive)
                else -> return id
            }
        }
        return null
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

    val length: Int get() = sb.length

    fun append(s: String): CodeBuilder = apply { sb.append(s) }
    fun append(c: Char): CodeBuilder = apply { sb.append(c) }

    /** Wraps [content] so the char range it produces is recorded under [blockId]. */
    fun trackBlock(blockId: String, content: CodeBuilder.() -> Unit): CodeBuilder = apply {
        val start = sb.length
        content()
        _blockRanges[blockId] = start until sb.length
    }

    fun build() = CodeGenResult(sb.toString(), _blockRanges.toMap())
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
    is KBUnaryArg -> "$op${operand.toCode()}"
    is KBArrowFunctionArg -> "(${params.joinToString(", ")}) => $bodySource"
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

private fun KBStmt.appendTo(builder: CodeBuilder) = when (this) {
    is KBImportStmt -> builder.append(toCode())
    is KBChainStmt -> appendTo(builder)
    is KBLetStmt -> builder.append("let $name")
    is KBConstStmt -> builder.append("const $name")
    is KBBlankLine -> Unit
}

private fun KBChainStmt.appendTo(builder: CodeBuilder) {
    val blocks = steps.filterIsInstance<KBCallBlock>()  // caller guarantees non-empty
    val stringHead = steps.firstOrNull() as? KBStringLiteralItem
    val hasVertical = blocks.any { it.pocketLayout == KBPocketLayout.VERTICAL }

    if (stringHead != null) {
        val escaped = stringHead.value
            .replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
        builder.append("\"$escaped\".")
    }

    blocks.forEachIndexed { index, block ->
        if (index > 0) builder.append(if (hasVertical) "\n  ." else ".")
        block.appendTo(builder)
    }
}

private fun KBCallBlock.appendTo(builder: CodeBuilder) {
    builder.trackBlock(id) {
        val filledArgs = args.filter { it !is KBEmptyArg }
        if (pocketLayout == KBPocketLayout.VERTICAL && filledArgs.isNotEmpty()) {
            append(funcName).append("(\n  ")
            filledArgs.forEachIndexed { index, arg ->
                if (index > 0) append(",\n  ")
                arg.appendTo(this)
            }
            append("\n)")
        } else {
            append(funcName).append("(")
            filledArgs.forEachIndexed { index, arg ->
                if (index > 0) append(", ")
                arg.appendTo(this)
            }
            append(")")
        }
    }
}

private fun KBArgValue.appendTo(builder: CodeBuilder) {
    when (this) {
        // Recurse so nested block positions are tracked.
        is KBNestedChainArg -> {
            val blocks = chain.steps.filterIsInstance<KBCallBlock>()
            if (blocks.isNotEmpty()) chain.appendTo(builder)
        }

        else -> builder.append(toCode())
    }
}
