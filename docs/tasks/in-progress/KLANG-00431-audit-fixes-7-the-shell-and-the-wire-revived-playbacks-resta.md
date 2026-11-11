# KLANG-00431: Audit fixes 7: the shell and the wire (revived playbacks, restarts, leaks, sample decoding on both platforms, latency, replaceVoices)

> **Status (2026-11-11): IN PROGRESS.** Fix worker started.
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
| L9-02 | major | A `ScheduleVoices` that arrives after its playback's `Cleanup` revives the playback: late ghost notes and an engine that never ends | Confirmed: Both halves reproduced: `[Cleanup, ScheduleVoices]` on the JVM dispatchers, the straggler starts 40 s after the stop, the engine never ends; the browser cannot reach it today |
| L9-04 | minor | A playback object started again after its stop: the gain can be lost and its inline instruments are never announced again | Confirmed: Measured: the restart's voices land at gain 1.0 after 0.3 was asked |
| L1-03 | minor | "Announce once" and "the fork dies with the engine" contradict each other when a playback is started again | Confirmed: No app path restarts a playback object; the library API does |
| L9-05 | minor | Every synth voice's sound and note is sent to the backend as a sample that was not found: a process-wide map that grows per edit, on both sides, missing from the debt list | Confirmed: 100 synth notes: 100 `NotFound` commands, 100 backend store entries, 100 preloader entries |
| L9-06 | minor | In the browser the backend keeps one full PCM copy per requested NOTE, as doubles, and nothing ever evicts | Confirmed: By reading: one PCM per requested note in the worklet |
| L8-08 | minor | The output latency the UI's clock gets: the JVM reports none, and the browser leaves out an authored master's delay | Confirmed: By reading: the master's latency is not carried; the JVM reports 0 |
| L9-09 | (read only) | A sample upload costs the audio thread one megabyte clone and one megabyte copy per chunk, and the whole PCM allocation at the first chunk; the comment reasons with half the size | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-11 | (read only) | The "dropped" and "soundless" voice counts fall back to 0 at every stop; the KDoc calls the counters monotone; the stats snapshot's version is a sum that can stand still | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-12 | (read only) | The warm-up runs every Ignitor node kind, and on the bus side only the six born-with stages | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-13 | (read only) | `Samples`' two caches are plain maps written from several threads on the JVM | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-16 | (read only) | The render headroom is timed with `Date.now()` in the worklet | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L1-06 | note | The diagnostics message is built, encoded and posted on the audio thread about 47 times a second | Confirmed, smaller: 104 to 352 B and under 1.3 us per report on the JVM, 47 reports a second |
| L8-05 | note | The diagnostics message is built and posted on the audio thread fifty times a second, also on an idle backend | Confirmed, smaller: Same measurement as L1-06 |
| L9-08 | note | The diagnostics are built, encoded and cloned on the audio thread about 47 times a second, and they are the clock | Confirmed, smaller: Same measurement as L1-06 |
| L1-07 | (read only) | The schema hash does not cover an enum's entries, the slot-key vocabulary, the catalogue index spaces or the codec's own format | not challenged; the auditor's reading in `layer-1-wire.md` |
| L1-08 | (read only) | The round-trip spec never crosses a structured clone, several wire shapes have no row, and the receiving side has no test | not challenged; the auditor's reading in `layer-1-wire.md` |
| L1-10 | (read only) | A slot map does not arrive the same on JS and on the JVM for two kinds of key | not challenged; the auditor's reading in `layer-1-wire.md` |
| L1-11 | (read only) | Both rings drop a message when full and no sender reads the result | not challenged; the auditor's reading in `layer-1-wire.md` |
| N-R3 | new | A stop during the start's sample preload is swallowed: the scheduler then starts and sends its first cycle | found in the disproof pass, `disproof-realtime.md` |
| N-R4 | new | `updatePattern`'s sample prefetch does nothing once the look-ahead has run | found in the disproof pass, `disproof-realtime.md` |
| L8-09 | (read only) | `PlaybackGain.retarget` ignores a new ramp time when the target is unchanged | not challenged; the auditor's reading in `layer-8-master-and-house.md` |
| L8-11 | (read only) | The offline render starts 5 ms late and the WAV length counter is an Int | not challenged; the auditor's reading in `layer-8-master-and-house.md` |

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
- 2026-11-11: IN PROGRESS. Fix worker started.
