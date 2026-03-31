# Audio Benchmark

Performance benchmark suite for Klang's exciter oscillators and DSP compositions.
Measures real-time factor (RTF) — values below 1.0 mean faster than real-time.

## Running

### JVM

```bash
./gradlew :audio_benchmark:jvmRun
```

### JS (browser via Karma/Node)

```bash
./gradlew :audio_benchmark:jsBrowserProductionRun
```

## Output

The benchmark prints:

- **Table** — aligned human-readable output to stdout
- **CSV** — machine-parseable, pipe to a file: `./gradlew :audio_benchmark:jvmRun > results.csv`
- **Markdown** — copy into `docs/benchmarks/` for historical tracking

## What it benchmarks

- **8 basic oscillators** — sine, sawtooth, square, triangle, ramp, zawtooth, impulse, pulze
- **5 super oscillators** — supersaw, supersine, supersquare, supertri, superramp (8 internal voices)
- **2 physical models** — pluck (Karplus-Strong), superpluck
- **4 noise generators** — white, pink, brown, dust
- **4 scaling tests** — supersaw at 1, 4, 8, 16 internal voices
- **5 compositions** — supersaw+lpf+adsr, +reverb, pluck+distort, square+fm, sine+vibrato+tremolo

## Saving results

Copy the Markdown output to `docs/benchmarks/YYYY-MM-DD-platform.md` for historical reference.
Compare across runs to catch regressions.
