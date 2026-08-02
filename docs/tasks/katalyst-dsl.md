# Katalyst DSL — author per-orbit effect chains

> **Stub — not started (2026-07-04).** Priority: **MUST** (see `_priorities.md`). This is the
> authoring surface for the Katalyst layer; the effects themselves already shipped.

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

## Links

- Effects + hardcoded order: `audio_be/.../cylinders/katalyst/`, `Cylinder.kt:68`.
- Prior design (Phase 4 Katalyzer): archived `../tasks-archive/2026-06/20260630-engine-dsl-design-record.md`.
- Counterpart DSL work: `engine-tuning-profile.md` (Pipeline DSL finish).
- Master stage: `per-playback-engine.md` §H / D6 — **and `master-dsl.md` (2026-08-02) is the pattern to follow**: it
  sets both the application path (in-pattern, registration + id-on-voice)
  AND the reuse rule (thin shells over the shared `audio_be/effects/` DSP classes; wire stages as
  `sealed @WireName` variants). Katalyst DSL and Master DSL share one effect vocabulary, different hosts (orbit bus /
  master bus).
- Memory: `project_katalyzers`, `project_engine_naming`, `pipeline_stage_design`, `osc_ignitor_misnamed`.
