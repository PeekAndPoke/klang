/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptReferenceError
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * The master doors: `Master(configure)` (the `invoke` operator), its alias `Master.build`, and
 * `Master.default()`. Decision D8 of `docs/tasks/dsl-configure-lambdas.md`: the callable form and
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
        val code = "m => m.reverb(r => r.wet(0.05).damp(0.5).roomSize(9)).gain(2.5).limiter(l => l.thresholdDb(-3))"
        ks("Master($code)") shouldBe ks("Master.build($code)")
    }

    "stages append in written order, script == Kotlin data classes" {
        ks("Master(m => m.reverb(r => r.wet(0.05).damp(0.5).roomSize(9)).gain(2.5).limiter())") shouldBe MasterDsl.of(
            MasterStageDsl.Reverb(wet = 0.05, damp = 0.5, roomSize = 9.0),
            MasterStageDsl.Gain(gain = 2.5),
            MasterStageDsl.Limiter(),
        )
    }

    "every limiter knob" {
        ks("Master(m => m.limiter(l => l.thresholdDb(-0.5).ratio(4).kneeDb(1).attack(0.01).lookahead(0.005).release(0.2)))") shouldBe
                MasterDsl.of(
                    MasterStageDsl.Limiter(
                        thresholdDb = -0.5, ratio = 4.0, kneeDb = 1.0,
                        attackSeconds = 0.01, lookaheadSeconds = 0.005, releaseSeconds = 0.2,
                    )
                )
    }

    "every reverb and delay knob" {
        ks("Master(m => m.reverb(r => r.wet(0.3).roomSize(8).damp(0.4).roomFade(0.12).roomLp(6000)).delay(d => d.wet(0.2).time(0.5).feedback(1.0).cap(3.0)))") shouldBe
                MasterDsl.of(
                    MasterStageDsl.Reverb(wet = 0.3, roomSize = 8.0, damp = 0.4, roomFade = 0.12, roomLp = 6000.0),
                    MasterStageDsl.Delay(wet = 0.2, timeSeconds = 0.5, feedback = 1.0, cap = 3.0),
                )
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
        val err = shouldThrow<KlangScriptTypeError> { ks("Master(m => m.limiter(l => { l.ratio(4) }))") }
        err.message shouldBe "the configure lambda of Master limiter returned nothing; return the builder it received (`x => x.analog(3)`)"
    }

    "Master.of and MasterFx are gone" {
        shouldThrow<KlangScriptTypeError> { ks("Master.of()") }
        shouldThrow<KlangScriptReferenceError> { ks("MasterFx.gain(2)") }
    }
})
