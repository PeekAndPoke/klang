# Katalyst knobs that listen: expressions over `Kat.param`, and the Katalyst's own modulators

Status: **in progress, steps 1 to 4 built** (decided: D1 to D4, Q2, Q3, Q8 and Q12 by the maintainer, 2026-10-11; the
other Q at the coordinator's lean).

## Why

The maintainer, 2026-10-11, after the Katalyst arithmetic stages landed
(`docs/tasks-archive/2026-10/20261011-katalyst-arithmetic.md`, "Not in this task"):

- "I think kat-param should be evaluated at least once per block."
- "Ignitors as katalyst params sound interesting, but i would say we should instead create the equivalents
  Kat.sine() etc. instead of mixing the two dsls."

Today a Katalyst knob is a number or a `Kat.param`, and nothing else listens:

- `Kat.param(...)` has no arithmetic of its own (`KatalystSlots.kt`, the `KatalystKnob` KDoc). An expression over a
  slot can only be written with Ignitor nodes (`Ignitor.param("room", 5).mul(2)`; a BARE `Ignitor.param` is a type
  error, an expression over one is accepted, `toKatalystKnob`), and anything that is neither a constant nor a slot
  is probed and FOLDED to one number when the chain is built (`KatalystSlots.coerce`). `katp` never reaches it.
- An Ignitor oscillator on a knob (`k.phaser(rate = Ignitor.sine(0.2))`) is accepted and goes through the same
  probe: a signal is not block-constant, so `controlRateValueOrNull` answers null and the knob falls back to its
  default. Silent, never an error.

The direction follows `docs/plans/future/signal-graph-engine.md` §6.10 (the Ignitor and the Katalyst stay two
DSLs, maintainer 2026-10-11): the Katalyst gets its own knob vocabulary, it does not borrow the Ignitor's.

## What it is

1. **Expressions over `Kat.param` that listen.** `k.gain(Kat.param("fader", 1).times(0.5))`,
   `k.delay(time = Kat.param("beat", 0.5).div(4))`: the expression is evaluated at least once per block, from the
   orbit's current `katp` state, so a pattern that moves the slot moves the knob.
2. **The Katalyst's own modulators.** `Kat.sine(freq)`, and the other shapes D3 settles, usable on any knob that
   takes a `Kat.param` today: `k.phaser(center = Kat.sine(0.1).range(400, 1600))`. A bus LFO, owned by the orbit's
   chain.

Both are one thing: a knob is an EXPRESSION in the Katalyst's own small language (constants, slots, arithmetic,
modulators), evaluated by the chain per block.

## Decided (maintainer, 2026-10-11)

- **D1:** a separate wire type, (a) below: "yes separate wire type".
- **D2:** per block, (a) below: "yes to the per block glide".
- **D3:** "yes same modulators as on ignitors": the Ignitor's LFO-capable oscillators, with their names and
  parameter words: `sine`, `saw`, `square`, `tri`, `ramp`, `perlin`, `berlin` (`KlangScriptIgnitor.kt`). The three
  audio-rate noises (`whitenoise`, `brownnoise`, `pinknoise`) are not on the list: read once per block they are a
  random step per block, not a modulator. The implementer confirms each door's parameters against the Ignitor's
  before building. (Correction from the plan: the Ignitor's word for `sine`, `saw`, `square`, `tri` and `ramp` is
  `freq`, in Hz; `perlin` and `berlin` take `rate`, which is NOT Hz, see Q12.)
- **D4:** "yes same": the arithmetic words, laws and clamps of the stages, plus `range(lo, hi)`.

## The questions as they were put

