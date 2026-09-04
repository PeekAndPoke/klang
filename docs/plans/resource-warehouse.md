# Resource warehouse — right-size first, then shelve what comes back

**Decided 2026-09-03 with the maintainer.** V1 Layer-1 items #7 (pool) and #8 (D4 eviction).
Supersedes the 2026-07-04 stub, whose central design (pre-warm a shelf of 10-second rings) is
replaced below. The old text's still-valid observations are folded in.

## The problem, measured

A `Cylinder` allocates, unconditionally, whether or not the orbit ever uses the effect:

| | size | share |
|---|---|---|
| `DelayLine(maxDelaySeconds = 10.0)`, stereo ring | **7.68 MB** @ 48 kHz | **97.4 %** |
| `Reverb` (Freeverb, 8 combs + 4 allpasses, stereo) | 204 KB | 2.6 % |
| mix / send buffers, effect wrappers | ~3 KB | |

Shipped songs touch up to **8 orbits**, so a first play zero-fills **~63 MB on the audio thread**,
one cylinder per orbit as voices first arrive — each a 1M-element `DoubleArray` fill inside a 2.9 ms
callback. That is the "Der Schmetterling" stutter.

And the delay times songs actually use: every `delaytime` in the corpus is WRITTEN as 1/8 to 1/4 of
a cycle — **0.25–0.5 s**. The ring is 15–30× larger than anything ever asked for, allocated for orbits
that have no delay at all. `MasterChain` already does it right (ring sized to `time + margin`).

> Review round 2 claimed these were Kotlin integer divisions (`pure(3/16)` → 0). **Wrong, withdrawn
> 2026-09-04 (maintainer):** the builtin songs are KlangScript source inside Kotlin string literals,
> and KlangScript `/` divides. The corpus measurement stands.

**So the ring is not a cost to manage; it is a mis-sizing to remove.** A warehouse of pre-warmed
10 s rings would cost 77 MB of browser memory to shelve a problem that should not exist.

## Decisions

### Rings: lazy, class-sized, open-ended, never shrunk

- **No delay configured → no ring.** Allocated on the first `configure(time >= MIN_ACTIVE)`.
- **Class ladder: powers of two from 0.5 s, no ceiling.** `0.5, 1, 2, 4, 8, …` A request is rounded
  up to its class. Ratio 2 was chosen over Fibonacci (≈1.6) because the ladder's job is *headroom*:
  a coarser ratio means a time change crosses a class boundary — and forces a regrow — less often.
  Fibonacci's tighter packing solves a memory problem right-sizing already solved 15×. `log2`
  indexing is a bonus. Class 0 holds a 0.5 s delay INCLUDING the 64-frame interpolation margin
  (review round 2: flat 0.5 s pushed exactly-0.5 s, a quarter at 120 BPM, into class 1).
- **No maximum.** `delaytime(60)` gets a 46 MB ring and the one-time allocation it asked for.
  **Known cost (review round 2):** two audio-thread scans are O(ring) and were bounded by the old
  10 s ceiling — `DelayLine.hasTail()` (orbit cleanup polling, every block once the mix is silent)
  and `drainSamplesUntilSilent`. A 64 s ring is a 6 M-element scan inside one block. The typical
  case is 20× cheaper than before; the tail case is newly unbounded. `hasTail` could scan only the
  reachable tap window (`tapWindowPeakAbs` already reasons that way) — a follow-up, not folded into
  a review batch because it moves deactivation timing.
  `MasterChain.MAX_DELAY_SECONDS = 10.0` and its `coerceAtMost` are **removed**. `DelayLine:200`'s
  `coerceIn(MIN, bufferSize - 2)` stays — it is the physical bound of whatever ring exists, not a
  policy, and it becomes the out-of-memory clamp for free (below).
- **Grow only, by migration.** A later owner asking for more than the ring holds gets the next
  sufficient class with the **contents copied in** — a delay that is ringing keeps ringing across
  the resize. `DelayLine` is circular, so "copy" means unrolling from the write index; O(old size).
  This is the one remaining allocation with audible risk, and it is rare: it fires only when a time
  change crosses a class boundary, and the shipped songs' times never move at all.
- **Never shrink.** Maintainer: a shrink is an allocation plus a copy for no audible benefit, and a
  ring that once held a long delay will be asked for it again. Dropping shrink deletes a whole
  family of decisions (when, with what hysteresis) that cannot be made well.

### The shelf: stocked by return, never by prediction

Maintainer's concern, verbatim: with size classes, "we will not know upfront which rings to keep,
so we would need some kind of heuristic, which can always be exactly wrong." Agreed — so there is no
heuristic:

- A ring goes **onto the shelf when returned** (cylinder eviction, playback disposal, or the old ring
  after a migration). Nothing is pre-filled by guessing.
- A request takes the **smallest ring on the shelf that is big enough** (maintainer: a 2 s request
  with only 4 s and 8 s available takes the 4 s). No upper bound on "too big" — the memory is
  already spent, and a fresh allocation beside an idle larger ring is worse on both axes. Bonus: an
  oversized ring absorbs later grows for free.
- Empty shelf → allocate. **A miss is no longer a spike**: the smallest class is ~0.4 MB, tens of
  microseconds on a desktop, well under a millisecond on the Fairphone.
