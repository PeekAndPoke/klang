# Configure lambdas + builder types on every sub-typed DSL door

> Status: **COMPLETE 2026-09-06.** Every step S0 to S7 is built, review-looped and committed on
> branch `dsl-adjustments` (commits e889f388, b1f37514, 644886dc, 998a735b, 7572773a). Kept as the
> record of the decisions and of what was migrated; the standing rules live in `/dsl-design`.
> Priority was **SHOULD** for V1 ("widen and harden the interface", `_v1-scope.md` line 2).
> **No backward compatibility.** The old sub-type chain form is removed, not deprecated. Clean
> foundation is the goal; every caller in the repo is migrated in the same change set.

## Why

Every DSL door that returns a *sub-type* with its own config methods breaks the chain as soon as
a base-type method is called:

```javascript
Osc.supersaw().voices(9).spread(0.1).lowpass(800).analog(3)
//                                   ^^^^^^^^^^^^ returns IgnitorDsl.Lowpass(inner = SuperSaw)
//                                                ^^^^^^^ .analog() lives on SuperSaw: unreachable
```

Today this is papered over with a documented rule ("config first, base wrappers last") repeated
in 17 KDoc headers. The rule is a symptom. The wrapper hides the source, so no chain order fixes
it structurally.

The fix: the sub-type configuration happens inside a lambda that receives a **dedicated builder
type**. The builder exposes ONLY that node's knobs, so calling a base wrapper inside the lambda is
not a runtime error, it is simply not offered. Outside the lambda, only base wrappers exist.

```javascript
Osc.supersaw(x => x.voices(9).spread(0.1).phasePool()).lowpass(800).adsr(0.01, 0.3, 0.5, 0.5)
//           ^ x: OscSuperSawBuilder            ^ back to IgnitorDsl
```

"Inside the braces you configure the oscillator, outside you process it." Same idiom the builtin
songs already use 32 times for patterns (`.superimpose(x => x.transpose("12").gain(0.5))`), so it
teaches nothing new. It also deletes the named-argument trap of the current form
(`Osc.supersaw(voices = 9, spread = 0.1)` fails at runtime today, see the safe-literal rule in
`/dsl-design` section 3): there is no leading parameter left to skip.

### Decisions (maintainer, 2026-09-05)

| # | Decision |
|---|----------|
| D1 | `Master(...)` / `Pipeline(...)` as callable objects go through the existing **native-object operators plan** (`klangscript-native-object-operators.md`, `invoke`). **That plan is revisited and revised first** (see "Prerequisite"). |
| D2 | **Remove everything the builders make redundant.** `MasterFx`, `Master.of`, `Pipeline.of`, `Stage`, all 17 `IgnitorDsl.*` sub-type extension objects, the `Eq`/`Phaser`/`Shimmer` knob objects, the `MasterStageDsl.*` and `StageDsl.*` extension objects, the `PipelineDsl` tweak knobs. No deprecation period. |
| D3 | Configure lambdas take a **builder type**, e.g. `Osc.sine(configure: (x: OscSineBuilder) -> OscSineBuilder)`. The knobs live on the builders ONLY. **`.analog()` and every other sub-type method disappear from `IgnitorDsl`.** (The `analog` *parameter* of the filter doors, `lowpass(freq, q, passes, analog)`, is a different thing and stays.) |
| D4 | Parameter name: `configure`. |
| D5 | `.phaser()` and `.shimmer()` get configure lambdas too. |
| D6 | Builder knobs are annotated **directly on the builder classes**; no delegate objects. More explicit, and open for oscillator-specific extensions. **Revised 2026-09-06:** the builders live in the new `klangscript-libs` module (not `audio_bridge`), after the module split of `klangscript-libs-split.md`; `audio_bridge` can never depend on the language runtime the generated registration needs. |
| D7 | Door parameters: `freq` (oscillators) and the wrapper's own inputs stay on the door. **Anything with a default is a knob** and lives on the builder. |
| D8 | `Master()` is the unity master and `Master(configure)` builds a chain. During the `invoke` rollout they are **aliases**: `Master(...) == Master.build(...)` and `Master() == Master.default()`, so the callable object can be tested against the method form. Same for `Pipeline`. |
| D9 | **Everything is immutable.** Builders are value wrappers; every "mutating" call returns a new instance with the updated values. Composition falls out of this. **General principle for ALL DSLs**, not only builders. |

