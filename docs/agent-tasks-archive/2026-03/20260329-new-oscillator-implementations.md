# Oscillator / Exciter Implementations

## Already Implemented

### Basic Waveforms

| Exciter  | Aliases    | Anti-aliasing | snd* DSL        | UI Tool |
|----------|------------|---------------|-----------------|---------|
| sine     | sin        | N/A           | **sndSine**     | N/A     |
| sawtooth | saw        | PolyBLEP      | **sndSaw**      | N/A     |
| ramp     |            | PolyBLEP      | **sndRamp**     | N/A     |
| square   | sqr, pulse | PolyBLEP      | **sndSquare**   | N/A     |
| triangle | tri        | N/A           | **sndTriangle** | N/A     |
| zawtooth | zaw        | None          | (via sound/s)   | N/A     |
| pulze    |            | None          | **sndPulze**    | DONE    |
| impulse  |            | N/A           | (via sound/s)   | N/A     |

### Unison (Super) Variants

| Exciter     | Aliases  | Anti-aliasing | snd* DSL           | UI Tool  |
|-------------|----------|---------------|--------------------|----------|
| supersaw    |          | PolyBLEP      | **sndSuperSaw**    | DONE     |
| supersine   |          | N/A           | **sndSuperSine**   | (shared) |
| supersquare | supersqr | PolyBLEP      | **sndSuperSquare** | (shared) |
| supertri    |          | N/A           | **sndSuperTri**    | (shared) |
| superramp   |          | PolyBLEP      | **sndSuperRamp**   | (shared) |

### Noise Generators

| Exciter    | Aliases | snd* DSL       | UI Tool  |
|------------|---------|----------------|----------|
| whitenoise | white   | **sndNoise**   | N/A      |
| brownnoise | brown   | **sndBrown**   | N/A      |
| pinknoise  | pink    | **sndPink**    | N/A      |
| dust       |         | **sndDust**    | DONE     |
| crackle    |         | **sndCrackle** | (shared) |

### Physical Models

| Exciter    | Aliases    | snd* DSL          | UI Tool |
|------------|------------|-------------------|---------|
| pluck      | ks, string | **sndPluck**      | TODO    |
| superpluck |            | **sndSuperPluck** | TODO    |

### Compositions

| Exciter | Type                                    |
|---------|-----------------------------------------|
| sgpad   | Detuned saws + lowpass                  |
| sgbell  | FM bell (sine carrier + sine modulator) |
| sgbuzz  | Filtered square                         |

### Cross-Cutting Features

- All oscillators support `analog` parameter (Perlin noise drift) via oscParams
- All oscillators support `warmth` parameter (one-pole lowpass) via oscParams
- Samples and impulse also support `analog`

---

## snd* DSL Functions — Status

The `snd*` family of DSL functions auto-set the sound AND parse colon-separated params.
Type `snd` in the editor → code completion shows all available sound sources. Uses camelCase.

| Function                        | Status   | Params (colon-separated)                                  |
|---------------------------------|----------|-----------------------------------------------------------|
| `sndPluck("d:b:p:s")`           | **DONE** | decay:brightness:pickPosition:stiffness                   |
| `sndSuperPluck("v:s:d:b:p:st")` | **DONE** | voices:freqSpread:decay:brightness:pickPosition:stiffness |
| `sndSine()`                     | **DONE** | — (no params, just sets sound)                            |
| `sndSaw()`                      | **DONE** | —                                                         |
| `sndSquare()`                   | **DONE** | —                                                         |
| `sndTriangle()`                 | **DONE** | —                                                         |
| `sndRamp()`                     | **DONE** | —                                                         |
| `sndSuperSaw("v:s")`            | **DONE** | voices:freqSpread                                         |
| `sndSuperSine("v:s")`           | **DONE** | voices:freqSpread                                         |
| `sndSuperSquare("v:s")`         | **DONE** | voices:freqSpread                                         |
| `sndSuperTri("v:s")`            | **DONE** | voices:freqSpread                                         |
| `sndSuperRamp("v:s")`           | **DONE** | voices:freqSpread                                         |
| `sndPulze("d")`                 | **DONE** | duty                                                      |
| `sndNoise()`                    | **DONE** | — (white noise)                                           |
| `sndBrown()`                    | **DONE** | —                                                         |
| `sndPink()`                     | **DONE** | —                                                         |
| `sndDust("d")`                  | **DONE** | density                                                   |
| `sndCrackle("d")`               | **DONE** | density                                                   |

