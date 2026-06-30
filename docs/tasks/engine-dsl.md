# Engine DSL — configurable engines & oscillators from KlangScript

> **STATUS (2026-06-30, branch `dsl-enhancements`).** The code DSL was renamed `EngineDsl` → **`PipelineDsl`**
> in Phase 1; this doc keeps the original name for continuity.
> - **Phase 1 (PipelineDsl) — DONE + committed.** Data-driven pipeline + `modern`/`pedal` + Vca/Filter character
    > threaded + by-name registration (per-playback, fork-ready) + KlangScript `Engine`/`Stage` surface + the inline
    > `.pipeline(dsl)` application path. (Rename + handover details archived alongside this file.)
> - **Phase 2 oscillator SOURCES — DONE (uncommitted).** The whole super-* unison family
    > (`supersaw/ramp/sine/square/tri`: `spreadPower/sideAtten/gainJitter/centerJitter` + `Slots` unification) and the
    > single shape oscillators (`saw`/`ramp`/`square`(`Pulze`)/`triangle`: `resetSamples/shapeMax` resp.
    > `flankSamples/riseFlank/fallFlank`) are now typed `IgnitorDsl` subtypes with chained config methods, dual-language
    > specs, and the **static supertype inferrer** (so base `.lowpass()`/`.adsr()` still resolve on a narrowed osc).
    > `WaveIgnitor` gained a `shapeMax` param. Defaults == the engine constants (guarded by `SuperOscDefaultsSyncSpec`
    >

+ `WaveOscDefaultsSyncSpec`), so behavior-preserving. Detailed osc working notes:
  > `docs/tasks-archive/2026-06/20260630-osc-dsl-phase2.md`.

> - **Phase 2 STILL OPEN (deferred):** configurable *wrappers* — `IgnitorDsl.Adsr` (declick/expK/curves) and
    > `Lowpass/Highpass/Bandpass` feel knobs (§2.1) — and the **analog-drift carriers** (the ~5 musical drift params,
    > bucket E). Sine/impulse/zaw/zamp/noise have no tunable character → intentionally untouched. `pluck/superpluck`
    > character is already ctor fields → chained-method consistency is optional.
> - **Phase 3 (`Osc.EngineDefault` + engine tuning profile) is NEXT** — the oscillator sources now expose every knob
    > an engine profile would default. NOTE the wrinkle: the character knobs are plain `Double` (not `IgnitorDsl`
    > nodes), so the `EngineDefault` sentinel slots cleanly into the node fields (`freq/voices/spread/analog`) but the
    > `Double` knobs need a parallel resolution path.

## Context

Tuning the Motör engine today means: edit an `internal const val` → recompile → listen. There are **~67**
such constants (`ADSR_EXP_K`, `ENV_DECLICK_SECONDS`, the SuperSaw/AnalogSaw tuning constants, filter
humanization, …). The loop is too slow for tuning by ear.

Goal: configure the engine from KlangScript exactly like Ignitors/Oscillators — a `@Serializable` DSL,
**registered by name**, referenced from `VoiceData`, crossing the worklet via the KSP trust-codec. By-name
registration makes the live editor the tuning panel: edit a value → re-register → hear it, no recompile.

The ~67 constants live at **three scopes**, two of which already have a home in the project's naming:

| Scope                             | Examples                                                              | Home                                                                                                |
|-----------------------------------|-----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Per-oscillator (~25)              | `SUPERSAW_DETUNE_POWER`, `SAW_RESET_SAMPLES`, `ANALOG_*`              | **Ignitors** — `IgnitorDsl` subtypes (Phase 2), engine-resolvable via `Osc.EngineDefault` (Phase 3) |
| Per-orbit effect (~22)            | reverb/delay/phaser/comp/duck                                         | **Katalyzers** — future (Phase 4)                                                                   |
| Engine character + topology (~10) | `ADSR_EXP_K`, `ENV_DECLICK_SECONDS`, filter humanization, stage order | **EngineDsl** — new (Phase 1)                                                                       |

