# KLANG-00455: Live act: a line-up scene, the band side by side, one steps forward with their stats

> **Status (2026-11-17): IDEA.** Asked for by the maintainer, for after the cleanup of the live act's structure.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

The maintainer, 2026-11-17: "I would like to have a scene where all band members are standing side by side.
Clicking one will make that one move forward and then the user will see stats, like name. Also the user can rotate
the musician to view them from all angles."

A scene of its own beside the concert: the six stand in a row, the viewer picks one, that one steps forward, their
sheet is shown (name, what they play, what they are like), and the viewer turns them round to see them from all
sides.

## Decide before implementing

- What the sheet says besides the name: the instrument, the character in a line, the eye colour, the song's part.
- Whether the one who stepped forward plays or moves while looked at (an idle pose, a few notes on their
  instrument), and whether they hold their instrument.
- How it is reached: a page of its own, or a mode of the concert's page.
- The light: the stage's lamps, or a plain light that shows the figure (as the `?head=` look and the figure
  script's `close=` pictures do).

## The work

It builds on a sheet per member (name, figure, dress, traits, instrument), which the cleanup of the live act's
structure is to bring: the line-up reads those sheets and needs nothing of its own about a member.

1. The row: the six side by side, in a plain light.
2. Picking: a click on a figure, that one steps forward and the others stay.
3. The sheet beside them.
4. Turning: dragging turns the one in front round the upright.

## Acceptance

Each of the six can be picked, steps forward, shows their sheet and can be turned all the way round; picking another
sends the first one back.

## History

- 2026-11-17: created.
