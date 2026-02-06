package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope

sealed interface Voice {

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Render Context
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Rendering context shared across all voices during a processing block.
     * Contains shared buffers and configuration for efficient batch processing.
     */
    class RenderContext(
        /** Orbit management for routing and effects */
        val orbits: Orbits,
        /** Audio sample rate in Hz */
        val sampleRate: Int,
        /** Number of frames to process per block */
        val blockFrames: Int,
        /** Shared mono buffer for voice rendering */
        val voiceBuffer: DoubleArray,
        /** Shared buffer for frequency/pitch modulation calculations */
        val freqModBuffer: DoubleArray,
    ) {
        /** Current block start frame (updated per block) */
        var blockStart: Long = 0
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Synthesis & Pitch Modulation
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * FM (Frequency Modulation) synthesis parameters.
     * Modulates the oscillator frequency with another oscillator for complex timbres.
     */
    class Fm(
        /** Frequency ratio between modulator and carrier (e.g., 1.0, 2.0, 3.14) */
        val ratio: Double,
        /** Modulation depth in Hz */
        val depth: Double,
        /** Envelope controlling modulation depth over time */
        val envelope: Envelope,
        /** Current modulator phase (state variable) */
        var modPhase: Double = 0.0,
    )

    /**
     * Pitch acceleration (glide/portamento).
     * Gradually shifts pitch over the voice's lifetime.
     */
    class Accelerate(
        /** Acceleration amount in semitones per voice duration */
        val amount: Double,
    )

    /**
     * Vibrato LFO (Low Frequency Oscillator).
     * Periodic pitch modulation for expressive wobble.
     */
    class Vibrato(
        /** LFO rate in Hz */
        val rate: Double,
        /** Modulation depth (fraction of semitone) */
        val depth: Double,
        /** Current LFO phase (state variable) */
        var phase: Double = 0.0,
    )

    /**
     * Pitch envelope for transient pitch effects.
     * Creates pitch bends during attack/decay (e.g., drum tuning, synth swoops).
     */
    class PitchEnvelope(
        /** Attack time in frames */
        val attackFrames: Double,
        /** Decay time in frames */
        val decayFrames: Double,
        /** Release time in frames */
        val releaseFrames: Double,
        /** Pitch shift amount in semitones */
        val amount: Double,
        /** Envelope curve shape (exponential vs linear) */
        val curve: Double,
        /** Anchor point: 0.0 = start at shifted pitch, 1.0 = start at base pitch */
        val anchor: Double,
    )

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Dynamics & Envelope
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * ADSR (Attack, Decay, Sustain, Release) amplitude envelope.
     * Controls volume shape over the voice's lifetime.
     */
    class Envelope(
        /** Attack time in frames */
        val attackFrames: Double,
        /** Decay time in frames */
        val decayFrames: Double,
        /** Sustain level (0.0 to 1.0) */
        val sustainLevel: Double,
        /** Release time in frames */
        val releaseFrames: Double,
        /** Current envelope level (state variable) */
        var level: Double = 0.0,
        /** Level captured the moment we enter release */
        var releaseStartLevel: Double = 0.0,
        /** Has the release phase been entered already? */
        var releaseStarted: Boolean = false,
    ) {
        companion object {
            /** Create envelope from resolved ADSR settings */
            fun of(adsr: AdsrEnvelope.Resolved, sampleRate: Int) = Envelope(
                attackFrames = adsr.attack * sampleRate,
                decayFrames = adsr.decay * sampleRate,
                sustainLevel = adsr.sustain,
                releaseFrames = adsr.release * sampleRate
            )
        }
    }

    /**
     * Compressor/limiter for dynamic range control.
     * Reduces volume of loud signals for consistent output levels.
     */
    class Compressor(
        /** Threshold in dB (signals above this are compressed) */
        val thresholdDb: Double,
        /** Compression ratio (e.g., 4:1 means 4dB input → 1dB output above threshold) */
        val ratio: Double,
        /** Knee width in dB (smoothness of compression curve) */
        val kneeDb: Double,
        /** Attack time in seconds (how fast compression engages) */
        val attackSeconds: Double,
        /** Release time in seconds (how fast compression releases) */
        val releaseSeconds: Double,
    )

    /**
     * Sidechain ducking for automatic volume reduction.
     * Lowers voice volume when another orbit is active (e.g., kick ducking bass).
     */
    class Ducking(
        /** Source orbit ID to monitor for ducking trigger */
        val orbitId: Int,
        /** Duck attack time in seconds */
        val attackSeconds: Double,
        /** Duck depth (0.0 = no ducking, 1.0 = full silence) */
        val depth: Double,
    )

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Filter Modulation
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Filter envelope modulator.
     * Sweeps filter cutoff frequency over time for dynamic tonal shaping.
     */
    class FilterModulator(
        /** Target filter to modulate (must support setCutoff) */
        val filter: AudioFilter.Tunable,
        /** Envelope controlling modulation amount over time */
        val envelope: Envelope,
        /** Modulation depth (multiplier for cutoff frequency) */
        val depth: Double,
        /** Base cutoff frequency in Hz */
        val baseCutoff: Double,
    )

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Effect Parameters
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Distortion/saturation effect parameters.
     * Adds harmonic richness through soft clipping.
     */
    class Distort(
        /** Distortion amount (0.0 = clean, 1.0+ = heavy saturation) */
        val amount: Double,
    )

    /**
     * Bit crushing effect parameters.
     * Reduces bit depth for lo-fi digital artifacts.
     */
    class Crush(
        /** Bit depth reduction amount (higher = more crushing) */
        val amount: Double,
    )

    /**
     * Sample rate reduction (coarse) effect parameters.
     * Downsamples audio for retro digital sound.
     */
    class Coarse(
        /** Downsample factor (higher = lower sample rate) */
        val amount: Double,
        /** Last sampled value (state variable) */
        var lastCoarseValue: Double = 0.0,
        /** Sample hold counter (state variable) */
        var coarseCounter: Double = 0.0,
    )

    /**
     * Phaser effect parameters.
     * All-pass filter sweep for swooshing/jet plane sounds.
     */
    class Phaser(
        /** LFO rate in Hz (sweep speed) */
        val rate: Double,
        /** Effect intensity (0.0 = off, 1.0 = full effect) */
        val depth: Double,
        /** Center frequency of sweep in Hz */
        val center: Double,
        /** Frequency range of sweep in Hz */
        val sweep: Double,
    )

    /**
     * Tremolo effect parameters.
     * Rhythmic amplitude modulation for vintage vibrato.
     */
    class Tremolo(
        /** LFO rate in Hz (modulation speed) */
        val rate: Double,
        /** Modulation depth (0.0 = off, 1.0 = full modulation) */
        val depth: Double,
        /** Waveform skew/symmetry */
        val skew: Double,
        /** Initial LFO phase offset */
        val phase: Double,
        /** Waveform shape (sine, tri, square, saw, ramp) */
        val shape: String?,
        /** Current LFO phase (state variable) */
        var currentPhase: Double = 0.0,
    )

    /**
     * Delay/echo effect parameters.
     * Time-based repetition effect (processed at orbit level).
     */
    class Delay(
        /** Wet/dry mix (0.0 = dry, 1.0 = fully wet) */
        val amount: Double,
        /** Delay time in seconds */
        val time: Double,
        /** Feedback amount (0.0 = single repeat, higher = more repeats) */
        val feedback: Double,
    )

    /**
     * Reverb effect parameters.
     * Simulates acoustic space reflections (processed at orbit level).
     */
    class Reverb(
        /** Wet/dry mix (0.0 = dry, 1.0 = fully wet) */
        val room: Double,
        /** Room size (0.0 = small, 1.0 = cathedral) - normalized from 0-10 range */
        val roomSize: Double,
        /** Reverb decay time/feedback */
        val roomFade: Double? = null,
        /** Lowpass filter cutoff for damping high frequencies */
        val roomLp: Double? = null,
        /** Dimension/diffusion amount (currently unused) */
        val roomDim: Double? = null,
        /** Impulse response file name for convolution reverb */
        val iResponse: String? = null,
    )

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Lifecycle & Routing
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /** Frame when the voice starts playing */
    val startFrame: Long

    /** Frame when the voice stops (including release phase) */
    val endFrame: Long

    /** Frame when the gate ends (release phase begins) */
    val gateEndFrame: Long

    /** Orbit/bus ID for routing to effects and mixing */
    val orbitId: Int

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Synthesis & Pitch
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /** FM synthesis parameters (modulates oscillator frequency) */
    val fm: Fm?

    /** Pitch acceleration/glide over the voice's lifetime */
    val accelerate: Accelerate

    /** Vibrato LFO (periodic pitch modulation) */
    val vibrato: Vibrato

    /** Pitch envelope (transient pitch modulation) */
    val pitchEnvelope: PitchEnvelope?

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Dynamics (Gain & Envelope)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /** Pre-envelope gain (includes velocity) */
    val gain: Double

    /** Stereo pan position (0.0 = left, 0.5 = center, 1.0 = right) */
    val pan: Double

    /** Post-envelope gain multiplier */
    val postGain: Double

    /** ADSR amplitude envelope */
    val envelope: Envelope

    /** Compressor/limiter settings for dynamic control */
    val compressor: Compressor?

    /** Sidechain ducking configuration */
    val ducking: Ducking?

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Filters & Modulation
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /** Main tonal filter (LowPass, HighPass, BandPass, Notch, Formant) */
    val filter: AudioFilter

    /** Filter envelope modulators (control cutoff over time) */
    val filterModulators: List<FilterModulator>

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Effect Pipeline (Ordered Processing Stages)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Pre-filters: Destructive/lo-fi effects applied BEFORE the main filter.
     * Order: BitCrush → SampleRateReducer (Coarse)
     * Applied early so the main filter can smooth out artifacts.
     */
    val preFilters: List<AudioFilter>

    /**
     * Post-filters: Color/modulation effects applied AFTER the envelope.
     * Order: Distortion → Phaser → Tremolo
     * Applied late to react to dynamics and shape the final tone.
     */
    val postFilters: List<AudioFilter>

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Time-Based Effects (Applied at Orbit Level)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /** Delay/echo parameters */
    val delay: Delay

    /** Reverb parameters */
    val reverb: Reverb

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Raw Effect Data (For Compatibility & Parameter Access)
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /** Phaser effect parameters (used to configure per-voice phaser filter) */
    val phaser: Phaser

    /** Tremolo effect parameters (used to configure per-voice tremolo filter) */
    val tremolo: Tremolo

    /** Distortion effect parameters (used to create distortion filter) */
    val distort: Distort

    /** Bit crush effect parameters (used to create bit crush filter) */
    val crush: Crush

    /** Sample rate reduction parameters (used to create coarse filter) */
    val coarse: Coarse

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Rendering
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Renders the voice into the context's buffers.
     *
     * Processing order:
     * 1. Source Generation (Oscillator/Sample + FM + Pitch Modulation)
     * 2. Pre-Filters (BitCrush, Coarse)
     * 3. Main Filter (with envelope modulation)
     * 4. VCA / Envelope (ADSR)
     * 5. Post-Filters (Distortion, Phaser, Tremolo)
     * 6. Mixer (Pan, Sends)
     *
     * @return true if the voice is still active, false if it has finished
     */
    fun render(ctx: RenderContext): Boolean
}
