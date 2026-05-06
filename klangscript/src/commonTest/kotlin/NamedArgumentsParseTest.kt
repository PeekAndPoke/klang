package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.Argument
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.AssignmentExpression
import io.peekandpoke.klang.script.ast.BinaryOperation
import io.peekandpoke.klang.script.ast.BinaryOperator
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.ast.NumberLiteral
import io.peekandpoke.klang.script.ast.StringLiteral
import io.peekandpoke.klang.script.parser.KlangScriptParser

/**
 * Phase 2 — parser-only coverage for named-argument syntax.
 *
 * The interpreter still runs with positional semantics in Phase 2, so these
 * tests only inspect the parse tree. Runtime semantics (mixing, binding to
 * FunctionValue parameters, native dispatch) land in Phase 3 with their own
 * test file.
 */
class NamedArgumentsParseTest : StringSpec({

    fun callOf(source: String): CallExpression {
        val program = KlangScriptParser.parse(source)
        val stmt = program.statements.single()
        stmt.shouldBeInstanceOf<ExpressionStatement>()
        val expr = stmt.expression
        expr.shouldBeInstanceOf<CallExpression>()
        return expr
    }

    "named arg: foo(a = 1)" {
        val call = callOf("foo(a = 1)")
        call.arguments.size shouldBe 1

        val arg = call.arguments[0]
        arg.shouldBeInstanceOf<Argument.Named>()
        arg.name shouldBe "a"
        val value = arg.value
        value.shouldBeInstanceOf<NumberLiteral>()
        value.value shouldBe 1.0
    }

    "all-named: foo(a = 1, b = 2)" {
        val call = callOf("foo(a = 1, b = 2)")
        call.arguments.size shouldBe 2

        val a = call.arguments[0]
        a.shouldBeInstanceOf<Argument.Named>()
        a.name shouldBe "a"

        val b = call.arguments[1]
        b.shouldBeInstanceOf<Argument.Named>()
        b.name shouldBe "b"
    }

    "named value is an arbitrary expression: foo(a = b + c)" {
        val call = callOf("foo(a = b + c)")
        val arg = call.arguments.single()
        arg.shouldBeInstanceOf<Argument.Named>()
        arg.name shouldBe "a"

        val value = arg.value
        value.shouldBeInstanceOf<BinaryOperation>()
        value.operator shouldBe BinaryOperator.ADD
    }

    "named value is an arrow function: foo(a = (b, c) => b + c)" {
        val call = callOf("foo(a = (b, c) => b + c)")
        val arg = call.arguments.single()
        arg.shouldBeInstanceOf<Argument.Named>()
        arg.value.shouldBeInstanceOf<ArrowFunction>()
    }

    "comparison is NOT a named arg: foo(x == y)" {
        val call = callOf("foo(x == y)")
        val arg = call.arguments.single()
        arg.shouldBeInstanceOf<Argument.Positional>()
        val value = arg.value
        value.shouldBeInstanceOf<BinaryOperation>()
        value.operator shouldBe BinaryOperator.EQUAL
    }

    "strict comparison is NOT a named arg: foo(x === y)" {
        val call = callOf("foo(x === y)")
        val arg = call.arguments.single()
        arg.shouldBeInstanceOf<Argument.Positional>()
        val value = arg.value
        value.shouldBeInstanceOf<BinaryOperation>()
        value.operator shouldBe BinaryOperator.STRICT_EQUAL
    }

    "compound-assign form is NOT a named arg: foo(x += 1)" {
        // Parenthesized so the whole AssignmentExpression is visible; bare x += 1
        // would also reach parseAssignment inside parseExpression.
        val call = callOf("foo(x += 1)")
        val arg = call.arguments.single()
        arg.shouldBeInstanceOf<Argument.Positional>()
        arg.value.shouldBeInstanceOf<AssignmentExpression>()
    }

    "parenthesised assignment is the escape hatch: foo((a = 1))" {
        val call = callOf("foo((a = 1))")
        val arg = call.arguments.single()
        arg.shouldBeInstanceOf<Argument.Positional>()

        val value = arg.value
        value.shouldBeInstanceOf<AssignmentExpression>()
        (value.target as Identifier).name shouldBe "a"
        (value.value as NumberLiteral).value shouldBe 1.0
    }

    "mixing positional and named parses cleanly: foo(1, a = 2)" {
        // Interpreter-level rejection of mixing lives in Phase 3.
        // The parser accepts it so the analyzer can emit a friendly diagnostic later.
        val call = callOf("foo(1, a = 2)")
        call.arguments.size shouldBe 2

        call.arguments[0].shouldBeInstanceOf<Argument.Positional>()
        val named = call.arguments[1]
        named.shouldBeInstanceOf<Argument.Named>()
        named.name shouldBe "a"
    }

    "mixing named-first also parses: foo(a = 1, 2)" {
        val call = callOf("foo(a = 1, 2)")
        call.arguments.size shouldBe 2

        call.arguments[0].shouldBeInstanceOf<Argument.Named>()
        call.arguments[1].shouldBeInstanceOf<Argument.Positional>()
    }

    "string-valued named arg: foo(label = \"hi\")" {
        val call = callOf("foo(label = \"hi\")")
        val arg = call.arguments.single()
        arg.shouldBeInstanceOf<Argument.Named>()
        arg.name shouldBe "label"
        (arg.value as StringLiteral).value shouldBe "hi"
    }

    "named arg tracks source location on the name token" {
        val call = callOf("foo(theParam = 42)")
        val arg = call.arguments.single()
        arg.shouldBeInstanceOf<Argument.Named>()

        val loc = arg.nameLocation
        loc?.startLine shouldBe 1
        // column is 1-based; "foo(" takes 4 chars, so "theParam" starts at column 5
        loc?.startColumn shouldBe 5
    }
})
