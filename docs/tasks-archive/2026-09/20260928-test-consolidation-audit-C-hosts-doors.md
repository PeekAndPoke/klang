> Audit of 2026-09-27 (read-only, three parallel auditors), the evidence behind `test-consolidation.md`. Line and row numbers refer to `engine-redesign` at `520801eb`; they drift as the code moves.

# Test audit, lane C: hosts, wire and registries, DSL doors and defaults

Branch `engine-redesign` at c12cdff5, read only. Rows are Kotest row declarations (StringSpec
`"..." {` lines, FunSpec `test(`), a row generated in a loop counts once. Lines are `wc -l`.
Counter: `rows.sh` next to this file. The per-file plan with every number is `plan.tsv`.

Lanes A (effect DSP: core, Ignitor node, Katalyst stage) and B (voices, envelopes, classic(),
samples, tails, baselines) are not audited here; overlaps into them are named only. The MASTER is
estimated only: the step 12 C3/C5 implementer owns the row-by-row work.

## Method

1. Every spec in scope was listed with rows and lines; row names were read for all of them, bodies
   for every file whose names suggested overlap.
2. Sprudel door rows were classified by name into FORM rows (the same door re-proved in another
   calling form: "sets X", "works as pattern extension", "works as string extension", "works in
   compiled code", "control pattern on existing pattern", "with continuous pattern", "reinterpret
   voice data as", "alias works", "can be used as PatternMapper") and the rest. The regex is
   `form.re`. 405 of 881 sprudel door rows are FORM rows, 95 more are `dsl interface` rows.
3. Clones were confirmed with `diff` after substituting the knob or type name (zero-line diffs:
   `LangNfattackSpec` vs `LangNfdecaySpec`; the five `KlangScriptSuper*Spec`; `KlangScriptRampSpec`
   vs `KlangScriptSawtoothSpec`).
4. A row was cut only when another named spec asserts the same thing with at least the same teeth.
   Where the other spec lacks a form (the string door, the standalone mapper), the cut is paired
   with a new table row that adds it (the proposed `LangDoorFormsSpec`).

## The one structural finding

The sprudel door surface is tested three times over:

- per knob, in one file per retired per-knob door (`LangNfattackSpec`, `LangLpqSpec`,
  `LangFmdecaySpec`, `LangDelayTimeSpec`, ...), each 5 to 9 FORM rows proving the same
  KSP-generated overloads;
- per door, in the compound door's own spec (`LangReverbSpec`, `LangPhaserSpec`, ...), again with
  the same FORM rows, plus a `dsl interface` row that already runs 6 to 8 forms over 17 cycles;
- per slot, both doors, in `LangFieldAccessorsSpec`, whose tables already cover ~120 slots for the
  mapper, the bare read, the control pattern, the gap, the tail-only guard and the bare reinterpret,
  on the Kotlin AND the script door.

`LangFieldAccessorsSpec` lacks only the string-receiver door and the standalone mapper door. One
new table spec (`LangDoorFormsSpec`, ~95 knob entries, 5 rows, ~560 lines) that runs
`dslInterfaceTests` per knob WITH a value assertion, plus the bare reinterpret per head, a
continuous-pattern sample and the cross-door mapper chain, replaces every FORM row. 45 sprudel files
then have nothing unique left (395 rows, 5138 lines); their few unique rows move into the parent
door spec (listed in the table's action column).

## Same behaviour at several layers (the traces)

**Reverb defaults (`REVERB_WET`, `REVERB_SIZE`).** Declared: `KatalystDefaultsSyncSpec` (constant
family, bare stage carries the touched constant). Door fill: `LangReverbSpec` (the blueprint,
`/dsl-design` section 4 names `fillReverbDefaults` as such) AND `LangKatalystParamSpec` "reverb(...)
writes its slots, and the call fills" AND `LangEffectsRoutingSpec` forms. Script door:
`KlangScriptKatalystDoorParitySpec` "an omitted door parameter is exactly what the bare stage
carries". Engine: `KatalystSlotResolverSpec` "size slot ... goes through normalizeSize" AND
`CylinderKatalystPipelineSpec` "updateFromVoice configures reverb parameters" (7.0 to 0.7, the same
law through the host). Master: `SendEffectDefaultsParitySpec` (C3). Keep: defaults-sync, the
blueprint, the resolver, the parity. Cut: the `LangKatalystParamSpec` reverb and delay rows (twins
of the blueprint), the routing forms, the host's per-stage `updateFromVoice` rows (fold into one
wiring row).

**The compound-door fill for body, vowel, compressor, phaser, duck.** Asserted in the door's own
spec (`LangBodySpec` floor row and "default wet" row, `LangVowelSpec` default-wet row,
`LangCompressorSpec` "leading params fill" and "named params fill", `LangPhaserSpec` "per-param
fills", `LangWetKnobSpec` "phaserFloor takes the default"), in `LangKatalystParamSpec` (the rule's
home: every door, the never-overwrite half, the katp-between-calls row, the duck name-knob rows),
in `LangFieldAccessorsSpec` fill cases, and rendered in `KatalystDoorFillRenderSpec`. Keep
`LangKatalystParamSpec` as the one home; drop the per-door fill rows. `KatalystDoorFillRenderSpec`:
keep body and duck (escape ledger rows 26 and 30, mutation-verified per its KDoc); the vowel and
compressor pairs (4 rows) are optional cuts, their slot fill is in `LangKatalystParamSpec` and their
resolve in `KatalystSlotResolverSpec`.

**Default q = 0.707.** `IgnitorDslSpec` (Bell and RawTap wire default == surface, typed == scalar
overloads), `LangDefaultQSpec` (all surfaces, the C1 tripwire; its "ignitor scalar doors: band()
and tap()" row repeats `IgnitorDslSpec`), `KlangScriptFilterDoorParitySpec` (scalar overloads
default like node overloads), `IgnitorDefaultsTest` (eqdemo, lane B). One row cut.

**The vowel table.** `CatalogueIndexSpec` (audio_bridge) pins every name to its index, the cross
product complete and collision-free, case-insensitivity, unknowns, the tie rule and one anchored
bank. `LangVowelComprehensiveSpec` repeats "resolves to a 5-band bank" for 88 names (weak, see
below) and carries 4 formant anchors that no other spec has. Keep the anchors (one row, or move
into `CatalogueIndexSpec`), the mixed valid/invalid sequence row and the step 3c parity row.
`LangBodySpec` "every catalogue material resolves to an 8-mode body" is the body twin of the same
duplication.

**The orbit lease.** Law: `VoiceLeaseSpec` (6 rows). Host wiring asserted in
`CylinderKatalystParamsSpec` (owner's state configures, owner change hands over; non-owner
ignored), `CylinderKatalystPipelineSpec` ("one lease owns ALL bus effects", "when the owner ends a
new voice takes over", plus two body lease rows), `CylinderCompressorSpec` (first-writer-wins,
owner ends), `CylinderFaderThroughZeroSpec` (non-owner keeps the orbit alive). The cylinder has ONE
lease for every stage; keep the `CylinderKatalystParamsSpec` pair, the body hand-off pair and the
fader row; cut the compressor pair and the two generic pipeline rows.

**Compressor on the cylinder.** `CylinderCompressorSpec` (class still named `OrbitCompressorSpec`)
"parameters are set correctly on first voice" == `CylinderKatalystPipelineSpec` "updateFromVoice
configures compressor" == `KatalystSlotResolverSpec` "ONE finite slot switches it on"; "instance
is reused" and "envelope state is preserved" == resolver "the instance is reused, so the envelope
follower survives". Keep the two takeover rows (cleared at once, glides out on a sounding orbit)
and one host row.

**Katalyst.classic() from both languages.** `KlangScriptKatalystDoorParitySpec` "Katalyst.classic()
== KatalystDsl.classic" and `LangKatalystSpec` "Katalyst.classic() is the historical chain, from
both languages". Small; left.

**Wire round trips.** `WireCodecRoundTripSpec` "ScheduledVoice round-trips a minimal VoiceData" and
`WorkletWireCodecRoundTripSpec` "minimal leaf voice survives" are the same codec path on the same
shape; the sprudel "each filter type + its envelope survives individually" row round-trips slot map
entries since phase 3 step 9 (no distinct codec path). Keep the sprudel fully-populated row (it is
the only one fed by `toVoiceData`) and the slots row.

## Door specs that test effect or engine behaviour (not the door)

- `LangVowelComprehensiveSpec` and `LangBodySpec` resolve the ENGINE's catalogue (`VowelBands`,
  `BodyMaterials`) instead of asserting the index the door writes.
