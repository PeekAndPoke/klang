# KSP-generated worklet wire codec

Status: **DONE** ✅ — generated codec live in WorkletContract; ~174× decode; browser playback confirmed; deps trimmed.
Created 2026-06-07.

Phase 7 + cleanup (done 2026-06-07): browser playback confirmed working with the generated codec. Dependency cleanup
(the worklet swap freed these): `audio_be` + `audio_jsworklet` had ZERO kotlinx-serialization usage (main+test) →
dropped the `kotlin("plugin.serialization")` plugin + serialization deps. `audio_bridge`: `serialization_json` moved to
`commonTest` (commonMain keeps `serialization_core` + plugin for the `@Serializable` wire-type annotations, which the
golden + round-trip oracle tests still require). kotlinx-serialization itself STAYS — used app-wide (common/sprudel/
audio_fe for songs/klangscript/klangbuch/persistence) and by the golden test (`json.encodeToString(VoiceData…)`).
Verified: audio_bridge jvmTest + full app JS compile all green.

Phase 6 (done 2026-06-07): generator emits `const val WIRE_SCHEMA_HASH` (deterministic structural hash of the type
graph via `typeSignature`/`typeSig`, sorted). `WorkletContract` rewritten (~242→~62 lines): `sendCmd`/`decodeCmd`/
`sendFeed`/`decodeFeed` now call `encode_KlangCommLink_Cmd`/`decode_…`/`…Feedback` + stamp/check `v = WIRE_SCHEMA_HASH`
(rejects stale-build skew). All hand marshalling + the kotlinx `Json` codec + `PROP_*` deleted; no external refs broke.
Full app JS compiles (klang/audio_jsworklet/audio_fe). Microbench now measures the GENERATED codec (replaced the hand
proof): **decode 67,001→385 ns/op (~174×), encode 4,904→478 ns/op (~10×), round-trips == original = true.**
Phase 7 REMAINING: real browser playback session (audio plays, no glitches) — only the user can do that; the JS worklet
path isn't covered by JVM tests. (audio_bridge jsTest codec round-trip + sprudel kotlinx
WorkletSerializationRoundTripSpec
both green.)

Phase 5 (done 2026-06-07): added `DoubleArray` pass-through, `data object` sealed subtypes (tag-only encode /
singleton decode), and intermediate-sealed flattening (`Cmd.Sample` → leaves, single `t` tag space via `leafSubtypes`).
This unlocked `Cmd` + the recursive 60+-node `IgnitorDsl` (recursion handled by the cycle-detecting walk + mutually
recursive generated functions) + `MonoSamplePcm`/`SampleMetadata`. **109 types generate**, the full protocol; jsTest
round-trips `Cmd`(+IgnitorDsl tree, +Sample.Chunk DoubleArray compared by content), `Feedback`, `ScheduledVoice` on JS.
**FAIL-EARLY (per request):** the processor NO LONGER silently defers — `collectType` records every unsupported shape
with a field/subtype path and `logger.error`s them (failing the build). To add a wire type, extend the emitter.
NOTE: schema-version tag is still Phase 6 (not yet emitted). Remaining: Phase 6 (WorkletContract swap + schema-hash),
Phase 7 (full validation + microbench re-run + browser).

