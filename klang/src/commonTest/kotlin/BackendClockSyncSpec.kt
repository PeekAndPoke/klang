/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Guards the single global FE↔BE clock offset (the fix for the dead per-controller drift correction).
 * The frontend "now" is injected so the EMA / large-jump-snap behaviour is deterministic.
 */
class BackendClockSyncSpec : StringSpec({

    fun diag(backendNowMs: Double, outputLatencyMs: Double = 0.0) =
        KlangCommLink.Feedback.Diagnostics(
            playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID,
            sampleRate = 48_000,
            renderHeadroom = 1.0,
            activeVoiceCount = 0,
            cylinders = emptyList(),
            backendNowMs = backendNowMs,
            outputLatencyMs = outputLatencyMs,
        )

    "one Diagnostics within the threshold nudges the offset via EMA (α=0.05)" {
        val sync = BackendClockSync(nowMs = { 0.0 })
        // rawOffset = backendNow(200) - now(0) + latency(0) = 200ms; drift from 100ms = 100 < 500ms.
        // EMA: 100*0.95 + 200*0.05 = 105ms.
        sync.onDiagnostics(diag(backendNowMs = 200.0))

        sync.offsetSec shouldBe (0.105 plusOrMinus 1e-9)
    }

    "the offset converges to the steady-state raw offset" {
        val sync = BackendClockSync(nowMs = { 0.0 })
        repeat(500) { sync.onDiagnostics(diag(backendNowMs = 1000.0, outputLatencyMs = 20.0)) }
        // steady raw offset = backendNow(1000) + latency(20) = 1020ms.
        sync.offsetSec shouldBe (1.020 plusOrMinus 1e-3)
    }

    "a large clock discontinuity snaps immediately instead of crawling via EMA" {
        val sync = BackendClockSync(nowMs = { 0.0 })
        // rawOffset = 5000ms; drift from initial 100ms = 4900 > 500ms threshold → snap.
        sync.onDiagnostics(diag(backendNowMs = 5000.0))

        sync.offsetSec shouldBe (5.0 plusOrMinus 1e-9)
    }
})
