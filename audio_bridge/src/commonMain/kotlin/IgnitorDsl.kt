/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.EQ_BAND_DB
import io.peekandpoke.klang.audio_bridge.constants.EQ_TAP_GAIN
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DEPTH_SEMITONES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.FILTER_PASSES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_Q
import io.peekandpoke.klang.audio_bridge.constants.FM_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FM_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FM_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FM_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.FM_RATIO
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.PULSE_FALL_FLANK
import io.peekandpoke.klang.audio_bridge.constants.PULSE_MIN_FLANK_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.PULSE_RISE_FLANK
import io.peekandpoke.klang.audio_bridge.constants.RAMP_RESET_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.RAMP_SHAPE_MAX
import io.peekandpoke.klang.audio_bridge.constants.RANGEX_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.SAW_RESET_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.SAW_SHAPE_MAX
import io.peekandpoke.klang.audio_bridge.constants.SHAPE_OVERSAMPLE
import io.peekandpoke.klang.audio_bridge.constants.SHIMMER_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.SHIMMER_PITCHES
import io.peekandpoke.klang.audio_bridge.constants.SHIMMER_TONE_HZ
import io.peekandpoke.klang.audio_bridge.constants.SHIMMER_WET
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_PHASE_POOL
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_PHASE_POOL
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_PHASE_POOL
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_PHASE_POOL
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_PHASE_POOL
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.TREMOLO_RANGE_FROM
import io.peekandpoke.klang.audio_bridge.constants.TREMOLO_RANGE_TO
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_PHASE
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_RANGE_FROM
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_RANGE_TO
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_SEMITONES



/**
 * Process-local counter used to stamp each noise-source DSL instance with a unique
 * [IgnitorDsl.WhiteNoise.uid] / [IgnitorDsl.BrownNoise.uid] / [IgnitorDsl.PinkNoise.uid].
 *
 * Two calls to `Ignitor.whitenoise()` produce two different DSL objects with different uids —
 * and therefore two independent Ignitors under the identity-caching `toExciter`. Binding to
 * a variable and re-using it keeps a single instance, so `let s = Ignitor.whitenoise(); s + s`
 * still collapses to one shared stream summed with itself.
 *
 * DSL construction is single-threaded in practice (the audio bridge is built from the UI
 * thread before being serialised to the worklet), so a plain counter is sufficient.
 */
private var noiseUidCounter: Int = 0

internal fun nextNoiseUid(): Int = noiseUidCounter++

/**
 * Serializable sealed DSL for describing exciter signal graphs.
 *
 * Each subtype represents a primitive oscillator, noise source, effect, filter, envelope,
 * or arithmetic combinator. Subtrees are composed declaratively and serialized across the
 * audio bridge boundary for rendering in the audio worklet. A node referenced twice (a `let` used twice) crosses
 * that boundary as one object ([WireShared]), so the worklet builds it once, as the JVM does.
 */
@WireFormat
@WireShared
sealed interface IgnitorDsl {

    /** Recursively collects all [Param] leaf nodes in this DSL subtree into [out]. */
    fun collectParams(out: MutableList<Param>)

