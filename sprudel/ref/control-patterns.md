# Sprudel — Control Patterns & Pattern Composition

## `_innerJoin` (CRITICAL)

Any DSL function accepting pattern arguments **must** use `_innerJoin`. Without it, static values
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

## `fmap` + `squeezeJoin`

`fmap` maps values into patterns (creates pattern-of-patterns).
`squeezeJoin` flattens by squeezing inner patterns into outer event timespans.

**Data merge rule** (critical): `outerEvent.data.copy(value = innerEvent.data.value)`
— preserves outer musical properties (sound, note, etc.) while using the inner event's value.

## When Stuck

1. Control patterns involved? → need `_innerJoin`
2. Look at similar working patterns: `BindPattern`, `TempoModifierPattern`, `RepeatCyclesPattern`
3. Check JS Strudel source for semantics
4. Write a unit test to isolate the issue
5. Trace through `fmap` / `squeezeJoin` data flow