/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * The JVM host's output latency (audit L8-08): what its line holds is rendered and not heard yet. It reported none, and
 * the UI's time ran a quarter second ahead of the sound. The engine's own latency (the house limiter's lookahead, a
 * master chain's) the dispatcher adds in every report since KLANG-00431 D3 (`PlaybackEngineDispatcherDiagnosticsTest`);
 * until then this figure carried the house lookahead itself, 5 ms more.
 */
class JvmAudioBackendLatencySpec : StringSpec({

    "the line's buffer in milliseconds, the engine's own latency left to the dispatcher" {
        // 250 ms of 16-bit stereo at 48 kHz: 12000 frames of 4 bytes
        JvmAudioBackend.outputLatencyMs(lineBufferBytes = 48_000, sampleRate = 48_000) shouldBe (250.0 plusOrMinus 1e-9)
        // A line that granted less than asked: what it holds counts, not what was asked for
        JvmAudioBackend.outputLatencyMs(lineBufferBytes = 17_640, sampleRate = 44_100) shouldBe (100.0 plusOrMinus 1e-9)
    }
})