- **Budget:** `SHELF_BUDGET_BYTES = 32 MB` (≈ four songs of right-sized rings), live browser only;
  the offline renderer renders once and needs no shelf. Over budget → free the largest ring first.
  A **named constant, not a mechanism**: the budget bounds only *idle* memory, so being wrong is
  never audible (too small = a few more allocations on re-run; too big = idle memory).
  `navigator.deviceMemory` is Chromium-only, coarse, and unreadable inside an AudioWorklet; revisit
  only if a device proves the need. One total budget; scratch is KB-scale and never meaningfully
  consumes it.

A cache cannot be "exactly wrong": its worst case is empty, and empty is today's behaviour minus
97 % of the cost. The re-evaluate-and-play loop that IS live coding hits the shelf every time, because
the rings that come back are precisely the ones the next run wants.

Total ring memory is therefore **monotone and predictable**: it grows to the largest simultaneous
demand the session has seen, quantised to classes, never above the budget.

### Out of memory: caught at one place, degraded, counted, reported

Today it is fatal: cylinders allocate inside `SendRenderer.render` → `process()`, and an uncaught
throw there stops the `AudioWorkletProcessor` **permanently** (`processorerror`; `process()` is never
called again). `MasterBus.chainFor` is the same. The JVM's `OutOfMemoryError` kills the render
thread. A live coder who typos `delaytime(6000)` loses the set until a reload.

- The warehouse is **the only allocation site** for the resources it owns, so it is the only catch
  site. It returns **nullable** — `DelayLine?`, `Reverb?` — and the compiler forces every consumer
  to decide. Maintainer's point: the compiler tells us every place that needs a null check.
- **Grow fails** → keep the ring you have; `DelayLine:200` clamps the time to the ring. No
  allocation, no audio break, the delay just does not get longer.
- **First allocation fails** → the effect stays dry on that orbit. If 0.4 MB will not allocate the
  device is moments from killing the tab anyway.
- **Counted per playback and surfaced through feedback**, like `droppedVoices` — so the frontend
  can say *"delay time reduced: out of memory"* instead of the set going quiet.
  **NOT BUILT (review round 2):** the counters exist (`deniedRents`, `effectiveDelaySeconds`,
  `SizedBuffers.failures/dropped/doubleReturns`, `ScratchBuffers.lateAllocations/unbalancedReleases`)
  and have zero non-test readers. Surfacing is its own step, with `droppedVoices` (B2), once the
  FE feedback channel for backend counters exists.
- On the house rule "no exceptions in audio hot paths": a catch around one large allocation, once
  per ring, is not a per-sample throw. JS: an ordinary `RangeError`. JVM: catching
  `OutOfMemoryError` at a single big-allocation site is the one sound pattern for it — the failed
  allocation never happened, so the heap is as it was.
- Transient worth knowing: a migration holds **both** rings for the copy. A 32 s → 64 s grow peaks
  at 73 MB, proportional to a delay the user explicitly asked for.

### Scratch buffers: shared, sized at build, never allocating in render

`ScratchBuffers` is already a pool (stack discipline, 4 pre-allocated, reset per block, a sibling
cache per oversample factor). Three things are wrong with it, none of them the pooling:

1. It **grows inside render** when a deep graph exhausts it (audit §6.5).
2. `release()` is unguarded — `nextFree--` can go negative.
3. **One instance per scheduler**, so every playback and the warmup engine build their own.