**Vision:** an engine is a complete *sonic identity* — swapping `engine` should re-skin a sound toward NES /
C64 / contemporary. The engine→oscillator coupling is intentional: in Phase 3 the engine carries a *tuning
profile* of default oscillator-param values, and oscillator fields default to an `Osc.EngineDefault` sentinel
that resolves against it.

## Decisions (locked)

1. Engines are **data, not hardcoded**; users author arbitrary pipelines, omitting stages freely. Minimal
   validation ("Motör stays raw").
2. **By-name registration**, mirroring Ignitors; referenced via the existing `VoiceData.engine: String`.
3. Config lives on **typed subtype DSLs** for sources AND configurable wrappers
   (`Osc.supersaw(): SupersawIgnitorDsl`, `.adsr(): AdsrIgnitorDsl`). Each builder returns its own subtype;
   **configure a node right after adding it, before the next wrapper**. Pass-throughs (`+`/`*`) stay base.
4. **Config updates the DSL instance directly** (`copy(field=…)`). `oscParams` map is only the opt-in user
   override channel for `Param` knobs the designer *opens*. Fields are `IgnitorDslLike` (number → `Constant`,
   `Osc.param(…)` → `Param`).
5. Two distinct envelopes, both tunable: engine voice amp-VCA (`Stage.vca().declick()`) and the
   ignitor-internal `IgnitorDsl.Adsr` (`.adsr().declickMs()`). Amp-envelope *times* stay on `note(…).adsr(…)`.
6. Engine = tuning profile, built in two steps: **Step 1** osc fields default to today's const; **Step 2**
   flip defaults to `EngineDefault`, resolved against the engine profile (fallback == today's const → no
   behavior change until an engine overrides).

## Feasibility (verified)

- **Typed subtype DSLs need no new language feature.** KlangScript already resolves methods AND properties via
  `getAllRegisteredSupertypes` = `[runtimeClass] + registeredSupertypes.filter { isInstance }`
  (`Environment.kt:312,353,370`) → enclosing scope, memoized. A subtype's methods are available when the
  object *is* that subtype; on a plain base value it errors (`Interpreter.kt:1382-1408`); base methods resolve
  via the supertype step. `@KlangScript.TypeExtensions(Subtype::class)` registers them; completion is
  type-aware (`ExpressionTypeInferrer` → `CompletionProvider`, FQCN match). Inheritance is derived from the
  real Kotlin type — declare `… : IgnitorDsl` and the chain is automatic. **Keep hierarchies linear** (order
  among multiple registered supertypes is map-iteration order).
- **By-name registration exists end-to-end** for Ignitors (player `registerOrLookup` + dedup
  `klang/.../IgnitorRegistry.kt`; `Cmd.RegisterIgnitor`; backend `audio_be/.../ignitor/IgnitorRegistry.kt`;
  KSP wire codec). EngineDsl mirrors it 1:1.

---

## Phase 1 — EngineDsl pipeline + registration + envelope/filter character

Makes engine character + topology data-driven and live-tunable. Solves the `ADSR_EXP_K`/`ENV_DECLICK_SECONDS`
pain.

### 1.1 Wire types — `audio_bridge` (new `EngineDsl.kt`)

