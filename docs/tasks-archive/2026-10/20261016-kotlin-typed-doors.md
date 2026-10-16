# Typed Kotlin doors: the compiler checks every door

Status: **decided (maintainer, 2026-10-13, Q24); design step done and answered 2026-10-16 (option C, with the answers
below, revised the same day: one set of builders, equal doors); steps 1 and 1b done; spike S1 done 2026-10-16 (review
rounds 1 and 2 applied); rollout S2 (the other oscillators), S3a (the filters and the eq) and S3b with S3c (the envelopes, the effects and the one-value doors) and S4 (the arithmetic) and S5 (the Katalyst) and S6 (the composition doors) and S7 (the visibility sweep) done 2026-10-16: the rollout is done, the task archived; what is left lives in other files ("Left after the rollout", at the end). The pattern stays in `/dsl-design` §3.** Large, mostly mechanical.

## Why

"I chose Kotlin exactly for the compiler." Today's Kotlin door types every Ignitor value as `IgnitorDslLike`, a
`typealias` for `Any` (`klangscript-libs/.../KlangScriptIgnitorExtensions.kt`), so `osc.vibrato(rate = "fast")`
compiles and fails only at run time. Beside it, the `audio_bridge` extensions (`fun IgnitorDsl.vibrato(rate: Double,
semitones: Double)` and friends) are a third spelling with limits of their own: eleven take only numbers, the
arithmetic and the filters take all numbers or all signals, `phaser` and `shimmer` have no `floor`.

## The decisions

1. **One typed Kotlin door per DSL.** Every value parameter takes the DSL's own type (`IgnitorDsl`,
   `KatalystKnobDsl`), so a number or a signal fits each argument on its own and there are no 2^n overloads.
   Numbers come in through two plain extension properties, `.ign` (an `IgnitorDsl.Constant`) and `.kat` (a
   `KatalystKnobDsl.Constant`):
   ```kotlin
   osc.vibrato(rate = 0.5.ign, semitones = 0.3.ign)
   osc.vibrato(rate = Ign.perlin(), semitones = 0.3.ign)   // a mix, the same one function
   ```
   One shared `.const` class in both sealed wire hierarchies was considered and dropped (the wire codec would need a
   class with two parents; complexity is the enemy).
2. **The mechanical changes are accepted.** Engine code, `classic()`, the defaults and the specs that write plain
   numbers today gain `.ign` / `.kat`; the `audio_bridge` shorthand extensions retire into the typed door. A pure
   replacement needs no mutation checks: everything replaced and every test still green is done.
3. **KlangScript keeps its own door.** The KlangScript door functions (`@KlangScript...`, number-or-signal at run
   time, as the script needs) become internal to their module and are no longer the Kotlin door. The Kotlin door is
   not exposed to KlangScript. Either the Kotlin door calls into the KlangScript door, or both call into one
   underlying layer; which one is the first design step. Also mostly mechanical.

## Open for the first step

- The layering in 3: the Kotlin door over the KlangScript door, or both over a shared layer.
- The "Two doors, one DSL" rule (`CLAUDE.md`, `/dsl-design` §3) and the door-parity specs: what parity means once the
  Kotlin door is typed and the script door is not (same names, meaning and scale stay; the types differ by design).
- The order: Ignitor first, then Katalyst; how the sweep is cut into reviewable steps.
- Related, separate: curves as nodes ([`future/curves.md`](../../tasks/future/curves.md)), where values plug into nodes the same way.

## Design (2026-10-16, read-only design step)

**Recommended layering, option C: the KlangScript door calls the Kotlin door; the Kotlin door is flat, in `audio_bridge`.**
Filters and `adsr` already work this way (`IgnitorDsl.kt:2768-2774`, the script door converts and calls it,
`KlangScriptIgnitorExtensions.kt:239`). The one public Kotlin door is the `audio_bridge` extension set, typed only
(about 28 `Double` twins deleted, 10 number-only doors retyped), holding every door's logic; oscillators and
Katalyst modulators use their node constructors. The script door (`KlangScriptIgnitor`, `KlangScriptIgnitorExtensions`,
the builders) becomes `internal`, converts (`toIgnitorDsl`, `toKatalystKnob`) and delegates. Why C: it is the only
option the engine, `IgnitorDefaults.kt` and the `audio_be` specs can reach (`audio_be` depends on `audio_bridge` only);
it extends a precedent instead of adding a layer; types flow inward and convert once at the script edge; fewest new
functions. Options A (Kotlin door wraps the script door in `klangscript-libs`, about 270 new wrappers, unreachable from
the engine) and B (both build on a shared layer, every door written twice) were weighed and not recommended.

```kotlin
// audio_bridge: THE Kotlin vibrato
fun IgnitorDsl.vibrato(rate: IgnitorDsl, semitones: IgnitorDsl, rangeFrom: IgnitorDsl = VIBRATO_RANGE_FROM.ign,
                       rangeTo: IgnitorDsl = VIBRATO_RANGE_TO.ign, phase: IgnitorDsl = VIBRATO_PHASE.ign) = ...
saw.vibrato(5.ign, 0.5.ign, rangeFrom = 0.ign, phase = 0.75.ign)
saw.vibrato(rate = IgnitorDsl.Sine(freq = 0.5.ign), semitones = 0.3.ign)   // a mix, one function
```

**Extensions:** `Double.ign`, `Int.ign`, `Double.kat`, `Int.kat` returning the precise `Constant`, plus `unaryMinus` on
`Constant` so `-1.ign` is `Constant(-1)` like the script's `-1` (boxing at build time is fine, maintainer 2026-10-16).
Name knobs through catalogue indices (`BodyMaterials.indexOf("wood").kat`).

**Rule text (proposed):** "Two doors, one DSL: every surface addition lands on the typed Kotlin door (`audio_bridge`;
every value an `IgnitorDsl` / `KatalystKnobDsl`, numbers through `.ign` / `.kat`) AND the KlangScript door (stdlib,
internal, number or signal at run time) in the same deliverable, with a door-parity spec. Same word, meaning, scale
and default; the types differ by design." Plus `/dsl-design` §3 and `door-shapes.md` adapted.

**Steps:** 1 the extensions and rule texts (about 7 files); 2a filters/eq (34 files), 2b adsr (16), 2c arithmetic
(31), 2d effects and pitch (34), each mechanical apart from the `IgnitorDsl.kt` hunk; 3 the Ignitor script door goes
internal and delegates (about 45, needs a reviewer); 4 the Katalyst chain door in `audio_bridge` (about 15, new door,
logic moves); 5 optional `.ign`/`.kat` for existing `Constant(x)` lines, only where edited anyway.

## The maintainer's answers (2026-10-16, Q44 to Q51)

- **Q44, Q45:** yes. Option C; the Kotlin door uses named parameters where the script takes a configure lambda
  (the named-parameter half is superseded by the revision below: the same builders and shapes on both doors).
- **Q46: surface parity.** "When there is an `Ign` object in KlangScript it should be there in Kotlin as well.
  Switching from KlangScript to Kotlin or vice versa should be easy for the user. The only difference might be the
  function doors and the `.ign` and `.kat` on the Kotlin side." So typed Kotlin `Ign` and `Kat` objects live in
  `audio_bridge` with the same words as the script objects (`Ign.saw()`, `Ign.perlin()`, `Kat.sine(...)`), building
  the nodes; the script objects in `klangscript-libs` become internal and delegate to them.
