package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * Tests for ADSR envelope implementation in Voice.
 * Verifies all phases (Attack, Decay, Sustain, Release) work correctly.
 *
 * These tests pin the envelope to LINEAR curves on all stages, so the assertions
 * remain valid regardless of the default-curve change. Curve-specific behaviour
 * (Square, Cube) is covered separately in [EnvelopeShapeTest].
 *
 * NOTE: the VCA applies a short one-pole de-click smoother to the gain
 * (ENV_DECLICK_SECONDS, see AdsrCurveMath). So mid-ramp values lag slightly and
 * an "instant" transition (zero attack/decay/release) fades over ~0.5ms instead
 * of one sample. These tests therefore assert phase *behaviour* (primed start,
 * monotonic direction, settled endpoints, the de-click fade) rather than exact
 * mid-ramp amplitudes — the precise raw-curve shape lives in [EnvelopeShapeTest],
 * which evaluates the generator directly and is unaffected by the smoother.
 */
class EnvelopeTest : StringSpec({

    fun linearEnv(
        attackFrames: Double, decayFrames: Double, sustainLevel: Double, releaseFrames: Double,
    ) = Voice.Envelope(
        attackFrames = attackFrames,
        decayFrames = decayFrames,
        sustainLevel = sustainLevel,
        releaseFrames = releaseFrames,
        attackCurve = AdsrCurve.Linear,
        decayCurve = AdsrCurve.Linear,
        releaseCurve = AdsrCurve.Linear,
    )

    "attack phase increases linearly from 0 to 1" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        // Attack rises monotonically from ~0 (de-click lags the exact ramp; shape
        // precision is in EnvelopeShapeTest).
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.02)
        (ctx.voiceBuffer[50] > ctx.voiceBuffer[0]) shouldBe true
        (ctx.voiceBuffer[99] > ctx.voiceBuffer[50]) shouldBe true
        (ctx.voiceBuffer[99] > 0.6) shouldBe true
    }

    "decay phase decreases from 1 to sustain level" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 100.0,
                sustainLevel = 0.5,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // Render decay + a long sustain tail so the gain settles regardless of the declick
        // time constant (tau-agnostic: direction + settled plateau, not an after-N-frames
        // magnitude — exact shape lives in EnvelopeShapeTest).
        val ctx = createContext(blockStart = 100, blockFrames = 600)
        voice.render(ctx)

        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.03)          // decay start ~1.0
        (ctx.voiceBuffer[50] < ctx.voiceBuffer[0]) shouldBe true    // falling
        (ctx.voiceBuffer[99] < ctx.voiceBuffer[50]) shouldBe true
        ctx.voiceBuffer[550] shouldBe (0.5 plusOrMinus 0.02)        // settled at sustain
    }

    "sustain phase holds at sustain level" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 1000,
            envelope = Voice.Envelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                sustainLevel = 0.6,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // Render at sustain phase (frame 200-300, after attack+decay)
        val ctx = createContext(blockStart = 200, blockFrames = 100)
        voice.render(ctx)

        // All samples should be at sustain level
        ctx.voiceBuffer[0] shouldBe (0.6 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (0.6 plusOrMinus 0.01)
        ctx.voiceBuffer[99] shouldBe (0.6 plusOrMinus 0.01)
    }

    "release phase decays from sustain to zero" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 800,
            gateEndFrame = 100, // Gate ends at 100, release starts
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 100.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // Render sustain phase first to establish envelope state
        voice.render(createContext(blockStart = 0, blockFrames = 100))

        // Release + a long tail so the gain settles regardless of declick tau (tau-agnostic:
        // direction + settled silence, not an after-N-frames magnitude).
        val ctx = createContext(blockStart = 100, blockFrames = 600)
        voice.render(ctx)

        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.03)          // release start ~1.0
        (ctx.voiceBuffer[50] < ctx.voiceBuffer[0]) shouldBe true    // falling
        (ctx.voiceBuffer[99] < ctx.voiceBuffer[50]) shouldBe true
        ctx.voiceBuffer[550] shouldBe (0.0 plusOrMinus 0.02)        // settled at silence
    }

    "zero attack time produces immediate full amplitude" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100,
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        // First sample should already be at full amplitude
        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (1.0 plusOrMinus 0.01)
    }

    "zero decay time transitions immediately to sustain" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 200,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 0.5,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // Render at frame 100 (end of attack, start of decay)
        val ctx = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx)

        // Should immediately be at sustain level
        ctx.voiceBuffer[0] shouldBe (0.5 plusOrMinus 0.02)
    }

    "zero release time produces very fast decay" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 700,
            gateEndFrame = 100,
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // Render sustain phase first to establish envelope state
        voice.render(createContext(blockStart = 0, blockFrames = 100))

        // Render the cutoff + a long tail so the fade settles regardless of declick tau.
        val ctx = createContext(blockStart = 100, blockFrames = 600)
        voice.render(ctx)

        // releaseFrames=0 is de-clicked: instead of a 1-sample cutoff (a click) the gain
        // fades over ~ENV_DECLICK_SECONDS, then settles at silence.
        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.02)        // still at sustain at relPos 0
        (ctx.voiceBuffer[1] < ctx.voiceBuffer[0]) shouldBe true   // fading (declining), not a hard cut
        (ctx.voiceBuffer[1] > 0.5) shouldBe true                  // ...nowhere near gone in one sample
        ctx.voiceBuffer[550] shouldBe (0.0 plusOrMinus 0.02)      // settled at silence
    }

    "full ADSR cycle works correctly" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 800,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 100.0,
                sustainLevel = 0.5,
                releaseFrames = 100.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // One contiguous block (attack 0-100, decay 100-200, sustain 200-800, release
        // 800-900). The long sustain lets the gain settle, so the plateau check is
        // tau-agnostic; the rest is direction (exact shape is in EnvelopeShapeTest).
        val ctx = createContext(blockStart = 0, blockFrames = 1000)
        voice.render(ctx)

        (ctx.voiceBuffer[80] > ctx.voiceBuffer[20]) shouldBe true    // attack rising
        (ctx.voiceBuffer[90] > ctx.voiceBuffer[700]) shouldBe true   // peak/decay above sustain
        ctx.voiceBuffer[700] shouldBe (0.5 plusOrMinus 0.02)         // sustain settled
        (ctx.voiceBuffer[850] < ctx.voiceBuffer[700]) shouldBe true  // release below sustain
        (ctx.voiceBuffer[899] < ctx.voiceBuffer[850]) shouldBe true  // ...and still falling
    }

    "envelope state is preserved across multiple renders" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 500,
            envelope = Voice.Envelope(
                attackFrames = 200.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // Render first half of attack
        val ctx1 = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx1)
        val firstHalfValue = ctx1.voiceBuffer[99]

        // Render second half of attack
        val ctx2 = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx2)
        val secondHalfStart = ctx2.voiceBuffer[0]

        // Second render should continue where first left off
        secondHalfStart shouldBe (firstHalfValue plusOrMinus 0.02)
    }

    "envelope clamps negative values to zero" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 300,
            gateEndFrame = 100,
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 0.5,
                releaseFrames = 50.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // Render well past release end (frame 200, release ends at 150)
        val ctx = createContext(blockStart = 200, blockFrames = 100)
        voice.render(ctx)

        // Should be clamped at 0, not negative
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (0.0 plusOrMinus 0.01)
    }

    "envelope with very small attack works correctly" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100,
            envelope = Voice.Envelope(
                attackFrames = 1.0, // Very short attack
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext(blockStart = 0, blockFrames = 10)
        voice.render(ctx)

        // attack=1 frame is de-clicked: the gain rises smoothly over ~ENV_DECLICK_SECONDS
        // instead of jumping to full amplitude in a single sample.
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.05)
        (ctx.voiceBuffer[1] > ctx.voiceBuffer[0]) shouldBe true   // rising
        (ctx.voiceBuffer[9] > ctx.voiceBuffer[1]) shouldBe true   // still rising over the block
    }

    "envelope with sustain level of 0 produces silence after decay" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 300,
            envelope = Voice.Envelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                sustainLevel = 0.0,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // Render at sustain phase (after attack+decay)
        val ctx = createContext(blockStart = 150, blockFrames = 100)
        voice.render(ctx)

        // Should be silent
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (0.0 plusOrMinus 0.01)
    }

    "envelope respects gate end frame for release timing" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 900,
            gateEndFrame = 700, // Gate ends at 700
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 100.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        // One contiguous block (attack 0-100, sustain 100-700, release 700-800). The long
        // sustain lets the gain settle, so the "holds until the gate" check is tau-agnostic.
        val ctx = createContext(blockStart = 0, blockFrames = 800)
        voice.render(ctx)

        // Sustain holds at 1.0 right up to the gate end at frame 700.
        ctx.voiceBuffer[650] shouldBe (1.0 plusOrMinus 0.02)        // settled sustain, before gate
        ctx.voiceBuffer[699] shouldBe (1.0 plusOrMinus 0.02)        // just before gate end
        // Release begins only after the gate, then falls.
        (ctx.voiceBuffer[750] < ctx.voiceBuffer[700]) shouldBe true // releasing
        (ctx.voiceBuffer[750] > 0.3) shouldBe true                  // ...about midway down
    }
})