```kotlin
@Serializable
data class EngineDsl(val stages: List<StageDsl>) {
    companion object {
        val modern = EngineDsl(
            listOf(
                StageDsl.FilterMod, StageDsl.Crush, StageDsl.Coarse, StageDsl.Distort,
                StageDsl.Filter(), StageDsl.Tremolo, StageDsl.Phaser, StageDsl.Vca(),
            )
        )
        val pedal = EngineDsl(
            listOf(
                StageDsl.FilterMod, StageDsl.Vca(), StageDsl.Crush, StageDsl.Coarse,
                StageDsl.Distort, StageDsl.Filter(), StageDsl.Tremolo, StageDsl.Phaser,
            )
        )
    }
}

@Serializable
sealed interface StageDsl {
    @Serializable
    @SerialName("filterMod")
    data object FilterMod : StageDsl
    @Serializable
    @SerialName("crush")
    data object Crush : StageDsl
    @Serializable
    @SerialName("coarse")
    data object Coarse : StageDsl
    @Serializable
    @SerialName("distort")
    data object Distort : StageDsl
    @Serializable
    @SerialName("tremolo")
    data object Tremolo : StageDsl
    @Serializable
    @SerialName("phaser")
    data object Phaser : StageDsl

    @Serializable
    @SerialName("filter")
    data class Filter(
        val cutoffOffsetPerAnalog: Double = 0.003,  // FILTER_CUTOFF_OFFSET_PER_ANALOG
        val drivePerAnalog: Double = 0.5,           // FILTER_DRIVE_PER_ANALOG
        val driftRelToOsc: Double = 5.0,            // FILTER_DRIFT_RELATIVE_TO_OSC
    ) : StageDsl

    @Serializable
    @SerialName("vca")
    data class Vca(
        val expK: Double = 3.0,                     // ADSR_EXP_K
        val declickSeconds: Double = 0.001,         // ENV_DECLICK_SECONDS
    ) : StageDsl
}
```

Defaults equal today's constants → byte-for-byte compatible. The `modern`/`pedal` lists reproduce the exact
order in `FilterPipelineBuilder.kt`.

### 1.2 Data-driven pipeline — `audio_be/.../voices/strip/filter/FilterPipelineBuilder.kt`

- Delete `buildModernFilterPipeline` / `buildPedalFilterPipeline`. Replace with one builder iterating stages:

```kotlin
fun buildFilterPipeline(
    engine: EngineDsl, modulators, startFrame, gateEndFrame,
    crush, coarse, mainFilter, envelope, distort, tremolo, phaser, sampleRate,
): List<BlockRenderer> = buildList {
    for (stage in engine.stages) when (stage) {
        StageDsl.FilterMod -> if (modulators.isNotEmpty()) add(FilterModRenderer(modulators, startFrame, gateEndFrame))
        StageDsl.Crush -> if (crush.amount > 0.0) add(CrushRenderer(crush.amount, crush.oversample))
        StageDsl.Coarse -> if (coarse.amount > 1.0) add(CoarseRenderer(coarse.amount, coarse.oversample))
        StageDsl.Distort -> if (distort.amount > 0.0) add(
            DistortionRenderer(
                distort.amount,
                distort.shape,
                distort.oversample
            )
        )
        is StageDsl.Filter -> add(AudioFilterRenderer.of(mainFilter))
        StageDsl.Tremolo -> if (tremolo.depth > 0.0) add(TremoloRenderer(tremolo.rate, tremolo.depth, sampleRate))
        StageDsl.Phaser -> if (phaser.depth > 0.0) add(StripPhaserRenderer(/* … */))
        is StageDsl.Vca -> add(
            EnvelopeRenderer(
                envelope,
                startFrame,
                gateEndFrame,
                expK = stage.expK,
                declickSeconds = stage.declickSeconds
            )
        )
    }
}
```

- Per-stage *amounts* (distort/crush/cutoff/adsr times) still come from `VoiceData` — a slot only renders when
  its amount is active, exactly as today.
- Validation: none that throws. Optional: `log` a warning if `FilterMod` is not first (control-rate). No VCA =
  a drone; allowed.

### 1.3 Thread character constants — `audio_be`

