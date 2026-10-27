/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** Reading a call from source text for the editor's tools (KLANG-00374): its parentheses, its arguments, a literal. */
class CallTextSpec : StringSpec({

    fun parens(source: String, at: String) = CallText.parentheses(source, source.indexOf(at))

    fun args(source: String, at: String): List<String> {
        val range = parens(source, at)!!

        return CallText.arguments(source, range).map { source.substring(it.from, it.to) }
    }

    "the closing parenthesis past nested calls, brackets, strings and comments" {
        val source = "lpf(f(1), [2, \")\"], ')' /* ) */ // )\n, 3).fast(2)"

        parens(source, "(f")!!.last shouldBe source.indexOf(").fast")
    }

    "a parenthesis that does not close, or no parenthesis at all: nothing" {
        parens("lpf(1, 2", "(").shouldBeNull()
        parens("lpf 1", " ").shouldBeNull()
    }

    "the arguments at the commas of their own level, trimmed; none in empty parentheses" {
        args("lpf(800, f(1, 2), \"a, b\" , 3 /* , */)", "(") shouldContainExactly listOf("800", "f(1, 2)", "\"a, b\"", "3 /* , */")
        args("gain(   )", "(") shouldContainExactly emptyList()
    }

    "a named argument: its name, and where its value starts; an arrow or a comparison is no name" {
        val source = "lpf(q = 4, freq=800, x => x, a == b)"
        val found = CallText.arguments(source, parens(source, "(")!!)

        found.map { it.name } shouldContainExactly listOf("q", "freq", null, null)
        source.substring(found[0].valueFrom, found[0].to) shouldBe "4"
        source.substring(found[1].valueFrom, found[1].to) shouldBe "800"
    }

    "a literal's end: a string with its escapes, a number, a negative number; none for a name" {
        CallText.literalEnd("\"a\\\"b\".fast(2)", 0) shouldBe 6
        CallText.literalEnd("0.25)", 0) shouldBe 4
        CallText.literalEnd("-3, 4", 0) shouldBe 2
        CallText.literalEnd("sine", 0).shouldBeNull()
        CallText.literalEnd("\"open", 0).shouldBeNull()
    }

    "a number as the lexer reads it: exponents, prefixes, a fraction only before a digit; none a name runs on from" {
        CallText.literalEnd("5e-1)", 0) shouldBe 4
        CallText.literalEnd("2.5E+3,", 0) shouldBe 6
        CallText.literalEnd("0x1F)", 0) shouldBe 4
        CallText.literalEnd("0b101)", 0) shouldBe 5
        CallText.literalEnd("2.pow(2)", 0) shouldBe 1
        CallText.literalEnd("- 0.5)", 0) shouldBe 5
        CallText.literalEnd("2px", 0).shouldBeNull()
        CallText.literalEnd("0x)", 0).shouldBeNull()
    }

    "the literal of a parameter: by its name, else by its place among the unnamed; only a literal or a chain's head" {
        fun literal(source: String, name: String, index: Int): String? =
            CallText.literalOf(source, parens(source, "(")!!, name, index)?.let { source.substring(it.first, it.last + 1) }

        literal("adsr(0.01, 0.3, 0.5)", name = "attack", index = 0) shouldBe "0.01"
        literal("adsr(decay = 0.3, attack = 0.01)", name = "attack", index = 0) shouldBe "0.01"
        literal("adsr(decay = 0.3)", name = "attack", index = 0).shouldBeNull()
        literal("gain(\"0.5 0.8\".fast(2))", name = "amount", index = 0) shouldBe "\"0.5 0.8\""
        literal("gain(sine.range(1, 2))", name = "amount", index = 0).shouldBeNull()
        literal("gain(-3 * 2)", name = "amount", index = 0).shouldBeNull()
        literal("gain((0.5))", name = "amount", index = 0).shouldBeNull()
        literal("gain(0.5 /* loud */)", name = "amount", index = 0) shouldBe "0.5"
    }

    "the last code before a place: past space and comments, a string counted, none at the start" {
        val source = "gain(0.5 // it's\n  /* x */ "

        CallText.lastCodeBefore(source, source.length) shouldBe source.indexOf("5")
        CallText.lastCodeBefore("f(\"a b\"  ", 8) shouldBe 6
        CallText.lastCodeBefore("f(\"a b", 5) shouldBe 4
        CallText.lastCodeBefore("  // x", 6) shouldBe -1
    }
})
