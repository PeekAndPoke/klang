package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.klangScript

class ScopeIsolationTest : StringSpec({

    "Encapsulation: Function parameters should not leak to global scope" {
        val engine = klangScript()
        val script = """
            let id = (param) => param
            id(42)
            param
        """.trimIndent()

        shouldThrow<ReferenceError> {
            engine.execute(script)
        }
    }

    "Encapsulation: Lexical Scoping (Closure)" {
        // Verify that a function captures the variable from its definition scope,
        // not from the caller's scope.
        val engine = klangScript()
        val script = """
            let x = "global"
            let getGlobalX = () => x
            
            // 'runner' has a parameter 'x' which shadows the global 'x' inside it.
            // It calls 'getGlobalX'.
            let runner = (x, func) => func()
            
            // We pass "shadowed" as 'x', but 'getGlobalX' should still see "global"
            runner("shadowed", getGlobalX)
        """.trimIndent()

        val result = engine.execute(script)
        result.toDisplayString() shouldBe "global"
    }

    "Encapsulation: Parameters shadow globals" {
        val engine = klangScript()
        val script = """
            let x = "global"
            let identity = (x) => x
            identity("shadowed")
        """.trimIndent()

        val result = engine.execute(script)
        result.toDisplayString() shouldBe "shadowed"
    }

    "Encapsulation: Globals remain unchanged after parameter shadowing" {
        val engine = klangScript()
        val script = """
            let x = "global"
            let identity = (x) => x
            identity("shadowed")
            x
        """.trimIndent()

        val result = engine.execute(script)
        result.toDisplayString() shouldBe "global"
    }

    "Encapsulation: Library internals are private (selective export)" {
        val engine = klangScript {
            registerLibrary(
                "secret_lib", """
                let publicVal = "public"
                let privateVal = "private"
                
                export { publicVal }
            """.trimIndent()
            )
        }

        // We import * which imports all EXPORTED symbols.
        // 'privateVal' should NOT be imported.
        val script = """
            import * from "secret_lib"
            privateVal
        """.trimIndent()

        shouldThrow<ReferenceError> {
            engine.execute(script)
        }
    }

    "Encapsulation: Library internals are private (aliased export)" {
        val engine = klangScript {
            registerLibrary(
                "math_lib", """
                let add = (a, b) => a + b
                export { add as sum }
            """.trimIndent()
            )
        }

        // 'add' is exported as 'sum'. 'add' should not be visible.
        val script = """
            import * from "math_lib"
            add(1, 2)
        """.trimIndent()

        shouldThrow<ReferenceError> {
            engine.execute(script)
        }
    }

    "Encapsulation: Libraries cannot see each other's globals" {
        // Lib A defines 'aVal'.
        // Lib B tries to use 'aVal' without importing it.
        val engine = klangScript {
            registerLibrary(
                "lib_a", """
                let aVal = "A"
            """.trimIndent()
            ) // Implicit export of all since no export statement

            registerLibrary(
                "lib_b", """
                let getA = () => aVal
            """.trimIndent()
            )
        }

        // We run code that loads Lib A (so 'aVal' is in A's env)
        // Then we load Lib B. Lib B's env should not see Lib A's env.
        // Inside 'getA', 'aVal' is looked up when called.
        val script = """
            import * from "lib_a"
            import * from "lib_b"
            getA()
        """.trimIndent()

        shouldThrow<ReferenceError> {
            engine.execute(script)
        }
    }

    "Encapsulation: Imported variables are isolated per environment" {
        // This ensures that if we have two different engine instances or environments,
        // imports in one don't affect the other.

        val engine1 = klangScript {
            registerLibrary("common", """let val = 1""")
        }
        val engine2 = klangScript {
            registerLibrary("common", """let val = 2""")
        }

        engine1.execute("""import * from "common"""")
        engine2.execute("""import * from "common"""")

        engine1.execute("val").toDisplayString().toDouble() shouldBe 1.0
        engine2.execute("val").toDisplayString().toDouble() shouldBe 2.0
    }
})
