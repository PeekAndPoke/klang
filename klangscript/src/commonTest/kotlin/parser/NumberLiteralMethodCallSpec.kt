/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.parser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.Argument
import io.peekandpoke.klang.script.ast.BinaryOperation
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.ast.NumberLiteral
import io.peekandpoke.klang.script.ast.UnaryOperation
import io.peekandpoke.klang.script.ast.UnaryOperator
import io.peekandpoke.klang.script.klangScriptEngine
import io.peekandpoke.klang.script.runtime.KlangScriptSyntaxError
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Methods on number literals: `2.pow(7/12)`, `-1.0.clamp(0, 1)`.
 *
 * Two parser-level pieces make the spelling work (`docs/tasks/klangscript-number-methods.md`):
 *
 * 1. The lexer takes a `.` into a number only when a digit follows it. Before, the scanner ate every dot,
 *    `2.pow` lexed as `2.` `pow`, and no method was reachable on a literal (the statement boundary rule
 *    then reported it as two statements).
 * 2. A minus sign in front of a number literal is part of the number, so `-1.0.clamp(0, 1)` is
 *    `(-1.0).clamp(0, 1)`. Kotlin and JS would read it as `-(1.0.clamp(0, 1))`, a plausible wrong number
 *    with no diagnostic; the maintainer chose the literal reading (2026-09-08). Only a literal folds.
 *
 * The methods themselves are stdlib work in `klangscript-libs`; this spec pins the AST shape and the
 * literals that must keep lexing as one token.
 */
