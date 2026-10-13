# Next steps after the pause (2026-10-13)

We paused for the weekly token budget (maintainer, 2026-10-12) on a clean point. This note says where each stream
picks up again. Read it first, then the task file it names.

## Where things stand

- `main` holds everything: v0.6.5 (KlangMap), engine follow-up 2 step 4 (`S-doors`), Klang-Research's Katalyst knob
  expressions (steps 3 to 7), and the Der Schmetterling paste fixed. Version 0.6.6 in `gradle.properties`, not tagged
  at the time of writing.
- The full check on `main`: 2026-10-13 on `9e1bcec7`: tests GREEN (17,684, 0 failed), allocation GREEN (80 of 80),
  timing done; corpus 16 of 18, the two CHANGED rows being the maintainer's own song edits (Kokon `7ccfb80bdaa75799`,
  Der Schmetterling `dc6c8f3ddf82ddab`), no engine change. Re-accept both (`runSongBenchmark --args=corpus-accept`)
  once the maintainer approves, and the check is green. (Its "tree changed" line was this note being written.)
- No branch holds unmerged work of either session. Merged and safe to delete when you like (the maintainer's call):
  `klang-map`, `sprudel-alloc-case`, `alloc-harness`, `katalyst-live-knobs` (and its worktree
  `../klang-worktrees/katalyst-live-knobs`), plus older ones (`engine-follow-ups-v8`, `engine-pass-1`,
  `pitch-pipeline`, `pitch-pipeline-fm`).

## Engine stream (Klang-Main-Dev)

The one task in progress: [`engine-follow-ups.md`](engine-follow-ups.md). Its first paragraph is the order:

1. The open V8 items: 31 (what item 29 left on a full `classic()` voice), 32 (the delay's and reverb's per-block
   `configure`), 34 (the bus distort's oversampled path), 39 to 41 (the Katalyst knob expressions' residues: the knob
   noises' `fbm`, the compressor's five setters, a moving delay time, reverb size and phaser breakpoint), 10c and 10d
   (fm).
2. The known tails of the allocation check, each a breach that a rerun clears (the rule is in the header of
   `audio_benchmark/alloc-budgets.txt`): 4 (`spluck-a`), 36 (`E-arith`'s second mode), 38 (`E-duck`).
3. Sections 2 to 6 of the same file (13, 11, the small leftovers, the audit's later steps).

A step there runs as before: a worker, a review loop, the allocation check for an allocation item, and a full check
(`console/full-check.sh --fresh`) before a merge to `main`.

## Katalyst stream (Klang-Research)

Its task is closed and archived:
[`20261012-katalyst-knob-expressions.md`](../../tasks-archive/2026-10/20261012-katalyst-knob-expressions.md), "Where
to continue". What is left waits for a song that asks for it:
[`../katalyst-knob-followups.md`](../katalyst-knob-followups.md). Its V8 items are 39 to 41 of the engine list above,
so agree who takes them before starting.

## Open with the maintainer

- `audio_be/src/commonMain/kotlin/DistortionCore.kt:147` still names `docs/tasks/engine-follow-ups.md` (old path). It
  is on the ADAA list a session keeps off; a one-line comment fix once the maintainer clears it.
- C1 to C11 in [`../../housekeeping/2026-10-10-baseline.md`](../../housekeeping/2026-10-10-baseline.md) (the FM
  diagnostic is C6).
- Q13, Q29 (`duty(width)`) and Q30 to Q33 in [`../_maintainer-questions.md`](../_maintainer-questions.md).
- A listening question from the knob expressions: [`../by-ear/delay-reverb-owner-change-cut.md`](../by-ear/delay-reverb-owner-change-cut.md).
- Two provisional review rules from 2026-10-11 to confirm or drop (`.claude/skills/review-loop/SKILL.md`): reviewers
  name the mutants and the worker runs them; a compare-only memo may hold raw knobs. No case against either has come up so far.
- The stdlib's `hashCode(dynamic)` closure (every Kotlin/JS `HashMap` operation allocates on V8) is noted for an
  upstream report, not filed (engine follow-ups, item 2).
