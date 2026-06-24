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

The backend is already **half-built** for the fix: `playbackId` is threaded through every `Cmd`, and the
scheduler keeps `playbackContexts: Map<String, PlaybackCtx>` (`VoiceScheduler.kt:403-415`, per-playback
epoch + forked ignitor registry). This task **completes** that: a per-`playbackId` **`PlaybackEngine`**
owning its own cylinders + (later) master + crossfade gain.

**This doc covers the foundation only.** Master chain, crossfade, metering, UI, and the `EngineDsl`
rename are deferred (see "Deferred phases"). The foundation fixes the collision bug and is
**behaviour-identical** for today's single-playback case.

## Decisions (locked)

1. **Name = `PlaybackEngine`** for the per-`playbackId` DSP instance. Chosen over `PlaybackProcessor`
   (collides with the worklet `AudioWorkletProcessor`/`process()` vocabulary).
2. **Accept the "engine" naming collision for now.** `EngineDsl` is really a per-voice *pipeline*; the
   rename `EngineDsl` → `PipelineDsl` is **deferred** (do not churn the in-flight
   `engine-dsl-osc-dsl-parameterization` branch). Memory note `engine_dsl_misnamed` to be recorded.
3. **Host stays `KlangAudioRenderer`** — it already owns the render loop *and* the limiter. It gains the
   per-engine collection, sums each engine's bus, then runs the existing limiter + DC + clip (relocated
   to the final sum, behaviour-identical).
4. **`PlaybackEngine` evolves from `PlaybackCtx`** — `PlaybackCtx` is already "created when a playback is
   first seen, destroyed on cleanup" (`PlaybackCtx.kt:11`). Add `cylinders` + a `bus` buffer to it (it
   becomes / is wrapped by `PlaybackEngine`).
5. **Cylinders allocate LAZILY per engine** (`getOrInit`); **never `preallocateAll` per engine.** Only
   one warmup engine JITs the render path.
6. **Two-tier cylinder lifecycle.** Today `tryDeactivate` only flips `isActive=false` (stops CPU) and
   the `Cylinder` (with its ~7.68 MB delay ring) lives in the map forever. Add a second tier: **evict**
   (remove from `id2cylinder` → free the buffers) after a *generous* idle timeout. Deactivate fast,
   evict slow (anti-thrash).
7. **Diagnostics carries per-engine stats**; UI-facing aggregates survive as computed convenience.

## Architecture (target)

```
KlangAudioRenderer (host, singleton)
  shared:  sample cache · EngineRegistry · IgnitorRegistry(parent)
  ├─ PlaybackEngine[A]   Cylinders_A → busA     (later: → MasterChain_A × xfadeGainA)
  ├─ PlaybackEngine[B]   Cylinders_B → busB     (later: → MasterChain_B × xfadeGainB)
  └─ FinalMix   Σ bus → [always-on safety limiter] → DC block → clip → out
voice = Ignitor + EngineDsl(→Pipeline) → Cylinder (in its PlaybackEngine)
```

## Current state (grounded)

| Concern                     | Where                                                                                                         | Note                                                                                                 |
|-----------------------------|---------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Singleton wiring            | `JvmAudioBackend.kt:34-60`, `KlangAudioWorklet.kt:40-66`                                                      | one `Cylinders`, one `VoiceScheduler`, one renderer                                                  |
| Render loop                 | `KlangAudioRenderer.kt:68-129`                                                                                | `cylinders.clearAll()` → `voices.process()` → `cylinders.processAndMix(mix)` → limiter → DC → clip   |
| Voice→cylinder routing seam | `SendRenderer.kt:27`                                                                                          | `ctx.renderContext.cylinders.getOrInit(voice.cylinderId, voice)` — **only** `getOrInit` caller       |
| Per-playback state          | `PlaybackCtx.kt`, `VoiceScheduler.kt:403-415` (`ensureEpoch`)                                                 | epoch + forked ignitor registry; created lazily per pid                                              |
| Active voices tagged by pid | `VoiceScheduler.kt:71-76` (`ActiveVoice.playbackId`), render loop `:343-363`                                  | enables per-pid grouping & cleanup                                                                   |
| Cylinder deactivation       | `Cylinder.tryDeactivate()` `:206-228`; round-robin in `Cylinders.processAndMix` step 4 `:109-123`             | silence grace `silentBlocksBeforeTailCheck=10` + delay/reverb tail check; **never removed from map** |
| cleanup semantics           | `VoiceScheduler.cleanup` `:249-252` (removes ctx + scheduled, lets active ring out), `cleanupHard` `:259-262` |                                                                                                      |
| Diagnostics emit            | `VoiceScheduler.process()` `:377-396`                                                                         | built from `options.cylinders.cylinders` + `active.size`; under `SYSTEM_PLAYBACK_ID`, every ~20ms    |
| Diagnostics type            | `KlangCommLink.Feedback.Diagnostics` `:171-210`                                                               | flat `activeVoiceCount: Int` + `cylinders: List<CylinderState>`                                      |
| Memory per cylinder         | `Cylinder.kt:37-38` → `DelayLine.kt:61-62`                                                                    | eager `10s × sr × 2ch × 8B` ≈ **7.68 MB** delay ring (+ ~216 KB reverb)                              |
| Warmup                      | `WarmupRunner.start()` `:59-111`, `preallocateCylinders()` `KlangAudioRenderer.kt:64-66`                      | **unconditionally** pre-allocates all 255 cylinders (on paper ~1.9 GB — see Open Q1)                 |

