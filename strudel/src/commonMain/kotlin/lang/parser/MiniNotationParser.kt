package io.peekandpoke.klang.strudel.lang.parser

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.*

class MiniNotationParser(
    input: String,
    private val atomFactory: (String) -> StrudelPattern,
) {
    private val tokens = tokenize(input)
    private var pos = 0

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
            match(TokenType.LITERAL) -> {
                val text = previous().text
                atomFactory(text)
            }

            else -> {
                // Determine what went wrong for better error
                if (isAtEnd()) error("Unexpected end of input")
                else error("Unexpected token: ${peek().text} at index $pos")
            }
        }

        // Apply modifiers (*, /)
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            if (match(TokenType.STAR)) {
                val factorStr = consume(TokenType.LITERAL, "Expected number after '*'").text
                val factor = factorStr.toDoubleOrNull() ?: 1.0
                pattern = pattern.fast(factor)
            } else if (match(TokenType.SLASH)) {
                val factorStr = consume(TokenType.LITERAL, "Expected number after '/'").text
                val factor = factorStr.toDoubleOrNull() ?: 1.0
                pattern = pattern.slow(factor)
            }
        }

        return pattern
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
        COMMA, STAR, SLASH, TILDE, LITERAL
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

                else -> {
                    val start = i
                    while (i < input.length) {
                        val next = input[i]
                        if (next in " []<>,*/~ \t\n\r") break
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
