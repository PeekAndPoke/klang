/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec

class SimpleParseErrorTest : StringSpec({

    "Reproduce parse error with no-arg method in chain" {
        val engine = klangScript()

        try {
            // This should trigger a parse error
            val script = "obj.method1().method2()"
            engine.execute(script)
        } catch (e: Exception) {
            println("Error type: ${e::class.simpleName}")
            println("Error message: ${e.message}")
            e.printStackTrace()
        }
    }
})
