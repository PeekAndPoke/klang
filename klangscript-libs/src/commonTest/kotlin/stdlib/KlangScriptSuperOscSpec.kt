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
 * Dual-language equivalence for the five unison stack doors, `Osc.supersaw`, `supersine`, `supersquare`,
 * `supertri` and `superramp`, and their five builder classes (`OscSuperSawBuilder` and its four siblings).
 *
 * The builders are five classes with the same knobs, so every row runs over every door. Each case is KlangScript
 * source run through the full engine (parse, interpret, native interop, the configure lambda floating into its
 * slot), compared against the Kotlin door's default node with the expected fields written by the node's own
 * data class `.copy()` ([SuperKnobs.onto]). The expectation never goes through a builder, so a knob writing the
 * wrong field, or a second one, is caught, and every case must differ from the defaults, so none passes vacuously.
 */
class KlangScriptSuperOscSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    val doors = listOf(
        SuperDoor("supersaw", { KlangScriptOsc.supersaw() }, { KlangScriptOsc.supersaw(configure = { it.voices(11).spread(0.12) }) }),
        SuperDoor("supersine", { KlangScriptOsc.supersine() }, { KlangScriptOsc.supersine(configure = { it.voices(11).spread(0.12) }) }),
        SuperDoor("supersquare", { KlangScriptOsc.supersquare() }, { KlangScriptOsc.supersquare(configure = { it.voices(11).spread(0.12) }) }),
        SuperDoor("supertri", { KlangScriptOsc.supertri() }, { KlangScriptOsc.supertri(configure = { it.voices(11).spread(0.12) }) }),
        SuperDoor("superramp", { KlangScriptOsc.superramp() }, { KlangScriptOsc.superramp(configure = { it.voices(11).spread(0.12) }) }),
    )

    "every calling form and every knob: script == the default node with the knob's own field written" {
        // The arguments inside `Osc.<door>(...)`, and the fields they must write.
        val cases = listOf(
            // freq is the door's first parameter, not a knob
            "220" to SuperKnobs(freq = c(220.0)),
            "x => x.voices(9)" to SuperKnobs(voices = c(9.0)),
            // voices accepts an Osc graph (control-rate)
            "x => x.voices(Osc.sine(0.5))" to SuperKnobs(voices = IgnitorDsl.Sine(freq = c(0.5))),
            "x => x.spread(0.3)" to SuperKnobs(spread = c(0.3)),
            "x => x.analog(5.0)" to SuperKnobs(analog = c(5.0)),
            // 0: the voices drift on ONE shared lane instead of their own
            "x => x.analogSpread(0)" to SuperKnobs(analogSpread = c(0.0)),
            "x => x.spreadPower(1.5)" to SuperKnobs(spreadPower = 1.5),
            "x => x.sideAtten(0.25)" to SuperKnobs(sideAtten = 0.25),
            "x => x.gainJitter(0.0)" to SuperKnobs(gainJitter = 0.0),
            // the knob's name is centerJitter, the field's centerJitterScale
            "x => x.centerJitter(1.0)" to SuperKnobs(centerJitterScale = 1.0),
            // on with the family's defaults: the sync guard, each builder's phasePool literals == its node's defaults
            "x => x.phasePool()" to SuperKnobs(phasePool = 1.0),
            "x => x.phasePool(on = 0, kMin = 0.2)" to SuperKnobs(phasePool = 0.0, kMin = 0.2),
            // a named arg skips the leading literal defaults
            "x => x.phasePool(refreshEvery = 0)" to SuperKnobs(phasePool = 1.0, refreshEvery = 0.0),
            // a named configure binds too
            "configure = x => x.voices(3)" to SuperKnobs(voices = c(3.0)),
            // every knob in one lambda, freq on the door, every value distinct so no two knobs can trade places;
            // phasePool is positional: on, kMin, kMax, drawTries, poolSize, refreshEvery, selection, warmup
            "110, x => x.voices(11).spread(0.12).analog(4.0).analogSpread(0.25)" +
                    ".spreadPower(1.4).sideAtten(0.2).gainJitter(0.1).centerJitter(0.6)" +
                    ".phasePool(1, 0.21, 0.7, 8, 64, 5, \"random\", 9)" to SuperKnobs(
                freq = c(110.0),
                voices = c(11.0),
                spread = c(0.12),
                analog = c(4.0),
                analogSpread = c(0.25),
                spreadPower = 1.4,
                sideAtten = 0.2,
                gainJitter = 0.1,
                centerJitterScale = 0.6,
                phasePool = 1.0,
                kMin = 0.21,
                kMax = 0.7,
                drawTries = 8.0,
                poolSize = 64.0,
                refreshEvery = 5.0,
                selection = "random",
                warmup = 9.0,
            ),
        )

        for (door in doors) {
            val default = door.kotlin()

            withClue("Osc.${door.name}(): script == Kotlin door, all defaults") {
                ks("Osc.${door.name}()") shouldBe default
            }

            for ((args, knobs) in cases) {
                val expected = knobs.onto(default)

                withClue("Osc.${door.name}($args)") {
                    expected shouldNotBe default
                    ks("Osc.${door.name}($args)") shouldBe expected
                }
            }
        }
    }

    "the Kotlin door takes the same lambda" {
        for (door in doors) {
            withClue(door.name) {
                ks("Osc.${door.name}(x => x.voices(11).spread(0.12))") shouldBe door.kotlinConfigured()
                door.kotlinConfigured() shouldBe SuperKnobs(voices = c(11.0), spread = c(0.12)).onto(door.kotlin())
            }
        }
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        for (door in doors) {
            withClue(door.name) {
                val dsl = ks("Osc.${door.name}(x => x.spreadPower(1.5)).lowpass(2000)")
                dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
                dsl.inner shouldBe SuperKnobs(spreadPower = 1.5).onto(door.kotlin())
            }
        }
    }

    "a lambda that returns nothing, or something else, is a script-level type error naming the door" {
        for (door in doors) {
            withClue(door.name) {
                val err = shouldThrow<KlangScriptTypeError> { ks("Osc.${door.name}(x => { x.voices(3) })") }
                err.message shouldBe "the configure lambda of Osc.${door.name} returned nothing; " +
                        "return the builder it received (`x => x.analog(3)`)"

                shouldThrow<KlangScriptTypeError> { ks("Osc.${door.name}(x => 5)") }
            }
        }
    }
})

