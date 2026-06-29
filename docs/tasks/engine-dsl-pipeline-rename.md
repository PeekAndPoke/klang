# EngineDsl → PipelineDsl rename (+ then wire the inline `.pipeline(dsl)` path)

> **Status:** **BOTH PHASES DONE — fully green (uncommitted).**
> - **Phase A (rename):** byte-identical, all-module `jvmTest` + JS wire round-trips + worklet/root compile;
    > `voicedata_golden.txt` regenerated (pure `engine=`→`pipeline=`, 2352/2352); zero stray tokens. *(committed
    > separately by the user.)*
> - **Phase B (inline `.pipeline(PipelineDsl)` path):** DONE. New authoring carrier `PipelineValue`
    > (`Named`|`Dsl`, mirror of `SoundValue`); `.pipeline(dsl)` stamps `PipelineValue.Dsl`; `queryEvents`
    > (live) + `KlangOfflineRenderer` pre-register inline pipelines → `VoiceData.pipeline = dsl.uniqueId()`.
    > `LangPipelineSpec` extended (inline-DSL stamp + uniqueId resolution); all JVM+JS green. **Ready for the
    > first by-ear custom-pipeline test.**

## Context

"Engine" now means the **per-`playbackId` `PlaybackEngine`** (cylinders + scheduler + master) introduced by
the multi-engine backend. The per-voice DSP stage chain `EngineDsl(stages: List<StageDsl>)` was *also* called
"engine" — a naming collision. This task renames the per-voice layer **`EngineDsl` → `PipelineDsl`** (it *is*
the ordered per-voice pipeline consumed by `FilterPipelineBuilder`), coherently across wire / backend /
frontend / KlangScript / sprudel, then — under the correct name — wires the still-missing inline application
path so a custom pipeline is actually audible.

**Decisions (locked with the user):**

- **Rename first, then #5b** (Phase A then Phase B below).
- **Hard rename, no alias.** User-facing `.engine()` → `.pipeline()`, KlangScript `Engine` → `Pipeline`;
  update the built-in songs + the one tutorial that call `.engine(...)`. No back-compat shim.
- **Rename the wire symbols too** (`Cmd.RegisterEngine` → `RegisterPipeline`, `VoiceData.engine` →
  `pipeline`). It's an in-repo FE↔BE codec, both ends rebuild together → just **bump `WIRE_SCHEMA_HASH`**.
- **Do NOT touch** `PlaybackEngine` / `PlaybackEngineDispatcher` / `engineFor(...)` — that layer keeps "engine".

**Naming principle (from the user): one word per concept, end to end.** The pipeline concept uses the single
word **"Pipeline"** at *every* layer (KlangScript obj, DSL type, sprudel fn, wire field/cmd, both registries,
the built-ins preset) — so intellisense + docs + the wire all read the same. This deliberately avoids the
existing **`Osc`/`Ignitor` two-word split** (KlangScript `Osc.supersaw()` returns an `IgnitorDsl` — a known
debt to fix later, see memory `osc_ignitor_misnamed`). The only other word in the map is **`Stage`** — a
distinct sub-concept (a step *inside* a pipeline), also kept to one word, not a synonym for "pipeline". So
`AudioEngine` → `PipelinePreset` (not e.g. "BuiltInEngine") precisely to stay on the one word.

## Naming map

| Old                                                          | New                                                     | Note                                                                                                                |
|--------------------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `EngineDsl` (type)                                           | `PipelineDsl`                                           | `.modern`/`.pedal` companions stay (on the renamed type)                                                            |
| `EngineDsl.kt`                                               | `PipelineDsl.kt`                                        | file                                                                                                                |
| `StageDsl` + `@WireName` discriminators                      | **unchanged**                                           | stages within a pipeline; `Stage` object stays                                                                      |
| `EngineDslIdentity.kt`                                       | `PipelineDslIdentity.kt`                                | `globalEngineNames`→`globalPipelineNames`, `nextGlobalEngineId`→`nextGlobalPipelineId`; `uniqueId()` stays          |
| `Cmd.RegisterEngine`                                         | `Cmd.RegisterPipeline`                                  | `@WireName("register-engine")`→`("register-pipeline")`, ctor `dsl: PipelineDsl`                                     |
| `VoiceData.engine: String?`                                  | `VoiceData.pipeline: String?`                           | wire field (+ KDoc line 144)                                                                                        |
| `AudioEngine` enum (built-ins)                               | `PipelinePreset`                                        | `engineName`→`pipelineName`, `fromName`, `.dsl`                                                                     |
| `EngineRegistry` (backend, `audio_be/engines/`)              | `PipelineRegistry`                                      | `parent`/`fork()`/`get`/`register`                                                                                  |
| `EngineRegistry` (frontend, `klang/`)                        | `PipelineRegistry`                                      | per-controller `registerOrLookup`                                                                                   |
| `AudioBackendContext.engineRegistry`                         | `.pipelineRegistry`                                     | + `VoiceScheduler.engineFork`→`pipelineFork`, `registerEngine`/`resolveEngine`→`registerPipeline`/`resolvePipeline` |
| `KlangPlaybackController.registerEngine(dsl)` / `engines`    | `registerPipeline(dsl)` / `pipelines`                   | dormant hook (used in Phase B)                                                                                      |
| KlangScript `@Object("Engine")` / `object KlangScriptEngine` | `@Object("Pipeline")` / `KlangScriptPipeline`           | file `KlangScriptEngine.kt`→`KlangScriptPipeline.kt`; `Stage` object unchanged                                      |
| sprudel `.engine()` / `engineMutation` / `lang_engine.kt`    | `.pipeline()` / `pipelineMutation` / `lang_pipeline.kt` | + `SprudelVoiceData.engine`→`pipeline`                                                                              |

