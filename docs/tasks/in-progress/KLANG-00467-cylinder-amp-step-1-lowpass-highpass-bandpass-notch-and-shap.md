# KLANG-00467: Cylinder amp step 1: lowpass, highpass, bandpass, notch and shape on a Katalyst chain

> **Status (2026-11-18): IN PROGRESS.** Klang-Main-Dev: step 1 starts, decisions settled 2026-11-18
>
> **Part of:** KLANG-00466 (Metal band instruments: the guitar amp on the cylinder, one amp per guitarist)

## Why this exists

Step 1 of KLANG-00466 (the guitar amp on the cylinder). A guitar amp is clippers with filters between them. A
cylinder chain has the clipper (`distort`) and a bell EQ, but no `lowpass`, `highpass`, `bandpass`, `notch` and no
`shape`, so an amp cannot be written on it. This step adds those five words, and `Kat.fn`.

No new DSP is needed for the filters: the cylinder's EQ engine already runs all four section kinds.

## Read first

- `/dsl-design`, all of it. This step is a DSL change on both doors; its review checklist applies item by item.
- `/klangaudio-knowhow`, then `audio/ref/katalyst.md` and `audio/ref/off-values.md`.
- `audio_bridge/src/commonMain/kotlin/KatalystBuilders.kt`: the typed chain doors. `delay` is the template for a
  door with a builder, `distort` for a name knob fixed with the chain.
- `audio_bridge/src/commonMain/kotlin/EffectBuilders.kt`: `KatalystEqBuilder` and `toEqSectionKnob`, the one
  conversion of a chain knob into an EQ section's knob, with its refusal of an expression.
- `audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt`, rule R1: `expandPasses` and `passesLadderRel`, the
  cascade's q ladder. The chain doors must build the SAME sections.
- `klangscript-libs/src/commonMain/kotlin/stdlib/KatalystBuilders.kt`: the script variants (`scriptDistort`,
  `scriptEq`, `scriptDelay`) and `toEqSectionKatalystKnob`; `stdlib/EffectBuilders.kt` for a builder knob's variant
  (`FilterBuilder.scriptPasses`); `stdlib/KlangScriptKatalyst.kt` for the `Katalyst` object.
- `audio_be/src/commonMain/kotlin/katalyst/KatalystDistortEffect.kt` and `KatalystChainBuilder.kt`: how a stage is
  built and what a `KatalystEffect` must do.

## Decide before implementing

Decisions D1 to D7 and D10 of KLANG-00466 are this step's. Settled by the maintainer on 2026-11-18 (KLANG-00466,
"Reworked by the maintainer"): D1 to D5, D7 and D10 as written, D6 reversed. If the code shows one of them cannot
hold, stop and report.

### Added by the rework: `drive` on a chain

The maintainer wants a voice chain to port word for word, and `powerStock` says `drive(0.3)`. So:

- **Typed door** on `KatalystBuilder`: `fun drive(amount: KatalystKnobDsl): KatalystBuilder`, the voice's word
  (`IgnitorDsl.drive`): gain without a curve, by the voice's drive law. Read the voice's `Drive` node and its
  backend first and use the SAME law function (one place for the conversion, `/dsl-design` section 4); do not write
  the formula a second time.
- **How it is stored:** prefer what a chain already has (the arithmetic, `mul`) over a new stage, if the law can be
  written with it for a number and for a `Kat.param`. If it cannot for a slot, a number-only door with the usual
  refusal is acceptable; a new stage only if neither works. Report which it became and why.
- **Script door** `scriptDrive(amount)`, with its KDoc and an audible example; a parity row from the script and from
  Kotlin; a row that `drive(0.3)` on a chain and on a voice give the same gain; a mutant for the law.
- The docs lists of this ticket get `drive` wherever they name `shape`; the recorded asymmetry "a chain has no
  `drive`" is not written.