- **D1, the wire type of a knob.** Today every stage field is an `IgnitorDsl` (`KatalystStageDsl.Gain(gain:
  IgnitorDsl)`, and so on), with a `Kat.param` carried as an `IgnitorDsl.Param`. Options:
  - (a) a new sealed `@WireName` hierarchy `KatalystKnobDsl` (Constant, Param, the arithmetic, the modulators), and
    every stage field moves to it. The two DSLs are separate on the wire too, which is what §6.10 asks for. Cost: every
    stage, the chain builder, the slot writers, the codecs and their round-trips, the defaults-sync spec, and every
    song that builds a chain in Kotlin.
  - (b) keep `IgnitorDsl` on the wire and give the Katalyst door its own builder nodes that compile to Ignitor nodes.
    Smaller, but the two DSLs stay mixed underneath, which the maintainer declined.

  Lean: (a). What happens to an Ignitor node handed to a knob after (a): a type error at the door ("a Katalyst knob
  takes a number, a Kat.param or a Kat expression"), the same shape as today's refusal of a bare `Ignitor.param`.
  UNVERIFIED: whether any builtin song or tutorial hands an Ignitor node to a Katalyst knob today; the implementer
  greps `src/commonMain/kotlin/builtinsongs/`, the tutorials and the Klangbuch before building on "none".
- **D2, the rate and the glide.** A `katp` move of a plain slot glides over `KNOB_GLIDE_SECONDS` (50 ms,
  `KnobGlide`) so it does not zip. A modulator must NOT go through that glide: at 50 ms it would smear every LFO above
  a few Hz and lag the rest. Options: (a) evaluate per block and ramp linearly across the block (the glide's own
  per-sample ramp, but over one block), with `KnobGlide` kept for a knob whose expression holds slots only; (b)
  evaluate per sample on the stages that already run a per-sample knob (gain, the arithmetic). Lean: (a), "at least
  once per block" in the maintainer's words, one rule for every stage. A stage that reads its knob once per block
  today (a delay time, a phaser center) reads the block's value as now.
- **D3, which modulators, and their clock.** Lean: `sine`, `tri`, `saw`, `square` and a seeded `perlin`, the
  Ignitor's names and parameter words, rate in Hz (the backend never learns about cycles, stone rule; a pattern that
  wants tempo sync writes the rate through a `Kat.param`). The phase: free-running from the chain's birth, or reset
  on each new chain. Lean: from the chain's birth (a chain swap crossfades, so a reset is not a click).
- **D4, the expression surface.** The arithmetic words of the stages (`plus`/`add`, `minus`/`sub`, `times`/`mul`,
  `div`, `pow`, `min`, `max` with the clamp crossing, `neg`, `abs`) plus the scalers a modulator needs (`range(lo,
  hi)`). One word per concept: the same words, laws and clamps as the Ignitor's arithmetic, evaluated on a scalar.
  Which knobs take an expression: every knob that takes a `Kat.param` today; not the build-time ones (the
  compressor's `lookahead`; correction from the plan: the build-time knobs are `lookahead`, `distort`'s `shape` and
  `oversample`, while `body.material` and `vowel.vowel` are runtime index slots, only the name-to-index conversion
  at the door is build-time).

## The plan (read-only planning pass, 2026-10-11)

**Size.** 35 `IgnitorDsl` knob fields on 17 stage types, plus 14 on the EQ sections shared with the voice. 33
`KatalystKnob` sites in the chain builder, 10 slot writers, 16 `KnobGlide`s in 9 stages, about 30 door parameters,
about 70 test files with about 600 knob-construction lines. No editor or UI reader. The KSP wire codec needs no change
(recursive sealed types and equal simple names across hierarchies already work).

**What breaks.** No song, tutorial or Klangbuch entry hands an Ignitor node to a Katalyst knob (verified: the builtin
songs use `master(Katalyst(...))` with numbers only). The coercion rows of `KatalystSlotResolverSpec` go with
`coerce`; in `KlangScriptKatalystDoorParitySpec` the refusal of `Kat.param("room", 5).mul(2)` flips to accepted.

**The hard parts found.**
1. An orbit chain is configured only while it has an owner (`Cylinder.commitOwner` returns early otherwise), but it
   processes while active. Modulators are therefore ticked at the top of `KatalystChain.process`, which also covers
   the master, the leaving chain of a swap and `parallel` branches.
