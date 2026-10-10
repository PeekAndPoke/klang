# The allocation harness in the repo, and one full check

Status: **in progress (2026-10-10): the design phase; the design and the case list go to the maintainer before the build.**

## Why

- **The V8 numbers only exist in scratch folders today.** These are the bytes per block that matter for the phone.
  - The harness lives in a session's scratch folder: node with `--trace-gc-nvp`, the two-process difference, the
    sampling heap profiler scripts.
  - A worker wiped that folder once, and each round rebuilt its tools.
- **The V8 wins are unguarded.** None of the wins in `engine-follow-ups.md` has a guard: the shaper and the limiter
  (items 9 and 3), the envelopes (10a and 10b), the knob reads (8 and 12). A later change could quietly bring the heap
  numbers back.
- **The JVM side is already guarded** in `jvmTest`: `FirstBlockAllocationSpec`, `KatalystResonatorAllocationSpec`.
- **The scores are scattered.** Each fix records its before and after table in its own task record, so no one place
  shows them all.

## The approach (maintainer, 2026-10-10)

- **Not inside kotest.**
  - The honest V8 number needs the production bundle, which the test tasks do not build.
  - It takes minutes per run, and its noise does not belong in the normal build.
  - Inside a test run the engine shares its process with thousands of other tests.
  - The fast, stable JVM guards stay in `jvmTest`.
- **One script that runs all the tests, then the benchmarks.** The maintainer: "A build script that runs all tests
  and all benchmarks would also work for me."

## The work

1. **One case list** in `audio_benchmark`'s common code, the same for both platforms:
   - the classic saw, sustained and releasing;
   - the unison stack, with and without analog;
   - the vibrato voice;
   - `whitecolor`;
   - the shaper;
   - the limiter;
   - the engine rows through `PlaybackEngineDispatcher`, among them a new note every 16 blocks.

   Start from the cases of `tmp/reviews/e8-proposal.md` and the e3, e9 and e10 reports.
2. **An allocation mode** beside the existing timing mode (real-time factor):
   - **JVM:** in-process, `ThreadMXBean.getThreadAllocatedBytes`, a warm-up, then rounds and medians.
   - **V8:** the production node bundle with `--trace-gc-nvp --max-semi-space-size=1`, two processes with different
     block counts, the difference divided by the extra blocks, medians of 3.

   The method is in `tmp/reviews/e8-proposal.md` §Method. The scratch tools to port are in the session scratchpad's
   `e8/tools/` and `e10-rB/tools/` folders, if they still exist; the method is enough without them.
3. **A budget file in the repo**, one line per case and platform. The budgets are generous: they only need to tell
   "about 0" from "one heap number per voice per block". Exceeding a budget fails the run and names the case.
4. **The scoreboard.** Every run writes one row to `docs/benchmarks/`: date, commit, bytes and time per case. It sits
   beside the existing `ledger.md`, so every fix leaves its score in one place.
5. **`console/full-check.sh`**, everything through `console/with-build-lock.sh`:
   - every test;
   - the corpus check;
   - the timing benchmarks;
   - the allocation check against the budgets.

   It ends in one summary: green or red, and what got worse. It runs before a release or a merge, not on every build.
   If CI comes later, the same script is the CI job.
6. **Tidying up:**
   - add a pointer from `audio/ref/performance.md` ("how to measure") and from `audio_benchmark/README.md`;
   - turn the task records' scattered tables into scoreboard rows only from the first run on, never back-filled.

## Constraints

- **Complexity is the enemy.**
  - The measurement must not be wired into `check` or `jvmTest`.
  - Use no Gradle plugins beyond what `audio_benchmark` already uses.
  - If the production bundle cannot be built and run without new wiring, stop and consult.
- **Block size stays 128 frames.**
- **The case list is fixed and learnable:** no random cases, no adaptive budgets.

## Design (proposal, for the maintainer)

Worker proposal, 2026-10-10. Nothing is built yet. The detail behind it (what was checked today and how, the cases
as code, what is ported from the scratch tools, sketches of the new files) is in
`tmp/reviews/alloc-harness-design.md`.

