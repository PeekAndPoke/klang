# Ignitor and Katalyst: one name each, end to end

Planned 2026-10-03 from the maintainer's decisions of the same day (brief: `tmp/reviews/naming-plan-brief.md`).
Status: **DONE 2026-10-04** on branch `ignitor-katalyst-naming` (commits C1 `807e42d3`, C2 `52e915f0`, D1 `d8b2642f`,
C3 `1ac2f0b0`, C4 `e9250ea4`, then C5 with D2). Verified: a corpus of 18 songs (the built-ins and the frozen texts) over
256 cycles hashes identical in raw doubles to the baseline taken before C1, after every code step and on the final tree,
with an engagement control (a deliberate `ignp` mutant moved exactly the 5 predicted rows); the full suite green after
each step; `WIRE_SCHEMA_HASH` moved (779447070 to -785660449). Reviews R-A to R-E, each until a clean round. Departures
from the plan: an Ignitor expression over a param on a Katalyst knob is documented, not refused (a tree walk would
trip on the `Slots.analog` every oscillator carries); `console/song-snapshots.sh` migrates the old tags' syntax on
extraction instead of letting them stop parsing; the shared `EqBuilder` carries an `onKatalyst` flag; the setter errors
carry the call location explicitly (4.5's assumption did not hold). Found along the way, filed separately:
`docs/tasks/stdlib-export-block.md`.

The plan as written follows. Every count below was taken with `git grep` on `main` at `38fa1d42`; a sentence
marked **UNVERIFIED** is a claim about behaviour that nobody has run yet, and the implementer confirms or refutes it
before building on it (`/review-loop`, "a claim about what existing code DOES").

## 1. The decisions

"We stay bold and keep the names Ignitor and Katalyst. We provide shortcut and full length names everywhere."

1. The KlangScript object `Osc` becomes `Ignitor`, with `Ign` as a second name for the SAME object
   (`Ign.sine()` is `Ignitor.sine()`, for every member). `Katalyst` gets `Kat` the same way.
2. `OscSlot` is removed. The slots stay reachable through the singular accessor: `Ignitor.slot.analog`,
   `Ign.slot.analog`. NEW: `Katalyst.slot.<stage>.<knob>` / `Kat.slot...` for the classic chain's knobs.
3. sprudel: `.ignitorParam(name, value)` and `.ignp(...)` replace `.oscparam` and `.oscp`; `.katalystParam(name,
   value)` is NEW as the full name of `.katp(...)`. No two-letter forms (`ip`, `kp`), no generic `.param` / `.p`.
4. Both setters take a slot NAME (string) or the param OBJECT. Ignitor params and Katalyst params are distinct
   types, so the wrong one is a script error at the call ("a Katalyst param passed to ignp; use katp").
5. One word end to end: wire field `oscParams` becomes `ignitorParams`, the Kotlin doors, internal names
   (`KlangScriptOsc`, `KlangScriptOscSlot`, helpers and variables that say osc for the Ignitor concept), the
   frontend, tutorials, songs, skills, docs, the whitepaper. A replaced surface is REMOVED (`/dsl-design` §5).
   An "osc" that is a real oscillator stays (section 2.12 draws the line per name).
6. Every song may be updated; the maintainer does not touch songs during the work. Everything is committed.

## 2. The name map

### 2.1 KlangScript surface (what a song writes)

| old | new | note |
|---|---|---|
| `Osc` (object) | `Ignitor`, and `Ign` | one object, two names (section 4.1) |
| `Osc.slot.<x>` | `Ignitor.slot.<x>`, `Ign.slot.<x>` | the singular accessor stays |
| `OscSlot` (global object) | removed | `OscSlot.lpf.freq` is `Ign.slot.lpf.freq` |
| `OscSlot.<group>.<knob>` | `Ignitor.slot.<group>.<knob>` | same `Param` instances |
| (none) | `Kat` | second name of `Katalyst` (section 4.1) |
| (none) | `Katalyst.slot.<stage>.<knob>`, `Kat.slot...` | NEW (section 4.3) |
| `Katalyst.param(...)` returns an `IgnitorDsl` | returns a `KatalystParam` | distinct type (section 4.4) |
| `.oscparam(key, value)` | `.ignitorParam(slot, value)` | four forms each (pattern, string, mapper factory, mapper chain) |
| `.oscp(key, value)` | `.ignp(slot, value)` | alias of `ignitorParam`, `@alias` KDoc pair as today |
| `.katp(key, value)` | `.katp(slot, value)` | unchanged name; now also takes the object |
| (none) | `.katalystParam(slot, value)` | NEW full name of `katp`, four forms |

The setter parameter is renamed `key` to `slot` (it is a slot name or a slot object; "slot" is the project's word).
No song passes it by name; two test rows do (`LangControlRestSpec.kt:124`, `:125`, `oscparam(key = "analog", ...)`)
and change with the rename. If the maintainer prefers to keep `key`, nothing else in this plan depends on it.

### 2.2 sprudel (Kotlin door and internals)

| old | new |
|---|---|
| `SprudelPattern.oscparam` / `String.oscparam` / `oscparam(...)` / `PatternMapperFn.oscparam` | `ignitorParam` (same four) |
| the four `oscp` forms | the four `ignp` forms |
| (none) | the four `katalystParam` forms beside the four `katp` forms |
| `lang/lang_synthesis_oscparam.kt` | `lang/lang_synthesis_ignitorparam.kt` (also hosts `analog`, `duty`, `onepole`) |
| `applyOscparam` | `applyIgnitorParam` |
| `SprudelVoiceData.oscParams` | `SprudelVoiceData.ignitorParams` |
| `oscParamsOrNew()` | `ignitorParamsOrNew()` |
| `putOscParam(name, value)` | `putIgnitorParam(name, value)` |
| KDoc "Sets any oscillator parameter by key", `@tags oscillator, parameter, osc` | "Writes one Ignitor slot by name", `@tags ignitor, parameter, slot` |
| `katp` KDoc "the orbit twin of [oscparam]", `ParamBag` and `SprudelVoiceData` KDoc naming `oscParams`, `withOscParam`, `withOscParams`, `mergeOscParamsFrom` | the new words; the sentence about the removed copying variants goes (it explains history) |
| internal callers `p.oscparam("decay", ...)` in `lang_synthesis_snd_super.kt`, `lang_synthesis_snd_basic.kt` | `p.ignitorParam(...)`; the `callInfo.forParam(i, 1)` indices are unchanged |

### 2.3 Wire and `audio_bridge`

| old | new | note |
|---|---|---|
| `VoiceData.oscParams` | `VoiceData.ignitorParams` | wire field; `WIRE_SCHEMA_HASH` changes (section 3.3) |
| `VoiceData.katalystParams` | unchanged | already the one word |
| `SoundValue.Osc(val osc: IgnitorDsl)` | `SoundValue.Dsl(val ignitor: IgnitorDsl)` | mirrors `KatalystValue.Dsl(val katalyst)`; not a wire type (open question Q7) |
| `IgnitorDsl.Slots`, `IgnitorDslClassic.kt` | unchanged names; KDoc examples `Osc.sine(x => x.analog(OscSlot.analog))` become `Ignitor.sine(x => x.analog(Ignitor.slot.analog))` | |
| KDoc "oscParams override matching", "osc-param" (`IgnitorDsl.kt`, `IgnitorDslOptimizer.kt`, `constants/OscillatorTuning.kt`) | "ignitorParams", "Ignitor slot" | |
| (none) | `KatalystParam` (typed handle, NOT a wire type), `KatalystDsl.Slots` | NEW (sections 4.3, 4.4) |

### 2.4 `audio_be`

| old | new |
|---|---|
| the `oscParams: Map<String, Double>?` parameter of `toExciter` and every helper in `IgnitorDslRuntime.kt` (130 hits) | `ignitorParams` (a named parameter: callers that write `oscParams =` change too) |
| `data.oscParams` readers in `VoiceFactory.kt`, `GraphCensus.kt`, `WarmupRunner.kt`, `ConstantIgnitor.kt`, `EqIgnitor.kt` | `ignitorParams` |
| `require(...) { "Osc.variants(...) must have at least one child" }` (`IgnitorDslRuntime.kt:251`) | `"Ignitor.variants(...) ..."` (see risk R6 for the `require` itself) |
| KDoc examples `.oscparam("eqdb", 9)` (`IgnitorDefaults.kt:223`), "osc-param" prose (`Ignitors.kt`, `IgnitorDefaults.kt`), `"Osc -> pregain -> classic"` (`IgnitorRegistry.kt:39`), `KatalystSlots.kt` | the new words |
| `Ignitor` (runtime interface), `IgnitorRegistry`, `KatalystSlots`, `KatalystChain` | unchanged |

### 2.5 `klangscript-libs` (the script door's Kotlin side)

| old | new |
|---|---|
| `KlangScriptOsc` (`@Object("Osc")`), file `KlangScriptOsc.kt` | `KlangScriptIgnitor` (`@Object("Ignitor")`), `KlangScriptIgnitor.kt` |
| `KlangScriptOscSlot` (`@Object("OscSlot")`), file `KlangScriptOscSlot.kt` | `KlangScriptIgnitorSlots`, registered with `@TypeExtensions` on itself (no global name, like the groups today), `KlangScriptIgnitorSlots.kt` |
| `KlangScriptCrushSlots`, `...CoarseSlots`, `...DistortSlots`, `...HpfSlots`, `...BpfSlots`, `...NotchSlots`, `...LpfSlots`, `...TremoloSlots`, `...AdsrSlots`, the five `...CurvesSlots`; file `KlangScriptClassicSlots.kt` | `KlangScriptIgnitorCrushSlots` and so on; `KlangScriptIgnitorClassicSlots.kt`. The prefix is needed because the Katalyst groups arrive beside them and both sides have a `classic` |
| `KlangScriptOscExtensions` (the `IgnitorDsl` extension methods), `KlangScriptOscExtensions.kt` | `KlangScriptIgnitorExtensions`, `KlangScriptIgnitorExtensions.kt` |
| `toString()` `"[Osc object]"`, `"[OscSlot object]"`, `"[OscSlot.crush]"` ... | `"[Ignitor object]"`, `"[Ignitor.slot]"`, `"[Ignitor.slot.crush]"` ... |
| `configuredBy("Osc.sine", ...)` door names in errors (16 doors) | `"Ignitor.sine"` ... |
| `KlangStdLib` source block `export { console, Math, Object, Osc }` | adds `Ignitor`, `Ign`, `Katalyst`, `Kat` (**UNVERIFIED** what this block governs today: `Katalyst` is not in it and works; the implementer reads `klangScriptLibrary(...).source` and names the effect) |
| `index_libs.kt`, `build.gradle.kts` header comments listing `Osc` | `Ignitor` |
| `Osc*Builder` (`OscSineBuilder` ... `OscSuperPluckBuilder`, 16 types) | **kept**, see 2.12 and Q4 |
| `IgnitorDslLike`, `toIgnitorDsl()`, `KatalystBuilder`, `EffectBuilders.kt` | unchanged names; `toIgnitorDsl()` gains one branch (section 4.4) |

### 2.6 `klangscript`, `klangscript-ksp`, `klangscript-annotations`, `common`

No code identifier says Osc. The hits are KDoc and comment examples (`Osc.sine()`, `Osc.supersaw(x => ...)`,
`"Osc", "OscSlot"` in `KlangType` KDoc, the `@Property` KDoc in `KlangScript.kt:143`, the KSP comments at
`KlangScriptProcessor.kt:1205`, `:1304`, `:1749`) and test fixtures:

- `klangscript-ksp/src/test/kotlin/EmissionHelpersTest.kt` uses the synthetic FQCNs `...stdlib.Osc`,
  `...sprudel.lang.Osc`, `...stdlib.KlangScriptOsc` to model a simple-name collision. Rename to `Ignitor` /
  `KlangScriptIgnitor`; the test is about names, any two equal simple names serve.
- `klangscript/.../NameSuggestionsSpec.kt` and `common/.../OsaDistanceSpec.kt` use `oscp` / `ocsp` as test words
  for a transposition. Swap to `ignp` / `ingp` (same shape, one adjacent transposition).
- `NameSuggestions.kt` KDoc and `Interpreter.kt:1446` tell the true story of `.ocsp` for `.oscp`. Rewrite as
  "a single adjacent transposition in a door name" with the date, so the guard needs no allowlist line here.
- `CallInfo.kt:32` example `oscparam(key, value)` becomes `ignitorParam(slot, value)`.

### 2.7 `klang`, `audio_benchmark`, `src/jvmMain`, `src/jvmTest`

- `klang/.../InlineDslRegistrar.kt`: `filterIsInstance<SoundValue.Osc>()`, `it.osc`; the stale KDoc "a synthetic
  `osc-N`" (the real names are `"ignitor-N"`, `IgnitorDslIdentity.kt:36`) is corrected.
- `klang/src/jsMain/kotlin/KlangBenchmark.kt`: `oscParams =`.
- `audio_benchmark`: `IgnitorBenchmark.kt` (27 named `oscParams =` args), `VoiceDataCopyBenchmark.kt`,
  `WorkletSerializationBenchmark.kt`. Benchmark CASE LABELS do not change (they are ledger row keys).
- `src/jvmMain`: `SongBenchmark.kt` (`sv.osc`, `data.oscParams`), `Cli.kt`, `SongBenchmarkCases.kt` (KlangScript
  text; its labels `"0 osc+env (supersaw uni9)"` mean a real oscillator and are ledger keys: kept),
  `FrozenSongs.kt`, `FrozenPieces.kt` (section 3.4).
- `src/jvmTest`: `OptimizerSongParitySpec.kt` (`sound.osc`, `oscParams`), `BuiltInSongsSmokeTest.kt` (comment),
  `SprudelDiagnosticsTest.kt`.

### 2.8 Frontend `src/jsMain`, tutorials, editor

- `pages/docs/KlangScriptLibraryDocsPage.kt:85` comment (`OscSlot`), `midi/MidiConnector.kt:69` comment
  ("oscparams").
- Tutorials: no tutorial teaches `Osc` or `oscp` today; one KDoc names the file `lang_synthesis_oscparam.kt`
  (`pages/docs/tutorials/tut_Thickness.kt:30`).
- Editor tools: **none writes `oscp`, `katp` or `oscParams` text** (grep of `src/jsMain/kotlin`, `klangui`,
  `klangscript-ui`, sprudel `lang/editor`: no hits). Completion, the docs popup and the docs pages are generated
  by KSP from the KDoc, so they follow the code.

### 2.9 Songs

Seven built-in songs, 178 lines: `DerSchmetterling.kt` 57, `Kokon.kt` 52, `IrishLament.kt` 21, `Sakura.kt` 19,
`ATruthWorthLyingFor.kt` 15, `Sandsturm.kt` 11, `Greensleeves.kt` 2. Spellings in use: `Osc.<door>`,
`OscSlot.<name>`, `Osc.param(...)`, `.oscp(...)`, one `.oscparam(...)`. No song uses `katp` or `Katalyst.param`.
Which spelling the songs get is Q1 (recommendation: `Ign.` and `ignp`, the same length as `Osc.` and `oscp`, so the
songs' aligned trailing comments stay aligned and a diff shows names only).

### 2.10 Docs, skills, register, whitepaper

Live documents, updated: `CLAUDE.md` (the guardrail row cites `StdLibOscTest`; the retired list explains a door as
"a `classic()` slot in `oscParams`"; a new retired entry is added, section 6 C5), `README.MD` (lines 71 and 130 list
`Osc`, and also the retired `Master` and `Pipeline`, see R7), module docs (`klangscript/CLAUDE.md`,
`klangscript/MEMORY.md`, `klangscript/language-features/04-functions.md`, `klangscript/ref/intel-analyzer.md`,
`klangscript-libs/CLAUDE.md` file table, `sprudel/MEMORY.md`, `sprudel/ref/dsl-conventions.md`, `audio/CLAUDE.md`,
`audio/MEMORY.md`, `audio/ref/{data-model,voice-synthesis,off-values,performance,numerical-safety}.md`), skills
(`klang-music-writing/ref/ignitor-reference.md` 159 lines, `klang-music-writing/ref/sprudel-reference.md`,
`klang-music-writing/SKILL.md`, `dsl-design/SKILL.md` including §5's "known debt" paragraph which this plan
closes, `klangaudio-knowhow/SKILL.md`, `klangscript-knowhow/SKILL.md`), `docs/instrument-prototypes.md` (83),
`docs/plans/*` (6 files, 60 lines; this file excepted), `docs/tasks/*` (22 files, 63 lines), the whitepaper
`src/jsMain/resources/klang-whitepaper.html` (37 lines, code blocks and prose) and its figures
`whitepaper/fig-glockenspiel.html` (7, SVG box labels sized to the text: `Ign.sine()` fits the boxes, `Ignitor.sine()`
does not), `fig-three-owners.html` (3), `fig-two-senders.html` (4, the field chip `oscParams`), `.idea/dictionaries/
project.xml` (`oscp`, `oscparam` become `ignp`).

History, unchanged (the house rule: historic records keep their words): `docs/tasks-archive/**`,
`**/memory-history.md`, `DEV-DIARY.MD`, `docs/history/**`, `docs/funding/evidence/**` and
`docs/funding/scripts/build_quotes.py` (verbatim maintainer quotes), the dated decision row R10 of
`docs/funding/design-decisions-skeleton.md`, `docs/strategy/**` (dated strategist records),
`.claude/build-lock-log.md`, the dated rows of `.claude/skills/review-loop/escape-ledger.md` and
`.claude/skills/agent-fleet/defect-density-ledger.md`, the generated dated snapshots
`src/jsMain/resources/klang-mission-log.html` and `klang-topic-map.html` (regenerated by their tool, never hand
edited), and the blog (section 5).

### 2.11 What does not change, and why

| name | why |
|---|---|
| Katalyst, `KatalystDsl`, `KatalystRegistry`, `.katalyst()`, `master(...)` | already the one word (`/dsl-design` §5's model) |
| `katp` | kept as the short name; `katalystParam` joins it |
| `VoiceData.katalystParams`, `SprudelVoiceData.katalystParams`, `katalystParamsOrNew`, `putKatalystParam` | already the one word |
| `IgnitorDsl`, `IgnitorDsl.Param`, `IgnitorDsl.Slots`, `IgnitorDslLike`, `Ignitor` (runtime), `IgnitorRegistry` | already the one word |
| the slot names themselves (`analog`, `voices`, `lpf.freq`, `adsr.attack`, `reverb.wet`, `gain.gain`) | they name a knob, not the host; renaming one would change what a song writes and what the engine reads |
| sprudel doors `analog`, `duty`, `onepole`, `pregain`, `unison` | oscillator or voice knobs by meaning |
| `KatalystValue.Named` / `.Dsl` | the model `SoundValue` now follows |
| benchmark case labels (`"0 osc+env ..."`), ledger rows | real oscillators, and ledger keys |
| real-oscillator names (2.12) | an oscillator is an oscillator |

### 2.12 The line: Ignitor concept or real oscillator

The test per name: does it name the HOST (the per-voice instrument, its object, its slot map, its setter), or a
SOURCE that oscillates? The first is renamed, the second kept.

| name | verdict | reason |
|---|---|---|
| `Osc`, `OscSlot`, `KlangScriptOsc`, `KlangScriptOscSlot`, `KlangScriptOscExtensions` | rename | the host object, its slot map, its extension methods (which apply to any `IgnitorDsl`, a filter or a noise too) |
| `oscp`, `oscparam`, `oscParams`, `putOscParam`, `oscParamsOrNew`, `applyOscparam`, `SoundValue.Osc`, `sv.osc` / `it.osc` / `sound.osc` holding an `IgnitorDsl` | rename | the host's slots, the host's tree |
| `LangOscparamSpec`, `KlangScriptOscSlotTest`, `StdLibOscTest` (tests the object), test helper `oscSlot(key)` in `LangDoorFormsSpec` | rename to `LangIgnitorParamSpec`, `KlangScriptIgnitorSlotTest`, `StdLibIgnitorTest`, `ignitorSlot(key)` | named after the renamed surface |
| prose "osc-param", "oscillator parameter" for an `oscp` slot | rename to "Ignitor slot" | a slot may hold a filter cutoff, not an oscillator knob |
| `Osc*Builder` (16 builder types) | keep (Q4) | each configures an oscillator node (`sine`, `saw`, `supersaw`, `pluck`); the editor shows `x: OscSuperSawBuilder` inside `Ignitor.supersaw(x => ...)`, which reads correctly |
| `OscillatorTuning.kt`, `WaveOscDefaultsSyncSpec`, `SuperOscDefaultsSyncSpec`, `OscShapeEffectSpec`, `KlangScriptSuperOscSpec`, `oscA` / `oscB` in tests, "self-osc" in the delay | keep | real oscillators, real self-oscillation |
| "oscillator" in prose about `sine`, `saw`, `analog` drift, `duty` | keep | true as written |

A scripted rename must know what a word is (`/review-loop` Gotchas, batch G): rewrite `\bOsc\.` and `\bOscSlot\b`
in KlangScript text and `\boscp\(` / `\boscparam\(` in call position; never a bare `osc` substring.

## 3. Inventory

Excluding the history paths of 2.10. "Lines" are lines containing at least one of `Osc` (word), `OscSlot`,
`KlangScriptOsc*`, `oscp`, `oscparam`, `oscParam(s)`, `putOscParam`, `SoundValue.Osc`.

### 3.1 Counts per layer

| layer | main | tests |
|---|---|---|
| `audio_bridge` | 4 files, 23 lines (`IgnitorDsl.kt`, `IgnitorDslOptimizer.kt`, `SoundValue.kt`, `VoiceData.kt`) | 4 files, 5 lines |
| `audio_be` | 13 files, 153 lines (130 in `IgnitorDslRuntime.kt`) | 35 files, 152 lines (`IgnitorDefaultsTest` 30, `IgnitorsTest` 22, `IgnitorDslOptimizerRenderSpec` 14, `IgnitorTailSpec` 11, ...) |
| `sprudel` | 14 files, 133 lines (`lang_synthesis_oscparam.kt` 44, `SprudelVoiceData.kt` 25, `lang_synthesis_snd_super.kt` 20, `lang_dynamics_unison.kt` 13) | 30 files, 210 lines (`SprudelVoiceDataSpec` 22, `LangSndSpec` 20, `LangDoorFormsSpec` 19, `LangPregainSpec` 18, `LangFieldAccessorsSpec` 18, `LangDensitySpec` 17, `ClassicSlotParamsSpec` 14, ...), plus `jsTest/WorkletWireCodecRoundTripSpec` 8 and `jvmTest/graal/GraalSprudelPattern` 3 |
| `klangscript-libs` | 14 files, 178 lines | 27 files, 651 lines (`StdLibOscTest` 114, `AnalyzedAstTest` 82, `StdlibDocsInferenceTest` 57, `CompletionProviderTest` 35, ...) |
| `klangscript` | 8 files, 13 lines (KDoc) | 3 files, 6 lines |
| `klangscript-ksp`, `klangscript-annotations` | 2 files, 4 lines (comments) | 1 file, 11 lines |
| `common` | 1 file, 1 line | 1 file, 5 lines |
| `klang` | 2 files, 2 lines | 1 file, 2 lines |
| `audio_benchmark` | 3 files, 30 lines | |
| songs `src/commonMain/kotlin/builtinsongs` | 7 files, 178 lines | |
| `src/jvmMain` | 5 files, 90 lines (`FrozenSongs` 35, `FrozenPieces` 34, `SongBenchmarkCases` 17) | `src/jvmTest` 3 files, 9 lines |
| frontend `src/jsMain/kotlin`, tutorials | 3 files, 3 lines (comments) | |
| whitepaper and figures | 4 files, 51 lines | |
| skills | 6 live files, about 175 lines | |
| docs (`docs/plans`, `docs/tasks`, `instrument-prototypes.md`, module docs, `CLAUDE.md`, `README.MD`) | about 50 files, about 260 lines | |

Total live: about 300 files, about 2,400 lines. `oscParams` alone: 99 files, 492 lines.

### 3.2 KSP: one object under two names

`@KlangScript.Object(name)` has no alias parameter (`klangscript-annotations/.../KlangScript.kt:45`), and no second
object is needed. The runtime keys methods by the Kotlin CLASS and maps a script name to an INSTANCE:
`registerObject(name, obj)` does `nativeObjects[name] = obj` and registers the class
(`klangscript/.../builder/KlangScriptExtensionBuilder.kt:208`). A top-level
`@KlangScript.Constant val Ign: KlangScriptIgnitor = KlangScriptIgnitor` emits `registerObject("Ign", Ign)`
(`KlangScriptProcessor.kt:753`), the same instance under a second name, with every method reachable. This is the
house pattern for an alias: sprudel's `@KlangScript.Constant val vel: velocity = velocity`
(`lang_dynamics_level.kt:501`, `sprudel/ref/dsl-conventions.md`). The editor types the constant by the object's
script name when the object is in the same module (`generateKlangType`, `KlangScriptProcessor.kt:1678`), so `Ign`
shows as `Ignitor`. **UNVERIFIED** for an `@Object` in `klangscript-libs` (the precedent is an accessor object in
sprudel): the alias spec of section 7 proves completion after `Ign.` and the signature popup of `Kat(`. The alias
KDoc carries `@category` and `@tags`, or the docs page lists it as "uncategorized" (`FreqAccessorIntelSpec`).
No KSP change, so the KSP mandatory mutation tier is not triggered.

### 3.3 The wire schema hash

`WIRE_SCHEMA_HASH` folds every non-sealed wire type's parameter NAMES and types
(`audio-wire-codec-ksp/.../WireCodecProcessor.kt:250`), and `VoiceData` is reached through `ScheduledVoice`, so
renaming the field changes the hash and the generated codec's JS property name. Both ends are built together; the
worklet rejects a stale bundle with its schema-mismatch message (`audio_be/src/jsMain/kotlin/WorkletContract.kt:64`)
until a reload. Nothing persists a `VoiceData` (it is not `@Serializable`; no JSON, golden or fixture file in the repo
carries `oscParams`). The old and new hash values go in the C1 commit message.

### 3.4 Golden data and frozen texts

- `audio_benchmark`: no golden files; only named arguments in Kotlin.
- `FrozenSongs.kt` and `FrozenPieces.kt`: the SYNTAX-ONLY exception (maintainer, 2026-08-23) covers exactly this:
  "renames ... because a frozen song that no longer parses guards nothing. Values must never change." Each gets a
  dated line in its KDoc migration list ("2026-10, the Ignitor rename: the script object, its slot object and the
  two setters, `docs/plans/ignitor-katalyst-naming.md`; the render bit-identical"), written WITHOUT the old
  spellings so the guard needs no allowlist line there.
- `console/song-snapshots.sh` renders old tags' song texts on today's engine; texts before this rename stop
  parsing, exactly as its header already records for texts before `v0.3.8.2`. Its header gets one line.

### 3.5 Examples that run

KDoc fences tagged `KlangScript(Playable)` / `KlangScript(Executable)` are executed by `DslDocExamplesSpec`. Fenced
example lines using an old name: `KlangScriptOsc.kt` 18, `KlangScriptOscExtensions.kt` 10, `IgnitorBuilders.kt` 6,
`lang_dynamics_level.kt` 3, `lang_synthesis_oscparam.kt` 2, `lang_dynamics_adsr.kt` 1, `KlangScriptOscSlot.kt` 1,
plus untagged fences in the same files. The whitepaper's code blocks are NOT executed by any spec (section 7).

### 3.6 Strings a user sees

The `toString()` names (2.5), the `configuredBy` door names in lambda errors, the engine's
`"Osc.variants(...)"` message, the KDoc that becomes the editor popup and the docs pages, the whitepaper, and the
docs page's library listing. `.idea/dictionaries/project.xml` holds `oscp` and `oscparam` as spelling words.

## 4. The new pieces

### 4.1 `Ign` and `Kat`

In `klangscript-libs/src/commonMain/kotlin/stdlib/`, beside each object:

```kotlin
/** The short name of [KlangScriptIgnitor]: `Ign.sine()` is `Ignitor.sine()`. @category ignitor @tags ... */
@KlangScript.Constant
val Ign: KlangScriptIgnitor = KlangScriptIgnitor

@KlangScript.Constant
val Kat: KlangScriptKatalyst = KlangScriptKatalyst
```

The same `val`s are the Kotlin door (`Ign.sine()` compiles in Kotlin). `Kat(k => ...)` must reach the
`@KlangScript.Invoke` member through the constant (**UNVERIFIED**: the invoke dispatch is by class, which predicts
yes). One alias per object, no alias factory, no third name.

### 4.2 `Ignitor.slot`

`KlangScriptIgnitor.slot` keeps its `@KlangScript.Property`; its type `KlangScriptIgnitorSlots` loses `@Object` and
gains `@TypeExtensions` on itself, exactly like the slot groups today, so `OscSlot` leaves the global namespace and
`Ign.slot.lpf.freq`, `Ignitor.slot.lpf.freq` and the Kotlin `IgnitorDsl.Slots.lpf.freq` are the same `Param`
instance. The editor names the type `KlangScriptIgnitorSlots` (the groups display their Kotlin name today too).

### 4.3 `Katalyst.slot`

Kotlin door, `audio_bridge` (new file `KatalystDslSlots.kt`, the twin of `IgnitorDslClassic.kt`'s groups):

```kotlin
object Slots {                       // KatalystDsl.Slots
    val body: BodySlots              // material, wet, floor
    val vowel: VowelSlots            // vowel, wet, floor
    val delay: DelaySlots            // wet, time, feedback, cap
    val reverb: ReverbSlots          // wet, size, lowpass
    val phaser: PhaserSlots          // rate, wet, center, sweep, floor
    val compressor: CompressorSlots  // threshold, ratio, knee, attack, release
    val gain: GainSlots              // gain
    val duck: DuckSlots              // orbit, depth, attack
}
```

Each knob is a `KatalystParam` holding today's `IgnitorDsl.Param(name = "<stage>.<knob>", default = ...)`, the
names and defaults copied from `KatalystDsl.classic` (`KatalystDsl.kt:151`), which is then built FROM `Slots`
(`wet = Slots.reverb.wet.param`). Equal `Param` values, so `classic` is unchanged by value and the render is
bit-identical. The 27 knobs are exactly the `katp` vocabulary its KDoc lists today.

Script door, `klangscript-libs` (new file `KlangScriptKatalystSlots.kt`): `KlangScriptKatalyst.slot` as an
`@KlangScript.Property` of type `KlangScriptKatalystSlots`, and one group object per stage
(`KlangScriptKatalystReverbSlots` ...), each `@TypeExtensions` on itself, each property handing back the SAME
`KatalystParam` instance as `KatalystDsl.Slots`. `Kat.slot.gain.gain` reads oddly and is right: it is the slot
`gain.gain` (one word per concept).

Out of scope, recorded: sprudel's bus doors still type their keys (`"reverb.wet"`); reading them from
`KatalystDsl.Slots`, as `_classic_slot_params.kt` reads `IgnitorDsl.Slots`, is a later refactor (Q10).

### 4.4 Distinct param types

The Ignitor side keeps `IgnitorDsl.Param` (a tree leaf; `Ign.param(...)` and `Ign.slot.*` return it). The Katalyst
side gets a typed handle in `audio_bridge`:

```kotlin
/** A named knob of a Katalyst chain: what `katp` writes. A handle, never on the wire: a chain holds [param]. */
data class KatalystParam(val param: IgnitorDsl.Param) {
    val name: String get() = param.name
}
```

- `Katalyst.param(name, default, description)` returns `KatalystParam`; `Kat.slot.*` hold `KatalystParam`.
- The Katalyst builder knobs (`KatalystBuilders.kt`, every door that takes `IgnitorDslLike`) convert through one new
  function `toKatalystKnob()`: a number is a `Constant`, a `KatalystParam` is its `param`, any other `IgnitorDsl`
  is taken as today (folded once at build). Whether a BARE `IgnitorDsl.Param` there becomes an error ("an Ignitor
  param in a Katalyst chain; use Kat.param") is Q2; today the KDoc invites it (`KatalystBuilders.kt:42`, `:48`).
- `toIgnitorDsl()` (the Ignitor doors) gets a `KatalystParam` branch with a clear message ("a Katalyst param in an
  Ignitor tree; use Ign.param") instead of the generic "got KatalystParam".
- Consequence (Q3): a `KatalystParam` is not an `IgnitorDsl`, so `Kat.param("room", 5).mul(2)` becomes a script
  error instead of the silent build-time fold that the `katp` KDoc warns about today. The warning paragraph goes.

Rejected: a side marker field on `IgnitorDsl.Param` (a wire change, every `Param(...)` call, and the node identity of
every tree that holds a param), and a second `@WireName` subtype (the engine would learn a frontend distinction it
does not need).

**UNVERIFIED**: a `KatalystParam` needs no `@TypeExtensions` registration to travel through the interpreter
(`KatalystDsl` has none and works), and the editor displays it as `KatalystParam`.

### 4.5 The script errors

Two shared resolvers in `klangscript-libs` (sprudel depends on it), used by both setters on both doors:

```kotlin
fun ignitorSlotName(slot: Any?, door: String): String = when (slot) {
    is String -> slot
    is IgnitorDsl.Param -> slot.name
    is KatalystParam -> throw KlangScriptTypeError("a Katalyst param passed to $door; use katp", ...)
    else -> throw KlangScriptTypeError("$door expects a slot name or an Ignitor param, got ...", ...)
}
// katalystSlotName: the mirror; IgnitorDsl.Param -> "an Ignitor param passed to $door; use ignp"
```

- `ignp(Ign.sine(), 1)` (a sound, not a param) and `ignp(Ign.param("x", 1).mul(2), 1)` (an expression over a param)
  are "expects a slot name or an Ignitor param, got a sound". A number key is Q8 (today `42` silently becomes the
  slot name `"42"`, and an object key silently becomes its `toString()`: `applyOscparam` and `applyKatp` call
  `args[0].value?.toString()`, `lang_synthesis_oscparam.kt:23`, `lang_katalyst.kt`).
- The error is raised when the door is CALLED (the key is read while the pattern is built, not per query), with the
  call's location (**UNVERIFIED** that `KlangScriptTypeError` thrown inside a sprudel door carries the `CallInfo`
  location to the editor; `configuredBy` is the precedent in `klangscript-libs`).
- **Why the stone rule "coerce user inputs, never `require()`" does not apply.** That rule protects the Motor from
  VALUES a user can reach: a negative oversample is coerced into something the engine can run. A Katalyst param
  handed to an Ignitor setter is not a value out of range; it is the wrong KIND of argument, which no coercion can
  make mean what the author wanted (writing it would silently set a voice slot nobody reads). It is a type error at
  the call, the same class as a lambda that returns the wrong type (`/dsl-design` checklist 13, `configuredBy`). It
  never reaches the audio path, and it is thrown as a script error naming the door, never as `require()`.
- The error rows run on JS too (checklist 13: a type check can compile away on JS).

### 4.6 `.katalystParam`

In `lang_katalyst.kt`, the four forms beside the four `katp` forms, `katalystParam` carrying the full KDoc (the ONE
home of the "which chain hears this map" rule moves with it, `katp` keeps "Alias of [katalystParam]" and
`@alias katalystParam`), mirroring how `oscparam` / `oscp` are written today. Both forms join `LangDoorFormsSpec`.

### 4.7 String or object, both doors

Script: `note("a").ignp("lpf.freq", 1200)`, `note("a").ignp(Ign.slot.lpf.freq, 1200)`,
`let cutoff = Ign.param("cutoff", 800)` then `note("a").ignp(cutoff, 1200)`; `katp("reverb.wet", 0.3)`,
`katp(Kat.slot.reverb.wet, 0.3)`. Kotlin: `pattern.ignp(IgnitorDsl.Slots.lpf.freq, 1200)`,
`pattern.katp(KatalystDsl.Slots.reverb.wet, 0.3)`. The parameter is a typealias of `Any`
(`IgnitorSlotLike`, `KatalystSlotLike`, after `IgnitorDslLike`), resolved by 4.5 on both doors, so the two doors
cannot drift. Typed Kotlin overloads that KlangScript does not see (`docs/tasks/klangscript-union-types.md` §3.5)
are not added: one function per door keeps parity a single code path. Only the param's NAME is written; its
default stays the tree's or the chain's. **UNVERIFIED**: how a native object arrives in a sprudel `Any` parameter
(`SprudelDslArg.value` as the Kotlin instance, or wrapped); the first spec row settles it.

## 5. The blog question

The posts are history: `docs/blog/howto-write-a-post.md` asks for code quoted verbatim from the tree with the file
named, and for links to the revision the post talks about, not `main`. Three posts quote `Osc`
(`2026-08-12-one-annotation-six-artifacts` 1 line, `2026-08-12-the-fundamental-lottery` 2,
`2026-09-07-loop-shape-beats-pass-count` 8), each as code of its day or as the story of a decision.

Proposal: the posts stay unchanged, and so do their built copies under `src/jsMain/resources/blog/`; none of them
presents code as "how you write it today" in a way a reader would copy and expect to run (each quotes a song or a
door at its date). The one live line is the howto itself (`howto-write-a-post.md:136`: "`klangscript` for songs,
patterns and `Osc.*`"), which becomes `Ignitor.*`. Optional: a one-line note at the foot of the three posts ("`Osc`
is called `Ignitor` since 2026-10"). **Maintainer decision** (Q5).

## 6. Workstreams and order

Shape: expand, migrate, contract. The script surface renames first while the old names still resolve as temporary
SCAFFOLDING (`@KlangScript.Constant val Osc = KlangScriptIgnitor`, `val OscSlot = KlangScriptIgnitorSlots`, and
`oscparam` / `oscp` as one-line forwards), so the call sites can migrate in parallel, in disjoint files, while every
commit stays green; the contract commit removes the scaffolding together with the guard that proves it is gone
(the "scaffolding goes when its job is done" guideline). The wire rename needs no scaffolding: it is compile
coupled and lands in one commit. Q6 is the big-bang alternative.

The coordinator owns Gradle (`/agent-fleet`): workers edit, never build; the coordinator builds after each merge
under `console/with-build-lock.sh`. The one exception is C4, whose single owner builds under the lock for its
mutation checks while only doc workers (no compile) run beside it. Stage by explicit path; check
`git diff --cached --stat` before each commit.

| commit | what | files, size | who | coupled with | verified by |
|---|---|---|---|---|---|
| C0 | baseline: corpus render at HEAD (raw doubles, hashes recorded), full test run, old `WIRE_SCHEMA_HASH` noted | none | coordinator | | |
| C1 | wire and data model: `VoiceData.ignitorParams`, `SprudelVoiceData.ignitorParams`, `ignitorParamsOrNew`, `putIgnitorParam`, `SoundValue.Dsl(ignitor)`, the `audio_be` parameter and readers, `klang`, `audio_benchmark`, `src/jvmMain` / `src/jvmTest` Kotlin (not KlangScript text), all tests, the KDoc in those files | about 100 files, 700 lines, mechanical | coordinator with IntelliJ rename refactoring, or one `opus` worker; one owner | compile coupled across `audio_bridge`, `audio_be`, `sprudel`, `klang`, `audio_benchmark`, root | all JVM tests; `compileTestKotlinJs` of every module; `audio_bridge` and `sprudel` jsTest (`WireCodecRoundTripSpec`, `WorkletWireCodecRoundTripSpec`); corpus render bit-identical; hash changed |
| C2 | expand the script surface: `KlangScriptIgnitor` (`@Object("Ignitor")`), `Ign`, `Kat`, `KlangScriptIgnitorSlots` retyped, slot groups and `KlangScriptIgnitorExtensions` renamed, `toString` and `configuredBy` names, stdlib export block; sprudel `ignitorParam` / `ignp` (file renamed) with `oscparam` / `oscp` forwards; the scaffolding constants; the KDoc examples in these main files; the tests that pin FQCNs or type names (`KlangDocsRegistryTest`, `CompletionProviderTest`, `StdlibDocsInferenceTest`, `GeneratedRegistrationTest`, `EmissionHelpersTest`) | about 20 main files, 8 test files, 450 lines | one `opus` worker | KSP output; FQCN pins | `klangscript-libs` all tests, `klangscript-ksp` test, `sprudel` jvmTest, root jvmTest (`DslDocExamplesSpec`, `BuiltInSongsSmokeTest` stay green through the scaffolding); JS compile |
| C3 | migrate the call sites, four workers in parallel on disjoint files: (a) songs, `FrozenSongs` / `FrozenPieces` with their dated note, `SongBenchmarkCases`, `Cli`, `song-snapshots.sh` header; (b) `klangscript-libs` tests; (c) sprudel main KDoc not done in C2 and sprudel tests (excluding `LangKatalystParamSpec`, owned by C4), `klangscript` / `klangscript-ksp` / `klangscript-annotations` / `common` comments and test words; (d) `audio_bridge` / `audio_be` / `klang` prose and strings (`"Ignitor.variants"`), frontend comments, `tut_Thickness` KDoc | about 110 files, 1,300 lines | four `sonnet` workers, no Gradle | none at compile level (the scaffolding resolves both spellings) | coordinator: full suite once after the four merge; corpus render bit-identical (the songs now spell the new names) |
| C4 | the new pieces: `KatalystParam`, `KatalystDsl.Slots` and `classic` built from it, `Katalyst.slot` groups, `Katalyst.param` retyped, `toKatalystKnob`, the `toIgnitorDsl` branch, the two resolvers and errors, `katalystParam` (four forms), string or object on `ignitorParam` / `ignp` / `katalystParam` / `katp`, all specs of section 7 for them | about 10 main files (3 new), 6 spec files, 600 lines | one `opus` owner, builds under the lock, mutation-checks every new test | `KatalystBuilders.kt`, `KlangScriptKatalyst.kt`, `lang_katalyst.kt`, `lang_synthesis_ignitorparam.kt`, `KatalystDsl.kt` | new specs, mutation-checked; full suite; corpus render bit-identical (`classic` now from `Slots`) |
| D1 | pure rename in docs, in parallel with C4: (a) skills; (b) `docs/plans`, `docs/tasks`, `docs/instrument-prototypes.md`, module `CLAUDE.md` / `ref` / `language-features` docs; (c) whitepaper and figures, `README.MD`, `.idea` dictionary, `howto-write-a-post.md:136` | about 60 files, 550 lines | three `sonnet` workers, no Gradle | none | reviewer reads; the whitepaper code check of section 7 |
| D2 | the docs of the NEW surface, after C4, written from the code: `Ign` / `Kat`, `Katalyst.slot`, `katalystParam`, string or object, the distinct types, in `ignitor-reference.md`, `sprudel-reference.md`, the whitepaper's Katalyst section (`Katalyst.param` and `katp` at lines 1909 to 1971), `dsl-design` §5 (the known debt closed) | about 10 files | one `sonnet` worker or the `writer` agent for the whitepaper (`/public-voice`) | C4 landed | reviewer verifies every claim against the code |
| C5 | contract: remove the scaffolding (`Osc`, `OscSlot` constants, `oscparam` / `oscp` forwards); add the old-names guard; `CLAUDE.md` register (the guardrail row's spec name, the retired-list wording, a new retired entry: `Osc`, `OscSlot`, `oscp`, `oscparam`, the wire field `oscParams`, `SoundValue.Osc`, `KlangScriptOsc`, `KlangScriptOscSlot`, `KlangScriptOscExtensions`); one History line in `klangscript/MEMORY.md`, `sprudel/MEMORY.md`, `audio/MEMORY.md` and their "now" sections updated | about 10 files | coordinator | everything above | guard spec (mutation-checked); full suite; final corpus render on the final tree; maintainer plays one song in the browser (worklet path, new hash) |

Order: C0, C1, C2, C3, then C4 and D1 in parallel, then D2, then C5. C1 and C2 touch disjoint names and could swap;
C1 first keeps the biggest mechanical change on a quiet tree. The tree builds and every test passes after each
commit; the docs lag the code between C3 and D1 and are closed by C5's guard.

Every worker brief carries: the opening line of `/agent-fleet` (world-class at the role, mistakes are fine, hiding one
is not), "Do NOT run Gradle; do NOT spawn sub-agents", the name map of section 2, the line of 2.12, the
word-aware rename rule of 2.12, and its exact file list.

## 7. Verification

### 7.1 The guards

- **Old names are gone**: `src/jvmTest/kotlin/RetiredIgnitorNamesSpec.kt`, walking the tree like `LexikonSpec`
  (repo root, `kt`, `kts`, `md`, `MD`, `html`, `xml`, `py`, `sh`; skipping `build`, `.git`, `cache`,
  `kotlin-js-store`, `node_modules`, `tmp`) for `\bOsc\b`, `\bOscSlot\b`, `KlangScriptOsc`, `\boscp\b`,
  `\boscparam\b`, `\boscParams\b`, `putOscParam`, `oscParamsOrNew`, `SoundValue\.Osc`. Allowlist: the history paths
  of 2.10 and the "Retired" section of `CLAUDE.md`, each entry with its reason; a new entry is a maintainer decision.
  Guard the guard: it fails when it scanned fewer files than a floor, and when the pattern does NOT match a known
  line in an allowlisted history file. Mutation: plant `Osc.sine()` in a song, red; remove one allowlist entry, red.
  A runtime "unknown name" spec for `Osc` / `oscp` would add nothing the text guard misses (a re-added registration
  spells the name in source) and is not written.
- **Alias specs** (`klangscript-libs` commonTest, runs on JVM and JS): for EVERY member the docs registry lists on
  `Ignitor`, `Ign.<member>` resolves to the same registration (a table, not a sample); `Ign.slot.lpf.freq` is the
  same instance as `Ignitor.slot.lpf.freq` and `IgnitorDsl.Slots.lpf.freq`; `Kat(k => k.reverb(0.2))` equals
  `Katalyst(k => k.reverb(0.2))`; `Kat.slot.reverb.wet` is `Katalyst.slot.reverb.wet` is
  `KatalystDsl.Slots.reverb.wet`. Mutation: delete the `Ign` constant, red. Intel (`klangscript-libs` jvmTest):
  completion after `Ign.` offers the `Ignitor` members, `Kat(` shows the invoke signature, the docs symbols `Ign` and
  `Kat` carry a category (the `FreqAccessorIntelSpec` pattern).
- **Door-parity specs** (`/dsl-design` §3, §4): `ignp` with a string, with `Ign.slot.*`, with `Ign.param(...)`, and
  the Kotlin door with `IgnitorDsl.Slots.*` write the same bag entry; the same four for `katp` / `katalystParam`
  with `KatalystDsl.Slots.*`; `katalystParam` and `katp`, `ignitorParam` and `ignp` are the same door
  (`LangDoorFormsSpec` rows for all four forms of all four names). `KlangScriptKatalystDoorParitySpec:535`, which
  asserts `Katalyst.param == Osc.param` today, is inverted: the two are distinct types, and `k.reverb(0.5,
  Kat.param("room", 2))` builds the same chain as the Kotlin door. Mutation: make `Katalyst.param` return the bare
  `Param`, red.
- **Script-error specs** (commonTest, so JS too, checklist 13): `ignp(KatalystParam)`, `katp(IgnitorDsl.Param)`,
  `ignitorParam` / `katalystParam` likewise, a sound or an expression as the key, the Q2 and Q8 rows if decided,
  each on both doors, each asserting the message names the right door and the right fix. Mutation per row: delete
  or swap the branch, red.
- **No sound changed**: the corpus render of `audio/ref/verification.md` (built-in songs plus `FrozenSongs` and
  `FrozenPieces`, 256 cycles, 48 kHz, the wall-clock seeds pinned, raw doubles, HEAD in a worktree against the
  tree), every row identical, after C1, C3, C4 and on the final tree. Engagement control: a mutant that makes
  `ignp` write a wrong key must move the rows of the songs that call it (`Kokon`, `DerSchmetterling`,
  `ATruthWorthLyingFor`, and the frozen `derSchmetterling` texts) and leave the others; predict the rows first.
  Renders run as plain `java` on a snapshotted classpath, several at a time (`/review-loop`, the rung rule).
- **The rest of the suite that this touches**: `BuiltInSongsSmokeTest`, `DslDocExamplesSpec` (every KDoc example
  runs), `ClassicSlotParamsSpec`, `LangKatalystParamSpec`, `KatalystDoorFillRenderSpec`, the wire codec round
  trips on JS, `StdLibIgnitorTest` (the `min` / `max` crossing guard named in `CLAUDE.md`).
- **The whitepaper's code** is not executed by any spec: in D1 and D2 the coordinator extracts every `klangscript`
  `<pre>` block once (a scratch script, not committed) and evaluates it, and the reviewer reads the prose.

### 7.2 The review plan

`/review-loop` applies per commit group; the ladder and the two-phase rounds as written there.

| unit | diff | reviewers | focus |
|---|---|---|---|
| R-A | C1 | coding + audio (it is a wire path) | nothing but names changed; every `oscParams` reader still reads the same map; the hash change; no allocation moved |
| R-B | C2 + C3 as one diff | coding | the line of 2.12 per name, word-aware rewrites (prose that lost meaning, an `osc` that was an oscillator), the scaffolding marked as such; generated batch rule: review the prose, trust the structure |
| R-C | C4 | coding, DSL checklist BY TABLE (every setter name x every key kind x both doors x JVM and JS against 4.5) | the type split, the error messages, `classic` from `Slots` unchanged, surplus (`/review-loop` coding template) |
| R-D | D1 + D2 | one reviewer, `/public-voice` grep list for the whitepaper and README | claims about the new surface verified against the code, no em-dashes |
| R-E | C5 | coding | the guard and its allowlist, the register lines |

Only CRITICAL and MAJOR loop; the safety valve after two unclean rounds stands.

## 8. Risks

- **R1 User songs outside the repo.** The browser keeps edited songs (`CodeSongPage`'s localStorage-backed stream);
  any that say `Osc` or `oscp` stop with an "unknown name" error after the deploy. Removed, not deprecated, says
  the rule; pre-launch, the affected author is mostly the maintainer. A "renamed to Ignitor" hint in the error is
  Q9.
- **R2 Two things called `Ignitor`.** The script object `Ignitor` and the runtime interface `Ignitor` in `audio_be`
  (`Ignitor.min`, `Ignitor.svf`) now share the word, which is the point (one concept), but the `CLAUDE.md`
  guardrail on `min` / `max` names "the runtime `Ignitor.min`/`max` primitives"; docs must say "the script object"
  or "the runtime primitive" where both could be meant.
- **R3 A scripted rename hits prose.** `Osc` and `oscp` are not English words, so the risk is lower than batch G's,
  but `osc` inside identifiers and "osc-param" prose need the 2.12 line; R-B reviews it.
- **R4 The schema hash.** A browser holding the old worklet bundle reports a schema mismatch until reload; expected,
  and the message says so.
- **R5 The UNVERIFIED claims** (3.2, 4.1, 4.4, 4.5, 4.7, the stdlib export block) are each settled by the first
  spec row that touches them; a refuted one changes the design before C4 continues.
- **R6 Found while surveying, not in scope**: `Ignitor.variants()` with no children reaches
  `require(children.isNotEmpty())` in the engine (`IgnitorDslRuntime.kt:251`), a user-reachable `require` against
  the stone rule; the door should coerce or raise a script error. A separate task.
- **R7 Found while surveying**: `README.MD` lines 71 and 130 still list the retired `Master` and `Pipeline`; D1 fixes
  them in the same edit (published writing, `/public-voice`).

## 9. Open questions for the maintainer

**All eleven DECIDED 2026-10-03 (maintainer): every recommendation accepted.** Q7: yes, `SoundValue.Dsl(ignitor)`.
Q5: the posts stay unchanged as history, only the howto line changes. Work runs on branch `ignitor-katalyst-naming`.

1. **Q1 Spelling in songs and examples.** Recommendation: songs use `Ign.` and `ignp` (same length as `Osc.` and
   `oscp`, alignment kept); KDoc, docs, whitepaper and skills use `Ignitor` and `ignitorParam` / `ignp` as each
   page's voice prefers, the figure boxes `Ign.`.
2. **Q2 Katalyst knobs and Ignitor params.** Today a Katalyst knob accepts `Osc.param(...)` and listens to `katp`. Make
   a bare Ignitor param there a script error (the mirror of the setter error)? Recommendation: yes.
3. **Q3** `Kat.param(...).mul(2)` becomes a script error (a `KatalystParam` is not an `IgnitorDsl`) instead of the
   documented silent fold. Accept? Recommendation: yes, it removes a trap and a paragraph.
4. **Q4** Keep the `Osc*Builder` names (they configure oscillators)? Recommendation: keep.
5. **Q5** The three blog posts unchanged (history), only the howto line updated? Optional footnote?
6. **Q6** Expand, migrate, contract with a temporary `Osc` alias inside the series (removed in C5), or one big-bang
   commit for the script surface? Recommendation: expand and contract (parallel workers, green commits).
7. **Q7** `SoundValue.Osc` becomes `SoundValue.Dsl(ignitor)`, mirroring `KatalystValue.Dsl(katalyst)`?
8. **Q8** A number as the slot (`ignp(42, x)` writes slot `"42"` today): error? Recommendation: yes.
9. **Q9** An "Osc is now Ignitor" hint in the unknown-name error? Recommendation: no (removed, not deprecated).
10. **Q10** sprudel's bus doors reading their keys from `KatalystDsl.Slots`: now or later? Recommendation: later.
11. **Q11** `docs/strategy/**` and the funding skeleton's decision rows count as history? Recommendation: yes.
