/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * The sprudel door for `.adsrOn()` / `.adsrOff()`: switching the voice's own amplitude envelope
 * (the VCA) on or off, so an ignitor that carries its own envelope does not compound with it.
 *
 * Covers the door through to the wire (`AdsrDef.Std.on`). The value-side merge is in
 * `AdsrOnFlagSpec` (audio_bridge) and the render end in `VcaOnFlagRenderSpec` (audio_be).
 *
 * See `docs/tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md` Phase 3.
 */
class LangAdsrOnOffSpec : StringSpec({

    fun wireAdsr(code: String): AdsrDef.Std =
        SprudelPattern.compile(code)!!.queryArc(0.0, 1.0).first().data.toVoiceData().adsr as AdsrDef.Std

    // ── Nothing said means nothing on the wire ────────────────────────────────

    "a pattern with no adsrOn/adsrOff carries NO claim on the wire" {
        // The engine's Vca stage must be free to answer. If this ever became `true`, the pipeline
        // layer would be dead and Vca(on = false) unreachable.
        wireAdsr("""note("c")""").on.shouldBeNull()
        wireAdsr("""note("c").adsr(0.01, 0.1, 0.7, 0.3)""").on.shouldBeNull()
    }

    // ── The two doors ─────────────────────────────────────────────────────────

    "adsrOff() puts false on the wire" {
        wireAdsr("""note("c").adsrOff()""").on shouldBe false
    }

    "adsrOn() puts true on the wire" {
        wireAdsr("""note("c").adsrOn()""").on shouldBe true
    }

    "adsrOn takes a flag, so it is patternable like any other control" {
        wireAdsr("""note("c").adsrOn(0)""").on shouldBe false
        wireAdsr("""note("c").adsrOn(1)""").on shouldBe true
    }

    // ── Flag, not variant: the numbers survive ────────────────────────────────

    "adsrOff keeps the envelope numbers, so it can be flipped back" {
        // This is the property that chose a flag over an AdsrDef.None variant: in a live-coding
        // language you switch it off, listen, and switch it back on.
        val off = wireAdsr("""note("c").adsr(0.005, 1.0, 1.0, 0.05).adsrOff()""")
        off.on shouldBe false
        off.attack shouldBe 0.005
        off.decay shouldBe 1.0
        off.sustain shouldBe 1.0
        off.release shouldBe 0.05

        val backOn = wireAdsr("""note("c").adsr(0.005, 1.0, 1.0, 0.05).adsrOff().adsrOn()""")
        backOn.on shouldBe true
        backOn.release shouldBe 0.05
    }

    "order does not matter: setting the envelope after switching off keeps it off" {
        wireAdsr("""note("c").adsrOff().adsr(0.005, 1.0, 1.0, 0.05)""").let {
            it.on shouldBe false
            it.release shouldBe 0.05
        }
    }

    // ── The other two receiver forms ──────────────────────────────────────────

    "the string door works" {
        wireAdsr(""""c".adsrOff().note()""").on shouldBe false
    }

    "the chained mapper door works" {
        // Form (d), PatternMapperFn.adsrOff, needs a mapper-ON-mapper call. `apply(x => x.adsrOff())`
        // does NOT reach it: `apply` takes vararg PatternMapperFn, so `x` binds to a SprudelPattern
        // and that is door (a) again — delete both PatternMapperFn overloads and it stays green.
        wireAdsr("""note("c").apply(gain(0.5).adsrOff())""").on shouldBe false
        wireAdsr("""note("c").apply(gain(0.5).adsrOn())""").on shouldBe true
    }

    "the standalone mapper door works" {
        // Form (c) of the four-door convention. Without it `apply(adsrOff())` and
        // `superimpose(adsrOff())` do not resolve — the idiom Der Schmetterling uses for pan.
        wireAdsr("""note("c").apply(adsrOff())""").on shouldBe false
        wireAdsr("""note("c").apply(adsrOn())""").on shouldBe true
        wireAdsr("""note("c").apply(adsrOn(0))""").on shouldBe false
    }
})