**In short:**
- 14 fixed cases, one list for both platforms, in `audio_benchmark`'s common code.
- The JVM measures in-process. V8 runs `audio_benchmark`'s own production bundle in node, with the e8 method.
- The bundle needs **no new Gradle wiring**. Checked today: it builds with an existing task and runs in node
  directly, with the GC flags.
- The driver is the benchmark's own JVM main, in an "alloc" mode.
- The budgets go in `audio_benchmark/alloc-budgets.txt`, the scoreboard in `docs/benchmarks/alloc-scoreboard.md`.
- `console/full-check.sh` has four steps, each in its own lock call. It takes about 15 to 20 minutes and exits 0 when
  green, 1 when red.

### 1. The cases

The cases run at 48 kHz, in 128-frame blocks, at 220 Hz, as in the records. "Voice" means one voice through
`VoiceFactory`. "Engine" means `PlaybackEngineDispatcher`, so the master stage and its limiter run too. The names
are the records' names, so a scoreboard row can be read against the reports.

| case | what it builds | why it is there | V8 now | V8 before the fix | JVM now | budget V8 / JVM |
|---|---|---|---|---|---|---|
| `saw` | voice: authored saw, no analog | the control | 16 B | (control) | 0 B | 64 / 12 |
| `sawC` | voice: `saw` under `classic()`, sustained | item 8, the knob reads | 21 B | 77 to 93 B | 0 B | 56 / 12 |
| `C-offrel` | voice: `classic()` saw inside a 1000 s release | 10b, the release arm | 21 B | 2,113 B | 0 B | 48 / 12 |
| `C-env3` | voice: `classic()` saw with amplitude, pitch and FM envelopes in decay | 10a, 10b, 10d's envelope part (all three hosts) | 93 B (before item 8) | 6,242 B | 0 B | 256 / 12 |
| `super7` | voice: supersaw, 7 voices, spread 0.3 | item 8, the unison stack | 0 B | 82 to 93 B | 0 B | 48 / 12 |
| `super7a` | voice: the same with analog 0.3 | item 8; item 7's drift ramp | 0 B | 94 to 113 B | 0 B | 48 / 12 |
| `sgpad-vib` | voice: `sgpad` with a vibrato | item 12 on the JVM (the ladder), item 8 on V8 | 48 B | 113 to 129 B | 0 B | 88 / 12 |
| `whitecolor` | voice: white noise, color 0.3 | item 3 (the noise stays clean); item 12's first JVM site | 0 B | (none) | 0 B (24 B in e3) | 48 / 12 |
| `C-dist` | voice: `classic()` saw, fused distort 0.5 | item 9 on the fused node | 31 B | about 1.6 KB | 0 B | 80 / 12 |
| `tube4x` | voice: saw, `distort(0.5, "tube", 4)` | item 9, the Shape node oversampled | 72 B | 8,247 B | 0 B | 256 / 12 |
| `C-full` | voice: `classic()` saw with distort, filter envelope, hpf, tremolo, analog | item 12 step 2 (48 B on the JVM before); holds item 29's open residue | 186 B | 273 to 346 B | 0 B | 320 / 12 |
| `E-sawC-sus` | engine: one `sawC` note, sustained | item 3, the limiter's level; item 8 | 68 B | 2,134 B | 0 B | 160 / 12 |
| `E-noisesaw-sus` | engine: saw plus white noise, loud enough to limit | item 3, the limiter's gain | 56 B (before item 8) | 4,108 B | 0 B | 192 / 12 |
| `E-C-rel05-n16` | engine: `classic()` saw, 0.5 s release, a new note every 16 blocks | 10b and item 8 across about 12 voices; item 11 (the build) | 1,045 B | 26,260 B | 377 B | 1,400 / 450 |

"V8 now" and "V8 before" are bytes per block, from the latest record of each fix (e3, e9, e10, e8 and its review,
mostly unpinned). The V8 times are in the detail file (0.7 µs for `saw` up to 35 µs for the n16 row). JVM times
are mostly not on record, so the first run sets them.

**One full run takes:**
- V8: about 2 minutes. That is 14 cases, 3 rounds and 2 processes each, calibrated today on 3 cases.
- JVM: under 1 minute, Gradle included.
- The bundle build: 22 s when only a little changed, up to about a minute.

