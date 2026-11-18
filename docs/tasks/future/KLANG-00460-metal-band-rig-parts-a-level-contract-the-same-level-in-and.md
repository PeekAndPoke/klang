# KLANG-00460: Metal band rig parts: a level contract, the same level in and out

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00459 (Metal band instruments: one band, the same instruments in every metal song)

## Why this exists

A rig is built by combining parts (pickup, pedal, preamp, power amp, cab). Each combination gives out a different
level today, so the song's gains and any compressor behind the rig have to be found again by ear.

Real gear has a convention for this: a pedal at "unity gain" gives out what it gets. With the same promise here,
any combination of parts would give a very similar level, and a compressor threshold would mean the same thing
behind every rig.

## The proposal (not decided)

Proposed to the maintainer on 2026-11-18; the numbers are the maintainer's to set.

1. **One reference level for the whole rig.** The string gives it out, every part expects it and returns it.
   Proposed: -18 dB RMS, the usual studio working level.
2. **Drive is a knob inside the part.** A stage that clips depends on how hard it is hit, so "the same level out
   as in" can only hold at the reference input. A clipping part boosts inside, clips, and turns itself back down.
   The pedals already work this way: their comment in Der Schmetterling says their `mul` is the level knob, set so
   the three guitars land within 2 dB of the stock rig.
3. **Matched by RMS, not by peak.** Clipping changes how spiky a signal is, so two parts can agree on one of the two
   only. RMS is the one that follows loudness; each part's peak is written down beside it.

## Decide before implementing

- The reference level and the tolerance (proposed: -18 dB RMS; no tolerance proposed yet).
- The reference input: which note or notes, at which `pregain`, for how long. A level read on one note says
  little about a rig that is played across two octaves.
- What the contract does NOT cover, and whether that is fine: unison voices and chords sum on the cylinder after
  the rig (guitar 1 of Der Schmetterling has 11 unison voices, guitars 2 and 3 have 7 and 5), so the level at a
  cylinder compressor still depends on how many voices play.
- Whether the `guitarShape` stage at the rig's end ("the microphone") is a part under the contract or the place
  where the rig's output level is set once.

## The work

Write the contract down where the rig parts live, in a few sentences. It is the input of KLANG-00461 (the level
check) and KLANG-00462 (the calibration).

## Acceptance

The contract is written, with its numbers decided by the maintainer.

## History

- 2026-11-18: created, from the Der Schmetterling session of 2026-11-17 and 18.
