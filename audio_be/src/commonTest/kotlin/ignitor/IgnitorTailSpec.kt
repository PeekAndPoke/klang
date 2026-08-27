/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul

/**
 * Guards [BuiltIgnitor.releaseTailSec] — the number `VoiceFactory` turns into voice lifetime.
 *
 * This replaces `IgnitorDsl.maxReleaseSec()` (deleted 2026-08-27). The bug it existed to prevent,
 * and the two it silently caused, are documented in `docs/tasks/ignitor-envelope-ownership.md`:
 * an under-reported tail truncates the release audibly, an over-reported one only keeps a silent
 * voice alive slightly too long. So every case below that could go either way is asserted in the
 * safe direction on purpose.
 */
class IgnitorTailSpec : StringSpec({

    fun tailOf(dsl: IgnitorDsl, oscParams: Map<String, Double>? = null, freqHz: Double = 440.0): Double? =
        dsl.buildExciter(oscParams, freqHz = freqHz).releaseTailSec

    fun c(v: Double) = IgnitorDsl.Constant(v)

    // ── The basics ────────────────────────────────────────────────────────────

    "no envelope anywhere reports null, not 0.0" {
        // null and 0.0 read the same at the call site, but null is the honest "no claim" and is
        // what lets a future caller tell "nothing found" from "found, and it is zero".
        tailOf(IgnitorDsl.Sine()).shouldBeNull()
    }

    "a literal release is reported exactly" {
        tailOf(IgnitorDsl.Sine().adsr(0.01, 0.1, 0.5, 0.4)) shouldBe 0.4
    }

    "a Param release falls back to its default" {
        val dsl = IgnitorDsl.Adsr(inner = IgnitorDsl.Sine(), releaseSec = IgnitorDsl.Param("rel", 0.7))
        tailOf(dsl) shouldBe 0.7
    }

    // ── Measurement 2: the oscp override used to be invisible to lifetime ─────

    "an oscp override on the release reaches the tail" {
        // THE original bug: `maxReleaseSec` read Param DEFAULTS only, so a 1.5 s release requested
        // via .oscp("release", …) was delivered as the voice's own ~0.05 s. The override is folded
        // into ParamIgnitor at build time, so it reaches the tail with no second lookup rule.
        val dsl = IgnitorDsl.Adsr(inner = IgnitorDsl.Sine(), releaseSec = IgnitorDsl.Param("rel", 0.013))
        tailOf(dsl, oscParams = mapOf("rel" to 1.5)) shouldBe 1.5
    }

    "an oscp override under a different param name works the same" {
        // The name is never hardcoded; it is read off the node.
        val dsl = IgnitorDsl.Adsr(inner = IgnitorDsl.Sine(), releaseSec = IgnitorDsl.Param("damping", 0.02))
        tailOf(dsl, oscParams = mapOf("damping" to 0.9)) shouldBe 0.9
    }

    // ── Expressions: the old hardcoded `else -> 0.3` guess ────────────────────

    "an arithmetic release expression resolves exactly, not to a guess" {
        // `maxReleaseSec` returned a hardcoded 0.3 here, truncating anything longer.
        val dsl = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(),
            releaseSec = IgnitorDsl.Param("rel", 0.4).mul(c(2.0)),
        )
        tailOf(dsl) shouldBe 0.8
    }

    "a pitch-relative release resolves against the note frequency" {
        // The shape docs/tasks/ignitor-envelope-ownership.md recommends for low notes: express the
        // release in periods rather than milliseconds. 200 periods of 100 Hz = 2.0 s.
        val dsl = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(),
            releaseSec = IgnitorDsl.Recip(IgnitorDsl.Freq).mul(c(200.0)),
        )
        tailOf(dsl, freqHz = 100.0) shouldBe 2.0
    }

    "a modulated release time reports null rather than guessing" {
        // No single static value is correct here, so make no claim: the voice's own release governs.
        val dsl = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(),
            releaseSec = IgnitorDsl.Sine(freq = c(0.5)).mul(c(0.5)),
        )
        tailOf(dsl).shouldBeNull()
    }

    // ── Spine vs parameter position ───────────────────────────────────────────

    "an envelope in a filter-cutoff position does NOT extend lifetime" {
        // A filter sweep is modulation, not amplitude. `maxReleaseSec` already excluded it
        // (`is Lowpass -> inner`), and the withMod/noMod split keeps that.
        val sweep = IgnitorDsl.Adsr(inner = IgnitorDsl.Constant(2000.0), releaseSec = c(5.0))
        tailOf(IgnitorDsl.Sine().lowpass(sweep)).shouldBeNull()
    }

    "an envelope multiplied into the signal DOES extend lifetime" {
        val env = IgnitorDsl.Adsr(inner = c(1.0), releaseSec = c(1.25))
        tailOf(IgnitorDsl.Sine().mul(env)) shouldBe 1.25
    }

    // ── The build-cache hole this design exists to close ──────────────────────

    "a subtree shared between a parameter position and the spine still counts" {
        // REGRESSION GUARD for the reason the tail is a return value and not an accumulator.
        // `Times` builds `left` first, which reaches `env` inside the cutoff (a parameter position,
        // so its tail is discarded there) and caches it. The later spine reference is a cache HIT.
        // With an accumulator gated on spine-ness, that hit contributes nothing and the voice is cut
        // 0.8 s short. Carrying the tail inside the cached value is what makes this work.
        val env = IgnitorDsl.Adsr(inner = c(1.0), releaseSec = IgnitorDsl.Param("rel", 0.8))
        val dsl = IgnitorDsl.Sine()
            .lowpass(c(800.0).mul(env))
            .mul(env)

        tailOf(dsl) shouldBe 0.8
    }

    "a nested shared envelope reports the deeper release too" {
        // Same hazard one level down: on a cache hit the walk does not descend, so the inner Adsr
        // would be missed if the stored value were not already the subtree max.
        val nested = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Adsr(inner = c(1.0), releaseSec = c(1.4)),
            releaseSec = c(0.2),
        )
        val dsl = IgnitorDsl.Sine().lowpass(c(800.0).mul(nested)).mul(nested)

        tailOf(dsl) shouldBe 1.4
    }

    // ── Several envelopes ─────────────────────────────────────────────────────

    "several envelopes on the spine report the max, ranked by RELEASE not by total length" {
        // Env A has the longer attack+release; env B needs the longer tail. Ranking by total would
        // pick A (0.1 s) and truncate B (0.5 s) — attack happens INSIDE the gate and never
        // contributes to the tail.
        val a = IgnitorDsl.Adsr(inner = IgnitorDsl.Sine(), attackSec = c(2.0), releaseSec = c(0.1))
        val b = IgnitorDsl.Adsr(inner = IgnitorDsl.Sawtooth(), attackSec = c(0.001), releaseSec = c(0.5))

        tailOf(IgnitorDsl.Plus(a, b)) shouldBe 0.5
    }

    "an FM modulator's envelope still counts" {
        // Not on the amplitude spine, but `maxReleaseSec` counted it (maxOf(carrier, modulator)).
        // Keeping it errs large; dropping it would silently shorten voices that render fine today.
        val modulator = IgnitorDsl.Sine().adsr(0.001, 0.1, 0.5, 0.9)
        val dsl = IgnitorDsl.Fm(carrier = IgnitorDsl.Sine(), modulator = modulator)

        tailOf(dsl) shouldBe 0.9
    }

    // ── Variants: the accuracy win over the deleted static walk ───────────────

    "Variants reports the tail of the child it actually built" {
        // `maxReleaseSec` took the max across ALL children, so every variant instrument
        // over-allocated lifetime to its longest sibling. The build only builds the picked child.
        val dsl = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine().adsr(0.01, 0.1, 0.5, 0.2),
                IgnitorDsl.Sawtooth().adsr(0.01, 0.1, 0.5, 1.5),
                IgnitorDsl.Square().adsr(0.01, 0.1, 0.5, 0.8),
            )
        )

        dsl.buildExciter(soundIndex = 0).releaseTailSec shouldBe 0.2
        dsl.buildExciter(soundIndex = 1).releaseTailSec shouldBe 1.5
        dsl.buildExciter(soundIndex = 2).releaseTailSec shouldBe 0.8
        // Wraps, like the render dispatch does.
        dsl.buildExciter(soundIndex = 3).releaseTailSec shouldBe 0.2
    }

    // ── The registry seam ─────────────────────────────────────────────────────

    "createExciter carries the tail through the registry and the onepole wrap" {
        val registry = IgnitorRegistry()
        registry.register("pad", IgnitorDsl.Sine().adsr(0.01, 0.1, 0.5, 1.1))

        val data = io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(sound = "pad")
        registry.createExciter("pad", data, 440.0)?.releaseTailSec shouldBe 1.1

        // The onepole wrap replaces the ignitor; it must not drop the finding with it.
        val withOnepole = data.copy(oscParams = mapOf("onepole" to 900.0))
        registry.createExciter("pad", withOnepole, 440.0)?.releaseTailSec shouldBe 1.1
    }
})
