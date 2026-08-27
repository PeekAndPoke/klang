# DSL Kotlin-surface parity — every DSL usable from Kotlin directly

**Principle (maintainer, 2026-08-20):** the foundation of every Klang DSL must allow direct
use from Kotlin, not only from KlangScript. Sprudel achieved this and is the model. Script
stdlib and Kotlin extensions are two doors to ONE DSL — every surface addition lands on both
in the same deliverable.

## Current state

- **Sprudel**: full parity (the model).
- **IgnitorDsl**: the chained filter/effect/oscillator vocabulary largely has fluent Kotlin
  extensions in `audio_bridge` (`.notch()/.highpass()/.lowpass()/.bandpass()/.adsr()/.fm()/
  .drive()/.detune()/...` — the audio_be parity specs use them daily). **Gaps:**
  - ~~`Eq` has NO fluent builder on either surface~~ **DONE (D5, 2026-08-20)**: `.eq()` on
    the base type plus TWO section methods typed onto `IgnitorDsl.Eq` (the supersaw
    config-method shape) — `.band(freq, q, db)` (serial bell) and `.tap(freq, q, gain)`
    (parallel boost, the form Der Schmetterling's guitar uses). Both shipped on BOTH doors
    with identical names and defaults. Future Eq section methods land on the same receiver.
  - **Known gap (D5 round 3):** the Kotlin door ships HOMOGENEOUS overloads (all-IgnitorDsl or
    all-Double), while the script door takes `IgnitorDslLike` per parameter — so the mixed
    `band(someDsl, 0.7, 6.0)` shape compiles in script but not in Kotlin, where the scalars
    need `IgnitorDsl.Constant(...)`. Names/defaults match (the rule holds in the letter);
    expressiveness does not.
  - ✅ **The filter doors are DONE (2026-08-27).** They used to be the worst offenders:
    `.lowpass()/.highpass()/.bandpass()/.notch()` had ONLY the all-`Double` form and no
    `analog` parameter at all, so a tracking cutoff (`Osc.freq().mul(k)`, which the script
    door supports and Der Schmetterling uses) was unreachable from Kotlin without hand-building
    `IgnitorDsl.Highpass(...)`. All five (those four plus `onepole`) now ship an `IgnitorDsl`
    primary plus a scalar convenience overload, the same shape `band`/`tap` already used, and
    `analog` is in the same fourth slot on both doors. Pinned by
    `KlangScriptFilterDoorParitySpec`, which compares the two doors node-for-node.
    Behaviour-preserving: node and script door both defaulted `analog` to `Constant(0.0)`.
  - A full audit of script-stdlib functions vs Kotlin extensions has not been done; unknown
    smaller gaps likely (phasePool/analog/spreadPower/gainJitter etc. — check which exist as
    Kotlin extensions vs script-only).
- **Master DSL / Pipeline DSL / (future) Katalyst DSL**: audit alongside.

## Work

1. Audit: enumerate KlangScript stdlib surface per DSL; diff against Kotlin extensions;
   produce the gap list.
2. Close the gaps (mechanical; each new extension mirrors the stdlib function's defaults —
   parameter-parity rule: same names, same meanings, same defaults on both doors).
3. Standing rule for reviews: a new stdlib function without its Kotlin twin is a finding.

## Links

- Memory: `feedback_dsl_dual_surface`, `feedback_parameter_parity`.
- Unified-eq plan D5 (both-doors requirement recorded there).
