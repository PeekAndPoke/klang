---
name: dsl-design
description: Use when someone asks to design a DSL surface, add a DSL door or knob, review a DSL change, add a builder or configure lambda, or asks about DSL immutability, dual surface, parameter parity, or naming across KlangScript, Kotlin, wire and backend.
---

## What This Skill Does

Loads the design principles every Klang DSL follows: IgnitorDsl (`Ignitor.*`), KatalystDsl (on an orbit and,
since phase 3 step 12, at the output through `master(...)`), sprudel patterns, and any DSL still to come. Apply these rules whenever
you add, change, or review a DSL surface, on either door (KlangScript stdlib or Kotlin).

This is a reference skill. It changes how you judge a design; it does not run a workflow.
Companion skills: `/klangaudio-knowhow` (the engine the DSLs drive), `/klangscript-knowhow`
(the language the script door is written in), `/review-loop` (how a DSL change gets reviewed),
`/code-style` (formatting).

---

## Rules

Moved here from the root rules register at the rules retro (2026-10-23), word for word; the levels (stone, rule,
guideline, guardrail) are the ones the root `CLAUDE.md` defines, and the root keeps one pointer row.

| Rule | Level | Since | Detail |
|------|-------|-------|--------|
| Every DSL value is immutable at construction time; runtime mutability is engine-internal (sprudel voice data is the deliberate exception). | stone | 2026-09-05 | `/dsl-design` §1 |
| Two doors, one DSL: every Ignitor and Katalyst surface addition lands on the typed Kotlin door (`audio_bridge`; every audio value an `IgnitorDsl` / `KatalystKnobDsl`, numbers through `.ign` / `.kat`; build-time knobs stay plain by the criterion in §3) AND the KlangScript door (stdlib, internal, number or signal at run time) in the same deliverable, with a door-parity spec. Same word, meaning, scale and default, the same builders and shapes; only the accepted types differ. Only KlangScript-exposed functions take `XxxLike = Any` parameters, converted and checked at run time; the Ignitor and Katalyst Kotlin doors are typesafe. sprudel's doors still take `PatternLike = Any` on one shared function until its own typed door is designed (maintainer's call; see the archived task KLANG-00314 (kotlin typed doors), "Left after the rollout"). | rule | 2026-08, typed door 2026-10-16 | `/dsl-design` §3 |
| Parameter parity: same name, meaning and scale on every surface; conversions in one place; asymmetries recorded with a reason. | rule | 2026-08-02 | `/dsl-design` §4 |
| One word per concept end to end; a replaced surface is removed, not deprecated. | rule | 2026-08 | `/dsl-design` §5 |
| *(provisional)* Units: time always in seconds, pitch always in semitones (where it can be); the unit lives in the KDoc, not the name (`attack`, not `attackSec`). Slots are `<door>.<param>`, the param is the engine door's word, the sprudel door follows. | rule | 2026-10-08 | `/dsl-design` §4 end |
| Door shape: musical inputs on the door (`wet` always first), secondary knobs on a builder behind a `configure` lambda that is last and always optional (the same builder on both doors, §3), dynamics stages flat, one shape per concept across the DSLs. | rule | 2026-09-05, refined 2026-09-23 | `/dsl-design` §2, `.claude/skills/dsl-design/door-shapes.md` |
| Wire types over enums: sealed `@WireName` hierarchies for wire-visible distinctions; enum only for a closed param-less set. | rule | 2026-08 | `/dsl-design` §7 |
| Compound doors fill per param at the door: a call that names a stage writes every companion it left out and the event has not set, from the constant in `audio_bridge/constants/`; an explicit value is never overwritten. A stage with a name knob is named only by that knob, and a tail-only call never invents it; a stage without one is named by any of its knobs. Which door is which: the two closed lists in `/dsl-design` §4, the one home of this rule's text, never copied. | rule | 2026-09-18 | `/dsl-design` §4, checklist 11 and 12 |
| `min`/`max` are clamps on every door: `a.max(b)` is "a, at most b". The Ignitor doors therefore build the opposite-named node (`max` builds `IgnitorDsl.Min`); the nodes and the runtime `Ignitor.min`/`max` primitives keep the mathematical meaning, and `Math.min(a, b)`/`Math.max(a, b)` still select. Do not "correct" the crossing. Guard: `StdLibIgnitorTest`, `StdLibNumberMethodsTest`. | guardrail | 2026-09-10 | `/dsl-design` §5 |
| Taste is also what you do not do: won't-implement is a first-class outcome, even for a designed feature; check upstream (mixing, levels) before animating a component. | guideline | 2026-08-20 | `/dsl-design` §9 |

## 1. Everything is immutable at construction time

Every DSL value a user or a Kotlin caller holds is immutable: nodes, builders,
`KatalystDsl`, patterns. A "mutating" operation returns a NEW instance with the updated values and
leaves the receiver untouched.

```javascript
let base = Ignitor.supersaw(x => x.voices(7).spread(0.15))
let bright = base.lowpass(4000)          // base is unchanged
let dark = base.lowpass(600)             // both share the same source tree by value
```

**Why (maintainer, 2026-09-05):** it removes an entire bug class, shared-state mutation at a
distance, and therefore removes an entire chapter of explaining. Nobody has to learn when a value
is "still being built", whether storing it in a `let` is safe, or why a sound changed because a
builder was touched somewhere else. Composition falls out of the same property.

**Rules:**

- Kotlin: `data class` with `val` fields; every knob is a `copy(...)`. No `var` in a DSL class, no
  `MutableList`/`MutableMap` escaping a DSL type, no builder that returns `this`.
