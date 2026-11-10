# KLANG-00430: Audit fixes 6: the sources (band-limited edges, the impulse, sample interpolation and the last frame, the noises)

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
| L3-02 | major | The "band-limited" saw and square use the weakest member of the BLEP family, and the KDoc's reason is untrue | Confirmed, smaller: A two-point PolyBLEP is 22 to 29 dB cleaner below 5 kHz but only 6 to 8 dB below 20 kHz; +12 semitones of pitch stage costs the saw 20 dB below 5 kHz |
| L3-03 | minor | The impulse aliases at -5.3 dB re its harmonics by construction | Confirmed, smaller: -5.4 to -4.4 dB by construction, a documented single-sample spike nobody uses; the proposed triangle kernel reaches -12.5 dB, not -20 |
| L3-04 | minor | The impulse outputs a run of 1.0 while its phase runs backwards | Confirmed: Ratio -1: 4778 of 4800 samples at 1.0; the built-in impulse under `fm` depth 440 Hz at 220 Hz: 1624 nonzero samples per 0.1 s |
| L3-05 | minor | Sample playback is two-point linear at every rate, and the JVM leaves the whole rate conversion to it | Confirmed, smaller: The auditor's numbers reproduce exactly; a four-point kernel gains 11 to 47 dB on transpositions but only 1 to 15 dB on rate conversion |
| L3-09 | (read only) | The perlin noise repeats every 256 lattice units: 1.78 s at `rate` 1 | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-10 | (read only) | Four noise colours are set per sample, so they follow the device's sample rate | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-11 | (read only) | The sources' levels differ by up to 20 dB, and a unison stack gets quieter with every voice | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-12 | (read only) | At an even voice count the stack has no centre voice, and only one of the two middle voices gets the reduced jitter | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-13 | minor | Stereo files: the browser keeps the left channel only, the JVM averages; the ref page says both | Confirmed: By reading, the stereo half of L9-07 |
| L3-14 | minor | The playhead never plays a sample's last frame, and a loop over the whole sample writes a zero at every pass | Confirmed: The last frame of a one-shot is never played; `loop(1)` writes a zero at frames 7, 15, 23 ... |
| L3-16 | (read only) | Two rng consumers in one voice make its noise depend on where the block borders fall | not challenged; the auditor's reading in `layer-3-sources.md` |
| L9-07 | minor | The browser and the JVM decode a sample differently, so the offline render of a sample song is not the live sound | Confirmed: By reading: left channel at 48 kHz against the average at the file's rate |

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