class NumberLiteralMethodCallSpec : StringSpec({

    fun parse(code: String) = KlangScriptParser.parse(code, "test.klang")

    fun topExpr(code: String): Expression {
        val program = parse(code)
        program.statements.size shouldBe 1

        return program.statements.single().shouldBeInstanceOf<ExpressionStatement>().expression
    }

    fun evalNumber(code: String): Double =
        klangScriptEngine().execute(code).shouldBeInstanceOf<NumberValue>().value

    /** `receiver.name(args)` with a number literal receiver: returns (receiver value, name, positional args). */
    fun methodCallOnLiteral(code: String): Triple<Double, String, List<Expression>> {
        val call = topExpr(code).shouldBeInstanceOf<CallExpression>()
        val member = call.callee.shouldBeInstanceOf<MemberAccess>()
        val receiver = member.obj.shouldBeInstanceOf<NumberLiteral>()
        val args = call.arguments.map { it.shouldBeInstanceOf<Argument.Positional>().value }

        return Triple(receiver.value, member.property, args)
    }

    // -- The reason for the change: a method on a number literal ------------------------------------------------------

    "2.pow(7/12) is one expression: a call on the literal 2" {
        val (receiver, name, args) = methodCallOnLiteral("2.pow(7/12)")

        receiver shouldBe 2.0
        name shouldBe "pow"
        args.single().shouldBeInstanceOf<BinaryOperation>()
    }

    "the boundary diagnostic for 2.pow(...) is gone" {
        // Before the lexer change this was "Expected a newline or ';' between statements. Did you mean
        // '.pow(...)'?": correct for the token stream of the day, wrong for what the user wrote.
        val program = parse("2.pow(2)")

        program.statements.size shouldBe 1
    }

    "2.5.pow(2): the dot before a digit still belongs to the number" {
        val (receiver, name, args) = methodCallOnLiteral("2.5.pow(2)")

        receiver shouldBe 2.5
        name shouldBe "pow"
        args.single().shouldBeInstanceOf<NumberLiteral>().value shouldBe 2.0
    }

    "0.5.toString(): the leading-zero scanner takes the same rule" {
        val (receiver, name, _) = methodCallOnLiteral("0.5.toString()")

        receiver shouldBe 0.5
        name shouldBe "toString"
    }

    "2.toString() parses as a call on the literal" {
        val (receiver, name, args) = methodCallOnLiteral("2.toString()")

        receiver shouldBe 2.0
        name shouldBe "toString"
        args shouldBe emptyList()
    }

    "a chain keeps going: 2.pow(2).toString()" {
        val outer = topExpr("2.pow(2).toString()").shouldBeInstanceOf<CallExpression>()
        val outerMember = outer.callee.shouldBeInstanceOf<MemberAccess>()
        outerMember.property shouldBe "toString"

        val inner = outerMember.obj.shouldBeInstanceOf<CallExpression>()
        inner.callee.shouldBeInstanceOf<MemberAccess>().property shouldBe "pow"
    }

    "a member call on a literal inside an arithmetic expression: 440 * 2.pow(-9/12)" {
        val product = topExpr("440 * 2.pow(-9/12)").shouldBeInstanceOf<BinaryOperation>()

        product.left.shouldBeInstanceOf<NumberLiteral>().value shouldBe 440.0
        val call = product.right.shouldBeInstanceOf<CallExpression>()
        call.callee.shouldBeInstanceOf<MemberAccess>().obj.shouldBeInstanceOf<NumberLiteral>().value shouldBe 2.0
    }

    // -- Negative literals: the minus is part of the number ---------------------------------------------------------

    "-1.0.clamp(0, 1) calls clamp on -1.0, not on 1.0" {
        val (receiver, name, args) = methodCallOnLiteral("-1.0.clamp(0, 1)")

        receiver shouldBe -1.0
        name shouldBe "clamp"
        args.size shouldBe 2
    }

    "-7.semitones() calls semitones on -7" {
        val (receiver, name, _) = methodCallOnLiteral("-7.semitones()")

        receiver shouldBe -7.0
        name shouldBe "semitones"
    }

    "the fold survives whitespace: - 7.abs()" {
        val (receiver, name, _) = methodCallOnLiteral("- 7.abs()")

        receiver shouldBe -7.0
        name shouldBe "abs"
    }

    "a bare negative number is a single literal, not a unary operation" {
        topExpr("-42").shouldBeInstanceOf<NumberLiteral>().value shouldBe -42.0
        topExpr("-2.5e-3").shouldBeInstanceOf<NumberLiteral>().value shouldBe -0.0025
        topExpr("-0xFF").shouldBeInstanceOf<NumberLiteral>().value shouldBe -255.0
    }

    "the folded literal's location spans the minus and the number" {
        val literal = topExpr("  -42").shouldBeInstanceOf<NumberLiteral>()

        literal.location?.startColumn shouldBe 3
        literal.location?.endColumn shouldBe 6
    }

    "only a literal folds: -x.abs() stays -(x.abs())" {
        val negate = topExpr("-x.abs()").shouldBeInstanceOf<UnaryOperation>()

        negate.operator shouldBe UnaryOperator.NEGATE
        val call = negate.operand.shouldBeInstanceOf<CallExpression>()
        call.callee.shouldBeInstanceOf<MemberAccess>().obj.shouldBeInstanceOf<Identifier>().name shouldBe "x"
    }

    "a parenthesised literal is not a literal: -(7).abs() stays -(7.abs())" {
        val negate = topExpr("-(7).abs()").shouldBeInstanceOf<UnaryOperation>()

        negate.operator shouldBe UnaryOperator.NEGATE
        negate.operand.shouldBeInstanceOf<CallExpression>()
    }

    "a binary minus never folds: a -1 and 3 -1.abs()" {
        val difference = topExpr("a -1").shouldBeInstanceOf<BinaryOperation>()
        difference.left.shouldBeInstanceOf<Identifier>().name shouldBe "a"
        difference.right.shouldBeInstanceOf<NumberLiteral>().value shouldBe 1.0

        // 3 - (1.abs()): the minus is binary, the call binds to the positive literal
        val second = topExpr("3 -1.abs()").shouldBeInstanceOf<BinaryOperation>()
        second.right.shouldBeInstanceOf<CallExpression>()
            .callee.shouldBeInstanceOf<MemberAccess>()
            .obj.shouldBeInstanceOf<NumberLiteral>().value shouldBe 1.0
    }

    "a double minus still negates twice: --10 and - -10" {
        evalNumber("--10") shouldBe 10.0
        evalNumber("- -10") shouldBe 10.0
        evalNumber("-(-10)") shouldBe 10.0
    }

    "the second minus of -- folds like a lone minus: --1.abs() and - -1.abs() have the same shape" {
        listOf("--1.abs()", "- -1.abs()").forEach { code ->
            withClue(code) {
                val negate = topExpr(code).shouldBeInstanceOf<UnaryOperation>()
                negate.operator shouldBe UnaryOperator.NEGATE

                val call = negate.operand.shouldBeInstanceOf<CallExpression>()
                call.callee.shouldBeInstanceOf<MemberAccess>().obj.shouldBeInstanceOf<NumberLiteral>().value shouldBe -1.0
            }
        }
    }

    "the literal folded out of -- starts behind the first minus" {
        val negate = topExpr("--42").shouldBeInstanceOf<UnaryOperation>()
        val literal = negate.operand.shouldBeInstanceOf<NumberLiteral>()

        literal.value shouldBe -42.0
        literal.location?.startColumn shouldBe 2
        literal.location?.endColumn shouldBe 5
    }

    "arithmetic with negative literals is unchanged" {
        listOf(
            "-2 * 3" to -6.0,
            "-2 + 3" to 1.0,
            "2 - -3" to 5.0,
            "-2 - 3" to -5.0,
            "-1 < 0 ? 1 : 0" to 1.0,
            "let x = -4; x * x" to 16.0,
            "let a = [-1, -2]; a[1]" to -2.0,
            "f(-1)" to -1.0,
        ).forEach { (code, expected) ->
            withClue(code) {
                val program = if (code.startsWith("f(")) "let f = v => v; $code" else code
                evalNumber(program) shouldBe expected
            }
        }
    }

    // -- Regression guard: every number spelling still lexes as one token ---------------------------------------------

    "number literals still lex as a single token" {
        listOf(
            "2.5" to 2.5,
            "10.25" to 10.25,
            "0.5" to 0.5,
            "0.125" to 0.125,
            "1e3" to 1000.0,
            "1E3" to 1000.0,
            "1.5e-3" to 0.0015,
            "2.5e+2" to 250.0,
            "0.5e2" to 50.0,
            "2.e5" to 200000.0,
            "2.E-2" to 0.02,
            "0.e1" to 0.0,
            "0xFF" to 255.0,
            "0o17" to 15.0,
            "0b101" to 5.0,
            "007" to 7.0,
        ).forEach { (code, expected) ->
            withClue(code) {
                topExpr(code).shouldBeInstanceOf<NumberLiteral>().value shouldBe (expected plusOrMinus 1e-12)
            }
        }
    }

    "an exponent needs a digit: 2.exp() and 2.e are member accesses, not numbers" {
        val (receiver, name, _) = methodCallOnLiteral("2.exp()")
        receiver shouldBe 2.0
        name shouldBe "exp"

        val member = topExpr("2.e").shouldBeInstanceOf<MemberAccess>()
        member.obj.shouldBeInstanceOf<NumberLiteral>().value shouldBe 2.0
        member.property shouldBe "e"

        // `2.e+x` is 2.e plus x: the sign alone does not make an exponent
        topExpr("2.e+x").shouldBeInstanceOf<BinaryOperation>()
    }

    "an incomplete exponent is not part of the number: 2e and 1.5e+ are a number and an identifier" {
        // Before: the token `2e` reached toDouble() and threw a NumberFormatException with no location
        listOf("2e", "1.5e+", "0e").forEach { code ->
            withClue(code) {
                val error = shouldThrow<KlangScriptSyntaxError> { parse(code) }
                error.message shouldContain "between statements"
            }
        }
    }

    "the column keeps counting behind a leading-zero literal: f(0.5, 1)" {
        // Both scanner call sites advance the column by the literal's length; this row guards the
        // leading-zero one, LocationTrackingTest guards the other
        val call = topExpr("f(0.125, 1)").shouldBeInstanceOf<CallExpression>()
        val second = call.arguments[1].value.shouldBeInstanceOf<NumberLiteral>()

        second.location?.startColumn shouldBe 10
    }

    "a trailing dot is a stray dot, not a number: 2." {
        // Before: lexed as the number 2.0. Now the parser wants a property name after the dot.
        val error = shouldThrow<KlangScriptSyntaxError> { parse("2.") }

        error.message shouldContain "Expected property name after '.'"
    }

    "two dots in a number are a parse error, not a NumberFormatException: 1.2.3" {
        val error = shouldThrow<KlangScriptSyntaxError> { parse("1.2.3") }

        error.message shouldContain "Expected property name after '.'"
    }

    "the boundary rule still fires for a real dropped dot" {
        val error = shouldThrow<KlangScriptSyntaxError> { parse("""s("bd") tag("x")""") }

        error.message shouldContain "Did you mean '.tag(...)'?"
        error.message shouldNotContain "property name"
    }
})
