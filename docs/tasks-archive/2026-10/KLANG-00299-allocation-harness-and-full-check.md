# KLANG-00299: The allocation harness in the repo, and one full check

> **Status (2026-10-10): DONE.**

Status: **done (2026-10-10): built, proven and reviewed in three rounds, the third clean (0 MAJOR). Archived 2026-10-11
(maintainer).** Run it: `console/full-check.sh` (`--fresh` forces every test task); the allocation step
alone is in `audio_benchmark/README.md`.

## Why

- **The V8 numbers only exist in scratch folders today.** These are the bytes per block that matter for the phone.
  - The harness lives in a session's scratch folder: node with `--trace-gc-nvp`, the two-process difference, the
    sampling heap profiler scripts.
  - A worker wiped that folder once, and each round rebuilt its tools.
- **The V8 wins are unguarded.** None of the wins in KLANG-00290 (engine follow ups) has a guard: the shaper and the limiter
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
| `super7a` | voice: the same with analog 0.3 | item 8, the unison stack with drift (its ramp never boxed: item 7) | 0 B | 94 to 113 B | 0 B | 48 / 12 |
| `spluck-a` | voice: superpluck, 8 voices, analog 0.3, feedback 1 | item 7, the drift ramp's holder | 15 to 40 B | 407 to 566 B | 0 B | 96 / 6 |
| `sgpad-vib` | voice: `sgpad` with a vibrato | item 12 on the JVM (the ladder), item 8 on V8 | 48 B | 113 to 129 B | 0 B | 88 / 12 |
| `whitecolor` | voice: white noise, color 0.3 | item 3 (the noise stays clean); item 12's first JVM site | 0 B | (none) | 0 B (24 B in e3) | 48 / 12 |
| `C-dist` | voice: `classic()` saw, fused distort 0.5 | item 9 on the fused node | 31 B | about 1.6 KB | 0 B | 80 / 12 |
| `tube4x` | voice: saw, `distort(0.5, "tube", 4)` | item 9, the Shape node oversampled | 72 B | 8,247 B | 0 B | 256 / 12 |
| `C-full` | voice: `classic()` saw with distort, filter envelope, hpf, tremolo, analog | item 12 step 2 (48 B on the JVM before); holds item 29's open residue | 186 B | 273 to 346 B | 0 B | 320 / 12 |
| `C-remap` | voice: two `sawC` voices with equal orbit delay maps, received as the engine receives them (on V8 through the wire codec), taking turns as their orbit's owner | item 2, the decoder's `KlangMap` (a new orbit map per block; not item 8: the mutant read median 160 B) | 77 to 88 B (medians; rounds to 150) | 1,128 to 1,164 B | 24 B | 192 / 36 |
| `E-sawC-sus` | engine: one `sawC` note, sustained | item 3, the limiter's level; item 8 | 68 B | 2,134 B | 0 B | 160 / 12 |
| `E-noisesaw-sus` | engine: saw plus white noise, loud enough to limit | item 3, the limiter's gain | 56 B (before item 8) | 4,108 B | 0 B | 192 / 12 |
| `E-delay` | engine: one `sawC` note, the orbit delay (wet 0.3, time 0.2501, feedback 0.45) | item 1, the orbit delay's sites as a whole (its parts: the KDocs) | 62 to 67 B | 160 to 171 B | 0 B | 112 / 6 |
| `E-stages` | engine: one `sawC` note, the delay, the reverb and the phaser (floor 0.5) | item 1: a revert of everything, the delay's sites with all three running, the per-sample class in the reverb and the phaser; the reverb's and the phaser's per-block sites only in part (a revert of them read 93 to 129 B, medians; their KDocs) | 11 to 51 B | 217 to 259 B | 0 B | 96 / 6 |
| `E-C-rel05-n16` | engine: `classic()` saw, 0.5 s release, a new note every 16 blocks | 10b and item 8 across about 12 voices; item 11 (the build) | 1,045 B | 26,260 B | 377 B | 1,400 / 450 |