Fix: one shared instance in the warehouse (safe: engines render **sequentially** within a block,
so the stack discipline holds, and the pool converges to the deepest graph any playback has had —
warmup's depth is *kept*, not disposed). `VoiceFactory` builds the ignitor graph and knows its
scratch depth, so the pool is **sized at build**; `acquire()` then never allocates in `process()`.
`release()` gets its guard.

**Scratch does NOT go through the nullable path.** A 1 KB failure cannot be handled meaningfully,
and `acquire()` is per node per block — nullable there would spread branches whose only content is
"render silence" through every composing ignitor. Remove the allocation from the hot path instead
of catching it there.

## Structure

```kotlin
/** One per backend, owned by AudioBackendContext — singleton in effect, injectable in specs. */
class ResourceWarehouse(sampleRate: Int, blockFrames: Int, budgetBytes: Int) {
    val sized: SizedBuffers        // delay rings: class ladder, rent (nullable) / return / byte budget
    val reverbs: ReverbUnits       // Freeverb networks: one size, rent (nullable) / return / count bound
    val cylinders: CylinderUnits   // whole orbits: rent / return / count bound (as built 2026-09-04)
    val scratch: ScratchBuffers    // one shared pool, sized at build, never allocates in render
    fun housekeep()                // one block's slice of deferred zeroing (review round 3)
    val isClean: Boolean           // the warmup waits for this before BackendReady
}
```

(As designed this was two shelves, `sized` holding "rings + reverb units"; as built, networks are
their own one-size shelf and cylinders joined later. The byte budget covers the rings; networks and
cylinders are count-bounded, ~6.5 MB and KBs respectively.)

`ctx.scratchBuffers` becomes `ctx.warehouse.scratch`. **Not a Kotlin `object`** (maintainer agreed):
a real singleton cannot be replaced, so every spec would share one warehouse, the offline renderer
would share it with the live backend in the same JVM, and state would leak between tests.
Owned-by-context gives the sharing in production and a fresh instance per spec.

## Inventory — what the warehouse owns, and what it deliberately does not

| allocation | size | when | warehouse? |
|---|---|---|---|
| delay rings | MB | first `configure`, grow | **shelf + OOM** |
| master chains (ring + reverb) | MB | `register` between renders; `chainFor` **inside the callback** | **shelf + OOM** — the second customer, and the worst site |
| reverb units | 204 KB | today: every cylinder; after: first `room` | **shelf + OOM**, lazy |
| sample PCM (`SampleStore` reassembly) | MB | chunks arrive, between renders | **OOM catch only** — content, not returnable; never shelved |
| cylinder + contexts + send buffers | ~3 KB | first touch of an orbit | with the cylinder (D4) |
| scratch | 1 KB | today: on exhaustion in render | sized at build; shared |
| diagnostics + `CylinderState` messages | small | every block, in the callback | no — a message, not a resource |
| **per-note voice graphs** (`Voice`, pipeline, the ignitor tree, filters, contexts) | many small objects | **every note**, in `promoteScheduled` | **no — a different problem** |

The last row is the engine's steady-state GC pressure and is inherent to heterogeneous voices; it
does not cause the first-note stutter. An arena / voice-pool is a separate, larger project — the
next tier, not this one.

## Process (maintainer)

**1. Build the warehouse in isolation and test it properly.** `ResourceWarehouseSpec`, no DSP:
best-fit-up, never shrink, return-to-shelf, budget with drop-largest, allocation failure → `null`
(injected, not by exhausting the JVM), scratch sized-at-build with a guarded release. Mutation-checked
before anything touches it.

> ✅ **DONE 2026-09-03.** `warehouse/SizedBuffers.kt`, `warehouse/ResourceWarehouse.kt`, and two
> additions to `ScratchBuffers` (`ensureCapacity`, guarded `release()`, `lateAllocations` +
> `unbalancedReleases` counters). Nothing in the DSP references any of it yet. 20 rows, 10 mutations
> (one per rule: linear ladder, largest-fit, too-small handed out, no clear on return, smallest freed
> first, budget unenforced, failure uncounted, the real catch rethrowing, `ensureCapacity` inert,
> `release` unguarded) — all red on exactly the row that names the rule. One row exercises the real
> catch: `allocateOrNull(Int.MAX_VALUE)` (34 GB a channel) returns `null` rather than throwing.

**2. Integrate one customer per step**, measuring at the audible one:

| step | change | proves |
|---|---|---|
| 2a | scratch: shared instance, `ctx.warehouse.scratch`, sized at build, guarded release | mechanical rename; render never allocates scratch |
| | ✅ **substance DONE 2026-09-03** — `AudioBackendContext` owns the `ResourceWarehouse`; the scheduler renders with `context.warehouse.scratch`; `SharedScratchSpec` proves through the real engine that every playback shares one pool, a 24-deep chain (high-water 30) makes zero in-render allocations, the oversample sub-pools exist before the first render, and the stack discipline holds. **Deviation recorded:** the pool is sized once at warehouse creation (`SCRATCH_DEPTH = 64`), not per voice at build — the DSL tree has no walker, and a per-voice count would be a 78-arm `when` every new node type must maintain, for a 1 KB buffer. The counter is the proof, not the number. **The rename `ctx.scratchBuffers → ctx.warehouse.scratch` is NOT done**: 45 production + 66 test sites, zero behaviour change; its own commit, when the parallel sessions are not mid-edit in `ignitor/` tests. | |
| 2b | rings: lazy, class-sized, rented from the shelf | **measure on the Fairphone — the stutter should already be gone** |
| | ✅ **DONE 2026-09-03** — `Cylinder` no longer constructs a `DelayLine`; `KatalystDelayEffect` holds `delayLine: DelayLine?` (null until a voice asks) and rents from the shelf on the first activating `configure`, sized to the class that holds the time + a 64-frame interpolation margin. Never shrinks; `reset()` keeps the ring; a refused rent leaves the effect Off (or keeps the ring it has, with `DelayLine.effectiveDelaySeconds` now exposing the clamp for the frontend). `DelayLine` takes its ring from outside (secondary constructor still allocates, for master + specs). `preallocateAll` deleted. **Growing is rent-bigger-and-return, no copy — the tail is lost; `LazyRingSpec` PINS that as a tripwire for 2c.** 14 rows, 6 mutations red; the headline "eight cylinders, zero ring bytes" row was strengthened after a mutation showed the shelf's counters cannot see an allocation that bypasses the shelf. **Fairphone measurement still owed.** | |
| 2c | grow: migrate up, contents preserved | a spec that *listens across the seam* |
| | ✅ **DONE 2026-09-03** — `DelayLine.adoptHistory(from)`: the old ring's history is copied oldest-first into `[0, n)` and the cursor left at `n`, so "n samples ago" reads the same sample in the new ring as in the old. Adopt before `giveBack` (which clears). The 2b tripwire is flipped; the seam row proves a grown ring is **bit-identical** to one that was big from the start — *for taps within the old ring's span*, which is the honest scope: a tap past that span reads never-recorded zeros in both. **The first cut of that row compared zeros to zeros** (a 0.9 s tap past 0.087 s of history) and two mutations survived it; it now shortens the tap back inside the span after the grow and carries a positive control. 5 mutations red: no migration, cursor not advanced, off by one, reversed copy, giveBack-before-adopt. | |
| | 🔎 **Review round 1 applied 2026-09-03** (two Opus reviewers, five overlapping findings). Fixed: a refused rent was **retried every block** (a per-block allocation storm exactly when memory is tight; now a `refusedFrames` latch, cleared on success and `reset()`); `framesFor` saturates hopeless times (past Int, NaN) to `Int.MAX_VALUE` → null, instead of renting the smallest ring; `adoptHistory` copies the **newest** n (the oldest-first cut was wrong, not truncated, for a smaller target; shrinks never happen, it is simply right now); the `DoubleArray` half of the scratch pool was un-hardened and empty (`ModApplyingIgnitor`'s first render allocated inside `process()` while `lateAllocations` read zero); shelf byte accounting was Int and wrapped past 134 M frames; a double `giveBack` could shelve one ring twice; `OVERSAMPLE_SCRATCH_DEPTH` 8 for factors 2/4/8/16; `StereoBuffer`'s redundant `init { clear() }` gone; stale docs in five places. New specs: `effects/DelayLineMigrationSpec` (cursor at 0, equal sizes = the branch whose absence HANGS, smaller target; its first ramp saturated in `softCap` to a wall of 1.0 — ones-vs-ones — and now carries a positive control), an offline `KlangAudioRenderer` clock-advance row, direct counter rows for the double half. All 19 mutations red. **Honest residual cost:** the first delay on a backend still allocates its ~0.4 MB class-0 ring inside render, and a grow allocates the next class in render; the shelf only helps the SECOND customer. Whether to pre-warm one class-0 ring at warmup, and whether a grow past the old ring's span (wet steps to zero for `newTime − oldSpan`) is acceptable or the minimum class should rise, are the two decisions parked with the maintainer. `nowFrame` in `VoiceFactory.createVoice` is dead since B2 and awaits its own cleanup commit. | |
| | 🔎 **Review round 2 applied 2026-09-03** (two fresh Opus reviewers; **zero new CRITICAL/MAJOR** — the one MAJOR is the parked D1 with numbers: a 0.3→0.6 s grow at 48 kHz mutes the wet path for 100 ms; the audio reviewer's own recommendation is to accept D1 as parked and, if ever taken up, spend the lines on a wet-gain declick across the migration rather than a bigger ring, since no ring size recovers audio that was never recorded). MINOR batch applied without a further round: the refusal latch now gates only the ALLOCATION and is keyed on the CLASS (a latched orbit still takes a ring the shelf can serve for free; `SizedBuffers.rent(allocateOnMiss)`); `giveBack` decides eviction first and clears only what stays; class 0 = 0.5 s **plus** the 64-frame margin (`ResourceWarehouse.RING_MARGIN_FRAMES`), so exactly-0.5 s fits; `ScratchBuffers.highWater` split from `doubleHighWater`, `reset()` deleted (half-synced, no caller), `ensureCapacity(depth, doubleDepth)`; oversample sub-pools are created, not pre-sized (real depth 1, double half empty — the round-1 depth-8 warm-up was ~460 KB nothing could reach); `capacityFrames` KDoc; hopeless-time row runs one fresh effect per case (the latch had swallowed its second half); corpus/counter claims in this plan corrected (above); 2e moved ahead of 2d. The corpus integer-division finding was WRONG (KlangScript source in a Kotlin string, see above). Rejected: making `rings` a required ctor param on `Cylinder`/`Cylinders` (44 test sites, many in files under parallel edit; the one production site is pinned through `PlaybackEngine.create` by `LazyRingSpec`). 13 mutations red; three equivalent mutants documented (latch storing raw frames is behaviour-identical on a power-of-two ladder; NaN never reaches `framesFor`, the off branch catches it; sub-pool depth 1 = the real depth). | |
| 2e | master: same ladder and shelf, `chainFor` rents; `MAX_DELAY_SECONDS` removed | the worst allocation site closed — **moved ahead of 2d (review round 2): the orbit/master `delaytime` parity gap is LIVE since 2b** (orbit uncapped, master clamps at 10 s), the roomSize-10× class of bug |
| | ✅ **DONE 2026-09-04** — `MasterChain.build(dsl, sampleRate, blockFrames, rings)` rents each delay ring from the backend's shelf by the SAME rule as `KatalystDelayEffect.framesFor` (`ceil(time·sr) + RING_MARGIN_FRAMES`, class-sized, no ceiling); `MAX_DELAY_SECONDS` and `DELAY_RING_MARGIN_SECONDS` deleted. A refused rent builds the chain WITHOUT that delay stage (`BuiltDelay.Denied`, counted in `MasterChain.deniedRents`); a past-Int time is denied the same way. `MasterBus(…, rings)` passes the shelf through; `evictIfNeeded` calls `MasterChain.releaseRings(rings)` on the victim, so an evicted chain's rings restock the shelf and the next master delay of that class is a hit. `MasterRingShelfSpec` (8 rows): class parity with the orbit for 0.25/0.5/3/20 s and a class-edge time 14 frames under the boundary (the one place the margin shows), `delay(20)` = 20 s effective, **bit-identical output** against the old `time + 0.05 s` private ring over 400 blocks of noise with a wet-energy positive control, refusal dry + counted + still processing, hopeless time denied, eviction return + hit, `releaseRings` returns every ring. 7 mutations red (no margin, the 10 s ceiling back, refusal uncounted, hopeless wraps, eviction keeps the ring, only the first ring returned, feedback drift). The first master delay of a class on a backend still allocates in render (D2, accepted). Chains that die WITH the engine do not return rings yet: that is 2f, the same return path. | |
| 2d | reverb units: lazy on first `room`, rented | |
| | ✅ **DONE 2026-09-04** — `warehouse/ReverbUnits`: a shelf of whole Freeverb networks (one size, so no ladder), stocked by return, LIFO, `maxIdle = 16` (~3 MB), a returned unit is `reset()` + `restoreDefaults()` so a rented one is **bit-identical to a new one** (spec: 100 blocks of noise through a hard-used-then-returned unit vs a fresh `Reverb`, with an energy positive control); allocation caught at one site → null; double return refused. `KatalystReverbEffect(units, blockFrames)` holds `reverb: Reverb?` — null until the first ACTIVATING configure (finite `roomFade`, or `roomSize >= MIN_ACTIVE_ROOM_SIZE`), kept across `reset()`, a refusal stays Off + `deniedRents` + a Boolean latch cleared by `reset()`; the `(reverb, blockFrames)` test seam keeps every existing spec. `Cylinder`/`Cylinders`/`MasterBus`/`MasterChain.build` take `reverbs: ReverbUnits`; `PlaybackEngine.create` passes `context.warehouse.reverbs`; master `buildReverb` rents (`BuiltReverb.Denied` counted in the same `deniedRents`); `releaseRings` became `releaseUnits(rings, reverbs)`. Cylinder's Ducking/Compressor took their sample rate from `reverb.reverb.sampleRate` — now from the cylinder's own. `LazyReverbSpec` (12 rows), 11 mutations red (own-network bypass, latch, count, latch not cleared, state reset skipped, defaults skipped, `damp` forgotten, `maxIdle`, double return, master refusal uncounted, eviction keeps the unit). Eight cylinders now cost ~0 bytes of effect memory until a note asks for delay or room. | |
| 2f | D4: eviction returns rings and reverb to the shelf; `id2cylinder` stops growing | memory monotone and bounded |
| | ✅ **DONE 2026-09-04, as ENGINE DISPOSAL** — `PlaybackEngine.dispose()` → `Cylinders.releaseAll()` (each cylinder's `delay.release()` / `reverb.release()` give the ring / network back and forget it, `id2cylinder` cleared) + `MasterBus.releaseAll()` (every cached chain's `releaseUnits`, cache cleared). The dispatcher calls it at both removal sites (drained + `cleanupHard`). Until now every playback's rings and networks became garbage at its end and the next playback allocated them again in render; now the next playback's first delay and room of that class are shelf hits (`EngineDisposalReturnSpec`, 5 rows through the real dispatcher: drained return with the very instances, next-playback hits with zero new allocations, hard-cleanup return, master chain units back, a returned ring reads all zeros). 8 mutations red. **Deliberately NOT done: evicting an idle cylinder inside a LIVE engine on a time heuristic.** A deactivated orbit keeps its ring and network across `reset()` so its next bar re-activates for free; returning them after "N idle seconds" is exactly the heuristic that can be exactly wrong (an orbit that plays once per chorus would re-rent and re-clear every chorus). `id2cylinder` is bounded by `MAX_CYLINDERS` and a ring-less, network-less cylinder is a few KB of send buffers. If a live engine's idle rings ever matter, the trigger should be memory pressure (a refused rent), not time. **Maintainer 2026-09-04: idle cylinders are kept for the life of their engine — SETTLED, not deferred.** | |
| 2g | sample PCM through the OOM catch | the last MB-scale site |
| | ✅ **DONE 2026-09-04** — `SampleStore(commLink, allocatePcm = ::allocatePcmOrNull)`: the `DoubleArray(totalSize)` a chunked upload's first chunk makes (on the audio thread, through the worklet's message port; the only MB-scale allocation the store makes — a JVM `Complete` arrives already allocated) goes through one caught site. A failure becomes `SampleEntry.AllocationFailed` (silent like `NotFound`, distinct so diagnostics can say "out of memory", and so the upload's remaining chunks are DROPPED rather than restarting the allocation each), counted in `allocationFailures`. Before, that OOM stopped the worklet for good. Two rows in `SampleStoreSpec`, 4 mutations red (later chunks restart, uncounted, not remembered, catch removed). Not routed through `ResourceWarehouse` on purpose: PCM is owned for the backend's life by the one store (no return path, nothing to shelve); the shape is the same, the site is the store's. | |

### Step 3 — cylinders join the warehouse; the warmup stocks it (2026-09-04, after the Fairphone)

**Measured on the Fairphone with 2a–2g in:** the first run of Der Schmetterling still killed the
playback. Its first frame builds eight cylinders and four reverb networks at once (no rings: the
song has no `delaytime`), and JITs Freeverb, the effect constructors and every ignitor graph the song
uses — the warmup only ever played a sine and a sample on orbit 0. ~1 MB of bytes, but the object
graphs and the JIT weigh as much on a phone. Maintainer's decisions: (1) build 16 cylinders at
warmup, **bucketed**, never all in one frame; (2) **cylinders join the warehouse**; (3) widen the
warmup's coverage; explicit synthetic voices, not a builtin song.

- ✅ `warehouse/CylinderUnits`: a shelf of whole cylinders. `Cylinder.retire()` = every bus effect
  off and cleared, lease freed, send buffers zeroed, ring and network handed back to THEIR shelves
  (a shelved cylinder holds nothing, so every idle byte is on exactly one shelf); `adopt(id, …)`
  re-labels a rented one. `Cylinders.getOrInit` rents, `releaseAll` gives back. `maxIdle = 32`;
  `ReverbUnits.MAX_IDLE_UNITS` raised to 32 to match.
- ✅ `WarmupRunner`: `WARMUP_ORBITS = 16` voices, orbit k starting mid-block k (one cylinder, one
  ring, one network per block — the same stall would otherwise just move into the warmup's first
  frame, inaudible but long enough to have the worklet dropped), each with delay + room + filter,
  rotating `WARMUP_SOUNDS` (sine, saw, supersaw, square, triangle, the all-zeros sample) so each
  ignitor graph is JITed before a song's first note of it. `warmupBlocks = 16 + 2` (round 3 shortened the tail), plus ~16 blocks until the shelves are clean, ≈ 90 ms at 48 kHz; worst case, if the shelves never come clean, the wait caps at 64 blocks and `BackendReady` goes out dirty at 18 + 64 blocks ≈ 219 ms.
  `cleanupHard` at the end is the return path: **16 cylinders, 16 rings, 16 networks on the shelves
  before `BackendReady`.**
- `CylinderShelfSpec` (5 rows): disposal returns/retires and the next engine takes the same instance
  under a new id; **a song on returned cylinders is bit-identical to the same song on fresh ones**
  (song A loud and wet with different settings on every orbit, phaser engaged; both runs at the same
  clock position, because the scheduled-time frame conversion rounds differently at different
  positions — a 1-LSB trap); `maxIdle` + double return; after warmup the shelves hold 16 of each,
  no block built more than one cylinder, and an 8-orbit wet song of every warmed sound makes zero
  allocations; every warmup voice actually sounded through a ring and a network with zero drops.
  10 mutations red (retire without `resetBusEffects` needed the phaser ENGAGED in both songs to
  die — the LFO phase is the state that crosses; units kept inside; old id kept; shelf never used;
  retire skipped; warmup un-bucketed; dry; without delay; fewer orbits).
- Not measured again yet: the Fairphone, after this. If it still stalls, the next suspects are
  per-note voice-graph construction and JIT of paths the warmup does not reach (body/vowel, master
  chains), and the measurement should be a profile, not a guess.

### Review round 3 (2026-09-04, two fresh Opus reviewers on 2d–2g + cylinders + warmup)

Both reviewers' MAJOR, which I had missed: **the teardown**. `retire()` zeroed every ring and
network twice (`reset()` then the shelf's `giveBack`), and the warmup's `cleanupHard` retired all
sixteen cylinders inside ONE render callback (~19 MB of stores, on the phone this exists for);
the same shape at every song stop (inside `renderBlockAt`) and at master-chain eviction (a 20 s
master ring is 24.6 MB, inside a command drain). The plan's own "clearing at return is away from
any onset" was false at every return site. **Fix: clearing is deferred housekeeping in the shelves.**
`giveBack` is O(1) and marks the unit dirty; `ResourceWarehouse.housekeep()` runs once per block
from the dispatcher and zeroes a bounded slice (one class-0 ring's frames + one network); `rent`
prefers a clean unit, any class, and zeroes a dirty one on the spot only when nothing clean fits
(what the allocation it replaces would have cost). `retire()` resets only the small effects. The
warmup holds `BackendReady` until `isClean`. This also retires round 1's "grow pays a clear at
note time" residual: a grow's old ring goes back dirty too.

Second MAJOR (coding): `SampleEntry.AllocationFailed` sent no `SampleReceived`, and the FE
preloader awaits that ack with no timeout — a failed upload hung the playback forever. Now acked.
MINORs, applied: the reverb's refusal latch blocked a free shelf hit (the delay's round-2 lesson,
re-learned: `ReverbUnits.rent(allocateOnMiss)`); the warmup bucketed on `RENDER_QUANTUM_FRAMES`,
not the host's block size (now `dispatcher.blockFrames`); `MasterChain.releaseUnits` left live
references (a `released` flag makes `process`/`reset` no-ops); a `LazyReverbSpec` row named the
catch but tested the happy path (renamed); the warmup now also reaches the phaser, compressor,
body and vowel constructors; stale KDocs. The audio reviewer's third MAJOR — sixteen wet
cylinders rendering for the warmup's last nine blocks — is answered by `TAIL_BLOCKS = 2`: the
peak lasts two blocks, and deactivation cannot fire inside the window anyway (round-robin tail
check, one cylinder per block). Rejected: retrying a failed sample upload (`contains` false would
re-upload megabytes on every note under the memory pressure that failed the first one; permanent
by design, documented). Noted, not changed: the master's `hasActiveTail` whole-ring scan lost its
10 s bound with `MAX_DELAY_SECONDS` (same follow-up as the orbit's, round 2). 17 mutations red.

### Review round 4 (2026-09-04, two fresh Opus reviewers on the round-3 fix)

One MAJOR, found by both: `rent`'s preference was class-unbounded. The clean-first pick could hand a
0.25 s delay an idle 32 s ring (every later `hasTail` scan O(ring)), and the dirty fallback could
zero a 24 MB master ring INSIDE an onset block — the round-3 stall relocated from the return to the
rent. **Fixed: best-fit-up is bounded at one class above the need** (the maintainer's "a 2 s request
takes an idle 4 s" is exactly the cap); past it the request allocates its own size, and an oversized
idle buffer serves only when allocation fails, clean before dirty. A second MAJOR (coding): the
warmup's ready-when-clean wait was unbounded on shared state — other playbacks return into the same
shelves, and the frontend gives up after 2 s and starts cold with the output still zeroed. **Fixed:
`maxCleanWaitBlocks` (4 × 16); past it `BackendReady` goes out dirty** (`readyWhileDirty`), which is
safe because `rent` zeroes what it hands out either way. Also: `dirtyCount` on both shelves so the
per-block `housekeep` is an integer compare while clean (100 % of playing time); the housekeeping
slice is `baseFrames` (rate-derived) not a literal; the offline `KlangAudioRenderer` housekeeps too;
`deniedRents` is per cylinder life; `isEngaged` seams are `internal`; a tautological assertion and a
row that claimed the right channel dropped/fixed; three stale docs. **Recorded, not changed:** a
master re-registration at the same class evicts a chain and rebuilds it in the same command drain,
so the rebuilt chain takes the just-returned dirty ring and zeroes it there — the same bytes a fresh
allocation would zero, i.e. the pre-2e cost, not the round-3 "O(1)" the triage line claimed for that
site. **Parked for the maintainer:** `Cylinder.tryDeactivate` → `resetBusEffects()` still zeroes the
kept ring and network synchronously in render every time an orbit goes silent (pre-existing, ~385 KB
+ a network at class 0); the alternative is to give the units back on deactivation (O(1), dirty; the
re-activation becomes a shelf hit, clean by then in practice). **Maintainer 2026-09-04: SETTLED, keep
as is** — "it does not really matter when we zero; keep a clean flag on each resource, and a resource
that comes off the shelf dirty is zeroed first; the price is paid in any case (except for one evicted
dirty, which is never zeroed)". That is the built design; the per-block slice is the optimisation on
top. 14 mutations red.

### Review round 5 (2026-09-04) — **clean on production code**

Zero CRITICAL/MAJOR defects in the code. The coding reviewer rated one item MAJOR as a *test gap*:
the sync-clean `dirtyCount--` in both shelves' `take` had no mutation coverage (a stuck count would
make `housekeep` walk the shelf every block and every warmup wait its full cap, silently). Rows added;
the mutant dies. The rest, all MINOR and applied: the oversized-under-OOM fallback now picks the
SMALLEST candidate first and uses cleanliness only as a tie-break (clean-first would have re-imported
the O(ring) tail scans the cap removed); a latched caller (`allocateOnMiss = false`) takes an
oversized buffer only if it is clean (a dirty one would be an unbounded clear with no allocation
having failed just now); the oversized-dirty zeroing has its own row; `readyWhileDirty` is an
`internal` test seam; the "costs what the allocation would" claim says "at most twice"; an orphaned
KDoc, a misplaced comment, a tautological assertion and an inert spec setup fixed; the plan's warmup
timing carries the worst case. 6 mutations red. **Loop closed here**: five rounds, the last one
clean on code; per the standard the remaining MINORs were applied as one batch without a re-review.

### Step 4 — the warmup vocabulary (2026-09-04, second Fairphone measurement)

**Measured with step 3 + rounds 3–5 in:** better, not perfect. The count-in is fine; when all the
other voices come in there is a spike followed by hiccups that stabilise; stop and run again and it
is fine. That is the signature of COLD CODE, not of resources: the song's instruments are custom
ignitors composed of node kinds the warmup had never executed (`eq` sections, `range`, `seg`,
`pitchEnvelope`, `crackle`, `unison` …), and each kind's first blocks JIT on the audio thread.
**Fix: `WarmupVocabulary`** — six synthetic graphs (waves, supers, noises, math, filters, effects)
that together touch every `IgnitorDsl` node kind and every `EqSection` kind, finite by
construction, registered on the warmup playback exactly as a song registers its own and rotated
over the sixteen orbits with the builtin sounds (twelve sounds, sixteen orbits). Guard:
`WarmupVocabularySpec` (JVM, reflection over the sealed hierarchy) fails when a node kind exists
that no vocabulary graph executes and is not excluded with a reason; every graph must render
audible, finite audio through the real engine, one fresh engine per sound (a shared one let an
earlier tail make a muted graph look audible, a mutation caught that). 5 mutations red; one
equivalent (`div` by a zero constant does not produce NaN — the engine guards it). Maintainer's
rule for this step: "if the solution introduces undue complexity, keep as is and defer to
docs/tasks/future" — judged not undue (one file, one spec, three lines in the runner).
**What the vocabulary does NOT cover, by design:** a song's own registered ignitor still builds
its graph on its first note (small, warm code); per-note voice construction; sample decoding.

### Step 5 — the tail question in closed form (2026-09-04)

The one follow-up every review round noted and the maintainer picked up: with no ring ceiling,
`DelayLine.hasTail()` / `Reverb.hasTail()` were O(unit) scans on the audio thread, run from
`Cylinder.tryDeactivate` (round-robin) and `MasterChain.hasActiveTail` (every 10 silent blocks).
**Built: `effects/TailCountdown`.** The drain proof the Draining state already trusted
(`drainSamplesUntilSilent`: the content ceiling falls by |feedback| per period, so after a
computable number of periods the unit is provably below −100 dBFS and cannot come back) is now
applied to the ACTIVE state too, triggered by the unit's INPUT: audible send → tail, no measurement;
the block the send turns silent → one bounded read (`tapWindowPeakAbs`, proportional to the delay
time, or `combPeakAbs`) starts the countdown; then subtraction. Idle (never fed since reset) → no
tail, no read. A parameter change mid-proof invalidates it (re-measured on the next silent block).
Self-oscillation → an infinite proof, pinning the orbit until the owner says off (raw engine).
The master chain keeps one countdown PER time-based stage, fed from that stage's own input: a
delay ahead of a reverb feeds it echoes after the bus went silent, so a single chain-wide
countdown measured at the bus was wrong by exactly the delay's decay (a spec caught it). The
scans stay in `DelayLine`/`Reverb` as test oracles with a "no production caller" comment.
Rendered samples unchanged; deactivation timing is "provably silent" instead of "the scan said
so" — conservative on the audible side, and shorter only for content the tap can never reach.
`TailCountdownSpec` (8 rows) + `ClosedFormTailSpec` (7 rows, the scan as the oracle); 10 mutations red.

**Step 5, review round 1 (2026-09-04):** the countdown was the wrong closed form. Both reviewers:
the "one bounded read at silence onset" fires once per NOTE GAP and is still O(delay time), a net
loss on sparse material with long delays; a grow could skip the invalidation (the compare ran
against a fresh line's constructor defaults) and cut a live echo; a non-finite parameter made the
invalidation fire every block (setters drop non-finite writes, so the compare never settled) —
the orbit pinned and the read per block; `Idle` collapsed into `Counting` after one silent block;
"silent input" at ≤ 1e-5 is not the exact zero the drain proof assumes (steady floor
`input/(1−fb)`, 1e-3 at fb 0.99). **Rebuilt as `effects/TailCeiling`: a running CEILING on the
unit's content, maintained from the block's input PEAK, no reads at all.** In windows of one
recirculation (+1 sample so a read never reaches past the previous window),
`current = peak·(1+fb+…+fb^(laps−1)) + |fb|·previous`; silent input → geometric decay by |fb| per
window (the drain proof); sub-threshold input → the true floor; |fb| ≥ 1 → pinned; parameter
changes need no invalidation (the next window decays by the feedback in force, as the ring does);
a never-fed unit is 0. `laps` is computed by the unit (ring: 2, only a window's first sample laps
inside it; Freeverb: 2, comb lengths under 2×). The master measures the send after `wet`, like the
orbit effects. The scans (`DelayLine.hasTail`, `Reverb.hasTail`) remain test oracles. Also fixed:
the vocabulary's `math` graph now IS "recip of +2" as its doc said (it was 1/sin² near zero
crossings, 1e13-class samples through the combs) with a `peak < 10` bound in the spec; two spec
rows that could not fail; the released master chain reports no tail. `TailCeilingSpec` (9 rows) +
`ClosedFormTailSpec` (7 rows, scans as oracles); 12 mutations red.

**All of 2a–2g shipped 2026-09-04.** The `ctx.scratchBuffers → ctx.warehouse.scratch` rename turned
out to be MOOT: `AudioBackendContext` no longer has a `scratchBuffers` at all (the warehouse owns it and
`VoiceScheduler` reads `context.warehouse.scratch`); the remaining `scratchBuffers` fields sit on the
per-block render contexts (`IgniteContext`, `BlockContext`, `Voice.Ctx`), where a direct field is the
right thing in a hot path and the name says what it holds. Dead `nowFrame` removed from
`VoiceFactory.makeVoice` and the scheduler's `activateVoice` (2026-09-04). Left open: the reporting half
(counters → FE feedback, with `droppedVoices`), and the **Fairphone measurement**, which is the
maintainer's.

If 2b removes the stutter, 2c–2g are memory hygiene and correctness rather than the audible fix, and
their urgency can be judged then.

## Scheduling

The 2026-07 stub said "last". That was decided when this was a pool project. As a right-sizing
change it is the **only remaining Layer-1 item that is audible**, and exactly the kind of thing that
would otherwise ambush the tutorial phase on a slower device. It moves up.

## Folded in from the old stub, still valid

- `Cylinders.preallocateAll()` is dead (zero callers) — delete it; warmup pre-fills nothing by
  guessing (see the shelf).
- `Cylinder.tryDeactivate()` deactivates but never removes from `id2cylinder` — that gap **is** D4,
  step 2f.
- Rented units must be **`reset()` on rent**: a pooled unit carries a *different owner's* tail — the
  hazard `DelayLine.reset()`'s KDoc already warns about.
- `MasterBus.MAX_CACHED_CHAINS = 8` exists only because chains own unpooled buffers; once chains rent,
  the cache can hold lightweight descriptors and the bound can relax.
- Unbounded-tail hole: an orbit delay at `feedback >= 1.0` pins `cylinders.anyActive()` forever so
  its engine is never disposed; the master has a 20 s bound, the orbit path has none. Fix while D4 is
  open.