    // ═════════════════════════════════════════════════════════════════════════════
    // Parameter Slot
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * A named parameter slot that produces a constant signal by default.
     *
     * At runtime, fills a buffer with [default]. Can be replaced with any [IgnitorDsl]
     * subtree to modulate the parameter at audio rate.
     *
     * A non-finite override reads as UNSET and the leaf falls back to [default]
     * (`/dsl-design` section 4, resolved in `IgnitorDslRuntime`).
     *
     * [Slots] holds the names a frontend may already write, so placing one of those wires that
     * door into an instrument. The one to know is [Slots.pregain]: how hard the pattern plays
     * INTO the instrument, written by sprudel's `pregain(x)`, default 1.0, with no meaning beyond
     * where the tree places it. Reach for `Slots.<name>` rather than retyping a name and a default
     * here, so one default serves every instrument; a name of your own is what this door is for.
     *
     * @param name parameter name — used for discovery, UI display, and ignitorParams override matching
     * @param default constant value when no modulator is wired in
     * @param description human-readable description for UI tooltips and auto-generated docs
     */
    @WireName("param")
    data class Param(
        val name: String,
        val default: Double,
        val description: String = "",
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            out.add(this)
        }
    }

    /**
     * A fixed constant value that cannot be overridden by ignitorParams.
     *
     * Use when the user explicitly sets a value (e.g. `Ignitor.sine(5)` = 5 Hz).
     * Unlike [Param], this is not discoverable and not overridable at play time.
     */
    @WireName("const")
    data class Constant(
        val value: Double,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /**
     * The voice's note frequency (e.g. 440 Hz for A4).
     * Use anywhere a frequency value is needed to track the played note.
     */
    @WireName("freq")
    data object Freq : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /**
     * Canonical open parameter slots that mirror sprudel's `ignp(name, value)` calls.
     *
     * Use these when defining a custom sound that should respond to sprudel modulation
     * (e.g. `note("c").analog(0.3)`). Each slot is a `Param(name, default)` singleton with
     * the same name + default value that sprudel addons expect.
     *
     * Builtin sounds (`IgnitorDefaults.kt`) wire these in automatically; custom sounds
     * opt in explicitly:
     * ```
     * let mypad = Ignitor.sine(x => x.analog(Ignitor.slot.analog))
     * note("c").sound(mypad)
     * ```
     */
    object Slots {
        val analog: IgnitorDsl = Param(name = "analog", default = 0.0)
        val bipolar: IgnitorDsl = Param(name = "bipolar", default = 0.0)
        val brightness: IgnitorDsl = Param(name = "brightness", default = 0.5)
        val chaos: IgnitorDsl = Param(name = "chaos", default = 1.5)
        val color: IgnitorDsl = Param(name = "color", default = 0.0)
        val declick: IgnitorDsl = Param(name = "declick", default = 0.0)
        val density: IgnitorDsl = Param(name = "density", default = 0.2)
        val duty: IgnitorDsl = Param(name = "duty", default = 0.5)
        val feedback: IgnitorDsl = Param(name = "feedback", default = 0.996)
        val leak: IgnitorDsl = Param(name = "leak", default = 0.02)

        val octaves: IgnitorDsl = Param(name = "octaves", default = 1.0)
        val persistence: IgnitorDsl = Param(name = "persistence", default = 0.5)
        val pickPosition: IgnitorDsl = Param(name = "pickPosition", default = 0.5)

        /**
         * How hard the sound is played INTO the instrument: the level at which the signal meets
         * the instrument's first nonlinearity. The pattern writes it with `pregain(x)`.
         *
         * **An ordinary slot: it does what the instrument wires it to, and nothing otherwise**
         * (the signal-flow plan, section 6). An instrument that never places it ignores
         * `pregain(x)` bit for bit, and that surprises nobody, because a bare sine has no drive.
         * Place it ONCE, in front of the nonlinearity it is meant to drive, not once per
         * oscillator: a sum is linear, so driving the sum is the same sound and one multiply.
         *
         * It changes TIMBRE only where the tree puts a nonlinearity after it, and only where that
         * nonlinearity has somewhere left to go. On a tree with no nonlinearity it is
         * mathematically just a level; on a CLIPPING shape already in hard saturation it is
         * neither, because a clipper holds the tone AND the level (measured: a shape distance of
         * 0.006 and a level ratio of 0.999 at `distort(2)`, against 0.223 at `distort(0.5)`).
         * Both are worth saying plainly rather than promising tone the slot cannot deliver. The
         * tone-neutral level word is `gain`, the channel fader after the whole instrument, and on
         * a heavily driven CLIPPER it is the only one that still moves anything.
         *
         * The WAVEFOLDERS are the exception and it is a big one: `fold`, `linearfold` and
         * `sineshaper` never saturate, so there this slot is the fold depth and the strongest tone
         * knob at any drive (a shape distance of 1.36 to 1.76 at `distort(2)`), and it does not
         * behave like a level at all: on `fold` at that drive, HALVING it makes the sound about
         * four times louder.
         */
        val pregain: IgnitorDsl = Param(name = "pregain", default = 1.0)
        val rate: IgnitorDsl = Param(name = "rate", default = 1.0)
        val spread: IgnitorDsl = Param(name = "spread", default = 0.2)
        val stiffness: IgnitorDsl = Param(name = "stiffness", default = 0.0)
        val tail: IgnitorDsl = Param(name = "tail", default = 1.0)
        val voices: IgnitorDsl = Param(name = "voices", default = 8.0)

        // ── The slots of the classic tail (phase 3 step 5), one group per stage ──
        //
        // `classic()` places them; an author writing a tail of their own places the same ones, and
        // the same doors fill them. Named `<door>.<param>`: the sprudel door's name, then the engine
        // door's word (`lpf.freq`, `crush.bits`); each group's KDoc in `IgnitorDslClassic.kt` names
        // the defaults and why.

        /** The vibrato stage, outside the accelerate: `vibrato.rate` and `vibrato.semitones` (the switch). */
        val vibrato: VibratoSlots = VibratoSlots()

        /**
         * The accelerate stage, between the pitch envelope and the vibrato: the glide in SEMITONES from the onset to the
         * gate close, held through the release (decision D2), which sprudel's `accelerate(semitones)` writes. It is the
         * stage's switch: default 0.0, which the gate reads as off (a non-finite value reads as unset, so off too). A
         * flat name, like [onepole]: the door has one knob (pitch pipeline step 3; decision D4).
         */
        val accelerate: IgnitorDsl = Param(name = "accelerate", default = 0.0, description = "Mirrors sprudel's reader `accelerate`")

        /**
         * The FM stage, `classic()`'s innermost pitch stage (pitch pipeline step 4): `fm.ratio`, `fm.depth` (the switch)
         * and the depth envelope's `fm.attack`, `fm.decay`, `fm.sustain`, `fm.release`.
         */
        val fm: FmSlots = FmSlots()

        /** The pitch envelope stage, around the FM: `penv.semitones` (the switch) and its four stages. */
        val penv: PitchEnvelopeSlots = PitchEnvelopeSlots()

        /** The pitch envelope's curves: `penvCurves.attack`, `penvCurves.decay`, `penvCurves.release`. */
        val penvCurves: ModEnvelopeCurvesSlots = ModEnvelopeCurvesSlots("penvCurves")

        /**
         * The one-pole lowpass stage, `classic()`'s first AMPLITUDE stage (behind the pitch stages): the cutoff in Hz, which sprudel's
         * `onepole(hz)` writes. Default 0.0, which the gate reads as off. A flat name, not `<door>.<param>`:
         * the door has one knob, and `onepole` is the key it has always written (phase 3 step 10 moved the
         * stage from the registry into `classic()`).
         */
        val onepole: IgnitorDsl = Param(name = "onepole", default = 0.0, description = "Mirrors sprudel's reader `onepole`")

        /** The crush stage: `crush.bits`. */
        val crush: CrushSlots = CrushSlots()

        /** The coarse stage: `coarse.factor`. */
        val coarse: CoarseSlots = CoarseSlots()

        /** The distort stage: `distort.amount`, `distort.shape`, `distort.oversample`. */
        val distort: DistortSlots = DistortSlots()

        /** The highpass stage: `hpf.freq`, `hpf.q`, `hpf.passes`, `hpf.env` and its four stages. */
        val hpf: PassFilterSlots = PassFilterSlots("hpf")

        /** The bandpass stage: `bpf.freq`, `bpf.q`, `bpf.env` and its four stages. */
        val bpf: BandFilterSlots = BandFilterSlots("bpf")

        /** The notch stage: `notch.freq`, `notch.q`, `notch.env` and its four stages. */
        val notch: BandFilterSlots = BandFilterSlots("notch")

        /** The lowpass stage: `lpf.freq`, `lpf.q`, `lpf.passes`, `lpf.env` and its four stages. */
        val lpf: PassFilterSlots = PassFilterSlots("lpf")

        /** The tremolo stage: `tremolo.depth`, `tremolo.rate`, `tremolo.shape`. */
        val tremolo: TremoloSlots = TremoloSlots()

        /** The amplitude envelope: `adsr.attack`, `adsr.decay`, `adsr.sustain`, `adsr.release`, `adsr.on`. */
        val adsr: AdsrSlots = AdsrSlots()

        /** The amplitude envelope's curves: `adsrCurves.attack`, `adsrCurves.decay`, `adsrCurves.release`. */
        val adsrCurves: AdsrCurvesSlots = AdsrCurvesSlots()

        /** The highpass envelope's curves: `hpfCurves.attack`, `hpfCurves.decay`, `hpfCurves.release`. */
        val hpfCurves: ModEnvelopeCurvesSlots = ModEnvelopeCurvesSlots("hpfCurves")

        /** The bandpass envelope's curves: `bpfCurves.attack`, `bpfCurves.decay`, `bpfCurves.release`. */
        val bpfCurves: ModEnvelopeCurvesSlots = ModEnvelopeCurvesSlots("bpfCurves")

        /** The notch envelope's curves: `notchCurves.attack`, `notchCurves.decay`, `notchCurves.release`. */
        val notchCurves: ModEnvelopeCurvesSlots = ModEnvelopeCurvesSlots("notchCurves")

        /** The lowpass envelope's curves: `lpfCurves.attack`, `lpfCurves.decay`, `lpfCurves.release`. */
        val lpfCurves: ModEnvelopeCurvesSlots = ModEnvelopeCurvesSlots("lpfCurves")

        /** The sample instrument's playback: `begin`, `end`, `speed`, `loop` (flat names, one knob per door). */
        val sample: SampleSlots = SampleSlots()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Oscillator Primitives
    //
    // freq default: Freq = use the voice's note frequency (e.g. 440 Hz for A4).
    // Constant(n) = fixed frequency in Hz (e.g. Constant(5.0) for a 5 Hz LFO).
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Sine wave oscillator, optionally carrying banks of sine PARTIALS at multiples of its own
     * frequency (KLANG-00185 (sine partial banks plan)). The sine itself is partial 1 at gain
     * [fundamental]; [harmonics] adds partials at `2f, 3f, ...`, [octaves] at `2f, 4f, 8f, ...`,
     * [suboctaves] at `f/2, f/4, ...`. An added partial at `m * f` or `f / m` has gain
     * `m ^ -rolloff` of its bank (the distance from the fundamental is `m` either way). Banks
     * sum without deduplication. With the literal defaults (fundamental 1, every count 0) the
     * engine builds the plain sine, bit-identical to before the banks existed; anything else, a
     * `Param` included, builds the partial bank. Every knob is a signal read once per block (an
     * explicit partial's signal gain per sample). Partials whose frequency's magnitude is at or above
     * Nyquist are silent (decided 2026-09-07; by magnitude since 2026-10-10). [analogSpread] blends the
     * drift lanes: 0 = one shared walk for the whole bank, 1 = one walk per partial.
     *
     * [partials] is the fourth bank (KLANG-00296 (sine inharmonic partials), Q26): explicit
     * [Partial]s at any ratio of this sine's frequency, each with its own gain and start phase, played in the
     * order written and summed raw with the fundamental and the other banks. The engine plays the first
     * [SINE_MAX_PARTIALS] of them.
     */
    @WireName("sine")
    data class Sine(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /** Gain of the sine's own partial; 0 leaves only the banks. */
        val fundamental: IgnitorDsl = Constant(1.0),
        /** Number of partials added at `2f, 3f, 4f, ...`; 0 = none. */
        val harmonics: IgnitorDsl = Constant(0.0),
        /** Gain law exponent of the [harmonics] bank: partial at `m` gets `m ^ -rolloff`. */
        val harmonicsRolloff: IgnitorDsl = Constant(1.0),
        /** Number of partials added at `2f, 4f, 8f, ...`; 0 = none. */
        val octaves: IgnitorDsl = Constant(0.0),
        /** Gain law exponent of the [octaves] bank. */
        val octavesRolloff: IgnitorDsl = Constant(1.0),
        /** Number of partials added at `f/2, f/4, f/8, ...`; 0 = none. */
        val suboctaves: IgnitorDsl = Constant(0.0),
        /** Gain law exponent of the [suboctaves] bank: partial at `f/m` gets `m ^ -rolloff`. */
        val suboctavesRolloff: IgnitorDsl = Constant(1.0),
        /** Drift lane blend, 0 = one shared analog walk for every partial, 1 = independent walks. */
        val analogSpread: IgnitorDsl = Constant(1.0),
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
        /** Explicit partials at any ratio, in the order written; empty = none. See [Partial]. */
        val partials: List<Partial> = emptyList(),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); fundamental.collectParams(out)
            harmonics.collectParams(out); harmonicsRolloff.collectParams(out)
            octaves.collectParams(out); octavesRolloff.collectParams(out)
            suboctaves.collectParams(out); suboctavesRolloff.collectParams(out)
            analogSpread.collectParams(out); phase.collectParams(out)

            for (p in partials) {
                p.collectParams(out)
            }
        }

        /** True when every bank knob is its literal default and no partial is listed: the engine builds the plain sine. */
        fun isPlainSine(): Boolean =
            fundamental == Constant(1.0) && harmonics == Constant(0.0) &&
                octaves == Constant(0.0) && suboctaves == Constant(0.0) && partials.isEmpty()

        /**
         * One explicit partial of the sine: a sine at [ratio] times the sine's own frequency, scaled by [gain],
         * starting [phase] into its cycle. Not a node: it lives only in [Sine.partials].
         *
         * - [ratio]: any number, no integer rule. A ratio of 1 is the sine's own frequency again, summed with the
         *   [Sine.fundamental] (a pure cluster writes `fundamental(0)`). The partial at `ratio * f` follows the
         *   door's `freq` and every pitch modulation over the sine, like the other banks.
         * - [gain]: linear, raw (no normalisation); a negative gain is the same partial at `phase + 0.5`.
         * - [phase]: a fraction of one cycle, the oscillators' unit, wrapped (1.25 is 0.25); 0 starts on the upward
         *   zero crossing.
         *
         * Every knob is a number or a signal. The ratio and the phase are read once per block like the other bank
         * knobs: a moving ratio steps at block boundaries, a moving phase glides to its new value across the block. A
         * gain that is a signal (an envelope, a tremolo) is read per sample; a number or a slot once per block. A
         * partial whose frequency's magnitude is at or above Nyquist, or not finite, is silent for that block.
         */
        data class Partial(
            val ratio: IgnitorDsl,
            val gain: IgnitorDsl = Constant(1.0),
            val phase: IgnitorDsl = Constant(0.0),
        ) {
            fun collectParams(out: MutableList<Param>) {
                ratio.collectParams(out); gain.collectParams(out); phase.collectParams(out)
            }
        }
    }

    /** Sawtooth wave oscillator, a rising ramp (`Ignitor.saw`). */
    @WireName("saw")
    data class Saw(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /** Analog flyback time in samples: lower = brighter/sharper reset, higher = softer (default [SAW_RESET_SAMPLES]). */
        val resetSamples: Double = SAW_RESET_SAMPLES,
        /** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps very high notes sane. */
        val shapeMax: Double = SAW_SHAPE_MAX,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /**
     * Square wave oscillator (fixed 50% duty). NOTE: the `square` / `sqr` / `pulse` *sound names* are
     * registered to [Pulze] (one pulse oscillator with a `duty` Ignitor slot); this type is retained as a
     * plain 50%-duty pulse used internally (presets, generic test fixtures).
     */
    @WireName("square")
    data class Square(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /** Triangle wave oscillator (`Ignitor.tri`). */
    @WireName("tri")
    data class Tri(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        // No shape knobs: the triangle is the pulse engine with both flanks fully open (rise = fall = 1.0),
        // which makes the min-flank floor (PULSE_MIN_FLANK_SAMPLES) always overridden — there is nothing
        // tunable here (see Ignitors.tri / WaveVoiceState.setPulseShape). Stays a plain oscillator.
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /**
     * White noise generator. Produces uniformly distributed random samples.
     *
     * [color] is an optional spectral tilt: `0` = flat white (default, filter bypassed); `<0` darkens
     * toward pink/brown (one-pole LP); `>0` brightens toward blue/violet (complementary HP). Range −1..1.
     *
     * [uid] is an instance-discriminator used by the runtime identity cache: each call to
     * `Ignitor.whitenoise()` creates a fresh [WhiteNoise] with a unique uid, so two such calls
     * yield two independent noise streams when composed (`a + b`). Binding to a variable and
     * re-using it (`let s = Ignitor.whitenoise(); s + s`) keeps a single instance and sums the
     * same stream twice.
     */
    @WireName("white-noise")
    data class WhiteNoise(
        val color: IgnitorDsl = Slots.color,
        val uid: Int = nextNoiseUid(),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            color.collectParams(out)
        }
    }

    /**
     * Zawtooth wave oscillator: a naive sawtooth with an instant reset, no flyback flank (brighter and harsher
     * than the saw).
     */
    @WireName("zawtooth")
    data class Zawtooth(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /** Zamp wave oscillator ("zamp"). Naive reverse sawtooth without anti-aliasing — the raw [Ramp]. */
    @WireName("zamp")
    data class Zamp(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /** Impulse oscillator. Emits a single-sample spike per cycle. */
    @WireName("impulse")
    data class Impulse(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /** Pulse wave oscillator with variable duty cycle. */
    @WireName("pulze")
    data class Pulze(
        val freq: IgnitorDsl = Freq,
        val duty: IgnitorDsl = Slots.duty,
        val analog: IgnitorDsl = Slots.analog,
        /** Minimum flank length in samples (a floor on every edge → softens with pitch). Default 2.0. */
        val flankSamples: Double = PULSE_MIN_FLANK_SAMPLES,
        /** Rising-edge flank fraction of the plateau (0 = sharpest/min floor, 1 = full ramp). Default 0.0. */
        val riseFlank: Double = PULSE_RISE_FLANK,
        /** Falling-edge flank fraction of the plateau (0 = sharpest/min floor, 1 = full ramp). Default 0.0. */
        val fallFlank: Double = PULSE_FALL_FLANK,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); duty.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /**
     * Raw pulse oscillator — naive (no anti-aliasing), the raw counterpart of the rounded pulse
     * (sound names `square`/`pulse`, which use [Pulze]). This type backs the `pulze` sound name.
     */
    @WireName("raw-pulze")
    data class RawPulze(
        val freq: IgnitorDsl = Freq,
        val duty: IgnitorDsl = Slots.duty,
        val analog: IgnitorDsl = Slots.analog,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); duty.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /**
     * Brown (Brownian/red) noise generator. Random-walk filtered noise with -6 dB/oct slope.
     *
     * [leak] is the per-sample white-leak coefficient `k` in `out = (out + k·white)/(1+k)`: lower =
     * deeper / slower-walking brown (default 0.02); higher = brighter (more white mixed in each sample).
     *
     * See [WhiteNoise] for the [uid] instance-discriminator story.
     */
    @WireName("brown-noise")
    data class BrownNoise(
        val leak: IgnitorDsl = Slots.leak,
        val uid: Int = nextNoiseUid(),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            leak.collectParams(out)
        }
    }

    /**
     * Pink noise generator. Equal energy per octave with -3 dB/oct slope.
     * See [WhiteNoise] for the [uid] instance-discriminator story.
     */
    @WireName("pink-noise")
    data class PinkNoise(val uid: Int = nextNoiseUid()) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /** Perlin noise generator. Smooth, continuous random signal useful for organic modulation. */
    @WireName("perlin-noise")
    data class PerlinNoise(
        val rate: IgnitorDsl = Slots.rate,
        /** fBm octaves: 1 = plain single-octave (default); higher = more fractal detail (engine caps at 8). */
        val octaves: IgnitorDsl = Slots.octaves,
        /** fBm amplitude falloff per octave (0..1, default 0.5; lower = quieter upper octaves). */
        val persistence: IgnitorDsl = Slots.persistence,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            rate.collectParams(out); octaves.collectParams(out); persistence.collectParams(out)
        }
    }

    /** Berlin noise generator. Bipolar variant of Perlin noise (output ranges -1..+1). */
    @WireName("berlin-noise")
    data class BerlinNoise(
        val rate: IgnitorDsl = Slots.rate,
        /** fBm octaves: 1 = plain single-octave (default); higher = more fractal detail (engine caps at 8). */
        val octaves: IgnitorDsl = Slots.octaves,
        /** fBm amplitude falloff per octave (0..1, default 0.5; lower = quieter upper octaves). */
        val persistence: IgnitorDsl = Slots.persistence,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            rate.collectParams(out); octaves.collectParams(out); persistence.collectParams(out)
        }
    }

    /** Dust noise generator. Emits sparse random impulses at a controllable density. */
    @WireName("dust")
    data class Dust(
        val density: IgnitorDsl = Slots.density,
        /** Heavy-tailed amplitude exponent: 1 = uniform (default); >1 = mostly-tiny, rare-loud (vinyl pops). */
        val tail: IgnitorDsl = Slots.tail,
        /** Bipolar pops (random ±sign) when > 0.5; default 0 = unipolar (today's behavior). */
        val bipolar: IgnitorDsl = Slots.bipolar,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            density.collectParams(out); tail.collectParams(out); bipolar.collectParams(out)
        }
    }

    /**
     * Crackle noise generator — a chaotic recurrence (SuperCollider's Crackle map), DC-blocked to
     * bipolar pops. [chaos] (≈1.0 sparse … 2.0 dense/noisy) drives the map. Unlike [Dust] it uses no
     * PRNG. (For the old sparse-impulse behavior crackle used to alias, use [Dust] directly.)
     */
    @WireName("crackle")
    data class Crackle(
        val chaos: IgnitorDsl = Slots.chaos,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            chaos.collectParams(out)
        }
    }

    /** Ramp oscillator. Reverse sawtooth, FALLING: the negated [Saw] (`Ignitors.ramp`
     *  builds it at `polarity = -1.0`). */
    @WireName("ramp")
    data class Ramp(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /** Analog flyback time in samples: lower = brighter/sharper reset, higher = softer (default [RAMP_RESET_SAMPLES]). */
        val resetSamples: Double = RAMP_RESET_SAMPLES,
        /** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps high notes sane. */
        val shapeMax: Double = RAMP_SHAPE_MAX,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); phase.collectParams(out)
        }
    }

    /**
     * Unison supersaw oscillator. Stacks multiple detuned sawtooth voices for a thick sound.
     *
     * The character fields below are plain scalars (read once per voice-count/freq change, not audio-rate).
     * Their defaults MUST stay in sync with `SUPERSAW_*` in `audio_be/.../ignitor/OscillatorTuning.kt` — they
     * are the same values, duplicated here because `audio_be` consts aren't visible to `audio_bridge`.
     */
    @WireName("supersaw")
    data class SuperSaw(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Slots.voices,
        /** Unison frequency spread between the voices (the pattern-level `.spread()` sets this). */
        val spread: IgnitorDsl = Slots.spread,
        val analog: IgnitorDsl = Slots.analog,
        /** Drift lane blend, 0 = one shared analog walk for every voice, 1 = independent walks. */
        val analogSpread: IgnitorDsl = Constant(1.0),
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = SUPERSAW_SPREAD_POWER,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = SUPERSAW_SIDE_ATTEN,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = SUPERSAW_GAIN_JITTER,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = SUPERSAW_CENTER_JITTER_SCALE,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = SUPERSAW_PHASE_POOL,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64). */
        val drawTries: Double = SUPERSAW_DRAW_TRIES,
        /** Accepted fundamental-coherence band K, lower edge (0 = cancelled, 1 = phase-aligned). */
        val kMin: Double = SUPERSAW_K_MIN,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = SUPERSAW_K_MAX,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = SUPERSAW_POOL_SIZE,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = SUPERSAW_REFRESH_EVERY,
        /**
         * Pool entry selection, `"name[:width[:outliers]]"` (value-colon form). `"normal"`
         * (default): normal-distribution serving over the vocabulary's rank order, median-
         * centered — width sets the spread (`0` = always the median take, `0.1` tight,
         * `0.5` default, `1`+ near-uniform, e.g. `"normal:1.5"` ≈ random with a slight
         * center edge); outliers (0..1, default 0) is the probability of serving an
         * EXTREME take instead — the vocabulary's lowest- or highest-K entry, coin-flip
         * side (with a reachable band those sit directly at kMin/kMax):
         * `"normal:0.1:0.05"` = tight typical takes, 5% wild plucks. `"random"`: uniform
         * vocabulary pick (band-accepted takes, not un-pooled legacy randomness).
         * `"roundrobin"` (opt-in): cycle the vocabulary — can gargle audibly.
         * Unrecognized names coerce to the default.
         */
        val selection: String = SUPERSAW_SELECTION,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = SUPERSAW_WARMUP,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out); phase.collectParams(out)
        }
    }

    /** Unison supersine oscillator. Stacks multiple detuned sine voices. */
    @WireName("supersine")
    data class SuperSine(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Slots.voices,
        /** Unison frequency spread between the voices (the pattern-level `.spread()` sets this). */
        val spread: IgnitorDsl = Slots.spread,
        val analog: IgnitorDsl = Slots.analog,
        /** Drift lane blend, 0 = one shared analog walk for every voice, 1 = independent walks. */
        val analogSpread: IgnitorDsl = Constant(1.0),
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = SUPERSINE_SPREAD_POWER,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = SUPERSINE_SIDE_ATTEN,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = SUPERSINE_GAIN_JITTER,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = SUPERSINE_CENTER_JITTER_SCALE,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = SUPERSINE_PHASE_POOL,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64).
         *  Deeper than the saw's 5: the high band is rare per draw, and a missed band degrades to
         *  closest-candidate (= coherence maximization). */
        val drawTries: Double = SUPERSINE_DRAW_TRIES,
        /** Band lower edge — the supersine's K IS the note (no other harmonics), so it sits high. */
        val kMin: Double = SUPERSINE_K_MIN,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = SUPERSINE_K_MAX,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = SUPERSINE_POOL_SIZE,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = SUPERSINE_REFRESH_EVERY,
        /**
         * Pool entry selection, `"name[:width[:outliers]]"` (value-colon form). `"normal"`
         * (default): normal-distribution serving over the vocabulary's rank order, median-
         * centered — width sets the spread (`0` = always the median take, `0.1` tight,
         * `0.5` default, `1`+ near-uniform, e.g. `"normal:1.5"` ≈ random with a slight
         * center edge); outliers (0..1, default 0) is the probability of serving an
         * EXTREME take instead — the vocabulary's lowest- or highest-K entry, coin-flip
         * side (with a reachable band those sit directly at kMin/kMax):
         * `"normal:0.1:0.05"` = tight typical takes, 5% wild plucks. `"random"`: uniform
         * vocabulary pick (band-accepted takes, not un-pooled legacy randomness).
         * `"roundrobin"` (opt-in): cycle the vocabulary — can gargle audibly.
         * Unrecognized names coerce to the default.
         */
        val selection: String = SUPERSINE_SELECTION,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = SUPERSINE_WARMUP,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out); phase.collectParams(out)
        }
    }

    /** Unison supersquare oscillator. Stacks multiple detuned square voices. */
    @WireName("supersquare")
    data class SuperSquare(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Slots.voices,
        /** Unison frequency spread between the voices (the pattern-level `.spread()` sets this). */
        val spread: IgnitorDsl = Slots.spread,
        val analog: IgnitorDsl = Slots.analog,
        /** Drift lane blend, 0 = one shared analog walk for every voice, 1 = independent walks. */
        val analogSpread: IgnitorDsl = Constant(1.0),
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = SUPERSQUARE_SPREAD_POWER,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = SUPERSQUARE_SIDE_ATTEN,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = SUPERSQUARE_GAIN_JITTER,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = SUPERSQUARE_CENTER_JITTER_SCALE,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = SUPERSQUARE_PHASE_POOL,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64). */
        val drawTries: Double = SUPERSQUARE_DRAW_TRIES,
        /** Accepted fundamental-coherence band K, lower edge (0 = cancelled, 1 = phase-aligned). */
        val kMin: Double = SUPERSQUARE_K_MIN,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = SUPERSQUARE_K_MAX,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = SUPERSQUARE_POOL_SIZE,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = SUPERSQUARE_REFRESH_EVERY,
        /**
         * Pool entry selection, `"name[:width[:outliers]]"` (value-colon form). `"normal"`
         * (default): normal-distribution serving over the vocabulary's rank order, median-
         * centered — width sets the spread (`0` = always the median take, `0.1` tight,
         * `0.5` default, `1`+ near-uniform, e.g. `"normal:1.5"` ≈ random with a slight
         * center edge); outliers (0..1, default 0) is the probability of serving an
         * EXTREME take instead — the vocabulary's lowest- or highest-K entry, coin-flip
         * side (with a reachable band those sit directly at kMin/kMax):
         * `"normal:0.1:0.05"` = tight typical takes, 5% wild plucks. `"random"`: uniform
         * vocabulary pick (band-accepted takes, not un-pooled legacy randomness).
         * `"roundrobin"` (opt-in): cycle the vocabulary — can gargle audibly.
         * Unrecognized names coerce to the default.
         */
        val selection: String = SUPERSQUARE_SELECTION,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = SUPERSQUARE_WARMUP,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out); phase.collectParams(out)
        }
    }

    /** Unison supertriangle oscillator. Stacks multiple detuned triangle voices. */
    @WireName("supertri")
    data class SuperTri(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Slots.voices,
        /** Unison frequency spread between the voices (the pattern-level `.spread()` sets this). */
        val spread: IgnitorDsl = Slots.spread,
        val analog: IgnitorDsl = Slots.analog,
        /** Drift lane blend, 0 = one shared analog walk for every voice, 1 = independent walks. */
        val analogSpread: IgnitorDsl = Constant(1.0),
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = SUPERTRI_SPREAD_POWER,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = SUPERTRI_SIDE_ATTEN,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = SUPERTRI_GAIN_JITTER,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = SUPERTRI_CENTER_JITTER_SCALE,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = SUPERTRI_PHASE_POOL,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64).
         *  Deeper than the saw's 5 — the higher band is rarer per draw. */
        val drawTries: Double = SUPERTRI_DRAW_TRIES,
        /** Band lower edge — 1/k² harmonics put most of the note in the fundamental, so it sits high-ish. */
        val kMin: Double = SUPERTRI_K_MIN,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = SUPERTRI_K_MAX,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = SUPERTRI_POOL_SIZE,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = SUPERTRI_REFRESH_EVERY,
        /**
         * Pool entry selection, `"name[:width[:outliers]]"` (value-colon form). `"normal"`
         * (default): normal-distribution serving over the vocabulary's rank order, median-
         * centered — width sets the spread (`0` = always the median take, `0.1` tight,
         * `0.5` default, `1`+ near-uniform, e.g. `"normal:1.5"` ≈ random with a slight
         * center edge); outliers (0..1, default 0) is the probability of serving an
         * EXTREME take instead — the vocabulary's lowest- or highest-K entry, coin-flip
         * side (with a reachable band those sit directly at kMin/kMax):
         * `"normal:0.1:0.05"` = tight typical takes, 5% wild plucks. `"random"`: uniform
         * vocabulary pick (band-accepted takes, not un-pooled legacy randomness).
         * `"roundrobin"` (opt-in): cycle the vocabulary — can gargle audibly.
         * Unrecognized names coerce to the default.
         */
        val selection: String = SUPERTRI_SELECTION,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = SUPERTRI_WARMUP,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out); phase.collectParams(out)
        }
    }

    /** Unison superramp oscillator. Stacks multiple detuned ramp voices. */
    @WireName("superramp")
    data class SuperRamp(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Slots.voices,
        /** Unison frequency spread between the voices (the pattern-level `.spread()` sets this). */
        val spread: IgnitorDsl = Slots.spread,
        val analog: IgnitorDsl = Slots.analog,
        /** Drift lane blend, 0 = one shared analog walk for every voice, 1 = independent walks. */
        val analogSpread: IgnitorDsl = Constant(1.0),
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = SUPERRAMP_SPREAD_POWER,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = SUPERRAMP_SIDE_ATTEN,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = SUPERRAMP_GAIN_JITTER,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = SUPERRAMP_CENTER_JITTER_SCALE,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = SUPERRAMP_PHASE_POOL,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64). */
        val drawTries: Double = SUPERRAMP_DRAW_TRIES,
        /** Accepted fundamental-coherence band K, lower edge (0 = cancelled, 1 = phase-aligned). */
        val kMin: Double = SUPERRAMP_K_MIN,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = SUPERRAMP_K_MAX,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = SUPERRAMP_POOL_SIZE,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = SUPERRAMP_REFRESH_EVERY,
        /**
         * Pool entry selection, `"name[:width[:outliers]]"` (value-colon form). `"normal"`
         * (default): normal-distribution serving over the vocabulary's rank order, median-
         * centered — width sets the spread (`0` = always the median take, `0.1` tight,
         * `0.5` default, `1`+ near-uniform, e.g. `"normal:1.5"` ≈ random with a slight
         * center edge); outliers (0..1, default 0) is the probability of serving an
         * EXTREME take instead — the vocabulary's lowest- or highest-K entry, coin-flip
         * side (with a reachable band those sit directly at kMin/kMax):
         * `"normal:0.1:0.05"` = tight typical takes, 5% wild plucks. `"random"`: uniform
         * vocabulary pick (band-accepted takes, not un-pooled legacy randomness).
         * `"roundrobin"` (opt-in): cycle the vocabulary — can gargle audibly.
         * Unrecognized names coerce to the default.
         */
        val selection: String = SUPERRAMP_SELECTION,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = SUPERRAMP_WARMUP,
        /**
         * Phase offset as a fraction of one cycle, added to the running phase every sample. 0 (default) starts
         * where the oscillator always started (its builder's KDoc names the point); it wraps, no clamp (1.25 is
         * 0.25, -0.25 is 0.75). A constant shifts the start; a moving signal is phase modulation.
         */
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out); phase.collectParams(out)
        }
    }

    /** Silent exciter. Outputs a zero-filled buffer. */
    @WireName("silence")
    data object Silence : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /**
     * The voice's resolved SAMPLE, as a source: the PCM that `sound("bd")` names, played with the voice's
     * pitch, `speed`, `begin`/`end` and loop (phase 3 step 7).
     *
     * The engine resolves and plays the sample itself (`VoiceFactory` builds the playhead from the voice's
     * playback fields) and hands it to the build, so this leaf carries no knobs. Its one user is the engine's
     * sample instrument (`IgnitorRegistry.SAMPLE_INSTRUMENT`, the built-in shape over this source), which every
     * sample voice runs. It has no authoring door on either side; built anywhere else it renders silence.
     */
    @WireName("sample")
    data object Sample : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Physical Models
    // ═════════════════════════════════════════════════════════════════════════════

    /** Karplus-Strong plucked string physical model. */
    @WireName("pluck")
    data class Pluck(
        val freq: IgnitorDsl = Freq,
        /** The loop feedback per pass, 0.9 to 0.999 (default 0.996): higher rings longer. Not a time. */
        val feedback: IgnitorDsl = Slots.feedback,
        val brightness: IgnitorDsl = Slots.brightness,
        val pickPosition: IgnitorDsl = Slots.pickPosition,
        val stiffness: IgnitorDsl = Slots.stiffness,
        val analog: IgnitorDsl = Slots.analog,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); feedback.collectParams(out); brightness.collectParams(out); pickPosition.collectParams(out)
            stiffness.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison superpluck. Stacks multiple detuned Karplus-Strong voices for a chorus-like string sound. */
    @WireName("superpluck")
    data class SuperPluck(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Slots.voices,
        val spread: IgnitorDsl = Slots.spread,
        /** The loop feedback per pass, as [Pluck.feedback]. */
        val feedback: IgnitorDsl = Slots.feedback,
        val brightness: IgnitorDsl = Slots.brightness,
        val pickPosition: IgnitorDsl = Slots.pickPosition,
        val stiffness: IgnitorDsl = Slots.stiffness,
        val analog: IgnitorDsl = Slots.analog,
        /** Drift lane blend, 0 = one shared analog walk for every voice, 1 = independent walks. */
        val analogSpread: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); feedback.collectParams(out); brightness.collectParams(
                out
            )
            pickPosition.collectParams(out); stiffness.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Dispatch / Selection
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Disables the graph optimizer for the WHOLE registered definition this appears in, not just
     * the subtree below it. Exists so a fusion can be ruled in or out BY EAR: wrap any sound,
     * set `on = 0`, and the tree is rendered exactly as authored.
     *
     * [on] is a plain Int, structural on purpose: the optimizer runs once at registration, long
     * before any note, so there is nothing to read a Param from. Coerced (`on != 0`), never
     * required, per the house no-throw-on-user-input rule.
     *
     * Dissolves in the `buildIgnitor` prologue like [Variants], so it costs nothing at render.
     */
    @WireName("optimizerHint")
    data class OptimizerHint(val inner: IgnitorDsl, val on: Int = 1) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Selects one of several child ignitors based on the voice's `soundIndex`.
     *
     * Lets a single sound expose multiple variants, addressable per note via the
     * `name:n` mini-notation or `.n(...)` pattern — the same mechanism that picks
     * variants in a sample bank (`bd:0`, `bd:1`, ...).
     *
     * Index wraps with floor-mod semantics: with N children, index `k` selects
     * `children[k.mod(N)]`, so negative indices wrap from the end and overflow
     * wraps to zero. Missing `:n` defaults to index 0 at the registry boundary. An empty
     * [children] list plays silence (the engine coerces it, it never throws).
     *
     * Nested variants all dispatch on the same `soundIndex` — this is intentional,
     * so a single switching axis can drive correlated changes deep in the tree.
     */
    @WireName("variants")
    data class Variants(val children: List<IgnitorDsl>) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            children.forEach { it.collectParams(out) }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic Composition
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Branches side by side, SUMMED: the twin of a series of stages (`serial`), built by the `parallel` door as
     * `x.parallel(a, b)` = `a(x) + b(x)` with every branch reading the SAME `x` (one instance, built once; a pitch node
     * in a branch forks it, as it forks any shared node).
     *
     * **Matched in phase.** A branch that delays the signal (an oversampled `distort` or `shape`, 3 to 5 samples)
     * would comb against the others in the sum, so the build gives every branch an unshaped round trip of each
     * oversampler it lacks (a phase twin), and all branches then share the same delay at every frequency. A branch
     * that mixes two oversamplers in a plain [Plus] falls back to a whole-sample delay, which matches the bass and
     * the mids only. A plain [Plus] is raw arithmetic and does not align (KLANG-00216 (signal graph engine)
     * §6.9).
     *
     * The doors build it for two branches or more (none is the signal unchanged, one is that branch).
     */
    @WireName("parallel")
    data class Parallel(val branches: List<IgnitorDsl>) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            branches.forEach { it.collectParams(out) }
        }
    }

    /** Additive combinator. Sums two ignitor signals sample-by-sample. */
    @WireName("plus")
    data class Plus(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Multiplicative combinator. Multiplies two ignitor signals sample-by-sample (ring modulation). */
    @WireName("times")
    data class Times(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /**
     * `mul · (x + pre) + add` in one pass: the optimizer's node for block-constant arithmetic (no
     * door builds it; the fold rules of `optimize()` will, from step 2 of the arithmetic folds).
     * One pre-add, one multiply, one add, in that order, so that BOTH authored orders fold bit for
     * bit: `x.mul(a).add(b)` is `Affine(x, -0.0, a, b)` and `x.add(b).mul(a)` is
     * `Affine(x, b, a, -0.0)`. Folding the second as `a · x + a · b` instead would be off by
     * cancellation wherever `x ≈ -b`, every zero crossing of an offset-then-scale shape, which no
     * relative margin survives.
     *
     * An ABSENT pre-add or add is `Constant(-0.0)`, the defaults: `v + (-0.0)` is `v` bit for bit
     * for every `v` (both zeros, NaN, the infinities), where `v + 0.0` turns `-0.0` into `+0.0`.
     * An authored `add(0.0)` therefore stays `Constant(0.0)` and keeps its effect. [mul] has no
     * default: a chain without a multiply stays `Plus` nodes, because the multiply's clamp is
     * what `Plus` refuses.
     *
     * The sample is sanitised the way the `Plus`/`Times`/`Plus` chain it replaces was: the
     * multiply clamps once (`safeOut`, the `Times` contract), the adds do not (the `Plus` contract):
     * `safeOut(mul · (x + pre)) + add`. A run of several multiplies composes to rounding only while
     * no intermediate reaches the clamp at `SAFE_MAX`, which a rule has to guarantee before it
     * composes them (the condition is on `x · product`, not on the product).
     *
     * [pre], [mul] and [add] are block-constant trees (a [Constant], a [Param], a [Freq]-derived
     * expression), read once per block; a modulated coefficient still renders the chain's values,
     * per sample through the node's own scratch path, slower than the chain and never what the
     * optimizer builds.
     */
    @WireName("affine")
    data class Affine(
        val inner: IgnitorDsl,
        val pre: IgnitorDsl = Constant(-0.0),
        val mul: IgnitorDsl,
        val add: IgnitorDsl = Constant(-0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); pre.collectParams(out); mul.collectParams(out); add.collectParams(out)
        }

        companion object {
            /**
             * Is this the ABSENT pre-add or add, `Constant(-0.0)`?
             *
             * The one home of that test, because two modules decide things by it and they must not
             * drift: the optimizer WRITES the encoding (its `ABSENT`, its multiply-run rule) and
             * the voice build READS it, to tell an `Affine` folded out of a bare `.mul(k)` from
             * one that also carries an add. Structural, and bitwise: `+0.0` is a different node
             * with a different meaning, because `v + 0.0` turns a `-0.0` sample into `+0.0`.
             */
            fun isAbsentAddend(node: IgnitorDsl): Boolean =
                node is Constant && node.value == 0.0 && 1.0 / node.value < 0.0
        }
    }

    /**
     * Divides the left signal by the right signal (per-sample division). A divisor of exactly
     * zero yields zero: a block-constant zero is a dead branch, nothing upstream renders.
     */
    @WireName("div")
    data class Div(
        val left: IgnitorDsl,
        val right: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Subtractive combinator. Subtracts the right signal from the left sample-by-sample. */
    @WireName("minus")
    data class Minus(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Negates the inner signal (flips polarity): a multiply by `-1`, clamped like [Times]. */
    @WireName("neg")
    data class Neg(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Absolute value of the inner signal (full-wave rectification). */
    @WireName("abs")
    data class Abs(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Raises [base] to the power of [exp] sample-by-sample.
     *
     * Uses signed-magnitude semantics: for negative [base], computes
     * `-(|base|^exp)`. Preserves sign and avoids `NaN` for any real
     * exponent — keeps the engine numerically stable at audio rate.
     */
    @WireName("pow")
    data class Pow(val base: IgnitorDsl, val exp: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            base.collectParams(out); exp.collectParams(out)
        }
    }

    /** Per-sample minimum of two signals. */
    @WireName("min")
    data class Min(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Per-sample maximum of two signals. */
    @WireName("max")
    data class Max(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Bounds the inner signal to `[lo, hi]` per sample. */
    @WireName("clamp")
    data class Clamp(
        val inner: IgnitorDsl,
        val lo: IgnitorDsl = Constant(-1.0),
        val hi: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); lo.collectParams(out); hi.collectParams(out)
        }
    }

    /** `e^x` per sample. */
    @WireName("exp")
    data class Exp(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Natural logarithm per sample.
     *
     * Signed-magnitude: for negative inputs computes `-ln(|x|)`.
     * `log(0)` is treated as `0` to avoid `-Inf` poisoning the audio path.
     */
    @WireName("log")
    data class Log(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Square root per sample.
     *
     * Signed-magnitude: for negative inputs computes `-√|x|` to keep the engine `NaN`-free.
     */
    @WireName("sqrt")
    data class Sqrt(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Sign of the inner signal (`-1`, `0`, or `+1`). */
    @WireName("sign")
    data class Sign(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Hyperbolic tangent per sample (smooth saturation curve). */
    @WireName("tanh")
    data class Tanh(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Linear interpolation per sample: `left·(1−t) + right·t`.
     *
     * Crossfades between [left] and [right] under per-sample weight [t] (typically `[0, 1]`).
     */
    @WireName("lerp")
    data class Lerp(
        val left: IgnitorDsl,
        val right: IgnitorDsl,
        val t: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out); t.collectParams(out)
        }
    }

    /**
     * Maps the inner signal from `[-1, 1]` to `[from, to]` per sample.
     *
     * Standard LFO scaler. Output = `from + (inner + 1)·0.5·(to − from)`.
     */
    @WireName("range")
    data class Range(
        val inner: IgnitorDsl,
        val from: IgnitorDsl,
        val to: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); from.collectParams(out); to.collectParams(out)
        }
    }

    /** Per-sample floor: largest integer `≤ x`. */
    @WireName("floor")
    data class Floor(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample ceiling: smallest integer `≥ x`. */
    @WireName("ceil")
    data class Ceil(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample round to nearest integer (banker's rounding ties to even). */
    @WireName("round")
    data class Round(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample fractional part: `x − floor(x)`. */
    @WireName("frac")
    data class Frac(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Per-sample modulo: `left mod right`.
     *
     * Zero divisors are substituted with a tiny epsilon (1e-30) so the engine
     * never produces `NaN`. The master limiter handles the resulting spike.
     */
    @WireName("mod")
    data class Mod(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /**
     * Per-sample reciprocal: `1 / x`.
     *
     * Zero inputs are substituted with a tiny epsilon (1e-30) so the engine
     * never produces `NaN`. The master limiter handles the resulting spike.
     */
    @WireName("recip")
    data class Recip(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample square: `x · x`. */
    @WireName("sq")
    data class Sq(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Per-sample conditional: returns [whenTrue] when [cond] `> 0`, else [whenFalse].
     *
     * Both branches are evaluated at audio rate (no short-circuit), so the gate is
     * deterministic and stateful sources advance regardless of which branch is selected.
     */
    @WireName("select")
    data class Select(
        val cond: IgnitorDsl,
        val whenTrue: IgnitorDsl,
        val whenFalse: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            cond.collectParams(out); whenTrue.collectParams(out); whenFalse.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Frequency Modifiers
    // ═════════════════════════════════════════════════════════════════════════════

    /** Shifts the pitch of the inner exciter by a number of semitones. */
    @WireName("detune")
    data class Detune(
        val inner: IgnitorDsl,
        val semitones: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); semitones.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * SVF lowpass filter. Attenuates frequencies above the cutoff; [passes] cascades the stage.
     *
     * **The cutoff envelope ([env], [attack], [decay], [sustain], [release]) and
     * the per-voice [humanize] lane are documented once, here**, and the other three filter nodes
     * point at this block rather than repeating it.
     */
    @WireName("lowpass")
    data class Lowpass(
        val inner: IgnitorDsl,
        val freq: IgnitorDsl = Constant(2000.0),
        val q: IgnitorDsl = Constant(FILTER_Q),
        /**
         * How analog the filter is, one unitless character scale (`/dsl-design` section 4): `0` is the
         * clean linear filter (the default, bit-identical to no saturation), 1 to 8 is usual, 10 strong.
         * Its tells per unit: the OB-X-style state-dependent damping in the SVF resonance feedback,
         * compressing the resonance peak (`FILTER_DRIVE_PER_ANALOG`), and with [humanize] the per-voice
         * cutoff tolerance and the cutoff wander, about two cents per unit (`FilterHumanizationDefaults.kt`).
         */
        val analog: IgnitorDsl = Constant(0.0),
        /**
         * Cascade count (C5): run the stage [passes] times, 2 = 24 dB/oct. Per-stage q is
         * staggered (Butterworth ladder scaled by `q/0.707`) so the cascade stays -3 dB at
         * [freq]; a resonant q's peak compounds across stages.
         *
         * A KNOB since phase 3 step 5 (2026-09-25; it was a structural `Int`), so that
         * `classic()` can fill it from the `lpf.passes` / `hpf.passes` slot. **Read ONCE, at voice
         * build**, the leaf-only way of the 3b `oversample` knob: a `Param` or `Constant` leaf gives
         * its value, anything else has no build-time answer and is one pass (and is not built, so
         * no rng draw moves). The value goes through `coercePasses`, the one coercion, which ROUNDS
         * (as the strip always has for sprudel's `lpf(passes = ...)`), bounds it to
         * 1..[FILTER_MAX_PASSES] and reads a non-finite value as one pass. The graph optimizer
         * fuses only a `Constant` count; a slot could change it per note. Default: one pass.
         */
        val passes: IgnitorDsl = Constant(FILTER_PASSES),
        /**
         * Cutoff-envelope DEPTH in semitones, and the node's envelope SWITCH: at exactly `0` no
         * envelope is built and the filter is bit-identical to one without these knobs.
         * `cutoff = freq * 2^(env/12 * envelopeValue)`, so `env = 12` doubles the cutoff at full
         * envelope and a negative value sweeps down, with no dead zone. Same word, meaning and
         * scale as sprudel's `lpf(env = ...)`.
         *
         * **The node's field default is `0`, the DOOR's is not.** A door call that names any of
         * the five envelope knobs fills the companions it left out, this one included, from
         * `constants/FilterEnvelopeDefaults.kt` (see `fillFilterEnvelope`), so
         * `lowpass(800, x => x.adsr(0.01, 0.3, 0.2, 0.1))` is the audible pluck that
         * `lpf(800, decay = 0.3, sustain = 0.2)` is, not a silent no-op. Only a call that names
         * NOTHING leaves the envelope off. A hand-built node is a value, not a call, so it gets
         * the plain `0`.
         *
         * The four stage knobs below are read ONCE per voice, at build, from a [Param] or
         * [Constant] leaf (which is what a slot is). That matched the retired strip, whose
         * `FilterDef.envelope` was resolved at note-on too. **A non-leaf EXPRESSION here is not
         * "modulated", it is UNREADABLE at build**, and the consequence differs per knob: a stage
         * knob falls back to its constant, but this DEPTH falls back to `0`, which switches the
         * whole envelope OFF with no warning. `lowpass(800, x => x.env(Ignitor.param("e", 24).max(36)))`
         * renders a static filter. Write a slot (`Ignitor.slot.lpf.env`) or a constant.
         *
         * **Which envelope this is (decision D3).** The law is the engine's one envelope law, and an
         * unshaped stage (curve knob at its default) takes `MOD_ENV_CURVE`, the house Exponential
         * curve (K = 3), as the retired voice strip's filter envelope did when sprudel's `lpfCurves`
         * named none. The
         * sampling was the same on both: the envelope is computed at block START and block END and
         * the SVF coefficients are interpolated across the block. So the same stage times, depth and
         * curve give the same sweep on both surfaces.
         */
        val env: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope attack in seconds. Inert while [env] is `0`. */
        val attack: IgnitorDsl = Constant(FILTER_ENV_ATTACK_SEC),
        /** Cutoff-envelope decay in seconds. Inert while [env] is `0`. */
        val decay: IgnitorDsl = Constant(FILTER_ENV_DECAY_SEC),
        /** Cutoff-envelope sustain share of [env], 0 to 1. Inert while [env] is `0`. */
        val sustain: IgnitorDsl = Constant(FILTER_ENV_SUSTAIN_LEVEL),
        /** Cutoff-envelope release in seconds. Inert while [env] is `0`. */
        val release: IgnitorDsl = Constant(FILTER_ENV_RELEASE_SEC),
        /**
         * Curve of the cutoff envelope's attack, the same six shapes as the chain's `adsr`, as an
         * INDEX into [AdsrCurves] (phase 3 step 3c: a knob, so a slot can carry it). Read ONCE at
         * build from a [Param] or [Constant] leaf; a non-leaf, a non-finite value and a bad index
         * all read as the default, `MOD_ENV_CURVE`, exponential (decision D3; the envelope was
         * linear before). Inert while [env] is `0`. The shapes and their composition are the chain
         * `adsr`'s (`adsrCurveShape` and `EnvelopeCore` in `audio_be`).
         */
        val attackCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's decay; see [attackCurve]. */
        val decayCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's release; see [attackCurve]. */
        val releaseCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /**
         * Per-voice analog humanization: the FIXED cutoff tolerance this voice's copy of the
         * filter gets, plus the slow drift lane that wanders it while the note sounds. Both are
         * scaled by [analog] and both are per-voice RANDOM DRAWS, which is why this is a
         * structural flag and not a knob: no number a pattern writes can carry a draw.
         *
         * `false` (the default) draws nothing and changes nothing. `true` draws only when
         * [analog] resolves above 0, and then exactly as the voice strip did: one
         * `nextDouble()` for the tolerance, then the drift lane's three
         * (`buildFilterHumanization` in `audio_be` owns the order). [analog] is read at build from
         * a [Param] or [Constant] LEAF, like the envelope knobs: a non-leaf expression there is
         * unreadable at build, so the lane silently does not exist and this flag does nothing. The
         * strip had no such case to match, its `analog` being one number off the voice's bag.
         *
         * It is what the built-in instruments of phase 3 switch on so `analog(3)` keeps meaning
         * what it means today. Scales come from `constants/FilterHumanizationDefaults.kt`.
         */
        val humanize: Boolean = false,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); freq.collectParams(out); q.collectParams(out); analog.collectParams(out)
            passes.collectParams(out)
            env.collectParams(out); attack.collectParams(out); decay.collectParams(out)
            sustain.collectParams(out); release.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
        }
    }

    /** SVF highpass filter. Attenuates frequencies below the cutoff; [passes] cascades the stage. */
    @WireName("highpass")
    data class Highpass(
        val inner: IgnitorDsl,
        val freq: IgnitorDsl = Constant(200.0),
        val q: IgnitorDsl = Constant(FILTER_Q),
        /** See [Lowpass.analog]: the same character scale and tells for the HP tap. */
        val analog: IgnitorDsl = Constant(0.0),
        /** Cascade count, a knob read once at voice build; see [Lowpass.passes]. */
        val passes: IgnitorDsl = Constant(FILTER_PASSES),
        /** Cutoff-envelope depth in semitones, and the node's envelope switch; see [Lowpass.env]. */
        val env: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope attack in seconds; see [Lowpass.env]. */
        val attack: IgnitorDsl = Constant(FILTER_ENV_ATTACK_SEC),
        /** Cutoff-envelope decay in seconds; see [Lowpass.env]. */
        val decay: IgnitorDsl = Constant(FILTER_ENV_DECAY_SEC),
        /** Cutoff-envelope sustain share of [env]; see [Lowpass.env]. */
        val sustain: IgnitorDsl = Constant(FILTER_ENV_SUSTAIN_LEVEL),
        /** Cutoff-envelope release in seconds; see [Lowpass.env]. */
        val release: IgnitorDsl = Constant(FILTER_ENV_RELEASE_SEC),
        /** Curve of the cutoff envelope's attack; see [Lowpass.attackCurve]. */
        val attackCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's decay; see [Lowpass.attackCurve]. */
        val decayCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's release; see [Lowpass.attackCurve]. */
        val releaseCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Per-voice cutoff tolerance and drift lane; see [Lowpass.humanize]. */
        val humanize: Boolean = false,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); freq.collectParams(out); q.collectParams(out); analog.collectParams(out)
            passes.collectParams(out)
            env.collectParams(out); attack.collectParams(out); decay.collectParams(out)
            sustain.collectParams(out); release.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
        }
    }

    /** Simple one-pole lowpass filter. Lightweight with -6 dB/oct rolloff and no resonance. */
    @WireName("one-pole-lowpass")
    data class OnePoleLowpass(
        val inner: IgnitorDsl,
        val freq: IgnitorDsl = Constant(2000.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); freq.collectParams(out)
        }
    }

    /** SVF bandpass filter. Passes frequencies near the cutoff, attenuates others. */
    @WireName("bandpass")
    data class Bandpass(
        val inner: IgnitorDsl,
        val freq: IgnitorDsl = Constant(1000.0),
        val q: IgnitorDsl = Constant(FILTER_Q),
        /**
         * The analog SATURATION is not implemented for this tap and the value does not reach it
         * (as the retired voice strip's bandpass had none); see [Lowpass.analog] for the semantics when
         * it is. The value is NOT inert, though: it scales [humanize]'s per-voice cutoff tolerance
         * and its drift lane, exactly as it did on the strip, so on a bandpass the character amount's
         * only tells today are the humanization ones; the saturation joins them when the tap gets it.
         */
        val analog: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope depth in semitones, and the node's envelope switch; see [Lowpass.env]. */
        val env: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope attack in seconds; see [Lowpass.env]. */
        val attack: IgnitorDsl = Constant(FILTER_ENV_ATTACK_SEC),
        /** Cutoff-envelope decay in seconds; see [Lowpass.env]. */
        val decay: IgnitorDsl = Constant(FILTER_ENV_DECAY_SEC),
        /** Cutoff-envelope sustain share of [env]; see [Lowpass.env]. */
        val sustain: IgnitorDsl = Constant(FILTER_ENV_SUSTAIN_LEVEL),
        /** Cutoff-envelope release in seconds; see [Lowpass.env]. */
        val release: IgnitorDsl = Constant(FILTER_ENV_RELEASE_SEC),
        /** Curve of the cutoff envelope's attack; see [Lowpass.attackCurve]. */
        val attackCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's decay; see [Lowpass.attackCurve]. */
        val decayCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's release; see [Lowpass.attackCurve]. */
        val releaseCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /**
         * Per-voice cutoff tolerance and drift lane; see [Lowpass.humanize]. The TOLERANCE and
         * the DRIFT reach this tap even though [analog]'s saturation does not, as they reached the
         * retired strip's bandpass.
         */
        val humanize: Boolean = false,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); freq.collectParams(out); q.collectParams(out); analog.collectParams(out)
            env.collectParams(out); attack.collectParams(out); decay.collectParams(out)
            sustain.collectParams(out); release.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
        }
    }

    /** SVF notch (band-reject) filter. Removes frequencies near the cutoff, passes others. */
    @WireName("notch")
    data class Notch(
        val inner: IgnitorDsl,
        val freq: IgnitorDsl = Constant(1000.0),
        val q: IgnitorDsl = Constant(FILTER_Q),
        /** The saturation is not implemented for this tap; it still scales [humanize]. See [Bandpass.analog]. */
        val analog: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope depth in semitones, and the node's envelope switch; see [Lowpass.env]. */
        val env: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope attack in seconds; see [Lowpass.env]. */
        val attack: IgnitorDsl = Constant(FILTER_ENV_ATTACK_SEC),
        /** Cutoff-envelope decay in seconds; see [Lowpass.env]. */
        val decay: IgnitorDsl = Constant(FILTER_ENV_DECAY_SEC),
        /** Cutoff-envelope sustain share of [env]; see [Lowpass.env]. */
        val sustain: IgnitorDsl = Constant(FILTER_ENV_SUSTAIN_LEVEL),
        /** Cutoff-envelope release in seconds; see [Lowpass.env]. */
        val release: IgnitorDsl = Constant(FILTER_ENV_RELEASE_SEC),
        /** Curve of the cutoff envelope's attack; see [Lowpass.attackCurve]. */
        val attackCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's decay; see [Lowpass.attackCurve]. */
        val decayCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's release; see [Lowpass.attackCurve]. */
        val releaseCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Per-voice cutoff tolerance and drift lane; see [Bandpass.humanize]. */
        val humanize: Boolean = false,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); freq.collectParams(out); q.collectParams(out); analog.collectParams(out)
            env.collectParams(out); attack.collectParams(out); decay.collectParams(out)
            sustain.collectParams(out); release.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Equalizer (unified-eq: one fused EqCore pass instead of chained filter nodes)
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * One section of an [Eq]. A sealed hierarchy, NOT an enum — the house wire rule (types
     * over enums): each variant carries exactly its own params, `@WireName`-keyed variants
     * are order-independent on the wire (no enum-ordinal append-only hazard), and every
     * consumer dispatches through an exhaustive `when` (the runtime maps each variant to
     * `EqCore`'s Int section types in ONE such `when` — a new variant without a mapping
     * fails compilation). Every param is a full [IgnitorDsl], resolved per block by the
     * runtime adapter (`EqIgnitor`) — which is the only layer where `Freq`-backed params
     * (the tracking highpass) can exist; the planned Master/Katalyst surfaces pass scalars.
     */
    @WireFormat
    sealed interface EqSection {

        /**
         * Per-variant param collection — declared HERE so a future field on any variant must
         * be wired next to its declaration (an external `when` over variants matches old
         * arms silently when a variant merely gains a field; this way the variant's own
         * override is the single place to forget, right beside the field).
         */
        fun collectParams(out: MutableList<Param>)

        /**
         * SVF low-pass section — the linear path of [IgnitorDsl.Lowpass], as an Eq section.
         * Defaults MATCH the chained node (parameter-parity rule: same param, same surface,
         * same meaning — and the same omitted-field sound).
         */
        @WireName("eqLowpass")
        data class Lowpass(
            val freq: IgnitorDsl = Constant(2000.0),
            val q: IgnitorDsl = Constant(FILTER_Q),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freq.collectParams(out); q.collectParams(out)
            }
        }

        /**
         * SVF high-pass section — the linear path of [IgnitorDsl.Highpass], as an Eq
         * section. Defaults match the chained node.
         */
        @WireName("eqHighpass")
        data class Highpass(
            val freq: IgnitorDsl = Constant(200.0),
            val q: IgnitorDsl = Constant(FILTER_Q),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freq.collectParams(out); q.collectParams(out)
            }
        }

        /** SVF band-pass section — [IgnitorDsl.Bandpass] as an Eq section; same defaults. */
        @WireName("eqBandpass")
        data class Bandpass(
            val freq: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(FILTER_Q),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freq.collectParams(out); q.collectParams(out)
            }
        }

        /** SVF notch section — [IgnitorDsl.Notch] as an Eq section; same defaults. */
        @WireName("eqNotch")
        data class Notch(
            val freq: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(FILTER_Q),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freq.collectParams(out); q.collectParams(out)
            }
        }

        /**
         * Simper peaking bell: [db] decibels of gain at [freq], [q] the PRE-GAIN bandwidth
         * (see `computeSvfBellCoeffs` for math + limits). 0 dB is bit-transparent; db is
         * COEFFICIENT-bearing (an LFO on it zippers like an LFO on cutoff, per-block snap).
         *
         * The [q] default is 0.707 and MUST stay equal to the `band()` default on both DSL
         * doors (parameter-parity: one bell, one omitted-field sound). Since C1 every
         * filter shares the 0.707 default — here it is a bell width, and musical bells sit
         * wide (the tap-to-bell conversions of the voicings in the real song land at
         * 0.33 to 0.70).
         *
         * ⚠ That conversion (`A² = 1 + g` since the C2 unity-peak taps, `q_bell = Q/A`) is exact for ONE tap in
         * isolation ONLY. N parallel taps are NOT N serial bells: bells multiply, taps sum,
         * and the cross term is what it leaves behind. Migrating a parallel boost
         * bank to bells is a NEW mix, not a conversion — use [RawTap]. See [IgnitorDsl.Eq].
         * (Worked example: two boosts of gain 1.7 @ 850 Hz/Q 0.707 and 5.0 @ 2500 Hz/Q 0.7 —
         * the guitar's default voicing — overshot by +4.5 dB at 1200 Hz (measured pre-C2; re-measure under unity-peak taps) when run as serial
         * bells instead of parallel taps. Wider/hotter voicings overshoot more; the term
         * scales with `g₁·g₂`, so re-measure per patch rather than reusing this figure.)
         */
        @WireName("eqBell")
        data class Bell(
            val freq: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(FILTER_Q),
            val db: IgnitorDsl = Constant(EQ_BAND_DB),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freq.collectParams(out); q.collectParams(out); db.collectParams(out)
            }
        }

        /**
         * Parallel boost tap: a bandpass of the Eq INPUT (never the running chain) added at
         * this list position, scaled by [gain] — the fused form of
         * `signal.add(signal.bandpass(freq, q).mul(gain))` WHEN [gain] is
         * Constant/Param-backed: the adapter snaps it once per block, while the legacy Times
         * node multiplies per SAMPLE — an expression-backed gain (an LFO) must never fuse
         * (a 128-frame gain staircase instead of a smooth tremolo; the optimizer's R2
         * precondition, same class as [Bell]'s coefficient-bearing `db`).
         */
        @WireName("eqRawTap")
        data class RawTap(
            val freq: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(FILTER_Q),
            val gain: IgnitorDsl = Constant(EQ_TAP_GAIN),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freq.collectParams(out); q.collectParams(out); gain.collectParams(out)
            }
        }
    }

    /**
     * Fused equalizer over [inner]: an ordered [sections] list rendered in ONE `EqCore` pass
     * instead of a chain of per-filter nodes. Authored via the `.eq()` surface, and ALSO
     * produced automatically by the graph optimizer (`IgnitorDsl.optimize`), which folds runs
     * of ADJACENT chained filters into sections bit-identically at registration time. The
     * chained syntax stays THE syntax for cutoff filters; nothing is ever reordered, so a
     * nonlinear node or a gain multiply between two filters keeps them apart.
     *
     * ## Two section families, two topologies
     *
     * Sections are visited in list order, and each one rewrites the running buffer in place,
     * so a section normally sees what the previous section produced. That is the SERIAL
     * family: [EqSection.Lowpass], [EqSection.Highpass], [EqSection.Bandpass],
     * [EqSection.Notch] and [EqSection.Bell] (`.band()`).
     *
     * [EqSection.RawTap] (`.tap()`) is the exception and the reason this node can replace a
     * hand-built parallel boost chain: a tap reads the **Eq INPUT** (a per-block snapshot
     * taken before any section runs) rather than the running buffer, and ADDS its band onto
     * the chain at its list position instead of replacing it.
     *
     * ```
     * input ──┬─────────────► [bell] ──► [notch] ──► [lowpass] ──► out
     *         │                 ▲
     *         └── bandpass ─────┘  (a tap: reads input, adds in at its position)
     * ```
     *
     * The practical consequence, and it is audible: N bells MULTIPLY
     * (`(1 + m1₁H₁)(1 + m1₂H₂)`), while N taps SUM (`1 + g₁H₁ + g₂H₂`). Converting a
     * parallel tap bank into serial bells leaves the cross term `g₁g₂H₁H₂` behind. On Der
     * Schmetterling's guitar (850 Hz and 2500 Hz boosts) that term measured **+4.5 dB around
     * 1200 Hz** (pre-C2; the exact figure needs a re-measure under unity-peak taps), and more on wider or hotter voicings. Per-BAND the two forms convert
     * exactly (`A² = 1 + g` since the C2 unity-peak taps, `q_bell = Q/A`); per-CHAIN they do not. Use [EqSection.RawTap]
     * to reproduce a parallel bank, and [EqSection.Bell] for ordinary cascading EQ bands.
     *
     * ⚠ The consequence when the two are MIXED: a bell earlier in the list cannot shape a
     * later tap, because the tap always reads the pre-section input. `.band(3000.0, db = -12.0)`
     * followed by `.tap(3000.0, 1.0, 5.0)` re-injects the very 3 kHz the band just removed,
     * taken from the uncut source. Put taps first (as the diagram shows) unless that
     * re-injection is what you want.
     *
     * Cost note: the input snapshot is taken ONLY when the list contains a tap, so a
     * bell-only Eq copies nothing.
     */
    @WireName("eq")
    data class Eq(
        val inner: IgnitorDsl,
        val sections: List<EqSection> = emptyList(),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)

            for (s in sections) {
                s.collectParams(out)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Envelope
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * ADSR amplitude envelope. Shapes the inner signal's volume over the note lifecycle.
     *
     * Every exponential stage bends at `ADSR_EXP_K` (3.0): the per-envelope `expK` knob was REMOVED
     * in phase 3 step 3c (maintainer, 2026-09-25); a per-curve bend is its own later design.
     */
    @WireName("adsr")
    data class Adsr(
        val inner: IgnitorDsl,
        val attack: IgnitorDsl = Constant(0.01),
        val decay: IgnitorDsl = Constant(0.1),
        val sustain: IgnitorDsl = Constant(ADSR_SUSTAIN_LEVEL),
        val release: IgnitorDsl = Constant(0.3),
        /**
         * Curve of the attack, as an INDEX into [AdsrCurves] (phase 3 step 3c: a knob, so a slot can
         * carry it). Read ONCE at voice build from a [Param] or [Constant] leaf; a non-leaf, a
         * non-finite value and a bad index all read as the default, [AdsrCurve.Default]
         * (exponential), which is the house default of every amplitude envelope stage.
         */
        val attackCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(AdsrCurve.Default)),
        /** Curve of the decay; see [attackCurve]. */
        val decayCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(AdsrCurve.Default)),
        /** Curve of the release; see [attackCurve]. */
        val releaseCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(AdsrCurve.Default)),
        /**
         * De-click smoothing on the final gain, in seconds (`Slots.declick`, default `0` = off:
         * this per-ignitor envelope is intentionally not de-clicked). `>0` runs a one-pole low-pass on
         * the gain that rounds the C1 corners at segment joins (attack to decay peak, gate-off, cutoff),
         * killing the low-note "plop" the same way the amp VCA does. `ignitorParam`-addressable / patternable.
         */
        val declick: IgnitorDsl = Slots.declick,
        /**
         * The envelope's ON/OFF switch, read ONCE at voice build from a [Param] or [Constant] leaf.
         * OFF is exactly `0.0`; anything else is ON, and so is UNSET (a non-finite value) and a
         * non-leaf: the house flag rule (a non-zero number is on), and the envelope is built by
         * default (`audio/ref/off-values.md`, the envelope row).
         *
         * A NODE FIELD ONLY, deliberately: no Ignitor door writes it. `classic()` fills it from
         * sprudel's `adsrOn`/`adsrOff`; on the Ignitor doors, not writing `adsr()` already means no
         * envelope (a recorded two-door asymmetry, maintainer, 2026-09-25).
         *
         * What OFF does today: the stage is not built, so the inner signal passes unchanged, and its
         * knob subtrees are not built either (a drawing source there takes no draws, the gate's
         * usual consequence). The voice's LIFETIME is kept: the node still reports the release tail
         * the envelope would have had, as the voice strip's `adsrOff` kept it. The switched-off node
         * hands on its inner's `BuiltIgnitor.endsInEnvelope` (the one home of the rule): when its inner is an
         * authored static-release envelope with nothing built over it (no stage of the instrument's own after
         * it, no written `classic()` stage), that envelope ends the voice; otherwise the voice ends on the teardown fade
         * (`TeardownFadeRenderer`, since phase 3 step 6).
         */
        val on: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); attack.collectParams(out); decay.collectParams(out)
            sustain.collectParams(out); release.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
            declick.collectParams(out); on.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM Synthesis
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Frequency modulation synthesis. The modulator's output shifts the carrier's frequency
     * at audio rate, with an optional ADSR envelope controlling modulation depth over time. The
     * envelope has no curve knob yet; its stages run `MOD_ENV_CURVE`, exponential (decision D3). With every envelope
     * stage at its default (attack 0, decay 0, sustain 1, release 0) no envelope runs: the depth is full from the
     * onset through the release tail. Sprudel's `fm` is this node too: it fills the FM stage `classic()` places, through
     * the `fm.*` slots (pitch pipeline step 4; the voice strip's `FmRenderer` retired with it).
     *
     * **A pitch node means what it wraps** (decision D1, pitch pipeline step 3b; the placement rule, maintainer,
     * 2026-10-09). A pitch modulation (a `vibrato`, `pitchMod`, `pitchEnvelope`, `accelerate`, an outer `fm`, a sprudel
     * pitch door through `classic()`):
     *  - above the fm moves the whole operator, the note's pitch: carrier and modulator together, the ratio exact
     *    (`Ign.sine().fm(Ign.sine(), 3.5, 400).vibrato(6, 0.5)`); a modulator with an absolute `freq`
     *    (`Ign.sine(330)`) stays at its frequency, as every absolute oscillator does;
     *  - on the modulator moves the modulator alone (`Ign.sine().fm(Ign.sine().vibrato(6, 0.5), 3.5, 400)`);
     *  - on the carrier moves the carrier alone, the modulator does not follow
     *    (`Ign.sine().vibrato(6, 0.5).fm(Ign.sine(), 3.5, 400)`).
     *
     * **One fm serves one carrier pitch.** When the fm's carrier holds two pitches (`(x + x.detune(7)).fm(m, ...)`),
     * one modulator serves both, renders once per pitch per block and advances its state each time. Give each layer
     * its own fm, inside its detune, and sum them: `x.fm(m1, ...) + x.fm(m2, ...).detune(7)`.
     */
    @WireName("fm")
    data class Fm(
        val carrier: IgnitorDsl,
        val modulator: IgnitorDsl,
        val ratio: IgnitorDsl = Constant(FM_RATIO),
        val depth: IgnitorDsl = Constant(0.0),
        val attack: IgnitorDsl = Constant(FM_ENV_ATTACK_SEC),
        val decay: IgnitorDsl = Constant(FM_ENV_DECAY_SEC),
        val sustain: IgnitorDsl = Constant(FM_ENV_SUSTAIN_LEVEL),
        val release: IgnitorDsl = Constant(FM_ENV_RELEASE_SEC),
        /** The frequency the FM machinery runs on: the modulator is driven at `freq x ratio`
         *  and the index is `depth / freq`. Defaults to [Freq] (the note), which makes FM
         *  transpose under `detune` like any note-pitched oscillator; authored absolute
         *  (`Constant(...)`) the patch is immune, like `Ignitor.sine(5)` — the same
         *  musical/absolute separation every oscillator has (D13's Fm special case retired).
         *  DELIBERATELY not exposed on the `fm(...)` builder or the script door (maintainer
         *  decision 2026-08-30): a hidden internal of the pitch machinery, raw-door-only —
         *  the default IS the semantics; absolute authoring stays a power-user construction. */
        val freq: IgnitorDsl = Freq,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            carrier.collectParams(out); modulator.collectParams(out); ratio.collectParams(out); depth.collectParams(out)
            freq.collectParams(out)
            attack.collectParams(out); decay.collectParams(out); sustain.collectParams(out); release.collectParams(
                out
            )
            freq.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Pre-amplification stage. Boosts signal level before the waveshaper ([Shape]): gain without
     * a curve, every colour belongs to [Shape].
     */
    @WireName("drive")
    data class Drive(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.5),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /**
     * Pure waveshaping without drive. Applies a nonlinear transfer function per sample, then a DC
     * blocker (on every shape, not only the asymmetric ones: see `Ignitor.distort` in the backend).
     *
     * Shapes (the catalogue is [DistortionShapes]; the knob carries a name's INDEX in it):
     *  - **Symmetric soft:** "soft" (tanh), "gentle" (soft clip, 2× gain), "softsat"
     *    (algebraic, gentler), "cubic", "exp" (transistor), "sineshaper" (peak-at-unity fold).
     *  - **Symmetric hard / harsh:** "hard" (clip), "zerosquare" (→ square), "chebyshev"
     *    (3rd-harmonic), "fold" (sin wavefold), "linearfold" (triangle wavefold).
     *  - **Asymmetric (even harmonics, DC):** "diode", "tube" (shifted-tanh), "asym" (poly),
     *    "stompbox" (diode pedal), "rectify" (full-wave).
     *
     * @param shape the waveshaper as an INDEX into [DistortionShapes.names] (phase 3 step 3b,
     *   2026-09-25; it was a name). A knob so that `classic()` can fill it from a slot. Both doors
     *   take a NAME and convert it through [DistortionShapes.indexOf]; the script door also takes a
     *   number or a slot. **Read ONCE, at voice build** (the shaper is chosen once per note, as it
     *   always was): a `Param` or `Constant` leaf gives its value, anything else has no build-time
     *   answer and is `soft`, and is not built at all (no rng draw moves). The index rounds to the
     *   nearest position; a non-finite, negative or past-the-end one is `soft`, exactly what an
     *   unknown name has always been. Default: `soft` (index 0).
     * @param oversample the oversampling FACTOR (2 = 2x, 4 = 4x, 8 = 8x; floored to a power of two;
     *   1 or less is no oversampler, today's plain path). **Read ONCE, at voice build**, the same
     *   leaf-only way as [shape] (a non-leaf is 0, off), and truncated to a whole factor, as the
     *   pattern door's `asIntOrNull` does; a non-finite one is off. No upper clamp (the Motor stays
     *   raw). A STOPGAP (decision D7, KLANG-00237 (builtin instruments) section 3): a knob so that
     *   `classic()` can fill it from `distort.oversample`, and KLANG-00214 (oversampling regions)
     *   retires it for a region. Default: 0 (off).
     */
    @WireName("shape")
    data class Shape(
        val inner: IgnitorDsl,
        val shape: IgnitorDsl = Constant(DistortionShapes.SOFT_INDEX.toDouble()),
        val oversample: IgnitorDsl = Constant(SHAPE_OVERSAMPLE),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); shape.collectParams(out); oversample.collectParams(out)
        }
    }

    /**
     * Drive and shape as ONE unit, gated as a whole on [amount] (the `distort` row of the off-value
     * table, `audio/ref/off-values.md`). Neither authoring door builds it: both
     * spell `distort` as `Shape(Drive(...))`, whose `Shape` half has no amount and cannot be gated. It is
     * `classic()`'s distort stage (phase 3 step 5), the one node that switches a distort off completely,
     * which a slotted tail needs.
     *
     * **Its law is the VOICE STRIP's, not the doors'** (phase 3 step 4, decision D2 option A,
     * 2026-09-25): the backend renders it through the loop the strip's distort ran
     * (`DistortionCore`): `shape(x * 10^(amount * 1.2))` with the drive applied INSIDE the oversampler,
     * then a DC blocker, and NO soft cap, so a hot shape (`gentle` is doubled) can leave `[-1, 1]`.
     * It was proven bit-identical to the strip before the strip retired (phase 3 step 9). The `distort`/`shape` doors keep
     * their capped `Shape(Drive(...))` law (drive at the base rate, `softCap` last), unchanged, because
     * songs depend on it.
     *
     * [amount] is read once per block. A leaf amount at or below 0, or unset, is not built (the gate);
     * a MODULATED amount at or below 0 is not a bypass: the node keeps shaping at unity drive, so its
     * oversampler and DC blocker never go stale across a crossing (ledger W5's hazard).
     *
     * Its KDoc used to call it "kept for backward compatibility with serialized trees"; wire trees are
     * never persisted, and that was not the reason it exists.
     *
     * [shape] and [oversample] are the knobs of [Shape], read the same way, at voice build.
     */
    @WireName("distort")
    data class Distort(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.5),
        val shape: IgnitorDsl = Constant(DistortionShapes.SOFT_INDEX.toDouble()),
        val oversample: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out); shape.collectParams(out); oversample.collectParams(out)
        }
    }

    /**
     * Bit-crush effect. Reduces amplitude resolution to create quantization noise: the voice strip's
     * asymmetric `floor` quantizer, `floor(x * 2^bits / 2) / (2^bits / 2)` clamped to `[-1, 1]`,
     * with a DC offset of about `-0.5 / halfLevels` (-0.5 at 1 bit), which moves with modulated
     * [bits] (phase 3 step 4, decision D1, 2026-09-25: FLOOR everywhere; it rounded before). Below
     * 1 bit it passes through.
     */
    @WireName("crush")
    data class Crush(
        val inner: IgnitorDsl,
        /** The bit depth: `2^bits` levels. Below 1.0 the stage passes through. */
        val bits: IgnitorDsl = Constant(8.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); bits.collectParams(out)
        }
    }

    /** Sample-rate reduction effect. Holds samples to create aliasing artifacts. */
    @WireName("coarse")
    data class Coarse(
        val inner: IgnitorDsl,
        /** The sample-hold factor: each sample is held for [factor] samples. At 1.0 or less nothing is held. */
        val factor: IgnitorDsl = Constant(4.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); factor.collectParams(out)
        }
    }

    /**
     * Phaser effect. Sweeps a series of allpass filters to create notch comb filtering.
     *
     * Wet/dry follows THE shared wet/dry law (`WetDryMix`, correlated branch, p = 2):
     * `out = max(floor, cos²(wet·π/2)) · dry + sin²(wet·π/2) · phased`. The phased path is
     * the input through the allpass chain — fully correlated with the dry — so the crossfade
     * holds constant AMPLITUDE across the knob.
     *
     * @param wet Wet/dry balance in [0, 1]. 0.0 = bit-exact bypass, 1.0 = phased signal only,
     *   0.5 = equal mix (both coefficients 0.5). Default: 0.5. The FIRST parameter of the door on
     *   both surfaces: `.phaser(0.3, rate)`.
     * @param floor Minimum dry coefficient in [0, 1]. Default 0.0 (true crossfade). Raising
     *   it keeps at least that much dry at every [wet]; at 1.0 the phaser is purely additive,
     *   like the orbit-side phaser. `floor` is a knob on effect builders only, where it cannot
     *   meet the round-down `floor()` of a signal, because a builder offers only its own knobs
     *   (maintainer, 2026-09-24).
     */
    @WireName("phaser")
    data class Phaser(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(0.5),
        val wet: IgnitorDsl = Constant(0.5),
        val center: IgnitorDsl = Constant(PHASER_CENTER_HZ),
        val sweep: IgnitorDsl = Constant(PHASER_SWEEP_HZ),
        val floor: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); wet.collectParams(out)
            center.collectParams(out); sweep.collectParams(out); floor.collectParams(out)
        }
    }

    /**
     * Tremolo effect: a pulsing volume change. At voice build it becomes an oscillator of [shape] at [rate]
     * (no analog drift; the square, sawtooth and ramp get a 16 ms soft edge, `TREMOLO_EDGE_SECONDS`), mapped
     * to a gain (by default from `1 - depth` to 1, see [rangeFrom]) and multiplied into the signal.
     *
     * @param rate LFO rate in Hz, read once per block. A negative rate runs the oscillator backwards, and its
     *   soft edges then go instant (the edge length goes negative), so the square, sawtooth and ramp click, and
     *   the sawtooth and ramp also lose amplitude (a peak of about 0.85 at -5 Hz). Default 5.0.
     * @param depth modulation depth, 0 to 1. A leaf at or below 0 (or unset) builds no stage and runs no
     *   oscillator. A moving depth is read per sample and floored at 0: wherever it is at or below 0 the
     *   signal passes unchanged, as with a fixed depth at or below 0. Default 0.5.
     * @param shape the LFO waveform as an INDEX into [LfoShapes.names] (`sine`, `triangle`, `square`,
     *   `sawtooth`, `ramp`). Both doors take a NAME (the script door also a number or a slot). **Read
     *   ONCE, at voice build**, leaf-only (a non-leaf is `sine` and is not built); the index rounds to
     *   the nearest position, and a non-finite, negative or past-the-end one is `sine`, as an unknown
     *   name is. Default: `sine` (index 0). Above about 31.25 Hz the 16 ms edges fill the whole cycle, so
     *   `square`, `sawtooth` and `ramp` all become the same symmetric triangle (only the start differs).
     * @param rangeFrom with [rangeTo], where the LFO's swing sits, in the -1..1 language of the Ignitor `range`: the
     *   LFO's -1 maps to [rangeFrom], its +1 to [rangeTo], and the gain is `1 + depth * that`. The default
     *   `(-1, 0)` is the tremolo as it always was, the gain dipping from 1 to `1 - depth` (and the engine then builds
     *   exactly the classic computation, bit for bit). `(0, 1)` swells upward from 1 to `1 + depth`, `(-1, 1)`
     *   swings both ways, `(0, 2)` twice the depth upward. Raw: no clamp, and the depth floor still applies (a
     *   depth at or below 0 is no tremolo). Signals, read like [depth]. The script door's builder knob is
     *   `range(from, to)`.
     * @param rangeTo see [rangeFrom].
     */
    @WireName("tremolo")
    data class Tremolo(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(5.0),
        val depth: IgnitorDsl = Constant(0.5),
        val shape: IgnitorDsl = Constant(LfoShapes.SINE_INDEX.toDouble()),
        val rangeFrom: IgnitorDsl = Constant(TREMOLO_RANGE_FROM),
        val rangeTo: IgnitorDsl = Constant(TREMOLO_RANGE_TO),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
            shape.collectParams(out); rangeFrom.collectParams(out); rangeTo.collectParams(out)
        }
    }

    /**
     * Shimmer effect. Granular pitch-shift cloud with feedback — Aetherizer-style.
     *
     * Chops the input into short overlapping grains, replays them at the pitch intervals
     * specified by [pitches] (in semitones), and feeds the wet output back into the grain buffer
     * through a one-pole lowpass at [tone] Hz.
     *
     * Wet/dry follows THE shared wet/dry law (`WetDryMix`, decorrelated branch, p = 1):
     * `out = max(floor, cos(wet·π/2)) · dry + sin(wet·π/2) · cloud`. The grain cloud is
     * decorrelated from the dry, so this equal-POWER crossfade holds 0 dB across the knob.
     *
     * @param wet Wet/dry balance in [0, 1]. 0.0 = bit-exact bypass REGARDLESS of [feedback]
     *   (the grain state is cleared on bypass entry, so a modulated wet cannot resurrect a
     *   stale tail), 1.0 = cloud only. Default: 0.5. The FIRST parameter of the door on both
     *   surfaces: `.shimmer(0.3)`.
     * @param floor Minimum dry coefficient in [0, 1]. Default 0.0 (true crossfade); see
     *   [Phaser.floor] for the name.
     * @param feedback Wet → grain-buffer feedback. 0.0 = no cascade, 0.9 = long tails.
     *   Hard-clamped to 0.95 internally for stability.
     * @param pitches Semitone transpositions for grains. Default: `[0, 7, 12]` (root + fifth + octave).
     *   Example: `[0, 4, 7, 11]` for a major 7th chord shimmer. An empty list spawns no grains: only the dry
     *   plays, at its wet/dry level (silent at wet 1).
     * @param tone One-pole lowpass cutoff in Hz applied in the feedback path.
     *   Lower = darker, more ghostly tails. Typical: 2000–6000. Default: 4000.
     */
    @WireName("shimmer")
    data class Shimmer(
        val inner: IgnitorDsl,
        val wet: IgnitorDsl = Constant(SHIMMER_WET),
        val feedback: IgnitorDsl = Constant(SHIMMER_FEEDBACK),
        val pitches: List<Double> = SHIMMER_PITCHES,
        val tone: IgnitorDsl = Constant(SHIMMER_TONE_HZ),
        val floor: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); wet.collectParams(out); feedback.collectParams(out)
            tone.collectParams(out); floor.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pitch Modulation
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Vibrato effect. Modulates pitch with a sinusoidal LFO: the ratio `2^((sin * max(semitones, 0)) / 12)` per sample.
     *
     * A description: the runtime composes it (pitch pipeline 7b) as `pitchModSemitones(sine(rate, analog = 0) *
     * max(semitones, 0))`, the tremolo's pattern, so a signal depth is followed per sample. Its edge rules (a depth at or
     * below 0, non-finite values): the `vibrato` row of `audio/ref/off-values.md`.
     *
     * @param rate LFO frequency in Hz (default 5.0), read once per block
     * @param semitones modulation depth in SEMITONES (default 0.25 ≈ quarter-semitone wobble).
     *   Sprudel's `vib(rate, semitones, rangeFrom, rangeTo, phase)` fills `classic()`'s vibrato stage, this node,
     *   through the `vibrato.*` slots.
     * @param rangeFrom with [rangeTo], where the LFO's swing sits, in the -1..1 language of the Ignitor `range` (pitch
     *   pipeline 7c): the LFO's -1 maps to [rangeFrom], its +1 to [rangeTo], and the result is scaled by [semitones],
     *   so the ratio is `2^((range(sin, from, to) * max(semitones, 0)) / 12)`. The default `(-1, 1)` swings both ways,
     *   the vibrato as it always was (a literal `(-1, 1)` builds no range, so it renders those bits exactly); `(0, 1)`
     *   swings only upward from the note (a guitar's vibrato), `(-1, 0)` only downward, `(1, -1)` inverts the LFO.
     *   Raw: no clamp, and the depth floor still applies. Signals, read per sample. The script door's builder knob is
     *   `range(from, to)`.
     * @param rangeTo see [rangeFrom].
     * @param phase the LFO's phase as a fraction of one cycle, the oscillators' `phase` knob (7c, decision D9): 0 (the
     *   default) starts the sine at `sin(0)`, rising, which is the MIDDLE of the swing (the note only for a range centred
     *   on 0: `range(0, 1)` starts a quarter of the depth sharp and is on the note at 0.75, `range(-1, 0)` at 0.25), 0.25
     *   at its peak, 0.5 falling through the middle; wrapped (1.25 is 0.25), a non-finite value reads as 0. A constant
     *   is the start phase; a moving signal is phase modulation of the LFO.
     */
    @WireName("vibrato")
    data class Vibrato(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(VIBRATO_RATE_HZ),
        val semitones: IgnitorDsl = Constant(VIBRATO_SEMITONES),
        val rangeFrom: IgnitorDsl = Constant(VIBRATO_RANGE_FROM),
        val rangeTo: IgnitorDsl = Constant(VIBRATO_RANGE_TO),
        val phase: IgnitorDsl = Constant(VIBRATO_PHASE),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); semitones.collectParams(out)
            rangeFrom.collectParams(out); rangeTo.collectParams(out); phase.collectParams(out)
        }
    }

    /**
     * Pitch acceleration: an exponential glide by [semitones] from the onset to the gate close, then held through the
     * release: `accelerate(12)` arrives one octave up when the note ends and stays there (decision D2 of the pitch
     * pipeline, with its hold, 2026-10-09). Sprudel's `accelerate` fills `classic()`'s accelerate stage, this node,
     * through the flat `accelerate` slot.
     */
    @WireName("accelerate")
    data class Accelerate(
        val inner: IgnitorDsl,
        val semitones: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); semitones.collectParams(out)
        }
    }

    /**
     * Pitch envelope: an ADSR on the pitch, the same pattern as the chain's own `adsr`, for kick
     * drum sweeps, laser effects and other transient pitch gestures.
     *
     * The level rises from 0 to 1 over [attack], falls to [sustain] over [decay],
     * holds, and from the gate's end falls from wherever it is back to 0 over [release]. The
     * pitch is `2^(semitones * level / 12)`, so a level of 0 is the note itself. The release does
     * NOT extend the voice's life: a pitch release longer than the amp envelope's is cut off with
     * the voice, and release `0` returns to the note at the gate's end.
     *
     * The level is the engine's one envelope law (`EnvelopeCore` in `audio_be`, decision D3), the
     * chain `adsr`'s: fractional attack and decay frame counts (`seconds * sampleRate` as a Double).
     * Sprudel's `penv` IS this node: it fills the `penv.*` and `penvCurves.*` slots of the stage `classic()`
     * places (pitch pipeline step 1), whose defaults are this node's (`constants/PitchEnvelopeDefaults.kt`).
     *
     * @param semitones pitch shift at envelope peak, in SEMITONES (`2^(semitones·env/12)`):
     *   +12 sweeps from an octave up, -24 from two octaves down.
     * @param sustain the held level, a share of [semitones]; 0 (the default) returns to the
     *   note after the decay. Not clamped: the Motor stays raw. A non-finite one reads as unset (0),
     *   the chain `adsr`'s rule.
     * @param attackCurve curve of the attack, as an INDEX into [AdsrCurves], read once at build
     *   from a leaf (phase 3 step 3c). The default, and what a non-leaf, a non-finite value or a bad
     *   index read as, is `MOD_ENV_CURVE`, exponential (decision D3; the envelope was linear
     *   before). The shapes and their composition are the chain `adsr`'s (`adsrCurveShape` in
     *   `audio_be`).
     * @param decayCurve curve of the decay; see [attackCurve].
     * @param releaseCurve curve of the release; see [attackCurve].
     */
    @WireName("pitch-envelope")
    data class PitchEnvelope(
        val inner: IgnitorDsl,
        val semitones: IgnitorDsl = Constant(0.0),
        val attack: IgnitorDsl = Constant(PITCH_ENV_ATTACK_SEC),
        val decay: IgnitorDsl = Constant(PITCH_ENV_DECAY_SEC),
        val sustain: IgnitorDsl = Constant(PITCH_ENV_SUSTAIN_LEVEL),
        val release: IgnitorDsl = Constant(PITCH_ENV_RELEASE_SEC),
        val attackCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        val decayCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        val releaseCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); semitones.collectParams(out); attack.collectParams(out)
            decay.collectParams(out); sustain.collectParams(out); release.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
        }
    }

    /**
     * Pitch modulation by any signal, the LINEAR law: the frequency of every pitched source under [inner] is multiplied
     * by `1 + mod`, per sample. [mod] is a deviation, unitless: 0 is no change, 1.0 is an octave up, 0.5 a just fifth
     * (`x 1.5`), -0.5 an octave down, and -1.0 stops the oscillator (ratio 0). The law of FM: a modulator's swing
     * around the carrier. For a modulation in SEMITONES (the exponential law, `2^(mod / 12)`) see [PitchModSemitones].
     * No switch and no clamp: the ratio passes raw, a NaN sample of [mod] included
     * (KLANG-00309 (bugfix non finite pitch strip and signals) section 2).
     */
    @WireName("pitch-mod")
    data class PitchMod(
        val inner: IgnitorDsl,
        val mod: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); mod.collectParams(out)
        }
    }

    /**
     * Pitch modulation by any signal, in SEMITONES, the exponential law: the frequency of every pitched source under
     * [inner] is multiplied by `2^(mod / 12)`, per sample. 12 is an octave up, 7 a fifth, -12 an octave down, 0 the
     * note; `Ignitor.sine(5).mul(0.5)` as [mod] is a vibrato half a semitone deep. The pitch law of `vibrato`,
     * `accelerate` and `pitchEnvelope`, as a primitive (pitch pipeline 7a, decision D8, maintainer 2026-10-09). For
     * the LINEAR law (`1 + mod`, FM's) see [PitchMod].
     *
     * Gated at build: a literal [mod] (a `Constant` or `Param` leaf) of 0, or non-finite, builds no node, so the voice
     * is the bare [inner] (`audio/ref/off-values.md`, row `pitchModSemitones`, with what that means for a shared inner).
     * A signal [mod] is always built. The ratio passes through `safeOut`, no clamp below it: past about 598 semitones it
     * holds at `SAFE_MAX`. A non-finite SAMPLE of a signal: +Infinity reads as `SAFE_MAX`, NaN or -Infinity as the
     * ratio 0, which holds the source still, as `pitchMod`'s raw NaN and FM's modulator do
     * (KLANG-00309 (bugfix non finite pitch strip and signals) section 2).
     */
    @WireName("pitch-mod-semitones")
    data class PitchModSemitones(
        val inner: IgnitorDsl,
        val mod: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); mod.collectParams(out)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════════
