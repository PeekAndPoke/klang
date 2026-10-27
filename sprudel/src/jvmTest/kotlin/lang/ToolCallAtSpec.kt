/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.ToolCallAt
import io.peekandpoke.klang.script.intel.toolCallAt

/**
 * The call the Sprudel tool shows (KLANG-00374): the innermost call around the cursor whose arguments have tools,
 * anywhere in its parentheses, and the argument its tools edit; against the real generated registries.
 */
class ToolCallAtSpec : StringSpec({

    val registry = KlangDocsRegistry().apply {
        registerAll(generatedStdlibDocs)
        registerAll(generatedSprudelDocs)
    }

    /** The tool call with the cursor [after] the first [needle] in [code] (0: on its first character). */
    fun at(code: String, needle: String, after: Int = 0): ToolCallAt? {
        val pos = code.indexOf(needle)

        withClue("needle '$needle' in '$code'") { pos shouldBeGreaterThanOrEqual 0 }

        return AnalyzedAst.build(code, registry).toolCallAt(pos + after)
    }

    "on an argument with tools: that argument, at the cursor" {
        val found = at("""note("c3").gain(0.5)""", "0.5").shouldNotBeNull()

        found.functionName shouldBe "gain"
        found.argIndex shouldBe 0
        found.atCursor shouldBe true
        found.binding.param.uitools.isNotEmpty() shouldBe true
    }

    "a string with a parenthesis in it does not end the call" {
        at("""note("c3").sound(")").gain(0.5)""", "0.5").shouldNotBeNull().functionName shouldBe "gain"
        // The cursor after a string's ")": a scan blind to strings would end the call there
        at("""note("c3").gain(")", 0.5)""", "0.5").shouldNotBeNull().functionName shouldBe "gain"
    }

    "anywhere in the parentheses: on the opening one, before the closing one, between the arguments" {
        val code = """note("c3").lpf(800, 4)"""

        at(code, "(800", after = 1).shouldNotBeNull().functionName shouldBe "lpf"
        at(code, "4)", after = 1).shouldNotBeNull().functionName shouldBe "lpf"
        at(code, ", 4", after = 1).shouldNotBeNull().let { found ->
            found.functionName shouldBe "lpf"
            // Between the arguments: the call's first one with tools
            found.argIndex shouldBe 0
            found.atCursor shouldBe false
        }
    }

    "an empty call: its first parameter, no argument yet" {
        val found = at("""note("c3").gain()""", "gain(", after = 5).shouldNotBeNull()

        found.functionName shouldBe "gain"
        found.argIndex shouldBe -1
        found.binding.param.name shouldBe "amount"
    }

    "on the called name, or past the closing parenthesis: not in that call" {
        at("""note("c3").gain(0.5)""", "gain").shouldBeNull()
        at("""note("c3").gain(0.5).fast(2)""", "fast(", after = 5).shouldBeNull()
    }

    "a literal inside a nested call is not the outer call's: gain(sine.slow(4)) gives no tool on the 4" {
        at("""note("c3").gain(sine.range(0.2, 0.5).slow(4))""", "4)").shouldBeNull()
        at("""note("c3").gain(sine.range(0.2, 0.5))""", "0.2").shouldBeNull()
        at("""note("c3").gain(steady(0.5))""", "0.5").shouldBeNull()
    }

    "a string at the head of the argument's chain is the literal edited, wherever the cursor is in it" {
        val code = """note("c3").gain("0.5 0.8".fast(2))"""
        val found = at(code, "2)").shouldNotBeNull()

        found.functionName shouldBe "gain"
        found.editRange shouldBe code.indexOf("\"0.5")..(code.indexOf("0.8\"") + 3)
    }

    "a comment in the parentheses, an apostrophe in it, does not end or open anything" {
        at("note(\"c3\").gain(0.5 // it's loud\n)", "0.5").shouldNotBeNull().functionName shouldBe "gain"
        at("note(\"c3\").gain(0.5 // it's loud\n)", "loud", after = 4).shouldNotBeNull().functionName shouldBe "gain"
    }

    "a number in another notation is edited whole; a parenthesised one is not offered" {
        val code = """note("c3").gain(5e-1)"""
        val found = at(code, "5e").shouldNotBeNull()

        code.substring(found.editRange!!.first, found.editRange!!.last + 1) shouldBe "5e-1"
        at("""note("c3").gain((0.5))""", "0.5").shouldBeNull()
    }

    "in space before an inner call's closing parenthesis: the inner call, not the one around it" {
        // lpf's own 800 is editable too: the outer call must not win
        val code = """note("c3").lpf(800, q = steady(1).gain(0.5 ))"""

        at(code, " ))", after = 1).shouldNotBeNull().functionName shouldBe "gain"
        at(code, "800").shouldNotBeNull().functionName shouldBe "lpf"
    }

    "an empty call with space in it, in a chain: its first parameter" {
        at("""note("c3").gain(  ).fast(2)""", "gain(", after = 6).shouldNotBeNull().argIndex shouldBe -1
    }

    "right before the opening parenthesis is on the name: nothing" {
        at("""note("c3").gain(0.5)""", "(0.5").shouldBeNull()
    }

    "the argument at the cursor is no literal: the call's next one that is, with tools" {
        val found = at("""note("c3").lpf(sine.range(200, 2000), 4)""", "sine").shouldNotBeNull()

        found.functionName shouldBe "lpf"
        found.argIndex shouldBe 1
        found.atCursor shouldBe false
    }

    "a call with no tools anywhere: nothing" {
        at("""note("c3").fast(2)""", "2").shouldBeNull()
    }
})
