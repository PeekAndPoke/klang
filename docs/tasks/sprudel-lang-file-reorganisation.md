# Sprudel: dissolve `lang/addons/`, split `lang_*.kt` into small files

Opened 2026-09-07. Maintainer request: "our sprudel impl has diverged significantly from Strudel,
so the whole addons concept does not even make sense anymore" plus "some files are enormous, which
is not ideal for coding agents".

Status: IN EXECUTION since 2026-09-07. Written to be executed by an Opus agent with no prior context.

**Revised 2026-09-07 after the accessor rework landed** (commits `d9b6c8e3` batch E, `0c535555`
batch F, `95705370` batch G). Compound doors became single objects with slot children, which
collapsed whole runs of per-knob sections into one: `lang_dynamics.kt` fell 2 198 -> 1 556,
`lang_tonal.kt` 2 294 -> 1 764, `lang_synthesis.kt` 606 -> 226, `lang_body.kt` 216 -> 122,
`lang_vowel.kt` 211 -> 120. The dynamics, tonal, synthesis and effects tables below are the revised
ones. Everything else measured unchanged. The conflict risk this plan was written around is gone:
that work is committed and the tree is clean.

## The two goals

1. **Dissolve `lang/addons/`.** The split was "what Strudel has" vs "what we added". Sprudel is its
   own language now, so the distinction carries no meaning: it only makes a reader guess which of
   two directories a function lives in. The eight addon files move one level up into
   `io.peekandpoke.klang.sprudel.lang`, and the `addon` marker in `@tags` goes with them.
2. **Many small files instead of a few huge ones.** `lang_structural.kt` is 4 652 lines,
   `lang_pattern_picking.kt` 2 390, `lang_tonal.kt` 2 294, `lang_dynamics.kt` 2 198. An agent that
   needs one function pays for all of them. Target: **no lang file over ~700 lines**, named
   `lang_<group>_<subgroup>.kt`.

Result: `lang/` plus `lang/addons/` is 30 files and 26 495 lines today. Twenty-three of them get
split or merged into **62 files, largest ~664 lines**. Six are already right and stay untouched:
`lang.kt`, `lang_helpers.kt`, `KlangScriptStrudelLib.kt`, and the three small sectioned files
`lang_conditional.kt` (401), `lang_master.kt` (111), `lang_pipeline.kt` (108). One, `lang_misc.kt`,
is deleted: it is a header and a package line.

## The inventory, as it stands

Measured 2026-09-07 across the 30 files in `lang/*.kt` and `lang/addons/*.kt`:

| annotation | count |
|---|---|
| `@KlangScript.Function` | 1 171 |
| `@KlangScript.Constant` | 65 |
| `@KlangScript.Property` | 58 |
| `@KlangScript.Object` (with `@KlangScript.Invoke`) | 55 |

That is 378 distinct script names: most have four doors (`SprudelPattern.x`, `String.x`, bare `x`
returning a `PatternMapperFn`, and `PatternMapperFn.x`), and many carry a short alias with four
more. They are organised into 326 banner sections across 26 files, and the section, not the
function, is the unit this plan moves. The `@category` KDoc tag spreads over eleven values today: structural 207, tonal
109, dynamics 52, random 51, effects 45, continuous 43, tempo 38, arithmetic 32, sampling 29,
synthesis 16, conditional 8. The file groups below follow those categories, with `filters`,
`picking` and `synthesis` broken out because they are large enough to earn their own name.

To rebuild the inventory at any time:

```bash
grep -rn '^// -- ' sprudel/src/commonMain/kotlin/lang/          # sections
grep -rho '@KlangScript\.[A-Za-z]*' sprudel/src/commonMain/kotlin/lang/ | sort | uniq -c
grep -rho '@category .*' sprudel/src/commonMain/kotlin/lang/ | sort | uniq -c | sort -rn
```

## What must NOT change

- **No behaviour change. Zero.** This is a pure move. No renames of script names, no signature
  changes, no KDoc rewrites beyond what an @tags edit needs, no "while I'm here" fixes.
  If a function looks wrong, leave it and note it at the end of the report.
- **No test file may change its assertions.** Phase 1 changes test `package` lines and imports.
  Phase 2 changes no test file at all.
- Every script name registered today is registered after, with the same doors. `LangRetiredDoorsSpec`
  and `SprudelDocsSpec` are the guards.

