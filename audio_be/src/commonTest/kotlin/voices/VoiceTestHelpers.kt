package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.AudioEngine
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.SampleIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.filter.buildFilterPipeline
import io.peekandpoke.klang.audio_be.voices.strip.ignite.IgniteRenderer
import io.peekandpoke.klang.audio_be.voices.strip.pitch.buildPitchPipeline
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import kotlin.math.PI
import kotlin.math.sin

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
        blockStart: Int = 0,
        blockFrames: Int = 100,
        sampleRate: Int = 44100,
    ): Voice.RenderContext {
        return Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = FloatArray(blockFrames),
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
        startFrame: Int = 0,
        endFrame: Int = 1000,
        gateEndFrame: Int = 1000,
        cylinderId: Int = 0,
        sampleRate: Int = 44100,
        blockFrames: Int = 100,

        // Synthesis & Pitch
        freqHz: Double = 440.0,
        signal: Ignitor = TestIgnitors.constant,
        fm: Voice.Fm? = null,
        accelerate: Voice.Accelerate = Voice.Accelerate(0.0),
        vibrato: Voice.Vibrato = Voice.Vibrato(0.0, 0.0),
        pitchEnvelope: Voice.PitchEnvelope? = null,

        // Dynamics
        gain: Double = 1.0,
        pan: Double = 0.5,
        postGain: Double = 1.0,
        envelope: Voice.Envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0, 1.0), // Always on
        compressor: Voice.Compressor? = null,
        ducking: Voice.Ducking? = null,

        // Filters & Modulation
        filter: AudioFilter = NoOpFilter,
        filterModulators: List<Voice.FilterModulator> = emptyList(),

        // Time-Based Effects
        delay: Voice.Delay = Voice.Delay(0.0, 0.0, 0.0),
        reverb: Voice.Reverb = Voice.Reverb(0.0, 0.0),

        // Raw Effect Data
        phaser: Voice.Phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
        tremolo: Voice.Tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
        distort: Voice.Distort = Voice.Distort(0.0),
        crush: Voice.Crush = Voice.Crush(0.0),
        coarse: Voice.Coarse = Voice.Coarse(0.0),

        // Cut group
        cut: Int? = null,
    ): Voice {
        val voiceDurationFrames = gateEndFrame - startFrame
        val releaseFrames = endFrame - gateEndFrame

        val signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = voiceDurationFrames,
            gateEndFrame = voiceDurationFrames,
            releaseFrames = releaseFrames,
            voiceEndFrame = voiceDurationFrames + releaseFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        // Build strip pipeline: Pitch → Ignite → Filter
        val pipeline = buildPitchPipeline(
            vibrato = vibrato,
            accelerate = accelerate,
            pitchEnvelope = pitchEnvelope,
            fm = fm,
            freqHz = freqHz,
            sampleRate = sampleRate,
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
        ) + IgniteRenderer(
            signal = signal,
            signalCtx = signalCtx,
            freqHz = freqHz,
            startFrame = startFrame,
        ) + buildFilterPipeline(
            engine = AudioEngine.Modern,
            modulators = filterModulators,
            startFrame = startFrame,
            gateEndFrame = gateEndFrame,
            crush = crush,
            coarse = coarse,
            mainFilter = filter,
            envelope = envelope,
            distort = distort,
            tremolo = tremolo,
            phaser = phaser,
            sampleRate = sampleRate,
        )

        val blockCtx = BlockContext(
            audioBuffer = FloatArray(blockFrames), // placeholder, updated per block
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
            sampleRate = sampleRate,
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            freqHz = freqHz,
            signal = signal,
            signalCtx = signalCtx,
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
        )

        return Voice(
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            cylinderId = cylinderId,
            gain = gain,
            pan = pan,
            postGain = postGain,
            compressor = compressor,
            ducking = ducking,
            delay = delay,
            reverb = reverb,
            phaser = phaser,
            cut = cut,
            pipeline = pipeline,
            blockCtx = blockCtx,
        )
    }

    /** Backward-compatible alias */
    fun createSynthVoice(
        startFrame: Int = 0,
        endFrame: Int = 1000,
        gateEndFrame: Int = 1000,
        cylinderId: Int = 0,
        sampleRate: Int = 44100,
        blockFrames: Int = 100,
        freqHz: Double = 440.0,
        signal: Ignitor = TestIgnitors.constant,
        fm: Voice.Fm? = null,
        accelerate: Voice.Accelerate = Voice.Accelerate(0.0),
        vibrato: Voice.Vibrato = Voice.Vibrato(0.0, 0.0),
        pitchEnvelope: Voice.PitchEnvelope? = null,
        gain: Double = 1.0,
        pan: Double = 0.5,
        postGain: Double = 1.0,
        envelope: Voice.Envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0, 1.0),
        compressor: Voice.Compressor? = null,
        ducking: Voice.Ducking? = null,
        filter: AudioFilter = NoOpFilter,
        filterModulators: List<Voice.FilterModulator> = emptyList(),
        delay: Voice.Delay = Voice.Delay(0.0, 0.0, 0.0),
        reverb: Voice.Reverb = Voice.Reverb(0.0, 0.0),
        phaser: Voice.Phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
        tremolo: Voice.Tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
        distort: Voice.Distort = Voice.Distort(0.0),
        crush: Voice.Crush = Voice.Crush(0.0),
        coarse: Voice.Coarse = Voice.Coarse(0.0),
    ) = createVoice(
        startFrame = startFrame, endFrame = endFrame, gateEndFrame = gateEndFrame,
        cylinderId = cylinderId, sampleRate = sampleRate, blockFrames = blockFrames,
        freqHz = freqHz, signal = signal, fm = fm, accelerate = accelerate,
        vibrato = vibrato, pitchEnvelope = pitchEnvelope, gain = gain, pan = pan,
        postGain = postGain, envelope = envelope, compressor = compressor,
        ducking = ducking, filter = filter, filterModulators = filterModulators,
        delay = delay, reverb = reverb, phaser = phaser, tremolo = tremolo,
        distort = distort, crush = crush, coarse = coarse,
    )

    /** Create a voice with SampleIgnitor for sample playback tests. */
    fun createSampleVoice(
        sample: MonoSamplePcm,
        startFrame: Int = 0,
        endFrame: Int = 1000,
        gateEndFrame: Int = 1000,
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
        vibrato: Voice.Vibrato = Voice.Vibrato(0.0, 0.0),
        pitchEnvelope: Voice.PitchEnvelope? = null,
        gain: Double = 1.0,
        pan: Double = 0.5,
        postGain: Double = 1.0,
        envelope: Voice.Envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0, 1.0),
        compressor: Voice.Compressor? = null,
        ducking: Voice.Ducking? = null,
        filter: AudioFilter = NoOpFilter,
        filterModulators: List<Voice.FilterModulator> = emptyList(),
        delay: Voice.Delay = Voice.Delay(0.0, 0.0, 0.0),
        reverb: Voice.Reverb = Voice.Reverb(0.0, 0.0),
        phaser: Voice.Phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
        tremolo: Voice.Tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
        distort: Voice.Distort = Voice.Distort(0.0),
        crush: Voice.Crush = Voice.Crush(0.0),
        coarse: Voice.Coarse = Voice.Coarse(0.0),
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
        ),
        fm = fm, accelerate = accelerate,
        vibrato = vibrato, pitchEnvelope = pitchEnvelope, gain = gain, pan = pan,
        postGain = postGain, envelope = envelope, compressor = compressor,
        ducking = ducking, filter = filter, filterModulators = filterModulators,
        delay = delay, reverb = reverb, phaser = phaser, tremolo = tremolo,
        distort = distort, crush = crush, coarse = coarse,
    )

    /**
     * No-op filter that does nothing.
     * Useful as default when you don't care about filtering.
     */
    object NoOpFilter : AudioFilter {
        override fun process(buffer: FloatArray, offset: Int, length: Int) {
            // Do nothing
        }
    }

    /**
     * Spy filter that tracks all process() calls.
     * Useful for verifying execution order and parameters.
     */
    open class SpyFilter(val name: String = "spy") : AudioFilter {
        data class ProcessCall(val offset: Int, val length: Int, val callIndex: Int)

        val processCalls = mutableListOf<ProcessCall>()

        override fun process(buffer: FloatArray, offset: Int, length: Int) {
            processCalls.add(ProcessCall(offset, length, processCalls.size))
        }

        open fun reset() {
            processCalls.clear()
        }
    }

    /**
     * Tunable spy filter that also tracks setCutoff() calls.
     * Useful for testing filter modulation.
     */
    class TunableSpyFilter(name: String = "tunableSpy") : SpyFilter(name), AudioFilter.Tunable {
        val cutoffHistory = mutableListOf<Double>()
        var currentCutoff = 0.0

        override fun setCutoff(cutoffHz: Double) {
            currentCutoff = cutoffHz
            cutoffHistory.add(cutoffHz)
        }

        override fun reset() {
            super.reset()
            cutoffHistory.clear()
            currentCutoff = 0.0
        }
    }
}

/**
 * Test sample generators.
 * Create predictable audio data for testing.
 */
object TestSamples {
    fun silence(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) { 0.0f },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    fun impulse(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) { if (it == 0) 1.0f else 0.0f },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    fun ramp(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) { it.toFloat() / (size - 1) },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    fun sine(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) {
                sin(2.0 * PI * it / size).toFloat()
            },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    fun constant(size: Int, value: Float = 0.5f, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) { value },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }
}

/**
 * Test signal generators (Ignitor interface).
 */
object TestIgnitors {
    val constant = Ignitor { buffer, _, ctx ->
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = 1.0f
        }
    }

    val ramp = Ignitor { buffer, _, ctx ->
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = (i - ctx.offset).toFloat() / ctx.length
        }
    }

    val silence = Ignitor { buffer, _, ctx ->
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = 0.0f
        }
    }
}
