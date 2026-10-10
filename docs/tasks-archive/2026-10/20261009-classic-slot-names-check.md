# The `classic()` slot names: one name per concept

> **DONE 2026-10-09** (branch `engine-pass-1`, merged as PR #85, v0.6.0): steps 1 to 4, every one bit-identical on
> the 18-song corpus and Kokon.
>
> Key commits: the decisions `f2c1ba0c` (Q21) and `dcf649d5` (Q22); the Lexikon's Crush and Phaser `66781690`; step 1
> `945c092b`; step 2a `27d833df`; step 2b `5e2b8be9`; step 3 `d72e119d`; step 4 `b72e605a`.
>
> **Where the open items went:** the weak pluck `ignitorParam` rows found in step 2b (`brightness`, `pickPosition`,
> `stiffness` on one advancing seed) are in [`docs/tasks/engine-follow-ups.md`](../../tasks/engine-follow-ups.md);
> sprudel's `analog(amount)` is Q25 in [`docs/tasks/_maintainer-questions.md`](../../tasks/_maintainer-questions.md).
> The red rig-anchor row of `SongBenchmarkCasesCompileSpec` the steps mention was fixed by `662aa8db`.

Status before archiving: **decided (maintainer, 2026-10-08, Q21), pulled ahead: it runs after the engine tidy-up and BEFORE pitch
pipeline step 1** ([`pitch-pipeline-into-the-tree.md`](../../tasks/pitch-pipeline-into-the-tree.md)). All decided (Q21, Q22).

## What it is

The maintainer, answering pitch decision D4: "I like the namespacing idea of `fm.xxx`, so the xxx parts must match the
names on the Ignitors. Probably the Ignitors already have the more stable and better names, but worth a check for
each param, so we get good and concise names."

The pitch pipeline names its own new slots that way (`vibrato.semitones`, `penv.semitones`, `fm.ratio`, `fm.depth`).
The older `classic()` slots are named after sprudel's readers instead. Examples: `lpf.freq`, `lpf.q`, `lpf.env`,
`tremolo.depth`, `tremolo.rate`, `tremolo.shape`, `crush.amount`, `coarse.amount`, `adsr.*`, and the flat ones
(`onepole`, `analog`, `voices`, ...). This task runs the same check over each of them.

## The work

1. **A table, one row per slot:** the slot name, the Ignitor door's name, the sprudel door's name, the unit, and a
   proposal, as Q9a did for the pitch slots. It goes to the maintainer before anything is renamed.
2. **Rename by the decided table:** the slot, the sprudel door parameter (the parity rule), and the KlangScript and
   Kotlin doors. Each old name is removed, not aliased, and gets an entry in `docs/retired-names.md`.
3. **Songs and docs:** `ignp("...")` calls in the songs and the tutorials name slots directly (Kokon writes
   `ignp("release", 3.0)`), so every rename sweeps them, along with the Lexikon, the KDoc and the docs model.

## Why it was pulled ahead

First planned after the pitch pipeline, so two kinds of change would not share a review. Pulled ahead by the
maintainer (2026-10-08), because the pipeline's new slots (`penv.attack`, `fm.attack`) would otherwise be born with
the envelope words this check renames.

## Decided (maintainer, 2026-10-08, Q21; the table: `tmp/reviews/classic-slot-naming-table.md`)

Pulled ahead of the pitch pipeline, so no slot is renamed twice.

1. **The envelope words on the Ignitor side become the slots' words:** `attack`, `decay`, `sustain`, `release`
   (was `attackSec`, `decaySec`, `sustainLevel`, `releaseSec`), on every `adsr` (the voice envelope, and the ones
   inside the filter, pitch envelope and FM builders), the flat Kotlin doors, the node fields (FM's `envAttackSec`
   and its siblings too) and the wire. Example: `Ign.saw().adsr(attack = 0.01, decay = 0.3, sustain = 0.5,
   release = 0.2)`. Positional calls are unchanged.
2. **`crush.amount` becomes `crush.bits`, `coarse.amount` becomes `coarse.factor`,** on the slot, the sprudel
   parameter and accessor, both Ignitor doors and the node field. The songs, the frozen songs, the tutorials, the
   whitepaper and the skill refs are adjusted (maintainer: "please adjust the songs / frozen songs / tutorials").
3. **`distort.amount` stays.** After 2, `amount` means only the distort drive.
4. **The filter `env` stays,** documented in semitones (the engine's rule: pitch in semitones).
5. **`tremolo.depth` stays;** "depth" is how far a modulation swings, in the stage's unit.
6. **`declickSeconds` becomes `declick`, flat.** The general rule (now in `/dsl-design` §4 and the register): time
   always in seconds, pitch always in semitones where it can be, the unit in the KDoc and not in the name.
7. **The two source slots get unique, concise names now** (maintainer: "choose unique useful but concise names"):
   - the pluck's `decay` (a loop feedback coefficient, 0.9 to 0.999, not seconds) becomes **`feedback`**: the same
     concept as the delay's `feedback` (a loop gain), which keeps one word per concept;
   - brown noise's `depth` (a per-sample white leak) becomes **`leak`**.
8. **The Katalyst keeps the namespacing,** `<sprudel door>.<Katalyst door knob>`: all 27 slots already fit, so no
   renames; `vowel.vowel` and `gain.gain` stay.
9. **`analog` is one unitless CHARACTER scale** (maintainer, 2026-10-08, Q22, option (a)): "we cannot fully go for
   cents, because there are multipliers involved that are currently not in the users' hands, and they might become
   user knobs, so here it is more about character, not so much about the exact cents." 0 is ideal, 1 to 8 usual, 10
   strong; each component documents its tells per unit (the oscillator about 1 cent of drift per unit; the filter
   its saturation, its cutoff tolerance and its wander). Bit-identical: only the KDoc and the docs change (sprudel's
   `analog` KDoc says "Peak drift in cents" today).
10. **The one-knob slots stay flat:** `onepole`, `pregain`, the sample slots `begin`, `end`, `speed`, `loop`.

## The work, in order

1. **The envelope words (decision 1, 6):** a mechanical rename across `audio_bridge` (`IgnitorDsl.kt`, the classic
   and walk files), `audio_be` (`IgnitorEnvelopes`, `IgnitorDslRuntime`, `PitchModFactories`, `IgnitorFilters`),
   `klangscript-libs` (`KlangScriptIgnitorExtensions`, `EffectBuilders`), the wire golden, `/dsl-design` §2 and
   `door-shapes.md`. Bit-identical (the corpus), a wire-schema hash change, door-parity rows.
2. **`crush.bits`, `coarse.factor`, `feedback`, `leak` (decisions 2, 7):** the slots, the doors, the accessors,
   the songs and every user-facing text; old names into `docs/retired-names.md`. Bit-identical.
3. **The findings of the check:** the script `Ignitor.slot` gets the 8 slots it lacks (`bipolar`, `chaos`,
   `color`, `declick`, `leak`, `octaves`, `persistence`, `tail`; a two-door gap), the stale
   `KlangScriptIgnitorSlots` KDoc, the three comments that still say "named after sprudel's readers". The Lexikon's
   Crush and Phaser entries are fixed (`66781690`).
4. **`analog` as a character (decision 9):** the KDoc of every `analog` door and of `AnalogDrift`, the filter
   humanization and the docs say "character amount" with the tells per unit, not "cents".

Before the pitch pipeline's step 1, so its new slots are born with the final words (`penv.attack`, `fm.attack`).

## Done

### Step 1, the envelope words and `declick` (2026-10-09)

- **What changed.** `attackSec`, `decaySec`, `sustainLevel`, `releaseSec` are `attack`, `decay`, `sustain`,
  `release` on every `adsr` (the chain, the filter, pitch envelope and FM builders), the flat Kotlin doors, the nodes
  `Lowpass`, `Highpass`, `Bandpass`, `Notch`, `Adsr`, `PitchEnvelope`, FM's `envAttackSec` and its siblings (Q-A: no
  `env` prefix), `FilterKnobs`, `FilterEnvDef`, and the runtime factories and Ignitor fields (Q-B). `declickSeconds`
  is `declick` (the slot `Slots.declick`, key `"declick"`; the node field; the doors). The frame-domain core keeps
  `sustainLevel` (`EnvelopeCore.prepare`, `Voice.Envelope`, `EnvelopeCalc`, `VoiceFactory`), and so does the voice
  tail's `releaseSec` in `VoiceFactory`; the constants keep their names (Q-C). The Katalyst's `attackSeconds` /
  `releaseSeconds` are untouched (Q-E). The retired `.declickSeconds(` assertions in `StdLibIgnitorTest` stay.
- **Found on the way.** `AdsrIgnitor` already had a member named `declick` (its `EnvelopeDeclick` state), which the
  renamed field would have clashed with; the state is `declickFilter` now. The runtime's Adsr arm had locals named
  `attack` ... `release`; they are `attackIgn` ... `releaseIgn`, built in the same order (inner, attack, decay,
  sustain, release, declick: rng draw order unchanged). `Slots` stays alphabetical (`color` moved above `decay`).
- **Wire.** `WIRE_SCHEMA_HASH` `-693111497` to `1502920473` (29 fields in 7 classes renamed; no `@WireName` changed).
  The codec specs run green on JS (`:audio_bridge:jsTest`, 262).
- **Corpus, bit-identical** (`tmp/naming/corpus-ep1-sn1.txt` against `corpus-ep1-before.txt`): all 16 rows
  identical; Der Schmetterling and Kokon from HEAD's text `94dfc72ae5637e91` and `2f8d88cd2458fce2`, as recorded.
- **Engagement control.** `classic()` with `decay = s.adsr.sustain` and `sustain = s.adsr.decay` swapped moved all
  16 rows and both live-song rows (`corpus-ep1-sn1-ctl.txt`); restored, `cmp` clean.
- **New door-parity row** (`KlangScriptEnvelopeDoorParitySpec`): the named arguments `attack`, `decay`, `sustain`,
  `release`, written in reverse order, build the same node on the script door as on the Kotlin door, on the chain,
  the four filters, the pitch envelope and FM. It pins script word = Kotlin word, not Kotlin word = node field (both
  doors route through the same Kotlin function). The field mapping is pinned elsewhere: for the filters by
  `KlangScriptFilterDoorParitySpec`'s literal rows, for the chain by "the Kotlin chain door: null keeps the node's
  defaults" (a hand-built `Adsr`), for FM by its two doors building the node on separate paths, and for the pitch
  envelope by its Kotlin side being the node constructor (review round 1, reviewer A). Mutation checks, each killed:
  the chain's script parameter back to `attackSec`, the filter builder's `sustain` back to `sustainLevel` (red at
  `lowpass`), FM's `release` back to `releaseSec` (red at `fm`). A row checking only that the retired names are
  refused was dropped in review: KlangScript refuses an unknown named argument, and its own suite tests that
  (`/review-loop`, "What deserves a test at all", 2026-10-03).
- **Suites.** `audio_bridge` jvmTest 146 and jsTest 262, `klangscript-libs` jvmTest 823 and jsTest 602 (822 and 601 after review round 1 dropped one row), `sprudel`
  jvmTest 3,471, `audio_be` jvmTest 2,412 and jsBrowserTest 2,308, all green; root guards `BuiltInSongsSmokeTest`,
  `DslDocExamplesSpec`, `LexikonSpec` green. `SongBenchmarkCasesCompileSpec`'s "every case compiles" row is green;
  its rig-anchor row is red on the maintainer's LIVE `DerSchmetterling.kt` (the anchor `.pitchEnvelope(0.5, x =` is
  in HEAD's text and not in the live file), not on this change.

### Step 2a, `crush.bits` and `coarse.factor` (2026-10-09)

- **What changed.** The shared `AmountSlots` split into `CrushSlots { bits }` and `CoarseSlots { factor }` (keys
  `crush.bits`, `coarse.factor`); the nodes `Crush.bits` and `Coarse.factor`; the flat Kotlin doors `crush(bits)`,
  `coarse(factor)`; the script doors and `Ignitor.slot.crush.bits` / `Ignitor.slot.coarse.factor`; the runtime
  `Ignitor.crush(bits)` / `Ignitor.coarse(factor)`, their Ignitor fields and `CrushCore.halfLevels(bits)`; sprudel's
  `crush(bits, oversample)` / `coarse(factor, oversample)`, the accessors `crush.bits` / `coarse.factor`, the
  `@tags`, and `ClassicSlotKeys.crushBits` / `coarseFactor`. The songs (Seltsamere Dinge, A Synth Worth Lying For,
  The Synthsale Piper's Last Rave, Die Kirschblüte), the frozen Stranger Things (and its header note),
  `SongBenchmarkCases`, the whitepaper (the Sakura listing and its prose, `fig-arrangement`, `fig-classic-stages`),
  the skill references and `audio/ref/off-values.md` say the new words. `distort.amount` is untouched.
- **Found on the way.** Two test keys the map did not list: `FreqAccessorIntelSpec` (the accessor children of
  `crush` and `coarse`) and `LangControlRestSpec` (the compound rows).
- **Wire.** `WIRE_SCHEMA_HASH` `1502920473` to `380449250` (`Crush.bits`, `Coarse.factor`). JS codec specs green.
- **Corpus, bit-identical, including every row whose text changed** (`corpus-ep1-sn2a.txt`): all 16 rows identical
  (Seltsamere Dinge, A Synth Worth Lying For, The Synthsale Piper's Last Rave, Die Kirschblüte and frozen Stranger
  Things among them); Der Schmetterling and Kokon from HEAD's text identical to their recorded hashes.
- **Engagement controls.** `ClassicSlotKeys.crushBits` writing `"crush.amount"` while the slot says `crush.bits`:
  Seltsamere Dinge, A Synth Worth Lying For and frozen Stranger Things moved, Greensleeves identical. The same for
  `coarseFactor` writing `"coarse.amount"`: The Synthsale Piper's Last Rave, Die Kirschblüte, Seltsamere Dinge and
  frozen Stranger Things moved; A Synth Worth Lying For (crush only) and Greensleeves identical. Restored, `cmp` clean.
- **Suites.** As step 1, same counts, all green; `SongBenchmarkCasesCompileSpec`'s every-case row green (it compiles
  the three `coarse(factor = 2, oversample = 4)` cases), its rig-anchor row still red on the live
  `DerSchmetterling.kt` only.

### Step 2b, the pluck's `feedback` and brown noise's `leak` (2026-10-09)

- **What changed.** `Slots.decay` is `Slots.feedback` (key `"feedback"`, default 0.996) and `Slots.depth` is
  `Slots.leak` (key `"leak"`, default 0.02), `Slots` still alphabetical; `Pluck.feedback`, `SuperPluck.feedback`,
  `BrownNoise.leak`; the walk; the runtime `karplusStrong(feedback)`, `superKarplusStrong(feedback)`,
  `brownNoise(leak)`, their fields and defaults, and the kernel `KarplusString`'s `feedback` (Q-B); the builder knob
  `x.feedback(...)` on `Ignitor.pluck` / `Ignitor.superpluck`, `Ignitor.brownnoise(leak)`, `Ignitor.slot.feedback`;
  sprudel's `sndPluck(feedback, ...)`, `sndSuperPluck(..., feedback, ...)`, `sndBrown(leak)` and their keys; the
  pluck editor tools (`domKey`, `paramInfoIcon`, the label "Feedback"). The Synthsale Piper's Farewell
  (`IrishLament.kt`) writes `x.feedback(...)`; the whitepaper figure's comment, the skill references and the open
  tasks (`pluck-release-tail.md`, `sprudel-sound-doors-compound.md`, the proposal in
  `sprudel-sound-function-surface.md`) follow. The envelope `decay`, the songs' own `Ign.param("decay")` with their
  `ignp("decay", ...)` (Der Schmetterling, Kokon, the frozen songs and pieces, `ParamToolArgumentSpec`), `echo`'s
  `decay` and every other `depth` (tremolo, duck, vibrato, FM, the filter envelope) are untouched.
- **A side effect, as the map predicted (D5).** A song's `ignp("decay", ...)` used to feed a built-in pluck in the
  same voice too, since both used the key `"decay"`; the keys are apart now. No corpus song played a built-in pluck
  while writing `ignp("decay")`, so nothing moved.
- **Found on the way.** Test keys the map did not list: `CompletionProviderTest` (the `Ignitor.slot` completion
  list), `LangControlRestSpec` (`pluckSlots`), `LangDoorFormsSpec`'s positional rows, `IgnitorsTest`'s `depth(...)`
  helper and `NoiseDefaultsSyncSpec`. And a weak spot that is older than this change: `IgnitorDefaultsTest`'s
  "pluck responds to ignitorParam ..." rows render both sides from one shared, advancing seed, so they differ even
  when the slot is not read (a mutant of `Slots.feedback`'s key left the renamed row green). The new rows below
  render both sides from the same fresh seed.
- **Wire.** `WIRE_SCHEMA_HASH` `380449250` to `1326193870` (`Pluck.feedback`, `SuperPluck.feedback`,
  `BrownNoise.leak`). New codec rows with the renamed fields NON-default: "BrownNoise with leak", "Pluck with
  feedback", "SuperPluck with feedback" (`:audio_bridge:jsTest` 265, green).
- **Corpus, bit-identical** (`corpus-ep1-sn2b.txt`): all 16 rows identical, The Synthsale Piper's Farewell (text
  edited) and every guitar row writing `ignp("decay", ...)` (frozen Der Schmetterling and its piece) among them; Der
  Schmetterling and Kokon from HEAD's text identical.
- **Engagement controls.** sprudel's `sndPluck` writing `"decay"` while the slot says `feedback`: Drunken Synthlor
  (`sndPluck(0.999, 0.8)`) moved; The Synthsale Piper's Farewell and Greensleeves identical. `OscPluckBuilder.feedback`
  made a no-op: The Synthsale Piper's Farewell moved, Greensleeves identical. Restored, `cmp` clean.
- **The leak row** (no song writes `leak`, so the corpus cannot see it): `IgnitorDefaultsTest` renders brown noise
  and the pluck from one fresh seed per render; the default equals itself and an explicit default, `leak = 0.3`
  (`feedback = 0.9`) moves it, the retired key `depth` (`decay`) does not. Plus `LangSndSpec` (`sndBrown` writes
  `"leak"`) and `StdLibIgnitorTest` (`Ignitor.brownnoise(leak = 0.3)`, named). Mutation checks, each killed:
  `Slots.leak` keyed `"depth"`, `Slots.feedback` keyed `"decay"`, `sndBrown` writing `"depth"`, the script door's
  parameter back to `depth`.
- **Suites.** `audio_bridge` jvmTest 146 and jsTest 265, `klangscript-libs` jvmTest 822 (after the completion-list
  fix and the round-1 row removal) and jsTest 601, `sprudel` jvmTest 3,471, `audio_be` jvmTest 2,414 and jsBrowserTest 2,310, root guards green
  but for the known rig-anchor row on the live `DerSchmetterling.kt`.
- **The grep of map §C** finds no old name outside history: only `docs/retired-names.md`, this task's decision text,
  the pitch pipeline's line 517 and `docs/plans/signal-flow-redesign.md:132` (a plan sketch whose
  `Ignitor.slot.decay` is the envelope decay of a design that did not ship). `ignitorParam("decay" | "depth"` in
  sprudel: none.

