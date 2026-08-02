# Master DSL — master-in-pattern (D6, detailed plan)

> **Created 2026-08-02, NOT started.** Priority: **MUST #1** (`_priorities.md` item 1). This is the
> detailed implementation plan for the revised D6 design in
> [`per-playback-engine.md`](per-playback-engine.md) §H (master-in-pattern — supersedes
> `Song.master`/`Cmd.SetMaster`). First feature to be built under the **`/review-loop`** standard.

## Design in one paragraph

Master settings ride the pattern, exactly like Ignitors and Pipelines: a `MasterDsl` is registered by id
(`Cmd.RegisterMaster`, send-once caching), voices carry only the `masterId`, and the sprudel top-level
`master(Master().gain().limiter())` emits a once-per-cycle **data-only rest event** that the backend consumes at its
`startTime` — applying the new master via a short crossfade. Master becomes patternable (`"~".slow(8).master(...)`,
per-section masters, fade-outs/endings). A song without `master()` sounds **exactly** like today.

---

## Reuse audit — one effect family, three hosts (checked 2026-08-02)

**Verified against the code: NO third effect-implementation family is needed.** The layering is already right:

- **The real DSP lives in `audio_be/.../effects/`** (`Compressor`, `Reverb`, `DelayLine`, phaser, …) — buffer-level,
  stereo, host-agnostic. **This is the reuse unit.**
- The Katalyst layer is ~20-line shells over it: `KatalystEffect` is a
  `fun interface { process(ctx) }`, `KatalystCompressorEffect` is literally
  `compressor?.process(ctx.mixBuffer.left, ctx.mixBuffer.right, ctx.blockFrames)`. The master chain gets the same kind
  of thin shells (`MasterFx*`), over the SAME DSP classes.
- The master mix is a `StereoBuffer` — the exact type Katalyst effects process. Insert effects (compressor/phaser
  pattern) work verbatim. **Send/return effects (Reverb, Delay) need one small adapter for insert-style master use**:
  the shell owns a scratch `StereoBuffer`, copies the mix scaled by a `wet` param into it, then calls the unchanged
  `Reverb.process(scratch, mix, frames)` — no new DSP, just routing.
- **`MasterStage` (audio_be) already exists** — today's limiter + DC + clip with `reset()`, invoked as
  `master.process(mix, out)`. The plan: the *limiter* becomes a configurable chain stage (parameters extracted,
  defaults == today's consts); **DC-block + clip + interleave stay fixed in the output stage** (safety/format, not
  sound — never user-configurable).
- ⚠ Naming: the wire stage type must NOT be called `MasterStage` (collides with the audio_be class) → wire type is **
  `MasterStageDsl`**, mirroring `StageDsl`.

Same story for the future Katalyst DSL: Master DSL and Katalyst DSL share the DSP layer AND the wire-stage pattern — one
vocabulary, different hosts (orbit bus / master bus).

## Part 1 — Wire-compatible data model (`audio_bridge`)

New `audio_bridge/.../MasterDsl.kt` — **modelled 1:1 on `PipelineDsl`** (the DX directive: same shape as
Ignitors/Pipeline everywhere):

- **`MasterDsl(stages: List<MasterStageDsl>)`** — mirror of `PipelineDsl(stages: List<StageDsl>)`.
- **`sealed interface MasterStageDsl`** with `@WireName` variants (the `StageDsl` pattern). v1 set (see §v1 effect set):
  `Gain`, `Limiter`, `Reverb`, `Delay`.
- **`MasterDsl.default`** == exactly today's chain (the current `MasterStage` limiter, as-is) — guarded by
  `MasterDefaultsSyncSpec` (the `*DefaultsSyncSpec` family).
- **Codec:** KSP trust-codec picks the new types up automatically (schema hash auto-shifts — the
  `RegisterPipeline` precedent; no manual bump).
