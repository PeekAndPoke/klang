# New Oscillator / SignalGen Implementations

Possible new oscillators and signal generators for the Klang audio engine, sorted by relevance
(bang-for-buck: unique sound character, implementation simplicity, CPU cost).

## Already Implemented

| Oscillator  | Aliases    | Type            | Anti-aliasing |
|-------------|------------|-----------------|---------------|
| sine        | sin        | Basic waveform  | N/A           |
| sawtooth    | saw        | Basic waveform  | PolyBLEP      |
| ramp        |            | Reverse saw     | PolyBLEP      |
| square      | sqr, pulse | Basic waveform  | PolyBLEP      |
| triangle    | tri        | Basic waveform  | N/A           |
| zawtooth    | zaw        | Naive saw       | None          |
| pulze       |            | Variable duty   | None          |
| impulse     |            | Click per cycle | N/A           |
| whitenoise  | white      | Noise           | N/A           |
| brownnoise  | brown      | Noise           | N/A           |
| pinknoise   | pink       | Noise           | N/A           |
| dust        |            | Sparse impulses | N/A           |
| crackle     |            | Sparse impulses | N/A           |
| supersaw    |            | Unison detuned  | PolyBLEP      |
| supersine   |            | Unison detuned  | N/A           |
| supersquare | supersqr   | Unison detuned  | PolyBLEP      |
| supertri    |            | Unison detuned  | N/A           |
| superramp   |            | Unison detuned  | PolyBLEP      |
| silence     |            | Utility         | N/A           |

All oscillators support the `analog` parameter (Perlin noise drift) via `oscParams`.

Compositions: `sgpad`, `sgbell`, `sgbuzz`.

---

## Candidates for Implementation

### 1. Karplus-Strong — Plucked String / Drum Synthesis

**Priority: Highest** | Complexity: Simple (~30 lines) | CPU: Very low | Data: None

Short noise burst fed into a delay line with averaging lowpass feedback. Produces realistic
plucked string sounds. Drum variant uses random sign-flip in the feedback loop.

- **Original paper:** Karplus & Strong, "Digital Synthesis of Plucked-String and Drum Timbres" (1983), Computer Music
  Journal
- **Extended KS:** Jaffe & Smith — adds fractional delay (allpass interpolation for accurate tuning), string stiffness,
  decay stretching, pick position modeling
- **Why highest priority:** Completely unique character unlike anything we have. Dead simple. Very cheap.

Possible names: `pluck`, `string`, `ks`

Parameters: `decay` (feedback), `brightness` (lowpass cutoff), `pickPosition`, `stiffness`

References:

- https://users.soe.ucsc.edu/~karplus/papers/digitar.pdf
- http://users.spa.aalto.fi/vpv/publications/cmj98.pdf

---

### 2. Phase Distortion — Casio CZ-style Warped Sine

**Priority: High** | Complexity: Simple | CPU: Very low | Data: None (or tiny sine table)

Instead of reading a sine table linearly, the phase accumulator is warped by a transfer function.
Different transfer functions produce saw, square, pulse, and unique **resonant waveforms** that
simulate filter resonance sweeps — a distinctive sound no other synthesis method easily produces.

- The DCW (Digital Controlled Wave) parameter morphs from pure sine to full waveshape.
- **Alias-free** — output is always a continuous warped sine, no discontinuities.
- 8+ distinct waveform shapes from a single oscillator with shape selection.

Possible names: `pd`, `cz`, `phasedist`

Parameters: `shape` (saw/square/pulse/resonant1/resonant2/resonant3), `dcw` (morph 0..1)

References:

- https://electricdruid.net/phase-distortion-synthesis/
- https://en.wikipedia.org/wiki/Phase_distortion_synthesis

---

### 3. Waveshaping — Chebyshev Polynomial Harmonics

**Priority: High** | Complexity: Simple | CPU: Very low | Data: None

Sine wave passed through a nonlinear transfer function. Chebyshev polynomials are especially
elegant: the nth Chebyshev polynomial applied to cos(x) produces exactly cos(n*x) — the nth
harmonic. By mixing Chebyshev polynomials, you get precise additive-synthesis-like control over
the harmonic spectrum, but computed as a simple polynomial evaluation per sample.

- Can create everything from mellow sine-like tones to bright, harmonically rich timbres.
- No aliasing issues when input stays in [-1, 1].

Possible names: `cheby`, `waveshape`

Parameters: `harmonics` (which Chebyshev orders to mix), `drive` (input amplitude)

References:

- Arfib, "Digital Synthesis of Complex Spectra by Means of Multiplication of Non-linear Distorted Sine Waves" (1978)
- Le Brun, "A Derivation of the Spectrum of FM with a Complex Modulating Wave" (1979)

---

### 4. Hard Sync Oscillator

**Priority: Medium-High** | Complexity: Medium | CPU: Low | Data: None

A slave oscillator resets its phase every time a master oscillator completes a cycle.
Sweeping the slave frequency produces the classic aggressive "sync sweep" lead sound
(heard in countless synth patches). The main challenge is anti-aliasing the discontinuity
at the sync reset point.

- Best approach: **PolyBLEP correction** at sync points (we already have PolyBLEP infrastructure).
- Master can be hidden (just a phase counter), slave is any waveform (saw, square, etc.)