- The amount of 0: what the voice does at 0 (off or unity) is what the chain does; say it in the KDoc and in
  `audio/ref/off-values.md`.

## The work

### 1. The typed doors (`audio_bridge`)

All members of `KatalystBuilder`, each with a one-line KDoc pointing at its script knob, as the other members have.

```kotlin
fun lowpass(freq: KatalystKnobDsl, q: KatalystKnobDsl = FILTER_Q.kat, configure: ((KatalystFilterBuilder) -> KatalystFilterBuilder)? = null): KatalystBuilder
fun highpass(freq: KatalystKnobDsl, q: KatalystKnobDsl = FILTER_Q.kat, configure: ((KatalystFilterBuilder) -> KatalystFilterBuilder)? = null): KatalystBuilder
fun bandpass(freq: KatalystKnobDsl, q: KatalystKnobDsl = FILTER_Q.kat): KatalystBuilder
fun notch(freq: KatalystKnobDsl, q: KatalystKnobDsl = FILTER_Q.kat): KatalystBuilder
fun shape(shape: String = DistortionShapes.names[DistortionShapes.SOFT_INDEX]): KatalystBuilder
```

- **Each filter door appends ONE `KatalystStageDsl.Eq`** whose sections are that filter:
  `IgnitorDsl.EqSection.Lowpass`, `Highpass`, `Bandpass` or `Notch`. No new stage type for the filters.
- **`freq` is required**, as on `band`. `q` defaults to `FILTER_Q` (0.707), the same symbol the voice's doors use.
- **Knobs convert through `toEqSectionKnob(door, param)`**, so a number or a `Kat.param` is accepted and a Katalyst
  expression is refused with the message that helper already writes. Pass the door's word (`"lowpass"`) and the
  parameter's (`"freq"`, `"q"`).
- **The builder** (new, beside the other chain stage builders in `KatalystBuilders.kt`):

  ```kotlin
  /** Builder for `k.lowpass(freq, q, configure)` and `k.highpass(freq, q, configure)` on a chain. Knob: `passes`. */
  data class KatalystFilterBuilder(val passes: Double = FILTER_PASSES) {
      fun passes(n: Double): KatalystFilterBuilder = copy(passes = n)
  }
  ```

  `passes` is a plain `Double`, read through `coercePasses(Double)` (rounded, 1 to 16, a non-finite value is one
  pass). `bandpass` and `notch` have no knob, so they get no builder and no `configure` (`/dsl-design` section 9).
- **The cascade.** `passes = N` appends N sections of the same kind and cutoff, each with its q on the Butterworth
  ladder, exactly as the voice's optimizer expands a voice filter:
  - one pass: one section with `q` as given;
  - N passes: section k gets `q * passesLadderRel(k, N)`; the middle stage of an odd N has a factor of exactly 1.0
    and gets `q` itself.
  Use the SAME function: make `passesLadderRel` in `IgnitorDslOptimizer.kt` `internal` (it is `private`) and call
  it; do not write the formula a second time (`/dsl-design` section 4, conversions in one place).
- **A slot as `q` with more than one pass is refused** (decision D3): the typed door throws an
  `IllegalArgumentException` naming the door and the parameter, in the wording `toEqSectionKnob` uses for an
  expression. With one pass a slot `q` is fine.
- **`shape`** appends the new stage (below) with the index `DistortionShapes.indexOf(shape).toInt()`; an unknown
  name is `soft`, coerced, never thrown. A name only, as the chain's `distort` takes its shape.
- **The new stage**, in `KatalystDsl.kt` beside `Distort`:

  ```kotlin
  @WireName("shape")
  data class Shape(val shape: Int = DistortionShapes.SOFT_INDEX) : KatalystStageDsl
  ```

  Its KDoc says: the voice's `shape` at the bus position; the curve at unity drive, then the DC blocker; left and
  right each through a core of their own; never off (there is no amount to switch it with); the shape is fixed with
  the chain; inside a region for less aliasing; a boost before it is `mul`.