### 2. How it runs

- **JVM:** `KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun`.
  - Every case runs in one process, a mixed profile, as e8 ran it; that profile is what found the megamorphic
    boxes.
  - Each case gets 9 rounds of 20,000 warm-up blocks and 16,000 measured blocks.
  - `ThreadMXBean.getThreadAllocatedBytes` brackets each measured window. The result is the median, and the max is
    printed beside it.
  - Checked today: an environment variable reaches the main through `jvmRun`.
- **V8:** the e8 method.
  - Per case and round, two node processes: 20,000 warm-up blocks, then 0 or 200,000 measured blocks. Node runs with
    `--trace-gc-nvp --max-semi-space-size=1 --min-semi-space-size=1`.
  - The bytes are the difference of the summed `allocated=` lines, divided by 200,000. The rounds are interleaved,
    and the result is the median of 3. The runs are unpinned.
  - The noise band is about 25 B either way per block.
- **What is ported:**
  - The probe core (cases, the voice and engine rigs, `measured`) becomes common code.
  - `run.js` becomes the JS main's alloc mode: it reads the case and the block counts from `process.env` and prints
    `NS ...`.
  - `measure.py` becomes about 40 lines in the JVM driver.
  - The JVM spec's measure mode moves into the JVM driver, outside kotest.
  - Not ported: the raw-double dumps, the heap profiler and JFR scripts (diagnostics for a person after a breach),
    the GC-bracketed group method, the hand-edited bundles, pinning. The list is in the detail file, section 5.
- **The bundle (the feasibility answer): no new wiring.**
  - `:audio_benchmark:compileProductionExecutableKotlinJs` (the task `jsNodeProductionRun` depends on) writes
    `audio_benchmark/build/compileSync/js/main/productionExecutable/kotlin/klang-engine-audio_benchmark.js`.
  - Built today under the lock, then run by node directly with the three GC flags: exit 0 on node 22 and on node 24,
    GC lines printed, no `NODE_PATH` needed.
  - Command-line arguments do not reach Kotlin (`main()` is called with none), but environment variables do. The
    bundle already reads `KLANG_BENCH_FILTER` that way, so the alloc mode reads its case the same way.
  - `jsNodeProductionRun` itself is not used: it would run the timing benchmark, and it takes no node flags without
    new build script code.
- **The driver: the benchmark's JVM main, in alloc mode.** It does four things, in this order:
  1. it measures the JVM cases in its own process;
  2. it spawns node for the V8 cases (`ProcessBuilder`, a regex over the GC lines);
  3. it compares both with the budget file;
  4. it appends the scoreboard rows, prints the result and exits 1 on a breach.

  Why this is the simplest:
  - the case list, the budgets and the scoreboard live in one Kotlin program, and the JVM half has to run in-process
    anyway;
  - it needs no Python, awk or second JavaScript tool;
  - it needs no new Gradle task;
  - bash only sequences the steps in `full-check.sh`.
- **The node: system `node` (22.12 here), with its version written into every scoreboard row, and `KLANG_NODE` to
  point at another one.**
  - All allocation records were taken on node 22.12. Gradle's node (24.10, used by the timing benchmark) has no
    stable path without new wiring.
  - Measured today: the bytes agree on both nodes (`sawC` 15.8 / 15.9 B, `super7a` -0.1 / -0.1 B, n16
    1,066 / 1,054 B). Only the time differs: `sawC` is 14 percent slower on node 24.

### 3. The budget file

- **Where:** `audio_benchmark/alloc-budgets.txt`.
- **Format:** plain text, one line per case and platform: `case  platform  budget  guards`. The budget is in bytes
  per block, and `#` starts a comment.
- **Checks:** a case without a line, or a line naming an unknown case, turns the run red, so the two lists stay in
  step.
