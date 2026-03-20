package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.signalgen.ScratchBuffers
import io.peekandpoke.klang.audio_be.signalgen.SignalContext
import io.peekandpoke.klang.audio_be.signalgen.SignalGen
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
        blockStart: Long = 0,
        blockFrames: Int = 100,
        sampleRate: Int = 44100,
    ): Voice.RenderContext {
        return Voice.RenderContext(
            orbits = Orbits(blockFrames = blockFrames, sampleRate = sampleRate),
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
     * Create a minimal SynthVoice with sensible defaults.
     * Only specify the parameters you want to test.
     */
    fun createSynthVoice(
        startFrame: Long = 0,
        endFrame: Long = 1000,
        gateEndFrame: Long = 1000,
        orbitId: Int = 0,
        sampleRate: Int = 44100,
        blockFrames: Int = 100,

        // Synthesis & Pitch
        freqHz: Double = 440.0,
        signal: SignalGen = TestSignalGens.constant,
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
    ): SynthVoice {
        val voiceDurationFrames = (gateEndFrame - startFrame).toInt()
        val releaseFrames = (endFrame - gateEndFrame).toInt()

        return SynthVoice(
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            orbitId = orbitId,
            fm = fm,
            accelerate = accelerate,
            vibrato = vibrato,
            pitchEnvelope = pitchEnvelope,
            gain = gain,
            pan = pan,
            postGain = postGain,
            envelope = envelope,
            compressor = compressor,
            ducking = ducking,
            filter = filter,
            filterModulators = filterModulators,
            delay = delay,
            reverb = reverb,
            phaser = phaser,
            tremolo = tremolo,
            distort = distort,
            crush = crush,
            coarse = coarse,
            signal = signal,
            signalCtx = SignalContext(
                sampleRate = sampleRate,
                voiceDurationFrames = voiceDurationFrames,
                gateEndFrame = voiceDurationFrames,
                releaseFrames = releaseFrames,
                voiceEndFrame = voiceDurationFrames + releaseFrames,
                scratchBuffers = ScratchBuffers(blockFrames),
            ),
            freqHz = freqHz,
        )
    }

    /**
     * Create a minimal SampleVoice with sensible defaults.
     */
    fun createSampleVoice(
        sample: MonoSamplePcm,
        startFrame: Long = 0,
        endFrame: Long = 1000,
        gateEndFrame: Long = 1000,
        orbitId: Int = 0,

        // Sample-specific
        samplePlayback: SampleVoice.SamplePlayback = SampleVoice.SamplePlayback.default,
        rate: Double = 1.0,
        playhead: Double = 0.0,

        // Synthesis & Pitch
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
    ): SampleVoice {
        return SampleVoice(
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            orbitId = orbitId,
            fm = fm,
            accelerate = accelerate,
            vibrato = vibrato,
            pitchEnvelope = pitchEnvelope,
            gain = gain,
            pan = pan,
            postGain = postGain,
            envelope = envelope,
            compressor = compressor,
            ducking = ducking,
            filter = filter,
            filterModulators = filterModulators,
            delay = delay,
            reverb = reverb,
            phaser = phaser,
            tremolo = tremolo,
            distort = distort,
            crush = crush,
            coarse = coarse,
            samplePlayback = samplePlayback,
            sample = sample,
            rate = rate,
            playhead = playhead
        )
    }

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
    /**
     * Create a silent sample (all zeros).
     */
    fun silence(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) { 0.0f },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    /**
     * Create an impulse sample (1.0 at index 0, rest zeros).
     */
    fun impulse(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) { if (it == 0) 1.0f else 0.0f },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    /**
     * Create a ramp sample (linear increase from 0.0 to 1.0).
     */
    fun ramp(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) { it.toFloat() / (size - 1) },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    /**
     * Create a sine wave sample (one complete cycle).
     */
    fun sine(size: Int, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) {
                sin(2.0 * PI * it / size).toFloat()
            },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    /**
     * Create a constant sample (all values equal).
     */
    fun constant(size: Int, value: Float = 0.5f, sampleRate: Int = 44100): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = FloatArray(size) { value },
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }
}

/**
 * Test oscillators for predictable signal generation (legacy OscFn interface).
 */
object TestOscillators {
    val constant = io.peekandpoke.klang.audio_be.osci.OscFn { buffer, offset, length, phase, _, _ ->
        for (i in 0 until length) {
            buffer[offset + i] = 1.0f
        }
        phase
    }

    val ramp = io.peekandpoke.klang.audio_be.osci.OscFn { buffer, offset, length, phase, _, _ ->
        for (i in 0 until length) {
            buffer[offset + i] = (i.toFloat() / length)
        }
        phase
    }

    val silence = io.peekandpoke.klang.audio_be.osci.OscFn { buffer, offset, length, phase, _, _ ->
        for (i in 0 until length) {
            buffer[offset + i] = 0.0f
        }
        phase
    }
}

/**
 * Test signal generators (SignalGen interface).
 */
object TestSignalGens {
    val constant = SignalGen { buffer, _, ctx ->
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = 1.0f
        }
    }

    val ramp = SignalGen { buffer, _, ctx ->
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = (i - ctx.offset).toFloat() / ctx.length
        }
    }

    val silence = SignalGen { buffer, _, ctx ->
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = 0.0f
        }
    }
}
