# Sprudel — Control Patterns & Pattern Composition

## Control patterns need a join (CRITICAL)

Any DSL function accepting pattern arguments **must** go through one of the joins below. Without one, static values
work but control patterns (e.g. `pressBy("<0 0.5>")`) silently break.

```kotlin
// ✅ Correct
fun applyPressBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val rArg = args.getOrNull(0) ?: return pattern
    return pattern._innerJoin(rArg) { src, rVal ->
        val r = rVal?.asDouble ?: return@_innerJoin src
        src.fmap { AtomicPattern.value(it) }
            .let { applyCompress(it, listOf(SprudelDslArg.of(r), SprudelDslArg.of(1.0))) }
            .squeezeJoin()
    }
}
```

## Three joins, pick by who owns the structure

| Join | Structure | Control read | Use for |
|---|---|---|---|
| `_innerJoin(args)` | the CONTROL (its parts, `weight` and `numSteps`; wholes stay the source's) | one value per control event | structural transforms: `pressBy`, `fast`, anything where the control's steps ARE the rhythm |
| `_outerJoin(control)` | the SOURCE, events unchanged in shape | `sampleAt(onset)`, once per source event | setters (`_liftNumericField`): `.gain("1 0.5")`, `.pan(sine)` |
| `_appLeft(control)` | the SOURCE wholes, one fragment per overlapping control event | over the source part, clipped to the query arc | arithmetic, comparison, bitwise (`applyArithmetic`) |

`_innerJoin` for a VALUE operation is the 2026-09-07 bug (`docs/tasks/sprudel-arithmetic-continuous-controls.md`):
a continuous control queried over a cycle yields ONE event valued at the cycle start, so
`seq("1 1 1").mul(sine)` gave every note the same number, and `weight`/`numSteps` came from the control.

A continuous SOURCE has no structure (one event per query arc): `note(saw.range(48, 60).add("0 12"))` plays
one note per cycle under every join. `seg()` the source first.

`_outerJoin` versus `_appLeft`: identical for what is played (only onsets are scheduled), different for what is
READ by a point query. Arithmetic results are read: `"<0.9>".mul("[1.3 0.99!7]")` is an accent map that
`.clip(...)` samples once per note, and it must answer `0.99` at the fifth eighth. `_appLeft` keeps that
fragment; `_outerJoin` would flatten the map to its onset value. The KDoc on `_appLeft` has the full argument.

**Point queries and leaves.** A leaf answers a point query with its FULL part (an atom asked at 0.5 still reports
`[0, 1)`); nothing clips to the query arc on the way up. Any pattern that turns one input event into several
(`SegmentPattern`, `_appLeft`) must therefore drop the pieces outside the query arc itself, or `sampleAt`'s
`firstOrNull` returns the first piece of the cycle for every onset. `segment(n)` had exactly that bug until
2026-09-07: every `.seg()` control inside a setter was one value per cycle. Since the same day a slice is
also the WHOLE of what it returns (Strudel's `struct(pure(true).fast(n))`), so `"0".segment(4).note()`
plays four notes; before, only the first slice of a discrete source was an onset.

## `fmap` + `squeezeJoin`

`fmap` maps values into patterns (creates pattern-of-patterns).
`squeezeJoin` flattens by squeezing inner patterns into outer event timespans.

**Data merge rule** (critical): `outerEvent.data.copy(value = innerEvent.data.value)`
— preserves outer musical properties (sound, note, etc.) while using the inner event's value.

## When Stuck

1. Control patterns involved? → pick the join by who owns the structure (table above); a value op is never `_innerJoin`
2. Look at similar working patterns: `BindPattern`, `TempoModifierPattern`, `RepeatCyclesPattern`
3. Check JS Strudel source for semantics
4. Write a unit test to isolate the issue
5. Trace through `fmap` / `squeezeJoin` data flow