2. The compressor's and the duck's writers allocate a settings object per resolve; a modulated knob would make that
   per block. Both become preallocated holders compared by value first (a behaviour-identical step).
3. `body`, `vowel` and `eq` install a new bank from zero state on any change (20 ms swap, parked during a fade): a
   modulated `body.wet` would restart the resonance every 20 ms. Broken sound, not cost (Q8).
4. The EQ sections are `IgnitorDsl.EqSection`, shared with the voice and with `bands`; §6.10 names the EQ sections a
   shared DSP law (Q2).
5. A modulated knob changes every block, which defeats the V8 steady-path fixes (`configure` returning early on an
   unchanged config); heap numbers per block may come back on the moving paths. Measured in step 6 (Q15).
6. An unset slot (NaN) inside arithmetic: `Times` scrubs NaN to 0, so `Kat.slot.body.wet.times(2)` would read as a
   SET 0, the opposite of "unset means the constant" (Q5).

**The evaluator.** Compiled once per knob at chain build into flat arrays (op codes, register indices, a register
`DoubleArray`, modulator state in arrays), constant subtrees folded at compile, evaluated by one loop whose `when`
arms are statements storing into the registers (no double crosses a non-inlined call, no lambda per block). A
slot-only expression evaluates only on a map change, so it costs nothing per block. The laws are the Ignitor's own
backend functions (one law, two hosts, the wire types stay apart): `_arithmetic_laws.kt` (a `rangeLaw` hoisted from
`RangeIgnitor`), `fastSin`, `waveTrapezoid` with `WaveVoiceState`'s shapes, `PerlinNoise.fbm`/`BerlinNoise.fbm`.
Sampled once per block, so not bit-identical to an Ignitor LFO: the same law at block resolution.

**D2 in practice.** `KnobGlide` gets a span chosen at construction: today's for a slot-only knob, one block for a
modulated one, which is D2's linear ramp across the block. Per knob, not per stage. Rates above about half the block
rate (about 187 Hz at 48 kHz) alias; no clamp (raw Motor), documented. Per stage: gain, arithmetic, distort amount,
delay wet and feedback, reverb wet and size, phaser center, sweep, wet and floor, compressor threshold, ratio and knee,
duck depth glide over one block; attack, release, phaser rate, delay cap and reverb lowpass apply per block; a
modulated delay time steps through its 50 ms tap crossfade (Q9); a modulated duck orbit switches with no mechanism;
body and vowel material/vowel indices step through the bank swap (the morph was rejected by ear, 2026-09).

**Steps** (one commit each, mandatory mutation tier):
1. Laws hoisted, behaviour-identical (`rangeLaw`, the perlin step and octave cap to one internal home).
   **Built** (2026-10-11): `rangeLaw` in `ignitor/_arithmetic_laws.kt`, `PERLIN_STEP` and `PERLIN_FBM_MAX_OCTAVES` in
   `ignitor/_noise_laws.kt`; guarded by `ConstantFoldParitySpec`, `VibratoRangePhaseSpec`, `TremoloCompositionSpec`
   (red on a mutated `rangeLaw`) and `BuiltInVoiceMatrixSpec` (the perlin and berlin pins).
2. The compressor's and the duck's settings in preallocated holders, behaviour-identical.
   **Built** (2026-10-11): each writer refills one holder, the compressor's `configure` compares values
   (`utils/same_knob.kt`, shared with the phaser); guarded by `KatalystSlotResolverSpec` (the holder rows),
   `KatalystCompressorEffectSpec` (the cache rows), `KatalystSettingsAllocationSpec` (JVM) and `SameKnobSpec`.
