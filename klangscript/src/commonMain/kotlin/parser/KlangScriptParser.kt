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
 * - Member access (dot notation): obj.property
 * - Method chaining: note("c").gain(0.5).pan("0 1")
 * - Arithmetic operators: +, -, *, /
 * - Operator precedence and associativity
 * - Parenthesized expressions
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

    /** Arithmetic operators */
    private val plus by literalToken("+")
    private val minus by literalToken("-")
    private val times by literalToken("*")
    private val divide by literalToken("/")

    /** Member access operator */
    private val dot by literalToken(".")

    /** Arrow function operator */
    private val arrow by literalToken("=>")

    // ============================================================
    // Grammar Rules
    // ============================================================

    /**
     * Forward declaration for top-level expressions
     * Points to the lowest precedence operation (arrow functions)
     */
    private val expression: Parser<Expression> by parser(this::arrowExpr)

    /**
     * Primary expressions - atomic building blocks
     * Numbers, strings, identifiers, or parenthesized expressions
     */
    private val primaryExpr: Parser<Expression> by
    (number use { NumberLiteral(text.toDouble()) }) or
            (string use { StringLiteral(text.substring(1, text.length - 1)) }) or  // Strip quotes
            (identifier use { Identifier(text) }) or
            (-leftParen * parser(this::expression) * -rightParen)  // Parenthesized expressions

    /**
     * Member access expressions - dot notation for properties and methods
     * Syntax: object.property.nestedProperty
     *
     * Member access has higher precedence than function calls, enabling:
     * - obj.method() -> access "method" property, then call it
     * - obj.a.b.c -> chain multiple property accesses
     *
     * This is implemented as left-associative to handle chains:
     * a.b.c parses as (a.b).c
     *
     * Implementation: start with primary expression, then parse zero or more
     * ".property" sequences, building nested MemberAccess nodes.
     */
    private val memberExpr: Parser<Expression> by
    (primaryExpr and zeroOrMore(-dot and identifier)).map { (base, properties) ->
        // Fold the property list into nested MemberAccess nodes
        properties.fold(base) { obj, property ->
            MemberAccess(obj, property.text)
        }
    }

    /**
     * Call expressions - function calls and method calls with chaining
     * Syntax: callee(arg1, arg2, ...)
     * Trailing commas allowed: func(1, 2,)
     *
     * This level handles:
     * - Regular function calls: print("hello")
     * - Method calls: obj.method(arg)
     * - Chained calls: note("c").gain(0.5).pan("0 1")
     *
     * Implementation strategy for chaining:
     * 1. Start with member expression (handles obj.property chains)
     * 2. Parse zero or more of: function call, then more member accesses
     * 3. This allows: obj.method().property.anotherMethod()
     *
     * Example parsing of note("c").gain(0.5):
     * 1. memberExpr parses: note
     * 2. First call: note("c")
     * 3. memberExpr suffix: .gain
     * 4. Second call: (...).gain(0.5)
     */
    private val callExpr: Parser<Expression> by
    (memberExpr and zeroOrMore(
        // Parse: (args) followed by optional .property.property...
        (-leftParen and separatedTerms(expression, comma, acceptZero = true) and -rightParen) and
                zeroOrMore(-dot and identifier)
    )).map { (base, callAndMemberPairs) ->
        // Fold through each call-and-member-access pair
        callAndMemberPairs.fold(base) { current, (args, properties) ->
            // First apply the call
            val afterCall = CallExpression(current, args)
            // Then apply any member accesses
            properties.fold(afterCall as Expression) { obj, property ->
                MemberAccess(obj, property.text)
            }
        }
    }

    /**
     * Multiplication and division (higher precedence)
     * Left-associative: 6 / 2 / 3 = (6 / 2) / 3
     */
    private val multiplicationExpr: Parser<Expression> by
    leftAssociative(callExpr, times or divide) { left, op, right ->
        val operator = when (op.text) {
            "*" -> BinaryOperator.MULTIPLY
            "/" -> BinaryOperator.DIVIDE
            else -> error("Unexpected operator: $op")
        }
        BinaryOperation(left, operator, right)
    }

    /**
     * Addition and subtraction (lower precedence)
     * Left-associative: 5 - 2 - 1 = (5 - 2) - 1
     */
    private val additionExpr: Parser<Expression> by
    leftAssociative(multiplicationExpr, plus or minus) { left, op, right ->
        val operator = when (op.text) {
            "+" -> BinaryOperator.ADD
            "-" -> BinaryOperator.SUBTRACT
            else -> error("Unexpected operator: $op")
        }
        BinaryOperation(left, operator, right)
    }

    /**
     * Arrow function expressions (lowest precedence)
     * Syntax:
     * - Single parameter: `x => expr`
     * - Multiple parameters: `(a, b) => expr`
     * - No parameters: `() => expr`
     *
     * Arrow functions have the lowest precedence to allow expressions in the body:
     * `x => x + 1` parses as `x => (x + 1)`, not `(x => x) + 1`
     *
     * Implementation strategy:
     * 1. Try to parse parameter list (single identifier OR parenthesized list)
     * 2. If we see `=>`, we have an arrow function
     * 3. Otherwise, fall back to regular expression (additionExpr)
     *
     * Examples:
     * - `x => x + 1` - Single param, arithmetic body
     * - `(a, b) => a * b` - Multi param
     * - `() => 42` - No params
     * - `x => y => x + y` - Nested (right-associative)
     * - `x => x.method()` - Method chaining in body
     */
    private val arrowExpr: Parser<Expression> by
    // Try to parse arrow function first
    (
            // Parse parameters: either single identifier or parenthesized list
            (
                    // Single parameter (no parens): x => expr
                    (identifier map { listOf(it.text) }) or
                            // Multiple/zero parameters (with parens): (a, b) => expr or () => expr
                            (-leftParen and separatedTerms(
                                identifier,
                                comma,
                                acceptZero = true
                            ) and -rightParen).map { params ->
                                params.map { it.text }
                            }
                    ) and -arrow and parser(this::arrowExpr)  // Right-associative for nested arrows
            ).map { (params, body) ->
            ArrowFunction(params, body)
        } or additionExpr  // Fall back to addition expression if not an arrow function

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