- **VCA:** `EnvelopeRenderer` (`voices/strip/filter/EnvelopeRenderer.kt`) gains ctor params
  `expK: Double = 3.0`, `declickSeconds: Double = 0.001`. In `render`, compute the exp-norm and declick coeff
  from them (per block, not per sample). In `AdsrCurveMath.kt` add parameterized helpers
  `adsrExpShape(x, k, norm)` and `expNormFor(k) = 1/(exp(k)-1)`; keep the existing `ADSR_EXP_K` /
  `ENV_DECLICK_SECONDS` as the `Vca` defaults. (Other envelope evaluators — `EnvelopeCalc`,
  `IgnitorEnvelopes` — keep the const default for now; they're different envelopes.)
- **Filter humanization:** the `Filter` stage's three fields feed filter construction. In `VoiceFactory`
  (filter build, ~`87-97`) resolve the engine first, find the `Filter` stage, and pass its fields into
  `FilterDef.toFilter(analog, humanization)` / the SVF + drift constructors, replacing the const reads in
  `filters/FilterHumanizationCoeffs.kt`. All per-voice/construction-time — no per-sample cost. Plain `Double`,
  no boxing.

### 1.4 Registration & delivery (mirror Ignitors)

- `audio_bridge/.../infra/KlangCommLink.kt`: `data class RegisterEngine(val name: String, val dsl: EngineDsl) : Cmd`.
- `audio_be/.../engines/EngineRegistry.kt` (new, copy `ignitor/IgnitorRegistry.kt`): `register(name, dsl)`,
  `get(name): EngineDsl`. Pre-register `"modern"`/`"pedal"`. Handle `RegisterEngine` where `RegisterIgnitor`
  is handled.
- `VoiceFactory` (~`481`): replace `AudioEngine.fromName(data.engine)` →
  `engineRegistry.get(data.engine ?: "modern")`, pass the `EngineDsl` into `buildFilterPipeline`.
- `klang/.../EngineRegistry.kt` (new, copy the player-side `IgnitorRegistry.kt`):
  `registerOrLookup(dsl): String` with structural dedup + first-sighting `RegisterEngine` send. Sprudel
  `.engine(dsl)` resolves a DSL value → registered name → `VoiceData.engine`; `.engine("modern")` still works.
- **Worklet codec:** `EngineDsl`/`StageDsl` get `@Serializable @SerialName`; extend `audio-wire-codec-ksp`
  exactly as `IgnitorDsl` is handled for the `RegisterEngine` Cmd (`WorkletContract.kt`). Bump
  `WIRE_SCHEMA_HASH`.

### 1.5 KlangScript surface — `klangscript/.../stdlib/`

- `KlangScriptEngine.kt`: `@KlangScript.Object("Engine")` → `modern(): EngineDsl`, `pedal(): EngineDsl`,
  `of(vararg stages: StageDsl): EngineDsl`.
- `KlangScriptStage.kt`: `@KlangScript.Object("Stage")` → `filterMod()`, `crush()`, `coarse()`, `distort()`,
  `tremolo()`, `phaser()`, `filter(): FilterStageDsl`, `vca(): VcaStageDsl`.
- `@KlangScript.TypeExtensions(VcaStageDsl::class)` → `expK(x): VcaStageDsl`, `declick(x): VcaStageDsl`;
  `@TypeExtensions(FilterStageDsl::class)` → `drift(x)`, `cutoffOffset(x)`, `driveScale(x)`. (Template:
  `KlangScriptOscExtensions.kt`.)
- Sugar on `EngineDsl`: `expK()/declick()` forwarding to the single `Vca` stage.
- `.engine()` in sprudel accepts `EngineDsl` (resolve→register→name) or `String`.

### 1.6 Tests

- Extend `AudioEngineSpec` + `VoiceFactoryFilterOrderSpec`: assert `EngineDsl.modern`/`pedal` produce the
  **identical renderer sequence** the hardcoded builders did (regression net).
- New `EngineDslWireSpec`: round-trip `EngineDsl` through the wire codec (encode→decode equality).
- New `EnginePipelineSpec`: `Engine.of(...)` with omitted stages → expected renderer list; `Vca.expK`/
  `declick` override changes the rendered gain (reuse `EnvelopeDeclickSpec` DC-carrier slew measurement).

### 1.7 Done when

`note("…").engine("modern")` unchanged; `Engine.modern().expK(2.5).declick(0.0008)` and
`Engine.of(Stage.vca().declick(0.5), Stage.filter())` work and re-register live; all specs green.

---

## Phase 2 — Typed oscillator/wrapper subtypes, local defaults ("Step 1")

Distributes the oscillator constants onto the oscillators; extends typed subtypes to configurable wrappers.
**Fields default to today's const** (decoupled, behavior-identical).

### 2.1 Subtypes — `audio_bridge/IgnitorDsl.kt`

- Promote oscillators-with-tunable-constants to narrow subtypes, e.g.:

```kotlin
@Serializable
@SerialName("supersaw")
data class SuperSaw(
    val freq: IgnitorDsl = Freq,
    val voices: IgnitorDsl = Constant(8.0),
    val freqSpread: IgnitorDsl = Constant(0.2),
    val detunePower: IgnitorDsl = Constant(1.2),  // SUPERSAW_DETUNE_POWER
    val sideAtten: IgnitorDsl = Constant(0.1),    // SUPERSAW_SIDE_ATTEN
    val gainJitter: IgnitorDsl = Constant(0.1),   // SUPERSAW_GAIN_JITTER
) : IgnitorDsl { /* collectParams over all fields */ }
```

Likewise the saw/ramp/pulse family (`resetSamples`, `shapeMax`) and analog-drift carriers (the ~5 *musical*
drift params, not derived math constants).

- Configurable **wrappers** become subtypes too: `IgnitorDsl.Adsr` exposes `declickSeconds`, `expK`,
  `attackCurve`/`decayCurve`/`releaseCurve`; `Lowpass`/`Highpass`/`Bandpass` expose their feel knobs. (The
  ignitor `Adsr` gains a declick one-pole — the same as the VCA; it has none today.)

### 2.2 Thread fields → runtime — `audio_be`

- `ignitor/IgnitorDslRuntime.buildIgnitor`: pass the new fields into the runtime factories
  (`Ignitors.superSaw(…)`, `AnalogDrift`/`AnalogDriftCoeffs`, saw/pulse generators, the ignitor `AdsrIgnitor`),
  replacing the `const val` reads in `ignitor/OscillatorTuning.kt` / `ignitor/AnalogDriftCoeffs.kt`. All
  construction-time, no per-sample cost.

### 2.3 KlangScript — `klangscript/.../stdlib/`

- Change `Osc.supersaw()` return type → `SupersawIgnitorDsl` (etc.).
- `@KlangScript.TypeExtensions(SupersawIgnitorDsl::class)` → `detune(x)`, `sideAtten(x)`, `gainJitter(x)`,
  `drift(fast, slow, depth)`, each `copy(field=…)` returning the subtype. Repeat per family.
  `@TypeExtensions(AdsrIgnitorDsl::class)` → `declickMs(x)`, `expK(x)`, `attackCurve(name)`.
- Base wrapper methods (`.lowpass()`, `.adsr()`) keep working via supertype lookup; `.adsr()` returns
  `AdsrIgnitorDsl` so config-first chaining works.
- Build-time **warn** (not throw) when a sound sets params it doesn't expose, via `IgnitorDsl.collectParams()`.

### 2.4 Tests

- `SupersawIgnitorDsl.detune(...)` changes the rendered detune; calling a subtype method on a base value
  errors; serialization round-trip; `.adsr().declickMs()` declicks the ignitor-internal envelope.

---

## Phase 3 — `Osc.EngineDefault` + engine tuning profile ("Step 2")

Turns engines into complete identities (NES/C64/contemporary): oscillator params resolve their default from
the active engine.

### 3.1 The sentinel — `audio_bridge`

```kotlin
@Serializable
@SerialName("engineDefault")
data object EngineDefault : IgnitorDsl {
    override fun collectParams(out: MutableList<Param>) {}
}
```

Exposed as `Osc.EngineDefault`. Sibling of `Freq`/`Constant`/`Param`.

### 3.2 Flip defaults

- Oscillator/wrapper field defaults `Constant(today)` → `EngineDefault` (e.g. `SuperSaw.detunePower = EngineDefault`).

### 3.3 Engine tuning profile — `audio_bridge`

```kotlin
@Serializable
data class EngineTuning(
    val detunePower: Double = 1.2,   // == SUPERSAW_DETUNE_POWER (the fallback IS the default)
    val sideAtten: Double = 0.1,
    val gainJitter: Double = 0.1,
    val sawResetSamples: Double = 2.0,
    val driftFastTauSec: Double = 0.05,
    val driftSlowTauSec: Double = 10.0,
    // … the engine-resolvable oscillator settings
)
// EngineDsl gains: val tuning: EngineTuning = EngineTuning()
```

`modern`/`pedal` use `EngineTuning()` (all defaults); a `"c64"` engine overrides specific values.

### 3.4 Resolution — `audio_be`

- Thread the active engine's `EngineTuning` into the `buildIgnitor` build context (it's available in
  `VoiceFactory` alongside the resolved `EngineDsl`).
