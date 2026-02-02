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
