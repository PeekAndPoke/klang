# KLANG-00489: notstrom the camera remembers what it showed and picks from what it has not shown lately

> **Status (2026-11-26): IN PROGRESS.** The kinds of rounds remember (step 1, done); the single angles, the song changes and the arc behind the crowd are open.

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

## One more move, noted with it (the maintainer, 2026-11-26)

"We could also have another angle that arcs around the back of the crowd pointing at the stage. Different starting
and end positions each time."

A new kind of round beside the five: the camera goes along an arc behind the audience, looking at the stage, and
where the arc begins and ends is drawn anew each time (from the seed and the round, as every choice is). It gives
the list above one more thing to pick from. To decide then: how high and how far back (the mixing desk's flight
already ends out there), and whether it is flown to or cut to.

## The work

**Step 1, done 2026-11-26: the kinds of rounds remember.**

- `Rounds.drawn(n)` (`LookPlan.kt`): a round's kind is drawn from the three kinds that waited longest since the seed
  last drew them (`KIND_CHOICE`), weighted by the shares each had before (`kindWeight`). So no kind comes twice in
  three rounds; over a long show circle, arc and flight come a fifth each, the reverse total about one in six,
  looks a little over a fifth.
- Decided in the work: one list for the kinds only. The memory goes by what the seed drew, not by what the round
  became (an arc without room is looks), so what cannot be shown does not block the list. Worked out from round 0
  on, a pure function of the seed: a round is the same kind however it is asked.
- The flight before the one who strolls counts as a flight: a round whose flight would leave within a round after
  it is looks (`flownBefore`), and she does not send the camera out within a round after a round's own flight
  (`roundFlewBefore`).

**Open:**

- The single looks of the stage from another angle (`ANGLES`) have no memory yet. The maintainer's words were about
  camera angles; the repeated flight to the mixing desk was the kinds' part.
- The memory starts over at every song change of a show (a new choreography per song, `View.retake`): the last
  round of one song and the first of the next can both be flights, and every song replays the seed's same order of
  kinds. Proposed to the maintainer: hand the last two kinds and the last flight before her over to the next song,
  and perhaps let the song's place in the show into the seed.
- The arc behind the crowd (above).

## Acceptance

Step 1: `LookPlanSpec` (no kind twice in three rounds over 20 seeds and 200 rounds, the weights tell, the same
however asked, the flight before her, the windows' edges, the wrap); klangviz js (592) and jvm (509) green; 10
mutants killed; review CLEAN in round 2 (reviewer-opus-high).

## History

- 2026-11-26: created from the maintainer's words; the arc behind the crowd added the same day.
- 2026-11-26: step 1, the kinds of rounds remember.
- 2026-11-26: IN PROGRESS.