Implementation file: `sprudel/.../lang/addons/lang_snd_addons.kt`

---

## UI Tools — Status

Each `snd*` function with parameters needs a corresponding `SprudelXxxSequenceEditor` Kraft UI
component for visual editing of the colon-separated params.

All UI tools should include **presets** (dropdown/selector) similar to `compressor()`, so users
can quickly pick a starting point and tweak from there.

| UI Tool                           | For               | Params                                                         | Presets                                                                                                                      | Status   |
|-----------------------------------|-------------------|----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|----------|
| `SprudelPluckSequenceEditor`      | `sndPluck()`      | decay, brightness, pickPosition, stiffness                     | Guitar, Pizzicato, Harp, Sitar, Banjo, Koto, Steel String, Nylon String                                                      | **DONE** |
| `SprudelSuperPluckSequenceEditor` | `sndSuperPluck()` | voices, freqSpread, decay, brightness, pickPosition, stiffness | 12-String Guitar, Choir Harp, Shimmer Pad, Thick Guitar, Ukulele Chorus, Ethereal Strings, Metallic Cluster, Tight Pizzicato | **DONE** |
| `SprudelSuperSawSequenceEditor`   | `sndSuperSaw()`   | voices, freqSpread                                             | Thin (3v), Classic (5v), Fat (7v), Wide, Tight, Massive (9v)                                                                 | **DONE** |
| `SprudelPulzeSequenceEditor`      | `sndPulze()`      | duty                                                           | Square, Thin, Clarinet, Wide, Nasal, Hollow                                                                                  | **DONE** |
| `SprudelDustSequenceEditor`       | `sndDust()`       | density                                                        | Sparse, Light, Medium, Dense, Heavy                                                                                          | **DONE** |

Simple sound selectors (no params) don't need UI tools — they're just sound name shortcuts.

---

## Candidates for New Exciters

### 1. Phase Distortion — Casio CZ-style Warped Sine

**Priority: High** | Complexity: Simple | CPU: Very low | Data: None

Instead of reading a sine table linearly, the phase accumulator is warped by a transfer function.
Different transfer functions produce saw, square, pulse, and unique **resonant waveforms** that
simulate filter resonance sweeps — a distinctive sound no other synthesis method easily produces.

- The DCW (Digital Controlled Wave) parameter morphs from pure sine to full waveshape.
- **Alias-free** — output is always a continuous warped sine, no discontinuities.
- 8+ distinct waveform shapes from a single oscillator with shape selection.

Possible names: `pd`, `cz`, `phasedist`

Params: `shape` (saw/square/pulse/resonant1/resonant2/resonant3), `dcw` (morph 0..1)

snd* DSL: `sndPhaseDistort("shape:dcw")` or `sndCZ("shape:dcw")`

References:

- https://electricdruid.net/phase-distortion-synthesis/
- https://en.wikipedia.org/wiki/Phase_distortion_synthesis

---

### 2. Waveshaping — Chebyshev Polynomial Harmonics

**Priority: High** | Complexity: Simple | CPU: Very low | Data: None

Sine wave passed through a nonlinear transfer function. Chebyshev polynomials: the nth polynomial
applied to cos(x) produces exactly cos(n*x) — the nth harmonic.

Possible names: `cheby`, `waveshape`

Params: `harmonics` (which Chebyshev orders to mix), `drive` (input amplitude)

snd* DSL: `sndCheby("h:d")`

References:

- Arfib (1978), Le Brun (1979)

---

### 3. Hard Sync Oscillator

