# Configurable filter envelopes (filter curves, and beyond)

## Context

Decided 2026-09-07 (maintainer, in `docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`): when the engine grows the fields, the doors
are `lpCurves`, `hpCurves`, `bpCurves` (not `lpadsrCurves`), each an object with the setter only, like
`adsrCurves`; the singular curve door does not come back.

The filter envelope is only *partly* configurable today: you can set its **times** (`lpf(attack, decay,
sustain, release)`), **depth** (`lpf(env)`), **Q** (`lpf(q)`) and base **cutoff** (`lpf(freq)`) — but **not its
curve shapes**. Unlike the amp ADSR (`adsrCurves("a", "d", "r")`), there is no filter curve door, and
`FilterEnvDef` carries no curve fields, so the
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
- **Backend:** `audio_be/voices/VoiceFactory.kt:~426-433` builds the filter modulator's `Voice.Envelope`
  from `envData.resolve()` **without passing curves** → falls back to `Voice.Envelope` defaults
  (`Voice.kt:157-159`). The renderer already supports per-curve filter envelopes
  (`EnvelopeCalc.calculateControlRateEnvelope` reads `env.decayCurve` / `env.releaseCurve`) — it's just never
  fed anything but the default.
- **Sprudel:** `.lpadsr` / `.lpe` / `.lpq` / `.lpf` set times/depth/Q/cutoff (`lang_filters.kt`,
  `lang_filters.kt`, the `lpf` compound since 2026-09-07); `FilterEnvDef` is assembled in `SprudelVoiceData.kt:~772-782` from the `Svd*`
  filter fields. **No curve fields, no curve DSL.**

The amp path to mirror: `.adsrCurves()` → `SprudelVoiceData` curve fields → `AdsrDef.Std` curves → resolve →
`Voice.Envelope.of(resolvedAdsr)` (`VoiceFactory.kt:479`, passes the curves through).

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

- `VoiceFactory.kt:~428`: pass `resolved.attackCurve / decayCurve / releaseCurve` into the filter
  `Voice.Envelope(...)` (today it relies on the ctor defaults). One small change; `EnvelopeCalc` already
  honours them.
- **Ignitor-internal filters** (the `IgnitorDsl` `.lowpass()` family / Phase-2 configurable wrappers,
  [[project_engine_dsl]]) are a *separate* filter path — if/when they gain envelopes, keep their curve config
  consistent with this one (ideally share the `FilterEnvDef` shape). Note here so the surfaces don't diverge.

### 3. Sprudel DSL

- `SprudelVoiceData.kt`: add `lpAttackCurve / lpDecayCurve / lpReleaseCurve` (nullable) to the `Svd*` filter
  group; thread into the `FilterEnvDef` construction (`:~772-782`).
- New DSL object **`lpCurves(attack, decay, release)`** (mirror of `adsrCurves`, setter only, parse `AdsrCurve` per stage) in
  `lang_filters.kt` next to the `lpf` compound (2026-09-07 naming; `lang_effects_addons.kt` no longer exists); plus mapper/string-receiver overloads per the DSL
  conventions. Follow [[feedback_klangscript_no_named_params]] / `/sprudel-dev-knowhow` for the function shape.
- KlangScript surface only if the amp `adsrCurves` is exposed there (match it).

## Decisions / open questions (resolve before building)

- **Name:** `lpCurves` (decided 2026-09-07; `lpadsrCurves` was the earlier candidate, `lpadsr` itself is gone).
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
- `sprudel`: `.lpCurves("lin", "exp", "lin")` sets the three `FilterEnvDef` curves (no colon form exists); round-trips through
  `toVoiceData`.
- `./gradlew :audio_bridge:jvmTest :audio_be:jvmTest :sprudel:jvmTest`.
- By-ear: an exp vs linear filter *release* should be audibly different on a plucky `lpf(attack, decay, sustain, release)` patch.

## Critical files

| Layer     | Files                                                                                                                     |
|-----------|---------------------------------------------------------------------------------------------------------------------------|
| Wire      | `audio_bridge/.../FilterEnvDef.kt`, `FilterDef.kt`; codec ([[project_worklet_serialization]])                             |
| Backend   | `audio_be/.../voices/VoiceFactory.kt` (~428), `voices/Voice.kt`, `voices/strip/EnvelopeCalc.kt`                           |
| DSL       | `sprudel/.../SprudelVoiceData.kt` (~772-782 + `Svd*` group), `lang/lang_filters.kt` |
| Templates | amp curves: `.adsrCurves` → `AdsrDef` → `Voice.Envelope.of` (`VoiceFactory.kt:479`)                                       |
