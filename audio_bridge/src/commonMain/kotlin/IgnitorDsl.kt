/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DEPTH_SEMITONES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.PULSE_FALL_FLANK
import io.peekandpoke.klang.audio_bridge.constants.PULSE_MIN_FLANK_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.PULSE_RISE_FLANK
import io.peekandpoke.klang.audio_bridge.constants.RAMP_RESET_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.RAMP_SHAPE_MAX
import io.peekandpoke.klang.audio_bridge.constants.SAW_RESET_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.SAW_SHAPE_MAX
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



/**
 * Process-local counter used to stamp each noise-source DSL instance with a unique
 * [IgnitorDsl.WhiteNoise.uid] / [IgnitorDsl.BrownNoise.uid] / [IgnitorDsl.PinkNoise.uid].
 *
 * Two calls to `Osc.whitenoise()` produce two different DSL objects with different uids —
 * and therefore two independent Ignitors under the identity-caching `toExciter`. Binding to
 * a variable and re-using it keeps a single instance, so `let s = Osc.whitenoise(); s + s`
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
 * audio bridge boundary for rendering in the audio worklet.
 */
@WireFormat
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
     * @param name parameter name — used for discovery, UI display, and oscParams override matching
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
     * A fixed constant value that cannot be overridden by oscParams.
     *
     * Use when the user explicitly sets a value (e.g. `Osc.sine(5)` = 5 Hz).
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
     * Canonical open parameter slots that mirror sprudel's `oscp(name, value)` calls.
     *
     * Use these when defining a custom sound that should respond to sprudel modulation
     * (e.g. `note("c").analog(0.3)`). Each slot is a `Param(name, default)` singleton with
     * the same name + default value that sprudel addons expect.
     *
     * Builtin sounds (`IgnitorDefaults.kt`) wire these in automatically; custom sounds
     * opt in explicitly:
     * ```
     * let mypad = Osc.sine(x => x.analog(OscSlot.analog))
     * note("c").sound(mypad)
     * ```
     */
    object Slots {
        val analog: IgnitorDsl = Param(name = "analog", default = 0.0)
        val bipolar: IgnitorDsl = Param(name = "bipolar", default = 0.0)
        val brightness: IgnitorDsl = Param(name = "brightness", default = 0.5)
        val chaos: IgnitorDsl = Param(name = "chaos", default = 1.5)
        val decay: IgnitorDsl = Param(name = "decay", default = 0.996)
        val declickSeconds: IgnitorDsl = Param(name = "declickSeconds", default = 0.0)
        val color: IgnitorDsl = Param(name = "color", default = 0.0)
        val density: IgnitorDsl = Param(name = "density", default = 0.2)
        val depth: IgnitorDsl = Param(name = "depth", default = 0.02)
        val duty: IgnitorDsl = Param(name = "duty", default = 0.5)

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
        // the same doors fill them. Named `<door>.<param>` after sprudel's readers (`lpf.freq`); each
        // group's KDoc in `IgnitorDslClassic.kt` names the defaults and why.

        /** The crush stage: `crush.amount`. */
        val crush: AmountSlots = AmountSlots("crush")

        /** The coarse stage: `coarse.amount`. */
        val coarse: AmountSlots = AmountSlots("coarse")

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

        /** The tremolo stage: `tremolo.depth`, `tremolo.sync`, `tremolo.shape`, `tremolo.skew`, `tremolo.phase`. */
        val tremolo: TremoloSlots = TremoloSlots()

        /** The amplitude envelope: `adsr.attack`, `adsr.decay`, `adsr.sustain`, `adsr.release`, `adsr.on`. */
        val adsr: AdsrSlots = AdsrSlots()

        /** The amplitude envelope's curves: `adsrCurves.attack`, `adsrCurves.decay`, `adsrCurves.release`. */
        val adsrCurves: AdsrCurvesSlots = AdsrCurvesSlots()

        /** The highpass envelope's curves: `hpfCurves.attack`, `hpfCurves.decay`, `hpfCurves.release`. */
        val hpfCurves: FilterCurvesSlots = FilterCurvesSlots("hpfCurves")

        /** The bandpass envelope's curves: `bpfCurves.attack`, `bpfCurves.decay`, `bpfCurves.release`. */
        val bpfCurves: FilterCurvesSlots = FilterCurvesSlots("bpfCurves")

        /** The notch envelope's curves: `notchCurves.attack`, `notchCurves.decay`, `notchCurves.release`. */
        val notchCurves: FilterCurvesSlots = FilterCurvesSlots("notchCurves")

        /** The lowpass envelope's curves: `lpfCurves.attack`, `lpfCurves.decay`, `lpfCurves.release`. */
        val lpfCurves: FilterCurvesSlots = FilterCurvesSlots("lpfCurves")
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Oscillator Primitives
    //
    // freq default: Freq = use the voice's note frequency (e.g. 440 Hz for A4).
    // Constant(n) = fixed frequency in Hz (e.g. Constant(5.0) for a 5 Hz LFO).
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Sine wave oscillator, optionally carrying banks of sine PARTIALS at multiples of its own
     * frequency (`docs/plans/sine-partial-banks.md`). The sine itself is partial 1 at gain
     * [fundamental]; [harmonics] adds partials at `2f, 3f, ...`, [octaves] at `2f, 4f, 8f, ...`,
     * [suboctaves] at `f/2, f/4, ...`. An added partial at `m * f` or `f / m` has gain
     * `m ^ -rolloff` of its bank (the distance from the fundamental is `m` either way). Banks
     * sum without deduplication. With the literal defaults (fundamental 1, every count 0) the
     * engine builds the plain sine, bit-identical to before the banks existed; anything else, a
     * `Param` included, builds the partial bank. Every knob is a signal read once per block.
     * Partials at or above Nyquist are silent (decided 2026-09-07). [analogSpread] blends the
     * drift lanes: 0 = one shared walk for the whole bank, 1 = one walk per partial.
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out); fundamental.collectParams(out)
            harmonics.collectParams(out); harmonicsRolloff.collectParams(out)
            octaves.collectParams(out); octavesRolloff.collectParams(out)
            suboctaves.collectParams(out); suboctavesRolloff.collectParams(out)
            analogSpread.collectParams(out)
        }

        /** True when every bank knob is its literal default: the engine builds the plain sine. */
        fun isPlainSine(): Boolean =
            fundamental == Constant(1.0) && harmonics == Constant(0.0) &&
                octaves == Constant(0.0) && suboctaves == Constant(0.0)
    }

    /** Sawtooth wave oscillator (rising ramp). */
    @WireName("sawtooth")
    data class Sawtooth(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /** Analog flyback time in samples — lower = brighter/sharper reset, higher = softer (default 2.0). */
        val resetSamples: Double = SAW_RESET_SAMPLES,
        /** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps very high notes sane. */
        val shapeMax: Double = SAW_SHAPE_MAX,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /**
     * Square wave oscillator (fixed 50% duty). NOTE: the `square` / `sqr` / `pulse` *sound names* are
     * registered to [Pulze] (one pulse oscillator with a `duty` osc-param); this type is retained as a
     * plain 50%-duty pulse used internally (presets, generic test fixtures).
     */
    @WireName("square")
    data class Square(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Triangle wave oscillator. */
    @WireName("triangle")
    data class Triangle(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        // No shape knobs: the triangle is the pulse engine with both flanks fully open (rise = fall = 1.0),
        // which makes the min-flank floor (PULSE_MIN_FLANK_SAMPLES) always overridden — there is nothing
        // tunable here (see Ignitors.triangle / WaveVoiceState.setPulseShape). Stays a plain oscillator.
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /**
     * White noise generator. Produces uniformly distributed random samples.
     *
     * [color] is an optional spectral tilt: `0` = flat white (default, filter bypassed); `<0` darkens
     * toward pink/brown (one-pole LP); `>0` brightens toward blue/violet (complementary HP). Range −1..1.
     *
     * [uid] is an instance-discriminator used by the runtime identity cache: each call to
     * `Osc.whitenoise()` creates a fresh [WhiteNoise] with a unique uid, so two such calls
     * yield two independent noise streams when composed (`a + b`). Binding to a variable and
     * re-using it (`let s = Osc.whitenoise(); s + s`) keeps a single instance and sums the
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

    /** Zawtooth wave oscillator. Naive sawtooth without PolyBLEP anti-aliasing (brighter/harsher). */
    @WireName("zawtooth")
    data class Zawtooth(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Zamp wave oscillator ("zamp"). Naive reverse sawtooth without anti-aliasing — the raw [Ramp]. */
    @WireName("zamp")
    data class Zamp(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Impulse oscillator. Emits a single-sample spike per cycle. */
    @WireName("impulse")
    data class Impulse(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); duty.collectParams(out); analog.collectParams(out)
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); duty.collectParams(out); analog.collectParams(out)
        }
    }

    /**
     * Brown (Brownian/red) noise generator. Random-walk filtered noise with -6 dB/oct slope.
     *
     * [depth] is the per-sample white-leak coefficient `k` in `out = (out + k·white)/(1+k)`: lower =
     * deeper / slower-walking brown (default 0.02); higher = brighter (more white mixed in each sample).
     *
     * See [WhiteNoise] for the [uid] instance-discriminator story.
     */
    @WireName("brown-noise")
    data class BrownNoise(
        val depth: IgnitorDsl = Slots.depth,
        val uid: Int = nextNoiseUid(),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            depth.collectParams(out)
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

    /** Ramp oscillator. Reverse sawtooth — FALLING, the negated [Sawtooth] (`Ignitors.ramp`
     *  builds it at `polarity = -1.0`). */
    @WireName("ramp")
    data class Ramp(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /** Analog flyback time in samples — lower = brighter/sharper reset, higher = softer (default 2.0). */
        val resetSamples: Double = RAMP_RESET_SAMPLES,
        /** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps high notes sane. */
        val shapeMax: Double = RAMP_SHAPE_MAX,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out)
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out)
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out)
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out)
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
            analogSpread.collectParams(out)
        }
    }

    /** Silent exciter. Outputs a zero-filled buffer. */
    @WireName("silence")
    data object Silence : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Physical Models
    // ═════════════════════════════════════════════════════════════════════════════

    /** Karplus-Strong plucked string physical model. */
    @WireName("pluck")
    data class Pluck(
        val freq: IgnitorDsl = Freq,
        val decay: IgnitorDsl = Slots.decay,
        val brightness: IgnitorDsl = Slots.brightness,
        val pickPosition: IgnitorDsl = Slots.pickPosition,
        val stiffness: IgnitorDsl = Slots.stiffness,
        val analog: IgnitorDsl = Slots.analog,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); decay.collectParams(out); brightness.collectParams(out); pickPosition.collectParams(out)
            stiffness.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison superpluck. Stacks multiple detuned Karplus-Strong voices for a chorus-like string sound. */
    @WireName("superpluck")
    data class SuperPluck(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Slots.voices,
        val spread: IgnitorDsl = Slots.spread,
        val decay: IgnitorDsl = Slots.decay,
        val brightness: IgnitorDsl = Slots.brightness,
        val pickPosition: IgnitorDsl = Slots.pickPosition,
        val stiffness: IgnitorDsl = Slots.stiffness,
        val analog: IgnitorDsl = Slots.analog,
        /** Drift lane blend, 0 = one shared analog walk for every voice, 1 = independent walks. */
        val analogSpread: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); decay.collectParams(out); brightness.collectParams(
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
     * wraps to zero. Missing `:n` defaults to index 0 at the registry boundary.
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
     * Maps the inner signal from `[-1, 1]` to `[lo, hi]` per sample.
     *
     * Standard LFO scaler. Output = `lo + (inner + 1)·0.5·(hi − lo)`.
     */
    @WireName("range")
    data class Range(
        val inner: IgnitorDsl,
        val lo: IgnitorDsl,
        val hi: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); lo.collectParams(out); hi.collectParams(out)
        }
    }

    /** Maps the inner signal from `[0, 1]` to `[-1, 1]` per sample. */
    @WireName("bipolar")
    data class Bipolar(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Maps the inner signal from `[-1, 1]` to `[0, 1]` per sample. */
    @WireName("unipolar")
    data class Unipolar(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
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
     * **The cutoff envelope ([env], [attackSec], [decaySec], [sustainLevel], [releaseSec]) and
     * the per-voice [humanize] lane are documented once, here**, and the other three filter nodes
     * point at this block rather than repeating it.
     */
    @WireName("lowpass")
    data class Lowpass(
        val inner: IgnitorDsl,
        val freq: IgnitorDsl = Constant(2000.0),
        val q: IgnitorDsl = Constant(0.707),
        /**
         * Analog character amount. `0` = clean linear filter (default — bit-identical
         * to pre-saturation behaviour). Higher values engage the OB-X-style state-dependent
         * damping in the SVF resonance feedback, compressing the resonance peak.
         * Typical range 0..10; values around 1–3 give Diva-default warmth.
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
        val passes: IgnitorDsl = Constant(1.0),
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
         * `lowpass(800, decaySec = 0.3, sustainLevel = 0.2)` is the audible pluck that
         * `lpf(800, decay = 0.3, sustain = 0.2)` is, not a silent no-op. Only a call that names
         * NOTHING leaves the envelope off. A hand-built node is a value, not a call, so it gets
         * the plain `0`.
         *
         * The four stage knobs below are read ONCE per voice, at build, from a [Param] or
         * [Constant] leaf (which is what a slot is). That matches the strip, whose
         * `FilterDef.envelope` is resolved at note-on too. **A non-leaf EXPRESSION here is not
         * "modulated", it is UNREADABLE at build**, and the consequence differs per knob: a stage
         * knob falls back to its constant, but this DEPTH falls back to `0`, which switches the
         * whole envelope OFF with no warning. `lowpass(800, x => x.env(Osc.param("e", 24).max(36)))`
         * renders a static filter. Write a slot (`OscSlot.lpf.env`) or a constant.
         *
         * **Which envelope this is (decision D3).** The law is the engine's one envelope law, and an
         * unshaped stage (curve knob at its default) takes `MOD_ENV_CURVE`, the house Exponential
         * curve (K = 3), which the voice strip's filter envelope takes too when sprudel's `lpfCurves`
         * names none (`FilterEnvDef.resolve`). The
         * sampling is the same on both: the envelope is computed at block START and block END and
         * the SVF coefficients are interpolated across the block. So the same stage times, depth and
         * curve give the same sweep on both surfaces.
         */
        val env: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope attack in seconds. Inert while [env] is `0`. */
        val attackSec: IgnitorDsl = Constant(FILTER_ENV_ATTACK_SEC),
        /** Cutoff-envelope decay in seconds. Inert while [env] is `0`. */
        val decaySec: IgnitorDsl = Constant(FILTER_ENV_DECAY_SEC),
        /** Cutoff-envelope sustain share of [env], 0 to 1. Inert while [env] is `0`. */
        val sustainLevel: IgnitorDsl = Constant(FILTER_ENV_SUSTAIN_LEVEL),
        /** Cutoff-envelope release in seconds. Inert while [env] is `0`. */
        val releaseSec: IgnitorDsl = Constant(FILTER_ENV_RELEASE_SEC),
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
         * [analog] resolves above 0, and then exactly as the voice strip does: one
         * `nextDouble()` for the tolerance, then the drift lane's three
         * (`buildFilterHumanization` in `audio_be` owns the order). [analog] is read at build from
         * a [Param] or [Constant] LEAF, like the envelope knobs: a non-leaf expression there is
         * unreadable at build, so the lane silently does not exist and this flag does nothing. The
         * strip has no such case to match, its `analog` being one number off the voice's bag.
         *
         * It is what the built-in instruments of phase 3 switch on so `analog(3)` keeps meaning
         * what it means today. Scales come from `constants/FilterHumanizationDefaults.kt`; the
         * per-engine `StageDsl.Filter` overrides do NOT reach a tree filter, because a tree has
         * no pipeline stage to carry them (the same asymmetry `FILTER_DRIVE_PER_ANALOG` already
         * has in `IgnitorFilters`).
         */
        val humanize: Boolean = false,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); freq.collectParams(out); q.collectParams(out); analog.collectParams(out)
            passes.collectParams(out)
            env.collectParams(out); attackSec.collectParams(out); decaySec.collectParams(out)
            sustainLevel.collectParams(out); releaseSec.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
        }
    }

    /** SVF highpass filter. Attenuates frequencies below the cutoff; [passes] cascades the stage. */
    @WireName("highpass")
    data class Highpass(
        val inner: IgnitorDsl,
        val freq: IgnitorDsl = Constant(200.0),
        val q: IgnitorDsl = Constant(0.707),
        /** See [Lowpass.analog] — same semantics for the HP tap. */
        val analog: IgnitorDsl = Constant(0.0),
        /** Cascade count, a knob read once at voice build; see [Lowpass.passes]. */
        val passes: IgnitorDsl = Constant(1.0),
        /** Cutoff-envelope depth in semitones, and the node's envelope switch; see [Lowpass.env]. */
        val env: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope attack in seconds; see [Lowpass.env]. */
        val attackSec: IgnitorDsl = Constant(FILTER_ENV_ATTACK_SEC),
        /** Cutoff-envelope decay in seconds; see [Lowpass.env]. */
        val decaySec: IgnitorDsl = Constant(FILTER_ENV_DECAY_SEC),
        /** Cutoff-envelope sustain share of [env]; see [Lowpass.env]. */
        val sustainLevel: IgnitorDsl = Constant(FILTER_ENV_SUSTAIN_LEVEL),
        /** Cutoff-envelope release in seconds; see [Lowpass.env]. */
        val releaseSec: IgnitorDsl = Constant(FILTER_ENV_RELEASE_SEC),
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
            env.collectParams(out); attackSec.collectParams(out); decaySec.collectParams(out)
            sustainLevel.collectParams(out); releaseSec.collectParams(out)
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
        val q: IgnitorDsl = Constant(0.707),
        /**
         * The analog SATURATION is not implemented for this tap and the value does not reach it
         * (same pattern as the voice-strip `SvfBPF`); see [Lowpass.analog] for the semantics when
         * it is. The value is NOT inert, though: it scales [humanize]'s per-voice cutoff tolerance
         * and its drift lane, exactly as it does on the strip, so `analog` on a bandpass is a
         * humanization amount today and a saturation amount as well later.
         */
        val analog: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope depth in semitones, and the node's envelope switch; see [Lowpass.env]. */
        val env: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope attack in seconds; see [Lowpass.env]. */
        val attackSec: IgnitorDsl = Constant(FILTER_ENV_ATTACK_SEC),
        /** Cutoff-envelope decay in seconds; see [Lowpass.env]. */
        val decaySec: IgnitorDsl = Constant(FILTER_ENV_DECAY_SEC),
        /** Cutoff-envelope sustain share of [env]; see [Lowpass.env]. */
        val sustainLevel: IgnitorDsl = Constant(FILTER_ENV_SUSTAIN_LEVEL),
        /** Cutoff-envelope release in seconds; see [Lowpass.env]. */
        val releaseSec: IgnitorDsl = Constant(FILTER_ENV_RELEASE_SEC),
        /** Curve of the cutoff envelope's attack; see [Lowpass.attackCurve]. */
        val attackCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's decay; see [Lowpass.attackCurve]. */
        val decayCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /** Curve of the cutoff envelope's release; see [Lowpass.attackCurve]. */
        val releaseCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        /**
         * Per-voice cutoff tolerance and drift lane; see [Lowpass.humanize]. The TOLERANCE and
         * the DRIFT reach this tap even though [analog]'s saturation does not: the strip's
         * `SvfBPF` takes the same `cutoffOffsetMul` and the same `FilterModRenderer` drift.
         */
        val humanize: Boolean = false,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); freq.collectParams(out); q.collectParams(out); analog.collectParams(out)
            env.collectParams(out); attackSec.collectParams(out); decaySec.collectParams(out)
            sustainLevel.collectParams(out); releaseSec.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
        }
    }

    /** SVF notch (band-reject) filter. Removes frequencies near the cutoff, passes others. */
    @WireName("notch")
    data class Notch(
        val inner: IgnitorDsl,
        val freq: IgnitorDsl = Constant(1000.0),
        val q: IgnitorDsl = Constant(0.707),
        /** The saturation is not implemented for this tap; it still scales [humanize]. See [Bandpass.analog]. */
        val analog: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope depth in semitones, and the node's envelope switch; see [Lowpass.env]. */
        val env: IgnitorDsl = Constant(0.0),
        /** Cutoff-envelope attack in seconds; see [Lowpass.env]. */
        val attackSec: IgnitorDsl = Constant(FILTER_ENV_ATTACK_SEC),
        /** Cutoff-envelope decay in seconds; see [Lowpass.env]. */
        val decaySec: IgnitorDsl = Constant(FILTER_ENV_DECAY_SEC),
        /** Cutoff-envelope sustain share of [env]; see [Lowpass.env]. */
        val sustainLevel: IgnitorDsl = Constant(FILTER_ENV_SUSTAIN_LEVEL),
        /** Cutoff-envelope release in seconds; see [Lowpass.env]. */
        val releaseSec: IgnitorDsl = Constant(FILTER_ENV_RELEASE_SEC),
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
            env.collectParams(out); attackSec.collectParams(out); decaySec.collectParams(out)
            sustainLevel.collectParams(out); releaseSec.collectParams(out)
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
            val q: IgnitorDsl = Constant(0.707),
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
            val q: IgnitorDsl = Constant(0.707),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freq.collectParams(out); q.collectParams(out)
            }
        }

        /** SVF band-pass section — [IgnitorDsl.Bandpass] as an Eq section; same defaults. */
        @WireName("eqBandpass")
        data class Bandpass(
            val freq: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(0.707),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freq.collectParams(out); q.collectParams(out)
            }
        }

        /** SVF notch section — [IgnitorDsl.Notch] as an Eq section; same defaults. */
        @WireName("eqNotch")
        data class Notch(
            val freq: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(0.707),
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
            val q: IgnitorDsl = Constant(0.707),
            val db: IgnitorDsl = Constant(0.0),
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
            val q: IgnitorDsl = Constant(0.707),
            val gain: IgnitorDsl = Constant(1.0),
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
        val attackSec: IgnitorDsl = Constant(0.01),
        val decaySec: IgnitorDsl = Constant(0.1),
        val sustainLevel: IgnitorDsl = Constant(ADSR_SUSTAIN_LEVEL),
        val releaseSec: IgnitorDsl = Constant(0.3),
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
         * De-click smoothing on the final gain, in seconds (`Slots.declickSeconds`, default `0` = off:
         * this per-ignitor envelope is intentionally not de-clicked). `>0` runs a one-pole low-pass on
         * the gain that rounds the C1 corners at segment joins (attack to decay peak, gate-off, cutoff),
         * killing the low-note "plop" the same way the amp VCA does. `oscParam`-addressable / patternable.
         */
        val declickSeconds: IgnitorDsl = Slots.declickSeconds,
        /**
         * The envelope's ON/OFF switch, read ONCE at voice build from a [Param] or [Constant] leaf.
         * OFF is exactly `0.0`; anything else is ON, and so is UNSET (a non-finite value) and a
         * non-leaf: the house flag rule (a non-zero number is on), and the envelope is built by
         * default (`docs/tasks/builtin-instruments.md` section 5b, the envelope row).
         *
         * A NODE FIELD ONLY, deliberately: no Ignitor door writes it. `classic()` fills it from
         * sprudel's `adsrOn`/`adsrOff`; on the Ignitor doors, not writing `adsr()` already means no
         * envelope (a recorded two-door asymmetry, maintainer, 2026-09-25).
         *
         * What OFF does today: the stage is not built, so the inner signal passes unchanged, and its
         * knob subtrees are not built either (a drawing source there takes no draws, the gate's
         * usual consequence). The voice's LIFETIME is kept: the node still reports the release tail
         * the envelope would have had, as the voice strip's `adsrOff` keeps it. There is no
         * teardown fade yet, so the voice ends on whatever its last frame carries; step 6's
         * teardown fade (section 6 of that plan) fills those last frames.
         */
        val on: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); attackSec.collectParams(out); decaySec.collectParams(out)
            sustainLevel.collectParams(out); releaseSec.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
            declickSeconds.collectParams(out); on.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM Synthesis
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Frequency modulation synthesis. The modulator's output shifts the carrier's frequency
     * at audio rate, with an optional ADSR envelope controlling modulation depth over time. The
     * envelope has no curve knob yet; its stages run `MOD_ENV_CURVE`, exponential (decision D3).
     */
    @WireName("fm")
    data class Fm(
        val carrier: IgnitorDsl,
        val modulator: IgnitorDsl,
        val ratio: IgnitorDsl = Constant(1.0),
        val depth: IgnitorDsl = Constant(0.0),
        val envAttackSec: IgnitorDsl = Constant(0.0),
        val envDecaySec: IgnitorDsl = Constant(0.0),
        val envSustainLevel: IgnitorDsl = Constant(1.0),
        val envReleaseSec: IgnitorDsl = Constant(0.0),
        /** The frequency the FM machinery runs on: the modulator is driven at `freq x ratio`
         *  and the index is `depth / freq`. Defaults to [Freq] (the note), which makes FM
         *  transpose under `detune` like any note-pitched oscillator; authored absolute
         *  (`Constant(...)`) the patch is immune, like `Osc.sine(5)` — the same
         *  musical/absolute separation every oscillator has (D13's Fm special case retired).
         *  DELIBERATELY not exposed on the `fm(...)` builder or the script door (maintainer
         *  decision 2026-08-30): a hidden internal of the pitch machinery, raw-door-only —
         *  the default IS the semantics; absolute authoring stays a power-user construction. */
        val freq: IgnitorDsl = Freq,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            carrier.collectParams(out); modulator.collectParams(out); ratio.collectParams(out); depth.collectParams(out)
            freq.collectParams(out)
            envAttackSec.collectParams(out); envDecaySec.collectParams(out); envSustainLevel.collectParams(out); envReleaseSec.collectParams(
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
     *   raw). A STOPGAP (decision D7, `docs/tasks/builtin-instruments.md` section 3): a knob so that
     *   `classic()` can fill it from `distort.oversample`, and `docs/tasks/oversampling-regions.md`
     *   retires it for a region. Default: 0 (off).
     */
    @WireName("shape")
    data class Shape(
        val inner: IgnitorDsl,
        val shape: IgnitorDsl = Constant(DistortionShapes.SOFT_INDEX.toDouble()),
        val oversample: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); shape.collectParams(out); oversample.collectParams(out)
        }
    }

    /**
     * Drive and shape as ONE unit, gated as a whole on [amount] (the `distort` row of the off-value
     * table, `docs/tasks/builtin-instruments.md` section 5b). Neither authoring door builds it: both
     * spell `distort` as `Shape(Drive(...))`, whose `Shape` half has no amount and cannot be gated. It is
     * `classic()`'s distort stage (phase 3 step 5), the one node that switches a distort off completely,
     * which a slotted tail needs.
     *
     * **Its law is the VOICE STRIP's, not the doors'** (phase 3 step 4, decision D2 option A,
     * 2026-09-25): the backend renders it through the same loop as the strip's `DistortionRenderer`
     * (`DistortionCore`): `shape(x * 10^(amount * 1.2))` with the drive applied INSIDE the oversampler,
     * then a DC blocker, and NO soft cap, so a hot shape (`gentle` is doubled) can leave `[-1, 1]`.
     * `ClassicStripParitySpec` proves it bit-identical to the strip. The `distort`/`shape` doors keep
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
     * asymmetric `floor` quantizer, `floor(x * 2^amount / 2) / (2^amount / 2)` clamped to `[-1, 1]`,
     * with a DC offset of about `-0.5 / halfLevels` (-0.5 at amount 1), which moves with a modulated
     * amount (phase 3 step 4, decision D1, 2026-09-25: FLOOR everywhere; it rounded before). Below an
     * amount of 1.0 it passes through.
     */
    @WireName("crush")
    data class Crush(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(8.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /** Sample-rate reduction effect. Holds samples to create aliasing artifacts. */
    @WireName("coarse")
    data class Coarse(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(4.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
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
        val center: IgnitorDsl = Constant(1000.0),
        val sweep: IgnitorDsl = Constant(1000.0),
        val floor: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); wet.collectParams(out)
            center.collectParams(out); sweep.collectParams(out); floor.collectParams(out)
        }
    }

    /**
     * Tremolo effect. Modulates amplitude with an LFO for a pulsing volume change.
     *
     * The LFO law is the voice strip's, ONE copy both use (`TremoloCore` in the backend): at every
     * [shape], [skew] and [phase] this node renders what the strip's tremolo renders, bit for bit, which
     * is what lets `classic()` rebuild it (phase 3 step 3b, 2026-09-25).
     *
     * @param rate LFO rate in Hz, read once per block. Default 5.0.
     * @param depth modulation depth, 0 to 1, read once per block; at or below 0 the tremolo passes the
     *   signal through and its clock keeps running. Default 0.5.
     * @param shape the LFO waveform as an INDEX into [LfoShapes.names] (`sine`, `triangle`, `square`,
     *   `sawtooth`, `ramp`). Both doors take a NAME (the script door also a number or a slot). **Read
     *   ONCE, at voice build**, leaf-only (a non-leaf is `sine` and is not built); the index rounds to
     *   the nearest position, and a non-finite, negative or past-the-end one is `sine`, as an unknown
     *   name is. Default: `sine` (index 0).
     * @param skew -1 to +1, 0 symmetric: positive keeps the LFO HIGH for more of the cycle, negative LOW,
     *   on every shape. A non-finite one reads as symmetric. Default 0.0. **Read once per block and held**
     *   for it: the skew warps the unwarped phase accumulator, which is never remapped (that keeps the
     *   LFO locked to the beat, ledger W2), so a MOVING skew steps the gain at every block edge. The
     *   step is about `depth * pi * deltaSkew / 2` mid-range and grows as `0.5 / duty` toward
     *   |skew| = 1; a full-range skew moving at 2 Hz on a 5 Hz sine at depth 1 steps by up to about 0.19,
     *   a zipper at the block rate (344 Hz at 128 frames). A constant skew, which is every slot
     *   `classic()` writes, never steps.
     * @param phase where in its own cycle the LFO starts, in cycles (0 to 1 is one cycle; 3.25 is a
     *   quarter). **Read ONCE, at voice build** (it seeds the clock), leaf-only (a non-leaf is 0 and
     *   is not built); a non-finite one is 0. Default 0.0.
     */
    @WireName("tremolo")
    data class Tremolo(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(5.0),
        val depth: IgnitorDsl = Constant(0.5),
        val shape: IgnitorDsl = Constant(LfoShapes.SINE_INDEX.toDouble()),
        val skew: IgnitorDsl = Constant(0.0),
        val phase: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
            shape.collectParams(out); skew.collectParams(out); phase.collectParams(out)
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
     *   Example: `[0, 4, 7, 11]` for a major 7th chord shimmer.
     * @param tone One-pole lowpass cutoff in Hz applied in the feedback path.
     *   Lower = darker, more ghostly tails. Typical: 2000–6000. Default: 4000.
     */
    @WireName("shimmer")
    data class Shimmer(
        val inner: IgnitorDsl,
        val wet: IgnitorDsl = Constant(0.5),
        val feedback: IgnitorDsl = Constant(0.5),
        val pitches: List<Double> = listOf(0.0, 7.0, 12.0),
        val tone: IgnitorDsl = Constant(4000.0),
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
     * Vibrato effect. Modulates pitch with a sinusoidal LFO.
     *
     * @param rate LFO frequency in Hz (default 5.0)
     * @param semitones modulation depth in SEMITONES (default 0.25 ≈ quarter-semitone wobble).
     *   Matches the sprudel `vibratoMod()` unit; pitch params are named by their unit.
     */
    @WireName("vibrato")
    data class Vibrato(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(5.0),
        val semitones: IgnitorDsl = Constant(0.25),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); semitones.collectParams(out)
        }
    }

    /**
     * Pitch acceleration. Continuously shifts pitch over the voice's duration using an
     * exponential curve, by [semitones] total: `accelerate(12)` ends one octave up.
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
     * The level rises from 0 to 1 over [attackSec], falls to [sustainLevel] over [decaySec],
     * holds, and from the gate's end falls from wherever it is back to 0 over [releaseSec]. The
     * pitch is `2^(semitones * level / 12)`, so a level of 0 is the note itself. The release does
     * NOT extend the voice's life: a pitch release longer than the amp envelope's is cut off with
     * the voice, and release `0` returns to the note at the gate's end.
     *
     * The level is the engine's one envelope law (`EnvelopeCore` in `audio_be`, decision D3), the
     * chain `adsr`'s: fractional attack and decay frame counts (`seconds * sampleRate` as a Double).
     * The voice strip's pitch envelope (sprudel's `penv`) is a host of the same law with the same
     * defaults (`constants/PitchEnvelopeDefaults.kt`), so the two sweep alike.
     *
     * @param semitones pitch shift at envelope peak, in SEMITONES (`2^(semitones·env/12)`):
     *   +12 sweeps from an octave up, -24 from two octaves down.
     * @param sustainLevel the held level, a share of [semitones]; 0 (the default) returns to the
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
        val attackSec: IgnitorDsl = Constant(PITCH_ENV_ATTACK_SEC),
        val decaySec: IgnitorDsl = Constant(PITCH_ENV_DECAY_SEC),
        val sustainLevel: IgnitorDsl = Constant(PITCH_ENV_SUSTAIN_LEVEL),
        val releaseSec: IgnitorDsl = Constant(PITCH_ENV_RELEASE_SEC),
        val attackCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        val decayCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
        val releaseCurve: IgnitorDsl = Constant(AdsrCurves.indexOf(MOD_ENV_CURVE)),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); semitones.collectParams(out); attackSec.collectParams(out)
            decaySec.collectParams(out); sustainLevel.collectParams(out); releaseSec.collectParams(out)
            attackCurve.collectParams(out); decayCurve.collectParams(out); releaseCurve.collectParams(out)
        }
    }

    /**
     * General-purpose pitch modulation. The [mod] Ignitor produces per-sample phase-deviation
     * values (0.0 = no change, positive = higher pitch, negative = lower). At runtime, the
     * build-time walker converts to ratio space (`value + 1.0`) and bubbles the mod to the
     * source oscillator.
     *
     * This is the general primitive underlying `.vibrato()`, `.accelerate()`, `.fm()`, and
     * `.pitchEnvelope()`. Use it for custom pitch modulation from any Ignitor source.
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
}

// ═════════════════════════════════════════════════════════════════════════════════
// Builder Extensions
// ═════════════════════════════════════════════════════════════════════════════════

// Arithmetic

/** Adds two ignitor signals together (sample-by-sample sum). */
operator fun IgnitorDsl.plus(other: IgnitorDsl) = IgnitorDsl.Plus(left = this, right = other)

/** Multiplies two ignitor signals together (ring modulation). */
operator fun IgnitorDsl.times(other: IgnitorDsl) = IgnitorDsl.Times(left = this, right = other)

/** Scales this signal by a modulatable [other] factor. Alias for [times]. */
fun IgnitorDsl.mul(other: IgnitorDsl) = IgnitorDsl.Times(left = this, right = other)

/**
 * Places the `pregain` slot here: how hard the pattern plays INTO whatever follows.
 *
 * Exactly `mul(IgnitorDsl.Slots.pregain)`, written out as a call to [mul] so the two spellings
 * can never drift apart: same node, same operand order, same content id. The script door's
 * `.pregain()` builds the same node from `OscSlot.pregain`, which is this same singleton.
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

/** Maps this signal from `[-1, 1]` to `[lo, hi]` per sample. */
fun IgnitorDsl.range(lo: IgnitorDsl, hi: IgnitorDsl) = IgnitorDsl.Range(inner = this, lo = lo, hi = hi)

/** Maps this signal from `[0, 1]` to `[-1, 1]` per sample. */
fun IgnitorDsl.bipolar() = IgnitorDsl.Bipolar(inner = this)

/** Maps this signal from `[-1, 1]` to `[0, 1]` per sample. */
fun IgnitorDsl.unipolar() = IgnitorDsl.Unipolar(inner = this)

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

// Frequency

/** Shifts pitch by the given number of [semitones]. */
fun IgnitorDsl.detune(semitones: Double) = IgnitorDsl.Detune(
    inner = this,
    semitones = IgnitorDsl.Constant(semitones),
)

// Filters

/**
 * The five cutoff-envelope knobs a filter DOOR hands to a filter node, after the compound fill.
 */
data class FilterEnvelopeKnobs(
    val env: IgnitorDsl,
    val attackSec: IgnitorDsl,
    val decaySec: IgnitorDsl,
    val sustainLevel: IgnitorDsl,
    val releaseSec: IgnitorDsl,
)

/**
 * **The compound fill for the filter cutoff envelope.** `null` means the call did NOT name that
 * knob; everything else is an explicit value and is never overwritten.
 *
 * The rule itself has ONE home and it is not here: `/dsl-design` section 4, "A compound door fills
 * per param, at the door, everywhere". What THIS door does under it: the filter envelope has no
 * NAME knob, so ANY of its five knobs names the stage, and a call that names one writes every
 * companion it left out from `audio_bridge/constants/FilterEnvelopeDefaults.kt`. `env` (the DEPTH)
 * is a companion like the rest, which is what makes `lowpass(800, decaySec = 0.3,
 * sustainLevel = 0.2)` an audible pluck instead of a silent no-op.
 *
 * It exists because sprudel already behaved that way and the two surfaces have to agree:
 * `SprudelVoiceData` builds a `FilterDef` envelope when ANY of `lpattack` / `lpdecay` /
 * `lpsustain` / `lprelease` / `lpenv` is present, and `FilterEnvDef.resolve()` then fills the
 * missing depth with [FILTER_ENV_DEPTH_SEMITONES]. `lpf(800, decay = 0.3, sustain = 0.2)` is a
 * pluck, so `lowpass(800, decaySec = 0.3, sustainLevel = 0.2)` has to be a pluck too, with the
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
    attackSec: IgnitorDsl?,
    decaySec: IgnitorDsl?,
    sustainLevel: IgnitorDsl?,
    releaseSec: IgnitorDsl?,
): FilterEnvelopeKnobs {
    val namesTheStage =
        env != null || attackSec != null || decaySec != null || sustainLevel != null || releaseSec != null

    return FilterEnvelopeKnobs(
        // The depth is a companion, not a gate: only a call that names NO knob at all leaves the
        // envelope off.
        env = env ?: if (namesTheStage) IgnitorDsl.Constant(FILTER_ENV_DEPTH_SEMITONES) else IgnitorDsl.Constant(0.0),
        attackSec = attackSec ?: IgnitorDsl.Constant(FILTER_ENV_ATTACK_SEC),
        decaySec = decaySec ?: IgnitorDsl.Constant(FILTER_ENV_DECAY_SEC),
        sustainLevel = sustainLevel ?: IgnitorDsl.Constant(FILTER_ENV_SUSTAIN_LEVEL),
        releaseSec = releaseSec ?: IgnitorDsl.Constant(FILTER_ENV_RELEASE_SEC),
    )
}

/** A scalar door argument as a knob: `null` (the call did not name it) stays null for the fill. */
private fun Double?.asKnob(): IgnitorDsl? = this?.let { IgnitorDsl.Constant(it) }

/** A scalar door's curve as its index knob: `null` (not named) stays null, the node's default. */
private fun AdsrCurve?.asCurveKnob(): IgnitorDsl? = this?.let { AdsrCurves.knob(it) }

/** A modulation envelope's unshaped curve: the index of `MOD_ENV_CURVE`, the node fields' default. */
private fun modEnvCurveKnob(): IgnitorDsl = AdsrCurves.knob(MOD_ENV_CURVE)

/**
 * Applies an SVF lowpass filter at [freq] with resonance [q].
 *
 * @param passes Cascade count (C5): run the 12 dB/oct stage that many times — `2` = 24 dB/oct,
 * `3` = 36. The per-stage q is STAGGERED (Butterworth ladder scaled by `q/0.707`), so at the
 * default q the cascade is -3 dB AT [freq] — `lowpass(800, passes = 2)` still means 800.
 * A resonant q compounds instead (`q = 1.0, passes = 2` is +3 dB at the cutoff). Coerced to
 * 1..[FILTER_MAX_PASSES]. The third slot on THIS flat Kotlin door and on sprudel's
 * `lpf(freq, q, passes)`; the script door takes it on its builder,
 * `lowpass(freq, q, x => x.passes(n))`, and refuses a third number. The node carries it as a knob
 * ([IgnitorDsl.Lowpass.passes]); a SLOT there is written through the node constructor, which is
 * what `classic()` does (a recorded two-door asymmetry, the `shape`/`distort` precedent of step 3b).
 * The five envelope knobs are a COMPOUND DOOR (`/dsl-design` section 4): `null` means the call
 * did not name that knob, and naming ANY of them fills the others from
 * `constants/FilterEnvelopeDefaults.kt`, `env` included. So `lowpass(800.0, decaySec = 0.3,
 * sustainLevel = 0.2)` is the pluck `lpf(800, decay = 0.3, sustain = 0.2)` is, and a call that
 * names none of them is the filter this door built before the knobs existed.
 *
 * @param env Cutoff-envelope DEPTH in semitones. Named alone it sweeps with the constant stage
 * times; left out of a call that names a stage knob it is filled with
 * [FILTER_ENV_DEPTH_SEMITONES]; left out of a call that names none of the five it is `0`, which
 * is the node's "no envelope". See [IgnitorDsl.Lowpass.env], which also says which envelope law
 * this is.
 * @param attackSec Cutoff-envelope attack in seconds. Inert when the envelope is off.
 * @param decaySec Cutoff-envelope decay in seconds. Needs a [sustainLevel] below 1 to be audible.
 * @param sustainLevel Cutoff-envelope sustain share of [env], 0 to 1.
 * @param releaseSec Cutoff-envelope release in seconds. It does NOT extend the voice's lifetime,
 * on either surface: a filter release longer than the amp envelope's is cut off with the voice.
 * @param attackCurve Curve of the envelope's attack, as its [AdsrCurves] index knob (a constant or a
 * slot); `null` is `MOD_ENV_CURVE` (see [IgnitorDsl.Lowpass.attackCurve]). The scalar overload takes
 * an [AdsrCurve]. The three curves are NOT companions of the fill: they shape an envelope, they do
 * not ask for one, so naming only a curve leaves the envelope off.
 * @param decayCurve Curve of the envelope's decay; see [attackCurve].
 * @param releaseCurve Curve of the envelope's release; see [attackCurve].
 * @param humanize Per-voice cutoff tolerance and drift lane, both scaled by `analog`
 * (see [IgnitorDsl.Lowpass.humanize]). `false` draws nothing and changes nothing.
 *
 * This flat signature is the ENGINE-LEVEL Kotlin door (the precedent of `eq`/`band`/`tap`,
 * `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`): `audio_bridge` cannot see the
 * script builders, so the script door `lowpass(freq, q, configure)` collects its builder's knobs
 * and calls THIS function after the lambda, which is where the compound fill runs, once, for both
 * doors. The one asymmetry: here the four stage times can be named one at a time, while the
 * builder's `adsr(attackSec, decaySec, sustainLevel, releaseSec)` names all four at once; every
 * builder call is therefore one call of this door.
 */
fun IgnitorDsl.lowpass(
    freq: IgnitorDsl,
    q: IgnitorDsl = IgnitorDsl.Constant(0.707),
    passes: Int = 1,
    analog: IgnitorDsl = IgnitorDsl.Constant(0.0),
    env: IgnitorDsl? = null,
    attackSec: IgnitorDsl? = null,
    decaySec: IgnitorDsl? = null,
    sustainLevel: IgnitorDsl? = null,
    releaseSec: IgnitorDsl? = null,
    attackCurve: IgnitorDsl? = null,
    decayCurve: IgnitorDsl? = null,
    releaseCurve: IgnitorDsl? = null,
    humanize: Boolean = false,
): IgnitorDsl.Lowpass {
    val envelope = fillFilterEnvelope(env, attackSec, decaySec, sustainLevel, releaseSec)

    return IgnitorDsl.Lowpass(
        inner = this,
        freq = freq,
        q = q,
        analog = analog,
        passes = IgnitorDsl.Constant(passes.toDouble()),
        env = envelope.env,
        attackSec = envelope.attackSec,
        decaySec = envelope.decaySec,
        sustainLevel = envelope.sustainLevel,
        releaseSec = envelope.releaseSec,
        attackCurve = attackCurve ?: modEnvCurveKnob(),
        decayCurve = decayCurve ?: modEnvCurveKnob(),
        releaseCurve = releaseCurve ?: modEnvCurveKnob(),
        humanize = humanize,
    )
}

/** Scalar convenience overload of [lowpass]. */
fun IgnitorDsl.lowpass(
    freq: Double,
    q: Double = 0.707,
    passes: Int = 1,
    analog: Double = 0.0,
    env: Double? = null,
    attackSec: Double? = null,
    decaySec: Double? = null,
    sustainLevel: Double? = null,
    releaseSec: Double? = null,
    attackCurve: AdsrCurve? = null,
    decayCurve: AdsrCurve? = null,
    releaseCurve: AdsrCurve? = null,
    humanize: Boolean = false,
): IgnitorDsl.Lowpass = lowpass(
    IgnitorDsl.Constant(freq), IgnitorDsl.Constant(q), passes, IgnitorDsl.Constant(analog),
    env.asKnob(), attackSec.asKnob(), decaySec.asKnob(), sustainLevel.asKnob(), releaseSec.asKnob(),
    attackCurve.asCurveKnob(), decayCurve.asCurveKnob(), releaseCurve.asCurveKnob(), humanize,
)

/**
 * Applies an SVF highpass filter at [freq] with resonance [q].
 *
 * @param passes Cascade count — see [lowpass].
 */
fun IgnitorDsl.highpass(
    freq: IgnitorDsl,
    q: IgnitorDsl = IgnitorDsl.Constant(0.707),
    passes: Int = 1,
    analog: IgnitorDsl = IgnitorDsl.Constant(0.0),
    env: IgnitorDsl? = null,
    attackSec: IgnitorDsl? = null,
    decaySec: IgnitorDsl? = null,
    sustainLevel: IgnitorDsl? = null,
    releaseSec: IgnitorDsl? = null,
    attackCurve: IgnitorDsl? = null,
    decayCurve: IgnitorDsl? = null,
    releaseCurve: IgnitorDsl? = null,
    humanize: Boolean = false,
): IgnitorDsl.Highpass {
    val envelope = fillFilterEnvelope(env, attackSec, decaySec, sustainLevel, releaseSec)

    return IgnitorDsl.Highpass(
        inner = this,
        freq = freq,
        q = q,
        analog = analog,
        passes = IgnitorDsl.Constant(passes.toDouble()),
        env = envelope.env,
        attackSec = envelope.attackSec,
        decaySec = envelope.decaySec,
        sustainLevel = envelope.sustainLevel,
        releaseSec = envelope.releaseSec,
        attackCurve = attackCurve ?: modEnvCurveKnob(),
        decayCurve = decayCurve ?: modEnvCurveKnob(),
        releaseCurve = releaseCurve ?: modEnvCurveKnob(),
        humanize = humanize,
    )
}

/** Scalar convenience overload of [highpass]. */
fun IgnitorDsl.highpass(
    freq: Double,
    q: Double = 0.707,
    passes: Int = 1,
    analog: Double = 0.0,
    env: Double? = null,
    attackSec: Double? = null,
    decaySec: Double? = null,
    sustainLevel: Double? = null,
    releaseSec: Double? = null,
    attackCurve: AdsrCurve? = null,
    decayCurve: AdsrCurve? = null,
    releaseCurve: AdsrCurve? = null,
    humanize: Boolean = false,
): IgnitorDsl.Highpass = highpass(
    IgnitorDsl.Constant(freq), IgnitorDsl.Constant(q), passes, IgnitorDsl.Constant(analog),
    env.asKnob(), attackSec.asKnob(), decaySec.asKnob(), sustainLevel.asKnob(), releaseSec.asKnob(),
    attackCurve.asCurveKnob(), decayCurve.asCurveKnob(), releaseCurve.asCurveKnob(), humanize,
)

/**
 * Controls the graph optimizer for the whole definition this node ends up in.
 *
 * `optimizer(0)` turns it OFF, so the tree renders exactly as authored — that is the useful
 * call, for A/B-ing a fusion by ear or as a hatch if one ever misbehaves. Any other value
 * leaves it on, and the marker then dissolves without trace (so it is not a fusion wall).
 * Note the default is 1: a bare `optimizer()` changes nothing. Same name and default as the
 * KlangScript stdlib `optimizer()`.
 */
fun IgnitorDsl.optimizer(on: Int = 1): IgnitorDsl = IgnitorDsl.OptimizerHint(inner = this, on = on)

/**
 * Starts (or continues) a fused equalizer — see [IgnitorDsl.Eq]. Idempotent. Same names and
 * defaults as the KlangScript stdlib `eq()` (dual-surface rule).
 *
 * ⚠ Idempotence composes badly with [tap] on a SHARED sound: if `base` already ends in an Eq,
 * `base.eq().tap(...)` appends into THAT Eq, so the tap reads base's PRE-Eq input rather than
 * its output — silently a different sound, with no error. Wrap deliberately when layering onto
 * someone else's Eq. Harmless for [band] (an append at the end is a plain serial append).
 */
fun IgnitorDsl.eq(): IgnitorDsl.Eq = when (this) {
    is IgnitorDsl.Eq -> this
    else -> IgnitorDsl.Eq(inner = this)
}

/**
 * Appends a Simper peaking bell ([db] dB at [freq], [q] = pre-gain bandwidth; 0 dB is
 * bit-transparent). Only available ON an Eq: `.eq()` is the entry point. Same names and
 * defaults as the KlangScript stdlib `band()` (dual-surface rule).
 *
 * The [q] width is INVARIANT in [db] while `q·A` stays unclamped (1.90 octaves at the default
 * q); below about -34 dB the clamp engages and deeper cuts narrow. Second positional arg is
 * [q], not gain: `band(1200.0, 6.0)` is a silent 0 dB band, `band(1200.0, db = 6.0)` is gain.
 * (In KlangScript that mixed form is rejected outright — there it is `band(freq = ..., db = ...)`.)
 *
 * All three params are resolved ONCE PER BLOCK and are coefficient-bearing, [db] included
 * (it moves the filter coefficients through `q·A`, while the resulting half-gain WIDTH stays
 * put — see above), so an LFO on [db] zippers exactly like an LFO on a cutoff. For a smooth
 * gain ride use a VCA, not a bell.
 */
fun IgnitorDsl.Eq.band(
    freq: IgnitorDsl,
    q: IgnitorDsl = IgnitorDsl.Constant(0.707),
    db: IgnitorDsl = IgnitorDsl.Constant(0.0),
): IgnitorDsl.Eq = copy(sections = sections + IgnitorDsl.EqSection.Bell(freq = freq, q = q, db = db))

/** Scalar convenience overload of [band]. */
fun IgnitorDsl.Eq.band(freq: Double, q: Double = 0.707, db: Double = 0.0): IgnitorDsl.Eq =
    band(IgnitorDsl.Constant(freq), IgnitorDsl.Constant(q), IgnitorDsl.Constant(db))

/**
 * Appends a PARALLEL band: a bandpass of the Eq INPUT, scaled by [gain] and added onto the
 * chain at this position. This is the fused form of
 * `signal.add(signal.bandpass(freq, q).mul(gain))` **when [gain] is Constant/Param-backed**.
 *
 * ⚠ An EXPRESSION-backed [gain] (an LFO) is NOT equivalent: the adapter resolves it once per
 * block, so a swept tap gain becomes a per-block staircase, where the chained `Times` node
 * multiplies per SAMPLE and stays smooth. Unlike [freq]/[q] — which snap per block on the
 * chained `SvfIgnitor` too, so those really are parity — [gain] is the one param where fusing
 * changes the sound. For a smoothly swept parallel boost, keep the chained form.
 *
 * Unlike [band], which cascades, taps SUM with the dry signal, so overlapping taps do not
 * multiply each other. [q] is the bandpass width and [gain] is a linear multiplier, NOT
 * decibels. (Rewriting the same tap as a [band]: the bell needs `db = 20·log10(1 + gain)`
 * and `q / sqrt(1 + gain)` — exact for one tap in isolation.)
 *
 * Since C2 of the filter unification the engine bandpass is UNITY-peak at fc, so [q] is a
 * pure WIDTH control here too: a tap's peak lift is `1 + gain` for ANY q (the old
 * constant-skirt engine made q level-bearing; that coupling is gone). To tighten a tap,
 * just raise [q]; the level stays put. The defaults (q 0.707, gain 1.0) give `1 + 1 = 2` —
 * a +6 dB lift, where a default [band] is transparent. Same names and defaults as the
 * KlangScript stdlib `tap()` (dual-surface rule).
 * See [IgnitorDsl.Eq] for the topology diagram.
 */
fun IgnitorDsl.Eq.tap(
    freq: IgnitorDsl,
    q: IgnitorDsl = IgnitorDsl.Constant(0.707),
    gain: IgnitorDsl = IgnitorDsl.Constant(1.0),
): IgnitorDsl.Eq = copy(sections = sections + IgnitorDsl.EqSection.RawTap(freq = freq, q = q, gain = gain))

/** Scalar convenience overload of [tap]. */
fun IgnitorDsl.Eq.tap(freq: Double, q: Double = 0.707, gain: Double = 1.0): IgnitorDsl.Eq =
    tap(IgnitorDsl.Constant(freq), IgnitorDsl.Constant(q), IgnitorDsl.Constant(gain))

/**
 * Applies a one-pole lowpass at [freq] Hz — 6 dB/oct, no resonance; musically a warmth/tone
 * control. ONE name on every door (formerly `onePoleLowpass`; the sprudel door's `warmth`
 * collapsed into this too).
 */
fun IgnitorDsl.onepole(freq: IgnitorDsl): IgnitorDsl.OnePoleLowpass = IgnitorDsl.OnePoleLowpass(
    inner = this,
    freq = freq,
)

/** Scalar convenience overload of [onepole]. */
fun IgnitorDsl.onepole(freq: Double): IgnitorDsl.OnePoleLowpass = onepole(IgnitorDsl.Constant(freq))

fun IgnitorDsl.bandpass(
    freq: IgnitorDsl,
    q: IgnitorDsl = IgnitorDsl.Constant(0.707),
    analog: IgnitorDsl = IgnitorDsl.Constant(0.0),
    env: IgnitorDsl? = null,
    attackSec: IgnitorDsl? = null,
    decaySec: IgnitorDsl? = null,
    sustainLevel: IgnitorDsl? = null,
    releaseSec: IgnitorDsl? = null,
    attackCurve: IgnitorDsl? = null,
    decayCurve: IgnitorDsl? = null,
    releaseCurve: IgnitorDsl? = null,
    humanize: Boolean = false,
): IgnitorDsl.Bandpass {
    val envelope = fillFilterEnvelope(env, attackSec, decaySec, sustainLevel, releaseSec)

    return IgnitorDsl.Bandpass(
        inner = this,
        freq = freq,
        q = q,
        analog = analog,
        env = envelope.env,
        attackSec = envelope.attackSec,
        decaySec = envelope.decaySec,
        sustainLevel = envelope.sustainLevel,
        releaseSec = envelope.releaseSec,
        attackCurve = attackCurve ?: modEnvCurveKnob(),
        decayCurve = decayCurve ?: modEnvCurveKnob(),
        releaseCurve = releaseCurve ?: modEnvCurveKnob(),
        humanize = humanize,
    )
}

/** Scalar convenience overload of [bandpass]. */
fun IgnitorDsl.bandpass(
    freq: Double,
    q: Double = 0.707,
    analog: Double = 0.0,
    env: Double? = null,
    attackSec: Double? = null,
    decaySec: Double? = null,
    sustainLevel: Double? = null,
    releaseSec: Double? = null,
    attackCurve: AdsrCurve? = null,
    decayCurve: AdsrCurve? = null,
    releaseCurve: AdsrCurve? = null,
    humanize: Boolean = false,
): IgnitorDsl.Bandpass = bandpass(
    IgnitorDsl.Constant(freq), IgnitorDsl.Constant(q), IgnitorDsl.Constant(analog),
    env.asKnob(), attackSec.asKnob(), decaySec.asKnob(), sustainLevel.asKnob(), releaseSec.asKnob(),
    attackCurve.asCurveKnob(), decayCurve.asCurveKnob(), releaseCurve.asCurveKnob(), humanize,
)

fun IgnitorDsl.notch(
    freq: IgnitorDsl,
    q: IgnitorDsl = IgnitorDsl.Constant(0.707),
    analog: IgnitorDsl = IgnitorDsl.Constant(0.0),
    env: IgnitorDsl? = null,
    attackSec: IgnitorDsl? = null,
    decaySec: IgnitorDsl? = null,
    sustainLevel: IgnitorDsl? = null,
    releaseSec: IgnitorDsl? = null,
    attackCurve: IgnitorDsl? = null,
    decayCurve: IgnitorDsl? = null,
    releaseCurve: IgnitorDsl? = null,
    humanize: Boolean = false,
): IgnitorDsl.Notch {
    val envelope = fillFilterEnvelope(env, attackSec, decaySec, sustainLevel, releaseSec)

    return IgnitorDsl.Notch(
        inner = this,
        freq = freq,
        q = q,
        analog = analog,
        env = envelope.env,
        attackSec = envelope.attackSec,
        decaySec = envelope.decaySec,
        sustainLevel = envelope.sustainLevel,
        releaseSec = envelope.releaseSec,
        attackCurve = attackCurve ?: modEnvCurveKnob(),
        decayCurve = decayCurve ?: modEnvCurveKnob(),
        releaseCurve = releaseCurve ?: modEnvCurveKnob(),
        humanize = humanize,
    )
}

/** Scalar convenience overload of [notch]. */
fun IgnitorDsl.notch(
    freq: Double,
    q: Double = 0.707,
    analog: Double = 0.0,
    env: Double? = null,
    attackSec: Double? = null,
    decaySec: Double? = null,
    sustainLevel: Double? = null,
    releaseSec: Double? = null,
    attackCurve: AdsrCurve? = null,
    decayCurve: AdsrCurve? = null,
    releaseCurve: AdsrCurve? = null,
    humanize: Boolean = false,
): IgnitorDsl.Notch = notch(
    IgnitorDsl.Constant(freq), IgnitorDsl.Constant(q), IgnitorDsl.Constant(analog),
    env.asKnob(), attackSec.asKnob(), decaySec.asKnob(), sustainLevel.asKnob(), releaseSec.asKnob(),
    attackCurve.asCurveKnob(), decayCurve.asCurveKnob(), releaseCurve.asCurveKnob(), humanize,
)

/** Pre-amplification: gain without a curve, see [IgnitorDsl.Drive]. */
fun IgnitorDsl.drive(amount: Double) =
    IgnitorDsl.Drive(this, IgnitorDsl.Constant(amount))

/**
 * Pure waveshaping without drive. See [IgnitorDsl.Shape] for the full list of supported [shape] values.
 *
 * Quick reference:
 *  - soft / gentle / softsat / cubic / exp / sineshaper — symmetric soft
 *  - hard / zerosquare / chebyshev / fold / linearfold — symmetric hard / wavefolding
 *  - diode / tube / asym / stompbox / rectify — asymmetric (even harmonics, DC offset)
 *
 * The node carries the shape as an INDEX and [oversample] as a knob; this door takes a name and a
 * whole factor. A SLOT in either position is written through the node (`IgnitorDsl.Shape(inner,
 * shape = IgnitorDsl.Param(...))`): a recorded two-door asymmetry, since the script door, which has no
 * node constructor, also accepts a number or a slot (phase 3 step 3b, 2026-09-25).
 */
fun IgnitorDsl.shape(shape: String = "soft", oversample: Int = 0) = IgnitorDsl.Shape(
    inner = this,
    shape = IgnitorDsl.Constant(DistortionShapes.indexOf(shape)),
    oversample = IgnitorDsl.Constant(oversample.toDouble()),
)

// Envelope

/**
 * Wraps this signal in an ADSR amplitude envelope.
 *
 * @param attackCurve curve of the attack, as its [AdsrCurves] index knob (a constant or a slot);
 *   `null` keeps the node's default, [AdsrCurve.Default] (exponential).
 * @param decayCurve curve of the decay; see [attackCurve].
 * @param releaseCurve curve of the release; see [attackCurve].
 * @param declickSeconds the de-click one-pole on the gain, in seconds; `null` keeps the node's
 *   default, the `declickSeconds` slot (0 = off). See [IgnitorDsl.Adsr.declickSeconds].
 *
 * This flat signature is the ENGINE-LEVEL Kotlin door, the filter doors' precedent: `audio_bridge`
 * cannot see the script builders, so the script door `adsr(a, d, s, r, e => e.curves(...).declick(...))`
 * collects its builder's knobs and calls THIS function. The builder's `declick` is `declickSeconds`
 * here, the node's name: the prefix drops inside a builder only (`/dsl-design` section 2). The
 * envelope's ON/OFF switch is not on either door ([IgnitorDsl.Adsr.on]).
 */
fun IgnitorDsl.adsr(
    attackSec: IgnitorDsl,
    decaySec: IgnitorDsl,
    sustainLevel: IgnitorDsl,
    releaseSec: IgnitorDsl,
    attackCurve: IgnitorDsl? = null,
    decayCurve: IgnitorDsl? = null,
    releaseCurve: IgnitorDsl? = null,
    declickSeconds: IgnitorDsl? = null,
): IgnitorDsl.Adsr {
    val defaults = IgnitorDsl.Adsr(inner = this)

    return defaults.copy(
        attackSec = attackSec,
        decaySec = decaySec,
        sustainLevel = sustainLevel,
        releaseSec = releaseSec,
        attackCurve = attackCurve ?: defaults.attackCurve,
        decayCurve = decayCurve ?: defaults.decayCurve,
        releaseCurve = releaseCurve ?: defaults.releaseCurve,
        declickSeconds = declickSeconds ?: defaults.declickSeconds,
    )
}

/** Scalar convenience overload of [adsr]: the curves as [AdsrCurve]s, `null` for the default. */
fun IgnitorDsl.adsr(
    attackSec: Double,
    decaySec: Double,
    sustainLevel: Double,
    releaseSec: Double,
    attackCurve: AdsrCurve? = null,
    decayCurve: AdsrCurve? = null,
    releaseCurve: AdsrCurve? = null,
    declickSeconds: Double? = null,
): IgnitorDsl.Adsr = adsr(
    IgnitorDsl.Constant(attackSec), IgnitorDsl.Constant(decaySec),
    IgnitorDsl.Constant(sustainLevel), IgnitorDsl.Constant(releaseSec),
    attackCurve.asCurveKnob(), decayCurve.asCurveKnob(), releaseCurve.asCurveKnob(), declickSeconds.asKnob(),
)

// FM

/** Applies FM synthesis to this carrier using the given [modulator], [ratio], and [depth]. */
fun IgnitorDsl.fm(
    modulator: IgnitorDsl,
    ratio: Double,
    depth: Double,
    envAttackSec: Double = 0.0,
    envDecaySec: Double = 0.0,
    envSustainLevel: Double = 1.0,
    envReleaseSec: Double = 0.0,
) = IgnitorDsl.Fm(
    carrier = this,
    modulator = modulator,
    ratio = IgnitorDsl.Constant(ratio),
    depth = IgnitorDsl.Constant(depth),
    envAttackSec = IgnitorDsl.Constant(envAttackSec),
    envDecaySec = IgnitorDsl.Constant(envDecaySec),
    envSustainLevel = IgnitorDsl.Constant(envSustainLevel),
    envReleaseSec = IgnitorDsl.Constant(envReleaseSec),
)

// Effects

/**
 * Applies waveshaping distortion with the given [amount] and waveshaper [shape].
 *
 * Equivalent to `this.drive(amount).shape(shape, oversample)`. See [IgnitorDsl.Shape] for the
 * full list of supported [shape] values.
 *
 * Quick reference:
 *  - soft / gentle / softsat / cubic / exp / sineshaper — symmetric soft
 *  - hard / zerosquare / chebyshev / fold / linearfold — symmetric hard / wavefolding
 *  - diode / tube / asym / stompbox / rectify — asymmetric (even harmonics, DC offset)
 *
 * A name and a whole factor, as on [shape]; a slot is written through the node (the same recorded
 * asymmetry).
 */
fun IgnitorDsl.distort(amount: Double, shape: String = "soft", oversample: Int = 0) =
    IgnitorDsl.Drive(inner = this, amount = IgnitorDsl.Constant(amount)).shape(shape, oversample)

/** Applies bit-crush quantization at the given bit [amount]. */
fun IgnitorDsl.crush(amount: Double) = IgnitorDsl.Crush(
    inner = this,
    amount = IgnitorDsl.Constant(amount),
)

/** Applies sample-rate reduction by the given [amount] factor. */
fun IgnitorDsl.coarse(amount: Double) = IgnitorDsl.Coarse(
    inner = this,
    amount = IgnitorDsl.Constant(amount),
)

/**
 * Applies a phaser effect: [wet] first, as on every door that has one, then the sweep [rate] in
 * Hz. Both are required, because a positional rate follows the wet. The dry floor is a field of
 * the node (`floor`, default 0.0) and a knob on the script builder only: `floor` is never a
 * method on a signal, where it would meet the round-down `floor()`.
 */
fun IgnitorDsl.phaser(wet: Double, rate: Double, center: Double = 1000.0, sweep: Double = 1000.0) = IgnitorDsl.Phaser(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    wet = IgnitorDsl.Constant(wet),
    center = IgnitorDsl.Constant(center),
    sweep = IgnitorDsl.Constant(sweep),
)

/**
 * Applies a tremolo (amplitude modulation) at the given LFO [rate] in Hz and [depth], with the LFO's
 * [shape] (a name from [LfoShapes], converted to its index), [skew] (-1 to +1) and start [phase] in
 * cycles. See [IgnitorDsl.Tremolo] for each knob.
 *
 * FLAT, where the script door is `tremolo(rate, depth, configure)` with `shape`, `skew` and `phase` on
 * a builder: a recorded two-door asymmetry, the filter doors' precedent (`audio_bridge` cannot see the
 * script builders; the flat door is a superset of the builder). A slot in any knob is written through
 * the node (phase 3 step 3b, 2026-09-25).
 */
fun IgnitorDsl.tremolo(
    rate: Double,
    depth: Double,
    shape: String = "sine",
    skew: Double = 0.0,
    phase: Double = 0.0,
) = IgnitorDsl.Tremolo(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    depth = IgnitorDsl.Constant(depth),
    shape = IgnitorDsl.Constant(LfoShapes.indexOf(shape)),
    skew = IgnitorDsl.Constant(skew),
    phase = IgnitorDsl.Constant(phase),
)

/**
 * Applies a granular shimmer (pitch-shift cloud with feedback). [wet] first, as on every door
 * that has one, default 0.5. [tone]: feedback-path LPF cutoff in Hz. [pitches]: semitone
 * transpositions. Same order as the script door. The dry floor is the node's `floor` field
 * (default 0.0), a knob on the script builder only; see [phaser].
 */
fun IgnitorDsl.shimmer(
    wet: Double = 0.5,
    feedback: Double = 0.5,
    tone: Double = 4000.0,
    pitches: List<Double> = listOf(0.0, 7.0, 12.0),
) = IgnitorDsl.Shimmer(
    inner = this,
    wet = IgnitorDsl.Constant(wet),
    feedback = IgnitorDsl.Constant(feedback),
    pitches = pitches,
    tone = IgnitorDsl.Constant(tone),
)

// Pitch modulation

/** Applies vibrato (pitch modulation) at the given LFO [rate], [semitones] deep. */
fun IgnitorDsl.vibrato(rate: Double, semitones: Double) = IgnitorDsl.Vibrato(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    semitones = IgnitorDsl.Constant(semitones),
)

/** Applies continuous pitch acceleration over the voice's duration. */
fun IgnitorDsl.accelerate(semitones: Double) = IgnitorDsl.Accelerate(
    inner = this,
    semitones = IgnitorDsl.Constant(semitones),
)

/**
 * Applies a custom pitch modulation from any Ignitor signal.
 *
 * The [mod] signal uses deviation space: 0.0 = no change, positive = higher, negative = lower.
 * At build time, the runtime converts to ratio space and bubbles the mod to the source oscillator.
 */
fun IgnitorDsl.pitchMod(mod: IgnitorDsl) = IgnitorDsl.PitchMod(inner = this, mod = mod)

// ═════════════════════════════════════════════════════════════════════════════════
// Discovery
// ═════════════════════════════════════════════════════════════════════════════════

/** Walks the DSL tree and collects all [IgnitorDsl.Param] leaf nodes. */
fun IgnitorDsl.getParamSlots(): List<IgnitorDsl.Param> {
    val result = mutableListOf<IgnitorDsl.Param>()
    collectParams(result)
    return result
}

