# KLANG-00399: Frontend and backend on one clock: the backend's frames drive playback, an OfflineBackend tests it

> **Status (2026-11-01): DONE.** The maintainer's device checks (2026-11-01): a closed and reopened laptop resumes the song where it stopped, no reload; on the phone it seems to work, the browser itself tries to reload the tab at times; the maintainer keeps an eye on it. Open beside it: KLANG-00401 (sprudel).

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

The analysis is from reading the code (2026-10-31) and was checked line by line by the review; nothing has run yet.
The specs of step 2 are to prove it.

## How it works today

**Three clocks.**

- **Frontend `KlangTime`** (`audio_bridge/src/jsMain/kotlin/KlangTime.kt`): `Date.now()` taken when the instance is
  made, plus `performance.now()` since. Each `KlangPatternScheduler` makes its own; `BackendClockSync` uses one
  file-level instance.
- **The backend's timeline** (`BackendClock`, `audio_be`): the worklet sets `startTimeSec = Date.now()` at its first
  `process()` (`KlangAudioWorklet.kt`), then "now" is `startTimeSec + cursorFrame / sampleRate`. It advances with
  the frames rendered, not with the wall clock.
- **The UI** times highlights and the voice rain against plain `Date.now()` (`CodeMirrorHighlightBuffer.kt`,
  `VoiceRain.kt`).

**A playback.**

1. `KlangPatternScheduler.runPlayback` waits for `BackendReady` (2 s timeout), preloads samples (5 s timeout),
   records `startTimeMs` on its own clock, queries the first cycle and sends it in one `ScheduleVoices`.
2. Every voice carries `startTime` (relative: cycle x seconds per cycle) and `playbackStartTime` (`startTimeMs`).
3. On a playback's first voice the backend sets `epoch = max(playbackStartTime, backend now)`
   (`VoiceScheduler.ensureEpoch`); a voice plays at `epoch + startTime`. The epoch never changes after.
4. Every 16 ms (`delay`) the frontend loop queries up to `lookaheadCycles` (2) ahead of its own "now" (frontend
   clock minus `startTimeMs`), one cycle per chunk, every chunk up to the target, without yielding.
5. The backend drops a voice whose start has passed ("no late voices, ever", `VoiceScheduler.promoteScheduled`) and
   counts it; `master`, `katalyst` and `solo` state of a late event is still applied.
6. An edit sends `ReplaceVoices` with a cutoff at frontend now + 0.2 s; the backend applies it against its epoch.
7. UI signals (`VoicesScheduled`, `CycleCompleted`) carry frontend times plus `BackendClockSync.offsetSec`, an
   average of `backendNowMs - frontendNow + outputLatency` from the diagnostics.
8. Feedback reaches the frontend through `KlangPlayer`'s own 16 ms polling loop; samples go to the worklet one 512 KB
   chunk per 10 ms timer (`JsAudioBackend`); the sample lookahead does not wait for them.

## What goes wrong

1. **A tempo change moves the frontend's origin, not the backend's.** `updateRpm` recomputes `startTimeMs` so the
   current cycle keeps its wall time under the new tempo; new voices are timed against the new `startTimeMs`, but
   the backend adds them to the old epoch. The error is (cycles played) x (old seconds per cycle - new): at cycle 60,
   30 to 33 rpm shifts everything by ~11 s; several changes add up.
   - The cutoff of the resync (`afterTimeSec`) is relative to the new origin too. Faster: it lies in the backend's
     past, so `removeWhen` clears every scheduled voice, the grace window included, and the silence starts at once;
     it stays once the error passes the lookahead (at 30 to 33 rpm from about cycle 20). Slower: the old lookahead
     plays, then a gap, then the UI runs ahead of the sound.
   - `updateRpm` sends no `PatternUpdated`, so the old tempo's highlights stay scheduled beside the new.
   - The trigger is the editor's rpm knob (debounced 150 ms, `KlangCodePlaybackCtrl`). Klang Amp is not: it stops,
     sets the rpm and starts anew. No spec covers `updateRpm`.
2. **The epoch compares two clocks as if they were one.** `max(playbackStartTime, backend now)` holds only while
   frontend `KlangTime` and the backend timeline agree. They agree at start to about a millisecond, then part: the
   audio clock drifts against `performance.now()` (tens of ppm), and a pause of the audio thread (sleep,
   suspension, a device change, a stutter that renders fewer blocks) leaves the backend behind. Behind, a new
   playback's epoch lies in the backend's future: silence that long before the first note, highlights early.