- Builders are value wrappers: `data class OscSuperSawBuilder(val node: IgnitorDsl.SuperSaw)`;
  each knob returns a new builder.
- Receiver lambdas (`Receiver.() -> Unit`) are PARKED for exactly this reason: they only work on
  mutable builders. Do not propose them.

**Scope: construction time only.** The runtime may use mutable objects for performance. Sprudel
does: every combinator returns a new pattern, but the query/render path mutates single-owner
`SprudelVoiceData` on purpose (leaf clone ~17x faster). That is engine-internal and invisible to
the authoring surface. Do not "fix" either side toward the other.

---

## 2. The door shape: configure lambdas on dedicated builder types

Decided 2026-09-05; plan and status in KLANG-00179 (dsl configure lambdas).

```
door(<construction inputs...>, configure: ((XyzBuilder) -> XyzBuilder)? = null)
```

(Both doors: the same builder and the same shape; only the accepted types differ, §3.)

```javascript
Ignitor.supersaw(x => x.voices(9).spread(0.1).phasePool()).lowpass(800).adsr(0.01, 0.3, 0.5, 0.5)
//           ^ inside the braces you configure the oscillator   ^ outside you process it
master(Katalyst(k => k.reverb(0.05, 9).gain(2.5).limiter()))   // reverb(wet, size): flat since 2026-09-24
```

**Rules:**

- The builder exposes ONLY that node's knobs. Base wrappers (`lowpass`, `adsr`, `mul`, ...) live on
  the base type only. Calling the wrong function inside the lambda is not an error, it is not
  offered. This is what the editor shows, so the type split IS the documentation.
