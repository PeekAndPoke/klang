# Per-Playback Engine — DSP isolation layer (foundation for master / loudness)

## Context

The whole audio pipeline ends in a single **hardcoded** master limiter (`KlangAudioRenderer.kt:26-38`);
there is no mastering/loudness surface, and the per-pattern/per-bus model of sprudel offers no natural
home for **session-global** settings. Before a master stage can exist, a structural gap must be closed.

Today the backend is **one singleton** `{VoiceScheduler + Cylinders + KlangAudioRenderer + limiter}`
(`JvmAudioBackend.kt:34-60`, `KlangAudioWorklet.kt:40-66`). All playbacks mix into one `Cylinders`, one
limiter, one output. This has three consequences:

1. **Latent bug — cylinders collide across playbacks.** `Cylinders.id2cylinder` is keyed purely by orbit
   id (`Cylinders.kt:24`); `Cylinder.updateFromVoice` is last-writer-wins (`Cylinder.kt:98-160`); reverb/
   delay tails persist in the buffers. Two songs that both use orbit 0 already corrupt each other's FX.
2. **No home for master settings.** They are session-global, want to live where RPM already lives
   (`Song.kt` metadata, not pattern data), and must not travel on every voice.
3. **Crossfade is impossible.** Two songs with different master settings cannot pass through one shared
   master chain.

The fix: a per-`playbackId` **`PlaybackEngine`** that owns its scheduling + DSP state, sitting behind a
**`PlaybackEngineDispatcher`** that holds the global resources and the final mix. The backend is already
**half-built** for this — `playbackId` is threaded through every `Cmd`, and the scheduler already keeps
`playbackContexts: Map<String, PlaybackCtx>` (`VoiceScheduler.kt:403-415`).

**This doc covers the foundation only.** A *thin* master path (Song → `Cmd.SetMaster` → per-engine output
gain) is in scope to prove the wiring; the rich master chain (glue/eq/drive/ceiling), crossfade, metering,
UI, and the `EngineDsl` rename are deferred (see "Deferred phases"). The foundation fixes the collision
bug and is **behaviour-identical** for today's single-playback case.

## Decisions (locked)

1. **`PlaybackEngine`** = per-`playbackId` instance owning **all per-playback state** (scheduled heap,
   active voices, solo/mute, epoch, forked ignitor registry, its own `Cylinders`, `bus`, `master`).
   Effectively *what `VoiceScheduler` becomes* once global state is hoisted out. (Name over
   `PlaybackProcessor` — avoids the worklet `AudioWorkletProcessor`/`process()` clash.)
2. **`PlaybackEngineDispatcher`** (host, commonMain) sits in front: owns the `Map<String, PlaybackEngine>`
    + global resources + final mix; **lazy-creates** an engine for an unknown `playbackId`; **centralizes
      `Cmd` dispatch** (currently duplicated between `JvmAudioBackend.kt:114-148` and
      `KlangAudioWorklet.kt:121-148`). Platform backends become thin pumps.
3. **Accept the "engine" naming collision for now.** `EngineDsl` is really a per-voice *pipeline*; the
   rename `EngineDsl` → `PipelineDsl` is **deferred** (do not churn the in-flight
   `engine-dsl-osc-dsl-parameterization` branch). Memory note `engine_dsl_misnamed` recorded.
4. **Global resources stay global** (in the dispatcher): sample cache, backend clock, `VoiceFactory`,
   shared per-block scratch (`ScratchBuffers` + `voiceBuffer` + `freqModBuffer`), the `IgnitorRegistry`
   *parent* (per-engine fork stays), the `EngineRegistry` (per-engine fork deferred to the EngineDsl
   branch), and the final master/output stage (`KlangAudioRenderer`: limiter + DC + clip).
5. **Shared workspace vs stateful** — the line is *inter-block state*. Per-block transient buffers are
   backend **singletons** reused across engines (engines render **sequentially** within a block).
   Anything stateful across blocks stays per-voice/per-engine: filters, envelopes, delay lines, and
   **`Oversampler`** (anti-alias filter history, per voice — `CrushRenderer.kt:48`/`CoarseRenderer.kt:30`;
   NOT shareable).
6. **Cylinders allocate LAZILY per engine** (`getOrInit`); **never `preallocateAll` per engine.** Only one
   warmup engine JITs the render path.
7. **Two-tier cylinder lifecycle.** `tryDeactivate` already flips `isActive=false` (stops CPU); add a
   second tier: **evict** (remove from `id2cylinder` → free the ~7.68 MB delay ring) after a *generous*
   idle timeout. Deactivate fast, evict slow (anti-thrash). This is what reclaims memory for paused engines.
