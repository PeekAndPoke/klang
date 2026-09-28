> Audit of 2026-09-27 (read-only, three parallel auditors), the evidence behind `test-consolidation.md`. Line and row numbers refer to `engine-redesign` at `520801eb`; they drift as the code moves.

# Test audit, lane B: voices, envelopes, classic(), samples, lifetime, pitch pipeline

Branch `engine-redesign` at c12cdff5, read only. Rows are Kotest row declarations (a row declared inside a
`for` loop counts once); where a loop generates many instances, the instance count is given separately.
Lines are `wc -l`. Counter: `rows.py` in this folder. Cross-lane items are named, not audited.

## 0. The lane's file set

| area | files | rows | lines |
|---|---|---|---|
| audio_be `voices/` (31, without `VoiceCompressorSpec`, which tests a Katalyst config: lane A/C) + root `EnvelopeLawSpec`, `RealtimeVoiceSpec`, `SchedulerStartupSpec` | 34 | 279 | 8897 |
| audio_be `ignitor/` lane-B subset (envelopes, classic, tails, gate, pitch mods, samples, parity, rigs; list in section 2) | 30 | 236 | 6891 |
| audio_be `jvmTest`: `ClassicVoiceBaselineSpec`, `BuiltInVoiceMatrixSpec` | 2 | 4 | 811 |
| audio_bridge: `ClassicTailSpec`, `AdsrCurvesSpec`, `AdsrDefTest`, `PregainSlotSpec` | 4 | 24 | 434 |
| sprudel slot-writing: `ClassicSlotParamsSpec`, `ClassicDoorRenderParitySpec`, `_wire_slot_readers.kt`, `LangAdsr*` (4), `LangFilterCurvesSpec`, `LangPassesSpec`, `LangDefaultQSpec`, `LangPenv*` (2), `LangPsustainSpec`, `LangPitchEnvelopeSpec`, `LangPregainSpec` | 15 | 116 | 1874 |
| **total** | **85** | **659** | **18907** |

Loop-generated instances in the four table specs: `ClassicVoiceBaselineSpec` 123, `BuiltInVoiceMatrixSpec` 474,
`ClassicVoiceContractSpec` 141, `SampleInstrumentSpec` 34 (772 together).

`ClassicTailRenderSpec` is in `audio_be/src/commonTest/kotlin/ignitor/`, not root `src/jvmTest`; root `src/jvmTest`
holds no lane-B render spec (its Katalyst render specs are lane C).

## 1. Laws asserted more than once (the overlap map)

