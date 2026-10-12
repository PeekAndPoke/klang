# Katalyst knobs that listen: expressions over `Kat.param`, and the Katalyst's own modulators

Status: **done 2026-10-12** on the branch `katalyst-live-knobs`: steps 1 to 7 built. A Katalyst knob is an
expression in the Katalyst's own wire type (`KatalystKnobDsl`: constants, `Kat.param`, the Ignitor's arithmetic and
`range`, and the modulators `Kat.sine`, `saw`, `ramp`, `tri`, `square`, `perlin`, `berlin`), evaluated on a changed
map or, with a modulator, once per processed block; `body` and `vowel` blend in place; measured, with five V8
remedies. Reviews (rounds, critical, major; from the records and the step commits): steps 1 and 2 together 2, 0, 1;
step 3 1, 0, 0; step 4 1, 0, 0; 5a 2, 0, 1; 5b 3, 0, 4; 5c 2, 0, 1; step 6 2, 0, 2; 13 rounds, 0 critical, 9 major,
each step's last round clean. The open items went to their homes ("Open, handed on" below): two listening
questions in `docs/tasks/by-ear/`, four follow-ups in `docs/tasks/katalyst-knob-followups.md`, the V8 items in the
engine follow-ups. (Decided: D1 to D4, Q2,
Q3, Q8 and Q12 by the maintainer, 2026-10-11, Q12's seed refined 2026-10-12; the other Q at the coordinator's lean.)

## Open, handed on

- **Cut against bleed on an owner change** (5b), a listening question:
  `docs/tasks/by-ear/delay-reverb-owner-change-cut.md`.
- **Engine follow-ups** (`docs/tasks/in-progress/engine-follow-ups.md`): items 39 (the knob noises' `fbm` crossings on V8) and
  40 (the modulated compressor's five setters), each with the budget lines a fix re-sets, and 41 (the residue of a
  moving delay time, reverb size and phaser breakpoint, not located) were filed by step 6; item 37 is Q12's (perlin
  and berlin per second; the Ignitor's and the Katalyst's noises change together). Item 36 (`E-arith`'s second V8
  mode, in 5b) and `spluck-a`'s known tail (in 5c; items 4 and 7, "Known tails" in
  `audio_benchmark/alloc-budgets.txt`) each turned one allocation check red, green on the second run. Items 34 and
  35 come from the stage measurements, 38 from engine follow-up 2; none from this task.
- **E1 (Q2), a `seed` knob (5a), a tape-style delay tap (Q9), `blend(wet)` with a `Kat.param` (Q14)**:
  `docs/tasks/katalyst-knob-followups.md`, sections 1 to 4.
- **The duck orbit switch click**: a modulated `duck.orbit` switches with no mechanism, as a `katp` one does:
  `docs/tasks/by-ear/duck-orbit-switch-click.md` (the corner is recorded there).

## Where to continue