- **How the budgets are chosen:** by hand, once, and never by a run.
  - A budget sits about 40 B or more above today's figure, so the noise band does not trip it.
  - It sits below the regression the case guards: one heap number per sample (about 2 KB per voice and block,
    items 3, 9, 10), or one per knob read (60 to 100 B per voice and block, item 8).
  - Where today's band and item 8's old figure overlap (`C-full`, `E-sawC-sus`), the budget guards only the
    per-sample class, and the voice rows guard item 8.
  - On the JVM, 12 B tells 0 from one `Double` per block (24 B).
- **The V8 limit, said plainly:** the method's noise (about 25 B) is larger than one heap number (16 B on node). So
  on V8 the budgets tell "about 0" from "one heap number per knob read or per sample", but not from a single new
  heap number per block. Only the JVM sees that one.
- **A breach** prints one line per case, for example `OVER sawC v8: 112 B per block, budget 56 (item 8, the knob
  reads)`, with the guard text from the file. The run still writes its scoreboard rows, then exits 1.

### 4. The scoreboard

- **Where:** `docs/benchmarks/alloc-scoreboard.md`, beside `ledger.md`.
- **Who writes it:** the driver. Rows are appended, never edited by hand, and never back-filled from the reports.
- **A row:** `date | commit | machine | platform | runtime | one cell per case`. A cell is the median bytes and µs
  per block, for example `21 B 1.34`. A breach carries a `!`. The commit comes from `git describe --always
  --dirty`, as in the ledger.
- **Two rows per run**, one for V8 and one for the JVM: 14 cases with both platforms in one row would be 28 cells
  wide.
- **If the case list ever changes,** a new table starts under a dated heading.

### 5. `console/full-check.sh`

Each step is its own `console/with-build-lock.sh` call, so a waiting agent waits for one step, not for 20 minutes.

