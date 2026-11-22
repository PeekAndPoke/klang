# KLANG-00480: Live act: the musicians' movement in versions, today's frozen as the first

> **Status (2026-11-22): IDEA.** the maintainer, 2026-11-22: a snapshot in the code of how the musicians move today, to switch between it and later versions
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

The maintainer, 2026-11-22: "It is very important that we take a snapshot, in the code, of the way the musicians
currently move and freeze it, so that we can switch between today and improved versions of the future, keeping the
history of the project visible and making the show more interesting."

So the way the band moves gets versions. Today's is the first and is never changed again. Later improvements are new
versions beside it, and the show can turn from one to another: by a switch, and perhaps by itself, as a look back at
how they once moved.

## The direction (the maintainer, 2026-11-23)

"If we can express the movements, like nodding, swaying left right etc. as skills then we can easily version them
like NoddV2."

So a version is not a copy of the whole band's players: it is a skill with a name. Today's nod is the skill `Nod`
and stays as it is; a better one is `NodV2` beside it, and a member's sheet says which one they have. That is the
plan of KLANG-00479 already (skills are equipped on the sheet), carried on to the movements that are not skills yet.

What it asks for:

- **The movements have to become skills first.** Today the looks and the idle moves are skills (`CameraStare`,
  `CrowdGaze`, `StickTwirl` and the rest). The nod, the sway, the breath and the walk are not: they live in the
  figure's own code (`Life`, `Mover`), and the playing itself in each instrument's player. KLANG-00479 step 3 (one
  pose contract: channels a skill claims) is the step that makes them skills.
- **The first version is today's, taken out without changing it.** Each movement that becomes a skill moves as it
  moved before; a recording of the poses before and after guards that (point 1 of the first proposal below, kept).
- **Switching** is then a choice of sheets: a member with `Nod` or with `NodV2`, one member at a time or the whole
  band by a named set ("as in November 2026").

Still open: whether the playing at the instruments (a strum, a stroke, the mallets) becomes skills with versions as
well, or stays the player's own; and whether the show itself turns between sets in the middle of a song.

## The first proposal (Klang-Research, 2026-11-22; replaced by the direction above, kept for its reasons)

1. **Record today's movement as data first, before anything is refactored.** For a fixed song and a few seeds, the
   pose of every member (each bone's turn, where they stand) at sampled moments, written by today's code into a file
   the specs read. That is the snapshot nobody can blur: whatever is built later, version 1 has to reproduce it.
2. **Then the pose contract** (KLANG-00479 step 3: every instrument's player offers the same channels). It is the
   seam a version plugs into: a version of the movement is one set of players behind the contract. Step 3 is meant to
   change no movement; the recording of point 1 proves it.
3. **Freeze version 1 behind the contract:** today's players and their plans in a folder of their own with the
   version's name, marked as frozen (a KDoc, and the recording as its guard). Work on better movement happens in a
   copy beside it, the next version.
4. **A switch and a key** to choose the version, as the looks have (`LookVariants`); later the show may turn between
   them by itself.

Why in this order: frozen code stays alive only while what lies under it holds still (the figures' skeleton, the
instruments' places, the song's readings). The contract is that still ground. Copying today's players away before
it exists would freeze them against an interface that step 3 is about to change, and the copy would have to be
edited after all.

What it costs: the frozen version is several thousand lines kept twice or more, which have to go on compiling. That
is weight on purpose (the stone rule on complexity asks for the maintainer's word on it, and this ticket is that
question).

## Decide before implementing

1. **What belongs to "how they move"?** Only the playing (hands, arms and heads at the instruments), or everything a
   member does: the stroll, the idle skills, the glances, the breath, the hair, the walk-in?
2. **How is it switched?** The whole band at once or member by member; by a switch and a key only, or also by the
   show itself in the middle of a song (which needs a soft turn from one version's pose to the other's).
3. **The order:** the recording now and the freeze after the pose contract (the proposal), or a plain copy of today's
   code at once, before any refactoring touches it.
4. **Do the figures and the instruments belong to a version too?** A later body or a later guitar changes how the
   same movement looks; today's models could be kept with version 1, or the versions could share the newest ones.

## The work

To be planned once the four points are decided.

## Acceptance

- Version 1 moves exactly as the band moved on 2026-11-22: the recording says so, in a spec.
- A second version can be added without touching the first.
- A switch turns between them while a show runs.

## History

- 2026-11-22: created.
- 2026-11-23: the direction: movements become skills, a version is a skill with a name (`NodV2`).
