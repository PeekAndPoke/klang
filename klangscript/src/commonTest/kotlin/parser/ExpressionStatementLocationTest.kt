package io.peekandpoke.klang.script.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.ast.ExpressionStatement

class ExpressionStatementLocationTest : StringSpec({

    "expression statement should carry source location" {
        val code = "foo(1)"
        val program = KlangScriptParser.parse(code, "test.klang")

        val stmt = program.statements[0] as ExpressionStatement
        stmt.location shouldNotBe null
        stmt.location!!.startLine shouldBe 1
    }

    "expression statement on line 3 should report correct start line" {
        val code = "a()\n\nb()"
        val program = KlangScriptParser.parse(code, "test.klang")

        val first = program.statements[0] as ExpressionStatement
        val second = program.statements[1] as ExpressionStatement

        first.location!!.startLine shouldBe 1
        second.location!!.startLine shouldBe 3
    }

    "blank lines between expression statements are detectable via location gap" {
        // Two blank lines between a() and b() — gap > 1 means blank lines exist
        val code = "a()\n\n\nb()"
        val program = KlangScriptParser.parse(code, "test.klang")

        val first = program.statements[0] as ExpressionStatement
        val second = program.statements[1] as ExpressionStatement

        val gap = second.location!!.startLine - first.location!!.endLine
        (gap > 1) shouldBe true
    }
})
