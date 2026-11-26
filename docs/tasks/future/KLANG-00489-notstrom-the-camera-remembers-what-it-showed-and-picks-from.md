# KLANG-00489: notstrom the camera remembers what it showed and picks from what it has not shown lately

> **Status (2026-11-26): IDEA.** The maintainer's note for an upcoming task, 2026-11-26.

## Why this exists

The maintainer (2026-11-26): "we need to remember which camera angles we have already used, maybe a cooldown there
too... or maybe better sort by how long they have not been used, and pick randomly from the 3-5 that have recently
not been used. Because currently we often have the fly-back to FOH in succession, which is boring."

## What is there today

The camera's rounds draw each choice on its own from the seed (`chooseKind` and `chooseLook` in `LookPlan.kt`): a
round's kind is a circle, an arc, a flight to the mixing desk, the reverse total or looks, each by a fixed share
(about a fifth is a flight), and a look's angle is drawn the same way. Nothing looks at what the rounds before
showed, so the same kind can come several times in a row. The flight before Sirene when she walks at the camera
(`Choreography.retreat`) is a second way to the mixing desk and has its own rest of some seconds, apart from the
rounds: a flight of the round right after one of hers is possible too.

## Decide before implementing

- The maintainer's own proposal: order what can be shown by how long it has not been shown, and draw from the
  three to five that waited longest. To be decided: whether one list holds everything (kinds of rounds and single
  angles), or the kinds have theirs and the angles theirs.
- The flight before Sirene counts as a flight shown, so the round does not fly out right after it.
- The camera's choices are told from the seed and the song alone today, the same whenever they are asked. A
  memory has to keep that: what was shown is worked out from the rounds before, not kept in a variable that a
  second asking would find changed (the looks already carry such a plan from one asking to the next, `LookPlan`).
- In Kokon few have room for an arc (`arcHasRoom`): what cannot be shown must not block the list.

## The work

Not planned yet.

## Acceptance

Not planned yet.

## History

- 2026-11-26: created from the maintainer's words.