The task is closed; nothing of it waits half-built. The work is on the branch `katalyst-live-knobs`. To understand
the knobs, read `audio/ref/katalyst.md` first ("Where a knob comes from", the stage table, and the lifecycle
template's lessons of 5b and 5c), then the KDocs of `KatalystKnobDsl` (`audio_bridge`), `KatalystKnobProgram` and
`KatalystKnob` (`audio_be/.../cylinders/katalyst/`) and `KatalystChain.process` (the hook). The doors are in
`KlangScriptKatalyst.kt` and `KatalystBuilders.kt` (`klangscript-libs`), their shapes in
`.claude/skills/dsl-design/door-shapes.md`, the song writer's view in
`.claude/skills/klang-music-writing/ref/ignitor-reference.md`. The V8 numbers and remedies: step 6 below and
`audio/ref/performance.md`. Next in this area: engine follow-ups 39 to 41, then whichever open item above a song asks
for.

## Why

The maintainer, 2026-10-11, after the Katalyst arithmetic stages landed
(`docs/tasks-archive/2026-10/20261011-katalyst-arithmetic.md`, "Not in this task"):

- "I think kat-param should be evaluated at least once per block."
- "Ignitors as katalyst params sound interesting, but i would say we should instead create the equivalents
  Kat.sine() etc. instead of mixing the two dsls."

Before this task (2026-10-11) a Katalyst knob was a number or a `Kat.param`, and nothing else listened:

- `Kat.param(...)` had no arithmetic of its own (`KatalystSlots.kt`, the `KatalystKnob` KDoc). An expression over a
  slot could only be written with Ignitor nodes (`Ignitor.param("room", 5).mul(2)`; a BARE `Ignitor.param` was a type
  error, an expression over one was accepted, `toKatalystKnob`), and anything that was neither a constant nor a slot
  was probed and FOLDED to one number when the chain was built (`KatalystSlots.coerce`, retired). `katp` never
  reached it.
- An Ignitor oscillator on a knob (`k.phaser(rate = Ignitor.sine(0.2))`) was accepted and went through the same
  probe: a signal is not block-constant, so `controlRateValueOrNull` answered null and the knob fell back to its
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
   Kokon edit) and the scaffolding fixture `KatalystKnobMigrationFixtureSpec` in the root module's `jvmTest` (removed in
   step 7: its job, proving that the migration rendered identically, was done; classic chain
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
   the stages; 5c the bank stages per Q8. (As run: 5a the nodes and the evaluator only, the doors move to 5b with the
   chain hook, so no door can build a modulator before a chain can tick one.)
   **5a built** (2026-10-12): the nodes `Sine`, `Saw`, `Ramp`, `Tri` (`freq` required, `phase` default 0), `Square`
   (`freq`, `duty` default 0.5, `phase`), `PerlinNoise`, `BerlinNoise` (`rate` 1, `octaves` 1, `persistence` 0.5) on
   `KatalystKnobDsl`, field and wire names of the Ignitor's (`sine`, `saw`, `ramp`, `tri`, `perlin-noise`,
   `berlin-noise`); `Square` takes the fields of the `Pulze` the Ignitor's `square` door builds and the door word as
   class and wire name (`square`). `WIRE_SCHEMA_HASH` 615137850 to 211191718, no KSP change. The evaluator
   (`KatalystKnobProgram`): one `MODULATOR` instruction per node, never folded (nor anything above it), its shape and
   clock in arrays; `run()` samples each modulator at its clock, `tick()` runs and then advances every clock one block
   (`dt * blockFrames`, `dt = freq / sampleRate`, wrapped by `wrapPhase`; a noise by `rate * PERLIN_STEP *
   blockFrames`), `isModulated` from the tree; `compile` takes the chain's `sampleRate` and `blockFrames`. The laws are
   the Ignitor's (`fastSin`, `waveTrapezoid` in a `WaveVoiceState` rebaked when `dt` or the duty changes, the fBm walks
   built in the Ignitor's order); the `phase` operand is read at `clock + phase` as the Ignitor's phase input. Seeds are
   fixed PER POSITION (Q12): the n-th noise of a chain, in build order (`KatalystNoiseOrdinals`, one counter the chain
   builder hands to every knob and into a `parallel`'s branches; within a knob, compile order), is seeded
   `KATALYST_KNOB_NOISE_SEED + n`. The noise clocks wrap: perlin by 256, its period, the same walk bit for bit; berlin
   at 2^23, one step per about 16 hours at rate 1, so the top octave never saturates `toInt()`. One deliberate
   difference: a non-finite noise rate holds the walk, where the Ignitor's would keep the NaN. **A hand-built modulator
   on a chain knob is FROZEN until 5b** (`KatalystKnob`): evaluated at build and on every changed map at the clocks'
   start, never ticked, compiled on a placeholder clock (`UNTICKED_SAMPLE_RATE`, `UNTICKED_BLOCK_FRAMES`, removed by
   5b); chosen over a refusal, which would be an exception on the audio thread for an input the wire accepts. Guarded by
   `KatalystKnobModulatorSpec` (each shape at the hand clock against hand laws and bit for bit against the Ignitor's
   oscillator at the same phase, five offsets, ticks 0 to 17 across the wrap; each shape against the Ignitor's own
   per-sample stream at 1/8, 1.25 and 2.5 cycles per block; the edges by hand; a slot `freq`; a moving `freq` rebaking
   the saw's and the square's edges and a moving duty the square's; perlin and berlin on two builds and against the
   Ignitor's walk; the seed per position in a knob, on a shared counter and through the chain builder, a `parallel`
   branch included; the law's period of 256 bit for bit, a fast perlin against the Ignitor's unwrapped walk, a perlin
   far past 2^24 cells bit for bit against the law at its position modulo 256 (the one row that binds the wrap), the
   berlin range far past 2^31; the non-finite rate; `Kat.sine(1).range(400, 1600)` by hand; the fold and `isModulated`;
   the frozen knob), `KatalystKnobModulatorAllocationSpec` (JVM, 10,000 ticks of every kind, 0 bytes),
   `WireCodecRoundTripSpec` (a row per node, its defaults, a nested tree).
   Known consequence of the seed rule: it is per CHAIN, so two orbits whose chains put a noise at the same position walk
   the same noise; a `seed` knob would be a later door decision.
   **Notes for 5b** (all four built in 5b). While a knob is unset (Q5), 5b does not tick it: its clocks pause, in line with Q3's processed
   blocks (a failed Q5 load leaves the failing leaf's non-finite value in its register, so a tick would advance a
   periodic clock by NaN, which `wrapPhase` reads as a rewind to 0). Tick-after-resolve order: resolve, then tick (a
   run, then the advance), never a double advance in one block. The noise `rate` is REQUIRED on the Katalyst doors, as
   `freq` is (Q10): the Ignitor's default of 1 is an audio-rate default, 0.384 cells a block, a noise AM rather than an
   LFO (coordinator's call, reopenable). The placeholder clock of `KatalystKnob` (`UNTICKED_*`) goes.
   **5b built** (2026-10-12): the per-block path and the doors.
   - **The hook** (hard part 1): `KatalystChain.process` ticks, at its top, every writer with a modulated knob
     (`KatalystSlotWriter.isModulated`, fixed at build, collected once into `modulated`) and applies it: each knob runs
     its program and advances its clocks one block (`KatalystKnob.tick`), the writer refills its holder, `apply`
     configures the stage. So an orbit with no owner, the master, the leaving chain of a swap through its fade AND its
     drain (Q4: its modulators run, its slots stay as its last resolve left them) and every `parallel` branch (its own
     `process`, once per block; the `parallel` writer never ticks) all modulate. Order per block: a changed map resolves
     in the host's `applyParams` (a run at the clocks as they stand), then the tick runs and advances once: never a
     double advance. An owned orbit therefore applies twice a block (the host's `applyParams` with the last tick's
     values, then the hook with this block's), which every stage takes as an unchanged config. An unset knob (Q5) is not
     ticked: its clocks pause and resume where they stopped. The `UNTICKED_*` placeholder is gone: knobs compile on the
     chain's sample rate and block size (`KatalystKnob(node, noises, sampleRate, blockFrames)`).
   - **The span** (D2): `KnobGlide(..., modulated)`, chosen per knob at construction: one block for a knob whose
     expression holds a modulator, `KNOB_GLIDE_SECONDS` for every other (a `katp`-moved knob on the same stage keeps
     its 50 ms). The builder now builds a stage's knobs before the stage (same noise ordinals, the writer's order), and
     the duck after the loop. Q6 (a slot and a modulator in one knob: one block) and Q16 (`carryOver` across spans
     clamps; reachable now, KDoc rewritten) as decided.
   - **The stages, as built:**

     | stage | knob | rule (modulated) |
     |---|---|---|
     | gain | `gain` | one-block level glide, per sample from the last block's value |
     | arithmetic | operand | one-block glide; `div` keeps gliding its reciprocal: through the pole the level ramps between the two blocks' reciprocals and nowhere else |
     | distort | `amount` | one-block glide (the drive ramped per sample); the `amount <= 0` gate reads the block's value and switches through the stage's 50 ms blend, turned around where it stands (an LFO through 0 never reaches Off: accepted, documented on the constructor) |
     | delay | `wet`, `feedback` | one-block glides |
     | delay | `time` | the block's value to the line: it steps through the 50 ms tap crossfade, a change mid-crossfade parked, latest wins (Q9, "not a chorus"); a finite time below `MIN_ACTIVE_DELAY_SECONDS` keeps the stage Active while the wet glides the feed to 0 (one block for a modulated wet), then the off-config that finds it landed drains, and above the gate it resumes; every return to Active (out of Off, out of Draining, the first activation) glides the wet up from 0 (`KnobGlide.startFrom`). Rounds 1 and 2: before, the feed was cut and restarted in one sample. Only a modulated time fades; every other off keeps the hard cut, a non-finite time on a modulated stage included, which acts at once, mid-fade too. A longer time grows the ring once per size class (a rent on the audio thread, as for `katp`) |
     | delay | `cap` | per block, no glide |
     | delay, reverb | `wet` gate | Q7: a MODULATING wet (`KatalystKnob.isModulating`: modulated, not unset, and finite since round 1) asks for the stage (`stageAskedFor`), so an LFO through 0 does not drain and re-rent; a NaN modulated wet is off, as a NaN constant is |
     | reverb | `wet`, `size` | one-block glides; a size below `MIN_ACTIVE_SIZE` drains, above it resumes, per block, with the delay's feed fade both ways when the size is modulated (rounds 1 and 2); an unset modulated size fades too (the writer hands `Reverb.normalizeSize`'s 0 for it, a finite size below the gate), where an unset delay time hard-cuts: `normalizeSize`'s NaN-to-0 is the pooled room's existing rule, and a delay time has no finite stand-in for unset, so the two differ by their doors, not by a 5b decision |
     | reverb | `lowpass` | per block, no glide; `ReverbConfig.lowpass` and `Reverb.lowpass` are primitives (NaN unset, a non-finite write unsets; round 1 made the DSP's ONE field, replacing the nullable setter and `setLowpassOrUnset`): the `Double?` holder allocated 24 bytes a block with a moving lowpass (measured, mutation M16c) |
     | phaser | `wet`, `floor` | the C4 pair glides over one block when EITHER is modulated (they share it; a `katp` move of the other then steps over one block too); the depth gate switches per block through the pair; an LFO through the gate lands the pair on identity, the next identity block clears the cascade, so every cycle restarts the cascade from rest (click-free: the wet coefficient is near 0 there) |
     | phaser | `center`, `sweep`; `rate` | one-block glides each; the rate at once. The `applied` short-circuit is defeated every block (step 6 measures) |
     | compressor | `threshold`, `ratio` (as its inverse), `knee`; `attack`, `release` | one-block glides; attack and release at once; the step 2 holders, no allocation (JVM). With a lookahead the attack resizes the box smoother CONTINUOUSLY (round 1: it re-primed both boxes on every length change, a gain step of 0.6 to 0.7) |
     | duck | `depth`; `attack`; `orbit` | one-block glide; Q7: a modulating depth keeps the stage declared (`Ducking` reads a depth below 0 as 0), a NaN one does not (round 1); the attack at once; the orbit switches with no mechanism (the open item `docs/tasks/by-ear/duck-orbit-switch-click.md`); the switch weight keeps its 50 ms |
     | body, vowel | `material`, `vowel` | ticked: a new index steps through the bank swap (20 ms, a change during a fade parked) |
     | body, vowel | `wet`, `floor` | ticked since 5c (Q8 (a)): the blend moves IN PLACE, no install. The pair of coefficients `dryGain`, `wetGain` glides per sample on every bank the swap holds (a fading pair included), over one block when either knob is modulated (they share the pair), 50 ms for a `katp` move otherwise; the bands ring on unless the mix lands at 0. Only a table change (index, on) installs through the swap, carrying its mix as one word; off fades the pair in service to dry. A mix at or below 0 bypasses once the blend has landed there; the landing block zeroes the band state, and a return starts the bands from rest under a wet coefficient from 0. (5b: refused at both doors and frozen when hand-built; 5c lifted both) |
     | eq | sections | refuse an expression, a modulator included (Q2, unchanged) |
   - **The doors** (both, one implementation on `KlangScriptKatalyst`, `Kat`/`Katalyst`): `sine`, `saw`, `ramp`, `tri`,
     `square` as `(freq, configure)` with builders `KatalystSineBuilder` ... `KatalystSquareBuilder` (`phase`; `duty` on
     the square), `perlin` and `berlin` flat as `(rate, octaves = 1, persistence = 0.5)`; `freq` and `rate` required,
     defaults plain literals, no `analog`, flank or reset knobs (Q10, Q11). A pre-existing KDoc example
     (`k.phaser(0.5, center = ...)` on `range`) mixed positional and named arguments, which KlangScript refuses; fixed.
   - **Decided in 5b, not by the plan** (coordinator's to reopen): the phaser's `wet` and `floor` share one glide, so
     either modulated makes the pair one block; the duck's `depth > 0` is an "asked for" question (Q7), not a
     threshold gate, so a modulating depth keeps the stage declared; a hand-built modulator on a `body`/`vowel` `wet`
     or `floor` is frozen rather than refused at runtime (5a's reason: no exception on the audio thread for an input
     the wire accepts); `Reverb` keeps its cutoff as one primitive field (`lowpass`, NaN unset, round 1)
     so a moving lowpass boxes nothing; `KatalystKnob.evaluations` counts ticks too.
   - **Guarded by** `KatalystModulatedChainSpec` (each periodic shape and both noises on a gain stage, the block's last
     sample against the hand law at blocks 0, 1 and k, the one-block ramp's middle; an arithmetic stage over a
     modulator; an orbit with no owner, the master, a `parallel` branch ticking once, the leaving chain through fade
     and drain; the unset clock pausing and resuming; resolve then tick; the span per knob on one phaser),
     `KatalystModulatedStagesSpec` (a row per stage decision above: `div` through its pole, the distort gate, delay
     wet and feedback with the Q7 gate, the time stepping and parking, the time gate, the cap, reverb wet, size,
     lowpass and the size gate, the phaser pair, rate, center, sweep and its depth gate, the compressor's five, the
     duck's depth, attack, orbit and Q7, the duck takeover across spans, the body index through the swap, the frozen
     hand-built body wet), `KatalystModulatedChainAllocationSpec` (JVM: 1,000 blocks of a chain with a modulated knob
     on every stage family, every knob of most, 0 bytes), `KatalystKnobModulatorSpec` (a resolve never advances, the
     knob's tick; the hand laws moved to `_katalyst_modulator_hand_laws.kt`),
     `KlangScriptKatalystModulatorDoorParitySpec` (every door, positional and named, script and Kotlin, against
     hand-built nodes; params and expressions as operands; a modulator in arithmetic and on stages; the Ignitor-node,
     `eq` and body/vowel refusals; a missing `freq`/`rate`). 38 mutations, 37 red (the hook moved into `applyParams`,
     a parent ticking its `parallel` branches, an unset knob ticked or advancing, a resolve that advances, the span
     back to 50 ms and every span one block, each stage not told its knob is modulated, `div` gliding the divisor, the
     distort gate cutting, the Q7 gates of the wet and the duck, `carryOver` unclamped, each per-block refill dropped,
     the delay's parking and time gate, the reverb's size gate, the body wet ticked and the index not, knobs compiled
     at 44.1 kHz, a settings object per refill, the boxed `ReverbConfig.lowpass`, five door mutations); the one green:
     writing the lowpass through the `Double?` setter, a box the JVM's escape analysis removes (the JVM row cannot see
     it; V8 is step 6's). Totals: `audio_be` JVM 2756 and JS 2647, `audio_bridge` 148 and 285, `klangscript-libs` 902
     and 679, `sprudel` 3509 (486 skipped) and 2998 (3 skipped), the root module JVM 64 (the step 3 fixture green: four
     rows, chains without modulators render identically), `klang` 33 and 18, all green. Corpus 18 of 18 identical. The
     allocation check: the first run RED on `E-arith` alone, the known second mode of `engine-follow-ups.md` item 36
     (rounds 155.1, 195.3, 15.5 B; a chain without a modulator, whose hook is an empty loop), the second run GREEN, 58
     of 58 (`E-arith` 47 B); both runs' rows are in `docs/benchmarks/alloc-scoreboard.md`. No allocation case holds a
     modulator yet: step 6.
   - **Review round 1 fixes** (2026-10-12): `KatalystKnob.isModulating` asks for a finite value too (a NaN modulated
     wet or depth is off, as a NaN constant is); `Compressor` resizes its lookahead box smoother continuously (the
     rings keep their whole capacity of history, a length change moves the running sums by the samples entering or
     leaving; `primeBoxes` only at construction and the resets; a fixed attack bit for bit as before): the largest
     one-sample gain step under an attack moving 2 to 4 ms per block went 0.61 to 0.0144, a fixed 2 ms attack's 0.0148;
     a MODULATED delay time or reverb size crossing its gate fades the feed (`timeModulated`, `sizeModulated` at build;
     round 1 ran the fade as a sub-mode at the head of Draining, round 2 rebuilt it, below); every other switch-off
     keeps the hard cut (coordinator, after a first version that faded every off moved the corpus: `frozen piece
     derSchmetterling_2026_09_16`, hash `0fc662a01aaecd03` to `07ee8c4618b22ca4`, peak 0.9144 to 0.9145, the whole move
     the fade, 18 of 18 identical with only the fade's retargets removed); `Reverb.lowpass` is
     one primitive field. Guarded by `LimiterLookaheadSpec` (the gain step; the ceiling under an attack moving every
     block), `KatalystModulatedStagesSpec` (a NaN modulated wet and depth; the feed edge per stage both ways) and the
     unchanged "drains on schedule" rows (the hard cut). 19 mutations, 18 red (the green one guarded code round 2
     deleted); corpus 18 of 18 identical; allocation check GREEN, 58 of 58.
   - **Review round 2 fixes** (2026-10-12): the fade REBUILT, smaller. A modulated time or size crossing the gate with
     a finite value keeps the stage Active while the wet glide falls to 0 (the ceiling observing as on any Active
     block); the off-config that finds it landed drains or goes Off exactly as an unmodulated off does. Contract, in
     the class KDocs: the off-config is re-applied every block (the writer of a modulated time or size is a modulated
     writer). Every return to Active from Off or Draining, the first activation and a hard-cut drain included, starts
     the wet at 0 (`KnobGlide.startFrom`); round 1 snapped there. Deleted: Draining's `fading` and `fed`, the
     countdown raise, the fed re-measure, both "never Off inside the fade" guards, the reverb's second heal and its
     Draining `size`; the unmodulated path is line for line pre-5b. Recorded behaviour change: such a stage fades its
     feed IN at its first activation (an empty unit). Feed edges through `KatalystChainBuilder.build` (DC 0.5, wet
     0.8, largest one-sample output step; round 1's in brackets, measured with `startFrom` removed): delay off 2.1e-4, from Off 2.1e-4 (0.4), from a
     hard-cut drain 2.9e-4 (0.4), first activation 2.1e-4 (0.4); reverb 5.9e-5, 5.9e-5 (0.0104), 6.0e-5 (0.0104),
     5.9e-5 (0.0104). Guarded by `KatalystModulatedStagesSpec` (a "through build()" row per stage; the two per-block
     gate rows re-baselined, block 4 is now the one-block fade) and `LimiterLookaheadSpec` (literal bounds next to the
     ratio). 9 mutations, 9 red. Totals: `audio_be` JVM 2766 and JS 2657, `klangscript-libs` 902 and 679, the root
     module JVM 64, all green; corpus 18 of 18 identical; allocation check GREEN, 58 of 58 (a first run refused a
     stale V8 bundle, its rows are in the scoreboard).
     **Open by ear:** an unmodulated delay or reverb still cuts its feed in one sample on an owner change or a `katp`
     off; a fade would remove that edge but feed the new owner's first 50 ms into the old owner's ring or room, a bleed
     across owners, so cut against bleed is a listening question outside 5b.
   - **Review:** 3 rounds (round 1 opus, round 2 reviewer-high, round 3 reviewer-xhigh, both reviewers each round;
     the safety valve fired after round 2 and the maintainer chose the rebuild and round 3), 0 critical, 4 major (round
     1: a NaN modulated value counted as asked for, a modulated lookahead attack re-primed the smoother; round 2: the
     return from Off still snapped, the builder's `timeModulated` unguarded), round 3 clean. Round 3's wording MINORs
     applied (a `katp` time cuts on an UNMODULATED stage; why an unset reverb size fades and an unset delay time cuts;
     the phaser cascade restarting from rest when an LFO crosses its depth gate). Lessons in `audio/ref/katalyst.md`,
     the stage-lifecycle template.
   **5c built** (2026-10-12): the in-place blend of `body` and `vowel` (Q8 (a)); 5b's refusal and freeze lifted.
   - **What changed.** Only the TABLE is a resonator bank's identity: `KatalystResonatorEffect.configure` compares the
     table alone (by reference), so a material or vowel change (or on, or off) still installs through the 20 ms swap,
     and a `wet` or `floor` change with the same table calls `ResonatorBank.glideBlend` on every pair the swap holds:
     no install, the bands and their state untouched. `ResonatorBank` holds its blend as two `KnobGlide`s, `dryGain`
     and `wetGain`, LEVEL knobs ramped per sample from the last block's value, written from the block's end; one block
     when `mixModulated`, 50 ms otherwise; `install` snaps them (a reinstalled bank is still bit for bit a new one), and
     a settled blend runs the old loop, bit for bit. The resonator writer ticks `wet` and `floor` too, and the builder
     builds the three knobs before the stage and passes `mixModulated = wet.isModulated || floor.isModulated`. The
     doors take a modulator on all four knobs (`toResonatorMixKnob` and `holdsModulator` deleted; nothing else used
     them).
   - **Decided in 5c, not by the plan** (coordinator's to reopen):
     - Glide the two COEFFICIENTS, not `w`: the output is linear in them, so a linear ramp of both is exactly a linear
       crossfade between the old blend's output and the new one's, never outside them; a `w` glide bends both along a
       `sin^2` and cannot carry a floor move. They share one span (the phaser's rule for its C4 pair): either knob
       modulated makes both one block, and a `katp` move of the other then steps over one block (Q6's reading).
     - The mix reaches every pair the swap HOLDS, the pair fading out included, so a mix moving during a table's fade
       moves both sides of it. A call with ANOTHER table carries its mix as one word: an install snaps the new pair's
       blend and leaves the outgoing pair's as it sounded (one block behind for a modulated mix, then it follows), and
       a parked change waits whole (5c-11's "the fade in flight is untouched" rows stand), so for at most one fade
       (20 ms) while a third table waits, the blend of the pairs that sound holds; the parked install snaps its pair to
       the latest mix, and the outgoing pair catches up at the next mix move, over one block for a modulated mix (up
       to 20 ms of modulation in one block; the audio review of round 1 estimated about 2 dB in one block for a 2 Hz sine
       over a range of 0.7). This keeps a table and a mix that change together rendering as before.
     - The bypass at a mix at or below 0 holds only once the blend has LANDED there; while it glides the bands run.
       The landing block zeroes the band state (silent: the output is the dry by its last sample), so a return starts
       the bands from rest under a wet coefficient ramping from 0, the phaser's cascade rule. The bypass keys on the
       target mix, not on the coefficients (a mix of 1e-300 still runs the bands, `ResonatorBankSpec`'s law row).
     - The stage counts its in-place moves (`blendMoves`, a test seam next to `installs`).
   - **Rows** (`KatalystModulatedStagesSpec`, every one through `KatalystChainBuilder.build`; the oracle is the hand
     blend `dry * dg + wet * wg` with the coefficients by hand per block and ramped per sample, over the bank's band sum
     taken from a full-wet render of the same table, so only a bank never re-installed matches it, within 1e-9):
     - a body with a sine wet: one install at blocks 0, 1, 2, 7, 13 and 23, one blend move per block whose wet changed,
       the knob at its clock, and every block of 24 on the oracle;
     - a vowel with a sine floor across the floor's kink (the law's 0.206 at wet 0.7): one install over 24 blocks, one
       blend move per block whose floor changed, and every block with the floor at its clock and on the oracle;
     - both in one chain: each installed once, the largest one-sample step 0.00624 against 0.00883 for the worst of the
       four fixed renders at the ranges' ends (bound 1.1 times);
     - a wet through 0 and back: blocks 0 to 4 on the oracle (block 4 glides to the dry), blocks 5 to 7 the tone bit for
       bit, blocks 8 to 11 on the oracle over bands FROM REST (a full-wet render whose tone starts at block 8); the
       return's largest step 0.01352 against the dry tone's own 0.01440 (bound 1.1 times; a fixed wet of 0.75 gives
       0.01235);
     - a `katp` move of an unmodulated wet (0.3 to 0.8): one install, one blend move, nothing parked, the 19-block glide
       at 48 kHz linear in each coefficient, every block of 30 on the oracle;
     - a material change (wood to glass by `katp`) while the wet moves: the second install at block 6, and blocks 0 to
       19 on the oracle of the swap's own crossfade (960 samples) of the two blends, each pair's coefficients as the
       decision above schedules them;
     - `KatalystModulatedChainAllocationSpec` (JVM): a second body and vowel with a moving wet AND floor (the body's
       wet through 0), 0 bytes per 1,000 blocks, one install each over 17,500 blocks;
     - `KlangScriptKatalystModulatorDoorParitySpec`: the refusal row flipped to acceptance, a body's and a vowel's wet
       (alone, inside a slot expression) and floor on the script door, and wet and floor on the Kotlin door, against
       hand-built nodes.
   - **Mutations**, 17, all red: the mix back in the identity compare (6 of the 6 stage rows; the allocation row, 2189
     installs); the writer not ticking `wet` (3) or `floor` (1); the writer's `isModulated` the index only (4); the
     builder not telling the body (3) or the vowel (1); the bank's dry glide (4) or wet glide (3) back to 50 ms for a
     modulated knob; the landing without its zeroing (the return row, 0.066 off the from-rest oracle at block 8); the
     bypass while the blend still glides (the through-0 row); a glide of `w` with the coefficients taken from it (6,
     1.4e-3 off on the sine-wet row); the mix reaching only the pair in service (the table-change row, block 7); no
     per-sample ramp (6); every span one block (the `katp` row); the body door dropping a wet, the vowel builder
     dropping a floor (the door row each). Each in one lock call, restored and compared.
   - **Behaviour change**: the corpus is 18 of 18 identical, but the step 3 scaffolding fixture's
     authored row moves (`KatalystKnobMigrationFixtureSpec`, 6a69ecdeb84e8574 to 2949f0d641d984ec): its
     `katp("vw", "<0.2 0.5>")` moves the vowel's wet alone each cycle, which glides in place now where it installed a
     fresh bank; peak delta 0.0714 (-22.9 dBFS) at 4.015 s, first difference at 2.005 s, peak 0.6271 unchanged. With the
     mix back in the identity compare the row renders 6a69ecdeb84e8574 again, so the delta is the in-place blend alone
     (its `mat` and `bf` move together, a table change, and render as before). The 5c commit re-pinned the row as the
     intended change of Q8 (a); step 7 removed the fixture.
   - **For step 6's list**, the double crossings of a moving blend, per moving block per bank (two banks, four during a
     fade): `WetDryMix.dryCoeff`/`wetCoeff` (in and out) and two `KnobGlide.retarget` in `ResonatorBank.targetBlend`;
     the two `KnobGlide.value` reads in `process`. The ramp's start values travel in a `DoubleArray`.
   - **Totals**: `audio_be` JVM 2771 and JS 2662, `klangscript-libs` 902 and 679, the root module JVM 64 with the one
     fixture row above red, all else green; corpus 18 of 18 identical; allocation check: the first run RED on
     `spluck-a` alone (V8 314 B, budget 96, the known tail), the second GREEN, 58 of 58; both runs' rows are in the
     scoreboard.
   - **Round 1 fixes** (2026-10-12): two rows, an owner back inside a fade to dry with a new wet (the resumed bank
     glides to it, on the oracle through the swap's turn-around; red under the mix moving only while `swap.active`,
     block 7, and under a snapping `glideBlend`) and a new wet after that fade has landed (a fresh install, no blend
     move; red under the old count); `blendMoves` counts only a move of a held pair; the resonance that rings on
     through a moving wet now says "unless the mix lands at 0" and the parked-table hold names the catch-up, at every
     site; the deleted door refusal's text gone from `KlangScriptKatalyst.kt` and the music-writing reference.
     `audio_be` JVM 2773 and JS 2664, `klangscript-libs` 902 and 679, the root module JVM 64 (the fixture at
     2949f0d641d984ec), corpus 18 of 18 identical.
   - **Review:** 2 rounds (round 1 opus, both reviewers; round 2 reviewer-high, one reviewer for the test-only delta),
     0 critical, 1 major (round 1: a new wet inside a fade to dry was untested, a mutant survived; a test gap), round 2
     clean. Round 2's wording MINORs applied: a same-table word during a fade is acted on at once (the configure KDoc);
     `audio/MEMORY.md` and `audio/ref/effects-mixing.md` say a wet or floor change glides in place; "off" installs
     nothing, it fades the pair in service to dry (`audio-constraints.md`, the stage table).
6. Measured in the harness: a slot expression, a modulated gain and phaser center, a perlin; time and bytes per
   block, JVM and V8. The per-block resolve of a modulated compressor or duck knob gets an allocation case of its own,
   measured single-case with `--predictable` on V8 (step 2's `CompressorSettings` and `DuckSettings` are double-field
   holders, proven allocation-free on the JVM only, and `audio/ref/performance.md` says such a holder can still box,
   the `DriftLanes` note), or the holders become `DoubleArray`s. The modulators (from the audio review of 5a), each in
   single-case `--predictable` processes and under a mixed orbit profile: (a) one sine knob, (b) a square with a slot
   duty, (c) a saw whose freq is a sine (a rebake per tick), (d) perlin at octaves 1 and 8, (e) a chain with all seven
   kinds. `fbm` takes and returns doubles once per noise op per tick (a few heap numbers expected), the shape setters
   only on a rebake. The per-block double crossings the JVM spec cannot see (5b review round 1, audio 3), each a
   candidate heap number per block on a moving knob: `KnobGlide.retarget`, the gain's and the arithmetic's
   `configure(Double)`, `gate()`, `guarded()`, `guardedDepth()`, `guardedFloor()`, `Reverb.normalizeSize`, the
   `Reverb.lowpass` setter, the five `Compressor` setters (each runs `updateCoefficients`, so 15 `timeConstantCoeff`
   per block on a mismatch), `Ducking.attackSeconds`. And Kotlin/JS's `Double.coerceIn`, which is not inline: 4 calls
   per bank per moving block of a body or vowel blend (`ResonatorBank.targetBlend` once, `WetDryMix.dryCoeff` twice,
   `WetDryMix.wetCoeff` once; 5c review round 1, audio 3).
   **6 built** (2026-10-12, review round 1 applied): ten allocation cases, twelve timing rows, five remedies, three
   follow-ups filed. **Every number below comes from a run of `docs/benchmarks/2026-10-12_183400_knob-step6-v8.md`** (named
   there: run T the tree, run H HEAD's engine with the tree's cases, mutants M1, M6, M7, M8, G1, probes P1 and P2, the
   hand-edited bundles X40 and XS, and the four full checks' allocation tables) **or from the full checks' timing
   records** `docs/benchmarks/2026-10-12_185434_*.md` and `2026-10-12_191024_*.md`.
   - **The cases** (`audio_benchmark/AllocationCases.kt`; every rate and range non-integral; a twin with constant
     knobs for the timing rows): `E-kat-sine` (a) a sine on the fader; `E-kat-square` (b) a square on the fader whose
     duty is a slot expression (`Kat.param("kat.duty", 0.3).plus(0.05)`, written by the case's `katp`), so the
     expression's arithmetic runs every block inside the modulated knob; `E-kat-sawfm` (c) a saw whose freq is a sine,
     a rebake per block; `E-kat-perlin` (d) perlin at one octave on the fader and at eight on a `times` stage (merged:
     one path, `fbm`, with two octave counts); `E-kat-all7` (e) all seven kinds, the fader and six `times` stages;
     `E-kat-dyn` the compressor with all five knobs moving and the duck's depth and attack (the settings holders and
     `Ducking.attackSeconds`); `E-kat-res` a body and a vowel with wet and floor moving (the 5c blend and its
     `WetDryMix` `coerceIn`; the body's wet through 0); `E-kat-gates` a delay time and a reverb size through their gates
     (the 5b feed fade) and the reverb's lowpass moving (the `Reverb.lowpass` setter); `E-kat-mix` the mixed orbit
     profile: body, vowel, delay, reverb, phaser, compressor, fader and duck (not `eq`, `distort` or the arithmetic),
     one knob each moving over the seven kinds (the phaser's center among them), a slot expression on the phaser's
     sweep; `C-kat-remap` `C-remap`'s two voices taking turns as the owner, so the orbit sees a new map every block,
     with an authored chain whose fader is a slot expression: one evaluation per block (it measures the resolve, not
     the stage's DSP: the voice rig does not process the chain).
   - **Bytes per block on V8**, `--predictable`, single-case, HEAD's engine (run H) / the tree (run T); the check's
     own method (unpinned, 3 rounds, medians) on the tree (run T, then the full checks 2026-10-12_184920 and
     `_190741`), and HEAD's engine by the check's method (run H). The fader alone (`E-gain`) reads 15.7 in both trees
     and 46 to 52 by the check's method.

     | case | `--predictable` H / T | the check's method, T and the two full checks | H, the check's method |
     |---|---|---|---|
     | `C-kat-remap` | 15.7 / 15.7 | 15, 11, 21 | 16 |
     | `E-kat-sine` | 52.5 / 15.5 | 47, 31, 47 | 48 |
     | `E-kat-square` | 31.2 / 15.6 | 47, 36, 50 | 61 |
     | `E-kat-sawfm` | 52.3 / 15.3 | 51, 46, 46 | 73 |
     | `E-kat-perlin` | 113.1 / 113.7 | 149, 145, 144 | 155 |
     | `E-kat-all7` | 149.9 / 120.0 | 161, 155, 155 | 181 |
     | `E-kat-dyn` | 262.7 / 98.7 | 103, 104, 103 | 269 |
     | `E-kat-res` | 159.9 / 15.7 | 6, 98, 21 | 171 |
     | `E-kat-gates` | 112.6 / 66.6 | 67, 117, 72 | 134 |
     | `E-kat-mix` | 521.5 / 196.3 | 205, 201, 196 | 587 |

     The classic orbits gained too (`--predictable`, H / T): `E-stages` 68.3 / 15.8, `E-delay` 75.9 / 32.3, `C-remap`
     160.1 / 30.1. The check's method reads several cases' HEAD and tree within each other's spread (`E-kat-sine`,
     `E-kat-perlin`, `E-kat-all7`): there the difference is not resolved by it. **The JVM reads 0 B on all ten** (the
     full checks: `ThreadMXBean`, 9 rounds, every case in one process).
   - **The remedies** (each located with the sampling heap profiler and `--trace-turbo-inlining` on the production
     bundle in development runs; each proven by a mutant in one lock call, backed up, relinked, restored, compared
     with `cmp` and the tree's diff hash). "Over its line" means the mutant's median by the check's method exceeds a
     budget line; nothing else is called red.
     1. `KnobGlide.retarget` Kotlin `inline`. M1: `E-kat-mix` 439.2 (`--predictable`) and 441.3 by the check's method,
        **over its line** (250); `E-kat-res` 158.4 and 184.7, over its 144 in that run but not counted (its healthy
        medians reach 98).
     2. `KnobGlide`'s `value`, `target` and `start` in a `DoubleArray`, read through `inline` accessors. M6:
        `E-stages` 45.5, `E-delay` 61.1, `E-kat-gates` 92.0, `E-kat-dyn` 113.7 (`--predictable`); by the check's method
        36.4, 61.2, 123.6, 108.7, **under every line**: guarded by its KDoc.
     3. `Compressor.processGliding` takes its six ramp ends from `Compressor.glideArgs`. M7: `E-kat-dyn` 196.5
        (`--predictable`), 196.4 by the check's method, **over its line** (152).
     4. `PhaserCore.prepareBlock(blockFrames, block)` reads the breakpoint from the `PhaserBlock`. M8: `E-kat-mix`
        226.1 (`--predictable`), 232.7 by the check's method, **under its line**: guarded by its KDoc.
     5. The hook's `isFinite()` calls as `isFiniteInline` (`utils/numerical_safety.kt`): `KnobGlide.retarget`,
        `KatalystKnob`'s result and `isModulating`, `stageAskedFor`, the writers' guards and gates (now `inline`), the
        compressor's and the duck's `fill`, the arithmetic stage's `configure`. Measured as a group, G1 (all of them
        back): `E-kat-sine` 31.2, `E-kat-square` 29.6, `E-kat-sawfm` 31.2, `E-kat-all7` 134.5, `E-kat-dyn` 128.8,
        `E-kat-mix` 272.1 (`--predictable`); by the check's method 67.3, 67.4, 61.9, 176.1, 134.2, 263.7, **under every
        line**: guarded by the KDoc of `isFiniteInline`.
     **Dropped in review round 1**: `KatalystGainEffect.configure` `inline` (no measured benefit of its own; `glide` is
     private again; run T reads `E-kat-sine` at the fader's floor without it), and the arithmetic stage taking the
     knob (`configure(KatalystKnob)`; its one figure was a mode of `E-kat-all7` that the tree shows too, run T's round
     of 277; the stage keeps its `configure(Double)`, now with `isFiniteInline`).
   - **The candidates of the plan's list, each with an outcome**: `KnobGlide.retarget` and the gain's and the
     arithmetic's `configure(Double)`: remedies 1 and 5. `gate()`, `guarded()`, `guardedDepth()`, `guardedFloor()`: in
     remedy 5. `Reverb.normalizeSize`: no case reads a difference that names it (not measured on its own). The
     `Reverb.lowpass` setter: no heap number, by review round 2's probes R1 and R2 of the run record (the setter call
     replaced by a direct field write, then the reverb configure's size path too: `E-kat-gates` 66.7 and 66.9 against
     run T's 66.6); probe P1 (a fixed lowpass against the moving one) could not see it, because both arms call the
     setter every block. The five `Compressor` setters: 52 B per block on `E-kat-dyn` (98.7 against `E-duck`'s 46.7, run T), filed
     as item 40 (X40: the fields written in place read 45.4). `Ducking.attackSeconds`: none, because X40 accounts for
     `E-kat-dyn`'s excess over the duck's floor (45.4 with the compressor's setters gone, `E-duck` 46.7); probe P2 (a
     fixed attack against the moving one) could not see it, for P1's reason. Kotlin/JS `Double.coerceIn` in the blend: `E-kat-res` reads
     15.7, the fader's floor (run T), none. The step 2 settings holders: XS (their fields as `Float64Array`s) reads
     `E-kat-dyn` 98.7 and `E-kat-mix` 196.3, as run T: they do not box. A slot expression on a new map every block:
     `C-kat-remap` 15.7 in both trees, none.
   - **Filed** (`docs/tasks/in-progress/engine-follow-ups.md`): item 39, the knob noises' `fbm` (three heap numbers per noise per
     block: `E-kat-perlin` 113.7 against the fader's 15.7); item 40, the compressor's five setters per block (52 B per
     block, about three heap numbers, and 15 `exp`); item 41, the residue on a moving delay time, reverb size and phaser
     breakpoint (`E-kat-gates` 66.6 against `E-delay`'s 32.3), not located. Their lines are budgeted as they stand.
   - **Budgets** (`alloc-budgets.txt`, the file's rule: about 40 B above the highest healthy median by the check's
     method, above a tail of high rounds): `C-kat-remap` 64, `E-kat-sine`, `-square`, `-sawfm` 96, `E-kat-perlin` 232 (above run T's round of 194),
     `E-kat-all7` 336 (above run T's round of 277), `E-kat-dyn` 152, `E-kat-res` 144, `E-kat-gates` 160 (set at 112
     first; the first full check of the final tree read a healthy 117 and went red), `E-kat-mix` 250 (the rule's 40 B over
     its medians; review round 2 chose it over 304, whose rounds of 265 and 248 came from the engine before review
     round 1, and at 250 it also guards the `isFinite` group, G1 264); JVM 6 each. **The lines that guard a remedy**: `E-kat-mix`
     (`KnobGlide.retarget` inline, and the `isFinite` group) and `E-kat-dyn` (`Compressor.glideArgs`). The others guard the per-sample class only
     (run H reads under each, but `E-kat-res`). The classic `E-stages` and `E-delay` lines are not re-set.
   - **Timing** (`EffectBenchmark`, 44.1 kHz, the chain from `KatalystChainBuilder`, its params applied and the chain
     processed every block; 10,000 warm-up and 3 iterations of 5,000 blocks per row, the median; µs per block from the
     full checks' records `2026-10-12_185434_compare.md` / `2026-10-12_191024_compare.md`):

     | row | JVM | node |
     |---|---|---|
     | empty chain (baseline) | 0.031 / 0.031 | 0.093 / 0.119 |
     | fader, constant (control) | 0.047 / 0.051 | 0.254 / 0.240 |
     | fader, slot, a new map every block | 0.367 / 0.321 | 0.266 / 0.256 |
     | fader, slot expression, a new map every block | 0.133 / 0.139 | 0.271 / 0.288 |
     | fader, sine | 0.186 / 0.189 | 0.301 / 0.259 |
     | fader, square with a slot duty | 0.108 / 0.106 | 0.273 / 0.272 |
     | fader, saw with a sine freq | 0.167 / 0.169 | 0.313 / 0.279 |
     | fader, perlin 1 octave | 0.148 / 0.150 | 0.277 / 0.265 |
     | fader, perlin 8 octaves | 0.191 / 0.191 | 0.309 / 0.330 |
     | 7 stages, constants (control) | 1.24 / 1.22 | 1.67 / 1.60 |
     | 7 stages, the seven kinds | 1.64 / 1.62 | 2.43 / 2.22 |
     | mixed orbit, constants (control) | 21.35 / 21.14 | 31.21 / 31.09 |
     | mixed orbit, modulated | 23.55 / 23.32 | 33.81 / 33.41 |

     **What the difference is.** A modulated row against its constant control is the modulation AND the DSP paths a
     moving knob takes where a constant one does not (the hook, the evaluator, the writer's refill and the stage's
     `configure`, plus the per-sample ramps: the fader's and the arithmetic's one-block glide, the compressor's
     `processGliding`, the body's and the vowel's blend ramps, and the delay's tap crossfade every block its time
     moves). It is not the cost of any one of them, item 40's `exp` included. **Read**: the mixed orbit costs 2.2 µs per
     block on the JVM (10 percent) and 2.3 to 2.6 on node (7 to 8 percent) over its constant twin; the seven kinds 0.4 µs
     on the JVM and 0.6 to 0.8 on node over seven constant stages. On the JVM a modulated fader costs 0.06 to 0.14 µs
     over the constant one. On node a modulated fader reads 0.02 to 0.09 µs over the constant control. **Not
     resolved**: single rows move by up to 0.04 µs between the two runs (the sine 0.301 and 0.259), so on node a slot
     expression's evaluation against a plain slot's lookup (0.271 and 0.288 against 0.266 and 0.256) and eight perlin
     octaves against one (0.309 and 0.330 against 0.277 and 0.265) are not resolved by three iterations of 5,000
     blocks.
   - **Proof**: `console/full-check.sh` on the final tree: 2026-10-12 18:49 RED on `E-kat-gates` alone (117 against
     its first line of 112, a healthy reading; the line was re-set to 160), then 19:07 GREEN: 17,688 tests (`audio_be`
     JVM 2,773 and JS 2,664 run fresh at 18:49 on this code, the root module JVM 64 with the step 3 fixture at
     `2949f0d641d984ec`), the corpus 18 of 18 identical, alloc 78 of 78 within budget. No sound changed: the remedies
     are inlining and holders, and the corpus and the tests are the guard.
   - **Review:** 2 rounds (round 1 opus, both reviewers; round 2 reviewer-high, one reviewer), 0 critical, 2 major
     (round 1: the timing table came from uncommitted scratch logs and contradicted the committed records it cited; the
     `E-kat-mix` line at 240 risked a red by chance on that engine), round 2 clean. Round 2's MINORs applied: the
     `E-kat-mix` line 250 (above), `E-kat-perlin` 232, the probes that could not see a setter crossing replaced by
     round 2's R1 to R3 in the run record, four sentences corrected.
7. Records, and the render fixture removed.
   **7 built** (2026-10-12): the scaffolding fixture `KatalystKnobMigrationFixtureSpec` removed (its four rows had
   proven the step 3 migration render-identical, and its authored row had pinned 5c's intended move). The records
   checked against the code: `audio/ref/katalyst.md` ("A knob is a `KatalystKnobDsl`" in "Where a knob comes from"),
   `audio/MEMORY.md` (a "Katalyst knobs" law, the one-block glide, one History line for steps 6 and 7),
   `docs/retired-names.md` (the `KatalystParam` row quotes the error text as it reads since step 4),
   `klangscript-libs/CLAUDE.md` (its file table names the modulators and the knob arithmetic);
   `.claude/skills/dsl-design/door-shapes.md` and the music-writing reference (`ref/ignitor-reference.md`, "Kat
   expressions" and "Kat modulators", an LFO example included) already matched the code. Credits: Berlin noise (James
   Coyne and Jade Rowland, with the MurmurHash3 finalizer of its hash) and fractal Brownian motion (the noises'
   octaves) added to `CREDITS.MD` and the in-app Credits page; Perlin noise was credited. This record archived from
   `docs/tasks/in-progress/`, every link to it along (33 files, code KDocs and test comments included).
   Tests, each through the lock: the root module JVM 60 (64 less the fixture's four rows), `audio_be` JVM 2773,
   `audio_bridge` JVM 148, `klangscript-libs` JVM 902, `audio_benchmark` compiled (no JVM tests), the root module's JS
   compiled for the Credits page; all green. No engine or DSL code changed in step 7: comments, records, the Credits
   page's text and the fixture's removal.

**Questions.** Decided by the maintainer (2026-10-11), each at the lean: Q2 E2 ("keep shared, slots only"), Q3
processed blocks, Q8 (a) the in-place blend, Q12 inherit with one fixed seed (refined 2026-10-12: fixed per position,
"the n-th noise in a chain gets seed + n, so two noise knobs move independently but every build sounds the same")
and an Ignitor follow-up for the sample-rate dependence (filed as item 37 of `docs/tasks/in-progress/engine-follow-ups.md`). The rest
the coordinator takes at the lean, recorded here, and any of them can be reopened.
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
- **Q7** the "was it asked for" gates count a modulated knob as asked for (while its value is finite); the threshold
  gates (delay time, reverb size, phaser depth, distort amount) read the per-block value and switch with their
  click-free mechanisms. Lean: yes. (For a modulated delay time or reverb size that mechanism is the wet glide on the
  feed, built in 5b review rounds 1 and 2: a finite time or size below the gate keeps the stage Active while the wet
  glides to 0, and the off-config that finds it landed drains; every return to Active, out of Off or Draining and the
  first activation included, glides the wet up from 0. Before it, both cut and restarted the feed in one sample. An
  unmodulated time or size, a hard cut on a modulated stage, and every owner change keep the hard cut: an open by-ear
  question, see the 5b record.)
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
