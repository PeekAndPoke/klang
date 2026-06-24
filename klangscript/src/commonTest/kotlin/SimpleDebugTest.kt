/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec

class SimpleDebugTest : StringSpec({

    "Debug: Simple chain" {
        val engine = klangScript()

        try {
            // Simplest possible case
            val result = engine.execute("obj.prop1().prop2")
            println("Success! Result: $result")
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        }
    }

    "Debug: Parse just parentheses after member" {
        val engine = klangScript()

        try {
            val result = engine.execute("obj.method()")
            println("Success! Result: $result")
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        }
    }
})
