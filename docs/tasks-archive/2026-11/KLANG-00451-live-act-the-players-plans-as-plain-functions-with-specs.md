# KLANG-00451: Live act: the players' plans as plain functions with specs

> **Status (2026-11-18): DONE.** Part 5, the camera, done with commit 0441b65cd (chooseLook, chooseKind, LookPlanSpec); all five plans are plain functions with specs.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

Since KLANG-00449 the band reads a song while it plays: the song grows, and it forgets what lies behind. Each
player then plans again from what is known, and carries on from the last thing forgotten, so that nothing still
known changes (which hand plays a note, which way the pick goes, where a phrase begins, a stroll that has begun,
a look the camera has begun).

In `Song` that promise has a spec: a reading after forgetting is compared with the reading of the whole song. In
the players it has none. Their plans sit inside classes that build three.js objects, and the test browser here
has no WebGL, so those classes cannot be made in a spec. The reviews of KLANG-00449 found four real defects in
exactly this kind of code by reading alone, and the one place that had such a spec still hid a fifth in the part
the spec did not cover. Reading is not enough for it.

## Decide before implementing

- Whether the plans move to `commonMain` (they are plain arithmetic on notes and need no three.js), so they run
  in the JVM specs too.
- The marimba's hands need the bars' places: whether the plan takes them as plain numbers.

## The work

Each as a plain function from the notes known and what is carried over, with a spec that compares forgetting
against the whole reading:

1. The marimba player's hands (`MarimbaPlayer.plan`): which hand plays which note, across a forget in a passage,
   with a chord at the cut, with none of her notes left. **Done** (d699a4b40): `planMarimba`, with `MarimbaPlanSpec`.
2. A string player's way through the strings and the fingering (`StringPlayer.fingered`). Since 2026-11-17 the fingering itself is
   plain and has a spec (`Tuning.grips`); what `fingered` keeps of the grips played when the song grows, forgets or
   begins again (the kept prefix up to now, the forgotten pluck) had none until d699a4b40.
   **Done** (d699a4b40): `planStrings`, with `StringPlanSpec`.
3. The frontwoman's phrases and strolls (`TrumpetPlayer.plan`), with the rule that a stroll that has begun stays.
   **Done** (d699a4b40): `planTrumpet`, with `TrumpetPlanSpec`.
4. The drummer's lists by limb (`Drummer.plan`). **Done** (d699a4b40): `planDrums`, with `DrumPlanSpec`.
5. The camera's choice of a look (`Choreography.choose`, `subject`, `kind`): stable through a round whatever the
   song forgets. **Done** (0441b65cd): `chooseLook` and `chooseKind`, going on from a `LookPlan` (the looks begun),
   with `LookPlanSpec`. `Subject`, `Placement`, `RoundKind`, the looks tables, the angles and `ORDER` are in
   `commonMain`; the players who play seldom and the one who strolls are handed in (`Rounds`); the choreography
   asks for each look as before, so the stills are the same.

## Acceptance

Each of the five has a spec that goes red when its carry-over is taken out, and the pictures of the recording
are the same as before (the 18 stills).

## History

- 2026-11-16: created.
- 2026-11-17: IN PROGRESS. Parts 1 to 4 done with commit d699a4b40 (planMarimba, planStrings, planDrums, planTrumpet, each with its spec); part 5, the camera, is open.
- 2026-11-18: DONE. Part 5, the camera, done with commit 0441b65cd (chooseLook, chooseKind, LookPlanSpec); all five plans are plain functions with specs.
