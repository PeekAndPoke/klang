# P1 working notes: the data

Collected 2026-09-16 for the thesis post; the post itself is not written yet. Facts about the
phone and the desktop, with sources, in `notes-facts.md`. Both notes files are working material
and are deleted when the post is done.

## The ledger's first before/after (same frozen pieces, same machine, JVM, medians of three)

Source: `docs/benchmarks/ledger.md` (the `v0.3.14` rows written by the harness, the `v0.3.12`
rows by the transplant), detail files `docs/benchmarks/2026-09-16_10*_song_jvm.md` and
`*_v0.3.12-transplant.md`.

| piece | v0.3.12 medRTF (3 runs) | v0.3.14 medRTF (3 runs) | change | active voices then / rendering now |
|---|---:|---:|---:|---|
| guitar melody (rig) | 0.01961 (0.01861 to 0.02019) | 0.01612 (0.01539 to 0.01652) | -18 % | 1.4 / 1 |
| guitars rhythm (rig) | 0.04352 (0.04113 to 0.04533) | 0.03746 (0.03559 to 0.03849) | -14 % | 2.8 / 2 |
| marimba | 0.03388 (0.03010 to 0.03766) | 0.02897 (0.02443 to 0.03452) | -14 % | 6.2 / 6 |
| trommel | 0.06037 (0.05514 to 0.06125) | 0.03365 (0.03355 to 0.03589) | -44 % | 4.8 / 5 |
| bass | 0.01009 (0.00937 to 0.01064) | 0.00451 (0.00450 to 0.00588) | -55 % | 0.9 / 1 |
| drums (samples) | 0.03968 (0.03615 to 0.04788) | 0.01726 (0.01712 to 0.01828) | -57 % | 23.8 / 6 |

The `v0.3.14` rows are the medians of the three final runs (`2026-09-16_1102*` to `_1103*`, the
census outside the timer), the `v0.3.12` rows the medians of the three transplant runs. The second
marimba run caught a pause (peak RTF 1.2) and reads high; its two clean runs say -28 % and -14 %. The drums went from 24 active voices
to 6 rendering ones (culling): their cost per voice rose while their RTF halved, which is why RTF
per voice is not the score either; RTF per unit of work is.

## The census at HEAD (passes over the block per block, summed over rendering voices; medians of the run)

Source: `docs/benchmarks/2026-09-16_110344_song_jvm.md` (the last of the three final runs;
the census columns are identical across runs by construction, the RTF columns vary).

| piece | voices | work | traffic/sample | KiB held | ns/smp/pass (3 runs) |
|---|---:|---:|---:|---:|---:|
| guitar melody (rig) | 1 | 48 | 212 | 6 | 6.7 to 7.2 |
| guitars rhythm (rig) | 2 | 114 | 606 | 21 | 6.5 to 7.0 |
| marimba | 6 | 108 | 240 | 13 | 4.7 to 6.7 |
| trommel | 5 | 120 | 360 | 9 | 5.8 to 6.2 |
| bass | 1 | 7 | 13 | 0 | 13.4 to 17.5 |
| drums (samples) | 6 | 6 | 6 | 0 | 59 to 64 (the strip is not counted; a sample voice is one pass) |

Per note: a rhythm guitar note is 57 passes (19 of them the unison stack's voices, then the pitch
envelope, the burst, the envelope, the rig's dozen filter sections, five drives and five
shapers), 303 buffer reads and writes per sample and about 10 KiB of held state; a melody guitar
note 48 passes and 212 per sample; a marimba note 18 passes and 40 per sample; a trommel note 24
passes and 72 per sample. Traffic per pass: guitar 4.4 to 5.3, marimba 2.2, trommel 3.0; the
guitar's oversampled shapers move the block four times over and read nine taps per decimated
sample.

## The September round, what moved which piece (from the archive record and the memory)

- culling (2026-09-15): the drums and the trommel (release tails).
- polynomial sine, 2^x, e^x (2026-09-15): every voice's envelope; the bass's harmonic bank; the marimba's partials.
- analog drift per block (2026-09-15): the guitars (19 % of a drifting guitar), the marimba (13 %), the trommel (15 %).
- the optimizer's margin and folds (2026-09-15/16): measured alone, nothing.
- the polyphase decimator (2026-09-15): the guitars' shapers, 53 -> 44 µs per voice on node.
