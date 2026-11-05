# KLANG-00415: master stop click on insert-only chains

> **Status (2026-11-05): SCHEDULED.** From KLANG-00293's record: the master's stop-time keep-alive ignores a ringing insert.
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

Left from KLANG-00293 (chain swap cuts ringing inserts), its record "Left, not this ticket". The master's stop-time
keep-alive (`MasterBus.isRinging`) is gated by `KatalystChain.declaresTail`, so a master chain that holds only insert
stages (an eq bell, a phaser, a body, a vowel) is never kept alive: once the voices are gone and every orbit is quiet
(about 27 ms at 48 kHz) the engine is disposed with the insert still ringing. The audio reviewer's scenario: a master
eq bell of +24 dB at q 30 at 220 Hz (time constant about 43 ms) on a song whose last sound stops faster than that still
rings at about half its level at disposal, an audible click; a slower release keeps it under -76 dB. Not measured yet.

The maintainer, 2026-11-05: "yes we should try to avoid the click".

## Decide before implementing

Nothing yet. The cheap fix proposed in the review: let the output audibility check that already runs (`isAudible`,
threshold 1e-4) keep `ringing` true also when no stage declares a tail. Check first what that does to a master chain
with a constant output on silence (KLANG-00293's DC case, which drains to the 20 s cap at a swap).

## The work

1. Measure: the step at disposal for each insert kind at the master, the scenario above first.
2. The fix in `MasterBus`, with a spec that is red on today's code.
3. Corpus identical (no song ends on a ringing master insert, predicted; to be checked).

## Acceptance

No click at disposal for a ringing master insert; a dry master disposes as early as today.

## History

- 2026-11-05: created.
