# Sprudel — Testing Strategy

## Commands

```bash
./gradlew :sprudel:jvmTest                          # preferred (fast)
./gradlew :sprudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :sprudel:jsTest                           # browser-specific only
```

## Rules

- **Test across ≥12 cycles** — timing bugs compound and only surface after several cycles
- **Always verify both `part` and `whole`** explicitly
- **Filter by `isOnset`** when testing playback behavior; `queryArc()` returns all events

## Test Structure

```kotlin
"pattern test" {
    val subject = createPattern()
    assertSoftly {
        repeat(12) { cycle ->
            withClue("Cycle $cycle") {
                val cycleDbl = cycle.toDouble()
                val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    .filter { it.isOnset }
                events[0].part.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
                events[0].whole.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
            }
        }
    }
}
```

## JS Compatibility

`compat/JsCompatTests.kt` — compares Kotlin output directly against JS implementation.
Add compat test cases when implementing any new DSL function.

## Door calling forms: one table

Each knob of the voice doors (level and routing, the oscillator knobs, `adsr`, `unison`, the bus doors, the modulation
and distortion doors, the four filters, `fm`, `vibrato`, `penv`; the spec's KDoc has the list and what is not in it) is
one entry of `lang/LangDoorFormsSpec`, which runs it through every calling form (pattern method, string receiver,
standalone mapper, chained mapper, each in Kotlin and KlangScript) and asserts the VALUE written, plus the bare-call
reinterpret of each head, a continuous pattern per knob, and each listed compound door's positional order. A new voice
door or knob adds an entry there; the door's own spec keeps only what is particular to the door (its fill, its
guards, its wire mapping). The accessor, mapper, gap and tail-only rows of both doors are `LangFieldAccessorsSpec`'s.
