# Next steps after the pause (2026-10-13)

We paused for the weekly token budget (maintainer, 2026-10-12) on a clean point. This note says where each stream
picks up again. Read it first, then the task file it names.

## Where things stand

- `main` holds everything: v0.6.5 (KlangMap), engine follow-up 2 step 4 (`S-doors`), Klang-Research's Katalyst knob
  expressions (steps 3 to 7), and the Der Schmetterling paste fixed. Version 0.6.6 in `gradle.properties`, not tagged
  at the time of writing.
- The full check on `main` (2026-10-13, `9e1bcec7`): tests GREEN (17,684), allocation GREEN (80 of 80), timing done;
  the corpus differed only on the maintainer's song edits, re-accepted the same day (Q34) on the songs as of
  `d9804f41`. Since 2026-10-15 a song edit no longer turns the check red ([`../../tasks-archive/2026-10/20261015-song-checks-move-freely.md`](../../tasks-archive/2026-10/20261015-song-checks-move-freely.md)).
- No branch holds unmerged work of either session. The merged branches and the `katalyst-live-knobs` worktree were
  deleted 2026-10-13 (Q36), and so were the 55 older merged local branches and the abandoned experiment
  `code-blocks-with-mutator` (maintainer). `main` is the only local branch.

## Engine stream (Klang-Main-Dev)

The one task in progress: [`engine-follow-ups.md`](engine-follow-ups.md). Its first paragraph is the order. Q38
(maintainer, 2026-10-13): finish 31 to 41 after the refill, **as long as each is a purely independent performance fix
that changes no API**; an item that turns out to need an API change (a door, a wire type, a public signature) stops
and goes to the maintainer.

1. The open V8 items: 31 (what item 29 left on a full `classic()` voice), 32 (the delay's and reverb's per-block
   `configure`), 34 (the bus distort's oversampled path), 39 to 41 (the Katalyst knob expressions' residues: the knob
   noises' `fbm`, the compressor's five setters, a moving delay time, reverb size and phaser breakpoint), 10c and 10d
   (fm).
2. The known tails of the allocation check, each a breach that a rerun clears (the rule is in the header of
   `audio_benchmark/alloc-budgets.txt`): 4 (`spluck-a`), 36 (`E-arith`'s second mode), 38 (`E-duck`).
3. Done 2026-10-15: songs move freely (`../../tasks-archive/2026-10/20261015-song-checks-move-freely.md`).
   Then the behaviour changes decided 2026-10-13: engine follow-up 42 (a delay or reverb switched off fades, Q39), 43 (solo reacts at once, Q28), 44 (`duty(width)`, Q29, done 2026-10-16). Larger, decided, its own task: typed Kotlin doors
   ([`../kotlin-typed-doors.md`](../kotlin-typed-doors.md), Q24)
   and the frozen songs' shared orbits ([`../song-orbit-ownership-review.md`](../song-orbit-ownership-review.md), Q20).
4. Sections 2 to 6 of the same file (13, 11, the small leftovers, the audit's later steps).

A step there runs as before: a worker, a review loop, the allocation check for an allocation item, and a full check
(`console/full-check.sh --fresh`) before a merge to `main`.

## Katalyst stream (Klang-Research)

Its task is closed and archived:
[`20261012-katalyst-knob-expressions.md`](../../tasks-archive/2026-10/20261012-katalyst-knob-expressions.md), "Where
to continue". What is left waits for a song that asks for it:
[`../katalyst-knob-followups.md`](../katalyst-knob-followups.md). Its V8 items, 39 to 41 of the engine list above,
are the engine stream's (Q38).

## Open with the maintainer

- Two listening tests: [`../by-ear/knob-glide-listening.md`](../by-ear/knob-glide-listening.md) (C13), and the
  listening rounds before the next songs (C10).
- An FM round together: [`../future/fm-revisit.md`](../future/fm-revisit.md).
- The stdlib's `hashCode(dynamic)` closure (every Kotlin/JS `HashMap` operation allocates on V8) is noted for an
  upstream report, not filed (engine follow-ups, item 2).
