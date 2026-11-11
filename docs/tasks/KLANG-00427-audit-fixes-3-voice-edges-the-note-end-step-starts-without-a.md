# KLANG-00427: Audit fixes 3: voice edges (the note-end step, starts without an envelope, cuts, the one-frame-early onset, stop)

> **Status (2026-11-10): PAUSED.** Nine of twelve findings landed. Waiting for the maintainer: L2-05, L2-06, L2-07 (see Decide).
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

The engine audit (KLANG-00420) found these. Every row is traced in `docs/audio-audit/2026-11-10/audit/` (the layer
report named by the id's layer, and the `disproof-*.md` that measured it); the index is `SUMMARY.md` there. The
maintainer's decision (2026-11-10): "create tickets and fix all of the findings. Sound changes are fine, for example
for the pluck."

## The findings

| Id | Severity now | Finding | Verdict and measurement |
|---|---|---|---|
| L2-01 | major | A voice that ends in its own envelope is cut on the de-click's lag: a step at the last frame that grows as the release gets shorter | Confirmed: The engine is the auditor's model to two digits: -49.6 dB left on the last frame at the default release, -23.4 at 5 ms; the probe's clean sine was a zero crossing |
| L2-05 | (read only) | With the envelope off the engine guards a voice's end and not its start | not challenged; the auditor's reading in `layer-2-voice-lifetime.md` |
| L4-03 | minor | The de-click smoother has no flush: an envelope at sustain 0 turns the voice's gain into a subnormal | Confirmed, smaller: First subnormal at 0.812 s, they reach the cylinder; the held voice renders 27 to 39 percent slower on the JVM, about 0.5 us per block; inaudible |
| L2-02 | minor | A cut kills a victim that has not rendered yet, even when the victim's onset lies before the cutter's: whether a note sounds depends on the block border | Confirmed: 64 frames apart: 0 of 64 victim frames in one block, 63 of 64 across a border |
| L2-03 | minor | The seconds round trip places a block-aligned onset one frame BEFORE the cursor: about half of all live realtime notes and first notes start with `voiceElapsedFrames` = 1 | Confirmed: 49.9 % of block-aligned stamps one frame early at a Unix start; nothing audible follows |
| L9-03 | minor | The live timeline is counted in Unix-epoch seconds: half of the on-grid onsets land one frame early, a start's first voice among them | Confirmed: A playback's dyadic-grid notes are either all early or all exact, half the playbacks each |
| L2-07 | minor | Nothing bounds a stopped playback's voices: a long gate or a long release keeps sounding, and keeps the engine, after stop | Confirmed: 60 s gate: full level 29 s after the stop, engine kept to 60.08 s; release 1e6 lives 11.6 days |
| L5-06 | minor | A release written over `Freq` under a detune: the lifetime is read at the note, the envelope runs at the detuned pitch | Confirmed: Under detune(-12) the voice is cut at -14.7 dB in mid-release |
| L2-06 | minor | The whole voice build runs inside the render call, in the block that holds the note's onset | Confirmed: 16 default saws starting together: 513 us (19 %); one isolated note's onset block 60 us |
| L2-09 | minor | No voice limit, and `replaceVoices` does quadratic work with deep compares on the audio thread | Confirmed, larger: Same measurement as L9-10 |
| L9-10 | minor | Work on the audio thread at command time has no bound: the optimizer, chain builds, the replace dedup, and any number of voices | Confirmed, larger: `ReplaceVoices` is O(incoming x active) in the real shape; 9.2 ms and 13.6 MB at 1000 x 1000 on the JVM |
| S-01 | new | The click table's sine note-end row is blind by construction | found in the disproof pass, `disproof-sound.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

**L2-05, the start of a voice without its own envelope (`adsrOff`, a bare authored tree).** The engine fades such a
voice's end (the teardown fade) and not its start. The doors do not settle it: `adsrOff` promises only that "the note
ends cleanly", and its documented use is an instrument that carries its own envelope (whose attack then is the
start). Options:
- (a) The start is the author's: one sentence in `teardown_fade.kt` and `audio/ref/voice-synthesis.md`, no sound
  change. A percussive sample or a Karplus pluck keeps its first sample.
- (b) An onset fade, the teardown's mirror (4 ms linear), on every voice that gets the teardown fade. Removes the
  measured jumps (tri -13.7 dB burst, supersine -26 dB), but softens every transient: a sample with `adsrOff` (a kick,
  a snare) loses the attack of its first 4 ms, and the corpus moves (`ATruthWorthLyingFor`'s guitars,
  `SchmetterlingJingle`'s marimba, `Kokon`'s `clean` use `adsrOff`).
- (c) A much shorter onset guard (one de-click time constant, about 1 ms), on non-sample voices only.
Recommendation: (a). The sample voice, the main `adsrOff` case, wants its transient, and an author who switches the
envelope off on a tonal source has the voice envelope one call away.

