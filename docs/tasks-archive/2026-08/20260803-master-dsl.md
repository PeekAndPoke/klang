# Master DSL — master-in-pattern (D6, detailed plan)

> **ARCHIVED 2026-08-03 — SHIPPED.** The master path (D6, the original goal of the whole
> per-playback-engine work) is built, reviewed and by-ear confirmed. Merged on `master-dsl`; the
> reverb/delay parameter-parity follow-up landed as `d78ff3da`. Remaining loose ends were carried
> forward to `docs/tasks/master-dsl-followups.md` — everything in the "Still open" section below is
> historical context for those.
>
> **Created 2026-08-02. IMPLEMENTED 2026-08-02 on branch `master-dsl`** (round-1 review applied; see
> §Implementation notes at the end for where the build deviated from this plan). Priority: **MUST #1** (`_priorities.md`
> item 1). This is the
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
- **✅ RESOLVED 2026-08-02 — the data-only voice (investigation done, see §Data-only voice below).**
  Original note kept for context: Rests today produce NO voice on the wire. Find where rest
  events are dropped (event→`VoiceData` mapping in
  `queryEvents`/`toVoiceData`/scheduling filter) and thread a "data-only event" through: it must (1) survive to
  `VoiceData` (no sound, no freq), (2) be scheduled, (3) be consumed by the scheduler at `startTime`, (4) NEVER reach
  synthesis (`VoiceFactory`). This is the one genuinely new concept in the whole feature — everything else is the
  pipeline playbook. Prototype this first; if it's ugly, the fallback is a dedicated wire message stream
  (`Cmd.ScheduleMasterChange`)
  — but prefer the voice path (one stream, one ordering, live-update dedup for free).

### Data-only voice — RESOLVED (investigation 2026-08-02)

Four findings from the code, and the design they force:

1. **Rests emit literally nothing.** `~` → `MnNode.Rest` → `applyMods(silence, mods)`
   (`MnPatternToSprudelPattern.kt:90`), and `silence = EmptyPattern` whose
   `queryArcContextual` returns `emptyList()`. So `"~".master(...)` **cannot** work as sketched — there is no event to
   attach data to. → **Carrier is `AtomicPattern.pure`** (`AtomicPattern.kt:33` — one event per cycle with empty
   `SprudelVoiceData`), reinterpreted to carry the master ref. Same *user-facing* idea, working primitive.
2. **`queryEvents` filters nothing** (`KlangPlaybackController.kt:403`) — every pattern event becomes a `ScheduledVoice`
   and crosses the wire. No change needed beyond pre-registration.
3. **⚠️ Sound-less ≠ silent.** `IgnitorRegistry.contains(null)` resolves `null → DEFAULT_SOUND
   ("triangle")` (`IgnitorRegistry.kt:34-36`), so a voice with `sound == null` **would synthesize a triangle**. A
   control event must therefore be **explicitly marked** — "no sound" is not enough. → New wire field **
   `VoiceData.control: Boolean?`** (`loop: Boolean?` is the precedent for a Boolean wire field; KSP codec regenerates,
   hash auto-shifts). KDoc: *engine-level control event, never synthesized*.
4. **The consumption hook already exists.** `VoiceScheduler.promoteScheduled` ends in
   `voiceFactory.makeVoice(...)?.let { active.add(...) }` (`VoiceScheduler.kt:374-383`) — a null result already drops a
   voice silently. Intercept **before** `makeVoice`:
   ```
   head.data.master?.let  { applyMaster(it, atFrame = absoluteStartSec) }
   if (head.data.control == true) continue   // consumed: never synthesized
   ```

**Bonus property:** master rides *any* event, so `note("c3").master(...)` applies the master at that note's onset AND
still sounds the note (control flag unset). The dedicated carrier is just the
"nothing but master" case.

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

---

## Implementation notes (2026-08-02) — where the build deviated from the plan

Recorded so the doc matches the code (all deliberate):

1. **No form- (c) mapper factory.** The plan asked for all four sprudel overload forms. `master(dsl)`
   as a bare factory would collide with the **carrier** of the same signature (the headline surface,
   `stack(lead, bass, master(...))`) — two overloads differing only by return type. Forms (a) `SprudelPattern.master`,
   (b) `String.master` and (d) `PatternMapperFn.master` all exist; the note in `lang_master.kt` records the reason.
2. **Dual-language cases live in `LangMasterSpec`**, not a separate `KlangScriptMasterSpec` — they need a sprudel
   pattern to assert against, so they belong with the other sprudel-side cases.
3. **`MasterDsl.default` is the EMPTY chain**, not "today's limiter". The safety chain (`MasterStage`: brick-wall
   limiter + DC + clip) still runs on the summed mix, so seeding a limiter into the default would put two in series and
   change every existing song. The opt-in
   `MasterFx.limiter()` *defaults* mirror the safety limiter's constants instead (`MasterDefaultsSyncSpec` guards both
   facts).
