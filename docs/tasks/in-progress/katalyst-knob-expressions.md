# Katalyst knobs that listen: expressions over `Kat.param`, and the Katalyst's own modulators

Status: **in progress, steps 1 to 4, 5a and 5b built** (decided: D1 to D4, Q2, Q3, Q8 and Q12 by the maintainer,
2026-10-11, Q12's seed refined 2026-10-12; the other Q at the coordinator's lean).

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
     | body, vowel | `wet`, `floor` | **TEMPORARY until 5c (Q8):** refused at both doors, a type error ("a Katalyst body's wet takes no Kat modulator yet; a moving wet would restart the resonance every block"), anywhere in the expression; a hand-built one (Kotlin, the wire) is FROZEN, never ticked (the resonator writer ticks the index only). 5c lifts both |
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
     keeps the hard cut (coordinator, after a first version that faded every off moved the corpus); `Reverb.lowpass` is
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
   per block on a mismatch), `Ducking.attackSeconds`.
7. Records, and the render fixture removed.

**Questions.** Decided by the maintainer (2026-10-11), each at the lean: Q2 E2 ("keep shared, slots only"), Q3
processed blocks, Q8 (a) the in-place blend, Q12 inherit with one fixed seed (refined 2026-10-12: fixed per position,
"the n-th noise in a chain gets seed + n, so two noise knobs move independently but every build sounds the same")
and an Ignitor follow-up for the sample-rate dependence (to be filed in `docs/tasks/engine-follow-ups.md`). The rest
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
