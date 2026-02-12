package io.peekandpoke.klang.script.parser

import com.github.h0tk3y.betterParse.parser.ParseException
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.ast.*

/**
 * Tests for parsing boolean logic operators (&& and ||)
 *
 * Covers:
 * - Parser accepts && and || tokens
 * - AST structure is correct
 * - Precedence is reflected in AST
 * - Complex expressions parse correctly
 */
class BooleanOperatorParsingTest : StringSpec({

    // ============================================================
    // Basic Parsing Tests
    // ============================================================

    "parser accepts && operator" {
        shouldNotThrow<ParseException> {
            KlangScriptParser.parse("true && false", "test.klang")
        }
    }

    "parser accepts || operator" {
        shouldNotThrow<ParseException> {
            KlangScriptParser.parse("true || false", "test.klang")
        }
    }

    "parser accepts both && and || in same expression" {
        shouldNotThrow<ParseException> {
            KlangScriptParser.parse("true && false || true", "test.klang")
        }
    }

    "parser accepts complex boolean expressions" {
        shouldNotThrow<ParseException> {
            KlangScriptParser.parse("a && b || c && d", "test.klang")
        }
    }

    "parser accepts chained AND operations" {
        shouldNotThrow<ParseException> {
            KlangScriptParser.parse("a && b && c && d", "test.klang")
        }
    }

    "parser accepts chained OR operations" {
        shouldNotThrow<ParseException> {
            KlangScriptParser.parse("a || b || c || d", "test.klang")
        }
    }

    // ============================================================
    // AST Structure Tests
    // ============================================================

    "parser creates correct AST structure for AND" {
        val ast = KlangScriptParser.parse("a && b", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        expr.operator shouldBe BinaryOperator.AND
        (expr.left as Identifier).name shouldBe "a"
        (expr.right as Identifier).name shouldBe "b"
    }

    "parser creates correct AST structure for OR" {
        val ast = KlangScriptParser.parse("a || b", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        expr.operator shouldBe BinaryOperator.OR
        (expr.left as Identifier).name shouldBe "a"
        (expr.right as Identifier).name shouldBe "b"
    }

    "parser creates correct AST for chained AND: a && b && c" {
        val ast = KlangScriptParser.parse("a && b && c", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should be left-associative: (a && b) && c
        expr.operator shouldBe BinaryOperator.AND
        (expr.right as Identifier).name shouldBe "c"

        val leftBinary = expr.left as BinaryOperation
        leftBinary.operator shouldBe BinaryOperator.AND
        (leftBinary.left as Identifier).name shouldBe "a"
        (leftBinary.right as Identifier).name shouldBe "b"
    }

    "parser creates correct AST for chained OR: a || b || c" {
        val ast = KlangScriptParser.parse("a || b || c", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should be left-associative: (a || b) || c
        expr.operator shouldBe BinaryOperator.OR
        (expr.right as Identifier).name shouldBe "c"

        val leftBinary = expr.left as BinaryOperation
        leftBinary.operator shouldBe BinaryOperator.OR
        (leftBinary.left as Identifier).name shouldBe "a"
        (leftBinary.right as Identifier).name shouldBe "b"
    }

    // ============================================================
    // Precedence in AST
    // ============================================================

    "AST reflects AND higher precedence than OR: a || b && c" {
        val ast = KlangScriptParser.parse("a || b && c", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should parse as: a || (b && c)
        expr.operator shouldBe BinaryOperator.OR
        (expr.left as Identifier).name shouldBe "a"

        val rightBinary = expr.right as BinaryOperation
        rightBinary.operator shouldBe BinaryOperator.AND
        (rightBinary.left as Identifier).name shouldBe "b"
        (rightBinary.right as Identifier).name shouldBe "c"
    }

    "AST reflects AND higher precedence than OR: a && b || c" {
        val ast = KlangScriptParser.parse("a && b || c", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should parse as: (a && b) || c
        expr.operator shouldBe BinaryOperator.OR
        (expr.right as Identifier).name shouldBe "c"

        val leftBinary = expr.left as BinaryOperation
        leftBinary.operator shouldBe BinaryOperator.AND
        (leftBinary.left as Identifier).name shouldBe "a"
        (leftBinary.right as Identifier).name shouldBe "b"
    }

    "AST reflects comparison higher precedence than AND: a > b && c < d" {
        val ast = KlangScriptParser.parse("a > b && c < d", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should parse as: (a > b) && (c < d)
        expr.operator shouldBe BinaryOperator.AND

        val leftComp = expr.left as BinaryOperation
        leftComp.operator shouldBe BinaryOperator.GREATER_THAN
        (leftComp.left as Identifier).name shouldBe "a"
        (leftComp.right as Identifier).name shouldBe "b"

        val rightComp = expr.right as BinaryOperation
        rightComp.operator shouldBe BinaryOperator.LESS_THAN
        (rightComp.left as Identifier).name shouldBe "c"
        (rightComp.right as Identifier).name shouldBe "d"
    }

    "AST reflects arithmetic and comparison higher precedence: a + b > c && d" {
        val ast = KlangScriptParser.parse("a + b > c && d", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should parse as: ((a + b) > c) && d
        expr.operator shouldBe BinaryOperator.AND
        (expr.right as Identifier).name shouldBe "d"

        val leftComp = expr.left as BinaryOperation
        leftComp.operator shouldBe BinaryOperator.GREATER_THAN
        (leftComp.right as Identifier).name shouldBe "c"

        val leftAdd = leftComp.left as BinaryOperation
        leftAdd.operator shouldBe BinaryOperator.ADD
        (leftAdd.left as Identifier).name shouldBe "a"
        (leftAdd.right as Identifier).name shouldBe "b"
    }

    // ============================================================
    // Parentheses Tests
    // ============================================================

    "parser respects parentheses: (a || b) && c" {
        val ast = KlangScriptParser.parse("(a || b) && c", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Parentheses force: (a || b) && c
        expr.operator shouldBe BinaryOperator.AND
        (expr.right as Identifier).name shouldBe "c"

        val leftBinary = expr.left as BinaryOperation
        leftBinary.operator shouldBe BinaryOperator.OR
        (leftBinary.left as Identifier).name shouldBe "a"
        (leftBinary.right as Identifier).name shouldBe "b"
    }

    "parser respects nested parentheses: ((a || b) && c) || d" {
        val ast = KlangScriptParser.parse("((a || b) && c) || d", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Outer level: ... || d
        expr.operator shouldBe BinaryOperator.OR
        (expr.right as Identifier).name shouldBe "d"

        // Inner level: (a || b) && c
        val leftBinary = expr.left as BinaryOperation
        leftBinary.operator shouldBe BinaryOperator.AND
        (leftBinary.right as Identifier).name shouldBe "c"

        // Innermost: a || b
        val innerBinary = leftBinary.left as BinaryOperation
        innerBinary.operator shouldBe BinaryOperator.OR
        (innerBinary.left as Identifier).name shouldBe "a"
        (innerBinary.right as Identifier).name shouldBe "b"
    }

    // ============================================================
    // With Literals
    // ============================================================

    "parser handles boolean literals with &&" {
        val ast = KlangScriptParser.parse("true && false", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        expr.operator shouldBe BinaryOperator.AND
        (expr.left as BooleanLiteral).value shouldBe true
        (expr.right as BooleanLiteral).value shouldBe false
    }

    "parser handles boolean literals with ||" {
        val ast = KlangScriptParser.parse("true || false", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        expr.operator shouldBe BinaryOperator.OR
        (expr.left as BooleanLiteral).value shouldBe true
        (expr.right as BooleanLiteral).value shouldBe false
    }

    "parser handles number literals in boolean context" {
        val ast = KlangScriptParser.parse("5 && 10", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        expr.operator shouldBe BinaryOperator.AND
        (expr.left as NumberLiteral).value shouldBe 5.0
        (expr.right as NumberLiteral).value shouldBe 10.0
    }

    // ============================================================
    // With NOT Operator
    // ============================================================

    "parser handles NOT with AND: !a && b" {
        val ast = KlangScriptParser.parse("!a && b", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should parse as: (!a) && b
        expr.operator shouldBe BinaryOperator.AND
        (expr.right as Identifier).name shouldBe "b"

        val leftUnary = expr.left as UnaryOperation
        leftUnary.operator shouldBe UnaryOperator.NOT
        (leftUnary.operand as Identifier).name shouldBe "a"
    }

    "parser handles NOT with OR: !a || b" {
        val ast = KlangScriptParser.parse("!a || b", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should parse as: (!a) || b
        expr.operator shouldBe BinaryOperator.OR
        (expr.right as Identifier).name shouldBe "b"

        val leftUnary = expr.left as UnaryOperation
        leftUnary.operator shouldBe UnaryOperator.NOT
        (leftUnary.operand as Identifier).name shouldBe "a"
    }

    "parser handles double NOT with AND: !!a && b" {
        val ast = KlangScriptParser.parse("!!a && b", "test.klang")
        val stmt = ast.statements[0] as ExpressionStatement
        val expr = stmt.expression as BinaryOperation

        // Should parse as: (!!a) && b
        expr.operator shouldBe BinaryOperator.AND
        (expr.right as Identifier).name shouldBe "b"

        val outerNot = expr.left as UnaryOperation
        outerNot.operator shouldBe UnaryOperator.NOT

        val innerNot = outerNot.operand as UnaryOperation
        innerNot.operator shouldBe UnaryOperator.NOT
        (innerNot.operand as Identifier).name shouldBe "a"
    }
})
