# KLANG-00293: A chain swap may cut an insert stage that still rings

> **Status (2026-11-04): DONE.** Measured (every insert kind cut audibly) and fixed in `ChainSwap`; the record is "Done 2026-11-04" below, with what it changes beyond the cut and the master's residue.
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

Status: **found 2026-10-09, not measured, not started.** Found in review round 1 of the Katalyst `distort` stage
(KLANG-00292 (katalyst distort stage)), where the same mechanism was measured for that stage and fixed.

## What the code does (read 2026-10-09)

`ChainSwap` (`audio_be/.../ChainSwap.kt`, the `Fading` state's `process`) ramps the leaving chain's INPUT to 0 over
`Crossfade.XFADE_SECONDS` (0.06 s) and adds its output at full weight. When the ramp is complete it asks
`out.hasTail()`: true drains the chain, false RETIRES it at once. No mix scan is involved.

The insert stages answer `hasTail()` false on a different argument, written in `KatalystResonatorEffect.hasTail`'s KDoc:
whatever they hold lands in the orbit mix, and `Cylinder.isMixBufferSilent()` scans that buffer after the chain. That
holds for the orbit's DEACTIVATION. It does not hold for the chain swap's retirement, which never scans.

The MASTER is a third asker with a gate of its own: `MasterBus.isRinging` asks `hasTail()` only for a chain whose
`KatalystChain.declaresTail` is true (reverb, delay, and since 2026-10-09 distort). A master chain with only a body,
a vowel or a phaser is never asked, so a stopped playback disposes the engine on the same terms (round 2 of the
`distort` stage found and fixed this for that stage). The three askers are written down in `audio/ref/katalyst.md`,
"Writing a stage lifecycle".

## The suspicion, UNVERIFIED

An insert whose state can still put audio into a silent input is cut when its chain is swapped out:

- **body and vowel** (`KatalystResonatorEffect`): narrow resonator modes ring after the input stops; a high-Q mode at a
  low frequency rings for tens of milliseconds;
- **the phaser** (`KatalystPhaserEffect`): feedback up to `PhaserCore.MAX_FEEDBACK` (0.95);
- **the EQ** (`KatalystEqEffect`): a narrow bell or notch rings briefly.

How much of that ring survives the 60 ms input ramp, and whether the cut is audible, is NOT measured. For the
`distort` stage it was: an asymmetric shape's DC blocker decay was cut by a step of 0.01 to 0.05 on `tube` and up to
0.37 on `rectify`, fixed by reporting the decay as a tail (`KatalystDistortEffect.hasTail`, `DcBlocker.holdsEnergy`).

## To do

1. Measure per stage: a swap away from a chain holding the stage, at its ringiest settings, the step at retirement.
2. Where it is audible, answer `hasTail()` from the state in O(1) (the `distort` stage's pattern), so the swap drains.
3. Correct the insert-vs-send rule in `KatalystResonatorEffect.hasTail`'s KDoc: the mix scan covers deactivation, not
   the swap's retirement. (Its text is the rule's home; the `distort` stage's KDoc already states the swap case.)

## Done 2026-11-04

**1. Measured** (a throwaway probe on `CylinderSwapRig`, 44.1 kHz: 200 blocks of a 220 Hz sine at 0.5 or noise at
0.5 through a chain holding the stage at wet 1, then a swap to a chain of gain 0, so after the fade the output is the
leaving chain alone; the step is the last sample before that output falls to exact zeros). Every insert kind was cut
audibly, none drained:

| stage | worst step at retirement | |
|---|---|---|
| body `bell` (sine) | 0.28 | -11 dBFS |
| body woods (`spruce`, `maple`, `mahogany`, `oak`; sine) | 0.055 to 0.071 | -23 to -25 dBFS |
| vowel (noise) | up to 0.023 | -33 dBFS |
| phaser, centre 200 Hz, sweep 100 Hz (sine) | 0.026 | -31 dBFS |
| eq bell +24 dB at 220 Hz, q 10 to 100 (sine) | 3.0 to 4.0 | +9.5 to +12 dBFS |
| eq bell +24 dB elsewhere (noise) | up to 0.055 | -25 dBFS |
| control: the stage off (body or vowel `none`) | 1.1e-4 | -80 dBFS, the ramp's own last value |

**2. Fixed in the swap, not per stage** (the ticket proposed an O(1) `hasTail()` per stage, the `distort` pattern; one
test in `ChainSwap` covers every insert and anything after one, a gain or a compressor's makeup included). Where the
fade ends, `Fading` drains (or, when a request waits, cuts) a leaving chain that has a tail OR rang at or over
`SILENCE_FLOOR` on the frames of its last block after the fade had ended (`Crossfade.settledFrom`: its input silent
there, its output out of its own latency). A chain that does not ring puts out exact zeros there, so it retires where it
always did: no drain, and a parked request is not turned into a 0.15 s cut. When the fade ended exactly on the block's
end (no such frame: 0.06 s at 96 kHz is 45 blocks), one more fade block runs first. Re-measured on the fix: all 206
probe rows that ring drain, the worst final step 9.9e-6; the two `none` rows retire as before, with the same step.

**3. The KDoc** of `KatalystResonatorEffect.hasTail` and the three askers in `audio/ref/katalyst.md` ("Writing a stage
lifecycle") now say that the swap scans too.

**Proof:** `ChainSwapRingingInsertSpec` (the three ringing kinds drain and leave a step under 1e-4; a dry chain and a
dry chain with a 5 ms lookahead neither drain nor cut, also with a parked request; a ringing chain with a parked
request is cut; at 96 kHz the end is decided one block later); mutation 4 of 4 killed. Full corpus identical (no song
swaps away from a chain that rings; Kokon and Der Schmetterling at the maintainer's edits' hashes).

**Review:** round 1 (level 1) clean from both reviewers; the MINORs applied as one batch (two stale KDocs that still
said the swap retires a ringing insert, in `Crossfade` and `KatalystCompressorEffect.hasTail`; the comment on the
constant-output case; a line over 140).

**What else it changes** (from the review, no corpus song is affected; the first two accepted by the maintainer
2026-11-05):
- A ringing insert now drains like a reverb, so it also falls under the parked-request rule (KLANG-00318 (chain swap request during drain)): a request
  that arrives during its fade waits for the 0.15 s cut. A pattern that switches chains holding a body per 16th at
  120 bpm (125 ms apart) now lands each switch after about 0.21 s instead of about 0.065 s, and the latest request
  wins, so every other switch is skipped. The latency reverb chains already have.
- A chain that puts out a constant on silence (an arithmetic offset, `plus(0.1)`) reads as ringing: it drains to the
  20 s cap and is released (about 4.5 s), where it used to leave a DC step; meanwhile two chains run, the orbit stays
  active, and at the master a stopped engine is held that long.
- Where the fade ends one frame before a block's end (only through a lookahead), the decision rests on one frame; a
  ring crossing zero there is retired with a step of about one sample's change. Rare (about 2e-5 for the `bell` body);
  closable by running the extra block whenever the window is short, not only when empty.
- Where the fade ends exactly on a block's end, a chain that does not ring retires one block later (a parked request
  lands one block later), inaudible.

**Left, not this ticket:** the master's stop-time keep-alive (`MasterBus.isRinging`) is gated by `declaresTail`, so a
master chain holding only inserts is never kept alive. Once the voices are gone and every orbit is quiet (about 27 ms
at 48 kHz) the engine is disposed. The audio reviewer's scenario: a master eq bell of +24 dB at q 30 at 220 Hz
(time constant about 43 ms) on a song whose last sound stops faster than that still rings at about half its level at
disposal, an audible click; a slower release keeps it under -76 dB. The phaser is the other candidate. The cheap fix
proposed: let the output audibility check that already runs (`isAudible`, threshold 1e-4) keep `ringing` true also
when no stage declares a tail. Its own ticket since 2026-11-05: KLANG-00415 (master stop click on insert-only chains).

## History

- 2026-10-20: numbered KLANG-00293 (was `docs/tasks/chain-swap-cuts-ringing-inserts.md`).
- 2026-10-27: part of KLANG-00353 (The Motor: correct at the edges), the epic grouping.
- 2026-11-04: DONE.
- 2026-11-05: the maintainer accepted the parked-switch latency and the constant-output drain; the master's residue
  went to KLANG-00415.
