# Playback layer — inventory and SRP decomposition

**Status:** ANALYSIS (2026-08-29). Based on a four-way read-only inventory of the whole stack
from `CodeSongPage` down to `KlangCommLink`. Nothing implemented. Companion of
`docs/tasks/realtime-playback-controller.md` (the MIDI-side controller this analysis informs).

## 1. The stack as it stands

| Layer | Type | Responsibilities it carries | Verdict |
|---|---|---|---|
| page | `CodeSongPage` | localStorage persistence, chrome/layout, built-in-song diff+reset, editor-highlight glue, doc-hover wiring | healthy — audio fully delegated (`onPlay()` = persist + `ctrl.play()`) |
| page | `PlayableCodeExample` | same ctrl usage, `.exclusive()` | duplicates the Play/Update/Loading button logic by hand |
| app | `KlangCodePlaybackCtrl` | compile→play lifecycle, diagnostics→`EditorError`, combined UI `State`, rpm debounce, `Player` coupling | 4 concerns; `State` mixes editor text + transport + a mirrored global flag |
| app | `Player` (object) | engine bootstrap + status + samples · **nowPlaying/exclusivity broker** · `createEngine()` factory | **3 unrelated jobs on one singleton** |
| klang | `KlangPlayer` | backend process lifecycle, feedback router, playback registry, context assembly, one-off diagnostics log | god-ish but each piece small; feedback `when` is a router in disguise |
| klang | `KlangPlaybackContext` | immutable dependency bundle | **misnamed** — one shared player-level instance, not per-playback |
| klang | `ContinuousPlayback` / `OneShotPlayback` | delegation shims over the controller | ~90% duplicate; real diff = pattern clipping + prefetch override + auto-stop |
| klang | `KlangRealtimeVoicePlayback` | send `StartRealtimeVoice`/`StopRealtimeVoice`, mint liveIds | leanest type in the layer; **no registries → inline DSLs silently unsupported** |
| klang | `KlangPatternScheduler` | 8 distinct clusters (below) | the god object |
| klang | `IgnitorRegistry` / `PipelineRegistry` / `MasterRegistry` | announce-once dedup + wire send | **near-verbatim triplets**; `clear()` is dead code |
| audio_bridge | `*DslIdentity` ×3 | process-global `uniqueId()` maps | **same template again — 6 copies total** |
| klang | `SamplePreloader` | load dedup, send-cache, ack bookkeeping, UI signals, wire send | 5 concerns, but cohesive; genuinely global — must stay shared |
| klang | `BackendClockSync` | FE↔BE offset EMA | cleanest type in the layer, single responsibility |
| audio_bridge | `KlangCommLink` | two ring buffers + wire types | pure transport, no logic |

### The controller's eight clusters

`A` lifecycle/job · `B` tempo+wall-clock↔cycle clock · `C` fetch cursor · `D` sample lookahead ·
`E` inline-DSL registries · `F` cycle-completion signals · `G` live resync · `H` the
`queryEvents` hub.

**`queryEvents` is the knot.** It is nominally a query but does three things: query the pattern,
*register* every inline ignitor/pipeline/master it sees, and *optionally* emit UI signals. Six
call sites pass different `sendSignals` flags, yet registration happens unconditionally — so a
sample-lookahead query 8 cycles ahead also announces DSLs to the backend as a side effect.
Everything else in the class either calls it or consumes what it produces, so **it must be
decomposed first**; nothing else can move while it stays triple-duty.

Other entanglements that any cut must respect:

- **`B` (clock) is read by almost every cluster** via raw field access — it is already a shared
  dependency, just an implicit one.
- The sequence *query → send → advance cursor* appears **three times** (first-cycle schedule,
  cursor drain, resync-with-`ReplaceVoices`) — a missing primitive.
- `G` **writes `C`'s field directly** (`queryCursorCycles = to`).
- `A`'s `running` flag is read from `F` and elsewhere as a cross-cutting stop-gate, including a
  deliberate double-check across a dispatcher hop.
- Registration must happen **before** `ScheduleVoices` goes out — today that ordering is implicit
  inside `queryEvents`; any split has to make it explicit.

## 2. The core insight: shared vs cyclic-only

Sorting every responsibility by *which kinds of playback need it* cuts the layer cleanly in two:

**Needed by EVERY playback (cyclic and realtime):** playbackId · signals stream · the wire handle ·
cleanup on stop · **inline-DSL announce-once bookkeeping**.

**Needed only by CYCLIC playback:** transport clock · pattern query · fetch cursor · sample
lookahead · cycle-completion signals · live resync.