| law | its core (keep) | duplicates found |
|---|---|---|
| per-stage curve shapes g(p), decay `s+(1-s)g(1-p)`, release `L g(1-p)`, all six curves | `EnvelopeLawSpec` row "each stage takes its own curve" (closed forms, 1e-15) | `EnvelopeShapeTest` (all 12 rows: midpoints per curve, decay/sustain boundary, release midpoint and endpoint through `calculateControlRateEnvelope`) |
| release counts floor(N), exact 0.0 on the last rendered frame, every curve; under two frames is 0 | `EnvelopeLawSpec` rows 101, 115; chain host row (`gated[60] == 0.0`); `BareTreeVoiceSpec` 147 through `VoiceFactory` | `ReleaseEndsAtZeroSpec` (3 rows); `EnvelopeShapeTest` 164 |
| control-rate mod envelope (filter, FM): early gate-off, zero/negative stages, sustain 0/1, clamp [0,1] | `EnvelopeLawSpec` core rows 90, 126, 146 and host rows "Ignitor FM index envelope", "Ignitor filter envelope" | `FilterEnvelopeTest` (all 13 rows, tolerances 0.01 to 0.02 on exact arithmetic) |
| `ADSR_EXP_K` = 3 | `AdsrIgnitorKnobsSpec` 83 (bit-exact closed form through the DSL) | `EnvelopeShapeTest` 67 (midpoint 0.1824); `EnvelopeLawSpec` `expCurve` oracle (1e-9) |
| unwritten chain curve = Exponential | `EnvelopeCurveKnobSpec` 97 (last clause) and 118 | `AdsrCurveDefaultRenderSpec` 64; sprudel `LangAdsrCurveDefaultSpec` 47 |
| unwritten modulation curve = `MOD_ENV_CURVE` = Exponential | `EnvelopeCurveKnobSpec` 247 (constructor defaults, 4 filters + pitch, by render); `EnvelopeLawSpec` FM host (expCurve oracle) | `ModEnvelopeDefaultCurveSpec` 147 (FM node, the same oracle as the EnvelopeLawSpec FM host row), 168 (filters); `FilterEnvelopeCurvesSpec` 122; `ClassicVoiceContractSpec` "unwritten filter curve IS Exponential" (x2 rates) |
| pitch envelope host law | `PitchEnvelopeAdsrSpec` (bit-exact host oracle) | `EnvelopeLawSpec` "host: the Ignitor pitch envelope" (keep both: the host has its own level-0 fast path); inside `PitchEnvelopeAdsrSpec`, 221 (every curve every stage) repeats `EnvelopeCurveKnobSpec`'s pitch rows plus the core; 146 is a subset of 167 |
| filter-envelope slot-layer fill (a stage written alone fills 7 st) | `FilterSlotLayerFillSpec` (escape row 62, one row per meaning) | `ClassicVoiceContractSpec` "the D3 fill row IS the door's sweep" |
| `adsr.on`: 0 builds nothing, keeps the tail; unset/NaN is on | `IgnitorGateSpec` 434, 467, 478 | `ClassicTailRenderSpec` 77, 81, 98 (the classic() wiring needs one row, not three) |
| `endsInEnvelope` truth table (authored own envelope, modulated release, switched off) | `IgnitorTailSpec` 199 (escape row 71) + `BareTreeVoiceSpec` 131/147 (factory wiring of both branches) | `AuthoredClassicSpec` 202 (modulated release, render level) |
| teardown fade follows a moved end (realtime note-off) | `RealtimeVoiceSpec` 369 | `TreeVoiceSpec` 146 (same `TeardownFadeRenderer`, same `releaseGate`) |
| doors reach an authored classic() tree | `AuthoredClassicSpec` 169 | `TreeVoiceSpec` 129; `ClassicVoiceContractSpec` per-row "AUTHORED IS the built-in" (61 rows x 2 rates) |
| every classic() slot is engaged | `ClassicDoorRenderParitySpec` (every slot against the same bag without it, 64 rows) | `ClassicVoiceContractSpec` per-row engagement (122); `BuiltInVoiceMatrixSpec` engagement (430); `SampleInstrumentSpec` per-stage loop (24) |
| pregain on a linear tree is an exact scale | `PregainSlotRenderSpec` 118 | `ClassicVoiceContractSpec` "pregain 2 doubles every sample exactly" |
| onepole slot in Hz, first classic() stage, in front of crush | `ClassicTailRenderSpec` 51 (+ `ClassicTailSpec` 52 order) | `OnepoleParitySpec` 68, 93 |
| gain 0 latches `heard`, the muted voice is culled | `VoiceGainWireSpec` 278 (with the late control) | `VoiceCullingSpec` 278 |
| late voice dropped and counted; the boundary is inclusive | `SchedulerStartupSpec` B2 rows (admission precedes the sample/osc decision) | `SampleVoiceOnsetSpec` 182, 198 |
| note-relative output identical at onsets 1, 37, 76, 127 | `BlockFramingInvarianceSpec` "I1 adsr on DC" (bit-identical, through `VoiceFactory`) | `IgniteOnsetOffsetSpec` (3 rows, 1e-12 and symptom thresholds) |
| tremolo keeps a voice from culling; the author's cull wins | `VoiceSchedulerCullingSpec` 98/109 (via slots since step 8) and 161/173 (tree) | 205/216 (the "slot-written, no typed field" pair; the typed field is gone, so it is the 98/109 path) |
| sample playhead: interpolation, rate, loop wrap, stopFrame, negative playhead, phaseMod | `SampleIgnitorTest` | `SampleVoiceSpecificTest` (16), `SampleVoiceRenderTest` (2) |
| voice windowing (before start, after end, mid-block partial) | `VoiceLifecycleTest` | `VoicePipelineTest` (all 5 rows); inside `VoiceLifecycleTest`, 235/247 repeat 20/35 |
| ignitor pitch-mod nodes at zero depth are exactly 1.0, near 1.0 at normal depth | `PitchModFactoriesSpec` 51, 57, 87, 115 | `PitchModSafetyTest` 55, 71, 97, 119; `PitchModFactoriesSpec` 184 vs `PitchModSafetyTest` finite rows |
| `AdsrCurve` enum entries | `AdsrCurvesSpec` 43 | `AdsrDefTest` (1 row) |
| classic() slot defaults and the wire map | `ClassicTailSpec` 93; `ClassicSlotParamsSpec` 114 (escape row 70, the full literal map) | `LangAdsrCurveDefaultSpec` 30; `LangFilterCurvesSpec` 136 |

