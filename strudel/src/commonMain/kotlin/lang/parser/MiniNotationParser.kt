package io.peekandpoke.klang.strudel.lang.parser

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.*

class MiniNotationParser(
    input: String,
    private val atomFactory: (String) -> StrudelPattern,
) {
    private val tokens = tokenize(input)
    private var pos = 0

    data class AtomInfo(
        val base: String,
        val sampleIndex: Int? = null,
    )

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
            steps.add(parseStep())
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
            match(TokenType.DASH) -> silence

            match(TokenType.LITERAL) -> {
                val text = previous().text
                parseAtom(text)
            }

            else -> {
                // Determine what went wrong for better error
                if (isAtEnd()) error("Unexpected end of input")
                else error("Unexpected token: ${peek().text} at index $pos")
            }
        }

        // Apply modifiers (*, /, @, !)
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.AT) || check(TokenType.EXCLAIM)) {
            when {
                match(TokenType.STAR) -> {
                    val factorStr = consume(TokenType.LITERAL, "Expected number after '*'").text
                    val factor = factorStr.toDoubleOrNull() ?: 1.0
                    pattern = pattern.fast(factor)
                }

                match(TokenType.SLASH) -> {
                    val factorStr = consume(TokenType.LITERAL, "Expected number after '/'").text
                    val factor = factorStr.toDoubleOrNull() ?: 1.0
                    pattern = pattern.slow(factor)
                }

                match(TokenType.AT) -> {
                    val factorStr = consume(TokenType.LITERAL, "Expected number after '@'").text
                    val factor = factorStr.toDoubleOrNull() ?: 1.0
                    // @n elongates: stretch the pattern to n times its duration
                    pattern = pattern.slow(factor)
                }

                match(TokenType.EXCLAIM) -> {
                    val factorStr = consume(TokenType.LITERAL, "Expected number after '!'").text
                    val times = factorStr.toIntOrNull() ?: 1
                    // !n replicates: repeat the pattern n times within the same duration
                    pattern = pattern.fast(times.toDouble())
                }
            }
        }

        return pattern
    }

    // Parse atom with optional :n sample selection
    private fun parseAtom(text: String): StrudelPattern {
        val atomInfo = parseAtomText(text)
        val pattern = atomFactory(atomInfo.base)

        // If we have a sample index, apply it using .n()
        return if (atomInfo.sampleIndex != null) {
            pattern.n(atomInfo.sampleIndex)
        } else {
            pattern
        }
    }

    // Parse "bd:3" into AtomInfo("bd", 3)
    private fun parseAtomText(text: String): AtomInfo {
        val colonIndex = text.indexOf(':')
        return if (colonIndex > 0) {
            val base = text.substring(0, colonIndex)
            val indexStr = text.substring(colonIndex + 1)
            val index = indexStr.toIntOrNull()
            AtomInfo(base, index)
        } else {
            AtomInfo(text, null)
        }
    }

    // Alternation: < a b c >  (Sequence treated as one per cycle)
    private fun parseAlternation(): StrudelPattern {
        val steps = mutableListOf<StrudelPattern>()

        // Parse items until '>'
        while (!isAtEnd() && !check(TokenType.R_ANGLE)) {
            steps.add(parseStep())
        }

        if (steps.isEmpty()) return silence

        // <a b c> is equivalent to slow(3, seq(a, b, c))
        // This makes each step take 1 full cycle
        val seqPattern = seq(*steps.toTypedArray())
        return seqPattern.slow(steps.size)
    }

    // --- Tokenizer ---

    private enum class TokenType {
        L_BRACKET, R_BRACKET, L_ANGLE, R_ANGLE,
        COMMA, STAR, SLASH, TILDE, DASH, AT, EXCLAIM, LITERAL
    }

    private data class Token(val type: TokenType, val text: String)

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when (c) {
                ' ', '\t', '\n', '\r' -> i++
                '[' -> {
                    tokens.add(Token(TokenType.L_BRACKET, "[")); i++
                }

                ']' -> {
                    tokens.add(Token(TokenType.R_BRACKET, "]")); i++
                }

                '<' -> {
                    tokens.add(Token(TokenType.L_ANGLE, "<")); i++
                }

                '>' -> {
                    tokens.add(Token(TokenType.R_ANGLE, ">")); i++
                }

                ',' -> {
                    tokens.add(Token(TokenType.COMMA, ",")); i++
                }

                '*' -> {
                    tokens.add(Token(TokenType.STAR, "*")); i++
                }

                '/' -> {
                    tokens.add(Token(TokenType.SLASH, "/")); i++
                }

                '~' -> {
                    tokens.add(Token(TokenType.TILDE, "~")); i++
                }

                '-' -> {
                    tokens.add(Token(TokenType.DASH, "-")); i++
                }

                '@' -> {
                    tokens.add(Token(TokenType.AT, "@")); i++
                }

                '!' -> {
                    tokens.add(Token(TokenType.EXCLAIM, "!")); i++
                }

                else -> {
                    val start = i
                    while (i < input.length) {
                        val next = input[i]
                        // Note: ':' is allowed within literals for sample selection (e.g., "bd:3")
                        if (next in " []<>,*/~-@! \t\n\r") break
                        i++
                    }
                    tokens.add(Token(TokenType.LITERAL, input.substring(start, i)))
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