Phases 3+4 (done 2026-06-07): generator core. Transitive walk from `@WireFormat` roots (`collectType`), defers a
root if its graph hits an unsupported shape. Emits `encode_<flat>`/`decode_<flat>` (flat = nesting chain, e.g.
`encode_FilterDef_LowPass`, `encode_KlangCommLink_Feedback`). Handles: scalars (pass-through), enums (ordinal Int),
**sealed (int `t` type-tag** over `getSealedSubclasses()`), nested classes, `List<T>`, `Map<String,Double>`, and
nullability (encode uses Kotlin `?.let` on the typed value; decode uses `if (acc != null)` — NOT `?.let`, which
doesn't bind on a `dynamic`). Hand-written JS-interop helpers in `audio_bridge/src/jsMain/.../WireCodecSupport.kt`
(`wireObj`/`wireEncodeList`/`wireDecodeList`/`wireEncode|DecodeStringDoubleMap`). Generated 20 types (full
`ScheduledVoice`→`VoiceData`→adsr/filters subgraph + `Feedback` subgraph). `Cmd` deferred — ONLY because of
`DoubleArray` (sample PCM) + `IgnitorDsl` `data object` subtypes. jsTest `WireCodecRoundTripSpec` round-trips a
populated `ScheduledVoice` + `Feedback` on the browser runtime. Remaining: Phase 5 (`DoubleArray` + `data object` +
recursion → unlock `Cmd`/`IgnitorDsl`), Phase 6 (WorkletContract swap + schema-hash), Phase 7 (validation + bench).

Phase 2 (done 2026-06-07): generator emits `encode<T>`/`decode<T>` for non-sealed all-scalar data classes
(`SampleRequest`) into `audio_bridge/build/generated/ksp/js/jsMain/kotlin/.../wire/WireCodecGenerated.kt`. KSP
auto-registers that dir on the JS compile path (no manual `srcDir`). New jsTest
`audio_bridge/src/jsTest/.../WireCodecRoundTripSpec` round-trips it (green on browser). Sealed/nested roots
(`Cmd`/`Feedback`/`ScheduledVoice`) logged as deferred + skipped → build green. Emitter helpers:
`isSimpleScalarDataClass`,
`emitScalarCodec`; `SCALARS` set.

Phase 1 (done 2026-06-07): `@WireFormat` annotation in `audio_bridge/commonMain`; new `:audio-wire-codec-ksp`
module (plain JVM, `@AutoService` provider, discovery-only processor); wired via `add("kspJs", ...)` on
`audio_bridge`. Annotated roots `ScheduledVoice`, `KlangCommLink.Cmd`, `KlangCommLink.Feedback`.
`:audio_bridge:kspKotlinJs` runs the processor on the JS target and logs all 3 roots; both targets compile.
The one unproven bit — `kspJs` into the JS target seeing commonMain types — is confirmed.

## Context

The audio worklet receives commands via `WorkletContract` (`audio_be/src/jsMain/kotlin/WorkletContract.kt`),
which serializes voice payloads with kotlinx `encodeToDynamic` / `decodeFromDynamic`. A microbench
(`audio_benchmark/src/jsMain/kotlin/WorkletSerializationBenchmark.kt`, run via `:audio_benchmark:jsNodeProductionRun`)
measured, per `ScheduledVoice`, on JS/Node24:

|                                    | kotlinx JSON-dynamic | hand trust-codec (JS object) | speedup   |
|------------------------------------|----------------------|------------------------------|-----------|
| encode (frontend, main thread)     | 4,994 ns             | 440 ns                       | ~11×      |
| **decode (worklet, AUDIO thread)** | **64,309 ns**        | **398 ns**                   | **~162×** |
| structuredClone (postMessage)      | 4,907 ns             | (unchanged)                  | —         |

The dominant cost is the worklet-side `decodeFromDynamic` (~64 µs/voice) **on the audio thread** — the kotlinx
generic decode driver (name→index matching, seen-bitmask, `ignoreUnknownKeys`, polymorphic discriminator dispatch
for sealed `AdsrDef`/`FilterDef`, construct-with-defaults). It's **defensive** because it can't trust its input.
**ProtoBuf was tried and rejected** — kotlinx binary is *slower* on Kotlin/JS (encode 50 µs). See
`project_worklet_serialization` memory.

Conclusion: a **"trust the input" codec** — we own both ends (frontend + worklet are the same bundle) — that
directly reads properties and constructs objects, skipping the framework, collapses decode ~160×. The hand-rolled
proof in the microbench round-trips `== kotlinx` and hits 398 ns. This task **generates** that codec with KSP for
**all** worklet wire types, which also lets `WorkletContract` shed its hand-written dynamic marshalling.

## Goal

- Generate symmetric `encode`/`decode` functions (JS, `dynamic` ⇄ Kotlin) for every type crossing the worklet
  boundary, replacing `encodeToDynamic`/`decodeFromDynamic` and the hand-built `jsObject{}` marshalling in
  `WorkletContract`.
- Net result: ~160× faster worklet decode (audio thread), ~11× faster encode, and a much simpler `WorkletContract`.

## Architecture

KSP processors must be a **separate JVM module** (the compiler loads them) — so the processor cannot live *inside*
`audio_bridge`. Layout, following the `klangscript-ksp` / `klangscript-annotations` convention:

- **`:audio-wire-annotations`** (or just reuse `audio_bridge`) — a `@WireFormat` annotation, `@Retention(SOURCE)`.
  Simplest: define `@WireFormat` directly in `audio_bridge/commonMain` (the wire types already live there; the
  processor matches by FQN string, so no module dependency is required).
- **`:audio-wire-codec-ksp`** — new plain `kotlin("jvm")` module (mirror `klangscript-ksp/build.gradle.kts`):
  `Deps.Ksp.symbol_processing` (v2.3.6), `Deps.JavaLibs.Google.auto_service`, `kotlin-kapt` + `@AutoService`
  registration. Implements `SymbolProcessorProvider` + `SymbolProcessor`.
- **Generated code lands in `audio_bridge` `jsMain`.** ⚠️ The codec uses `dynamic` / `js("{}")`, so it is
  **JS-only** — it must NOT go through `kspCommonMainMetadata` (the klangscript pattern, which targets commonMain).
  Wire it per-target on the JS compilation:
  ```kotlin
  // audio_bridge/build.gradle.kts  (already applies the ksp plugin)
  dependencies { add("kspJs", project(":audio-wire-codec-ksp")) }
  kotlin.sourceSets.jsMain { kotlin.srcDir("build/generated/ksp/js/jsMain/kotlin") }
  // + the kspKotlinJs-before-compileKotlinJs task ordering, mirroring the existing setup
  ```
  When KSP runs for the JS target it sees the annotated types in `commonMain` and emits JS code into `jsMain`.
  `audio_be` (depends on `audio_bridge`) then calls the generated functions from `WorkletContract`.

**Why generate on `audio_bridge` (not `audio_be`):** KSP's `getSymbolsWithAnnotation` scans the *current module's*
sources, not dependencies. The wire types are defined in `audio_bridge`, so the processor must run there.

## Codec design

- **Format: keyed JS object** (start here — it's exactly what the 398 ns proof used; full field names for
  debuggability). Optional later optimization: **positional arrays** (no key strings → smaller payload, faster
  `structuredClone`) — measure before adopting; only matters if the residual ~5 µs main-thread clone becomes the
  bottleneck. Do NOT go binary (ProtoBuf proved JS binary is slow).
- **Sealed/polymorphic** (`Cmd`, `Feedback`, `AdsrDef`, `FilterDef`, `IgnitorDsl`): emit a small integer `t` type-tag
  (subtype ordinal) + the subtype's fields; decode `when (o.t)`. (Integer tag beats the `@SerialName` string for
  speed; we own both ends.)
