# KLANG-00427: Audit fixes 3: voice edges (the note-end step, starts without an envelope, cuts, the one-frame-early onset, stop)

> **Status (2026-11-10): IN PROGRESS.** Started.
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

(filled while the work runs)

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started.