// Builder Extensions
// ═════════════════════════════════════════════════════════════════════════════════

// Numbers into the typed Kotlin door

/**
 * This number as an Ignitor value: the way a plain number enters the typed Kotlin door, e.g.
 * `osc.vibrato(rate = 0.5.ign, semitones = 0.3.ign)`. The same node the script door builds from a bare `0.5`.
 *
 * Boxing a number into a node at build time is fine (maintainer, 2026-10-16): it happens once, when the tree is
 * written, never in the audio path.
 */
val Double.ign: IgnitorDsl.Constant get() = IgnitorDsl.Constant(this)

/** [Double.ign] for an [Int]: `5.ign` is `IgnitorDsl.Constant(5.0)`, the same tree as `5.0.ign`. */
val Int.ign: IgnitorDsl.Constant get() = IgnitorDsl.Constant(this.toDouble())

/**
 * The negated number, still a [IgnitorDsl.Constant]: `-1.ign` is `Constant(-1.0)`, the same tree as the script's
 * `-1`, not a negation node around a constant.
 *
 * Kotlin reads `-1.ign` as `-(1.ign)`, so this operator is what keeps the tree flat. Being declared on the precise
 * `Constant` type, it wins over any extension operator on `IgnitorDsl` in this package (for example one that
 * builds [IgnitorDsl.Neg]); keep the general operators as extensions (a member would win).
 */
