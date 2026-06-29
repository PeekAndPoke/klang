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

## Status — end of Q2 2026 (current)

**Foundation DONE + browser-confirmed.** D1, D2, D3 (folded into D2) and D5 are complete and committed;
single-playback audio is identical and the orbit-collision bug is fixed. Two things landed beyond the
original plan:

- **`AudioBackendContext` + `BackendClock`** (call it "D2·d"): all shared backend state (sample store,
  registries, IPC link, config, the one read-only clock) aggregated into one injected context;
  `VoiceScheduler.Options` collapsed to `(context, cylinders)`. The shared clock fixed the
  **first-notes-lost** regression (a fresh engine computed its epoch against the backend *start* time instead
  of *now*) — resolves the first-note half of Open Q1.
- **FE/BE state-placement review** (own thread; memory `project_fe_be_state_placement`): the frontend now
  mirrors the backend's granularity. Shipped — FE↔BE **clock offset → `KlangPlayer`** (`BackendClockSync`; it
  was dead per-controller → UI drift); **custom oscs/engines → per-playback** (BE registry forks + FE
  registries on `KlangPlaybackController` — fixes a registry leak); dead-message cleanup
  (`ScheduleVoice.clearScheduled` removed; `Cmd.ScheduleVoice`/`ClearScheduled` kept + documented); the
  `SampleRequest` universal-key invariant KDoc.

**D5 was done SIMPLER than §D below:** emission moved to the dispatcher (`emitDiagnostics`, runs every block
→ emits zeros when idle, fixing the stuck gauges), but kept the **flat** `Diagnostics` (`activeVoiceCount` +
`cylinders`) *aggregated across engines*. The `PlaybackEngineStats` / `engines: List` reshape in §D was NOT
adopted (unnecessary). Guard: `PlaybackEngineDispatcherDiagnosticsTest`.

**Remaining:**

- **D6 — the thin master path** (§H): the actual original goal (`Song.master` → `Cmd.SetMaster` → per-engine
  gain). NOT started — the natural next feature.
- **D4 — cylinder eviction:** still open, now **folds into the warehouse pool** (memory
  `project_resource_warehouse_pool`) — a self-balancing pool for the 7.68 MB delay rings + cylinders that also
  kills the first-note **allocation** hiccup (the cylinder half of Open Q1). Scheduled **last**.
- Deferred: crossfade, metering, master UI, `EngineDsl`→`PipelineDsl` rename, worklet clock divergence.

## Decisions (locked)

1. **`PlaybackEngine`** = per-`playbackId` instance owning **all per-playback state** (scheduled heap,
   active voices, solo/mute, epoch, forked ignitor registry, its own `Cylinders` + `master`).
   Effectively *what `VoiceScheduler` becomes* once global state is hoisted out. (Name over
   `PlaybackProcessor` — avoids the worklet `AudioWorkletProcessor`/`process()` clash.)
2. **`PlaybackEngineDispatcher`** (host, commonMain) sits in front: owns the `Map<String, PlaybackEngine>`
    + global resources + final mix; **lazy-creates** an engine for an unknown `playbackId`; **centralizes
      `Cmd` dispatch** (currently duplicated between `JvmAudioBackend.kt:114-148` and
      `KlangAudioWorklet.kt:121-148`). Platform backends become thin pumps.
3. **Accept the "engine" naming collision for now.** `EngineDsl` is really a per-voice *pipeline*; the
   rename `EngineDsl` → `PipelineDsl` is **deferred** (do not churn the in-flight
   `engine-dsl-osc-dsl-parameterization` branch). Memory note `engine_dsl_misnamed` recorded.
