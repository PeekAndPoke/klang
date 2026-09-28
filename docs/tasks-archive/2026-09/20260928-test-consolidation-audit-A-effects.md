> Audit of 2026-09-27 (read-only, three parallel auditors), the evidence behind `test-consolidation.md`. Line and row numbers refer to `engine-redesign` at `520801eb`; they drift as the code moves.

# Test audit, lane A: effect DSP across core, Ignitor node and Katalyst stage

Repo `/opt/dev/peekandpoke/klang`, branch `engine-redesign` at `c12cdff5`. Read-only audit; nothing edited, no Gradle.
Counting: Kotest StringSpec rows (`"..." {`, loop-generated rows counted once per declaration), lines by `wc -l`.
Helper scripts and raw data in this folder: `count.sh`, `rowasserts.awk`, `row_asserts.txt` (direct assertion count
per row, repo-wide), `all_counts.txt`, `cuts.txt`, `cut_spans.txt`.

## 1. Headline

- Lane A today: 58 whole spec files plus the effect rows of two mixed files (`IgnitorsTest` 9 rows,
  `IgnitorCombinatorsSpec` 13 rows): **621 rows, about 19,040 lines**.
- Conservative target: **560 rows (-61, -9.8 %), about 17,665 lines (-1,375, -7.2 %), 57 files (-1)**; 55 files if
  the two small leftovers (CrushLawSpec's 2 rows, DistortionSpec's 2 DC rows) move into `StripLawCoresSpec`.
- Optional, no coverage change: `ShapingFuncsBoundsSpec` rewritten as table-driven rows (44 -> about 4 rows,
  about -120 lines). With it: -101 rows (-16 %).
- The premise "each DSP law is tested at core, node AND stage" holds only in pockets. The Katalyst specs are about
  46 % of the lane's lines (8,756 of 19,040) and are almost entirely HOST logic: on/off fades, parking, drains,
  knob glides, swaps, reset/retire. No core spec covers any of that, so it stays. Most Katalyst specs already use the
  right pattern: one "settled: the stage renders exactly what the bare core renders" bit-identity row, then
  lifecycle rows. The duplication that exists sits in (a) old qualitative rows in the Ignitor combinator/oscillator
  specs written before the law specs, (b) node rows that restate an oracle-pinned law, (c) a few Katalyst rows that
  re-prove the core's law or KnobGlide's law after the settled bit-identity row already proves the wiring, and
  (d) tests of dead or diagnostic code.

## 2. Per family

Layers: CORE = the DSP class/object on its own; NODE = Ignitor node (runtime or DSL build); KAT = Katalyst stage;
BUS = the cylinder-bus class a Katalyst stage wraps; ROOT = root `src/jvmTest` render spec.

### 2.1 Filters (LP/HP/BP/notch/SVF/onepole/resonators/body/vowel/EQ)

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE | filters/ BodyFilterSpec 4, EqCoreSpec 31, FilterNormalizationSpec 5, FormantBlendSpec 2, LowPassHighPassFiltersSpec 21, ParallelMixFilterSpec 5, PassesCascadeSpec 7, PassesLadderParitySpec 1, ResonatorBankSpec 4, SvfCoeffSweepSpec 5, WetDryMixSpec 5 | 90 | 74 |
| NODE | ignitor/ SvfNodeLawSpec 7, OnepoleParitySpec 4, FilterPassesKnobSpec 8, IgnitorFilterKnobsSpec 18, FilterEnvelopeCurvesSpec 3, IgnitorFilterEnvSemitoneSpec 5, EqIgnitorSpec 23; IgnitorCombinatorsSpec filter rows 6; IgnitorsTest bandpass/notch 2 | 76 | 68 |
| KAT | KatalystEqEffectSpec 16, KatalystBodyEffectSpec 19, KatalystFormantEffectSpec 18 | 53 | 52 |
| ROOT | KatalystBodyNonFiniteWetSpec 3 | 3 | 3 |

