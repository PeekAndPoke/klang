# WireFormat enhancements — `@WireName` discriminators + retire kotlinx for wire types

## Outcome (2026-06-11, branch `engine-dsl-osc-dsl-parameterization`)

**Done — full de-kotlinx, larger than the original plan (user opted in).** Key deviations from the plan
below, all confirmed by the build:

- **The plan's premise was wrong**: kotlinx `@Serializable` was *not* vestigial. `SprudelVoiceData`
  (kept, "out of scope") is itself `@Serializable` and holds `sound: SoundValue` (→ `IgnitorDsl`) +
  `SvdAdsr.attackCurve: AdsrCurve`, so its generated serializer pinned those audio_bridge types — the
  serialization plugin/deps could not be dropped without de-kotlinx-ing sprudel too. The user directed: rip
  **all** kotlinx serialization out of sprudel as well (no back-compat needed). `SprudelVoiceValue` lost its
  custom `SprudelVoiceValueSerializer`; `StaticSprudelPattern` (an early-dev remnant — `toJson`/`fromJson`
  unused, only `makeStatic` test-exercised) was deleted along with the `makeStatic` helpers.
- **Real latent bug found + fixed**: the sealed discriminator key `t` collided with `IgnitorDsl.Lerp.t`
  (the tag overwrote the field → silent wire corruption; the old int discriminator had the same bug, never
  tested). Fix: reserved wire keys are now **non-identifiers** — `#t` (codec type-tag) and `#v`
  (`WorkletContract` schema-version) — so no Kotlin data-class field can ever collide. A `WireCodecRoundTripSpec`
    + `IgnitorDslWireCodecSpec` case for `Lerp` guards it.
- Tests moved onto the **real** WireCodec (jsTest): `IgnitorDslWireCodecSpec`,
  `WorkletWireCodecRoundTripSpec` (sprudel). Pure-serialization tests deleted. `MutableVoiceDataGoldenSpec`
    + `JsCompatTests` rewritten to flatten via reflection (no kotlinx); golden regenerated.
- Plugin + `serialization_core`/`serialization_json` dropped from **both** `audio_bridge` and `sprudel`
  build files (sprudel keeps `coroutines_core`).

Phases 1–4 below were all completed (with the above expansions).

## Context

The audio frontend↔backend protocol moved to a KSP "trust-codec" (`:audio-wire-codec-ksp`, processor
`audio-wire-codec-ksp/src/main/kotlin/WireCodecProcessor.kt`) that generates `encode_X`/`decode_X` JS-object
functions into `audio_bridge/.../wire/WireCodecGenerated.kt` (jsMain only). `WorkletContract` uses it instead
of kotlinx `encodeToDynamic`/`decodeFromDynamic`.

Two leftovers from that migration, plus one weakness, are worth cleaning up:

1. **kotlinx `@Serializable` is now vestigial for wire types.** It is *not* on any runtime path — JS uses the
   WireCodec, JVM passes `Cmd`s in-process (no serialization). `serialization_json` is already a
   **commonTest-only** dependency (`audio_bridge/build.gradle.kts:39-49` comment). The only remaining
   consumers of the kotlinx serializers are a few JVM round-trip tests (see Phase 4).
2. **The sealed discriminator is a positional ordinal**, not a name. `WireCodecProcessor.emitSealed`
   (lines 261-288) emits `o.t = <index>` where the index is the leaf's position in `leafSubtypes()`
   (declaration order). Inserting a subtype mid-hierarchy shifts every later tag. It's safe (the auto
   `WIRE_SCHEMA_HASH` catches stale-build skew with a loud error — `WorkletContract:56`), but fragile and
   opaque on the wire. A stable string discriminator (`@WireName`) removes the order-sensitivity.
3. **9 unused `const val SERIAL_NAME` constants** in `KlangCommLink.kt` (lines 31-147) — defined, referenced
   nowhere. Their string values (`"cleanup"`, `"register-ignitor"`, `"schedule-voices"`, …) are exactly the
   stable wire names `@WireName` wants, so the two cleanups fold into one.

**Goal:** introduce `@WireName(name)`, switch the discriminator from ordinal to that string, drop kotlinx
`@Serializable`/`@SerialName` (+ the `SERIAL_NAME` constants + freed deps) from the audio wire types, and move
the round-trip tests onto the real WireCodec (jsTest).

## Architecture reference (verified — for the executing agent)