operator fun IgnitorDsl.Constant.unaryMinus(): IgnitorDsl.Constant = IgnitorDsl.Constant(-value)

// Arithmetic
//
// ONE typed function per word, every operand an IgnitorDsl; the script's aliases (`add`, `sub`, `mul`, `mix`, `rem`,
// `power`, `negate`, `reciprocal`) are one-line typed delegates to their main word, so a script ports word for word: a number comes in through `.ign` (`saw * 0.5.ign`,
// `x.clamp(lo = (-0.5).ign, hi = 0.5.ign)`). The `Double` twins, operators included, went with the typed-doors
// rollout S4 (Q47); the script door keeps taking numbers (`x.mul(0.5)`), converts and calls these. The general
// operators stay EXTENSIONS: a member would beat `Constant.unaryMinus`, and `-1.ign` would become a `Neg` node.

/** Adds two ignitor signals together (sample-by-sample sum). */
operator fun IgnitorDsl.plus(other: IgnitorDsl) = IgnitorDsl.Plus(left = this, right = other)

/** Multiplies two ignitor signals together (ring modulation). */
operator fun IgnitorDsl.times(other: IgnitorDsl) = IgnitorDsl.Times(left = this, right = other)

/** Scales this signal by a modulatable [other] factor. Alias for [times], the script's `mul`. */
fun IgnitorDsl.mul(other: IgnitorDsl) = times(other)

