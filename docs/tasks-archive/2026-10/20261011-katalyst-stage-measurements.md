# The Katalyst stages in the measurement harness: time and bytes per block

Status: **done 2026-10-11**, reviewed in one round (clean, 0 CRITICAL, 0 MAJOR, five MINORs applied). Ten
allocation cases, six timing rows, budgets and the scoreboard table; two V8 allocations found and left open (see
Built), filed as engine follow-ups 33 and 34, the gliding `div`'s JVM time as 35.

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

## Built (2026-10-11)

**M1 as decided, checked against `KatalystStageDsl`.** The variants are body, vowel, delay, reverb, phaser,
compressor, distort, duck, eq, gain, the eight arithmetic stages and parallel. `E-delay` and `E-stages` already ran the
delay, the reverb and the phaser (through the classic chain's slots; the body, the vowel and the compressor sit off
there, the gain at unity, the duck unnamed). Two names in the list are doors, not stages: `limiter` appends a
`compressor` with limiter numbers, and `bands` writes a `parallel` of branches that each start with `eq` crossover and
all-pass sections. `bands` keeps its own case: it is the only one that runs the crossover sections. Not covered: an
authored chain at the master position (`MasterBus`; every engine case runs its empty default), and a moving knob on any
stage but the arithmetic.

**The rig.** `AllocationCases.Case` takes a `chain` (a `KatalystDsl`, built through the Kotlin door `KatalystBuilder`);
the engine rig registers it (`RegisterKatalyst`) and every note references it (`VoiceData.katalyst`). `katp` (renamed
from `katalyst`, which now would read as the chain) is the orbit's param map, `katpNext` the second map notes alternate
with, `duckTrigger` a sustained saw on orbit 1 for a duck to listen to.

**M2.** `E-arith-glide` moves every operand with a note every 32 blocks (gate 30 blocks), alternating two maps; the
second unsets the cap, so its arrival path runs too. The control, the same notes with constant operands, read 205 B on
the JVM and 439 B on V8 (one run), against the glide case's 205 and 450 to 456: the note path is most of the figure,
the glide adds nothing the JVM sees. No hook was needed: the JVM reads the note path the same in every run, so its line
sits 6 B above 205, and the mutants below show the line binds. The control is not kept as a case.

**The cases**, bytes per block (medians of 4 runs of 3 rounds on V8, rounds in brackets from runs 2 to 4; 9 rounds on
the JVM; the scoreboard holds runs 2 to 4, run 1 was a development run whose case list still had a control case) and µs per block (V8 with a 1 MB semi-space, so not comparable with the timing rows). The baseline
`E-sawC-sus` (one saw, classic chain) read 56 to 67 B and 6.3 µs on V8, 0 B and 3.5 µs on the JVM.

| case | chain | V8 B | V8 µs | JVM B | JVM µs | budget V8 / JVM |
|---|---|---|---|---|---|---|
| `E-arith` | times, plus, minus, div, pow, cap, floor, abs; constants | 10 to 52 (10 to 57) | 18.3 | 0 | 7.3 | 96 / 6 |
| `E-arith-glide` | the same over slots, notes every 32 blocks | 450 to 456 (444 to 461) | 21.9 | 205 | 9.2 | 500 / 211 |
| `E-distort` | `distort(0.3)`, 2x | 155 to 185 (154 to 186) | 15.8 | 0 | 9.1 | 232 / 6 |
| `E-comp` | compressor, then a limiter with 2 ms lookahead | 15 to 51 (-0.3 to 52) | 13.2 | 0 | 7.6 | 96 / 6 |
| `E-eq` | two bells and a tap | 26 to 57 (20 to 56) | 10.0 | 0 | 6.9 | 96 / 6 |
| `E-gain` | `gain(0.7)` | 45 to 57 (11 to 52) | 6.1 | 0 | 3.4 | 96 / 6 |
| `E-duck` | `duck(orbit = 1, depth = 0.6)` | 50 to 56 (41 to 73) | 9.2 | 0 | 5.2 | 96 / 6 |
| `E-parallel` | a distorted branch beside an empty one | 571 to 604 (571 to 619) | 21.1 | 0 | 11.8 | 664 / 6 |
| `E-bands` | three bands, the mids distorted | 809 to 840 (794 to 850) | 36.3 | 0 | 25.4 | 896 / 6 |
| `E-body-vowel` | `body(0.5, "wood")`, `vowel(0.5, "a")` | 16 to 56 (15 to 63) | 21.3 | 0 | 17.0 | 96 / 6 |

