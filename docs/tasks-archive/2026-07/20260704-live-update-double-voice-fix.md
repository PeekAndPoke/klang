# Live-update "double voice" fix

Status: **DONE 2026-07-04** (uncommitted at time of writing).

## Problem

Editing live-coding code sometimes made a note play twice — a long-standing, intermittent bug.
`KlangPlaybackController.updatePattern()` → `resyncCurrentCycle()` cancels already-scheduled future
voices past a grace cutoff and reschedules the new ones after it (imminent voices keep playing).

## Root cause

`VoiceScheduler.replaceVoices` removed voices **only from the `scheduled` heap, never from `active`**,
and there was no per-event dedup. A voice at/after the cutoff already **promoted to `active`** before
the `Cmd.ReplaceVoices` was processed was never removed, while its resend was re-added and promoted →
the note sounded twice. Promotion runs on the **backend** clock, the cutoff on the **frontend** clock
— they drift, plus command latency — so whenever the BE has passed the FE's "now + grace", a voice the
FE still calls "future" is already active. Intermittent by nature. Aggravated by the grace window
being **50 ms** (not the ~500 ms assumed).

## Why the obvious dedup key is wrong

`VoiceData.sourceId` (stable `loc_<hash>` across re-eval) is assigned **once per source expression**,
so legitimately-simultaneous voices SHARE it: `superimpose`/`layer` layers and chords at one location
(`n("[0,4,7]")`). A `(sourceId, startTime)` dedup would silently collapse chord tones and layers.

## Fix

1. **Grace window** `resyncGraceWindowSec` 0.05 → **0.2** (`KlangPlaybackController.kt`). ~4× fewer races.
2. **Identity dedup scoped to the replace path** (`VoiceScheduler.dedupAgainstActive`, called before
   `scheduleVoices` in `replaceVoices`). Keeps the playing voice, drops its redundant resend (no click,
   no teardown). Added `source: ScheduledVoice` to `ActiveVoice` (set at promotion to the relative
   `head`). Match = **`ScheduledVoice.isDuplicate(other)`** = `startTime == other.startTime && data ==
   other.data` (structural), **1-to-1** (each active claimed once → multiplicity preserved).
   `playbackId` is NOT compared — the scheduler is per-playback (one engine per playback), so all its
   voices share one playbackId. Full-payload match is inherently chord/superimpose-safe (distinct
   simultaneous voices differ in `data`).
3. **Regression tests** (`PlaybackEngineDispatcherTest`): double-voice (promote past cutoff → resend →
   assert 1) and chord-safety (two same-time voices differing in `freqHz` → assert 2).

## Residual / out of scope

- If the user *changes* a note that has already leaked into `active`, the old note can briefly overlap
  its replacement — rare (needs a >200 ms skew leak AND a change at that exact event), transient.
- The FE-side thread race between `resyncCurrentCycle` and the fetch loop — JVM-only, benign on JS.
- A first-class per-event identity on `ScheduledVoice` — the scoped identity match avoids new wire fields.

## Key files

`KlangPlaybackController.kt` (grace window), `VoiceScheduler.kt` (`ActiveVoice.source` + dedup),
`ScheduledVoice.kt` (`isDuplicate`), `PlaybackEngineDispatcherTest.kt` (tests). Architecture context:
`audio/ref/architecture.md` "Per-Playback Engine Isolation".
