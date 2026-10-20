# KLANG-00300: Arithmetic on the Katalyst: the Ignitor's operators as bus stages, and a small optimizer

> **Status (2026-10-11): DONE.**

Status: **done 2026-10-11** on the branch `bus-distort-default`: steps 1, 2 and 4 built; step 3, the optimizer, is
**won't implement** (maintainer, 2026-10-11, on the D5 numbers below: "Ok then no optimizer for the catalyst now").
Reviewed in three rounds, the third clean (2026-10-11).

## Why

The maintainer, 2026-10-11: "There is one thing we are totally missing in the KatalystDsl and it is the arithmetic
operators like in the IgnitorDsl", and then: "This means the KatalystDsl would also need an Optimizer".

On the Ignitor every signal has `plus`/`add`, `minus`/`sub`, `times`/`mul`, `div`, `neg`/`negate`, `abs`,
`pow`/`power`, `min`, `max` and more (`KlangScriptIgnitorExtensions.kt`, section "Arithmetic"). The Katalyst has
two of them in another form: `gain` (a multiply) and `parallel` (a sum). Nothing else: a bus cannot be rectified,
hard-capped, raised to a power or flipped in polarity.

## Decided (maintainer, 2026-10-11)

- **The Ignitor and the Katalyst stay two DSLs.** We looked at one shared hierarchy (a Katalyst as an Ignitor tree
  with an input leaf, or `Dsp<C>` with a context per host) and set it aside: "Maybe it is the better approach to treat
  ignitors and katalyst individually, even if that means we need to duplicate the dsls and backend functionalities.
  More code but less complex." The record of what was considered: KLANG-00216 (signal graph engine) §6.10.
- **So the arithmetic acts on the bus signal itself, as stages.** Each operator is a stage in the chain, at its list
  position, like `gain`. The operand is a number or a `Kat.param`, the knob vocabulary every Katalyst stage already
  has (block-constant: `Constant` or `Param`).
- **The laws are shared, the shells are not.** Each operator's per-sample law already lives once, in
  `audio_be/src/commonMain/kotlin/ignitor/_arithmetic_laws.kt` (`plusLaw`, `minusLaw`, `timesLaw`, `divLaw`,
  `powLaw`, `minLaw`, `maxLaw`, `absLaw`; `internal`, so reachable from the `cylinders/katalyst` package of the same
  module). A Katalyst stage calls the same function, so `pow` with the same numbers gives the same sample on both
  sides, its signed-magnitude rule and its `safeOut` clamp included. Duplicating the law is a review finding.
- **The Katalyst gets an optimizer** of its own, on the stage list (step 3), with its scope and its worth decided
  in D5.

## What it looks like

```javascript
Katalyst(k => k
  .eq(e => e.lowpass(6000))
  .abs()                        // full-wave: an octave up, before the distort
  .distort(0.4)
  .mul(Kat.param("trim", 0.8))  // a level a pattern can move with katp
  .max(0.9))                    // "at most 0.9": a hard cap
```

Each call is a stage. Per channel, the same operand on left and right. Memoryless: no latency, no tail
(`hasTail()` false), nothing allocated per block.

## Read from the code (2026-10-11)

- The laws and their clamps: `_arithmetic_laws.kt`. `Plus` and `Minus` are bare; `Times`, `Div` and `Pow` apply
  `safeOut` (±1e15, NaN to 0); `Div` maps a divisor of exactly zero to zero and guards any other with `safeDiv`.
- The Katalyst `gain` stage (`KatalystGainEffect`) multiplies WITHOUT `safeOut`, glides a change over
  `KNOB_GLIDE_SECONDS` (`KnobGlide`, the LEVEL rule of `audio/ref/knob-glide.md`) and skips the multiply at exactly
  unity (bit-transparent).
- A Katalyst knob takes a number or a `Kat.param`, through `toKatalystKnob` (`KatalystBuilders.kt`); a bare
  `Ignitor.param` is a script error there. A `Param` knob reads the orbit's `katp` state per block; at the output
  position nothing fills a slot, so it plays its default.
- The orbit's silence test reads the mix AFTER the chain (`Cylinder.tryDeactivate`, `isMixBufferSilent`), and the
  output has the house DC blockers (`MasterStage`, about 7 Hz).