**L2-06, the voice build inside the render call.** Measured (disproof-realtime): 16 default saws starting together
cost 513 us, 19 % of a block (all `classic()` stages on: 1056 us, 39.6 %; 16 superplucks: max 2639 us, 99 %; shimmer:
max 4850 us, a missed block); one isolated note's onset block 60 us against 3.2 us. Options:
- (a) Build at schedule time (the command drain, up to 1.5 s ahead): removes the onset spike; holds built voices for
  the lookahead (a default saw about 6 KB, a superpluck 167 KB, a shimmer 775 KB each), must keep the voice rng's
  draw order (it is dealt per `makeVoice`, today in promotion order), and a replace must drop the built voices.
- (b) Build one block ahead, between blocks: in the worklet and the JVM loop that is the same callback, so it moves a
  chord's cost to the block before and does not spread it.
- (c) Lower the per-build cost first: the Karplus rings pooled (L5-03) and the shimmer at wet 0 not built (L4-09), in
  their own tickets; that is what pushes a burst toward the deadline.
Recommendation: (c), then measure again; (a) only if a plain burst still misses. No small safe step exists here.

**L2-07, a stopped playback's voices.** The ordinary case is BY DESIGN: "**A stopped playback is never hard-cut**"
(`audio/MEMORY.md`), "Every tail that can end on its own rings out in full, however long it takes" (decision (j),
`PlaybackEngine.isIdle`), "the voices already playing ring out" (`VoiceScheduler.cleanup`); `RealtimeVoiceSpec` pins
it. The unbounded case (a gate up to 12.4 h, `release(1e6)` living 11.6 days, the engine never idle) has no bound
anywhere: the 20 s hold (`MAX_TAIL_HOLD_SECONDS`) is for ENDLESS tails only, and decision (j) explicitly lets a finite
tail ring however long. Options:
- (a) Leave it: the author wrote the long note.
- (b) A note-off at stop for every timeline voice whose gate is open (what a DAW's stop does: every held note
  releases into its own release); the voices not yet started are dropped already. Changes the pinned behaviour.
- (c) Count a voice whose remaining life at the stop exceeds the 20 s hold as an endless tail: after the hold, the
  engine's output is released (`TailRelease`), as for a delay at feedback 1.
Recommendation: (b) for musical sense, with (c) as the bound for a release that is itself absurd. Both need the
maintainer, since they change decision (j)'s promise.

### Decided (maintainer, 2026-11-11)

"All as you recommend."

- **L2-05:** (a), the start of a voice without its own envelope is the author's. One sentence in `teardown_fade.kt`
  and `audio/ref/voice-synthesis.md`. Owed.
- **L2-06:** (c), the build cost was lowered in KLANG-00429; build at schedule time is not built.
- **L2-07:** (b) a note-off at stop for every timeline voice whose gate is open, with (c) as the bound: a voice whose
  remaining life exceeds the 20 s hold counts as an endless tail. This changes decision (j) and the behaviour
  `RealtimeVoiceSpec` pins. Owed.

### Decided, second round (maintainer, 2026-11-11)

- **L2-07:** "yes that is ok": held notes release at stop, the 20 s hold bounds the rest. Decision (j) changes.

## Notes from the work (2026-11-10)

**Sound changes, and the songs they move** (the corpus read as before against after, 256 cycles, no song edited
between the runs; each fix attributed by a run with it switched off):
- L2-01 with L4-03 (the de-click's settle and flush): every note that ends in its envelope now runs 36 ms and a frame
  past its release while the de-click's gain falls by e per millisecond to an exact 0. It moves 17 of the 18 corpus
  rows (all but The Synthsale Piper's Farewell). A release of 0 now ends on that 1 ms fall instead of a cut.
  Rendered to 16-bit before and after the fix alone: Stein um Stein shows no difference at all (under one LSB), The
  Synthsale Piper's Last Rave differs on 2.9 % of its frames, at most 7 LSB (-68.8 dB re its peak), in stretches of a
  median 43 ms where its notes end (longer where a delay or a reverb carries them).
- L2-03 with L9-03 (onsets in frames on the backend's own timeline): the offline renders were not exact either: a
  timeline note on the frame grid whose time came back a hair under its frame started one frame early. Those notes now
  start on their frame, one frame (21 us) later. It moves 13 rows: Kokon, Die Kirschblüte, Der Schmetterling,
  Seltsamere Dinge, Remix: Echo um Echo, Greensleeves Chant, A Synth Worth Lying For, Synthsturm, The Synthsale
  Piper's Farewell, Drunken Synthlor and the three frozen rows.
- L2-02, L5-06 and the replace dedup move no corpus row.

**Click probe, the sine's note-end row** (S-01: the gate moved by a quarter period, so the voice no longer ends on a
zero crossing): before L2-01's fix kink 15.97 and a burst of -65.2 dB (the old blind row read 0.95 and -102.4 dB);
after it kink 1.13 and -105.8 dB. The supersine: 8.74 and -69.0 dB before, 0.99 and -107.2 dB after.

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started.
- 2026-11-10: PAUSED. Nine of twelve findings landed. Waiting for the maintainer: L2-05, L2-06, L2-07 (see Decide).