### General principle: immutable DSLs

Every DSL value (nodes, builders, `MasterDsl`, `PipelineDsl`, patterns) is immutable. A
"mutating" operation returns a new instance with updated values and leaves the receiver untouched.

**Why (maintainer, 2026-09-05):** it removes an entire bug class, shared-state mutation at a
distance, and therefore removes an entire chapter of explaining. Nobody has to learn when a
value is "still being built", whether storing it in a `let` is safe, or why a sound changed
because a builder was touched somewhere else. There is nothing to explain because nothing can
happen. Composition falls out of the same property: a builder or node can be stored, reused in two
places, and configured differently in each without either seeing the other's changes.

```javascript
let base = Osc.supersaw(x => x.voices(7).spread(0.15))
let bright = base.lowpass(4000)          // base is unchanged
let dark = base.lowpass(600)             // both share the same source tree by value
```

Checked in reviews: no `var` in a DSL class, no `MutableList` escaping a builder, every knob is a
`copy(...)`.

**Scope of the principle: construction time.** Sprudel follows it too, every pattern combinator
returns a new pattern. At RUNTIME sprudel deliberately uses mutable objects (single-owner
`SprudelVoiceData`, see `sprudel/MEMORY.md`, leaf clone ~17x faster). That is a
performance decision inside the engine, invisible to the authoring surface, and it stays. The
principle governs what a user (or a Kotlin caller) holds in their hands, not what the query or
render loop does with it internally.

### Language direction, recorded

The maintainer considered moving KlangScript to a Kotlin subset (trailing lambdas `f { ... }`,
receiver lambdas). Decided for V1: **config lambdas on the existing `x => ...` syntax, no
grammar change.** Receiver lambdas are parked: they need multiple `this` in scope and only work
on mutable builders. Round trip with Kotlin projects is an editor feature for later (detect pasted
Kotlin and convert; "copy as Kotlin" generated from the AST), not a language feature.
`configure` is always the LAST parameter so a future trailing-lambda syntax would be pure sugar.

## Prerequisite: revise `klangscript-native-object-operators.md`

That plan predates the KSP annotation era (it registers `invoke` by hand via
`registerVarargMethod`). Before S3 it needs a revision round covering:

- **Registration path**: a `@KlangScript.Method("invoke")` (or a dedicated `@KlangScript.Invoke`)
  on an `@KlangScript.Object` member, emitted by KSP like any method, spec-aware (named args,
  defaults, and the R1 lambda rule below must work for `Master(configure = m => ...)`).
- **Interpreter dispatch**: `CallExpression` whose callee evaluates to a native object value looks
  up `invoke` (plan step 3a). Only `invoke` is needed for this task; the arithmetic operators
  are a separate decision and stay in that plan.
- **Analyzer**: `ExpressionTypeInferrer.inferCallExpression` on an `Identifier` that resolves to
  an object with an `invoke` callable returns its return type; hover on `Master(` shows the
  invoke signature; completion after `Master(m => m.` types `m` (R2/R3 below).
- **Docs popup**: how a callable object renders (object docs + invoke signature).
- Scope check against `sprudel-field-accessors.md`, which also depends on `invoke`; both consumers
  must be satisfied by one mechanism.

## The shape, one rule everywhere

```
door(<scalar params...>, configure: ((XyzBuilder) -> XyzBuilder)? = null)
```

- The door builds the node with its defaults, wraps it in the builder, hands the builder to the
  lambda, and unwraps what comes back. The lambda is called ONCE at construction.
- **Builders are immutable value wrappers**: `data class OscSuperSawBuilder(val node: IgnitorDsl.SuperSaw)`,
  every knob returns a new builder. No mutable state, no ordering traps, same model as every DSL
  node (D9, the general principle above).
- The resulting tree is bit-identical to what the old chain produced. No new node kinds, no wire
  change, `IgnitorDslIdentity`/`uniqueId` untouched.
