package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.parser.KlangScriptParser

class DebugProgramTest : StringSpec({

    "debug parsing issue" {
        // Test just the method chaining line
        val simpleSource = """note("c").gain(0.5)"""
        val simpleResult = KlangScriptParser.parse(simpleSource)
        println("Simple test: ${simpleResult.statements.size} statements")

        // Now test it in context
        val source = """
            add(1, 2, 3)

            // Method chaining
            note("c").gain(0.5)

            // Arrow functions
            let double = x => x * 2
        """.trimIndent()

        val result = KlangScriptParser.parse(source)

        println("\nContext test: ${result.statements.size} statements")
        result.statements.forEachIndexed { index, stmt ->
            println("$index: ${stmt::class.simpleName}")
        }
    }
})
