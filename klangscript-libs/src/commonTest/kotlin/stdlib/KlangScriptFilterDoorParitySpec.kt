/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DEPTH_SEMITONES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.audio_bridge.onepole
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/** The five cutoff-envelope knobs of whichever of the four filter nodes this is, in node order. */
private fun IgnitorDsl.envKnobs(): List<IgnitorDsl> = when (this) {
    is IgnitorDsl.Lowpass -> listOf(env, attackSec, decaySec, sustainLevel, releaseSec)
    is IgnitorDsl.Highpass -> listOf(env, attackSec, decaySec, sustainLevel, releaseSec)
    is IgnitorDsl.Bandpass -> listOf(env, attackSec, decaySec, sustainLevel, releaseSec)
    is IgnitorDsl.Notch -> listOf(env, attackSec, decaySec, sustainLevel, releaseSec)
    else -> error("not a filter node: ${this::class.simpleName}")
}

/**
 * Dual-surface rule: every DSL must be usable from Kotlin directly, with the same parameters
 * meaning the same things. The filter doors drifted — the script door took an `IgnitorDsl` for
 * every knob plus `analog`, while the Kotlin door was `Double`-only and had no `analog` at all.
 * Sakura's `.lowpass(freq = Osc.sine(0.3)..., analog = Osc.slot.analog)` was therefore
 * expressible in a song and NOT from Kotlin.
 *
 * This spec compares the two doors node-for-node. It deliberately compares the FILTER node's
 * own fields rather than whole trees, so a difference in how the two sides build the upstream
 * oscillator cannot mask (or fake) a door difference.
 */
class KlangScriptFilterDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    val modulated = IgnitorDsl.Param("cut", 800.0)
    val q = IgnitorDsl.Constant(1.2)
    val analog = IgnitorDsl.Constant(4.0)

    "lowpass: a modulated freq + analog is expressible on BOTH doors, identically" {
        val script = ks("""Osc.saw().lowpass(freq = Osc.param("cut", 800), q = 1.2, passes = 2, analog = 4)""")
            as IgnitorDsl.Lowpass
        val kotlin = IgnitorDsl.Sawtooth().lowpass(modulated, q, passes = 2, analog = analog)

        withClue("freq") { script.freq shouldBe kotlin.freq }
        withClue("q") { script.q shouldBe kotlin.q }
        withClue("analog") { script.analog shouldBe kotlin.analog }
        withClue("passes") { script.passes shouldBe kotlin.passes }
    }

    "highpass: same" {
        val script = ks("""Osc.saw().highpass(freq = Osc.param("cut", 800), q = 1.2, passes = 3, analog = 4)""")
            as IgnitorDsl.Highpass
        val kotlin = IgnitorDsl.Sawtooth().highpass(modulated, q, passes = 3, analog = analog)
        script.freq shouldBe kotlin.freq
        script.q shouldBe kotlin.q
        script.analog shouldBe kotlin.analog
        script.passes shouldBe kotlin.passes
    }

    "bandpass and notch: analog reaches the node from Kotlin too (it had no way in before)" {
        val bp = ks("""Osc.saw().bandpass(freq = Osc.param("cut", 800), q = 1.2, analog = 4)""")
            as IgnitorDsl.Bandpass
        val bpk = IgnitorDsl.Sawtooth().bandpass(modulated, q, analog)
        bp.freq shouldBe bpk.freq
        bp.q shouldBe bpk.q
        bp.analog shouldBe bpk.analog

        val nt = ks("""Osc.saw().notch(freq = Osc.param("cut", 800), q = 1.2, analog = 4)""")
            as IgnitorDsl.Notch
        val ntk = IgnitorDsl.Sawtooth().notch(modulated, q, analog)
        nt.freq shouldBe ntk.freq
        nt.q shouldBe ntk.q
        nt.analog shouldBe ntk.analog
    }

    "onepole: a modulated freq works from Kotlin (it was Double-only)" {
        val script = ks("""Osc.saw().onepole(Osc.param("cut", 800))""") as IgnitorDsl.OnePoleLowpass
        val kotlin = IgnitorDsl.Sawtooth().onepole(modulated)
        script.freq shouldBe kotlin.freq
    }

    // ── Phase 3 step 3a: the cutoff envelope and the humanize lane, on both doors ──────────

    "the cutoff ENVELOPE is expressible on both doors, identically, on all four filters" {
        val env = IgnitorDsl.Constant(24.0)
        val attack = IgnitorDsl.Constant(0.005)
        val decay = IgnitorDsl.Constant(0.3)
        val sustain = IgnitorDsl.Constant(0.2)
        val release = IgnitorDsl.Constant(0.05)

        fun assertEnv(script: IgnitorDsl, kotlin: IgnitorDsl) {
            val s = script.envKnobs()
            val k = kotlin.envKnobs()
            withClue("env") { s[0] shouldBe k[0] }
            withClue("attackSec") { s[1] shouldBe k[1] }
            withClue("decaySec") { s[2] shouldBe k[2] }
            withClue("sustainLevel") { s[3] shouldBe k[3] }
            withClue("releaseSec") { s[4] shouldBe k[4] }
        }

        val tail = "env = 24, attackSec = 0.005, decaySec = 0.3, sustainLevel = 0.2, releaseSec = 0.05"

        withClue("lowpass") {
            assertEnv(
                ks("""Osc.saw().lowpass(freq = 800, q = 1.2, $tail)"""),
                IgnitorDsl.Sawtooth().lowpass(
                    IgnitorDsl.Constant(800.0), q, env = env,
                    attackSec = attack, decaySec = decay, sustainLevel = sustain, releaseSec = release,
                ),
            )
        }

        withClue("highpass") {
            assertEnv(
                ks("""Osc.saw().highpass(freq = 800, q = 1.2, $tail)"""),
                IgnitorDsl.Sawtooth().highpass(
                    IgnitorDsl.Constant(800.0), q, env = env,
                    attackSec = attack, decaySec = decay, sustainLevel = sustain, releaseSec = release,
                ),
            )
        }

        withClue("bandpass") {
            assertEnv(
                ks("""Osc.saw().bandpass(freq = 800, q = 1.2, $tail)"""),
                IgnitorDsl.Sawtooth().bandpass(
                    IgnitorDsl.Constant(800.0), q, env = env,
                    attackSec = attack, decaySec = decay, sustainLevel = sustain, releaseSec = release,
                ),
            )
        }

        withClue("notch") {
            assertEnv(
                ks("""Osc.saw().notch(freq = 800, q = 1.2, $tail)"""),
                IgnitorDsl.Sawtooth().notch(
                    IgnitorDsl.Constant(800.0), q, env = env,
                    attackSec = attack, decaySec = decay, sustainLevel = sustain, releaseSec = release,
                ),
            )
        }
    }

    "a MODULATED envelope depth reaches the node from both doors" {
        val script = ks("""Osc.saw().lowpass(freq = 800, env = Osc.param("lpenv", 0))""") as IgnitorDsl.Lowpass
        val kotlin = IgnitorDsl.Sawtooth().lowpass(IgnitorDsl.Constant(800.0), env = IgnitorDsl.Param("lpenv", 0.0))

        script.env shouldBe kotlin.env
    }

    "humanize is a flag on both doors, and the script door takes a truthy NUMBER" {
        // The runtime hands numeric literals through as Double, so `humanize = 1` must work the
        // way the sibling `Pipeline` door's `on(1)` and sprudel's `adsrOn(0)` do.
        (ks("""Osc.saw().lowpass(freq = 800, humanize = true)""") as IgnitorDsl.Lowpass).humanize shouldBe true
        (ks("""Osc.saw().lowpass(freq = 800, humanize = 1)""") as IgnitorDsl.Lowpass).humanize shouldBe true
        (ks("""Osc.saw().lowpass(freq = 800, humanize = 0)""") as IgnitorDsl.Lowpass).humanize shouldBe false
        (ks("""Osc.saw().notch(freq = 800, humanize = true)""") as IgnitorDsl.Notch).humanize shouldBe true

        IgnitorDsl.Sawtooth().lowpass(800.0, humanize = true).humanize shouldBe true
        IgnitorDsl.Sawtooth().highpass(800.0, humanize = true).humanize shouldBe true
        IgnitorDsl.Sawtooth().bandpass(800.0, humanize = true).humanize shouldBe true
        IgnitorDsl.Sawtooth().notch(800.0, humanize = true).humanize shouldBe true
    }

    "THE COMPOUND FILL: naming any stage knob writes the companions, env included, on both doors" {
        // `/dsl-design` section 4. Without this, `lowpass(800, decaySec = 0.3)` is a silent no-op
        // while the identical sprudel call, `lpf(800, decay = 0.3)`, is an audible pluck:
        // SprudelVoiceData builds a FilterDef envelope when ANY of the five is present and
        // FilterEnvDef.resolve() fills the depth with FILTER_ENV_DEPTH_SEMITONES.
        val filled = listOf(
            IgnitorDsl.Constant(FILTER_ENV_DEPTH_SEMITONES),
            IgnitorDsl.Constant(FILTER_ENV_ATTACK_SEC),
            IgnitorDsl.Constant(0.3),
            IgnitorDsl.Constant(0.2),
            IgnitorDsl.Constant(FILTER_ENV_RELEASE_SEC),
        )
        val f = IgnitorDsl.Constant(800.0)

        withClue("script door") {
            ks("""Osc.saw().lowpass(freq = 800, decaySec = 0.3, sustainLevel = 0.2)""").envKnobs() shouldBe filled
            ks("""Osc.saw().highpass(freq = 800, decaySec = 0.3, sustainLevel = 0.2)""").envKnobs() shouldBe filled
            ks("""Osc.saw().bandpass(freq = 800, decaySec = 0.3, sustainLevel = 0.2)""").envKnobs() shouldBe filled
            ks("""Osc.saw().notch(freq = 800, decaySec = 0.3, sustainLevel = 0.2)""").envKnobs() shouldBe filled
        }

        withClue("Kotlin scalar overload") {
            IgnitorDsl.Sawtooth().lowpass(800.0, decaySec = 0.3, sustainLevel = 0.2).envKnobs() shouldBe filled
            IgnitorDsl.Sawtooth().highpass(800.0, decaySec = 0.3, sustainLevel = 0.2).envKnobs() shouldBe filled
            IgnitorDsl.Sawtooth().bandpass(800.0, decaySec = 0.3, sustainLevel = 0.2).envKnobs() shouldBe filled
            IgnitorDsl.Sawtooth().notch(800.0, decaySec = 0.3, sustainLevel = 0.2).envKnobs() shouldBe filled
        }

        withClue("Kotlin node overload") {
            val d = IgnitorDsl.Constant(0.3)
            val su = IgnitorDsl.Constant(0.2)

            IgnitorDsl.Sawtooth().lowpass(f, decaySec = d, sustainLevel = su).envKnobs() shouldBe filled
            IgnitorDsl.Sawtooth().highpass(f, decaySec = d, sustainLevel = su).envKnobs() shouldBe filled
            IgnitorDsl.Sawtooth().bandpass(f, decaySec = d, sustainLevel = su).envKnobs() shouldBe filled
            IgnitorDsl.Sawtooth().notch(f, decaySec = d, sustainLevel = su).envKnobs() shouldBe filled
        }

        withClue("an EXPLICIT env is never overwritten by the fill") {
            ks("""Osc.saw().lowpass(freq = 800, env = 24, decaySec = 0.3)""").envKnobs()[0] shouldBe
                IgnitorDsl.Constant(24.0)
            IgnitorDsl.Sawtooth().lowpass(800.0, env = 24.0, decaySec = 0.3).envKnobs()[0] shouldBe
                IgnitorDsl.Constant(24.0)
            IgnitorDsl.Sawtooth().lowpass(f, env = IgnitorDsl.Constant(24.0), decaySec = IgnitorDsl.Constant(0.3))
                .envKnobs()[0] shouldBe IgnitorDsl.Constant(24.0)
        }

        withClue("env alone names the stage and gets the constant stage times") {
            ks("""Osc.saw().lowpass(freq = 800, env = 24)""").envKnobs() shouldBe listOf(
                IgnitorDsl.Constant(24.0),
                IgnitorDsl.Constant(FILTER_ENV_ATTACK_SEC),
                IgnitorDsl.Constant(FILTER_ENV_DECAY_SEC),
                IgnitorDsl.Constant(FILTER_ENV_SUSTAIN_LEVEL),
                IgnitorDsl.Constant(FILTER_ENV_RELEASE_SEC),
            )
        }
    }

    "the new knobs default the same on both doors, and the default is 'no envelope, no lane'" {
        // A bare call NAMES no envelope knob, so the fill leaves the depth at the node's off
        // value and the stage knobs at their constants: the filter this door built before the
        // envelope existed, which is what the corpus identity render rests on.
        val defaults = listOf(
            IgnitorDsl.Constant(0.0),
            IgnitorDsl.Constant(FILTER_ENV_ATTACK_SEC),
            IgnitorDsl.Constant(FILTER_ENV_DECAY_SEC),
            IgnitorDsl.Constant(FILTER_ENV_SUSTAIN_LEVEL),
            IgnitorDsl.Constant(FILTER_ENV_RELEASE_SEC),
        )

        for (door in listOf("lowpass", "highpass", "bandpass", "notch")) {
            withClue("$door, script door") { ks("""Osc.saw().$door(800)""").envKnobs() shouldBe defaults }
        }

        // BOTH Kotlin overloads: the scalar one and the IgnitorDsl one carry their own default
        // lists, so a drift in either is a drift in the surface.
        val f = IgnitorDsl.Constant(800.0)

        withClue("lowpass, Kotlin scalar") { IgnitorDsl.Sawtooth().lowpass(800.0).envKnobs() shouldBe defaults }
        withClue("lowpass, Kotlin node") { IgnitorDsl.Sawtooth().lowpass(f).envKnobs() shouldBe defaults }
        withClue("highpass, Kotlin scalar") { IgnitorDsl.Sawtooth().highpass(800.0).envKnobs() shouldBe defaults }
        withClue("highpass, Kotlin node") { IgnitorDsl.Sawtooth().highpass(f).envKnobs() shouldBe defaults }
        withClue("bandpass, Kotlin scalar") { IgnitorDsl.Sawtooth().bandpass(800.0).envKnobs() shouldBe defaults }
        withClue("bandpass, Kotlin node") { IgnitorDsl.Sawtooth().bandpass(f).envKnobs() shouldBe defaults }
        withClue("notch, Kotlin scalar") { IgnitorDsl.Sawtooth().notch(800.0).envKnobs() shouldBe defaults }
        withClue("notch, Kotlin node") { IgnitorDsl.Sawtooth().notch(f).envKnobs() shouldBe defaults }

        withClue("humanize is off on both overloads too") {
            IgnitorDsl.Sawtooth().lowpass(f).humanize shouldBe false
            IgnitorDsl.Sawtooth().highpass(f).humanize shouldBe false
            IgnitorDsl.Sawtooth().bandpass(f).humanize shouldBe false
            IgnitorDsl.Sawtooth().notch(f).humanize shouldBe false
        }

        IgnitorDsl.Sawtooth().lowpass(800.0).humanize shouldBe false
        (ks("""Osc.saw().lowpass(800)""") as IgnitorDsl.Lowpass).humanize shouldBe false
    }

    "the scalar overloads still wrap in Constant, and the defaults did not move" {
        // Adding `analog` must not change what an existing Kotlin caller builds.
        val lp = IgnitorDsl.Sawtooth().lowpass(800.0)
        lp.freq shouldBe IgnitorDsl.Constant(800.0)
        lp.q shouldBe IgnitorDsl.Constant(0.707)
        lp.analog shouldBe IgnitorDsl.Constant(0.0)
        lp.passes shouldBe 1

        // ...and the scalar door reaches analog too, in the same fourth slot as the script door.
        IgnitorDsl.Sawtooth().lowpass(800.0, 1.0, 2, 3.0).analog shouldBe IgnitorDsl.Constant(3.0)
        IgnitorDsl.Sawtooth().notch(1000.0, 0.9, 2.0).analog shouldBe IgnitorDsl.Constant(2.0)
    }
})
