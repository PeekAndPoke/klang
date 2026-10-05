/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerType

class ChainDebugTest : StringSpec({

    class ContinuousPattern(val value: Double) {
        fun shifted(): ContinuousPattern {
            println("shifted: $value -> ${(value + 1.0) / 2.0}")
            return ContinuousPattern((value + 1.0) / 2.0)
        }

        fun range(from: Double, to: Double): ContinuousPattern {
            val result = from + value * (to - from)
            println("range($from, $to): $value -> $result")
            return ContinuousPattern(result)
        }
    }

    "Debug chain calculation" {
        val engine = klangScriptEngine {
            registerFunction<Double, ContinuousPattern>("lfo") { value ->
                println("lfo($value)")
                ContinuousPattern(value)
            }
            registerType<ContinuousPattern> {
                registerMethod("shifted") { shifted() }
                registerMethod("range") { from: Double, to: Double -> range(from, to) }
                registerMethod("value") {
                    println("Getting value: $value")
                    value
                }
            }
        }

        val script = """lfo(0.5).shifted().range(0.1, 0.9).value()"""

        val result = engine.execute(script)
        println("Final result: $result (type: ${result::class.simpleName})")
        println("Display: ${result.toDisplayString()}")
    }
})
