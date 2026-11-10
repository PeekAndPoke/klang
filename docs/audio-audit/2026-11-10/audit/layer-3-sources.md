# Layer 3: the sources

Audit of 2026-11-10 (KLANG-00420). Read-only. All line numbers are of the tree at commit `66ef2a867`.

**What I read** (in full unless noted)

- `audio_be/src/commonMain/kotlin/ignitor/Ignitors.kt`: all of it; the sine partial bank's three per-partial
  loops (lines 499 to 635) only skimmed.
- `ignitor/WaveVoiceState.kt`, `KarplusString.kt`, `SampleIgnitor.kt`, `PhaseOffset.kt`, `PhasePool.kt`,
  `DriftLanes.kt`, `AnalogDrift.kt`, `AnalogDriftCoeffs.kt`, `_noise_laws.kt`, `IgnitorDefaults.kt`
  (`builtInSources()`), `ModApplyingIgnitor.kt`, `CarrierFreqMod.kt`.
- `audio_be/.../SampleStore.kt`, `AudioSample.kt`; `audio_bridge/.../MonoSamplePcm.kt`,
  `audio_bridge/.../constants/OscillatorTuning.kt`; `utils/phase_wrap.kt`, `fastSin` in `utils/fast_math.kt`.
- `common/.../math/PerlinNoise.kt`, `BerlinNoise.kt`.
- Where a source depends on it: the sample branch of `voices/VoiceFactory.kt` (lines 161 to 290), the output
  lines of `FmModIgnitor` in `PitchModFactories.kt`, `audio_fe/.../BrowserAudioDecoder.kt`, the head of the JVM
  decoders.
- The docs the brief names: `audio/CLAUDE.md`, `audio-constraints.md`, `docs/audio-backend-file-map.md`,
  `audio/ref/sample-management.md` in full; `audio/MEMORY.md` and `audio/ref/voice-synthesis.md` by section
  (everything that names a source, the unison stack, drift, phase, samples, the rng); `audio/ref/performance.md`
  by headings and keyword only.
- The probe tables: every `source` row of `aliasing.txt`, `stage-cost.txt` and `clicks.txt`, the lint lines of
  my files in `hot-path-lint.txt`, the leads section of the ticket.
- Tests: the test NAMES of `IgnitorsTest`, `KarplusStringSpec`, `SampleIgnitorTest`, `AnalogSawSpec`,
  `PulseShapeSpec`, `SuperPluckBaseDelaySpec`, `OscShapeEffectSpec`, and a few bodies (cited below).

**What I did not read**

- The source arms of `IgnitorDslRuntime.kt` beyond the lines that hand the rng and the knobs to the factories
  (1361 to 1500 by grep only). `GraphCensus.kt`, `IgnitorRegistry.kt`, `BuiltIgnitor.kt`.
- The bodies of most specs, and none of the phase pool's four specs.
- `stage-probe.txt` and `framing.txt` beyond their legends (their source content came to me through the leads).
- Sprudel's doors, the frontend's sample registry, the soundfont loader.
- Nothing was run in the engine (no Gradle). Where a number below is not from a probe table it is from a small
  Python replica of the code path, run in memory with `python3 -I -` (no file written). Each replica is
  validated against the probe where a probe row exists: my replica of the saw reads -33.4 / -23.2 / -15.9 dB and
  of the naive saw -23.4 / -12.9 / -5.5, the square -44.8 / -34.5 / -25.5, the triangle -77.8 / -46.8 / -26.3,
  the linear-interpolated sample -61.1 / -39.8 / -30.2: the probe's own numbers to 0.1 dB. The pluck replica has
  no probe row to validate against; it is a line-by-line transcription of `KarplusString.render`.

## The probes' leads in this layer

| Lead | Verdict | Cause |
|------|---------|-------|
| Aliasing 1: saw, ramp, supersaw only about 10 dB better than `zawtooth` | CONFIRMED, and the meter overstates it | `Ignitors.kt:959` (`rf = flankSamples * dt`), `WaveVoiceState.kt:52` and `:89`: the edge is a linear flank of `SAW_RESET_SAMPLES` = 1 sample. That is exactly a BLEP with a one-sample box kernel (my box-kernel replica and the engine replica agree to 0.3 dB at every pitch). Its spectrum weight is `sinc(f / fs)`: -3.9 dB at Nyquist, first null at fs. So what folds back from just above Nyquist is hardly reduced, and the meter's total is dominated by that band (the top alias is at 23.9 kHz). See L3-02 for the numbers by band and for what a PolyBLEP reads. |
| Aliasing 1: square -25.5, tri -26.3 at the top pitch | CONFIRMED (square), BY DESIGN (tri) | Square: the same method with a 2-sample flank (`PULSE_MIN_FLANK_SAMPLES`, `WaveVoiceState.kt:67`). Tri: `Ignitors.kt:1192` to `:1200`, the pulse with both flanks fully open is the naive triangle; "triangle aliasing (no PolyBLEP)" is in `audio-constraints.md`. |
| Aliasing 2: the impulse has as much energy off its harmonics as on them | CONFIRMED | `Ignitors.kt:1338` to `1363`: the spike is one whole sample of 1.0 on the first sample after the wrap. That is a Dirac comb sampled through a one-sample box: the folded energy is `1 - integral of sinc^2 over +-fs/2` = 22.6 percent of the total, -5.3 dB re the harmonics, at EVERY pitch. The table reads -5.4, -5.3, -5.5, -4.4, -4.8. See L3-03. |
| Aliasing 3: sample off its own rate, -105.9 down to -30.2 dB | CONFIRMED | `SampleIgnitor.kt:71` to `78` and `98` to `105`: two-point linear interpolation, no other kernel. Images at `fs -+ f` are weighted `sinc^2`: for the 7044 Hz row the image of the 6649 Hz source tone at 41351 Hz is -31.7 dB and lands at 4190 Hz, the table's `top_Hz`. See L3-05. |
| Cost: supersine 18.6 ns for 8 voices against supersaw 9.3 | CONFIRMED, no anomaly | Linear in voices on both (supersine 2.09 / 18.62 / 37.44 at 1 / 8 / 16, supersaw 1.29 / 9.27 / 18.52). The difference is the shape function: `sineLoop` (`Ignitors.kt:2173`) calls `fastSin(phase * TWO_PI)`, a degree-11 odd polynomial with two fold branches (`fast_math.kt:59`), about 2.3 ns per voice sample, the plain sine's own 2.24; the trapezoid is one compare chain and one multiply-add, 1.16 ns. See the performance table. |
| Cost 9: superpluck 5.8 ns per voice | CONFIRMED | `KarplusString.render`, lines 121 to 170: three integer `%` by a run-time length, a division per sample when drift or pitch mod is on, a clamp. See the performance table. |
| Hot-path lint: `PhasePool.kt` 206 to 480 | CONFIRMED, all once per note or rarer, pool ON only (the default is off, `SUPERSAW_PHASE_POOL = 0.0`) | 206: the eviction scan, only for a new key at the cap of 64. 216, 226: a new pool, once per key. 330: one `DoubleArray(voices)` per top-up, 1 or more per served note until the vocabulary is full (about 240 notes). 476, 480: `copyInto` in the banded draw, per draw. 371 to 410: the `!!` cannot throw: `filled >= 1` after the top-up branch and every index is below `filled`. 65: a `Char` in `split(':')`, at voice build, not in render. |
| Hot-path lint: `DriftLanes.kt:238` (and 188, 234) | CONFIRMED, once per note at most | 238: `Random(sharedSeed)` at the first block whose `analogSpread` is below 1 (decision D7 in the KDoc). 188: the lane array grows only past the build-time capacity (a `voices` signal). 234: the shared lane object, only when the build guessed spread 1. |
| Hot-path lint: `Ignitors.kt:923`, `:2950` (and 404 to 407, 1872, 2304, 2723) | CONFIRMED, once per note, only on a build-time misjudgement or a count signal | 923, 2950: drift storage built at the first block when the build read `analog` as 0. 1872, 2723: the voice or string array grows when `voices` is a signal or reads `Freq`. Note the size at 2723: each `KarplusString()` is a 2500-sample line (20 KB), so a superpluck whose count the build could not read allocates 20 KB per string inside `generate`. |
| Clicks 5: `adsr.on=0` does not fade the START (tri jump 109, supersine, berlin) | NOT MINE for the missing fade; the source side BY DESIGN | The sources start where their KDoc says: the trapezoid at phase 0 is -1 (`WaveVoiceState.phase = 0.0`, `waveTrapezoid` first branch), the stack at random phases (`Ignitors.kt:1885`), berlin at a random point. A triangle started at phase 0.25 would start at 0; that is the `phase` knob's job today. |
| Clicks 6: supersine note end kink | NOT TRACED | Nothing in `UnisonStackIgnitor` acts at a note's end. Belongs to the envelope and voice layers. |
| Framing 2: analog drift depends on the block grid | BY DESIGN, confirmed | `AnalogDrift.beginBlock` (`AnalogDrift.kt:135`) steps once per `generate` call whatever the window's length, and the ramp spreads that step over `end - off` frames. KDoc and `audio/MEMORY.md` say so. |
| Framing 3: all 23 sources identical at every offset | CONFIRMED for one source per voice; see L3-16 for two rng consumers in one voice | |
| Dead knobs: `rate`, `octaves`, `persistence`, `bipolar` | NOT MINE | The readers exist (`Ignitors.kt:1550`, `1551`, `1581`, `1582`, `1620`); what is missing is a door. |

