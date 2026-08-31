# `voiceElapsedFrames` means two different things (pre-existing, found 2026-08-21)

**Status:** ✅ FIXED (archived 2026-08-31) · **Filed:** 2026-08-21 · **Fixed:** 2026-08-27

Not fixed as its own task: the block-framing invariance audit swept it up as **"Confirmed instance 1"**
(`docs/plans/block-framing-invariance.md:39`). `IgniteRenderer.kt:44` now reads

```kotlin
signalCtx.voiceElapsedFrames = (ctx.blockStart + ctx.offset - startFrame).toInt()
```

so all four sites agree on one definition, **the elapsed count AT buffer index `ctx.offset`**, and
`Voice.kt:157-160` (`offset = max(blockStart, startFrame) - blockStart`) makes a voice's first block
land on `voiceElapsedFrames == 0`. That is now the stated contract of the whole audit: *a voice's
first `generate` call has `voiceElapsedFrames == 0`, and rendering is contiguous thereafter.*
Guard: `audio_be/.../voices/IgniteOnsetOffsetSpec.kt`, commit `ef71331a`.

**Both "before acting" questions below were answered by measurement, and both the way this file
guessed.** It was real at runtime (44.1 kHz / 128 frames, 10 ms attack, `startFrame = 76`: 52 silent
frames, then a step from 0 to 0.0222 in ONE sample), and it was audible — it is the guitar knock this
file suspected when it pointed at `GuitarClickHuntTest.kt`. It hid for months because the strip VCA
was simultaneously in its own correctly-timed, de-clicked attack and attenuated the step 10-20x;
`.adsrOff()` (the envelope-ownership work,
`docs/tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md`) replaced that with unity gain and
exposed it at full size. Present since `f36b9740`.

⚠️ **What this file asked for and did NOT get: the re-pinning.** Four spec sites still set the
PRE-FIX shape `voiceElapsedFrames = -offset`, and three assert in a comment that it is what production
does, which is no longer true:
`IgnitorDslOptimizerRenderSpec.kt:64`, `ConstantFoldParitySpec.kt:64`, `EqCoreSpec.kt:546`,
`EqIgnitorSpec.kt:648`. They are A/B parity specs so they still pass — both sides get the same
context — but a negative clock clamps any envelope in them to zero, so those rows compare near-silence.
The correct shape is `offset = 37, voiceElapsedFrames = 0`. Carried forward in
`docs/tasks/audio-backend-audit.md` §5.2 rather than done here, because it wants a test run and the
build lock was held elsewhere on 2026-08-31.

Everything below is the original 2026-08-21 filing, kept as the record of how it was spotted by
reading alone.

---

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