- **`@WireFormat`** (`audio_bridge/.../WireFormat.kt`, `SOURCE` retention, `CLASS` target) marks protocol
  ROOTS only: `KlangCommLink.Cmd`, `KlangCommLink.Feedback`, `ScheduledVoice`, `SampleRequest`. The processor
  walks the transitive graph from roots — **nested types (`IgnitorDsl`, `VoiceData`, `FilterDef`, `AdsrDef`,
  `EngineDsl`-to-be) need no annotation** and are already covered (e.g. `encode_IgnitorDsl_SuperSaw` exists).
- **Processor** (`WireCodecProcessor.kt`):
    - `process()` (55-104): collects roots, walks via `collectType`, emits `WIRE_SCHEMA_HASH` then per-type
      encode/decode.
    - `collectType()` (113-195): recursive graph walk; supports scalars (passthrough), enums (as ordinal),
      `List<T>`, `Map<String,Double>`, nested classes w/ primary ctor, sealed (flattened, incl. intermediate
      sealed levels), `data object` leaves. Unsupported shapes → `logger.error` (build fails), never silent.
    - `emitSealed()` (261-288): **the ordinal discriminator** — `o.t = $i` on encode, `when (o.t.unsafeCast<Int>())`
      on decode. `data object` leaves carry only the tag.
    - `emitClass()` (232-253): per-field `o.<name> = encExpr(...)` / ctor decode.
    - `typeSignature()` (214-220): per-type structure string folded into `WIRE_SCHEMA_HASH`; sealed signature is
      `codecId|leafCodecId,leafCodecId,...` (currently uses codecIds, **not** wire names).
    - companion (43-53): `ANN_WIRE_FORMAT`, `GEN_PKG`, scalar set.
- **Runtime helpers** (`audio_bridge/src/jsMain/kotlin/WireCodecSupport.kt`): `wireObj()`,
  `wireEncodeStringDoubleMap`, `wireEncodeList`/`wireDecodeList`. Unaffected by this change.
- **Schema hash** stamped/checked in `audio_be/src/jsMain/kotlin/WorkletContract.kt` (`PROP_VERSION`/`v`,
  `requireSchema`). The int→string discriminator change will bump it once (automatic; never hand-bumped).
- **kotlinx consumers still present** (all test-only for wire types):
  `audio_bridge/commonTest/IgnitorDslSerializationTest.kt`, `audio_bridge/commonTest/AdsrDefTest.kt`,
  `sprudel/commonTest/WorkletSerializationRoundTripSpec.kt`. (Unrelated, KEEP: `audio_fe` `SoundfontIndex`
  JSON loading; `sprudel` `SprudelVoiceValueSerializer` — custom, not the plugin.)

---

## Phase 1 — `@WireName` + string discriminator (processor change, do first + verify)

The riskiest, foundational step — change the processor and confirm the generated codec + a JS round-trip
before touching any annotations en masse.

1. **New annotation** `audio_bridge/src/commonMain/kotlin/WireName.kt` (sibling of `WireFormat.kt`):
   ```kotlin
   @Retention(AnnotationRetention.SOURCE)
   @Target(AnnotationTarget.CLASS)
   annotation class WireName(val name: String)
   ```
2. **Processor** (`WireCodecProcessor.kt`):
    - Add `private const val ANN_WIRE_NAME = "io.peekandpoke.klang.audio_bridge.WireName"`.
    - Helper:
      ```kotlin
      private fun wireName(decl: KSClassDeclaration): String =
          decl.annotations.firstOrNull { it.shortName.asString() == "WireName" }
              ?.arguments?.firstOrNull()?.value as? String
              ?: decl.simpleName.asString()   // fallback: simple name (rename-fragile; @WireName makes it stable)
      ```
    - `emitSealed`: replace the ordinal with `val tag = wireName(s)` and emit `o.t = "$tag"` (encode) /
      `when (o.t.unsafeCast<String>()) { "$tag" -> … }` (decode). Keep the `data object` vs class branches.
    - **Validate uniqueness:** within one sealed hierarchy, error if two leaves resolve to the same
      `wireName` (a name collision would silently mis-route) — add to the `errors` list like other diagnostics.
    - `typeSignature` (sealed branch): fold the **wire names** in, not leaf codecIds:
      `"${codecId(decl)}|" + leafSubtypes(decl).joinToString(",") { wireName(it) }` — so renaming a wire tag
      changes `WIRE_SCHEMA_HASH`.
3. **Verify** before Phase 2: `./gradlew :audio_bridge:compileKotlinJs`, inspect
   `audio_bridge/build/generated/ksp/js/.../WireCodecGenerated.kt` (tags now strings, falling back to simple
   names since no `@WireName` yet), and run the existing worklet round-trip once it's migrated, OR a temporary
   jsTest that does `decode_FilterDef(encode_FilterDef(x)) == x`.

## Phase 2 — Annotate wire leaves with `@WireName`