## Findings

### L3-01 The pluck is flat: the loop filter's delay is not taken out of the delay line

- **Severity**: critical
- **Tag**: bug / sound
- **Where**: `audio_be/src/commonMain/kotlin/ignitor/KarplusString.kt:183` (`baseDelayOf`), `:121` to `:170` (the
  loop), `:189` (`lpAlphaOf`), `:195` (`apCoeffOf`); `audio_bridge/.../IgnitorDsl.kt:251` (brightness default 0.5).
- **What**: the delay line is read at exactly `sampleRate / freq` samples behind the write head (line 137:
  `readPosF = wp - dl`; the value written at `wp` is read back when `wp` has advanced by `dl`). The loop then
  passes the one-pole `lp += alpha * (sample - lp)` before the write. That filter has a phase delay of
  `(1 - alpha) / alpha` samples at low frequency, 1 whole sample at the default `brightness` 0.5, 4 samples at
  0.2. Nothing subtracts it from `dl`. The loop period is `N + (1 - alpha) / alpha` samples instead of `N`.
  The stiffness allpass `(a + z^-1) / (1 + a z^-1)` adds `(1 - a) / (1 + a)` more, with `a = stiffness * 0.5`:
  almost one further sample for any small stiffness above 0, and none at exactly 0 (the allpass is skipped,
  `hasStiffnessOf`).
  Replica of the loop, fundamental found by an interpolated FFT peak, feedback 0.996, cents re the note asked:

  | Note | brightness 0.5 (default) | brightness 1.0 | brightness 0.2 | 0.5 + stiffness 0.01 | 0.5 + stiffness 0.5 |
  |------|------|------|------|------|------|
  | 110 Hz | -4.0 | 0.0 | -15.8 | -7.9 | -6.3 |
  | 220 Hz | -7.9 | 0.0 | -31.3 | -15.7 | -12.6 |
  | 440 Hz | -15.7 | 0.0 | -61.1 | -31.2 | -25.2 |
  | 880 Hz | -31.1 | 0.0 | -114.4 | -61.7 | -49.7 |
  | 1046.5 Hz | -36.7 | 0.0 | -132.2 | -73.0 | -58.8 |
  | 2093 Hz | -69.7 | -0.1 | -218.5 | -140.3 | -113.0 |

  Three wrong results in one: (a) a default pluck is 16 cents flat at A4 and a third of a semitone flat at C6,
  so it beats against every other source of the song (the recipe list in `IgnitorDefaults.kt` itself suggests
  `Pluck + Sine`); (b) `brightness`, a tone knob, moves the pitch, by more than a semitone over its range;
  (c) `stiffness` jumps the pitch by one more sample of delay between 0 and any small value, then sharpens as it
  grows. The superpluck inherits all three on every string.
- **Reach**: `s("pluck")`, `s("ks")`, `s("string")`, `s("superpluck")`, `IgnitorDsl.Pluck`, at their defaults.
- **Settle it**: a spec that renders `Ignitors.karplusStrong` at 440 Hz for 0.5 s and reads the period from the
  autocorrelation peak (or counts 220 periods): expect 109.09 samples, the code gives about 110.09. The fix to
  test against: `dl = sr / f - (1 - alpha) / alpha - (allpass delay)`, with the fractional part taken by the
  interpolator (exact at low pitch; the one-pole's delay falls with frequency, so the top octave needs the
  phase delay at the fundamental, `atan2((1 - alpha) sin w, 1 - (1 - alpha) cos w) / w`).
- **Confidence**: high for the mechanism (derived from the loop and reproduced by the replica; brightness 1.0
  reads 0.0 cents, which is the control). The cents are the replica's, not the engine's.

### L3-02 The "band-limited" saw and square use the weakest member of the BLEP family, and the KDoc's reason is untrue