## Design

### A. `PlaybackEngine` (evolve `PlaybackCtx`)

Add to the per-playback container:

- `cylinders: Cylinders` — its own orbit pool (constructed lazily; **not** `preallocateAll`'d).
- `bus: StereoBuffer(blockFrames)` — this engine's stereo output, summed into the final mix.
- (existing) `playbackId`, `ignitorRegistry`, `epoch`.
- (later) `master: MasterChain`, `xfadeGain: ValueRamp`.

Either rename `PlaybackCtx` → `PlaybackEngine`, or keep `PlaybackCtx` as internal state wrapped by a
`PlaybackEngine`. Recommended: **rename** — the lifecycle already matches. `ensureEpoch`
(`VoiceScheduler.kt:403`) becomes `ensureEngine`, constructing the `Cylinders` there.

### B. Voice → cylinder routing seam

The shared render context carries a `cylinders` pointer; `SendRenderer.kt:27` reads
`ctx.renderContext.cylinders`. In the render loop (`VoiceScheduler.process()` `:343-363`), each
`ActiveVoice` already knows its `playbackId`. Before `activeVoice.voice.render(ctx)`, **swap**
`ctx.renderContext.cylinders` to that engine's cylinders:

```kotlin
val engine = playbackEngines[activeVoice.playbackId] ?: continue   // resolve once per voice
ctx.renderContext.cylinders = engine.cylinders                      // var, swapped per voice
activeVoice.voice.render(ctx)
```

This is the single routing change — no per-voice cylinder field needed. (Confirm `renderContext.cylinders`
is a `var`; make it one if not.)

### C. `KlangAudioRenderer` host

`KlangAudioRenderer` already holds `voices: VoiceScheduler`; expose the engine collection from the
scheduler (`voices.playbackEngines: Collection<PlaybackEngine>`) and **drop** the separate `cylinders`
ctor param. `renderBlock` becomes:

```
mix.clear()
for (e in voices.playbackEngines) e.cylinders.clearAll()
voices.process(cursorFrame)                       // voices route into their engine's cylinders
for (e in voices.playbackEngines) {
    e.bus.clear()
    e.cylinders.processAndMix(e.bus)              // (later: e.master.process(e.bus); e.xfadeGain.apply(e.bus))
    mix += e.bus                                  // accumulate
}
limiter.process(mix.left, mix.right, blockFrames) // unchanged safety brick, now at the final sum
dcBlockerL/R.process(...) ; clip+interleave        // unchanged
```

Behaviour-identical for one engine (sum of one bus == today's single mix).

### D. Cylinder lifecycle — two-tier (deactivate → evict)

- **Tier 1 (exists): deactivate.** `tryDeactivate()` flips `isActive=false` after `silentBlocksBeforeTailCheck`
  silent blocks + delay/reverb tail check. Stops CPU. Keep as-is.
- **Tier 2 (new): evict.** Track blocks-since-deactivated (or a wall-clock idle timer). After
  `evictAfterInactiveBlocks` (a **generous** threshold — seconds, not the ~27 ms deactivation grace),
  **remove the cylinder from `id2cylinder`** so its `DelayLine`/`Reverb` buffers become garbage.
  `getOrInit` reconstructs on next touch (already does).
- **Anti-thrash (critical):** a 4-on-the-floor kick leaves its orbit silent ~470 ms between hits and
  deactivates ~27 ms after each. Evicting on deactivation would free+realloc 7.68 MB **every beat**. The
  eviction timer must exceed any musical inter-onset gap → default a few seconds of *continuous*
  inactivity (tunable const, e.g. `EVICT_AFTER_SECONDS = 4.0`). A steadily-played orbit never evicts; a
  truly-finished one frees after the timeout.
- **Mechanics:** extend `Cylinders.processAndMix` step 4 (round-robin, one cylinder/block). It already
  does single-entry indexed access + `break`; after deciding "evict", record the id and remove it from
  `id2cylinder` after the loop body (avoid concurrent-modification while iterating). Round-robin keeps it
  alloc-free and amortized.
- **Interaction with warmup pre-alloc:** with eviction + lazy per-engine alloc, the eager
  `preallocateAll(255)` is both unnecessary and self-defeating (everything idles out post-warmup). Warmup
  keeps only JIT priming (allocate a couple cylinders, exercise the path, let them evict). See H.

### E. `PlaybackEngine` GC

GC an engine only when **all three** hold (so a live-but-resting playback is never killed — it always has
look-ahead voices scheduled):

- no **scheduled** voices for the pid (`scheduled.none { it.playbackId == pid }`), **and**
- no **active** voices for the pid (`active.none { it.playbackId == pid }`), **and**
- all its cylinders inactive/evicted (`engine.cylinders.cylinders.none { it.isActive }`).

Removing the engine frees its cylinders wholesale (covers stop). Check cadence: engine count is small
(≤ a few) → scan once per block or per diagnostics tick.

### F. `cleanup(pid)` → drain semantics

Today `cleanup` immediately `playbackContexts.remove(pid)` (`:250`) while letting active voices ring out.
Once the ctx owns the cylinders, immediate removal **orphans ringing reverb/delay tails** (stopping a
song would cut its own tail). Change `cleanup` to **mark draining** (stop scheduling; clear scheduled);
actual disposal moves to the E. idle-GC check. `cleanupHard` keeps immediate semantics (warmup/clean slate).

### G. Diagnostics reshape

Add a nested `PlaybackEngineStats` and an `engines` list to `Diagnostics` (`KlangCommLink.kt:171`):

```kotlin
data class PlaybackEngineStats(
    val playbackId: String,
    val activeVoiceCount: Int,
    val cylinders: List<CylinderState>,   // room later: peakL/R, rmsL/R, gainReductionDb
)
// in Diagnostics: val engines: List<PlaybackEngineStats>
// UI-facing aggregates as computed, NON-wire getters (only ctor params serialize in @WireFormat):
val activeVoiceCount    get() = engines.sumOf { it.activeVoiceCount }
val activeCylinderCount get() = engines.sumOf { it.cylinders.count { c -> c.active } }
val cylinders           get() = engines.flatMap { it.cylinders }
```

Global fields (`renderHeadroom`, `sampleRate`, latency, `backendNowMs`) stay top-level; message still
under `SYSTEM_PLAYBACK_ID`. Emit site `VoiceScheduler.kt:377-396`: build `engines` by grouping
`active.groupingBy { it.playbackId }` for counts and reading each engine's `cylinders`. **Verify the KSP
wire codec ignores body getters** (serializes only constructor params) — see `wireformat-enhancements.md`.

### H. Warmup adaptation

- Drop per-engine `preallocateAll`. Warmup creates one warmup `PlaybackEngine` (pid `WARMUP_PLAYBACK_ID`),
  schedules the existing synth+sample warmup voices (`WarmupRunner.start()` `:85-111`) to JIT the path,
  then `cleanupHard` + `resetPostChain` (`:127-129`). New engines at runtime allocate cylinders lazily.
- First-note allocation hitch (the original reason for `preallocateAll`, per `WarmupRunner` comment): the
  render path is JIT'd once globally, so per-cylinder construction is fast; remaining risk is a song
  first-touching many orbits in one block. Mitigation options (pick during impl): pre-touch a small
  cylinder pool per new engine, or accept the lazy cost (measure first). Track in Open Q1.

## Wire / contract changes

- `KlangCommLink.Feedback.Diagnostics`: + `engines: List<PlaybackEngineStats>`, new nested
  `PlaybackEngineStats`; `activeVoiceCount`/`cylinders` become computed getters (wire shrinks). KSP codec
  regenerates. **No new `Cmd`** in this pass (`Cmd.SetMaster`/`Cmd.Crossfade` are deferred).

## File-by-file change list

- `audio_be/.../voices/PlaybackCtx.kt` → `PlaybackEngine` (+ `cylinders`, `bus`).
- `audio_be/.../voices/VoiceScheduler.kt` — `ensureEpoch`→`ensureEngine` builds `Cylinders`; expose
  `playbackEngines`; render-loop cylinder swap (B); `cleanup` drain (F); GC scan (E); diagnostics (G).
- `audio_be/.../cylinders/Cylinders.kt` — Tier-2 eviction in `processAndMix` step 4; remove the
  global-`preallocateAll` assumption.
- `audio_be/.../cylinders/Cylinder.kt` — blocks-since-deactivated counter / idle timer for eviction.
- `audio_be/.../KlangAudioRenderer.kt` — host loop (C); drop `cylinders` ctor param; warmup hook (H).
- `audio_be/.../WarmupRunner.kt` — single warmup engine; drop 255-cylinder pre-alloc (H).
- `audio_bridge/.../infra/KlangCommLink.kt` — `Diagnostics` reshape + `PlaybackEngineStats` (G).
- `audio_be/src/jvmMain/.../JvmAudioBackend.kt`, `audio_jsworklet/.../KlangAudioWorklet.kt` — wiring
  (renderer no longer takes a global `Cylinders`).
- Voice render context — make `renderContext.cylinders` a swappable `var` (B).

## Implementation order

1. `PlaybackEngine` (rename `PlaybackCtx` + add `cylinders`/`bus`); `ensureEngine` builds cylinders.
2. Render-loop routing swap (B) + host per-engine sum (C); keep limiter where it is. Suite green, one engine.
3. `cleanup` drain (F) + `PlaybackEngine` GC (E). Tests: stop → engine gone after tails; resting → kept.
4. Cylinder Tier-2 eviction (D) with generous timeout. Tests: rhythmic orbit not evicted; finished orbit freed.
5. Diagnostics reshape (G) + convenience getters. Update `VoiceSchedulerDiagnosticsTest`.
6. Warmup adaptation (H); drop `preallocateAll`. Measure browser memory.

## Test plan

- `cylinders/CylindersCleanupTest.kt` — extend: eviction removes from map after timeout; rhythmic re-trigger
  resets the evict timer (no thrash); reconstruct on next `getOrInit`.
- `voices/VoiceSchedulerDiagnosticsTest.kt` — per-engine `engines` list; aggregates equal old single values.
- New isolation spec — two playbacks on the same orbit id keep independent reverb/delay/phaser/comp state.
- New lifecycle spec — GC fires only on (no scheduled ∧ no active ∧ all cylinders inactive); draining song
  keeps its tail.
- Run one spec: `--tests` + UNQUOTED FQCN (no quotes/wildcards).

## Verification (end-to-end)

- Single song sounds **identical** to before (limiter relocation is behaviour-neutral) — in-browser listen.
- Two overlapping songs on the same orbit stay clean (no FX bleed) — the bug this fixes.
- After stopping a song, `Diagnostics.activeCylinderCount` returns to 0 and memory drops (eviction) — watch
  the cylinders gauge (`PlayerMiniStats`) + browser memory.

## Open questions

1. **Real browser cost of `preallocateAll` / the 10 s delay ring.** Reconcile the on-paper ~1.9 GB with what
   actually runs; measure post-eviction steady state. May motivate right-sizing the delay ring (allocate to
   actual delay time / grow on demand) — the real lever for many decks. Tracks the first-note-hitch tradeoff (H).
2. **Deck budget.** With eviction, memory no longer forces a hard cap → default lazy + idle-GC, no hard cap;
   soft-cap only if CPU (each active deck renders its full graph) shows strain.

## Deferred phases (after foundation)

1. **MasterChain** (gain → glue → eq → drive → ceiling) + `MasterDsl` + `Cmd.SetMaster(playbackId, …)`
   (set once + live, debounced like RPM). Per-deck creative master; the final brick stays the global safety.
2. **Crossfade** — per-engine `ValueRamp` gain (reuse the solo/mute ramp `VoiceScheduler.kt:82`) +
   `Cmd.Crossfade(fromId, toId, durationMs)` (sample-accurate ramp in the backend).
3. **Metering** — peak/RMS/gain-reduction into `PlaybackEngineStats` + UI master meters.
4. **UI master panel** + master-settings persistence (sibling of `song-{id}-rpm` in localStorage; `Song.kt`).
5. **`EngineDsl` → `PipelineDsl` rename** — coordinate with the in-flight branch.
6. **Delay-ring right-sizing** (memory optimization for many simultaneous decks).
