# KLANG-00490: notstrom the cast says in one place who is on stage what they play and by which events

> **Status (2026-11-26): IDEA.** The maintainer's note for later, 2026-11-26.

## Why this exists

The maintainer (2026-11-26), of `cast = Cast(drums = DrumSeat..., marimba = ...)`: "We have to restructure this
here ... The instruments should not be properties of the Cast. The instrument should be a property of the
player... or even differently, we should say which musician is on stage and which instruments they play and how
the mapping from the music events is... all in one place rather than scattered across different objects / parts.
We also have to give instruments where it applies the note range, f.e. for guitars the lowest note and similar for
the marimba."

## What is there today

- A song's `Cast` (`band/Cast.kt`) has one field per kind of instrument: `drums` (a `DrumSeat`), `marimba` and
  `trumpet` (a `NoteSeat` each, or none), `strings` (a list of `StringSeat`s), and `seldom`. Each seat names its
  player, what they play in words, and the song's tags for it.
- What a seat does not say lies elsewhere: where the player stands and whether they sit (`StagePlot`), which hand
  they play with and how the instrument lies in the lap (the member's sheet), who strolls (derived from the
  trumpet's seat), the skills a song rests (`Score.resting`).
- The band reads the cast by kind: `Notstrom` makes a drummer, a marimba player, a trumpet player and the string
  players each in its own piece of code, and `reseat`, `Cast.keeps`, `tagsOf` and `playsOf` each go through the
  kinds again.
- A note range is known for strung instruments only by their tuning (`StringDesign.tuning`); a `StringSeat` has a
  `riffLow` for the test notes. The marimba takes its lowest and highest bar from the notes it is given; the
  trumpet has none.

## Decide before implementing

Nothing is designed yet. First thoughts, for the maintainer:

- One line per musician on stage: who, the instrument they play in this song, the song's events it is played by
  (tags), and where they are. Whether the place joins that line or stays in the stage plot is the first question.
- The instrument as a thing of its own that the line names (a guitar of a design, the marimba, the trumpet, the
  kit), with what it says of itself: its note range where it has one (a guitar's lowest note from its tuning, the
  marimba's lowest and highest bar), so that a part outside it can be seen, and folded or told.
- The band then makes each player from that line, by the instrument's kind, in one place; the kinds stop being
  fields of the cast. This is what KLANG-00479 (building blocks) is about; this ticket is its next step for the
  cast.
- It touches the change of song (KLANG-00481: who keeps their instrument is read from the two casts) and the page
  "Meet the band" (it reads what each one plays).

## The work

Not planned yet.

## Acceptance

Not planned yet.

## History

- 2026-11-26: created from the maintainer's words.
