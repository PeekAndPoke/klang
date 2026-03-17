package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.ast.AstIndex
import io.peekandpoke.klang.script.parser.KlangScriptParser

class AstCallFinderTest : StringSpec({

    fun findAt(source: String, pos: Int): io.peekandpoke.klang.script.ast.CallExpressionAtResult? {
        val program = KlangScriptParser.parse(source)
        val index = AstIndex.build(program, source)
        return index.callArgAt(pos)
    }

    "simple call - cursor on first arg" {
        val result = findAt("foo(42)", 4).shouldNotBeNull()
        result.functionName shouldBe "foo"
        result.argIndex shouldBe 0
    }

    "simple call - cursor on second arg" {
        val result = findAt("foo(1, 2)", 7).shouldNotBeNull()
        result.functionName shouldBe "foo"
        result.argIndex shouldBe 1
    }

    "cursor on callee has negative argIndex" {
        val result = findAt("foo(1)", 1) // on 'o' in foo
        if (result != null) {
            result.argIndex shouldBe -1
        }
    }

    "nested call - cursor on inner arg" {
        val result = findAt("foo(bar(1), 2)", 8).shouldNotBeNull() // on '1' inside bar()
        result.functionName shouldBe "bar"
        result.argIndex shouldBe 0
    }

    "nested call - cursor on outer second arg" {
        val result = findAt("foo(bar(1), 2)", 12).shouldNotBeNull() // on '2'
        result.functionName shouldBe "foo"
        result.argIndex shouldBe 1
    }

    "method chain - cursor on method arg" {
        val src = "\"c3\".note().gain(0.5)"
        val pos = src.indexOf("0.5")
        val result = findAt(src, pos).shouldNotBeNull()
        result.functionName shouldBe "gain"
        result.argIndex shouldBe 0
    }

    "string arg with parens" {
        val src = "foo(\"hello (world)\")"
        val pos = src.indexOf("hello")
        val result = findAt(src, pos).shouldNotBeNull()
        result.functionName shouldBe "foo"
        result.argIndex shouldBe 0
    }

    "call with no args" {
        val result = findAt("foo()", 4)
        if (result != null) {
            result.functionName shouldBe "foo"
        }
    }

    "multiline call - first arg" {
        val src = "foo(\n  42,\n  \"hello\"\n)"
        val pos = src.indexOf("42")
        val result = findAt(src, pos).shouldNotBeNull()
        result.functionName shouldBe "foo"
        result.argIndex shouldBe 0
    }

    "multiline call - second arg" {
        val src = "foo(\n  42,\n  \"hello\"\n)"
        val pos = src.indexOf("hello")
        val result = findAt(src, pos).shouldNotBeNull()
        result.functionName shouldBe "foo"
        result.argIndex shouldBe 1
    }

    "expression as first arg" {
        val src = "foo(1 + 2, 3)"
        //         0123456789012
        // pos=4 is on '1' which is inside '1 + 2' which is arg[0] of foo
        // NumberLiteral(1) has location 1:5-6 -> offset 4..5
        // BinaryOperation(1+2) has location 1:5-10 -> offset 4..9
        // Both contain pos=4. The NumberLiteral at higher level should win as deepest node.
        // NumberLiteral.parent = BinaryOperation.parent = CallExpression(foo)
        val result = findAt(src, 4).shouldNotBeNull()
        result.functionName shouldBe "foo"
        result.argIndex shouldBe 0
    }

    "expression as second arg" {
        val result = findAt("foo(1 + 2, 3)", 11).shouldNotBeNull() // on '3'
        result.functionName shouldBe "foo"
        result.argIndex shouldBe 1
    }

    "method chain - second call with string arg" {
        val src = "sound(\"bd sd\").note(\"c3 e3\")"
        val pos = src.indexOf("c3 e3")
        val result = findAt(src, pos).shouldNotBeNull()
        result.functionName shouldBe "note"
        result.argIndex shouldBe 0
    }

    "method chain - first call with string arg" {
        val src = "sound(\"bd sd\").note(\"c3 e3\")"
        val pos = src.indexOf("bd sd")
        val result = findAt(src, pos).shouldNotBeNull()
        result.functionName shouldBe "sound"
        result.argIndex shouldBe 0
    }

    "cursor on receiver string walks up to outer call" {
        // .adsr("...".slow(16)) — cursor on the string should resolve to adsr, arg 0
        val src = "x.adsr(\"0.01:0.8\".slow(16))"
        val pos = src.indexOf("0.01")
        val result = findAt(src, pos).shouldNotBeNull()
        result.functionName shouldBe "adsr"
        result.argIndex shouldBe 0
    }
})
