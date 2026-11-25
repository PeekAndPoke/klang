# KLANG-00486: notstrom the ghosts dissolve into the fog as they fade

> **Status (2026-11-25): IDEA.** The maintainer's note for later, 2026-11-25.

## Why this exists

The maintainer (2026-11-25): "when the ghosts fade out, before they have fully faded out can we copy their remnants
into the fog, so that they dissolve slowly with the blur of the fog?"

## What is there today

The ghosts are the band's shadows on the wall and behind the fence, thrown by the front lamp, in the ghosts world
(KLANG-00478). When a visit ends, the world's share falls and the lamp moves back, so the shadows shrink and pale
until they are gone (`WorldLight.ghostShare`, `ghostCreep`). The fog is a stack of drifting sheets drawn by its own
shader (`GroundFog`), with no knowledge of the shadows.

## Decide before implementing

Nothing is designed yet. First thoughts, for the maintainer:

- What "copy into the fog" is in the picture: the shadows' shapes as they are at that moment, laid into the fog as
  a tint that then drifts and blurs with it, in the ghosts' colour.
- Where it shows: in the fog before the fence, on the wall behind it, or both.
- How long the remnants last, and whether they drift away with the fog's own motion.
- The cost: the shapes would have to be drawn once into a picture the fog reads. The fog's sheets are being
  halved (KLANG-00484), which is worth doing first.

## The work

Not planned yet.

## Acceptance

Not planned yet.

## History

- 2026-11-25: created from the maintainer's words.
