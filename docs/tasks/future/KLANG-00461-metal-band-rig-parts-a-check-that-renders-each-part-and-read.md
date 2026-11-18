# KLANG-00461: Metal band rig parts: a check that renders each part and reads its level

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00459 (Metal band instruments: one band, the same instruments in every metal song)

## Why this exists

KLANG-00460 (the level contract) needs a way to see whether a part keeps it. Without one, calibrating is done by
ear, which is what the contract is meant to end.

## The work (a sketch)

Render each rig part alone with the contract's reference input and print four numbers per part: RMS and peak going
in, RMS and peak coming out. A table of all parts is the working tool for KLANG-00462 (the calibration).

## Decide before implementing

- **A check for the future or scaffolding** (the rule of 2026-10-13). As a lasting check it would go red when a
  shared part leaves the tolerance. The rule "songs move freely" speaks against a check that goes red because a
  song was edited, so a lasting check fits only once the parts live in a shared place (KLANG-00463 (the shared
  guitar rig)) and are no longer part of a song.
- Where it runs: an offline render in a test, or a script beside the DSP probes (`audio/ref/verification.md`).
- UNVERIFIED: whether a single `Ign.fn` part can be rendered alone with a test signal today, or whether the check
  has to build a small voice around it. Nobody has read that code for this ticket.

## Acceptance

One run prints the table for every rig part, and the numbers of one part have been confirmed by a second
measurement.

## History

- 2026-11-18: created, from the Der Schmetterling session of 2026-11-17 and 18.
