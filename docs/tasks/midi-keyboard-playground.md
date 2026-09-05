# MIDI Keyboard Playground

**Status: v0 + v2 SHIPPED** (2026-08-29, uncommitted pending the maintainer's commit go).
**NEXT: v1 — the ignitor editor pane.**

> ⚠️ Sections below marked ~~superseded~~ are the ORIGINAL 2026-08-28 plan, kept for the
> reasoning trail. What actually shipped is in "Frontend steps" — read that first. The v2
> note-off design, its 3-round review loop and the decided semantics are archived at
> `docs/tasks-archive/2026-08/20260829-realtime-note-off-gate-release.md`.

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

## ~~Agreed design decisions~~ — SUPERSEDED (see "Frontend steps")

> Point 1 was REJECTED during implementation: `ScheduledVoice` was never made nullable. Immediacy
> is the ABSENCE of a start time, so the realtime path got its own wire type (`RealtimeVoice` +
> `Cmd.StartRealtimeVoice`). Point 2 was DROPPED: no idle reaper exists, so keep-alive is free
> and `ConfigurePlayback`/`autoCleanup` were never built.

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

## ~~Engine creation on the fly~~ — PARTLY SUPERSEDED (decided 2026-08-28)

> What SHIPPED from this section: the playbackId mangling (`"custom-$name"`), the
> `KlangPlayer.createRealtimePlayback(name)` factory (idempotent per name — reuse won), and
> `KlangRealtimeVoicePlayback : KlangPlayback`. What did NOT: `Cmd.ConfigurePlayback` /
> `PlaybackConfig` / `autoCleanup` (unnecessary — nothing reaps idle engines).

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
  `KlangCodePlaybackCtrl`; `KlangPatternScheduler` is internal). Surface: `scheduleVoice(...)`
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
5. ✅ v0 verified by ear in Chrome (2026-08-29); browser support notes: snap Chromium can NEVER
   do Web MIDI (no alsa interface), Firefox gates behind a site-permission add-on.
6. ✅ v2 note-off SHIPPED 2026-08-29 — `Cmd.StopRealtimeVoice` + `Voice.releaseGate` (the gate
   moves on BOTH doors, so ignitor-internal `.adsr()` envelopes obey note-off). FE:
   `stopVoice(liveId)`, page holds (channel,note)→liveId (retrigger stops the old voice first),
   voices start held (`gateDurSec = null`), CC 120/123 panic + unmount/unplug release-all.
   Review loop CLOSED on a clean round 3; design, findings and decided semantics archived at
   `docs/tasks-archive/2026-08/20260829-realtime-note-off-gate-release.md`.
7. **NEXT — v1, the ignitor editor pane:** a KlangScript editor on the page (existing editor
   infra) → compile → `Cmd.RegisterIgnitor` on the playground playback → notes play the user's
   ignitor instead of the built-in supersaw. Open design points to settle when starting:
   re-register on every edit vs. on an explicit apply; what happens to voices already sounding
   when the ignitor changes (the registry fork is per-playback, so a rebuild affects only new
   voices); where the editor sits in the page layout.
8. Then: deep-link cold start (AudioContext needs a user gesture — a resume kick on the page's
   first click closes it; reachable only by loading `/midi-playground` directly in a fresh tab);
   later velocity curves, CC→oscparam, voice takeover, computer-keyboard fallback.

## Open questions — ANSWERED

- ~~Does the playground share the master chain (limiter) with normal playbacks?~~ YES — the
  house master (limiter + its 5 ms lookahead) is global, post-sum, and always in the path.
  Consequence worth remembering: all output is delayed ~220 frames (~1.7 blocks at 44.1k), which
  is why the realtime specs pin block indices rather than "the very next block".
- ~~`isDuplicate` / `ReplaceVoices` dedup vs realtime voices~~ MOOT and structurally closed:
  there is no nullable `startTime`, and `VoiceOrigin.Timeline`/`Realtime` means the replace-dedup
  can only ever match timeline voices.
