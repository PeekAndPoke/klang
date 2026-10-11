# The Katalyst stages in the measurement harness: time and bytes per block

Status: **open** (2026-10-11). Not started.

## Why

The maintainer, 2026-10-11: "measuring the cost and put them into the measurement harness would be good, especially
the memory consumption as already present for most ignitor nodes".

- **The arithmetic stages were measured once, by hand.** D5 of `docs/tasks-archive/2026-10/20261011-katalyst-arithmetic.md`
  timed them with a scratch loop on the JVM only (one cheap stage about 50 ns per block, a `pow` about 3.4 µs); the JS
  figure was never taken, and the loop is gone.
- **No harness case runs an authored Katalyst chain.** The allocation check (`audio_benchmark/AllocationCases.kt`,
  `alloc-budgets.txt`) has two orbit cases, `E-delay` and `E-stages`, and both run the classic chain through its
  slots. The timing suite (`EffectBenchmark.kt`) runs the effect classes directly (the compressor, the filters), not
  Katalyst stages.
- **The JVM guards** in `jvmTest` cover the first block of the voices (`FirstBlockAllocationSpec`) and the resonator
  stages (`KatalystResonatorAllocationSpec`), nothing else of the Katalyst.

## What it is

1. **Allocation cases for an authored chain.** An engine case may carry a `KatalystDsl`: the rig registers it
   (`KlangCommLink.Cmd.RegisterKatalyst`) and the voice references it by name (`VoiceData.katalyst`), so the chain
   runs exactly as a song's would, the dispatcher, the cylinder and the master stage included. Cases:
   - `E-arith`: the eight arithmetic stages in one chain, every operand a constant (the plain law loops, `pow` and
     `div` included).
   - `E-arith-glide`: the same chain with `Kat.param` operands that a pattern MOVES, so the glide path runs (the
     per-sample ramp, `div`'s reciprocal, the cap's settle). A fixed, learnable move: new notes every N blocks that
     alternate between two `katp` values, no random cases.
2. **Timing rows** for the same chains, built through the real chain builder, on both platforms, beside the
   existing effect rows: one cheap stage, three cheap stages, `pow`, `div` while gliding.
3. **Budgets and the scoreboard.** The new lines in `alloc-budgets.txt` are set by hand from the first runs by the
   file's own rule (about 40 B above the V8 median, below the regression they guard; 6 B on the JVM where it reads
   0). A change to the case list starts a new scoreboard table (`docs/benchmarks/alloc-scoreboard.md`).

## Decide before implementing

- **M1, scope.** The arithmetic stages only, or every Katalyst stage the harness does not run today (bus `distort`,
  `compressor`/`limiter`, `eq`, `gain`, `duck`, `parallel`, `bands`; `body` and `vowel` on V8)? The rig is most of
  the work and a stage is one more case. Lean: the arithmetic now plus the uncovered stages in the same change, one
  case per stage family, so the harness covers the Katalyst the way it covers the Ignitor nodes.
- **M2, the moving operand.** How a case moves a `Kat.param`: alternating `katp` values on notes every N blocks
  (what the engine cases can do today), or a new rig hook that writes the orbit's param state directly. Lean: notes,
  no new hook, unless the note path's own allocation hides the stage's.

## Steps

1. The rig: an engine case with a `KatalystDsl`, registered and referenced. Prove it measures the chain (a mutant
   that allocates once per block in one arithmetic stage must breach on both platforms).
2. The cases and the timing rows (M1, M2). Each new case proves it still sounds at the end of its window (the
   harness's own peak check).
3. Budgets, the scoreboard table, `audio_benchmark/README.md` (the case count), `audio/ref/performance.md`.
4. `console/full-check.sh` runs green with the new cases.

## References

- `audio_benchmark/README.md` (how the check runs), `audio_benchmark/src/commonMain/kotlin/AllocationCases.kt`,
  `audio_benchmark/alloc-budgets.txt`, `audio_benchmark/src/commonMain/kotlin/EffectBenchmark.kt`
- `docs/tasks-archive/2026-10/20261011-katalyst-arithmetic.md` (D5)
- `docs/tasks/katalyst-knob-expressions.md` step 4 measures its knobs with this harness.
