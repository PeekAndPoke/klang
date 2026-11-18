# KLANG-00463: Metal band instruments: the guitar rig in one shared place, imported by the songs

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00459 (Metal band instruments: one band, the same instruments in every metal song)

## Why this exists

Kokon and Der Schmetterling each carry a full copy of the guitar rig, and parts are copied between them by hand.
The band should have one rig.

## What was found (2026-11-18)

The two copies were compared by reading: the rig sections are the same but for the 4x12 cab.

- its thump band: 180 Hz at +3.30 dB in Der Schmetterling, 120 Hz at +3.10 dB in Kokon;
- its highpass: 80 Hz in Der Schmetterling, 105 Hz in Kokon.

Outside the compared sections, `guitarShape` differs since 2026-11-17: Kokon still ends in `.mul(0.35)`, which was
removed in Der Schmetterling (its guitar compressors and faders were moved to match).

## The work (a sketch)

- The rig parts and `makeGuitar` move to one shared place, and both songs import them.
- The only mechanism today is to put them into a built-in song and import from it (maintainer, 2026-11-18). That
  is the first step. A rig is not a song, so whether Klang gets a real home for reusable sound parts is a later
  question, and by the stone rule on complexity one to consult on before building anything.

## Decide before implementing

- **What is a parameter.** `snareCut` notches at the song's `snareHz`. The cab differences above may be the same
  case, a choice per song, or plain drift; the maintainer knows which.
- Which copy wins where the two differ.
- Whether this waits for KLANG-00462 (the calibration). Proposed: yes.

## Acceptance

Both songs play from the shared rig, no rig part is left in either song, and the maintainer has listened to both.

## History

- 2026-11-18: created, from the Der Schmetterling session of 2026-11-17 and 18.
