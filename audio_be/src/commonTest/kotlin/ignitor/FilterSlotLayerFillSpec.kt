/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass

/**
 * THE FILTER ENVELOPE'S SLOT-LAYER FILL, one row per term (phase 3 step 5, maintainer 2026-09-25; the
 * rule is `slotLayerDepth` in `IgnitorDslRuntime`). A slotted filter never calls the door per note, so
 * the build asks the door's question against the bag: an UNSET depth slot (a non-finite default, as
 * `classic()` places it) that the bag did not write takes the shared depth when ANY of the four stage
 * knobs is written; a written depth and an authored default, 0 included, always stand.
 *
 * The ORACLES are the door itself and the plain filter. A stage knob written alone must render bit
 * for bit what the door renders when a call names that one knob (the door's compound fill supplies
 * the depth and the other stages from the same constants); a term that must NOT switch the envelope on
 * must render the static filter. Every row also asserts the OTHER outcome is different on its input, so
 * a misfire in either direction is visible (the sustain of 1.0 holds the sweep's full 7 semitones for
 * the whole note, so even a release-only write moves the render from the first block).
 */
class FilterSlotLayerFillSpec : StringSpec({

    val saw = IgnitorDsl.Sawtooth()

    /** A lowpass whose five envelope knobs are all slots, as `classic()` places them. */
    fun slotted(
        env: IgnitorDsl = IgnitorDsl.Param("e", SLOT_UNSET),
        attack: IgnitorDsl = IgnitorDsl.Param("a", FILTER_ENV_ATTACK_SEC),
        decay: IgnitorDsl = IgnitorDsl.Param("d", FILTER_ENV_DECAY_SEC),
        sustain: IgnitorDsl = IgnitorDsl.Param("s", FILTER_ENV_SUSTAIN_LEVEL),
        release: IgnitorDsl = IgnitorDsl.Param("r", FILTER_ENV_RELEASE_SEC),
    ): IgnitorDsl = IgnitorDsl.Lowpass(
        inner = saw,
        freq = IgnitorDsl.Constant(800.0),
        env = env,
        attackSec = attack,
        decaySec = decay,
        sustainLevel = sustain,
        releaseSec = release,
    )

    val static: DoubleArray by lazy { renderVoiceWindows(saw.lowpass(800.0)) }
    val filledSeven: DoubleArray by lazy { renderVoiceWindows(saw.lowpass(800.0, attackSec = FILTER_ENV_ATTACK_SEC)) }

    fun shouldRenderStatic(clue: String, actual: DoubleArray) {
        withClue("$clue: renders the static filter") { firstBitMismatch(static, actual) shouldBe -1 }
        withClue("$clue: the fill would have been audible here") { firstBitMismatch(filledSeven, actual) shouldNotBe -1 }
    }

    fun shouldRenderDoor(clue: String, door: DoubleArray, actual: DoubleArray) {
        withClue("$clue: renders what the door renders") { firstBitMismatch(door, actual) shouldBe -1 }
        withClue("$clue: and that is not the static filter") { firstBitMismatch(static, actual) shouldNotBe -1 }
    }

    "none written: no envelope" {
        shouldRenderStatic("none written", renderVoiceWindows(slotted(), emptyMap()))
        shouldRenderStatic("no bag at all", renderVoiceWindows(slotted(), null))
    }

    "attack written alone: the depth fills with the shared 7 semitones, as `lowpass(800, attackSec = x)` does" {
        shouldRenderDoor(
            "attack", renderVoiceWindows(saw.lowpass(800.0, attackSec = 0.05)),
            renderVoiceWindows(slotted(), mapOf("a" to 0.05)),
        )
    }

    "decay written alone: the same fill" {
        shouldRenderDoor(
            "decay", renderVoiceWindows(saw.lowpass(800.0, decaySec = 0.3)),
            renderVoiceWindows(slotted(), mapOf("d" to 0.3)),
        )
    }

    "sustain written alone: the same fill" {
        shouldRenderDoor(
            "sustain", renderVoiceWindows(saw.lowpass(800.0, sustainLevel = 0.2)),
            renderVoiceWindows(slotted(), mapOf("s" to 0.2)),
        )
    }

    "release written alone: the same fill" {
        shouldRenderDoor(
            "release", renderVoiceWindows(saw.lowpass(800.0, releaseSec = 0.2)),
            renderVoiceWindows(slotted(), mapOf("r" to 0.2)),
        )
    }

    "the depth written alone: its own value stands, not the shared 7" {
        val actual = renderVoiceWindows(slotted(), mapOf("e" to 24.0))

        shouldRenderDoor("env 24", renderVoiceWindows(saw.lowpass(800.0, env = 24.0)), actual)

        withClue("not the shared depth") { firstBitMismatch(filledSeven, actual) shouldNotBe -1 }
    }

    "an explicit depth of 0 with a stage written stays static: a written value is never overwritten by a fill" {
        shouldRenderStatic("env 0 and attack", renderVoiceWindows(slotted(), mapOf("e" to 0.0, "a" to 0.05)))
    }

    "an authored default alone is not written: a stage slot whose default is a real number does not switch it on" {
        shouldRenderStatic(
            "authored attack default 0.05",
            renderVoiceWindows(slotted(attack = IgnitorDsl.Param("a", 0.05)), emptyMap()),
        )
    }

    "a non-finite value in the bag is unset, so it does not switch it on" {
        shouldRenderStatic("attack NaN", renderVoiceWindows(slotted(), mapOf("a" to Double.NaN)))
        shouldRenderStatic("decay +Inf", renderVoiceWindows(slotted(), mapOf("d" to Double.POSITIVE_INFINITY)))
    }

    "a CONSTANT depth is never a question: a hand-built static filter stays static when a stage slot is written" {
        shouldRenderStatic(
            "Constant(0) depth, attack written",
            renderVoiceWindows(slotted(env = IgnitorDsl.Constant(0.0)), mapOf("a" to 0.05)),
        )
    }

    "an authored non-zero depth default stands when a stage is written: the fill only answers an unset slot" {
        shouldRenderDoor(
            "authored depth 12",
            renderVoiceWindows(saw.lowpass(800.0, env = 12.0, attackSec = 0.05)),
            renderVoiceWindows(slotted(env = IgnitorDsl.Param("e", 12.0)), mapOf("a" to 0.05)),
        )
    }

    "an authored depth default of 0 is the author's answer: a written stage does NOT fill it (round 1)" {
        shouldRenderStatic(
            "Param(e, 0.0) depth, attack written",
            renderVoiceWindows(slotted(env = IgnitorDsl.Param("e", 0.0)), mapOf("a" to 0.05)),
        )
    }

    "the fill is the filters' one rule: a highpass fills the same way" {
        val slottedHp = IgnitorDsl.Highpass(
            inner = saw,
            freq = IgnitorDsl.Constant(800.0),
            env = IgnitorDsl.Param("e", SLOT_UNSET),
            decaySec = IgnitorDsl.Param("d", FILTER_ENV_DECAY_SEC),
            sustainLevel = IgnitorDsl.Param("s", FILTER_ENV_SUSTAIN_LEVEL),
        )
        val actual = renderVoiceWindows(slottedHp, mapOf("s" to 0.3))

        withClue("highpass sustain alone") {
            firstBitMismatch(renderVoiceWindows(saw.highpass(800.0, sustainLevel = 0.3)), actual) shouldBe -1
            firstBitMismatch(renderVoiceWindows(saw.highpass(800.0)), actual) shouldNotBe -1
        }
    }
})