- `LangDefaultQSpec` and `LangWetKnobSpec` assert Ignitor node defaults and the KlangScript Ignitor
  door from inside sprudel; their homes are `IgnitorDslSpec`, `KlangScriptEffectBuilderSpec`,
  `KlangScriptFilterDoorParitySpec`. `LangDefaultQSpec` stays as the declared cross-surface
  tripwire; `LangWetKnobSpec`'s Ignitor rows move.
- `KatalystPipelineSpec` "delay-only pipeline adds delayed signal", "reverb-only adds reverb",
  "compressor reduces loud signal": effect behaviour, lane A's Katalyst stage specs cover it.
- `KlangAudioRendererSpec` three "limiter compressor" rows: the house limiter's DSP (C3 / lane A).

## Large table-driven specs

- `LangVowelComprehensiveSpec`: 105 rows, one assertion (bank size 5) over 88 names plus 17 others.
  Representative subset: 3 rows. The TABLE is not the value here: it is a copy of the cross product
  `CatalogueIndexSpec` already asserts literally.
- `KlangScriptOscSlotTest`: 13 rows, one per slot (name and default). This IS a mapping table: keep
  every entry, as ONE row asserting the full literal map (13 to 2 rows, lines roughly halve).
- `IgnitorDslAnalogSpreadDefaultsSpec`: 7 one-line rows, one per oscillator: keep every entry, one
  row.
- `CylinderCleanupTest`: 7 `tryDeactivate` rows over one silence detector (threshold, left, right,
  negative, middle, end, above threshold): one table row with clues.
- `KlangAudioRendererSpec`: 9 `clip` rows and 4 `interleave` rows over one conversion: one table row
  each (boundaries kept as entries).
- JS-compat data (`JsCompatTestData`, 435 examples; `JsCompatTestSongs`, 15): a differential oracle
  against real Strudel, one function per example, GraalVM-gated. The table IS the value; pattern
  algebra, out of this lane; keep.
- `IgnitorDslWireCodecSpec` per-type rows (five Super types, four filters): the codec is generated
  per type, so each row is a distinct code path; keep.
- The five `KlangScriptSuper*Spec` (20 rows each): textual clones, but the five builders are five
  classes (`IgnitorBuilders.kt` lines 267 to 623), so keep every knob per type, as one table spec.

## Migration scaffolding that outlived its job

1. `KlangScriptKatalystDoorParitySpec` "the limiter's numbers are the Master DSL limiter's, until
   that DSL retires": goes in C5.
2. `LangWetKnobSpec` (KDoc: "C4.2 guard", pins renamed surfaces writing unchanged wire fields): the
   rename is done; rows 1, 2 and 4 repeat `LangFieldAccessorsSpec` and `LangKatalystParamSpec`.
   Keep the positional-order row and the wire-boundary floor row, move the three Ignitor rows.