/** Alias for [plus], the script's `add`. */
fun IgnitorDsl.add(other: IgnitorDsl) = plus(other)

/** Alias for [minus], the script's `sub`. */
fun IgnitorDsl.sub(other: IgnitorDsl) = minus(other)

/** Alias for [neg], the script's `negate`. */
fun IgnitorDsl.negate() = neg()

/** Alias for [pow], the script's `power`. */
fun IgnitorDsl.power(exp: IgnitorDsl) = pow(exp)

/** Alias for [lerp], the script's `mix`. */
fun IgnitorDsl.mix(other: IgnitorDsl, t: IgnitorDsl) = lerp(other = other, t = t)

/** Alias for [mod], the script's `rem`. */
fun IgnitorDsl.rem(other: IgnitorDsl) = mod(other)

/** Alias for [recip], the script's `reciprocal`. */
fun IgnitorDsl.reciprocal() = recip()

/**
 * Places the `pregain` slot here: how hard the pattern plays INTO whatever follows.
 *
 * Exactly `mul(IgnitorDsl.Slots.pregain)`, written out as a call to [mul] so the two spellings
 * can never drift apart: same node, same operand order, same content id. The script door's
 * `.pregain()` builds the same node from `Ignitor.slot.pregain`, which is this same singleton.
 *
 * No parameter: the slot IS the parameter, and a pattern moves it with `pregain(x)`. Put it in
 * front of the nonlinearity it should drive, once (see [IgnitorDsl.Slots.pregain] for why once).
 * On a tree with nothing nonlinear after it, this is a plain level.
 */
