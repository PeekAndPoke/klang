package io.peekandpoke.klang.script.parser

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.ast.*
import io.peekandpoke.klang.script.runtime.KlangScriptSyntaxError

/**
 * Hand-rolled recursive descent parser for KlangScript
 *
 * This parser transforms source code text into an Abstract Syntax Tree (AST).
 * It replaces the previous better-parse combinator implementation to fix
 * Kotlin/JS production build issues.
 *
 * Capabilities:
 * - Numeric, string, boolean, and null literals
 * - Identifiers (variables and function names)
 * - Function calls with arguments (trailing commas allowed)
 * - Member access (dot notation): obj.property
 * - Method chaining: note("c").gain(0.5).pan("0 1")
 * - Arithmetic operators: +, -, *, /, %
 * - Comparison operators: ==, !=, <, <=, >, >=
 * - Logical operators: &&, ||
 * - Unary operators: -, +, !
 * - Arrow functions: x => x + 1, (a, b) => a + b, () => { ... }
 * - Object literals: { key: value }
 * - Array literals: [1, 2, 3]
 * - Variable declarations: let, const
 * - Import/export statements
 * - Return statements
 * - Block bodies for arrow functions
 * - Comments: // and /* */
 * - Source location tracking
 */
class KlangScriptParser private constructor(
    private val currentSource: String? = null,
) {
    companion object {
        /** Keyword → TokenType lookup map. */
        private val keywords = mapOf(
            "true" to TokenType.TRUE,
            "false" to TokenType.FALSE,
            "null" to TokenType.NULL,
            "let" to TokenType.LET,
            "const" to TokenType.CONST,
            "import" to TokenType.IMPORT,
            "export" to TokenType.EXPORT,
            "from" to TokenType.FROM,
            "as" to TokenType.AS,
            "return" to TokenType.RETURN,
            "in" to TokenType.IN,
            "if" to TokenType.IF,
            "else" to TokenType.ELSE,
            "while" to TokenType.WHILE,
            "do" to TokenType.DO,
            "for" to TokenType.FOR,
            "break" to TokenType.BREAK,
            "continue" to TokenType.CONTINUE,
        )

        /**
         * Parse KlangScript source code into an AST
         *
         * @param source The source code to parse
         * @param sourceName Optional source file name for error reporting
         * @return Program AST node
         * @throws KlangScriptSyntaxError if the source contains syntax errors
         */
        fun parse(source: String, sourceName: String? = null): Program {
            val parser = KlangScriptParser(currentSource = sourceName)
            return parser.parseInternal(source)
        }
    }

    // ============================================================
    // Token Types and Lexer
    // ============================================================

    /** Token types produced by the lexer. */
    private enum class TokenType {
        // Literals
        NUMBER, STRING, BACKTICK_STRING, IDENTIFIER,

        // Keywords (must match before IDENTIFIER)
        TRUE, FALSE, NULL, LET, CONST, IMPORT, EXPORT, FROM, AS, RETURN, IN,
        IF, ELSE, WHILE, DO, FOR, BREAK, CONTINUE,

        // Punctuation
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
        LEFT_BRACKET, RIGHT_BRACKET, COMMA, COLON, DOT, QUESTION, SEMICOLON,

        // Multi-char operators (MUST tokenize before single-char — longer before shorter)
        ARROW,               // =>
        TRIPLE_EQUALS,       // ===  (before ==)
        DOUBLE_EQUALS,       // ==   (before =)
        NOT_DOUBLE_EQUALS,   // !==  (before !=)
        NOT_EQUALS,          // !=   (before !)
        SHIFT_LEFT_EQUALS,   // <<=  (before << and <=)
        SHIFT_LEFT,          // <<   (before <= and <)
        LESS_EQUAL,          // <=   (before <)
        UNSIGNED_SHIFT_RIGHT, // >>>  (before >>= and >>)
        SHIFT_RIGHT_EQUALS,  // >>=  (before >> and >=)
        SHIFT_RIGHT,         // >>   (before >= and >)
        GREATER_EQUAL,       // >=   (before >)
        DOUBLE_AMP,          // &&   (before &= and &)
        AMP_EQUALS,          // &=   (before &)
        DOUBLE_PIPE,         // ||   (before |= and |)
        PIPE_EQUALS,         // |=   (before |)
        DOUBLE_QUESTION,     // ??   (before ?. and ?)
        QUESTION_DOT,        // ?.   (before ?)
        CARET_EQUALS,        // ^=   (before ^)
        DOUBLE_STAR_EQUALS,  // **=  (before ** and *=)
        DOUBLE_STAR,         // **   (before *=)
        PLUS_PLUS,           // ++   (before + and +=)
        MINUS_MINUS,         // --   (before - and -=)
        PLUS_EQUALS,         // +=   (before +)
        MINUS_EQUALS,        // -=   (before -)
        STAR_EQUALS,         // *=   (before *)
        SLASH_EQUALS,        // /=   (before /)
        PERCENT_EQUALS,      // %=   (before %)

        // Single-char operators
        EQUALS, PLUS, MINUS, STAR, SLASH, PERCENT,
        EXCLAMATION, LESS_THAN, GREATER_THAN,
        AMP, PIPE, CARET, TILDE,

        EOF
    }

    /** A lexed token with type, text content, and source position. */
    private data class Token(
        val type: TokenType,
        val text: String,
        val line: Int,      // 1-based (start line)
        val column: Int,    // 1-based (start column)
        val endLine: Int,   // 1-based
        val endColumn: Int,  // 1-based, exclusive
    )

    private lateinit var tokens: List<Token>
    private var pos: Int = 0

    private fun parseInternal(source: String): Program {
        this.tokens = tokenize(source)
        this.pos = 0
        return parseProgram()
    }

    /**
     * Tokenize source code into a list of tokens
     */
    private fun tokenize(source: String): List<Token> {
        val tokens = mutableListOf<Token>()

        // Convert source string to IntArray of char codes once. On Kotlin/JS, String[i] returns
        // a boxed Char (heap-allocated wrapper). IntArray[i] returns a plain JS number — no boxing.
        // This is critical for a lexer that runs on every keystroke in a live-coding environment.
        val codes = IntArray(source.length) { source[it].code }

        // Unboxed char classification helpers — Char.isDigit() etc. box on Kotlin/JS
        fun Int.isAsciiDigit(): Boolean = this in 48..57           // '0'.code..'9'.code
        fun Int.isAsciiLetter(): Boolean = this in 65..90 || this in 97..122  // A-Z, a-z
        fun Int.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || isAsciiDigit()

        var i = 0
        var line = 1
        var column = 1

        fun addToken(type: TokenType, text: String, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) {
            tokens.add(
                Token(
                    type = type,
                    text = text,
                    line = startLine,
                    column = startColumn,
                    endLine = endLine,
                    endColumn = endColumn
                )
            )
        }

        // Overload for non-multiline tokens (single line)
        fun addToken(type: TokenType, text: String, startColumn: Int) {
            addToken(type, text, line, startColumn, line, column)
        }

        while (i < source.length) {
            val ch = codes[i]
            val startColumn = column

            when {
                // Whitespace
                ch == ' '.code || ch == '\t'.code || ch == '\r'.code -> {
                    i++
                    column++
                }

                // Newline
                ch == '\n'.code -> {
                    i++
                    line++
                    column = 1
                }

                // Single-line comment
                ch == '/'.code && i + 1 < source.length && codes[i + 1] == '/'.code -> {
                    i += 2
                    column += 2
                    while (i < source.length && codes[i] != '\n'.code) {
                        i++
                        column++
                    }
                }

                // Multi-line comment
                ch == '/'.code && i + 1 < source.length && codes[i + 1] == '*'.code -> {
                    i += 2
                    column += 2
                    while (i < source.length - 1) {
                        if (codes[i] == '*'.code && codes[i + 1] == '/'.code) {
                            i += 2
                            column += 2
                            break
                        }
                        if (codes[i] == '\n'.code) {
                            line++
                            column = 1
                        } else {
                            column++
                        }
                        i++
                    }
                }

                // Multi-char operators (MUST check before single-char — longer before shorter)

                // === (before == and =)
                ch == '='.code && i + 2 < source.length && codes[i + 1] == '='.code && codes[i + 2] == '='.code -> {
                    addToken(TokenType.TRIPLE_EQUALS, "===", startColumn)
                    i += 3
                    column += 3
                }

                // => (before ==)
                ch == '='.code && i + 1 < source.length && codes[i + 1] == '>'.code -> {
                    addToken(TokenType.ARROW, "=>", startColumn)
                    i += 2
                    column += 2
                }

                // == (before =)
                ch == '='.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.DOUBLE_EQUALS, "==", startColumn)
                    i += 2
                    column += 2
                }

                // !== (before != and !)
                ch == '!'.code && i + 2 < source.length && codes[i + 1] == '='.code && codes[i + 2] == '='.code -> {
                    addToken(TokenType.NOT_DOUBLE_EQUALS, "!==", startColumn)
                    i += 3
                    column += 3
                }

                // != (before !)
                ch == '!'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.NOT_EQUALS, "!=", startColumn)
                    i += 2
                    column += 2
                }

                // <<= (before << and <=)
                ch == '<'.code && i + 2 < source.length && codes[i + 1] == '<'.code && codes[i + 2] == '='.code -> {
                    addToken(TokenType.SHIFT_LEFT_EQUALS, "<<=", startColumn)
                    i += 3
                    column += 3
                }

                // << (before <=)
                ch == '<'.code && i + 1 < source.length && codes[i + 1] == '<'.code -> {
                    addToken(TokenType.SHIFT_LEFT, "<<", startColumn)
                    i += 2
                    column += 2
                }

                ch == '<'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.LESS_EQUAL, "<=", startColumn)
                    i += 2
                    column += 2
                }

                // >>> (before >>= and >>)
                ch == '>'.code && i + 2 < source.length && codes[i + 1] == '>'.code && codes[i + 2] == '>'.code -> {
                    addToken(TokenType.UNSIGNED_SHIFT_RIGHT, ">>>", startColumn)
                    i += 3
                    column += 3
                }

                // >>= (before >> and >=)
                ch == '>'.code && i + 2 < source.length && codes[i + 1] == '>'.code && codes[i + 2] == '='.code -> {
                    addToken(TokenType.SHIFT_RIGHT_EQUALS, ">>=", startColumn)
                    i += 3
                    column += 3
                }

                // >> (before >=)
                ch == '>'.code && i + 1 < source.length && codes[i + 1] == '>'.code -> {
                    addToken(TokenType.SHIFT_RIGHT, ">>", startColumn)
                    i += 2
                    column += 2
                }

                ch == '>'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.GREATER_EQUAL, ">=", startColumn)
                    i += 2
                    column += 2
                }

                ch == '&'.code && i + 1 < source.length && codes[i + 1] == '&'.code -> {
                    addToken(TokenType.DOUBLE_AMP, "&&", startColumn)
                    i += 2
                    column += 2
                }

                // &= (before &)
                ch == '&'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.AMP_EQUALS, "&=", startColumn)
                    i += 2
                    column += 2
                }

                ch == '|'.code && i + 1 < source.length && codes[i + 1] == '|'.code -> {
                    addToken(TokenType.DOUBLE_PIPE, "||", startColumn)
                    i += 2
                    column += 2
                }

                // |= (before |)
                ch == '|'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.PIPE_EQUALS, "|=", startColumn)
                    i += 2
                    column += 2
                }

                // ++ (before + and +=)
                ch == '+'.code && i + 1 < source.length && codes[i + 1] == '+'.code -> {
                    addToken(TokenType.PLUS_PLUS, "++", startColumn)
                    i += 2
                    column += 2
                }

                // += (before +)
                ch == '+'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.PLUS_EQUALS, "+=", startColumn)
                    i += 2
                    column += 2
                }

                // -- (before - and -=)
                ch == '-'.code && i + 1 < source.length && codes[i + 1] == '-'.code -> {
                    addToken(TokenType.MINUS_MINUS, "--", startColumn)
                    i += 2
                    column += 2
                }

                // -= (before -)
                ch == '-'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.MINUS_EQUALS, "-=", startColumn)
                    i += 2
                    column += 2
                }

                // **= (before ** and *=)
                ch == '*'.code && i + 2 < source.length && codes[i + 1] == '*'.code && codes[i + 2] == '='.code -> {
                    addToken(TokenType.DOUBLE_STAR_EQUALS, "**=", startColumn)
                    i += 3
                    column += 3
                }

                // ** (before *=)
                ch == '*'.code && i + 1 < source.length && codes[i + 1] == '*'.code -> {
                    addToken(TokenType.DOUBLE_STAR, "**", startColumn)
                    i += 2
                    column += 2
                }

                // *= (before *)
                ch == '*'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.STAR_EQUALS, "*=", startColumn)
                    i += 2
                    column += 2
                }

                // /= (before /)
                ch == '/'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.SLASH_EQUALS, "/=", startColumn)
                    i += 2
                    column += 2
                }

                // %= (before %)
                ch == '%'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.PERCENT_EQUALS, "%=", startColumn)
                    i += 2
                    column += 2
                }

                // Single-char tokens
                ch == '('.code -> {
                    addToken(TokenType.LEFT_PAREN, "(", startColumn)
                    i++
                    column++
                }

                ch == ')'.code -> {
                    addToken(TokenType.RIGHT_PAREN, ")", startColumn)
                    i++
                    column++
                }

                ch == '{'.code -> {
                    addToken(TokenType.LEFT_BRACE, "{", startColumn)
                    i++
                    column++
                }

                ch == '}'.code -> {
                    addToken(TokenType.RIGHT_BRACE, "}", startColumn)
                    i++
                    column++
                }

                ch == '['.code -> {
                    addToken(TokenType.LEFT_BRACKET, "[", startColumn)
                    i++
                    column++
                }

                ch == ']'.code -> {
                    addToken(TokenType.RIGHT_BRACKET, "]", startColumn)
                    i++
                    column++
                }

                ch == ','.code -> {
                    addToken(TokenType.COMMA, ",", startColumn)
                    i++
                    column++
                }

                ch == ':'.code -> {
                    addToken(TokenType.COLON, ":", startColumn)
                    i++
                    column++
                }

                ch == '.'.code -> {
                    addToken(TokenType.DOT, ".", startColumn)
                    i++
                    column++
                }

                // ?? (before ?. and ?)
                ch == '?'.code && i + 1 < source.length && codes[i + 1] == '?'.code -> {
                    addToken(TokenType.DOUBLE_QUESTION, "??", startColumn)
                    i += 2
                    column += 2
                }

                // ?. (before ?)
                ch == '?'.code && i + 1 < source.length && codes[i + 1] == '.'.code -> {
                    addToken(TokenType.QUESTION_DOT, "?.", startColumn)
                    i += 2
                    column += 2
                }

                ch == '?'.code -> {
                    addToken(TokenType.QUESTION, "?", startColumn)
                    i++
                    column++
                }

                ch == ';'.code -> {
                    addToken(TokenType.SEMICOLON, ";", startColumn)
                    i++
                    column++
                }

                ch == '='.code -> {
                    addToken(TokenType.EQUALS, "=", startColumn)
                    i++
                    column++
                }

                ch == '+'.code -> {
                    addToken(TokenType.PLUS, "+", startColumn)
                    i++
                    column++
                }

                ch == '-'.code -> {
                    addToken(TokenType.MINUS, "-", startColumn)
                    i++
                    column++
                }

                ch == '*'.code -> {
                    addToken(TokenType.STAR, "*", startColumn)
                    i++
                    column++
                }

                ch == '/'.code -> {
                    addToken(TokenType.SLASH, "/", startColumn)
                    i++
                    column++
                }

                ch == '%'.code -> {
                    addToken(TokenType.PERCENT, "%", startColumn)
                    i++
                    column++
                }

                ch == '!'.code -> {
                    addToken(TokenType.EXCLAMATION, "!", startColumn)
                    i++
                    column++
                }

                ch == '<'.code -> {
                    addToken(TokenType.LESS_THAN, "<", startColumn)
                    i++
                    column++
                }

                ch == '>'.code -> {
                    addToken(TokenType.GREATER_THAN, ">", startColumn)
                    i++
                    column++
                }

                // ^= (before ^)
                ch == '^'.code && i + 1 < source.length && codes[i + 1] == '='.code -> {
                    addToken(TokenType.CARET_EQUALS, "^=", startColumn)
                    i += 2
                    column += 2
                }

                ch == '&'.code -> {
                    addToken(TokenType.AMP, "&", startColumn)
                    i++
                    column++
                }

                ch == '|'.code -> {
                    addToken(TokenType.PIPE, "|", startColumn)
                    i++
                    column++
                }

                ch == '^'.code -> {
                    addToken(TokenType.CARET, "^", startColumn)
                    i++
                    column++
                }

                ch == '~'.code -> {
                    addToken(TokenType.TILDE, "~", startColumn)
                    i++
                    column++
                }

                // String literals
                ch == '"'.code || ch == '\''.code -> {
                    val startLine = line  // Save starting line for multiline strings
                    val quote = ch
                    val sb = StringBuilder()
                    i++ // Skip opening quote
                    column++

                    while (i < source.length && codes[i] != quote) {
                        if (codes[i] == '\\'.code && i + 1 < source.length) {
                            // Escape sequence
                            i++
                            column++
                            when (codes[i]) {
                                'n'.code -> sb.append('\n')
                                't'.code -> sb.append('\t')
                                'r'.code -> sb.append('\r')
                                '\\'.code -> sb.append('\\')
                                '"'.code -> sb.append('"')
                                '\''.code -> sb.append('\'')
                                else -> sb.append(codes[i].toChar())
                            }
                        } else {
                            sb.append(codes[i].toChar())
                        }
                        if (codes[i] == '\n'.code) {
                            line++
                            column = 1
                        } else {
                            column++
                        }
                        i++
                    }

                    if (i >= source.length) {
                        throw KlangScriptSyntaxError(
                            "Unterminated string",
                            location = SourceLocation(currentSource, startLine, startColumn, line, column),
                        )
                    }

                    i++ // Skip closing quote
                    column++
                    addToken(TokenType.STRING, sb.toString(), startLine, startColumn, line, column)
                }

                // Backtick strings
                ch == '`'.code -> {
                    val startLine = line  // Save starting line for multiline strings
                    val sb = StringBuilder()
                    i++ // Skip opening backtick
                    column++

                    while (i < source.length && codes[i] != '`'.code) {
                        if (codes[i] == '\\'.code && i + 1 < source.length) {
                            // Escape sequence
                            i++
                            column++
                            when (codes[i]) {
                                'n'.code -> sb.append('\n')
                                't'.code -> sb.append('\t')
                                'r'.code -> sb.append('\r')
                                '\\'.code -> sb.append('\\')
                                '`'.code -> sb.append('`')
                                else -> sb.append(codes[i].toChar())
                            }
                        } else {
                            sb.append(codes[i].toChar())
                        }
                        if (codes[i] == '\n'.code) {
                            line++
                            column = 1
                        } else {
                            column++
                        }
                        i++
                    }

                    if (i >= source.length) {
                        throw KlangScriptSyntaxError(
                            "Unterminated backtick string",
                            location = SourceLocation(currentSource, startLine, startColumn, line, column),
                        )
                    }

                    i++ // Skip closing backtick
                    column++
                    addToken(TokenType.BACKTICK_STRING, sb.toString(), startLine, startColumn, line, column)
                }

                // Numbers (including hex 0x, octal 0o, binary 0b, scientific notation: 1e6, 2.5e-3, 1E+10)
                ch.isAsciiDigit() -> {
                    val start = i

                    // Check for hex, octal, or binary prefix
                    if (ch == '0'.code && i + 1 < source.length) {
                        val prefix = codes[i + 1]
                        when {
                            // Hex: 0x or 0X
                            prefix == 'x'.code || prefix == 'X'.code -> {
                                i += 2
                                column += 2
                                val hexStart = i
                                while (i < source.length &&
                                    (codes[i].isAsciiDigit() || codes[i] in 'a'.code..'f'.code || codes[i] in 'A'.code..'F'.code)
                                ) {
                                    i++
                                    column++
                                }
                                val hexStr = source.substring(hexStart, i)
                                val numValue = hexStr.toLong(16).toDouble()
                                addToken(TokenType.NUMBER, numValue.toString(), startColumn)
                            }
                            // Octal: 0o or 0O
                            prefix == 'o'.code || prefix == 'O'.code -> {
                                i += 2
                                column += 2
                                val octStart = i
                                while (i < source.length && codes[i] in '0'.code..'7'.code) {
                                    i++
                                    column++
                                }
                                val octStr = source.substring(octStart, i)
                                val numValue = octStr.toLong(8).toDouble()
                                addToken(TokenType.NUMBER, numValue.toString(), startColumn)
                            }
                            // Binary: 0b or 0B
                            prefix == 'b'.code || prefix == 'B'.code -> {
                                i += 2
                                column += 2
                                val binStart = i
                                while (i < source.length && (codes[i] == '0'.code || codes[i] == '1'.code)) {
                                    i++
                                    column++
                                }
                                val binStr = source.substring(binStart, i)
                                val numValue = binStr.toLong(2).toDouble()
                                addToken(TokenType.NUMBER, numValue.toString(), startColumn)
                            }
                            // Regular number starting with 0
                            else -> {
                                while (i < source.length && (codes[i].isAsciiDigit() || codes[i] == '.'.code)) {
                                    i++
                                    column++
                                }
                                // Scientific notation
                                if (i < source.length && (codes[i] == 'e'.code || codes[i] == 'E'.code)) {
                                    i++
                                    column++
                                    if (i < source.length && (codes[i] == '+'.code || codes[i] == '-'.code)) {
                                        i++
                                        column++
                                    }
                                    while (i < source.length && codes[i].isAsciiDigit()) {
                                        i++
                                        column++
                                    }
                                }
                                addToken(TokenType.NUMBER, source.substring(start, i), startColumn)
                            }
                        }
                    } else {
                        while (i < source.length && (codes[i].isAsciiDigit() || codes[i] == '.'.code)) {
                            i++
                            column++
                        }
                        // Scientific notation: optional e/E followed by optional +/- and digits
                        if (i < source.length && (codes[i] == 'e'.code || codes[i] == 'E'.code)) {
                            i++
                            column++
                            if (i < source.length && (codes[i] == '+'.code || codes[i] == '-'.code)) {
                                i++
                                column++
                            }
                            while (i < source.length && codes[i].isAsciiDigit()) {
                                i++
                                column++
                            }
                        }
                        addToken(TokenType.NUMBER, source.substring(start, i), startColumn)
                    }
                }

                // Identifiers and keywords
                ch.isAsciiLetter() || ch == '_'.code -> {
                    val start = i
                    while (i < source.length && (codes[i].isAsciiLetterOrDigit() || codes[i] == '_'.code)) {
                        i++
                        column++
                    }
                    val text = source.substring(start, i)
                    val type = keywords[text] ?: TokenType.IDENTIFIER
                    addToken(type, text, startColumn)
                }

                else -> {
                    throw KlangScriptSyntaxError(
                        "Unexpected character: ${ch.toChar()}",
                        location = SourceLocation(currentSource, line, column, line, column + 1),
                    )
                }
            }
        }

        // Add EOF token
        tokens.add(Token(TokenType.EOF, "", line, column, line, column))
        return tokens
    }

    // ============================================================
    // Parser Helper Methods
    // ============================================================

    private fun peek(): Token = tokens[pos]

    private fun previous(): Token = tokens[pos - 1]

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun advance(): Token = tokens[pos++]

    private fun check(type: TokenType): Boolean = !isAtEnd() && peek().type == type

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        error(message)
    }

    private fun error(message: String): Nothing {
        val token = if (isAtEnd()) previous() else peek()
        throw KlangScriptSyntaxError(
            message,
            location = SourceLocation(currentSource, token.line, token.column, token.endLine, token.endColumn),
        )
    }

    /** Skip any semicolons (used as optional statement terminators outside for-loop headers) */
    private fun skipSemicolons() {
        while (check(TokenType.SEMICOLON)) {
            advance()
        }
    }

    private fun Token.toSourceLocation(): SourceLocation {
        return SourceLocation(
            source = currentSource,
            startLine = line,
            startColumn = column,
            endLine = endLine,
            endColumn = endColumn
        )
    }

    // ============================================================
    // Expression Parsing
    // ============================================================

    private fun parseExpression(): Expression {
        return parseArrowFunction()
    }

    /**
     * Arrow functions (lowest precedence, right-associative)
     * Syntax: x => expr, (a, b) => expr, () => { ... }
     */
    private fun parseArrowFunction(): Expression {
        // Save position for potential backtracking
        val checkpoint = pos

        // Try to parse arrow parameters
        val maybeParams = tryParseArrowParams()

        if (maybeParams != null && match(TokenType.ARROW)) {
            // Confirmed arrow function
            val arrowToken = previous()
            val body = parseArrowBody()
            return ArrowFunction(maybeParams, body, arrowToken.toSourceLocation())
        }

        // Not an arrow function - backtrack
        pos = checkpoint
        return parseAssignment()
    }

    /**
     * Assignment expressions (=, +=, -=, *=, /=, %=)
     * Right-associative, lower precedence than ternary.
     * Compound assignments are desugared to AssignmentExpression(target, BinaryOperation(target, op, value))
     */
    private fun parseAssignment(): Expression {
        val left = parseTernary()

        // Check for assignment operators
        val assignOp = when {
            check(TokenType.EQUALS) -> null  // plain =
            check(TokenType.PLUS_EQUALS) -> BinaryOperator.ADD
            check(TokenType.MINUS_EQUALS) -> BinaryOperator.SUBTRACT
            check(TokenType.STAR_EQUALS) -> BinaryOperator.MULTIPLY
            check(TokenType.SLASH_EQUALS) -> BinaryOperator.DIVIDE
            check(TokenType.PERCENT_EQUALS) -> BinaryOperator.MODULO
            check(TokenType.AMP_EQUALS) -> BinaryOperator.BITWISE_AND
            check(TokenType.PIPE_EQUALS) -> BinaryOperator.BITWISE_OR
            check(TokenType.CARET_EQUALS) -> BinaryOperator.BITWISE_XOR
            check(TokenType.SHIFT_LEFT_EQUALS) -> BinaryOperator.SHIFT_LEFT
            check(TokenType.SHIFT_RIGHT_EQUALS) -> BinaryOperator.SHIFT_RIGHT
            check(TokenType.DOUBLE_STAR_EQUALS) -> BinaryOperator.POWER
            else -> return left  // Not an assignment
        }

        // Verify target is assignable
        if (left !is Identifier && left !is MemberAccess && left !is IndexAccess) {
            error("Invalid assignment target")
        }

        val opToken = advance()  // consume the assignment operator
        val right = parseAssignment()  // right-associative

        val value = if (assignOp != null) {
            // Desugar compound assignment: x += y  →  x = x + y
            BinaryOperation(left, assignOp, right, opToken.toSourceLocation())
        } else {
            right
        }

        return AssignmentExpression(left, value, opToken.toSourceLocation())
    }

    /**
     * Ternary operator: condition ? thenExpr : elseExpr
     * Right-associative.
     */
    private fun parseTernary(): Expression {
        val condition = parseNullishCoalescing()

        if (!match(TokenType.QUESTION)) return condition

        val questionToken = previous()
        val thenExpr = parseTernary()  // Right-associative
        consume(TokenType.COLON, "Expected ':' in ternary expression")
        val elseExpr = parseTernary()  // Right-associative

        return TernaryExpression(condition, thenExpr, elseExpr, questionToken.toSourceLocation())
    }

    /**
     * Try to parse arrow function parameters
     * Returns null if doesn't match arrow parameter pattern
     */
    private fun tryParseArrowParams(): List<String>? {
        return when {
            // Single param: x =>
            check(TokenType.IDENTIFIER) -> {
                val lookahead = tokens.getOrNull(pos + 1)
                if (lookahead?.type == TokenType.ARROW) {
                    listOf(advance().text)
                } else {
                    null
                }
            }

            // Multiple/zero params: (a, b) => or () =>
            check(TokenType.LEFT_PAREN) -> {
                val saved = pos
                advance() // consume (

                val params = mutableListOf<String>()
                while (!check(TokenType.RIGHT_PAREN) && !isAtEnd()) {
                    if (!check(TokenType.IDENTIFIER)) {
                        pos = saved
                        return null
                    }
                    params.add(advance().text)
                    if (!match(TokenType.COMMA)) break
                }

                if (!check(TokenType.RIGHT_PAREN)) {
                    pos = saved
                    return null
                }
                advance() // consume )

                // Must be followed by arrow
                if (!check(TokenType.ARROW)) {
                    pos = saved
                    return null
                }

                params
            }

            else -> null
        }
    }

    /**
     * Parse arrow function body
     * Can be expression body or block body
     *
     * Disambiguates between object literals and block bodies:
     * - `=> { key: value }` is an object literal (expression body)
     * - `=> { statement; }` is a block body
     */
    private fun parseArrowBody(): ArrowFunctionBody {
        return when {
            // Check if { is followed by identifier : (object literal)
            check(TokenType.LEFT_BRACE) && isObjectLiteralStart() -> {
                // Object literal as expression body
                val expr = parseArrowFunction() // Right-associative
                ArrowFunctionBody.ExpressionBody(expr)
            }

            // Block body: { statements }
            match(TokenType.LEFT_BRACE) -> {
                val statements = parseBlockStatements()
                consume(TokenType.RIGHT_BRACE, "Expected '}' after block")
                ArrowFunctionBody.BlockBody(statements)
            }

            // Expression body: expr (implicit return)
            else -> {
                val expr = parseArrowFunction() // Right-associative
                ArrowFunctionBody.ExpressionBody(expr)
            }
        }
    }

    /**
     * Check if current position is start of an object literal
     * Look ahead for pattern: { identifier : OR { identifier , OR { identifier } (shorthand)
     */
    private fun isObjectLiteralStart(): Boolean {
        if (!check(TokenType.LEFT_BRACE)) return false

        // Look ahead: { identifier :
        val saved = pos
        advance() // consume {

        // Empty object {}
        if (check(TokenType.RIGHT_BRACE)) {
            pos = saved
            return true
        }

        // Check for identifier or string followed by colon (regular property)
        val nextType = tokens.getOrNull(pos + 1)?.type
        val isObjLiteral =
            (check(TokenType.IDENTIFIER) || check(TokenType.STRING) || check(TokenType.BACKTICK_STRING)) &&
                    (nextType == TokenType.COLON ||
                            nextType == TokenType.COMMA ||
                            nextType == TokenType.RIGHT_BRACE)

        pos = saved
        return isObjLiteral
    }

    /**
     * Nullish coalescing (??)
     */
    private fun parseNullishCoalescing(): Expression {
        var expr = parseLogicalOr()

        while (match(TokenType.DOUBLE_QUESTION)) {
            val opToken = previous()
            val right = parseLogicalOr()
            expr = BinaryOperation(expr, BinaryOperator.NULLISH_COALESCE, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Logical OR (||)
     */
    private fun parseLogicalOr(): Expression {
        var expr = parseLogicalAnd()

        while (match(TokenType.DOUBLE_PIPE)) {
            val opToken = previous()
            val right = parseLogicalAnd()
            expr = BinaryOperation(expr, BinaryOperator.OR, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Logical AND (&&)
     */
    private fun parseLogicalAnd(): Expression {
        var expr = parseBitwiseOr()

        while (match(TokenType.DOUBLE_AMP)) {
            val opToken = previous()
            val right = parseBitwiseOr()
            expr = BinaryOperation(expr, BinaryOperator.AND, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Bitwise OR (|)
     */
    private fun parseBitwiseOr(): Expression {
        var expr = parseBitwiseXor()

        while (match(TokenType.PIPE)) {
            val opToken = previous()
            val right = parseBitwiseXor()
            expr = BinaryOperation(expr, BinaryOperator.BITWISE_OR, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Bitwise XOR (^)
     */
    private fun parseBitwiseXor(): Expression {
        var expr = parseBitwiseAnd()

        while (match(TokenType.CARET)) {
            val opToken = previous()
            val right = parseBitwiseAnd()
            expr = BinaryOperation(expr, BinaryOperator.BITWISE_XOR, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Bitwise AND (&)
     */
    private fun parseBitwiseAnd(): Expression {
        var expr = parseEquality()

        while (match(TokenType.AMP)) {
            val opToken = previous()
            val right = parseEquality()
            expr = BinaryOperation(expr, BinaryOperator.BITWISE_AND, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Equality operators: ===, !==, ==, !=
     */
    private fun parseEquality(): Expression {
        var expr = parseComparison()

        while (match(
                TokenType.TRIPLE_EQUALS, TokenType.NOT_DOUBLE_EQUALS,
                TokenType.DOUBLE_EQUALS, TokenType.NOT_EQUALS,
            )
        ) {
            val operator = when (previous().type) {
                TokenType.TRIPLE_EQUALS -> BinaryOperator.STRICT_EQUAL
                TokenType.NOT_DOUBLE_EQUALS -> BinaryOperator.STRICT_NOT_EQUAL
                TokenType.DOUBLE_EQUALS -> BinaryOperator.EQUAL
                TokenType.NOT_EQUALS -> BinaryOperator.NOT_EQUAL
                else -> error("Unexpected operator")
            }
            val opToken = previous()
            val right = parseComparison()
            expr = BinaryOperation(expr, operator, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Relational/comparison operators: <, <=, >, >=, in
     */
    private fun parseComparison(): Expression {
        var expr = parseShift()

        while (match(
                TokenType.LESS_THAN, TokenType.LESS_EQUAL,
                TokenType.GREATER_THAN, TokenType.GREATER_EQUAL,
                TokenType.IN
            )
        ) {
            val operator = when (previous().type) {
                TokenType.LESS_THAN -> BinaryOperator.LESS_THAN
                TokenType.LESS_EQUAL -> BinaryOperator.LESS_THAN_OR_EQUAL
                TokenType.GREATER_THAN -> BinaryOperator.GREATER_THAN
                TokenType.GREATER_EQUAL -> BinaryOperator.GREATER_THAN_OR_EQUAL
                TokenType.IN -> BinaryOperator.IN
                else -> error("Unexpected operator")
            }
            val opToken = previous()
            val right = parseShift()
            expr = BinaryOperation(expr, operator, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Shift operators: <<, >>, >>>
     */
    private fun parseShift(): Expression {
        var expr = parseAddition()

        while (match(TokenType.SHIFT_LEFT, TokenType.SHIFT_RIGHT, TokenType.UNSIGNED_SHIFT_RIGHT)) {
            val operator = when (previous().type) {
                TokenType.SHIFT_LEFT -> BinaryOperator.SHIFT_LEFT
                TokenType.SHIFT_RIGHT -> BinaryOperator.SHIFT_RIGHT
                TokenType.UNSIGNED_SHIFT_RIGHT -> BinaryOperator.UNSIGNED_SHIFT_RIGHT
                else -> error("Unexpected operator")
            }
            val opToken = previous()
            val right = parseAddition()
            expr = BinaryOperation(expr, operator, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Addition and subtraction: +, -
     */
    private fun parseAddition(): Expression {
        var expr = parseMultiplication()

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = if (previous().type == TokenType.PLUS) {
                BinaryOperator.ADD
            } else {
                BinaryOperator.SUBTRACT
            }
            val opToken = previous()
            val right = parseMultiplication()
            expr = BinaryOperation(expr, operator, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Multiplication, division, and modulo: *, /, %
     */
    private fun parseMultiplication(): Expression {
        var expr = parsePower()

        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val operator = when (previous().type) {
                TokenType.STAR -> BinaryOperator.MULTIPLY
                TokenType.SLASH -> BinaryOperator.DIVIDE
                TokenType.PERCENT -> BinaryOperator.MODULO
                else -> error("Unexpected operator")
            }
            val opToken = previous()
            val right = parsePower()
            expr = BinaryOperation(expr, operator, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Exponentiation: a ** b  (right-associative, higher than multiplication)
     */
    private fun parsePower(): Expression {
        val base = parseUnary()

        if (match(TokenType.DOUBLE_STAR)) {
            val opToken = previous()
            val exponent = parsePower()  // Right-associative
            return BinaryOperation(base, BinaryOperator.POWER, exponent, opToken.toSourceLocation())
        }

        return base
    }

    /**
     * Unary operators: -expr, +expr, !expr, ++x, --x
     *
     * Note: ++ and -- are only treated as prefix increment/decrement when followed by
     * an identifier. Otherwise (e.g. --10) they fall through to parseCallExpression.
     */
    private fun parseUnary(): Expression {
        when {
            match(TokenType.MINUS, TokenType.PLUS, TokenType.EXCLAMATION, TokenType.TILDE) -> {
                val operator = when (previous().type) {
                    TokenType.MINUS -> UnaryOperator.NEGATE
                    TokenType.PLUS -> UnaryOperator.PLUS
                    TokenType.EXCLAMATION -> UnaryOperator.NOT
                    TokenType.TILDE -> UnaryOperator.BITWISE_NOT
                    else -> error("Unexpected unary operator")
                }
                val opToken = previous()
                val operand = parseUnary() // Right-associative
                return UnaryOperation(operator, operand, opToken.toSourceLocation())
            }

            // ++x — prefix increment when followed by an identifier
            check(TokenType.PLUS_PLUS) && tokens.getOrNull(pos + 1)?.type == TokenType.IDENTIFIER -> {
                advance() // consume ++
                val opToken = previous()
                val operand = parseCallExpression()
                return UnaryOperation(UnaryOperator.PREFIX_INCREMENT, operand, opToken.toSourceLocation())
            }

            // ++expr (non-identifier) — desugar as +(+expr) for compatibility
            check(TokenType.PLUS_PLUS) -> {
                advance() // consume ++
                val opToken = previous()
                val operand = parseUnary() // right-associative
                return UnaryOperation(
                    UnaryOperator.PLUS,
                    UnaryOperation(UnaryOperator.PLUS, operand, opToken.toSourceLocation()),
                    opToken.toSourceLocation()
                )
            }

            // --x — prefix decrement when followed by an identifier
            check(TokenType.MINUS_MINUS) && tokens.getOrNull(pos + 1)?.type == TokenType.IDENTIFIER -> {
                advance() // consume --
                val opToken = previous()
                val operand = parseCallExpression()
                return UnaryOperation(UnaryOperator.PREFIX_DECREMENT, operand, opToken.toSourceLocation())
            }

            // --expr (non-identifier) — desugar as -(-expr) for backward compatibility with --10
            check(TokenType.MINUS_MINUS) -> {
                advance() // consume --
                val opToken = previous()
                val operand = parseUnary() // right-associative
                return UnaryOperation(
                    UnaryOperator.NEGATE,
                    UnaryOperation(UnaryOperator.NEGATE, operand, opToken.toSourceLocation()),
                    opToken.toSourceLocation()
                )
            }
        }
        return parseCallExpression()
    }

    /**
     * Call and member access (method chaining)
     * CRITICAL: Allows alternating .prop, (), [ index ] in any order
     * Also handles postfix ++ and --
     * Fixes: sine2.fromBipolar().range(0.1, 0.9)
     */
    private fun parseCallExpression(): Expression {
        var expr = parsePrimary()

        // Loop handles ANY sequence: .prop, (), [i], x++, x--, etc.
        while (true) {
            when {
                match(TokenType.DOT) -> {
                    val property = consume(TokenType.IDENTIFIER, "Expected property name after '.'")
                    expr = MemberAccess(expr, property.text, optional = false, property.toSourceLocation())
                }

                match(TokenType.QUESTION_DOT) -> {
                    val property = consume(TokenType.IDENTIFIER, "Expected property name after '?.'")
                    expr = MemberAccess(expr, property.text, optional = true, property.toSourceLocation())
                }

                match(TokenType.LEFT_PAREN) -> {
                    val lparen = previous()
                    val args = parseArguments()
                    consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments")
                    expr = CallExpression(expr, args, lparen.toSourceLocation())
                }

                // Only treat [ as index access when preceded by an indexable expression
                // (identifier, call, member access, or another index access)
                // This prevents parsing `5\n[1, 2, 3]` as `5[1, 2, 3]`
                check(TokenType.LEFT_BRACKET) &&
                        (expr is Identifier || expr is CallExpression || expr is MemberAccess || expr is IndexAccess) -> {
                    advance()  // consume [
                    val lbracket = previous()
                    val index = parseExpression()
                    consume(TokenType.RIGHT_BRACKET, "Expected ']' after index")
                    expr = IndexAccess(expr, index, lbracket.toSourceLocation())
                }

                // Postfix ++ and -- only apply to assignable expressions (identifiers)
                // to avoid ambiguity with prefix ++/-- on the next line/statement
                check(TokenType.PLUS_PLUS) && expr is Identifier -> {
                    advance()  // consume ++
                    val opToken = previous()
                    expr = UnaryOperation(UnaryOperator.POSTFIX_INCREMENT, expr, opToken.toSourceLocation())
                }

                check(TokenType.MINUS_MINUS) && expr is Identifier -> {
                    advance()  // consume --
                    val opToken = previous()
                    expr = UnaryOperation(UnaryOperator.POSTFIX_DECREMENT, expr, opToken.toSourceLocation())
                }

                else -> break
            }
        }

        return expr
    }

    /**
     * Parse function call arguments
     * Handles trailing commas
     */
    private fun parseArguments(): List<Expression> {
        val args = mutableListOf<Expression>()

        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                args.add(parseExpression())
            } while (match(TokenType.COMMA) && !check(TokenType.RIGHT_PAREN))
        }

        return args
    }

    /**
     * Primary expressions: literals, identifiers, grouping, objects, arrays
     */
    private fun parsePrimary(): Expression {
        return when {
            match(TokenType.NUMBER) -> {
                val token = previous()
                NumberLiteral(token.text.toDouble(), token.toSourceLocation())
            }

            match(TokenType.STRING) -> {
                val token = previous()
                StringLiteral(token.text, token.toSourceLocation())
            }

            match(TokenType.BACKTICK_STRING) -> {
                val token = previous()
                parseTemplateLiteralFromRaw(token.text, token.toSourceLocation())
            }

            match(TokenType.TRUE) -> {
                BooleanLiteral(true, previous().toSourceLocation())
            }

            match(TokenType.FALSE) -> {
                BooleanLiteral(false, previous().toSourceLocation())
            }

            match(TokenType.NULL) -> {
                NullLiteral
            }

            match(TokenType.IF) -> {
                parseIfExpression()
            }

            match(TokenType.IDENTIFIER) -> {
                val token = previous()
                Identifier(token.text, token.toSourceLocation())
            }

            match(TokenType.LEFT_BRACE) -> {
                parseObjectLiteral()
            }

            match(TokenType.LEFT_BRACKET) -> {
                parseArrayLiteral()
            }

            match(TokenType.LEFT_PAREN) -> {
                val expr = parseExpression()
                consume(TokenType.RIGHT_PAREN, "Expected ')' after expression")
                expr
            }

            else -> error("Expected expression")
        }
    }

    /**
     * Parse object literal: { key: value, ... }
     * Supports shorthand properties: { name } == { name: name }
     * Supports trailing commas
     * Mixed shorthand and regular: { name, age: 30 }
     */
    private fun parseObjectLiteral(): ObjectLiteral {
        val startToken = previous() // LEFT_BRACE consumed
        val properties = mutableListOf<Pair<String, Expression>>()

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            // Key: identifier or string
            if (!match(TokenType.IDENTIFIER, TokenType.STRING, TokenType.BACKTICK_STRING)) {
                error("Expected property key")
            }
            val keyToken = previous()
            val key = keyToken.text

            val value = if (check(TokenType.COLON)) {
                // Regular property: key: value
                advance() // consume ':'
                parseExpression()
            } else {
                // Shorthand property: key  (same as key: key)
                // Only valid for IDENTIFIER keys (shorthand means "use variable with same name")
                if (keyToken.type != TokenType.IDENTIFIER) {
                    error("Shorthand property notation requires an identifier key")
                }
                Identifier(key, keyToken.toSourceLocation())
            }

            properties.add(key to value)

            if (!match(TokenType.COMMA)) break
            // Allow trailing comma
            if (check(TokenType.RIGHT_BRACE)) break
        }

        consume(TokenType.RIGHT_BRACE, "Expected '}'")
        return ObjectLiteral(properties, startToken.toSourceLocation())
    }

    /**
     * Parse array literal: [expr1, expr2, ...]
     * Supports trailing commas
     */
    private fun parseArrayLiteral(): ArrayLiteral {
        val startToken = previous() // LEFT_BRACKET consumed
        val elements = mutableListOf<Expression>()

        while (!check(TokenType.RIGHT_BRACKET) && !isAtEnd()) {
            elements.add(parseExpression())
            if (!match(TokenType.COMMA)) break
            // Allow trailing comma
            if (check(TokenType.RIGHT_BRACKET)) break
        }

        consume(TokenType.RIGHT_BRACKET, "Expected ']'")
        return ArrayLiteral(elements, startToken.toSourceLocation())
    }

    // ============================================================
    // Statement Parsing
    // ============================================================

    private fun parseStatement(): Statement {
        return when {
            match(TokenType.IMPORT) -> parseImportStatement()
            match(TokenType.EXPORT) -> parseExportStatement()
            match(TokenType.LET) -> parseLetDeclaration()
            match(TokenType.CONST) -> parseConstDeclaration()
            match(TokenType.RETURN) -> parseReturnStatement()
            match(TokenType.IF) -> ExpressionStatement(parseIfExpression())
            match(TokenType.WHILE) -> parseWhileStatement()
            match(TokenType.DO) -> parseDoWhileStatement()
            match(TokenType.FOR) -> parseForStatement()
            match(TokenType.BREAK) -> {
                val tok = previous()
                BreakStatement(tok.toSourceLocation())
            }

            match(TokenType.CONTINUE) -> {
                val tok = previous()
                ContinueStatement(tok.toSourceLocation())
            }
            else -> parseExpression().let { ExpressionStatement(it, it.location) }
        }
    }

    /**
     * Parse let declaration: let x = expr OR let x
     */
    private fun parseLetDeclaration(): LetDeclaration {
        val letToken = previous() // LET consumed
        val name = consume(TokenType.IDENTIFIER, "Expected variable name")

        val initializer = if (match(TokenType.EQUALS)) {
            parseExpression()
        } else {
            null
        }

        return LetDeclaration(name.text, initializer, letToken.toSourceLocation())
    }

    /**
     * Parse const declaration: const x = expr
     */
    private fun parseConstDeclaration(): ConstDeclaration {
        val constToken = previous() // CONST consumed
        val name = consume(TokenType.IDENTIFIER, "Expected constant name")
        consume(TokenType.EQUALS, "Expected '=' after const name")
        val initializer = parseExpression()

        return ConstDeclaration(name.text, initializer, constToken.toSourceLocation())
    }

    /**
     * Parse return statement: return expr OR return
     */
    private fun parseReturnStatement(): ReturnStatement {
        val returnToken = previous() // RETURN consumed

        val value = if (isAtEnd() || check(TokenType.RIGHT_BRACE)) {
            null
        } else {
            parseExpression()
        }

        return ReturnStatement(value, returnToken.toSourceLocation())
    }

    /**
     * Parse import statement
     * Forms:
     * - import * from "lib"
     * - import * as name from "lib"
     * - import { x, y as z } from "lib"
     */
    private fun parseImportStatement(): ImportStatement {
        val importToken = previous() // IMPORT consumed

        when {
            match(TokenType.STAR) -> {
                // Wildcard or namespace import
                val namespaceAlias = if (match(TokenType.AS)) {
                    consume(TokenType.IDENTIFIER, "Expected namespace name").text
                } else {
                    null
                }

                consume(TokenType.FROM, "Expected 'from' after import")
                val libraryName = consume(TokenType.STRING, "Expected library name").text

                return ImportStatement(
                    libraryName,
                    imports = null,
                    namespaceAlias = namespaceAlias,
                    location = importToken.toSourceLocation()
                )
            }

            match(TokenType.LEFT_BRACE) -> {
                // Selective import
                val imports = mutableListOf<Pair<String, String>>()

                do {
                    val name = consume(TokenType.IDENTIFIER, "Expected import name").text
                    val alias = if (match(TokenType.AS)) {
                        consume(TokenType.IDENTIFIER, "Expected alias").text
                    } else {
                        name
                    }
                    imports.add(name to alias)
                } while (match(TokenType.COMMA))

                consume(TokenType.RIGHT_BRACE, "Expected '}'")
                consume(TokenType.FROM, "Expected 'from'")
                val libraryName = consume(TokenType.STRING, "Expected library name").text

                return ImportStatement(
                    libraryName,
                    imports,
                    location = importToken.toSourceLocation()
                )
            }

            else -> error("Expected '*' or '{' after 'import'")
        }
    }

    /**
     * Parse export statement: export { x, y as z }
     */
    private fun parseExportStatement(): ExportStatement {
        val exportToken = previous() // EXPORT consumed
        consume(TokenType.LEFT_BRACE, "Expected '{' after 'export'")

        val exports = mutableListOf<Pair<String, String>>()

        do {
            val localName = consume(TokenType.IDENTIFIER, "Expected export name").text
            val exportedName = if (match(TokenType.AS)) {
                consume(TokenType.IDENTIFIER, "Expected exported name").text
            } else {
                localName
            }
            exports.add(localName to exportedName)
        } while (match(TokenType.COMMA))

        consume(TokenType.RIGHT_BRACE, "Expected '}'")

        return ExportStatement(exports, exportToken.toSourceLocation())
    }

    /**
     * Parse program: zero or more statements
     */
    private fun parseProgram(): Program {
        val statements = mutableListOf<Statement>()

        while (!isAtEnd()) {
            skipSemicolons()
            if (isAtEnd()) break
            statements.add(parseStatement())
            skipSemicolons()
        }

        return Program(statements)
    }

    /**
     * Parse if expression: if ( condition ) { then } [ else { else } | else if ... ]
     *
     * `if` is an expression so it can produce a value.
     * At statement level it becomes an ExpressionStatement(IfExpression(...)).
     */
    private fun parseIfExpression(): IfExpression {
        val ifToken = previous() // IF consumed
        consume(TokenType.LEFT_PAREN, "Expected '(' after 'if'")
        val condition = parseExpression()
        consume(TokenType.RIGHT_PAREN, "Expected ')' after if condition")
        consume(TokenType.LEFT_BRACE, "Expected '{' after if condition")
        val thenBranch = parseBlockStatements()
        consume(TokenType.RIGHT_BRACE, "Expected '}' after if body")

        val elseBranch = if (match(TokenType.ELSE)) {
            if (match(TokenType.IF)) {
                // else if chain
                ElseBranch.If(parseIfExpression())
            } else {
                // else block
                consume(TokenType.LEFT_BRACE, "Expected '{' after 'else'")
                val elseStmts = parseBlockStatements()
                consume(TokenType.RIGHT_BRACE, "Expected '}' after else body")
                ElseBranch.Block(elseStmts)
            }
        } else {
            null
        }

        return IfExpression(condition, thenBranch, elseBranch, ifToken.toSourceLocation())
    }

    /**
     * Parse statements inside a block (until RIGHT_BRACE or EOF)
     * Skips semicolons between statements.
     */
    private fun parseBlockStatements(): List<Statement> {
        val statements = mutableListOf<Statement>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            skipSemicolons()
            if (check(TokenType.RIGHT_BRACE) || isAtEnd()) break
            statements.add(parseStatement())
            skipSemicolons()
        }
        return statements
    }

    /**
     * Parse template literal from raw backtick string content.
     *
     * Scans the raw string for `${...}` patterns and builds a TemplateLiteral node.
     * If no interpolations exist, returns a plain StringLiteral for efficiency.
     */
    private fun parseTemplateLiteralFromRaw(raw: String, location: SourceLocation): Expression {
        // Quick check: if no '${' in the string, return a plain StringLiteral
        if (!raw.contains("\${")) {
            return StringLiteral(raw, location)
        }

        val parts = mutableListOf<TemplatePart>()
        var idx = 0

        while (idx < raw.length) {
            // Find next `${`
            val interpStart = raw.indexOf("\${", idx)
            if (interpStart < 0) {
                // Rest is plain text
                if (idx < raw.length) {
                    parts.add(TemplatePart.Text(raw.substring(idx)))
                }
                break
            }

            // Add text before `${`
            if (interpStart > idx) {
                parts.add(TemplatePart.Text(raw.substring(idx, interpStart)))
            }

            // Find matching `}`, tracking string literals so braces inside strings are ignored.
            val exprStart = interpStart + 2
            var depth = 1
            var j = exprStart
            var inString: Char? = null   // current string delimiter (' or "), null if not in string
            var escaped = false          // true if previous char was backslash inside a string
            while (j < raw.length && depth > 0) {
                val c = raw[j]
                when {
                    escaped -> escaped = false
                    inString != null && c == '\\' -> escaped = true
                    inString != null && c == inString -> inString = null
                    inString == null && (c == '\'' || c == '"') -> inString = c
                    inString == null && c == '{' -> depth++
                    inString == null && c == '}' -> depth--
                }
                if (depth > 0) j++
            }
            if (depth != 0) {
                error("Unterminated template literal interpolation")
            }
            val exprSource = raw.substring(exprStart, j)

            // Parse the expression inside ${...} using a sub-parser
            val subProgram = parse(exprSource, currentSource)
            if (subProgram.statements.isEmpty()) {
                error("Empty expression in template literal interpolation")
            }

            val expr = when (val stmt = subProgram.statements.first()) {
                is ExpressionStatement -> stmt.expression
                else -> error("Template literal interpolation must be an expression")
            }
            parts.add(TemplatePart.Interp(expr))

            idx = j + 1 // Skip past the closing `}`
        }

        // If only one text part, return StringLiteral
        if (parts.size == 1 && parts[0] is TemplatePart.Text) {
            return StringLiteral((parts[0] as TemplatePart.Text).value, location)
        }

        return TemplateLiteral(parts, location)
    }

    /**
     * Parse while statement: while ( condition ) { body }
     */
    private fun parseWhileStatement(): WhileStatement {
        val whileToken = previous() // WHILE consumed
        consume(TokenType.LEFT_PAREN, "Expected '(' after 'while'")

        val condition = parseExpression()
        consume(TokenType.RIGHT_PAREN, "Expected ')' after while condition")
        consume(TokenType.LEFT_BRACE, "Expected '{' after while condition")

        val body = parseBlockStatements()
        consume(TokenType.RIGHT_BRACE, "Expected '}' after while body")

        return WhileStatement(condition, body, whileToken.toSourceLocation())
    }

    /**
     * Parse do-while statement: do { body } while ( condition )
     */
    private fun parseDoWhileStatement(): DoWhileStatement {
        val doToken = previous() // DO consumed
        consume(TokenType.LEFT_BRACE, "Expected '{' after 'do'")

        val body = parseBlockStatements()
        consume(TokenType.RIGHT_BRACE, "Expected '}' after do body")
        consume(TokenType.WHILE, "Expected 'while' after do body")
        consume(TokenType.LEFT_PAREN, "Expected '(' after 'while'")

        val condition = parseExpression()
        consume(TokenType.RIGHT_PAREN, "Expected ')' after do-while condition")

        return DoWhileStatement(body, condition, doToken.toSourceLocation())
    }

    /**
     * Parse for statement: for ( [ init ] ; [ condition ] ; [ update ] ) { body }
     *
     * The for loop runs in its own scope (init let declarations are scoped to loop).
     * Semicolons are used as separators in the for header (not statement terminators).
     * The init can be a let declaration, an assignment expression, or empty.
     */
    private fun parseForStatement(): ForStatement {
        val forToken = previous() // FOR consumed
        consume(TokenType.LEFT_PAREN, "Expected '(' after 'for'")

        // Parse init (optional)
        val init: Statement? = when {
            check(TokenType.SEMICOLON) -> null  // empty init
            match(TokenType.LET) -> parseLetDeclaration()
            match(TokenType.CONST) -> parseConstDeclaration()
            else -> {
                val expr = parseExpression()
                ExpressionStatement(expr)
            }
        }
        consume(TokenType.SEMICOLON, "Expected ';' after for init")

        // Parse condition (optional)
        val condition: Expression? = if (check(TokenType.SEMICOLON)) null else parseExpression()
        consume(TokenType.SEMICOLON, "Expected ';' after for condition")

        // Parse update (optional)
        val update: Expression? = if (check(TokenType.RIGHT_PAREN)) null else parseExpression()
        consume(TokenType.RIGHT_PAREN, "Expected ')' after for clauses")

        consume(TokenType.LEFT_BRACE, "Expected '{' after for header")
        val body = parseBlockStatements()
        consume(TokenType.RIGHT_BRACE, "Expected '}' after for body")

        return ForStatement(init, condition, update, body, forToken.toSourceLocation())
    }
}