Note on layers: the SVF lowpass/highpass/notch kernel lives INSIDE `SvfIgnitor` (no separable core; `BaseSvf` is only
`SvfBPF`'s base), so `SvfNodeLawSpec` is the core spec for it. Same for the live one-pole (`OnePoleLowpassIgnitor`).

Same law tested more than once:
- LP/HP attenuation: `IgnitorCombinatorsSpec` "lowpass(cutoff) attenuates", "highpass(cutoff) attenuates",
  "svf(LOWPASS) attenuates" restate what `SvfNodeLawSpec` (bit-exact TPT oracle) and `PassesCascadeSpec` (absolute
  dB at fc and one octave out, LP and HP) pin with more teeth. CUT 3.
- Bandpass at centre: `IgnitorsTest` "bandpass passes center frequency" duplicates `IgnitorCombinatorsSpec`
  "svf(BANDPASS) passes centre" (keep that one; the node's bandpass is also bit-equal to EqCore BANDPASS through
  `EqCoreSpec`/`EqIgnitorSpec` "single bandpass section" and unity-peaked by `FilterNormalizationSpec`). CUT 1.
- Notch at centre: `IgnitorsTest` "notch attenuates center" is implied by `SvfNodeLawSpec` topology identity
  (notch = lp + hp) plus EqCore NOTCH parity and `FilterNormalizationSpec` "notch keeps its math". CUT 1.
- SvfBPF unity at fc: `LowPassHighPassFiltersSpec` "SvfBPF passes signal near center" (> 0.3) is dominated by
  `FilterNormalizationSpec` "SvfBPF peaks at unity at fc regardless of q" (C2 guard). CUT 1.
- Body 1/Q normalisation: `BodyFilterSpec` "db=0 mode peaks at unity regardless of Q" follows from
  `ResonatorBankSpec` (bank == bare SvfBPF times the dB factor) plus `FilterNormalizationSpec` (SvfBPF unity). CUT 1.
- Wet/dry law (C4): `ParallelMixFilterSpec` "floor=0 is an amplitude-complementary crossfade (p = 2)" and "amount
  clamps to 1" restate `WetDryMixSpec` (p = 2 invariant, domain coercion; `WetDryMix` clamps w itself). The
  "C4 law" row (0.6 / 0.5 coefficients, p = 2 specific) keeps the wiring. CUT 2.
- Passes cascade (C5): the relative rows "one octave up loses about 12 dB more", "AT fc stagger survives" and
  "HIGHPASS relative" are implied by the absolute rows. Fold the passes = 1 at-fc and the HP at-fc assertions into
  the absolute "STAGGERED q ... -3 dB AT the cutoff" row, then CUT 3. The C5 decision stays pinned absolutely.
- Filter env semitones (C3): `SvfCoeffSweepSpec` pins `filterEnvCutoff = base * 2^(depth/12 * level)`; the node
  rows +12, -12, 0, +7 restate it four times. Keep +7 (fractional, the wiring row), -12 (sign) and the clamp row;
  CUT "+12" and "depth 0" (depth 0 is also `FilterEnvelopeCurvesSpec` row 3 and `IgnitorFilterKnobsSpec`
  "env = 0 is the switch"). CUT 2.
- EQ: `EqIgnitorSpec` "guitar serial tail: chained == fused" is a strict subset of "full guitar tail (2 taps + 4
  serial)" on the same path. CUT 1. `KatalystEqEffectSpec` "a bell at 0 dB is bit-transparent through the core's
  explicit passthrough" restates `EqCoreSpec` "BELL at 0 dB is bit-transparent" (incl. -0.0/Inf/NaN); the stage's
  per-type loop already proves same core, same coefficients, bit for bit. CUT 1.

Dead-code rows: `LowPassHighPassFiltersSpec` OnePoleLPF (5 rows) and OnePoleHPF (4 rows) test classes whose KDoc says
"TEST/BENCHMARK-ONLY since 2026-08-24 ... no production path constructs this". The live one-pole LPF is guarded by
`OnepoleParitySpec` (|H| at cutoff pinned, warmth conversion pinned). CUT 9 (about 134 lines). If the maintainer
keeps the classes for `audio_benchmark/EffectBenchmark.kt`, one smoke row can stay; the honest alternative is to
retire the classes and the rows together ("scaffolding goes").

Wiring rows that stay: all of `IgnitorFilterKnobsSpec` (family loop over four kinds, ledger 2026-09-20 3a),
`FilterPassesKnobSpec`, `FilterEnvelopeCurvesSpec`, `OnepoleParitySpec`, EqIgnitorSpec adapter rows (readParam,
dynamic sections, drift, RNG order, 48 kHz, tracking HP, skip machinery, surface `.eq().tap()`, collectParams),
KatalystEq per-type bit-identity loop and one/two-voice rows.

