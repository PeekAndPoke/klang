# Realtime note-off: gate release for running voices (v2) — DESIGN FOR REVIEW

**Status:** PROPOSED (2026-08-28), not implemented. Parent workstream:
`docs/tasks/midi-keyboard-playground.md`. This file is the handover for an independent
judgement call on the design before it is cut.

## Goal

A MIDI key is released → the already-running realtime voice must enter its ADSR **release
tail from its current level** (no hard cut, no click), then die right after the tail.

## What already exists (v0, shipped 2026-08-28)

- Wire: `RealtimeVoice(liveId, data, gateDurSec?)` + `Cmd.StartRealtimeVoice`. `gateDurSec = null`
  means "held": the BE opens the gate at `nowSec + REALTIME_HELD_GATE_SEC` (36 000 s — bounded so
  `VoiceFactory`'s Int gate-duration math cannot overflow; `VoiceFactory.kt:93`).
- BE: `VoiceScheduler.startRealtimeVoice` promotes straight to active via the shared
  `activateVoice`; active voices carry a sealed `VoiceOrigin` — `Timeline(source)` or
  `Realtime(liveId)` — so the stop command has its handle and the replace-dedup structurally
  cannot match realtime voices.
- FE: `KlangRealtimeVoicePlayback.startVoice(data, gateDurSec): Int` (returns liveId).

## The proposed mechanism

### Why it is small: the envelopes are position-based and re-entrant

- Amp VCA (`strip/filter/EnvelopeRenderer.kt`): per sample checks `absPos >= gateEndPos`
  (line ~93); on the first release sample it captures `releaseStartLevel = currentEnv` — i.e.
  **release-from-current-level is already built in**. `gateEndPos` is currently a `val` baked at
  construction (line ~51) from a constructor-passed `gateEndFrame`.
- Control-rate envelopes (`strip/EnvelopeCalc.kt:23` `calculateControlRateEnvelope`) take
  `gateEndFrame` as a parameter per call; callers `FilterModRenderer.kt:28` and `FmRenderer.kt:27`
  each hold their own constructor-baked copy. `FilterModRenderer` computes the level at gate end
  via `levelAtPosition` (the 2026-03 release-from-current-level fix), so a moved gate stays
  consistent.
- `BlockContext` (`strip/BlockContext.kt:49-55`) ALREADY carries absolute `startFrame` /
  `endFrame` / `gateEndFrame` per voice — currently `val`, currently redundant with the baked
  renderer copies.

### The cut

1. **New Cmd** `@WireName("stop-realtime-voice") StopRealtimeVoice(playbackId, liveId)`.
   Dispatcher arm uses `engines[playbackId]?` (a stop must never CREATE an engine, unlike
   `engineFor`). Forwarding arm in `JsAudioBackend`'s exhaustive `when` (~line 178).
2. **`Voice.releaseGate(atFrame)`** — `gateEndFrame` and `endFrame` become `var`:

   ```kotlin
   fun releaseGate(atFrame: Double) {
       if (atFrame >= gateEndFrame) return           // natural gate earlier — no-op
       val releaseSpan = endFrame - gateEndFrame     // preserve this voice's own tail length
       gateEndFrame = atFrame
       endFrame = minOf(endFrame, atFrame + releaseSpan)
       blockCtx.gateEndFrame = gateEndFrame
       blockCtx.endFrame = endFrame
   }
   ```

   Moving `endFrame` is what prevents the leak: without it a released voice renders silence in
   the active list until the 10 h horizon (`Voice.render` returns false only past `endFrame`,
   `Voice.kt:103`).
3. **Single source of truth**: `BlockContext.gateEndFrame`/`endFrame` become `var`;
   `EnvelopeRenderer`, `FilterModRenderer`, `FmRenderer` drop their baked copies and read
   `ctx.gateEndFrame` (recompute `gateEndPos = (ctx.gateEndFrame - startFrame).toInt()` once per
   render call — one subtraction per block, the per-sample loop stays Int).
   `EnvelopeRenderer.kt:196` already reads `ctx.endFrame` for the release landing frame, which
   now updates correctly for free.
4. **Scheduler**: `stopRealtimeVoice(liveId)` scans `active` for
   `origin == VoiceOrigin.Realtime(liveId)` → `voice.releaseGate(context.clock.cursorFrame)`.
   Unknown liveId = silent no-op (releasing a key after a fixed-length voice ended is normal).
5. **FE**: `KlangRealtimeVoicePlayback.stopVoice(liveId)`; the page keeps `held: note → liveId`.
   Note-on on an already-held note (retrigger) stops the old liveId first — otherwise the old
   voice is orphaned into its 10 h gate. Note-off: `held.remove(note)?.let { stopVoice(it) }`.
6. **Specs**: held+stop → silent after the release span; two held voices, stop one → the other
   keeps sounding; unknown liveId → no-op. Plus wire-codec round-trip for the new Cmd.

## Known limitation (deliberate v2 scope)

Ignitor-INTERNAL `.adsr()` envelopes read the gate from `IgniteContext.gateEndFrame`
(voice-RELATIVE Int — `IgnitorEnvelopes.kt:88`, `IgnitorFilters.kt:663`), a separate context
from `BlockContext`. They keep the original 10 h gate after a stop. Believed inaudible: the amp
VCA release takes the voice to silence and the moved `endFrame` kills it. Judgement welcome.

## Points the reviewing agent should judge

1. **Threading**: `releaseGate` mutates `Voice`/`BlockContext` from the command-drain path.
   Assumption: the dispatcher pump is drain-commands → renderBlock on ONE audio thread, so no
   concurrent access. Confirm nothing else touches voices off-thread.
2. **`endFrame` consumers**: `Voice.endFrame` was `val`; it is read in `Voice.render` and
   `BlockContext.endFrame` (release landing at `EnvelopeRenderer.kt:196`). Are there other
   consumers (SendRenderer, VoiceLease/orbit ownership, diagnostics) that assume immutability?
3. **Release-state machine**: `releaseGate` guards `atFrame >= gateEndFrame`, so the gate only
   ever moves EARLIER, and only while the voice is still gated (cursor < old gate). Confirm no
   renderer caches "not yet released" state across blocks that would miss the moved gate
   (`env.releaseStarted` is set lazily on first release sample — believed safe).
4. **Precomputed release constants**: `EnvelopeRenderer` precomputes `relFrames`/`relDenom`/
   `relOffset` from `env.releaseFrames` at construction — the release SPAN is unchanged by a
   moved gate, so these stay valid. Confirm.
5. **Sample voices**: `endFrame` may be bounded by sample length rather than gate+release
   (`VoiceFactory.kt:359` area). The `minOf` in `releaseGate` only ever shrinks `endFrame` —
   confirm that is the right behavior for samples (and that `releaseSpan` can be ≤ 0 there
   without harm).
6. **Legato**: `VoiceFactory.kt:92-95` scales the gate duration by `legato` at build time.
   `releaseGate` bypasses that (realtime voices are not expected to carry legato). Acceptable?
7. **Alternatives considered** (for completeness):
   - Baked-copies + a `Gated` sub-interface on renderers that Voice walks on release — more
     plumbing, `BlockRenderer` is a `fun interface` (SAM), and it duplicates gate state again.
   - Hard cut via `endFrame` only — rejected: no musical release, and the 1 ms VCA de-click
     fade is a choke, not a tail.
   - Sealed `ScheduledVoice` hierarchy on the wire — rejected earlier in the workstream
     (realtime voices never touch heap/epoch machinery; split at the Cmd level instead).

## Non-goals

- Pedal/sostenuto semantics, polyphony limits / voice takeover (`docs/tasks/voice-takeover.md`),
  velocity curves, CC mapping.
- Any change to the timeline path's behavior — after the refactor it must be bit-identical
  (renderers read the same values from `BlockContext` that they used to hold as copies).
