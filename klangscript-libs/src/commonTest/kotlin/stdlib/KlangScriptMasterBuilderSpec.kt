/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * One Master stage door: its parameters as (name, a value unlike the bare stage's own), a writer
 * that sets one field by its door-parameter name on the bare data class, and the Kotlin door
 * called with only the parameters in its map (a missing key is an omitted parameter).
 */
private class MasterDoor(
    val stage: String,
    val bare: MasterStageDsl,
    val params: List<Pair<String, Double>>,
    val set: (MasterStageDsl, String, Double) -> MasterStageDsl,
    val kotlin: (MasterBuilder, Map<String, Double>) -> MasterBuilder,
) {
    fun expect(written: List<Pair<String, Double>>): MasterStageDsl =
        written.fold(bare) { stage, (name, value) -> set(stage, name, value) }
}

private fun unknownMaster(stage: String, name: String): Nothing = error("$stage has no door parameter '$name'")

/** The whole family of Master stage doors with parameters: every door-shape row loops over it. */
private val masterDoors: List<MasterDoor> = listOf(
    MasterDoor(
        stage = "limiter",
        bare = MasterStageDsl.Limiter(),
        // The door's order: threshold, ratio, knee, attack, lookahead, release.
        params = listOf(
            "threshold" to -0.5, "ratio" to 4.0, "knee" to 1.0,
            "attack" to 0.01, "lookahead" to 0.005, "release" to 0.2,
        ),
        set = { s, n, v ->
            val l = s as MasterStageDsl.Limiter
            when (n) {
                "threshold" -> l.copy(threshold = v)
                "ratio" -> l.copy(ratio = v)
                "knee" -> l.copy(knee = v)
                "attack" -> l.copy(attackSeconds = v)
                "lookahead" -> l.copy(lookaheadSeconds = v)
                "release" -> l.copy(releaseSeconds = v)
                else -> unknownMaster("limiter", n)
            }
        },
        kotlin = { m, a ->
            m.limiter(
                threshold = a["threshold"], ratio = a["ratio"], knee = a["knee"],
                attack = a["attack"], lookahead = a["lookahead"], release = a["release"],
            )
        },
    ),
    MasterDoor(
        stage = "reverb",
        bare = MasterStageDsl.Reverb(),
        params = listOf("wet" to 0.3, "size" to 8.0, "lowpass" to 6000.0),
        set = { s, n, v ->
            val r = s as MasterStageDsl.Reverb
            when (n) {
                "wet" -> r.copy(wet = v)
                "size" -> r.copy(size = v)
                "lowpass" -> r.copy(lowpass = v)
                else -> unknownMaster("reverb", n)
            }
        },
        kotlin = { m, a -> m.reverb(wet = a["wet"], size = a["size"], lowpass = a["lowpass"]) },
    ),
    MasterDoor(
        stage = "delay",
        bare = MasterStageDsl.Delay(),
        params = listOf("wet" to 0.2, "time" to 0.5, "feedback" to 0.9),
        set = { s, n, v ->
            val d = s as MasterStageDsl.Delay
            when (n) {
                "wet" -> d.copy(wet = v)
                "time" -> d.copy(time = v)
                "feedback" -> d.copy(feedback = v)
                else -> unknownMaster("delay", n)
            }
        },
        kotlin = { m, a -> m.delay(wet = a["wet"], time = a["time"], feedback = a["feedback"]) },
    ),
)

/**
 * The master doors: `Master(configure)` (the `invoke` operator), its alias `Master.build`, and
 * `Master.default()`. Decision D8 of `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`: the callable form and
 * the method form are aliases, pinned here node for node, so the operator is tested against a
 * form that works without it. The Kotlin side is `MasterDsl.of(...)` with the stage data classes.
 */
class KlangScriptMasterBuilderSpec : StringSpec({

    fun ks(code: String): MasterDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<MasterDsl>()
    }

    "Master() is the unity chain, same as Master.default()" {
        ks("Master()") shouldBe MasterDsl.default
        ks("Master.default()") shouldBe MasterDsl.default
        ks("Master()") shouldBe ks("Master.default()")
    }

    "Master(m => ...) == Master.build(m => ...), node for node" {
        val code = "m => m.reverb(0.05, 9).gain(2.5).limiter(threshold = -3)"
        ks("Master($code)") shouldBe ks("Master.build($code)")
    }

    "stages append in written order, script == Kotlin data classes" {
        ks("Master(m => m.reverb(0.05, 9).gain(2.5).limiter())") shouldBe MasterDsl.of(
            MasterStageDsl.Reverb(wet = 0.05, size = 9.0),
            MasterStageDsl.Gain(gain = 2.5),
            MasterStageDsl.Limiter(),
        )
    }

    // ── The door shapes (phase 3 step 3d(ii), `docs/tasks/builtin-instruments.md` section 3b) ──
    //
    // Every row LOOPS over [masterDoors] and compares three forms: the script door, the Kotlin door
    // (the same builder function called from Kotlin) and the stage data class written by hand.

    "every door parameter reaches its own field, positionally, on both doors" {
        masterDoors.forEach { door ->
            val args = door.params.joinToString(", ") { it.second.toString() }
            val expected = door.expect(door.params)

            withClue("${door.stage}: script, positional") {
                ks("Master(m => m.${door.stage}($args))") shouldBe MasterDsl.of(expected)
            }
            withClue("${door.stage}: Kotlin") {
                KlangScriptMaster.build { door.kotlin(it, door.params.toMap()) } shouldBe MasterDsl.of(expected)
            }
        }
    }

    "an omitted door parameter is exactly what the bare stage carries, on both doors" {
        // The identity rule of step 3d(ii): leaving a parameter out means what leaving the builder
        // knob out meant before, the bare data class's default. Each parameter is left out once,
        // with every other one written.
        masterDoors.forEach { door ->
            door.params.forEach { omitted ->
                val written = door.params - omitted
                val args = written.joinToString(", ") { "${it.first} = ${it.second}" }
                val expected = door.expect(written)

                withClue("${door.stage} without ${omitted.first}: script") {
                    ks("Master(m => m.${door.stage}($args))") shouldBe MasterDsl.of(expected)
                }
                withClue("${door.stage} without ${omitted.first}: Kotlin") {
                    KlangScriptMaster.build { door.kotlin(it, written.toMap()) } shouldBe MasterDsl.of(expected)
                }
            }

            withClue("${door.stage}: nothing written is the bare stage") {
                ks("Master(m => m.${door.stage}())") shouldBe MasterDsl.of(door.bare)
                KlangScriptMaster.build { door.kotlin(it, emptyMap()) } shouldBe MasterDsl.of(door.bare)
            }
        }
    }

    "the delay's cap is the one builder knob, on both doors" {
        ks("Master(m => m.delay(0.2, d => d.cap(3.0)))") shouldBe
                MasterDsl.of(MasterStageDsl.Delay(wet = 0.2, cap = 3.0))
        KlangScriptMaster.build { it.delay(0.2, configure = { d -> d.cap(3.0) }) } shouldBe
                MasterDsl.of(MasterStageDsl.Delay(wet = 0.2, cap = 3.0))
    }

    "the retired forms fail loudly instead of meaning something else" {
        listOf(
            "Master(m => m.reverb(r => r.wet(0.05)))" to "expected Double, got a function",
            "Master(m => m.limiter(l => l.threshold(-3)))" to "expected Double, got a function",
            "Master(m => m.delay(d => d.wet(0.2)))" to "has no method 'wet'",
            "Master(m => m.limiter(thresholdDb = -3))" to "unknown parameter 'thresholdDb'",
            "Master(m => m.limiter(kneeDb = 1))" to "unknown parameter 'kneeDb'",
        ).forEach { (script, reason) ->
            withClue(script) { shouldThrowAny { ks(script) }.message shouldContain reason }
        }
    }

    "gain takes its value directly, twice in a chain is two stages" {
        ks("Master(m => m.gain(1.45).gain(1.4))") shouldBe MasterDsl.of(MasterStageDsl.Gain(1.45), MasterStageDsl.Gain(1.4))
    }

    "the Kotlin door takes the same lambda" {
        ks("Master(m => m.gain(2.5).limiter())") shouldBe KlangScriptMaster.build { it.gain(2.5).limiter() }
    }

    "Master is still a value: stored, then called" {
        ks("let M = Master\nM(m => m.gain(2))") shouldBe MasterDsl.of(MasterStageDsl.Gain(2.0))
    }

    "a lambda that returns nothing is a script-level type error naming the door" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Master(m => { m.gain(2) })") }
        err.message shouldBe "the configure lambda of Master returned nothing; return the builder it received (`x => x.analog(3)`)"
    }

    "a stage lambda that returns nothing names its stage" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Master(m => m.delay(configure = d => { d.cap(2) }))") }
        err.message shouldBe "the configure lambda of Master delay returned nothing; return the builder it received (`x => x.analog(3)`)"
    }
})
