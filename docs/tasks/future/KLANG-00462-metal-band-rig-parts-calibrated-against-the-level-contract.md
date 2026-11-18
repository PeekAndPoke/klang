# KLANG-00462: Metal band rig parts: calibrated against the level contract

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00459 (Metal band instruments: one band, the same instruments in every metal song)

## Why this exists

With KLANG-00460 (the level contract) decided and KLANG-00461 (the level check) printing each part's level, the 19
rig parts are brought to the contract: every part gives out the reference level for the reference input.

## The work

- Calibrate the parts in ONE song first. Proposed: Der Schmetterling, whose rig was worked on last.
- Each part gets its level knob set (the `mul` at its end), nothing else. A change to a part's character is not
  this ticket.
- The song's own gains and its guitar compressors are set again afterwards, since every rig's level moves.

## Decide before implementing

- The song WILL sound different after this unless every change is compensated in the song's gains. Whether the
  goal is "the same sound as before" (compensate everything, compare renders) or "a new balance found by ear".

## Acceptance

The check's table shows every part inside the tolerance, and the maintainer has listened to the song.

## History

- 2026-11-18: created, from the Der Schmetterling session of 2026-11-17 and 18.