fun IgnitorDsl.pregain() = mul(IgnitorDsl.Slots.pregain)

/** Divides this signal by a modulatable [other] divisor. */
fun IgnitorDsl.div(other: IgnitorDsl) = IgnitorDsl.Div(left = this, right = other)

/** Subtracts [other] from this signal sample-by-sample. */
fun IgnitorDsl.minus(other: IgnitorDsl) = IgnitorDsl.Minus(left = this, right = other)

/** Negates this signal (flips polarity). */
fun IgnitorDsl.neg() = IgnitorDsl.Neg(inner = this)

/** Absolute value of this signal (full-wave rectification). */
fun IgnitorDsl.abs() = IgnitorDsl.Abs(inner = this)

/** Raises this signal to the power of [exp]. Signed-magnitude (no NaN for negative bases). */
fun IgnitorDsl.pow(exp: IgnitorDsl) = IgnitorDsl.Pow(base = this, exp = exp)

/**
 * Enforces a minimum allowed value per sample: this signal, but at least [other].
 *
 * The node crossing is deliberate: a floor is the per-sample *maximum* of the two signals,
 * so the `min` door builds [IgnitorDsl.Max]. Do not "correct" it.
 */
fun IgnitorDsl.min(other: IgnitorDsl) = IgnitorDsl.Max(left = this, right = other)

/**
 * Enforces a maximum allowed value per sample: this signal, but at most [other].
 *
 * The node crossing is deliberate: a cap is the per-sample *minimum* of the two signals,
 * so the `max` door builds [IgnitorDsl.Min]. Do not "correct" it.
 */
fun IgnitorDsl.max(other: IgnitorDsl) = IgnitorDsl.Min(left = this, right = other)

/** Bounds this signal to the range `[lo, hi]` per sample. */
fun IgnitorDsl.clamp(lo: IgnitorDsl, hi: IgnitorDsl) = IgnitorDsl.Clamp(inner = this, lo = lo, hi = hi)

/** `e^x` per sample. */
fun IgnitorDsl.exp() = IgnitorDsl.Exp(inner = this)

/** Natural logarithm per sample. Signed-magnitude; `log(0) = 0` (no `-Inf`). */
fun IgnitorDsl.log() = IgnitorDsl.Log(inner = this)

/** Square root per sample. Signed-magnitude (no `NaN` for negatives). */
fun IgnitorDsl.sqrt() = IgnitorDsl.Sqrt(inner = this)

/** Sign of this signal: `-1`, `0`, or `+1`. */
fun IgnitorDsl.sign() = IgnitorDsl.Sign(inner = this)

/** `tanh(x)` per sample (smooth saturation curve). */
fun IgnitorDsl.tanh() = IgnitorDsl.Tanh(inner = this)

/** Linear interpolation: `this·(1−t) + other·t`. */
fun IgnitorDsl.lerp(other: IgnitorDsl, t: IgnitorDsl) = IgnitorDsl.Lerp(left = this, right = other, t = t)

/** Maps this signal from `[-1, 1]` to `[from, to]` per sample; where the swing sits: the script door `range`. */
fun IgnitorDsl.range(from: IgnitorDsl, to: IgnitorDsl) = IgnitorDsl.Range(inner = this, from = from, to = to)

