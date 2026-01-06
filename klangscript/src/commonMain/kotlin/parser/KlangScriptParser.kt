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
    @Suppress("unused")
    private val ws by regexToken("\\s+", ignore = true)

    /** Single-line comments starting with // */
    @Suppress("unused")
    private val lineComment by regexToken("//[^\\n]*", ignore = true)

    /** Multi-line comments: /* comment */ */
    @Suppress("unused")
    private val blockComment by regexToken("/\\*[\\s\\S]*?\\*/", ignore = true)

    /** Numeric literals: 42, 3.14, 0.5 */
    private val number by regexToken("\\d+(\\.\\d+)?")

    /** Backtick string literals (multi-line): `hello world` */
    private val backtickString by regexToken("`([^`\\\\]|\\\\.)*`")

    /** String literals: "hello", 'world' */
    private val string by regexToken("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")

    /** Keywords - must be defined before identifier */
    private val trueKeyword by literalToken("true")
    private val falseKeyword by literalToken("false")
    private val nullKeyword by literalToken("null")
    private val letKeyword by literalToken("let")
    private val constKeyword by literalToken("const")
    private val importKeyword by literalToken("import")
    private val exportKeyword by literalToken("export")
    private val fromKeyword by literalToken("from")
    private val asKeyword by literalToken("as")

    /** Identifiers: foo, myVar, _private */
    private val identifier by regexToken("[a-zA-Z_][a-zA-Z0-9_]*")

    /** Punctuation for function calls */
    private val leftParen by literalToken("(")
    private val rightParen by literalToken(")")
    private val comma by literalToken(",")

    /** Punctuation for object literals */
    private val leftBrace by literalToken("{")
    private val rightBrace by literalToken("}")
    private val colon by literalToken(":")

    /** Arithmetic operators */
    private val plus by literalToken("+")
    private val minus by literalToken("-")
    private val times by literalToken("*")
    private val divide by literalToken("/")
    private val exclamation by literalToken("!")

    /** Member access operator */
    private val dot by literalToken(".")

    /** Arrow function operator */
    private val arrow by literalToken("=>")

    /** Assignment operator */
    private val equals by literalToken("=")

    // ============================================================
    // Grammar Rules
    // ============================================================

    /**
     * Forward declaration for top-level expressions
     * Points to the lowest precedence operation (arrow functions)
     */
    private val expression: Parser<Expression> by parser(this::arrowExpr)

    /**
     * Object literal expression
     * Syntax: { key: value, key2: value2 }
     *
     * Supports:
     * - Empty objects: {}
     * - Identifier keys: { x: 10, y: 20 }
     * - String keys: { "name": "Alice" }
     * - Trailing commas: { a: 1, b: 2, }
     *
     * Examples:
     * - {}
     * - { x: 10 }
     * - { a: 1, b: 2 }
     * - { "first-name": "John" }
     */
    private val objectLiteral: Parser<Expression> by
    (-leftBrace and separatedTerms(
        // Parse key-value pair: identifier or string (including backtick), then colon, then expression
        ((identifier map { it.text }) or
                (backtickString use { text.substring(1, text.length - 1) }) or
                (string use { text.substring(1, text.length - 1) })) and
                -colon and
                parser(this::expression),
        comma,
        acceptZero = true
    ) and -rightBrace).map { properties ->
        ObjectLiteral(properties.map { (key, value) -> key to value })
    }

    /**
     * Primary expressions - atomic building blocks
     * Numbers, strings, booleans, null, identifiers, object literals, or parenthesized expressions
     */
    private val primaryExpr: Parser<Expression> by
    (number use { NumberLiteral(text.toDouble()) }) or
            (trueKeyword use { BooleanLiteral(true) }) or
            (falseKeyword use { BooleanLiteral(false) }) or
            (nullKeyword use { NullLiteral }) or
            (backtickString use { StringLiteral(text.substring(1, text.length - 1)) }) or  // Strip backticks
            (string use { StringLiteral(text.substring(1, text.length - 1)) }) or  // Strip quotes
            objectLiteral or
            (identifier use { Identifier(text) }) or
            (-leftParen * parser(this::expression) * -rightParen)  // Parenthesized expressions

    /**
     * Unary expressions - prefix operators
     * Syntax: -expr, +expr, !expr
     *
     * Unary operators have higher precedence than binary operators but lower than primary.
     * They bind tightly to their operand:
     * - -5 + 3 parses as (-5) + 3, not -(5 + 3)
     * - !flag parses as a single unary operation
     *
     * Supported operators:
     * - `-` : Arithmetic negation (flip sign)
     * - `+` : Arithmetic identity (no-op, for clarity)
     * - `!` : Logical NOT (boolean negation)
     */
    private val unaryExpr: Parser<Expression> by
    ((minus use { UnaryOperator.NEGATE }) or
            (plus use { UnaryOperator.PLUS }) or
            (exclamation use { UnaryOperator.NOT }) and
            parser(this::unaryExpr)).map { (op, operand) ->
        UnaryOperation(op, operand)
    } or primaryExpr

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
     * Implementation: start with unary expression, then parse zero or more
     * ".property" sequences, building nested MemberAccess nodes.
     */
    private val memberExpr: Parser<Expression> by
    (unaryExpr and zeroOrMore(-dot and identifier)).map { (base, properties) ->
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
     * Let declaration statement
     * Syntax: let x = expr OR let x
     *
     * Examples:
     * - let count = 0
     * - let name = "Alice"
     * - let uninitialized
     */
    private val letDeclaration: Parser<Statement> by
    (-letKeyword and identifier and optional(-equals and expression)).map { (name, initOpt) ->
        LetDeclaration(name.text, initOpt)
    }

    /**
     * Const declaration statement
     * Syntax: const x = expr
     *
     * Note: Const requires an initializer
     *
     * Examples:
     * - const MAX_SIZE = 100
     * - const PI = 3.14159
     */
    private val constDeclaration: Parser<Statement> by
    (-constKeyword and identifier and -equals and expression).map { (name, init) ->
        ConstDeclaration(name.text, init)
    }

    /**
     * Export statement
     * Syntax: export { name1, name2, ... }
     *
     * Marks symbols for export from a library. Only exported symbols
     * are accessible when the library is imported.
     *
     * Examples:
     * - export { add, multiply }
     * - export { note, sound, sine }
     */
    private val exportStatement: Parser<Statement> by
    (-exportKeyword and -leftBrace and separatedTerms(
        identifier,
        comma,
        acceptZero = false
    ) and -rightBrace).map { identifiers ->
        ExportStatement(identifiers.map { it.text })
    }

    /**
     * Wildcard import: import * from "lib"
     */
    private val wildcardImport: Parser<Statement> by
    (-importKeyword and -times and -fromKeyword and string).map { libraryNameToken ->
        val libraryName = libraryNameToken.text.substring(1, libraryNameToken.text.length - 1)
        ImportStatement(libraryName, null)  // null means wildcard
    }

    /**
     * Import specifier: either "name" or "name as alias"
     * Returns Pair(exportName, localAlias)
     */
    private val importSpecifier: Parser<Pair<String, String>> by
    (identifier and optional(-asKeyword and identifier)).map { (name, aliasOpt) ->
        val exportName = name.text
        val localAlias = aliasOpt?.text ?: exportName
        Pair(exportName, localAlias)
    }

    /**
     * Selective import: import { name1, name2 as alias2 } from "lib"
     */
    private val selectiveImport: Parser<Statement> by
    (-importKeyword and -leftBrace and separatedTerms(
        importSpecifier,
        comma,
        acceptZero = false
    ) and -rightBrace and -fromKeyword and string).map { (specifiers, libraryNameToken) ->
        val libraryName = libraryNameToken.text.substring(1, libraryNameToken.text.length - 1)
        ImportStatement(libraryName, specifiers)
    }

    /**
     * Import statement - wildcard or selective
     *
     * Syntax:
     * - import * from "libraryName" - Import all exports
     * - import { name1, name2 } from "libraryName" - Import specific exports
     *
     * Examples:
     * - import * from "strudel.klang"
     * - import { add, multiply } from "math"
     */
    private val importStatement: Parser<Statement> by
    wildcardImport or selectiveImport

    /**
     * Expression statements
     * Expressions used as statements: print("hello")
     */
    private val expressionStatement: Parser<Statement> by
    expression.map { ExpressionStatement(it) }

    /**
     * Statements
     * Supports:
     * - Import statements
     * - Export statements
     * - Variable declarations (let, const)
     * - Expression statements
     */
    private val statement: Parser<Statement> by
    importStatement or exportStatement or letDeclaration or constDeclaration or expressionStatement

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
