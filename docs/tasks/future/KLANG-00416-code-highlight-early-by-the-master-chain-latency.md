# KLANG-00416: code highlight early by the master chain latency

> **Status (2026-11-05): IDEA.** Split out of KLANG-00137 (maintainer, 2026-11-05).
>
> **Part of:** KLANG-00357 (Effects: the Katalyst and the master)

## Why this exists

The live front end tells every visual that follows the audio (the code highlight, the block highlight) how late the
sound reaches the speaker: `JsAudioBackend` adds `AudioContext.baseLatency`, the device's output latency and the
house limiter's fixed lookahead (`MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS`). It leaves out the latency of the
song's own master chain (`MasterBus.latencyFrames`: a compressor's or limiter's authored `lookahead`, at most
`Compressor.MAX_LOOKAHEAD_SECONDS` 0.05 s per stage, and an oversampling region's conversions). So with such a master
the highlight runs early by that much.

Found by the review of KLANG-00137 (master dsl followups) §8 on 2026-10-23; §8 fixed the same gap for the offline
render. Today's songs author at most 5 ms of master lookahead, under what an eye sees; an authored 50 ms lookahead
would show.

## Decide before implementing

- **Where the number travels:** the worklet knows the chain's latency, the main thread does not. The simplest path is
  a field on the `Diagnostics` feedback (the master chain's latency in ms, per playback or the largest), added to
  `outputLatencyMs` where the house limiter's lookahead is added today. A wire change.
- **Per playback or one number:** two playbacks with different masters run late by different amounts; the highlight
  is per playback.
- **During a swap:** the chain leaving and the chain arriving can differ in latency for the length of the fade. Taking
  the arriving chain's number at once is probably enough.

## The work

1. The latency on the feedback (`KlangCommLink.Feedback.Diagnostics`), filled from each engine's `MasterBus.latencyFrames`.
2. `JsAudioBackend` adds it to `outputLatencyMs`, as it adds the house limiter's lookahead.
3. A row that pins it: a master with a 20 ms lookahead moves the reported latency by 20 ms.

## Acceptance

- A master with an authored lookahead moves the highlight by exactly that lookahead; a master without one changes
  nothing.

## History

- 2026-11-05: created.