Added 2026-10-11 (engine follow-up 7): `spluck-a`, the fifteenth case, and `super7a`'s guard corrected; its numbers are
that change's (KLANG-00290 (engine follow ups), item 7). The other rows are as designed; the budgets in force are
`audio_benchmark/alloc-budgets.txt`.
Added 2026-10-11 (engine follow-up 1): `E-delay` and `E-stages`, the sixteenth and seventeenth cases; their numbers are
that change's harness medians (KLANG-00290 (engine follow ups), item 1); `E-stages` as re-set in its review round 1.
Added 2026-10-11 (engine follow-up 2, step 1): `C-remap`, the eighteenth case on its branch, with an `expect` helper of its own,
`receivedByEngine` (`audio_benchmark/.../engine_inbox.kt`: the generated wire codec on JS, the voice as it is on the
JVM); its numbers are that change's rounds, "before" the decoder reverted (KLANG-00290 (engine follow ups), item 2).
Its JVM line is 36, not 6: the case reads 24 B, the reverb writer's documented `Double?` lowpass per new map. Adding
it moved `E-C-rel05-n16` on the JVM from 409 to 414 B (5 of 5 runs; 409 with only `C-remap` taken out, review round 1):
the shared process's mixed profile, under its 450.
Added 2026-10-11 (Katalyst stage measurements): ten engine cases with an authored Katalyst chain, `E-arith` to
`E-body-vowel`, 27 cases in all on its branch; what each builds, guards and reads:
KLANG-00301 (katalyst stage measurements).
With both branches merged (2026-10-11, `klang-map`) and `E-C-n1` (engine follow-up 2, step 3), 29 cases.

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

### Decided (maintainer, 2026-10-10: "Yes, build it", with the coordinator's answers)

1. **Node:** the system node, its version in every row. `KLANG_NODE` points at another one.
2. **V8 resolution:** accepted as it is, with the 200,000-block window. The V8 budgets catch one heap number per
   sample or per knob read; the JVM catches a single box.
3. **The bundle switch:** cross-check three cases on both bundles in the build phase, then confirm every budget
   against the harness's first run before it is committed.
4. **Frequency:** middle C, 261.63 Hz, for every case. The scoreboard starts fresh, so the old 220 Hz records are not
   the reference.
5. **Two scoreboard rows per run** (V8 and JVM).
6. **Every alloc run writes its rows**, a dirty tree marked dirty, as the ledger does.
7. **The corpus check** runs under `runSongBenchmark` (no new task). Its first reference is taken on a commit where
   the scratch spec still confirms the accepted sound (v0.6.3: `tmp/naming/corpus-merge063.txt`); the coordinator
   then retires the scratch spec.
8. **Tests:** Gradle skips test tasks whose inputs did not change; `full-check.sh --fresh` forces every test task to
   run.
9. **The JVM's process-dependent profile:** accepted and noted in the budget file's header.

## Built (worker, 2026-10-10, branch `alloc-harness` at `d7a455e4`, uncommitted)

### What is in the tree

| file | what |
|---|---|
| `audio_benchmark/src/commonMain/kotlin/AllocationCases.kt` | the 14 cases (the design's names), middle C (261.6255653 Hz), the voice rig and the engine rig, public API only |
| `audio_benchmark/src/jsMain/kotlin/allocation_mode.kt`, `main.kt` | the JS alloc mode: `KLANG_ALLOC_CASE`, `KLANG_ALLOC_WARM`, `KLANG_ALLOC_COUNT` from the environment, prints `NS <ns per block>` |
| `audio_benchmark/src/jvmMain/kotlin/AllocationCheck.kt`, `main.kt` | the driver, `KLANG_BENCH_MODE=alloc`: JVM in-process (9 rounds), V8 by spawning node (3 interleaved rounds, two processes), the budgets, the scoreboard, `OVER` lines, exit 1 |
| `audio_benchmark/alloc-budgets.txt` | the 28 budgets, set by hand from the first run; the header notes decisions 2 and 9 |
| `docs/benchmarks/alloc-scoreboard.md` | the scoreboard, two rows per run |
| `src/jvmMain/kotlin/CorpusCheck.kt` (+ a branch in `SongBenchmarkMain.kt`) | the tracked corpus check, `runSongBenchmark --args=corpus` and `--args=corpus-accept`, through `KlangOfflineRenderer`, no reflection |
| `docs/benchmarks/corpus-reference.md` | the first reference, 18 songs |
| `console/full-check.sh` | the four steps, each in its own lock call, `--fresh`, the tree guard, the summary, exit 0 or 1 |
| `audio/ref/performance.md` ("Measuring allocations"), `audio_benchmark/README.md` | how to measure, and how to locate a box after a breach |

No Gradle task, no plugin, nothing wired into `check`, `jvmTest` or `allTests`.

### The bundle cross-check (decision 3)

`sawC`, `super7a` and `E-C-rel05-n16` on `audio_be`'s production TEST bundle (the e8-r1B probe, its `c4:` cases,
installed and removed again under the lock) and on the harness bundle, the e8 method, node 22.12, 3 interleaved
rounds, unpinned, the median:

