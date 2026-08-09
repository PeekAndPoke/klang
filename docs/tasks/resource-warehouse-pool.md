# Resource warehouse pool — the alloc-spike killer

> **Stub — not started (2026-07-04).** Priority: **SHOULD** (audible quality — arguably MUST), but Q3
> schedules it **last** in the backend work, after the master/loudness stage. Do not start before then.

## Motivation

Expensive per-engine resources — the ~7.68 MB delay rings and the cylinders — are allocated on demand.
That causes:

- a **first-note allocation spike** (the audible "Der Schmetterling" stutter), and
- **unbounded memory growth** — cylinders are never evicted (`Cylinders.id2cylinder` only grows).

**The master bus is a second customer (added 2026-08-02, see §Master chains below):** master chains own the *same*
expensive resources — the same `Reverb` and `DelayLine` classes — and today build them on the audio thread.

## Goal

A self-balancing "warehouse" pool that keeps **N** of each expensive resource ready:

- **Warm-up pre-fills** the pool ahead of playback (`WarmupRunner.kt` is the home).
- **Refill / shrink ±1 per render cycle** toward the target N.
- **Create-on-the-fly** when the pool is empty (never block the audio thread).
- **Caps memory** by bounding the pool size.

Folds in **D4** (cylinder eviction) from `per-playback-engine.md`.

## Current state

- `WarmupRunner.kt` exists — the warm-up entry point.
- `Cylinders.preallocateAll()` (`Cylinders.kt:59`) is **dead** — zero callers; replace with pool warm-up.
- `Cylinder.tryDeactivate()` (`Cylinder.kt:259`) deactivates but **never removes** from
  `Cylinders.id2cylinder` (`Cylinders.kt:24`) → the map only grows. No `EVICT_AFTER_SECONDS` / Tier-2
  removal. That gap **is** D4.

## Master chains — the second customer (added 2026-08-02)

The Master DSL ([shipped/archived](../tasks-archive/2026-08/20260803-master-dsl.md)) reuses the shared DSP, so a master
chain owns exactly
the resources this pool is meant to manage: a Freeverb (~180 KB of comb/allpass buffers) and a `DelayLine` ring sized to
the declared time — up to **7.68 MB** at the 10 s cap, the same figure as a cylinder's ring.

**Two allocation sites should rent from the pool instead of calling `MasterChain.build`:**

1. **`MasterBus.register(name, dsl)`** — builds a chain when `Cmd.RegisterMaster` arrives. Both backends drain commands
   **on the audio thread**, so this is an allocation between render quanta at the exact moment the user hits play or
   re-evaluates. Same class as the first-note cylinder spike.
2. **`MasterBus.chainFor(key)`** — the lazy rebuild after a cache miss. This one runs **inside the render callback**
   (`process()` → fade completion → `chainFor`), so it is the worse of the two. It is reachable by ordinary live coding:
   master names are content-derived, so eight edits fill the cache and going *back* to an earlier value (undo, or
   A/B-ing "was 2.0 better?") is a miss.

**What the pool changes:** `MasterChain` should rent its `Reverb`/`DelayLine` from the warehouse and return them on
eviction, so building a chain becomes cheap bookkeeping rather than a multi-MB zero-fill. Note rented units must be **
`reset()` on rent** — a master chain already resets on re-adoption (a cached chain would otherwise replay a previous
section's tail), and a pooled unit carries a *different owner's* tail, which is the same hazard `DelayLine.reset()`'s
KDoc warns about.

**Knock-on simplification:** `MasterBus.MAX_CACHED_CHAINS = 8` exists *only* because chains own unpooled buffers. With a
pool, the cache can hold lightweight chain descriptors and rent buffers on activation, so the bound (and the
eviction-rebuild hazard) can relax or disappear.

Also poolable, though minor: the per-engine `PlaybackEngine.bus` and `MasterBus.scratch`
`StereoBuffer`s (~2 KB each, one-time per engine).

## Open items

- The pool itself, per resource type (delay rings, cylinders, **Freeverb units**): keep-N + ±1 balancing +
  create-on-empty.
- **Cylinder eviction (D4, Tier 2):** remove idle cylinders from `id2cylinder` after a grace period and
  return them to the pool.
- **Master chains rent from the pool** (both sites above) + `reset()` on rent; revisit
  `MAX_CACHED_CHAINS`.
- Right-size the delay rings (deferred from per-playback). *Partly done for the master already:* master rings are sized
  to the declared delay time, not a blanket maximum — the same trick applies to cylinders, which allocate a flat 10 s
  ring regardless of the delay actually used.
- Wire the warm-up pre-fill through `WarmupRunner`.
- **Unbounded-tail hole (shared with the master):** an orbit delay at `feedback >= 1.0` pins
  `cylinders.anyActive()` true forever, so its engine is never disposed. `PlaybackEngine` now bounds the *master* tail
  hold (20 s of silence); the orbit path has no equivalent. Worth one shared fix while this area is open — see
  `master-dsl.md` §Still open.

## Scheduling

Q3 puts this **last** in the backend work — after the master/loudness stage and the FE/BE cleanup (both
done). It is a memory/alloc optimisation, not a feature; sequence it accordingly.

## Links

- `per-playback-engine.md` (D4 eviction, `WarmupRunner`, `MasterStage`).
- Memory: `project_resource_warehouse_pool`, `project_per_playback_engine`, `project_song_cpu_benchmark`.
