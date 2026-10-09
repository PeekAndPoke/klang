# A chain swap may cut an insert stage that still rings

Status: **found 2026-10-09, not measured, not started.** Found in review round 1 of the Katalyst `distort` stage
([`katalyst-distort-stage.md`](katalyst-distort-stage.md)), where the same mechanism was measured for that stage and fixed.

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
