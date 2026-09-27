# Test consolidation after the engine redesign

Status: **open, audited 2026-09-27.** A sub-task of phase 3 (`builtin-instruments.md`, row "test
consolidation"). The audit is done; the maintainer's decisions are in section 4; the cuts start
after phase 3 step 12 C3. Evidence, row by row: `test-consolidation-audit-A-effects.md`,
`test-consolidation-audit-B-voices.md`, `test-consolidation-audit-C-hosts-doors.md`.

## 1. The principle (maintainer, 2026-09-27)

A law is tested once, at its core. Most effects now have one DSP core that can be tested on its own;
the Ignitor node and the Katalyst stage over it test their WIRING (the right knobs reach the core,
units, defaults, the host's switching and gliding) plus a handful of integration rows per effect,
not the DSP law again. Each DSL surface tests its doors once, with a parity spec across the two doors.
The master tests nothing an effect test already covers (the step 12 cleanup, done in C3 and C5).

## 2. The numbers

Rows are counted by Kotest declaration (a row declared in a loop counts once), lines with `wc -l`. The
three lanes touch at their edges, so the totals are rough; each lane counted a shared file once.

| lane | files | rows | lines |
|---|---|---|---|
| A, effect DSP (core, Ignitor node, Katalyst stage) | 58 → 57 | 621 → 560 | 19,040 → 17,665 |
| B, voices, envelopes, `classic()`, samples, tails, baselines | 85 → 76 | 659 → 524 | 18,907 → 15,310 |
| C, hosts, wire, registries, DSL doors (master rows included) | 162 → 112 | 1,865 → 1,028 | 31,609 → 22,819 |
| **total** | **305 → 245 (-20 %)** | **3,145 → 2,112 (-33 %)** | **69,556 → 55,794 (-20 %)** |

- Rewriting `ShapingFuncsBoundsSpec` as a table (44 rows to about 4, no coverage change) takes lane A to
  520 rows and the total to about -34 %.
- The loop-driven table specs of lane B go from 772 row INSTANCES to 170 (-78 %), most of it
  `BuiltInVoiceMatrixSpec`, which is also most of that lane's JVM runtime.
- About 21 rows and 440 lines of lane C are master tests; C3 and C5 remove them.

Where the reduction lives, and where it does not:
- **The sprudel door surface is tested three times over** (a clone file per retired knob, the same
  checks in each door spec, and again in `LangFieldAccessorsSpec`'s slot tables): 405 of 881 door rows
  only re-prove a calling form. One table spec over every knob and form replaces them, and fixes a
  weak spot on the way: 22 "dsl interface" rows assert only "not empty", so the script-string and
  script-mapper forms are never checked for their VALUE anywhere today. This is the biggest cut.
- **The Katalyst specs are NOT mostly duplication.** They are about 46 % of lane A's lines, and nearly
  all of it is host logic (fades, parking, drains, knob glides, swaps, reset and retire) that no core
  spec covers. Most already follow the target pattern: one "settled: stage == bare core, bit for bit"
  row, then lifecycle rows. They stay.
- **Migration scaffolding** that outlived its job sits mostly in lane B: `ClassicVoiceContractSpec`'s
  per-row loop (it cannot fail since step 9: one tree, one path), the old SynthVoice/SampleVoice-era
  specs with "output differs" rows, envelope-law duplicates of `EnvelopeLawSpec`, the test rigs that
  translate typed fields to slots.

## 3. The work, in reviewable commits

Each commit: every removed row names the spec that covers it; a moved assertion is mutation-checked
where it lands; the guards stay (section 5); suites green; one reviewer for both roles (tests only,
no sound change), a second role only where production code moves.

1. **Scaffolding and weak rows** (lane B top cuts 2 to 4): the old voice-pipeline specs
   (`SampleVoiceSpecificTest`, `SampleVoiceRenderTest`, `VoicePipelineTest`, `PitchModulationTest`
   16 to 4 (commit 1 kept "vibrato and accelerate combine", the only guard of accelerate's multiply-in), `FmSynthesisTest` 14 to 2), the envelope duplicates (`EnvelopeShapeTest`,
   `FilterEnvelopeTest`, `ReleaseEndsAtZeroSpec`), `ClassicVoiceContractSpec`'s loop, and the weak
   rows each audit lists. Pointer updates: `docs/plans/block-framing-invariance.md` (the `Guard:`
   lines for `IgniteOnsetOffsetSpec` and `SampleVoiceOnsetSpec`), `audio/MEMORY.md` (`EnvelopeShapeTest`).
2. **The sprudel door forms** (lane C top cuts 1 and 2): one `LangDoorFormsSpec` over every knob and
   calling form, checking the VALUE; the 45 per-knob and pre-compound files go, their few unique rows
   move into the parent door spec; `LangKatalystParamSpec` stays the one home of the fill rule.
3. **Tables for tables' sake**: `LangVowelComprehensiveSpec` (105 rows to 4; `CatalogueIndexSpec`
   pins the vowel INDEX table, not the bands, so the 88 "5 bands" rows became one loop row), the KlangScript oscillator builder clones (7 files to 2 table specs),
   `ShapingFuncsBoundsSpec`.
4. **The effect layers** (lane A top cuts): the qualitative rows in `IgnitorCombinatorsSpec` and
   `IgnitorsTest` covered by law specs with oracles, `DistortionSpec` and `CrushLawSpec` into
   `StripLawCoresSpec`, the Katalyst rows that re-prove a core law after a bit-identity row, the small
   folds each audit lists.
5. **Host de-duplication** (lane C top cut 5): the cylinder compressor spec against
   `KatalystSlotResolverSpec`, the per-stage `updateFromVoice` rows, the clip and interleave tables.
6. **The baselines** (lane B top cuts 1 and 5; also the `ClassicVoiceRig` row title and baseline keys that name the
   "door-sweep row" commit 1 removed, now covered by `FilterSlotLayerFillSpec`, and the rig's dead `untouched`): `BuiltInVoiceMatrixSpec` down to `untouched` plus one
   configured variant per name (474 to 87 instances), `ClassicVoiceBaselineSpec` 61 to 28 configs;
   retired or regenerated at the phase 3 end listening checkpoint (signal-flow plan section 12 keeps a
   baseline until then).
7. **The gaps, which ADD tests** (lane A): a law spec for `PhaserCore` (today every phaser row is
   relative, so a change inside the core moves all hosts together), an output oracle for the reverb
   network, an oracle for the doors' drive and shape distortion (three songs' guitars), a core for
   coarse, a direct `DcBlocker` spec, and a bounds row for `ShapingFuncs.softCapTo` (the delay-line feedback
   saturation, covered by no spec before or after commit 3). Until they exist, the host rows that are the only guard stay.

## 4. Decisions (maintainer, 2026-09-27)

- **`GuitarClickHuntTest`: tagged out of the default run.** It stays as the standing click-hunt harness
  (the parked decision in `docs/tasks/future/audit-parked-decisions.md` section 1), behind a Kotest tag
  or a dedicated task, so every suite run and mutation campaign drops its 5.4 s.
- **`MutableVoiceDataGoldenSpec`: replaced by targeted rows.** A few focused rows for what it really
  guards (no aliasing between events, the wire mapping of each field group); the 2.7 MB golden goes.
  Done 2026-09-28: `VoiceDataAliasingSpec` (by identity, no two events of a query share data, an `Svd*` group or a
  bag, over the leaf emitters, the fan-outs and both merge paths) and ownership plus wire rows in
  `SprudelVoiceDataSpec` (fm, solo, sourceId, the per-call sourceId stamp). Of 15 aliasing and wire mutants the new
  rows kill all 15; the golden killed 5 (it never saw a merge helper, a clone that shares a group, fm or solo).
- **The baselines: trimmed now, retired or regenerated at the phase 3 end listening checkpoint.**
  `BuiltInVoiceMatrixSpec` to `untouched` plus one configured variant per name,
  `ClassicVoiceBaselineSpec` 61 to 28 configs.
- **The one-poles.** `OnePoleLPF` and `OnePoleHPF` in `filters/LowPassHighPassFilters.kt` are a second
  copy of the math the live Ignitor nodes carry (`OnePoleLowpassIgnitor`, `OnePoleHighpassIgnitor` in
  `ignitor/IgnitorFilters.kt`, same coefficients and topology): they go, with their rows, and the
  benchmark measures the live nodes instead. No capability is lost. The live `onePoleHighpass` node
  STAYS: it is a real primitive (6 dB/oct) that nothing else expresses (the SVF highpass is 12 dB/oct
  per pass; `x - onepole(x)` needs the source twice, which breaks for noise and analog drift, and has the
  Nyquist droop the canonical topology was chosen to avoid). What it lacks is a door: follow-up task
  `docs/tasks/future/onepole-highpass-door.md`.

## 4b. Unsure: for the maintainer, in one go (maintainer, 2026-09-27)

A row or spec where it is not clear whether it should go is KEPT for now and listed here, so the work does
not block; the maintainer decides the whole list later. Each entry: the spec and row, what it asserts, the
spec that might cover it, and why it is unclear.

**From commit 1** (all kept):

1. **`ignitor/FilterEnvelopeCurvesSpec` "an UNSET curve is MOD_ENV_CURVE, exponential: bit for bit the explicit
   Exponential and the resolved envelope without curves"** (audit B: cut).
   - Asserts: on all four filter kinds, the Kotlin door helper `saw.lowpass(..., attackCurve = null, ...)` renders bit
     for bit like the same call with the three curves named Exponential, and like the runtime door with an unnamed
     `FilterEnvDef`.
   - Might be covered by: `EnvelopeCurveKnobSpec` "every modulation node's CONSTRUCTOR default curve is
     MOD_ENV_CURVE" (the data classes) and `EnvelopeLawSpec` "host: the Ignitor filter envelope" (an unnamed
     `FilterEnvDef` against the Exponential oracle).
   - Unclear because: the row goes through the door HELPERS in `audio_bridge/IgnitorDsl.kt`, whose `?: modEnvCurveKnob()`
     null mapping nothing else in audio_be exercises; an audio_bridge spec may pin it (not checked in this commit).

2. **`voices/VoiceLifecycleTest` "voice with startFrame > endFrame handles edge case"** (weak list: render-returns-true).
   - Asserts: an inverted window (start 100, end 50) renders without throwing and returns true.
   - Might be covered by: nothing; the window rows (`VoiceLifecycleTest`, `ZeroLengthWindowSpec`) never invert it.
   - Unclear because: its own comment says the case is unguarded and "the result depends"; it pins undefined behaviour,
     so it is either a no-crash characterization worth one row or noise.

3. **The Double convenience overloads of four effects**: `IgnitorCombinatorsSpec` "tremolo(rate, depth) - output
   amplitude varies", "phaser(rate, wet) - output differs from dry signal", "coarse(amount) - output has
   sample-and-hold staircase pattern", and `IgnitorsTest` "clip fold produces non-zero output" (audit A top cut 5: cut).
   - Assert: the effect is audible through `tremolo(Double, Double)`, `phaser(wet, rate, ...)`, `coarse(Double)`,
     `shape("fold")`.
   - Might be covered by: `TremoloLawSpec` "a plain rate modulates", `PhaserFloorLawSpec` / `IgnitorDryFloorSpec`
     sanity rows, `ModulationClockSpec` "W1: the first hold is `amount` samples", `ShapeCatalogueSpec` ("fold" parses to
     FOLD) with `ShapingFuncsBoundsSpec` (the fold function).
   - Unclear because: those law specs build the nodes through the Ignitor-argument overloads; these rows are the only
     callers of the Double overloads, each with its own bypass branch (`depth <= 0`, `wet <= 0`, `amount <= 1`), and of
     `shape("fold")` through the string. The overloads' one production caller is `WarmupVocabulary` (JIT warmup, not
     sound), so a guard may not be worth a row.

4. **The any-difference slot rows**: `AdsrIgnitorKnobsSpec` "oscParam override reaches the declickSeconds slot" and the
   `IgnitorDefaultsTest` "responds to oscParam X" rows (audit B weak list; its per-spec verdict: keep).
   - Assert: writing the slot through the voice's bag changes the render (any difference).
   - Might be covered by: `EnvelopeDeclickSpec` (what the de-click does, but with the constructor argument, not the
     slot); the door parity specs for the oscillator knobs.
   - Unclear because: they look like the only coverage of the slot wiring; a value oracle would replace them, not a cut.

5. **`ignitor/PitchModFactoriesSpec` "vibratoMod: output is centered near 1.0 (ratio space)"** (audit B weak list).
   - Asserts: the vibrato ratio averages within 0.01 of 1.0 over a second at 10 Hz.
   - Might be covered by: `ModulatorPhaseWrapSpec` "the vibrato ignitor stays within its depth" (the bound, both ends).
   - Unclear because: the bound row does not assert symmetry about 1.0; this one does, loosely.
6. **`ignitor/AdsrIgnitorKnobsSpec` "declickSeconds>0 rounds the attack to decay corner"** (audit B: cut; RESTORED in
   commit 1's review because `audio/MEMORY.md` names it as a guard, and section 5 keeps named guards).
   - Asserts: with `declickSeconds = 0.001` the second difference at the attack-to-decay join is lower than without.
   - Might be covered by: `EnvelopeDeclickSpec` (what the de-click does, its exponential row fails without it) plus the
     kept "oscParam override reaches the declickSeconds slot" row (the wiring).
   - Unclear because: the coverage holds by the review, but the row is a named guard; the maintainer decides whether
     the MEMORY guard line moves to `EnvelopeDeclickSpec` and the row goes.
7. **A doc question, not a row**: the root `CLAUDE.md` guardrail "OnePole HPF cutoff bias is documented, not
   corrected" and `docs/tasks/future/ignitor-optimizer-open-items.md:90`. The retired `OnePoleHPF` class and the live
   `OnePoleHighpassIgnitor` both use the canonical bilinear topology with a true -3 dB at the cutoff; no bias is
   documented in code. The guardrail may date from the old topology. Keep, correct or retire it?

**From commit 2** (all kept):

Format: spec and row; what it asserts; what might cover it; why unclear.

1. `LangAdsrOnOffSpec` "the string door works", "the chained mapper door works", "the standalone mapper door works"
   (audit: 3 door-form rows to the table).
   - Asserts: `adsrOff()` / `adsrOn()` / `adsrOn(0)` through the string, chained-mapper and standalone-mapper forms put
     the flag on the wire (`adsr.on`).
   - Might be covered by: `LangDoorFormsSpec` T1 now has an `adsrOn` entry (flag "1 0", every form, the field).
   - Unclear because: `adsrOff` takes no argument, so it has no table shape; these rows are its only form coverage, and
     they read the WIRE slot where the table reads the field.

2. `LangPitchEnvelopeSpec` the four "tail-only: penv(<stage> = ...) leaves the amount untouched on a numeric receiver"
   (audit: 4 rows to 1 table row).
   - Asserts: each tail term of penv's head guard, on a numeric receiver.
   - Might be covered by: one table row over the four stages.
   - Unclear because: escape-ledger row 61 says "a guard built from OR/AND terms gets one row per term", and the
     section cites it. A table row with a clue per term may or may not satisfy that; not folded.

3. `LangPitchEnvelopeSpec` "every penv and pamt form forwards sustain and release to their own slots, by name and
   positionally".
   - Asserts: all five penv/pamt values in every form, named and positional.
   - Might be covered by: `LangDoorFormsSpec` T4 (penv and pamt positional, all eight forms) and T1 (penv named tails).
   - Unclear because: the NAMED `pamt(sustain = ..., release = ...)` path is only here (T1 has pamt as a head only), and
     a signature that swaps the two names while forwarding by position would pass T4. The same gap holds for every alias
     tail (`comp`, `uni`, `vib`, `lowpass`/`highpass`/`bandpass` named tails): never tested by name, before or after.
     Adding the alias tails to T1 is ~30 entries; not done in this commit.

4. `LangVowelSpec` "reinterpret voice data as wet | seq(\"0.2 0.5 0.8\").vowel()".
   - Asserts: the bare call writes the wet and leaves the vowel name unset.
   - Might be covered by: T2 (vowel.wet) and `LangFieldAccessorsSpec` "batch G: a bare call reinterprets the pattern's
     values as the head slot, in both doors" (asserts the vowel stays null).
   - Unclear because: `sprudel/MEMORY.md` names `LangVowelSpec` among the step 3d(iii) bare-call guards.

5. `LangPregainSpec` "the chained mapper form applies after the previous mapper", "pregain() with no argument reinterprets
   the pattern's own value".
   - Asserts: the chained form after a gain prefix; the bare reinterpret.
   - Might be covered by: T1 chained form (pregain after a pan prefix and a decoy), T2 (pregain).
   - Unclear because: `sprudel/MEMORY.md` names the whole spec as the pregain door's guard; only its dsl-interface row was cut.

6. `LangCompressorSpec` "params are independently patternable", "alternation form selects per cycle"; `LangDelaySpec`
   and `LangPhaserSpec` "per-param mini-notation patterns"; `LangOnepoleSpec` "onepole() can be applied to different
   oscillators".
   - Assert: per-event / per-cycle control values across two knobs of one call (delay and phaser also: a REST in a TAIL
     control leaves the constant the head's fill wrote).
   - Might be covered by: T1 (per event), `LangFieldAccessorsSpec` gap rows (per cycle, heads only).
   - Unclear because: the tail-rest-after-fill relation has no other home; the rest are form-ish but cheap.

7. `LangBodySpec` "every catalogue material resolves to an 8-mode body (except 'none')" (audit: duplicate of
   `CatalogueIndexSpec`).
   - Not a door-form row: the table-for-its-own-sake kind that commit 3 handles (`LangVowelComprehensiveSpec`'s twin).

8. `LangWetKnobSpec` the three Ignitor rows, and `LangKatalystSpec` "fold 2 replace rows" (audit).
   - Not sprudel door forms: the Ignitor rows belong in `IgnitorDslSpec` / `KlangScriptEffectBuilderSpec`
     (klangscript-libs, not touched here), the Katalyst replace rows are the chain door's own rule. Left for a later commit.

**From commit 3** (kept):

1. **`LangVowelComprehensiveSpec`, the loop row over every catalogue name** (the audit proposed cutting the 88 "5 bands"
   rows to a subset). `CatalogueIndexSpec` pins the index table but not the band count or the formants beyond one
   anchor, so the rows became ONE loop row (the index the door writes, and 5 bands per bank) instead of going.
   Unclear whether a band-count check per name is worth a loop at all.

**From commit 4** (all kept):

Format: spec and row; what it asserts; what might cover it; why unclear.

1. `cylinders/katalyst/KatalystGainEffectSpec` "the glide bounds the step at the change, where a one-block ramp and a
   snap would not" and "a new target mid-glide turns from where the fader stands, never from where the old glide was
   going" (audit A 2.11: cut).
   - Assert: the fader's step at a change is bounded by the LEVEL glide; a retarget mid-glide starts from the current value.
   - Might be covered by: `KnobGlideSpec` "LEVEL: ramps per sample along the straight line" and "a new target
     mid-glide restarts from the current value", plus the kept full-range row (the fader uses the LEVEL mode).
   - Unclear because: `audio/MEMORY.md` names both as mutation-checked guards of the fader ("the step bound, a retarget
     mid-glide turning from where the fader stands"); section 5 keeps named guards. The maintainer decides whether the
     MEMORY guard line moves to KnobGlideSpec and the rows go.

2. `cylinders/katalyst/KatalystCompressorLookaheadSpec` "the lookahead is what holds the ceiling on a transient, on an
   orbit" (audit A 2.7: cut, moderate confidence).
   - Asserts: an authored limiter stage with a 5 ms lookahead lets no sample of a +12 dB kick past 1.0; without the
     lookahead some do (the control).
   - Might be covered by: `master/LimiterLookaheadSpec` "the limiter holds the ceiling on a transient" plus this spec's
     "the limiter stage is the Compressor its constructor builds from the same numbers, bit for bit".
   - Unclear because: LimiterLookaheadSpec runs the HOUSE limiter (`HOUSE_LIMITER_ATTACK_SECONDS`, the house lookahead),
     this row the AUTHORED limiter (`AUTHORED_LIMITER_ATTACK_SECONDS`, 5 ms): the ceiling is not proven for this
     configuration anywhere else. Written in step 12 C2, the day before.

3. `ignitor/IgnitorCombinatorsSpec` "crush(amount) - output is quantized (fewer unique values than input)" (audit A 2.3:
   cut, weak list).
   - Asserts: `Ignitor.crush(3.0)` has fewer unique values than its input.
   - Might be covered by: `StripLawCoresSpec` FLOOR oracle rows (through `crush(Ignitor)`).
   - Unclear because: the same case as 4b item 3 (commit 1): this row is the only test caller of the runtime
     `Ignitor.crush(Double)` convenience overload, which has its own `amount < 1.0` short-circuit (the only other test
     use, `SharedScratchSpec`, calls the DSL `IgnitorDsl.crush`). Belongs with item 3's decision.

## 5. Guards that stay, whatever the cut

Everything named in root `CLAUDE.md` (guardrails), `.claude/skills/review-loop/escape-ledger.md`,
`.claude/skills/review-loop/audio-constraints.md`, `audio/MEMORY.md`, or a spec KDoc that says it pins an
escape or a maintainer decision. Each audit lists its lane's guards by name; among them `EnvelopeLawSpec`
(the one envelope-law home), `IgnitorTailSpec`, `ClassicSlotParamsSpec`, `FilterSlotLayerFillSpec`,
`BlockFramingInvarianceSpec`, `StripLawCoresSpec`, `FilterNormalizationSpec`, `ReverbStabilitySpec`,
`LimiterLookaheadSpec`, `KatalystDefaultsSyncSpec`, `ChainSwapStateIdentitySpec`, `StdLibOscTest`,
`StdLibNumberMethodsTest`, `StructuralCycleSelectionSpec`.

## 6. When

After phase 3 step 12, unless the maintainer wants a cheap early commit (commit 1 touches no production
code and no file step 12 changes). The master's own cleanup is not here: it happens in step 12 C3 and C5.
