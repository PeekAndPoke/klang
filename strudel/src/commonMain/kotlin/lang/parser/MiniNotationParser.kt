package io.peekandpoke.klang.strudel.lang.parser

import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.pattern.ChoicePattern.Companion.choice
import io.peekandpoke.klang.strudel.pattern.EuclideanPattern
import io.peekandpoke.klang.strudel.pattern.PropertyOverridePattern
import io.peekandpoke.klang.strudel.pattern.SequencePattern

fun <T> parseMiniNotation(
    input: StrudelDslArg<T>,
    atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
): StrudelPattern = when (val v = input.value) {
    is StrudelPattern -> v
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
    atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
): StrudelPattern = MiniNotationParser(
    input = input,
    baseLocation = baseLocation,
    atomFactory = atomFactory,
).parse()

class MiniNotationParser(
    private val input: String,
    private val baseLocation: SourceLocation? = null,
    private val atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
) {
    private val tokens = tokenize(input)
    private var pos = 0

    // Internal pattern to mark sequences that should be flattened into the parent
    private class SplittableSequencePattern(val patterns: List<StrudelPattern>) : StrudelPattern {
        // This pattern acts like a SequencePattern if used directly (fallback)
        val inner = SequencePattern.create(patterns)

        override val weight: Double get() = inner.weight

        override val numSteps: Rational? get() = inner.numSteps

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> =
            inner.queryArcContextual(from, to, ctx)
    }

    fun parse(): StrudelPattern {
        if (tokens.isEmpty()) return silence

        val result = parseExpression()

        // We could check if (pos < tokens.size) here to warn about unconsumed input

        return result
    }

    // Expression: pattern1, pattern2, ... (Stack)
    private fun parseExpression(): StrudelPattern {
        val patterns = mutableListOf<StrudelPattern>()

        do {
            patterns.add(parseSequence())
        } while (match(TokenType.COMMA))

        return if (patterns.size == 1) patterns[0] else stack(*patterns.toTypedArray())
    }

    // Sequence: pattern1 pattern2 ... (Sequence)
    private fun parseSequence(): StrudelPattern {
        val steps = mutableListOf<StrudelPattern>()

        while (!isAtEnd() && !check(TokenType.COMMA) && !check(TokenType.R_BRACKET) && !check(TokenType.R_ANGLE)) {
            val step = parseStep()
            if (step is SplittableSequencePattern) {
                steps.addAll(step.patterns)
            } else {
                steps.add(step)
            }
        }

        return if (steps.isEmpty()) silence
        else if (steps.size == 1) steps[0]
        else seq(*steps.toTypedArray())
    }

    // Step: Atom, Group [], Alternation <>, Silence ~, with modifiers
    private fun parseStep(): StrudelPattern {
        var pattern = when {
            match(TokenType.L_BRACKET) -> {
                val p = parseExpression()
                consume(TokenType.R_BRACKET, "Expected ']'")
                p
            }

            match(TokenType.L_ANGLE) -> {
                val p = parseAlternation()
                consume(TokenType.R_ANGLE, "Expected '>'")
                p
            }

            match(TokenType.TILDE) -> silence

            match(TokenType.LITERAL) -> {
                val token = previous()
                val text = token.text

                // Build source location chain for this atom
                val atomLocation = token.toLocation(baseLocation)
                val locationChain = if (atomLocation != null) {
                    SourceLocationChain.single(atomLocation)
                } else null

                // Always treat as a single atom, no special parsing here
                atomFactory(text, locationChain)
            }

            else -> {
                // Determine what went wrong for better error
                if (isAtEnd()) error("Unexpected end of input")
                else error("Unexpected token: ${peek().text} at index $pos")
            }
        }

        // Apply modifiers (?, |, !, *, /, @, (p,s))
        while (!isAtEnd() && (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.AT) ||
                    check(TokenType.L_PAREN) || check(TokenType.QUESTION) || check(TokenType.PIPE) ||
                    check(TokenType.BANG))
        ) {
            if (match(TokenType.STAR)) {
                val factorStr = consume(TokenType.LITERAL, "Expected number after '*'").text
                val factor = factorStr.toDoubleOrNull() ?: 1.0
                pattern = pattern.fast(factor)
            } else if (match(TokenType.SLASH)) {
                val factorStr = consume(TokenType.LITERAL, "Expected number after '/'").text
                val factor = factorStr.toDoubleOrNull() ?: 1.0
                pattern = pattern.slow(factor)
            } else if (match(TokenType.BANG)) {
                val countStr = if (check(TokenType.LITERAL)) consume(TokenType.LITERAL, "").text else "2"
                val count = countStr.toIntOrNull() ?: 2
                val steps = List(count) { pattern }
                // Wrap in SplittableSequencePattern to allow flattening
                pattern = SplittableSequencePattern(steps)
            } else if (match(TokenType.QUESTION)) {
                val probStr = if (check(TokenType.LITERAL) && peek().text.firstOrNull()?.isDigit() == true) {
                    consume(TokenType.LITERAL, "").text
                } else null
                val probability = probStr?.toDoubleOrNull() ?: 0.5
                pattern = pattern.degradeBy(probability)
            } else if (match(TokenType.PIPE)) {
                val right = parseStep()
                pattern = pattern.choice(right)
            } else if (match(TokenType.AT)) {
                val weightStr = consume(TokenType.LITERAL, "Expected number after '@'").text
                val weight = weightStr.toDoubleOrNull() ?: 1.0
                pattern = PropertyOverridePattern(pattern, weightOverride = weight)
            } else if (match(TokenType.L_PAREN)) {
                // Euclidean rhythm: (pulses, steps) or (pulses, steps, rotation)
                val pulsesStr = consume(TokenType.LITERAL, "Expected pulses number").text
                val pulses = pulsesStr.toIntOrNull() ?: error("Invalid pulses number: $pulsesStr")

                consume(TokenType.COMMA, "Expected ',' in Euclidean rhythm")

                val stepsStr = consume(TokenType.LITERAL, "Expected steps number").text
                val steps = stepsStr.toIntOrNull() ?: error("Invalid steps number: $stepsStr")

                var rotation = 0
                if (match(TokenType.COMMA)) {
                    val rotationStr = consume(TokenType.LITERAL, "Expected rotation number").text
                    rotation = rotationStr.toIntOrNull() ?: error("Invalid rotation number: $rotationStr")
                }

                consume(TokenType.R_PAREN, "Expected ')' after Euclidean rhythm")

                pattern = EuclideanPattern
                    .create(inner = pattern, pulses = pulses, steps = steps, rotation = rotation)
            }
        }

        return pattern
    }

    // Alternation: < a b c >  (Sequence treated as one per cycle)
    private fun parseAlternation(): StrudelPattern {
        val steps = mutableListOf<StrudelPattern>()

        // Parse items until '>'
        while (!isAtEnd() && !check(TokenType.R_ANGLE)) {
            val step = parseStep()
            // Should we flatten ! inside alternation?
            // <a!2 c> -> <a a c> -> Cycle 0: a, Cycle 1: a, Cycle 2: c.
            // Yes, this is consistent.
            if (step is SplittableSequencePattern) {
                steps.addAll(step.patterns)
            } else {
                steps.add(step)
            }
        }

        if (steps.isEmpty()) return silence

        // <a b c> is equivalent to slow(3, seq(a, b, c))
        // This makes each step take 1 full cycle
        val seqPattern = seq(*steps.toTypedArray())
        return seqPattern.slow(steps.size)
    }

    // --- Tokenizer ---

    private enum class TokenType {
        L_BRACKET, R_BRACKET, L_ANGLE, R_ANGLE, L_PAREN, R_PAREN,
        COMMA, STAR, SLASH, TILDE, AT, PIPE, QUESTION, BANG, LITERAL
    }

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
    ) {
        /**
         * Create a SourceLocation for this token
         */
        fun toLocation(base: SourceLocation?): SourceLocation? {
            if (base == null) return null

            // Calculate absolute line and column
            // base.startColumn points to the first character INSIDE the string (after the opening quote)
            // base.startLine points to the line with the opening quote
            // token.line and token.column are both 1-based within the string content

            val absoluteStartLine = base.startLine + line - 1 // -1 because line 1 in content is on base.startLine
            val absoluteStartColumn = if (line == 1) {
                // Same line as the opening quote - add to base column (both 1-based)
                base.startColumn + column
            } else {
                // Different line - just use the token's column (already 1-based)
                column
            }

            // Calculate end position based on token length
            val absoluteEndLine = absoluteStartLine
            val absoluteEndColumn = absoluteStartColumn + text.length

            return SourceLocation(
                source = base.source,
                startLine = absoluteStartLine,
                startColumn = absoluteStartColumn,
                endLine = absoluteEndLine,
                endColumn = absoluteEndColumn
            )
        }
    }

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var line = 1 // 1-based
        var column = 1 // 1-based

        fun addToken(type: TokenType, text: String, start: Int, end: Int, tokenLine: Int, tokenColumn: Int) {
            tokens.add(
                Token(type = type, text = text, start = start, end = end, line = tokenLine, column = tokenColumn)
            )
        }

        while (i < input.length) {
            val c = input[i]
            when (c) {
                '\n' -> {
                    i++
                    line++
                    column = 1 // Reset to 1 at start of new line
                }

                ' ', '\t', '\r' -> {
                    i++
                    column++
                }
                '(' -> {
                    addToken(
                        type = TokenType.L_PAREN,
                        text = "(",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                ')' -> {
                    addToken(
                        type = TokenType.R_PAREN,
                        text = ")",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '[' -> {
                    addToken(
                        type = TokenType.L_BRACKET,
                        text = "[",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                ']' -> {
                    addToken(
                        type = TokenType.R_BRACKET,
                        text = "]",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '<' -> {
                    addToken(
                        type = TokenType.L_ANGLE,
                        text = "<",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '>' -> {
                    addToken(
                        type = TokenType.R_ANGLE,
                        text = ">",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                ',' -> {
                    addToken(
                        type = TokenType.COMMA,
                        text = ",",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '*' -> {
                    addToken(
                        type = TokenType.STAR,
                        text = "*",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '/' -> {
                    addToken(
                        type = TokenType.SLASH,
                        text = "/",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '~' -> {
                    addToken(
                        type = TokenType.TILDE,
                        text = "~",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '@' -> {
                    addToken(
                        type = TokenType.AT,
                        text = "@",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '|' -> {
                    addToken(
                        type = TokenType.PIPE,
                        text = "|",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '?' -> {
                    addToken(
                        type = TokenType.QUESTION,
                        text = "?",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                '!' -> {
                    addToken(
                        type = TokenType.BANG,
                        text = "!",
                        start = i,
                        end = i + 1,
                        tokenLine = line,
                        tokenColumn = column
                    )
                    i++
                    column++
                }

                else -> {
                    val start = i
                    val tokenLine = line
                    val tokenColumn = column
                    while (i < input.length) {
                        if (input[i] in " []<>,*~@()|?! \t\n\r") break

                        if (input[i] == '/') {
                            // Check if this slash is likely a division operator (followed by number or space)
                            val next = if (i + 1 < input.length) input[i + 1] else null
                            // If followed by digit, dot (float), or whitespace -> treat as operator
                            if (next == null || next.isDigit() || next == '.' || next.isWhitespace()) {
                                break
                            }
                            // Otherwise treat as part of the literal (e.g. chord "F/A")
                        }

                        i++
                        column++
                    }
                    addToken(
                        type = TokenType.LITERAL,
                        text = input.substring(start, i),
                        start = start,
                        end = i,
                        tokenLine = tokenLine,
                        tokenColumn = tokenColumn
                    )
                }
            }
        }
        return tokens
    }

    // --- Helpers ---

    private fun peek(): Token = tokens[pos]
    private fun previous(): Token = tokens[pos - 1]
    private fun isAtEnd(): Boolean = pos >= tokens.size

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun match(type: TokenType): Boolean {
        if (check(type)) {
            pos++
            return true
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) {
            return tokens[pos++]
        }
        error(message)
    }
}
