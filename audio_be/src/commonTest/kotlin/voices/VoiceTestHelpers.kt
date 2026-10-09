/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBuffer

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.SampleIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.adsr
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.ignite.IgniteRenderer
import io.peekandpoke.klang.audio_be.voices.strip.pitch.buildPitchPipeline
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * Shared test helpers for voice tests.
 * Reduces boilerplate and ensures consistency across test files.
 */
object VoiceTestHelpers {

    /**
     * Create a render context with specified parameters.
     * All parameters have sensible defaults for most test cases.
     */
    fun createContext(
        blockStart: Double = 0.0,
        blockFrames: Int = 100,
        sampleRate: Int = 44100,
    ): Voice.RenderContext {
        return Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = AudioBuffer(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply {
            this.blockStart = blockStart
        }
    }

    /**
     * Create a minimal voice with sensible defaults.
     * Only specify the parameters you want to test.
     */
    fun createVoice(
        startFrame: Double = 0.0,
        endFrame: Double = 1000.0,
        gateEndFrame: Double = 1000.0,
        cylinderId: Int = 0,
        sampleRate: Int = 44100,
        blockFrames: Int = 100,

        // Synthesis & Pitch
        freqHz: Double = 440.0,
        signal: Ignitor = TestIgnitors.constant,
        fm: Voice.Fm? = null,
        accelerate: Voice.Accelerate = Voice.Accelerate(0.0),
        vibrato: Voice.Vibrato = Voice.Vibrato(rate = 0.0, semitones = 0.0),

        // Dynamics
        gain: Double = 1.0,
        pan: Double = 0.5,
        /**
         * The test instrument's own amplitude envelope, in frames, or null for none. It is put IN THE TREE
         * (the chain `adsr` over [signal], no de-click), because since phase 3 step 9 a voice has no envelope
         * of its own: the voice strip and its VCA retired, and the instrument's tree owns its amplitude.
         */
        envelope: Voice.Envelope? = null,

        // Cut group
        cut: Int? = null,

        // Silence culling window in seconds (null = engine default, negative = never)
        cull: Double? = null,

        // The orbit chain's param state this voice carries while it owns the orbit.
        katalystParams: Map<String, Double>? = null,

        // Stages after the tree, as the factory's `treeStages` (e.g. the teardown fade).
        treeStages: List<BlockRenderer> = emptyList(),
    ): Voice {
        // Voice-RELATIVE duration — Int, mirrors VoiceFactory. (Absolute frames are Double.)
        val voiceDurationFrames = (gateEndFrame - startFrame).toInt()

        val signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = voiceDurationFrames,
            gateEndFrame = voiceDurationFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = testRandom,
        )

        val instrument = if (envelope == null) {
            signal
        } else {
            val sr = sampleRate.toDouble()

            signal.adsr(
                attack = envelope.attackFrames / sr,
                decay = envelope.decayFrames / sr,
                sustain = envelope.sustainLevel,
                release = envelope.releaseFrames / sr,
                attackCurve = envelope.attackCurve,
                decayCurve = envelope.decayCurve,
                releaseCurve = envelope.releaseCurve,
            )
        }

        // The voice's stages: Pitch → Ignite (the Send stage is appended by the voice)
        val pipeline = buildPitchPipeline(
            vibrato = vibrato,
            accelerate = accelerate,
            fm = fm,
            freqHz = freqHz,
            sampleRate = sampleRate,
            startFrame = startFrame,
            endFrame = endFrame,
        ) + IgniteRenderer(
            signal = instrument,
            signalCtx = signalCtx,
            freqHz = freqHz,
        ) + treeStages

        val blockCtx = BlockContext(
            audioBuffer = AudioBuffer(blockFrames), // placeholder, updated per block
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
            sampleRate = sampleRate,
            limits = VoiceLimits(startFrame = startFrame, gateEndFrame = gateEndFrame, endFrame = endFrame),
        )

        return Voice(
            cylinderId = cylinderId,
            gain = gain,
            pan = pan,
            katalystParams = katalystParams,
            cut = cut,
            cull = cull,
            pipeline = pipeline,
            blockCtx = blockCtx,
        )
    }