- **`Kat.fn`** on the typed `Kat` object (`Kat.kt`), the twin of `Ign.fn` in `Ign.kt`:

  ```kotlin
  fun fn(f: (KatalystBuilder) -> KatalystBuilder): (KatalystBuilder) -> KatalystBuilder = f
  ```

### 2. The script doors (`klangscript-libs`)

The typed-door pattern of `/dsl-design` section 3, point by point. In `stdlib/KatalystBuilders.kt`:

```kotlin
@KlangScript.Function(name = "lowpass")
internal fun KatalystBuilder.scriptLowpass(
    freq: IgnitorDslLike,
    q: IgnitorDslLike = 0.707,
    configure: ((KatalystFilterBuilder) -> KatalystFilterBuilder)? = null,
): KatalystBuilder
```

and the same shape for `scriptHighpass`; `scriptBandpass(freq, q = 0.707)`, `scriptNotch(freq, q = 0.707)`;
`scriptShape(shape: String = "soft")`. The builder knob, beside `FilterBuilder.scriptPasses` in
`stdlib/EffectBuilders.kt`:

```kotlin
@KlangScript.Function(name = "passes")
internal fun KatalystFilterBuilder.scriptPasses(n: Double): KatalystFilterBuilder = this.passes(n = n)
```

and `fn` on the script `Katalyst` object (`stdlib/KlangScriptKatalyst.kt`), written as `Ign.fn` is in
`KlangScriptIgnitor.kt`.

- **Convert once and delegate.** `freq.toEqSectionKatalystKnob()` and `q.toEqSectionKatalystKnob()` (the pre-check
  that turns the expression refusal into a script error), then the typed door, with the checking wrapper for the
  lambda: `{ it.configuredBy("Katalyst lowpass", configure) }`.
- **Pre-check the D3 refusal script-side**, so the author gets a `KlangScriptTypeError` with the script's words
  ("a Katalyst lowpass with more than one pass takes a number for q; got a Kat.param"), not the typed door's
  exception.
- **Defaults are safe literals** (`0.707`, `"soft"`); KSP refuses anything else.
- **The user-facing KDoc lives on these variants.** Each says what the filter does to a cylinder's mix, that it is
  the voice's filter (the same sections) at the bus position, what `q` does, that there is no off value (a filter
  written is a filter built; a cutoff that is not finite reads as 1000 Hz, as on a `band`), and that it has no
  sprudel twin (sprudel's `lpf` and its siblings are the voice's doors; the cylinder route is
  `.katalyst(Katalyst(k => k.lowpass(...)))`).
- **Every example must be audible** (checklist item 10), for example:

  ```
  note("c2 e2 g2").s("supersaw").cylinder(1).katalyst(Katalyst(k => k.distort(0.4, "tube").lowpass(1800, 0.707, x => x.passes(2))))
  ```

### 3. The backend (`audio_be`)

- **Nothing for the filters.** They arrive as `Eq` stages, which `KatalystChainBuilder` already builds.
- **`KatalystShapeEffect`**, new, in `katalyst/`. It is `KatalystDistortEffect` without the switch and without the
  amount:
  - two `DistortionCore`s, left and right, built as that class builds them: the shape through
    `distortionShapeAt`, the DC blocker's coefficient from `houseDcBlockCoeff(sampleRate shr firstStage)` mapped by
    `DistortionCore.dcBlockCoefficientAt(coefficient, stages = firstStage)`;
  - `process`: `core.process(buffer, offset = 0, length = ctx.blockFrames, drive = 1.0)` for each side, in place;
  - `reset`: both cores; `retire`: `reset`; `hasTail`: either core's `dcHoldsEnergy(SILENCE_FLOOR)`;
  - `latencyFrames` as the distort effect declares it;
  - no state machine, no fades, no knob: it is on for as long as the chain runs, and a chain swap fades whole
    chains. No allocation in `process`, no exception (the engine rules).