- **Q47:** retire the Kotlin `Double` operators (`saw * 0.5.ign`), "as long as the KlangScript side stays intact"
  (the script keeps `saw * 0.5`).
- **Q48:** allow `floor =` on the Kotlin phaser and shimmer. (Superseded by the revision below: `floor` is a builder
  knob on the Kotlin door, as on the script.)
- **Q49:** an eq section given an expression throws `IllegalArgumentException` at build time, naming door and parameter.
- **Q50: the rule.** "Only KlangScript-exposed functions accept any `XxxLike = Any` params, converted and checked at
  run time. The Kotlin doors should be typesafe." So `bands`, `cut`, `serial`, `parallel` and `blend` may stay one
  shared function only where their signature has no `Any`-typed parameter; any that takes an `XxxLike` gets a typed
  Kotlin door like the rest.
- **Q51:** yes, build-time knobs stay plain values: the one recorded exception. The criterion (a knob the script
  takes as a plain number or string stays plain; a plain `Double`, no `Int` overloads) lives in `/dsl-design` §3.
  Corrected 2026-10-16 (maintainer, S3c review round 1): the voice `shape`'s and `distort`'s `shape` and `oversample`
  are build-time in the engine's sense (read once per voice) but SLOT-READ, read per event from the slots (`IgnitorDslRuntime.kt`,
  `distortionShapeKnob`, `oversampleStagesKnob`), so they are typed on the Kotlin door. Checked the same way: the
  Katalyst compressor's `lookahead` and `distort` knobs, the super oscillators' `phasePool` arguments and the
  shimmer's `pitches` are plain fields on their nodes (`Double`, `String`, `List<Double>`), never a slot, so they
  stay plain. The filters' `passes` is the same class (an `IgnitorDsl` on the node, `classic()`'s `lpf.passes` /
  `hpf.passes` slot read at voice build), so it is typed too under the same decision: `FilterBuilder.passes(n:
  IgnitorDsl)` (the band filters have no `passes`), default `FILTER_PASSES` (new in `constants/FilterDefaults.kt`, the
  node fields read it), a constant still rounded and coerced to 1..16 at the door (`coercePasses`), a slot passed
  through for the runtime's own coercion; the script variant converts and delegates, so `passes` is no longer a
  `classic()`-only group. `KlangScriptFilterDoorParitySpec` has a slot row and a literal default-pass row; two mutants
  (the script variant casting, a default of 2) went red, restored and `cmp`-checked.

## Step 1 (2026-10-16): the extensions and the rule texts

`audio_bridge` gained `val Double.ign` / `val Int.ign` (an `IgnitorDsl.Constant`, in `IgnitorDsl.kt` before the
arithmetic) and `val Double.kat` / `val Int.kat` (a `KatalystKnobDsl.Constant`, at the end of `KatalystKnobDsl.kt`),
plus `operator fun unaryMinus()` on each `Constant` returning the negated `Constant`: Kotlin reads `-1.ign` as
`-(1.ign)`, and the operator keeps it `Constant(-1.0)`, the script's `-1`. No `ign`, `kat` or `unaryMinus` existed
before, so nothing clashes; the KDoc says the precise `Constant` receiver wins over any later general operator.
`NumberExtensionsSpec` (commonTest, 6 rows) pins the constants, Int against Double, and the minus rows; both mutants
(each `unaryMinus` returning `Neg(this)`) went red by assertion, restored and `cmp`-checked; the JS target compiles.
The rule texts now describe the typed door as the target state, the sweep following in steps 2 to 4: the "Two doors,
one DSL" row in `CLAUDE.md` (with Q46's `Ign` / `Kat` objects and Q50's `XxxLike = Any` only on script functions),
`/dsl-design` §3 (the layering replaces "klangscript-libs IS the Kotlin door"), the two recorded asymmetry bullets in
`door-shapes.md` (named parameters on the Kotlin door are the rule now), and `klangscript-libs/CLAUDE.md`. No door
and no caller changed.

## Step 1b (2026-10-16): the typed `Ign` and `Kat` objects

Inventory: the script `Ign` has 28 functions and the `slot` property (`freq`, nine oscillators, `silence`, six noises,
five super oscillators, `pluck`, `superpluck`, `param`, `constant`, `variants`); the script `Kat` has 10 functions,
the call form and the `slot` property (`build` and its call form `Kat(...)`, `classic`, `param`, five periodic
modulators, `perlin`, `berlin`). `audio_bridge` gained `object Ign` (`Ign.kt`) and `object Kat` (`Kat.kt`) with the
same words, parameter names, order and defaults; every value parameter is an `IgnitorDsl` / `KatalystKnobDsl`, the
builder knobs are named parameters of the same word with the node's default (`Ign.saw(freq = 110.ign, analog =
5.ign, phase = 0.5.ign)`), and the knobs the script already takes as plain numbers (`resetSamples`, `shapeMax`, the
flanks, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`) stay plain `Double` (Q51). Each function returns
its precise node type; `Ign.slot` is `IgnitorDsl.Slots`, `Kat.slot` is `KatalystDsl.Slots`. One piece of door logic
moved: the sealed string defaults of `pluck` and `superpluck` (constants, not the open `Slots.*` params) live in the
Kotlin door now, and the script doors build on it (`KotlinIgn.pluck(freq = ...)`, an import alias, since both
objects are called `Ign`). The other script doors keep their conversions (`orNoteFreq`, the `variants` child check),
which belong to the script edge.

