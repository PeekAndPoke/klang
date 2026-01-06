package io.peekandpoke.klang.script.parser

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import io.peekandpoke.klang.script.ast.*

/**
 * Parser for KlangScript using the better-parse combinator library
 *
 * This parser transforms source code text into an Abstract Syntax Tree (AST).
 * It uses better-parse's combinator approach, which allows composing simple
 * parsers into more complex ones.
 *
 * Current capabilities:
 * - Numeric and string literals
 * - Identifiers (variables and function names)
 * - Function calls with arguments
 * - Nested function calls
 * - Single-line comments
 * - Multiple statements (newline-separated)
 */
object KlangScriptParser : Grammar<Program>() {

    // ============================================================
    // Lexical Tokens
    // ============================================================

    /** Whitespace (ignored but acts as token separator) */
    private val ws by regexToken("\\s+", ignore = true)

    /** Single-line comments starting with // */
    private val lineComment by regexToken("//[^\\n]*", ignore = true)

    /** Numeric literals: 42, 3.14, 0.5 */
    private val number by regexToken("\\d+(\\.\\d+)?")

    /** String literals: "hello", 'world' */
    private val string by regexToken("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")

    /** Identifiers: foo, myVar, _private */
    private val identifier by regexToken("[a-zA-Z_][a-zA-Z0-9_]*")

    /** Punctuation for function calls */
    private val leftParen by literalToken("(")
    private val rightParen by literalToken(")")
    private val comma by literalToken(",")

    // ============================================================
    // Grammar Rules
    // ============================================================

    /**
     * Forward declaration for recursive grammar
     * Allows nested expressions: print(upper("hello"))
     */
    private val expression: Parser<Expression> by parser(this::callExpr)

    /**
     * Primary expressions - atomic building blocks
     * Numbers, strings, or identifiers
     */
    private val primaryExpr: Parser<Expression> by
    (number use { NumberLiteral(text.toDouble()) }) or
            (string use { StringLiteral(text.substring(1, text.length - 1)) }) or  // Strip quotes
            (identifier use { Identifier(text) })

    /**
     * Call expressions - function calls
     * Syntax: callee(arg1, arg2, ...)
     * Trailing commas allowed: func(1, 2,)
     */
    private val callExpr: Parser<Expression> by
    (primaryExpr and optional(
        skip(leftParen) and  // Parse but don't include in result
                separatedTerms(expression, comma, acceptZero = true) and
                skip(rightParen)
    )).map { (callee, argsOpt) ->
        // If we found arguments, create CallExpression
        // Otherwise, return the callee as-is
        if (argsOpt != null) {
            CallExpression(callee, argsOpt)
        } else {
            callee
        }
    }

    /**
     * Expression statements
     * Expressions used as statements: print("hello")
     */
    private val expressionStatement: Parser<Statement> by
    expression.map { ExpressionStatement(it) }

    /**
     * Statements
     * Currently only expression statements supported
     */
    private val statement: Parser<Statement> by expressionStatement

    /**
     * Program root
     * Zero or more statements (newline-separated)
     */
    override val rootParser: Parser<Program> by
    zeroOrMore(statement).map { Program(it) }

    /**
     * Parse source code into an AST
     *
     * @param source The KlangScript source code
     * @return Program AST node
     * @throws ParseException on syntax errors
     */
    fun parse(source: String): Program {
        return parseToEnd(source)
    }
}
