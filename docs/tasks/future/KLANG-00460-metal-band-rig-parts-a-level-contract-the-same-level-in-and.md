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

## Found by reading the engine (2026-11-18): the proposal above needs two levels, not one

Read in `DistortionCore` and `ShapingFuncs`, not measured:

- `distort(amount, shape)` is `shape(x * 10^(1.2 * amount))` and a DC blocker. There is no make-up gain and no
  threshold: an amount of 0.15 is a gain of 1.51 (+3.6 dB) into the curve, 0.35 is 2.63 (+8.4 dB).
- Every curve bends around an input of 1.0 and is bounded: `soft` (tanh) at +-1, `tube` at +0.37 and -1, `asym` at
  +-1.
- So inside the rig the level INTO a stage is that stage's real drive knob, and every `mul` in an earlier part
  changes the character of every later part. Level and tone are one knob until the last clipper. This is the
  mechanism behind "I do not know what the rig parts output".

What follows for the contract:

- **-18 dB RMS is the wrong reference for the inside of the rig.** The parts work with peaks near 1.0, where the
  curves bend. A reference of -18 dB RMS in and out of every part would mean rewriting every clipping part with a
  boost in front and a cut behind, for no gain.
- **Two levels instead:** a rig-internal level, taken from where the parts work today (to be measured), which every
  part expects and returns; and the rig's output level into the cylinder (the -18 dB RMS proposal belongs here),
  with ONE conversion between them at the rig's end. `guitarShape` already starts with that conversion (`mul(0.18)`).
- **A saturated part sets its own output level.** Behind the high-gain preamp the level is close to fixed whatever
  came before it; behind the clean preamp it follows the playing. So "very similar output for any combination" can
  hold for the level at normal playing, not for how much the level follows the playing. That difference is the
  parts' purpose.
- **The pickups' output differs on purpose** (0.8, 1.3, 1.2: "its output sets how hard the amp is pushed"). The
  contract either exempts them or the difference moves into a named knob.

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
- 2026-11-18: findings from reading the rig and the engine's shaper code added.
