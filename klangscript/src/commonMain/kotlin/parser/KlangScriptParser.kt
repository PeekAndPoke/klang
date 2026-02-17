package io.peekandpoke.klang.script.parser

import io.peekandpoke.klang.script.ast.*

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
        /**
         * Parse KlangScript source code into an AST
         *
         * @param source The source code to parse
         * @param sourceName Optional source file name for error reporting
         * @return Program AST node
         * @throws ParseException if the source contains syntax errors
         */
        fun parse(source: String, sourceName: String? = null): Program {
            val parser = KlangScriptParser(currentSource = sourceName)
            return parser.parseInternal(source)
        }
    }

    // ============================================================
    // Token Types and Lexer
    // ============================================================

    private enum class TokenType {
        // Literals
        NUMBER, STRING, BACKTICK_STRING, IDENTIFIER,

        // Keywords (must match before IDENTIFIER)
        TRUE, FALSE, NULL, LET, CONST, IMPORT, EXPORT, FROM, AS, RETURN,

        // Punctuation
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
        LEFT_BRACKET, RIGHT_BRACKET, COMMA, COLON, DOT,

        // Multi-char operators (MUST tokenize before single-char)
        ARROW,           // =>
        DOUBLE_EQUALS,   // ==
        NOT_EQUALS,      // !=
        LESS_EQUAL,      // <=
        GREATER_EQUAL,   // >=
        DOUBLE_AMP,      // &&
        DOUBLE_PIPE,     // ||

        // Single-char operators
        EQUALS, PLUS, MINUS, STAR, SLASH, PERCENT,
        EXCLAMATION, LESS_THAN, GREATER_THAN,

        EOF
    }

    private data class Token(
        val type: TokenType,
        val text: String,
        val line: Int,      // 1-based (start line)
        val column: Int,    // 1-based (start column)
        val endLine: Int,   // 1-based
        val endColumn: Int,  // 1-based, exclusive
    ) {
        fun toLocation(): SourceLocation {
            return SourceLocation(
                source = null, // Will be set by parser
                startLine = line,
                startColumn = column,
                endLine = endLine,
                endColumn = endColumn
            )
        }
    }

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
            val ch = source[i]
            val startColumn = column

            when {
                // Whitespace
                ch == ' ' || ch == '\t' || ch == '\r' -> {
                    i++
                    column++
                }

                // Newline
                ch == '\n' -> {
                    i++
                    line++
                    column = 1
                }

                // Single-line comment
                ch == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    i += 2
                    column += 2
                    while (i < source.length && source[i] != '\n') {
                        i++
                        column++
                    }
                }

                // Multi-line comment
                ch == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i += 2
                    column += 2
                    while (i < source.length - 1) {
                        if (source[i] == '*' && source[i + 1] == '/') {
                            i += 2
                            column += 2
                            break
                        }
                        if (source[i] == '\n') {
                            line++
                            column = 1
                        } else {
                            column++
                        }
                        i++
                    }
                }

                // Multi-char operators (MUST check before single-char)
                ch == '=' && i + 1 < source.length && source[i + 1] == '>' -> {
                    addToken(TokenType.ARROW, "=>", startColumn)
                    i += 2
                    column += 2
                }

                ch == '=' && i + 1 < source.length && source[i + 1] == '=' -> {
                    addToken(TokenType.DOUBLE_EQUALS, "==", startColumn)
                    i += 2
                    column += 2
                }

                ch == '!' && i + 1 < source.length && source[i + 1] == '=' -> {
                    addToken(TokenType.NOT_EQUALS, "!=", startColumn)
                    i += 2
                    column += 2
                }

                ch == '<' && i + 1 < source.length && source[i + 1] == '=' -> {
                    addToken(TokenType.LESS_EQUAL, "<=", startColumn)
                    i += 2
                    column += 2
                }

                ch == '>' && i + 1 < source.length && source[i + 1] == '=' -> {
                    addToken(TokenType.GREATER_EQUAL, ">=", startColumn)
                    i += 2
                    column += 2
                }

                ch == '&' && i + 1 < source.length && source[i + 1] == '&' -> {
                    addToken(TokenType.DOUBLE_AMP, "&&", startColumn)
                    i += 2
                    column += 2
                }

                ch == '|' && i + 1 < source.length && source[i + 1] == '|' -> {
                    addToken(TokenType.DOUBLE_PIPE, "||", startColumn)
                    i += 2
                    column += 2
                }

                // Single-char tokens
                ch == '(' -> {
                    addToken(TokenType.LEFT_PAREN, "(", startColumn)
                    i++
                    column++
                }

                ch == ')' -> {
                    addToken(TokenType.RIGHT_PAREN, ")", startColumn)
                    i++
                    column++
                }

                ch == '{' -> {
                    addToken(TokenType.LEFT_BRACE, "{", startColumn)
                    i++
                    column++
                }

                ch == '}' -> {
                    addToken(TokenType.RIGHT_BRACE, "}", startColumn)
                    i++
                    column++
                }

                ch == '[' -> {
                    addToken(TokenType.LEFT_BRACKET, "[", startColumn)
                    i++
                    column++
                }

                ch == ']' -> {
                    addToken(TokenType.RIGHT_BRACKET, "]", startColumn)
                    i++
                    column++
                }

                ch == ',' -> {
                    addToken(TokenType.COMMA, ",", startColumn)
                    i++
                    column++
                }

                ch == ':' -> {
                    addToken(TokenType.COLON, ":", startColumn)
                    i++
                    column++
                }

                ch == '.' -> {
                    addToken(TokenType.DOT, ".", startColumn)
                    i++
                    column++
                }

                ch == '=' -> {
                    addToken(TokenType.EQUALS, "=", startColumn)
                    i++
                    column++
                }

                ch == '+' -> {
                    addToken(TokenType.PLUS, "+", startColumn)
                    i++
                    column++
                }

                ch == '-' -> {
                    addToken(TokenType.MINUS, "-", startColumn)
                    i++
                    column++
                }

                ch == '*' -> {
                    addToken(TokenType.STAR, "*", startColumn)
                    i++
                    column++
                }

                ch == '/' -> {
                    addToken(TokenType.SLASH, "/", startColumn)
                    i++
                    column++
                }

                ch == '%' -> {
                    addToken(TokenType.PERCENT, "%", startColumn)
                    i++
                    column++
                }

                ch == '!' -> {
                    addToken(TokenType.EXCLAMATION, "!", startColumn)
                    i++
                    column++
                }

                ch == '<' -> {
                    addToken(TokenType.LESS_THAN, "<", startColumn)
                    i++
                    column++
                }

                ch == '>' -> {
                    addToken(TokenType.GREATER_THAN, ">", startColumn)
                    i++
                    column++
                }

                // String literals
                ch == '"' || ch == '\'' -> {
                    val startLine = line  // Save starting line for multiline strings
                    val quote = ch
                    val sb = StringBuilder()
                    i++ // Skip opening quote
                    column++

                    while (i < source.length && source[i] != quote) {
                        if (source[i] == '\\' && i + 1 < source.length) {
                            // Escape sequence
                            i++
                            column++
                            when (source[i]) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                '\\' -> sb.append('\\')
                                '"' -> sb.append('"')
                                '\'' -> sb.append('\'')
                                else -> sb.append(source[i])
                            }
                        } else {
                            sb.append(source[i])
                        }
                        if (source[i] == '\n') {
                            line++
                            column = 1
                        } else {
                            column++
                        }
                        i++
                    }

                    if (i >= source.length) {
                        throw ParseException(ErrorResult("Unterminated string", column, line))
                    }

                    i++ // Skip closing quote
                    column++
                    addToken(TokenType.STRING, sb.toString(), startLine, startColumn, line, column)
                }

                // Backtick strings
                ch == '`' -> {
                    val startLine = line  // Save starting line for multiline strings
                    val sb = StringBuilder()
                    i++ // Skip opening backtick
                    column++

                    while (i < source.length && source[i] != '`') {
                        if (source[i] == '\\' && i + 1 < source.length) {
                            // Escape sequence
                            i++
                            column++
                            when (source[i]) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                '\\' -> sb.append('\\')
                                '`' -> sb.append('`')
                                else -> sb.append(source[i])
                            }
                        } else {
                            sb.append(source[i])
                        }
                        if (source[i] == '\n') {
                            line++
                            column = 1
                        } else {
                            column++
                        }
                        i++
                    }

                    if (i >= source.length) {
                        throw ParseException(ErrorResult("Unterminated backtick string", column, line))
                    }

                    i++ // Skip closing backtick
                    column++
                    addToken(TokenType.BACKTICK_STRING, sb.toString(), startLine, startColumn, line, column)
                }

                // Numbers
                ch.isDigit() -> {
                    val start = i
                    while (i < source.length && (source[i].isDigit() || source[i] == '.')) {
                        i++
                        column++
                    }
                    addToken(TokenType.NUMBER, source.substring(start, i), startColumn)
                }

                // Identifiers and keywords
                ch.isLetter() || ch == '_' -> {
                    val start = i
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) {
                        i++
                        column++
                    }
                    val text = source.substring(start, i)
                    val type = when (text) {
                        "true" -> TokenType.TRUE
                        "false" -> TokenType.FALSE
                        "null" -> TokenType.NULL
                        "let" -> TokenType.LET
                        "const" -> TokenType.CONST
                        "import" -> TokenType.IMPORT
                        "export" -> TokenType.EXPORT
                        "from" -> TokenType.FROM
                        "as" -> TokenType.AS
                        "return" -> TokenType.RETURN
                        else -> TokenType.IDENTIFIER
                    }
                    addToken(type, text, startColumn)
                }

                else -> {
                    throw ParseException(ErrorResult("Unexpected character: $ch", column, line))
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
        throw ParseException(ErrorResult(message, token.column, token.line))
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
        return parseLogicalOr()
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

                // Trailing comma handling
                if (params.isNotEmpty() && previous().type == TokenType.RIGHT_PAREN) {
                    // Check if there was a trailing comma before )
                    // We already consumed it in the loop, so params is correct
                }

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
                val statements = mutableListOf<Statement>()
                while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
                    statements.add(parseStatement())
                }
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
     * Look ahead for pattern: { identifier :
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

        // Check for identifier or string followed by colon
        val isObjLiteral =
            (check(TokenType.IDENTIFIER) || check(TokenType.STRING) || check(TokenType.BACKTICK_STRING)) &&
                    tokens.getOrNull(pos + 1)?.type == TokenType.COLON

        pos = saved
        return isObjLiteral
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
        var expr = parseComparison()

        while (match(TokenType.DOUBLE_AMP)) {
            val opToken = previous()
            val right = parseComparison()
            expr = BinaryOperation(expr, BinaryOperator.AND, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Comparison operators: ==, !=, <, <=, >, >=
     */
    private fun parseComparison(): Expression {
        var expr = parseAddition()

        while (match(
                TokenType.DOUBLE_EQUALS, TokenType.NOT_EQUALS,
                TokenType.LESS_THAN, TokenType.LESS_EQUAL,
                TokenType.GREATER_THAN, TokenType.GREATER_EQUAL
            )
        ) {
            val operator = when (previous().type) {
                TokenType.DOUBLE_EQUALS -> BinaryOperator.EQUAL
                TokenType.NOT_EQUALS -> BinaryOperator.NOT_EQUAL
                TokenType.LESS_THAN -> BinaryOperator.LESS_THAN
                TokenType.LESS_EQUAL -> BinaryOperator.LESS_THAN_OR_EQUAL
                TokenType.GREATER_THAN -> BinaryOperator.GREATER_THAN
                TokenType.GREATER_EQUAL -> BinaryOperator.GREATER_THAN_OR_EQUAL
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
            val operator = if (previous().type == TokenType.PLUS)
                BinaryOperator.ADD
            else
                BinaryOperator.SUBTRACT
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
        var expr = parseUnary()

        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val operator = when (previous().type) {
                TokenType.STAR -> BinaryOperator.MULTIPLY
                TokenType.SLASH -> BinaryOperator.DIVIDE
                TokenType.PERCENT -> BinaryOperator.MODULO
                else -> error("Unexpected operator")
            }
            val opToken = previous()
            val right = parseUnary()
            expr = BinaryOperation(expr, operator, right, opToken.toSourceLocation())
        }

        return expr
    }

    /**
     * Unary operators: -expr, +expr, !expr
     */
    private fun parseUnary(): Expression {
        if (match(TokenType.MINUS, TokenType.PLUS, TokenType.EXCLAMATION)) {
            val operator = when (previous().type) {
                TokenType.MINUS -> UnaryOperator.NEGATE
                TokenType.PLUS -> UnaryOperator.PLUS
                TokenType.EXCLAMATION -> UnaryOperator.NOT
                else -> error("Unexpected unary operator")
            }
            val opToken = previous()
            val operand = parseUnary() // Right-associative
            return UnaryOperation(operator, operand, opToken.toSourceLocation())
        }
        return parseCallExpression()
    }

    /**
     * Call and member access (method chaining)
     * CRITICAL: Allows alternating .prop and () in any order
     * Fixes: sine2.fromBipolar().range(0.1, 0.9)
     */
    private fun parseCallExpression(): Expression {
        var expr = parsePrimary()

        // Loop handles ANY sequence: .prop, (), .prop(), ().(), etc.
        while (true) {
            when {
                match(TokenType.DOT) -> {
                    val property = consume(TokenType.IDENTIFIER, "Expected property name after '.'")
                    expr = MemberAccess(expr, property.text, property.toSourceLocation())
                }

                match(TokenType.LEFT_PAREN) -> {
                    val lparen = previous()
                    val args = parseArguments()
                    consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments")
                    expr = CallExpression(expr, args, lparen.toSourceLocation())
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

            match(TokenType.STRING, TokenType.BACKTICK_STRING) -> {
                val token = previous()
                StringLiteral(token.text, token.toSourceLocation())
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
     * Supports trailing commas
     */
    private fun parseObjectLiteral(): ObjectLiteral {
        val startToken = previous() // LEFT_BRACE consumed
        val properties = mutableListOf<Pair<String, Expression>>()

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            // Key: identifier or string
            val key = when {
                match(TokenType.IDENTIFIER) -> previous().text
                match(TokenType.STRING, TokenType.BACKTICK_STRING) -> previous().text
                else -> error("Expected property key")
            }

            consume(TokenType.COLON, "Expected ':' after property key")
            val value = parseExpression()
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
            else -> ExpressionStatement(parseExpression())
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
            statements.add(parseStatement())
        }

        return Program(statements)
    }
}
