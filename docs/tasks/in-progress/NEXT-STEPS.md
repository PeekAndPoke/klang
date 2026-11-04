# Next steps (2026-11-04)

The queue of the engine stream (Klang-Main-Dev), in order. The note of 2026-10-20 (after the pause) is in git history.

## Where things stand

- `main` holds everything; the allocation check is green again (80 of 80, 2026-11-04, after the budgets were raised
  for the diagnostics KLANG-00399 counts since 2026-10-31, `audio_benchmark/alloc-budgets.txt`).
- KLANG-00214 (oversampling regions): steps 1, 3a, 3b and 4 landed; the soft cap inside a region heard and accepted;
  the first merge, Der Schmetterling's two guitar rigs in one 4x region each, made by the maintainer. PAUSED: the
  other merges are by ear, song by song (Kokon's four rigs next, an A/B on request).
- KLANG-00290 (engine follow-ups): PAUSED, everything left waits on a decision (its status paragraph).

## Queue (Klang-Main-Dev)

1. **KLANG-00210 (signal flow redesign), phase 5: the announcements** (the maintainer, 2026-11-04: continue the
   unification). The grep for the retired words (`PipelineDsl`, `postgain`, `pedal`, the voice pipeline, the filter
   pipeline, `voices/strip`) over `docs/`, the skills, the tutorials and the songs; the plan is complete when it is
   empty outside the history.
2. **KLANG-00245 (pitch pipeline into the tree), 7f**: `progress()` and `elapsed()` as public signals (decided, Q13).
3. Then, the maintainer's pick: KLANG-00293 (a chain swap may cut a ringing insert), KLANG-00137 (master dsl
   followups), the edge bugs of KLANG-00353.

## Katalyst stream (Klang-Research)

Unchanged since 2026-10-13: its task is closed and archived, and what is left waits for a song that asks for it
(KLANG-00303 (katalyst knob followups)).

## Open with the maintainer

- KLANG-00214's merges by ear (Kokon next) and the leftovers in its section 11 (sources not rate-mapped inside a
  region, the shimmer ring at 16x).
- KLANG-00290's decisions: 14 (by ear), 15 (does a replace resume a stopped engine?), 11 (the Zig port), 37 (a sound
  change), section 6.
- An FM round together: KLANG-00305 (fm revisit), now with the open sites of engine follow-ups 10c and 10d.
- Completion, left open: a local that shadows a library name shows two rows; a local bound to an arrow shows no type;
  after a named argument the list still shows locals and library names; a new line inside a multi-line call offers
  no names until a comma. Records: KLANG-00319 (editor local symbol completion) and KLANG-00130 (klangscript named
  args docs polish), "Open, for the maintainer".
- sprudel's own typed Kotlin door (its doors take `PatternLike = Any`): needs its own design and a home
  (KLANG-00314 (kotlin typed doors), "Left after the rollout").
- The stdlib's `hashCode(dynamic)` closure (every Kotlin/JS `HashMap` operation allocates on V8) is noted for an
  upstream report, not filed (KLANG-00290, item 2).
- `docs/tasks/in-progress/codemirror-bump-checklist.md` (a hand-test checklist for the Ultra 0.110 bump, 2026-10-18):
  still wanted, or done?
