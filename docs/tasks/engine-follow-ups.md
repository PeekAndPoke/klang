# Engine follow-ups after the tidy-up

Status: **open, collected 2026-10-09** when the engine tidy-up was archived
([`20261009-engine-tidy-up.md`](../tasks-archive/2026-10/20261009-engine-tidy-up.md), "the record" below; v0.6.0). Each
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

The JVM allocates nothing per block in steady state; these are V8 only. The method and the rules learned are in
`audio/ref/performance.md`.

1. **The stages still allocate** (delay, reverb, phaser). After the V8 pass, bytes per block at engine level,
   production bundle pinned: delay 82, reverb 22, phaser 144 (192 with floor 0.5), all three 197; development pinned:
   131, 102, 96 (128), 276. The boxing sites are not located. Source: the record, "The V8 allocation pass" (the
   result table) and "Found during tidy-up step 12". M.
2. **A new param map's resolve costs about 1,130 bytes** on the classic chain after S1; the profiler attributes it to
   `KatalystKnob.resolve` on both bundles, whose code (two field stores) does not explain it. A `.katp` burst pays it
   per block. Source: the record, "Found during the V8 allocation pass". M.
3. **A noise voice allocates about 2 KB per block** at engine level (the probe's noise-plus-saw sound). Not located.
   Source: as 2. S to M.
4. **The superpluck allocates per block**: 450 to 700 bytes with 8 voices and drift, the pluck about 65, before and
   after the pass alike. Not located. Source: as 2. S to M.
5. **The PWM loop boxes one heap number per sample** on the development bundle under a mixed profile (about 2 KB per
   block, `pulze` with a duty signal): the per-sample `setPulseShape(duty = ...)` call (`Ignitors.kt`). Not seen on the
   production bundle in isolation. Source: as 2. S.
6. **The plain wave oscillators box `dt`**: `WaveIgnitor` hands its non-integral `dt` to its loop methods, about 16
   bytes per block per oscillator on the production bundle, pinned. The same argument class as the stages. Source: as
   2. S.
7. **A drifting stack's ramp can box twice per voice per block**: `DriftLanes.startOf` and `endOf` return a double,
   and where V8 does not inline `blendOf` (some mixed-profile processes, both bundles) that is 221 to 515 bytes per
   block. A holder for the ramp is the shape. Source: as 2. S.
8. **`ConstantIgnitor.controlRateValueOrNull` is a heap number per read** when the call is not inlined and the
   constant is not integral: about 18 bytes per block through `blockStartValue` in the unison stacks (development,
   mixed profile). It is the interface's shape (`Double?`); the same root as item 12. Source: as 2. M.
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
    in both conditions. Source: as 2. S.
10a. **The per-sample pitch envelope boxes on V8**: on the production bundle, pinned and unpinned, a voice whose pitch
    envelope is in its attack or decay allocates about 2.17 KB per block (one 16-byte heap number per sample); settled
    or gated off it allocates about 0.1 KB. HEAD and pitch pipeline step 1 alike (the strip and the `classic()` stage
    run the same `renderPitchEnvelopeRatios` loop over `core.at(...)`), so a typical kick sweep costs about 65 KB per
    note. Likely a double crossing a call V8 does not inline (`adsrCurveShape`, `fastExp2` or `safeOut`); not located.
    Source: `tmp/reviews/pp1-r1-B.md` (pitch pipeline step 1, review round 1, reviewer B, the NIT). S.
10b. **A classic voice deep in a long release boxes on V8** (a lead): a saw with a 0.01 s gate and a 1000 s release,
    measured inside the release (`saw-offrel`, no accelerate), allocates about 2.1 KB per block (2,120 to 2,137 bytes,
    roughly one heap number per sample) on the production test bundle, pinned and unpinned, on HEAD and in pitch
    pipeline step 3 alike; before its gate the same saw allocates about 0.1 KB. Unchecked at ordinary release lengths,
    where the release lasts only a few hundred blocks; likely the release path of the amplitude envelope, the class of
    10a. Locate it with the sampling heap profiler. Source: `tmp/reviews/pp3-r1-B.md` (pitch pipeline step 3, review
    round 1, reviewer B, NIT 3). S.
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
    `docs/tasks/in-progress/pitch-pipeline-into-the-tree.md`, `tmp/reviews/pp-step4-report.md`. S.

## 2. Allocation on the JVM, at build and per orbit

11. **`Voice` copies its stage list into an `Array` per voice start** (`pipeline.toTypedArray()`), one small
    allocation per note, part of the voice build, kept on purpose. The bigger question behind it: the build runs in
    the render callback and allocates the whole voice. No allocation in the callback at all means building voices
    outside it, or pools per sound with a reset contract on every node. L to XL; decide with the Zig port. Source: the
    record, "Found during tidy-up steps 7 to 9".
12. **The JVM boxes a `Double` per block-constant param read** (`controlRateValueOrNull` returns `Double?`, read by
    `blockStartValue` every block): 0 to 48 bytes per block for a voice built from constants, varying per run.
    Nothing in the browser. A non-null `controlRateValue(freqHz): Double` beside `isBlockConstant` would remove it, if
    a JVM backend ever needs a render without allocation. Source: the record, "Found during tidy-up step 10". M.
    **A second site** (pitch pipeline 7b, 2026-10-10): `binaryLadder`'s constant-operand read (`TimesIgnitor`'s
    `b.controlRateValueOrNull`, here through `MaxIgnitor` and `ParamIgnitor`), measured with JFR as `Double.valueOf`
    under the composed vibrato's `x * max(semitones, 0)` (the tremolo's floor has the same shape): `sgpad` plus a
    vibrato allocated 72 to 96 bytes per block on the tree in the 9-run medians (HEAD 0 to 24), and 11.7 on average
    in one long run of 2 million blocks on the tree; V8 none. The same remedy covers both sites
    (`docs/tasks/in-progress/pitch-pipeline-into-the-tree.md`, 7b record). **Possibly a third** (7c, 2026-10-10, not
    profiled): `RangeIgnitor`'s constant-bound path reads both bounds with `controlRateValueOrNull` every block; in the
    worker's run a vibrato with a constant `range(0, 1)` allocated 24 bytes per block on the JVM where the unranged one
    allocated 0 (9-run medians), but in reviewer B's run HEAD's plain vibrato allocated 72 and the ranged one 0, so the
    24 B sit inside this item's own noise band; a JFR profile would decide. The same remedy either way.
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
    already do. Source: [`20261009-classic-slot-names-check.md`](../tasks-archive/2026-10/20261009-classic-slot-names-check.md),
    step 2b. S.