- **Which parameter goes where** (refined 2026-09-23 by the maintainer, walking every door of the
  Ignitor, Katalyst and Master DSLs, the last retired into the Katalyst on 2026-09-28; the per-door record is `.claude/skills/dsl-design/door-shapes.md`):
  - The effect's MUSICAL inputs stay on the door, defaulted or not: `freq` on oscillators
    (`Ignitor.sine(0.5)` as an LFO is the most common modulator idiom), `lowpass(freq, q)`,
    `tremolo(rate, depth)`, `fm(modulator, ratio, depth)`. SECONDARY knobs go on the builder
    (`passes`, `analog`, `humanize`, `floor`, `cap`), even when one is the builder's only knob.
  - **`wet` is the very FIRST parameter** of every door that has one, in all four DSLs, sprudel
    included: `phaser(wet, rate, center, sweep)`, `body(wet, material)`, `reverb(wet, size, lowpass)`.
    `floor` is always on the builder.
  - A DYNAMICS stage is flat, because every one of its knobs is musical: `compressor` and `duck`,
    each identical to sprudel's, and `limiter`, the Katalyst's compressor preset (no sprudel twin).
  - An envelope is ONE `adsr(attack, decay, sustain, release, configure)` call, never four
    stage knobs, with the SAME shape wherever it appears: on the chain and nested inside a filter's or the
    pitch envelope's builder (`x => x.adsr(a, d, s, r, e => e.curves(...))`). Inside a builder a knob drops
    the prefix its door needed (`curves`, not `adsrCurves`; `declick`, not `declickSeconds`). An envelope's
    builder offers only what THAT envelope can do (the chain: `curves`, `declick`; the filter and pitch
    envelopes: `curves`; FM's index envelope: none yet). Maintainer, 2026-09-25.
  - A door whose only defaulted knob is temporary stays flat (`shape`, `distort`: their `oversample`
    leaves with KLANG-00214 (oversampling regions)).
  - A knob that nothing reads is REMOVED, not moved (`drive`'s `driveType`, the pitch envelope's
    `releaseSec` and `curve`).
  - ONE shape per concept across the DSLs. On the Katalyst every door parameter is optional: an
    omitted one is the bare stage's fixed default, never the owner voice; only a `Param` slot reads
    the orbit's `katp` state (per-door record: `.claude/skills/dsl-design/door-shapes.md`).
- `configure` is always the LAST parameter, always named `configure`, and always OPTIONAL (no
  lambda = defaults; the maintainer, 2026-09-23: "all configure callbacks are optional", so an
  `eq()` with no bands is a transparent stage, not an error). The one historical exception,
  `tuneVca(configure)` on a pipeline preset, retired with the Pipeline DSL (phase 3 step 9, 2026-09-27).
- The lambda is called ONCE at construction; the tree it produces is bit-identical to hand-built
  nodes. No new node kinds, no wire change.
- Callable objects (`Katalyst(...)`) go through the call operator (`@KlangScript.Invoke` on
  `operator fun invoke`, the internal script symbol `__invoke__`,
  KLANG-00056 (klangscript native object operators)); the docs show the object and its call form as
  two variants of one symbol. A callable object may be aliased to a method form
  (`Katalyst.build(...)`; `Katalyst()` == `Katalyst.build()`, the empty chain) so both can be tested
  against each other.
- No sub-type methods on the node types. The pre-2026-09 "config first, base wrappers last" chain
  form is gone; do not reintroduce it.
- Sprudel patterns have no builder layer: every method returns a `SprudelPattern`, there is no
  sub-type wall to scope.

---

## 3. Two doors, one DSL

Every DSL is directly usable from Kotlin, not only from KlangScript. Sprudel is the model.

**Why:** builtin sounds, tests, tools and future Kotlin-side authoring all build DSL trees. A
script-only surface forces raw constructor calls and splits the vocabulary.

**Rules:**

- A surface addition lands on BOTH doors in the same deliverable. A stdlib function without its
  Kotlin twin is a review finding (KLANG-00148 (dsl kotlin surface parity)).
- **The layering of the Ignitor and Katalyst DSLs (maintainer, 2026-10-16, option C; in force since the
  rollout S1 to S7 of KLANG-00314 (kotlin typed doors), 2026-10-16):** every new
  Ignitor or Katalyst door lands in it. sprudel's doors still take `PatternLike = Any` on one shared function
  until its own typed door is designed (maintainer's call; see the archived task, "Left after the rollout").
  - The ONE public Kotlin door is typed and lives in `audio_bridge`, next to the wire types: every
    value parameter is an `IgnitorDsl` / `KatalystKnobDsl`, a number enters as `0.5.ign` /
    `0.5.kat` (`-1.ign` is `Constant(-1.0)`), so a number or a signal fits each argument on its own
    with no overloads. It holds each door's logic. Typed Kotlin `Ign` and `Kat` objects mirror the
    script objects word for word (`Ign.saw()`, `Kat.sine(...)`), so moving between KlangScript and
    Kotlin costs only the `.ign` / `.kat`: the builders and the shapes are the same
    (`Ign.saw().vibrato(5.ign, 0.3.ign) { it.range(0.ign, 1.ign) }`).
  - The KlangScript door (`klangscript-libs`, the `@KlangScript...` functions, objects and
    builder knobs) is internal to its module, converts its number-or-signal arguments once
    (`toIgnitorDsl`, `toKatalystKnob`) and calls the typed Kotlin door. It is not the Kotlin door: a
    Kotlin caller outside the module cannot reach `KlangScriptIgnitor`, `KlangScriptKatalyst`, the
    script `Ign` / `Kat` or the conversions (S7). Public there only what other modules use and is
    no audio door: the slot-name resolvers `ignitorSlotName` / `katalystSlotName` with their
    `IgnitorSlotLike` / `KatalystSlotLike` (sprudel's `ignp` / `katp`), the engine entry
    (`klangScript()`, `stdlibLib`, `KlangStdLib`), `Interval.ratio` (the one Kotlin door kept in
    this module, for the interval vocabulary of `tones`, not an audio door) and the script constants
    `PI` / `E` (no door, left as they were).
  - In the Ignitor and Katalyst DSLs only KlangScript-exposed functions take `XxxLike = Any`
    parameters, converted and checked at run time. A shared function (`bands`, `cut`, `serial`, `parallel`, `blend`) may serve both
    doors only while its signature has no `Any`-typed parameter.
  - The same builders and shapes on both doors (maintainer, 2026-10-16): where the script takes a
    configure lambda, so does the Kotlin door, with the same builder; only the accepted types differ.
  - The one recorded exception, the build-time knobs, by one criterion: **a knob the script takes
    as a plain number (or string) stays plain on the Kotlin door** (Q51). A number is a plain
    `Double` (the script's `resetSamples(4)` is Kotlin `resetSamples(4.0)`), no `Int` overloads.
    This is the one place the criterion lives; other texts point here instead of listing names.
    Not to be confused with the engine's "build-time knobs" (`buildTimeKnobValue`: shapes, the
    oversample factor, `passes`): those are read once per voice but FROM THE SLOTS, so they are
    typed like any knob (maintainer, 2026-10-16). Only a plain node field (a `Double`, `String` or
    `List` the script also takes plain) stays plain.
- **The typed-door pattern** (spike S1 of KLANG-00314 (kotlin typed doors), 2026-10-16; this
  is its one home):
  1. The builder lives in `audio_bridge` (`IgnitorBuilders.kt` / `KatalystBuilders.kt`) under its
     simple name (the script shows it), its knobs CLASS MEMBERS with typed parameters, each with a
     one-line KDoc pointing at the script knob.
  2. The Kotlin door takes `configure: ((B) -> B)? = null` last and ends in
     `B(node).configured(configure).node`. Its other parameters are NON-null, with the default
     SYMBOL (`freq: IgnitorDsl = IgnitorDsl.Freq`; a Katalyst stage takes its bare-stage default
     symbol); no nullable Kotlin parameters for a default. A script door whose parameter is optional
     keeps its safe literal `null` and substitutes the SAME symbol on `null`, never a retyped literal:
     `freq?.toIgnitorDsl() ?: IgnitorDsl.Freq` (`orNoteFreq` in `KlangScriptIgnitor.saw` is the
     example). The default-node row (below) pins that the two agree. A multi-argument chained knob
     (`phasePool(on, kMin, ...)`) keeps literal defaults on the script variant (KSP) while the Kotlin
     member takes the engine constants (`SUPERTRI_K_MIN`); the bare-call parity row (`phasePool()`
     from both doors) guards against the two drifting apart.
  3. Every script function on a receiver type that delegates to a typed function of the same word
     gets a Kotlin name of its own and the script word in `name = "..."`, never only the clashing
     ones: the builder knobs, and as well the Katalyst knob arithmetic (`plus`, `times`, ...) and
     every other receiver function of that kind:
     `@KlangScript.Function(name = "range") internal fun VibratoBuilder.scriptRange(from: IgnitorDslLike, to: IgnitorDslLike) = this.range(from = from.toIgnitorDsl(), to = to.toIgnitorDsl())`.
     A variant that shared the member's name would silently call ITSELF the day the member's
     parameter names or types drift (a `StackOverflowError`); renamed, the drift is a compile error.
  4. The user-facing KDoc stays on the script variant, deliberately: KSP reads KDoc only from its
     own module.
  5. The script door converts its `Any` arguments, calls the typed door and passes the checking
     wrapper: `self.vibrato(r, s) { it.configuredBy("vibrato", configure) }`; `configuredBy` stays in
     `klangscript-libs`. A refusal the maintainer decided (Q49, an eq section given an expression)
     is pre-checked script-side, with a script error. Everything else follows the stone rule: coerce
     user-reachable inputs, never `require` them (§6).
  6. One concept with two value domains gets two typed builders of the same words sharing one
     helper: the eq is `band` / `tap` on a voice builder (`IgnitorDsl`) and on a Katalyst builder
     (`KatalystKnobDsl`), one helper for the section conversion and the Q49 refusal.
  7. The Kotlin door offers the forms the script offers, per knob (coordinator, 2026-10-16, S5): a name knob the
     script takes as a name OR an index or slot (the Ignitor name knobs and the Katalyst `material` / `vowel`, through
     `catalogueIndex`) gets a typed `String` member beside the index form (`it.shape("square")`); one the script takes
     only as a name (the Katalyst `distort`'s `shape`) is only a `String` on Kotlin too. Both go through the lookup
     helper the script variant uses, which coerces and never uses `require`. A knob with several name arguments
     (`curves(attack, decay, release)`) has two Kotlin overloads, all-`String` or all-typed; a mixed call (a name
     beside a slot) writes the names as knobs in the typed form, `curves(attack = AdsrCurves.knob(AdsrCurve.Linear),
     decay = slot, release = ...)`, since 2^n overloads are what the typed door avoids.
  8. Sweeps: update `door-shapes.md` and every KDoc citing the old door; a regex sweep skips string
     literals (KlangScript in Kotlin strings looks like Kotlin); `-1.ign` needs
     `import io.peekandpoke.klang.audio_bridge.unaryMinus` (a top-level extension).
  9. Cases met in the rollout, S3a first (the filters and the eq):
     - A nested builder moves with its parent (`ModAdsrBuilder` with `FilterBuilder.adsr`), even ahead of its own step.
     - A `String` name form cannot default its first argument: with the index form also all-default, `curves()`
       would be ambiguous. The index form keeps every default; the name form needs the first.
     - A script default that is a literal (`q = 0.707`), not `null`, stays a literal (KSP); the default-node row pins
       it equal to the Kotlin symbol (`FILTER_Q.ign`).
     - A node-less builder (`FilterKnobs`, which must know what was NAMED for the compound fill) does not end in
       `.node`: the door builds the node itself after the fill.
     - A door with no knobs (`onepole`) gets no builder and no `configure`.
     - Kotlin call sites name arguments that could be swapped (`lowpass(freq = 800.ign, q = 2.ign)`,
       `band(freq = ..., q = ..., db = ...)`, `/code-style`); a single argument stays positional, and KlangScript stays
       positional.
     - Met in rollout S3b and S3c (the envelopes, the effects, the one-value doors):
       - A builder that only collected nullable knobs, with nothing left to fill (the old `AdsrBuilder`), becomes a node
         builder: the door builds the default node, the knobs copy onto it, the door ends in `.node`. `FilterKnobs` stays
         node-less only because its fill must know what was NAMED.
       - A builder fill that falls back to "the node's default" reads a FRESH default node, never the builder's current
         node (`PitchEnvelopeBuilder.adsr`), or a second `adsr` call keeps the first one's curves; a row with two calls pins it.
       - A word the script has and Kotlin lacked gets its Kotlin door in the same step (`pitchEnvelope`, `octaveUp`,
         `octaveDown`), and the script door delegates.
       - Before keeping a knob plain (point 3), check the runtime: a knob it reads per event from a slot is typed
         (`shape`'s and `distort`'s `shape` and `oversample`, maintainer 2026-10-16), whatever the old door took.
       - A default symbol is the other surface's constant when the meaning is the same (the voice phaser's `center` and
         `sweep` are `PHASER_CENTER_HZ`, `PHASER_SWEEP_HZ`), otherwise a new constants file (`ShimmerDefaults.kt`); the
         node's field reads the same symbol.
       - A one-argument name knob has no ambiguity to dodge: the index form's parameter is `index`, the name form's
         `name` (`TremoloBuilder.shape`).
     - Met in rollout S4 (the arithmetic):
       - A script word on a `@KlangScript.TypeExtensions` object (`fun mul(self, other)`) cannot recurse through
         `self.mul(...)` (an object member is no extension candidate), but takes point 3 all the same: `internal`,
         `scriptMul`, `@KlangScript.Method(name = "mul")`. One rule, no case to judge; the generated docs stay
         byte-identical (they use the script name).
       - A script alias (`add`, `sub`, `mix`, `rem`, `power`, `negate`, `reciprocal`) calls the typed function of its
         main word. (Superseded in S5 review round 1, coordinator: Kotlin mirrors the script's words, aliases
         included; each alias is a one-line typed delegate to its main word, see the S5 sub-points.)
       - A guardrail that crosses nodes (`min` builds `Max`) lives ONCE, in the typed function; the script variant
         delegates and never builds the node, so one "correction" turns both doors red together.
       - A parameter keeps the script's name even where a call site would read better otherwise (`clamp(lo, hi)`):
         a Kotlin-only rename is a parameter-parity break (§4).
       - Check what the script's operators do before promising them: the interpreter's binary operators take numbers
         and strings only, so a signal's script arithmetic is the method (`saw.mul(0.5)`); a row pins `saw * 0.5` as
         the type error it already was. A recorded asymmetry: Kotlin has `+` / `*` on signals, the script is
         method-only until KLANG-00056 (klangscript native object operators) lands (decided 2026-10-07,
         `__times__` / `__rtimes__`, the goal `saw * 4`); that row then goes red on purpose.
     - Met in rollout S5 (the Katalyst):
       - Kotlin mirrors the script's words, aliases included; an alias delegates to its main word (coordinator, S5
         review round 1: porting is the goal, so the script's `mid.distort(...).mul(0.80)` is Kotlin
         `mid.distort(...).mul(0.80.kat)`). One line each, `fun IgnitorDsl.mix(other, t) = lerp(other = other, t = t)`,
         `fun KatalystBuilder.mul(other) = times(other)`; the guardrail crossing stays in the main word only. Each
         alias has its written-out row.
       - A stage door's parameters are the bare stage's constants (`delay(time = DELAY_TIME_SECONDS.kat)`), the ones the
         `KatalystStageDsl` data class reads; the script variant substitutes the same symbol for its `null`. A field
         whose bare value IS `null` (the reverb's `lowpass`, the engine's own damping, no wire constant) keeps a
         nullable parameter defaulting to `null`: the node's value, not a stand-in for a default; the variant passes
         its `null` through.
       - A name knob that is a DOOR parameter (`body`'s `material`, `vowel`'s `vowel`) gets a second door overload
         with the name as a required `String` (`body(wet, material: String, configure)`); the index form keeps every
         default, so `body()` and `body(material = "wood")` never meet. A name knob on a plain node field that the
         script takes only by name (`distort`'s `shape`, an `Int` on the stage, Q51) is only a `String` on Kotlin
         too, its default the catalogue's own name (`DistortionShapes.names[SOFT_INDEX]`); an index form there was
         dropped (coordinator, S5: the doors offer the same forms, point 7).
       - A member of the same name beats an extension, so a script door that was an extension on the moved builder
         (`fun KatalystBuilder.reverb(...)`) must take point 3 or Kotlin silently calls the member; the knob arithmetic
         on `KatalystKnobDsl` is the same (`scriptTimes`), its typed functions plain extensions returning the precise
         node, no operators (there were none).
       - The builder's own primitive (`KatalystBuilder.append`) is public. (Since S6 no script function appends
         through it; it stays a public member, a stage door of its own.)
       - A door-table spec that omitted parameters through a `null` cannot do so on a typed door: its Kotlin lambda
         fills the others from the bare data class, the script variant called from Kotlin (`scriptDelay(time = ...)`)
         carries the omission rows, and the default-node rows, written with literal numbers, pin the typed defaults.
     - Met in rollout S6 (the composition doors, `serial`, `parallel`, `blend`, `bands`):
       - A script function that takes a FUNCTION the script supplies (a stage, a branch, a band processor) checks it
         where it is called, never in the typed door: the variant wraps each one in its check (`checkedStage`, over
         `runStage`, in `Configure.kt`) and hands the wrapper to the typed door, which only invokes what it gets. The
         wrapper knows its number when it is made (`band ${processors.size}`, processor `processors.last().size`), so
         the messages are the ones the script had. The typed door and the typed `split` hold no `runStage`.
       - A function argument the script refuses at the call (not a function, `null` on a non-vararg parameter: "expected
         a function, got null") is refused before the variant runs; only a vararg element reaches the null check.
       - The §3 criterion decides a door parameter as it decides a knob: the Katalyst `blend`'s `wet`, which the script
         takes as a plain number only, is a plain `Double` on Kotlin; the Ignitor's, a slot or a signal, an
         `IgnitorDsl`. A `bands` `cut`, written into the EQ sections as a constant and never slot-read, is a plain
         `Double` on both doors.
       - The crossover's math lives once beside its builders in `audio_bridge`; the split is pinned against nodes
         written out by hand (each cut, the bands in order, each processor on its own band), since a parity row shares
         the split.
       - KSP: a top-level extension function's entry records its own file as a source (`collectSourceFiles`, pinned by
         `SourceFilesTest`), or an incremental run that does not dirty that file drops its registrations when its
         receiver has no file in the module (met when `BandsBuilders.kt` kept only renamed variants; the gap predates
         S1, sprudel's `lang_structural_chunk.kt` had it too, and the other builder files were dirty only by chance,
         through `toIgnitorDsl`).
       - A song in the corpus that reads "edited" is not engine-guarded: refresh its live row (a plain
         `corpus-accept`, which refuses a red row) before relying on the corpus for a door the song uses.
- Pin parity with a door-parity spec: the script form and the Kotlin form must produce equal nodes
  (`KlangScriptFilterDoorParitySpec` is the pattern). Since both doors share the builder members, a
  parity row cannot see a member that writes the wrong field, nor a wrong default in the Kotlin door
  the script door builds on: every member knob also gets a row against the node WRITTEN OUT with
  `.copy()`, never through a builder, with a distinct value off its default (`KlangScriptSawRampSpec`
  is the template), and one row pins each Kotlin door's default node. A repeatable (appending) knob
  (`partial`) keeps its append logic in the member; a written-out row with two or more calls, the list in written order (`KlangScriptSineSpec`),
  (`node.copy(partials = listOf(...))`) pins it. A sealed-default door (`pluck`, `superpluck`, whose
  knobs default to constants, not the node's `Slots.*`) has the sealed node, written out, as its
  default-node row, not `Node(freq = Freq)`.
- Script-door param defaults are SAFE LITERALS (number, string, bool, null). Since 2026-10-06 the
  KSP processor refuses any other default (`IgnitorDsl.Slots.rate`, `emptyList()`) with a build error
  naming the door and the parameter. The spec's default thunk returns the literal and fills an
  omitted argument on every script call, named or positional; the generated call pastes the same
  literal for a native caller that passes fewer arguments (a native function in a Kotlin function
  slot). Bake literals on the door, keep the `Slots.*` leaf on the data class.

---

## 4. Parameter parity: same word, same meaning, same scale

**Maintainer, 2026-08-02:** "The same params must be available in the sprudel DSL, Ignitor, Master
etc. and they need to mean the same thing."

**Why:** the reverb size once meant a ~1 s tail on an orbit and ~12.5 s on the master because
sprudel divided by 10 and the master did not, and the tail override existed on only one bus. A shipped song had
the bug.

**Rules:**

- When adding a param to one surface, check every other surface for the same concept and match
  name, scale, and availability.
- Shared conversions live in ONE place (`Reverb.normalizeSize` serves both buses). Never let
  each host convert on its own.
- **Defaults are the same on every surface, and live in ONE place** (maintainer, 2026-09-16): the
  wire defaults in `audio_bridge/constants/` (`BusEffectDefaults.kt` for every Katalyst stage). The
  Katalyst stage (on an orbit or at the output), a sprudel call (it sets every slot it leaves unset at write time), the engine's
  rule for a slot written raw through `katp` and the editor tools all read the same constant; a non-finite
  value reads as unset. Never a literal default per host.
- **A compound door fills per param, at the door, everywhere** (maintainer, 2026-09-18): when a
  call NAMES a stage, the door checks every companion slot of that stage and, if it is not yet
  set on the event and the call did not provide it, writes the constant from
  `audio_bridge/constants/`. A later call that provides the knob overwrites it; an earlier
  explicit value is never overwritten by a fill. What "names the stage" means differs by stage
  kind, and the two kinds must not be confused (round 1 of step 5a-3 caught this text saying
  "wet is the gate of the sends", which its own blueprint contradicts):
  - stages with a NAME knob (`body` by its material, `vowel` by its vowel, `duck` by its orbit)
    are named only by that knob; a tail-only call (`body(wet = 0.3)`, `duck(depth = 0.5)`)
    writes its own slot and never invents the name knob, because inventing it would switch the
    stage on;
  - the sends, the compressor and the phaser have no name knob, so ANY of their knobs names the
    stage: `reverb(size = 4)` fills `wet` with `REVERB_WET` and the room is on,
    `compressor(ratio = 8)` fills the other four and compresses at the constant threshold,
    `phaser(rate = 2)` fills the other four and stays silent because `PHASER_WET` is 0. That is
    the documented, guarded behaviour of `fillReverbDefaults` / `fillDelayDefaults`, the
    blueprint. The two lists above are CLOSED and complete for the bus doors (name knob: body,
    vowel, duck; any knob: delay, reverb, compressor, phaser); a new bus door is added to one of
    them in the same change, or the rule is silently wrong for it (round 2 of step 5a-3 caught the
    phaser missing from this list one round after the sends' gate was mis-stated).
  A knob with no constant, where unset means off (`reverb.lowpass`), is not a companion in this
  sense and is never filled; inventing a constant for it would damp every room.
  **This bullet is the ONE home of the rule's text.** Its row in this skill's Rules table is its index line. Every
  other site (a door's KDoc, `ParamBag`, the classic chain's KDoc, the constants headers, the
  module `MEMORY.md` files) states only what THAT door or class does, in a sentence or two, and
  points here. The rule escaped review three times in one step because its text was copied to a
  dozen sites and each correction reached only some of them.
  `body`, `vowel`, `compressor`, `phaser` and `duck` follow the blueprint since Katalyst step
  5a-3, which also gave the bags a class (`ParamBag`, `setOrDefault`). The value a door hands to `setOrDefault` is
  what THIS call named, never a voice field that an earlier fill wrote, or a `katp` between two
  calls of the same door is overwritten. The engine's non-finite guard stays as the NaN rule for
  a raw `katp` write, not as a second fill. The voice-side compound doors (`adsr`, `lpf`, FM and
  pitch envelopes) adopt it when phase 3 of KLANG-00210 (signal flow redesign) rebuilds the
  built-ins as instruments with slots. **First adoption, 2026-09-20 (phase 3 step 3a): the four
  Ignitor filter doors.** Their envelope has no NAME knob, so ANY of its five knobs names the stage
  and a call that names one writes every companion it left out, `env` included; a call that names
  none is the untouched filter. Since step 3d (2026-09-24) the five knobs are two builder calls,
  `env(...)` and `adsr(...)`, and the fill runs once after the lambda. The three CURVES are NOT stage
  knobs (maintainer, 2026-09-23): a curve shapes an envelope, it does not ask for one. Since 3c they
  live only inside the envelope's own builder (`adsr(..., e => e.curves(...))`), so on the script door a
  curve cannot be written without naming the stage, and on the Kotlin door too, which has the same builder since
  the typed-door rollout S3a. The pitch envelope's builder
  has no depth to fill (the depth is its door input), so the rule has nothing to do there. Sprudel's
  `penv(semitones, attack, decay, sustain, release)` (phase 3 step 5b (c), 2026-09-25; `semitones` was `amount`
  until pitch pipeline step 1, 2026-10-09) is a voice-side door on
  neither closed list: `semitones` is its switch and a tail-only call never invents it; its unset stages read
  the shared `PitchEnvelopeDefaults` (the `penv.*` slot defaults of `classic()`'s pitch envelope stage). Sprudel's
  `vib(rate, semitones, rangeFrom, rangeTo, phase)` has the same shape (pitch pipeline step 2, the range and the
  phase since 7c): `semitones` is its switch, and a rate-only call (`vib(4)`) or a range- or phase-only call
  (`vib(rangeFrom = 0)`) writes its own slots and never invents the depth; an unset rate, range or phase reads its
  `vibrato.*` slot default (`PitchModDefaults.kt`), so nothing is filled at the door. Sprudel's `fm(depth, ratio, attack, decay, sustain,
  release)` too (pitch pipeline step 4): `depth` is its switch, and `fm(ratio = 2)` or `fm(release = 0.3)` writes its
  own slot and never invents the depth. The `<door>Curves` doors (`adsrCurves`, `penvCurves`,
  `lpfCurves`, ...) are setters only: a curve never switches its envelope on, and a bare call changes
  nothing. **It is adopted AT THE DOOR only, and a door fill does not survive
  SLOTTING:** the reading is "named against null" at call time, while a slotted instrument hands the
  node one set of `Param`s once and the per-note decision moves into the build, which has no notion
  of "named". **The SLOT layer answers it a second time (decided 2026-09-25, built in phase 3 step 5 as
  `slotLayerDepth` in `IgnitorDslRuntime.filterEnvDef`):** a knob is WRITTEN when the note's bag holds a
  finite value for its slot (sprudel's `!= null`; an authored default alone never counts). A written depth
  always stands, an explicit 0 included. Only an UNSET depth slot (a `Param` whose default is the
  `SLOT_UNSET` sentinel, as `classic()` places it) takes `FILTER_ENV_DEPTH_SEMITONES`, and only when one of
  the FOUR stage knobs is written. An authored depth default, 0 included, is the author's value and is
  never filled; a `Constant` depth (a door fill) is never the question.
- A KDoc claim "orbit twin: x()" must be verified; a wrong parity claim is worse than none.
- Deliberate asymmetries are RECORDED with their reason (the compressor's `lookahead` exists on the
  Katalyst doors only, `k.compressor(...)` and `k.limiter(...)`, not on sprudel's `compressor(...)`: it is
  fixed when the chain is built because it sizes a delay ring, while a sprudel door writes slots on the
  running chain; the orbit route is `katalyst(Katalyst(k => k.limiter(lookahead = ...)))`; and the FM's positional
  order: sprudel's `fm(depth, ratio, ...)` leads with the depth, the Ignitor door `x.fm(modulator, ratio, depth, ...)`
  leads with the modulator, its structure, and puts the ratio first; the words are the same since pitch pipeline step
  4 (D4), and sprudel kept its order so that no positional call changed, `fm(300, 1.4)` is still depth 300, ratio
  1.4). See
  KLANG-00137 (master dsl followups) section 1 for the parity audit brief.

---

**Units in names (maintainer, 2026-10-08, provisional: question it when it makes an outcome messier):** time is always in seconds, pitch is always in semitones (where it can
be), and the unit lives in the KDoc, never in the name: `attack`, not `attackSec`; `declick`, not `declickSeconds`;
`threshold`, not `thresholdDb`. A name says what the value IS when the bare word would hide it: `crush.bits`,
`coarse.factor`, `penv.semitones`. The filter `env` stays `env` and is documented in semitones. One word keeps one
meaning: `depth` is how far a modulation swings, in the stage's unit (tremolo and duck 0 to 1, FM in Hz); `amount`
is the distort drive only. A name may also stand for a CHARACTER rather than a unit, when the component maps it
through multipliers of its own: `analog` is "how analog", 0 ideal, 1 to 8 usual, 10 strong, and each component
documents its tells per unit (Q22).

**Slot names (maintainer, 2026-10-08, decision D4 and Q21, provisional):** a slot is `<door>.<param>`; the namespace is the sprudel
door's name (`lpf`, `penv`); the param part is the engine door's word (the Ignitor's, or the Katalyst's for a bus
stage), after a check that it is the better word, and the sprudel door's parameter takes the same word. A one-knob
door stays flat (`onepole`, `accelerate`, `pregain`, the sample slots). A `snd*` door's parameter takes the knob or slot word
(`sndPulze(duty)`), not the knob's inner parameter word (`duty(width)`, Q29, 2026-10-16). The table: KLANG-00289 (classic slot names check).

## 5. One word per concept, end to end

A concept carries ONE name across the KlangScript object, the `*Dsl` type, the sprudel carrier,
the wire, and the backend registry/runtime. The Katalyst is the model:
`Katalyst` object, `KatalystDsl`, `.katalyst()`, `KatalystRegistry`, no split.

The split that was left open, a script object named apart from its type `IgnitorDsl`, is resolved (2026-10-04,
KLANG-00269 (ignitor katalyst naming)): the script object, the wire field (`ignitorParams`), the setter
(`ignitorParam`) and the runtime all say Ignitor. A second, shorter name is the house pattern, for songs: `Ign` and
`Kat` are `@KlangScript.Constant` vals holding the same object as `Ignitor` and `Katalyst`, so every member answers
to both (no alias factory, no third name).

Also: when a surface is redesigned, REMOVE what it replaces. Two doors to the same thing
(`MasterFx.gain()` next to `Master(m => m.gain())`, both gone since) is a finding, not backward compatibility.
The project does not keep deprecated surfaces.

---

## 6. Coerce user input, keep the signal path raw

Two complementary rules that are often confused:

- **Coerce, never `require()`, on anything a user can reach** (sprudel args, KlangScript DSL
  args, UI inputs, imports). `passes.coerceIn(1, FILTER_MAX_PASSES)`, not `require(passes >= 1)`.
  Crashing the engine because someone typed `oversample(-1)` is unacceptable. `require()` is for
  internal invariants only.
- **Do not add safety clamps to audio parameters without asking.** The Motor is raw with sharp
  edges by design; delay feedback above 1.0 (bounded by its `cap`) or extreme drive is a creative
  choice, the house limiter (`MasterStage`) is the safety net. When a review flags a parameter as "could clip",
  ask, do not clamp. The reverb is the recorded exception: above unity comb feedback it has no
  sound, only a network running away to Inf/NaN, and its size bound sits at 10 (feedback 0.98) by
  maintainer decision (2026-09-16, `Reverb.normalizeSize`).

Rule of thumb: coerce the range into something the engine can execute; never neuter what it
executes.

---

## 7. Wire types over enums

New wire-visible distinctions start as sealed `@WireName` hierarchies, not enums.

**Why:** variants carry exactly their own params; name-addressed variants have no ordinal
append-only hazard (the KSP schema hash does not cover enum entries); exhaustive `when` over the
sealed type makes every consumer arm compiler-checked. Precedents: `AdsrDef`, `KatalystStageDsl`,
`EqSection`. An enum is acceptable only for a genuinely closed, param-less set (`AdsrCurve`).

---

## 8. Boundaries the DSLs must respect

- **The engine is the horse.** A frontend (sprudel, MIDI, a future sequencer) never gets DSP of
  its own. When a sprudel surface needs an engine capability, add a wire-level field that
  `VoiceFactory` maps onto an EXISTING engine component. Never a frontend class that computes audio.
- **The backend never learns about cycles.** Only seconds cross the wire. Never propose
  cycle/bar/phrase fields in the wire format or `audio_be`; phrase-aware behavior derives from the
  note stream (works for every source).
- **Plans and tasks are tickets in `docs/tasks/`** (`/project-layout`), in the repo. Never in a home directory.

---

## 9. Taste is also what you do not do

A designed, cheap, technically appealing feature can still be closed as WON'T IMPLEMENT on taste.
The resonator-swing plan (2026-08-20) was fully designed and archived unbuilt: the design turned
out to be a family of models, so by-ear tuning could not attribute what it heard, and the
motivating complaint was really a mixing problem.

**How to apply:** when a feature reveals itself as a family of models rather than one knob, or when
tuning it would not be attributable by ear, name this principle and offer "archive as
won't-implement" as a first-class outcome. Sunk design work is not a reason to build. Before
animating a component, check whether the problem is upstream (mixing, levels). Omission is a
feature: a door with no knobs gets no `configure` lambda.

---

## Review checklist for a DSL change

Run this on every DSL diff (the `/review-loop` reviewer cites the item number):

1. Immutable: no `var`, no escaping mutable collection, every knob a `copy`, no `this`-returning builder.
2. Door shape: knobs on the builder, construction inputs on the door, `configure` last and optional.
3. Both doors, same names, same defaults, door-parity spec present.
4. Parity across surfaces checked (name, scale, availability); conversions in one place.
5. One word per concept; the replaced surface is removed, not deprecated.
6. User-reachable params coerced, not asserted; no unasked safety clamp on audio.
7. Wire changes: sealed `@WireName` types, not enums.
8. No frontend DSP, no cycles over the wire.
9. Docs that feed the editor popup (KDoc on the door and the builder) updated in the same diff.
10. Every example in the diff is AUDIBLE, not just compilable: the stage the knob drives is
    actually built (read the `VoiceFactory` gate: a tremolo needs depth > 0, a filter envelope needs its cutoff, a decay needs a sustain below 1, ducking needs depth and a
    trigger on the named orbit) and the comment describes what the engine does. Lesson of the
    accessor sweep (2026-09-07): every MAJOR across four batches was an example that compiled,
    queried and demonstrated nothing; `DslDocExamplesSpec` cannot hear. An example that NAMES a
    built-in ("the `sgbell` index envelope") quotes the built-in's values read from its definition,
    never from memory: step 3d(i) (2026-09-24) shipped `sgbell` with release 0, the very variant the
    preset's own comment says ticks on every note-off.
11. A compound door fills its companions per param from `audio_bridge/constants/` when the
    stage is named, never overwrites an explicit value, and never invents a NAME knob. Check the
    door against the two closed lists in §4 (they are not repeated here on purpose); a new bus
    door joins one of them in the same change. Review a change to this rule BY TABLE: every door,
    every setter, against every clause.

12. The value handed to `setOrDefault` is what this call named, never a field an earlier fill
    wrote (§4).

13. A door that calls script lambdas (a `configure`, the stages of `serial`, the branches of `parallel`, the
    processors of `bands`) checks what each one
    returns, as `configuredBy` and `runStage` do: null (a block body without `return`) and the
    wrong type are script errors naming the door, never a cast deep inside. The error rows run on JS
    too: a null check on a value of a non-null declared type can be compiled away there and not on
    the JVM (`through`, 2026-10-02).

14. A door that hands a node it BUILT to a script function (a `bands` processor, a `parallel` branch) asks, of every
    door the receiver may call on it next, whether that door CONTINUES the node instead of wrapping it (`eq()`
    continues an `Eq`, and a raw tap reads its `Eq`'s input). Where one does, hand a neutral wrapper, and render a
    continuing door on the handed node in the spec (`bands`, 2026-10-10: a tap in a band read the unsplit signal).
