/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystEqBuilder
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The eq on its two builders (the typed-doors rollout S3a, decision A): the voice's `EqBuilder` (knobs `IgnitorDsl`)
 * and the chain's `KatalystEqBuilder` (knobs `KatalystKnobDsl`), the same words `band` and `tap`, the same sections and
 * defaults. Each member knob has a row against the section WRITTEN OUT, from Kotlin and from the script; each Kotlin
 * door a default-node row; the chain's refusal of a Katalyst expression (Q49) a row per parameter on both doors: an
 * `IllegalArgumentException` at build time from Kotlin, a script error (pre-checked, the script's own words) from the
 * script.
 */
class KlangScriptEqDoorParitySpec : StringSpec({

    fun engine() = klangScript().also { it.execute("""import * from "stdlib"""") }

    fun ks(code: String): IgnitorDsl = engine().execute(code).toObjectOrNull<IgnitorDsl>()!!

    fun chain(code: String): List<IgnitorDsl.EqSection> =
        (engine().execute(code).toObjectOrNull<KatalystDsl>()!!.stages.single() as KatalystStageDsl.Eq).sections

    fun c(v: Double) = IgnitorDsl.Constant(v)

    val saw = IgnitorDsl.Saw()
    val bell = IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(1.3), db = c(-4.0))
    val tap = IgnitorDsl.EqSection.RawTap(freq = c(850.0), q = c(2.0), gain = c(1.7))
    val bellDefaults = IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(0.707), db = c(0.0))
    val tapDefaults = IgnitorDsl.EqSection.RawTap(freq = c(850.0), q = c(0.707), gain = c(1.0))

    // ── the voice ───────────────────────────────────────────────────────────────

    "voice eq: the default node of the Kotlin door, written out, and the script's bare call" {
        saw.eq() shouldBe IgnitorDsl.Eq(inner = saw)
        saw.eq { it } shouldBe IgnitorDsl.Eq(inner = saw)
        ks("Ignitor.saw().eq()") shouldBe IgnitorDsl.Eq(inner = saw)
    }

    "voice eq: band and tap write their own section, against the section written out, from both doors" {
        withClue("band kotlin") { saw.eq { it.band(freq = 300.ign, q = 1.3.ign, db = (-4.0).ign) } shouldBe IgnitorDsl.Eq(saw, listOf(bell)) }
        withClue("band script") { ks("Ignitor.saw().eq(e => e.band(300, 1.3, -4))") shouldBe IgnitorDsl.Eq(saw, listOf(bell)) }
        withClue("tap kotlin") { saw.eq { it.tap(freq = 850.ign, q = 2.0.ign, gain = 1.7.ign) } shouldBe IgnitorDsl.Eq(saw, listOf(tap)) }
        withClue("tap script") { ks("Ignitor.saw().eq(e => e.tap(850, 2.0, 1.7))") shouldBe IgnitorDsl.Eq(saw, listOf(tap)) }
    }

    "voice eq: the defaults of band and tap, written out, from both doors" {
        withClue("band kotlin") { saw.eq { it.band(300.ign) } shouldBe IgnitorDsl.Eq(saw, listOf(bellDefaults)) }
        withClue("band script") { ks("Ignitor.saw().eq(e => e.band(300))") shouldBe IgnitorDsl.Eq(saw, listOf(bellDefaults)) }
        withClue("tap kotlin") { saw.eq { it.tap(850.ign) } shouldBe IgnitorDsl.Eq(saw, listOf(tapDefaults)) }
        withClue("tap script") { ks("Ignitor.saw().eq(e => e.tap(850))") shouldBe IgnitorDsl.Eq(saw, listOf(tapDefaults)) }
    }

    "voice eq: sections append in written order, and an eq on an eq continues it, on both doors" {
        val both = IgnitorDsl.Eq(saw, listOf(tap, bell))

        saw.eq { it.tap(freq = 850.ign, q = 2.0.ign, gain = 1.7.ign).band(freq = 300.ign, q = 1.3.ign, db = (-4.0).ign) } shouldBe both
        saw.eq { it.tap(freq = 850.ign, q = 2.0.ign, gain = 1.7.ign) }.eq { it.band(freq = 300.ign, q = 1.3.ign, db = (-4.0).ign) } shouldBe both
        ks("Ignitor.saw().eq(e => e.tap(850, 2.0, 1.7)).eq(e => e.band(300, 1.3, -4))") shouldBe both
    }

    "voice eq: a section knob takes a slot or a signal, as a tree leaf" {
        val param = IgnitorDsl.Param("f", 300.0)

        saw.eq { it.band(param) }.sections.single() shouldBe bellDefaults.copy(freq = param)
        ks("""Ignitor.saw().eq(e => e.band(Ignitor.param("f", 300)))""").let { (it as IgnitorDsl.Eq).sections.single() } shouldBe
            bellDefaults.copy(freq = param)
    }

    // ── the chain ───────────────────────────────────────────────────────────────

    "chain eq: band and tap write their own section, against the section written out, from both doors" {
        withClue("band kotlin") { KatalystEqBuilder().band(freq = 300.kat, q = 1.3.kat, db = (-4.0).kat).sections shouldBe listOf(bell) }
        withClue("band script") { chain("Katalyst(k => k.eq(e => e.band(300, 1.3, -4)))") shouldBe listOf(bell) }
        withClue("tap kotlin") { KatalystEqBuilder().tap(freq = 850.kat, q = 2.0.kat, gain = 1.7.kat).sections shouldBe listOf(tap) }
        withClue("tap script") { chain("Katalyst(k => k.eq(e => e.tap(850, 2.0, 1.7)))") shouldBe listOf(tap) }
    }

    "chain eq: the defaults of band and tap, written out, from both doors; the builder starts empty" {
        KatalystEqBuilder().sections shouldBe emptyList()
        KatalystEqBuilder().band(300.kat).sections shouldBe listOf(bellDefaults)
        chain("Katalyst(k => k.eq(e => e.band(300)))") shouldBe listOf(bellDefaults)
        KatalystEqBuilder().tap(850.kat).sections shouldBe listOf(tapDefaults)
        chain("Katalyst(k => k.eq(e => e.tap(850)))") shouldBe listOf(tapDefaults)
    }

    "chain eq: the same words make the same sections on a voice and on a chain" {
        KatalystEqBuilder().tap(freq = 850.kat, q = 2.0.kat, gain = 1.7.kat).band(freq = 300.kat, q = 1.3.kat, db = (-4.0).kat).sections shouldBe
            saw.eq { it.tap(freq = 850.ign, q = 2.0.ign, gain = 1.7.ign).band(freq = 300.ign, q = 1.3.ign, db = (-4.0).ign) }.sections
    }

    "chain eq: a Katalyst param is the section's Ignitor slot of the same name, default and description" {
        val expected = bellDefaults.copy(freq = IgnitorDsl.Param("f", 300.0, "the bell"))

        KatalystEqBuilder().band(Kat.param("f", 300.0, "the bell")).sections.single() shouldBe expected
        chain("""Katalyst(k => k.eq(e => e.band(Kat.param("f", 300, "the bell"))))""").single() shouldBe expected
    }

    "chain eq: a Katalyst expression on any section knob is refused, at build time from Kotlin, as a script error from the script (Q49)" {
        val expr: KatalystKnobDsl = KatalystKnobDsl.Plus(300.kat, Kat.sine(0.1.kat))
        val kotlin: List<Pair<String, () -> Unit>> = listOf(
            "band(freq = ...)" to { KatalystEqBuilder().band(freq = expr) },
            "band(q = ...)" to { KatalystEqBuilder().band(freq = 300.kat, q = expr) },
            "band(db = ...)" to { KatalystEqBuilder().band(freq = 300.kat, db = expr) },
            "tap(freq = ...)" to { KatalystEqBuilder().tap(freq = expr) },
            "tap(q = ...)" to { KatalystEqBuilder().tap(freq = 850.kat, q = expr) },
            "tap(gain = ...)" to { KatalystEqBuilder().tap(freq = 850.kat, gain = expr) },
        )

        for ((call, build) in kotlin) {
            withClue("kotlin $call") {
                shouldThrow<IllegalArgumentException> { build() }.message shouldBe
                    "Katalyst eq $call takes a number or a Kat.param; got a Kat expression"
            }
        }

        val script = listOf(
            "e.band(freq = Kat.sine(0.1).range(200, 400))",
            "e.band(freq = 300, q = Kat.param(\"q\", 1).times(2))",
            "e.band(freq = 300, db = Kat.sine(0.1))",
            "e.tap(freq = Kat.sine(0.1))",
            "e.tap(freq = 850, q = Kat.sine(0.1))",
            "e.tap(freq = 850, gain = Kat.sine(1))",
        )

        for (call in script) {
            withClue("script $call") {
                shouldThrow<KlangScriptTypeError> { chain("Katalyst(k => k.eq(e => $call))") }.message shouldBe
                    "a Katalyst eq section takes a number or a Kat.param; got a Kat expression"
            }
        }
    }
})
