package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * Tests for [computeFilterEnvelope] — the control-rate ADSR used by Ignitor-level
 * filter and FM modulation.
 *
 * Covers edge cases: early gate-off during attack/decay, zero segment times,
 * sustain 0/1, and boundary conditions.
 */
class FilterEnvelopeTest : StringSpec({

    fun ctx(
        sampleRate: Int = 48000,
        voiceElapsedFrames: Int,
        gateEndFrame: Int,
        voiceDurationFrames: Int = gateEndFrame + 4800,
    ) = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = voiceDurationFrames,
        gateEndFrame = gateEndFrame,
        releaseFrames = voiceDurationFrames - gateEndFrame,
        voiceEndFrame = voiceDurationFrames,
        scratchBuffers = ScratchBuffers(blockFrames = 256),
        voiceElapsedFrames = voiceElapsedFrames,
    )

    // ── Normal ADSR lifecycle ────────────────────────────────────────────────

    "attack ramps from 0 to 1" {
        // attack = 100 frames, at frame 50 → should be 0.5
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 50, gateEndFrame = 1000),
            attackSec = 100.0 / 48000,
            decaySec = 0.0,
            sustainLevel = 1.0,
            releaseSec = 0.0,
        )
        result shouldBe (0.5 plusOrMinus 0.02)
    }

    "decay ramps from 1 to sustain" {
        // attack = 100, decay = 100, sustain = 0.5. At frame 150 (mid decay) → 0.75
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 150, gateEndFrame = 1000),
            attackSec = 100.0 / 48000,
            decaySec = 100.0 / 48000,
            sustainLevel = 0.5,
            releaseSec = 0.0,
        )
        result shouldBe (0.75 plusOrMinus 0.02)
    }

    "sustain holds at sustain level" {
        // attack = 100, decay = 100, sustain = 0.6. At frame 500 → 0.6
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 500, gateEndFrame = 1000),
            attackSec = 100.0 / 48000,
            decaySec = 100.0 / 48000,
            sustainLevel = 0.6,
            releaseSec = 0.0,
        )
        result shouldBe (0.6 plusOrMinus 0.01)
    }

    "release decays from sustain to 0" {
        // sustain = 0.8, release = 200 frames, gate ends at frame 500, at frame 600 (mid release) → 0.4
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 600, gateEndFrame = 500),
            attackSec = 10.0 / 48000,
            decaySec = 10.0 / 48000,
            sustainLevel = 0.8,
            releaseSec = 200.0 / 48000,
        )
        result shouldBe (0.4 plusOrMinus 0.02)
    }

    // ── Critical edge case: early gate-off ───────────────────────────────────

    "early gate-off during attack — release starts from actual level, not sustain" {
        // attack = 200 frames, gate ends at frame 100 (halfway through attack)
        // At gate-off, actual level should be 0.5, NOT sustainLevel
        // release = 100 frames, at frame 150 (50 frames into release) → 0.25
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 150, gateEndFrame = 100),
            attackSec = 200.0 / 48000,
            decaySec = 100.0 / 48000,
            sustainLevel = 0.8,
            releaseSec = 100.0 / 48000,
        )
        // Level at gate-off = 100/200 = 0.5 (mid attack)
        // 50 frames into release: 0.5 - (50 * 0.5/100) = 0.25
        result shouldBe (0.25 plusOrMinus 0.02)
    }

    "early gate-off during decay — release starts from actual level" {
        // attack = 100, decay = 200, sustain = 0.4, gate ends at frame 200 (mid decay)
        // At gate-off: level = 1.0 - (100 * (1-0.4)/200) = 1.0 - 0.3 = 0.7
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 200, gateEndFrame = 200),
            attackSec = 100.0 / 48000,
            decaySec = 200.0 / 48000,
            sustainLevel = 0.4,
            releaseSec = 200.0 / 48000,
        )
        // At gate-off = frame 200, we're AT gate end, so release just started → level = 0.7
        result shouldBe (0.7 plusOrMinus 0.02)
    }

    // ── Zero time edge cases ─────────────────────────────────────────────────

    "zero attack — instant rise to peak" {
        // attack = 0, at frame 0 → should be 1.0 (or sustain if decay = 0 too)
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 0, gateEndFrame = 1000),
            attackSec = 0.0,
            decaySec = 100.0 / 48000,
            sustainLevel = 0.5,
            releaseSec = 0.0,
        )
        // With 0 attack, attRate = 1.0, absPos * 1.0 = 0. But absPos=0 < attackFrames=0 is false,
        // so we fall through to decay. decPos = 0 - 0 = 0, so level = 1.0
        result shouldBe (1.0 plusOrMinus 0.01)
    }

    "zero release — instant cutoff at gate end" {
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 501, gateEndFrame = 500),
            attackSec = 10.0 / 48000,
            decaySec = 10.0 / 48000,
            sustainLevel = 0.8,
            releaseSec = 0.0,
        )
        // releaseFrames = 0, relRate = 1.0, result = 0.8 - (1 * 1.0) = -0.2 → clamped to 0.0
        result shouldBe (0.0 plusOrMinus 0.01)
    }

    // ── Sustain extremes ─────────────────────────────────────────────────────

    "sustain 0 — fully percussive, decays to zero" {
        // attack = 50, decay = 50, sustain = 0.0, at frame 100 (end of decay)
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 100, gateEndFrame = 1000),
            attackSec = 50.0 / 48000,
            decaySec = 50.0 / 48000,
            sustainLevel = 0.0,
            releaseSec = 0.0,
        )
        result shouldBe (0.0 plusOrMinus 0.01)
    }

    "sustain 1 — no decay, holds at peak" {
        // attack = 50, decay = 100, sustain = 1.0, at frame 100 (during would-be decay)
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 100, gateEndFrame = 1000),
            attackSec = 50.0 / 48000,
            decaySec = 100.0 / 48000,
            sustainLevel = 1.0,
            releaseSec = 0.0,
        )
        // decRate = (1 - 1) / 100 = 0, so level stays at 1.0
        result shouldBe (1.0 plusOrMinus 0.01)
    }

    // ── Negative inputs — clamped to safe values ───────────────────────────

    "negative attack/release treated as zero" {
        // Negative times should be clamped to 0 → instant attack, instant release
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 0, gateEndFrame = 1000),
            attackSec = -1.0,
            decaySec = -0.5,
            sustainLevel = 0.7,
            releaseSec = -2.0,
        )
        // attackFrames = 0, decayFrames = 0 → immediately at sustain
        result shouldBe (0.7 plusOrMinus 0.01)
    }

    // ── Boundary: gate-off at exact attack peak ──────────────────────────────

    "gate-off at exact attack-to-decay boundary" {
        // attack = 100 frames, gate ends at exactly frame 100 (attack just completed)
        // Level at gate-off should be 1.0 (peak), release from there
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 150, gateEndFrame = 100),
            attackSec = 100.0 / 48000,
            decaySec = 200.0 / 48000,
            sustainLevel = 0.5,
            releaseSec = 200.0 / 48000,
        )
        // At gate-off (frame 100): attack just finished → level = 1.0
        // (100 < 100 is false, 100 < 100+200 is true, decPos=0, so level = 1.0)
        // 50 frames into release: 1.0 - (50 * 1.0/200) = 0.75
        result shouldBe (0.75 plusOrMinus 0.02)
    }

    // ── Boundary: result clamped to [0, 1] ───────────────────────────────────

    "result is clamped to 0 when release overshoots" {
        val result = computeFilterEnvelope(
            ctx = ctx(voiceElapsedFrames = 10000, gateEndFrame = 100),
            attackSec = 10.0 / 48000,
            decaySec = 10.0 / 48000,
            sustainLevel = 0.5,
            releaseSec = 100.0 / 48000,
        )
        result shouldBe (0.0 plusOrMinus 0.001)
    }
})