| case | test bundle | harness bundle |
|---|---|---|
| `sawC` | 30.6 B, 1,288 ns | 31.0 B, 1,292 ns |
| `super7a` | 15.9 B, 3,983 ns | 14.4 B, 4,014 ns |
| `E-C-rel05-n16` | 1,318 B, 33,730 ns | 1,293 B, 33,748 ns |

The two bundles agree inside the noise band (2 B on the voices, 25 B or 2 percent on the n16 row).

### The first run (the budgets were set from it)

Node 22.12.0, JVM 17.0.17, unpinned, median bytes per block (the max beside it) and µs per block:

| case | V8 B (max) | V8 µs | V8 budget | JVM B (max) | JVM µs | JVM budget (12 until review round 1) |
|---|---|---|---|---|---|---|
| `saw` | 30 (36) | 0.73 | 72 | 0 (0) | 0.25 | 6 |
| `sawC` | 42 (43) | 1.39 | 80 | 0 (0) | 0.77 | 6 |
| `C-offrel` | 31 (32) | 2.38 | 72 | 0 (0) | 1.30 | 6 |
| `C-env3` | 61 (72) | 7.66 | 160 | 0 (0) | 4.69 | 6 |
| `super7` | 17 (26) | 3.17 | 64 | 0 (0) | 1.68 | 6 |
| `super7a` | 21 (27) | 4.09 | 64 | 0 (0) | 2.13 | 6 |
| `sgpad-vib` | 67 (68) | 4.82 | 112 | 0 (0) | 2.51 | 6 |
| `whitecolor` | 17 (20) | 1.15 | 64 | 0 (0) | 0.41 | 6 |
| `C-dist` | 51 (56) | 1.90 | 96 | 0 (0) | 1.11 | 6 |
| `tube4x` | 72 (83) | 5.64 | 160 | 0 (0) | 3.40 | 6 |
| `C-full` | 253 (259) | 7.67 | 320 | 0 (0) | 5.77 | 6 |
| `E-sawC-sus` | 83 (83) | 5.78 | 160 | 0 (0) | 3.07 | 6 |
| `E-noisesaw-sus` | 77 (83) | 7.48 | 160 | 0 (0) | 4.07 | 6 |
| `E-C-rel05-n16` | 1,293 (1,320) | 34.70 | 1,500 | 409 (414) | 18.56 | 450 |

At middle C the voice rows read about 10 to 25 B more on V8 than the 220 Hz records (the `saw` control 30 B against
16 B), as the records predicted. Across the five runs of this build phase the V8 medians moved by up to about 15 B on
a voice row (`sawC` 26 to 42, `saw` 24 to 41). `C-full` moves the most: about 90 B, medians 164 to 253 across
the build phase and review round 1, single rounds 139 to 273 (reviewer B); its budget of 320 still holds. The JVM
did not move.

### The harness's own mutation check

Each in ONE lock call: back up with `cp`, mutate, build, run the check, restore with `cp`, verify with `cmp`. The
scoreboard was backed up and restored too, so a mutant's rows are not in the record.

1. **Item 8 regression** (`Ignitors.readParam` a plain function again): **RED, exit 1**, 7 of 28 over budget, all
   on V8: `sawC` 93 B (budget 80), `C-offrel` 82 (72), `super7` 77 (64), `super7a` 91 (64), `sgpad-vib` 145 (112),
   `C-dist` 123 (96), `E-C-rel05-n16` 2,055 (1,500). As designed, `E-sawC-sus` (145, budget 160) and `C-full`
   (304, budget 320) guard the per-sample class only and stayed under. Restored, `cmp` identical.
