# Phase 2 — typed oscillator subtype DSLs (supersaw first)

> **ARCHIVED 2026-06-30 — all oscillator SOURCES done.** Every shape/character-bearing oscillator is now a typed
> `IgnitorDsl` subtype with chained config methods + dual-language specs: the super-* unison family
> (`supersaw/ramp/sine/square/tri`) and the single shape oscillators (`saw`/`ramp`/`square`(`Pulze`)/`triangle`).
> The static supertype inferrer + `WaveIgnitor.shapeMax` landed too. Remaining Phase-2 items (configurable
> *wrappers* `Adsr`/`Lowpass/Highpass/Bandpass`, and analog-drift carriers) + Phase 3 are tracked in the master
> plan `docs/tasks/engine-dsl.md`. Uncommitted on branch `dsl-enhancements`. This doc is the detailed osc working
> log; the master plan supersedes it.

## Status — supersaw DONE (2026-06-30)

Supersaw is the first typed oscillator subtype and the template for the rest. Shipped:

- `Osc.supersaw()` narrows its return to `IgnitorDsl.SuperSaw` with chained config methods
  (`.freq/.voices/.spread/.analog` + character `.spreadPower/.sideAtten/.gainJitter/.centerJitter`),
  defaults synced to the `SUPERSAW_*` engine constants (guarded by `SuperSawDefaultsSyncSpec`).
- **Static inferrer now walks supertypes** (the blocker the narrowing exposed): base `IgnitorDsl`
  methods (`.lowpass()`/`.adsr()`/…) resolve on a `SuperSaw` receiver again. Implemented as:
  - `KlangType.supertypes: List<KlangType>` (new field, default empty).
  - KSP `generateKlangType(…, includeSupertypes = true)` emits `getAllSuperTypes()` minus `kotlin.*`
    on **return** + **property** types (`KlangScriptProcessor.supertypeListExpr`). Base-type returns
    (only ancestor = `Any`) get nothing → lean (~40 entries, the narrowed subtypes).
  - `KlangDocsRegistry.receiverChain(query)` = query + its supertypes (most-specific first); routed
    through `getCallable` / `getVariantsForReceiver` / `getSymbolWithReceiver`. Hand-built query types
    carry no supertypes → existing behavior unchanged. Mirrors runtime `Environment.getAllRegisteredSupertypes`.
  - The 3 previously-failing inference tests fixed (`StdlibDocsInferenceTest` test 1 expectation updated
    `supersaw()`→`"SuperSaw"`; the 2 chain tests pass via the supertype walk).
- **Dual-language equivalence spec** `KlangScriptSuperSawSpec` (commonTest): each fluent method, the full
  chain, and the default — KlangScript source `shouldBe` the Kotlin builder + `.copy()`. Template to copy.
- Suite green: klangscript JVM+JS, audio_bridge/audio_be JVM, root song smoke.

**Whole super-* unison family DONE (2026-06-30, uncommitted).** Replicated the supersaw template to
`superramp/supersine/supersquare/supertri`: character fields (`spreadPower/sideAtten/gainJitter/centerJitterScale`,
literal defaults guarded by the renamed `SuperOscDefaultsSyncSpec`) + `Slots.*` defaults (the "#5 Slots
unification" — they now respond to `unison()`/`spread()` like supersaw) on the `IgnitorDsl.Super*` data classes;
`Ignitors.super*` factories widened to accept the 4 character params (defaults = `SUPER{FAMILY}_*` consts);
`IgnitorDslRuntime` branches thread them; `KlangScriptOsc.super*` narrowed to `IgnitorDsl.Super*` + `Slots`
defaults; new `KlangScriptSuper{Ramp,Sine,Square,Tri}Extensions` + dual-language
`KlangScriptSuper{Ramp,Sine,Square,Tri}Spec`.
Behavior-preserving (defaults unchanged). Green: audio_bridge JVM+JS-codec, audio_be JVM, klangscript JVM, root smoke.

**Next per the plan below:** the `saw`/`pulse` shape constants, then the analog-drift constants. Each gets its own
dual-language spec.

## Open notes (decided, not yet applied)

- **#5 Slots unification (user, 2026-06-30):** unify the param-default Slots across the whole super-* family
  *now*; keep the set of default Slots **as minimal as possible**, with **no overlaps or ambiguities** (one
  Slot per distinct concept). Apply when we resolve the naming.

- **Align sprudel DSL fn names ↔ Ignitor param/Slot names (user, 2026-06-30; AFTER main task).** The sprudel
  pattern functions that write `oscParams` should match the Ignitor Slot keys. Audit result (writers in
  `sprudel/.../lang/lang_dynamics.kt` via `putOscParam`):
  - `unison()` → writes key `"voices"` — **MISALIGNED. Add a `voices()` alias** (keep `unison()` too; both →
    `oscParams["voices"]`). The one real gap.
  - `spread()`→`"spread"` ✅, `panSpread()`→`"panSpread"` ✅ (exists — created in the detune→spread rename;
    no-op while oscs are mono), `density()`→`"density"` ✅, `analog()`→`"analog"` ✅, `duty()`→`"duty"` ✅,
    `warmth()`→`"warmth"` ✅ (warmth isn't a Slot but the name matches).
  - Slots `decay / brightness / pickPosition / stiffness / rate` have **no dedicated sprudel fn** (set only via
    the positional `snd("superpluck", "voices:spread:decay:brightness:pickPosition:stiffness")` string). Adding
    dedicated `.decay()/.brightness()/…` is optional/future, not an alignment fix.
  - **Then: docs sweep** — every doc/KDoc that mentions these params + their DSL fns. Known stale: the
    `snd(...)` param-string KDoc in `lang_snd_addons.kt` still reads `"voices:freqSpread:…"` (→ `"voices:spread:…"`);
    the `spread()`/`unison()` KDoc; and any tutorials referencing `detune`/`freqSpread`/`spread`.

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