## Facts you need before you start (all verified 2026-09-07)

- **Registration is annotation-driven, not list-driven.** `KlangScriptStrudelLib.kt` calls
  `registerSprudelGenerated()`; the KSP processor emits
  `build/generated/ksp/metadata/commonMain/.../GeneratedSprudelRegistration.kt` from
  `@KlangScript.*` annotations. There is **no hand-maintained registry to update** when a function
  changes file. The generated file only references declarations by import, so a package move
  regenerates itself.
- Every lang file needs this exact preamble; a new file without `@file:KlangScript.Library("sprudel")`
  contributes nothing to the library and the failure is silent at compile time:

  ```kotlin
  /*
   * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
   * SPDX-License-Identifier: AGPL-3.0-or-later
   */

  @file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
  @file:KlangScript.Library("sprudel")

  package io.peekandpoke.klang.sprudel.lang
  ```
- **Files are already sectioned.** Almost every function block starts with a
  `// -- name ----------...` banner comment running to column 120. The banner plus everything up to
  the next banner is the unit you move: it carries the private `applyX` / `xMutation` helpers, the
  KDoc, and all four doors (`SprudelPattern.x`, `String.x`, `x`, `PatternMapperFn.x`).
- **Line counts in this document were measured on 2026-09-07 and will drift.** They exist to size
  the work, not to address it. Address sections by their banner text, never by line number.
- **Another agent is reworking the DSL surface on this same branch** (field accessors, compound
  slot objects; see `docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md` and
  `docs/tasks-archive/2026-09/20260907-sprudel-accessors-compound-slots.md`). Sections may be renamed, added or deleted
  under you. Therefore: **re-derive the section list of a file at the moment you split it**
  (`grep -n '^// -- ' <file>`), and treat this document's tables as the grouping decision, not as
  a manifest. A section that no longer exists is skipped, silently. A section that is new goes into
  the file whose group it belongs to; say so in the report.
- No detekt or `allWarningsAsErrors` in the Gradle build, so a redundant same-package import is not
  a build failure. Remove them anyway.
- **A file's name does not have to match its `@category` KDoc tag.** `silence` is
  `@category continuous` and lands in `lang_structural_sources.kt`. That is intended: categories are
  the documentation taxonomy, file names are the reading taxonomy. Do not "fix" either to match.

## Phase 1: dissolve `addons/` (one commit, purely mechanical)

Do this first and completely. It changes package and location, nothing else. Afterwards every lang
declaration lives in `io.peekandpoke.klang.sprudel.lang` and Phase 2 needs **no import edits
anywhere in the repo**, because every move is then within one package.

1. `git mv sprudel/src/commonMain/kotlin/lang/addons/*.kt sprudel/src/commonMain/kotlin/lang/`
   (8 files: `lang_arithmetic_addons.kt`, `lang_continuous_addons.kt`, `lang_dynamics_addons.kt`,
   `lang_filters_addons.kt`, `lang_osc_addons.kt`, `lang_snd_addons.kt`,
   `lang_structural_addons.kt`, `lang_tempo_addons.kt`). Keep the `_addons` names for now; Phase 2
   deletes them. `git mv` keeps blame.
2. In those 8 files: `package io.peekandpoke.klang.sprudel.lang.addons` ->
   `package io.peekandpoke.klang.sprudel.lang`, then delete every
   `import io.peekandpoke.klang.sprudel.lang.<X>` line that has become same-package.
3. `git mv sprudel/src/commonTest/kotlin/lang/addons/*.kt sprudel/src/commonTest/kotlin/lang/`
   (36 spec files; verified: no filename collides with an existing spec in `lang/`). Change their
   `package io.peekandpoke.klang.sprudel.lang.addons` -> `...lang`, and delete the
   `import io.peekandpoke.klang.sprudel.lang.<X>` lines that became same-package. None of the 36
   imports anything from `lang.addons` today (they relied on same-package resolution), so after the
   package change they resolve again with no new import.