- Error contract (coerce where sensible, clear errors otherwise, never `require()` on user input):
  - lambda returns `null` (block body without `return`): `KlangScriptTypeError`
    "configure lambda for Osc.supersaw returned nothing, return the builder".
  - lambda returns something that is not the builder (a Number, a pattern, an `IgnitorDsl`):
    typed error naming the door and the expected builder.
  - lambda with 0 params is fine (ignores the builder, must still return one). Extra params bind
    `null` (existing `callFunction` zip behaviour).
- Return type of every door is the BASE type (`IgnitorDsl`, `MasterDsl`, `PipelineDsl`). The
  narrow sub-types carry no script methods any more, so the analyzer no longer needs them.

## Runtime (klangscript module)

Already works, verified 2026-09-05:

- `NativeInterop.convertToKotlin` turns a `FunctionValue` into a Kotlin `FunctionN`
  (`convertFunctionToKotlin`); `callFunction` unwraps the result with `result.value`, so a
  builder comes back as the Kotlin object.
- KSP already emits `ParamSpec(kotlinType = Function1::class)` and casts to the structural type
  (see `filterWhen` in `GeneratedSprudelRegistration.kt`). A nullable `((X) -> Y)? = null`
  default is a `SafeDefaultLiteral`, so the thunk is emitted and named calls resolve.

Missing:

### R1. Lambda floats to the trailing function-typed slot (ONE place)

`Osc.sine(x => x.analog(3))` puts the lambda in the `freq` slot positionally;
`IgnitorDslLike.toIgnitorDsl()` then throws "Expected IgnitorDsl or Number, got Function".

Rule, implemented in `resolveByParamSpec` (`runtime/NativeInterop.kt`), which every spec-aware
call passes through: **when the LAST positional argument is a `FunctionValue`, the spec at its
index is not function-typed, and exactly one function-typed spec follows, the argument binds to
that spec; the skipped slots take their defaults.** Only the last positional argument floats, so
`f(a, lambda, b)` stays an error. Inert for every existing registration (none has a trailing
optional function param). The legacy path in `Interpreter.positionalArgsForNative` is untouched;
every door here has literal defaults and takes the `canResolveAll` branch.

The alignment logic is extracted into a pure function shared with the analyzer (R3). Two copies
of this rule would be a bug farm.

### R2. Function types carry their signature (KSP + KlangType)

KSP emits `KlangType(simpleName = "Function1", fqcn = "kotlin.Function1")` for a lambda param:
the type arguments are dropped, so the analyzer cannot know what `x` is.

- `types/KlangType.kt`: `functionParams: List<KlangType>? = null`, `functionReturn: KlangType? = null`;
  `render()` prints `(OscSuperSawBuilder) -> OscSuperSawBuilder` when set.
- `klangscript-ksp/KlangScriptProcessor.kt::generateKlangType`: for `FunctionN<P1..Pn, R>`
  (detection already exists in `resolveCastType`) emit the component types, following typealiases.
- Popup signature rendering picks up `render()`; adjust `StdlibDocsInferenceTest`.

## Analyzer / editor (intel package)

### R3. Typed lambda parameters

`AnalyzedAst.TypeMapBuilder` binds arrow parameters with `type = null` (comment in the
`ArrowFunction` visit). That is also why there is no completion after `x.` inside
`.superimpose(x => x.` today. Fix for every lambda-taking native, not only the new doors:

- On `CallExpression`, resolve the callable as `ExpressionTypeInferrer.inferCallExpression` does,
  align arguments to `params` with the shared R1 function, and for an `ArrowFunction` argument
  bound to a param with `functionParams`, bind the arrow's parameters with those types in the
  child scope.
- `receiverTypeBeforeDot` then works unchanged inside the lambda body; hover on the param shows
  the LOCAL chip with the type.
- Registry consequence of D3: completion inside `Osc.supersaw(x => x.` lists ONLY the builder's
  knobs (no `supertypes` walk needed, the builder has none). Outside, `IgnitorDsl` lists only the
  wrappers. This is the "wrong function is not even possible" property, visible in the editor.
- Tests: `AnalyzedAstTest` (typed binding, floating alignment, named `configure =`),
  `CompletionProviderTest` (builder knobs inside, wrappers outside), one e2e in
  `ExpressionTypeInferrerE2eTest` for `.superimpose(x => x.`.

