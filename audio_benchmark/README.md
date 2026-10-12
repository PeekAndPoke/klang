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

### One family of cases only

`KLANG_BENCH_FILTER` runs just the ignitor and effect cases whose name contains it and skips
everything else, which is what an A/B wants (a pair of rows, a minute, both platforms):

```bash
KLANG_BENCH_FILTER=guitar-rig ./gradlew :audio_benchmark:jsNodeProductionRun
KLANG_BENCH_FILTER=Katalyst ./gradlew :audio_benchmark:jvmRun   # the Katalyst stage rows
```

### The allocation check (bytes per block)

Beside the timing (real-time factor) there is an allocation mode: 30 fixed cases (`AllocationCases.kt`, common
code, middle C), measured on the JVM in-process and on V8 in node, and compared with the budget file
`alloc-budgets.txt`. Thirteen render voices without the engine (`C-remap` two, taking turns); sixteen run the engine (`PlaybackEngineDispatcher`, the master stage
included), ten of them with an authored Katalyst chain on the orbit: the case registers it
(`KlangCommLink.Cmd.RegisterKatalyst`) and its notes reference it by name (`VoiceData.katalyst`), as a song's do. One, `S-doors`, runs
sprudel's per-event path (a one-note pattern with voice and bus doors, `toVoiceData`, the wire encode on JS) and
renders no sound: its flag `makesSound = false` makes the driver check the outputs of its last step, not a peak. The
chains are built through the Kotlin door (`KatalystBuilder`). `E-arith-glide` moves its chain's slots with notes every
32 blocks that alternate between two `katp` maps, so its figure includes the note path's (one new voice per 32
blocks).
One case, `C-remap`, takes its voices through `receivedByEngine` (`engine_inbox.kt`): on JS the
generated wire codec, the path every voice takes into the worklet, so a change of the decoder's map type shows in
its bytes; on the JVM the voice as it is.

```bash
./gradlew :audio_benchmark:compileProductionExecutableKotlinJs --rerun   # the node bundle the V8 half runs
KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun                  # exit 1 on a breach
```

The bundle must be newer than every `commonMain`, `jsMain` and `main` source of the modules it is built from,
otherwise the V8 half would measure old code and the run is red with a `FAIL v8: the bundle is older than ...` line.
`--rerun` relinks the bundle (about 15 s), so its time is fresh even when Gradle saw no change (a touched file).
The module list is kept by hand (`AllocationCheck.kt`): it does not see a new module dependency or source set, a
change to the build (`buildSrc`, `gradle.properties`, a `build.gradle.kts`), or a source whose old time came back
(`cp -p`, `touch -r` after a mutation). `console/full-check.sh` always relinks right before the check, so the gap
matters only for a standalone run: relink first.

- **JVM:** every case in one process (a mixed profile), 9 rounds of 20,000 warm-up and 16,000 measured blocks,
  `ThreadMXBean` around the window; the median, the max beside it.
- **V8:** the JVM driver starts node (`KLANG_NODE`, or the system `node`) on
  `build/compileSync/js/main/productionExecutable/kotlin/klang-engine-audio_benchmark.js` with
  `--trace-gc-nvp --max-semi-space-size=1 --min-semi-space-size=1`: two processes per case and round (20,000
  warm-up, then 0 or 200,000 blocks), the difference of their summed `allocated=` lines over 200,000; 3 interleaved
  rounds, the median. The bundle's alloc mode runs one case from `KLANG_ALLOC_CASE`, `KLANG_ALLOC_WARM` and
  `KLANG_ALLOC_COUNT` and prints `NS <ns per block>`.
- **The budget file** `alloc-budgets.txt`: one line per case and platform (`case platform budget guards`), set by
  hand, never by a run; its header says how the numbers were chosen and the two limits (V8 cannot see a single new
  heap number per block; the JVM's mixed profile is process-dependent). A case without a line, or a line naming an
  unknown case, is red.
- **The output:** a table, one `OVER <case> <platform>: ...` line per breach naming what the budget guards, and a
  `RESULT GREEN` or `RESULT RED` line. Every run appends two rows (V8, JVM) to
  `docs/benchmarks/alloc-scoreboard.md`, with the node version and `git describe --always --dirty`.
- **A measurement that saw nothing is red, never 0 B:** a node process without `allocated=` lines, a missing `NS`
  line, a case whose last block is silent (peak below 1e-4: it no longer exercises its path), a node that runs past
  120 s (it is killed with its children, and the V8 half stops), a `node --version` that gives no answer within
  10 s, a JVM case that runs past 120 s in one round (the check stops), a JVM that does not count thread
  allocations. Each is a `FAIL` line naming the case and the cause. A true 0 B (the window allocating no more than
  its base, inside the noise) is green.
- **After a breach:** the check names the case, not the box. `audio/ref/performance.md` ("Measuring allocations")
  has the recipe for locating it.

`console/full-check.sh` runs this as its `alloc` step, after every test, the corpus check and the timing run.

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

The effect section (`EffectBenchmark.kt`) times the effect classes one by one, and the Katalyst arithmetic stages as an
orbit runs them: a chain built by `KatalystChainBuilder`, its param state applied and the chain processed every
block. Subtract its "Katalyst empty chain" row (the stereo copy and the dispatch) for the stages alone.

Results are sorted most expensive first.

## Saving results

Copy the Markdown output to `docs/benchmarks/YYYY-MM-DD-platform.md` for historical reference.
Compare across runs to catch regressions.
