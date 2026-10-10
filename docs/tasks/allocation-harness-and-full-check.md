# The allocation harness in the repo, and one full check

Status: **decided (maintainer, 2026-10-10), queued right after engine follow-ups 8 and 12; not started.**

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