## Builders

Live in `klangscript-libs` next to the doors (`klangscript-libs/src/commonMain/kotlin/stdlib/`),
which is also the Kotlin door for them (`audio_be` main code never needs builders; it consumes
nodes). One file per family: `IgnitorBuilders.kt`, `MasterBuilders.kt`, `PipelineBuilders.kt`. Naming
follows the door: `Osc<Name>Builder` for oscillators, `<Effect>Builder` for wrappers,
`MasterBuilder` + `Master<Stage>Builder`, `PipelineBuilder` + `Pipeline<Stage>Builder`.

Knob names, meanings and defaults are exactly today's (parameter-parity rule); only their home
moves. Every knob that today has a KDoc keeps it, word for word where still true.

### Oscillator builders (`stdlib/KlangScriptOsc.kt` doors)

| Door | Builder | Knobs (moved from the deleted `KlangScript*Extensions`) |
|------|---------|----------------------------------------------------------|
| `supersaw`, `supersine`, `supersquare`, `supertri`, `superramp` | `OscSuperSawBuilder` ... `OscSuperRampBuilder` | `voices spread analog spreadPower sideAtten gainJitter centerJitter phasePool(...)` |
| `saw` | `OscSawBuilder` | `analog resetSamples shapeMax` |
| `ramp` | `OscRampBuilder` | `analog resetSamples shapeMax` |
| `square` | `OscSquareBuilder` (wraps `Pulze`) | `duty analog flankSamples riseFlank fallFlank` |
| `pulze` | `OscPulzeBuilder` (wraps `RawPulze`) | `duty`, `analog` (`duty` is new on the script side: the node always had it, the old extension object never exposed it; D7 makes it a knob) | add `configure`; return type now `IgnitorDsl` |
| `sine`, `triangle`, `zawtooth`, `zamp`, `impulse` | `OscSineBuilder` ... | `analog` |
| `pluck`, `superpluck` | `OscPluckBuilder`, `OscSuperPluckBuilder` | `analog` (plus whatever scalar params D7 moves in) |
| `whitenoise`, `pinknoise`, `brownnoise`, `perlin`, `berlin`, `dust`, `crackle`, `silence`, `freq`, `param`, `constant`, `variants` | none | no knobs, **no lambda** (omission is the feature) |

Door signature, the model for all of them:

```kotlin
@KlangScript.Method
fun supersaw(
    freq: IgnitorDslLike = IgnitorDsl.Freq,
    configure: ((OscSuperSawBuilder) -> OscSuperSawBuilder)? = null,
): IgnitorDsl = OscSuperSawBuilder(IgnitorDsl.SuperSaw(freq = freq.toIgnitorDsl()))
    .configured(configure, door = "Osc.supersaw")
    .node
```

`freq` stays the first positional parameter on every oscillator door: `Osc.sine(0.5)` as an LFO
is the most common modulator idiom in the songs (11+ sites) and must not become
`Osc.sine(x => x.freq(0.5))`. The `freq` KNOB is therefore dropped from the builders (one way to
say it). `voices`/`spread` leave the super-oscillator doors and live only in the builder (D7).

`configured()` is one small generic helper (`inline fun <B> B.configured(...)`) that applies the
lambda and enforces the error contract; the stdlib owns it.

### Effect wrappers (`stdlib/KlangScriptOscExtensions.kt`)

| Door | Builder | Knobs |
|------|---------|-------|
| `.eq(configure)` | `EqBuilder` | `band(freq, q, db)`, `tap(freq, q, gain)` |
| `.phaser(rate, center, sweep, configure)` | `PhaserBuilder` | `wet`, `dryFloor` |
| `.shimmer(feedback, tone, pitches, configure)` | `ShimmerBuilder` | `wet`, `dryFloor` |

`.eq()` keeps its merge rule (an `.eq()` on a tree that already ends in an `Eq` continues that
equalizer); the lambda receives the merged node's builder. Nothing else in the base extension
file returns a sub-type with knobs (checked).

