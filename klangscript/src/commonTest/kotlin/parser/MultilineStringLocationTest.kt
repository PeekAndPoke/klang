package io.peekandpoke.klang.script.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.ast.LetDeclaration
import io.peekandpoke.klang.script.ast.StringLiteral

class MultilineStringLocationTest : StringSpec({

    "multiline backtick string should track location across lines" {
        val code = "let x = `line 1\nline 2\nline 3`"
        val program = KlangScriptParser.parse(code, "test.klang")

        val letDecl = program.statements[0] as LetDeclaration
        val stringLiteral = letDecl.initializer as StringLiteral

        stringLiteral.location shouldNotBe null
        val loc = stringLiteral.location!!

        // Should start at line 1
        loc.startLine shouldBe 1
        loc.startColumn shouldBe 9  // After "let x = "

        // Should end at line 3
        loc.endLine shouldBe 3
    }
})
