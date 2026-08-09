# Sample voice onsets were quantised to the block boundary

> Status: **FIXED + guarded**, 2026-08-07. Branch `master-dsl`.
> Found while chasing "the WAV sounds different from the browser" — this one is the timing half.

## The bug

`VoiceFactory.makeVoice` computed the exact scheduled onset for every voice:

```kotlin
val startFrame = (relativeStartTime * sampleRate).toInt()
```

…then the oscillator branch used it, and **the sample branch threw it away** and passed `nowFrame`
— the first frame of the block currently being rendered — as the voice's start frame instead:

```kotlin
val voiceDurationFrames = gateEndFrame - nowFrame
buildVoice(data, resolvedAdsr, nowFrame, gateEndFrame, …)   // ← nowFrame, not startFrame
```

So every sample onset was rounded **down** to a block boundary and fired **early** by
`0 .. blockFrames-1` frames.

The mechanism to do it right was already there and already in use by osc voices — `Voice.render`
clips the voice into the block itself:

```kotlin
val vStart = maxOf(ctx.blockStart, startFrame)
val offset = vStart - ctx.blockStart      // Voice.kt
```

The sample branch simply wasn't using it.

## Why it is audible

It is **not a constant offset** — a constant few ms early would be inaudible. It is **jitter**, because where a hit
falls inside its block varies from hit to hit. Two hits scheduled one frame apart collapse onto the same output frame if
they share a block; two hits scheduled 1 frame apart across a block boundary stay `blockFrames` apart.

| Path                            | Block | Onset jitter |
|---------------------------------|-------|--------------|
| Browser worklet                 | 128   | ±2.7 ms      |
| Offline WAV render (as shipped) | 512   | ±10.7 ms     |

±10.7 ms of random per-hit jitter on percussion is the "the render has broken timing" feeling. ±2.7 ms live reads as
looseness rather than as a fault, which is why it survived this long.

Note the two numbers differ — so this bug and the block-size split ([block-size-parity](20260807-block-size-parity.md))
compounded: the WAV was not merely different from live playback, it was *more* jittery than live playback.

## The fix

```kotlin
// audio_be/.../voices/VoiceFactory.kt
val sampleStartFrame = maxOf(startFrame, nowFrame)
val voiceDurationFrames = gateEndFrame - sampleStartFrame
buildVoice(data, resolvedAdsr, sampleStartFrame, gateEndFrame, …)
```

`maxOf`, not a bare `startFrame`, because `nowFrame` was serving a second, legitimate purpose that has to be preserved:
it is a **floor** for the late case.

A voice whose scheduled start is already behind the current block (FE/BE clock skew, command latency, a worklet stall)
would otherwise run its ADSR from a past frame while `SampleIgnitor`'s playhead still begins at the start of the PCM —
envelope and sample desynced, which is the
"late-start artifact" the original comment referred to. Clamping to `nowFrame` keeps that. Voices later than
`5 * blockFrames` are dropped upstream by `VoiceScheduler.oldestAllowedSec` anyway.

The floor **bounds** that desync to one block; it does not eliminate it. Commands are drained before the cursor
advances, so a voice arriving between render blocks is promoted against the block just rendered and first sounds in the
next one. The old code hit that worst case on *every* voice, so this is strictly better — but it is not a guarantee, and
the comment in the source says so.

## Guard

`audio_be/src/commonTest/kotlin/voices/SampleVoiceOnsetSpec.kt`

7 cases. Drives a bare `PlaybackEngine` with its own `BackendClock` — **not** the dispatcher's
`renderBlock`, whose master limiter lookahead (5 ms) would shift the onset under test, and whose DC blockers would
annihilate the DC probe. Owning the clock means `cursorFrame` advances in step with the render, so `ensureEpoch`'s
`clock.nowSec()` sees the block actually being rendered.

**On-time (4 cases, parametrised over non-block-aligned onsets):**

- every frame before the scheduled onset is silent — the assertion that kills the old behaviour
- the onset lands within a few frames of the schedule (slack for the ADSR attack leaving 0.0)
- the render is not vacuously silent

Deliberately **not** `blockFrames + 1`: with the onset quantised to the boundary, the single frame in front of it is the
attack at exactly 0.0, so that case cannot distinguish the two behaviours (verified — it was the one case that passed
under mutation).

**Jitter (1 case):** two voices scheduled one frame apart come out one frame apart. Under block-quantised onsets they
collapse onto the same frame whenever they share a block.

**Late (2 cases):** the floor needs its own guard, and it has to assert the **envelope**, not the onset frame —
`Voice.render` clamps the render window itself, so the first audible frame is the same either way. What differs is
envelope phase: built with a past `startFrame`, the first rendered frame already carries the attack ~100 frames in
(≈0.032) instead of 0.0. The second case covers
`oldestAllowedSec` and is honest in its comment that it does *not* guard the floor.

**Mutation checks (both halves of the `maxOf`):**

| Mutation                        | Result                                      |
|---------------------------------|---------------------------------------------|
| `= nowFrame` (the original bug) | 5 red — all on-time cases + the jitter case |
| `= startFrame` (floor deleted)  | 1 red — the late case, on `frames[0]`       |
| restored                        | 7 green                                     |

The second mutation is the one that matters: before the review added the late cases, deleting the
`maxOf` passed the entire 966-test suite. The `openingPeak` ratio assertion was also tightened from 5% to 0.5% — the
mutation lands at ≈4.6%, so the original threshold sat 9% from not discriminating.

## Not changed

- **Oscillator voices** were always sample-accurate. Untouched.
- **`nowFrame` stays in the signature** — it is still the late-case floor, and osc voices still ignore it. The KDoc now
  says which is which.