3. The per-knob spec files named after doors retired 2026-09-07 (CLAUDE.md "Retired, do not
   restore or cite"): `LangNfattackSpec`, `LangNfdecaySpec`, `LangNfenvSpec`, `LangNfreleaseSpec`,
   `LangNfsustainSpec`, `LangNfadsrSpec`, `LangNresonanceSpec`, `LangNotchfSpec`, `LangLpqSpec`,
   `LangLpeSpec`, `LangLpadsrSpec`, `LangHpqSpec`, `LangHpeSpec`, `LangHpadsrSpec`, `LangBpqSpec`,
   `LangBpeSpec`, `LangBpadsrSpec`, `LangFmattackSpec`, `LangFmdecaySpec`, `LangFmsustainSpec`,
   `LangFmhSpec`, `LangPsustainSpec`, `LangVibratoModSpec`, `LangPanSpreadSpec`,
   `LangReverbSizeSpec`, `LangDelayFeedbackSpec`, `LangDelayTimeSpec`; and row labels that still say
   `nfadsr dsl interface`, `fmatt dsl interface`, `duckorbit dsl interface`, `detune dsl interface`,
   `lpe dsl interface`, `hpq dsl interface`, `attack dsl interface` (in `LangDynamicsSpec`).
4. `LangDynamicsSpec`, `LangEffectsRoutingSpec`, `LangSynthesisSpec` (the one FunSpec): pre-compound
   sweeps whose every row is a FORM row or a chain row repeated elsewhere.
5. `CylinderCompressorSpec`, class `OrbitCompressorSpec`: written for the per-cylinder compressor
   before it became a Katalyst stage; most rows now re-assert `KatalystSlotResolverSpec`.
6. `MutableVoiceDataGoldenSpec`: the Phase 0 fixture of the mutable-voicedata refactor (archived
   `docs/tasks-archive/2026-06/20260607-mutable-voicedata-optimization.md`; the KDoc still points at
   the deleted `docs/agent-tasks/` path). It still guards aliasing, a live risk, but its 2.7 MB
   golden has been regenerated in 18 commits, so every door change pays for it. Maintainer call:
   rebrand it as the wire regression guard (fix the KDoc) or replace it with targeted aliasing rows.
   Counted as kept.
7. Removal tripwires (review, not counted): "the retired forms fail loudly" (Katalyst and Master
   parity), "the retired curve and anchor slots are gone on the script door, loudly"
   (`LangPitchEnvelopeSpec`), "adsrCurves is gone" (two rows in `KlangScriptFilterDoorParitySpec` and
   `KlangScriptEffectBuilderSpec`). They stop an old spelling from silently meaning something else;
   drop them once no migrated song can carry the old spelling.
8. Cross-lane, name only: `DelayLineMigrationSpec` (lane A).

## Guards that stay (in this lane)

- `StdLibOscTest`, `StdLibNumberMethodsTest` (CLAUDE.md guardrail, the min/max crossing);
  `StructuralCycleSelectionSpec` (CLAUDE.md, outside this lane, untouched).
- `ClassicSlotParamsSpec`, the full-literal-map row (escape ledger row 70).
- `KatalystDefaultsSyncSpec`, the unset family (ledger row 26).
- `LangKatalystParamSpec`: the duck name-knob rows (ledger row 30), "a fill never stamps a constant
  over a katp written BETWEEN two calls" (ledger row 28, checklist 12), "a bare body(), vowel() or
  phaser() on a value that is not a number writes NOTHING".
- `LangFieldAccessorsSpec` tail-only and "wet heads" rows, `LangPitchEnvelopeSpec` tail-only and
  "an amount WITH a stage" (ledger row 61, the terms of the tail-only guard).
- `LangReverbSpec` and `LangDelaySpec` blueprint fill rows (`/dsl-design` section 4).
- `KatalystBodyNonFiniteWetSpec` (ledger row 36); `KatalystDoorFillRenderSpec` body and duck rows
  and their teeth rows; `KatalystClassicMatchesUntouchedVoiceSpec` material-only row (named by the
  fill render KDoc as the engine's second line of defence).
- `KlangScriptFilterDoorParitySpec` (named by `/dsl-design` section 3 as the pattern);
  `DslDocExamplesSpec` (`/dsl-design` checklist 10).
- `LangDefaultQSpec` (C1 cross-surface tripwire).
- `ChainSwapStateIdentitySpec` (the identity bullet of `docs/plans/effect-state-machines.md`: the
  state machine allocates nothing); `CylinderChainCrossfadeSpec` duck and send rows (ledger row 23).
- `CylinderKatalystPipelineSpec` phaser-floor forwarding (C4.2, kept inside the folded wiring row).
- `WireCodecRoundTripSpec` SLOT_UNSET row and decoded-classic-name row; `CatalogueIndexSpec` tie rule.
- `InlineDslRegistrarTest` (the per-playback cleanup rule).

## Weak-teeth rows (separate list)

1. `LangDynamicsSpec`: all 22 `dsl interface` rows assert only `events.shouldNotBeEmpty()`; a door
   that writes nothing is green.
2. `LangReverbSpec`, `LangReverbSizeSpec`, `LangDistortSpec`, `LangCrushSpec`, `LangCoarseSpec`:
   the `dsl interface` row asserts only non-empty, so the "script string" and "script apply" forms
   are never value-checked anywhere.
3. `LangVowelComprehensiveSpec`: 88 rows assert only `bands.size == 5`; every vowel mapped to the
   same bank stays green.
4. `LangCanonicalFilterNamesSpec` "reachable on all four surface forms": asserts `size == 1`, not
   the frequency.
5. `WorkletWireCodecRoundTripSpec` "each filter type + its envelope survives individually": since
   the filters became slots it exercises no codec path the fully-populated row does not.
6. `KlangAudioRendererSpec` "renderBlock with no voices produces all-zero output", "multiple silent
   renderBlock calls all produce zeros", "at various cursor positions produces consistent silent
   output": a renderer that always emits zeros passes all three.
7. `IgnitorRegistryTest` "registerDefaults compositions produce non-zero output" (any non-zero
   sample; also `IgnitorDefaultsTest`'s sg* rows, lane B).
8. `ClassicDoorRenderParitySpec`: its own KDoc says the doors-agree bit comparison cannot fail
   independently of the tree-equality row; the ENGAGEMENT half has the teeth. Documented, kept.

## Totals

| area | files | rows | lines |
|---|---|---|---|
| Sprudel door specs | 80 to 36 | 909 to 239 | 12138 to 5130 |
| KlangScript door parity and builders | 27 to 21 | 374 to 261 | 4384 to 3515 |
| Wire, registries, defaults, door fill | 20 to 20 | 193 to 179 | 4011 to 3832 |
| Hosts (cylinder, swap, Katalyst chain, warehouse, glide, dispatcher, scheduler) | 35 to 35 | 389 to 349 | 11076 to 10342 |
| **Lane C** | **162 to 112** | **1865 to 1028 (-45%)** | **31609 to 22819 (-28%)** |

Of the lane's cut, 21 rows and about 440 lines are MASTER items that C3/C5 own
(`KlangScriptMasterBuilderSpec` whole, the Katalyst limiter==Master row, two `WireCodecRoundTripSpec`
master rows, two `LazyReverbSpec` and one `EngineDisposalReturnSpec` master rows, three
`KlangAudioRendererSpec` limiter rows). Lane C's own cut: about 816 rows (44%) and 8350 lines (26%).

**Master estimate (for C3/C5, not counted above):** 12 master-named files (`audio_be/.../master/*`,
`MasterStageSpec`, `KlangScriptMasterBuilderSpec`, `LangMasterSpec`, `KlangOfflineRendererMasterTest`)
hold 103 rows and 2857 lines, plus about 9 master rows (~300 lines) inside other files. The
maintainer's keep list is 7 master-position rows; with their rigs about 10 rows and 500 to 600 lines.
Expected cut: about 95 rows and 2500 lines. `LangMasterSpec` is a row-for-row twin of
`LangKatalystSpec` (control carrier, keeps emitting, stamps events, dsl interface, script door,
denormalized name, no merge), so C5 loses nothing there.

## Per-file plan

### Sprudel door specs

| file | rows | lines | rows after | lines after | action |
|---|---|---|---|---|---|
| LangAccelerateSpec.kt | 7 | 93 | 0 | 0 | forms+dsli to table |
| LangAdsrCurveDefaultSpec.kt | 3 | 79 | 3 | 79 | keep |
| LangAdsrCurvesSpec.kt | 5 | 85 | 5 | 85 | keep |
| LangAdsrOnOffSpec.kt | 9 | 109 | 6 | 81 | 3 door-form rows to table |
| LangAdsrSpec.kt | 7 | 117 | 5 | 91 | compiled/mapper forms to table |
| LangAnalogSpec.kt | 7 | 97 | 1 | 35 | forms to table |
| LangBodySpec.kt | 13 | 133 | 8 | 91 | fill rows dup LangKatalystParamSpec, catalogue row dup CatalogueIndexSpec |
| LangBpadsrSpec.kt | 5 | 93 | 0 | 0 | clone; partial row into filter table |
| LangBpeSpec.kt | 6 | 88 | 0 | 0 | clone; env-slots row into filter table |
| LangBpfSpec.kt | 10 | 142 | 0 | 0 | 2 unique rows into filter table |
| LangBpqSpec.kt | 6 | 87 | 0 | 0 | clone |
| LangCanonicalFilterNamesSpec.kt | 3 | 55 | 2 | 45 | size-only row weak |
| LangCoarseSpec.kt | 9 | 112 | 0 | 0 | forms (weak dsli) |
| LangCompressorSpec.kt | 9 | 159 | 5 | 99 | 2 fill rows dup LangKatalystParamSpec |
| LangCrushSpec.kt | 9 | 112 | 0 | 0 | forms (weak dsli) |
| LangCylinderSpec.kt | 3 | 49 | 2 | 41 | keep alias |
| LangDefaultQSpec.kt | 6 | 106 | 5 | 93 | scalar band/tap row dup IgnitorDslSpec |
| LangDelayFeedbackSpec.kt | 5 | 63 | 0 | 0 | clone forms |
| LangDelaySpec.kt | 18 | 233 | 7 | 106 | blueprint fill rows kept |
| LangDelayTimeSpec.kt | 5 | 62 | 0 | 0 | clone forms |
| LangDistortSpec.kt | 24 | 288 | 6 | 91 | forms (weak dsli) |
| LangDuckingSpec.kt | 12 | 176 | 2 | 50 | 3 dsli (retired names) to table |
| LangDynamicsSpec.kt | 49 | 582 | 0 | 0 | 22 weak dsli, chains dup |
| LangEffectAliasesSpec.kt | 5 | 56 | 0 | 0 | o alias in FieldAccessors |
| LangEffectsRoutingSpec.kt | 40 | 353 | 0 | 0 | 36/40 forms; 4 chain rows to table |
| LangFeedbackCapSpec.kt | 3 | 72 | 2 | 56 | keep |
| LangFieldAccessorsSpec.kt | 30 | 1005 | 30 | 1005 | keep: the both-doors accessor/control/tail-only table |
| LangFilterCurvesSpec.kt | 11 | 163 | 11 | 163 | keep |
| LangFiltersSpec.kt | 22 | 241 | 0 | 0 | forms; unique rows into filter table |
| LangFmattackSpec.kt | 6 | 86 | 0 | 0 | clone |
| LangFmdecaySpec.kt | 6 | 86 | 0 | 0 | clone |
| LangFmhSpec.kt | 5 | 67 | 0 | 0 | clone |
| LangFmsustainSpec.kt | 6 | 86 | 0 | 0 | clone |
| LangGainSpec.kt | 10 | 149 | 0 | 0 | forms |
| LangHpadsrSpec.kt | 5 | 93 | 0 | 0 | clone |
| LangHpeSpec.kt | 6 | 88 | 0 | 0 | clone |
| LangHpfSpec.kt | 10 | 132 | 0 | 0 | 2 unique rows into filter table |
| LangHpqSpec.kt | 2 | 50 | 0 | 0 | clone |
| LangKatalystParamSpec.kt | 32 | 689 | 30 | 655 | keep: fill-rule home; its reverb and delay rows twin the blueprint rows in LangReverbSpec/LangDelaySpec |
| LangKatalystSpec.kt | 18 | 259 | 16 | 233 | fold 2 replace rows |
| LangLpadsrSpec.kt | 5 | 89 | 0 | 0 | clone |
| LangLpeSpec.kt | 6 | 88 | 0 | 0 | clone |
| LangLpfSpec.kt | 11 | 135 | 8 | 180 | becomes the filter-door table (lpf/hpf/bpf/notch unique rows) |
| LangLpqSpec.kt | 6 | 81 | 0 | 0 | clone |
| LangNfadsrSpec.kt | 5 | 93 | 0 | 0 | clone |
| LangNfattackSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNfdecaySpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNfenvSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNfreleaseSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNfsustainSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNotchfSpec.kt | 9 | 112 | 0 | 0 | forms |
| LangNresonanceSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangOnepoleSpec.kt | 14 | 177 | 4 | 68 | forms |
| LangOrbitSpec.kt | 8 | 135 | 0 | 0 | forms; orbit in FieldAccessors |
| LangOscparamSpec.kt | 8 | 130 | 4 | 78 | forms |
| LangPanSpec.kt | 10 | 137 | 0 | 0 | forms |
| LangPanSpreadSpec.kt | 6 | 88 | 0 | 0 | clone |
| LangPassesSpec.kt | 8 | 90 | 8 | 90 | keep |
| LangPenvCurvesSpec.kt | 7 | 95 | 7 | 95 | keep |
| LangPenvSpec.kt | 7 | 72 | 0 | 0 | forms; pamt alias in FieldAccessors |
| LangPhaserSpec.kt | 22 | 292 | 4 | 74 | forms; fill row dup LangKatalystParamSpec |
| LangPitchEnvelopeSpec.kt | 27 | 338 | 6 | 95 | forms; 4 tail-only rows to 1 table row |
| LangPitchParamNamesSpec.kt | 2 | 53 | 2 | 53 | keep |
| LangPregainSpec.kt | 8 | 117 | 7 | 106 | dsli to table |
| LangPsustainSpec.kt | 5 | 62 | 0 | 0 | wire row moves to LangPitchEnvelopeSpec |
| LangReverbSizeSpec.kt | 6 | 75 | 0 | 0 | clone forms (weak dsli) |
| LangReverbSpec.kt | 30 | 355 | 12 | 157 | blueprint fill rows kept |
| LangSndPluckSpec.kt | 9 | 138 | 5 | 88 | forms |
| LangSndSuperPluckSpec.kt | 4 | 72 | 3 | 60 | forms |
| LangSpreadSpec.kt | 6 | 88 | 0 | 0 | clone |
| LangSynthesisSpec.kt | 9 | 95 | 0 | 0 | FunSpec, fm via compile, all dup |
| LangTremoloCompoundSpec.kt | 10 | 139 | 4 | 71 | 5 positional rows to 1 |
| LangTremoloSpec.kt | 15 | 161 | 1 | 34 | forms |
| LangUnisonSpec.kt | 12 | 160 | 0 | 0 | forms |
| LangVelocitySpec.kt | 14 | 199 | 0 | 0 | forms |
| LangVibratoModSpec.kt | 6 | 81 | 0 | 0 | clone |
| LangVibratoSpec.kt | 11 | 130 | 0 | 0 | forms |
| LangVowelComprehensiveSpec.kt | 105 | 566 | 3 | 70 | size==5 rows dup CatalogueIndexSpec |
| LangVowelSpec.kt | 13 | 145 | 7 | 90 | forms; fill rows dup |
| LangWetKnobSpec.kt | 8 | 123 | 3 | 62 | C4.2 rename guard done |
| (new) LangDoorFormsSpec.kt | 0 | 0 | 5 | 560 | NEW: one table over ~95 knobs: eight forms write the slot (value asserted), bare call reinterprets, continuous pattern, chained mappers across doors |

### KlangScript door-parity and builder specs

| file | rows | lines | rows after | lines after | action |
|---|---|---|---|---|---|
| KlangScriptSuperSawSpec.kt | 20 | 149 | 20 | 210 | 5 textual clones -> 1 table spec over the 5 builders |
| KlangScriptSuperSineSpec.kt | 20 | 149 | 0 | 0 | clone |
| KlangScriptSuperSquareSpec.kt | 20 | 149 | 0 | 0 | clone |
| KlangScriptSuperTriSpec.kt | 20 | 149 | 0 | 0 | clone |
| KlangScriptSuperRampSpec.kt | 20 | 149 | 0 | 0 | clone |
| KlangScriptSawtoothSpec.kt | 9 | 72 | 9 | 85 | 2 clones -> 1 table |
| KlangScriptRampSpec.kt | 9 | 72 | 0 | 0 | clone |
| KlangScriptOscSlotTest.kt | 13 | 86 | 2 | 55 | mapping table as one literal-map row |
| KlangScriptKatalystDoorParitySpec.kt | 22 | 576 | 21 | 551 | limiter==Master row goes in C5 |
| KlangScriptMasterBuilderSpec.kt | 12 | 219 | 0 | 0 | C5 (Master DSL retires) |
| KlangScriptEffectBuilderSpec.kt | 18 | 188 | 18 | 188 | keep |
| KlangScriptFilterDoorParitySpec.kt | 12 | 275 | 12 | 275 | keep (named pattern) |
| KlangScriptEnvelopeDoorParitySpec.kt | 6 | 197 | 6 | 197 | keep |
| KlangScriptWaveshaperDoorParitySpec.kt | 10 | 162 | 10 | 162 | keep |
| KlangScriptClassicDoorParitySpec.kt | 5 | 96 | 5 | 96 | keep |
| KlangScriptPregainDoorParitySpec.kt | 4 | 78 | 4 | 78 | keep |
| KlangScriptFilterSlotOrderSpec.kt | 5 | 76 | 5 | 76 | keep |
| KlangScriptAnalogSurfaceSpec.kt | 4 | 120 | 4 | 120 | keep |
| KlangScriptSineSpec.kt | 16 | 126 | 16 | 126 | keep |
| KlangScriptSuperPluckSpec.kt | 16 | 125 | 16 | 125 | keep |
| KlangScriptPulzeSpec.kt | 11 | 80 | 11 | 80 | keep |
| KlangScriptNoiseFbmSpec.kt | 6 | 67 | 6 | 67 | keep |
| KlangScriptCrackleSpec.kt | 3 | 43 | 3 | 43 | keep |
| KlangScriptPhasePoolSelectionSpec.kt | 4 | 48 | 4 | 48 | keep |
| StdLibOscTest.kt | 72 | 596 | 72 | 596 | GUARD (CLAUDE.md) |
| StdLibNumberMethodsTest.kt | 13 | 273 | 13 | 273 | GUARD (CLAUDE.md) |
| StdLibNumberMethodsDoorParitySpec.kt | 4 | 64 | 4 | 64 | keep |

### Wire, registries, defaults, door-fill

| file | rows | lines | rows after | lines after | action |
|---|---|---|---|---|---|
| KatalystDefaultsSyncSpec.kt | 13 | 433 | 13 | 433 | GUARD (ledger 26) |
| CatalogueIndexSpec.kt | 18 | 296 | 19 | 310 | absorbs the vowel formant anchors |
| IgnitorDslSpec.kt | 14 | 152 | 14 | 152 | keep |
| IgnitorDslAnalogSpreadDefaultsSpec.kt | 7 | 52 | 1 | 40 | 7 one-line rows -> 1 table row |
| ClassicTailSpec.kt | 13 | 225 | 13 | 225 | keep |
| PregainSlotSpec.kt | 4 | 76 | 4 | 76 | keep |
| WireCodecRoundTripSpec.kt | 13 | 317 | 11 | 285 | MasterDsl + master-ref rows go in C5 |
| IgnitorDslWireCodecSpec.kt | 23 | 346 | 23 | 346 | keep (per generated type) |
| WorkletWireCodecRoundTripSpec.kt | 4 | 136 | 2 | 81 | minimal row dup; per-filter row weak since slots |
| MutableVoiceDataGoldenSpec.kt | 1 | 142 | 1 | 142 | keep (decision: rebrand or retire) |
| ClassicSlotParamsSpec.kt | 11 | 170 | 11 | 170 | GUARD (ledger 70) |
| ClassicDoorRenderParitySpec.kt | 2 | 188 | 2 | 188 | keep |
| ParamBagSpec.kt | 9 | 180 | 9 | 180 | keep |
| WireGainFoldSpec.kt | 9 | 96 | 9 | 96 | keep |
| InlineDslRegistrarTest.kt | 12 | 246 | 12 | 246 | keep |
| IgnitorRegistryTest.kt | 23 | 394 | 22 | 378 | compositions non-zero row dup IgnitorDefaultsTest (lane B) |
| KatalystRegistrySpec.kt | 4 | 67 | 4 | 67 | keep |
| KatalystDoorFillRenderSpec.kt | 9 | 248 | 5 | 170 | vowel + compressor render pairs: slot level covers (optional) |
| KatalystBodyNonFiniteWetSpec.kt | 3 | 90 | 3 | 90 | GUARD (ledger 36) |
| DslDocExamplesSpec.kt | 1 | 157 | 1 | 157 | GUARD (/dsl-design) |

### Hosts

| file | rows | lines | rows after | lines after | action |
|---|---|---|---|---|---|
| ChainSwapSpec.kt | 6 | 275 | 6 | 275 | keep |
| ChainSwapStateIdentitySpec.kt | 1 | 179 | 1 | 179 | keep (no-alloc guard) |
| CylinderChainCrossfadeSpec.kt | 30 | 1360 | 30 | 1360 | keep |
| CylinderChainSwapSpec.kt | 22 | 610 | 22 | 610 | keep |
| CylinderSwapRig.kt | 0 | 157 | 0 | 157 | helper |
| CylinderCleanupTest.kt | 17 | 372 | 11 | 300 | 7 tryDeactivate rows -> 1 table |
| CylindersCleanupTest.kt | 8 | 245 | 6 | 190 | 3 not-mixed rows -> 1 |
| CylinderCompressorSpec.kt | 10 | 214 | 4 | 101 | dup KatalystSlotResolverSpec + lease rows |
| CylinderFaderThroughZeroSpec.kt | 3 | 236 | 3 | 236 | keep |
| CylinderKatalystParamsSpec.kt | 16 | 418 | 16 | 418 | keep |
| CylinderKatalystPipelineSpec.kt | 22 | 529 | 15 | 369 | 6 updateFromVoice -> 1; 2 lease rows dup |
| KatalystChainRequestSpec.kt | 4 | 169 | 4 | 169 | keep |
| KatalystSlotResolverSpec.kt | 39 | 739 | 39 | 739 | keep |
| KatalystChainBuilderSpec.kt | 11 | 284 | 11 | 284 | keep |
| KatalystPipelineSpec.kt | 6 | 176 | 3 | 101 | 3 effect-behaviour rows (lane A) |
| KatalystClassicGainStageSpec.kt | 6 | 229 | 6 | 229 | keep |
| KatalystClassicMatchesUntouchedVoiceSpec.kt | 6 | 229 | 6 | 229 | keep |
| KatalystClassicPipelineOrderSpec.kt | 7 | 218 | 7 | 218 | keep |
| KatalystInsertFeedSpec.kt | 4 | 279 | 4 | 279 | keep |
| VoiceLeaseSpec.kt | 6 | 67 | 6 | 67 | keep (lease core) |
| CylinderShelfSpec.kt | 9 | 363 | 9 | 363 | keep |
| EngineDisposalReturnSpec.kt | 5 | 189 | 4 | 156 | master row -> C3 |
| LazyReverbSpec.kt | 14 | 333 | 12 | 289 | 2 master rows -> C3 |
| LazyRingSpec.kt | 18 | 427 | 18 | 427 | keep |
| ResourceWarehouseSpec.kt | 32 | 513 | 32 | 513 | keep |
| SharedScratchSpec.kt | 7 | 209 | 7 | 209 | keep |
| WarehouseStatsSpec.kt | 4 | 166 | 4 | 166 | keep |
| KnobGlideSpec.kt | 11 | 375 | 11 | 375 | keep (core) |
| PlaybackEngineDispatcherTest.kt | 11 | 220 | 11 | 220 | keep |
| PlaybackEngineDispatcherDiagnosticsTest.kt | 7 | 141 | 7 | 141 | keep |
| KlangAudioRendererSpec.kt | 21 | 412 | 8 | 230 | 9 clip -> 1 table, 4 interleave -> 2, 3 limiter -> C3, 3 silent rows -> 1 |
| SchedulerStartupSpec.kt | 4 | 163 | 4 | 163 | keep |
| LongRunningTimelineSpec.kt | 3 | 106 | 3 | 106 | keep |
| VoiceSchedulerCullingSpec.kt | 8 | 225 | 8 | 225 | keep |
| VoiceSchedulerSoloCutSpec.kt | 11 | 249 | 11 | 249 | keep |



## Appendix: the per-file table

### Sprudel door specs

| file | rows | lines | rows after | lines after | action |
|---|---|---|---|---|---|
| LangAccelerateSpec.kt | 7 | 93 | 0 | 0 | forms+dsli to table |
| LangAdsrCurveDefaultSpec.kt | 3 | 79 | 3 | 79 | keep |
| LangAdsrCurvesSpec.kt | 5 | 85 | 5 | 85 | keep |
| LangAdsrOnOffSpec.kt | 9 | 109 | 6 | 81 | 3 door-form rows to table |
| LangAdsrSpec.kt | 7 | 117 | 5 | 91 | compiled/mapper forms to table |
| LangAnalogSpec.kt | 7 | 97 | 1 | 35 | forms to table |
| LangBodySpec.kt | 13 | 133 | 8 | 91 | fill rows dup LangKatalystParamSpec, catalogue row dup CatalogueIndexSpec |
| LangBpadsrSpec.kt | 5 | 93 | 0 | 0 | clone; partial row into filter table |
| LangBpeSpec.kt | 6 | 88 | 0 | 0 | clone; env-slots row into filter table |
| LangBpfSpec.kt | 10 | 142 | 0 | 0 | 2 unique rows into filter table |
| LangBpqSpec.kt | 6 | 87 | 0 | 0 | clone |
| LangCanonicalFilterNamesSpec.kt | 3 | 55 | 2 | 45 | size-only row weak |
| LangCoarseSpec.kt | 9 | 112 | 0 | 0 | forms (weak dsli) |
| LangCompressorSpec.kt | 9 | 159 | 5 | 99 | 2 fill rows dup LangKatalystParamSpec |
| LangCrushSpec.kt | 9 | 112 | 0 | 0 | forms (weak dsli) |
| LangCylinderSpec.kt | 3 | 49 | 2 | 41 | keep alias |
| LangDefaultQSpec.kt | 6 | 106 | 5 | 93 | scalar band/tap row dup IgnitorDslSpec |
| LangDelayFeedbackSpec.kt | 5 | 63 | 0 | 0 | clone forms |
| LangDelaySpec.kt | 18 | 233 | 7 | 106 | blueprint fill rows kept |
| LangDelayTimeSpec.kt | 5 | 62 | 0 | 0 | clone forms |
| LangDistortSpec.kt | 24 | 288 | 6 | 91 | forms (weak dsli) |
| LangDuckingSpec.kt | 12 | 176 | 2 | 50 | 3 dsli (retired names) to table |
| LangDynamicsSpec.kt | 49 | 582 | 0 | 0 | 22 weak dsli, chains dup |
| LangEffectAliasesSpec.kt | 5 | 56 | 0 | 0 | o alias in FieldAccessors |
| LangEffectsRoutingSpec.kt | 40 | 353 | 0 | 0 | 36/40 forms; 4 chain rows to table |
| LangFeedbackCapSpec.kt | 3 | 72 | 2 | 56 | keep |
| LangFieldAccessorsSpec.kt | 30 | 1005 | 30 | 1005 | keep: the both-doors accessor/control/tail-only table |
| LangFilterCurvesSpec.kt | 11 | 163 | 11 | 163 | keep |
| LangFiltersSpec.kt | 22 | 241 | 0 | 0 | forms; unique rows into filter table |
| LangFmattackSpec.kt | 6 | 86 | 0 | 0 | clone |
| LangFmdecaySpec.kt | 6 | 86 | 0 | 0 | clone |
| LangFmhSpec.kt | 5 | 67 | 0 | 0 | clone |
| LangFmsustainSpec.kt | 6 | 86 | 0 | 0 | clone |
| LangGainSpec.kt | 10 | 149 | 0 | 0 | forms |
| LangHpadsrSpec.kt | 5 | 93 | 0 | 0 | clone |
| LangHpeSpec.kt | 6 | 88 | 0 | 0 | clone |
| LangHpfSpec.kt | 10 | 132 | 0 | 0 | 2 unique rows into filter table |
| LangHpqSpec.kt | 2 | 50 | 0 | 0 | clone |
| LangKatalystParamSpec.kt | 32 | 689 | 30 | 655 | keep: fill-rule home; its reverb and delay rows twin the blueprint rows in LangReverbSpec/LangDelaySpec |
| LangKatalystSpec.kt | 18 | 259 | 16 | 233 | fold 2 replace rows |
| LangLpadsrSpec.kt | 5 | 89 | 0 | 0 | clone |
| LangLpeSpec.kt | 6 | 88 | 0 | 0 | clone |
| LangLpfSpec.kt | 11 | 135 | 8 | 180 | becomes the filter-door table (lpf/hpf/bpf/notch unique rows) |
| LangLpqSpec.kt | 6 | 81 | 0 | 0 | clone |
| LangNfadsrSpec.kt | 5 | 93 | 0 | 0 | clone |
| LangNfattackSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNfdecaySpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNfenvSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNfreleaseSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNfsustainSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangNotchfSpec.kt | 9 | 112 | 0 | 0 | forms |
| LangNresonanceSpec.kt | 5 | 72 | 0 | 0 | clone |
| LangOnepoleSpec.kt | 14 | 177 | 4 | 68 | forms |
| LangOrbitSpec.kt | 8 | 135 | 0 | 0 | forms; orbit in FieldAccessors |
| LangOscparamSpec.kt | 8 | 130 | 4 | 78 | forms |
| LangPanSpec.kt | 10 | 137 | 0 | 0 | forms |
| LangPanSpreadSpec.kt | 6 | 88 | 0 | 0 | clone |
| LangPassesSpec.kt | 8 | 90 | 8 | 90 | keep |
| LangPenvCurvesSpec.kt | 7 | 95 | 7 | 95 | keep |
| LangPenvSpec.kt | 7 | 72 | 0 | 0 | forms; pamt alias in FieldAccessors |
| LangPhaserSpec.kt | 22 | 292 | 4 | 74 | forms; fill row dup LangKatalystParamSpec |
| LangPitchEnvelopeSpec.kt | 27 | 338 | 6 | 95 | forms; 4 tail-only rows to 1 table row |
| LangPitchParamNamesSpec.kt | 2 | 53 | 2 | 53 | keep |
| LangPregainSpec.kt | 8 | 117 | 7 | 106 | dsli to table |
| LangPsustainSpec.kt | 5 | 62 | 0 | 0 | wire row moves to LangPitchEnvelopeSpec |
| LangReverbSizeSpec.kt | 6 | 75 | 0 | 0 | clone forms (weak dsli) |
| LangReverbSpec.kt | 30 | 355 | 12 | 157 | blueprint fill rows kept |
| LangSndPluckSpec.kt | 9 | 138 | 5 | 88 | forms |
| LangSndSuperPluckSpec.kt | 4 | 72 | 3 | 60 | forms |
| LangSpreadSpec.kt | 6 | 88 | 0 | 0 | clone |
| LangSynthesisSpec.kt | 9 | 95 | 0 | 0 | FunSpec, fm via compile, all dup |
| LangTremoloCompoundSpec.kt | 10 | 139 | 4 | 71 | 5 positional rows to 1 |
| LangTremoloSpec.kt | 15 | 161 | 1 | 34 | forms |
| LangUnisonSpec.kt | 12 | 160 | 0 | 0 | forms |
| LangVelocitySpec.kt | 14 | 199 | 0 | 0 | forms |
| LangVibratoModSpec.kt | 6 | 81 | 0 | 0 | clone |
| LangVibratoSpec.kt | 11 | 130 | 0 | 0 | forms |
| LangVowelComprehensiveSpec.kt | 105 | 566 | 3 | 70 | size==5 rows dup CatalogueIndexSpec |
| LangVowelSpec.kt | 13 | 145 | 7 | 90 | forms; fill rows dup |
| LangWetKnobSpec.kt | 8 | 123 | 3 | 62 | C4.2 rename guard done |
| (new) LangDoorFormsSpec.kt | 0 | 0 | 5 | 560 | NEW: one table over ~95 knobs: eight forms write the slot (value asserted), bare call reinterprets, continuous pattern, chained mappers across doors |

### KlangScript door-parity and builder specs

| file | rows | lines | rows after | lines after | action |
|---|---|---|---|---|---|
| KlangScriptSuperSawSpec.kt | 20 | 149 | 20 | 210 | 5 textual clones -> 1 table spec over the 5 builders |
| KlangScriptSuperSineSpec.kt | 20 | 149 | 0 | 0 | clone |
| KlangScriptSuperSquareSpec.kt | 20 | 149 | 0 | 0 | clone |
| KlangScriptSuperTriSpec.kt | 20 | 149 | 0 | 0 | clone |
| KlangScriptSuperRampSpec.kt | 20 | 149 | 0 | 0 | clone |
| KlangScriptSawtoothSpec.kt | 9 | 72 | 9 | 85 | 2 clones -> 1 table |
| KlangScriptRampSpec.kt | 9 | 72 | 0 | 0 | clone |
| KlangScriptOscSlotTest.kt | 13 | 86 | 2 | 55 | mapping table as one literal-map row |
| KlangScriptKatalystDoorParitySpec.kt | 22 | 576 | 21 | 551 | limiter==Master row goes in C5 |
| KlangScriptMasterBuilderSpec.kt | 12 | 219 | 0 | 0 | C5 (Master DSL retires) |
| KlangScriptEffectBuilderSpec.kt | 18 | 188 | 18 | 188 | keep |
| KlangScriptFilterDoorParitySpec.kt | 12 | 275 | 12 | 275 | keep (named pattern) |
| KlangScriptEnvelopeDoorParitySpec.kt | 6 | 197 | 6 | 197 | keep |
| KlangScriptWaveshaperDoorParitySpec.kt | 10 | 162 | 10 | 162 | keep |
| KlangScriptClassicDoorParitySpec.kt | 5 | 96 | 5 | 96 | keep |
| KlangScriptPregainDoorParitySpec.kt | 4 | 78 | 4 | 78 | keep |
| KlangScriptFilterSlotOrderSpec.kt | 5 | 76 | 5 | 76 | keep |
| KlangScriptAnalogSurfaceSpec.kt | 4 | 120 | 4 | 120 | keep |
| KlangScriptSineSpec.kt | 16 | 126 | 16 | 126 | keep |
| KlangScriptSuperPluckSpec.kt | 16 | 125 | 16 | 125 | keep |
| KlangScriptPulzeSpec.kt | 11 | 80 | 11 | 80 | keep |
| KlangScriptNoiseFbmSpec.kt | 6 | 67 | 6 | 67 | keep |
| KlangScriptCrackleSpec.kt | 3 | 43 | 3 | 43 | keep |
| KlangScriptPhasePoolSelectionSpec.kt | 4 | 48 | 4 | 48 | keep |
| StdLibOscTest.kt | 72 | 596 | 72 | 596 | GUARD (CLAUDE.md) |
| StdLibNumberMethodsTest.kt | 13 | 273 | 13 | 273 | GUARD (CLAUDE.md) |
| StdLibNumberMethodsDoorParitySpec.kt | 4 | 64 | 4 | 64 | keep |

### Wire, registries, defaults, door-fill

| file | rows | lines | rows after | lines after | action |
|---|---|---|---|---|---|
| KatalystDefaultsSyncSpec.kt | 13 | 433 | 13 | 433 | GUARD (ledger 26) |
| CatalogueIndexSpec.kt | 18 | 296 | 19 | 310 | absorbs the vowel formant anchors |
| IgnitorDslSpec.kt | 14 | 152 | 14 | 152 | keep |
| IgnitorDslAnalogSpreadDefaultsSpec.kt | 7 | 52 | 1 | 40 | 7 one-line rows -> 1 table row |
| ClassicTailSpec.kt | 13 | 225 | 13 | 225 | keep |
| PregainSlotSpec.kt | 4 | 76 | 4 | 76 | keep |
| WireCodecRoundTripSpec.kt | 13 | 317 | 11 | 285 | MasterDsl + master-ref rows go in C5 |
| IgnitorDslWireCodecSpec.kt | 23 | 346 | 23 | 346 | keep (per generated type) |
| WorkletWireCodecRoundTripSpec.kt | 4 | 136 | 2 | 81 | minimal row dup; per-filter row weak since slots |
| MutableVoiceDataGoldenSpec.kt | 1 | 142 | 1 | 142 | keep (decision: rebrand or retire) |
| ClassicSlotParamsSpec.kt | 11 | 170 | 11 | 170 | GUARD (ledger 70) |
| ClassicDoorRenderParitySpec.kt | 2 | 188 | 2 | 188 | keep |
| ParamBagSpec.kt | 9 | 180 | 9 | 180 | keep |
| WireGainFoldSpec.kt | 9 | 96 | 9 | 96 | keep |
| InlineDslRegistrarTest.kt | 12 | 246 | 12 | 246 | keep |
| IgnitorRegistryTest.kt | 23 | 394 | 22 | 378 | compositions non-zero row dup IgnitorDefaultsTest (lane B) |
| KatalystRegistrySpec.kt | 4 | 67 | 4 | 67 | keep |
| KatalystDoorFillRenderSpec.kt | 9 | 248 | 5 | 170 | vowel + compressor render pairs: slot level covers (optional) |
| KatalystBodyNonFiniteWetSpec.kt | 3 | 90 | 3 | 90 | GUARD (ledger 36) |
| DslDocExamplesSpec.kt | 1 | 157 | 1 | 157 | GUARD (/dsl-design) |

### Hosts

| file | rows | lines | rows after | lines after | action |
|---|---|---|---|---|---|
| ChainSwapSpec.kt | 6 | 275 | 6 | 275 | keep |
| ChainSwapStateIdentitySpec.kt | 1 | 179 | 1 | 179 | keep (no-alloc guard) |
| CylinderChainCrossfadeSpec.kt | 30 | 1360 | 30 | 1360 | keep |
| CylinderChainSwapSpec.kt | 22 | 610 | 22 | 610 | keep |
| CylinderSwapRig.kt | 0 | 157 | 0 | 157 | helper |
| CylinderCleanupTest.kt | 17 | 372 | 11 | 300 | 7 tryDeactivate rows -> 1 table |
| CylindersCleanupTest.kt | 8 | 245 | 6 | 190 | 3 not-mixed rows -> 1 |
| CylinderCompressorSpec.kt | 10 | 214 | 4 | 101 | dup KatalystSlotResolverSpec + lease rows |
| CylinderFaderThroughZeroSpec.kt | 3 | 236 | 3 | 236 | keep |
| CylinderKatalystParamsSpec.kt | 16 | 418 | 16 | 418 | keep |
| CylinderKatalystPipelineSpec.kt | 22 | 529 | 15 | 369 | 6 updateFromVoice -> 1; 2 lease rows dup |
| KatalystChainRequestSpec.kt | 4 | 169 | 4 | 169 | keep |
| KatalystSlotResolverSpec.kt | 39 | 739 | 39 | 739 | keep |
| KatalystChainBuilderSpec.kt | 11 | 284 | 11 | 284 | keep |
| KatalystPipelineSpec.kt | 6 | 176 | 3 | 101 | 3 effect-behaviour rows (lane A) |
| KatalystClassicGainStageSpec.kt | 6 | 229 | 6 | 229 | keep |
| KatalystClassicMatchesUntouchedVoiceSpec.kt | 6 | 229 | 6 | 229 | keep |
| KatalystClassicPipelineOrderSpec.kt | 7 | 218 | 7 | 218 | keep |
| KatalystInsertFeedSpec.kt | 4 | 279 | 4 | 279 | keep |
| VoiceLeaseSpec.kt | 6 | 67 | 6 | 67 | keep (lease core) |
| CylinderShelfSpec.kt | 9 | 363 | 9 | 363 | keep |
| EngineDisposalReturnSpec.kt | 5 | 189 | 4 | 156 | master row -> C3 |
| LazyReverbSpec.kt | 14 | 333 | 12 | 289 | 2 master rows -> C3 |
| LazyRingSpec.kt | 18 | 427 | 18 | 427 | keep |
| ResourceWarehouseSpec.kt | 32 | 513 | 32 | 513 | keep |
| SharedScratchSpec.kt | 7 | 209 | 7 | 209 | keep |
| WarehouseStatsSpec.kt | 4 | 166 | 4 | 166 | keep |
| KnobGlideSpec.kt | 11 | 375 | 11 | 375 | keep (core) |
| PlaybackEngineDispatcherTest.kt | 11 | 220 | 11 | 220 | keep |
| PlaybackEngineDispatcherDiagnosticsTest.kt | 7 | 141 | 7 | 141 | keep |
| KlangAudioRendererSpec.kt | 21 | 412 | 8 | 230 | 9 clip -> 1 table, 4 interleave -> 2, 3 limiter -> C3, 3 silent rows -> 1 |
| SchedulerStartupSpec.kt | 4 | 163 | 4 | 163 | keep |
| LongRunningTimelineSpec.kt | 3 | 106 | 3 | 106 | keep |
| VoiceSchedulerCullingSpec.kt | 8 | 225 | 8 | 225 | keep |
| VoiceSchedulerSoloCutSpec.kt | 11 | 249 | 11 | 249 | keep |

