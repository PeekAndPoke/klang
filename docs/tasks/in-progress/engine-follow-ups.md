# Engine follow-ups after the tidy-up

**Paused 2026-10-12 (maintainer: weekly token budget), on a stable point.** Item 2 is DONE (KlangMap, released in
v0.6.5, step 4 merged after). Where to continue, open V8 items first: 31 (what item 29 left on a full `classic()`
voice), 32 (the delay's and reverb's per-block `configure`), 34 (the bus distort's oversampled path), 39 to 41 (the
Katalyst knob expressions' residues), 10c and 10d (fm); the known tails 4, 36 and 38; then sections 2 to 5 (13, 11 and the small ones)
and section 6. Open with the maintainer: C1 to C11 (the FM diagnostic is C6) in
`docs/housekeeping/2026-10-10-baseline.md`, and Q13, Q29 (`duty(width)`) and Q30 to Q33 in
[`_maintainer-questions.md`](../_maintainer-questions.md).

Status: **open, collected 2026-10-09** when the engine tidy-up was archived
([`20261009-engine-tidy-up.md`](../../tasks-archive/2026-10/20261009-engine-tidy-up.md), "the record" below; v0.6.0). Each
item was checked against the later sections of the record and against the code at `662aa8db`; only what is still open
is listed. Nothing here changes a surface a tutorial teaches, except where an item says so. A behaviour-neutral item is
proven the usual way: bit-identity on the 18-song corpus and Kokon, and for an allocation item a measurement on the
production bundle, pinned AND unpinned (`audio/ref/performance.md`).

Sizes: S (an hour or two), M (a day), L (several days), as in the audit.

**Assessed and discussed (2026-10-09).** The assessment, item by item with expected outcome and a recommendation:
`tmp/reviews/engine-follow-ups-assessment.md`. The maintainer's answers:
- **The phone runs Chrome** (the Fairphone 4), so the V8 numbers here are the ones that matter.
- **Where Kokon still glitches:** when the drum set comes in, for a few seconds, then it settles. Likely first-use
  resource allocation (`future/first-run-spike-v2.md`) and the voice build in the render callback (item 11), not the
  steady per-block items. "Fine for now, unless there is a simple fix in the resource warehouse, where we maybe create
  more resources upfront."
- **Sequential, no rush:** these follow the pitch pipeline and the inharmonic partials, one at a time. The first two
  candidates by expected outcome are the shaper's per-sample boxes (item 9) and the noise voice (item 3), each to be
  measured on the production bundle first.

## 1. Allocation on V8 (the worklet)

These are V8 only. The JVM allocates nothing per block in steady state except item 12's `Double` per
block-constant param read (0 to 48 bytes per block; gone since item 12, step 2: see items 8 and 12). The method and the rules learned are in
`audio/ref/performance.md`.

1. **DONE (2026-10-11): every box located in the stages' block processing is gone, and the phaser's per-block
   `configure` skips an unchanged config (review round 1); the delay's and the reverb's `configure` is item 32.** Reported as: after the V8 pass, bytes per block at engine level, production bundle pinned: delay 82, reverb
   22, phaser 144 (192 with floor 0.5), all three 197; development pinned: 131, 102, 96 (128), 276. The boxing sites
   were not located. Source: the record, "The V8 allocation pass" (the result table) and "Found during tidy-up step
   12". **Measured on the production bundle** (`:audio_be:compileTestProductionExecutableKotlinJs`, node 22), one
   sustained note through `PlaybackEngineDispatcher` (the master stage runs too) with the stage's `katalystParams` on
   the classic orbit chain: a sine at middle C with the delay (wet 0.3, time 0.25, feedback 0.5; and time 0.2501,
   feedback 0.45, a fractional tap), the reverb (wet 0.3, size 5; and size 6.3 with a lowpass), the phaser (wet 0.5,
   rate 0.5, sweep 800; and floor 0.5), all three, the same on the classic saw, on `C-full`'s voice and on a 7-voice
   supersaw; single-case and mixed (every case warmed round-robin in one process, then each measured between two
   forced GCs, whole heap numbers; e7's method), pinned and unpinned. Reproduced first (HEAD, mixed, 4 processes,
   bytes over the same engine without a stage): delay 17 to 97, fractional delay 65 to 177, reverb 5 to 21, phaser 32
   to 64, with floor 48 to 96, all three 69 to 149, all three fractional with floor 149 to 293.
   **Sites** (sampling heap profiler, `--trace-turbo-inlining`, `--no-concurrent-recompilation` traces per compile,
   single-case and mixed), each a double crossing a call V8 did not inline:
   - `KnobGlide.advance()`'s result (111 bytes), left out of the delay's and the reverb's `process` (the settled glide
     of the feedback and the size, its result discarded and still boxed in the callee): one per block each.
   - The delay's and the reverb's tail step: `DelayLine.reachSamples` and `shortestSamples` (88 bytes each) behind
     `tailWindowSamples` and `tailLapsPerWindow`, left out (one each when the tap is fractional), and in some compiles
     `TailCeiling.observe` itself (358 bytes, three doubles), its `fresh`, `currentDelaySamples` and `isFinite`.
   - `DelayLine.processInternal` (past the 460-byte limit, reason 5) took the tap's fraction, the feedback, its step
     and the cap as arguments, twice per block: one or two per block, in every process.
   - The delay's ring check in `configure`, every block: the `toInt()` inside `framesFor(time)`, a stdlib call
     (`numberToInt`, 28 bytes) V8 left out, handed a `Math.ceil` that V8 cannot rule out as -0, so even a whole
     frame count crossed as a heap number.
   - The phaser: `PhaserCore.alphaAt` (twice per core per block, its phase and result) left out of `Phaser.process`
     when pinned, about five per block; in `configure` the depth's `coerceIn` (a stdlib call), the per-block rewrite
     of the constant feedback 0.5 (`PhaserCore`'s setter left out), the rate setter and the wet/dry law
     (`Phaser.dryCoeff`/`wetCoeff` through `WetDryMix`).
   With ten times V8's inlining budgets the stages add 1 to 7 bytes per block (one process of three; the other two
   then boxed per sample elsewhere), as the pass found: all are budget boxes.
   **Fix** (remedies the rules name, no ADAA file edited):
   - `KnobGlide.advance` is Kotlin `inline` (`audio_be/.../KnobGlide.kt`; the state it reads is `internal`).
   - `DelayLine.observeTail(ceiling, input, frames)` and `Reverb.observeTail(...)`: one call that takes no double and
     returns none; `TailCeiling.observe`, `fresh`, `saturate` and `peakOf` are Kotlin `inline` (the ceiling's state
     `internal`), and so are `DelayLine.currentDelaySamples`, `reachSamples`, `shortestSamples` and the reverb's
     `effectiveFeedback` and `feedbackFor`. `DelayLine.tailWindowSamples` and `tailLapsPerWindow` are gone (their KDoc
     moved to `observeTail`), the reverb's two are private. `fresh`'s loop seed takes the `* 1.0` of the seed rule.
   - `DelayLine.blockArgs`, a `DoubleArray` holder for the loops' per-block doubles (an array, item 7's lesson).
   - `KatalystDelayEffect.ensureRing` compares the ring's capacity with `framesNeeded(time)` in doubles, so the
     `toInt()` runs only when a ring is rented or grown (the same decision for every time).
   - `PhaserCore.alphaAt` Kotlin `inline`; `Phaser.depth`'s clamp written out (item 29's precedent); the phaser
     stage's `configure` no longer rewrites the constant feedback (set where the cores are made and in
     `resetForReuse`, written nowhere else).
   The KDoc at each site is the guard.
   **Bit for bit:** raw doubles of the probe, 600 blocks, both channels, HEAD against the tree, 30 of 30 cases
   identical on V8 and 30 of 30 on the JVM, every last block sounding: every stage case above, an owner-change case
   (the delay crossfades to a new tap, the feedback, size and phaser knobs glide, then the delay and the reverb
   drain), four at 220 Hz, the engine saw and `C-full`, and the harness's voice cases. The corpus 18 of 18
   (`CORPUS GREEN`). `:audio_be:jvmTest` 2,662 of 2,662 and `:audio_be:jsBrowserTest` 2,558 of 2,558.
   **Bytes per block, HEAD / tree / HEAD again (a byte copy), mixed, one value per process** (unpinned 6 shuffled
   processes, pinned `taskset -c 11` 3), ranges over the processes; the engine without a stage reads 16 to 48:

   | case | mixed unpinned | mixed pinned | single-case unpinned (medians of 3) | single-case pinned |
   |---|---|---|---|---|
   | engine, no stage (control) | 16-48 / 16-48 / 16-48 | 16-48 / 16 / 16 | 62 / 67 / 66 | 41 / 52 / 16 |
   | delay | 65-145 / 16-48 / 65-145 | 65-176 / 16-96 / 81-145 | 104 / 67 / 93 | 58 / 46 / 92 |
   | delay, fractional tap | 113-209 / 16-48 / 129-257 | 160-241 / 16-96 / 129-209 | 166 / 67 / 165 | 151 / 31 / 119 |
   | reverb | 21-69 / 16-48 / 21-84 | 37-53 / 16 / 21-69 | 57 / 67 / 82 | 51 / 31 / 42 |
   | phaser | 80-112 / 48-96 / 80-112 | 80-112 / 64-80 / 48-80 | 98 / 77 / 108 | 176 / 68 / 196 |
   | phaser, floor 0.5 | 96-144 / 80-112 / 96-144 | 96-144 / 80-112 / 64-96 | 124 / 114 / 123 | 166 / 82 / 176 |
   | all three | 133-197 / 48-96 / 133-213 | 149-245 / 64-160 / 133-229 | 134 / 88 / 171 | 207 / 63 / 232 |
   | all three, fractional, floor 0.5 | 213-293 / 80-112 / 213-357 | 229-341 / 80-192 / 197-309 | 222 / 113 / 217 | 279 / 92 / 284 |
   | `C-full`'s voice, all three | 245-341 / 112-144 / 245-389 | 277-373 / 128-224 / 245-357 | 263 / 139 / 279 | 357 / 129 / 335 |

   Unpinned and mixed, the delay and the reverb read exactly the engine without them in every process, and all three
   read what the phaser alone reads; `--predictable` single-case (medians of 2, HEAD / tree): fractional delay 176 /
   83, reverb 52 / 36, phaser with floor 206 / 129, all three 377 / 176 (control 36 / 36). The voice cases are
   unchanged (`spluck-a` read 288 in one tree process, item 7's tail). One heap number is 16 bytes on node and 12 in
   Chrome. **Time** (mixed unpinned, medians): delay 8,917 / 8,468 / 8,994 ns, fractional 8,933 / 8,517 / 8,824,
   reverb 17,232 / 17,117 / 17,362, phaser 8,942 / 8,793 / 9,090, all three 21,929 / 21,440 / 21,440, `C-full` with
   all three 28,517 / 28,338 / 28,469, the control 6,698 / 6,604 / 6,649: 0.95 to 1.00 of HEAD. **JVM** (`ThreadMXBean`,
   7 rounds, every case in one process): 0 B per block on both sides in all 26 cases, time 0.88 to 1.04 of HEAD.
   **What is left, and why:**
   - **The per-block `configure`** (the first form's residue). A writer re-applies the resolved knobs every block
     (`KatalystChain.applyParams`), and in a mixed profile, where most orbits take the off arm, V8 can compile a
     stage's `configure` with nothing inlined but `isFinite` (a trace of such a process: the delay's setters, both
     `KnobGlide.retarget`s and `ensureRing` left out), so every knob crosses a call: the phaser 2 to 4 heap numbers per
     block in every process, the delay and the reverb up to 5 in some processes (two of three pinned mixed processes,
     rarely unpinned). In single-case processes the phaser's residue was `WetDryMix.dryCoeff` and `wetCoeff`, left out
     of the writer's budget (reviewer B, round 1). The phaser's half is fixed in review round 1 (below); the delay's and
     the reverb's is item 32.
   - **An off phaser's call** (`KatalystPhaserEffect.process` into `Phaser.process` while bypassed) costs about one
     heap number per block in single-case processes, not in mixed ones; a guard around the call removes it, an early
     return inside does not. Not explained, inside the noise band. (So "every box located": this one is located, not
     removed.)
   - Outside the stages, in every engine case: item 30's root frequency (16 B at middle C) and about 16 to 32 B in
     `PlaybackEngine.renderBlock` (its per-block seconds handed to calls; not located further).
   - The development bundle was not measured (the fixes are the same source on both bundles).
   - **Two compiled forms in ADAA-owned files change, unmeasured:** `KnobGlide.advance` is now inlined into the orbit
     distort stage (`KatalystDistortEffect`'s `amountGlide.advance()`); bit for bit by construction, but no case and no
     probe runs that stage, so its bytes are not measured (a note for the ADAA session). `PhaserCore.alphaAt` inlines
     only into `PhaserCore.prepareBlock`, so the voice phaser in `IgnitorEffects.kt` compiles as before (reviewer A).
   **The harness guards it with two cases** (`AllocationCases.kt`): `E-delay` (the classic saw, the fractional delay)
   and `E-stages` (all three, the phaser with floor 0.5). Budgets by the file's rule from this change's runs (3 runs
   each side, interleaved): `E-delay` V8 112 (tree rounds 30 to 71, medians 62 to 67; HEAD's code 125 to 171, medians
   160 to 171), `E-stages` V8 176 (tree 77 to 130, medians 92 to 102; HEAD's code 203 to 346, medians 217 to 259), JVM
   6 for both (0 B). HEAD's code (the nine source files at HEAD, the new cases kept) read RED in 3 of 3 runs, both
   cases over (over the first lines, 120 and 200, and every median above the final ones); the tree GREEN 3 of 3, 34 of
   34, and once more on the first lines, 112 and 176 (`E-delay` 32, `E-stages` 109 B, the first form). The item 8 mutant (`readParam` a plain function) against the full budget set, one lock call: RED, 10 of 34
   over (`sawC` 78, `C-offrel` 67, `super7` 78, `super7a` 94, `spluck-a` 381, `sgpad-vib` 114, `C-dist` 114, `C-full`
   180, `E-delay` 128, n16 1,860 B); `E-stages` read 164, under its 176. The case list changed, so the driver starts
   a new scoreboard table at its next run; no row written. Report: `tmp/reviews/e1-report.md`.
   **Review round 1, applied** (`tmp/reviews/e1-r1-A.md`, `e1-r1-B.md`; 1 MAJOR, 7 MINOR, 6 NIT; the code bit for bit
   in every path, the numbers reproduced):
   - B1 (MAJOR) and B MINOR 1, 2: `E-stages`'s first guard text claimed the reverb's and the phaser's sites; a revert of
     their five files read 130 B, green under 176 (reviewer B). The texts now say what each line guards (the budget
     file, its header, this item, the harness task doc): `E-delay` the delay's sites as a whole (a revert of the glide
     alone read 61 B); `E-stages` a revert of everything, the delay's sites with all three running, the per-sample
     class in the reverb and the phaser, and their per-block sites only in part.
   - B MINOR 3, built: the phaser stage skips an unchanged config (`KatalystPhaserEffect.applied`, the house pattern
     of the EQ, the resonator and the compressor): the five raw knobs compared with the last ones applied, NaN-aware
     and with -0.0 told apart, in a `DoubleArray`; `reset` invalidates it. Exact, because every write of `configure` is
     a function of the five knobs and of state only `configure` and `reset` change. The delay's and the reverb's half
     is item 32 (a skip is not exact there without more work). Bit for bit: 30 of 30 dumps on V8 and on the JVM, HEAD
     against the tree, the owner-change case among them; the corpus 18 of 18. Bytes, HEAD / tree (round 1 form),
     one value per process:

     | case | mixed unpinned (6) | mixed pinned (3) | single-case unpinned / pinned (medians of 3; first form between) |
     |---|---|---|---|
     | engine, no stage (control) | 16-48 / 16-48 | 16-48 / 16 | 63 / 67 / 62, 21 / 68 / 72 |
     | delay, fractional tap | 97-209 / 16-48 | 129-193 / 16-32 | 160 / 67 / 62, 139 / 36 / 17 |
     | reverb | 21-69 / 16-48 | 21-101 / 16 | 83 / 62 / 31, 37 / 42 / 73 |
     | phaser | 64-112 / 16-48 | 64-112 / 16 | 139 / 93 / 46, 165 / 47 / 26 |
     | phaser, floor 0.5 | 80-144 / 16-48 | 96-144 / 16 | 119 / 108 / 46, 191 / 72 / 11 |
     | all three | 101-197 / 16-48 | 133-245 / 16-32 | 180 / 73 / 51, 216 / 67 / 10 |
     | all three, fractional, floor 0.5 | 165-309 / 16-48 | 213-325 / 16-32 | 232 / 109 / 47, 304 / 73 / 11 |
     | `C-full`'s voice, all three | 197-357 / 64-96 | 261-357 / 64 | 283 / 144 / 83, 330 / 129 / 52 |

     In every mixed process every stage case now reads what the engine without a stage reads, but for one pinned
     process with the delay one heap number above it. The time column of that run is within HEAD's (all three 25,922
     against 26,422 ns, in a run where the machine was busier). The allocation harness on the round 1 form, 6 tree
     runs: GREEN every time, `E-stages` 11 to 51 B (medians; rounds 10 to 57), `E-delay` 62 to 67.
   - The `E-stages` line re-set from 176 to 96 by the file's rule (45 B above the highest healthy median, 39 above the
     worst round). It catches a revert of everything (HEAD's code 217 to 259), and the item 8 mutant now too (one lock
     call: RED, 11 of 34 over, `E-stages` 108, `E-delay` 128, `C-full` 171, `sawC` 77, n16 1,867 B). A revert of the
     reverb's and the phaser's five files (each run in one lock call, restored and checked with `cmp`): medians 128,
     129, 129 B in three runs and 93 in a fourth (rounds 92 to 222), so it reads red in three runs of four, not always.
   - A MINOR 1: `DelayLineSpec`'s two crossfade rows take fractional taps (0.0101, 0.0203, 0.0307 s), so the holder's
     crossfade slots are pinned (mutation: the `ARG_TO_ALPHA` write dropped, RED, 2 of 19; green on the old times).
     A MINOR 2: a `LazyRingSpec` row, "an installed ring keeps serving up to exactly its class, and grows one frame
     past it" (mutations `>=` to `>` and the margin dropped from the steady compare: RED, 1 of 21 each). A new
     `KatalystPhaserEffectSpec` row pins the skip's invalidation ("a reset under an owner whose knobs stand": the
     invalidation dropped, RED; a compare that leaves out the floor is red on an existing row). Each mutation in one
     lock call, restored with `cp` and checked with `cmp`. `:audio_be:jvmTest` 2,664 of 2,664, `:audio_be:jsBrowserTest`
     2,560 of 2,560.
   - A MINOR 3 and the NITs: the History line says "unpinned"; the `KnobGlide` comment; "the `toInt()` inside
     `framesFor(time)`"; `TailCeiling`'s "How" names `observeTail`; the phaser stage's comment says it no longer forces
     0.5 onto the `Phaser` it is handed; this item's headline scoped. B MINOR 4: the ADAA bullet above.
   **Review round 2** (`tmp/reviews/e1-r2/review.md`, clean: 0 MAJOR, 2 MINOR, 2 NIT; a revert of the reverb's and the
   phaser's five files red in 4 of 5 runs across the worker and the reviewer). MINOR 1, applied: two
   `KatalystPhaserEffectSpec` rows pin the skip's sweep term and its NaN arm (the writer hands rate, center and sweep
   raw, and an unset slot is NaN): "the skip sees a change of the sweep alone" (the sweep left out of the compare: RED)
   and "an unset knob (NaN) followed by a set one is a change" (the NaN arm matching any value: RED); both mutants had
   passed the whole suite. MINOR 2 and NIT 1: `audio/MEMORY.md` and `audio/ref/performance.md` take this item's
   wording (every box located; the off phaser's call located, not removed; one pinned process one heap number above),
   and the first lines' readings above are labelled as such.
2. **DONE (2026-10-12), in five steps (1, 2, 3 and 5 on 2026-10-11, 4 on 2026-10-12): every Kotlin/JS `HashMap` lookup
   allocated 40 bytes on V8; the worklet's slot maps (step 1), sprudel's param bags (step 2), the sound registry
   (step 3) and the dispatcher's engines (step 5) are a `KlangMap` now, and a `KlangMap` is not a Kotlin `Map` (step
   5).** Reported as: a new param map's resolve costs about 1,130 bytes on the
   classic chain after S1, attributed to `KatalystKnob.resolve`, whose code does not explain it. **The cause**
   (production bundle, node 22.12, a probe with the lookups alone, the knobs alone, the chain, two voices taking turns
   as the orbit's owner; mixed and single-case, pinned and unpinned): the stdlib's generic `hashCode(dynamic)`, which
   every `HashMap`/`HashSet` `get`, `put` and `containsKey` calls, ends in `else -> throw`, a closure that captures
   the `typeof` result, so V8 creates a 40-byte function context on every call (node x64; about 20 in Chrome,
   inferred). 27 lookups read 1,080 B exactly in every process, hit or miss, empty map or full; a new orbit map 1,096 B
   (plus the reverb's documented lowpass box); a classic voice build makes 24 lookups whenever its `ignitorParams` map
   is not null, up to 76 with many slots set. A counting copy of the bundle: no steady block hashes at all. A bundle
   copy with only that `throw` arm rewritten read 0 B per lookup and 16 B per new map: the ceiling. The JVM allocates
   nothing on a lookup (24 B per new map, the lowpass box). Tried and rejected: the lookup written out as a scan over
   the entries (its iterator escapes wherever V8 does not inline the loop, 1,554 B on a one-entry map; on the JVM an
   iterator per knob, 888 B). Report: `tmp/reviews/e2-report.md`; the design, `tmp/reviews/e2-klangmap-proposal.md`.
   **Step 1** (the maintainer's direction: "a multiplatform KlangMap with dedicated implementations on each platform",
   in `common`): `KlangMap<V>` (`common/src/*/kotlin/infra/KlangMap.kt`), a `String`-keyed `Map<String, V>` with three
   writers (`set`, `setAll`, `copy`; `setAll` and not `putAll`, which clashes with `java.util.Map.putAll` on the JVM;
   `remove` dropped in review round 1, no caller), over a native JS `Map` on JS and a `LinkedHashMap` on the JVM,
   read-only to every reader on both (the JVM hands out the views of one `Collections.unmodifiableMap`, and the
   compiler's `java.util.Map` writers throw); `KlangMapSpec` pins the contract on both platforms against
   `LinkedHashMap` (14 rows), `KlangMapJvmSpec` the `java.util.Map` writers; every mutant red. Read it by key, never
   walk it on a hot path: a walk allocates about 110 B per entry on JS (`audio/ref/performance.md`). On the JVM the
   warm-up now trains on `KlangMap` while real JVM voices carry sprudel's `LinkedHashMap` until step 2 (a bimorphic
   site, negligible). `wireDecodeStringDoubleMap` returns one, and so
   do the engine's two producers (`WarmupRunner`, the sample-envelope merge). No wire type, codec, schema hash,
   `@WireName` or signature changed; `IgnitorDslRuntime.kt` untouched. A new orbit map now costs about 16 B (the
   lowpass box) under a mixed profile, a voice build loses its 24 to 76 lookups (957 to 3,062 B and 10 to 38 percent
   of its time in the probe). **Bit for bit:** raw doubles of the probe, 600 blocks, both channels, HEAD's three
   changed files against the tree, every voice through the generated wire codec on V8 (so its maps are the decoder's):
   18 of 18 identical on V8 and 18 of 18 on the JVM, every last block sounding (the classic chain with ten map
   contents and a burst of ten different maps, two voices taking turns as the owner, five engine cases, two with
   `ignitorParams`). The corpus 18 of 18 (`CORPUS GREEN`). `:common` 128 (JVM) and 130 (JS),
   `:audio_bridge:jvmTest` 148 and `:audio_bridge:jsBrowserTest` 280 (a new round-trip row: the decoded slot maps are
   `KlangMap`s, in order, NaN kept; red with the decoder reverted), `:audio_be:jvmTest` 2,675 and
   `:audio_be:jsBrowserTest` 2,571, green. **The harness guards it with a new case**, `C-remap`: two classic saw
   voices, received through `receivedByEngine` (`audio_benchmark/.../engine_inbox.kt`: the wire codec on JS, the voice
   as it is on the JVM), take turns as their orbit's owner, so the chain re-resolves every block. 6 runs GREEN, 36 of
   36: healthy rounds 76.5 to 87.5 B with two single rounds at 143.9 and 149.8 (medians 77 to 88), JVM 24 B (the
   lowpass box). Budgets by the file's rule: V8 192, JVM 36 (it reads 24, so 6 does not apply). The decoder reverted
   (one lock call, restored and checked with `cmp`): RED, `C-remap` 1,128 to 1,164 B, the only line over. The item 8
   mutant: RED with ten voice and engine lines, `C-remap` rounds 154 to 212 B, median 160 (review round 1: 139 to 145,
   median 140), the median under its line: not an item 8 line. Time: `C-remap` 2.03 to 2.16 us per block against 2.80
   to 3.11 with the decoder reverted, about 0.9 us per new map. No `C-build` case: one decoder function serves both fields, so `C-remap` sees a revert whole, and a voice
   build per block reads about 21 KB, where the 2.2 KB step is about 10 percent and the harness's spread there is not
   measured. Report: `tmp/reviews/e2-step1-report.md`.
   **Measured, not removed:** in single-case processes (the harness runs one case per process) three writers' `resolve`
   box one heap number each per new map: the body's and the vowel's, whose `fill` V8 leaves out and which hands the
   unset index (NaN) to `ResonatorTables.at`, and the delay writer's (fully inlined; not explained). `C-remap` reads
   77 to 93 B (medians) with them, the mixed probe 32 to 48 B.
   **Step 3 DONE (2026-10-11): the per-note lookups.** Located (probe cases `reg-*` and `N-saw`, a short saw note per
   block through the dispatcher; a counting copy of the bundle: 8 stdlib hashes per note, 6 on `saw`, 2 on the
   playback id): the registry's `contains` from the scheduler's prefetch and from `VoiceFactory`, and its `optimized`
   from `createExciter`, each hashing on the playback's fork and again on its root, and `engineFor`'s two lookups. A
   second cost turned up: V8's `toLowerCase` allocates a fresh string even when nothing changes (23.9 B per call in a
   node loop at every length measured, 1 to 8 characters; in the engine's path a 2-character `"bd"` paid it too), and
   the registry lowercased the name at every level of the fork chain, 7 times per synth note. HEAD: 128 B per registry
   lookup on V8, about 488 B per synth note. Fixed with the smallest changes: the registry's two maps are `KlangMap`s
   (String keys, never removed from); its lookups ask for `lowerKey(name)` once per call and hand it down the chain
   (the fork before its parent); `lowerKey` is exactly `name.lowercase()` but returns the name itself when no
   character could change (an index loop; any ASCII capital or any character above 127 takes `lowercase()`; review
   round 1, after round 0 tried the name as given and lowercased on a miss); `engineFor` looks the id up once
   (`scheduledAgain` returns whether the engine stays attached). The dispatcher's `engines` map stays a
   `LinkedHashMap` (it is removed from and walked, and `KlangMap` has no `remove`): one hash per scheduled voice, 40 B,
   is left (gone in step 5, where `KlangMap` gained `delete`). V8 now (the worker's probe, single-case): a registry lookup about 1 B, hit or miss (a mixed-case name 24 B,
   its lowercased copy); a synth note per block 13,257 / 12,806 B per block (HEAD / tree), a sample note 1,033 / 737
   (`"snare"`) and 1,066 / 773 (`"bd"`). JVM: 0 B per lookup on both sides (a mixed-case name 48 B, both sides).
   **Bit for bit:** the engine dumps (HEAD's registry and dispatcher against the tree, 600
   blocks, every voice through the wire codec on V8, a note per block among them) 19 of 19 on V8 and 19 of 19 on the
   JVM; the corpus 18 of 18. `:audio_be:jvmTest` 2,678 and `:audio_be:jsBrowserTest` 2,574, green (after review round 2). A new
   `IgnitorRegistryTest` row: a mixed-case name finds the lowercase registration through a fork, the fork's own before
   its parent's, for `get`, `optimized`, `contains` and `createExciter`; review round 1 added a non-ASCII row (`Ärger`,
   `ölbass`). Mutants, round 0 (each in one lock call, unfiltered): the lookups without their lowercase retry RED;
   `scheduledAgain` detaching a stopped engine RED, keeping a releasing one RED. Round 1 (the reviewers' list): the
   parent asked before the fork in `authoredAt` and `optimizedAt` RED (2, 1), the parent arm of `containsAt` dropped
   RED (133), `register` storing the name as given RED (3), `createExciter` without lowercasing RED (1), `Playing`
   detaching RED (41), `Disposed` detaching GREEN (unreachable: a disposed engine is never filed), `lowerKey` never
   lowercasing RED (3). **The harness guards it with a new case**, `E-C-n1` (in `AllocationCases.kt`; README): a short
   classic saw note every block through the dispatcher. Healthy rounds 12,982 to 13,036 B (about 30 rounds, the
   worker's 7 runs and review round 1's), JVM 6,616 B; the HEAD code 13,435 to 13,476 B, RED against the line, the only
   line over. Budgets by the file's rule: V8 13,100 (64 B above the worst healthy round, 335 below HEAD's lowest), JVM
   6,628 (12 B above the reading: one `Double` per note reads 6,640, RED). It catches the registry back on a HashMap
   (13,235 to 13,254) and the lowercasing back at every level (13,153 to 13,184); it does not catch `engineFor`'s second
   lookup (40 B, 13,024 to 13,048) nor a lowercase on every call (about 72 B per synth note, 13,059 to 13,075, 25 B
   under the line): the comments at those sites guard them. A voice build per block, so any change to the voice build
   moves it. A first JVM probe run read 24 B on the engine cases of the tree; the repeat read
   24 B on HEAD's and 0 on the tree's (the JVM's process-dependent profile), and the alloc check read 0 on every
   engine line. Report: `tmp/reviews/e2-step3-report.md`.
   **Step 2 DONE (2026-10-11): sprudel's `ParamBag` and `classicSlotParams`' copy are a `KlangMap`, public API
   unchanged.** Measured first (probe `tmp/reviews/e2/s2/probe/`, sprudel's production test bundle, node 22.12): each
   case queries one cycle of a pattern per step and converts every event with `toVoiceData`, on JS plus the wire
   encode (`encode_ScheduledVoice`, the browser's next step); the cases: a plain note, three bus doors, six voice doors,
   many doors of both kinds, a patterned control, and the bag's own operations. HEAD hashed through the stdlib 0, 64,
   14, 143 and 29 times per event. **The first form** (the bag on a `KlangMap`, `copy` through `new Map(source)`, the
   merges and the encoder walking `entries`) cut the time by about a third or more but saved only about a tenth of the bytes, and the JVM got
   worse (+200 to +460 B per event: the read-only wrapper made per map, and walks through it). **The final form**:
   `KlangMap` gains `forEachEntry` and a `capacityHint` constructor; on JS `copy` and `setAll` from a `KlangMap` walk
   the JS `Map`'s own `forEach`, and the wire encoder walks a `KlangMap` with `forEachEntry`; on the JVM the read-only
   wrapper is made only when a view is asked for, `setAll` and `equals` take a `KlangMap`'s backing map directly, and
   `classicSlotParams` presizes its copy as HEAD did (`toKlangMap(EXPECTED_SLOTS)`; `capacityHint` is the JVM's initial
   table size, parity with HEAD's `LinkedHashMap(size + extraCapacity)`). V8, bytes per
   event, mixed profile, HEAD / tree, medians of 3 processes (pinned alike): plain 2,772 / 2,783 (no bag; time 838 /
   812 ns), three bus doors 17,128 / 12,418 (7.8 / 4.3 us), six voice doors 19,026 / 17,607 (7.6 / 6.2 us), many doors
   45,334 / 31,727 (24.5 / 12.5 us), patterned 16,254 / 13,470 (6.0 / 4.8 us), the bag alone 9,793 / 3,355 (5.9 / 1.4
   us); without the encode the same shape (three bus doors 16.1 / 11.3 KB). JVM (`ThreadMXBean`, 7 rounds, 5 HEAD and
   3 tree runs interleaved): +0 to +168 B per event depending on the shape (plain 634 / 634, three bus doors 5,402 /
   5,474, many 14,614 / 14,782; up to about 2.4 percent on small events, review round 1 read +2.0 and +2.4 percent; the
   wrapper object per map), time within HEAD's spread (six voice doors 1,686 to 1,764 / 1,634 to 1,755 ns). Without the
   presized copy the worker's probe read `p-voice6` and `p-many` 6 to 8 percent slower in 3 runs each; review round 1
   found no such effect on six shapes, a 24-slot event among them, so the hint is kept as parity with HEAD. **Bit for bit:** the probe's converted events (64 cycles, every case, the maps
   printed in their order) identical HEAD against the tree on V8 and on the JVM; the engine dumps (HEAD's `KlangMap`
   and encoder against the tree, every voice through the wire codec on V8) 18 of 18 on V8 and 18 of 18 on the JVM; the
   corpus 18 of 18. Tests: `:common` 131 (JVM) and 133 (JS), `:audio_bridge` 148 and 281, `:sprudel:jvmTest` 3,508,
   `:sprudel:jsBrowserTest` 2,997, `:audio_be:jvmTest` 2,675, `:audio_be:jsBrowserTest` 2,571, green; the alloc check
   GREEN, 36 of 36 (`C-remap` 83 B). New rows: `KlangMapSpec` (`forEachEntry`; `setAll` from a `KlangMap`; two
   `KlangMap`s compare by content), `WireCodecRoundTripSpec` (`KlangMap` slot maps encode every entry in order), and
   `ClassicSlotParamsSpec` ("the voice doors are written into a copy: the event's own bag stays as it was", a gap the
   mutation check found: `toKlangMap` handing out the bag's own storage was green on all of sprudel's 3,505 JVM
   tests). Mutants, each in one lock call: 11 of 11 RED (the JS `forEachEntry` with key and value swapped, the JS and
   JVM `setAll` fast paths doing nothing, the JVM `equals` fast path always true, the JVM `forEachEntry` doing
   nothing, the encoder writing 0.0, `ParamBag.copy` and `toMap` sharing the storage, `mergeFrom` doing nothing,
   `toKlangMap` sharing it). Review round 1 added type rows (`ParamBagSpec`: the bag's `toMap` hands a `KlangMap`;
   `ClassicSlotParamsSpec`: `toVoiceData`'s two maps are `KlangMap`s), which catch a revert to HEAD's `ParamBag`; they do not see the bag's
   storage (review round 2: the storage on `mutableMapOf` with `toMap` still handing a `KlangMap` stayed green on all
   3,508 JVM tests). **Guarded by their KDocs only:** the bag's storage, the encoder's `KlangMap` branch and the JS
   `setAll`/`copy` fast paths through the native `forEach` (each changes only bytes, and no test sees bytes). **Open, for the maintainer:** whether sprudel gets an
   allocation case of its own (the engine's harness does not run sprudel). Decided: it does, as step 4 (below). Comparing or printing a param map on JS
   walks `entries` (`==` about 1.8 times HEAD's bytes, `toString` 10 to 30 percent more); nothing does it per event.
   The engine's own copies (the sample-envelope merge, the warm-up) now take the native `forEach` copy on the
   worklet: 600 B and 161 to 164 ns for 6 entries against step 1's `entries` loop at 1,160 B and 167 to 172 ns (review
   round 2, this tree's production bundle; step 1's 473 ns for that copy did not reproduce). Report: `tmp/reviews/e2-step2-report.md`.
   **Step 5 DONE (2026-10-11): `KlangMap` is not a `Map`** (maintainer, 2026-10-11: "not a `Map`; helpers as
   extension functions when needed"). Plan: `tmp/reviews/e2-step5-plan.md`. `KlangMap<V>` has no `kotlin.collections`
   supertype; its members are what its callers use (`get`, `contains`, `size`, `isEmpty`, `set`, `setAll`, `delete`,
   `copy`, `forEachEntry`, the `capacityHint` constructor; `equals`, `hashCode` and `toString` by content, against
   another `KlangMap` only), plus `klangMapOf` and two interop extensions, `KlangMap.toMap()` and `Map.toKlangMap()`,
   for tests and tools. `VoiceData.ignitorParams` and `katalystParams` are `KlangMap<Double>?`, and so is every engine
   signature that carried them (56 lines in 10 `audio_be` files, `Voice.kt` and the Klang-Research files listed in the
   report, which only had their types changed). The dispatcher's `engines` map is a `KlangMap` (`delete` replaces
   `remove`), so a synth note no longer hashes through the stdlib. Sprudel: `toVoiceData` hands `KlangMap`s over,
   `ParamBag.toMap()` returns a plain copy. The wire codec generator lost its `Map<String, Double>` branch: a stdlib
   `Map` field is refused with an error that names the field and says to use `KlangMap<Double>` (proven on a HEAD
   copy: both `VoiceData` fields named); the `KlangMap` branch keeps the old type signature, so `WIRE_SCHEMA_HASH`
   stays 1330155241. The KSP module has no test setup, so the refusal has no test (none built, as decided). The
   harness converts its `mapOf` literals once per case (`Case.paramsMap`, `katpMap`, `katpNextMap`), so the chain's
   identity gate sees the same map every block, as before. **Bit for bit:** a new `WireCodecRoundTripSpec` row
   compares the encoded wire object with a `JSON.stringify` golden captured at `ccde9f9e` (a NaN slot and the key order
   included; RED with the encoder dropping NaN, scaling a value or reversing the order; a `toFloat()` mutant was
   GREEN because it is equivalent on Kotlin/JS); the engine dumps (HEAD against the tree, 600 blocks, every voice
   through the wire codec on V8) 19 of 19 on V8 and 19 of 19 on the JVM, every last block sounding; sprudel's
   converted events (64 cycles, 7 cases) identical on V8 and the JVM; the corpus 18 of 18. Tests, 0 failures:
   `:common` 131 (JVM) and 132 (JS), `:audio_bridge` 148 and 283, `:audio_be` 2,692 and 2,586, `:sprudel` 3,508 (486
   skipped, as before) and 2,997, the root's `:jvmTest` 60 (after review round 1). `KlangMapSpec` was rewritten for the new API (16 rows,
   among them "it is not a Map" and the interop extensions); `KlangMapJvmSpec` now pins that it is no `java.util.Map`
   and no `java.util.Collection`, and that a negative hint is coerced. The `shouldBe mapOf(...)` assertions compare
   `KlangMap` with `klangMapOf(...)`; none was weakened. Mutants, each in one lock call, unfiltered: 19 of 19 RED (six
   on the JS side of `KlangMap`, three on the JVM side, two on the common helpers, three on the encoder, three on the
   dispatcher's `delete` and `activePlaybackIds`, `toKlangMap` sharing the bag's storage, `toVoiceData` dropping
   `katalystParams`). **The allocation check:** `E-C-n1` read about 35 B less per note, all of it the dispatcher's map
   (review round 1: that map back on a `LinkedHashMap` read what HEAD reads), and was re-set from 13,100 to 13,068 by
   the file's rule: 45 healthy rounds 12,988.5 to 13,050.7 B (the worker's 6 runs and review round 1's 27 rounds,
   full-check medians 13,004 to 13,023), the line 45 B above the worst median and 17 above the worst round. The
   margins are thin, as `E-parallel`'s: the lowercasing back at every level read 13,166 to 13,191 (RED); a lowercase
   on every call, step 3's gap, is caught by its median (13,090 and 13,091 in two samples), its lowest round 5 B over
   the line; the dispatcher's map back on a `HashMap` goes red in about 1 run in 30 (medians 13,047 to 13,058), so
   the comment at that field guards it. Two other lines moved down on V8: `E-C-rel05-n16` about 50 B (its notes stop
   hashing the dispatcher's map) and `E-arith-glide` about 12 B (its `katp` maps are now `KlangMap`s, as the worklet's
   decoder delivers them, where the harness used to hand the engine stdlib maps). With step 5 the encoder has one
   branch for the slot maps, so the step 2 list of sites guarded by their KDocs only is the bag's storage and the JS
   `setAll`/`copy` fast paths. 3 runs GREEN, 58 of 58, before review round 1; on the final tree g1 went RED on
   `spluck-a`'s tail only (median 288: rounds 21.7, 324.4, 287.7), then g2 to g4 GREEN, 58 of 58; review round 2's
   own run went RED on `E-arith`'s second mode only (median 191: rounds 190.9, 195.6, 51.2). Both are known tails, not
   this change (the "Known tails" paragraph in `alloc-budgets.txt`). **Review round 1** (no CRITICAL or MAJOR): the
   generator refuses a `KlangMap` whose values are not a non-null `Double` (a `KlangMap<Double?>` or `KlangMap<*>` used
   to pass and fail in the generated code); `ParamBag.toKlangMap()` without added capacity is `KlangMap.copy` (19 to
   32 B less per event on V8 than the sized form, under HEAD's figures; time the same); new rows pin that the sample
   envelope fills a copy, never the voice's wire map (the in-place fill had passed every test), and pin
   `WIRE_SCHEMA_HASH` at 1330155241 (a changed type signature had passed every test); the reviewers' mutants all
   RED, the in-place fill and the changed signature only on the new rows, the harness's maps made per read on
   `E-arith-glide`'s JVM line (233 against 211). `E-duck` was re-set to 152 for its V8 tail (item 38). Report:
   `tmp/reviews/e2-step5-report.md`.
   **Step 4 DONE (2026-10-12): the sprudel allocation case `S-doors`** (plan `tmp/reviews/e2-step4-plan.md`). One
   step queries one cycle of `note("c")` with voice and bus doors, converts the event with `toVoiceData` and hands it
   on as the browser does (`sentToWorklet` in `engine_inbox.kt`: the wire encode on JS, the voice itself on the JVM).
   It makes no sound: a `makesSound = false` flag on the case, and the driver checks the rig's `outputs()` (an
   `OUTPUTS n` line on V8) instead of a peak. V8 healthy rounds 35,636 to 36,140 B per event (3 runs, medians 35,702
   to 36,052); the `ParamBag` storage back on a stdlib `LinkedHashMap` read 45,730 to 45,920, the JS `setAll`/`copy`
   fast path through the iterator protocol 39,159 to 39,599 (each mutant built and restored in one lock call). Lines:
   V8 36,500, JVM 15,764 (it reads 15,752; neither mutant moves the JVM). The encoder mutant of the plan no longer
   exists: since step 5 the encoder has one walk. 2 more runs GREEN, 60 of 60; the first went RED on `E-arith`'s
   second mode only (rounds 154.7, 20.7, 190.9: a known tail). The outputs guard seen red (review): with `note("~")`
   (no event per step) the check read `FAIL S-doors jvm` and `FAIL S-doors v8: it produced nothing in its window
   (outputs 0)`, 0 of 60 over budget otherwise; the line restored and checked with `cmp`.
   **A note for upstream:** the stdlib's `hashCode(dynamic)` closure makes every Kotlin/JS `HashMap`/`HashSet`
   operation allocate on V8; a statement-form `throw` there would remove it everywhere. Not filed from here.
3. **DONE (2026-10-10): no noise box reproduces; the house limiter boxed per sample.** Reported as about 2 KB per
   block for a noise voice at engine level (the V8 pass's noise-plus-saw sound). Source: as 2. No noise box
   reproduces on either bundle, at voice level or through the dispatcher (reviewer A rebuilt the development bundle:
   `white` 0.8, `noisesaw` 15.8, `E-noisesaw-sus` 41.4 B per block on the tree). The limiter boxes found on the way
   are the likely cause of the old figure, unproven: the pass's probe ran through `PlaybackEngine`, which has no
   master stage, and it is gone (an orbit compressor with a lookahead runs the same `lookaheadStep`, unmeasured then).
   **Measured on the production bundle** (`:audio_be:compileTestProductionExecutableKotlinJs`, node 22): one voice
   through `VoiceFactory` allocates nothing per sample with any noise. White, pink and brown alone, white with a
   color, noise plus saw, the `whitenoise`, `pinknoise`, `brownnoise`, `dust` and `crackle` built-ins and noise plus
   saw under `classic()` all read 0 to 90 bytes per block, the saw's band, pinned and unpinned, alone and under a
   mixed profile: V8 inlines the per-sample `rng.nextDouble()`. Through the whole engine (`PlaybackEngineDispatcher`,
   where the realtime master stage runs; offline it is `KlangAudioRenderer`) every sounding voice took about 2.1 KB per block, a saw alone too, and noise plus saw
   about 4.1 KB. **Cause** (sampling heap profiler, `--trace-turbo-inlining`): the house limiter's per-sample
   `Compressor.lookaheadStep`, a plain function of 974 bytes of bytecode, past V8's inlining limit (460), so V8
   never inlined it. Its level argument was a heap number per sample whenever anything sounded, and its gain result
   one more per sample from the first time the limiter reduced on (its release settles a few ulps below 1.0, never
   exactly 1.0, so the box stayed until `MasterStage.reset()`): noise plus saw is loud enough to engage it (-1 dB),
   a saw or a white noise alone is not. The rule in `audio/ref/performance.md` (a double handed to a function that is not
   inlined). **Fix**: `lookaheadStep` is a Kotlin `inline` function, in the same form as the class's `envelopeStep`,
   `followEnvelope` and `gainFor` (`@Suppress("NOTHING_TO_INLINE") private inline fun`); V8 still inlines the
   plain `gainReductionDb` it calls (reviewer B, `--trace-turbo-inlining`); the KDoc at the site is the guard. It reaches the authored
   `compressor` and `limiter` with a lookahead too. Bit for bit: raw doubles of the probe, HEAD against the tree, 28
   of 28 cases on V8 and on the JVM (13 voice cases, 15 engine cases through the limiter, engaged in the loud ones);
   the corpus 18 of 18 (`tmp/naming/corpus-e3.txt` against `corpus-e9.txt`). Render bytes and ns per block, medians
   of 3, HEAD / tree / HEAD again (the bytes are the difference of two processes, so a few bytes either way, even
   below zero, is the noise band):

   | case | V8 unpinned | V8 pinned (`taskset -c 11`) |
   |---|---|---|
   | voice: saw (control) | 20 / 20 / 10 B, 801 / 696 / 707 ns | 16 / 32 / 21 B, 859 / 891 / 895 ns |
   | voice: white | -5 / 5 / -5 B, 1,120 / 1,137 / 1,227 ns | 0 / 0 / 5 B, 1,450 / 1,372 / 1,302 ns |
   | voice: pink | 5 / 4 / 0 B, 1,665 / 1,687 / 1,673 ns | -5 / 5 / -10 B, 2,079 / 1,993 / 2,285 ns |
   | voice: brown | -1 / 6 / 1 B, 1,161 / 1,172 / 1,152 ns | 15 / -10 / -5 B, 1,400 / 1,435 / 1,393 ns |
   | voice: noise + saw | 20 / 21 / 21 B, 1,551 / 1,585 / 1,577 ns | 16 / 11 / 16 B, 1,843 / 2,112 / 1,847 ns |
   | voice: `saw` built-in, `classic()` (control) | 83 / 83 / 77 B, 1,384 / 1,620 / 1,724 ns | 57 / 72 / 93 B, 1,644 / 1,751 / 1,768 ns |
   | voice: `whitenoise` built-in, `classic()` | 63 / 63 / 67 B, 2,163 / 2,117 / 2,138 ns | 52 / 67 / 77 B, 2,061 / 2,014 / 2,010 ns |
   | voice: noise + saw, `classic()` | 77 / 77 / 77 B, 2,676 / 2,688 / 2,856 ns | 88 / 72 / 88 B, 2,749 / 2,705 / 2,698 ns |
   | engine: saw | 2,093 / 10 / 2,099 B, 7,160 / 6,320 / 7,381 ns | 2,072 / 21 / 2,072 B, 7,099 / 6,182 / 7,349 ns |
   | engine: noise + saw | 4,108 / 56 / 4,149 B, 10,814 / 8,783 / 10,714 ns | 4,119 / 47 / 4,108 B, 10,767 / 7,374 / 8,862 ns |
   | engine: `saw` built-in | 2,134 / 118 / 2,134 B, 8,279 / 6,880 / 8,375 ns | 2,124 / 83 / 2,160 B, 6,507 / 5,678 / 6,715 ns |
   | engine: `whitenoise` built-in | 2,150 / 99 / 2,145 B, 9,060 / 7,893 / 9,122 ns | 2,113 / 62 / 2,124 B, 7,883 / 7,230 / 8,124 ns |
   | engine: noise + saw, `classic()` | 4,176 / 78 / 4,170 B, 11,046 / 10,107 / 11,600 ns | 4,191 / 83 / 4,186 B, 13,412 / 9,871 / 13,305 ns |
   | engine: `saw` built-in, body `wood` | 4,176 / 83 / 4,175 B, 19,744 / 17,773 / 19,880 ns | 4,186 / 78 / 4,196 B, 20,543 / 18,681 / 20,283 ns |
   | engine: noise + saw, `classic()`, body `wood` | 4,170 / 119 / 4,212 B, 21,635 / 19,554 / 21,022 ns | 4,206 / 88 / 4,186 B, 23,270 / 19,778 / 23,060 ns |
   | engine: noise + saw, `classic()`, a new note every 16 blocks | 8,946 / 4,854 / 8,951 B, 18,117 / 16,985 / 18,315 ns | 8,954 / 4,850 / 8,953 B, 17,965 / 17,120 / 20,921 ns |

   One heap number per sample is 2 KB per block on node (16 bytes) and about 1.5 KB in Chrome (12 bytes, pointer
   compression); the count is the same. The engine rows run 0.81 to 0.94 of HEAD's time unpinned and 0.68 to 0.95
   pinned (the low end is the pinned noise-plus-saw row, whose HEAD-again run read 8,862, so it is noisy); the voice
   rows do not move beyond their noise. What is left
   with a new note every 16 blocks, about 4.85 KB per block, is the voice build, about 78 KB per note on V8 (item 11).
   JVM (`ThreadMXBean`, 9 rounds of 20k warm-up plus 16k blocks, medians): 0 bytes per block on both sides for every
   sustained case but the white noise with a constant color (24 B on every side, item 12's read) (the note-every-16-blocks rows 114 to 335 bytes per block on both sides alike, the voice build);
   render time unchanged within the run's drift (the tree 0.89 to 1.18 of the first HEAD, the widest spread on voice
   rows the change does not touch; HEAD again 0.73 to 0.85 across the board). Report: `tmp/reviews/e3-report.md`.
   Reviewer B's own probe (production bundle, HEAD / tree, medians of 3): the house limiter driven directly, loud
   (input 1.6, out peak 0.917), 4,093 / -6 B unpinned and 4,103 / 16 B pinned, 0.75 and 0.74 of HEAD's time; quiet
   (0.05) 2,046 / -5 B; the authored lookahead compressor, loud, 4,098 / 16 B unpinned and 4,093 / -5 B pinned;
   `processGliding` does not box (26 / 11 B loud, 26 / 25 B quiet, the trace shows `gainReductionDb` inlined). Under a
   mixed profile (quiet warm-up, then loud) HEAD 4,109 B, the tree 5 B; raw doubles 10 of 10 identical.
   **Review round 1, applied** (`tmp/reviews/e3-r1-A.md`, `e3-r1-B.md`, 0 MAJOR, 3 MINOR, 4 NIT): the attribution to
   the old figure hedged and the development bundle named (A1); the gain box ran from the first reduction on, not
   only while reducing (A2); the master stage's two hosts (A3, B2); the rule asks for a check that the helper's own
   callees stay inlined (A4); the `@Suppress` precedent as form, not reason (A5); the JVM line names `whitecolor` and
   item 12, and the section header points at item 12 (B1); B's rows added. Records and KDoc only.
4. **The superpluck allocates per block**: 450 to 700 bytes with 8 voices and drift, the pluck about 65, before and
   after the pass alike. Not located. Source: as 2. S to M. **Measured at item 7 (2026-10-11):** the superpluck with
   drift now reads 32 B per block (48 at `analogSpread` 0.5) in every process the item 7 probe measured, from 288 to
   688; two of its boxes were the drift ramp (item 7's fix) and `getUnisonDetune`'s result, one per string, which V8
   inlined in every process traced. What is left there is item 30's root frequency and one more heap number. But on
   the harness bundle some processes still read 288 to 459 B on the drift path, not located (item 7's tail: 9 of 292
   rounds at item 7, 11 of 51 at item 2, step 5; the "Known tails" paragraph in `alloc-budgets.txt`).
   The pluck was not measured. **Item 4 stays open while that tail is unlocated**; closing it needs the tail and a
   look at the pluck.
   **Found at item 5 (2026-10-11), development bundle only, not followed up:** on
   `compileTestDevelopmentExecutableKotlinJs` the superpluck with drift (`spluck-a`'s voice) reads 288 to 416 B per
   block in every mixed process measured (21 of 21, before and after item 5 alike), the size of item 7's regression;
   production reads 32. Item 7 measured production only.
5. **DONE (2026-10-11): not reproduced on production; the development-bundle box was the PWM loop's phase seed, fixed
   by the seed rule, which now covers every field seed of an oscillator loop.** Reported as one heap number per sample
   on the development bundle under a mixed profile (about 2 KB per block, `pulze` with a duty signal), blamed on the
   per-sample `setPulseShape(duty = ...)` call (`Ignitors.kt`); not seen on the production bundle in isolation.
   Source: as 2. S. **Measured** (node 22, `:audio_be:compileTestProductionExecutableKotlinJs` and
   `compileTestDevelopmentExecutableKotlinJs`, one voice through `VoiceFactory` at middle C; e7's method: every case
   built and warmed round-robin in one process, then each measured between two forced GCs, so a window reads in whole
   heap numbers). Cases: the rounded pulse with a duty of `0.5 + 0.3 * sine(0.7 Hz)`, with `analog` 0.3, with a 37 Hz
   duty, the raw pulse, with a moving phase, inside `classic()` and inside `C-full`'s chain, an engine row, beside
   the harness's cases as the other voices.
   - **Production: no per-sample box in any process.** PWM voices 16 to 32 B per block (`C-full`'s chain 64 to 80,
     the engine row 32 to 64), the constant-duty pulse 16 to 32: single-case, mixed in 7 shuffled processes, pinned
     (`taskset -c 11`, 3), `--predictable` (2). Also on the harness's own bundle (four cases added there for one
     build, then removed): 4 to 72 B per block in 5 processes per case. JVM (`ThreadMXBean`, 7 rounds): 0 B in every
     case.
   - **Development: reproduced in every mixed process (12 of 12)**, 2,080 to 2,136 B per block on every PWM voice
     but the phased one (32 B); single-case 16 B. The profiler books it in `WaveIgnitor.generate`.
   - **The cause is not the `setPulseShape` call**: V8 inlines it (152 bytes) into `generate` on both bundles
     (`--trace-turbo-inlining`); a hand-edited development bundle with the call written out in place still read
     2,096 B, as did the development bundle at ten times V8's inlining budgets, and one with the `lastDuty` field
     kept in a local. It is the PWM loop's phase seed, `var phase = voice.phase`, a field read that seeded the
     loop-carried phase without the arithmetic operation of the `* 1.0` rule (`audio/ref/performance.md`; the loop's
     drift seed had it, the phase seed did not). Why only on the development bundle: there `voice.phase` compiles to
     a getter call (`get_phase_...()`), and in one traced mixed process V8's later re-optimizations of
     `WaveIgnitor.generate` did not inline it (reviewer B, `--trace-turbo-inlining`), so the seed is a call result
     under the rule; `renderHoisted`, `renderPhased` and the stack loops inlined the same getter, and production
     loads the field directly. One trace: a strong pointer, not a full proof.
   - **Production's margin** (not this box): with V8's cumulative inlining budget cut from 920 to 600 the production
     PWM voices still read 16 to 32 B; at 500 the drifting one boxes per sample (2,080 B) with or without the `* 1.0`,
     so that box is another one, not located (it does not occur at V8's real budgets).

   **Fix** (coordinator's decision, 2026-10-11: the development bundle is what the dev server runs; the rule applied
   by its text to the oscillator loops). `* 1.0` with the rule's comment on every loop-carried double of an
   oscillator loop seeded straight from a field:
   - `audio_be/src/commonMain/kotlin/ignitor/Ignitors.kt:1003`, `WaveIgnitor.generate`'s PWM loop, the phase (the
     box; its comment names this item);
   - `Ignitors.kt:1049` `renderHoisted` and `Ignitors.kt:1102` `renderPhased`, the phase;
   - `Ignitors.kt:2040` `trapezoidLoop`, `Ignitors.kt:2097` `trapezoidLoopPhased`, `Ignitors.kt:2149` `sineLoop`,
     `Ignitors.kt:2190` `sineLoopPhased` (the unison stacks), the voice's phase;
   - `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:50`, the playhead;
   - `audio_be/src/commonMain/kotlin/ignitor/KarplusString.kt:117` to `119`, the string's lowpass and allpass state
     (`lpState`, `apPrevIn`, `apPrevOut`; the pluck and the superpluck).

   The oscillator scope is a choice, not the rule's letter (which covers every loop). Not swept, candidates with no
   box measured: the SVF coefficient ramps seeded from fields in `IgnitorFilters.kt` (`a1`, `a2`, `a3`, `k`, `g`, lines
   208, 216, 219) and `DcBlocker.process` (`filters/LowPassHighPassFilters.kt:454` and `471`, `xp` and `yc`; `C-dist`
   and `tube4x` read the same on both bundles under the mixed profile).
   Checked and left, with the reason: the sine (`SineIgnitor`) and the impulse carry their phase in a field updated per
   sample, no local seed; the noises (white, pink, brown, dust, crackle, Perlin) carry their state in fields too; the
   partial bank's three loops already seed `* 1.0` (`renderPartial`, `renderPartialPhased`, `renderPartialGained`);
   `Ignitors.kt:261` and `1431` (`var inc = phaseInc`) and `Ignitors.kt:603` (`var p = ph`) are set fresh every sample,
   not loop-carried; the partial-apply loop at `Ignitors.kt:748` runs over partials, once per block, not over samples;
   the Karplus `writePos` and `AnalogDrift`'s `rngState` are `Int`s; the accelerate ramp
   (`PitchModFactories.kt:191`, `var ratio = 2.0.pow(...)`) is a pitch modulator, outside the scope (its seed is a
   `Math.pow` in place; whether V8 treats it as a call result was not measured). Every drift seed (`m`) already had it.

   **Proof.** Raw doubles, HEAD against the tree, 600 blocks, 35 of 35 cases identical on V8 and 35 of 35 on the JVM,
   every last block sounding: the PWM cases (plain, drift, 37 Hz, raw, phased, in `classic()`, in `C-full`'s chain,
   three at 220 Hz, two engine rows), every touched loop (the saw plain, with drift and phased; the constant-duty
   pulse and square; the supersaw plain and phased, `super7a`; the supersine with drift plain and phased; the pluck
   with stiffness and with drift, `spluck-a`; a looping sample plain and with drift) and the harness's voice cases.
   The corpus 18 of 18 (`CORPUS GREEN`). `:audio_be:jvmTest` 2,662 of 2,662 and `:audio_be:jsBrowserTest` 2,558 of
   2,558. Bytes per block, mixed, 3 shuffled processes each, HEAD / tree:

   | case | development | production |
   |---|---|---|
   | `pwm` | 2,096 / 64 B, 2,452 / 2,010 ns | 32 / 32 B |
   | `pwm`, `analog` 0.3 | 2,128 / 96 B, 2,595 / 2,319 ns | 32 / 32 B |
   | `pwm`, 37 Hz duty | 2,080 / 48 B | 16 / 16 B |
   | raw pulse, PWM | 2,096 / 64 B | 32 / 32 B |
   | `pwm` in `classic()` | 2,096 / 64 B, 3,317 / 2,979 ns | 32 / 32 B |
   | `pwm` in `C-full`'s chain | 2,128 / 96 B | 64 to 80 / 80 B |
   | engine `pwm` | 2,096 to 2,134 / 64 to 102 B | 32 to 64 / 48 to 64 B |
   | phased PWM, saw, phased saw, stacks, pluck, sample | unchanged (16 to 48 B) | unchanged (16 to 32 B) |

   Time on production, mixed, 6 shuffled processes, medians, HEAD / tree / HEAD again (a byte copy): `pwm` 2,120 /
   2,042 / 2,060 ns, `pwm` with drift 2,198 / 2,205 / 2,241, the phased saw 1,653 / 1,608 / 1,609, the saw with drift
   870 / 880 / 904, `super7` 3,600 / 3,656 / 3,650, the phased supersaw 4,874 / 4,863 / 4,923, the supersine with
   drift 6,508 / 6,220 / 6,405, phased 7,890 / 7,979 / 7,701, the pluck with stiffness 2,408 / 2,361 / 2,382, with
   drift 2,147 / 2,180 / 2,111, the sample 547 / 549 / 544, with drift 626 / 633 / 619, `spluck-a` 10,446 / 10,483 /
   10,708: within HEAD against HEAD. Bytes on production unchanged in every row. **The allocation harness**, one run on
   the tree: GREEN, 30 of 30 (V8 medians `saw` 21, `super7` 11, `super7a` 15, `spluck-a` 37, `C-full` 32, n16 1,115
   B; JVM 0, n16 409); one `spluck-a` round read 304 B, item 7's recorded tail (a per-process coin on both sides:
   pooled over the production mixed processes of this item and its review, 288 B in 2 of 26 on HEAD and 3 of 20 on
   the tree). **Nothing automated guards the development-bundle box; the comment at each seed is the only guard.**
   Two reasons: the allocation check runs the production bundle, and its V8 half measures each case in its own
   process, while this box needs other voices warmed in the same process (single-case on the development bundle:
   16 B). No test either: the sound is bit-identical either way. The cheap guard, named and not built: an alloc-mode
   option that builds and warms the other cases round-robin in the same process before measuring (the probe's
   method), run on the development bundle (`:audio_benchmark:compileDevelopmentExecutableKotlinJs`) with a `pwm`
   case and a budget of about 200 B (the box is about 2 KB, about 80 times the method's 25 B noise). Item 6's PWM
   row with a moving phase (71 / 31 B) was the phased path and its `dt`: it reads 32 B. Review round 1
   (`tmp/reviews/e5-r1-A.md`, `e5-r1-B.md`, 0 MAJOR, 4 MINOR, 5 NIT) applied, records only. Report:
   `tmp/reviews/e5-report.md`.
6. **DONE (2026-10-10): the plain wave oscillators boxed `dt`, one heap number per block per oscillator.** Reported
   as about 16 bytes per block per oscillator on the production bundle, pinned: `WaveIgnitor` hands its non-integral
   `dt` to its loop methods. Source: as 2. **Measured on the production bundle**
   (`:audio_be:compileTestProductionExecutableKotlinJs`, node 22, one voice through `VoiceFactory` at 220 Hz, no
   drift unless named): one heap number per block in every oscillator of the family (saw, ramp, square, tri, `pulze`,
   and so `zawtooth`, `zamp` and `classic()`'s oscillator), about 16 B, pinned and unpinned; two on the phased PWM
   path. **Cause** (sampling heap profiler,
   `--trace-turbo-inlining`): `renderHoisted`, the loop every block-constant wave renders through, is 474 bytes of
   bytecode, just past V8's inlining limit (460: "Cannot consider renderHoisted for inlining (reason: 5)"), so `dt`,
   its one double argument, crossed a call V8 does not inline and became a heap number in the caller (the profiler put
   about 18.6 B per block in `WaveIgnitor.generate` on HEAD, none on the tree). `renderPhased` took `dt` too, and on
   the PWM path also `floor = flankSamples * dt`. The rule in `audio/ref/performance.md` (a non-integral double handed
   to a function that is not inlined). **Fix** (`audio_be/.../ignitor/Ignitors.kt`): a holder, the oscillator's own
   `WaveVoiceState.dt`, the field the unison stacks already read their increment from (the mono oscillator left it at
   0.0 until now): `generate` writes `voice.dt = dt` once per block (line 930), `renderHoisted` (line 1045) and
   `renderPhased` (line 1092) read it, and `renderPhased` computes the floor itself from it (line 1093, the same
   expression the PWM loop uses). `renderHoisted` is still not inlined (485 bytes in this form): nothing double
   crosses the call any more. The comment at the write and the KDoc of `WaveVoiceState.dt` are the guard; no test can
   pin it. (The first form was a second field, `blockDt`, beside it; review round 1 asked for the one holder.) Kotlin
   `inline` was not chosen: each loop method has two or three call sites in a `generate` that is already large, and
   item 3's caution (a method `inline` makes large can leave V8 without the budget for its small callees) applies.
   The unison stacks do not share the cause (their loops read `dt` from their voices' `WaveVoiceState`, the same
   holder: super7 0 B on both sides); the sine and the impulse render their plain loops inside `generate` and are not
   affected.
   Bit for bit: raw doubles of the probe, HEAD against the tree, 600 blocks per case, 16 of 16 cases identical on V8
   and 16 of 16 on the JVM, none silent (saw, square, tri, ramp, `zawtooth`, `pulze` at duty 0.3, the saw with drift,
   the phased saw and square, PWM plain and phased, sine, `classic()` saw, the unison stack, and two engine rows); the
   corpus 18 of 18 against `docs/benchmarks/corpus-reference.md`. Render bytes and ns per block, 20,000 warm-up and
   200,000 measured blocks, medians of 3, HEAD / tree / HEAD again (a byte copy of HEAD, the control); a few bytes
   either way, even below zero, is the noise band:

   | case | V8 unpinned | V8 pinned (`taskset -c 11`) |
   |---|---|---|
   | saw | 15.6 / 0.4 / 15.9 B, 796 / 751 / 802 ns | 36.3 / -0.1 / 10.7 B, 783 / 710 / 758 ns |
   | square | 15.1 / 0.2 / 15.2 B, 911 / 757 / 861 ns | 15.7 / 0.2 / 10.5 B, 816 / 754 / 850 ns |
   | tri | 16.1 / -0.1 / 20.2 B, 853 / 754 / 852 ns | 26.2 / 0.2 / 5.3 B, 841 / 787 / 814 ns |
   | ramp | 10.9 / -0.3 / 15.6 B, 766 / 664 / 787 ns | 15.7 / -10.5 / 25.9 B, 784 / 689 / 759 ns |
   | `pulze`, duty 0.3 | 15.5 / -0.2 / 15.6 B, 881 / 756 / 865 ns | 15.6 / 0.3 / 26.1 B, 837 / 698 / 848 ns |
   | saw, drift 0.3 | 15.7 / 0.4 / 21.3 B, 892 / 907 / 906 ns | 21.0 / -20.5 / 26.1 B, 901 / 821 / 882 ns |
   | saw, moving phase | 5.2 / 4.4 / 21.2 B, 1,871 / 1,828 / 1,856 ns | 20.7 / -5.2 / 0.0 B, 1,814 / 1,881 / 1,902 ns |
   | PWM, moving phase | 72.0 / 31.1 / 67.0 B, 3,449 / 2,947 / 3,371 ns | 67.6 / 15.5 / 62.0 B, 3,430 / 2,858 / 3,371 ns |
   | `saw` built-in, `classic()` | 15.5 / 0.2 / 15.8 B, 1,444 / 1,332 / 1,427 ns | 5.0 / 5.0 / 41.4 B, 1,441 / 1,305 / 1,413 ns |
   | unison stack, 7 voices (control) | -0.3 / 0.0 / -4.8 B, 3,435 / 3,485 / 3,498 ns | -5.1 / -10.3 / -5.6 B, 3,468 / 3,387 / 3,520 ns |
   | sine (control) | -5.3 / 0.3 / -5.4 B, 953 / 932 / 951 ns | 5.0 / -0.1 / 4.9 B, 1,024 / 936 / 920 ns |

   The table is the first form (`blockDt`). The final form (`WaveVoiceState.dt`), re-measured unpinned, HEAD / tree /
   HEAD again, medians of 3: saw 15.7 / -5.7 / 15.7 B, 828 / 695 / 799 ns; square 21.0 / -0.5 / 15.8 B, 864 / 796 /
   859 ns; tri 21.3 / -0.5 / 15.6 B, 843 / 770 / 845 ns; PWM with a moving phase 71.0 / 30.8 / 66.6 B, 3,515 / 2,896
   / 3,437 ns; `classic()` saw 15.4 / 0.0 / 19.9 B, 1,531 / 1,353 / 1,433 ns; sine (control) 5.5 / -4.8 / -0.2 B,
   951 / 961 / 906 ns. The same bytes and the same time (0.83 to 0.92 of HEAD).
   One heap number is 16 bytes on node and 12 in Chrome (pointer compression), the count the same. The phased saw
   is inside its noise (its phase LFO allocates beside it); its profile showed the box on HEAD. Time: the plain waves
   run 0.85 to 0.94 of HEAD's unpinned and 0.83 to 0.95 pinned, the phased PWM 0.86 and 0.84, the drifting and phased
   saws 0.92 to 1.01, the controls 0.96 to 1.01; HEAD again stays at HEAD in every row, so the gain is not order,
   warm-up or heat, and the inlining decision is the same on both sides (`renderHoisted` refused on size, reason 5,
   474 bytes on HEAD and 485 on the tree; `renderPhased` too), so it is not an inlining change either. The gain is
   larger than one allocation per block would explain: measured, not explained (reviewer B reproduced it, 0.83 to
   0.93). **The allocation harness** (`KLANG_BENCH_MODE=alloc`, middle C, 3 runs each,
   HEAD's and the tree's interleaved), V8 bytes per block HEAD / tree, medians: `saw` 31 / 16 (runs 31, 31, 35 against
   16, 11, 16), `sawC` 31 / 15, `C-offrel` 36 / 16, `C-env3` 84 / 63, `sgpad-vib` 67 / 31, `C-dist` 47 / 31, `tube4x`
   77 / 57, `E-sawC-sus` 83 / 62, `E-C-rel05-n16` 1,301 / 1,103 (about 12 sounding voices), `super7` 16 / 17 (the
   control); every run green; the time 0.90 of HEAD on `saw`, 0.92 on `sawC`, 0.95 on n16, 0.94 to 1.00 elsewhere.
   JVM: 0 B per block on both sides in every steady case (n16 409 / 410, the voice build, item 11), time unchanged.
   What is left on the `saw` case, about 16 B per block, is not the oscillator: the voice's own frequency handed to the
   root `generate`, one heap number per voice block at a non-integral pitch, item 30 (reviewer B located it; the
   probe at 220 Hz does not show it, the harness at middle C does).
   **The budgets moved with it** (review round 1). The fix took about 16 B off every wave voice, so item 8's regression
   (the `readParam` mutant, `Ignitors.readParam` a plain function again) read under the old budgets: reviewer B's
   mutant runs, medians of 3, `sawC` 83 and 82 against the old 80 (a margin of 2 to 3 B, inside the noise),
   `C-offrel` 66 and 62 against the old 72 (green: the guard was gone), `sgpad-vib` 114 and 113 against the old 112
   (two of six rounds already under it). The V8 budgets were re-set from this change's runs by the file's rule (about
   40 B above the median): `saw`, `sawC` and `C-offrel` 56 (tree medians 16, 15, 16 B), `sgpad-vib` 80 (31 to 36 B);
   the mutant reads red against the three item 8 lines (`sgpad-vib`'s lowest round 107.4), and `saw`, the control,
   stays green under it. `sawC` is the item 8 line on the plain voice, `C-offrel` guards the release arm (item 10b).
   Review round 2 added `C-dist` (96 to 72: healthy 47 to 31 B, the mutant's rounds 108 to 114, a margin below the
   method's noise against 96). The header of `audio_benchmark/alloc-budgets.txt` records the re-set. Healthy runs: no single round came within
   24 B of 56 (worst 25.9, 31.5, 25.9 over 16 to 18 rounds). On the final form, three alloc checks GREEN, 28 of 28
   (`saw` 15 / 21 / 16, `sawC` 19 / 15 / 15, `C-offrel` 21 / 15 / 21, `sgpad-vib` 36 / 30 / 31 B), and the mutant
   once RED, 7 of 28 over: `sawC` 93, `C-offrel` 62, `sgpad-vib` 114, `super7` 84, `super7a` 98, `C-dist` 114,
   n16 1,873 B. Reviewer B's first healthy run went red once with "no PEAK line": Kotlin/JS `println` is two writes,
   and a GC trace line that fell between the text and its newline was glued onto `PEAK`; the alloc mode now prints
   `NS` and `PEAK` in one `process.stdout.write`. Tests: `:audio_be:jvmTest` 2,661 of 2,661 and
   `:audio_be:jsBrowserTest` 2,557 of 2,557 green, on both forms. Reports: `tmp/reviews/e6-report.md`,
   `tmp/reviews/e6-r1-A.md`, `tmp/reviews/e6-r1-B.md`.
7. **DONE (2026-10-11): the drift ramp boxed twice per voice per block in the superpluck; not reproduced in the
   unison stacks.** Reported as "a drifting stack's ramp can box twice per voice per block": `DriftLanes.startOf` and
   `endOf` return a double, and where V8 does not inline `blendOf` (some mixed-profile processes, both bundles) that
   is 221 to 515 bytes per block. Source: as 2. **Measured on the production bundle**
   (`:audio_be:compileTestProductionExecutableKotlinJs`, node 22, one voice through `VoiceFactory` at middle C; the
   bytes are those allocated between two forced GCs around the measured window, `--expose-gc --trace-gc-nvp` at a
   1 MB semi-space, so a window reads in whole heap numbers): every `DriftLanes` adopter with drift (the five unison
   stacks, 7 and 16 voices, with a vibrato, a moving phase, `analogSpread` 0.5 and 0; the partial bank, plain, phased,
   with a signal gain, at spread 0.5; the superpluck with 8 strings, at spread 1 and 0.5), alone, under a mixed profile
   (every case built and warmed round-robin in one process, then each measured; 15 processes in shuffled orders) and
   with `--predictable`. **The stacks did not box the ramp in any process**: 16 B per block everywhere (item 30's root
   frequency), and `--trace-turbo-inlining` shows `blendOf` inlined twice into `trapezoidLoop` and into
   `renderPartial`. **The superpluck did**, in every process, alone or mixed: `SuperKarplusStrongIgnitor.generate`,
   past the 460-byte limit itself, inlines `startOf` and `endOf` but never `blendOf` (144 bytes), so both results
   crossed the call, 2 heap numbers per string per block (the sampling heap profiler, `--predictable`: 257 B per block
   in `blendOf`). The rule in `audio/ref/performance.md` (a double result of a call V8 does not inline).
   **Fix** (`audio_be/.../ignitor/DriftLanes.kt`): `advanceLane(lane)` steps the lane and leaves the voice's ramp in a
   holder, `ramp`, a `DoubleArray(2)`, and returns nothing; `blockStart` and `blockEnd` are `inline` properties that
   read it, so a read is an element load at the caller. `startOf`, `endOf` and `blendOf` are gone; the eight adopter
   sites in `Ignitors.kt` read `blockStart` and `blockEnd` after their `advanceLane` (the partial bank's three loops,
   the stacks' four, the superpluck's one). The KDoc of `ramp` is the guard. **The first form, two double fields,
   only moved the boxes:** in a single-case process V8 stored them as fresh heap numbers inside the superpluck's
   `generate` (the profiler, `--predictable`: 273 B per block there, about the HEAD count), and they went only under a
   mixed profile; a hand-edited copy of that bundle with the fields replaced by a `Float64Array` read 32 B in 8 of 8
   processes, so the holder is an array (measured, not explained: a field initialized at 1.5 instead of 1.0 boxed
   the same). In the final form V8 does not inline `advanceLane` (274 bytes) into the superpluck's `generate`; it
   returns nothing, and no double crosses. The stacks and the partial bank inline it (trace).
   Bit for bit: raw doubles of the probe, HEAD against the tree, 600 blocks, 30 of 30 cases identical on V8 and 30 of
   30 on the JVM, every last block sounding (every adopter above, the classic saw, `C-full`, `sgpad-vib`, three at 220
   Hz, three engine rows); the corpus 18 of 18 against `docs/benchmarks/corpus-reference.md`. Bytes per block, one
   value per process, HEAD / tree / HEAD again (a byte copy of HEAD; the single-case column HEAD / tree):

   | case | single-case, unpinned (8 processes) | mixed, unpinned (5) | mixed, pinned `taskset -c 11` (3) | `--predictable` (2) |
   |---|---|---|---|---|
   | superpluck, analog 0.3 | 416 to 544 / 32 (all) | 288 / 32 / 288 (all) | 288 to 416 / 32 / 416 | 416 / 32 / 416 |
   | the same, `analogSpread` 0.5 | 432 to 688 / 48 (all) | 304 / 48 / 304 (all) | 304 to 432 / 48 / 432 | 432 / 48 / 432 |
   | superpluck, no drift (control) | | 32 / 32 / 32 | 32 to 160 / 32 / 160 | 32 / 32 / 32 |
   | supersaw 7, analog 0.3 (`super7a`) | | 16 / 16 / 16 | 16 / 16 / 16 | 16 / 16 / 16 |
   | the 22 other cases | | unchanged within one heap number | unchanged within one heap number | |

   The superpluck's other box, `getUnisonDetune`'s double result, one per string (the 128-byte steps of HEAD's
   spread), is gone with it: V8 now inlines it into `generate` in every process traced (item 4). The e8 method
   (two processes, the GC-trace difference, 20,000 warm-up and 200,000 measured blocks, the mixed profile, medians
   of 3, HEAD / tree / HEAD again): superpluck 401 / 72 / 401 B, at spread 0.5 445 / 36 / 385, without drift 159 / 5
   / 160, the stacks, the partial bank and the controls within the method's noise. Time per block (mixed, 8
   processes, medians): superpluck 10,124 / 9,845 / 9,836 ns, `super7a` 4,893 / 4,901 / 4,900, `super16a` 9,595 /
   9,648 / 9,615, the partial bank 4,455 / 4,544 / 4,459, the classic saw (control) 1,438 / 1,522 / 1,499: within
   the noise of HEAD against HEAD (0.97 to 1.03; single processes on every bundle ran up to 2.5 times the median).
   **JVM**
   (`ThreadMXBean`, 7 rounds, every case in one process): 0 B per block on both sides in all 27 cases, time 0.96 to
   1.08 of HEAD (the rows that do not touch the drift as spread as the rows that do). **The allocation harness**
   (`KLANG_BENCH_MODE=alloc`, 3 runs each, the tree's and HEAD's interleaved): every case unchanged within noise
   (`super7a` 15 / 16, `super7` 21 / 16, n16 1,094 / 1,094 B), every run green 28 of 28, JVM 0 B on both sides (n16
   409). No existing budget moves: the fix takes nothing off a harness case, so no regression reads under a budget it
   read over before. **The harness guards it with a new case** (coordinator, 2026-10-11): `spluck-a`, the built-in
   superpluck at middle C, 8 voices, `analog` 0.3, feedback 1 (at the default 0.996 the strings are silent long before
   the window ends, which the check refuses: the last block's peak reads 0; with feedback 1 it reads 0.006). Budgets
   by the file's rule: V8 96 (healthy rounds 1 to 53 B, median about 31; HEAD's code 407 to 566 B), JVM 6 (0 B on
   both sides). With the item 7 source change reverted (HEAD's `DriftLanes.kt`, `Ignitors.kt` and the two specs, in
   one lock call, restored and checked with `cmp`), the check is RED three times out of three, 1 of 30 over:
   `spluck-a` 553, 546, 552 B (rounds 407 to 566); the tree GREEN three times, 30 of 30 (`spluck-a` 21, 40, 15). The
   item 8 mutant (`readParam` a plain function) against the full budget set: RED, 9 of 30 over, `spluck-a` 381 B.
   The case list changed, so the driver starts a new table in `docs/benchmarks/alloc-scoreboard.md` at its next run
   (it compares the last table's header with today's case list). **A rare tail, not located:** about 1 window process
   in 30 reads 300 to 460 B on `spluck-a`, the size of HEAD's regression (pooled over this change and review round 1,
   9 of 292 rounds and process pairs, 295 to 459 B; 0 of 50 with `analog` 0, so it is on the drift path); the profiler
   did not catch one in 110 processes (it perturbs the compile). The median of 3 rounds absorbs one such round; a
   false red needs two of three. The rate varies from session to session: item 2, step 5 read 11 of 51 tree rounds
   high, about 1 run in 9 red by the median (the "Known tails" paragraph in `alloc-budgets.txt`, which has the rule: a
   breach with every round over the line is a regression, a breach with a round under it is the tail; run once more
   to confirm, and a second breach of the tail's shape is still the tail).
   HEAD's code read high in every round of every run, the item 8 mutant in every round of its one run. In the first form V8 inlined `advanceLane` into the superpluck's `generate`, and the
   two fields ended as Double representation (`--trace-generalization`, reviewer B), like `AnalogDrift`'s, which do
   not box: representation is ruled out, the cause is not located. `super7a`'s guard
   text no longer names item 7: the stacks never boxed on the ramp. Tests: the specs read the holder
   (`DriftLanesSpec`, `AnalogDriftSpec`); `DriftLanesSpec`'s "a retired index is nobody's lane" row now advances a
   live lane first, so a retired index must overwrite the holder, not inherit the previous lane's ramp
   (mutation-checked: the holder left unwritten for a voice with neither walk is red on that row and was green on
   the old order; reading the holder before `advanceLane` is red on existing rows in all four adopter families,
   8, 2, 8 and 1 failures). `:audio_be:jvmTest` 2,662 of 2,662 and `:audio_be:jsBrowserTest` 2,558 of 2,558 green.
   Report: `tmp/reviews/e7-report.md`.
   The development bundle's superpluck (found at item 5): see item 4.
8. **DONE (2026-10-10, with item 12): every knob read boxed on V8, through the shared `readParam`.**
   Reported as `ConstantIgnitor.controlRateValueOrNull` boxing, about 18 bytes per block through `blockStartValue` in
   the unison stacks (development, mixed profile), blamed on the interface's shape (`Double?`). Source: as 2.
   **Cause** (measured, `tmp/reviews/e8-proposal.md`): not the type, on JS `Double?` and `Double` are the same number.
   Every knob read of the engine went through one shared one-line function, `Ignitors.readParam`, so its
   `blockStartValue` call was ONE call site for the whole engine: megamorphic, never inlined, and its double result a
   heap number per read whenever it was not a small integer. **Fix** (candidate (d) of the proposal): `readParam` is
   Kotlin `inline`, gated on `isBlockConstant` (step 1 still fell back to the member on a contract breach; step 2
   below dropped that path, so the flag decides alone), so every caller owns its own site and V8 inlines the scalar into it; the five direct `blockStartValue` callers
   (the white noise color, the brown leak, the dust tail and bipolar reads, `DetuneIgnitor`) read through it too. The
   KDoc at `readParam` is the guard; the rule is in `audio/ref/performance.md` ("a shared one-line forwarding helper
   is one megamorphic call site"). A new row in `ControlRateValueSpec` pins that `readParam` reads what
   `blockStartValue` reads (in step 1 for all four pairings of flag and scalar, the breach included; step 2
   rewrote it for the new contract, where the flag alone decides and a false flag renders, see step 2 below).
   Bit for bit: raw
   doubles of the probe, HEAD against the tree, 18 of 18 cases identical on V8, none silent; the corpus 18 of 18
   (`tmp/naming/corpus-e8.txt` against `corpus-e10.txt`). Measured on the BUILT production bundle (the proposal had
   only a hand-edited one), node 22, 20,000 warm-up and 200,000 measured blocks, bytes and ns per block, medians of
   3, HEAD / tree / HEAD again (a byte copy of HEAD, the control); `c4:` is the same case at 261.63 Hz, where a
   frequency is not a small integer:

   | case | unpinned | pinned (`taskset -c 11`) | time, tree / HEAD (unpinned, pinned) |
   |---|---|---|---|
   | `saw` (authored, the control) | 16 / 20 / 16 B | 25 / 16 / 21 B | 1.00, 1.00 |
   | `sawC` (classic, sustain) | 77 / 21 / 82 B | 93 / 21 / 83 B | 0.96, 0.99 |
   | `C-offrel` (classic, releasing) | 67 / 21 / 67 B | 77 / 16 / 57 B | 0.99, 1.00 |
   | `super7` (unison stack) | 83 / -5 / 82 B | 88 / 0 / 83 B | 0.99, 0.98 |
   | `super7a` (unison, analog) | 98 / 0 / 97 B | 94 / 0 / 98 B | 0.97, 0.98 |
   | `whitecolor` | 0 / 0 / 0 B | -5 / -26 / 0 B | 1.01, 1.01 |
   | `sgpad-vib` (vibrato voice) | 113 / 47 / 108 B | 129 / 47 / 108 B | 1.00, 0.98 |
   | `sawC-vibr` | 77 / 21 / 82 B | 77 / -5 / 77 B | 0.99, 0.98 |
   | `c4:sawC` | 93 / 31 / 97 B | 114 / 46 / 92 B | 0.97, 0.97 |
   | `c4:super7a` | 114 / 16 / 113 B | 109 / 6 / 119 B | 0.99, 0.97 |
   | `c4:sgpad-vib` | 129 / 67 / 128 B | 144 / 67 / 124 B | 0.99, 0.98 |
   | engine `sawC` sus | 134 / 26 / 134 B | 103 / 72 / 92 B | 0.98, 0.99 |
   | engine n16 (a classic note every 16 blocks) | 1,846 / 1,062 / 1,844 B | 1,800 / 1,062 / 1,794 B | 0.98, 0.99 |
   | engine `super7` sus | 129 / 47 / 129 B | 134 / 16 / 119 B | 0.99, 1.00 |
   | engine `sgpad-vib` sus | 160 / 93 / 159 B | 145 / 78 / 155 B | 0.99, 1.00 |

   So 60 to 100 B per classic or unison voice block go (4 to 6 heap numbers), about 780 B per block on the n16 row,
   pinned and unpinned, at 0.96 to 1.01 of HEAD's time; the HEAD-again control stays at HEAD. The pinned engine `sawC`
   row is the noisy one (HEAD 72 to 134 B across its three rounds, the tree 41 to 73). `--trace-turbo-inlining`,
   classic saw: on HEAD `AdsrIgnitor.generate` inlines `readParam` five times but keeps the five `blockStartValue`
   calls inside it as candidates it never inlines; on the tree it inlines `isBlockConstant` and the scalar five times
   each, and no `blockStartValue` candidate is left in the trace (the unison stack the same). The heap profile of the
   tree has no knob read left; what remains is the classes this list already names (`WaveIgnitor`'s `dt`, item 6; the
   voice's stages; the build on the n16 row, item 11), plus at 261.63 Hz about one heap number per block in
   `AdsrIgnitor.generate` that is not a knob read (likely the frequency handed on to the upstream node, the class of
   10c; not located further), and the vibrato LFO's `SineIgnitor.generate`, about 44 B per block (HEAD 48), not a knob read: `resolveFreq`,
   inlined by hand at its seven sites in a copy of the tree's bundle, moved nothing (`sgpad-vib` 46.7 / 46.6 B,
   `c4:sgpad-vib` 62.1 / 61.8, the other rows within noise, bit for bit the same), so it stays a plain function. **JVM, step 1 alone:** the probe's `ThreadMXBean` harness (9 rounds, every case in
   one process) read it unchanged (0 B per block in every steady case, the vibrato cases 0 with max 72 on both
   sides, n16 357.5 on both), **but a megamorphic read site boxed on the JVM where HEAD did not**, found by
   `FirstBlockAllocationSpec` ("sine explicit partials": 144 B per block, HEAD 0): the inline body called the
   nullable scalar at each site, and a site C2 could not reduce to an inlined fresh box (three or more node classes,
   production ones among them: `ConstantIgnitor`, `ParamIgnitor`, a `times` and a `plus` over constants, rotating
   per note in a scratch probe: 72 B per block, HEAD 0 by note 139) returned its `Double?` as a box per read.
   **Step 2 (item 12) closed it**, by the maintainer's decision of 2026-10-10: the scalar is the primitive
   `controlRateValue(freqHz): Double`, read only when `isBlockConstant`. Then:
   - The same scratch probe reads 0 B per block (one note at 5 B in 400, warm-up), `FirstBlockAllocationSpec` is
     green with every row as it was, and its explicit-partials warm-up pass is gone: it absorbed the JIT's cold
     `ConstantIgnitor` boxes, which a primitive return does not make (the row passed without it, twice, before it
     was removed).
   - The harness, every case in one process (three processes on step 2): the vibrato cases read 0 B per block with
     max 0 in every round (HEAD 0 with max 72, the proposal's second process 72 in all 9 rounds). JFR over a whole
     harness run: about 98.8 MB of `Double`s on HEAD, 7.35 MB on step 2, and none of them on the render path.
   - **Left on the JVM, cold:** the n16 row reads 377 B per block in the mixed profile (HEAD 357.5; 357.5 on step
     2 in a process running n16 alone, twice). The difference is the voice BUILD (item 11): its nullable reads,
     `buildTimeKnobValue` and the release tail in `IgnitorDslRuntime.kt` (lines 665 and 1674) and the sizing reads
     of `Ignitors.sizingValueOrNull`, now box through the `controlRateValueOrNull` extension, about 13 more `Double`s
     per note there. An `inline` extension measured the same (377). Reading the flag and the primitive scalar there
     would remove them: in `IgnitorDslRuntime.kt` a textual edit in an ADAA-owned file, not made; in
     `sizingValueOrNull` (`Ignitors.kt`, four callers) left on purpose so both build-side halves move together, after
     the ADAA session reports.

   **Step 2 on V8**, the same bundle method, HEAD / tree / HEAD again, medians of 3, unpinned: bit for bit (18 of 18
   dumps), and the bytes as step 1's (`sawC` 82 / 21 / 78, `C-offrel` 62 / 21 / 63, `super7` 82 / 0 / 82, `super7a`
   94 / 0 / 98, `sgpad-vib` 113 / 48 / 119, `sawC-vibr` 88 / 16 / 77, `c4:sawC` 92 / 30 / 98, `c4:super7a` 109 / 16
   / 113, `c4:sgpad-vib` 129 / 62 / 135, engine `sawC` sus 129 / 68 / 124, n16 1,850 / 1,045 / 1,838, engine
   `super7` sus 134 / 52 / 124, engine `sgpad-vib` sus 134 / 93 / 160, `saw` 16 / 16 / 15, `whitecolor` 0 / 0 / 11),
   time 0.97 to 1.00 of HEAD (`whitecolor` 1.05 in that run, 1.01 over 5 rounds unpinned and 0.99 pinned: noise).
   Tests: `:audio_be:jvmTest` 2,645 of 2,645 and `:audio_be:jsBrowserTest` 2,541 of 2,541 green; the corpus 18 of
   18 (`tmp/naming/corpus-e8b.txt` against `corpus-e8.txt`). Report: `tmp/reviews/e8-impl-report.md`.
9. **DONE (2026-10-10): the Shape node allocated, and so did the fused `Distort`.** Reported as about 4 KB per block
   at stage 0 in the development JS build (52 scavenges per 105,000 blocks), 835 scavenges at stage 4 (16x). Source:
   the record, "Found during tidy-up step 11" (`tmp/reviews/tidy11-r1-B.md`, "Outside this change").
   **Measured on the production bundle** (`:audio_be:compileTestProductionExecutableKotlinJs`, node 22, one voice
   through `VoiceFactory`, a saw at 220 Hz, drive 0.5): one heap number per shaped sample whose value is not a small
   integer, at every oversampling factor, in the shared `DistortionCore`, so the fused `Distort` node (`classic()`'s
   stage) paid the same. The probe's knob is the oversample FACTOR (`distort(amount, shape, factor)`, as the songs
   write it): `soft` 1.56 KB per block at factor 0 (its clamped samples, `±1`, travel as Smis), `tube` 2.06 KB;
   factor 4 (4x, stage 2) 6.2 and 8.2 KB; factor 16 (16x, stage 4, the item's "stage 4") 24.8 and 32.8 KB (reviewer B,
   `tube` at factors 0, 1, 2, 3, 4, 8, 16: 2,068 / 2,066 / 4,162 / 4,151 / 8,241 / 16,436 / 32,828 B, 128 x 16 B
   times the oversampling). **Cause** (located with the sampling heap profiler, proven on hand-edited bundles): the
   per-sample exhaustive `when` of `applyDistortionShape`; Kotlin/JS leaves its result unassigned in the `default`
   arm, so V8 carries it tagged around the loop (the rule is in `audio/ref/performance.md`). **Fix**:
   `DistortionCore` dispatches once per block to one loop per shape (`shapeRun`, `shapeRunRamped`, an inline loop
   helper per form; worked example 1's shape), the KDoc at the site the guard. The cheaper statement form (an
   initialized `var` assigned in a statement `when`) removes the boxes too, but the hoisted loops are 12 to 25 percent
   faster again (both reviewers measured it), so the shaper keeps the hoist for its speed. Bit for bit: raw doubles of
   the probe, HEAD against the tree, every shape on both nodes at factors 0, 1 and 4 (stages 0 and 2; 96 of 96 on V8,
   10 of 10 on the JVM) and at stages 1, 3 and 4 (reviewer B, 96 of 96 on V8), so every stage 0 to 4; the corpus 18
   of 18 (`tmp/naming/corpus-e9.txt` against `corpus-partials-r1.txt`). A new row in
   `OversamplerDecimatorParitySpec` pins the ramped table against the constant one (mutation-checked); the existing
   rows pin the constant table. Render bytes and ns per block, medians of 3, HEAD / tree / HEAD again:

   | case | V8 unpinned | V8 pinned (`taskset -c 11`) |
   |---|---|---|
   | saw (control) | 14 / 17 / 17 B, 837 / 829 / 789 ns | 24 / 24 / 14 B, 992 / 1,005 / 935 ns |
   | saw + `drive` (control) | 31 / 34 / 38 B, 1,012 / 982 / 1,043 ns | 37 / 31 / 31 B, 1,612 / 1,608 / 1,196 ns |
   | `distort(0.5, "soft", 0)` | 1,564 / 35 / 1,564 B, 2,386 / 1,809 / 2,522 ns | 1,550 / 31 / 1,557 B, 3,142 / 2,433 / 3,307 ns |
   | `distort(0.5, "soft", 4)`, 4x | 6,244 / 73 / 6,254 B, 7,447 / 4,971 / 8,360 ns | 6,241 / 76 / 6,254 B, 11,882 / 5,360 / 7,994 ns |
   | `distort(0.5, "tube", 0)` | 2,065 / 35 / 2,065 B, 3,031 / 1,760 / 2,796 ns | 2,055 / 41 / 2,072 B, 2,827 / 1,949 / 3,306 ns |
   | `distort(0.5, "tube", 4)`, 4x | 8,247 / 72 / 8,251 B, 7,960 / 4,607 / 7,452 ns | 8,241 / 72 / 8,251 B, 11,581 / 5,103 / 10,821 ns |
   | `distort(0.5, "asym", 4)`, 4x | 2,131 / 76 / 2,124 B, 6,694 / 4,861 / 6,387 ns | 2,142 / 65 / 2,124 B, 7,304 / 5,769 / 7,266 ns |
   | fused `Distort` soft, 0 | 1,577 / 45 / 1,580 B, 2,067 / 1,781 / 2,390 ns | 1,584 / 48 / 1,584 B, 2,253 / 1,848 / 2,253 ns |
   | fused `Distort` soft, 4x | 6,268 / 86 / 6,268 B, 7,980 / 4,695 / 8,417 ns | 6,261 / 79 / 6,258 B, 10,774 / 6,193 / 7,050 ns |
   | fused `Distort` tube, 4x | 8,261 / 86 / 8,264 B, 8,911 / 4,397 / 7,915 ns | 8,261 / 93 / 8,265 B, 7,338 / 4,740 / 9,271 ns |

   At 16x (stage 4), reviewer B, HEAD / tree, medians of 3:

   | case | V8 unpinned | V8 pinned |
   |---|---|---|
   | `distort(0.5, "tube", 16)` | 32,825 / 72 B, 19,975 / 11,545 ns | 32,830 / 93 B, 19,693 / 11,297 ns |
   | `distort(0.5, "soft", 16)` | 24,815 / 67 B, 17,490 / 11,152 ns | 24,835 / 52 B, 17,792 / 11,049 ns |
   | fused `Distort` tube, 16x | 32,840 / 94 B, 19,640 / 11,247 ns | 32,844 / 88 B, 19,485 / 11,089 ns |
   | `drive` (control) | 36 / 31 B, 881 / 877 ns | 36 / 31 B, 885 / 887 ns |

   The bytes are far outside the noise band in both conditions; a heap number is 16 bytes on node and 12 in Chrome
   (pointer compression), the count the same. The pinned times at 4x are noisy (V8's compiler shares the core), the
   unpinned ones say about 15 to 40 percent faster per node; at 16x 36 to 43 percent, pinned and unpinned agreeing.
   What is left oversampled, about 40 bytes per block, is `ScratchBuffers.oversample`'s map lookup (item 13), the same
   on HEAD. JVM (9 rounds, `ThreadMXBean`): 0 bytes per block on both sides; render time at factor 4 0.76 to 0.80 of
   HEAD (`asym` 0.64), factor 0 unchanged (the HEAD-again run was 1.44 times slower across the board, the saw
   included, so it is read relative to its saw). Report: `tmp/reviews/e9-report.md`.
   **Review round 1, applied** (`tmp/reviews/e9-r1-A.md`, `e9-r1-B.md`): the probe's "stage 4" relabelled as factor
   4 and the 16x rows added (B1); the statement form named beside the hoist in the rule, with its `/code-style` §19
   exception, and speed given as the reason to keep the hoist (A1, B2); `applyDistortionShape`'s KDoc warns against
   calling it per sample (A2); the heap number's size per engine (B3). Records and KDocs only, no code change.
   **Review round 2** (`tmp/reviews/e9-r2.md`, clean: 0 MAJOR, 1 MINOR, 2 NIT), applied by the coordinator, texts
   only: the statement form's rule says the compiler checks that every arm exists, not that it assigns (a spec that
   renders every arm is the guard); the `/code-style` §19 exception covers an inline function called per sample; the
   speed-up "about 15 to 50 percent".
10. **Kotlin's `isFinite()` is a stdlib call on Kotlin/JS**, left out of the stages' inlining budget. An inline
    compare helped pinned and hurt unpinned, so it was dropped; worth a second look only with a measurement that holds
    in both conditions. Also in the SVF clamps (`clampSvfCutoff`, `clampSvfQ`): item 29 wrote their `coerceIn` out
    as comparisons (`clampSvfRange`) and kept `isFinite`, a 23-byte stdlib call that V8 inlined in both voice shapes
    measured there, the next one to be left out if `SvfIgnitor.generate` grows. Source: as 2. S.
10a. **DONE (2026-10-10, with 10b): every per-sample envelope boxed on V8, through the curve `when`.** Reported as
    about 2.17 KB per block for a voice whose pitch envelope is in its attack or decay (one 16-byte heap number per
    sample), about 65 KB per kick sweep; a double crossing a call V8 does not inline was suspected (`adsrCurveShape`,
    `fastExp2` or `safeOut`). Source: `tmp/reviews/pp1-r1-B.md` (pitch pipeline step 1, review round 1, reviewer B,
    the NIT). **Measured on the production bundle** (`:audio_be:compileTestProductionExecutableKotlinJs`, node 22), one
    voice through `VoiceFactory` and the whole engine through `PlaybackEngineDispatcher`: not the pitch envelope alone.
    Every host of the envelope law that reads it per sample paid one heap number per sample while its envelope moved,
    about 2.1 KB per block (2,077 to 2,206 B) on every row, pinned and unpinned, alone or through the engine: the chain
    `adsr` and `classic()`'s envelope in attack, decay and release (10b), on every curve (linear, square, cube,
    scurve, invsquare, exponential), the pitch envelope (`penv`) in attack and decay, and the FM index envelope (the
    envelope part of 10d); about 6.2 KB per block with all three moving. In the sustain no curve is evaluated (the
    level is the sustain itself), and a settled or released pitch envelope writes one ratio per block, so nothing
    boxed there.
    **Cause** (sampling heap profiler, HEAD against a bundle that differs only in the helper): the exhaustive `when` of
    `adsrCurveShape` (`AdsrCurveMath.kt`), inlined three times into each per-sample loop (`AdsrIgnitor.generate`,
    `renderPitchEnvelopeRatios`, `FmModIgnitor.generate`): Kotlin/JS leaves its result unassigned in the `default`
    arm, so V8 carries it tagged around the loop, item 9's rule (`audio/ref/performance.md`). The profiler put 2,083 to
    2,090 B per block at each of the three functions on HEAD and nothing per sample on the tree. `fastExp2` and
    `safeOut` are Kotlin `inline` and box nothing; the classic envelope's de-click (`EnvelopeDeclick.next`, a plain
    method) boxes nothing either (V8 inlines it). What is left is per block: the knob reads (`blockStartValue`,
    item 8 on V8; item 12 on the JVM) and, in the fm, 10d's per-block calls. The only other `when` left in a sample loop in the bundle
    (`UnisonStackIgnitor`'s per-voice dispatch) produces no value. **Fix**: the statement form in the shared inline
    helper, one change for every host (an initialized `var y = 0.0`, a statement `when` that assigns it, `return y`);
    the KDoc at the site is the guard. Chosen over the hoist by measurement: a hand-edited copy of the tree's bundle
    with every curve switch deleted from the three loops (the exponential arm only, a perfect hoist's upper bound, valid
    for the all-exponential rows) ran 0.98 to 1.01 of the statement form's time, pinned and unpinned (medians of 5);
    with the linear arm kept (reviewer B), 0.92 to 0.96 on a bare linear `adsr` and 0.99 to 1.00 inside `classic()`.
    So a hoist buys about 2 percent on the exponential (default) rows and 4 to 8 percent on a bare linear envelope,
    not enough for a table of loops in three hosts; the envelope's loop is dominated by its arithmetic, not
    by the switch (the shaper's tighter loop gained 12 to 25 percent from its hoist, item 9). Bit for bit: raw doubles
    of the probe, HEAD / tree / HEAD again, 77 of 77 cases on V8 and 77 of 77 on the JVM (every curve through every
    stage, the chain and `classic()`, the pitch and FM envelopes, the engine rows); the corpus 18 of 18
    (`tmp/naming/corpus-e10.txt` against `corpus-e10-before.txt`). The guard row: `EnvelopeLawSpec` "each stage takes
    its own curve" renders every arm against its oracle; mutation-checked, each of the six arms with its assignment
    dropped turns it red (and "a fractional release counts floor(N) frames" with it). Render bytes and ns per block,
    medians of 3, HEAD / tree / HEAD again (a few bytes either way is the noise band; one heap number is 16 bytes on
    node and 12 in Chrome, the count the same):

    | case | V8 unpinned | V8 pinned (`taskset -c 11`) |
    |---|---|---|
    | voice: saw (control) | 10 / 21 / 21 B, 720 / 717 / 728 ns | 21 / 5 / 10 B, 752 / 728 / 726 ns |
    | voice: `saw` built-in, `classic()` (control) | 78 / 72 / 79 B, 1,344 / 1,342 / 1,371 ns | 83 / 88 / 72 B, 1,368 / 1,368 / 1,363 ns |
    | voice: chain `adsr` in sustain (control) | 78 / 82 / 77 B, 1,244 / 1,223 / 1,236 ns | 83 / 88 / 72 B, 1,163 / 1,133 / 1,142 ns |
    | voice: chain `adsr`, attack, linear | 2,098 / 62 / 2,093 B, 1,381 / 1,132 / 1,370 ns | 2,093 / 67 / 2,103 B, 1,377 / 1,163 / 1,375 ns |
    | voice: chain `adsr`, attack, exponential | 2,165 / 134 / 2,160 B, 2,232 / 1,911 / 2,247 ns | 2,129 / 140 / 2,160 B, 2,210 / 1,905 / 2,222 ns |
    | voice: chain `adsr`, decay, linear | 2,093 / 42 / 2,093 B, 1,479 / 1,257 / 1,486 ns | 2,083 / 31 / 2,103 B, 1,464 / 1,237 / 1,444 ns |
    | voice: chain `adsr`, decay, exponential | 2,094 / 52 / 2,098 B, 2,401 / 2,154 / 2,450 ns | 2,093 / 57 / 2,067 B, 2,437 / 2,157 / 2,397 ns |
    | voice: chain `adsr`, release, linear | 2,118 / 62 / 2,114 B, 1,628 / 1,375 / 1,604 ns | 2,109 / 62 / 2,129 B, 1,598 / 1,357 / 1,581 ns |
    | voice: chain `adsr`, release, exponential | 2,119 / 67 / 2,108 B, 2,671 / 2,385 / 2,743 ns | 2,135 / 47 / 2,119 B, 2,699 / 2,334 / 2,699 ns |
    | voice: `classic()` in sustain (control) | 98 / 93 / 98 B, 1,441 / 1,360 / 1,376 ns | 98 / 108 / 88 B, 1,377 / 1,371 / 1,352 ns |
    | voice: `classic()`, attack, exponential | 2,171 / 134 / 2,171 B, 2,275 / 1,992 / 2,287 ns | 2,165 / 93 / 2,145 B, 2,310 / 2,000 / 2,319 ns |
    | voice: `classic()`, decay, exponential | 2,109 / 62 / 2,109 B, 2,512 / 2,263 / 2,534 ns | 2,108 / 67 / 2,114 B, 2,508 / 2,242 / 2,536 ns |
    | voice: `penv` 24, attack | 2,181 / 156 / 2,175 B, 4,366 / 3,978 / 4,355 ns | 2,139 / 98 / 2,114 B, 4,316 / 3,925 / 4,334 ns |
    | voice: `penv` 24, decay, exponential | 2,124 / 77 / 2,134 B, 4,649 / 4,197 / 4,642 ns | 2,124 / 93 / 2,129 B, 4,569 / 4,219 / 4,568 ns |
    | voice: `penv` 24, decay, linear | 2,129 / 83 / 2,124 B, 3,377 / 3,219 / 3,334 ns | 2,124 / 88 / 2,129 B, 3,341 / 3,218 / 3,341 ns |
    | voice: `fm(300, 1.4)`, depth envelope in decay | 2,155 / 103 / 2,155 B, 4,284 / 4,111 / 4,307 ns | 2,155 / 160 / 2,139 B, 4,291 / 4,102 / 4,284 ns |
    | voice: `sgbell`, settled (control) | 134 / 119 / 134 B, 3,051 / 2,979 / 3,092 ns | 145 / 129 / 150 B, 2,993 / 2,985 / 3,001 ns |
    | voice: `sgbell` in its 0.5 s FM decay, 128 voices, per voice block | 2,621 / 136 / 2,621 B, 5,277 / 5,180 / 5,316 ns | 2,620 / 135 / 2,620 B, 5,799 / 5,714 / 5,858 ns |
    | voice: `classic()` with all three in decay | 6,242 / 93 / 6,233 B, 8,807 / 7,979 / 8,853 ns | 6,227 / 114 / 6,248 B, 8,826 / 7,961 / 8,901 ns |
    | engine: `saw` built-in (control) | 94 / 113 / 114 B, 5,835 / 5,803 / 5,821 ns | 83 / 77 / 88 B, 5,788 / 5,793 / 5,826 ns |
    | engine: `classic()` in sustain (control) | 134 / 134 / 129 B, 5,880 / 5,809 / 5,863 ns | 134 / 103 / 93 B, 5,820 / 5,796 / 5,857 ns |
    | engine: `classic()`, attack, exponential | 2,176 / 154 / 2,201 B, 6,779 / 6,647 / 6,856 ns | 2,175 / 93 / 2,139 B, 7,086 / 6,444 / 6,745 ns |
    | engine: `classic()`, decay, exponential | 2,150 / 109 / 2,150 B, 7,039 / 6,771 / 7,006 ns | 2,165 / 42 / 2,124 B, 7,034 / 6,730 / 6,974 ns |
    | engine: `penv` 24, decay | 2,165 / 114 / 2,160 B, 9,081 / 8,762 / 9,079 ns | 2,145 / 98 / 2,155 B, 9,030 / 8,693 / 9,082 ns |
    | engine: `fm`, depth envelope in decay | 2,186 / 145 / 2,202 B, 8,793 / 8,630 / 8,926 ns | 2,206 / 134 / 2,170 B, 8,762 / 8,572 / 8,693 ns |
    | engine: `classic()` with all three in decay | 6,247 / 130 / 6,263 B, 13,258 / 12,408 / 13,404 ns | 6,268 / 108 / 6,294 B, 13,340 / 12,522 / 13,322 ns |

    The square, cube, scurve and invsquare rows (chain `adsr`, every stage; `classic()`'s release, 10b) read the same:
    HEAD 2,077 to 2,155 B, the tree 26 to 108 B. Time: the voice rows run 0.79 to 0.98 of HEAD's unpinned and 0.82 to
    0.99 pinned (the plain curves about 0.82 to 0.86, the exponential ones 0.86 to 0.90, the pitch and FM rows 0.90 to
    0.96, where the oscillator dominates), the engine rows 0.88 to 0.98; HEAD again 0.95 to 1.04, the controls 0.94
    to 1.00. JVM (`ThreadMXBean`, 7 rounds of 20k warm-up plus 16k blocks, medians): 0 bytes per block on every side
    for every case but the note-every-16-blocks row (357.5 B on every side, the voice build, item 11); render time
    unchanged (a second run in the reverse order, tree / HEAD / tree, read 0.96 to 1.02 of HEAD on 13 rows; the first,
    HEAD / tree / HEAD, had the tree 1.06 to 1.14 on every row, the envelope-free saw included, so drift). Report:
    `tmp/reviews/e10-report.md`. S.
    **Review round 1, applied** (`tmp/reviews/e10-r1-A.md`, `e10-r1-B.md`, 0 MAJOR, 2 MINOR, 2 NIT): the helper's
    KDoc names the three hosts that call it per sample, not every host (A1); 10b's note-every-16-blocks residue split
    by reviewer B's profile, the voice build about 13 KB per note on V8, not 29 (B1); the hoist bound with the linear
    arm, 4 to 8 percent on a bare linear envelope, the decision unchanged (B2); the V8 knob reads are item 8 (B3).
    Records and KDoc only.
10b. **DONE (2026-10-10, with 10a): the release boxed through the curve `when`, at every release length.** Reported
    as about 2.1 KB per block for a saw with a 0.01 s gate and a 1000 s release, measured inside the release
    (`saw-offrel`), about 0.1 KB before its gate; unchecked at ordinary release lengths. Source: `tmp/reviews/pp3-r1-B.md`
    (pitch pipeline step 3, review round 1, reviewer B, NIT 3). It is 10a's cause: the release arm of `adsrCurveShape`
    in `AdsrIgnitor.generate` (`classic()`'s envelope is that node), on every curve, and the same fix. Before the
    gate the saw sat in its sustain, a constant level, so nothing boxed there. An ordinary release boxes the same: 0.5
    s, measured over its blocks 5 to 175 on 128 voices at once, with the window bracketed by forced collections (a
    difference of two processes is too noisy for a window that short). Production bundle, one voice through
    `VoiceFactory` and through the engine, medians of 3, HEAD / tree / HEAD again:

    | case | V8 unpinned | V8 pinned (`taskset -c 11`) |
    |---|---|---|
    | voice: `saw-offrel`, exponential (the default) | 2,113 / 67 / 2,108 B, 2,777 / 2,467 / 2,801 ns | 2,114 / 88 / 2,109 B, 2,751 / 2,408 / 2,754 ns |
    | voice: `saw-offrel`, linear | 2,113 / 67 / 2,109 B, 1,714 / 1,498 / 1,696 ns | 2,114 / 67 / 2,093 B, 1,757 / 1,500 / 1,695 ns |
    | voice: `saw-offrel`, scurve | 2,155 / 98 / 2,150 B, 1,798 / 1,537 / 1,836 ns | 2,155 / 88 / 2,129 B, 1,802 / 1,569 / 1,792 ns |
    | voice: a 0.5 s release, 128 voices, per voice block | 2,144 / 96 / 2,144 B, 3,314 / 3,101 / 3,375 ns | 2,150 / 102 / 2,150 B, 4,132 / 3,850 / 4,204 ns |
    | engine: `saw-offrel` | 2,150 / 98 / 2,145 B, 7,114 / 6,737 / 7,034 ns | 2,155 / 72 / 2,124 B, 7,143 / 6,717 / 7,093 ns |
    | engine: a 0.5 s release, a new note every 16 blocks (about 12 voices releasing) | 26,260 / 1,805 / 26,311 B, 40,280 / 35,251 / 40,254 ns | 26,225 / 1,801 / 26,266 B, 40,217 / 35,485 / 40,229 ns |

    What is left on the last row, about 1.8 KB per block, is not all the voice build (reviewer B, the sampling heap
    profiler over the tree's row, 1,910 B per block): voice build and scheduling 831 B (about 13 KB per note on V8,
    item 11), the knob reads (`blockStartValue` in `Voice.render`) 764 B, about 12 live voices at about 64 B (item 8,
    it grows with the sounding voices), `WaveIgnitor.generate` 195 B (item 6's `dt`), other about 120 B. The JVM's
    357.5 B per block (about 5.7 KB per note) is the build alone: its knob reads do not box. The controls, the JVM,
    bit-identity, the guard row and the corpus are 10a's. Report: `tmp/reviews/e10-report.md`. S.
    **Review round 1, applied**: see 10a (B1 corrected the residue above).
10c. **An fm whose modulator reads a freq-keyed mod allocates a heap number per block on V8** (fixed for
    freq-invariant mods, open for freq-keyed ones). The site (pitch pipeline step 3b, review round 1, reviewer B
    MINOR 1): `CarrierFreqMod.generate` hands the pinned carrier frequency, a double loaded from a field, to
    `mod.generate`, a megamorphic call V8 does not inline, so it boxes one heap number (about 16 bytes) per wrapper per
    block; the house rule in `audio/ref/performance.md`. Holding the pin in a `DoubleArray(1)` does not help (the load
    is the same). Fixed where the outer mod is freq-invariant (every sprudel door, every vibrato with constant knobs, a
    nested wrapper): the wrapper hands on the caller's tagged `freqHz` instead, the same samples (the 3b specs bit for
    bit). Measured on the production test bundle, render bytes per block, median of 3, HEAD / tree / HEAD control: the
    bell under `vib` 164.4 / 165.0 / 154.7 pinned and 161.6 / 161.9 / 165.1 unpinned, HEAD's bytes again (it was +24 to
    +28 before). Open: a wrapper whose outer mod keeps its freq key, here the inner fms of a chain (the outer fms read
    the note): a chain of three fms +7 and +37 without a door, +24 and +44 under `vib` (pinned and unpinned; HEAD's own
    spread is about 30), about one heap number per such wrapper per block. The JVM renders all of it allocation-free.
    A remedy would hand the pinned value on without a load V8 boxes. Source: `tmp/reviews/pp3b-r1-B.md`,
    `tmp/reviews/pp-step3b-report.md`. S.
10d. **Sprudel's `fm` allocates on V8 per block since it became `classic()`'s FM stage** (pitch pipeline step 4,
    review round 1, reviewer B MINOR 3). The strip's `FmRenderer` allocated nothing per block; the Ignitor `fm` node
    hands per-block doubles to calls V8 does not inline (the modulator's `generate` at `fmFreqVal * ratioVal`, the
    knob reads, `prepareModEnvelope`'s stage times), the class of `audio/ref/performance.md`. Measured on the
    production test bundle, one voice through `VoiceFactory`, render bytes per block, medians of 3, HEAD / tree / HEAD
    control, unpinned: `fm(300, 1.4)` 82 / 110 / 75, the bell envelope 117 / 162 / 110, `sgpad` 127 / 189 / 127
    (pinned: 90 / 124 / 55, 106 / 165 / 96, 131 / 196 / 127); off unchanged. The authored `fm` node paid the same
    before step 4. The JVM renders it allocation-free. Source: `tmp/reviews/pp4-r1-B.md`, the step 4 record in
    `docs/tasks/pitch-pipeline-into-the-tree.md`, `tmp/reviews/pp-step4-report.md`. S.
    **The envelope part is DONE (2026-10-10, with 10a), and it was per sample, not per block.** While the depth
    envelope moves, the fm boxed one heap number per sample (about 2.1 KB per block, `fm(300, 1.4)` with its envelope
    in decay; the bell in its 0.5 s decay 2.6 KB per voice block with the amplitude decay), through the same curve
    `when`; the figures above did not show it because the bell's envelope settles at sustain 0 after 0.5 s, a constant
    level. Fixed by 10a (the tree reads 103 and 136 B). What this item names is still open: the per-block doubles, the
    modulator's `generate` at `fmFreqVal * ratioVal` (about 50 B per block on the tree, attributed to the modulator's
    sine, `SineIgnitor.generate`, called from `FmModIgnitor.generate`) and the knob reads (`Ignitors.readParam`, about
    65 B per block, item 8 on V8; item 12 on the JVM).

29. **DONE (2026-10-11): the SVF node's per-block setup boxed on V8 in a full `classic()` voice, and so did the
    filter drift.** Reported as about 130 + 65 + 32 B per block in the cutoff chain of a full `classic()` voice
    (filter, filter envelope, tremolo, analog), the same on HEAD and after items 8 and 12, so not a knob read; the
    harness case `C-full` read about 200 to 250 B per block. Source: `tmp/reviews/e8-r1-B.md`. **Measured on the
    production bundle** (`:audio_be:compileTestProductionExecutableKotlinJs`, node 22, one voice through
    `VoiceFactory`, classic saw at middle C and at 220 Hz; narrow cases built from `C-full`'s slots): the three sites
    are the lowpass (`lpf` 1200 Hz, q 1.3, `env` 12, `analog` 0.2), about 8 heap numbers per block, the highpass
    (`hpf` 80 Hz, `analog`), about 4, and the two filters' drift lanes, one each. Every one is a double crossing a
    call V8 did not inline. **Cause** (sampling heap profiler, `--trace-turbo-inlining` on a copy of the bundle with
    every method named): `SvfIgnitor.generate` is one compiled body with one inlining budget for every SVF node, and
    in this voice its two instances take different branches (the envelope path, the static path), so each branch's
    callees run at half the frequency and are the last in line for the budget. They were left out: on the envelope
    path `prepareModEnvelope` (its stage times, 0.01 and 0.1 s, crossed as heap numbers) and the level's `coerceIn`,
    on the static path the clamps of `computeSvfCoeffs` (Kotlin's `isFinite` and `coerceIn`, stdlib calls on
    Kotlin/JS; the drifted cutoff and the q 0.707). `SvfCoeffSweep.prepare` is past the 460-byte limit (reason 5) and
    is never inlined, so both cutoffs and the q 1.3 crossed it every block. And `FilterHumanization` stepped its lane
    through `AnalogDrift.nextMultiplier()`, which V8 left out, so its result crossed. Alone, the lowpass with `env`
    and `analog` read 47 to 88 B on HEAD (the sweep's arguments, about 3) and the highpass with `analog` 5 to 46 (the
    noise band): the rest appears only in the full voice, which runs out of budget. The class is new to
    `audio/ref/performance.md` ("one node class is one compiled body with one inlining budget"). **Fix** (no ADAA
    file touched):
    - `SvfIgnitor` reads its cutoff, q and drift into fields of its own (a holder) and runs the envelope path's
      setup in `prepareEnvelopeSweep` (`IgnitorFilters.kt`), a method that takes the context only, returns nothing,
      and is too large to be inlined back; the static path's setup in `computeStaticCoeffs`, the same shape.
    - `SvfCoeffSweep.prepare(sampleRate, frames)` reads the cutoffs and q its host wrote into its fields
      (`cutoffStartHz`, `cutoffEndHz`, `q`) instead of taking them as arguments (`SvfCoeffSweepSpec` follows).
    - `clampSvfCutoff` and `clampSvfQ` (`LowPassHighPassFilters.kt`) clamp through `clampSvfRange`, `coerceIn`
      written out as two comparisons (the same value for every input with lo at most hi; the `isFinite` guard
      stays a stdlib call, item 10). Needed for the one-filter voice: there V8 inlines `computeStaticCoeffs` into
      `generate` (238 bytes in the final form; 211 in the intermediate shape with the stdlib clamps), and the stdlib
      `coerceIn` was then left out (a lowpass with `analog` alone read about 46 to 56 B against HEAD's 0 to 26 with
      the split alone).
    - The filter drift is a step that returns nothing and a field: `FilterHumanization.stepDrift(ctx)` (replaces
      `blockDriftMultiplier`) steps the lane with `beginBlock()` and leaves the multiplier in `driftMul`, which
      `SvfIgnitor.generate` reads (through an `if`, not an elvis, which would merge the double with null). And
      `AnalogDrift`'s step is a private Kotlin `inline` function that `beginBlock` runs in place, so the lane's
      step has no double-returning call either. The first form (review round 1, B1) kept a double-returning
      `blockDriftMultiplier`: in the two-filter voice V8 then left IT out of `generate`, and the box moved one level
      up instead of going (the trace showed it).
    The KDoc at each site is the guard; no test can pin a box. Two other placements were measured and dropped: the
    static path left in `generate` (the highpass's clamps boxed again in the full voice, `C-full` 77 to 87 B) and
    both paths in one method (the one-filter voice boxed, 72 to 88 B, not explained).
    Bit for bit: raw doubles of the probe, HEAD against the tree, 600 blocks, 38 of 38 cases identical on V8 and 38
    of 38 on the JVM, none silent (every narrow case, `C-full` and its sustain-0.5, moving-decay and release
    variants, each at middle C and at 220 Hz, and two engine rows); the corpus 18 of 18 against
    `docs/benchmarks/corpus-reference.md`. Render bytes and ns per block, 20,000 warm-up and 200,000 measured
    blocks, medians of 3, HEAD / tree / HEAD again (a byte copy of HEAD), unpinned; a few bytes either way, even
    below zero, is the noise band:

    | case | V8 unpinned |
    |---|---|
    | `C-full` (middle C) | 226 / 41 / 186 B, 8,361 / 8,261 / 8,382 ns |
    | `C-full` at 220 Hz | 201 / 16 / 196 B, 8,378 / 8,189 / 8,326 ns |
    | `C-full` without the distort | 212 / 72 / 196 B, 7,682 / 7,668 / 7,707 ns |
    | `C-full`, `lpf.sustain` 0.5 | 263 / 52 / 253 B, 8,429 / 8,300 / 8,417 ns |
    | `C-full`, the cutoff envelope in its decay | 237 / 47 / 223 B, 8,509 / 8,793 / 8,513 ns (5 rounds: 8,492 / 8,434 / 8,527) |
    | `C-full`, filter and amplitude in their release | 248 / 41 / 232 B, 9,555 / 9,373 / 9,739 ns |
    | `lpf` + `env` | 37 / 15 / 31 B, 2,294 / 2,240 / 2,251 ns |
    | `lpf` + `env` + `analog` | 67 / 6 / 61 B, 4,142 / 4,118 / 4,235 ns |
    | `lpf` + `env`, sustain 0.5 | 98 / 31 / 124 B, 2,239 / 2,221 / 2,304 ns |
    | `lpf` + `analog` | 40 / 21 / 26 B, 4,115 / 4,148 / 4,257 ns |
    | `hpf` + `analog` | 46 / 21 / 47 B, 4,147 / 4,112 / 4,103 ns |
    | tremolo alone (control) | 16 / 31 / 36 B, 2,178 / 2,503 / 2,242 ns (5 rounds: 2,173 / 2,221 / 2,234) |
    | `lpf` alone (control) | 16 / 16 / 21 B, 2,162 / 2,200 / 2,151 ns |
    | classic saw, `analog` (control, oscillator drift) | 10 / 20 / 16 B, 1,563 / 1,573 / 1,560 ns |
    | `C-dist` (control) | 36 / 31 / 31 B, 1,975 / 1,979 / 2,018 ns |
    | classic saw (control) | 21 / 20 / 15 B, 1,376 / 1,345 / 1,364 ns |
    | engine, `C-full` sustained | 205 / 103 / 253 B, 13,603 / 13,805 / 13,715 ns |
    | engine, classic saw (control) | 67 / 62 / 72 B, 6,330 / 6,466 / 6,153 ns |

    Pinned (`taskset -c 11`, medians of 3): `C-full` 279 / 36 / 243 B, 8,269 / 8,460 / 8,392 ns; the one-filter rows
    are noisy pinned on both sides (`hpf` + `analog` 42 / 52 B, `lpf` + `analog` 57 / -10, medians of 5, single
    rounds from -26 to 108). One heap number is 16 bytes on node and 12 in Chrome (pointer compression), the count the
    same. Time: within the noise of HEAD and HEAD again (`C-full` 0.99, the others 0.97 to 1.03; the tremolo row's
    first run had one 4.7 µs outlier round, 5 rounds read 1.02). What is left on `C-full`: item 30 (about 16 B at
    middle C, gone at 220 Hz), and the residues item 31 lists (the tremolo's depth ladder, the fused distort,
    `EnvelopeCore.prepare`'s arguments), all inside the GC trace's noise band on `C-full`. The table above is the
    first form (the drift's box moved, not removed); the final form's rows are under "Review round 1" below. **JVM**
    (`ThreadMXBean`, 7 rounds of 20,000 warm-up plus 16,000 blocks, every case in one process): 0 B per block on both
    sides in every case, time 0.97 to 1.04 of HEAD. **The allocation harness** (`KLANG_BENCH_MODE=alloc`, 3 runs
    each, HEAD's and the tree's interleaved): V8 `C-full` 186 / 42 B per block (runs 186, 190, 169 against 42, 47,
    41), every other case unchanged within noise, every run green 28 of 28; time 0.97 to 1.03 of HEAD; JVM 0 B on
    both sides (n16 409, the voice build). Tests: `:audio_be:jvmTest` 2,661 of 2,661 and `:audio_be:jsBrowserTest`
    2,557 of 2,557 green. Report: `tmp/reviews/e29-report.md`. S to M.
    **Review round 1, applied** (`tmp/reviews/e29-r1-A.md`, `e29-r1-B.md`, 2 MAJOR, 4 MINOR, 3 NIT; the code was
    correct and bit for bit):
    - B1: the filter drift's box had moved, not gone. In the two-filter voice V8 left the double-returning
      `blockDriftMultiplier` out of `generate`. It is now `stepDrift`, which returns nothing, plus a field the node
      reads (the fix bullet above). The trace of `C-full` shows `FilterHumanization.stepDrift` as a call that
      returns nothing, with `beginBlock` and its step inlined into it, so no double crosses.
    - A1: the static path's KDoc and the rule said the clamps need no inlining. `isFinite` is still a stdlib call:
      both texts now say what holds, and `computeStaticCoeffs` is 238 bytes (211 was the intermediate shape).
      Item 10 records it.
    - B2 and B3: `C-full v8` re-set from 320 to 112, with guards that do not overclaim and a re-set clause in the
      header of `audio_benchmark/alloc-budgets.txt`.
    - B4: the residues moved to a new open item, 31.
    - B5: the rule's "about 0 in either filter alone" is corrected.
    - A3 and B6: `nextMultiplier` is marked as the tests' reference.

    Bit for bit: 38 of 38 dumps on V8 and on the JVM, HEAD against the tree; the corpus 18 of 18. Render bytes and
    ns per block, unpinned, medians of 3, HEAD / first form / final form / HEAD again:

    | case | B per block | ns per block |
    |---|---|---|
    | `C-full` | 201 / 41 / 26 / 176 | 8,393 / 8,322 / 8,240 / 8,395 |
    | `C-full` at 220 Hz | 176 / 11 / 17 / 181 | |
    | `lpf` + `analog` | 47 / 16 / 15 / 11 | |
    | `hpf` + `analog` | 42 / 15 / 21 / 30 | |

    The allocation harness on the final form with budget 112, 3 runs: `C-full` 36, 52, 51 B (rounds 16 to 62),
    every run green, 28 of 28. The item 8 mutant against it: RED, 8 of 28 over; `C-full` read 175 (rounds 169,
    186, 175). Tests: `:audio_be:jvmTest` 2,661 of 2,661 and `:audio_be:jsBrowserTest` 2,557 of 2,557.
    **Review round 2** (`tmp/reviews/e29-r2.md`, clean: 0 MAJOR, 1 MINOR, 2 NIT). MINOR 1, applied: the node's use of
    the drift had no unit guard (a lost field read or a read before the step stayed green). A new row in
    `IgnitorFilterKnobsSpec`, "the drift reaches the COEFFICIENTS in the block it was stepped for", renders a
    lane-only humanization against the same lowpass whose cutoff is set per block to `800 * twin.nextMultiplier()`,
    exact bits. Mutation-checked: `blockDriftMul = 1.0` red, the read moved before `stepDrift` red, the restored
    tree green. NIT 3, applied: the clamp bullet gives 238 bytes for the final form. Tests: `:audio_be:jvmTest`
    2,662 of 2,662 and `:audio_be:jsBrowserTest` 2,558 of 2,558.
30. **WON'T IMPLEMENT (maintainer, 2026-10-11), the ready fix written down below. Every voice boxes its own
    frequency once per block on V8** (numbered after the last item, added 2026-10-10):
    `IgniteRenderer.render` (`voices/IgniteRenderer.kt`) hands the voice's `freqHz`, a double field, to the root
    Ignitor's `generate`, a call V8 never inlines, so a non-integral note frequency (almost every real note; middle C
    in the allocation harness) is one heap number per voice per block. The children receive it already tagged, so it
    is one box per voice, not per node; an integral frequency (220 Hz, a Smi) does not box. About 16 B per sounding
    voice per block, about 190 B per block on the harness's n16 row (about 12 voices): the same order as item 6's win,
    and inside the V8 noise of a single-voice budget, so no budget catches it. It is what is left on the harness's
    `saw` case after item 6. **Proof** (reviewer B, scratch copies of the tree's harness bundle, nothing in the repo
    touched): the sampled heap profile books it in `Voice.renderStages` (`IgniteRenderer.render` is inlined there);
    with the constant `261.6255653005986` handed to the root `generate` in place of the field, the GC-trace bytes
    read, medians of 3, tree against the copy, `saw` 13.6 / -3.3, `sawC` 10.8 / -0.7, `super7` 15.8 / 1.6 B per
    block (a pan `coerceIn` in `SendRenderer` replaced by `Math.min`/`max`, the other suspect, moved nothing). The
    remedy class is the holder of `audio/ref/performance.md` (for example the frequency on the `IgniteContext` the
    root already receives), but it touches the `Ignitor.generate` signature or the voice's render entry, so it is a
    design call, not a one-liner. Source: `tmp/reviews/e6-r1-B.md`, MINOR 4. S.
    **The design call (2026-10-11).** Four options were measured on scratch copies of the production bundle
    (`tmp/reviews/e30-proposal.md`, data in `tmp/reviews/e30-design/`):
    - **A, a plain holder for the root: does not work.** The frequency already sits in a plain double field
      (`IgniteRenderer.freqHz`); loading it to pass it is what boxes, so the box only moves.
    - **B, a tagged cell in `IgniteRenderer` (S, one file): the ready fix.** A field typed `Any?`, written `null`
      first and then the frequency, read with `as Double`: V8 keeps the field tagged, so the box is made once per
      voice, not per block. Hand-edited bundle: `saw` 16 to -5, `sawC` 15 to -2, `super7` 21 to -1, n16 1,096 to
      920 B per block, time unchanged, last-block peaks identical in all 14 cases; on the JVM one `Double` per voice
      build. Guard: the n16 V8 budget re-set from 1500 to about 960, mutation-checked.
    - **C, the frequency off `generate` onto `IgniteContext` (L):** 73 `generate` overrides, ADAA-owned
      `IgnitorEffects.kt`, about 277 test calls; removes the root box and `DetuneIgnitor`'s, but can add boxes where
      the frequency is passed into a call V8 does not inline (`controlRateValue` at polymorphic sites,
      `resolveFreq`), so its net effect is unknown without a prototype.
    - **D, leave it: chosen.** About 12 B per voice block in Chrome, bytes only, no measured speed behind it; B is a V8
      trick in plain sight, weighed against the stone rule "Complexity is the enemy". If the phone ever needs it, B
      is the fix to build.
    Found on the way: `DetuneIgnitor` (`freqHz * ratio`) boxes once per detune node per block (`sgpad-vib` 15.5
    against -0.2 B); pitch envelopes, vibrato, accelerate and FM's carrier work through `IgniteContext.phaseMod` and
    box nothing. `controlRateValue(freqHz)` keeps its argument: the build, the sizing reads and the Katalyst slots
    call it with no render context.

31. **What item 29 left on a full `classic()` voice on V8** (numbered after the last item, added 2026-10-11): sites
    item 29 located and did not fix, each about one heap number per block (16 B on node, 12 in Chrome), inside the
    harness's V8 noise on `C-full`, so no budget catches them. Source: item 29, `tmp/reviews/e29-report.md`,
    `tmp/reviews/e29-r1-B.md` (MINOR 4). S each.
    - **`EnvelopeCore.prepare`'s arguments.** It is past V8's 460-byte limit (reason 5), never inlined, and takes
      six doubles (the attack, decay and release frame counts, the sustain, `k`, `norm`). Every envelope host (the
      chain `adsr` and `classic()`'s envelope, the FM, pitch and filter envelopes) calls it per block, so a
      non-integral frame count or sustain is a heap number per block per host. `C-full`'s defaults travel as small
      integers (0.01 and 0.1 s at 48 kHz are 480 and 4,800 frames, sustain 1.0); `lpf.sustain` 0.5 shows one more
      (in `SvfIgnitor.prepareEnvelopeSweep`). Smallest remedy: a holder, fields on `EnvelopeCore` the host writes
      and a `prepare()` that reads them: an interface change across every envelope host.
    - **The tremolo's depth ladder.** `1 - max(depth, 0)` is block-constant; the `MinusIgnitor` scalar path calls
      `b.controlRateValue` at one shared, polymorphic site that V8 does not inline, so `MaxIgnitor`'s non-integral
      result is boxed (seen as `MaxIgnitor.controlRateValue` under the tremolo's `TimesIgnitor`). Smallest remedy:
      a build-time fold of a block-constant subtree whose leaves are voice constants into one scalar: a new
      mechanism, or a change in `IgnitorDslRuntime.kt` (ADAA-owned).
    - **The fused distort** (`FusedDistortIgnitor.generate`, `IgnitorEffects.kt`, ADAA-owned): about one heap number
      per block on `C-full` and `C-dist` (`DcBlocker` is a candidate it does not inline). Not located further.
    - **Note, the SVF's `saturate` test** (`mode == SvfMode.LOWPASS || mode == SvfMode.HIGHPASS` in
      `SvfIgnitor.generate`) is a constant per node but runs per block, and in the two-filter voice it spends about
      200 bytes of `generate`'s inlining budget on two enum `getInstance` / `initEntries` calls (reviewer B, round 1
      of item 29). A constructor `val` would free that budget; not a box today, so left for a measured change.
    The filter drift that item 29's first form only moved is fixed (item 29, review round 1).
    - **Checked at item 7 (2026-10-11): item 29's double-field holders do not box.** Item 7 found two double fields
      boxing in a single-case process where an array did not, so item 29's holders were measured in `--predictable`
      single-case processes on HEAD (`338e520d`; item 29's probe bundle, whose engine module is byte-identical):
      `C-full` 46 B per block, `lpf` + `env` + `analog` 21, `lpf` + `analog` 31, `hpf` + `analog` 46, classic saw
      with `analog` 16, classic saw 16 (e8 method, medians of 2). A hand-edited copy with the SVF node's cutoff, q and drift fields
      and `FilterHumanization.driftMul` replaced by `Float64Array`s read the same (`C-full` 46, `hpf` + `analog` 46),
      so the fields are not the box. What the one-filter voices with `analog` keep, one or two heap numbers above the
      classic saw, the profiler books in `SvfIgnitor.generate`; writing out the `coerceAtLeast` of the analog read
      there moved `hpf` + `analog` from 46 to 37 B and `lpf` + `analog` from 31 to 37 (within a heap number, not
      conclusive). Not located further; inside the harness's noise band.

32. **What item 1 left: the delay's and the reverb's per-block `configure`** (numbered after the last item, added
    2026-10-11). Source: item 1 and its review round 1 (`tmp/reviews/e1-r1-B.md`, MINOR 3). The chain's writers
    re-apply their resolved knobs on every block (`KatalystChain.applyParams`). On V8 a stage's `configure` can then be
    a cold compile in a mixed profile, where most orbits take the off arm: a trace of one such process (item 1) shows
    the delay's `configure` with nothing inlined but `isFinite`, its setters (`time`, `feedback`, `cap`), both
    `KnobGlide.retarget`s and `ensureRing` left out, so every knob crosses a call, up to 5 heap numbers per block. In
    single-case processes (the allocation check's shape) the phaser's residue was its wet/dry law instead,
    `WetDryMix.dryCoeff` and `wetCoeff` left out of the writer's budget (reviewer B's trace). **The phaser is done**
    (item 1, round 1): it skips an unchanged config, the house pattern (the EQ's `unchanged`, the resonator's, the
    compressor's by reference; the writers' comment `KatalystSlotWriters.kt`, "the effect short-circuits an unchanged
    config"), comparing the raw knobs NaN-aware and with -0.0 told apart (the cached-config rule: a NaN never defeats
    the compare), invalidated by `reset`. After it no residue was measured on the delay or the reverb either: in 6
    unpinned and 3 pinned mixed processes every stage case read the engine without a stage, but for one pinned
    process with the delay one heap number above it (item 1, round 1). So what is left is a risk, not a measured
    cost: a later compile history can bring the cold `configure` back. **Not built for the delay and the reverb,
    because a skip is not exact there without more work:** the delay's `configure` writes the feedback target into
    the line every block and `advanceGlide` overwrites it with the glide's value before the block, so between the two
    a reader (`Active.sustainsItself`) sees the target; skipping would leave the glide's
    last value there mid-glide. And a refused ring grow consults the shelf again on every `configure` (the latched
    retry), which a skip would stop. The reverb writes its size target the same way (`advanceGlide` overwrites it
    while the size glides). The remedy, when a case shows the cost again: the same skip with those two writes kept on
    the steady path (the glide targets compared, the line's knobs rewritten only when they move, the latched retry
    exempt), proven bit for bit with the owner-change dumps of item 1. Related: item 10 (`isFinite`, the call that
    stays). S.
33. **DONE (2026-10-11): `KatalystParallelEffect.process` copied the bus into each branch with `copyInto`, a
    typed-array view per call on V8.** Reported (numbered after the last item, added 2026-10-11) as about 350 B per
    block in `E-parallel`, the largest site in the sampling profiler (`subarray`); an experiment with `copyRangeInto`
    read `E-parallel` 206 to 243 B and `E-bands` 223 to 270 against about 590 and 820. Source:
    `docs/tasks-archive/2026-10/20261011-katalyst-stage-measurements.md`, "Built". **Cause:** Kotlin/JS's `copyInto` on
    a typed array runs the stdlib's `arrayCopy`, `destination.set(source.subarray(startIndex, endIndex), offset)`, and
    the view is a new object on every call: two per branch and block, four in `E-parallel`, six in `E-bands`. **Fix**
    (`audio_be/.../cylinders/katalyst/KatalystParallelEffect.kt`): the house helper `copyRangeInto`
    (`utils/buffer_copy.kt`; the rule in `audio/MEMORY.md`, "Guardrails", and `audio/ref/performance.md`), a plain
    loop, so the same samples arrive and the copy no longer allocates on V8. What `process` still allocates there is
    the distorted branch's oversampled path and its phase twins (`Pad.addPadded`), item 34's.
    `KatalystParallelEffectSpec` (20), `KatalystEqEffectSpec` (20), `KatalystChainBuilderSpec` (12) and
    `KatalystClassicPipelineOrderSpec` (7) green on the JVM and in the audio_be JS suite (2,571 of 2,571),
    `BandsCrossoverSpec` 8 of 8 on the JVM; the corpus 18 of 18 identical (its songs run no authored Katalyst chain, so
    for the stage itself the specs are the proof).
    **The sweep** (every `copyInto` in `audio_be`'s main sources):
    - `KatalystEqEffect.configure` copied the arriving scalars with `copyInto` twice, into `parkedValues` while a fade
      runs and into `current` before an install. Not per block in a steady chain (an unchanged curve returns before
      either), but on the audio thread on every block a knob moves during a fade, and the `parkedValues` KDoc says
      parking "allocates nothing". Fixed the same way in this change; no allocation case runs it (`E-eq` installs once).
      `KatalystEqEffectSpec` binds both copies: a mutant that copies nothing into `parkedValues` fails 1 test ("a curve
      change on every block"), into `current` 13 (one lock call, restored, `cmp`).
    - Left as they are, not on a per-block path: `PhasePool.drawBandedInto` (`target.copyInto(scratch)`,
      `scratch.copyInto(target)`), a note-on path that redraws one entry every N notes and allocates a new entry while
      the pool grows anyway; `SampleStore` (`msg.data.copyInto(...)`), once per sample chunk arriving, where the range
      check is wanted (a malformed chunk throws on the JVM instead of writing past the end).
    - Every other copy in `audio_be` already used `copyRangeInto` (`ChainSwap`, `Oversampler`, `KatalystFilterSwap`,
      `KatalystCompressorEffect`, `KatalystDistortEffect`, `EqCore`, `Compressor`, `MemoizingIgnitor`).
    **Measured** (the allocation check, unpinned; 4 full runs of 3 rounds on the tree, the last after the budgets were
    re-set, and 15 single-case rounds per case by the driver's method; HEAD's code from the stage measurements' 3 runs
    and the mutant below; bytes and µs per block):

    | case | V8 HEAD | V8 tree | JVM HEAD / tree | V8 µs, the mutant's run / tree |
    |---|---|---|---|---|
    | `E-parallel` | medians 571 to 619 (rounds 568 to 624) | medians 185 to 228 (rounds 176 to 253) | 0 / 0 | 19.0 / 18.0 to 18.1 |
    | `E-bands` | medians 809 to 840 (rounds 794 to 861) | medians 227 to 273 (rounds up to 279) | 0 / 0 | 32.7 / 31.1 to 31.5 |

    (The stage measurements' rows read 21 and 36 µs; the whole machine read slower then, so only the same day's
    mutant is compared.) What is left, about 150 B (`E-parallel`) and 200 B (`E-bands`) above the engine without a
    stage (`E-sawC-sus`, about 60), is most likely the distorted branch's oversampled path (item 34; `E-distort` alone
    reads about 100 above) and its phase twins, which take their work buffers the same way; not located further.
    **The guard:** `alloc-budgets.txt`, `E-parallel` v8 664 to 268 and `E-bands` v8 896 to 320, by the file's rule (40
    and 47 B above the highest healthy median; `E-parallel` set at 272 first and lowered in review round 1, where it
    sat on the single-view mutant's lowest round). Mutants (one lock call, restored, `cmp`; their scoreboard rows taken
    out with the backup): HEAD's two files, `E-parallel` 619 B and `E-bands` 809 over
    their lines (the full check RED); a single view per block put back (one channel of the first branch), single-case
    rounds `E-parallel` 272 to 319 (median 299) and `E-bands` 330 to 373 (median 331), both over.
    **Found on the way:** `E-arith` on V8 has a second mode. With the tree's bundle (one link for the first three
    runs, a fresh one for the fourth) it read medians 46, 175 (rounds 160, 175, 190: the second run RED against its 96, the row in the scoreboard), 52 and
    52, with single rounds of 139 and 160 in the green runs; the HEAD mutant read 30 (rounds 15 to 63). The chain runs
    no `parallel` and no `eq`, so not this change; not located. Like `spluck-a`'s tail (item 7), a breach that does not
    repeat on a second run is that mode. HEAD's code also read `E-arith-glide` 511 B once (its line 500); filed as item
    36. S.
34. **The bus distort's oversampled path allocates about 120 B per block on V8** (numbered after the last item, added
    2026-10-11): `E-distort` at the default 2x reads 155 to 191 B, at `oversample = 0` 46 to 73 B. The profiler names
    `DistortionCore.shapeRun`, the oversampler's `interpolate44` and `decimate44`, and a map lookup (likely
    `ScratchBuffers.oversample`'s `getOrPut` by factor); not located further. A fix re-sets the `E-distort` line.
    Source: `docs/tasks-archive/2026-10/20261011-katalyst-stage-measurements.md`, "Built". S.

## 2. Allocation on the JVM, at build and per orbit

11. **`Voice` copies its stage list into an `Array` per voice start** (`pipeline.toTypedArray()`), one small
    allocation per note, part of the voice build, kept on purpose. The bigger question behind it: the build runs in
    the render callback and allocates the whole voice. No allocation in the callback at all means building voices
    outside it, or pools per sound with a reset contract on every node. L to XL; decide with the Zig port. Source: the
    record, "Found during tidy-up steps 7 to 9".
12. **DONE (2026-10-10, with item 8): the JVM boxed a `Double` per block-constant param read**
    (`controlRateValueOrNull` returned `Double?`, read by `blockStartValue` every block): 0 to 48 bytes per block for a voice built from constants, varying per run.
    Nothing in the browser. A non-null `controlRateValue(freqHz): Double` beside `isBlockConstant` would remove it, if
    a JVM backend ever needs a render without allocation. Source: the record, "Found during tidy-up step 10". M.
    **A second site** (pitch pipeline 7b, 2026-10-10): `binaryLadder`'s constant-operand read (`TimesIgnitor`'s
    `b.controlRateValueOrNull`, here through `MaxIgnitor` and `ParamIgnitor`), measured with JFR as `Double.valueOf`
    under the composed vibrato's `x * max(semitones, 0)` (the tremolo's floor has the same shape): `sgpad` plus a
    vibrato allocated 72 to 96 bytes per block on the tree in the 9-run medians (HEAD 0 to 24), and 11.7 on average
    in one long run of 2 million blocks on the tree; V8 none. The same remedy covers both sites
    (`docs/tasks/pitch-pipeline-into-the-tree.md`, 7b record). **Possibly a third** (7c, 2026-10-10, not
    profiled): `RangeIgnitor`'s constant-bound path reads both bounds with `controlRateValueOrNull` every block; in the
    worker's run a vibrato with a constant `range(0, 1)` allocated 24 bytes per block on the JVM where the unranged one
    allocated 0 (9-run medians), but in reviewer B's run HEAD's plain vibrato allocated 72 and the ranged one 0, so the
    24 B sit inside this item's own noise band; a JFR profile would decide. The same remedy either way.
    **Measured and split in two steps (2026-10-10, `tmp/reviews/e8-proposal.md`).** JFR located the steady JVM boxes
    in the second site only: the ladder over the vibrato's `max(semitones, 0)` (`TimesIgnitor` < `MaxIgnitor` <
    `ParamIgnitor` and `ConstantIgnitor`), three `Double`s, 72 B per vibrato voice block, in one process in all 9
    rounds and in another in 1 of 9. The first site (a voice built from constants, e3's 24 B on `whitecolor`) and the
    third (the vibrato's `range`, 7c's 24 B) did NOT reproduce in either process: no white-noise or `RangeIgnitor`
    frame in the census, and the ranged vibrato read the same 72 B as the plain one.
    - **Step 1 (V8), with item 8:** `readParam` inline, gated on `isBlockConstant`. It does not reach the ladder, and
      alone it opened a JVM cost of its own at megamorphic read sites (item 8).
    - **Step 2 (JVM), maintainer's decision of 2026-10-10:** the interface member is the primitive
      `controlRateValue(freqHz): Double` (NaN by default, unspecified and unread unless `isBlockConstant`), and
      `blockStartValue`, `readParam`, the ladder, `Affine`, `Clamp`, `Lerp`, `Range`, the phase offset and the pulse
      duty gate on the flag and read the primitive. The nullable view stays as the extension
      `Ignitor.controlRateValueOrNull` (the flag, then the value, else null) for the cold readers, so
      `IgnitorDslRuntime.kt` (ADAA-owned) compiles unchanged; `KatalystSlots.kt` imports it. **The "contract breach"
      path is dropped** (a true flag with a null value can no longer be written): the degrade branches in
      `binaryLadder`, `AffineIgnitor`, `ClampIgnitor`, `LerpIgnitor`, `RangeIgnitor` and the pulse duty, the
      phase offset's per-block `perSample` unfold and the impulse's `bookkeeping` shift, and the two breach specs in
      `OscillatorPhaseSpec` (`BreachingConstant`, `SometimesBreaching`). `ControlRateScalarParitySpec` pins that a
      true flag always comes with a real value (not NaN) for every combinator in every child slot, and
      `ControlRateValueSpec` that the flag alone decides what `readParam` and `blockStartValue` read
      (mutation-checked). Result on the JVM: the ladder's 72 B per vibrato voice block and step 1's
      megamorphic-site boxes are gone (item 8); bit for bit, the corpus 18 of 18.
13. **The B4.5 orbit-side items**: `KatalystDelayEffect` makes a `DelayLine` per ring rent, and
    `ScratchBuffers.oversample` looks its sub-pool up in a map per block (`getOrPut`; the first use per factor
    allocates, once per warehouse). Per orbit or per backend, not per voice. The resonators' part closed with step 12
    (a). Source: the record, "Found during tidy-up step 10" and "Found during tidy-up step 12". S.

## 3. Behaviour and order

14. **The active list's order reaches the phase pool takes**: a unison oscillator takes its start phases from the
    orbit's pool on the voice's first rendered block (`pool.next(...)` in `Ignitors.kt`), so a change to the render
    order re-deals phases (lifecycle step 5 did, audibly in places). The fix: draw at promotion in onset order, or
    keyed by the voice. A one-time sound change, by ear. Source: the record, "Found during voice lifecycle step 5" and
    "Found during tidy-up step 10". M.
15. **`Cmd.ReplaceVoices` on a stopped engine** schedules its voices without resuming the engine
    (`PlaybackEngineDispatcher.replaceVoices` goes straight to the scheduler). Decide whether a replace means "resume"
    or is ignored after a stop. Source: the record, "Found during tidy-up steps 7 to 9". S.

## 4. Tests

16. **The weak pluck `ignitorParam` rows**: `IgnitorDefaultsTest`'s "pluck responds to ignitorParam" rows for
    `brightness`, `pickPosition` and `stiffness` render both sides from one shared, advancing `testRandom`, so they
    differ even when the slot is not read. Render each side from the same fresh seed, as the `feedback` and `leak` rows
    already do. Source: [`20261009-classic-slot-names-check.md`](../../tasks-archive/2026-10/20261009-classic-slot-names-check.md),
    step 2b. S.
17. **`DelayLineMigrationSpec`'s "the timeout is the assertion" row may not be able to fail**: a kotest `timeout`
    cannot interrupt a busy loop on the JVM, so a mutant that spins hangs the suite instead of going red. Not checked
    yet. Source: the record, "Found during the empty-variants fix". S.

## 5. Small leftovers

18. **`VoiceFactory.buildVoice` takes `cut = data.cut` beside `data`** at both call sites, a redundancy. Source:
    [`code-style-named-args-pass.md`](../code-style-named-args-pass.md), "Declarations". S.
19. **`VcaOffTeardownSpec` keeps its file name** after its constant became `TEARDOWN_FADE_SECONDS`. Source: the record,
    step 5, "Left". S.
20. **Test-only DSP entry points from the per-voice era** (audit A2.9): `Compressor.process(buffer, offset, length)`,
    `Ducking.process(input, sidechain, blockSize)`, `Phaser.process(buffer, frames)`, `Reverb.hasTail` and
    `DelayLine.hasTail`; move the specs onto the production entry points. The audit's other test-only seams (`*ForTest`,
    `currentState`, `installed*`) are the same question (the record, "Decided", D10). S.
28. **`GraphCensus` still counts the vibrato as one pitch node** (numbered after the last item, added 2026-10-10):
    its arm predates the composition (pitch pipeline 7b: the sine LFO, the floor, the multiply, the converter) and the
    7c range pass (a constant one in place, a signal one reading two bounds) and phase input (a moving phase read per
    sample), so a song benchmark's `work` column under-counts a vibrato. The tremolo's arm is the pattern (it counts
    only what runs). Source: `docs/tasks/pitch-pipeline-into-the-tree.md`, 7c review round 1, A6. S.
35. **A gliding `div` stage is slow on the JVM** (numbered after the last item, added 2026-10-11): the timing row
    "`div` gliding" read 1.8 to 3.7 µs per block on the JVM in every run against 0.39 to 0.47 on node, about eight
    cheap stages against one (the full check's compare file flags it, JVM 3.88x slower than node). Not explained; the
    moving branch is `timesLaw` over a per-sample ramp (`applyLaw`'s ramp loop), while the `times` row, a constant
    operand, runs the plain loop at about 0.3 µs, so no row times the ramp loop on its own. Source:
    `docs/tasks-archive/2026-10/20261011-katalyst-stage-measurements.md`, "Built". S.

36. **`E-arith` has a second mode on V8** (numbered after the last item, added 2026-10-11): one full run of the
    allocation check read it at a median of 175 B (rounds 160 to 190) against its line of 96, the other runs 46 to 52
    with single rounds of 139 and 160; `E-arith-glide` once read 511 against its 500. The chain runs only the
    arithmetic stages, so no item 33 site. Not located. Like `spluck-a`'s tail (item 7), a breach that does not repeat
    on a second run is that mode; if it repeats it turns the check red at random, and then either the site is found or
    the line follows item 7's rule. Item 2, step 5 read the mode in 4 of 51 tree rounds (181 to 196 B), and one run
    went red on it at a median of 191; the rule for reading such a breach is the "Known tails" paragraph in
    `alloc-budgets.txt`. Source: item 33, "Found on the way". S.
37. **The Ignitor's `perlin` and `berlin` move per SAMPLE** (numbered after the last item, added 2026-10-11): their
    `rate` is 0.003 noise units per sample (`PERLIN_STEP`), so the same number runs about 9 % faster at 48 kHz than at
    44.1 kHz (about 144 units per second against 132). Seconds are the house unit. The Katalyst's `Kat.perlin` and
    `Kat.berlin` inherit the law for parity (maintainer, 2026-10-11, Q12 of `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`),
    so both hosts change together: a rate per second, which moves the sound of every song that uses them at one of the
    two rates. S.
38. **`E-duck` has a tail on V8** (numbered after the last item, added 2026-10-11): about 1 round in 7 reads 97.6 to
    108.8 B against healthy rounds of 24.8 to 66.7 (5 of 33 tree rounds; HEAD has it too, 103.1 in 8 rounds), and one
    run held two tail rounds and read RED at 98 against the line of 96 (review round 1 of item 2, step 5). Its line was
    re-set to 152, above the tail, so the release's full check does not go red by chance (`alloc-budgets.txt`). The
    chain runs the duck with a sounding sidechain; the tail's site is not located. Locate it as `spluck-a`'s (item 7)
    and `E-arith`'s (item 36) were looked for; a fix re-sets the line. S.
39. **The Katalyst knob noises cross three doubles per noise per block on V8** (numbered after the last item, added
    2026-10-12): `KatalystKnobProgram.sample` calls `PerlinNoise.fbm(x, octaves, persistence)` and `BerlinNoise.fbm`
    once per noise per tick; V8 does not inline them there, so the clock, the persistence and the result each cross a
    call as a heap number, about 48 B per noise per block. `E-kat-perlin` (two noises) reads 113.7 B per block against
    the fader's 15.7 and `E-kat-all7` (two noises among seven knobs) 120.0 (`--predictable`, single-case, run T of
    `docs/benchmarks/2026-10-12_183400_knob-step6-v8.md`); with the allocation check's method `E-kat-perlin` reads medians of 144 to 149. The JVM reads 0. A
    remedy changes the noise classes in `common.math`, which the Ignitor's per-sample noise sources share (a holder for
    the argument and the result, or an `inline` fbm whose `noise` calls V8 then inlines, both to be measured on the
    Ignitor's own cases too), so it was not built in step 6. A fix re-sets the `E-kat-perlin` and `E-kat-all7` lines.
    Source: `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`, step 6. S.
40. **A modulated orbit compressor writes its five knobs through five setters every block** (numbered after the last
    item, added 2026-10-12): `KatalystCompressorEffect.configure` calls `Compressor.thresholdDb`, `ratio`, `kneeDb`,
    `attackSeconds` and `releaseSeconds`, each a setter call that V8 does not inline, so each knob crosses as a heap
    number, and each setter runs `updateCoefficients` (15 `timeConstantCoeff`, an `exp` each, per block where one would
    do). `E-kat-dyn` reads 98.7 B per block against the duck's floor of 46.7 (`--predictable`, single-case, run T of
    `docs/benchmarks/2026-10-12_183400_knob-step6-v8.md`): 52 B, about three heap numbers. Measured on a hand-edited copy of that bundle (run X40 of the same
    record): the five fields written in place and `updateCoefficients` (with `resizeSmoothing` under a lookahead)
    called once read `E-kat-dyn` 45.4 and `E-kat-mix` 165.4 against 196.3 (`--predictable`; 57.2 and 164.4 with the
    check's method). The remedy is a change to how `Compressor` stores its knobs (a holder or a `DoubleArray` the orbit
    stage fills and one method that applies it, the setters kept for the other callers; the class is also the house
    limiter's), so it was not built in step 6. Its time is not measured on its own. A fix re-sets the `E-kat-dyn` and
    `E-kat-mix` lines. Source: as 39. S.
41. **The residue of a moving delay time, reverb size and phaser breakpoint on V8** (numbered after the last item,
    added 2026-10-12): `E-kat-gates` reads 66.6 B per block against the delay alone's 32.3 (`--predictable`,
    single-case, run T of `docs/benchmarks/2026-10-12_183400_knob-step6-v8.md`). The sampling heap profiler (development runs, not recorded) books it in the
    reverb's `process` and `observeTail`, the delay line's crossfade loop and `observeTail`, and the delay and reverb
    writers' tick; on `E-kat-mix` also the phaser core's per-block writes of the new breakpoint. Not located further.
    Ruled out by hand-edited copies of that bundle (probes R1 to R3 of the same record, step 6's review round 2): the
    `Reverb.lowpass` setter as a direct write (66.7), the reverb configure's size path inlined too (66.9), and the
    delay configure's five `isFinite` calls inlined (66.1; `E-kat-mix` 201.8): none removes a heap number. Source: as
    39. S.

## 6. The audit's later steps and its open decisions

The tidy-up ran the audit's order (section D of
[`../audio-audit/2026-10-07-engine-tidy-audit.md`](../../audio-audit/2026-10-07-engine-tidy-audit.md)) through step 13,
and step 14 (the empty-variants bug) first. Steps 15 to 20 are shape and sound changes; each needs the maintainer.
Each was checked against the code: none is done (item 21's package dissolve aside).

21. **Step 15, planned work in its own order:** after the pitch pipeline
    ([`pitch-pipeline-into-the-tree.md`](../pitch-pipeline-into-the-tree.md)), dissolve `voices/strip/` (done
    in its step 5, 2026-10-10: the package is `voices/`) and fold the contexts (B3.1, B3.2; `SendRenderer` loses the "send" word with it, A2.5); then
    [`future/one-chain-host.md`](../future/one-chain-host.md), extended by the two tail polls of one law (A2.10) and the
    host plumbing's package move (B3.8).
22. **Step 16, the duck's "attack" renamed** to what it is, a release (A2.7; `Ducking.kt` still says "named
    externally as attack for Strudel compat"): [`future/ducking-unfinished.md`](../future/ducking-unfinished.md) item 1. A
    door rename. S to M.
23. **Step 17, one word for the vowel stage** (B3.6): `LowPassHighPassFilters.createFormant` still says formant. S.
24. **Step 18, the wire and the words:**
    - "cylinder" is the word (decision D1, maintainer, 2026-10-07): sprudel's canonical door becomes `cylinder`, with
      `orbit` and `o` as aliases (today `orbit` is canonical), and the docs, KDoc, tutorials, UI text and the Katalyst
      vocabulary follow. L; plan it as its own step. A surface change.
    - `legato` on the wire or folded into the gate time by the frontend (decision D6, open; not bit-identical).
    - `Cmd.ScheduleVoice` and `Cmd.ClearScheduled` have no sender outside the specs (B1.10). S.
25. **Step 19, sound, optional:** the per-sample `exp(-k / tau)` in `TailRelease` (B4.19), and one silence floor for
    the master's tail poll (1e-4 against 1e-5 everywhere else, decision D9). S each.
26. **Step 20, the Katalyst by-ear items**, each with a home: A1.1
    ([`future/delay-ceiling-edges.md`](../future/delay-ceiling-edges.md)), A1.2
    ([`by-ear/duck-orbit-switch-click.md`](../by-ear/duck-orbit-switch-click.md)), A1.3
    ([`by-ear/chain-swap-request-during-drain.md`](../by-ear/chain-swap-request-during-drain.md)), A1.9
    ([`future/transition-times.md`](../future/transition-times.md) §4).
27. **The audit's decisions not taken yet** (section E): D2 (`MasterStage` against `MasterBus`, two "masters"), D4
    (one block driver or two: offline renders never run the dispatcher), D6 and D9 (above), D8 (the delay and reverb
    lifecycles: one core or two readable twins), D11 (`SampleStore` grows for the life of the backend), C4.1 and C4.2
    (the orbit's activity flags, `WarmupRunner`). D7 (own the RNG) is decided: deferred to the Zig port's preparation;
    the shared drift lane's `Random(sharedSeed)` at the first block waits on it.

## 7. Questions with the maintainer

Open, in [`_maintainer-questions.md`](../_maintainer-questions.md), not repeated here: Q19 (the engine disposal order),
Q24 (the third Kotlin spelling of the Ignitor doors) and Q28 (solo protects the SOLOED voice, the refinement of Q14).

Decided 2026-10-09 and done in the batch of small items between pitch pipeline steps 3b and 4: Q14 (a soloed release
tail beside another solo stays at full level while the voice lives:
[`20261009-solo-protects-whole-voice.md`](../../tasks-archive/2026-10/20261009-solo-protects-whole-voice.md)), Q16
(the PolyBLEP texts; `CREDITS.MD` and the Credits page keep it), Q17 (the warehouse panel shows `reverbFailures` and
`reverbDropped`) and Q23 (`variants` takes plain numbers as constants). Decided and kept as they are: Q12, the solo
ramp times (1.5 s in and out, a 2 s hold), recorded in the same solo task.
