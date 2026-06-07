# KSP-generated worklet wire codec

Status: **planned** (not started). Created 2026-06-07.

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
- **Version-skew guard:** "trust the input" assumes frontend + worklet are the **same build** (true — one bundle).
  Add a single `v` schema-version int to the top-level envelope; the worklet rejects/logs on mismatch so a stale
  cached worklet fails loudly instead of producing silent garbage.

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
