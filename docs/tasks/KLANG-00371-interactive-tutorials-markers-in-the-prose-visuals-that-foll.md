# KLANG-00371: Interactive tutorials: markers in the prose, visuals that follow the player (a light running along the ADSR curve)

> **Status (2026-10-27): SCHEDULED.** after the Learn pages (KLANG-00370)
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27, on the ADSR picture in "The Shape of a Note": "It would be cool to have interactive
elements in the Tutorials. So we would need some "marker" in the markdown that references the interactive content.
Then we could listen for the events of the player and animate each note on the adsr curve... like a bright light
following the curve while the sound plays".

Today a tutorial's visual (`Block.Visual.Adsr`, `AdsrVisual`) is a still picture drawn from the same values as the
code beside it (lint-enforced: picture and code cannot drift); the example player (`PlayableCodeExample`) plays and
knows each sound event as it is scheduled (the highlights in the code use them).

## Decide before implementing

- The marker: in the markdown itself (a fenced block or a short tag such as `{{adsr:example-2}}` that names the
  example it follows), or kept as a block of its own beside the markdown as today (`Block.Visual`), linked to an
  example by its place or a name. A marker in the markdown keeps the prose and the picture together; it needs a
  syntax the curriculum lint and marked both leave alone.
- What follows the player: each note a light that runs along the curve from its start to its release, as long as the
  note sounds, several at once for overlapping notes; dimmed when nothing plays.
- Where the events come from: the player's voice events (`KlangPlaybackSignal.VoicesScheduled`, as the code
  highlights use them), with their start time and their gate; the curve's shape from the same values as today.
- Which other visuals follow (later): a filter's cutoff, a pattern's steps lighting in the cycle, the cycle itself.
- The engine stays the horse (CLAUDE.md, stone): the visual only reads the events the player already sends; no DSP
  of its own.

## The work

Not planned while it waits; first the Learn pages (KLANG-00370).

## Acceptance

Not set yet.

## History

- 2026-10-27: created from the maintainer's idea while the Learn pages were built.
