package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ExportStatement
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for Export Aliasing (Step 3.8.3)
 *
 * Validates:
 * - Parsing export statements with aliases
 * - Exporting with single alias
 * - Exporting with multiple aliases
 * - Mixed exports (some aliased, some not)
 * - Importing using exported names
 * - Original names not accessible when aliased
 */
class ExportAliasingTest : StringSpec({

    "should parse export with single alias" {
        val result = KlangScriptParser.parse("""export { add as sum }""")

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ExportStatement>()
        stmt.exports shouldBe listOf(Pair("add", "sum"))
    }

    "should parse export with multiple aliases" {
        val result = KlangScriptParser.parse("""export { add as sum, multiply as mul }""")

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ExportStatement>()
        stmt.exports shouldBe listOf(
            Pair("add", "sum"),
            Pair("multiply", "mul")
        )
    }

    "should parse mixed export with and without aliases" {
        val result = KlangScriptParser.parse("""export { add, multiply as mul }""")

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ExportStatement>()
        stmt.exports shouldBe listOf(
            Pair("add", "add"),  // No alias
            Pair("multiply", "mul")  // Aliased
        )
    }

    "should parse export without aliases" {
        val result = KlangScriptParser.parse("""export { add, multiply }""")

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ExportStatement>()
        stmt.exports shouldBe listOf(
            Pair("add", "add"),
            Pair("multiply", "multiply")
        )
    }

    "should import using exported alias name" {
        val builder = KlangScript.builder()

        builder.registerLibrary(
            "math", """
                let add = (a, b) => a + b
                export { add as sum }
            """.trimIndent()
        )

        val engine = builder.build()

        val result = engine.execute(
            """
                import { sum } from "math"
                sum(1, 2)
            """.trimIndent()
        )

        result shouldBe NumberValue(3.0)
    }

    "should not allow importing using original name when aliased" {
        val builder = KlangScript.builder()

        builder.registerLibrary(
            "math", """
                let add = (a, b) => a + b
                export { add as sum }
            """.trimIndent()
        )

        val engine = builder.build()

        try {
            engine.execute(
                """
                    import { add } from "math"
                    add(1, 2)
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Cannot import non-exported symbols: add"
        }
    }

    "should support multiple export aliases" {
        val builder = KlangScript.builder()

        builder.registerLibrary(
            "math", """
                let add = (a, b) => a + b
                let multiply = (a, b) => a * b
                export { add as sum, multiply as mul }
            """.trimIndent()
        )

        val engine = builder.build()

        val result = engine.execute(
            """
                import { sum, mul } from "math"
                sum(2, mul(3, 4))
            """.trimIndent()
        )

        result shouldBe NumberValue(14.0)
    }

    "should support mixed export with aliases" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    let subtract = (a, b) => a - b
                    let multiply = (a, b) => a * b
                    export { add, subtract as sub, multiply }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { add, sub } from "math"
                add(10, sub(5, 2))
            """.trimIndent()
        )

        result shouldBe NumberValue(13.0)  // 10 + (5 - 2) = 13
    }

    "should export alias work with wildcard import" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    export { add as sum }
                """.trimIndent()
            )
        }


        val result = engine.execute(
            """
                import * from "math"
                sum(5, 3)
            """.trimIndent()
        )

        result shouldBe NumberValue(8.0)
    }

    "should export alias work with namespace import" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    let multiply = (a, b) => a * b
                    export { add as sum, multiply as mul }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * as math from "math"
                math.sum(2, math.mul(3, 4))
            """.trimIndent()
        )

        result shouldBe NumberValue(14.0)
    }

    "should not expose original name in wildcard import when aliased" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    export { add as sum }
                """.trimIndent()
            )
        }

        try {
            engine.execute(
                """
                    import * from "math"
                    add(1, 2)  // Should fail - only 'sum' is exported
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Undefined variable: add"
        }
    }

    "should allow import aliasing on top of export aliasing" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    export { add as addition }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { addition as myAdd } from "math"
                myAdd(7, 3)
            """.trimIndent()
        )

        result shouldBe NumberValue(10.0)
    }

    "should export aliasing preserve function behavior" {
        val engine = klangScript {
            registerLibrary(
                "funcs", """
                    let square = (x) => x * x
                    let double = (x) => x * 2
                    export { square as sq, double }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { sq, double } from "funcs"
                sq(double(3))
            """.trimIndent()
        )

        result shouldBe NumberValue(36.0)  // double(3) = 6, sq(6) = 36
    }

    "should export aliasing work with objects" {
        val engine = klangScript {
            registerLibrary(
                "config", """
                    let settings = { value: 42 }
                    export { settings as config }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { config } from "config"
                config.value
            """.trimIndent()
        )

        result shouldBe NumberValue(42.0)
    }

    "should handle complex real-world pattern with export aliases" {
        val engine = klangScript {
            registerFunction1("nativeLog") { value ->
                NumberValue((value as NumberValue).value * 10)
            }

            registerLibrary(
                "api", """
                    let internalProcess = (x) => nativeLog(x)
                    let internalTransform = (x) => x * 2
                    let helperFunc = (x) => x + 1
        
                    // Only expose public API with clean names
                    export { internalProcess as process, internalTransform as transform }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { process, transform } from "api"
                process(transform(3))
            """.trimIndent()
        )

        result shouldBe NumberValue(60.0)  // transform(3) = 6, process(6) = 60
    }

    "should error when trying to import non-exported symbol even if it exists" {
        val engine = klangScript {
            registerLibrary(
                "lib", """
                    let public = (x) => x + 1
                    let private = (x) => x * 2
                    export { public as pub }
                """.trimIndent()
            )
        }

        try {
            engine.execute(
                """
                    import { private } from "lib"
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Cannot import non-exported symbols: private"
        }
    }
})
