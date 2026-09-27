# A door for the one-pole highpass (and the one-poles on the Katalyst)

Status: **future.** Found in the test consolidation audit (2026-09-27); the maintainer wants the primitive
kept and reachable: "we might need it in the future, so the only thing is the missing doors".

## What exists

- **One-pole lowpass**: the engine node `OnePoleLowpassIgnitor` (`audio_be/.../ignitor/IgnitorFilters.kt`),
  the wire node `IgnitorDsl.OnePoleLowpass`, the doors `onepole(freq)` (Kotlin and KlangScript), and the
  `classic()` slot `onepole` (sprudel's `onepole(...)` door). Complete.
- **One-pole highpass**: only the engine node `OnePoleHighpassIgnitor` and `Ignitor.onePoleHighpass(...)`.
  No `IgnitorDsl` node, no runtime arm, no door on either surface, no caller, no test. Unreachable today.
- **The Katalyst**: no one-pole stage at all, lowpass or highpass.

## Why it is worth a door

A 6 dB/oct highpass is the gentle rumble and DC trim the SVF cannot give (the SVF highpass is 12 dB/oct
per pass). The only DSL spelling today, `x.minus(x.onepole(f))`, builds the source twice (two
oscillators, which diverge for noise and analog drift) and has the Nyquist droop the canonical bilinear
topology was chosen to avoid (`LowPassHighPassFilters.kt` history).

## The work (per `/dsl-design`)

1. `IgnitorDsl.OnePoleHighpass(inner, freq)` on the wire, its runtime arm (the gate on `freq <= 0`, as the
   lowpass has), `IgnitorDslWalk`, `GraphCensus`, the optimizer note.
2. The door on both surfaces in one deliverable, with a door-parity row. Naming is the maintainer's: the
   lowpass is `onepole(freq)`, so the pair wants one shape (for example `onepole(freq)` and
   `onepoleHp(freq)`, or a `highpass` flag, or renaming both); decide before building.
3. A law spec for the node (the -3 dB point, DC gain 0, Nyquist gain 1), mutation-checked.
4. Open: does the Katalyst get one-pole stages (an orbit or master DC trim), and does `classic()` want a
   one-pole highpass slot? Neither is needed today.