3. (Waits for `KlangMap` on main: the param maps become `KlangMap<Double>?` in the Klang-Main-Dev session's engine
   follow-up 2; this step rebases onto it first, so the migration is written once against the new type. Agreed
   between the sessions, 2026-10-11.) D1: the wire type with `Constant` and `Param` only, the full migration, renders identical in raw doubles for the
   master-chain songs and one classic-chain `katp` song (a scaffolding fixture, removed in step 7). `KatalystParam`
   retired (Q1), `coerce` deleted, an Ignitor node on a knob a type error.
   **Built** (2026-10-12): `KatalystKnobDsl` (`audio_bridge/KatalystKnobDsl.kt`, leaves `Constant(value)` and
   `Param(name, default, description)`, wire names `const` and `param` like the Ignitor's) on all 35 stage knob fields;
   `KatalystDsl.Slots.*` and `Kat.param` are `KatalystKnobDsl.Param` (the script door types both as the root
   `KatalystKnobDsl`, as `Ignitor.param` is typed `IgnitorDsl`); `toKatalystKnob` refuses every Ignitor node,
   `toIgnitorDsl` every Katalyst knob; the `eq` door maps a `Kat.param` onto the section's `IgnitorDsl.Param`
   (`toEqSectionKnob`, Q2) and the chain builder reads only those two leaves off a section (`eqKnob`, anything else
   unset, `KatalystEqEffectSpec` pins it); `KatalystSlots.resolve`/`coerce` and their six rows in `KatalystSlotResolverSpec` deleted, and the
   `WireSharedSpec` row about an Ignitor tree shared between two stage knobs. `WIRE_SCHEMA_HASH` 1330155241 to
   1168938423; no change to either KSP processor. Behaviour changes, all on inputs no song uses: an Ignitor node on a
   knob was folded once and is now a script error (`k.blend(Ignitor.constant(0.3), ...)` included, which was accepted),
   and a hand-built Kotlin EQ section holding an expression reads unset instead of its fold. Guarded by `KatalystDslSlotsSpec`, `KatalystDefaultsSyncSpec`,
   `WireCodecRoundTripSpec` (a row per stage with both leaves), `KlangScriptKatalystDoorParitySpec` (the new D1 row),
   `LangSlotSetterSpec`, `KatalystSlotResolverSpec`, `KatalystEqEffectSpec`. Renders identical in raw doubles: the
   corpus (18 songs, Kokon at `dfa9cd64389e619f` before and after, the reference itself predates the `eb39b385`
   Kokon edit) and the scaffolding fixture `src/jvmTest/kotlin/KatalystKnobMigrationFixtureSpec.kt` (classic chain
   through `katp` `afce19df06d968f4`, through the bus doors `f64d42e5c1e288b1`, an authored chain with a `Kat.param`
   on every knob kind, each moved by `katp` through two values its default is not, `6a69ecdeb84e8574`, a master chain
   `de98222378e41b4d`; each pinned twice on the unchanged tree, the authored row in a scratch worktree at `eb39b385`
   after review round 1 found its first version moved only 5 of its 18 params).
4. Expressions over slots (D4): the arithmetic, `neg`, `abs`, `range`, both doors, the evaluator, the unset rule (Q5).
   The non-null knobs' fallback arguments are dead since step 3; step 4 makes `KatalystKnob` non-null with the
   nullable path kept for `lowpass` and the EQ.
   **Built** (2026-10-12): the nodes `Plus`, `Minus`, `Times`, `Div`, `Pow(base, exp)`, `Min`, `Max` (mathematical),
   `Neg`, `Abs`, `Range(inner, from, to)` on `KatalystKnobDsl`, the Ignitor twins' field and wire names
   (`WIRE_SCHEMA_HASH` 1168938423 to 615137850, no KSP change). The evaluator `KatalystKnobProgram`
   (`audio_be/.../katalyst/KatalystKnobProgram.kt`): compiled once per knob into an op array, operand and output
   register indices and one register `DoubleArray`; constant subtrees fold at compile through the same `execute` (a
   statement `when` over `KatalystKnobOp` storing into the registers, the laws of `_arithmetic_laws.kt`, `neg` as
   `timesLaw(x, -1.0)` as the Ignitor's `neg()` builds it). `KatalystKnob(node)` is non-null; `knobOrUnset` is the
   nullable path (reverb `lowpass`, the EQ section knobs), a missing knob being `Constant(SLOT_UNSET)`. A plain
   `Param` resolves exactly as before; an expression loads one register per slot leaf (one lookup each) and runs
   once per changed map, never per block. Q5: a leaf reading a non-finite value (state or default) stops the load
   and the knob is `SLOT_UNSET`, not written. `written` = a leaf took a finite value from the state AND the value is
   finite (so `written` always comes with a finite value, as on a plain slot; a NaN constant through `plus` is no
   write). Doors (both, `KatalystBuilders.kt`): `plus`/`add`, `minus`/`sub`, `times`/`mul`, `div`, `pow`/`power`,
   `min`/`max` (the crossing), `neg`/`negate`, `abs`, `range(from, to)` on `KatalystKnobDsl`; operands through
   `toKatalystKnob` (text now "a number, a Kat.param or a Kat expression"); `toEqSectionKnob` refuses an expression
   (E2, recorded); `katalystSlotName` refuses one ("an expression is not a slot, write its param"). `blend(wet)`
   unchanged; its reason text no longer claims a param has no arithmetic (Q14 stays the follow-up). Guarded by
   `KatalystKnobExpressionSpec` (every op against the Ignitor runtime's control-rate value of the same tree over
   15 operands, NaN, both zeros, 0 divisors, 1e300 and the infinities included, every leaf placement: the fold and
   the evaluator; the fold's instruction count; Q5; `written`; a stage's constant on an unset expression; the delay's
   `stageAskedFor` on a written expression wet; one evaluation per resolve over 50 blocks),
   `KatalystGainEffectSpec` (`katp("fader", 0.6)` on `gain(fader.times(0.5))`: the 17-block glide from 0.5 to 0.3,
   frame 63 of the first block 0.24705882352941178 by hand), `KatalystSettingsAllocationSpec` (a three-slot nested
   expression re-resolved every block: 0 bytes, 4001 evaluations), `WireCodecRoundTripSpec` (a row per node and a
   deep tree), `KlangScriptKatalystKnobExpressionDoorParitySpec` (every door and alias, positional and named, both
   doors, the crossing, Ignitor operands, the EQ refusal), `LangSlotSetterSpec` (`katp`/`ignp` refuse an
   expression), `KlangScriptKatalystDoorParitySpec` (the Q3 row flips to accepted). 30 mutations, each red (every
   law arm, the crossing, the register wiring, the fold, the unset short-circuit, `written`, a per-block evaluation,
   an allocating load, each door); the wire rows' one DSL-side mutation (a duplicate wire name) is refused by the
   codec processor at build. Allocation check GREEN, 58 of 58 within budget (no case holds an expression knob yet:
   step 6).
5. Modulators (D3) and the per-block path (D2), probably split: 5a nodes, doors and evaluator; 5b the chain hook and
   the stages; 5c the bank stages per Q8.
6. Measured in the harness: a slot expression, a modulated gain and phaser center, a perlin; time and bytes per
   block, JVM and V8. The per-block resolve of a modulated compressor or duck knob gets an allocation case of its own,
   measured single-case with `--predictable` on V8 (step 2's `CompressorSettings` and `DuckSettings` are double-field
   holders, proven allocation-free on the JVM only, and `audio/ref/performance.md` says such a holder can still box,
   the `DriftLanes` note), or the holders become `DoubleArray`s.
7. Records, and the render fixture removed.

**Questions.** Decided by the maintainer (2026-10-11), each at the lean: Q2 E2 ("keep shared, slots only"), Q3
processed blocks, Q8 (a) the in-place blend, Q12 inherit with one fixed seed and an Ignitor follow-up for the
sample-rate dependence (to be filed in `docs/tasks/engine-follow-ups.md`). The rest the coordinator takes at the lean,
recorded here, and any of them can be reopened.
- **Q1** `KatalystParam` retired, `Kat.param` returns `KatalystKnobDsl.Param`, `KatalystDsl.Slots.*` are the wire
  nodes; `katp` refuses an expression ("an expression is not a slot"). Lean: yes.
- **Q2** the EQ sections: (E2) keep `IgnitorDsl.EqSection` with a constant or a `Kat.param` only, an expression on an
  EQ knob a type error and a recorded asymmetry; or (E1) a Katalyst section hierarchy, with `eq` and `bands` split
  per host. Lean: E2 now, E1 as its own task when a song wants a swept bus EQ.
- **Q3** the modulator clock: the phase advances over the blocks the chain processed (it pauses while the orbit
  sleeps, `reset` does not rewind), or it follows engine time. Lean: processed blocks.
- **Q4** a draining leaving chain keeps its modulators running. Lean: yes.
- **Q5** any non-finite slot leaf makes the whole knob unset. Lean: yes.
- **Q6** an expression mixing a slot and a modulator glides over one block, so a `katp` jump on it steps over 2.7 ms.
  Lean: accept.
- **Q7** the "was it asked for" gates count a modulated knob as asked for; the threshold gates (delay time, reverb
  size, phaser depth, distort amount) read the per-block value and switch with their click-free mechanisms. Lean: yes.
- **Q8** `body`/`vowel` wet and floor: (a) an in-place per-sample blend in `ResonatorBank` (a DSP change), (b) accept
  20 ms zero-state steps, (c) refuse modulators there. Lean: (a); the index knobs step through the swap.
- **Q9** a modulated delay time steps through the 50 ms crossfade, "not a chorus"; a tape-style tap is future work.
  Lean: yes.
- **Q10** `freq`, required, on the periodic modulators (the Ignitor's word). Lean: yes.
- **Q11** builder knobs `phase` on all and `duty` on `square`; no `analog`, no flank or reset knobs (sample-level
  edges): recorded asymmetries. Lean: yes.
- **Q12** `perlin`/`berlin`: one fixed seed; and their `rate` is the Ignitor's 0.003 noise units per SAMPLE, so its
  speed depends on the sample rate (about 144 units/s at 48 kHz, 132 at 44.1 kHz). Inherit it for parity, or make the
  Katalyst's per second. Lean: one seed, inherit, and an Ignitor follow-up for the sample-rate dependence.
- **Q13** no `rangex` or `clamp` now. **Q14** lifting `blend(wet)`'s plain-number asymmetry: a follow-up. **Q15** the V8
  cost on moving paths: decided on step 6's numbers. **Q16** `carryOver` across spans clamps: accept. **Q17** no
  `@WireShared` on the knob type.

## Steps (as first written)

1. The knob language: the wire type (D1), the evaluator (per block, allocation-free, no boxing on V8), the codec rows.
2. The doors: `Kat.param(...)` gains the arithmetic, `Kat.sine(...)` and the rest of D3, both doors, door-parity
   spec.
3. The stages read their knob per block (D2); the slot writers resolve an expression from the orbit's state.
4. Measured in the harness (`docs/tasks-archive/2026-10/20261011-katalyst-stage-measurements.md`): one expression knob and one modulated knob,
   time and bytes per block, JVM and V8.
5. Records: `audio/ref/katalyst.md`, `audio/MEMORY.md`, `.claude/skills/dsl-design/door-shapes.md`, the music-writing
   reference.

## References

- `audio_be/src/commonMain/kotlin/cylinders/katalyst/KatalystSlots.kt` (`KatalystKnob`; `coerce` deleted in step 3)
- `klangscript-libs/src/commonMain/kotlin/stdlib/KatalystBuilders.kt` (`toKatalystKnob`, the one conversion)
- `audio_be/src/commonMain/kotlin/KnobGlide.kt`
- `docs/plans/future/signal-graph-engine.md` §6.10