4. Rewrite the remaining references to the old package. The complete list today:
   - `sprudel/src/commonMain/kotlin/lang/lang_structural.kt`
   - `sprudel/src/commonMain/kotlin/lang/parser/MnPatternToSprudelPattern.kt`
   - `src/commonMain/kotlin/TestKotlinPatterns.kt` (root module)
   - `sprudel/src/commonTest/kotlin/lang/LangDefaultQSpec.kt`, `LangFieldAccessorsSpec.kt`,
     `LangFiltersSpec.kt`, `LangInsideSpec.kt`

   Re-derive it rather than trusting the list:
   `grep -rn "sprudel\.lang\.addons" --include=*.kt .`
   Each is `import io.peekandpoke.klang.sprudel.lang.addons.X` -> `...lang.X`, and where that
   import is now same-package (all of the sprudel-internal ones), delete it instead.
5. Remove the `addon` marker from `@tags` in the moved files (109 occurrences today). The tag is a
   documentation-only artifact: nothing in the UI, editor or docs browser filters on it (verified by
   a repo-wide grep). Three assertions in
   `sprudel/src/commonTest/kotlin/lang/docs/SprudelDocsSpec.kt` check
   `tagDoc.tags shouldContain "addon"` for `tag`, `tweak` and `tweaks`; delete those three
   assertions with the tag. **Ask the maintainer before doing step 5 if anything about it surprises
   you**; steps 1 to 4 stand on their own and can ship without it.
6. Delete `sprudel/src/commonMain/kotlin/lang/lang_misc.kt`. It holds a copyright header and a
   package line, nothing else.
7. Verify, then commit as one change.

There is no name collision between the two packages: a receiver+name comparison of every public
declaration in `lang/` against every one in `lang/addons/` finds zero overlap. Kotlin resolves an
explicit import ahead of a same-package declaration, so `lang_structural.kt`'s
`import kotlin.math.log2` keeps beating the sprudel `log2`, and `kotlin.math.abs` keeps beating the
sprudel `abs` value. Nothing to do about either; the compiler is the check.

## Phase 2: split into `lang_<group>_<subgroup>.kt`

One commit per group below. Order is chosen so the easy, uncontested files come first and the ones
the other agent is actively editing come last; **if `git status` shows a group's file dirty from the
other agent's work, skip that group and take the next one.**

