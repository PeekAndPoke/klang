# First-run spike on the phone, v2: measure before guessing again

**Status:** OPEN, maintainer 2026-09-04: "a future round here, which is ok".

## What is known

Der Schmetterling on the Fairphone, three measurements on 2026-09-04:

1. Before the resource warehouse: the first run killed the playback (8 × 7.68 MB delay rings
   zero-filled in the first frame).
2. With the warehouse (rings, reverb networks and cylinders lazy and shelved; a bucketed 16-orbit
   warmup stocking the shelves before `BackendReady`; deferred zeroing): better. The count-in is
   fine; when all the other voices set in there is a spike, then hiccups that stabilise. A second
   run is clean.
3. With the warmup vocabulary (`WarmupVocabulary`: every `IgnitorDsl` node kind executed before
   the first song): "a bit better, still a massive spike when all voices set in".

So the resource side is closed (`docs/plans/resource-warehouse.md`), the node-kind JIT theory
helped a little, and something else dominates. "Second run clean" still says: first-time work,
per session, not per playback.

## Suspects, in the order to check

- **Per-instrument first-note work that the vocabulary cannot reach.** A song's registered
  ignitor builds its graph on its first note (`IgnitorBuildCache` / `IgnitorDslRuntime`); the
  song's guitar is a large graph with `Osc.param` slots, `unison(9..15)`, `phasePool`. Eight of
  those in one frame, plus `superimpose` doubling voices.
- **JIT of paths that are per-shape, not per-kind**: V8 optimises per call site and inline cache;
  a graph of the same kinds in a different shape can still deopt. The vocabulary warms kinds, not
  shapes.
- **The count-in is fine, the tutti is not**: 8 orbits × N voices at once. This may be simply the
  steady-state CPU of that bar on a phone, unrelated to "first" — except that the second run is
  clean. Compare the diagnostics headroom trace of run 1 vs run 2 at the same bar.
- **Sample decoding / soundfont**: does the song touch a sample at that bar? (It should not — no
  `sound("...")` of a sample in the file — but verify.)
- **GC**: the first tutti allocates the voice graphs; on a phone the young-gen collection can
  land inside the callback.

## What to do (in this order)

1. **Profile, don't guess.** Chrome remote devtools → Performance recording on the phone during
   the first run, with the AudioWorklet thread visible; find the long frame(s) at the tutti and
   read the stack. One recording answers all five suspects.
2. Compare `Feedback.Diagnostics` headroom (`durationMs / blockDurationMs`) run 1 vs run 2 at the
   same cursor — that separates "first-time work" from "this bar is heavy".
3. Only then: if it is graph construction, consider pre-building a song's registered ignitors at
   `RegisterIgnitor` time (a warm build off the first note) — bounded and explicit. If it is JIT
   of shapes, the honest answer is a warmup that renders the SONG's own ignitors silently once
   registered (one block each, bucketed) — the warmup engine is there already.

## Rule

Maintainer: if the fix would introduce undue complexity, keep as is and defer. Measure first.
