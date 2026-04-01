# Exciter / Oscillator Benchmark Suite

## Goal

Automated performance benchmarks for all oscillators and common exciter compositions.
Catch regressions like the `wrapPhase` modulo incident (2x slowdown) before they ship.

## What to benchmark

### Individual oscillators

- sine, sawtooth, square, triangle, pulse, ramp, zawtooth, impulse, pulze
- superSaw, superSine, superSquare, superTri, superRamp (with voices=8)
- karplusStrong, superKarplusStrong
- noise (white, pink, brown), dust

### Common compositions (exciter chains)

- superSaw + lowpass + ADSR + reverb (the current StartPage benchmark pattern)
- karplusStrong + distortion + delay
- square + FM modulation + phaser
- sine + vibrato + tremolo

### What to measure

- RTF (real-time factor) per oscillator at 1, 4, 8 voices
- RTF for compositions at 4 voices
- Absolute render time per block (128 frames at 48kHz)

## How to run

- JVM-based test suite (deterministic, no browser needed, CI-friendly)
- Compare against baseline stored in a JSON/CSV file
- Fail if any oscillator regresses by > 10% from baseline
- Print a summary table after each run

## Notes

- The existing `KlangBenchmark.kt` (jsMain) is a browser-based benchmark for the StartPage.
  The new suite should be JVM-only for CI reproducibility.
- Consider using Kotlin's `measureNanoTime` or `TimeSource.Monotonic` for measurement.
- Warmup: render 100 blocks before measuring to let JIT stabilize.
