# Configurable filter envelopes (lpadsr curves, and beyond)

## Context

The filter envelope (`lpadsr`) is only *partly* configurable today: you can set its **times** (`.lpadsr`),
**depth** (`.lpe`), **Q** (`.lpq`) and base **cutoff** (`.lpf`) — but **not its curve shapes**. Unlike the amp
ADSR (`.adsrCurves("a:d:r")`), there is no `lpadsrCurves`, and `FilterEnvDef` carries no curve fields, so the
filter sweep is locked to whatever `Voice.Envelope`'s defaults are.

As of 2026-06-19 those defaults are **Exponential / Exponential / Exponential** for all envelopes (amp, filter,
FM) — see `AdsrDef.kt` (`defaultSynth` + resolve fallbacks) and `Voice.kt` (`Voice.Envelope` ctor defaults).
This task makes the filter envelope's curves an **override-only** setting on top of that default, mirroring how
`.adsrCurves()` works for the amp. (Primary missing axis = curves; times/depth/Q/cutoff are already exposed.)

The work spans three layers — **wire contract (FilterDef), backend rendering (Ignitors/audio_be), and the
Sprudel DSL** — plus a note on ignitor-internal filters.

## Current shape (where the curves are lost)

- **Wire:** `audio_bridge/FilterEnvDef.kt` — `FilterEnvDef(attack, decay, sustain, release, depth)` and its
  `Resolved` — **no curve fields**. `FilterDef.LowPass.envelope: FilterEnvDef?` is the only filter envelope.
- **Backend:** `audio_be/voices/VoiceFactory.kt:~414-420` builds the filter modulator's `Voice.Envelope`
  from `envData.resolve()` **without passing curves** → falls back to `Voice.Envelope` defaults
  (`Voice.kt:157-159`). The renderer already supports per-curve filter envelopes
  (`EnvelopeCalc.calculateControlRateEnvelope` reads `env.decayCurve` / `env.releaseCurve`) — it's just never
  fed anything but the default.
- **Sprudel:** `.lpadsr` / `.lpe` / `.lpq` / `.lpf` set times/depth/Q/cutoff (`lang_filters.kt`,
  `lang_effects_addons.kt`); `FilterEnvDef` is assembled in `SprudelVoiceData.kt:~755-776` from the `Svd*`
  filter fields. **No curve fields, no curve DSL.**

The amp path to mirror: `.adsrCurves()` → `SprudelVoiceData` curve fields → `AdsrDef.Std` curves → resolve →
`Voice.Envelope.of(resolvedAdsr)` (`VoiceFactory.kt:464`, passes the curves through).

## Plan

### 1. Wire contract — `audio_bridge` (`FilterDef.kt` / `FilterEnvDef.kt`)

- Add `attackCurve / decayCurve / releaseCurve: AdsrCurve? = null` to `FilterEnvDef` **and** to
  `FilterEnvDef.Resolved` (non-null).
- `resolve(...)` fills nulls from the engine default (**Exponential**, matching `AdsrDef`'s new default — keep
  the two in sync, ideally referencing one shared default).
- `AdsrCurve` is **already in the codec graph** (used by `AdsrDef` under the `@WireFormat` `Cmd`/`VoiceData`),
  so adding the fields to `FilterEnvDef` needs no new codec support — but **bump `WIRE_SCHEMA_HASH`** /
  re-verify the round-trip ([[project_worklet_serialization]]).

### 2. Backend rendering — `audio_be` ("Ignitors")

- `VoiceFactory.kt:~415`: pass `resolved.attackCurve / decayCurve / releaseCurve` into the filter
  `Voice.Envelope(...)` (today it relies on the ctor defaults). One small change; `EnvelopeCalc` already
  honours them.
- **Ignitor-internal filters** (the `IgnitorDsl` `.lowpass()` family / Phase-2 configurable wrappers,
  [[project_engine_dsl]]) are a *separate* filter path — if/when they gain envelopes, keep their curve config
  consistent with this one (ideally share the `FilterEnvDef` shape). Note here so the surfaces don't diverge.

### 3. Sprudel DSL

- `SprudelVoiceData.kt`: add `lpAttackCurve / lpDecayCurve / lpReleaseCurve` (nullable) to the `Svd*` filter
  group; thread into the `FilterEnvDef` construction (`:~755-776`).
- New DSL function **`lpadsrCurves("a:d:r")`** (mirror of `adsrCurves`, parse `AdsrCurve` per stage) in
  `lang_filters.kt` (or `lang_effects_addons.kt`); plus mapper/string-receiver overloads per the DSL
  conventions. Follow [[feedback_klangscript_no_named_params]] / `/sprudel-dev-knowhow` for the function shape.
- KlangScript surface only if the amp `adsrCurves` is exposed there (match it).

## Decisions / open questions (resolve before building)

- **Name:** `lpadsrCurves` (consistent with `adsrCurves` + `lpadsr`) — recommended — vs `lpcurve` / `lpenvCurves`.
- **Default:** Exponential (just set globally); this feature is override-only, no behaviour change until used.
- **Scope:** LPF env only (it's the only filter envelope that exists). **No `hpadsr`** today — adding a
  high-pass/band-pass envelope is a separate, larger feature; list as out-of-scope.
- **FM env:** unrelated to this task, but note it shares `Voice.Envelope` defaults (see the 2026-06-19 change).

## Verification

- `audio_bridge`: `FilterEnvDef` curve round-trip through the wire codec (encode→decode equality); resolve
  fills Exponential when null.
- `audio_be`: a filter env rendered with e.g. `Linear` vs `Exponential` release produces a measurably
  different cutoff trajectory (reuse the `EnvelopeDeclickSpec` / filter-env render harness); default (no curve
  set) stays Exponential.
- `sprudel`: `.lpadsrCurves("lin:exp:lin")` sets the three `FilterEnvDef` curves; round-trips through
  `toVoiceData`.
- `./gradlew :audio_bridge:jvmTest :audio_be:jvmTest :sprudel:jvmTest`.
- By-ear: an exp vs linear filter *release* should be audibly different on a plucky `lpadsr` patch.

## Critical files

| Layer     | Files                                                                                                                     |
|-----------|---------------------------------------------------------------------------------------------------------------------------|
| Wire      | `audio_bridge/.../FilterEnvDef.kt`, `FilterDef.kt`; codec ([[project_worklet_serialization]])                             |
| Backend   | `audio_be/.../voices/VoiceFactory.kt` (~415), `voices/Voice.kt`, `voices/strip/EnvelopeCalc.kt`                           |
| DSL       | `sprudel/.../SprudelVoiceData.kt` (~755-776 + `Svd*` group), `lang/lang_filters.kt`, `lang/addons/lang_effects_addons.kt` |
| Templates | amp curves: `.adsrCurves` → `AdsrDef` → `Voice.Envelope.of` (`VoiceFactory.kt:464`)                                       |
