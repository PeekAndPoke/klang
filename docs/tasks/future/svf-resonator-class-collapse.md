# Collapse `BaseSvf` and `SvfBPF` into one static-coefficient resonator class

Status: **future, parked.** Found in the phase 3 step 9 (a2) review (2026-09-27); opened as its own file 2026-09-28
when the phase 3 record was archived (`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`, section 9 row
"9 follow-up").

## What it is

`LowPassHighPassFilters.BaseSvf` and its one subclass `SvfBPF` (`audio_be/.../filters/LowPassHighPassFilters.kt`)
are what is left of the voice strip's SVF family after step 9 retired `SvfLPF`, `SvfHPF` and `SvfNotch`. Their one
production user is `ResonatorBank` (the orbit's `body` and `vowel`), which builds each band once with a fixed cutoff
and q and never retunes it.

## Why it is open

The sweep machinery is dead in production since the strip filters retired:

- `BaseSvf.sweepCutoff(startHz, endHz, frames)` has one production caller, the constructor's snap with 0 frames
  (its own KDoc says so);
- the `a1Step`/`a2Step`/`a3Step`/`kStep`/`gStep` fields and `sweepFrames`, and `SvfBPF.process`'s step-add branch,
  therefore never move a coefficient;
- `g` is stepped but never read by `SvfBPF` (its KDoc: "kept so the one remaining loop stays byte-for-byte");
- `cutoffOffsetMul` (the strip's per-voice cutoff tolerance) is never passed by `ResonatorBank`.

Tests still exercise the sweep (`LowPassHighPassFiltersSpec` "SvfBPF - sweepCutoff updates behavior" and the NaN
row), as does `EffectBenchmark`'s `SvfBPF` case.

## The work

One class with coefficients computed once at construction (through `computeSvfCoeffs`, as today), no sweep fields,
no `g`, no `cutoffOffsetMul`. Acceptance: the orbit renders bit-identical (the loop's arithmetic is unchanged at
zero sweep frames), `ResonatorBankSpec` and `FilterNormalizationSpec` unchanged; the sweep rows go with the sweep.
Check `SvfCoeffSweep`'s KDoc, which names `BaseSvf` as a caller.

No sound change and no decision needed; parked only because nothing forces it.
