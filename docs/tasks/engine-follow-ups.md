# Engine follow-ups after the tidy-up

Status: **open, collected 2026-10-09** when the engine tidy-up was archived
([`20261009-engine-tidy-up.md`](../tasks-archive/2026-10/20261009-engine-tidy-up.md), "the record" below; v0.6.0). Each
item was checked against the later sections of the record and against the code at `662aa8db`; only what is still open
is listed. Nothing here changes a surface a tutorial teaches, except where an item says so. A behaviour-neutral item is
proven the usual way: bit-identity on the 18-song corpus and Kokon, and for an allocation item a measurement on the
production bundle, pinned AND unpinned (`audio/ref/performance.md`).

Sizes: S (an hour or two), M (a day), L (several days), as in the audit.

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
9. **The Shape node allocates**: about 4 KB per block at stage 0 in the development JS build (52 scavenges per
   105,000 blocks), 835 scavenges per 105,000 blocks at stage 4, before and after step 11 alike. It needs its own
   probe on the production bundle before anything changes. Source: the record, "Found during tidy-up step 11"
   (`tmp/reviews/tidy11-r1-B.md`, "Outside this change"). M.
10. **Kotlin's `isFinite()` is a stdlib call on Kotlin/JS**, left out of the stages' inlining budget. An inline
    compare helped pinned and hurt unpinned, so it was dropped; worth a second look only with a measurement that holds
    in both conditions. Source: as 2. S.

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

## 6. The audit's later steps and its open decisions

The tidy-up ran the audit's order (section D of
[`../audio-audit/2026-10-07-engine-tidy-audit.md`](../audio-audit/2026-10-07-engine-tidy-audit.md)) through step 13,
and step 14 (the empty-variants bug) first. Steps 15 to 20 are shape and sound changes; each needs the maintainer.
Each was checked against the code: none is done.

21. **Step 15, planned work in its own order:** after the pitch pipeline
    ([`pitch-pipeline-into-the-tree.md`](pitch-pipeline-into-the-tree.md)), dissolve `voices/strip/` and fold the
    contexts (B3.1, B3.2; `SendRenderer` loses the "send" word with it, A2.5); then
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

## 7. Questions already with the maintainer

In [`_maintainer-questions.md`](_maintainer-questions.md), not repeated here: Q16 (the oscillators are not PolyBLEP;
the texts that say so), Q17 (`WarehouseStats.reverbFailures` / `reverbDropped`), Q19 (the engine disposal order), Q23
(`variants` with plain numbers), Q24 (the third Kotlin spelling of the Ignitor doors), and from the solo fix Q12 (the
ramp times) and Q14 (a soloed release tail beside another solo).