Integration rows that stay: KatalystEq crossfade/two-bank/park/latest-wins/retire/reset rows; KatalystBody and
KatalystFormant fade/park/replace/return/reset rows (intentional un-deduped twins per `audio-constraints.md`: twin
code needs twin rows).

### 2.2 Distortion, drive and shape

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE | ShapingFuncsBoundsSpec 44, ShapeCatalogueSpec 9, DistortionSpec shaper rows 6 | 59 | 54 (optional: about 14 if table-driven) |
| NODE | DistortionSpec node rows 6, WaveshaperKnobsSpec 13, StripLawCoresSpec distort 3, ModulationClockSpec W5 1, IgnitorsTest 7, IgnitorCombinatorsSpec distort/dcBlock 3, GuitarClickHuntTest 7 | 40 | 27 |

- `GuitarClickHuntTest`: 707 lines, 7 rows, ZERO assertions (print-only click-metric harness for one song's guitar).
  DELETE the file.
- `DistortionSpec` shaper rows (fastTanh, hardClip, softClip, cubicClip, diodeClip, sineFold): bounds and symmetry
  restate `ShapingFuncsBoundsSpec`; a few landmark values are unique (hardClip identity in range, sineFold(pi/2) = 1
  and sineFold(pi) = 0). MOVE the landmark asserts into ONE new row in `ShapingFuncsBoundsSpec`; net -5.
- `DistortionSpec` node rows "soft bounded", "hard clips to [-1, 1]", "all shapes bounded (< 3.0)" are dominated by
  `StripLawCoresSpec` "the fused node renders the strip's law" (bit-exact oracle incl. DC blocker) plus
  `ShapingFuncsBoundsSpec`; "unknown shape falls back to soft" tests `parseDistortionShape`, which
  `ShapeCatalogueSpec` "an unknown distortion name is soft" pins. CUT 4. Keep the two DC-blocker rows (diode,
  rectify: the audible reason for the accepted "unconditional DC-block on distort").
- `ModulationClockSpec` "W5 superseded by D2" (DSL Distort == runtime fusedDistort, != Drive+Shape chain) is a subset
  of `WaveshaperKnobsSpec` "the Distort node reads its shape and factor ... renders the strip's law" (same two
  assertions plus slot reads and NaN). Its own KDoc points W5's hazard at StripLawCoresSpec. CUT 1.
- `IgnitorsTest` "clip fold produces non-zero output": weak (asserts only non-zero). CUT 1.
- KEEP the doors' law rows (`IgnitorsTest` drive x2, shape soft/hard direct, IgniteRenderer wrap, "distort at extreme
  drive does NOT DC-lock"; `IgnitorCombinatorsSpec` distort bounded, distort(0) bypass, dcBlock): the doors'
  Drive+Shape+DC-blocker+softCap law (`Ignitor.distort/drive/shape`, which renders three songs' guitars) has no
  oracle spec, so these loose rows are its only law coverage. See gaps.

### 2.3 Crush

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| NODE (CrushCore's sole host) | StripLawCoresSpec crush 2, CrushLawSpec 7, IgnitorCombinatorsSpec crush 1 | 10 | 4 |

`StripLawCoresSpec` pins `CrushCore` bit-exactly against a FLOOR oracle at amounts 1, 2.5, 4, 8 (with a
floor-vs-round non-vacuity check) plus NaN sample, NaN amount, < 1 bypass, Inf amount. Implied, CUT from
`CrushLawSpec`: "amount=0 passes unchanged" (also IgnitorGateSpec), "quantizes to discrete levels", "zero in, zero
out", "asymmetric DC bias (crunch)" (the D1 floor decision, already the oracle row's point), "fractional amount
differs" (oracle at 2.5). KEEP "never inflates magnitude for any amount in [1, 16]" (range beyond the oracle) and
"bypasses for 0 < amount < 1" (a named regression). `IgnitorCombinatorsSpec` "crush fewer unique values": weak,
implied. CUT 1.

### 2.4 Coarse

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| NODE (no core) | ModulationClockSpec W1-W4 5, IgnitorCombinatorsSpec coarse 1 | 6 | 5 |

`IgnitorCombinatorsSpec` "coarse staircase (hold ratio > 0.8)" is implied by ModulationClockSpec W1 ("the first hold
is `amount` samples, like every other hold"). CUT 1. W1-W4 are ledger guards: KEEP.

### 2.5 Tremolo

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| NODE (TremoloCore's sole host) | TremoloLawSpec 15, TremoloNodeKnobsSpec 4, ModulationClockSpec W2 2, IgnitorCombinatorsSpec 1 | 22 | 21 |

(`WaveshaperKnobsSpec` also carries 3 tremolo knob-wiring rows, counted under 2.2; they stay.)
`IgnitorCombinatorsSpec` "tremolo amplitude varies" is dominated by TremoloLawSpec "a plain rate modulates" (full
[1 - depth, 1] range). CUT 1. Both W2 non-finite-rate rows stay: TremoloLawSpec pins the steady 1 - depth/2 value,
ModulationClockSpec pins the heal after the NaN (different assertions; could be merged into one file, no row saving).

### 2.6 Phaser

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE (PhaserCore) | none | 0 | 0 (gap) |
| BUS (effects/Phaser) | PhaserFloorLawSpec 3, PhaserClockSpec bus rows 3 | 6 | 6 |
| NODE | PhaserClockSpec ignitor rows 3, IgnitorDryFloorSpec 5 (phaser 2 + shimmer 2 + sanity 1), WetZeroBypassSpec 2, IgnitorCombinatorsSpec 1 | 11 | 10 |
| KAT | KatalystPhaserEffectSpec 14 | 14 | 14 |

The floor law (C4.2) appears at bus and node, but each checks that ITS host feeds `WetDryMix` with p = 2 and the
floor knob: wiring, KEEP. `IgnitorCombinatorsSpec` "phaser output differs from dry": weak; the sanity rows of
PhaserFloorLawSpec and IgnitorDryFloorSpec prove the wet is audible. CUT 1. KatalystPhaserEffectSpec: settled ==
bare DSP, then per-knob glide mode (C4 coefficients per sample, breakpoint per block, rate not glided), ON/OFF edges,
gate, reset: all host logic, KEEP.

### 2.7 Compressor and limiter

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE | CompressorSpec 6, CompressorSmoothnessSpec 3, master/LimiterLookaheadSpec 11 | 20 | 20 |
| KAT | KatalystCompressorEffectSpec 14, KatalystCompressorLookaheadSpec 20 (10 of them swap rows, lane C overlap) | 34 | 31 |

- `KatalystCompressorEffectSpec` "compresses loud signal" and "quiet signal passes uncompressed" restate CompressorSpec
  "reduces above threshold" / "does not affect below"; the stage's "first initialisation is instant" row already
  proves the stage == bare `Compressor` bit for bit. CUT 2. Keep "does nothing when no compressor is configured"
  (host wiring).
- `KatalystCompressorLookaheadSpec` "the lookahead is what holds the ceiling on a transient, on an orbit" restates
  `LimiterLookaheadSpec` "holds the ceiling on a transient"; the same spec's "at the output, the limiter stage is the
  master's authored limiter bit for bit" carries the wiring. CUT 1 (moderate confidence: written today, step 12 C2).
- Guards: LimiterLookaheadSpec (watched failing before the fix), CompressorSmoothnessSpec, CompressorSpec "+Inf
  sample", KatalystCompressorEffectSpec "wide ratio swing glides linearly in the inverse ratio" (ledger 2026-09-19
  5c-7), the latency-pair swap rows (ledger 2026-09-27 step 12 C2, lane C).

### 2.8 Delay (and the tail ceiling it shares with the reverb)

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE | DelayLineSpec 19, DelayLineMigrationSpec 5, TailCeilingSpec 19 | 43 | 43 |
| KAT | KatalystDelayEffectSpec 21, KatalystDelayGlideSpec 7, ClosedFormTailSpec 8 | 36 | 35 |

`KatalystDelayEffectSpec` "delay line parameters are accessible and writable": weak (reads back the constructor
value, then asserts `time = 1.0` reads 1.0). CUT 1. Self-oscillation appears at three layers (TailCeilingSpec
"|feedback| >= 1 never decays", ClosedFormTailSpec "self-oscillation keeps the tail", KatalystDelayEffectSpec
"feedback >= 1.0 never auto-drains") but they are three different mechanisms (the bound, the Active-state tail
answer against the scan oracle, the Off/Draining state machine): KEEP. Drain rows are ledger D3 and 5c-1 guards
(incl. the `Off.enter` tail reset row).

### 2.9 Reverb

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE | ReverbStabilitySpec 5 | 5 | 5 |
| KAT | KatalystReverbEffectSpec 19, KatalystReverbGlideSpec 6 | 25 | 24 |

`KatalystReverbEffectSpec` "reverb parameters are accessible": weak (reads back size and sampleRate given to the
constructor; size wiring is pinned by lane C's MasterOrbitReverbParitySpec). CUT 1. ReverbStabilitySpec is the
maintainer-decision guard (size bounded 0..1, 2026-09-16): KEEP.

### 2.10 Duck

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE | DuckingSpec 8 | 8 | 8 |
| KAT | KatalystDuckEffectSpec 19 | 19 | 17 |

"linked stereo: both channels identical reduction" restates DuckingSpec processStereo rows; "no ducking when the
sidechain is silent" restates the core law; both are implied by the stage's "settled: runs exactly what the bare
Ducking does" bit-identity row. CUT 2. Keep "no ducking configured" and "null sidechain" (host wiring).

### 2.11 Gain (fader, knob glide, pregain)

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE | KnobGlideSpec 11 | 11 | 11 |
| KAT | KatalystGainEffectSpec 14 | 14 | 12 |
| NODE | PregainSlotRenderSpec 8 | 8 | 8 |

`KatalystGainEffectSpec` "the glide bounds the step at the change" (derived from the LEVEL glide law) and "a new
target mid-glide turns from where the fader stands" restate KnobGlideSpec "LEVEL: ramps per sample along the straight
line" and "a new target mid-glide restarts from the current value". CUT 2. Keep the full-range row (it pins that the
fader uses the LEVEL mode across polarity), reset/retire/steady/katp/grace rows (host logic).

### 2.12 Oversampling

| layer | specs | rows now | rows proposed |
|---|---|---|---|
| CORE | OversamplerSpec 7, OversamplerDecimatorParitySpec 1 | 8 | 8 |

No cuts. Node wiring of the factor lives in WaveshaperKnobsSpec (kept).

## 3. Totals for lane A

| | files | rows | lines |
|---|---|---|---|
| now | 58 (+2 mixed) | 621 | about 19,040 |
| proposed | 57 (+2 mixed) | 560 | about 17,665 |
| change | -1 (-3 with the two optional moves) | -61 (-9.8 %) | -1,375 (-7.2 %) |
| with table-driven ShapingFuncsBoundsSpec | same | about 520 (-16 %) | about 17,545 (-7.9 %) |

Row-level cut list with spans: `cut_spans.txt` (55 rows, 688 lines) plus GuitarClickHuntTest (7 rows, 707 lines);
add-backs: one landmark row in ShapingFuncsBoundsSpec (about 15 lines), the folded at-fc asserts in PassesCascadeSpec
(about 4 lines).

## 4. Guards kept (in lane)

C2 FilterNormalizationSpec; C3 IgnitorFilterEnvSemitoneSpec (+7, -12, clamp); C4 WetDryMixSpec, WetZeroBypassSpec;
C4.2 PhaserFloorLawSpec, IgnitorDryFloorSpec; C5 PassesCascadeSpec (absolute rows, ladder), PassesLadderParitySpec;
FormantBlendSpec (vowel regression); OnepoleParitySpec (warmth conversion); StripLawCoresSpec (D1, D2, W5 continuity,
NaN amount); ModulationClockSpec W1-W4, W2; TremoloLawSpec W2, W10 DrunkenSailor; PhaserClockSpec D1/D2/D5/D7;
LimiterLookaheadSpec; CompressorSmoothnessSpec; the compressor inverse-ratio glide row (ledger 5c-7); delay and reverb
drain rows (ledger D3, 5c-1); ReverbStabilitySpec (maintainer 2026-09-16); body/vowel non-finite wet rows and
KatalystBodyNonFiniteWetSpec (ledger 2026-09-18, a guard rendered through its path); IgnitorsTest DC-lock and
IgniteRenderer-wrap rows (audio constraints: DC-block on distort, hard clip); DelayLineSpec "one NaN sample" (no
per-sample isFinite constraint); EqCoreSpec (D2a-c parity); IgnitorFilterKnobsSpec family loops (ledger 3a).

## 5. Weak-teeth rows

- GuitarClickHuntTest, all 7 rows: no assertion at all, only println.
- KatalystDelayEffectSpec "delay line parameters are accessible and writable": asserts a value it just wrote.
- KatalystReverbEffectSpec "reverb parameters are accessible": reads back the constructor's arguments.
- IgnitorsTest "clip fold produces non-zero output"; "bandpass passes center frequency" (non-zero, peak > 0.3).
- IgnitorCombinatorsSpec "phaser output differs from dry"; "crush fewer unique values".
- DistortionSpec "all shapes produce bounded output" (bound 3.0 for shapers bounded by 1).
- LowPassHighPassFiltersSpec three "zero-length buffer does not crash" rows (no assertion; only a throw fails them).
- IgnitorsTest "shape soft/hard direct ... < 2.5": loose, but the only law coverage of the doors' Shape node (keep
  until an oracle exists).

## 6. Gaps (a core without its own spec)

- PhaserCore: no law spec. Every phaser row is relative (stage == bare Phaser, floor differences, clock resume).
  The allpass-cascade sweep itself is unpinned; a mutation inside PhaserCore moves all hosts together.
- Reverb network: ReverbStabilitySpec pins bounds, stability and the drain arithmetic only; no oracle for the
  Freeverb output.
- The doors' distortion law (`Ignitor.drive` + `Ignitor.shape`: drive, shaper, DC blocker, softCap): no oracle;
  only loose bound rows. Writing one would let the IgnitorsTest/IgnitorCombinatorsSpec distort/shape rows go (about
  7 more rows).
- Coarse: no core at all; the S&H law lives inline in `CoarseIgnitor` (pinned on the node by ModulationClockSpec).
- DcBlocker: no direct spec; covered through distort and the dcBlock combinator row.
- The live `onePoleHighpass` Ignitor has no door, no caller and no test (dead code candidate).
- SVF LP/HP/notch and the live one-pole LPF: the kernel is inside the node, so the node spec IS the core spec; no
  separate core to move rows to.
- CrushCore, TremoloCore, DistortionCore are tested only through their one host node, against oracles written in the
  spec: acceptable, no second layer restates them.

## 7. Top 5 cuts by value

1. Delete GuitarClickHuntTest: 7 rows, 707 lines, zero assertions.
2. LowPassHighPassFiltersSpec OnePoleLPF/HPF rows: 9 rows, about 134 lines, test/benchmark-only classes.
3. DistortionSpec: 10 of 12 rows (6 merge into one ShapingFuncsBoundsSpec row, 4 cut); plus ModulationClockSpec W5.
4. CrushLawSpec 5 of 7 rows plus the combinator crush row: all implied by StripLawCoresSpec's bit-exact floor oracle.
5. The old qualitative rows in IgnitorCombinatorsSpec/IgnitorsTest (lowpass, highpass, svf LP, bandpass, notch,
   tremolo, phaser, coarse, fold: 9 rows, about 120 lines), each dominated by a law spec with an oracle.
   Runner-up: 8 Katalyst rows that re-prove a core or KnobGlide law after a bit-identity row (compressor 3, duck 2,
   gain 2, EQ 1).

## 8. Cross-lane overlaps (names only)

Lane B: FilterEnvelopeTest (13 rows restating EnvelopeLawSpec through `prepareModEnvelope`), KatalystClassicGainStageSpec,
TailCeilingSpec/ClosedFormTailSpec (tails; counted here), IgnitorsTest and IgnitorCombinatorsSpec non-effect rows.
Lane C: KatalystFilterSwapSpec, FilterSwapLaw, Katalyst*StateIdentitySpec, the 10 swap rows of
KatalystCompressorLookaheadSpec, CylinderCompressorSpec, VoiceCompressorSpec, VoicePregainWireSpec, LazyReverbSpec,
MasterRingShelfSpec, MasterOrbitReverbParitySpec, SendEffectDefaultsParitySpec, KatalystDoorFillRenderSpec,
FilterSlotLayerFillSpec, IgnitorGateSpec (per-effect build gates: wiring, keep).