/**
 * The exponential twin of [range]: maps this signal from `[-1, 1]` to `[from, to]` so that equal steps of the signal
 * are equal RATIOS, `from · (to / from)^((x + 1) / 2)`. Perceptually even for frequencies; the script door `rangex`
 * has the table and the example.
 *
 * Built by composition, no node of its own: `exp(range(ln(max(from, floor)), ln(max(to, floor))))`, with
 * [RANGEX_FLOOR] as the floor. A value at or below 0 is coerced, as in sprudel, instead of meeting the engine's
 * signed-magnitude `Log` (`0 -> 0`, `-v -> -ln v`), which would give a wrong sweep: `rangex(0, 1000)` would start
 * at 1, not at almost nothing. The mathematical [IgnitorDsl.Max] node on purpose: the door named `max` is a clamp
 * and builds [IgnitorDsl.Min]. The operand order matters too: the value on the left and the floor on the right, so
 * a NaN value reads as the floor (`Max` keeps its right operand when `left > right` is false).
 */
fun IgnitorDsl.rangex(from: IgnitorDsl, to: IgnitorDsl): IgnitorDsl {
    val floor = IgnitorDsl.Constant(RANGEX_FLOOR)
    val logFrom = IgnitorDsl.Log(inner = IgnitorDsl.Max(left = from, right = floor))
    val logTo = IgnitorDsl.Log(inner = IgnitorDsl.Max(left = to, right = floor))

    return IgnitorDsl.Exp(inner = IgnitorDsl.Range(inner = this, from = logFrom, to = logTo))
}

/** Per-sample floor. */
fun IgnitorDsl.floor() = IgnitorDsl.Floor(inner = this)

/** Per-sample ceiling. */
fun IgnitorDsl.ceil() = IgnitorDsl.Ceil(inner = this)

/** Per-sample round to nearest integer. */
fun IgnitorDsl.round() = IgnitorDsl.Round(inner = this)

/** Per-sample fractional part: `x − floor(x)`. */
fun IgnitorDsl.frac() = IgnitorDsl.Frac(inner = this)

/** Per-sample modulo. Zero divisors substituted with `1e-30` to avoid `NaN`. */
fun IgnitorDsl.mod(other: IgnitorDsl) = IgnitorDsl.Mod(left = this, right = other)

/** Per-sample reciprocal: `1 / x`. Zero inputs substituted with `1e-30` to avoid `NaN`. */
fun IgnitorDsl.recip() = IgnitorDsl.Recip(inner = this)

/** Per-sample square: `x · x`. */
fun IgnitorDsl.sq() = IgnitorDsl.Sq(inner = this)

/** Per-sample conditional: when this signal `> 0` use [whenTrue], else [whenFalse]. */
fun IgnitorDsl.select(whenTrue: IgnitorDsl, whenFalse: IgnitorDsl) =
    IgnitorDsl.Select(cond = this, whenTrue = whenTrue, whenFalse = whenFalse)

// Composition

/**
 * Runs this signal through [stages] in series, in the order written: `x.serial(a, b, c)` is `c(b(a(x)))`, the
 * same node as the nested calls. A stage is any function from a signal to a signal (a pickup, a pedal, an amp,
 * a cab), so a signal chain is written as the list it is, with no fixed number of slots. With no stage,
 * `serial()` returns the signal as it is.
 *
 * One stage into the next. Not sprudel's `apply(f, g)`, which stacks the results side by side.
 */
fun IgnitorDsl.serial(vararg stages: (IgnitorDsl) -> IgnitorDsl): IgnitorDsl =
    stages.fold(this) { signal, stage -> stage(signal) }

/**
 * Runs this signal through [branches] side by side and SUMS them, the twin of [serial]: `x.parallel(a, b)` is
 * `a(x) + b(x)`, every branch reading the same `x` (one instance, built once; a pitch node in a branch forks it). A
 * branch that delays the signal (an oversampled `distort` or `shape`) is matched in phase by the others, each running
 * through an unshaped round trip of the oversampler it lacks, so the sum does not comb ([IgnitorDsl.Parallel]).
 * The sum is plain: `x.parallel({ it }, { it })` is twice `x`; a branch's own `mul` sets the blend.
 *
 * With no branch, `parallel()` returns the signal as it is; with one, that branch's output.
 */
fun IgnitorDsl.parallel(vararg branches: (IgnitorDsl) -> IgnitorDsl): IgnitorDsl = when (branches.size) {
    0 -> this
    1 -> branches[0](this)
    else -> IgnitorDsl.Parallel(branches = branches.map { it(this) })
}

/**
 * A dry/wet blend, the linear law: `x.blend(wet, f)` is `x.parallel({ it.mul(1 - wet) }, { f(it).mul(wet) })`, so 0
 * is the dry signal and 1 is [branch]'s output alone (the dry/wet helper of KLANG-00216 (signal graph engine)
 * §6.9; the linear law only, the default taken 2026-10-10 in KLANG-00297 (parallel serial bands)). Linear
 * is right for correlated branches (distortion, filters); a decorrelated one (a room) loses about 3 dB in the middle,
 * which a song can lift with the branch's own `mul`. [wet] may be a slot or a signal (a moving blend); a constant that
 * is not finite reads as 0, the dry signal. The branch is matched in phase as in [parallel]. ONE door, typed (the
 * `Double` twin went with the typed-doors rollout S6): a number is `0.25.ign`.
 */
fun IgnitorDsl.blend(wet: IgnitorDsl, branch: (IgnitorDsl) -> IgnitorDsl): IgnitorDsl {
    // NaN-guard on a value the author can write: an unset blend is no blend. The one guard, for both doors.
    val share = if (wet is IgnitorDsl.Constant && !wet.value.isFinite()) IgnitorDsl.Constant(0.0) else wet
    val dry = if (share is IgnitorDsl.Constant) IgnitorDsl.Constant(1.0 - share.value) else IgnitorDsl.Constant(1.0).minus(share)

    return parallel({ it.mul(dry) }, { branch(it).mul(share) })
}

/**
 * Splits this signal into frequency bands, processes each on its own and sums them again: the bands from an
 * [IgnitorBandsBuilder] (`band`, `cut`), the Linkwitz-Riley crossover in `BandsBuilders.kt`. With no [configure], or no
 * `cut`, it is the one band. The text lives on the script door (`KlangScriptIgnitorExtensions.scriptBands`).
 *
 *     saw.bands { it.cut(250.0).band { mid -> mid.distort(0.4.ign) }.cut(3000.0) }
 */
fun IgnitorDsl.bands(configure: ((IgnitorBandsBuilder) -> IgnitorBandsBuilder)? = null): IgnitorDsl =
    IgnitorBandsBuilder().configured(configure).split(signal = this)

// Frequency

/**
 * Shifts pitch by [semitones], a number (`7.ign`), a slot or a moving signal. ONE door, typed (the `Double` twin went
 * with the typed-doors rollout S3c); the text lives on the script door (`KlangScriptIgnitorExtensions.detune`).
 */
fun IgnitorDsl.detune(semitones: IgnitorDsl): IgnitorDsl.Detune = IgnitorDsl.Detune(inner = this, semitones = semitones)

/** Shifts pitch up one octave: [detune] by 12 semitones. */
fun IgnitorDsl.octaveUp(): IgnitorDsl.Detune = detune(semitones = 12.ign)

/** Shifts pitch down one octave: [detune] by -12 semitones. */
fun IgnitorDsl.octaveDown(): IgnitorDsl.Detune = detune(semitones = (-12).ign)

// Filters

/**
 * The five cutoff-envelope knobs a filter DOOR hands to a filter node, after the compound fill.
 */
data class FilterEnvelopeKnobs(
    val env: IgnitorDsl,
    val attack: IgnitorDsl,
    val decay: IgnitorDsl,
    val sustain: IgnitorDsl,
    val release: IgnitorDsl,
)

/**
 * **The compound fill for the filter cutoff envelope.** `null` means the call did NOT name that
 * knob; everything else is an explicit value and is never overwritten.
 *
 * The rule itself has ONE home and it is not here: `/dsl-design` section 4, "A compound door fills
 * per param, at the door, everywhere". What THIS door does under it: the filter envelope has no
 * NAME knob, so ANY of its five knobs names the stage, and a call that names one writes every
 * companion it left out from `audio_bridge/constants/FilterEnvelopeDefaults.kt`. `env` (the DEPTH)
 * is a companion like the rest, which is what makes `lowpass(800, x => x.adsr(0.01, 0.3, 0.2, 0.1))`
 * an audible pluck instead of a silent no-op. Both doors name the stages through the builder
 * ([FilterKnobs]), the four stage times in one `adsr` call; this runs once per door call, after the
 * lambda ([FilterKnobs.envelope], typed-doors rollout S3a).
 *
 * It exists because the sprudel door behaves that way and the two surfaces have to agree: a
 * pattern that writes ANY of the `lpf.*` envelope stage slots but no depth gets
 * [FILTER_ENV_DEPTH_SEMITONES] from the runtime's slot-layer depth fill (`slotLayerDepth` in
 * `audio_be/.../IgnitorDslRuntime.kt`). `lpf(800, decay = 0.3, sustain = 0.2)` is a pluck, so `lowpass(800, x => x.adsr(0.01, 0.3, 0.2, 0.1))` has to be a pluck too, with the
 * same depth and the same stage times, and (decision D3) the same default curve through them,
 * `MOD_ENV_CURVE`; see [IgnitorDsl.Lowpass.env].
 *
 * A call that names NOTHING gets `env = 0`, which is the node's "no envelope" switch, and the four
 * stage knobs at their constants where they are inert. `lowpass(800)` is therefore exactly the
 * filter it was before this fill existed.
 *
 * The NODE's own field defaults are deliberately not this function: a hand-built
 * [IgnitorDsl.Lowpass] is a value, not a call, and has no "named" to read.
 */
fun fillFilterEnvelope(
    env: IgnitorDsl?,
    attack: IgnitorDsl?,
    decay: IgnitorDsl?,
    sustain: IgnitorDsl?,
    release: IgnitorDsl?,
): FilterEnvelopeKnobs {
    val namesTheStage =
        env != null || attack != null || decay != null || sustain != null || release != null

    return FilterEnvelopeKnobs(
        // The depth is a companion, not a gate: only a call that names NO knob at all leaves the
        // envelope off.
        env = env ?: if (namesTheStage) IgnitorDsl.Constant(FILTER_ENV_DEPTH_SEMITONES) else IgnitorDsl.Constant(0.0),
        attack = attack ?: IgnitorDsl.Constant(FILTER_ENV_ATTACK_SEC),
        decay = decay ?: IgnitorDsl.Constant(FILTER_ENV_DECAY_SEC),
        sustain = sustain ?: IgnitorDsl.Constant(FILTER_ENV_SUSTAIN_LEVEL),
        release = release ?: IgnitorDsl.Constant(FILTER_ENV_RELEASE_SEC),
    )
}

/**
 * A filter's `passes` knob as the node carries it: a constant is rounded and coerced to 1..[FILTER_MAX_PASSES] here,
 * at the door ([coercePasses], S3a's rule); a slot or another node passes through, and the runtime coerces the value it
 * reads at voice build the same way (`passesKnob`).
 */
private fun IgnitorDsl.coercedPasses(): IgnitorDsl = when (this) {
    is IgnitorDsl.Constant -> IgnitorDsl.Constant(coercePasses(value).toDouble())
    else -> this
}

/** A modulation envelope's unshaped curve: the index of `MOD_ENV_CURVE`, the node fields' default. */
private fun modEnvCurveKnob(): IgnitorDsl = AdsrCurves.knob(MOD_ENV_CURVE)

/**
 * Applies an SVF lowpass filter at [freq] with resonance [q]. Any value may be a number (`800.ign`), a slot or a
 * moving signal. The door carries the filter's musical inputs; everything else is a knob on the [FilterBuilder] the
 * [configure] lambda receives, the same builder and shape as the script door:
 *
 * ```kotlin
 * Ign.saw().lowpass(freq = 800.ign, q = 1.2.ign) { it.passes(2.ign).analog(3.ign) }
 * Ign.saw().lowpass(800.ign) { it.env(24.ign).adsr(0.005.ign, 0.3.ign, 0.2.ign, 0.2.ign) { e -> e.curves("lin", "exp", "exp") } }
 * // script: Ignitor.saw().lowpass(800, x => x.env(24).adsr(0.005, 0.3, 0.2, 0.2, e => e.curves("lin", "exp", "exp")))
 * ```
 *
 * The cutoff envelope is a COMPOUND pair (`/dsl-design` section 4): naming `env` or `adsr` in the lambda switches the
 * envelope on and fills the other from `constants/FilterEnvelopeDefaults.kt`, once, after the lambda
 * ([fillFilterEnvelope], here, for both doors); a lambda that names neither is the filter without an envelope. The
 * four stage times are named together, by the builder's `adsr`: the flat door that named one stage at a time
 * (`lowpass(800.0, decay = 0.3)`) is gone, recorded in KLANG-00314 (kotlin typed doors) (rollout S3a). `passes` is
 * rounded and coerced to 1..[FILTER_MAX_PASSES] here when it is a number ([coercePasses]); a SLOT goes straight
 * in (`it.passes(IgnitorDsl.Slots.lpf.passes)`) and the runtime coerces what it reads. See [IgnitorDsl.Lowpass]; the full text lives on the script door
 * (`KlangScriptIgnitorExtensions.lowpass`).
 */
fun IgnitorDsl.lowpass(
    freq: IgnitorDsl,
    q: IgnitorDsl = FILTER_Q.ign,
    configure: ((FilterBuilder) -> FilterBuilder)? = null,
): IgnitorDsl.Lowpass {
    val k = FilterBuilder().configured(configure).knobs
    val envelope = k.envelope()

    return IgnitorDsl.Lowpass(
        inner = this,
        freq = freq,
        q = q,
        analog = k.analog,
        passes = k.passes.coercedPasses(),
        env = envelope.env,
        attack = envelope.attack,
        decay = envelope.decay,
        sustain = envelope.sustain,
        release = envelope.release,
        attackCurve = k.attackCurve ?: modEnvCurveKnob(),
        decayCurve = k.decayCurve ?: modEnvCurveKnob(),
        releaseCurve = k.releaseCurve ?: modEnvCurveKnob(),
        humanize = k.humanize,
    )
}

/** Applies an SVF highpass filter at [freq] with resonance [q]; the knobs are [lowpass]'s, on the same [FilterBuilder]. */
fun IgnitorDsl.highpass(
    freq: IgnitorDsl,
    q: IgnitorDsl = FILTER_Q.ign,
    configure: ((FilterBuilder) -> FilterBuilder)? = null,
): IgnitorDsl.Highpass {
    val k = FilterBuilder().configured(configure).knobs
    val envelope = k.envelope()

    return IgnitorDsl.Highpass(
        inner = this,
        freq = freq,
        q = q,
        analog = k.analog,
        passes = k.passes.coercedPasses(),
        env = envelope.env,
        attack = envelope.attack,
        decay = envelope.decay,
        sustain = envelope.sustain,
        release = envelope.release,
        attackCurve = k.attackCurve ?: modEnvCurveKnob(),
        decayCurve = k.decayCurve ?: modEnvCurveKnob(),
        releaseCurve = k.releaseCurve ?: modEnvCurveKnob(),
        humanize = k.humanize,
    )
}

/**
 * Applies an SVF bandpass filter at [freq], [q] its width; the knobs are [lowpass]'s without `passes`, on a
 * [BandFilterBuilder].
 */
fun IgnitorDsl.bandpass(
    freq: IgnitorDsl,
    q: IgnitorDsl = FILTER_Q.ign,
    configure: ((BandFilterBuilder) -> BandFilterBuilder)? = null,
): IgnitorDsl.Bandpass {
    val k = BandFilterBuilder().configured(configure).knobs
    val envelope = k.envelope()

    return IgnitorDsl.Bandpass(
        inner = this,
        freq = freq,
        q = q,
        analog = k.analog,
        env = envelope.env,
        attack = envelope.attack,
        decay = envelope.decay,
        sustain = envelope.sustain,
        release = envelope.release,
        attackCurve = k.attackCurve ?: modEnvCurveKnob(),
        decayCurve = k.decayCurve ?: modEnvCurveKnob(),
        releaseCurve = k.releaseCurve ?: modEnvCurveKnob(),
        humanize = k.humanize,
    )
}

/** Applies an SVF notch (band-reject) filter at [freq], [q] its width; the knobs are [bandpass]'s, on a [BandFilterBuilder]. */
fun IgnitorDsl.notch(
    freq: IgnitorDsl,
    q: IgnitorDsl = FILTER_Q.ign,
    configure: ((BandFilterBuilder) -> BandFilterBuilder)? = null,
): IgnitorDsl.Notch {
    val k = BandFilterBuilder().configured(configure).knobs
    val envelope = k.envelope()

    return IgnitorDsl.Notch(
        inner = this,
        freq = freq,
        q = q,
        analog = k.analog,
        env = envelope.env,
        attack = envelope.attack,
        decay = envelope.decay,
        sustain = envelope.sustain,
        release = envelope.release,
        attackCurve = k.attackCurve ?: modEnvCurveKnob(),
        decayCurve = k.decayCurve ?: modEnvCurveKnob(),
        releaseCurve = k.releaseCurve ?: modEnvCurveKnob(),
        humanize = k.humanize,
    )
}

/**
 * Applies a one-pole lowpass at [freq] Hz: 6 dB/oct, no resonance; musically a warmth/tone control. ONE name on every
 * door (formerly `onePoleLowpass`; the sprudel door's `warmth` collapsed into this too). No knobs, so no builder.
 */
fun IgnitorDsl.onepole(freq: IgnitorDsl): IgnitorDsl.OnePoleLowpass = IgnitorDsl.OnePoleLowpass(inner = this, freq = freq)

/**
 * Controls the graph optimizer for the whole definition this node ends up in.
 *
 * `optimizer(0)` turns it OFF, so the tree renders exactly as authored; that is the useful
 * call, for A/B-ing a fusion by ear or as a hatch if one ever misbehaves. Any other value
 * leaves it on, and the marker then dissolves without trace (so it is not a fusion wall).
 * Note the default is 1: a bare `optimizer()` changes nothing. Same name and default as the
 * KlangScript stdlib `optimizer()`.
 */