- **Severity**: major
- **Tag**: sound / doc
- **Where**: `WaveVoiceState.kt:52` to `:76` (shapes), `:89` (KDoc: "a finite-slope edge is inherently
  band-limited"), `Ignitors.kt:959` and `:2019` (the flank from the unmodulated increment), `:983` and `:2029` (pulse floor),
  `OscillatorTuning.kt:38` (`SAW_RESET_SAMPLES = 1.0`), `:230` (`PULSE_MIN_FLANK_SAMPLES = 2.0`); the words
  "band-limited" at `Ignitors.kt:1452`, `:2464` (the supertri: "piecewise linear, inherently band-limited") and
  `IgnitorDefaults.kt:59`.
- **What**: a linear flank of W samples is the ideal edge convolved with a W-sample box. A box is not a
  band-limiting kernel: its response is `sinc(W f / fs)`, which at W = 1 is still 0.64 at Nyquist and only
  reaches zero at fs. The method is implemented correctly for what it is (the engine's saw and a box-kernel BLEP
  agree within 0.3 dB everywhere), but it is the first and weakest of the family box, triangle (the two-point
  PolyBLEP), cubic (the four-point one). Non-harmonic energy re the fundamental, same window and bands as the
  probe, split by where the aliases land:

  | Saw at | variant | total (the probe's number) | below 15 kHz | below 5 kHz | below the fundamental | harmonic near 10 kHz / 18 kHz re ideal |
  |------|------|------|------|------|------|------|
  | 110 Hz | naive `zawtooth` | -23.4 | -26.0 | -31.0 | -47.7 | 0 / 0 |
  | 110 Hz | engine (box, 1 sample) | -33.4 | -41.3 | -56.7 | -107.5 | -0.6 / -2.1 |
  | 110 Hz | two-point PolyBLEP | -39.5 | -52.5 | -78.8 | below -160 | -1.3 / -4.2 |
  | 110 Hz | four-point (cubic) BLEP | -49.6 | -71.7 | -119.6 | below -160 | -2.5 / -8.5 |
  | 1244.5 Hz | naive | -12.9 | -15.5 | -20.5 | -26.5 | |
  | 1244.5 Hz | engine | -23.2 | -31.0 | -46.6 | -65.8 | |
  | 1244.5 Hz | two-point PolyBLEP | -29.3 | -41.9 | -67.7 | -81 | |
  | 1244.5 Hz | four-point BLEP | -39.5 | -60.7 | -75 | -81 | |
  | 2637 Hz | engine | -20.6 | -27.8 | -43.4 | -52.4 | |
  | 2637 Hz | two-point PolyBLEP | -27.0 | -38.7 | -65.9 | -80.6 | |
  | 4186 Hz | engine | -17.0 | -24.2 | -44.3 | -44.7 | |
  | 4186 Hz | two-point PolyBLEP | -22.5 | -34.2 | -66.9 | -67.5 | |
  | 7044.4 Hz | engine | -15.9 | -22.6 | -44.3 | -32.5 | |
  | 7044.4 Hz | two-point PolyBLEP | -22.1 | -32.0 | -44.4 | -42.1 | |

  Reading: (1) the lead is true but the total is the wrong number to judge by. A correct two-point PolyBLEP
  reads only 6 dB better on it, because both leave the band just under Nyquist full; nobody hears 23.9 kHz.
  (2) Where it is heard, the engine's saw is fine for bass and mid notes (aliases under the fundamental at -107
  dB at 110 Hz) and weak from about 1 kHz up: -66 dB under the fundamental at D#6, -52 at E7, -45 at C8, and
  -31 dB below 15 kHz at D#6. A two-point PolyBLEP buys 10 to 25 dB in the audible band at the price of twice
  the top-octave droop (-1.3 dB against -0.6 at 10 kHz).
  (3) The square: its 2-sample linear flank costs MORE treble than a two-point PolyBLEP of the same width
  (-2.6 dB against -1.3 at 10 kHz, about -10 against -4 at 18 kHz) and rejects LESS in the audible band:

  | Square at | variant | total | below 15 kHz | below 5 kHz | below the fundamental |
  |------|------|------|------|------|------|
  | 440 Hz | engine (2-sample flank) | -38.8 | -41.2 | -54.4 | -93.2 |
  | 440 Hz | two-point PolyBLEP | -36.2 | -49.7 | -76.7 | -143.9 |
  | 1244.5 Hz | engine | -34.5 | -36.5 | -49.3 | -70.7 |
  | 1244.5 Hz | two-point PolyBLEP | -33.1 | -44.0 | -69.9 | -87.3 |
  | 4186 Hz | engine | -28.6 | -31.4 | -47.7 | -47.8 |
  | 4186 Hz | two-point PolyBLEP | -28.8 | -42.0 | -71.5 | -72.0 |

  Here the total even ranks the two the wrong way round at low pitch.
  (4) The flank is sized from the UNMODULATED increment (`rf = flankSamples * dt`, `dt = actualFreq / sr`, before
  `phaseMod` and drift). Under a pitch stage that raises the pitch, the edge is crossed faster than one sample
  and the saw turns into its raw twin: at +24 semitones of pitch envelope the flyback lasts a quarter of a
  sample. `PhaseOffset`'s KDoc says this for a moving `phase`; nothing says it for `penv`, `accelerate`, `fm`.
  A PolyBLEP that takes the per-sample increment has no such gap.
  (5) The naive triangle (by design) for scale: -63 dB under the fundamental at 1244.5 Hz, -51 at 2637, -41 at
  4186, -33 at 7044.
- **Reach**: every `saw`, `ramp`, `square`, `pulse`, `supersaw`, `superramp`, `supersquare` voice; audible on
  exposed notes above about 1 kHz and under upward pitch modulation.
- **Settle it**: add a band-limited column to the aliasing meter (non-harmonic energy below 15 kHz and below the
  fundamental); it separates the methods where the total cannot. Then a listening test of a saw lead at C7 to C8
  against a PolyBLEP build. The doc part needs no test: strike "inherently band-limited" and "band-limited".
- **Confidence**: high (the replica reproduces five probe rows per shape to 0.1 dB).

### L3-03 The impulse aliases at -5.3 dB re its harmonics by construction

- **Severity**: major
- **Tag**: sound
- **Where**: `Ignitors.kt:1338` to `:1363` (the four plain loops), `:1431` (the phased loop).
- **What**: `buffer[i] = if (phase < lastPhase) 1.0 else 0.0`. The spike is placed on the first whole sample after
  the wrap, with amplitude 1 whatever the fraction of a sample by which the wrap was passed. Input: 110 Hz at
  48 kHz, period 436.36 samples; the spikes fall 436 and 437 samples apart in a pattern, which is a jittered
  train. In the frequency domain it is the ideal comb (flat to infinity) through `sinc(f / fs)`: 22.6 percent of
  the power lies beyond Nyquist and folds back. Expected of a band-limited impulse train: no energy off the
  harmonics. Actual: -5.3 dB re the harmonics (probe: -5.4 to -4.4 at all five pitches).
  Two side effects of the unit sample: the level follows the pitch (fundamental -46.8 dBFS at 110 Hz, -11.0 at
  7044 Hz), which is the nature of a spike train, and it follows the device's sample rate (the same train is 3 dB
  quieter at 96 kHz than at 48).
