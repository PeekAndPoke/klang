# Phase 2 — typed oscillator subtype DSLs (supersaw first)

## Context

Phase 1 (PipelineDsl) made the voice *pipeline* configurable. Phase 2 does the same for **oscillators**:
distribute the ~25 baked-in oscillator constants onto the oscillators as **typed subtype DSLs** — fluent
builders where every setting is a chained config method, mirroring the proven `Stage.vca().expK()` pattern.

**One oscillator at a time.** SuperSaw is first (most constants, most-used, best understood); it establishes
the template every other `super*` / osc copies. Each oscillator ships with a **dual-language equivalence
test**: the same expression in Kotlin and in KlangScript must deserialize to the **same** `data class` object
(`shouldBe`, structural equality) — one assertion that validates the whole binding chain.

**Decisions (with the user):**

- Fully fluent, **zero-param** `Osc.supersaw()`; freq defaults to the note pitch (`Osc.freq()`), set via
  `.freq(osc)`. Everything (voices/freqSpread/analog + the character constants) is a chained method.
- **Constant-faithful method names** (`.detunePower()`, `.sideAtten()`, `.gainJitter()`, `.centerJitter()`,
  `.voices()`, `.freqSpread()`, `.analog()`, `.freq()`).
- Reuse the existing `IgnitorDsl.SuperSaw` data class **as** the typed subtype (it already is one) — no new class.
- Character fields are plain `Double` (compile-time scalars, not audio-rate `Param`s — they feed
  `computeVoiceGains`/`computeDetunes` once per voice-count/freq change). Defaults == today's constants →
  backward-compatible; the `@WireFormat` schema hash bumps.

## What already exists (from exploration)

- `IgnitorDsl.SuperSaw` (`audio_bridge/IgnitorDsl.kt`) is a `@WireName("supersaw") data class` with
  `freq/voices/freqSpread/analog` — no character fields yet.
- The engine **already accepts** `sideAtten/gainJitter/detunePower` as `Double` ctor params
  (`DetunedStackIgnitor`); they're just **hardcoded** in `Ignitors.superSawRaw` from `SUPERSAW_*`
  (`OscillatorTuning.kt`) instead of threaded from the DSL. `centerJitterScale` is read **globally** in
  `computeVoiceGains` → promote it to a ctor param like the others.
- The DSL→factory seam is the single missing link: `IgnitorDslRuntime.kt:~208` (`is SuperSaw ->
  Ignitors.superSaw(...)`).
- Config-method precedent: `KlangScriptOscExtensions.analog()` does a `when(self){ is SuperSaw -> copy(...) }`
  cascade — but Phase 2 uses **typed** subtype extensions instead (narrow return → chainable, intellisense).
- Dual-language test recipe (confirmed): `klangScript()` → `execute("""import * from "stdlib"""")` →
  `execute(code).toObjectOrNull<IgnitorDsl>()` → `shouldBe` the Kotlin-built object (all nodes are data classes).

## Changes (supersaw)

1. **`audio_bridge/IgnitorDsl.kt`** — add to `SuperSaw`: `detunePower: Double = 1.2`, `sideAtten = 0.1`,
   `gainJitter = 0.15`, `centerJitterScale = 0.4` (defaults == `SUPERSAW_*`; add a "keep in sync" comment, like
   `FilterHumanizationCoeffs` ↔ `PipelineDsl.Filter`). `collectParams` unchanged (Doubles, not Params).
2. **`audio_be`** — widen `Ignitors.superSaw`/`superSawRaw` to take the 4 values (default = the `SUPERSAW_*`
   consts, so non-DSL callers/tests are unaffected); add `centerJitterScale` as a `DetunedStackIgnitor` ctor
   field + use it in `computeVoiceGains` (replacing the global const read). `IgnitorDslRuntime.kt:~208` passes
   `dsl.detunePower/sideAtten/gainJitter/centerJitterScale`. `SawStackIgnitor` already passes the others.
3. **`klangscript/stdlib/KlangScriptOsc.kt`** — `supersaw()` becomes **zero-param** returning the narrow
   `IgnitorDsl.SuperSaw` (was `IgnitorDsl` with freq/voices/freqSpread params).
4. **`klangscript/stdlib/KlangScriptSuperSawExtensions.kt`** (new) —
   `@KlangScript.TypeExtensions(IgnitorDsl.SuperSaw::class)` with `.freq/.voices/.freqSpread/.analog`
   (`IgnitorDslLike` → `toIgnitorDsl()`) and `.detunePower/.sideAtten/.gainJitter/.centerJitter` (`Double`),
   each `self.copy(...)` returning `IgnitorDsl.SuperSaw`. Mirror `KlangScriptVcaStageExtensions`. Watch the KSP
   import gotcha (nested subtype must be public so `qualifiedName` resolves — already true).

## Verification

- **Dual-language spec** `klangscript/.../stdlib/KlangScriptSuperSawSpec.kt` (new) — for each method, assert
  `runKs<IgnitorDsl>("Osc.supersaw().detunePower(1.5)") shouldBe Osc.supersaw().detunePower(1.5)` (Kotlin
  builder == KlangScript). Plus a full-chain case and the default (`Osc.supersaw()` == `IgnitorDsl.SuperSaw()`).
- **Behavior-preserving**: defaults == old constants, so a plain `supersaw` renders byte-identical — pin with an
  audio render/`AnalogSawSpec`-style check that the default DSL still feeds the same `sideAtten/gainJitter/...`.
- Wire round-trip: `SuperSaw` with custom character survives encode→decode (the codec specs); bump
  `WIRE_SCHEMA_HASH`.
- `./gradlew :audio_bridge:jvmTest :audio_be:jvmTest :klangscript:jvmTest :sprudel:jvmTest` (+ JS wire tests).
- By-ear: `Osc.supersaw().detunePower(...).gainJitter(...)` audibly changes the unison character.

## Then (other oscillators)

Repeat the template per family: `superramp/supersquare/supertri/supersine` (same unison engine + own consts),
then `saw`/`pulse` shape (`resetSamples`/`shapeMax`), then the analog-drift musical constants
(`ANALOG_*_TAU_SEC`, `ANALOG_*_PEAK_CENTS`, mean-reversion — deeper threading through `AnalogDrift`). Each its
own dual-language spec. The `Osc`/`Ignitor` two-word naming debt ([[osc_ignitor_misnamed]]) stays deferred —
not blocking, but every osc method we add here will need carrying through that eventual rename.
