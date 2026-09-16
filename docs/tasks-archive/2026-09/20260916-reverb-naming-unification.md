# Reverb naming unification: one word, no prefixes, one knob per concept

> Archived 2026-09-16. Status: **COMPLETE 2026-09-16.** S1 to S3 landed as one change set (the wire rename crosses both
> doors, so splitting it left an uncompilable middle). Decisions taken by the maintainer on 2026-09-16 (table below).
> Review: round 1 clean (coding + audio reviewer, 0 CRITICAL/MAJOR); 6 MINORs, 5 applied as one batch (two KDocs that
> described the reverted soft-cap or a stale field count, a wrong slot-order example in `sprudel/MEMORY.md`,
> `DEFAULT_DAMP` made private, a threshold row `0.1 to true` in `MasterOrbitReverbParitySpec`), 1 rejected (the
> Strudel-syntax `room` in the skipped `JsCompatTestData` fixture is deliberate). Mutation-checked, each red then
> restored: master drops `lowpass`; orbit on-threshold moved; orbit gate `>=` to `>`; master gate `<` to `<=`; orbit
> keeps a non-finite `lowpass`; orbit gate without `isFinite`; `reset` and `restoreDefaults` keep `lowpass`; wire
> `lowpass` default non-null; sprudel door accepts `fade`, names its object `room`, swaps `size`/`lowpass`; master
> builder regains `damp`.
> Priority: **SHOULD.** DSL hygiene under the standing rules "one word per concept" and "parameter parity"
> (`/dsl-design` §4, §5). Resolves three open bullets of `master-dsl-followups.md` §1.
> **No backward compatibility.** The replaced surface is removed, not deprecated; every caller in the repo is migrated
> in the same change set. No song changes its sound.

## Why

The reverb is the one effect whose vocabulary drifted across its two doors. The maintainer spotted it in
`DerSchmetterling.kt`:

```javascript
// sprudel, orbit reverb
s("bd sd").room(wet = 0.3, size = 4, fade = 0.5, lowpass = 3000, dim = 2000)
// master reverb
m.reverb(r => r.wet(0.2).damp(0.8).roomSize(7).roomLp(3500))
```

| Concept                  | sprudel `room(...)`   | master `reverb(r => ...)` | engine `Reverb`             |
|--------------------------|-----------------------|---------------------------|-----------------------------|
| send                     | `wet`                 | `wet`                     | caller side                 |
| tail length, about 0..10 | `size`                | `roomSize`                | `roomSize` (normalized 0..1) |
| tail override, 0..1      | `fade`                | `roomFade`                | `roomFade`                  |
| damping, Hz              | `lowpass`             | `roomLp`                  | `roomLp`, overrides `damp`  |
| damping, 0..1            | none                  | `damp`                    | `damp`                      |
| "damping frequency"      | `dim`, never read     | none                      | `roomDim`, never read       |

Four problems in one table:

1. **Two words for the concept.** Sprudel says `room`; the master, the wire name, the engine class, the Katalyst
   effect, the editor widgets (`SprudelReverbEditor`) and the docs say reverb. `room` is also the only effect door
   named after a parameter instead of the effect (`delay`, `phaser`, `vowel`, `compressor`, ...).
2. **Prefixes inside a builder.** The 2026-09-07 slot migration gave sprudel `size`, `fade`, `lowpass`; the master
   reverb builder kept `roomSize`, `roomFade`, `roomLp`. The master delay builder already moved (`time`, `feedback`,
   `cap`), so the reverb is the last one behind. Inside `reverb(r => ...)` the prefix repeats what the lambda already
   says.
3. **`fade` is `size` again, on another scale.** See "Facts" below. It overrides `size` completely, and the trap is
   live: `DerSchmetterling.kt:416` in the maintainer's working copy reads `room(wet = 0.40, size = 7.0, fade = 0.5)`,
   where the 7 is ignored and the orbit runs at size 5.
4. **Two damping knobs, one overriding the other, plus a dead third.** `damp` (0..1) is master-only, `lowpass` (Hz)
   overrides it, and `dim` is stored end to end but never read. Both shipped uses of master `damp` are inert today:
   `StrangerThings` sets `damp(0.5)`, the default; `DerSchmetterling` sets `damp(0.8)` next to `roomLp(3500)`, which
   overrides it.