4. **Swap start is block-quantized** (the fade itself is per-sample). The shared DSP processes buffers from index 0, so
   a sample-accurate start would mean shuffling sub-block segments for a ≤2.7 ms gain against a 60 ms fade. Documented
   in `MasterBus`.
5. **Chains are built at registration, not at swap time** — `MasterChain.build` allocates (Freeverb buffers, delay
   rings) and the swap path runs on the audio thread. Delay rings are also sized to the declared time rather than a
   blanket 4 s. (Review finding: a 3 MB allocation per swap inside the render callback.)
6. **Retarget policy = queue-max-1** (as proposed): a swap arriving mid-fade waits for the running fade instead of
   cutting it. Cutting would drop the outgoing chain at full weight — the exact click the crossfade exists to prevent.
7. **An unknown master name is never latched.** `MasterRegistry.find` returns null (rather than falling back to unity)
   so a late/dropped `RegisterMaster` cannot pin a playback to unity for the rest of its life; the next re-emission of
   the carrier applies it.
8. **`PlaybackEngine.isIdle()` also waits for the master tail** (`MasterBus.isRinging`), so stopping a playback whose
   reverb lives on the master no longer chops the decay.
9. **`control` does not merge** (like `patternId`) — otherwise merging a carrier into a sounding pattern would silence
   real notes.
10. **Control events are filtered out of `VoicesScheduled`** — they are engine config, not notes, and must not appear as
    phantom voices in the UI.

### Actual new files

- `audio_bridge/`: `MasterDsl.kt` (+ `MasterStageDsl`), `MasterValue.kt`, `MasterDslIdentity.kt`
- `audio_be/master/`: `MasterBus.kt`, `MasterChain.kt`, `MasterRegistry.kt`
- `klang/`: `MasterRegistry.kt` (FE send-once tracker)
- `klangscript/stdlib/`: `KlangScriptMaster.kt` (`Master` + `MasterFx`), `KlangScriptMasterFxExtensions.kt`
- `sprudel/lang/`: `lang_master.kt`
- Tests: `audio_be/master/MasterBusTest.kt`, `audio_be/master/MasterDefaultsSyncSpec.kt`,
  `sprudel/lang/LangMasterSpec.kt`, plus master cases in `WireCodecRoundTripSpec.kt`

### Review loop — 5 rounds run (2026-08-02)

Built under the new `/review-loop` standard: coding + audio-engineer reviewers each round, fresh agents, every fix
re-reviewed. Rounds 1–4 each found real defects **in the previous round's fixes** — the loop paid for itself several
times over. Highlights: a multi-MB allocation on the audio thread per swap; a mid-fade swap dropping the outgoing chain
at full weight; an unknown master name latching a playback to unity forever; a cached chain replaying a previous
section's reverb tail; a NaN delay time throwing inside the render callback (killing the worklet); a tail bound that
counted *uptime* instead of *silence*, chopping the master tail of any song longer than 20 s; and a frozen `ringing`
flag leaking one engine per stop. Every new test was mutation-checked (mutate → red → restore).

Round 5 found no CRITICAL/HIGH issues. Two clearly-correct fixes were applied (the tail bound is now derived from
seconds so it no longer varies 20 s browser / 75 s desktop; the master delay cap now matches the orbit bus at 10 s
instead of silently re-timing long echoes). The loop then stopped at the standard's 5-round valve with the items below
parked for a decision.

### Still open — needs a decision

- **Deleting `master(...)` does not restore unity.** `master == null` means "no change", so removing the line leaves the
  last chain in place until the playback stops. **Partly addressed 2026-08-02: `Master.default()`** (KlangScript) /
  `MasterDsl.default` (Kotlin) is now the named, discoverable way to switch a master back off —
  `master(Master.default())`. It swaps to the empty chain through the normal crossfade, so it is click-free and releases
  the engine properly. (The capability already existed as `Master.of()` with no stages; it just had no name.)
  **Still open: true delete-to-undo**, and it is not simply a missing signal. The FE would have to distinguish "the code
  no longer contains `master(...)`" from "this query chunk happens to contain no master event" — and the second is
  legitimate: a sectioned song (`arrange`) has cycles with no master event and must not be reverted. Options, none free:
    1. **Accept it** — `Master.default()` is the documented way back. Zero risk.
    2. **Revert on live update** — on `updatePattern`, reset to unity and let the pattern re-assert. Works for the
       re-emitting top-level carrier, but loses one-shot `note(...).master(...)`
       automation and briefly drops to unity on every keystroke-eval (audible level jump).
    3. **Lookahead detection** — on live update, check whether any master event appears in the already-queried prefetch
       window; revert if the playback had one and now doesn't. Narrows the false-positive window but does not close it
       for long-section arrangements. **User decision** — 1 is the current behaviour and is defensible.
- By-ear pass on `MASTER_XFADE_SECONDS` (0.06) and the v1 effect defaults.
- **Tail bound cuts a long delay without a fade.** At the 20 s bound a genuine (non-runaway) delay — e.g.
  `time(2.0).feedback(0.85)` — can still be ~14 dB down, and disposal removes the engine between two samples: a step,
  not a fade. Options: fade the engine out over the last N blocks before disposal; raise the bound; or accept it. Note
  the orbit path has the same unbounded-tail hole (`feedback >= 1.0` pins `cylinders.anyActive()` forever), so a shared
  fix may be the better move.
