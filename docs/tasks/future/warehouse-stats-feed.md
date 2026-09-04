# Warehouse stats feed: one entry point, incrementally maintained, sent with Diagnostics

**Status:** IDEA, maintainer 2026-09-04. Not started. Belongs after the resource warehouse
(`docs/plans/resource-warehouse.md`, all steps shipped) as its "reporting half".

## The idea (maintainer, verbatim in substance)

Put the **sample storage into the warehouse as well**, keeping its functionality exactly as it is.
Why: then the warehouse is the **single point of entry for stats** about every expensive thing the
backend holds:

- how much memory each part currently holds (rings, reverb networks, cylinders, scratch, sample PCM),
- counters for creation and eviction per part (allocations / hits / failures / dropped / double
  returns / denied rents … most already exist per shelf),
- whatever else turns out useful.

This can then be **sent along with the `Diagnostics` feedback** to the frontend.

**And the stats must be maintained incrementally:** each part updates its own numbers at the moment
something changes (a rent, a return, a drop, a sample upload). Reading the stats is then a plain
read — no walk over the shelves, no recomputation on every diagnostics tick. When nothing has
changed, the stats are simply the same object/values as before.

## What already exists

- `ResourceWarehouse { sized, reverbs, cylinders, scratch }` with per-shelf counters and
  `SizedBuffers.shelfBytes` (Double, maintained on rent/return) — already incremental.
- `SampleStore` lives on `AudioBackendContext` next to the warehouse, with `allocationFailures`.
  Nothing tracks its resident PCM bytes yet.
- `KlangCommLink.Feedback.Diagnostics` is emitted by `PlaybackEngineDispatcher.emitDiagnostics`
  every ~20 ms; it carries voice count, cylinder states, headroom.
- Every warehouse counter has **zero non-test readers** (review round 2 finding).

## Shape to aim for (not designed yet)

- `ResourceWarehouse.samples: SampleStore` (constructed by the warehouse or handed in; the store's
  API stays as is — `AudioBackendContext.sampleStore` can keep pointing at it).
- A `WarehouseStats` value the warehouse keeps up to date (one mutable holder per part, bumped at
  the change sites; a `version`/dirty flag so `emitDiagnostics` can skip serialising when nothing
  changed).
- `Diagnostics` gains a `warehouse` field (wire type, `@WireName`, per house rule — no enums).
- Frontend: a small panel/gauge. Also the place to surface `droppedVoices` (block-framing B2) and
  the per-orbit `deniedRents` ("delay time reduced: out of memory").

## Not to forget

- Sample PCM bytes are the one part that only grows (no return path today); the stat makes that
  visible, which is the point.
- Keep the accounting exact (Double for bytes, no Long) and O(1) per change.