3. **The clock sync measures the wrong thing.** `Diagnostics.backendNowMs` is the worklet's `Date.now()`
   (`KlangAudioWorklet.kt`, `performanceTimeMs = { Date.now() }`; `PlaybackEngineDispatcher.emitDiagnostics`), on the
   JVM the `KlangTime` singleton, never the timeline; its KDoc in `KlangCommLink` says otherwise. The sync compares
   wall clock with wall clock, so it cannot see 2. Its "snap" branch fires only where `performance.now()` pauses in
   an OS sleep while `Date.now()` jumps, and then measures that, not the timeline.
4. **Each playback starts later than the frontend believes,** by the message transit, the first cycle's query time
   and any clock difference. The frontend's "now" runs ahead of the backend's by that much for the whole playback:
   highlights and the cycle counter early, edits applied later than the 0.2 s grace says.
5. **The lookahead is counted in cycles.** At 120 rpm two cycles are one second. A main-thread stall (GC, the
   visuals, a compile, timers throttled on a dimmed phone) or a `ScheduleVoices` handled late on an overloaded audio
   thread makes a chunk arrive late; its voices before "now" are dropped, the rest play: the half cycle.
6. **A one-shot is cut short when the backend lags.** `OneShotPlayback` stops on the frontend's `CycleCompleted`; the
   `Cleanup` that follows drops every voice not yet promoted (`VoiceScheduler.cleanup`, `clearScheduled`). With the
   backend L behind, the last L seconds never play: after a stutter a jingle plays half.
7. **A sample voice whose sample has not arrived vanishes, uncounted.** `VoiceFactory`: `getSample(...) ?: return
   null`; `droppedVoices` does not see it. On a stalled or throttled phone the sample drip falls behind the voices.
8. **After a pause the loop floods, where the frontend clock ran on.** `while (queryCursorCycles < targetCycles)`
   fetches every missed cycle without yielding: after 30 minutes asleep, about 900 pattern queries in a row on the
   main thread (a freeze of seconds), and 30 minutes of voices queued behind the backend's frozen position. Edits
   then cut 30 minutes ahead and no longer apply. Where `performance.now()` pauses during the sleep, the running
   playback pauses with the frames and does not flood; there the reload comes from 2: a new playback's `KlangTime`
   takes the post-sleep `Date.now()`, the frames do not.
9. **No AudioContext state is handled.** No `statechange` listener, no `resume()` (`JsAudioBackend`): a context left
   suspended or "interrupted" after a wake (Safari, iOS, some device changes) needs a reload under any clock. To see
   on a device.
10. **Frontend clocks disagree among themselves.** The UI compares with `Date.now()`, signals are `KlangTime`; after
    an NTP adjustment or a sleep the two part, and schedulers made at different times disagree.
11. **`localFrameCounter` is an `Int`** (elapsed seconds x sample rate): `Double.toInt()` saturates after ~12.4 h of
    one playback, time freezes and the scheduler stops fetching.

Most of it has one cause: the backend's timeline is not the reference, and the frontend never measures it.

## The design

The stone rule stays: "only seconds cross the wire, the backend never learns about cycles". Cycles stay in the
frontend; the seconds on the wire become a playback's own seconds, counted in the backend's frames.

1. **Playback seconds.** A playback's epoch is set by the backend at its first voice. A voice's `startTime` stays
   relative to the epoch. A playback restarted under the same id gets a fresh epoch (`cleanup` sets `playback =
   null`): the frontend drops its anchor on stop. The realtime path (`ensureRealtimeCtx`) keeps its own epoch and
   no frontend clock.
