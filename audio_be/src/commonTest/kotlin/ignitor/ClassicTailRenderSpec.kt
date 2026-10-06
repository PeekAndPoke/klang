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
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.onepole
import io.peekandpoke.klang.audio_bridge.optimize
import kotlin.random.Random

/**
 * What an UNWRITTEN `classic()` builds, through the gate (phase 3 step 5): every stage whose slot the
 * note does not write is not built, so the tail is the envelope alone, at the voice envelope's
 * defaults and the strip's de-click. And the envelope's own switch: `adsr.on = 0` builds nothing at all
 * but keeps the voice's lifetime. The whole-voice fingerprints frozen when the strip retired were retired in turn at
 * the phase 3 end checkpoint (2026-10-02); every built-in's own render is fingerprinted in `BuiltInVoiceMatrixSpec`.
 */
class ClassicTailRenderSpec : StringSpec({

    val saw = IgnitorDsl.Saw()
    val tail = saw.classic()

    val envelopeAlone = saw.adsr(
        VOICE_ADSR_ATTACK_SEC, VOICE_ADSR_DECAY_SEC, VOICE_ADSR_SUSTAIN_LEVEL, VOICE_ADSR_RELEASE_SEC,
        declickSeconds = ENV_DECLICK_SECONDS,
    )

    "an unwritten classic() renders the envelope alone, bit for bit" {
        val expected = renderVoiceWindows(envelopeAlone)

        withClue("raw tree") { firstBitMismatch(expected, renderVoiceWindows(tail, emptyMap())) shouldBe -1 }
        withClue("optimized tree, as a registered instrument renders") {
            firstBitMismatch(expected, renderVoiceWindows(tail.optimize(), emptyMap())) shouldBe -1
        }
        withClue("and the envelope is really there") { firstBitMismatch(renderVoiceWindows(saw), expected) shouldNotBe -1 }
    }

    "classic()'s FIRST stage is the pattern's onepole: the `onepole` slot renders the source's onepole in front of the envelope" {
        // Since step 10 (it sat on a built-in's source before, the same place): `onepole` 900 is `saw.onepole(900)`
        // under the unwritten envelope, bit for bit, and an unwritten slot builds no onepole (the row above).
        val expected = renderVoiceWindows(
            saw.onepole(900.0).adsr(
                VOICE_ADSR_ATTACK_SEC, VOICE_ADSR_DECAY_SEC, VOICE_ADSR_SUSTAIN_LEVEL, VOICE_ADSR_RELEASE_SEC,
                declickSeconds = ENV_DECLICK_SECONDS,
            ),
        )

        withClue("raw tree") { firstBitMismatch(expected, renderVoiceWindows(tail, mapOf("onepole" to 900.0))) shouldBe -1 }
        withClue("optimized tree") { firstBitMismatch(expected, renderVoiceWindows(tail.optimize(), mapOf("onepole" to 900.0))) shouldBe -1 }
        withClue("engaged: the onepole changes the sound") { firstBitMismatch(renderVoiceWindows(envelopeAlone), expected) shouldNotBe -1 }
        withClue("in front of crush, not behind it: the other order is a different signal") {
            val behind = renderVoiceWindows(
                IgnitorDsl.Crush(inner = saw, amount = IgnitorDsl.Constant(5.0)).onepole(900.0).adsr(
                    VOICE_ADSR_ATTACK_SEC, VOICE_ADSR_DECAY_SEC, VOICE_ADSR_SUSTAIN_LEVEL, VOICE_ADSR_RELEASE_SEC,
                    declickSeconds = ENV_DECLICK_SECONDS,
                ),
            )
            val inFront = renderVoiceWindows(tail, mapOf("onepole" to 900.0, "crush.amount" to 5.0))

            firstBitMismatch(behind, inFront) shouldNotBe -1
        }
    }

    "adsr.on = 0 builds no stage at all: the source passes bit for bit" {
        firstBitMismatch(renderVoiceWindows(saw), renderVoiceWindows(tail, mapOf("adsr.on" to 0.0))) shouldBe -1
    }

    "an OFF envelope still reports its release as the voice's tail, the voice envelope's 0.05 or the written one" {
        fun tailOf(bag: Map<String, Double>): Double? =
            tail.buildExciter(ignitorParams = bag, random = Random(7), freqHz = 220.0).releaseTailSec

        tailOf(mapOf("adsr.on" to 0.0)) shouldBe VOICE_ADSR_RELEASE_SEC
        tailOf(mapOf("adsr.on" to 0.0, "adsr.release" to 0.3)) shouldBe 0.3
        tailOf(emptyMap()) shouldBe VOICE_ADSR_RELEASE_SEC
    }

    "an unwritten classic() does not gate its output; a written tremolo does (the cull rule)" {
        fun gates(bag: Map<String, Double>): Boolean =
            tail.buildExciter(ignitorParams = bag, random = Random(7), freqHz = 220.0).gatesOutput

        gates(emptyMap()) shouldBe false
        gates(mapOf("tremolo.depth" to 0.5, "tremolo.rate" to 4.0)) shouldBe true
    }

    "an unset or non-finite adsr.on is ON: the envelope is built by default" {
        val expected = renderVoiceWindows(envelopeAlone)

        firstBitMismatch(expected, renderVoiceWindows(tail, mapOf("adsr.on" to Double.NaN))) shouldBe -1
        firstBitMismatch(expected, renderVoiceWindows(tail, mapOf("adsr.on" to 1.0))) shouldBe -1
    }
})
