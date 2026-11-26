# KLANG-00490: notstrom the cast says in one place who is on stage what they play and by which events

> **Status (2026-11-26): IDEA.** The maintainer's note for later, 2026-11-26.

## Why this exists

The maintainer (2026-11-26), of `cast = Cast(drums = DrumSeat..., marimba = ...)`: "We have to restructure this
here ... The instruments should not be properties of the Cast. The instrument should be a property of the
player... or even differently, we should say which musician is on stage and which instruments they play and how
the mapping from the music events is... all in one place rather than scattered across different objects / parts.
We also have to give instruments where it applies the note range, f.e. for guitars the lowest note and similar for
the marimba."

Made clear the same day: "With property of player i mean, it should be a property of the seat. Then we will
probably also not need DrumSeat, StringSeat ... cast will be a list of seats or musicians and each musicians object
has all it needs to be able to react to music events, start position etc."

## The direction (the maintainer, 2026-11-26)

- The cast is a list of seats (or musicians), one kind of seat for everyone: `DrumSeat`, `NoteSeat` and
  `StringSeat` go.
- The instrument is a property of the seat.
- A seat holds all its musician needs: who they are, the instrument, the music events it is played by, the start
  position, and whatever else the band reads for them today from other places.
- An instrument says its note range where it has one (a guitar's lowest note, the marimba's).

## A wider frame, a heads-up (the maintainer, 2026-11-26)

"I think we need to design this even more broadly ... currently the Cast is bound to a song. We need to come to
the point where the cast can change mid song or properties of one member... this would also make for smoother
transitions between songs. This would also, in the long run, allow steering the band through patterns in the song
or a separate choreography system that works on patterns too. Not sure about the final shape, just as a heads up."

What it asks of the design, as far as it can be said now:

- A seat is not fixed for a song: the cast, or one property of one member (the instrument, the place, sitting, a
  skill), can change at any moment on the show's clock.
- A change of song is then one case of that: many seats change at once. Today it is a path of its own
  (`Notstrom.reseat`, KLANG-00481), which takes two whole casts.
- In the long run the changes are told by patterns: the song's own, or a choreography of its own that works on
  patterns. So a seat's properties should be values that can be set from outside over time, not constructor
  arguments read once.
- The final shape is open. This is a frame to design within, not a plan.

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

The direction is given above; the shape is not designed yet. First thoughts, for the maintainer:

- The start position is the seat's by the maintainer's word, so the stage plot's places move into the seats. What
  is left of the plot (the walk-in's length, the entrances, which are planned from the places at a change of song)
  needs a home: with the seat too, or a plan made from the seats.
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

- 2026-11-26: created from the maintainer's words; the direction made clear the same day (the instrument is the
  seat's, one kind of seat, the cast a list of them); the wider frame added (a cast that can change mid song,
  steered by patterns in the long run).