- **Reach**: `s("impulse")`, `IgnitorDsl.Impulse` as an audible source or as an exciter into a filter (the recipe
  "Impulse.lowpass(200), kick body" hides it; a resonant filter or a comb does not).
- **Settle it**: the probe row is the test. A fix to measure against: split the spike over the two samples
  around the true crossing by the fraction (`1 - d`, `d`), a triangle kernel; by the sinc^4 weight that reads
  about -20 dB at once, a four-point kernel far lower.
- **Confidence**: high.

### L3-04 The impulse outputs a run of 1.0 while its phase runs backwards

- **Severity**: minor
- **Tag**: bug
- **Where**: `Ignitors.kt:1338`, `:1346`, `:1356`, `:1363` (the plain loops' rule `phase < lastPhase`); the KDoc of
  `renderPhased` (above `:1404`) names the difference to the phased loop without calling it a defect.
- **What**: input: a negative increment, from a negative `freq` or from `phaseMod[i] < 0` (FM's linear law
  `1 + mod * depth / freq` is negative whenever the depth exceeds the carrier frequency; `safeOut` keeps the
  sign). Then `phase` falls every sample, `phase < lastPhase` is true every sample, and the node writes 1.0 on
  every sample except the one where the phase wraps upward. Expected: one spike per cycle crossed, in either
  direction (or none). Actual: a block of full-scale DC for as long as the modulator holds the phase in reverse.
  The phased loop (`renderPhased`, drop greater than pi) does not do this, so one oscillator has two answers.
- **Reach**: `impulse` under `fm` with depth above the note's frequency, or `Ign.impulse(freq = negative)`.
- **Settle it**: `IgnitorsTest` has "negative phaseMod does not cause drift" for sine, saw, square, triangle,
  pulze and supersaw, and none for the impulse. Add it: a constant `phaseMod` of -1 for one block must not give
  more than `ceil(128 * f / sr) + 1` ones.
- **Confidence**: high (read, not run).

### L3-05 Sample playback is two-point linear at every rate, and the JVM leaves the whole rate conversion to it

- **Severity**: major
- **Tag**: sound / consistency
- **Where**: `SampleIgnitor.kt:63` to `:79`, `:90` to `:106`; `VoiceFactory.kt:181` (the rate folds the PCM's own
  sample rate in); `audio_fe/src/jsMain/kotlin/BrowserAudioDecoder.kt:32` (decodes at a fixed 48000) against
  `audio_fe/src/jvmMain/kotlin/decoders/JvmWavDecoder.kt:139` (keeps the file's rate).
- **What**: (a) Interpolation. A sine sample played off its rate, non-harmonic energy re the tone:

  | Output tone | rate | linear (the engine) | 4-point Hermite | 4-point Lagrange | level loss, linear |
  |------|------|------|------|------|------|
  | 1244.5 Hz | a semitone up | -61.1 | -87.5 | -108.0 | 0.02 dB |
  | 4186 Hz | a semitone up | -39.8 | -55.3 | -65.9 | 0.19 dB |
  | 7044.4 Hz | a semitone up | -30.2 | -40.9 | -47.8 | 0.55 dB |
  | 5 kHz | 44.1 kHz file on a 48 kHz engine | -33.9 (image at 8.9 kHz) | -46.5 | -54.8 | 0.37 dB |
  | 10 kHz | 44.1 kHz file on a 48 kHz engine | -20.4 (image at 13.9 kHz) | -26.4 | -30.6 | 1.5 dB |
  | 15 kHz | 44.1 kHz file on a 48 kHz engine | -11.1 (image at 18.9 kHz) | -13.8 | -15.9 | 3.1 dB |

  The engine column is the probe's own (-61.1, -39.8, -30.2). The images are not harmonics of the note, so on a
  tonal sample (a piano or bell multisample played between its roots, which is most notes) they are heard as
  roughness; the level loss is a treble droop that depends on the fractional position. There is also no
  low-pass before a rate above 1: content above `Nyquist / rate` folds. A four-point kernel costs two more
  reads and about ten multiply-adds per sample on a stage that costs 1.6 ns today.
  (b) Two platforms, two converters. In the browser `decodeAudioData` runs in an `OfflineAudioContext(1, 1,
  48000)`, so every file reaches the backend already converted to 48 kHz by the browser's resampler, and the
  playhead's rate is 1 for an untransposed sample on a 48 kHz engine. On the JVM the decoder hands over the
  file's own rate, and the playhead does the conversion with the linear interpolator: a 44.1 kHz file reads the
  last three rows above, a 22.05 kHz soundfont zone far worse (its images start at 11 kHz). So an offline render
  of a sample song is not the browser's sound in the top octaves, and a browser engine NOT at 48 kHz (the
  `AudioContext` takes `config.sampleRate`) gets a conversion by 48000 / rate on every sample for nothing.
- **Reach**: every pitched sample voice; every sample on the JVM whose file is not at the engine's rate.
- **Settle it**: the probe has the first three rows. Add two: the same sine sample with `sampleRate = 44100`
  under a 48 kHz engine at pitch ratio 1, and a 22050 one. For (b), render one soundfont note offline and in
  the browser and compare the spectra above 10 kHz.
- **Confidence**: high for (a); medium for (b) (read from the two decoders, the JVM's MP3 and generic paths not
  read).

### L3-06 A pluck carries a random DC offset that outlives its tone

- **Severity**: minor
- **Tag**: sound
- **Where**: `KarplusString.kt:52` to `:60` (the burst), `:145` and `:159` (the one-pole has unity gain at 0 Hz, so
  DC is damped by `feedback` alone).
- **What**: the burst is `burstLen` uniform values in -1..1; its mean is not removed. Its mean circulates with
  loop gain `feedback` (0.996) while every partial is also damped by the one-pole. Replica, 220 Hz, defaults,
  five seeds: mean over the first 0.1 s 0.046, 0.010, -0.013, 0.004, 0.027 (about -27 to -48 dBFS); after
  1.9 s the mean is 0.0086, 0.0019, -0.0024, 0.0007, 0.0051 while the AC rms left is 0.0069, 0.0039, 0.0038,
  0.0033, 0.0083. So the last second of a held pluck is as much DC as string. The house stage blocks DC at the
  output; a voice-level `distort` or a cylinder compressor sees it first, and a note end steps from it.
- **Reach**: every pluck and superpluck; with `feedback` 1 (raw by rule) the offset never decays.
- **Settle it**: a spec: render 2 s of `karplusStrong` at 220 Hz for 8 seeds, assert `|mean| < 0.1 * rms` over
  the last 0.1 s. The fix to test: subtract the burst's mean in `excite`.
- **Confidence**: high for the mechanism, the numbers are the replica's.

### L3-07 The pluck's linear-interpolated read makes the damping of the highs depend on the note

- **Severity**: minor
- **Tag**: sound
- **Where**: `KarplusString.kt:137` to `:142`.
- **What**: the fractional delay is a two-tap linear interpolation INSIDE the feedback loop. Its gain per trip is
  1 at a fraction of 0 and `cos(w / 2)` at a fraction of 0.5 (0.92 at 6 kHz, 0.71 at 12 kHz). At 438.4 Hz the
  delay is 109.5 samples, at 440.4 Hz it is 109.0. With `brightness` 1 (the one-pole is then a wire, and the
  only setting that plays in tune, see L3-01) the first note loses its top within tens of milliseconds and the
  second rings bright for seconds. At the default brightness the one-pole dominates (0.68 per trip at 6 kHz) and
  the effect is smaller. The usual cure is a first-order allpass for the fraction, outside the damping filter.
- **Reach**: melodic plucks with `brightness` near 1; drift and vibrato sweep the fraction, so they modulate the
  brightness as well as the pitch.
- **Settle it**: render both notes above at brightness 1, compare the energy above 5 kHz in the window 0.2 to
  0.4 s.
- **Confidence**: medium (derived, not rendered).

### L3-08 `pickPosition` sets the burst's length, not a pick position, and moves the level by about 10 dB

- **Severity**: minor
- **Tag**: doc / sound
- **Where**: `KarplusString.kt:52` to `:53`; KDoc at `Ignitors.kt:2545` ("pick position modeling").
- **What**: `burstLen = delayLen * (0.1 + 0.9 * pp)`, `burstStart = (delayLen - burstLen) * pp`. At 0 the burst
  is the first tenth of the period, at 1 the whole period. A picked string's position gives a comb (the
  harmonics at multiples of `1 / position` are missing); a shorter noise burst gives the same flat spectrum
  with a tenth of the energy. Expected from the name and the KDoc: a timbre knob at constant level. Actual: a
  level knob (rms falls with `sqrt(burstLen)`, 10 dB from 1 to 0) with a small change of the attack.
