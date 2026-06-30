# Engine tuning profile — Phase 3 + the Phase 2 wrapper/drift leftovers

> **STATUS (2026-06-30, branch `engine-dsl-osc-dsl-parameterization`):** NOT STARTED. This is the remaining
> tail of the EngineDsl/osc-DSL work-stream. **Done & committed already:** Phase 1 (PipelineDsl), Phase 2
> oscillator *sources* (super-* unison family + single-shape oscs + static supertype inferrer + `WaveIgnitor.shapeMax`),
> and the noise-generator calibration knobs. Full design + history live in `docs/tasks/engine-dsl.md` (§2.1, §3);
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

1. **`IgnitorDsl.Adsr` wrapper subtype** — expose `declickSeconds` (the ignitor `AdsrIgnitor` gains a declick
   one-pole, same as the VCA — it has none today), `expK`, and `attackCurve`/`decayCurve`/`releaseCurve`.
   `.adsr()` should return the subtype so config-first chaining works; base lookups still resolve via the
   static supertype inferrer.
2. **Filter feel knobs** — `Lowpass`/`Highpass`/`Bandpass` subtypes expose their drift/cutoff-offset/drive-scale
   feel knobs (the same constants `FilterPipelineBuilder` reads today). (Cross-check against the already-shipped
   `FilterStageDsl` from PipelineDsl Phase 1 so we don't duplicate the filter-character surface.)
3. **Analog-drift carrier params** — the ~5 *musical* drift params (the calibrated ones from the
   analog-drift-tuning work, NOT the derived math coefficients) become configurable fields on the carriers.

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
- Full design reference: `docs/tasks/engine-dsl.md` §2.1 (wrappers), §3 (Phase 3).