The Kotlin door already has `IgnitorDsl.eq/band/tap` extension functions
(`audio_bridge/IgnitorDsl.kt` lines 1853 to 1914). They STAY as the engine-level Kotlin API
(`audio_be` uses them) and `EqBuilder.band/tap` delegate to them; `audio_bridge` cannot see the
builder, so the Kotlin `eq()` gets no `configure` parameter (revised 2026-09-06).

### Master (`stdlib/KlangScriptMaster.kt`)

Today: `master(Master.of(MasterFx.reverb().wet(0.05).roomSize(9), MasterFx.gain(2.5), MasterFx.limiter()))`.

Target:

```javascript
master(Master(m => m
    .reverb(r => r.wet(0.05).roomSize(9))
    .gain(2.5)
    .limiter(l => l.thresholdDb(-3))
))
master(Master())          // the unity master, alias of Master.default() (D8)
```

- `MasterBuilder` knobs: `gain(gain = 1.0)`, `limiter(configure)`, `reverb(configure)`,
  `delay(configure)`. Each appends one stage in call order (the order IS the chain) and returns a
  new builder. Stage builders `MasterGainBuilder` (none today beyond the value), `MasterLimiterBuilder`
  (`thresholdDb ratio kneeDb attack lookahead release`), `MasterReverbBuilder`
  (`wet roomSize damp roomFade roomLp`), `MasterDelayBuilder` (`wet time feedback cap`).
- `Master` becomes callable via `invoke` (prerequisite plan): `Master(configure)`. Method
  aliases stay so the callable object can be tested against them (D8):
  `Master(...) == Master.build(...)` and `Master() == Master.default()`. `build` is the new name
  of today's `of`, taking the lambda instead of a stage list. The spec asserts equality of the
  two forms node-for-node.
- **Removed**: `Master.of`, the `MasterFx` object, the four `KlangScriptMaster*Extensions`
  objects. `MasterDsl.of(...)` on the Kotlin companion STAYS as the engine-level Kotlin API;
  `MasterDsl.default` stays (the engine uses it).
- Sprudel doors `master(...)` (`lang_master.kt`) and `SprudelPattern.master(...)` take a
  `MasterDsl`; unchanged. Their KDoc examples migrate.

### Pipeline (`stdlib/KlangScriptPipeline.kt`)

Today: `Pipeline.of(Stage.filter().drive(2), Stage.vca().expK(3), Stage.distort())`,
presets `Pipeline.modern()`/`Pipeline.pedal()`, and `PipelineDsl` tweak knobs `expK/declick/vcaOn`.

Target:

```javascript
.pipeline(Pipeline(p => p.filter(f => f.drive(2)).vca(v => v.expK(3)).distort()))
.pipeline(Pipeline.pedal(p => p.tuneVca(v => v.expK(3))))  // preset as the starting point, its VCA tuned
.pipeline("pedal")                                         // by name, unchanged
```

- `PipelineBuilder` knobs: `filterMod() crush() coarse() distort() tremolo() phaser()` (append
  the parameterless stage), `filter(configure)`, `vca(configure)`. Stage order = call order =
  topology, as `of` today. `PipelineFilterBuilder` (`cutoffOffset drive drift`),
  `PipelineVcaBuilder` (`expK declick on`). `tuneVca`/`tuneFilter` take a REQUIRED lambda: an
  optional one would make `tuneVca()` a silent no-op (the one exception to "configure is optional").