| step | command | time |
|---|---|---|
| tests | delete the old test XML with `find ... -name '*.xml' -delete` (so every test task runs again and the counts are this run's), then `./gradlew allTests :klangscript-ksp:test --continue` | about 5 minutes when nothing needs compiling (2 min 21 s today with half the tasks up to date), up to about 10 after a big change |
| corpus | `./gradlew runSongBenchmark --args=corpus` | about 3 minutes |
| timing | `console/run-dsp-benchmarks.sh`, unchanged | about 3 minutes (the JVM run 128 s, the JS run 45 s, today) |
| alloc | `./gradlew :audio_benchmark:compileProductionExecutableKotlinJs`, then `KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun`, in one lock call | about 3 to 4 minutes |

- **The tests:** `allTests` covers every module but `:klangscript-ksp`, which has a plain `test`. Today: 17,340
  tests, all green.
- **The total:** about 15 minutes, up to 20 when much needs compiling.
- **The tree guard:** the script records `HEAD` and a hash of `git status --porcelain` before and after. If they
  differ, the run is red, because its results would mix two states of the tree.
- **The summary,** one line per step and one verdict:

  ```
  Klang full check, 2026-10-11 10:42, 0ba63a33-dirty
    tests   GREEN  17,340 tests, 0 failed                         5m 02s
    corpus  GREEN  18 of 18 songs identical to the reference      2m 41s
    timing  DONE   docs/benchmarks/2026-10-11_104733_compare.md   3m 10s
    alloc   RED    1 of 28 over budget                            3m 25s
            OVER sawC v8: 112 B per block, budget 56 (item 8, the knob reads)
  RED. What got worse: alloc sawC on v8.
  ```

  "What got worse" lists the failed test classes, the changed songs and the cases over budget. Timing is never red:
  it has no budgets, so its noise cannot fail a run. The compare file's "JVM slower than node" flags are printed as
  information.
- **The exit code:** 0 when every step is green. 1 otherwise: a failed test, a changed song, a breach, a step that
  could not get the lock, or a tree that changed during the run.
- **The corpus check, made tracked.** The scratch spec renders the corpus in `jvmTest` behind `CORPUS_LABEL`. It
  reaches the internal `InlineDslRegistrar` and the renderer's private `mix` by reflection, and writes into the
  ignored `tmp/naming/`. The tracked version:
  - **Where it lives:** `src/jvmMain/kotlin/CorpusCheck.kt`, started by the existing `runSongBenchmark` task with
    `--args=corpus` (check) or `--args=corpus-accept` (rewrite the reference). Not a spec, so it sits in no test
    task.
  - **How it renders:** through `KlangOfflineRenderer.render`, which is public and already does what the spec does
    by hand (phase pool seed 1, the inline DSL registration, samples, a 2 s tail plus the limiter's latency). So no
    reflection is needed.
  - **The corpus:** the same as today: the built-in songs plus the two frozen songs and the frozen piece (18 today),
    256 cycles, 48 kHz, the wall-clock seeds pinned to `pure(0.5)`.
  - **Per song:** a SHA-256 of the raw doubles of the engine's output after the master (what is heard), and the
    peak.
  - **The reference output:** `docs/benchmarks/corpus-reference.md`, one line per song and the commit it was
    accepted on. A commit that changes the sound on purpose carries its new reference, so that file's history lists
    every deliberate sound change.
  - **The check:** it prints identical, CHANGED, MISSING or NEW per song, and exits 1 on any difference.

### 6. What it deliberately does not do

- It is not wired into `check`, `jvmTest` or `allTests`.
- It adds no Gradle plugin and no Gradle task. It uses `allTests`, `jvmRun`,
  `compileProductionExecutableKotlinJs` and `runSongBenchmark`, which all exist.
- It does not replace the JVM guards in `jvmTest` (`FirstBlockAllocationSpec`, `KatalystResonatorAllocationSpec`).
- It does not measure in Chrome. Node's V8 stands in for the phone: a heap number is 16 B on node and 12 B in
  Chrome, and the count is the same.
- It has no random or adaptive cases, never updates a budget by itself, and has no mixed profile on V8 (one case
  per process).
- It does not pin to a core, and it does not locate a box. Locating stays a person's job, with the heap profiler,
  `--trace-turbo-inlining` and JFR. The recipe goes into `audio/ref/performance.md`.
- It does not run on CI; if CI comes, the same script is the job.

### Open questions for the maintainer

1. **Which node?** The proposal is system node: all allocation records were taken on it, and its version goes into
   every row. The alternative is Gradle's node 24, the one the timing benchmark uses. The bytes agreed on both
   today; the times did not.
2. **V8 resolution.** The budgets cannot see a single new heap number per block on V8 (the noise is about 25 B,
   one heap number is 16 B). Is that enough? A 1,000,000-block window would spread each process's fixed error over 5
   times the blocks, at about 5 times the V8 time (about 10 minutes). Not measured.
3. **The bundle switch.** The records were taken on `audio_be`'s test bundle; the harness uses `audio_benchmark`'s
   main bundle (same compiler, no kotest). The proposal: in the build phase, cross-check three cases on both
   bundles, and confirm every budget against the harness's first run before it is committed. A budget that does not
   fit is set again by hand, once.
4. **220 Hz or a frequency that is not an integer?** 220 Hz is a small integer, so it hides one class of box: at
   261.63 Hz the records read 10 to 15 B more per voice. The proposal keeps 220 Hz so the rows can be read against
   the records. Moving every case to middle C is the honest alternative, since the scoreboard starts fresh anyway.
5. **Two scoreboard rows per run** (V8 and JVM) instead of the one row the task names. Is that all right?
6. **Every alloc run writes its rows,** also from a dirty tree, as the ledger does. Or should only the full check
   write them?
7. **The corpus reference.**
   - The tracked check hashes the output after the master, so its hashes are new and cannot be compared with the
     files in `tmp/naming/`. Its first reference should be taken on a commit where the scratch spec still reads the
     accepted hashes. After that, the scratch spec's owner retires it.
   - The corpus needs the sample cache (`./cache`) or the network. A sample that fails to load changes a song's
     hash.
   - It runs as a suite of `runSongBenchmark`, a task whose name says benchmark. Is that acceptable, or is a
     subcommand of `runCli` preferred?
8. **Force every test to run** (delete the old XML first, which costs about 3 minutes), or let Gradle skip test
   tasks whose inputs did not change?
9. **The JVM's mixed profile is process-dependent.** The records show one process reading 0 B and another 72 B on
   the same case (item 12, before step 2). A future regression of that kind could breach in one run and not the
   next. The median of 9 rounds damps this, but does not remove it.
