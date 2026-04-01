# Audio Benchmark

Performance benchmark suite for Klang's ignitor oscillators and DSP compositions.
Measures real-time factor (RTF) — values below 1.0 mean faster than real-time.

## Running

### JVM

```bash
./gradlew :audio_benchmark:jvmRun
```

### JS (Node.js — console output)

```bash
./gradlew :audio_benchmark:jsNodeProductionRun
```

### JS (Browser — webpack dev server)

```bash
./gradlew :audio_benchmark:jsBrowserDevelopmentRun
```

## Output

The benchmark prints:

- **Table** — aligned human-readable output to stdout
- **CSV** — machine-parseable
- **Markdown** — copy into `docs/benchmarks/` for historical tracking

## What it benchmarks

- **8 basic oscillators** — sine, sawtooth, square, triangle, ramp, zawtooth, impulse, pulze
- **5 super oscillators** — supersaw, supersine, supersquare, supertri, superramp (8 internal voices)
- **2 physical models** — pluck (Karplus-Strong), superpluck
- **4 noise generators** — white, pink, brown, dust
- **4 scaling tests** — supersaw at 1, 4, 8, 16 internal voices
- **5 compositions** — supersaw+lpf+adsr, +reverb, pluck+distort, square+fm, sine+vibrato+tremolo

Results are sorted most expensive first.

## Saving results

Copy the Markdown output to `docs/benchmarks/YYYY-MM-DD-platform.md` for historical reference.
Compare across runs to catch regressions.