4. **Shared resources** (in the dispatcher): the **`SampleStore`** sample cache — the one real
   extraction, because `Cmd.Sample` uploads are SYSTEM-wide and PCM is MBs — plus the backend clock
   (one timeline), the `IgnitorRegistry` *parent* (per-engine fork stays), the `EngineRegistry`
   (per-engine fork deferred to the EngineDsl branch), and the final stage **`MasterStage`** (limiter +
   DC + clip) run **once** on the summed mix. `VoiceFactory` and per-block scratch are **per-engine** (#5).
5. **Each engine owns its full render state** — its own `RenderContext` + scratch
   (`voiceBuffer`/`freqModBuffer`/`ScratchBuffers`) + `VoiceFactory`. **No shared `RenderContext`, no
   per-voice pointer-swap.** Sharing scratch across engines was rejected: it forces sequential rendering
   and breaks future multi-threading (one deck per core). Cost is negligible (~30–50 KB/engine vs the
   **7.68 MB** per-cylinder `DelayLine` ring — and cylinders are per-engine regardless). The only shared
   transient is the dispatcher's **mixdown scratch** (#11), touched only in the ≥2 path. Per-voice
   stateful objects (`Oversampler` anti-alias history `CrushRenderer.kt:48`/`CoarseRenderer.kt:30`,
   filters, envelopes, delay lines) stay per-voice as before.
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
11. **Single-playback fast path + one shared mixdown scratch.** The final mixdown is `Σ engine
    contributions`, but engines do **not** own a `bus`. With **one** active engine it renders **straight into
    the final `mix`** — no extra buffer, no sum, cost-identical to today. With **≥2** (crossfade/layering)
    each engine renders into **one shared mixdown scratch** (reused across engines, safe because they render
    sequentially and we accumulate immediately) which is added into `mix`. Per-engine metering/master reads
    the contribution right after `renderInto`, before it is summed/overwritten.

## Architecture (target)

```
PlaybackEngineDispatcher (host, singleton, commonMain)
  global:  SampleStore(cache) · backend clock · VoiceFactory · scratch singletons
           IgnitorRegistry(parent) · EngineRegistry · KlangAudioRenderer(limiter+DC+clip)
  handles: KlangCommLink.Cmd  (lazy-create engine on unknown pid; route; Cmd.Cleanup→drain→dispose)
  renders: 1 engine → renderInto(mix) directly  |  N engines → each renderInto(scratch); mix += scratch
           mix → KlangAudioRenderer(safety limiter → DC → clip) → out
  ├─ PlaybackEngine[A]   scheduled·active·solo·epoch·ignitorReg(fork)·Cylinders(lazy)·master
  ├─ PlaybackEngine[B]   …
  └─ …
platform backend (Jvm / JsWorklet) = thin: pump ring buffer → dispatcher.handle(cmd); dispatcher.renderBlock(out)
voice = Ignitor + EngineDsl(→Pipeline) → Cylinder (in its PlaybackEngine)
```

## Resource scope (global vs per-engine)

| Resource                                                   | Scope                           | Why                                                |
|------------------------------------------------------------|---------------------------------|----------------------------------------------------|
| Sample cache (PCM)                                         | **global** (dispatcher)         | large, shared across playbacks                     |
| `ScratchBuffers`/`voiceBuffer`/`freqMod` + mixdown scratch | **global singletons**           | per-block transient; engines render sequentially   |
| backend clock / start time / cursor frame                  | **global**                      | one audio timeline                                 |
| `VoiceFactory`                                             | **global**                      | stateless factory                                  |
| `IgnitorRegistry`                                          | global **parent** + engine fork | defaults shared; per-playback custom osc (exists)  |
| `EngineRegistry`                                           | **global** (fork later)         | per-engine fork deferred to EngineDsl branch       |
| limiter + DC blockers                                      | **global** (final stage)        | safety brick on the summed output                  |
| scheduled heap · active voices · solo/mute                 | **per engine**                  | per-playback timeline & mute                       |
| epoch                                                      | **per engine**                  | per-playback time origin (exists in `PlaybackCtx`) |
| `Cylinders` (orbits + FX) · `master`                       | **per engine** (lazy cylinders) | isolation (the bug fix); mixdown buffer is shared  |
| `Oversampler`, filters, envelopes, delay lines             | **per voice**                   | inter-block state — not shareable                  |

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

### A. `PlaybackEngine` (per playbackId) — composition

`PlaybackEngine` is a thin aggregate: **`{ VoiceScheduler(one pid) + Cylinders(lazy) + master }`** (no `bus`
— the mixdown buffer is the dispatcher's, see #11). `VoiceScheduler` keeps its identity and tests — it becomes
**single-playback-scoped**, which *removes* machinery rather than adding it:

- `playbackContexts: Map<String, PlaybackCtx>` → gone; `epoch` + forked `ignitorRegistry` become plain fields.
- `ActiveVoice.playbackId` → gone (every active voice here is this playback).
- `cleanup()/clearScheduled()/replaceVoices()` → drop the `playbackId` param; the **dispatcher** routes each
  `Cmd` to the right engine's scheduler.

The engine owns `cylinders: Cylinders` (lazy; **not** `preallocateAll`'d) and `master: MasterDsl` (default
unity) — but **no `bus`**. Global deps (sample store, scratch, clock, `VoiceFactory`, ignitor-registry parent)
are **injected** by the dispatcher — the scheduler does not own scratch (decision #5).
`renderInto(target, cursorFrame)` writes the engine's stereo output into the **caller's** buffer (the final
`mix` for one engine, or the shared mixdown scratch for ≥2 — see #11): `cylinders.clearAll()` →
`scheduler.process(cursorFrame)` (renders its voices into its cylinders) → `cylinders.processAndMix(target)` →
(v1) apply `master` gain to `target`. Diagnostics *emission* moves out of `process()` up to the dispatcher (D).

### B. `PlaybackEngineDispatcher` (host)

- Owns `Map<String, PlaybackEngine>` + global resources (see Resource scope table) + `KlangAudioRenderer`
  (narrowed to the final master/output stage: limiter + DC + clip + interleave).
- `handle(cmd)` — the single dispatch (replaces the duplicated `when(cmd)`):
    - voice/sample/register cmds → resolve/create the engine for `cmd.playbackId`, delegate.
    - `Cmd.SetMaster` → `engine.master = cmd.dsl`.
    - `Cmd.Cleanup` → mark draining (see E).
    - sample uploads / registrations → global stores.
- `renderBlock(out)` — `mix.clear()`, then the #11 fast path: **1** active engine → `renderInto(mix)`
  directly; **≥2** → for each, `mixdownScratch.clear(); renderInto(mixdownScratch); mix += mixdownScratch`.
  Then `renderer.finalize(mix, out)` (limiter → DC → clip). Diagnostics emitted here (see D).

### C. Voice → cylinder routing seam

`SendRenderer.kt:27` reads `ctx.renderContext.cylinders` (the only `getOrInit` caller). With composition (A),
each engine's `VoiceScheduler` builds **its own** `RenderContext` once, fixed to that engine's `Cylinders` and
holding the **shared** scratch arrays by reference. Because engines render **sequentially**, sharing the
scratch arrays is safe and **no per-voice pointer swap is needed** — each scheduler simply renders into its own
cylinders. That is the whole routing change.

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

- `audio_be/.../voices/PlaybackCtx.kt` → fields fold into the (now single-pid) `VoiceScheduler` as plain
  fields; file likely removed.
- `audio_be/.../voices/VoiceScheduler.kt` → becomes **single-playback** (shed `playbackContexts`,
  `ActiveVoice.playbackId`, `playbackId` params); receives globals injected; stops *emitting* diagnostics
  (exposes stats instead). Kept as a unit (and its tests).
- **new** `audio_be/.../voices/PlaybackEngine.kt` — aggregate `{ VoiceScheduler + Cylinders + master }`
    + `renderInto(target, cursorFrame)` (writes into the caller's buffer — no per-engine bus).
- **new** `audio_be/.../PlaybackEngineDispatcher.kt` — host: engine map, global resources, `handle(cmd)`,
  `renderBlock(out)`, diagnostics.
- `audio_be/.../cylinders/Cylinders.kt` — Tier-2 eviction; drop global-`preallocateAll` assumption.
- `audio_be/.../cylinders/Cylinder.kt` — blocks-since-deactivated counter / idle timer.
- `audio_be/.../KlangAudioRenderer.kt` — narrow to final master/output stage (limiter+DC+clip); the sum is
  the dispatcher's job.
- `audio_be/.../WarmupRunner.kt` — single warmup engine; drop 255-cylinder pre-alloc.
- `audio_be/.../Voice.kt` (RenderContext) — one `RenderContext` per engine (own cylinders + shared scratch
  refs); no swappable pointer needed.
- `audio_bridge/.../infra/KlangCommLink.kt` — `Diagnostics` reshape + `PlaybackEngineStats`; new
  `Cmd.SetMaster`.
- **new** `audio_bridge/.../MasterDsl.kt` — `@WireFormat` master config (minimal).
- `src/commonMain/kotlin/Song.kt` — add `master: MasterDsl`.
- `klang/.../KlangPlaybackController.kt` (+ frontend ctrl) — send `Cmd.SetMaster` on play.
- `audio_be/src/jvmMain/.../JvmAudioBackend.kt`, `audio_jsworklet/.../KlangAudioWorklet.kt` — thin pumps;
  remove the duplicated `when(cmd)`.

## Deliverables (independently shippable + verifiable)

Each deliverable lands on its own branch, keeps the suite green, and has **both a unit gate and a human
gate**. Ordered so every step is behaviour-neutral or purely additive — nothing degrades single-playback
audio until D6 (and then only when a non-unity `Song.master` is set).

### D1 — Centralize Cmd dispatch · *behaviour-neutral refactor*

- **Scope:** new `PlaybackEngineDispatcher` shell owns the *existing* single `VoiceScheduler` + `Cylinders`
  and exposes `handle(cmd)` + `renderBlock(out)`; both platform backends delegate to it. Removes the
  duplicated `when(cmd)` (`JvmAudioBackend.kt:114-148` ≈ `KlangAudioWorklet.kt:121-148`).
- **Unit:** dispatcher test — each `Cmd` subtype routes to the expected scheduler method (spy/fake scheduler).
- **Human:** JVM app + browser — play / stop / update / register-ignitor behave exactly as before.
- **Done when:** no `when(cmd)` in either backend; dispatcher is the single dispatch site; suite green.
- **Status:** ✅ landed + committed. Review fixes applied: test drives `create()`, dead `cylinders` dropped,
  `handle` is an exhaustive expression, `KlangOfflineRenderer` reuses `create()`.

### D2 — Per-engine `PlaybackEngine` + dispatcher map · *the core; single-playback identical*

- **Scope:** dispatcher hoists globals (sample store, scratch singletons, clock, `VoiceFactory`) and
  **lazy-creates** one `PlaybackEngine = { VoiceScheduler(one pid) + Cylinders(lazy) }` per pid (`master`
  added in D6); mixes down via the **#11 fast path** (1 engine → `renderInto(mix)`; ≥2 → one shared mixdown
  scratch) → `KlangAudioRenderer` (limiter+DC+clip). `VoiceScheduler` sheds its playbackId machinery (A).
  Warmup → one warmup engine; **drop global `preallocateAll`**; resolve the first-note hitch (small keep-warm
  orbit pool, or accept lazy + measure).
- **Unit:** adapted single-pid `VoiceScheduler` tests green; **isolation spec** — two engines (two pids) on
  the same orbit id keep independent reverb/delay/phaser/comp state.
- **Human:** a single song is bit-for-bit identical (one engine == old path); two overlapping songs on the
  same orbit stay clean; first note of a multi-orbit song is glitch-free; browser memory sane (**answers Open Q1**).
- **Done when:** isolation spec passes; single-song listen identical; no `preallocateAll`.
- **Status:** ✅ done + committed + browser-confirmed (single song identical, isolation holds). Extended by
  **D2·d** = `AudioBackendContext` + `BackendClock` (see the Status banner). Sub-steps: D2·b·1 (PlaybackEngine
    + single-engine dispatcher render), D2·b·2 (`Map<pid,PlaybackEngine>` + lazy `engineFor` + per-pid routing +
      additive mixdown), D2·c (warmup on the dispatcher, dropped `preallocateAll`). The #11 mixdown relies on
  `Cylinders.processAndMix` accumulating (no scratch yet — the shared scratch lands in D6 with per-engine
      master gain). **D3's drain-dispose was folded in here.**

### D3 — Explicit cleanup + drain lifecycle · *behaviour change: disposal* · ✅ FOLDED INTO D2·b·2

- **Scope:** engine created lazily on first `Cmd` for an unknown pid; `Cmd.Cleanup` → drain (stop scheduling,
  ring out, dispose once `active` empty **and** all cylinders inactive); `cleanupHard` immediate; **no
  auto-GC**; pause just withholds scheduling.
- **Status:** ✅ implemented in D2·b·2 — `draining` set + `disposeDrainedEngines()` + `PlaybackEngine.isIdle()`;
  `engineFor()` removes a pid from `draining` so a `Cleanup`→re-`Schedule` cancels the pending disposal.

### D4 — Cylinder eviction (tier 2) · *memory*

- **Scope:** `Cylinder` tracks idle-since-deactivation; `Cylinders.processAndMix` step 4 evicts from
  `id2cylinder` after `EVICT_AFTER_SECONDS` (generous, tunable); `getOrInit` reconstructs on next touch.
- **Unit:** `CylindersCleanupTest` — evict after the timeout; rhythmic re-trigger resets the timer (no
  thrash); rebuild on next `getOrInit`.
- **Human:** an orbit used only in an intro frees after the timeout (memory drops); a steady 4-on-floor orbit
  never evicts (no glitch); after stop, `activeCylinderCount` → 0 and RSS falls.
- **Done when:** eviction spec passes; measured memory drops for idle orbits; no thrash artifact by ear.

### D5 — Diagnostics reshape · *observable; UI-neutral*

- **Scope:** `PlaybackEngineStats(playbackId, activeVoiceCount, cylinders)` + `engines: List<…>` on
  `Diagnostics`; computed convenience getters; built + emitted by the dispatcher.
- **Unit:** `VoiceSchedulerDiagnosticsTest` — per-engine list correct; `activeVoiceCount`/`activeCylinderCount`
  aggregates equal the old single-engine values for one playback.
- **Human:** `PlayerMiniStats` gauges read correctly with one engine and with two simultaneous playbacks.
- **Done when:** diagnostics spec passes; gauges visually unchanged for one playback.
- **Status:** ✅ done — but SIMPLER than the §D design above (see Status banner). Emission moved to
  `PlaybackEngineDispatcher.emitDiagnostics` (runs every block → emits zeros when idle, fixing the gauges
  that froze on stop); kept the **flat** `Diagnostics` aggregated across engines. `PlaybackEngineStats` /
  `engines: List` was NOT adopted. Guard: `PlaybackEngineDispatcherDiagnosticsTest`.

### D6 — Thin master path · *new feature; default no-op*

- **Scope:** `MasterDsl(gain=1.0)` (`@WireFormat`) in `audio_bridge`; `Cmd.SetMaster(playbackId, MasterDsl)`;
  `Song.master` (default unity); frontend sends `Cmd.SetMaster` on play before the first voices;
  `PlaybackEngine` applies `master.gain` to its bus.
- **Unit:** master spec — `SetMaster` gain scales only that engine's bus; unity == unchanged output.
- **Human:** set `Song.master` gain on a built-in song → audibly quieter/louder, **no UI**; a second song at
  a different gain is independent.
- **Done when:** master spec passes; gain is audible end-to-end via `Song`.

## Cross-cutting checks (every deliverable)

- Full suite green; run one spec via `--tests` + UNQUOTED FQCN (no quotes/wildcards).
- After each structural step: in-browser smoke — play a built-in song, stop, update, replay.
- Single-playback audio stays identical through D5; only D6 (non-unity `Song.master`) changes the sound.

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
