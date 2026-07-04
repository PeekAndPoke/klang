# Resource warehouse pool — the alloc-spike killer

> **Stub — not started (2026-07-04).** Priority: **SHOULD** (audible quality — arguably MUST), but Q3
> schedules it **last** in the backend work, after the master/loudness stage. Do not start before then.

## Motivation

Expensive per-engine resources — the ~7.68 MB delay rings and the cylinders — are allocated on demand.
That causes:

- a **first-note allocation spike** (the audible "Der Schmetterling" stutter), and
- **unbounded memory growth** — cylinders are never evicted (`Cylinders.id2cylinder` only grows).

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

## Open items

- The pool itself, per resource type (delay rings, cylinders): keep-N + ±1 balancing + create-on-empty.
- **Cylinder eviction (D4, Tier 2):** remove idle cylinders from `id2cylinder` after a grace period and
  return them to the pool.
- Right-size the delay rings (deferred from per-playback).
- Wire the warm-up pre-fill through `WarmupRunner`.

## Scheduling

Q3 puts this **last** in the backend work — after the master/loudness stage and the FE/BE cleanup (both
done). It is a memory/alloc optimisation, not a feature; sequence it accordingly.

## Links

- `per-playback-engine.md` (D4 eviction, `WarmupRunner`, `MasterStage`).
- Memory: `project_resource_warehouse_pool`, `project_per_playback_engine`, `project_song_cpu_benchmark`.
