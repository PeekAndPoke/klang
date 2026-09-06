# Sprudel arithmetic: a continuous control is evaluated once per query arc

Found 2026-09-06 while piloting the field accessors (`docs/tasks/sprudel-field-accessors.md`).

## Symptom

```
seq("1 1 1").mul(sine)                  // 0.5, 0.5, 0.5      expected 0.5, 0.93, 0.07
seq("1 1 1").mul(perlin.range(0.95, 1.05))   // 1.0, 1.0, 1.0  (perlin at an integer time is its lattice zero)
```

Setters behave differently and as expected: `s("hh*8").pan(sine.range(0, 1))` sweeps per event.

## Cause

`applyArithmetic` (`lang_arithmetic.kt`) joins through `_innerJoin(args)`, which is
`control._bind { ... }`: the STRUCTURE comes from the control. A `ContinuousPattern` queried
over the arc `[0, 1)` emits one event with the value computed at `from`, so every source event in
that cycle receives the value at the cycle start.

The setters go through `_liftNumericField` / `_applyControl`: structure from the source, control
sampled with `sampleAt(onset)` per event.

Strudel's default `add`/`mul`/... are the source-structured ("in") form; the control-structured
form is the explicit `.add.out(...)`.

## Workaround in use

Segment the control: `seq("1 1 1").mul(perlin.seg(4).range(0.95, 1.05))`. This is the documented
idiom for continuous patterns as controls (`perlin.range(200, 2000).freq().segment(128)`), and the
accessor pilot's violin example uses it.

## Options

1. Arithmetic joins like the setters: structure from the source, control sampled at each source
   onset. Audibly identical for discrete controls (only onsets are scheduled), no fragmentation of
   source events, continuous controls work without `seg`. Blast radius: every arithmetic and
   comparison operator, their specs, golden fixtures.
2. Keep control-structured arithmetic, make `ContinuousPattern` emit per-source-event values.
   Not possible: the leaf does not know the source structure.
3. Leave as is, document `seg` as required for continuous controls in arithmetic.

Recommendation: 1, as its own change with the 12-cycle specs and golden comparison, after the
accessor pilot. Decide with the maintainer whether the control-structured form should survive
under another name.