17. **`DelayLineMigrationSpec`'s "the timeout is the assertion" row may not be able to fail**: a kotest `timeout`
    cannot interrupt a busy loop on the JVM, so a mutant that spins hangs the suite instead of going red. Not checked
    yet. Source: the record, "Found during the empty-variants fix". S.

## 5. Small leftovers

18. **`VoiceFactory.buildVoice` takes `cut = data.cut` beside `data`** at both call sites, a redundancy. Source:
    [`code-style-named-args-pass.md`](code-style-named-args-pass.md), "Declarations". S.
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
    only what runs). Source: `docs/tasks/in-progress/pitch-pipeline-into-the-tree.md`, 7c review round 1, A6. S.

## 6. The audit's later steps and its open decisions

The tidy-up ran the audit's order (section D of
[`../audio-audit/2026-10-07-engine-tidy-audit.md`](../audio-audit/2026-10-07-engine-tidy-audit.md)) through step 13,
and step 14 (the empty-variants bug) first. Steps 15 to 20 are shape and sound changes; each needs the maintainer.
Each was checked against the code: none is done (item 21's package dissolve aside).

21. **Step 15, planned work in its own order:** after the pitch pipeline
    ([`pitch-pipeline-into-the-tree.md`](in-progress/pitch-pipeline-into-the-tree.md)), dissolve `voices/strip/` (done
    in its step 5, 2026-10-10: the package is `voices/`) and fold the contexts (B3.1, B3.2; `SendRenderer` loses the "send" word with it, A2.5); then
    [`future/one-chain-host.md`](future/one-chain-host.md), extended by the two tail polls of one law (A2.10) and the
    host plumbing's package move (B3.8).
22. **Step 16, the duck's "attack" renamed** to what it is, a release (A2.7; `Ducking.kt` still says "named
    externally as attack for Strudel compat"): [`future/ducking-unfinished.md`](future/ducking-unfinished.md) item 1. A
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
    ([`future/delay-ceiling-edges.md`](future/delay-ceiling-edges.md)), A1.2
    ([`by-ear/duck-orbit-switch-click.md`](by-ear/duck-orbit-switch-click.md)), A1.3
    ([`by-ear/chain-swap-request-during-drain.md`](by-ear/chain-swap-request-during-drain.md)), A1.9
    ([`future/transition-times.md`](future/transition-times.md) §4).
27. **The audit's decisions not taken yet** (section E): D2 (`MasterStage` against `MasterBus`, two "masters"), D4
    (one block driver or two: offline renders never run the dispatcher), D6 and D9 (above), D8 (the delay and reverb
    lifecycles: one core or two readable twins), D11 (`SampleStore` grows for the life of the backend), C4.1 and C4.2
    (the orbit's activity flags, `WarmupRunner`). D7 (own the RNG) is decided: deferred to the Zig port's preparation;
    the shared drift lane's `Random(sharedSeed)` at the first block waits on it.

## 7. Questions with the maintainer

Open, in [`_maintainer-questions.md`](_maintainer-questions.md), not repeated here: Q19 (the engine disposal order),
Q24 (the third Kotlin spelling of the Ignitor doors) and Q28 (solo protects the SOLOED voice, the refinement of Q14).

Decided 2026-10-09 and done in the batch of small items between pitch pipeline steps 3b and 4: Q14 (a soloed release
tail beside another solo stays at full level while the voice lives:
[`20261009-solo-protects-whole-voice.md`](../tasks-archive/2026-10/20261009-solo-protects-whole-voice.md)), Q16
(the PolyBLEP texts; `CREDITS.MD` and the Credits page keep it), Q17 (the warehouse panel shows `reverbFailures` and
`reverbDropped`) and Q23 (`variants` takes plain numbers as constants). Decided and kept as they are: Q12, the solo
ramp times (1.5 s in and out, a 2 s hold), recorded in the same solo task.