### Step 3, the eight missing script slots (2026-10-09)

- **What changed.** `Ignitor.slot` (`KlangScriptIgnitorSlots`) gains `octaves`, `persistence`, `color`, `leak`,
  `tail`, `bipolar`, `chaos` and `declick`, each the same `IgnitorDsl.Slots` object as the Kotlin door, its default
  and its user in the KDoc. The class KDoc no longer claims the data-class defaults are sealed constants (the
  oscillator doors keep the open slots, the noise doors seal theirs, the pluck doors all but `analog`). The three "named after sprudel's
  readers" comments (`KlangScriptIgnitorSlots`, `IgnitorDsl.Slots`, `IgnitorDslClassic.kt`'s header) and the
  `Ignitor.param` KDoc's slot list now say `<door>.<param>` with the engine door's word, and that `declick` has no
  sprudel door.
- **No tree changes, so no corpus re-render:** the step adds script properties and rewrites comments; nothing a
  song or a built-in builds is different.
- **Tests.** `KlangScriptIgnitorSlotTest` gets eight rows; the new `KlangScriptIgnitorSlotCompletenessSpec`
  (jvmTest) reads every flat `Param` leaf of `IgnitorDsl.Slots` by reflection and checks that `Ignitor.slot.<name>`
  is the same object (`shouldBeSameInstanceAs`), and that every flat script property is a Kotlin leaf of the same name,
  so the gap cannot reopen from either side. Mutation checks, each killed: `declick` removed from the script door
  (its row red, and the completeness row red), `color` pointed at `Slots.chaos` (completeness red), `color` a fresh
  equal `Param` copy (both identity rows red), a stray script-only slot (the reverse row red).