- **Carrier: `MasterValue = Named(name) | Dsl(master)`** — the exact mirror of `PipelineValue`. NOT a wire type;
  denormalized to `VoiceData.master: String?` at the boundary (`Dsl → uniqueId()`, `Named → name`).
- House rules: `Double`/`Int` only (no boxed types); coerce user params, never `require()`.
- Out of scope v1: patternable per-param master slots (`collectParams()`) — patternability comes from *events*, not
  slots. Revisit only if a real need appears.

## Part 2 — Builder DSL (Kotlin)

Both in-house DX idioms, mirroring their originals exactly:

- **Primary (the Pipeline twin):** `Master.of(MasterFx.gain(2.0), MasterFx.limiter(), …)` — mirrors
  `Pipeline.of(Stage.vca(), …)` verbatim; `MasterFx` is the stage factory object (the
  `Stage` counterpart; name open — `MasterFx` avoids the `MasterStage` collision).
- **Chained sugar (the Ignitor feel):** `Master().gain(2.0).limiter()` — each call appends a stage, copy-on-chain
  (`copy(stages = stages + MasterStageDsl.X(...))`), matching the
  `Osc.supersaw().spread().analog()` idiom. `Master()` with no stages == unity pass-through.
- **Identity/caching:** `MasterDslIdentity` mirroring `PipelineDslIdentity` — synthetic
  `master-N` names via `uniqueId()`, `registerOrLookup` dedup by value so identical DSLs share one registration (the
  send-once contract: a voice carries the `masterId` only after the data is known to be registered — same as Ignitors).
- Named presets resolvable via `MasterValue.Named` (v1: `"default"`; more later).

## v1 effect set — the minimal mastering experience

Driven by the real workflow pain: **songs are currently mixed down low to avoid per-orbit compressor plops — the master
must bring the level back up and limit it.**

1. **`Gain`** — make-up gain, the structural fix for the mixed-low workaround. Trivial new code (one multiply loop; no
   DSP to reuse).
2. **`Limiter`** — extract/parameterize today's `MasterStage` limiter; defaults == today.
3. **`Reverb`** — reuse `effects/Reverb` via the insert adapter (`wet` param + scratch send).
4. **`Delay`** — reuse the delay DSP the same way (`wet`, `time`, `feedback`).

Deliberately NOT v1: a master **Compressor** — plop/pumping on the full mix is exactly the problem the mixed-low
workaround fled from; the `Compressor` class is a 20-line shell away whenever wanted. Glue/EQ/drive: later, same
pattern.

## Part 3 — KlangScript integration

- **Two stdlib objects mirroring `Pipeline` + `Stage` exactly:** `@Object("Master")` (with `.of(…)`)
  and `@Object("MasterFx")` (stage factories), plus `@KlangScript.TypeExtensions` chained methods on `MasterDsl` for the
  sugar form. Named args work (`name = value`). Same registration surface, same docs surface, same feel — a user who
  knows `Pipeline.of(Stage.vca())` already knows
  `Master.of(MasterFx.gain())`.
- KDoc per param: description, default, range (doc-block quality standard) → live docs.
- **Dual-language equivalence spec** (`KlangScriptMasterSpec`, commonTest): each method + full chain + default —
  KlangScript source `shouldBe` the Kotlin builder (the `KlangScriptSuperSawSpec`
  template).
- Static inference: `Master().gain()` chains need the return-type narrowing to keep base methods — the static supertype
  inferrer already handles this (KSP `supertypes` emit) if subtypes are used; with a single `MasterDsl` type it's
  trivial.

## Part 4 — Sprudel surface

- **`SprudelVoiceData.master: MasterValue?`** — flat field, mirroring `pipeline: PipelineValue?`
  (no Svd group needed for a single ref; follow the pipeline precedent exactly, incl. merge semantics: last-writer-wins
  on merge).
- **Top-level `master(dsl)`** → rest-carrier pattern: one whole-cycle event per cycle whose only payload is the master
  ref — conceptually
  `silence.reinterpretVoice { copy(master = MasterValue.Dsl(dsl)) }`. Composes as
  `stack(lead, bass, …, master(...))`.
