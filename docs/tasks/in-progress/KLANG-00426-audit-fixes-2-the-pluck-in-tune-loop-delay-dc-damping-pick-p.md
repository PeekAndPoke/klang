# KLANG-00426: Audit fixes 2: the pluck in tune (loop delay, DC, damping, pick position)

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
| L3-01 | critical | The pluck is flat: the loop filter's delay is not taken out of the delay line | Confirmed: Measured in the engine by two methods agreeing within 0.001 cent there: the default pluck is -15.75 cents at A4 and -36.73 at C6; brightness 1 reads 0.00 |
| L3-06 | minor | A pluck carries a random DC offset that outlives its tone | Confirmed: After 1.9 s the mean is 0.08 to 1.84 times the AC level (pluck), 0.27 to 4.26 (superpluck) |
| L3-07 | (read only) | The pluck's linear-interpolated read makes the damping of the highs depend on the note | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-08 | (read only) | `pickPosition` sets the burst's length, not a pick position, and moves the level by about 10 dB | not challenged; the auditor's reading in `layer-3-sources.md` |

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