Not mirrored, each waiting for an API decision (superseded by the revision below: these take the script's own
builder shapes, `harmonics(count, rolloff)`, chained `partial`, chained `phasePool`, so the questions are closed): the sine's partial banks (`harmonics(count, rolloff)`,
`octaves(count, rolloff)`, `suboctaves(count, rolloff)`: `harmonics` + `harmonicsRolloff` as the node names them, or
`harmonicsCount` + `harmonicsRolloff` by the `rangeFrom` / `rangeTo` rule), the sine's repeatable `partial(ratio, gain,
phase)` (a list parameter, perhaps `partials = listOf(...)`, has no script word), the super oscillators'
`phasePool(on, kMin, kMax, drawTries, poolSize, refreshEvery, selection, warmup)` (the node's flat names, or prefixed
ones; `on` defaults to 1 in the call and 0 on the node), and `Kat.build` / `Kat(...)`, the chain, which is step 4.

`KotlinIgnObjectParitySpec` (72 tests) and `KotlinKatObjectParitySpec` (24) compare the script call with the Kotlin
call for every mirrored function: defaults, numbers, a signal, and every knob with distinct values; noise sources
compare with the per-call `uid` zeroed, and one row pins the pluck doors' sealed node by hand, since the script door
now builds on the Kotlin one. Six mutants went red and were restored and `cmp`-checked: a wrong `whitenoise` default,
a swapped `saw` parameter, a dropped `supersaw` knob, a wrong sealed `pluck` default, a swapped `Kat.square` duty and
phase, a wrong `Kat.perlin` default. `:audio_bridge:jvmTest`, `:klangscript-libs:jvmTest` and
`:audio_bridge:compileKotlinJs` are green; the corpus is GREEN, frozen 3 of 3 identical, 13 live identical and 2
edited, `Kokon` and `Der Schmetterling`, whose code hashes changed with the maintainer's song commit `13019900`, not
with this step.

## Revision (maintainer, 2026-10-16): one set of builders, equal doors

"The underlying builders should be the same for KlangScript or Kotlin and the DSL doors should be equal / similar,
only differing in which parameters are accepted." This supersedes Q45's flat named parameters: the Kotlin door uses
the same builders and the same shapes as the script (configure lambdas, chained knobs, `harmonics(count, rolloff)`,
chained `partial`, chained `phasePool`, maintainer 2026-10-16), with typed parameters:

```
Ign.saw().vibrato(5, 0.3, v => v.range(0, 1))                       // KlangScript
Ign.saw().vibrato(5.ign, 0.3.ign) { it.range(0.ign, 1.ign) }        // Kotlin
```

So the builders move to `audio_bridge` with typed knob methods (reachable by the engine); the KlangScript side keeps
thin internal `Any`-taking variants that convert and call the same typed builder methods (Q50), registered by KSP.
Step 1b's flat named parameters are reworked onto the builders; the step 1 rule texts that say "named parameters for
a configure lambda" come back out. "The rest should follow naturally" (maintainer).

## Spike S1 (2026-10-16): `vibrato` and `saw` on one set of builders

`VibratoBuilder` and `OscSawBuilder` moved to `audio_bridge/src/commonMain/kotlin/IgnitorBuilders.kt` (same simple
names, the script shows them) with typed member knobs (`range(from, to)`, `phase`, `analog` take `IgnitorDsl`;
`resetSamples`, `shapeMax` stay `Double`, Q51), plus `internal fun <B> B.configured(configure)`. `IgnitorDsl.vibrato`
is now ONE door, `vibrato(rate: IgnitorDsl, semitones: IgnitorDsl, configure)`, replacing the flat `Double` and
`IgnitorDsl` overloads (`rangeFrom`, `rangeTo`, `phase` as named parameters are gone); `Ign.saw` is
`saw(freq, configure)`. The script doors `vibrato` and `Ignitor.saw` convert and call the typed doors with the checking
wrapper (`self.vibrato(r, s) { it.configuredBy("vibrato", configure) }`); the script knobs are `internal` extensions
that convert and call `this.member(...)`. Callers moved to `.ign` (`WarmupVocabulary`, six `audio_be` specs, the JS
wire codec spec, `KlangScriptVibratoDoorParitySpec`); `KotlinIgnObjectParitySpec` has the saw rows in the builder
shape, four vibrato rows and a pin of the saw and vibrato default nodes (the script doors build on the Kotlin ones now).

Proven: (a) an `internal` `@KlangScript.Function` compiles in the generated KSP code, JVM and JS (the generated file is
in the same module); a `name = "resetSamples"` override generates `typedReceiver.scriptResetSamples(...)` under the
script word. (b) The script call reaches the variant and the variant the member: no recursion, the right node (the
parity rows; a variant returning `this`, a swapped `range` in the variant, `scriptResetSamples` calling `shapeMax`, and
a swapped `range` in the member each went red, as did a wrong `Ign.saw` freq default; restored, `cmp`-checked).
(c) `GeneratedStdlibDocs.kt` differs only in the two types' `fqcn` (`script.stdlib` to `audio_bridge`); every KDoc text
is byte-identical. (d) In `sprudel` (a scratch file, removed), `saw.phase(0.5.ign)` and `vib.range(0.ign, 1.ign)`
compile, `saw.phase(0.5)`, `vib.range(0, 1)` and `saw.scriptResetSamples(4.0)` fail with "Cannot access ...: it is
internal in file". (e) `:audio_bridge:compileKotlinJs`, `:klangscript-libs:compileKotlinJs` and
`:audio_bridge:compileTestKotlinJs` are green. `:audio_bridge`, `:klangscript-libs`, `:audio_be`, `:sprudel` and root
`jvmTest` are green; the corpus is GREEN, frozen 3 of 3 identical, 13 live identical, `Kokon` and `Der Schmetterling`
edited by the maintainer's song commit as in step 1b.

### Pattern

The recipe lives once, in `/dsl-design` §3 ("The typed-door pattern"), with the test rule beside it (one row per
member knob against the node written out with `.copy()`, plus the parity rows).

### Review round 1 (2026-10-16)

Two reviewers found two gaps, both applied. (1) A script variant named like its member silently called ITSELF once
the member's parameter names drifted (reviewer A's mutant `range(lo, hi)`: it compiled and threw a
`StackOverflowError`). Every script variant now has a Kotlin name of its own and the script word in the annotation
(`scriptRange`, `scriptPhase`, `scriptAnalog`, `scriptResetSamples`, `scriptShapeMax`); the same mutant is now a
compile error in the variant ("No parameter with name 'from' found"), restored and `cmp`-checked. (2) Parity rows
cannot see a member bug, since both doors share the member: `KotlinIgnObjectParitySpec` gained one row per saw and
vibrato member knob against the node written out with `.copy()`, from Kotlin and from the script
(`KlangScriptSawRampSpec`, `KlangScriptIgnitorPhaseDoorParitySpec` and `KlangScriptVibratoDoorParitySpec` already
covered every knob against written-out nodes from the script). Decided for the rollout (coordinator): the eq becomes
two typed builders of the same words (voice and Katalyst) sharing one helper for the section conversion and the Q49
refusal; a name knob gets a typed `String` member beside the index form through the same coercing lookup; build-time
knobs take a plain `Double`, no `Int` overloads. The rule texts now say "the same builders and shapes; only the
accepted types differ" (`CLAUDE.md`, `/dsl-design` §2 to §4, `door-shapes.md`, `klangscript-libs/CLAUDE.md`, the
flat doors' KDocs in `IgnitorDsl.kt`, `Ign.kt`, `Kat.kt`).

## Rollout S2 (2026-10-16): the other oscillators on their builders

The fifteen other oscillator builders moved to `audio_bridge/IgnitorBuilders.kt` with typed member knobs:
`OscSineBuilder`, `OscTriBuilder`, `OscZawtoothBuilder`, `OscZampBuilder`, `OscImpulseBuilder`, `OscPulzeBuilder`,
`OscSquareBuilder`, `OscRampBuilder`, the five super builders, `OscPluckBuilder` and `OscSuperPluckBuilder`. The sine
keeps its chained shapes (`harmonics`, `octaves`, `suboctaves` as `(count, rolloff = 1.0.ign)`, `partial(ratio, gain =
1.0.ign, phase = 0.0.ign)` appending one partial per call), the super builders keep the one-call `phasePool(on = 1.0,
kMin, ...)` with plain `Double` and `String` arguments and the family's engine constants as Kotlin defaults (the script
variant keeps its literals, the same values; the bare `phasePool()` row pins that they agree). Plain-number knobs stay
plain (`flankSamples`, `riseFlank`, `fallFlank`, `resetSamples`, `shapeMax`, `spreadPower`, `sideAtten`, `gainJitter`,
`centerJitter`). No oscillator knob is a name knob, so pattern point 7 had nothing to do here (`phasePool`'s `selection`
is a free-form `"name[:width[:outliers]]"` string, plain on both doors). Every `Ign` oscillator door is now
`(freq = IgnitorDsl.Freq, configure)`, the flat named knobs gone; `pluck` and `superpluck` build their sealed default
node and hand it to the builder. The script doors in `KlangScriptIgnitor` call the Kotlin doors with the checking
wrapper (`KotlinIgn.sine(freq = freq.orNoteFreq()) { it.configuredBy("Ignitor.sine", configure) }`), and all 92 script
knobs are `internal`, renamed (`scriptVoices`, `name = "voices"`) variants that convert and call the member. Callers:
only `klangscript-libs` tests reached the moved knobs from Kotlin (six specs, onto `.ign`); no caller in `audio_be`,
`audio_bridge`, `sprudel`, `src/` or `audio_benchmark` used them or the flat `Ign` doors. `GeneratedStdlibDocs.kt`
differs from the S1 tree only in the fifteen types' `fqcn` (`script.stdlib` to `audio_bridge`); every KDoc text is
byte-identical.

`KotlinIgnObjectParitySpec` has its oscillator rows in the builder shape (plus a partial-bank row and two `phasePool`
rows per super oscillator), one default-node row per Kotlin door (pluck and superpluck keep their sealed-node pin), and
one row per member knob (101 rows) against the node written out with `.copy()`, each run from Kotlin and from the
script, with a guard that the row moves its knob off the default. Five mutants went red and were restored and
`cmp`-checked: `OscSuperSineBuilder.analogSpread` writing `analog` (caught only by the member rows), a wrong default
symbol on `Ign.zamp`'s `freq` (the default-node row), `scriptStiffness` casting instead of converting (an internal
error on a number), `OscSuperRampBuilder.scriptPhasePool` passing a fixed `selection` (the free-form string knob mapped
wrong), and a wrong `kMin` default on the supertri `phasePool` member. `:audio_bridge`, `:klangscript-libs`, `:audio_be`, `:sprudel` and root
`jvmTest` are green, `:audio_bridge:compileKotlinJs` and `:klangscript-libs:compileKotlinJs` too; the corpus is
GREEN, frozen 3 of 3 identical, 13 live identical, `Kokon` and `Der Schmetterling` edited by the maintainer's song
commit as in S1.

## Rollout S3a (2026-10-16): the filters and the eq on their builders

`FilterKnobs`, `FilterBuilder`, `BandFilterBuilder` and the eq's builders moved to `audio_bridge/EffectBuilders.kt`
(a new file beside `IgnitorBuilders.kt`, the script side's name), with typed member knobs: `passes(n: Double)` (typed since S3c, see Q51; then a
build-time number, Q51), `analog`, `env` (`IgnitorDsl`), `humanize(on: Boolean = true)` (the script variant coerces
with `coerceFlag`), and `adsr(attack, decay, sustain, release, configure)` with a nested configure on `ModAdsrBuilder`,
which had to move with it (S3b's `AdsrBuilder`, `PitchEnvelopeBuilder` and `FmBuilder` stay); `ModAdsrBuilder.curves`
has the index form (`IgnitorDsl`, omitted = `MOD_ENV_CURVE`) and, by pattern point 7, a `String` form through
`AdsrCurves.indexOf`, the lookup the script variant uses. The Kotlin doors are ONE function per word:
`lowpass`/`highpass`/`bandpass`/`notch(freq, q = FILTER_Q.ign, configure)`, `onepole(freq)` and `eq(configure)`; the
flat engine-level doors, their `Double` twins and the flat `IgnitorDsl.Eq.band`/`tap` are gone. `fillFilterEnvelope`
still lives once in `audio_bridge` and runs in the Kotlin door after the lambda (`FilterKnobs.envelope()`), which the
script door calls (`self.lowpass(f, q) { it.configuredBy("lowpass", configure) }`). New default symbols in
`constants/FilterDefaults.kt` (`FILTER_Q`, `EQ_BAND_DB`, `EQ_TAP_GAIN`), read by the doors, the builders and the
node fields (the same numbers); the script keeps its literal `0.707` (KSP literals), and the default-node rows pin
that the two agree. The recorded loss: Kotlin can no longer name one filter-envelope stage (or one curve) at a time;
callers name the four stages in one `adsr`, the left-out ones as their `FILTER_ENV_*` constants (the same node).

The eq is decision A: `EqBuilder` (voice, `IgnitorDsl` knobs) and `KatalystEqBuilder` (chain, `KatalystKnobDsl`
knobs, holding the section list), the same words, defaults and sections. ONE helper, `eqSectionKnobOrNull` in
`audio_bridge`, maps a Katalyst knob onto the section's Ignitor leaf and decides the refusal of an expression; the
Kotlin builder refuses it at build time (`IllegalArgumentException`, "Katalyst eq band(freq = ...) takes a number or
a Kat.param; got a Kat expression", Q49), the script variant pre-checks it (`toEqSectionKatalystKnob`) with the
script's old error. The Katalyst half landed now: only the chain DOOR `KatalystBuilder.eq` stays on today's layering
(in `klangscript-libs`, `configuredBy`) until S5; it now takes a `KatalystEqBuilder`. Generated docs: the moved types
differ only in their `fqcn`; the additions are the two `KatalystEqBuilder` knobs (`band`, `tap`, new short KDocs) and
the Katalyst `eq` door's `configure` type and KDoc (it named the shared `EqBuilder`); every other text is
byte-identical.

Callers: 37 files, mechanical (`.ign` / `.kat`, the builder shape; the warmup vocabulary, `IgnitorDefaults`,
the benchmarks' guitar and `E-eq` cases, 18 `audio_be` specs (`IgnitorFilterKnobsSpec`, `FilterEnvelopeCurvesSpec`, `FilterSlotLayerFillSpec`, `EqIgnitorSpec`, ...), the wire codec and wire-shared JS specs, the
klangscript-libs, sprudel and root specs). One twin-overload row (`IgnitorDslSpec`, "the DSL-typed and scalar
overloads carry the SAME defaults") went with the twins; three Kotlin-door rows that expected a script error from
`KatalystBuilder.eq` now expect the build-time refusal or call the script variant. `KlangScriptFilterDoorParitySpec`
is reworked onto the builder shape (parity of every knob, a default-node row per door, one written-out row per member
knob from both doors, the compound fill from both doors, the Kotlin `passes` coercion and `humanize(false)`);
`KlangScriptEqDoorParitySpec` is new (10 rows: voice and chain, written-out sections, defaults, order, slots, the
refusal per parameter from both doors). Nine mutants went red, restored and `cmp`-checked: `BandFilterBuilder.env`
writing `analog`, the highpass door skipping the depth fill, a wrong `q` default symbol on `bandpass`, the Kotlin eq
refusal removed, the script pre-check removed (an internal error instead of the script error), `scriptTap` on the
chain casting instead of converting, `scriptEnv` casting, the `curves` String form swapping decay and release, and
the voice `tap` swapping `q` and `gain`. `:audio_bridge`, `:klangscript-libs`, `:audio_be`, `:sprudel` and root
`jvmTest` are green, the JS sources of `audio_bridge`, `audio_be`, `klangscript-libs` and `sprudel` compile; the
corpus is GREEN, frozen 3 of 3 identical, 13 live identical, `Kokon` and `Der Schmetterling` edited by the
maintainer's song commit as before; an engagement control (the lowpass door with `q = 0.8`) turned it RED (2 frozen,
5 live moved), restored.

Review round 1 (2026-10-16): no critical or major finding. Applied: rows for the omitted-argument defaults of
`ModAdsrBuilder.curves` (both forms, against the script `curves("lin")`; the reviewer's surviving mutant on the
`String` decay default and a wrong index-form release default now go red, restored and `cmp`-checked); named
arguments at about 150 Kotlin call sites where they could be swapped (`/code-style`); three nits; the S3a cases as
point 9 of the typed-door pattern in `/dsl-design` §3.

## Rollout S3b and S3c (2026-10-16): the envelopes and the effects on their builders

S3b: `AdsrBuilder`, `FmBuilder` and `PitchEnvelopeBuilder` moved to `audio_bridge/EffectBuilders.kt` with typed member
knobs. `AdsrBuilder` collected nullable knobs before; with nothing to fill it is a node builder now (`curves` in the
index form, omitted = `AdsrCurve.Default`, and by pattern point 7 a `String` form through `AdsrCurves.indexOf`;
`declick`). `FmBuilder.adsr` takes the four stages (no lambda: the FM envelope has no curves yet).
`PitchEnvelopeBuilder.adsr(attack, decay, sustain, release, configure)` holds the envelope's default fill, ONCE for both
doors: a curve its `ModAdsrBuilder` lambda left out is the node's default read off a fresh `PitchEnvelope`, so a later
`adsr` replaces an earlier one completely. The Kotlin doors are one function per word: `adsr(attack, decay, sustain,
release, configure)`, `fm(modulator, ratio, depth, configure)` and, new on Kotlin (there was none, Kotlin wrote the
node), `pitchEnvelope(semitones, configure)`. The flat `adsr` with `attackCurve` ... `declick` parameters, its `Double`
twin and the flat `fm` with its four stage parameters are gone.

S3c: `PhaserBuilder`, `ShimmerBuilder` (`floor`, as on the script; Q48's named `floor =` stays superseded) and
`TremoloBuilder` (`range(from, to)`, `shape(index)` and, a name knob by point 7, `shape(name: String)` through
`LfoShapes.indexOf`, the lookup the script variant's `catalogueIndex` uses) moved there too. The doors:
`phaser(wet, rate, center = PHASER_CENTER_HZ.ign, sweep = PHASER_SWEEP_HZ.ign, configure)` (the bus phaser's
constants, the same 1000 Hz, now also the node fields' defaults), `shimmer(wet = SHIMMER_WET.ign, feedback =
SHIMMER_FEEDBACK.ign, tone = SHIMMER_TONE_HZ.ign, pitches = SHIMMER_PITCHES, configure)` (new
`constants/ShimmerDefaults.kt`, read by the node too; `pitches` a plain list) and `tremolo(rate, depth, configure)`;
the flat `Double` doors and the flat tremolo's `shape`, `rangeFrom`, `rangeTo` are gone. The number-only doors of the
family are typed, their `Double` twins deleted: `detune`, `drive`, `crush`, `coarse`, `accelerate`, `pitchMod`,
`pitchModSemitones`, and `shape` and `distort`: `shape(shape = DistortionShapes.SOFT_INDEX.ign, oversample =
SHAPE_OVERSAMPLE.ign)` and `distort(amount, shape = ..., oversample = ...)`, each with the point 7 name form
(`shape: String`, no default, through `DistortionShapes.indexOf`), since `shape` and `oversample` are slot knobs the
runtime reads per event (maintainer, 2026-10-16, review round 1; the first cut kept them plain, `String` and `Int`).
New `constants/ShapeDefaults.kt` (`SHAPE_OVERSAMPLE`, 0, which the node reads too). The script doors convert
(`catalogueIndex`) and delegate; their own `Shape` construction is gone. `octaveUp`
and `octaveDown` were script-only words and got their Kotlin door (`detune(12.ign)`, `detune((-12).ign)`). The
arithmetic (S4) and `blend` (S6) keep their twins for now.

Every script door of the family converts and calls the typed door with the checking wrapper (`self.tremolo(r, d) {
it.configuredBy("tremolo", configure) }`); the shimmer's pitch-list check stays script-side. All eight script knobs are
`internal`, renamed variants (`scriptCurves`, `scriptDeclick`, `scriptAdsr` on the pitch envelope and the FM,
`scriptFloor` on the phaser and the shimmer, `scriptRange`, `scriptShape`). Callers: 41 files, mechanical (the
warmup vocabulary, `IgnitorDefaults`' `sgpad` and `sgbell`, the benchmarks' thud, guitar and `tube4x` cases, 23
`audio_be` specs, the JS wire codec spec, the klangscript-libs, sprudel and audio_bridge specs); Kotlin call sites name
the arguments that could be swapped (`phaser(wet = ..., rate = ...)`, `fm(modulator = ..., ratio = ..., depth =
...)`), and `.copy(floor = ...)` became the builder's `floor`. Generated docs: the six moved types differ only in their
`fqcn` (`script.stdlib` to `audio_bridge`); every KDoc text is byte-identical.

`KlangScriptEffectDoorParitySpec` is new (52 rows, each from both doors): a default-node row per Kotlin door (16,
written out with literal numbers, never a constant), door-input rows (every defaulted argument off its default, and a
signal in each door), one row per member knob against the node written out with `.copy()` (16, with a guard that each
moves its knob off the default, the omitted-argument defaults of both `curves` forms included), the tremolo's unknown
name, and three rows for the pitch envelope's fill through both doors (a left-out curve, a second `adsr` call, a
slotted depth). Eight mutants went red, restored and `cmp`-checked: `FmBuilder.adsr` writing `decay` from `sustain`,
the pitch envelope's fill reading the builder's node instead of a fresh default (the skipped fill), a wrong default
symbol on the shimmer's `tone`, `scriptDeclick` casting instead of converting (an internal error on a number), the
tremolo `String` form mapping one shape off, `distort` dropping its `shape`, `octaveDown` passing +12, and a wrong
decay default in `AdsrBuilder`'s name form. `:audio_bridge`, `:klangscript-libs`, `:audio_be`, `:sprudel` and root
`jvmTest` are green, the JS sources and JS tests of `audio_bridge`, `klangscript-libs`, `audio_be`, `sprudel` and the
root compile; the corpus is GREEN, frozen 3 of 3 identical, 13 live identical, `Kokon` and `Der Schmetterling` edited by
the maintainer's song commit as before; an engagement control (the Kotlin `adsr` door building with `declick =
0.001`) turned it RED (2 frozen, 5 live moved), restored.

## Rollout S4 (2026-10-16): the arithmetic, the `Double` twins out (Q47)

The Ignitor arithmetic in `audio_bridge/IgnitorDsl.kt` is ONE typed function per word, every operand an `IgnitorDsl`:
the fifteen `Double` twins are gone, the two operators among them (`plus`, `times`) included, so Kotlin writes
`saw * 0.5.ign`, `x.clamp(lo = -0.5.ign, hi = 0.5.ign)`, `x.range(from = 200.ign, to = 800.ign)`. Deleted: `plus`,
`times`, `mul`, `div`, `minus`, `pow`, `min`, `max`, `clamp`, both `lerp` twins, `range`, `rangex`, `mod`, `select`.
`blend`'s twin stays for S6. The operators stay extensions (a member would beat `Constant.unaryMinus`); `minus` and
`div` stay plain functions, as before (no new operators). The `min` / `max` crossing (guardrail) lives once, in the
typed functions; the script variants delegate and never build the node. `clamp` keeps `lo` / `hi`: the script door
has those names (`clamp(hi = 0.75, lo = -0.5)` is a script call in the parity rows), and renaming the Kotlin side alone
would break parameter parity.

The script side is intact. Every arithmetic word of `KlangScriptIgnitorExtensions` (35 methods, the one-operand words
and `pregain` included) is an `internal`, renamed variant (`@KlangScript.Method(name = "mul") internal fun
scriptMul(self, other)`) that converts once (`toIgnitorDsl`) and calls the typed function; an alias calls the typed
function of its main word (`add` and `plus` both `self.plus(...)`, `sub` `minus`, `mix` `lerp`, `rem` `mod`, `power`
`pow`, `negate` `neg`, `reciprocal` `recip`). Before, each built its node itself. The interpreter has no operator
overloading for native values (`Interpreter.evaluateBinaryOp` takes numbers and strings only), so the script's
arithmetic on a signal was and is the method, `saw.mul(0.5)`; `Ign.sine(2) * 0.5` is a `KlangScriptTypeError`
before and after, pinned by a row so the step is seen not to move it. A recorded asymmetry (review round 1): Kotlin
has `+` / `*` on signals, the script is method-only until [`klangscript-native-object-operators.md`](../../tasks/klangscript-native-object-operators.md)
lands (decided 2026-10-07: script operators on native objects through `__times__` / `__rtimes__`, the goal `saw * 4`);
the pinning row goes red on purpose then. `GeneratedStdlibDocs.kt` is byte-identical
(the KSP docs use the script name); the registration calls the renamed variants.

Callers: 15 files, mechanical (`.ign`): six `audio_be` specs (`FmModulatorFollowsPitchSpec`, `FmModulatorTopologySpec`,
`IgnitorParallelSpec`, `PitchModSemitonesSpec`, `SineExplicitPartialsSpec`, `VibratoCompositionSpec`), the benchmark's
thud (`IgnitorBenchmark`), the two JS wire specs of `audio_bridge`, four `klangscript-libs` specs (bands, blend,
parallel, pitch modulation) and the root `BandsCrossoverSpec`. No engine code, no default, no `classic()` and no
`sprudel` source used a twin; the design's estimate of about 30 files was high, most callers wrote nodes or were moved
already. `KlangScriptIgnitorArithmeticDoorParitySpec` is rewritten (19 rows): per word the node written out by hand
with distinct values, the Kotlin call (`x + 0.5.ign`, `x * 0.25.ign` and the named forms) and every script spelling
and alias against it; the crossing pinned on both doors with a number and with a signal; every two-operand word with a
signal from the script; the one-operand words in one table row; `-1.ign` beside the arithmetic (`sine * -1.ign` is the
script's `mul(-1)`); and the script's numbers-only operators. Six mutants went red, restored and `cmp`-checked: the
typed `range` swapping its ends (the written-out row), the crossing "corrected" on `max` and, separately, on `min`
(red in this spec AND in `StdLibIgnitorTest`, the guard unchanged), `scriptMul` casting instead of converting (an
internal error on a number), the alias `reciprocal` calling `sq`, and a general `unaryMinus` member on `IgnitorDsl`
(`NumberExtensionsSpec` red: `-1.ign` became `Neg(Constant(1))`). `:audio_bridge`, `:klangscript-libs`,
`:klangscript`, `:audio_be`, `:sprudel` and `jvmTest` across all projects are green, every module's JVM and JS test
sources compile (`compileTestKotlinJvm compileTestKotlinJs`); the corpus is GREEN, frozen 3 of 3 identical, 13 live
identical, `Kokon` and `Der Schmetterling` edited by the maintainer's song commit as before; an engagement control (the
typed `mul` scaling its operand by 0.9, which every script `mul` reaches through its variant) turned it RED (3 frozen,
13 live moved), restored.

## Rollout S5 (2026-10-16): the Katalyst on its builders

`KatalystBuilder`, its stage builders (`KatalystBodyBuilder`, `KatalystVowelBuilder`, `KatalystDelayBuilder`,
`KatalystPhaserBuilder`, with `floor` and `cap`) and the modulators' builders (`KatalystSineBuilder`, `Saw`, `Ramp`,
`Tri`, `Square` with `phase` and `duty`) moved to `audio_bridge/KatalystBuilders.kt` with typed members. Every stage
door is a member (`body`, `vowel`, `delay`, `reverb`, `phaser`, `compressor`, `limiter`, `distort`, `duck`, `eq`,
`gain`, `classic`, `append`, and the arithmetic stages `plus`, `minus`, `times`, `div`, `neg`, `abs`, `pow`, `min`,
`max`, the crossing once in the member); its parameters are non-null with the bare stage's constant
(`delay(wet = DELAY_WET.kat, time = DELAY_TIME_SECONDS.kat, ...)`), and the script variant substitutes the same symbol
for its `null`. The one nullable parameter is the reverb's `lowpass`, whose bare value is `null` itself (the engine's
damping, no wire constant), passed through. The limiter preset numbers, the `classic` once-only logic
(`containsInOrder`) and the eq stage's section building live in the members now. Name knobs: `body(wet, material:
String, configure)` and `vowel(wet, vowel: String, configure)` beside the index forms, through `BodyMaterials.indexOf`
/ `VowelBands.indexOf` (the lookups the script's `catalogueIndex` uses); `distort(amount, shape: String =
DistortionShapes.names[SOFT_INDEX], oversample = DISTORT_OVERSAMPLE)` takes the name only, as the script does (`shape`
and `oversample` stay plain, fields of the stage, Q51; a first cut's Kotlin-only `shape: Int` form was dropped,
coordinator: the doors offer the same forms, point 7 corrected). The reverb's nullable `lowpass` was accepted by the
coordinator. `Kat.build(configure)` and `Kat { ... }` are new on the
typed `Kat`; its five periodic modulators are `(freq, configure)` on the builders (step 1b's flat `phase` / `duty`
parameters gone), `perlin` and `berlin` stay flat. The knob arithmetic is one typed function per word in
`audio_bridge/KatalystKnobDsl.kt` (`Kat.param("fader", 1.0).times(0.5.kat)`, precise node types, plain functions, no
operators as before).

The script side converts and delegates: every chain knob, stage-builder knob, modulator-builder knob and knob-arithmetic
word in `klangscript-libs/KatalystBuilders.kt` is an `internal`, renamed variant (`scriptReverb`, `scriptFloor`,
`scriptPhase`, `scriptTimes`, ...; an alias calls its main word's member, `scriptAdd` calls `plus`), and the script `Kat`
object's `build`, `classic`, `param` and modulators call the typed `Kat` (`KotlinKat.build { it.configuredBy("Katalyst",
configure) }`). The script `eq` door is `this.eq { it.configuredBy("Katalyst eq", configure) }` onto S3a's
`KatalystEqBuilder`. `toKatalystKnob`, the eq pre-check, `configuredBy` and `runStage` stay script-side; `serial`,
`parallel`, `blend` and `bands` keep today's layering for S6 and compile against the moved builder (`blend` passes
`share.kat`; `append` is public for them). Generated docs: the ten moved types differ only in their `fqcn`
(`script.stdlib` to `audio_bridge`); every KDoc text is byte-identical. Callers: `audio_benchmark` (`AllocationCases`,
`EffectBenchmark`) off `stdlib.Kat` onto the typed `Kat` (the `katalyst { }` helper is `Kat.build`), the root
`BandsCrossoverSpec`, and eight `klangscript-libs` specs; no `audio_be` or `sprudel` spec built a chain through the old
door.

`KotlinKatObjectParitySpec` (37 tests) is on the builder shape: the parity rows (the modulators with `configure`, and
seven chain rows through `Kat()`, `Kat.build` and every builder shape), a default-node row per stage door, written out
with literal numbers from both doors (14, the limiter preset and the three name forms included), one row per member
knob and per stage-door parameter against the stage written out with `.copy()` (50, each guarded off its bare stage,
from Kotlin and from the script), the modulator members, `classic` once-only and the eq refusal (Q49) through both
doors. The six Katalyst parity specs (door, arithmetic, knob expression, modulator, and the serial / parallel / blend /
bands rows) call the typed door for Kotlin and the script variants from Kotlin for the omission and refusal rows.
Eight mutants went red, restored and `cmp`-checked: the phaser member writing `center` from `sweep`, a wrong default
symbol on the member's `delay(time)`, the same wrong symbol in `scriptDelay`'s `null` substitution, `scriptGain`
casting instead of converting, `classic` appending twice, the knob `plus` building `Times`, a wrong limiter `knee`
preset, and the body name form looking up `VowelBands`. `:audio_bridge`, `:klangscript-libs`, `:audio_be`, `:sprudel`
and root `jvmTest` are green, the JS sources and JS tests of `audio_bridge`, `klangscript-libs` and the root compile,
`:audio_benchmark:compileKotlinJvm` too; the alloc harness is GREEN, 80 of 80 within budget; the corpus is GREEN,
frozen 3 of 3 identical, 13 live identical, `Kokon` and `Der Schmetterling` edited by the maintainer's song commit as
before; an engagement control (the typed `reverb` member scaling `wet` by 0.9, which every script `k.reverb` reaches
through its variant) turned it RED (2 frozen, 4 live moved), restored.

Review round 1 (2026-10-16): code clean; DSL one minor, applied. Kotlin mirrors the script's words, aliases included
(coordinator: porting is the goal, `mid.distort(...).mul(0.80)` ports as `.mul(0.80.kat)`): each alias is a one-line
typed delegate to its main word, on `KatalystBuilder` and `KatalystKnobDsl` (`add`, `sub`, `mul`, `power`, `negate`)
and on the Ignitor (`add`, `sub`, `mix`, `rem`, `power`, `negate`, `reciprocal`, and `mul` now delegating to `times`),
each with a written-out row; the S4 sub-point that said "Kotlin has one word per concept" is superseded in point 9.
Nits: point 7 says "per knob" and how a mixed `curves` call is written; `door-shapes.md` has `lookahead` on the
compressor row, a Katalyst `limiter` row, and the build-time sentence no longer contradicts `distort`'s name-only
`shape`; the name-form rows use literal indices; the saw, ramp and tri default nodes are written out.

## Rollout S6 (2026-10-16): `bands`, `serial`, `parallel` and `blend`

`IgnitorBandsBuilder` and `KatalystBandsBuilder` moved to `audio_bridge/BandsBuilders.kt` with typed members,
`band(processor)` and `cut(freq: Double)`, and the Linkwitz-Riley crossover (`coerceCut`, the sides, the all-pass, the
two `split`s) lives there once. `cut` stays plain by the §3 criterion: the script takes a plain number, and the runtime
never reads it from a slot (the split writes it into the EQ sections as a `Constant`, the order coercion needs the
number when the split is written). The typed `split` only invokes the processors it holds; the script variant
`scriptBand` wraps the script's processor in its stage check (`checkedStage` over `runStage`, new in `Configure.kt`,
`runSerialStages` gone) with the band and processor numbers taken when it wraps (`processors.size`,
`processors.last().size`), so the messages are unchanged. `scriptCut` passes the number through. The Kotlin doors:
`IgnitorDsl.bands(configure)` (new in `IgnitorDsl.kt`), and `serial`, `parallel`, `blend` and `bands` as members of
`KatalystBuilder` (`serial` folds, `parallel` gives each branch an empty builder, `bands(configure)`). `blend`: the
Ignitor's is ONE typed door, `blend(wet: IgnitorDsl, branch)`, its `Double` twin from S4 gone (the script takes a
number, a slot or a signal); the Katalyst's is `blend(wet: Double, branch)`, since the script takes a plain number
only (the recorded Q14 asymmetry), with the NaN guard once in the member; the script's `wet` check (a number or a Kat
constant, else the script error) stays in `scriptBlend` (pattern point 5). The Ignitor `serial`, `parallel` and
`blend` were typed already; their script doors are now `internal`, renamed variants (`scriptSerial`,
`scriptParallel`, `scriptBlend`, `scriptBands`, on both DSLs) that convert, wrap each stage or branch in its check and
call the typed door; the script `parallel` no longer builds its `Parallel` node itself. No word was script-only or
Kotlin-only, so no alias or new word was needed.

A KSP fix came with it: `KlangScriptProcessor` recorded a top-level extension function's RECEIVER file as the source
of the aggregating outputs, and once the receiver moved to `audio_bridge` (no file in this module) the function's own
file was not recorded. An incremental KSP run that did not dirty that file then dropped its registrations from the
regenerated outputs (seen: `GeneratedStdlibBandsBuildersRegistration.kt` vanished and every script `cut` and `band`
was "no method"). The gap predates S1: it covers any file holding only top-level extension functions on a receiver with no file in the
module, so `sprudel/src/commonMain/kotlin/lang/lang_structural_chunk.kt` (only `String` extensions, a type with no file
at all) had it too, and the other builder files survived only by chance, made dirty through their lookups of
`toIgnitorDsl`. The fix covers all of them: the collection is a pure function, `collectSourceFiles` in
`klangscript-ksp/SourceFiles.kt`, which adds every type extension's method files, pinned by `SourceFilesTest` (a
receiver without a file, its function's file among the sources; dropping that line went red, restored and
`cmp`-checked). Three KSP runs in a row (full, incremental, up to date) keep 179 functions, and `sourceToOutputs` lists
the four builder files.

Callers: the root `BandsCrossoverSpec` (`sine(f).bands { ... }`, `KatalystBandsBuilder` from `audio_bridge`), the
benchmark's `AllocationCases` (the stdlib imports gone, the members reached), and the four door-parity specs; no
`audio_be`, `sprudel` or song caller needed a change. `GeneratedStdlibDocs.kt` differs only in the two bands builders'
`fqcn` (`script.stdlib` to `audio_bridge`); every KDoc text is byte-identical. New rows: in
`KlangScriptBandsDoorParitySpec` the split WRITTEN OUT by hand on both DSLs (each cut's sections, the all-pass, the
bands in order, a distinct processor per band) from both doors, the members written out (a lone cut, two processors
summed, a falling cut coerced), default-node rows from Kotlin (`bands()`, `bands { it }`, one processor), and the band
and processor numbers in the script's errors (processor 2 of band 3, and so on); in `KlangScriptBlendDoorParitySpec` the
Ignitor node written out with a number and with a param from both doors, the Katalyst chain written out from Kotlin,
and `scriptBlend` given a Kat constant; written-out rows for the Kotlin `parallel` (both DSLs) and the Katalyst
`serial`. Five mutants went red, restored and `cmp`-checked: the Ignitor `cut`
member prepending its cut (the two cuts swapped), the Katalyst split handing band k the processors of band N-1-k, the
Ignitor `blend` share inverted, `scriptBand` handing the processor on without its check, and `scriptBlend` casting `wet`
instead of converting. `:audio_bridge`, `:klangscript-libs`, `:audio_be`, `:sprudel`, root `jvmTest` and
`:klangscript-ksp:test` (with `SourceFilesTest`) are green; `compileKotlinJs` and `compileTestKotlinJs` of `audio_bridge` and `klangscript-libs`
and `:audio_benchmark:compileKotlinJvm` too. The corpus baseline was refreshed: `Kokon` and `Der Schmetterling` (the songs with `bands`
and `serial`) had read "edited" since the maintainer's song commit, so the corpus did not guard them. A plain
`corpus-accept` (no red row) on this step's tree (`a7c25296-dirty`, S6 to be committed on it) wrote their live rows,
the song texts the maintainer's as of `13019900` (unchanged since); `corpus` then read GREEN, frozen 3 of 3 identical,
live 15 identical, 0 edited. An engagement control (every cut scaled by 1.1 in `coerceCut`) turned it RED, both songs
"moved by the engine", restored and `cmp`-checked.

## Rollout S7 (2026-10-16): the visibility sweep

The script door objects in `klangscript-libs` are `internal` now: `KlangScriptIgnitor` and the script `Ign`,
`KlangScriptIgnitorExtensions`, `KlangScriptKatalyst` and the script `Kat`, the 28 slot objects
(`KlangScriptIgnitorSlots`, the classic slot groups, `KlangScriptKatalystSlots` and its stage groups), the two
conversions `toIgnitorDsl` and `toKatalystKnob` (public extensions on `Any` until now) and the `IgnitorDslLike` alias.
The builder knobs, the arithmetic and the composition variants were internal already (S1 to S6). Public by design,
since other modules use them and they are no door: `ignitorSlotName` / `katalystSlotName` with `IgnitorSlotLike` /
`KatalystSlotLike` (sprudel's `ignp` / `katp`, the one remaining public `Any` parameter, kept on purpose), the engine
entry (`klangScript()`, `stdlibLib`, `KlangStdLib`), `ConsoleLevel` (the app's player), `Interval.ratio` and the
script constants `PI` / `E` (no door, left as they were); `Interval.ratio` is the one Kotlin door kept in the module, for `tones`, not an audio door. Callers moved: three sprudel specs (`EmptyVariantsDoorRenderSpec`,
`VariantsNumberChildRenderSpec`, `LangSlotSetterSpec`) called `KlangScriptIgnitor.sine` / `variants` as their Kotlin
door and now call `Ign.sine()`, `Ign.variants(400.ign, 1200.ign)`; nothing in `src/`, `audio_be` or `audio_benchmark`
called a script door. Leftovers: none. No public `IgnitorDslLike` or `Any`-typed Kotlin door is left outside the
script door, no `Double` twin is left in `IgnitorDsl.kt` or `KatalystKnobDsl.kt`, and every Kotlin door the script
door calls is typed; `optimizer(on: Int = 1)` keeps its `Int`, a plain node field the script takes plain as well
(§3 criterion, not a twin).

Proofs: a scratch file in `sprudel` commonTest (removed) importing `KlangScriptIgnitor`, `KlangScriptIgnitorExtensions`,
`KlangScriptKatalyst`, `KlangScriptIgnitorSlots`, the script `Ign`, `toIgnitorDsl` and `toKatalystKnob` failed with
"Cannot access ...: it is internal in file" on every one, while `Ign.saw(110.ign) { it.analog(5.ign) }`,
`Kat.sine(0.7.kat)` and `ignitorSlotName(...)` compiled. The 19 KSP outputs, `GeneratedStdlibDocs.kt` among them,
were regenerated byte-identical (visibility changes no docs). Under the build lock, one call at a time: `jvmTest`
across every project, `compileKotlinJs` and `compileTestKotlinJs` across every project with
`:audio_benchmark:compileKotlinJvm`, and `:klangscript-ksp:test` are green; the corpus is GREEN, frozen 3 of 3
identical, live 15 identical, 0 edited. A pure visibility change needs no mutation check (decision 2). The rule texts
lost their transition markers ("target state", "until the rollout"): the "Two doors, one DSL" row in `CLAUDE.md`,
`/dsl-design` §3 (the layering, and the filter-curve note of §4), `door-shapes.md` and `klangscript-libs/CLAUDE.md`
describe the code as it is; the typed-door pattern stays in `/dsl-design` §3 as the reference.

## Left after the rollout

Each open point has its own home, or needs one:

- **Sprudel's own typed Kotlin door** (its `PatternLike = Any` doors): flagged during the rollout, out of its scope,
  needs its own design. No task file yet; where it lives is the maintainer's call.
- **Script operators on signals** (`saw * 4` in KlangScript; today `saw.mul(4)`, and a row pins `saw * 0.5` as a type
  error that goes red on purpose when it lands): [`klangscript-native-object-operators.md`](../../tasks/klangscript-native-object-operators.md).
- **Q14, the Katalyst `blend` taking a `Kat.param`** (its Kotlin `wet` then becomes a `KatalystKnobDsl`):
  [`katalyst-knob-followups.md`](../../tasks/katalyst-knob-followups.md) §4.

## Rollout steps (coordinator, 2026-10-16; replace steps 2 to 4)

Each step follows the typed-door pattern in `/dsl-design` §3: builders into `audio_bridge` with typed members,
renamed internal script variants, the script door converting and delegating, written-out-node rows per knob.

| Step | Scope | From the design step's cut |
|------|-------|----------------------------|
| S2  | the other oscillators (`Ign.sine` with its partial banks, square, tri, ramp, zawtooth, zamp, impulse, pulze, the super oscillators with `phasePool`, pluck, superpluck) onto their builders | new with the revision |
| S3a | filters and eq (two typed eq builders, voice and Katalyst, one helper with the Q49 refusal) | step 2a |
| S3b | adsr, fm and pitchEnvelope | step 2b, part of 2d |
| S3c | phaser, shimmer (`floor` as a builder knob) and tremolo | part of 2d |
| S4  | the arithmetic; the `Double` twins out (Q47) | step 2c |
| S5  | Katalyst: the chain door `Kat.build` / `Kat(...)`, its stage builders and the knob arithmetic (renamed variants) | step 4 |
| S6  | `bands`, `serial`, `parallel` and `blend` (shared only where no `Any` parameter remains, Q50) | new with Q50 |
| S7  | the visibility sweep: every remaining script door and builder knob `internal` (done 2026-10-16) | step 3 |

The file counts per step come from the design report's table, which this record does not carry; the coordinator
holds it.