This confirms the hypothesis that the bookkeeping belongs on the playback rather than the
scheduler — and it is not merely tidier. `KlangRealtimeVoicePlayback` has no registries today,
which is exactly why inline-DSL sounds are unsupported on the realtime path. **v1's ignitor
editor needs precisely that plumbing.** Moving the registrar up to the playback serves the MIDI
workstream and de-duplicates the cyclic path in one move.

## 3. Proposed parts

Ordered by value. Each is independently shippable.

### P1 — `InlineDslRegistrar` shared by composition (**unblocks MIDI v1**) — ✅ DONE 2026-08-29 (live paths), offline pending

**Shipped (uncommitted):** the three registry classes are gone, replaced by
`AnnounceOnceRegistry<T>` + `InlineDslRegistrar` (which also owns the sweep).
`KlangPlaybackContext.registrarFor(playbackId)` is the one construction site; `ContinuousPlayback`
and `OneShotPlayback` mint it and hand it to the scheduler; `KlangRealtimeVoicePlayback` now has
`registerIgnitor(dsl): String`. `InlineDslRegistrarTest` (10 rows) replaces `IgnitorRegistryTest`;
3 mutations killed.

**Still open:** `KlangOfflineRenderer` keeps its own hand-rolled sweep — it needs the local
(in-process) sink rather than the wire sink. Deferred deliberately: the maintainer plans to
rewrite the offline path anyway. Until then the live/offline drift risk in §5.7 remains.

Original analysis follows.

There are **four** copies of the same idea today, and the fourth one determines the shape:

| Path | Sweep | Dedup on | Sink |
|---|---|---|---|
| cyclic live | `KlangPatternScheduler.queryEvents:390-407` | DSL object (`sentToBackend` set) | `sendControl(Cmd.RegisterX)` |
| realtime live | — **missing** — | — | — (inline DSLs unsupported) |
| offline render | `KlangOfflineRenderer.kt:105-125` | name (`registry.contains`) | in-process `registry.register(name, dsl)` |
| identity | `audio_bridge/*DslIdentity.kt` ×3 | — | global `uniqueId()` maps |

So the concept splits in two:

- **`collectInlineDsls(events) -> InlineDsls(ignitors, pipelines, masters)`** — a pure sweep over
  events, shared by every path.
- **A sink**, `InlineDslSink.ensureRegistered(name, dsl)`, with two implementations: a wire sink
  (first sighting → `Cmd.RegisterX`) and a local sink (`!contains(name)` → `register`). Same
  shape, same dedup contract, different destination.

`InlineDslRegistrar` = the three announce-once registries behind one sink, collapsed onto one
generic `AnnounceOnceRegistry<T>`.

**Share it by COMPOSITION, not inheritance** — and the offline renderer is the proof: it is not
a `KlangPlayback` at all and never can be, yet it needs exactly this. A base class would exclude
it; an interface with default methods would drag mutable state into an interface. A small owned
object with a swappable sink fits all four callers.

Where each one gets it: `KlangPlaybackContext.registrarFor(playbackId)` (one construction site,
per-playback instance, wire sink) for both cyclic and realtime playbacks; the offline renderer
builds its own with the local sink.

### P2 — `TransportClock` (biggest correctness win)
Owns `cyclesPerSecond`, `startTimeMs`, the frame counter, and the rpm-rebasing math; exposes
`nowCycle()`, `cycleStartTime(n)`, `setRpm(rpm)`. Pure, deterministic, unit-testable without a
backend. Everyone that reads B's fields takes it as a dependency instead. **It also closes a
latent bug** (see §5).

### P3 — Pure `PatternVoiceQuery`
`query(pattern, from, to) -> QueryResult(voices, uiEvents, inlineDsls)` — no registration, no
signal emission. Callers then do explicitly what is implicit today: announce the DSLs, send the
voices, emit the signals. The three duplicated *query→send→advance* sites collapse onto one
`fetchAndDispatch` primitive built on it.

### P4 — `CycleSignalEmitter`
`lastEmittedCycle` + the stall-safe emission loop. Pure UI notification; schedules nothing.
Depends on `TransportClock` and the stop-gate.

### P5 — `PatternScheduler` (what remains, renamed)
The fetch loop, the cursor, sample-lookahead policy, resync policy, start/stop. Composed of
P2+P3 plus the registrar handed down from the playback. This is the class currently called
`KlangPatternScheduler`, minus everything above — and the name finally matches the job.

