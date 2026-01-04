package io.peekandpoke.klang.strudel.lang.parser

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class StrudelCodeParserSpec : StringSpec({

    "Parses a simple function call" {
        val input = "note(\"c3\")"
        val ast = StrudelCodeGrammar.parseToEnd(input)

        ast.shouldBeInstanceOf<FunCallNode>()
        ast.name shouldBe "note"
        ast.args.size shouldBe 1
        (ast.args[0] as LiteralNode).value shouldBe "c3"
    }

    "Parses chained calls" {
        val input = "note(\"c3\").fast(2)"
        val ast = StrudelCodeGrammar.parseToEnd(input)

        // Structure: Chain(Chain(note), fast) ? No, Chain(note, fast)
        ast.shouldBeInstanceOf<ChainNode>()
        ast.methodCall.name shouldBe "fast"

        // Receiver should be the note() call
        ast.receiver.shouldBeInstanceOf<FunCallNode>()
        ast.receiver.name shouldBe "note"
    }

    "Parses nested stack with backticks and formatting" {
        val input = """
            stack(
                note(`c3`).fast(1),
                sound("bd").gain(0.5)
            )
        """.trimIndent()

        val ast = StrudelCodeGrammar.parseToEnd(input)

        ast.shouldBeInstanceOf<FunCallNode>()
        ast.name shouldBe "stack"
        ast.args.size shouldBe 2

        // First arg: note(...).fast(...)
        ast.args[0].shouldBeInstanceOf<ChainNode>()

        // Second arg: sound(...).gain(...)
        ast.args[1].shouldBeInstanceOf<ChainNode>()
    }
})
