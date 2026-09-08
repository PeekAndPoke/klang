# Sprudel arithmetic: a continuous control is evaluated once per query arc

Found 2026-09-06 while piloting the field accessors (`docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`).

## Symptom

```
seq("1 1 1").mul(sine)                  // 0.5, 0.5, 0.5      expected 0.5, 0.93, 0.07
seq("1 1 1").mul(perlin.range(0.95, 1.05))   // 1.0, 1.0, 1.0  (perlin at an integer time is its lattice zero)
```

Setters behave differently and as expected: `s("hh*8").pan(sine.range(0, 1))` sweeps per event.

## Cause

`applyArithmetic` (`lang_arithmetic_math.kt`) joins through `_innerJoin(args)`, which is
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

---

## Done 2026-09-07 (uncommitted at the time of writing, review loop pending)

Not option 1 as written. Option 1 (structure from the source, control sampled ONCE at each
source onset, i.e. `_outerJoin`) was built first and it **reversed two song structures**:

```
guitarClip = "<0.93 …>".sub(perlin.range(0, 0.02).seg(8)).mul("<0.985!32 [1.3 0.99!7]!32 …>")   // Der Schmetterling
velocity(cat(saw.range(0.25, 1).pow(1.5).slow(32), pure(1).slow(256)).mul("1 0.95 0.975 0.95".fast(2)))   // Stranger Things
```

Both use arithmetic as an **accent map**: a slow or continuous source times a stepped control,
read once per note by a setter (`.clip(guitarClip.fast(2))`, `velocity(...)`). Onset sampling
answers with the control value at the SOURCE's onset, `1.3` for the whole cycle, and the accent
on the first eighth is gone. The old inner join preserved it by accident (the control's steps
became the wholes).

### What shipped: `_appLeft`, Strudel's `appLeft`

`SprudelPattern._appLeft(control) { source, control -> }` (in `SprudelPattern.kt`, next to
`_outerJoin`): structure from the source, values from both. Per source event the control is queried
over the source PART clipped to the query arc; one fragment per overlapping control event, `part` =
the overlap, `whole` = the source's. `applyArithmetic` is built on it, so every arithmetic,
comparison and bitwise operator now:

- keeps the source's wholes, `weight` and `numSteps` (only the first fragment of a source event is an onset);
- reads a continuous control at each onset without `seg()` (the symptom above);
- answers a point query with the fragment covering that point (the accent maps keep working);
- drops a source span where the control has a rest (as before, as in Strudel).

`clamp(lo, hi)` is now the floor and then the cap, two plain arithmetic joins, instead of its own
three-way inner join.

Why the control is queried over the source part clipped to the query arc and not over the source
whole (which is what Strudel does before intersecting): sprudel leaves answer with their full part
even for a point query, so without clipping a point query would return every fragment of the cycle
and `sampleAt`'s `firstOrNull` would pick the first one. This is the same handoff class as the bug
below.

### A second bug the switch exposed: `segment(n)` answered point queries with the first slice

`SegmentPattern` sliced the control's event (an atom, which reports its whole cycle even for a
point query) and returned **all** slices regardless of the query arc. Every setter samples its
control with `sampleAt(onset)`, so every `.gain(saw.segment(4))`, `.bpf(freq = x.seg(4)…)`,
`.lpf(q = x.seg(32).slow(32))` in the corpus read the FIRST slice for every note. (`late`/`early`
are not setters: `TimeShiftPattern` walks the control's events over the arc, so `.late(x.seg(4))`
was never affected; round 2 corrected the first draft here.) The static path
in `applySegment` (`struct("x".fast(n))`, which would have been fine) is dead code: it tests
`nArg?.asIntOrNull()` on the `SprudelDslArg` wrapper, not on its value, so every call takes the
`SegmentPattern` path. Fixed in `SegmentPattern` by skipping slices outside the query arc.

Review round 1 (both reviewers, independently) added the second half: `SegmentPattern` kept the
SOURCE's whole, so `"0".segment(4).note()` (the KDoc example, "four evenly-spaced notes") played
one note per cycle, and `"c e g a b c d e".seg(4)` played all eight. Strudel's `segment` is
`struct(pure(true).fast(n))`: the slice is the whole. Now it is here too (four onsets, and the
four notes under the slice starts), and the dead static path is gone; one implementation. Round 2
added what the re-birth implies: `numSteps` is the slice count and `weight` is 1 (as for `struct`;
`"0".seg(8).take(4)` was a silent no-op with the atom's 1 step), and the unreferenced
`SegmentPattern.static()` (which sliced the QUERY arc, not the cycle) is deleted. Round 2 also made
every arithmetic fragment own a `clone()` of its voice data: `_appLeft` is the first node that fans one
source event out into several, and the shallow `copy` shared the mutable groups between siblings
(`note("c").bpf(freq = 500, q = 4).bpf(freq = mul("1 2"))` wrote 1000 into the played note's filter).

Songs whose written intent now plays for the first time (by-ear entry in
`docs/tasks/by-ear/README.md` §7): Der Schmetterling's two `.sub(perlin…)` humanisations, Greensleeves `bpf(freq = perlin.seg(4).range(180, 1100))`,
Stranger Things `bpf(freq = perlin….segment(16).slow(6))`, Tetris `lpf(q = berlin….seg(32).slow(32))`.

### Verification

- New 12-cycle rows: `LangArithmeticSpec` (continuous control per onset; fragments under one whole;
  wholes/steps from the source; the accent map read through `.clip()`; rest drops),
  `LangComparisonAndLogicSpec` (two rows), `LangMinMaxClampSpec` (continuous bounds),
  `LangSegmentSpec` (point query returns the covering slice; a segmented control reaches every note).
  Every row mutation-checked RED against onset sampling, control-over-the-query-arc, rest-keeps-source,
  the old clamp join, and the unfiltered segmenter; byte-exact restores.
- `MutableVoiceDataGoldenSpec` byte-identical (the corpus has no accent map and no segmented setter control).
- `LangNoteSpec` "continuous values combined with sequence" and `LangFreqAccessorSpec` "novice violin"
  were the two pre-existing rows that caught the segment bug once arithmetic sampled per onset.

### Not done, by decision

- No `.add.out(...)` / `.add.mix(...)` variants (the control-structured form under another name).
  Nothing in the corpus needs it; add when asked.
- A continuous SOURCE with a stepped control (`note(saw.range(48, 60).add("0 12"))`) plays one note
  per cycle (it used to play one per control step, because the inner join re-queried the signal per
  step). Strudel plays nothing there (a signal has no whole). Documented, one spec row, `seg()` the
  source first. No song or example has the shape.

Correction to the first draft of this section, from review round 1: the old inner join never gave
the result the CONTROL's wholes (`BindPattern` preserves the inner whole); it gave it the control's
parts, `weight` and `numSteps`. The fragments were the same as now. What changed for discrete
controls is `numSteps`/`weight` only; the fix is about continuous controls and the segment handoff.
