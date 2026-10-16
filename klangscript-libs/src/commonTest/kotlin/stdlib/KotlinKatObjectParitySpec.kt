/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The typed Kotlin `Kat` object (`audio_bridge`, step 1b of `docs/tasks/kotlin-typed-doors.md`, Q46) against the
 * KlangScript `Kat`: for every function the typed object mirrors, the script call and the Kotlin call build equal
 * values, with the defaults, with numbers, with a signal, and with every builder knob (the script's `configure` lambda,
 * the Kotlin door's named parameters). The chain door (`Kat.build`, `Kat(...)`) is step 4 and not mirrored yet.
 */
class KotlinKatObjectParitySpec : StringSpec({

    fun ks(code: String): Any {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<Any>()!!
    }

    val lfo = "Kat.perlin(0.5)"
    val lfoK = Kat.perlin(0.5.kat)

    val rows: List<Pair<String, () -> Any>> = listOf(
        "Kat.classic()" to { Kat.classic() },
        "Kat.param(\"depth\", 0.4)" to { Kat.param("depth", 0.4) },
        "Kat.param(\"depth\", 0.4, \"how deep\")" to { Kat.param("depth", 0.4, "how deep") },

        "Kat.sine(0.7)" to { Kat.sine(0.7.kat) },
        "Kat.sine($lfo)" to { Kat.sine(lfoK) },
        "Kat.sine(0.7, x => x.phase(0.25))" to { Kat.sine(freq = 0.7.kat, phase = 0.25.kat) },
        "Kat.sine(0.7, x => x.phase($lfo))" to { Kat.sine(freq = 0.7.kat, phase = lfoK) },
        "Kat.saw(0.7)" to { Kat.saw(0.7.kat) },
        "Kat.saw(0.7, x => x.phase(0.25))" to { Kat.saw(freq = 0.7.kat, phase = 0.25.kat) },
        "Kat.saw($lfo)" to { Kat.saw(lfoK) },
        "Kat.ramp(0.7)" to { Kat.ramp(0.7.kat) },
        "Kat.ramp(0.7, x => x.phase(0.25))" to { Kat.ramp(freq = 0.7.kat, phase = 0.25.kat) },
        "Kat.tri(0.7)" to { Kat.tri(0.7.kat) },
        "Kat.tri(0.7, x => x.phase(0.25))" to { Kat.tri(freq = 0.7.kat, phase = 0.25.kat) },
        "Kat.square(0.7)" to { Kat.square(0.7.kat) },
        "Kat.square(0.7, x => x.duty(0.3).phase(0.25))" to { Kat.square(freq = 0.7.kat, duty = 0.3.kat, phase = 0.25.kat) },
        "Kat.square($lfo, x => x.duty($lfo))" to { Kat.square(freq = lfoK, duty = lfoK) },

        "Kat.perlin(0.5)" to { Kat.perlin(0.5.kat) },
        "Kat.perlin(0.5, 4, 0.7)" to { Kat.perlin(0.5.kat, 4.kat, 0.7.kat) },
        "Kat.perlin(rate = Kat.sine(0.1), persistence = 0.7)" to { Kat.perlin(rate = Kat.sine(0.1.kat), persistence = 0.7.kat) },
        "Kat.berlin(0.5)" to { Kat.berlin(0.5.kat) },
        "Kat.berlin(0.5, 4, 0.7)" to { Kat.berlin(0.5.kat, 4.kat, 0.7.kat) },
        "Kat.berlin(rate = 0.5, octaves = $lfo)" to { Kat.berlin(rate = 0.5.kat, octaves = lfoK) },
    )

    rows.forEach { (code, kotlin) ->
        "$code equals the Kotlin Kat call" {
            withClue(code) {
                ks(code) shouldBe kotlin()
            }
        }
    }

    "Kat.slot is the script's Kat.slot, leaf for leaf" {
        Kat.slot shouldBeSameInstanceAs KatalystDsl.Slots
        ks("Kat.slot.reverb.wet") shouldBeSameInstanceAs Kat.slot.reverb.wet
    }
})
