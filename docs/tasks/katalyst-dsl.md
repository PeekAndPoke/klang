# Katalyst DSL — author per-orbit effect chains

> **Stub — not started (2026-07-04).** Priority: **MUST** (see `_priorities.md`). This is the
> authoring surface for the Katalyst layer; the effects themselves already shipped.

## Why it matters for CPU — the motivation, with numbers (added 2026-08-11)

A per-voice filter is paid **once per simultaneous voice**; the same filter on the orbit bus is paid **once**. Most
orbits run 2+ voices, and `superimpose` multiplies that — which is exactly how
`body` became the CPU sink that moved it to orbit level in July.

Measured filter costs (JVM / Node, `docs/benchmarks/2026-08-11_103957_*`):

| filter                          | JVM                 | Node                |
|---------------------------------|---------------------|---------------------|
| SvfBPF / SvfLPF / SvfHPF, plain | ~0.000167           | ~0.000279           |
| SvfHPF **with `analog=3`**      | 0.000701 (**4.2×**) | 0.000807 (**2.9×**) |

⚠️ **But "per-voice vs per-orbit" is not the right criterion.** Three cases, and only the first is a free win:

1. **Static cutoff + LINEAR filter → moves losslessly.** A linear filter obeys superposition, so
   `filter(a) + filter(b) == filter(a + b)`. Identical output, `N×` cheaper. `SvfBPF` has no `analog`
   parameter at all, so every bandpass is in this class.
2. **Pitch-tracking cutoff → CANNOT move.** `.highpass(Osc.freq().mul(k), ...)` tracks the note. The orbit bus carries
   several pitches at once, so there is no single correct cutoff. These have to stay per-voice, and no DSL can fix that.
3. **Nonlinear (`analog > 0`) → moves, but is NOT the same sound.** State-dependent damping breaks superposition:
   filtering the sum ≠ summing the filtered parts. Offering it at orbit level is legitimate, but it is a different
   effect, not an optimisation — and it must be documented as such or people will "move it for CPU" and wonder why the
   tone changed.

**Worked example** (a user's SuperSaw ignitor, 2026-08-11):

```kotlin
signal.add(signal.bandpass(800, 0.5)).add(signal.bandpass(1500, 0.5))   // case 1 — CAN move
  ...
  .highpass(Osc.freq().mul(pHpTrack), pHpQ, pAnalog)                     // cases 2+3 — CANNOT
```

The two bandpasses are static and linear, so at 2 voices/orbit moving them halves their cost for bit-identical output.
The highpass both tracks pitch *and* uses `analog`, so it is stuck per-voice on two independent grounds — and it is the
expensive one (4.2× a plain filter).

**Design consequence for this DSL:** the authoring surface should make the distinction visible, not leave it as
folklore. A user reaching for the orbit chain to save CPU needs to know which of their filters can follow them there.

## Context — what exists

The **Katalyst** layer is the per-orbit (Cylinder-level) effect chain — the bus counterpart to the
per-voice **Ignitor** (exciter) and the per-voice **Pipeline** (signal path). One word per concept.

The effects are already built and committed under `audio_be/src/commonMain/kotlin/cylinders/katalyst/`:
`KatalystEffect` (base) + **Body / Formant(vowel) / Delay / Reverb / Phaser / Compressor / Ducking**,
plus `KatalystContext` and `VoiceLease`. Body & vowel moved here from the per-voice filter chain in the
2026-07-03 orbit-Katalyst work (archived `../tasks-archive/2026-07/20260703-body-vowel-orbit-katalyst.md`).

**But the chain is hardcoded.** `Cylinder.kt:68`:

```kotlin
val pipeline: List<KatalystEffect> = listOf(body, vowel, delay, reverb, phaser, compressor)
```

There is no way to author, reorder, or parameterise a per-orbit chain from KlangScript.

## Goal

A KlangScript-facing DSL to declare a per-orbit effect chain — which effects, in what order, with what
params — mirroring how `PipelineDsl` declares the per-voice pipeline and `IgnitorDsl` the exciter. This
is the materialised **Phase 4 ("Katalyzer")** of the archived engine-dsl design record — the effects
landed; the authoring surface didn't.

## Open design questions

- **Surface.** A `Katalyst { ... }` builder mirroring `PipelineDsl`'s `StageDsl`? How does an orbit get
  its chain — a song-level `orbit → katalyst` map, a `.katalyst(...)` call, per-orbit vs per-song scope?
- **Vocabulary.** Expose the 7 existing effects as DSL stages with their params (body, vowel/formant,
  delay, reverb, phaser, compressor, ducking).
- **Order.** Today fixed body→vowel→delay→reverb→phaser→compressor; make it declarable/reorderable with
  a sensible default.
- **Wire format.** New `Cmd.RegisterKatalyst`(?) + `@WireName` codec entries — same pattern as
  `PipelineDsl` / `Cmd.RegisterPipeline` / `PipelineRegistry`.
- **Application path.** `PlaybackEngineDispatcher` / `Cylinder` consume the registered chain instead of
  the hardcoded `listOf(...)`.
- **Master interaction.** Orbit bus → master/loudness stage (per-playback D6).
- **Body/vowel should become EqCore-backed here (noted 2026-08-20, unified-eq D2b).**
  `FormantFilter` is structurally N parallel bandpasses × gain, summed — exactly `EqCore`'s
  RAW_TAP topology (minus the dry path). Today it makes THREE passes over the block per band
  (a per-band `copyInto` into `bandBuffer`, the class-form `SvfBPF.process` with state in
  fields, a separate mix loop); EqCore's tap arm does bandpass + gain + accumulate in ONE
  pass with state in locals — the loop shape that won the D2b bake-off by 17–38% on V8.
  When Katalyst adopts EqCore (precondition: the coefficient-ramp API — EqCore is snap-only,
  see its KDoc), rebase body/vowel on it instead of hand-optimizing FormantFilter separately:
  one machinery, one place to optimize, current `FormantFilter` output as the bit-parity
  oracle for the linear path, by-ear-tuned tables/constants untouched. Leverage is per-ORBIT
  (once per orbit, maintainer's own optimization), so absolute CPU is small — measure before
  prioritizing; the win is consolidation first, cycles second.

## Links

- Effects + hardcoded order: `audio_be/.../cylinders/katalyst/`, `Cylinder.kt:68`.
- Prior design (Phase 4 Katalyzer): archived `../tasks-archive/2026-06/20260630-engine-dsl-design-record.md`.
- Counterpart DSL work: `engine-tuning-profile.md` (Pipeline DSL finish).
- Master stage: `per-playback-engine.md` §H / D6 — **and the now-SHIPPED Master
  DSL ([archived](../tasks-archive/2026-08/20260803-master-dsl.md)) is the pattern to follow**: it
  sets both the application path (in-pattern, registration + id-on-voice)
  AND the reuse rule (thin shells over the shared `audio_be/effects/` DSP classes; wire stages as
  `sealed @WireName` variants). Katalyst DSL and Master DSL share one effect vocabulary, different hosts (orbit bus /
  master bus).
- Memory: `project_katalyzers`, `project_engine_naming`, `pipeline_stage_design`, `osc_ignitor_misnamed`.