Every case sounds at the end of its window (the harness's peak check). `E-duck`'s peak includes its trigger orbit, so
for that case the check proves the engine sounds, not the ducked orbit.

**Two V8 allocations found, left open** (JVM 0 B for both; budgeted as they stand, a fix re-sets the lines; filed
as items 33 and 34 of `docs/tasks/engine-follow-ups.md`, the gliding `div`'s JVM time below as item 35):
- `KatalystParallelEffect.process` copies the bus into each branch with `copyInto`, which on Kotlin/JS makes a
  typed-array view per call (the sampling profiler: `subarray`, the largest site). With `copyRangeInto` (an
  experiment, restored) `E-parallel` read 206 to 243 B and `E-bands` 223 to 270.
- The bus distort's oversampled path: about 120 B per block (`E-distort` at `oversample = 0` read 46 to 73 B, an
  experiment, restored). The profiler names `DistortionCore.shapeRun`, the oversampler's `interpolate44` and
  `decimate44`, and a map lookup (likely `ScratchBuffers.oversample`'s `getOrPut` by factor); not located further.

**The proof that the rig measures the chain** (each mutant in `KatalystArithmeticEffect`, one lock call, the full check,
restored and compared with `cmp`):
- a `DoubleArray(8)` per block in the `times` stage only: `E-arith` 309 B on V8 (96) and 80 on the JVM (6),
  `E-arith-glide` 717 (500) and 285 (211); nothing else over.
- one boxed `Double` per block in the `times` stage only: the JVM red on both cases (24 B, 229 B), V8 under its lines
  (61, 465): below V8's resolution, as the budget file says.
- `applyLaw` a plain function instead of `inline`: V8 red on both (49,317 B, 55,236 B), the JVM red on
  `E-arith-glide` (11,149 B) and 0 B on `E-arith` (the JVM inlines it on the settled path).

**Timing rows** (`EffectBenchmark`, 44.1 kHz, a chain from `KatalystChainBuilder`, param state applied and the chain
processed every block; µs per block; the JVM varied up to 2x between runs, node did not):

The ranges come from filtered runs that write no record; the one committed record is the full check's
(`docs/benchmarks/2026-10-11_152249_*.md`), and it reads outside them in four rows: the empty chain 0.031 µs (JVM)
and 0.115 (node), the gliding `div` 0.473 (node), the eight stages 6.14 (node).

| row | JVM (3 runs) | node (2 runs) |
|---|---|---|
| empty chain (baseline: stereo copy and dispatch) | 0.07 to 0.08 | 0.15 to 0.17 |
| `times` (1 cheap stage) | 0.28 to 0.31 | 0.41 to 0.43 |
| `times`, `plus`, `max` (3 cheap stages) | 0.41 to 0.44 | 0.63 to 0.70 |
| `pow` | 3.4 to 8.0 | 3.9 to 4.2 |
| `div` gliding (the divisor moves every 8 blocks) | 1.8 to 3.7 | 0.39 to 0.43 |
| the eight stages of `E-arith` | 3.9 to 4.8 | 5.3 |

Against D5's scratch loop (one cheap stage about 50 ns, `pow` about 3.4 µs, JVM): `pow` agrees; a cheap stage reads
about 0.2 µs here above the baseline, through the real chain (the writer, the glide's advance, the dispatch). The gliding
`div` costs the JVM about eight cheap stages and node about one; not explained (the JVM rows are noisy, but it stayed at
1.8 µs or more in every run, and the full check's compare file flags it, JVM 3.88x slower than node).

**The cost of the check.** The alloc step took 2 min 33 s with 17 cases; with 27 one run takes about 5 min 15 s. The
time is per block (V8: 3 rounds of 220,000 blocks in two processes; JVM: 9 of 36,000), so grouping cases saves only the
node start-ups (six per case), a few seconds per case merged; by the measured µs, `E-bands` is about 35 s of a run and
the two arithmetic cases about 35 s together.

**The full check** (`console/full-check.sh`, 2026-10-11, on this tree): GREEN, 17,407 tests, corpus 18 of 18, alloc 54
of 54, 17 min 46 s (alloc 5 min 42 s). Its alloc run read inside the lines; three medians fell outside the ranges in
the table above: `E-distort` 191 B, `E-eq` 10 B and `E-bands` 799 B on V8.

**Also changed:** `KLANG_BENCH_FILTER` now runs the matching effect rows too (it ran the ignitor rows only), so
`KLANG_BENCH_FILTER=Katalyst` times the new rows on either platform.

