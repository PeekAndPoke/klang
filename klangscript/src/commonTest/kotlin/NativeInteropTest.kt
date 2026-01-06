package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.runtime.TypeError

/**
 * Mock native class for testing
 */
class StrudelPattern(val pattern: String) {
    fun sound(soundName: String): StrudelPattern {
        return StrudelPattern("$pattern|sound:$soundName")
    }

    fun gain(amount: Double): StrudelPattern {
        return StrudelPattern("$pattern|gain:$amount")
    }

    fun reverse(): StrudelPattern {
        return StrudelPattern("$pattern|reversed")
    }

    fun pan(left: Double, right: Double): StrudelPattern {
        return StrudelPattern("$pattern|pan:$left,$right")
    }

    override fun toString(): String = pattern
}

/**
 * Native Kotlin interop tests
 *
 * Tests the integration between KlangScript and native Kotlin classes,
 * including extension method registration, method chaining, and type conversion.
 */
class NativeInteropTest : StringSpec({

    "Native object creation via registered function" {
        val engine = KlangScript()

        // Register factory function that returns native object
        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        val script = "note(\"a b c d\")"
        val result = engine.execute(script)

        result.toDisplayString() shouldContain "a b c d"
    }

    "Extension method with one parameter" {
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
        }

        val script = """
            let pattern = note("a b c d")
            pattern.sound("saw")
        """.trimIndent()

        val result = engine.execute(script)
        result.toDisplayString() shouldContain "a b c d|sound:saw"
    }

    "Extension method with no parameters" {
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod0<StrudelPattern, StrudelPattern>("reverse") { receiver ->
            receiver.reverse()
        }

        val script = """
            let pattern = note("a b c d")
            pattern.reverse()
        """.trimIndent()

        val result = engine.execute(script)
        result.toDisplayString() shouldContain "reversed"
    }

    "Extension method with two parameters" {
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod2<StrudelPattern, Double, Double, StrudelPattern>("pan") { receiver, left, right ->
            receiver.pan(left, right)
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
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
        }

        engine.registerExtensionMethod1<StrudelPattern, Double, StrudelPattern>("gain") { receiver, amount ->
            receiver.gain(amount)
        }

        // The exact use case from the design discussion!
        val script = """note("a b c d").sound("saw").gain(0.8)"""

        val result = engine.execute(script)
        result.toDisplayString() shouldContain "a b c d|sound:saw|gain:0.8"
    }

    "Method chaining - long chain" {
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
        }

        engine.registerExtensionMethod1<StrudelPattern, Double, StrudelPattern>("gain") { receiver, amount ->
            receiver.gain(amount)
        }

        engine.registerExtensionMethod0<StrudelPattern, StrudelPattern>("reverse") { receiver ->
            receiver.reverse()
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
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
        }

        val script = """
            let pattern = note("a b c d")
            pattern.nonExistent("test")
        """.trimIndent()

        val error = shouldThrow<TypeError> {
            engine.execute(script)
        }

        error.message shouldContain "has no method 'nonExistent'"
        error.message shouldContain "StrudelPattern"
        // Should suggest available methods
        error.message shouldContain "sound"
    }

    "Error - wrong argument count" {
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
        }

        val script = """
            let pattern = note("a b c d")
            pattern.sound("saw", "extra")
        """.trimIndent()

        shouldThrow<TypeError> {
            engine.execute(script)
        }
    }

    "Native objects in variables" {
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
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
        val engine = KlangScript()
        val captured = mutableListOf<String>()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerFunction("capture") { args ->
            captured.add(args[0].toDisplayString())
            args[0]
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
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
        val engine = KlangScript()

        // Register first type
        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
        }

        // Register second type (simulate another native class)
        engine.registerNativeFunction<String, StrudelPattern>("rhythm") { pattern ->
            StrudelPattern("rhythm:$pattern")
        }

        engine.registerExtensionMethod1<StrudelPattern, Double, StrudelPattern>("speed") { receiver, factor ->
            StrudelPattern("${receiver.pattern}|speed:$factor")
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
        val engine = KlangScript()

        engine.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
            StrudelPattern(pattern)
        }

        engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
            receiver.sound(soundName)
        }

        engine.registerExtensionMethod1<StrudelPattern, Double, StrudelPattern>("gain") { receiver, amount ->
            receiver.gain(amount)
        }

        // Register library that uses native objects
        engine.registerLibrary(
            "patterns",
            """
            let makePattern = (notes) => note(notes).sound("saw")
            let withGain = (pattern, amount) => pattern.gain(amount)

            export { makePattern, withGain }
            """.trimIndent()
        )

        val script = """
            import { makePattern, withGain } from "patterns"

            let pattern = makePattern("a b c d")
            withGain(pattern, 0.8)
        """.trimIndent()

        val result = engine.execute(script)
        result.toDisplayString() shouldContain "gain:0.8"
    }
})