2. **A per-block `Double` box** planted in `WhiteNoiseIgnitor.generate` (a field of type `Any?` written once per
   block): **RED, exit 1**, `whitecolor` jvm 24 B (budget 12) and `E-noisesaw-sus` jvm 24 B (budget 12), exactly the
   two cases with white noise. V8 read `whitecolor` 35 B, inside its budget: the V8 limit of decision 2, as said.
   Restored, `cmp` identical.
3. **The budget file:** the `sawC v8` line deleted and a line for an unknown case added: **RED, exit 1**,
   `MISSING budget: sawC v8 ...` and `UNKNOWN case in budget: ... "nosuchcase" ...`. Restored, `cmp` identical.

The JFR recipe of `audio/ref/performance.md` was tried on mutant 2 (same lock discipline): the recording's
`java.lang.Double` stacks named `WhiteNoiseIgnitor.generate`, the planted line. The heap profiler snippet was run on
the harness bundle as written.

### The corpus

- **v0.6.3's sound first:** the scratch spec (`CORPUS_LABEL=harness-base`) on this tree: `tmp/naming/corpus-harness-base.txt`
  is byte-identical to `tmp/naming/corpus-merge063.txt`, 18 of 18.
- **`corpus-accept`**, then **`corpus` three times:** 18 of 18 identical each time (175, 176, 175 s inside the
  JVM, about 177 s wall).
- **A finding:** the tracked hashes are not new, against open question 7's expectation. They equal the scratch
  spec's `raw` column, 18 of 18 (hash and peak). The master stage processes the mix in place (DC blockers, limiter)
  and only then clips into the output, so the private `mix` the scratch spec read was already the post-master signal,
  and no song's peak reaches the clip. A song that clips would differ.
- Each of the 4 render workers owns its own `Samples`: its caches are plain maps, and the scratch spec's mutex is not
  reachable inside `KlangOfflineRenderer`.

### Run times (this machine)

| what | wall |
|---|---|
| the allocation step (bundle up to date, JVM half and V8 half) | 1 min 38 s; 2 min 18 s to 2 min 30 s with a rebuild after a mutation |
| the corpus check | 3 to 4 min (175 s of render here, 221 to 224 s on reviewer B's runs) |
| the scratch corpus spec | about 3 min 13 s |
| the cross-check (3 cases, 2 bundles, 3 rounds) | 1 min 7 s |

### The full check, on the final tree

`console/full-check.sh --fresh` (every test task ran again: all 1,475 test XML files were newer than the start),
exit 0:

```
Klang full check, 2026-10-10 20:12, d7a455e4-dirty, --fresh
  logs    build/full-check/2026-10-10_201258
  tests   GREEN  17,372 tests, 0 failed                             3m 48s
  corpus  GREEN  18 of 18 songs identical to the reference          5m 15s
  timing  DONE   docs/benchmarks/2026-10-10_202201_compare.md       1m 20s
          information: the JVM is slower than node on 3 of the compare file's rows
  alloc   GREEN  28 of 28 within budget                             1m 47s
GREEN. Total 12m 10s.
```

The corpus step's 5 min 15 s includes recompiling the root module and `audio_be` after the mutation checks; its
render is 3 to 4 min. The run left its timing files (`docs/benchmarks/2026-10-10_202201_*.md`) and its two
scoreboard rows; whether they are committed is the coordinator's call.

### Choices and doubts for the coordinator

- **The frequency** is middle C to the double (`261.6255653005986`), the value of the records' `c4:` cases; the
  docs say 261.63 Hz.
- **The tree guard** hashes `git status --porcelain` together with each listed file's content (`git hash-object`),
  so an edit to a file that was already dirty also counts, and it leaves `docs/benchmarks/` out, where the run writes
  its own scoreboard row and timing files.
- **A failed timing step** (it could not get the lock, or it crashed) turns the run red; its numbers never do.
- **Budgets near item 8's class:** `sawC` sits at 80 with today's medians between 26 and 42 and the mutant at 93, so
  the margins are about 40 B below and 13 B above. The mutant proved it; a noisier machine may want a look.

### Review round 1 (2026-10-10): what changed

Reviews `tmp/reviews/alloc-r1-A.md` (0 MAJOR, 8 MINOR, 6 NIT) and `tmp/reviews/alloc-r1-B.md` (1 MAJOR, 3 MINOR,
5 NIT). Fixed:

- **A measurement that saw nothing is red, never 0 B** (B1, A5). A node window with no `allocated=` lines, or no
  more bytes than its base, or no `NS` line, or an `NS` of 0, is a `FAIL <case> v8` naming the cause, with the tail
  of the node's output. A JVM without thread allocation accounting (or a -1 read) is a `FAIL jvm`. Proven with
  reviewer B's wrapper (`allocated=` renamed): 14 `FAIL ... no allocated= lines` and `RESULT RED`, exit 1.
- **A stale bundle is refused** (A6, B2): the bundle must be newer than every `commonMain`, `jsMain` and `main`
  source of `audio_benchmark`'s compile closure (14 modules, read from the build scripts). A touch alone leaves
  Gradle's bundle as it was, so the build command is `compileProductionExecutableKotlinJs --rerun` (a relink, about
  15 s), which `full-check.sh` now uses too. Proven: a touched `IgnitorDefaults.kt` gave `FAIL v8: the bundle is
  older than ...`, exit 1.
