# KLANG-00459: Metal band instruments: one band, the same instruments in every metal song

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00356 (Sound sources: instruments, samples, soundfonts)

## Why this exists

The maintainer's goal (2026-11-18): the "metal" songs are all played by the same band on the same instruments.

Today each song carries its own copy. Kokon and Der Schmetterling hold the same guitar rig (four pickups, four
pedals, four preamps, three power amps, four cabs, 19 parts), and for weeks the maintainer has copied parts back and
forth between them by hand.

A second problem came up in the same session: nobody knows what level a rig part gives out, so every new
combination of parts has to be rebalanced, and a compressor behind the rig meets a different level each time. The
maintainer: "one source of constant confusion for me is that I do not know what the rig parts output".

## The tickets

In the order proposed to the maintainer. Calibrating comes before sharing: sharing first would only move
uncalibrated parts to a place where a change affects two songs.

1. KLANG-00460 (the level contract): what "the same level in and out" means for a rig part.
2. KLANG-00461 (the level check): each part rendered with a reference input, its level read.
3. KLANG-00462 (the calibration): the parts brought to the contract.
4. KLANG-00463 (the shared guitar rig): one place, imported by the songs.
5. KLANG-00464 (the bass and the drums): the rest of the band, the same way.

## Notes

- **Parameters for the rigs** may be needed later (maintainer, 2026-11-18). The first case is already there: the
  rig's `snareCut` notches at the song's snare frequency. It is part of KLANG-00463.
- **Related:** KLANG-00457 (named nodes report their level) would show a part's level while a song plays; the check
  of KLANG-00461 does it offline.
- **The cylinder side of the band** (the guitars' compressor, its make-up gain, the reverb) was set by ear in Der
  Schmetterling on 2026-11-17 and is not part of any ticket here yet. It becomes learnable once the rig's level is
  known, which is the point of the contract.

## History

- 2026-11-18: created, from the Der Schmetterling session of 2026-11-17 and 18.