2. **The frontend knows the backend's position** (choice A below): the timeline position, `startTimeSec +
   cursorFrame / sampleRate`, plus each playback's epoch, sent once when it is set (`Feedback.PlaybackAnchored`).
   A playback's position is timeline position minus epoch.
3. **`PlaybackClock` on the frontend:** the last known position plus `performance.now()` since it was taken, capped
   past it by a few report intervals measured, not fixed (Android delivers `process()` in bursts of 40 to 100 ms),
   and never going backwards. A backend that stutters or stops reports slower or not at all, and the clock slows or
   stops with it. No `Date.now()` in the sync.
4. **A tempo map instead of a moving origin:** cycle to playback seconds as linear pieces. A tempo change appends a
   piece at "now + grace"; voices already sent stay valid, the epoch never moves. Pieces older than the current
   cycle fold into one. Every place that turns cycles into seconds or back reads the map (seven today:
   `queryEventsUntimed`, `getNowCycle`, `requestNextCyclesAndAdvanceCursor`, `emitCompletedCycles`,
   `resyncCurrentCycle` with its cutoff, `reemitVoiceSignals`, `updateRpm`). A tempo change sends `PatternUpdated`.
5. **The scheduler pulls** (choice B below): `tick(playbackNowSec)` tops up the lookahead from the clock and the map.
   Live a timer calls it with `PlaybackClock`'s time; offline the driver calls it with the backend's exact position.
   The lookahead is counted in seconds, with a floor (1.5 to 2 s, more on a phone if drops show). A cursor behind
   "now" catches up bounded: it skips the late voices, but queries the skipped window for its control events
   (`master`, `katalyst`, `solo`) and sends the latest of each kind and orbit at "now", so no state is lost; a
   catch-up yields between chunks. The edit cutoff, the cycle counter, the reemit and a one-shot's end read the
   same clock.
6. **The UI on `PlaybackClock`:** signals carry playback seconds (or cycles); highlights, the voice rain, the ADSR
   signals and the cycle counter compare them every frame with `clock.now() - outputLatency`.
7. **What is lost is counted:** a sample voice without its sample counts in the diagnostics as its own number, beside
   the late voices.

**What goes:** `BackendClockSync` (or it stays as the estimator, step 3a), `startTimeMs`, `localFrameCounter` and the
scheduler's own `KlangTime`, absolute times and offsets in the signals, the UI's `Date.now()` for audio; and, in step
3c, `playbackStartTime` on the wire (choice C).

## Choices (decided by the maintainer, 2026-10-31: A1, B, C)

- **A. Where the frontend reads the backend's position.**
  (1) From the diagnostics: the backend puts its timeline in `backendNowMs` (what its KDoc already claims), with a
  report cadence counted in frames, not `Date.now()` (a backward wall-clock step now silences the reports for its
  length). Works the same on JS, JVM and offline. The frontend extrapolates between reports, stamping each in
  `JsAudioBackend`'s `port.onmessage`, not at the 16 ms poll.
  (2) On JS from the browser: `AudioContext.getOutputTimestamp()` pairs the audio clock (the frames the graph
  rendered) with `performance.now()`, output-side, which is what the UI needs; the worklet's timeline aligns on
  `currentFrame`. No extrapolation, no cap, no jitter; the JVM and offline still need (1).
  Recommendation: (1) first, it is one code path and fixes the most; (2) later on JS if (1) shows jitter.
  **Decided: (1), the diagnostics.**
- **B. Report-driven scheduler or a pull API.** Reports reach the frontend through the same main thread and its
  16 ms poll, so "driven by reports" helps nothing against a main-thread stall. Recommendation: the pull API
  `tick(playbackNowSec)`, a timer live, the driver offline; it also makes lockstep simple.
  **Decided: the pull API.** The 16 ms loop stays live; what changes is who owns it. Today the loop sits inside the
  scheduler and reads the scheduler's own wall clock. Afterwards the scheduler is a plain object with `tick`, and
  a small live driver keeps the loop: every 16 ms it calls `tick(clock.now())` with `PlaybackClock`'s time. The
  offline driver calls the same `tick` with the backend's exact position, no timer at all.
- **C. The epoch: "backend now" or `max(requested, now)`.** Keeping `max` with a requested start on the playback's
  own timeline costs nothing and leaves a door open (a start quantized to a bar of another playback). Recommendation:
  keep `max`, but the requested start is in timeline seconds the frontend learned from the backend, never a
  frontend clock.
  **Decided: keep `max`, with the requested start in timeline seconds.**

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
only when told, with the frontend's time a fake in the same spec. Primitives:

- `render(blocks)`; `backendStall(ms)`: frontend time passes, nothing renders (the audio thread starved);
  `frontendStall(ms)`: the backend renders, the frontend does not tick (a main-thread stall); `sleep(ms)` in two
  forms, frontend time running on or paused, for the two platform behaviours; a clock-rate factor for drift; a
  fast-forward of the timeline for long-run cases.
- A hook on the `VoiceScheduler` to read which voice started at which frame, which were late, which lacked a sample.
- `BackendReady` without a warmup.

The scheduler first gets a time seam (its clock and its timer injected), behaviour-neutral, so today's code runs
on the fake.

| Spec | Today | After |
|---|---|---|
| `frontendStall(1.5 s)` at 120 rpm | half a cycle dropped | nothing dropped (lookahead well above the stall) |
| `backendStall(2 s)`, then a new playback | the first note 2 s late, highlights early | the first note at once, highlights on the sound |
| a one-shot after `backendStall(L)` | its last L seconds lost | every voice starts |
| `sleep(30 min)`, frontend clock running on | a flood, edits cut 30 min ahead | on from the backend's position |
| `sleep(30 min)`, frontend clock paused, then a new playback | the first note late | the first note at once |
| a `master` / `katalyst` event inside a skipped window | (no skip today) | the state applied at "now" |
| a sample voice whose sample is late | lost, uncounted | counted |
| audio clock 100 ppm fast for an hour | the lookahead eaten, UI drifts | in step |
| two playbacks at once; a restart under the same id | (to see) | each on its own anchor |

The tempo cases (one change, two in a row, an edit after a change) are step 1's, on the scheduler. Rows that are red
today land with the step that turns them green (no red spec on main), the bug shown by a mutant of the fix.

**Layer 2, offline rendering through the real player:** `KlangPlayer` + `OfflineBackend` in lockstep on one thread:
the driver renders a block, calls the scheduler's `tick` with the backend's exact position, delivers feedback
directly, until the end plus the tail. `KlangOfflineRenderer` becomes a thin call. Not through today's timers: every
hop there is a wall-time wait (the feedback poll, the scheduler's 16 ms, the 2 s and 5 s timeouts), which would cap a
render near real time (`CorpusCheck` renders 256 cycles per song).

## Decide before implementing

- **Choices A, B, C** above.
- **Layer 2, "identical sample for sample", needs three decisions first:**
  - The playback id seeds each voice's randomness (`PlaybackCtx`: `Random(0x5EED xor playbackId.hashCode())`);
    offline uses "offline", the player "playback-N" from a process-wide counter, so seeds differ and depend on the
    order specs run in. A fixed id for an offline render.
  - `KlangMinHeap` is not stable: voices with the same start (chords, stacks) come out in an order that depends on
    when they were pushed; offline pushes the whole song at once, live per cycle. That order deals the random seeds
    (`VoiceFactory`) and decides cut groups. A stable heap (ties on insertion order) changes today's bits once; the
    "identical" baseline is taken after it.
  - The report cadence counted in frames (choice A1), or push timing varies run to run.
- **Layer 2, the rest:** custom ignitors (`render(customIgnitors)` writes into the parent registry; the player has no
  such input); the dispatcher's `latencyFrames` for the tail; "a sample load pending" defined from the frontend's
  preloader, `NotFound` ending the wait; the benchmarks and specs that use `KlangAudioRenderer` (`SongBenchmark`,
  `IgnitorBenchmark`, `KlangBenchmark`, `MasterStageSpec`, `KlangAudioRendererSpec`) keep measuring the engine
  alone, so it goes only once they have a home on the dispatcher, or it stays as their harness.
- **Several playbacks:** who passes the backend's position to each scheduler, on which dispatcher; on the JVM the
  fetcher dispatcher is multi-threaded and the scheduler's state is not synchronized.
- **The JVM backend:** its render cursor runs up to 250 ms ahead of what is heard (the line's buffer) and its output
  latency is 0; its position needs the line's buffered frames as latency.
- **The stutter on a device:** is `process()` called less often (frames behind wall time) or in bursts? With the
  diagnostics' counts (late, sample missing) on a phone.
- **`playbackStartTime`** is in about 50 files: `ScheduledVoice` doubles as the backend's internal absolute voice,
  the warmup and the benchmarks set it. Step 3c's scope is that.

## The work

1. **Tempo map** (frontend, `klang`): the time seam on the scheduler (clock, timer, a capturing `sendControl`); the
   map; `updateRpm` appends a piece; all seven cycle/second sites read it; a tempo change sends `PatternUpdated`.
   Specs on the scheduler: `playbackStartTime` stays the same across `updateRpm`, the next voice's `startTime`
   follows the map, two changes in a row, an edit after a change, the cycle counter steady; mutant: today's moving
   origin. Fixes the tempo jump on its own, nothing in `audio_be`.
   **Done (2026-10-31):** `TempoMap` (immutable, a piece per tempo, folded behind the cycle counter); the scheduler's
   origin fixed, every conversion through the map, a query split where the tempo changes so each part is asked at its
   own tempo (sprudel's `cps`, `beats`), one clock reading per change; seams `nowMs` and `pause`.
   `TempoMapSpec` (7), `KlangPatternSchedulerTempoSpec` (6, the loop stepped by hand on a fake clock); 14 mutants
   killed; three review rounds (level 2).
2. **`OfflineBackend`, layer 1** (`audio_be`): the primitives and the hook. Its rows land with steps 3a to 3d.
   **Done (2026-10-31):** `OfflineBackend` (the live dispatcher; `render`, `renderUntil`, `drain`; `BackendReady` at
   once; the timeline from `startTimeSec`) and `VoiceWatch` (started, late, soundless, for each voice that reached its
   start; null on the live path). The frontend-side primitives (a backend or a frontend that stands still, sleep,
   drift) live in the klang rig built on it (`KlangPlaybackSyncSpec`), not in `audio_be`; the fast-forward waits for
   the drift row. The holder reads the feedback as it renders: the link's ring is bounded.
3. **The rework, in four cuts:**
   - 3a. The backend reports its timeline (choice A) with a cadence in frames, and `PlaybackAnchored`; the frontend
     estimates the position (`PlaybackClock`, or `BackendClockSync` turned into it). Fixes 2, 3, 4, 10.
     **Done (2026-10-31):** the diagnostics carry the timeline at the end of the block, one per 20 ms of audio
     rendered; `Feedback.PlaybackAnchored` from `ensureEpoch`; `PlaybackClock` replaces `BackendClockSync` (the last
     report plus the local time since, capped at 3 of the longest of the last 16 report gaps within 0.1 to 0.5 s, so
     bursts do not stop it; never backwards; before the first report a guess on the frontend's clock that holds
     nothing back; a lock, as the JVM reads it from several threads). The scheduler: its time is `clock.nowSec() -
     epochSec`, the epoch
     asked for is the clock's now (choice C with no lead) and then the backend's anchor; `startTimeMs`, the
     `localFrameCounter` (its `Int` too) and the scheduler's `KlangTime` for timing are gone. The UI's times are put
     on `uiClockMs()`, which is `Date.now()` in the browser, the clock every consumer compares with, plus the output
     latency. Deviation: a report is stamped where the player polls the link, not in `port.onmessage`, so every
     estimate lags the backend by up to one poll (16 ms) plus the message's transit: highlights up to a frame late,
     the look-ahead that much shorter. Small enough for now; the rig delivers with no poll, so no spec sees it.
     Stamping in `onmessage` is the fix if it shows.
   - 3b. The pull API (choice B), lookahead in seconds, the bounded catch-up keeping control events, a one-shot's
     end on the clock, the lost-sample count. Fixes 5, 6, 7, 8, 11.
     From 3a's review: the cap follows the longest recent report gap, so a stall of S raises it to 3 S (at most
     0.5 s) for the next 16 reports; while it is up, the clock runs that far past a stalled backend: a playback
     started then begins that much late, highlights come that much early, and a one-shot ending on the estimate
     could still lose its last half second (problem 6). End a one-shot on the last reported position, not the
     estimate; optionally let only a gap the backend kept up across (a burst) raise the cap.
     **Done (2026-10-31):** `tick(nowSec)`, the live loop its driver; the look-ahead at least `LOOKAHEAD_MIN_SEC` 1.5 s;
     `catchUp`: a cursor behind now skips the cycles gone by (at most 64 read) and sends their state as control events
     at their own times (the backend applies a late control's state in time order, so the current cycle's newer state
     still wins): the last `master`, the last `katalyst` of each orbit, every solo control event still running (a
     note's solo never sets the end, `SoloTracker`); `CycleCompleted` on the last reported position
     (`PlaybackClock.reportedSec`), so a one-shot ends when its last cycle has been rendered; the soundless voices
     counted (`PlaybackCtx.soundlessVoices`, `Diagnostics.soundlessVoices`, a "Silent voices" tile beside "Late
     voices"). Not done: the burst-only cap (the cost above stays bounded by 0.5 s and no longer reaches a one-shot).
   - 3c. `playbackStartTime` and the epoch's `max` as choice C decides. From 3a's review: a pattern whose first sound
     comes after the first fetches (an intro rest longer than the look-ahead) sends nothing at first, so the backend
     sets the epoch only at the first voice, late, and `max` shifts the whole playback by that much; since 3a the
     frontend follows the late anchor (highlights on the sound, the cycle counter pausing for the shift). Open the
     playback at its start even when the first cycles are empty (accepting a past epoch instead would drop the first
     note as late).
     **Done (2026-10-31):** choice C was already in place with 3a (the epoch asked for is the clock's now, on the
     timeline); a first cycle with nothing in it sends a silent control event at second 0, so the backend opens the
     playback where it was asked to.
   - 3d. The UI consumers on `PlaybackClock`. Checked on 3a (the maintainer, 2026-10-31: "check all consumers in the
     frontend that displays things based on the timing of the audio-events"): the code highlights
     (`CodeMirrorHighlightBuffer`), the mini-notation and staff editors (`MnEditorBase`, `NoteStaffEditor`), the ADSR
     curves (`AdsrSignals`, `KlangAdsrCurve`) and the voice rain (`VoiceRain`) all compare the signals' times with
     `Date.now()`, which the signals are now on, so they follow the backend with nothing else changed. Left for 3d:
     the cycle counter (`KlangCodePlaybackCtrl`) counts a cycle when the scheduler sees it end, not when it is heard
     (the output latency early, as before); Klang Amp's play time (`KlangampDeck`) is wall time since the start, so
     after a stall it runs ahead of what played; `OneShotPlayback`'s end (3b).
     **Done (2026-10-31):** the cycle counter counts a cycle at its `atTimeSec`, when it is heard; Klang Amp shows
     `KlangCyclicPlayback.heardSec()`, the backend's reported position less the output latency, which stands still
     with the backend.
   - Open from step 1's review: a voice kept by the grace window keeps the gate end the old tempo gave it, so on a
     faster tempo the note at the switch rings into the next one and its highlight ends before its sound. As before
     the map; decide in 3b whether a tempo change re-sends it. Decided in 3b: left as it is. The note is already
     sounding or about to (within the 0.2 s grace); re-sending it would cut it or play it twice, and the overlap lasts
     at most one note, only on a faster tempo. Revisit if it is heard.
   - Beside them, on a device: AudioContext state handling (`statechange`, `resume()` on a user gesture) for 9.
     **Done (2026-10-31):** `keepRunning` (`audio_be/src/jsMain/kotlin/_audio_context_wake.kt`): a context the browser
     stopped ("suspended", iOS "interrupted") is asked to `resume()` on every gesture (`pointerup`, `touchend`,
     `click`, `keydown`: a tap's `pointerdown` is no gesture) and, once at a time, when the page is shown again; the
     state change itself only logs. `AudioContextWakeSpec` guards the wiring (6 mutants); what each browser takes as a
     gesture is a device check, not yet made. With a mouse the wake comes on release: nothing plays on press today.
4. **Layer 2:** the decisions above, then the offline renderer on the real player in lockstep; the render specs
   (klang, sprudel) on the real path; a render identical before and after (after the stable heap).
   From 3a's review: an `OfflineBackend` at second 0 sends `BackendReady` before any report, so a playback asks for
   its epoch from the clock's guess (the wall clock, about 1.8e9 s) and the backend never reaches it. The driver
   gives the scheduler the backend's exact position first, or the `OfflineBackend` starts on the wall clock as the
   live backends do.
   **Done (2026-11-01),** the maintainer's decisions: a fixed playback id ("offline"); a STABLE voice heap
   (`KlangMinHeap`: ties pop in push order, so live and offline deal seeds and cut groups alike); the benchmarks and
   engine specs keep `KlangAudioRenderer`. `KlangOfflineRenderer` now runs the real path in lockstep: the scheduler
   (`startDriven`, `tick` with the backend's position, a `PlaybackClock` on it, so the epoch question above is moot)
   sends over the wire to an `OfflineBackend` (seed 1); samples are loaded up front; a query error fails the render
   (a live one steps past the chunk). The tempo map keeps the tempo as given and times a note's length as cycles
   times seconds per cycle within one tempo, so its last bits match the old path. Corpus (`runSongBenchmark
   --args=corpus`, in a worktree of HEAD plus this step): 17 of the 18 renders are bit-identical to the old path
   with the stable heap; the stable heap alone moved 7 (4 live songs, 3 frozen), accepted with
   `corpus-accept-engine` as the new baseline. The 18th, "The Synthsale Piper's Last Rave" (an edited live song, so
   information only), differs because sprudel answers it differently for one whole-song query (the old path) than
   cycle by cycle (the new path, as live playback): KLANG-00401. The new path renders it as it plays live. The corpus
   also renders faster (180 s against 250 s).

## Acceptance

- Step 1's specs green; the table's specs green on `OfflineBackend`, each mutation-checked (the mandatory tier:
  engine and wire).
- In the app: a tempo change at any point of a song keeps the beat; after a laptop sleep playback goes on without a
  reload; on a phone a stutter delays the sound but drops no voice that arrived in time, and what is lost is counted.
- No `Date.now()` and no `KlangTime` in the frontend's audio sync.
- One host (`PlaybackEngineDispatcher`) for live and offline playback; renders identical before and after step 4.
  Met for 17 of 18 songs: the new path is bit-identical to the old one; the stable heap, decided by the maintainer,
  moved 7 renders once and is the new baseline. The 18th renders as it plays live now, the old path having differed
  from live (KLANG-00401).

## Related

- KLANG-00401 (sprudel answers differently for one long query than cycle by cycle): found by step 4's review.
- KLANG-00183 (worklet clock divergence): largely answered by step 3a, the frontend's clock running on the backend's
  frames.
- KLANG-00398 (a song started while the Motor loads plays on after its page is gone): the same scheduler, a separate
  fix.

## Review

- **Design review, level 2 (2026-10-31), read-only against the code:** the eleven problems confirmed, with
  corrections: the tempo error's arithmetic holds and its cutoff makes it worse (now in 1); Klang Amp is no trigger;
  `backendNowMs` on the JVM and the "snap" branch (3); the flood depends on the platform (8); the `Int` saturates
  (11). New: the one-shot cut (6), the uncounted sample voices (7), no AudioContext state handling (9). Design: the
  cursor's jump would lose control state (now kept, design 5); "driven by reports" does not help against a
  main-thread stall (choice B); `PlaybackClock`'s stamping, cap and monotony; the frame cadence; the JVM's latency;
  restart under the same id; the map's growth; the scope of `playbackStartTime`. Testing: the stall row was a
  backend stall that is green today (now two primitives); the tempo row could never be red after step 1 (now step 1's
  scheduler specs); a time seam first; missing cases added. Layer 2: lockstep not feasible through the timers (now a
  pull API on one thread); "identical" needed the seed, heap and cadence decisions. Step 3 cut in four.

## History

- 2026-10-31: created from the analysis with the maintainer (Klang-Research).
- 2026-10-31: revised after the design review: two new causes, the corrections, choices A to C, step 3 in four cuts.
- 2026-10-31: the maintainer chose A1 (the diagnostics), B (the pull API, the live loop moves into a driver), C.
- 2026-10-31: step 1 done, the tempo map (Klang-Research), built in a window Klang-Main-Dev gave.
- 2026-10-31: steps 2 and 3a done (Klang-Research): the OfflineBackend, the backend's timeline in its reports,
  `PlaybackClock`, the anchor; the UI consumers checked; reviews at level 2 and 3, the last clean.
- 2026-10-31: steps 3b to 3d done (Klang-Research), the maintainer having smoke-tested 3a ("the RPM dial now works
  perfectly").
- 2026-10-31: problem 9, the AudioContext woken on a gesture or when shown (Klang-Research); reviews at level 2 and 3.
- 2026-11-01: step 4 done (Klang-Research): offline renders on the real path, the stable heap, the corpus re-based.
- 2026-11-01: PAUSED. Steps 1 to 4 and problem 9 done; waiting for the maintainer's checks on a device (a laptop's sleep, a phone's stutter, the AudioContext's wake).
- 2026-11-01: DONE. The maintainer's device checks (2026-11-01): a closed and reopened laptop resumes the song where it stopped, no reload; on the phone it seems to work, the browser itself tries to reload the tab at times; the maintainer keeps an eye on it. Open beside it: KLANG-00401 (sprudel).