- **A timeout on every node process** (A7, B N4): 600 s, then the child is killed and the case is a `FAIL`. Proven
  with a node that sleeps and the bound cut to 5 s by a mutation: 14 `FAIL ... timeout`, exit 1, no process left.
- **`full-check.sh`:** `alloc` and `corpus` are green only with their own `RESULT GREEN` or `CORPUS GREEN` line
  (A8); the test XML is read inside the tests step's lock call, the tree state before inside the first lock call and
  after inside the last one (A1, A4); the XML is searched only in the root's and the top-level modules' build dirs,
  never in `.claude/worktrees/` or a top-level folder holding a `.git` (A2); the tree guard leaves out only the
  scoreboard and the timing step's dated files, so `corpus-reference.md` is guarded (A3); paths are read with
  `--porcelain -z` (A NIT 1); an empty "What got worse" points at the logs (A NIT 6); the header says it needs Linux
  tools (B N5).
- **The corpus check without samples** (B3): the default loader below the disk cache notes every file it could not
  fetch; any such file makes the run red with `CORPUS RED: N sample files could not load (no ./cache and no
  network?), so no song was compared`, before any comparison, and an accept is refused. Proven with no cache and no
  network (`unshare -rn`): exactly that line, 7 files, exit 1.
- **The JVM budgets are 6 B** where the first run read 0 (B N1): a box every other block (12 B) is caught.
- **Guard texts** (B N2): the n16 V8 line names item 3, the limiter; `super7a jvm` says item 7's drift ramp is V8
  only.
- **NITs:** braces on the corpus `when` arms, one environment reader in `jsMain`, `phasePoolSeed = 1` in the engine
  rig.