## Decisions (maintainer, 2026-09-16)

| #  | Decision |
|----|----------|
| D1 | **The word is `reverb`** on every surface: sprudel door and accessor object, master builder, wire, flat carriers, engine. `room` is retired and guarded. |
| D2 | **No `room*` prefixes** anywhere a scope already names the concept (builder lambda, named slot, nested wire type, engine class). Flat carriers keep the concept word as a namespace (D6). |
| D3 | **`fade` is removed** on both doors and in the engine. Migration: `fade = x` becomes `size = 10x`. |
| D4 | **`damp` is removed** from the master; `lowpass` (Hz) is the one damping knob on both doors. Sprudel's dead `dim` is removed with it, end to end. |
| D5 | **The size ceiling stays at 10**, recorded as a deliberate bound with a corrected explanation (see "Facts"). |
| D6 | Damping knob spelling: **`lowpass`**, not `lp`. It is already the sprudel slot word and the Osc filter door word; `lp` would be a third spelling. (Proposed in session, not objected to.) |

## Facts the decisions rest on

### What `fade` actually did

The engine runs the comb feedback at `(roomFade ?: roomSize) * 0.28 + 0.7`, where `roomSize` is the authored size
divided by 10 (`Reverb.normalizeRoomSize`) and `roomFade` is taken as-is, bounded to 0..1. So:

- `fade = 0.5` and `size = 5` give the same feedback, bit for bit.
- 0 is the shortest tail (about 0.7 s), 1 the longest (about 12.5 s). A higher `fade` is a longer tail, a SLOWER
  fade: the name reads backwards. It is inherited from the old `room("a:b:c")` colon packing.
- The only behaviour `size` cannot express: `fade = 0` switched the reverb ON, while a `size` below 0.1 (normalized
  `MIN_ACTIVE_ROOM_SIZE = 0.01`) switches the orbit reverb OFF. The tail difference between normalized 0 and 0.01 is
  a feedback of 0.7000 against 0.7028, inaudible. No shipped song relies on it.

### The size ceiling

- Feedback at authored size 10 is **0.98, not 1.0**. Unity sits at about size 10.71.
- 10 to 10.71 is real, if steep, sound: about 25 s of tail at 10.36, about 50 s at 10.54, approaching a freeze.
- Above 10.71 the combs grow without bound to Inf/NaN (measured; a soft-cap experiment produced pure DC, see
  `Reverb.normalizeSize` KDoc). On the master that rails every playback until a reload.
- Decision D5 keeps the bound at 10, which is canonical Freeverb's top (Jezar's 0.70..0.98 range), and which the
  drain math (`drainSamplesUntilSilent`) and `TailCeiling` proofs are written for. The KDoc that says "past 10 the
  feedback exceeds unity" is wrong and gets corrected. `/dsl-design` §6 says "reverb feedback above 1.0 ... is a
  creative choice"; true for the delay (which has `cap`), false for the reverb. Corrected in the same change.
- The floor at 0 stays too. It is also a bound inside the stable range (negative sizes would give tails shorter than
  0.7 s), but `size` doubles as the orbit's on switch. Out of scope, recorded below.

### `damp` is expressible through `lowpass`

`Reverb.process` maps `lowpass` to damping as `1 - (hz / nyquist)`, bounded to 0..1, which spans the whole `damp`
range. Nothing is lost. With `lowpass` unset the engine keeps its fixed default damping of 0.5.

## Target surface

