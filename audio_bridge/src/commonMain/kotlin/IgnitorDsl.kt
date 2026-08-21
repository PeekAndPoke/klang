/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K


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
     * Canonical open parameter slots that mirror sprudel's `withOscParam(name)` calls.
     *
     * Use these when defining a custom sound that should respond to sprudel modulation
     * (e.g. `note("c").analog(0.3)`). Each slot is a `Param(name, default)` singleton with
     * the same name + default value that sprudel addons expect.
     *
     * Builtin sounds (`IgnitorDefaults.kt`) wire these in automatically; custom sounds
     * opt in explicitly:
     * ```
     * let mypad = Osc.sine().analog(OscSlot.analog())
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

        val expK: IgnitorDsl = Param(name = "expK", default = ADSR_EXP_K)
        val octaves: IgnitorDsl = Param(name = "octaves", default = 1.0)
        val persistence: IgnitorDsl = Param(name = "persistence", default = 0.5)
        val pickPosition: IgnitorDsl = Param(name = "pickPosition", default = 0.5)
        val rate: IgnitorDsl = Param(name = "rate", default = 1.0)
        val spread: IgnitorDsl = Param(name = "spread", default = 0.2)
        val stiffness: IgnitorDsl = Param(name = "stiffness", default = 0.0)
        val tail: IgnitorDsl = Param(name = "tail", default = 1.0)
        val voices: IgnitorDsl = Param(name = "voices", default = 8.0)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Oscillator Primitives
    //
    // freq default: Freq = use the voice's note frequency (e.g. 440 Hz for A4).
    // Constant(n) = fixed frequency in Hz (e.g. Constant(5.0) for a 5 Hz LFO).
    // ═════════════════════════════════════════════════════════════════════════════

    /** Sine wave oscillator. */
    @WireName("sine")
    data class Sine(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Sawtooth wave oscillator (rising ramp). */
    @WireName("sawtooth")
    data class Sawtooth(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /** Analog flyback time in samples — lower = brighter/sharper reset, higher = softer (default 2.0). */
        val resetSamples: Double = 2.0,
        /** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps very high notes sane. */
        val shapeMax: Double = 0.5,
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
        val flankSamples: Double = 2.0,
        /** Rising-edge flank fraction of the plateau (0 = sharpest/min floor, 1 = full ramp). Default 0.0. */
        val riseFlank: Double = 0.0,
        /** Falling-edge flank fraction of the plateau (0 = sharpest/min floor, 1 = full ramp). Default 0.0. */
        val fallFlank: Double = 0.0,
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

    /** Ramp oscillator. Reverse sawtooth (ramp up, opposite slope of [Sawtooth]). */
    @WireName("ramp")
    data class Ramp(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Slots.analog,
        /** Analog flyback time in samples — lower = brighter/sharper reset, higher = softer (default 2.0). */
        val resetSamples: Double = 2.0,
        /** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps high notes sane. */
        val shapeMax: Double = 0.5,
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
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = 1.2,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = 0.1,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = 0.15,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = 0.4,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = 0.0,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64). */
        val drawTries: Double = 5.0,
        /** Accepted fundamental-coherence band K, lower edge (0 = cancelled, 1 = phase-aligned). */
        val kMin: Double = 0.30,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = 0.55,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = 256.0,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = 10.0,
        /** Pool entry selection: 0 = roundRobin (default), 1 = random. */
        val selection: Double = 0.0,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = 16.0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
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
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = 1.2,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = 0.1,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = 0.15,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = 0.4,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = 0.0,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64).
         *  Deeper than the saw's 5: the high band is rare per draw, and a missed band degrades to
         *  closest-candidate (= coherence maximization). */
        val drawTries: Double = 40.0,
        /** Band lower edge — the supersine's K IS the note (no other harmonics), so it sits high. */
        val kMin: Double = 0.50,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = 0.80,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = 256.0,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = 10.0,
        /** Pool entry selection: 0 = roundRobin (default), 1 = random. */
        val selection: Double = 0.0,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = 16.0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
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
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = 1.2,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = 0.1,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = 0.15,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = 0.4,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = 0.0,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64). */
        val drawTries: Double = 5.0,
        /** Accepted fundamental-coherence band K, lower edge (0 = cancelled, 1 = phase-aligned). */
        val kMin: Double = 0.30,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = 0.55,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = 256.0,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = 10.0,
        /** Pool entry selection: 0 = roundRobin (default), 1 = random. */
        val selection: Double = 0.0,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = 16.0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
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
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = 1.2,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = 0.1,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = 0.15,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = 0.4,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = 0.0,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64).
         *  Deeper than the saw's 5 — the higher band is rarer per draw. */
        val drawTries: Double = 16.0,
        /** Band lower edge — 1/k² harmonics put most of the note in the fundamental, so it sits high-ish. */
        val kMin: Double = 0.40,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = 0.65,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = 256.0,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = 10.0,
        /** Pool entry selection: 0 = roundRobin (default), 1 = random. */
        val selection: Double = 0.0,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = 16.0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
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
        /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward. */
        val spreadPower: Double = 1.2,
        /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
        val sideAtten: Double = 0.1,
        /** Per-voice random amplitude offset (±fraction); 0 = off. */
        val gainJitter: Double = 0.15,
        /** Fraction of [gainJitter] the on-pitch center voice gets (0 = stable center, 1 = jittered like sides). */
        val centerJitterScale: Double = 0.4,
        /** Banded start-phase selection (phase pool): 0 = off (bit-identical legacy random), 1 = on. */
        val phasePool: Double = 0.0,
        /** Candidate phase sets scored per note-on when [phasePool] is on (best-of-M; engine caps at 64). */
        val drawTries: Double = 5.0,
        /** Accepted fundamental-coherence band K, lower edge (0 = cancelled, 1 = phase-aligned). */
        val kMin: Double = 0.30,
        /** Accepted fundamental-coherence band K, upper edge. */
        val kMax: Double = 0.55,
        /** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
        val poolSize: Double = 256.0,
        /** Notes between fresh pool draws (random eviction); 0 = frozen pool. */
        val refreshEvery: Double = 10.0,
        /** Pool entry selection: 0 = roundRobin (default), 1 = random. */
        val selection: Double = 0.0,
        /** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
        val warmup: Double = 16.0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); analog.collectParams(out)
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); spread.collectParams(out); decay.collectParams(out); brightness.collectParams(
                out
            )
            pickPosition.collectParams(out); stiffness.collectParams(out); analog.collectParams(out)
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

    /** Divides the left signal by the right signal (per-sample division). */
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

    /** Negates the inner signal (flips polarity). */
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

    /** Biquad lowpass filter. Attenuates frequencies above the cutoff. */
    @WireName("lowpass")
    data class Lowpass(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(2000.0),
        val q: IgnitorDsl = Constant(0.707),
        /**
         * Analog character amount. `0` = clean linear filter (default — bit-identical
         * to pre-saturation behaviour). Higher values engage the OB-X-style state-dependent
         * damping in the SVF resonance feedback, compressing the resonance peak.
         * Typical range 0..10; values around 1–3 give Diva-default warmth.
         */
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out); analog.collectParams(out)
        }
    }

    /** Biquad highpass filter. Attenuates frequencies below the cutoff. */
    @WireName("highpass")
    data class Highpass(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(200.0),
        val q: IgnitorDsl = Constant(0.707),
        /** See [Lowpass.analog] — same semantics for the HP tap. */
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out); analog.collectParams(out)
        }
    }

    /** Simple one-pole lowpass filter. Lightweight with -6 dB/oct rolloff and no resonance. */
    @WireName("one-pole-lowpass")
    data class OnePoleLowpass(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(2000.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out)
        }
    }

    /** SVF bandpass filter. Passes frequencies near the cutoff, attenuates others. */
    @WireName("bandpass")
    data class Bandpass(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(1000.0),
        val q: IgnitorDsl = Constant(1.0),
        /**
         * Reserved for forward-compat — accepted but currently a no-op (BP saturation
         * not yet implemented; same pattern as the voice-strip `SvfBPF`).
         * See [Lowpass.analog] for semantics when implemented.
         */
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out); analog.collectParams(out)
        }
    }

    /** SVF notch (band-reject) filter. Removes frequencies near the cutoff, passes others. */
    @WireName("notch")
    data class Notch(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(1000.0),
        val q: IgnitorDsl = Constant(1.0),
        /** Reserved for forward-compat — accepted but currently a no-op (see [Bandpass.analog]). */
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out); analog.collectParams(out)
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
            val freqHz: IgnitorDsl = Constant(2000.0),
            val q: IgnitorDsl = Constant(0.707),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freqHz.collectParams(out); q.collectParams(out)
            }
        }

        /**
         * SVF high-pass section — the linear path of [IgnitorDsl.Highpass], as an Eq
         * section. Defaults match the chained node.
         */
        @WireName("eqHighpass")
        data class Highpass(
            val freqHz: IgnitorDsl = Constant(200.0),
            val q: IgnitorDsl = Constant(0.707),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freqHz.collectParams(out); q.collectParams(out)
            }
        }

        /** SVF band-pass section — [IgnitorDsl.Bandpass] as an Eq section; same defaults. */
        @WireName("eqBandpass")
        data class Bandpass(
            val freqHz: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(1.0),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freqHz.collectParams(out); q.collectParams(out)
            }
        }

        /** SVF notch section — [IgnitorDsl.Notch] as an Eq section; same defaults. */
        @WireName("eqNotch")
        data class Notch(
            val freqHz: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(1.0),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freqHz.collectParams(out); q.collectParams(out)
            }
        }

        /**
         * Simper peaking bell: [db] decibels of gain at [freqHz], [q] the PRE-GAIN bandwidth
         * (see `computeSvfBellCoeffs` for math + limits). 0 dB is bit-transparent; db is
         * COEFFICIENT-bearing (an LFO on it zippers like an LFO on cutoff, per-block snap).
         *
         * The [q] default is 0.707 and MUST stay equal to the `band()` default on both DSL
         * doors (parameter-parity: one bell, one omitted-field sound). It is deliberately
         * wider than the bandpass/notch default of 1.0 — there 1.0 is a resonance setting,
         * here it is a bell width, and musical bells sit wide (the tap-to-bell conversions
         * of the voicings in the real song land at 0.33 to 0.70).
         *
         * ⚠ That conversion (`A² = 1 + g·Q`, `q_bell = Q/A`) is exact for ONE tap in
         * isolation ONLY. N parallel taps are NOT N serial bells: bells multiply, taps sum,
         * and the cross term is what it leaves behind. Migrating a parallel boost
         * bank to bells is a NEW mix, not a conversion — use [RawTap]. See [IgnitorDsl.Eq].
         * (Worked example: two boosts of gain 1.7 @ 850 Hz/Q 0.707 and 5.0 @ 2500 Hz/Q 0.7 —
         * the guitar's default voicing — overshoot by +4.5 dB at 1200 Hz when run as serial
         * bells instead of parallel taps. Wider/hotter voicings overshoot more; the term
         * scales with `g₁·g₂`, so re-measure per patch rather than reusing this figure.)
         */
        @WireName("eqBell")
        data class Bell(
            val freqHz: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(0.707),
            val db: IgnitorDsl = Constant(0.0),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freqHz.collectParams(out); q.collectParams(out); db.collectParams(out)
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
            val freqHz: IgnitorDsl = Constant(1000.0),
            val q: IgnitorDsl = Constant(1.0),
            val gain: IgnitorDsl = Constant(1.0),
        ) : EqSection {
            override fun collectParams(out: MutableList<Param>) {
                freqHz.collectParams(out); q.collectParams(out); gain.collectParams(out)
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
     * 1200 Hz**, and more on wider or hotter voicings. Per-BAND the two forms convert
     * exactly (`A² = 1 + g·Q`, `q_bell = Q/A`); per-CHAIN they do not. Use [EqSection.RawTap]
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

    /** ADSR amplitude envelope. Shapes the inner signal's volume over the note lifecycle. */
    @WireName("adsr")
    data class Adsr(
        val inner: IgnitorDsl,
        val attackSec: IgnitorDsl = Constant(0.01),
        val decaySec: IgnitorDsl = Constant(0.1),
        val sustainLevel: IgnitorDsl = Constant(0.7),
        val releaseSec: IgnitorDsl = Constant(0.3),
        val attackCurve: AdsrCurve? = null,
        val decayCurve: AdsrCurve? = null,
        val releaseCurve: AdsrCurve? = null,
        /**
         * De-click smoothing on the final gain, in seconds (`Slots.declickSeconds`, default `0` = off —
         * this per-ignitor envelope is intentionally not de-clicked). `>0` runs a one-pole low-pass on
         * the gain that rounds the C1 corners at segment joins (attack→decay peak, gate-off, cutoff),
         * killing the low-note "plop" the same way the amp VCA does. `oscParam`-addressable / patternable.
         */
        val declickSeconds: IgnitorDsl = Slots.declickSeconds,
        /**
         * Curvature of [AdsrCurve.Exponential] segments (`Slots.expK`, defaults to [ADSR_EXP_K]).
         * Larger = steeper initial change (faster decay drop / sharper attack finish). `oscParam`-addressable.
         */
        val expK: IgnitorDsl = Slots.expK,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); attackSec.collectParams(out); decaySec.collectParams(out)
            sustainLevel.collectParams(out); releaseSec.collectParams(out)
            declickSeconds.collectParams(out); expK.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM Synthesis
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Frequency modulation synthesis. The modulator's output shifts the carrier's frequency
     * at audio rate, with an optional ADSR envelope controlling modulation depth over time.
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
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            carrier.collectParams(out); modulator.collectParams(out); ratio.collectParams(out); depth.collectParams(out)
            envAttackSec.collectParams(out); envDecaySec.collectParams(out); envSustainLevel.collectParams(out); envReleaseSec.collectParams(
                out
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Pre-amplification stage. Boosts signal level before clipping.
     * Types: "linear" (current gain curve).
     */
    @WireName("drive")
    data class Drive(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.5),
        val driveType: String = "linear",
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /**
     * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
     * Includes DC blocker for asymmetric shapes.
     *
     * Shapes:
     *  - **Symmetric soft:** "soft" (tanh), "gentle" (soft clip, 2× gain), "softsat"
     *    (algebraic, gentler), "cubic", "exp" (transistor), "sineshaper" (peak-at-unity fold).
     *  - **Symmetric hard / harsh:** "hard" (clip), "zerosquare" (→ square), "chebyshev"
     *    (3rd-harmonic), "fold" (sin wavefold), "linearfold" (triangle wavefold).
     *  - **Asymmetric (even harmonics, DC):** "diode", "tube" (shifted-tanh), "asym" (poly),
     *    "stompbox" (diode pedal), "rectify" (full-wave).
     */
    @WireName("clip")
    data class Clip(
        val inner: IgnitorDsl,
        val shape: String = "soft",
        val oversample: Int = 0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Legacy distortion node. Kept for backward compatibility with serialized trees.
     * New code should use [Drive] + [Clip] instead. The builder extension [IgnitorDsl.distort]
     * creates a Clip(Drive(...)) chain.
     */
    @WireName("distort")
    data class Distort(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.5),
        val shape: String = "soft",
        val oversample: Int = 0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /** Bit-crush effect. Reduces amplitude resolution to create quantization noise. */
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
     * @param blend Crossfade between dry and wet. 0.0 = 100% dry (bypass), 1.0 = 100% wet (effect only).
     *   Formula: `out = dry · (1 − blend) + wet · blend`. Default: 0.5 (equal mix).
     */
    @WireName("phaser")
    data class Phaser(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(0.5),
        val blend: IgnitorDsl = Constant(0.5),
        val center: IgnitorDsl = Constant(1000.0),
        val sweep: IgnitorDsl = Constant(1000.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); blend.collectParams(out)
            center.collectParams(out); sweep.collectParams(out)
        }
    }

    /** Tremolo effect. Modulates amplitude with an LFO for a pulsing volume change. */
    @WireName("tremolo")
    data class Tremolo(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(5.0),
        val depth: IgnitorDsl = Constant(0.5),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
        }
    }

    /**
     * Shimmer effect. Granular pitch-shift cloud with feedback — Aetherizer-style.
     *
     * Chops the input into short overlapping grains, replays them at the pitch intervals
     * specified by [pitches] (in semitones), and feeds the wet output back into the grain buffer
     * through a one-pole lowpass at [tone] Hz.
     *
     * @param blend Crossfade between dry and wet. 0.0 = 100% dry (bypass), 1.0 = 100% wet (effect only).
     *   Formula: `out = dry · (1 − blend) + wet · blend`. Default: 0.5 (equal mix).
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
        val blend: IgnitorDsl = Constant(0.5),
        val feedback: IgnitorDsl = Constant(0.5),
        val pitches: List<Double> = listOf(0.0, 7.0, 12.0),
        val tone: IgnitorDsl = Constant(4000.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); blend.collectParams(out); feedback.collectParams(out); tone.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pitch Modulation
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Vibrato effect. Modulates pitch with a sinusoidal LFO.
     *
     * @param rate LFO frequency in Hz (default 5.0)
     * @param depth modulation depth in semitones (default 0.25 ≈ quarter-semitone wobble).
     *   Matches the sprudel `vibratoMod()` unit: both specify depth in semitones.
     */
    @WireName("vibrato")
    data class Vibrato(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(5.0),
        val depth: IgnitorDsl = Constant(0.25),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
        }
    }

    /** Pitch acceleration. Continuously shifts pitch over the voice's duration using an exponential curve. */
    @WireName("accelerate")
    data class Accelerate(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /**
     * Pitch envelope. Applies an attack-decay-release envelope to pitch, useful for
     * kick drum sweeps, laser effects, and other transient pitch gestures.
     */
    @WireName("pitch-envelope")
    data class PitchEnvelope(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.0),
        val attackSec: IgnitorDsl = Constant(0.01),
        val decaySec: IgnitorDsl = Constant(0.1),
        val releaseSec: IgnitorDsl = Constant(0.0),
        val curve: IgnitorDsl = Constant(0.0),
        val anchor: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out); attackSec.collectParams(out)
            decaySec.collectParams(out); releaseSec.collectParams(out); curve.collectParams(out); anchor.collectParams(out)
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

/** Per-sample minimum of this signal and [other]. */
fun IgnitorDsl.min(other: IgnitorDsl) = IgnitorDsl.Min(left = this, right = other)

/** Per-sample maximum of this signal and [other]. */
fun IgnitorDsl.max(other: IgnitorDsl) = IgnitorDsl.Max(left = this, right = other)

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

/** Applies a biquad lowpass filter at [cutoffHz] with resonance [q]. */
fun IgnitorDsl.lowpass(cutoffHz: Double, q: Double = 0.707) = IgnitorDsl.Lowpass(
    inner = this,
    cutoffHz = IgnitorDsl.Constant(cutoffHz),
    q = IgnitorDsl.Constant(q),
)

/** Applies a biquad highpass filter at [cutoffHz] with resonance [q]. */
fun IgnitorDsl.highpass(cutoffHz: Double, q: Double = 0.707) = IgnitorDsl.Highpass(
    inner = this,
    cutoffHz = IgnitorDsl.Constant(cutoffHz),
    q = IgnitorDsl.Constant(q),
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
): IgnitorDsl.Eq = copy(sections = sections + IgnitorDsl.EqSection.Bell(freqHz = freq, q = q, db = db))

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
 * multiply each other. [q] is the plain bandpass Q and [gain] is a linear multiplier, NOT
 * decibels, so the values from a hand-built tap chain transfer UNCHANGED. (Rewriting the same
 * tap as a [band] does not: a tap's audible bump is wider than the bandpass inside it, so the
 * bell needs `db = 20·log10(1 + gain·q)` and a WIDER `q / sqrt(1 + gain·q)`.)
 *
 * ⚠ [q] is LEVEL-BEARING here, unlike on [band]: the engine bandpass is constant-skirt (peak
 * gain = its own Q), so a tap's peak lift is `1 + gain·q`. Raising [q] therefore NARROWS the
 * band and LIFTS it at once (gain 1.0: q 0.5 → +3.5 dB over ~3.0 oct, q 4.0 → +14.0 dB over
 * ~0.8 oct); to tighten a tap at constant level, lower [gain] as you raise [q]. [band]'s peak
 * is `A²` for any [q]. The defaults (q 1.0, gain 1.0) give `1 + 1·1 = 2` — a +6 dB lift, where
 * a default [band] is transparent. Same names and defaults as the KlangScript stdlib `tap()` (dual-surface rule).
 * See [IgnitorDsl.Eq] for the topology diagram.
 */
fun IgnitorDsl.Eq.tap(
    freq: IgnitorDsl,
    q: IgnitorDsl = IgnitorDsl.Constant(1.0),
    gain: IgnitorDsl = IgnitorDsl.Constant(1.0),
): IgnitorDsl.Eq = copy(sections = sections + IgnitorDsl.EqSection.RawTap(freqHz = freq, q = q, gain = gain))

/** Scalar convenience overload of [tap]. */
fun IgnitorDsl.Eq.tap(freq: Double, q: Double = 1.0, gain: Double = 1.0): IgnitorDsl.Eq =
    tap(IgnitorDsl.Constant(freq), IgnitorDsl.Constant(q), IgnitorDsl.Constant(gain))

/** Applies a lightweight one-pole lowpass filter at [cutoffHz]. */
fun IgnitorDsl.onePoleLowpass(cutoffHz: Double) = IgnitorDsl.OnePoleLowpass(
    inner = this,
    cutoffHz = IgnitorDsl.Constant(cutoffHz),
)

fun IgnitorDsl.bandpass(cutoffHz: Double, q: Double = 1.0) = IgnitorDsl.Bandpass(
    this, IgnitorDsl.Constant(cutoffHz), IgnitorDsl.Constant(q),
)

fun IgnitorDsl.notch(cutoffHz: Double, q: Double = 1.0) = IgnitorDsl.Notch(
    this, IgnitorDsl.Constant(cutoffHz), IgnitorDsl.Constant(q),
)

fun IgnitorDsl.drive(amount: Double, driveType: String = "linear") =
    IgnitorDsl.Drive(this, IgnitorDsl.Constant(amount), driveType)

/**
 * Pure waveshaping without drive. See [IgnitorDsl.Clip] for the full list of supported [shape] values.
 *
 * Quick reference:
 *  - soft / gentle / softsat / cubic / exp / sineshaper — symmetric soft
 *  - hard / zerosquare / chebyshev / fold / linearfold — symmetric hard / wavefolding
 *  - diode / tube / asym / stompbox / rectify — asymmetric (even harmonics, DC offset)
 */
fun IgnitorDsl.clip(shape: String = "soft", oversample: Int = 0) = IgnitorDsl.Clip(this, shape, oversample)

// Envelope

/** Wraps this signal in an ADSR amplitude envelope. */
fun IgnitorDsl.adsr(attackSec: Double, decaySec: Double, sustainLevel: Double, releaseSec: Double) = IgnitorDsl.Adsr(
    inner = this,
    attackSec = IgnitorDsl.Constant(attackSec),
    decaySec = IgnitorDsl.Constant(decaySec),
    sustainLevel = IgnitorDsl.Constant(sustainLevel),
    releaseSec = IgnitorDsl.Constant(releaseSec),
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
 * Applies waveshaping distortion with the given [amount] and clipping [shape].
 *
 * Equivalent to `this.drive(amount).clip(shape, oversample)`. See [IgnitorDsl.Clip] for the
 * full list of supported [shape] values.
 *
 * Quick reference:
 *  - soft / gentle / softsat / cubic / exp / sineshaper — symmetric soft
 *  - hard / zerosquare / chebyshev / fold / linearfold — symmetric hard / wavefolding
 *  - diode / tube / asym / stompbox / rectify — asymmetric (even harmonics, DC offset)
 */
fun IgnitorDsl.distort(amount: Double, shape: String = "soft", oversample: Int = 0) =
    IgnitorDsl.Clip(inner = IgnitorDsl.Drive(inner = this, amount = IgnitorDsl.Constant(amount)), shape = shape, oversample = oversample)

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

/** Applies a phaser effect. [blend]: 0.0 = dry only, 1.0 = wet only (crossfade). */
fun IgnitorDsl.phaser(rate: Double, blend: Double = 0.5, center: Double = 1000.0, sweep: Double = 1000.0) = IgnitorDsl.Phaser(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    blend = IgnitorDsl.Constant(blend),
    center = IgnitorDsl.Constant(center),
    sweep = IgnitorDsl.Constant(sweep),
)

/** Applies a tremolo (amplitude modulation) at the given LFO [rate] and [depth]. */
fun IgnitorDsl.tremolo(rate: Double, depth: Double) = IgnitorDsl.Tremolo(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    depth = IgnitorDsl.Constant(depth),
)

/**
 * Applies a granular shimmer (pitch-shift cloud with feedback).
 * [blend]: 0.0 = dry only, 1.0 = wet only (crossfade). [pitches]: semitone transpositions.
 * [tone]: feedback-path LPF cutoff in Hz.
 */
fun IgnitorDsl.shimmer(
    blend: Double = 0.5,
    feedback: Double = 0.5,
    pitches: List<Double> = listOf(0.0, 7.0, 12.0),
    tone: Double = 4000.0,
) = IgnitorDsl.Shimmer(
    inner = this,
    blend = IgnitorDsl.Constant(blend),
    feedback = IgnitorDsl.Constant(feedback),
    pitches = pitches,
    tone = IgnitorDsl.Constant(tone),
)

// Pitch modulation

/** Applies vibrato (pitch modulation) at the given LFO [rate] and [depth]. */
fun IgnitorDsl.vibrato(rate: Double, depth: Double) = IgnitorDsl.Vibrato(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    depth = IgnitorDsl.Constant(depth),
)

/** Applies continuous pitch acceleration over the voice's duration. */
fun IgnitorDsl.accelerate(amount: Double) = IgnitorDsl.Accelerate(
    inner = this,
    amount = IgnitorDsl.Constant(amount),
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

/**
 * Walks the DSL tree and returns the maximum ADSR release time (in seconds)
 * found in any [IgnitorDsl.Adsr] node.
 *
 * Returns 0.0 if no Adsr nodes exist in the tree.
 * Only reads [IgnitorDsl.Constant] values and [IgnitorDsl.Param] defaults —
 * dynamically modulated release times use the static default.
 */
fun IgnitorDsl.maxReleaseSec(): Double = when (this) {
    // Adsr: extract this node's release and recurse into inner
    is IgnitorDsl.Adsr -> {
        val thisRelease = when (releaseSec) {
            is IgnitorDsl.Constant -> releaseSec.value
            is IgnitorDsl.Param -> releaseSec.default
            else -> 0.3
        }
        maxOf(thisRelease, inner.maxReleaseSec())
    }
    // Wrapper nodes with `inner`
    is IgnitorDsl.Lowpass -> inner.maxReleaseSec()
    is IgnitorDsl.Highpass -> inner.maxReleaseSec()
    is IgnitorDsl.Bandpass -> inner.maxReleaseSec()
    is IgnitorDsl.Notch -> inner.maxReleaseSec()
    is IgnitorDsl.Eq -> inner.maxReleaseSec()
    is IgnitorDsl.OnePoleLowpass -> inner.maxReleaseSec()
    is IgnitorDsl.Distort -> inner.maxReleaseSec()
    is IgnitorDsl.Drive -> inner.maxReleaseSec()
    is IgnitorDsl.Clip -> inner.maxReleaseSec()
    is IgnitorDsl.Crush -> inner.maxReleaseSec()
    is IgnitorDsl.Coarse -> inner.maxReleaseSec()
    is IgnitorDsl.Phaser -> inner.maxReleaseSec()
    is IgnitorDsl.Tremolo -> inner.maxReleaseSec()
    is IgnitorDsl.Shimmer -> inner.maxReleaseSec()
    is IgnitorDsl.PitchMod -> inner.maxReleaseSec()
    is IgnitorDsl.Vibrato -> inner.maxReleaseSec()
    is IgnitorDsl.Accelerate -> inner.maxReleaseSec()
    is IgnitorDsl.PitchEnvelope -> inner.maxReleaseSec()
    is IgnitorDsl.Detune -> inner.maxReleaseSec()
    // Binary nodes
    is IgnitorDsl.Plus -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Times -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Div -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Minus -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Pow -> maxOf(base.maxReleaseSec(), exp.maxReleaseSec())
    is IgnitorDsl.Min -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Max -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Neg -> inner.maxReleaseSec()
    is IgnitorDsl.Abs -> inner.maxReleaseSec()
    is IgnitorDsl.Clamp -> maxOf(inner.maxReleaseSec(), lo.maxReleaseSec(), hi.maxReleaseSec())
    is IgnitorDsl.Exp -> inner.maxReleaseSec()
    is IgnitorDsl.Log -> inner.maxReleaseSec()
    is IgnitorDsl.Sqrt -> inner.maxReleaseSec()
    is IgnitorDsl.Sign -> inner.maxReleaseSec()
    is IgnitorDsl.Tanh -> inner.maxReleaseSec()
    is IgnitorDsl.Lerp -> maxOf(left.maxReleaseSec(), right.maxReleaseSec(), t.maxReleaseSec())
    is IgnitorDsl.Range -> maxOf(inner.maxReleaseSec(), lo.maxReleaseSec(), hi.maxReleaseSec())
    is IgnitorDsl.Bipolar -> inner.maxReleaseSec()
    is IgnitorDsl.Unipolar -> inner.maxReleaseSec()
    is IgnitorDsl.Floor -> inner.maxReleaseSec()
    is IgnitorDsl.Ceil -> inner.maxReleaseSec()
    is IgnitorDsl.Round -> inner.maxReleaseSec()
    is IgnitorDsl.Frac -> inner.maxReleaseSec()
    is IgnitorDsl.Mod -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Recip -> inner.maxReleaseSec()
    is IgnitorDsl.Sq -> inner.maxReleaseSec()
    is IgnitorDsl.Select -> maxOf(cond.maxReleaseSec(), whenTrue.maxReleaseSec(), whenFalse.maxReleaseSec())
    is IgnitorDsl.Fm -> maxOf(carrier.maxReleaseSec(), modulator.maxReleaseSec())
    is IgnitorDsl.OptimizerHint -> inner.maxReleaseSec()
    // Variants: voice lifetime must cover whichever child gets picked, so take the max.
    is IgnitorDsl.Variants -> children.maxOfOrNull { it.maxReleaseSec() } ?: 0.0
    // Leaf nodes — no release info
    is IgnitorDsl.Freq -> 0.0
    is IgnitorDsl.Param -> 0.0
    is IgnitorDsl.Constant -> 0.0
    is IgnitorDsl.Silence -> 0.0
    is IgnitorDsl.WhiteNoise -> 0.0
    is IgnitorDsl.BrownNoise -> 0.0
    is IgnitorDsl.PinkNoise -> 0.0
    is IgnitorDsl.PerlinNoise -> 0.0
    is IgnitorDsl.BerlinNoise -> 0.0
    is IgnitorDsl.Dust -> 0.0
    is IgnitorDsl.Crackle -> 0.0
    is IgnitorDsl.Sine -> 0.0
    is IgnitorDsl.Sawtooth -> 0.0
    is IgnitorDsl.Square -> 0.0
    is IgnitorDsl.Triangle -> 0.0
    is IgnitorDsl.Ramp -> 0.0
    is IgnitorDsl.Zawtooth -> 0.0
    is IgnitorDsl.Zamp -> 0.0
    is IgnitorDsl.Impulse -> 0.0
    is IgnitorDsl.Pulze -> 0.0
    is IgnitorDsl.RawPulze -> 0.0
    is IgnitorDsl.SuperSaw -> 0.0
    is IgnitorDsl.SuperSine -> 0.0
    is IgnitorDsl.SuperSquare -> 0.0
    is IgnitorDsl.SuperTri -> 0.0
    is IgnitorDsl.SuperRamp -> 0.0
    is IgnitorDsl.Pluck -> 0.0
    is IgnitorDsl.SuperPluck -> 0.0
}