Migrate the existing kotlinx `@SerialName("…")` values 1:1 to `@WireName("…")` on every sealed leaf in the
wire graph, preserving the strings:

- `IgnitorDsl` (~80 `@SerialName` subtypes) → `@WireName` with the same names.
- `FilterDef` (BandPass/Body/Formant/HighPass/LowPass/Notch), `AdsrDef.Std` (`"std"`), `SoundValue`,
  `EngineDsl`/`StageDsl` (the new types from `docs/tasks/engine-dsl.md`).
- `KlangCommLink.Cmd`/`Feedback`/`Sample` leaves → `@WireName` using the **`SERIAL_NAME` string values**
  (`"cleanup"`, `"register-ignitor"`, `"schedule-voices"`, `"sample-not-found"`, …). This is where the two
  cleanups meet.

Prefer explicit `@WireName` over the simple-name fallback for every wire leaf (rename-safety).

## Phase 3 — Drop kotlinx `@Serializable` + `SERIAL_NAME` + freed deps

- Remove `@Serializable` + `@SerialName` + the `kotlinx.serialization.*` imports from the audio wire types:
  `IgnitorDsl`, `EngineDsl`, `VoiceData`, `AdsrDef`, `FilterDef`, `FilterDefs`, `FilterEnvDef`,
  `SampleRequest`, `ScheduledVoice`, `SoundValue`, `KlangCommLink`.
- Delete the **9 unused `const val SERIAL_NAME`** companion constants in `KlangCommLink.kt` (31-147) — their
  values now live as `@WireName` (Phase 2). Grep-confirm zero references first (already verified: none today).
- `audio_bridge/build.gradle.kts`: once **no `@Serializable` remains in audio_bridge** (audit first — there
  may be non-wire types), drop `kotlin("plugin.serialization")` (line 8) + `serialization_core` (40); drop
  `serialization_json` (49) from commonTest after Phase 4 moves the tests off kotlinx.
- **Audit before dropping deps:** confirm no persistence/other path serializes these types via kotlinx
  (checked: `audio_fe` uses Json only for `SoundfontIndex`; `sprudel` `SprudelVoiceValue` uses a custom
  serializer — both out of scope and unaffected).

## Phase 4 — Move round-trip tests onto the real WireCodec (jsTest)

The generated codec is JS-only, so these move `commonTest` → `jsTest` and assert against the *actual* wire
functions (more meaningful than the kotlinx proxy):

- `audio_bridge/.../IgnitorDslSerializationTest.kt` → jsTest: `decode_IgnitorDsl(encode_IgnitorDsl(dsl)) == dsl`
  for a representative leaf per family + a deep composite tree. (Data classes give structural `==`.)
- `audio_bridge/.../AdsrDefTest.kt`: move the serialization assertions to jsTest via `encode_AdsrDef`; keep the
  rest.
- `sprudel/.../WorkletSerializationRoundTripSpec.kt` → jsTest via `encode_ScheduledVoice`/`decode_ScheduledVoice`.
- Add a guard that every `StageDsl`/`IgnitorDsl` leaf has a unique `@WireName` (can be a processor-time error
  from Phase 1, or a reflection-free jsTest enumerating known tags).

## Risks & verification

- **Critical path:** the processor underpins the whole worklet boundary. Do Phase 1 + a JS round-trip before
  the mass annotation churn; bisect-friendly commits per phase.
- **Schema hash bumps once** on the int→string switch (expected; `WorkletContract` will reject any stale
  worklet, which is correct).
- **Wire-name collisions** must fail the build (Phase 1 validation), never silently mis-route.
- Full gates: `./gradlew :audio_bridge:jvmTest :audio_bridge:jsTest :audio_be:jvmTest :sprudel:jsTest`, plus a
  by-hand worklet smoke (play a note in the browser) since the codec only truly runs on JS.

## Effort

Medium. Phase 1 is small but delicate (a focused processor diff). Phase 2 is large but mechanical (~90
leaves, mostly `IgnitorDsl`). Phase 3-4 are straightforward once Phase 1 is proven. Sequence:
1 (processor) → 2 (annotate) → 3 (strip kotlinx + SERIAL_NAME + deps) → 4 (tests).

## Relationship to the EngineDsl work

`docs/tasks/engine-dsl.md` adds `EngineDsl`/`StageDsl` with kotlinx `@Serializable`/`@SerialName` to match the
*current* IgnitorDsl convention. When this task lands, those become `@WireName` + no `@Serializable` like
everything else. If this task is done **first**, EngineDsl should be born with `@WireName` and no kotlinx
annotations (and `Cmd.RegisterEngine` makes it reachable for the walk).
