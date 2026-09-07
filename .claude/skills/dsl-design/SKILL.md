---
name: dsl-design
description: Use when someone asks to design a DSL surface, add a DSL door or knob, review a DSL change, add a builder or configure lambda, or asks about DSL immutability, dual surface, parameter parity, or naming across KlangScript, Kotlin, wire and backend.
---

## What This Skill Does

Loads the design principles every Klang DSL follows: IgnitorDsl (`Osc.*`), MasterDsl, PipelineDsl,
sprudel patterns, the future Katalyst DSL, and any DSL still to come. Apply these rules whenever
you add, change, or review a DSL surface, on either door (KlangScript stdlib or Kotlin).

This is a reference skill. It changes how you judge a design; it does not run a workflow.
Companion skills: `/klangaudio-knowhow` (the engine the DSLs drive), `/klangscript-knowhow`
(the language the script door is written in), `/review-loop` (how a DSL change gets reviewed),
`/code-style` (formatting).

---

## 1. Everything is immutable at construction time

Every DSL value a user or a Kotlin caller holds is immutable: nodes, builders, `MasterDsl`,
`PipelineDsl`, patterns. A "mutating" operation returns a NEW instance with the updated values and
leaves the receiver untouched.

```javascript
let base = Osc.supersaw(x => x.voices(7).spread(0.15))
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

Decided 2026-09-05; plan and status in `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`.

```
door(<construction inputs...>, configure: ((XyzBuilder) -> XyzBuilder)? = null)
```

```javascript
Osc.supersaw(x => x.voices(9).spread(0.1).phasePool()).lowpass(800).adsr(0.01, 0.3, 0.5, 0.5)
//           ^ inside the braces you configure the oscillator   ^ outside you process it
master(Master(m => m.reverb(r => r.wet(0.05).roomSize(9)).gain(2.5).limiter()))
```

**Rules:**

- The builder exposes ONLY that node's knobs. Base wrappers (`lowpass`, `adsr`, `mul`, ...) live on
  the base type only. Calling the wrong function inside the lambda is not an error, it is not
  offered. This is what the editor shows, so the type split IS the documentation.
- **Anything with a default is a knob** and lives on the builder. Construction inputs stay on the
  door: `freq` on oscillators (`Osc.sine(0.5)` as an LFO is the most common modulator idiom), the
  wrapper's own inputs (`phaser(rate, center, sweep)`).
- `configure` is always the LAST parameter, always named `configure`, and optional on a DOOR
  (no lambda = defaults). The one exception: an operation whose whole purpose is the lambda, like
  `tuneVca(configure)` on a pipeline preset, takes it REQUIRED, because an optional lambda there
  would make `tuneVca()` a silent no-op.
- The lambda is called ONCE at construction; the tree it produces is bit-identical to hand-built
  nodes. No new node kinds, no wire change.
- Callable objects (`Master(...)`, `Pipeline(...)`) go through the `invoke` operator
  (`docs/tasks/klangscript-native-object-operators.md`), aliased to a method form
  (`Master.build(...)`, `Master()` == `Master.default()`) so both can be tested against each other.
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
  Kotlin twin is a review finding (`docs/tasks/dsl-kotlin-surface-parity.md`).
- Builders live in `klangscript-libs`, next to their doors, and are annotated for KlangScript
  directly (KSP runs there like in sprudel): one implementation, one KDoc, no delegate objects.
  `klangscript-libs` IS the Kotlin door for the builders; `audio_bridge` stays pure wire types.
- Pin parity with a door-parity spec: the script form and the Kotlin form must produce equal nodes
  (`KlangScriptFilterDoorParitySpec` is the pattern).
- Script-door param defaults are SAFE LITERALS (number, string, bool, null). A default like
  `IgnitorDsl.Slots.rate` makes KSP emit no thunk, and a named call that skips that param fails at
  runtime. Bake literals on the door, keep the `Slots.*` leaf on the data class.

---

## 4. Parameter parity: same word, same meaning, same scale

**Maintainer, 2026-08-02:** "The same params must be available in the sprudel DSL, Ignitor, Master
etc. and they need to mean the same thing."

**Why:** `roomSize` once meant a ~1 s tail on an orbit and ~12.5 s on the master because sprudel
divided by 10 and the master did not, and `roomFade` existed on only one bus. A shipped song had
the bug.

**Rules:**

- When adding a param to one surface, check every other surface for the same concept and match
  name, scale, and availability.
- Shared conversions live in ONE place (`Reverb.normalizeRoomSize` serves both buses). Never let
  each host convert on its own.
- A KDoc claim "orbit twin: x()" must be verified; a wrong parity claim is worse than none.
- Deliberate asymmetries are RECORDED with their reason (the master limiter's `lookahead` exists
  on the master only because a per-orbit lookahead would shift that orbit late). See
  `docs/tasks/master-dsl-followups.md` for the audit brief.

---

## 5. One word per concept, end to end

A concept carries ONE name across the KlangScript object, the `*Dsl` type, the sprudel carrier,
the wire, and the backend registry/runtime. The Pipeline rename is the model:
`Pipeline` object, `PipelineDsl`, `.pipeline()`, `PipelineRegistry`, no split.

Known debt, capture-only, not scheduled: the script object is `Osc` but the type is `IgnitorDsl`
and the runtime is the Ignitor. When unifying, pick one word and carry it everywhere.

Also: when a surface is redesigned, REMOVE what it replaces. Two doors to the same thing
(`MasterFx.gain()` next to `Master(m => m.gain())`) is a finding, not backward compatibility.
The project does not keep deprecated surfaces.

---

## 6. Coerce user input, keep the signal path raw

Two complementary rules that are often confused:

- **Coerce, never `require()`, on anything a user can reach** (sprudel args, KlangScript DSL
  args, UI inputs, imports). `passes.coerceIn(1, FILTER_MAX_PASSES)`, not `require(passes >= 1)`.
  Crashing the engine because someone typed `oversample(-1)` is unacceptable. `require()` is for
  internal invariants only.
- **Do not add safety clamps to audio parameters without asking.** The Motor is raw with sharp
  edges by design; reverb feedback above 1.0 or extreme drive is a creative choice, the master
  limiter is the safety net. When a review flags a parameter as "could clip", ask, do not clamp.

Rule of thumb: coerce the range into something the engine can execute; never neuter what it
executes.

---

## 7. Wire types over enums

New wire-visible distinctions start as sealed `@WireName` hierarchies, not enums.

**Why:** variants carry exactly their own params; name-addressed variants have no ordinal
append-only hazard (the KSP schema hash does not cover enum entries); exhaustive `when` over the
sealed type makes every consumer arm compiler-checked. Precedents: `FilterDefs`, `MasterStageDsl`,
`EqSection`. An enum is acceptable only for a genuinely closed, param-less set (`AdsrCurve`).

---

## 8. Boundaries the DSLs must respect

- **The engine is the horse.** A frontend (sprudel, MIDI, a future sequencer) never gets DSP of
  its own. When a sprudel surface needs an engine capability, add a wire-level field that
  `VoiceFactory` maps onto an EXISTING engine component. Never a frontend class that computes audio.
- **The backend never learns about cycles.** Only seconds cross the wire. Never propose
  cycle/bar/phrase fields in the wire format or `audio_be`; phrase-aware behavior derives from the
  note stream (works for every source).
- **Plans live in `docs/plans/`, tasks in `docs/tasks/`**, in the repo. Never in a home directory.

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
    actually built (read the `VoiceFactory` gate: a tremolo needs depth > 0, a delay needs a time,
    a filter envelope needs its cutoff, a decay needs a sustain below 1, ducking needs depth and a
    trigger on the named orbit) and the comment describes what the engine does. Lesson of the
    accessor sweep (2026-09-07): every MAJOR across four batches was an example that compiled,
    queried and demonstrated nothing; `DslDocExamplesSpec` cannot hear.
