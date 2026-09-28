# New oscillator candidates

Status: **future, feature, not started.** Extracted 2026-09-27 from `audio-pipeline-open-topics.md`
(archived as `docs/tasks-archive/2026-09/20260927-audio-pipeline-open-topics.md`). Full specs, maths
and references for every candidate: `docs/tasks-archive/2026-03/20260329-new-oscillator-implementations.md`,
section "Candidates for New Exciters".

Each one arrives as an Ignitor oscillator on both doors (KlangScript and Kotlin), with a sprudel
sound name where it earns one, and is tuned by ear.

## Open

| Candidate                                   | Priority (March spec) | Note, 2026-09-27                                                                                   |
|---------------------------------------------|-----------------------|----------------------------------------------------------------------------------------------------|
| **Phase distortion** (Casio CZ warped sine) | high                  | Not built.                                                                                         |
| **Chebyshev harmonics** as an oscillator    | high                  | Only as a distortion shape (`DistortionShape.CHEBYSHEV`, 3rd harmonic); no harmonic-mix oscillator. |
| **Hard sync**                               | medium-high           | Not built.                                                                                         |
| **Formant / vocal** oscillator              | medium                | Not built. Related, not the same: the orbit `vowel` filter and `future/phoneme-singing.md`.       |
| **Waveguide reed / clarinet**               | medium                | Not built.                                                                                         |
| **Wavetable**                               | low                   | Not built; needs wavetable data.                                                                   |

## Covered since the spec

- **Additive (sum of harmonics):** `Osc.sine()` with `harmonics`, `octaves` and `fundamental`
  (`docs/plans/sine-partial-banks.md`, shipped 2026-09-07).