- **The arm in `KatalystChainBuilder`**: `is KatalystStageDsl.Shape -> pipeline.add(KatalystShapeEffect(...))`,
  with the stage count of the region it sits in, as the `Distort` arm passes `firstStage = stages`. No writer.
- **Every exhaustive `when` over `KatalystStageDsl`** gets its arm. The compiler finds them; `Shape` is a plain
  stage, so it joins the list of plain stages in each (`KatalystChainBuilder` has three; other modules may have
  more). Build every module that uses the type before claiming the tree compiles.
- **The probes.** `Shape` is a new stage: read `audio/ref/verification.md`, "The probes", run the ones that apply
  (its core is the distort stage's), and hand their tables to the audio reviewer.

### 4. The tests

Each row names what it pins. New specs unless a file is named.

**`audio_bridge` (commonTest), `KatalystFilterDoorsSpec`:**

| Row | Pins |
|---|---|
| each of the four doors against the stage WRITTEN OUT (`KatalystDsl.of(KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Lowpass(freq = ..., q = ...))))`), with values off their defaults | the door writes the right section kind and fields |
| `lowpass(freq)` with no `q` | the default is `FILTER_Q` |
| `passes` 2, 3 and 4 on `lowpass` and `highpass`, the sections written out with their q values | the ladder, the middle stage of 3 at exactly `q` |
| the same cascade as the voice builds it: `Ign.saw().lowpass(freq = 800.ign, q = 2.ign) { it.passes(3.ign) }.optimize()` holds an `IgnitorDsl.Eq` whose sections equal the chain door's | one section law on a voice and on a chain |
| `passes` of 0, 99, 2.6, NaN | coerced to 1, 16, 3, 1 (check the rounding against `coercePasses`) |
| a `Kat.param` as `freq` and as `q` with one pass | the section carries the `IgnitorDsl.Param` of the same name and default |
| an expression as `freq` or `q` | refused, naming door and parameter |
| a `Kat.param` as `q` with `passes(2)` | refused (D3) |
| `shape()` and `shape("tube")` against `KatalystStageDsl.Shape` written out; an unknown name | the index; `soft` for unknown |
| two doors in a row | two `Eq` stages in written order (merging is step 2's, not the door's) |

**`audio_be` (commonTest, `katalyst/`), `KatalystShapeEffectSpec`:**

| Row | Pins |
|---|---|
| the law for `soft`, `tube` and `asym`, against an oracle WRITTEN IN THE TEST: per sample the shape function, then a one-pole DC blocker with the house coefficient | drive is 1.0, the curve, the DC blocker |
| left and right fed different signals | each side matches its own oracle (a core per side) |
| render, `reset()`, render the same input again | the second render equals the first (the state is cleared) |
| `hasTail()` after a lopsided burst (`tube`), and after it has decayed | true, then false |
| built with `firstStage = 2` at four times the rate | the DC blocker's corner stays where it is in Hz (the distort effect's spec shows how) |
| silence in | silence out, exactly |

**`audio_be`, `KatalystChainBuilderSpec`:** one row that a chain holding `Shape` builds and renders, one that a
chain holding the four filter doors builds and renders the same samples as the same sections written into one
`eq` stage by hand.

**`klangscript-libs` (commonTest, `stdlib/`), `KlangScriptKatalystFilterDoorParitySpec`** (the pattern is
`KlangScriptFilterDoorParitySpec` and `KlangScriptKatalystDoorParitySpec`):

| Row | Pins |
|---|---|
| each door from the script and from Kotlin | equal `KatalystDsl` values |
| the bare forms (`lowpass(800)`, `shape()`) | the script literal equals the Kotlin default symbol |
| `x => x.passes(3)` from both doors | the builder knob |
| an expression as `freq`; a `Kat.param` as `q` with `passes(2)` | a `KlangScriptTypeError` naming the door |
| a `configure` that returns nothing, or a wrong type | a script error naming the door (checklist item 13) |
| `Kat.fn(k => k.lowpass(800))` handed to `serial` | it is the function itself |

