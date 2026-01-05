package io.peekandpoke.klang.strudel.lang.parser

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.strudel.StrudelPattern

class StrudelCodeParseEvaluateSpec : StringSpec({

    val evaluator = StrudelExpressionEvaluator()

    fun eval(code: String): StrudelPattern {
        val ast = StrudelCodeGrammar.parseToEnd(code)
        val result = evaluator.evaluate(ast)
        result.shouldBeInstanceOf<StrudelPattern>()
        return result
    }

    "Full Integration: note().fast().gain()" {
        // This tests chaining, simple args, and method registry
        val p = eval(""" note("c3").fast(2).gain(0.5) """)

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2 // fast(2) = 2 events per cycle
        events[0].data.gain shouldBe 0.5
    }

    "Full Integration: stack(note, sound)" {
        // This tests function registry (stack) and nested calls
        val code = """
            stack(
                note("a3"),
                sound("bd")
            )
        """
        val p = eval(code)

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
    }

    "Full Integration: n() with numbers" {
        val p = eval(""" n("0 2").scale("C:major") """)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.note?.toDouble() shouldBe 0.0
        events[0].data.scale shouldBe "C:major"
    }

    "Full Integration: Mini-Notation with backticks" {
        // Tests the grammar's ability to handle backticks
        val code = """
            note(`
                [c3 e3]
                [g3 b3]
            `).slow(2)
        """
        val p = eval(code)
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
    }
})