- Per-field resolver: when a field is `EngineDefault`, read `engineTuning.<thatField>`; otherwise use the
  explicit value. Cascade = **explicit instance value → engine profile → field default** (the `EngineTuning`
  default == today's const). Construction-time only.

### 3.5 KlangScript

- `Osc.EngineDefault` property; `Engine.modern().tune(detunePower = 1.5, …)` (or a `Tuning.of(...)` builder)
  to author profiles. Wire `EngineTuning` through the codec (rides on the registered `EngineDsl`).

### 3.6 Tests

- Two engines with different `EngineTuning` render different detune for the same `Osc.supersaw()` (no explicit
  `.detune()`); an explicit `.detune()` ignores the engine; `modern` reproduces today's sound exactly.

---

## Phase 4 — Katalyzer (future, out of scope)

Per-orbit effect constants (reverb/delay/phaser/comp/duck) belong in the planned Katalyzer (per-orbit
counterpart to Ignitors), not EngineDsl. Marked only to fix the boundary.

---

## Cross-cutting

- **Performance / no boxing:** every migrated constant is read at voice/block construction; the two per-sample
  ones (`expK` via `exp`, declick coeff) recompute one coefficient at the block boundary — the existing
  pattern. All config fields plain `Double`/`Int`.
- **Backward compatibility:** all defaults == today's constants; `modern`/`pedal` pre-registered;
  `VoiceData.engine: String` semantics unchanged; Phase 3 changes nothing until an engine overrides.
- **Scope:** EngineDsl covers the tonal/filter pipeline (`buildFilterPipeline`). The pitch pipeline
  (vibrato/accel/pitchEnv/FM) stays per-note, untouched.
- **Guards:** `AudioEngineSpec` + `VoiceFactoryFilterOrderSpec` are the regression net proving the data-driven
  builder reproduces the hardcoded one.

## Verification (end to end)

```
./gradlew :audio_bridge:jvmTest :audio_be:jvmTest :klangscript:jvmTest
```

Then the by-ear loop: in the live editor, `note("…").engine(Engine.modern().expK(2.5))`, re-register, confirm
the timbre changes with no recompile; offline WAV A/B via the recording pipeline for a low note.

## Effort (rough)

- Phase 1: medium — touches audio_bridge + audio_be + klang + klangscript + codec, but mostly mirrors the
  Ignitor pattern; the pipeline refactor is mechanical with a strong spec net.
- Phase 2: medium — repetitive subtype/`@TypeExtensions` work per oscillator family.
- Phase 3: small-medium — one sentinel + one profile type + a resolver, layered on Phase 2.

## Critical files

`audio_bridge`: `EngineDsl.kt` (new), `IgnitorDsl.kt`, `infra/KlangCommLink.kt`, `VoiceData.kt` ·
`audio_be`: `voices/strip/filter/FilterPipelineBuilder.kt`, `voices/VoiceFactory.kt`,
`voices/strip/filter/EnvelopeRenderer.kt`, `AdsrCurveMath.kt`, `filters/FilterHumanizationCoeffs.kt`,
`ignitor/IgnitorDslRuntime.kt`, `ignitor/OscillatorTuning.kt`, `ignitor/AnalogDriftCoeffs.kt`,
`ignitor/Ignitors.kt`, `engines/EngineRegistry.kt` (new), `WorkletContract.kt` ·
`klang`: `EngineRegistry.kt` (new, template `IgnitorRegistry.kt`) ·
`klangscript`: `stdlib/KlangScriptEngine.kt` + `KlangScriptStage.kt` (new) + `@TypeExtensions` classes
(templates `KlangScriptOsc.kt`, `KlangScriptOscExtensions.kt`) · `audio-wire-codec-ksp`.