- **Reach**: `pickPosition` on pluck and superpluck.
- **Settle it**: render at 0, 0.5 and 1, compare rms over the first 50 ms and the spectrum's comb.
- **Confidence**: high for the formula, medium for "how much it matters" (a listening task).

### L3-09 The perlin noise repeats every 256 lattice units: 1.78 s at `rate` 1

- **Severity**: minor
- **Tag**: sound
- **Where**: `common/src/commonMain/kotlin/math/PerlinNoise.kt:54` (`floor(x).toInt() and 255`), `_noise_laws.kt:26`
  (`PERLIN_UNITS_PER_SECOND = 144`), `Ignitors.kt:1548` to `:1557`.
- **What**: the gradient table has 256 entries and the index wraps, so `noise(x + 256) == noise(x)` exactly;
  every fBm octave is then periodic in 256 too. The walk moves 144 units per second times `rate`. Input: `rate`
  1, a 10 s note. Expected of a noise: no period. Actual: the same 1.778 s curve five and a half times; at
  `rate` 10 a 0.178 s loop, which is a 5.6 Hz pattern. The start is random per voice, the curve is not. The
  Katalyst's perlin modulator reads the same law. Berlin has no period (a hash of the index).
- **Reach**: `perlin` as a modulator on anything held longer than `1.78 / rate` seconds (the "wandering filter"
  recipe), and as a source.
- **Settle it**: render 4 s at rate 1 and assert that the samples at `t` and `t + 256 / 144` s differ.
- **Confidence**: high (read; the arithmetic is exact).

### L3-10 Four noise colours are set per sample, so they follow the device's sample rate

- **Severity**: minor
- **Tag**: consistency
- **Where**: `Ignitors.kt:1232` (`NOISE_TILT_LP_COEF` 0.15 per sample), `:1487` (brown, `BROWN_LEAK_DEFAULT` per
  sample), `:1511` to `:1516` (Kellet's pink coefficients, designed for 44.1 kHz), `:1678` (`CRACKLE_DC_POLE`
  0.995 per sample; the crackle map itself runs once per sample).
- **What**: the same per-sample coefficient is a different corner at another rate. White's tilt pivot is 1140 Hz
  at 44.1 kHz, 1241 Hz at 48, 2483 Hz at 96. Brown's corner is 142 Hz, 154 Hz, 309 Hz. Pink's 1/f band and
  crackle's texture move up an octave at 96 kHz. `perlin` and `berlin` had this defect and were fixed on
  2026-11-05 (`_noise_laws.kt`); these four were not. The engine's rate is the host's (`config.sampleRate`).
- **Reach**: any song with noise played on a 44.1 or 96 kHz device against one made at 48.
- **Settle it**: render brown noise at 48000 and 96000, compare the -3 dB corner.
- **Confidence**: high.

### L3-11 The sources' levels differ by up to 20 dB, and a unison stack gets quieter with every voice

- **Severity**: note
- **Tag**: sound / consistency
- **Where**: `Ignitors.kt:2256` to `:2279` and `:3006` to `:3030` (stack gains normalised to SUM 1), `:2718`
  (superpluck `1 / v`), `:1487` (brown), `:1521` (pink `* 0.11`).
- **What**: measured rows (voice rms from `clicks.txt`, fundamentals from `aliasing.txt`):

  | Source | rms re a saw voice | why |
  |------|------|------|
  | saw, tri | 0 dB (rms 0.577) | peak 1 |
  | sine | +1.8 dB | peak 1 |
  | square | +4.8 dB | peak 1, fundamental +2.1 dBFS |
  | white noise | 0 dB | uniform -1..1 |
  | pink noise | -9.1 dB | the Kellet sum times 0.11 |
  | brown noise | -19.9 dB | `k / (3 (2 + k))` variance at k 0.02 |
  | supersaw, 8 voices | -11 dB | gains sum to 1, phases random: about `-10 log10(v)` |
  | supersine, 8 voices | -11.4 dB re a sine | the same |
  | superpluck, 8 strings | about -9 dB re a pluck | `1 / v`, independent bursts |

  The sum-to-one law guarantees a peak of 1 and costs 3 dB per doubling of the count: `voices` is also a volume
  knob (2 voices -3 dB, 16 voices -12 dB). Brown at a fifth of pink's level surprises whoever swaps one for the
  other.
- **Reach**: every song that swaps sources or automates `voices`.
- **Settle it**: a maintainer's decision (see "Decisions worth revisiting"), not a test.
- **Confidence**: high.

### L3-12 At an even voice count the stack has no centre voice, and only one of the two middle voices gets the reduced jitter

- **Severity**: minor
- **Tag**: consistency / test
- **Where**: `Ignitors.kt:2261` (`center = (v - 1) / 2`), `:2265`; `:3038` to `:3061` (`getUnisonDetune`);
  `IgnitorDsl.kt:294` (default `voices` 8); `OscillatorTuning.kt:56` (`SUPERSAW_SIDE_ATTEN = 0.1`).
