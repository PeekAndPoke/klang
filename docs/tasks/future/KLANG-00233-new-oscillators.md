# KLANG-00233: New oscillator candidates

> **Status (2026-10-20): IDEA.**

Status: **future, feature, not started.** Extracted 2026-09-27 from KLANG-00218 (audio pipeline open topics)
(archived as KLANG-00218 (audio pipeline open topics)). Full specs, maths
and references for every candidate: KLANG-00076 (new oscillator implementations),
section "Candidates for New Exciters".

Each one arrives as an Ignitor oscillator on both doors (KlangScript and Kotlin), with a sprudel
sound name where it earns one, and is tuned by ear.

## Open

| Candidate                                   | Priority (March spec) | Note, 2026-09-27                                                                                   |
|---------------------------------------------|-----------------------|----------------------------------------------------------------------------------------------------|
| **Phase distortion** (Casio CZ warped sine) | high                  | Not built.                                                                                         |
| **Chebyshev harmonics** as an oscillator    | high                  | Only as a distortion shape (`DistortionShape.CHEBYSHEV`, 3rd harmonic); no harmonic-mix oscillator. |
| **Hard sync**                               | medium-high           | Not built.                                                                                         |
| **Formant / vocal** oscillator              | medium                | Not built. Related, not the same: the orbit `vowel` filter and KLANG-00181 (phoneme singing).       |
| **Waveguide reed / clarinet**               | medium                | Not built.                                                                                         |
| **Wavetable**                               | low                   | Not built; needs wavetable data.                                                                   |

## Covered since the spec

- **Additive (sum of harmonics):** `Ignitor.sine()` with `harmonics`, `octaves` and `fundamental`
  (KLANG-00185 (sine partial banks plan), shipped 2026-09-07).

## History

- 2026-10-20: numbered KLANG-00233 (was `docs/tasks/future/new-oscillators.md`).