**Naming (discussed 2026-08-29).** `Playback` is the most overloaded word in the layer (five
`*Playback*` types), so `KlangPatternController` was floated as a cheap improvement and is
strictly better than today's name. Two notes against stopping there: the `Klang` prefix marks
the module's PUBLIC surface (`KlangPlayer`, `KlangPlayback`, `KlangCommLink`) while this class is
`internal` like its unprefixed peers (`ContinuousPlayback`, `IgnitorRegistry`,
`SamplePreloader`); and "Controller" is the vague half that started this discussion. Recommended:
rename once, straight to `PatternScheduler`. Cost either way: 3 sites, own commit.

### P6 — Fold `OneShot` into `Continuous`
Their only real differences are pattern clipping, a prefetch override and auto-stop-after-N.
Either a shared base or `PatternScheduler` taking an optional `maxCycles` leaves one playback
class with two option presets.

### P7 — Split `Player` (app layer)
Three ways: the engine/samples bootstrap singleton stays; `nowPlaying` + `nowPlayingOwner` +
`requestExclusivePlayback` become a small `PlaybackFloorBroker` (it is UI coordination, not
engine state); `createEngine()` becomes a free function (it touches none of `Player`'s state).

## 4. What NOT to touch

- `BackendClockSync` — already single-responsibility.
- `SamplePreloader`'s global scope — the cross-playback cache is deliberate; do not make it
  per-playback.
- `KlangCommLink` — pure transport, correct as is.
- The `CodeSongPage` ↔ ctrl seam — the page already delegates all audio cleanly.
- `KlangPlayback` as the root interface — the right abstraction; realtime and cyclic both fit.

## 5. Findings worth fixing regardless of any refactor

1. **Latent TOCTOU on tempo change.** `emitCompletedCycles` decides a cycle boundary using
   `secPerCycle`, then re-reads `startTimeMs`/`secPerCycle` *later*, inside a coroutine hopped
   onto `callbackDispatcher`. A concurrent `updateRpm`/`updatePattern` in between makes the
   emission use different values than the decision did. P2 (an immutable clock snapshot handed to
   the emission) fixes it by construction.
2. **Dead code**: `clear()` on all three registries (never called anywhere); the three no-op
   branches in the controller's `handleFeedback`; `Cmd.ScheduleVoice` and `Cmd.ClearScheduled`
   have no production senders (test-only primitives — worth documenting as such).
3. **Redundant work**: the registry sweep runs in both the sample-lookahead and schedule paths
   every tick where the windows overlap (harmless, deduped by the set, but wasted).
4. **Dead return value**: `registerIgnitor(it.osc)`'s synthetic name is discarded; the name
   actually used is re-resolved independently in `toVoiceData()`.
5. **Style inconsistency**: `registerIgnitor` is a bound-method field while pipelines/masters get
   real methods.
6. **`KlangPlaybackContext` is misnamed** — it is player-scoped, not playback-scoped.
7. **Latent drift between live and offline renders.** The inline-DSL sweep is written twice, in
   two files, with two different dedup keys. Adding a fourth inline-DSL kind (a Katalyst, say)
   means remembering to update BOTH — and if only the live path is updated, offline renders
   silently differ from what you hear, with no error. Nothing enforces the pairing today. P1
   removes the class of bug by giving both paths one sweep.

## 5b. Offline renderer — impact assessment

`KlangOfflineRenderer` (202 lines) deliberately bypasses the live playback stack: it drives
`KlangAudioRenderer` directly, block by block, with its own `KlangCommLink` that nothing pumps.
It therefore has **no** clock, cursor, lookahead, resync or signal concerns — P2-P6 do not touch
it at all.

It IS touched by P1, and beneficially:

- it loses ~20 lines of hand-rolled sweep (`:105-125`) in favour of the shared one;
- the live/offline drift risk above disappears;
- its `customIgnitors` parameter (register a named ignitor before rendering) turns out to be the
  same operation MIDI v1 needs from the editor — one `registerIgnitor(name, dsl)` surface serves
  the CLI renderer and the playground.

Its sample preloading (`:130-150`, `samples.get(req)` → `voiceScheduler.addSample`) is a third
variant of "get samples to the engine", next to `SamplePreloader`'s live path. Out of scope here,
worth noting as the next duplication if anyone goes looking.

## 6. Suggested order

P1 first (small, unblocks MIDI v1, deletes 6 copies of one template). Then P2 (correctness).
Then P3, which unlocks P4/P5. P6 and P7 are independent cleanups that can happen any time.
Each step is its own commit and its own review; none of them needs to block the MIDI work after
P1.
