package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.builder.registerVarargFunction
import io.peekandpoke.klang.script.runtime.toDoubleOrNull
import io.peekandpoke.klang.script.runtime.toIntOrNull
import io.peekandpoke.klang.script.runtime.toStringOrNull

/**
 * Comprehensive tests for KlangScriptExtensionBuilder registration methods
 *
 * Tests all variants of:
 * - registerFunction (0-5 parameters + vararg)
 * - registerMethod (0-5 parameters + vararg)
 */
class KlangScriptExtensionBuilderTest : StringSpec() {

    object MathHelper {
        override fun toString() = "[MathHelper]"
    }

    init {

        // ==============================================
        // Native Function Registration Tests
        // ==============================================

        "registerFunction with 0 parameters" {
            val engine = klangScript {
                registerFunction("getAnswer") { 42.0 }
            }

            val result = engine.execute("getAnswer()")

            result.toDoubleOrNull() shouldBe 42.0
        }

        "registerFunction with 1 parameter" {
            val engine = klangScript {
                registerFunction("double") { x: Int -> x * 2 }
            }

            val result = engine.execute("double(21)")

            result.toIntOrNull() shouldBe 42
        }

        "registerFunction with 2 parameters" {
            val engine = klangScript {
                registerFunction("add") { a: Int, b: Int -> a + b }
            }

            val result = engine.execute("add(40, 2)")

            result.toIntOrNull() shouldBe 42
        }

        "registerFunction with 3 parameters" {
            val engine = klangScript {
                registerFunction("sum3") { a: Int, b: Int, c: Int -> a + b + c }
            }

            val result = engine.execute("sum3(10, 20, 12)")

            result.toIntOrNull() shouldBe 42
        }

        "registerFunction with 4 parameters" {
            val engine = klangScript {
                registerFunction("sum4") { a: Int, b: Int, c: Int, d: Int -> a + b + c + d }
            }

            val result = engine.execute("sum4(10, 10, 10, 12)")

            result.toIntOrNull() shouldBe 42
        }

        "registerFunction with 5 parameters" {
            val engine = klangScript {
                registerFunction("sum5") { a: Int, b: Int, c: Int, d: Int, e: Int -> a + b + c + d + e }
            }

            val result = engine.execute("sum5(8, 8, 8, 8, 10)")

            result.toIntOrNull() shouldBe 42
        }

        "registerNativeFunctionVararg with variable parameters" {
            val engine = klangScript {
                registerVarargFunction("sum") { numbers: List<Int> ->
                    numbers.sum()
                }
            }

            engine.execute("sum()").toIntOrNull() shouldBe 0
            engine.execute("sum(42)").toIntOrNull() shouldBe 42
            engine.execute("sum(10, 20, 12)").toIntOrNull() shouldBe 42
            engine.execute("sum(1, 2, 3, 4, 5, 6, 7, 8, 6)").toIntOrNull() shouldBe 42
        }

        // ==============================================
        // Extension Method Registration Tests
        // ==============================================

        class Counter(var value: Int = 0) {
            override fun toString() = "Counter($value)"
        }

        "registerMethod with 0 parameters" {
            val engine = klangScript {
                registerFunction("counter") { Counter(0) }
                registerType<Counter> {
                    registerMethod("get") { _: Any? -> value }
                }
            }

            val result = engine.execute("counter().get()")

            result.toIntOrNull() shouldBe 0
        }

        "registerMethod with 1 parameter" {
            val engine = klangScript {
                registerFunction("counter") { Counter(0) }
                registerType<Counter> {
                    registerMethod("add") { amount: Int ->
                        value += amount
                        value
                    }
                }
            }

            val result = engine.execute("counter().add(42)")

            result.toIntOrNull() shouldBe 42
        }

        "registerMethod with 2 parameters" {
            val engine = klangScript {
                registerFunction("counter") { Counter(0) }
                registerType<Counter> {
                    registerMethod("addTwo") { a: Int, b: Int ->
                        value += a + b
                        value
                    }
                }
            }

            val result = engine.execute("counter().addTwo(40, 2)")

            result.toIntOrNull() shouldBe 42
        }

        "registerMethod with 3 parameters" {
            val engine = klangScript {
                registerFunction("counter") { Counter(0) }
                registerType<Counter> {
                    registerMethod("addThree") { a: Int, b: Int, c: Int ->
                        value += a + b + c
                        value
                    }
                }
            }

            val result = engine.execute("counter().addThree(10, 20, 12)")

            result.toIntOrNull() shouldBe 42
        }

        "registerMethod with 4 parameters" {
            val engine = klangScript {
                registerFunction("counter") { Counter(0) }
                registerType<Counter> {
                    registerMethod("addFour") { a: Int, b: Int, c: Int, d: Int ->
                        value += a + b + c + d
                        value
                    }
                }
            }

            val result = engine.execute("counter().addFour(10, 10, 10, 12)")

            result.toIntOrNull() shouldBe 42
        }

        "registerMethod with 5 parameters" {
            val engine = klangScript {
                registerFunction("counter") { Counter(0) }
                registerType<Counter> {
                    registerMethod("addFive") { a: Int, b: Int, c: Int, d: Int, e: Int ->
                        value += a + b + c + d + e
                        value
                    }
                }
            }

            val result = engine.execute("counter().addFive(8, 8, 8, 8, 10)")

            result.toIntOrNull() shouldBe 42
        }

        "registerVarargMethod with variable parameters" {
            val engine = klangScript {
                registerFunction("counter") { Counter(0) }
                registerType<Counter> {
                    registerVarargMethod("addAll") { numbers: List<Int> ->
                        value += numbers.sum()
                        value
                    }
                }
            }

            engine.execute("counter().addAll()").toIntOrNull() shouldBe 0
            engine.execute("counter().addAll(42)").toIntOrNull() shouldBe 42
            engine.execute("counter().addAll(10, 20, 12)").toIntOrNull() shouldBe 42
            engine.execute("counter().addAll(1, 2, 3, 4, 5, 6, 7, 8, 6)").toIntOrNull() shouldBe 42
        }

        // ==============================================
        // registerObject Tests
        // ==============================================

        "registerObject with 0-parameter method" {
            val engine = klangScript {
                registerObject("Math", MathHelper) {
                    registerMethod("getPi") { _: Any? -> 3.14159 }
                }
            }

            val result = engine.execute("Math.getPi()")

            result.toDoubleOrNull() shouldBe 3.14159
        }

        "registerObject with 1-parameter method" {
            val engine = klangScript {
                registerObject("Math", MathHelper) {
                    registerMethod("square") { x: Double -> x * x }
                }
            }

            val result = engine.execute("Math.square(7)")

            result.toDoubleOrNull() shouldBe 49.0
        }

        "registerObject with 2-parameter method" {
            val engine = klangScript {
                registerObject("Math", MathHelper) {
                    registerMethod("multiply") { a: Double, b: Double -> a * b }
                }
            }

            val result = engine.execute("Math.multiply(6, 7)")

            result.toDoubleOrNull() shouldBe 42.0
        }

        "registerObject with 3-parameter method" {
            val engine = klangScript {
                registerObject("Math", MathHelper) {
                    registerMethod("sum3") { a: Double, b: Double, c: Double -> a + b + c }
                }
            }

            val result = engine.execute("Math.sum3(10, 20, 12)")

            result.toDoubleOrNull() shouldBe 42.0
        }

        "registerObject with 4-parameter method" {
            val engine = klangScript {
                registerObject("Math", MathHelper) {
                    registerMethod("sum4") { a: Double, b: Double, c: Double, d: Double -> a + b + c + d }
                }
            }

            val result = engine.execute("Math.sum4(10, 10, 10, 12)")

            result.toDoubleOrNull() shouldBe 42.0
        }

        "registerObject with 5-parameter method" {
            val engine = klangScript {
                registerObject("Math", MathHelper) {
                    registerMethod("sum5") { a: Double, b: Double, c: Double, d: Double, e: Double -> a + b + c + d + e }
                }
            }

            val result = engine.execute("Math.sum5(8, 8, 8, 8, 10)")

            result.toDoubleOrNull() shouldBe 42.0
        }

        "registerObject with vararg method" {
            val engine = klangScript {
                registerObject("Math", MathHelper) {
                    registerVarargMethod("sum") { numbers: List<Double> -> numbers.sum() }
                }
            }

            engine.execute("Math.sum()").toDoubleOrNull() shouldBe 0.0
            engine.execute("Math.sum(42)").toDoubleOrNull() shouldBe 42.0
            engine.execute("Math.sum(10, 20, 12)").toDoubleOrNull() shouldBe 42.0
            engine.execute("Math.sum(1, 2, 3, 4, 5, 6, 7, 8, 6)").toDoubleOrNull() shouldBe 42.0
        }

        // ==============================================
        // Method Chaining Tests
        // ==============================================

        class StringBuilder(private val value: String = "") {
            fun append(str: String) = StringBuilder(value + str)
            fun build() = value
            override fun toString() = "StringBuilder($value)"
        }

        "method chaining with multiple parameter counts" {
            val engine = klangScript {
                registerFunction("builder") { StringBuilder() }
                registerType<StringBuilder> {
                    registerMethod("append") { str: String -> append(str) }
                    registerMethod("build") { _: Any? -> build() }
                }
            }

            val result = engine.execute(
                """
                    builder()
                        .append("Hello")
                        .append(" ")
                        .append("World")
                        .build()
                """.trimIndent()
            )

            result.toStringOrNull() shouldBe "Hello World"
        }

        // ==============================================
        // Mixed Registration Tests
        // ==============================================

        "mixing functions and extension methods" {
            val engine = klangScript {
                registerFunction("create") { Counter(10) }
                registerFunction("getValue") { c: Counter -> c.value }
                registerType<Counter> {
                    registerMethod("increment") { _: Any? ->
                        value++
                        this
                    }
                    registerMethod("add") { amount: Int ->
                        value += amount
                        this
                    }
                }
            }

            val result = engine.execute(
                """
                    let c = create().increment().add(5)
                    getValue(c)
                """.trimIndent()
            )

            result.toIntOrNull() shouldBe 16
        }

        "registerObject can be called from library builder" {
            val lib = klangScriptLibrary("testlib") {
                registerObject("Helper", MathHelper) {
                    registerMethod("answer") { _: Any? -> 42 }
                }
                source("export { Helper }")
            }

            val engine = klangScript {
                registerLibrary(lib)
            }

            val result = engine.execute(
                """
                    import * from "testlib"
                    Helper.answer()
                """.trimIndent()
            )

            result.toIntOrNull() shouldBe 42
        }
    }
}