- **What**: at the default 8 voices the detunes are symmetric about 0 with none AT 0: the two middle voices sit
  at +-0.097 of half the spread (+-0.97 cents at the default spread 0.2), with equal base gains (0.986 of the
  maximum). `center` is index 3, so voice 3 gets `centerJitterScale` 0.4 and its twin, voice 4, the full jitter.
  The KDoc's "CENTER voice (detune about 0, carries the perceived pitch, the ring)" describes odd counts only;
  at 8 the pitch is carried by a pair that beats (0.29 Hz at middle C). Also, at side attenuation 0.1 the
  profile is nearly flat (edge voices at 0.9 of the middle), so "center-dominant" in the KDocs at `:1761`, `:2239` and
  `:2986` no longer describes the constant in force.
- **Reach**: every super oscillator at an even count, the default included.
- **Settle it**: `AnalogSawSpec`'s two stack tests build 17 voices "(odd, exact center)". Add the default 8, and
  decide whether an even count should scale the jitter of both middle voices.
- **Confidence**: high.

### L3-13 Stereo files: the browser keeps the left channel only, the JVM averages; the ref page says both

- **Severity**: minor
- **Tag**: consistency / doc
- **Where**: `audio_fe/src/jsMain/kotlin/BrowserAudioDecoder.kt:47` (`getChannelData(0)`);
  `audio_fe/src/jvmMain/kotlin/decoders/JvmWavDecoder.kt:134` (`sum / channels`);
  `audio/ref/sample-management.md` ("Returns first channel as mono PCM" and, further down, "Stereo samples are
  downmixed to mono during decoding (L+R averaged)").
- **What**: `decodeAudioData` keeps the file's channel count whatever the context's; channel 0 is the left one.
  A sample whose content sits on the right is silent in the browser and at half level on the JVM; a wide stereo
  sample is a different sound on the two platforms. Outside this layer's code, reported because the brief asks
  for the stereo handling.
- **Reach**: any stereo sample file.
- **Settle it**: decode a file with a tone on the right channel only on both platforms.
- **Confidence**: medium (the browser's behaviour is from the Web Audio specification, not run).

### L3-14 The playhead never plays a sample's last frame, and a loop over the whole sample writes a zero at every pass

- **Severity**: minor
- **Tag**: bug
- **Where**: `SampleIgnitor.kt:72` and `:99` (`if (base >= pcmMax) 0.0`); `VoiceFactory.kt:198` to `:201` (an
  explicit loop without `begin` and `end` is `[0, pcmSize)`).
- **What**: for `ph` in `[size - 1, size)` the code writes 0.0 where the last frame (or the last frame
  interpolated towards the loop's first) belongs. One shot: the final frame is lost (the spec pins this:
  `SampleIgnitorTest`, "Last sample: base=4 >= pcmMax(4), so output is 0"). Looping with `loopEnd == size`
  (`s("x").loop(1)`): `1 / rate` output samples of exact zero at every pass, a tick wherever the sample does not
  end at 0. In general the interpolation at a loop's end reads `pcm[loopEnd]`, the frame after the loop, not
  `pcm[loopStart]`; that is right for soundfont loops (their end frame equals their start frame by convention)
  and a small step for any other loop region.
- **Reach**: `loop` on a whole sample (drum loops, textures).
- **Settle it**: a spec: PCM of constant 0.5, 8 frames, looping `[0, 8)`, rate 1, render 20 frames: every sample
  must be 0.5; the code gives 0.0 at frames 7 and 15.
- **Confidence**: high.

### L3-15 Texts that say what the code does not do

- **Severity**: note
- **Tag**: doc
- **Where and what**:
  - `WaveVoiceState.kt:89`, `Ignitors.kt:1452`, `:2464`, `IgnitorDefaults.kt:59`: "band-limited" (see L3-02; the
    supertri line claims it for the naive triangle, whose aliasing `audio-constraints.md` accepts by name).
  - `audio/ref/voice-synthesis.md`, "Oscillators": the character constants "live in
    `ignitor/OscillatorTuning.kt`"; they moved to `audio_bridge/.../constants/OscillatorTuning.kt` on 2026-08-29.
    `docs/audio-backend-file-map.md` lists `OscillatorTuning` under the ignitor files too.
  - `audio/ref/sample-management.md`: `MonoSamplePcm.pcm: FloatArray` (it is a `DoubleArray`), `decode(bytes,
    mimeType)` (it is `decodeMonoFloatPcm(audioBytes)`), the decoder paths, the two stereo statements (L3-13).
  - `AudioSample.kt:16`: "`MonoSamplePcm.pcm: FloatArray` is widened when read in `SampleIgnitor`"; the widening
    happens in the decoders.
  - `MonoSamplePcm.kt:13`: "sampleRate matches renderer sampleRate (resampling can be added later)";
    `VoiceFactory.kt:181` converts in the playhead's rate.
  - `SampleIgnitor.kt:20`: "applies Perlin noise drift"; the lane is the Ornstein-Uhlenbeck `AnalogDrift`.
  - `Ignitors.kt:1761`, `:2239`, `:2986`: "center-dominant" at side attenuation 0.1 (L3-12).
  - `PhasePool.kt:122`: "Bounded (at most about 4k trig-loop iterations per note)"; see the suspicion
    on the pool's work bound.
  - `IgnitorsTest.kt:856`, "supersaw - more voices increases energy": the body asserts only that both renders
    are non-zero, and under the sum-to-one law more voices give LESS energy.
- **Confidence**: high.

### L3-16 Two rng consumers in one voice make its noise depend on where the block borders fall

- **Severity**: note
- **Tag**: consistency
- **Where**: `IgnitorDslRuntime.kt:1391` to `:1397` (every noise node gets the ONE `cache.random`), the per-sample
  draws at `Ignitors.kt:1223`, `:1230`, `:1485`, `:1509`, `:1626`; `KarplusString.kt:57` (the burst, drawn inside the
  first `generate`).
- **What**: the white, brown, pink and dust nodes draw from the voice's shared stream per sample, node after
  node, block after block. With two of them in one tree, node A takes `length` draws, then node B. A voice that
  starts at frame 37 of a block renders 91 frames first, at frame 0 it renders 128: A's 92nd sample is the
  183rd draw in the first case and the 92nd in the second. The same holds for a noise layered with a pluck: the
  burst is drawn after or before the noise's first window. The statistics are the same; the samples are not,
  so the framing probe's "identical at every offset" and "every voice drew the same random values at every
  offset" hold for one consumer per voice only (the probe's "not covered" list does not name this).
- **Reach**: `Ign.white() + Ign.dust()`, a noise layer on a pluck, any tree with two per-sample drawers.
- **Settle it**: a framing case with `WhiteNoise + BrownNoise`. A per-node generator seeded from the voice
  stream at build (the `AnalogDrift` pattern) removes it and the virtual `Random` call per sample.
- **Confidence**: high (read).

## Performance candidates

JVM costs are from `stage-cost.txt`. The Kotlin/JS costs are from `stage-cost-js.txt`, which appeared in the
folder while this audit ran (the median of 5 node processes; its header says to quote ratios to the plain sine,
4.07 ns there, and the ranking, not the nanoseconds). The JS ranking differs from the JVM's exactly where a
source draws from `kotlin.random.Random` or hashes integers: berlin is 4.1 times the sine on JS (2.0 on the JVM),
pink 2.5 (1.6), dust 1.5 (1.0), brown 1.5 (1.6), white 1.4 (0.96), against a saw at 0.45 on both.

| Where | What | Rate | Measured cost | Expected gain (rough) | Risk to the sound |
|------|------|------|------|------|------|
| `Ignitors.kt:2151` `sineLoop` | `fastSin` per voice per sample. With no pitch mod and no drift the frequency is constant over the block: a rotation (two multiplies, two adds) or the two-term recurrence replaces the polynomial. | per sample, per voice | JVM: supersine 18.6 ns at 8 voices, 2.3 per voice; trapezoid 1.16. JS: 27.8 against the supersaw's 19.4, so the sine's extra is smaller there (3.5 against 2.4 per voice) | 30 to 50 percent of the supersine on the JVM, less on JS: measure on JS first | A SOUND decision: the recurrence drifts in amplitude unless renormalised per block; the phased, modulated and drift loops stay as they are. Not bit-identical. |
| `Ignitors.kt:499` `renderPartial` | The same for the partial bank's plain path. | per sample, per partial | JVM 13.1 ns for 5 partials, 2.6 each; JS 24.5, 4.9 each (dearer per sine than the stack's 3.5: one more thing to look at) | as above | as above |
| `KarplusString.kt:139`, `:141`, `:169` | Three `%` by a run-time length per sample. `readPosWrapped` is already in `[0, len]`, `readIdx + 1` and `wp + 1` overshoot by at most one: a compare and a subtract each. The KDoc records modulo by constant against modulo by length; a conditional wrap is not among the measured forms. | per sample, per string | JVM: pluck 4.56 ns, superpluck 45.8 at 8 strings. JS: 7.2 and 59.2 (14 times the sine, the dearest default source) | 1 to 2 ns per string | none (the same indices) |
| `KarplusString.kt:126`, `:130` | `dl /= phaseMod[i]` and `dl /= m` are two divisions per sample; `m` is a linear ramp near 1. One division of the product, or a reciprocal ramp. | per sample, drift or pitch mod on | not in the table (the superpluck rows are at analog 0) | measure first | A reciprocal ramp is a SOUND decision at 1e-7 relative; one division of the product is exact to rounding. |
| `Ignitors.kt:1025`, `:1125` PWM | `d != lastDuty` is true on every sample of a moving duty, so `setPulseShape` runs per sample: two divisions and four clamps. Bake at a coarser rate or keep the slopes as `2 / floor` (the floor is block-constant) when the flank is the floor. | per sample, signal duty | no row | measure first | none if only the floor case is specialised |
| `Ignitors.kt:1223`, `:1485`, `:1509`, `:1626` noise | `rng.nextDouble()` is a virtual call into `kotlin.random.Random` (two 32-bit draws per double) per sample. `AnalogDrift` already carries an inline xorshift32 for this reason. | per sample | JVM: white 2.14, brown 3.60, pink 3.53, dust 2.26 ns. JS: white 5.68, brown 6.07, pink 10.10, dust 6.37, against a saw at 1.81: the draw is most of a noise's cost there | about 1 ns per noise on the JVM, 3 to 4 ns on JS | A SOUND decision in the weak sense: other dice, the same statistics; it also settles L3-16. |
| `Ignitors.kt:1587` berlin | Two integer hashes per sample; the lattice index changes once per 333 samples at rate 1. Cache `v0`, `v1` while `floor(t)` holds (single octave). | per sample | JVM: berlin 4.43 against perlin 2.64. JS: berlin 16.84 against perlin 4.23, the dearest single-voice source | about 2 ns on the JVM, most of 12 ns on JS | none |
| `Ignitors.kt:1626` dust | A uniform draw per sample to decide a hit that comes about once per 1200 samples at the default density. Draw the gap to the next hit instead. | per sample | JVM 2.26 ns, JS 6.37 | about 1.5 ns on the JVM, about 4 on JS | other dice |
| `Ignitors.kt:2077`, `:2081`, `:1079` | `buffer[i] = if (first) s else buffer[i] + s` and `if (pm != null)` inside the per-sample loops: two loop-invariant branches. The JIT unswitches them on the JVM; on V8 it is worth one measurement. | per sample, per voice | supersaw 1.16 ns per voice | small | none |
| `DriftLanes.kt:238` | `Random(sharedSeed)` to seed the shared lane. The seed needs two doubles and one int: an inline generator on `sharedSeed` avoids the object. | once per note | an object | removes one of the lint's 11 open findings | other dice for the shared lane |

## Suspicions

- **States with no way back from one non-finite knob read.** The oscillators recover (their wrap turns a NaN
  phase into 0). These do not: brown noise (`Ignitors.kt:1480`, a NaN `leak` passes `coerceAtLeast`, then `out`
  is NaN for good); `perlin` and `berlin` (`:1556`, `:1588`, `pos += step` with a NaN `rate`); the sample
  playhead (`SampleIgnitor.kt:82`, `:109`: a NaN `phaseMod` sample makes `ph` NaN, every compare fails, `frac` is
  NaN, every later sample is NaN). Not traced to a door: slots read non-finite as unset, the pitch stages pass
  `safeOut`, the arithmetic guards its results. What would settle it: an `OpaqueIgnitor` that returns NaN for one
  block in each of the three positions; and for the playhead, whether a raw `pitchMod` can sit over a sample.
- **`pitchRatio` NaN.** `VoiceFactory.kt:179`: `(target / base).coerceIn(...)` passes a NaN (0 / 0 when a
  sample's `pitchHz` and the note are both 0); the rate is then NaN and the playhead with it. Settle: can
  `entry.pitchHz` be 0?
- **`end` cuts a sample without a fade.** `SampleIgnitor.kt:68`, `:95`: `ph >= stopFrame` writes 0.0 from one sample to
  the next. If the gate outlasts `end`, that is a step of up to the sample's level. The click probe did not
  measure sample edges. Settle: a click case with `end(0.5)` on a sustained tone.
- **The phase pool's stated work bound.** `PhasePool.kt:298` to `:301`: `topUpPerNote` is at least 1, so one
  served note costs `tries * voices` trig pairs: 64 * 256 = 16384 at the caps, four times the "about 4k" the
  class KDoc states, and the stateless `selectBandedPhases` (`Ignitors.kt:2290`) has the same product per note.
  A six-note chord of such voices would be about 100k `sin` and `cos` in one block. Far from any default (the
  pool is off, supersine's 40 tries at 8 voices is 320). Settle: time one note-on at `voices` 256, `drawTries`
  64.
- **A partial bank knob moved by an LFO steps once per block.** `Ignitors.kt:667` to `:694`: `rolloff` and the
  fundamental's gain are read once per block and applied as constants, so a moving rolloff is a 375 Hz
  staircase on each partial's gain. The KDoc recommends automating `rolloff`. Settle: a click or zipper row with
  `harmonicsRolloff = sine(2).range(0, 2)`.
- **A string that never finishes arriving.** `SampleStore.kt:137` to `:185`: a `Partial` entry whose last chunk
  never comes keeps its array and answers `contains` true for the backend's life, so the sample is never asked
  for again. Also `residentBytes` is not reduced when a `Complete` replaces a `Partial`. Settle: does the
  frontend ever abandon an upload?
- **Crackle at `chaos` 2.0.** The bound is documented as the edge of divergence. My replica of the map stayed
  finite over 2 million samples (peak 3.9), and a NaN re-seeds. If the map ever reached infinity the DC blocker
  (`Ignitors.kt:1678`) would compute `inf - inf` and hold a NaN that the guard does not cover. Not observed.

## Decisions worth revisiting

- **No PolyBLEP** (`audio-constraints.md` accepts it for the triangle; for the saw and square it is a KDoc
  statement). The argument is L3-02: the engine already pays the cost class of a BLEP (a branch and a
  multiply-add at the edge) and gets the box kernel's rejection. A two-point PolyBLEP is the same idea one order
  up, is brighter than the square's 2-sample flank, and follows pitch modulation. "Softens with pitch" could be
  kept as a separate, deliberate tilt if the ear wants it.
- **The stack's gains sum to 1** (L3-11). A constant-power law, or a law between the two, would keep `voices`
  from being a volume knob; the price is a peak above 1 at coherent moments, which the engine tolerates
  elsewhere (the Motor is raw).
- **A default of 8 voices** (L3-12): an odd default (7 or 9) has the centre voice the KDocs describe.
- **`pickPosition`** (L3-08): either rename what it does or make it the comb.

## Listening tasks

- A saw and a square lead from C6 to C8, dry, then with `penv` +12 and +24: is the inharmonic grit under the
  note acceptable, and does it change under the pitch envelope (L3-02 point 4)?
- The square against the saw at the same pitch: the square is duller at the top (2-sample flank against 1). Is
  that intended character?
- A pluck melody doubled by a sine or a saw an octave apart, default knobs: the beating of L3-01. Then
  `brightness` swept on a held chord: does the chord go out of tune?
- Two plucks a few hertz apart at `brightness` 1 (L3-07): does one ring and the other not?
- A piano or bell soundfont, top two octaves, browser against an offline render (L3-05).
- `perlin` on a filter cutoff at `rate` 1 for 10 s: can the 1.78 s loop be heard (L3-09)?
- supersaw at 7, 8 and 9 voices, low notes: the slow beat of the middle pair at 8 (L3-12).
- The impulse through a resonant filter at 110 and 880 Hz: roughness between the harmonics (L3-03).

## Test truth

**Pinned**: frequency by zero crossings for sine, triangle, saw, square, pulze, ramp (at 100, 440, 1000 Hz);
amplitude bounds; phase continuity across blocks; "negative phaseMod does not cause drift" for six oscillators;
the trapezoid's shapes (`PulseShapeSpec`, `AnalogSawSpec`); the byte-identical default paths of white, brown
and dust; the pluck's burst draw order, state carry-over and the superpluck's delay cache
(`KarplusStringSpec`, `SuperPluckBaseDelaySpec`); the sample playhead's interpolation, loop wrap, stop frame and
negative playhead; the fBm single-site form; the phase pool (four specs, not read).

**Not pinned, and the mutation that would survive**:

- The pluck's pitch. Nothing measures it. Mutant: `baseDelayOf` returns `sampleRate / freqHz + 1.0`. Every
  spec stays green (the delay-cache spec compares delays with each other, not with a pitch).
- The pluck's DC and decay. Mutant: `lpAlphaOf` returns `brightness.coerceIn(0.01, 1.0) * 0.5`.
- Pink noise's spectrum: the tests are "bounded", "roughly zero mean", "gain scales". Mutant: change `0.99886`
  to `0.9` (the lowest pole): green. The same for brown's default corner beyond the byte-identity of one seed.
- Any aliasing threshold. The probe measures; no spec holds a number. Mutant: `SAW_RESET_SAMPLES = 0.0` turns
  the saw into `zawtooth`; `OscShapeEffectSpec` asserts only that the knob "reaches the audio", and the saw
  tests allow it ("amplitude close to gain").
- The impulse under a negative phase increment (L3-04), and its count above Nyquist.
- The stack at an even count (the two stack tests use 17 voices). Mutant: `centerJitterScale` forced to 1.0:
  "onsets never go dead" asserts rms above 0.02 over 17 voices, which no jitter setting can fail.
- "supersaw - more voices increases energy" asserts nothing about energy (L3-15).
- The stack's level against the voice count, and the detune law's width (only its symmetry is pinned).
- The perlin period (L3-09); the noise colours against the sample rate (L3-10).
- A loop over the whole sample (L3-14): "looping wraps playhead correctly" would need to be read for whether its
  PCM hides the zero frame; the one-shot test pins the dropped last frame as intended behaviour.
- The sample player at a PCM rate other than the engine's.

## For the audit ruleset

- A replica of ten lines, checked against the probe's own rows before it is trusted, turned one lead ("only 10
  dB better") into a table by band with the alternatives beside it. The rule "read-only, no Gradle" should say
  plainly whether an in-memory calculation is allowed; I assumed yes and declared it.
- A probe total is a lead, not a verdict. The aliasing meter's single number is dominated by the band above 20
  kHz and ranked the square and a PolyBLEP square the wrong way round. Probes that feed an audit should carry a
  band-limited or weighted column.
- The probes marked the pluck "not measurable" and the audit found its worst defect there by deriving the loop
  delay. "Not measurable this way" rows deserve a second, simpler measurement (here: the period), not a skip.
- Tuning is a question no probe and no spec asked of any source. A pitch column (the `f_cents` the aliasing
  meter already computes) for every pitched source would have caught L3-01.
- The brief's list of questions for the layer was the most useful part; the generic checklist added the
  non-finite and test-truth passes. `audio/MEMORY.md` at 876 lines is too long to read whole inside one layer's
  budget; a per-layer extract would help.
- Platform edges (decoders, sample rates) sit between layers. Name an owner for "what reaches the backend",
  or each auditor will touch it only in passing, as I did.
