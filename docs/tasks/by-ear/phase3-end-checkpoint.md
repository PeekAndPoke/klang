# The phase 3 end checkpoint: retire or regenerate the two voice baselines

Status: **owed, by ear.** Opened 2026-09-28 when the phase 3 record was archived
(`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`). The decision it closes is the test consolidation's
(`docs/tasks-archive/2026-09/20260928-test-consolidation.md` section 4: "trimmed now, retired or regenerated at the
phase 3 end listening checkpoint").

## What it is

`docs/plans/signal-flow-redesign.md` section 12 names "the end of phase 3" as a checkpoint: the maintainer says
"this sounds right", the baselines are regenerated, the migration fixtures whose migration is finished are
deleted. Phase 3 ended on 2026-09-28. The audit pass that section asks for was the test consolidation (done). What
is left is the listening verdict and what it means for two baselines:

- `ClassicVoiceBaselineSpec` (`audio_be/src/jvmTest/kotlin/ignitor/`): the built-in `saw` through `classic()`, one
  voice per slot row, raw-bits fingerprints at 48 and 44.1 kHz frozen at step 9 (a) as "the strip's sound";
  trimmed to 37 rows.
- `BuiltInVoiceMatrixSpec` (same folder): every built-in sound, `untouched` and `analog 2, lpf 1200`, fingerprints
  frozen at step 9 (a); the second row carries the analog draw-order record (the archived record's section 8).

## What was heard already

The listening checkpoint of 2026-09-26 covered every pair on the phase 3 list up to step 7 (record section 9,
"Listening checkpoint PASSED"); steps 8, 9 and 10 proved render identity; step 12 C4's pair (the master drains) was
accepted on 2026-09-28. So the question is not "does phase 3 sound right" pair by pair, but whether the maintainer
wants one whole listen at the end before the baselines lose their "strip" meaning.

## The decision

1. Listen (or declare the earlier checkpoints sufficient).
2. Per spec: RETIRE it (its job, holding the strip's sound through the migration, is done; the stage laws have
   their own specs) or REGENERATE it as the new baseline (fingerprints taken from the current tree, never
   hand-edited), keeping `BuiltInVoiceMatrixSpec`'s draw-order row either way if the draw order is still wanted as
   a guard.
3. Update the KDoc of whichever stays, and `audio/MEMORY.md` (its Open threads name them as the frozen strip).