/** One stack door: its script name, its Kotlin door, and the Kotlin door with the lambda the script rows use. */
private class SuperDoor(
    val name: String,
    val kotlin: () -> IgnitorDsl,
    val kotlinConfigured: () -> IgnitorDsl,
)

/** The fields of a unison stack node a case expects written; null keeps the node's own value. */
private data class SuperKnobs(
    val freq: IgnitorDsl? = null,
    val voices: IgnitorDsl? = null,
    val spread: IgnitorDsl? = null,
    val analog: IgnitorDsl? = null,
    val analogSpread: IgnitorDsl? = null,
    val spreadPower: Double? = null,
    val sideAtten: Double? = null,
    val gainJitter: Double? = null,
    val centerJitterScale: Double? = null,
    val phasePool: Double? = null,
    val drawTries: Double? = null,
    val kMin: Double? = null,
    val kMax: Double? = null,
    val poolSize: Double? = null,
    val refreshEvery: Double? = null,
    val selection: String? = null,
    val warmup: Double? = null,
) {
    /** [node] with these fields written through its own data class `.copy()`, never through a builder. */
    fun onto(node: IgnitorDsl): IgnitorDsl = when (node) {
        is IgnitorDsl.SuperSaw -> node.copy(
            freq = freq ?: node.freq, voices = voices ?: node.voices, spread = spread ?: node.spread,
            analog = analog ?: node.analog, analogSpread = analogSpread ?: node.analogSpread,
            spreadPower = spreadPower ?: node.spreadPower, sideAtten = sideAtten ?: node.sideAtten,
            gainJitter = gainJitter ?: node.gainJitter, centerJitterScale = centerJitterScale ?: node.centerJitterScale,
            phasePool = phasePool ?: node.phasePool, drawTries = drawTries ?: node.drawTries,
            kMin = kMin ?: node.kMin, kMax = kMax ?: node.kMax, poolSize = poolSize ?: node.poolSize,
            refreshEvery = refreshEvery ?: node.refreshEvery, selection = selection ?: node.selection,
            warmup = warmup ?: node.warmup,
        )

        is IgnitorDsl.SuperSine -> node.copy(
            freq = freq ?: node.freq, voices = voices ?: node.voices, spread = spread ?: node.spread,
            analog = analog ?: node.analog, analogSpread = analogSpread ?: node.analogSpread,
            spreadPower = spreadPower ?: node.spreadPower, sideAtten = sideAtten ?: node.sideAtten,
            gainJitter = gainJitter ?: node.gainJitter, centerJitterScale = centerJitterScale ?: node.centerJitterScale,
            phasePool = phasePool ?: node.phasePool, drawTries = drawTries ?: node.drawTries,
            kMin = kMin ?: node.kMin, kMax = kMax ?: node.kMax, poolSize = poolSize ?: node.poolSize,
            refreshEvery = refreshEvery ?: node.refreshEvery, selection = selection ?: node.selection,
            warmup = warmup ?: node.warmup,
        )

        is IgnitorDsl.SuperSquare -> node.copy(
            freq = freq ?: node.freq, voices = voices ?: node.voices, spread = spread ?: node.spread,
            analog = analog ?: node.analog, analogSpread = analogSpread ?: node.analogSpread,
            spreadPower = spreadPower ?: node.spreadPower, sideAtten = sideAtten ?: node.sideAtten,
            gainJitter = gainJitter ?: node.gainJitter, centerJitterScale = centerJitterScale ?: node.centerJitterScale,
            phasePool = phasePool ?: node.phasePool, drawTries = drawTries ?: node.drawTries,
            kMin = kMin ?: node.kMin, kMax = kMax ?: node.kMax, poolSize = poolSize ?: node.poolSize,
            refreshEvery = refreshEvery ?: node.refreshEvery, selection = selection ?: node.selection,
            warmup = warmup ?: node.warmup,
        )

        is IgnitorDsl.SuperTri -> node.copy(
            freq = freq ?: node.freq, voices = voices ?: node.voices, spread = spread ?: node.spread,
            analog = analog ?: node.analog, analogSpread = analogSpread ?: node.analogSpread,
            spreadPower = spreadPower ?: node.spreadPower, sideAtten = sideAtten ?: node.sideAtten,
            gainJitter = gainJitter ?: node.gainJitter, centerJitterScale = centerJitterScale ?: node.centerJitterScale,
            phasePool = phasePool ?: node.phasePool, drawTries = drawTries ?: node.drawTries,
            kMin = kMin ?: node.kMin, kMax = kMax ?: node.kMax, poolSize = poolSize ?: node.poolSize,
            refreshEvery = refreshEvery ?: node.refreshEvery, selection = selection ?: node.selection,
            warmup = warmup ?: node.warmup,
        )

        is IgnitorDsl.SuperRamp -> node.copy(
            freq = freq ?: node.freq, voices = voices ?: node.voices, spread = spread ?: node.spread,
            analog = analog ?: node.analog, analogSpread = analogSpread ?: node.analogSpread,
            spreadPower = spreadPower ?: node.spreadPower, sideAtten = sideAtten ?: node.sideAtten,
            gainJitter = gainJitter ?: node.gainJitter, centerJitterScale = centerJitterScale ?: node.centerJitterScale,
            phasePool = phasePool ?: node.phasePool, drawTries = drawTries ?: node.drawTries,
            kMin = kMin ?: node.kMin, kMax = kMax ?: node.kMax, poolSize = poolSize ?: node.poolSize,
            refreshEvery = refreshEvery ?: node.refreshEvery, selection = selection ?: node.selection,
            warmup = warmup ?: node.warmup,
        )

        else -> error("not a unison stack node: $node")
    }
}
