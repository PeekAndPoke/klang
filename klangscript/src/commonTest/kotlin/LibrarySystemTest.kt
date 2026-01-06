package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.builder.registerNativeExtensionMethod
import io.peekandpoke.klang.script.builder.registerNativeFunction
import io.peekandpoke.klang.script.builder.registerNativeType
import io.peekandpoke.klang.script.runtime.ImportError
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for Step 3.11: Library System Enhancement
 *
 * Tests the KlangScriptLibrary builder API that allows bundling:
 * - KlangScript source code
 * - Native Kotlin function registrations
 * - Native Kotlin type registrations
 * - Native Kotlin extension method registrations
 *
 * Validates:
 * - Library creation with builder API
 * - Script-only libraries
 * - Native-only libraries
 * - Mixed script + native libraries
 * - Multiple libraries with different native types
 * - Library reusability across engines
 * - Export control still works with native registrations
 * - Backward compatibility with old registerLibrary(name, source) API
 */
class LibrarySystemTest : StringSpec({

    // Test data class for native interop
    class MathHelper(val value: Double) {
        fun double(): MathHelper = MathHelper(value * 2)
        fun add(other: Double): MathHelper = MathHelper(value + other)
        override fun toString(): String = "MathHelper($value)"
    }

    class StringHelper(val text: String) {
        fun upper(): StringHelper = StringHelper(text.uppercase())
        fun append(suffix: String): StringHelper = StringHelper(text + suffix)
        override fun toString(): String = "StringHelper($text)"
    }

    "Library with script code only (backward compatibility)" {
        val engine = klangScript {
            // Old API still works
            registerLibrary(
                "math", """
                    let square = (x) => x * x
                    let cube = (x) => x * x * x
                    export { square, cube }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "math"
                square(5) + cube(2)
            """.trimIndent()
        )

        result shouldBe NumberValue(33.0) // 25 + 8
    }

    "Library with script code only (new API)" {

        val mathLib = KlangScriptLibrary.builder("math")
            .source(
                """
                    let square = (x) => x * x
                    let cube = (x) => x * x * x
                    export { square, cube }
                """.trimIndent()
            )
            .build()

        val engine = klangScript {
            registerLibrary(mathLib)
        }

        val result = engine.execute(
            """
                import * from "math"
                square(5) + cube(2)
            """.trimIndent()
        )

        result shouldBe NumberValue(33.0) // 25 + 8
    }

    "Library with native functions only" {

        val mathLib = klangScriptLibrary("math") {
            registerNativeFunction<Double, MathHelper>("create") { value ->
                MathHelper(value)
            }
            registerNativeExtensionMethod<MathHelper, MathHelper>("double") {
                double()
            }
            registerNativeExtensionMethod<MathHelper, Double, MathHelper>("add") { value ->
                add(value)
            }
        }

        val engine = klangScript {
            registerLibrary(mathLib)
        }

        val result = engine.execute(
            """
                import * from "math"
                create(10).double().add(5)
            """.trimIndent()
        )

        result.toDisplayString() shouldContain "25" // (10 * 2) + 5
    }

    "Library with both script and native code" {
        val mathLib = klangScriptLibrary("math") {
            source(
                """
                    // Script helper that uses native function
                    let quickDouble = (x) => create(x).double()
                    export { quickDouble }
                """.trimIndent()
            )
            registerNativeFunction<Double, MathHelper>("create") { value ->
                MathHelper(value)
            }

            registerNativeExtensionMethod<MathHelper, MathHelper>("double") {
                double()
            }
        }

        val engine = klangScript {
            registerLibrary(mathLib)
        }

        val result = engine.execute(
            """
                import * from "math"
                quickDouble(7)
            """.trimIndent()
        )

        result.toDisplayString() shouldContain "14"
    }

    "Library with multiple native types" {

        val lib = klangScriptLibrary("helpers") {
            registerNativeFunction<Double, MathHelper>("math") { value ->
                MathHelper(value)
            }
            registerNativeExtensionMethod<MathHelper, MathHelper>("double") {
                double()
            }
            registerNativeFunction<String, StringHelper>("str") { text ->
                StringHelper(text)
            }
            registerNativeExtensionMethod<StringHelper, StringHelper>("upper") {
                upper()
            }
        }

        val engine = klangScript {
            registerLibrary(lib)
        }

        val result = engine.execute(
            """
                import * from "helpers"
                let doubled = math(5).double()
                let uppercased = str("hello").upper()
                doubled
            """.trimIndent()
        )

        result.toDisplayString() shouldContain "10"
    }

    "Multiple libraries with different native types" {
        val mathLib = klangScriptLibrary("math") {
            registerNativeFunction<Double, MathHelper>("createMath") { value ->
                MathHelper(value)
            }
            registerNativeExtensionMethod<MathHelper, Double, MathHelper>("add") { value ->
                add(value)
            }
        }

        val stringLib = klangScriptLibrary("strings") {
            registerNativeFunction<String, StringHelper>("createString") { text ->
                StringHelper(text)
            }
            registerNativeExtensionMethod<StringHelper, String, StringHelper>("append") { suffix ->
                append(suffix)
            }
        }

        val engine = klangScript {
            registerLibrary(mathLib)
            registerLibrary(stringLib)
        }

        val result = engine.execute(
            """
                import * from "math"
                import * from "strings"
                let num = createMath(10).add(5)
                let text = createString("hello").append(" world")
                text
            """.trimIndent()
        )

        result.toDisplayString() shouldContain "hello world"
    }

    "Export control works with script functions (native functions are global)" {
        val lib = klangScriptLibrary("math") {
            source(
                """
                    let helper = (x) => create(x).double()
                    let publicFunc = (x) => helper(x)
                    export { publicFunc }
                """.trimIndent()
            )

            registerNativeFunction<Double, MathHelper>("create") { value ->
                MathHelper(value)
            }
            registerNativeExtensionMethod<MathHelper, MathHelper>("double") {
                double()
            }
        }

        val engine = klangScript {
            registerLibrary(lib)
        }

        // Can use exported function
        val result = engine.execute(
            """
                import * from "math"
                publicFunc(5)
            """.trimIndent()
        )
        result.toDisplayString() shouldContain "10"

        // Cannot use non-exported script function helper
        shouldThrow<io.peekandpoke.klang.script.runtime.ReferenceError> {
            engine.execute(
                """
                    import * from "math"
                    helper(5)
                """.trimIndent()
            )
        }

        // Note: Native function 'create' IS accessible because native functions are registered globally
        // This is by design - native registrations are global
        val nativeResult = engine.execute(
            """
                import * from "math"
                create(3).double()
            """.trimIndent()
        )
        nativeResult.toDisplayString() shouldContain "6"
    }

    "Native functions registered in library are globally available" {
        val lib = klangScriptLibrary("math") {
            registerNativeFunction<Double, MathHelper>("create") { value ->
                MathHelper(value)
            }
            registerNativeExtensionMethod<MathHelper, MathHelper>("double") {
                double()
            }
        }

        val engine = klangScript {
            registerLibrary(lib)
        }

        // Import the library
        engine.execute("import * from \"math\"")

        // Native functions are available globally after import
        val result = engine.execute("create(3).double()")
        result.toDisplayString() shouldContain "6"
    }

    "Library can be used by multiple engines" {
        val lib = klangScriptLibrary("math") {
            source(
                """
                    let square = (x) => x * x
                    export { square }
                """.trimIndent()
            )
            registerNativeFunction<Double, MathHelper>("create") { value ->
                MathHelper(value)
            }
        }

        // Use with first engine
        val engine1 = klangScript {
            registerLibrary(lib)
        }

        val result1 = engine1.execute(
            """
                import * from "math"
                square(4)
            """.trimIndent()
        )

        result1 shouldBe NumberValue(16.0)

        // Use with second engine
        val engine2 = klangScript {
            registerLibrary(lib)
        }

        val result2 = engine2.execute(
            """
                import * from "math"
                square(5)
            """.trimIndent()
        )

        result2 shouldBe NumberValue(25.0)
    }

    "Library with no source code (native only)" {
        val lib = klangScriptLibrary("nativelib") {
            registerNativeFunction<Double, MathHelper>("create") { value ->
                MathHelper(value)
            }
            registerNativeExtensionMethod<MathHelper, MathHelper>("double") {
                double()
            }
        }

        val engine = klangScript {
            registerLibrary(lib)
        }

        val result = engine.execute(
            """
                import * from "nativelib"
                create(7).double()
            """.trimIndent()
        )

        result.toDisplayString() shouldContain "14"
    }

    "Library not found error still works" {
        val engine = klangScript()

        val exception = shouldThrow<ImportError> {
            engine.execute("""import * from "nonexistent" """)
        }

        exception.message shouldContain "Library not found"
    }

    "Selective imports work with native library functions" {
        val lib = klangScriptLibrary("math") {
            source(
                """
                    let helper1 = (x) => create(x)
                    let helper2 = (x) => create(x).double()
                    export { helper1, helper2 }
                """.trimIndent()
            )
            registerNativeFunction<Double, MathHelper>("create") { value ->
                MathHelper(value)
            }
            registerNativeExtensionMethod<MathHelper, MathHelper>("double") {
                double()
            }
        }

        val engine = klangScript {
            registerLibrary(lib)
        }

        val result = engine.execute(
            """
                import { helper2 } from "math"
                helper2(4)
            """.trimIndent()
        )

        result.toDisplayString() shouldContain "8"
    }

    "Namespace imports work with native library functions" {
        val lib = klangScriptLibrary("math") {
            source(
                """
                    let square = (x) => x * x
                    export { square }
                """.trimIndent()
            )
        }

        val engine = klangScript {
            registerLibrary(lib)
        }

        val result = engine.execute(
            """
                import * as M from "math"
                M.square(6)
            """.trimIndent()
        )

        result shouldBe NumberValue(36.0)
    }

    "Chain multiple extension methods from library" {
        val lib = klangScriptLibrary("math") {
            registerNativeFunction<Double, MathHelper>("create") { value ->
                MathHelper(value)
            }
            registerNativeExtensionMethod<MathHelper, MathHelper>("double") {
                double()
            }
            registerNativeExtensionMethod<MathHelper, Double, MathHelper>("add") { value ->
                add(value)
            }
        }

        val engine = klangScript {
            registerLibrary(lib)
        }

        val result = engine.execute(
            """
                import * from "math"
                create(5).double().add(3).double()
            """.trimIndent()
        )

        result.toDisplayString() shouldContain "26" // ((5 * 2) + 3) * 2 = 26
    }

    "Library builder is fluent" {
        // Verify that all methods return the library for chaining
        val lib = klangScriptLibrary("test") {
            source("let x = 1")
            registerNativeType<MathHelper>()
            registerNativeFunction<Double, MathHelper>("create") { MathHelper(it) }
            registerNativeFunction("test") { _ -> NumberValue(0.0) }
            registerNativeExtensionMethod<MathHelper, MathHelper>("double") { double() }
            registerNativeExtensionMethod<MathHelper, Double, MathHelper>("add") { v -> add(v) }
            registerNativeExtensionMethod<MathHelper, Double, Double, MathHelper>("addTwo") { v1, v2 -> add(v1 + v2) }
        }

        // If this compiles, the fluent API works
        lib.name shouldBe "test"
    }

    "Library with registerFunction (not registerNativeFunction)" {

        val lib = klangScriptLibrary("utils") {
            registerNativeFunction("sum") { args ->
                val sum = args.sumOf { (it as NumberValue).value }
                NumberValue(sum)
            }
        }

        val engine = klangScript {
            registerLibrary(lib)
        }

        val result = engine.execute(
            """
                import * from "utils"
                sum(1, 2, 3, 4)
            """.trimIndent()
        )

        result shouldBe NumberValue(10.0)
    }
})
