package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.runtime.*

/**
 * Comprehensive integration tests that exercise the full KlangScript system
 * including parsing, interpretation, error handling, and all language features.
 */
class ComprehensiveIntegrationTest : StringSpec({

    "Complete live coding example with variables, functions, and chaining" {
        val builder = KlangScript.builder()
        val events = mutableListOf<String>()

        // Register a note() function that returns a chainable object
        builder.registerFunctionRaw("note") { values ->
            val value = values[0]
            val notes = (value as StringValue).value
            ObjectValue(
                mutableMapOf(
                    "notes" to StringValue(notes),
                    "gain" to NativeFunctionValue("gain") { args ->
                        val gainValue = (args[0] as NumberValue).value
                        events.add("note($notes).gain($gainValue)")
                        ObjectValue(
                            mutableMapOf(
                                "notes" to StringValue(notes),
                                "gainValue" to NumberValue(gainValue),
                                "pan" to NativeFunctionValue("pan") { panArgs ->
                                    val panValue = (panArgs[0] as StringValue).value
                                    events.add("note($notes).gain($gainValue).pan($panValue)")
                                    NullValue
                                }
                            )
                        )
                    }
                )
            )
        }

        // Register a stack() function that takes multiple arguments
        builder.registerFunctionRaw("stack") { args ->
            events.add("stack(${args.size} items)")
            NullValue
        }

        val script = """
            let melody = "a b c d"
            let volume = 0.5

            note(melody).gain(volume).pan("0 1")

            stack(
                note("a"),
                note("b")
            )
        """.trimIndent()

        val engine = builder.build()

        engine.execute(script, sourceName = "live.klang")

        // Expected events:
        // 1. note(melody).gain(volume) captures "note(a b c d).gain(0.5)"
        // 2. .pan("0 1") captures "note(a b c d).gain(0.5).pan(0 1)"
        // 3. stack(...) captures "stack(2 items)"
        events shouldBe listOf(
            "note(a b c d).gain(0.5)",
            "note(a b c d).gain(0.5).pan(0 1)",
            "stack(2 items)"
        )
    }

    "Complex script with library imports, exports, and namespaces" {
        val builder = KlangScript.builder()
        val results = mutableListOf<Double>()

        // Register native math operations
        builder.registerFunctionRaw("add") { args ->
            NumberValue(args.sumOf { (it as NumberValue).value })
        }

        builder.registerFunctionRaw("mul") { args ->
            NumberValue(args.fold(1.0) { acc, v -> acc * (v as NumberValue).value })
        }

        // Register a math library with helper functions
        builder.registerLibrary(
            "math",
            """
                let square = (x) => mul(x, x)
                let cube = (x) => mul(x, x, x)
                let addSquares = (a, b) => add(square(a), square(b))
    
                export { square, cube as cubed, addSquares }
            """.trimIndent()
        )

        // Register a result capture function
        builder.registerFunctionRaw("capture") { value ->
            results.add((value.first() as NumberValue).value)
            value.first()
        }

        val script = """
            import * as math from "math"
            import { addSquares } from "math"

            // Test namespace access
            capture(math.square(5))
            capture(math.cubed(3))

            // Test selective import
            capture(addSquares(3, 4))

            // Test variable and closure
            let makeMultiplier = (factor) => (x) => mul(x, factor)
            let double = makeMultiplier(2)
            let triple = makeMultiplier(3)

            capture(double(7))
            capture(triple(7))
        """.trimIndent()

        val engine = builder.build()

        engine.execute(script, sourceName = "main.klang")

        results shouldBe listOf(
            25.0,   // square(5)
            27.0,   // cube(3)
            25.0,   // addSquares(3, 4) = 9 + 16
            14.0,   // double(7)
            21.0    // triple(7)
        )
    }

    "Nested closures and higher-order functions" {
        val builder = KlangScript.builder()
        val results = mutableListOf<Double>()

        builder.registerFunctionRaw("add") { args ->
            NumberValue(args.sumOf { (it as NumberValue).value })
        }

        builder.registerFunctionRaw("mul") { args ->
            NumberValue(args.fold(1.0) { acc, v -> acc * (v as NumberValue).value })
        }

        builder.registerFunctionRaw("capture") { value ->
            results.add((value.first() as NumberValue).value)
            value.first()
        }

        val script = """
            // Higher-order function that takes a transformation function
            let transform = (f) => (x) => f(x)

            // Create transformers
            let addFive = transform((x) => add(x, 5))
            let multiplyByThree = transform((x) => mul(x, 3))

            // Compose functions manually
            let addFiveThenMultiplyByThree = (x) => multiplyByThree(addFive(x))

            capture(addFive(10))
            capture(multiplyByThree(10))
            capture(addFiveThenMultiplyByThree(10))

            // Nested closures with mutable state
            // Note: Without block bodies, we simulate mutable counter differently
            let makeAdder = (start) => (x) => add(start, x)
            let addTen = makeAdder(10)

            capture(addTen(5))
            capture(addTen(15))
            capture(addTen(25))
        """.trimIndent()

        val engine = builder.build()

        engine.execute(script, sourceName = "closures.klang")

        results shouldBe listOf(
            15.0,   // addFive(10) = 10 + 5
            30.0,   // multiplyByThree(10) = 10 * 3
            45.0,   // (10 + 5) * 3
            15.0,   // addTen(5) = 10 + 5
            25.0,   // addTen(15) = 10 + 15
            35.0    // addTen(25) = 10 + 25
        )
    }

    "Object literals, property access, and methods" {
        val builder = KlangScript.builder()
        val results = mutableListOf<String>()

        builder.registerFunctionRaw("capture") { value ->
            results.add(value.first().toDisplayString())
            value.first()
        }

        builder.registerFunctionRaw("add") { args ->
            NumberValue(args.sumOf { (it as NumberValue).value })
        }

        val script = """
            let person = {
                name: "Alice",
                age: 30,
                greet: (greeting) => greeting
            }

            capture(person.name)
            capture(person.age)
            capture(person.greet("Hello!"))

            // Nested objects
            let config = {
                audio: {
                    sampleRate: 48000,
                    channels: 2
                },
                video: {
                    resolution: "1920x1080"
                }
            }

            capture(config.audio.sampleRate)
            capture(config.audio.channels)
            capture(config.video.resolution)

            // Object with computed values
            let computed = {
                x: 10,
                y: 20,
                sum: add(10, 20)
            }

            capture(computed.sum)
        """.trimIndent()

        val engine = builder.build()

        engine.execute(script, sourceName = "objects.klang")

        // Check results - note that JS and JVM format numbers differently
        results.size shouldBe 7
        results[0] shouldBe "Alice"
        results[1] shouldContain "30"  // Could be "30" or "30.0"
        results[2] shouldBe "Hello!"
        results[3] shouldContain "48000"  // Could be "48000" or "48000.0"
        results[4] shouldContain "2"  // Could be "2" or "2.0"
        results[5] shouldBe "1920x1080"
        results[6] shouldContain "30"  // Could be "30" or "30.0"
    }

    "Error handling with stack traces" {
        val engine = klangScript {
            registerFunctionRaw("add") { args ->
                NumberValue(args.sumOf { (it as NumberValue).value })
            }
        }


        val script = """
            let innerFunc = (x) => add(x, nonExistentVariable)
            let middleFunc = (x) => innerFunc(x)
            let outerFunc = (x) => middleFunc(x)

            outerFunc(5)
        """.trimIndent()

        try {
            engine.execute(script, sourceName = "error.klang")
            throw AssertionError("Should have thrown ReferenceError")
        } catch (e: ReferenceError) {
            val formatted = e.format()

            // Should contain error type and location
            formatted shouldContain "ReferenceError"
            formatted shouldContain "error.klang"

            // Should contain stack trace with anonymous functions
            formatted shouldContain "at <anonymous>"
        }
    }

    "Multiple scripts with shared and isolated environments" {
        val builder = KlangScript.builder()
        val results = mutableListOf<Double>()

        builder.registerFunctionRaw("add") { args ->
            NumberValue(args.sumOf { (it as NumberValue).value })
        }

        builder.registerFunctionRaw("capture") { value ->
            results.add((value.first() as NumberValue).value)
            value.first()
        }

        val engine = builder.build()

        // First script defines a variable
        engine.execute(
            """
                let shared = 100
                capture(shared)
            """.trimIndent()
        )

        // Second script can access it (same environment)
        engine.execute(
            """
                capture(add(shared, 50))
            """.trimIndent()
        )

        results shouldBe listOf(100.0, 150.0)
    }

    "Complex arithmetic and operator precedence" {
        val builder = KlangScript.builder()
        val results = mutableListOf<Double>()

        builder.registerFunctionRaw("capture") { value ->
            val first = value.first()
            val numValue = when (first) {
                is NumberValue -> first.value
                is BooleanValue -> if (first.value) 1.0 else 0.0
                else -> throw IllegalArgumentException("Expected number or boolean")
            }
            results.add(numValue)
            first
        }

        val script = """
            capture(1 + 2 * 3)
            capture((1 + 2) * 3)
            capture(10 - 2 - 3)
            capture(10 / 2 / 5)
            capture(-5 + 3)
            capture(-(5 + 3))
            capture(!true)
            capture(!!true)
        """.trimIndent()

        val engine = builder.build()

        engine.execute(script, sourceName = "arithmetic.klang")

        results shouldBe listOf(
            7.0,    // 1 + (2 * 3)
            9.0,    // (1 + 2) * 3
            5.0,    // (10 - 2) - 3
            1.0,    // (10 / 2) / 5
            -2.0,   // -5 + 3
            -8.0,   // -(5 + 3)
            0.0,    // !true = false = 0
            1.0     // !!true = true = 1
        )
    }

    "Live coding pattern: Strudel-style musical sequencing" {
        val builder = KlangScript.builder()
        val sequence = mutableListOf<String>()

        // Register a note() function that returns a pattern object
        builder.registerFunctionRaw("note") { values ->
            val value = values[0]
            val pattern = (value as StringValue).value
            ObjectValue(
                mutableMapOf(
                    "_pattern" to StringValue(pattern),
                    "sound" to NativeFunctionValue("sound") { args ->
                        val sound = (args[0] as StringValue).value
                        sequence.add("note($pattern).sound($sound)")
                        ObjectValue(
                            mutableMapOf(
                                "_pattern" to StringValue(pattern),
                                "_sound" to StringValue(sound),
                                "gain" to NativeFunctionValue("gain") { gainArgs ->
                                    val gain = (gainArgs[0] as NumberValue).value
                                    sequence.add("note($pattern).sound($sound).gain($gain)")
                                    NullValue
                                }
                            )
                        )
                    },
                    "scale" to NativeFunctionValue("scale") { args ->
                        val scale = (args[0] as StringValue).value
                        sequence.add("note($pattern).scale($scale)")
                        NullValue
                    }
                )
            )
        }

        builder.registerFunctionRaw("s") { values ->
            val value = values[0]
            val sound = (value as StringValue).value
            sequence.add("s($sound)")
            NullValue
        }

        builder.registerLibrary(
            "patterns",
            """
                let chord = (name) => name
                export { chord }
            """.trimIndent()
        )

        val script = """
            import { chord } from "patterns"

            // Simple pattern
            note("a b c d").sound("piano").gain(0.8)

            // Scale application
            note("0 2 4 5").scale("C:major")

            // Using imported function
            let chordName = chord("Cm7")
            note(chordName).sound("synth")

            // Short-hand notation
            s("bd hh sd hh")
        """.trimIndent()

        val engine = builder.build()

        engine.execute(script, sourceName = "music.klang")

        sequence shouldBe listOf(
            "note(a b c d).sound(piano)",
            "note(a b c d).sound(piano).gain(0.8)",
            "note(0 2 4 5).scale(C:major)",
            "note(Cm7).sound(synth)",
            "s(bd hh sd hh)"
        )
    }
})