## 2. Per-spec verdicts

### audio_be voices/ and root (34 files, 279 rows, 8897 lines)

| spec | rows | verdict | rows after | note |
|---|---|---|---|---|
| EnvelopeLawSpec | 14 | KEEP, the one envelope home | 14 | core + every host; escape row 65 rows |
| RealtimeVoiceSpec | 21 | KEEP | 21 | realtime home |
| SchedulerStartupSpec | 4 | KEEP (B1/B2 guard) | 4 | |
| AuthoredClassicSpec | 6 | cut 202 | 5 | F1 rows are a maintainer decision (2026-09-26) |
| BareTreeVoiceSpec | 6 | KEEP; fold one fractional-N exponential release assertion from ReleaseEndsAtZeroSpec into 147 | 6 | maintainer 2026-09-27 (no fade-in) |
| BlockFramingInvarianceSpec | 10 | KEEP (E3 KNOWN DEFECT pin) | 10 | |
| EnvelopeDeclickSpec | 2 | KEEP (guard, audio/MEMORY) | 2 | |
| EnvelopeShapeTest | 12 | DELETE | 0 | fix the audio/MEMORY pointer (line ~2587) to EnvelopeLawSpec |
| FmSynthesisTest | 14 | keep 48 (depth 0 vs real) and 227 (modulator phase advance exact); cut 12 "differs" rows | 2 | FM law: ModulatorPhaseWrapSpec 113, MidBlockOnsetControlRateSpec, EnvelopeLawSpec control-rate host |
| IgniteOnsetOffsetSpec | 3 | DELETE, guard moves to BlockFramingInvarianceSpec "I1 adsr on DC" | 0 | named "Guard:" in `docs/plans/block-framing-invariance.md` line 77 and 610: update the pointer. Conservative fallback: keep |
| ModulatorPhaseWrapSpec | 4 | KEEP (guard) | 4 | |
| PitchModulationTest | 16 | keep 46 (+ 420's negative-depth clause), 68, 320 (vibrato reaches the FM modulator); cut 13 | 3 | penv law: EnvelopeLawSpec strip pitch host, StripPitchEnvelopeParitySpec; accelerate: AccelerateSemitoneLawSpec |
| SampleInstrumentSpec | 8 (34 inst.) | KEEP the rows; shrink the 12-stage table to 2 entries (all stages at once, adsrOff) | 8 (14 inst.) | the SoundFont guard (audio/MEMORY); row 228 pins the shape |
| SamplePlayheadStartSpec | 10 | KEEP | 10 | |
| SampleVoiceOnsetSpec | 4 | cut 182, 198 | 2 | update block-framing plan line 518 |
| SampleVoiceRenderTest | 2 | DELETE | 0 | |
| SampleVoiceSpecificTest | 16 | DELETE | 0 | stacked-mod finite check survives in SynthVoiceTest 161 |
| SeededPlaybackReproducibilitySpec | 4 | KEEP | 4 | |
| SynthVoiceTest | 11 | merge 26/37/48 into one row, cut 135 (adsr node) | 8 | IgniteRenderer plumbing: phaseMod, offset/length, elapsed frames |
| TreeVoiceSpec | 6 | cut 129, 146; merge 163+179 | 3 | 163/179 are escape row 68's guard; 136 is escape row 69's |
| VcaOffTeardownSpec | 8 | KEEP | 8 | the fade renderer's own spec |
| VoiceBagGuardSpec | 14 | KEEP (guard; optional merge of the +/-Inf/NaN rows, -4, no assertion lost) | 14 | escape row 40, VoiceFactory KDoc |
| VoiceCullingSpec | 14 | cut 278 | 13 | guard (mutation-checked) |
| VoiceGainWireSpec | 12 | merge the 7 `wireGain` rows into 2 (non-finite reads unset; finite passes raw) | 7 | guard |
| VoiceLifecycleTest | 18 | cut 181 (envelope node), 219, 235, 247, 259, 312, 332 | 11 | |
| VoicePipelineTest | 5 | DELETE | 0 | |
| VoicePregainWireSpec | 6 | merge NaN/+Inf/-Inf | 4 | |
| VoiceSchedulerCullingSpec | 8 | cut 205, 216 | 6 | |
| VoiceSchedulerSoloCutSpec | 11 | KEEP | 11 | |
| ZeroLengthWindowSpec | 3 | KEEP (O8) | 3 | |
| strip/MidBlockOnsetControlRateSpec | 1 | KEEP | 1 | |
| strip/pitch/AccelerateSemitoneLawSpec | 1 | KEEP (strip host; the node's is PitchModFactoriesSpec 102) | 1 | |
| VoiceTestHelpers, _typed_fields_as_slots | 0 | rigs, see scaffolding | | |

After: 29 files, 190 rows, about 6830 lines (-89 rows, about -2070 lines).

### audio_be ignitor/ lane subset (30 files, 236 rows, 6891 lines)

| spec | rows | verdict | rows after |
|---|---|---|---|
| AdsrCurveDefaultRenderSpec | 3 | cut 64 (DSL runtime; the two raw factory overload rows stay) | 2 |
| AdsrIgnitorKnobsSpec | 5 | cut 111 (expK migration guard), 63 (declick corner; EnvelopeDeclickSpec is the guard) | 3 |
| EnvelopeCurveKnobSpec | 6 | KEEP, the curve-knob home | 6 |
| FilterEnvelopeCurvesSpec | 3 | cut 122 | 2 |
| FilterEnvelopeTest | 13 | DELETE | 0 |
| ModEnvelopeDefaultCurveSpec | 5 | cut 147 (FM node), 168 (filters); keep the raw pitch factory default and the two voice-pipeline hosts | 3 |
| PitchEnvelopeAdsrSpec | 8 | cut 221, merge 146 into 167 | 6 |
| ReleaseEndsAtZeroSpec | 3 | DELETE (one assertion folded into BareTreeVoiceSpec 147) | 0 |
| IgnitorGateSpec | 26 | KEEP (gate core, audio/MEMORY) | 26 |
| IgnitorTailSpec | 18 | KEEP (escape row 71) | 18 |
| ClassicTailRenderSpec | 6 | merge 77, 81, 98 into one `adsr.on` wiring row | 4 |
| ClassicVoiceContractSpec | 8 (141 inst.) | cut the per-row loop, "harness sees sound", "unwritten filter curve", "D3 fill", "pregain doubles"; keep the named-curves loop and the two pregain placement rows | 3 (12 inst.) |
| ClassicVoiceRig | 0 | drop the oracles only the cut rows use (`authoredsaw`, `expfilter`, `linfilter`, `doorfill`, `doorFilledLowpass`); trim `rows` with the baseline | |
| FilterSlotLayerFillSpec | 13 | KEEP (escape row 62) | 13 |
| FilterPassesKnobSpec | 8 | KEEP (engine side of the count; sprudel side is LangPassesSpec) | 8 |
| PregainSlotRenderSpec | 8 | KEEP | 8 |
| StripPitchEnvelopeParitySpec | 3 | KEEP (two live hosts, parameter parity) | 3 |
| OnepoleParitySpec | 4 | DELETE: 68, 93 covered by ClassicTailRenderSpec 51; 116 (the magnitude pin at the cutoff) and 150 (the coefficient at 12 kHz) MOVE to lane A's one-pole kernel spec | 0 |
| PitchModFactoriesSpec | 18 | cut 184 | 17 |
| PitchModSafetyTest | 15 | cut 55, 71, 97, 119 | 11 |
| AbsoluteFreqPitchModSpec | 7 | KEEP (W13) | 7 |
| IgnitorOctaveShiftSpec | 6 | KEEP | 6 |
| VibratoConsistencyTest | 5 | merge the three depth rows into one loop | 3 |
| SampleIgnitorTest | 10 | KEEP, the playhead home | 10 |
| SeededVoiceRngSpec | 4 | KEEP | 4 |
| IgnitorFilterEnvSemitoneSpec | 5 | KEEP (lane A overlap with SvfNodeLawSpec) | 5 |
| IgnitorDefaultsTest | 19 | KEEP rows; fix the stale "before AND after the refactoring" KDoc | 19 |
| GuitarClickHuntTest | 7 | KEEP (maintainer, parked decision 1: tag it out of the default run; asserts nothing, 78 % of suite runtime) | 7 |
| _classic_render_helpers, _render_hash | 0 | keep | |

After: 27 files, 194 rows, about 5900 lines (-42 rows, about -990 lines).

### jvmTest baselines (2 files, 4 rows, 811 lines, 597 instances)

**ClassicVoiceBaselineSpec** (61 row configs x 2 rates). KDoc: a BASELINE (signal-flow plan section 12), frozen at
step 9 (a) from the strip-era `classic()` renders, regenerated at a listening checkpoint. Its migration job (prove
steps 9 (a), (a2), (b) moved no sound: 17/17 and 19/19 identical) is done. By section 12 a baseline is not deleted
with the migration, it lives to the next ear checkpoint. What only it guards today: exact end-to-end bits of the
classic() voice through `VoiceFactory`, notably the analog draw order with filters (section 8), 44.1 kHz fractional
frame counts, and the combination rows. Every stage LAW has its own oracle spec (lane A for crush/distort/SVF/
tremolo; EnvelopeLawSpec; EnvelopeCurveKnobSpec). Proposal: trim to 28 configs (one per stage and mode, the
envelope/lifetime rows, the four analog rows that matter, the three combos): cut crush 8, crush 1, coarse 7.5,
distort 0.3/1.0/2.0 soft, tube x0, gentle, hard, fold, rectify, soft x2, hpf 400, lpf 1200, all four filters, lpf env
24, lpf attack only, lpf explicit env 0, bpf env 12, notch env 12, hpf/bpf/notchCurves, the four single-door step
envelopes, analog hpf env step, analog lpf 1200, tremolo depth only, adsr release 0.02, onepole with distort, unknown
shape names (33). 123 -> 57 instances. Alternative: retire it at the phase 3 end checkpoint and let
BuiltInVoiceMatrixSpec's analog rows carry the draw-order record.

**BuiltInVoiceMatrixSpec** (43 names x 11 stage groups). Same origin (step 6 parity, frozen at step 9 (a)). The ten
stage rows run the one `classic()` chain 43 times; what differs per name is only the source. Proposal: keep
`untouched` (the only bit-level fingerprint of each built-in's source) and `analog 2, lpf 1200` (the section 8
draw-order record: perlin, berlin, crackle draw at construction) plus the contract row. 474 -> 87 instances, about
-390 lines, most of its "about a minute" of JVM time.

After: 2 files, 4 rows, about 340 lines, 144 instances.

### audio_bridge (4 files, 24 rows, 434 lines)

`AdsrDefTest` DELETE (dup of `AdsrCurvesSpec` 43). `ClassicTailSpec` KEEP (structural contract; escape row 69 rows
on `endsInClassic` through an optimizer hint). `AdsrCurvesSpec`, `PregainSlotSpec` KEEP. After: 3 files, 23 rows, 414
lines.

### sprudel slot-writing (15 files, 116 rows, 1874 lines)

`ClassicSlotParamsSpec` KEEP (escape row 70). `ClassicDoorRenderParitySpec` KEEP: after the ClassicVoiceContractSpec
loop goes it is the one per-slot engagement sweep (its own KDoc says the script-vs-Kotlin bit compare cannot fail
apart from the tree row; keep as stated). `LangAdsrCurveDefaultSpec` cut 30 (wire default: ClassicTailSpec 93 /
ClassicSlotParamsSpec 45) and 47 (engine node default: EnvelopeCurveKnobSpec 97/118). `LangFilterCurvesSpec` cut 136
(ClassicSlotParamsSpec 114 holds the curves per door). `LangPassesSpec` KEEP (more thorough than
ClassicSlotParamsSpec 68 at the same code site). `LangPsustainSpec` 49 KEEP (the pitch fields are typed wire fields,
not classic slots, so ClassicSlotParamsSpec does not cover them). `LangDefaultQSpec` 65/82/90 test Ignitor and
KlangScript door defaults: lane C. After: 15 files, 113 rows, about 1827 lines.

Cross-lane with C: 39 sprudel voice-door files (`LangLp*`, `LangHp*`, `LangBp*`, `LangNf*`, `LangNotchf`,
`LangNresonance`, `LangFm*`, `LangVibrato*`, `LangPitchEnvelope`, `LangBegin`, `LangSpeed`, `LangGain`,
`LangVelocity`, ...) follow a template whose "sets field", "string extension" and "compiled code" rows are subsumed
by the same file's "dsl interface" row (which runs all six to eight forms); about 138 such rows. The per-knob files
of one door (notch: 8 files) could fold into one door spec with a knob loop. Not counted in lane B.

## 3. Totals

| area | files | rows | lines | instances |
|---|---|---|---|---|
| voices + root | 34 -> 29 | 279 -> 190 | 8897 -> ~6830 | |
| ignitor subset | 30 -> 27 | 236 -> 194 | 6891 -> ~5900 | ClassicVoiceContract 141 -> 12 |
| jvm baselines | 2 -> 2 | 4 -> 4 | 811 -> ~340 | 597 -> 144 |
| audio_bridge | 4 -> 3 | 24 -> 23 | 434 -> 414 | |
| sprudel | 15 -> 15 | 116 -> 113 | 1874 -> ~1827 | SampleInstrument 34 -> 14 (audio_be) |
| **lane B** | **85 -> 76 (-11 %)** | **659 -> 524 (-20 %)** | **18907 -> ~15310 (-19 %)** | **772 -> 170 (-78 %)** |

Optional on top (no coverage change, mechanical): retire the two rigs `_typed_fields_as_slots.kt` (169) and
sprudel `_wire_slot_readers.kt` (83): -2 files, about -250 lines (plus some inline slot maps in 13 users).

## 4. Scaffolding that outlived its job

1. `ClassicVoiceContractSpec` per-row "AUTHORED IS the built-in" (122 instances): since step 9 the factory and the
   registry have no built-in flag (`VoiceFactory.makeVoice`, `IgnitorRegistry.builtInVoice`); both registrations are
   one tree on one path, so the comparison is a tautology.
2. `ClassicVoiceBaselineSpec` and `BuiltInVoiceMatrixSpec`: frozen at step 9 (a) as "the strip's sound"
   (audio/MEMORY line 17); that migration is finished. Section 12 keeps baselines to the next ear checkpoint, so:
   trim now, regenerate or retire at the phase 3 end checkpoint.
3. `AdsrIgnitorKnobsSpec` "the old expK oscParam is an unread key" (the knob was removed 2026-09-25).
4. `OnepoleParitySpec` "conversion pin == the old warmth(0.5)" (the warmth migration of 2026-08-24; section 12 named
   the spec as an audit candidate). The coefficient value itself moves to lane A.
5. `_typed_fields_as_slots.kt` (`DoorFields`, `withClassicSlots`): a test copy of sprudel's classic-slot mapping so
   pre-step-9 specs keep their typed shape; escape row 70 records that "the audio_be specs used a test copy".
   `ClassicVoiceRig` already writes slot keys directly.
6. sprudel `_wire_slot_readers.kt`: the reverse rig, slot keys read back into the old `WireFilter` shape.
7. `VoiceTestHelpers.createSynthVoice` ("Backward-compatible alias"), and the SynthVoice/SampleVoice naming in
   `SynthVoiceTest`, `SampleVoiceSpecificTest`, `VoiceLifecycleTest` 259: the two voice classes are long merged.
   Related parked decision 3 (`docs/tasks/future/audit-parked-decisions.md`): `createVoice` bypasses `VoiceFactory`.
8. `VoiceSchedulerCullingSpec` 205/216, "no typed tremolo field": the typed-versus-slot distinction ended in step 8.
9. `IgnitorDefaultsTest` KDoc "must pass BEFORE and AFTER the refactoring": stale text, the rows are contracts.
10. `AuthoredClassicSpec`, `TreeVoiceSpec`, `ClassicVoiceContractSpec` KDocs still narrate the strip-side twin they
    lost; fine, no action beyond the cuts.

## 5. Guards that stay (named in CLAUDE.md, the escape ledger, audio-constraints, MEMORY, or a decision KDoc)

- `EnvelopeLawSpec` (the one law home; escape row 65's gate-at-or-before-onset rows).
- `IgnitorTailSpec` 199 (escape row 71), `ClassicTailSpec` endsInClassic rows and `TreeVoiceSpec` 136 (escape row 69).
- `ClassicSlotParamsSpec` 114 (escape row 70). `FilterSlotLayerFillSpec` (escape row 62).
- `TreeVoiceSpec` 163/179, `SampleInstrumentSpec` 218, the baseline's `adsr release -0.1` row (escape row 68).
- `VoiceBagGuardSpec` (escape row 40, VoiceFactory KDoc), `VoiceGainWireSpec`, `VoiceCullingSpec`,
  `ModulatorPhaseWrapSpec`, `EnvelopeDeclickSpec`, `IgnitorGateSpec`, `SampleInstrumentSpec` (audio/MEMORY).
- `BlockFramingInvarianceSpec` (E3 KNOWN DEFECT pin), `ZeroLengthWindowSpec` (O8), `SchedulerStartupSpec` (B1/B2),
  `MidBlockOnsetControlRateSpec`, `VcaOffTeardownSpec`, `PitchModFactoriesSpec` E2/E10 rows, `AbsoluteFreqPitchModSpec`
  (block-framing ledger).
- `AdsrIgnitorKnobsSpec` 83 (ADSR_EXP_K, maintainer 2026-09-25); `AuthoredClassicSpec` F1 rows (maintainer
  2026-09-26); `BareTreeVoiceSpec` (maintainer 2026-09-27).
- `GuitarClickHuntTest` (maintainer: keep, move out of the default run).
- Two-door parity contracts on live hosts: `StripPitchEnvelopeParitySpec`, `VibratoConsistencyTest`,
  `ClassicDoorRenderParitySpec`.

## 6. Weak-teeth rows (separate list)

- `PitchModulationTest` 13 rows and `FmSynthesisTest` 12 rows: "output differs by more than 1e-4" or "a > b"
  (cut above).
- `SampleVoiceSpecificTest` 243, 283, 321 ("any difference > 1e-9"), 428 (`buffer[15] >= 0.0`) (cut above).
- `VoiceLifecycleTest` 219 and 332 (only `render(...) shouldBe true`), 181 (`midRelease < atGateEnd`).
- `PitchModFactoriesSpec` 57, 66 (`rms > 0.9` for a ratio near 1 is always true), 72, 125, 149, 160 (relative or
  loose tolerances); 184.
- `AdsrIgnitorKnobsSpec` 63 (one `<` compare), 128 (any difference).
- `IgnitorDefaultsTest` "produces non-zero output" and "responds to oscParam X" (any difference).
- `VibratoConsistencyTest` (parity within 1e-3), `EnvelopeDeclickSpec` (slew < 0.1, loose but a named guard).
- `FilterEnvelopeTest` (0.01 to 0.02 tolerances on exact arithmetic; cut above).
- `IgniteOnsetOffsetSpec` 105, 113 (symptom thresholds; cut above).
- `SynthVoiceTest` 161 (non-zero and finite).
- Self-declared: `ClassicDoorRenderParitySpec` script-vs-Kotlin bits ("cannot fail independently"),
  `SampleInstrumentSpec` 228 ("close to a value echo on purpose").
- `GuitarClickHuntTest`: no assertions at all (by design).

## 7. Top 5 cuts by value

1. `BuiltInVoiceMatrixSpec` to 2 rows per name: -387 fingerprints, about -390 lines, most of a minute of JVM time.
2. `ClassicVoiceContractSpec` per-row loop and four duplicate rows: -122 tautological instances, -5 rows.
3. The legacy voice-pipeline specs: delete `SampleVoiceSpecificTest`, `SampleVoiceRenderTest`, `VoicePipelineTest`;
   `PitchModulationTest` 16 -> 3, `FmSynthesisTest` 14 -> 2: -48 rows, about -1330 lines, mostly weak teeth.
4. Envelope-law duplicates: delete `EnvelopeShapeTest`, `FilterEnvelopeTest`, `ReleaseEndsAtZeroSpec`, trim
   `ModEnvelopeDefaultCurveSpec` and `FilterEnvelopeCurvesSpec`: -31 rows, about -670 lines; `EnvelopeLawSpec` and
   `EnvelopeCurveKnobSpec` stay the homes.
5. `ClassicVoiceBaselineSpec` trim 61 -> 28 configs (-66 fingerprints), or retire at the phase 3 end checkpoint.

## 8. Cross-lane mentions

- Lane A: `OnepoleParitySpec` 116 and 150 move to the one-pole kernel spec; `StripLawCoresSpec`,
  `SvfNodeLawSpec`, `TremoloLawSpec` carry the stage laws the trimmed baseline rows leave; `IgnitorFilterEnvSemitoneSpec`
  and `FilterPassesKnobSpec` overlap the SVF node; `VoiceCompressorSpec` (lives in `voices/`, tests the Katalyst
  compressor config).
- Lane C: `LangDefaultQSpec` 65/82/90, the sprudel door template (about 138 subsumed rows in 39 files),
  `IgnitorDslSpec` and the wire codec specs in audio_bridge, `KlangAudioRendererSpec`, `PlaybackEngineDispatcher*`,
  `LongRunningTimelineSpec`, `WarmupVocabularySpec`.
- Unassigned (oscillator sources, neither A nor my lane): `IgnitorsTest`, `PhasePool*`, `SuperStack*`,
  `SinePartialBankSpec`, `AnalogDrift*`, `DriftLanesSpec`, `Noise*`, `*DefaultsSyncSpec`.
