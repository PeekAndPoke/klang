# KLANG-00429: Audit fixes 5: the Ignitor build (the fused filter's gate, instrument names, per-note buffers, the off stages' build cost)

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
| L5-01 | major | A fused filter loses its gate: an unset cutoff plays a 1 kHz filter | Confirmed: An unset `hpf.freq` slot plays a 1 kHz highpass when optimized: -26.3 dB at 220 Hz, through the registry too |
| L5-02 | major | The inline instrument's name is structural, the build shares by identity: an edit that only changes the sharing is not heard | Confirmed: One name (`ignitor-0`) for two trees whose renders differ by 3 dB RMS and in waveform |
| L5-03 | minor | Large per-note buffers are allocated at note-on and never pooled (shimmer ring, pluck rings) | Confirmed: Pluck 26 KB, superpluck 167 KB, shimmer 775 KB per note, never pooled |
| L4-09 | major | Shimmer allocates its 96000-sample ring at every voice build, on the audio thread | Confirmed: Shimmer at wet 0: 774,520 B and 71 us per note; 16 in one block 1.6 ms median, 4.8 ms max |
| L5-04 | minor | A gated-off stage still pays the whole build path: 5 to 10 us and several KB per note for a tail that builds one stage | Confirmed: The gated-off tail is 11 of the 12 us and 3.6 of the 5.8 KB of a default saw build |
| L5-05 | (read only) | A pitch node bends a musical oscillator in a SOURCE's parameter position, and a shared one depends on who renders first | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |
| L5-07 | minor | Inside a region a shared node repeats one block for ever once the region's frame counter saturates | Confirmed: A shared body node freezes into a 128-frame loop at saturation; the auditor's example (the input read twice) does not |
| L5-08 | (read only) | A knob that reads a lifted LFO inside a region reads the upsampler's start-up at the first block, and pays a whole conversion per read | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |
| L5-09 | note | Per-note allocations in render at the first block (the lint's leads, with their reach) | Confirmed, smaller: First-block allocation: 80 B for a region voice, 0 B for the others measured |
| L5-10 | note | Registered inline instruments are never dropped while a playback lives, and a noise instrument gets a new name at every evaluation | Confirmed: By reading |
| L5-11 | (read only) | The gate reads leaves only: the full table, both directions | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |
| L5-13 | (read only) | What the parity specs do not reach | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |

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
