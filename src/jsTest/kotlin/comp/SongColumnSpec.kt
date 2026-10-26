/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly

/** The song in the column (KLANG-00349): the libraries its code imports. */
class SongColumnSpec : StringSpec({

    "the imports are the names after `from`, in either quotes, also over lines, in order, each once, only at a line's start" {
        val code = """
            import * from "stdlib"
            import { sound } from "sprudel"
            import * from 'tones'
            import {
                a,
                b
            } from "mini"
            import * from "stdlib"
            // import * from "commented-out" is not one
            let text = "import * from \"nope\""
        """.trimIndent()

        SongColumn.importsOf(code) shouldContainExactly listOf("stdlib", "sprudel", "tones", "mini")
    }
})
