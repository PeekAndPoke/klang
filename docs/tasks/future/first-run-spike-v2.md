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
4. With the vocabulary certifying the OPTIMIZED graph and an `analog = 1.0` filter chain (the SVF
   branch Der Schmetterling's guitar takes, previously fused away by the optimizer in the warmup):
   **"the warmup now works on the Fairphone"** — the first run plays. What remains visible is the
   warmup's own 100 % gauge spike before the song, paid in silence (maintainer: fine). This task
   is therefore no longer urgent; it stays as the place to profile if a first-run artefact returns.

So the resource side is closed (`docs/tasks-archive/2026-09/20260927-resource-warehouse.md`), the node-kind JIT theory
helped a little, and something else dominates. "Second run clean" still says: first-time work,
per session, not per playback.

## Suspects, in the order to check

- **Per-instrument first-note work that the vocabulary cannot reach.** A song's registered
  ignitor builds its graph on its first note (`IgnitorBuildCache` / `IgnitorDslRuntime`); the
  song's guitar is a large graph with `Ignitor.param` slots, `unison(9..15)`, `phasePool`. Eight of
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

## Noted 2026-09-16 (blog fact check)

`WarmupVocabulary`'s class KDoc says the completeness guard is "an exhaustive `when` over every
`IgnitorDsl` kind, so adding a kind without deciding whether it is warmed does not compile";
`WarmupVocabularySpec` enumerates the sealed hierarchy by reflection and fails at test time instead.
The spec is the guard; the KDoc sentence is stale and should say so.

## Idea 2026-10-07: warmup in the worklet constructor (nice to have, far future)

Maintainer: "doing the warmup in the constructor at some point, this should remove even more first
voice stutter, but this is a very future and very nice to have thing."

Today `KlangAudioWorklet` builds its context and starts `WarmupRunner` on the first `process()`
call (`contextFor()`), and the warmup ticks one block per callback, silenced, until `BackendReady`.
Nothing blocks a constructor version: the class already compiles to a real ES class with a plain
`super()` constructor (es2015 target), `port` and `sampleRate` are available there, and the block
size would be `RENDER_QUANTUM_FRAMES` with a one-time check on the first `process()`.

What it would and would not buy, to weigh when we get here:

- **Would:** the warmup's own render spike leaves the audio callback (no overrun, no 100 % gauge
  spike), and `BackendReady` arrives as soon as the node exists instead of after ~20 callbacks.
- **Would not, by itself:** warm anything new. The same code runs in the same realm either way,
  so the suspects above (the song's own ignitors, per-shape JIT, GC at the tutti) stay where they
  are. Measure the first tutti before and after.
- **Cost to check:** `WarmupRunner` is block-paced on purpose (the warehouse zeroes a bounded slice
  per block). Run synchronously in the constructor it becomes one long call; harmless while silent,
  but it delays node creation, which matters most on a phone.

The `contextFor()` lazy setup stays as it is until then: it works reliably and its per-block cost is
one null check (maintainer, 2026-10-07).
