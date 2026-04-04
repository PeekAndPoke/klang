package io.peekandpoke.klang.sprudel.lang.parser

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.silence

// ── Backward-compatible public API ────────────────────────────────────────────

fun <T> parseMiniNotation(
    input: SprudelDslArg<T>,
    atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
): SprudelPattern = when (val v = input.value) {
    is SprudelPattern -> v
    null -> silence
    else -> parseMiniNotation(
        input = v.toString(),
        baseLocation = input.location,
        atomFactory = atomFactory,
    )
}

/** Shortcut for parsing mini notation into patterns */
fun parseMiniNotation(
    input: String,
    baseLocation: SourceLocation? = null,
    atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
): SprudelPattern = MnPatternToSprudelPattern.convert(
    pattern = parseMiniNotationMnPattern(input, baseLocation),
    baseLocation = baseLocation,
    atomFactory = atomFactory,
)

/** Phase 1 only — parses mini-notation string into an intermediate [MnPattern] tree. */
fun parseMiniNotationMnPattern(input: String, baseLocation: SourceLocation? = null): MnPattern =
    MiniNotationParser(input, baseLocation).parse()

// ── Parser ────────────────────────────────────────────────────────────────────

/**
 * Phase-1 parser: tokenises [input] and builds an [MnPattern] AST.
 *
 * No sprudel runtime dependency — the result can be used by the visual editor
 * and round-tripped through [MnRenderer] without ever touching [SprudelPattern].
 *
 * Phase 2 ([MnPatternToSprudelPattern]) converts the tree to a [SprudelPattern].
 *
 * [baseLocation] is optional and used only for error message formatting.
 */