- **Suites.** `klangscript-libs` jvmTest 832 and jsTest 609 (after review round 1: one step-1 row dropped, the
  completeness spec checks both directions by identity, two rows), the rest as step 2b, green but for the known rig-anchor
  row.

### Step 4, `analog` as a character amount (2026-10-09)

- **What changed, docs and KDoc only.** Every `analog` door now says what Q22 decided: one unitless CHARACTER scale,
  0 ideal, 1 to 8 usual, 10 strong, each component with its tells per unit. Sprudel's `analog` (the door, the string
  door, the object and its invoke, the mapper chain) no longer calls the amount "peak drift in cents"; the 16
  oscillator and pluck builders' `analog` knobs, `Lowpass.analog` (its tells: the resonance damping, and with
  `humanize` the cutoff tolerance and the wander), `Highpass.analog`, `Bandpass.analog`, `AnalogDrift`'s KDoc (the
  oscillator's tell, about a cent of peak per unit), the header of `FilterHumanizationDefaults.kt` (the filter's
  three tells), the Thickness tutorial's comment and the two skill references follow. `AnalogDriftCoeffs`,
  `FilterHumanization` and the per-constant KDoc already state per-unit tells and stay; so do `AnalogDriftSpec`'s
  "cents budget" comments, which describe a measurement.
- **Found on the way.** The music-writing reference listed an Ignitor chain method `.analog(amount)` ("Perlin noise
  pitch jitter") that does not exist; the row now names the builder knob `x => x.analog(n)`.
- **Not this step:** sprudel's `analog(amount)` keeps its parameter name, as Q-D put that to the maintainer (Q25).
- **Proof.** The diff touches comment and markdown lines only (checked by filtering the patch to non-comment
  lines); bit-identical by construction, no corpus re-render.