`uniqueId()` (the extension on the DSL) keeps its name. `IgnitorDsl` and everything osc-side is untouched.

## Phase A — the rename (mechanical, suite stays green)

Work module-by-module bottom-up so each compiles before the next:

1. **`audio_bridge`** (wire root): `EngineDsl.kt`→`PipelineDsl.kt`, `EngineDslIdentity.kt`→`PipelineDslIdentity.kt`,
   `KlangCommLink.kt` (`RegisterEngine`→`RegisterPipeline` + `@WireName`), `VoiceData.kt` (`engine`→`pipeline`
    + KDoc). The KSP codec regenerates; **`WIRE_SCHEMA_HASH` changes** — update any pinned expected value
      (`WireCodecRoundTripSpec`, and any hash constant test).
2. **`audio_be`**: `engines/AudioEngine.kt`→`PipelinePreset`, `engines/EngineRegistry.kt`→`PipelineRegistry.kt`,
   `AudioBackendContext` (`engineRegistry`→`pipelineRegistry`), `VoiceScheduler` (`engineFork`/`registerEngine`/
   `resolveEngine`), `FilterPipelineBuilder`/`EnvelopeRenderer`/`FilterHumanizationCoeffs` (param/type refs),
   `PlaybackEngineDispatcher` (`Cmd.RegisterPipeline` case, `data.pipeline`). **Keep** `PlaybackEngine*`/`engineFor`.
3. **`klang`**: `EngineRegistry.kt`→`PipelineRegistry.kt`, `KlangPlaybackController` (`registerEngine`→
   `registerPipeline`, `engines`→`pipelines`).
4. **`klangscript`**: `KlangScriptEngine.kt`→`KlangScriptPipeline.kt` (`@Object("Engine")`→`("Pipeline")`),
   `KlangScriptStageExtensions.kt` (`@TypeExtensions(EngineDsl::class)`→`PipelineDsl`).
5. **`sprudel`**: `lang_engine.kt`→`lang_pipeline.kt` (`.engine()`→`.pipeline()` ×4 overloads, `engineMutation`,
   KDoc/`@tags`), `SprudelVoiceData.kt` (`engine`→`pipeline` field + the two `mergeWith` lines).
6. **Songs + tutorial** (hard rename, update call sites): `builtinsongs/DerSchmetterling.kt`,
   `DialogueWithTheStars.kt`, `TetrisRemix.kt`, and `tutorials/tut_derSchmetterlingSoundDesignPlaygroundTutorial.kt:248`
   (`.engine("pedal")`→`.pipeline("pedal")` + the `// "pedal" engine` comment). *(Prose "engine" in
   `tut_FirstChords.kt:22` stays — not a DSL call.)*
7. **Tests**: `EngineRegistrySpec`→`PipelineRegistrySpec`, `PlaybackEngineDispatcherTest` (refs only — file stays),
   `WireCodecRoundTripSpec`, sprudel `LangEngineSpec`→`LangPipelineSpec` + `golden/GoldenCorpus.kt` (regenerate
   the affected golden entries), `CompletionProviderTest` (top-level symbol list `"Engine"`→`"Pipeline"`).

**Verification (Phase A):** `./gradlew :audio_bridge:jvmTest :audio_be:jvmTest :klangscript:jvmTest
:sprudel:jvmTest` green. In-browser smoke: a built-in song that used `.engine("pedal")` (DerSchmetterling) plays
identically. Behaviour is **byte-identical** — pure rename.

## Phase B — wire the inline `.pipeline(PipelineDsl)` application path (the old #5b)

The FE→BE transport is **already built and per-playback** (handover `engine-dsl-handover.md`): a
`Cmd.RegisterPipeline` + a voice with `VoiceData.pipeline = name` resolves end-to-end today; the dormant hook
is `KlangPlaybackController.registerPipeline(dsl)`. Mirror the inline-**oscillator** path 1:1:

1. **Pattern surface** — add a `.pipeline(PipelineDsl)` overload (or make `pipelineMutation` dispatch
   `is PipelineDsl`) that stashes the inline DSL on the event, exactly like `sound(IgnitorDsl)` →
   `SoundValue.Osc` (`lang_tonal.kt`). Likely a small `PipelineValue.Dsl` carrier or stash on the voice data.
2. **Denormalize in `queryEvents`** (`KlangPlaybackController.kt:~376-379`, next to the `registerIgnitor`
   loop): detect inline-pipeline events → `registerPipeline(dsl)` (pre-register, fires `Cmd.RegisterPipeline`
   once per unique DSL via `uniqueId()`) → resolve to `VoiceData.pipeline = dsl.uniqueId()` in `toVoiceData`.

After that the existing backend (`PipelineRegistry` fork, `VoiceFactory.get(data.pipeline)`) just works.

**Verification (Phase B):** new spec — an inline `Pipeline.of(Stage.vca().expK(…), Stage.filter())` on a voice
registers + renders the custom pipeline (parallel to the inline-osc test). Then the **first by-ear test of a
custom pipeline** in the live editor. `./gradlew :sprudel:jvmTest :klang:jvmTest` (+ offline render path).

## Out of scope (later)

Phase 2 typed oscillator subtype DSLs (`SupersawIgnitorDsl` etc. — not started), Phase 3 `Osc.EngineDefault`
tuning profiles, Phase 4 Katalyzer. Per-engine `PipelineRegistry` fork already exists (from the FE/BE work) —
carry it through the rename, no new forking needed.