**Priority: Medium-High** | Complexity: Medium | CPU: Low | Data: None

Slave oscillator resets phase on master cycle. Classic aggressive sync sweep sound.
Best approach: PolyBLEP correction at sync points (infrastructure already exists).

Possible names: `sync`, `hardsync`

Params: `syncRatio` (slave/master frequency ratio), `slaveShape` (saw/square/tri)

snd* DSL: `sndSync("ratio:shape")`

References:

- Brandt, "Hard Sync Without Aliasing" (2001): http://www.cs.cmu.edu/~eli/papers/icmc01-hardsync.pdf
- DAFx 2022: https://dafx.de/paper-archive/2022/papers/DAFx20in22_paper_3.pdf

---

### 4. Formant / Vocal Oscillator

**Priority: Medium** | Complexity: Medium | CPU: Low | Data: Tiny vowel table

Parallel resonator bank: glottal pulse drives 3-5 biquad filters tuned to vowel formant
frequencies. Can morph between vowels (a, e, i, o, u).

Possible names: `vocal`, `formant`, `vowel`

Params: `vowel` (a/e/i/o/u or continuous 0..1 morph)

snd* DSL: `sndVocal("vowel")`

References:

- Smith, Formant Synthesis Models: https://ccrma.stanford.edu/~jos/pasp/Formant_Synthesis_Models.html

---

### 5. Waveguide Reed / Clarinet

**Priority: Medium** | Complexity: Medium | CPU: Low-Medium | Data: None

Delay line + nonlinear reed function. Builds on same delay line concept as Karplus-Strong.

Possible names: `reed`, `clarinet`, `wind`

Params: `pressure` (blowing pressure), `stiffness` (reed stiffness)

snd* DSL: `sndReed("pressure:stiffness")`

References:

- Smith, "Physical Modeling Using Digital Waveguides" (1992): https://ccrma.stanford.edu/~jos/pmudw/pmudw.pdf
- Full free textbook: https://www.dsprelated.com/freebooks/pasp/

---

### 6. Additive Oscillator — Sum of Harmonics

**Priority: Low-Medium** | Complexity: Simple | CPU: Moderate (N sine calls) | Data: None

Already partially achievable via composition DSL. Dedicated implementation would be more efficient
and offer preset spectra (organ, bell, etc.).

Possible names: `additive`, `organ`

Params: `preset` (organ/bell/choir/etc.), or `harmonics` (list of amplitude weights)

---

### 7. Wavetable Oscillator

**Priority: Low** | Complexity: High | CPU: Low per sample | Data: Requires wavetable files

Reads stored single-cycle waveforms, morphs between them (Serum-style). Large undertaking.

Possible names: `wavetable`, `wt`

Params: `table` (wavetable name), `position` (morph position 0..1)

---

## Exciter DSL — Remaining Future Work

| Feature                   | Description                                                                     |
|---------------------------|---------------------------------------------------------------------------------|
| Frontend waveform preview | `ExciterDsl.renderPreview()` in `audio_bridge` — pure-math renderer for display |
| Feedback combinators      | `feedback()`, `feedbackTuned()`, `phaseFeedback()`                              |
| Time-windowed combinators | `during()`, `duringProgress()`, `chain()`, `ring()`                             |
| ControlRate combinator    | Block-rate computation for modulators                                           |
| KlangScript `registerOsc` | Per-playback oscillator registration from KlangScript                           |

## Key References

- Julius O. Smith III, "Physical Audio Signal Processing" (free): https://www.dsprelated.com/freebooks/pasp/
- Valimaki & Huovilainen, "Oscillator and Filter Algorithms for Virtual Analog Synthesis" (2006)
- Adam Szabo, "How to Emulate the Super
  Saw": https://www.adamszabo.com/internet/adam_szabo_how_to_emulate_the_super_saw.pdf
- Stilson & Smith, "Alias-Free Digital Synthesis of Classic Analog Waveforms" (
  BLIT): https://ccrma.stanford.edu/~stilti/papers/blit.pdf
