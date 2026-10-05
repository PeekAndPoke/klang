/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.parser.KlangScriptParser

class DirectParserTest : StringSpec({

    "Parse: just lfo" {
        val code = "lfo"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: lfo.prop" {
        val code = "lfo.prop"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: lfo.shifted" {
        val code = "lfo.shifted"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: lfo.shifted()" {
        val code = "lfo.shifted()"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: lfo.shifted().range" {
        val code = "lfo.shifted().range"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "Parse: lfo.shifted().range(0.1, 0.9)" {
        val code = "lfo.shifted().range(0.1, 0.9)"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }
})