8. **Engine disposal is EXPLICIT (`Cmd.Cleanup`), never auto-GC.** A paused playback is indistinguishable
   from a stopped one; idle-GC would dispose it and force a master/warmup resend on resume. Pause = withhold
   scheduling; the engine (with its `master`) stays alive, its heavy buffers freed by cylinder eviction (#7).
9. **Thin master path in scope.** `Song.master: MasterDsl` (default unity) → on play the frontend sends
   `Cmd.SetMaster(playbackId, MasterDsl)` before the first voices → engine applies it (v1 = output gain on
   its bus). Rich chain + live UI deferred.
10. **Diagnostics carries per-engine stats**; UI-facing aggregates survive as computed convenience.

## Architecture (target)

```
PlaybackEngineDispatcher (host, singleton, commonMain)
  global:  SampleStore(cache) · backend clock · VoiceFactory · scratch singletons
           IgnitorRegistry(parent) · EngineRegistry · KlangAudioRenderer(limiter+DC+clip)
  handles: KlangCommLink.Cmd  (lazy-create engine on unknown pid; route; Cmd.Cleanup→drain→dispose)
  renders: for each engine → engine.renderInto(engine.bus, cursorFrame)
           Σ engine.bus → KlangAudioRenderer(safety limiter → DC → clip) → out
  ├─ PlaybackEngine[A]   scheduled·active·solo·epoch·ignitorReg(fork)·Cylinders(lazy)·bus·master
  ├─ PlaybackEngine[B]   …
  └─ …
platform backend (Jvm / JsWorklet) = thin: pump ring buffer → dispatcher.handle(cmd); dispatcher.renderBlock(out)
voice = Ignitor + EngineDsl(→Pipeline) → Cylinder (in its PlaybackEngine)
```

## Resource scope (global vs per-engine)

| Resource                                       | Scope                           | Why                                                |
|------------------------------------------------|---------------------------------|----------------------------------------------------|
| Sample cache (PCM)                             | **global** (dispatcher)         | large, shared across playbacks                     |
| `ScratchBuffers` / `voiceBuffer` / `freqMod`   | **global singletons**           | per-block transient; engines render sequentially   |
| backend clock / start time / cursor frame      | **global**                      | one audio timeline                                 |
| `VoiceFactory`                                 | **global**                      | stateless factory                                  |
| `IgnitorRegistry`                              | global **parent** + engine fork | defaults shared; per-playback custom osc (exists)  |
| `EngineRegistry`                               | **global** (fork later)         | per-engine fork deferred to EngineDsl branch       |
| limiter + DC blockers                          | **global** (final stage)        | safety brick on the summed output                  |
| scheduled heap · active voices · solo/mute     | **per engine**                  | per-playback timeline & mute                       |
| epoch                                          | **per engine**                  | per-playback time origin (exists in `PlaybackCtx`) |
| `Cylinders` (orbits + FX) · `bus` · `master`   | **per engine** (lazy cylinders) | isolation (the bug fix) + per-deck output          |
| `Oversampler`, filters, envelopes, delay lines | **per voice**                   | inter-block state — not shareable                  |

## Current state (grounded)

| Concern                     | Where                                                                                                         | Note                                                                                                 |
|-----------------------------|---------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Singleton wiring            | `JvmAudioBackend.kt:34-60`, `KlangAudioWorklet.kt:40-66`                                                      | one `Cylinders`, one `VoiceScheduler`, one renderer                                                  |
| Cmd dispatch (DUPLICATED)   | `JvmAudioBackend.kt:114-148` ≈ `KlangAudioWorklet.kt:121-148`                                                 | identical `when(cmd)` except `voices.` vs `ctx.voices.` → centralize in dispatcher                   |
| Render loop                 | `KlangAudioRenderer.kt:68-129`                                                                                | `cylinders.clearAll()` → `voices.process()` → `cylinders.processAndMix(mix)` → limiter → DC → clip   |
| Voice→cylinder routing seam | `SendRenderer.kt:27`                                                                                          | `ctx.renderContext.cylinders.getOrInit(voice.cylinderId, voice)` — **only** `getOrInit` caller       |
| VoiceScheduler state        | `VoiceScheduler.kt:64-157`                                                                                    | per-pid (scheduled/active/solo/epoch/ctx) + global (samples/clock/scratch/factory) intermixed        |
| Per-playback ctx            | `PlaybackCtx.kt`, `VoiceScheduler.kt:403-415` (`ensureEpoch`)                                                 | epoch + forked ignitor registry; created lazily per pid                                              |
| Active voices tagged by pid | `VoiceScheduler.kt:71-76` (`ActiveVoice.playbackId`), render loop `:343-363`                                  | enables per-pid grouping & cleanup                                                                   |
| Cylinder deactivation       | `Cylinder.tryDeactivate()` `:206-228`; round-robin in `Cylinders.processAndMix` step 4 `:109-123`             | silence grace `silentBlocksBeforeTailCheck=10` + delay/reverb tail check; **never removed from map** |
| cleanup semantics           | `VoiceScheduler.cleanup` `:249-252` (removes ctx + scheduled, lets active ring out), `cleanupHard` `:259-262` |                                                                                                      |
| Diagnostics emit            | `VoiceScheduler.process()` `:377-396`                                                                         | built from `options.cylinders.cylinders` + `active.size`; under `SYSTEM_PLAYBACK_ID`, every ~20ms    |
| Diagnostics type            | `KlangCommLink.Feedback.Diagnostics` `:171-210`                                                               | flat `activeVoiceCount: Int` + `cylinders: List<CylinderState>`                                      |
| Song metadata               | `Song.kt` (`io.peekandpoke.klang`)                                                                            | `{id, title, rpm, code, icon}` — add `master`                                                        |
| Memory per cylinder         | `Cylinder.kt:37-38` → `DelayLine.kt:61-62`                                                                    | eager `10s × sr × 2ch × 8B` ≈ **7.68 MB** delay ring (+ ~216 KB reverb)                              |
| Warmup                      | `WarmupRunner.start()` `:59-111`, `preallocateCylinders()` `KlangAudioRenderer.kt:64-66`                      | **unconditionally** pre-allocates all 255 cylinders (on paper ~1.9 GB — see Open Q1)                 |

## Design

### A. `PlaybackEngine` (per playbackId)

Absorbs `PlaybackCtx` + the per-playback half of `VoiceScheduler`. Owns:

- `scheduled: KlangMinHeap<ScheduledVoice>`, `active: ArrayList<ActiveVoice>`, solo state
  (`soloMuteRamp`, `SoloSourceTracker`) — moved verbatim from `VoiceScheduler`.
- `epoch`, `ignitorRegistry` (fork) — existing `PlaybackCtx` fields.
- `cylinders: Cylinders` (constructed lazily; **not** `preallocateAll`'d), `bus: StereoBuffer(blockFrames)`.
- `master: MasterDsl` (from `Cmd.SetMaster`; default unity).
- Methods: `schedule/replace/clear`, `promote`, `renderInto(bus, cursorFrame)` =
  `cylinders.clearAll()` → render its active voices into its cylinders → `cylinders.processAndMix(bus)`
  → (v1) apply `master` gain to `bus`.
  Global deps (sample store, scratch, clock, voiceFactory, registry parent) are injected by the dispatcher.

### B. `PlaybackEngineDispatcher` (host)

- Owns `Map<String, PlaybackEngine>` + global resources (see Resource scope table) + `KlangAudioRenderer`
  (narrowed to the final master/output stage: limiter + DC + clip + interleave).
- `handle(cmd)` — the single dispatch (replaces the duplicated `when(cmd)`):
    - voice/sample/register cmds → resolve/create the engine for `cmd.playbackId`, delegate.
    - `Cmd.SetMaster` → `engine.master = cmd.dsl`.
    - `Cmd.Cleanup` → mark draining (see E).
    - sample uploads / registrations → global stores.
- `renderBlock(out)` — for each engine `renderInto(engine.bus, cursorFrame)`; sum buses into `mix`;
  `renderer.finalize(mix, out)` (limiter → DC → clip). Diagnostics emitted here (see D).

### C. Voice → cylinder routing seam

The shared `RenderContext` carries a `cylinders` pointer; `SendRenderer.kt:27` reads
`ctx.renderContext.cylinders`. Each engine renders with the `cylinders` pointer set to **its own**
`Cylinders` (set once per engine's `renderInto`, not per voice — engines render sequentially). Make
`renderContext.cylinders` a swappable `var`. Single routing change; no per-voice cylinder field.

### D. Diagnostics reshape

Add `PlaybackEngineStats(playbackId, activeVoiceCount, cylinders: List<CylinderState>)` (room later for
peak/rms/gainReduction) and `engines: List<PlaybackEngineStats>` to `Diagnostics` (`KlangCommLink.kt:171`).
Keep UI working via **computed, non-wire** getters (only ctor params serialize in `@WireFormat`):
```kotlin
val activeVoiceCount    get() = engines.sumOf { it.activeVoiceCount }
val activeCylinderCount get() = engines.sumOf { it.cylinders.count { c -> c.active } }
val cylinders           get() = engines.flatMap { it.cylinders }
```

Global fields (`renderHeadroom`, `sampleRate`, latency, `backendNowMs`) stay top-level; message still under
`SYSTEM_PLAYBACK_ID`. Built by the dispatcher from each engine. **Verify the KSP wire codec ignores body
getters** (serializes only constructor params) — see `wireformat-enhancements.md`.

### E. Lifecycle — explicit disposal + drain

- **Create:** lazily, when the dispatcher first sees a `Cmd` for an unknown `playbackId` (constructs the
  engine + its empty `Cylinders`).
- **Dispose:** **only** on `Cmd.Cleanup` → mark draining (stop scheduling, clear scheduled); keep rendering
  so active voices + cylinder tails ring out; remove the engine once `active` is empty **and** all its
  cylinders are inactive. `cleanupHard` (warmup) disposes immediately.
- **No auto-GC.** Pause = the frontend simply stops scheduling without sending `Cleanup`; the engine stays
  (master retained, instant resume). Memory while paused is reclaimed by cylinder eviction (F).
- Today `cleanup` removes the ctx immediately (`:250`) while letting voices ring out — once the engine owns
  the cylinders that would orphan the tails, hence the drain change above.

### F. Cylinder lifecycle — two-tier (deactivate → evict)

- **Tier 1 (exists): deactivate** — `tryDeactivate()` flips `isActive=false` after the silence grace +
  delay/reverb tail check. Stops CPU.
- **Tier 2 (new): evict** — track blocks-since-deactivated (or wall-clock idle); after a **generous**
  `EVICT_AFTER_SECONDS` (tunable, e.g. 4 s of *continuous* inactivity) **remove the cylinder from
  `id2cylinder`** so its `DelayLine`/`Reverb` buffers are freed. `getOrInit` reconstructs on next touch.
- **Anti-thrash (critical):** a 4-on-the-floor kick deactivates ~27 ms after each hit but re-triggers
  ~470 ms later; evicting on deactivation would free+realloc 7.68 MB **every beat**. The evict timer must
  exceed any musical inter-onset gap — a steadily-played orbit never evicts; a finished one frees after the
  timeout. Mechanics: extend `Cylinders.processAndMix` step 4 (round-robin, alloc-free); record the id and
  remove after the loop body (avoid concurrent modification).

### G. Warmup adaptation

Drop per-engine `preallocateAll`. Warmup creates one warmup `PlaybackEngine` (`WARMUP_PLAYBACK_ID`),
schedules the existing synth+sample warmup voices to JIT the path, then `cleanupHard` + `resetPostChain`.
Runtime engines allocate cylinders lazily; the JIT'd path keeps construction cheap. First-note-many-orbits
hitch tracked in Open Q1.

### H. Thin master path (Song → engine)

- `audio_bridge`: `MasterDsl` (`@WireFormat`, v1 minimal: `gain: Double = 1.0`; ceiling/glue/eq later) +
  `MasterDsl.default`.
- `KlangCommLink.Cmd.SetMaster(playbackId, dsl: MasterDsl)` — new command (mirrors `RegisterEngine`).
- `Song.master: MasterDsl = MasterDsl.default` (verify `klang` module can reference the bridge type).
- Frontend: on play, send `Cmd.SetMaster(pid, song.master)` *before* the first `ScheduleVoices`
  (playback start choreography in `KlangPlaybackController`).
- `PlaybackEngine.renderInto` applies `master.gain` to its bus (post-`processAndMix`, pre-sum). The global
  safety limiter stays at the final mix. UI live-edit deferred.

## File-by-file change list

- `audio_be/.../voices/PlaybackCtx.kt` → fold into new `PlaybackEngine` (per-playback state + cylinders +
  bus + master + render).
- `audio_be/.../voices/VoiceScheduler.kt` → split: per-playback logic → `PlaybackEngine`; global bits
  (sample store, scratch, clock, factory) → dispatcher. May dissolve / be renamed.
- **new** `audio_be/.../PlaybackEngineDispatcher.kt` — host: engine map, global resources, `handle(cmd)`,
  `renderBlock(out)`, diagnostics.
- `audio_be/.../cylinders/Cylinders.kt` — Tier-2 eviction; drop global-`preallocateAll` assumption.
- `audio_be/.../cylinders/Cylinder.kt` — blocks-since-deactivated counter / idle timer.
- `audio_be/.../KlangAudioRenderer.kt` — narrow to final master/output stage (limiter+DC+clip); the sum is
  the dispatcher's job.
- `audio_be/.../WarmupRunner.kt` — single warmup engine; drop 255-cylinder pre-alloc.
- `audio_be/.../Voice.kt` (RenderContext) — `cylinders` becomes a swappable `var`.
- `audio_bridge/.../infra/KlangCommLink.kt` — `Diagnostics` reshape + `PlaybackEngineStats`; new
  `Cmd.SetMaster`.
- **new** `audio_bridge/.../MasterDsl.kt` — `@WireFormat` master config (minimal).
- `src/commonMain/kotlin/Song.kt` — add `master: MasterDsl`.
- `klang/.../KlangPlaybackController.kt` (+ frontend ctrl) — send `Cmd.SetMaster` on play.
- `audio_be/src/jvmMain/.../JvmAudioBackend.kt`, `audio_jsworklet/.../KlangAudioWorklet.kt` — thin pumps;
  remove the duplicated `when(cmd)`.

## Implementation order (de-risked, behaviour-neutral first)

1. **Centralize dispatch** — extract the duplicated `when(cmd)` into `PlaybackEngineDispatcher.handle`,
   still backed by the single `VoiceScheduler`/`Cylinders`. Platform backends become pumps. No behaviour change.
2. **Hoist globals** — move sample store / scratch / clock / factory to the dispatcher; thread them in.
3. **Introduce `PlaybackEngine`** owning per-playback state + its own `Cylinders` (lazy); dispatcher
   lazy-creates; render-loop swaps the cylinders pointer (C) + per-engine bus sum (B). One engine ⇒ identical output.
4. **Explicit cleanup + drain** (E). Tests: stop → engine gone after tails; pause (no Cleanup) → engine kept.
5. **Cylinder eviction** (F) with generous timeout. Tests: rhythmic orbit not evicted; finished orbit freed.
6. **Diagnostics reshape** (D) + convenience getters. Update `VoiceSchedulerDiagnosticsTest`.
7. **Thin master path** (H): `MasterDsl` + `Cmd.SetMaster` + `Song.master` + frontend send + per-engine gain.
8. **Warmup adaptation** (G); drop `preallocateAll`. Measure browser memory.

## Test plan

- `cylinders/CylindersCleanupTest.kt` — eviction removes from map after timeout; rhythmic re-trigger resets
  the evict timer (no thrash); reconstruct on next `getOrInit`.
- `voices/VoiceSchedulerDiagnosticsTest.kt` — per-engine `engines` list; aggregates equal old single values.
- New isolation spec — two playbacks on the same orbit id keep independent reverb/delay/phaser/comp state.
- New lifecycle spec — `Cmd.Cleanup` drains then disposes; no Cleanup ⇒ engine retained; master survives.
- New master spec — `Cmd.SetMaster` gain affects only that engine's bus.
- Run one spec: `--tests` + UNQUOTED FQCN (no quotes/wildcards).

## Verification (end-to-end)

- Single song sounds **identical** to before (dispatch centralization + limiter relocation are
  behaviour-neutral) — in-browser listen.
- Two overlapping songs on the same orbit stay clean (no FX bleed) — the bug this fixes.
- After stopping a song (`Cmd.Cleanup`), `Diagnostics.activeCylinderCount` → 0 and memory drops (eviction).
- A song's `Song.master` gain is audible without any UI (proves the Song → `Cmd.SetMaster` → engine path).

## Open questions
1. **Real browser cost of `preallocateAll` / the 10 s delay ring.** Reconcile the on-paper ~1.9 GB with what
   actually runs; measure post-eviction steady state. May motivate right-sizing the delay ring (allocate to
   actual delay time / grow on demand) — the real lever for many decks. Tracks the first-note-hitch tradeoff (G).
2. **Deck budget.** With eviction + explicit cleanup, memory no longer forces a hard cap → default lazy +
   explicit-dispose; soft-cap only if CPU (each active deck renders its full graph) shows strain.

## Deferred phases (after foundation)

1. **MasterChain** — extend `MasterDsl` to gain → glue → eq → drive → ceiling; per-deck creative master.
2. **Crossfade** — per-engine `ValueRamp` gain (reuse the solo/mute ramp `VoiceScheduler.kt:82`) +
   `Cmd.Crossfade(fromId, toId, durationMs)` (sample-accurate ramp in the backend).
3. **Metering** — peak/RMS/gain-reduction into `PlaybackEngineStats` + UI master meters.
4. **UI master panel** + live `Cmd.SetMaster` (debounced like RPM); persist master with the song.
5. **`EngineDsl` → `PipelineDsl` rename** + per-engine `EngineRegistry` fork — coordinate with the in-flight branch.
6. **Delay-ring right-sizing** (memory optimization for many simultaneous decks).
