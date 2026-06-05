# Oscillator Engine Unification (completed 2026-06-05)

Branch `dedicated-cycle-time`. A three-part consolidation of the `audio_be` oscillator code: one
waveform shape engine, control-rate reads on the `Ignitor` interface, and one unison engine behind
every super-oscillator. All in `audio_be` (DSL / registry / sprudel / klangscript untouched). Deep
design record lives in memory `project_supersaw_rewrite.md`.

## 1. One waveform shape engine (`waveTrapezoid` / `WaveVoiceState`)

`analogSawShape` + `pulseTrapezoidShape` → ONE `waveTrapezoid` (4-segment: rise → high plateau → fall →
low plateau) in `DspUtil.kt`. `SawVoiceState` + `PulseWaveState` → ONE `WaveVoiceState`
(`setSawShape(rf)` / `setPulseShape(duty, riseFlank, fallFlank, floor)`). ONE mono `WaveIgnitor(kind =
SAW|PULSE, polarity, flankSamples, duty, riseFlank, fallFlank)` behind
sawtooth/ramp/square/pulze/triangle/zaw/zamp/rawPulze (deleted `SawtoothIgnitor`, `RampIgnitor`,
`PulseIgnitor`, `ZawtoothIgnitor`, `ZampIgnitor`, `RawPulseIgnitor`). **Lossless**: the saw config
(empty plateaus) makes `waveTrapezoid` bit-identical to the old `analogSawShape`; pulse/triangle are
rise-first phase-shifted but sonically identical. Guards: `AnalogSawSpec`, `PulseShapeSpec`.

## 2. Control-rate value on the `Ignitor` interface

The `ControlRateIgnitor` marker was replaced by two members on `interface Ignitor` (`Ignitor.kt`):

- `controlRateValueOrNull(freqHz, ctx): Double?` — non-null iff block-constant (the scalar), else null.
  Overridden by the leaves (`ConstantIgnitor` / `ParamIgnitor` / `FreqIgnitor`) **and** all ~13 pure
  pointwise combinators (`plus`/`times`/`mul`/`div`/`minus`/`neg`/`abs`/`pow`/`min`/`max`/`clamp`/`exp`/
  `log`), which fold over block-constant children (mirroring their per-sample math + safety).
- `blockStartValue(freqHz, ctx): Double` — default = `controlRateValueOrNull(...) ?: <scratch render one
  sample>` (the old `readParam` fallback; advances stateful nodes one block, as before).

Beneficiaries that previously rendered a whole scratch buffer to read `[ctx.offset]` now take the scalar
directly: `readParam` / `resolveFreq` / drift-init, `DetuneIgnitor` (semitone amount), the three
`spreadBuf` reads in the super-variants, and the pulse `duty` const-vs-PWM discriminator
(`duty.controlRateValueOrNull(...) != null`). `ControlRateIgnitor.kt` deleted. `withGain` (build-time
`value == 1.0`) and `IgnitorFilters` (overridability caching) keep their concrete `is` checks — different
semantics. Guard: `ControlRateValueSpec` (10 tests).

## 3. All super-oscillators on one unison engine

Before: only saw/ramp shared an engine (`DetunedSawStackIgnitor`); supersine/supersquare/supertri were
three separate classes using PolyBLEP / `sin`, flat `1/v` gain, and a single shared drift object.

After — a 2-level hierarchy in `Ignitors.kt`:

```
DetunedStackIgnitor (abstract)        — voice mgmt, center-dominant gains + amplitude jitter,
│                                        gain-weighted centroid-anchored detune, per-voice AnalogDrift,
│                                        freq/voices/spread caching, voice-major loop
├── TrapezoidStackIgnitor (abstract)  — shared waveTrapezoid per-voice loop
│   ├── SawStackIgnitor               — supersaw + superramp (was DetunedSawStackIgnitor)
│   └── PulseStackIgnitor             — supersquare (duty 0.5) + supertri (flanks 1.0/1.0)
└── SineStackIgnitor                  — supersine (pure sin, no shape)
```

Subclasses supply only `configureShape(vs, dt)` + `renderVoice(...)`. Each variant got its own
`SUPER{SQUARE,TRI,SINE}_{SIDE_ATTEN,GAIN_JITTER,DETUNE_POWER}` constants in `OscillatorTuning.kt`,
seeded `= SUPERSAW_*` (change to diverge) — same attenuation logic (`superSawVoiceGains`) and freq
spreading (`getUnisonDetune` + `detunePower`) as supersaw. `polyBlep` / `BLEP_MIN_DT` are now unused in
`Ignitors.kt` (removed; the `polyBlep` util stays in `DspUtil.kt`).

**Behaviour change (intended):** supersquare/supertri/supersine now sound like supersaw-family voices —
center-dominant gains, per-voice drift, finite-slope edges, on-note centroid tuning. supersaw/superramp
output is byte-identical (same renderVoice loop, relocated into a method). supertri's per-voice shape is
byte-identical to the old triangle formula; supersquare matches the mono square.

## Verification

- `:audio_be:compileKotlinJvm` + `compileKotlinJs` green; `:audio_be:jvmTest` green (incl. new
  `ControlRateValueSpec`).
- Benchmarks (quiet machine, JVM / Node µs per voice): supersaw 4.65 / 8.57 (unchanged),
  supersquare 5.00 / 9.60 (was ~8.2 JVM with PolyBLEP), supertri 5.04 / 9.73,
  supersine 15.68 / 22.56 (was 18.7 JVM). Hot loops untouched; only the once-per-block param read got
  cheaper. All comfortably real-time on the JS worklet.

## Out of scope (future)

- Raw super variants (`superzaw` / `superpulze` / `superzamp`) — not requested; the engine could host
  them via `PulseStackIgnitor` / `SawStackIgnitor` with `flankSamples = 0`.
- A single continuous `(duty, riseFlank, fallFlank)` morph spanning saw↔pulse — the shared primitive is
  enough; not pursued.