Possible names: `sync`, `hardsync`

Parameters: `syncRatio` (slave/master frequency ratio), `slaveShape` (saw/square/tri)

References:

- Brandt, "Hard Sync Without Aliasing" (2001, ICMC): http://www.cs.cmu.edu/~eli/papers/icmc01-hardsync.pdf
- DAFx 2022, "Antialiasing for Sine Hard Sync": https://dafx.de/paper-archive/2022/papers/DAFx20in22_paper_3.pdf

---

### 5. Formant / Vocal Oscillator

**Priority: Medium** | Complexity: Medium | CPU: Low | Data: Tiny vowel table

Parallel resonator bank: a glottal pulse (impulse train or filtered noise) drives 3-5 biquad
filters tuned to vowel formant frequencies. Can morph between vowels (a, e, i, o, u).

- Reuses existing `AudioFilter` biquad infrastructure.
- Vowel formant data is just a small lookup table (~5 vowels x 3-5 frequency/bandwidth pairs).

Possible names: `vocal`, `formant`, `vowel`

Parameters: `vowel` (a/e/i/o/u or continuous 0..1 morph), `pitch` (glottal rate)

References:

- Smith, Formant Synthesis Models: https://ccrma.stanford.edu/~jos/pasp/Formant_Synthesis_Models.html

---

### 6. Waveguide Reed / Clarinet

**Priority: Medium** | Complexity: Medium | CPU: Low-Medium | Data: None

Digital waveguide model: delay line simulating the bore of a wind instrument, coupled with a
nonlinear reed function (cubic polynomial or tanh). Produces distinctive clarinet, sax, and
oboe-like tones that are impossible to achieve with basic waveforms.

- Builds on the same delay line concept as Karplus-Strong.
- The reed nonlinearity is what gives it the characteristic "buzzy" wind instrument quality.

Possible names: `reed`, `clarinet`, `wind`

Parameters: `pressure` (blowing pressure), `stiffness` (reed stiffness), `bore` (tube length)

References:

- Smith, "Physical Modeling Using Digital Waveguides" (1992): https://ccrma.stanford.edu/~jos/pmudw/pmudw.pdf
- Smith, Waveguide Reed
  Implementation: https://ccrma.stanford.edu/~jos/pasp/Digital_Waveguide_Single_Reed_Implementation.html
- Full free textbook: https://www.dsprelated.com/freebooks/pasp/

---

### 7. Additive Oscillator — Sum of Harmonics

**Priority: Low-Medium** | Complexity: Simple | CPU: Moderate (N sine calls) | Data: None

Sum of N sine oscillators at harmonic intervals with individual amplitudes. Already partially
achievable via the SignalGen composition DSL (`Sine() + Sine().detune(12.0)` etc.), but a
dedicated implementation could be more efficient and offer preset spectra (organ, bell, etc.).

Possible names: `additive`, `organ`

Parameters: `harmonics` (list of amplitude weights), `preset` (organ/bell/choir/etc.)

---

### 8. Wavetable Oscillator

**Priority: Low** | Complexity: High | CPU: Low per sample | Data: Requires wavetable files

Reads through stored single-cycle waveforms and morphs between them (Serum-style). Extremely
versatile but requires a wavetable format, loading infrastructure, and anti-aliasing via
mip-mapping or oversampling.

- Large undertaking compared to the other candidates.
- Could be very powerful if combined with sample loading infrastructure that already exists.

Possible names: `wavetable`, `wt`

Parameters: `table` (wavetable name), `position` (morph position 0..1)

---

## SignalGen DSL — Remaining Future Work

Carried forward from the completed SignalGen DSL/Registry implementation (archived:
`20260320-signal-gen-dsl-registry-impl.md`).

### Not Yet Implemented

| Feature                   | Description                                                                                         |
|---------------------------|-----------------------------------------------------------------------------------------------------|
| Frontend waveform preview | `SignalGenDsl.renderPreview()` in `audio_bridge` — lightweight pure-math renderer for display       |
| Feedback combinators      | `feedback()`, `feedbackTuned()`, `phaseFeedback()` — needed for Karplus-Strong and waveguide models |
| Time-windowed combinators | `during()`, `duringProgress()`, `chain()`, `ring()`                                                 |
| ControlRate combinator    | Block-rate computation for modulators (cheaper than per-sample)                                     |
| KlangScript `registerOsc` | Per-playback oscillator registration from KlangScript                                               |

### Related: Modulated Delay Line Effects

See `docs/agent-tasks/modulated-delay-line-effects.md` for flanger, chorus, and vibrato implementations
using the existing `DelayLine` with fractional delay and LFO modulation.

---

## Key References

- Julius O. Smith III, "Physical Audio Signal Processing" (free): https://www.dsprelated.com/freebooks/pasp/
- Valimaki & Huovilainen, "Oscillator and Filter Algorithms for Virtual Analog Synthesis" (2006)
- Adam Szabo, "How to Emulate the Super
  Saw": https://www.adamszabo.com/internet/adam_szabo_how_to_emulate_the_super_saw.pdf
- Stilson & Smith, "Alias-Free Digital Synthesis of Classic Analog Waveforms" (
  BLIT): https://ccrma.stanford.edu/~stilti/papers/blit.pdf