fun IgnitorDsl.optimizer(on: Int = 1): IgnitorDsl = IgnitorDsl.OptimizerHint(inner = this, on = on)

/**
 * Starts (or continues) a fused equalizer, see [IgnitorDsl.Eq], and appends the sections the [configure] lambda
 * names on the [EqBuilder] (`band`, `tap`), in written order; the same builder and shape as the script door:
 *
 * ```kotlin
 * Ign.saw().eq { it.band(freq = 300.ign, q = 1.0.ign, db = (-4).ign).tap(freq = 850.ign, q = 0.707.ign, gain = 1.7.ign) }   // script: e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7)
 * ```
 *
 * Idempotent: an `eq` directly on an Eq continues it. That composes badly with `tap` on a SHARED sound: if `base`
 * already ends in an Eq, `base.eq { it.tap(...) }` appends into THAT Eq, so the tap reads base's PRE-Eq input rather
 * than its output, silently a different sound. Harmless for `band` (an append at the end is a plain serial append).
 * The full text lives on the script door (`KlangScriptIgnitorExtensions.eq`) and its knobs.
 */
fun IgnitorDsl.eq(configure: ((EqBuilder) -> EqBuilder)? = null): IgnitorDsl.Eq {
    val opened = when (this) {
        is IgnitorDsl.Eq -> this
        else -> IgnitorDsl.Eq(inner = this)
    }

    return EqBuilder(opened).configured(configure).node
}

/**
 * Pre-amplification: gain without a curve, see [IgnitorDsl.Drive]. [amount] is a number (`0.4.ign`), a slot or a
 * signal; ONE door, typed (the `Double` twin went with the typed-doors rollout S3c).
 */
fun IgnitorDsl.drive(amount: IgnitorDsl): IgnitorDsl.Drive = IgnitorDsl.Drive(inner = this, amount = amount)

/**
 * Pure waveshaping without drive. [shape] is the waveshaper's INDEX in [DistortionShapes] (`DistortionShapes.SOFT_INDEX`,
 * `soft`, by default), [oversample] the oversampling factor (2, 4, 8; 0 or 1 = off); both are knobs the runtime reads
 * once per note, so each takes a number (`4.ign`) or a slot (a moving signal is a non-leaf and reads as the default). The name form
 * takes the waveshaper's name. See [IgnitorDsl.Shape] for the shapes:
 *
 * Quick reference:
 *  - soft / gentle / softsat / cubic / exp / sineshaper: symmetric soft
 *  - hard / zerosquare / chebyshev / fold / linearfold: symmetric hard / wavefolding
 *  - diode / tube / asym / stompbox / rectify: asymmetric (even harmonics, DC offset)
 *
 * Typed since the typed-doors rollout S3c (maintainer, 2026-10-16: slot knobs, not build-time knobs); the full text
 * lives on the script door (`KlangScriptIgnitorExtensions.shape`).
 */
fun IgnitorDsl.shape(
    shape: IgnitorDsl = DistortionShapes.SOFT_INDEX.ign,
    oversample: IgnitorDsl = SHAPE_OVERSAMPLE.ign,
): IgnitorDsl.Shape = IgnitorDsl.Shape(inner = this, shape = shape, oversample = oversample)

/**
 * [shape] by NAME (`shape("tube")`), through the lookup the script door uses ([DistortionShapes.indexOf]): an unknown
 * name is `soft`, coerced, never thrown. The name has no default here (pattern point 9: with the index form all-default,
 * a bare call would be ambiguous).
 */
fun IgnitorDsl.shape(shape: String, oversample: IgnitorDsl = SHAPE_OVERSAMPLE.ign): IgnitorDsl.Shape =
    shape(shape = DistortionShapes.indexOf(shape).ign, oversample = oversample)

// Envelope

/**
 * Wraps this signal in an ADSR amplitude envelope: [attack], [decay] and [release] in seconds, [sustain] a level 0 to
 * 1, each a number (`0.01.ign`), a slot or a signal. The curves and the de-click are knobs on the [AdsrBuilder] the
 * [configure] lambda receives, the same builder and shape as the script door:
 *
 * ```kotlin
 * Ign.saw().adsr(0.01.ign, 0.3.ign, 0.5.ign, 0.2.ign) { it.curves("linear", "exp", "exp").declick(0.0005.ign) }
 * // script: Ignitor.saw().adsr(0.01, 0.3, 0.5, 0.2, e => e.curves("linear", "exp", "exp").declick(0.0005))
 * ```
 *
 * Unshaped stages keep the node's default, [AdsrCurve.Default] (exponential); an unnamed `declick` keeps the node's
 * `declick` slot (0 = off). The flat door that took the curves and the de-click as parameters
 * (`adsr(a, d, s, r, attackCurve = ..., declick = ...)`) and its `Double` twin went with the typed-doors rollout S3b
 * (KLANG-00314 (kotlin typed doors)). The envelope's ON/OFF switch is on neither door ([IgnitorDsl.Adsr.on]). The
 * full text lives on the script door (`KlangScriptIgnitorExtensions.adsr`).
 */
fun IgnitorDsl.adsr(
    attack: IgnitorDsl,
    decay: IgnitorDsl,
    sustain: IgnitorDsl,
    release: IgnitorDsl,
    configure: ((AdsrBuilder) -> AdsrBuilder)? = null,
): IgnitorDsl.Adsr = AdsrBuilder(
    IgnitorDsl.Adsr(inner = this, attack = attack, decay = decay, sustain = sustain, release = release),
).configured(configure).node

// FM

/**
 * Applies FM synthesis to this carrier: [modulator] at [ratio] times the carrier's frequency, [depth] the modulation
 * index in Hz. The index envelope is a knob on the [FmBuilder] the [configure] lambda receives, the same builder and
 * shape as the script door:
 *
 * ```kotlin
 * Ign.sine().fm(modulator = Ign.sine(), ratio = 1.4.ign, depth = 300.ign) { it.adsr(0.001.ign, 0.5.ign, 0.ign, 0.05.ign) }
 * // script: Ignitor.sine().fm(Ignitor.sine(), 1.4, 300, x => x.adsr(0.001, 0.5, 0, 0.05))
 * ```
 *
 * Without the knob the depth is constant (`FM_ENV_*`, `adsr(0, 0, 1, 0)`). The flat door that took the four stages
 * as parameters went with the typed-doors rollout S3b. See [IgnitorDsl.Fm]; the full text lives on the script door
 * (`KlangScriptIgnitorExtensions.fm`).
 */
fun IgnitorDsl.fm(
    modulator: IgnitorDsl,
    ratio: IgnitorDsl,
    depth: IgnitorDsl,
    configure: ((FmBuilder) -> FmBuilder)? = null,
): IgnitorDsl.Fm = FmBuilder(
    IgnitorDsl.Fm(carrier = this, modulator = modulator, ratio = ratio, depth = depth),
).configured(configure).node

// Effects

/**
 * Applies waveshaping distortion: [drive] by [amount], then [shape] with the waveshaper [shape] (an index, `soft` by
 * default) and the [oversample] factor, each a number or a slot, read once per note; the name form takes the waveshaper's name.
 * See [IgnitorDsl.Shape] for the shapes, and the script door (`KlangScriptIgnitorExtensions.distort`) for the full text.
 */
fun IgnitorDsl.distort(
    amount: IgnitorDsl,
    shape: IgnitorDsl = DistortionShapes.SOFT_INDEX.ign,
    oversample: IgnitorDsl = SHAPE_OVERSAMPLE.ign,
): IgnitorDsl.Shape = drive(amount = amount).shape(shape = shape, oversample = oversample)

/** [distort] with the waveshaper by NAME (`distort(amount = 0.4.ign, shape = "tube")`), through [DistortionShapes.indexOf]. */
fun IgnitorDsl.distort(amount: IgnitorDsl, shape: String, oversample: IgnitorDsl = SHAPE_OVERSAMPLE.ign): IgnitorDsl.Shape =
    drive(amount = amount).shape(shape = shape, oversample = oversample)

/**
 * Applies bit-crush quantization at the bit depth [bits] (`2^bits` levels; below 1 it passes through), a number
 * (`6.ign`), a slot or a signal. ONE door, typed (the `Double` twin went with the typed-doors rollout S3c).
 */
fun IgnitorDsl.crush(bits: IgnitorDsl): IgnitorDsl.Crush = IgnitorDsl.Crush(inner = this, bits = bits)

/**
 * Applies sample-rate reduction by the sample-hold [factor] (at 1 or less nothing is held), a number (`3.ign`), a slot
 * or a signal. ONE door, typed (the `Double` twin went with the typed-doors rollout S3c).
 */
fun IgnitorDsl.coarse(factor: IgnitorDsl): IgnitorDsl.Coarse = IgnitorDsl.Coarse(inner = this, factor = factor)

/**
 * Applies a phaser effect: [wet] first, as on every door that has one, then the sweep [rate] in Hz. Both are required,
 * because a positional rate follows the wet; [center] and [sweep] in Hz default to the bus phaser's constants
 * ([PHASER_CENTER_HZ], [PHASER_SWEEP_HZ]). The dry floor is a knob on the [PhaserBuilder] the [configure] lambda
 * receives, the same builder and shape as the script door (`floor` is never a method on a signal, where it would meet
 * the round-down `floor()`):
 *
 * ```kotlin
 * Ign.saw().phaser(wet = 0.3.ign, rate = 0.5.ign) { it.floor(0.2.ign) }   // script: Ignitor.saw().phaser(0.3, 0.5, x => x.floor(0.2))
 * ```
 *
 * See [IgnitorDsl.Phaser]; the full text lives on the script door (`KlangScriptIgnitorExtensions.phaser`).
 */
fun IgnitorDsl.phaser(
    wet: IgnitorDsl,
    rate: IgnitorDsl,
    center: IgnitorDsl = PHASER_CENTER_HZ.ign,
    sweep: IgnitorDsl = PHASER_SWEEP_HZ.ign,
    configure: ((PhaserBuilder) -> PhaserBuilder)? = null,
): IgnitorDsl.Phaser = PhaserBuilder(
    IgnitorDsl.Phaser(inner = this, rate = rate, wet = wet, center = center, sweep = sweep),
).configured(configure).node

/**
 * True when the tremolo's swing is the literal default `(-1, 0)`, the classic dip: the one test of it, read by the
 * runtime (which then builds the classic gain) and by the graph census.
 */
fun IgnitorDsl.Tremolo.hasClassicRange(): Boolean =
    rangeFrom == IgnitorDsl.Constant(TREMOLO_RANGE_FROM) && rangeTo == IgnitorDsl.Constant(TREMOLO_RANGE_TO)

/**
 * Applies a tremolo (amplitude modulation): an LFO at [rate] Hz pulls the level down by up to [depth] (0 to 1). The
 * LFO's shape and where its swing sits are knobs on the [TremoloBuilder] the [configure] lambda receives, the same
 * builder and shape as the script door:
 *
 * ```kotlin
 * Ign.saw().tremolo(rate = 4.ign, depth = 0.8.ign) { it.shape("square").range(from = 0.ign, to = 1.ign) }
 * // script: Ignitor.saw().tremolo(4, 0.8, x => x.shape("square").range(0, 1))
 * ```
 *
 * Without the knobs the LFO is a sine and the swing the classic dip (`range(-1, 0)`, [TREMOLO_RANGE_FROM]). The flat
 * door that took `shape`, `rangeFrom` and `rangeTo` as parameters went with the typed-doors rollout S3c
 * (KLANG-00314 (kotlin typed doors)). See [IgnitorDsl.Tremolo]; the full text lives on the script door
 * (`KlangScriptIgnitorExtensions.tremolo`).
 */
fun IgnitorDsl.tremolo(
    rate: IgnitorDsl,
    depth: IgnitorDsl,
    configure: ((TremoloBuilder) -> TremoloBuilder)? = null,
): IgnitorDsl.Tremolo = TremoloBuilder(
    IgnitorDsl.Tremolo(inner = this, rate = rate, depth = depth),
).configured(configure).node

/**
 * Applies a granular shimmer (pitch-shift cloud with feedback). [wet] first, as on every door that has one; [tone] is
 * the feedback path's lowpass cutoff in Hz, [pitches] the grains' transpositions in semitones, a plain list (an empty
 * one spawns no grains: only the dry plays, at its wet/dry level). The defaults are the `SHIMMER_*` constants, the
 * node's. The dry floor is a knob on the [ShimmerBuilder] the [configure] lambda receives, the same builder and shape
 * as the script door:
 *
 * ```kotlin
 * Ign.saw().shimmer(wet = 0.4.ign, feedback = 0.5.ign, tone = 4000.ign, pitches = listOf(0.0, 7.0, 12.0)) { it.floor(0.2.ign) }
 * // script: Ignitor.saw().shimmer(0.4, 0.5, 4000, [0, 7, 12], x => x.floor(0.2))
 * ```
 *
 * See [IgnitorDsl.Shimmer]; the full text lives on the script door (`KlangScriptIgnitorExtensions.shimmer`).
 */
fun IgnitorDsl.shimmer(
    wet: IgnitorDsl = SHIMMER_WET.ign,
    feedback: IgnitorDsl = SHIMMER_FEEDBACK.ign,
    tone: IgnitorDsl = SHIMMER_TONE_HZ.ign,
    pitches: List<Double> = SHIMMER_PITCHES,
    configure: ((ShimmerBuilder) -> ShimmerBuilder)? = null,
): IgnitorDsl.Shimmer = ShimmerBuilder(
    IgnitorDsl.Shimmer(inner = this, wet = wet, feedback = feedback, pitches = pitches, tone = tone),
).configured(configure).node

// Pitch modulation

/**
 * Applies vibrato (pitch modulation) at the given LFO [rate] in Hz, [semitones] deep. Any knob may be a number
 * (`5.ign`), a slot or a moving signal; a signal depth is read per sample. Where the swing sits and where the LFO
 * starts are knobs on the [VibratoBuilder], the same builder and shape as the script door:
 *
 * ```kotlin
 * Ign.saw().vibrato(5.ign, 0.3.ign) { it.range(0.ign, 1.ign) }   // script: Ign.saw().vibrato(5, 0.3, v => v.range(0, 1))
 * ```
 *
 * See [IgnitorDsl.Vibrato]; the full text lives on the script door (`KlangScriptIgnitorExtensions.vibrato`).
 */
fun IgnitorDsl.vibrato(
    rate: IgnitorDsl,
    semitones: IgnitorDsl,
    configure: ((VibratoBuilder) -> VibratoBuilder)? = null,
): IgnitorDsl.Vibrato = VibratoBuilder(
    IgnitorDsl.Vibrato(inner = this, rate = rate, semitones = semitones),
).configured(configure).node

/**
 * Applies a pitch glide of [semitones] from the onset to the gate close, held through the release; a number
 * (`12.ign`), a slot or a signal. ONE door, typed (the `Double` twin went with the typed-doors rollout S3c).
 */
fun IgnitorDsl.accelerate(semitones: IgnitorDsl): IgnitorDsl.Accelerate = IgnitorDsl.Accelerate(inner = this, semitones = semitones)

/**
 * Applies a pitch envelope: the pitch sweeps [semitones] up at the envelope's peak and back to the note. The stages
 * are ONE `adsr` call on the [PitchEnvelopeBuilder] the [configure] lambda receives, whose own lambda shapes them with
 * `curves`, the same builders and shape as the script door:
 *
 * ```kotlin
 * Ign.sine().pitchEnvelope(24.ign) { it.adsr(0.001.ign, 0.05.ign, 0.ign, 0.ign) { e -> e.curves("lin", "exp", "exp") } }
 * // script: Ignitor.sine().pitchEnvelope(24, x => x.adsr(0.001, 0.05, 0, 0, e => e.curves("lin", "exp", "exp")))
 * ```
 *
 * Without the `adsr` knob the stages are the `PITCH_ENV_*` constants, exponential (the node's defaults). New with the
 * typed-doors rollout S3b: before it, a Kotlin caller wrote the node. See [IgnitorDsl.PitchEnvelope]; the full text
 * lives on the script door (`KlangScriptIgnitorExtensions.pitchEnvelope`).
 */
fun IgnitorDsl.pitchEnvelope(
    semitones: IgnitorDsl,
    configure: ((PitchEnvelopeBuilder) -> PitchEnvelopeBuilder)? = null,
): IgnitorDsl.PitchEnvelope = PitchEnvelopeBuilder(
    IgnitorDsl.PitchEnvelope(inner = this, semitones = semitones),
).configured(configure).node

/**
 * Pitch modulation by any signal, the LINEAR law: the frequency times `1 + mod` (0 = no change, 1.0 = an octave up,
 * -1.0 stops the oscillator). In semitones: [pitchModSemitones].
 */
fun IgnitorDsl.pitchMod(mod: IgnitorDsl): IgnitorDsl.PitchMod = IgnitorDsl.PitchMod(inner = this, mod = mod)


/**
 * Pitch modulation by any signal, in SEMITONES: the frequency times `2^(mod / 12)` (0 = no change, 12 = an octave up).
 * The linear law: [pitchMod].
 */
fun IgnitorDsl.pitchModSemitones(mod: IgnitorDsl): IgnitorDsl.PitchModSemitones = IgnitorDsl.PitchModSemitones(inner = this, mod = mod)

// ═════════════════════════════════════════════════════════════════════════════════
// Discovery
// ═════════════════════════════════════════════════════════════════════════════════

/** Walks the DSL tree and collects all [IgnitorDsl.Param] leaf nodes. */
fun IgnitorDsl.getParamSlots(): List<IgnitorDsl.Param> {
    val result = mutableListOf<IgnitorDsl.Param>()
    collectParams(result)
    return result
}