- **Mapper form `.master(dsl)`** — all four overload forms (a) `SprudelPattern.master`, (b) `String.master`, (c)
  `master()` factory, (d) `PatternMapperFn.master` chained — WITH the form- (d) test from the start (don't recreate the
  untested-overload gap).
- **⚠️ FIRST INVESTIGATION ITEM — the data-only voice.** Rests today produce NO voice on the wire. Find where rest
  events are dropped (event→`VoiceData` mapping in
  `queryEvents`/`toVoiceData`/scheduling filter) and thread a "data-only event" through: it must (1) survive to
  `VoiceData` (no sound, no freq), (2) be scheduled, (3) be consumed by the scheduler at `startTime`, (4) NEVER reach
  synthesis (`VoiceFactory`). This is the one genuinely new concept in the whole feature — everything else is the
  pipeline playbook. Prototype this first; if it's ugly, the fallback is a dedicated wire message stream
  (`Cmd.ScheduleMasterChange`)
  — but prefer the voice path (one stream, one ordering, live-update dedup for free).

## Part 5 — Sending & caching (frontend)

- **`Cmd.RegisterMaster(uniqueId, dsl)`** — mirror `Cmd.RegisterPipeline` (KSP codec auto-bump).
- `KlangPlaybackController.queryEvents`: pre-register masters exactly like pipelines (walk events → `registerOrLookup` →
  fire `Cmd.RegisterMaster` before the voices carrying the id).
- **Offline:** `KlangOfflineRenderer` registers on a new `renderer.masterRegistry` accessor — mirror of the
  `pipelineRegistry` accessor. Renders stay faithful with zero extra plumbing.
- Live updates: master events ride `Cmd.ReplaceVoices` like any voice; the full-identity dedup already compares `data`,
  and per-cycle re-emissions have distinct `startTime`s → safe. No new code expected; add a regression test anyway.

## Part 6 — Backend application

- **`MasterRegistry`** (BE): registrations are playback-scoped in arrival but content-addressed — follow the pipeline
  registry placement (per-playback fork of a shared parent, matching the FE/BE state-placement rules; masters are
  engine-level state).
- **`VoiceScheduler`**: at promotion time, a voice with `master != null` and no sound is a **master-change event** —
  route to `PlaybackEngine.applyMaster(masterId, atFrame)` and consume it (never enters `VoiceFactory`/rendering).
  **Last writer wins** per engine (strudel convention); within one block, the latest event at the same time wins.
- **`PlaybackEngine.renderInto`**: applies the active master chain to its bus post-`processAndMix`, pre-sum (the §H
  architecture). The **global safety limiter at the final mix STAYS** — master is sound, the output stage is safety.
- `KlangAudioRenderer`: extract today's hardcoded limiter parameters as the `MasterDsl.default`
  constants (single source of truth; sync-spec guards it).

## Part 7 — Soft transitions: the master crossfade (no cracks)

The user-facing question: how do we go from master A to master B without crackling?

**Chosen approach: dual-chain crossfade** (recommendation — decide before building):

- Run the OLD and NEW master chains **in parallel** over a short window, blend the outputs, then drop the old chain.
  Precedent: `KatalystFilterSwap` (the body/vowel live-change click fix).
- Why not parameter ramping: it only works when both masters share a topology (same stages), and limiter *state*
  (envelope followers) cannot be meaningfully interpolated. Dual-chain handles any A→B pair with ONE mechanism. Cost: 2×
  master DSP for ~50–100 ms, **per engine, not per voice** — negligible.
- **Warm-up for free:** the new chain processes real input during the whole fade window, so its limiter envelope
  followers settle *before* it reaches full weight — no cold-start pumping.