| Layer | Today | After |
|-------|-------|-------|
| sprudel door | `room(wet, size, fade, lowpass, dim)` on `SprudelPattern`, `String`, `PatternMapperFn` | `reverb(wet, size, lowpass)` |
| sprudel accessor object | `@KlangScript.Object("room") object room`, `room.wet/.size/.fade/.lowpass/.dim` | `object reverb`, `reverb.wet`, `reverb.size`, `reverb.lowpass` |
| master builder | `MasterReverbBuilder.wet/roomSize/damp/roomFade/roomLp` | `wet`, `size`, `lowpass` |
| wire, master | `MasterStageDsl.Reverb(wet, roomSize, damp, roomFade, roomLp)`, `DEFAULT_ROOM_SIZE` | `MasterStageDsl.Reverb(wet, size, lowpass)`, `DEFAULT_SIZE` |
| wire, voice (flat) | `VoiceData.room, roomSize, roomFade, roomLp, roomDim, iResponse` | `reverb, reverbSize, reverbLowpass, iResponse` |
| sprudel carrier (group) | `SprudelVoiceData.reverb: SvdReverb?`, `SvdReverb(room, roomSize, roomFade, roomLp, roomDim, iResponse)` | `SprudelVoiceData.reverbFx: SvdReverb?`, `SvdReverb(reverb, reverbSize, reverbLowpass, iResponse)` |
| sprudel carrier (flat accessors) | `room, roomSize, roomFade, roomLp, roomDim, iResponse` | `reverb, reverbSize, reverbLowpass, iResponse` |
| engine voice | `Voice.Reverb(room, roomSize, roomFade, roomLp, roomDim, iResponse)` | `Voice.Reverb(amount, size, lowpass, iResponse)` |
| engine DSP | `Reverb.roomSize, damp, roomFade, roomLp, roomDim, iResponse`; `normalizeRoomSize`, `AUTHORED_ROOM_SIZE_SCALE` | `Reverb.size, lowpass, iResponse`; damping default a private constant; `normalizeSize`, `AUTHORED_SIZE_SCALE` |
| engine orbit effect | `KatalystReverbEffect.configure(roomSize, roomFade, roomLp, roomDim, iResponse)`, `MIN_ACTIVE_ROOM_SIZE` | `configure(size, lowpass, iResponse)`, `MIN_ACTIVE_SIZE` |
| editor tools | `SprudelRoomSizeEditor`, `SprudelRoomSizeSequenceEditor`; reverb editor has fade and dim fields | `SprudelReverbSizeEditor`, `SprudelReverbSizeSequenceEditor`; reverb editor edits wet, size, lowpass |

**Why the flat carriers look like this (deviation from the in-session table, recorded).** They hold every effect side by side, so the concept word is the namespace.
They mirror the delay exactly: `delay`, `delayTime`, `delayFeedback`, `delayCap` on the flat carriers, `delayFx` as
the sprudel group property (the flat `delay` accessor needs the plain name), `Voice.Delay(amount, time, ...)` in the
engine. The in-session table proposed `reverbWet`; matching the delay precedent wins, so a reader who knows one knows
the other.

**Positional order changes meaning at the third argument.** `room(wet, size, fade, ...)` becomes
`reverb(wet, size, lowpass)`. A migrated three-argument positional call would silently turn a fade into a 0.1 Hz
lowpass. The first check (2026-09-16) truncated its output and missed five of them; the full sweep found them in
`Tetris`, `TetrisRemix`, `Sakura`, `FrozenSongs` and `SongBenchmarkCases`, all converted by the rule below. New
specs pin the order (`LangReverbSpec`).

## Migration rules (sound-preserving)

| Old | New | Identical? |
|-----|-----|------------|
| `.room(...)`, `room(...)`, `room.x` | `.reverb(...)`, `reverb(...)`, `reverb.x` | yes |
| `room(fade = x)` | `reverb(size = 10x)`; if the call also sets `size`, drop that `size` (fade won) | musically yes; bit-exact for every value migrated here (not for every double: 0.11 comes back 1 ULP off), except `x < 0.01` (on-switch, above) |
| `room(dim = x)` | removed | yes, never read |
| `r.roomSize(x)` | `r.size(x)` | yes |
| `r.roomLp(x)` | `r.lowpass(x)` | yes |
| `r.roomFade(x)` | `r.size(10x)` | yes (no shipped use) |
| `r.damp(x)` | removed | yes where `x == 0.5` or `lowpass` is set; both shipped uses qualify |

Shipped call sites that need more than a word swap:

- `DerSchmetterling.kt:416` (maintainer's uncommitted edit): was `room(wet = 0.40, size = 7.0, fade = 0.5)`, where the
  7 was ignored. The maintainer dropped the `fade` during the session, so the line is a plain word swap. The maintainer's
  other uncommitted tuning in that file (`postgain(mul(0.22))`, `size = 7.0`) stays out of the commit.
- Positional fades: `Tetris` `room(0.3, 3, 0.05, 11500)` -> `reverb(0.3, 0.5, 11500)`; `TetrisRemix`
  `room(0.8, 5, 0.1)` -> `reverb(0.8, 1)`; `Sakura` `room(0.35, 7, 0.75)` -> `reverb(0.35, 7.5)`; `FrozenSongs`
  `room(0.3, 5, 0.1)` -> `reverb(0.3, 1)` and `room(..., size = 8, fade = 0.12, ...)` -> `reverb(..., size = 1.2, ...)`;
  `SongBenchmarkCases` `room(0.10, 8, 0.12)` -> `reverb(0.10, 1.2)` (case labels unchanged, they are stable case IDs).
  Every `size = 10x` was checked bit-exact against the fade it replaces (`(10x) / 10 == x` in IEEE doubles).
- `DerSchmetterling.kt:435` and its frozen copy `FrozenPieces.kt:441`: `r.wet(0.2).damp(0.8).roomSize(7).roomLp(3500)`
  becomes `r.wet(0.2).size(7).lowpass(3500)`.
- `StrangerThings.kt:80`: `r.wet(0.05).damp(0.5).roomSize(9)` becomes `r.wet(0.05).size(9)`.

## Steps

Each step builds, passes its tests, goes through `/review-loop` until a clean round, and is committed on the working
branch.

### S1. Master reverb stage

- `audio_bridge/.../MasterDsl.kt`: `MasterStageDsl.Reverb(wet, size, lowpass)`, `DEFAULT_SIZE`; KDoc twins name
  `reverb(size = ...)`, `reverb(lowpass = ...)`.
- `klangscript-libs/.../MasterBuilders.kt`: knobs `wet`, `size`, `lowpass`; remove `damp`, `roomFade`, `roomSize`,
  `roomLp`. Header example and `KlangScriptMaster.kt` example.
- `audio_be/.../master/MasterChain.kt`: `buildReverb` reads `size` and `lowpass`; the `hasFade` gate and the `damp`
  write go.
- Songs and fixtures: `DerSchmetterling`, `StrangerThings`, `IrishLamentTechno`, `Greensleeves`,
  `ATruthWorthLyingFor`, `FrozenPieces`, and every other master reverb call.
- Specs: `KlangScriptMasterBuilderSpec` (door parity, removed knobs refused), `MasterChainSpec`, `MasterBusTest`,
  `MasterDefaultsSyncSpec`, `MasterOrbitReverbParitySpec`, `LangMasterSpec`, `WireCodecRoundTripSpec`,
  `MasterRingShelfSpec`.

S1 leaves the engine `Reverb` properties alone; it only stops writing `damp` and `roomFade` from the master.

### S2. Sprudel door, voice wire, engine

- `sprudel/.../lang/lang_effects_reverb.kt`: `reverb(wet, size, lowpass)` on the three receivers and the `reverb`
  object; KDoc, examples, `@param-tool size SprudelReverbSizeEditor, SprudelReverbSizeSequenceEditor`, `@tags`.
  `iresponse`/`ir` stay (out of scope).
- `SvdGroups.kt`, `SprudelVoiceData.kt`: group property `reverbFx`, fields and flat accessors per the table, merge
  function, `toVoiceData`.
- `audio_bridge/.../VoiceData.kt`: flat fields per the table.
- `audio_be`: `VoiceFactory`, `Voice.Reverb`, `SendRenderer`, `Cylinder`, `KatalystReverbEffect`, `Reverb` (drop
  `damp`, `roomFade`, `roomDim` properties; rename `roomSize`/`roomLp`; `restoreDefaults`, `effectiveFeedback`, KDoc
  including the corrected ceiling explanation), `WarmupRunner`, `PlaybackEngine` KDoc.
- Benchmarks: `audio_benchmark` (`EffectBenchmark`, `IgnitorBenchmark`, `VoiceDataCopyBenchmark`),
  `klang/.../KlangBenchmark.kt`.
- UI: `SprudelUiTools.kt` registrations, `SprudelNumericEditorTool.kt` (`SprudelReverbSizeEditorTool`),
  `SprudelReverbEditorTool.kt` (call name `reverb`, fields wet/size/lowpass).
- Guards: `LangRetiredDoorsSpec` gains `room`; a spec that `reverb(fade = ...)` and `reverb(dim = ...)` are refused.
- Specs: `LangRoomSpec`, `LangRoomSizeSpec`, `LangReverbSpec` fold into `LangReverbSpec` (plus a size spec if it
  stays separate); `LangEffectsRoutingSpec`, `LangFieldAccessorsSpec`, `LangFeedbackCapSpec`, `LangWetKnobSpec`,
  `LangDeletedWetNamesSpec`, `CallInfoTest`, `SprudelScopeSpec`, `SprudelVoiceDataSpec`, `FreqAccessorIntelSpec`,
  `WorkletWireCodecRoundTripSpec`, `IgnitorDslSpec`, all `audio_be` reverb specs (`KatalystReverbEffectSpec`,
  `ReverbStabilitySpec`, `LazyReverbSpec`, `ClosedFormTailSpec`, `CylinderKatalystPipelineSpec`,
  `CylinderCleanupTest`, `KatalystPipelineSpec`, `CylinderShelfSpec`, `EngineDisposalReturnSpec`).
- Strudel comparison harness: `GraalSprudelPattern.kt` maps Strudel's `room`/`roomsize`/`roomlp` onto the new
  carrier names (`roomfade` maps to `reverbSize = 10x`, `roomdim` is dropped); `JsCompatTestData`,
  `JsCompatTestSongs` use the sprudel door.
- Golden corpus: `GoldenCorpus.kt` (pinned Der Schmetterling copy) migrated;
  `voicedata_golden.txt` regenerated with `UPDATE_GOLDEN=true`.
- Songs, tutorials and app code: every `room(` in `src/` (builtin songs, `tut_*`, `LexikonData`, `TestKotlinPatterns`,
  `TestTextPatterns`, `StartPage`, `PlayerWarehouseStats`, `AdsrVisual`, `FrozenPieces`, `FrozenSongs`,
  `SongBenchmarkCases`).
- KDoc and examples elsewhere in sprudel (`lang_dynamics_orbit`, `lang_effects_body`, `lang_effects_delay`,
  `lang_effects_vowel`, `SprudelPattern`), `klangscript-annotations` (`KlangScope`, `KlangScript`), `klangscript-ksp`
  (`KDocParser`, `KDocParserTest`) where they cite `room` as the example.

### S3. Docs, skills, rules

- `CLAUDE.md` retired list: `room` (and its `fade`, `dim` slots), master `roomSize`, `roomFade`, `roomLp`, `damp`;
  fix the existing entry that cites `room(fade = 0.3)` as the live form.
- `/dsl-design` §6: the reverb has no creative regime above unity feedback; §4 example names.
- `/klang-music-writing` refs (`sprudel-reference.md`, `ignitor-reference.md`, `production-quality-rubric.md`).
- `sprudel/MEMORY.md`, `sprudel/ref/dsl-conventions.md`, `sprudel/ref/uitools.md`, `audio/MEMORY.md`,
  `audio/ref/data-model.md`, `audio/ref/effects-mixing.md`, `docs/knowhow/recording-a-drum-kit.md`.
- Live task docs that describe the current surface: `master-dsl-followups.md` §1 (resolve the `fade`, `damp`,
  `roomDim` bullets, link here), `sprudel-ui-tools.md`, `editor-tools-named-arguments.md`, `tutorial-master-plan.md`,
  `tutorial-curriculum.md`, `ignitor-dsl-open-items.md`, `katalyst-dsl.md`, `audio-backend-audit.md`,
  `audio-pipeline-open-topics.md`, `pipeline-dsl-coefficient-exposure.md`, `copyright-audit-07` (a dated note: the
  door no longer uses Strudel's `room` word; no legal claim).
- Historic records stay as written: `docs/tasks-archive/`, `docs/blog/`, `DEV-DIARY.MD`, `docs/benchmarks/`,
  `docs/strategy/`, `.claude/vision/`, strategist memory.

## Verification

- **Golden proof of no behaviour change.** Before regenerating, parse every event line of the old
  `voicedata_golden.txt` into its key/value set, apply the key renames (`room` to `reverb`, `roomSize` to `reverbSize`,
  `roomLp` to `reverbLowpass`) and compare against the regenerated file. The only expected difference is where the
  corpus used `fade` (then `reverbSize = 10x` replaces it) or `dim` (dropped). Scripted in the scratchpad, result
  recorded in the step report.
- **Door parity.** Sprudel `reverb(size = x, lowpass = y)` and master `r.size(x).lowpass(y)` configure an identical
  `Reverb` (extend `MasterOrbitReverbParitySpec`).
- **Guards, mutation-checked** (mandatory tier: engine, wire, KSP-visible doors): each new or changed guard fails when
  its protected line is reverted (retired `room`, refused `fade`/`dim`, refused master `damp`/`roomSize`/`roomFade`/
  `roomLp`, positional order `wet, size, lowpass`, the ceiling at 10 still bounded).
- **Builds and suites**, one Gradle run at a time: `:audio_bridge`, `:audio_be`, `:klangscript-libs`, `:sprudel`
  (jvmTest, including `DslDocExamplesSpec` and the golden spec), `:klang`, root app `compileKotlinJs` and
  `compileKotlinJvm`, `:audio_benchmark` compile.
- **Repo sweep** at the end: no `roomSize`, `roomFade`, `roomLp`, `roomDim`, `.room(`, `room.`, `damp(` outside the
  historic records listed in S3.

## Out of scope (recorded, not scheduled)

- ~~`iresponse()`/`ir` and the engine `iResponse` field~~: DONE the same day, see the follow-up below.
- The size floor at 0 (shorter rooms need a separate on/off switch first).
- The engine names the delay's and the reverb's send `amount` while both doors say `wet`; the flat carriers use the
  bare concept word. Pre-existing delay convention, kept for symmetry.
- The master delay's wire field `timeSeconds` against the door knob `time`: unchanged.

## Follow-up 2026-09-16: `iresponse` / `ir` removed

Maintainer, same day: "please remove the ir / iresponse dsl". The doors stored an impulse-response name that no engine
path ever read (there is no convolution reverb), so they went end to end like `dim`: the `iresponse`/`ir` doors on
all three receivers, `SprudelVoiceData.iResponse` and its `SvdReverb` field, `VoiceData.iResponse`,
`Voice.Reverb.iResponse`, `Reverb.iResponse` and its TODO hook, and the `iResponse` parameter of
`KatalystReverbEffect.configure`. No song, tutorial, benchmark or golden fixture used them. Guard: `iresponse` and `ir`
in `LangRetiredDoorsSpec`. The planned L3 "Convolution Reverb" lesson in `tutorial-master-plan.md` is marked dropped
until a convolution reverb exists; `sprudel/TODOS.MD` keeps the engine idea, which designs its own door.

## Follow-up 2026-09-16: no specs for names that are gone

Maintainer, same day: "There is no need for LangRetiredDoorsSpec. This will only cause confusion in the future." A
removed name is simply gone; no spec asserts that it stays gone. Deleted: `LangRetiredDoorsSpec`,
`LangDeletedWetNamesSpec`, `LangDeletedFilterAliasesSpec`, and the "X is gone" tests in `LangReverbSpec` (`fade`/`dim`),
`KlangScriptMasterBuilderSpec` (retired knobs, `Master.of`, `MasterFx`), `KlangScriptPipelineBuilderSpec`
(`Pipeline.of`, `Stage`), `KlangScriptEffectBuilderSpec` (old chain knobs), `StdLibOscTest` (`.eq().band`),
`LangAdsrCurveDefaultSpec` (singular `adsrCurve`), `LangPitchParamNamesSpec` (old param names) and `LangWetKnobSpec`
(`blend`). The guard sentence in the `CLAUDE.md` retired list went with them. The guard rows named in the sections above
describe what this task did at the time and no longer exist.