    /** Backward-compatible alias */
    fun createSynthVoice(
        startFrame: Double = 0.0,
        endFrame: Double = 1000.0,
        gateEndFrame: Double = 1000.0,
        cylinderId: Int = 0,
        sampleRate: Int = 44100,
        blockFrames: Int = 100,
        freqHz: Double = 440.0,
        signal: Ignitor = TestIgnitors.constant,
        fm: Voice.Fm? = null,
        accelerate: Voice.Accelerate = Voice.Accelerate(0.0),
        vibrato: Voice.Vibrato = Voice.Vibrato(rate = 0.0, semitones = 0.0),
        gain: Double = 1.0,
        pan: Double = 0.5,
        envelope: Voice.Envelope? = null,
        katalystParams: Map<String, Double>? = null,
    ) = createVoice(
        startFrame = startFrame, endFrame = endFrame, gateEndFrame = gateEndFrame,
        cylinderId = cylinderId, sampleRate = sampleRate, blockFrames = blockFrames,
        freqHz = freqHz, signal = signal, fm = fm, accelerate = accelerate,
        vibrato = vibrato, gain = gain, pan = pan,
        envelope = envelope,
        katalystParams = katalystParams,
    )

    /** Create a voice with SampleIgnitor for sample playback tests. */
    fun createSampleVoice(
        sample: MonoSamplePcm,
        startFrame: Double = 0.0,
        endFrame: Double = 1000.0,
        gateEndFrame: Double = 1000.0,
        cylinderId: Int = 0,
        sampleRate: Int = 44100,
        blockFrames: Int = 100,
        freqHz: Double = 440.0,
        rate: Double = 1.0,
        playhead: Double = 0.0,
        loopStart: Double = -1.0,
        loopEnd: Double = -1.0,
        isLooping: Boolean = false,
        stopFrame: Double = Double.MAX_VALUE,
        fm: Voice.Fm? = null,
        accelerate: Voice.Accelerate = Voice.Accelerate(0.0),
        vibrato: Voice.Vibrato = Voice.Vibrato(rate = 0.0, semitones = 0.0),
        gain: Double = 1.0,
        pan: Double = 0.5,
        envelope: Voice.Envelope? = null,
    ) = createVoice(
        startFrame = startFrame, endFrame = endFrame, gateEndFrame = gateEndFrame,
        cylinderId = cylinderId, sampleRate = sampleRate, blockFrames = blockFrames,
        freqHz = freqHz,
        signal = SampleIgnitor(
            pcm = sample.pcm,
            rate = rate,
            playhead = playhead,
            loopStart = loopStart,
            loopEnd = loopEnd,
            isLooping = isLooping,
            stopFrame = stopFrame,
            sampleRate = sampleRate,
            rng = testRandom,
        ),
        fm = fm, accelerate = accelerate,
        vibrato = vibrato, gain = gain, pan = pan,
        envelope = envelope,
    )
}

/**
 * Test sample generators.
 * Create predictable audio data for testing.
 */
object TestSamples {
    fun silence(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = AudioBuffer(size) { 0.0 },
            meta = SampleMetadata(loop = null, adsr = null, anchor = 0.0)
        )
    }

    fun impulse(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = AudioBuffer(size) { if (it == 0) 1.0 else 0.0 },
            meta = SampleMetadata(loop = null, adsr = null, anchor = 0.0)
        )
    }

    fun ramp(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = AudioBuffer(size) { it.toDouble() / (size - 1) },
            meta = SampleMetadata(loop = null, adsr = null, anchor = 0.0)
        )
    }

    fun sine(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = AudioBuffer(size) {
                sin(2.0 * PI * it / size)
            },
            meta = SampleMetadata(loop = null, adsr = null, anchor = 0.0)
        )
    }

    fun constant(size: Int, value: Double = 0.5, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = AudioBuffer(size) { value },
            meta = SampleMetadata(loop = null, adsr = null, anchor = 0.0)
        )
    }
}

/**
 * Test signal generators (Ignitor interface).
 */
object TestIgnitors {
    val constant: Ignitor = object : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                buffer[i] = 1.0
            }
        }
    }

    val ramp: Ignitor = object : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                buffer[i] = (i - ctx.offset).toDouble() / ctx.length
            }
        }
    }

    val silence: Ignitor = object : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                buffer[i] = 0.0
            }
        }
    }
}
