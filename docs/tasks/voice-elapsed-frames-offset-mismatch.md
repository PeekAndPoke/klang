# `voiceElapsedFrames` means two different things (pre-existing, found 2026-08-21)

Found by a DSP reviewer during the D4 optimizer review, as an out-of-scope observation. **Not
caused by D4 and not fixed by it** — the optimizer's bit-identity oracle sees the same behaviour
on both sides, so the fusion work is unaffected. Filed so it is not lost. NOT yet verified by
running anything: this is from reading the sources.

## The mismatch

`IgniteRenderer` sets `voiceElapsedFrames = blockStart - startFrame`, i.e. the voice-relative
frame of **buffer index 0**.

Every time consumer instead reads it as the voice-relative frame of the **window start**:

- `IgnitorEnvelopes.kt` — `AdsrIgnitor` starts `absPos = ctx.voiceElapsedFrames` at `i = ctx.offset`
- `PitchModFactories.kt` — `pitchEnvelopeModIgnitor` uses `voiceElapsedFrames + (i - offset)`
- `IgnitorFilters.kt` — `computeFilterEnvelope` does the same

These agree only when `offset == 0`.

## What it would cause

On a voice's FIRST block with a mid-block onset (say offset 37), the envelope is evaluated at
voice frame `-37` where the voice's frame 0 actually is, so roughly 0.8 ms at 44.1 kHz, clamped
to level 0. Then the clock jumps forward by `offset` at the block boundary: block 1 ends at
`absPos 53` while block 2 starts at `91`.

On a slow attack that step is a few percent of the attack range, landing exactly on a block
boundary. That is the shape of an onset click, and there is already a `GuitarClickHuntTest.kt`
in the tree, so it may be worth checking whether this is the cause of something already noticed.

## Before acting

Verify by experiment first, not by reading. Two questions:

1. Is the discrepancy real at runtime, or does some caller normalise `offset` before these
   consumers see it?
2. Is it audible? A few percent of an attack range at one block boundary may be inaudible on
   fast attacks and only matter on slow ones.

Whoever fixes it must decide which definition is canonical and make all four sites agree, then
re-pin the mid-block onset rows that currently encode the present behaviour (`EqCoreSpec`,
`EqIgnitorSpec`, `IgnitorDslOptimizerRenderSpec` all set `voiceElapsedFrames = -offset` to match
production as it is today).
