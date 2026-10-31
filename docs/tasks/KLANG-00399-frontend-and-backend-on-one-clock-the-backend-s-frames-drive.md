# KLANG-00399: Frontend and backend on one clock: the backend's frames drive playback, an OfflineBackend tests it

> **Status (2026-10-31): DESIGNED.** Analysis and design agreed with the maintainer; step 1 can start, steps 2 to 4
> touch `audio_be` and wait for Klang-Main-Dev's DSP refactoring to leave it.

## Why this exists

The maintainer, 2026-10-31: "The synchronization between the audio backend and the frontends plus the playbacks
[...] works mostly ok, but there are cases where it does not." The cases seen:

- **A tempo change during a song** goes wrong, the more the longer the song has played ("I think this exactly what
  I experience").
- **Closing the laptop and opening it again** "forces a page reload as frontend and backend are hopelessly out of
  sync".
- **On the phone, when it stutters,** "the next voices are "lost" or only half cycles are played".
- "When the backend starts to stutter and misses frames, the frontend clock runs away. So we need to somehow update
  the frontend clock from the number of frames that were processed by the backend. So no wall clock there, or only
  to calculate the difference between the last sync with the backend."

The analysis below is from reading the code (2026-10-31), not from a test; step 2's specs are to prove it.

## How it works today

**Three clocks.**

- **Frontend `KlangTime`** (`audio_bridge/src/jsMain/kotlin/KlangTime.kt`): `Date.now()` taken when the instance is
  made, plus `performance.now()` since. Each `KlangPatternScheduler` and `BackendClockSync` make their own.
- **The backend's timeline** (`BackendClock`, `audio_be`): the worklet sets `startTimeSec = Date.now()` at its first
  `process()` (`KlangAudioWorklet.kt`), then "now" is `startTimeSec + cursorFrame / sampleRate`. It advances with
  the frames rendered, not with the wall clock.
- **The UI** times highlights and the voice rain against plain `Date.now()` (`CodeMirrorHighlightBuffer.kt`,
  `VoiceRain.kt`).

**A playback.**

1. `KlangPatternScheduler.runPlayback` waits for `BackendReady`, preloads samples, records `startTimeMs` on its own
   clock, queries the first cycle and sends it in one `ScheduleVoices`.
2. Every voice carries `startTime` (relative: cycle x seconds per cycle) and `playbackStartTime` (`startTimeMs`).
3. On a playback's first voice the backend sets `epoch = max(playbackStartTime, backend now)`
   (`VoiceScheduler.ensureEpoch`); a voice plays at `epoch + startTime`. The epoch never changes after.
4. Every 16 ms (`delay`) the frontend loop queries up to `lookaheadCycles` (2) ahead of its own "now" (frontend
   clock minus `startTimeMs`), one cycle per chunk, every chunk up to the target.
5. The backend drops a voice whose start has passed ("no late voices, ever", `VoiceScheduler.promoteScheduled`) and
   counts it.
6. An edit sends `ReplaceVoices` with a cutoff at frontend now + 0.2 s; the backend applies it against its epoch.
7. UI signals (`VoicesScheduled`, `CycleCompleted`) carry frontend times plus `BackendClockSync.offsetSec`, an
   average of `backendNowMs - frontendNow + outputLatency` from the diagnostics.

## What goes wrong

1. **A tempo change moves the frontend's origin, not the backend's.** `updateRpm` recomputes `startTimeMs` so the
   current cycle keeps its wall time under the new tempo; new voices are timed against the new `startTimeMs`, but
   the backend adds them to the old epoch. The error is about (cycles played) x (old seconds per cycle - new):
   at cycle 60, 30 to 33 rpm shifts everything by ~11 s, more than the lookahead. Faster: every voice lands in the
   past and is dropped (silence); slower: a gap, then the UI runs ahead of the sound. Called by the editor's rpm
   knob (debounced 150 ms, `KlangCodePlaybackCtrl`) and by Klang Amp. No spec covers `updateRpm`.
2. **The epoch compares two clocks as if they were one.** `max(playbackStartTime, backend now)` holds only while
   frontend `KlangTime` and the backend timeline agree. They agree at start to about a millisecond, then part: the
   audio clock drifts against `performance.now()` (tens of ppm), and a pause of the audio thread (sleep,
   suspension, a device change, a stutter that renders fewer blocks) leaves the backend behind. Behind, a new
   playback's epoch lies in the backend's future: silence that long before the first note, highlights early.
3. **The clock sync cannot see 2.** `Diagnostics.backendNowMs` is the worklet's `Date.now()`
   (`PlaybackEngineDispatcher.emitDiagnostics`, `performanceTimeMs = { Date.now() }`), not the timeline: the sync
   compares wall clock with wall clock. Its "snap on a suspension" branch can never fire for a suspension.
4. **Each playback starts later than the frontend believes,** by the message transit, the first cycle's query time
   and any clock difference. The frontend's "now" runs ahead of the backend's by that much for the whole playback:
   highlights and the cycle counter early, edits applied later than the 0.2 s grace says.
5. **The lookahead is counted in cycles.** At 120 rpm two cycles are one second. A main-thread stall (GC, the
   visuals, a compile, timers throttled on a dimmed phone) or a late-handled `ScheduleVoices` on an overloaded audio
   thread makes a chunk arrive late; its voices before "now" are dropped, the rest play: the half cycle.
6. **After a pause the loop floods.** `while (queryCursorCycles < targetCycles)` fetches every missed cycle: after
   30 minutes asleep it sends 30 minutes of voices at once, which the backend queues behind its frozen position.
   Edits then cut 30 minutes ahead and no longer apply: the reload.
7. **Frontend clocks disagree among themselves.** The UI compares with `Date.now()`, signals are `KlangTime`; after
   an NTP adjustment or a sleep the two part, and instances made at different times disagree.
8. **`localFrameCounter` is an `Int`** (elapsed seconds x sample rate): it overflows after ~12.4 h of one playback
   and the scheduler stops fetching.

All of it has one cause: the backend's timeline is not the reference, and the frontend never measures it.

## The design

The stone rule stays: "only seconds cross the wire, the backend never learns about cycles". Cycles stay in the
frontend; the seconds on the wire become a playback's own seconds, counted in the backend's frames.

1. **Playback seconds.** A playback's epoch is set by the backend at its first voice, plainly "backend now".
   `playbackStartTime` leaves the wire; a voice's `startTime` stays relative to the epoch.
2. **The backend reports where it is.** The diagnostics (every ~20 ms) carry the timeline's position,
   `startTimeSec + cursorFrame / sampleRate`, in place of `Date.now()`. When a playback's epoch is set the backend
   says so once (`Feedback.PlaybackAnchored(playbackId, epochSec)`). The frontend reads a playback's position as
   timeline position minus epoch.
3. **`PlaybackClock` on the frontend:** the last reported position plus `performance.now()` elapsed since that report
   arrived, capped at a few reports' worth (~50 ms) past it. A backend that stutters or stops reports slower or not
   at all, and the clock slows or stops with it. No `Date.now()` in the sync, no `KlangTime` anchors.
4. **A tempo map instead of a moving origin:** cycle to playback seconds as linear pieces. A tempo change appends a
   piece at "now + grace"; voices already sent stay valid, the epoch never moves. Fixes 1.
5. **The scheduler is driven by the reports, not by a timer.** Each report tops up the lookahead from
   `PlaybackClock` + tempo map. The lookahead is counted in seconds, with a floor (1.5 to 2 s, more on a phone if
   drops show). A cursor behind "now" jumps to "now" instead of fetching what is already late. The edit cutoff,
   the cycle counter and the reemit use the same clock.
6. **The UI on `PlaybackClock`:** signals carry playback seconds (or cycles); highlights, the voice rain, the ADSR
   signals and the cycle counter compare them every frame with `clock.now() - outputLatency`.

**What goes:** `BackendClockSync`, `startTimeMs`, `playbackStartTime` and `epoch = max(...)`, `localFrameCounter`
and the scheduler's own `KlangTime`, the 16 ms loop, absolute times and offsets in the signals, the UI's
`Date.now()` for audio.

## Testing: an OfflineBackend

The maintainer, 2026-10-31: "I think an OfflineBackend, where we are able to drive the clock might the right
solution. This would also help with the offline renderer moving onto a real backend implementation. Currently the
offline renderer duplicates quite some code."

**Duplicated today:**

- Two hosts: the live backends run on `PlaybackEngineDispatcher` (engines per playback, the master, diagnostics,
  commands); offline has its own `KlangAudioRenderer` (one engine, the master, the same block loop).
- Two frontends: `KlangOfflineRenderer` queries the whole song, registers the inline DSLs, loads samples, converts
  cycles to seconds and schedules by hand, what `KlangPatternScheduler` does live. Offline renders and the sprudel
  render specs therefore never pass the real playback path.

**Layer 1, `OfflineBackend` (in `audio_be`):** an `AudioBackend` on the same `PlaybackEngineDispatcher`, rendering
only when told: `render(blocks)`, `stall(ms)` (frontend time passes, nothing renders), `sleep(ms)`. The frontend's
time source is a fake in the same spec. Specs read which voices started at which frame and how many were dropped:
exact, no audio compared.

| Spec | Today (expected red) | After |
|---|---|---|
| tempo 30 to 33 rpm at cycle 60 | voices ~11 s off, dropped | the next voice where the tempo map says |
| `sleep(30 min)`, then on | a flood, edits cut at the wrong place | on from the backend's position |
| a new playback after the sleep | the first note 30 min late | the first note at "backend now" |
| `stall(1.5 s)` at 120 rpm | half a cycle dropped | nothing dropped |
| 13 h of one playback | the scheduler stops (`Int`) | plays on |

They are written before the fix: red on today's code, green after.

**Layer 2, offline rendering through the real player:** `KlangPlayer` + `OfflineBackend` in lockstep: render, report,
top up, until the end plus the tail. `KlangOfflineRenderer` becomes a thin call; `KlangAudioRenderer` goes.

## Decide before implementing

- **Samples in lockstep.** Offline loads every sample before the first block; live loads some ahead without waiting,
  and a voice whose sample is missing is dropped. Proposal: in lockstep the backend does not render while a sample
  load is pending.
- **Renders stay the same, sample for sample.** `KlangOfflineRenderer` passes `phasePoolSeed = 1`; the dispatcher has
  no such parameter yet. A check that a render is identical before and after layer 2 comes first.
- **Warmup and latency offline:** no `WarmupRunner`; the tail covers `latencyFrames` as now.
- **The stutter on a device:** when the worklet is overloaded, is `process()` called less often (frames fall behind
  wall time) or in a burst? The frame count is the truth either way; the cap on `PlaybackClock`'s extrapolation
  covers both. To see once on a phone, with the diagnostics' dropped-voice count.
- **Report rate and message:** the position in the existing diagnostics (one timeline for all) plus one
  `PlaybackAnchored` per playback, rather than a position per playback every block.
- **The JVM backend** gets the same reports (it shares the dispatcher); its `KlangTime` stays for performance
  measurement only.

## The work

1. **The tempo map** (frontend, `klang`): a pure class, cycle to playback seconds in pieces; `updateRpm` appends a
   piece instead of moving `startTimeMs`. Its spec in klang's common tests. Fixes the tempo jump on its own, with
   no change in `audio_be`.
2. **`OfflineBackend`, layer 1** (`audio_be`) and the specs of the table, red on today's code.
3. **The rework:** the backend reports its timeline and the anchor; `PlaybackClock` replaces `BackendClockSync`; the
   scheduler driven by reports, lookahead in seconds, the cursor's jump; `playbackStartTime` and `epoch = max(...)`
   gone; the UI consumers on `PlaybackClock`. The specs turn green.
4. **Layer 2:** the offline renderer on the real player in lockstep; `KlangAudioRenderer` deleted; the render specs
   (klang, sprudel) on the real path; a render identical before and after.

## Acceptance

- The specs of the table green on `OfflineBackend`, each mutation-checked (the mandatory tier: engine and wire).
- In the app: a tempo change at any point of a song keeps the beat; after a laptop sleep playback goes on without a
  reload; on a phone a stutter delays the sound but drops no voice that arrived in time.
- No `Date.now()` and no `KlangTime` in the frontend's audio sync; `BackendClockSync` deleted.
- One host (`PlaybackEngineDispatcher`) for live and offline; renders identical before and after step 4.

## Related

- KLANG-00398 (a song started while the Motor loads plays on after its page is gone): the same scheduler, a separate
  fix.

## History

- 2026-10-31: created from the analysis with the maintainer (Klang-Research).