- **Curve: LINEAR, not equal-power.** DSP subtlety: both chains process the SAME input, so their outputs are highly
  **correlated** — amplitude-complementary (linear) blending sums correctly; equal-power (sin/cos) would bump correlated
  material up to +3 dB mid-fade. (Equal-power is for *uncorrelated* sources.) Verify by ear; keep the curve a named
  constant.
- **Per-sample fade** — the blend coefficient ramps every sample, never in per-block steps (block steps ARE the zipper).
- **Timing:** the fade starts at the master event's `startTime`, sample-accurate within the block.
- **Constants:** `MASTER_XFADE_SEC` ≈ 0.05–0.1, tune by ear.
- **Stateful tails on swap:** with Reverb/Delay in the chain, the OLD chain's tail fades out over the crossfade window
  and is then cut with the chain. v1: accept it (50–100 ms fade masks it; by-ear check with a long reverb). If audible,
  extend the old chain's life until its output falls below −80 dB (the voice-culling threshold idea) before dropping it.
- **Retarget policy (open decision):** a new master event arriving MID-fade. Options: (a) complete the current fade in
  its remaining window, then start the next fade (queue, max 1 pending — simplest, slightly laggy); (b) retarget —
  snapshot the current blend as the new "old" side (needs a third chain or accepting a small step). **Proposal: (a)**
  for v1 — rapid master changes are an edge case; last-writer-wins collapses bursts anyway. By-ear check with a
  pathological fast-master pattern.

## Tests (apply `/review-loop`: every one mutation-checked)

- **Wire round-trip** jvm+js (`WireCodecRoundTripSpec` family) — `MasterDsl` + `Cmd.RegisterMaster`.
- **Defaults sync** — `MasterDsl.default` fields == the extracted `KlangAudioRenderer` consts.
- **Golden** — a song WITHOUT `master()` renders byte-identical to today (behavior-preserving).
- **Render effect** — `master(Master().gain(0.5))` measurably halves output (render-diff guard).
- **Data-only voice** — scheduled, consumed at `startTime`, never synthesized; survives sprudel→wire;
  `stack(notes, master(...))` produces N notes + 1 master event per cycle.
- **Crossfade continuity** — render across a master swap; assert bounded sample-to-sample delta (click detector); fade
  completes to pure-B; old chain dropped.
- **Live-update** — master event resend through `ReplaceVoices` dedups safely.
- **Dual-language** — `KlangScriptMasterSpec`.
- Sprudel overload forms a–d tested from day one.

## Open decisions

1. Data-only voice path — where rests are dropped today; voice-stream vs fallback message (§Part 4).
2. Crossfade retarget policy (§Part 7 — proposal: queue-max-1).
3. Fade window + curve final values (by ear; start linear / 50 ms).
4. `Master()` empty = unity vs default-seeded (§Part 2 — proposal: empty = unity).

## Critical files

- **new** `audio_bridge/.../MasterDsl.kt` (+ `MasterValue`), `KlangCommLink.kt` (`Cmd.RegisterMaster`)
- **new** `sprudel/.../lang/lang_master.kt`; `SprudelVoiceData` (+ merge), `toVoiceData`
- **new** klangscript stdlib `Master` object + extensions
- `klang/.../KlangPlaybackController.kt` (`queryEvents` pre-registration), `KlangOfflineRenderer`
- `audio_be`: **new** `MasterRegistry`, `VoiceScheduler` (data-only consume), `PlaybackEngine`
  (active master + crossfade), `KlangAudioRenderer` (extract default consts)

## Links

- [`per-playback-engine.md`](per-playback-engine.md) §H — the design decision record (revised 2026-08-02)
- [`katalyst-dsl.md`](katalyst-dsl.md) — will follow this application-path precedent
- Memory: `project_per_playback_engine` (D6 revision paragraph), `engine_dsl_misnamed`
  (`PipelineValue` playbook), `project_live_update_double_voice` (dedup), `project_body_resonator`
  (`KatalystFilterSwap` crossfade precedent), `feedback_review_loop`
