# KLANG-00415: master stop click on insert-only chains

> **Status (2026-11-05): DONE.** A stopped engine's master is kept alive while its output is audible, whatever the chain; the record is "Done 2026-11-05". Left: one question for the maintainer (the master's silence floor) and one found leak (a tail chain with an offset).
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

## Done 2026-11-05

**Measured** (`MasterStopRingingInsertSpec`'s rig, 44.1 kHz: a 220 Hz sine at 0.5 for 0.5 s with a 5 ms release
through a master of one stage, the playback stopped at once, rendered without the house stage until the engine reads
idle; the step is the last sample the dispatcher renders):

| master | step at disposal, before | after | idle, before | after |
|---|---|---|---|---|
| eq bell +24 dB, q 30, 220 Hz | 0.30 (-10 dBFS) | 8.7e-5 | 0.53 s | 2.37 s |
| `bell` body | 0.026 | 4.1e-6 | 0.53 s | 0.99 s |
| phaser at 200 Hz | 7.2e-4 | 2.2e-5 | 0.53 s | 0.55 s |
| `gain(0.9)` (dry) | 0 | 0 | 0.53 s | 0.53 s |
| `plus(0.1)` (sounds on silence) | 0.1 | 0 (released) | 0.53 s | 25.0 s |

The eq bell rings longer than the review's estimate (time constant about 43 ms): a boost of 24 dB narrows the
resonance, and it takes about 1.8 s to fall under -80 dB.

**The fix** (`MasterBus`, `PlaybackEngine`): an audible master output keeps the engine alive whatever the chain, and a
silent output of a chain with no stage that can ring is the end of it at once (`updateTailState` asks the output
first). `isRinging` reads only while the bus is active: a bus switched to the empty chain is no longer processed, so
its last answer would be stale. A master that still sounds with nothing of the engine's own left (no voice, no active
orbit) and no stage that can ring counts as an endless tail (`MasterBus.ringsWithoutTail`, in
`PlaybackEngine.sustainsItself`), so the hold of 20 s and the release of 4.5 s of decision (j) end it: by then an
insert has rung out long ago, and what is left (an offset, a `plus` of an oscillator) can never end. The guard on the
engine's own sound keeps an orbit's long finite echo under a dry master from being released at the hold.

**Specs:** `MasterStopRingingInsertSpec` (5 rows: the three inserts rung out under 1e-4 and later than an unmastered
engine; a dry master disposed in the very block an unmastered engine is; a switch to the empty chain while the tone
sounds lets the stopped engine go; an orbit's finite echo under a dry master not released at the hold; an offset
released after the hold), and in `MasterOutputChainSpec` the limiter-only row rewritten to the new law (held while it
sounds, let go on its first silent block) and a row for a lookahead limiter (below). Mutation: 7 of 7 killed over two
campaigns. Full `audio_be` suites green (JVM 2947, JS 2842); corpus: 3 frozen and 13 live songs identical, Kokon and
Der Schmetterling read as edited by the maintainer's commit `f46ed64f4` of the same day (no render of them can move
here: the change decides only when a stopped engine is disposed, and the release needs 20 s after a stop where a
corpus render's tail is 2 s).

**Review:** round 1 (level 1), clean from both reviewers. The MINORs applied as one batch: a master lookahead
compressor (an authored `limiter(lookahead = ...)` or `compressor`) is asked `hasTail()` at once on a silent output of
a chain without a declared tail, so a last short hit after a gap is no longer disposed unplayed in its ring (audio
reviewer; it was so before this change too); two KDocs that said a limiter-only chain never rings, and the clause's
"an insert has rung out long before" now names its one exception (an orbit's long echo outlasting the hold leaves an
insert ringing on its residue, which is then released, inaudibly, over the release's 4.5 s); the builder spec's
comment.

**For the maintainer (audio reviewer, MINOR 2):** the master's silence floor is 1e-4 (`TAIL_SILENCE_THRESHOLD`), where
the orbit deactivation and the chain swap use 1e-5 (`SILENCE_FLOOR`). The step a disposal leaves is under the floor by
construction, so about -80 dBFS today (the eq bell: 8.7e-5), at the edge of audible on headphones in a quiet room;
1e-5 costs about 2.3 more time constants of life (about 100 ms for the eq bell) and moves the disposal moment of the
tail chains too. The same question as the audit's decision D9 (KLANG-00290 section 6, item 25).

**Fixed 2026-11-05 in KLANG-00290 (engine follow-ups) item 25** (an offset is now an endless tail, released after the
hold; this ticket's `ringsWithoutTail` clause went with it), as was the floor question above. **Found, not fixed
(before this change too):** a master with a stage that declares a tail AND sounds on silence (a
reverb with a `plus(0.1)`) keeps a stopped engine for ever: its output is always audible and nothing endless is
declared, so neither the ring-out nor the release ends it (probe: not idle after 30 s). Widening the new clause to
chains with a tail would also release a long finite master echo at the hold, against decision (j). Rare (an offset or
an oscillator added at the master); a question for the maintainer.

## History

- 2026-11-05: created.
- 2026-11-05: DONE.
- 2026-11-05: DONE.
