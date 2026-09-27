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
   16 to 3, `FmSynthesisTest` 14 to 2), the envelope duplicates (`EnvelopeShapeTest`,
   `FilterEnvelopeTest`, `ReleaseEndsAtZeroSpec`), `ClassicVoiceContractSpec`'s loop, and the weak
   rows each audit lists. Pointer updates: `docs/plans/block-framing-invariance.md` (the `Guard:`
   lines for `IgniteOnsetOffsetSpec` and `SampleVoiceOnsetSpec`), `audio/MEMORY.md` (`EnvelopeShapeTest`).
2. **The sprudel door forms** (lane C top cuts 1 and 2): one `LangDoorFormsSpec` over every knob and
   calling form, checking the VALUE; the 45 per-knob and pre-compound files go, their few unique rows
   move into the parent door spec; `LangKatalystParamSpec` stays the one home of the fill rule.
3. **Tables for tables' sake**: `LangVowelComprehensiveSpec` (105 rows to 3; `CatalogueIndexSpec`
   pins the whole vowel table), the KlangScript oscillator builder clones (7 files to 2 table specs),
   `ShapingFuncsBoundsSpec`.
4. **The effect layers** (lane A top cuts): the qualitative rows in `IgnitorCombinatorsSpec` and
   `IgnitorsTest` covered by law specs with oracles, `DistortionSpec` and `CrushLawSpec` into
   `StripLawCoresSpec`, the Katalyst rows that re-prove a core law after a bit-identity row, the small
   folds each audit lists.
5. **Host de-duplication** (lane C top cut 5): the cylinder compressor spec against
   `KatalystSlotResolverSpec`, the per-stage `updateFromVoice` rows, the clip and interleave tables.
6. **The baselines** (lane B top cuts 1 and 5): `BuiltInVoiceMatrixSpec` down to `untouched` plus one
   configured variant per name (474 to 87 instances), `ClassicVoiceBaselineSpec` 61 to 28 configs;
   retired or regenerated at the phase 3 end listening checkpoint (signal-flow plan section 12 keeps a
   baseline until then).
7. **The gaps, which ADD tests** (lane A): a law spec for `PhaserCore` (today every phaser row is
   relative, so a change inside the core moves all hosts together), an output oracle for the reverb
   network, an oracle for the doors' drive and shape distortion (three songs' guitars), a core for
   coarse, a direct `DcBlocker` spec. Until they exist, the host rows that are the only guard stay.

## 4. Decisions (maintainer, 2026-09-27)

- **`GuitarClickHuntTest`: tagged out of the default run.** It stays as the standing click-hunt harness
  (the parked decision in `docs/tasks/future/audit-parked-decisions.md` section 1), behind a Kotest tag
  or a dedicated task, so every suite run and mutation campaign drops its 5.4 s.
- **`MutableVoiceDataGoldenSpec`: replaced by targeted rows.** A few focused rows for what it really
  guards (no aliasing between events, the wire mapping of each field group); the 2.7 MB golden goes.
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

(none yet)

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