- **Enums** (`AdsrCurve`): encode `.ordinal` (Int); decode `Enum.entries[i]`.
- **Lists** (`List<ScheduledVoice>`, `List<FilterDef>`, `List<IgnitorDsl>`, `List<Double>`, `List<Band>`,
  `List<CylinderState>`): JS array, element codec applied per item.
- **Map<String, Double>?** (`oscParams`): nested object of key→value.
- **Recursion** (`IgnitorDsl`, 60+ subtypes, unbounded depth): naturally handled — the generated
  `encodeIgnitorDsl`/`decodeIgnitorDsl` call themselves for child `IgnitorDsl` fields. The 60+ subtypes become one
  big `when` on the type-tag. Not perf-critical (only in `RegisterIgnitor`, infrequent) but needed for completeness.
- **`DoubleArray`** (`MonoSamplePcm.pcm`, `Sample.Chunk.data`): pass the typed array through **as-is** (`o.pcm = arr`);
  do NOT element-encode. For `Sample.Chunk.data` consider `postMessage` **transfer** (it's large + chunked) — a later
  refinement. `MonoSamplePcm` is not `@Serializable`; the generated codec handles it like any other annotated class.
- **Nullable / defaults**: omit nulls on encode; on decode, missing → the constructor default (the generator knows
  each param's default, or passes null for nullable-without-default).
- **Version-skew guard (auto-derived — NOT hand-bumped):** "trust the input" assumes frontend + worklet are the
  **same build**. That holds for one bundle, but the AudioWorklet module is fetched by URL and the browser can cache
  it independently → a stale worklet can meet a fresh frontend. Guard: the processor, while walking the type graph,
  folds a **deterministic structural hash** (each type's field names+types+order, sealed subtype lists, enum entries)
  and emits `const val WIRE_SCHEMA_HASH: Int`. Any wire-type change → graph changes → hash changes, automatically (no
  human bump to forget). `encodeCmd` stamps `o.v = WIRE_SCHEMA_HASH` **once on the top-level envelope** (one int/msg,
  not per nested object); `decodeCmd` checks it first and on mismatch rejects + logs loudly (optionally emits a
  `Feedback` so the frontend can prompt a reload). Same-bundle operation always matches → guard is free; only a
  stale-cached worklet trips it.

## Type inventory (everything to cover)

Roots (annotate these; processor walks the transitive `@Serializable` graph):

- **`KlangCommLink.Cmd`** (sealed, 9): Cleanup, ClearScheduled, ReplaceVoices, ScheduleVoice, ScheduleVoices,
  RegisterIgnitor, Sample.{NotFound, Complete, Chunk}. (Currently hand-dispatched in WorkletContract — the codec
  subsumes that.)
- **`KlangCommLink.Feedback`** (sealed, 4): BackendReady, RequestSample, SampleReceived, Diagnostics (+ nested
  `CylinderState`).

Transitive: `ScheduledVoice`; `VoiceData` (~70 fields); `AdsrDef.Std` (+ enum `AdsrCurve`); `FilterDefs`→
`List<FilterDef>`;
`FilterDef.{LowPass,HighPass,BandPass,Notch,Formant}` (+ `Formant.Band`); `FilterEnvDef`; `SampleRequest`;
`SampleMetadata` (+ `LoopRange`, nullable `AdsrDef`); `MonoSamplePcm` (DoubleArray, not @Serializable);
`IgnitorDsl` (60+ recursive subtypes, enum `AdsrCurve`, `List<IgnitorDsl>`, `List<Double>`).
Exclude: `AdsrDef.Resolved` / `FilterEnvDef.Resolved` (runtime-only, never on the wire).

Hard cases (call out in implementation): IgnitorDsl recursion; DoubleArray pass-through/transfer; the 60+-subtype
dispatch; nullable sealed (`SampleMetadata.adsr`).

## WorkletContract integration

Replace the per-payload `codec.encodeToDynamic(...)` / `decodeFromDynamic(...)` (and ideally the hand-built `Cmd`
envelope `jsObject{}` blocks + `MonoSamplePcm.encode` etc.) with calls to the generated `encodeCmd`/`decodeCmd`,
`encodeFeedback`/`decodeFeedback`. This is the "much simpler WorkletContract" payoff. Keep `postMessage`/`MessagePort`
plumbing. The `Json { ignoreUnknownKeys; explicitNulls=false }` codec can be removed once nothing uses it.

## Validation

- **Generated codec is symmetric** (both ends generated from the same types), so the correctness guard is a
  **self-round-trip corpus**: `encode(x)` → `decode` → `== x`, for every `Cmd`/`Feedback` subtype, a fully-populated
  `VoiceData` (all filter types + adsr + oscParams), a deep nested `IgnitorDsl` tree, and a `Sample.Chunk` with a
  `DoubleArray`. Must be a **jsTest** (the codec is `dynamic`-based). New:
  `audio_be/src/jsTest/.../WorkletCodecRoundTripSpec`.
- Keep the existing **`sprudel/.../WorkletSerializationRoundTripSpec`** (kotlinx) — it independently guards the
  `toVoiceData()` → `VoiceData` shape on JVM+JS.
- Re-run the microbench (`:audio_benchmark:jsNodeProductionRun`) to confirm encode/decode land at the ~400 ns the
  hand proof showed.

## Phasing

1. `@WireFormat` annotation in `audio_bridge/commonMain`; new `:audio-wire-codec-ksp` module + settings include;
   `@AutoService` provider skeleton that lists annotated symbols (no codegen yet) — verify KSP runs on `audio_bridge`
   JS.
2. Generate for a simple type (`SampleRequest`, all-scalar) end-to-end; wire `srcDir`; confirm it compiles + a tiny
   jsTest round-trips.
3. Add: enums, nullable/defaults, nested `@Serializable`, `List`, `Map` → covers `VoiceData`, `FilterDef`*, `AdsrDef`,
   `ScheduledVoice`.
4. Add sealed-with-type-tag dispatch → `Cmd`, `Feedback`, `FilterDef`, `AdsrDef`.
5. Add recursion + the 60+ subtypes → `IgnitorDsl`. Add `DoubleArray` pass-through → `MonoSamplePcm`, `Sample.Chunk`.
6. Wire `WorkletContract` to the generated codec; delete the hand marshalling + the `Json` codec. Add version tag.
7. Full corpus jsTest + microbench re-run + a real browser session (audio plays, no glitches) + the offline-render
   suites (`audio_bridge`/`audio_be`/`klang` jvmTest) for regression.

## Expected payoff

Worklet decode ~64 µs → ~0.4 µs/voice (audio thread); encode ~5 µs → ~0.44 µs; `WorkletContract` loses its
reflective codec + most hand marshalling. `structuredClone` (~5 µs, main thread) remains — acceptable; revisit with
positional arrays only if it surfaces.

## Risks / gotchas

- **JS-only generation**: must use `kspJs` into `jsMain`, NOT `kspCommonMainMetadata` (codec uses `dynamic`).
- **KSP scans current module**: generate on `audio_bridge` (where the types are), consume from `audio_be`.
- **Version skew**: same-build assumption + schema-version tag (fail loud).
- **DoubleArray + structuredClone**: large sample buffers — pass-through now, consider transfer later.
- **`.also { }` on `dynamic` doesn't bind `it`** (hit in the microbench) — generated code must use explicit locals
  (`val o = js("{}"); o.x = …`), not `jsEmpty().also { it.x = … }`.
- Keep `WorkletSerializationBenchmark.kt`'s hand codec as the reference for the generated output's shape.