- **Cache eviction can rebuild on the audio thread.** Returning to a master last used more than
  `MAX_CACHED_CHAINS` (8) edits ago is a cache miss that rebuilds inside `process()`. Bounded and documented, but a
  live-coding A/B across many edits can hit it. Proper fix needs off-thread building (→ resource-warehouse-pool work).

---

## Reverb parameter parity (2026-08-03)

Triggered by a real symptom: `MasterFx.reverb().wet(0.025).damp(0.9).roomSize(3)` gave a ~12.5 s tail where ~1 s was
meant. Two causes, both now fixed:

1. **`roomSize` was on a 10× different scale.** Sprudel divides `roomsize` by 10 (`VoiceFactory`); the master did not.
   `roomSize(3)` was 0.3 on an orbit but the maximum on the master. Now ONE `Reverb.normalizeRoomSize()` serves both
   buses.
2. **`roomFade`/`roomLp` existed only on the orbit** — and `roomFade` is what actually sets the tail
   (`effectiveSize = roomFade ?: roomSize`). Both are now on the master too, same names, same scales.

Also fixed along the way: the sprudel `roomfade`/`rfade` KDoc claimed **seconds** across 8 overloads with *playable*
examples (`roomfade(2.0)` → comb feedback 1.26); `reverb("…")`'s addon doc contradicted `room("…")`'s; and the
audibility gate on **both** buses tested `roomSize` while the DSP decays from `roomFade ?: roomSize`, which made
`room(0.6).roomfade(0.1)` silent on an orbit.

### The reverted experiment — worth keeping in the record

The first attempt honoured "the engine stays raw, no clamping" by removing the `coerceIn` and soft-capping the comb
feedback instead, with the ceiling as a user param (`rcap`, `MasterFx…cap()`), so out-of-range values would
"self-oscillate rather than diverge". **Two review rounds proved that premise false**, numerically:

- **`softCap` is a rail, not an asymptote.** `fastTanh` hard-clamps for |x| ≥ 3, so `softCap` returns *exactly* ±1.0 for
  |x| ≥ 1.10. In a loop with gain > 1 every sample latches: measured output DC +0.12 with **AC-RMS 0.00000**, both
  channels bit-identical. The `ANTI_DENORMAL` 1e-18 bias alone ramps the network to that rail from silence in ~4 s.
  There is no wash above unity — only DC.
- **A cap at 1.0 is not transparent.** A comb's internal gain is `1/(1−feedback)` — 6.25× at
  `rsize(5)`, 50× at `rsize(10)` — which is why `FIXED_GAIN = 0.015` exists. The comb *state*
  routinely exceeds the 0.95 knee at ordinary settings: measured −3…−4 dB and level-dependent squash on
  `IrishLamentTechno.kt:218` (`.room(0.7).rsize(10)`).
- **Raising the cap made things quieter.** The DC pedestal scales with the ceiling, the master limiter then ducks the
  whole mix, and the DC blocker strips the pedestal itself.

**Conclusion (user decision):** clamp `roomSize`/`roomFade` to 0..1. This is not a taste clamp — above unity a Freeverb
network has no sound to preserve. The bound and its reasoning live in
`Reverb.normalizeRoomSize`'s KDoc so nobody "fixes" it back.

**The delay keeps its `cap`** (`delaycap`/`dcap`, `MasterFx.delay().cap()`): there the soft-cap was already in place,
and `feedback >= 1.0` genuinely self-oscillates as audio.

### Guards added (all mutation-checked)

`MasterOrbitReverbParitySpec` is the one that would have caught the original bug — it drives the **real** production
path on each side (`VoiceFactory` vs `MasterChain.build`) and asserts they agree. Plus `ReverbStabilitySpec` (scale
bound + no DC pedestal), scale/None-finite/stage-drop cases in
`MasterChainSpec`, the default-equivalence guard in `MasterDefaultsSyncSpec`, `LangFeedbackCapSpec`
(delay cap incl. form- (d) and an explicit merge assertion — the merge helpers were provably untested), and the new
fields in both wire round-trip specs.

### Open

- **By ear**: Der Schmetterling and `ATruthWorthLyingFor` keep `roomSize(3)`; both tails go 12.5 s → ~1.0 s, so `wet`
  (0.025 / 0.01) was dialled against the old wash and needs raising — start ~0.05–0.1. Optionally swap Schmetterling's
  `damp(0.9)` for `roomLp(12000)`.
- **Round 2 of `/review-loop` on the revert itself has not been run** — the revert and the doc/gate fixes are, by the
  standard's own rule, unreviewed changes.
- `roomDim` / `iResponse` remain stored-but-unread on both paths (`Reverb.kt` TODO).
- Freeverb's ~0.71 s tail floor (`FEEDBACK_OFFSET`) — nothing on either bus can go shorter.
