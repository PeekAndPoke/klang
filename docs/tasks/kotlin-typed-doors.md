# Typed Kotlin doors: the compiler checks every door

Status: **decided (maintainer, 2026-10-13, Q24); design step done and answered 2026-10-16 (option C, with the answers
below, revised the same day: one set of builders, equal doors); steps 1 and 1b done; spike S1 done 2026-10-16 (review
rounds 1 and 2 applied); rollout S2 (the other oscillators) and S3a (the filters and the eq) done 2026-10-16, S3b next (the steps at the end), following the typed-door pattern in `/dsl-design` §3.** Large, mostly mechanical.

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
- Related, separate: curves as nodes ([`future/curves.md`](future/curves.md)), where values plug into nodes the same way.

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
(a new file beside `IgnitorBuilders.kt`, the script side's name), with typed member knobs: `passes(n: Double)` (a
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
| S7  | the visibility sweep: every remaining script door and builder knob `internal` | step 3 |

The file counts per step come from the design report's table, which this record does not carry; the coordinator
holds it.
