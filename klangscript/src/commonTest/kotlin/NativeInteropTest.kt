package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.runtime.ArgumentError
import io.peekandpoke.klang.script.runtime.TypeError

/**
 * Native Kotlin interop tests
 *
 * Tests the integration between KlangScript and native Kotlin classes,
 * including extension method registration, method chaining, and type conversion.
 */
class NativeInteropTest : StringSpec() {

    /**
     * Mock native class for testing
     */
    class NativeObject(val pattern: String) {
        fun sound(soundName: String): NativeObject {
            return NativeObject("$pattern|sound:$soundName")
        }

        fun gain(amount: Double): NativeObject {
            return NativeObject("$pattern|gain:$amount")
        }

        fun reverse(): NativeObject {
            return NativeObject("$pattern|reversed")
        }

        fun pan(left: Double, right: Double): NativeObject {
            return NativeObject("$pattern|pan:$left,$right")
        }

        override fun toString(): String = pattern
    }

    init {

        "Native object creation via registered function" {
            // Register factory function that returns native object
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
            }

            val script = "note(\"a b c d\")"
            val result = engine.execute(script)

            result.toDisplayString() shouldContain "a b c d"
        }

        "Extension method with one parameter" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String -> sound(soundName) }
                }
            }

            val script = """
                let pattern = note("a b c d")
                pattern.sound("saw")
            """.trimIndent()

            val result = engine.execute(script)
            result.toDisplayString() shouldContain "a b c d|sound:saw"
        }

        "Extension method with no parameters" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerType<NativeObject> {
                    registerMethod("reverse") { reverse() }
                }
            }

            val script = """
                let pattern = note("a b c d")
                pattern.reverse()
            """.trimIndent()

            val result = engine.execute(script)
            result.toDisplayString() shouldContain "reversed"
        }

        "Extension method with two parameters" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerType<NativeObject> {
                    registerMethod("pan") { left: Double, right: Double ->
                        pan(left, right)
                    }
                }
            }

            val script = """
                let pattern = note("a b c d")
                pattern.pan(-1.0, 1.0)
            """.trimIndent()

            val result = engine.execute(script)
            result.toDisplayString() shouldContain "pan:-1"
            result.toDisplayString() shouldContain ",1"
        }

        "Method chaining - multiple calls" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String ->
                        sound(soundName)
                    }
                    registerMethod("gain") { amount: Double ->
                        gain(amount)
                    }
                }
            }

            // The exact use case from the design discussion!
            val script = """note("a b c d").sound("saw").gain(0.8)"""

            val result = engine.execute(script)
            result.toDisplayString() shouldContain "a b c d|sound:saw|gain:0.8"
        }

        "Method chaining - long chain" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String ->
                        sound(soundName)
                    }
                    registerMethod("gain") { amount: Double ->
                        gain(amount)
                    }
                    registerMethod("reverse") {
                        reverse()
                    }
                }
            }

            val script = """
                note("a b c d")
                    .sound("saw")
                    .gain(0.8)
                    .reverse()
            """.trimIndent()

            val result = engine.execute(script)
            result.toDisplayString() shouldContain "reversed"
            result.toDisplayString() shouldContain "gain:0.8"
        }

        "Error - method not found on native type" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String ->
                        sound(soundName)
                    }
                }
            }

            val script = """
                let pattern = note("a b c d")
                pattern.nonExistent("test")
            """.trimIndent()

            val error = shouldThrow<TypeError> {
                engine.execute(script)
            }

            error.message shouldContain "has no method 'nonExistent'"
            error.message shouldContain NativeObject::class.simpleName!!
            // Should suggest available methods
            error.message shouldContain "sound"
        }

        "Error - wrong argument count" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String ->
                        sound(soundName)
                    }
                }
            }

            val script = """
                let pattern = note("a b c d")
                pattern.sound()
            """.trimIndent()

            shouldThrow<ArgumentError> {
                engine.execute(script)
            }
        }

        "Native objects in variables" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String ->
                        sound(soundName)
                    }
                }
            }

            val script = """
                let melody = note("a b c d")
                let withSound = melody.sound("saw")
                withSound
            """.trimIndent()

            val result = engine.execute(script)
            result.toDisplayString() shouldContain "sound:saw"
        }

        "Native objects passed as function arguments" {
            val captured = mutableListOf<String>()

            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                registerFunctionRaw("capture") { args, _ ->
                    captured.add(args[0].toDisplayString())
                    args[0]
                }
                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String ->
                        sound(soundName)
                    }
                }
            }

            val script = """
                let pattern = note("a b c d").sound("saw")
                capture(pattern)
            """.trimIndent()

            engine.execute(script)
            captured.size shouldBe 1
            captured[0] shouldContain "sound:saw"
        }

        "Multiple native types registered" {
            // Register first type
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }
                // Register second type (simulate another native class)
                registerFunction<String, NativeObject>("rhythm") { pattern ->
                    NativeObject("rhythm:$pattern")
                }

                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String -> sound(soundName) }
                    registerMethod("speed") { factor: Double ->
                        NativeObject("${pattern}|speed:$factor")
                    }
                }
            }

            val script = """
                let melody = note("a b c d").sound("saw")
                let beat = rhythm("x x x x").speed(2.0)
                melody
            """.trimIndent()

            val result = engine.execute(script)
            result.toDisplayString() shouldContain "sound:saw"
        }

        "Native objects work with libraries and imports" {
            val engine = klangScript {
                registerFunction<String, NativeObject>("note") { pattern ->
                    NativeObject(pattern)
                }

                registerType<NativeObject> {
                    registerMethod("sound") { soundName: String ->
                        sound(soundName)
                    }
                    registerMethod("gain") { amount: Double ->
                        gain(amount)
                    }
                }

                // Register library that uses native objects
                registerLibrary(
                    "patterns",
                    """
                        let makePattern = (notes) => note(notes).sound("saw")
                        let withGain = (pattern, amount) => pattern.gain(amount)
        
                        export { makePattern, withGain }
                    """.trimIndent()
                )
            }

            val script = """
                import { makePattern, withGain } from "patterns"
    
                let pattern = makePattern("a b c d")
                withGain(pattern, 0.8)
            """.trimIndent()

            val result = engine.execute(script)
            result.toDisplayString() shouldContain "gain:0.8"
        }
    }
}
