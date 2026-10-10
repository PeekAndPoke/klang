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

These are V8 only. The JVM allocates nothing per block in steady state except item 12's `Double` per
block-constant param read (0 to 48 bytes per block). The method and the rules learned are in
`audio/ref/performance.md`.

1. **The stages still allocate** (delay, reverb, phaser). After the V8 pass, bytes per block at engine level,
   production bundle pinned: delay 82, reverb 22, phaser 144 (192 with floor 0.5), all three 197; development pinned:
   131, 102, 96 (128), 276. The boxing sites are not located. Source: the record, "The V8 allocation pass" (the
   result table) and "Found during tidy-up step 12". M.
2. **A new param map's resolve costs about 1,130 bytes** on the classic chain after S1; the profiler attributes it to
   `KatalystKnob.resolve` on both bundles, whose code (two field stores) does not explain it. A `.katp` burst pays it
   per block. Source: the record, "Found during the V8 allocation pass". M.
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
    (`docs/tasks/pitch-pipeline-into-the-tree.md`, 7b record). **Possibly a third** (7c, 2026-10-10, not
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
    only what runs). Source: `docs/tasks/pitch-pipeline-into-the-tree.md`, 7c review round 1, A6. S.

## 6. The audit's later steps and its open decisions

The tidy-up ran the audit's order (section D of
[`../audio-audit/2026-10-07-engine-tidy-audit.md`](../audio-audit/2026-10-07-engine-tidy-audit.md)) through step 13,
and step 14 (the empty-variants bug) first. Steps 15 to 20 are shape and sound changes; each needs the maintainer.
Each was checked against the code: none is done (item 21's package dissolve aside).

21. **Step 15, planned work in its own order:** after the pitch pipeline
    ([`pitch-pipeline-into-the-tree.md`](pitch-pipeline-into-the-tree.md)), dissolve `voices/strip/` (done
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
