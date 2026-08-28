/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Guards [AdsrDef.Std.on], the flag that says whether the VCA stage shapes a voice at all.
 *
 * Three layers answer it, in order: the voice (sprudel), then the pipeline's `Vca` stage, then a
 * hard `true`. This spec covers the first layer and the hand-off to the second. The engine end of
 * the chain is in `VcaOnFlagRenderSpec` (audio_be).
 *
 * See `docs/tasks/ignitor-envelope-ownership.md` Phase 3.
 */
class AdsrOnFlagSpec : StringSpec({

    // ── The property that makes the layering work at all ──────────────────────

    "the inherit shape carries NO claim about on" {
        // THE guard for `on: Boolean? = null`. If the default were `true`, every inherit-shaped Std
        // would assert "VCA on", `on ?: other.on` could never fall through, and a pipeline-level
        // Vca(on = false) would be unreachable. Make the default `true` and this goes red.
        AdsrDef.Std().on.shouldBeNull()
        (AdsrDef.empty as AdsrDef.Std).on.shouldBeNull()
    }

    "resolve leaves on unset when nobody claimed it, so the pipeline can answer" {
        // Resolved's other six fields all get a hard fallback here. `on` deliberately does not:
        // it has one more layer to fall through.
        AdsrDef.Std().resolve(AdsrDef.Std()).on.shouldBeNull()
    }

    // ── Merge ─────────────────────────────────────────────────────────────────

    "a set on wins over the fallback" {
        AdsrDef.Std(on = false).mergeWith(AdsrDef.Std(on = true)).let {
            (it as AdsrDef.Std).on shouldBe false
        }
    }

    "an unset on takes the fallback's" {
        AdsrDef.Std().mergeWith(AdsrDef.Std(on = false)).let {
            (it as AdsrDef.Std).on shouldBe false
        }
    }

    "false is not confused with unset" {
        // The whole point of the nullable: `on = false` is a claim, absence is not.
        AdsrDef.Std(on = false).mergeWith(AdsrDef.Std(on = true)).let {
            (it as AdsrDef.Std).on shouldBe false
        }
        AdsrDef.Std(on = true).mergeWith(AdsrDef.Std(on = false)).let {
            (it as AdsrDef.Std).on shouldBe true
        }
    }

    "resolve takes the voice's on over the defaults'" {
        AdsrDef.Std(on = false).resolve(AdsrDef.Std(on = true)).on shouldBe false
        AdsrDef.Std().resolve(AdsrDef.Std(on = false)).on shouldBe false
    }

    // ── Flag, not variant ─────────────────────────────────────────────────────

    "switching off keeps the numbers, so it can be flipped back" {
        // This is why `on` is a flag rather than an AdsrDef.None variant: in a live-coding language
        // you turn it off, listen, and turn it back on. A variant throws the values away.
        val tuned = AdsrDef.Std(attack = 0.005, decay = 1.0, sustain = 1.0, release = 0.05)
        val off = tuned.copy(on = false)

        off.attack shouldBe 0.005
        off.decay shouldBe 1.0
        off.sustain shouldBe 1.0
        off.release shouldBe 0.05

        off.copy(on = true) shouldBe tuned.copy(on = true)
    }

    // ── The pipeline's end of the hand-off ────────────────────────────────────

    "the Vca stage default is on, so existing songs are unaffected" {
        StageDsl.Vca().on shouldBe true
    }

    "the built-in engines leave the VCA on" {
        // Flipping these would change how every existing song sounds. Vca(on = false) is for
        // engines built around ignitors that carry their own envelope.
        for (preset in listOf(PipelineDsl.modern, PipelineDsl.pedal)) {
            preset.stages.filterIsInstance<StageDsl.Vca>().forEach { it.on shouldBe true }
        }
    }
})