- Stage knobs ALWAYS append (the whitepaper's double-VCA chain needs two `vca()` calls). Tuning
  a preset's existing stage is a separately named operation, `tuneVca(configure)` /
  `tuneFilter(configure)`, which errors when no such stage exists. Decided 2026-09-06 over an
  append-or-tune rule: one word per operation, no guessing.
- `Pipeline(configure)` via `invoke`, with the same alias pair as Master:
  `Pipeline(...) == Pipeline.build(...)`, `Pipeline() == Pipeline.modern()` (the engine default).
  `Pipeline.modern(configure)`/`Pipeline.pedal(configure)` stay as methods with the new lambda.
- **Removed**: `Pipeline.of`, the `Stage` object, `KlangScriptVcaStageExtensions`,
  `KlangScriptFilterStageExtensions`, `KlangScriptPipelineExtensions`.
- Sprudel inline door `.pipeline(PipelineDsl)` (`lang_pipeline.kt::applyPipeline`) unchanged.

### Not affected

- **Sprudel patterns**: every method returns `SprudelPattern`, no sub-type wall.
- **`SprudelSuperSawEditorTool`** edits the sprudel-level `sndSuperSaw(voices, spread)` call,
  not the Osc chain. Unaffected.
- **OscSlot**, **Math**, **Object**, value-type extensions.
- **Katalyst DSL** (future, `katalyst-dsl.md`): design it in this shape from day one:
  `Katalyst(k => k.reverb(r => ...).delay(d => ...))`.

## What gets deleted (D2)

Stdlib files: `KlangScriptSuperSawExtensions.kt`, `...SuperSine...`, `...SuperSquare...`,
`...SuperTri...`, `...SuperRamp...`, `...Sawtooth...`, `...Ramp...`, `...Pulze...`,
`...RawPulze...`, `...Sine...`, `...Triangle...`, `...Zawtooth...`, `...Zamp...`,
`...Impulse...`, `...Pluck...`, `...SuperPluck...`, `KlangScriptEqExtensions.kt`,
`KlangScriptWetKnobExtensions.kt`, `KlangScriptMasterFxExtensions.kt`,
`KlangScriptStageExtensions.kt`; the `MasterFx` and `Stage` objects; `Master.of`,
`Pipeline.of`.

Every caller migrated in the same change set as its step (S2, S3, S5, S6; the grep of 2026-09-05
gave the upper bounds, `.band(`/`.tap(`/`.spread(` also exist as sprudel pattern methods). The
table is the record of what was found:

| Location | Sites | Note |
|----------|-------|------|
| `src/commonMain/kotlin/builtinsongs` (9 files) + `src/jvmMain/kotlin/SongBenchmarkCases.kt` | ~50 (12 `Osc.supersaw`, 16 `Osc.pluck`, 11 `.eq()`, 10 `.phaser`, 7 `Master.of`, 18 `MasterFx`) | KlangScript inside Kotlin strings. Verify bit-identical: frozen-song specs / `runSongBenchmark` DSL trees before vs after, not by ear |
| `klangscript/src/commonTest` (7 stdlib specs, `StdLibOscTest`) + `jvmTest/intel` (`AnalyzedAstTest`, `StdlibDocsInferenceTest`) | ~115 | Rewrite to the builder form; the super-oscillator specs become the builder specs |
| `audio_be/src/commonTest` (`PhasePoolDslSeamSpec`, `LimiterLookaheadSpec`, `MasterDefaultsSyncSpec`, `MasterStageSpec`) | 16 | Mostly Kotlin-door node construction; switch `MasterDsl.of` to `MasterBuilder` |
| `sprudel/src` (`lang_master.kt`, `lang_effects.kt` KDoc; `LangMasterSpec`, `LangFeedbackCapSpec`, `LangDeletedWetNamesSpec`) | few | KDoc examples feed the popup |
| `.claude/skills/klang-music-writing/ref/*` | 49 | LLM-facing; also fix the stale `.oscParam` casing while there |
| `docs/instrument-prototypes.md`, `docs/whitepaper/klang-whitepaper.html` (canonical HTML only) | few | |
| Live task docs: `master-dsl-followups.md`, `engine-tuning-profile.md`, `pipeline-dsl-coefficient-exposure.md`, `katalyst-dsl.md`, `ignitor-dsl-open-items.md` | examples | Blog posts, plans, archive, diary: history, do NOT touch |
| Tutorials | 0 | Directory currently empty (rework owned by a separate session). Hand that session the builder form BEFORE it writes Ignitor/Master/Pipeline tutorials |

KDoc rewrite: the five super-oscillator constructors and the "config first, base wrappers last"
paragraph everywhere it appears. `klangscript/language-features/04-functions.md`,
`ref/feature-catalog.md`, `ref/intel-analyzer.md` ("parameters bind with type = null" paragraph)
updated.

## Kotlin door

The builders ARE the Kotlin door for the knobs (there were no fluent Kotlin extensions for the
oscillator sub-types before; `dsl-kotlin-surface-parity.md` gap 2 closes here). Kotlin code writes
`OscSuperSawBuilder(IgnitorDsl.SuperSaw()).voices(9).node` or keeps constructing nodes with named
args. Door-parity spec per family, modelled on `KlangScriptFilterDoorParitySpec`: script lambda
form vs Kotlin builder form produce equal nodes.

**Registration (D6, revised 2026-09-06):** the builders and their knobs live in
`klangscript-libs`, where the KSP processor already runs; knobs are top-level extension
functions on the builder (`@KlangScript.Function fun OscSuperSawBuilder.voices(...)`, the shape
sprudel uses for `SprudelPattern`), one implementation, one KDoc, no delegate objects. The
`audio_bridge` variant needed a cross-module generated-source arrangement and was dropped
(`CLAUDE.md`, "Complexity is the enemy"); the module split that made this possible is
`klangscript-libs-split.md`.

A pleasant side effect: oscillator-specific extensions can be added next to the oscillator
(a new knob on `OscSuperSawBuilder` is one annotated function in one file), without touching the
stdlib module at all.

**Door parameters (D7, decided):** `freq` (oscillators) and the wrapper's own inputs
(`phaser(rate, center, sweep)`, `shimmer(feedback, tone, pitches)`) stay on the door.
**Anything with a default is a knob** and lives on the builder. For `pluck`/`superpluck` that
audit happens in S2; the rule decides, not taste per parameter.

All decisions are closed; nothing is open for the maintainer at this point.

## Sequencing

| Step | Scope | Depends on |
|------|-------|------------|
| S0 | ✅ **DONE 2026-09-05.** `klangscript-native-object-operators.md` gained a "Revision 2026-09-05" section: `invoke` via `@KlangScript.Method(name = "invoke")`, dispatch through the spec-aware call path, analyzer `invoke` fallback in `resolveCallable`, signature rendering, alias-first rollout, and the field-accessor consumer. | none |
| S1 | ✅ **BUILT 2026-09-05, four review rounds, committed b1f37514.** R1 floating rule (`runtime/ArgAlignment`, applied in `resolveByParamSpec`), R2 function-type signatures (`KlangType.functionParams/functionReturn`, KSP emits them, aliases followed), R3 typed lambda params in `AnalyzedAst`. Extra hardening: a function value converting to a non-function target is now a `KlangScriptTypeError` on both platforms (was a JVM `ClassCastException`, silent garbage on JS). Tests: `ArgAlignmentTest`, `ConfigureLambdaBindingTest`, `AnalyzedAstTest` ("configure lambda" cases); JVM + JS suites green. | none |
| S1b | ✅ **REPLACED by the module split** (`klangscript-libs-split.md`, done 2026-09-06): builders live in `klangscript-libs`; no build-graph change needed. | none |
| S2 | ✅ **BUILT 2026-09-06, one clean review round, committed 998a735b.** `IgnitorBuilders.kt` (16 immutable builders, knobs as `@KlangScript.Function` extension functions), `Configure.kt` (`configuredBy`, the error contract), all 16 oscillator doors `(freq?, configure?)` returning `IgnitorDsl`, 17 extension objects deleted, `toIgnitorDsl()` refuses a function with a script-level error. `pluck`/`superpluck` keep their SEALED constant defaults (the old doors baked `Constant`s, the nodes carry open `Slots.*` params; the sound-tree baseline caught the difference). Migrated: 6 songs + `SongBenchmarkCases`, 8 oscillator specs rewritten in builder form, analog-surface / phase-pool / osc / slot / docs / analyzer tests, whitepaper, music-writing skill, `OscSlot` KDoc. Migration guard (removed 2026-09-06 once the migration was done): a spec fingerprinting every builtin song's DSL trees over 256 cycles against a checked-in baseline, bit-identical before and after. | S1, split |
| S3 | ✅ **BUILT 2026-09-06, reviewed (doc findings fixed), committed 7572773a.** `EffectBuilders.kt`: `EqBuilder` (`band`, `tap`, delegating to the audio_bridge Kotlin extensions, which stay as the engine-level API used by `audio_be`), `PhaserBuilder`, `ShimmerBuilder` (`wet`, `dryFloor`). Doors `.eq(configure)`, `.phaser(rate, center, sweep, configure)`, `.shimmer(feedback, tone, pitches?, configure)` all return `IgnitorDsl`; `shimmer.pitches` default became `null` (literal) so the lambda can float. Eq/WetKnob objects deleted. Migrated: Der Schmetterling (two eq blocks), osc test eq block, `KlangScriptEffectBuilderSpec` (new parity spec), two sprudel specs, skill reference, instrument prototypes. Sound-tree baseline unchanged. | S1 |
| S4 | ✅ **BUILT 2026-09-06 (core), reviewed with S5 and S6, committed 7572773a.** Interpreter branch for `NativeObjectValue` callees (spec-aware path, error names `invoke`), analyzer fallback in `resolveCallable`, signature rendering `Master(...)`, `invoke` hidden from member completion. Tests `NativeObjectInvokeTest`, `InvokeAnalysisTest`, both mutation-checked. | S0, S1 |
| S5 | ✅ **BUILT 2026-09-06, reviewed, committed 7572773a.** `MasterBuilders.kt` (`MasterBuilder`: gain/limiter/reverb/delay append stages; `MasterLimiterBuilder`, `MasterReverbBuilder`, `MasterDelayBuilder`), `Master.build(configure)`, `Master.default()`, `Master(configure)` via `invoke` (alias spec pins `Master(...) == Master.build(...)`, `Master() == Master.default()`). `Master.of` and `MasterFx` deleted. Migrated: 5 songs, whitepaper, `LangMasterSpec`, sprudel KDoc samples and prose, engine comments, music-writing skill. Baseline (now covering master chains) unchanged. | S4 |
| S6 | ✅ **BUILT 2026-09-06, reviewed, committed 7572773a.** `PipelineBuilders.kt` (`PipelineBuilder`: marker stages + `filter(configure)`/`vca(configure)` APPEND; `tuneVca`/`tuneFilter` configure the stages already present, error when none; `PipelineVcaBuilder`, `PipelineFilterBuilder`). `Pipeline(configure)` via `invoke`, `Pipeline.build`, `Pipeline.modern(configure)`, `Pipeline.pedal(configure)`; `Pipeline()` == `modern()`. `Pipeline.of`, `Stage`, and the three stage knob objects deleted. No song used them; whitepaper and one task doc migrated. | S4 |
| S7 | ✅ **DONE 2026-09-06**, committed 7572773a: language-features 4.10/4.11, feature catalog, intel ref, whitepaper (three examples), music-writing skill (ignitor + sprudel refs), every live task doc that quoted a deleted form (`master-dsl-followups`, `master-limiter-lookahead`, `auto-mix-advisor`, `realtime-analytics-meters`, `audio-bridge-constants`, `ignitor-optimizer-followups`, `dsl-kotlin-surface-parity`, `future/pipeline-oversampling-regions`), engine comments, `audio/`+`klangscript/`+`sprudel/MEMORY.md`, and a dated hand-off note at the top of `tutorial-curriculum.md` for the session that owns it. Home-directory memories: the maintainer's housekeeping session. | S2..S6 |

Each step lands as its own reviewed diff (stop before commit, maintainer inspects). Because there
is no back-compat, S2, S5 and S6 each remove and migrate in ONE diff so the tree never has a
half-state. Bit-identical verification of every migrated song is part of the step, not of S7.

## Links

- Principles: `.claude/skills/dsl-design/SKILL.md` (immutability, door shape, two doors, parity,
  one word per concept, omission). Direction record: `klangscript/MEMORY.md` Design Decisions;
  engine-side record: `audio/MEMORY.md` Architecture Decisions.
- Tasks: `klangscript-native-object-operators.md` (S0 prerequisite), `sprudel-field-accessors.md`
  (second consumer of `invoke`), `dsl-kotlin-surface-parity.md` (gap 2 closes here),
  `katalyst-dsl.md` (adopt the shape), `master-dsl-followups.md`, `klangscript-intellisense.md`.
- Precedent: `sprudel/lang/lang_structural.kt::superimpose` / `filterWhen` (lambda-taking
  natives), `KlangScriptFilterDoorParitySpec` (door-parity test pattern).
