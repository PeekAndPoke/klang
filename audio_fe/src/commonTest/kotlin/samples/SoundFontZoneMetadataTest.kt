/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe.samples

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.AdsrDef

class SoundFontZoneMetadataTest : StringSpec({

    fun zone(
        loopStart: Int = 0,
        loopEnd: Int = 0,
        sampleRate: Int = 22050,
        ahdsr: Boolean = false,
        anchor: Double = 0.0,
    ) = SoundfontIndex.SoundData.Zone(
        midi = 60,
        originalPitch = 6000.0,
        keyRangeLow = 0,
        keyRangeHigh = 127,
        loopStart = loopStart,
        loopEnd = loopEnd,
        coarseTune = 0,
        fineTune = 0,
        sampleRate = sampleRate,
        ahdsr = ahdsr,
        file = "",
        anchor = anchor,
    )

    // ── Loops: a loop is a loop ──────────────────────────────────────────────────────────────────
    //
    // The rows below replace a 50 ms "sustain loop" heuristic that discarded 45 % of all loops in the
    // shipped corpus (2 698 of 5 959 zones): a 1–5 ms loop is a single-cycle sustain loop, the
    // standard technique for a held tone, not a fake. See the KDoc on getSampleMetadata().

    "no loop when loopStart == loopEnd" {
        zone(loopStart = 100, loopEnd = 100).getSampleMetadata().loop.shouldBeNull()
    }

    "no loop when both are zero" {
        zone(loopStart = 0, loopEnd = 0).getSampleMetadata().loop.shouldBeNull()
    }

    "no loop when loopEnd precedes loopStart" {
        zone(loopStart = 500, loopEnd = 100).getSampleMetadata().loop.shouldBeNull()
    }

    "a SINGLE-CYCLE loop is honoured — 3 ms at 44100 is a sustain loop, not a fake" {
        // JCLive's accordion: 0.13–0.39 s samples ending in 1–5 ms loops. 132 frames = 3 ms.
        val meta = zone(loopStart = 10_000, loopEnd = 10_132, sampleRate = 44100).getSampleMetadata()
        val loop = meta.loop.shouldNotBeNull()
        loop.startSec shouldBe (10_000.0 / 44100)
        loop.endSec shouldBe (10_132.0 / 44100)
    }

    "a long loop is honoured, and travels as seconds at the zone's own rate" {
        // Seconds, not frames: the browser decodes at the context rate, so the backend multiplies
        // these by the DECODED rate and lands on the right frame regardless of resampling.
        val meta = zone(loopStart = 100, loopEnd = 1300, sampleRate = 22050).getSampleMetadata()
        val loop = meta.loop.shouldNotBeNull()
        loop.startSec shouldBe (100.0 / 22050)
        loop.endSec shouldBe (1300.0 / 22050)
    }

    // ── Envelope: the sample IS the envelope ─────────────────────────────────────────────────────

    "every zone gets the transparent VCA — attack 0, sustain 1, a short release, nothing else" {
        // Regardless of loop or ahdsr. The synthesized "percussive" shape used to cut a
        // four-second guitar ring at 0.5 s, and the "sustain" shape's 10 ms attack softened the
        // very transient playback-from-frame-0 restored.
        for (z in listOf(
            zone(loopStart = 0, loopEnd = 0),
            zone(loopStart = 0, loopEnd = 5000, sampleRate = 22050),
            zone(loopStart = 10_000, loopEnd = 10_132, sampleRate = 44100),
            zone(loopStart = 0, loopEnd = 0, ahdsr = true),
        )) {
            val adsr = z.getSampleMetadata().adsr.shouldBeInstanceOf<AdsrDef.Std>()
            adsr.attack shouldBe 0.0
            adsr.decay shouldBe 0.0
            adsr.sustain shouldBe 1.0
            adsr.release shouldBe SoundfontIndex.SoundData.Zone.SOUNDFONT_RELEASE_SEC
        }
    }

    "the release is short but not zero — a looping sample must be cut without a click" {
        val r = SoundfontIndex.SoundData.Zone.SOUNDFONT_RELEASE_SEC
        (r > 0.0) shouldBe true
        (r <= 0.1) shouldBe true
    }

    "anchor is passed through" {
        // Informational only now: it is the loudest sample's position, and nothing on the playback
        // path reads it any more (see docs/tasks-archive/2026-09/20260903-soundfont-looping-investigation.md).
        zone(anchor = 1.5).getSampleMetadata().anchor shouldBe 1.5
    }
})
