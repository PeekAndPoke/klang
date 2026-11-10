# KLANG-00432: Audit fixes 8: the per-note stages (the filter envelope's grid, fastExp, the doors' soft cap, non-finite samples, the house stage)

> **Status (2026-11-10): DESIGNED.** From the engine audit (KLANG-00420); maintainer, 2026-11-10: fix all findings, sound changes are fine.
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
| L4-02 | minor | The filter envelope is read at a block's two ends, and every sprudel filter envelope reaches it | Confirmed, smaller: Mechanism, a stale ledger line and SEVEN songs confirmed; the per-note variation is -40 to -63 dB re the peak in the songs' own shapes; the grid fix at N=128 is invariant but no more accurate |
| L4-04 | (read only) | `exp` and `stompbox` call `pow` per sample once the drive pushes the argument past 22 | not challenged; the auditor's reading in `layer-4-note-stages.md` |
| L4-05 | (read only) | The doors' soft cap is the alias source: a near-hard knee from 0.95 to 1.10 after every `Shape` | not challenged; the auditor's reading in `layer-4-note-stages.md` |
| L4-06 | minor | Shimmer stores a NaN in its grain ring and plays it again | Confirmed: one NaN in, two NaN out (frames 4800 and 13599, 0.183 s later) |
| L4-07 | minor | `coarse` turns one infinite sample into `factor` infinite samples | Confirmed: one infinity in, `factor` infinities out (4 and 4800) |
| L4-08 | minor | Five answers to one bad sample: the layer has no rule for a non-finite SAMPLE outside the arithmetic nodes | Confirmed: the rows I measured or read in the probe table match |
| L4-10 | (read only) | The cutoff clamp sits 1 Hz under Nyquist, where the filter is a resonator at Nyquist | not challenged; the auditor's reading in `layer-4-note-stages.md` |
| L4-11 | minor | The bare `drive` node emits infinity and NaN from finite input | Confirmed: `drive` 257 and up: NaN on every exact-zero input sample (silence), infinity elsewhere; 256 is finite |
| L4-01 | minor | The saturating SVF stops decaying once its state is large: more damping means a slower decay | Confirmed, smaller: the hold law matches the engine within 3 percent, but no ordinary signal reaches the slow region: it takes a transient 60 to 80 dB over full scale |
| N1 | new | The analog SVF oscillates for good near fs/3: q 200 and analog 5 at 16 kHz ring at full scale after the input stops | found in the disproof pass, `disproof-safety.md` |
| L8-01 | minor | A huge finite sample on the mix becomes up to seconds of full-scale DC: the DC blockers carry it and the 20:1 limiter cannot bring it under the clip | Confirmed, smaller: numbers confirmed (1e15 for one block: 26,783 rail samples, clean after 1.13 s); only extreme raw values put such a block on the mix |
| L8-02 | minor | A huge constant on the mix absorbs every playback's music: steps of 0.125 at 1e15, exact silence above 9e15, and the DC blocker hides the cause | Confirmed, smaller: 1e15 crushes every playback's music (error -15.4 dB), 1e17 gives exact zeros; same reach as L8-01 |
| L8-06 | (read only) | The 16-bit edge truncates toward zero and has no dither | not challenged; the auditor's reading in `layer-8-master-and-house.md` |
| L8-07 | (read only) | The house DC pole is a fixed coefficient, so its corner moves with the sample rate, and the comment beside it is wrong | not challenged; the auditor's reading in `layer-8-master-and-house.md` |

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
