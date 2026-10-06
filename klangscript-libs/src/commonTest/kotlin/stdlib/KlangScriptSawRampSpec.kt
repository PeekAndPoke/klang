/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Dual-language equivalence for the `Ignitor.saw(freq, configure)` and `Ignitor.ramp(freq, configure)` doors and their
 * builders, `OscSawBuilder` and `OscRampBuilder`: two classes with the same three knobs, so every row runs over both.
 *
 * Script source vs the Kotlin door's default node with the expected fields written by the node's own data class
 * `.copy()` ([SawRampKnobs.onto]), never through a builder, so a knob writing the wrong field, or a second one, is
 * caught; every case must differ from the defaults.
 */
class KlangScriptSawRampSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    val doors = listOf(
        SawRampDoor("saw", { KlangScriptIgnitor.saw() }, { KlangScriptIgnitor.saw(configure = { it.shapeMax(0.3) }) }),
        SawRampDoor("ramp", { KlangScriptIgnitor.ramp() }, { KlangScriptIgnitor.ramp(configure = { it.shapeMax(0.3) }) }),
    )

    "every calling form and every knob: script == the default node with the knob's own field written" {
        // The arguments inside `Ignitor.<door>(...)`, and the fields they must write.
        val cases = listOf(
            // freq is the door's first parameter, not a knob
            "220" to SawRampKnobs(freq = c(220.0)),
            "x => x.analog(5.0)" to SawRampKnobs(analog = c(5.0)),
            "x => x.resetSamples(4.0)" to SawRampKnobs(resetSamples = 4.0),
            "x => x.shapeMax(0.3)" to SawRampKnobs(shapeMax = 0.3),
            // every knob in one lambda, every value distinct so no two knobs can trade places
            "110, x => x.analog(5.0).resetSamples(4.0).shapeMax(0.3)" to SawRampKnobs(
                freq = c(110.0),
                analog = c(5.0),
                resetSamples = 4.0,
                shapeMax = 0.3,
            ),
        )

        for (door in doors) {
            val default = door.kotlin()

            withClue("Ignitor.${door.name}(): script == Kotlin door, all defaults") {
                ks("Ignitor.${door.name}()") shouldBe default
            }

            for ((args, knobs) in cases) {
                val expected = knobs.onto(default)

                withClue("Ignitor.${door.name}($args)") {
                    expected shouldNotBe default
                    ks("Ignitor.${door.name}($args)") shouldBe expected
                }
            }
        }
    }

    "the Kotlin door takes the same lambda" {
        for (door in doors) {
            withClue(door.name) {
                ks("Ignitor.${door.name}(x => x.shapeMax(0.3))") shouldBe door.kotlinConfigured()
                door.kotlinConfigured() shouldBe SawRampKnobs(shapeMax = 0.3).onto(door.kotlin())
            }
        }
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        for (door in doors) {
            withClue(door.name) {
                val dsl = ks("Ignitor.${door.name}(x => x.shapeMax(0.3)).lowpass(2000)")
                dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
                dsl.inner shouldBe SawRampKnobs(shapeMax = 0.3).onto(door.kotlin())
            }
        }
    }

    "a lambda that returns nothing is a script-level type error" {
        for (door in doors) {
            withClue(door.name) {
                shouldThrow<KlangScriptTypeError> { ks("Ignitor.${door.name}(x => { x.shapeMax(0.3) })") }
            }
        }
    }
})

/** One door: its script name, its Kotlin door, and the Kotlin door with the lambda the script rows use. */
private class SawRampDoor(
    val name: String,
    val kotlin: () -> IgnitorDsl,
    val kotlinConfigured: () -> IgnitorDsl,
)

/** The fields of a saw or ramp node a case expects written; null keeps the node's own value. */
private data class SawRampKnobs(
    val freq: IgnitorDsl? = null,
    val analog: IgnitorDsl? = null,
    val resetSamples: Double? = null,
    val shapeMax: Double? = null,
) {
    /** [node] with these fields written through its own data class `.copy()`, never through a builder. */
    fun onto(node: IgnitorDsl): IgnitorDsl = when (node) {
        is IgnitorDsl.Saw -> node.copy(
            freq = freq ?: node.freq,
            analog = analog ?: node.analog,
            resetSamples = resetSamples ?: node.resetSamples,
            shapeMax = shapeMax ?: node.shapeMax,
        )

        is IgnitorDsl.Ramp -> node.copy(
            freq = freq ?: node.freq,
            analog = analog ?: node.analog,
            resetSamples = resetSamples ?: node.resetSamples,
            shapeMax = shapeMax ?: node.shapeMax,
        )

        else -> error("not a saw or ramp node: $node")
    }
}