Run the error rows on JS too (`:klangscript-libs:jsBrowserTest`): a null check can be compiled away there.

**Also:** a default-node row for each new typed door in `KotlinKatObjectParitySpec`; a round-trip row for
`KatalystStageDsl.Shape` and for an `Eq` holding all six section kinds in `WireCodecRoundTripSpec`
(`:audio_bridge:jsBrowserTest`); and run the specs that list or walk stages (`KatalystDslSlotsSpec`,
`KatalystDefaultsSyncSpec`, `DslDocExamplesSpec`), which may want a row.

**Mutation checks** (mandatory tier; one `mutate` campaign, each mutant red for the reason named):

| Mutant | Must turn red |
|---|---|
| the ladder dropped (every stage gets `q`) | the cascade rows and the voice-parity row |
| `highpass` builds a `Lowpass` section | the written-out row |
| the script default `0.707` changed | the bare-form row |
| the D3 refusal removed | its row |
| `KatalystShapeEffect` drives at 2.0 | the law rows |
| the right side processed by the left core | the left-and-right row |
| `hasTail()` returns false | the tail row |

### 5. The docs, in the same change

- `.claude/skills/dsl-design/door-shapes.md`: rows for the Katalyst `lowpass`, `highpass` (`freq, q = 0.707,
  configure`, builder `passes`), `bandpass`, `notch` (`freq, q = 0.707`), `shape` (`shape = "soft"`), `Kat.fn`.
- `.claude/skills/dsl-design/SKILL.md`, section 4, the recorded asymmetries, one clause each: the chain filters
  have no sprudel twin; they have no `analog`, `humanize`, `env`, `adsr`; `passes` is a plain number fixed with the
  chain, a slot knob on a voice; a slot `q` with more than one pass is refused; a chain has no `drive` (D6).
- `audio/ref/katalyst.md` (the new stage and the doors), `audio/ref/off-values.md` (a chain filter is always
  built; `Shape` is never off), `audio/MEMORY.md` (its section in place, one History line),
  `klangscript-libs/CLAUDE.md` (the layout rows), the `KatalystStageDsl.Eq` KDoc (the doors that build it).
- **Close the old statements by grep.** Docs, KDoc and skills say in places that a chain has no filter, or that the
  four filter sections exist "only as what the optimizer fuses" (KLANG-00250 (general EQ core) says so). Grep the
  repository, `.claude/skills/` included, for the mechanism words (`eqLowpass`, "no lowpass", "bells and taps",
  "only as what the optimizer fuses") and correct each place or add a dated line to it; record the grep and its
  result in the report.
- Run `docs_lint.py` before committing.

## Out of this step

- Merging the stages: KLANG-00468 (step 2).
- Section words inside `eq(...)`, `drive` on a chain, a tremolo stage, any sprudel door.
- The songs.

## Acceptance

- The five words and `Kat.fn` work from KlangScript and from Kotlin, with equal results (the parity spec).
- `:audio_bridge:jvmTest`, `:audio_be:jvmTest`, `:klangscript-libs:jvmTest`, `:sprudel:jvmTest`,
  `:apps:common:jvmTest` (the songs still compile), `:audio_bridge:jsBrowserTest` and
  `:klangscript-libs:jsBrowserTest` are green, read with test-digest and `--expect` naming the new specs.
- The mutation campaign's table is in the report, every mutant red for its reason, the files restored.
- A review round with zero CRITICAL and zero MAJOR findings, code and audio.
- The docs above are updated and lint clean.

## History

- 2026-11-18: created, as step 1 of KLANG-00466.
- 2026-11-18: the maintainer's rework: decisions settled, `drive` added to this step.
- 2026-11-18: IN PROGRESS. Klang-Main-Dev: step 1 starts, decisions settled 2026-11-18