class MiniNotationParser(
    private val input: String,
    private val baseLocation: SourceLocation? = null,
) {
    companion object {
        // Pre-computed char codes — avoids any ambiguity about runtime Char boxing.
        // Kotlin inlines 'x'.code as a compile-time Int constant, but named vals are clearer.
        private val C_LF = '\n'.code
        private val C_SPACE = ' '.code
        private val C_TAB = '\t'.code
        private val C_CR = '\r'.code
        private val C_LPAREN = '('.code
        private val C_RPAREN = ')'.code
        private val C_LBRACKET = '['.code
        private val C_RBRACKET = ']'.code
        private val C_LANGLE = '<'.code
        private val C_RANGLE = '>'.code
        private val C_COMMA = ','.code
        private val C_STAR = '*'.code
        private val C_TILDE = '~'.code
        private val C_AT = '@'.code
        private val C_PIPE = '|'.code
        private val C_QUESTION = '?'.code
        private val C_BANG = '!'.code
        private val C_SLASH = '/'.code
        private val C_DOT = '.'.code
        private val C_LBRACE = '{'.code
        private val C_RBRACE = '}'.code
        private val C_EQUALS = '='.code
        private val C_0 = '0'.code
        private val C_9 = '9'.code
    }

    private val tokens = tokenize(input)
    private var pos = 0

    // ── Public entry point ─────────────────────────────────────────────────

    fun parse(): MnPattern {
        if (tokens.isEmpty()) return MnPattern.Empty
        return MnPattern(parseExpression())
    }

    // ── Recursive descent ─────────────────────────────────────────────────

    /**
     * expression = sequence ( ',' sequence )*
     *
     * Returns a flat [List<MnNode>]. When commas are present the result is a
     * single-element list containing a [MnNode.Stack]; otherwise it is the
     * plain sequence produced by [parseSequence].
     */
    private fun parseExpression(): List<MnNode> {
        val first = parseSequence()
        if (!check(TokenType.COMMA)) return first
        val layers = mutableListOf(first)
        while (match(TokenType.COMMA)) layers.add(parseSequence())
        return listOf(MnNode.Stack(layers))
    }

    /** sequence = step* */
    private fun parseSequence(): List<MnNode> {
        val nodes = mutableListOf<MnNode>()
        while (!isAtEnd() && !check(TokenType.COMMA) && !check(TokenType.R_BRACKET) && !check(TokenType.R_ANGLE)) {
            nodes.addAll(parseStep())
        }
        return nodes
    }

    /**
     * Parses one step (base node + modifiers).
     *
     * Usually returns a single-element list.
     * Returns n copies when `!n` is the last modifier (bang expansion flattened into parent).
     */
    private fun parseStep(): List<MnNode> {
        var node = parseBaseNode()

        while (!isAtEnd() && isModifier()) {
            when {
                match(TokenType.STAR) -> {
                    val factor = consume(TokenType.LITERAL, "Expected number after '*'").text.toDoubleOrNull() ?: 1.0
                    node = node.withMod { copy(multiplier = (multiplier ?: 1.0) * factor) }
                }

                match(TokenType.SLASH) -> {
                    val factor = consume(TokenType.LITERAL, "Expected number after '/'").text.toDoubleOrNull() ?: 1.0
                    node = node.withMod { copy(divisor = (divisor ?: 1.0) * factor) }
                }

                match(TokenType.AT) -> {
                    val weight = consume(TokenType.LITERAL, "Expected number after '@'").text.toDoubleOrNull() ?: 1.0
                    node = node.withMod { copy(weight = weight) }
                }

                match(TokenType.QUESTION) -> {
                    val probStr = if (check(TokenType.LITERAL) && peek().text.firstOrNull()?.isDigit() == true) {
                        consume(TokenType.LITERAL, "").text
                    } else {
                        null
                    }
                    val probability = probStr?.toDoubleOrNull() ?: 0.5
                    node = node.withMod { copy(probability = probability) }
                }

                match(TokenType.PIPE) -> {
                    // Left-associative, flatten chains: a | b | c → Choice([a, b, c])
                    val rightItems = parseStep()
                    val right = rightItems.asSingleNode()
                    val rightOptions = if (right is MnNode.Choice) right.options else listOf(right)
                    node = when (node) {
                        is MnNode.Choice -> node.copy(options = node.options + rightOptions)
                        else -> MnNode.Choice(listOf(node) + rightOptions)
                    }
                }

                match(TokenType.BANG) -> {
                    val countStr = if (check(TokenType.LITERAL)) consume(TokenType.LITERAL, "").text else "2"
                    val count = countStr.toIntOrNull() ?: 2
                    // Produce a Repeat node; any following modifiers are applied to it via the outer loop
                    node = MnNode.Repeat(node, count)
                }

                match(TokenType.L_PAREN) -> {
                    val openPos = previous().start
                    val pulsesStr = consume(TokenType.LITERAL, "Expected pulses number", fromPosition = openPos).text
                    val pulses = pulsesStr.toIntOrNull() ?: parseError("Invalid pulses: $pulsesStr", fromPosition = openPos)
                    consume(TokenType.COMMA, "Expected ',' in Euclidean rhythm", fromPosition = openPos)
                    val stepsStr = consume(TokenType.LITERAL, "Expected steps number", fromPosition = openPos).text
                    val steps = stepsStr.toIntOrNull() ?: parseError("Invalid steps: $stepsStr", fromPosition = openPos)
                    var rotation = 0
                    if (match(TokenType.COMMA)) {
                        val rotStr = consume(TokenType.LITERAL, "Expected rotation number", fromPosition = openPos).text
                        rotation = rotStr.toIntOrNull() ?: parseError("Invalid rotation: $rotStr", fromPosition = openPos)
                    }
                    consume(TokenType.R_PAREN, "Expected ')' after Euclidean rhythm", fromPosition = openPos)
                    node = node.withMod { copy(euclidean = MnNode.Euclidean(pulses, steps, rotation)) }
                }

                match(TokenType.L_BRACE) -> {
                    val openPos = previous().start
                    val entries = linkedMapOf<String, String>()
                    while (!isAtEnd() && !check(TokenType.R_BRACE)) {
                        val key = consume(TokenType.LITERAL, "Expected attribute key", fromPosition = openPos).text
                        consume(TokenType.EQUALS, "Expected '=' after attribute key '$key'", fromPosition = openPos)
                        val value = consume(TokenType.LITERAL, "Expected value after '$key='", fromPosition = openPos).text
                        entries[key] = value
                    }
                    consume(TokenType.R_BRACE, "Expected '}' after attribute block", fromPosition = openPos)
                    node = node.withMod { copy(attrs = MnNode.Attrs(entries)) }
                }

                else -> break
            }
        }

        return listOf(node)
    }

    /** Parses the primary node: atom, group `[]`, alternation `<>`, or rest `~`. */
    private fun parseBaseNode(): MnNode = when {
        match(TokenType.L_BRACKET) -> {
            val openPos = previous().start
            val items = parseExpression()
            consume(TokenType.R_BRACKET, "Expected ']'", fromPosition = openPos)
            MnNode.Group(items)
        }

        match(TokenType.L_ANGLE) -> {
            val openPos = previous().start
            val items = parseAlternationItems()
            consume(TokenType.R_ANGLE, "Expected '>'", fromPosition = openPos)
            MnNode.Alternation(items)
        }

        match(TokenType.TILDE) -> MnNode.Rest(sourceRange = previous().start until previous().end)

        match(TokenType.LINEBREAK) -> MnNode.Linebreak()

        match(TokenType.LITERAL) -> {
            val token = previous()
            MnNode.Atom(
                value = token.text,
                sourceRange = token.start until token.end,
                sourceLine = token.line,
                sourceColumn = token.column,
            )
        }

        isAtEnd() -> parseError("Unexpected end of input")

        else -> parseError("Unexpected token: ${peek().text}")
    }

    /** Parses the items inside `< … >`, flattening any bang expansions. */
    private fun parseAlternationItems(): List<MnNode> {
        val items = mutableListOf<MnNode>()
        while (!isAtEnd() && !check(TokenType.R_ANGLE)) {
            items.addAll(parseStep())
        }
        return items
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun isModifier(): Boolean {
        if (isAtEnd()) return false
        return peek().type in modifierTypes
    }

    private fun List<MnNode>.asSingleNode(): MnNode = when {
        size == 1 -> this[0]
        else -> MnNode.Group(items = this)
    }

    private fun peek(): Token = tokens[pos]
    private fun previous(): Token = tokens[pos - 1]
    private fun isAtEnd(): Boolean = pos >= tokens.size

    private fun check(type: TokenType): Boolean = !isAtEnd() && peek().type == type

    private fun match(type: TokenType): Boolean {
        if (check(type)) {
            pos++
            return true
        }
        return false
    }

    private fun consume(type: TokenType, message: String, fromPosition: Int? = null): Token {
        if (check(type)) return tokens[pos++]
        parseError(message, fromPosition)
    }

    private fun parseError(message: String, fromPosition: Int? = null): Nothing {
        val charPos = if (pos < tokens.size) {
            tokens[pos].start
        } else if (tokens.isNotEmpty()) {
            tokens.last().start + tokens.last().text.length
        } else {
            0
        }
        throw MiniNotationParseException(
            message = message,
            startPosition = fromPosition ?: charPos,
            endPosition = charPos,
            baseLocation = baseLocation,
        )
    }

    // ── Token types ───────────────────────────────────────────────────────

    private enum class TokenType {
        L_BRACKET, R_BRACKET, L_ANGLE, R_ANGLE, L_PAREN, R_PAREN,
        L_BRACE, R_BRACE, EQUALS,
        COMMA, STAR, SLASH, TILDE, AT, PIPE, QUESTION, BANG, LITERAL, LINEBREAK
    }

    private val modifierTypes = setOf(
        TokenType.STAR,
        TokenType.SLASH,
        TokenType.AT,
        TokenType.L_PAREN,
        TokenType.QUESTION,
        TokenType.PIPE,
        TokenType.BANG,
        TokenType.L_BRACE,
    )

    private data class Token(
        val type: TokenType,
        val text: String,
        /** Start position in the input string (inclusive) */
        val start: Int,
        /** End position in the input string (exclusive) */
        val end: Int,
        /** Line number within the input string (1-based) */
        val line: Int,
        /** Column number within the line (1-based) */
        val column: Int,
    )

    // ── Tokenizer ─────────────────────────────────────────────────────────

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Convert to IntArray once: on Kotlin/JS, String[i] returns a boxed Char (heap-allocated
        // wrapper). IntArray[i] returns a plain JS number — no boxing per access.
        val codes = IntArray(input.length) { input[it].code }
        var i = 0
        var line = 1
        var column = 1

        fun addToken(type: TokenType, text: String, start: Int, end: Int, tokenLine: Int, tokenColumn: Int) {
            tokens.add(Token(type = type, text = text, start = start, end = end, line = tokenLine, column = tokenColumn))
        }

        while (i < input.length) {
            val c = codes[i]
            when (c) {
                C_LF -> {
                    addToken(TokenType.LINEBREAK, "\n", i, i + 1, line, column)
                    i++; line++; column = 1
                }

                C_SPACE, C_TAB, C_CR -> {
                    i++; column++
                }

                C_LPAREN -> {
                    addToken(TokenType.L_PAREN, "(", i, i + 1, line, column); i++; column++
                }

                C_RPAREN -> {
                    addToken(TokenType.R_PAREN, ")", i, i + 1, line, column); i++; column++
                }

                C_LBRACKET -> {
                    addToken(TokenType.L_BRACKET, "[", i, i + 1, line, column); i++; column++
                }

                C_RBRACKET -> {
                    addToken(TokenType.R_BRACKET, "]", i, i + 1, line, column); i++; column++
                }

                C_LANGLE -> {
                    addToken(TokenType.L_ANGLE, "<", i, i + 1, line, column); i++; column++
                }

                C_RANGLE -> {
                    addToken(TokenType.R_ANGLE, ">", i, i + 1, line, column); i++; column++
                }

                C_COMMA -> {
                    addToken(TokenType.COMMA, ",", i, i + 1, line, column); i++; column++
                }

                C_STAR -> {
                    addToken(TokenType.STAR, "*", i, i + 1, line, column); i++; column++
                }

                C_TILDE -> {
                    addToken(TokenType.TILDE, "~", i, i + 1, line, column); i++; column++
                }

                C_AT -> {
                    addToken(TokenType.AT, "@", i, i + 1, line, column); i++; column++
                }

                C_PIPE -> {
                    addToken(TokenType.PIPE, "|", i, i + 1, line, column); i++; column++
                }

                C_QUESTION -> {
                    addToken(TokenType.QUESTION, "?", i, i + 1, line, column); i++; column++
                }

                C_BANG -> {
                    addToken(TokenType.BANG, "!", i, i + 1, line, column); i++; column++
                }

                C_LBRACE -> {
                    addToken(TokenType.L_BRACE, "{", i, i + 1, line, column); i++; column++
                }

                C_RBRACE -> {
                    addToken(TokenType.R_BRACE, "}", i, i + 1, line, column); i++; column++
                }

                C_EQUALS -> {
                    addToken(TokenType.EQUALS, "=", i, i + 1, line, column); i++; column++
                }

                C_SLASH -> {
                    if (i + 1 < input.length && codes[i + 1] == C_SLASH) {
                        // Skip comment until end of line
                        i += 2
                        while (i < input.length && codes[i] != C_LF) i++
                    } else {
                        addToken(TokenType.SLASH, "/", i, i + 1, line, column)
                        i++; column++
                    }
                }

                else -> {
                    val start = i
                    val tokenLine = line
                    val tokenColumn = column
                    while (i < input.length) {
                        val ci = codes[i]
                        if (ci == C_SPACE || ci == C_LBRACKET || ci == C_RBRACKET || ci == C_LANGLE ||
                            ci == C_RANGLE || ci == C_COMMA || ci == C_STAR || ci == C_TILDE ||
                            ci == C_AT || ci == C_LPAREN || ci == C_RPAREN || ci == C_PIPE ||
                            ci == C_QUESTION || ci == C_BANG || ci == C_LBRACE || ci == C_RBRACE ||
                            ci == C_EQUALS || ci == C_TAB || ci == C_LF || ci == C_CR
                        ) break
                        if (ci == C_SLASH) {
                            val next = if (i + 1 < input.length) codes[i + 1] else -1
                            if (next == -1 || next in C_0..C_9 || next == C_DOT ||
                                next == C_SPACE || next == C_TAB || next == C_LF || next == C_CR
                            ) break
                        }
                        i++; column++
                    }
                    addToken(
                        type = TokenType.LITERAL,
                        text = input.substring(start, i),
                        start = start,
                        end = i,
                        tokenLine = tokenLine,
                        tokenColumn = tokenColumn,
                    )
                }
            }
        }
        return tokens
    }
}
