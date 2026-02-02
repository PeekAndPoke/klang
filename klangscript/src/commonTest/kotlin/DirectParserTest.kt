package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.parser.KlangScriptParser

class DirectParserTest : StringSpec({

    "Parse: just sine2" {
        val code = "sine2"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: sine2.prop" {
        val code = "sine2.prop"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: sine2.fromBipolar" {
        val code = "sine2.fromBipolar"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: sine2.fromBipolar()" {
        val code = "sine2.fromBipolar()"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: sine2.fromBipolar().range" {
        val code = "sine2.fromBipolar().range"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: sine2.fromBipolar().range(0.1, 0.9)" {
        val code = "sine2.fromBipolar().range(0.1, 0.9)"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }
})