Recommended order: `euclid`, `sample`, `random`, `arithmetic`, `continuous`, `tempo`, `picking`,
`synthesis`, `structural`, then `dynamics`, `effects`, `filters`, `tonal` (the last four are the
accessor rework's territory).

### The procedure for one group

1. `grep -n '^// -- ' <source file>` to get the live section list. Reconcile with the table below.
2. Create each target file with the exact preamble from "Facts you need", then move whole sections
   into it **in the order the table lists them** (that is source order, which keeps diffs readable).
3. Give each new file its imports: start from the source file's import block, drop what the new file
   does not use. `import io.peekandpoke.klang.sprudel.lang.X` is never needed, it is the same package.
4. Delete the source file when its last section has left. `lang_arithmetic.kt`,
   `lang_structural.kt`, `lang_tonal.kt` and the other originals all disappear; nothing keeps a
   bare group name.
5. `./gradlew :sprudel:jvmTest` must be green before the commit. A missing
   `@file:KlangScript.Library("sprudel")` shows up here as a wall of dispatch failures, not as a
   compile error.

### Private helpers that cross a new file boundary

Seven `private` top-level helpers are used by sections that land in different files. Change each to
`internal` (module scope, so no import is needed) and leave it in the file named below. Everything
else stays `private`.

| helper | declared in | lands in | also used by |
|---|---|---|---|
| `reifyLookup` | `lang_pattern_picking.kt` | `lang_picking_core.kt` | pick / out / inhabit / restart / reset |
| `extractIndex` | `lang_pattern_picking.kt` | `lang_picking_core.kt` | pick / out / inhabit / restart / reset |
| `extractKey` | `lang_pattern_picking.kt` | `lang_picking_core.kt` | pick / out / inhabit / restart / reset |
| `applyIrand` | `lang_random.kt` | `lang_random_sources.kt` | `lang_random_shuffle.kt` (`scramble`) |
| `applySeq` | `lang_structural.kt` | `lang_structural_seq.kt` | `lang_structural_chunk.kt` (`chunk`) |
| `applySound` | `lang_tonal.kt` | `lang_tonal_sound.kt` | `lang_tonal_note.kt` (`note`) |
| `soundMutation` | `lang_tonal.kt` | `lang_tonal_sound.kt` | `lang_tonal_note.kt` (`note`) |

`applyArithmetic` and `applyUnaryOp` (preamble of `lang_arithmetic.kt`) are already `internal`;
they stay in `lang_arithmetic_math.kt` and the other three arithmetic files use them as-is.

### Sections that live before the first banner

Four files have declarations above the first `// -- ` banner. The tables call this `(preamble)`.

| file | what is up there |
|---|---|
| `lang_arithmetic.kt` | `internal fun applyArithmetic`, `internal fun applyUnaryOp` |
| `lang_tempo.kt` | `internal fun applyTimeShift` |
| `lang_tonal.kt` | `String.cleanScaleName` and `SprudelVoiceData.resolveNote`, both public. Split them: `cleanScaleName` to `lang_tonal_scale.kt` (its other caller is `scaleTranspose`), `resolveNote` to `lang_tonal_note.kt`. They stay public, so the cross-file calls need nothing. `VoiceDataSpec.kt` imports `resolveNote` by its package name, which does not change. |
| `lang_pattern_picking.kt` | four private helpers **and all twelve `pick()` doors**: the only preamble that holds public API. Split it: `reifyLookup`, `extractIndex`, `extractKey` to `lang_picking_core.kt` as `internal`; `dispatchPick` and the `pick()` doors to `lang_picking_pick.kt`, where `dispatchPick` stays `private`. |

For the other files `(preamble)` is just the header plus imports and is reproduced, not moved.

### The grouping

Reading the tables: each row is one new file. `(preamble)` in a section list means "everything in
that source file above its first banner", which for most files is only the header and the imports
and is reproduced rather than moved (the four exceptions are in the table above). Where a row draws
from two source files, its sections are listed one source file at a time. `~lines` is the measured
size of the moved sections on 2026-09-07, before the per-file header and imports are added.


#### arithmetic: from `lang_arithmetic.kt`, `lang_arithmetic_addons.kt` (1786 lines) into 4 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_arithmetic_bitwise.kt` | 352 | `bitAnd() (Bitwise AND)`, `bitOr() (Bitwise OR)`, `bitXor() (Bitwise XOR)`, `bitShl() (Bitwise Left Shift)`, `bitShr() (Bitwise Right Shift)` |
| `lang_arithmetic_compare.kt` | 370 | `lt() (Less Than)`, `gt() (Greater Than)`, `lte() (Less Than or Equal)`, `gte() (Greater Than or Equal)`, `eq() (Equal)`, `eqt() (Truthiness Equal)`, `ne() (Not Equal)`, `net() (Truthiness Not Equal)`, `and() (Logical AND)`, `or() (Logical OR)`, `(preamble)`, `not` |
| `lang_arithmetic_math.kt` | 604 | `(preamble)`, `add()`, `sub()`, `mul()`, `div()`, `mod()`, `pow()`, `log2()` |
| `lang_arithmetic_numeric.kt` | 460 | `round()`, `floor()`, `ceil()`, `negateValue`, `oneMinus`, `abs`, `min`, `max`, `clamp` |

#### continuous: from `lang_continuous.kt`, `lang_continuous_addons.kt` (1214 lines) into 4 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_continuous_clock.kt` | 219 | `time`, `(preamble)`, `cps()`, `rpm()`, `bpm()`, `Time of Day Functions` |
| `lang_continuous_range.kt` | 452 | `(preamble)`, `toBipolar`, `fromBipolar`, `range`, `rangex`, `range2` |
| `lang_continuous_waves.kt` | 484 | `signal`, `steady`, `sine / sine2`, `cosine / cosine2`, `saw / saw2`, `isaw / isaw2`, `tri / tri2`, `itri / itri2`, `square / square2`, `perlin / perlin2`, `berlin / berlin2` |
| `lang_structural_sources.kt` | +59 | `silence / rest / nothing`. This file is the structural group's; create it here if the structural group has not run yet, otherwise append. |

#### dynamics: from `lang_dynamics.kt`, `lang_dynamics_addons.kt` (1 743 lines) into 5 files

Revised: batch G folded `spread()` and `panSpread()` into the `unison` object, and the three duck
knobs into one `duck` section, so `lang_dynamics.kt` is 12 sections now, not 16.

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_dynamics_level.kt` | 446 | `gain()`, `pan()`, `velocity() / vel()`, `postgain()` |
| `lang_dynamics_compressor.kt` | 179 | `compressor` |
| `lang_dynamics_unison.kt` | 305 | `unison`, `density() / d()` |
| `lang_dynamics_adsr.kt` | 435 | `ADSR stages`, `ADSR adsr()`, `ADSR curves` from `lang_dynamics.kt`; the `adsrOn()` / `adsrOff()` section from `lang_dynamics_addons.kt` |
| `lang_dynamics_orbit.kt` | 332 | `orbit() / o()`, `duck` from `lang_dynamics.kt`; the `cylinder()` alias section from `lang_dynamics_addons.kt` |

#### effects: from `lang_effects.kt`, `lang_body.kt`, `lang_vowel.kt` (1 332 lines) into 6 files

Revised: `lang_body.kt` and `lang_vowel.kt` are one section each now (122 and 120 lines), so each
is a straight rename, not a split.

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_effects_distortion.kt` | 319 | `distort, the amount slot`, `distort.oversample`, `distort, the shape slot`, `crush, the amount slot`, `crush.oversample`, `coarse, the amount slot`, `coarse.oversample` |
| `lang_effects_reverb.kt` | 324 | `room, the wet slot`, `room.size`, `room.fade`, `room.lowpass`, `room.dim`, `iresponse() / ir()` |
| `lang_effects_delay.kt` | 139 | `delay, the wet slot`, `delay.time`, `delay.feedback`, `delay.cap` |
| `lang_effects_modulation.kt` | 308 | `phaser, the rate slot`, `phaser.wet`, `phaser.floor`, `phaser.center`, `phaser.sweep`, `tremolo.sync`, `tremolo.depth`, `tremolo.skew`, `tremolo.phase`, `tremolo, the shape slot` |
| `lang_effects_body.kt` | 122 | the whole of `lang_body.kt` (one `body` section); rename the file, do not split it |
| `lang_effects_vowel.kt` | 120 | the whole of `lang_vowel.kt` (one `vowel` section); rename the file, do not split it |

#### filters: from `lang_filters.kt`, `lang_filters_addons.kt` (867 lines) into 4 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_filters_bpf.kt` | 206 | `bpf`, `bandpass` |
| `lang_filters_hpf.kt` | 220 | `hpf`, `highpass` |
| `lang_filters_lpf.kt` | 241 | `(preamble)`, `lpf`, `lowpass` |
| `lang_filters_notch.kt` | 200 | `(preamble)`, `notch` |

#### euclid: from `lang_euclid.kt` (842 lines) into 2 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_euclid_basic.kt` | 476 | `(preamble)`, `euclid()`, `euclidRot()`, `bjork()` |
| `lang_euclid_legato.kt` | 366 | `euclidLegato()`, `euclidLegatoRot()`, `euclidish()` |

#### picking: from `lang_pattern_picking.kt` (2390 lines) into 7 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_picking_core.kt` | ~210 | `(preamble)` up to the first `@KlangScript.Function`: `reifyLookup`, `extractIndex`, `extractKey` (each promoted to `internal`), plus the file's shared imports |
| `lang_picking_pick.kt` | ~480 | the rest of `(preamble)`, that is the twelve `pick()` doors and `dispatchPick`, then `pickmod()` |
| `lang_picking_out.kt` | 387 | `pickout()`, `pickmodOut()` |
| `lang_picking_inhabit.kt` | 527 | `inhabit()`, `pickSqueeze()`, `inhabitmod()`, `pickmodSqueeze()`, `squeeze()` |
| `lang_picking_restart.kt` | 383 | `pickRestart()`, `pickmodRestart()` |
| `lang_picking_reset.kt` | 382 | `pickReset()`, `pickmodReset()` |
| `lang_picking_f.kt` | 189 | `pickF()`, `pickmodF()` |

`dispatchPick`, `dispatchPickOuter`, `dispatchInhabit`, `applyInhabitTopLevel`, `dispatchPickRestart`
and `dispatchPickReset` each stay `private`: every one of their callers lands in the same file as
the declaration. Only the three in `lang_picking_core.kt` become `internal`.

#### random: from `lang_random.kt` (1769 lines) into 5 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_random_choose.kt` | 436 | `chooseWith()`, `chooseInWith()`, `choose()`, `chooseOut()`, `chooseIn()`, `choose2()`, `chooseCycles()`, `randcat()`, `wchoose()`, `wchooseCycles()`, `wrandcat()` |
| `lang_random_degrade.kt` | 336 | `degradeBy()`, `degrade()`, `degradeByWith()`, `undegradeBy()`, `undegradeByWith()`, `undegrade()` |
| `lang_random_shuffle.kt` | 118 | `shuffle()`, `scramble()` |
| `lang_random_sometimes.kt` | 450 | `sometimesBy()`, `sometimes()`, `often()`, `rarely()`, `almostNever()`, `almostAlways()`, `never()`, `always()`, `someCyclesBy()`, `someCycles()` |
| `lang_random_sources.kt` | 429 | `(preamble)`, `Helpers`, `seed()`, `withSeed()`, `rand / rand2 / randCycle`, `brand() / brandBy()`, `irand()`, `randL()`, `randrun()` |

#### sample: from `lang_sample.kt` (1045 lines) into 3 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_sample_loop.kt` | 487 | `loop()`, `loopBegin() / loopb()`, `loopEnd() / loope()`, `loopAt()`, `loopAtCps()` |
| `lang_sample_playback.kt` | 420 | `(preamble)`, `begin()`, `end()`, `speed()`, `unit()`, `cut()` |
| `lang_sample_slice.kt` | 138 | `slice()`, `splice()` |

#### structural: from `lang_structural.kt`, `lang_structural_addons.kt` (5542 lines) into 13 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_structural_cat.kt` | 597 | `stepcat() / timeCat()`, `polyrhythm()`, `cat()`, `slowcatPrime()`, `polymeter()`, `polymeterSteps()` |
| `lang_structural_chunk.kt` | 429 | `chunk()`, `chunkBack() / chunkback()`, `fastChunk() / fastchunk()`, `chunkInto()`, `chunkBackInto()` |
| `lang_structural_echo.kt` | 300 | `echo() / stut()`, `echoWith() / stutWith()` |
| `lang_structural_iter.kt` | 457 | `iter()`, `iterBack()`, `invert() / inv()`, `applyN()`, `pressBy()`, `press()` |
| `lang_structural_layer.kt` | 447 | `jux()`, `juxBy()`, `off()`, `superimpose()`, `layer()`, `merge()` |
| `lang_structural_mask.kt` | 352 | `struct()`, `structAll()`, `mask()`, `maskAll()`, `filter()`, `filterWhen()` |
| `lang_structural_mute.kt` | 280 | `hush() / bypass() / mute()`, `solo()` |
| `lang_structural_repeat.kt` | 343 | `repeatCycles()`, `extend()`, `timeLoop()`, `repeat()` |
| `lang_structural_seq.kt` | 427 | `seq()`, `stack()`, `arrange()`, `stackBy()`, `stackLeft()`, `stackRight()`, `stackCentre()`, `sequenceP()` |
| `lang_structural_sources.kt` | 664 | `gap()`, `mini()`, `pure()`, `run()`, `binaryN()`, `binary()`, `binaryNL()`, `binaryL()` from `lang_structural.kt`; `morse()` from `lang_structural_addons.kt`; `silence / rest / nothing` from `lang_continuous.kt` (see the continuous group: whichever group runs first creates this file, the other appends) |
| `lang_structural_steps.kt` | 492 | `segment()`, `ratio`, `pace() / steps()`, `take()`, `drop()` |
| `lang_structural_tag.kt` | 262 | `tag()`, `tweak()`, `tweaks()` |
| `lang_structural_window.kt` | 551 | `zoom()`, `within()`, `linger()`, `bite()`, `ribbon()` |

#### synthesis: from `lang_synthesis.kt`, `lang_osc_addons.kt`, `lang_snd_addons.kt` (1 660 lines) into 4 files

Revised: batch G folded the five FM knobs into one `fm` object, so `lang_synthesis.kt` is 226 lines
and one section. It is not split; it is renamed and keeps its own file.

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_synthesis_fm.kt` | 226 | the whole of `lang_synthesis.kt` (one `fm` section); rename the file, do not split it |
| `lang_synthesis_oscparam.kt` | 429 | `oscparam() / oscp()`, `analog()`, `duty()`, `onepole()` from `lang_osc_addons.kt` |
| `lang_synthesis_snd_basic.kt` | 541 | `sndSine()`, `sndSaw()`, `sndSquare()`, `sndTriangle()`, `sndRamp()`, `sndZamp()`, `sndNoise()`, `sndBrown()`, `sndPink()`, `sndPulze()`, `sndDust()`, `sndCrackle()` |
| `lang_synthesis_snd_super.kt` | 466 | `sndPluck()`, `sndSuperPluck()`, `sndSuperSaw()`, `sndSuperSine()`, `sndSuperSquare()`, `sndSuperTri()`, `sndSuperRamp()` |

#### tempo: from `lang_tempo.kt`, `lang_tempo_addons.kt` (1712 lines) into 5 files

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_tempo_ply.kt` | 354 | `ply()`, `plyWith()`, `plyForEach()` |
| `lang_tempo_reverse.kt` | 241 | `rev()`, `revv()`, `palindrome()`, `brak()` |
| `lang_tempo_shift.kt` | 451 | `early()`, `late()`, `compress()`, `focus()`, `helpers`, `lateInCycle()`, `earlyInCycle()` |
| `lang_tempo_speed.kt` | 450 | `slow()`, `fast()`, `hurry()`, `fastGap()` from `lang_tempo.kt`; `stretchBy()` from `lang_tempo_addons.kt` |
| `lang_tempo_swing.kt` | 216 | `inside()`, `outside()`, `swingBy()`, `swing()` |

#### tonal: from `lang_tonal.kt` (1 764 lines) into 5 files

Revised: batch G folded the six pitch-envelope knobs into one `penv` object and the two vibrato
knobs into one `vibrato`, so `lang_tonal.kt` is 15 sections now, not 21. `lang_tonal_pitchenv.kt`
is no longer worth its own file; `penv` joins `vibrato` and `accelerate` in `lang_tonal_pitchmod.kt`.

| new file | ~lines | sections, in source order |
|---|---|---|
| `lang_tonal_note.kt` | 345 | `note()`, `n()`, `legato() / clip()`, `freq()`, plus `resolveNote` from the preamble |
| `lang_tonal_sound.kt` | 195 | `sound() / s()`, `bank()` |
| `lang_tonal_scale.kt` | 322 | `scale()`, `transpose()`, `scaleTranspose()`, plus `cleanScaleName` from the preamble |
| `lang_tonal_chord.kt` | 379 | `chord()`, `rootNotes()`, `voicing()` |
| `lang_tonal_pitchmod.kt` | 411 | `vibrato`, `penv`, `accelerate()` |

## Phase 3: the documents that describe the old layout (one commit)

| file | change |
|---|---|
| `sprudel/ref/dsl-addons.md` | delete. Its content is the addon concept: where addon files live, the `addon` tag rule, the four-form example. Move the one piece worth keeping, the four-form door example pointing at `tag()`, into `ref/dsl-conventions.md` with its new file name. |
| `sprudel/ref/dsl-conventions.md` | drop the "Original Strudel vs Addon" decision at the top and the `See ref/dsl-addons.md` line. Replace with: a new function goes in the `lang_<group>_<subgroup>.kt` its group names; if no subgroup fits, add one rather than growing a file past ~700 lines. Fix the `lang/addons/lang_structural_addons.kt` reference in the "good reference implementations" note. |
| `sprudel/CLAUDE.md` | drop the `Addon functions in lang/addons/` row from the reference table. In Key Files, replace the `lang_*.kt` row with `lang_<group>_<subgroup>.kt` and one sentence on the naming. |
| `.claude/skills/sprudel-dev-knowhow/SKILL.md` | drop the `Adding addon functions in lang/addons/` row. |
| `sprudel/MEMORY.md` | the Lessons and status entries that say `lang_structural_addons.kt`, `lang_effects_addons.kt`, "addon accessors live in `lang.addons`", "carry the `addon` tag". Rewrite to the new file names, and add one Lesson recording that the addons split is gone and why. |
| `CLAUDE.md` rules register | add to **Retired, do not restore or cite**: the `lang/addons/` directory, the `io.peekandpoke.klang.sprudel.lang.addons` package and the `addon` doc tag (gone 2026-09-07, sprudel is not a Strudel port). |
| `docs/tasks/_priorities.md` | link this task if the maintainer wants it tracked there. |

## Verification

After **every** commit, not only at the end:

```bash
./gradlew :sprudel:jvmTest          # the whole sprudel suite; must be green
```

The specs that actually prove this refactor did nothing:

- `LangRetiredDoorsSpec`: the retired names still fail dispatch.
- `LangDeletedFilterAliasesSpec`, `LangDeletedWetNamesSpec`: same, for filters and wet knobs.
- `SprudelDocsSpec`: every documented function is still registered with its category and tags.
- `LangFieldAccessorsSpec`: the accessor objects still resolve.

Two extra checks worth running once, at the end of Phase 2:

```bash
# every lang file declares the library, or its functions silently vanish
grep -L '@file:KlangScript.Library("sprudel")' sprudel/src/commonMain/kotlin/lang/lang_*.kt

# nothing is over the size budget
wc -l sprudel/src/commonMain/kotlin/lang/*.kt | sort -rn | head -20
```

The strongest end-to-end check, and the one that gives certainty that not a single script name
moved: record the registered names from the KSP output before you start, and diff after Phase 2.
Build once first so the generated file exists (`./gradlew :sprudel:jvmTest`).

```bash
grep -o '"[a-zA-Z_][a-zA-Z0-9_]*"' \
  sprudel/build/generated/ksp/metadata/commonMain/kotlin/io/peekandpoke/klang/script/generated/GeneratedSprudelRegistration.kt \
  | sort -u > /tmp/sprudel-names-before.txt
# ... after ...
diff /tmp/sprudel-names-before.txt /tmp/sprudel-names-after.txt   # must be empty
```

Phase 1 step 5 is the one legitimate exception: dropping the `addon` tag changes the tag strings in
that file, not the names. If you also dropped the tag, filter to registered names only, or take the
before-snapshot after Phase 1 instead. This is the single most valuable guard in the plan. Run it.

## Risks and how they show up

| risk | symptom | guard |
|---|---|---|
| A new file misses `@file:KlangScript.Library("sprudel")` | compiles fine, every function in it disappears from the script surface | the `grep -L` above, and `SprudelDocsSpec` |
| A section is dropped during a cut and paste | a spec for one function fails, or a name vanishes from the generated file | the generated-name diff |
| A private helper is left behind in the deleted source file | compile error, immediate | the compiler |
| The other agent renames a section under you | your grep finds a name the table does not list | re-derive sections per file; put new sections in the group they belong to and report it |
| Merge conflict with the accessor rework | git says so | split groups in the recommended order, one commit each, and skip a group whose file is dirty |
| A KDoc `@param-tool` reference breaks | UI editor tools stop resolving | `ref/uitools.md`; nothing in this refactor touches KDoc, so this only happens if you edit while moving. Do not edit while moving. |

## Why these groupings

The subgroup boundaries follow how the functions are actually reached for, not the alphabet:

- **structural** is the big one and splits by what the operation does to the timeline:
  `sources` makes patterns from nothing, `seq`/`cat` combine them, `mask` gates them,
  `layer` stacks them, `chunk`/`window` select a part, `echo`/`repeat` repeat them,
  `steps` counts them, `iter` rotates them, `mute` and `tag` annotate them.
- **picking** splits by lookup family, because the twelve doors of each family are near-identical
  and always read together: `pick`, `out`, `inhabit`, `restart`, `reset`, `f`.
- **filters** gets one file per filter (`lpf`, `hpf`, `bpf`, `notch`), each around 200 lines with
  its aliases. They share nothing, so nothing is gained by keeping them together.
- **effects** splits by effect family, and absorbs `lang_body.kt` and `lang_vowel.kt`, which are
  effects that only ever had their own file because they arrived separately.
- **arithmetic** splits math from bitwise from comparison from the numeric helpers, which is where
  the addon file's `abs`/`min`/`max`/`clamp` naturally land next to `round`/`floor`/`ceil`.
- **synthesis** absorbs the two addon files: FM knobs, oscillator params, and the `snd*` shorthands
  split into basic waveforms and the super/pluck family.

Where an addon function had no obvious host it went to the group that owns its concept, not to a
leftovers file: `cylinder` to `orbit`, `solo` to `mute`, `merge` to `layer`, `morse` to `sources`,
`stretchBy` to `speed`, `earlyInCycle`/`lateInCycle` to `shift`, `timeLoop`/`repeat` to `repeat`.
There is no `lang_misc.kt` at the end of this and there should never be one again.
