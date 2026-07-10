# Engine tuning profile — Phase 3 + the Phase 2 wrapper/drift leftovers

> **This doc supersedes the now-archived `engine-dsl.md` design record**
> (`docs/tasks-archive/2026-06/20260630-engine-dsl-design-record.md`) and is the **authoritative tracker**
> for the remaining EngineDsl/PipelineDsl work.

> **STATUS (updated 2026-07-05, branch `ignitor-dsl-surface`):** IN PROGRESS — **Part A #1 (Adsr
> `declickSeconds`/`expK`) DONE; Part A #2 (filter feel knobs) DONE/RESOLVED — already on the pipeline
> `StageDsl.Filter`, don't duplicate on the ignitor filters.** Remaining: Part A #3 (drift carriers — folds
> into Part B) + Part B (Phase 3 `EngineTuning`), the headline piece. **Done & committed already:** Phase 1 (
> PipelineDsl), Phase 2
> oscillator *sources* (super-* unison family + single-shape oscs + static supertype inferrer + `WaveIgnitor.shapeMax`),
> and the noise-generator calibration knobs. Full design + history live in the archived
`docs/tasks-archive/2026-06/20260630-engine-dsl-design-record.md` (§2.1, §3);
> this doc is the focused punch-list of what's left.
>
> ⚠ The design in `engine-dsl.md` predates two renames — use the **current** names here: super-osc spread param is
> `spread` (not `freqSpread`), the character knob is `spreadPower` (not `detunePower`), subtypes are
> `IgnitorDsl.SuperSaw`/`SuperSine`/… and the wire annotation is `@WireName` (not `@Serializable`/`@SerialName`).

## Why this is the headline remaining piece

The whole EngineDsl effort exists so the Motör's *voice character* can be tuned from KlangScript instead of
"edit an `internal const val` in `OscillatorTuning.kt` → recompile → listen." The oscillator sources now expose
**every** knob a profile would set (Phase 2 sources done), so the groundwork is in place. Phase 3 turns engines
into complete identities (e.g. `modern`/`pedal` today, a future `c64`/`nes`): an oscillator field left at its
default resolves from the *active engine's* tuning profile. This is the item most aligned with the Q3 "nail the
sounds" goal — it makes by-ear engine tuning a live-coding loop.

## Part A — Phase 2 leftovers (do first; they widen the knob surface Phase 3 resolves)

These are the oscillator **wrappers** and **drift carriers** that Phase 2 deferred (sources are done). Same
pattern as the shipped osc subtypes: typed `IgnitorDsl` subtype → `IgnitorDslRuntime` thread → factory param →
`@KlangScript.TypeExtensions` chained methods → dual-language + render-effect + sync-guard specs. Each field
**defaults to today's `OscillatorTuning` const** (behavior-identical).

1. ✅ **DONE (2026-07-04) — `IgnitorDsl.Adsr` gained `declickSeconds` + `expK`.** Both landed as
   **`IgnitorDsl.Slots` Params** (`Slots.declickSeconds` = 0.0/off; `Slots.expK` = 3.0 mirroring `ADSR_EXP_K`)
   — i.e. **nodes/slots, not plain `Double`** → oscParam-addressable / patternable / in `collectParams()`,
   read per-block in `AdsrIgnitor`. `AdsrIgnitor` got the opt-in declick one-pole (`envDeclickCoeff`, primed
   to first level) + parameterized `adsrExpShape(x, k, norm)`. KlangScript `.declickSeconds(x)` / `.expK(x)`
   take `IgnitorDslLike` (config chaining via the copy-onto-`Adsr`-or-wrap idiom, like `adsrCurves` — no
   return-type narrowing needed). Behaviour-identical by default. Guards: `AdsrIgnitorKnobsSpec`,
   `StdLibOscTest`, `IgnitorDslWireCodecSpec`. **Precedent for #2/#3 + the §Part B wrinkle:** character
   knobs go in as Slots (nodes), sidestepping the plain-`Double` resolution problem.