**The JVM's limit, recorded (decision 9):** reviewer B routed the ladder's constant reads through a nullable read
(item 12's class). The JVM read 0 B: C2 optimised the box away, while V8 read one more heap number on `sgpad-vib`
(under its budget). So the JVM half has not been shown to see item 12's process-dependent box. That is the accepted
limit of decision 9, now also said in the budget file's header.

**The full check after round 1** (`console/full-check.sh`, not `--fresh`: every test task was up to date, the counts
read inside the tests step's lock call), exit 0:

```
Klang full check, 2026-10-10 21:13, d7a455e4-dirty
  logs    build/full-check/2026-10-10_211305
  tests   GREEN  17,372 tests, 0 failed                             0m 12s
  corpus  GREEN  18 of 18 songs identical to the reference          3m 42s
  timing  DONE   docs/benchmarks/2026-10-10_211659_compare.md       1m 19s
          information: the JVM is slower than node on 1 of the compare file's rows
  alloc   GREEN  28 of 28 within budget                             1m 59s
GREEN. Total 7m 12s.
```

`tree.before` and `tree.after` were identical. The run left its timing files (`docs/benchmarks/2026-10-10_211659_*.md`)
and two scoreboard rows (the scoreboard now holds three runs: the first, the `--fresh` full check, this one).

### Review round 2 (2026-10-10): what changed

Review `tmp/reviews/alloc-r2.md` (1 MAJOR, 2 MINOR, 4 NIT). Fixed:

- **MAJOR 1, a true 0 B read as a failure:** round 1's `window <= base` clause is gone; the per-process noise is
  larger than the window's bytes when a case allocates about 0, so the window's sum falls below the base's about
  half the time. The "no `allocated=` lines" check now applies to the base process too. Proven: at 220 Hz (the
  reviewer's mutant A) the check is GREEN, with rounds reading -11 to +6 B on `super7`, `super7a` and `whitecolor`;
  reviewer B's renaming wrapper is still RED (`FAIL saw v8: no allocated= lines in the base process's ...`).
- **MINOR 1, a case gone silent:** each rig reports its last block's peak (the voice rig keeps its cylinder's mix
  until the next block, the engine rig its output). V8 prints `PEAK` beside `NS`; the JVM reads it after each
  window. A peak below 1e-4 (or NaN) is `FAIL <case> <platform>: silent, it rendered nothing in its window`. The
  quietest case sounding is `C-offrel` at the end of the V8 window (587 s into its 1000 s release): peak 0.091, so
  the floor sits about 59 dB below it; `whitecolor` reads 0.72. Proven with the reviewer's mutant B (the n16 notes
  scheduled two blocks in the past): `FAIL E-C-rel05-n16 jvm: silent ...` and `FAIL E-C-rel05-n16 v8: silent ...`,
  exit 1.
- **MINOR 2, bounded runs:** a failed base process skips its window and the case's later rounds; a timeout, or a
  failure of the first case (other than silent), stops the V8 half with one line (`FAIL v8: stopped after ...`);
  `node --version` and `git describe` are bounded at 10 s; a timeout kills the whole process tree
  (`ProcessHandle.descendants()` first); the node bound is 120 s. The JVM half runs on a watched daemon thread: a
  case that runs past 120 s in one round stops the check (`FAIL <case> jvm: timeout ...`, the V8 half not run),
  because a thread cannot be killed. Proven: a node hanging on `--version` (`FAIL v8: not run, ... gave no answer
  within 10 s`), a node hanging in a pipeline with the bound cut to 5 s (`FAIL saw v8: timeout ...`, `FAIL v8:
  stopped after saw's timeout`, no process left), the JVM bound cut to 0 s (`FAIL sgpad-vib jvm: timeout ...`).
- **NITs:** a crashed node's `FAIL` keeps its first error-like line (not an `at` frame) and its last line; a
  non-finite or negative budget is a `BAD` line; the stale-bundle list's limits are said in the KDoc and the README.

### Review round 3 (2026-10-10): clean

Review `tmp/reviews/alloc-r3.md` (0 MAJOR, 1 MINOR, 3 NIT): no round 2 fix broken, no combination of the early stop,
the silence check and the timeouts reads green without measuring or red on a healthy tree. Fixed by the coordinator:
- **MINOR 1:** a crashed node's `FAIL` showed the throw site's source line, which node prints before the message. It
  now prefers node's `Name: message` line. Proven in one lock call: a `saw` rig that throws reads `FAIL saw v8: node
  exit 1, no NS line; it said: IllegalStateException: probe: saw is broken / Node.js v22.12.0`.
- **NIT 3:** one JVM case's exception costs that case only, as on V8 (the same probe: `FAIL saw jvm: ...`, the other
  JVM cases measured), and the JVM timeout path keeps the problems found before it.
- **NIT 1:** `console/with-build-lock.sh`'s release truncate cannot mask the command's status (`|| true`). The
  coordinator's fix to that script rides with this change: it opens the lock file for append, so a waiting agent no
  longer wipes the holder's record, and blanks the record by path (`: >&9` truncated nothing). Reviewed in round 3,
  eight cases on a private lock file.
- **NIT 2**, a negative V8 median printing as `-6 B`: left. It is honest noise and reads green.

The healthy tree stayed green in the same lock call (`RESULT GREEN: 28 of 28 within budget`).

**Found, not fixed** (outside this harness):
- `DiskUrlCache.getOrPut` writes every concurrent miss of one URL through the same `<key>.<ext>.tmp` name, so on a
  cold cache two of the corpus check's four workers can tear a sample file, and its song reads CHANGED once, on the
  first run of a fresh clone (reviewer of round 2, `tmp/reviews/alloc-r2.md` NIT 3). A unique tmp name would fix it.

## History

- 2026-10-20: numbered KLANG-00299 (was `docs/tasks-archive/2026-10/20261010-allocation-harness-and-full-check.md`).
