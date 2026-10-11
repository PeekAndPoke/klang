# Katalyst knobs that listen: expressions over `Kat.param`, and the Katalyst's own modulators

Status: **open, decided** (D1 to D4, maintainer, 2026-10-11). Not started.

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
2. **The Katalyst's own modulators.** `Kat.sine(rate)`, and the other shapes D3 settles, usable on any knob that
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
  before building.
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
  compressor's `lookahead`, the catalogue names of `body` and `vowel`).

## Steps

1. The knob language: the wire type (D1), the evaluator (per block, allocation-free, no boxing on V8), the codec rows.
2. The doors: `Kat.param(...)` gains the arithmetic, `Kat.sine(...)` and the rest of D3, both doors, door-parity
   spec.
3. The stages read their knob per block (D2); the slot writers resolve an expression from the orbit's state.
4. Measured in the harness (`docs/tasks-archive/2026-10/20261011-katalyst-stage-measurements.md`): one expression knob and one modulated knob,
   time and bytes per block, JVM and V8.
5. Records: `audio/ref/katalyst.md`, `audio/MEMORY.md`, `.claude/skills/dsl-design/door-shapes.md`, the music-writing
   reference.

## References

- `audio_be/src/commonMain/kotlin/cylinders/katalyst/KatalystSlots.kt` (`KatalystKnob`, `coerce`)
- `klangscript-libs/src/commonMain/kotlin/stdlib/KatalystBuilders.kt` (`toKatalystKnob`, the one conversion)
- `audio_be/src/commonMain/kotlin/KnobGlide.kt`
- `docs/plans/future/signal-graph-engine.md` §6.10