2. ✅ **DONE / RESOLVED (2026-07-05) — the filter feel knobs already live on the pipeline `StageDsl.Filter`.**
   `StageDsl.Filter(cutoffOffsetPerAnalog, drivePerAnalog, driftRelToOsc)` (PipelineDsl Phase 1) is **fully
   wired**: `FilterPipelineBuilder` (`:63`) + `VoiceFactory` read the stage values (`stage.cutoffOffsetPerAnalog`
   → `perVoiceCutoffOffsetMul`, `stage.drivePerAnalog` → the SVF, `stage.driftRelToOsc` → the filter
   `AnalogDrift`), with `KlangScriptStageExtensions` chained methods + `WireCodecRoundTripSpec`. That IS the
   filter-character surface (the voice-strip filter). **Do NOT duplicate on the ignitor `Lowpass`/`Highpass`/
   `Bandpass` wrappers** — those are a separate in-graph surface (cutoff/q/analog; their `analog` already gives
   the SVF drive), and the per-voice cutoff-offset/drift paths are voice-strip-only (`VoiceFactory`).
3. **Analog-drift carrier params** — the ~5 *musical* drift params (the calibrated ones from the
   analog-drift-tuning work, NOT the derived math coefficients) become configurable fields on the carriers.
   OPEN — not exposed on any DSL surface today (only the `analog` *amount* is). **Overlaps Part B:** these are
   exactly the per-engine drift character an `EngineTuning` profile would set (`driftFastTauSec`/`driftSlowTauSec`/…)
   → fold into Phase 3 rather than adding standalone per-osc fields, unless per-instance drift tuning is wanted.

> Sine/impulse/zaw/zamp/noise have no tunable character → intentionally untouched. `pluck`/`superpluck`
> character is already ctor fields → chained-method consistency is optional.

## Part B — Phase 3: `Osc.EngineDefault` + `EngineTuning` profile

1. **Sentinel** — `IgnitorDsl.EngineDefault` (`@WireName("engineDefault")`, no params), exposed as
   `Osc.EngineDefault`. Sibling of `Freq`/`Constant`/`Param`.
2. **Flip defaults** — oscillator/wrapper field defaults flip from `Constant(today)` / `Slots.X` to
   `EngineDefault` where the value should be engine-resolvable (e.g. `SuperSaw.spreadPower`).
3. **`EngineTuning` profile** (`audio_bridge`) — a data class of the engine-resolvable osc settings
   (`spreadPower`, `sideAtten`, `gainJitter`, `sawResetSamples`, `driftFastTauSec`, `driftSlowTauSec`, …);
   **the fallback default == today's const**, so `modern`/`pedal` reproduce today's sound exactly. Carried on
   the registered `PipelineDsl`/`PipelinePreset`, rides the existing `@WireName` codec.
4. **Resolution** (`audio_be`) — thread the active engine's `EngineTuning` into the `buildIgnitor` build
   context. Per-field cascade: **explicit instance value → engine profile → field default**. Construction-time
   only, zero per-sample cost.
5. **KlangScript** — `Osc.EngineDefault` property + an authoring surface to set a profile
   (`Engine.modern().tune(spreadPower = 1.5, …)` or a `Tuning.of(...)` builder).

### The known wrinkle (decide first)

The Phase 2 character knobs are plain **`Double`** fields, not `IgnitorDsl` nodes. The `EngineDefault` sentinel
slots cleanly into the **node** fields (`freq`/`voices`/`spread`/`analog`), but the `Double` knobs need a
**parallel resolution path** (e.g. a `Double?`-nullable "unset → engine profile" or a small `EngineDouble`
wrapper). Settle this before flipping defaults — it shapes §B.2/§B.4.

## Tests

- Two engines with different `EngineTuning` render different `spreadPower` for the same bare `Osc.supersaw()`;
  an explicit `.spreadPower()` ignores the engine; **`modern` reproduces today's sound exactly** (golden).
- Part A: each new wrapper/drift knob reaches the audio (render-effect guard); defaults match the engine consts
  (extend the `*DefaultsSyncSpec` family); serialization round-trip (jvm + js).

## Out of scope

- **Phase 4 — Katalyzer** (per-orbit effect constants): belongs to the planned per-orbit counterpart to
  Ignitors, not EngineDsl. Boundary only.

## Critical files

- `audio_bridge/.../IgnitorDsl.kt` — wrapper subtypes, `EngineDefault`, `EngineTuning` (+ `collectParams`).
- `audio_be/.../ignitor/IgnitorDslRuntime.kt` (`buildIgnitor` resolution), `OscillatorTuning.kt`,
  `AnalogDriftCoeffs.kt`, `AdsrIgnitor`, `FilterPipelineBuilder.kt`.
- `klangscript/.../stdlib/` — `Osc.EngineDefault`, `Engine.tune(...)`, wrapper `@TypeExtensions`.
- Full design reference: archived `docs/tasks-archive/2026-06/20260630-engine-dsl-design-record.md` §2.1 (wrappers),
  §3 (Phase 3).