- The Ignitor optimizer runs once per registration (`IgnitorRegistry.register`) and holds every rule to
  `OPTIMIZER_PARITY` (1e-12 of the block's scale), proven by `IgnitorDslOptimizerRenderSpec` and
  `IgnitorDslOptimizerFuzzSpec`. The Katalyst registers its chains in `KatalystRegistry.register`.
- The `min`/`max` doors are clamps on every surface: `a.max(b)` is "a, at most b", so the `max` door builds the `Min`
  node (the guardrail in `CLAUDE.md`, guarded by `StdLibIgnitorTest`). The Katalyst doors follow the same crossing.

## Decided while building (2026-10-11)

The maintainer, while step 1 was being written: "yes these should be per sample operations like in the ignitors",
and "I would expect them to behave all identical to the ignitor arithmetic". That settled D2, D3 and D6 against the
coordinator's leans:

- **D1:** one wire stage per operator (`Plus`, `Minus`, `Times`, `Div`, `Pow`, `Min`, `Max`, `Abs`), one runtime
  class over them (`KatalystArithmeticEffect`, each operator's law inlined into its own loop).
- **D2:** `times`/`mul` build their own `Times` stage on `timesLaw`, clamp included, NOT the fader: `gain` stays the
  tone-neutral level (no clamp, unity skipped, the `gain.gain` slot). `neg`/`negate` are `Times(-1)`, as the
  Ignitor's runtime `neg` is `mul(-1)`.
- **D3:** `plus`/`add` and `minus`/`sub` are offered, option (b): the door's KDoc says a constant offset keeps its
  orbit from ever going silent.
- **D4:** every operand glides per sample over `KNOB_GLIDE_SECONDS` (the fader's ramp), and each sample takes the law
  with the operand at that sample. A divisor glides as its RECIPROCAL, the level, and divides again once settled: the
  first build glided the divisor itself, and both round 1 reviewers found the glide inventing divisors near zero (from
  `div(0)` to `div(1)` it opened at about +67 dB; from `div(-1)` to `div(1)` at 48 kHz one sample landed on the
  `safeDiv` floor, a gain of about +300 dB, while at 44.1 kHz the ramp hit an exact zero and peaked near +61 dB). A non-finite operand is unset: the operator's identity (0 for plus and minus, 1
  for times, div and pow), reached by a glide; a cap or a floor has none, so an unset `Min`/`Max` passes the bus and
  its first operand after that arrives at once. These two are the only differences from the Ignitor, both because
  this is an orbit knob.
- **D5, measured** (JVM, 48 kHz, 128 frames, a timing loop that includes refilling the block): one `times` stage 232
  ns per block, `times` + `plus` + `min` 331 ns, so one cheap stage is about 50 ns; a `pow` 3,359 ns (a reference
  compressor 2,696 ns, a reference reverb 4,572 ns). The block budget is 2,666,667 ns. Folding three cheap stages into
  one would save about 100 ns per orbit per block, about 0.004 %, and a `pow` cannot be folded away. Measured on the
  JVM only; the JS figure the brief asked for was not taken. Decided (maintainer): no optimizer. A fold would also
  have to answer the glide: two gliding operands folded into one product do not give the same ramp (the product of
  two linear ramps is not linear), so it would cover constant operands only or need a margin decision of its own.
  Revisit if a measurement ever shows long runs of arithmetic on many orbits.
- **D6:** the set above with the Ignitor's aliases (`add`, `sub`, `mul`, `power`, `negate`) and parameter names
  (`other`, `exp`).

## Decide before implementing (as written before the build)

- **D1, the wire shape.** One sealed `@WireName` stage per operator (`Mul(operand)`, `Abs`, ...), mirroring the
  Ignitor's one node per op, or one stage with an op field. The house rule (wire types over enums, `/dsl-design` §7)
  points to one stage per operator. Lean: per operator.
- **D2, `mul` and `gain`.** Two words for one multiply on the bus breaks "one word per concept" unless they are one
  node. Lean: `times`/`mul` build the existing `Gain` stage, the way the Ignitor's `times` and `mul` build one `Times`
  node. Open with it: the gain stage does not clamp at ±1e15 where `timesLaw` does. Either the gain stage adopts
  `timesLaw` (a change only above +300 dB) or the difference is recorded as an asymmetry.
- **D3, `add`/`sub` with a constant: a DC offset on a bus.** Its one musical use is before a nonlinearity
  (asymmetric clipping). But a nonzero offset keeps the post-chain mix from ever being silent, so the orbit never
  deactivates. It also steps on with the orbit's first note, which the house DC blocker turns into a low thump.
  `abs` and an even `pow` make DC from the signal too, but they are silent on silence. Options: (a) leave `add`/`sub` off the
  Katalyst and give `distort` a `bias` knob if a song wants asymmetric clipping (won't-implement is a first-class
  outcome); (b) offer them, with a KDoc on the orbit cost; (c) offer them and pair each with a DC blocker after the
  next nonlinearity (more mechanism). Lean: (a).
- **D4, glides.** A `Kat.param` operand moves per block. The LEVEL rule asks a level knob to glide (`mul`, `div`).
  For `pow`, `min` and `max` a moving operand also zippers. Decide per operator which glide applies, from the
  knob-glide plan; a constant operand never glides (a new number is a new chain, and the chain crossfade covers it).
- **D5, does the optimizer pay?** The Ignitor's optimizer pays because a voice tree runs once per voice, many at a
  time. A bus chain runs once per orbit, and a memoryless stage is one pass over 2 × 128 samples. Measure first:
  three neighbouring arithmetic stages against one, on the JS target. If the saving is small, the optimizer waits. Lean: build step 3 only
  after the number, and bring the number to the maintainer.
- **D6, which operators.** The set above (`plus`/`add` and `minus`/`sub` subject to D3, `times`/`mul`, `div`,
  `neg`/`negate`, `abs`, `pow`/`power`, `min`, `max`), with the Ignitor's aliases and the same KDoc words. The
  Ignitor's other unary ops (`tanh`, `sign`, `sqrt`, `exp`, `log`, `floor`, ...) and `clamp` wait for a song that
  asks; `tanh` is a saturation, which `distort` already covers.

## Steps

1. **The stages.** The wire types in `KatalystDsl.kt` (D1), the effects in `audio_be/.../cylinders/katalyst/`, each
   calling its law from `_arithmetic_laws.kt`, the arms in `KatalystChainBuilder` (and the exhaustive `when`s next
   to it: the duck walks), the knob writers for a `Param` operand (`KatalystSlotWriters.kt`), the glide (D4).
   Specs: per stage, the output equals the law applied to the input, sample for sample, on both channels; a
   `Kat.param` operand follows `katp`; NaN in the operand reads as the stage's "unset" (decided per stage, like the
   gain's unity). Mandatory tier, every spec mutation-checked.
2. **The doors.** KlangScript and Kotlin in one change (`KatalystBuilders.kt`, the `@KlangScript.Function`
   extensions are both doors), with the Ignitor's aliases, the `min`/`max` crossing and their own door-parity spec,
   `KlangScriptKatalystArithmeticDoorParitySpec`. Script-door defaults stay plain literals (there are none: every operand is
   required).
3. **The optimizer: won't implement** (maintainer, 2026-10-11, D5). What it would have been: a pure `KatalystDsl -> KatalystDsl` pass at registration, the Ignitor
   optimizer's rule shape on a list. Candidates: neighbouring constant arithmetic folds into one stage, identities go
   (`mul(1)`, `div(1)`, `add(0)` if D3 keeps it), a `Kat.param` operand is a wall. Each rule held to
   `OPTIMIZER_PARITY` by a render spec (authored chain against optimized chain), plus a kill switch like the Ignitor's
   `OptimizerHint` if the rules grow past folding.
4. **The records.** `.claude/skills/dsl-design/door-shapes.md` (the new doors), `audio/ref/katalyst.md`,
   `audio/MEMORY.md` (its section in place, one History line), the KDoc of `KatalystStageDsl` and of each door.

## Not in this task

The first two are taken up by KLANG-00302 (katalyst knob expressions) (maintainer, 2026-10-11: a `Kat.param`
expression is evaluated at least once per block, and the Katalyst gets its own `Kat.sine()` and the rest rather than
Ignitor nodes); the cost of the stages goes into the harness with KLANG-00301 (katalyst stage measurements).

- **Knob expressions** that follow `katp` live (`gain(Kat.param("fader").mul(0.5))`). An expression over a param is
  still folded once when the chain is built (`KatalystBuilders.kt`, the header KDoc), and `blend`'s `wet` stays a
  plain number (its recorded asymmetry).
- **An Ignitor signal as an operand** (`k.mul(Ignitor.sine(2))`): it needs an Ignitor runtime on the bus. A bus LFO, if
  wanted, is its own small knob source later.
- **KlangScript operators on a builder** (`k * 0.5`): KLANG-00056 (klangscript native object operators) covers
  operators on native objects; a builder is the wrong receiver for them.

## Links

- KLANG-00216 (signal graph engine) §6.10 (the unified hierarchy, considered and set aside).
- `audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt` and KLANG-00204 (ignitor optimizer open items)
  (the Ignitor optimizer this one borrows its rule shape from).
- `audio/ref/knob-glide.md` (D4).

## History

- 2026-10-20: numbered KLANG-00300 (was `docs/tasks-archive/2026-10/20261011-katalyst-arithmetic.md`).
