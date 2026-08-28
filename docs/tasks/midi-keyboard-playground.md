# MIDI Keyboard Playground

**Status:** DRAFT (2026-08-28) — approach agreed, not started.

## Goal

Play a user-defined ignitor live from a MIDI keyboard in the browser. Playground page first,
latency tuning last: "first we simply try to make it work."

## Architecture principle

MIDI is a **second note source** next to sprudel. The BE stays cycle-free (see
`feedback_be_cycle_free`): it only ever sees voices, so the realtime path reuses the existing
wire format instead of growing a parallel one.

Already in place:

- `Cmd.ScheduleVoice` (single-voice scheduling) + `Cmd.RegisterIgnitor` (user ignitor by name)
- `ScheduledVoice.gateEndTime` — the note-off concept already exists in the wire format
- Per-playback engines (`PlaybackEngineDispatcher`) — the playground gets its own
  `playbackId`, scheduler, and cylinders

## Agreed design decisions

1. **`startTime: Double?` — null means "play immediately".** The BE stamps the actual start
   at receipt (next block). No FE/BE clock comparison on the realtime path at all (sidesteps
   the worklet clock divergence issue).
   - Convention to confirm: when `startTime == null`, `gateEndTime` is interpreted as a
     **duration from actual start** (it cannot be absolute without a start).
2. **Realtime playbacks are exempt from idle cleanup.** Idle `PlaybackEngine`s get cleaned up
   today, so introduce a "realtime" playback concept — `autoCleanup = false` or similar. Same
   concept a future sound-effects engine needs (voices at random times, no pattern, no end).
3. **v0 skips the editor.** Hardcoded default supersaw; the ignitor editor comes after sound
   works end-to-end.

## Milestones

- **v0 — make it sound:** Web MIDI (`navigator.requestMIDIAccess`, main thread) → note-on
  builds a plain `VoiceData` event (MIDI note → freq via tones, velocity → gain,
  `sound = "supersaw"`, orbit 0) → `Cmd.ScheduleVoice(startTime = null, fixed length)` into a
  keep-alive playground playback. `sound = "supersaw"` resolves to the **built-in oscillator**
  — no ignitor is built or registered at all. No UI beyond a MIDI-device picker.
- **v1 — the playground:** ignitor editor pane (existing KlangScript editor infra) →
  `Cmd.RegisterIgnitor` → notes play the user's ignitor.
- **v2 — real note-off:** optional `liveId` on `ScheduledVoice` + new
  `Cmd.GateOff(playbackId, liveId)`. Note-on schedules with far-future gate; note-off sends
  GateOff; envelope enters release from current level. Key by `liveId`, NOT by MIDI note
  number (identity trap — chords, retriggers; cf. the live-update double-voice fix).
  `ClearScheduled` doubles as the panic button.
  - Acceptance test: a held note must hold ADSR sustain indefinitely until gate-off.
- **v3 — latency:** measure before optimizing. Chain: MIDI → main thread → comm-link ring →
  next worklet block (2.7 ms at 128 frames; output latency dominates, ~10–25 ms in Chrome).
  Bluetooth latency is hidden by Chrome (`project_bluetooth_latency` — AnalyserNode probe idea).

## Later / out of scope for now

- Velocity curves, aftertouch, CC → `Osc.param` slot mapping (the slots are built for this).
- Polyphony limits / voice stealing → voice-takeover design (`docs/tasks/voice-takeover.md`).
- Computer-keyboard fallback input for users without MIDI hardware.

## Engine creation on the fly (decided 2026-08-28)

There is no explicit "create playback" command — every `Cmd` implicitly creates its engine
(`PlaybackEngineDispatcher.engineFor` → `getOrPut`). Engine params don't exist yet; the
closest wire-format shape is the `Register*` family (playback-targeted, send-once-before-voices,
implicit creation). So:

- **New `Cmd.ConfigurePlayback(playbackId, config: PlaybackConfig)`** — fourth `Register*`-style
  sibling. `PlaybackConfig` is its own `@WireFormat` data class, nullable-means-default fields
  (`VoiceData` convention), first field `autoCleanup: Boolean? = null`.
- **Never sent directly by feature code — always through the `KlangPlayer` instance**, which owns
  playbackId hygiene: FE-supplied names are mangled to `"custom-$name"` so they cannot collide
  with the generated `"playback-N"` ids (`KlangPlayer.generatePlaybackId()`).
- **Factory returns a playback object**: `KlangPlayer.createRealtimePlayback(name, config)` →
  mangles the id, sends `ConfigurePlayback`, calls `registerPlayback(...)` (so feedback routing
  by playbackId works), and returns a new **`KlangRealtimePlayback : KlangPlayback`** — the
  realtime sibling of `KlangCyclicPlayback` (which is what `CodeSongPage` uses via
  `KlangCodePlaybackCtrl`; `KlangPlaybackController` is internal). Surface: `scheduleVoice(...)`
  for v0, `noteOn`/`noteOff` with internal `liveId` bookkeeping for v2, `stop()` sends `Cleanup`.
- Calling the factory twice with the same name: decide reuse-or-error when implementing (lean
  reuse — page remounts shouldn't leak engines).

## Frontend steps (started 2026-08-28)

1. ✅ Midi Playground page + "Discover more" menu entry + route (`/midi-playground`).
2. ✅ Web MIDI listener (`src/jsMain/kotlin/midi/WebMidi.kt` externals, hot-plug via
   `onstatechange`, note-on/off parsing incl. running-status vel-0 note-offs).
3. ✅ Pressed keys displayed live (note name via tones `Midi.midiToNoteName` + velocity +
   event log).
4. ✅ v0 sound — CODE-COMPLETE 2026-08-28 (tests pending a Gradle run):
   - Wire: `RealtimeVoice(liveId, data, gateDurSec?)` + `Cmd.StartRealtimeVoice` (NO nullable
     startTime on `ScheduledVoice` — immediacy = absence of the concept; user decision).
     `ConfigurePlayback`/`autoCleanup` DROPPED: no idle reaper exists, cleanup is only ever sent
     by a playback's own stop, so keep-alive is free.
   - BE: `VoiceScheduler.startRealtimeVoice` stamps `clock.nowSec()` (cursor-derived), promotes
     straight to active via extracted `activateVoice`; `VoiceOrigin` sealed (Timeline(source) /
     Realtime(liveId)) replaces nullable neighbors; held-gate horizon 36_000 s (Int-frame-count
     overflow guard in VoiceFactory). Spec: `RealtimeVoiceSpec` (4 tests) + codec round-trips.
   - FE: `KlangRealtimeVoicePlayback : KlangPlayback` via `KlangPlayer.createRealtimePlayback(name)`
     (mangles `"custom-$name"`, idempotent per name); page fires `sound = "supersaw"` per note-on
     (freq via `Midi.midiToFreq`, velocity/127, fixed 0.5 s gate).
5. NEXT: run `:audio_be:jvmTest` + `:audio_bridge:jsTest`, then by-ear in Chrome; then v2
   (`StopRealtimeVoice` gate-off) and v1 (ignitor editor).

## Open questions
- Does the playground share the master chain (limiter) with normal playbacks? (It should —
  raw supersaw + no limiter is ear-unsafe.)
- `isDuplicate` uses `startTime` — realtime voices with `startTime = null` must bypass the
  live-update dedup path (they never go through `ReplaceVoices`, so likely a non-issue; verify).
